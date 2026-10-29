//! A blocking system resolver runs only in a bounded, owned Windows process.
//! Host goes through stdin; no URL/query, business root, credentials or app setup.
use crate::desktop_web_text_snapshot_v1::{validate_addresses, validate_url};
use std::{
    io::{Read, Write},
    net::IpAddr,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
    time::Duration,
};
const ARGUMENT: &str = "--nanfeng-web-dns-worker-v1";
const MAX_INPUT: usize = 253;
const MAX_OUTPUT: usize = 4096;
const MEMORY: usize = 256 * 1024 * 1024;
const BUDGET: Duration = Duration::from_secs(12);

fn valid_host(host: &str) -> bool {
    host.len() <= MAX_INPUT
        && validate_url(&format!("https://{host}/"))
            .is_ok_and(|url| url.host == host && url.display_url == format!("https://{host}/"))
}
pub(crate) fn run_if_requested() -> bool {
    let args: Vec<_> = std::env::args_os().skip(1).collect();
    if !args.iter().any(|a| a == ARGUMENT) {
        return false;
    }
    let result: Result<Vec<IpAddr>, String> = (|| {
        if args.len() != 1 {
            return Err("DNS_PROTOCOL".into());
        }
        if !crate::desktop_pdf_worker_v1::limits_present() {
            return Err("WORKER_LIMIT_UNAVAILABLE".into());
        }
        let mut bytes = Vec::new();
        std::io::stdin()
            .lock()
            .take((MAX_INPUT + 1) as u64)
            .read_to_end(&mut bytes)
            .map_err(|_| "DNS_PROTOCOL")?;
        let host = std::str::from_utf8(&bytes).map_err(|_| "DNS_PROTOCOL")?;
        if !valid_host(host) {
            return Err("INVALID_URL".into());
        }
        use std::net::ToSocketAddrs;
        let mut addresses = Vec::new();
        for address in (host, 443).to_socket_addrs().map_err(|_| "DNS")? {
            if addresses.len() >= 64 {
                return Err("DNS_TOO_MANY".into());
            }
            addresses.push(address.ip());
        }
        validate_addresses(&addresses).map_err(str::to_owned)?;
        Ok(addresses)
    })();
    // Never initialize application logging or echo resolver OS diagnostics.
    if let Ok(bytes) = serde_json::to_vec(&result) {
        let _ = std::io::stdout().lock().write_all(&bytes);
    }
    true
}
struct CancelOnDrop(Arc<AtomicBool>);
impl Drop for CancelOnDrop {
    fn drop(&mut self) {
        self.0.store(true, Ordering::SeqCst);
    }
}

#[cfg(windows)]
async fn run_async(
    command: std::process::Command,
    input: Vec<u8>,
) -> Result<Vec<IpAddr>, &'static str> {
    run_async_started(command, input, |_| {}).await
}
#[cfg(windows)]
async fn run_async_started(
    command: std::process::Command,
    input: Vec<u8>,
    started: impl FnMut(u32) + Send + 'static,
) -> Result<Vec<IpAddr>, &'static str> {
    run_async_configured(command, input, started, None).await
}
#[cfg(windows)]
async fn run_async_configured(
    command: std::process::Command,
    input: Vec<u8>,
    started: impl FnMut(u32) + Send + 'static,
    pause_root: Option<std::path::PathBuf>,
) -> Result<Vec<IpAddr>, &'static str> {
    let cancel = Arc::new(AtomicBool::new(false));
    let _cancel_on_drop = CancelOnDrop(cancel.clone());
    let output = tokio::task::spawn_blocking(move || {
        run_command_configured(command, &input, &cancel, BUDGET, started, pause_root)
    })
    .await
    .map_err(|_| "DNS")?;
    let bytes = output.map_err(|code| match code.as_str() {
        "WORKER_TIMEOUT" => "TIMEOUT",
        "CANCELLED" => "CANCELLED",
        "OUTPUT_TOO_LARGE" => "DNS_TOO_MANY",
        _ => "DNS",
    })?;
    let result: Result<Vec<IpAddr>, String> = serde_json::from_slice(&bytes).map_err(|_| "DNS")?;
    let addresses = result.map_err(|code| match code.as_str() {
        "DNS_NOT_PUBLIC" => "DNS_NOT_PUBLIC",
        _ => "DNS",
    })?;
    validate_addresses(&addresses)?;
    Ok(addresses)
}
#[cfg(all(windows, debug_assertions))]
pub(crate) fn acceptance_pause_root(
    root: &std::path::Path,
) -> Result<Option<std::path::PathBuf>, &'static str> {
    let app_root = if root.file_name() == Some(std::ffi::OsStr::new("work-area")) {
        root.parent().ok_or("DNS_ACCEPTANCE_ROOT")?
    } else {
        root
    };
    let arm = app_root.join(".web-dns-input-arm");
    let metadata = match std::fs::symlink_metadata(&arm) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(_) => return Err("DNS_ACCEPTANCE_ARM"),
    };
    use std::os::windows::fs::MetadataExt;
    if !metadata.is_file() || metadata.len() != 0 || metadata.file_attributes() & 0x400 != 0 {
        return Err("DNS_ACCEPTANCE_ARM");
    }
    let verified = crate::desktop_windows_acceptance::resolve(
        Some(app_root),
        true,
        crate::desktop_windows_acceptance::IDENTIFIER,
        app_root.parent().ok_or("DNS_ACCEPTANCE_ROOT")?,
    )
    .map_err(|_| "DNS_ACCEPTANCE_ROOT")?;
    if verified != Some(app_root.canonicalize().map_err(|_| "DNS_ACCEPTANCE_ROOT")?) {
        return Err("DNS_ACCEPTANCE_ROOT");
    }
    std::fs::remove_file(arm).map_err(|_| "DNS_ACCEPTANCE_ARM")?;
    Ok(Some(app_root.to_owned()))
}
#[cfg(all(windows, debug_assertions))]
pub(crate) async fn resolve_paused(
    host: &str,
    root: std::path::PathBuf,
) -> Result<Vec<IpAddr>, &'static str> {
    if !valid_host(host) {
        return Err("INVALID_URL");
    }
    let mut command = std::process::Command::new(std::env::current_exe().map_err(|_| "DNS")?);
    command.arg(ARGUMENT);
    run_async_configured(command, host.as_bytes().to_vec(), |_| {}, Some(root)).await
}
pub(crate) async fn resolve(host: &str) -> Result<Vec<IpAddr>, &'static str> {
    if !valid_host(host) {
        return Err("INVALID_URL");
    }
    #[cfg(windows)]
    {
        let mut command = std::process::Command::new(std::env::current_exe().map_err(|_| "DNS")?);
        command.arg(ARGUMENT);
        run_async(command, host.as_bytes().to_vec()).await
    }
    // No unbounded in-process fallback on another platform.
    #[cfg(not(windows))]
    {
        Err("DNS")
    }
}

#[cfg(all(windows, test))]
fn run_command(
    command: std::process::Command,
    input: &[u8],
    cancelled: &AtomicBool,
    budget: Duration,
    started: impl FnMut(u32),
) -> Result<Vec<u8>, String> {
    run_command_configured(command, input, cancelled, budget, started, None)
}
#[cfg(windows)]
fn run_command_configured(
    mut command: std::process::Command,
    input: &[u8],
    cancelled: &AtomicBool,
    budget: Duration,
    mut started: impl FnMut(u32),
    pause_root: Option<std::path::PathBuf>,
) -> Result<Vec<u8>, String> {
    use std::{sync::mpsc, thread, time::Instant};
    if input.len() > MAX_INPUT {
        return Err("DNS_PROTOCOL".into());
    }
    if cancelled.load(Ordering::SeqCst) {
        return Err("CANCELLED".into());
    }
    let began = Instant::now();
    let mut owned = crate::desktop_worker_host_v1::start(&mut command, MEMORY)?;
    started(owned.child.id());
    #[cfg(not(debug_assertions))]
    let _ = pause_root;
    #[cfg(debug_assertions)]
    if let Some(root) = pause_root {
        // Stdin stays open and empty. The real protected self-exe blocks reading
        // its input, so this acceptance point cannot execute system DNS.
        let mut marker = std::fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(root.join(format!("web-dns-input-{}.reached", owned.child.id())))
            .map_err(|_| "DNS_ACCEPTANCE_MARKER")?;
        marker
            .write_all(owned.child.id().to_string().as_bytes())
            .and_then(|_| marker.sync_all())
            .map_err(|_| "DNS_ACCEPTANCE_MARKER")?;
        while !cancelled.load(Ordering::SeqCst) && began.elapsed() < budget {
            thread::sleep(Duration::from_millis(10));
        }
        return Err(if cancelled.load(Ordering::SeqCst) {
            "CANCELLED"
        } else {
            "WORKER_TIMEOUT"
        }
        .into());
        // Owned Job/child Drop kills and waits before the blocking helper exits.
    }
    let result = (|| {
        let mut pipe = owned.child.stdin.take().ok_or("DNS_PROTOCOL")?;
        let stdout = owned.child.stdout.take().ok_or("DNS_PROTOCOL")?;
        let input = input.to_vec();
        let writer = thread::spawn(move || pipe.write_all(&input));
        let (send, receive) = mpsc::sync_channel(1);
        let reader = thread::spawn(move || {
            let mut data = Vec::new();
            let result = stdout
                .take((MAX_OUTPUT + 1) as u64)
                .read_to_end(&mut data)
                .map_err(|_| "DNS_PROTOCOL".to_owned())
                .and_then(|_| {
                    if data.len() > MAX_OUTPUT {
                        Err("OUTPUT_TOO_LARGE".into())
                    } else {
                        Ok(data)
                    }
                });
            let _ = send.send(result);
        });
        let answer = (|| {
            let mut output = None;
            loop {
                if cancelled.load(Ordering::SeqCst) {
                    return Err("CANCELLED".into());
                }
                if began.elapsed() >= budget {
                    return Err("WORKER_TIMEOUT".into());
                }
                if output.is_none() {
                    if let Ok(result) = receive.try_recv() {
                        output = Some(result?);
                    }
                }
                if let Some(status) = owned.child.try_wait().map_err(|_| "WORKER_EXIT_FAILED")? {
                    if !status.success() {
                        return Err("WORKER_EXIT_FAILED".into());
                    }
                    return output.take().map(Ok).unwrap_or_else(|| {
                        receive.recv().map_err(|_| "DNS_PROTOCOL".to_owned())?
                    });
                }
                thread::sleep(Duration::from_millis(10));
            }
        })();
        // Kill/wait before joining pipe threads, including cancellation while resolver blocks.
        drop(owned);
        let wrote = writer.join().map_err(|_| "DNS_PROTOCOL".to_owned());
        let read = reader.join().map_err(|_| "DNS_PROTOCOL".to_owned());
        let answer = answer?;
        wrote?.map_err(|_| "DNS_PROTOCOL")?;
        read?;
        Ok(answer)
    })();
    result
}

#[cfg(all(test, windows))]
mod tests {
    use super::*;
    #[cfg(debug_assertions)]
    #[test]
    fn dns_input_pause_requires_exact_marked_root_and_one_shot_empty_arm() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("nanfeng-ai-windows-acceptance.aabbccdd");
        std::fs::create_dir(&root).unwrap();
        assert_eq!(acceptance_pause_root(&root).unwrap(), None);
        let arm = root.join(".web-dns-input-arm");
        std::fs::write(&arm, "").unwrap();
        assert!(acceptance_pause_root(&root).is_err());
        assert!(arm.exists());
        std::fs::write(root.join(crate::desktop_windows_acceptance::MARKER), r#"{"schemaVersion":1,"identifier":"com.nanzhufeng.ai.desktop.windowsacceptance","runId":"aabbccdd"}"#).unwrap();
        std::fs::write(&arm, "nonempty").unwrap();
        assert!(acceptance_pause_root(&root).is_err());
        std::fs::write(&arm, "").unwrap();
        let work = root.join("work-area");
        std::fs::create_dir(&work).unwrap();
        assert_eq!(acceptance_pause_root(&work).unwrap(), Some(root));
        assert!(!arm.exists());
        assert_eq!(acceptance_pause_root(&work).unwrap(), None);
    }
    #[cfg(debug_assertions)]
    #[test]
    fn dns_input_pause_writes_no_input_and_budget_still_reaps_the_owned_child() {
        let temp = tempfile::tempdir().unwrap();
        let mut handle = std::ptr::null_mut();
        let mut child_id = 0;
        let result = run_command_configured(
            command("$v=[Console]::In.ReadToEnd();if($v.Length){exit 3}"),
            b"public.example.org",
            &AtomicBool::new(false),
            Duration::from_millis(250),
            |pid| {
                child_id = pid;
                handle = unsafe {
                    windows_sys::Win32::System::Threading::OpenProcess(0x00100000, 0, pid)
                };
            },
            Some(temp.path().to_owned()),
        );
        assert_eq!(result.unwrap_err(), "WORKER_TIMEOUT");
        assert!(!handle.is_null());
        let wait = unsafe { windows_sys::Win32::System::Threading::WaitForSingleObject(handle, 0) };
        unsafe {
            windows_sys::Win32::Foundation::CloseHandle(handle);
        }
        assert_eq!(wait, 0);
        let files: Vec<_> = std::fs::read_dir(temp.path())
            .unwrap()
            .map(|x| x.unwrap().path())
            .collect();
        assert_eq!(files.len(), 1);
        let pid: u32 = std::fs::read_to_string(&files[0]).unwrap().parse().unwrap();
        assert_eq!(pid, child_id);
    }
    fn command(script: &str) -> std::process::Command {
        let path = std::path::PathBuf::from(std::env::var_os("SystemRoot").unwrap())
            .join("System32/WindowsPowerShell/v1.0/powershell.exe");
        let mut c = std::process::Command::new(path);
        c.args(["-NoProfile", "-NonInteractive", "-Command", script]);
        c
    }
    #[test]
    fn dns_owned_timeout_cancel_and_output_limit_reap_process() {
        for (script, cancel, budget, expected) in [
            (
                "[Threading.Thread]::Sleep(60000)",
                false,
                Duration::from_millis(250),
                "WORKER_TIMEOUT",
            ),
            (
                "[Threading.Thread]::Sleep(60000)",
                true,
                BUDGET,
                "CANCELLED",
            ),
            (
                "[Console]::Out.Write(('x'*4097));[Threading.Thread]::Sleep(60000)",
                false,
                BUDGET,
                "OUTPUT_TOO_LARGE",
            ),
        ] {
            let flag = AtomicBool::new(false);
            let mut handle = None;
            let result = run_command(command(script), b"example.org", &flag, budget, |pid| {
                let h = unsafe {
                    windows_sys::Win32::System::Threading::OpenProcess(
                        windows_sys::Win32::System::Threading::PROCESS_SYNCHRONIZE,
                        0,
                        pid,
                    )
                };
                assert!(!h.is_null());
                handle = Some(crate::desktop_worker_host_v1::Handle(h));
                if cancel {
                    flag.store(true, Ordering::SeqCst);
                }
            });
            assert_eq!(result.unwrap_err(), expected);
            assert_eq!(
                unsafe {
                    windows_sys::Win32::System::Threading::WaitForSingleObject(
                        handle.as_ref().unwrap().0,
                        1000,
                    )
                },
                0
            );
        }
    }
    #[test]
    fn dns_child_output_addresses_are_revalidated_and_host_input_is_strict() {
        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        for (output, ok) in [
            ("{\"Ok\":[\"8.8.8.8\",\"2001:4860:4860::8888\"]}", true),
            ("{\"Ok\":[\"8.8.8.8\",\"127.0.0.1\"]}", false),
            ("{\"Ok\":[]}", false),
            ("{\"Ok\":[\"8.8.8.8\"],\"extra\":1}", false),
        ] {
            let script = format!("[Console]::Out.Write('{}')", output);
            assert_eq!(
                rt.block_on(run_async(command(&script), b"example.org".to_vec()))
                    .is_ok(),
                ok
            );
        }
        for host in [
            "localhost",
            "127.0.0.1",
            "example.org/path",
            "example.org?x=y",
            "user@example.org",
            "example.org:443",
            "Example.org",
            "example.org\n",
        ] {
            assert!(!valid_host(host), "{host}");
        }
    }
    #[test]
    fn dns_future_drop_signals_owned_blocking_process_cleanup() {
        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        rt.block_on(async {
            let (tx, rx) = std::sync::mpsc::channel();
            let task = tokio::spawn(run_async_started(
                command("[Threading.Thread]::Sleep(60000)"),
                b"example.org".to_vec(),
                move |pid| {
                    tx.send(pid).unwrap();
                },
            ));
            let pid = tokio::time::timeout(Duration::from_secs(3), async {
                loop {
                    if let Ok(pid) = rx.try_recv() {
                        break pid;
                    }
                    tokio::time::sleep(Duration::from_millis(10)).await;
                }
            })
            .await
            .unwrap();
            let h = unsafe {
                windows_sys::Win32::System::Threading::OpenProcess(
                    windows_sys::Win32::System::Threading::PROCESS_SYNCHRONIZE,
                    0,
                    pid,
                )
            };
            assert!(!h.is_null());
            let h = crate::desktop_worker_host_v1::Handle(h);
            task.abort();
            assert!(task.await.unwrap_err().is_cancelled());
            assert_eq!(
                unsafe { windows_sys::Win32::System::Threading::WaitForSingleObject(h.0, 1000) },
                0
            );
        });
    }
}
