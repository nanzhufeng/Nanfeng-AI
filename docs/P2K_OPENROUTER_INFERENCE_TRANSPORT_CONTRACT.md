# 南枫 AI P2-K OpenRouter 推理传输与强制门禁合同

日期：2026-08-12  
状态：已实现本地传输合同与 loopback/fake 验证代码；生产真实推理仍未授权，默认阻止

## 1. 唯一目标、所有权与禁止项

```text
ConfirmAiRequest（逐次确认、价格版本提示）
→ RunAiTaskUseCase（统一预检；BLOCKED 无 Attempt）
→ OpenRouterInferenceAdapter（OpenRouter 专有请求、重试、响应投影）
→ OpenRouterInferenceTransport
  ├─ OfficialOpenRouterInferenceTransport（固定官方 HTTPS POST，默认不可达）
  └─ test-only fake / 127.0.0.1 loopback（本地合同验证）
→ InvocationRepository（安全运行元数据）
```

- `OpenRouterInferenceAdapter` 是唯一能构造推理 HTTP 请求的 Adapter；Provider 原始 JSON、HTTP 状态与重试只停留在其中。UI、Room、SavedState、日志、导出均不消费 Provider 原始事件或正文。
- 正式地址固定为 `POST https://openrouter.ai/api/v1/chat/completions`，`stream=false`、`temperature=0.2`、`response_format=json_object`，连接/读取超时为 8 s / 30 s，响应上限 1 MiB，禁止重定向。
- 生产容器固定注入 `OpenRouterEgressPolicy.Disabled`。因此没有本阶段之后的用户明确授权路径时，Adapter 在读取 Keystore、构造 Authorization、创建 HTTP 请求之前返回 `ProviderEgressNotAuthorized`。
- P2-K 不开放真实文字/图片调用、Key 测试、图片编码、系统图片分享、拍照、Knowledge 搜索/图谱/编辑管理/导入、账号、同步、Hub、Agent 或图标改动。

## 2. 请求前的全量强制门

一次 OpenRouter Task 必须同时通过下列条件；任一缺失均由 `RunAiTaskUseCase` 结构化保存为 `BLOCKED TaskRun`，没有 `ProviderAttempt`：

1. Task 明确为 `OPENROUTER`，含明确实际 Model；当前 Registry Snapshot 已验证且含同一模型。
2. `EgressConsent` 与当前 Provider、Model、草稿指纹完全一致；同一 Task ID 在同一运行时只能使用一次。
3. 模型支持 Text 与 Structured Output；有附件时还要先通过 Vision 能力门。P2-K 尚不编码附件，带附件文本请求会被内容安全门阻止。
4. `CostDisclosure` 与当前模型的 price version / currency 一致；未知估算是 `null`，不是免费或零成本。
5. 请求文本非空、清洗后不超过 12,000 字符，并拒绝控制字符、疑似 Authorization/API Key/Bearer、`sk-`、中国手机号与邮箱形态。只把清洗后的短文本留在调用栈内。
6. Credential handle 可用；Key 仅在通过其余门后由 Keystore 读入 `CharArray`，用后清零。
7. 当前 egress policy 已被未来用户授权阶段显式启用；P2-K 应用配置恒为关闭。

## 3. HTTP、重试、响应与隐私

- `Authorization: Bearer …` 只在 transport 设置请求头的内存瞬间存在；不进入 `OpenRouterTransportRequest`、Room、SavedState、日志、测试报告、导出或文档。请求 JSON 和 Provider 响应也只在当前调用栈内。
- 使用 Task ID 作为 `Idempotency-Key`。只对 `408`、`429` 与 `5xx` 最多重试一次，保持同一 Key；`401`、`402`、`413`、畸形响应、取消和网络失败不盲目重试。
- HTTP `401/403`、`402`、`408/504`、`413`、`429`、`5xx` 分别映射为鉴权、余额、超时、上下文溢出、限流、服务不可用；取消为 `CANCELLED`，且有已开始 Attempt。
- Provider 成功 JSON 只提取 request ID（不持久化）、usage、cost 和结构化 `content`。`content` 必须再投影为长度受限的 `title` / `body` Candidate；未知字段和原始正文不进入 Ledger。Token/费用 `null` 不等于 `0`。
- 真实/已开始调用按 `TaskRun → ProviderAttempt → Generation → Validation` 记录。预检阻止严格为 `BLOCKED` 且 Attempt 为空；Provider 原始响应、完整 Prompt、Key、Authorization、原图和附件正文没有对应持久化字段。

## 4. 本地验证与供应链

- 新增 `P2KOpenRouterInferenceTransportContractsTest`：默认生产门禁、无 Attempt、无 Key load；127.0.0.1 真正 POST 的请求头、内存 Authorization、JSON 与响应投影；401/402/408/413/429/5xx、超时、取消、畸形响应、上下文溢出、重试与同一幂等 Key；不安全文本与缺少费用提示的预检阻止。
- loopback stub 只绑定 `127.0.0.1`，不会访问 OpenRouter 官方推理端点；fixture 使用 `test-key-not-real`，不读取本机真实 Key。
- 没有新增网络库。实现使用 Android/JDK 既有 `HttpsURLConnection`，无新增许可证、传递依赖、遥测或远程组件。替代方案是 OkHttp；当前不采用以避免增加供应链和日志拦截器边界，后续若为 HTTP/2、取消或可观测性确有证据需求，再单独评估精确版本、Apache-2.0 许可证、传递依赖和外发面。
- 已执行证据：Android Studio JBR 21.0.10 下 `testDebugUnitTest` 56 项（含本类 4 项）通过，`lintDebug`、正式签名 Debug/Release 构建与 v2/v3 验签通过；API 35 模拟器同签名覆盖/冷启动只验证未授权状态，未访问官方推理端点。

## 5. 可见状态、验证等级与停止条件

- 模型设置明确显示“传输准备就绪 · 真实服务尚未授权”。它不表示 Key 可用、真实模型可用、请求已发出、额度可用或真实服务成功。
- P2-K 的 API 35 只允许验证该未授权状态和门禁，不能点击或绕过以触发官方推理。
- 真实推理必须由后续独立任务取得用户针对本机测试 Key、额度、非敏感资料和真实外发的明确授权后才能启用，并分别复验文本、图片、Token、费用与真机；本合同和 loopback 证据不能替代。
