# 南枫 AI P0 Usage Ledger 合同

<!-- contract-governance:start -->
> **合同口径：领域／模块限定合同。** 领域入口：[ 当前调用计量与币种 ](AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** append-only数量事实内核；Android独立Room与Mac生产账本接线分开，不替代当前计量合同
<!-- contract-governance:end -->

日期：2026-08-15  
状态：append-only数量账本内核与平台存储合同；当前生产消费者按平台区分，不由早期未接入状态推断

## 目标与边界

`usage-ledger-v1` 是独立、append-only 的本地数量事实账本，定义数量、费用事实与对账的内核；它不是 Provider 调用器、密钥存储或内容存储。当前用户界面经[费用合同](AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md)指定的只读投影消费，不直接改写账本。本阶段新增 Android Room `usage_ledger_entries` 与 Desktop app-private `usage-ledger-v1.sqlite3`，两端都以内存派生 read model；不改变 P3-I receipt、Invocation Ledger、Conversation、消息、附件或现有表。

唯一合法边界为：

```text
authorized accounting owner → immutable entry → platform append-only store → read model
```

Android 的独立 `RoomUsageLedgerRepository` 未注入 `AppContainer`，普通聊天实际计量沿 `ChatCallAccounting` 及其持久化投影。Desktop 的 `usage_ledger_v1::Ledger` 已由 workspace 生产 owner 记录普通调用与标题等事实，再通过 IPC 读取；`usage_ledger_call_metadata` 的附属元数据事务与重开恢复遵循费用合同。不能把 Android 独立内核的接线状态扩大到 Desktop，或将旧 sidecar 定位解释为整机无真实计量。

## 字段白名单与隐私

每条事实只保存：稳定 entry/replay 标识、P3-I execution / conversation / branch-leaf / invocation / attempt 绑定、事实类型与等级、requested/actual model、token 数量、charge/budget/adjustment 微单位及 ISO 币种、SHA-256 reconciliation fingerprint、被对账 entry ID、跨端来源与时间。

严禁保存 Prompt、response、Key、credential、secret、Authorization、URI、path、附件、附件内容、Provider payload、请求/响应正文、账户身份或同步指令。模型与对账指纹只是受限元数据：前者由安全字符集限制，后者必须是小写 SHA-256。

## 追加性、事实等级与对账

- 唯一 `replayToken`：同 token 且完整事实相同回 `Replayed`；任一不同回 `Conflict`，不覆盖旧行。
- DAO 只提供 insert/read，完全没有 update/delete。read model 总是由排序后的原始事实重建，不持久化可改摘要。
- `STREAM_PENDING` 只能是 `ESTIMATED` 且必须带 reconciliation fingerprint；read model 仅在存在 `RECONCILIATION_ADJUSTMENT.reconcilesEntryId` 时将其从 pending 移除。
- `FINAL_MEASURED` 只能为 `PROVIDER_REPORTED` 或 `RECONCILED`；`RECONCILIATION_ADJUSTMENT` 必须为 `RECONCILED`，不可引用自身，并允许正/负金额调整。
- 预算采用 append-only reservation/release 事实；read model 分别汇总 reserve/release，绝不回写原预算。Android/ Desktop / cross-platform import 来源以受限 enum 保留，未启动同步。

## 迁移与验证

- Android Room Schema **30→31**：只建 `usage_ledger_entries` 与其 replay/execution/conversation/reconciliation 索引；Desktop 只建立独立 `usage-ledger-v1.sqlite3` sidecar 与同构表/索引。两端均没有 `ALTER`、`UPDATE` 或 `DELETE`。
- 同时登记此前遗漏的 **29→30** 迁移，以确保现有库可以连续走到 31；未变更既有 schema/数据。
- 定向合同测试覆盖：领域字段白名单、pending→append-only reconciliation、requested/actual model、预算与跨端来源、replay/conflict、重建 read model、Room 列白名单和 18→31 连续升级；Desktop Rust 同样覆盖 sidecar 重开、对账目标匹配及列白名单。
- 这些测试不读 Key、不构造 Authorization、不发 HTTP、不读取或外发附件，不构成 Provider、费用结算、跨端同步、UI 或设备验收。
