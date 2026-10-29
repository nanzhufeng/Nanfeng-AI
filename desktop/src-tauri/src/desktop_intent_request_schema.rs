//! Forward-only storage step for request identities on committed CHAT/WORK areas.
//! Existing Schema 45 intent rows have no recoverable original request; their new
//! fingerprint column deliberately remains NULL instead of guessing from snapshots.

use crate::desktop_schema_source::{classify, SchemaSource};
use rusqlite::{Connection, OptionalExtension, Transaction};
use serde_json::Value;

pub(crate) fn request_fingerprint(kind: &str, request: &Value) -> Result<String, String> {
    let canonical = crate::canonical_json(request)?;
    Ok(crate::sha256(format!("nfai-intent-request-v1:{kind}:{canonical}").as_bytes()))
}

pub(crate) fn fingerprint_enabled(connection: &Connection) -> Result<bool, String> {
    let version: u32 = connection
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| "intent 请求指纹 Schema 不可读".to_owned())?;
    match version {
        0..=45 => Ok(false),
        46..=crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION => Ok(true),
        _ => Err("intent 请求指纹 Schema 比当前客户端更新".into()),
    }
}

pub(crate) fn require_replay_match(
    connection: &Connection,
    intent_id: &str,
    expected: &str,
) -> Result<(), String> {
    if !fingerprint_enabled(connection)? {
        return Ok(());
    }
    let stored: Option<Option<String>> = connection
        .query_row(
            "SELECT request_fingerprint FROM domain_intents WHERE intent_id=?1",
            [intent_id],
            |row| row.get(0),
        )
        .optional()
        .map_err(|_| "intent 请求指纹不可读".to_owned())?;
    match stored.flatten() {
        Some(actual) if actual == expected => Ok(()),
        Some(_) => Err("intent 请求内容与首次提交不符，拒绝重放".into()),
        None => Err("旧 intent 无可验证请求指纹，拒绝重放；请使用新 intent ID".into()),
    }
}

pub(crate) fn record(
    transaction: &Transaction<'_>,
    intent_id: &str,
    fingerprint: &str,
) -> Result<(), String> {
    if !fingerprint_enabled(transaction)? {
        return Ok(());
    }
    let updated = transaction
        .execute(
            "UPDATE domain_intents SET request_fingerprint=?1 WHERE intent_id=?2 AND request_fingerprint IS NULL",
            (fingerprint, intent_id),
        )
        .map_err(|_| "intent 请求指纹无法与业务动作同事务保存".to_owned())?;
    if updated != 1 {
        return Err("intent 请求指纹行缺失或已存在，业务动作已回滚".into());
    }
    Ok(())
}

pub(crate) fn retire_workspace(
    transaction: &Transaction<'_>,
    workspace_id: &str,
) -> Result<(), String> {
    if fingerprint_enabled(transaction)? {
        transaction
            .execute(
                "UPDATE domain_intents SET request_fingerprint=NULL WHERE workspace_id=?1",
                [workspace_id],
            )
            .map_err(|_| "清理后 intent 请求指纹未失效".to_owned())?;
    }
    Ok(())
}

pub(crate) fn migrate_committed_area(
    connection: &mut Connection,
    expected_area: &str,
) -> Result<(), String> {
    if !matches!(expected_area, "CHAT" | "WORK") {
        return Err("Schema46 区域参数无效".into());
    }
    let version: u32 = connection
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| "Schema46 来源版本不可读".to_owned())?;
    match classify(connection, version).map_err(|_| "Schema46 来源结构不可读".to_owned())? {
        SchemaSource::Common46 { area, split_done: true } | SchemaSource::Common47 { area, split_done: true } | SchemaSource::Common48 { area, split_done: true } | SchemaSource::Common49 { area, split_done: true } | SchemaSource::Common50 { area, split_done: true } | SchemaSource::Common51 { area, split_done: true } | SchemaSource::Common52 { area, split_done: true } | SchemaSource::Common53 { area, split_done: true } | SchemaSource::Common54 { area, split_done: true } | SchemaSource::Common55 { area, split_done: true } | SchemaSource::Common56 { area, split_done: true } | SchemaSource::Common57 { area, split_done: true } | SchemaSource::Common58 { area, split_done: true } | SchemaSource::Common59 { area, split_done: true } if area == expected_area => return Ok(()),
        SchemaSource::Common45 { area, split_done: true } if area == expected_area => {}
        _ => return Err("Schema46 来源不是已提交的目标区域，原库未改动".into()),
    }
    let transaction = connection
        .transaction()
        .map_err(|_| "Schema46 前向迁移事务不可用".to_owned())?;
    transaction
        .execute_batch("ALTER TABLE domain_intents ADD COLUMN request_fingerprint TEXT;")
        .map_err(|_| "Schema46 请求指纹字段无法添加，原库未改动".to_owned())?;
    transaction
        .pragma_update(None, "user_version", 46)
        .map_err(|_| "Schema46 版本不可写，原库未改动".to_owned())?;
    transaction
        .commit()
        .map_err(|_| "Schema46 前向迁移未提交".to_owned())?;
    if !matches!(
        classify(connection, 46).map_err(|_| "Schema46 升级后结构不可读".to_owned())?,
        SchemaSource::Common46 { area, split_done: true } if area == expected_area
    ) {
        return Err("Schema46 升级后区域结构不符".into());
    }
    let integrity: String = connection
        .query_row("PRAGMA integrity_check", [], |row| row.get(0))
        .map_err(|_| "Schema46 升级后完整性不可验证".to_owned())?;
    if integrity != "ok" {
        return Err("Schema46 升级后完整性失败".into());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    fn schema45_fixture(path: &std::path::Path, area: &str) -> Connection {
        let connection = Connection::open(path).unwrap();
        connection.execute_batch("CREATE TABLE desktop_conversation_drafts_v1(workspace_id TEXT,conversation_key TEXT,text TEXT,updated_at_ms INTEGER,model_id TEXT,tone_override TEXT,web_search_override INTEGER); CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); CREATE TABLE domain_intents(intent_id TEXT PRIMARY KEY,workspace_id TEXT NOT NULL,entity TEXT NOT NULL,action TEXT NOT NULL,object_id TEXT NOT NULL,expected_revision INTEGER,before_exchange_json TEXT NOT NULL,after_exchange_json TEXT NOT NULL,before_semantic_hash TEXT NOT NULL,after_semantic_hash TEXT NOT NULL,undone INTEGER NOT NULL,created_at TEXT NOT NULL,result_revision INTEGER NOT NULL); PRAGMA user_version=45;").unwrap();
        connection.execute("INSERT INTO desktop_data_area_v1 VALUES(1,?1,1)", [area]).unwrap();
        connection.execute("INSERT INTO domain_intents VALUES('old-intent','workspace-old','project','create','project-old',NULL,'{}','{\"projects\":[]}','before','after',0,'2026-09-25',1)", []).unwrap();
        connection
    }

    #[test]
    fn schema45_area_upgrades_without_rewriting_old_intents_and_reopens_idempotently() {
        for area in ["CHAT", "WORK"] {
            let directory = tempdir().unwrap();
            let path = directory.path().join("workspace.sqlite3");
            let mut connection = schema45_fixture(&path, area);
            migrate_committed_area(&mut connection, area).unwrap();
            drop(connection);
            let mut reopened = Connection::open(&path).unwrap();
            migrate_committed_area(&mut reopened, area).unwrap();
            let version: u32 = reopened.pragma_query_value(None, "user_version", |row| row.get(0)).unwrap();
            assert_eq!(version, 46);
            let row: (String, String, Option<String>) = reopened.query_row("SELECT before_exchange_json,after_exchange_json,request_fingerprint FROM domain_intents WHERE intent_id='old-intent'", [], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?))).unwrap();
            assert_eq!(row, ("{}".into(), "{\"projects\":[]}".into(), None));
            assert_eq!(reopened.query_row("PRAGMA integrity_check", [], |row| row.get::<_, String>(0)).unwrap(), "ok");
        }
    }

    #[test]
    fn wrong_area_and_uncommitted_split_are_rejected_without_changes() {
        let directory = tempdir().unwrap();
        let path = directory.path().join("workspace.sqlite3");
        let mut connection = schema45_fixture(&path, "CHAT");
        assert!(migrate_committed_area(&mut connection, "WORK").is_err());
        connection.execute("UPDATE desktop_data_area_v1 SET split_done=0 WHERE id=1", []).unwrap();
        assert!(migrate_committed_area(&mut connection, "CHAT").is_err());
        assert_eq!(connection.pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0)).unwrap(), 45);
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM pragma_table_info('domain_intents') WHERE name='request_fingerprint'", [], |row| row.get::<_, i64>(0)).unwrap(), 0);
    }
}
