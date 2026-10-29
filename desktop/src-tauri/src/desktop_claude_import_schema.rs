//! Schema 48: bind Claude JSON tasks and receipts to their destination workspace.
//! Old rows are retained. Only a unique, observed conversation owner is backfilled;
//! ambiguous or pending-only tasks remain unbound and require a fresh file selection.

use crate::desktop_schema_source::{classify, SchemaSource};
use rusqlite::{params, Connection, OptionalExtension, Transaction};

fn observed_workspace(transaction: &Transaction<'_>, conversation_id: &str) -> Result<Option<String>, String> {
    let owners = transaction
        .prepare("SELECT DISTINCT workspace_id FROM workspace_exchange, json_each(workspace_exchange.exchange_json,'$.conversations') AS conversation WHERE json_extract(conversation.value,'$.id')=?1")
        .map_err(|_| "Schema48 工作区来源查询不可用")?
        .query_map([conversation_id], |row| row.get::<_, String>(0))
        .map_err(|_| "Schema48 工作区来源不可读")?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "Schema48 工作区来源无效")?;
    Ok(if owners.len() == 1 { owners.into_iter().next() } else { None })
}

pub(crate) fn migrate_committed_area(connection: &mut Connection, expected_area: &str) -> Result<(), String> {
    if !matches!(expected_area, "CHAT" | "WORK") { return Err("Schema48 区域参数无效".into()); }
    let version: u32 = connection.pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| "Schema48 来源版本不可读")?;
    match classify(connection, version).map_err(|_| "Schema48 来源结构不可读")? {
        SchemaSource::Common48 { area, split_done: true } | SchemaSource::Common49 { area, split_done: true } | SchemaSource::Common50 { area, split_done: true } | SchemaSource::Common51 { area, split_done: true } | SchemaSource::Common52 { area, split_done: true } | SchemaSource::Common53 { area, split_done: true } | SchemaSource::Common54 { area, split_done: true } | SchemaSource::Common55 { area, split_done: true } | SchemaSource::Common56 { area, split_done: true } | SchemaSource::Common57 { area, split_done: true } | SchemaSource::Common58 { area, split_done: true } | SchemaSource::Common59 { area, split_done: true } if area == expected_area => return Ok(()),
        SchemaSource::Common47 { area, split_done: true } if area == expected_area => {},
        _ => return Err("Schema48 来源不是已提交的目标区域，原库未改动".into()),
    }
    let transaction = connection.transaction().map_err(|_| "Schema48 前向迁移事务不可用")?;
    transaction.execute_batch(
        "ALTER TABLE claude_import_tasks ADD COLUMN workspace_id TEXT;
         ALTER TABLE claude_import_provenance ADD COLUMN workspace_id TEXT;
         CREATE INDEX claude_import_tasks_workspace_updated ON claude_import_tasks(workspace_id,updated_at_ms);
         DROP INDEX claude_import_provenance_source_package;
         CREATE UNIQUE INDEX claude_import_provenance_workspace_source_package ON claude_import_provenance(workspace_id,source_conversation_id,package_hash);
         CREATE TABLE claude_import_receipts_v48(
           workspace_id TEXT,
           source_conversation_id TEXT NOT NULL,
           package_hash TEXT NOT NULL,
           conversation_id TEXT NOT NULL,
           content_hash TEXT NOT NULL,
           committed_at_ms INTEGER NOT NULL,
           PRIMARY KEY(workspace_id,source_conversation_id,package_hash)
         );"
    ).map_err(|_| "Schema48 Claude 字段或 receipt 候选无法创建")?;
    let legacy_receipts = transaction.prepare("SELECT source_conversation_id,package_hash,conversation_id,content_hash,committed_at_ms FROM claude_import_receipts")
        .map_err(|_| "Schema48 旧 receipt 不可读")?
        .query_map([], |row| Ok((row.get::<_, String>(0)?,row.get::<_, String>(1)?,row.get::<_, String>(2)?,row.get::<_, String>(3)?,row.get::<_, i64>(4)?)))
        .map_err(|_| "Schema48 旧 receipt 无效")?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "Schema48 旧 receipt 无法枚举")?;
    for (source_id, package_hash, conversation_id, content_hash, committed_at_ms) in legacy_receipts {
        let owner = observed_workspace(&transaction, &conversation_id)?;
        transaction.execute("INSERT INTO claude_import_receipts_v48(workspace_id,source_conversation_id,package_hash,conversation_id,content_hash,committed_at_ms) VALUES(?1,?2,?3,?4,?5,?6)",
            params![owner,source_id,package_hash,conversation_id,content_hash,committed_at_ms])
            .map_err(|_| "Schema48 旧 receipt 无法保留")?;
    }
    let provenance_ids = transaction.prepare("SELECT conversation_id FROM claude_import_provenance")
        .map_err(|_| "Schema48 provenance 不可读")?
        .query_map([], |row| row.get::<_, String>(0))
        .map_err(|_| "Schema48 provenance 无效")?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "Schema48 provenance 无法枚举")?;
    for conversation_id in provenance_ids {
        let owner = observed_workspace(&transaction, &conversation_id)?;
        transaction.execute("UPDATE claude_import_provenance SET workspace_id=?2 WHERE conversation_id=?1",params![conversation_id,owner])
            .map_err(|_| "Schema48 provenance 归属无法回填")?;
    }
    let task_ids = transaction.prepare("SELECT id FROM claude_import_tasks")
        .map_err(|_| "Schema48 任务不可读")?
        .query_map([], |row| row.get::<_, String>(0))
        .map_err(|_| "Schema48 任务无效")?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "Schema48 任务无法枚举")?;
    for task_id in task_ids {
        let confirmed = transaction.prepare("SELECT conversation_id FROM claude_import_items WHERE task_id=?1 AND status='CONFIRMED' AND conversation_id IS NOT NULL")
            .map_err(|_| "Schema48 已确认项不可读")?
            .query_map([&task_id], |row| row.get::<_, String>(0))
            .map_err(|_| "Schema48 已确认项无效")?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| "Schema48 已确认项无法枚举")?;
        let mut owner: Option<String> = None;
        let mut unique = !confirmed.is_empty();
        for conversation_id in confirmed {
            match observed_workspace(&transaction, &conversation_id)? {
                Some(candidate) if owner.as_ref().is_none_or(|current| current == &candidate) => owner = Some(candidate),
                _ => { unique = false; break; }
            }
        }
        if unique {
            transaction.execute("UPDATE claude_import_tasks SET workspace_id=?2 WHERE id=?1",params![task_id,owner])
                .map_err(|_| "Schema48 任务归属无法回填")?;
        }
    }
    transaction.execute_batch("DROP TABLE claude_import_receipts; ALTER TABLE claude_import_receipts_v48 RENAME TO claude_import_receipts; CREATE UNIQUE INDEX claude_import_receipts_workspace_source_package ON claude_import_receipts(workspace_id,source_conversation_id,package_hash);")
        .map_err(|_| "Schema48 receipt 不能原子替换")?;
    transaction.pragma_update(None, "user_version", 48).map_err(|_| "Schema48 版本不可写")?;
    transaction.commit().map_err(|_| "Schema48 前向迁移未提交")?;
    if !matches!(classify(connection, 48).map_err(|_| "Schema48 升级后结构不可读")?,
        SchemaSource::Common48 { area, split_done: true } if area == expected_area) {
        return Err("Schema48 升级后区域结构不符".into());
    }
    let integrity: String = connection.query_row("PRAGMA integrity_check", [], |row| row.get(0))
        .map_err(|_| "Schema48 升级后完整性不可验证")?;
    if integrity != "ok" { return Err("Schema48 升级后完整性失败".into()); }
    Ok(())
}

pub(crate) fn task_workspace(connection: &Connection, task_id: &str) -> Result<Option<String>, String> {
    connection.query_row("SELECT workspace_id FROM claude_import_tasks WHERE id=?1",[task_id],|row| row.get(0))
        .optional().map(|value| value.flatten()).map_err(|_| "Claude 任务归属不可读".into())
}

pub(crate) fn legacy_single_workspace(connection: &Connection, expected: &str) -> Result<(), String> {
    let (count, only): (i64, Option<String>) = connection.query_row(
        "SELECT COUNT(*),MIN(id) FROM workspaces",[],|row|Ok((row.get(0)?,row.get(1)?))
    ).map_err(|_| "旧版 Claude 工作区不可读")?;
    if count != 1 || only.as_deref() != Some(expected) {
        return Err("旧版 Claude 导入仅能在唯一工作区续作；先完成分区升级".into());
    }
    Ok(())
}


#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn schema47_upgrade_preserves_old_owner_and_allows_same_package_in_another_workspace() {
        for area in ["CHAT", "WORK"] {
            let directory = tempdir().unwrap();
            let path = directory.path().join("workspace.sqlite3");
            let mut db = Connection::open(&path).unwrap();
            db.execute_batch("CREATE TABLE desktop_conversation_drafts_v1(workspace_id TEXT,conversation_key TEXT,text TEXT,updated_at_ms INTEGER,model_id TEXT,tone_override TEXT,web_search_override INTEGER);
                CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT,split_done INTEGER);
                CREATE TABLE domain_intents(intent_id TEXT,request_fingerprint TEXT);
                CREATE TABLE workspaces(id TEXT PRIMARY KEY);
                CREATE TABLE workspace_exchange(workspace_id TEXT PRIMARY KEY,exchange_json TEXT NOT NULL);
                CREATE TABLE chatgpt_import_tasks(id TEXT PRIMARY KEY,workspace_id TEXT);
                CREATE TABLE chatgpt_import_receipts(workspace_id TEXT,source_conversation_id TEXT,package_hash TEXT);
                CREATE TABLE claude_import_tasks(id TEXT PRIMARY KEY,status TEXT,updated_at_ms INTEGER);
                CREATE TABLE claude_import_items(task_id TEXT,id TEXT,status TEXT,conversation_id TEXT);
                CREATE TABLE claude_import_provenance(conversation_id TEXT PRIMARY KEY,source_conversation_id TEXT,package_hash TEXT);
                CREATE UNIQUE INDEX claude_import_provenance_source_package ON claude_import_provenance(source_conversation_id,package_hash);
                CREATE TABLE claude_import_receipts(source_conversation_id TEXT,package_hash TEXT,conversation_id TEXT,content_hash TEXT,committed_at_ms INTEGER,PRIMARY KEY(source_conversation_id,package_hash));
                INSERT INTO workspaces VALUES('workspace-a');
                INSERT INTO workspaces VALUES('workspace-b');
                INSERT INTO workspace_exchange VALUES('workspace-a','{\"conversations\":[{\"id\":\"conversation-old\"}]}');
                INSERT INTO workspace_exchange VALUES('workspace-b','{\"conversations\":[]}');
                INSERT INTO claude_import_tasks VALUES('task-old','COMPLETED',1);
                INSERT INTO claude_import_tasks VALUES('task-pending','AWAITING_CONFIRMATION',2);
                INSERT INTO claude_import_items VALUES('task-old','item-old','CONFIRMED','conversation-old');
                INSERT INTO claude_import_provenance VALUES('conversation-old','source-one','hash-one');
                INSERT INTO claude_import_receipts VALUES('source-one','hash-one','conversation-old','content-one',1);
                PRAGMA user_version=47;").unwrap();
            db.execute("INSERT INTO desktop_data_area_v1 VALUES(1,?1,1)", [area]).unwrap();
            migrate_committed_area(&mut db, area).unwrap();
            drop(db);
            let mut db = Connection::open(&path).unwrap();
            migrate_committed_area(&mut db, area).unwrap();
            assert_eq!(db.pragma_query_value(None,"user_version",|row|row.get::<_,u32>(0)).unwrap(),48);
            assert_eq!(task_workspace(&db,"task-old").unwrap().as_deref(),Some("workspace-a"));
            assert_eq!(task_workspace(&db,"task-pending").unwrap(),None);
            let old_owner: String = db.query_row("SELECT workspace_id FROM claude_import_receipts WHERE conversation_id='conversation-old'",[],|row|row.get(0)).unwrap();
            assert_eq!(old_owner,"workspace-a");
            let provenance_owner: String = db.query_row("SELECT workspace_id FROM claude_import_provenance WHERE conversation_id='conversation-old'",[],|row|row.get(0)).unwrap();
            assert_eq!(provenance_owner,"workspace-a");
            db.execute("INSERT INTO claude_import_provenance VALUES('conversation-new','source-one','hash-one','workspace-b')",[]).unwrap();
            db.execute("INSERT INTO claude_import_receipts VALUES('workspace-b','source-one','hash-one','conversation-new','content-one',2)",[]).unwrap();
            assert_eq!(db.query_row("PRAGMA integrity_check",[],|row|row.get::<_,String>(0)).unwrap(),"ok");
        }
    }
}
