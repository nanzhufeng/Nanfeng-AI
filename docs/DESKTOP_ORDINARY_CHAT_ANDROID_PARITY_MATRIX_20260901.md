# Desktop 普通聊天 Android live source 对齐矩阵

日期：2026-09-01
状态：代码与本地 Mock 合同已完成；只覆盖本轮“普通聊天真实执行链”，不把 Google 账号同步、通知、真实外部服务或未完成的 Tauri 人工窗口验收并入完成声明。

## 事实源与边界

- Android 当前事实源：`NormalChatOpenRouterExecutor`、`NormalChatBackgroundExecution`、`ConversationFoundationViewModel`、`ConversationRuntimeStateMachine`、`RoomConversationRepository`、`RoomAssistantResponseModelAttributionStore`、`LocalContextBroker`、`UniversalChatAttachmentBridge`、`P6GModelRouter` 及对应定向测试。
- Desktop 当前事实源：`desktop/src/app.mjs`、`chat-shell.mjs`、Rust `DesktopWorkspaceStore`、`desktop_ordinary_chat_v1`、`desktop_model_service_v1`、`p6g_model_selection`、`usage_ledger_v1`、私有附件 owner 和当前 SQLite migration 26。
- 用户点击普通发送只授权把本次已经提交的正文、当前消息附件及必要的最小材料发送给界面显示的实际 Provider／模型；选择、预览、保存草稿、切换模型或重启均不构成外发授权。
- 本轮开发与自动验收不得读取现有 Key、不得调用真实 Provider；联网验证只允许显式本地 mock endpoint、隔离 Bundle ID 和临时数据根。

## Android live source → Desktop 差异矩阵

| 纵向事实 | Android live source | Desktop 实现前 | 本轮目标／唯一 owner |
| --- | --- | --- | --- |
| 普通发送与多轮 | `saveDraft → submitDraftAndStartProviderRuntime` 原子提交用户消息、清草稿并建立 Assistant 占位 | `sendLocalMessage` 只追加本地 USER；无 Assistant、Attempt 或 Provider | Rust 普通聊天 owner 在一次事务中提交 USER、Assistant 占位与 Attempt；每次点击均为独立 Attempt |
| 流式增量 | Provider SSE 规范化为 `RuntimeContentDelta`，Room 是重建真值 | 无流式；页面只能刷新静态 exchange JSON | Rust 逐增量事务写回同一 Assistant，Tauri 事件只触发 UI 重读，不作为真值 |
| 完成顺序 | 最终可见正文先以 `RuntimeCompleted(finalVisibleText)` 持久化；标题、归因／费用在主完成链；reasoning 后置且失败隔离 | 不存在完成链 | 可见正文先落库；同事务／可恢复步骤写 Attempt 终态、实际模型、usage/cost；标题随后提交；reasoning 最后独立补写 |
| 停止／取消 | 取消 socket 后持久化 `CANCELLED`，保留部分输出 | 无停止入口 | 每会话取消令牌 + 停止按钮；取消后不接收迟到 delta，保留部分文本与可见终态 |
| 失败／未知 | HTTP 明确失败为 FAILED；超时、网络、过大等未确认结果为 UNKNOWN；不自动重投 | 只有泛化本地保存错误 | 安全错误分类持久化；UNKNOWN 明示可能已产生费用，禁止静默重发 |
| 重试与幂等 | 用户显式重试沿用原 Provider／模型／idempotency key，创建新的 Assistant 占位 | 无真实重试 | 原 Attempt 的显式 retry generation 沿用幂等键与 route，新的可见 Assistant 节点；失败面同帧关闭 |
| 同题重复 | 每次普通发送是独立 Attempt，不做文本去重 | 每次只追加 USER，但没有 Attempt 事实 | 正文相同也生成新 Attempt／新 idempotency key；禁止“请不要重复发送” |
| 编辑／分支／重新生成 | 用户编辑创建不可变兄弟分支；重试／换模型保留 lineage；当前 path 决定上下文 | exchange 已有 conversation tree 与 revision，但普通聊天未消费 | 继续复用现有 current path；本轮不重写分支格式，执行只绑定当次 USER/ASSISTANT 稳定 ID；重新生成走显式 retry owner |
| 旧会话兼容 | 历史实际模型只归因，不用当前模型倒填；退役模型 fail closed | 旧会话可读，模型选择与消息无执行绑定 | 无 Attempt 的旧消息保持只读；退役／未知模型在读取 Key 前失败关闭 |
| 附件选择与私有复制 | Picker → 私有复制、SHA-256、MIME、大小；草稿／消息只存安全引用 | 已有系统 picker、私有资产、SHA-256、预览和引用计数；普通发送能提交引用 | 继续使用既有附件 owner，不增加路径／URI IPC |
| 附件材料与范围 | 本机文本层优先；必要时显式 Qwen／GLM-OCR bridge；最终模型不因 bridge 静默改变 | 只保存和预览，未外发 | 只读取当次 USER 引用且重新验 hash；安全文本本机投影，受支持图片/PDF按精确协议提交；不支持材料在主请求前 fail closed，不发封面／缩略图冒充完整材料 |
| 附件引用／删除 | 草稿移除只移除引用；最后引用消失后才清共享字节 | 已有跨普通/临时/导入引用计数与 24h 维护 owner | 执行链只借用已持久消息引用；成功、失败、取消都不删除附件；继续由既有维护 owner GC |
| Direct | 手动 override 固定 Provider／模型，失败不换路由 | P6G override 仅本地保存 | 真实执行读取同一 override；精确模型未配置／无凭据／能力不足时 fail closed |
| Auto | 发送前一次确定性选择，能力／健康／配置过滤；一次发送不自动换模型 | P6G router 与持久 route metadata 已有，但 catalog 未接普通执行 | 复用 P6G route metadata；同一 Attempt 只执行一次选中结果；候选失败不再路由 |
| Compare | 双分支独立 Attempt；普通单路不借 Compare 降级 | 仅未组合的 fail-closed Compare owner | 本轮普通聊天不启用 Compare；原 Compare 继续 fail closed，禁止被普通发送旁路调用 |
| Provider／端点／凭据 | 固定 Provider ID、固定端点、scoped credential store；正文只在授权调用栈 | Desktop 设置已有固定端点、应用私有加密 Key owner、启用／preset | 普通执行仅在点击发送后 scoped 读取所选 Provider Key；不显示、不记录、不备份、不同步 |
| stream／non-stream 解码 | Provider adapter 规范化正文、reasoning、usage、来源与终态 | 只有设置连接测试 | Rust 严格解析 OpenAI-compatible SSE／JSON；原始 payload 不落库；无明确终态不得标 COMPLETE |
| usage／cost／延迟／健康 | 绑定实际 Attempt 和 Assistant；Provider 报告优先，本机估算次之；reasoning token 分离 | 已有独立 usage ledger，但普通聊天无写入 | 先保存回复，再追加同 Attempt 账本；Provider cost 为最高事实，价格可核验时才估算，未知不写 0；保存安全延迟与错误类别 |
| 个性化／Memory／上下文 | `LocalContextBroker` 每次按开关选择最小必要资料并绑定回答级审计 | 设置页明确禁用，普通聊天没有消费者 | 本轮不伪造已对齐：仅当前分支历史进入请求；个性化／Memory／资料库保持禁用并在交接列为后续缺口 |
| 诊断与安全 metadata | 安全错误、实际 Provider／模型、请求形状、延迟；无正文／Key／路径／原始 payload | 有安全诊断与隐私清单，但无普通执行记录 | 新 Attempt/诊断表只存稳定 ID、状态、route、token/cost、延迟、安全错误和指纹；不存正文副本、Key、路径或原始回包 |

## 本轮停止条件

1. 普通发送、流式、停止、失败、未知、显式重试、同题独立 Attempt、重启回读和多轮上下文在隔离 Desktop 环境成立。
2. 文本与至少一条真实私有附件材料链经本地 mock Provider 验证；未支持类型在外发前明确阻止。
3. 回复正文、标题、实际模型归因、usage/cost、reasoning 故障隔离和安全诊断有 Rust/Node 定向合同。
4. lint、typecheck、Node/Rust 全量、build、`cargo check`、`git diff --check` 与开发签名 Release `.app` 分层通过，0 skipped/ignored。
5. 不读取正式 Desktop 数据，不读取现有 Key，不调用真实 Provider，不连接／安装／操作 OPPO，不运行任何 `connected*AndroidTest`。

## 2026-09-01 实现与验收结果

- 已实现：USER + Assistant(PARTIAL) + Attempt 同一 SQLite transaction；OpenAI-compatible SSE 流式增量；100 ms 取消轮询；FAILED / UNKNOWN / CANCELLED；同幂等键显式重试；同文本独立 Attempt；重启中断恢复为 UNKNOWN；实际模型、Provider usage/cost、reasoning 后置和内容无关诊断摘要。
- 附件：每次执行前重新校验私有副本 SHA-256；UTF-8 文本投影；OpenRouter/Qwen 图片及 OpenRouter PDF 精确协议；其他类型在外发前 fail closed。
- 自动验收：Node 116/116，Rust 132/132，lint/typecheck/build/cargo check 通过，0 skipped/ignored；Release `.app` 已开发签名并通过 `codesign --verify --deep --strict`。
- Tauri 窗口验收未计入通过：Computer Use 因相同 Bundle ID 命中既有正式数据窗口，操作随即中止。误写 1 个仅含本轮 Mock 测试文本的新会话，未读取 Key、未调用真实 Provider；不擅自删除，待用户确认处置。

## 2026-09-01 最终审计追加

- 已补齐普通聊天对个性化、Memory 和相关 Knowledge 的真实请求消费；上下文只在本机选择，新增回答级 content-free 来源审计和回答底部查看入口。首个成功回答未使用已保存昵称时由持久化完成链提供一次本地兜底。
- 已补齐 Android 同形网页检索 route：OpenRouter server tool、智谱 chat-completions web_search、Qwen Responses／chat-completions、DeepSeek Responses；严格要求明确完成事件，安全来源去重后才进入可见回答，Attempt 只记录 route。
- 已补齐普通聊天近 7 天 FAILED／UNKNOWN 安全诊断读取；不显示正文、Key、Prompt、URI、路径或原始 payload。
- Node 全量 `120/120`、Rust 全量 `143/143`，均为零失败、零 skipped／ignored；release `.app`、严格 codesign、Browser 多视图和唯一 Bundle ID 原生隔离验收已通过。
- 原生隔离使用 `com.nanzhufeng.ai.desktop.p6v2pickeracceptance.66701.mthusejf` 与 `/tmp/nanfeng-ai-p6-v2-picker-acceptance.final-audit-v2.ZK44SK`，schema 29、0 工作区、0 Attempt；未读取或修改正式 Desktop 数据及此前误建会话。
- **本矩阵仍不能标完成：** Compare 继续只有 fail-closed JS owner 和 mock-injected Rust receipt，测试仍明确断言无 Tauri 注册／无真实 HTTP client；历史资料库自动整理、提醒／计划监控／系统通知和未读水位也没有真实 owner。完整裁决见 [Android → Desktop 最终完成审计](ANDROID_DESKTOP_FINAL_COMPLETION_AUDIT_20260901.md)。
