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
const USAGE_LEDGER_SCHEMA: u32 = 1;
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
    "desktop_attachment_staging",
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
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PersistedState {
    operation: String,
    phase: String,
    fingerprint: Option<String>,
    checkpoint_id: Option<String>,
    process_id: Option<u32>,
}

impl Default for PersistedState {
    fn default() -> Self {
        Self {
            operation: "IDLE".into(),
            phase: "NONE".into(),
            fingerprint: None,
            checkpoint_id: None,
            process_id: None,
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

fn read_state(root: &Path) -> PersistedState {
    fs::read(state_path(root))
        .ok()
        .and_then(|bytes| serde_json::from_slice(&bytes).ok())
        .unwrap_or_default()
}

fn sync_file(path: &Path) -> Result<(), String> {
    fs::OpenOptions::new()
        .read(true)
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

fn sanitize_snapshot(path: &Path) -> Result<(), String> {
    let connection = Connection::open(path).map_err(|_| json_error("数据库快照不可读"))?;
    connection
        .pragma_update(None, "foreign_keys", "OFF")
        .map_err(|_| json_error("数据库快照无法进入安全清理模式"))?;
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
    Ok(())
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
        || lower.contains("sk-") && value.len() >= 24
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
    for controlled in CONTROLLED_ROOTS {
        let source = root.join(controlled.local);
        if !source.exists() {
            continue;
        }
        let source = source
            .canonicalize()
            .map_err(|_| json_error("本机受控资产根不可读"))?;
        let mut pending = vec![source.clone()];
        while let Some(directory) = pending.pop() {
            for entry in fs::read_dir(&directory).map_err(|_| json_error("本机受控资产不可读"))?
            {
                let entry = entry.map_err(|_| json_error("本机受控资产不可读"))?;
                let metadata = fs::symlink_metadata(entry.path())
                    .map_err(|_| json_error("本机受控资产不可读"))?;
                if metadata.file_type().is_symlink() {
                    return Err(json_error("本机受控资产包含符号链接，已拒绝整个备份"));
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

fn create_package(
    root: &Path,
    snapshot: &Path,
    package: &Path,
    app_version: &str,
    auxiliary_sources: Vec<(String, PathBuf)>,
) -> Result<BackupManifest, String> {
    let mut sources = vec![(DATABASE_ENTRY.to_owned(), snapshot.to_path_buf())];
    sources.extend(auxiliary_sources);
    sources.extend(collect_controlled_files(root)?);
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
        || manifest.schema_version != expected_schema
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
    if !sqlite_healthy(path)?
        || schema_version(path)? != USAGE_LEDGER_SCHEMA
        || !table_counts(path)?.contains_key("usage_ledger_entries")
        || contains_sensitive_values(path)?
    {
        return Err(json_error("费用账本完整性、Schema 或保密边界不匹配"));
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
    if !source.exists() {
        return Ok(());
    }
    let metadata = fs::symlink_metadata(source).map_err(|_| json_error("恢复资产不可读"))?;
    if metadata.file_type().is_symlink() {
        return Err(json_error("恢复资产包含符号链接"));
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
        copy_tree(
            &root.join(controlled.local),
            &checkpoint.join("assets").join(controlled.package),
        )?;
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
    let columns = table_columns(&destination_connection, table)?;
    let source_columns = table_columns(&source_connection, table)?;
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

fn restore_checkpoint(root: &Path, database: &Path, checkpoint_id: &str) -> Result<(), String> {
    let checkpoint = checkpoint_path(root, checkpoint_id);
    let snapshot = checkpoint.join("workspace.snapshot");
    if !snapshot.is_file() || !sqlite_healthy(&snapshot)? {
        return Err(json_error("恢复 checkpoint 不可用"));
    }
    clear_path(database)?;
    fs::copy(&snapshot, database).map_err(|_| json_error("checkpoint 数据库回滚失败"))?;
    sync_file(database)?;
    let usage_ledger = usage_ledger_path(root);
    clear_path(&usage_ledger)?;
    let usage_snapshot = checkpoint.join(USAGE_LEDGER_CHECKPOINT);
    if usage_snapshot.is_file() {
        if let Some(parent) = usage_ledger.parent() {
            fs::create_dir_all(parent).map_err(|_| json_error("费用账本回滚目录不可用"))?;
        }
        fs::copy(&usage_snapshot, &usage_ledger)
            .map_err(|_| json_error("checkpoint 费用账本回滚失败"))?;
        sync_file(&usage_ledger)?;
    }
    for controlled in CONTROLLED_ROOTS {
        let destination = root.join(controlled.local);
        clear_path(&destination)?;
        copy_tree(
            &checkpoint.join("assets").join(controlled.package),
            &destination,
        )?;
    }
    Ok(())
}

fn switch_candidate(root: &Path, database: &Path, stage: &Path) -> Result<(), String> {
    let candidate = stage.join(DATABASE_ENTRY);
    let switch = owner_root(root).join(unique_id("switch-old"));
    fs::create_dir_all(&switch).map_err(|_| json_error("无法创建恢复切换隔离区"))?;
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
    if requires_restart(root) {
        return Err(json_error("本机恢复已完成，请完全重启 App"));
    }
    let selected = selected_backup_path(selected_path, false)?;
    let stage = owner_root(root).join(unique_id("export"));
    fs::create_dir_all(&stage).map_err(|_| json_error("无法创建备份 staging"))?;
    let result = (|| {
        let snapshot = stage.join("nanfeng-ai.snapshot");
        snapshot_database(database, &snapshot)?;
        sanitize_snapshot(&snapshot)?;
        let mut auxiliary_sources = Vec::new();
        let usage_ledger = usage_ledger_path(root);
        if usage_ledger.is_file() {
            let usage_snapshot = stage.join("desktop-usage-ledger-v1.snapshot");
            snapshot_database(&usage_ledger, &usage_snapshot)?;
            validate_usage_ledger(&usage_snapshot)?;
            auxiliary_sources.push((USAGE_LEDGER_ENTRY.to_owned(), usage_snapshot));
        }
        let package = stage.join("backup.nfai-backup");
        let manifest = create_package(root, &snapshot, &package, app_version, auxiliary_sources)?;
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
    if requires_restart(root) {
        return Err(json_error("本机恢复已完成，请完全重启 App"));
    }
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
            || schema_version(&candidate)? != current_schema
            || table_counts(&candidate)? != verified.preflight.table_counts
            || contains_sensitive_values(&candidate)?
        {
            return Err(json_error("候选数据库完整性、Schema、计数或保密边界不匹配"));
        }
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
    if requires_restart(root) {
        return Err(json_error("本机恢复已完成，请完全重启 App"));
    }
    let current_schema = schema_version(database)?;
    let inbox = inbox_path(root);
    let verified = verify_package(&inbox, current_schema)?;
    let persisted = read_state(root);
    if persisted.fingerprint.as_deref() != Some(fingerprint)
        || verified.preflight.fingerprint != fingerprint
    {
        return Err(json_error("备份包或本地预检已变化，请重新选择并预检"));
    }
    if is_business_non_empty(database, root)? && !replace_local {
        return Err(json_error("本地已有数据：请明确选择替换本地或取消"));
    }
    let restore_id = unique_id("restore");
    let stage = owner_root(root).join(&restore_id);
    extract_verified(&inbox, &stage, &verified)?;
    let candidate = stage.join(DATABASE_ENTRY);
    if !sqlite_healthy(&candidate)?
        || schema_version(&candidate)? != current_schema
        || table_counts(&candidate)? != verified.preflight.table_counts
        || contains_sensitive_values(&candidate)?
    {
        let _ = fs::remove_dir_all(stage);
        return Err(json_error("候选数据库完整性、Schema、计数或保密边界不匹配"));
    }
    validate_optional_usage_ledger(&stage)?;
    preserve_device_state(database, &candidate)?;
    let checkpoint_id = unique_id("checkpoint");
    create_checkpoint(root, database, &checkpoint_id)?;
    write_state(
        root,
        &PersistedState {
            operation: "INTERRUPTED".into(),
            phase: "CHECKPOINTED".into(),
            fingerprint: Some(fingerprint.into()),
            checkpoint_id: Some(checkpoint_id.clone()),
            process_id: Some(std::process::id()),
        },
    )?;
    let mut switching = read_state(root);
    switching.phase = "SWITCHING".into();
    write_state(root, &switching)?;
    if let Err(error) = switch_candidate(root, database, &stage) {
        let rollback = restore_checkpoint(root, database, &checkpoint_id);
        let mut failed = read_state(root);
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
        },
    ) {
        let rollback = restore_checkpoint(root, database, &checkpoint_id);
        let mut failed = read_state(root);
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
    let state = read_state(root);
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
        },
    )
}

pub fn requires_restart(root: &Path) -> bool {
    let state = read_state(root);
    state.operation == "RESTORED_RESTART_REQUIRED" && state.process_id == Some(std::process::id())
}

pub fn pending_fresh_restart(root: &Path) -> bool {
    let state = read_state(root);
    state.operation == "RESTORED_RESTART_REQUIRED" && state.process_id != Some(std::process::id())
}

/// Called before opening the ordinary store. A crash during the switch never continues the
/// candidate restore; it only rolls back to the already-fsynced checkpoint and remains visible
/// as INTERRUPTED so the user may explicitly retry or cancel.
pub fn recover_interrupted_switch(root: &Path, database: &Path) -> Result<(), String> {
    let mut state = read_state(root);
    if state.operation != "INTERRUPTED" || state.phase != "SWITCHING" {
        return Ok(());
    }
    let checkpoint_id = state
        .checkpoint_id
        .clone()
        .ok_or_else(|| json_error("中断恢复缺少 checkpoint"))?;
    restore_checkpoint(root, database, &checkpoint_id)?;
    state.phase = "ROLLED_BACK_INTERRUPTED".into();
    write_state(root, &state)
}

/// Called only after the fresh process has successfully reopened and migrated the restored DB.
pub fn finalize_fresh_restart(root: &Path) -> Result<(), String> {
    let mut state = read_state(root);
    if state.operation != "RESTORED_RESTART_REQUIRED"
        || state.process_id == Some(std::process::id())
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
    write_state(root, &state)
}

pub fn read_status(root: &Path, database: &Path) -> Result<BackupStatus, String> {
    let state = read_state(root);
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
        "INTERRUPTED" => Some("上次恢复已中断，未自动继续；可重新确认重试或取消此次恢复。".into()),
        _ => None,
    };
    Ok(BackupStatus {
        operation: state.operation.clone(),
        preflight,
        notice,
        restart_required: state.operation == "RESTORED_RESTART_REQUIRED",
        interrupted: state.operation == "INTERRUPTED",
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

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
}
