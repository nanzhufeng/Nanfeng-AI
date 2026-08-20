# 南枫 AI P2-M 一次真实文本执行合同

日期：2026-08-15  
状态：**v2 已按本次授权创建，尚未执行 HTTP。** v1 是不可变的既有历史：其 `p2l-text-organize-v1` RunSpec/nonce 已消费，不能重新签发、重试或替代为 v2。

## v2 冻结 RunSpec

| 字段 | 固定值 |
|---|---|
| RunSpec / Task 版本 | `p2m-openrouter-text-v2` / `p2m-openrouter-text-task-v2`；Harness `p2m-text-organize` v2 |
| Provider / endpoint | OpenRouter / `POST https://openrouter.ai/api/v1/chat/completions` |
| 模型与 Registry | 仅现有已保存、启用、已验证的 OpenRouter 预设解析出的 Text + Structured 模型；既有确认页显示的指纹精确绑定当时的 model、Snapshot ID 与 catalog SHA-256 |
| 夹具 | `p2m-text-organize-v2`，126 bytes，SHA-256 `fa005b85661eb71ff1b63b2e5b5804d14ae1f71a0a430b28769fcd2635907cae`；固定合成非敏感文本 |
| 内容与输出 | 文本-only、`stream=false`、`temperature=0.2`、固定 `title`/`body` JSON 合同 |
| 上限 | 输入 256 Token、输出 180 Token、USD 10,000 微单位（USD 0.01） |
| 超时与重试 | 连接 8 s、读取 30 s、`retryCount=0`，因此最多一条 HTTP Attempt |
| v2 nonce | `1ae933d3-9c4f-4fe0-8b8c-5ec7d50b742e`；只可与 v2 RunSpec 指纹原子消费一次 |

图片、附件、目录再次核验、账号、同步、Hub、Agent、发布、OPPO 与图标均不在本次授权范围。

## 执行门与不可重用性

1. 不新增 UI：仅使用现有可见确认页。该页复核到的 v2 RunSpec 指纹会作为 `confirmRealServiceOnce()` 的参数；指纹变化即停止，不发行 nonce。
2. 先以 `Disabled` policy 执行 DryRun，核验 Registry、模型 Text/Structured 能力、v2 夹具哈希、内容清洗、Credential presence、上下文、费用上限与 Consent；DryRun 固定零网络、零 Authorization、零 Key bytes。
3. 仅 DryRun 通过才把绑定完整 v2 RunSpec 的 app-private nonce 原子消费。nonce 文件存在（包括 `CONSUMED`）即阻止进程重建后再次发行该 nonce。
4. 消费后仅构造 `ExactSingleUseRun` Adapter，且 `maxAttempts=retryCount+1=1`。它不是、也不会成为普通全局 egress 开关。
5. Adapter 在这一步才解密 Key 到临时 `CharArray`；明显占位凭据会在 HTTP 前拒绝并清零。Authorization、请求正文与原始响应不写入 Room、SavedState、日志、文档、截图或导出。

## 终态与可见证据

- DryRun、nonce 或 Key 读取前阻止：写安全 `BLOCKED` Task Run，Attempt 为空；不得称已真实调用。
- HTTP 一旦开始：无论成功、401/402/413/429、超时、网络或解析错误，均写一条安全 Attempt 终态，且不重试。
- 成功只投影 `title` / `body` 到 `PENDING_REVIEW` Candidate，不自动写 Knowledge。调用记录仅显示安全运行元数据；缺失 Token 或成本保持“未知”。
- 实际成本必须不高于 USD 0.01。若 Provider 未返回成本，账本显示“未知”，不能写作 `0`。

## 本地合同验证

- P2-L fake 测试验证 v2 夹具、RunSpec、单次 nonce、DryRun 零 egress 与旧 v1 标识仍可读取。
- P2-K fake 测试验证 `ExactSingleUseRun` 的 503 场景：即使下一 fake 响应可成功，仍只形成一条失败 Attempt、Transport 调用一次。
- 真实 HTTP、真实响应、费用、模拟器 UI、正式签名包、真机与发布是分离证据；本次均未发生。

## v1 不可变执行证据

### 2026-08-12 唯一执行证据

- API 35 模拟器以既有正式证书同签名覆盖 P2-M Debug 包（`versionCode=13`、`versionName=0.2.0-p2m`），保留已有私有 Registry 与凭据槽位；没有读取、显示或导出 Key。
- 固定 RunSpec 指纹：`f00bda03e693b19596e41b4daed994ae0811e4693ae069d0eef9d7a1e29751a2`。DryRun 通过：Registry、模型能力、126-byte fixture、内容清洗、Credential presence、上下文、Consent 与预算均通过；目录最大估算 `USD 2312` 微单位，小于 `USD 10000` 微单位，DryRun 的 Authorization/网络/Key bytes 均为 `false/0/false`。
- nonce `f00bda03-e693-b195-96e4-1b4daed994ae` 已原子消费并持久化为 `CONSUMED`。后续同一 RunSpec 被拒绝，不能再发行或重试。
- 仅在 DryRun 后解密的凭据命中明显占位检查，安全 Ledger 为 `BLOCKED`、错误 `ProviderCredentialInvalid`；`provider_attempts` 没有新增 OpenRouter Attempt。故本次没有 `chat/completions` HTTP、真实 Provider 响应、真实 Token、真实费用或 Candidate；费用不是 `0`，而是“未产生请求”。
- 当时的下一步是用户明确保存一个非占位 OpenRouter Key，并为新的精确 RunSpec/nonce 重新授权一次外发；v1 本身不得自行替换 Key、追加请求或把失败称为成功。
- 回归门：Android Studio JBR 21.0.10 下全量单测 65 项（0 skipped / 0 failures / 0 errors）、`lintDebug` 通过；正式签名 Debug/Release 构建通过。Debug SHA-256 为 `bb62733340e9815c47709ebf24029341f2e41b827c3c49b19e747dbd8b3a928f`，Release SHA-256 为 `8bd0d0541f9451820489e3f96708d176c6493203d7ba04a9bfcf11c5150aeda5`；两包均通过 v2/v3 验签，证书 SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。API 35 模拟器正常冷启动 P2-M 包并回读上述 BLOCKED/0 Attempt 安全状态；未验证真机、OPPO 或发布。
