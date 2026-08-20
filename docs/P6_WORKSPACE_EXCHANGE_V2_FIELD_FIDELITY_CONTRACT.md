# P6 工作区交换 v2 字段保真与拒绝合同

## 结论与范围

`nfai.exchange.v2` 是 P6 全对象交换的**协议/IR 前置**，不是 Android 完整工作区 UI、SAF 或任一端生产导入已开放的声明。v1 保持为已交付的窄语义投影；不得把 v2 fixture、schema 或预检说成真实迁移完成。

v2 只接纳下表中有现存 Android owner、清晰字段语义、并且不会携带路径、URI、凭据、运行时事件或 Provider 原始负载的事实。Desktop 的当前唯一持久 owner 仍是 `workspace_exchange.exchange_json` + `object_provenance` + `import_journal`：在完成 SQLite migration 和原子 transaction 前，它只能严格验证并保留 v2 IR，不能声称已还原为 Desktop 原生 Project/Knowledge/Memory owner。

## 逐字段矩阵

| 对象 / Android 唯一 owner | 字段事实 | v1 | v2 目标与可逆性 | 迁移、降级或必须拒绝 |
| --- | --- | --- | --- | --- |
| Project / `ProjectDomain`、`ProjectRepository` | `id,title,description,pinned,archived,createdAt,updatedAt` | 已表示 | 原样保留，可逆 | v1→v2 不猜 `appearance` / 指令；缺 v2 必填 metadata 时拒绝完整交换 |
| Project | `color,icon,schemaVersion` | 丢失 | `appearance.color/icon`、`schemaVersion`，可逆 | 枚举未知、null 组合或版本不受支持拒绝；不以默认颜色/图标补写 |
| Project | 每条 `ProjectInstructionRevision(id,revision,content,source,contentHash,createdAt,schemaVersion)` | 仅最大 revision 数字 | `instructionHistory` 全量、有序、可逆 | 任何 revision 缺失、重复、hash 不符或未知 source 拒绝；v1 不可升格 |
| Conversation / `ConversationRepository`、Message Tree | v1 message tree、title、Project、生命周期、revision | 已表示（不含所有 node owner 字段） | 保持 v1 语义；v2 另带 `autoTitlePending,surface,schemaVersion` | `WORK`、草稿、ToolResult、runtime/checkpoint、invocation/attempt 不是交换事实；存在即拒绝，不投影成静态消息 |
| Conversation | `defaultProviderId,defaultModelId,harnessId,harnessVersion,contextPolicyVersion,memorySources` | 丢失；v1 直接拒绝 memory source | `settings` 全量保留，可逆 | provider/model/harness 只是配置 ID，不带凭据/路由偏好；memory source 需指向同包 Memory 且 `sourceKind/sourceVersion` 受限，否则拒绝 |
| Knowledge / `KnowledgeDomain`、`KnowledgeManagementRepository` | 内容、tags、scope、project、status、lifecycle hash/time | 已表示（body-byte hash 是投影） | 保留 owner `contentHash`、`schemaVersion` 与 v1 body hash 各自语义，可逆 | 不允许把 owner `title+body` hash 偷换成 v1 body hash；两者均不符拒绝 |
| Knowledge | `sourceEvidence`、`CandidateProvenance`、全部 `KnowledgeRevision` | 丢失 | source type/time/contributed fields、candidate/invocation/provider/model/harness 身份以及完整 revision history | `sourceReference` 不在 v2：它可能是路径/URI，非 null 一律拒绝；Provider raw payload、credential、usage/cost 或 diagnostics 一律拒绝 |
| Knowledge | 私有附件元数据/二进制 | v1 无关联位置，整体拒绝 | `attachments` 只含 id、MIME、显示名、字节数、SHA-256、`assets/<hash>`；二进制内容寻址，可逆 | Android 内部 `reference` 路径永不出包；不可读、hash 不符、未知 MIME、未列 manifest asset 或缺 bytes 均拒绝 |
| Memory / `MemoryDomain`、`MemoryRepository` | body、scope、status、owner content hash、时间 | body/scope/status 有投影，标题/来源/概念和 history 丢失 | title、source、sourceStableId、sourceSummary、conceptHash、lastConfirmed/deleted、完整 revisions，全量可逆 | sourceStableId/sourceSummary 仅允许本地语义标识；疑似 URI/path、凭据或敏感正文拒绝；v1 不可补回 |
| Relationship / `KnowledgeRelationshipDomain` | endpoints、status、revision、createdAt | v1 只安全表示 `RELATED`；其余被拒绝 | 全部真实 type、scope/project、updatedAt、created/latest intent、suggestion source、完整 revision history，可逆 | endpoint 必须是同包 Knowledge 且 scope/project 与 endpoints 一致；未知 enum、缺历史或跨 scope 一律拒绝 |
| Conversation/Knowledge 附件 owner | display name、MIME、size、SHA-256、classification | Conversation 已表示；Knowledge 丢失 | 每项 asset metadata + content-addressed bytes，引用可逆 | EXIF、内部 storage key、外部 URI/path、picker token 一律拒绝 |
| Safe app settings / `NfaiExchangeSafeSettings` | `uiLanguage,theme` | 已表示 | 原样保留，可逆 | Provider、credentials、route preference、runtime、usage/cost、diagnostics、path/URI 永远不进入 v2 |
| Desktop / `DesktopWorkspaceStore` | 当前 `workspace_exchange` serialized IR、asset archive、provenance、journal | v1 原子导入已闭合 | v2 先以同一 canonical IR/semantic hash 严格验证；后续迁移必须把 IR、asset index、provenance、journal 放在一个 transaction | 本合同不允许 v1 importer 将 v2 当 v1 解析，也不允许 v2 verifier 直接写 SQLite；未知字段或任一 owner 无法恢复时拒绝整个包 |

## v2 当前最小实现与停止门

- `protocol/nfai.exchange.v2.schema.json` 与 `protocol/scripts/exchange-v2-lib.mjs` 是 schema/canonical semantic hash 的唯一协议事实；`nfai.exchange.v2.golden.json` 同时由 Android 和 Desktop 定向合同读取。
- v2 预检为**纯 IR**：不读 Room、SQLite、私有附件、SAF、文件 picker、网络、Provider 或 Key，也不注册 UI。它的功能是把上述 owner 字段缺失/不安全/不可验证的情况明确拒绝，防止 v1 的静默丢失被重新引入。
- Android 的 `NfaiExchangeV2OwnerMapper` 已能从显式选择的 owner 只读生成并复验 exact IR；它仅在内存中核验附件 bytes/hash，不保留 bytes、不建 package、不写 Room，也不注册 UI/SAF。它拒绝缺 history、`sourceReference`、定位符、私有 `reference` 外泄、运行时节点与无法证实的附件。这个 owner mapper 不能替代本段的纯 IR validator，后者仍不访问任何 owner。
- 下一增量才可让 Desktop 使用独立 schema version 的 staging + asset archive + SQLite transaction + journal 原子导入；两端都要覆盖重开、回导、拒绝和中断回滚。完成前不开放“完整工作区”选择或 SAF。

## 用户入口

本增量没有用户可见能力、按钮或设置入口，故 Android/Desktop 的“设置 → 功能审阅”不新增条目。将来仅当 v2 owner mapper、原子导入及真实文件链都闭合时，才在同一改动登记“完整工作区交换”的去留、设置二级入口与不新增聊天/Composer 常驻按键建议。
