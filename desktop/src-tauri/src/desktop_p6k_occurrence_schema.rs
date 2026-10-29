//! Schema 50: durable, content-free ZIP attachment counts for each import task.

use crate::desktop_schema_source::{classify, SchemaSource};
use rusqlite::Connection;

pub(crate) const ATTACHMENT_SUMMARY_TABLE_SQL: &str = "CREATE TABLE p6k_zip_import_attachment_summary (
        task_id TEXT PRIMARY KEY NOT NULL REFERENCES p6k_zip_import_tasks(id) ON DELETE CASCADE,
        referenced_occurrences INTEGER NOT NULL CHECK(referenced_occurrences>=0),
        recoverable_unique_assets INTEGER NOT NULL CHECK(recoverable_unique_assets>=0),
        missing_unique_assets INTEGER NOT NULL CHECK(missing_unique_assets>=0),
        fallback_name_unique_assets INTEGER NOT NULL CHECK(fallback_name_unique_assets>=0),
        restored_unique_assets INTEGER NOT NULL CHECK(restored_unique_assets>=0),
        restored_occurrences INTEGER NOT NULL CHECK(restored_occurrences>=0)
    );";

pub(crate) fn migrate_committed_area(connection: &mut Connection, expected_area: &str) -> Result<(), String> {
    if !matches!(expected_area, "CHAT" | "WORK") { return Err("Schema50 区域参数无效".into()); }
    let version: u32 = connection.pragma_query_value(None,"user_version",|row|row.get(0))
        .map_err(|_|"Schema50 来源版本不可读")?;
    match classify(connection,version).map_err(|_|"Schema50 来源结构不可读")? {
        SchemaSource::Common50 { area, split_done:true } | SchemaSource::Common51 { area, split_done:true } | SchemaSource::Common52 { area, split_done:true } | SchemaSource::Common53 { area, split_done: true } | SchemaSource::Common54 { area, split_done: true } | SchemaSource::Common55 { area, split_done: true } | SchemaSource::Common56 { area, split_done: true } | SchemaSource::Common57 { area, split_done: true } | SchemaSource::Common58 { area, split_done: true } | SchemaSource::Common59 { area, split_done: true } if area == expected_area => return Ok(()),
        SchemaSource::Common49 { area, split_done:true } if area == expected_area => {},
        _ => return Err("Schema50 来源不是已提交的目标区域，原库未改动".into()),
    }
    let transaction = connection.transaction().map_err(|_|"Schema50 前向迁移事务不可用")?;
    transaction.execute_batch(ATTACHMENT_SUMMARY_TABLE_SQL).map_err(|_|"Schema50 ZIP 附件回执表无法创建")?;
    transaction.pragma_update(None,"user_version",50).map_err(|_|"Schema50 版本不可写")?;
    transaction.commit().map_err(|_|"Schema50 前向迁移未提交")?;
    if !matches!(classify(connection,50).map_err(|_|"Schema50 升级后结构不可读")?,
        SchemaSource::Common50 { area, split_done:true } if area == expected_area) {
        return Err("Schema50 升级后区域结构不符".into());
    }
    let integrity: String = connection.query_row("PRAGMA integrity_check",[],|row|row.get(0))
        .map_err(|_|"Schema50 升级后完整性不可验证")?;
    if integrity != "ok" { return Err("Schema50 升级后完整性失败".into()); }
    Ok(())
}
