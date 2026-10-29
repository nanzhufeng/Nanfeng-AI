//! Identify the divergent Desktop database branches before any migration writes.
//! Upstream 43 introduced area identity; Windows 43/44 persisted draft choices.
use rusqlite::{Connection, OptionalExtension};
use std::collections::BTreeMap;

pub(crate) const CURRENT_AREA_SCHEMA_VERSION: u32 = 59;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum SchemaSource {
    Legacy(u32),
    UpstreamArea43 { area: String, split_done: bool },
    WindowsDraft43,
    WindowsDraft44,
    Common45 { area: String, split_done: bool },
    Common46 { area: String, split_done: bool },
    Common47 { area: String, split_done: bool },
    Common48 { area: String, split_done: bool },
    Common49 { area: String, split_done: bool },
    Common50 { area: String, split_done: bool },
    Common51 { area: String, split_done: bool },
    Common52 { area: String, split_done: bool },
    Common53 { area: String, split_done: bool },
    Common54 { area: String, split_done: bool },
    Common55 { area: String, split_done: bool },
    Common56 { area: String, split_done: bool },
    Common57 { area: String, split_done: bool },
    Common58 { area: String, split_done: bool },
    Common59 { area: String, split_done: bool },
    Unknown,
    Future(u32),
}

fn table_exists(connection: &Connection, name: &str) -> rusqlite::Result<bool> {
    connection.query_row(
        "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name=?1)",
        [name],
        |row| row.get(0),
    )
}

pub(crate) fn classify(connection: &Connection, version: u32) -> rusqlite::Result<SchemaSource> {
    if version > CURRENT_AREA_SCHEMA_VERSION {
        return Ok(SchemaSource::Future(version));
    }
    let area_table = table_exists(connection, "desktop_data_area_v1")?;
    if version < 43 {
        return Ok(if area_table {
            SchemaSource::Unknown
        } else {
            SchemaSource::Legacy(version)
        });
    }
    if !table_exists(connection, "desktop_conversation_drafts_v1")? {
        return Ok(SchemaSource::Unknown);
    }
    let columns = connection
        .prepare("SELECT name,type FROM pragma_table_info('desktop_conversation_drafts_v1')")?
        .query_map([], |row| {
            Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?))
        })?
        .collect::<rusqlite::Result<BTreeMap<_, _>>>()?;
    if [
        ("workspace_id", "TEXT"),
        ("conversation_key", "TEXT"),
        ("text", "TEXT"),
        ("updated_at_ms", "INTEGER"),
    ]
    .iter()
    .any(|(name, data_type)| columns.get(*name).map(String::as_str) != Some(*data_type))
    {
        return Ok(SchemaSource::Unknown);
    }
    let model = columns.contains_key("model_id");
    let tone = columns.contains_key("tone_override");
    let search = columns.contains_key("web_search_override");
    let fingerprint = if version >= 45 && table_exists(connection, "domain_intents")? {
        connection
            .query_row(
                "SELECT type FROM pragma_table_info('domain_intents') WHERE name='request_fingerprint'",
                [],
                |row| row.get::<_, String>(0),
            )
            .optional()?
    } else {
        None
    };
    let chatgpt_workspace = if version >= 47 {
        connection.query_row("SELECT type FROM pragma_table_info('chatgpt_import_tasks') WHERE name='workspace_id'", [], |row| row.get::<_, String>(0)).optional()?
    } else { None };
    let chatgpt_receipt_workspace = if version >= 47 {
        connection.query_row("SELECT type FROM pragma_table_info('chatgpt_import_receipts') WHERE name='workspace_id'", [], |row| row.get::<_, String>(0)).optional()?
    } else { None };
    let claude_workspace = if version >= 48 {
        connection.query_row("SELECT type FROM pragma_table_info('claude_import_tasks') WHERE name='workspace_id'", [], |row| row.get::<_, String>(0)).optional()?
    } else { None };
    let claude_receipt_workspace = if version >= 48 {
        connection.query_row("SELECT type FROM pragma_table_info('claude_import_receipts') WHERE name='workspace_id'", [], |row| row.get::<_, String>(0)).optional()?
    } else { None };
    let knowledge_workspace = if version >= 49 {
        connection.query_row("SELECT type FROM pragma_table_info('nanfeng_knowledge_import_tasks') WHERE name='workspace_id'", [], |row| row.get::<_, String>(0)).optional()?
    } else { None };
    let knowledge_receipt_workspace = if version >= 49 {
        connection.query_row("SELECT type FROM pragma_table_info('nanfeng_knowledge_import_receipts') WHERE name='workspace_id'", [], |row| row.get::<_, String>(0)).optional()?
    } else { None };
    let p6k_attachment_summary = if version >= 50 {
        table_exists(connection, "p6k_zip_import_attachment_summary")?
    } else { false };
    let memory_retrieval_column = if version >= 51 {
        connection.query_row("SELECT type FROM pragma_table_info('desktop_app_settings') WHERE name='memory_retrieval_enabled'", [], |row| row.get::<_, String>(0)).optional()?
    } else { None };
    let ocr_attempts_valid = if version >= 52 {
        let expected = [("attempt_id","TEXT"),("task_id","TEXT"),("attempt_number","INTEGER"),("provider_id","TEXT"),("requested_model_id","TEXT"),("source_display_name","TEXT"),("created_at_ms","INTEGER"),("terminal_at_ms","INTEGER"),("state","TEXT"),("safe_error_code","TEXT"),("provider_request_id","TEXT"),("page_count","INTEGER"),("input_tokens","INTEGER"),("output_tokens","INTEGER"),("charge_micros","INTEGER")];
        let mut valid = true;
        for (name,kind) in expected {
            let actual = connection.query_row("SELECT type FROM pragma_table_info('desktop_ocr_attempts_v1') WHERE name=?1",[name],|row|row.get::<_,String>(0)).optional()?;
            valid &= actual.as_deref()==Some(kind);
        }
        valid
    } else { true };
    if (model && columns.get("model_id").map(String::as_str) != Some("TEXT"))
        || (tone && columns.get("tone_override").map(String::as_str) != Some("TEXT"))
        || (search && columns.get("web_search_override").map(String::as_str) != Some("INTEGER"))
    {
        return Ok(SchemaSource::Unknown);
    }
    if area_table {
        if !((version == 43 && !model && !tone && !search)
            || (version == 45 && model && tone && search && fingerprint.is_none())
            || (version == 46 && model && tone && search && fingerprint.as_deref() == Some("TEXT"))
            || (version == 47 && model && tone && search && fingerprint.as_deref() == Some("TEXT") && chatgpt_workspace.as_deref() == Some("TEXT") && chatgpt_receipt_workspace.as_deref() == Some("TEXT"))
            || (version == 48 && model && tone && search && fingerprint.as_deref() == Some("TEXT") && chatgpt_workspace.as_deref() == Some("TEXT") && chatgpt_receipt_workspace.as_deref() == Some("TEXT") && claude_workspace.as_deref() == Some("TEXT") && claude_receipt_workspace.as_deref() == Some("TEXT"))
            || (matches!(version, 49 | 50 | 51 | 52 | 53 | 54 | 55 | 56 | 57 | 58 | 59) && model && tone && search && fingerprint.as_deref() == Some("TEXT") && chatgpt_workspace.as_deref() == Some("TEXT") && chatgpt_receipt_workspace.as_deref() == Some("TEXT") && claude_workspace.as_deref() == Some("TEXT") && claude_receipt_workspace.as_deref() == Some("TEXT") && knowledge_workspace.as_deref() == Some("TEXT") && knowledge_receipt_workspace.as_deref() == Some("TEXT") && (version == 49 || p6k_attachment_summary) && (version < 51 || memory_retrieval_column.as_deref() == Some("INTEGER")) && ocr_attempts_valid && (version < 53 || crate::desktop_markdown_knowledge_v1::schema_valid(connection)?) && (version < 54 || crate::desktop_json_knowledge_tasks_v1::schema_valid(connection)?) && (version < 55 || crate::desktop_pdf_knowledge_tasks_v1::schema_valid(connection)?) && (version < 56 || crate::desktop_web_knowledge_tasks_v1::schema_valid(connection)?) && (version < 57 || crate::desktop_composer_model_v1::schema_valid(connection)?) && (version < 58 || crate::attachment_search_checkpoint_v1::schema_valid(connection)?) && (version < 59 || crate::desktop_history_knowledge_v1::accounting_schema_valid(connection)?)))
        {
            return Ok(SchemaSource::Unknown);
        }
        let identity: Option<(String, i64)> = connection
            .query_row(
                "SELECT area,split_done FROM desktop_data_area_v1 WHERE id=1",
                [],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .optional()?;
        let count: i64 =
            connection.query_row("SELECT COUNT(*) FROM desktop_data_area_v1", [], |row| {
                row.get(0)
            })?;
        return Ok(match identity {
            Some((area, split_done))
                if count == 1
                    && matches!(area.as_str(), "CHAT" | "WORK")
                    && matches!(split_done, 0 | 1)
                    && (area == "CHAT" || split_done == 1) =>
            {
                if version == 43 {
                    SchemaSource::UpstreamArea43 {
                        area,
                        split_done: split_done == 1,
                    }
                } else if version == 45 {
                    SchemaSource::Common45 {
                        area,
                        split_done: split_done == 1,
                    }
                } else if version == 46 {
                    SchemaSource::Common46 {
                        area,
                        split_done: split_done == 1,
                    }
                } else if version == 47 {
                    SchemaSource::Common47 {
                        area,
                        split_done: split_done == 1,
                    }
                } else if version == 48 {
                    SchemaSource::Common48 { area, split_done: split_done == 1 }
                } else if version == 49 {
                    SchemaSource::Common49 { area, split_done: split_done == 1 }
                } else if version == 50 {
                    SchemaSource::Common50 { area, split_done: split_done == 1 }
                } else if version == 51 {
                    SchemaSource::Common51 { area, split_done: split_done == 1 }
                } else if version == 52 {
                    SchemaSource::Common52 { area, split_done: split_done == 1 }
                } else if version == 53 {
                    SchemaSource::Common53 { area, split_done: split_done == 1 }
                } else if version == 54 {
                    SchemaSource::Common54 { area, split_done: split_done == 1 }
                } else if version == 55 {
                    SchemaSource::Common55 { area, split_done: split_done == 1 }
                } else if version==59 {
                    SchemaSource::Common59 {area,split_done:split_done==1}
                } else if version==58 {
                    SchemaSource::Common58 {area,split_done:split_done==1}
                } else if version==57 {
                    SchemaSource::Common57 {area,split_done:split_done==1}
                } else {
                    SchemaSource::Common56 { area, split_done: split_done == 1 }
                }
            }
            _ => SchemaSource::Unknown,
        });
    }
    Ok(match (version, model, tone, search) {
        (43, true, false, false) => SchemaSource::WindowsDraft43,
        (44, true, true, true) => SchemaSource::WindowsDraft44,
        _ => SchemaSource::Unknown,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fixture(version: u32, columns: &str, identity: Option<(&str, i64)>) -> Connection {
        let connection = Connection::open_in_memory().unwrap();
        connection
            .execute_batch("CREATE TABLE desktop_conversation_drafts_v1(workspace_id TEXT,conversation_key TEXT,text TEXT,updated_at_ms INTEGER);")
            .unwrap();
        for column in columns.split_whitespace() {
            let data_type = if column == "web_search_override" {
                "INTEGER"
            } else {
                "TEXT"
            };
            connection
                .execute_batch(&format!(
                    "ALTER TABLE desktop_conversation_drafts_v1 ADD COLUMN {column} {data_type};"
                ))
                .unwrap();
        }
        if let Some((area, split_done)) = identity {
            connection.execute_batch("CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT,split_done INTEGER);").unwrap();
            connection
                .execute(
                    "INSERT INTO desktop_data_area_v1 VALUES(1,?1,?2)",
                    (area, split_done),
                )
                .unwrap();
        }
        connection
            .pragma_update(None, "user_version", version)
            .unwrap();
        connection
    }

    #[test]
    fn known_schema_branches_have_distinct_signatures() {
        let cases = [
            (42, "", None, SchemaSource::Legacy(42)),
            (
                43,
                "",
                Some(("CHAT", 0)),
                SchemaSource::UpstreamArea43 {
                    area: "CHAT".into(),
                    split_done: false,
                },
            ),
            (
                43,
                "",
                Some(("WORK", 1)),
                SchemaSource::UpstreamArea43 {
                    area: "WORK".into(),
                    split_done: true,
                },
            ),
            (43, "model_id", None, SchemaSource::WindowsDraft43),
            (
                44,
                "model_id tone_override web_search_override",
                None,
                SchemaSource::WindowsDraft44,
            ),
            (
                45,
                "model_id tone_override web_search_override",
                Some(("WORK", 1)),
                SchemaSource::Common45 {
                    area: "WORK".into(),
                    split_done: true,
                },
            ),
            (
                46,
                "model_id tone_override web_search_override",
                Some(("WORK", 1)),
                SchemaSource::Common46 {
                    area: "WORK".into(),
                    split_done: true,
                },
            ),
        ];
        for (version, columns, identity, expected) in cases {
            let connection = fixture(version, columns, identity);
            if version == 46 {
                connection.execute_batch("CREATE TABLE domain_intents(request_fingerprint TEXT);").unwrap();
            }
            assert_eq!(classify(&connection, version).unwrap(), expected);
        }
    }

    #[test]
    fn mixed_or_invalid_area_signatures_are_unknown() {
        let cases = [
            (43, "model_id", Some(("CHAT", 0))),
            (43, "", Some(("WORK", 0))),
            (43, "", Some(("INVALID", 1))),
            (43, "", None),
            (44, "tone_override web_search_override", None),
            (42, "", Some(("CHAT", 0))),
        ];
        for (version, columns, identity) in cases {
            let connection = fixture(version, columns, identity);
            assert_eq!(
                classify(&connection, version).unwrap(),
                SchemaSource::Unknown
            );
        }
    }

    #[test]
    fn matching_column_names_with_wrong_types_do_not_identify_a_known_branch() {
        let connection = Connection::open_in_memory().unwrap();
        connection.execute_batch("CREATE TABLE desktop_conversation_drafts_v1(workspace_id TEXT,conversation_key TEXT,text TEXT,updated_at_ms INTEGER,model_id TEXT,tone_override TEXT,web_search_override TEXT); PRAGMA user_version=44;").unwrap();
        assert_eq!(classify(&connection, 44).unwrap(), SchemaSource::Unknown);
    }

    #[test]
    fn schema46_requires_the_versioned_request_fingerprint_column() {
        let connection = fixture(
            46,
            "model_id tone_override web_search_override",
            Some(("CHAT", 1)),
        );
        assert_eq!(classify(&connection, 46).unwrap(), SchemaSource::Unknown);
        connection.execute_batch("CREATE TABLE domain_intents(request_fingerprint INTEGER);").unwrap();
        assert_eq!(classify(&connection, 46).unwrap(), SchemaSource::Unknown);
        connection.execute_batch("DROP TABLE domain_intents; CREATE TABLE domain_intents(request_fingerprint TEXT);").unwrap();
        assert_eq!(classify(&connection, 46).unwrap(), SchemaSource::Common46 {
            area: "CHAT".into(), split_done: true,
        });
        connection.pragma_update(None, "user_version", 45).unwrap();
        assert_eq!(classify(&connection, 45).unwrap(), SchemaSource::Unknown);
    }

    #[test]
    fn future_schema_is_not_interpreted_as_a_known_branch() {
        let future = CURRENT_AREA_SCHEMA_VERSION + 1;
        let connection = fixture(future, "model_id tone_override web_search_override", None);
        assert_eq!(classify(&connection, future).unwrap(), SchemaSource::Future(future));
    }
}
