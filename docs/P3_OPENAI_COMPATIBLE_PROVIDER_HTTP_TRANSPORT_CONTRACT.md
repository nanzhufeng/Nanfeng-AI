# 南枫 AI P3 OpenAI-compatible Provider HTTP Transport 合同

日期：2026-08-15  
状态：未注册、默认 fail-closed 的 HTTP transport infrastructure；只允许内存 mock 验证

## 范围

本合同在既有 `provider-transport-boundary-v1` 之下，为未来**另行明确授权**的 OpenAI-compatible `/chat/completions` 请求准备 core。它不读取 `ProviderCredentialStore.loadCredential()`，不包含 `HttpsURLConnection`、OkHttp 或任何真实 client 实现，也不接入 AppContainer、ViewModel、发送按钮、Conversation runtime、Usage Ledger、附件 egress 或数据库。

```text
future authorized credential broker (本阶段不存在)
→ ProtectedProviderCredentialHandle (opaque)
→ OpenAiCompatibleProviderTransport
→ injected HTTP client (默认 Disabled；本轮仅 in-memory fake)
→ existing ProviderTransport normalized events / safe metadata
```

## 固定端点与严格请求

- 仅 `OpenAiCompatibleProviderPreset` 可以选择端点；当前只有 `openrouter → https://openrouter.ai/api/v1/chat/completions`。调用方不能传 URI/host/path 或额外 headers。
- 请求恒为 `POST`，header 白名单只有 `Accept`、`Content-Type`、`Idempotency-Key`；Authorization 不在 request object 中。
- Body 严格为 `model`、一条 `user` text message、`stream`、`temperature` 和可选 `max_tokens`。不接受附件、图片、工具、任意 JSON 扩展、system 注入或不受控 headers。
- `ProtectedProviderCredentialHandle` 只暴露 provider handle 与安全 reference；没有 Key 字节、字符串 getter、解密或 Authorization API。将来实际 credential broker 必须另获授权并在本边界外实现。

## 响应、取消与安全投影

- HTTP response bytes 与 SSE/JSON parser 都是调用栈内的暂态数据。终态结果只复用 P3 的 execution/invocation/attempt/fingerprint、provider/model、终态、安全错误码与事件数；没有正文、raw payload、HTTP status、Key、URI、附件、token 或费用。
- SSE 只接受 `data:` JSON 与唯一 `[DONE]`；JSON 只接受一条 `choices[0].message.content`。格式、content type、模型标识或终态异常统一为 `PROVIDER_RESPONSE_MALFORMED`。
- 执行前／响应后发现取消均收敛为唯一 `TRANSPORT_CANCELLED`，不会继续解析或投影文本。非 2xx、timeout、network 只映射至既有安全错误类别。
- 默认 `DisabledOpenAiCompatibleHttpClient` 返回 `DisabledNoNetwork`，最终为无事件、无假回复的 `TRANSPORT_DISABLED_NO_NETWORK`。无重试、无后台续发。

## 验证与非结论

定向 JVM 合同测试使用内存 fake client 覆盖固定端点／严格 body、SSE、non-stream JSON、错误分类、取消、opaque credential handle、metadata 白名单和默认 fail-closed；并回归 P3 boundary/P3-I/P1/P0。它不执行真实或 loopback HTTP，不读取/打印/迁移 Key，不证明 Provider 可用、模型响应、费用、附件外发、UI、设备或发布。
