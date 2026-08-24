# 南枫 AI 决策日志

## 决策：Android 会话界面只保留一个现行合同（2026-08-24）

- 当前选择：[Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 是 Android `ConversationWorkspace` 的唯一视觉和交互正文，覆盖顶栏分流、材质、左栏会话行、右滑快捷操作、主屏开侧栏、Composer、模型选择面、文本选择菜单及消息页脚。
- 固定边界：`CHAT_FIRST_INTENT_ORGANIZATION_CONTRACT.md` 继续定义信息架构，`P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_CONTRACT.md` 继续定义消息/附件语义，`P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md` 继续定义 Auto/手动选择和路由，`P2D_MODEL_SETTINGS_CONTRACT.md` 继续定义设置凭据；它们不得以历史 Android UI 数值覆盖当前合同。
- 不采用方案：不在多个阶段合同中同步维护尺寸、颜色、滑动和外点行为；不以截图或安装记录替代明确合同；不把视觉合同扩展为 Provider、凭据或真实外发成功声明。

## 决策：实时检索的实际接收方必须独立于回答模型持久化（2026-08-24）

- 当前选择：复杂推理的 OpenRouter 模型通过 OpenRouter `openrouter:web_search` 发送，千问模型通过其 Chat Completions `enable_search` 发送；DeepSeek V4 Pro 的实时检索使用千问官方 Responses 的 `web_search`，因此逻辑回答模型为 DeepSeek、实际接收服务商为千问。
- 固定边界：`NormalChatSendAttempt.egressProviderId`、调用诊断和助手归属记录保存实际网络接收方；DeepSeek→千问恢复只能继续原 receiver 和 idempotency key。界面、错误提示和助手尾部必须明确“DeepSeek 回答 · 千问官方实时检索”，不得伪称 DeepSeek 官方 API 已执行检索。
- 不采用方案：不把所有供应商硬塞进一个 Chat Completions 结构；不因当前 Composer 选择变动而改变未知 Attempt 的 receiver；不让非深度普通对话凭系统提示词假装取得实时来源。

## 决策：助手结果尾部只显示消息绑定的实际模型路由（2026-08-24）

- 当前选择：普通发送创建 `NormalChatSendAttempt` 后，以助手消息 ID + Attempt ID 持久化无正文的 `AssistantResponseModelAttribution`。普通流在请求开始前绑定持久助手占位；对比和按原编号重试在创建新的助手消息前绑定原 Attempt。尾部展示持久化的模型显示名与接收服务商，而非当前 Composer、Auto 规则或后来刷新的模型目录。
- 固定边界：该表只含助手消息 ID、Attempt ID、Provider、实际 model ID、显示名和记录时间；不保存请求正文、回复、附件、URL、凭据或原始响应。未具有精确归属的历史/导入消息显示“模型信息未记录”，不能从相邻用户消息、时间或当前设置推断。
- 不采用方案：不以“Auto”“深度”“对比”等逻辑槽位替代真实 model ID；不把调用诊断的最近一条记录套给会话消息；不在消息页新增操作按钮；Desktop 在拥有对应真实发送和消息归属 owner 前不仿制此标签。

## 决策：普通对话、项目工作区与临时对话必须有独立导航投影（2026-08-23）

- `CHAT`、`WORK` 与临时恢复不是同一列表的不同标题：普通和工作会话各按 `ConversationSurfaceRepository` 的持久化 surface 读取并保留各自选中会话；临时对话仅由 `TemporaryConversationDomain` 管理，不展示或写入持久会话列表。
- 工作区的树形入口以既有 `ProjectDomain` 为唯一项目所有者。创建项目、编辑、置顶、归档经 `ManageProjectUseCase`；在项目中创建工作对话时，持久化同一 `projectId + WORK` 会话，不建立平行文件夹或依赖外部目录。
- 借鉴 Codex 的信息架构，不复制其本机资源管理、永久工作树或隐藏访问权限；归档或异常项目归属的既有工作对话必须仍可见，不能因 UI 分类而静默丢失。

## 决策：P6 完整工作区交换先以 v2 字段保真/拒绝 IR 阻止静默降级（已确定）

- 当前选择：`nfai.exchange.v1` 继续只用于既有语义投影；P6 完整对象必须先经过 v2 的逐字段矩阵与共享 canonical semantic-hash IR。Project appearance/instruction history、Conversation settings/memory sources、Knowledge source/provenance/history/attachment metadata、Memory title/source/concept/history 及 Relationship scope/project/time/history 是 v2 的必保留事实。
- 固定安全边界：v2 不接受 path、URI、picker token、`sourceReference`、credential、Provider raw payload、runtime、diagnostic 或 route preference。任何 owner history/依赖/内容寻址附件无法验证，或 Desktop 无法原子持久化，均拒绝整包；不从 v1、默认值或文件名推断丢失事实。
- 不采用方案：不把 v2 schema/golden 当作 Android mapper、Desktop SQLite import、SAF、备份或真实跨端验收；在 Android/desktop完整 mapper、asset archive、transaction/journal、重开和回导合同闭合前，不新增完整工作区入口或功能审阅条目。
- 重新评估触发条件：Android 只读 mapper 可以产生 exact v2 IR，Desktop 以版本化 staging/asset archive/transaction/journal 导入并完成中断回滚和真实回导后，才评估设置二级入口及双端功能审阅。

## 决策：Android v2 恢复只允许设置 OpenDocument 的单文件、空本机路径（2026-08-23）

- 采用：`设置 → 数据与导入 → 完整工作区交换（v2）` 内的 OpenDocument 只消费用户明确选择的一个 URI，受控 bridge 有界读取 bytes 后直达 strict reader → atomic restore owner。UI 不读取流、不解析 ZIP/JSON、不持久化 URI/path/name 或包正文；UI state 只含 content-free outcome、semantic hash 前缀和匿名计数。
- 采用：以 exact package SHA-256 派生 opaque intent ID，使用户重新选择同一 byte package 时才可能回收同一未发布 journal；非空本机、不同 intent/package 或任何未知 journal 不覆盖、不合并、不删除，按拒绝或 `RECOVERY_REQUIRED` 停止。
- 不采用：聊天/Composer/工作页入口、目录扫描、多文件选择、SAF/UI 自行解析、用新 intent 绕过同包 journal、将 Android 恢复描述成备份或云同步。
- 配套修正：chat-first 首次启动不得自动写入空“新对话”，否则用户正常打开设置前本机已非空而恢复入口不可用。保留抽屉中的显式新对话动作，不新增常驻按键。
- 配套修正：v2 不携带未发送草稿，但 atomic restore 必须为每个已恢复 Conversation 写入空、无附件的本机 draft 占位并在 typed readback 验证。否则恢复虽可提交，却会使既有严格 writer 因缺少 snapshot 必需记录而拒绝重新映射；这不是放宽 v2 的草稿可移植范围，也不新增入口。

## 决策：用户操作直接执行，安全约束不以二次确认表达（已确定）

- 当前选择：用户在应用内选择导入或执行即直接运行；ChatGPT ZIP 在严格预检/解析后自动逐项原子导入，普通聊天与 Compare 不再要求应用内二次确认。
- 固定边界：保留系统权限、私有 staging、严格格式/大小校验、最小必要外发、凭据不曝光、费用/错误可见、幂等/回执/冲突和 Agent 外部 authority；这不授权静默外部副作用或绕过平台权限。
- 影响：旧 `AWAITING_CONFIRMATION` 仅可作为内部短暂处理状态，不能成为用户产品闸门；历史确认 UI/合同须逐步迁移为直接执行状态与可读结果。

## 决策：第三方 ChatGPT／Claude ZIP 只按已登记版本导入，资料与偏好仅经唯一安全 owner 写入（已确定）

- 当前选择：P6-K 是当前最高优先级。用户在系统 picker 选择 ZIP 即直接运行严格预检、候选映射与每项原子提交；没有应用内逐项确认。ChatGPT 根或实包编号 `conversations*.json` 必须逐 entry 通过 P6-H 严格 parser；Claude 根 `conversations.json` 必须通过 P6-I 严格 parser。2026-08-16 两份授权实包在该既有严格树合同下均得到零候选，故 fail-closed、零 Conversation 写入；需要最小化的结构证据才能扩展各自 parser，不能猜测兼容。
- 已确认事实与依据：P6-H/I/J 都只支持用户自行解压后选择的 JSON；P3 的 Conversation/Attachment/Preview 已能提供私有资产、树和媒体展示，但没有 ZIP entry ownership、富文本 IR 或 profile 映射 transaction。OpenAI 官方资料确认 ZIP 可能含根 `conversations.json`、assets 和 account metadata；Anthropic 官方资料确认其导出含 conversations/user data，但仍不足以作为 Claude ZIP 目录/字段的实现合同。
- 固定安全边界：ZIP 路径、URI、picker token、Key、cookie、密码、支付、组织权限、Provider/模型/同步设置不进入业务表或日志；拒绝 zip-slip、加密/多盘、炸弹、重复规范化路径、未知压缩法/版本和 manifest 不一致。会话、附件、provenance/receipt 只由专属 commit owner 原子新建；不 merge/覆盖已有 Conversation、附件、Key 或设置。K6 只接受 `nfai.third-party-profile-personalization/v1` 的七项白名单；Android/Desktop 各有一个 native profile owner，task 不保存值，owner transaction 负责 canonical value、receipt/provenance、幂等、冲突与批次撤销。
- 不采用方案：不盲目解压；不把 P6-H/I 的 JSON fixture 当 ZIP；不从文件名推断附件归属；不把 ChatGPT 格式套给 Claude；不将用户 profile/personalization 伪装为南枫 AI 账号同步或自动覆盖本地偏好。
- 重新评估触发条件：获得一份用户明确选择、可合法审计的脱敏 ZIP 或官方版本化格式说明后，先验证实际 ChatGPT entry 与媒体 stable ownership，或添加 Claude provider+version+manifest evidence；两者都必须先补 Parser-first 合同，之后才分别实现 Android/Desktop 确认事务与真实闭环。

## 决策：K7 只采纳 message-scope 的直接资产 identity，导出文件目录不是归属（已确定）

- 已确认事实：两份用户已选择的实包以只读匿名统计审计。ChatGPT 有 719 个非 JSON 资产，但其精确 entry-path/SHA-256 值只位于导出清单和逻辑文件目录，7,629 个已解析 mapping message 对象内为 0；Claude 没有非 JSON 资产。
- 当前选择：未出现“解析 message scope 内的精确完整 entry 或 SHA-256 identity”即保持 `UNMAPPED_REJECTED`。不从文件名、逻辑目录、导出清单、文本片段或时间邻近性推断任何消息归属。
- 影响：不写 Android/ Desktop attachment asset、Message attachment block、asset receipt/provenance 或 Preview owner；不改变现有聊天布局。未来只有 versioned provider relation schema 加合成 mapper→owner→receipt/retry/revoke 覆盖，才能重新评估。

## 决策：Compare Android 平台 Key/HTTP 只绑定已确认的精确双分支授权（已确定）

- 当前选择：`AndroidOpenRouterComparePlatformAdapter` 是唯一 Android credential/HTTP 平台边界；它只能由未过期的已确认 ChatGPT+Claude `CompareExecutionGrantedPlan` 构造，作为同一对象注入 `OpenAiCompatibleProviderTransport` 的 opaque credential handle 与 HTTP client。构造不读取 Key、不打开连接；每个 grant 中精确的 provider-facing model 仅可调用一次。
- 已确认事实与依据：Compare owner 的 grants 已绑定 context/text SHA-256、catalog/price/预算与五分钟期限，Schema 33 的 `RoomCompareBranchExecutionPorts` 已能逐 branch 写入安全 receipt 和 Usage reservation/release；旧 P3 test port、普通 Settings preset 和 legacy inference adapter 都不符合动态 Compare deployment/branch binding。
- 固定安全边界：固定 OpenRouter HTTPS POST、受限请求头、timeout 和 response 上限；取消、过期、handle/模型错配、重复、缺 Key 与异常均失败关闭。Key 只在平台 HTTPS 调用栈短暂以 `CharArray` 存在并 finally 清零；不记录文本、响应、Key 或 Authorization。该 adapter 不被 `AppContainer`、Activity、ViewModel 或 UI 注册。
- 不采用方案：不让普通聊天的 `EGRESS_UNREGISTERED` 展示确认充当 Compare 授权；不以 grant/hash 直接恢复原文；不允许批次内重试、任意 model/endpoint/header，或为测试绕过可见确认直接注入 Key/HTTP。
- 重新评估触发条件：不改已确认布局的 Compare 可见确认/执行公开入口通过定向与视觉验收，且用户在当次以非敏感文本明确确认 ChatGPT+Claude、费用/次数上限与外发范围；届时才可注入该 adapter 并做少量真实验证。

## 决策：Compare 原文只以临时租约交给一次批量双分支接收（已确定）

- 当前选择：`CompareExecutionApplicationOwner` 在汇总确认后继续唯一持有 process-memory-only 原文 lease；仅在同一同步临界区将同一 Canonical Context、两已确认 grants 和同一 lease 交给 `CompareBatchDispatchPort`。端口只支持 all-or-nothing batch acceptance，默认 fail-closed。
- 已确认事实与依据：MM-O4-A 的 grants 与 Schema 32 都只含 text SHA-256；hash 无法且不得恢复原文。若确认时就释放原文，后续执行不能在不降低隐私边界的前提下发生；若逐支交付，会产生第一支已接收、第二支不明的不可审计状态。
- 固定安全边界：取消、过期、确认范围变化、Store 拒绝、port 拒绝/异常和 accept 后均释放 lease。Schema 32 复用 append-only runtime event 持久化 content-free dispatch intent/outcome；进程重启的任何非 accepted 状态都是 `retry required`，必须 fresh text + fresh confirmation，绝不 hash 恢复、静默重发或重用旧 grant。
- 不采用方案：不落原文到 Room/log/Usage/receipt/handoff；MM-O4-D 不以 dispatch metadata 为由增加 Schema 33；不把 batch fake 当真实 Provider 执行；不预插 assistant、不改 current leaf、不写 P3-I receipt/Usage 或将 branch partial result 回滚 sibling。
- 重新评估触发条件：独立真实 Provider execution 增量能证明 batch acceptance 与其本地 outbox/branch runtime 事务一致，并获得逐第三方 egress、credential 与实际结果事实的授权。

## 决策：Compare OpenRouter 执行复用 P3 coordinator，不伪装为普通 preset（已确定）

- 当前选择：`OpenRouterCompareBatchDispatchAdapter` 是唯一 adapter readiness 接合层。它使用 `CompareBatchDispatch` 的两支 Store identity、同一临时文本和 grant 中的实际 provider-facing model ID，经同一 `RealTextExecutionCoordinator` 复用 `OpenAiCompatibleProviderTransport`；不接 legacy OpenRouter 推理路径。
- 已确认事实与依据：P3 `RealTextExecutionReadyPlan.presetId` 是普通 Settings/Registry 的 preset 事实，Compare deployment 来自当前 grant/registry，强行映射会把动态 target 写成错误的设置语义。新增 mutually-exclusive content-free `verifiedCompareDeployment` target 后，旧 P3 preflight 与 current-leaf 前提不被改义。
- 固定安全边界：默认 credential handle、HTTP client、receipt port 与 Usage port 均 disabled；adapter 不读取/解密 Key、不注册生产 composition、不产生假 receipt/Usage。batch accepted 只表示两个 coordinator execution 已接管；终态按 branch 独立，partial failure/cancel 不回滚 sibling。
- 不采用方案：不新增第二套 HTTP/JSON/error mapping/Usage/receipt owner；不注册 `RoomRealTextExecutionCoordinatorTestPortAdapter`；不升级 Schema、不预插 assistant、不改 shared current leaf；不以测试内容或历史消息作为真实外发资料。
- 重新评估触发条件：用户在应用内完成当次可见确认并提供授权的非敏感资料，同时已有 production-ready 逐 branch receipt/Usage 事务实现和受审查 credential/HTTP platform adapter。

## 决策：Compare receipt 只以独立 Schema 33 分支事实落库（已确定）

- 当前选择：`RoomCompareBranchExecutionPorts` 只对已存在的 Schema 32 Compare branch 追加 `compare_branch_execution_receipts`，并复用既有 immutable Usage Ledger 保存预算预留/释放。它不把两个 branch 硬塞进 P3 每 conversation 唯一 runtime/receipt，也不创造第二个 HTTP 或账本协议。
- 已确认事实与依据：协调器需要可重放的 `PREPARED/RUNNING/terminal` receipt，而 Schema 32 branch projection只有 RESERVED/终态，不能可靠表达运行态；P3 test port 同时依赖普通 conversation execution record，不能作为生产 Compare adapter。
- 固定安全边界：receipt 先逐项核对 branch 的 execution/invocation/attempt/请求指纹、会话、父 user 节点与 provider-facing model；partial delta 只在调用栈中存在。Schema 33 不含文本、响应、Key、HTTP 元数据或 MessageNode mutation。AppContainer 只注册 migration，仍不构造 Store/ports/adapter。
- 不采用方案：不复用 `RoomRealTextExecutionCoordinatorTestPortAdapter`，不更新 P3 receipt、`conversation_runtime_states`、`Conversation.currentLeafMessageId` 或 message tree；不因有 Room port 而宣称已读取 Key、可联网或真实执行。
- 重新评估触发条件：独立受审查的 Android credential/HTTP adapter 可在当次可见确认、用户授权文本和明确 egress policy 下被注入；此前保持 disabled/default unregistered。

## 决策：Direct production composition 只投影已验证 OpenRouter 目录（已确定）

- 当前选择：`OpenRouterVerifiedMultiProviderRegistryProjection` 是 `VersionedModelRegistry` 到多供应商 Logical Model/Deployment Registry 的唯一只读生产投影。它只接受当前 `OPENROUTER_CATALOG + VERIFIED + SHA-256 + 非 fallback` 快照，且 ChatGPT/Claude 仅由显式 preset 映射识别。
- 已确认事实与依据：P3-C fixture 是 `MOCK/LOCAL_FIXTURE`，P6-G `curated:*` 是本机显示选择 ID，均不代表可外发的 Provider-facing model ID。现有 AppContainer 有 OpenRouter 持久化目录槽位，但当前未保证已有可投影快照。
- 固定安全边界：provider-facing ID、价格/版本和 catalog version 必须原样取自已验证 snapshot；未知价格、无映射、fallback 映射、未验证或不支持来源拒绝。AppContainer 只构造惰性 owner，不能读取 Key/settings、HTTP、探测 Provider、写 Room/Usage 或注入 UI。
- 不采用方案：不从模型展示名、P6-G、UI、P2-M 合成 fixture 或测试数据推测部署；不在无目录时预置假模型；不把只读 projection 变成目录发布/回滚 owner。
- 重新评估触发条件：真实 P2-J 验证目录可稳定产生映射不足的 snapshot，或新增 Provider Adapter；届时先为该 Provider 增加独立 projection 与实际 target 合同，不能放宽现有 OpenRouter 投影。

## 决策：Direct 与 P3 的输入费用只使用保守预算上界（已确定）

- 当前选择：`ConservativeInputBillingBudget` 是 Direct confirmation 与 P3 reservation 的唯一输入计费 owner。它以 UTF-8 字节数作为保守 token 上界，并以精确安全算术计算最大预算；该数值绝不表述为 Provider tokenizer 结果、实际 token 或实际费用。
- 已确认事实与依据：旧的 `(text.length + 3) / 4` 会将中文等多字节文本折为乐观字符数，可能使 Direct 确认上限低于 P3 预留。共享领域 owner 使两处使用同一预算语义，并在溢出时失败关闭。
- 固定安全边界：Confirmation 只携带完整范围的 SHA-256 fingerprint，不含原文；其绑定 request id、execution/context、逻辑模型、部署、Provider、provider-facing model ID、catalog/price/currency、text SHA-256、输出上限和预算。待确认原文只可停留在有界短期 map，取消、过期、范围变化和确认/preflight 终态必须释放，之后只保留有 TTL/容量上限的 content-free replay 拒绝事实。
- 不采用方案：不猜测各 Provider 的精确 tokenizer，不把本地预算写成 Usage 实绩，不允许未知价格以确认绕过，也不通过 UI、Key、HTTP、Room 或 Usage 改动修复此债务。
- 重新评估触发条件：引入经验证、Provider 明示的预报价语义，或真实 Usage 对账合同需要新增独立事实字段；仍不得回写或改称本地保守上界为实际用量。

## 决策：采纳 Direct／Compare／Auto 同级多供应商模型编排（已确定，产品参数已确认）

- 当前选择：正式采纳《南枫AI_多供应商模型编排架构_v1.0》，以 `MultiModelOrchestrator` 统一产出 `DIRECT / COMPARE / AUTO` 三种同级计划；Compare 和 Direct 不经过 Auto Router，Auto 只在用户未指定目标时工作。
- 已确认事实与依据：用户明确要求优先加入总控并优先落地；现有工程已有 Provider Transport、Model Registry、Conversation Tree、Runtime Receipt 与 Usage Ledger，可增量复用而无需推倒重建。
- 关键推理与权衡：`Compare > Direct > Auto` 先解释为研发与架构优先级，保留当前普通对话未指定模型时的 Auto 默认表面；先做纯领域核心与 OpenRouter 首部署，再按一个 Adapter 一个验收扩充服务商，以避免 UI、网络、凭据和数据库同时变化。
- 固定安全边界：用户精确选择不得被 Router 或 fallback 覆盖；Compare 所有分支使用同一 Canonical Context Snapshot，默认不综合、不自动写入共享上下文；跨 Provider fallback 必须事先明确授权；Key 不进入编排计划、数据库、日志或遥测。
- 不采用方案：不以原方案设置页示意图覆盖现有 UI；不一次建设五家未经真实验收的 Provider；不新建与 `ConversationTreeService` 平行的比较消息树；不以 OpenAI-compatible 为理由合并各 Provider 身份。
- 首批实施：MM-O1 只增加纯领域类型、编排计划和合同测试，不修改 UI、不读 Key、不发 HTTP、不接线当前未验证 P3-J WIP。详细计划见 `MULTI_PROVIDER_MODEL_ORCHESTRATION_ADOPTION_PLAN.md`。
- 用户确认记录（2026-08-16）：`Compare > Direct > Auto` 仅作研发／架构优先级，普通对话未指定目标时继续默认 Auto；Provider 按统一核心、OpenRouter 首部署、原生 Adapter 一个一个验收；Compare MVP 默认且最多同时比较 ChatGPT 与 Claude 两个逻辑模型，并继续采用一次汇总确认、分支独立记账。动态 Provider-facing 实际模型 ID 不写死，由 Deployment Registry 解析。
- 重新评估触发条件：用户要求改变默认入口、MVP Provider 数量或 Compare 上限，或真实 Provider 能力证明 Logical Model/Deployment 映射不足。

## 决策：Compare 以独立 branch runtime 持久化，不复用 P3 的单 current runtime（已确定）

- 当前选择：Schema 32 以 `CompareConversationSessionStore` 为 Compare 持久化唯一 owner。它只追加 session、双 branch identity、独立 runtime/checkpoint、append-only runtime event 和 typed follow-up/adopt/synthesis/terminal intent；每 branch 独立引用 assistant、Invocation、Attempt、未来 execution、cancel 与 usage reservation identity。
- 已确认事实与依据：现有 `conversation_runtime_states` 对 `conversationId` 唯一，且 `ConversationRuntimeStateMachine` 的 start 会切换 shared current leaf，后续事件亦要求该 leaf 仍为当前。因此让 Compare 双 branch 硬走 P3 runtime 会互相覆盖，不能满足部分失败／取消保留 sibling 的合同。
- 固定安全边界：Schema 32 不插入无内容 `message_nodes`，不改 `Conversation.currentLeafMessageId`，不改旧 P3 runtime/index、P3-I receipt 或 Usage Ledger 事实定义；execution/reservation 在 branch 表中只是 future reference，不代表模型已执行或已发生用量。
- 不采用方案：不创建第二棵消息树；不以每 conversation 两条 P3 state 或覆盖 current runtime 的方式实现 Compare；不以计划写入伪造 P3 receipt、Usage reservation、Provider attempt、文本或 HTTP 事实。
- 重新评估触发条件：独立的真实 Compare execution 增量已获得外发授权、content lifecycle 与 P3-I/Usage 逐 branch 事务合同，并能证明 MessageTree mutation 不改变 shared current leaf。

## 决策：P6 反馈采用单一可消费索引，合同与路线各自保留唯一正文（已确定）

- 当前选择：`PRODUCT_FEEDBACK_DECISION_LEDGER.md` 是 P6 用户反馈、覆盖关系、阶段所有者、状态和复评条件的唯一索引；P6-E/P6-F/P6-F2/P6-G 合同拥有规则正文，`MASTER_DEVELOPMENT_BLUEPRINT.md` 只拥有路线。
- 依据与边界：对应 `FB-P6-001` 至 `FB-P6-022`；索引仅为后续复盘/交接准备，不授权复盘 UI、Provider、凭据读取或外部调用。
- 重新评估条件：出现新的跨阶段反馈类别，或静态完整性检查不能覆盖新增索引字段时，先更新索引与检查脚本，再变更实现。

## 决策：最终产品采用本地安全底座与受控联网双路径（已确定）

- 当前选择：模型执行固定区分 `LOCAL_OFFLINE` / `ONLINE_PROVIDER`，数据固定区分 `LOCAL_ONLY` / `ENCRYPTED_SYNC`；两条路径独立配置、独立授权、独立降级。
- 已确认事实与依据：用户已明确离线不是最终产品终态，Provider 与账号同步均为后续必须真实验收的产品路径；当前仍未授权读取 Key 或发真实 HTTP。
- 关键推理与权衡：本地路径必须始终可用，而联网路径必须保留 Key presence、目录、费用、逐次 consent、网络与同步状态，避免“一键联网”自动外发所有本地数据。
- 放弃方案及原因：不继续将 Desktop 表述为纯离线最终产品；不以 fake/loopback、配置页或模拟器替代真实 Provider/同步成功；不在当前阶段接入 Key、HTTP、OAuth 或云端。
- 风险与待验证项：真实 Key/HTTP 小额文本、Provider cancel/retry/费用、Google/Supabase 身份、跨网络同步、OPPO 和 Windows 均未完成。
- 重新评估触发条件：未来真实服务合同要求改变状态语义、凭据存储边界或同步授权范围时，先更新本决策与专属合同。

## 决策：先完成捕获闭环，随后以 Claude 级多模型对话为核心（已确定）

- 当前选择：第一落点采用“快速捕获与知识沉淀”，输入范围确定包含文本、Android 系统分享和图片；完成后立即把开发重心转向“Claude 级多模型对话体验”。
- 已确认事实与依据：用户明确选择捕获与知识沉淀优先，确认最小闭环包含图片，同时说明后续的 Claude 级对话才是自己最需要的能力。
- 关键推理与权衡：捕获闭环能尽早形成南枫 AI 独有的数据资产价值；但它不能演变为长期占用全部开发资源的知识采集工具。首个闭环必须主动收窄，避免延误对话主产品。
- 放弃方案及原因：不选择先完整打磨所有 Claude 级功能，因为会推迟首个可用闭环；不选择两条线同时完整开发，因为会造成范围失控和验证口径分叉。
- 风险与待验证项：图片会增加权限、私有保存、预览、压缩、视觉模型能力判断和第三方外发确认，但仍属于可控范围；文件、语音、视频和网页解析不得同时扩入首个闭环。
- 重新评估触发条件：最小捕获闭环不能产生可保存、可追溯的知识资产，或者真实使用证明用户只需要对话、不需要知识沉淀。

## 决策：Android 首版使用 Kotlin 与 Jetpack Compose（已确定）

- 当前选择：Android 首版采用原生 Kotlin + Jetpack Compose。
- 已确认事实与依据：用户在原生 Android、React Native、Web/PWA 三个方向中明确选择原生 Android。
- 关键推理与权衡：原生方案更适合系统分享、文件、语音、本地数据库、后台任务、Android Keystore 和真实设备体验；桌面端以后复用领域协议，不强求复用 UI 代码。
- 放弃方案及原因：React Native + Expo 不作为首版技术栈；Web/PWA 不作为移动端先行方案。
- 风险与待验证项：未来 Desktop / Web 无法直接复用 Compose UI，需要依靠稳定领域合同、数据协议和 Provider/Harness 接口控制重复实现。
- 重新评估触发条件：Android 原生无法满足目标平台，或未来明确要求 iOS 与 Android 同期开发并证明跨端收益高于迁移成本。

## 决策：V1 本地单用户、Provider 直连（已确定）

- 当前选择：V1 采用本地单用户、本地业务数据、本机安全存储 API Key、应用直连 Provider；只预留账号、云同步、远程 BFF 和 AI Hub 的接口边界。
- 已确认事实与依据：用户明确选择 V1 排除账号、云同步和远程 AI Hub。
- 关键推理与权衡：先验证核心产品价值，避免服务器、账号、同步、运维和云成本拖慢移动端闭环；同时保持未来扩展路径。
- 放弃方案及原因：V1 不同时建设可选 AI Hub，也不加入账号、跨设备同步或远程 BFF。
- 风险与待验证项：Provider 直连要求严格保护本地密钥，并处理各 Provider 的网络、限流和错误差异；未来同步必须设计可迁移协议，不能重新定义本地真值。
- 重新评估触发条件：移动端核心闭环和 Claude 级对话达到验收后，出现明确的跨设备同步、统一路由、集中成本或多应用协同需求。

## 决策：OpenRouter 作为首个真实 Provider，优先测试 Claude（已确定）

- 当前选择：V1 首个真实 Provider 使用 OpenRouter，第一批真实文本与图片调用优先测试 Claude 模型。
- 已确认事实与依据：用户明确指定 OpenRouter 优先，并指定 Claude 为第一测试模型族。
- 关键推理与权衡：OpenRouter 可以先验证统一模型入口、Claude 文本与视觉能力、用量和费用链路，减少首个闭环同时接入多个官方账户的成本；业务层仍通过 Provider Adapter，不直接依赖 OpenRouter 请求格式。
- 放弃方案及原因：V1 不同时接入多家真实 Provider；不把某个 Claude Model ID 写死在业务代码中。
- 风险与待验证项：开发时必须从 OpenRouter 当前目录确认可用 Claude 型号、视觉能力、价格、上下文与路由状态；OpenRouter 兼容调用不能替代未来对 Anthropic 原生能力的独立验证。
- 重新评估触发条件：OpenRouter 无法稳定提供目标 Claude 模型、图片能力、用量信息或错误语义，或者 Claude 级对话需要 OpenRouter 未暴露的 Anthropic 原生能力。

## 决策：完整总方案先于任何代码实现（已确定）

- 当前选择：在创建 Android 工程或业务代码前，先完成覆盖整个南枫 AI 生命周期的总控开发方案；现有 V1 实施计划只能作为总方案中的近期子计划。
- 已确认事实与依据：用户明确要求先固定整体方向与完整执行顺序，防止后续遗忘步骤、局部开发破坏总体架构或遗漏长期能力。
- 关键推理与权衡：总方案必须同时锁定产品阶段、架构演进、数据协议、平台路线、Provider/Harness、知识与记忆、Agent、生态整合、AI Hub、测试、真机、发布和复评门槛；每个阶段只实现当前增量，但不得失去后续兼容路径。
- 放弃方案及原因：不直接从 V1 工程骨架开工；不把短期 Backlog 当作完整路线；不为追求一次性完整而同时开发所有阶段。
- 风险与待验证项：总方案如果只列功能清单、没有依赖和停止条件，仍会失去方向控制作用；需要建立可检查的阶段门、所有权表、数据演进和遗漏检查表。
- 重新评估触发条件：产品定位、首发平台、本地优先原则或 AI Hub 边界发生用户明确变更时，先更新总方案和决策日志，再调整实施顺序。

## 决策：Android 本地真值与跨端复用方式（已确认）

- 当前选择：Android 使用 Room/SQLite 保存结构化业务真值，附件二进制放在 App 私有文件目录；未来 Desktop 优先复用领域语义、版本化导入导出协议和合同测试，不把共享 UI、共享数据库文件或共享运行时作为 V1 前置。
- 已确认事实与依据：用户已选择 Kotlin + Jetpack Compose、本地单用户和移动端先行，同时要求整体方案不能因局部实现破坏长期跨端方向。
- 关键推理与权衡：Room、私有文件和版本化协议能先满足 Android 离线、迁移和生命周期要求，也能避免为未来 Desktop 过早引入云 Core 或跨端框架；代价是 Desktop 可能需要独立实现客户端数据层。
- 放弃方案及原因：不让 Desktop 直接打开 Android 数据库文件；不为代码复用优先改回 React Native；不在 V1 建立强制远程 Core。
- 风险与待验证项：具体 Schema、数据库静态加密范围、附件清理、协议字段和迁移性能需在 P1/P2 评审与实测。
- 重新评估触发条件：出现明确的多端同步需求，或行为合同不足以避免 Android/Desktop 语义分叉。
- 确认记录：用户于 2026-08-12 确认总控开发蓝图，本决策随总图转为已确认基线。

## 决策：Desktop、同步、Agent、生态与 AI Hub 的长期顺序（已确认）

- 当前选择：Claude 级对话、项目/记忆/知识和 Android 产品化完成后，依次建设 Desktop 控制中心、可选端到端加密同步、受控 Agent、南枫生态；只有至少两个应用稳定使用 Provider Framework 且集中治理收益有证据时，才建设可选 AI Hub。
- 已确认事实与依据：用户要求先固定完整方向；原始资料同时要求移动优先、桌面深度工作、本地优先、Agent 演进和 Hub 可选。
- 关键推理与权衡：顺序先证明个人核心价值和本地协议，再承担跨端、云端、自动化和集中运维复杂度；P7 同步允许在真实多设备需求出现时与 Desktop 中后段协调，但不能改变离线本地模式。
- 放弃方案及原因：不在 V1 同时建设 Desktop、账号、同步、Agent 和 Hub；不让 Hub 成为南枫应用的业务数据库或启动依赖。
- 风险与待验证项：Desktop 技术栈、真实跨设备频率、服务器预算、生态应用优先级和 Hub 触发时点必须在各阶段重新核验。
- 重新评估触发条件：用户明确改变阶段优先级，或真实使用、外部服务和维护成本证明当前顺序不合理。
- 确认记录：用户于 2026-08-12 确认总控开发蓝图，本顺序随总图转为已确认基线。

## 决策：P2-A 采用 Room 初始 Schema、私有内容寻址附件与领域导出快照（已确定）

- 当前选择：结构化 Capture Draft、Evidence、Attachment 元数据与 Knowledge Item 由 Room Schema 1 保存；图片二进制先导入 `filesDir/attachments/v1`，以 SHA-256 内容名、MIME、大小与逻辑存储键关联；导出使用版本化领域快照，不暴露 Android 绝对路径。
- 已确认事实与依据：产品基线要求 Android 本地数据库为业务真值、附件进入 App 私有目录、导出可供未来 Desktop 精确保真；P2-A 合同已明确外部 URI 不能成为业务真值。
- 关键推理与权衡：Room 的关系表与导出 Schema 可保留来源、顺序、稳定 ID 和调用溯源；内容寻址副本避免外部临时 URI 失效且便于完整性校验。代价是后续需独立实现垃圾回收、回收站、Migration 和真实导出包，不能在首次写库时偷偷删除或迁移数据。
- 放弃方案及原因：不把图片 BLOB 塞入业务表；不将 `content://`、`file://` 或绝对路径保存到 Room/导出；不以 UI 展示文本作为导出真值；不引入 Room KAPT，因为 AGP Built-in Kotlin 不兼容。
- 技术例外：KSP 2.2.10-2.0.2 目前需设置 `android.disallowKotlinSourceSets=false` 才能和 AGP Built-in Kotlin 协作生成源码；它是明确记录的临时兼容开关，不是退回 Kotlin Android 插件或 KAPT 的授权。
- 风险与待验证项：真实相册/系统分享输入、临时 URI 权限、私有附件清理、Schema 升级、真实导出文件、设备重启及真机恢复尚未验证。
- 重新评估触发条件：升级 AGP、KSP 或 Room；新增删除/回收站、导入恢复、真实导出包或非图片附件；或发现当前领域合同无法支持跨端回读。

## 决策：GitHub 成熟项目采用能力级参考而非产品 Fork（已确定）

- 当前选择：公开 GitHub 项目只按能力吸收成熟状态模型、接口、失败恢复、测试和交互经验；南枫 AI 不 Fork 任一现成 AI 工作台，也不在 P1 引入这些项目的运行时依赖。
- 已确认事实与依据：2026-08-12 研究了 RikkaHub、PocketPal AI、LibreChat、Memos、AnythingLLM、LiteLLM、Langfuse、Promptfoo、Mem0、LangGraph、OpenHands Agent SDK、AppFlowy 等项目；没有单一项目同时满足原生 Android、本地业务真值、逐次外发确认、知识/记忆、加密快照、受控 Agent 和南枫生态边界。
- 关键推理与权衡：Fork Web/Server 产品会带入多用户、云端、插件执行和运维复杂度；Fork AGPL 或自定义许可证项目还会扩大分发约束。自主领域合同可保留南枫 AI 的产品顺序，同时复用成熟项目已经验证的消息树、类型化流、文档队列、调用树、Memory CRUD、Eval、Checkpoint 和风险确认思想。
- 放弃方案及原因：不以 Star 数决定技术选型；不把 OpenAI 兼容层当统一业务模型；不复制 AGPL、社区许可证、修改版许可证或企业目录源码；不把 LiteLLM/Langfuse 服务设为 V1 前置。
- 风险与待验证项：文档中的经验仍需在 Kotlin/Android、本地数据和目标真机上重新实现并验证；未来新增具体依赖时必须核验当时许可证、版本、遥测、传递依赖和维护状态。
- 重新评估触发条件：未来决定直接嵌入、分发、修改或托管某个外部项目，或许可证/商业模式发生变化时，先完成文件级法律与供应链评审。
- 证据：`GITHUB_MATURE_PROJECT_REFERENCE_RESEARCH.md` 与 `MASTER_DEVELOPMENT_BLUEPRINT.md` 3.4。

## 决策：P2-B 使用 Photo Picker 后立即私有复制（已确定）

- 当前选择：相册图片只通过 Android Photo Picker 单选入口进入；不申请整库媒体权限。系统回调 URI 只用于本次打开，随后立即流式复制到 App 私有附件目录并写入 Room 草稿。
- 已确认事实与依据：Android 官方 Photo Picker 支持单图选择且无需整库运行时权限；项目既有合同要求外部 URI 不成为长期真值，Activity 重建后必须从 Room 与私有附件恢复。
- 关键推理与权衡：立即复制可避免临时 grant、设备重启或来源应用变化导致草稿失效；代价是占用 App 私有空间，因此设置 20 MB 单图上限，并把删除、回收站与垃圾回收留给独立高风险生命周期设计。
- 放弃方案及原因：不保存 `content://` 到 Room；不为一次选图申请 `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE`；不把页面预览成功当成草稿保存成功；不在 P2-B 同时接拍照或系统图片分享。
- 风险与待验证项：Photo Picker 回退实现、OEM 相册、真实 HEIC/WebP、大图内存、Activity/进程重建与 OPPO 视觉仍需模拟器和真机验证。
- 重新评估触发条件：需要多选、后台长期上传、非图片附件，或私有空间占用与清理策略进入实现阶段。

## 决策：首个可安装版本即固定正式签名（已确定）

- 当前选择：南枫 AI 从 `versionCode=2` 起固定独立正式证书，Debug 开发验收主 APK 与 Release APK 使用同一证书，后续只允许同签名覆盖升级。
- 已确认事实与依据：用户明确把“所有开发软件提前正式签名”设为跨项目标准，目的是避免默认 debug 证书安装后补签名造成覆盖失败、卸载和数据迁移风险。
- 关键推理与权衡：每 App 独立 keystore 可隔离泄漏风险；仓库外 keystore、系统钥匙串口令、备份和证书指纹共同保证恢复性。代价是本机构建依赖受控签名资产，因此 Gradle 对所有可安装构建启用 fail-closed 门禁。
- 放弃方案及原因：不等 Release 阶段才生成证书；不把 keystore/口令提交仓库；不复用 Android 默认 debug 证书；不在真实设备遇到签名不一致时卸载或清数据绕过。
- 风险与待验证项：OPPO 尚未安装；正式设备同签名覆盖和数据保留仍需单独验收。keystore 灾难恢复需要定期核对备份与证书指纹。
- 重新评估触发条件：证书轮换、Google Play App Signing、包名变更、跨平台 CI 或密钥托管策略变化。

## 决策：P2-F 以追加式 Room Ledger 保存安全 Invocation 事实（已确定）

- 当前选择：Invocation 的唯一持久化所有者为 `InvocationRepository`；`RunAiTaskUseCase` 写入 `InvocationRecord → TaskRun → ProviderAttempt → Generation → Validation`。Schema 2 用单一事务写完整层级，并以稳定 ID 做同内容幂等。
- 已确认事实与依据：总蓝图要求调用历史保留实际 Provider、Model、Harness、价格、Token、费用、错误和终态，同时禁止 Key、完整 Prompt/响应、原图和附件正文进入本地账本。P2-E 已有四层领域合同，P2-F 负责将其持久化和可见化。
- 关键推理与权衡：把账本从 Capture、Knowledge、Provider Adapter 和 UI 分离，才能让进程重建、失败诊断、成本读取和未来重试共享一套事实；代价是新增 Schema 2 与显式 Migration，但避免以清库或 UI 记录替代运行历史。
- 放弃方案及原因：不把完整请求/响应存入 Room；不让 UI 直写 DAO；不把确认门禁伪造成 Provider Attempt；不以未知 Token/费用的零值掩盖缺失；本阶段不提供删除入口，避免未经授权的历史清除。
- 风险与待验证项：真实 Provider 多 Attempt/重试、真实取消、实际 Token/费用与长期保留/用户删除策略均未验证；真实服务必须另获联网、Key 和非敏感资料授权。
- 重新评估触发条件：引入流式运行、重试/Fallback、多 Provider、用户可见删除/导出或任一真实服务字段无法映射到当前安全合同。
# 2026-08-13 — ADR-002 P6-A Desktop 与交换协议

- 选择 Tauri 2 进入 P6-B；PWA 只作能力受限 fallback。依据、官方核验来源、spike 证据、复审条件与 Windows 债务见 `ADR-002-P6A_DESKTOP_AND_EXCHANGE.md`。
- `nfai.exchange.v1` 是独立的版本化语义迁移包，不是 P5-D backup/Room DB；权威 schema、canonical hash、预检/敏感/升级规则见 `NFAI_EXCHANGE_V1_CONTRACT.md`。
