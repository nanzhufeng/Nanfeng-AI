//! Persistent root selection lives outside both the bundle and the selected root.
use std::{fs, path::{Path, PathBuf}};
use fs2::FileExt;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
struct Selection { active: PathBuf, pending: Option<PathBuf> }
fn read(config: &Path, default: &Path) -> Result<Selection, String> {
    if !config.exists() { return Ok(Selection { active: default.into(), pending: None }); }
    serde_json::from_slice(&fs::read(config).map_err(|_| "无法读取数据路径配置")?).map_err(|_| "数据路径配置损坏，请恢复配置".into())
}
fn write(config: &Path, selection: &Selection) -> Result<(), String> {
    fs::create_dir_all(config.parent().ok_or("配置目录无效")?).map_err(|_| "无法保存路径配置")?;
    let temporary = config.with_extension("pending");
    fs::write(&temporary, serde_json::to_vec(selection).map_err(|_| "无法保存路径配置")?).map_err(|_| "无法保存路径配置")?;
    fs::rename(temporary, config).map_err(|_| "无法保存路径配置".into())
}
fn validate(path: &Path) -> Result<(), String> {
    if !path.is_absolute() || !path.is_dir() || fs::symlink_metadata(path).map_err(|_| "目录不可读")?.file_type().is_symlink() { return Err("请选择可用的本机数据目录".into()); }
    Ok(())
}
fn existing_database(path: &Path) -> Result<bool, String> {
    if fs::read_dir(path).map_err(|_| "目录不可读")?.next().is_none() { return Ok(false); }
    let db = path.join("workspace.sqlite3");
    let connection = rusqlite::Connection::open_with_flags(db, rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY).map_err(|_| "所选目录不是有效的南枫 AI 数据目录")?;
    connection.prepare("SELECT workspace_id FROM workspace_exchange LIMIT 0").map_err(|_| "所选目录不是有效的南枫 AI 数据目录")?;
    let result: String = connection.query_row("PRAGMA quick_check", [], |r| r.get(0)).map_err(|_| "数据校验失败")?;
    if result != "ok" { return Err("数据目录校验未通过".into()); }
    Ok(true)
}
pub fn schedule(config: &Path, current: &Path, selected: &Path) -> Result<(), String> {
    validate(selected)?;
    let selected = fs::canonicalize(selected).map_err(|_| "目录不可用")?;
    let current = fs::canonicalize(current).map_err(|_| "当前目录不可用")?;
    if selected == current { return write(config, &Selection { active: current, pending: None }); }
    if selected.starts_with(&current) || current.starts_with(&selected) { return Err("新旧数据目录不能相互包含".into()); }
    existing_database(&selected)?;
    write(config, &Selection { active: current, pending: Some(selected) })
}
fn copy_tree(source: &Path, target: &Path) -> Result<(), String> {
    fs::create_dir_all(target).map_err(|_| "无法准备新目录")?;
    for entry in fs::read_dir(source).map_err(|_| "无法读取旧目录")? {
        let entry = entry.map_err(|_| "无法读取数据文件")?;
        if entry.file_name() == ".runtime-owner.lock" { continue; }
        let kind = entry.file_type().map_err(|_| "无法读取数据文件")?;
        let destination = target.join(entry.file_name());
        if kind.is_symlink() { return Err("数据目录含符号链接，迁移已停止，旧数据保留".into()); }
        if kind.is_dir() { copy_tree(&entry.path(), &destination)?; }
        else if kind.is_file() { fs::copy(entry.path(), destination).map_err(|_| "数据复制失败，旧数据保留")?; }
        else { return Err("数据目录包含特殊文件，旧数据保留".into()); }
    }
    Ok(())
}
pub fn resolve(config: &Path, default: &Path) -> Result<PathBuf, String> {
    resolve_with_recovery(config, default, |_| Ok(()))
}

pub fn resolve_with_recovery(config: &Path, default: &Path, recover: impl Fn(&Path) -> Result<(), String>) -> Result<PathBuf, String> {
    let mut selection = read(config, default)?;
    if let Some(target) = selection.pending.clone() {
        validate(&selection.active)?;
        let lock = fs::OpenOptions::new().create(true).read(true).write(true).open(selection.active.join(".runtime-owner.lock")).map_err(|_| "无法锁定旧目录")?;
        lock.try_lock_exclusive().map_err(|_| "旧目录仍在使用，请关闭其他实例后再启动")?;
        recover(&selection.active)?;
        let staging = target.with_file_name(format!(".{}.nanfeng-migration", target.file_name().and_then(|s| s.to_str()).ok_or("目录无效")?));
        if !target.exists() && staging.exists() {
            if !existing_database(&staging)? { return Err("迁移暂存目录缺少数据库，未切换".into()); }
            fs::rename(&staging, &target).map_err(|_| "无法恢复目录切换")?;
        }
        validate(&target)?;
        if !existing_database(&target)? {
            if staging.exists() { return Err("迁移暂存目录已存在，请保留旧目录并检查迁移状态".into()); }
            if let Err(error) = copy_tree(&selection.active, &staging) {
                let _ = fs::remove_dir_all(&staging);
                return Err(error);
            }
            if !existing_database(&staging)? { return Err("迁移暂存目录缺少数据库，未切换".into()); }
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
            let lock = fs::OpenOptions::new().create(true).read(true).write(true)
                .open(selection.active.join(".runtime-owner.lock")).map_err(|_| "无法锁定数据目录")?;
            lock.try_lock_exclusive().map_err(|_| "数据目录仍在使用，请关闭其他实例后再启动")?;
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
        let old = temp.path().join("old"); let new = temp.path().join("new");
        seed(&old); fs::create_dir(&new).unwrap();
        let config = temp.path().join("config/location.json");
        schedule(&config, &old, &new).unwrap();
        assert_eq!(resolve(&config, &old).unwrap(), fs::canonicalize(&new).unwrap());
        assert_eq!(resolve(&config, &temp.path().join("changed-default")).unwrap(), fs::canonicalize(&new).unwrap());
        assert_eq!(fs::read(new.join("attachment.bin")).unwrap(), b"retained attachment");
        assert!(old.join("workspace.sqlite3").exists());
    }
    #[test]
    fn existing_directory_is_selected_without_overwriting() {
        let temp = tempfile::tempdir().unwrap(); let old = temp.path().join("old"); let new = temp.path().join("new");
        seed(&old); seed(&new); fs::write(new.join("attachment.bin"), b"different").unwrap();
        let config = temp.path().join("location.json"); schedule(&config, &old, &new).unwrap();
        assert_eq!(resolve(&config, &old).unwrap(), fs::canonicalize(&new).unwrap());
        assert_eq!(fs::read(new.join("attachment.bin")).unwrap(), b"different");
    }
    #[test]
    fn reject_foreign_or_nested_directory_and_never_fall_back_when_missing() {
        let temp = tempfile::tempdir().unwrap(); let old = temp.path().join("old"); seed(&old);
        let nested = old.join("nested"); fs::create_dir(&nested).unwrap();
        let config = temp.path().join("location.json"); assert!(schedule(&config, &old, &nested).is_err());
        fs::write(nested.join("unrelated"), b"keep").unwrap(); assert!(existing_database(&nested).is_err());
        write(&config, &Selection { active: temp.path().join("missing"), pending: None }).unwrap();
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
        fs::write(&config, serde_json::to_vec(&serde_json::json!({"active":old,"pending":target})).unwrap()).unwrap();
        let lock = fs::OpenOptions::new().create(true).read(true).write(true).open(old.join(".runtime-owner.lock")).unwrap();
        lock.try_lock_exclusive().unwrap();
        let result = super::resolve(&config, &old);
        println!("PROBE locked_recovery returned_error={} target_published={} staging_remaining={} source_retained={}",
                 result.is_err(), target.exists(), staging.exists(), old.join("workspace.sqlite3").exists());
        assert!(result.is_err());
        assert!(!target.exists(), "Recovery published target before proving exclusive source ownership");
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
        println!("PROBE selected_missing_db accepted={} attachment_retained={}", result.is_ok(), target.join("attachment.bin").exists());
        assert!(result.is_err(), "Configured root without its database was accepted");
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
        let lock = fs::OpenOptions::new().create(true).read(true).write(true).open(old.join(".runtime-owner.lock")).unwrap();
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
        write(&config, &Selection { active: root.clone(), pending: None }).unwrap();
        assert!(resolve(&config, &root).is_err());
        assert!(!root.join("workspace.sqlite3").exists());
    }

    #[test]
    fn empty_recovery_staging_is_never_published() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old"); seed(&old);
        let target = temp.path().join("new");
        let staging = temp.path().join(".new.nanfeng-migration");
        fs::create_dir(&staging).unwrap();
        let config = temp.path().join("location.json");
        write(&config, &Selection { active: old.clone(), pending: Some(target.clone()) }).unwrap();
        let before = fs::read(&config).unwrap();
        assert!(resolve(&config, &old).is_err());
        assert!(!target.exists());
        assert!(staging.exists());
        assert_eq!(fs::read(&config).unwrap(), before);
    }

    #[test]
    fn interrupted_restore_runs_under_lock_before_database_validation() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("selected"); fs::create_dir(&root).unwrap();
        let config = temp.path().join("location.json");
        write(&config, &Selection { active: root.clone(), pending: None }).unwrap();
        let selected = resolve_with_recovery(&config, &root, |path| {
            let competing = fs::OpenOptions::new().read(true).write(true).open(path.join(".runtime-owner.lock")).unwrap();
            assert!(competing.try_lock_exclusive().is_err());
            seed(path); // Synthetic stand-in for the existing backup checkpoint owner.
            Ok(())
        }).unwrap();
        assert_eq!(selected, root);
        assert!(existing_database(&root).unwrap());
    }
}
