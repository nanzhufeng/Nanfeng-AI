# 南枫 AI 实施计划

> 状态：近期实施子计划。当前只锁定 V1 至 Claude 级对话的实施顺序、合同和验证门槛，不代表相应代码已经完成。  
> 上位总图：`MASTER_DEVELOPMENT_BLUEPRINT.md`。  
> 总图门槛：已于 2026-08-12 通过用户确认；P2-L 已完成真实推理前的离线验收合同、DryRun 与单次 nonce 准备，P2-M 已执行一次授权文本动作但在 HTTP 前因占位凭据阻止。用户已明确把真实推理成功与目标真机改列后期独立验收门，不阻塞 P3 主体开发；这些证据仍未通过，不能由 Mock/loopback/模拟器替代。

## 目标

先交付 Android 上可验证的“文本/分享/图片捕获 → OpenRouter Claude 整理 → 用户核对 → 本地保存 → 再次读取”闭环；达到后停止扩充捕获类型，转入 Claude 级多模型对话。

## 固定边界

- Kotlin + Jetpack Compose。
- 本地单用户、Provider 直连。
- API Key 使用 Android Keystore 保护。
- OpenRouter 首发，Claude 第一测试模型族。
- V1 无登录、云同步、远程 BFF、远程 AI Hub 或 Desktop / Web。
- 不在源码中硬编码具体 Claude Model ID、价格或能力。

## 阶段 0：需求合同

### 交付

- 产品简报。
- 架构所有权表与入口矩阵。
- 领域规则。
- 决策日志与动态交接。

### 退出门槛

- 产品顺序、平台、部署边界、Provider 和图片范围不再冲突。
- 已实现、文档合同和待验证项明确分开。

## 阶段 1：Android 工程与领域骨架

> 状态：已完成（本地工程级验证；未执行模拟器、真机、真实服务或发布验证）。

### 交付

- Kotlin / Compose Android 工程。
- 只承载实际职责的逻辑层：UI、Application、Domain、Data、AI Provider。
- Capture Draft、Source Evidence、Generated Candidate、Knowledge Item、AI Task、Invocation 的领域类型。
- Mock Provider 和内存 Repository，用于无真实密钥测试。

### 定向测试

- 文本、分享和图片输入规范化。
- 未确认不能运行 AI Task。
- Candidate 未确认不能创建 Knowledge Item。
- 保存后再次读取保持来源和溯源。

### 停止条件

- 不接真实 OpenRouter，不提前开发全部页面。

## 阶段 2：本地持久化与图片安全链路

> P2-A、P2-B 已完成；P2-C 已实现手工文本、`ACTION_SEND text/plain`、同一 Room 草稿恢复与最小捕获页。系统图片分享、拍照与真实服务尚未开始；真实导出文件已在 P2-I 单独完成。

### 交付

- 本地数据库与 Repository。
- App 私有附件目录、来源记录和清理策略。
- 相册图片选择作为首个图片入口；系统图片分享与拍照分别通过权限、取消、临时文件和真机门后开放。
- 最小导出协议及版本。

### 定向测试

- 临时 URI 内容复制到私有空间后仍可读取。
- 取消、失败和重建场景不丢草稿、不误存知识。
- 保存、读取和导出字段一致。

## 阶段 3：模型设置与 OpenRouter Adapter

> P2-D 至 P2-I 的本地闭环已完成；P2-J 已完成固定无认证 `GET /api/v1/models` 的公开目录核验、白名单化私有快照、哈希回读与上一稳定版回退；P2-K 已完成固定推理 POST 的 Adapter/Transport 合同与本地 loopback 结构；P2-L 已冻结离线真实服务验收 RunSpec、合成夹具、DryRun 与单次 nonce。它们不是授权后的真实 Key 或真实文字/图片调用；导入、系统目录和真实调用仍未开始。

### 交付

- OpenRouter 固定官方端点预设。
- Android Keystore 凭据存储与隐藏 Key 输入。
- 当前模型目录映射到旗舰、均衡、快速预设。
- 捕获整理的文本与图片请求、用量、费用和结构化错误；流式事件由同一 Adapter 合同支持并在对话阶段成为必需。
- 调用记录只保存安全运行元数据。
- `RunAiTaskUseCase` 是 Ledger 唯一写入口；每个终态以一个事务保存 `Task Run → Provider Attempt → Generation → Validation`。成功、失败、取消有已发生 Attempt；确认门禁为无 Attempt 的 `BLOCKED` Task Run。
- P2-G 的 `ConfirmAiRequest` 只在用户勾选确认后生成一次性 Consent；本地 Mock 成功后以独立 `GeneratedCandidateRepository` 保存待核对候选，用户编辑/取消/确认保存后才触及 Knowledge。Schema 2→3 只增加候选表，Mock `CNY 0` 是本地成本，Token 未估算保持 `null`。
- 查询按完成时间倒序；Token/费用的 `null` 与 `0` 分别显示。Schema 1→2 使用显式迁移，账本本阶段没有删除入口。
- P2-H 的 Knowledge 只读入口为 `ReadKnowledgeLibraryUseCase`：列表按 `createdAt DESC, id DESC` 显示真实已保存 Knowledge；详情回读完整正文、来源及 Candidate/Invocation 安全溯源，Candidate/草稿/账本/fixture 不进入知识列表。Schema 3 未变，不需要迁移。
- P2-I 的导出入口为 `ExportKnowledgePackageUseCase → KnowledgeExportStore`：只读正式 Knowledge，输出 Manifest + `knowledge.json` 的 `.nfai` 包，临时写入/同步/原子移动后必须从同文件解析并核对 SHA-256；空库、取消和失败不产生成功文件，重复导出不覆盖。Schema 3 未变，不需要迁移。
- P2-J 的目录入口为 `OpenRouterRegistryCatalogClient → OpenRouterRegistrySnapshotVerifier → ModelRegistrySnapshotStore → VersionedModelRegistry`：只允许固定公开 GET、无请求体/认证/用户内容；只保存模型白名单字段、来源、ETag、catalog SHA-256 与可复现预设映射。失败不覆盖当前稳定版，重启重算哈希；设置页显示已验证、稳定回退或未验证状态。Schema 3 未变。
- P2-K 的传输入口为 `ConfirmAiRequest → RunAiTaskUseCase → OpenRouterInferenceAdapter → OpenRouterInferenceTransport`：固定官方 `POST /chat/completions`、非流式 JSON、仅内存 Authorization、超时/响应上限、HTTP 错误映射、同 Key 单次重试与响应白名单投影。Registry、Provider/Model、逐次 Consent、费用提示、能力、非敏感清洗文本、Credential 与 egress policy 任一不满足即在 HTTP 前形成无 Attempt 的 BLOCKED；AppContainer 固定 `Disabled`，所以不读 Key、不外发。Schema 3 未变。
- P2-L 的验收入口为 `RealServiceRunSpec → RealServiceDryRunPreflight → RealServiceAcceptanceTokenService`：只接受合成夹具元数据、已验证 Snapshot、预算、Credential 存在性与 Consent 指纹，报告不含 Key/正文且不能创建 Authorization 或网络请求。单次 token 保存在 app-private 原子文件，精确绑定 RunSpec 且重建后不可重复；它不接入 Adapter，AppContainer 继续 `Disabled`，Schema 3 未变。
- P2-M 只执行用户精确授权的一份合成文本 RunSpec：DryRun 保持 Disabled，之后原子消费该完整 Spec 的持久 nonce，才临时构造 `ExactSingleUseRun` Adapter；`retryCount=0` 强制最多一条 HTTP Attempt。它不是设置开关或普通真实服务按钮，图片仍无路径。详见 `P2M_REAL_TEXT_EXECUTION_CONTRACT.md`。
- P2-M 的首次唯一动作已被占位 Key 在 HTTP 前阻止：安全账本为 BLOCKED 且 OpenRouter Attempt 为零，nonce 已消费。故“用户授权的 P2-L 精确非敏感文本真实调用”仍未成功完成，等待新 Key 与新授权，不能在此重试。

### 验证顺序

1. Mock Provider、OpenRouter 配置与错误映射合同测试。
2. 只读公开 Model Registry 的来源、字段白名单、哈希、回退与重启回读核验。
3. P2-K fake/loopback 的请求头、JSON、超时、取消、错误、响应清洗、重试/幂等与无 Attempt 门禁。
4. P2-L fake DryRun 的 Snapshot/能力/清洗/预算/夹具哈希/Credential presence/Consent/nonce/重建与敏感信息排除。
5. 用户授权的 P2-L 精确非敏感文本真实调用。
6. 用户授权的 P2-L 精确非敏感图片真实调用。
7. Token、费用、失败和重试记录核对。

### 停止条件

- 未提供明确测试 Key 和非敏感资料时，只完成模拟与本地测试，不尝试真实服务。
- 不把真实 Key 写入源码、测试资源或文档。
- 没有用户对精确 P2-L RunSpec 的一次外发授权时，不发行可执行授权、不消费 nonce、不改变 `Disabled` policy。

## 阶段 4：最小捕获 UI 闭环

### 页面与状态

- 捕获页：文字、系统分享、图片预览。
- 发送确认：OpenRouter、Claude 实际模型、内容范围、费用提示。
- 生成状态：进行中、成功、可重试错误、取消。
- 核对页：结构化候选可编辑、取消、保存。
- 知识读取：最小列表与详情。
- 设置：模型设置、调用记录。

### 验收

- 模拟器完整链路。
- 真实非敏感文本和图片服务链路。
- 生命周期重建与草稿保留。
- 错误恢复、重复提交防护和保存幂等。
- 真实 Android 设备另列验收，不以模拟器替代。

### 阶段停止规则

闭环达到验收后停止增加文件、语音、视频和网页解析，进入对话阶段。

## 阶段 5：Claude 级多模型对话

### 第一增量

- P3-A 已完成 Conversation / Message Tree Schema 设计评审与本地持久化底座：Schema 3→4 显式迁移，Conversation Domain 管 current leaf/分支/路径，Repository 管 Room 事务与重建，Ledger 不保存消息正文。最小 UI 只提供真实空态和本地会话列表；详见 `P3A_CONVERSATION_MESSAGE_TREE_CONTRACT.md`。
- P3-B 已完成版本化 `AiRuntimeEvent` 与本地确定性状态机：Schema 4→5 只增加事件指纹/顺序事实和运行状态表；开始、delta、usage、checkpoint、完成、失败、取消均经状态机投影，并与 MessageNode/checkpoint 原子保存。最小 UI 仅可启动/观察/停止本地 fixture；详见 `P3B_RUNTIME_EVENT_STATE_MACHINE_CONTRACT.md`。
- P3-C 已完成 continue/retry/change-model 本地动作：继续保留原 `CANCELLED`/`FAILED` 部分输出并以其为父创建新 assistant；重试/换模型重答在同一 user 父节点下创建不可变兄弟版本、切换 current leaf。每次有新 Invocation 与安全 Attempt Lineage；Schema 5→6 不清库；详见 `P3C_CONVERSATION_ACTION_LINEAGE_CONTRACT.md`。
- P3-D 已完成安全 Markdown/代码展示、长会话虚拟化合同和草稿/错误恢复：`MessagePresentationRenderer` 只对已持久化 ContentBlock 生成内存 IR，链接可见但不可执行，HTML/畸形语法安全降级；对话详情使用 `LazyColumn` stable key/contentType。草稿复用 Schema 6 的 `conversation_drafts`，发送消息与成功清草稿在同一事务；详见 `P3D_PRESENTATION_VIRTUALIZATION_DRAFT_RECOVERY_CONTRACT.md`。
- P3-E 已完成本地会话管理、搜索和真实可回读导出：`ConversationManagementDomain` 统一标题/置顶/归档/排序/筛选；Schema 6→7 仅增加无正文的管理 intent 幂等事实，未清库。搜索只投影同一 Room 的标题和当前路径 user/assistant 文本；Conversation 导出使用独立 `Manifest + conversation.json` 私有原子包、同文件回读与 SHA-256，默认仅当前路径。详见 `P3E_CONVERSATION_MANAGEMENT_SEARCH_EXPORT_CONTRACT.md`。
- P3-F 已完成离线用户消息编辑与完整分支历史：仅当前路径纯文本 user 可编辑，原节点不可变；新修订作为同父 user 叶被选中，全部 assistant/user leaf 都可切回。`ConversationBranchHistory → EditConversationUserMessageUseCase / SwitchConversationBranchUseCase → ConversationRepository` 是唯一链路，Schema 7 不变。详见 `P3F_CONVERSATION_EDIT_BRANCH_HISTORY_CONTRACT.md`。
- P3-G 已完成对话附件本地引用与草稿/消息生命周期：Photo Picker 经私有复制/资产目录后才进入 Conversation；草稿和消息只保存安全 `AttachmentId` 引用，缩略图受控有界解码。Schema 7→8 仅为剥离 Conversation 路径字段；无 GC、删除、Provider 或外发。详见 `P3G_CONVERSATION_ATTACHMENT_LIFECYCLE_CONTRACT.md`。
- P3-H 已完成本地尝试历史读取模型：`ConversationAttemptHistoryProjection → ReadConversationAttemptHistoryUseCase` 只显示 P3-C continue/retry/change-model 的同会话安全 lineage，与目标 assistant 状态关联并稳定排序；当前 runtime 绝不污染历史 sibling，fixture 零费率与未知 Token 均不伪装为真实费用。Schema 8 不变；详见 `P3H_CONVERSATION_ATTEMPT_HISTORY_CONTRACT.md`。
- 后续才实现真实 Provider/Usage 可见追踪以及明确合同下的对话附件外发。

### 第二增量

- 消息树与分支切换。
- 附件引用、Projects 和项目级指令。
- 对话搜索、归档、置顶和导出。

### 第三增量

- P4-A 已完成本地 Projects 基础：`ProjectDomain → ProjectRepository/Room` 是项目生命周期、指令 revision、会话单归属和 Knowledge scope 的唯一链路；Schema 8→9 增量迁移保留既有 P1–P3 数据。`ProjectContextSnapshot / InstructionResolution` 只记录系统/安全/项目/会话/当前用户的优先级与 hash，不构造 Prompt 或网络请求；详见 `P4A_PROJECTS_INSTRUCTIONS_KNOWLEDGE_SCOPE_CONTRACT.md`。
- P4-B 已完成 metadata-only Context 选择边界；P4-C 已完成长期 Memory 显式治理：`MemoryDomain → ManageMemoryUseCase → RoomMemoryRepository` 管 stable ID、scope/来源、CRUD、暂停、软删除、revision、重复与确定性冲突。Schema 9→10 不清库；Memory 不自动读取或加入 Context。详见 `P4C_MEMORY_GOVERNANCE_CONTRACT.md`。
- P4-D 建立独立的 L0-L2 本机正文选择：`ContextBodySelectionDomain → ReadExplicitContextBodyUseCase` 只在每次用户勾选后读取当前路径 Text、当前 Project 指令和 scope 合法的 ACTIVE Memory，生成不持久化的预览 IR。P4-B metadata 入口保持，`memorySources` 未读未写，检索/语义摘要/真实缓存和 Prompt/RunSpec/egress 未实现。详见 `P4D_EXPLICIT_CONTEXT_BODY_SELECTION_CONTRACT.md`。
- P4-M 补齐与正文完全分离的 L3：`LocalActionTraceDomain → ReadExplicitLocalActionTraceUseCase` 只读当前 Conversation 中可证明用户发起的 P3-C 终态 local-fixture `CONTINUE/RETRY/CHANGE_MODEL` 谱系。默认关闭，必须启用并逐项选择；按时间和安全 selector 有界稳定排序。编辑/切分支没有 append-only 动作谱系，明确排除；L3 不进入 P4-D、P4-J/K、Prompt、RunSpec、Export、Harness、Invocation 或生产 Eval。详见 `P4M_L3_LOCAL_ACTION_TRACE_CONTRACT.md`。
- P4-E 完成本地 Knowledge 管理与显式检索选择：`KnowledgeDomain → ManageKnowledgeUseCase → Room` 管编辑 revision、标签、GLOBAL/PROJECT、归档/回收站；标题/正文/标签/来源检索为有界稳定投影。Context 必须在用户搜索、逐项选择后以 revision/hash 与 P4-B metadata 重验，选择不持久化。详见 `P4E_LOCAL_KNOWLEDGE_RETRIEVAL_CONTRACT.md`。
- P4-F 完成只读重复候选；P4-G 在其后独立落地人工关系：`KnowledgeRelationshipDomain → ManageKnowledgeRelationshipsUseCase → Room` 只在用户从两个 ACTIVE 同范围 Knowledge 逐项选择类型并确认后写入。关系端点的方向/对称性、revision/hash、高敏和重复边均由 Domain 统一解释；Schema 11→12 仅追加关系/修订/intent。详见 `P4F_LOCAL_KNOWLEDGE_DEDUPLICATION_CANDIDATES_CONTRACT.md` 与 `P4G_LOCAL_KNOWLEDGE_RELATIONSHIPS_CONTRACT.md`。
- 可见可控的长期记忆。
- Context 分层、压缩、缓存和离线 Eval。
- 判断是否需要 Anthropic 原生 Adapter 承担 OpenRouter 未暴露的 Claude 能力。

## 验证等级

每阶段分别报告：

1. 文档或设计方向；
2. 已实现代码；
3. 领域与入口契约；
4. 构建；
5. 模拟器；
6. 真实 OpenRouter 服务；
7. 真实 Android 设备；
8. 未验证风险。

任何一级不能替代更高一级。

本文件在总图中的对应关系：阶段 0 属于 P0；阶段 1 属于 P1；阶段 2–4 属于 P2；阶段 5 属于 P3，并在后续按总图 P4–P11 继续展开。
