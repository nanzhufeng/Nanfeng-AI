//! Windows system handoff. Callers own attachment identity/format validation and
//! external-access authorization; this module never accepts a shell command.
use std::{
    ffi::OsStr,
    fs::{self, OpenOptions},
    io::{Read, Write},
    os::windows::{
        ffi::OsStrExt,
        fs::{MetadataExt, OpenOptionsExt},
        io::AsRawHandle,
    },
    path::{Path, PathBuf},
};
use windows_sys::Win32::{
    Foundation::{GetLastError, ERROR_ACCESS_DENIED, ERROR_CANCELLED, ERROR_NO_ASSOCIATION},
    Storage::FileSystem::{
        GetFileInformationByHandle, BY_HANDLE_FILE_INFORMATION, FILE_ATTRIBUTE_REPARSE_POINT,
        FILE_FLAG_OPEN_REPARSE_POINT, FILE_SHARE_READ,
    },
    System::Com::{
        CoInitializeEx, CoUninitialize, COINIT_APARTMENTTHREADED, COINIT_DISABLE_OLE1DDE,
    },
    UI::{
        Shell::{ShellExecuteExW, SEE_MASK_FLAG_NO_UI, SEE_MASK_NOASYNC, SHELLEXECUTEINFOW},
        WindowsAndMessaging::SW_SHOWNORMAL,
    },
};

/// A separate file, never a hard link to the content-addressed asset. Existing
/// edited copies are preserved and rejected rather than silently overwritten.
pub(crate) fn prepare_docx(root: &Path, bytes: &[u8], digest: &str) -> Result<PathBuf, String> {
    if !root.is_absolute() || !crate::is_sha256(digest) || crate::sha256(bytes) != digest {
        return Err(crate::json_error("系统打开副本身份无效"));
    }
    let directory = root.join("system-open");
    match fs::create_dir(&directory) {
        Ok(()) => (),
        Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => (),
        Err(_) => return Err(crate::json_error("无法创建本机系统打开目录")),
    }
    let metadata =
        fs::symlink_metadata(&directory).map_err(|_| crate::json_error("系统打开目录不可读"))?;
    if !metadata.is_dir() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0 {
        return Err(crate::json_error("系统打开目录不能是链接或重解析点"));
    }
    let path = directory.join(format!("{digest}.docx"));
    match OpenOptions::new()
        .write(true)
        .create_new(true)
        .share_mode(0)
        .open(&path)
    {
        Ok(mut file) => {
            // Exclusive sharing keeps readers from observing a partial copy.
            let result = file.write_all(bytes).and_then(|_| file.sync_all());
            drop(file);
            if result.is_err() {
                // Only this create_new-owned file is eligible for cleanup.
                let _ = fs::remove_file(&path);
                return Err(crate::json_error("无法持久化本机系统打开副本"));
            }
        }
        Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {
            let mut file = OpenOptions::new()
                .read(true)
                .share_mode(FILE_SHARE_READ)
                .custom_flags(FILE_FLAG_OPEN_REPARSE_POINT)
                .open(&path)
                .map_err(|_| crate::json_error("系统打开副本正在使用或不可读"))?;
            let metadata = file
                .metadata()
                .map_err(|_| crate::json_error("系统打开副本不可读"))?;
            if !metadata.is_file() || metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0
            {
                return Err(crate::json_error("系统打开副本不能是链接或重解析点"));
            }
            let mut information = BY_HANDLE_FILE_INFORMATION::default();
            if unsafe { GetFileInformationByHandle(file.as_raw_handle(), &mut information) } == 0
                || information.nNumberOfLinks != 1
            {
                return Err(crate::json_error(
                    "系统打开副本必须是独立文件，不能是硬链接",
                ));
            }
            if metadata.len() != bytes.len() as u64 {
                return Err(crate::json_error(
                    "系统打开副本已修改；原附件保持不变，请先保存外部编辑内容",
                ));
            }
            let mut existing = Vec::with_capacity(bytes.len());
            file.read_to_end(&mut existing)
                .map_err(|_| crate::json_error("系统打开副本不可读"))?;
            if existing != bytes {
                return Err(crate::json_error(
                    "系统打开副本已修改；原附件保持不变，请先保存外部编辑内容",
                ));
            }
        }
        Err(_) => return Err(crate::json_error("无法建立本机系统打开副本")),
    }
    Ok(path)
}

pub(crate) fn open_docx(path: &Path) -> Result<(), String> {
    if !path.is_absolute() || path.extension() != Some(OsStr::new("docx")) {
        return Err(crate::json_error("系统打开文件类型无效"));
    }
    execute(path.as_os_str())
}

pub(crate) fn open_source(url: &reqwest::Url) -> Result<(), String> {
    // Defense in depth: the IPC owner applies the same source-link contract.
    let validated = crate::validated_desktop_source_link(url.as_str())?;
    execute(OsStr::new(validated.as_str()))
}

fn execute(target: &OsStr) -> Result<(), String> {
    let mut wide: Vec<u16> = target.encode_wide().collect();
    if wide.is_empty() || wide.contains(&0) {
        return Err(crate::json_error("系统打开目标无效"));
    }
    wide.push(0);
    // A dedicated STA avoids altering Tauri's COM apartment. NOASYNC completes
    // the document handoff before this thread exits. No parameters, environment
    // expansion, command interpreter, elevation verb or zone-check bypass.
    std::thread::Builder::new()
        .name("nf-system-open".into())
        .spawn(move || {
            let initialized = unsafe {
                CoInitializeEx(
                    std::ptr::null(),
                    (COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE) as u32,
                )
            };
            if initialized < 0 {
                return Err(crate::json_error("Windows 系统打开服务不可用"));
            }
            struct Apartment;
            impl Drop for Apartment {
                fn drop(&mut self) {
                    unsafe { CoUninitialize() };
                }
            }
            let _apartment = Apartment;
            let verb: Vec<u16> = "open\0".encode_utf16().collect();
            let mut info = SHELLEXECUTEINFOW {
                cbSize: std::mem::size_of::<SHELLEXECUTEINFOW>() as u32,
                fMask: SEE_MASK_FLAG_NO_UI | SEE_MASK_NOASYNC,
                lpVerb: verb.as_ptr(),
                lpFile: wide.as_ptr(),
                nShow: SW_SHOWNORMAL,
                ..Default::default()
            };
            if unsafe { ShellExecuteExW(&mut info) } == 0 {
                let message = match unsafe { GetLastError() } {
                    ERROR_NO_ASSOCIATION => {
                        "未找到可打开此内容的默认应用，请在 Windows 中设置关联应用"
                    }
                    ERROR_ACCESS_DENIED => "Windows 拒绝打开此内容",
                    ERROR_CANCELLED => "系统打开已取消",
                    _ => "Windows 未能打开此内容，请检查文件或默认应用",
                };
                return Err(crate::json_error(message));
            }
            // Success means the OS accepted the handoff, not that an editor finished
            // loading the document or a browser successfully fetched the website.
            Ok(())
        })
        .map_err(|_| crate::json_error("无法启动系统打开任务"))?
        .join()
        .map_err(|_| crate::json_error("系统打开任务异常"))?
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn system_open_rejects_executable_paths_and_non_web_links_before_dispatch() {
        for path in ["relative.docx", "C:\\fixture.exe", "C:\\fixture\0.docx"] {
            assert!(open_docx(Path::new(path)).is_err());
        }
        for url in [
            "file:///C:/fixture.docx",
            "javascript:alert(1)",
            "https://user:secret@example.com",
        ] {
            assert!(open_source(&reqwest::Url::parse(url).unwrap()).is_err());
        }
    }

    #[test]
    fn system_open_rejects_a_hard_link_to_the_original_without_modifying_it() {
        let directory = tempfile::tempdir().unwrap();
        let bytes = b"synthetic verified owner bytes";
        let digest = crate::sha256(bytes);
        let original = directory.path().join("original");
        fs::write(&original, bytes).unwrap();
        fs::create_dir(directory.path().join("system-open")).unwrap();
        let presentation = directory
            .path()
            .join("system-open")
            .join(format!("{digest}.docx"));
        fs::hard_link(&original, &presentation).unwrap();
        assert!(prepare_docx(directory.path(), bytes, &digest).is_err());
        assert_eq!(fs::read(&original).unwrap(), bytes);
        assert_eq!(fs::read(&presentation).unwrap(), bytes);
    }

    #[test]
    fn system_open_rejects_busy_or_invalid_destinations_and_recovers_after_unlock() {
        let directory = tempfile::tempdir().unwrap();
        let bytes = b"synthetic verified owner bytes";
        let digest = crate::sha256(bytes);
        assert!(prepare_docx(directory.path(), bytes, "../outside").is_err());
        assert!(!directory.path().join("system-open").exists());
        fs::write(directory.path().join("system-open"), b"not a directory").unwrap();
        assert!(prepare_docx(directory.path(), bytes, &digest).is_err());
        fs::remove_file(directory.path().join("system-open")).unwrap();
        let presentation = prepare_docx(directory.path(), bytes, &digest).unwrap();
        let lock = OpenOptions::new()
            .read(true)
            .share_mode(0)
            .open(&presentation)
            .unwrap();
        assert!(prepare_docx(directory.path(), bytes, &digest).is_err());
        drop(lock);
        assert_eq!(
            prepare_docx(directory.path(), bytes, &digest).unwrap(),
            presentation
        );
        assert_eq!(fs::read(presentation).unwrap(), bytes);
    }
}
