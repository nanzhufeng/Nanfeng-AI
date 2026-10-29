//! One-time physical split. Original SQLite snapshot is retained outside both active stores.
//! Both row sets commit through SQLite's rollback super-journal, before runtime owners start.
use super::*;
const LEGACY_TABLES: &str = "chatgpt_import_items chatgpt_import_messages chatgpt_import_provenance chatgpt_import_receipts chatgpt_import_tasks claude_import_items claude_import_messages claude_import_provenance claude_import_receipts claude_import_tasks desktop_app_settings desktop_attachment_assets desktop_attachment_staging desktop_audio_preview_positions desktop_background_runtime_v1 desktop_cloud_account_state desktop_compare_executions desktop_conversation_attachments desktop_conversation_draft_attachments_v1 desktop_conversation_drafts_v1 desktop_conversation_favorites desktop_conversation_read_markers_v1 desktop_conversation_title_attempts desktop_history_knowledge_candidates_v1 desktop_history_knowledge_checkpoints_v1 desktop_history_knowledge_schedule_v1 desktop_local_search_history desktop_local_search_index desktop_local_search_index_state desktop_ordinary_chat_attempts desktop_ordinary_chat_context_sources desktop_ordinary_chat_diagnostics desktop_pdf_preview_positions desktop_portable_personalization_v1 desktop_provider_settings desktop_recovery_rotation desktop_reminder_drafts_v1 desktop_reminder_plans_v1 desktop_reminder_runs_v1 desktop_selected_conversation_sync desktop_sync_diagnostics desktop_sync_jobs desktop_sync_notifications desktop_temporary_attachments desktop_temporary_recovery desktop_transcription_segments desktop_transcription_settings desktop_transcription_tasks desktop_video_preview_positions domain_intents exchange_v2_assets exchange_v2_import_journal exchange_v2_import_receipts exchange_v2_imports exchange_v2_owner_provenance import_journal local_exact_reuse_entries model_metadata nanfeng_knowledge_import_items nanfeng_knowledge_import_messages nanfeng_knowledge_import_provenance nanfeng_knowledge_import_receipts nanfeng_knowledge_import_tasks object_provenance p6g_catalog p6g_conversation_override p6g_global_default p6g_route_metadata p6k_profile_import_provenance p6k_profile_import_receipts p6k_profile_personalization_settings p6k_zip_asset_link_receipts p6k_zip_import_asset_candidates p6k_zip_import_entries p6k_zip_import_items p6k_zip_import_messages p6k_zip_import_profile_candidates p6k_zip_import_provenance p6k_zip_import_receipts p6k_zip_import_tasks p6k_zip_official_identity_ledger sync_account_metadata sync_intents window_layout workspace_assets workspace_exchange workspaces desktop_data_area_v1";

pub(crate) fn verify_required_singletons(connection: &Connection) -> Result<(), String> {
    for table in [
        "desktop_app_settings",
        "desktop_background_runtime_v1",
        "desktop_history_knowledge_schedule_v1",
        "desktop_portable_personalization_v1",
        "desktop_transcription_settings",
    ] {
        let count: i64 = connection
            .query_row(&format!("SELECT COUNT(*) FROM {table} WHERE id=1"), [], |row| row.get(0))
            .map_err(|_| json_error(&format!("区域必需设置结构不可读：{table}")))?;
        if count != 1 {
            return Err(json_error(&format!("区域必需设置缺失：{table}")));
        }
    }
    Ok(())
}

pub(crate) fn plain_directory(path: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|_| json_error("区域迁移目录不可读"))?;
    if !metadata.is_dir() || metadata.file_type().is_symlink() {
        return Err(json_error("区域迁移目录是链接或不是目录"));
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        use windows::Win32::Storage::FileSystem::FILE_ATTRIBUTE_REPARSE_POINT;
        if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 {
            return Err(json_error("区域迁移目录是重解析点"));
        }
    }
    Ok(())
}

pub(crate) fn plain_file_if_present(path: &Path) -> Result<bool, String> {
    let metadata = match fs::symlink_metadata(path) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(false),
        Err(_) => return Err(json_error("区域迁移文件状态不可读")),
    };
    if !metadata.is_file() || metadata.file_type().is_symlink() {
        return Err(json_error("区域迁移文件是链接或不是普通文件"));
    }
    #[cfg(windows)]
    {
        use std::os::windows::{fs::MetadataExt, io::AsRawHandle};
        use windows::Win32::{
            Foundation::HANDLE,
            Storage::FileSystem::{
                GetFileInformationByHandle, BY_HANDLE_FILE_INFORMATION,
                FILE_ATTRIBUTE_REPARSE_POINT,
            },
        };
        if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 {
            return Err(json_error("区域迁移文件是重解析点"));
        }
        let file = fs::File::open(path).map_err(|_| json_error("区域迁移文件不可读"))?;
        let mut info = BY_HANDLE_FILE_INFORMATION::default();
        unsafe { GetFileInformationByHandle(HANDLE(file.as_raw_handle()), &mut info) }
            .map_err(|_| json_error("区域迁移文件链接数不可验证"))?;
        if info.nNumberOfLinks != 1 {
            return Err(json_error("区域迁移文件存在额外硬链接"));
        }
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::MetadataExt;
        if metadata.nlink() != 1 {
            return Err(json_error("区域迁移文件存在额外硬链接"));
        }
    }
    Ok(true)
}

pub(crate) fn plain_directory_if_present(path: &Path) -> Result<(), String> {
    match fs::symlink_metadata(path) {
        Ok(_) => plain_directory(path),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(_) => Err(json_error("区域迁移目录状态不可读")),
    }
}

fn ensure_plain_child_directories(root: &Path, relative: &Path) -> Result<PathBuf, String> {
    plain_directory(root)?;
    let mut current = root.to_path_buf();
    for component in relative.components() {
        let std::path::Component::Normal(name) = component else {
            return Err(json_error("工作区文件目录无效"));
        };
        current.push(name);
        match fs::symlink_metadata(&current) {
            Ok(_) => plain_directory(&current)?,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                fs::create_dir(&current).map_err(|_| json_error("工作区文件目录不可创建"))?;
                plain_directory(&current)?;
            }
            Err(_) => return Err(json_error("工作区文件目录不可读")),
        }
    }
    Ok(current)
}

pub(crate) fn commit_staged_area_file(staging: &Path, target: &Path) -> Result<(), String> {
    #[cfg(windows)]
    {
        use std::os::windows::ffi::OsStrExt;
        use windows::{
            core::PCWSTR,
            Win32::Storage::FileSystem::{MoveFileExW, MOVEFILE_WRITE_THROUGH},
        };
        let wide = |path: &Path| {
            path.as_os_str()
                .encode_wide()
                .chain(std::iter::once(0))
                .collect::<Vec<_>>()
        };
        let from = wide(staging);
        let to = wide(target);
        unsafe {
            MoveFileExW(
                PCWSTR(from.as_ptr()),
                PCWSTR(to.as_ptr()),
                MOVEFILE_WRITE_THROUGH,
            )
        }
        .map_err(|_| json_error("WORK 私有文件暂存未提交"))
    }
    #[cfg(not(windows))]
    {
        // The app's root process lock excludes a second migration writer.
        fs::rename(staging, target).map_err(|_| json_error("WORK 私有文件暂存未提交"))
    }
}

fn copy_verified_area_file(
    source: &Path,
    target: &Path,
    expected_hash: &str,
    pending: &Path,
) -> Result<(), String> {
    if !plain_file_if_present(pending)?
        || fs::read(pending).map_err(|_| json_error("私有文件迁移标记不可读"))? != b"1"
    {
        return Err(json_error("私有文件迁移标记无效，未复制"));
    }
    if !plain_file_if_present(source)? {
        return Err(json_error("旧区域引用的私有文件缺失，未提交迁移"));
    }
    if sha256_file(source)? != expected_hash {
        return Err(json_error("旧区域引用的私有文件校验失败，未提交迁移"));
    }
    if plain_file_if_present(target)? {
        if sha256_file(target)? == expected_hash {
            return Ok(());
        }
        return Err(json_error("WORK 已有同名但内容不同的私有文件，未覆盖"));
    }
    let mut staging_name = target.as_os_str().to_os_string();
    staging_name.push(".nfai-split-copying");
    let staging = PathBuf::from(staging_name);
    if plain_file_if_present(&staging)? && sha256_file(&staging)? != expected_hash {
        // Only this migration writes the adjacent, fixed-suffix staging file.
        // A partial write is discarded on retry; the final target is untouched.
        fs::remove_file(&staging).map_err(|_| json_error("WORK 不完整的迁移副本不可清理"))?;
    }
    if !plain_file_if_present(&staging)? {
        use std::io::Write;
        let mut input = fs::File::open(source).map_err(|_| json_error("旧区域私有文件不可读"))?;
        let mut output = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&staging)
            .map_err(|_| json_error("WORK 私有文件暂存不可独占创建"))?;
        std::io::copy(&mut input, &mut output)
            .map(|_| ())
            .and_then(|_| output.flush())
            .and_then(|_| output.sync_all())
            .map_err(|_| json_error("WORK 私有文件暂存未完整保存"))?;
    }
    if sha256_file(&staging)? != expected_hash {
        return Err(json_error("WORK 私有文件暂存校验失败"));
    }
    if plain_file_if_present(target)? {
        return Err(json_error("WORK 私有文件目标在迁移期间出现，未覆盖"));
    }
    commit_staged_area_file(&staging, target)?;
    if !plain_file_if_present(target)? || sha256_file(target)? != expected_hash {
        return Err(json_error("WORK 私有文件提交后校验失败"));
    }
    Ok(())
}

fn verify_before_split_snapshot(backup: &Path, source: &Connection) -> Result<(), String> {
    let snapshot = Connection::open_with_flags(backup, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| json_error("旧库备份不可读，未继续分库"))?;
    let integrity: String = snapshot
        .query_row("PRAGMA integrity_check", [], |row| row.get(0))
        .map_err(|_| json_error("旧库备份完整性不可验证"))?;
    let version: u32 = snapshot
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| json_error("旧库备份版本不可读"))?;
    if integrity != "ok"
        || !matches!(
            desktop_schema_source::classify(&snapshot, version),
            Ok(desktop_schema_source::SchemaSource::Common45 {
                area,
                split_done: false
            }) if area == "CHAT"
        )
    {
        return Err(json_error("旧库备份不是完整的未拆分 Schema45"));
    }
    let schema =
        |db: &Connection| -> Result<Vec<(String, String, String, Option<String>)>, String> {
            db.prepare("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type,name")
            .and_then(|mut statement| {
                statement.query_map([], |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)))?.collect()
            })
            .map_err(|_| json_error("区域迁移结构不可核对"))
        };
    let source_schema = schema(source)?;
    let tables = source_schema
        .iter()
        .filter(|(kind, _, _, _)| kind == "table")
        .map(|(_, name, _, _)| name.as_str())
        .collect::<BTreeSet<_>>();
    if tables != LEGACY_TABLES.split_whitespace().collect::<BTreeSet<_>>() {
        return Err(json_error("区域迁移源表清单不完整"));
    }
    if source_schema != schema(&snapshot)? {
        return Err(json_error("旧库备份结构与当前源库不一致"));
    }
    for table in LEGACY_TABLES.split_whitespace() {
        let columns: Vec<String> = source
            .prepare("SELECT name FROM pragma_table_info(?1) ORDER BY cid")
            .and_then(|mut statement| statement.query_map([table], |row| row.get(0))?.collect())
            .map_err(|_| json_error("区域迁移字段不可核对"))?;
        if columns.is_empty()
            || columns.iter().any(|name| {
                !name
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || byte == b'_')
            })
        {
            return Err(json_error("区域迁移字段名无效"));
        }
        let order = columns
            .iter()
            .map(|name| format!("typeof(\"{name}\"),CAST(\"{name}\" AS BLOB)"))
            .collect::<Vec<_>>()
            .join(",");
        let sql = format!("SELECT * FROM \"{table}\" ORDER BY {order}");
        let mut source_statement = source
            .prepare(&sql)
            .map_err(|_| json_error("源库业务表不可核对"))?;
        let mut backup_statement = snapshot
            .prepare(&sql)
            .map_err(|_| json_error("旧库备份业务表不可核对"))?;
        if source_statement.column_count() != columns.len()
            || backup_statement.column_count() != columns.len()
        {
            return Err(json_error("旧库备份业务表字段数量不一致"));
        }
        let mut source_rows = source_statement
            .query([])
            .map_err(|_| json_error("源库业务表不可读取"))?;
        let mut backup_rows = backup_statement
            .query([])
            .map_err(|_| json_error("旧库备份业务表不可读取"))?;
        loop {
            let left = source_rows
                .next()
                .map_err(|_| json_error("源库业务行不可读取"))?;
            let right = backup_rows
                .next()
                .map_err(|_| json_error("旧库备份业务行不可读取"))?;
            match (left, right) {
                (None, None) => break,
                (Some(left), Some(right)) => {
                    for index in 0..columns.len() {
                        let left = left
                            .get_ref(index)
                            .map_err(|_| json_error("源库业务值不可读取"))?;
                        let right = right
                            .get_ref(index)
                            .map_err(|_| json_error("旧库备份业务值不可读取"))?;
                        let equal = match (left, right) {
                            (
                                rusqlite::types::ValueRef::Real(a),
                                rusqlite::types::ValueRef::Real(b),
                            ) => a.to_bits() == b.to_bits(),
                            _ => left == right,
                        };
                        if !equal {
                            return Err(json_error("旧库备份业务数据与当前源库不一致"));
                        }
                    }
                }
                _ => return Err(json_error("旧库备份业务数据与当前源库不一致")),
            }
        }
    }
    Ok(())
}

fn run_sql(connection: &Connection, sql: &str) -> Result<(), String> {
    connection
        .execute_batch(sql)
        .map_err(|_| json_error("区域迁移未提交，原记录保留"))
}
fn ids(value: &Value, key: &str) -> BTreeSet<String> {
    value[key]
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(|r| r["id"].as_str().map(str::to_owned))
        .collect()
}
fn split_exchange(original: &Value) -> Result<(Value, Value), String> {
    let mut chat = original.clone();
    let mut work = original.clone();
    let conversations: BTreeSet<_> = original["conversations"]
        .as_array()
        .into_iter()
        .flatten()
        .filter(|r| r["surface"] == "WORK" || r["projectId"].as_str().is_some())
        .filter_map(|r| r["id"].as_str().map(str::to_owned))
        .collect();
    let projects = ids(original, "projects");
    let knowledge: BTreeSet<_> = original["knowledge"]
        .as_array()
        .into_iter()
        .flatten()
        .filter(|r| {
            r["projectId"]
                .as_str()
                .is_some_and(|id| projects.contains(id))
        })
        .filter_map(|r| r["id"].as_str().map(str::to_owned))
        .collect();
    for key in [
        "projects",
        "conversations",
        "knowledge",
        "memory",
        "relations",
    ] {
        let rows = original[key]
            .as_array()
            .ok_or_else(|| json_error("旧工作区结构无效"))?;
        let belongs = |r: &Value| match key {
            "projects" => true,
            "conversations" => r["id"]
                .as_str()
                .is_some_and(|id| conversations.contains(id)),
            "knowledge" => r["id"].as_str().is_some_and(|id| knowledge.contains(id)),
            "memory" => match r["scope"].as_str() {
                Some("PROJECT") => r["scopeId"]
                    .as_str()
                    .is_some_and(|id| projects.contains(id)),
                Some("CONVERSATION") => r["scopeId"]
                    .as_str()
                    .is_some_and(|id| conversations.contains(id)),
                _ => false,
            },
            "relations" => ["fromId", "toId"].iter().any(|key| {
                r[*key].as_str().is_some_and(|id| {
                    knowledge.contains(id) || projects.contains(id) || conversations.contains(id)
                })
            }),
            _ => false,
        };
        chat[key] = rows
            .iter()
            .filter(|r| !belongs(r))
            .cloned()
            .collect::<Vec<_>>()
            .into();
        work[key] = rows
            .iter()
            .filter(|r| belongs(r))
            .cloned()
            .collect::<Vec<_>>()
            .into();
    }
    // A previously explicit relation keeps an immutable copy of its global endpoint.
    let endpoints: BTreeSet<String> = work["relations"]
        .as_array()
        .into_iter()
        .flatten()
        .flat_map(|r| {
            ["fromId", "toId"]
                .into_iter()
                .filter_map(|k| r[k].as_str().map(str::to_owned))
        })
        .collect();
    for row in original["knowledge"].as_array().into_iter().flatten() {
        if row["id"]
            .as_str()
            .is_some_and(|id| endpoints.contains(id) && !knowledge.contains(id))
        {
            work["knowledge"].as_array_mut().unwrap().push(row.clone());
        }
    }
    for row in original["memory"].as_array().into_iter().flatten() {
        if row["id"].as_str().is_some_and(|id| endpoints.contains(id))
            && !work["memory"]
                .as_array()
                .unwrap()
                .iter()
                .any(|r| r["id"] == row["id"])
        {
            work["memory"].as_array_mut().unwrap().push(row.clone());
        }
    }
    for exchange in [&mut chat, &mut work] {
        for row in exchange["conversations"].as_array_mut().unwrap() {
            row.as_object_mut().unwrap().remove("surface");
        }
        refresh_exchange_hash(exchange)?;
        validate_exchange(exchange)?;
    }
    Ok((chat, work))
}
fn collect_assets(value: &Value, output: &mut BTreeSet<(String, String)>) {
    match value {
        Value::Object(object) => {
            if let (Some(id), Some(hash)) = (
                object.get("id").and_then(Value::as_str),
                object.get("sha256").and_then(Value::as_str),
            ) {
                if hash.len() == 64 && hash.bytes().all(|b| b.is_ascii_hexdigit()) {
                    output.insert((id.to_owned(), hash.to_owned()));
                }
            }
            for child in object.values() {
                collect_assets(child, output);
            }
        }
        Value::Array(rows) => {
            for row in rows {
                collect_assets(row, output);
            }
        }
        _ => {}
    }
}

pub(crate) fn migrate(store: &DesktopWorkspaceStore) -> Result<(), String> {
    migrate_before_commit(store, || Ok(()))
}
fn migrate_before_commit(
    store: &DesktopWorkspaceStore,
    before_commit: impl FnOnce() -> Result<(), String>,
) -> Result<(), String> {
    plain_directory(&store.root)?;
    if !plain_file_if_present(&store.database)? {
        return Err(json_error("区域迁移源数据库缺失"));
    }
    let mut connection = store.connection()?;
    let version: u32 = connection
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| json_error("区域迁移源版本不可读"))?;
    if !matches!(
        desktop_schema_source::classify(&connection, version),
        Ok(desktop_schema_source::SchemaSource::Common45 { area, .. }
            | desktop_schema_source::SchemaSource::Common46 { area, split_done: true }
            | desktop_schema_source::SchemaSource::Common47 { area, split_done: true }
            | desktop_schema_source::SchemaSource::Common48 { area, split_done: true }
            | desktop_schema_source::SchemaSource::Common49 { area, split_done: true }
            | desktop_schema_source::SchemaSource::Common50 { area, split_done: true }
            | desktop_schema_source::SchemaSource::Common51 { area, split_done: true } | desktop_schema_source::SchemaSource::Common52 { area, split_done: true } | desktop_schema_source::SchemaSource::Common53 { area, split_done: true } | desktop_schema_source::SchemaSource::Common54 { area, split_done: true } | desktop_schema_source::SchemaSource::Common55 { area, split_done: true } | desktop_schema_source::SchemaSource::Common56 { area, split_done: true } | desktop_schema_source::SchemaSource::Common57 { area, split_done: true } | crate::desktop_schema_source::SchemaSource::Common58 { area, split_done: true } | crate::desktop_schema_source::SchemaSource::Common59 { area, split_done: true }) if area == "CHAT"
    ) {
        return Err(json_error("区域迁移源必须是完整的 CHAT Schema45"));
    }
    let done: bool = connection
        .query_row(
            "SELECT split_done FROM desktop_data_area_v1 WHERE id=1",
            [],
            |r| r.get(0),
        )
        .map_err(|_| json_error("区域迁移版本不可读"))?;
    if desktop_data_area::read_area(&connection)? != DesktopDataArea::Chat {
        return Err(json_error("区域迁移源身份无效"));
    }
    if done {
        let recovery = store.root.join("data-area-migration-v1");
        let work_root = store.root.join("work-area");
        plain_directory_if_present(&recovery)?;
        plain_directory_if_present(&work_root)?;
        let backup = recovery.join("before-split.sqlite3");
        let target = work_root.join("workspace.sqlite3");
        let backup_present = plain_file_if_present(&backup)?;
        let target_present = plain_file_if_present(&target)?;
        if backup_present || target_present {
            if !target_present {
                return Err(json_error("已完成的区域迁移缺少 WORK 数据库，未创建空库"));
            }
            let work = Connection::open_with_flags(&target, OpenFlags::SQLITE_OPEN_READ_ONLY)
                .map_err(|_| json_error("已完成的 WORK 数据库不可读"))?;
            if desktop_data_area::read_area(&work)? != DesktopDataArea::Work {
                return Err(json_error("已完成的 WORK 数据库身份无效"));
            }
            let work_done: i64 = work
                .query_row(
                    "SELECT split_done FROM desktop_data_area_v1 WHERE id=1",
                    [],
                    |row| row.get(0),
                )
                .map_err(|_| json_error("已完成的 WORK 数据库状态无效"))?;
            if work_done != 1 {
                return Err(json_error("已完成的 WORK 数据库状态无效"));
            }
        }
        return Ok(());
    }
    let originals: Vec<(String, String)> = {
        let mut s = connection
            .prepare(
                "SELECT workspace_id,exchange_json FROM workspace_exchange ORDER BY workspace_id",
            )
            .map_err(|_| json_error("旧工作区不可读"))?;
        let rows = s
            .query_map([], |r| Ok((r.get(0)?, r.get(1)?)))
            .map_err(|_| json_error("旧工作区不可读"))?
            .collect::<Result<_, _>>()
            .map_err(|_| json_error("旧工作区不可读"))?;
        rows
    };
    let mut exchanges = Vec::new();
    for (id, raw) in &originals {
        let value: Value =
            serde_json::from_str(&raw).map_err(|_| json_error("旧工作区无法解码"))?;
        let (chat, work) = split_exchange(&value)?;
        let unbound_draft: bool = connection
            .query_row(
                "SELECT EXISTS(SELECT 1 FROM desktop_conversation_drafts_v1 WHERE workspace_id=?1 AND conversation_key=?2)",
                params![id, NEW_CONVERSATION_DRAFT_KEY],
                |row| row.get(0),
            )
            .map_err(|_| json_error("旧版未发送草稿范围不可读"))?;
        if unbound_draft
            || [
                "projects",
                "conversations",
                "knowledge",
                "memory",
                "relations",
            ]
            .iter()
            .any(|key| work[*key].as_array().is_some_and(|rows| !rows.is_empty()))
        {
            exchanges.push((id.clone(), chat, work));
        }
    }
    if exchanges.is_empty() {
        run_sql(
            &connection,
            "UPDATE desktop_data_area_v1 SET split_done=1 WHERE id=1",
        )?;
        return Ok(());
    }
    let recovery = store.root.join("data-area-migration-v1");
    fs::create_dir_all(&recovery).map_err(|_| json_error("旧库备份目录不可用"))?;
    plain_directory(&recovery)?;
    let backup = recovery.join("before-split.sqlite3");
    if !plain_file_if_present(&backup)? {
        connection
            .execute("VACUUM INTO ?1", [backup.to_string_lossy().as_ref()])
            .map_err(|_| json_error("旧库备份未完成"))?;
    }
    verify_before_split_snapshot(&backup, &connection)?;
    fs::OpenOptions::new()
        .read(true)
        .write(true)
        .open(&backup)
        .and_then(|file| file.sync_all())
        .map_err(|_| json_error("旧库备份未同步到磁盘"))?;
    let target_root = store.root.join("work-area");
    fs::create_dir_all(&target_root).map_err(|_| json_error("工作区目录不可用"))?;
    plain_directory(&target_root)?;
    let target = target_root.join("workspace.sqlite3");
    let pending = recovery.join("pending");
    let target_present = plain_file_if_present(&target)?;
    let pending_present = plain_file_if_present(&pending)?;
    if target_present && !pending_present {
        return Err(json_error("WORK 数据库已存在且无迁移标记，未覆盖旧数据"));
    }
    if target_present && pending_present {
        verify_before_split_snapshot(&target, &connection)?;
    }
    if pending_present {
        if fs::read(&pending).map_err(|_| json_error("迁移恢复标记不可读"))? != b"1" {
            return Err(json_error("迁移恢复标记内容无效"));
        }
    } else {
        use std::io::Write;
        let mut marker = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&pending)
            .map_err(|_| json_error("迁移恢复标记不可创建"))?;
        marker
            .write_all(b"1")
            .and_then(|_| marker.sync_all())
            .map_err(|_| json_error("迁移恢复标记不可保存"))?;
    }
    for path in [
        target.clone(),
        PathBuf::from(format!("{}-wal", target.display())),
        PathBuf::from(format!("{}-shm", target.display())),
        PathBuf::from(format!("{}-journal", target.display())),
    ] {
        if plain_file_if_present(&path)? {
            fs::remove_file(path).map_err(|_| json_error("旧迁移候选不可清理"))?;
        }
    }
    connection
        .execute("VACUUM INTO ?1", [target.to_string_lossy().as_ref()])
        .map_err(|_| json_error("工作区候选未保存"))?;
    run_sql(
        &connection,
        "PRAGMA wal_checkpoint(TRUNCATE); PRAGMA journal_mode=DELETE; PRAGMA foreign_keys=OFF;",
    )?;
    connection
        .execute(
            "ATTACH DATABASE ?1 AS work",
            [target.to_string_lossy().as_ref()],
        )
        .map_err(|_| json_error("工作区候选未连接"))?;
    run_sql(&connection, "PRAGMA work.journal_mode=DELETE;")?;
    let transaction = connection
        .transaction_with_behavior(rusqlite::TransactionBehavior::Immediate)
        .map_err(|_| json_error("迁移事务不可用"))?;
    run_sql(&transaction,"CREATE TEMP TABLE moving_workspace(id TEXT PRIMARY KEY); CREATE TEMP TABLE moving_conversation(workspace_id TEXT,id TEXT,PRIMARY KEY(workspace_id,id)); CREATE TEMP TABLE moving_message(id TEXT PRIMARY KEY); CREATE TEMP TABLE moving_entity(workspace_id TEXT,kind TEXT,id TEXT,PRIMARY KEY(workspace_id,kind,id)); CREATE TEMP TABLE moving_asset(workspace_id TEXT,id TEXT,sha256 TEXT,PRIMARY KEY(workspace_id,id)); CREATE TEMP TABLE staying_asset(workspace_id TEXT,id TEXT,sha256 TEXT,PRIMARY KEY(workspace_id,id));")?;
    for (id, chat, work) in &exchanges {
        transaction
            .execute("INSERT INTO moving_workspace VALUES(?1)", [id])
            .map_err(|_| json_error("迁移范围不可写"))?;
        for cid in ids(work, "conversations") {
            transaction
                .execute(
                    "INSERT INTO moving_conversation VALUES(?1,?2)",
                    params![id, cid],
                )
                .map_err(|_| json_error("迁移会话不可写"))?;
        }
        for message in work["conversations"]
            .as_array()
            .into_iter()
            .flatten()
            .flat_map(|c| c["messages"].as_array().into_iter().flatten())
        {
            transaction
                .execute(
                    "INSERT OR IGNORE INTO moving_message VALUES(?1)",
                    [message["id"].as_str()],
                )
                .map_err(|_| json_error("迁移消息范围不可写"))?;
        }
        for key in [
            "projects",
            "conversations",
            "knowledge",
            "memory",
            "relations",
        ] {
            for eid in ids(work, key) {
                transaction
                    .execute(
                        "INSERT INTO moving_entity VALUES(?1,?2,?3)",
                        params![id, key, eid],
                    )
                    .map_err(|_| json_error("迁移实体不可写"))?;
            }
        }
        let mut staying = BTreeSet::new();
        collect_assets(chat, &mut staying);
        for (aid, hash) in staying {
            transaction
                .execute(
                    "INSERT OR IGNORE INTO staying_asset VALUES(?1,?2,?3)",
                    params![id, aid, hash],
                )
                .map_err(|_| json_error("保留附件范围不可写"))?;
        }
        let mut assets = BTreeSet::new();
        collect_assets(work, &mut assets);
        for (aid, hash) in assets {
            transaction
                .execute(
                    "INSERT OR IGNORE INTO moving_asset VALUES(?1,?2,?3)",
                    params![id, aid, hash],
                )
                .map_err(|_| json_error("迁移附件不可写"))?;
        }
    }
    let scoped="EXISTS(SELECT 1 FROM moving_conversation c WHERE c.workspace_id=t.workspace_id AND c.id=t.conversation_id)";
    let global = "conversation_id IN (SELECT id FROM moving_conversation)";
    let mut rules: BTreeMap<String, String> = BTreeMap::new();
    for table in [
        "desktop_app_settings",
        "desktop_portable_personalization_v1",
        "desktop_transcription_settings",
        "desktop_history_knowledge_schedule_v1",
    ] {
        rules.insert(table.into(), "id=1".into());
    }
    for table in [
        "desktop_compare_executions",
        "desktop_conversation_favorites",
        "desktop_conversation_read_markers_v1",
        "desktop_conversation_title_attempts",
        "desktop_history_knowledge_candidates_v1",
        "desktop_history_knowledge_checkpoints_v1",
        "desktop_local_search_index",
        "desktop_ordinary_chat_attempts",
        "desktop_reminder_drafts_v1",
        "desktop_reminder_plans_v1",
        "desktop_selected_conversation_sync",
        "desktop_sync_jobs",
        "p6g_conversation_override",
        "p6g_route_metadata",
        "p6k_zip_asset_link_receipts",
        "p6k_zip_import_provenance",
        "p6k_zip_import_receipts",
    ] {
        rules.insert(table.into(), scoped.into());
    }
    for table in [
        "desktop_conversation_drafts_v1",
        "desktop_conversation_draft_attachments_v1",
    ] {
        // The old single-area composer stored one unbound draft with no CHAT/WORK identity.
        // Keep an independent copy in each new area rather than guessing its last pane.
        rules.insert(table.into(),format!("EXISTS(SELECT 1 FROM moving_conversation c WHERE c.workspace_id=t.workspace_id AND c.id=t.conversation_key) OR (t.conversation_key='{NEW_CONVERSATION_DRAFT_KEY}' AND t.workspace_id IN (SELECT id FROM moving_workspace))"));
    }
    for prefix in ["chatgpt", "claude", "nanfeng_knowledge", "p6k_zip"] {
        for suffix in ["import_items", "import_provenance", "import_receipts"] {
            rules
                .entry(format!("{prefix}_{suffix}"))
                .or_insert(global.into());
        }
    }
    rules.insert("desktop_ordinary_chat_context_sources".into(),format!("attempt_id IN (SELECT attempt_id FROM desktop_ordinary_chat_attempts t WHERE {scoped})"));
    rules.insert(
        "desktop_ordinary_chat_diagnostics".into(),
        rules["desktop_ordinary_chat_context_sources"].clone(),
    );
    rules.insert(
        "desktop_reminder_runs_v1".into(),
        format!("plan_id IN (SELECT plan_id FROM desktop_reminder_plans_v1 t WHERE {scoped})"),
    );
    // Retain just the selected importer items/messages; task receipts are dependencies, not a reason to copy other messages.
    for prefix in ["chatgpt", "claude", "nanfeng_knowledge", "p6k_zip"] {
        rules.insert(format!("{prefix}_import_messages"),format!("EXISTS(SELECT 1 FROM {prefix}_import_items i WHERE i.task_id=t.task_id AND i.id=t.item_id AND i.{global})"));
        rules.insert(
            format!("{prefix}_import_tasks"),
            format!("id IN (SELECT task_id FROM {prefix}_import_items WHERE {global})"),
        );
    }
    run_sql(&transaction,&format!("INSERT OR IGNORE INTO moving_asset SELECT a.workspace_id,a.attachment_id,a.sha256 FROM desktop_conversation_attachments a JOIN desktop_conversation_draft_attachments_v1 d ON a.workspace_id=d.workspace_id AND a.attachment_id=d.attachment_id JOIN moving_workspace w ON w.id=d.workspace_id WHERE d.conversation_key='{NEW_CONVERSATION_DRAFT_KEY}' OR EXISTS(SELECT 1 FROM moving_conversation c WHERE c.workspace_id=d.workspace_id AND c.id=d.conversation_key);"))?;
    run_sql(&transaction,"INSERT OR IGNORE INTO staying_asset SELECT a.workspace_id,a.attachment_id,a.sha256 FROM desktop_conversation_attachments a JOIN desktop_conversation_draft_attachments_v1 d ON a.workspace_id=d.workspace_id AND a.attachment_id=d.attachment_id WHERE NOT EXISTS(SELECT 1 FROM moving_conversation c WHERE c.workspace_id=d.workspace_id AND c.id=d.conversation_key);")?;
    run_sql(&transaction,"INSERT OR IGNORE INTO moving_asset SELECT t.workspace_id,t.result_attachment_id,t.result_sha256 FROM desktop_transcription_tasks t WHERE t.result_attachment_id IS NOT NULL AND t.result_sha256 IS NOT NULL AND EXISTS(SELECT 1 FROM moving_asset a WHERE a.workspace_id=t.workspace_id AND a.id=t.source_attachment_id);")?;
    for table in [
        "desktop_conversation_attachments",
        "desktop_audio_preview_positions",
        "desktop_pdf_preview_positions",
        "desktop_video_preview_positions",
    ] {
        rules.insert(table.into(),"EXISTS(SELECT 1 FROM moving_asset a WHERE a.workspace_id=t.workspace_id AND a.id=t.attachment_id)".into());
    }
    rules.insert(
        "local_exact_reuse_entries".into(),
        "response_message_id IN (SELECT id FROM moving_message)".into(),
    );
    rules.insert(
        "desktop_attachment_assets".into(),
        "sha256 IN (SELECT sha256 FROM moving_asset)".into(),
    );
    rules.insert("workspace_assets".into(),"EXISTS(SELECT 1 FROM moving_asset a WHERE a.workspace_id=t.workspace_id AND a.sha256=t.sha256)".into());
    rules.insert(
        "exchange_v2_assets".into(),
        rules["workspace_assets"].clone(),
    );
    rules.insert("desktop_transcription_tasks".into(),"EXISTS(SELECT 1 FROM moving_asset a WHERE a.workspace_id=t.workspace_id AND a.id=t.source_attachment_id)".into());
    rules.insert(
        "desktop_transcription_segments".into(),
        format!(
            "task_id IN (SELECT id FROM desktop_transcription_tasks t WHERE {})",
            rules["desktop_transcription_tasks"]
        ),
    );
    for table in ["object_provenance", "exchange_v2_owner_provenance"] {
        let column = if table == "object_provenance" {
            "object_id"
        } else {
            "owner_id"
        };
        rules.insert(table.into(),format!("EXISTS(SELECT 1 FROM moving_entity e WHERE e.workspace_id=t.workspace_id AND e.id=t.{column})"));
    }
    rules.insert("p6k_zip_official_identity_ledger".into(),"EXISTS(SELECT 1 FROM moving_entity e WHERE e.workspace_id=t.workspace_id AND e.id=t.local_object_id)".into());
    rules.insert(
        "workspaces".into(),
        "id IN (SELECT id FROM moving_workspace)".into(),
    );
    rules.insert(
        "workspace_exchange".into(),
        "workspace_id IN (SELECT id FROM moving_workspace)".into(),
    );
    // All selectors materialize before either database is changed.
    let tables: Vec<String> = {
        let mut s=transaction.prepare("SELECT name FROM main.sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").map_err(|_|json_error("迁移结构不可读"))?;
        let rows = s
            .query_map([], |r| r.get(0))
            .map_err(|_| json_error("迁移结构不可读"))?
            .collect::<Result<_, _>>()
            .map_err(|_| json_error("迁移结构不可读"))?;
        rows
    };
    if tables.iter().map(String::as_str).collect::<BTreeSet<_>>()
        != LEGACY_TABLES.split_whitespace().collect::<BTreeSet<_>>()
    {
        return Err(json_error("旧数据库结构与区域迁移版本不一致，原数据未改动"));
    }
    for table in &tables {
        if table == "desktop_data_area_v1" {
            continue;
        }
        if !table
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'_')
        {
            return Err(json_error("迁移表名无效"));
        }
        let predicate = rules.get(table).map(String::as_str).unwrap_or("0");
        run_sql(&transaction,&format!("CREATE TEMP TABLE move_{table} AS SELECT rowid AS id FROM main.{table} t WHERE {predicate};"))?;
    }
    let shared = [
        "workspaces",
        "workspace_exchange",
        // A mixed legacy database had one row for both areas. Preserve the
        // user's choices as independent starting values in CHAT and WORK.
        "desktop_app_settings",
        "desktop_portable_personalization_v1",
        "desktop_transcription_settings",
        "desktop_history_knowledge_schedule_v1",
        "desktop_attachment_assets",
        "desktop_conversation_attachments",
        "workspace_assets",
        "exchange_v2_assets",
        "chatgpt_import_tasks",
        "claude_import_tasks",
        "nanfeng_knowledge_import_tasks",
        "p6k_zip_import_tasks",
    ];
    for table in &tables {
        if table == "desktop_data_area_v1" {
            continue;
        }
        run_sql(
            &transaction,
            &format!("DELETE FROM work.{table} WHERE rowid NOT IN (SELECT id FROM move_{table});"),
        )?;
        if !shared.contains(&table.as_str()) {
            let keep_legacy_unbound_draft = matches!(
                table.as_str(),
                "desktop_conversation_drafts_v1" | "desktop_conversation_draft_attachments_v1"
            );
            let condition = if keep_legacy_unbound_draft {
                format!(" AND conversation_key<>'{NEW_CONVERSATION_DRAFT_KEY}'")
            } else {
                String::new()
            };
            run_sql(
                &transaction,
                &format!("DELETE FROM main.{table} WHERE rowid IN (SELECT id FROM move_{table}){condition};"),
            )?;
        }
    }
    // Background installation belongs to the running process, not to either
    // copied area. WORK needs a valid local row but must not inherit a stale
    // installed/active status from the old single-area runtime.
    run_sql(&transaction,"INSERT INTO work.desktop_background_runtime_v1(id,desired_enabled,installed,safe_code,label,updated_at_ms) VALUES(1,0,0,'NOT_CONFIGURED','',0);")?;
    run_sql(&transaction,"UPDATE work.desktop_history_knowledge_schedule_v1 SET last_dispatched_at_ms=NULL WHERE id=1;")?;
    run_sql(&transaction,"DELETE FROM main.desktop_conversation_attachments AS t WHERE EXISTS(SELECT 1 FROM moving_asset a WHERE a.workspace_id=t.workspace_id AND a.id=t.attachment_id) AND NOT EXISTS(SELECT 1 FROM staying_asset a WHERE a.workspace_id=t.workspace_id AND a.id=t.attachment_id);")?;
    // Old full-workspace undo/import snapshots cannot resurrect the other area's records.
    // Their exact originals remain in before-split.sqlite3 rather than in an active restore path.
    run_sql(&transaction,"DELETE FROM main.domain_intents WHERE workspace_id IN (SELECT id FROM moving_workspace); DELETE FROM main.exchange_v2_imports WHERE workspace_id IN (SELECT id FROM moving_workspace); DELETE FROM main.desktop_local_search_index_state WHERE workspace_id IN (SELECT id FROM moving_workspace);")?;
    for (id, chat, work) in &exchanges {
        for (schema, value) in [("main", chat), ("work", work)] {
            transaction.execute(&format!("UPDATE {schema}.workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2"),params![value.to_string(),id]).map_err(|_|json_error("迁移正文无法保存"))?;
            transaction
                .execute(
                    &format!("UPDATE {schema}.workspaces SET semantic_hash=?1 WHERE id=?2"),
                    params![value["export"]["semanticHash"].as_str(), id],
                )
                .map_err(|_| json_error("迁移摘要无法保存"))?;
        }
    }
    let hashes: Vec<String> = {
        let mut s = transaction
            .prepare("SELECT DISTINCT sha256 FROM moving_asset")
            .map_err(|_| json_error("迁移附件不可读"))?;
        let rows = s
            .query_map([], |r| r.get(0))
            .map_err(|_| json_error("迁移附件不可读"))?
            .collect::<Result<_, _>>()
            .map_err(|_| json_error("迁移附件不可读"))?;
        rows
    };
    if !hashes.is_empty() {
        plain_directory(&store.root.join("assets"))?;
    }
    let target_assets = ensure_plain_child_directories(&target_root, Path::new("assets"))?;
    for hash in hashes {
        if hash.len() != 64 || !hash.bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err(json_error("旧附件标识无效"));
        }
        let source = store.root.join("assets").join(&hash);
        copy_verified_area_file(&source, &target_assets.join(&hash), &hash, &pending)?;
    }
    for (table, prefix) in [
        ("chatgpt_import_tasks", "chatgpt-import-assets"),
        ("claude_import_tasks", "claude-import-assets"),
        (
            "nanfeng_knowledge_import_tasks",
            "nanfeng-knowledge-import-assets",
        ),
        ("p6k_zip_import_tasks", "p6k-zip-import-assets"),
    ] {
        let mut statement = transaction
            .prepare(&format!(
                "SELECT storage_key,package_hash FROM work.{table}"
            ))
            .map_err(|_| json_error("迁移导入来源不可读"))?;
        let rows = statement
            .query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?)))
            .map_err(|_| json_error("迁移导入来源不可读"))?;
        for row in rows {
            let (key, hash) = row.map_err(|_| json_error("迁移导入来源不可读"))?;
            let relative = Path::new(&key);
            if !key.starts_with(&format!("{prefix}/"))
                || relative
                    .components()
                    .any(|c| !matches!(c, std::path::Component::Normal(_)))
            {
                return Err(json_error("旧导入来源位置无效"));
            }
            let source = store.root.join(relative);
            let target = target_root.join(relative);
            let canonical = source
                .canonicalize()
                .map_err(|_| json_error("旧导入来源缺失或不可读，未提交迁移"))?;
            if !canonical.starts_with(
                store
                    .root
                    .canonicalize()
                    .map_err(|_| json_error("旧目录不可读"))?,
            ) {
                return Err(json_error("旧导入来源越过数据根"));
            }
            ensure_plain_child_directories(
                &target_root,
                relative
                    .parent()
                    .ok_or_else(|| json_error("导入目标无效"))?,
            )?;
            copy_verified_area_file(&canonical, &target, &hash, &pending)?;
        }
    }
    before_commit()?;
    run_sql(&transaction,"UPDATE main.desktop_data_area_v1 SET split_done=1 WHERE id=1; UPDATE work.desktop_data_area_v1 SET area='WORK',split_done=1 WHERE id=1;")?;
    for schema in ["main", "work"] {
        let mut statement = transaction
            .prepare(&format!("PRAGMA {schema}.foreign_key_check"))
            .map_err(|_| json_error("迁移关联不可验证"))?;
        if statement
            .query([])
            .map_err(|_| json_error("迁移关联不可验证"))?
            .next()
            .map_err(|_| json_error("迁移关联不可验证"))?
            .is_some()
        {
            return Err(json_error("迁移关联验证失败，原记录保留"));
        }
    }
    transaction
        .commit()
        .map_err(|_| json_error("区域迁移未提交"))?;
    run_sql(
        &connection,
        "DETACH DATABASE work; PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;",
    )?;
    fs::remove_file(pending).map_err(|_| json_error("迁移已提交，恢复标记待清理"))?;
    Ok(())
}

#[cfg(test)]
mod windows_schema45_tests {
    use super::*;

    #[test]
    fn completed_empty_chat_bootstraps_or_resumes_a_separate_empty_work_area() {
        for resume_state in [
            "fresh",
            "schema44",
            "schema43",
            "schema44-with-business-record",
        ] {
            let directory = tempfile::tempdir().unwrap();
            let root = directory.path().to_path_buf();
            let chat = DesktopWorkspaceStore::open(root.clone()).unwrap();
            let connection = chat.connection().unwrap();
            desktop_data_area::migrate(&connection).unwrap();
            connection.pragma_update(None, "user_version", 45).unwrap();
            drop(connection);
            migrate(&chat).unwrap();
            assert!(!root.join("work-area/workspace.sqlite3").exists());
            if resume_state != "fresh" {
                let work_root = root.join("work-area");
                let unfinished = DesktopWorkspaceStore::open_for_startup(
                    work_root.clone(),
                    DesktopStartupMode::UiSchemaDiagnostic,
                )
                .unwrap();
                if resume_state == "schema44-with-business-record" {
                    unfinished.ensure_local_workspace().unwrap();
                }
                if resume_state == "schema43" {
                    let older = Connection::open(work_root.join("workspace.sqlite3")).unwrap();
                    older.execute_batch("ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN tone_override; ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN web_search_override;").unwrap();
                    older.pragma_update(None, "user_version", 43).unwrap();
                }
                assert!(DesktopWorkspaceStore::open_committed_schema45_area(
                    root.clone(),
                    DesktopDataArea::Work,
                    DesktopStartupMode::UiSchemaDiagnostic,
                )
                .is_err());
                fs::write(
                    work_root.join(".schema45-empty-bootstrap-pending"),
                    b"schema45-empty-work-v1",
                )
                .unwrap();
                if resume_state == "schema44-with-business-record" {
                    assert!(DesktopWorkspaceStore::open_committed_schema45_area(
                        root,
                        DesktopDataArea::Work,
                        DesktopStartupMode::UiSchemaDiagnostic,
                    )
                    .err()
                    .unwrap()
                    .contains("workspaces"));
                    assert_eq!(unfinished.list_workspaces().unwrap().len(), 1);
                    continue;
                }
            }
            let work = DesktopWorkspaceStore::open_committed_schema45_area(
                root.clone(),
                DesktopDataArea::Work,
                DesktopStartupMode::UiSchemaDiagnostic,
            )
            .unwrap();
            let work_db = work.connection().unwrap();
            assert_eq!(
                desktop_data_area::read_area(&work_db).unwrap(),
                DesktopDataArea::Work
            );
            let version: u32 = work_db
                .pragma_query_value(None, "user_version", |row| row.get(0))
                .unwrap();
            assert_eq!(version, 45);
            assert!(work.list_workspaces().unwrap().is_empty());
            let work_workspace = work.ensure_local_workspace().unwrap();
            assert_eq!(work.list_workspaces().unwrap().len(), 1);
            let chat_owner = DesktopWorkspaceStore::open_committed_schema45_area(
                root.clone(),
                DesktopDataArea::Chat,
                DesktopStartupMode::UiSchemaDiagnostic,
            )
            .unwrap();
            assert!(chat_owner.list_workspaces().unwrap().is_empty());
            assert!(!root
                .join("work-area/.schema45-empty-bootstrap-pending")
                .exists());
            let reopened = DesktopWorkspaceStore::open_committed_schema45_area(
                root,
                DesktopDataArea::Work,
                DesktopStartupMode::UiSchemaDiagnostic,
            )
            .unwrap();
            assert_eq!(reopened.list_workspaces().unwrap().len(), 1);
            assert_eq!(
                reopened.ensure_local_workspace().unwrap().summary.id,
                work_workspace.summary.id
            );
        }
    }

    #[test]
    fn physical_split_rejects_windows_schema44_before_creating_a_backup() {
        let directory = tempfile::tempdir().unwrap();
        let store = DesktopWorkspaceStore::open(directory.path().to_path_buf()).unwrap();
        assert!(migrate(&store).unwrap_err().contains("CHAT Schema45"));
        assert!(!directory.path().join("data-area-migration-v1").exists());
    }

    #[test]
    fn unbound_legacy_draft_without_work_conversation_survives_in_both_areas() {
        let directory = tempfile::tempdir().unwrap();
        let store = DesktopWorkspaceStore::open(directory.path().to_path_buf()).unwrap();
        store.ensure_local_workspace().unwrap();
        let connection = store.connection().unwrap();
        let workspace_id: String = connection
            .query_row("SELECT id FROM workspaces LIMIT 1", [], |row| row.get(0))
            .unwrap();
        let attachment = b"legacy unbound draft attachment";
        let hash = sha256(attachment);
        let attachment_id = "attachment-legacy-unbound";
        fs::write(directory.path().join("assets").join(&hash), attachment).unwrap();
        connection.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,123,123,123,1)", params![hash, attachment.len() as i64]).unwrap();
        connection.execute("INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,?2,'text/plain','草稿.txt',?3,?4,'2026-09-23T00:00:00Z')", params![workspace_id, attachment_id, attachment.len() as i64, hash]).unwrap();
        connection.execute("INSERT INTO desktop_conversation_drafts_v1(workspace_id,conversation_key,text,updated_at_ms,model_id,tone_override,web_search_override) VALUES(?1,?2,'未发送正文',123,'deepseek-v4-pro','direct',0)", params![workspace_id, NEW_CONVERSATION_DRAFT_KEY]).unwrap();
        connection.execute("INSERT INTO desktop_conversation_draft_attachments_v1(workspace_id,conversation_key,position,attachment_id) VALUES(?1,?2,0,?3)", params![workspace_id, NEW_CONVERSATION_DRAFT_KEY, attachment_id]).unwrap();
        desktop_data_area::migrate(&connection).unwrap();
        connection.pragma_update(None, "user_version", 45).unwrap();
        drop(connection);

        fs::remove_file(directory.path().join("assets").join(&hash)).unwrap();
        assert!(migrate(&store).unwrap_err().contains("私有文件缺失"));
        let unfinished: i64 = store
            .connection()
            .unwrap()
            .query_row(
                "SELECT split_done FROM desktop_data_area_v1 WHERE id=1",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(unfinished, 0);
        let work_asset = directory.path().join("work-area/assets").join(&hash);
        let staged_asset = PathBuf::from(format!("{}.nfai-split-copying", work_asset.display()));
        let outside = directory.path().join("outside-copy-marker");
        fs::write(&outside, b"preserve unrelated bytes").unwrap();
        fs::hard_link(&outside, &staged_asset).unwrap();
        fs::write(directory.path().join("assets").join(&hash), attachment).unwrap();
        assert!(migrate(&store).unwrap_err().contains("硬链接"));
        assert_eq!(fs::read(&outside).unwrap(), b"preserve unrelated bytes");
        fs::remove_file(&staged_asset).unwrap();
        fs::write(&staged_asset, b"interrupted partial copy").unwrap();
        fs::write(&work_asset, b"unrelated existing content").unwrap();
        assert!(migrate(&store).unwrap_err().contains("未覆盖"));
        assert_eq!(
            fs::read(&work_asset).unwrap(),
            b"unrelated existing content"
        );
        fs::remove_file(&work_asset).unwrap();
        migrate(&store).unwrap();
        assert!(!staged_asset.exists());
        let chat = store.connection().unwrap();
        let work = Connection::open(directory.path().join("work-area/workspace.sqlite3")).unwrap();
        for db in [&chat, &work] {
            let draft: (String, String, String, i64) = db.query_row("SELECT text,model_id,tone_override,web_search_override FROM desktop_conversation_drafts_v1 WHERE workspace_id=?1 AND conversation_key=?2", params![workspace_id, NEW_CONVERSATION_DRAFT_KEY], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?))).unwrap();
            assert_eq!(
                draft,
                (
                    "未发送正文".into(),
                    "deepseek-v4-pro".into(),
                    "direct".into(),
                    0
                )
            );
            let count: i64 = db.query_row("SELECT COUNT(*) FROM desktop_conversation_draft_attachments_v1 WHERE workspace_id=?1 AND conversation_key=?2 AND attachment_id=?3", params![workspace_id, NEW_CONVERSATION_DRAFT_KEY, attachment_id], |row| row.get(0)).unwrap();
            assert_eq!(count, 1);
            let violations: i64 = db
                .query_row("SELECT COUNT(*) FROM pragma_foreign_key_check", [], |row| {
                    row.get(0)
                })
                .unwrap();
            assert_eq!(violations, 0);
        }
        assert_eq!(
            fs::read(directory.path().join("assets").join(&hash)).unwrap(),
            attachment
        );
        assert_eq!(
            fs::read(directory.path().join("work-area/assets").join(&hash)).unwrap(),
            attachment
        );
        work.execute("UPDATE desktop_conversation_drafts_v1 SET text='WORK 修改' WHERE workspace_id=?1 AND conversation_key=?2", params![workspace_id, NEW_CONVERSATION_DRAFT_KEY]).unwrap();
        let chat_text: String = chat.query_row("SELECT text FROM desktop_conversation_drafts_v1 WHERE workspace_id=?1 AND conversation_key=?2", params![workspace_id, NEW_CONVERSATION_DRAFT_KEY], |row| row.get(0)).unwrap();
        assert_eq!(chat_text, "未发送正文");
    }

    #[test]
    fn local_draft_schema_and_project_conversation_split_into_physical_chat_and_work_databases() {
        let directory = tempfile::tempdir().unwrap();
        let store = DesktopWorkspaceStore::open(directory.path().to_path_buf()).unwrap();
        let connection = store.connection().unwrap();
        let mut exchange: Value = serde_json::from_str(include_str!(
            "../../../protocol/fixtures/nfai.exchange.v1.golden.json"
        ))
        .unwrap();
        exchange["conversations"][0]["messages"][1]["blocks"] =
            json!([{ "kind": "TEXT", "ordinal": 0, "text": "工作区合成正文" }]);
        let work_id = exchange["conversations"][0]["id"]
            .as_str()
            .unwrap()
            .to_owned();
        let mut chat = exchange["conversations"][0].clone();
        chat["id"] = "conversation-chat".into();
        chat["projectId"] = Value::Null;
        let mut message = chat["messages"][0].clone();
        message["id"] = "message-chat".into();
        chat["currentLeafId"] = "message-chat".into();
        chat["messages"] = json!([message]);
        exchange["conversations"].as_array_mut().unwrap().push(chat);
        let hash = refresh_exchange_hash(&mut exchange).unwrap();
        validate_exchange(&exchange).unwrap();
        connection
            .execute(
                "INSERT INTO workspaces VALUES('legacy','legacy',?1,?1,'2026-09-23T00:00:00Z')",
                [&hash],
            )
            .unwrap();
        connection
            .execute(
                "INSERT INTO workspace_exchange VALUES('legacy',?1)",
                [exchange.to_string()],
            )
            .unwrap();
        connection.execute("INSERT INTO desktop_conversation_drafts_v1(workspace_id,conversation_key,text,updated_at_ms,model_id,tone_override,web_search_override) VALUES('legacy',?1,'work draft',123,'deepseek-v4-pro','直言不讳',0)", [&work_id]).unwrap();
        connection.execute_batch("UPDATE desktop_app_settings SET nickname='保留的昵称' WHERE id=1;
            UPDATE desktop_portable_personalization_v1 SET interests='保留的兴趣' WHERE id=1;
            UPDATE desktop_transcription_settings SET output_format='txt' WHERE id=1;
            UPDATE desktop_history_knowledge_schedule_v1 SET paused=0,last_dispatched_at_ms=123 WHERE id=1;
            UPDATE desktop_background_runtime_v1 SET desired_enabled=1,installed=1,safe_code='READY',label='old-runtime' WHERE id=1;").unwrap();
        desktop_data_area::migrate(&connection).unwrap();
        connection.pragma_update(None, "user_version", 45).unwrap();
        drop(connection);

        let recovery = directory.path().join("data-area-migration-v1");
        fs::create_dir_all(&recovery).unwrap();
        let sentinel = directory.path().join("unrelated-marker");
        fs::write(&sentinel, b"1").unwrap();
        let pending = recovery.join("pending");
        fs::hard_link(&sentinel, &pending).unwrap();
        assert!(migrate(&store).unwrap_err().contains("硬链接"));
        assert_eq!(fs::read(&sentinel).unwrap(), b"1");
        fs::remove_file(&pending).unwrap();
        let backup = recovery.join("before-split.sqlite3");
        let valid_backup = fs::read(&backup).unwrap();
        fs::write(&backup, b"invalid sqlite backup").unwrap();
        assert!(migrate(&store).unwrap_err().contains("旧库备份"));
        fs::write(&backup, &valid_backup).unwrap();
        let stale = Connection::open(&backup).unwrap();
        stale
            .execute(
                "UPDATE workspace_exchange SET exchange_json='{}' WHERE workspace_id='legacy'",
                [],
            )
            .unwrap();
        drop(stale);
        assert!(migrate(&store)
            .unwrap_err()
            .contains("备份业务数据与当前源库不一致"));
        fs::write(&backup, &valid_backup).unwrap();
        let stale_draft = Connection::open(&backup).unwrap();
        stale_draft.execute("UPDATE desktop_conversation_drafts_v1 SET text='stale draft' WHERE workspace_id='legacy'", []).unwrap();
        drop(stale_draft);
        assert!(migrate(&store)
            .unwrap_err()
            .contains("备份业务数据与当前源库不一致"));
        fs::write(&backup, &valid_backup).unwrap();
        let stale_schema = Connection::open(&backup).unwrap();
        stale_schema
            .execute_batch(
                "CREATE INDEX stale_draft_index ON desktop_conversation_drafts_v1(text);",
            )
            .unwrap();
        drop(stale_schema);
        assert!(migrate(&store)
            .unwrap_err()
            .contains("备份结构与当前源库不一致"));
        fs::write(&backup, &valid_backup).unwrap();
        let target = directory.path().join("work-area/workspace.sqlite3");
        let independent = Connection::open(&target).unwrap();
        independent
            .execute_batch(
                "CREATE TABLE sentinel(value TEXT); INSERT INTO sentinel VALUES('keep');",
            )
            .unwrap();
        drop(independent);
        assert!(migrate(&store).unwrap_err().contains("无迁移标记"));
        let independent = Connection::open(&target).unwrap();
        let value: String = independent
            .query_row("SELECT value FROM sentinel", [], |row| row.get(0))
            .unwrap();
        assert_eq!(value, "keep");
        drop(independent);
        fs::remove_file(&target).unwrap();

        assert!(migrate_before_commit(&store, || Err("injected commit failure".into())).is_err());
        let valid_target = fs::read(&target).unwrap();
        let stale_target = Connection::open(&target).unwrap();
        stale_target
            .execute(
                "UPDATE workspace_exchange SET exchange_json='{}' WHERE workspace_id='legacy'",
                [],
            )
            .unwrap();
        drop(stale_target);
        assert!(migrate(&store)
            .unwrap_err()
            .contains("备份业务数据与当前源库不一致"));
        fs::write(&target, valid_target).unwrap();
        let target_alias = directory.path().join("work-candidate-alias.sqlite3");
        fs::hard_link(&target, &target_alias).unwrap();
        assert!(migrate(&store).unwrap_err().contains("硬链接"));
        fs::remove_file(&target_alias).unwrap();
        let after_failure: String = store
            .connection()
            .unwrap()
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='legacy'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(
            serde_json::from_str::<Value>(&after_failure).unwrap(),
            exchange
        );
        let pending_split: i64 = store
            .connection()
            .unwrap()
            .query_row(
                "SELECT split_done FROM desktop_data_area_v1 WHERE id=1",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(pending_split, 0);
        migrate(&store).unwrap();
        migrate(&store).unwrap();
        let chat_db = store.connection().unwrap();
        let work_db =
            Connection::open(directory.path().join("work-area/workspace.sqlite3")).unwrap();
        for db in [&chat_db, &work_db] {
            let settings = crate::desktop_app_settings_v1::read(db).unwrap();
            assert_eq!(settings.product.nickname, "保留的昵称");
            assert_eq!(settings.product.interests, "保留的兴趣");
            let format: String = db.query_row("SELECT output_format FROM desktop_transcription_settings WHERE id=1", [], |row| row.get(0)).unwrap();
            assert_eq!(format, "txt");
            let paused: i64 = db.query_row("SELECT paused FROM desktop_history_knowledge_schedule_v1 WHERE id=1", [], |row| row.get(0)).unwrap();
            assert_eq!(paused, 0);
        }
        let work_runtime = crate::desktop_background_runtime_v1::read_projection(&work_db).unwrap();
        assert!(!work_runtime.installed);
        assert!(!work_runtime.desired_enabled);
        let work_dispatch: Option<i64> = work_db.query_row("SELECT last_dispatched_at_ms FROM desktop_history_knowledge_schedule_v1 WHERE id=1", [], |row| row.get(0)).unwrap();
        assert_eq!(work_dispatch, None);
        work_db.execute("UPDATE desktop_app_settings SET nickname='WORK 独立昵称' WHERE id=1", []).unwrap();
        assert_eq!(crate::desktop_app_settings_v1::read(&chat_db).unwrap().product.nickname, "保留的昵称");
        assert_eq!(
            desktop_data_area::read_area(&chat_db).unwrap(),
            DesktopDataArea::Chat
        );
        assert_eq!(
            desktop_data_area::read_area(&work_db).unwrap(),
            DesktopDataArea::Work
        );
        let read_exchange = |connection: &Connection| -> Value {
            let raw: String = connection
                .query_row(
                    "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='legacy'",
                    [],
                    |row| row.get(0),
                )
                .unwrap();
            serde_json::from_str(&raw).unwrap()
        };
        assert_eq!(
            ids(&read_exchange(&chat_db), "conversations"),
            BTreeSet::from(["conversation-chat".to_owned()])
        );
        assert_eq!(
            ids(&read_exchange(&work_db), "conversations"),
            BTreeSet::from([work_id.clone()])
        );
        let choices: (String, String, String, i64) = work_db.query_row("SELECT text,model_id,tone_override,web_search_override FROM desktop_conversation_drafts_v1 WHERE conversation_key=?1", [&work_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?))).unwrap();
        assert_eq!(
            choices,
            (
                "work draft".into(),
                "deepseek-v4-pro".into(),
                "直言不讳".into(),
                0
            )
        );
        assert!(directory
            .path()
            .join("data-area-migration-v1/before-split.sqlite3")
            .is_file());
        work_db
            .execute("UPDATE desktop_data_area_v1 SET area='CHAT' WHERE id=1", [])
            .unwrap();
        assert!(migrate(&store).unwrap_err().contains("身份无效"));
        work_db
            .execute("UPDATE desktop_data_area_v1 SET area='WORK' WHERE id=1", [])
            .unwrap();
        drop(work_db);
        drop(chat_db);
        fs::remove_file(directory.path().join("work-area/workspace.sqlite3")).unwrap();
        assert!(migrate(&store).unwrap_err().contains("缺少 WORK 数据库"));
    }

    #[test]
    fn windows_draft_candidate_can_be_split_without_touching_its_source() {
        let directory = tempfile::tempdir().unwrap();
        let source_root = directory.path().join("source");
        let source_store = DesktopWorkspaceStore::open(source_root.clone()).unwrap();
        let connection = source_store.connection().unwrap();
        let mut exchange: Value = serde_json::from_str(include_str!(
            "../../../protocol/fixtures/nfai.exchange.v1.golden.json"
        ))
        .unwrap();
        exchange["conversations"][0]["messages"][1]["blocks"] =
            json!([{ "kind": "TEXT", "ordinal": 0, "text": "工作区合成正文" }]);
        let work_id = exchange["conversations"][0]["id"]
            .as_str()
            .unwrap()
            .to_owned();
        let mut chat = exchange["conversations"][0].clone();
        chat["id"] = "candidate-chat".into();
        chat["projectId"] = Value::Null;
        let mut message = chat["messages"][0].clone();
        message["id"] = "candidate-message-chat".into();
        chat["currentLeafId"] = "candidate-message-chat".into();
        chat["messages"] = json!([message]);
        exchange["conversations"].as_array_mut().unwrap().push(chat);
        let hash = refresh_exchange_hash(&mut exchange).unwrap();
        validate_exchange(&exchange).unwrap();
        connection
            .execute(
                "INSERT INTO workspaces VALUES('legacy','legacy',?1,?1,'2026-09-23T00:00:00Z')",
                [&hash],
            )
            .unwrap();
        connection
            .execute(
                "INSERT INTO workspace_exchange VALUES('legacy',?1)",
                [exchange.to_string()],
            )
            .unwrap();
        connection.execute("INSERT INTO desktop_conversation_drafts_v1(workspace_id,conversation_key,text,updated_at_ms,model_id,tone_override,web_search_override) VALUES('legacy',?1,'persisted draft',123,'deepseek-v4-pro','直言不讳',0)", [&work_id]).unwrap();
        let staged_attachment = b"isolated schema45 bundle attachment";
        let staged_hash = sha256(staged_attachment);
        fs::write(
            source_root.join("assets").join(&staged_hash),
            staged_attachment,
        )
        .unwrap();
        connection.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,123,123,123,1)", params![staged_hash, staged_attachment.len() as i64]).unwrap();
        connection.execute("INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES('legacy','candidate-stage-attachment','text/plain','bundle.txt',?1,?2,'2026-09-23T00:00:00Z')", params![staged_attachment.len() as i64, staged_hash]).unwrap();
        connection.execute("INSERT INTO desktop_conversation_draft_attachments_v1(workspace_id,conversation_key,position,attachment_id) VALUES('legacy',?1,0,'candidate-stage-attachment')", [&work_id]).unwrap();
        let import_source = source_root.join("chatgpt-import-assets/nested/package.bin");
        fs::create_dir_all(import_source.parent().unwrap()).unwrap();
        fs::write(&import_source, b"synthetic import package bytes").unwrap();
        drop(connection);
        let source_db = source_root.join("workspace.sqlite3");
        let stage_root =
            desktop_schema_reconcile::prepare_schema45_split_bundle(&source_db).unwrap();
        let staged_import = stage_root.join("chatgpt-import-assets/nested/package.bin");
        let partial_import =
            PathBuf::from(format!("{}.nfai-stage-copying", staged_import.display()));
        fs::write(&partial_import, b"leftover after complete copy").unwrap();
        assert_eq!(
            desktop_schema_reconcile::prepare_schema45_split_bundle(&source_db).unwrap(),
            stage_root
        );
        assert!(!partial_import.exists());
        fs::remove_file(&staged_import).unwrap();
        fs::write(&partial_import, b"interrupted copy").unwrap();
        assert_eq!(
            desktop_schema_reconcile::prepare_schema45_split_bundle(&source_db).unwrap(),
            stage_root
        );
        assert!(!partial_import.exists());
        let unrelated = stage_root.join("chatgpt-import-assets/nested/unrelated-file");
        fs::write(&unrelated, b"keep unrelated content").unwrap();
        assert!(
            desktop_schema_reconcile::prepare_schema45_split_bundle(&source_db)
                .unwrap_err()
                .contains("额外或缺失文件")
        );
        assert_eq!(fs::read(&unrelated).unwrap(), b"keep unrelated content");
        fs::remove_file(&unrelated).unwrap();
        let stage_store = DesktopWorkspaceStore {
            configuration_database: None,
            database: stage_root.join("workspace.sqlite3"),
            credentials: desktop_model_service_v1::AppPrivateProviderCredentialStore::at(
                &stage_root,
            ),
            p7e_isolated_store: p7e_isolated_workspace_v1::IsolatedWorkspaceStore::open(
                stage_root.join("p7e-isolated-workspace-v1"),
            )
            .unwrap(),
            p8_agent_ledger: p8_agent_ledger_v1::AgentLedgerStore::open(
                stage_root.join("p8-agent-ledger-v1"),
            )
            .unwrap(),
            root: stage_root.clone(),
        };
        migrate(&stage_store).unwrap();
        migrate(&stage_store).unwrap();
        let source_connection = source_store.connection().unwrap();
        let source_version: u32 = source_connection
            .pragma_query_value(None, "user_version", |row| row.get(0))
            .unwrap();
        assert_eq!(source_version, 44);
        let original: String = source_connection
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='legacy'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(serde_json::from_str::<Value>(&original).unwrap(), exchange);
        assert_eq!(
            fs::read(source_root.join("assets").join(&staged_hash)).unwrap(),
            staged_attachment
        );
        assert_eq!(
            fs::read(stage_root.join("assets").join(&staged_hash)).unwrap(),
            staged_attachment
        );
        assert_eq!(
            fs::read(stage_root.join("work-area/assets").join(&staged_hash)).unwrap(),
            staged_attachment
        );
        assert_eq!(
            fs::read(&staged_import).unwrap(),
            fs::read(import_source).unwrap()
        );
        let chat_db = stage_store.connection().unwrap();
        let work_db = Connection::open(stage_root.join("work-area/workspace.sqlite3")).unwrap();
        assert_eq!(
            desktop_data_area::read_area(&chat_db).unwrap(),
            DesktopDataArea::Chat
        );
        assert_eq!(
            desktop_data_area::read_area(&work_db).unwrap(),
            DesktopDataArea::Work
        );
        let read_conversations = |db: &Connection| -> BTreeSet<String> {
            let raw: String = db
                .query_row(
                    "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='legacy'",
                    [],
                    |row| row.get(0),
                )
                .unwrap();
            ids(
                &serde_json::from_str::<Value>(&raw).unwrap(),
                "conversations",
            )
        };
        assert_eq!(
            read_conversations(&chat_db),
            BTreeSet::from(["candidate-chat".to_owned()])
        );
        assert_eq!(
            read_conversations(&work_db),
            BTreeSet::from([work_id.clone()])
        );
        let draft: (String, String, String, i64) = work_db.query_row("SELECT text,model_id,tone_override,web_search_override FROM desktop_conversation_drafts_v1 WHERE conversation_key=?1", [&work_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?))).unwrap();
        assert_eq!(
            draft,
            (
                "persisted draft".into(),
                "deepseek-v4-pro".into(),
                "直言不讳".into(),
                0
            )
        );
        for db in [&chat_db, &work_db] {
            let integrity: String = db
                .query_row("PRAGMA integrity_check", [], |row| row.get(0))
                .unwrap();
            assert_eq!(integrity, "ok");
            let violations: i64 = db
                .query_row("SELECT COUNT(*) FROM pragma_foreign_key_check", [], |row| {
                    row.get(0)
                })
                .unwrap();
            assert_eq!(violations, 0);
        }
        let configuration_path =
            desktop_data_area::configuration_database(&stage_root, &chat_db).unwrap();
        let configuration = Connection::open(configuration_path).unwrap();
        let config_tables: BTreeSet<String> = configuration
            .prepare("SELECT name FROM sqlite_master WHERE type='table'")
            .unwrap()
            .query_map([], |row| row.get(0))
            .unwrap()
            .collect::<Result<_, _>>()
            .unwrap();
        assert_eq!(
            config_tables,
            BTreeSet::from([
                "desktop_provider_settings".to_owned(),
                "p6g_catalog".to_owned(),
                "p6g_global_default".to_owned(),
            ])
        );
        assert!(!config_tables.contains("workspace_exchange"));
        drop(configuration);

        let chat_owner = DesktopWorkspaceStore::open_committed_schema45_area(
            stage_root.clone(),
            DesktopDataArea::Chat,
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .unwrap();
        let work_owner = DesktopWorkspaceStore::open_committed_schema45_area(
            stage_root.clone(),
            DesktopDataArea::Work,
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .unwrap();
        assert_eq!(chat_owner.list_workspaces().unwrap().len(), 1);
        assert_eq!(work_owner.list_workspaces().unwrap().len(), 1);
        let provider = &desktop_model_service_v1::PROVIDERS[0];
        chat_owner
            .save_desktop_model_service_setting_record(
                provider.id,
                true,
                provider.default_preset_id,
                0,
            )
            .unwrap();
        let shared_settings = work_owner
            .read_desktop_model_service_setting_records()
            .unwrap();
        assert!(shared_settings
            .iter()
            .any(|setting| setting.provider_id == provider.id
                && setting.enabled
                && setting.revision == 1));
        work_owner
            .save_desktop_model_service_setting_record(
                provider.id,
                false,
                provider.default_preset_id,
                1,
            )
            .unwrap();
        let shared_back = chat_owner
            .read_desktop_model_service_setting_records()
            .unwrap();
        assert!(shared_back
            .iter()
            .any(|setting| setting.provider_id == provider.id
                && !setting.enabled
                && setting.revision == 2));
        chat_owner
            .p6g_set_global_default(P6gGlobalDefaultArgs {
                expected_revision: 0,
                tier: Some(p6g::ModelTier::Deep),
            })
            .unwrap();
        let global = work_owner
            .p6g_global_default(&work_owner.connection().unwrap())
            .unwrap();
        assert_eq!(global.revision, 1);
        assert_eq!(global.tier, Some(p6g::ModelTier::Deep));
        for db in [&chat_db, &work_db] {
            let count: i64 = db
                .query_row("SELECT COUNT(*) FROM p6g_global_default", [], |row| {
                    row.get(0)
                })
                .unwrap();
            assert_eq!(count, 0);
        }
        source_connection.execute("UPDATE desktop_conversation_drafts_v1 SET text='new source edit' WHERE workspace_id='legacy'", []).unwrap();
        assert!(
            desktop_schema_reconcile::prepare_schema45_split_bundle(&source_db)
                .unwrap_err()
                .contains("源库已变化")
        );
        let staged_draft: String = work_db
            .query_row(
                "SELECT text FROM desktop_conversation_drafts_v1 WHERE workspace_id='legacy'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(staged_draft, "persisted draft");
        work_db
            .execute("UPDATE desktop_data_area_v1 SET area='CHAT' WHERE id=1", [])
            .unwrap();
        assert!(DesktopWorkspaceStore::open_committed_schema45_area(
            stage_root,
            DesktopDataArea::Work,
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .err()
        .unwrap()
        .contains("身份或拆分状态不符"));
    }
}
