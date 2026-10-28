# 南枫 AI P3-J 普通聊天显式外发确认、运行状态与失败恢复合同

<!-- contract-governance:start -->
> **合同口径：现行领域合同。** 领域入口：[ 普通发送授权与执行 ](P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** 普通发送与显式重试的准确材料/接收方授权；旧账本读取不授予外发
<!-- contract-governance:end -->

日期：2026-08-15  
历史阶段状态：**合同与纯静态守卫已建立；无 production 注册、无可见布局改动、无 Provider/HTTP/Key/Room schema 改动。2026-08-16 仅隔离 Composer 的错误 confirmation callback。**

## 当前普通发送与授权口径

当前Android普通发送链为Compose/ViewModel → NormalChatEgressAuthorization → NormalChatOpenRouterExecutor → Provider Adapter／HTTP／SSE → ConversationRuntime／Room正文及独立调用计量；后台由已有生成服务持有请求与持久检查点。Mac为ESM → Tauri prepare／submit／retry → desktop_ordinary_chat_v1／SQLite。选择、预览或打开设置不自动外发。

授权绑定本次准确材料、回答Provider／模型及已披露的检索接收方，不因文档整理新增重复确认。新发送使用当前授权版本；历史disclosureVersion保留原值，读取兼容不产生网络权限。具体当前范围、版本及已验API别名见[RelayCore合同](RELAYCORE_MODEL_CURRENT_CONTRACT.md)和[模型架构](model-service/MODEL_SERVICE_ARCHITECTURE.md)。

附件辅助识读与网页检索是不同外发目的。依用户明确要求，Android／Mac聊天输入框不显示附件识读、接收方、计费或“点击发送即授权”等常驻小字，也不把这类说明改成重复弹窗；不为新增读取路径自行恢复已删除的提示。既有发送授权仍冻结准确材料、原选回答模型与具体读取方，删除展示不扩大外发范围，不新增或更换接收方。Android辅助模型映射纳入同一材料指纹；Mac使用独立`attachmentReading`（`normal-chat-attachment-reading-v2`），绑定原选Provider／模型、材料指纹和读取方集合，不能借用`retrievalRecipient`的Luna搜索grant。混合图片与音视频须分别包含所需接收方；旧`normal-chat-image-reading-v1`不能授权新增类型或读取方，历史仍可只读。可读纯文字PDF可仅使用本机提取，不要求一次没有实际发生的识读调用。配置缺失、身份或材料变化均在外发前阻止，不能静默换模型、Key或接收方。

按用户2026-10-13明确要求，Android／Mac Composer正常与重试状态不再常驻原有接收方、附件清单及费用的重复说明；不新增同类显而易见提示。接收方与准确材料继续在既有发送选择／授权owner冻结并校验，去掉文案不扩大授权，不恢复旧授权权限；必要失败状态与真实账目仍保留。

普通／工作生成和显式重试沿原持久owner收尾；UNKNOWN不自动重发，不换Provider／模型／协议。临时聊天仍只追加本地消息，不读取模型凭据或发送网络请求。当前恢复、上下文、计量与后台规则分别归运行时、费用和南枫岛合同。

下方P3-J记录约束当时的fail-closed预备owner／静态守卫，不得将其“只有本地USER”“尚无生产注册”等历史阶段状态套到现在的普通生成流程。

## 历史P3-J预备链与验收记录（2026-08-15／16）

### 结论与唯一 owner

普通聊天未来若获得单独授权接入真实文本执行，唯一生产编排 owner 必须是新增的
`NormalChatRealTextExecutionOwner`。它是一个明确命名、可审计的应用层 owner；不是
`ConversationWorkspace`、`ConversationFoundationViewModel`、`AppContainer` 的隐式副作用，
不是 `P2MRealTextExecutionBridge`，也不是 Provider、HTTP、Key、Room repository 或 Usage
Ledger 任一方的反向入口。

源码存在 fail-closed `NormalChatRealTextExecutionOwner` 及其 ViewModel/AppContainer 注入，但它没有 P3
ports、confirm/send API、credential、transport、receipt 或 ledger 能力，且 Composer 不再触发它。当时普通
聊天的发送链仍然只有：

```text
chat-first Composer → ConversationFoundationViewModel.submitCurrentDraft()
→ SubmitConversationDraftUseCase → 本地 USER 草稿提交
→ “未连接 Provider”安全提示
```

这不是已接通、已确认或已调用的替代说法。P2-M 的桥接只处理固定合成文本和默认拒绝端口，
其结果被 legacy executor 忽略；它永远不能成为普通聊天的快捷入口。

### 冻结布局内允许的未来新增内容

`UI_LAYOUT_FREEZE_BASELINE.md` 继续优先。未来实现只能在既有 chat-first 信息架构中增加下列
必要内容，不能移动、改尺、重着色、重排或合并现有 Composer、菜单、顶部栏、抽屉或消息控件。

| 现有槽位 | 允许的未来新增内容 | 不允许的变化 |
|---|---|---|
| 当前 Chat Composer 的发送意图 | 触发一次内存态 `ExternalSendConfirmation`；尚未确认时不创建 egress | 把 Composer 改成新页面、改变 dock 高度/位置/测量，或默认向外发送 |
| 根 overlay（现有 Composer/Menu 的 sibling，且在 Composer measurement tree 外） | 一个白色、可关闭的显式外发确认面 | 让确认面推动 transcript、改变 Composer 位置，或增加第二套底部操作栏 |
| 既有 transcript assistant 行 | 一条当前执行的安全状态或终态恢复行 | 用假回复、fixture 成功或额外工程控制面伪装真实结果 |
| 既有尝试/调用记录信息区 | 仅安全运行元数据的历史投影 | 显示 prompt、回复正文、Key、URI/path、原始 payload/错误或把本地 fixture 标为 Provider 调用 |

本合同不固定新增文案的视觉设计；它只冻结必须披露的内容、状态与交互门。任何实际 Compose
变更仍须先按冻结基线单独获得视觉授权。

### 显式外发确认合同

#### 何时可出现

1. 只在普通 `CHAT`、用户对当前非空草稿发起一次“请求第三方生成”的明确意图后出现。
   当前普通 `submitCurrentDraft()` 的本地提交语义在本增量不变，不得偷偷转换为外发。
2. owner 先在内存中冻结草稿文本、会话/branch、选择的 provider/preset/model、已验证 registry
   snapshot、价格版本、最大预算和取消身份；然后以这些事实创建 P3 preflight 所需的 request。
   此阶段没有 Room receipt、Usage reservation、Key bytes、Authorization、HTTP 或附件读取。
3. `RealTextExecutionPreflightOrchestrator` 仅返回 `ReadyPlan` 或受限 `Blocked`。只有 `ReadyPlan`
   与同一冻结 scope 匹配，才可以显示可确认面；`Blocked` 只显示安全原因和本机下一步。

#### 确认面必须同时展示

- 第三方服务商的显示名，以及本次精确 provider handle。
- 本次模型显示名、精确 model ID、preset 和已验证 registry snapshot；不得拿 P6-G 本地 fixture
  或“自动”标签冒充将要外发的模型。
- 发送内容类别：**仅当前这条用户草稿的文本**；并明确声明不发送其他会话历史、系统隐藏内容、
  URI/path、设备/账号资料、Key、调用记录或诊断。
- 费用：价格版本、币种、预估 input/输出上限与“本次最多预留/可能产生”的金额。价格或上限未知
  即不显示可确认状态；不能写成 `0`。
- 附件结论：P3-J 为 **text-only**。任何草稿附件（图片、视频、音频、PDF、文件或临时附件）均为
  `ATTACHMENTS_NOT_SUPPORTED`，不提供勾选、预览、读取、上传、续期或绕过路径；确认面必须明确
  “附件不会发送”，并要求用户移除附件或继续当前本地草稿流。
- 未勾选的、一次性的显式确认框；确认按钮在勾选前不可用。没有预勾选、默认同意、记住同意、
  静默复用、后台确认或由旧 P2-M 同意替代。

确认只绑定冻结的 request fingerprint、provider/model/preset、registry snapshot、价格版本、币种、
最大预算、text-only 内容类别、会话/branch 与草稿版本。任一项变化、草稿编辑、会话/branch 切换、
模型/registry/价格变化、附件出现，或超过 **5 分钟**，均立即过期、撤销勾选并回到重新 preflight；
不能用旧确认继续发送。

### 未来唯一接线顺序

只有 `NormalChatRealTextExecutionOwner` 可以把确认后的瞬时文本连接到 P3；所有箭头均由该 owner
正向调用，任何后端端口不得反向触发 UI 或 egress。

```text
明确外发意图
→ freeze draft / selected route / fee scope（内存）
→ P3 preflight（presence-only Key、verified registry、text-only、附件强拒绝）
→ 根 overlay 的未勾选可见确认
→ final scope + expiry + duplicate check
→ 本地 USER/assistant 位置 + P3-I prepare + P0 reserve 的可恢复一致性边界
→ opaque credential broker（仅本次调用栈）
→ OpenAiCompatibleProviderTransport / injected real HTTP client
→ P3-B runtime event + P3-I terminal receipt + P0 settle/release + Invocation/Attempt safe facts
→ transcript 状态、恢复操作与安全调用记录
```

接线规则：

1. `AppContainer` 在获得**新的实现授权**后只能构造这一个 owner，并向它显式注入 P3 preflight、
   P3-I durable receipt、P0 reservation/settlement、P3-B runtime、已授权 credential broker 和真实
   transport；不能把这些能力直接注入 `ConversationFoundationViewModel`、Composer 或 P2-M bridge。
2. owner 在最终确认后重新核对完整 scope，再一次性分配 execution/invocation/attempt/idempotency
   identity。P3-I `PREPARED` 不是“已发送”；只有 transport 建立并产生 `Started` 后才能显示为运行中。
3. `ProtectedProviderCredentialHandle` 只能由另行授权的 broker 在最终调用路径提供。preflight 的
   `credentialPresence()` 永远不能升级为读取 Key bytes 的授权。
4. 当前 `OpenAiCompatibleProviderTransport` 的 disabled client、P3 coordinator 默认拒绝 ports 和
   P3-I/Room test adapters 继续 fail-closed/test-only；不可将它们注册为生产入口。

### 运行状态、取消与恢复

| 可见状态 | 最小事实 | 用户可做什么 | 禁止的推断或行为 |
|---|---|---|---|
| 待确认 | 内存 `ReadyPlan`，未勾选 | 取消、回到编辑、勾选确认 | 不创建 receipt/Attempt，不称已发送 |
| 已过期/被阻止 | 受限 preflight 或 scope/TTL blocker | 修改草稿、配置或重新开始 | 不保留旧勾选，不读 Key 或重试网络 |
| 已确认，准备中 | P3-I `PREPARED` 与一次性 identity 已建立 | 取消尚未开始的请求 | 不称已运行、成功或已产生实际费用 |
| 生成中 | 仅在规范化 `Started` 后的 `RUNNING` | 显式停止 | 不接受重复发送，不把 partial 当完成 |
| 已完成 | `SUCCEEDED` 及一致的 runtime/attempt 终态 | 阅读结果；如需再次生成，重新确认 | 不改写为另一终态，不杜撰 token/实际费用 |
| 已失败/已取消 | `FAILED` 或 `CANCELLED` 的安全错误码 | 查看安全原因、编辑后新建一次确认 | 不自动重试、后台续发或用 fake 回复补洞 |
| 需恢复核对 | 进程重启读回 `PREPARED/RUNNING` 的持久事实 | 仅手动结束、查看安全记录或发起全新确认 | 不自动恢复 HTTP、续流、重建正文或释放未知实际费用 |

取消必须有唯一终态：确认面取消/过期时仅丢弃内存 snapshot；已 prepare 未开始时安全取消并按事实
释放预留；运行中向 transport 发显式 cancel，并由 terminal receipt 驱动后续 release/settlement。若
进程在 `RUNNING` 中断，owner 不得猜测 Provider 是否完成、不得自动重发或把成本写为零；未知的预留
保持待对账，直到存在安全、持久的终态事实。

重复点击、重复确认或同一 idempotencyKey 的重放只能读回同一安全 terminal，绝不再次 reserve、读取
credential 或执行 transport。任何失败后的“重试”都必须是新的草稿 scope、新 execution/invocation/
attempt identity、新费用确认和新的未勾选确认框。

### 调用记录语义

- 一条 `ConversationRealTextExecution` receipt 表示一次逻辑执行；`Invocation` 连接会话 assistant
  位置；`ProviderAttempt` 表示一个物理外发尝试。三者 ID、request fingerprint、requested/actual
  provider/model、状态、时间、规范化事件数和安全错误码必须可相互核对。
- 记录可以显示确认时的最大预算/预留与服务端**安全、持久、可核验**的用量或成本；缺失则显示
  “未知”，不能以 `0`、空白成功或本地 fixture 推断。实际费用不能超过已确认上限，否则安全失败并
  保留对账事实。
- 记录永不保存或显示草稿/prompt、模型回复原文、Key、Authorization、endpoint/URI/path、附件字节、
  原始 HTTP status/body、Provider payload 或诊断堆栈。用户可见 assistant 正文属于既有会话消息
  owner，不是调用记录字段。
- 本地 fixture、scripted transport、P2-M 历史和 test port 必须显式保持各自来源；不得合并、排序或
  贴标为普通聊天 Provider 成功。

### 后端入口矩阵与验收

| 入口/owner | 当前事实 | P3-J 后续允许的职责 | 当前禁止 |
|---|---|---|---|
| Composer / Workspace | 只转发本地草稿提交；冻结 UI | 仅触发/承载确认面与安全状态投影 | 创建 egress、读取 Key、调用 transport、改变已冻结布局 |
| ConversationFoundationViewModel | `submitCurrentDraft()` 只走本地 `SubmitConversationDraftUseCase` | 只把明确的 UI intent 交给唯一 owner，保留 render state | 自行 preflight/confirm/HTTP 或持有 credential |
| `NormalChatRealTextExecutionOwner` | fail-closed source owner、不可由 Composer 触发，未注册 P3 ports | 唯一编排上述完整顺序与恢复 | 绕过确认、静默 retry、兼容 P2-M fixture |
| AppContainer | 构造 P2-M 默认拒绝 bridge 与 fail-closed normal owner；无普通聊天 P3 注册 | 仅显式构造唯一 owner | 把 P3 ports/HTTP/Key 广播给 UI 或普通 ViewModel |
| P3 preflight/coordinator/P3-I | 已实现但未在普通聊天注册 | 作为 owner 的受限端口 | 把 Ready/receipt 当 egress 授权或真实成功 |
| Provider/credential/HTTP | disabled 或未授权生产 broker/client | 只在最终确认后的 owner 调用栈 | P2-M 复用、附件 egress、自动网络/重试 |

#### 自动验收（未来实现前不得跳过）

1. 静态/单元：普通 `submitCurrentDraft()` 仍无 Provider；无 `NormalChatRealTextExecutionOwner`
   注册时，P3 端口只能 fail-closed。确认字段、未勾选默认、scope/TTL 过期、附件拒绝、重复点击与
   P2-M 隔离均有测试。
2. 领域/Room：preflight 不读 Key bytes、不读附件；同一 idempotency replay 不再发起 transport；
   cancellation、失败、重启 readback、未知费用与 reservation/release/settlement 的一致性均有
   fake/Room 回归。test adapter 不能被 AppContainer 注册。
3. transport fake：严格 provider/model/body、取消、401/402/429/timeout/5xx/解析失败映射、安全
   metadata 白名单以及无自动 retry；不以 fake 代替真实服务。

#### 真实验收（需要新授权，当前未执行）

1. 在正常 chat-first Composer 路径实际看见未勾选确认、服务商/模型/文本类别/费用/附件排除，并验证
   取消、过期、重复点击与失败恢复均不移动冻结布局。
2. 使用用户明确授权的非敏感测试文本、受控 credential broker 和一次受限真实请求，分别留存确认、
   transport、receipt/ledger、运行状态、终态和冷启动 readback 的安全证据。
3. Provider 真实响应、实际 token/费用、Android/Desktop/OPPO、签名包与发布均分别验收；构建、
   fake、Room 或 UI 截图都不能替代其中任何一项。

在新的实现授权到来前，本合同的正确状态就是：普通聊天本地提交保持不变，P3 普通聊天 egress 保持
未注册且 fail-closed。

### 2026-08-16 MM-O3-A P3-J WIP 隔离修正

审查发现 `ConversationWorkspace` 曾在同一个 Composer `onSubmit` 回调中先创建未注册的
`ExternalSendConfirmation`、再调用既有本地 `onSubmitDraft()`。这会使本地草稿提交/清理之后仍出现
默认不可确认的模态，不是用户单独提出的第三方发送意图。现仅移除该一次回调：普通发送恢复为单一既有
本地 owner；保留但未注册的 Dialog、ViewModel 内存态和 fail-closed owner 不构成 MM-O3-A 可见入口。

此修正不得改变任何 Composable 结构、测量、位置、尺寸、颜色、圆角、阴影、文案或 Dialog 外观。
`P3JNormalChatExplicitEgressContractsTest` 同时锁定本地 `onSubmitDraft()`、无 confirmation callback、既有
工作/聊天计数更新，以及 composer 的既有 overlay、padding、36dp token 与 `DraftComposer` 结构。

## 当前Luna辅助接收方授权

Android复用v3的精确auxiliaryRecipientModelIds，将当前Luna预设、Provider/API ID与原回答模型、文字及附件身份／hash同时纳入发送指纹；后台Intent完整传递该映射。旧Sol辅助映射或旧独立服务授权不满足本次Luna校验，新发送不继承旧搜索接收方。

Mac的Lunareceipt版本为`normal-chat-luna-search-v1`，包含`OPENROUTER / GPT_6_LUNA / openai/gpt-6-luna`和`SHA256(JSON([trim后的本次文字,排序后的不可变附件ID]))`。正常发送在读Key／建立连接前核验；显式重试绑定原文字及原附件，不使用后来编辑的草稿。无receipt、旧版本、模型或材料变化均拒绝，不能靠读取兼容扩大外发。普通自身联网沿用原发送语义，不增设弹窗或常驻按键。来源、失败与计量唯一正文见[C12](C12_MODEL_NETWORK_PARITY_CONTRACT.md)。

## 历史附件独立搜索服务授权（只读）

以下用于解释旧v3记录；新发送及重试拒绝旧服务grant，不把它作为Luna授权。


独立搜索服务不是auxiliary模型：serviceId=`ZHIPU_WEB_SEARCH`、engineId=`search_pro`；正常Send前在原入口显示接收本次所有附件公开核验要点、原选模型回答及可能费用。Android的v3指纹绑定auxiliaryServiceEngines，后台Intent严格保留映射；旧无服务字段v3及历史v2只读不扩大授权。Mac服务receipt自身为normal-chat-egress-v3，并校验当前文字与不可变附件ID材料指纹。无授权／材料变化不读Key或发送。显式重试从原材料重新构建当前已披露服务授权，仍用原回答模型；读取历史及后台UNKNOWN恢复不构成新发送。独立服务接口、覆盖、空来源和计量唯一规则见[C12](C12_MODEL_NETWORK_PARITY_CONTRACT.md)。
