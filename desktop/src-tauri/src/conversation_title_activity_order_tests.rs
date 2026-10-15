use super::*;

fn args(
    workspace: &str,
    action: &str,
    id: Option<&str>,
    revision: Option<u64>,
    fields: Value,
) -> DomainMutationArgs {
    DomainMutationArgs {
        intent_id: format!("title-order-{}", action.to_ascii_lowercase()),
        workspace_id: workspace.into(),
        entity: "conversation".into(),
        action: action.into(),
        object_id: id.map(Into::into),
        expected_revision: revision,
        fields,
    }
}
fn conversation(projection: &WorkspaceProjection, id: &str) -> Value {
    projection.exchange["conversations"]
        .as_array()
        .unwrap()
        .iter()
        .find(|c| c["id"] == id)
        .unwrap()
        .clone()
}
fn retime_fixture(store: &DesktopWorkspaceStore, workspace: &str, id: &str, at: &str) {
    let mut exchange = store.workspace_projection(workspace).unwrap().exchange;
    let c = exchange["conversations"]
        .as_array_mut()
        .unwrap()
        .iter_mut()
        .find(|c| c["id"] == id)
        .unwrap();
    c["createdAt"] = json!(at);
    c["updatedAt"] = json!(at);
    for message in c["messages"].as_array_mut().unwrap() {
        message["createdAt"] = json!(at);
    }
    let hash = refresh_exchange_hash(&mut exchange).unwrap();
    validate_exchange(&exchange).unwrap();
    let connection = store.connection().unwrap();
    connection
        .execute(
            "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
            params![serde_json::to_string(&exchange).unwrap(), workspace],
        )
        .unwrap();
    connection
        .execute(
            "UPDATE workspaces SET semantic_hash=?1 WHERE id=?2",
            params![hash, workspace],
        )
        .unwrap();
}

#[test]
fn title_only_changes_preserve_activity_time_and_versions_after_reopen() {
    for automatic in [false, true] {
        let (_dir, store, imported) = tests::imported_store();
        let workspace = &imported.summary.id;
        let opening = store
            .prepare_ordinary_chat(
                DesktopOrdinaryChatSubmitArgs {
                    workspace_id: workspace.clone(),
                    conversation_id: None,
                    project_id: None,
                    expected_revision: None,
                    text: "公开问题".into(),
                    attachment_ids: vec![],
                    model_id: None,
                    tone_override: None,
                    web_search_override: Some(false),
                    egress_authorization: None,
                },
                Some("http://127.0.0.1:1/chat/completions"),
            )
            .unwrap();
        store
            .update_ordinary_chat_message(
                &opening.projection.attempt_id,
                "公开回答",
                "COMPLETE",
                None,
                None,
                None,
            )
            .unwrap();
        let id = &opening.projection.conversation_id;
        retime_fixture(&store, workspace, id, "2026-10-10T12:00:00Z");
        let newer = store
            .mutate_domain(args(
                workspace,
                "create",
                None,
                None,
                json!({"title":"较新的对话","firstMessage":"新的问题"}),
            ))
            .unwrap();
        retime_fixture(&store, workspace, &newer.object_id, "2026-10-11T12:00:00Z");
        let before_projection = store.workspace_projection(workspace).unwrap();
        let before = conversation(&before_projection, id);
        let title = if automatic {
            "自动整理的新标题"
        } else {
            "手动修改的新标题"
        };
        if automatic {
            let prepared = DesktopConversationTitlePrepared {
                attempt_id: "title-order-fixture".into(),
                workspace_id: workspace.clone(),
                conversation_id: id.clone(),
                assistant_message_id: opening.projection.assistant_message_id.clone(),
                endpoint: "http://127.0.0.1:1/chat/completions".into(),
                provider_id: "QWEN".into(),
                model_id: "qwen3.6-flash".into(),
                messages: json!([]),
                idempotency_key: "title-order-fixture".into(),
                occurred_at_ms: 1_792_022_400_000,
            };
            assert!(store
                .complete_desktop_conversation_title(&prepared, title)
                .unwrap());
        } else {
            store
                .mutate_domain(args(
                    workspace,
                    "update",
                    Some(id),
                    before["revision"].as_u64(),
                    json!({"title":title}),
                ))
                .unwrap();
        }
        let after_projection = store.workspace_projection(workspace).unwrap();
        let after = conversation(&after_projection, id);
        assert_eq!(after["title"], title);
        assert_eq!(
            after["updatedAt"], before["updatedAt"],
            "Title metadata must not change recent-list activity"
        );
        assert_eq!(
            after["revision"].as_u64().unwrap(),
            before["revision"].as_u64().unwrap() + 1
        );
        assert_eq!(
            after["titleRevision"].as_u64().unwrap(),
            before["titleRevision"].as_u64().unwrap_or(0) + 1
        );
        assert_ne!(
            before_projection.summary.semantic_hash, after_projection.summary.semantic_hash,
            "Sync identity still changes"
        );
        assert_eq!(
            conversation(&after_projection, &newer.object_id),
            conversation(&before_projection, &newer.object_id)
        );
        let root = store.root.clone();
        drop(store);
        let reopened = DesktopWorkspaceStore::open(root).unwrap();
        let restored = conversation(&reopened.workspace_projection(workspace).unwrap(), id);
        for field in [
            "title",
            "updatedAt",
            "createdAt",
            "revision",
            "titleRevision",
            "autoTitlePending",
            "currentLeafId",
        ] {
            assert_eq!(restored[field], after[field], "Persisted {field}");
        }
        reopened
            .mutate_domain(args(
                workspace,
                "appendMessage",
                Some(id),
                after["revision"].as_u64(),
                json!({"role":"user","text":"新的对话内容"}),
            ))
            .unwrap();
        let active = conversation(&reopened.workspace_projection(workspace).unwrap(), id);
        assert!(
            active["updatedAt"].as_str().unwrap() > "2026-10-11T12:00:00Z",
            "Content still advances recency"
        );
    }
}
