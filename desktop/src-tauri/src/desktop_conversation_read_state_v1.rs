//! Device-local conversation read watermarks matching Android's current owner.
//!
//! This table stores only opaque workspace/conversation/message IDs and millisecond
//! watermarks. It is deliberately excluded from portable exchange and account sync.

use rusqlite::{params, Connection, OptionalExtension, Transaction};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::BTreeSet;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ConversationReadState {
    pub conversation_id: String,
    pub unread: bool,
    pub manual_unread_at_ms: Option<i64>,
    pub last_read_at_ms: i64,
    pub latest_completed_at_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Projection {
    pub workspace_id: String,
    pub conversations: Vec<ConversationReadState>,
}

pub fn migrate(transaction: &Transaction<'_>) -> Result<(), String> {
    transaction
        .execute_batch(
            "CREATE TABLE IF NOT EXISTS desktop_conversation_read_markers_v1 (
                workspace_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                last_read_at_ms INTEGER NOT NULL,
                latest_completed_at_ms INTEGER NOT NULL,
                latest_completed_message_id TEXT,
                manual_unread_at_ms INTEGER,
                PRIMARY KEY(workspace_id,conversation_id)
            );
            CREATE INDEX IF NOT EXISTS desktop_conversation_read_markers_manual
                ON desktop_conversation_read_markers_v1(workspace_id,manual_unread_at_ms);",
        )
        .map_err(|_| "Desktop 会话已读水位迁移失败".to_owned())
}

/// Records one newly completed Assistant message. Replaying the same terminal event is a no-op;
/// a retry or Compare sibling has a distinct message ID and therefore creates a new watermark.
pub fn record_completed_assistant(
    transaction: &Transaction<'_>,
    workspace_id: &str,
    conversation_id: &str,
    assistant_message_id: &str,
    completed_at_ms: i64,
) -> Result<(), String> {
    validate_id(workspace_id)?;
    validate_id(conversation_id)?;
    validate_id(assistant_message_id)?;
    let previous: Option<(i64, Option<String>)> = transaction
        .query_row(
            "SELECT latest_completed_at_ms,latest_completed_message_id
             FROM desktop_conversation_read_markers_v1
             WHERE workspace_id=?1 AND conversation_id=?2",
            params![workspace_id, conversation_id],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .optional()
        .map_err(|_| "会话已读水位无法读取".to_owned())?;
    if previous
        .as_ref()
        .and_then(|(_, message_id)| message_id.as_deref())
        == Some(assistant_message_id)
    {
        return Ok(());
    }
    let latest = previous
        .map(|(value, _)| value.saturating_add(1).max(completed_at_ms))
        .unwrap_or(completed_at_ms.max(1));
    transaction
        .execute(
            "INSERT INTO desktop_conversation_read_markers_v1(
                workspace_id,conversation_id,last_read_at_ms,latest_completed_at_ms,
                latest_completed_message_id,manual_unread_at_ms
             ) VALUES(?1,?2,0,?3,?4,NULL)
             ON CONFLICT(workspace_id,conversation_id) DO UPDATE SET
                latest_completed_at_ms=excluded.latest_completed_at_ms,
                latest_completed_message_id=excluded.latest_completed_message_id",
            params![workspace_id, conversation_id, latest, assistant_message_id],
        )
        .map_err(|_| "会话生成完成水位无法保存".to_owned())?;
    Ok(())
}

/// Reading the projection may observe the currently visible conversation. This advances only
/// the automatic watermark; Android's manual "未读" survives startup/reload until a true open.
pub fn read_projection(
    connection: &mut Connection,
    workspace_id: &str,
    observed_conversation_id: Option<&str>,
    now_ms: i64,
) -> Result<Projection, String> {
    validate_id(workspace_id)?;
    let conversation_ids = workspace_conversation_ids(connection, workspace_id)?;
    if let Some(conversation_id) = observed_conversation_id {
        validate_id(conversation_id)?;
        if !conversation_ids.contains(conversation_id) {
            return Err("要读取的会话已不存在".to_owned());
        }
    }
    let transaction = connection
        .transaction()
        .map_err(|_| "无法开启会话已读投影".to_owned())?;
    seed_missing(&transaction, workspace_id, &conversation_ids, now_ms)?;
    prune_missing(&transaction, workspace_id, &conversation_ids)?;
    if let Some(conversation_id) = observed_conversation_id {
        transaction
            .execute(
                "UPDATE desktop_conversation_read_markers_v1
                 SET last_read_at_ms=MAX(last_read_at_ms,latest_completed_at_ms,?3)
                 WHERE workspace_id=?1 AND conversation_id=?2",
                params![workspace_id, conversation_id, now_ms],
            )
            .map_err(|_| "当前会话已读水位无法更新".to_owned())?;
    }
    let conversations = project_rows(&transaction, workspace_id, &conversation_ids)?;
    transaction
        .commit()
        .map_err(|_| "会话已读投影无法提交".to_owned())?;
    Ok(Projection {
        workspace_id: workspace_id.to_owned(),
        conversations,
    })
}

/// A true row/search/lifecycle open clears both automatic and manual unread sources.
pub fn mark_opened(
    connection: &mut Connection,
    workspace_id: &str,
    conversation_id: &str,
    now_ms: i64,
) -> Result<Projection, String> {
    validate_id(workspace_id)?;
    validate_id(conversation_id)?;
    let conversation_ids = workspace_conversation_ids(connection, workspace_id)?;
    if !conversation_ids.contains(conversation_id) {
        return Err("要打开的会话已不存在".to_owned());
    }
    let transaction = connection
        .transaction()
        .map_err(|_| "无法开启会话阅读事务".to_owned())?;
    seed_missing(&transaction, workspace_id, &conversation_ids, now_ms)?;
    transaction
        .execute(
            "UPDATE desktop_conversation_read_markers_v1
             SET last_read_at_ms=MAX(last_read_at_ms,latest_completed_at_ms,?3),
                 manual_unread_at_ms=NULL
             WHERE workspace_id=?1 AND conversation_id=?2",
            params![workspace_id, conversation_id, now_ms],
        )
        .map_err(|_| "会话阅读状态无法保存".to_owned())?;
    let conversations = project_rows(&transaction, workspace_id, &conversation_ids)?;
    transaction
        .commit()
        .map_err(|_| "会话阅读状态无法提交".to_owned())?;
    Ok(Projection {
        workspace_id: workspace_id.to_owned(),
        conversations,
    })
}

pub fn mark_manual_unread(
    connection: &mut Connection,
    workspace_id: &str,
    conversation_id: &str,
    marked_at_ms: i64,
) -> Result<Projection, String> {
    validate_id(workspace_id)?;
    validate_id(conversation_id)?;
    let conversation_ids = workspace_conversation_ids(connection, workspace_id)?;
    if !conversation_ids.contains(conversation_id) {
        return Err("要标记的会话已不存在".to_owned());
    }
    let transaction = connection
        .transaction()
        .map_err(|_| "无法开启手动未读事务".to_owned())?;
    seed_missing(&transaction, workspace_id, &conversation_ids, marked_at_ms)?;
    transaction
        .execute(
            "UPDATE desktop_conversation_read_markers_v1 SET manual_unread_at_ms=?3
             WHERE workspace_id=?1 AND conversation_id=?2",
            params![workspace_id, conversation_id, marked_at_ms],
        )
        .map_err(|_| "会话未读标记无法保存".to_owned())?;
    let conversations = project_rows(&transaction, workspace_id, &conversation_ids)?;
    transaction
        .commit()
        .map_err(|_| "会话未读标记无法提交".to_owned())?;
    Ok(Projection {
        workspace_id: workspace_id.to_owned(),
        conversations,
    })
}

pub fn delete_conversation(
    transaction: &Transaction<'_>,
    workspace_id: &str,
    conversation_id: &str,
) -> Result<(), String> {
    transaction
        .execute(
            "DELETE FROM desktop_conversation_read_markers_v1
             WHERE workspace_id=?1 AND conversation_id=?2",
            params![workspace_id, conversation_id],
        )
        .map_err(|_| "会话已读水位无法清理".to_owned())?;
    Ok(())
}

fn workspace_conversation_ids(
    connection: &Connection,
    workspace_id: &str,
) -> Result<BTreeSet<String>, String> {
    let raw: String = connection
        .query_row(
            "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
            [workspace_id],
            |row| row.get(0),
        )
        .map_err(|_| "会话已读工作区不存在".to_owned())?;
    let exchange: Value =
        serde_json::from_str(&raw).map_err(|_| "会话已读工作区无效".to_owned())?;
    exchange
        .get("conversations")
        .and_then(Value::as_array)
        .ok_or_else(|| "会话集合无效".to_owned())?
        .iter()
        .map(|conversation| {
            conversation
                .get("id")
                .and_then(Value::as_str)
                .ok_or_else(|| "会话标识无效".to_owned())
                .and_then(|id| {
                    validate_id(id)?;
                    Ok(id.to_owned())
                })
        })
        .collect()
}

fn seed_missing(
    transaction: &Transaction<'_>,
    workspace_id: &str,
    conversation_ids: &BTreeSet<String>,
    now_ms: i64,
) -> Result<(), String> {
    for conversation_id in conversation_ids {
        transaction
            .execute(
                "INSERT OR IGNORE INTO desktop_conversation_read_markers_v1(
                    workspace_id,conversation_id,last_read_at_ms,latest_completed_at_ms,
                    latest_completed_message_id,manual_unread_at_ms
                 ) VALUES(?1,?2,?3,0,NULL,NULL)",
                params![workspace_id, conversation_id, now_ms],
            )
            .map_err(|_| "会话已读初始水位无法保存".to_owned())?;
    }
    Ok(())
}

fn prune_missing(
    transaction: &Transaction<'_>,
    workspace_id: &str,
    conversation_ids: &BTreeSet<String>,
) -> Result<(), String> {
    let stored = transaction
        .prepare(
            "SELECT conversation_id FROM desktop_conversation_read_markers_v1 WHERE workspace_id=?1",
        )
        .map_err(|_| "会话已读水位无法读取".to_owned())?
        .query_map([workspace_id], |row| row.get::<_, String>(0))
        .map_err(|_| "会话已读水位无法读取".to_owned())?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "会话已读水位无效".to_owned())?;
    for conversation_id in stored
        .into_iter()
        .filter(|conversation_id| !conversation_ids.contains(conversation_id))
    {
        transaction
            .execute(
                "DELETE FROM desktop_conversation_read_markers_v1
                 WHERE workspace_id=?1 AND conversation_id=?2",
                params![workspace_id, conversation_id],
            )
            .map_err(|_| "失效会话已读水位无法清理".to_owned())?;
    }
    Ok(())
}

fn project_rows(
    transaction: &Transaction<'_>,
    workspace_id: &str,
    conversation_ids: &BTreeSet<String>,
) -> Result<Vec<ConversationReadState>, String> {
    let mut rows = transaction
        .prepare(
            "SELECT conversation_id,last_read_at_ms,latest_completed_at_ms,manual_unread_at_ms
             FROM desktop_conversation_read_markers_v1 WHERE workspace_id=?1
             ORDER BY conversation_id",
        )
        .map_err(|_| "会话已读投影无法读取".to_owned())?;
    let projected = rows
        .query_map([workspace_id], |row| {
            let conversation_id: String = row.get(0)?;
            let last_read_at_ms: i64 = row.get(1)?;
            let latest_completed_at_ms: i64 = row.get(2)?;
            let manual_unread_at_ms: Option<i64> = row.get(3)?;
            Ok(ConversationReadState {
                conversation_id,
                unread: latest_completed_at_ms > last_read_at_ms,
                manual_unread_at_ms,
                last_read_at_ms,
                latest_completed_at_ms,
            })
        })
        .map_err(|_| "会话已读投影无法读取".to_owned())?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "会话已读投影无效".to_owned())?;
    Ok(projected
        .into_iter()
        .filter(|row| conversation_ids.contains(&row.conversation_id))
        .collect())
}

fn validate_id(value: &str) -> Result<(), String> {
    if value.is_empty()
        || value.len() > 200
        || !value.chars().all(|character| {
            character.is_ascii_alphanumeric() || matches!(character, '.' | '_' | ':' | '-')
        })
    {
        return Err("会话已读标识无效".to_owned());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn database() -> Connection {
        let mut connection = Connection::open_in_memory().unwrap();
        connection
            .execute_batch(
                "CREATE TABLE workspace_exchange(workspace_id TEXT PRIMARY KEY,exchange_json TEXT NOT NULL);
                 INSERT INTO workspace_exchange VALUES(
                    'workspace-one',
                    '{\"conversations\":[{\"id\":\"conversation-a\"},{\"id\":\"conversation-b\"}]}'
                 );",
            )
            .unwrap();
        let transaction = connection.transaction().unwrap();
        migrate(&transaction).unwrap();
        transaction.commit().unwrap();
        connection
    }

    #[test]
    fn existing_conversations_initialize_read_and_manual_marker_survives_observation() {
        let mut connection = database();
        let initial =
            read_projection(&mut connection, "workspace-one", Some("conversation-a"), 10).unwrap();
        assert!(initial.conversations.iter().all(|row| !row.unread));
        let marked =
            mark_manual_unread(&mut connection, "workspace-one", "conversation-b", 20).unwrap();
        assert_eq!(
            marked
                .conversations
                .iter()
                .find(|row| row.conversation_id == "conversation-b")
                .unwrap()
                .manual_unread_at_ms,
            Some(20)
        );
        let observed =
            read_projection(&mut connection, "workspace-one", Some("conversation-b"), 30).unwrap();
        assert_eq!(
            observed
                .conversations
                .iter()
                .find(|row| row.conversation_id == "conversation-b")
                .unwrap()
                .manual_unread_at_ms,
            Some(20)
        );
        let opened = mark_opened(&mut connection, "workspace-one", "conversation-b", 40).unwrap();
        assert_eq!(
            opened
                .conversations
                .iter()
                .find(|row| row.conversation_id == "conversation-b")
                .unwrap()
                .manual_unread_at_ms,
            None
        );
    }

    #[test]
    fn completion_retry_and_duplicate_events_have_durable_independent_watermarks() {
        let mut connection = database();
        read_projection(&mut connection, "workspace-one", Some("conversation-a"), 10).unwrap();
        {
            let transaction = connection.transaction().unwrap();
            record_completed_assistant(
                &transaction,
                "workspace-one",
                "conversation-b",
                "message-first",
                20,
            )
            .unwrap();
            record_completed_assistant(
                &transaction,
                "workspace-one",
                "conversation-b",
                "message-first",
                99,
            )
            .unwrap();
            transaction.commit().unwrap();
        }
        let unread =
            read_projection(&mut connection, "workspace-one", Some("conversation-a"), 30).unwrap();
        let first = unread
            .conversations
            .iter()
            .find(|row| row.conversation_id == "conversation-b")
            .unwrap();
        assert!(first.unread);
        assert_eq!(first.latest_completed_at_ms, 20);
        mark_opened(&mut connection, "workspace-one", "conversation-b", 40).unwrap();
        {
            let transaction = connection.transaction().unwrap();
            record_completed_assistant(
                &transaction,
                "workspace-one",
                "conversation-b",
                "message-retry",
                41,
            )
            .unwrap();
            transaction.commit().unwrap();
        }
        let retried = read_projection(&mut connection, "workspace-one", None, 50).unwrap();
        assert!(
            retried
                .conversations
                .iter()
                .find(|row| row.conversation_id == "conversation-b")
                .unwrap()
                .unread
        );
    }
}
