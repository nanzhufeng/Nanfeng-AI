//! Persistent root selection lives outside both the bundle and the selected root.
use fs2::FileExt;
use serde::{Deserialize, Serialize};
use std::{
    collections::BTreeMap,
    fs,
    io::Read,
    path::{Path, PathBuf},
};

#[derive(Serialize, Deserialize)]
struct Selection {
    active: PathBuf,
    pending: Option<PathBuf>,
}
fn read(config: &Path, default: &Path) -> Result<Selection, String> {
    if !config.exists() {
        return Ok(Selection {
            active: default.into(),
            pending: None,
        });
    }
    serde_json::from_slice(&fs::read(config).map_err(|_| "无法读取数据路径配置")?)
        .map_err(|_| "数据路径配置损坏，请恢复配置".into())
}
fn write(config: &Path, selection: &Selection) -> Result<(), String> {
    fs::create_dir_all(config.parent().ok_or("配置目录无效")?).map_err(|_| "无法保存路径配置")?;
    let temporary = config.with_extension("pending");
    fs::write(
        &temporary,
        serde_json::to_vec(selection).map_err(|_| "无法保存路径配置")?,
    )
    .map_err(|_| "无法保存路径配置")?;
    fs::rename(temporary, config).map_err(|_| "无法保存路径配置".into())
}
fn is_link_or_reparse(metadata: &fs::Metadata) -> bool {
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        metadata.file_attributes() & 0x400 != 0 // FILE_ATTRIBUTE_REPARSE_POINT
    }
    #[cfg(not(windows))]
    {
        metadata.file_type().is_symlink()
    }
}
#[cfg(windows)]
fn is_unc_path(path: &Path) -> bool {
    use std::path::{Component, Prefix};
    matches!(
        path.components().next(),
        Some(Component::Prefix(prefix))
            if matches!(prefix.kind(), Prefix::UNC(..) | Prefix::VerbatimUNC(..))
    )
}
#[cfg(windows)]
fn validate_local_volume(path: &Path) -> Result<(), String> {
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Storage::FileSystem::{GetDriveTypeW, GetVolumePathNameW};

    // GetVolumePathNameW resolves mapped letters as well as ordinary drive paths.
    // GetDriveTypeW: 2 = removable, 3 = fixed, 4 = remote; fail closed otherwise.
    let input: Vec<u16> = path.as_os_str().encode_wide().chain(Some(0)).collect();
    let mut volume = vec![0u16; 32768];
    if unsafe { GetVolumePathNameW(input.as_ptr(), volume.as_mut_ptr(), volume.len() as u32) } == 0
    {
        return Err("无法确认数据目录所在磁盘".into());
    }
    if !matches!(unsafe { GetDriveTypeW(volume.as_ptr()) }, 2 | 3) {
        return Err("请选择本机固定磁盘或可移动磁盘上的数据目录".into());
    }
    Ok(())
}
fn validate(path: &Path) -> Result<(), String> {
    #[cfg(windows)]
    if is_unc_path(path) {
        return Err("不支持将数据目录设在网络共享上".into());
    }
    if !path.is_absolute() || !path.is_dir() {
        return Err("请选择可用的本机数据目录".into());
    }
    // A child of a junction looks like an ordinary directory to symlink_metadata.
    // Inspect the original path before canonicalizing it in schedule().
    for ancestor in path
        .ancestors()
        .take_while(|ancestor| ancestor.is_absolute())
    {
        if is_link_or_reparse(&fs::symlink_metadata(ancestor).map_err(|_| "目录不可读")?) {
            return Err("请选择不经过链接或重解析点的本机数据目录".into());
        }
    }
    #[cfg(windows)]
    validate_local_volume(path)?;
    Ok(())
}
fn checked_area_database(path: &Path, area: crate::DesktopDataArea) -> Result<bool, String> {
    if fs::read_dir(path)
        .map_err(|_| "目录不可读")?
        .next()
        .is_none()
    {
        return Ok(false);
    }
    let db = path.join("workspace.sqlite3");
    let connection =
        rusqlite::Connection::open_with_flags(db, rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY)
            .map_err(|_| "所选目录不是有效的南枫 AI 数据目录")?;
    connection
        .prepare("SELECT workspace_id FROM workspace_exchange LIMIT 0")
        .map_err(|_| "所选目录不是有效的南枫 AI 数据目录")?;
    if crate::desktop_data_area::read_area(&connection)? != area {
        return Err("所选目录的数据库区域身份不匹配".into());
    }
    let result: String = connection
        .query_row("PRAGMA quick_check", [], |r| r.get(0))
        .map_err(|_| "数据校验失败")?;
    if result != "ok" {
        return Err("数据目录校验未通过".into());
    }
    Ok(true)
}
fn existing_database(path: &Path) -> Result<bool, String> {
    if !checked_area_database(path, crate::DesktopDataArea::Chat)? {
        return Ok(false);
    }
    let work = path.join("work-area");
    if work.exists() {
        validate(&work)?;
        if !checked_area_database(&work, crate::DesktopDataArea::Work)? {
            return Err("工作区分库缺少有效数据库".into());
        }
    }
    let shared = path.join("app-configuration.sqlite3");
    if shared.exists() {
        let shared_metadata = fs::symlink_metadata(&shared).map_err(|_| "共享配置数据库不可读")?;
        if !shared_metadata.file_type().is_file() || is_link_or_reparse(&shared_metadata) {
            return Err("共享配置数据库类型无效".into());
        }
        let connection = rusqlite::Connection::open_with_flags(
            &shared,
            rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY,
        )
        .map_err(|_| "共享配置数据库不可读")?;
        let result: String = connection
            .query_row("PRAGMA quick_check", [], |r| r.get(0))
            .map_err(|_| "共享配置数据库校验失败")?;
        if result != "ok" {
            return Err("共享配置数据库校验失败".into());
        }
    }
    Ok(true)
}
pub fn schedule(config: &Path, current: &Path, selected: &Path) -> Result<(), String> {
    validate(selected)?;
    let selected = fs::canonicalize(selected).map_err(|_| "目录不可用")?;
    let current = fs::canonicalize(current).map_err(|_| "当前目录不可用")?;
    if selected == current {
        return write(
            config,
            &Selection {
                active: current,
                pending: None,
            },
        );
    }
    if selected.starts_with(&current) || current.starts_with(&selected) {
        return Err("新旧数据目录不能相互包含".into());
    }
    existing_database(&selected)?;
    write(
        config,
        &Selection {
            active: current,
            pending: Some(selected),
        },
    )
}

pub fn pending_path(config: &Path, current: &Path) -> Result<Option<PathBuf>, String> {
    Ok(read(config, current)?.pending)
}
fn copy_tree(source: &Path, target: &Path) -> Result<(), String> {
    let mut directories = vec![(source.to_path_buf(), target.to_path_buf(), true)];
    while let Some((source, target, is_root)) = directories.pop() {
        fs::create_dir_all(&target).map_err(|_| "无法准备新目录")?;
        for entry in fs::read_dir(&source).map_err(|_| "无法读取旧目录")? {
            let entry = entry.map_err(|_| "无法读取数据文件")?;
            if is_root && entry.file_name() == ".runtime-owner.lock" {
                continue;
            }
            let metadata = fs::symlink_metadata(entry.path()).map_err(|_| "无法读取数据文件")?;
            let kind = metadata.file_type();
            let destination = target.join(entry.file_name());
            if is_link_or_reparse(&metadata) {
                return Err("数据目录含符号链接，迁移已停止，旧数据保留".into());
            }
            if kind.is_dir() {
                directories.push((entry.path(), destination, false));
            } else if kind.is_file() {
                fs::copy(entry.path(), destination).map_err(|_| "数据复制失败，旧数据保留")?;
            } else {
                return Err("数据目录包含特殊文件，旧数据保留".into());
            }
        }
    }
    Ok(())
}
fn same_tree(source: &Path, target: &Path) -> Result<bool, String> {
    fn entries(
        path: &Path,
        is_root: bool,
    ) -> Result<BTreeMap<std::ffi::OsString, PathBuf>, String> {
        fs::read_dir(path)
            .map_err(|_| "数据复制核对时目录不可读".to_owned())?
            .map(|entry| {
                let entry = entry.map_err(|_| "数据复制核对时条目不可读")?;
                Ok((entry.file_name(), entry.path()))
            })
            .filter(|entry: &Result<(std::ffi::OsString, PathBuf), String>| {
                !is_root || !matches!(entry, Ok((name, _)) if name == ".runtime-owner.lock")
            })
            .collect()
    }
    fn same_file_bytes(original: &Path, copied: &Path) -> Result<bool, String> {
        let original_len = fs::metadata(original)
            .map_err(|_| "数据复制核对时源文件不可读")?
            .len();
        if original_len
            != fs::metadata(copied)
                .map_err(|_| "数据复制核对时目标文件不可读")?
                .len()
        {
            return Ok(false);
        }
        let mut original_file = fs::File::open(original).map_err(|_| "源文件无法核对")?;
        let mut copied_file = fs::File::open(copied).map_err(|_| "目标文件无法核对")?;
        let (mut original_bytes, mut copied_bytes) = (vec![0u8; 65536], vec![0u8; 65536]);
        let mut remaining = original_len;
        while remaining > 0 {
            let size = remaining.min(original_bytes.len() as u64) as usize;
            original_file
                .read_exact(&mut original_bytes[..size])
                .map_err(|_| "源文件核对中断")?;
            copied_file
                .read_exact(&mut copied_bytes[..size])
                .map_err(|_| "目标文件核对中断")?;
            if original_bytes[..size] != copied_bytes[..size] {
                return Ok(false);
            }
            remaining -= size as u64;
        }
        Ok(true)
    }
    let mut directories = vec![(source.to_path_buf(), target.to_path_buf(), true)];
    while let Some((source, target, is_root)) = directories.pop() {
        let left = entries(&source, is_root)?;
        let right = entries(&target, is_root)?;
        if left.keys().ne(right.keys()) {
            return Ok(false);
        }
        for (name, original) in left {
            let copied = &right[&name];
            let original_metadata =
                fs::symlink_metadata(&original).map_err(|_| "数据复制核对时源文件不可读")?;
            let copied_metadata =
                fs::symlink_metadata(copied).map_err(|_| "数据复制核对时目标文件不可读")?;
            let original_kind = original_metadata.file_type();
            let copied_kind = copied_metadata.file_type();
            if is_link_or_reparse(&original_metadata) || is_link_or_reparse(&copied_metadata) {
                return Err("数据复制核对发现符号链接，未切换".into());
            }
            if original_kind.is_dir() && copied_kind.is_dir() {
                directories.push((original, copied.clone(), false));
            } else if original_kind.is_file() && copied_kind.is_file() {
                if !same_file_bytes(&original, copied)? {
                    return Ok(false);
                }
            } else {
                return Err("数据复制核对发现特殊文件，未切换".into());
            }
        }
    }
    Ok(true)
}
pub fn resolve(config: &Path, default: &Path) -> Result<PathBuf, String> {
    resolve_with_recovery(config, default, |_| Ok(()))
}

pub fn resolve_with_recovery(
    config: &Path,
    default: &Path,
    recover: impl Fn(&Path) -> Result<(), String>,
) -> Result<PathBuf, String> {
    let mut selection = read(config, default)?;
    if let Some(target) = selection.pending.clone() {
        validate(&selection.active)?;
        let lock = fs::OpenOptions::new()
            .create(true)
            .read(true)
            .write(true)
            .open(selection.active.join(".runtime-owner.lock"))
            .map_err(|_| "无法锁定旧目录")?;
        lock.try_lock_exclusive()
            .map_err(|_| "旧目录仍在使用，请关闭其他实例后再启动")?;
        recover(&selection.active)?;
        let staging = target.with_file_name(format!(
            ".{}.nanfeng-migration",
            target
                .file_name()
                .and_then(|s| s.to_str())
                .ok_or("目录无效")?
        ));
        if staging.exists() {
            validate(&staging)?;
            if target.exists() {
                validate(&target)?;
                if fs::read_dir(&target)
                    .map_err(|_| "目标目录不可读，未切换")?
                    .next()
                    .is_some()
                {
                    return Err("迁移暂存目录与非空目标目录同时存在，未切换".into());
                }
            }
            if !same_tree(&selection.active, &staging)? {
                return Err("迁移暂存目录与旧数据不一致，未切换".into());
            }
            if !existing_database(&staging)? {
                return Err("迁移暂存目录缺少数据库，未切换".into());
            }
            if target.exists() {
                fs::remove_dir(&target).map_err(|_| "目标目录发生变化，未切换")?;
            }
            fs::rename(&staging, &target).map_err(|_| "无法恢复目录切换")?;
        }
        validate(&target)?;
        if !existing_database(&target)? {
            if staging.exists() {
                return Err("迁移暂存目录已存在，请保留旧目录并检查迁移状态".into());
            }
            if let Err(error) = copy_tree(&selection.active, &staging) {
                let _ = fs::remove_dir_all(&staging);
                return Err(error);
            }
            if !same_tree(&selection.active, &staging)? {
                return Err("迁移复制与旧数据不一致，未切换".into());
            }
            if !existing_database(&staging)? {
                return Err("迁移暂存目录缺少数据库，未切换".into());
            }
            fs::remove_dir(&target).map_err(|_| "目标目录发生变化，未切换")?;
            fs::rename(&staging, &target).map_err(|_| "无法发布新目录，旧数据保留")?;
        }
        selection.active = target;
        selection.pending = None;
        write(config, &selection)?;
    }
    if config.exists() {
        validate(&selection.active)?;
        if !matches!(existing_database(&selection.active), Ok(true)) {
            // A legitimate interrupted backup switch may temporarily have no database.
            // Only its existing recovery owner may restore it, under exclusive ownership.
            let lock = fs::OpenOptions::new()
                .create(true)
                .read(true)
                .write(true)
                .open(selection.active.join(".runtime-owner.lock"))
                .map_err(|_| "无法锁定数据目录")?;
            lock.try_lock_exclusive()
                .map_err(|_| "数据目录仍在使用，请关闭其他实例后再启动")?;
            recover(&selection.active)?;
            if !matches!(existing_database(&selection.active), Ok(true)) {
                return Err("已配置的数据目录缺少有效数据库，请恢复数据后重试".into());
            }
        }
    }
    Ok(selection.active)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn seed(root: &Path) {
        fs::create_dir_all(root).unwrap();
        let db = rusqlite::Connection::open(root.join("workspace.sqlite3")).unwrap();
        db.execute_batch("CREATE TABLE workspace_exchange(workspace_id TEXT); INSERT INTO workspace_exchange VALUES ('retained');").unwrap();
        fs::write(root.join("attachment.bin"), b"retained attachment").unwrap();
    }
    #[test]
    fn migrate_then_reopen_uses_selected_directory_and_keeps_source() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let new = temp.path().join("new");
        seed(&old);
        seed(&old.join("work-area"));
        fs::write(
            old.join("work-area/.runtime-owner.lock"),
            b"nested lock entry",
        )
        .unwrap();
        let work_db = rusqlite::Connection::open(old.join("work-area/workspace.sqlite3")).unwrap();
        work_db.execute_batch("CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY, area TEXT); INSERT INTO desktop_data_area_v1 VALUES (1,'WORK');").unwrap();
        let config_db = rusqlite::Connection::open(old.join("app-configuration.sqlite3")).unwrap();
        config_db
            .execute_batch(
                "CREATE TABLE setting(value TEXT); INSERT INTO setting VALUES ('shared');",
            )
            .unwrap();
        fs::create_dir(&new).unwrap();
        let config = temp.path().join("config/location.json");
        schedule(&config, &old, &new).unwrap();
        assert_eq!(
            resolve(&config, &old).unwrap(),
            fs::canonicalize(&new).unwrap()
        );
        assert_eq!(
            resolve(&config, &temp.path().join("changed-default")).unwrap(),
            fs::canonicalize(&new).unwrap()
        );
        assert_eq!(
            fs::read(new.join("attachment.bin")).unwrap(),
            b"retained attachment"
        );
        assert_eq!(
            fs::read(new.join("work-area/attachment.bin")).unwrap(),
            b"retained attachment"
        );
        assert_eq!(
            fs::read(new.join("work-area/.runtime-owner.lock")).unwrap(),
            b"nested lock entry"
        );
        assert!(existing_database(&new).unwrap());
        assert!(old.join("workspace.sqlite3").exists());
    }
    #[test]
    fn repeating_a_pending_selection_replaces_the_existing_location_config() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let new = temp.path().join("new");
        seed(&old);
        fs::create_dir(&new).unwrap();
        let config = temp.path().join("config/location.json");
        schedule(&config, &old, &new).unwrap();
        schedule(&config, &old, &new).unwrap();
        assert_eq!(pending_path(&config, &old).unwrap(), Some(fs::canonicalize(&new).unwrap()));
        assert_eq!(resolve(&config, &old).unwrap(), fs::canonicalize(&new).unwrap());
        assert!(pending_path(&config, &old).unwrap().is_none());
        assert!(old.join("workspace.sqlite3").exists());
    }

    #[test]
    fn existing_directory_is_selected_without_overwriting() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let new = temp.path().join("new");
        seed(&old);
        seed(&new);
        fs::write(new.join("attachment.bin"), b"different").unwrap();
        let config = temp.path().join("location.json");
        schedule(&config, &old, &new).unwrap();
        assert_eq!(
            resolve(&config, &old).unwrap(),
            fs::canonicalize(&new).unwrap()
        );
        assert_eq!(fs::read(new.join("attachment.bin")).unwrap(), b"different");
    }
    #[test]
    fn work_database_cannot_be_selected_as_the_application_root() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let work = temp.path().join("work");
        seed(&old);
        seed(&work);
        let db = rusqlite::Connection::open(work.join("workspace.sqlite3")).unwrap();
        db.execute_batch("CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY, area TEXT); INSERT INTO desktop_data_area_v1 VALUES (1,'WORK');").unwrap();
        let config = temp.path().join("location.json");
        assert!(schedule(&config, &old, &work).is_err());
        assert!(!config.exists());
        assert_eq!(
            fs::read(old.join("attachment.bin")).unwrap(),
            b"retained attachment"
        );
    }
    #[cfg(windows)]
    #[test]
    fn selected_child_beneath_windows_junction_is_rejected() {
        let temp = tempfile::tempdir().unwrap();
        let target = temp.path().join("target");
        let child = target.join("child");
        fs::create_dir_all(&child).unwrap();
        let link = temp.path().join("junction");
        let quoted = |path: &Path| path.to_string_lossy().replace('\'', "''");
        let command = format!(
            "New-Item -ItemType Junction -Path '{}' -Target '{}' -ErrorAction Stop | Out-Null",
            quoted(&link),
            quoted(&target)
        );
        let output = std::process::Command::new("powershell")
            .args(["-NoProfile", "-NonInteractive", "-Command", &command])
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "cannot create Windows junction fixture"
        );
        let direct = validate(&link);
        let nested = validate(&link.join("child"));
        fs::remove_dir(&link).unwrap();
        assert!(direct.is_err());
        assert!(nested.is_err());
    }
    #[cfg(windows)]
    #[test]
    fn unc_paths_are_rejected_before_accessing_the_share_but_local_volume_is_allowed() {
        let unc = Path::new(r"\\example.invalid\share\nanfeng-ai");
        let verbatim_unc = Path::new(r"\\?\UNC\example.invalid\share\nanfeng-ai");
        assert!(is_unc_path(unc));
        assert!(is_unc_path(verbatim_unc));
        assert_eq!(validate(unc).unwrap_err(), "不支持将数据目录设在网络共享上");
        assert_eq!(
            validate(verbatim_unc).unwrap_err(),
            "不支持将数据目录设在网络共享上"
        );
        let local = tempfile::tempdir().unwrap();
        assert!(!is_unc_path(local.path()));
        assert!(validate(local.path()).is_ok());
    }
    #[test]
    fn existing_chat_root_with_damaged_work_database_is_rejected() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("target");
        seed(&old);
        seed(&target);
        fs::create_dir(target.join("work-area")).unwrap();
        fs::write(target.join("work-area/workspace.sqlite3"), b"not sqlite").unwrap();
        let config = temp.path().join("location.json");
        assert!(schedule(&config, &old, &target).is_err());
        assert!(!config.exists());
    }
    #[test]
    fn interrupted_copy_with_changed_attachment_cannot_publish() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("new");
        seed(&old);
        fs::create_dir(&target).unwrap();
        let config = temp.path().join("location.json");
        schedule(&config, &old, &target).unwrap();
        // This is the crash point after removing the empty target and before publishing staging.
        fs::remove_dir(&target).unwrap();
        let staging = temp.path().join(".new.nanfeng-migration");
        copy_tree(&old, &staging).unwrap();
        fs::write(staging.join("attachment.bin"), b"changed after copy").unwrap();
        assert!(resolve(&config, &old).is_err());
        assert!(!target.exists());
        assert!(staging.exists());
        assert_eq!(
            fs::read(old.join("attachment.bin")).unwrap(),
            b"retained attachment"
        );
        assert_eq!(
            read(&config, &old).unwrap().active,
            fs::canonicalize(old).unwrap()
        );
    }
    #[test]
    fn completed_staging_with_original_empty_target_publishes_after_restart() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("new");
        seed(&old);
        fs::create_dir(&target).unwrap();
        let config = temp.path().join("location.json");
        schedule(&config, &old, &target).unwrap();
        // The process stopped after copying and checking, before removing the empty target.
        let staging = temp.path().join(".new.nanfeng-migration");
        copy_tree(&old, &staging).unwrap();
        assert_eq!(
            resolve(&config, &old).unwrap(),
            fs::canonicalize(&target).unwrap()
        );
        assert!(!staging.exists());
        assert_eq!(
            fs::read(target.join("attachment.bin")).unwrap(),
            b"retained attachment"
        );
        assert!(old.join("workspace.sqlite3").exists());
    }
    #[test]
    fn changed_staging_with_original_empty_target_keeps_source_and_pending_selection() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("new");
        seed(&old);
        fs::create_dir(&target).unwrap();
        let config = temp.path().join("location.json");
        schedule(&config, &old, &target).unwrap();
        let staging = temp.path().join(".new.nanfeng-migration");
        copy_tree(&old, &staging).unwrap();
        fs::write(staging.join("attachment.bin"), b"changed after copy").unwrap();
        assert!(resolve(&config, &old).is_err());
        assert!(target.is_dir());
        assert!(staging.is_dir());
        assert_eq!(
            fs::read(old.join("attachment.bin")).unwrap(),
            b"retained attachment"
        );
        let selected = read(&config, &old).unwrap();
        assert_eq!(selected.active, fs::canonicalize(&old).unwrap());
        assert_eq!(selected.pending, Some(fs::canonicalize(&target).unwrap()));
    }
    #[test]
    fn copy_preserves_committed_wal_content() {
        let temp = tempfile::tempdir().unwrap();
        let live = temp.path().join("live");
        let old = temp.path().join("old");
        let target = temp.path().join("target");
        seed(&live);
        let connection = rusqlite::Connection::open(live.join("workspace.sqlite3")).unwrap();
        connection.execute_batch("PRAGMA journal_mode=WAL; PRAGMA wal_autocheckpoint=0; INSERT INTO workspace_exchange VALUES ('committed-in-wal');").unwrap();
        assert!(
            fs::metadata(live.join("workspace.sqlite3-wal"))
                .unwrap()
                .len()
                > 0
        );
        copy_tree(&live, &old).unwrap();
        drop(connection);
        fs::create_dir(&target).unwrap();
        let config = temp.path().join("location.json");
        schedule(&config, &old, &target).unwrap();
        assert_eq!(
            resolve(&config, &old).unwrap(),
            fs::canonicalize(&target).unwrap()
        );
        let copied = rusqlite::Connection::open(target.join("workspace.sqlite3")).unwrap();
        let retained: i64 = copied
            .query_row(
                "SELECT COUNT(*) FROM workspace_exchange WHERE workspace_id='committed-in-wal'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(retained, 1);
    }
    #[test]
    fn nested_private_files_copy_without_recursive_stack_growth() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("target");
        seed(&old);
        let mut deep = old.join("assets");
        for _ in 0..45 {
            deep.push("d");
        }
        fs::create_dir_all(&deep).unwrap();
        fs::write(deep.join("private.bin"), b"nested private bytes").unwrap();
        fs::create_dir(&target).unwrap();
        let config = temp.path().join("location.json");
        schedule(&config, &old, &target).unwrap();
        resolve(&config, &old).unwrap();
        let relative = deep.strip_prefix(&old).unwrap();
        assert_eq!(
            fs::read(target.join(relative).join("private.bin")).unwrap(),
            b"nested private bytes"
        );
    }
    #[test]
    fn committed_schema45_root_moves_both_areas_and_shared_configuration() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("target");
        fs::create_dir(&old).unwrap();
        let lock = fs::OpenOptions::new()
            .create(true)
            .read(true)
            .write(true)
            .open(old.join(".runtime-owner.lock"))
            .unwrap();
        lock.try_lock_exclusive().unwrap();
        crate::desktop_schema_reconcile::reconcile_schema45_at_startup_under_existing_lock(
            &old, &lock,
        )
        .unwrap();
        drop(lock);
        let (chat, work) = crate::open_desktop_area_stores_for_startup(
            old.clone(),
            crate::DesktopStartupMode::Normal,
        )
        .unwrap();
        chat.ensure_local_workspace().unwrap();
        work.unwrap().ensure_local_workspace().unwrap();
        drop(chat);
        fs::write(old.join("assets/chat-marker.bin"), b"chat-only").unwrap();
        fs::write(old.join("work-area/assets/work-marker.bin"), b"work-only").unwrap();
        let shared_before = fs::read(old.join("app-configuration.sqlite3")).unwrap();
        assert!(!shared_before.is_empty());

        fs::create_dir(&target).unwrap();
        let config = temp.path().join("location.json");
        schedule(&config, &old, &target).unwrap();
        assert_eq!(
            resolve(&config, &old).unwrap(),
            fs::canonicalize(&target).unwrap()
        );
        assert_eq!(
            fs::read(target.join("assets/chat-marker.bin")).unwrap(),
            b"chat-only"
        );
        assert_eq!(
            fs::read(target.join("work-area/assets/work-marker.bin")).unwrap(),
            b"work-only"
        );
        assert_eq!(
            fs::read(target.join("app-configuration.sqlite3")).unwrap(),
            shared_before
        );
        assert!(old.join("workspace.sqlite3").exists());
        assert!(old.join("work-area/workspace.sqlite3").exists());

        let (moved_chat, moved_work) = crate::open_desktop_area_stores_for_startup(
            target.clone(),
            crate::DesktopStartupMode::Normal,
        )
        .unwrap();
        assert_eq!(
            crate::desktop_data_area::read_area(&moved_chat.connection().unwrap()).unwrap(),
            crate::DesktopDataArea::Chat
        );
        let moved_work = moved_work.unwrap();
        assert_eq!(
            crate::desktop_data_area::read_area(&moved_work.connection().unwrap()).unwrap(),
            crate::DesktopDataArea::Work
        );
        assert_eq!(
            moved_chat
                .connection()
                .unwrap()
                .query_row("SELECT COUNT(*) FROM workspaces", [], |row| row
                    .get::<_, i64>(0))
                .unwrap(),
            1
        );
        assert_eq!(
            moved_work
                .connection()
                .unwrap()
                .query_row("SELECT COUNT(*) FROM workspaces", [], |row| row
                    .get::<_, i64>(0))
                .unwrap(),
            1
        );
        assert_eq!(
            resolve(&config, &old).unwrap(),
            fs::canonicalize(target).unwrap()
        );
    }
    #[test]
    fn reject_foreign_or_nested_directory_and_never_fall_back_when_missing() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        seed(&old);
        let nested = old.join("nested");
        fs::create_dir(&nested).unwrap();
        let config = temp.path().join("location.json");
        assert!(schedule(&config, &old, &nested).is_err());
        fs::write(nested.join("unrelated"), b"keep").unwrap();
        assert!(existing_database(&nested).is_err());
        write(
            &config,
            &Selection {
                active: temp.path().join("missing"),
                pending: None,
            },
        )
        .unwrap();
        assert!(resolve(&config, &old).is_err());
    }
    #[test]
    fn recovery_must_not_publish_before_acquiring_source_lock() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("new");
        let staging = temp.path().join(".new.nanfeng-migration");
        let config = temp.path().join("location.json");
        seed(&old);
        seed(&staging);
        fs::write(
            &config,
            serde_json::to_vec(&serde_json::json!({"active":old,"pending":target})).unwrap(),
        )
        .unwrap();
        let lock = fs::OpenOptions::new()
            .create(true)
            .read(true)
            .write(true)
            .open(old.join(".runtime-owner.lock"))
            .unwrap();
        lock.try_lock_exclusive().unwrap();
        let result = super::resolve(&config, &old);
        println!("PROBE locked_recovery returned_error={} target_published={} staging_remaining={} source_retained={}",
                 result.is_err(), target.exists(), staging.exists(), old.join("workspace.sqlite3").exists());
        assert!(result.is_err());
        assert!(
            !target.exists(),
            "Recovery published target before proving exclusive source ownership"
        );
    }

    #[test]
    fn selected_root_missing_database_must_fail_closed() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("new");
        let config = temp.path().join("location.json");
        seed(&old);
        seed(&target);
        super::schedule(&config, &old, &target).unwrap();
        super::resolve(&config, &old).unwrap();
        fs::remove_file(target.join("workspace.sqlite3")).unwrap();
        let result = super::resolve(&config, &old);
        println!(
            "PROBE selected_missing_db accepted={} attachment_retained={}",
            result.is_ok(),
            target.join("attachment.bin").exists()
        );
        assert!(
            result.is_err(),
            "Configured root without its database was accepted"
        );
    }

    #[test]
    fn normal_migration_respects_source_lock() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("new");
        let config = temp.path().join("location.json");
        seed(&old);
        fs::create_dir(&target).unwrap();
        super::schedule(&config, &old, &target).unwrap();
        let lock = fs::OpenOptions::new()
            .create(true)
            .read(true)
            .write(true)
            .open(old.join(".runtime-owner.lock"))
            .unwrap();
        lock.try_lock_exclusive().unwrap();
        assert!(super::resolve(&config, &old).is_err());
        assert!(target.read_dir().unwrap().next().is_none());
    }

    #[test]
    fn unconfigured_first_start_is_allowed_but_configured_empty_root_is_not() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("new-install");
        let config = temp.path().join("location.json");
        assert_eq!(resolve(&config, &root).unwrap(), root);
        fs::create_dir(&root).unwrap();
        write(
            &config,
            &Selection {
                active: root.clone(),
                pending: None,
            },
        )
        .unwrap();
        assert!(resolve(&config, &root).is_err());
        assert!(!root.join("workspace.sqlite3").exists());
    }

    #[test]
    fn empty_recovery_staging_is_never_published() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        seed(&old);
        let target = temp.path().join("new");
        let staging = temp.path().join(".new.nanfeng-migration");
        fs::create_dir(&staging).unwrap();
        let config = temp.path().join("location.json");
        write(
            &config,
            &Selection {
                active: old.clone(),
                pending: Some(target.clone()),
            },
        )
        .unwrap();
        let before = fs::read(&config).unwrap();
        assert!(resolve(&config, &old).is_err());
        assert!(!target.exists());
        assert!(staging.exists());
        assert_eq!(fs::read(&config).unwrap(), before);
    }

    #[test]
    fn interrupted_restore_runs_under_lock_before_database_validation() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("selected");
        fs::create_dir(&root).unwrap();
        let config = temp.path().join("location.json");
        write(
            &config,
            &Selection {
                active: root.clone(),
                pending: None,
            },
        )
        .unwrap();
        let selected = resolve_with_recovery(&config, &root, |path| {
            let competing = fs::OpenOptions::new()
                .read(true)
                .write(true)
                .open(path.join(".runtime-owner.lock"))
                .unwrap();
            assert!(competing.try_lock_exclusive().is_err());
            seed(path); // Synthetic stand-in for the existing backup checkpoint owner.
            Ok(())
        })
        .unwrap();
        assert_eq!(selected, root);
        assert!(existing_database(&root).unwrap());
    }
}

#[cfg(all(test, windows))]
#[path = "desktop_storage_windows_locked_tests.rs"]
mod desktop_storage_windows_locked_tests;
