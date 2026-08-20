# 南枫 AI P8-A 本地退出证据

日期：2026-08-13  
范围：受控 Agent 的本地 durable 合同、Android Room ledger 与 Desktop SQLite ledger。不是 Provider、自动 Agent、外部工具、P9、P10、OPPO、Windows 或发布证据。

## 已实现事实

- 合同：`P8A_CONTROLLED_AGENT_LOCAL_RUNTIME_CONTRACT.md`。唯一执行路径为 `ControlledAgentRuntime → AgentLedger`；Tool Schema v1 固定风险、permission、副作用和 rollback 字段，`UNKNOWN`/未知预算一律拒绝，`null` 不等于 `0`。
- Android：Room Schema 19→20 只新增 `agent_runs`、`agent_steps`、`agent_events`、`agent_checkpoints`、`agent_side_effect_receipts`。`RoomAgentLedger` 在一个 Room transaction 中追加 step/event/checkpoint/receipt 并更新 Run；持久字段只有安全 hash/状态/序号/错误码，没有正文、Prompt、Key、Provider、路径或 URI。
- Desktop：`p8_agent_ledger_v1::AgentLedgerStore` 持有独立 app-private SQLite `p8-agent-ledger-v1/agent-ledger.sqlite3`，`user_version=1`；不触碰 P6 workspace SQLite、P7 isolated workspace 或 Tauri command。它只在 `DesktopWorkspaceStore` 内部打开以保证本地 ledger 可创建，未提供 UI/生产工具注册。
- 内建 fixture 只有 `fixture_research`、`fixture_draft` 和 `fixture_reversible_action`，都标 `LOCAL_TEST_ONLY`；Android release `AppContainer` 未创建 `ControlledAgentRuntime`/registry，Desktop invoke handler 未暴露 P8 executor。没有 Provider、HTTP、Key、系统文件、外发、购买、删除或跨应用路径。

## 自动与构建证据

- Android：定向 `P8ControlledAgentRuntimeContractsTest` 与 `P8AgentLedgerRoomContractsTest` 通过；覆盖 injection 作为不可信 hash、未知、权限/风险、tool exception、预算、pause/resume/cancel、checkpoint/replay/rollback、Room readback 和 19→20 旧 `sync_jobs` 表保留。全量 `:app:testDebugUnitTest --rerun-tasks` 通过，222 tests、0 failures/errors。
- Android lint/包：`:app:lintDebug :app:assembleDebug :app:assembleRelease --rerun-tasks` 成功；lint 为 0 errors / 23 warnings（既有 SDK/Gradle/依赖、图标/KTX 等提示，本轮不改图标）。`0.3.0-p8a` / code 46 Debug SHA-256 `d99d1f584285b540f9206522f76caf42cee372f627c2195917fbea911ac56670`，Release `5982db89a92ab7533a51d0e6dbb59b10688e57ec4d9bcd11603aa56275f70cee`；v2/v3 通过，签名证书 SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- Emulator：仅 `emulator-5554` 以正式 Debug `install -r --user 0` 覆盖，`ceDataInode=574936` 不变；`NanfengAiActivity` cold start 1.905s，设备回读 code 46/version `0.3.0-p8a`，回拉 `base.apk` SHA-256 与 Debug 完全相同。P8-A 没有可见 Agent UI，故没有伪造用户 Agent 成功链；未清数据、未装 test APK、未操作 OPPO。
- Desktop：前端 `typecheck/lint/test(4)/build` 通过；Rust `fmt --check`、`test`（25）、`clippy -D warnings`、`check` 均通过，P8 reopen/replay/预算/rollback 由 Rust ledger tests 覆盖。`CARGO_NET_OFFLINE=true cargo tauri build --bundles app` 后重作 ad-hoc 签名，`codesign --verify --deep --strict` 通过；`南枫 AI Desktop.app` 13 MiB，executable SHA-256 `303654de1e364faf30498bcad2d41aae947b709fc4a3b37ca5474ae8b3b57cf2`，`Signature=adhoc` / `TeamIdentifier=not set`，非 Developer ID、未 notarized、未发布。

## 明确未覆盖

- 没有真实 OpenRouter Key、Provider/HTTP、费用、图片、真实研究、文件/系统访问、外发、购买、删除、跨应用、账号/同步、OPPO 或 Windows 验证。
- 没有生产 Agent UI、自动 planner/loop、真实 side effect 或外部 rollback；本地 fixture receipt 不能作为真实外部动作证据。
- P8-A 是 P8 的第一个受控本地基座，不是总蓝图或项目终点。后续每类真实工具、权限 UI、外部 side effect 与 P9/P10 都必须另立合同。
