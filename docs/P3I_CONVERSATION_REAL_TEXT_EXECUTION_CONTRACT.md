# 南枫 AI P3-I ConversationRealTextExecution 合同

日期：2026-08-15  
状态：P0 底层合同与持久化锚点；未接入 UI、Composer、Provider、凭据或 HTTP

## 目标与范围

本合同只冻结普通 Conversation 的未来真实文本执行事实。它不改变现有 P2-K、P2-M、P3-B/P3-C、P3-G 或 P6 的 owner，也不允许任何现有入口发送网络请求。

```text
future explicit-confirmation owner
→ ConversationRealTextExecutionRequest
→ durable execution receipt boundary
→ future real-text transport adapter
→ existing Invocation Ledger / Conversation Runtime owners
```

本阶段唯一可运行的 transport 是 `DISABLED_NO_NETWORK`。它不读取 Key、不构造 Authorization、不包含正文且不会访问网络。

## 跨端规范化事实

Android Room 与 Desktop SQLite 都使用 `conversation-real-text-execution-v1`，且只持久化：

| 字段 | 语义 |
|---|---|
| executionId / idempotencyKey | 一次逻辑执行及稳定重放键 |
| requestFingerprint | 已确认范围的 SHA-256；不是 Prompt 正文 |
| conversationId / userMessageId / assistantMessageId | 同一 Conversation 树的将来绑定位置 |
| invocationId / attemptId | 对应 Invocation 与物理 Provider Attempt 的预分配稳定 ID |
| state / createdAt / updatedAt / terminalAt | `PREPARED → RUNNING → SUCCEEDED|FAILED|CANCELLED` 的可重建终态 |
| safeErrorCode | 仅受限大写错误码；成功、准备和运行中必须为空 |

不得持久化 Prompt、Assistant 原始响应、Key、Authorization、URI、path、附件、附件内容、Provider payload、token、费用、余额或 Provider 返回的原始错误正文。Usage/费用 migration、对账与附件 egress 都不属于本合同。

## ID、幂等与终态

- 一次 request 的 Conversation、USER、ASSISTANT、Invocation、Attempt ID 均为必填且不可在同一 request 内复用。
- `idempotencyKey` 唯一；相同键、相同 request fingerprint 与 ID 集合回读为 `REPLAYED`，任一不同为 `CONFLICT`，不得覆盖旧事实。
- `PREPARED` 只表示已建立本地事实，`RUNNING` 只表示未来 transport owner 已开始；二者不是 Provider 成功或已发送的证明。
- `FAILED` 与 `CANCELLED` 均须保留安全错误码；所有终态不可改写，也不能转为另一终态。
- 重启只读取同一 receipt，不自动重试、不续发、不重建 assistant 正文。将来实际执行器须把 runtime event、Invocation Ledger 与本 receipt 置于可恢复的一致性边界。

## 平台边界与验证

- Android：Room Schema 29→30 只追加 `conversation_real_text_executions`；未修改已有表或既有数据。
- Desktop：独立 app-private `conversation-real-text-execution-v1.sqlite3`；没有 Tauri command、IPC 或 UI consumer。
- 定向合同覆盖相同 request 重放、冲突、FAILED/CANCELLED 不可逆、重开读回与持久字段白名单。它们不执行 HTTP，也不代表真实 Provider、Usage、附件外发、UI 或真机验收。

## Android test transport adapter

- `RoomConversationRealTextTestExecutionAdapter` 只供 JVM/Robolectric 合同使用，未注册给 AppContainer、ViewModel 或任何 UI 入口。
- 它只接受脚本化的 `TextDelta / Completed / Failed / Cancelled` 规范化事件；没有 HTTP、Key、Provider、P2-M nonce、Usage 或附件输入。
- 每次显式 start 以一个 Room 事务同时建立 `RUNNING` receipt 与相同 `InvocationId` 的 `PARTIAL` assistant。receipt 中的 `AttemptId` 与 message 的 invocation link 共同构成同一物理 Attempt 的可读绑定。
- 重建 adapter 仅回读 `RUNNING` receipt，不自动消费脚本、不自动重试；终态仍只能由明确的规范化终态事件写入。
