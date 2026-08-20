# 南枫 AI 多供应商模型编排方案采纳与落地计划

> 来源：`/Users/nanzhufeng/Downloads/南枫AI_多供应商模型编排架构_v1.0(1).md`  
> 状态：已纳入总控，高优先级实施  
> 采纳日期：2026-08-15  
> 适用范围：Android、Desktop、Provider Framework、模型目录、对话分支、调用记录与费用治理

> **最高优先级覆盖（2026-08-16）**：在继续 Compare／Direct／Auto 的功能扩展前，先完成 P6-K 第三方 ChatGPT／Claude ZIP 导入。Android emulator/OPPO 现暂停；Desktop 已从用户选择的两份真实 ZIP 完成会话提交与重开回读，K6 profile 则只由合成 versioned fixture 验证 owner transaction/retry/revoke；两实包都是 `NO_SAFE_PROFILE_FIELDS / 0`，没有 profile 写入。K7 的匿名只读 audit 还确认没有稳定 message↔asset ownership：ChatGPT 资产仅被导出目录列举、未进入 message scope，Claude 无媒体；两端维持 `UNMAPPED_REJECTED`，不新增附件/预览链。该工作不读 Key、不发 HTTP。权威合同、入口矩阵和分期见 `P6K_CHATGPT_CLAUDE_ZIP_IMPORT_ADOPTION_CONTRACT.md`；未知 ZIP 版本必须拒绝，不能借 P6-H/I JSON Adapter 伪称支持。

## 1. 采纳结论

原方案的产品方向成立，并正式成为“Claude 级多模型对话”阶段的高优先级专项方案。南枫 AI 的模型执行固定为三个同级模式：

- `DIRECT`：用户明确选定服务商部署与模型时，严格按所选目标执行；自动路由不得介入，也不得静默换模型或换服务商。
- `COMPARE`：同一份规范化上下文快照并行交给多个明确目标，各分支独立生成、取消、失败、记账和追问；默认不综合，也不把比较结果自动写入共享上下文。
- `AUTO`：仅在用户没有指定目标且允许自动选择时，根据能力、质量、成本、延迟和可用性产生可解释路由计划。

`COMPARE > DIRECT > AUTO` 作为架构建设与开发顺序，不自动改变当前已确认的普通对话默认 Auto 表面。任何默认入口调整都属于 UI／产品决策，必须另行确认和验收。

## 2. 固定不变量

1. 三种模式是同级入口；Auto Router 不能成为 Direct 或 Compare 的上层总入口。
2. 用户明确选择高于自动判断；Direct 不允许路由器覆盖目标。
3. Compare 首版即依赖 Conversation Tree，不建设与现有消息树平行的第二套分支系统。
4. Compare 的所有分支共享同一个 `CanonicalContextSnapshot`，避免不同模型获得不同历史或附件范围。
5. Compare 输出默认停留在各自分支；只有用户明确采用某一回答或明确请求综合后，才可进入后续共享上下文。
6. 综合回答是一个新的、可追踪的 Invocation，不得伪装为原始比较结果。
7. 逻辑模型、部署和服务商端点必须分离；同一逻辑模型可以有多个部署，但每次实际收件人必须明确。
8. 每个 Provider 保持独立 Adapter、凭据、错误映射和能力证明；“OpenAI-compatible”只允许复用受审查的协议核心，不允许合并业务身份。
9. 任何跨 Provider fallback 都会改变第三方收件人，因此必须事先显式授权；Direct 失败不得静默转交。
10. 安全拒绝不得通过换模型、换 Provider 或自动重试绕过。
11. 一次 Compare 可以使用一个汇总确认面披露所有目标、内容类别和总预算，但每个分支分别保存 Attempt、Usage、Cost、Cancel 和 Error 事实。
12. Canonical Conversation 保持本地真值，不依赖任何 Provider conversation ID。
13. Key 不进入业务数据库、日志、遥测、导出、截图或编排计划；编排层只接触 credential presence／handle。
14. 遥测默认仅保存 app-private 安全元数据；远程遥测必须另行 opt-in。

## 3. 对原方案的项目化适配

| 原方案方向 | 南枫 AI 采纳方式 | 原因 |
|---|---|---|
| Compare 优先 | 作为首个新增产品能力；先落纯领域编排，再接现有对话树和受控外发 | 保留用户价值，同时避免 UI 与网络一起大改 |
| Direct 多 Provider | 先完成统一协议与 OpenRouter 首部署，再按一个 Adapter 一个验收增加原生 Provider | 不能用下拉选项冒充真实接入 |
| Auto Router | 保留现有 P6-G 作为候选路由能力，只允许由 `AUTO` 模式调用 | 防止 Router 截获 Direct/Compare |
| Conversation Tree | 复用既有 `ConversationTreeService` 与消息谱系 | 避免平行真值和数据迁移风险 |
| Provider Interface | 拆为生成传输、模型/能力目录、成本估算、健康观察四类责任 | 避免巨型接口和后台隐式联网 |
| Health Check | 默认采用显式检查与失败事实推导，不做后台定时带 Key 探测 | 符合本地优先和凭据边界 |
| 成本 | 以 Provider 原币种 micros + price version 保存，界面按区域展示 | 不把人民币或当前价格写成永久真值 |
| Desktop 凭据 | macOS 使用 Keychain；未来 Windows 才使用 Credential Manager | 适配当前交付平台 |
| Provider 设置示意 | 只采纳信息语义，不复制原文页面布局 | 已确认界面布局继续冻结 |

## 4. 复用与新增所有权

| 能力 | 现有可复用基座 | 新增唯一所有者 |
|---|---|---|
| Provider 流式传输 | `ProviderTransportBoundary`、OpenAI-compatible core | 各 Provider Generation Adapter |
| 模型目录 | `VersionedModelRegistry`、`NanfengModelServiceCatalog` | Logical Model / Deployment Registry |
| 自动选择 | `P6GModelRouter` | `AUTO` 的 `AutoRoutingPort` |
| 对话分支 | `ConversationTreeService`、Message Node lineage | Compare Session / Compare Branch 投影 |
| 运行事实 | `ConversationRealTextExecution`、Runtime Receipt | 每分支 Invocation/Attempt owner |
| 用量费用 | Usage Ledger | Compare 汇总只读投影，不另建费用真值 |
| 三模式编排 | 尚无 | `MultiModelOrchestrator` |
| 公平上下文 | P4 Context IR 基座 | `CanonicalContextSnapshot` owner |

## 5. 分阶段落地顺序

### MM-O1：编排领域核心（当前首批）

- 建立 `DIRECT / COMPARE / AUTO` 类型化请求与计划。
- 建立 Logical Model、Deployment、Provider Handle 和 Canonical Context Snapshot 的稳定标识。
- 证明 Direct/Compare 不调用 Auto Router；只有 Auto 可以调用。
- Compare 默认且最多 2 个唯一逻辑模型目标、同一上下文快照、独立稳定分支、无默认综合、无自动共享上下文采用；首组为 ChatGPT 与 Claude，具体 Deployment 仍由 Registry 解析。
- 只生成纯计划：不读 Key、不发 HTTP、不写数据库、不修改 UI。

退出门：已通过。`MultiModelOrchestrationContractsTest` 为 **5 tests / 0 failures / 0 errors / 0 skipped**；现有 P3-J 未验证 WIP 文件未由本增量修改。

### MM-O2：Registry 与部署分离

- 将当前“全部经 OpenRouter 的 curated model”迁移为 Logical Model 与 Deployment 显式映射。
- 历史 Invocation 保留实际 Provider、实际模型 ID、部署 ID、目录版本和价格版本。
- OpenRouter 仍是首个真实部署；不因架构升级一次接入所有厂商。

退出门：目录升级/回滚、未知价格、失效部署、历史 readback 合同通过。

纯领域／Registry 增量已于 2026-08-15 完成：`MultiProviderModelRegistry.kt` 以 OpenRouter 为首个且唯一 Adapter 身份，显式分开 Logical Model、Deployment、Provider 及 provider-facing model ID；支持单个稳定上版的显式回滚，未知价格或失效／错配部署失败关闭，并以 content-free `HistoricalDeploymentReference` 固定实际收件人与目录／价格版本。证据见 `MM_O2_MULTI_PROVIDER_REGISTRY_EVIDENCE.md`。本增量未接线 Transport、P6-G Router、Runtime、Usage Ledger、UI 或数据库，故不构成真实 Provider 或 UI 闭环。

### MM-O3：Direct 生产闭环

- 由正常对话的显式目标选择进入 Direct。
- 复用逐次 egress consent、credential presence、预算与 P3 runtime。
- 目标不可用时明确失败，不调用 Auto，不静默 fallback。

退出门：用户可见确认、单次真实非敏感文本、停止/失败/重启/Usage 证据齐全。真实调用仍需当次用户授权与已配置 Key。

#### MM-O3-A：Direct 应用边界（2026-08-16，已完成，非真实闭环）

- `DirectExecutionApplicationOwner` 只接受显式 Direct target，先复用 `MultiModelOrchestrator` 和
  `MultiProviderModelRegistry` 解析精确 Deployment，再冻结 request/context hash、实际 Provider/model、
  text-only 类别、预算与 5 分钟一次性确认。
- 确认后才复用 P3 `RealTextExecutionPreflightOrchestrator` 的 credential presence 与预算/registry
  复核，并交给默认拒绝的 `RealTextExecutionCoordinator`；未在 AppContainer 注册 Direct owner、credential
  broker、transport、runtime receipt 或 Usage reservation。
- Direct 及全部失败路径不调用 P6-G Auto Router，不 fallback；附件、未知价格、失效部署、未勾选、取消、
  过期、目录变化和缺凭据均失败关闭。P3-J 的 Composer 同时本地提交/弹不可确认模态冲突已做最小行为隔离，
  未改变可见布局。

#### MM-O3-A2：Direct 生产前安全收敛（2026-08-16，已完成，非真实闭环）

- `ConservativeInputBillingBudget` 是 Direct confirmation 和 P3 reservation 唯一可用的保守输入计费上界；它按 UTF-8 字节计算，覆盖 CJK、空白、emoji 与安全算术溢出。它不是 Provider tokenizer 或实际 token/费用事实。
- Confirmation 以不含原文的 SHA-256 scope fingerprint 绑定 request id、execution fingerprint、context hash、logical model、deployment、provider、provider-facing model ID、catalog/price/currency、text SHA-256、输出上限和最大预算；P3 reservation 使用相同上界，因此确认最大预算不低于预留。
- `issued` 只在待确认期临时持有请求原文；取消、过期、范围变化和 confirm/preflight 终态立即释放。随后只保存 TTL/容量受限、content-free 的 replay 拒绝事实。未知价格从 Registry 到 Direct 统一为 `PRICE_UNAVAILABLE`。
- 本次没有接线 UI、AppContainer、Key、HTTP、Room、Usage、真实 Provider、设备或 P3-J WIP；自动合同和 Debug 编译不构成 MM-O3 真实服务退出门。

#### MM-O3-B：Production Composition Readiness（2026-08-16，已完成，非真实闭环）

- `OpenRouterVerifiedMultiProviderRegistryProjection` 只消费当前已验证的 OpenRouter 目录快照：Provider-facing model ID、pricing/price version 和 catalog version 均来自 snapshot；逻辑 ChatGPT/Claude 只通过显式 preset map 识别，允许同一逻辑模型拥有多个部署。
- P3-C local fixture、P6-G `curated:*`、模型展示名、UI 选择和测试 fixture 都不得成为生产投影输入。未验证目录、无 SHA-256、fallback mapping、缺失映射、非唯一逻辑归属和未知价格均拒绝。
- `AppContainer` 注册惰性 Direct owner；默认 coordinator 的 transport/runtime/Usage ports 继续 disabled。没有 UI/Activity/ViewModel/Workspace 注入，没有 Key/settings 读取、HTTP、Provider probe、Room/Usage 写入或真实调用。
- 当前安装态若没有已验证的持久化 OpenRouter snapshot，Direct 必须保持 readiness-rejected；下一独立缺口是获得既有 P2-J 目录验证路径产生的真实 verified snapshot，不能以 fixture 或 UI 推测填补。

证据见 `MM_O3A_DIRECT_APPLICATION_BOUNDARY_EVIDENCE.md`。这不满足 MM-O3 的用户可见确认、真实文本、
停止/失败/重启/Usage 或任何真实 Provider 退出门。

### MM-O4：Compare MVP

- 默认且最多 2 个逻辑模型目标（首组为 ChatGPT 与 Claude），并行独立执行；Provider-facing 实际模型 ID 只在当时 Deployment Registry 中解析，不写死到产品合同。
- Android 使用不改变现有布局骨架的渐进入口；Desktop 采用现有工作台响应式承载，不复制示意图。
- 每个结果绑定独立 branch；支持对单一分支继续追问。
- 汇总确认披露所有第三方收件人和总预算，各分支分别记账。

退出门：2 个分支成功、部分失败、分别取消、重启恢复、分支追问和费用聚合均有真实可见证据。

#### MM-O4-A：Compare 纯 domain/application 核心（2026-08-16，已完成，非真实闭环）

- `CompareExecutionApplicationOwner` 是唯一 Compare 应用 owner；它固定只接受 ChatGPT + Claude 两个不同 Logical Model、两个不同 Deployment、同一 `CanonicalContextSnapshot` 与同一 text-only 输入。请求顺序直接形成稳定的 `branch:1/branch:2`，不会由 Provider、展示名或 Auto 重排。
- owner 明确调用 `MultiModelOrchestrator.Compare`，而 `AutoRoutingPort` 在合法或拒绝路径均不参与；随后 `MultiProviderModelRegistry` 必须逐支精确解析 Provider、provider-facing model ID、目录版本、price version、币种及已知价格。附件、无目录/未验证投影、未知价格、第三目标、重复模型/部署、非 ChatGPT+Claude 都拒绝，绝不 fallback 或局部替换。
- 两个分支均复用 `ConservativeInputBillingBudget` 对同一 UTF-8 输入和输出上限计算保守预算。只在币种相同时使用安全加法得到总上限；币种不同时显式拒绝，溢出同样拒绝，绝不把不同币种或溢出值相加。
- 仅生成一个默认未勾选、5 分钟、一次消费的汇总确认，绑定 request/context/text SHA-256、两位实际收件人、目录/价格/币种、分支预算及总预算。确认后只生成两个 content-free、独立、稳定的 branch grants；默认 `NOT_REQUESTED` synthesis 与 `EXPLICIT_ADOPTION_ONLY` shared context。不调用 Direct 的第二确认，不接 transport、credential、Runtime、Usage、Room、UI 或 AppContainer。
- 取消、过期、确认、目录/价格范围变化及显式单目标替换都会立即释放原文；只保留有 TTL/容量上限的 content-free replay 拒绝事实。单目标变化会使整个汇总确认失效，调用方必须重新发起完整 Compare，不存在部分静默替换。
- 返回的 `CompareExecutionGrantedPlan` 与每个 branch grant 额外绑定同一 text SHA-256、确认到期时间及稳定 one-shot grant identity，供后续纯领域 Session owner 验证；仍不含原文或可执行 transport。
- 证据见 `MM_O4A_COMPARE_APPLICATION_CORE_EVIDENCE.md`。这不满足 MM-O4 的真实两分支、部分失败、取消、重启、追问、Usage 聚合或任一 Provider/UI/设备退出门。

#### MM-O4-B：Compare Session 与 Conversation Tree 纯领域接合（2026-08-16，已完成，非真实闭环）

- `CompareConversationSessionOwner` 是唯一新增 Session/Branch IR owner。它只接受尚未过期且未被其他 session 消费的 `CompareExecutionGrantedPlan`，以现有 `ConversationSnapshot`/`MessageTree` 验证同一个 conversation、同一个当前 user parent leaf 与同一个 `CanonicalContextSnapshotRef`；它不保存或复制 MessageNode 内容，不能建立平行消息树。
- 每支 grant 预留独立 assistant node、Invocation、Attempt 与 cancellation identity，初态为 `RESERVED`；其 terminal IR 可分别变为成功、失败或取消，单支失败/取消不回滚另一成功支。会话计划不会改写 `Conversation.currentLeafMessageId`，也不会调用 `ConversationTreeService` mutation、Repository、Runtime、Room 或 transport。
- 分支追问只允许成功 branch，输出新的 Canonical Context 引用，并把该 branch assistant 作为唯一 tail、其 sibling assistant IDs 作为排除集；因此后续 adapter 不得把另一 Compare 输出并入该支 context。显式 adopt 也只允许一个成功支，输出保留所有 sibling 的审计 plan；重复相同 intent replay，第二 adopt 或冲突 intent 拒绝。
- Synthesis 默认不存在。只有两个 branch 都成功后，显式 synthesis intent 才能产生第三个独立 assistant/Invocation/Attempt 的新 branch plan；它绑定两个来源 branch/assistant、一个新的 Canonical Context 和默认未确认的新 consent/budget gate，明确不能被当作任一原 Compare 回答。重复同一 intent replay，额外 synthesis 或过期 gate 拒绝。
- 审计结论：现有 `ConversationTreeService`/`MessageTree` 可作为唯一 tree truth；但 P3 `ConversationRuntimeStateMachine` 每 conversation 只持有一个 current runtime，启动和后续事件均要求当前 leaf，当前 Room repository/API 也没有 Compare session 存储。因此本阶段正确停在纯 IR/adapter plan，未改 Schema/DAO；未来持久化/执行必须先单独设计这一接合，不得偷建第二棵树。
- 证据见 `MM_O4B_COMPARE_CONVERSATION_TREE_EVIDENCE.md`。这不满足 MM-O4 的真实分支执行、部分失败/取消恢复、重启、追问、adopt、synthesis、Usage 聚合或任何 Provider/UI/设备退出门。

#### MM-O4-C：Compare 持久化与 Runtime 基座（2026-08-16，已完成，非真实闭环）

- Schema 31→32 只追加 `CompareConversationSessionStore` 所需的 session、双 branch identity、独立 branch runtime/checkpoint、append-only runtime event 与 typed terminal/follow-up/adopt/synthesis intent 事实。每 branch 有独立 assistant/invocation/attempt/future execution/cancel/reservation identity；P3-I receipt 与 Usage Ledger 继续是既有事实 owner，本增量不生成或改写它们。
- `conversation_runtime_states` 的 conversation 唯一约束与 `ConversationRuntimeStateMachine` 的 current-leaf 前提继续原样保留；Compare 绝不硬复用该单 current runtime。Room adapter 不向 `message_nodes` 插入无内容 reserved assistant，不改变 `Conversation.currentLeafMessageId`，也不建第二棵树。
- session、terminal、follow-up、adopt、synthesis 均持久化并在 restart 后可读回；相同 intent replay、不同事实 conflict fail-closed。一个 sibling 成功、失败或取消不会覆盖另一支；follow-up 固定排除 sibling，adopt 保留 sibling，synthesis 仍是两个成功支后的独立 invocation/新 context/新 gate。
- 证据见 `MM_O4C_COMPARE_PERSISTENCE_RUNTIME_EVIDENCE.md`。这不满足 MM-O4 的真实模型执行、P3-I receipt、真实 Usage、Provider、UI、设备或 egress 退出门。

#### MM-O4-D：Compare 临时内容租约与双分支事务接合（2026-08-16，已完成，非真实闭环）

- 生命周期审计确认：MM-O4-A 原 `confirm()` 在给出 content-free grants 前释放唯一原文引用；因此 hash 只能校验、不能恢复文本。本增量使同一 `CompareExecutionApplicationOwner` 在确认成功后唯一持有 process-memory-only text lease，直到一次批量 dispatch 尝试的终态才释放。
- 新增 `CompareBatchDispatchPort` 仅有全双分支 batch acceptance 合同，接收同一 `CanonicalContextSnapshotRef`、两已确认 grants 与同一 one-shot lease；默认实现 fail-closed。它不是 Provider Adapter，不读 Key、不发 HTTP、不建 transport、不接 UI，也没有被 `AppContainer` 注册。
- Schema 32 不升级：`CompareConversationSessionStore` 复用现有 append-only `compare_branch_runtime_events` 记录两支一致的 `DISPATCH_INTENT_RECORDED`、`DISPATCH_ACCEPTED` 或 `DISPATCH_REJECTED_RETRY_REQUIRED`。Room readback 只重建 content-free `RETRY_REQUIRED` / pending / rejected / accepted 状态；重启前原文丢失时，所有非 accepted 状态都必须重新提供匹配文本并重新确认，绝不由 hash 恢复、静默重发或消费旧 grant。
- 同 intent 的重复 accept 或冲突均 fail-closed；port 只可整体接受或整体拒绝，不能先接受一支再留下第二支未定义。日后实际 Provider 执行的分支 partial success/failure/cancel 仍属于独立 branch runtime，不能回滚 sibling。
- 本增量不预插 assistant，不改变 `Conversation.currentLeafMessageId`，不创建第二棵树，不写 P3-I receipt/Usage/真实 attempt，也不构成 Provider、HTTP、UI、设备或 egress 证据。证据见 `MM_O4D_COMPARE_TRANSIENT_LEASE_DISPATCH_EVIDENCE.md`。

#### MM-O4-E：OpenRouter Compare 双分支执行 Adapter Readiness（2026-08-16，已完成，非真实服务闭环）

- 唯一执行链为 `CompareExecutionApplicationOwner` 将 Store readback 的两支 execution/invocation/attempt/cancel/reservation identity 注入 batch → `OpenRouterCompareBatchDispatchAdapter` → 同一个 `RealTextExecutionCoordinator` → 既有 `OpenAiCompatibleProviderTransport(OPENROUTER)`。不调用旧 `OpenRouterInferenceAdapter`，不新建 HTTP/JSON/错误映射/receipt/Usage owner，Auto Router 仍为零调用。
- `RealTextExecutionReadyPlan` 的普通 `ModelPresetId` 代表旧 Settings/Registry 语义，不能伪装动态 Compare deployment；因此仅新增 mutually-exclusive、content-free `VerifiedCompareDeployment` target。P3 preflight/settings/credential-presence/current-leaf 合同保持原义，Compare 用 grant 的实际 OpenRouter provider-facing model ID、catalog/price version 与 Schema 32 branch identity 构造 coordinator plan。
- Adapter 一次 take 同一 text lease，在进程内为两支构造 `ProviderTransportRequest`；两支只接受 `providerHandle=openrouter`、ChatGPT+Claude 的 verified grant、不同 deployment 和原 grant model ID。一次 batch accepted 仅说明两个 branch 已交给同一个 coordinator；success/failure/cancel 可独立写 Schema 32 branch terminal，不回滚 sibling。
- `ProtectedProviderCredentialHandle` 继续 opaque。默认 disabled handle、`DisabledOpenAiCompatibleHttpClient`、disabled receipt/Usage ports 均 fail-closed；未读取/解密 Key、未注册真实 HTTP 或生产 Room test port。未来生产 receipt/Usage adapter 必须另行实现现有 port 合同与逐 branch 事务，不能注册当前 `RoomRealTextExecutionCoordinatorTestPortAdapter`。
- Schema 不升级、不预插 message、不改 shared current leaf、不建第二树。证据见 `MM_O4E_OPENROUTER_COMPARE_ADAPTER_READINESS_EVIDENCE.md`；这不构成真实 OpenRouter、Usage、receipt、UI、设备或 egress 验收。

### MM-O5：协作能力

- Cross Review、角色分工、显式 Synthesis。
- 所有交叉输入都形成新的 Canonical Context Snapshot 和 Invocation。
- 不默认综合，不自动采用。

### MM-O6：Auto Router

- 在 Direct 与 Compare 契约稳定后，扩展质量/均衡/成本策略。
- 硬约束先过滤，软评分后排序；理由、候选、拒绝原因和 fallback 授权可审计。
- Auto 只能在用户未指定目标时工作。

## 6. 首批实现边界

MM-O1 与上述 MM-O2 纯领域／Registry 增量已完成。不得将本批次扩展为：

- 修改 Drawer、Composer、消息列表、按钮、颜色、尺寸、位置、层级或任何已确认 UI；
- 接线当前未验证的 P3-J UI WIP；
- 读取或修改 API Key，发送 HTTP，消费真实验收 nonce；
- 新增数据库 Schema、后台任务、Provider 健康探测或远程遥测；
- 把 OpenRouter 的单一部署现状误写为逻辑模型永久绑定；
- 用 Mock、静态测试或构建成功宣称 Direct/Compare 真实闭环完成。

## 7. 已确认的产品参数（2026-08-16）

1. `Compare > Direct > Auto` 仅代表研发／架构优先级；普通对话未指定模型时继续默认 Auto。
2. Provider 采用统一核心、OpenRouter 首部署、原生 Adapter 一个一个验收；不得等待或伪装为五家原生 API 同时完成。
3. Compare MVP 默认且最多同时比较 2 个逻辑模型；首组明确为 ChatGPT 与 Claude。一次汇总确认与分支独立账本仍是后续真实执行合同；本计划不写死动态 Provider-facing 实际模型 ID。
