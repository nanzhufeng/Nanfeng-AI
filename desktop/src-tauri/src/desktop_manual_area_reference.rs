//! Explicit, text-only snapshots; no enumeration, mutable link or implicit cross-area context.
use super::*;

pub(crate) fn selected_message(
    source: &DesktopWorkspaceStore,
    workspace_id: &str,
    conversation_id: &str,
    message_id: &str,
    expected_conversation_revision: u64,
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
    if conversation["revision"].as_u64() != Some(expected_conversation_revision) {
        return Err(json_error("所选消息来源已变化，请刷新后重新选择"));
    }
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
    fn matching_ids_in_separate_stores_keep_the_selected_source_and_fail_after_target_removal() {
        let root = tempfile::tempdir().unwrap();
        let source = DesktopWorkspaceStore::open(root.path().join("source")).unwrap();
        let target = DesktopWorkspaceStore::open(root.path().join("target")).unwrap();
        let workspace_id = source.ensure_local_workspace().unwrap().summary.id;
        let source_exchange = json!({"conversations":[{"id":"shared-conversation","title":"source title","revision":1,"messages":[{"id":"shared-message","blocks":[{"kind":"TEXT","text":"source-only text"}]}]}]}).to_string();
        source
            .connection()
            .unwrap()
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
                params![source_exchange, workspace_id],
            )
            .unwrap();
        let target_exchange = json!({"conversations":[{"id":"shared-conversation","title":"target title","revision":1,"messages":[{"id":"shared-message","blocks":[{"kind":"TEXT","text":"target-only text"}]}]}]}).to_string();
        let target_connection = target.connection().unwrap();
        target_connection.execute(
            "INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES(?1,'other area','','','2026-09-23T00:00:00Z')",
            [&workspace_id],
        ).unwrap();
        target_connection
            .execute(
                "INSERT INTO workspace_exchange(workspace_id,exchange_json) VALUES(?1,?2)",
                params![workspace_id, target_exchange],
            )
            .unwrap();

        let selected = selected_message(
            &source,
            &workspace_id,
            "shared-conversation",
            "shared-message",
            1,
        )
        .unwrap();
        assert!(selected.contains("source-only text"));
        assert!(!selected.contains("target-only text"));
        append_to_new_draft(&target, &workspace_id, &selected).unwrap();
        let saved = target
            .read_conversation_draft(&workspace_id, None)
            .unwrap()
            .unwrap()
            .text;
        assert_eq!(saved, selected);

        let edited = json!({"conversations":[{"id":"shared-conversation","title":"source title","revision":2,"messages":[{"id":"shared-message","blocks":[{"kind":"TEXT","text":"edited source text"}]}]}]}).to_string();
        source
            .connection()
            .unwrap()
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
                params![edited, workspace_id],
            )
            .unwrap();
        assert!(selected_message(
            &source,
            &workspace_id,
            "shared-conversation",
            "shared-message",
            1,
        )
        .is_err());
        assert_eq!(
            target
                .read_conversation_draft(&workspace_id, None)
                .unwrap()
                .unwrap()
                .text,
            saved
        );

        target_connection
            .execute("DELETE FROM workspaces WHERE id=?1", [&workspace_id])
            .unwrap();
        assert!(append_to_new_draft(&target, &workspace_id, &selected).is_err());
        assert!(target
            .read_conversation_draft(&workspace_id, None)
            .unwrap()
            .is_none());
        assert!(selected_message(
            &source,
            &workspace_id,
            "shared-conversation",
            "shared-message",
            2,
        )
        .unwrap()
        .contains("edited source text"));
    }

    #[test]
    fn concurrent_references_append_without_losing_either_selected_text() {
        let root = tempfile::tempdir().unwrap();
        let target_root = root.path().join("target");
        let target = DesktopWorkspaceStore::open(target_root.clone()).unwrap();
        let workspace_id = target.ensure_local_workspace().unwrap().summary.id;
        let barrier = std::sync::Arc::new(std::sync::Barrier::new(2));
        let mut threads = Vec::new();
        for reference in ["first selected reference", "second selected reference"] {
            let target_root = target_root.clone();
            let workspace_id = workspace_id.clone();
            let barrier = barrier.clone();
            threads.push(std::thread::spawn(move || {
                let store = DesktopWorkspaceStore::open(target_root).unwrap();
                barrier.wait();
                append_to_new_draft(&store, &workspace_id, reference)
            }));
        }
        for thread in threads {
            thread.join().unwrap().unwrap();
        }
        let text = target
            .read_conversation_draft(&workspace_id, None)
            .unwrap()
            .unwrap()
            .text;
        assert_eq!(text.matches("first selected reference").count(), 1);
        assert_eq!(text.matches("second selected reference").count(), 1);
    }

    #[test]
    fn explicit_selection_copies_only_one_version_and_preserves_source_and_target_draft() {
        let root = tempfile::tempdir().unwrap();
        let source = DesktopWorkspaceStore::open(root.path().join("source")).unwrap();
        let target = DesktopWorkspaceStore::open(root.path().join("target")).unwrap();
        let source_workspace_id = source.ensure_local_workspace().unwrap().summary.id;
        let target_workspace_id = target.ensure_local_workspace().unwrap().summary.id;
        let connection = source.connection().unwrap();
        let exchange = json!({"conversations":[{"id":"source-conversation","title":"source","revision":1,"messages":[
            {"id":"chosen","blocks":[{"kind":"TEXT","text":"selected text"},{"kind":"ATTACHMENT","asset":{"id":"source-private-asset","displayName":"source-private.pdf"}}]},
            {"id":"not-chosen","blocks":[{"kind":"TEXT","text":"unselected text"}]}]}]});
        let raw = exchange.to_string();
        connection
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
                params![raw, source_workspace_id],
            )
            .unwrap();
        target.connection().unwrap().execute(
            "INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,'attachment-target-one','application/pdf','target-existing.pdf',12,?2,'2026-09-23T00:00:00Z')",
            params![target_workspace_id, "a".repeat(64)],
        ).unwrap();
        target
            .save_conversation_draft(DesktopConversationDraftArgs {
                workspace_id: target_workspace_id.clone(),
                conversation_id: None,
                text: "existing draft".into(),
                attachment_ids: vec!["attachment-target-one".into()],
                model_id: Some("DEEPSEEK_V4_PRO".into()),
                tone_override: Some("professional".into()),
                web_search_override: Some(false),
            })
            .unwrap();
        let selected = selected_message(
            &source,
            &source_workspace_id,
            "source-conversation",
            "chosen",
            1,
        )
        .unwrap();
        append_to_new_draft(&target, &target_workspace_id, &selected).unwrap();
        let draft = target
            .read_conversation_draft(&target_workspace_id, None)
            .unwrap()
            .unwrap();
        assert!(draft.text.starts_with("existing draft\n\n"));
        assert!(draft.text.contains("> selected text"));
        assert!(draft
            .text
            .contains("CHAT/source-conversation/chosen；版本 SHA-256 "));
        assert!(!draft.text.contains("unselected text"));
        assert!(!draft.text.contains("source-private-asset"));
        assert!(!draft.text.contains("source-private.pdf"));
        assert_eq!(draft.attachments.len(), 1);
        assert_eq!(draft.attachments[0].id, "attachment-target-one");
        assert_eq!(draft.model_id.as_deref(), Some("DEEPSEEK_V4_PRO"));
        assert_eq!(draft.tone_override.as_deref(), Some("professional"));
        assert_eq!(draft.web_search_override, Some(false));
        assert!(selected_message(
            &source,
            &source_workspace_id,
            "source-conversation",
            "missing",
            1
        )
        .is_err());
        assert!(selected_message(
            &source,
            &source_workspace_id,
            "source-conversation",
            "chosen",
            2
        )
        .is_err());
        let mut deleted = exchange.clone();
        deleted["conversations"][0]["deleted"] = true.into();
        connection
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
                params![deleted.to_string(), source_workspace_id],
            )
            .unwrap();
        assert!(selected_message(
            &source,
            &source_workspace_id,
            "source-conversation",
            "chosen",
            1
        )
        .is_err());
        connection
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
                params![raw, source_workspace_id],
            )
            .unwrap();
        assert_eq!(
            raw,
            connection
                .query_row::<String, _, _>(
                    "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
                    [&source_workspace_id],
                    |r| r.get(0)
                )
                .unwrap()
        );
        target.connection().unwrap().execute(
            "UPDATE desktop_conversation_drafts_v1 SET text='expired text',updated_at_ms=?1 WHERE workspace_id=?2 AND conversation_key=?3",
            params![system_now_millis() - NEW_CONVERSATION_DRAFT_TTL_MS - 1, target_workspace_id, NEW_CONVERSATION_DRAFT_KEY],
        ).unwrap();
        append_to_new_draft(&target, &target_workspace_id, &selected).unwrap();
        let renewed = target
            .read_conversation_draft(&target_workspace_id, None)
            .unwrap()
            .unwrap();
        assert_eq!(renewed.text, selected);
        assert!(!renewed.text.contains("expired text"));
        drop(target);
        let reopened = DesktopWorkspaceStore::open(root.path().join("target")).unwrap();
        assert_eq!(
            renewed.text,
            reopened
                .read_conversation_draft(&target_workspace_id, None)
                .unwrap()
                .unwrap()
                .text
        );
    }
}
