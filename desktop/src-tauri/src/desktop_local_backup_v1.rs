//! Desktop projection of Android's current P5-D local backup/restore contract.
//!
//! The owner accepts only a native-picker-selected `.nfai-backup` path. It creates a
//! SQLite-consistent `VACUUM INTO` snapshot, removes device-only state before a second
//! compaction, packages only controlled private roots, verifies the finished archive,
//! and reads the selected destination back by SHA-256. Restore is replacement-only,
//! checkpointed, rollback-safe, and ends at an explicit full-process restart boundary.

use rusqlite::Connection;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::{BTreeMap, BTreeSet},
    fs,
    io::{Read, Write},
    path::{Path, PathBuf},
};
use zip::{write::SimpleFileOptions, CompressionMethod, ZipArchive, ZipWriter};

const FORMAT: &str = "nanfeng-ai.local-backup";
const FORMAT_VERSION: u32 = 1;
const MANIFEST_ENTRY: &str = "manifest.json";
const DATABASE_ENTRY: &str = "database/nanfeng-ai.snapshot";
const USAGE_LEDGER_ENTRY: &str = "database/desktop-usage-ledger-v1.snapshot";
const USAGE_LEDGER_LOCAL: &str = "usage-ledger/usage-ledger-v1.sqlite3";
const USAGE_LEDGER_CHECKPOINT: &str = "databases/desktop-usage-ledger-v1.snapshot";
const CHECKPOINT_V3_MANIFEST: &str = "checkpoint-manifest-v3.json";
const CHECKPOINT_V4_MANIFEST: &str = "checkpoint-manifest-v4.json";
const USAGE_LEDGER_SCHEMA: u32 = 6;
const MAX_MANIFEST_BYTES: u64 = 1024 * 1024;
const MAX_ARCHIVE_BYTES: u64 = 256 * 1024 * 1024;
const MAX_ENTRY_BYTES: u64 = 256 * 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 512 * 1024 * 1024;
const MAX_ENTRY_COUNT: usize = 10_000;
const MAX_COMPRESSION_RATIO: u64 = 100;
const OWNER_DIR: &str = "local-backup/v1";
const STATE_FILE: &str = "state.json";
const INBOX_FILE: &str = "inbox/pending.nfai-backup";

/// Device-only state is never exported. Tables tied to restored objects are intentionally
/// left empty after restore; only the stable device-level subset below is copied forward.
const EXCLUDED_TABLES: &[&str] = &[
    "desktop_attachment_search_checkpoints",
    "desktop_attachment_search_coverage",
    "desktop_attachment_staging",
    "desktop_temporary_attachments",
    "desktop_temporary_recovery",
    "desktop_app_settings",
    "desktop_background_runtime_v1",
    "desktop_conversation_read_markers_v1",
    "desktop_audio_preview_positions",
    "desktop_local_search_history",
    "desktop_local_search_index",
    "desktop_local_search_index_state",
    "desktop_pdf_preview_positions",
    "desktop_provider_settings",
    "desktop_video_preview_positions",
    "p6g_catalog",
    "p6g_conversation_override",
    "p6g_global_default",
    "p6g_route_metadata",
    "sync_account_metadata",
    "sync_intents",
    "window_layout",
];

const PRESERVED_DEVICE_TABLES: &[&str] = &[
    "desktop_app_settings",
    "desktop_background_runtime_v1",
    "desktop_conversation_read_markers_v1",
    "desktop_provider_settings",
    "p6g_catalog",
    "p6g_global_default",
    "sync_account_metadata",
    "sync_intents",
    "window_layout",
];

const TASK_TABLES: &[&str] = &[
    "chatgpt_import_tasks",
    "claude_import_tasks",
    "nanfeng_knowledge_import_tasks",
    "p6k_zip_import_tasks",
];

#[derive(Clone, Copy)]
struct ControlledRoot {
    local: &'static str,
    package: &'static str,
}

const CONTROLLED_ROOTS: &[ControlledRoot] = &[
    ControlledRoot {
        local: "assets",
        package: "attachments/v1",
    },
    ControlledRoot {
        local: "packages",
        package: "workspace-packages/v1",
    },
    ControlledRoot {
        local: "p6k-zip-import-assets",
        package: "zip-import-assets/v1",
    },
    ControlledRoot {
        local: "exchange-v2/archives",
        package: "workspace-exchange-archives/v2",
    },
    ControlledRoot {local:"markdown-knowledge-import",package:"markdown-knowledge-import"},
    ControlledRoot {local:"json-knowledge-import",package:"json-knowledge-import"},
    ControlledRoot {local:"pdf-knowledge-import",package:"pdf-knowledge-import"},
    ControlledRoot {local:"web-text-snapshots",package:"web-text-snapshots"},
];

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct BackupArtifact {
    pub file_name: String,
    pub sha256: String,
    pub byte_count: u64,
    pub schema_version: u32,
    pub table_counts: BTreeMap<String, u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct BackupPreflight {
    pub format: String,
    pub version: u32,
    pub schema_version: u32,
    pub table_counts: BTreeMap<String, u64>,
    pub asset_bytes: u64,
    pub missing: Vec<String>,
    pub conflicts: Vec<String>,
    pub unsupported: Vec<String>,
    pub fingerprint: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct RestoreReceipt {
    pub checkpoint_id: String,
    pub restart_required: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct BackupStatus {
    pub operation: String,
    pub preflight: Option<BackupPreflight>,
    pub notice: Option<String>,
    pub restart_required: bool,
    pub interrupted: bool,
    pub rollback_pending: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PersistedState {
    operation: String,
    phase: String,
    fingerprint: Option<String>,
    checkpoint_id: Option<String>,
    process_id: Option<u32>,
    #[serde(default)]
    process_instance: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CheckpointV3Manifest {
    version: u8,
    workspace_sha256: String,
    usage_ledger_sha256: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CheckpointV4Manifest {
    version: u8,
    workspace_sha256: String,
    usage_ledger_sha256: Option<String>,
    // A missing root has no entry; a present empty directory has a None entry.
    assets: BTreeMap<String, Option<String>>,
}

impl Default for PersistedState {
    fn default() -> Self {
        Self {
            operation: "IDLE".into(),
            phase: "NONE".into(),
            fingerprint: None,
            checkpoint_id: None,
            process_id: None,
            process_instance: None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ManifestEntry {
    path: String,
    bytes: u64,
    sha256: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BackupManifest {
    format: String,
    version: u32,
    app_version: String,
    schema_version: u32,
    scope: String,
    excluded: Vec<String>,
    table_counts: BTreeMap<String, u64>,
    entries: Vec<ManifestEntry>,
    manifest_sha256: String,
}

#[derive(Debug)]
struct VerifiedPackage {
    preflight: BackupPreflight,
    manifest: BackupManifest,
}

fn owner_root(root: &Path) -> PathBuf {
    root.join(OWNER_DIR)
}

fn state_path(root: &Path) -> PathBuf {
    owner_root(root).join(STATE_FILE)
}

fn inbox_path(root: &Path) -> PathBuf {
    owner_root(root).join(INBOX_FILE)
}

fn checkpoint_path(root: &Path, checkpoint_id: &str) -> PathBuf {
    owner_root(root).join("recovery").join(checkpoint_id)
}

fn restore_stage_path(root: &Path, checkpoint_id: &str) -> PathBuf {
    owner_root(root).join(format!("restore-{checkpoint_id}"))
}

fn switch_old_path(root: &Path, checkpoint_id: &str) -> PathBuf {
    owner_root(root).join(format!("switch-old-{checkpoint_id}"))
}

fn generated_checkpoint_for_cleanup(checkpoint_id: &str) -> bool {
    let Some(suffix) = checkpoint_id
        .strip_prefix("checkpoint-v2-")
        .or_else(|| checkpoint_id.strip_prefix("checkpoint-v3-"))
        .or_else(|| checkpoint_id.strip_prefix("checkpoint-v4-")) else {
        return false;
    };
    let mut parts = suffix.split('-');
    matches!(parts.next(), Some(pid) if !pid.is_empty() && pid.bytes().all(|byte| byte.is_ascii_digit()))
        && matches!(parts.next(), Some(nanos) if !nanos.is_empty() && nanos.bytes().all(|byte| byte.is_ascii_digit()))
        && parts.next().is_none()
}

fn plain_private_entry(metadata: &fs::Metadata) -> bool {
    if metadata.file_type().is_symlink() {
        return false;
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        if metadata.file_attributes() & 0x400 != 0 {
            return false;
        }
    }
    metadata.is_dir() || metadata.is_file()
}

fn remove_exact_transient_directories(root: &Path, checkpoint_id: &str) -> Result<(), String> {
    // Older states used unrelated random directory names. Never guess which legacy
    // directories belong to a checkpoint or sweep by prefix.
    if !generated_checkpoint_for_cleanup(checkpoint_id) {
        return Ok(());
    }
    for parent in [root.join("local-backup"), owner_root(root)] {
        let metadata = fs::symlink_metadata(&parent)
            .map_err(|_| json_error("恢复私有目录不可读；检查点已保留"))?;
        if !metadata.is_dir() || !plain_private_entry(&metadata) {
            return Err(json_error("恢复私有目录不是普通目录；检查点已保留"));
        }
    }
    for target in [
        restore_stage_path(root, checkpoint_id),
        switch_old_path(root, checkpoint_id),
    ] {
        let metadata = match fs::symlink_metadata(&target) {
            Ok(metadata) => metadata,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
            Err(_) => return Err(json_error("恢复隔离目录不可读；检查点已保留")),
        };
        if !metadata.is_dir() || !plain_private_entry(&metadata) {
            return Err(json_error("恢复隔离目录不是普通目录；检查点已保留"));
        }
        let mut stack = vec![target.clone()];
        while let Some(directory) = stack.pop() {
            for entry in fs::read_dir(&directory)
                .map_err(|_| json_error("恢复隔离目录不可读；检查点已保留"))?
            {
                let entry = entry.map_err(|_| json_error("恢复隔离目录不可读；检查点已保留"))?;
                let entry_metadata = fs::symlink_metadata(entry.path())
                    .map_err(|_| json_error("恢复隔离内容不可读；检查点已保留"))?;
                if !plain_private_entry(&entry_metadata) {
                    return Err(json_error("恢复隔离内容包含链接；检查点已保留"));
                }
                if entry_metadata.is_dir() {
                    stack.push(entry.path());
                }
            }
        }
        fs::remove_dir_all(&target)
            .map_err(|_| json_error("恢复隔离目录无法清理；检查点已保留"))?;
    }
    Ok(())
}

fn unique_id(prefix: &str) -> String {
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|value| value.as_nanos())
        .unwrap_or_default();
    format!("{prefix}-{}-{nanos}", std::process::id())
}

fn json_error(message: impl AsRef<str>) -> String {
    serde_json::json!({ "message": message.as_ref() }).to_string()
}

fn read_state(root: &Path) -> Result<PersistedState, String> {
    let recovery = owner_root(root).join("recovery");
    let checkpoint_present = match fs::read_dir(&recovery) {
        Ok(mut entries) => entries
            .next()
            .transpose()
            .map_err(|_| json_error("本机恢复检查点不可读"))?
            .is_some(),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => false,
        Err(_) => return Err(json_error("本机恢复检查点不可读")),
    };
    let path = state_path(root);
    let metadata = match fs::symlink_metadata(&path) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound && !checkpoint_present => {
            return Ok(PersistedState::default());
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Err(json_error("本机恢复状态缺失；已保留恢复检查点"));
        }
        Err(_) => return Err(json_error("本机恢复状态不可读")),
    };
    if !metadata.file_type().is_file() || metadata.len() == 0 || metadata.len() > 16 * 1024 {
        return Err(json_error("本机恢复状态文件无效"));
    }
    let bytes = fs::read(&path).map_err(|_| json_error("本机恢复状态不可读"))?;
    let state: PersistedState =
        serde_json::from_slice(&bytes).map_err(|_| json_error("本机恢复状态文件无效"))?;
    let checkpoint_required = match (state.operation.as_str(), state.phase.as_str()) {
        ("IDLE", "NONE")
        | ("PREFLIGHTED", "READY")
        | ("CANCELLED", "NONE")
        | ("COMPLETED", "RESTARTED") => false,
        ("INTERRUPTED", "CHECKPOINTED" | "SWITCHING" | "ROLLED_BACK_INTERRUPTED")
        | ("FAILED", "ROLLED_BACK" | "ROLLBACK_FAILED")
        | ("RESTORED_RESTART_REQUIRED", "SWITCHED") => true,
        _ => return Err(json_error("本机恢复状态阶段无效")),
    };
    if checkpoint_required != state.checkpoint_id.is_some()
        || (!checkpoint_required && checkpoint_present)
    {
        return Err(json_error("本机恢复状态与检查点不匹配"));
    }
    if let Some(id) = state.checkpoint_id.as_deref() {
        if id.is_empty()
            || !id
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-' || byte == b'_')
        {
            return Err(json_error("本机恢复检查点标识无效"));
        }
        if !matches!(fs::symlink_metadata(checkpoint_path(root, id)), Ok(metadata) if metadata.file_type().is_dir())
        {
            return Err(json_error("本机恢复检查点不可用"));
        }
    }
    Ok(state)
}

fn require_no_pending_rollback(root: &Path) -> Result<(), String> {
    let state = read_state(root)?;
    if state.operation == "FAILED" && state.phase == "ROLLBACK_FAILED" {
        return Err(json_error(
            "上次自动回滚未完成；请释放占用文件并完全重启 App",
        ));
    }
    Ok(())
}

fn require_no_retained_checkpoint(root: &Path) -> Result<(), String> {
    if read_state(root)?.checkpoint_id.is_some() {
        return Err(json_error("上次恢复检查点仍保留，请先取消上次恢复，再重新选择并预检备份；本机数据和检查点未改动"));
    }
    Ok(())
}

fn sync_file(path: &Path) -> Result<(), String> {
    fs::OpenOptions::new()
        .read(true)
        // FlushFileBuffers requires a write-capable handle on Windows. This is
        // an already-created private backup/restore file, never a truncating open.
        .write(cfg!(windows))
        .open(path)
        .and_then(|file| file.sync_all())
        .map_err(|_| json_error("本机备份文件无法持久化"))
}

fn write_state(root: &Path, state: &PersistedState) -> Result<(), String> {
    let path = state_path(root);
    let parent = path
        .parent()
        .ok_or_else(|| json_error("本机备份状态目录无效"))?;
    fs::create_dir_all(parent).map_err(|_| json_error("无法创建本机备份状态目录"))?;
    let temporary = parent.join(format!(".{STATE_FILE}.part"));
    let bytes = serde_json::to_vec(state).map_err(|_| json_error("本机备份状态无法编码"))?;
    fs::write(&temporary, bytes).map_err(|_| json_error("本机备份状态无法保存"))?;
    sync_file(&temporary)?;
    fs::rename(&temporary, &path).map_err(|_| json_error("本机备份状态无法发布"))?;
    Ok(())
}

fn sha256_file(path: &Path) -> Result<String, String> {
    let mut file = fs::File::open(path).map_err(|_| json_error("本机备份文件不可读"))?;
    sha256_reader(&mut file)
}

fn sha256_reader(reader: &mut impl Read) -> Result<String, String> {
    let mut digest = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    loop {
        let read = reader
            .read(&mut buffer)
            .map_err(|_| json_error("本机备份哈希读取失败"))?;
        if read == 0 {
            break;
        }
        digest.update(&buffer[..read]);
    }
    Ok(format!("{:x}", digest.finalize()))
}

fn sha256_bytes(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}

fn selected_backup_path(path: &str, must_exist: bool) -> Result<PathBuf, String> {
    let path = PathBuf::from(path);
    let name = path.file_name().and_then(|value| value.to_str());
    if !name.is_some_and(|name| name.ends_with(".nfai-backup")) {
        return Err(json_error("只接受 .nfai-backup 文件"));
    }
    if must_exist {
        let metadata =
            fs::symlink_metadata(&path).map_err(|_| json_error("所选本机备份文件不可读"))?;
        if metadata.file_type().is_symlink()
            || !metadata.is_file()
            || metadata.len() == 0
            || metadata.len() > MAX_ARCHIVE_BYTES
        {
            return Err(json_error("所选本机备份文件大小或类型无效"));
        }
    } else if fs::symlink_metadata(&path)
        .ok()
        .is_some_and(|metadata| metadata.file_type().is_symlink())
    {
        return Err(json_error("备份目标不能是符号链接"));
    }
    Ok(path)
}

fn escape_sql_path(path: &Path) -> String {
    path.to_string_lossy().replace('\'', "''")
}

fn snapshot_database(database: &Path, target: &Path) -> Result<(), String> {
    if let Some(parent) = target.parent() {
        fs::create_dir_all(parent).map_err(|_| json_error("无法创建数据库快照目录"))?;
    }
    let _ = fs::remove_file(target);
    let connection =
        Connection::open(database).map_err(|_| json_error("无法打开当前 SQLite 数据库"))?;
    connection
        .execute_batch(&format!("VACUUM INTO '{}'", escape_sql_path(target)))
        .map_err(|_| json_error("SQLite 一致性快照失败"))?;
    sync_file(target)?;
    if !sqlite_healthy(target)? {
        return Err(json_error("SQLite 一致性快照未通过完整性检查"));
    }
    Ok(())
}

fn table_exists(connection: &Connection, table: &str) -> Result<bool, String> {
    connection
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name=?1)",
            [table],
            |row| row.get(0),
        )
        .map_err(|_| json_error("SQLite 表清单不可读"))
}

fn quote_identifier(value: &str) -> String {
    format!("\"{}\"", value.replace('"', "\"\""))
}

/// Identify temporary-only bytes in the consistent snapshot, before removing their
/// recovery rows. Persistent attachment and exchange owners retain shared bytes.
fn temporary_only_asset_hashes(connection: &Connection) -> Result<BTreeSet<String>, String> {
    if !table_exists(connection, "desktop_temporary_attachments")? {
        return Ok(BTreeSet::new());
    }
    let mut exclusions = Vec::new();
    for table in ["desktop_conversation_attachments", "workspace_assets", "exchange_v2_assets"] {
        if table_exists(connection, table)? {
            exclusions.push(format!("NOT EXISTS (SELECT 1 FROM {} ordinary WHERE ordinary.sha256=temporary.sha256)", quote_identifier(table)));
        }
    }
    let predicate = if exclusions.is_empty() { String::new() } else { format!(" WHERE {}", exclusions.join(" AND ")) };
    let mut statement = connection.prepare(&format!("SELECT DISTINCT temporary.sha256 FROM desktop_temporary_attachments temporary{predicate}"))
        .map_err(|_| json_error("临时附件隔离索引不可读"))?;
    let hashes = statement.query_map([], |row| row.get::<_, String>(0))
        .map_err(|_| json_error("临时附件隔离索引不可读"))?
        .collect::<Result<BTreeSet<_>, _>>()
        .map_err(|_| json_error("临时附件隔离索引无效"))?;
    if hashes.iter().any(|hash| hash.len()!=64 || !hash.bytes().all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))) {
        return Err(json_error("临时附件字节身份无效，已拒绝备份"));
    }
    Ok(hashes)
}

fn require_temporary_backup_isolation(path: &Path) -> Result<(), String> {
    let connection = Connection::open(path).map_err(|_| json_error("备份临时隔离检查不可读"))?;
    for table in ["desktop_temporary_recovery", "desktop_temporary_attachments"] {
        if table_exists(&connection, table)? {
            let count: u64 = connection.query_row(&format!("SELECT COUNT(*) FROM {}", quote_identifier(table)), [], |row| row.get(0))
                .map_err(|_| json_error("备份临时隔离检查失败"))?;
            if count>0 {
                return Err(json_error("备份包含临时聊天恢复记录，已拒绝恢复；请使用新版重新导出"));
            }
        }
    }
    Ok(())
}

fn sanitize_snapshot(path: &Path) -> Result<BTreeSet<String>, String> {
    let connection = Connection::open(path).map_err(|_| json_error("数据库快照不可读"))?;
    connection
        .pragma_update(None, "foreign_keys", "OFF")
        .map_err(|_| json_error("数据库快照无法进入安全清理模式"))?;
    let temporary_assets = temporary_only_asset_hashes(&connection)?;
    if table_exists(&connection, "desktop_attachment_assets")? {
        for hash in &temporary_assets {
            connection.execute("DELETE FROM desktop_attachment_assets WHERE sha256=?1", [hash])
                .map_err(|_| json_error("临时附件索引无法从备份快照排除"))?;
        }
    }
    for table in EXCLUDED_TABLES {
        if table_exists(&connection, table)? {
            connection
                .execute(&format!("DELETE FROM {}", quote_identifier(table)), [])
                .map_err(|_| json_error("设备专属状态无法从备份快照排除"))?;
        }
    }
    for table in TASK_TABLES {
        if !table_exists(&connection, table)? {
            continue;
        }
        let columns = table_columns(&connection, table)?;
        if !columns.iter().any(|column| column == "status") {
            continue;
        }
        let terminal = "('COMPLETED','PARTIALLY_COMPLETED','FAILED','CANCELLED','SKIPPED')";
        if columns.iter().any(|column| column == "failure") {
            connection
                .execute(
                    &format!(
                        "UPDATE {} SET status='FAILED',failure='INTERRUPTED' WHERE status NOT IN {terminal}",
                        quote_identifier(table)
                    ),
                    [],
                )
                .map_err(|_| json_error("恢复任务中断状态无法固化"))?;
        } else {
            connection
                .execute(
                    &format!(
                        "UPDATE {} SET status='FAILED' WHERE status NOT IN {terminal}",
                        quote_identifier(table)
                    ),
                    [],
                )
                .map_err(|_| json_error("恢复任务中断状态无法固化"))?;
        }
    }
    if table_exists(&connection, "desktop_transcription_tasks")? {
        connection.execute(
            "UPDATE desktop_transcription_tasks SET state='RECOVERY_REQUIRED',error_code='BACKUP_RESTORED',user_message='任务已从本机备份恢复，请显式重试',technical_detail='active task restored without automatic provider replay' WHERE state IN ('QUEUED','PREPARING','TRANSCRIBING','EXPORTING')",
            [],
        ).map_err(|_| json_error("语音转写任务无法转换为安全恢复状态"))?;
    }
    if table_exists(&connection, "desktop_ocr_attempts_v1")? {
        // Restoring a snapshot cannot resume an external request. Keep completed
        // history intact and mark only unfinished calls as an unknown outcome.
        connection.execute(
            "UPDATE desktop_ocr_attempts_v1 SET state='UNKNOWN',safe_error_code='BACKUP_RESTORED',terminal_at_ms=0 WHERE state='RUNNING'",
            [],
        ).map_err(|_| json_error("OCR 调用无法转换为安全恢复状态"))?;
    }
    if table_exists(&connection, "desktop_history_knowledge_candidates_v1")? {
        connection.execute(
            "UPDATE desktop_history_knowledge_candidates_v1 SET status='UNKNOWN',safe_error_code='BACKUP_RESTORED',updated_at_ms=0 WHERE status IN ('RESERVED','RUNNING')",
            [],
        ).map_err(|_|json_error("历史资料库任务无法转换为安全恢复状态"))?;
        connection.execute(
            "UPDATE desktop_history_knowledge_checkpoints_v1 SET status='UNKNOWN',updated_at_ms=0 WHERE status IN ('RESERVED','RUNNING')",
            [],
        ).map_err(|_|json_error("历史资料库 checkpoint 无法转换为安全恢复状态"))?;
    }
    if table_exists(&connection, "desktop_reminder_drafts_v1")? {
        connection.execute(
            "UPDATE desktop_reminder_drafts_v1 SET status='UNKNOWN',safe_error_code='BACKUP_RESTORED',updated_at_ms=0 WHERE status='RUNNING'",
            [],
        ).map_err(|_|json_error("提醒草案无法转换为安全恢复状态"))?;
    }
    if table_exists(&connection, "desktop_reminder_plans_v1")? {
        connection.execute(
            "UPDATE desktop_reminder_plans_v1 SET status='UNKNOWN',next_run_at_ms=NULL,last_safe_error_code='BACKUP_RESTORED',updated_at_ms=0 WHERE status='RUNNING'",
            [],
        ).map_err(|_|json_error("提醒计划无法转换为安全恢复状态"))?;
    }
    if table_exists(&connection, "desktop_reminder_runs_v1")? {
        connection.execute(
            "UPDATE desktop_reminder_runs_v1 SET status='UNKNOWN',completed_at_ms=0,safe_error_code='BACKUP_RESTORED',notification_state='SUPPRESSED',notification_safe_code='BACKUP_RESTORED' WHERE status='RUNNING'",
            [],
        ).map_err(|_|json_error("提醒执行无法转换为安全恢复状态"))?;
        connection.execute(
            "UPDATE desktop_reminder_runs_v1 SET notification_state='SUPPRESSED',notification_safe_code='BACKUP_RESTORED' WHERE notification_state='PENDING'",
            [],
        ).map_err(|_|json_error("提醒通知无法转换为安全恢复状态"))?;
    }
    connection
        .execute_batch("VACUUM")
        .map_err(|_| json_error("排除设备专属状态后无法压实数据库快照"))?;
    sync_file(path)?;
    if contains_sensitive_values(path)? {
        return Err(json_error(
            "发现疑似凭据、恢复材料或高敏内容；为避免不安全副本，已拒绝整个备份。",
        ));
    }
    Ok(temporary_assets)
}

fn table_columns(connection: &Connection, table: &str) -> Result<Vec<String>, String> {
    let mut statement = connection
        .prepare(&format!("PRAGMA table_info({})", quote_identifier(table)))
        .map_err(|_| json_error("SQLite 列清单不可读"))?;
    let columns = statement
        .query_map([], |row| row.get(1))
        .map_err(|_| json_error("SQLite 列清单不可读"))?
        .collect::<Result<Vec<String>, _>>()
        .map_err(|_| json_error("SQLite 列清单无效"))?;
    Ok(columns)
}

fn looks_sensitive(value: &str) -> bool {
    let lower = value.to_ascii_lowercase();
    let compact = lower
        .chars()
        .filter(|c| !c.is_whitespace())
        .collect::<String>();
    value.contains("-----BEGIN PRIVATE KEY-----")
        || value.contains("-----BEGIN OPENSSH PRIVATE KEY-----")
        || lower.contains("authorization: bearer ")
        || lower.contains("xoxb-")
        || lower.contains("ghp_")
        || lower.contains("akia") && value.len() >= 20
        || lower.contains("aiza") && value.len() >= 35
        || value.len() >= 24
            && lower.match_indices("sk-").any(|(index, _)| {
                // Import owners generate identifiers such as `chatgpt-task-...`.
                // A credential prefix must start a token, not occur inside `task`.
                index == 0
                    || (!lower.as_bytes()[index - 1].is_ascii_alphanumeric()
                        && lower.as_bytes()[index - 1] != b'_')
            })
        || [
            "api_key=",
            "apikey=",
            "access_token=",
            "refresh_token=",
            "recovery_code=",
        ]
        .iter()
        .any(|marker| compact.contains(marker))
}

fn contains_sensitive_values(path: &Path) -> Result<bool, String> {
    let connection = Connection::open(path).map_err(|_| json_error("数据库快照不可读"))?;
    let mut tables = connection
        .prepare(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
        )
        .map_err(|_| json_error("数据库快照表清单不可读"))?
        .query_map([], |row| row.get::<_, String>(0))
        .map_err(|_| json_error("数据库快照表清单不可读"))?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| json_error("数据库快照表清单无效"))?;
    tables.sort();
    for table in tables {
        let mut text_columns = Vec::new();
        let mut statement = connection
            .prepare(&format!("PRAGMA table_info({})", quote_identifier(&table)))
            .map_err(|_| json_error("数据库快照列清单不可读"))?;
        let rows = statement
            .query_map([], |row| {
                Ok((row.get::<_, String>(1)?, row.get::<_, String>(2)?))
            })
            .map_err(|_| json_error("数据库快照列清单不可读"))?;
        for row in rows {
            let (name, kind) = row.map_err(|_| json_error("数据库快照列清单无效"))?;
            if kind.eq_ignore_ascii_case("TEXT") || kind.is_empty() {
                text_columns.push(name);
            }
        }
        for column in text_columns {
            let sql = format!(
                "SELECT {} FROM {} WHERE {} IS NOT NULL",
                quote_identifier(&column),
                quote_identifier(&table),
                quote_identifier(&column)
            );
            let mut statement = connection
                .prepare(&sql)
                .map_err(|_| json_error("数据库快照文本列不可读"))?;
            let values = statement
                .query_map([], |row| row.get::<_, String>(0))
                .map_err(|_| json_error("数据库快照文本列不可读"))?;
            for value in values {
                if value.map(|value| looks_sensitive(&value)).unwrap_or(true) {
                    return Ok(true);
                }
            }
        }
    }
    Ok(false)
}

fn sqlite_healthy(path: &Path) -> Result<bool, String> {
    let connection = Connection::open(path).map_err(|_| json_error("SQLite 文件不可读"))?;
    connection
        .query_row("PRAGMA integrity_check", [], |row| row.get::<_, String>(0))
        .map(|value| value == "ok")
        .map_err(|_| json_error("SQLite 完整性检查失败"))
}

fn schema_version(path: &Path) -> Result<u32, String> {
    Connection::open(path)
        .map_err(|_| json_error("SQLite 文件不可读"))?
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| json_error("SQLite Schema 版本不可读"))
}

fn backup_schema_compatible(current: u32, archived: u32) -> bool {
    current == archived
        || (current <= crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION
            && (45..current).contains(&archived))
}

fn require_forward_area_schema(current: u32, archived: u32, candidate: &Path) -> Result<(), String> {
    // Schema45 -> 46 was supported before the later area migrations. For a
    // larger forward jump, verify the archived database is an actual committed
    // area schema before allowing it to replace the current area on restart.
    if current <= 46 || current == archived {
        return Ok(());
    }
    let connection = Connection::open_with_flags(candidate, rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| json_error("旧备份数据库不可读"))?;
    use crate::desktop_schema_source::SchemaSource;
    let source = crate::desktop_schema_source::classify(&connection, archived)
        .map_err(|_| json_error("旧备份 Schema 无法核验"))?;
    if matches!((archived, source),
        (45, SchemaSource::Common45 { split_done: true, .. })
        | (46, SchemaSource::Common46 { split_done: true, .. })
        | (47, SchemaSource::Common47 { split_done: true, .. })
        | (48, SchemaSource::Common48 { split_done: true, .. })
        | (49, SchemaSource::Common49 { split_done: true, .. })
        | (50, SchemaSource::Common50 { split_done: true, .. })
        | (51, SchemaSource::Common51 { split_done: true, .. })) {
        Ok(())
    } else {
        Err(json_error("旧备份不是完整的已提交区域 Schema"))
    }
}

fn require_matching_schema45_area(
    current_database: &Path,
    candidate_database: &Path,
    schema: u32,
) -> Result<(), String> {
    if schema < 45 {
        return Ok(());
    }
    if read_schema45_area(current_database)? != read_schema45_area(candidate_database)? {
        return Err(json_error("备份属于另一个数据区域，不能跨 CHAT/WORK 恢复"));
    }
    Ok(())
}

fn read_schema45_area(path: &Path) -> Result<String, String> {
    let connection = Connection::open_with_flags(path, rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| json_error("备份区域数据库不可读"))?;
    let (area, split_done): (String, i64) = connection
        .query_row(
            "SELECT area,split_done FROM desktop_data_area_v1 WHERE id=1",
            [],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .map_err(|_| json_error("备份缺少有效的区域身份"))?;
    if !matches!(area.as_str(), "CHAT" | "WORK") || split_done != 1 {
        return Err(json_error("备份区域身份尚未完成分库"));
    }
    Ok(area)
}

fn table_counts(path: &Path) -> Result<BTreeMap<String, u64>, String> {
    let connection = Connection::open(path).map_err(|_| json_error("SQLite 文件不可读"))?;
    let tables = connection
        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
        .map_err(|_| json_error("SQLite 表清单不可读"))?
        .query_map([], |row| row.get::<_, String>(0))
        .map_err(|_| json_error("SQLite 表清单不可读"))?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| json_error("SQLite 表清单无效"))?;
    let mut counts = BTreeMap::new();
    for table in tables {
        let count = connection
            .query_row(
                &format!("SELECT COUNT(*) FROM {}", quote_identifier(&table)),
                [],
                |row| row.get::<_, u64>(0),
            )
            .map_err(|_| json_error("SQLite 表计数不可读"))?;
        counts.insert(table, count);
    }
    Ok(counts)
}

fn is_business_non_empty(database: &Path, root: &Path) -> Result<bool, String> {
    let counts = table_counts(database)?;
    if counts
        .iter()
        .any(|(table, count)| *count > 0 && !EXCLUDED_TABLES.contains(&table.as_str()))
    {
        return Ok(true);
    }
    let usage_ledger = usage_ledger_path(root);
    if usage_ledger.is_file()
        && table_counts(&usage_ledger)?
            .values()
            .any(|count| *count > 0)
    {
        return Ok(true);
    }
    for controlled in CONTROLLED_ROOTS {
        let path = root.join(controlled.local);
        if path.exists()
            && fs::read_dir(&path)
                .map_err(|_| json_error("本机资产目录不可读"))?
                .next()
                .is_some()
        {
            return Ok(true);
        }
    }
    Ok(false)
}

fn safe_relative(path: &Path) -> Result<String, String> {
    let text = path
        .to_str()
        .ok_or_else(|| json_error("本机资产名称不是安全 UTF-8"))?
        .replace('\\', "/");
    if text.is_empty()
        || text.starts_with('/')
        || text
            .split('/')
            .any(|part| part.is_empty() || part == "." || part == "..")
        || !text
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || "._-/".contains(c))
    {
        return Err(json_error("本机资产名称不安全"));
    }
    Ok(text)
}

fn collect_controlled_files(root: &Path) -> Result<Vec<(String, PathBuf)>, String> {
    let mut files = Vec::new();
    let canonical_root = root
        .canonicalize()
        .map_err(|_| json_error("本机备份根不可读"))?;
    for controlled in CONTROLLED_ROOTS {
        let mut source = root.to_path_buf();
        let mut missing = false;
        for component in Path::new(controlled.local).components() {
            source.push(component);
            let metadata = match fs::symlink_metadata(&source) {
                Ok(metadata) => metadata,
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                    missing = true;
                    break;
                }
                Err(_) => return Err(json_error("本机受控资产根不可读")),
            };
            if !metadata.is_dir() || !plain_private_entry(&metadata) {
                return Err(json_error("本机受控资产根包含链接或非普通目录"));
            }
        }
        if missing {
            continue;
        }
        let source = source
            .canonicalize()
            .map_err(|_| json_error("本机受控资产根不可读"))?;
        if !source.starts_with(&canonical_root) {
            return Err(json_error("本机受控资产根发生路径逃逸"));
        }
        let mut pending = vec![source.clone()];
        while let Some(directory) = pending.pop() {
            for entry in fs::read_dir(&directory).map_err(|_| json_error("本机受控资产不可读"))?
            {
                let entry = entry.map_err(|_| json_error("本机受控资产不可读"))?;
                let metadata = fs::symlink_metadata(entry.path())
                    .map_err(|_| json_error("本机受控资产不可读"))?;
                if !plain_private_entry(&metadata) {
                    return Err(json_error("本机受控资产包含链接或非普通文件，已拒绝整个备份"));
                }
                if metadata.is_dir() {
                    pending.push(entry.path());
                } else if metadata.is_file() {
                    let canonical = entry
                        .path()
                        .canonicalize()
                        .map_err(|_| json_error("本机受控资产不可读"))?;
                    if !canonical.starts_with(&source) {
                        return Err(json_error("本机受控资产发生路径逃逸，已拒绝整个备份"));
                    }
                    let relative = canonical
                        .strip_prefix(&source)
                        .map_err(|_| json_error("本机受控资产路径无效"))?;
                    let relative = safe_relative(relative)?;
                    files.push((
                        format!("assets/{}/{relative}", controlled.package),
                        canonical,
                    ));
                }
            }
        }
    }
    files.sort_by(|left, right| left.0.cmp(&right.0));
    Ok(files)
}

fn manifest_without_hash(manifest: &BackupManifest) -> Result<Vec<u8>, String> {
    let mut value =
        serde_json::to_value(manifest).map_err(|_| json_error("备份 Manifest 无法编码"))?;
    value
        .as_object_mut()
        .ok_or_else(|| json_error("备份 Manifest 无效"))?
        .remove("manifestSha256");
    serde_json::to_vec(&value).map_err(|_| json_error("备份 Manifest 无法编码"))
}

fn write_zip_entry(
    writer: &mut ZipWriter<fs::File>,
    name: &str,
    source: &Path,
) -> Result<(), String> {
    writer
        .start_file(
            name,
            SimpleFileOptions::default().compression_method(CompressionMethod::Deflated),
        )
        .map_err(|_| json_error("备份 ZIP entry 无法创建"))?;
    let mut input = fs::File::open(source).map_err(|_| json_error("备份源文件不可读"))?;
    std::io::copy(&mut input, writer).map_err(|_| json_error("备份 ZIP entry 无法写入"))?;
    Ok(())
}

// Check SQLite source receipts against the exact bytes/hash facts packaged in the archive.
// Old databases without these owners remain supported; a receipt cannot silently lose its copy.
fn require_knowledge_asset_manifest(database:&Path,entries:&[ManifestEntry])->Result<(),String> {
    let c=Connection::open_with_flags(database,rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY).map_err(|_|json_error("知识来源收据数据库不可读"))?;
    let manifest:BTreeMap<_,_>=entries.iter().map(|e|(e.path.as_str(),(e.bytes,e.sha256.as_str()))).collect();
    let mut count=0;
    for (table,prefix,column) in [
        ("desktop_markdown_knowledge_tasks_v1","markdown-knowledge-import/v1/","source_hash"),
        ("desktop_json_knowledge_tasks_v1","json-knowledge-import/v1/","source_hash"),
        ("desktop_pdf_knowledge_tasks_v1","pdf-knowledge-import/v1/","source_hash"),
        ("desktop_web_knowledge_assets_v1","web-text-snapshots/v1/","raw_html_hash"),
    ] {
        if !table_exists(&c,table)? {continue;}
        let mut q=c.prepare(&format!("SELECT storage_key,byte_count,{column} FROM {table} WHERE storage_key IS NOT NULL LIMIT 10001")).map_err(|_|json_error("知识来源副本收据列无效"))?;
        let mut rows=q.query([]).map_err(|_|json_error("知识来源副本收据不可读"))?;
        while let Some(row)=rows.next().map_err(|_|json_error("知识来源副本收据不可读"))? {
            count+=1;if count>MAX_ENTRY_COUNT{return Err(json_error("知识来源副本收据过多"));}
            let key:String=row.get(0).map_err(|_|json_error("知识来源副本路径无效"))?;
            let bytes:i64=row.get(1).map_err(|_|json_error("知识来源副本大小无效"))?;
            let hash:String=row.get(2).map_err(|_|json_error("知识来源副本哈希无效"))?;
            if key.len()>4096 || !key.starts_with(prefix) || safe_relative(Path::new(&key)).as_deref()!=Ok(key.as_str()) || bytes<0 || hash.len()!=64 || !hash.bytes().all(|b|b.is_ascii_digit() || (b'a'..=b'f').contains(&b)) {
                return Err(json_error("知识来源副本收据无效"));
            }
            if manifest.get(format!("assets/{key}").as_str()).copied()!=Some((bytes as u64,hash.as_str())) {
                return Err(json_error("知识来源副本缺失或大小、哈希与收据不匹配"));
            }
        }
    }
    Ok(())
}
fn require_knowledge_checkpoint_assets(checkpoint:&Path)->Result<(),String> {
    let inventory=checkpoint_asset_inventory(checkpoint)?;
    let mut entries=Vec::new();
    for (key,hash) in inventory {
        if let Some(sha256)=hash {
            let bytes=fs::metadata(checkpoint.join("assets").join(&key)).map_err(|_|json_error("知识来源checkpoint副本不可读"))?.len();
            entries.push(ManifestEntry{path:format!("assets/{key}"),bytes,sha256});
        }
    }
    require_knowledge_asset_manifest(&checkpoint.join("workspace.snapshot"),&entries)
}

fn create_package(
    root: &Path,
    snapshot: &Path,
    package: &Path,
    app_version: &str,
    auxiliary_sources: Vec<(String, PathBuf)>,
    temporary_assets: &BTreeSet<String>,
) -> Result<BackupManifest, String> {
    let mut sources = vec![(DATABASE_ENTRY.to_owned(), snapshot.to_path_buf())];
    sources.extend(auxiliary_sources);
    sources.extend(collect_controlled_files(root)?.into_iter().filter(|(path, _)| {
        path.strip_prefix("assets/attachments/v1/")
            .map_or(true, |hash| !temporary_assets.contains(hash))
    }));
    if sources.len() + 1 > MAX_ENTRY_COUNT {
        return Err(json_error("本机备份 entry 数量超出安全上限"));
    }
    let mut entries = Vec::with_capacity(sources.len());
    for (path, source) in &sources {
        let metadata = fs::metadata(source).map_err(|_| json_error("备份源文件不可读"))?;
        if !metadata.is_file() || metadata.len() > MAX_ENTRY_BYTES {
            return Err(json_error("备份源文件大小或类型无效"));
        }
        entries.push(ManifestEntry {
            path: path.clone(),
            bytes: metadata.len(),
            sha256: sha256_file(source)?,
        });
    }
    require_knowledge_asset_manifest(snapshot,&entries)?;
    let mut manifest = BackupManifest {
        format: FORMAT.into(),
        version: FORMAT_VERSION,
        app_version: app_version.into(),
        schema_version: schema_version(snapshot)?,
        scope: "desktop_sqlite_usage_ledger_and_controlled_private_assets".into(),
        excluded: vec![
            "provider-secret-material".into(),
            "route-preferences".into(),
            "diagnostics".into(),
            "exports".into(),
            "registry".into(),
            "recovery-workspace".into(),
            "temporary-conversations-and-exclusive-attachments".into(),
            "search-and-preview-cache".into(),
            "signing-secrets".into(),
        ],
        table_counts: table_counts(snapshot)?,
        entries,
        manifest_sha256: String::new(),
    };
    manifest.manifest_sha256 = sha256_bytes(&manifest_without_hash(&manifest)?);
    let manifest_bytes =
        serde_json::to_vec(&manifest).map_err(|_| json_error("备份 Manifest 无法编码"))?;
    if let Some(parent) = package.parent() {
        fs::create_dir_all(parent).map_err(|_| json_error("无法创建备份 staging"))?;
    }
    let file = fs::File::create(package).map_err(|_| json_error("无法创建备份包"))?;
    let mut writer = ZipWriter::new(file);
    writer
        .start_file(
            MANIFEST_ENTRY,
            SimpleFileOptions::default().compression_method(CompressionMethod::Deflated),
        )
        .map_err(|_| json_error("备份 Manifest entry 无法创建"))?;
    writer
        .write_all(&manifest_bytes)
        .map_err(|_| json_error("备份 Manifest entry 无法写入"))?;
    for (name, source) in &sources {
        write_zip_entry(&mut writer, name, source)?;
    }
    writer
        .finish()
        .map_err(|_| json_error("备份 ZIP 无法完成"))?
        .sync_all()
        .map_err(|_| json_error("备份 ZIP 无法持久化"))?;
    Ok(manifest)
}

fn safe_package_entry(name: &str) -> bool {
    if name == MANIFEST_ENTRY || name == DATABASE_ENTRY || name == USAGE_LEDGER_ENTRY {
        return true;
    }
    let Some(relative) = name.strip_prefix("assets/") else {
        return false;
    };
    CONTROLLED_ROOTS.iter().any(|controlled| {
        relative
            .strip_prefix(controlled.package)
            .is_some_and(|suffix| {
                suffix.starts_with('/') && safe_relative(Path::new(&suffix[1..])).is_ok()
            })
    })
}

fn verify_package(path: &Path, expected_schema: u32) -> Result<VerifiedPackage, String> {
    let metadata = fs::metadata(path).map_err(|_| json_error("备份包不可读"))?;
    if !metadata.is_file() || metadata.len() == 0 || metadata.len() > MAX_ARCHIVE_BYTES {
        return Err(json_error("备份包大小或类型无效"));
    }
    let file = fs::File::open(path).map_err(|_| json_error("备份包不可读"))?;
    let mut archive = ZipArchive::new(file).map_err(|_| json_error("备份包不是受支持的 ZIP"))?;
    if archive.len() == 0 || archive.len() > MAX_ENTRY_COUNT {
        return Err(json_error("备份包 entry 数量无效"));
    }
    let mut seen = BTreeSet::new();
    let mut total = 0u64;
    let mut manifest_bytes = None;
    let mut actual = BTreeMap::new();
    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .map_err(|_| json_error("备份包 entry 不可读"))?;
        let name = entry.name().to_owned();
        if entry.is_dir()
            || !safe_package_entry(&name)
            || !seen.insert(name.clone())
            || entry
                .unix_mode()
                .is_some_and(|mode| mode & 0o170000 == 0o120000)
            || entry.size() > MAX_ENTRY_BYTES
        {
            return Err(json_error(
                "备份包包含重复、越界、符号链接或不受支持的 entry",
            ));
        }
        total = total
            .checked_add(entry.size())
            .ok_or_else(|| json_error("备份包展开大小溢出"))?;
        if total > MAX_TOTAL_BYTES
            || (entry.compressed_size() > 0
                && entry.size() / entry.compressed_size().max(1) > MAX_COMPRESSION_RATIO)
        {
            return Err(json_error("备份包展开大小或压缩比不安全"));
        }
        if name == MANIFEST_ENTRY {
            if entry.size() > MAX_MANIFEST_BYTES {
                return Err(json_error("备份 Manifest 超出安全上限"));
            }
            let mut bytes = Vec::with_capacity(entry.size() as usize);
            entry
                .read_to_end(&mut bytes)
                .map_err(|_| json_error("备份 Manifest 读取失败"))?;
            manifest_bytes = Some(bytes);
        } else {
            let bytes = entry.size();
            let sha256 = sha256_reader(&mut entry)?;
            actual.insert(
                name,
                ManifestEntry {
                    path: String::new(),
                    bytes,
                    sha256,
                },
            );
        }
    }
    let manifest: BackupManifest = serde_json::from_slice(
        manifest_bytes
            .as_deref()
            .ok_or_else(|| json_error("备份包缺少 Manifest"))?,
    )
    .map_err(|_| json_error("备份 Manifest 无效"))?;
    if manifest.format != FORMAT
        || manifest.version != FORMAT_VERSION
        || !backup_schema_compatible(expected_schema, manifest.schema_version)
        || manifest.manifest_sha256 != sha256_bytes(&manifest_without_hash(&manifest)?)
    {
        return Err(json_error("备份格式、Schema 或 Manifest 哈希不匹配"));
    }
    if manifest.entries.len() != actual.len() {
        return Err(json_error("备份 Manifest entry 清单不匹配"));
    }
    for listed in &manifest.entries {
        let actual = actual
            .get(&listed.path)
            .ok_or_else(|| json_error("备份 Manifest 缺少对应 entry"))?;
        if listed.bytes != actual.bytes || listed.sha256 != actual.sha256 {
            return Err(json_error("备份 entry 大小或哈希不匹配"));
        }
    }
    if !actual.contains_key(DATABASE_ENTRY) {
        return Err(json_error("备份包缺少 SQLite 快照"));
    }
    let asset_bytes = manifest
        .entries
        .iter()
        .filter(|entry| entry.path.starts_with("assets/"))
        .map(|entry| entry.bytes)
        .sum();
    Ok(VerifiedPackage {
        preflight: BackupPreflight {
            format: manifest.format.clone(),
            version: manifest.version,
            schema_version: manifest.schema_version,
            table_counts: manifest.table_counts.clone(),
            asset_bytes,
            missing: Vec::new(),
            conflicts: Vec::new(),
            unsupported: Vec::new(),
            fingerprint: sha256_file(path)?,
        },
        manifest,
    })
}

fn extract_verified(
    package: &Path,
    target: &Path,
    verified: &VerifiedPackage,
) -> Result<(), String> {
    if target.exists() {
        fs::remove_dir_all(target).map_err(|_| json_error("恢复 staging 冲突"))?;
    }
    fs::create_dir_all(target).map_err(|_| json_error("无法创建恢复 staging"))?;
    let file = fs::File::open(package).map_err(|_| json_error("隔离备份包不可读"))?;
    let mut archive = ZipArchive::new(file).map_err(|_| json_error("隔离备份包无效"))?;
    for listed in &verified.manifest.entries {
        let mut entry = archive
            .by_name(&listed.path)
            .map_err(|_| json_error("恢复 entry 缺失"))?;
        let destination = target.join(&listed.path);
        if !destination.starts_with(target) {
            return Err(json_error("恢复 entry 发生路径逃逸"));
        }
        if let Some(parent) = destination.parent() {
            fs::create_dir_all(parent).map_err(|_| json_error("无法创建恢复 entry 目录"))?;
        }
        let mut output =
            fs::File::create(&destination).map_err(|_| json_error("无法创建恢复 entry"))?;
        std::io::copy(&mut entry, &mut output).map_err(|_| json_error("恢复 entry 写入失败"))?;
        output
            .sync_all()
            .map_err(|_| json_error("恢复 entry 无法持久化"))?;
        if sha256_file(&destination)? != listed.sha256 {
            return Err(json_error("恢复 entry 回读哈希不一致"));
        }
    }
    Ok(())
}

fn package_asset_source(stage: &Path, controlled: ControlledRoot) -> PathBuf {
    stage.join("assets").join(controlled.package)
}

fn usage_ledger_path(root: &Path) -> PathBuf {
    root.join(USAGE_LEDGER_LOCAL)
}

fn validate_usage_ledger(path: &Path) -> Result<(), String> {
    let version=schema_version(path)?;
    let tables=table_counts(path)?;
    if !sqlite_healthy(path)?
        || !(1..=USAGE_LEDGER_SCHEMA).contains(&version)
        || !tables.contains_key("usage_ledger_entries")
        || (version>=2 && !tables.contains_key("usage_ledger_call_metadata"))
        || (version>=3 && !tables.contains_key("usage_ledger_call_recovery"))
        || (version>=4 && !tables.contains_key("usage_ledger_retrieval_progress"))
        || (version>=5 && !tables.contains_key("usage_ledger_call_outcomes"))
        || contains_sensitive_values(path)?
    {
        return Err(json_error("费用账本完整性、Schema 或保密边界不匹配"));
    }
    if version>=2 {
        crate::usage_ledger_v1::Ledger::validate_recovery_snapshot(path)
            .map_err(|_|json_error("费用调用记录或恢复收据不匹配；原始备份保留"))?;
    }
    Ok(())
}

fn validate_optional_usage_ledger(stage: &Path) -> Result<(), String> {
    let candidate = stage.join(USAGE_LEDGER_ENTRY);
    if candidate.exists() {
        validate_usage_ledger(&candidate)?;
    }
    Ok(())
}

fn copy_tree(source: &Path, destination: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(source).map_err(|_| json_error("恢复资产不可读"))?;
    if !plain_private_entry(&metadata) {
        return Err(json_error("恢复资产包含链接或非普通文件"));
    }
    if metadata.is_file() {
        if let Some(parent) = destination.parent() {
            fs::create_dir_all(parent).map_err(|_| json_error("无法创建恢复资产目录"))?;
        }
        fs::copy(source, destination).map_err(|_| json_error("恢复资产复制失败"))?;
        sync_file(destination)?;
        return Ok(());
    }
    fs::create_dir_all(destination).map_err(|_| json_error("无法创建恢复资产目录"))?;
    for entry in fs::read_dir(source).map_err(|_| json_error("恢复资产目录不可读"))? {
        let entry = entry.map_err(|_| json_error("恢复资产目录不可读"))?;
        copy_tree(&entry.path(), &destination.join(entry.file_name()))?;
    }
    Ok(())
}

fn copy_optional_tree(source: &Path, destination: &Path) -> Result<(), String> {
    match fs::symlink_metadata(source) {
        Ok(_) => copy_tree(source, destination),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(_) => Err(json_error("恢复资产不可读")),
    }
}

fn stage_v4_checkpoint_copy<F>(
    checkpoint: &Path,
    workspace_sha256: &str,
    usage_ledger_sha256: Option<&str>,
    assets: &BTreeMap<String, Option<String>>,
    after_first_copy: F,
) -> Result<PathBuf, String>
where
    F: FnOnce() -> Result<(), String>,
{
    if !matches!(fs::symlink_metadata(checkpoint), Ok(metadata) if metadata.file_type().is_dir() && plain_private_entry(&metadata)) {
        return Err(json_error("恢复 checkpoint 目录无效"));
    }
    // An interrupted process may leave only this exact app-owned staging tree.
    // Never copy directly over an active owner while source I/O can still fail.
    let stage = checkpoint.join("rollback-copy-stage");
    match fs::symlink_metadata(&stage) {
        Ok(metadata) if metadata.file_type().is_dir() && plain_private_entry(&metadata) => {
            fs::remove_dir_all(&stage).map_err(|_| json_error("旧回滚暂存无法清理；活动资料未改动"))?;
        }
        Ok(_) => return Err(json_error("回滚暂存目录无效；活动资料未改动")),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => return Err(json_error("回滚暂存目录不可读；活动资料未改动")),
    }
    fs::create_dir(&stage).map_err(|_| json_error("回滚暂存目录无法创建；活动资料未改动"))?;
    let result = (|| {
        let database_copy = stage.join("workspace.snapshot");
        fs::copy(checkpoint.join("workspace.snapshot"), &database_copy)
            .map_err(|_| json_error("回滚主库暂存复制失败；活动资料未改动"))?;
        sync_file(&database_copy)?;
        after_first_copy()?;
        if usage_ledger_sha256.is_some() {
            let ledger_copy = stage.join(USAGE_LEDGER_CHECKPOINT);
            fs::create_dir_all(ledger_copy.parent().unwrap())
                .map_err(|_| json_error("回滚账本暂存目录无法创建；活动资料未改动"))?;
            fs::copy(checkpoint.join(USAGE_LEDGER_CHECKPOINT), &ledger_copy)
                .map_err(|_| json_error("回滚账本暂存复制失败；活动资料未改动"))?;
            sync_file(&ledger_copy)?;
        }
        for controlled in CONTROLLED_ROOTS {
            copy_optional_tree(
                &checkpoint.join("assets").join(controlled.package),
                &stage.join("assets").join(controlled.package),
            )?;
        }
        if sha256_file(&database_copy)? != workspace_sha256
            || match usage_ledger_sha256 {
                Some(expected) => sha256_file(&stage.join(USAGE_LEDGER_CHECKPOINT))? != expected,
                None => fs::symlink_metadata(stage.join(USAGE_LEDGER_CHECKPOINT)).is_ok(),
            }
            || checkpoint_asset_inventory(&stage)? != *assets
        {
            return Err(json_error("回滚暂存与 checkpoint 清单不匹配；活动资料未改动"));
        }
        Ok(())
    })();
    if let Err(error) = result {
        let _ = fs::remove_dir_all(&stage);
        return Err(error);
    }
    Ok(stage)
}

fn visit_asset_inventory(path: &Path, key: &str, entries: &mut BTreeMap<String, Option<String>>) -> Result<(), String> {
    if entries.len() >= MAX_ENTRY_COUNT {
        return Err(json_error("恢复 checkpoint 资产条目过多"));
    }
    let metadata = fs::symlink_metadata(path).map_err(|_| json_error("恢复 checkpoint 资产不可读"))?;
    if !plain_private_entry(&metadata) {
        return Err(json_error("恢复 checkpoint 资产包含链接或非普通文件"));
    }
    if metadata.is_file() {
        entries.insert(key.into(), Some(sha256_file(path)?));
        return Ok(());
    }
    entries.insert(key.into(), None);
    for entry in fs::read_dir(path).map_err(|_| json_error("恢复 checkpoint 资产目录不可读"))? {
        let entry = entry.map_err(|_| json_error("恢复 checkpoint 资产目录不可读"))?;
        let name = entry.file_name();
        let name = name.to_str().ok_or_else(|| json_error("恢复 checkpoint 资产名称无效"))?;
        visit_asset_inventory(&entry.path(), &format!("{key}/{name}"), entries)?;
    }
    Ok(())
}

fn checkpoint_asset_inventory(checkpoint: &Path) -> Result<BTreeMap<String, Option<String>>, String> {
    let mut entries = BTreeMap::new();
    let assets_root = checkpoint.join("assets");
    match fs::symlink_metadata(&assets_root) {
        Ok(metadata) if metadata.is_dir() && plain_private_entry(&metadata) => {},
        Ok(_) => return Err(json_error("恢复 checkpoint 资产目录无效")),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(entries),
        Err(_) => return Err(json_error("恢复 checkpoint 资产目录不可读")),
    }
    for controlled in CONTROLLED_ROOTS {
        let path = assets_root.join(controlled.package);
        if let Some(parent) = path.parent() {
            if parent != assets_root {
                match fs::symlink_metadata(parent) {
                    Ok(metadata) if metadata.is_dir() && plain_private_entry(&metadata) => {},
                    Ok(_) => return Err(json_error("恢复 checkpoint 资产父目录无效")),
                    Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
                    Err(_) => return Err(json_error("恢复 checkpoint 资产父目录不可读")),
                }
            }
        }
        match fs::symlink_metadata(&path) {
            Ok(metadata) if metadata.is_dir() && plain_private_entry(&metadata) => {
                visit_asset_inventory(&path, controlled.package, &mut entries)?;
            }
            Ok(_) => return Err(json_error("恢复 checkpoint 资产根无效")),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {},
            Err(_) => return Err(json_error("恢复 checkpoint 资产根不可读")),
        }
    }
    Ok(entries)
}

fn asset_root_inventory(path: &Path) -> Result<Option<BTreeMap<String, Option<String>>>, String> {
    match fs::symlink_metadata(path) {
        Ok(metadata) if metadata.is_dir() && plain_private_entry(&metadata) => {
            let mut entries = BTreeMap::new();
            visit_asset_inventory(path, "root", &mut entries)?;
            Ok(Some(entries))
        }
        Ok(_) => Err(json_error("恢复资产根不是私有普通目录")),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(_) => Err(json_error("恢复资产根不可读")),
    }
}

fn expected_asset_root_inventory(
    assets: &BTreeMap<String, Option<String>>,
    package: &str,
) -> Option<BTreeMap<String, Option<String>>> {
    let prefix = format!("{package}/");
    let mut entries = BTreeMap::new();
    for (key, value) in assets {
        if key == package {
            entries.insert("root".to_owned(), value.clone());
        } else if let Some(relative) = key.strip_prefix(&prefix) {
            entries.insert(format!("root/{relative}"), value.clone());
        }
    }
    if entries.is_empty() { None } else { Some(entries) }
}

fn active_asset_inventory(root: &Path) -> Result<BTreeMap<String, Option<String>>, String> {
    let mut entries = BTreeMap::new();
    for controlled in CONTROLLED_ROOTS {
        let path = root.join(controlled.local);
        if let Some(parent) = path.parent() {
            if parent != root {
                match fs::symlink_metadata(parent) {
                    Ok(metadata) if metadata.is_dir() && plain_private_entry(&metadata) => {},
                    Ok(_) => return Err(json_error("活动资产父目录无效")),
                    Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
                    Err(_) => return Err(json_error("活动资产父目录不可读")),
                }
            }
        }
        match fs::symlink_metadata(&path) {
            Ok(metadata) if metadata.is_dir() && plain_private_entry(&metadata) => {
                visit_asset_inventory(&path, controlled.package, &mut entries)?;
            }
            Ok(_) => return Err(json_error("活动资产根无效")),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {},
            Err(_) => return Err(json_error("活动资产根不可读")),
        }
    }
    Ok(entries)
}

fn create_checkpoint(root: &Path, database: &Path, checkpoint_id: &str) -> Result<PathBuf, String> {
    let checkpoint = checkpoint_path(root, checkpoint_id);
    if checkpoint.exists() {
        return Err(json_error("恢复 checkpoint 冲突"));
    }
    fs::create_dir_all(&checkpoint).map_err(|_| json_error("无法创建恢复 checkpoint"))?;
    snapshot_database(database, &checkpoint.join("workspace.snapshot"))?;
    let usage_ledger = usage_ledger_path(root);
    if usage_ledger.is_file() {
        snapshot_database(&usage_ledger, &checkpoint.join(USAGE_LEDGER_CHECKPOINT))?;
    }
    for controlled in CONTROLLED_ROOTS {
        copy_optional_tree(
            &root.join(controlled.local),
            &checkpoint.join("assets").join(controlled.package),
        )?;
    }
    require_knowledge_checkpoint_assets(&checkpoint)?;
    if checkpoint_id.starts_with("checkpoint-v3-") || checkpoint_id.starts_with("checkpoint-v4-") {
        let usage_snapshot = checkpoint.join(USAGE_LEDGER_CHECKPOINT);
        let workspace_sha256 = sha256_file(&checkpoint.join("workspace.snapshot"))?;
        let usage_ledger_sha256 = if usage_snapshot.is_file() { Some(sha256_file(&usage_snapshot)?) } else { None };
        let v4 = checkpoint_id.starts_with("checkpoint-v4-");
        let bytes = if v4 {
            serde_json::to_vec(&CheckpointV4Manifest {
                version: 4, workspace_sha256, usage_ledger_sha256,
                assets: checkpoint_asset_inventory(&checkpoint)?,
            })
        } else {
            serde_json::to_vec(&CheckpointV3Manifest {
                version: 3, workspace_sha256, usage_ledger_sha256,
            })
        }.map_err(|_| json_error("恢复 checkpoint 清单无法编码"))?;
        if bytes.len() as u64 > MAX_MANIFEST_BYTES {
            return Err(json_error("恢复 checkpoint 清单过大"));
        }
        let path = checkpoint.join(if v4 { CHECKPOINT_V4_MANIFEST } else { CHECKPOINT_V3_MANIFEST });
        fs::write(&path, bytes).map_err(|_| json_error("恢复 checkpoint 清单无法保存"))?;
        sync_file(&path)?;
    }
    Ok(checkpoint)
}

fn copy_table(source: &Path, destination: &Path, table: &str) -> Result<(), String> {
    let destination_connection =
        Connection::open(destination).map_err(|_| json_error("候选数据库不可读"))?;
    if !table_exists(&destination_connection, table)? {
        return Ok(());
    }
    let source_connection = Connection::open(source).map_err(|_| json_error("当前数据库不可读"))?;
    if !table_exists(&source_connection, table)? {
        return Ok(());
    }
    let mut columns = table_columns(&destination_connection, table)?;
    let source_columns = table_columns(&source_connection, table)?;
    // An older CHAT backup has no retrieval flag. Preserve the current device's
    // independent setting before the restored area runs through Schema51.
    if table == "desktop_app_settings"
        && source_columns.len() == columns.len() + 1
        && source_columns.last().map(String::as_str) == Some("memory_retrieval_enabled")
        && source_columns[..columns.len()] == columns[..]
    {
        destination_connection.execute_batch("ALTER TABLE desktop_app_settings ADD COLUMN memory_retrieval_enabled INTEGER NOT NULL DEFAULT 1 CHECK(memory_retrieval_enabled IN (0,1));")
            .map_err(|_| json_error("设备记忆设置无法保留"))?;
        columns = table_columns(&destination_connection, table)?;
    }
    if columns != source_columns {
        return Err(json_error("设备专属状态 Schema 不匹配"));
    }
    drop(source_connection);
    let alias = "current_device";
    destination_connection
        .execute_batch(&format!(
            "ATTACH DATABASE '{}' AS {alias}",
            escape_sql_path(source)
        ))
        .map_err(|_| json_error("当前设备状态无法附加到候选数据库"))?;
    let list = columns
        .iter()
        .map(|column| quote_identifier(column))
        .collect::<Vec<_>>()
        .join(",");
    let result = destination_connection.execute_batch(&format!(
        "DELETE FROM {table}; INSERT INTO {table}({list}) SELECT {list} FROM {alias}.{table};",
        table = quote_identifier(table),
    ));
    let _ = destination_connection.execute_batch(&format!("DETACH DATABASE {alias}"));
    result.map_err(|_| json_error("当前设备专属状态无法保留"))
}

fn preserve_device_state(current: &Path, candidate: &Path) -> Result<(), String> {
    for table in PRESERVED_DEVICE_TABLES {
        copy_table(current, candidate, table)?;
    }
    Ok(())
}

fn clear_path(path: &Path) -> Result<(), String> {
    if !path.exists() {
        return Ok(());
    }
    let metadata = fs::symlink_metadata(path).map_err(|_| json_error("恢复目标不可读"))?;
    if metadata.is_dir() {
        fs::remove_dir_all(path).map_err(|_| json_error("恢复目标无法清理"))
    } else {
        fs::remove_file(path).map_err(|_| json_error("恢复目标无法清理"))
    }
}

fn restore_checkpoint_with_hook<F>(root: &Path, database: &Path, checkpoint_id: &str, after_preflight: F) -> Result<(), String>
where
    F: FnOnce() -> Result<(), String>,
{
    restore_checkpoint_with_copy_hook(root, database, checkpoint_id, after_preflight,
        || abort_windows_backup_rollback_copy_acceptance(root))
}

fn restore_checkpoint_with_copy_hook<F, G>(root: &Path, database: &Path, checkpoint_id: &str, after_preflight: F, after_first_copy: G) -> Result<(), String>
where
    F: FnOnce() -> Result<(), String>,
    G: FnOnce() -> Result<(), String>,
{
    restore_checkpoint_with_publish_hook(root, database, checkpoint_id, after_preflight,
        after_first_copy, || pause_windows_backup_rollback_publish_acceptance(root, 0),
        || pause_windows_backup_rollback_publish_acceptance(root, 1),
        || pause_windows_backup_rollback_publish_acceptance(root, 3),
        || pause_windows_backup_rollback_publish_acceptance(root, 2),
        || pause_windows_backup_rollback_publish_acceptance(root, 4),
        || pause_windows_backup_rollback_publish_acceptance(root, 5))
}

fn restore_checkpoint_with_publish_hook<F, G, H, I, J, K, L, M>(root: &Path, database: &Path, checkpoint_id: &str, after_preflight: F, after_first_copy: G, after_first_publish: H, after_ledger_publish: I, after_first_asset_clear: J, after_first_asset_publish: K, after_second_asset_clear: L, after_second_asset_publish: M) -> Result<(), String>
where
    F: FnOnce() -> Result<(), String>,
    G: FnOnce() -> Result<(), String>,
    H: FnOnce() -> Result<(), String>,
    I: FnOnce() -> Result<(), String>,
    J: FnOnce() -> Result<(), String>,
    K: FnOnce() -> Result<(), String>,
    L: FnOnce() -> Result<(), String>,
    M: FnOnce() -> Result<(), String>,
{
    let checkpoint = checkpoint_path(root, checkpoint_id);
    let snapshot = checkpoint.join("workspace.snapshot");
    if !matches!(fs::symlink_metadata(&snapshot), Ok(metadata) if metadata.file_type().is_file())
        || !sqlite_healthy(&snapshot)?
    {
        return Err(json_error("恢复 checkpoint 不可用"));
    }
    require_knowledge_checkpoint_assets(&checkpoint)?;
    // Validate every SQLite snapshot before replacing any live owner. A damaged
    // ledger must never be copied after the business database has been cleared.
    let usage_snapshot = checkpoint.join(USAGE_LEDGER_CHECKPOINT);
    let mut v4_expected = None;
    if checkpoint_id.starts_with("checkpoint-v3-") || checkpoint_id.starts_with("checkpoint-v4-") {
        let v4 = checkpoint_id.starts_with("checkpoint-v4-");
        let path = checkpoint.join(if v4 { CHECKPOINT_V4_MANIFEST } else { CHECKPOINT_V3_MANIFEST });
        if !matches!(fs::symlink_metadata(&path), Ok(metadata) if metadata.file_type().is_file() && metadata.len() > 0 && metadata.len() <= MAX_MANIFEST_BYTES) {
            return Err(json_error("恢复 checkpoint 清单不可用"));
        }
        let bytes = fs::read(&path).map_err(|_| json_error("恢复 checkpoint 清单不可读"))?;
        let (version, workspace_sha256, usage_ledger_sha256, assets) = if v4 {
            let manifest: CheckpointV4Manifest = serde_json::from_slice(&bytes)
                .map_err(|_| json_error("恢复 checkpoint 清单无效"))?;
            (manifest.version, manifest.workspace_sha256, manifest.usage_ledger_sha256, Some(manifest.assets))
        } else {
            let manifest: CheckpointV3Manifest = serde_json::from_slice(&bytes)
                .map_err(|_| json_error("恢复 checkpoint 清单无效"))?;
            (manifest.version, manifest.workspace_sha256, manifest.usage_ledger_sha256, None)
        };
        if version != if v4 { 4 } else { 3 }
            || sha256_file(&snapshot)? != workspace_sha256
            || match usage_ledger_sha256.as_deref() {
                Some(expected) => !matches!(fs::symlink_metadata(&usage_snapshot), Ok(metadata) if metadata.file_type().is_file())
                    || sha256_file(&usage_snapshot)? != expected,
                None => fs::symlink_metadata(&usage_snapshot).is_ok(),
            }
        {
            return Err(json_error("恢复 checkpoint 清单与快照不匹配"));
        }
        if let Some(expected) = assets {
            if checkpoint_asset_inventory(&checkpoint)? != expected {
                return Err(json_error("恢复 checkpoint 清单与资产不匹配"));
            }
            v4_expected = Some((workspace_sha256, usage_ledger_sha256, expected, sha256_file(&path)?));
        }
    }
    match fs::symlink_metadata(&usage_snapshot) {
        Ok(metadata) => {
            if !metadata.file_type().is_file() || validate_usage_ledger(&usage_snapshot).is_err() {
                return Err(json_error("费用账本恢复 checkpoint 不可用"));
            }
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => return Err(json_error("费用账本恢复 checkpoint 不可读")),
    }
    // v2 cannot tell whether an absent ledger snapshot means "originally absent"
    // or "lost after checkpoint". A current ledger makes that ambiguity unsafe.
    // For both v2 and v3, pin the observed snapshot through the last preflight.
    let legacy_ledger_sha = if v4_expected.is_none() {
        let expected = if usage_snapshot.is_file() { Some(sha256_file(&usage_snapshot)?) } else { None };
        if checkpoint_id.starts_with("checkpoint-v2-") && expected.is_none()
            && !matches!(fs::symlink_metadata(usage_ledger_path(root)), Err(error) if error.kind() == std::io::ErrorKind::NotFound)
        {
            return Err(json_error("旧恢复 checkpoint 费用账本来源无法验证；活动资料未改动"));
        }
        Some(expected)
    } else {
        None
    };
    // v2/v3 have no asset presence/hash manifest. A missing old snapshot is
    // indistinguishable from an asset root that was originally absent. Only
    // recover when current and checkpoint assets already match exactly; then
    // leave those bytes untouched while restoring the SQLite owners.
    let legacy_assets_unchanged = if v4_expected.is_none() {
        let snapshot_assets = checkpoint_asset_inventory(&checkpoint)?;
        if active_asset_inventory(root)? != snapshot_assets {
            return Err(json_error("旧恢复 checkpoint 资产来源无法验证；活动资料未改动"));
        }
        Some(snapshot_assets)
    } else {
        None
    };
    after_preflight()?;
    pause_windows_backup_rollback_acceptance(root)?;
    // The acceptance pause models a source changing after the initial check.
    // Recheck every v4 snapshot before clearing any active owner, including the
    // manifest that binds their identities. A later copy failure still retains
    // the checkpoint for another startup, but this known race must fail closed.
    if let Some((workspace_sha256, usage_ledger_sha256, assets, manifest_sha256)) = v4_expected.as_ref() {
        let manifest_path = checkpoint.join(CHECKPOINT_V4_MANIFEST);
        if !matches!(fs::symlink_metadata(&manifest_path), Ok(metadata) if metadata.file_type().is_file())
            || sha256_file(&manifest_path)? != *manifest_sha256
            || !matches!(fs::symlink_metadata(&snapshot), Ok(metadata) if metadata.file_type().is_file())
            || sha256_file(&snapshot)? != *workspace_sha256
            || match usage_ledger_sha256.as_deref() {
                Some(expected) => !matches!(fs::symlink_metadata(&usage_snapshot), Ok(metadata) if metadata.file_type().is_file())
                    || sha256_file(&usage_snapshot)? != expected,
                None => fs::symlink_metadata(&usage_snapshot).is_ok(),
            }
            || checkpoint_asset_inventory(&checkpoint)? != *assets
        {
            return Err(json_error("恢复 checkpoint 在预检后变化；活动资料未改动"));
        }
    }
    if let Some(expected) = legacy_assets_unchanged.as_ref() {
        if checkpoint_asset_inventory(&checkpoint)? != *expected
            || active_asset_inventory(root)? != *expected
        {
            return Err(json_error("旧恢复 checkpoint 资产在预检后变化；活动资料未改动"));
        }
    }
    if let Some(expected) = legacy_ledger_sha.as_ref() {
        let actual = match fs::symlink_metadata(&usage_snapshot) {
            Ok(metadata) if metadata.file_type().is_file() => Some(sha256_file(&usage_snapshot)?),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => None,
            _ => return Err(json_error("旧恢复 checkpoint 费用账本在预检后变化")),
        };
        if actual != *expected
            || (checkpoint_id.starts_with("checkpoint-v2-") && expected.is_none()
                && !matches!(fs::symlink_metadata(usage_ledger_path(root)), Err(error) if error.kind() == std::io::ErrorKind::NotFound))
        {
            return Err(json_error("旧恢复 checkpoint 费用账本在预检后变化；活动资料未改动"));
        }
    }
    let staged_v4 = if let Some((workspace_sha256, usage_ledger_sha256, assets, _)) = v4_expected.as_ref() {
        Some(stage_v4_checkpoint_copy(
            &checkpoint, workspace_sha256, usage_ledger_sha256.as_deref(), assets, after_first_copy,
        )?)
    } else {
        None
    };
    let usage_ledger = usage_ledger_path(root);
    let result = (|| {
        if let Some(stage) = staged_v4.as_ref() {
            // The stage and active database share a volume. Replacing an existing
            // file in one rename avoids a crash window with no active database.
            fs::rename(stage.join("workspace.snapshot"), database)
                .map_err(|_| json_error("checkpoint 数据库回滚失败"))?;
        } else {
            clear_path(database)?;
            fs::copy(&snapshot, database).map_err(|_| json_error("checkpoint 数据库回滚失败"))?;
        }
        sync_file(database)?;
        if staged_v4.is_some() {
            after_first_publish()?;
        }
        let ledger_source = staged_v4.as_ref()
            .map(|stage| stage.join(USAGE_LEDGER_CHECKPOINT))
            .unwrap_or_else(|| usage_snapshot.clone());
        if ledger_source.is_file() {
            if let Some(parent) = usage_ledger.parent() {
                fs::create_dir_all(parent).map_err(|_| json_error("费用账本回滚目录不可用"))?;
            }
            if staged_v4.is_some() {
                fs::rename(&ledger_source, &usage_ledger)
                    .map_err(|_| json_error("checkpoint 费用账本回滚失败"))?;
            } else {
                clear_path(&usage_ledger)?;
                fs::copy(&ledger_source, &usage_ledger)
                    .map_err(|_| json_error("checkpoint 费用账本回滚失败"))?;
            }
            sync_file(&usage_ledger)?;
        } else {
            clear_path(&usage_ledger)?;
        }
        if staged_v4.is_some() {
            after_ledger_publish()?;
        }
        let displaced_root = checkpoint.join("rollback-displaced-assets");
        if legacy_assets_unchanged.is_none() {
            let mut first_asset_clear_hook = Some(after_first_asset_clear);
            let mut first_asset_hook = Some(after_first_asset_publish);
            let mut second_asset_clear_hook = Some(after_second_asset_clear);
            let mut second_asset_hook = Some(after_second_asset_publish);
            for (index, controlled) in CONTROLLED_ROOTS.iter().enumerate() {
                let destination = root.join(controlled.local);
                let source = staged_v4.as_ref().unwrap_or(&checkpoint)
                    .join("assets").join(controlled.package);
                if staged_v4.is_some() {
                    let source_inventory = asset_root_inventory(&source)?;
                    let expected = expected_asset_root_inventory(
                        &v4_expected.as_ref().expect("v4 stage has a manifest").2,
                        controlled.package,
                    );
                    if source_inventory != expected {
                        return Err(json_error("回滚暂存资产在发布前变化；活动资产未改动"));
                    }
                    let displaced = displaced_root.join(format!("root-{index}"));
                    let held = asset_root_inventory(&displaced)?.is_some();
                    let active = asset_root_inventory(&destination)?;
                    if held && active.is_some() {
                        // A previous attempt already published this complete root. Keep it
                        // instead of recursively deleting either copy on restart.
                        if active != source_inventory {
                            return Err(json_error("已发布资产与恢复 checkpoint 不匹配"));
                        }
                        continue;
                    }
                    let moved_now = active.is_some();
                    if moved_now {
                        match fs::symlink_metadata(&displaced_root) {
                            Ok(metadata) if metadata.is_dir() && plain_private_entry(&metadata) => {}
                            Ok(_) => return Err(json_error("回滚旧资产保留目录无效")),
                            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                                fs::create_dir(&displaced_root).map_err(|_| json_error("回滚旧资产保留目录无法创建"))?;
                            }
                            Err(_) => return Err(json_error("回滚旧资产保留目录不可读")),
                        }
                        fs::rename(&destination, &displaced)
                            .map_err(|_| json_error("回滚旧资产无法安全移出"))?;
                    }
                    if index == 0 {
                        if let Err(error) = first_asset_clear_hook.take().expect("first asset clear hook only runs once")() {
                            if moved_now { let _ = fs::rename(&displaced, &destination); }
                            return Err(error);
                        }
                    } else if index == 1 {
                        if let Err(error) = second_asset_clear_hook.take().expect("second asset clear hook only runs once")() {
                            if moved_now { let _ = fs::rename(&displaced, &destination); }
                            return Err(error);
                        }
                    }
                    if source_inventory.is_some() {
                        if fs::rename(&source, &destination).is_err() {
                            if moved_now { let _ = fs::rename(&displaced, &destination); }
                            return Err(json_error("checkpoint 资产回滚失败"));
                        }
                    }
                    if index == 0 {
                        first_asset_hook.take().expect("first asset hook only runs once")()?;
                    } else if index == 1 {
                        second_asset_hook.take().expect("second asset hook only runs once")()?;
                    }
                } else {
                    clear_path(&destination)?;
                    copy_optional_tree(&source, &destination)?;
                }
            }
        }
        if let Some((workspace_sha256, usage_ledger_sha256, assets, _manifest_sha256)) = v4_expected {
            if sha256_file(database)? != workspace_sha256
                || match usage_ledger_sha256.as_deref() {
                    Some(expected) => sha256_file(&usage_ledger)? != expected,
                    None => fs::symlink_metadata(&usage_ledger).is_ok(),
                }
                || active_asset_inventory(root)? != assets
            {
                return Err(json_error("恢复 checkpoint 写回与清单不匹配"));
            }
        }
        if let Some(expected) = legacy_assets_unchanged {
            if checkpoint_asset_inventory(&checkpoint)? != expected
                || active_asset_inventory(root)? != expected
            {
                return Err(json_error("旧恢复 checkpoint 写回时资产发生变化"));
            }
        }
        if let Some(expected) = legacy_ledger_sha {
            let actual = match fs::symlink_metadata(&usage_ledger) {
                Ok(metadata) if metadata.file_type().is_file() => Some(sha256_file(&usage_ledger)?),
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => None,
                _ => return Err(json_error("旧恢复 checkpoint 写回时费用账本无效")),
            };
            if actual != expected {
                return Err(json_error("旧恢复 checkpoint 写回时费用账本不匹配"));
            }
        }
        if staged_v4.is_some() {
            match fs::symlink_metadata(&displaced_root) {
                Ok(metadata) if metadata.is_dir() && plain_private_entry(&metadata) => {
                    fs::remove_dir_all(&displaced_root)
                        .map_err(|_| json_error("回滚旧资产保留目录未能清理"))?;
                }
                Ok(_) => return Err(json_error("回滚旧资产保留目录无效")),
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
                Err(_) => return Err(json_error("回滚旧资产保留目录不可读")),
            }
        }
        Ok(())
    })();
    if let Some(stage) = staged_v4 {
        let _ = fs::remove_dir_all(stage);
    }
    result
}

fn restore_checkpoint(root: &Path, database: &Path, checkpoint_id: &str) -> Result<(), String> {
    restore_checkpoint_with_hook(root, database, checkpoint_id, || Ok(()))
}

#[cfg(all(windows, debug_assertions))]
fn abort_windows_backup_rollback_copy_acceptance(root: &Path) -> Result<(), String> {
    let Ok(requested_area) = std::env::var("NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_COPY_ABORT") else {
        return Ok(());
    };
    if requested_area.is_empty() { return Ok(()); }
    let work = root.file_name() == Some(std::ffi::OsStr::new("work-area"));
    let area = if work { "WORK" } else { "CHAT" };
    let app_root = if work { root.parent().ok_or_else(|| json_error("回滚复制验收根无效"))? } else { root };
    let verified = crate::desktop_windows_acceptance::resolve(
        Some(app_root), true, crate::desktop_windows_acceptance::IDENTIFIER, &std::env::temp_dir(),
    )?.ok_or_else(|| json_error("回滚复制验收根无效"))?;
    if requested_area != area || verified != app_root {
        return Err(json_error("回滚复制验收区域不符"));
    }
    Err(json_error("受控回滚复制中断；活动资料未改动"))
}

#[cfg(not(all(windows, debug_assertions)))]
fn abort_windows_backup_rollback_copy_acceptance(_root: &Path) -> Result<(), String> { Ok(()) }

#[cfg(all(windows, debug_assertions))]
fn pause_windows_backup_rollback_publish_acceptance(root: &Path, point: u8) -> Result<(), String> {
    let (variable, marker_point) = match point {
        0 => ("NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_PUBLISH_PAUSE", "rollback-publish"),
        1 => ("NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_LEDGER_PUBLISH_PAUSE", "rollback-publish-ledger"),
        2 => ("NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_ASSET_PUBLISH_PAUSE", "rollback-publish-asset"),
        3 => ("NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_ASSET_CLEAR_PAUSE", "rollback-clear-asset"),
        4 => ("NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_SECOND_ASSET_CLEAR_PAUSE", "rollback-clear-second-asset"),
        5 => ("NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_SECOND_ASSET_PUBLISH_PAUSE", "rollback-publish-second-asset"),
        _ => return Err(json_error("回滚发布验收停点无效")),
    };
    let Ok(requested_area) = std::env::var(variable) else {
        return Ok(());
    };
    if requested_area.is_empty() { return Ok(()); }
    let work = root.file_name() == Some(std::ffi::OsStr::new("work-area"));
    let area = if work { "WORK" } else { "CHAT" };
    let app_root = if work { root.parent().ok_or_else(|| json_error("回滚发布验收根无效"))? } else { root };
    let verified = crate::desktop_windows_acceptance::resolve(
        Some(app_root), true, crate::desktop_windows_acceptance::IDENTIFIER, &std::env::temp_dir(),
    )?.ok_or_else(|| json_error("回滚发布验收根无效"))?;
    if requested_area != area || verified != app_root {
        return Err(json_error("回滚发布验收区域不符"));
    }
    let marker = app_root.join(format!("local-backup-{area}-{marker_point}.reached"));
    let resume = app_root.join(format!("local-backup-{area}-{marker_point}.continue"));
    if fs::symlink_metadata(&resume).is_ok() {
        return Err(json_error("回滚发布验收继续标记已存在"));
    }
    let file = fs::OpenOptions::new().write(true).create_new(true).open(marker)
        .map_err(|_| json_error("回滚发布验收停点标记无法创建"))?;
    file.sync_all().map_err(|_| json_error("回滚发布验收停点标记无法持久化"))?;
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(60);
    loop {
        match fs::symlink_metadata(&resume) {
            Ok(metadata) if metadata.file_type().is_file() && plain_private_entry(&metadata) && metadata.len() == 0 => return Ok(()),
            Ok(_) => return Err(json_error("回滚发布验收继续标记无效")),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => return Err(json_error("回滚发布验收继续标记不可读")),
        }
        if std::time::Instant::now() >= deadline {
            return Err(json_error("回滚发布验收停点等待超时"));
        }
        std::thread::sleep(std::time::Duration::from_millis(100));
    }
}

#[cfg(not(all(windows, debug_assertions)))]
fn pause_windows_backup_rollback_publish_acceptance(_root: &Path, _point: u8) -> Result<(), String> { Ok(()) }

#[cfg(all(windows, debug_assertions))]
fn pause_windows_backup_rollback_acceptance(root: &Path) -> Result<(), String> {
    let Ok(requested_area) = std::env::var("NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_PAUSE") else {
        return Ok(());
    };
    if requested_area.is_empty() {
        return Ok(());
    }
    let work = root.file_name() == Some(std::ffi::OsStr::new("work-area"));
    let area = if work { "WORK" } else { "CHAT" };
    let app_root = if work { root.parent().ok_or_else(|| json_error("回滚验收根无效"))? } else { root };
    let verified = crate::desktop_windows_acceptance::resolve(
        Some(app_root), true, crate::desktop_windows_acceptance::IDENTIFIER, &std::env::temp_dir(),
    )?.ok_or_else(|| json_error("回滚验收根无效"))?;
    if requested_area != area || verified != app_root {
        return Err(json_error("回滚验收区域不符"));
    }
    let marker = app_root.join(format!("local-backup-{area}-rollback-preflight.reached"));
    let resume = app_root.join(format!("local-backup-{area}-rollback-preflight.continue"));
    if fs::symlink_metadata(&resume).is_ok() {
        return Err(json_error("回滚验收继续标记已存在"));
    }
    let file = fs::OpenOptions::new().write(true).create_new(true).open(marker)
        .map_err(|_| json_error("回滚验收停点标记无法创建"))?;
    file.sync_all().map_err(|_| json_error("回滚验收停点标记无法持久化"))?;
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(60);
    loop {
        match fs::symlink_metadata(&resume) {
            Ok(metadata) if metadata.file_type().is_file() && plain_private_entry(&metadata) && metadata.len() == 0 => return Ok(()),
            Ok(_) => return Err(json_error("回滚验收继续标记无效")),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {},
            Err(_) => return Err(json_error("回滚验收继续标记不可读")),
        }
        if std::time::Instant::now() >= deadline {
            return Err(json_error("回滚验收停点等待超时"));
        }
        std::thread::sleep(std::time::Duration::from_millis(100));
    }
}

#[cfg(not(all(windows, debug_assertions)))]
fn pause_windows_backup_rollback_acceptance(_root: &Path) -> Result<(), String> {
    Ok(())
}

fn switch_candidate_with_hook<F>(
    root: &Path,
    database: &Path,
    stage: &Path,
    checkpoint_id: &str,
    after_first_asset_switch: F,
) -> Result<(), String>
where
    F: FnOnce() -> Result<(), String>,
{
    let candidate = stage.join(DATABASE_ENTRY);
    let switch = switch_old_path(root, checkpoint_id);
    fs::create_dir(&switch).map_err(|_| json_error("无法创建恢复切换隔离区"))?;
    let mut after_first_asset_switch = Some(after_first_asset_switch);
    let result = (|| {
        let old_database = switch.join("workspace.sqlite3");
        if database.exists() {
            fs::rename(database, &old_database)
                .map_err(|_| json_error("当前数据库无法进入恢复隔离区"))?;
        }
        fs::rename(&candidate, database).map_err(|_| json_error("候选数据库无法原子发布"))?;
        sync_file(database)?;
        let usage_ledger = usage_ledger_path(root);
        let old_usage_ledger = switch.join(USAGE_LEDGER_LOCAL);
        if usage_ledger.exists() {
            if let Some(parent) = old_usage_ledger.parent() {
                fs::create_dir_all(parent).map_err(|_| json_error("无法创建费用账本切换隔离区"))?;
            }
            fs::rename(&usage_ledger, &old_usage_ledger)
                .map_err(|_| json_error("当前费用账本无法进入恢复隔离区"))?;
        }
        let candidate_usage_ledger = stage.join(USAGE_LEDGER_ENTRY);
        if candidate_usage_ledger.exists() {
            if let Some(parent) = usage_ledger.parent() {
                fs::create_dir_all(parent).map_err(|_| json_error("无法创建费用账本目标目录"))?;
            }
            fs::rename(&candidate_usage_ledger, &usage_ledger)
                .map_err(|_| json_error("候选费用账本无法原子发布"))?;
            sync_file(&usage_ledger)?;
        }
        for controlled in CONTROLLED_ROOTS {
            let destination = root.join(controlled.local);
            let old = switch.join("assets").join(controlled.package);
            if destination.exists() {
                if let Some(parent) = old.parent() {
                    fs::create_dir_all(parent).map_err(|_| json_error("无法创建资产切换隔离区"))?;
                }
                fs::rename(&destination, &old)
                    .map_err(|_| json_error("当前资产无法进入恢复隔离区"))?;
            }
            let source = package_asset_source(stage, *controlled);
            if source.exists() {
                if let Some(parent) = destination.parent() {
                    fs::create_dir_all(parent)
                        .map_err(|_| json_error("无法创建恢复资产目标目录"))?;
                }
                fs::rename(source, &destination).map_err(|_| json_error("候选资产无法原子发布"))?;
            }
            if let Some(hook) = after_first_asset_switch.take() {
                hook()?;
            }
        }
        Ok(())
    })();
    if result.is_err() {
        let _ = clear_path(database);
        let old_database = switch.join("workspace.sqlite3");
        if old_database.exists() {
            let _ = fs::rename(&old_database, database);
        }
        let usage_ledger = usage_ledger_path(root);
        let old_usage_ledger = switch.join(USAGE_LEDGER_LOCAL);
        let _ = clear_path(&usage_ledger);
        if old_usage_ledger.exists() {
            if let Some(parent) = usage_ledger.parent() {
                let _ = fs::create_dir_all(parent);
            }
            let _ = fs::rename(&old_usage_ledger, &usage_ledger);
        }
        for controlled in CONTROLLED_ROOTS {
            let destination = root.join(controlled.local);
            let old = switch.join("assets").join(controlled.package);
            let _ = clear_path(&destination);
            if old.exists() {
                if let Some(parent) = destination.parent() {
                    let _ = fs::create_dir_all(parent);
                }
                let _ = fs::rename(old, destination);
            }
        }
    }
    let _ = fs::remove_dir_all(&switch);
    result
}

pub fn export_selected(
    root: &Path,
    database: &Path,
    selected_path: &str,
    app_version: &str,
) -> Result<BackupArtifact, String> {
    require_no_pending_rollback(root)?;
    if requires_restart(root) {
        return Err(json_error("本机恢复已完成，请完全重启 App"));
    }
    let selected = selected_backup_path(selected_path, false)?;
    let stage = owner_root(root).join(unique_id("export"));
    fs::create_dir_all(&stage).map_err(|_| json_error("无法创建备份 staging"))?;
    let result = (|| {
        let snapshot = stage.join("nanfeng-ai.snapshot");
        snapshot_database(database, &snapshot)?;
        let temporary_assets = sanitize_snapshot(&snapshot)?;
        let mut auxiliary_sources = Vec::new();
        let usage_ledger = usage_ledger_path(root);
        if usage_ledger.is_file() {
            let usage_snapshot = stage.join("desktop-usage-ledger-v1.snapshot");
            snapshot_database(&usage_ledger, &usage_snapshot)?;
            validate_usage_ledger(&usage_snapshot)?;
            auxiliary_sources.push((USAGE_LEDGER_ENTRY.to_owned(), usage_snapshot));
        }
        let package = stage.join("backup.nfai-backup");
        let manifest = create_package(root, &snapshot, &package, app_version, auxiliary_sources, &temporary_assets)?;
        let expected_hash = sha256_file(&package)?;
        let verified = verify_package(&package, manifest.schema_version)?;
        let destination_parent = selected
            .parent()
            .ok_or_else(|| json_error("所选备份位置无效"))?;
        fs::create_dir_all(destination_parent).map_err(|_| json_error("所选备份位置不可写"))?;
        let temporary = destination_parent.join(format!(".{}.part", unique_id("nfai-backup")));
        fs::copy(&package, &temporary).map_err(|_| json_error("无法写入所选备份位置"))?;
        sync_file(&temporary)?;
        fs::rename(&temporary, &selected).map_err(|_| json_error("无法发布到所选备份位置"))?;
        let written_hash = sha256_file(&selected)?;
        if written_hash != expected_hash {
            return Err(json_error("所选位置回读哈希不一致"));
        }
        Ok(BackupArtifact {
            file_name: "nanfeng-ai-local-backup.nfai-backup".into(),
            sha256: written_hash,
            byte_count: fs::metadata(&selected)
                .map_err(|_| json_error("所选位置回读失败"))?
                .len(),
            schema_version: verified.preflight.schema_version,
            table_counts: verified.preflight.table_counts,
        })
    })();
    let _ = fs::remove_dir_all(stage);
    result
}

pub fn preflight_selected(
    root: &Path,
    database: &Path,
    selected_path: &str,
) -> Result<BackupPreflight, String> {
    require_no_pending_rollback(root)?;
    if requires_restart(root) {
        return Err(json_error("本机恢复已完成，请完全重启 App"));
    }
    require_no_retained_checkpoint(root)?;
    let selected = selected_backup_path(selected_path, true)?;
    let inbox = inbox_path(root);
    if let Some(parent) = inbox.parent() {
        fs::create_dir_all(parent).map_err(|_| json_error("无法创建备份私有 inbox"))?;
    }
    let temporary = inbox.with_extension("part");
    let input = fs::File::open(selected).map_err(|_| json_error("所选备份文件不可读"))?;
    let mut output =
        fs::File::create(&temporary).map_err(|_| json_error("无法创建备份私有副本"))?;
    let copied = std::io::copy(&mut input.take(MAX_ARCHIVE_BYTES + 1), &mut output)
        .map_err(|_| json_error("备份私有副本写入失败"))?;
    if copied == 0 || copied > MAX_ARCHIVE_BYTES {
        let _ = fs::remove_file(&temporary);
        return Err(json_error("所选备份文件大小无效"));
    }
    output
        .sync_all()
        .map_err(|_| json_error("备份私有副本无法持久化"))?;
    fs::rename(&temporary, &inbox).map_err(|_| json_error("备份私有副本无法发布"))?;
    let current_schema = schema_version(database)?;
    let verified = verify_package(&inbox, current_schema)?;
    let inspect = owner_root(root).join(unique_id("preflight"));
    let result = (|| {
        extract_verified(&inbox, &inspect, &verified)?;
        let candidate = inspect.join(DATABASE_ENTRY);
        if !sqlite_healthy(&candidate)?
            || schema_version(&candidate)? != verified.preflight.schema_version
            || table_counts(&candidate)? != verified.preflight.table_counts
            || contains_sensitive_values(&candidate)?
        {
            return Err(json_error("候选数据库完整性、Schema、计数或保密边界不匹配"));
        }
        require_knowledge_asset_manifest(&candidate,&verified.manifest.entries)?;
        require_temporary_backup_isolation(&candidate)?;
        require_matching_schema45_area(database, &candidate, current_schema)?;
        require_forward_area_schema(current_schema, verified.preflight.schema_version, &candidate)?;
        validate_optional_usage_ledger(&inspect)?;
        let mut preflight = verified.preflight;
        if is_business_non_empty(database, root)? {
            preflight
                .conflicts
                .push("本地已有业务数据；只能明确选择替换本地或取消，不支持合并。".into());
        }
        write_state(
            root,
            &PersistedState {
                operation: "PREFLIGHTED".into(),
                phase: "READY".into(),
                fingerprint: Some(preflight.fingerprint.clone()),
                checkpoint_id: None,
                process_id: Some(std::process::id()),
            process_instance: Some(current_process_instance()?.to_owned()),
            },
        )?;
        Ok(preflight)
    })();
    let _ = fs::remove_dir_all(inspect);
    if result.is_err() {
        let _ = fs::remove_file(&inbox);
    }
    result
}

pub fn restore_preflighted(
    root: &Path,
    database: &Path,
    fingerprint: &str,
    replace_local: bool,
) -> Result<RestoreReceipt, String> {
    restore_preflighted_with_hook(root, database, fingerprint, replace_local, || {
        pause_windows_backup_switch_acceptance(root)
    })
}

#[cfg(all(windows, debug_assertions))]
fn pause_windows_backup_switch_acceptance(root: &Path) -> Result<(), String> {
    let Ok(requested_area) = std::env::var("NANFENG_AI_WINDOWS_BACKUP_SWITCH_PAUSE") else {
        return Ok(());
    };
    if requested_area.is_empty() {
        return Ok(());
    }
    let work = root.file_name() == Some(std::ffi::OsStr::new("work-area"));
    let area = if work { "WORK" } else { "CHAT" };
    let app_root = if work {
        root.parent()
            .ok_or_else(|| json_error("备份强杀验收根无效"))?
    } else {
        root
    };
    let verified = crate::desktop_windows_acceptance::resolve(
        Some(app_root),
        true,
        crate::desktop_windows_acceptance::IDENTIFIER,
        &std::env::temp_dir(),
    )?
    .ok_or_else(|| json_error("备份强杀验收根无效"))?;
    if requested_area != area || verified != app_root {
        return Err(json_error("备份强杀验收区域不符"));
    }
    let marker = app_root.join(format!("local-backup-{area}-after-first-asset.reached"));
    let file = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(marker)
        .map_err(|_| json_error("备份强杀验收标记无法创建"))?;
    file.sync_all()
        .map_err(|_| json_error("备份强杀验收标记无法持久化"))?;
    loop {
        std::thread::park_timeout(std::time::Duration::from_secs(1));
    }
}

#[cfg(not(all(windows, debug_assertions)))]
fn pause_windows_backup_switch_acceptance(_root: &Path) -> Result<(), String> {
    Ok(())
}

fn restore_preflighted_with_hook<F>(
    root: &Path,
    database: &Path,
    fingerprint: &str,
    replace_local: bool,
    after_first_asset_switch: F,
) -> Result<RestoreReceipt, String>
where
    F: FnOnce() -> Result<(), String>,
{
    require_no_pending_rollback(root)?;
    if requires_restart(root) {
        return Err(json_error("本机恢复已完成，请完全重启 App"));
    }
    require_no_retained_checkpoint(root)?;
    let current_schema = schema_version(database)?;
    let inbox = inbox_path(root);
    let verified = verify_package(&inbox, current_schema)?;
    let persisted = read_state(root)?;
    if persisted.fingerprint.as_deref() != Some(fingerprint)
        || verified.preflight.fingerprint != fingerprint
    {
        return Err(json_error("备份包或本地预检已变化，请重新选择并预检"));
    }
    if is_business_non_empty(database, root)? && !replace_local {
        return Err(json_error("本地已有数据：请明确选择替换本地或取消"));
    }
    let checkpoint_id = unique_id("checkpoint-v4");
    let stage = restore_stage_path(root, &checkpoint_id);
    if fs::symlink_metadata(&stage).is_ok() {
        return Err(json_error("恢复 staging 冲突"));
    }
    extract_verified(&inbox, &stage, &verified)?;
    let candidate = stage.join(DATABASE_ENTRY);
    if !sqlite_healthy(&candidate)?
        || schema_version(&candidate)? != verified.preflight.schema_version
        || table_counts(&candidate)? != verified.preflight.table_counts
        || contains_sensitive_values(&candidate)?
    {
        let _ = fs::remove_dir_all(stage);
        return Err(json_error("候选数据库完整性、Schema、计数或保密边界不匹配"));
    }
    if let Err(error) = require_knowledge_asset_manifest(&candidate,&verified.manifest.entries)
        .and_then(|()| require_temporary_backup_isolation(&candidate))
        .and_then(|()| require_matching_schema45_area(database, &candidate, current_schema))
        .and_then(|()| require_forward_area_schema(current_schema, verified.preflight.schema_version, &candidate)) {
        let _ = fs::remove_dir_all(stage);
        return Err(error);
    }
    validate_optional_usage_ledger(&stage)?;
    preserve_device_state(database, &candidate)?;
    create_checkpoint(root, database, &checkpoint_id)?;
    write_state(
        root,
        &PersistedState {
            operation: "INTERRUPTED".into(),
            phase: "CHECKPOINTED".into(),
            fingerprint: Some(fingerprint.into()),
            checkpoint_id: Some(checkpoint_id.clone()),
            process_id: Some(std::process::id()),
            process_instance: Some(current_process_instance()?.to_owned()),
        },
    )?;
    let mut switching = read_state(root)?;
    switching.phase = "SWITCHING".into();
    write_state(root, &switching)?;
    if let Err(error) = switch_candidate_with_hook(
        root,
        database,
        &stage,
        &checkpoint_id,
        after_first_asset_switch,
    ) {
        let rollback = restore_checkpoint(root, database, &checkpoint_id);
        let mut failed = read_state(root)?;
        failed.operation = "FAILED".into();
        failed.phase = if rollback.is_ok() {
            "ROLLED_BACK".into()
        } else {
            "ROLLBACK_FAILED".into()
        };
        let _ = write_state(root, &failed);
        let _ = fs::remove_dir_all(stage);
        return Err(if rollback.is_ok() {
            error
        } else {
            json_error("替换失败且自动回滚未完成；恢复 checkpoint 已保留")
        });
    }
    let _ = fs::remove_dir_all(stage);
    if let Err(state_error) = write_state(
        root,
        &PersistedState {
            operation: "RESTORED_RESTART_REQUIRED".into(),
            phase: "SWITCHED".into(),
            fingerprint: None,
            checkpoint_id: Some(checkpoint_id.clone()),
            process_id: Some(std::process::id()),
            process_instance: Some(current_process_instance()?.to_owned()),
        },
    ) {
        let rollback = restore_checkpoint(root, database, &checkpoint_id);
        let mut failed = read_state(root)?;
        failed.operation = "FAILED".into();
        failed.phase = if rollback.is_ok() {
            "ROLLED_BACK".into()
        } else {
            "ROLLBACK_FAILED".into()
        };
        let _ = write_state(root, &failed);
        return Err(if rollback.is_ok() {
            state_error
        } else {
            json_error("替换完成但重启状态未保存，且自动回滚未完成；恢复 checkpoint 已保留")
        });
    }
    Ok(RestoreReceipt {
        checkpoint_id,
        restart_required: true,
    })
}

pub fn cancel_pending(root: &Path) -> Result<(), String> {
    require_no_pending_rollback(root)?;
    let state = read_state(root)?;
    if state.operation == "RESTORED_RESTART_REQUIRED" {
        return Err(json_error("恢复已经完成，必须先完全重启 App"));
    }
    let _ = fs::remove_file(inbox_path(root));
    if state.phase != "ROLLBACK_FAILED" {
        if let Some(checkpoint_id) = state.checkpoint_id.as_deref() {
            let _ = fs::remove_dir_all(checkpoint_path(root, checkpoint_id));
        }
    }
    write_state(
        root,
        &PersistedState {
            operation: "CANCELLED".into(),
            phase: "NONE".into(),
            fingerprint: None,
            checkpoint_id: if state.phase == "ROLLBACK_FAILED" {
                state.checkpoint_id
            } else {
                None
            },
            process_id: Some(std::process::id()),
            process_instance: Some(current_process_instance()?.to_owned()),
        },
    )
}

// PID is diagnostic metadata: the OS can reuse it after a complete restart.
// Keep one nonce for this process, shared by both areas and every owner call.
fn current_process_instance() -> Result<&'static str, String> {
    static INSTANCE: std::sync::OnceLock<Result<String, String>> = std::sync::OnceLock::new();
    INSTANCE.get_or_init(|| {
        let mut bytes = [0u8; 32];
        getrandom::fill(&mut bytes).map_err(|_| json_error("无法建立本机恢复进程身份"))?;
        Ok(bytes.iter().map(|byte| format!("{byte:02x}")).collect())
    }).as_ref().map(String::as_str).map_err(Clone::clone)
}

fn restored_by_current_process(state: &PersistedState) -> Result<bool, String> {
    // Old releases cannot have written this process's nonce. A legacy PID alone
    // must not prevent cold startup, even when it happens to match the new PID.
    match state.process_instance.as_deref() {
        None => Ok(false),
        Some(instance) if instance.len() == 64 && instance.bytes().all(|byte| byte.is_ascii_hexdigit()) => {
            Ok(instance == current_process_instance()?)
        }
        Some(_) => Err(json_error("本机恢复进程身份损坏")),
    }
}

pub fn requires_restart(root: &Path) -> bool {
    read_state(root)
        .and_then(|state| {
            Ok(state.operation == "RESTORED_RESTART_REQUIRED"
                && restored_by_current_process(&state)?)
        })
        .unwrap_or(true)
}

pub fn pending_fresh_restart(root: &Path) -> Result<bool, String> {
    let state = read_state(root)?;
    Ok(state.operation == "RESTORED_RESTART_REQUIRED"
        && !restored_by_current_process(&state)?)
}

/// Called before opening the ordinary store. A crash or a rollback blocked by an external file
/// handle never continues the candidate restore; both retry the already-fsynced checkpoint.
pub fn recover_interrupted_switch(
    root: &Path,
    database: &Path,
    expected_area: &str,
) -> Result<(), String> {
    let mut state = read_state(root)?;
    let interrupted = state.operation == "INTERRUPTED"
        && matches!(state.phase.as_str(), "CHECKPOINTED" | "SWITCHING");
    let retry_failed_rollback = state.operation == "FAILED" && state.phase == "ROLLBACK_FAILED";
    if !interrupted && !retry_failed_rollback {
        return Ok(());
    }
    let checkpoint_id = state
        .checkpoint_id
        .clone()
        .ok_or_else(|| json_error("中断恢复缺少 checkpoint"))?;
    let checkpoint_snapshot = checkpoint_path(root, &checkpoint_id).join("workspace.snapshot");
    if !checkpoint_snapshot.is_file() || !sqlite_healthy(&checkpoint_snapshot)? {
        return Err(json_error("恢复 checkpoint 不可用"));
    }
    let has_area_evidence = [
        root.join("work-area"),
        root.join("data-area-migration-v1/original-before-install.sqlite3"),
        root.join("data-area-migration-v1/schema45-install.json"),
    ]
    .iter()
    .any(|path| {
        !matches!(
            fs::symlink_metadata(path),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound
        )
    });
    let checkpoint_schema = schema_version(&checkpoint_snapshot)?;
    if checkpoint_schema < 45 {
        if expected_area != "CHAT" || has_area_evidence {
            return Err(json_error("旧单区恢复不能覆盖已拆分或待发布的双区数据"));
        }
    } else if read_schema45_area(&checkpoint_snapshot)? != expected_area {
        return Err(json_error("恢复 checkpoint 与目标 CHAT/WORK 区域不符"));
    }
    restore_checkpoint(root, database, &checkpoint_id)?;
    remove_exact_transient_directories(root, &checkpoint_id)?;
    state.phase = if retry_failed_rollback {
        "ROLLED_BACK".into()
    } else {
        "ROLLED_BACK_INTERRUPTED".into()
    };
    write_state(root, &state)
}

/// Called only after the fresh process has successfully reopened and migrated the restored DB.
pub fn finalize_fresh_restart(root: &Path) -> Result<(), String> {
    let mut state = read_state(root)?;
    if state.operation != "RESTORED_RESTART_REQUIRED"
        || restored_by_current_process(&state)?
    {
        return Ok(());
    }
    if let Some(checkpoint_id) = state.checkpoint_id.take() {
        let _ = fs::remove_dir_all(checkpoint_path(root, &checkpoint_id));
    }
    let _ = fs::remove_file(inbox_path(root));
    state.operation = "COMPLETED".into();
    state.phase = "RESTARTED".into();
    state.process_id = Some(std::process::id());
    state.process_instance = Some(current_process_instance()?.to_owned());
    write_state(root, &state)
}

pub fn read_status(root: &Path, database: &Path) -> Result<BackupStatus, String> {
    let state = read_state(root)?;
    let preflight = if matches!(state.operation.as_str(), "PREFLIGHTED" | "INTERRUPTED")
        && inbox_path(root).is_file()
    {
        verify_package(&inbox_path(root), schema_version(database)?)
            .ok()
            .map(|mut verified| {
                if is_business_non_empty(database, root).unwrap_or(true) {
                    verified
                        .preflight
                        .conflicts
                        .push("本地已有业务数据；只能明确选择替换本地或取消，不支持合并。".into());
                }
                verified.preflight
            })
    } else {
        None
    };
    let notice = match state.operation.as_str() {
        "COMPLETED" => Some("恢复已在完全重启后生效；未自动继续任何任务。".into()),
        "CANCELLED" => Some("已取消；未改动本地数据。".into()),
        "FAILED" if state.phase == "ROLLED_BACK" => {
            Some("上次替换失败，已从恢复 checkpoint 回滚。".into())
        }
        "FAILED" if state.phase == "ROLLBACK_FAILED" => Some(
            "上次自动回滚未完成；请释放占用文件并完全重启 App，恢复将从 checkpoint 重试。".into(),
        ),
        "INTERRUPTED" => Some("上次恢复已中断，未自动继续；可重新确认重试或取消此次恢复。".into()),
        _ => None,
    };
    Ok(BackupStatus {
        operation: state.operation.clone(),
        preflight,
        notice,
        restart_required: state.operation == "RESTORED_RESTART_REQUIRED",
        interrupted: state.operation == "INTERRUPTED",
        rollback_pending: state.operation == "FAILED" && state.phase == "ROLLBACK_FAILED",
    })
}

#[cfg(test)]
mod tests {
    fn knowledge_logical_database(path:&Path)->BTreeMap<String,Vec<Vec<String>>> {
        let c=Connection::open(path).unwrap();table_counts(path).unwrap().keys().map(|table| {
            let mut q=c.prepare(&format!("SELECT * FROM {} ORDER BY rowid",quote_identifier(table))).unwrap();let columns=q.column_count();
            let values=q.query_map([],|r|Ok((0..columns).map(|i|format!("{:?}",r.get_ref(i).unwrap())).collect())).unwrap().map(Result::unwrap).collect();(table.clone(),values)
        }).collect()
    }
    fn knowledge_copies(root:&Path,marker:&str)->(PathBuf,Vec<(String,Vec<u8>)>) {
        fs::create_dir_all(root).unwrap();let db=root.join("workspace.sqlite3");database(&db);
        let c=Connection::open(&db).unwrap();
        crate::desktop_markdown_knowledge_v1::create(&c).unwrap();crate::desktop_json_knowledge_tasks_v1::create(&c).unwrap();crate::desktop_pdf_knowledge_tasks_v1::create(&c).unwrap();crate::desktop_web_knowledge_tasks_v1::create(&c).unwrap();
        let mut copies=Vec::new();
        for (kind,ext) in [("markdown","md"),("json","json"),("pdf","pdf")] {
            let id=format!("{kind}-task-acde");let key=format!("{kind}-knowledge-import/v1/{id}.{ext}");let bytes=format!("synthetic-{marker}-{kind}-private-copy").into_bytes();
            fs::create_dir_all(root.join(&key).parent().unwrap()).unwrap();fs::write(root.join(&key),&bytes).unwrap();
            c.execute(&format!("INSERT INTO desktop_{kind}_knowledge_tasks_v1(id,workspace_id,status,display_name,storage_key,byte_count,source_hash,created_at_ms,updated_at_ms) VALUES(?1,'workspace-one','AWAITING_CONFIRMATION','synthetic',?2,?3,?4,1,1)"),rusqlite::params![id,key,bytes.len() as i64,sha256_bytes(&bytes)]).unwrap();copies.push((key,bytes));
        }
        let key="web-text-snapshots/v1/web-text-task-acde/g1/snapshot.html";let bytes=format!("<!doctype html><p>synthetic {marker} private page</p>").into_bytes();
        fs::create_dir_all(root.join(key).parent().unwrap()).unwrap();fs::write(root.join(key),&bytes).unwrap();
        c.execute("INSERT INTO desktop_web_knowledge_tasks_v1(id,workspace_id,status,requested_url,generation,created_at_ms,updated_at_ms) VALUES('web-text-task-acde','workspace-one','FAILED','https://example.org/',1,1,1)",[]).unwrap();
        c.execute("INSERT INTO desktop_web_knowledge_assets_v1(task_id,generation,storage_key,display_url,host,byte_count,raw_html_hash,adapter_version,extraction_version,candidate_version) VALUES('web-text-task-acde',1,?1,'https://example.org/','example.org',?2,?3,1,1,1)",rusqlite::params![key,bytes.len() as i64,sha256_bytes(&bytes)]).unwrap();copies.push((key.into(),bytes));(db,copies)
    }
    #[test]
    fn call_outcome_business_backup_restores_result_without_mutating_original_facts() {
        use crate::usage_ledger_v1::{Ledger,Entry,Kind,FactGrade,Source};
        use crate::chat_call_accounting_v1::{Metadata,CallOutcome,CallOutcomeState,CallOutcomeOrigin};
        let dir=tempfile::tempdir().unwrap();let source=dir.path().join("source");fs::create_dir_all(&source).unwrap();let db=source.join("workspace.sqlite3");database(&db);
        let ledger=Ledger::open(&source.join("usage-ledger")).unwrap();
        let pending=Entry{entry_id:"pending-child".into(),replay_token:"pending-child".into(),execution_id:"parent".into(),conversation_id:"conversation".into(),branch_leaf_message_id:"assistant".into(),invocation_id:"child".into(),attempt_id:"child".into(),kind:Kind::StreamPending,fact_grade:FactGrade::Estimated,requested_model_id:"openai/gpt-6.1-sol".into(),actual_model_id:None,input_tokens:None,output_tokens:None,cached_input_tokens:None,charge_micros:None,budget_micros:None,adjustment_micros:None,currency_code:None,cost_source:None,reconciliation_fingerprint:Some("a".repeat(64)),reconciles_entry_id:None,source:Source::DesktopLocal,occurred_at_ms:7};
        let metadata=Metadata{role:"RETRIEVAL".into(),model_display_name:"Sol".into(),..Default::default()};ledger.append_call(&pending,&metadata).unwrap();
        let mut measured=pending.clone();measured.entry_id="final-child".into();measured.replay_token=measured.entry_id.clone();measured.kind=Kind::FinalMeasured;measured.fact_grade=FactGrade::ProviderReported;measured.reconciles_entry_id=Some(pending.entry_id.clone());measured.input_tokens=Some(17);measured.output_tokens=Some(5);measured.cached_input_tokens=Some(3);measured.charge_micros=Some(6);measured.currency_code=Some("USD".into());measured.cost_source=Some("PROVIDER_RESPONSE".into());
        ledger.append_recoverable_call(&measured,&metadata).unwrap();let outcome=CallOutcome{state:CallOutcomeState::Completed,safe_error_code:None,model_elapsed_ms:Some(7),origin:CallOutcomeOrigin::LocalExecutionResult};ledger.append_call_outcome(&pending,&outcome).unwrap();drop(ledger);
        let original=sha256_file(&source.join("usage-ledger/usage-ledger-v1.sqlite3")).unwrap();let selected=dir.path().join("outcomes.nfai-backup");export_selected(&source,&db,selected.to_str().unwrap(),"synthetic-outcomes").unwrap();assert_eq!(sha256_file(&source.join("usage-ledger/usage-ledger-v1.sqlite3")).unwrap(),original);
        let target=dir.path().join("target");fs::create_dir_all(&target).unwrap();let destination=target.join("workspace.sqlite3");database(&destination);
        let pre=preflight_selected(&target,&destination,selected.to_str().unwrap()).unwrap();restore_preflighted(&target,&destination,&pre.fingerprint,true).unwrap();
        let restored=Ledger::open(&target.join("usage-ledger")).unwrap();assert_eq!(restored.entry(&measured.entry_id).unwrap(),Some(measured.clone()));assert_eq!(restored.call_metadata(&measured.entry_id).unwrap(),Some(metadata));assert_eq!(restored.call_outcome(&measured).unwrap(),Some(outcome));assert_eq!(sha256_file(&source.join("usage-ledger/usage-ledger-v1.sqlite3")).unwrap(),original);
    }

    fn backup_metadata_fixture(root:&Path)->(PathBuf,PathBuf) {
        use crate::usage_ledger_v1::{Ledger,Entry,Kind,FactGrade,Source};
        use crate::chat_call_accounting_v1::{Metadata,CallOutcome,CallOutcomeState,CallOutcomeOrigin};
        let source=root.join("source");fs::create_dir_all(&source).unwrap();let db=source.join("workspace.sqlite3");database(&db);
        let ledger=Ledger::open(&source.join("usage-ledger")).unwrap();
        let pending=Entry{entry_id:"pending-child".into(),replay_token:"pending-child".into(),execution_id:"parent".into(),conversation_id:"conversation".into(),branch_leaf_message_id:"assistant".into(),invocation_id:"child".into(),attempt_id:"child".into(),kind:Kind::StreamPending,fact_grade:FactGrade::Estimated,requested_model_id:"openai/gpt-6.1-sol".into(),actual_model_id:None,input_tokens:None,output_tokens:None,cached_input_tokens:None,charge_micros:None,budget_micros:None,adjustment_micros:None,currency_code:None,cost_source:None,reconciliation_fingerprint:Some("a".repeat(64)),reconciles_entry_id:None,source:Source::DesktopLocal,occurred_at_ms:7};
        let metadata=Metadata{role:"RETRIEVAL".into(),model_display_name:"Sol · Pro3".into(),route:Some(crate::chat_call_accounting_v1::ModelCallRoute::capture("RELAYCORE_GPT","Sol · Pro3").unwrap()),..Default::default()};ledger.append_call(&pending,&metadata).unwrap();
        let mut measured=pending.clone();measured.entry_id="final-child".into();measured.replay_token=measured.entry_id.clone();measured.kind=Kind::FinalMeasured;measured.fact_grade=FactGrade::ProviderReported;measured.reconciles_entry_id=Some(pending.entry_id.clone());measured.input_tokens=Some(17);measured.output_tokens=Some(5);measured.cached_input_tokens=Some(3);measured.charge_micros=Some(6);measured.currency_code=Some("USD".into());measured.cost_source=Some("PROVIDER_RESPONSE".into());
        ledger.append_recoverable_call(&measured,&metadata).unwrap();let outcome=CallOutcome{state:CallOutcomeState::Completed,safe_error_code:None,model_elapsed_ms:Some(7),origin:CallOutcomeOrigin::LocalExecutionResult};ledger.append_call_outcome(&pending,&outcome).unwrap();drop(ledger);
        (db,source.join("usage-ledger/usage-ledger-v1.sqlite3"))
    }
    fn corrupt_settled_backup_route(path:&Path) {
        let c=Connection::open(path).unwrap();let raw:String=c.query_row("SELECT metadata_json FROM usage_ledger_call_metadata WHERE entry_id='final-child'",[],|r|r.get(0)).unwrap();let mut value:serde_json::Value=serde_json::from_str(&raw).unwrap();value["route"]["channelId"]=serde_json::json!("PRO2");c.execute("UPDATE usage_ledger_call_metadata SET metadata_json=?1 WHERE entry_id='final-child'",[value.to_string()]).unwrap();
    }
    #[test]
    fn backup_call_metadata_export_rejects_changed_route_and_keeps_selected_bytes() {
        let dir=tempfile::tempdir().unwrap();let(db,path)=backup_metadata_fixture(dir.path());corrupt_settled_backup_route(&path);let source=db.parent().unwrap();let before=fs::read(&path).unwrap();let selected=dir.path().join("retain.nfai-backup");fs::write(&selected,b"previous selected artifact").unwrap();
        assert!(export_selected(source,&db,selected.to_str().unwrap(),"synthetic metadata validation").is_err());assert_eq!(fs::read(&selected).unwrap(),b"previous selected artifact");assert_eq!(fs::read(&path).unwrap(),before);
    }
    #[test]
    fn backup_call_metadata_preflight_rejects_valid_hash_archive_with_changed_route_and_preserves_live_data() {
        let dir=tempfile::tempdir().unwrap();let(db,path)=backup_metadata_fixture(dir.path());corrupt_settled_backup_route(&path);let selected=dir.path().join("old-corrupt.nfai-backup");
        // Historical/corrupt archive fixture: CRC, manifest and entry hashes are all valid.
        create_package(db.parent().unwrap(),&db,&selected,"synthetic",vec![(USAGE_LEDGER_ENTRY.into(),path)],&BTreeSet::new()).unwrap();assert!(verify_package(&selected,schema_version(&db).unwrap()).is_ok());
        let destination=dir.path().join("current");fs::create_dir_all(&destination).unwrap();let current=destination.join("workspace.sqlite3");database(&current);let before=fs::read(&current).unwrap();let selected_before=fs::read(&selected).unwrap();
        assert!(preflight_selected(&destination,&current,selected.to_str().unwrap()).is_err());assert_eq!(fs::read(&current).unwrap(),before);assert_eq!(fs::read(&selected).unwrap(),selected_before);
    }
    #[test]
    fn backup_call_metadata_valid_legacy_two_five_and_current_six_restore_exact_metadata() {
        for version in [2,5,6] {
            let dir=tempfile::tempdir().unwrap();let(db,path)=backup_metadata_fixture(dir.path());
            if version<6 {
                let c=Connection::open(&path).unwrap();let rows:Vec<(String,String)>=c.prepare("SELECT entry_id,metadata_json FROM usage_ledger_call_metadata").unwrap().query_map([],|r|Ok((r.get(0)?,r.get(1)?))).unwrap().map(Result::unwrap).collect();
                for(id,raw)in rows {let mut value:serde_json::Value=serde_json::from_str(&raw).unwrap();value.as_object_mut().unwrap().remove("route");c.execute("UPDATE usage_ledger_call_metadata SET metadata_json=?1 WHERE entry_id=?2",rusqlite::params![value.to_string(),id]).unwrap();}
                if version==2 {c.execute_batch("DROP TABLE usage_ledger_call_outcomes;DROP TABLE usage_ledger_retrieval_progress;DROP TABLE usage_ledger_call_recovery;UPDATE usage_ledger_entries SET cost_source=NULL;").unwrap();}c.pragma_update(None,"user_version",version).unwrap();
            }
            let c=Connection::open(&path).unwrap();let original:Vec<(String,String)>=c.prepare("SELECT entry_id,metadata_json FROM usage_ledger_call_metadata ORDER BY entry_id").unwrap().query_map([],|r|Ok((r.get(0)?,r.get(1)?))).unwrap().map(Result::unwrap).collect();drop(c);let before=fs::read(&path).unwrap();
            let selected=dir.path().join("valid.nfai-backup");export_selected(db.parent().unwrap(),&db,selected.to_str().unwrap(),"synthetic").unwrap();assert_eq!(fs::read(&path).unwrap(),before);
            let target=dir.path().join("target");fs::create_dir_all(&target).unwrap();let current=target.join("workspace.sqlite3");database(&current);let pre=preflight_selected(&target,&current,selected.to_str().unwrap()).unwrap();restore_preflighted(&target,&current,&pre.fingerprint,true).unwrap();
            let ledger=crate::usage_ledger_v1::Ledger::open(&target.join("usage-ledger")).unwrap();let measured=ledger.entry("final-child").unwrap().unwrap();assert_eq!(measured.charge_micros,Some(6));assert_eq!(measured.cost_source.is_none(),version==2);for(id,_)in &original {let metadata=ledger.call_metadata(id).unwrap().unwrap();assert_eq!(metadata.route.is_some(),version==6);}drop(ledger);
            let c=Connection::open(target.join("usage-ledger/usage-ledger-v1.sqlite3")).unwrap();let restored:Vec<(String,String)>=c.prepare("SELECT entry_id,metadata_json FROM usage_ledger_call_metadata ORDER BY entry_id").unwrap().query_map([],|r|Ok((r.get(0)?,r.get(1)?))).unwrap().map(Result::unwrap).collect();assert_eq!(restored,original);assert_eq!(fs::read(&path).unwrap(),before);
        }
    }

    #[test]
    fn call_route_business_backup_restores_original_channel_without_source_rewrite() {
        use crate::usage_ledger_v1::{Ledger,Entry,Kind,FactGrade,Source};
        use crate::chat_call_accounting_v1::{Metadata,CallOutcome,CallOutcomeState,CallOutcomeOrigin};
        let dir=tempfile::tempdir().unwrap();let source=dir.path().join("source");fs::create_dir_all(&source).unwrap();let db=source.join("workspace.sqlite3");database(&db);
        let ledger=Ledger::open(&source.join("usage-ledger")).unwrap();
        let pending=Entry{entry_id:"pending-child".into(),replay_token:"pending-child".into(),execution_id:"parent".into(),conversation_id:"conversation".into(),branch_leaf_message_id:"assistant".into(),invocation_id:"child".into(),attempt_id:"child".into(),kind:Kind::StreamPending,fact_grade:FactGrade::Estimated,requested_model_id:"openai/gpt-6.1-sol".into(),actual_model_id:None,input_tokens:None,output_tokens:None,cached_input_tokens:None,charge_micros:None,budget_micros:None,adjustment_micros:None,currency_code:None,cost_source:None,reconciliation_fingerprint:Some("a".repeat(64)),reconciles_entry_id:None,source:Source::DesktopLocal,occurred_at_ms:7};
        let metadata=Metadata{role:"RETRIEVAL".into(),model_display_name:"Sol · Pro3".into(),route:Some(crate::chat_call_accounting_v1::ModelCallRoute::capture("RELAYCORE_GPT","Sol · Pro3").unwrap()),..Default::default()};ledger.append_call(&pending,&metadata).unwrap();
        let mut measured=pending.clone();measured.entry_id="final-child".into();measured.replay_token=measured.entry_id.clone();measured.kind=Kind::FinalMeasured;measured.fact_grade=FactGrade::ProviderReported;measured.reconciles_entry_id=Some(pending.entry_id.clone());measured.input_tokens=Some(17);measured.output_tokens=Some(5);measured.cached_input_tokens=Some(3);measured.charge_micros=Some(6);measured.currency_code=Some("USD".into());measured.cost_source=Some("PROVIDER_RESPONSE".into());
        ledger.append_recoverable_call(&measured,&metadata).unwrap();let outcome=CallOutcome{state:CallOutcomeState::Completed,safe_error_code:None,model_elapsed_ms:Some(7),origin:CallOutcomeOrigin::LocalExecutionResult};ledger.append_call_outcome(&pending,&outcome).unwrap();drop(ledger);
        let original=sha256_file(&source.join("usage-ledger/usage-ledger-v1.sqlite3")).unwrap();let selected=dir.path().join("outcomes.nfai-backup");export_selected(&source,&db,selected.to_str().unwrap(),"synthetic-outcomes").unwrap();assert_eq!(sha256_file(&source.join("usage-ledger/usage-ledger-v1.sqlite3")).unwrap(),original);
        let target=dir.path().join("target");fs::create_dir_all(&target).unwrap();let destination=target.join("workspace.sqlite3");database(&destination);
        let pre=preflight_selected(&target,&destination,selected.to_str().unwrap()).unwrap();restore_preflighted(&target,&destination,&pre.fingerprint,true).unwrap();
        let restored=Ledger::open(&target.join("usage-ledger")).unwrap();assert_eq!(restored.entry(&measured.entry_id).unwrap(),Some(measured.clone()));assert_eq!(restored.call_metadata(&measured.entry_id).unwrap(),Some(metadata));assert_eq!(restored.call_outcome(&measured).unwrap(),Some(outcome));assert_eq!(sha256_file(&source.join("usage-ledger/usage-ledger-v1.sqlite3")).unwrap(),original);
    }

    #[test]
    fn material_read_business_backup_restores_result_without_mutating_original_facts() {
        use crate::usage_ledger_v1::{Ledger,Entry,Kind,FactGrade,Source};
        use crate::chat_call_accounting_v1::{Metadata,CallOutcome,CallOutcomeState,CallOutcomeOrigin};
        let dir=tempfile::tempdir().unwrap();let source=dir.path().join("source");fs::create_dir_all(&source).unwrap();let db=source.join("workspace.sqlite3");database(&db);
        let ledger=Ledger::open(&source.join("usage-ledger")).unwrap();
        let pending=Entry{entry_id:"pending-child".into(),replay_token:"pending-child".into(),execution_id:"parent".into(),conversation_id:"conversation".into(),branch_leaf_message_id:"assistant".into(),invocation_id:"child".into(),attempt_id:"child".into(),kind:Kind::StreamPending,fact_grade:FactGrade::Estimated,requested_model_id:"openai/gpt-6.1-sol".into(),actual_model_id:None,input_tokens:None,output_tokens:None,cached_input_tokens:None,charge_micros:None,budget_micros:None,adjustment_micros:None,currency_code:None,cost_source:None,reconciliation_fingerprint:Some("a".repeat(64)),reconciles_entry_id:None,source:Source::DesktopLocal,occurred_at_ms:7};
        let metadata=Metadata{role:"MATERIAL_READ".into(),model_display_name:"Sol".into(),..Default::default()};ledger.append_call(&pending,&metadata).unwrap();
        let mut measured=pending.clone();measured.entry_id="final-child".into();measured.replay_token=measured.entry_id.clone();measured.kind=Kind::FinalMeasured;measured.fact_grade=FactGrade::ProviderReported;measured.reconciles_entry_id=Some(pending.entry_id.clone());measured.input_tokens=Some(17);measured.output_tokens=Some(5);measured.cached_input_tokens=Some(3);measured.charge_micros=Some(6);measured.currency_code=Some("USD".into());measured.cost_source=Some("PROVIDER_RESPONSE".into());
        ledger.append_recoverable_call(&measured,&metadata).unwrap();let outcome=CallOutcome{state:CallOutcomeState::Completed,safe_error_code:None,model_elapsed_ms:Some(7),origin:CallOutcomeOrigin::LocalExecutionResult};ledger.append_call_outcome(&pending,&outcome).unwrap();drop(ledger);
        let original=sha256_file(&source.join("usage-ledger/usage-ledger-v1.sqlite3")).unwrap();let selected=dir.path().join("outcomes.nfai-backup");export_selected(&source,&db,selected.to_str().unwrap(),"synthetic-outcomes").unwrap();assert_eq!(sha256_file(&source.join("usage-ledger/usage-ledger-v1.sqlite3")).unwrap(),original);
        let target=dir.path().join("target");fs::create_dir_all(&target).unwrap();let destination=target.join("workspace.sqlite3");database(&destination);
        let pre=preflight_selected(&target,&destination,selected.to_str().unwrap()).unwrap();restore_preflighted(&target,&destination,&pre.fingerprint,true).unwrap();
        let restored=Ledger::open(&target.join("usage-ledger")).unwrap();assert_eq!(restored.entry(&measured.entry_id).unwrap(),Some(measured.clone()));assert_eq!(restored.call_metadata(&measured.entry_id).unwrap(),Some(metadata));assert_eq!(restored.call_outcome(&measured).unwrap(),Some(outcome));assert_eq!(sha256_file(&source.join("usage-ledger/usage-ledger-v1.sqlite3")).unwrap(),original);
    }

    #[test]
    fn knowledge_private_copies_archive_restore_and_failure_rollback_preserve_all_four_roots() {
        let dir=tempfile::tempdir().unwrap();let source=dir.path().join("source");let (db,copies)=knowledge_copies(&source,"original");let selected=dir.path().join("knowledge.nfai-backup");
        export_selected(&source,&db,selected.to_str().unwrap(),"synthetic").unwrap();
        let zip=ZipArchive::new(fs::File::open(&selected).unwrap()).unwrap();let names:BTreeSet<_>=zip.file_names().map(str::to_owned).collect();for(key,_) in &copies {assert!(names.contains(&format!("assets/{key}")));}
        let destination=dir.path().join("destination");let (dest,_) = knowledge_copies(&destination,"current");
        let pre=preflight_selected(&destination,&dest,selected.to_str().unwrap()).unwrap();restore_preflighted_with_hook(&destination,&dest,&pre.fingerprint,true,||Ok(())).unwrap();
        for(key,bytes) in &copies {assert_eq!(fs::read(destination.join(key)).unwrap(),*bytes);}
        let c=Connection::open(&dest).unwrap();assert_eq!(c.query_row("SELECT raw_html_hash FROM desktop_web_knowledge_assets_v1",[],|r|r.get::<_,String>(0)).unwrap(),sha256_bytes(&copies[3].1));
        let rollback=dir.path().join("rollback");let (active,current)=knowledge_copies(&rollback,"must-survive");let before=knowledge_logical_database(&active);
        let pre=preflight_selected(&rollback,&active,selected.to_str().unwrap()).unwrap();let err=restore_preflighted_with_hook(&rollback,&active,&pre.fingerprint,true,||Err("synthetic post-switch failure".into())).unwrap_err();assert!(err.contains("synthetic post-switch failure"));
        for(key,bytes) in &current {assert_eq!(fs::read(rollback.join(key)).unwrap(),*bytes);}
        assert_eq!(knowledge_logical_database(&active),before);
    }
    #[test]
    fn knowledge_missing_changed_and_escaped_receipts_refuse_export_without_overwriting_selection() {
        let dir=tempfile::tempdir().unwrap();let (db,copies)=knowledge_copies(dir.path(),"original");let selected=dir.path().join("keep.nfai-backup");fs::write(&selected,b"retain selected bytes").unwrap();
        for(kind,key,bytes) in [("markdown",&copies[0].0,&copies[0].1),("json",&copies[1].0,&copies[1].1),("pdf",&copies[2].0,&copies[2].1),("web",&copies[3].0,&copies[3].1)] {
            fs::remove_file(dir.path().join(key)).unwrap();assert!(export_selected(dir.path(),&db,selected.to_str().unwrap(),"synthetic").is_err(),"{kind}");
            fs::write(dir.path().join(key),b"changed bytes").unwrap();assert!(export_selected(dir.path(),&db,selected.to_str().unwrap(),"synthetic").is_err(),"{kind}");fs::write(dir.path().join(key),bytes).unwrap();assert_eq!(fs::read(&selected).unwrap(),b"retain selected bytes");
        }
        let c=Connection::open(&db).unwrap();c.execute("UPDATE desktop_web_knowledge_assets_v1 SET storage_key='../outside.html'",[]).unwrap();assert!(export_selected(dir.path(),&db,selected.to_str().unwrap(),"synthetic").is_err());assert_eq!(fs::read(&selected).unwrap(),b"retain selected bytes");
    }
    #[test]
    fn old_archive_with_dangling_knowledge_copy_refuses_preflight_before_live_changes() {
        let dir=tempfile::tempdir().unwrap();let source=dir.path().join("source");let (db,copies)=knowledge_copies(&source,"original");let good=dir.path().join("good.nfai-backup");export_selected(&source,&db,good.to_str().unwrap(),"synthetic").unwrap();
        let mut archive=ZipArchive::new(fs::File::open(&good).unwrap()).unwrap();let mut bytes=BTreeMap::new();for i in 0..archive.len(){let mut e=archive.by_index(i).unwrap();let mut data=Vec::new();e.read_to_end(&mut data).unwrap();bytes.insert(e.name().to_owned(),data);}
        let missing=format!("assets/{}",copies[3].0);bytes.remove(&missing);let mut manifest:BackupManifest=serde_json::from_slice(&bytes[MANIFEST_ENTRY]).unwrap();manifest.entries.retain(|e|e.path!=missing);manifest.manifest_sha256=sha256_bytes(&manifest_without_hash(&manifest).unwrap());bytes.insert(MANIFEST_ENTRY.into(),serde_json::to_vec(&manifest).unwrap());
        let bad=dir.path().join("old-missing.nfai-backup");let mut zip=ZipWriter::new(fs::File::create(&bad).unwrap());for(name,data) in bytes {zip.start_file(name,SimpleFileOptions::default().compression_method(CompressionMethod::Deflated)).unwrap();zip.write_all(&data).unwrap();}zip.finish().unwrap();
        // Internal archive facts are self-consistent; missing source receipt is the rejected boundary.
        assert!(verify_package(&bad,schema_version(&db).unwrap()).is_ok());let destination=dir.path().join("destination");let(active,current)=knowledge_copies(&destination,"current");let before=fs::read(&active).unwrap();
        assert!(preflight_selected(&destination,&active,bad.to_str().unwrap()).is_err());assert_eq!(fs::read(&active).unwrap(),before);for(key,bytes) in current{assert_eq!(fs::read(destination.join(key)).unwrap(),bytes);}
    }

    #[test]
    fn process_instance_restart_gate_preserves_same_process_and_accepts_reused_pid() {
        let dir = tempfile::tempdir().unwrap();
        fs::create_dir_all(checkpoint_path(dir.path(), "gate-checkpoint")).unwrap();
        let mut state = PersistedState {
            operation: "RESTORED_RESTART_REQUIRED".into(),
            phase: "SWITCHED".into(),
            checkpoint_id: Some("gate-checkpoint".into()),
            process_id: Some(std::process::id()),
            process_instance: Some(current_process_instance().unwrap().to_owned()),
            ..PersistedState::default()
        };
        write_state(dir.path(), &state).unwrap();
        assert!(requires_restart(dir.path()));
        assert!(!pending_fresh_restart(dir.path()).unwrap());
        finalize_fresh_restart(dir.path()).unwrap();
        assert_eq!(read_state(dir.path()).unwrap().operation, "RESTORED_RESTART_REQUIRED");
        // Precise owner-level simulation; no OS PID reuse or native claim.
        state.process_instance = Some(if current_process_instance().unwrap() == "0".repeat(64) {
            "1".repeat(64)
        } else { "0".repeat(64) });
        write_state(dir.path(), &state).unwrap();
        assert!(!requires_restart(dir.path()));
        assert!(pending_fresh_restart(dir.path()).unwrap());
        finalize_fresh_restart(dir.path()).unwrap();
        let final_state = read_state(dir.path()).unwrap();
        assert_eq!(final_state.operation, "COMPLETED");
        assert_eq!(final_state.process_instance.as_deref(), Some(current_process_instance().unwrap()));
    }

    #[test]
    fn malformed_process_instance_and_state_fail_closed() {
        let dir = tempfile::tempdir().unwrap();
        fs::create_dir_all(checkpoint_path(dir.path(), "gate-checkpoint")).unwrap();
        let state = PersistedState {
            operation: "RESTORED_RESTART_REQUIRED".into(),
            phase: "SWITCHED".into(),
            checkpoint_id: Some("gate-checkpoint".into()),
            process_instance: Some("invalid".into()),
            ..PersistedState::default()
        };
        write_state(dir.path(), &state).unwrap();
        assert!(requires_restart(dir.path()));
        assert!(pending_fresh_restart(dir.path()).is_err());
        assert!(finalize_fresh_restart(dir.path()).is_err());
        fs::write(state_path(dir.path()), b"{").unwrap();
        assert!(requires_restart(dir.path()));
        assert!(pending_fresh_restart(dir.path()).is_err());
        assert!(finalize_fresh_restart(dir.path()).is_err());
    }

    #[test]
    fn legacy_restart_state_with_reused_pid_is_a_fresh_process() {
        let dir = tempfile::tempdir().unwrap();
        fs::create_dir_all(checkpoint_path(dir.path(), "legacy-checkpoint")).unwrap();
        fs::write(state_path(dir.path()), format!(r#"{{"operation":"RESTORED_RESTART_REQUIRED","phase":"SWITCHED","fingerprint":null,"checkpointId":"legacy-checkpoint","processId":{}}}"#, std::process::id())).unwrap();
        assert_eq!(read_state(dir.path()).unwrap().process_id, Some(std::process::id()));
        assert!(!requires_restart(dir.path()));
        assert!(pending_fresh_restart(dir.path()).unwrap());
        finalize_fresh_restart(dir.path()).unwrap();
        assert_eq!(read_state(dir.path()).unwrap().operation, "COMPLETED");
    }

    #[test]
    fn task_identifiers_do_not_block_nonempty_backup_snapshot() {
        let dir = tempfile::tempdir().unwrap();
        let source = dir.path().join("source.sqlite3");
        let snapshot = dir.path().join("snapshot.sqlite3");
        let connection = Connection::open(&source).unwrap();
        connection.execute_batch("CREATE TABLE chatgpt_import_tasks(id TEXT PRIMARY KEY,status TEXT NOT NULL,storage_key TEXT NOT NULL,failure TEXT); CREATE TABLE import_receipts(task_id TEXT NOT NULL,conversation_id TEXT NOT NULL);").unwrap();
        let id = "chatgpt-task-1790861452000-12345";
        connection.execute("INSERT INTO chatgpt_import_tasks VALUES(?1,'AWAITING_CONFIRMATION',?2,NULL)", rusqlite::params![id,format!("packages/{id}.json")]).unwrap();
        connection.execute("INSERT INTO import_receipts VALUES(?1,'w15-task-chat-synthetic-conversation')",[id]).unwrap();
        drop(connection);
        snapshot_database(&source,&snapshot).unwrap();
        sanitize_snapshot(&snapshot).unwrap();
        assert!(!contains_sensitive_values(&snapshot).unwrap());
        let restored=Connection::open(&snapshot).unwrap();
        assert_eq!(restored.query_row("SELECT id,status,storage_key,failure FROM chatgpt_import_tasks",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,String>(1)?,r.get::<_,String>(2)?,r.get::<_,String>(3)?))).unwrap(),(id.into(),"FAILED".into(),format!("packages/{id}.json"),"INTERRUPTED".into()));
        assert_eq!(restored.query_row("SELECT COUNT(*) FROM import_receipts",[],|r|r.get::<_,i64>(0)).unwrap(),1);
        let original=Connection::open(&source).unwrap();
        assert_eq!(original.query_row("SELECT status FROM chatgpt_import_tasks",[],|r|r.get::<_,String>(0)).unwrap(),"AWAITING_CONFIRMATION");
    }

    #[test]
    fn backup_sensitive_scan_requires_sk_token_start_and_still_rejects_keys() {
        for value in ["chatgpt-task-1790861452000-12345", "packages/claude-task-1790861452000-12345.json", "{\"id\":\"w15-task-chat-synthetic\"}", "ask-about-synthetic-nonsecret-task-name"] {
            assert!(!looks_sensitive(value),"ordinary task identity rejected: {value}");
        }
        for value in ["sk-synthetic-credential-123456789", "{\"key\":\"sk-synthetic-credential-123456789\"}", "key=SK-synthetic-credential-123456789", "中文sk-synthetic-credential-123456789", "Authorization: Bearer synthetic", "api_key=synthetic", "refresh_token=synthetic", "-----BEGIN PRIVATE KEY-----synthetic"] {
            assert!(looks_sensitive(value),"credential marker missed: {value}");
        }
    }

    #[test]
    fn backup_snapshot_closes_only_running_ocr_attempts_without_rewriting_history() {
        let dir = tempfile::tempdir().unwrap();
        let source = dir.path().join("source.sqlite3");
        let snapshot = dir.path().join("snapshot.sqlite3");
        let connection = rusqlite::Connection::open(&source).unwrap();
        crate::desktop_ocr_attempt_schema::create(&connection).unwrap();
        for (number,state) in [(1,"RUNNING"),(2,"COMPLETED"),(3,"CANCELLED"),(4,"FAILED")] {
            connection.execute("INSERT INTO desktop_ocr_attempts_v1(attempt_id,task_id,attempt_number,provider_id,requested_model_id,source_display_name,created_at_ms,terminal_at_ms,state,safe_error_code,provider_request_id,page_count,input_tokens,output_tokens,charge_micros) VALUES(?1,'task',?2,'ZHIPU','glm-ocr','original.png',100,?3,?4,?5,'request',1,12,8,4)",rusqlite::params![format!("attempt-{number}"),number,if number==1 {None} else {Some(200_i64)},state,if number==1 || number==2 {None} else {Some(state)}]).unwrap();
        }
        drop(connection);
        super::snapshot_database(&source,&snapshot).unwrap();
        super::sanitize_snapshot(&snapshot).unwrap();
        super::sanitize_snapshot(&snapshot).unwrap();
        let restored = rusqlite::Connection::open(&snapshot).unwrap();
        let original = rusqlite::Connection::open(&source).unwrap();
        assert_eq!(original.query_row("SELECT state FROM desktop_ocr_attempts_v1 WHERE attempt_number=1",[],|r|r.get::<_,String>(0)).unwrap(),"RUNNING");
        assert_eq!(restored.query_row("SELECT state,safe_error_code,terminal_at_ms FROM desktop_ocr_attempts_v1 WHERE attempt_number=1",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,String>(1)?,r.get::<_,i64>(2)?))).unwrap(),("UNKNOWN".into(),"BACKUP_RESTORED".into(),0));
        for number in 2..=4 {
            let sql = "SELECT state,safe_error_code,terminal_at_ms,source_display_name,provider_request_id,page_count,input_tokens,output_tokens,charge_micros FROM desktop_ocr_attempts_v1 WHERE attempt_number=?1";
            let read = |r: &rusqlite::Row<'_>| Ok((r.get::<_,String>(0)?,r.get::<_,Option<String>>(1)?,r.get::<_,i64>(2)?,r.get::<_,String>(3)?,r.get::<_,String>(4)?,r.get::<_,i64>(5)?,r.get::<_,i64>(6)?,r.get::<_,i64>(7)?,r.get::<_,i64>(8)?));
            assert_eq!(restored.query_row(sql,[number],read).unwrap(),original.query_row(sql,[number],read).unwrap());
        }
        assert_eq!(restored.query_row("PRAGMA quick_check",[],|r|r.get::<_,String>(0)).unwrap(),"ok");
    }

    use super::*;
    use tempfile::tempdir;

    #[test]
    fn linked_asset_root_cannot_enter_backup_package() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-backup-root");
        let outside = temp.path().join("outside-private-files");
        fs::create_dir(&root).unwrap();
        fs::create_dir(&outside).unwrap();
        fs::write(outside.join("outside.txt"), b"outside fixture").unwrap();
        #[cfg(windows)]
        {
            let quoted = |path: &Path| path.to_string_lossy().replace('\'', "''");
            let command = format!(
                "New-Item -ItemType Junction -Path '{}' -Target '{}' -ErrorAction Stop | Out-Null",
                quoted(&root.join("assets")),
                quoted(&outside)
            );
            let output = std::process::Command::new("powershell")
                .args(["-NoProfile", "-NonInteractive", "-Command", &command])
                .output()
                .unwrap();
            assert!(output.status.success(), "cannot create Windows junction fixture");
        }
        #[cfg(unix)]
        std::os::unix::fs::symlink(&outside, root.join("assets")).unwrap();
        assert!(collect_controlled_files(&root).is_err());
        assert_eq!(fs::read(outside.join("outside.txt")).unwrap(), b"outside fixture");
    }

    fn database(path: &Path) {
        let connection = Connection::open(path).unwrap();
        connection
            .execute_batch(
                "CREATE TABLE workspaces(id TEXT PRIMARY KEY,title TEXT NOT NULL);\
                 CREATE TABLE desktop_provider_settings(provider_id TEXT PRIMARY KEY,enabled INTEGER NOT NULL,preset_id TEXT NOT NULL,revision INTEGER NOT NULL,updated_at_ms INTEGER NOT NULL);\
                 CREATE TABLE desktop_local_search_index(entry_id TEXT PRIMARY KEY,normalized_text TEXT NOT NULL);\
                 CREATE TABLE desktop_portable_personalization_v1(id INTEGER PRIMARY KEY,interests TEXT NOT NULL,revision INTEGER NOT NULL,updated_at_ms INTEGER NOT NULL);\
                 CREATE TABLE desktop_history_knowledge_candidates_v1(candidate_id TEXT PRIMARY KEY,status TEXT NOT NULL,safe_error_code TEXT,updated_at_ms INTEGER NOT NULL);\
                 CREATE TABLE desktop_history_knowledge_checkpoints_v1(checkpoint_id TEXT PRIMARY KEY,status TEXT NOT NULL,updated_at_ms INTEGER NOT NULL);\
                 CREATE TABLE desktop_reminder_drafts_v1(draft_id TEXT PRIMARY KEY,status TEXT NOT NULL,safe_error_code TEXT,updated_at_ms INTEGER NOT NULL);\
                 CREATE TABLE desktop_reminder_plans_v1(plan_id TEXT PRIMARY KEY,status TEXT NOT NULL,next_run_at_ms INTEGER,last_safe_error_code TEXT,updated_at_ms INTEGER NOT NULL);\
                 CREATE TABLE desktop_reminder_runs_v1(run_id TEXT PRIMARY KEY,status TEXT NOT NULL,completed_at_ms INTEGER,safe_error_code TEXT,notification_state TEXT NOT NULL,notification_safe_code TEXT);\
                 CREATE TABLE desktop_conversation_read_markers_v1(workspace_id TEXT NOT NULL,conversation_id TEXT NOT NULL,last_read_at_ms INTEGER NOT NULL,latest_completed_at_ms INTEGER NOT NULL,latest_completed_message_id TEXT,manual_unread_at_ms INTEGER,PRIMARY KEY(workspace_id,conversation_id));\
                 CREATE TABLE chatgpt_import_tasks(id TEXT PRIMARY KEY,status TEXT NOT NULL,failure TEXT);\
                 PRAGMA user_version=23;\
                 INSERT INTO workspaces VALUES('workspace-one','备份前');\
                 INSERT INTO desktop_provider_settings VALUES('QWEN',1,'QWEN_3_7_PLUS',1,1);\
                 INSERT INTO desktop_local_search_index VALUES('derived','不进入备份');\
                 INSERT INTO desktop_portable_personalization_v1 VALUES(1,'备份前的关注方向',1,1);\
                 INSERT INTO desktop_history_knowledge_candidates_v1 VALUES('candidate-running','RUNNING',NULL,1);\
                 INSERT INTO desktop_history_knowledge_checkpoints_v1 VALUES('checkpoint-running','RUNNING',1);\
                 INSERT INTO desktop_reminder_drafts_v1 VALUES('draft-running','RUNNING',NULL,1);\
                 INSERT INTO desktop_reminder_plans_v1 VALUES('plan-running','RUNNING',1,NULL,1);\
                 INSERT INTO desktop_reminder_runs_v1 VALUES('run-running','RUNNING',NULL,NULL,'PENDING',NULL);\
                 INSERT INTO desktop_conversation_read_markers_v1 VALUES('workspace-one','conversation-one',10,20,'assistant-one',111);\
                 INSERT INTO chatgpt_import_tasks VALUES('task-running','RUNNING',NULL);",
            )
            .unwrap();
    }


    fn temporary_backup_fixture(root: &Path) -> (PathBuf, String, String) {
        fs::create_dir_all(root.join("assets")).unwrap();
        let path = root.join("workspace.sqlite3");
        database(&path);
        let private = sha256_bytes(b"synthetic temporary-only attachment");
        let shared = sha256_bytes(b"synthetic ordinary shared attachment");
        let connection = Connection::open(&path).unwrap();
        connection.execute_batch("CREATE TABLE desktop_temporary_recovery(temporary_id TEXT PRIMARY KEY,recovery_json TEXT NOT NULL,updated_at_ms INTEGER NOT NULL); CREATE TABLE desktop_temporary_attachments(temporary_id TEXT NOT NULL REFERENCES desktop_temporary_recovery(temporary_id),attachment_id TEXT NOT NULL,display_name TEXT NOT NULL,sha256 TEXT NOT NULL); CREATE TABLE desktop_attachment_assets(sha256 TEXT PRIMARY KEY,reference_count INTEGER NOT NULL); CREATE TABLE desktop_conversation_attachments(attachment_id TEXT PRIMARY KEY,sha256 TEXT NOT NULL); INSERT INTO desktop_temporary_recovery VALUES('temporary-synthetic','synthetic private draft and message',123);").unwrap();
        for (hash, name, count) in [(&private,"private.txt",0),(&shared,"shared.txt",1)] {
            connection.execute("INSERT INTO desktop_temporary_attachments VALUES('temporary-synthetic',?1,?1,?2)",rusqlite::params![name,hash]).unwrap();
            connection.execute("INSERT INTO desktop_attachment_assets VALUES(?1,?2)",rusqlite::params![hash,count]).unwrap();
        }
        connection.execute("INSERT INTO desktop_conversation_attachments VALUES('ordinary-shared',?1)",[&shared]).unwrap();
        fs::write(root.join("assets").join(&private),b"synthetic temporary-only attachment").unwrap();
        fs::write(root.join("assets").join(&shared),b"synthetic ordinary shared attachment").unwrap();
        (path,private,shared)
    }

    #[test]
    fn local_continuity_diagnostic_never_enters_business_backup() {
        let dir=tempdir().unwrap();let root=dir.path().join("source");let (database,_,_)=temporary_backup_fixture(&root);
        let diagnostic=root.join("generation-continuity-v1.json");fs::write(&diagnostic,b"synthetic device-local diagnostics").unwrap();
        let selected=dir.path().join("diagnostic-exclusion.nfai-backup");export_selected(&root,&database,selected.to_str().unwrap(),"test").unwrap();
        let archive=zip::ZipArchive::new(std::io::Cursor::new(fs::read(selected).unwrap())).unwrap();
        assert!(!archive.file_names().any(|name|name.contains("generation-continuity")));assert_eq!(fs::read(diagnostic).unwrap(),b"synthetic device-local diagnostics");
    }

    #[test]
    fn temporary_backup_excludes_private_records_and_bytes_without_mutating_source_or_shared_assets() {
        let dir=tempdir().unwrap();
        let root=dir.path().join("synthetic-root");
        let (database,private,shared)=temporary_backup_fixture(&root);
        let selected=dir.path().join("isolated.nfai-backup");
        let artifact=export_selected(&root,&database,selected.to_str().unwrap(),"test").unwrap();
        assert_eq!(artifact.table_counts["desktop_temporary_recovery"],0);
        assert_eq!(artifact.table_counts["desktop_temporary_attachments"],0);
        let verified=verify_package(&selected,23).unwrap();
        let stage=dir.path().join("inspect");extract_verified(&selected,&stage,&verified).unwrap();
        assert!(!stage.join(format!("assets/attachments/v1/{private}")).exists());
        assert_eq!(fs::read(stage.join(format!("assets/attachments/v1/{shared}"))).unwrap(),b"synthetic ordinary shared attachment");
        let snapshot=Connection::open(stage.join(DATABASE_ENTRY)).unwrap();
        assert_eq!(snapshot.query_row("SELECT COUNT(*) FROM desktop_attachment_assets WHERE sha256=?1",[&private],|r|r.get::<_,u64>(0)).unwrap(),0);
        assert_eq!(snapshot.query_row("SELECT reference_count FROM desktop_attachment_assets WHERE sha256=?1",[&shared],|r|r.get::<_,u64>(0)).unwrap(),1);
        assert!(!String::from_utf8_lossy(&fs::read(stage.join(DATABASE_ENTRY)).unwrap()).contains("synthetic private draft and message"));
        let original=Connection::open(&database).unwrap();
        assert_eq!(original.query_row("SELECT recovery_json,updated_at_ms FROM desktop_temporary_recovery",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,i64>(1)?))).unwrap(),("synthetic private draft and message".into(),123));
        assert_eq!(original.query_row("SELECT COUNT(*) FROM desktop_temporary_attachments",[],|r|r.get::<_,u64>(0)).unwrap(),2);
        assert_eq!(fs::read(root.join("assets").join(private)).unwrap(),b"synthetic temporary-only attachment");
        assert_eq!(fs::read(root.join("assets").join(shared)).unwrap(),b"synthetic ordinary shared attachment");
        assert!(preflight_selected(&root,&database,selected.to_str().unwrap()).is_ok());
    }

    #[test]
    fn temporary_backup_keeps_assets_owned_by_persistent_exchange_indexes() {
        for table in ["workspace_assets", "exchange_v2_assets"] {
            let dir=tempdir().unwrap();let root=dir.path().join("synthetic-root");
            let (database,private,shared)=temporary_backup_fixture(&root);
            let connection=Connection::open(&database).unwrap();
            connection.execute("DELETE FROM desktop_conversation_attachments",[]).unwrap();
            connection.execute_batch(&format!("CREATE TABLE {table}(sha256 TEXT PRIMARY KEY)")).unwrap();
            connection.execute(&format!("INSERT INTO {table} VALUES(?1)"),[&shared]).unwrap();
            drop(connection);
            let selected=dir.path().join("exchange-shared.nfai-backup");
            export_selected(&root,&database,selected.to_str().unwrap(),"test").unwrap();
            let verified=verify_package(&selected,23).unwrap();let stage=dir.path().join("inspect");
            extract_verified(&selected,&stage,&verified).unwrap();
            assert!(!stage.join(format!("assets/attachments/v1/{private}")).exists());
            assert_eq!(fs::read(stage.join(format!("assets/attachments/v1/{shared}"))).unwrap(),b"synthetic ordinary shared attachment");
        }
    }

    #[test]
    fn temporary_backup_preflight_rejects_legacy_private_records_without_replacing_local_data() {
        let dir=tempdir().unwrap();let root=dir.path().join("synthetic-root");
        let (database,_,_)=temporary_backup_fixture(&root);
        let snapshot=dir.path().join("legacy.snapshot");snapshot_database(&database,&snapshot).unwrap();
        let selected=dir.path().join("legacy.nfai-backup");
        create_package(&root,&snapshot,&selected,"test",Vec::new(), &BTreeSet::new()).unwrap();
        let result=preflight_selected(&root,&database,selected.to_str().unwrap());
        assert!(result.is_err(),"legacy temporary recovery must not enter the restore path");
        let original=Connection::open(database).unwrap();
        assert_eq!(original.query_row("SELECT recovery_json FROM desktop_temporary_recovery",[],|r|r.get::<_,String>(0)).unwrap(),"synthetic private draft and message");
    }

    fn usage_ledger(root: &Path) {
        let path = usage_ledger_path(root);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        Connection::open(path)
            .unwrap()
            .execute_batch(
                "CREATE TABLE usage_ledger_entries(entry_id TEXT PRIMARY KEY,input_tokens INTEGER NOT NULL);\
                 PRAGMA user_version=1;\
                 INSERT INTO usage_ledger_entries VALUES('usage-before',42);",
            )
            .unwrap();
    }

    fn assert_checkpoint_reselection(preflight:bool) {
        for (operation,phase) in [("INTERRUPTED","ROLLED_BACK_INTERRUPTED"),("FAILED","ROLLED_BACK")] {
            let temp=tempdir().unwrap();let root=temp.path().join("owned-reselection");fs::create_dir_all(root.join("assets")).unwrap();let database_path=root.join("workspace.sqlite3");database(&database_path);usage_ledger(&root);fs::write(root.join("assets/original"),b"synthetic original asset").unwrap();let selected=temp.path().join("synthetic-original.nfai-backup");export_selected(&root,&database_path,selected.to_str().unwrap(),"test").unwrap();let first=preflight_selected(&root,&database_path,selected.to_str().unwrap()).unwrap();
            Connection::open(&database_path).unwrap().execute("UPDATE workspaces SET title='rollback-current'",[]).unwrap();let id="checkpoint-v4-700-previous";create_checkpoint(&root,&database_path,id).unwrap();
            write_state(&root,&PersistedState{operation:operation.into(),phase:phase.into(),fingerprint:Some(first.fingerprint.clone()),checkpoint_id:Some(id.into()),process_id:Some(std::process::id()),process_instance:Some(current_process_instance().unwrap().to_owned())}).unwrap();
            let before_state=fs::read(state_path(&root)).unwrap();let before_inbox=fs::read(inbox_path(&root)).unwrap();let before_checkpoint=fs::read(checkpoint_path(&root,id).join("workspace.snapshot")).unwrap();
            let error=if preflight {preflight_selected(&root,&database_path,selected.to_str().unwrap()).err()}else{restore_preflighted(&root,&database_path,&first.fingerprint,true).err()};assert!(error.is_some(),"An existing recovered checkpoint cannot lose its binding through a new selection or direct retry");
            assert_eq!(fs::read(state_path(&root)).unwrap(),before_state);assert_eq!(fs::read(inbox_path(&root)).unwrap(),before_inbox);assert_eq!(fs::read(checkpoint_path(&root,id).join("workspace.snapshot")).unwrap(),before_checkpoint);assert!(read_state(&root).is_ok());assert_eq!(Connection::open(&database_path).unwrap().query_row("SELECT title FROM workspaces",[],|r|r.get::<_,String>(0)).unwrap(),"rollback-current");assert_eq!(fs::read(root.join("assets/original")).unwrap(),b"synthetic original asset");
            cancel_pending(&root).unwrap();assert!(!checkpoint_path(&root,id).exists());let next=preflight_selected(&root,&database_path,selected.to_str().unwrap()).unwrap();let restored=restore_preflighted(&root,&database_path,&next.fingerprint,true).unwrap();assert!(restored.restart_required);assert_eq!(Connection::open(&database_path).unwrap().query_row("SELECT title FROM workspaces",[],|r|r.get::<_,String>(0)).unwrap(),"备份前");
        }
    }
    #[test]
    fn checkpoint_reselection_after_rollback_preserves_bound_checkpoint_until_explicit_cancel() {assert_checkpoint_reselection(true);}
    #[test]
    fn checkpoint_reselection_direct_retry_requires_cancel_and_fresh_preflight() {assert_checkpoint_reselection(false);}


    #[test]
    fn roundtrip_preserves_business_data_and_keeps_current_device_only_settings() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("root");
        fs::create_dir_all(root.join("assets")).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        usage_ledger(&root);
        fs::write(root.join("assets").join("asset-one"), b"before").unwrap();
        let selected = directory.path().join("backup.nfai-backup");
        let artifact =
            export_selected(&root, &database_path, selected.to_str().unwrap(), "test").unwrap();
        assert_eq!(artifact.schema_version, 23);
        let connection = Connection::open(&database_path).unwrap();
        connection
            .execute("UPDATE workspaces SET title='备份后'", [])
            .unwrap();
        connection
            .execute(
                "UPDATE desktop_provider_settings SET preset_id='CURRENT_DEVICE'",
                [],
            )
            .unwrap();
        connection
            .execute(
                "UPDATE desktop_portable_personalization_v1 SET interests='备份后的关注方向'",
                [],
            )
            .unwrap();
        connection.execute("UPDATE desktop_conversation_read_markers_v1 SET last_read_at_ms=30,manual_unread_at_ms=222",[]).unwrap();
        drop(connection);
        let usage_ledger = usage_ledger_path(&root);
        Connection::open(&usage_ledger)
            .unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=99", [])
            .unwrap();
        fs::write(root.join("assets").join("asset-one"), b"after").unwrap();
        let preflight =
            preflight_selected(&root, &database_path, selected.to_str().unwrap()).unwrap();
        assert_eq!(preflight.conflicts.len(), 1);
        let receipt =
            restore_preflighted(&root, &database_path, &preflight.fingerprint, true).unwrap();
        assert!(receipt.restart_required);
        let connection = Connection::open(&database_path).unwrap();
        let title: String = connection
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0))
            .unwrap();
        let preset: String = connection
            .query_row(
                "SELECT preset_id FROM desktop_provider_settings",
                [],
                |row| row.get(0),
            )
            .unwrap();
        let derived: u64 = connection
            .query_row(
                "SELECT COUNT(*) FROM desktop_local_search_index",
                [],
                |row| row.get(0),
            )
            .unwrap();
        let task: (String, Option<String>) = connection
            .query_row(
                "SELECT status,failure FROM chatgpt_import_tasks",
                [],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .unwrap();
        let interests: String = connection
            .query_row(
                "SELECT interests FROM desktop_portable_personalization_v1 WHERE id=1",
                [],
                |row| row.get(0),
            )
            .unwrap();
        let history_state:String=connection.query_row("SELECT status FROM desktop_history_knowledge_candidates_v1 WHERE candidate_id='candidate-running'",[],|row|row.get(0)).unwrap();
        let reminder_draft:(String,Option<String>)=connection.query_row("SELECT status,safe_error_code FROM desktop_reminder_drafts_v1 WHERE draft_id='draft-running'",[],|row|Ok((row.get(0)?,row.get(1)?))).unwrap();
        let reminder_plan:(String,Option<i64>,Option<String>)=connection.query_row("SELECT status,next_run_at_ms,last_safe_error_code FROM desktop_reminder_plans_v1 WHERE plan_id='plan-running'",[],|row|Ok((row.get(0)?,row.get(1)?,row.get(2)?))).unwrap();
        let reminder_run:(String,String,Option<String>)=connection.query_row("SELECT status,notification_state,notification_safe_code FROM desktop_reminder_runs_v1 WHERE run_id='run-running'",[],|row|Ok((row.get(0)?,row.get(1)?,row.get(2)?))).unwrap();
        let read_marker:(i64,Option<i64>)=connection.query_row("SELECT last_read_at_ms,manual_unread_at_ms FROM desktop_conversation_read_markers_v1 WHERE workspace_id='workspace-one' AND conversation_id='conversation-one'",[],|row|Ok((row.get(0)?,row.get(1)?))).unwrap();
        assert_eq!(title, "备份前");
        assert_eq!(preset, "CURRENT_DEVICE");
        assert_eq!(derived, 0);
        assert_eq!(task, ("FAILED".into(), Some("INTERRUPTED".into())));
        assert_eq!(interests, "备份前的关注方向");
        assert_eq!(history_state, "UNKNOWN");
        assert_eq!(
            reminder_draft,
            ("UNKNOWN".into(), Some("BACKUP_RESTORED".into()))
        );
        assert_eq!(
            reminder_plan,
            ("UNKNOWN".into(), None, Some("BACKUP_RESTORED".into()))
        );
        assert_eq!(
            reminder_run,
            (
                "UNKNOWN".into(),
                "SUPPRESSED".into(),
                Some("BACKUP_RESTORED".into())
            )
        );
        assert_eq!(read_marker, (30, Some(222)));
        let usage_tokens: i64 = Connection::open(usage_ledger)
            .unwrap()
            .query_row(
                "SELECT input_tokens FROM usage_ledger_entries WHERE entry_id='usage-before'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(usage_tokens, 42);
        assert_eq!(
            fs::read(root.join("assets").join("asset-one")).unwrap(),
            b"before"
        );
        assert!(requires_restart(&root));
    }

    #[test]
    fn selecting_another_backup_replaces_the_preflighted_private_copy() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("root");
        fs::create_dir_all(root.join("assets")).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        let first = directory.path().join("first.nfai-backup");
        let second = directory.path().join("second.nfai-backup");
        export_selected(&root, &database_path, first.to_str().unwrap(), "test").unwrap();
        Connection::open(&database_path).unwrap()
            .execute("UPDATE workspaces SET title='第二份备份'", []).unwrap();
        export_selected(&root, &database_path, second.to_str().unwrap(), "test").unwrap();
        Connection::open(&database_path).unwrap()
            .execute("UPDATE workspaces SET title='当前本机数据'", []).unwrap();

        let first_preflight = preflight_selected(&root, &database_path, first.to_str().unwrap()).unwrap();
        let second_preflight = preflight_selected(&root, &database_path, second.to_str().unwrap()).unwrap();
        assert_ne!(first_preflight.fingerprint, second_preflight.fingerprint);
        assert!(restore_preflighted(&root, &database_path, &first_preflight.fingerprint, true).is_err());
        restore_preflighted(&root, &database_path, &second_preflight.fingerprint, true).unwrap();
        let title: String = Connection::open(&database_path).unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
        assert_eq!(title, "第二份备份");
    }

    #[test]
    fn non_empty_restore_requires_explicit_replace_and_cancel_keeps_data() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("root");
        fs::create_dir_all(&root).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        let selected = directory.path().join("backup.nfai-backup");
        export_selected(&root, &database_path, selected.to_str().unwrap(), "test").unwrap();
        let preflight =
            preflight_selected(&root, &database_path, selected.to_str().unwrap()).unwrap();
        assert!(restore_preflighted(&root, &database_path, &preflight.fingerprint, false).is_err());
        cancel_pending(&root).unwrap();
        assert!(!inbox_path(&root).exists());
        let title: String = Connection::open(database_path)
            .unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0))
            .unwrap();
        assert_eq!(title, "备份前");
    }

    #[test]
    fn path_escape_archive_is_rejected_before_database_changes() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("root");
        fs::create_dir_all(&root).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        let selected = directory.path().join("unsafe.nfai-backup");
        let file = fs::File::create(&selected).unwrap();
        let mut writer = ZipWriter::new(file);
        writer
            .start_file("../escape", SimpleFileOptions::default())
            .unwrap();
        writer.write_all(b"unsafe").unwrap();
        writer.finish().unwrap();
        assert!(preflight_selected(&root, &database_path, selected.to_str().unwrap()).is_err());
        let title: String = Connection::open(database_path)
            .unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0))
            .unwrap();
        assert_eq!(title, "备份前");
    }

    #[test]
    fn checkpoint_v2_recovery_removes_only_its_own_transient_directories() {
        for phase in ["CHECKPOINTED", "SWITCHING"] {
            let temp = tempdir().unwrap();
            let root = temp.path().join("synthetic-restore-root");
            fs::create_dir(&root).unwrap();
            let db = root.join("workspace.sqlite3");
            database(&db);
            let id = "checkpoint-v2-1-1";
            create_checkpoint(&root, &db, id).unwrap();
            if phase == "SWITCHING" {
                Connection::open(&db)
                    .unwrap()
                    .execute("UPDATE workspaces SET title='候选数据'", [])
                    .unwrap();
            }
            let stage = owner_root(&root).join(format!("restore-{id}"));
            let switch = owner_root(&root).join(format!("switch-old-{id}"));
            let unrelated = owner_root(&root).join("restore-unrelated");
            for path in [&stage, &switch, &unrelated] {
                fs::create_dir_all(path).unwrap();
                fs::write(path.join("keep.txt"), b"synthetic").unwrap();
            }
            write_state(
                &root,
                &PersistedState {
                    operation: "INTERRUPTED".into(),
                    phase: phase.into(),
                    checkpoint_id: Some(id.into()),
                    ..PersistedState::default()
                },
            )
            .unwrap();
            recover_interrupted_switch(&root, &db, "CHAT").unwrap();
            assert!(!stage.exists());
            assert!(!switch.exists());
            assert!(unrelated.join("keep.txt").is_file());
            assert!(checkpoint_path(&root, id).is_dir());
            let title: String = Connection::open(&db)
                .unwrap()
                .query_row("SELECT title FROM workspaces", [], |row| row.get(0))
                .unwrap();
            assert_eq!(title, "备份前");
            assert_eq!(read_state(&root).unwrap().phase, "ROLLED_BACK_INTERRUPTED");
        }
    }

    #[test]
    fn legacy_checkpoint_with_unproven_asset_change_fails_before_touching_live_owners() {
        for checkpoint_id in ["checkpoint-v2-1-asset", "checkpoint-v3-1-asset"] {
            let temp = tempdir().unwrap();
            let root = temp.path().join("synthetic-restore-root");
            fs::create_dir_all(root.join("assets")).unwrap();
            let db = root.join("workspace.sqlite3");
            database(&db);
            let active_asset = root.join("assets/original.txt");
            fs::write(&active_asset, b"original asset").unwrap();
            create_checkpoint(&root, &db, checkpoint_id).unwrap();
            Connection::open(&db).unwrap()
                .execute("UPDATE workspaces SET title='candidate data'", []).unwrap();
            fs::write(&active_asset, b"current asset").unwrap();
            fs::remove_file(checkpoint_path(&root, checkpoint_id)
                .join("assets/attachments/v1/original.txt")).unwrap();
            write_state(&root, &PersistedState {
                operation: "INTERRUPTED".into(), phase: "SWITCHING".into(),
                checkpoint_id: Some(checkpoint_id.into()), ..PersistedState::default()
            }).unwrap();

            assert!(recover_interrupted_switch(&root, &db, "CHAT").is_err());
            assert_eq!(fs::read(&active_asset).unwrap(), b"current asset");
            let title: String = Connection::open(&db).unwrap()
                .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
            assert_eq!(title, "candidate data");
            assert!(checkpoint_path(&root, checkpoint_id).is_dir());
            assert_eq!(read_state(&root).unwrap().phase, "SWITCHING");
        }
    }

    #[test]
    fn legacy_checkpoint_with_identical_assets_can_restore_database_without_replacing_assets() {
        for checkpoint_id in ["checkpoint-v2-1-same", "checkpoint-v3-1-same"] {
            let temp = tempdir().unwrap();
            let root = temp.path().join("synthetic-restore-root");
            fs::create_dir_all(root.join("assets")).unwrap();
            let db = root.join("workspace.sqlite3");
            database(&db);
            let asset = root.join("assets/original.txt");
            fs::write(&asset, b"same asset").unwrap();
            create_checkpoint(&root, &db, checkpoint_id).unwrap();
            Connection::open(&db).unwrap()
                .execute("UPDATE workspaces SET title='candidate data'", []).unwrap();
            write_state(&root, &PersistedState {
                operation: "INTERRUPTED".into(), phase: "SWITCHING".into(),
                checkpoint_id: Some(checkpoint_id.into()), ..PersistedState::default()
            }).unwrap();

            recover_interrupted_switch(&root, &db, "CHAT").unwrap();
            assert_eq!(fs::read(&asset).unwrap(), b"same asset");
            let title: String = Connection::open(&db).unwrap()
                .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
            assert_eq!(title, "备份前");
        }
    }

    #[test]
    fn legacy_checkpoint_asset_change_after_preflight_blocks_database_write() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-restore-root");
        fs::create_dir_all(root.join("assets")).unwrap();
        let db = root.join("workspace.sqlite3");
        database(&db);
        let asset = root.join("assets/original.txt");
        fs::write(&asset, b"same asset").unwrap();
        let checkpoint_id = "checkpoint-v3-1-preflight";
        create_checkpoint(&root, &db, checkpoint_id).unwrap();
        Connection::open(&db).unwrap()
            .execute("UPDATE workspaces SET title='candidate data'", []).unwrap();
        let snapshot_asset = checkpoint_path(&root, checkpoint_id)
            .join("assets/attachments/v1/original.txt");
        let error = restore_checkpoint_with_hook(&root, &db, checkpoint_id, || {
            fs::remove_file(&snapshot_asset).unwrap();
            Ok(())
        }).unwrap_err();
        assert!(error.contains("预检后变化"));
        assert_eq!(fs::read(&asset).unwrap(), b"same asset");
        let title: String = Connection::open(&db).unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
        assert_eq!(title, "candidate data");
    }

    #[test]
    fn checkpoint_v2_missing_ledger_snapshot_keeps_current_ledger_and_database() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-restore-root");
        fs::create_dir(&root).unwrap();
        let db = root.join("workspace.sqlite3");
        database(&db);
        usage_ledger(&root);
        let id = "checkpoint-v2-1-ledger";
        create_checkpoint(&root, &db, id).unwrap();
        let snapshot = checkpoint_path(&root, id).join(USAGE_LEDGER_CHECKPOINT);
        let snapshot_bytes = fs::read(&snapshot).unwrap();
        Connection::open(&db).unwrap()
            .execute("UPDATE workspaces SET title='candidate data'", []).unwrap();
        let ledger = usage_ledger_path(&root);
        Connection::open(&ledger).unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=99", []).unwrap();
        fs::remove_file(&snapshot).unwrap();
        write_state(&root, &PersistedState {
            operation: "INTERRUPTED".into(), phase: "SWITCHING".into(),
            checkpoint_id: Some(id.into()), ..PersistedState::default()
        }).unwrap();

        assert!(recover_interrupted_switch(&root, &db, "CHAT").is_err());
        let title: String = Connection::open(&db).unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
        let tokens: i64 = Connection::open(&ledger).unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get(0)).unwrap();
        assert_eq!(title, "candidate data");
        assert_eq!(tokens, 99);
        assert!(checkpoint_path(&root, id).is_dir());
        assert_eq!(read_state(&root).unwrap().phase, "SWITCHING");

        fs::create_dir_all(snapshot.parent().unwrap()).unwrap();
        fs::write(&snapshot, snapshot_bytes).unwrap();
        recover_interrupted_switch(&root, &db, "CHAT").unwrap();
        let restored_tokens: i64 = Connection::open(&ledger).unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get(0)).unwrap();
        assert_eq!(restored_tokens, 42);
    }

    #[test]
    fn checkpoint_v2_cleanup_collision_keeps_recovery_retryable() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-restore-root");
        fs::create_dir(&root).unwrap();
        let db = root.join("workspace.sqlite3");
        database(&db);
        let id = "checkpoint-v2-1-2";
        create_checkpoint(&root, &db, id).unwrap();
        Connection::open(&db)
            .unwrap()
            .execute("UPDATE workspaces SET title='候选数据'", [])
            .unwrap();
        let collision = restore_stage_path(&root, id);
        fs::write(&collision, b"unrelated file").unwrap();
        write_state(
            &root,
            &PersistedState {
                operation: "INTERRUPTED".into(),
                phase: "SWITCHING".into(),
                checkpoint_id: Some(id.into()),
                ..PersistedState::default()
            },
        )
        .unwrap();

        assert!(recover_interrupted_switch(&root, &db, "CHAT").is_err());
        assert_eq!(fs::read(&collision).unwrap(), b"unrelated file");
        assert_eq!(read_state(&root).unwrap().phase, "SWITCHING");
        assert!(checkpoint_path(&root, id).is_dir());
        let title: String = Connection::open(&db)
            .unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0))
            .unwrap();
        assert_eq!(title, "备份前");

        fs::remove_file(&collision).unwrap();
        recover_interrupted_switch(&root, &db, "CHAT").unwrap();
        assert_eq!(read_state(&root).unwrap().phase, "ROLLED_BACK_INTERRUPTED");
    }

    #[test]
    fn damaged_or_missing_state_with_checkpoint_blocks_recovery_and_preserves_data() {
        for damaged in [true, false] {
            let temp = tempdir().unwrap();
            let root = temp.path().join("synthetic-restore-root");
            fs::create_dir(&root).unwrap();
            let db = root.join("workspace.sqlite3");
            database(&db);
            create_checkpoint(&root, &db, "synthetic-checkpoint").unwrap();
            Connection::open(&db)
                .unwrap()
                .execute("UPDATE workspaces SET title='候选数据'", [])
                .unwrap();
            write_state(
                &root,
                &PersistedState {
                    operation: "INTERRUPTED".into(),
                    phase: "SWITCHING".into(),
                    checkpoint_id: Some("synthetic-checkpoint".into()),
                    ..PersistedState::default()
                },
            )
            .unwrap();
            let valid_state = fs::read(state_path(&root)).unwrap();
            if damaged {
                fs::write(state_path(&root), b"{damaged").unwrap();
            } else {
                fs::remove_file(state_path(&root)).unwrap();
            }
            assert!(recover_interrupted_switch(&root, &db, "CHAT").is_err());
            assert!(read_status(&root, &db).is_err());
            assert!(cancel_pending(&root).is_err());
            assert!(checkpoint_path(&root, "synthetic-checkpoint").is_dir());
            let candidate_title: String = Connection::open(&db)
                .unwrap()
                .query_row("SELECT title FROM workspaces", [], |row| row.get(0))
                .unwrap();
            assert_eq!(candidate_title, "候选数据");
            write_state(
                &root,
                &PersistedState {
                    operation: "IDLE".into(),
                    phase: "NONE".into(),
                    checkpoint_id: Some("synthetic-checkpoint".into()),
                    ..PersistedState::default()
                },
            )
            .unwrap();
            assert!(recover_interrupted_switch(&root, &db, "CHAT").is_err());
            fs::write(state_path(&root), valid_state).unwrap();
            recover_interrupted_switch(&root, &db, "CHAT").unwrap();
            let restored_title: String = Connection::open(&db)
                .unwrap()
                .query_row("SELECT title FROM workspaces", [], |row| row.get(0))
                .unwrap();
            assert_eq!(restored_title, "备份前");
        }
    }

    #[test]
    fn selected_root_missing_database_recovers_real_checkpoint_before_validation() {
        use fs2::FileExt;
        let temp = tempdir().unwrap();
        let root = temp.path().join("selected");
        fs::create_dir(&root).unwrap();
        let db = root.join("workspace.sqlite3");
        database(&db);
        Connection::open(&db)
            .unwrap()
            .execute_batch("CREATE TABLE workspace_exchange(workspace_id TEXT);")
            .unwrap();
        create_checkpoint(&root, &db, "boundary-checkpoint").unwrap();
        write_state(
            &root,
            &PersistedState {
                operation: "INTERRUPTED".into(),
                phase: "SWITCHING".into(),
                checkpoint_id: Some("boundary-checkpoint".into()),
                ..PersistedState::default()
            },
        )
        .unwrap();
        fs::remove_file(&db).unwrap();
        let config = temp.path().join("location.json");
        fs::write(
            &config,
            serde_json::to_vec(&serde_json::json!({"active": root, "pending": null})).unwrap(),
        )
        .unwrap();
        let selected =
            crate::desktop_storage_location::resolve_with_recovery(&config, &root, |selected| {
                let competing = fs::OpenOptions::new()
                    .read(true)
                    .write(true)
                    .open(selected.join(".runtime-owner.lock"))
                    .unwrap();
                assert!(competing.try_lock_exclusive().is_err());
                recover_interrupted_switch(selected, &selected.join("workspace.sqlite3"), "CHAT")
            })
            .unwrap();
        assert_eq!(selected, root);
        let title: String = Connection::open(&db)
            .unwrap()
            .query_row(
                "SELECT title FROM workspaces WHERE id='workspace-one'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(title, "备份前");
        assert_eq!(read_state(&root).unwrap().phase, "ROLLED_BACK_INTERRUPTED");
    }

    #[test]
    fn legacy_restore_rejects_an_existing_work_area_before_replacing_chat() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("selected");
        fs::create_dir(&root).unwrap();
        let db = root.join("workspace.sqlite3");
        database(&db);
        let before = fs::read(&db).unwrap();
        let work = root.join("work-area");
        fs::create_dir(&work).unwrap();
        fs::write(work.join("keep.txt"), b"WORK content").unwrap();
        create_checkpoint(&root, &db, "old-checkpoint").unwrap();
        write_state(
            &root,
            &PersistedState {
                operation: "INTERRUPTED".into(),
                phase: "SWITCHING".into(),
                checkpoint_id: Some("old-checkpoint".into()),
                ..PersistedState::default()
            },
        )
        .unwrap();

        let error = recover_interrupted_switch(&root, &db, "CHAT").unwrap_err();
        assert!(error.contains("旧单区恢复不能覆盖已拆分或待发布的双区数据"));
        assert_eq!(fs::read(&db).unwrap(), before);
        assert_eq!(fs::read(work.join("keep.txt")).unwrap(), b"WORK content");
    }

    #[test]
    fn schema45_backup_preflight_rejects_cross_area_without_touching_target() {
        let temp = tempdir().unwrap();
        let chat_root = temp.path().join("chat");
        let work_root = temp.path().join("work");
        fs::create_dir(&chat_root).unwrap();
        fs::create_dir(&work_root).unwrap();
        let chat_database = chat_root.join("workspace.sqlite3");
        let work_database = work_root.join("workspace.sqlite3");
        for (path, area) in [(&chat_database, "CHAT"), (&work_database, "WORK")] {
            database(path);
            let connection = Connection::open(path).unwrap();
            connection
                .execute_batch(&format!(
                    "PRAGMA user_version=45; CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'{area}',1);"
                ))
                .unwrap();
        }
        let selected = temp.path().join("chat.nfai-backup");
        export_selected(
            &chat_root,
            &chat_database,
            selected.to_str().unwrap(),
            "test",
        )
        .unwrap();
        let before = fs::read(&work_database).unwrap();
        assert!(
            preflight_selected(&work_root, &work_database, selected.to_str().unwrap())
                .unwrap_err()
                .contains("不能跨 CHAT/WORK 恢复")
        );
        assert_eq!(fs::read(&work_database).unwrap(), before);
        assert!(!inbox_path(&work_root).exists());
        assert_eq!(read_state(&work_root).unwrap().operation, "IDLE");
        let preflight =
            preflight_selected(&chat_root, &chat_database, selected.to_str().unwrap()).unwrap();
        Connection::open(&chat_database)
            .unwrap()
            .execute("UPDATE desktop_data_area_v1 SET area='WORK' WHERE id=1", [])
            .unwrap();
        let after_preflight_change = fs::read(&chat_database).unwrap();
        assert!(
            restore_preflighted(&chat_root, &chat_database, &preflight.fingerprint, true)
                .unwrap_err()
                .contains("不能跨 CHAT/WORK 恢复")
        );
        assert_eq!(fs::read(&chat_database).unwrap(), after_preflight_change);
    }

    #[test]
    fn schema46_area_accepts_its_schema45_backup_and_publishes_it_for_restart_upgrade() {
        let temp = tempdir().unwrap();
        let archived_root = temp.path().join("archived");
        let current_root = temp.path().join("current");
        fs::create_dir(&archived_root).unwrap();
        fs::create_dir(&current_root).unwrap();
        let archived_database = archived_root.join("workspace.sqlite3");
        let current_database = current_root.join("workspace.sqlite3");
        for path in [&archived_database, &current_database] {
            database(path);
            Connection::open(path).unwrap().execute_batch("CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'CHAT',1);").unwrap();
        }
        Connection::open(&archived_database).unwrap().pragma_update(None, "user_version", 45).unwrap();
        Connection::open(&current_database).unwrap().pragma_update(None, "user_version", 46).unwrap();
        let selected = temp.path().join("old-chat.nfai-backup");
        export_selected(&archived_root, &archived_database, selected.to_str().unwrap(), "test").unwrap();
        let preflight = preflight_selected(&current_root, &current_database, selected.to_str().unwrap()).unwrap();
        assert_eq!(preflight.schema_version, 45);
        restore_preflighted(&current_root, &current_database, &preflight.fingerprint, true).unwrap();
        assert_eq!(schema_version(&current_database).unwrap(), 45);
        assert_eq!(read_schema45_area(&current_database).unwrap(), "CHAT");
        assert!(requires_restart(&current_root));
    }

    #[test]
    fn schema52_restores_committed_schema50_and_schema51_backups() {
        for archived in [50, 51] {
            let temp = tempdir().unwrap();
            let archived_root = temp.path().join("archived");
            let current_root = temp.path().join("current");
            fs::create_dir(&archived_root).unwrap();
            fs::create_dir(&current_root).unwrap();
            let archived_database = archived_root.join("workspace.sqlite3");
            let current_database = current_root.join("workspace.sqlite3");
            for (path, version) in [(&archived_database, archived), (&current_database, 52)] {
                database(path);
                let connection = Connection::open(path).unwrap();
                connection.execute_batch(
                    "CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT,split_done INTEGER);
                     INSERT INTO desktop_data_area_v1 VALUES(1,'WORK',1);
                     CREATE TABLE desktop_conversation_drafts_v1(workspace_id TEXT,conversation_key TEXT,text TEXT,updated_at_ms INTEGER,model_id TEXT,tone_override TEXT,web_search_override INTEGER);
                     CREATE TABLE domain_intents(request_fingerprint TEXT);
                     ALTER TABLE chatgpt_import_tasks ADD COLUMN workspace_id TEXT;
                     CREATE TABLE chatgpt_import_receipts(workspace_id TEXT);
                     CREATE TABLE claude_import_tasks(workspace_id TEXT);
                     CREATE TABLE claude_import_receipts(workspace_id TEXT);
                     CREATE TABLE nanfeng_knowledge_import_tasks(workspace_id TEXT);
                     CREATE TABLE nanfeng_knowledge_import_receipts(workspace_id TEXT);
                     CREATE TABLE p6k_zip_import_attachment_summary(attachment_id TEXT);")
                    .unwrap();
                if version >= 51 {
                    connection.execute_batch("CREATE TABLE desktop_app_settings(id INTEGER PRIMARY KEY,memory_retrieval_enabled INTEGER);").unwrap();
                }
                connection.pragma_update(None, "user_version", version).unwrap();
            }
            let connection = Connection::open(&archived_database).unwrap();
            assert!(matches!(crate::desktop_schema_source::classify(&connection, archived).unwrap(),
                crate::desktop_schema_source::SchemaSource::Common50 { split_done: true, .. }
                | crate::desktop_schema_source::SchemaSource::Common51 { split_done: true, .. }));
            drop(connection);
            let selected = temp.path().join("historical.nfai-backup");
            export_selected(&archived_root, &archived_database, selected.to_str().unwrap(), "test").unwrap();
            let preflight = preflight_selected(&current_root, &current_database, selected.to_str().unwrap()).unwrap();
            assert_eq!(preflight.schema_version, archived);
            restore_preflighted(&current_root, &current_database, &preflight.fingerprint, true).unwrap();
            assert_eq!(schema_version(&current_database).unwrap(), archived);
            assert_eq!(read_schema45_area(&current_database).unwrap(), "WORK");
            assert!(requires_restart(&current_root));
            let connection = Connection::open(&current_database).unwrap();
            assert_eq!(connection.query_row("SELECT title FROM workspaces WHERE id='workspace-one'", [], |r|r.get::<_,String>(0)).unwrap(), "备份前");
            // A version label alone must still fail the same structural gate.
            connection.execute_batch("DROP TABLE p6k_zip_import_attachment_summary;").unwrap();
            drop(connection);
            assert!(require_forward_area_schema(52, archived, &current_database).is_err());
        }
    }

    #[test]
    fn forward_backup_rejects_a_version_label_without_the_committed_area_schema() {
        let temp = tempdir().unwrap();
        let candidate = temp.path().join("claimed-schema49.sqlite3");
        let connection = Connection::open(&candidate).unwrap();
        connection.execute_batch("CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'CHAT',1); PRAGMA user_version=49;").unwrap();
        drop(connection);
        assert!(backup_schema_compatible(50, 49));
        assert!(require_forward_area_schema(50, 49, &candidate)
            .unwrap_err()
            .contains("旧备份不是完整的已提交区域 Schema"));
    }

    #[test]
    fn interrupted_work_restore_rolls_back_only_work_checkpoint() {
        let temp = tempdir().unwrap();
        let app_root = temp.path().join("root");
        let work_root = app_root.join("work-area");
        fs::create_dir_all(&work_root).unwrap();
        let chat_database = app_root.join("workspace.sqlite3");
        let work_database = work_root.join("workspace.sqlite3");
        for (path, area) in [(&chat_database, "CHAT"), (&work_database, "WORK")] {
            database(path);
            Connection::open(path)
                .unwrap()
                .execute_batch(&format!(
                    "PRAGMA user_version=45; CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'{area}',1);"
                ))
                .unwrap();
        }
        let chat_before = fs::read(&chat_database).unwrap();
        create_checkpoint(&work_root, &work_database, "work-checkpoint").unwrap();
        write_state(
            &work_root,
            &PersistedState {
                operation: "INTERRUPTED".into(),
                phase: "SWITCHING".into(),
                checkpoint_id: Some("work-checkpoint".into()),
                ..PersistedState::default()
            },
        )
        .unwrap();
        fs::remove_file(&work_database).unwrap();

        recover_interrupted_switch(&work_root, &work_database, "WORK").unwrap();

        let title: String = Connection::open(&work_database)
            .unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0))
            .unwrap();
        assert_eq!(title, "备份前");
        assert_eq!(
            read_state(&work_root).unwrap().phase,
            "ROLLED_BACK_INTERRUPTED"
        );
        assert_eq!(fs::read(&chat_database).unwrap(), chat_before);
        assert_eq!(read_state(&app_root).unwrap().operation, "IDLE");
    }

    #[test]
    fn failed_work_asset_switch_rolls_back_work_and_preserves_other_area() {
        let temp = tempdir().unwrap();
        let app_root = temp.path().join("root");
        let work_root = app_root.join("work-area");
        fs::create_dir_all(&work_root).unwrap();
        let chat_database = app_root.join("workspace.sqlite3");
        let work_database = work_root.join("workspace.sqlite3");
        for (path, area) in [(&chat_database, "CHAT"), (&work_database, "WORK")] {
            database(path);
            Connection::open(path)
                .unwrap()
                .execute_batch(&format!(
                    "PRAGMA user_version=45; CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'{area}',1);"
                ))
                .unwrap();
        }
        usage_ledger(&work_root);
        fs::create_dir_all(work_root.join("assets")).unwrap();
        fs::write(work_root.join("assets/one.txt"), b"backup asset").unwrap();
        let selected = temp.path().join("work.nfai-backup");
        export_selected(
            &work_root,
            &work_database,
            selected.to_str().unwrap(),
            "test",
        )
        .unwrap();

        Connection::open(&work_database)
            .unwrap()
            .execute("UPDATE workspaces SET title='restore 前的 WORK 修改'", [])
            .unwrap();
        Connection::open(usage_ledger_path(&work_root))
            .unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=99", [])
            .unwrap();
        fs::write(work_root.join("assets/one.txt"), b"current asset").unwrap();
        fs::write(work_root.join("assets/two.txt"), b"new current asset").unwrap();
        let shared_config = app_root.join("shared-config.json");
        fs::write(&shared_config, b"shared config must stay").unwrap();
        let chat_before = fs::read(&chat_database).unwrap();
        let config_before = fs::read(&shared_config).unwrap();
        let preflight =
            preflight_selected(&work_root, &work_database, selected.to_str().unwrap()).unwrap();

        let error = restore_preflighted_with_hook(
            &work_root,
            &work_database,
            &preflight.fingerprint,
            true,
            || {
                let switched_title: String = Connection::open(&work_database)
                    .unwrap()
                    .query_row("SELECT title FROM workspaces", [], |row| row.get(0))
                    .unwrap();
                assert_eq!(switched_title, "备份前");
                let switched_tokens: i64 = Connection::open(usage_ledger_path(&work_root))
                    .unwrap()
                    .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| {
                        row.get(0)
                    })
                    .unwrap();
                assert_eq!(switched_tokens, 42);
                assert_eq!(
                    fs::read(work_root.join("assets/one.txt")).unwrap(),
                    b"backup asset"
                );
                Err(json_error("注入首个资产切换后的失败"))
            },
        )
        .unwrap_err();
        assert!(error.contains("注入首个资产切换后的失败"));
        let work_title: String = Connection::open(&work_database)
            .unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0))
            .unwrap();
        assert_eq!(work_title, "restore 前的 WORK 修改");
        let usage_tokens: i64 = Connection::open(usage_ledger_path(&work_root))
            .unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| {
                row.get(0)
            })
            .unwrap();
        assert_eq!(usage_tokens, 99);
        assert_eq!(
            fs::read(work_root.join("assets/one.txt")).unwrap(),
            b"current asset"
        );
        assert_eq!(
            fs::read(work_root.join("assets/two.txt")).unwrap(),
            b"new current asset"
        );
        assert_eq!(fs::read(&chat_database).unwrap(), chat_before);
        assert_eq!(fs::read(&shared_config).unwrap(), config_before);
        let state = read_state(&work_root).unwrap();
        assert_eq!(state.operation, "FAILED");
        assert_eq!(state.phase, "ROLLED_BACK");
        assert!(checkpoint_path(&work_root, state.checkpoint_id.as_deref().unwrap()).is_dir());
        assert!(!requires_restart(&work_root));
    }

    #[test]
    fn failed_work_rollback_retries_checkpoint_on_fresh_start() {
        let temp = tempdir().unwrap();
        let app_root = temp.path().join("root");
        let work_root = app_root.join("work-area");
        fs::create_dir_all(&work_root).unwrap();
        let chat_database = app_root.join("workspace.sqlite3");
        let work_database = work_root.join("workspace.sqlite3");
        for (path, area) in [(&chat_database, "CHAT"), (&work_database, "WORK")] {
            database(path);
            Connection::open(path)
                .unwrap()
                .execute_batch(&format!(
                    "PRAGMA user_version=45; CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'{area}',1);"
                ))
                .unwrap();
        }
        usage_ledger(&work_root);
        fs::create_dir_all(work_root.join("assets")).unwrap();
        fs::write(
            work_root.join("assets/original.txt"),
            b"current private asset",
        )
        .unwrap();
        let shared_config = app_root.join("shared-config.json");
        fs::write(&shared_config, b"shared untouched").unwrap();
        let chat_before = fs::read(&chat_database).unwrap();
        create_checkpoint(&work_root, &work_database, "checkpoint-v4-1-10").unwrap();

        Connection::open(&work_database)
            .unwrap()
            .execute("UPDATE workspaces SET title='candidate not committed'", [])
            .unwrap();
        Connection::open(usage_ledger_path(&work_root))
            .unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=100", [])
            .unwrap();
        fs::write(work_root.join("assets/original.txt"), b"candidate asset").unwrap();
        fs::write(work_root.join("assets/extra.txt"), b"candidate extra").unwrap();
        write_state(
            &work_root,
            &PersistedState {
                operation: "FAILED".into(),
                phase: "ROLLBACK_FAILED".into(),
                checkpoint_id: Some("checkpoint-v4-1-10".into()),
                ..PersistedState::default()
            },
        )
        .unwrap();
        assert!(cancel_pending(&work_root).is_err());
        assert!(export_selected(&work_root, &work_database, "invalid", "test").is_err());
        assert!(
            read_status(&work_root, &work_database)
                .unwrap()
                .rollback_pending
        );

        recover_interrupted_switch(&work_root, &work_database, "WORK").unwrap();
        recover_interrupted_switch(&work_root, &work_database, "WORK").unwrap();

        let title: String = Connection::open(&work_database)
            .unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0))
            .unwrap();
        assert_eq!(title, "备份前");
        let tokens: i64 = Connection::open(usage_ledger_path(&work_root))
            .unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| {
                row.get(0)
            })
            .unwrap();
        assert_eq!(tokens, 42);
        assert_eq!(
            fs::read(work_root.join("assets/original.txt")).unwrap(),
            b"current private asset"
        );
        assert!(!work_root.join("assets/extra.txt").exists());
        assert_eq!(fs::read(&chat_database).unwrap(), chat_before);
        assert_eq!(fs::read(&shared_config).unwrap(), b"shared untouched");
        assert_eq!(read_state(&work_root).unwrap().operation, "FAILED");
        assert_eq!(read_state(&work_root).unwrap().phase, "ROLLED_BACK");
        assert!(
            !read_status(&work_root, &work_database)
                .unwrap()
                .rollback_pending
        );
        assert!(checkpoint_path(&work_root, "checkpoint-v4-1-10").is_dir());
    }

    #[test]
    fn corrupt_usage_ledger_checkpoint_cannot_mutate_active_work_area() {
        let temp = tempdir().unwrap();
        let app_root = temp.path().join("root");
        let work_root = app_root.join("work-area");
        fs::create_dir_all(&work_root).unwrap();
        let chat_database = app_root.join("workspace.sqlite3");
        let work_database = work_root.join("workspace.sqlite3");
        database(&chat_database);
        database(&work_database);
        Connection::open(&work_database)
            .unwrap()
            .execute_batch("PRAGMA user_version=45; CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'WORK',1);")
            .unwrap();
        usage_ledger(&work_root);
        fs::create_dir_all(work_root.join("assets")).unwrap();
        fs::write(work_root.join("assets/current.txt"), b"original asset").unwrap();
        let shared_config = app_root.join("shared-config.json");
        fs::write(&shared_config, b"shared unchanged").unwrap();

        let checkpoint_id = "checkpoint-v4-1-11";
        create_checkpoint(&work_root, &work_database, checkpoint_id).unwrap();
        let ledger_snapshot = checkpoint_path(&work_root, checkpoint_id).join(USAGE_LEDGER_CHECKPOINT);
        let healthy_snapshot = fs::read(&ledger_snapshot).unwrap();
        Connection::open(&work_database)
            .unwrap()
            .execute("UPDATE workspaces SET title='current work'", [])
            .unwrap();
        Connection::open(usage_ledger_path(&work_root))
            .unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=100", [])
            .unwrap();
        fs::write(work_root.join("assets/current.txt"), b"current asset").unwrap();
        write_state(&work_root, &PersistedState {
            operation: "FAILED".into(),
            phase: "ROLLBACK_FAILED".into(),
            checkpoint_id: Some(checkpoint_id.into()),
            ..PersistedState::default()
        }).unwrap();
        fs::write(&ledger_snapshot, b"damaged ledger snapshot").unwrap();

        let before = [
            fs::read(&chat_database).unwrap(),
            fs::read(&work_database).unwrap(),
            fs::read(usage_ledger_path(&work_root)).unwrap(),
            fs::read(work_root.join("assets/current.txt")).unwrap(),
            fs::read(&shared_config).unwrap(),
        ];
        assert!(recover_interrupted_switch(&work_root, &work_database, "WORK").is_err());
        assert_eq!(fs::read(&chat_database).unwrap(), before[0]);
        assert_eq!(fs::read(&work_database).unwrap(), before[1]);
        assert_eq!(fs::read(usage_ledger_path(&work_root)).unwrap(), before[2]);
        assert_eq!(fs::read(work_root.join("assets/current.txt")).unwrap(), before[3]);
        assert_eq!(fs::read(&shared_config).unwrap(), before[4]);
        assert_eq!(read_state(&work_root).unwrap().phase, "ROLLBACK_FAILED");
        assert_eq!(fs::read(&ledger_snapshot).unwrap(), b"damaged ledger snapshot");

        fs::remove_file(&ledger_snapshot).unwrap();
        Connection::open(&ledger_snapshot)
            .unwrap()
            .execute_batch("PRAGMA user_version=1; CREATE TABLE unrelated(value TEXT);")
            .unwrap();
        assert!(sqlite_healthy(&ledger_snapshot).unwrap());
        assert!(recover_interrupted_switch(&work_root, &work_database, "WORK").is_err());
        assert_eq!(fs::read(&work_database).unwrap(), before[1]);
        assert_eq!(fs::read(usage_ledger_path(&work_root)).unwrap(), before[2]);
        assert_eq!(read_state(&work_root).unwrap().phase, "ROLLBACK_FAILED");

        fs::write(&ledger_snapshot, healthy_snapshot).unwrap();
        recover_interrupted_switch(&work_root, &work_database, "WORK").unwrap();
        assert_eq!(read_state(&work_root).unwrap().phase, "ROLLED_BACK");
        assert_eq!(fs::read(&chat_database).unwrap(), before[0]);
        assert_eq!(fs::read(&shared_config).unwrap(), before[4]);
        assert_eq!(fs::read(work_root.join("assets/current.txt")).unwrap(), b"original asset");
        assert_eq!(Connection::open(usage_ledger_path(&work_root)).unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get::<_, i64>(0)).unwrap(), 42);
    }

    #[test]
    fn v3_checkpoint_distinguishes_missing_ledger_snapshot_from_originally_absent_ledger() {
        let temp = tempdir().unwrap();
        let app_root = temp.path().join("root");
        let work_root = app_root.join("work-area");
        fs::create_dir_all(&work_root).unwrap();
        let chat_database = app_root.join("workspace.sqlite3");
        let work_database = work_root.join("workspace.sqlite3");
        database(&chat_database);
        database(&work_database);
        Connection::open(&work_database).unwrap().execute_batch(
            "PRAGMA user_version=45; CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'WORK',1);",
        ).unwrap();
        usage_ledger(&work_root);
        let checkpoint_id = "checkpoint-v3-1-1";
        create_checkpoint(&work_root, &work_database, checkpoint_id).unwrap();
        let ledger_snapshot = checkpoint_path(&work_root, checkpoint_id).join(USAGE_LEDGER_CHECKPOINT);
        let saved_snapshot = fs::read(&ledger_snapshot).unwrap();
        Connection::open(&work_database).unwrap()
            .execute("UPDATE workspaces SET title='current after checkpoint'", []).unwrap();
        Connection::open(usage_ledger_path(&work_root)).unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=100", []).unwrap();
        write_state(&work_root, &PersistedState {
            operation: "FAILED".into(),
            phase: "ROLLBACK_FAILED".into(),
            checkpoint_id: Some(checkpoint_id.into()),
            ..PersistedState::default()
        }).unwrap();
        let work_before = fs::read(&work_database).unwrap();
        let ledger_before = fs::read(usage_ledger_path(&work_root)).unwrap();
        let chat_before = fs::read(&chat_database).unwrap();
        let manifest_path = checkpoint_path(&work_root, checkpoint_id).join(CHECKPOINT_V3_MANIFEST);
        let saved_manifest = fs::read(&manifest_path).unwrap();
        fs::remove_file(&manifest_path).unwrap();
        assert!(recover_interrupted_switch(&work_root, &work_database, "WORK").is_err());
        assert_eq!(fs::read(&work_database).unwrap(), work_before);
        assert_eq!(fs::read(usage_ledger_path(&work_root)).unwrap(), ledger_before);
        assert_eq!(read_state(&work_root).unwrap().phase, "ROLLBACK_FAILED");
        fs::write(&manifest_path, saved_manifest).unwrap();
        fs::remove_file(&ledger_snapshot).unwrap();

        assert!(recover_interrupted_switch(&work_root, &work_database, "WORK").is_err());
        assert_eq!(fs::read(&work_database).unwrap(), work_before);
        assert_eq!(fs::read(usage_ledger_path(&work_root)).unwrap(), ledger_before);
        assert_eq!(fs::read(&chat_database).unwrap(), chat_before);
        assert_eq!(read_state(&work_root).unwrap().phase, "ROLLBACK_FAILED");

        fs::write(&ledger_snapshot, saved_snapshot).unwrap();
        recover_interrupted_switch(&work_root, &work_database, "WORK").unwrap();
        assert_eq!(read_state(&work_root).unwrap().phase, "ROLLED_BACK");
        assert_eq!(Connection::open(usage_ledger_path(&work_root)).unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get::<_, i64>(0)).unwrap(), 42);

        let empty_root = temp.path().join("originally-no-ledger");
        fs::create_dir_all(&empty_root).unwrap();
        let empty_database = empty_root.join("workspace.sqlite3");
        database(&empty_database);
        let empty_id = "checkpoint-v3-1-2";
        create_checkpoint(&empty_root, &empty_database, empty_id).unwrap();
        assert!(!checkpoint_path(&empty_root, empty_id).join(USAGE_LEDGER_CHECKPOINT).exists());
        usage_ledger(&empty_root);
        write_state(&empty_root, &PersistedState {
            operation: "FAILED".into(),
            phase: "ROLLBACK_FAILED".into(),
            checkpoint_id: Some(empty_id.into()),
            ..PersistedState::default()
        }).unwrap();
        recover_interrupted_switch(&empty_root, &empty_database, "CHAT").unwrap();
        assert!(!usage_ledger_path(&empty_root).exists());
    }

    #[test]
    fn v4_checkpoint_rejects_missing_asset_before_touching_live_owners() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-root");
        fs::create_dir_all(root.join("assets")).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        usage_ledger(&root);
        fs::write(root.join("assets/original.txt"), b"original asset").unwrap();
        let checkpoint_id = "checkpoint-v4-1-1";
        create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        Connection::open(&database_path).unwrap()
            .execute("UPDATE workspaces SET title='current after checkpoint'", []).unwrap();
        Connection::open(usage_ledger_path(&root)).unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=100", []).unwrap();
        fs::write(root.join("assets/original.txt"), b"current asset").unwrap();
        write_state(&root, &PersistedState {
            operation: "FAILED".into(),
            phase: "ROLLBACK_FAILED".into(),
            checkpoint_id: Some(checkpoint_id.into()),
            ..PersistedState::default()
        }).unwrap();
        let before = [
            fs::read(&database_path).unwrap(),
            fs::read(usage_ledger_path(&root)).unwrap(),
            fs::read(root.join("assets/original.txt")).unwrap(),
        ];
        let asset_snapshot = checkpoint_path(&root, checkpoint_id)
            .join("assets/attachments/v1/original.txt");
        let saved_asset = fs::read(&asset_snapshot).unwrap();
        fs::remove_file(&asset_snapshot).unwrap();
        assert!(recover_interrupted_switch(&root, &database_path, "CHAT").is_err());
        assert_eq!(fs::read(&database_path).unwrap(), before[0]);
        assert_eq!(fs::read(usage_ledger_path(&root)).unwrap(), before[1]);
        assert_eq!(fs::read(root.join("assets/original.txt")).unwrap(), before[2]);
        assert_eq!(read_state(&root).unwrap().phase, "ROLLBACK_FAILED");
        fs::write(&asset_snapshot, saved_asset).unwrap();
        fs::write(&asset_snapshot, b"tampered asset").unwrap();
        assert!(recover_interrupted_switch(&root, &database_path, "CHAT").is_err());
        assert_eq!(fs::read(root.join("assets/original.txt")).unwrap(), before[2]);
        fs::write(&asset_snapshot, b"original asset").unwrap();
        let extra = asset_snapshot.parent().unwrap().join("unexpected.txt");
        fs::write(&extra, b"unexpected").unwrap();
        assert!(recover_interrupted_switch(&root, &database_path, "CHAT").is_err());
        assert_eq!(fs::read(&database_path).unwrap(), before[0]);
        fs::remove_file(extra).unwrap();
        recover_interrupted_switch(&root, &database_path, "CHAT").unwrap();
        assert_eq!(fs::read(root.join("assets/original.txt")).unwrap(), b"original asset");
    }

    #[test]
    fn v4_checkpoint_records_originally_absent_asset_root() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-root");
        fs::create_dir_all(&root).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        let checkpoint_id = "checkpoint-v4-1-2";
        create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        let manifest: CheckpointV4Manifest = serde_json::from_slice(&fs::read(
            checkpoint_path(&root, checkpoint_id).join(CHECKPOINT_V4_MANIFEST)
        ).unwrap()).unwrap();
        assert!(manifest.assets.is_empty());
        fs::create_dir_all(root.join("assets")).unwrap();
        fs::write(root.join("assets/new.txt"), b"new after checkpoint").unwrap();
        write_state(&root, &PersistedState {
            operation: "FAILED".into(), phase: "ROLLBACK_FAILED".into(),
            checkpoint_id: Some(checkpoint_id.into()), ..PersistedState::default()
        }).unwrap();
        recover_interrupted_switch(&root, &database_path, "CHAT").unwrap();
        assert!(!root.join("assets").exists());
    }

    #[test]
    fn v4_checkpoint_does_not_report_success_if_asset_disappears_after_preflight() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-root");
        fs::create_dir_all(root.join("assets")).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        fs::write(root.join("assets/original.txt"), b"original asset").unwrap();
        let checkpoint_id = "checkpoint-v4-1-3";
        create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        fs::write(root.join("assets/original.txt"), b"current asset").unwrap();
        let live_database = sha256_file(&database_path).unwrap();
        let live_asset = sha256_file(&root.join("assets/original.txt")).unwrap();
        let snapshot_asset = checkpoint_path(&root, checkpoint_id)
            .join("assets/attachments/v1/original.txt");
        let saved_asset = fs::read(&snapshot_asset).unwrap();
        assert!(restore_checkpoint_with_hook(&root, &database_path, checkpoint_id, || {
            fs::remove_file(&snapshot_asset).unwrap();
            Ok(())
        }).is_err());
        assert!(checkpoint_path(&root, checkpoint_id).exists());
        assert_eq!(sha256_file(&database_path).unwrap(), live_database);
        assert_eq!(sha256_file(&root.join("assets/original.txt")).unwrap(), live_asset);
        fs::write(&snapshot_asset, saved_asset).unwrap();
        assert!(restore_checkpoint_with_hook(&root, &database_path, checkpoint_id, || {
            fs::write(&snapshot_asset, b"changed after preflight").unwrap();
            Ok(())
        }).is_err());
        assert_eq!(sha256_file(&database_path).unwrap(), live_database);
        assert_eq!(sha256_file(&root.join("assets/original.txt")).unwrap(), live_asset);
        fs::write(&snapshot_asset, b"original asset").unwrap();
        restore_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        assert_eq!(fs::read(root.join("assets/original.txt")).unwrap(), b"original asset");
    }

    #[test]
    fn v4_checkpoint_changed_database_ledger_or_manifest_keeps_active_owners() {
        for changed in ["workspace.snapshot", USAGE_LEDGER_CHECKPOINT, CHECKPOINT_V4_MANIFEST] {
            let temp = tempdir().unwrap();
            let root = temp.path().join("synthetic-root");
            fs::create_dir_all(&root).unwrap();
            let database_path = root.join("workspace.sqlite3");
            database(&database_path);
            usage_ledger(&root);
            let checkpoint_id = "checkpoint-v4-1-source-race";
            create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
            Connection::open(&database_path).unwrap()
                .execute("UPDATE workspaces SET title='current'", []).unwrap();
            let ledger = usage_ledger_path(&root);
            Connection::open(&ledger).unwrap()
                .execute("UPDATE usage_ledger_entries SET input_tokens=99", []).unwrap();
            let live_database = sha256_file(&database_path).unwrap();
            let live_ledger = sha256_file(&ledger).unwrap();
            let source = checkpoint_path(&root, checkpoint_id).join(changed);
            let original = fs::read(&source).unwrap();
            let error = restore_checkpoint_with_hook(&root, &database_path, checkpoint_id, || {
                fs::write(&source, b"changed after preflight").unwrap();
                Ok(())
            }).unwrap_err();
            assert!(error.contains("预检后变化"), "{changed}: {error}");
            assert_eq!(sha256_file(&database_path).unwrap(), live_database, "{changed}");
            assert_eq!(sha256_file(&ledger).unwrap(), live_ledger, "{changed}");
            fs::write(&source, original).unwrap();
            restore_checkpoint(&root, &database_path, checkpoint_id).unwrap();
            let restored_title: String = Connection::open(&database_path).unwrap()
                .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
            let restored_tokens: i64 = Connection::open(&ledger).unwrap()
                .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get(0)).unwrap();
            assert_eq!(restored_title, "备份前", "{changed}");
            assert_eq!(restored_tokens, 42, "{changed}");
        }
    }

    #[test]
    fn v4_checkpoint_copy_failure_preserves_all_active_owners() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-root");
        fs::create_dir_all(root.join("assets")).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        usage_ledger(&root);
        let asset = root.join("assets/original.txt");
        fs::write(&asset, b"original asset").unwrap();
        let checkpoint_id = "checkpoint-v4-1-copy-failure";
        create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        Connection::open(&database_path).unwrap()
            .execute("UPDATE workspaces SET title='current'", []).unwrap();
        let ledger = usage_ledger_path(&root);
        Connection::open(&ledger).unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=99", []).unwrap();
        fs::write(&asset, b"current asset").unwrap();
        let before = [&database_path, &ledger, &asset]
            .map(|path| sha256_file(path).unwrap());
        let error = restore_checkpoint_with_copy_hook(
            &root, &database_path, checkpoint_id, || Ok(()),
            || Err(json_error("合成复制失败")),
        ).unwrap_err();
        assert!(error.contains("合成复制失败"));
        assert!(checkpoint_path(&root, checkpoint_id).is_dir());
        assert_eq!([&database_path, &ledger, &asset].map(|path| sha256_file(path).unwrap()), before);
        restore_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        let restored_title: String = Connection::open(&database_path).unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
        assert_eq!(restored_title, "备份前");
    }

    #[test]
    fn v4_checkpoint_interrupted_after_database_publish_retries_stale_stage() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-root");
        fs::create_dir_all(root.join("assets")).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        usage_ledger(&root);
        let asset = root.join("assets/original.txt");
        fs::write(&asset, b"checkpoint asset").unwrap();
        let checkpoint_id = "checkpoint-v4-1-publish-kill";
        create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        Connection::open(&database_path).unwrap()
            .execute("UPDATE workspaces SET title='current'", []).unwrap();
        let ledger = usage_ledger_path(&root);
        Connection::open(&ledger).unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=99", []).unwrap();
        fs::write(&asset, b"current asset").unwrap();
        let interrupted = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = restore_checkpoint_with_publish_hook(
                &root, &database_path, checkpoint_id, || Ok(()), || Ok(()),
                || panic!("synthetic process kill after database publish"),
                || Ok(()), || Ok(()), || Ok(()), || Ok(()), || Ok(()),
            );
        }));
        assert!(interrupted.is_err());
        let stage = checkpoint_path(&root, checkpoint_id).join("rollback-copy-stage");
        assert!(stage.is_dir());
        let title: String = Connection::open(&database_path).unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
        let tokens: i64 = Connection::open(&ledger).unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get(0)).unwrap();
        assert_eq!(title, "备份前");
        assert_eq!(tokens, 99);
        assert_eq!(fs::read(&asset).unwrap(), b"current asset");
        restore_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        assert!(!stage.exists());
        let restored_tokens: i64 = Connection::open(&ledger).unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get(0)).unwrap();
        assert_eq!(restored_tokens, 42);
        assert_eq!(fs::read(&asset).unwrap(), b"checkpoint asset");
    }

    #[test]
    fn v4_checkpoint_interrupted_after_ledger_publish_retries_stale_stage() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-root");
        fs::create_dir_all(root.join("assets")).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        usage_ledger(&root);
        let asset = root.join("assets/original.txt");
        fs::write(&asset, b"checkpoint asset").unwrap();
        let checkpoint_id = "checkpoint-v4-1-ledger-publish-kill";
        create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        Connection::open(&database_path).unwrap()
            .execute("UPDATE workspaces SET title='current'", []).unwrap();
        let ledger = usage_ledger_path(&root);
        Connection::open(&ledger).unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=99", []).unwrap();
        fs::write(&asset, b"current asset").unwrap();
        let interrupted = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = restore_checkpoint_with_publish_hook(
                &root, &database_path, checkpoint_id, || Ok(()), || Ok(()),
                || Ok(()), || panic!("synthetic process kill after ledger publish"),
                || Ok(()), || Ok(()), || Ok(()), || Ok(()),
            );
        }));
        assert!(interrupted.is_err());
        let stage = checkpoint_path(&root, checkpoint_id).join("rollback-copy-stage");
        assert!(stage.is_dir());
        let title: String = Connection::open(&database_path).unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
        let tokens: i64 = Connection::open(&ledger).unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get(0)).unwrap();
        assert_eq!(title, "备份前");
        assert_eq!(tokens, 42);
        assert_eq!(fs::read(&asset).unwrap(), b"current asset");
        restore_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        assert!(!stage.exists());
        assert_eq!(fs::read(&asset).unwrap(), b"checkpoint asset");
    }

    #[test]
    fn v4_checkpoint_interrupted_after_first_asset_clear_retries_missing_destination() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-root");
        fs::create_dir_all(root.join("assets")).unwrap();
        fs::create_dir_all(root.join("packages")).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        usage_ledger(&root);
        let asset = root.join("assets/original.txt");
        let package = root.join("packages/original.txt");
        fs::write(&asset, b"checkpoint asset").unwrap();
        fs::write(&package, b"checkpoint package").unwrap();
        let checkpoint_id = "checkpoint-v4-1-asset-clear-kill";
        create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        Connection::open(&database_path).unwrap()
            .execute("UPDATE workspaces SET title='current'", []).unwrap();
        let ledger = usage_ledger_path(&root);
        Connection::open(&ledger).unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=99", []).unwrap();
        fs::write(&asset, b"current asset").unwrap();
        fs::write(&package, b"current package").unwrap();
        let interrupted = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = restore_checkpoint_with_publish_hook(
                &root, &database_path, checkpoint_id, || Ok(()), || Ok(()),
                || Ok(()), || Ok(()),
                || panic!("synthetic process kill after first asset clear"), || Ok(()),
                || Ok(()), || Ok(()),
            );
        }));
        assert!(interrupted.is_err());
        let stage = checkpoint_path(&root, checkpoint_id).join("rollback-copy-stage");
        assert!(stage.is_dir());
        let title: String = Connection::open(&database_path).unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
        let tokens: i64 = Connection::open(&ledger).unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get(0)).unwrap();
        assert_eq!(title, "备份前");
        assert_eq!(tokens, 42);
        assert!(!root.join("assets").exists());
        let displaced = checkpoint_path(&root, checkpoint_id)
            .join("rollback-displaced-assets/root-0/original.txt");
        assert_eq!(fs::read(&displaced).unwrap(), b"current asset");
        assert_eq!(fs::read(&package).unwrap(), b"current package");
        restore_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        assert!(!stage.exists());
        assert!(!displaced.exists());
        assert_eq!(fs::read(&asset).unwrap(), b"checkpoint asset");
        assert_eq!(fs::read(&package).unwrap(), b"checkpoint package");
    }

    #[cfg(windows)]
    #[test]
    fn v4_checkpoint_locked_asset_rejects_without_deleting_active_tree() {
        use std::os::windows::fs::OpenOptionsExt;
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-root");
        fs::create_dir_all(root.join("assets/nested")).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        usage_ledger(&root);
        let asset = root.join("assets/nested/original.txt");
        fs::write(&asset, b"checkpoint asset").unwrap();
        let checkpoint_id = "checkpoint-v4-1-locked-asset";
        create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        Connection::open(&database_path).unwrap()
            .execute("UPDATE workspaces SET title='current'", []).unwrap();
        let ledger = usage_ledger_path(&root);
        Connection::open(&ledger).unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=99", []).unwrap();
        fs::write(&asset, b"current asset").unwrap();
        let held = fs::OpenOptions::new().read(true).share_mode(0).open(&asset).unwrap();
        assert!(restore_checkpoint(&root, &database_path, checkpoint_id).is_err());
        let title: String = Connection::open(&database_path).unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
        let tokens: i64 = Connection::open(&ledger).unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get(0)).unwrap();
        assert_eq!(title, "备份前");
        assert_eq!(tokens, 42);
        assert!(root.join("assets").is_dir());
        assert!(!checkpoint_path(&root, checkpoint_id)
            .join("rollback-displaced-assets/root-0").exists());
        drop(held);
        assert_eq!(fs::read(&asset).unwrap(), b"current asset");
        restore_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        assert_eq!(fs::read(&asset).unwrap(), b"checkpoint asset");
    }

    #[test]
    fn v4_checkpoint_staged_asset_change_rejects_before_displacing_active_tree() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-root");
        fs::create_dir_all(root.join("assets")).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        usage_ledger(&root);
        let asset = root.join("assets/original.txt");
        fs::write(&asset, b"checkpoint asset").unwrap();
        let checkpoint_id = "checkpoint-v4-1-staged-asset-change";
        create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        fs::write(&asset, b"current asset").unwrap();
        let staged_asset = checkpoint_path(&root, checkpoint_id)
            .join("rollback-copy-stage/assets/attachments/v1/original.txt");
        let result = restore_checkpoint_with_publish_hook(
            &root, &database_path, checkpoint_id, || Ok(()), || Ok(()),
            || Ok(()), || {
                fs::write(&staged_asset, b"changed staged asset").unwrap();
                Ok(())
            }, || Ok(()), || Ok(()), || Ok(()), || Ok(()),
        );
        assert!(result.is_err());
        assert_eq!(fs::read(&asset).unwrap(), b"current asset");
        assert!(!checkpoint_path(&root, checkpoint_id)
            .join("rollback-displaced-assets/root-0").exists());
        restore_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        assert_eq!(fs::read(&asset).unwrap(), b"checkpoint asset");
    }

    #[test]
    fn v4_checkpoint_interrupted_after_first_asset_publish_retries_remaining_assets() {
        let temp = tempdir().unwrap();
        let root = temp.path().join("synthetic-root");
        fs::create_dir_all(root.join("assets")).unwrap();
        fs::create_dir_all(root.join("packages")).unwrap();
        let database_path = root.join("workspace.sqlite3");
        database(&database_path);
        usage_ledger(&root);
        let asset = root.join("assets/original.txt");
        let package = root.join("packages/original.txt");
        fs::write(&asset, b"checkpoint asset").unwrap();
        fs::write(&package, b"checkpoint package").unwrap();
        let checkpoint_id = "checkpoint-v4-1-asset-publish-kill";
        create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        Connection::open(&database_path).unwrap()
            .execute("UPDATE workspaces SET title='current'", []).unwrap();
        let ledger = usage_ledger_path(&root);
        Connection::open(&ledger).unwrap()
            .execute("UPDATE usage_ledger_entries SET input_tokens=99", []).unwrap();
        fs::write(&asset, b"current asset").unwrap();
        fs::write(&package, b"current package").unwrap();
        let interrupted = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = restore_checkpoint_with_publish_hook(
                &root, &database_path, checkpoint_id, || Ok(()), || Ok(()),
                || Ok(()), || Ok(()), || Ok(()),
                || panic!("synthetic process kill after first asset publish"),
                || Ok(()), || Ok(()),
            );
        }));
        assert!(interrupted.is_err());
        let stage = checkpoint_path(&root, checkpoint_id).join("rollback-copy-stage");
        assert!(stage.is_dir());
        let title: String = Connection::open(&database_path).unwrap()
            .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
        let tokens: i64 = Connection::open(&ledger).unwrap()
            .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get(0)).unwrap();
        assert_eq!(title, "备份前");
        assert_eq!(tokens, 42);
        assert_eq!(fs::read(&asset).unwrap(), b"checkpoint asset");
        assert_eq!(fs::read(&package).unwrap(), b"current package");
        let displaced = checkpoint_path(&root, checkpoint_id)
            .join("rollback-displaced-assets/root-0/original.txt");
        assert_eq!(fs::read(&displaced).unwrap(), b"current asset");
        fs::write(&asset, b"tampered after first publish").unwrap();
        assert!(restore_checkpoint(&root, &database_path, checkpoint_id).is_err());
        assert_eq!(fs::read(&asset).unwrap(), b"tampered after first publish");
        assert_eq!(fs::read(&displaced).unwrap(), b"current asset");
        fs::write(&asset, b"checkpoint asset").unwrap();
        restore_checkpoint(&root, &database_path, checkpoint_id).unwrap();
        assert!(!stage.exists());
        assert!(!displaced.exists());
        assert_eq!(fs::read(&asset).unwrap(), b"checkpoint asset");
        assert_eq!(fs::read(&package).unwrap(), b"checkpoint package");
    }

    #[test]
    fn v4_checkpoint_interrupted_during_second_asset_switch_keeps_both_old_roots() {
        for after_clear in [true, false] {
            let temp = tempdir().unwrap();
            let root = temp.path().join("synthetic-root");
            fs::create_dir_all(root.join("assets")).unwrap();
            fs::create_dir_all(root.join("packages")).unwrap();
            let database_path = root.join("workspace.sqlite3");
            database(&database_path);
            usage_ledger(&root);
            let asset = root.join("assets/original.txt");
            let package = root.join("packages/original.txt");
            fs::write(&asset, b"checkpoint asset").unwrap();
            fs::write(&package, b"checkpoint package").unwrap();
            let checkpoint_id = "checkpoint-v4-1-second-asset-kill";
            create_checkpoint(&root, &database_path, checkpoint_id).unwrap();
            Connection::open(&database_path).unwrap()
                .execute("UPDATE workspaces SET title='current'", []).unwrap();
            let ledger = usage_ledger_path(&root);
            Connection::open(&ledger).unwrap()
                .execute("UPDATE usage_ledger_entries SET input_tokens=99", []).unwrap();
            fs::write(&asset, b"current asset").unwrap();
            fs::write(&package, b"current package").unwrap();
            let interrupted = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                let _ = restore_checkpoint_with_publish_hook(
                    &root, &database_path, checkpoint_id, || Ok(()), || Ok(()),
                    || Ok(()), || Ok(()), || Ok(()), || Ok(()),
                    || { if after_clear { panic!("synthetic kill after second asset clear") } else { Ok(()) } },
                    || { if !after_clear { panic!("synthetic kill after second asset publish") } else { Ok(()) } },
                );
            }));
            assert!(interrupted.is_err());
            let checkpoint = checkpoint_path(&root, checkpoint_id);
            assert!(checkpoint.join("rollback-copy-stage").is_dir());
            assert_eq!(fs::read(&asset).unwrap(), b"checkpoint asset");
            assert_eq!(fs::read(checkpoint.join("rollback-displaced-assets/root-0/original.txt")).unwrap(), b"current asset");
            assert_eq!(fs::read(checkpoint.join("rollback-displaced-assets/root-1/original.txt")).unwrap(), b"current package");
            if after_clear {
                assert!(!root.join("packages").exists());
            } else {
                assert_eq!(fs::read(&package).unwrap(), b"checkpoint package");
                fs::write(&package, b"tampered after second publish").unwrap();
                assert!(restore_checkpoint(&root, &database_path, checkpoint_id).is_err());
                assert_eq!(fs::read(checkpoint.join("rollback-displaced-assets/root-1/original.txt")).unwrap(), b"current package");
                fs::write(&package, b"checkpoint package").unwrap();
            }
            restore_checkpoint(&root, &database_path, checkpoint_id).unwrap();
            assert!(!checkpoint.join("rollback-copy-stage").exists());
            assert!(!checkpoint.join("rollback-displaced-assets").exists());
            assert_eq!(fs::read(&asset).unwrap(), b"checkpoint asset");
            assert_eq!(fs::read(&package).unwrap(), b"checkpoint package");
            let title: String = Connection::open(&database_path).unwrap()
                .query_row("SELECT title FROM workspaces", [], |row| row.get(0)).unwrap();
            let tokens: i64 = Connection::open(&ledger).unwrap()
                .query_row("SELECT input_tokens FROM usage_ledger_entries", [], |row| row.get(0)).unwrap();
            assert_eq!(title, "备份前");
            assert_eq!(tokens, 42);
        }
    }

    #[test]
    fn interrupted_schema45_chat_restores_even_with_work_area_but_legacy_is_blocked() {
        let temp = tempdir().unwrap();
        let app_root = temp.path().join("root");
        let work_root = app_root.join("work-area");
        fs::create_dir_all(&work_root).unwrap();
        let chat_database = app_root.join("workspace.sqlite3");
        let work_database = work_root.join("workspace.sqlite3");
        database(&chat_database);
        database(&work_database);
        Connection::open(&chat_database)
            .unwrap()
            .execute_batch("PRAGMA user_version=45; CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'CHAT',1);")
            .unwrap();
        let work_before = fs::read(&work_database).unwrap();
        create_checkpoint(&app_root, &chat_database, "chat-checkpoint").unwrap();
        write_state(
            &app_root,
            &PersistedState {
                operation: "INTERRUPTED".into(),
                phase: "SWITCHING".into(),
                checkpoint_id: Some("chat-checkpoint".into()),
                ..PersistedState::default()
            },
        )
        .unwrap();
        fs::remove_file(&chat_database).unwrap();
        recover_interrupted_switch(&app_root, &chat_database, "CHAT").unwrap();
        assert_eq!(schema_version(&chat_database).unwrap(), 45);
        assert_eq!(
            read_state(&app_root).unwrap().phase,
            "ROLLED_BACK_INTERRUPTED"
        );
        assert_eq!(fs::read(&work_database).unwrap(), work_before);

        let legacy_root = temp.path().join("legacy");
        fs::create_dir_all(legacy_root.join("work-area")).unwrap();
        let legacy_database = legacy_root.join("workspace.sqlite3");
        database(&legacy_database);
        create_checkpoint(&legacy_root, &legacy_database, "legacy-checkpoint").unwrap();
        write_state(
            &legacy_root,
            &PersistedState {
                operation: "INTERRUPTED".into(),
                phase: "SWITCHING".into(),
                checkpoint_id: Some("legacy-checkpoint".into()),
                ..PersistedState::default()
            },
        )
        .unwrap();
        fs::remove_file(&legacy_database).unwrap();
        assert!(
            recover_interrupted_switch(&legacy_root, &legacy_database, "CHAT")
                .unwrap_err()
                .contains("旧单区恢复不能覆盖")
        );
        assert!(!legacy_database.exists());
    }
}


#[cfg(test)]
mod usage_ledger_backup_schema_tests {
    use super::*;
    #[test]
    fn a9_answer_snapshot_keeps_physical_call_facts_separate_from_portable_history() {
        use crate::usage_ledger_v1::{Ledger,Entry,Kind,FactGrade,Source,AppendResult};
        let fixture:serde_json::Value=serde_json::from_str(include_str!("../../../protocol/fixtures/answer-model-facts-v1.json")).unwrap();
        let facts=&fixture["cases"][2]["expectedPortable"];
        let root=tempfile::tempdir().unwrap();let source=root.path().join("source");let ledger=Ledger::open(&source).unwrap();
        let entry=Entry{entry_id:"a9-physical-call".into(),replay_token:"a9-physical-call".into(),execution_id:"a9-execution".into(),
            conversation_id:"a9-conversation".into(),branch_leaf_message_id:"a9-answer".into(),invocation_id:"a9-invocation".into(),attempt_id:"a9-attempt".into(),
            kind:Kind::FinalMeasured,fact_grade:FactGrade::ProviderReported,requested_model_id:facts["modelId"].as_str().unwrap().into(),actual_model_id:Some(facts["modelId"].as_str().unwrap().into()),
            input_tokens:facts["inputTokens"].as_i64(),output_tokens:facts["outputTokens"].as_i64(),cached_input_tokens:facts["cachedInputTokens"].as_i64(),charge_micros:facts["costTotalMicros"].as_i64(),
            currency_code:Some(facts["costCurrencyCode"].as_str().unwrap().into()),cost_source:Some(facts["costSource"].as_str().unwrap().into()),budget_micros:None,adjustment_micros:None,
            reconciliation_fingerprint:None,reconciles_entry_id:None,source:Source::DesktopLocal,occurred_at_ms:1};
        let metadata=crate::chat_call_accounting_v1::Metadata{role:"ANSWER".into(),model_display_name:facts["modelDisplayName"].as_str().unwrap().into(),
            reasoning_tokens:facts["reasoningTokens"].as_i64(),price_version:Some(facts["costPriceVersion"].as_str().unwrap().into()),..Default::default()};
        assert!(matches!(ledger.append_call(&entry,&metadata).unwrap(),AppendResult::Appended(_)));
        let saved=root.path().join("restored");fs::create_dir_all(&saved).unwrap();let snapshot=saved.join("usage-ledger-v1.sqlite3");
        snapshot_database(&source.join("usage-ledger-v1.sqlite3"),&snapshot).unwrap();validate_usage_ledger(&snapshot).unwrap();
        let restored=Ledger::open(&saved).unwrap();assert_eq!(restored.entry(&entry.entry_id).unwrap(),Some(entry.clone()));
        assert_eq!(restored.call_metadata(&entry.entry_id).unwrap(),Some(metadata));assert_eq!(restored.recover_calls().unwrap(),0);
        assert_eq!(ledger.entry(&entry.entry_id).unwrap(),Some(entry));
    }
    #[test]
    fn snapshot_retains_received_measurement_and_restored_owner_recovers_it_once() {
        use crate::usage_ledger_v1::{Ledger,Entry,Kind,FactGrade,Source};
        let root=tempfile::tempdir().unwrap();let source=root.path().join("source");let ledger=Ledger::open(&source).unwrap();
        let entry=Entry {entry_id:"synthetic-backup-call".into(),replay_token:"synthetic-backup-call".into(),
            execution_id:"synthetic-execution".into(),conversation_id:"synthetic-conversation".into(),branch_leaf_message_id:"synthetic-branch".into(),
            invocation_id:"synthetic-invocation".into(),attempt_id:"synthetic-attempt".into(),kind:Kind::FinalMeasured,fact_grade:FactGrade::ProviderReported,
            requested_model_id:"openai/gpt-6-luna".into(),actual_model_id:Some("openai/gpt-6-luna".into()),input_tokens:Some(5),output_tokens:Some(2),cached_input_tokens:None,
            charge_micros:Some(4),currency_code:Some("USD".into()),cost_source:Some("PROVIDER_RESPONSE".into()),budget_micros:None,adjustment_micros:None,
            reconciliation_fingerprint:None,reconciles_entry_id:None,source:Source::DesktopLocal,occurred_at_ms:1};
        let metadata=crate::chat_call_accounting_v1::Metadata{role:"RETRIEVAL".into(),model_display_name:"GPT-6 Luna · OpenRouter".into(),..Default::default()};
        let path=source.join("usage-ledger-v1.sqlite3");let fault=Connection::open(&path).unwrap();
        fault.execute_batch("CREATE TRIGGER reject_final BEFORE INSERT ON usage_ledger_entries BEGIN SELECT RAISE(ABORT,'synthetic snapshot fault'); END;").unwrap();drop(fault);
        assert!(ledger.append_recoverable_call(&entry,&metadata).is_err());validate_usage_ledger(&path).unwrap();
        let restored=root.path().join("restored");fs::create_dir_all(&restored).unwrap();
        let restored_path=restored.join("usage-ledger-v1.sqlite3");snapshot_database(&path,&restored_path).unwrap();
        let connection=Connection::open(&restored_path).unwrap();connection.execute_batch("DROP TRIGGER reject_final;").unwrap();drop(connection);
        let recovered=Ledger::open(&restored).unwrap();assert_eq!(recovered.entry(&entry.entry_id).unwrap(),Some(entry.clone()));
        assert_eq!(recovered.call_metadata(&entry.entry_id).unwrap(),Some(metadata));assert_eq!(recovered.recover_calls().unwrap(),0);
        assert!(ledger.entry(&entry.entry_id).unwrap().is_none());
    }
    #[test]
    fn backup_accepts_known_ledger_versions_read_only_and_preserves_recovery_receipts() {
        let root=tempfile::tempdir().unwrap();let source=root.path().join("source");
        drop(crate::usage_ledger_v1::Ledger::open(&source).unwrap());
        let path=source.join("usage-ledger-v1.sqlite3");let before=sha256_file(&path).unwrap();
        validate_usage_ledger(&path).unwrap();assert_eq!(sha256_file(&path).unwrap(),before);
        let target=root.path().join("restored.sqlite3");snapshot_database(&path,&target).unwrap();
        let target_connection=Connection::open(&target).unwrap();
        assert!(target_connection.prepare("SELECT entry_json,metadata_json FROM usage_ledger_call_recovery LIMIT 0").is_ok());
        let malformed=Connection::open(&path).unwrap();
        malformed.execute("INSERT INTO usage_ledger_call_recovery(entry_id,entry_json,metadata_json) VALUES('bad','{}','{}')",[]).unwrap();
        let original=sha256_file(&path).unwrap();assert!(validate_usage_ledger(&path).is_err());assert_eq!(sha256_file(&path).unwrap(),original);
        malformed.execute("DELETE FROM usage_ledger_call_recovery WHERE entry_id='bad'",[]).unwrap();drop(malformed);
        for version in [2,1] {
            let connection=Connection::open(&path).unwrap();connection.pragma_update(None,"user_version",version).unwrap();drop(connection);
            let original=sha256_file(&path).unwrap();validate_usage_ledger(&path).unwrap();assert_eq!(sha256_file(&path).unwrap(),original);
        }
        for version in [0,99] {
            let connection=Connection::open(&path).unwrap();connection.pragma_update(None,"user_version",version).unwrap();drop(connection);
            let original=sha256_file(&path).unwrap();assert!(validate_usage_ledger(&path).is_err());assert_eq!(sha256_file(&path).unwrap(),original);
        }
        let connection=Connection::open(&path).unwrap();
        connection.execute_batch("PRAGMA user_version=3;DROP TABLE usage_ledger_call_recovery;").unwrap();
        assert!(validate_usage_ledger(&path).is_err());
        connection.execute_batch("PRAGMA user_version=2;DROP TABLE usage_ledger_call_metadata;").unwrap();
        assert!(validate_usage_ledger(&path).is_err());
    }
}

#[cfg(test)]
mod retrieval_progress_backup_tests {
    use super::*;
    #[test]
    fn backup_preserves_current_progress_table_and_refuses_malformed_snapshot_read_only() {
        let root=tempfile::tempdir().unwrap();let source=root.path().join("source");drop(crate::usage_ledger_v1::Ledger::open(&source).unwrap());
        let path=source.join("usage-ledger-v1.sqlite3");assert_eq!(schema_version(&path).unwrap(),6);let original=sha256_file(&path).unwrap();validate_usage_ledger(&path).unwrap();assert_eq!(sha256_file(&path).unwrap(),original);
        let restored=root.path().join("restored.sqlite3");snapshot_database(&path,&restored).unwrap();let c=Connection::open(&restored).unwrap();assert!(c.prepare("SELECT pending_id,entry_id,entry_json,metadata_json,interrupted FROM usage_ledger_retrieval_progress LIMIT 0").is_ok());drop(c);
        let c=Connection::open(&path).unwrap();c.execute_batch("PRAGMA foreign_keys=OFF;").unwrap();c.execute("INSERT INTO usage_ledger_retrieval_progress VALUES('missing','bad','{}','{}',0)",[]).unwrap();drop(c);let before=sha256_file(&path).unwrap();assert!(validate_usage_ledger(&path).is_err());assert_eq!(sha256_file(&path).unwrap(),before);
    }
    #[test]
    fn backup_restores_received_retrieval_snapshot_without_finalizing_or_changing_its_source() {
        use crate::usage_ledger_v1::{Ledger,Entry,Kind,FactGrade,Source};
        let root=tempfile::tempdir().unwrap();let source=root.path().join("source");let ledger=Ledger::open(&source).unwrap();
        let pending=Entry{entry_id:"pending-child".into(),replay_token:"pending-child".into(),execution_id:"parent".into(),conversation_id:"conversation".into(),branch_leaf_message_id:"assistant".into(),invocation_id:"child".into(),attempt_id:"child".into(),kind:Kind::StreamPending,fact_grade:FactGrade::Estimated,requested_model_id:"openai/gpt-6.1-sol".into(),actual_model_id:None,input_tokens:None,output_tokens:None,cached_input_tokens:None,charge_micros:None,budget_micros:None,adjustment_micros:None,currency_code:None,cost_source:None,reconciliation_fingerprint:Some("a".repeat(64)),reconciles_entry_id:None,source:Source::DesktopLocal,occurred_at_ms:7};
        let metadata=crate::chat_call_accounting_v1::Metadata{role:"RETRIEVAL".into(),model_display_name:"Sol".into(),..Default::default()};ledger.append_call(&pending,&metadata).unwrap();
        let mut measured=pending.clone();measured.entry_id="final-child".into();measured.replay_token=measured.entry_id.clone();measured.kind=Kind::FinalMeasured;measured.fact_grade=FactGrade::ProviderReported;measured.reconciles_entry_id=Some(pending.entry_id);measured.input_tokens=Some(17);measured.output_tokens=Some(0);measured.cached_input_tokens=Some(3);
        ledger.checkpoint_retrieval(&measured,&metadata).unwrap();drop(ledger);
        let path=source.join("usage-ledger-v1.sqlite3");let before=sha256_file(&path).unwrap();validate_usage_ledger(&path).unwrap();
        let restored=root.path().join("restored");fs::create_dir_all(&restored).unwrap();snapshot_database(&path,&restored.join("usage-ledger-v1.sqlite3")).unwrap();assert_eq!(sha256_file(&path).unwrap(),before);
        let restored=Ledger::open(&restored).unwrap();assert_eq!(restored.retrieval_progress().unwrap(),vec![(measured.clone(),metadata,false)]);assert!(restored.entry(&measured.entry_id).unwrap().is_none());assert_eq!(restored.display_entries().unwrap()[0].kind,Kind::StreamPending);
        restored.recover_interrupted_retrieval(&measured.entry_id).unwrap();assert_eq!(restored.entry(&measured.entry_id).unwrap(),Some(measured));assert_eq!(sha256_file(&path).unwrap(),before);
    }
}
