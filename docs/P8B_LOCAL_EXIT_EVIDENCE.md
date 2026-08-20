# 南枫 AI P8-B 本地退出证据

日期：2026-08-13  
范围：P8-B 的 `LOCAL_TEST_ONLY` plan/approval/executor harness 与 production 不可见只读 ledger 状态入口。不是可见 Agent、真实工具、Provider、P9/P10、OPPO、Windows 或发布证据。

## 已实现事实

- Android `ControlledAgentRuntime` 与 Desktop `p8_agent_ledger_v1::AgentLedgerStore` 同语义提供 test-only `plan → approve → executeApproved`：计划仅稳定 tool/idempotency/input SHA-256，approval token 绑定 plan hash；token 原值不落盘。
- 工具 schema/permission/risk/side-effect/budget 在 plan 和 execute 前失败关闭。`fixture_failure`/`fixture_cancel` 是无副作用的测试故障注入；失败/取消先原子提交 Event/Checkpoint 与 `FAILED`/`CANCELLED`，再返回安全错误，重启不会丢失终态。
- duplicate receipt 只回读、duplicate event 同 fingerprint 只回读、冲突 fingerprint 拒绝；rollback 仍只允许可逆 fixture receipt。Android Room 20 与 Desktop private SQLite v1 没有 schema 或业务事实变更。
- production Android `AppContainer` 只持有 `RoomAgentLedger → P8BProductionReadOnlyAgentLedgerStatus`，Desktop 只在 `DesktopWorkspaceStore` 有私有只读入口。两端没有 P8 production registry/executor、UI、worker、Tauri command/capability 或 frontend state。状态聚合只暴露 schema/Run/Step/Event/Checkpoint/Receipt 数量，`null` 是未知、`0` 是已知空。

## 自动、构建和签名证据

- Android 定向 P8 Domain/Room/status contracts 通过；全量 `:app:testDebugUnitTest --rerun-tasks`：225 tests，0 failures/errors。覆盖计划/approval、注入仅 hash、unknown/risk/permission/tool/budget fail-closed、tool failure/cancel、pause/resume、checkpoint/restart、receipt/event replay、rollback、Room readback 与 19→20 旧 sync 表保留。
- Android `lintDebug` 为 0 errors / 23 warnings；Debug/Release assemble 成功。`0.3.0-p8b` / code 47 Debug SHA-256 `d0b101ee3a4064e9efa538db161cbe927a0f2671a3e39e85ac7b1f789cef4939`，Release `667d496537563d3cc4eaf7d24b8261cf4adacf5d2d9b768c37ecdd4b843cbb30`。两包 v2/v3 均通过，证书 SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- Emulator：仅 `emulator-5554` 同签名 `install -r --user 0` 覆盖；未清数据、`ceDataInode=574936` 保留。`NanfengAiActivity` cold start 1.924s，设备回读 code 47/version `0.3.0-p8b`；回拉 `base.apk` 与 Debug SHA-256 完全一致。P8-B 不提供可见 Agent UI，故没有伪造工具成功链。
- Desktop：frontend typecheck/lint/test(4)/build、protocol golden 通过；Rust fmt、全量 test（28）、clippy -D warnings、check 与 `CARGO_NET_OFFLINE=true cargo tauri build --bundles app` 通过。最终 `南枫 AI Desktop.app` 已 ad-hoc strict verify，13 MiB，executable SHA-256 `f3bcad6577e794fc57dc1e388156a52106410cc62f5d7eabf14c5f463234b691`，`Signature=adhoc`、`TeamIdentifier=not set`；非 Developer ID、未 notarized、未发布。

## 明确未覆盖

- 没有 production Agent executor、自动 planner/loop、Agent UI、真实 research、文件/系统工具、跨应用、购买、删除、外发、Provider/HTTP/Key、费用、账号/同步、OPPO 或 Windows 验证。
- `LOCAL_TEST_ONLY` fixture 的 success/failure/cancel/rollback 只是受控本地合同，不能作为真实外部 side effect 或真实用户 Agent 的证据。
- P8-B 不是 P8 或总蓝图终点。后续每个生产工具、用户 approval UI、真实 side effect、P9/P10 均须独立合同。
