//! Window-bound Windows Share UI for verified local attachment copies.
//!
//! The workspace owner validates the attachment identity first. This module creates one
//! independent lease copy, supplies it through `IDataTransferManagerInterop` for the actual
//! Tauri HWND. Cancellation removes the copy. Once supplied to Windows, a copy
//! survives completion and process exit because targets can open its path later.

use sha2::{Digest, Sha256};
use std::{
    cell::RefCell,
    fs::{self, OpenOptions},
    io::{Read, Write},
    os::windows::{
        fs::{MetadataExt, OpenOptionsExt},
        io::AsRawHandle,
    },
    path::{Path, PathBuf},
    sync::{
        atomic::{AtomicBool, AtomicU64, Ordering},
        Arc,
    },
};
use windows::{
    core::{factory, Interface, HSTRING},
    ApplicationModel::DataTransfer::{DataPackage, DataRequestedEventArgs, DataTransferManager},
    Foundation::TypedEventHandler,
    Storage::{IStorageItem, StorageFile},
    Win32::{
        Foundation::{HANDLE, HWND},
        Storage::FileSystem::{
            GetFileInformationByHandle, BY_HANDLE_FILE_INFORMATION, FILE_ATTRIBUTE_REPARSE_POINT,
            FILE_FLAG_OPEN_REPARSE_POINT, FILE_SHARE_READ,
        },
        UI::Shell::IDataTransferManagerInterop,
    },
};
use windows_collections::IVectorView;

static LEASE_COUNTER: AtomicU64 = AtomicU64::new(1);
const HANDOFF_MARKER: &str = ".share-handoff-v1";

fn mark_handoff(path: &Path) -> Result<(), String> {
    let marker = path.parent().ok_or("Windows 分享租约无效")?.join(HANDOFF_MARKER);
    let mut file = OpenOptions::new().write(true).create_new(true).open(marker)
        .map_err(|_| crate::json_error("Windows 分享交付租约未保存"))?;
    file.write_all(b"1").and_then(|_| file.sync_all())
        .map_err(|_| crate::json_error("Windows 分享交付租约未保存"))
}

fn trace_event(event: &str) {
    #[cfg(debug_assertions)]
    eprintln!("windows-share:{event}");
    #[cfg(not(debug_assertions))]
    let _ = event;
}

fn storage_file_path(path: &Path) -> Result<String, String> {
    let original = path.to_str().ok_or_else(|| crate::json_error("Windows 分享路径无效"))?;
    let normalized = original.replace('/', "\\");
    let native = if let Some(unc) = normalized.strip_prefix(r"\\?\UNC\") {
        format!(r"\\{unc}")
    } else if let Some(disk) = normalized.strip_prefix(r"\\?\") {
        if disk.as_bytes().get(1) != Some(&b':') || disk.as_bytes().get(2) != Some(&b'\\') {
            return Err(crate::json_error("Windows 分享路径类型不可用"));
        }
        disk.to_owned()
    } else {
        normalized
    };
    // WinRT StorageFile does not accept the verbatim prefix emitted by canonicalize.
    // Convert only the representation of this already verified lease, then confirm it
    // still resolves to the same file before giving the path to the system.
    if !Path::new(&native).is_absolute()
        || fs::canonicalize(&native).ok() != Some(fs::canonicalize(path)
            .map_err(|_| crate::json_error("Windows 分享文件不可读"))?)
    {
        return Err(crate::json_error("Windows 分享路径身份不一致"));
    }
    Ok(native)
}

struct ShareSession {
    manager: DataTransferManager,
    token: i64,
    #[cfg(debug_assertions)]
    provider_token: Option<i64>,
}

impl Drop for ShareSession {
    fn drop(&mut self) {
        let _ = self.manager.RemoveDataRequested(self.token);
        #[cfg(debug_assertions)]
        if let Some(token) = self.provider_token {
            let _ = self.manager.RemoveShareProvidersRequested(token);
        }
    }
}

thread_local! {
    // DataTransferManager is window-thread bound. Replacing this slot unregisters the
    // previous request handler without moving its COM object across apartments.
    static SHARE_SESSION: RefCell<Option<ShareSession>> = const { RefCell::new(None) };
}

// Opt-in observation of the real subclass chain. This records no paths, payloads,
// HWNDs or controller pointers and never consumes or synthesizes window messages.
#[cfg(debug_assertions)]
unsafe extern "system" fn close_message_probe(
    hwnd: HWND, message: u32,
    wparam: windows::Win32::Foundation::WPARAM,
    lparam: windows::Win32::Foundation::LPARAM,
    subclass: usize, reenter_focus: usize,
) -> windows::Win32::Foundation::LRESULT {
    use windows::Win32::UI::Shell::{DefSubclassProc, RemoveWindowSubclass};
    let name = match message {
        0x0010 => Some("close"), 0x0002 => Some("destroy"),
        0x0082 => Some("nc-destroy"), 0x0007 => Some("set-focus"),
        0x0008 => Some("kill-focus"), 0x0005 => Some("size"),
        0x0003 => Some("move"), 0x0216 => Some("moving"),
        0x0231 => Some("enter-size-move"),
        // wry 0.55.1's parent teardown message; observation only.
        0x0465 => Some("wry-parent-destroy"), _ => None,
    };
    if let Some(name) = name { eprintln!("windows-share:close-probe:before:{name}"); }
    let result = DefSubclassProc(hwnd, message, wparam, lparam);
    if let Some(name) = name { eprintln!("windows-share:close-probe:after:{name}"); }
    // Explicit fault experiment, separate from passive observation. It exercises
    // the dependency boundary after controller teardown but before the outer
    // WM_DESTROY returns; no production or ordinary acceptance run enables it.
    if message == 0x0465 && reenter_focus == 1 {
        trace_event("close-probe:injected-set-focus-after-parent-destroy");
        windows_sys::Win32::UI::WindowsAndMessaging::SendMessageW(hwnd.0, 0x0007, 0, 0);
    }
    if message == 0x0082 { let _ = RemoveWindowSubclass(hwnd, Some(close_message_probe), subclass); }
    result
}

#[cfg(debug_assertions)]
fn install_close_message_probe(window: &tauri::WebviewWindow, hwnd: HWND) -> Result<(), String> {
    use tauri::Manager;
    use windows::Win32::UI::Shell::SetWindowSubclass;
    let Some(root) = std::env::var_os(crate::desktop_windows_acceptance::ROOT_ENV).map(PathBuf::from) else { return Ok(()); };
    let arm = root.join(".share-close-message-probe");
    if !arm.exists() { return Ok(()); }
    crate::desktop_windows_acceptance::resolve(Some(&root), true,
        &window.app_handle().config().identifier, root.parent().ok_or("隔离分享根无效")?)?;
    let metadata = fs::symlink_metadata(&arm).map_err(|_| crate::json_error("隔离关闭探针不可读"))?;
    if !metadata.is_file() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 || metadata.len() != 0 {
        return Err(crate::json_error("隔离关闭探针标记无效"));
    }
    let focus_arm = root.join(".share-close-reentrant-focus-probe");
    let reenter_focus = if focus_arm.exists() {
        let metadata = fs::symlink_metadata(&focus_arm).map_err(|_| crate::json_error("隔离重入探针不可读"))?;
        if !metadata.is_file() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 || metadata.len() != 0 {
            return Err(crate::json_error("隔离重入探针标记无效"));
        }
        1
    } else { 0 };
    if !unsafe { SetWindowSubclass(hwnd, Some(close_message_probe), 0x4E464143, reenter_focus) }.as_bool() {
        return Err(crate::json_error("隔离关闭观察探针无法注册"));
    }
    fs::remove_file(arm).map_err(|_| crate::json_error("隔离关闭探针标记未消费"))?;
    if reenter_focus == 1 { fs::remove_file(focus_arm).map_err(|_| crate::json_error("隔离重入探针标记未消费"))?; }
    trace_event("close-probe-installed");
    Ok(())
}

fn safe_leaf(display_name: &str) -> String {
    let leaf = Path::new(display_name)
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or("附件");
    let mut safe = leaf
        .chars()
        .map(|character| {
            if character.is_control()
                || matches!(
                    character,
                    '<' | '>' | ':' | '"' | '/' | '\\' | '|' | '?' | '*'
                )
            {
                '-'
            } else {
                character
            }
        })
        .take(160)
        .collect::<String>();
    while safe.ends_with([' ', '.']) {
        safe.pop();
    }
    let reserved_stem = safe
        .split('.')
        .next()
        .unwrap_or_default()
        .trim_end_matches([' ', '.'])
        .to_ascii_uppercase();
    let reserved = matches!(reserved_stem.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        || reserved_stem
            .strip_prefix("COM")
            .or_else(|| reserved_stem.strip_prefix("LPT"))
            .and_then(|suffix| suffix.parse::<u8>().ok())
            .is_some_and(|number| (1..=9).contains(&number));
    if safe.is_empty() {
        "附件".to_owned()
    } else if reserved {
        format!("_{safe}")
    } else {
        safe
    }
}

fn ensure_plain_directory(path: &Path, message: &'static str) -> Result<(), String> {
    match fs::create_dir(path) {
        Ok(()) => (),
        Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => (),
        Err(_) => return Err(crate::json_error(message)),
    }
    let metadata = fs::symlink_metadata(path).map_err(|_| crate::json_error(message))?;
    if !metadata.is_dir() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 {
        return Err(crate::json_error("Windows 分享目录不能是链接或重解析点"));
    }
    Ok(())
}

fn single_link_file(file: &fs::File) -> bool {
    let mut information = BY_HANDLE_FILE_INFORMATION::default();
    (unsafe { GetFileInformationByHandle(HANDLE(file.as_raw_handle()), &mut information).is_ok() })
        && information.nNumberOfLinks == 1
}

pub(crate) fn prepare_attachment(
    root: &Path,
    source: &Path,
    display_name: &str,
    byte_count: u64,
    digest: &str,
) -> Result<PathBuf, String> {
    if !root.is_absolute() || !source.is_absolute() || !crate::is_sha256(digest) {
        return Err(crate::json_error("Windows 分享副本身份无效"));
    }
    let outbox = root.join("share-outbox");
    ensure_plain_directory(&outbox, "无法创建 Windows 分享目录")?;
    let digest_directory = outbox.join(&digest[..16]);
    ensure_plain_directory(&digest_directory, "无法创建 Windows 分享附件目录")?;

    let mut lease_directory = None;
    for _ in 0..16 {
        let sequence = LEASE_COUNTER.fetch_add(1, Ordering::Relaxed);
        let candidate = digest_directory.join(format!("lease-{}-{sequence}", std::process::id()));
        match fs::create_dir(&candidate) {
            Ok(()) => {
                lease_directory = Some(candidate);
                break;
            }
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => continue,
            Err(_) => return Err(crate::json_error("无法创建 Windows 分享租约")),
        }
    }
    let lease_directory =
        lease_directory.ok_or_else(|| crate::json_error("Windows 分享租约冲突"))?;
    let destination = lease_directory.join(safe_leaf(display_name));
    let result = (|| {
        let mut input = OpenOptions::new()
            .read(true)
            .share_mode(FILE_SHARE_READ.0)
            .custom_flags(FILE_FLAG_OPEN_REPARSE_POINT.0)
            .open(source)
            .map_err(|_| crate::json_error("本地附件正在使用或不可读"))?;
        let source_metadata = input
            .metadata()
            .map_err(|_| crate::json_error("本地附件不可读"))?;
        if !source_metadata.is_file()
            || source_metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0
            || source_metadata.len() != byte_count
        {
            return Err(crate::json_error("本地附件校验不一致"));
        }
        let mut output = OpenOptions::new()
            .write(true)
            .create_new(true)
            .share_mode(0)
            .open(&destination)
            .map_err(|_| crate::json_error("无法建立 Windows 分享副本"))?;
        if !single_link_file(&output) {
            return Err(crate::json_error("Windows 分享副本必须是独立文件"));
        }
        let mut hasher = Sha256::new();
        let mut copied = 0_u64;
        let mut buffer = [0_u8; 64 * 1024];
        loop {
            let count = input
                .read(&mut buffer)
                .map_err(|_| crate::json_error("本地附件读取失败"))?;
            if count == 0 {
                break;
            }
            copied = copied.saturating_add(count as u64);
            hasher.update(&buffer[..count]);
            output
                .write_all(&buffer[..count])
                .map_err(|_| crate::json_error("Windows 分享副本写入失败"))?;
        }
        output
            .sync_all()
            .map_err(|_| crate::json_error("Windows 分享副本未能持久化"))?;
        if copied != byte_count || format!("{:x}", hasher.finalize()) != digest {
            return Err(crate::json_error("本地附件在分享准备期间发生变化"));
        }
        Ok(())
    })();
    if let Err(error) = result {
        let _ = fs::remove_file(&destination);
        let _ = fs::remove_dir(&lease_directory);
        return Err(error);
    }
    Ok(destination)
}

fn cleanup_lease(path: &Path) {
    let _ = fs::remove_file(path);
    if let Some(lease) = path.parent() {
        let marker = lease.join(HANDOFF_MARKER);
        if let Ok(metadata) = fs::symlink_metadata(&marker) {
            if metadata.is_file() && metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 == 0
                && metadata.len() == 1 && fs::read(&marker).ok().as_deref() == Some(b"1") {
                let _ = fs::remove_file(marker);
            }
        }
        let _ = fs::remove_dir(lease);
        if let Some(digest_directory) = lease.parent() {
            let _ = fs::remove_dir(digest_directory);
        }
    }
}

// A failed UI request is not proof that Windows never received the package.
// Only the window thread may reclaim an unoffered lease; timeout callers cannot
// delete a copy while a queued DataRequested handler is still able to offer it.
fn cleanup_failed_request(path: &Path) {
    if let Some(lease) = path.parent() {
        let _ = cleanup_unoffered_tree(lease);
    }
}

pub(crate) struct RetainedCopies {
    pub count: u64,
    pub bytes: u64,
    pub fingerprint: String,
    files: Vec<PathBuf>,
}

fn lease_process_may_be_running(pid: u32) -> bool {
    use windows::Win32::{Foundation::CloseHandle, System::Threading::{GetExitCodeProcess, OpenProcess, PROCESS_QUERY_LIMITED_INFORMATION}};
    match unsafe { OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid) } {
        Ok(handle) => {
            let mut code = 259_u32;
            let queried = unsafe { GetExitCodeProcess(handle, &mut code) }.is_ok();
            let _ = unsafe { CloseHandle(handle) };
            !queried || code == 259
        },
        // ERROR_INVALID_PARAMETER means that this process ID does not exist.
        // Access denied and every other uncertainty preserve the lease.
        Err(error) => error.code().0 as u32 != 0x80070057,
    }
}

// Enumerate only the adapter's exact three-level layout. Unknown trees are
// preserved and reported instead of recursively treating them as cache.
pub(crate) fn retained_copies(root: &Path, previous_process_only: bool) -> Result<RetainedCopies, String> {
    let outbox = root.join("share-outbox");
    let mut files = Vec::new();
    let mut bytes = 0_u64;
    if !root.is_absolute() { return Err(crate::json_error("Windows 分享数据根无效")); }
    let plain_dir = |path: &Path| -> Result<(), String> {
        let metadata = fs::symlink_metadata(path).map_err(|_| crate::json_error("Windows 分享目录不可读"))?;
        if !metadata.is_dir() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 {
            return Err(crate::json_error("Windows 分享目录身份无效；副本已保留"));
        }
        Ok(())
    };
    match fs::symlink_metadata(&outbox) {
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {},
        _ => {
            plain_dir(&outbox)?;
            for digest in fs::read_dir(&outbox).map_err(|_| crate::json_error("Windows 分享目录不可读"))? {
                let digest = digest.map_err(|_| crate::json_error("Windows 分享目录不可读"))?;
                let name = digest.file_name().to_string_lossy().into_owned();
                if name.len() != 16 || !name.bytes().all(|c| c.is_ascii_hexdigit()) {
                    return Err(crate::json_error("Windows 分享目录名称无效；副本已保留"));
                }
                plain_dir(&digest.path())?;
                for lease in fs::read_dir(digest.path()).map_err(|_| crate::json_error("Windows 分享租约不可读"))? {
                    let lease = lease.map_err(|_| crate::json_error("Windows 分享租约不可读"))?;
                    plain_dir(&lease.path())?;
                    let name = lease.file_name().to_string_lossy().into_owned();
                    let parts = name.split('-').collect::<Vec<_>>();
                    if parts.len() != 3 || parts[0] != "lease" || parts[1].parse::<u32>().is_err() || parts[2].parse::<u64>().is_err() {
                        return Err(crate::json_error("Windows 分享租约名称无效；副本已保留"));
                    }
                    let marker = lease.path().join(HANDOFF_MARKER);
                    if !marker.exists() { continue; }
                    let metadata = fs::symlink_metadata(&marker).map_err(|_| crate::json_error("Windows 分享交付标记不可读"))?;
                    if !metadata.is_file() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 || fs::read(&marker).ok().as_deref() != Some(b"1") {
                        return Err(crate::json_error("Windows 分享交付标记无效；副本已保留"));
                    }
                    let mut payloads = Vec::new();
                    for entry in fs::read_dir(lease.path()).map_err(|_| crate::json_error("Windows 分享租约不可读"))? {
                        let path = entry.map_err(|_| crate::json_error("Windows 分享租约不可读"))?.path();
                        if path == marker { continue; }
                        let metadata = fs::symlink_metadata(&path).map_err(|_| crate::json_error("Windows 分享副本不可读"))?;
                        if !metadata.is_file() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 {
                            return Err(crate::json_error("Windows 分享副本身份无效；副本已保留"));
                        }
                        payloads.push((path, metadata.len()));
                    }
                    if payloads.len() != 1 { return Err(crate::json_error("Windows 分享租约内容无效；副本已保留")); }
                    if previous_process_only && lease_process_may_be_running(parts[1].parse::<u32>().unwrap()) { continue; }
                    let (path, size) = payloads.pop().unwrap();
                    files.push(path);
                    bytes = bytes.saturating_add(size);
                }
            }
        }
    }
    files.sort();
    let mut hasher = Sha256::new();
    for path in &files {
        hasher.update(path.strip_prefix(&outbox).map_err(|_| crate::json_error("Windows 分享副本范围无效"))?.to_string_lossy().as_bytes());
        let mut file = OpenOptions::new().read(true).share_mode(FILE_SHARE_READ.0).custom_flags(FILE_FLAG_OPEN_REPARSE_POINT.0).open(path)
            .map_err(|_| crate::json_error("Windows 分享副本正在使用或不可读"))?;
        if !single_link_file(&file) { return Err(crate::json_error("Windows 分享副本不是独立文件")); }
        let mut buffer = [0; 65536];
        loop { let n = file.read(&mut buffer).map_err(|_| crate::json_error("Windows 分享副本不可读"))?; if n == 0 { break; } hasher.update(&buffer[..n]); }
    }
    Ok(RetainedCopies { count: files.len() as u64, bytes, fingerprint: format!("{:x}", hasher.finalize()), files })
}

pub(crate) fn delete_retained_copies(root: &Path, fingerprint: &str) -> Result<(), String> {
    let copies = retained_copies(root, true)?;
    if copies.fingerprint != fingerprint { return Err(crate::json_error("Windows 分享副本已变化，请重新预览")); }
    let mut locked = Vec::new();
    let mut hasher = Sha256::new();
    for path in &copies.files {
        let lease = path.parent().ok_or_else(|| crate::json_error("Windows 分享租约无效"))?;
        let open = |path: &Path| -> Result<fs::File, String> {
            // DELETE + GENERIC_READ, deny other reads/writes/deletes until the
            // fingerprint is checked and these exact handles are disposed.
            let file = OpenOptions::new().access_mode(0x00010000 | 0x80000000)
                .share_mode(0).custom_flags(FILE_FLAG_OPEN_REPARSE_POINT.0).open(path)
                .map_err(|_| crate::json_error("Windows 分享副本正在使用或不可清理"))?;
            let metadata = file.metadata().map_err(|_| crate::json_error("Windows 分享副本身份不可读"))?;
            if !metadata.is_file() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 || !single_link_file(&file) {
                return Err(crate::json_error("Windows 分享副本身份已变化；副本已保留"));
            }
            Ok(file)
        };
        let mut payload = open(path)?;
        let mut marker = open(&lease.join(HANDOFF_MARKER))?;
        let mut marker_bytes = Vec::new();
        Read::by_ref(&mut marker).take(2).read_to_end(&mut marker_bytes).map_err(|_| crate::json_error("Windows 分享交付标记不可读"))?;
        if marker_bytes != b"1" { return Err(crate::json_error("Windows 分享交付标记已变化；副本已保留")); }
        hasher.update(path.strip_prefix(root.join("share-outbox")).map_err(|_| crate::json_error("Windows 分享范围无效"))?.to_string_lossy().as_bytes());
        let mut buffer = [0; 65536];
        loop { let n = payload.read(&mut buffer).map_err(|_| crate::json_error("Windows 分享副本不可读"))?; if n == 0 {break;} hasher.update(&buffer[..n]); }
        locked.push((path.clone(), payload, marker));
    }
    if format!("{:x}", hasher.finalize()) != fingerprint { return Err(crate::json_error("Windows 分享副本已变化，请重新预览")); }
    use windows::Win32::Storage::FileSystem::{SetFileInformationByHandle, FileDispositionInfo, FILE_DISPOSITION_INFO};
    let disposition = FILE_DISPOSITION_INFO { DeleteFile: true.into() };
    for (path, payload, marker) in locked {
        for file in [&payload, &marker] {
            unsafe { SetFileInformationByHandle(HANDLE(file.as_raw_handle()), FileDispositionInfo,
                &disposition as *const _ as *const _, std::mem::size_of::<FILE_DISPOSITION_INFO>() as u32) }
                .map_err(|_| crate::json_error("Windows 分享副本清理未全部完成；剩余副本已保留"))?;
        }
        drop(payload);
        drop(marker);
        let lease = path.parent().ok_or_else(|| crate::json_error("Windows 分享租约无效"))?;
        fs::remove_dir(lease).map_err(|_| crate::json_error("Windows 分享租约无法清理"))?;
        if let Some(parent) = lease.parent() { let _ = fs::remove_dir(parent); }
    }
    Ok(())
}

// Returns false when a durable handoff still owns this subtree. Rejecting an
// invalid marker preserves it; no guessed expiry can revoke a receiver's path.
fn cleanup_unoffered_tree(path: &Path) -> Result<bool, String> {
    let marker = path.join(HANDOFF_MARKER);
    match fs::symlink_metadata(&marker) {
        Ok(metadata) => {
            if !metadata.is_file() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0
                || metadata.len() != 1 || fs::read(&marker).ok().as_deref() != Some(b"1") {
                return Err(crate::json_error("Windows 分享交付标记无效；副本已保留"));
            }
            return Ok(false);
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {},
        Err(_) => return Err(crate::json_error("Windows 分享交付标记不可读；副本已保留")),
    }
    let mut retained = false;
    for entry in fs::read_dir(path).map_err(|_| crate::json_error("Windows 分享目录不可读"))? {
        let child = entry.map_err(|_| crate::json_error("Windows 分享目录不可读"))?.path();
        let metadata = fs::symlink_metadata(&child).map_err(|_| crate::json_error("Windows 分享目录不可读"))?;
        if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 {return Err(crate::json_error("Windows 分享租约不能包含链接或重解析点"));}
        if metadata.is_dir() {retained |= !cleanup_unoffered_tree(&child)?;}
        else if metadata.is_file() {fs::remove_file(child).map_err(|_| crate::json_error("Windows 分享租约无法清理"))?;}
        else {return Err(crate::json_error("Windows 分享租约类型无效"));}
    }
    if !retained {fs::remove_dir(path).map_err(|_| crate::json_error("Windows 分享目录无法清理"))?;}
    Ok(!retained)
}

/// Exit cleanup removes only unoffered copies. Durable handoffs and suspicious
/// trees are preserved; the content-addressed `assets` owner is never traversed.
pub(crate) fn cleanup_outbox(root: &Path) -> Result<(), String> {
    if !root.is_absolute() {
        return Err(crate::json_error("Windows 分享数据根无效"));
    }
    let outbox = root.join("share-outbox");
    let metadata = match fs::symlink_metadata(&outbox) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(_) => return Err(crate::json_error("Windows 分享目录不可读")),
    };
    if !metadata.is_dir() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 {
        return Err(crate::json_error("Windows 分享目录不能是链接或重解析点"));
    }
    cleanup_unoffered_tree(&outbox).map(|_| ())
}

fn install_cleanup(package: &DataPackage, path: PathBuf, retain_completed_probe: bool) {
    let cleaned = Arc::new(AtomicBool::new(false));
    let completed_flag = Arc::clone(&cleaned);
    let completed_registration = package.ShareCompleted(&TypedEventHandler::new(move |_, _| {
        trace_event("completed");
        if !completed_flag.swap(true, Ordering::AcqRel) {
            if retain_completed_probe {
                trace_event("completed-lease-retained-probe");
            } else {
                trace_event("completed-lease-retained");
            }
        }
        Ok(())
    }));
    let canceled_flag = Arc::clone(&cleaned);
    let canceled_registration = package.ShareCanceled(&TypedEventHandler::new(move |_, _| {
        trace_event("canceled");
        if !canceled_flag.swap(true, Ordering::AcqRel) {
            cleanup_lease(&path);
        }
        Ok(())
    }));
    #[cfg(debug_assertions)]
    for (event, registration) in [("completed", completed_registration), ("canceled", canceled_registration)] {
        match registration {
            Ok(_) => eprintln!("windows-share:registered:{event}"),
            Err(error) => eprintln!("windows-share:registration-failed:{event}:{:08X}", error.code().0 as u32),
        }
    }
    #[cfg(not(debug_assertions))]
    let _ = (completed_registration, canceled_registration);
}

#[cfg(debug_assertions)]
fn take_retain_completed_probe(window: &tauri::WebviewWindow) -> Result<bool, String> {
    use tauri::Manager;
    let Some(root) = std::env::var_os(crate::desktop_windows_acceptance::ROOT_ENV).map(PathBuf::from) else { return Ok(false); };
    take_retain_completed_probe_for(&root, &window.app_handle().config().identifier)
}

#[cfg(debug_assertions)]
fn take_retain_completed_probe_for(root: &Path, identifier: &str) -> Result<bool, String> {
    let arm = root.join(".share-retain-completed-probe");
    if !arm.exists() { return Ok(false); }
    crate::desktop_windows_acceptance::resolve(Some(root), true, identifier, root.parent().ok_or("隔离分享根无效")?)?;
    let metadata = fs::symlink_metadata(&arm).map_err(|_| "隔离分享保留探针不可读")?;
    if !metadata.is_file() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 || metadata.len() != 0 {
        return Err(crate::json_error("隔离分享保留探针标记无效"));
    }
    fs::remove_file(arm).map_err(|_| "隔离分享保留探针未消费")?;
    Ok(true)
}

// An opt-in local receiver is compiled out of release builds. It cannot consume
// user material: identity, marked root, one-shot arm and fixed public bytes gate it.
#[cfg(debug_assertions)]
fn install_local_receiver_probe(window: &tauri::WebviewWindow, manager: &DataTransferManager, path: &Path) -> Result<Option<i64>, String> {
    use tauri::Manager;
    use windows::ApplicationModel::DataTransfer::{ShareProvider, ShareProviderHandler, ShareProvidersRequestedEventArgs};
    use windows::Storage::Streams::{DataWriter, InMemoryRandomAccessStream, RandomAccessStreamReference};
    use windows::UI::Color;
    const DIGEST: &str = "3f3fa3e4842fd4d54be489e980058c1aaf1bd89951dd0dd521e29f0b81639969";
    let Some(root) = std::env::var_os(crate::desktop_windows_acceptance::ROOT_ENV).map(PathBuf::from) else { return Ok(None); };
    let arm = root.join(".share-local-receiver-arm");
    if !arm.exists() { return Ok(None); }
    let identifier = &window.app_handle().config().identifier;
    let canonical = crate::desktop_windows_acceptance::resolve(Some(&root), true, identifier, root.parent().ok_or("隔离接收根无效")?)?
        .ok_or("隔离接收根缺失")?;
    let metadata = fs::symlink_metadata(&arm).map_err(|_| "隔离接收标记不可读")?;
    if !metadata.is_file() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 || metadata.len() != 0 {
        return Err(crate::json_error("隔离接收标记无效"));
    }
    let source = fs::canonicalize(path).map_err(|_| "隔离分享副本不可读")?;
    let bytes = fs::read(&source).map_err(|_| "隔离分享副本不可读")?;
    if !source.starts_with(canonical.join("share-outbox")) || bytes.len() != 43 || crate::sha256(&bytes) != DIGEST {
        return Err(crate::json_error("隔离接收只允许公开合成附件"));
    }
    let receipt = canonical.join(format!("share-local-received-{}.json", std::process::id()));
    if receipt.exists() { return Err(crate::json_error("隔离接收收据已存在")); }
    fs::remove_file(arm).map_err(|_| "隔离接收标记未消费")?;
    let provider_handler = TypedEventHandler::<DataTransferManager, ShareProvidersRequestedEventArgs>::new(move |_, args| {
        trace_event("providers-requested");
        let source = source.clone();
        let receipt = receipt.clone();
        let callback = ShareProviderHandler::new(move |operation| {
            trace_event("local-receiver-invoked");
            let operation = operation.ok()?;
            let items = operation.Data()?.GetStorageItemsAsync()?.get()?;
            if items.Size()? != 1 { return Err(windows::core::Error::from_hresult(windows::core::HRESULT(0x80070057u32 as i32))); }
            let file: StorageFile = items.GetAt(0)?.cast()?;
            let received_path = PathBuf::from(file.Path()?.to_string());
            let received = fs::read(&received_path).map_err(|_| windows::core::Error::from_win32())?;
            if fs::canonicalize(&received_path).ok() != Some(source.clone()) || received.len() != 43 || crate::sha256(&received) != DIGEST {
                return Err(windows::core::Error::from_hresult(windows::core::HRESULT(0x80070057u32 as i32)));
            }
            let summary = serde_json::json!({"layer":"WINRT_CUSTOM_LOCAL_SHARE_PROVIDER", "bytes":received.len(), "sha256":DIGEST, "storageItemIdentityConfirmed":true, "networkUsed":false});
            let mut output = OpenOptions::new().write(true).create_new(true).open(&receipt).map_err(|_| windows::core::Error::from_win32())?;
            output.write_all(summary.to_string().as_bytes()).and_then(|_| output.sync_all()).map_err(|_| windows::core::Error::from_win32())?;
            operation.ReportCompleted()?;
            trace_event("local-receiver-reported-completed");
            Ok(())
        });
        let stream = InMemoryRandomAccessStream::new()?;
        let writer = DataWriter::CreateDataWriter(&stream)?;
        // A public 1x1 PNG, held only in memory for the system provider icon.
        let png = hex::decode("89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000b49444154789c636000020000050001a5f645400000000049454e44ae426082").unwrap();
        writer.WriteBytes(&png)?;
        writer.StoreAsync()?.get()?;
        writer.DetachStream()?;
        stream.Seek(0)?;
        let icon = RandomAccessStreamReference::CreateFromStream(&stream)?;
        let provider = ShareProvider::Create(&HSTRING::from("本地合成接收验证"), &icon, Color { A:255, R:30, G:120, B:80 }, &callback)?;
        args.ok()?.Providers()?.Append(&provider)?;
        trace_event("local-provider-added");
        Ok(())
    });
    let token = manager.ShareProvidersRequested(&provider_handler).map_err(|error| {
        eprintln!("windows-share:provider-registration-failed:{:08X}", error.code().0 as u32);
        crate::json_error("隔离接收探针无法注册")
    })?;
    trace_event("provider-registered");
    Ok(Some(token))
}

// Inject real window-thread queue contention only for a one-shot, public fixture
// in the marked acceptance bundle. The production timeout remains five seconds.
#[cfg(debug_assertions)]
fn take_queue_delay_probe(window: &tauri::WebviewWindow, path: &Path) -> Result<bool, String> {
    use tauri::Manager;
    let Some(root) = std::env::var_os(crate::desktop_windows_acceptance::ROOT_ENV).map(PathBuf::from) else { return Ok(false); };
    take_queue_delay_probe_for(&root, &window.app_handle().config().identifier, path)
}

#[cfg(debug_assertions)]
fn take_queue_delay_probe_for(root: &Path, identifier: &str, path: &Path) -> Result<bool, String> {
    let arm = root.join(".share-window-queue-delay-probe");
    if !arm.exists() { return Ok(false); }
    crate::desktop_windows_acceptance::resolve(Some(root), true, identifier, root.parent().ok_or("隔离分享根无效")?)?;
    let metadata = fs::symlink_metadata(&arm).map_err(|_| crate::json_error("隔离分享排队探针不可读"))?;
    if !metadata.is_file() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 || metadata.len() != 0 {
        return Err(crate::json_error("隔离分享排队探针标记无效"));
    }
    let canonical_root = fs::canonicalize(root).map_err(|_| crate::json_error("隔离分享根不可读"))?;
    let canonical_path = fs::canonicalize(path).map_err(|_| crate::json_error("隔离分享文件不可读"))?;
    if !canonical_path.starts_with(canonical_root.join("share-outbox")) {
        return Err(crate::json_error("隔离分享排队探针路径越界"));
    }
    let file_metadata = fs::symlink_metadata(path).map_err(|_| crate::json_error("隔离分享文件不可读"))?;
    if !file_metadata.is_file() || file_metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 || file_metadata.len() != 43 {
        return Err(crate::json_error("隔离分享排队探针仅接受公开合成附件"));
    }
    let bytes = fs::read(path).map_err(|_| crate::json_error("隔离分享文件不可读"))?;
    if bytes.len() != 43 || format!("{:x}", Sha256::digest(&bytes)) != "3f3fa3e4842fd4d54be489e980058c1aaf1bd89951dd0dd521e29f0b81639969" {
        return Err(crate::json_error("隔离分享排队探针仅接受公开合成附件"));
    }
    fs::remove_file(arm).map_err(|_| crate::json_error("隔离分享排队探针未消费"))?;
    Ok(true)
}

pub(crate) async fn show(window: &tauri::WebviewWindow, path: &Path) -> Result<(), String> {
    if !path.is_absolute() || !path.is_file() {
        return Err(crate::json_error("Windows 分享文件无效"));
    }
    #[cfg(debug_assertions)]
    if take_queue_delay_probe(window, path)? {
        window.run_on_main_thread(|| {
            trace_event("queue-delay-probe-started");
            std::thread::sleep(std::time::Duration::from_millis(6500));
            trace_event("queue-delay-probe-ended");
        }).map_err(|_| crate::json_error("隔离分享排队探针无法进入窗口线程"))?;
    }
    let task_window = window.clone();
    let task_path = path.to_path_buf();
    let cleanup_path = task_path.clone();
    let abandoned = Arc::new(AtomicBool::new(false));
    let task_abandoned = Arc::clone(&abandoned);
    let (sender, receiver) = tokio::sync::oneshot::channel();
    if window
        .run_on_main_thread(move || {
            if task_abandoned.load(Ordering::Acquire) {
                cleanup_failed_request(&task_path);
                trace_event("abandoned-before-window-request");
                return;
            }
            let result = show_on_window_thread(&task_window, &task_path);
            if result.is_err() || task_abandoned.load(Ordering::Acquire) {
                cleanup_failed_request(&task_path);
            }
            let _ = sender.send(result);
        })
        .is_err()
    {
        cleanup_failed_request(&cleanup_path);
        return Err(crate::json_error("Windows 分享任务无法进入窗口线程"));
    }
    match tokio::time::timeout(std::time::Duration::from_secs(5), receiver).await {
        Ok(Ok(result)) => result,
        _ => {
            abandoned.store(true, Ordering::Release);
            Err(crate::json_error("Windows 分享窗口响应超时"))
        }
    }
}

fn show_on_window_thread(window: &tauri::WebviewWindow, path: &Path) -> Result<(), String> {
    #[cfg(debug_assertions)]
    let retain_completed_probe = take_retain_completed_probe(window)?;
    #[cfg(not(debug_assertions))]
    let retain_completed_probe = false;
    let hwnd: HWND = window
        .hwnd()
        .map_err(|_| crate::json_error("Windows 分享窗口不可用"))?;
    #[cfg(debug_assertions)]
    install_close_message_probe(window, hwnd)?;
    let path_text = storage_file_path(path)?;
    let title = path
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or("南枫 AI 附件")
        .to_owned();
    let cleanup_path = path.to_path_buf();
    let handler = TypedEventHandler::<DataTransferManager, DataRequestedEventArgs>::new(
        move |_, event_args| {
            trace_event("data-requested");
            let request = event_args.ok()?.Request()?;
            let package = request.Data()?;
            let prepared = (|| -> windows::core::Result<()> {
                let storage_file =
                    StorageFile::GetFileFromPathAsync(&HSTRING::from(&path_text))?.get()?;
                let item: IStorageItem = storage_file.cast()?;
                let items: IVectorView<IStorageItem> = vec![Some(item)].into();
                package.Properties()?.SetTitle(&HSTRING::from(&title))?;
                package.SetStorageItemsReadOnly(&items)
            })();
            if let Err(error) = prepared {
                #[cfg(debug_assertions)]
                eprintln!("windows-share:prepare-failed:{:08X}", error.code().0 as u32);
                #[cfg(not(debug_assertions))]
                let _ = error;
                let _ = request.FailWithDisplayText(&HSTRING::from("附件无法交给 Windows 分享"));
                cleanup_failed_request(&cleanup_path);
                return Ok(());
            }
            install_cleanup(&package, cleanup_path.clone(), retain_completed_probe);
            if mark_handoff(&cleanup_path).is_err() {
                let _ = request.FailWithDisplayText(&HSTRING::from("分享交付租约无法保存"));
                cleanup_failed_request(&cleanup_path);
                trace_event("handoff-marker-failed");
                return Ok(());
            }
            trace_event("package-ready");
            Ok(())
        },
    );
    let interop: IDataTransferManagerInterop = factory::<DataTransferManager, _>()
        .map_err(|_| crate::json_error("Windows 分享服务不可用"))?;
    let manager: DataTransferManager = unsafe { interop.GetForWindow(hwnd) }
        .map_err(|_| crate::json_error("Windows 分享窗口无法绑定"))?;
    let token = manager
        .DataRequested(&handler)
        .map_err(|_| crate::json_error("Windows 分享请求无法注册"))?;
    #[cfg(debug_assertions)]
    let provider_token = match install_local_receiver_probe(window, &manager, path) {
        Ok(token) => token,
        Err(error) => {
            let _ = manager.RemoveDataRequested(token);
            return Err(error);
        }
    };
    SHARE_SESSION.with(|slot| {
        slot.replace(Some(ShareSession {
            manager: manager.clone(),
            token,
            #[cfg(debug_assertions)]
            provider_token,
        }));
    });
    if unsafe { interop.ShowShareUIForWindow(hwnd) }.is_err() {
        SHARE_SESSION.with(|slot| {
            slot.replace(None);
        });
        cleanup_failed_request(path);
        return Err(crate::json_error("Windows 分享面板未打开"));
    }
    trace_event("ui-requested");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(debug_assertions)]
    #[test]
    fn windows_share_queue_probe_requires_identity_public_bytes_and_consumes_once() {
        let directory = tempfile::tempdir().unwrap();
        let run_id = "12345678-1234-1234-1234-123456789abc";
        let root = directory.path().join(format!("nanfeng-ai-windows-acceptance.{run_id}"));
        fs::create_dir(&root).unwrap();
        let identifier = "com.nanzhufeng.ai.desktop.windowsacceptance";
        fs::write(root.join(".nanfeng-windows-acceptance.json"), serde_json::json!({"schemaVersion":1,"identifier":identifier,"runId":run_id}).to_string()).unwrap();
        let source = directory.path().join("public.txt");
        let public = b"Non-sensitive exchange fixture attachment.\n";
        assert_eq!(public.len(), 43);
        fs::write(&source, public).unwrap();
        let copy = prepare_attachment(&root, &source, "public.txt", 43, &crate::sha256(public)).unwrap();
        let arm = root.join(".share-window-queue-delay-probe");
        assert!(!take_queue_delay_probe_for(&root, identifier, &copy).unwrap());
        fs::write(&arm, b"").unwrap();
        assert!(take_queue_delay_probe_for(&root, "com.nanzhufeng.ai.desktop", &copy).is_err());
        assert!(arm.exists());
        assert!(take_queue_delay_probe_for(&root, identifier, &source).is_err());
        assert!(arm.exists());
        fs::write(&copy, [b'x'; 43]).unwrap();
        assert!(take_queue_delay_probe_for(&root, identifier, &copy).is_err());
        assert!(arm.exists());
        fs::write(&copy, public).unwrap();
        fs::write(&arm, b"invalid").unwrap();
        assert!(take_queue_delay_probe_for(&root, identifier, &copy).is_err());
        fs::write(&arm, b"").unwrap();
        assert!(take_queue_delay_probe_for(&root, identifier, &copy).unwrap());
        assert!(!arm.exists());
        assert!(!take_queue_delay_probe_for(&root, identifier, &copy).unwrap());
        assert_eq!(fs::read(source).unwrap(), public);
    }

    #[test]
    fn windows_share_exited_process_with_retained_handle_does_not_block_cleanup() {
        use std::os::windows::process::CommandExt;
        let mut child = std::process::Command::new("cmd.exe")
            .args(["/c", "exit", "0"]).creation_flags(0x08000000).spawn().unwrap();
        let pid = child.id();
        assert!(child.wait().unwrap().success());
        assert!(!lease_process_may_be_running(pid));
        assert!(lease_process_may_be_running(std::process::id()));
    }

    #[test]
    fn windows_share_failed_request_preserves_handoff_and_other_leases() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("app-data");
        fs::create_dir(&root).unwrap();
        let source = directory.path().join("asset");
        let bytes = b"synthetic delayed receiver";
        fs::write(&source, bytes).unwrap();
        let digest = crate::sha256(bytes);
        let delivered = prepare_attachment(&root, &source, "delivered.txt", bytes.len() as u64, &digest).unwrap();
        let pending = prepare_attachment(&root, &source, "pending.txt", bytes.len() as u64, &digest).unwrap();
        mark_handoff(&delivered).unwrap();
        cleanup_failed_request(&delivered);
        assert_eq!(fs::read(&delivered).unwrap(), bytes);
        assert_eq!(fs::read(&pending).unwrap(), bytes);
        cleanup_failed_request(&pending);
        assert!(!pending.exists());
        assert_eq!(fs::read(&delivered).unwrap(), bytes);
        assert_eq!(fs::read(&source).unwrap(), bytes);
    }

    #[test]
    fn windows_share_failed_request_preserves_invalid_handoff() {
        let directory = tempfile::tempdir().unwrap();
        let lease = directory.path().join("lease");
        fs::create_dir(&lease).unwrap();
        let copy = lease.join("shared.txt");
        fs::write(&copy, b"synthetic receiver").unwrap();
        fs::write(lease.join(HANDOFF_MARKER), b"invalid").unwrap();
        cleanup_failed_request(&copy);
        assert_eq!(fs::read(&copy).unwrap(), b"synthetic receiver");
    }

    #[test]
    fn windows_share_cleanup_preview_binds_identity_and_protects_running_source() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("app-data");
        fs::create_dir(&root).unwrap();
        let source = directory.path().join("asset");
        let bytes = b"synthetic receiver";
        fs::write(&source, bytes).unwrap();
        let copy = prepare_attachment(&root, &source, "shared.txt", bytes.len() as u64, &crate::sha256(bytes)).unwrap();
        mark_handoff(&copy).unwrap();
        assert_eq!(retained_copies(&root, false).unwrap().count, 1);
        assert_eq!(retained_copies(&root, true).unwrap().count, 0);
        let old_lease = copy.parent().unwrap().parent().unwrap().join("lease-4294967294-1");
        fs::rename(copy.parent().unwrap(), &old_lease).unwrap();
        let old_copy = old_lease.join("shared.txt");
        let preview = retained_copies(&root, true).unwrap();
        assert_eq!(preview.count, 1);
        assert_eq!(preview.bytes, bytes.len() as u64);
        fs::write(&old_copy, b"changed same size!").unwrap();
        assert!(delete_retained_copies(&root, &preview.fingerprint).is_err());
        assert!(old_copy.exists());
        let preview = retained_copies(&root, true).unwrap();
        delete_retained_copies(&root, &preview.fingerprint).unwrap();
        assert!(!old_copy.exists());
        assert_eq!(fs::read(&source).unwrap(), bytes);
    }

    #[test]
    fn windows_share_cleanup_rejects_unknown_tree_and_hardlinked_payload() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("app-data");
        let lease = root.join("share-outbox/0123456789abcdef/lease-4294967294-1");
        fs::create_dir_all(&lease).unwrap();
        let original = directory.path().join("original");
        fs::write(&original, b"preserve").unwrap();
        fs::hard_link(&original, lease.join("shared.txt")).unwrap();
        fs::write(lease.join(HANDOFF_MARKER), b"1").unwrap();
        assert!(retained_copies(&root, true).is_err());
        assert_eq!(fs::read(&original).unwrap(), b"preserve");
        fs::create_dir(root.join("share-outbox/unknown")).unwrap();
        assert!(retained_copies(&root, false).is_err());
    }

    #[test]
    fn windows_share_cleanup_preserves_receiver_open_file_and_marker() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("app-data");
        let lease = root.join("share-outbox/0123456789abcdef/lease-4294967294-1");
        fs::create_dir_all(&lease).unwrap();
        let copy = lease.join("shared.txt");
        fs::write(&copy, b"synthetic receiver").unwrap();
        fs::write(lease.join(HANDOFF_MARKER), b"1").unwrap();
        let preview = retained_copies(&root, true).unwrap();
        let receiver = OpenOptions::new().read(true).share_mode(FILE_SHARE_READ.0).open(&copy).unwrap();
        assert!(delete_retained_copies(&root, &preview.fingerprint).is_err());
        assert!(copy.exists());
        assert!(lease.join(HANDOFF_MARKER).exists());
        drop(receiver);
        delete_retained_copies(&root, &preview.fingerprint).unwrap();
        assert!(!lease.exists());
    }

    #[test]
    fn windows_share_handoff_survives_exit_cleanup_and_receiver_can_read_later() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("app-data");
        fs::create_dir(&root).unwrap();
        let source = directory.path().join("asset");
        let bytes = b"synthetic delayed receiver";
        fs::write(&source, bytes).unwrap();
        let digest = crate::sha256(bytes);
        let handed_off = prepare_attachment(&root, &source, "shared.txt", bytes.len() as u64, &digest).unwrap();
        let unoffered = prepare_attachment(&root, &source, "unoffered.txt", bytes.len() as u64, &digest).unwrap();
        mark_handoff(&handed_off).unwrap();
        cleanup_outbox(&root).unwrap();
        assert_eq!(fs::read(&handed_off).unwrap(), bytes);
        assert!(!unoffered.exists());
        assert_eq!(fs::read(&source).unwrap(), bytes);
        cleanup_lease(&handed_off);
        assert!(!handed_off.parent().unwrap().exists());
        cleanup_outbox(&root).unwrap();
        assert!(!root.join("share-outbox").exists());
    }

    #[test]
    fn windows_share_invalid_handoff_marker_preserves_copy_and_reports_error() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("app-data");
        fs::create_dir(&root).unwrap();
        let source = directory.path().join("asset");
        let bytes = b"synthetic delayed receiver";
        fs::write(&source, bytes).unwrap();
        let copy = prepare_attachment(&root, &source, "shared.txt", bytes.len() as u64, &crate::sha256(bytes)).unwrap();
        fs::write(copy.parent().unwrap().join(HANDOFF_MARKER), b"invalid").unwrap();
        assert!(cleanup_outbox(&root).is_err());
        assert_eq!(fs::read(&copy).unwrap(), bytes);
        assert_eq!(fs::read(&source).unwrap(), bytes);
    }

    fn receiver_probe_root() -> (tempfile::TempDir, PathBuf) {
        let parent = tempfile::tempdir().unwrap();
        let run_id = "12345678-abcd";
        let root = parent.path().join(format!("nanfeng-ai-windows-acceptance.{run_id}"));
        fs::create_dir(&root).unwrap();
        fs::write(root.join(crate::desktop_windows_acceptance::MARKER),
            serde_json::json!({"schemaVersion":1,"identifier":crate::desktop_windows_acceptance::IDENTIFIER,"runId":run_id}).to_string()).unwrap();
        (parent, root)
    }

    #[test]
    fn share_retention_probe_rejects_daily_identity_without_consuming_arm() {
        let (_parent, root) = receiver_probe_root();
        let arm = root.join(".share-retain-completed-probe");
        fs::write(&arm, []).unwrap();
        assert!(take_retain_completed_probe_for(&root, "com.nanzhufeng.ai.desktop").is_err());
        assert!(arm.exists());
    }

    #[test]
    fn share_retention_probe_requires_empty_arm_and_valid_root_marker() {
        let (_parent, root) = receiver_probe_root();
        let arm = root.join(".share-retain-completed-probe");
        fs::write(&arm, b"unexpected").unwrap();
        assert!(take_retain_completed_probe_for(&root, crate::desktop_windows_acceptance::IDENTIFIER).is_err());
        fs::write(&arm, []).unwrap();
        fs::write(root.join(crate::desktop_windows_acceptance::MARKER), b"{}").unwrap();
        assert!(take_retain_completed_probe_for(&root, crate::desktop_windows_acceptance::IDENTIFIER).is_err());
        assert!(arm.exists());
    }

    #[test]
    fn share_retention_probe_is_one_shot_only_in_marked_acceptance_root() {
        let (_parent, root) = receiver_probe_root();
        assert!(!take_retain_completed_probe_for(&root, crate::desktop_windows_acceptance::IDENTIFIER).unwrap());
        fs::write(root.join(".share-retain-completed-probe"), []).unwrap();
        assert!(take_retain_completed_probe_for(&root, crate::desktop_windows_acceptance::IDENTIFIER).unwrap());
        assert!(!take_retain_completed_probe_for(&root, crate::desktop_windows_acceptance::IDENTIFIER).unwrap());
    }

    #[test]
    fn windows_share_storage_file_reads_canonical_root_lease() {
        use windows::Win32::System::WinRT::{RoInitialize, RoUninitialize, RO_INIT_MULTITHREADED};
        unsafe { RoInitialize(RO_INIT_MULTITHREADED).unwrap() };
        struct Apartment;
        impl Drop for Apartment {
            fn drop(&mut self) { unsafe { RoUninitialize() } }
        }
        let _apartment = Apartment;
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().canonicalize().unwrap();
        let source = root.join("asset");
        let bytes = b"synthetic canonical share attachment";
        fs::write(&source, bytes).unwrap();
        let copy = prepare_attachment(&root, &source, "canonical.txt", bytes.len() as u64,
            &crate::sha256(bytes)).unwrap();
        let native = storage_file_path(&copy).unwrap();
        assert_eq!(storage_file_path(Path::new(&native)).unwrap(), native);
        assert!(storage_file_path(Path::new("relative.txt")).is_err());
        let storage_file = StorageFile::GetFileFromPathAsync(&HSTRING::from(native))
            .unwrap().get().unwrap();
        assert_eq!(storage_file.Name().unwrap(), "canonical.txt");
        assert_eq!(fs::read(&copy).unwrap(), bytes);
        cleanup_lease(&copy);
        assert_eq!(fs::read(&source).unwrap(), bytes);
    }

    #[test]
    fn windows_share_filename_removes_reserved_and_path_syntax() {
        assert_eq!(safe_leaf("../CON.txt"), "_CON.txt");
        assert_eq!(safe_leaf("report:Q3?.pdf. "), "report-Q3-.pdf");
        assert_eq!(safe_leaf(".."), "附件");
    }

    #[test]
    fn windows_share_copy_is_independent_verified_and_cleanup_is_scoped() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("app-data");
        fs::create_dir(&root).unwrap();
        let source = directory.path().join("asset");
        let bytes = b"synthetic share attachment";
        fs::write(&source, bytes).unwrap();
        let digest = crate::sha256(bytes);
        let copy = prepare_attachment(&root, &source, "fixture?.txt", bytes.len() as u64, &digest)
            .unwrap();
        assert_eq!(fs::read(&copy).unwrap(), bytes);
        fs::write(&copy, b"recipient-side mutation").unwrap();
        assert_eq!(fs::read(&source).unwrap(), bytes);
        let lease = copy.parent().unwrap().to_path_buf();
        cleanup_lease(&copy);
        assert!(!copy.exists());
        assert!(!lease.exists());
        assert_eq!(fs::read(&source).unwrap(), bytes);
    }

    #[test]
    fn windows_share_rejects_invalid_or_reparse_outbox_without_rewriting_source() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("app-data");
        fs::create_dir(&root).unwrap();
        let source = directory.path().join("asset");
        let bytes = b"synthetic share attachment";
        fs::write(&source, bytes).unwrap();
        let digest = crate::sha256(bytes);
        fs::create_dir(root.join("share-outbox")).unwrap();
        let redirected = directory.path().join("redirected");
        fs::create_dir(&redirected).unwrap();
        fs::remove_dir(root.join("share-outbox")).unwrap();
        if std::os::windows::fs::symlink_dir(&redirected, root.join("share-outbox")).is_err() {
            // Standard non-elevated Windows sessions may lack symlink privilege. A plain
            // file at the owner directory exercises the same fail-closed path.
            fs::write(root.join("share-outbox"), b"not a directory").unwrap();
        }
        assert!(
            prepare_attachment(&root, &source, "fixture.txt", bytes.len() as u64, &digest,)
                .is_err()
        );
        assert_eq!(fs::read(source).unwrap(), bytes);
        assert!(redirected.read_dir().unwrap().next().is_none());
    }

    #[test]
    fn windows_share_exit_cleanup_removes_only_the_derived_outbox() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("app-data");
        let lease = root.join("share-outbox").join("digest").join("lease");
        fs::create_dir_all(&lease).unwrap();
        fs::write(lease.join("shared.txt"), b"derived").unwrap();
        fs::write(root.join("business.sqlite3"), b"preserve").unwrap();
        cleanup_outbox(&root).unwrap();
        assert!(!root.join("share-outbox").exists());
        assert_eq!(
            fs::read(root.join("business.sqlite3")).unwrap(),
            b"preserve"
        );
    }
}
