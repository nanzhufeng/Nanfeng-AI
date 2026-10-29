//! Windows-only protection for application-owned credential files.
//!
//! Provider and account credentials keep separate product owners. This module only owns their
//! shared platform boundary: a protected DACL, current-user DPAPI wrapping for each install-local
//! AES key, and replace-existing atomic commits that keep the previous valid document on failure.

use fs2::FileExt;
use getrandom::fill as random_fill;
use std::{
    fs::{self, File, OpenOptions},
    io::{self, Write},
    os::windows::{
        ffi::OsStrExt,
        fs::{MetadataExt, OpenOptionsExt},
        io::AsRawHandle,
    },
    path::Path,
    ptr, slice,
    sync::atomic::{AtomicU64, Ordering},
};
use windows::{
    core::{w, PCWSTR, PWSTR},
    Win32::{
        Foundation::{CloseHandle, LocalFree, ERROR_SUCCESS, HANDLE, HLOCAL},
        Security::{
            Authorization::{
                SetEntriesInAclW, SetNamedSecurityInfoW, EXPLICIT_ACCESS_W, SET_ACCESS,
                SE_FILE_OBJECT, TRUSTEE_IS_SID, TRUSTEE_IS_USER, TRUSTEE_IS_WELL_KNOWN_GROUP,
                TRUSTEE_W,
            },
            CreateWellKnownSid,
            Cryptography::{
                CryptProtectData, CryptUnprotectData, CRYPTPROTECT_UI_FORBIDDEN, CRYPT_INTEGER_BLOB,
            },
            GetTokenInformation, TokenUser, WinBuiltinAdministratorsSid, WinLocalSystemSid,
            DACL_SECURITY_INFORMATION, PROTECTED_DACL_SECURITY_INFORMATION, PSID,
            SECURITY_MAX_SID_SIZE, SUB_CONTAINERS_AND_OBJECTS_INHERIT, TOKEN_QUERY, TOKEN_USER,
        },
        Storage::FileSystem::{
            GetFileInformationByHandle, MoveFileExW, BY_HANDLE_FILE_INFORMATION, FILE_ALL_ACCESS,
            FILE_ATTRIBUTE_REPARSE_POINT, FILE_SHARE_DELETE, FILE_SHARE_READ, FILE_SHARE_WRITE,
            MOVEFILE_REPLACE_EXISTING, MOVEFILE_WRITE_THROUGH,
        },
        System::Threading::{GetCurrentProcess, OpenProcessToken},
    },
};

const KEY_MAGIC: &[u8] = b"NFAI-DPAPI-KEY-V1\0";
const DPAPI_ENTROPY: &[u8] = b"com.nanzhufeng.ai.desktop.private-credentials.v1";
static TEMPORARY_COUNTER: AtomicU64 = AtomicU64::new(1);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum KeyLoadError {
    Missing,
    Invalid,
    Unavailable,
}

pub(crate) struct StoreLock(File);

impl Drop for StoreLock {
    fn drop(&mut self) {
        let _ = FileExt::unlock(&self.0);
    }
}

struct OwnedHandle(HANDLE);

impl Drop for OwnedHandle {
    fn drop(&mut self) {
        let _ = unsafe { CloseHandle(self.0) };
    }
}

fn path_wide(path: &Path) -> Vec<u16> {
    path.as_os_str().encode_wide().chain(Some(0)).collect()
}

fn store_lock_path(root: &Path) -> io::Result<std::path::PathBuf> {
    let parent = root
        .parent()
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "private root has no parent"))?;
    let leaf = root
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| {
            io::Error::new(io::ErrorKind::InvalidInput, "private root name is invalid")
        })?;
    Ok(parent.join(format!(".{leaf}.lock")))
}

pub(crate) fn lock_store(root: &Path) -> io::Result<StoreLock> {
    let parent = root
        .parent()
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "private root has no parent"))?;
    fs::create_dir_all(parent)?;
    let path = store_lock_path(root)?;
    let file = match OpenOptions::new()
        .read(true)
        .write(true)
        .create_new(true)
        .open(&path)
    {
        Ok(file) => file,
        Err(error) if error.kind() == io::ErrorKind::AlreadyExists => {
            secure_path(&path, false)?;
            OpenOptions::new().read(true).write(true).open(&path)?
        }
        Err(error) => return Err(error),
    };
    secure_path(&path, false)?;
    file.lock_exclusive()?;
    if root.exists() {
        secure_path(root, true)?;
        cleanup_stale_temporary_files(root)?;
    }
    Ok(StoreLock(file))
}

fn is_private_temporary_name(name: &str) -> bool {
    [".credentials.json.", ".local.key."].iter().any(|prefix| {
        let Some(body) = name
            .strip_prefix(prefix)
            .and_then(|value| value.strip_suffix(".tmp"))
        else {
            return false;
        };
        let Some((process_id, sequence)) = body.split_once('.') else {
            return false;
        };
        !process_id.is_empty()
            && !sequence.is_empty()
            && process_id.bytes().all(|byte| byte.is_ascii_digit())
            && sequence.bytes().all(|byte| byte.is_ascii_digit())
    })
}

fn cleanup_stale_temporary_files(root: &Path) -> io::Result<()> {
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        let Some(name) = entry.file_name().to_str().map(str::to_owned) else {
            continue;
        };
        if !is_private_temporary_name(&name) {
            continue;
        }
        let path = entry.path();
        secure_path(&path, false)?;
        fs::remove_file(path)?;
    }
    Ok(())
}

fn current_user_sid() -> io::Result<(Vec<u8>, PSID)> {
    let mut token = HANDLE::default();
    unsafe { OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &mut token) }
        .map_err(|_| io::Error::last_os_error())?;
    let token = OwnedHandle(token);
    let mut required = 0_u32;
    let _ = unsafe { GetTokenInformation(token.0, TokenUser, None, 0, &mut required) };
    if required < std::mem::size_of::<TOKEN_USER>() as u32 {
        return Err(io::Error::new(
            io::ErrorKind::Other,
            "Windows current-user SID is unavailable",
        ));
    }
    let mut buffer = vec![0_u8; required as usize];
    unsafe {
        GetTokenInformation(
            token.0,
            TokenUser,
            Some(buffer.as_mut_ptr().cast()),
            required,
            &mut required,
        )
    }
    .map_err(|_| io::Error::last_os_error())?;
    let sid = unsafe { (*(buffer.as_ptr().cast::<TOKEN_USER>())).User.Sid };
    if sid.0.is_null() {
        return Err(io::Error::new(
            io::ErrorKind::Other,
            "Windows current-user SID is empty",
        ));
    }
    Ok((buffer, sid))
}

fn well_known_sid(kind: windows::Win32::Security::WELL_KNOWN_SID_TYPE) -> io::Result<Vec<u8>> {
    let mut buffer = vec![0_u8; SECURITY_MAX_SID_SIZE as usize];
    let mut size = buffer.len() as u32;
    unsafe {
        CreateWellKnownSid(
            kind,
            None,
            Some(PSID(buffer.as_mut_ptr().cast())),
            &mut size,
        )
    }
    .map_err(|_| io::Error::last_os_error())?;
    buffer.truncate(size as usize);
    Ok(buffer)
}

fn acl_entry(
    sid: PSID,
    trustee_type: windows::Win32::Security::Authorization::TRUSTEE_TYPE,
    directory: bool,
) -> EXPLICIT_ACCESS_W {
    EXPLICIT_ACCESS_W {
        grfAccessPermissions: FILE_ALL_ACCESS.0,
        grfAccessMode: SET_ACCESS,
        grfInheritance: if directory {
            SUB_CONTAINERS_AND_OBJECTS_INHERIT
        } else {
            windows::Win32::Security::ACE_FLAGS(0)
        },
        Trustee: TRUSTEE_W {
            pMultipleTrustee: ptr::null_mut(),
            MultipleTrusteeOperation: Default::default(),
            TrusteeForm: TRUSTEE_IS_SID,
            TrusteeType: trustee_type,
            ptstrName: PWSTR(sid.0.cast()),
        },
    }
}

/// Replace inherited access with current user, LocalSystem and Administrators only.
pub(crate) fn secure_path(path: &Path, directory: bool) -> io::Result<()> {
    let metadata = fs::symlink_metadata(path)?;
    if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0
        || (directory && !metadata.is_dir())
        || (!directory && !metadata.is_file())
    {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "private path is not a plain local object",
        ));
    }
    if !directory {
        let file = OpenOptions::new()
            .read(true)
            .share_mode((FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE).0)
            .open(path)?;
        let mut information = BY_HANDLE_FILE_INFORMATION::default();
        unsafe { GetFileInformationByHandle(HANDLE(file.as_raw_handle()), &mut information) }
            .map_err(|_| io::Error::last_os_error())?;
        if information.nNumberOfLinks != 1 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "private file must have exactly one hard link",
            ));
        }
    }
    let (_user_storage, user_sid) = current_user_sid()?;
    let mut system_storage = well_known_sid(WinLocalSystemSid)?;
    let mut administrators_storage = well_known_sid(WinBuiltinAdministratorsSid)?;
    let entries = [
        acl_entry(user_sid, TRUSTEE_IS_USER, directory),
        acl_entry(
            PSID(system_storage.as_mut_ptr().cast()),
            TRUSTEE_IS_WELL_KNOWN_GROUP,
            directory,
        ),
        acl_entry(
            PSID(administrators_storage.as_mut_ptr().cast()),
            TRUSTEE_IS_WELL_KNOWN_GROUP,
            directory,
        ),
    ];
    let mut acl = ptr::null_mut();
    let status = unsafe { SetEntriesInAclW(Some(&entries), None, &mut acl) };
    if status != ERROR_SUCCESS {
        return Err(io::Error::from_raw_os_error(status.0 as i32));
    }
    let wide = path_wide(path);
    let result = unsafe {
        SetNamedSecurityInfoW(
            PCWSTR(wide.as_ptr()),
            SE_FILE_OBJECT,
            DACL_SECURITY_INFORMATION | PROTECTED_DACL_SECURITY_INFORMATION,
            None,
            None,
            Some(acl),
            None,
        )
    };
    let _ = unsafe { LocalFree(Some(HLOCAL(acl.cast()))) };
    if result == ERROR_SUCCESS {
        Ok(())
    } else {
        Err(io::Error::from_raw_os_error(result.0 as i32))
    }
}

fn input_blob(bytes: &[u8]) -> io::Result<CRYPT_INTEGER_BLOB> {
    Ok(CRYPT_INTEGER_BLOB {
        cbData: u32::try_from(bytes.len())
            .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "DPAPI input is too large"))?,
        pbData: bytes.as_ptr() as *mut u8,
    })
}

fn protect_key(key: &[u8; 32]) -> io::Result<Vec<u8>> {
    let input = input_blob(key)?;
    let entropy = input_blob(DPAPI_ENTROPY)?;
    let mut output = CRYPT_INTEGER_BLOB::default();
    unsafe {
        CryptProtectData(
            &input,
            w!("Nanfeng AI local credential key"),
            Some(&entropy),
            None,
            None,
            CRYPTPROTECT_UI_FORBIDDEN,
            &mut output,
        )
    }
    .map_err(|_| io::Error::last_os_error())?;
    if output.pbData.is_null() || output.cbData == 0 {
        return Err(io::Error::new(
            io::ErrorKind::Other,
            "DPAPI returned an empty protected key",
        ));
    }
    let protected = unsafe { slice::from_raw_parts(output.pbData, output.cbData as usize) };
    let mut encoded = Vec::with_capacity(KEY_MAGIC.len() + protected.len());
    encoded.extend_from_slice(KEY_MAGIC);
    encoded.extend_from_slice(protected);
    let _ = unsafe { LocalFree(Some(HLOCAL(output.pbData.cast()))) };
    Ok(encoded)
}

fn unprotect_key(encoded: &[u8]) -> io::Result<[u8; 32]> {
    let protected = encoded.strip_prefix(KEY_MAGIC).ok_or_else(|| {
        io::Error::new(
            io::ErrorKind::InvalidData,
            "credential key version is invalid",
        )
    })?;
    let input = input_blob(protected)?;
    let entropy = input_blob(DPAPI_ENTROPY)?;
    let mut output = CRYPT_INTEGER_BLOB::default();
    unsafe {
        CryptUnprotectData(
            &input,
            None,
            Some(&entropy),
            None,
            None,
            CRYPTPROTECT_UI_FORBIDDEN,
            &mut output,
        )
    }
    .map_err(|_| io::Error::last_os_error())?;
    let result = if output.pbData.is_null() || output.cbData != 32 {
        Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "credential key payload has an invalid length",
        ))
    } else {
        let mut key = [0_u8; 32];
        key.copy_from_slice(unsafe { slice::from_raw_parts(output.pbData, 32) });
        Ok(key)
    };
    if !output.pbData.is_null() {
        unsafe { ptr::write_bytes(output.pbData, 0, output.cbData as usize) };
        let _ = unsafe { LocalFree(Some(HLOCAL(output.pbData.cast()))) };
    }
    result
}

fn temporary_path(destination: &Path) -> io::Result<std::path::PathBuf> {
    let parent = destination
        .parent()
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "private path has no parent"))?;
    let leaf = destination
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("private");
    for _ in 0..16 {
        let sequence = TEMPORARY_COUNTER.fetch_add(1, Ordering::Relaxed);
        let candidate = parent.join(format!(".{leaf}.{}.{sequence}.tmp", std::process::id()));
        if !candidate.exists() {
            return Ok(candidate);
        }
    }
    Err(io::Error::new(
        io::ErrorKind::AlreadyExists,
        "private temporary path is busy",
    ))
}

fn write_temporary(path: &Path, bytes: &[u8]) -> io::Result<()> {
    let mut options = OpenOptions::new();
    options.write(true).create_new(true).share_mode(0);
    let mut file = options.open(path)?;
    file.write_all(bytes)?;
    file.sync_all()?;
    drop(file);
    secure_path(path, false)?;
    Ok(())
}

fn commit(temporary: &Path, destination: &Path, replace: bool) -> io::Result<()> {
    let temporary_wide = path_wide(temporary);
    let destination_wide = path_wide(destination);
    let flags = if replace {
        MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH
    } else {
        MOVEFILE_WRITE_THROUGH
    };
    unsafe {
        MoveFileExW(
            PCWSTR(temporary_wide.as_ptr()),
            PCWSTR(destination_wide.as_ptr()),
            flags,
        )
    }
    .map_err(|_| io::Error::last_os_error())
}

fn write_private(path: &Path, bytes: &[u8], replace: bool) -> io::Result<()> {
    let parent = path
        .parent()
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "private path has no parent"))?;
    secure_path(parent, true)?;
    match fs::symlink_metadata(path) {
        Ok(_) => secure_path(path, false)?,
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => return Err(error),
    }
    let temporary = temporary_path(path)?;
    let result = (|| {
        write_temporary(&temporary, bytes)?;
        commit(&temporary, path, replace)
    })();
    if result.is_err() {
        let _ = fs::remove_file(&temporary);
    }
    result
}

pub(crate) fn atomic_write_private(path: &Path, bytes: &[u8]) -> io::Result<()> {
    write_private(path, bytes, true)
}

fn read_existing_key(root: &Path, path: &Path) -> Result<[u8; 32], KeyLoadError> {
    match fs::symlink_metadata(path) {
        Ok(_) => {}
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Err(KeyLoadError::Missing),
        Err(_) => return Err(KeyLoadError::Unavailable),
    }
    secure_path(root, true).map_err(|_| KeyLoadError::Unavailable)?;
    secure_path(path, false).map_err(|_| KeyLoadError::Unavailable)?;
    let encoded = fs::read(path).map_err(|_| KeyLoadError::Unavailable)?;
    if encoded.len() == 32 {
        let mut key = [0_u8; 32];
        key.copy_from_slice(&encoded);
        // Migration is opportunistic and fail-safe: verify DPAPI round-trip before replacing
        // the legacy raw key. If replacement is blocked, keep the ACL-protected old key intact.
        if let Ok(protected) = protect_key(&key) {
            if unprotect_key(&protected).ok().as_ref() == Some(&key) {
                let _ = atomic_write_private(path, &protected);
            }
        }
        return Ok(key);
    }
    if !encoded.starts_with(KEY_MAGIC) {
        return Err(KeyLoadError::Invalid);
    }
    unprotect_key(&encoded).map_err(|_| KeyLoadError::Unavailable)
}

pub(crate) fn load_or_create_key(
    root: &Path,
    path: &Path,
    create_if_missing: bool,
) -> Result<[u8; 32], KeyLoadError> {
    match read_existing_key(root, path) {
        Ok(key) => return Ok(key),
        Err(KeyLoadError::Missing) if create_if_missing => {}
        Err(error) => return Err(error),
    }
    fs::create_dir_all(root).map_err(|_| KeyLoadError::Unavailable)?;
    secure_path(root, true).map_err(|_| KeyLoadError::Unavailable)?;
    let mut key = [0_u8; 32];
    random_fill(&mut key).map_err(|_| KeyLoadError::Unavailable)?;
    let protected = protect_key(&key).map_err(|_| KeyLoadError::Unavailable)?;
    match write_private(path, &protected, false) {
        Ok(()) => Ok(key),
        Err(_) if path.exists() => read_existing_key(root, path),
        Err(_) => Err(KeyLoadError::Unavailable),
    }
}

#[cfg(test)]
fn acl_summary(path: &Path) -> io::Result<(bool, u32)> {
    use windows::Win32::Security::{
        AclSizeInformation, Authorization::GetNamedSecurityInfoW, GetAclInformation,
        GetSecurityDescriptorControl, ACL_SIZE_INFORMATION, PSECURITY_DESCRIPTOR,
        SE_DACL_PROTECTED,
    };
    let wide = path_wide(path);
    let mut dacl = ptr::null_mut();
    let mut descriptor = PSECURITY_DESCRIPTOR::default();
    let status = unsafe {
        GetNamedSecurityInfoW(
            PCWSTR(wide.as_ptr()),
            SE_FILE_OBJECT,
            DACL_SECURITY_INFORMATION,
            None,
            None,
            Some(&mut dacl),
            None,
            &mut descriptor,
        )
    };
    if status != ERROR_SUCCESS {
        return Err(io::Error::from_raw_os_error(status.0 as i32));
    }
    let result = (|| {
        let mut control = 0_u16;
        let mut revision = 0_u32;
        unsafe { GetSecurityDescriptorControl(descriptor, &mut control, &mut revision) }
            .map_err(|_| io::Error::last_os_error())?;
        let mut info = ACL_SIZE_INFORMATION::default();
        unsafe {
            GetAclInformation(
                dacl,
                (&mut info as *mut ACL_SIZE_INFORMATION).cast::<std::ffi::c_void>(),
                std::mem::size_of::<ACL_SIZE_INFORMATION>() as u32,
                AclSizeInformation,
            )
        }
        .map_err(|_| io::Error::last_os_error())?;
        Ok((control & SE_DACL_PROTECTED.0 != 0, info.AceCount))
    })();
    let _ = unsafe { LocalFree(Some(HLOCAL(descriptor.0))) };
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;

    #[test]
    fn windows_private_key_is_dpapi_wrapped_and_acl_protected() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("private");
        let path = root.join("local.key");
        let key = load_or_create_key(&root, &path, true).unwrap();
        let encoded = fs::read(&path).unwrap();
        assert!(encoded.starts_with(KEY_MAGIC));
        assert_ne!(encoded.as_slice(), key.as_slice());
        assert_eq!(load_or_create_key(&root, &path, false).unwrap(), key);
        assert_eq!(acl_summary(&root).unwrap(), (true, 3));
        assert_eq!(acl_summary(&path).unwrap(), (true, 3));
    }

    #[test]
    fn windows_legacy_raw_key_migrates_after_verified_round_trip() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("private");
        fs::create_dir(&root).unwrap();
        let path = root.join("local.key");
        let legacy = [7_u8; 32];
        fs::write(&path, legacy).unwrap();
        assert_eq!(load_or_create_key(&root, &path, false).unwrap(), legacy);
        let migrated = fs::read(&path).unwrap();
        assert!(migrated.starts_with(KEY_MAGIC));
        assert_eq!(unprotect_key(&migrated).unwrap(), legacy);
    }

    #[test]
    fn windows_legacy_key_remains_readable_when_migration_commit_is_blocked() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("private");
        fs::create_dir(&root).unwrap();
        secure_path(&root, true).unwrap();
        let path = root.join("local.key");
        let legacy = [11_u8; 32];
        fs::write(&path, legacy).unwrap();
        secure_path(&path, false).unwrap();
        let mut options = OpenOptions::new();
        options.read(true).share_mode(FILE_SHARE_READ.0);
        let busy = options.open(&path).unwrap();
        assert_eq!(load_or_create_key(&root, &path, false).unwrap(), legacy);
        assert_eq!(fs::read(&path).unwrap(), legacy);
        drop(busy);
    }

    #[test]
    fn windows_store_lock_cleans_only_strict_stale_private_temporary_files() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("provider-credentials-v1");
        fs::create_dir(&root).unwrap();
        secure_path(&root, true).unwrap();
        let stale_document = root.join(".credentials.json.123.7.tmp");
        let stale_key = root.join(".local.key.456.8.tmp");
        let unrelated = root.join(".credentials.json.manual.tmp");
        for path in [&stale_document, &stale_key, &unrelated] {
            fs::write(path, b"synthetic").unwrap();
            secure_path(path, false).unwrap();
        }
        let lock = lock_store(&root).unwrap();
        assert!(!stale_document.exists());
        assert!(!stale_key.exists());
        assert!(unrelated.exists());
        drop(lock);
    }

    #[test]
    fn windows_private_files_reject_additional_hard_links() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("private");
        let key_path = root.join("local.key");
        let key_alias = root.join("local-key-alias");
        let original_key = load_or_create_key(&root, &key_path, true).unwrap();
        fs::hard_link(&key_path, &key_alias).unwrap();
        assert_eq!(
            load_or_create_key(&root, &key_path, false),
            Err(KeyLoadError::Unavailable)
        );
        assert!(fs::read(&key_path).unwrap().starts_with(KEY_MAGIC));
        assert_ne!(fs::read(&key_path).unwrap(), original_key);

        fs::remove_file(&key_alias).unwrap();
        let document_path = root.join("credentials.json");
        let document_alias = root.join("credentials-alias.json");
        atomic_write_private(&document_path, b"old-valid-document").unwrap();
        fs::hard_link(&document_path, &document_alias).unwrap();
        assert!(atomic_write_private(&document_path, b"new-document").is_err());
        assert_eq!(fs::read(&document_path).unwrap(), b"old-valid-document");
        assert_eq!(fs::read(&document_alias).unwrap(), b"old-valid-document");
    }

    #[test]
    fn windows_atomic_replace_keeps_previous_document_when_destination_is_busy() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("private");
        fs::create_dir(&root).unwrap();
        secure_path(&root, true).unwrap();
        let path = root.join("credentials.json");
        atomic_write_private(&path, b"old-valid-document").unwrap();
        let mut options = OpenOptions::new();
        options.read(true).share_mode(FILE_SHARE_READ.0);
        let busy = options.open(&path).unwrap();
        assert!(atomic_write_private(&path, b"new-document").is_err());
        drop(busy);
        assert_eq!(fs::read(&path).unwrap(), b"old-valid-document");
        atomic_write_private(&path, b"new-document").unwrap();
        assert_eq!(fs::read(&path).unwrap(), b"new-document");
    }

    #[test]
    fn windows_store_lock_excludes_a_second_process_handle() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("provider-credentials-v1");
        let first = lock_store(&root).unwrap();
        let path = store_lock_path(&root).unwrap();
        let second = OpenOptions::new()
            .read(true)
            .write(true)
            .open(path)
            .unwrap();
        assert!(second.try_lock_exclusive().is_err());
        drop(first);
        second.try_lock_exclusive().unwrap();
        FileExt::unlock(&second).unwrap();
    }
}
