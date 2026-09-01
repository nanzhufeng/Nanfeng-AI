# 南枫 AI P4-E 本地 Knowledge 检索与显式 Context 选择合同

日期：2026-08-13  
状态：P4 的第五个本地增量；Schema 10→11。仅管理本机正式 Knowledge、确定性检索和用户逐项 Context 选择；不读取 Key、不构造 Prompt/RunSpec、不发 HTTP、不产生费用或图片外发。

## 所有权与数据链

```text
Knowledge UI（明确用户操作）
→ KnowledgeDomain → ManageKnowledgeUseCase → KnowledgeManagementRepository / Room
→ knowledge_items + knowledge_revisions + tags + knowledge_project_scopes
→ SearchKnowledge（query/scope/order/snippet）
→ ContextBodySelectionDomain（仅重验后的明确 Knowledge ID）
```

- Knowledge Domain 独占资产标题/正文、标签、`ACTIVE/ARCHIVED/DELETED`、GLOBAL/PROJECT 关系和 append-only revision；Room 是唯一真值，UI、DAO、Search 与 Context 都不得复制或自行解释这些规则。
- 每次编辑、归档、恢复、软删除/从回收站恢复都会追加可审计 revision。普通读取/导出默认仅 ACTIVE；归档和回收站只能由明确用户操作显示，软删除不抹去历史。
- Schema 10→11 只追加 lifecycle 列、`knowledge_revisions`、tag/revision-tag 关系与索引，并为旧 Knowledge 生成 revision 1；迁移不得 wipe、fallbackToDestructiveMigration 或改写 P1–P4-D 事实。
- 不使用 embeddings/vector DB 或 FTS。搜索是同一 Room 真值的确定性投影：标题、正文、标签、来源以 `Locale.ROOT` 小写子串匹配，空查询可列出，按 `updatedAt DESC, KnowledgeId ASC`，最多 50 条；snippet 至多 240 字符。中文、英文、Markdown 与代码均按原文本匹配，不摘要或压缩。

## Context 边界

- Knowledge 在 Context 中默认关闭，不自动检索或注入。用户先输入检索，再逐项选择结果，最后才可请求本机预览。
- `ReadExplicitContextBodyUseCase` 重读 P4-B 当前 conversation/project metadata，并重新验证 Knowledge 为 `ACTIVE`、GLOBAL 或当前 Project、revision/hash 与高敏门禁。缺失、归档、删除、跨项目、revision/hash 变化或敏感正文均整体拒绝，绝不返回部分正文。
- Context 选择只保存在 ViewModel 内存；关闭、切换会话或进程重建即丢弃。不得触碰 `ConversationSettings.memorySources`、Room、导出包、Invocation、日志、同步或诊断。
- 统一复用 `MemoryDomain.sensitiveRejection`；命中密码、API Key、Authorization/Bearer、恢复码或完整支付卡号时，Knowledge 写入前拒绝，Context 返回前也拒绝。来源引用、URI、路径、附件字节和 Tool/网页内容均不进入检索或 Context 正文。

## 导出、UI 与验证

- P2-I 的 `.nfai` 继续只从正式 Knowledge Repository 读取，以 Manifest + `knowledge.json`、原子写入、同文件回读和 SHA-256 为成功条件。隐藏 Knowledge 不默认导出；Key、Prompt、原始 Provider 响应、URI/路径与附件字节继续排除。
- UI 管理面顶部使用居中的“知识”标题和左侧返回图标，不再重复“本地知识”标题或本地优先说明；进入条目后同一顶部位置切换为“知识详情”，返回图标只退回知识列表，禁止再叠一层详情标题。常驻筛选只保留“知识／归档／回收站”三个生命周期分组，并使用单一紧凑分段控件；来源继续作为条目事实与搜索字段保留，不再常驻陈列“全部来源／历史对话／手工／分享／图片”筛选。新建知识与整理当前对话是操作而非分类，必须从筛选中拆出并使用独立按钮。详情只显示标题、正文、易懂来源、保存时间、状态、标签和编辑／归档／回收站图标；Candidate、Invocation、Provider、Harness、Scope、修订数、重复候选、关系入口和重复安全说明不再常驻展示，但底层审计事实与关系数据不得删除。所有 App 自有 Dialog/Popup/选择面消费当前主题前景色。Context 面的 Knowledge 检索/选择/预览与管理面分离。
- 定向测试覆盖 CRUD/revision/tags/archive/trash、GLOBAL/PROJECT/order/snippet/多语言/隐藏状态、高敏拒绝、显式选择/默认关闭/无持久化、P4-B–D 回归和 Schema 10→11；再分层运行全量测试、Lint、正式签名 Debug/Release 与 API 35 `emulator-5554`。这些本地结果不替代真实 Provider、费用、OPPO、图标或发布验收。
