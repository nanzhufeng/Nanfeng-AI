# P3 真实执行外部门去内容化复核（2026-08-21）

## 结论

§17 P3 的最靠前未关闭退出项仍是**真实**流式、停止/失败/重试/换模型、部分计费、真实用量/成本可见追踪、长会话实测及 OpenRouter 充分性判断。当前仓库没有可在本机诚实补闭这些退出项的 production consumer：普通聊天没有真实 egress owner，默认 transport 也不会制造回复。因此本轮停止 P3 扩展，不以 fixture、数据库注入、假回执、启动参数或本地 UI 状态代替真实服务证据。

## 已证实（只读、去内容化）

- `P3_REAL_TEXT_EXECUTION_PREFLIGHT_CONTRACT.md` 与 `RealTextExecutionPreflightOrchestrator` 只调用 `credentialPresence()`，并要求已验证目录、完整价格和费用确认；它不读取 Key bytes、不会写 Usage/Room 或启动 transport。
- `P3_PROVIDER_TRANSPORT_BOUNDARY_CONTRACT.md` 的 production 默认值为 `DisabledNoNetworkProviderTransport`。源码只返回 `TRANSPORT_DISABLED_NO_NETWORK` 安全终态，零事件、零假回复、零 HTTP。
- `AppContainer` 只为 P2-M 特殊合成文本路径构造 preflight/coordinator；默认 coordinator ports 在 transport、runtime receipt 与 Usage Ledger 之前拒绝。该 bridge 的结果被既有 P2-M executor 忽略，普通聊天仍没有 production owner、ViewModel、Composer 或 HTTP 注册。
- `DirectExecutionProductionComposition` 只是未被 UI 引用的构造 seam；其 composition 同样没有 reservation/runtime/transport consumer。它不是 P3 真实执行或 Provider 已配置的证据。

## 本轮未做与外部门

- 未读、解密、探测、导出或记录 Android 应用私有凭据；未访问 Keychain、未构造 Authorization、未发 HTTP、未发行/消费 nonce，未创建 Provider Attempt、Token、费用、Candidate 或 Knowledge。
- 未运行设备、模拟器、instrumentation 或 `connected*AndroidTest`，未触碰 OPPO 或禁止 AVD。
- 本轮没有重跑 P3 Gradle 合同：另一个项目的 Gradle 测试仍在运行，按“无 Gradle 争用”门禁不并发启动本项目 Gradle。既有 P3 自动合同是历史本地证据，不能替代本轮的真实服务证据。
- 要解除 P3 外部门，仍须用户明确授权的非敏感输入、可合法的凭据 presence 检查、冻结目录/价格、当次可见费用与外发确认，以及一次受控真实请求后的安全 receipt/Usage/UI 或设备 readback。成功或失败均不得自动重试。

## 排程结论

P3 本轮不新增用户功能、常驻入口或设置条目；“设置 → 功能审阅”无需变更。P3 的局部本地合同继续保留，但不称为 P3 或 P0–P11 总控完成。下一可本机候选是 P11 Gradle dependency verification metadata，前提是确认没有其他 Gradle 争用；截至本复核尚未生成任何 metadata 文件。
