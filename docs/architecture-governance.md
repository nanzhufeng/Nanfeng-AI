# 南枫 AI 架构治理

> 2026-08-16 权威修订：Local Persistence 与 Provider Adapters 是同一产品数据链的并列基础设施，不构成独立本地工作区。Application Use Case 负责权限/边界/可见错误和原子事务；用户已选择导入或执行后不再追加产品级确认闸门。

> 本文只记录南枫 AI 的项目事实、概念所有权和明确边界。通用 App 架构原则由用户级治理基线维护。

## 依赖方向

```text
Android UI / 系统入口
        ↓
Application Use Cases
        ↓
Domain Contracts
        ↓
Repository / AI Harness Interfaces
        ↓
Local Persistence / Provider Adapters / Platform Services
```

- UI 不直接访问数据库、Android Keystore 或 OpenRouter HTTP API。
- Provider Adapter 不决定知识是否保存，也不直接写业务数据库。
- 输入适配器只规范化文字、分享内容和图片，保留来源证据。
- Application Use Case 负责权限校验、调用、候选校验和正式保存的完整事务。
- AI Hub 只保留未来接口边界，V1 不实现远程依赖。

## 多供应商模型编排边界

- `MultiModelOrchestrator` 是 Direct／Compare／Auto 三种模式的唯一编排 owner；它只生成计划，不访问 UI、Key、网络、数据库或后台任务。
- `DIRECT` 精确保留用户选择的 Logical Model、Deployment 与 Provider；不得调用 Auto Router。
- `COMPARE` 为每个唯一 Deployment 生成独立分支计划，所有分支引用同一 `CanonicalContextSnapshot`；默认不综合、不自动采用到共享上下文，也不得调用 Auto Router。
- `AUTO` 是唯一可调用 `AutoRoutingPort` 的模式；路由失败必须显式失败关闭。
- Logical Model 表达用户理解的模型资产；Deployment 表达该模型在特定 Provider 上的可调用部署；Provider Adapter 表达认证、端点、协议和错误映射。三者不得继续压缩成一个字符串。
- `OpenRouterVerifiedMultiProviderRegistryProjection` 是 MM-O3-B 唯一的 production-composition 目录投影 owner：它只读取当前 `OPENROUTER_CATALOG + VERIFIED + SHA-256 + 非 fallback` 的 `VersionedModelRegistry` 快照，以显式 preset→ChatGPT/Claude 映射投影实际 deployment。P3-C fixture、P6-G `curated:*`、模型展示名、UI 状态、Key、HTTP 和目录写入都不能参与该投影。
- `DirectExecutionProductionComposition` 只在 `AppContainer` 构造惰性 `DirectExecutionApplicationOwner`；构造不读取 credential/settings、不调用 Auto、不创建 transport/runtime/Usage 端口，也不注入 Activity、ViewModel 或 Workspace。不存在可投影的已验证目录时，Direct 保持 readiness rejection。
- `CompareExecutionApplicationOwner` 是 MM-O4-A/D 唯一 Compare application/dispatch owner，当前未注册到 `AppContainer` 或 UI。它把同一 Canonical Context 与同一 text-only 输入解析为 ChatGPT + Claude 两个精确 deployment，生成一次未勾选、五分钟、一次消费的汇总确认和 content-free grants；确认后只由它暂持 process-memory-only 原文 lease，并在同一临界区交给全双分支 `CompareBatchDispatchPort`。取消、过期、范围变化、port 拒绝/异常、accept 后及所有 Store 失败路径都会释放 lease。默认 port fail-closed；它不读 Key、不发 HTTP、不接 Provider/transport/UI，不预插 assistant、不写 P3 Runtime/receipt/Usage。hash 只能校验不能恢复；restart 的非 accepted content-free 状态必为 fresh text + fresh confirmation required。
- `CompareConversationSessionOwner` 是 MM-O4-B 唯一 Compare Session/Branch IR owner。它只校验并引用现有 `ConversationSnapshot`/`MessageTree` 的当前 user 叶节点，预留两个 assistant/invocation/attempt/cancellation 身份；不创建平行消息树，也不改变 `Conversation.currentLeafMessageId` 或 MessageNode。现有 P3 Runtime 每个 conversation 只持有一个 current runtime，且运行投影要求当前叶，因此本阶段只能输出未来接合 adapter 的 content-free plan，不可伪装为双分支持久化或执行。
- `CompareConversationSessionStore` / `RoomCompareConversationSessionStore` 是 MM-O4-C/D 的唯一 session/branch 持久化 owner。Schema 32 仅追加 session、branch、branch runtime/checkpoint 及 typed intent 的 content-free 事实；MM-O4-D 复用现有 append-only branch event 保存两支一致的 dispatch intent/accepted/rejected-retry-required 状态。每 branch 有独立 assistant/invocation/attempt/execution/cancel/reservation identity。它不向 `message_nodes` 插入 reserved assistant，不改 `Conversation.currentLeafMessageId`，也不写旧 `conversation_runtime_states`。
- `RoomCompareBranchExecutionPorts` 是 MM-O4-F 的唯一 Compare receipt/Usage Room owner。Schema 33 的 `compare_branch_execution_receipts` 只记录每 branch execution 的 identity、运行/终态和安全错误码；它以 Schema 32 branch binding 作为前置，再委托既有 append-only Usage Ledger 写入预留/释放。它不使用 P3 test port，不保存 partial delta/文本/响应/Key，不改消息树/current leaf/P3 runtime；生产 builder 仅注册 `MIGRATION_32_33`，未构造或注册该 port、Compare Store 或 execution adapter。
- `OpenRouterCompareBatchDispatchAdapter` 是 MM-O4-E 唯一 Compare OpenRouter execution adapter，且未注册生产 composition。它只接受 Store 注入的 Schema 32 双 branch identity 与 OpenRouter verified grants；单次 lease 在内存桥接为两条既有 `ProviderTransportRequest`，由同一个 `RealTextExecutionCoordinator` 调用既有 `OpenAiCompatibleProviderTransport`。`AndroidOpenRouterComparePlatformAdapter` 是 MM-O4-G 唯一受审查 Android credential/HTTP adapter：它只能从未过期的已确认 ChatGPT+Claude grants 创建，构造不读 Key/不开连接，且每个精确 provider-facing model 只可一次；取消、过期、错配、重复、缺 Key 和异常均失败关闭，Key 仅在受限 HTTPS 调用栈中短暂存在。它和 Schema 33 `RoomCompareBranchExecutionPorts` 仍未被 `AppContainer`、UI 或 adapter production composition 注入。不得调用 legacy `OpenRouterInferenceAdapter`、读取 Key、伪造 receipt/Usage、改 shared current leaf 或将 sibling terminal 相互回滚。
- 跨 Provider fallback 会改变第三方收件人，必须在执行前获得明确授权；安全拒绝永不参与 fallback。
- 已确认 UI 布局不属于本专项的默认修改范围。方案中的设置页、列布局和移动 Tabs 只作为语义参考，实施时必须服从现有产品表面及独立视觉验收。

## FB-P6-025：意图优先的产品表面治理

- `Conversation`、`Project`、`Attachment`、`Model Selection`、`Provider`、`Search`、`Memory` 与未来 `Tool` 的 owner 不因存在而获得根级 UI 入口；UI 根壳只服务用户当前意图与对象。
- 所有模型 override、上下文、附件、project/workspace 关联及未来工具适用性必须通过所属 conversation/project/file/task owner 读写，页面不得复制为全局前置选择器或平行真值。
- Settings 仅公开持久策略、隐私/安全、数据生命周期、账号/费用与明确管理 override；日常调用应回到对话或对象上下文。Auto 是 P6-G 的默认可发送状态，catalog/policy 是 Settings 的治理事实。
- UI 的渐进揭示不扩大能力：任何 egress、Provider、Agent 或 tool execution 仍必须由既有 Application Use Case 和明确 consent/阶段合同放行。

## 架构所有权表

| 概念 | 产品含义 | 唯一所有者 | 公开入口 | 生产消费者 | 禁止的平行规则 | 最小验证 | 当前状态 |
|---|---|---|---|---|---|---|---|
| Capture Draft | 尚未外发或保存的文字、分享或图片草稿 | Capture Domain | `CaptureDraftRepository` | 捕获 UI、发送确认 | 页面直接生成正式知识 | Room 回读字段和顺序一致 | P2-C 文本/分享/图片本地验证 |
| Source Evidence | 输入来源、时间、原始附件引用与贡献字段 | Evidence Domain | `CaptureDraftRepository` / `KnowledgeRepository` | 草稿、知识、导出 | 把来源拼入普通备注 | Room 保存与导出可回溯原来源 | P2-A 本地验证 |
| Attachment | App 私有副本的逻辑引用、MIME、大小和哈希 | Attachment Domain / `PrivateAttachmentRepository` | `PrivateAttachmentStore` → Repository | Capture/Knowledge、P3-G 对话引用与受控缩略图 | Conversation/UI/Export/Ledger 保存外部 URI、私有路径、EXIF 或二进制 | 资产目录回读、哈希和有界私有缩略图 | P3-G Schema 8/本地合同验证；GC/删除与外发未实现 |
| Third-party ZIP import | 已选择的 ChatGPT/Claude 导出包的格式登记、私有 staging、会话/资产候选与直接原子提交 | P6-K provider strict adapter / `ZipImportTask` / Conversation commit owner；K6 profile owner；K8 仅 `RoomP6KZipManualAssetLinkOwner`（Android）/ `p6k_zip_asset_link_receipts`（Desktop） | Settings → 数据导入 → 明确选择 ZIP；K8 还须明确资产+已导入 message | Conversation、既有 Attachment/Preview 与 profile owner | JSON Adapter、UI、DAO、profile/settings 自动解压、文件名猜归属、覆盖/merge用户数据，或保存外部路径/Key | metadata preflight、未知版本拒绝、private archive、receipt/provenance、幂等/冲突、重开与 batch revoke | K7 证明真实包无 message-scope identity，故默认 `UNMAPPED_REJECTED`。K8 只在明确操作后校验 path/hash/MIME/size/target/provenance，并复用既有 attachment renderer；Android 任一 revoke 失败保留 task/archive 供重试 |
| Egress Consent | 一次第三方外发的明确同意 | AI Request Use Case | `ConfirmAiRequest` | 捕获、对话、图片识别 | 记住一次确认后永久放行 | 未确认绝不发出网络请求 | 文档合同 |
| Multi-model Orchestration | Direct／Compare／Auto 同级计划与模式边界 | `MultiModelOrchestrator` | `MultiModelOrchestrationRequest` → `MultiModelExecutionPlan` | 后续 Direct/Compare/Auto Application owner | UI/Adapter 直接决定模式；Direct/Compare 经 Auto Router | 纯领域合同证明 Router 仅由 Auto 调用 | MM-O1 领域核心 |
| Logical Model / Deployment | 用户理解的模型资产与特定 Provider 可调用部署 | Logical Model / Deployment Registry + `OpenRouterVerifiedMultiProviderRegistryProjection` | `LogicalModelId` / `ModelDeploymentId` / `ProviderHandle` | 编排、惰性 Direct owner、未注册 Compare owner | 用一个 model 字符串混合模型、端点和收件人；用 fixture、展示名或 `curated:*` 猜测实际目标 | 目录版本、部署失效与历史实际目标可回读；未验证/未知价格/映射缺失拒绝 | MM-O3-B 只读 projection、Direct 惰性注册与 MM-O4-A Compare 纯应用解析；真实目录、UI 和 egress 待后续 |
| Canonical Context Snapshot | 一次多目标执行共同使用的规范化上下文事实 | Context Snapshot Owner | `CanonicalContextSnapshotRef` | Direct/Compare/Auto、后续缓存 | 各 Adapter 自行拼接不同历史或附件范围 | Compare 所有分支引用同一 ID/hash/revision | MM-O1 纯引用合同；正文构建待后续 |
| Compare Execution Consent / Dispatch | 两个第三方收件人、两支预算、一次授权与单次 batch lease hand-off | `CompareExecutionApplicationOwner` + `OpenRouterCompareBatchDispatchAdapter` + `AndroidOpenRouterComparePlatformAdapter` | request → summary confirmation → content-free grants → batch dispatch → P3 coordinator | Compare Session/Branch IR、未来明确授权 Runtime/Usage owner | 每支再确认、部分交付、hash 恢复正文、旧授权重放、legacy adapter 或 Direct owner 外发 | ChatGPT + Claude 两目标、Auto 零调用、同 context/lease/Schema32 handles 双 grants、授权后精确模型 one-shot、独立 terminal、取消/过期/异常释放、restart fresh confirmation | MM-O4-G；无已注册 Compare UI/生产组合，无 Key/真实 network/Room text/receipt/Usage |
| Compare Session / Branch | 同一 user 父节点的两支 Compare 预留、dispatch 状态、后续追问、采用与显式综合关系 | `CompareConversationSessionOwner`（IR）+ `CompareConversationSessionStore`（Schema 32）+ 既有 `MessageTree` | content-free session/branch/dispatch/adopt/synthesis intent → Room readback | 后续 Compare UI、Runtime、分支追问、Usage 聚合 | 平行消息树、修改共享 current leaf、预插空 assistant、跨支上下文混入、自动 adopt/synthesis、把 synthesis 伪装原回答 | 双 branch identity/独立 runtime checkpoint/dispatch restart status、同 intent replay/冲突关闭、追问排除 sibling、adopt 保留 sibling | MM-O4-D Schema 32 event reuse；不写 MessageNode、P3 receipt、Usage 或真实执行 |
| Real-service Acceptance | 用户授权前冻结的真实验收范围与单次执行资格 | Acceptance Contract | `RealServiceRunSpec` → `RealServiceDryRunPreflight` → `RealServiceAcceptanceTokenService` | 未来受控真实验收 | DryRun 读 Key/发网络，或 token 泛化到其他请求 | RunSpec 指纹、零网络报告、重建后重复消费拒绝 | P2-L 离线合同；production egress 仍 Disabled |
| Provider | 模型服务入口与认证、协议、健康状态 | Provider Registry / OpenRouterInferenceAdapter | `ProviderDescriptor` / `OpenRouterInferenceAdapter` | 设置、Adapter、调用 | 页面保存任意端点真值或 Adapter 直接写业务库 | 固定端点、已验证目录、逐次外发与预算门禁、内部事件投影一致 | P2-K 传输合同已实现但生产 egress 默认阻止；真实服务待用户授权 |
| Model | 可选择、可追踪的具体模型资产 | Model Registry | `VersionedModelRegistry` / `ModelRegistrySnapshot` | 选择器、Harness、记录 | 业务代码硬编码 Model ID | 预设策略与已验证目录快照分离，上一稳定版可回退 | P2-J 白名单快照、私有哈希回读与 API 35 冷启动已验证；真实调用待授权 |
| Capability | 模型实际支持的文本、视觉、流式等能力 | Capability Matrix | `ModelCapabilities` | 选择器、请求校验 | 以 Provider 能力代替模型能力 | 不支持视觉时发送前阻止 | 文档合同 |
| Harness Profile | Prompt、Context、工具、解析与验证配置 | Harness Registry | `HarnessProfile` | AI Task Runner | 由页面拼 Prompt | 调用记录保存配置版本 | 文档合同 |
| AI Task | 一次整理或对话请求及其合同 | AI Task Domain | `ConfirmAiRequest` → `RunAiTaskUseCase` | 捕获、聊天 | Adapter 自行解释业务任务或绕过统一预检 | Registry、Provider/Model、逐次 Consent、费用提示、能力、清洗请求、Credential 和 egress policy 同时通过 | P2-K 本地合同；真实服务待授权 |
| Generated Candidate | AI 返回但尚未成为正式事实的候选 | Candidate Domain | `GeneratedCandidate` | 核对 UI、保存用例 | AI 响应直接落正式表 | 未确认不可产生 Knowledge Item | 文档合同 |
| Knowledge Item | 用户确认保存的本地知识资产 | Knowledge Domain | `SaveKnowledgeItem` / `ReadKnowledgeLibraryUseCase` → `KnowledgeRepository` | 列表、详情、搜索、导出 | 保存时丢失来源或覆盖原输入；用候选或 fixture 填充正式列表 | Room 保存、最新优先读取与安全溯源详情一致 | P2-I 已由同一 Repository 生成真实导出；搜索待后续 |
| Conversation | 用户拥有的对话、消息树、当前分支、草稿与管理状态 | Conversation Domain / Draft Repository / `ConversationManagementDomain` / `ConversationBranchHistory` | `ConversationTreeService`、附件草稿用例、编辑/切分支/管理 → `ConversationRepository` | P3-H 本地工作区、搜索、导出与未来历史 | UI/DAO 自行排序、原地改写消息、把 Capture 草稿当 Conversation 草稿、保存路径/URI/二进制或隐式外发 | 当前路径、附件 ID/元数据、修订谱系、所有 leaf 与草稿均可重建；导出只读安全快照 | P3-H Schema 8 本地 Room/分支/附件/尝试历史重建验证；真实 Provider 待后续 |
| Project | 项目生命周期、项目指令、会话集合和知识可见范围 | `ProjectDomain` | `ManageProjectUseCase` → `ProjectRepository` | Projects UI、Conversation 工作区、未来 Context/Knowledge | Conversation/UI/DAO 复制项目指令、由标题或搜索隐式迁移、项目归档级联资产 | stable intent/fingerprint、revision/hash、单会话归属、Room 重建与 Schema 8→9 保留 | P4-A Schema 9 本地合同；工作抽屉只消费活动项目与持久化 `projectId`，不是外部文件夹；Memory/检索/导出/同步/协作未实现 |
| Project Context IR | 指令版本与优先级的本地审计快照 | `ProjectInstructionResolution` | `ProjectContextSnapshot` / `InstructionResolution` | 未来 Context Builder | UI/附件/网页把不可信内容升格为项目指令、构造 Prompt/RunSpec | `SYSTEM > SAFETY > PROJECT > CONVERSATION > CURRENT_USER`、revision/hash 可追溯 | P4-A 本地 IR；不调用模型、不写 Invocation 历史 |
| Context Selection IR | 当前会话路径与项目 revision 的 metadata-only 候选选择 | `ContextSelectionDomain` | `ReadContextSelectionUseCase` → `ContextSelectionSnapshot` | 未来 Context Builder | UI/Harness/DAO 拼 Prompt、混入草稿/兄弟分支/Memory/Knowledge 或读取附件正文 | 当前根→叶路径、Project revision/hash、来源排除矩阵可审计；无正文/Prompt/Token/RunSpec | P4-B Schema 9 不变的本地合同；Memory、检索、摘要/压缩与真实 Context 均未实现 |
| Long-term Memory | 用户显式确认的长期内容、scope、来源、状态与历史 | `MemoryDomain` | `ManageMemoryUseCase` → `MemoryRepository` | Memory UI、未来单独合同下的 Context Builder | UI/DAO/AI/网页/Tool 自动保存、自动注入 Context、静默覆盖或级联删除 | stable intent/fingerprint、追加 revision、高敏拒绝无落库、scope/状态/冲突/重建 | P4-C Schema 10 本地合同；P4-B Context 仍为 NOT_IMPLEMENTED，导出/同步/检索未实现 |
| Explicit Context Body IR | 用户逐次选择的 L0-L3 本机正文预览 | `ContextBodySelectionDomain` | `ReadExplicitContextBodyUseCase` | Context 控制面、未来独立 Harness 合同 | UI/Harness/DAO 自动注入 Memory、把 IR 变成 Prompt/RunSpec 或持久化选择 | P4-B 一致性、当前路径文本、Memory scope/status 与高敏拒绝 | P4-D Schema 10 不变；无 Prompt、egress、检索、摘要或缓存 |
| Local Action Trace IR | 当前 Conversation 的 P3-C 用户动作终态安全元数据预览 | `LocalActionTraceDomain` | `ReadExplicitLocalActionTraceUseCase` | Context 控制面的独立 L3 只读预览 | UI/DAO 从消息正文、runtime event、编辑或切分支快照猜测行动，或把 L3 作为正文/P4-J/K 输入 | 两次 P4-B/lineage 回读一致；仅同会话终态 local-fixture `CONTINUE/RETRY/CHANGE_MODEL`；稳定上限与安全摘要 | P4-M Schema 15 不变；无正文、Prompt、RunSpec、Export、Eval 生产结论或 egress |
| Local Context Compression IR | 用户已明确选择正文的版本化、确定性抽取式压缩预览 | `LocalContextCompressionDomain` | `ReadLocalContextCompressionUseCase` | Context 控制面、未来独立 Harness 合同 | UI/Harness/DAO/Provider 自行截断、重排、语义改写或持久化摘要 | 只委托 P4-D、来源 hash/revision、Unicode code point 截断、高敏整体拒绝 | P4-J Schema 14 不变；无语义模型摘要、Prompt、egress、缓存或写入 |
| Knowledge lifecycle and retrieval | 正式 Knowledge 的 revision、标签、状态、范围与本地搜索 | `KnowledgeDomain` | `ManageKnowledgeUseCase` / `KnowledgeManagementRepository` | Knowledge UI、显式 Context 选择 | UI/DAO/Search 自动注入、复制正文或把索引当真值 | stable revision/hash、ACTIVE scope 重验、bounded deterministic snippet、高敏拒绝 | P4-E Schema 10→11；无 FTS/vector、Prompt、egress 或自动检索 |
| Knowledge relationships | 两条正式 Knowledge 间由用户确认的语义边与可撤销审计 | `KnowledgeRelationshipDomain` | `ManageKnowledgeRelationshipsUseCase` / `KnowledgeRelationshipRepository` | Knowledge 详情、关系列表/审计 | UI/DAO/P4-F 自动建边、各自判断方向、复制正文、自动注入 Context/Export | type canonicalization、ACTIVE same-scope、revision/hash 与高敏重验、append-only revision/intent | P4-G Schema 11→12；无合并、导入、Context、Prompt、Provider 或同步 |
| Conversation Attempt History | P3-C 本地 continue/retry/change-model 的可见安全历史 | `ConversationAttemptHistoryProjection` | `ReadConversationAttemptHistoryUseCase` | P3-H 工作区只读历史 | UI 用当前 runtime、文本或索引推测历史；把 fixture 伪装为真实 Provider/Token/费用 | 同会话 lineage、目标 assistant 与 Invocation 关联过滤，稳定排序，历史 sibling 不受当前 runtime 污染 | P3-H 本地 Room/冷启动验证；真实 Usage/费用待后续 |
| Message Presentation | 不可信 ContentBlock 的安全可选中展示 | `MessagePresentationRenderer` | 版本化 `PresentedMessage` / `PresentationBlock` | P3-D 对话详情 | UI 解析 Provider chunk、执行 HTML/链接、持久化 IR/缓存或回写消息真值 | block identity、畸形降级、缓存局部失效、无执行合同 | P3-D 内存 IR 与 LazyColumn 本地验证；真实性能基线待后续 |
| Invocation | 一次实际调用的安全运行元数据 | Invocation Ledger | `RunAiTaskUseCase` / `ConversationAttemptLineage` | 调用记录、成本、诊断、P3-C 分支谱系 | 保存 API Key、完整 Prompt 或完整响应 | P2-F 四层账本与 P3-C content-free lineage 均追加式；真实 BLOCKED 无 Provider Attempt | P2-F Schema 2、P3-C Schema 6 本地恢复与最小 UI 已实现；真实 Provider 待授权 |
| AI Runtime Event | Provider 原始流的版本化内部事件 | AI Event Protocol | `AiRuntimeEvent` → `ConversationRuntimeStateMachine` | P3-B 本地状态卡、未来对话运行投影 | UI 直接解析 Provider Chunk、事件表保存正文 | 顺序、重复、断档和重连结果确定 | P3-B Schema 5 本地 fixture/Room 重建；真实流待独立验收 |
| Eval Fixture | 可复现的任务输入、期望与断言 | Eval Domain | `EvalFixture` | Harness、Provider、模型回归 | 以单次主观聊天判断质量 | 同一 Fixture 可跨版本回放和比较 | P4 实现 |
| Agent Run | 可暂停、恢复和审计的受控执行 | `ControlledAgentRuntime` / `AgentLedger` | test-only `plan/approve/executeApproved`；Android only `p8c_local_ledger_inspect`；Desktop only `inspect_p8_agent_runs` | 模型/UI/Tool 直接执行或扩大权限；P3/P5/P6/P7 ledger 充当 Agent 真值 | 单调 event/checkpoint、原子 receipt、重建/replay 不重复副作用 | P8-D 本地主体退出：Android Room 20/Desktop SQLite v1；fixture success/failure/cancel/timeout 与 approval/replay/rollback/reopen 合同；release 无 fixture executor，Desktop command 只读 inspect |
| Ecosystem Read Integration | 未来由目标南枫应用拥有、可撤销的最小只读摘要接入 | 目标应用的版本化 Integration Contract；南枫 AI 仅消费获授权 projection | 当前不存在；P9-A 仅有审计报告 | 无；尚未接入 | 不得读取其他应用数据库/私有文件或把其内部 Repository、SQLite、导出能力推定为公开 API | 目标应用确认的 stable ID/schema/permission/preview/readback/revoke 合同 | P9-A 审计完成但阻塞：仅发现知识库参考快照，未发现可验证实际目标公开入口；无代码、无连接、无数据访问 |
| P9-B Local Integration Audit | 对未来单目标只读 Adapter 的 secret-free 合同状态与审计底座 | `P9BContractParser` → test-only `P9BLocalTestOnlyHarness` → `P9BIntegrationLedger` | 仅 Android/desktop 定向测试；无 release entry | 无；生产默认 disabled | release DI/UI/Tauri command/Manifest 注册 fixture target；任何 Provider、文件、网络或跨应用调用 | strict parser/preflight、状态/readback/revoke、Room 20→21、Desktop SQLite reopen；真实 target 独立验收 | LOCAL_TEST_ONLY 基础完成；不拥有目标数据，不是 P9 接入或退出证据 |
| API Key | OpenRouter 本机凭据 | Secure Credential Store | `ProviderCredentialStore` | OpenRouter Adapter | 写入 Room、日志或导出 | Keystore AES-GCM、密文往返与泄漏检查 | P2-D 本地实现；模拟器待验 |
| Export Package | 可迁移的用户数据协议 | Export Domain | `ExportKnowledgePackageUseCase` → `KnowledgeExportStore`；`ExportConversationPackageUseCase` → `ConversationExportStore` | 导出 UI、未来桌面端 | 导出展示文本代替协议值或候选/账本；Knowledge 与 Conversation 包混用 | 各自版本化 Manifest + Payload + SHA-256 回读，附件仅安全元数据引用 | P2-I Knowledge 与 P3-E Conversation 私有文件/重建回读本地验证 |
| P6-A Cross-platform text conversation exchange | 一个明确选择的 Android 普通文本会话，使用共享 `nfai.exchange.v1` 交给 Desktop 独立工作区导入 | `ExportConversationExchangeUseCase` + `NfaiExchangeV1Gateway`；SAF 只由 `AndroidConversationExchangeExportPort` 输出 | Android 设置 → 对话 → 导出当前文本会话到 Desktop | Android Settings 入口、既有 Desktop workspace exchange owner | UI/DAO 各自拼 JSON、导出附件却不复制字节、把 `.nfai-exchange` 当 P5 备份/同步，或声明完整工作区保真 | 已保存活动 CHAT、无 Project/草稿/附件/工具结果，canonical semantic hash、gateway preflight、SAF SHA-256 回读 | Android 导出窄范围已接入；Desktop 实际导入→再导出、完整对象 mapper、异常恢复、Windows/OPPO 均未验证 |
| Hub Policy | 未来是否启用 AI Hub | Hub Integration Boundary | `HubPolicy` | Provider Framework | Hub 成为应用启动依赖 | V1 无 Hub 仍能完成闭环 | 仅预留 |

## V1 入口矩阵

| 入口或消费者 | 是否进入首个闭环 | 角色 | 唯一入口 | 最小验证 |
|---|---|---|---|---|
| 手工文本输入 | 是，P2-C 已实现 | 受影响 | `CaptureTextDraftUseCase` | 空白拒绝、Room 草稿与重建恢复 |
| Android 文本分享 | 是，P2-C 已实现 | 受影响 | `AndroidTextShareAdapter` → `CaptureTextDraftUseCase` | `text/plain`、来源清洗与重复 Intent 门禁 |
| 相册图片 | 是，P2-B 首个系统图片入口 | 受影响 | Photo Picker → `CaptureGalleryImageUseCase` | 取消不写库、私有复制、Room 恢复与预览 |
| 拍照 | 待确认 | 不得默认虚构 | Image Input Adapter | 权限、取消与临时文件回收 |
| 系统图片分享 | 待确认 | 不得默认虚构 | Share Input Adapter | URI 权限与私有复制 |
| 文件、语音、视频、网页解析 | 否 | 不存在于首个闭环 | 未来 Adapter | 不为表格完整提前建空壳 |
| OpenRouter 文本调用 | 是 | 受影响 | OpenRouter Adapter | 文本、流式、错误、用量 |
| OpenRouter 图片调用 | 是 | 受影响 | OpenRouter Adapter | 能力判断、图片请求、错误、用量 |
| Claude 模型选择 | 是 | 受影响 | Model Registry | 实时目录映射预设，不写死 ID |
| 本地保存与再次读取 | 是 | 受影响 | Knowledge Repository | 候选确认后保存，字段一致 |
| 导出 | 是，最小协议 | 精确保真 | Export Use Case | 版本、引用和来源不丢 |
| 云同步、账号、AI Hub | 否 | 不存在于 V1 | 未来边界 | 不能成为当前代码依赖 |
| Desktop / Web | 否 | 后续消费者 | 共享领域协议 | 当前不创建客户端空壳 |

## 技术边界

- 首发平台：Android。
- UI：Kotlin + Jetpack Compose。
- 业务数据：Room 初始 Schema `1` 是 Android 本地真值；未来升级必须添加显式 Migration，不能通过清库处理。
- 图片：先复制到 App 私有空间，再预览和外发；不得长期依赖临时外部 URI。
- 附件：二进制存 App 私有文件目录，元数据、哈希和引用存本地数据库。
- 跨端：优先复用领域语义、版本化协议和合同测试，不把共享 UI 或共享运行时作为 V1 前置。
- 密钥：Android Keystore 保护的本机凭据存储。
- 首个真实 Provider：OpenRouter。
- 第一测试模型族：Claude；具体 Model ID 在实现和测试时读取当前目录。
- 测试替身：Mock Provider 与真实 Adapter 使用同一上层合同。
- 未来同步：本地数据库继续是业务真值，云端只存版本化 AES-GCM 加密快照；不进入 V1。

## 外部参考与供应链边界

- GitHub 研究证据和采用决定见 `GITHUB_MATURE_PROJECT_REFERENCE_RESEARCH.md`。
- 公开仓库只提供能力级经验，不成为项目领域真值或依赖批准。
- AGPL、社区许可证、修改版许可证和企业目录代码默认只读研究；任何源码复用先做文件级许可证、传递依赖和分发影响审计。
- 新依赖必须记录精确版本、数据外发、遥测、替代方案和回滚；动态版本与未核验远程配置禁止进入构建。

## 当前技术债务与待定项

- P1 Android 工程、领域合同、Mock Provider 与内存 Repository 已完成本地工程验证：6 项定向测试、Lint 和 Debug 构建通过。
- Room 初始 Schema、版本化领域快照、私有附件副本/哈希与 P2-I 应用私有真实导出/回读已完成本地验证；附件垃圾回收、删除/回收站、Schema 升级 Migration、导入和静态数据加密仍待设计与验证。
- P2-F 已将 Invocation Ledger 落入 Room Schema 2：四层记录在单一事务写入，稳定 ID 幂等，完成时间倒序读取，Schema 1→2 显式迁移保留旧草稿；调用记录 Dialog 只读安全元数据。账本暂不提供删除入口，未来保留/删除须单独设计。
- P2-K 已将推理结构收口为 `ConfirmAiRequest → RunAiTaskUseCase → OpenRouterInferenceAdapter → OpenRouterInferenceTransport`：固定官方 POST、内存 Authorization、响应投影、一次幂等重试和结构化错误只在 Adapter 内；生产 App 注入 `Disabled` egress policy，在读取 Key 和发请求前阻止。真实 Key、真实外发和真实 Provider 成功仍待独立授权。
- P2-L 已把真实服务前验收收口为 `RealServiceRunSpec → RealServiceDryRunPreflight → RealServiceAcceptanceTokenService`：合成夹具和 Snapshot/预算/Consent 指纹可审计，DryRun 没有 Transport/Key loader/Authorization，token 只绑定一次精确 Spec 并可跨重建拒绝重用；它未接到 P2-K Adapter，不能改变 Disabled policy。
- P2-M 仅为用户授权的固定文本 Spec 增加 `P2MRealServiceExecutor`：DryRun 通过后消费同一 Spec 的 app-private nonce，再以 `ExactSingleUseRun` 和一次 Attempt 接入既有 Adapter；真实响应仍只投影 Candidate/Ledger，不能自动写 Knowledge 或成为通用 egress 能力。
- P2-M 的唯一实际动作已在占位 Key 检查处于 HTTP 前 BLOCKED；nonce 已消费，账本没有 OpenRouter Attempt。因此当前没有真实服务成功、Token、费用或 Candidate 事实，下一次尝试必须是新 Key 与新授权。
- P3-A 已将 Conversation / Message Tree 落入 Room Schema 4：Conversation Domain 管树和 current leaf，Repository 管原子持久化/重建，消息内容与 Invocation Ledger 分离。Project ID、Memory 来源、Tool 安全摘要、附件引用与 checkpoint 只保留版本化接口；没有启用对应功能、流式或 egress。
- P3-B 已将 `AiRuntimeEvent → ConversationRuntimeStateMachine → RoomConversationRepository` 落入 Schema 5：事件事实只含身份/顺序/指纹/安全元数据，正文只在 MessageNode 规范化投影；停止、失败、重放、断档与恢复检查点均由状态机和单事务控制。生产 egress 仍 Disabled。
- P3-C 已将 `ConversationActionOrchestrator → ConversationRuntimeStateMachine → RoomConversationRepository` 落入 Schema 6：continue/retry/change-model 由 action owner 校验，新的 Invocation/intent/lineage 与 started 事件和消息投影同事务保存。Lineage 不保存正文或凭据；生产 egress 仍 Disabled。
- P3-D 已在不升级 Schema 的前提下接入安全展示、长列表和草稿恢复：Renderer 只缓存内存 IR；`conversation_drafts`/附件引用仍是草稿唯一真值，消息追加和成功清草稿同事务；Runtime State Machine 仍是错误终态唯一所有者。生产 egress 仍 Disabled。
- P3-G 已以 Schema 7→8 唯一迁移理由剥离 Conversation 私有路径：`PrivateAttachmentRepository` 管资产目录；草稿/消息仅含安全 `AttachmentId` 引用与元数据；私有预览为有界缩略图。含附件 user 消息保持不可编辑，加入对话固定为 local-only/no-egress，不产生 Provider、Usage 或外发。生产 egress 仍 Disabled。
- P3-H 以不变的 Schema 8 公开 `ConversationAttemptHistoryProjection`：只读取同会话的 P3-C append-only lineage 与对应 assistant MessageNode；当前 runtime 仅在 Invocation/Message 精确相同时显示本地事件 Token，历史项不回填/估算 Token 或费用。生产 egress 仍 Disabled。
- P4-B 在不变的 Schema 9 建立 `ContextSelectionDomain → ReadContextSelectionUseCase`：只从当前 Conversation 根→叶路径和 Project revision 生成无正文的 metadata-only IR；草稿、兄弟分支、附件、Tool、Knowledge、Memory、检索、摘要/压缩和缓存都明确排除或未实现。它不构造 Prompt、RunSpec、Invocation 或网络请求，production egress 仍 Disabled。
- P4-J 以不变的 Schema 14 建立 `ReadExplicitContextBodyUseCase → LocalContextCompressionDomain → ReadLocalContextCompressionUseCase`：只从用户明确选择且已被 P4-D scope/revision/hash/高敏重验通过的瞬时正文生成 `p4j-extractive-v1` 抽取式压缩 IR。策略、Unicode code point 前后截断、来源/压缩 hash 与整体拒绝由 Domain 独占；无语义模型摘要、Room/缓存/Export/Prompt/RunSpec/Provider/egress，production egress 仍 Disabled。
- P4-C 以 Schema 9→10 新增 `MemoryDomain → ManageMemoryUseCase → RoomMemoryRepository`：Memory 的正文、scope、来源、状态、敏感门禁与 revision 由 Domain 独占，Room 只负责事务、intent 重放、冲突和重建。不会读取 `memorySources` 或改变 P4-B 的 `MEMORY = NOT_IMPLEMENTED` 边界。
- P2-I 已将正式 Knowledge 导出收口为 `KnowledgeRepository → ExportKnowledgePackageUseCase → KnowledgeExportStore`：真实 `.nfai` 文件以 Manifest + Payload + SHA-256 落地、同文件回读和重建复验，附件只保留 ID/MIME/大小/哈希，不含 Key、Prompt/响应、Candidate/账本内容、原图、附件正文或路径。Room Schema 未变；导入、搜索、图谱和编辑管理仍未实现。
- Claude 级对话所需的 Anthropic 原生能力尚未判断是否必须增加原生 Adapter。
- P2-B 相册与 P2-C 手工文本/`text/plain` 分享都已进入本地自动验证；最近草稿按文本/图片分支恢复，系统文本分享有来源清洗与重复 Intent 门禁。P2-C 模拟器/真机仍待验收；系统图片分享与拍照继续保持不存在，须分别完成合同和真机门。
- 全生命周期阶段门和长期边界由 `MASTER_DEVELOPMENT_BLUEPRINT.md` 统一控制。
