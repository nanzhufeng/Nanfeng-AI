# 南枫 AI P3-A Conversation / Message Tree 本地持久化合同

日期：2026-08-12  
状态：已实现本地领域、Room Schema 3→4、最小可见会话入口；不含流式、真实 Provider、Tool、Project、Memory 或 Agent 功能

## 唯一目标与所有权

```text
ConversationTreeService（树、当前分支、上下文路径、编辑分支）
→ ConversationRepository（Room 事务、幂等、进程重建）
→ conversations / message_nodes / content blocks / drafts / memory-source references
→ 最小 Compose 会话空态与本地列表
```

- Conversation Domain 是 Conversation、MessageTree、Branch 和 current leaf 的唯一所有者；`MessageTree.contextPath()` 只返回所选根→叶路径。
- `ConversationRepository` 是唯一持久化入口，负责单事务保存、稳定 ID 冲突拒绝和 Room 回读；UI 不接触 DAO。
- Invocation Ledger 仍只保存运行安全元数据。消息只持有 `invocationId` 关联，不复制 Key、Authorization、Prompt、Provider 原始响应、Token 或费用。

## 版本化领域合同

- `Conversation` 保存稳定 ID、标题、Project ID 预留、当前叶、归档/置顶/软删除预留、设置与 schema version。
- `MessageNode` 保存稳定 ID、父节点、同级稳定排序、合法角色（system/user/assistant/tool）、有序 `ContentBlock`、交付状态、修订、Invocation 关联和恢复 checkpoint。`PARTIAL` 只允许 assistant，以便未来流中断保留可解释部分输出。
- `ContentBlock` 当前支持 Text、私有 Attachment 引用和 ToolResult 安全摘要；附件字节与外部 URI 不复制进会话。
- `MessageRevision` 记录修订号及原消息 ID。编辑历史 user 消息创建同父同角色的新节点，不改写旧节点；assistant 重试/换模型仍留给后续 P3 增量。
- `Branch` 是由稳定 leaf ID 派生的根→叶快照。兄弟分支不混入当前 Context；切换只接受叶节点。
- `ConversationDraft` 独立保存未发送文字与附件引用。`ConversationSettings` 已预留 Provider/Model/Harness、Memory 来源及 Context policy，但不实现对应业务功能。

## Room 与迁移

- Schema 4 显式 `MIGRATION_3_4` 新增 `conversations`、`message_nodes`、`message_content_blocks`、`conversation_drafts`、`conversation_draft_attachments` 与 `conversation_memory_sources`。
- 不修改或清空 P2 的 Capture、Attachment、Ledger、Candidate、Knowledge 表。迁移失败不得降级为清库。
- 节点和内容块使用业务 UUID 与 `(parent, siblingPosition)` 稳定排序；同 ID 的不同消息内容被拒绝，完全相同的重复保存可幂等回读。
- P3-A 不开放归档、置顶或删除。未来删除只能是用户明确触发的软删除/保留期回收策略；不得级联静默删除 Invocation Ledger，附件回收需确认无有效引用。

## 核心不变量与验证

- 新会话、追加、分支编辑、切换、当前路径、部分输出、草稿与关联位都只在本地执行。
- 定向测试覆盖树不变量、角色、附件/Project/Invocation/checkpoint 预留、Room 幂等和重建、事务失败回滚、Schema 3→4 的 P2 行保留，以及会话表敏感运行字段排除。
- 最小界面显示真实会话空态和保存后的本地列表；它不提供流式聊天或真实服务按钮。
- OpenRouter production egress 保持 `Disabled`。Mock、fixture、loopback 或模拟器不能替代真实文本/图片、Token/费用、真机、OPPO 或发布验收。
