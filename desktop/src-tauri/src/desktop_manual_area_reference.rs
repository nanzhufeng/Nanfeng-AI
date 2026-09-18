//! Explicit, text-only snapshots; no enumeration, mutable link or implicit cross-area context.
use super::*;

pub(crate) fn selected_message(
    source: &DesktopWorkspaceStore,
    workspace_id: &str,
    conversation_id: &str,
    message_id: &str,
) -> Result<String, String> {
    let connection = source.connection()?;
    let area = desktop_data_area::read_area(&connection)?;
    let raw: String = connection
        .query_row(
            "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
            [workspace_id],
            |r| r.get(0),
        )
        .map_err(|_| json_error("引用源工作区不可用"))?;
    let exchange: Value = serde_json::from_str(&raw).map_err(|_| json_error("引用源无法读取"))?;
    let conversation = exchange["conversations"]
        .as_array()
        .and_then(|rows| {
            rows.iter().find(|r| {
                r["id"] == conversation_id
                    && r["deleted"] != true
                    && r.get("deletedAt").is_none_or(Value::is_null)
            })
        })
        .ok_or_else(|| json_error("所选会话已不可用"))?;
    let message = conversation["messages"]
        .as_array()
        .and_then(|rows| rows.iter().find(|r| r["id"] == message_id))
        .ok_or_else(|| json_error("所选消息已不可用"))?;
    let text = message["blocks"]
        .as_array()
        .ok_or_else(|| json_error("所选消息没有可引用文字"))?
        .iter()
        .filter(|b| b["kind"] == "TEXT")
        .filter_map(|b| b["text"].as_str())
        .collect::<Vec<_>>()
        .join("\n");
    if text.trim().is_empty() || text.chars().count() > 100_000 {
        return Err(json_error("所选消息没有可引用文字，或超出单次引用上限"));
    }
    let title = conversation["title"].as_str().unwrap_or("对话");
    Ok(format!(
        "引用自{}：{}\n\n{}\n\n来源：{}/{}/{}；版本 SHA-256 {}\n",
        if area == DesktopDataArea::Work {
            "工作区"
        } else {
            "对话区"
        },
        title,
        text.split('\n')
            .map(|line| format!("> {line}"))
            .collect::<Vec<_>>()
            .join("\n"),
        area.wire(),
        conversation_id,
        message_id,
        sha256(text.as_bytes())
    ))
}

pub(crate) fn append_to_new_draft(
    target: &DesktopWorkspaceStore,
    workspace_id: &str,
    reference: &str,
) -> Result<(), String> {
    let mut connection = target.connection()?;
    let transaction = connection
        .transaction_with_behavior(rusqlite::TransactionBehavior::Immediate)
        .map_err(|_| json_error("引用草稿暂不可写"))?;
    let previous: Option<(String, i64)> = transaction.query_row("SELECT text,updated_at_ms FROM desktop_conversation_drafts_v1 WHERE workspace_id=?1 AND conversation_key=?2", params![workspace_id, NEW_CONVERSATION_DRAFT_KEY], |r| Ok((r.get(0)?, r.get(1)?))).optional().map_err(|_| json_error("引用目标草稿无法读取"))?;
    let now = system_now_millis();
    let previous = previous
        .filter(|(_, updated)| now < updated.saturating_add(NEW_CONVERSATION_DRAFT_TTL_MS))
        .map(|(text, _)| text)
        .unwrap_or_default();
    let text = if previous.is_empty() {
        reference.to_owned()
    } else {
        format!("{previous}\n\n{reference}")
    };
    if text.chars().count() > 120_000 {
        return Err(json_error("目标草稿超过文字上限，原草稿未改动"));
    }
    transaction.execute("INSERT INTO desktop_conversation_drafts_v1(workspace_id,conversation_key,text,updated_at_ms) VALUES(?1,?2,?3,?4) ON CONFLICT(workspace_id,conversation_key) DO UPDATE SET text=excluded.text,updated_at_ms=excluded.updated_at_ms", params![workspace_id,NEW_CONVERSATION_DRAFT_KEY,text,now])
        .map_err(|_| json_error("引用草稿未能保存"))?;
    transaction
        .commit()
        .map_err(|_| json_error("引用草稿未提交"))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn explicit_selection_copies_only_one_version_and_preserves_source_and_target_draft() {
        let root = tempfile::tempdir().unwrap();
        let source =
            DesktopWorkspaceStore::open_area(root.path().into(), DesktopDataArea::Chat).unwrap();
        let target =
            DesktopWorkspaceStore::open_area(root.path().into(), DesktopDataArea::Work).unwrap();
        source.ensure_local_area_workspace().unwrap();
        target.ensure_local_area_workspace().unwrap();
        let connection = source.connection().unwrap();
        let exchange = json!({"conversations":[{"id":"source-conversation","title":"source","messages":[
            {"id":"chosen","blocks":[{"kind":"TEXT","text":"selected text"}]},
            {"id":"not-chosen","blocks":[{"kind":"TEXT","text":"unselected text"}]}]}]});
        let raw = exchange.to_string();
        connection
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='native-chat'",
                [&raw],
            )
            .unwrap();
        target
            .save_conversation_draft(DesktopConversationDraftArgs {
                workspace_id: "native-work".into(),
                conversation_id: None,
                text: "existing draft".into(),
                attachment_ids: vec![],
            })
            .unwrap();
        let selected =
            selected_message(&source, "native-chat", "source-conversation", "chosen").unwrap();
        append_to_new_draft(&target, "native-work", &selected).unwrap();
        let draft = target
            .read_conversation_draft("native-work", None)
            .unwrap()
            .unwrap();
        assert!(draft.text.starts_with("existing draft\n\n"));
        assert!(draft.text.contains("> selected text"));
        assert!(draft
            .text
            .contains("CHAT/source-conversation/chosen；版本 SHA-256 "));
        assert!(!draft.text.contains("unselected text"));
        assert!(draft.attachments.is_empty());
        assert!(
            selected_message(&source, "native-chat", "source-conversation", "missing").is_err()
        );
        let mut deleted = exchange.clone();
        deleted["conversations"][0]["deleted"] = true.into();
        connection
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='native-chat'",
                [deleted.to_string()],
            )
            .unwrap();
        assert!(selected_message(&source, "native-chat", "source-conversation", "chosen").is_err());
        connection
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='native-chat'",
                [&raw],
            )
            .unwrap();
        assert_eq!(
            raw,
            connection
                .query_row::<String, _, _>(
                    "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='native-chat'",
                    [],
                    |r| r.get(0)
                )
                .unwrap()
        );
        drop(target);
        let reopened =
            DesktopWorkspaceStore::open_area(root.path().into(), DesktopDataArea::Work).unwrap();
        assert_eq!(
            draft.text,
            reopened
                .read_conversation_draft("native-work", None)
                .unwrap()
                .unwrap()
                .text
        );
    }
}
