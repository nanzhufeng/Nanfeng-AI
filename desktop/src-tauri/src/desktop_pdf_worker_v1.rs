//! PDF parsing in an owned process, before any app setup. No external path/secret arguments.
use crate::desktop_pdf_text_knowledge_v1::{self as adapter, Parsed};
use serde::{Deserialize, Serialize};
use std::io::{Read, Write};

const ARGUMENT: &str = "--nanfeng-pdf-text-worker-v1";
const MAX_OUTPUT: usize = 2 * 1024 * 1024;
const MEMORY_LIMIT: usize = 256 * 1024 * 1024;
const TIME_LIMIT: std::time::Duration = std::time::Duration::from_secs(30);

pub(crate) fn validate_artifacts(bytes: &[u8], parsed: &Parsed) -> Result<(), String> {
    let bad = || "WORKER_PROTOCOL".to_owned();
    if parsed.source_sha256 != crate::sha256(bytes) || parsed.pages.is_empty() || parsed.pages.len()>adapter::MAX_PAGES
        || parsed.extraction_version!=adapter::EXTRACTION_VERSION || parsed.candidate_version!=adapter::CANDIDATE_VERSION {
        return Err(bad());
    }
    let mut total=0;let mut item_index=0;
    for (index,page) in parsed.pages.iter().enumerate() {
        let count=page.text.chars().count();
        if page.page_number!=index as u32+1 || page.code_point_count!=count || count>adapter::MAX_PAGE_TEXT
            || page.text_sha256!=crate::sha256(page.text.as_bytes()) || page.extraction_version!=adapter::EXTRACTION_VERSION {
            return Err(bad());
        }
        crate::require_safe_memory_body(&page.text).map_err(|_| bad())?;
        total+=count;
        if !page.text.is_empty() {
            let item=parsed.items.get(item_index).ok_or_else(bad)?;
            let title:String=page.text.lines().find(|l|!l.trim().is_empty()).unwrap_or("PDF 文本资料").trim().chars().take(120).collect();
            if item.ordinal!=item_index || item.page_number!=page.page_number || item.content.body!=page.text
                || item.content.title!=title || !item.content.tags.is_empty() || item.candidate_version!=adapter::CANDIDATE_VERSION
                || item.candidate_sha256!=crate::sha256(format!("{}\\n{}",page.page_number,page.text).as_bytes())
                || crate::desktop_markdown_knowledge_v1::validate_candidate(item.content.clone()).map_err(|_|bad())?!=item.content {
                return Err(bad());
            }
            item_index+=1;
        }
    }
    let joined=parsed.pages.iter().map(|p|format!("{}:{}",p.page_number,p.text_sha256)).collect::<Vec<_>>().join("|");
    if total>adapter::MAX_TOTAL_TEXT || item_index==0 || item_index!=parsed.items.len() || parsed.extracted_sha256!=crate::sha256(joined.as_bytes()) {return Err(bad());}
    Ok(())
}

#[derive(Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "camelCase", deny_unknown_fields)]
enum Frame {
    Progress { completed: usize, total: usize },
    Final { result: Result<Parsed, String> },
}

fn write_frame(output: &mut impl Write, frame: &Frame) -> Result<(), String> {
    let mut bytes = serde_json::to_vec(frame).map_err(|_| "WORKER_PROTOCOL")?;
    if bytes.len() >= MAX_OUTPUT { return Err("OUTPUT_TOO_LARGE".into()); }
    bytes.push(b'\n');
    output.write_all(&bytes).and_then(|_| output.flush()).map_err(|_| "INTERRUPTED".into())
}

pub(crate) fn run_if_requested() -> bool {
    let args: Vec<_> = std::env::args_os().skip(1).collect();
    if !args.iter().any(|arg| arg == ARGUMENT) { return false; }
    if args.len() != 1 {
        let _ = write_frame(&mut std::io::stdout().lock(), &Frame::Final { result: Err("WORKER_PROTOCOL".into()) });
        return true;
    }
    if !limits_present() {
        let _ = write_frame(&mut std::io::stdout().lock(), &Frame::Final { result: Err("WORKER_LIMIT_UNAVAILABLE".into()) });
        return true;
    }
    let mut input = Vec::new();
    let result = std::io::stdin().lock().take((adapter::MAX_BYTES + 1) as u64).read_to_end(&mut input)
        .map_err(|_| "PRIVATE_READ_FAILED".to_owned()).and_then(|_| {
            if input.len() > adapter::MAX_BYTES { return Err("TOO_LARGE".into()); }
            adapter::parse("selected.pdf", "application/pdf", &input, |completed, total| {
                write_frame(&mut std::io::stdout().lock(), &Frame::Progress { completed, total })
            })
        });
    // No app logging is initialized in this mode. All data goes only to the owning pipe.
    let _ = write_frame(&mut std::io::stdout().lock(), &Frame::Final { result });
    true
}

pub(crate) fn limits_present() -> bool {
    #[cfg(windows)] {
        use windows_sys::Win32::System::JobObjects::*;
        let mut info = JOBOBJECT_EXTENDED_LIMIT_INFORMATION::default();
        let ok = unsafe { QueryInformationJobObject(std::ptr::null_mut(), JobObjectExtendedLimitInformation,
            (&mut info as *mut _) as _, std::mem::size_of::<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>() as u32, std::ptr::null_mut()) };
        ok != 0 && info.ProcessMemoryLimit > 0 && info.ProcessMemoryLimit <= MEMORY_LIMIT
            && info.BasicLimitInformation.ActiveProcessLimit == 1
            && info.BasicLimitInformation.LimitFlags & (JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE | JOB_OBJECT_LIMIT_PROCESS_MEMORY | JOB_OBJECT_LIMIT_ACTIVE_PROCESS)
                == JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE | JOB_OBJECT_LIMIT_PROCESS_MEMORY | JOB_OBJECT_LIMIT_ACTIVE_PROCESS
    }
    #[cfg(not(windows))] { false }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Receipt {
    pub pid: u32,
    pub peak_committed_bytes: usize,
    pub elapsed_ms: u128,
    pub memory_limit_bytes: usize,
}

#[cfg(windows)]
mod host {
    use super::*;
    use std::{io::BufRead, process::Command, sync::mpsc, thread, time::Instant};
    use crate::desktop_worker_host_v1::{start, peak, Owned};
    #[cfg(test)] use crate::desktop_worker_host_v1::Handle;
    pub(super) fn decode(bytes: &[u8], mut progress: impl FnMut(usize, usize) -> Result<(), String>) -> Result<(Parsed, Receipt), String> {
        adapter::preflight("selected.pdf", "application/pdf", bytes)?;
        let mut command = Command::new(std::env::current_exe().map_err(|_| "WORKER_START_FAILED")?);
        command.arg(ARGUMENT);
        decode_command(command,bytes,&mut progress,TIME_LIMIT)
    }

    fn decode_command(mut command: Command, bytes: &[u8], mut progress: impl FnMut(usize, usize) -> Result<(), String>, budget: std::time::Duration) -> Result<(Parsed, Receipt), String> {
        let began = Instant::now();
        let mut owned = start(&mut command, MEMORY_LIMIT)?;
        let pid = owned.child.id();
        let mut input = owned.child.stdin.take().ok_or("WORKER_START_FAILED")?;
        let output = owned.child.stdout.take().ok_or("WORKER_START_FAILED")?;
        let data = bytes.to_vec();
        let writer = thread::spawn(move || input.write_all(&data));
        let (send, receive) = mpsc::sync_channel(4);
        let reader = thread::spawn(move || {
            let mut reader = std::io::BufReader::new(output.take((MAX_OUTPUT + 1) as u64));
            let mut consumed = 0;
            for _ in 0..170 {
                let mut line = Vec::new();
                match reader.read_until(b'\n', &mut line) {
                    Ok(0) => return,
                    Ok(n) => consumed += n,
                    Err(_) => { let _ = send.send(Err("WORKER_PROTOCOL".to_owned())); return; }
                }
                if consumed > MAX_OUTPUT { let _ = send.send(Err("OUTPUT_TOO_LARGE".to_owned())); return; }
                let message = serde_json::from_slice::<Frame>(&line).map_err(|_| "WORKER_PROTOCOL".to_owned());
                if send.send(message).is_err() { return; }
            }
            let _ = send.send(Err("WORKER_PROTOCOL".to_owned()));
        });
        let mut completed = 0;
        let mut total_seen = 0;
        let mut final_result = None;
        let result: Result<(Parsed, Receipt), String> = (|| {
            loop {
                progress(completed, total_seen)?;
                if began.elapsed() >= budget { return Err("WORKER_TIMEOUT".into()); }
                while let Ok(message) = receive.try_recv() {
                    match message? {
                        Frame::Progress { completed: next, total } => {
                            if final_result.is_some() || next < completed || next > total || total > adapter::MAX_PAGES
                                || (total_seen != 0 && total_seen != total) { return Err("WORKER_PROTOCOL".into()); }
                            completed = next; total_seen = total;
                            progress(completed, total_seen)?;
                        }
                        Frame::Final { result } => {
                            if final_result.is_some() { return Err("WORKER_PROTOCOL".into()); }
                            final_result = Some(result);
                        }
                    }
                }
                if let Some(status) = owned.child.try_wait().map_err(|_| "WORKER_EXIT_FAILED")? {
                    // Process exit may race its final pipe frame. Drain the reader before adjudication.
                    for message in receive.iter() {
                        match message? {
                            Frame::Final { result } if final_result.is_none() => final_result = Some(result),
                            Frame::Progress { completed: next, total } if final_result.is_none() && next >= completed && next <= total && total <= adapter::MAX_PAGES && (total_seen==0 || total_seen==total) => {completed=next;total_seen=total;progress(next,total)?;}
                            _ => return Err("WORKER_PROTOCOL".into()),
                        }
                    }
                    if !status.success() { return Err("WORKER_EXIT_FAILED".into()); }
                    let parsed = final_result.take().ok_or("WORKER_PROTOCOL")??;
                    if parsed.pages.len() != total_seen || completed != total_seen { return Err("WORKER_PROTOCOL".into()); }
                    validate_artifacts(bytes,&parsed)?;
                    return Ok((parsed, Receipt {pid, peak_committed_bytes:peak(&owned.job)?, elapsed_ms:began.elapsed().as_millis(),memory_limit_bytes:MEMORY_LIMIT}));
                }
                thread::sleep(std::time::Duration::from_millis(20));
            }
        })();
        drop(receive);
        drop(owned); // Every failure kills/waits this exact owned process; job closes all its handles.
        let wrote = writer.join().map_err(|_| "WORKER_PROTOCOL");
        let read = reader.join().map_err(|_| "WORKER_PROTOCOL");
        let value = result?;
        wrote?.map_err(|_| "PRIVATE_READ_FAILED")?;
        read?;
        Ok(value)
    }

    #[cfg(test)]
    mod tests {
        use super::*;
        fn powershell(script: &str) -> Command {
            let path = std::path::PathBuf::from(std::env::var_os("SystemRoot").unwrap()).join("System32/WindowsPowerShell/v1.0/powershell.exe");
            let mut command = Command::new(path);
            command.args(["-NoProfile", "-NonInteractive", "-Command", script]);
            command
        }
        fn wait(owned: &mut Owned) -> std::process::ExitStatus {
            let deadline=Instant::now()+std::time::Duration::from_secs(8);
            loop {
                if let Some(status)=owned.child.try_wait().unwrap() {return status;}
                assert!(Instant::now()<deadline,"Synthetic child did not exit within observation budget");
                thread::sleep(std::time::Duration::from_millis(20));
            }
        }
        #[test]
        fn pdf_worker_job_memory_commit_limit_is_effective() {
            let mut owned=start(&mut powershell("try { $data=[byte[]]::new(300*1024*1024); [Console]::Out.Write('UNBOUNDED'); exit 2 } catch { [Console]::Out.Write('MEMORY_REFUSED'); exit 0 }"),MEMORY_LIMIT).unwrap();
            let status=wait(&mut owned);let mut text=String::new();owned.child.stdout.take().unwrap().read_to_string(&mut text).unwrap();
            assert!(status.success());assert_eq!(text,"MEMORY_REFUSED");let used=peak(&owned.job).unwrap();
            assert!(used>0 && used<=MEMORY_LIMIT);println!("synthetic JobObject peak_commit_bytes={used} limit={MEMORY_LIMIT}");
        }
        #[test]
        fn pdf_worker_owned_process_drop_reaps_exact_child() {
            let owned=start(&mut powershell("[Threading.Thread]::Sleep(60000)"),MEMORY_LIMIT).unwrap();let pid=owned.child.id();
            let handle=unsafe { windows_sys::Win32::System::Threading::OpenProcess(windows_sys::Win32::System::Threading::PROCESS_SYNCHRONIZE,0,pid) };
            assert!(!handle.is_null());let handle=Handle(handle);drop(owned);
            assert_eq!(unsafe {windows_sys::Win32::System::Threading::WaitForSingleObject(handle.0,1000)},0);
        }
        #[test]
        fn pdf_worker_environment_does_not_inherit_business_configuration() {
            let mut command=powershell("if (Test-Path Env:NANFENG_PDF_SYNTHETIC_SENTINEL) {exit 2}; [Console]::Out.Write('ENV_ISOLATED'); exit 0");
            command.env("NANFENG_PDF_SYNTHETIC_SENTINEL","synthetic-not-a-key");
            let mut owned=start(&mut command,MEMORY_LIMIT).unwrap();assert!(wait(&mut owned).success());
            let mut text=String::new();owned.child.stdout.take().unwrap().read_to_string(&mut text).unwrap();assert_eq!(text,"ENV_ISOLATED");
        }

        fn bytes() -> Vec<u8> { adapter::tests::pdf(&["synthetic worker page"],false) }
        fn fake_reply(parsed: Parsed) -> Command {
            use base64::{Engine,engine::general_purpose::STANDARD};
            let count=parsed.pages.len();
            let output=format!("{}\n{}\n",serde_json::to_string(&Frame::Progress{completed:count,total:count}).unwrap(),serde_json::to_string(&Frame::Final{result:Ok(parsed)}).unwrap());
            let encoded=STANDARD.encode(output);
            powershell(&format!("$s=[Console]::OpenStandardInput(); $buffer=[byte[]]::new(8192); while ($s.Read($buffer,0,$buffer.Length) -gt 0) {{}}; [Console]::Out.Write([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('{encoded}'))); exit 0"))
        }
        #[test]
        fn pdf_worker_host_typed_reply_and_all_artifacts_are_verified() {
            let bytes=bytes();let expected=adapter::parse("synthetic.pdf","application/pdf",&bytes,|_,_|Ok(())).unwrap();
            let (actual,receipt)=decode_command(fake_reply(expected.clone()),&bytes,|_,_|Ok(()),TIME_LIMIT).unwrap();
            assert_eq!(actual,expected);assert!(receipt.peak_committed_bytes>0 && receipt.peak_committed_bytes<=MEMORY_LIMIT);
            let mut corrupt=expected;corrupt.pages[0].text_sha256="0".repeat(64);
            assert_eq!(decode_command(fake_reply(corrupt),&bytes,|_,_|Ok(()),TIME_LIMIT).unwrap_err(),"WORKER_PROTOCOL");
        }
        #[test]
        fn pdf_worker_host_output_cap_kills_owned_child() {
            assert_eq!(decode_command(powershell("[Console]::Out.Write(('x' * (2*1024*1024+1))); [Threading.Thread]::Sleep(60000)"),&bytes(),|_,_|Ok(()),TIME_LIMIT).unwrap_err(),"OUTPUT_TOO_LARGE");
        }
        #[test]
        fn pdf_worker_host_timeout_and_cancel_reap_owned_child() {
            assert_eq!(decode_command(powershell("[Threading.Thread]::Sleep(60000)"),&bytes(),|_,_|Ok(()),std::time::Duration::from_millis(250)).unwrap_err(),"WORKER_TIMEOUT");
            assert_eq!(decode_command(powershell("[Threading.Thread]::Sleep(60000)"),&bytes(),|_,_|Err("INTERRUPTED".into()),TIME_LIMIT).unwrap_err(),"INTERRUPTED");
        }
    }
}

pub(crate) fn decode(bytes: &[u8], progress: impl FnMut(usize, usize) -> Result<(), String>) -> Result<(Parsed, Receipt), String> {
    #[cfg(windows)] { host::decode(bytes, progress) }
    #[cfg(not(windows))] { let _ = (bytes, progress); Err("WORKER_LIMIT_UNAVAILABLE".into()) }
}
