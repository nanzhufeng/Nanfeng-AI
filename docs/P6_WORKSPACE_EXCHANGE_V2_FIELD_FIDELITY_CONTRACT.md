# P6 工作区交换 v2 字段保真与拒绝合同

## 结论与范围

`nfai.exchange.v2` 是 P6 全对象交换的**协议/IR 前置**，不是 Android 完整工作区 UI、SAF 或任一端生产导入已开放的声明。v1 保持为已交付的窄语义投影；不得把 v2 fixture、schema 或预检说成真实迁移完成。

v2 只接纳下表中有现存 Android owner、清晰字段语义、并且不会携带路径、URI、凭据、运行时事件或 Provider 原始负载的事实。Desktop 的 v2 唯一持久 owner 是独立 `exchange_v2_*` 五表、`exchange-v2/archives/<packageHash>/` 与 journal/receipt；它不读写 v1 的 `workspace_exchange.exchange_json`、`object_provenance` 或 `import_journal`，也不能声称已还原为 Desktop 原生 Project/Knowledge/Memory owner。

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
- Android 的 `NfaiExchangeV2OwnerMapper` 已能从显式选择的 owner 只读生成并复验 exact IR；它仅在内存中核验附件 bytes/hash，不保留 bytes、不写 Room，也不注册 UI/SAF。它拒绝缺 history、`sourceReference`、定位符、私有 `reference` 外泄、运行时节点与无法证实的附件。这个 owner mapper 不能替代本段的纯 IR validator，后者仍不访问任何 owner。
- Android 的未注册 `NfaiExchangeV2PackageWriter` 是 mapper 后的本地 serializer，不是 SAF 或持久化 adapter：它只为 IR 账本明确引用的附件再次只读核验 bytes/hash，在内存中写 canonical `manifest.json + exchange.json + assets/<sha256>`，并对成品执行 v2 package preflight。唯一输出是要求原子发布的有限 port；mapper/ledger/preflight 任一步失败时不得调用该 port，receipt 只含 hash、枚举、计数与 `ownerFieldHashes`。没有 app-data archive、Room/SQLite 写入、导入、UI、SAF、picker、网络或 Key。
- Desktop 的独立 schema version、package、private staging、asset archive、SQLite transaction、journal/receipt、失败注入、重开与回导的唯一正文见 [v2 Desktop 原子导入合同](P6_WORKSPACE_EXCHANGE_V2_DESKTOP_IMPORT_TRANSACTION_CONTRACT.md)。Android 只经设置范围选择后的 SAF CreateDocument 输出；Desktop 的受限 native picker 只接纳单一用户选择的 package，不能说成 Desktop 原生 owner 恢复。

## 文件名、MIME 与回执边界

- Android 建议显示名固定为 `nanfeng-ai-workspace-v2.nfai-exchange`，CreateDocument MIME 固定为 `application/zip`。系统 DocumentsUI 可以把实际用户可见文件名保存为 `*.nfai-exchange.zip`，也可能仅保留普通 `*.zip`；这是 provider 的显示/扩展名行为，不改变 package bytes、manifest、semantic hash 或任一 `ownerFieldHash`。
- Desktop picker 可展示并接纳用户显式选择的 `<stem>.nfai-exchange`、`<stem>.nfai-exchange.zip` 或 Android DocumentsUI 实际保存的 `<stem>.zip` 输入候选。`<stem>` 非空，且不能再以 `.nfai-exchange` 或 `.zip` 结尾；路径片段、任何附加后缀（如 `.nfai-exchange.zip.exe`）、嵌套协议/ZIP 后缀（如 `.zip.nfai-exchange`）一律在读 bytes 前拒绝。普通 `.zip` 从不获得内容有效性或持久化资格，仍必须先通过 v2 exact manifest、语义、owner-field hash 和 asset ledger 的 strict preflight；回导输出继续只接纳 `.nfai-exchange`。
- 文件名与 MIME 只是系统 picker 的入场标签，绝不构成包身份、来源证明或内容豁免。三种允许名都必须依次通过 v2 ZIP entry/size/path 防护、exact manifest、canonical export、IR semantic hash、asset ledger/bytes hash 和全量 owner-field-hash preflight；v1 或任何混入 v1 的包仍由 `packageVersion: 2` / `exchangeVersion: 2` 门禁拒绝。
- Android SAF 成功回执与 Desktop committed/replay 回执都只含 package/semantic hash、匿名计数、枚举和 owner field hashes（以及 Desktop workspace/replay 状态）；不得返回或持久化 selected path、用户显示名、MIME、正文或附件 bytes。文件名改变而 bytes 不变不产生新的语义身份。

## 用户入口

Desktop 回导只从 import、journal 与 receipt 均完整的 private v2 owner 读取 canonical IR 和内容寻址附件。设置页只投影匿名已提交候选与附件计数；用户点选后，native save picker 只能输出精确 `.nfai-exchange` 名称，Rust 仍须严格 preflight 并比较 semantic hash、每一个 `ownerFieldHash` 与附件账本。路径、显示名、正文和附件 bytes 不进入候选、回执或错误。回导不读 v1 工作区、不开放聊天/Composer/工作页入口，且不代表 Desktop 原生对象恢复、备份或同步。

Desktop 现在只在“设置 → 数据与导入”提供 `完整工作区交换（v2）` 的 native picker 二级入口：它只读取用户所选的精确 `.nfai-exchange` 或 DocumentsUI 兼容 `.nfai-exchange.zip` 有限 bytes，依次执行 v2 strict preflight 与独立 private archive/transaction owner，返回 content-free receipt 或脱敏的真实拒绝，不创建 v1/可见工作区。Android 在同一设置层通过显式完整范围与系统保存位置输出 v2 包。Android/Desktop 的“设置 → 功能审阅”同改登记其“待您判断”去留；建议保留设置二级入口，不新增聊天、Composer 或工作页常驻按键。本条不是已完成真实用户文件验收、跨端互通、备份/同步或 Desktop 原生 owner 恢复声明。
