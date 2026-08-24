# 南枫AI完整开发档案

> 用途：交给 Claude 或新的架构负责人，快速理解当前 Android 项目已经有什么、哪里已经接通、哪里只是历史合同，以及最应优先重构的地方。
>
> 更新日期：2026-08-24。项目源码是当前行为的第一事实源；[CURRENT_HANDOFF.md](CURRENT_HANDOFF.md) 是近期变更与设备事实源；较早的 P0–P11 合同大量保留历史边界，不能单独证明当前线上能力。

## 0. 2026-08-24 增量校正

- **当前基线：** Android 正式包为 `66 / 0.3.0-p10j`，Room Schema 为 45；OPPO Find N5 已同签名覆盖该版本，首次安装时间保持不变。此前本文中 code 63 / Schema 39 的快照仅为历史记录，不得作为当前基线。
- **会话 UI：** 当前 Android 的顶栏状态、无色相底层、左栏会话行、右滑动作、Composer、模型显示与选择面以 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 为唯一正文。本文及旧阶段合同的视觉数值不再并列覆盖该合同。
- **发送与模型：** 普通聊天已有正式发送/回读、模型归因、目录刷新与 Fast 变体拒绝代码路径；构建、签名和设备覆盖不等于真实第三方服务闭环。真实联网结果、主屏右滑和点外部收起的最终手感仍须在用户实际操作时单独验证。

## 1. 一句话结论

南枫AI是一个 Android 本地优先、多模型聊天与知识工作台。它已经拥有：本地会话树、草稿、知识库、Memory、附件私有存储、多个严格导入/导出链、模型配置、OpenRouter 目录校验、Qwen/DeepSeek 直连、自动路由和调用审计。

但模型调用层是在既有“离线/结构化任务合同”上逐步接上的，尚未形成单一、清晰、可恢复的联网消息发送架构。当前最值得重构的不是再加模型名称，而是把 **持久化消息、一次真实外发、回包解析、失败状态、重试、审计和 UI 投影** 收敛为一个正式的发送状态机。

## 2. 当前工程快照

| 项目 | 当前事实 |
|---|---|
| Android 包名 | `com.nanzhufeng.ai` |
| SDK | min 26 / target 36 |
| 当前正式 APK | `versionCode 66` / `0.3.0-p10j` |
| Room Schema | 45 |
| 当前 Git 基线 | 当前 `main` 的本地 checkpoint；后续工作区状态与产物哈希以 `CURRENT_HANDOFF.md` 为准 |
| Android 分层 | `domain/` 业务规则与端口；`data/` Android/Room/文件实现；`ai/` Provider 与编解码；`ui/` Compose/ViewModel；`app/` 装配 |
| 主设备 | OPPO Find N5，正式 App 已同签名覆盖至 code 66；禁止仪器测试、卸载、清数据或读取私有业务数据 |
| 外部同步 | Google / Supabase 已由产品负责人明确跳过，不应在重构时顺手启用 |

当前最新版的设备覆盖和发送问题见 [CURRENT_HANDOFF.md](CURRENT_HANDOFF.md)。旧合同中“未接 Provider”的措辞是历史状态，不能覆盖 code 63 的当前源码。

## 3. 产品对象与本地数据模型

### 3.1 主要对象

```text
Conversation（会话树）
  ├─ MessageNode（角色、父子、同级顺序、版本、投递状态）
  │    └─ ContentBlock.Text / ContentBlock.Attachment
  └─ ConversationDraft（当前唯一草稿）

Project / Knowledge / Memory / Relation
  └─ 可被 LocalContextBroker 本地排序后作为上下文摘录

Private Attachment Asset
  └─ 私有字节、MIME、尺寸、SHA-256；消息里只有稳定引用

Provider Settings / Encrypted Credential / Registry Snapshot / Routing Policy
  └─ 与业务正文分开保存

Usage / DirectChatCallAudit
  └─ 只记录调用元数据，不记录 Key、请求正文或原始响应
```

数据库定义在 `app/src/main/java/com/nanzhufeng/ai/data/local/NanfengAiDatabase.kt`，Room 已累计到 schema 39。迁移数量多，重构时必须保留完整升级链，不能以删库、`fallbackToDestructiveMigration` 或手工 DB 注入替代。

### 3.2 本地优先与隐私

- 会话、草稿、知识、Memory、关系、任务状态和附件均先落本机。
- API Key 不在 Room：Android Keystore AES-GCM 加密，密文放 private SharedPreferences；每个 Provider 的 Keystore alias 分离，并以 Provider ID 作为 AAD。
- Settings 读取“是否存在凭据”不解密 Key；真正外发时才短暂解密为 `CharArray`，调用结束后清零。
- 调用审计只应保存：`Provider → endpoint → model_id → alias → reasoning level → timestamp → token usage → result`。不得保存 Key、Authorization、请求正文、回复正文、附件字节或原始 HTTP 回包。
- [P5-C 隐私与数据管理合同](P5C_PRIVACY_DATA_DIAGNOSTICS_CONTRACT.md) 规定数据管理只展示安全聚合和占用字节；诊断导出严格白名单，禁止正文、URL、路径、Key、请求/响应。

## 4. 联网模型配置：当前实现

### 4.1 服务商、固定端点与模型归属

端点在 `domain/ModelService.kt` 固定，普通设置页不允许用户任意填写 URL：

| 逻辑服务商 | 实际端点 | 使用模型 |
|---|---|---|
| OpenRouter | `https://openrouter.ai/api/v1/chat/completions` | GPT-5.6、Claude、Gemini |
| Qwen 官方直连 | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` | Qwen3.7-Plus、Qwen3.8-Max、Qwen3.6 Flash |
| DeepSeek 官方直连 | `https://api.deepseek.com/v1/chat/completions` | DeepSeek V4 Pro |

模型名称是产品的 **逻辑槽位**，不应把显示名直接当作永恒 API model ID。OpenRouter 实际 model ID 由公开目录快照精确映射；Qwen/DeepSeek 当前在 `ComposerModelRoutingCatalog.concreteModelId()` 中有固定 ID。重构后应将这两种来源统一成一个 `ResolvedModel` 对象。

### 4.2 Composer 的五个入口

| 槽位 | 子项 / 实际意图 |
|---|---|
| 自动 | 默认 GPT-5.6 Terra；视频/图片/PDF 优先 Gemini 3.7 Flash；大量知识优先 Qwen3.6 Flash；复杂调试可升级 GPT-5.6 Sol |
| 对比 | GPT-5.6 Sol / Claude Opus 5；Claude Opus 5 / Qwen3.8-Max |
| 日常 | GPT-5.6 Terra、Qwen3.7-Plus、Gemini 3.7 Flash |
| 深度 | GPT-5.6 Sol、Claude Opus 5、DeepSeek V4 Pro、Qwen3.8-Max |
| 图像/视频/PDF | Gemini 3.7 Flash、Qwen3.7-Plus、GPT-5.6 Terra |

`ChatRoutingPolicy` 当前可持久化：自动路由、自动兜底、质量升级、跨模型审查策略、跨 Provider 兜底开关。默认允许同 Provider 兜底，但跨 Provider 兜底默认关闭，避免用户不知情地把内容换服务商发送。

### 4.3 OpenRouter 模型目录

目录入口为无认证、无正文的 `GET https://openrouter.ai/api/v1/models`：

- 连接超时 8 秒，读取超时 20 秒，最大回包 4 MiB；拒绝重定向。
- 只保存清洗后的模型能力、上下文、价格、预设映射、ETag、摘要 hash；不保存原始目录 body。
- `OpenRouterRegistrySnapshotVerifier` 只接受精确存在的 GPT-5.6 Sol/Terra、Claude Opus 5、Gemini 3.7 Flash 映射，不拿“相近模型”偷偷替代。
- 目录存在但过期时，code 62 起会在发送前发现逻辑模型无法精确解析后自动刷新，再继续发送；不再让用户手动进设置“核验”。

### 4.4 普通聊天真实请求链

核心类当前名为 `NormalChatOpenRouterExecutor`，但它实际已承载 OpenRouter、Qwen、DeepSeek，名称已不准确。

```text
点击发送
→ ViewModel 用 mutex 写入当前可见草稿
→ SubmitConversationDraftUseCase 原子写入 USER Message 并清草稿
→ UI 立即刷新，用户消息先出现
→ LocalContextBroker 组装有限本地上下文
→ 解析逻辑槽位、Provider、精确 model_id、凭据
→ POST /chat/completions（stream=false）
→ 安全解码正文
→ 追加 ASSISTANT Message
→ 仅写入调用元数据审计
```

当前请求属性：

- `Accept: application/json`、`Content-Type: application/json; charset=utf-8`、`Authorization: Bearer <本机凭据>`、`Idempotency-Key: UUID`。
- HTTP POST 连接超时 8 秒、读取超时 30 秒、回包上限 1 MiB；禁重定向、禁缓存。
- 普通文本请求有 `model`、`messages`、`stream:false`。当前没有向普通聊天强加 `response_format: json_object`。
- HTTP 非 2xx 仅记录状态分类，不持久化错误 body；401/余额/429/超时/网络/服务端故障分别映射为用户可读错误。
- 失败时不自动重复真实请求，避免因网络半成功或费用不确定造成重复扣费。

### 4.5 当前已修的两次真实故障

1. **旧目录把 Auto 拦截为“当前预设模型不可用”。** 根因是仅在目录完全不存在时刷新，而不是在某个逻辑槽位没有精确映射时刷新。code 62 已改为“缺映射 → 无内容公开目录刷新 → 精确重解析”。
2. **服务返回正文却显示“服务返回内容无法安全读取”。** 根因是普通聊天复用了结构化任务解码器；若 `usage.cost` 精度低于一微元，旧 `BigDecimal` 精确换算抛错，导致整个成功正文被丢弃。code 63 改为正文优先：成本或 token 元数据无法精确转存时记为未知，正文照常显示；同时接受 typed text part 数组。

截图中两条一样的 USER 消息是两次已经提交的真实发送尝试：第一次因旧解码错误没有显示回复，第二次是重试。不能为了界面好看直接删除第一条，因为那会伪造“未曾外发”。

## 5. 上下文、附件与外发边界

### 5.1 上下文

`LocalContextBroker` 读取全部未删除的历史会话、ACTIVE Memory、ACTIVE Knowledge，在本地按关键词和更新时间排序，再截断外发：

| 来源 | 本地读取 | 外发上限 |
|---|---:|---:|
| 当前会话路径 | 当前叶路径 | 最多 24 条、28,000 字符 |
| Memory | 全部 ACTIVE | 最多 6 条 |
| Knowledge | 全部 ACTIVE | 最多 8 条 |
| 其他历史 | 全部可搜索会话 | 最多 8 条 |
| 检索来源合计 | 本地排序后摘录 | 18,000 字符 |
| 总外发上下文 | — | 42,000 字符 |

附件字节、工具输出、凭据、运行日志不会进入这个检索上下文。当前问题是每次发送都会扫全量 Memory/Knowledge/历史，数据量上来后会有明显性能与 token 估计问题；见第 10 节建议。

### 5.2 附件：当前代码与旧文档存在冲突

历史 [P3-G 附件合同](P3G_CONVERSATION_ATTACHMENT_LIFECYCLE_CONTRACT.md) 规定“加入会话不等于授权图片外发”，当时附件仅本地。

**当前 code 63 的 `NormalChatOpenRouterExecutor` 已经不同：** 当选择 Auto 或“图像/视频/PDF”槽位时，它会：

- 图片：从私有资产读取，单个用于外发的图片不超过 6 MiB；
- PDF：取第一页并转为 PNG；
- 视频：取本地封面并转为 PNG；
- 最多 4 个，组装为 OpenAI-compatible `image_url` 的 `data:<mime>;base64,...`；
- 历史会话/知识库中的附件不读取，也不外发。

这是架构与隐私合同的高风险矛盾。Claude 重构前必须作出明确产品决定：

1. 附件继续只本地，则删掉当前外发代码并在 UI 明确拒绝；或
2. 允许多模态外发，则把“用户点击发送即授权当前附件发送给实际 Provider/模型”的规则、大小/类型/预览、调用记录和失败恢复写成新的正式合同。

不能继续保持“代码会发送、旧文档写不发送”的状态。

## 6. JSON、ZIP 与导入/导出体系

### 6.1 知识 JSON：已完成且边界最清晰

入口文件：`domain/JsonKnowledgePortability.kt`、`data/AndroidJsonKnowledgePortabilityStores.kt`、`ui/JsonKnowledgePortabilityUi.kt`。

只接受 `nfai.knowledge.json` / `schemaVersion: 1`：

```json
{
  "format": "nfai.knowledge.json",
  "schemaVersion": "1",
  "exportedAt": "ISO-8601",
  "items": [
    {
      "id": "stable-source-id",
      "title": "...",
      "body": "...",
      "tags": ["..."],
      "scope": "GLOBAL | PROJECT",
      "projectRef": "... | null",
      "revision": "positive integer string",
      "hash": "64-char sha256",
      "source": { "kind": "...", "summary": "safe text only" }
    }
  ]
}
```

安全规则：严格 UTF-8、最大 768 KiB、最多 100 项、最大深度 24、字符串最大 12,000 code points、顶层/条目字段必须精确匹配、拒绝重复 key/未知字段/浮点和 NaN/Infinity/高敏内容/URL/路径/Authorization/API Key/Provider payload。

导入流程为：系统选择 → 私有副本 → 严格解析 → 逐条确认/跳过 → 通过正式 `KnowledgeDomain` 写入。导入永远重建本地 Knowledge ID；PROJECT 没有明确映射时降为 GLOBAL。导出仅允许用户选择 ACTIVE Knowledge，采用 `.part → fsync → 原子移动 → 回读 SHA-256`。

### 6.2 ChatGPT / Claude ZIP 导入

相关入口：`ChatGptExportJson.kt`、`ClaudeExportJson.kt`、`AndroidChatGptExportImportAssets.kt`、`AndroidClaudeExportImportAssets.kt`、相应 UI。

- ZIP 不应直接被普通 JSON 入口读取；先经过 ZIP inventory、路径遍历/压缩炸弹/大小/格式门，再私有 staging。
- 对话树映射只导入安全文本层；HTML、脚本、样式、tool/thinking/执行指令要文本化或跳过。
- Profile/personalization 只允许极少数白名单字段；绝不导入密码、cookie、MFA、付款、联系人、API Key、Provider endpoint、模型选择、联网许可、同步或账户安全设置。
- 从真实包得到的附件如果没有可验证的 message↔asset 身份关系，保持 `UNMAPPED_REJECTED`，不能靠文件名猜归属。
- 已有“人工精确关联未关联媒体”的受控路径：用户必须同时选匿名资产和目标消息；owner 重验大小、hash、MIME、单次归属和上限。

这组功能结构复杂且是高风险面。重构时请保留“先私有副本、再解析、逐项确认、owner transaction、receipt/provenance、可撤销”的链，不要改成“读 ZIP 后直接批量写会话”。

### 6.3 南枫AI 跨端交换

| 格式 | 用途 | 当前边界 |
|---|---|---|
| `nfai.exchange.v1` | Android 单个纯文本会话导出给 Desktop | 只允许当前已保存 CHAT、无草稿、无附件/工具/Project 的文本会话 |
| v2 完整工作区交换 | Project / Conversation / Knowledge / Memory / Relation / 附件账本的严格包 | `NfaiExchangeV2PackageReader` 是 Android bytes 唯一 preflight owner；非空本机拒绝覆盖 |

v2 包不允许 adapter/UI 自己解析 ZIP/JSON；必须在内存 strict preflight 后交 `WorkspaceExchangeV2AtomicRestoreOwner`。关键规则是 `LOCAL_TRUTH_PRESENT`：已有本机数据或待恢复记录时 fail-closed，不 merge、不覆盖、不偷偷写 receipt/provenance。

### 6.4 其他本地导入

- Markdown Knowledge：独立任务/私有副本/逐项确认，不能与 JSON Adapter 混用。
- PDF 文本知识：独立的私有资产、分页面解析、任务状态与确认链。
- HTTPS 网页文本：仅用户主动输入 URL 且经过确认的受控 Web Adapter；不是通用浏览器或后台抓取器。

## 7. 现有 UI 与设置

当前聊天主界面是 chat-first：顶部“对话/工作”，侧栏收纳搜索、置顶、最近和设置；普通聊天的 Composer 只保留模型逻辑槽位，不应堆叠 Provider/Key/同步等复杂开关。

模型设置至少包括：

```text
AI Providers
  OpenRouter / Qwen / DeepSeek 的已连接或待配置状态

Routing
  Auto Routing
  Automatic Fallback
  Quality Escalation
  Cross-model Review
  Cross-provider fallback（默认关）

Cost Limit
  Daily / Monthly（UI 与真正强制预算仍需统一）

Advanced
  Routing logs / Provider health / Model registry / Pricing / Cache statistics
```

重要约束：用户明确要求正常聊天在首次设置凭据后“点发送直接联网”，不得在每条消息前再增加确认弹窗、勾选或二次确认。附件边界现已明确：选择、预览与草稿仅本机处理；点击发送即授权把本次准确已提交草稿中仍存在的附件或必要解析结果发送给界面显示的当前 Provider/模型。切换 Provider 不得静默转发，删除附件、日志、统计与无关第三方均不得获得附件。

## 8. 测试、设备与交付事实

### 已有本轮证据

- code 63 的 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleRelease --offline --no-daemon` 已通过；Lint 0 errors。
- 新增回归：OpenRouter 正文在精细成本小数和 typed text parts 情况下仍成功解码。
- 正式 APK 使用同一 release 签名；OPPO 由 code 62 覆盖到 code 63，未卸载、未清数据；安装后 APK byte hash 与本机一致，冷启动成功。

### 不能冒充的证据

- 代码构建、单元测试、安装和冷启动 **不等于** 真实 Provider 对话完整成功。
- 不读取或导出 OPPO 私有数据、Key、实际对话正文；不会用它们做自动回归。
- 永久禁止 `connected*AndroidTest`（包括指定 serial 的变体）；它会枚举并可能影响主设备。
- Windows 验收、Google/Supabase 同步已经被用户明确跳过，不能作为当前阻塞项，也不能被写成已完成。

## 9. 已知问题与架构风险（请 Claude 优先处理）

### P0：发送状态机不完整

当前点击发送后立刻提交 USER message；Provider 失败时，消息没有一个可见、可恢复的 delivery state，也没有保存同一次 `Idempotency-Key`。用户重输并发送会得到两条相同消息，虽然这在事实层面正确，但体验很差。

**建议：** 设计持久化 `OutgoingMessageAttempt`：

```text
QUEUED → DISPATCHING → SUCCEEDED / FAILED_RETRYABLE / FAILED_FINAL / UNKNOWN_OUTCOME
```

- USER Message 只有一条；attempt 挂在该 message 上。
- 首次请求生成 idempotency key 并持久化；人工“重试”复用同一 key（前提是 Provider 支持），换模型则新 attempt、明确标注。
- 网络中断/进程重启后显示“结果未知，可能已送达”，不能静默重发。
- assistant 回复与 attempt 以同一事务或可恢复 receipt 关联；UI 显示失败原因和一个最小“重试”动作，而不是要求用户复制重发。

### P0：一个类承担太多职责

`NormalChatOpenRouterExecutor` 同时负责草稿提交、自动路由、目录刷新、附件转换、凭据、HTTP、回包解码、审计、Compare 输出和落库；其名字还只写 OpenRouter，实际支持三家 Provider。

**建议拆分：**

```text
ChatSendCoordinator             // 唯一状态机与事务编排
ModelResolver                   // logical slot → exact provider/model/capabilities
ContextAssembler                // 本地检索与 egress budget
AttachmentEgressPreparer        // 明确许可、转码、上限、hash
ProviderAdapter                 // 每家请求编码、HTTP、回复解码、错误映射
ResponseNormalizer              // provider DTO → PlainChatReply / Usage / Safety metadata
AttemptRepository + AuditWriter // 状态、幂等、去敏审计
```

### P1：普通聊天仍复用结构化 OpenRouter 代码

code 63 已绕开“费用小数让正文失败”的直接 bug，但普通对话仍调用 `OpenRouterJsonCodec`，而这个类本来服务结构化任务。未来任一 Provider 在 `choices`、content part、reasoning、tool call、usage 或 error envelope 上变形，仍可能造成耦合回归。

**建议：** 普通聊天建立独立的 `ChatCompletionResponseDecoder`，只把必要字段解析成标准 DTO；结构化任务使用独立 schema decoder。可选/未知计费字段只能影响账本，不得吞掉可显示正文。

### P1：上下文检索是全量扫描且用字符预算

`LocalContextBroker` 每次发送扫描全部 Knowledge、Memory 和历史。数据量大时会卡 UI/IO，且汉字、代码和英文的“字符数”不等于 token 数。

**建议：**

- 建本地全文索引/倒排索引或离线 embedding 索引；索引增量更新，不能每次全扫。
- 使用 Provider/model 的 token 估算器和上下文窗口预算；保留来源引用、截断理由、可见选中数量。
- 先保留当前会话最近窗口，再按相关性分配 Memory/Knowledge/历史预算；不要把所有命中都伪装成 system message。
- Context 不要无限跨所有会话默认外发；给用户一个可理解的“本次引用了哪些本地资料”的轻量投影。

### P1：模型目录、固定直连模型、价格和能力是三套事实

OpenRouter 有动态目录快照；Qwen/DeepSeek 使用硬编码 ID；UI 有逻辑名；路由有 fallback 列表；价格字段来自目录但不是统一预算真值。

**建议：** 统一 `ModelRegistry`：每一个可选模型都有 provider、endpoint、model_id、alias、能力、上下文、价格版本、健康状态、启用条件、替代策略和更新时间。Auto 必须输出 route reason；手动选择必须 exact，不自动改名。

### P1：多模态的产品协议尚未定稿

当前代码已可外发转码后的图片/PDF 首页/视频封面，旧合同却仍写 local-only。请先决定是否允许，然后统一：发送预览、Provider/model 可用性、文件大小、成本预估、失败与审计、导出排除、隐私设置。

### P2：导入/交换子系统复杂，入口可能碎片化

JSON Knowledge、Markdown、PDF、网页、ChatGPT ZIP、Claude ZIP、v1 单会话、v2 工作区均各有任务/receipt/provenance 逻辑。这是安全上正确的隔离，但 UI 容易让用户不知道“导入到哪里、是否已写入、能否撤销”。

**建议：** 保留不同 Adapter 的严格 parser，但提供一个统一“导入中心”：选择来源 → 私有副本 → 预检摘要 → 逐项确认 → 写入结果/可撤销记录。不要把协议合并为万能 JSON parser。

### P2：文档与代码已出现历史漂移

示例：旧 P3-G 写附件不外发，当前发送代码会外发；早期 P6-G 写未真实 Provider，当前已接普通发送；当前 Handoff 已更新而许多历史证据仍保留旧状态。

**建议：** 建一份小而强的“当前运行时事实表”，由 CI 校验：版本、端点、逻辑槽位、外发能力、数据保存项、禁用能力、真实验收状态。历史合同继续归档，但不能当首页事实。

## 10. 给 Claude 的推荐重构顺序

1. **先冻结真实行为。** 为 code 63 的普通文本发送写 provider mock 合同：成功、200 但价格异常、typed content、401、402、429、超时、断网、1 MiB 超限、进程重启、重复点击。
2. **引入发送 Attempt 状态机。** 先不动 Conversation 数据结构，把 attempt 表/状态、幂等 key、失败 UI 投影接在现有 USER message 上。
3. **拆 `NormalChatOpenRouterExecutor`。** 先抽 `ProviderAdapter` 和 `ChatCompletionResponseDecoder`，再迁移 OpenRouter/Qwen/DeepSeek；保留现有 endpoint 与加密凭据实现。
4. **统一 ModelRegistry。** 每次请求和历史消息保存实际 provider/endpoint/model_id/alias/reasoning/timestamp/token usage；Auto 的实际落点必须可见。
5. **明确多模态。** 在实现更多文件类型前先定外发许可、数据最小化和失败语义。
6. **优化 Context。** 引入本地索引和 token-aware budget，但不改变“模型都能访问同一用户库、只在外发前最小化摘录”的产品原则。
7. **最后整合导入中心。** 保留 JSON/ZIP/workspace 的严格协议和原子写入，不把安全 Adapter 合并成一条危险的万能导入链。

## 11. 不可突破的约束

- 用户提供的 API Key、token、私钥只能在本机受控运行时使用；禁止写进源码、文档、日志、构建物、截图、GitHub、远程仓库、Issue、PR 或第三方服务。
- 正常纯文本聊天在用户完成首次本机配置后，点击发送直接联网；不得增加逐条确认弹窗。
- 附件是否可外发必须由新的明确产品合同决定，不能靠旧 local-only 文案掩盖当前实现。
- OPPO 主设备只能同签名正式包 `pm install -r --user 0` 覆盖；绝不卸载、清数据、Debug/仪器测试、`connected*AndroidTest`、数据库注入或读取私有业务数据。
- Google/Supabase 同步与 Windows 验证当前均跳过；不要在此次架构重做里擅自启用。
- 不要把构建、单测、安装或调用记录结构当作真实模型业务闭环。

## 12. 关键文件索引

| 主题 | 关键文件 |
|---|---|
| Provider 端点、预设、配置 | `domain/ModelService.kt`、`data/ModelServiceStorage.kt` |
| 普通发送 | `ai/NormalChatOpenRouterExecutor.kt`、`ui/ConversationFoundationViewModel.kt` |
| HTTP 传输 | `ai/OfficialProviderChatTransport.kt` |
| OpenRouter 目录 | `domain/RegistryVerification.kt`、`data/OpenRouterRegistryCatalog.kt` |
| 普通/结构化回包解码 | `ai/OpenRouterOfflineContracts.kt` |
| 自动路由 | `domain/ChatRoutingPolicy.kt`、`domain/P6GModelRouter.kt`、`domain/ChatModelRouting.kt` |
| 上下文 | `domain/LocalContextBroker.kt` |
| JSON 知识导入 | `domain/JsonKnowledgePortability.kt`、`data/AndroidJsonKnowledgePortabilityStores.kt` |
| ChatGPT/Claude 导入 | `domain/ChatGptExportJson.kt`、`domain/ClaudeExportJson.kt`、`ui/ChatGptExportImportUi.kt`、`ui/ClaudeExportImportUi.kt` |
| 工作区 v2 | `domain/WorkspaceExchangeV2AtomicRestore.kt`、`domain/WorkspaceExchangeV2Export.kt`、`data/AndroidWorkspaceExchangeV2AtomicRestoreStore.kt` |
| Room | `data/local/NanfengAiDatabase.kt` |
| 隐私与存储占用 | `domain/PrivacyData.kt`、`data/AndroidPrivacyDataManager.kt`、`ui/PrivacyDataUi.kt` |
| 当前变更/设备证据 | [CURRENT_HANDOFF.md](CURRENT_HANDOFF.md) |

---

本档案故意不含任何 API Key、密文、对话正文、真实原始回包、设备私有数据、文件路径或外部账号资料。
