//! One-time physical split. Original SQLite snapshot is retained outside both active stores.
//! Both row sets commit through SQLite's rollback super-journal, before runtime owners start.
use super::*;
const LEGACY_TABLES: &str = "chatgpt_import_items chatgpt_import_messages chatgpt_import_provenance chatgpt_import_receipts chatgpt_import_tasks claude_import_items claude_import_messages claude_import_provenance claude_import_receipts claude_import_tasks desktop_app_settings desktop_attachment_assets desktop_attachment_staging desktop_audio_preview_positions desktop_background_runtime_v1 desktop_cloud_account_state desktop_compare_executions desktop_conversation_attachments desktop_conversation_draft_attachments_v1 desktop_conversation_drafts_v1 desktop_conversation_favorites desktop_conversation_read_markers_v1 desktop_conversation_title_attempts desktop_history_knowledge_candidates_v1 desktop_history_knowledge_checkpoints_v1 desktop_history_knowledge_schedule_v1 desktop_local_search_history desktop_local_search_index desktop_local_search_index_state desktop_ordinary_chat_attempts desktop_ordinary_chat_context_sources desktop_ordinary_chat_diagnostics desktop_pdf_preview_positions desktop_portable_personalization_v1 desktop_provider_settings desktop_recovery_rotation desktop_reminder_drafts_v1 desktop_reminder_plans_v1 desktop_reminder_runs_v1 desktop_selected_conversation_sync desktop_sync_diagnostics desktop_sync_jobs desktop_sync_notifications desktop_temporary_attachments desktop_temporary_recovery desktop_transcription_segments desktop_transcription_settings desktop_transcription_tasks desktop_video_preview_positions domain_intents exchange_v2_assets exchange_v2_import_journal exchange_v2_import_receipts exchange_v2_imports exchange_v2_owner_provenance import_journal local_exact_reuse_entries model_metadata nanfeng_knowledge_import_items nanfeng_knowledge_import_messages nanfeng_knowledge_import_provenance nanfeng_knowledge_import_receipts nanfeng_knowledge_import_tasks object_provenance p6g_catalog p6g_conversation_override p6g_global_default p6g_route_metadata p6k_profile_import_provenance p6k_profile_import_receipts p6k_profile_personalization_settings p6k_zip_asset_link_receipts p6k_zip_import_asset_candidates p6k_zip_import_entries p6k_zip_import_items p6k_zip_import_messages p6k_zip_import_profile_candidates p6k_zip_import_provenance p6k_zip_import_receipts p6k_zip_import_tasks p6k_zip_official_identity_ledger sync_account_metadata sync_intents window_layout workspace_assets workspace_exchange workspaces desktop_data_area_v1";

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
    let mut connection = store.connection()?;
    let done: bool = connection
        .query_row(
            "SELECT split_done FROM desktop_data_area_v1 WHERE id=1",
            [],
            |r| r.get(0),
        )
        .map_err(|_| json_error("区域迁移版本不可读"))?;
    if done {
        return Ok(());
    }
    if desktop_data_area::read_area(&connection)? != DesktopDataArea::Chat {
        return Err(json_error("区域迁移源身份无效"));
    }
    let originals: Vec<(String, String)> = {
        let mut s = connection
            .prepare("SELECT workspace_id,exchange_json FROM workspace_exchange")
            .map_err(|_| json_error("旧工作区不可读"))?;
        let rows = s
            .query_map([], |r| Ok((r.get(0)?, r.get(1)?)))
            .map_err(|_| json_error("旧工作区不可读"))?
            .collect::<Result<_, _>>()
            .map_err(|_| json_error("旧工作区不可读"))?;
        rows
    };
    let mut exchanges = Vec::new();
    for (id, raw) in originals {
        let value: Value =
            serde_json::from_str(&raw).map_err(|_| json_error("旧工作区无法解码"))?;
        let (chat, work) = split_exchange(&value)?;
        if [
            "projects",
            "conversations",
            "knowledge",
            "memory",
            "relations",
        ]
        .iter()
        .any(|key| work[*key].as_array().is_some_and(|rows| !rows.is_empty()))
        {
            exchanges.push((id, chat, work));
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
    let backup = recovery.join("before-split.sqlite3");
    if !backup.exists() {
        connection
            .execute("VACUUM INTO ?1", [backup.to_string_lossy().as_ref()])
            .map_err(|_| json_error("旧库备份未完成"))?;
    }
    let target_root = store.root.join("work-area");
    fs::create_dir_all(&target_root).map_err(|_| json_error("工作区目录不可用"))?;
    let target = target_root.join("workspace.sqlite3");
    let pending = recovery.join("pending");
    if target.exists() && !pending.exists() {
        let peer = Connection::open_with_flags(&target, OpenFlags::SQLITE_OPEN_READ_ONLY)
            .map_err(|_| json_error("工作区候选不可读"))?;
        let count: i64 = peer
            .query_row("SELECT COUNT(*) FROM workspace_exchange", [], |r| r.get(0))
            .map_err(|_| json_error("工作区候选结构无效"))?;
        if count > 0 {
            return Err(json_error("工作区已有独立记录，未覆盖旧数据"));
        }
    }
    fs::write(&pending, b"1").map_err(|_| json_error("迁移恢复标记不可写"))?;
    for path in [
        target.clone(),
        PathBuf::from(format!("{}-wal", target.display())),
        PathBuf::from(format!("{}-shm", target.display())),
        PathBuf::from(format!("{}-journal", target.display())),
    ] {
        if path.exists() {
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
        rules.insert(table.into(),"EXISTS(SELECT 1 FROM moving_conversation c WHERE c.workspace_id=t.workspace_id AND c.id=t.conversation_key)".into());
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
    run_sql(&transaction,"INSERT OR IGNORE INTO moving_asset SELECT a.workspace_id,a.attachment_id,a.sha256 FROM desktop_conversation_attachments a JOIN desktop_conversation_draft_attachments_v1 d ON a.workspace_id=d.workspace_id AND a.attachment_id=d.attachment_id JOIN moving_conversation c ON c.workspace_id=d.workspace_id AND c.id=d.conversation_key;")?;
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
            run_sql(
                &transaction,
                &format!("DELETE FROM main.{table} WHERE rowid IN (SELECT id FROM move_{table});"),
            )?;
        }
    }
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
    fs::create_dir_all(target_root.join("assets"))
        .map_err(|_| json_error("工作区附件目录不可用"))?;
    for hash in hashes {
        if hash.len() != 64 || !hash.bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err(json_error("旧附件标识无效"));
        }
        let source = store.root.join("assets").join(&hash);
        if source.is_file() {
            let bytes = fs::read(&source).map_err(|_| json_error("旧附件不可读"))?;
            if sha256(&bytes) != hash {
                return Err(json_error("旧附件校验失败，原记录保留"));
            }
            fs::write(target_root.join("assets").join(hash), bytes)
                .map_err(|_| json_error("工作区附件副本未保存"))?;
        }
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
            if source.is_file() {
                let canonical = source
                    .canonicalize()
                    .map_err(|_| json_error("旧导入来源不可读"))?;
                if !canonical.starts_with(
                    store
                        .root
                        .canonicalize()
                        .map_err(|_| json_error("旧目录不可读"))?,
                ) || sha256_file(&canonical)? != hash
                {
                    return Err(json_error("旧导入来源校验失败"));
                }
                fs::create_dir_all(target.parent().ok_or_else(|| json_error("导入目标无效"))?)
                    .map_err(|_| json_error("导入目标目录不可用"))?;
                fs::copy(canonical, &target).map_err(|_| json_error("导入来源副本未保存"))?;
                fs::File::open(&target)
                    .and_then(|file| file.sync_all())
                    .map_err(|_| json_error("导入来源副本未同步"))?;
                if sha256_file(&target)? != hash {
                    return Err(json_error("导入来源副本校验失败"));
                }
            }
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
mod tests {
    use super::*;
    #[test]
    fn legacy_project_conversations_drafts_and_sync_receipts_split_and_reopen_once() {
        let root = tempfile::tempdir().unwrap();
        let source = DesktopWorkspaceStore::open(root.path().into()).unwrap();
        let connection = source.connection().unwrap();
        let mut value: Value = serde_json::from_str(include_str!(
            "../../../protocol/fixtures/nfai.exchange.v1.golden.json"
        ))
        .unwrap();
        let work_id = value["conversations"][0]["id"].as_str().unwrap().to_owned();
        let mut chat = value["conversations"][0].clone();
        chat["id"] = "conversation-chat".into();
        chat["projectId"] = Value::Null;
        let mut message = chat["messages"][0].clone();
        message["id"] = "message-chat".into();
        chat["currentLeafId"] = "message-chat".into();
        chat["messages"] = json!([message]);
        value["conversations"].as_array_mut().unwrap().push(chat);
        let hash = refresh_exchange_hash(&mut value).unwrap();
        validate_exchange(&value).unwrap();
        connection
            .execute(
                "INSERT INTO workspaces VALUES('legacy','legacy',?1,?1,'2026-09-18T00:00:00Z')",
                [&hash],
            )
            .unwrap();
        connection
            .execute(
                "INSERT INTO workspace_exchange VALUES('legacy',?1)",
                [value.to_string()],
            )
            .unwrap();
        connection
            .execute(
                "INSERT INTO desktop_conversation_drafts_v1 VALUES('legacy',?1,'work draft',?2)",
                params![work_id, system_now_millis()],
            )
            .unwrap();
        connection.execute("INSERT INTO desktop_selected_conversation_sync VALUES('account','legacy',?1,'legacy-document',7,'hash','hash',0)",[&work_id]).unwrap();
        drop(connection);
        assert!(migrate_before_commit(&source, || Err("injected commit failure".into())).is_err());
        let unchanged: String = source
            .connection()
            .unwrap()
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='legacy'",
                [],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(serde_json::from_str::<Value>(&unchanged).unwrap(), value);
        migrate(&source).unwrap();
        let target =
            DesktopWorkspaceStore::open_area(root.path().into(), DesktopDataArea::Work).unwrap();
        let read = |store: &DesktopWorkspaceStore| -> Value {
            let raw: String = store
                .connection()
                .unwrap()
                .query_row(
                    "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='legacy'",
                    [],
                    |r| r.get(0),
                )
                .unwrap();
            serde_json::from_str(&raw).unwrap()
        };
        let chat = read(&source);
        let work = read(&target);
        assert_eq!(
            ids(&chat, "conversations"),
            BTreeSet::from(["conversation-chat".to_owned()])
        );
        assert_eq!(
            ids(&work, "conversations"),
            BTreeSet::from([work_id.clone()])
        );
        assert_eq!(
            target
                .read_conversation_draft("legacy", Some(&work_id))
                .unwrap()
                .unwrap()
                .text,
            "work draft"
        );
        assert_eq!(
            target
                .connection()
                .unwrap()
                .query_row::<String, _, _>(
                    "SELECT document_id FROM desktop_selected_conversation_sync",
                    [],
                    |r| r.get(0)
                )
                .unwrap(),
            "legacy-document"
        );
        assert_eq!(
            source
                .connection()
                .unwrap()
                .query_row::<i64, _, _>(
                    "SELECT COUNT(*) FROM desktop_selected_conversation_sync",
                    [],
                    |r| r.get(0)
                )
                .unwrap(),
            0
        );
        migrate(&source).unwrap();
        assert_eq!(read(&target), work);
        let backup = Connection::open_with_flags(
            root.path()
                .join("data-area-migration-v1/before-split.sqlite3"),
            OpenFlags::SQLITE_OPEN_READ_ONLY,
        )
        .unwrap();
        let raw: String = backup
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='legacy'",
                [],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(serde_json::from_str::<Value>(&raw).unwrap(), value);
    }
}
