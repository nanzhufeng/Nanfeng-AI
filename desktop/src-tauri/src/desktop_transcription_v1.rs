//! Persistent Desktop speech-transcription owner.
//!
//! GLM-OCR and speech transcription reuse the existing workspace attachment and credential
//! owners while keeping task state, recovery checkpoints and exports in the main workspace
//! database so local backup and full-local deletion remain truthful.

use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use rusqlite::{params, Connection, OptionalExtension, Transaction};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    fs,
    io::{Read, Write},
    path::{Path, PathBuf},
    process::Command,
    time::{SystemTime, UNIX_EPOCH},
};
use zip::{write::SimpleFileOptions, CompressionMethod, ZipWriter};

pub const QWEN_MODEL_ID: &str = "qwen3-asr-flash";
pub const SENSEVOICE_MODEL_ID: &str = "sensevoice-small-int8";
pub const GLM_OCR_MODEL_ID: &str = "glm-ocr";
const TRANSCRIPTION_WORKSPACE_ID: &str = "workspace-transcription-local";
const QWEN_ENDPOINT: &str = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
const GLM_OCR_ENDPOINT: &str = "https://open.bigmodel.cn/api/paas/v4/layout_parsing";
const MAX_SOURCE_BYTES: u64 = 2 * 1024 * 1024 * 1024;
const GLM_OCR_IMAGE_MAX_BYTES: u64 = 10 * 1024 * 1024;
const GLM_OCR_PDF_MAX_BYTES: u64 = 50 * 1024 * 1024;
const MAX_DURATION_MILLIS: u64 = 12 * 60 * 60 * 1000;
const CHUNK_MILLIS: u64 = 5 * 60 * 1000;
const QWEN_ESTIMATED_MICRO_CNY_PER_SECOND: u64 = 220;

struct GlmOcrJsonBody {
    prefix: std::io::Cursor<Vec<u8>>,
    source: fs::File,
    encoded: std::io::Cursor<Vec<u8>>,
    suffix: std::io::Cursor<Vec<u8>>,
    phase: u8,
}

impl GlmOcrJsonBody {
    fn new(source: fs::File, mime_type: &str) -> Self {
        Self {
            prefix: std::io::Cursor::new(
                format!("{{\"model\":\"glm-ocr\",\"file\":\"data:{mime_type};base64,").into_bytes(),
            ),
            source,
            encoded: std::io::Cursor::new(Vec::new()),
            suffix: std::io::Cursor::new(b"\"}".to_vec()),
            phase: 0,
        }
    }
}

impl Read for GlmOcrJsonBody {
    fn read(&mut self, output: &mut [u8]) -> std::io::Result<usize> {
        let mut written = 0;
        while written < output.len() && self.phase < 3 {
            let count = match self.phase {
                0 => self.prefix.read(&mut output[written..])?,
                1 => {
                    let count = self.encoded.read(&mut output[written..])?;
                    if count > 0 {
                        count
                    } else {
                        let mut raw = [0u8; 12_288];
                        let mut raw_count = 0;
                        while raw_count < raw.len() {
                            let next = self.source.read(&mut raw[raw_count..])?;
                            if next == 0 {
                                break;
                            }
                            raw_count += next;
                        }
                        if raw_count == 0 {
                            self.phase = 2;
                            continue;
                        }
                        self.encoded =
                            std::io::Cursor::new(BASE64.encode(&raw[..raw_count]).into_bytes());
                        continue;
                    }
                }
                2 => self.suffix.read(&mut output[written..])?,
                _ => 0,
            };
            if count == 0 {
                self.phase += 1;
            } else {
                written += count;
            }
        }
        Ok(written)
    }
}

const SUPPORTED_EXTENSIONS: [&str; 17] = [
    "aac", "flac", "m4a", "mp3", "ogg", "opus", "wav", "wma", "3gp", "avi", "m4v", "mkv", "mov",
    "mp4", "mpeg", "mpg", "webm",
];

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportArgs {
    pub workspace_id: String,
    pub selected_path: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveSettingsArgs {
    pub model_id: String,
    pub language_code: Option<String>,
    pub output_format: String,
    pub expected_revision: u64,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExportArgs {
    pub task_id: String,
    pub selected_path: String,
    pub format: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SettingsProjection {
    pub model_id: String,
    pub language_code: Option<String>,
    pub output_format: String,
    pub revision: u64,
    pub sense_voice_available: bool,
    pub sense_voice_status: &'static str,
    pub source_max_bytes: u64,
    pub source_max_duration_millis: u64,
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct SegmentProjection {
    pub ordinal: u64,
    pub start_millis: u64,
    pub end_millis: u64,
    pub text: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TaskProjection {
    pub id: String,
    pub workspace_id: String,
    pub source_attachment_id: String,
    pub source_mime_type: String,
    pub source_display_name: String,
    pub source_byte_count: u64,
    pub model_id: String,
    pub language_code: Option<String>,
    pub state: String,
    pub progress_millis: u64,
    pub total_duration_millis: Option<u64>,
    pub error_code: Option<String>,
    pub user_message: Option<String>,
    pub technical_detail: Option<String>,
    pub attempt_count: u64,
    pub result_attachment_id: Option<String>,
    pub provider_request_count: u64,
    pub provider_request_id: Option<String>,
    pub page_count: Option<u64>,
    pub billable_audio_millis: u64,
    pub input_tokens: Option<u64>,
    pub output_tokens: Option<u64>,
    pub estimated_charge_micros: u64,
    pub created_at_millis: i64,
    pub updated_at_millis: i64,
    pub segments: Vec<SegmentProjection>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StateProjection {
    pub settings: SettingsProjection,
    pub tasks: Vec<TaskProjection>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExportReceipt {
    pub format: String,
    pub byte_count: u64,
    pub sha256: String,
}

pub fn migrate(connection: &Connection) -> Result<(), String> {
    connection.execute_batch(
        "CREATE TABLE IF NOT EXISTS desktop_transcription_settings (
            id INTEGER PRIMARY KEY CHECK(id=1),
            model_id TEXT NOT NULL,
            language_code TEXT,
            output_format TEXT NOT NULL,
            revision INTEGER NOT NULL,
            updated_at_ms INTEGER NOT NULL
        );
        INSERT OR IGNORE INTO desktop_transcription_settings(id,model_id,language_code,output_format,revision,updated_at_ms)
            VALUES(1,'qwen3-asr-flash',NULL,'md',0,0);
        CREATE TABLE IF NOT EXISTS desktop_transcription_tasks (
            id TEXT PRIMARY KEY NOT NULL,
            workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
            source_attachment_id TEXT NOT NULL,
            source_sha256 TEXT NOT NULL,
            source_mime_type TEXT NOT NULL,
            source_display_name TEXT NOT NULL,
            source_byte_count INTEGER NOT NULL,
            model_id TEXT NOT NULL,
            language_code TEXT,
            state TEXT NOT NULL,
            progress_millis INTEGER NOT NULL DEFAULT 0,
            total_duration_millis INTEGER,
            error_code TEXT,
            user_message TEXT,
            technical_detail TEXT,
            attempt_count INTEGER NOT NULL DEFAULT 0,
            result_attachment_id TEXT,
            result_sha256 TEXT,
            result_byte_count INTEGER,
            provider_request_count INTEGER NOT NULL DEFAULT 0,
            provider_request_id TEXT,
            page_count INTEGER,
            billable_audio_millis INTEGER NOT NULL DEFAULT 0,
            input_tokens INTEGER,
            output_tokens INTEGER,
            estimated_charge_micros INTEGER NOT NULL DEFAULT 0,
            created_at_ms INTEGER NOT NULL,
            updated_at_ms INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS desktop_transcription_tasks_workspace_time ON desktop_transcription_tasks(workspace_id,created_at_ms DESC);
        CREATE TABLE IF NOT EXISTS desktop_transcription_segments (
            task_id TEXT NOT NULL REFERENCES desktop_transcription_tasks(id) ON DELETE CASCADE,
            ordinal INTEGER NOT NULL,
            start_millis INTEGER NOT NULL,
            end_millis INTEGER NOT NULL,
            text TEXT NOT NULL,
            PRIMARY KEY(task_id,ordinal)
        );",
    )
    .map_err(|_| "语音转写数据库迁移失败".to_owned())?;
    for (name, definition) in [("provider_request_id", "TEXT"), ("page_count", "INTEGER")] {
        let present: i64 = connection.query_row(
            "SELECT COUNT(*) FROM pragma_table_info('desktop_transcription_tasks') WHERE name=?1",
            [name],
            |row| row.get(0),
        ).map_err(|_| "语音转写数据库列无法检查".to_owned())?;
        if present == 0 {
            connection
                .execute_batch(&format!(
                    "ALTER TABLE desktop_transcription_tasks ADD COLUMN {name} {definition};"
                ))
                .map_err(|_| "语音转写数据库列迁移失败".to_owned())?;
        }
    }
    recover_interrupted(connection)
}

pub fn recover_interrupted(connection: &Connection) -> Result<(), String> {
    connection.execute(
        "UPDATE desktop_transcription_tasks SET state='RECOVERY_REQUIRED',error_code='APP_INTERRUPTED',user_message='上次转写被中断，请重试',technical_detail='desktop process ended during active transcription',updated_at_ms=?1 WHERE state IN ('PREPARING','TRANSCRIBING','EXPORTING')",
        [now_millis()],
    ).map_err(|_| "语音转写恢复状态无法保存".to_owned())?;
    Ok(())
}

pub fn read_state(database: &Path, workspace_id: Option<&str>) -> Result<StateProjection, String> {
    let connection = open(database)?;
    let settings = read_settings(&connection)?;
    let mut statement = connection.prepare(
        "SELECT id,workspace_id,source_attachment_id,source_mime_type,source_display_name,source_byte_count,model_id,language_code,state,progress_millis,total_duration_millis,error_code,user_message,technical_detail,attempt_count,result_attachment_id,provider_request_count,provider_request_id,page_count,billable_audio_millis,input_tokens,output_tokens,estimated_charge_micros,created_at_ms,updated_at_ms FROM desktop_transcription_tasks WHERE (?1 IS NULL OR workspace_id=?1) ORDER BY created_at_ms DESC,id DESC",
    ).map_err(|_| "无法读取语音转写任务".to_owned())?;
    let rows = statement
        .query_map([workspace_id], |row| {
            Ok(TaskProjection {
                id: row.get(0)?,
                workspace_id: row.get(1)?,
                source_attachment_id: row.get(2)?,
                source_mime_type: row.get(3)?,
                source_display_name: row.get(4)?,
                source_byte_count: row.get(5)?,
                model_id: row.get(6)?,
                language_code: row.get(7)?,
                state: row.get(8)?,
                progress_millis: row.get(9)?,
                total_duration_millis: row.get(10)?,
                error_code: row.get(11)?,
                user_message: row.get(12)?,
                technical_detail: row.get(13)?,
                attempt_count: row.get(14)?,
                result_attachment_id: row.get(15)?,
                provider_request_count: row.get(16)?,
                provider_request_id: row.get(17)?,
                page_count: row.get(18)?,
                billable_audio_millis: row.get(19)?,
                input_tokens: row.get(20)?,
                output_tokens: row.get(21)?,
                estimated_charge_micros: row.get(22)?,
                created_at_millis: row.get(23)?,
                updated_at_millis: row.get(24)?,
                segments: Vec::new(),
            })
        })
        .map_err(|_| "无法读取语音转写任务".to_owned())?;
    let mut tasks = rows
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "语音转写任务数据无效".to_owned())?;
    for task in &mut tasks {
        task.segments = read_segments(&connection, &task.id)?;
    }
    Ok(StateProjection { settings, tasks })
}

pub fn save_settings(
    database: &Path,
    args: SaveSettingsArgs,
) -> Result<SettingsProjection, String> {
    validate_settings(
        &args.model_id,
        args.language_code.as_deref(),
        &args.output_format,
    )?;
    let connection = open(database)?;
    let changed = connection.execute(
        "UPDATE desktop_transcription_settings SET model_id=?1,language_code=?2,output_format=?3,revision=revision+1,updated_at_ms=?4 WHERE id=1 AND revision=?5",
        params![args.model_id,args.language_code,args.output_format,now_millis(),args.expected_revision],
    ).map_err(|_| "语音转写设置未保存".to_owned())?;
    if changed != 1 {
        return Err("语音转写设置已在其他窗口更新，请刷新后重试".to_owned());
    }
    read_settings(&connection)
}

pub fn import_source(
    root: &Path,
    database: &Path,
    args: ImportArgs,
) -> Result<TaskProjection, String> {
    let source = PathBuf::from(&args.selected_path);
    if !source.is_absolute() {
        return Err("请选择本机绝对路径中的音频或视频".to_owned());
    }
    let metadata = fs::symlink_metadata(&source).map_err(|_| "所选音视频不可读".to_owned())?;
    if metadata.file_type().is_symlink() || !metadata.file_type().is_file() {
        return Err("只接受普通音频或视频文件，不接受符号链接".to_owned());
    }
    if metadata.len() == 0 || metadata.len() > MAX_SOURCE_BYTES {
        return Err("音视频必须大于 0 且不超过 2 GiB".to_owned());
    }
    let extension = source
        .extension()
        .and_then(|value| value.to_str())
        .unwrap_or("")
        .to_ascii_lowercase();
    if !SUPPORTED_EXTENSIONS.contains(&extension.as_str()) {
        return Err("该音视频格式不受支持".to_owned());
    }
    let display_name = safe_display_name(&source)?;
    let mime_type = mime_for_extension(&extension).to_owned();
    let now = now_millis();
    fs::create_dir_all(root.join("assets")).map_err(|_| "无法创建私有附件目录".to_owned())?;
    fs::create_dir_all(root.join("staging")).map_err(|_| "无法创建私有暂存目录".to_owned())?;
    let pending = root
        .join("staging")
        .join(format!("transcription-{}.pending", random_hex(16)?));
    let (digest, byte_count) = copy_and_hash(&source, &pending)?;
    if byte_count != metadata.len() {
        let _ = fs::remove_file(&pending);
        return Err("音视频复制期间发生变化，请重新选择".to_owned());
    }
    let target = root.join("assets").join(&digest);
    if target.exists() {
        fs::remove_file(&pending).map_err(|_| "重复音视频暂存无法清理".to_owned())?;
    } else {
        fs::rename(&pending, &target).map_err(|_| "音视频无法原子发布到私有存储".to_owned())?;
    }

    let mut connection = open(database)?;
    let settings = read_settings(&connection)?;
    let requested_workspace = args.workspace_id.trim();
    let workspace_id = if requested_workspace.is_empty() {
        TRANSCRIPTION_WORKSPACE_ID.to_owned()
    } else {
        let workspace_exists: bool = connection
            .query_row(
                "SELECT EXISTS(SELECT 1 FROM workspaces WHERE id=?1)",
                [requested_workspace],
                |row| row.get(0),
            )
            .map_err(|_| "无法确认工作区".to_owned())?;
        if !workspace_exists {
            return Err("工作区不存在".to_owned());
        }
        requested_workspace.to_owned()
    };
    let attachment_id = format!("attachment-{}", &digest[..24]);
    let task_id = format!("transcription-{}", random_hex(16)?);
    let state = if settings.model_id == SENSEVOICE_MODEL_ID {
        "WAITING_MODEL"
    } else {
        "QUEUED"
    };
    let user_message = if state == "WAITING_MODEL" {
        Some("SenseVoice 验证中／保留实验；Desktop 本地引擎与许可尚未完成")
    } else {
        None
    };
    let transaction = connection
        .transaction()
        .map_err(|_| "无法开启语音转写导入事务".to_owned())?;
    if workspace_id == TRANSCRIPTION_WORKSPACE_ID {
        transaction.execute(
            "INSERT OR IGNORE INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES(?1,'南枫转写（本地）','','',datetime(?2/1000,'unixepoch'))",
            params![TRANSCRIPTION_WORKSPACE_ID, now],
        ).map_err(|_| "无法建立语音转写本地空间".to_owned())?;
    }
    transaction.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,?3,?3,?3,1) ON CONFLICT(sha256) DO UPDATE SET byte_count=excluded.byte_count,last_referenced_at_ms=excluded.last_referenced_at_ms", params![digest,byte_count,now]).map_err(|_| "音视频资产元数据未保存".to_owned())?;
    transaction.execute("INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,?2,?3,?4,?5,?6,datetime(?7/1000,'unixepoch')) ON CONFLICT(workspace_id,sha256) DO NOTHING", params![workspace_id,attachment_id,mime_type,display_name,byte_count,digest,now]).map_err(|_| "音视频附件元数据未保存".to_owned())?;
    let actual_attachment_id: String = transaction.query_row("SELECT attachment_id FROM desktop_conversation_attachments WHERE workspace_id=?1 AND sha256=?2", params![workspace_id,digest], |row| row.get(0)).map_err(|_| "音视频附件无法回读".to_owned())?;
    transaction.execute("INSERT INTO desktop_transcription_tasks(id,workspace_id,source_attachment_id,source_sha256,source_mime_type,source_display_name,source_byte_count,model_id,language_code,state,user_message,created_at_ms,updated_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?12)", params![task_id,workspace_id,actual_attachment_id,digest,mime_type,display_name,byte_count,settings.model_id,settings.language_code,state,user_message,now]).map_err(|_| "语音转写任务未保存".to_owned())?;
    index_task_source(&transaction, &task_id)?;
    transaction
        .commit()
        .map_err(|_| "语音转写任务未提交；已回滚".to_owned())?;
    read_task(&connection, &task_id)
}

/// Imports the same JPG/PNG/PDF contract as Android GLM-OCR while reusing the Desktop private
/// attachment catalogue, reference counter, search index and deletion lifecycle.
pub fn import_document_source(
    root: &Path,
    database: &Path,
    args: ImportArgs,
) -> Result<TaskProjection, String> {
    let source = PathBuf::from(&args.selected_path);
    if !source.is_absolute() {
        return Err("请选择本机绝对路径中的图片或 PDF".to_owned());
    }
    let metadata = fs::symlink_metadata(&source).map_err(|_| "所选图片或 PDF 不可读".to_owned())?;
    if metadata.file_type().is_symlink() || !metadata.file_type().is_file() {
        return Err("只接受普通图片或 PDF 文件，不接受符号链接".to_owned());
    }
    let extension = source
        .extension()
        .and_then(|value| value.to_str())
        .unwrap_or("")
        .to_ascii_lowercase();
    let mime_type = match extension.as_str() {
        "jpg" | "jpeg" => "image/jpeg",
        "png" => "image/png",
        "pdf" => "application/pdf",
        _ => return Err("GLM-OCR 只支持 JPG、PNG 和 PDF".to_owned()),
    };
    let max_bytes = if mime_type == "application/pdf" {
        GLM_OCR_PDF_MAX_BYTES
    } else {
        GLM_OCR_IMAGE_MAX_BYTES
    };
    if metadata.len() == 0 || metadata.len() > max_bytes {
        return Err(if mime_type == "application/pdf" {
            "PDF 必须大于 0 且不超过 50 MB"
        } else {
            "图片必须大于 0 且不超过 10 MB"
        }
        .to_owned());
    }
    let display_name = safe_display_name(&source)?;
    let now = now_millis();
    fs::create_dir_all(root.join("assets")).map_err(|_| "无法创建私有附件目录".to_owned())?;
    fs::create_dir_all(root.join("staging")).map_err(|_| "无法创建私有暂存目录".to_owned())?;
    let pending = root
        .join("staging")
        .join(format!("ocr-{}.pending", random_hex(16)?));
    let (digest, byte_count) = copy_and_hash(&source, &pending)?;
    if byte_count != metadata.len() {
        let _ = fs::remove_file(&pending);
        return Err("文件复制期间发生变化，请重新选择".to_owned());
    }
    let target = root.join("assets").join(&digest);
    if target.exists() {
        fs::remove_file(&pending).map_err(|_| "重复文件暂存无法清理".to_owned())?;
    } else {
        fs::rename(&pending, &target).map_err(|_| "文件无法原子发布到私有存储".to_owned())?;
    }
    let mut connection = open(database)?;
    let requested_workspace = args.workspace_id.trim();
    let workspace_id = if requested_workspace.is_empty() {
        TRANSCRIPTION_WORKSPACE_ID.to_owned()
    } else {
        let workspace_exists: bool = connection
            .query_row(
                "SELECT EXISTS(SELECT 1 FROM workspaces WHERE id=?1)",
                [requested_workspace],
                |row| row.get(0),
            )
            .map_err(|_| "无法确认工作区".to_owned())?;
        if !workspace_exists {
            return Err("工作区不存在".to_owned());
        }
        requested_workspace.to_owned()
    };
    let attachment_id = format!("attachment-{}", &digest[..24]);
    let task_id = format!("transcription-{}", random_hex(16)?);
    let transaction = connection
        .transaction()
        .map_err(|_| "无法开启南枫转写导入事务".to_owned())?;
    if workspace_id == TRANSCRIPTION_WORKSPACE_ID {
        transaction.execute("INSERT OR IGNORE INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES(?1,'南枫转写（本地）','','',datetime(?2/1000,'unixepoch'))", params![TRANSCRIPTION_WORKSPACE_ID,now]).map_err(|_| "无法建立南枫转写本地空间".to_owned())?;
    }
    transaction.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,?3,?3,?3,1) ON CONFLICT(sha256) DO UPDATE SET byte_count=excluded.byte_count,last_referenced_at_ms=excluded.last_referenced_at_ms", params![digest,byte_count,now]).map_err(|_| "文档资产元数据未保存".to_owned())?;
    transaction.execute("INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,?2,?3,?4,?5,?6,datetime(?7/1000,'unixepoch')) ON CONFLICT(workspace_id,sha256) DO NOTHING", params![workspace_id,attachment_id,mime_type,display_name,byte_count,digest,now]).map_err(|_| "文档附件元数据未保存".to_owned())?;
    let actual_attachment_id: String = transaction.query_row("SELECT attachment_id FROM desktop_conversation_attachments WHERE workspace_id=?1 AND sha256=?2", params![workspace_id,digest], |row| row.get(0)).map_err(|_| "文档附件无法回读".to_owned())?;
    transaction.execute("INSERT INTO desktop_transcription_tasks(id,workspace_id,source_attachment_id,source_sha256,source_mime_type,source_display_name,source_byte_count,model_id,language_code,state,created_at_ms,updated_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,NULL,'QUEUED',?9,?9)", params![task_id,workspace_id,actual_attachment_id,digest,mime_type,display_name,byte_count,GLM_OCR_MODEL_ID,now]).map_err(|_| "南枫转写文档任务未保存".to_owned())?;
    index_task_source(&transaction, &task_id)?;
    transaction
        .commit()
        .map_err(|_| "南枫转写文档任务未提交；已回滚".to_owned())?;
    read_task(&connection, &task_id)
}

pub fn retry_task(database: &Path, task_id: &str) -> Result<TaskProjection, String> {
    let connection = open(database)?;
    let changed = connection.execute("UPDATE desktop_transcription_tasks SET state=CASE WHEN model_id=?1 THEN 'WAITING_MODEL' ELSE 'QUEUED' END,error_code=NULL,user_message=CASE WHEN model_id=?1 THEN 'SenseVoice 验证中／保留实验；Desktop 本地引擎与许可尚未完成' ELSE NULL END,technical_detail=NULL,updated_at_ms=?2 WHERE id=?3 AND state IN ('FAILED','RECOVERY_REQUIRED','WAITING_MODEL','CANCELLED')", params![SENSEVOICE_MODEL_ID,now_millis(),task_id]).map_err(|_| "任务重试状态未保存".to_owned())?;
    if changed != 1 {
        return Err("当前任务状态不可重试".to_owned());
    }
    read_task(&connection, task_id)
}

pub fn cancel_task(database: &Path, task_id: &str) -> Result<TaskProjection, String> {
    let connection = open(database)?;
    let changed = connection.execute("UPDATE desktop_transcription_tasks SET state='CANCELLED',error_code=NULL,user_message='已取消',technical_detail=NULL,updated_at_ms=?1 WHERE id=?2 AND state IN ('QUEUED','WAITING_INPUT','WAITING_MODEL','PREPARING','TRANSCRIBING','EXPORTING','RECOVERY_REQUIRED','FAILED')", params![now_millis(),task_id]).map_err(|_| "任务取消状态未保存".to_owned())?;
    if changed != 1 {
        return Err("当前任务状态不可取消".to_owned());
    }
    read_task(&connection, task_id)
}

pub fn delete_task(database: &Path, task_id: &str) -> Result<(), String> {
    let mut connection = open(database)?;
    let transaction = connection
        .transaction()
        .map_err(|_| "无法开启任务删除事务".to_owned())?;
    transaction
        .execute(
            "DELETE FROM desktop_local_search_index WHERE conversation_id=?1",
            [search_owner_id(task_id)],
        )
        .map_err(|_| "任务搜索索引未清理".to_owned())?;
    let changed = transaction.execute("DELETE FROM desktop_transcription_tasks WHERE id=?1 AND state NOT IN ('PREPARING','TRANSCRIBING','EXPORTING')", [task_id]).map_err(|_| "任务未删除".to_owned())?;
    if changed != 1 {
        return Err("运行中的任务不能删除，请先取消".to_owned());
    }
    transaction
        .commit()
        .map_err(|_| "任务删除未提交；已回滚".to_owned())
}

pub fn run_task(
    root: &Path,
    database: &Path,
    task_id: &str,
    api_key: &[u8],
) -> Result<TaskProjection, String> {
    if api_key.is_empty() {
        fail_task(
            database,
            task_id,
            "QWEN_API_KEY_MISSING",
            "高精度 Qwen 未配置 API Key，请在设置中保存后重试",
            "credential owner returned empty secret",
        )?;
        return Err("高精度 Qwen 未配置 API Key，请在设置中保存后重试".to_owned());
    }
    let connection = open(database)?;
    let task = read_task(&connection, task_id)?;
    if task.model_id != QWEN_MODEL_ID {
        connection.execute("UPDATE desktop_transcription_tasks SET state='WAITING_MODEL',error_code='SENSEVOICE_UNAVAILABLE',user_message='SenseVoice 验证中／保留实验；Desktop 本地引擎与许可尚未完成',technical_detail='no licensed Desktop SenseVoice runtime',updated_at_ms=?1 WHERE id=?2", params![now_millis(),task_id]).map_err(|_| "等待模型状态未保存".to_owned())?;
        return Err("SenseVoice 验证中／保留实验；Desktop 本地引擎与许可尚未完成".to_owned());
    }
    if !matches!(
        task.state.as_str(),
        "QUEUED" | "FAILED" | "RECOVERY_REQUIRED"
    ) {
        return Err("当前任务状态不可执行".to_owned());
    }
    connection.execute("UPDATE desktop_transcription_tasks SET state='PREPARING',attempt_count=attempt_count+1,error_code=NULL,user_message=NULL,technical_detail=NULL,updated_at_ms=?1 WHERE id=?2", params![now_millis(),task_id]).map_err(|_| "准备状态未保存".to_owned())?;
    let source_sha: String = connection
        .query_row(
            "SELECT source_sha256 FROM desktop_transcription_tasks WHERE id=?1",
            [task_id],
            |row| row.get(0),
        )
        .map_err(|_| "音视频引用不可用".to_owned())?;
    let source = root.join("assets").join(&source_sha);
    let ffprobe =
        find_binary("ffprobe").ok_or_else(|| "未找到 ffprobe；请安装 FFmpeg 后重试".to_owned());
    let ffmpeg =
        find_binary("ffmpeg").ok_or_else(|| "未找到 ffmpeg；请安装 FFmpeg 后重试".to_owned());
    let (ffprobe, ffmpeg) = match (ffprobe, ffmpeg) {
        (Ok(probe), Ok(decode)) => (probe, decode),
        (Err(message), _) | (_, Err(message)) => {
            fail_task(
                database,
                task_id,
                "DECODER_UNAVAILABLE",
                &message,
                "ffmpeg/ffprobe executable unavailable",
            )?;
            return Err(message);
        }
    };
    let duration = match probe_duration(&ffprobe, &source) {
        Ok(value) => value,
        Err(detail) => {
            fail_task(
                database,
                task_id,
                "MEDIA_INVALID",
                "无法读取该音视频，请确认文件完整且格式受支持",
                &detail,
            )?;
            return Err("无法读取该音视频，请确认文件完整且格式受支持".to_owned());
        }
    };
    if duration == 0 || duration > MAX_DURATION_MILLIS {
        let message = "音视频时长必须大于 0 且不超过 12 小时";
        fail_task(
            database,
            task_id,
            "DURATION_LIMIT",
            message,
            &format!("duration_millis={duration}"),
        )?;
        return Err(message.to_owned());
    }
    connection.execute("UPDATE desktop_transcription_tasks SET state='TRANSCRIBING',total_duration_millis=?1,updated_at_ms=?2 WHERE id=?3", params![duration,now_millis(),task_id]).map_err(|_| "转写状态未保存".to_owned())?;
    let completed_end: u64 = connection.query_row("SELECT COALESCE(MAX(end_millis),0) FROM desktop_transcription_segments WHERE task_id=?1", [task_id], |row| row.get(0)).map_err(|_| "无法读取转写断点".to_owned())?;
    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(180))
        .build()
        .map_err(|_| "无法创建 Qwen 请求客户端".to_owned())?;
    let mut start = completed_end.min(duration);
    while start < duration {
        if current_state(database, task_id)? == "CANCELLED" {
            return read_task(&open(database)?, task_id);
        }
        let end = (start + CHUNK_MILLIS).min(duration);
        let work = root.join("transcription-work");
        fs::create_dir_all(&work).map_err(|_| "无法创建转写临时目录".to_owned())?;
        let wav = work.join(format!("{task_id}-{}.wav", start));
        if let Err(detail) = decode_chunk(&ffmpeg, &source, &wav, start, end - start) {
            let _ = fs::remove_file(&wav);
            fail_task(
                database,
                task_id,
                "DECODE_FAILED",
                "音视频解码失败，请确认文件完整",
                &detail,
            )?;
            return Err("音视频解码失败，请确认文件完整".to_owned());
        }
        let bytes = fs::read(&wav).map_err(|_| "解码后的音频切片不可读".to_owned())?;
        let _ = fs::remove_file(&wav);
        let language = task
            .language_code
            .as_deref()
            .filter(|value| !value.is_empty());
        let response = match qwen_request(&client, api_key, &bytes, language) {
            Ok(value) => value,
            Err((code, message, detail)) => {
                fail_task(database, task_id, &code, &message, &detail)?;
                return Err(message);
            }
        };
        let connection = open(database)?;
        let ordinal: u64 = connection
            .query_row(
                "SELECT COUNT(*) FROM desktop_transcription_segments WHERE task_id=?1",
                [task_id],
                |row| row.get(0),
            )
            .map_err(|_| "无法计算转写分段序号".to_owned())?;
        connection.execute("INSERT OR REPLACE INTO desktop_transcription_segments(task_id,ordinal,start_millis,end_millis,text) VALUES(?1,?2,?3,?4,?5)", params![task_id,ordinal,start,end,response.text]).map_err(|_| "转写分段未保存".to_owned())?;
        let attempted = end - start;
        let charge = attempted
            .saturating_mul(QWEN_ESTIMATED_MICRO_CNY_PER_SECOND)
            .saturating_add(999)
            / 1000;
        connection.execute("UPDATE desktop_transcription_tasks SET progress_millis=?1,provider_request_count=provider_request_count+1,billable_audio_millis=billable_audio_millis+?2,input_tokens=COALESCE(input_tokens,0)+?3,output_tokens=COALESCE(output_tokens,0)+?4,estimated_charge_micros=estimated_charge_micros+?5,updated_at_ms=?6 WHERE id=?7", params![end,attempted,response.input_tokens.unwrap_or(0),response.output_tokens.unwrap_or(0),charge,now_millis(),task_id]).map_err(|_| "转写进度未保存".to_owned())?;
        start = end;
    }
    complete_task(root, database, task_id)
}

pub fn run_ocr_task(
    root: &Path,
    database: &Path,
    task_id: &str,
    api_key: &[u8],
) -> Result<TaskProjection, String> {
    let connection = open(database)?;
    let task = read_task(&connection, task_id)?;
    if task.model_id != GLM_OCR_MODEL_ID {
        return Err("当前任务不是图片/PDF 转写任务".to_owned());
    }
    if !matches!(
        task.state.as_str(),
        "QUEUED" | "FAILED" | "RECOVERY_REQUIRED"
    ) {
        return Err("当前任务状态不可执行".to_owned());
    }
    connection.execute("UPDATE desktop_transcription_tasks SET attempt_count=attempt_count+1,error_code=NULL,user_message=NULL,technical_detail=NULL,updated_at_ms=?1 WHERE id=?2", params![now_millis(),task_id]).map_err(|_| "GLM-OCR Attempt 未保存".to_owned())?;
    if api_key.is_empty() {
        fail_task(
            database,
            task_id,
            "ZHIPU_API_KEY_MISSING",
            "智谱 GLM-OCR 未配置 API Key，请在设置中保存后重试",
            "credential owner returned empty secret",
        )?;
        return Err("智谱 GLM-OCR 未配置 API Key，请在设置中保存后重试".to_owned());
    }
    connection.execute("UPDATE desktop_transcription_tasks SET state='TRANSCRIBING',updated_at_ms=?1 WHERE id=?2", params![now_millis(),task_id]).map_err(|_| "文档转写状态未保存".to_owned())?;
    let source_sha: String = connection
        .query_row(
            "SELECT source_sha256 FROM desktop_transcription_tasks WHERE id=?1",
            [task_id],
            |row| row.get(0),
        )
        .map_err(|_| "图片/PDF 引用不可用".to_owned())?;
    let source = root.join("assets").join(&source_sha);
    let source_bytes = fs::read(&source).map_err(|_| "图片/PDF 私有副本不可读".to_owned())?;
    let max_bytes = if task.source_mime_type == "application/pdf" {
        GLM_OCR_PDF_MAX_BYTES
    } else {
        GLM_OCR_IMAGE_MAX_BYTES
    };
    if source_bytes.is_empty()
        || source_bytes.len() as u64 > max_bytes
        || hex_digest(&source_bytes) != source_sha
    {
        fail_task(
            database,
            task_id,
            "SOURCE_INTEGRITY",
            "图片/PDF 私有副本校验失败，请重新选择",
            "source byte count or sha256 mismatch",
        )?;
        return Err("图片/PDF 私有副本校验失败，请重新选择".to_owned());
    }
    let key =
        std::str::from_utf8(api_key).map_err(|_| "智谱 API Key 格式无效，请重新保存".to_owned())?;
    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(300))
        .build()
        .map_err(|_| "无法创建 GLM-OCR 请求客户端".to_owned())?;
    drop(source_bytes);
    let payload = GlmOcrJsonBody::new(
        fs::File::open(&source).map_err(|_| "图片/PDF 私有副本不可读".to_owned())?,
        &task.source_mime_type,
    );
    connection.execute("UPDATE desktop_transcription_tasks SET provider_request_count=provider_request_count+1,updated_at_ms=?1 WHERE id=?2", params![now_millis(),task_id]).map_err(|_| "GLM-OCR Attempt 未保存".to_owned())?;
    let response = match client
        .post(GLM_OCR_ENDPOINT)
        .bearer_auth(key)
        .header(reqwest::header::CONTENT_TYPE, "application/json")
        .body(reqwest::blocking::Body::new(payload))
        .send()
    {
        Ok(response) => response,
        Err(error) => {
            fail_task(
                database,
                task_id,
                "NETWORK_UNKNOWN",
                "GLM-OCR 连接失败，请检查网络后显式重试",
                &format!("request failed: {}", error.without_url()),
            )?;
            return Err("GLM-OCR 连接失败，请检查网络后显式重试".to_owned());
        }
    };
    let status = response.status();
    if !status.is_success() {
        let code = match status.as_u16() {
            401 | 403 => "AUTHENTICATION".to_owned(),
            402 => "BALANCE".to_owned(),
            413 => "SOURCE_TOO_LARGE".to_owned(),
            429 => "RATE_LIMIT".to_owned(),
            value if value >= 500 => format!("HTTP_{value}_UNKNOWN"),
            value => format!("HTTP_{value}"),
        };
        let message = if matches!(status.as_u16(), 401 | 403) {
            "智谱 API Key 无效或没有 GLM-OCR 权限"
        } else if status.as_u16() == 429 {
            "GLM-OCR 请求过于频繁，请稍后显式重试"
        } else {
            "GLM-OCR 请求失败，请稍后显式重试"
        };
        fail_task(
            database,
            task_id,
            &code,
            message,
            &format!("HTTP {}", status.as_u16()),
        )?;
        return Err(message.to_owned());
    }
    let response_bytes = match response.bytes() {
        Ok(bytes) => bytes,
        Err(_) => {
            fail_task(
                database,
                task_id,
                "RESPONSE_UNREADABLE_UNKNOWN",
                "GLM-OCR 返回内容不可读，请显式重试",
                "response body read failed",
            )?;
            return Err("GLM-OCR 返回内容不可读，请显式重试".to_owned());
        }
    };
    if response_bytes.len() > 32 * 1024 * 1024 {
        fail_task(
            database,
            task_id,
            "RESPONSE_TOO_LARGE",
            "GLM-OCR 返回内容过大，未保存",
            "response exceeded 32 MiB",
        )?;
        return Err("GLM-OCR 返回内容过大，未保存".to_owned());
    }
    let value: Value = match serde_json::from_slice(&response_bytes) {
        Ok(value) => value,
        Err(_) => {
            fail_task(
                database,
                task_id,
                "RESPONSE_FORMAT",
                "GLM-OCR 返回格式异常，请显式重试",
                "invalid JSON response",
            )?;
            return Err("GLM-OCR 返回格式异常，请显式重试".to_owned());
        }
    };
    let markdown = glm_ocr_markdown(&value);
    if markdown.trim().is_empty() {
        fail_task(
            database,
            task_id,
            "EMPTY_MARKDOWN",
            "GLM-OCR 未返回可用 Markdown",
            "md_results was empty",
        )?;
        return Err("GLM-OCR 未返回可用 Markdown".to_owned());
    }
    if current_state(database, task_id)? == "CANCELLED" {
        return read_task(&open(database)?, task_id);
    }
    let input_tokens = value
        .pointer("/usage/prompt_tokens")
        .and_then(Value::as_u64)
        .or_else(|| value.pointer("/usage/input_tokens").and_then(Value::as_u64));
    let output_tokens = value
        .pointer("/usage/completion_tokens")
        .and_then(Value::as_u64)
        .or_else(|| {
            value
                .pointer("/usage/output_tokens")
                .and_then(Value::as_u64)
        });
    let provider_request_id = value
        .get("request_id")
        .or_else(|| value.get("id"))
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|item| !item.is_empty())
        .map(|item| item.chars().take(200).collect::<String>());
    let page_count = value
        .pointer("/data_info/num_pages")
        .and_then(Value::as_u64);
    let total_tokens = input_tokens
        .unwrap_or(0)
        .saturating_add(output_tokens.unwrap_or(0));
    let cost_micros = total_tokens.saturating_mul(200_000).saturating_add(999_999) / 1_000_000;
    open(database)?.execute("UPDATE desktop_transcription_tasks SET provider_request_id=?1,page_count=?2,input_tokens=?3,output_tokens=?4,estimated_charge_micros=?5,updated_at_ms=?6 WHERE id=?7", params![provider_request_id,page_count,input_tokens,output_tokens,cost_micros,now_millis(),task_id]).map_err(|_| "GLM-OCR 用量未保存".to_owned())?;
    complete_ocr_task(root, database, task_id, &markdown)
}

fn glm_ocr_markdown(value: &Value) -> String {
    match value.get("md_results") {
        Some(Value::String(text)) => text.clone(),
        Some(Value::Array(items)) => items
            .iter()
            .filter_map(|item| match item {
                Value::String(text) => Some(text.clone()),
                Value::Object(map) => map
                    .get("markdown")
                    .or_else(|| map.get("content"))
                    .and_then(Value::as_str)
                    .map(str::to_owned),
                _ => None,
            })
            .collect::<Vec<_>>()
            .join("\n\n"),
        Some(Value::Object(map)) => map
            .get("markdown")
            .or_else(|| map.get("content"))
            .and_then(Value::as_str)
            .unwrap_or("")
            .to_owned(),
        _ => String::new(),
    }
}

pub fn export_task(database: &Path, args: ExportArgs) -> Result<ExportReceipt, String> {
    let connection = open(database)?;
    let task = read_task(&connection, &args.task_id)?;
    if task.state != "COMPLETED" {
        return Err("只有已完成任务可以导出".to_owned());
    }
    let format = normalize_format(&args.format)?;
    let target = PathBuf::from(&args.selected_path);
    if !target.is_absolute()
        || target
            .extension()
            .and_then(|value| value.to_str())
            .map(|value| value.to_ascii_lowercase())
            != Some(format.to_owned())
    {
        return Err("导出路径或扩展名与格式不一致".to_owned());
    }
    let bytes = render_export(&task, format)?;
    write_atomic(&target, &bytes)?;
    let readback = fs::read(&target).map_err(|_| "导出文件无法回读".to_owned())?;
    if readback != bytes {
        return Err("导出文件回读校验失败".to_owned());
    }
    Ok(ExportReceipt {
        format: format.to_owned(),
        byte_count: bytes.len() as u64,
        sha256: hex_digest(&bytes),
    })
}

pub fn add_attachment_references(
    transaction: &Transaction<'_>,
    counts: &mut std::collections::BTreeMap<String, u64>,
) -> Result<(), String> {
    let mut statement = transaction.prepare("SELECT digest,COUNT(*) FROM (SELECT source_sha256 AS digest FROM desktop_transcription_tasks UNION ALL SELECT result_sha256 AS digest FROM desktop_transcription_tasks WHERE result_sha256 IS NOT NULL) GROUP BY digest").map_err(|_| "无法读取语音转写附件引用".to_owned())?;
    let rows = statement
        .query_map([], |row| {
            Ok((row.get::<_, String>(0)?, row.get::<_, u64>(1)?))
        })
        .map_err(|_| "无法读取语音转写附件引用".to_owned())?;
    for row in rows {
        let (digest, count) = row.map_err(|_| "语音转写附件引用无效".to_owned())?;
        *counts.entry(digest).or_default() += count;
    }
    Ok(())
}

pub fn index_tasks(transaction: &Transaction<'_>, workspace_id: &str) -> Result<(), String> {
    let ids = transaction
        .prepare("SELECT id FROM desktop_transcription_tasks WHERE workspace_id=?1")
        .map_err(|_| "无法读取语音转写搜索任务".to_owned())?
        .query_map([workspace_id], |row| row.get::<_, String>(0))
        .map_err(|_| "无法读取语音转写搜索任务".to_owned())?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "语音转写搜索任务无效".to_owned())?;
    for id in ids {
        index_task_source(transaction, &id)?;
        index_task_result(transaction, &id)?;
    }
    Ok(())
}

fn open(database: &Path) -> Result<Connection, String> {
    let connection = Connection::open(database).map_err(|_| "无法打开语音转写数据库".to_owned())?;
    connection
        .pragma_update(None, "foreign_keys", "ON")
        .map_err(|_| "无法启用语音转写外键".to_owned())?;
    connection
        .busy_timeout(std::time::Duration::from_secs(3))
        .map_err(|_| "无法设置语音转写数据库等待时间".to_owned())?;
    Ok(connection)
}

fn read_settings(connection: &Connection) -> Result<SettingsProjection, String> {
    connection.query_row("SELECT model_id,language_code,output_format,revision FROM desktop_transcription_settings WHERE id=1", [], |row| Ok(SettingsProjection { model_id:row.get(0)?,language_code:row.get(1)?,output_format:row.get(2)?,revision:row.get(3)?,sense_voice_available:false,sense_voice_status:"验证中／保留实验；Desktop 本地引擎与许可尚未完成",source_max_bytes:MAX_SOURCE_BYTES,source_max_duration_millis:MAX_DURATION_MILLIS })).map_err(|_| "语音转写设置不可用".to_owned())
}

fn validate_settings(model: &str, language: Option<&str>, format: &str) -> Result<(), String> {
    if !matches!(model, QWEN_MODEL_ID | SENSEVOICE_MODEL_ID) {
        return Err("语音转写模型无效".to_owned());
    }
    if !matches!(language, None | Some("") | Some("zh") | Some("en")) {
        return Err("语音转写语言无效".to_owned());
    }
    normalize_format(format).map(|_| ())
}

fn normalize_format(value: &str) -> Result<&'static str, String> {
    match value.to_ascii_lowercase().as_str() {
        "txt" => Ok("txt"),
        "md" | "markdown" => Ok("md"),
        "srt" => Ok("srt"),
        "docx" => Ok("docx"),
        _ => Err("导出格式无效".to_owned()),
    }
}

fn read_task(connection: &Connection, task_id: &str) -> Result<TaskProjection, String> {
    let mut task = connection.query_row("SELECT id,workspace_id,source_attachment_id,source_mime_type,source_display_name,source_byte_count,model_id,language_code,state,progress_millis,total_duration_millis,error_code,user_message,technical_detail,attempt_count,result_attachment_id,provider_request_count,provider_request_id,page_count,billable_audio_millis,input_tokens,output_tokens,estimated_charge_micros,created_at_ms,updated_at_ms FROM desktop_transcription_tasks WHERE id=?1", [task_id], |row| Ok(TaskProjection { id:row.get(0)?,workspace_id:row.get(1)?,source_attachment_id:row.get(2)?,source_mime_type:row.get(3)?,source_display_name:row.get(4)?,source_byte_count:row.get(5)?,model_id:row.get(6)?,language_code:row.get(7)?,state:row.get(8)?,progress_millis:row.get(9)?,total_duration_millis:row.get(10)?,error_code:row.get(11)?,user_message:row.get(12)?,technical_detail:row.get(13)?,attempt_count:row.get(14)?,result_attachment_id:row.get(15)?,provider_request_count:row.get(16)?,provider_request_id:row.get(17)?,page_count:row.get(18)?,billable_audio_millis:row.get(19)?,input_tokens:row.get(20)?,output_tokens:row.get(21)?,estimated_charge_micros:row.get(22)?,created_at_millis:row.get(23)?,updated_at_millis:row.get(24)?,segments:Vec::new() })).map_err(|_| "语音转写任务不存在".to_owned())?;
    task.segments = read_segments(connection, task_id)?;
    Ok(task)
}

fn read_segments(connection: &Connection, task_id: &str) -> Result<Vec<SegmentProjection>, String> {
    connection.prepare("SELECT ordinal,start_millis,end_millis,text FROM desktop_transcription_segments WHERE task_id=?1 ORDER BY ordinal").map_err(|_| "无法读取转写时间轴".to_owned())?.query_map([task_id], |row| Ok(SegmentProjection { ordinal:row.get(0)?,start_millis:row.get(1)?,end_millis:row.get(2)?,text:row.get(3)? })).map_err(|_| "无法读取转写时间轴".to_owned())?.collect::<Result<Vec<_>,_>>().map_err(|_| "转写时间轴无效".to_owned())
}

fn complete_task(root: &Path, database: &Path, task_id: &str) -> Result<TaskProjection, String> {
    let mut connection = open(database)?;
    let mut task = read_task(&connection, task_id)?;
    if task
        .segments
        .iter()
        .all(|segment| segment.text.trim().is_empty())
    {
        connection.execute("UPDATE desktop_transcription_tasks SET state='NO_SPEECH',user_message='未识别到可转写语音',updated_at_ms=?1 WHERE id=?2", params![now_millis(),task_id]).map_err(|_| "无语音状态未保存".to_owned())?;
        return read_task(&connection, task_id);
    }
    connection
        .execute(
            "UPDATE desktop_transcription_tasks SET state='EXPORTING',updated_at_ms=?1 WHERE id=?2",
            params![now_millis(), task_id],
        )
        .map_err(|_| "结果生成状态未保存".to_owned())?;
    task.state = "COMPLETED".to_owned();
    let markdown = render_markdown(&task);
    let bytes = markdown.as_bytes();
    let digest = hex_digest(bytes);
    let attachment_id = format!("attachment-{}", &digest[..24]);
    let target = root.join("assets").join(&digest);
    if !target.exists() {
        write_atomic(&target, bytes)?;
    }
    let result_name = format!("{}.转写.md", task.source_display_name);
    let now = now_millis();
    let transaction = connection
        .transaction()
        .map_err(|_| "无法开启转写结果事务".to_owned())?;
    transaction.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,?3,?3,?3,1) ON CONFLICT(sha256) DO UPDATE SET last_referenced_at_ms=excluded.last_referenced_at_ms", params![digest,bytes.len() as u64,now]).map_err(|_| "转写结果资产未保存".to_owned())?;
    transaction.execute("INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,?2,'text/markdown',?3,?4,?5,datetime(?6/1000,'unixepoch')) ON CONFLICT(workspace_id,sha256) DO NOTHING", params![task.workspace_id,attachment_id,result_name,bytes.len() as u64,digest,now]).map_err(|_| "转写结果附件未保存".to_owned())?;
    let actual_id: String = transaction.query_row("SELECT attachment_id FROM desktop_conversation_attachments WHERE workspace_id=?1 AND sha256=?2", params![task.workspace_id,digest], |row| row.get(0)).map_err(|_| "转写结果附件无法回读".to_owned())?;
    transaction.execute("UPDATE desktop_transcription_tasks SET state='COMPLETED',progress_millis=total_duration_millis,result_attachment_id=?1,result_sha256=?2,result_byte_count=?3,error_code=NULL,user_message=NULL,technical_detail=NULL,updated_at_ms=?4 WHERE id=?5", params![actual_id,digest,bytes.len() as u64,now,task_id]).map_err(|_| "完成状态未保存".to_owned())?;
    index_task_result(&transaction, task_id)?;
    transaction
        .commit()
        .map_err(|_| "转写结果未提交；已回滚".to_owned())?;
    read_task(&open(database)?, task_id)
}

fn complete_ocr_task(
    root: &Path,
    database: &Path,
    task_id: &str,
    markdown: &str,
) -> Result<TaskProjection, String> {
    let mut connection = open(database)?;
    let task = read_task(&connection, task_id)?;
    connection
        .execute(
            "UPDATE desktop_transcription_tasks SET state='EXPORTING',updated_at_ms=?1 WHERE id=?2",
            params![now_millis(), task_id],
        )
        .map_err(|_| "GLM-OCR 结果生成状态未保存".to_owned())?;
    let bytes = markdown.as_bytes();
    let digest = hex_digest(bytes);
    let attachment_id = format!("attachment-{}", &digest[..24]);
    let target = root.join("assets").join(&digest);
    if !target.exists() {
        write_atomic(&target, bytes)?;
    }
    let stem = task
        .source_display_name
        .rsplit_once('.')
        .map(|(value, _)| value)
        .unwrap_or(&task.source_display_name)
        .trim();
    let result_name = format!("{}-OCR.md", if stem.is_empty() { "GLM-OCR" } else { stem });
    let now = now_millis();
    let transaction = connection
        .transaction()
        .map_err(|_| "无法开启 GLM-OCR 结果事务".to_owned())?;
    transaction
        .execute(
            "DELETE FROM desktop_transcription_segments WHERE task_id=?1",
            [task_id],
        )
        .map_err(|_| "旧 GLM-OCR 结果未清理".to_owned())?;
    transaction.execute("INSERT INTO desktop_transcription_segments(task_id,ordinal,start_millis,end_millis,text) VALUES(?1,0,0,0,?2)", params![task_id,markdown]).map_err(|_| "GLM-OCR Markdown 未保存".to_owned())?;
    transaction.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,?3,?3,?3,1) ON CONFLICT(sha256) DO UPDATE SET last_referenced_at_ms=excluded.last_referenced_at_ms", params![digest,bytes.len() as u64,now]).map_err(|_| "GLM-OCR 结果资产未保存".to_owned())?;
    transaction.execute("INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,?2,'text/markdown',?3,?4,?5,datetime(?6/1000,'unixepoch')) ON CONFLICT(workspace_id,sha256) DO NOTHING", params![task.workspace_id,attachment_id,result_name,bytes.len() as u64,digest,now]).map_err(|_| "GLM-OCR 结果附件未保存".to_owned())?;
    let actual_id: String = transaction.query_row("SELECT attachment_id FROM desktop_conversation_attachments WHERE workspace_id=?1 AND sha256=?2", params![task.workspace_id,digest], |row| row.get(0)).map_err(|_| "GLM-OCR 结果附件无法回读".to_owned())?;
    transaction.execute("UPDATE desktop_transcription_tasks SET state='COMPLETED',progress_millis=0,total_duration_millis=NULL,result_attachment_id=?1,result_sha256=?2,result_byte_count=?3,error_code=NULL,user_message=NULL,technical_detail=NULL,updated_at_ms=?4 WHERE id=?5", params![actual_id,digest,bytes.len() as u64,now,task_id]).map_err(|_| "GLM-OCR 完成状态未保存".to_owned())?;
    index_task_result(&transaction, task_id)?;
    transaction
        .commit()
        .map_err(|_| "GLM-OCR 结果未提交；已回滚".to_owned())?;
    read_task(&open(database)?, task_id)
}

fn fail_task(
    database: &Path,
    task_id: &str,
    code: &str,
    message: &str,
    detail: &str,
) -> Result<(), String> {
    open(database)?.execute("UPDATE desktop_transcription_tasks SET state='FAILED',error_code=?1,user_message=?2,technical_detail=?3,updated_at_ms=?4 WHERE id=?5 AND state!='CANCELLED'", params![code,message,detail,now_millis(),task_id]).map_err(|_| "失败状态未保存".to_owned())?;
    Ok(())
}

struct QwenResponse {
    text: String,
    input_tokens: Option<u64>,
    output_tokens: Option<u64>,
}

fn qwen_request(
    client: &reqwest::blocking::Client,
    api_key: &[u8],
    wav: &[u8],
    language: Option<&str>,
) -> Result<QwenResponse, (String, String, String)> {
    let key = std::str::from_utf8(api_key).map_err(|_| {
        (
            "QWEN_API_KEY_INVALID".to_owned(),
            "Qwen API Key 格式无效，请重新保存".to_owned(),
            "credential is not UTF-8".to_owned(),
        )
    })?;
    let mut options = json!({"enable_itn":false});
    if let Some(language) = language {
        options["language"] = Value::String(language.to_owned());
    }
    let payload = json!({"model":QWEN_MODEL_ID,"stream":false,"messages":[{"role":"user","content":[{"type":"input_audio","input_audio":{"data":format!("data:audio/wav;base64,{}",BASE64.encode(wav))}}]}],"asr_options":options});
    let response = client
        .post(QWEN_ENDPOINT)
        .bearer_auth(key)
        .json(&payload)
        .send()
        .map_err(|error| {
            (
                "QWEN_NETWORK".to_owned(),
                "千问3-ASR 连接失败，请检查网络后重试".to_owned(),
                format!("request failed: {}", error.without_url()),
            )
        })?;
    let status = response.status();
    if !status.is_success() {
        return Err((
            format!("QWEN_HTTP_{}", status.as_u16()),
            if matches!(status.as_u16(), 401 | 403) {
                "千问3-ASR 鉴权失败，请检查 API Key"
            } else if status.as_u16() == 429 {
                "千问3-ASR 请求过于频繁，请稍后重试"
            } else {
                "千问3-ASR 请求失败，请稍后重试"
            }
            .to_owned(),
            format!("HTTP {}", status.as_u16()),
        ));
    }
    let value: Value = response.json().map_err(|_| {
        (
            "QWEN_RESPONSE_INVALID".to_owned(),
            "千问3-ASR 返回格式异常，请稍后重试".to_owned(),
            "invalid JSON response".to_owned(),
        )
    })?;
    let text = value
        .pointer("/choices/0/message/content")
        .and_then(Value::as_str)
        .unwrap_or("")
        .trim()
        .to_owned();
    if text.is_empty() {
        return Err((
            "QWEN_EMPTY_TRANSCRIPT".to_owned(),
            "千问3-ASR 未返回转写文字，请重试".to_owned(),
            "empty choices.message.content".to_owned(),
        ));
    }
    Ok(QwenResponse {
        text,
        input_tokens: value.pointer("/usage/input_tokens").and_then(Value::as_u64),
        output_tokens: value
            .pointer("/usage/output_tokens")
            .and_then(Value::as_u64),
    })
}

fn find_binary(name: &str) -> Option<PathBuf> {
    [
        format!("/opt/homebrew/bin/{name}"),
        format!("/usr/local/bin/{name}"),
        format!("/usr/bin/{name}"),
    ]
    .into_iter()
    .map(PathBuf::from)
    .find(|path| path.is_file())
}

fn probe_duration(ffprobe: &Path, source: &Path) -> Result<u64, String> {
    let output = Command::new(ffprobe)
        .args([
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
        ])
        .arg(source)
        .output()
        .map_err(|_| "ffprobe launch failed".to_owned())?;
    if !output.status.success() {
        return Err(format!(
            "ffprobe exit={}",
            output.status.code().unwrap_or(-1)
        ));
    }
    let seconds: f64 = String::from_utf8_lossy(&output.stdout)
        .trim()
        .parse()
        .map_err(|_| "ffprobe duration invalid".to_owned())?;
    if !seconds.is_finite() || seconds <= 0.0 {
        return Err("ffprobe duration not positive".to_owned());
    }
    Ok((seconds * 1000.0).ceil() as u64)
}

fn decode_chunk(
    ffmpeg: &Path,
    source: &Path,
    target: &Path,
    start: u64,
    duration: u64,
) -> Result<(), String> {
    let output = Command::new(ffmpeg)
        .args([
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
            "-ss",
            &format!("{:.3}", start as f64 / 1000.0),
            "-t",
            &format!("{:.3}", duration as f64 / 1000.0),
            "-i",
        ])
        .arg(source)
        .args([
            "-vn",
            "-ac",
            "1",
            "-ar",
            "16000",
            "-c:a",
            "pcm_s16le",
            "-f",
            "wav",
            "-y",
        ])
        .arg(target)
        .output()
        .map_err(|_| "ffmpeg launch failed".to_owned())?;
    if !output.status.success() {
        return Err(format!(
            "ffmpeg exit={} stderr={}",
            output.status.code().unwrap_or(-1),
            String::from_utf8_lossy(&output.stderr)
                .chars()
                .take(240)
                .collect::<String>()
        ));
    }
    let length = fs::metadata(target)
        .map_err(|_| "decoded chunk missing".to_owned())?
        .len();
    if length <= 44 {
        return Err("decoded chunk empty".to_owned());
    }
    Ok(())
}

fn current_state(database: &Path, task_id: &str) -> Result<String, String> {
    open(database)?
        .query_row(
            "SELECT state FROM desktop_transcription_tasks WHERE id=?1",
            [task_id],
            |row| row.get(0),
        )
        .map_err(|_| "任务状态不可用".to_owned())
}

fn render_markdown(task: &TaskProjection) -> String {
    if task.model_id == GLM_OCR_MODEL_ID {
        return task
            .segments
            .iter()
            .map(|item| item.text.as_str())
            .collect::<Vec<_>>()
            .join("\n\n");
    }
    let mut out = format!("# {}\n\n- 模型：{}\n- 语言：{}\n- 计费音频：{} ms\n- 费用：¥{:.6}（估算）\n\n## 转写正文\n\n{}\n\n## 时间轴\n\n", task.source_display_name,task.model_id,task.language_code.as_deref().unwrap_or("自动识别"),task.billable_audio_millis,task.estimated_charge_micros as f64/1_000_000.0,task.segments.iter().map(|item| item.text.as_str()).collect::<Vec<_>>().join("\n\n"));
    for segment in &task.segments {
        out.push_str(&format!(
            "- {} → {}  {}\n",
            format_time(segment.start_millis),
            format_time(segment.end_millis),
            segment.text
        ));
    }
    out
}

fn render_export(task: &TaskProjection, format: &str) -> Result<Vec<u8>, String> {
    match format {
        "txt" => Ok(task
            .segments
            .iter()
            .map(|item| item.text.as_str())
            .collect::<Vec<_>>()
            .join("\n\n")
            .into_bytes()),
        "md" => Ok(render_markdown(task).into_bytes()),
        "srt" => Ok(task
            .segments
            .iter()
            .enumerate()
            .map(|(index, item)| {
                format!(
                    "{}\n{} --> {}\n{}\n",
                    index + 1,
                    format_srt(item.start_millis),
                    format_srt(item.end_millis),
                    item.text
                )
            })
            .collect::<Vec<_>>()
            .join("\n")
            .into_bytes()),
        "docx" => render_docx(task),
        _ => Err("导出格式无效".to_owned()),
    }
}

fn render_docx(task: &TaskProjection) -> Result<Vec<u8>, String> {
    let mut cursor = std::io::Cursor::new(Vec::new());
    {
        let mut zip = ZipWriter::new(&mut cursor);
        let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
        zip.start_file("[Content_Types].xml", options)
            .map_err(|_| "DOCX 无法创建".to_owned())?;
        zip.write_all(br#"<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"#).map_err(|_| "DOCX 无法写入".to_owned())?;
        zip.start_file("_rels/.rels", options)
            .map_err(|_| "DOCX 无法创建".to_owned())?;
        zip.write_all(br#"<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"#).map_err(|_| "DOCX 无法写入".to_owned())?;
        let paragraphs = task
            .segments
            .iter()
            .map(|item| {
                format!(
                    "<w:p><w:r><w:t xml:space=\"preserve\">{}</w:t></w:r></w:p>",
                    xml_escape(&item.text)
                )
            })
            .collect::<String>();
        zip.start_file("word/document.xml", options)
            .map_err(|_| "DOCX 无法创建".to_owned())?;
        zip.write_all(format!(r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>{}</w:t></w:r></w:p>{}<w:sectPr/></w:body></w:document>"#,xml_escape(&task.source_display_name),paragraphs).as_bytes()).map_err(|_| "DOCX 无法写入".to_owned())?;
        zip.finish().map_err(|_| "DOCX 无法完成".to_owned())?;
    }
    Ok(cursor.into_inner())
}

fn index_task_source(transaction: &Transaction<'_>, task_id: &str) -> Result<(), String> {
    let row: Option<(String,String,String,String,String,String,u64,i64)> = transaction.query_row("SELECT workspace_id,source_attachment_id,source_mime_type,source_display_name,model_id,state,source_byte_count,created_at_ms FROM desktop_transcription_tasks WHERE id=?1", [task_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?,row.get(4)?,row.get(5)?,row.get(6)?,row.get(7)?))).optional().map_err(|_| "无法读取南枫转写源索引".to_owned())?;
    let Some((workspace, attachment, mime, name, model_id, state, bytes, created)) = row else {
        return Ok(());
    };
    let kind = if mime.starts_with("video/") {
        "VIDEO"
    } else if mime.starts_with("audio/") {
        "AUDIO"
    } else if mime.starts_with("image/") {
        "IMAGE"
    } else {
        "FILE"
    };
    let source_label = if model_id == GLM_OCR_MODEL_ID {
        "南枫转写 · 原始文件"
    } else {
        "南枫转写 · 语音"
    };
    let file_type = if mime == "application/pdf" {
        "pdf"
    } else {
        "other"
    };
    transaction.execute("INSERT OR REPLACE INTO desktop_local_search_index(workspace_id,entry_id,conversation_id,message_id,attachment_id,content_kind,title,normalized_text,snippet,timestamp,mime_type,display_name,file_type,byte_count,branch_leaf_id,source_label,archived,conversation_revision,title_match) VALUES(?1,?2,?3,NULL,?4,?5,?6,?7,?8,datetime(?9/1000,'unixepoch'),?10,?6,?11,?12,NULL,?13,0,0,0)", params![workspace,format!("{task_id}:source"),search_owner_id(task_id),attachment,kind,name,format!("{} {} {}",name,mime,state).to_lowercase(),format!("{} · {}",name,state),created,mime,file_type,bytes,source_label]).map_err(|_| "南枫转写源索引未保存".to_owned())?;
    Ok(())
}

fn index_task_result(transaction: &Transaction<'_>, task_id: &str) -> Result<(), String> {
    let row: Option<(String,Option<String>,String,String,i64)> = transaction.query_row("SELECT workspace_id,result_attachment_id,source_display_name,model_id,updated_at_ms FROM desktop_transcription_tasks WHERE id=?1", [task_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?,row.get(4)?))).optional().map_err(|_| "无法读取南枫转写结果索引".to_owned())?;
    let Some((workspace, Some(attachment), name, model_id, updated)) = row else {
        return Ok(());
    };
    let text: String=transaction.query_row("SELECT COALESCE(GROUP_CONCAT(text,' '),'') FROM desktop_transcription_segments WHERE task_id=?1",[task_id],|row|row.get(0)).map_err(|_|"无法读取转写正文索引".to_owned())?;
    let source_label = if model_id == GLM_OCR_MODEL_ID {
        "南枫转写 · Markdown"
    } else {
        "南枫转写 · 语音"
    };
    transaction.execute("INSERT OR REPLACE INTO desktop_local_search_index(workspace_id,entry_id,conversation_id,message_id,attachment_id,content_kind,title,normalized_text,snippet,timestamp,mime_type,display_name,file_type,byte_count,branch_leaf_id,source_label,archived,conversation_revision,title_match) SELECT ?1,?2,?3,NULL,?4,'TEXT',?5,?6,?7,datetime(?8/1000,'unixepoch'),'text/markdown',display_name,'markdown',byte_count,NULL,?9,0,0,0 FROM desktop_conversation_attachments WHERE workspace_id=?1 AND attachment_id=?4", params![workspace,format!("{task_id}:result"),search_owner_id(task_id),attachment,name,text.to_lowercase(),text.chars().take(240).collect::<String>(),updated,source_label]).map_err(|_| "南枫转写结果索引未保存".to_owned())?;
    Ok(())
}

fn search_owner_id(task_id: &str) -> String {
    format!("transcription-task:{task_id}")
}
fn mime_for_extension(ext: &str) -> &'static str {
    match ext {
        "mp3" => "audio/mpeg",
        "wav" => "audio/wav",
        "m4a" | "aac" => "audio/mp4",
        "flac" => "audio/flac",
        "ogg" | "opus" => "audio/ogg",
        "wma" => "audio/x-ms-wma",
        "mp4" | "m4v" | "mov" => "video/mp4",
        "webm" => "video/webm",
        "mkv" => "video/x-matroska",
        "avi" => "video/x-msvideo",
        "3gp" => "video/3gpp",
        "mpeg" | "mpg" => "video/mpeg",
        _ => "application/octet-stream",
    }
}
fn safe_display_name(path: &Path) -> Result<String, String> {
    let name = path
        .file_name()
        .and_then(|v| v.to_str())
        .ok_or_else(|| "音视频文件名无效".to_owned())?;
    if name.chars().count() > 180 || name.contains('/') || name.contains('\\') {
        Err("音视频文件名无效".to_owned())
    } else {
        Ok(name.to_owned())
    }
}
fn copy_and_hash(source: &Path, target: &Path) -> Result<(String, u64), String> {
    let mut input = fs::File::open(source).map_err(|_| "所选音视频不可读".to_owned())?;
    let mut output = fs::File::create(target).map_err(|_| "无法创建音视频暂存副本".to_owned())?;
    let mut digest = Sha256::new();
    let mut total = 0u64;
    let mut buffer = [0u8; 1024 * 1024];
    loop {
        let count = input
            .read(&mut buffer)
            .map_err(|_| "音视频复制失败".to_owned())?;
        if count == 0 {
            break;
        }
        total = total.saturating_add(count as u64);
        if total > MAX_SOURCE_BYTES {
            return Err("音视频超过 2 GiB".to_owned());
        }
        digest.update(&buffer[..count]);
        output
            .write_all(&buffer[..count])
            .map_err(|_| "音视频复制失败".to_owned())?;
    }
    output
        .sync_all()
        .map_err(|_| "音视频暂存未同步".to_owned())?;
    Ok((format!("{:x}", digest.finalize()), total))
}
fn write_atomic(target: &Path, bytes: &[u8]) -> Result<(), String> {
    let parent = target.parent().ok_or_else(|| "导出目录无效".to_owned())?;
    fs::create_dir_all(parent).map_err(|_| "无法创建输出目录".to_owned())?;
    let pending = parent.join(format!(".nanfeng-transcription-{}.pending", random_hex(8)?));
    let mut file = fs::File::create(&pending).map_err(|_| "无法创建输出暂存文件".to_owned())?;
    file.write_all(bytes)
        .map_err(|_| "输出文件写入失败".to_owned())?;
    file.sync_all().map_err(|_| "输出文件未同步".to_owned())?;
    fs::rename(&pending, target).map_err(|_| "输出文件无法原子发布".to_owned())
}
fn random_hex(size: usize) -> Result<String, String> {
    let mut bytes = vec![0u8; size];
    getrandom::fill(&mut bytes).map_err(|_| "无法生成本地安全 ID".to_owned())?;
    Ok(hex::encode(bytes))
}
fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
        .min(i64::MAX as u128) as i64
}
fn hex_digest(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}
fn format_time(ms: u64) -> String {
    format!(
        "{:02}:{:02}:{:02}.{:03}",
        ms / 3_600_000,
        (ms / 60_000) % 60,
        (ms / 1000) % 60,
        ms % 1000
    )
}
fn format_srt(ms: u64) -> String {
    format_time(ms).replace('.', ",")
}
fn xml_escape(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&apos;")
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    fn database() -> (tempfile::TempDir, PathBuf, PathBuf) {
        let dir = tempdir().unwrap();
        let root = dir.path().join("app");
        fs::create_dir_all(root.join("assets")).unwrap();
        fs::create_dir_all(root.join("staging")).unwrap();
        let db = root.join("workspace.sqlite3");
        let connection = Connection::open(&db).unwrap();
        connection.execute_batch("PRAGMA foreign_keys=ON;CREATE TABLE workspaces(id TEXT PRIMARY KEY,title TEXT,semantic_hash TEXT,package_hash TEXT,created_at TEXT);CREATE TABLE desktop_attachment_assets(sha256 TEXT PRIMARY KEY,byte_count INTEGER,created_at_ms INTEGER,last_referenced_at_ms INTEGER,last_unreferenced_at_ms INTEGER,reference_count INTEGER);CREATE TABLE desktop_conversation_attachments(workspace_id TEXT,attachment_id TEXT,mime_type TEXT,display_name TEXT,byte_count INTEGER,sha256 TEXT,created_at TEXT,PRIMARY KEY(workspace_id,attachment_id),UNIQUE(workspace_id,sha256));CREATE TABLE desktop_local_search_index(workspace_id TEXT,entry_id TEXT,conversation_id TEXT,message_id TEXT,attachment_id TEXT,content_kind TEXT,title TEXT,normalized_text TEXT,snippet TEXT,timestamp TEXT,mime_type TEXT,display_name TEXT,file_type TEXT,byte_count INTEGER,branch_leaf_id TEXT,source_label TEXT,archived INTEGER,conversation_revision INTEGER,title_match INTEGER,PRIMARY KEY(workspace_id,entry_id));INSERT INTO workspaces VALUES('workspace-test','Test','','','');").unwrap();
        migrate(&connection).unwrap();
        drop(connection);
        (dir, root, db)
    }

    #[test]
    fn migration_recovers_active_tasks_and_keeps_experimental_model_unavailable() {
        let (_dir, _root, db) = database();
        let connection = open(&db).unwrap();
        connection.execute("INSERT INTO desktop_transcription_tasks(id,workspace_id,source_attachment_id,source_sha256,source_mime_type,source_display_name,source_byte_count,model_id,state,created_at_ms,updated_at_ms) VALUES('transcription-active','workspace-test','attachment-a','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','audio/wav','a.wav',1,?1,'TRANSCRIBING',1,1)",[QWEN_MODEL_ID]).unwrap();
        migrate(&connection).unwrap();
        assert_eq!(
            read_task(&connection, "transcription-active")
                .unwrap()
                .state,
            "RECOVERY_REQUIRED"
        );
        assert!(!read_settings(&connection).unwrap().sense_voice_available);
    }

    #[test]
    fn import_is_streamed_persistent_and_referenced_without_recording_or_pause_states() {
        let (dir, root, db) = database();
        let source = dir.path().join("fixture.wav");
        fs::write(&source, b"RIFF safe fixture").unwrap();
        let task = import_source(
            &root,
            &db,
            ImportArgs {
                workspace_id: "workspace-test".into(),
                selected_path: source.to_string_lossy().into_owned(),
            },
        )
        .unwrap();
        assert_eq!(task.state, "QUEUED");
        assert!(root.join("assets").read_dir().unwrap().next().is_some());
        let state = read_state(&db, Some("workspace-test")).unwrap();
        assert_eq!(state.tasks.len(), 1);
        assert!(!["RECORDING", "PAUSED"].contains(&state.tasks[0].state.as_str()));
        let connection = open(&db).unwrap();
        let mut counts = std::collections::BTreeMap::new();
        let transaction = connection.unchecked_transaction().unwrap();
        add_attachment_references(&transaction, &mut counts).unwrap();
        assert_eq!(counts.values().sum::<u64>(), 1);
    }

    #[test]
    fn import_without_selected_workspace_uses_private_transcription_space() {
        let (dir, root, db) = database();
        let source = dir.path().join("standalone.wav");
        fs::write(&source, b"RIFF standalone fixture").unwrap();
        let task = import_source(
            &root,
            &db,
            ImportArgs {
                workspace_id: String::new(),
                selected_path: source.to_string_lossy().into_owned(),
            },
        )
        .unwrap();
        assert_eq!(task.workspace_id, TRANSCRIPTION_WORKSPACE_ID);
        assert_eq!(cancel_task(&db, &task.id).unwrap().state, "CANCELLED");
        assert_eq!(retry_task(&db, &task.id).unwrap().state, "QUEUED");
        let connection = open(&db).unwrap();
        let title: String = connection
            .query_row(
                "SELECT title FROM workspaces WHERE id=?1",
                [TRANSCRIPTION_WORKSPACE_ID],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(title, "南枫转写（本地）");
    }

    #[test]
    fn document_import_uses_glm_ocr_and_searches_as_image_not_audio() {
        let (dir, root, db) = database();
        let source = dir.path().join("receipt.png");
        fs::write(&source, b"safe png fixture").unwrap();
        let task = import_document_source(
            &root,
            &db,
            ImportArgs {
                workspace_id: "workspace-test".into(),
                selected_path: source.to_string_lossy().into_owned(),
            },
        )
        .unwrap();
        assert_eq!(task.model_id, GLM_OCR_MODEL_ID);
        assert_eq!(task.source_mime_type, "image/png");
        assert_eq!(task.state, "QUEUED");
        let connection = open(&db).unwrap();
        let indexed: (String, String) = connection.query_row("SELECT content_kind,source_label FROM desktop_local_search_index WHERE entry_id=?1", [format!("{}:source", task.id)], |row| Ok((row.get(0)?, row.get(1)?))).unwrap();
        assert_eq!(indexed, ("IMAGE".into(), "南枫转写 · 原始文件".into()));
    }

    #[test]
    fn document_missing_credential_attempt_is_persistent_recoverable_and_never_reaches_provider() {
        let (dir, root, db) = database();
        for (name, bytes) in [
            ("receipt.png", b"real-local-png-fixture".as_slice()),
            (
                "statement.pdf",
                b"%PDF-1.4\nreal-local-pdf-fixture\n%%EOF".as_slice(),
            ),
        ] {
            let source = dir.path().join(name);
            fs::write(&source, bytes).unwrap();
            let task = import_document_source(
                &root,
                &db,
                ImportArgs {
                    workspace_id: "workspace-test".into(),
                    selected_path: source.to_string_lossy().into_owned(),
                },
            )
            .unwrap();

            assert_eq!(
                run_ocr_task(&root, &db, &task.id, &[]).unwrap_err(),
                "智谱 GLM-OCR 未配置 API Key，请在设置中保存后重试"
            );
            let failed = read_state(&db, Some("workspace-test"))
                .unwrap()
                .tasks
                .into_iter()
                .find(|candidate| candidate.id == task.id)
                .unwrap();
            assert_eq!(failed.state, "FAILED");
            assert_eq!(failed.error_code.as_deref(), Some("ZHIPU_API_KEY_MISSING"));
            assert_eq!(failed.attempt_count, 1);
            assert_eq!(failed.provider_request_count, 0);
            assert_eq!(
                fs::read(root.join("assets").join(hex_digest(bytes))).unwrap(),
                bytes
            );

            assert_eq!(retry_task(&db, &task.id).unwrap().state, "QUEUED");
            assert_eq!(cancel_task(&db, &task.id).unwrap().state, "CANCELLED");
            assert_eq!(retry_task(&db, &task.id).unwrap().state, "QUEUED");
        }
    }

    #[test]
    fn glm_ocr_response_variants_keep_exact_markdown_and_raw_markdown_export() {
        let response = json!({"md_results":[{"markdown":"# 第一页"},{"content":"第二页"}]});
        assert_eq!(glm_ocr_markdown(&response), "# 第一页\n\n第二页");
        let mut task = TaskProjection {
            id: "ocr-test".into(),
            workspace_id: "workspace-test".into(),
            source_attachment_id: "attachment-a".into(),
            source_mime_type: "application/pdf".into(),
            source_display_name: "资料.pdf".into(),
            source_byte_count: 1,
            model_id: GLM_OCR_MODEL_ID.into(),
            language_code: None,
            state: "COMPLETED".into(),
            progress_millis: 0,
            total_duration_millis: None,
            error_code: None,
            user_message: None,
            technical_detail: None,
            attempt_count: 1,
            result_attachment_id: None,
            provider_request_count: 1,
            provider_request_id: Some("request-safe".into()),
            page_count: Some(2),
            billable_audio_millis: 0,
            input_tokens: Some(2),
            output_tokens: Some(3),
            estimated_charge_micros: 1,
            created_at_millis: 1,
            updated_at_millis: 2,
            segments: Vec::new(),
        };
        task.segments.push(SegmentProjection {
            ordinal: 0,
            start_millis: 0,
            end_millis: 0,
            text: "# 原样 Markdown".into(),
        });
        assert_eq!(
            String::from_utf8(render_export(&task, "md").unwrap()).unwrap(),
            "# 原样 Markdown"
        );
    }

    #[test]
    fn glm_ocr_request_body_streams_exact_segmented_base64_json() {
        let dir = tempdir().unwrap();
        let source = dir.path().join("source.bin");
        let bytes = (0..24_581)
            .map(|index| (index % 251) as u8)
            .collect::<Vec<_>>();
        fs::write(&source, &bytes).unwrap();
        let mut body = GlmOcrJsonBody::new(fs::File::open(source).unwrap(), "application/pdf");
        let mut output = String::new();
        body.read_to_string(&mut output).unwrap();
        assert_eq!(
            output,
            format!(
                "{{\"model\":\"glm-ocr\",\"file\":\"data:application/pdf;base64,{}\"}}",
                BASE64.encode(bytes)
            )
        );
    }

    #[test]
    fn export_formats_are_bounded_and_docx_is_a_real_zip() {
        let task = TaskProjection {
            id: "transcription-test".into(),
            workspace_id: "workspace-test".into(),
            source_attachment_id: "attachment-a".into(),
            source_mime_type: "audio/wav".into(),
            source_display_name: "采访.wav".into(),
            source_byte_count: 1,
            model_id: QWEN_MODEL_ID.into(),
            language_code: Some("zh".into()),
            state: "COMPLETED".into(),
            progress_millis: 1000,
            total_duration_millis: Some(1000),
            error_code: None,
            user_message: None,
            technical_detail: None,
            attempt_count: 1,
            result_attachment_id: None,
            provider_request_count: 1,
            provider_request_id: None,
            page_count: None,
            billable_audio_millis: 1000,
            input_tokens: Some(2),
            output_tokens: Some(3),
            estimated_charge_micros: 220,
            created_at_millis: 1,
            updated_at_millis: 2,
            segments: vec![SegmentProjection {
                ordinal: 0,
                start_millis: 0,
                end_millis: 1000,
                text: "你好 & world".into(),
            }],
        };
        assert!(String::from_utf8(render_export(&task, "srt").unwrap())
            .unwrap()
            .contains("00:00:01,000"));
        let docx = render_export(&task, "docx").unwrap();
        assert!(docx.starts_with(b"PK"));
        assert!(String::from_utf8(render_export(&task, "md").unwrap())
            .unwrap()
            .contains("（估算）"));
    }
}
