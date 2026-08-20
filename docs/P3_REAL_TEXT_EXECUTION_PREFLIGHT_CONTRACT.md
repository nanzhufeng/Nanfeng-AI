# 南枫 AI P3 RealTextExecution Preflight / Orchestrator 合同

日期：2026-08-15  
状态：纯领域、未注册、只生成安全 Ready/Blocked 计划

## 范围

本合同将现有 P3-I execution identity、P3 Provider Transport route、P1 附件资格/授权、P0 Usage 预留语义编排为未来可见确认之前的 fail-closed preflight。它不调用 HTTP/模型，不写 P3-I receipt、Conversation runtime、Room/SQLite、Usage Ledger 或附件 owner。

```text
safe configuration/presence + verified preset + transient text + fee proof + P1 safe summaries
→ RealTextExecutionPreflightOrchestrator
→ Ready(safe plan) | Blocked(safe codes)
→ future visible confirmation owner (本阶段不存在)
```

## 顺序与安全输入

1. 取消与 P3-I/transport execution binding；不一致立即阻止。
2. 固定 provider handle、只读设置的 enabled/preset，以及 `ProviderCredentialStore.credentialPresence()`；该接口是唯一凭据接触点，不能返回或读取 Key bytes。
3. 已验证 registry 的 text-capable model 与 route 精确一致；文本保持瞬时，仅验证长度、控制符与明显 credential marker。
4. 仅当 model pricing 完整时，推导 P0 `BUDGET_RESERVATION` 的**计划**；不得 append `UsageLedgerEntry`。
5. 独立费用确认必须绑定 request fingerprint、价格版本、币种、时间及不低于预留上限的金额。
6. 每个附件只检查 P1 capability 及仍有效的安全授权 summary；不读取、消费、上传或续期附件。

## Ready/Blocked 边界

- `Ready` 只含 execution/invocation/attempt/fingerprint、provider/model/preset、费用确认指纹、Usage 预留数量/币种/对账指纹、附件 hash/MIME/大小/授权 hash，以及取消身份；没有文本、请求 body、Key、credential、endpoint、URI/path、附件字节、response、token 实测、费用实绩或 Provider payload。
- `Blocked` 只返回受限 enum，例如未配置凭据、未验证 registry、价格未知、费用确认不匹配、附件资格/授权不足、取消或 execution binding 错误；不包含原始错误或用户资料。
- Ready 不是用户确认、没有 egress 权限、不会构造 `ProtectedProviderCredentialHandle`，更不会启动 transport。未来 UI 经可见确认后仍须独立执行 P1/P0/P3 的最终原子 owner。

## 验证

定向 fake 合同测试覆盖 presence-only credential（`loadCredential` 必须未调用）、固定 preset/registry、文本与价格/费用 fail-closed、附件 active summary 不消费、取消与 binding 短路、Ready metadata 白名单，以及 P3/P1/P0 回归。它不证明 Key 有效、Provider 已配置可调用、账本已预留、附件已外发、UI、网络、费用或设备验收。
