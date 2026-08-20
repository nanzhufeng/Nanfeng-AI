# 南枫 AI P3 Provider Transport Boundary 合同

日期：2026-08-15  
状态：纯领域、未注册、无网络的将来 Provider transport 边界

## 目标与范围

本合同只为未来一次另行获得用户明确授权的真实文本请求准备类型化、可配置的 provider/model 边界。它不改变 P3-I receipt、现有 scripted transport、Conversation runtime、P0 Usage Ledger 或 P1 附件资格／授权 owner。

```text
future explicit-confirmation owner
→ ephemeral ProviderTransportRequest(provider/model/text)
→ normalized stream events + safe terminal metadata
→ future adapter (本阶段不存在)
→ existing P3-I / runtime / ledger owners (本阶段不接线)
```

当前唯一 production transport 是 `DisabledNoNetworkProviderTransport`：不读取 Key、不构造 Authorization、不含 endpoint、不访问网络、不发事件，也不生成任何假回复。

## 类型与隐私边界

- Route 只含受限 `providerHandle` 和 `modelId`；可以配置，但不含 URI、endpoint、credential、Key、Authorization 或 provider payload。
- Request 持有一次性内存文本及既有 P3-I execution/invocation/attempt/fingerprint handle。文本只可在未来 adapter 的本次调用栈中存在，绝不写入 receipt、runtime metadata、日志或结果。
- Upstream adapter 只能输出 `StreamStarted`、`TextDelta`、`Completed`、`Failed(sanitized signal)`、`Cancelled`。它不得把原始异常、响应 body 或 provider event payload 交给 Conversation owner。
- Safe terminal metadata 只保留 P3-I 绑定 ID、指纹、requested/actual provider/model、终态、安全错误码与规范化事件数；没有输入／输出文本、Key、URI、path、附件、token、费用、原始状态码或原始错误。

## 规范化、错误与取消

- 已建立的流必须以单次 `StreamStarted` 开始，随后零或多个 `TextDelta`，并以单次 `Completed`／`Failed`／`Cancelled` 终止。HTTP/解析等尚未建立流的安全失败可直接终止；其余乱序或重复控制事件收敛为 `TRANSPORT_PROTOCOL_VIOLATION`，不会泄露上游资料。
- 401/403/402/429/408/504/5xx 和受限失败种类分别分类为鉴权、授权、额度、限流、超时、服务不可用等安全码；没有自动重试、后台续发或故障降级为假回复。
- 显式取消在任意阶段都生成唯一 `TRANSPORT_CANCELLED` 终态；之后的上游事件只能读回相同安全结果，不能再投影文本。

## 非范围与验证

不得接入 AppContainer、ViewModel、Composer/发送按钮、UI、真实 Provider、HTTP、Key、P2-M nonce、附件读取或外发、Usage consumer、Room/SQLite migration。`ScriptedConversationRealTextTestTransport` 和 `RoomConversationRealTextTestExecutionAdapter` 保持原样，仅作兼容回归。

定向合同测试覆盖：类型化 route/request、local stub 的流事件正规化、错误分类、取消、乱序 fail-closed、metadata 字段白名单与 disabled 默认行为；另运行 P3-I、P1 egress、P0 ledger 既有回归。所有测试均为本地 JVM/Robolectric，不证明真实 Provider、费用、附件外发、UI 或设备验收。
