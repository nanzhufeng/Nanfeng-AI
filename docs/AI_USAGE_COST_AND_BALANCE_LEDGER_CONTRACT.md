# AI 用量、成本与余额台账合同（未来阶段；未实现）

不编号 P6-I，避免阶段冲突；不读 Key、不查余额、不发 HTTP。集成时动态复核 [OpenRouter usage](https://openrouter.ai/docs/cookbook/administration/usage-accounting)、[current key](https://openrouter.ai/docs/api/api-reference/api-keys/get-current-key)、[credits](https://openrouter.ai/docs/api/api-reference/credits/get-credits)、[generation audit](https://openrouter.ai/docs/api/api-reference/generations/get-generation)。

- 事实等级：`PROVIDER_SETTLED`、`PROVIDER_RESPONSE_REPORTED`、`LOCAL_ESTIMATE`、`LOCAL_EXACT_ZERO`、`UNKNOWN`；余额：`PROVIDER_ACCOUNT_BALANCE`、`KEY_LIMIT_REMAINING`、`LOCAL_BUDGET_REMAINING`、`UNAVAILABLE`。估算绝不冒充结算或 Provider 余额。
- 每个物理 Provider attempt 追加不可变 entry：ledger/logical invocation/attempt ID、时间、application ID/version/platform/device class、workspace/project/conversation/task/message/branch、feature、provider、requested/actual model snapshot、router tier/rule、fallback/retry、finish/status、安全 request/generation ref、key alias hash、pricing/catalog/currency、输入/cache read-write/uncached/输出/reasoning/tool-web-media-audio usage、reported/upstream/estimated cost、balance ref、latency/cache/egress-sensitivity policy；绝不含 Prompt/回答/Key/URI/path。
- `nanfeng-ai.desktop`、`nanfeng-ai.android` 是稳定 app ID；未来南枫记/八字独立记录，跨端保留原始产生端。任务可多 attempts；以 idempotency/fingerprint 去重，stream 尾包缺 usage 为 `PENDING_RECONCILIATION`，generation 对账后追加 adjustment，不写零/不覆盖。
- UI：模型选择小额度状态；设置→用量支持软件/任务/会话/模型/时间下钻；对话只轻量本次/月度入口。普通 key 只能显示 Key 可用额度；账户 credits 仅 management key，权限不足/离线明确不可获取与上次同步。余额节流/后台/手动刷新且失败不影响对话，凭据层只给 scoped result。
- 原币种为事实；CNY 仅带汇率快照参考。Assistant/Tool 小字可显示模型、tokens、金额、cache savings；本地 exact 为“本地缓存 · $0 Provider”。支持区间与 software/model/provider/feature/task/conversation/cache/online-local 聚合、CSV/JSON 脱敏导出、可配 app-private 保留、月度聚合和 adjustment 链。
- 全局/软件/workspace/model 日月软硬预算，80/90/100 提醒；硬限默认阻止付费 attempt，本地与 exact 继续，人工覆盖明确。Router 用缓存后边际成本+质量；Prompt Cache 只从 ledger 的真实 cache tokens/cost 计算节省，exact zero 不伪造 Provider usage。P6-F 消息 metadata 预留 usage/cost；P6-G 写 attempt 事实。
- 对账链：response→generation/账单导入→adjustment，记录差异、价格、Provider 时区和结算延迟，并分实时估算/已对账。TEMP 可保留无内容的账务事实但不保留消息/Prompt，UI 明示。测试覆盖流/非流、缺 usage、retry/fallback、多 Provider、cache/local zero、cancel、重复、FX、余额权限/离线、跨端、导出脱敏、adjustment、预算。
