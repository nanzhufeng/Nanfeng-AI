//! Schema 51: separate profile personalization from memory-summary retrieval.

use crate::desktop_schema_source::{classify, SchemaSource};
use rusqlite::Connection;

pub(crate) fn migrate_committed_area(connection: &mut Connection, expected_area: &str) -> Result<(), String> {
    if !matches!(expected_area, "CHAT" | "WORK") { return Err("Schema51 区域参数无效".into()); }
    let version: u32 = connection.pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| "Schema51 来源版本不可读")?;
    match classify(connection, version).map_err(|_| "Schema51 来源结构不可读")? {
        SchemaSource::Common51 { area, split_done: true } | SchemaSource::Common52 { area, split_done: true } | SchemaSource::Common53 { area, split_done: true } | SchemaSource::Common54 { area, split_done: true } | SchemaSource::Common55 { area, split_done: true } | SchemaSource::Common56 { area, split_done: true } | SchemaSource::Common57 { area, split_done: true } | SchemaSource::Common58 { area, split_done: true } | SchemaSource::Common59 { area, split_done: true } if area == expected_area => return Ok(()),
        SchemaSource::Common50 { area, split_done: true } if area == expected_area => {},
        _ => return Err("Schema51 来源不是已提交的目标区域，原库未改动".into()),
    }
    let transaction = connection.transaction().map_err(|_| "Schema51 前向迁移事务不可用")?;
    let already_present: bool = transaction.query_row(
        "SELECT EXISTS(SELECT 1 FROM pragma_table_info('desktop_app_settings') WHERE name='memory_retrieval_enabled')",
        [], |row| row.get(0),
    ).map_err(|_| "Schema51 记忆状态结构不可读")?;
    if !already_present {
        transaction.execute_batch("ALTER TABLE desktop_app_settings ADD COLUMN memory_retrieval_enabled INTEGER NOT NULL DEFAULT 1 CHECK(memory_retrieval_enabled IN (0,1));
            UPDATE desktop_app_settings SET memory_retrieval_enabled=memory_enabled;")
            .map_err(|_| "Schema51 记忆状态迁移失败")?;
    }
    transaction.pragma_update(None, "user_version", 51).map_err(|_| "Schema51 版本不可写")?;
    transaction.commit().map_err(|_| "Schema51 前向迁移未提交")?;
    if !matches!(classify(connection, 51).map_err(|_| "Schema51 升级后结构不可读")?,
        SchemaSource::Common51 { area, split_done: true } | SchemaSource::Common52 { area, split_done: true } | SchemaSource::Common53 { area, split_done: true } | SchemaSource::Common54 { area, split_done: true } | SchemaSource::Common55 { area, split_done: true } | SchemaSource::Common56 { area, split_done: true } | SchemaSource::Common57 { area, split_done: true } | SchemaSource::Common58 { area, split_done: true } | SchemaSource::Common59 { area, split_done: true } if area == expected_area) {
        return Err("Schema51 升级后区域结构不符".into());
    }
    let integrity: String = connection.query_row("PRAGMA integrity_check", [], |row| row.get(0))
        .map_err(|_| "Schema51 升级后完整性不可验证")?;
    if integrity != "ok" { return Err("Schema51 升级后完整性失败".into()); }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn schema50_preserves_each_areas_old_memory_choice_and_reopens() {
        for (area, enabled) in [("CHAT", 0), ("WORK", 1)] {
            let mut connection = Connection::open_in_memory().unwrap();
            connection.execute_batch("CREATE TABLE desktop_conversation_drafts_v1(workspace_id TEXT,conversation_key TEXT,text TEXT,updated_at_ms INTEGER,model_id TEXT,tone_override TEXT,web_search_override INTEGER);
                CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL);
                CREATE TABLE domain_intents(request_fingerprint TEXT);
                CREATE TABLE chatgpt_import_tasks(workspace_id TEXT);
                CREATE TABLE chatgpt_import_receipts(workspace_id TEXT);
                CREATE TABLE claude_import_tasks(workspace_id TEXT);
                CREATE TABLE claude_import_receipts(workspace_id TEXT);
                CREATE TABLE nanfeng_knowledge_import_tasks(workspace_id TEXT);
                CREATE TABLE nanfeng_knowledge_import_receipts(workspace_id TEXT);
                CREATE TABLE p6k_zip_import_attachment_summary(id INTEGER);
                CREATE TABLE desktop_app_settings(id INTEGER PRIMARY KEY,memory_enabled INTEGER NOT NULL);
                PRAGMA user_version=50;").unwrap();
            connection.execute("INSERT INTO desktop_data_area_v1 VALUES(1,?1,1)", [area]).unwrap();
            connection.execute("INSERT INTO desktop_app_settings VALUES(1,?1)", [enabled]).unwrap();
            migrate_committed_area(&mut connection, area).unwrap();
            let value: i64 = connection.query_row("SELECT memory_retrieval_enabled FROM desktop_app_settings WHERE id=1", [], |row| row.get(0)).unwrap();
            assert_eq!(value, enabled);
            migrate_committed_area(&mut connection, area).unwrap();
            assert_eq!(connection.pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0)).unwrap(), 51);
        }
    }
}
