//! Schema 52: retain local OCR invocation facts independently of mutable tasks.
use crate::desktop_schema_source::{classify, SchemaSource};
use rusqlite::Connection;

pub(crate) fn create(connection: &Connection) -> Result<(), String> {
    connection.execute_batch("CREATE TABLE desktop_ocr_attempts_v1 (
        attempt_id TEXT PRIMARY KEY NOT NULL,
        task_id TEXT NOT NULL,
        attempt_number INTEGER NOT NULL CHECK(attempt_number>0),
        provider_id TEXT NOT NULL,
        requested_model_id TEXT NOT NULL,
        source_display_name TEXT NOT NULL,
        created_at_ms INTEGER NOT NULL,
        terminal_at_ms INTEGER,
        state TEXT NOT NULL CHECK(state IN ('RUNNING','COMPLETED','FAILED','CANCELLED','UNKNOWN')),
        safe_error_code TEXT,
        provider_request_id TEXT,
        page_count INTEGER,
        input_tokens INTEGER,
        output_tokens INTEGER,
        charge_micros INTEGER,
        UNIQUE(task_id,attempt_number)
    );").map_err(|_| "Schema52 OCR Attempt 结构创建失败".into())
}

pub(crate) fn migrate_committed_area(connection: &mut Connection, expected_area: &str) -> Result<(), String> {
    let version: u32 = connection.pragma_query_value(None,"user_version",|row|row.get(0)).map_err(|_|"Schema52 来源版本不可读")?;
    match classify(connection,version).map_err(|_|"Schema52 来源结构不可读")? {
        SchemaSource::Common52 {area,split_done:true} | SchemaSource::Common53 { area, split_done: true } | SchemaSource::Common54 { area, split_done: true } | SchemaSource::Common55 { area, split_done: true } | SchemaSource::Common56 { area, split_done: true } | SchemaSource::Common57 { area, split_done: true } | SchemaSource::Common58 { area, split_done: true } | SchemaSource::Common59 { area, split_done: true } if area==expected_area => return Ok(()),
        SchemaSource::Common51 {area,split_done:true} if area==expected_area => {},
        _ => return Err("Schema52 来源不是已提交的目标区域，原库未改动".into()),
    }
    let transaction=connection.transaction().map_err(|_|"Schema52 事务不可用")?;
    create(&transaction)?;
    transaction.pragma_update(None,"user_version",52).map_err(|_|"Schema52 版本不可写")?;
    if !matches!(classify(&transaction,52).map_err(|_|"Schema52 目标结构不可读")?,SchemaSource::Common52 {area,split_done:true} | SchemaSource::Common53 { area, split_done: true } | SchemaSource::Common54 { area, split_done: true } | SchemaSource::Common55 { area, split_done: true } | SchemaSource::Common56 { area, split_done: true } | SchemaSource::Common57 { area, split_done: true } | SchemaSource::Common58 { area, split_done: true } | SchemaSource::Common59 { area, split_done: true } if area==expected_area) {
        return Err("Schema52 目标结构不符，迁移已回滚".into());
    }
    transaction.commit().map_err(|_|"Schema52 迁移未提交")?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn previous(path: &std::path::Path, area: &str) -> Connection {
        let connection=Connection::open(path).unwrap();
        connection.execute_batch("CREATE TABLE desktop_conversation_drafts_v1(workspace_id TEXT,conversation_key TEXT,text TEXT,updated_at_ms INTEGER,model_id TEXT,tone_override TEXT,web_search_override INTEGER);
            CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL);
            CREATE TABLE domain_intents(request_fingerprint TEXT);
            CREATE TABLE chatgpt_import_tasks(workspace_id TEXT);CREATE TABLE chatgpt_import_receipts(workspace_id TEXT);
            CREATE TABLE claude_import_tasks(workspace_id TEXT);CREATE TABLE claude_import_receipts(workspace_id TEXT);
            CREATE TABLE nanfeng_knowledge_import_tasks(workspace_id TEXT);CREATE TABLE nanfeng_knowledge_import_receipts(workspace_id TEXT);
            CREATE TABLE p6k_zip_import_attachment_summary(id INTEGER);
            CREATE TABLE desktop_app_settings(memory_retrieval_enabled INTEGER);
            INSERT INTO desktop_conversation_drafts_v1 VALUES('local','','synthetic old draft',100,NULL,NULL,0);
            PRAGMA user_version=51;").unwrap();
        connection.execute("INSERT INTO desktop_data_area_v1 VALUES(1,?1,1)",[area]).unwrap();
        connection
    }
    #[test]
    fn both_schema51_areas_upgrade_preserve_old_data_and_reopen_without_fabricating_attempts() {
        for area in ["CHAT","WORK"] {
            let dir=tempfile::tempdir().unwrap();let path=dir.path().join("workspace.sqlite3");
            let mut connection=previous(&path,area);
            assert!(migrate_committed_area(&mut connection,if area=="CHAT" {"WORK"} else {"CHAT"}).is_err());
            migrate_committed_area(&mut connection,area).unwrap();drop(connection);
            let mut reopened=Connection::open(path).unwrap();migrate_committed_area(&mut reopened,area).unwrap();
            assert_eq!(reopened.query_row("SELECT text FROM desktop_conversation_drafts_v1",[],|row|row.get::<_,String>(0)).unwrap(),"synthetic old draft");
            assert_eq!(reopened.query_row("SELECT COUNT(*) FROM desktop_ocr_attempts_v1",[],|row|row.get::<_,i64>(0)).unwrap(),0);
            assert!(matches!(classify(&reopened,52).unwrap(),SchemaSource::Common52 {area:identity,split_done:true} if identity==area));
        }
    }
    #[test]
    fn unexpected_existing_table_rolls_back_version_and_preserves_original() {
        let dir=tempfile::tempdir().unwrap();let mut connection=previous(&dir.path().join("workspace.sqlite3"),"CHAT");
        connection.execute_batch("CREATE TABLE desktop_ocr_attempts_v1(unrecognized TEXT);INSERT INTO desktop_ocr_attempts_v1 VALUES('preserve');").unwrap();
        assert!(migrate_committed_area(&mut connection,"CHAT").is_err());
        assert_eq!(connection.pragma_query_value(None,"user_version",|row|row.get::<_,u32>(0)).unwrap(),51);
        assert_eq!(connection.query_row("SELECT unrecognized FROM desktop_ocr_attempts_v1",[],|row|row.get::<_,String>(0)).unwrap(),"preserve");
        assert_eq!(classify(&connection,52).unwrap(),SchemaSource::Unknown);
    }
}
