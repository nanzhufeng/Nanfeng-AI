//! Offline, recoverable replacement of the complete CHAT/WORK business root.
//! The caller must own the root process lock and must have stopped every live store.
use crate::{desktop_schema_reconcile, open_desktop_area_stores_for_startup, DesktopStartupMode};
use serde::{Deserialize, Serialize};
use std::{
    collections::BTreeSet,
    fs,
    path::{Path, PathBuf},
};

const MARKER: &str = ".full-local-delete-v1.json";
const COMMITTED: &str = ".full-local-delete-v1.committed";
const COMMIT_TEMP: &str = ".full-local-delete-v1.committing";
const OUTCOME: &str = ".full-local-delete-v1.outcome.json";
const OUTCOME_TEMP: &str = ".full-local-delete-v1.outcome.tmp";
const LOCK: &str = ".runtime-owner.lock";
// The isolated acceptance identity is not user business data and must survive
// a restart on the same marked synthetic root.
const ACCEPTANCE_IDENTITY: &str = ".nanfeng-windows-acceptance.json";

#[derive(Debug, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Journal {
    version: u8,
    operation_id: String,
    candidate_leaf: String,
    quarantine_leaf: String,
    old_entries: Vec<String>,
    candidate_entries: Vec<String>,
}

#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Receipt {
    pub cleanup_pending: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
pub(crate) struct Outcome {
    version: u8,
    pub status: String,
    pub cleanup_pending: bool,
}

fn write_outcome(root: &Path, status: &str, cleanup_pending: bool) -> Result<(), String> {
    use std::io::Write;
    let outcome = Outcome {
        version: 1,
        status: status.to_owned(),
        cleanup_pending,
    };
    let bytes = serde_json::to_vec(&outcome).map_err(|_| "全部删除结果无法编码")?;
    let temporary = root.join(OUTCOME_TEMP);
    remove_outcome_temporary(&temporary)?;
    let mut file = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&temporary)
        .map_err(|_| "全部删除结果暂存文件无法创建")?;
    file.write_all(&bytes)
        .and_then(|_| file.sync_all())
        .map_err(|_| "全部删除结果无法持久化")?;
    drop(file);
    // The previous result is already superseded by this operation. Windows rename
    // cannot replace an existing destination, so remove that content-free receipt.
    match fs::remove_file(root.join(OUTCOME)) {
        Ok(()) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => return Err("全部删除旧结果无法替换".into()),
    }
    fs::rename(temporary, root.join(OUTCOME)).map_err(|_| "全部删除结果无法发布".into())
}

fn remove_outcome_temporary(path: &Path) -> Result<(), String> {
    match fs::symlink_metadata(path) {
        Ok(metadata) if metadata.is_file() && !metadata.file_type().is_symlink() => {
            fs::remove_file(path).map_err(|_| "全部删除结果暂存文件无法清理".into())
        }
        Ok(_) => Err("全部删除结果暂存身份无效".into()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(_) => Err("全部删除结果暂存身份不可读".into()),
    }
}

pub(crate) fn read_outcome(root: &Path) -> Result<Option<Outcome>, String> {
    let path = root.join(OUTCOME);
    let metadata = match fs::symlink_metadata(&path) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(_) => return Err("全部删除结果不可读".into()),
    };
    if !metadata.is_file() || metadata.file_type().is_symlink() || metadata.len() > 256 {
        return Err("全部删除结果身份无效".into());
    }
    let outcome: Outcome =
        serde_json::from_slice(&fs::read(path).map_err(|_| "全部删除结果不可读")?)
            .map_err(|_| "全部删除结果内容无效")?;
    if outcome.version != 1
        || !["COMPLETED", "ROLLED_BACK", "FAILED_BEFORE_START"].contains(&outcome.status.as_str())
    {
        return Err("全部删除结果状态无效".into());
    }
    Ok(Some(outcome))
}

pub(crate) fn acknowledge_outcome(root: &Path) -> Result<(), String> {
    read_outcome(root)?;
    match fs::remove_file(root.join(OUTCOME)) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(_) => Err("全部删除结果无法确认".into()),
    }
}

pub(crate) fn record_failed_before_start(root: &Path) -> Result<(), String> {
    if read_journal(root)?.is_some() || is_committed(root)? {
        return Ok(());
    }
    if read_outcome(root)?.is_none() {
        write_outcome(root, "FAILED_BEFORE_START", false)?;
    }
    Ok(())
}

fn path_leaf(root: &Path) -> Result<&str, String> {
    root.file_name()
        .and_then(|part| part.to_str())
        .filter(|part| !part.is_empty() && *part != "." && *part != "..")
        .ok_or_else(|| "全部删除数据根名称无效".into())
}

fn sibling(root: &Path, leaf: &str) -> Result<PathBuf, String> {
    root.parent()
        .map(|parent| parent.join(leaf))
        .ok_or_else(|| "全部删除数据根无父目录".into())
}

fn is_plain_name(name: &str) -> bool {
    !name.is_empty() && name != "." && name != ".." && !name.contains(['/', '\\', ':'])
}

fn validate_journal(root: &Path, journal: &Journal) -> Result<(PathBuf, PathBuf), String> {
    if journal.version != 1
        || journal.operation_id.len() != 32
        || !journal
            .operation_id
            .bytes()
            .all(|byte| byte.is_ascii_hexdigit())
    {
        return Err("全部删除恢复记录身份无效".into());
    }
    let leaf = path_leaf(root)?;
    if journal.candidate_leaf != format!(".{leaf}.full-delete-candidate-{}", journal.operation_id)
        || journal.quarantine_leaf != format!(".{leaf}.full-delete-old-{}", journal.operation_id)
    {
        return Err("全部删除恢复目录身份无效".into());
    }
    for names in [&journal.old_entries, &journal.candidate_entries] {
        let unique = names.iter().collect::<BTreeSet<_>>();
        if unique.len() != names.len() || names.iter().any(|name| !is_plain_name(name)) {
            return Err("全部删除恢复文件清单无效".into());
        }
    }
    if journal.old_entries.iter().any(|name| {
        name == LOCK
            || name == MARKER
            || name == COMMITTED
            || name == COMMIT_TEMP
            || name == ACCEPTANCE_IDENTITY
            || name == OUTCOME
            || name == OUTCOME_TEMP
    }) || journal.candidate_entries.iter().any(|name| {
        name == LOCK
            || name == MARKER
            || name == COMMITTED
            || name == COMMIT_TEMP
            || name == ACCEPTANCE_IDENTITY
            || name == OUTCOME
            || name == OUTCOME_TEMP
    }) {
        return Err("全部删除恢复文件清单包含控制文件".into());
    }
    Ok((
        sibling(root, &journal.candidate_leaf)?,
        sibling(root, &journal.quarantine_leaf)?,
    ))
}

fn plain_entries(root: &Path, exclusions: &[&str]) -> Result<Vec<String>, String> {
    let mut names = Vec::new();
    for entry in fs::read_dir(root).map_err(|_| "全部删除数据根不可枚举")? {
        let entry = entry.map_err(|_| "全部删除数据根条目不可读")?;
        let name = entry
            .file_name()
            .into_string()
            .map_err(|_| "全部删除数据根名称无效")?;
        if !is_plain_name(&name) {
            return Err("全部删除数据根名称无效".into());
        }
        let metadata = fs::symlink_metadata(entry.path()).map_err(|_| "全部删除文件身份不可读")?;
        if exclusions.contains(&name.as_str()) {
            if !metadata.is_file() || metadata.file_type().is_symlink() {
                return Err("全部删除保留身份文件类型无效".into());
            }
            continue;
        }
        if metadata.file_type().is_symlink() || !(metadata.is_file() || metadata.is_dir()) {
            return Err("全部删除范围包含非普通文件；未移动数据".into());
        }
        names.push(name);
    }
    names.sort();
    Ok(names)
}

fn remove_plain(path: &Path) -> Result<(), String> {
    match fs::symlink_metadata(path) {
        Ok(metadata) if metadata.is_file() => {
            fs::remove_file(path).map_err(|_| "全部删除恢复文件不可移除".into())
        }
        Ok(metadata) if metadata.is_dir() && !metadata.file_type().is_symlink() => {
            // Reject links anywhere in the tree before recursive removal.
            crate::DesktopWorkspaceStore::privacy_file_aggregate(path)?;
            fs::remove_dir_all(path).map_err(|_| "全部删除恢复目录不可移除".into())
        }
        Ok(_) => Err("全部删除恢复范围包含链接或特殊文件".into()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(_) => Err("全部删除恢复文件身份不可读".into()),
    }
}

fn read_journal(root: &Path) -> Result<Option<Journal>, String> {
    let path = root.join(MARKER);
    let metadata = match fs::symlink_metadata(&path) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(_) => return Err("全部删除恢复记录不可读".into()),
    };
    if !metadata.is_file() || metadata.file_type().is_symlink() || metadata.len() > 128 * 1024 {
        return Err("全部删除恢复记录类型或长度无效".into());
    }
    let journal: Journal =
        serde_json::from_slice(&fs::read(path).map_err(|_| "全部删除恢复记录不可读")?)
            .map_err(|_| "全部删除恢复记录内容无效")?;
    validate_journal(root, &journal)?;
    Ok(Some(journal))
}

fn write_journal(root: &Path, journal: &Journal) -> Result<(), String> {
    use std::io::Write;
    let bytes = serde_json::to_vec(journal).map_err(|_| "全部删除恢复记录无法编码")?;
    let mut file = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(root.join(MARKER))
        .map_err(|_| "全部删除恢复记录无法创建")?;
    if file
        .write_all(&bytes)
        .and_then(|_| file.sync_all())
        .is_err()
    {
        drop(file);
        fs::remove_file(root.join(MARKER))
            .map_err(|_| "全部删除恢复记录写入失败且无法清理".to_owned())?;
        return Err("全部删除恢复记录无法持久化".into());
    }
    Ok(())
}

fn mark_committed(root: &Path) -> Result<(), String> {
    use std::io::Write;
    let mut file = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(root.join(COMMIT_TEMP))
        .map_err(|_| "全部删除提交标记无法创建")?;
    file.write_all(b"full-local-delete-v1-committed")
        .and_then(|_| file.sync_all())
        .map_err(|_| "全部删除提交标记无法持久化")?;
    drop(file);
    fs::rename(root.join(COMMIT_TEMP), root.join(COMMITTED))
        .map_err(|_| "全部删除提交标记无法发布".into())
}

fn is_committed(root: &Path) -> Result<bool, String> {
    let path = root.join(COMMITTED);
    match fs::symlink_metadata(&path) {
        Ok(metadata)
            if metadata.is_file()
                && !metadata.file_type().is_symlink()
                && metadata.len() == b"full-local-delete-v1-committed".len() as u64 =>
        {
            if fs::read(path).map_err(|_| "全部删除提交标记不可读")?
                == b"full-local-delete-v1-committed"
            {
                Ok(true)
            } else {
                Err("全部删除提交标记内容无效".into())
            }
        }
        Ok(_) => Err("全部删除提交标记身份无效".into()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(false),
        Err(_) => Err("全部删除提交标记不可读".into()),
    }
}

fn finish_committed(root: &Path, journal: &Journal) -> Result<Receipt, String> {
    let (candidate, quarantine) = validate_journal(root, journal)?;
    verify_empty_pair(root)?;
    let mut cleanup_pending = false;
    for path in [&quarantine, &candidate] {
        if remove_plain(path).is_err() {
            cleanup_pending = true;
        }
    }
    write_outcome(root, "COMPLETED", cleanup_pending)?;
    if !cleanup_pending {
        remove_plain(&root.join(COMMIT_TEMP))?;
        fs::remove_file(root.join(MARKER)).map_err(|_| "全部删除恢复记录未清理")?;
        // Keep the commit proof until the journal is gone. If killed between these
        // two removals, startup verifies the new pair and finishes only this marker.
        fs::remove_file(root.join(COMMITTED)).map_err(|_| "全部删除提交标记未清理")?;
    }
    Ok(Receipt { cleanup_pending })
}

fn rollback_uncommitted(root: &Path, journal: &Journal) -> Result<(), String> {
    let (candidate, quarantine) = validate_journal(root, journal)?;
    // A root entry with a counterpart in quarantine is a newly published replacement.
    // An old entry not yet moved remains in root and must be left untouched.
    for name in &journal.candidate_entries {
        if !journal.old_entries.contains(name) {
            remove_plain(&root.join(name))?;
        }
    }
    for name in &journal.old_entries {
        let old = quarantine.join(name);
        if fs::symlink_metadata(&old).is_ok() {
            remove_plain(&root.join(name))?;
            fs::rename(&old, root.join(name)).map_err(|_| "全部删除旧数据无法恢复")?;
        }
    }
    remove_plain(&candidate)?;
    remove_plain(&quarantine)?;
    remove_plain(&root.join(COMMIT_TEMP))?;
    write_outcome(root, "ROLLED_BACK", false)?;
    fs::remove_file(root.join(MARKER)).map_err(|_| "全部删除恢复记录未清理")?;
    Ok(())
}

/// Resume only an already-started offline transaction. It never starts a new deletion.
pub(crate) fn recover_under_existing_lock(
    root: &Path,
    _root_lock: &fs::File,
) -> Result<(), String> {
    remove_outcome_temporary(&root.join(OUTCOME_TEMP))?;
    let Some(journal) = read_journal(root)? else {
        if is_committed(root)? {
            verify_empty_pair(root)?;
            remove_plain(&root.join(COMMIT_TEMP))?;
            fs::remove_file(root.join(COMMITTED)).map_err(|_| "全部删除提交标记未清理")?;
            return Ok(());
        }
        if fs::symlink_metadata(root.join(COMMIT_TEMP)).is_ok() {
            return Err("全部删除提交暂存标记缺少恢复记录".into());
        }
        return Ok(());
    };
    if is_committed(root)? {
        finish_committed(root, &journal).map(|_| ())
    } else {
        rollback_uncommitted(root, &journal)
    }
}

fn fresh_candidate(candidate: &Path) -> Result<Vec<String>, String> {
    use fs2::FileExt;
    fs::create_dir(candidate).map_err(|_| "全部删除空双区候选目录无法创建")?;
    let lock = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .create_new(true)
        .open(candidate.join(LOCK))
        .map_err(|_| "全部删除空双区候选锁无法创建")?;
    lock.try_lock_exclusive()
        .map_err(|_| "全部删除空双区候选锁不可用")?;
    desktop_schema_reconcile::reconcile_schema45_at_startup_under_existing_lock(candidate, &lock)?;
    verify_empty_pair(candidate)?;
    drop(lock);
    plain_entries(candidate, &[LOCK])
}

fn verify_empty_pair(root: &Path) -> Result<(), String> {
    for path in [
        root.join("workspace.sqlite3"),
        root.join("work-area/workspace.sqlite3"),
        root.join("app-configuration.sqlite3"),
    ] {
        if !crate::desktop_legacy_area_split::plain_file_if_present(&path)? {
            return Err("全部删除空双区必需数据库缺失".into());
        }
    }
    let (chat, work) = open_desktop_area_stores_for_startup(
        root.to_path_buf(),
        DesktopStartupMode::UiSchemaDiagnostic,
    )?;
    let work = work.ok_or("全部删除空候选缺少 WORK 区")?;
    if !chat.list_workspaces()?.is_empty() || !work.list_workspaces()?.is_empty() {
        return Err("全部删除空候选含业务记录".into());
    }
    for store in [&chat, &work] {
        let connection = store.connection()?;
        let version: u32 = connection
            .pragma_query_value(None, "user_version", |row| row.get(0))
            .map_err(|_| "全部删除空双区版本不可读")?;
        let integrity: String = connection
            .query_row("PRAGMA integrity_check", [], |row| row.get(0))
            .map_err(|_| "全部删除空双区完整性不可读")?;
        if version != crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION || integrity != "ok" {
            return Err("全部删除空双区版本或完整性不符".into());
        }
    }
    Ok(())
}

/// Called after the GUI and its stores have exited, while holding the root lock.
/// The hook exists for deterministic process-kill tests and must not do product work.
pub(crate) fn replace_offline_under_existing_lock(
    root: &Path,
    root_lock: &fs::File,
    mut after_step: impl FnMut(&str) -> Result<(), String>,
) -> Result<Receipt, String> {
    crate::desktop_legacy_area_split::plain_directory(root)?;
    recover_under_existing_lock(root, root_lock)?;
    let (chat, work) = open_desktop_area_stores_for_startup(
        root.to_path_buf(),
        DesktopStartupMode::UiSchemaDiagnostic,
    )?;
    if work.is_none() || chat.configuration_database.is_none() {
        return Err("全部删除仅允许已提交 CHAT/WORK 双区".into());
    }
    drop(work);
    drop(chat);
    let mut random = [0u8; 16];
    getrandom::fill(&mut random).map_err(|_| "全部删除操作 ID 无法生成")?;
    let operation_id = random
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect::<String>();
    let leaf = path_leaf(root)?;
    let candidate_leaf = format!(".{leaf}.full-delete-candidate-{operation_id}");
    let quarantine_leaf = format!(".{leaf}.full-delete-old-{operation_id}");
    let candidate = sibling(root, &candidate_leaf)?;
    let quarantine = sibling(root, &quarantine_leaf)?;
    if candidate.exists()
        || quarantine.exists()
        || root.join(MARKER).exists()
        || root.join(COMMITTED).exists()
        || root.join(COMMIT_TEMP).exists()
    {
        return Err("全部删除隔离目录或恢复标记冲突".into());
    }
    let old_entries = plain_entries(root, &[LOCK, ACCEPTANCE_IDENTITY, OUTCOME, OUTCOME_TEMP])?;
    crate::DesktopWorkspaceStore::privacy_file_aggregate(root)?;
    let candidate_entries = match fresh_candidate(&candidate) {
        Ok(entries) => entries,
        Err(error) => {
            let _ = remove_plain(&candidate);
            return Err(error);
        }
    };
    if fs::create_dir(&quarantine).is_err() {
        let _ = remove_plain(&candidate);
        return Err("全部删除旧数据保留目录不可创建".into());
    }
    let journal = Journal {
        version: 1,
        operation_id,
        candidate_leaf,
        quarantine_leaf,
        old_entries,
        candidate_entries,
    };
    if let Err(error) = write_journal(root, &journal) {
        let _ = remove_plain(&candidate);
        let _ = remove_plain(&quarantine);
        return Err(error);
    }
    let operation = (|| {
        for name in &journal.old_entries {
            fs::rename(root.join(name), quarantine.join(name))
                .map_err(|_| "全部删除旧数据无法保留")?;
            after_step("old-moved")?;
        }
        for name in &journal.candidate_entries {
            fs::rename(candidate.join(name), root.join(name))
                .map_err(|_| "全部删除空双区无法发布")?;
            after_step("candidate-published")?;
        }
        verify_empty_pair(root)?;
        after_step("before-commit")?;
        mark_committed(root)
    })();
    if let Err(error) = operation {
        return match rollback_uncommitted(root, &journal) {
            Ok(()) => Err(error),
            Err(recovery) => Err(format!("{error}；旧数据恢复待处理：{recovery}")),
        };
    }
    // A kill here must keep the new pair; startup only finishes old quarantine cleanup.
    after_step("after-commit")?;
    finish_committed(root, &journal)
}

#[cfg(test)]
mod tests {
    use super::*;
    use fs2::FileExt;
    use std::process::{Command, Stdio};
    use tempfile::tempdir;

    fn occupied_pair(root: &Path) -> fs::File {
        fs::create_dir(root).unwrap();
        let lock = fs::OpenOptions::new()
            .read(true)
            .write(true)
            .create_new(true)
            .open(root.join(LOCK))
            .unwrap();
        lock.try_lock_exclusive().unwrap();
        desktop_schema_reconcile::reconcile_schema45_at_startup_under_existing_lock(root, &lock)
            .unwrap();
        let (chat, work) = open_desktop_area_stores_for_startup(
            root.to_path_buf(),
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .unwrap();
        chat.ensure_local_workspace().unwrap();
        work.unwrap().ensure_local_workspace().unwrap();
        lock
    }

    fn assert_old_pair(root: &Path) {
        let (chat, work) = open_desktop_area_stores_for_startup(
            root.to_path_buf(),
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .unwrap();
        assert_eq!(chat.list_workspaces().unwrap().len(), 1);
        assert_eq!(work.unwrap().list_workspaces().unwrap().len(), 1);
        assert!(root.join("app-configuration.sqlite3").is_file());
    }

    fn assert_empty_pair(root: &Path) {
        let (chat, work) = open_desktop_area_stores_for_startup(
            root.to_path_buf(),
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .unwrap();
        let work = work.unwrap();
        assert!(chat.list_workspaces().unwrap().is_empty());
        assert!(work.list_workspaces().unwrap().is_empty());
        for store in [&chat, &work] {
            assert_eq!(
                store
                    .connection()
                    .unwrap()
                    .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                    .unwrap(),
                crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION
            );
            assert_eq!(
                store
                    .connection()
                    .unwrap()
                    .query_row("PRAGMA integrity_check", [], |row| row.get::<_, String>(0))
                    .unwrap(),
                "ok"
            );
        }
        assert_eq!(chat.configuration_database, work.configuration_database);
    }

    #[test]
    fn offline_replacement_publishes_empty_current_schema_pair_and_cleans_old_quarantine() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let lock = occupied_pair(&root);
        fs::write(root.join(ACCEPTANCE_IDENTITY), b"synthetic identity").unwrap();
        fs::create_dir(root.join("provider-credentials-v1")).unwrap();
        fs::write(
            root.join("provider-credentials-v1/synthetic-key"),
            b"synthetic secret",
        )
        .unwrap();
        let result = replace_offline_under_existing_lock(&root, &lock, |_| Ok(())).unwrap();
        assert_eq!(
            result,
            Receipt {
                cleanup_pending: false
            }
        );
        assert_empty_pair(&root);
        assert!(root.join(LOCK).is_file());
        assert_eq!(
            fs::read(root.join(ACCEPTANCE_IDENTITY)).unwrap(),
            b"synthetic identity"
        );
        assert!(!root.join("provider-credentials-v1").exists());
        assert_eq!(read_outcome(&root).unwrap().unwrap().status, "COMPLETED");
        acknowledge_outcome(&root).unwrap();
        assert!(read_outcome(&root).unwrap().is_none());
        assert!(!root.join(MARKER).exists());
        assert!(!root.join(COMMITTED).exists());
        assert_eq!(fs::read_dir(directory.path()).unwrap().count(), 1);
    }

    #[test]
    fn offline_replacement_error_after_first_old_move_restores_both_areas() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let lock = occupied_pair(&root);
        let error = replace_offline_under_existing_lock(&root, &lock, |step| {
            if step == "old-moved" {
                Err("synthetic stop".into())
            } else {
                Ok(())
            }
        })
        .unwrap_err();
        assert!(error.contains("synthetic stop"));
        assert_old_pair(&root);
        assert_eq!(read_outcome(&root).unwrap().unwrap().status, "ROLLED_BACK");
        assert!(!root.join(MARKER).exists());
    }

    #[test]
    fn process_kill_after_candidate_publication_rolls_back_on_same_root() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let lock = occupied_pair(&root);
        let killed = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = replace_offline_under_existing_lock(&root, &lock, |step| {
                if step == "candidate-published" {
                    panic!("synthetic process kill")
                }
                Ok(())
            });
        }));
        assert!(killed.is_err());
        assert!(root.join(MARKER).is_file());
        recover_under_existing_lock(&root, &lock).unwrap();
        assert_old_pair(&root);
        assert_eq!(read_outcome(&root).unwrap().unwrap().status, "ROLLED_BACK");
        assert!(!root.join(MARKER).exists());
    }

    #[test]
    fn process_kill_after_commit_keeps_empty_pair_and_finishes_cleanup() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let lock = occupied_pair(&root);
        let killed = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = replace_offline_under_existing_lock(&root, &lock, |step| {
                if step == "after-commit" {
                    panic!("synthetic process kill")
                }
                Ok(())
            });
        }));
        assert!(killed.is_err());
        assert!(root.join(MARKER).is_file());
        assert!(root.join(COMMITTED).is_file());
        assert_empty_pair(&root);
        recover_under_existing_lock(&root, &lock).unwrap();
        assert_empty_pair(&root);
        assert_eq!(read_outcome(&root).unwrap().unwrap().status, "COMPLETED");
        assert!(!root.join(MARKER).exists());
        assert!(!root.join(COMMITTED).exists());
    }

    #[test]
    fn committed_recovery_retains_old_quarantine_if_new_work_database_is_missing() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let lock = occupied_pair(&root);
        let killed = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = replace_offline_under_existing_lock(&root, &lock, |step| {
                if step == "after-commit" {
                    panic!("synthetic process kill")
                }
                Ok(())
            });
        }));
        assert!(killed.is_err());
        let journal = read_journal(&root).unwrap().unwrap();
        let (_, quarantine) = validate_journal(&root, &journal).unwrap();
        assert!(quarantine.join("work-area/workspace.sqlite3").is_file());
        fs::remove_file(root.join("work-area/workspace.sqlite3")).unwrap();
        assert!(recover_under_existing_lock(&root, &lock).is_err());
        assert!(quarantine.join("work-area/workspace.sqlite3").is_file());
        assert!(root.join(MARKER).is_file());
        assert!(root.join(COMMITTED).is_file());
    }

    #[test]
    fn commit_proof_survives_interruption_between_journal_and_commit_cleanup() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let lock = occupied_pair(&root);
        let killed = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = replace_offline_under_existing_lock(&root, &lock, |step| {
                if step == "after-commit" {
                    panic!("synthetic process kill")
                }
                Ok(())
            });
        }));
        assert!(killed.is_err());
        let journal = read_journal(&root).unwrap().unwrap();
        let (candidate, quarantine) = validate_journal(&root, &journal).unwrap();
        remove_plain(&candidate).unwrap();
        remove_plain(&quarantine).unwrap();
        fs::remove_file(root.join(MARKER)).unwrap();
        assert!(root.join(COMMITTED).is_file());
        recover_under_existing_lock(&root, &lock).unwrap();
        assert_empty_pair(&root);
        assert!(!root.join(COMMITTED).exists());
    }

    #[test]
    fn offline_delete_kill_child() {
        let Some(root) = std::env::var_os("NANFENG_AI_FULL_DELETE_KILL_TEST_ROOT") else {
            return;
        };
        let root = PathBuf::from(root);
        let phase = std::env::var("NANFENG_AI_FULL_DELETE_KILL_TEST_PHASE").unwrap();
        let lock = fs::OpenOptions::new()
            .read(true)
            .write(true)
            .open(root.join(LOCK))
            .unwrap();
        lock.try_lock_exclusive().unwrap();
        let marker = root
            .parent()
            .unwrap()
            .join("full-delete-kill-point.reached");
        let _ = replace_offline_under_existing_lock(&root, &lock, |step| {
            if step == phase {
                fs::write(&marker, step.as_bytes()).unwrap();
                loop {
                    std::thread::park_timeout(std::time::Duration::from_secs(1));
                }
            }
            Ok(())
        });
        panic!("synthetic kill point was not reached");
    }

    #[test]
    fn actual_child_process_kill_recovers_before_and_after_commit() {
        for phase in ["candidate-published", "after-commit"] {
            let directory = tempdir().unwrap();
            let root = directory.path().join("app-data");
            let lock = occupied_pair(&root);
            drop(lock);
            let mut child = Command::new(std::env::current_exe().unwrap())
                .args([
                    "--exact",
                    "desktop_full_local_delete_v1::tests::offline_delete_kill_child",
                    "--nocapture",
                ])
                .env("NANFENG_AI_FULL_DELETE_KILL_TEST_ROOT", &root)
                .env("NANFENG_AI_FULL_DELETE_KILL_TEST_PHASE", phase)
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .spawn()
                .unwrap();
            let marker = directory.path().join("full-delete-kill-point.reached");
            let deadline = std::time::Instant::now() + std::time::Duration::from_secs(30);
            while !marker.exists() && std::time::Instant::now() < deadline {
                if let Some(status) = child.try_wait().unwrap() {
                    panic!("full delete child exited before {phase}: {status}");
                }
                std::thread::sleep(std::time::Duration::from_millis(20));
            }
            child.kill().unwrap();
            child.wait().unwrap();
            assert_eq!(fs::read(&marker).unwrap(), phase.as_bytes());
            let lock = fs::OpenOptions::new()
                .read(true)
                .write(true)
                .open(root.join(LOCK))
                .unwrap();
            lock.try_lock_exclusive().unwrap();
            assert!(root.join(MARKER).is_file());
            recover_under_existing_lock(&root, &lock).unwrap();
            if phase == "candidate-published" {
                assert_old_pair(&root);
            } else {
                assert_empty_pair(&root);
            }
            assert!(!root.join(MARKER).exists());
            assert!(!root.join(COMMITTED).exists());
        }
    }
}
