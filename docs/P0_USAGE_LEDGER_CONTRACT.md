# 南枫 AI P0 Usage Ledger 合同

日期：2026-08-15  
状态：P0 独立安全内核与 Android Room / Desktop SQLite sidecar；未接入 UI、Compose、Provider、HTTP、Key、附件外发或 P3 Adapter 注册

## 目标与边界

`usage-ledger-v1` 是独立、append-only 的本地数量事实账本，服务于将来的费用、预算和运行核对；它不是 Provider 调用器、密钥存储、内容存储或 UI 数据源。本阶段新增 Android Room `usage_ledger_entries` 与 Desktop app-private `usage-ledger-v1.sqlite3`，两端都以内存派生 read model；不改变 P3-I receipt、Invocation Ledger、Conversation、消息、附件或现有表。

唯一合法边界为：

```text
future explicit owner → immutable UsageLedgerEntry → Room append-only table → in-memory UsageLedgerReadModel
```

当前没有 production caller；`RoomUsageLedgerRepository` 未注入 `AppContainer`，Desktop `usage_ledger_v1::Ledger` 也未出现在 Tauri command、IPC、前端或 Adapter registry 中。

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
