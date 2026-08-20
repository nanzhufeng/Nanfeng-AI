# 南枫 AI P2-L 真实服务验收准备合同

日期：2026-08-12  
状态：已实现离线合同、合成夹具、DryRun 和单次令牌；真实 OpenRouter 推理仍未授权且生产 egress 保持 `Disabled`

## 1. 目标、停止线与唯一链路

P2-L 不是 P2 终点，也不是一次真实调用。它只将未来真实验收所需的事实冻结为可审计的离线 `RealServiceRunSpec`，使获得用户授权后只需受控执行指定的一次 RunSpec。

```text
合成夹具 + 已验证 Registry Snapshot + RunSpec + 一次性 Consent 指纹
        ↓
RealServiceDryRunPreflight（纯本地、无网络、无 Authorization、无 Key bytes）
        ↓
可见安全报告 + app-private 单次 nonce 状态机
        ↓
未来、独立授权任务才可接入 P2-K Adapter（当前不存在该连接）
```

- 本阶段绝不访问 `chat/completions` 或其他官方推理端点，不读取/测试真实 Key，不构造 `Authorization`，不发送文字、图片、附件或 Prompt，不产生费用。
- `OfficialOpenRouterInferenceTransport` 仍仅由 `OpenRouterEgressPolicy.Disabled` 构造；P2-L 没有按钮、Use Case 或状态转换可把它改为开启。
- 不实现 Knowledge 搜索、图谱、管理、导入、系统图片分享、拍照、账号、同步、Hub、Agent 或图标改动。

## 2. 版本化 RunSpec

`RealServiceAcceptanceContract` 固定 `id=nanfeng-ai-real-service-acceptance`、`version=1`。每个 `RealServiceRunSpec` 必须包含：

- 精确 Provider `OPENROUTER`、实际 Model ID、已验证 Registry Snapshot ID 与完整 catalog SHA-256；Model 必须在该快照中，支持 Text 和 Structured Output，图片另要求 Vision。
- 合成 fixture ID、种类、SHA-256、MIME、字节数与图片尺寸；文本/图片绝不在报告、Ledger 或 Token 文件中保存正文。
- 最大输入 256 Token、最大输出 180 Token、USD 10,000 微单位硬上限；连接/读取超时最多 8 s / 30 s，最多一次重试。未知价格或最终费用保留 `null`/“未知”，绝不写作 `0`。
- `openrouter-keystore-v1` Credential handle（只是不含 Key 的逻辑句柄）、一次性 Consent 指纹、`Task Run → Provider Attempt → Generation → Validation` 的预期 Ledger 状态、成功后 `PENDING_REVIEW` Candidate、用户核对前绝不写 Knowledge，以及完整证据清单。

RunSpec 指纹由上述合同字段和预期状态计算，不含 Key、Prompt、图片正文或用户资料；因此 Token 只能绑定精确 RunSpec，不能复用于同模型或同一份文字的其他请求。

## 3. 仓库内合成夹具

夹具由 `P2LSyntheticFixtures` 在源码中确定性生成，仅用于离线验证。两者均无个人资料、账户资料、真实请求内容或 Provider 结果。

| ID | 种类 | MIME / 尺寸 | 字节 | SHA-256 | 当前状态 |
|---|---|---:|---:|---|---|
| `p2l-text-organize-v1` | 文本整理 | `text/plain; charset=utf-8` | 126 | `f3123d0c190eb600ccdffeebf53d5914d6b162c96ba91b46acea1dc8b06cc8de` | 可做离线 Text DryRun |
| `p2l-image-shapes-v1` | 程序自有 SVG：矩形与圆形 | `image/svg+xml`, 96×64 | 247 | `d57392200728284d798398a9022c3cbba8c40f7906d2e171d28d12d8374991c2` | 仅完整性准备；P2-K 尚无图片编码，DryRun 必须阻止 |

图片没有外发路径；准备它不等于视觉模型、图片 MIME 或真实费用已验收。

## 4. DryRun 与可见报告

`RealServiceDryRunPreflight` 的构造函数只接收 Registry、Credential **存在性**探针、Disabled egress policy、清洗器与 Clock；没有 Transport、`loadCredential`、请求 JSON 或 Authorization 类型。

它完整验证：生产 egress 仍关闭、夹具哈希、Snapshot/模型能力、文本清洗和请求上限、上下文、预算、Credential 存在性、Consent/RunSpec 指纹。报告只显示安全元数据、检查结论和“费用已知/未知/超限”；固定声明 `authorizationConstructed=false`、`networkRequestsConstructed=0`、`credentialBytesRead=false`。

- 已知最大估算高于硬上限，必须 BLOCKED。
- 目录价格缺失或无法精确换算时，报告为 `UNKNOWN_NOT_ZERO`；这不把未知误作免费，后续真实阶段仍需用户确认未知语义。
- 缺 Key、过期/篡改 Snapshot、能力不符、不安全/超限文本、夹具篡改、Consent 不匹配均只形成离线失败报告；不会写 Invocation，因此没有 Provider Attempt。
- 图片 fixture 在本阶段必定因 P2-K 不编码图片而 BLOCKED；没有“试一下图片”的例外。

## 5. 一次性 nonce 与恢复

`RealServiceAcceptanceTokenService` 只有在未来的显式单次 Consent 与 RunSpec 指纹完全一致、并指定未过期时间时，才会发行 UUID nonce。它本身不是 Authorization，也不改变 egress policy。

- token 只保存 nonce、RunSpec 指纹、过期时间、`ISSUED/CONSUMED`；不保存 Key、正文、图片、费用或 Provider 响应。
- `consume` 原子比对 nonce 与精确 RunSpec 指纹后写为 `CONSUMED`；不同 Spec、过期、重复消费均被拒绝。
- Android 使用 app-private 原子文件存储；Activity/进程重建后重新读取，已消费 nonce 不可再用。
- 当前生产 UI 没有发行或消费 token 的入口。未来若有真实授权，仍必须独立审查它如何在 P2-K 的 Disabled 门之后使用 token。

## 6. UI、证据与未覆盖风险

模型设置仅显示：`验收准备未完成`、`离线预检通过` 或 `等待用户授权`，并列出合成资料、预算上限和“未读取/测试 Key、未构造 Authorization、未执行真实服务”的事实。它不显示真实调用按钮，不将配置 Key 的存在误写成 Key 可用或服务成功。

离线测试覆盖：通过与失败、预算超限、夹具哈希篡改、过期 Registry、能力不符、Credential 存在性边界、敏感信息不出报告、重复 nonce 消费、Activity/进程重建与图片阻止。P2-K 既有门禁继续证明 `BLOCKED` 没有 Attempt。

P2-L 不能证明：真实 Key、真实 Claude 模型、真实文本/图片输出、Token、费用、真实 Android/OPPO、图标或发布。真正验收必须由用户另行明确授权测试 Key、额度、精确非敏感 RunSpec、一次外发和目标设备后单独执行。
