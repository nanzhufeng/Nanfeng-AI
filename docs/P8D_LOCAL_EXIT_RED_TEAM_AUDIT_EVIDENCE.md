# 南枫 AI P8-D 本地退出与红队审计证据

日期：2026-08-13  
结论：P8 的**本地主体退出条件已逐项成立**。本结论仅覆盖本地 durable runtime、`LOCAL_TEST_ONLY` fixture harness、Android production local ledger self-check 与 Desktop read-only inspect。真实外部工具、Provider、文件/跨应用、高风险动作、OPPO、Windows、Developer ID/notarization、发布与 P9 均未验证或未开始。

## P8-D 修复

- 发现并修复：Android `P8CProductionLocalAgentController` 在 PENDING Run 被取消后，内存中的 approval 可能仍被 `confirm()` 消费。现在 `cancel()` 会同步丢弃同 Run pending approval。
- 定向合同证明：approval 过期或取消后只保留 `PLAN_ACCEPTED` / `CANCELLED` durable chain，不写 `PLAN_APPROVED`、Step 或 Receipt；重建也不会自动执行。
- 新增 `LOCAL_TEST_ONLY fixture_timeout`，在两端都持久化为 `FAILED / TOOL_TIMEOUT`。它不访问任何外部资源。

## Requirement → authoritative evidence

完整矩阵在 `P8D_LOCAL_EXIT_RED_TEAM_AUDIT_CONTRACT.md`；本轮重新执行的 authoritative evidence 如下：

| 要求 | Android | Desktop | 结论 |
|---|---|---|---|
| 未知 schema/tool、UNKNOWN risk/permission、未知/0/exhausted budget | `P8ControlledAgentRuntimeContractsTest` | `p8_agent_ledger_v1::unknown_budget_tool_and_exhaustion_fail_closed` | fail-closed |
| injection、越权、外部 effect | input 仅 SHA-256；schema/permission guard | hash-only fixture 与 fixture meta guard | 无权限提升或 egress |
| fixture success/failure/cancel/timeout/crash-reopen | research/draft/reversible success；failure/cancel/timeout durable terminal; restart no-auto-execute | 同等 Rust fixture/reopen tests | 每种 local fixture outcome 有 audit chain；crash 证据是 durable reopen，不伪称真实外部工具崩溃 |
| approval binding/expiry/one-shot | plan hash、90 秒 memory-only token、P8-D expiry/cancel test | test-only plan-hash approval；production 无 executor | 取消/重建/expiry 不执行 |
| pause/resume/cancel/checkpoint/replay/receipt/idempotency | runtime + Room tests | Rust durable/reopen/replay-conflict tests | stable durable semantics |
| rollback success/denial | reversible fixture only | reversible fixture only | 不伪造外部 rollback |
| event order/no gaps | P8-C UI and Room sequence | `p8c_inspection_is_read_only_and_orders_durable_events` | stable sequence |
| data minimization/release surface | Room fields/UI + AppContainer scan | command/ACL/frontend Node test | no body, Key, Prompt, URI/path or fixture executor surface |

生产唯一 `p8c_local_ledger_inspect` 已有真实本地成功、取消和安全拒绝（expiry/approval required）链。它是只读账本自检，工具体没有外部 I/O 或合理的真实失败面；因此没有制造“外部工具失败”来填充证据。Desktop production 保持没有 executor：非空 ledger 不存在合法 UI 产生路径，非空状态只由 Rust internal harness/reopen tests 证明。

## 自动与静态验证

- Android 定向：`P8ControlledAgentRuntimeContractsTest` 通过，新增 P8-D tests 覆盖 future schema、五个 fixture outcome、approval expiry/cancel no-execute。
- Android 全量：`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --rerun-tasks` 通过；229 tests，0 failures/errors；lint 0 errors / 24 existing warnings。
- Android 正式构建：`0.3.0-p8d` / code 49。Debug SHA-256 `1f818087835e859213a2930533aae01cd5573380cdd82844235f8a578857085d`；Release SHA-256 `3485e5773427afd0c6b9de9d8f3575c81c3e26e28e57c664600ed7b63c130259`。两包 apksigner v2/v3=true，证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- Desktop：frontend `typecheck/lint/test(5)/build`、Rust `fmt/test(29)/clippy -D warnings/check` 均通过。P8 Rust 定向为 6 tests；Node P8-C command/ACL test 证明只允许 `inspect_p8_agent_runs`。
- Desktop release surface static audit：Android `AppContainer`/UI 未匹配 fixture registry/runtime；Desktop frontend/capability/permissions 只匹配 inspect command，未匹配 fixture/execute/plan/approve/cancel command。

## 真实可见回归与产物

- Android：仅 API 35 `emulator-5554`。code 48→49 以正式 Debug `install -r --user 0` 覆盖，无清数据，`ceDataInode=574936` 不变。冷启动 1.712s 后 Settings → 受控本地运行回读已有账本 `Run 2 / Step 1 / Event 7 / Receipt 1` 及暂停/取消控制，明确“未连接模型与外部工具”。设备 `base.apk` 回拉 SHA-256 与 Debug 包相同。未安装 test APK、未做数据库注入。
- Desktop：新 `南枫 AI Desktop.app` 已实际打开，并进入“本地受控记录”。页面显示 `READ_ONLY · LOCAL_READ · NONE`、无 production executor、无模型/外部工具及“账本当前为空 / 已知空账本”；未产生 Run。Desktop 账本非空没有合法 UI 产生路径，故非空只作为 Rust internal harness/reopen 自动证据，不冒充可见用户成功。
- Desktop package：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，13 MiB，executable SHA-256 `c3f23ced261782540dd3d74111fb907ea02b167a29a7c1eece13ee68eb53d229`；build 后 ad-hoc signing、`codesign --verify --deep --strict` 通过，`TeamIdentifier=not set`，不是 Developer ID/notarized/发布包。

## P8 退出边界

P8 现可标记为“本地主体退出”。这不授予真实 Provider/Key/HTTP、真实研究/文件/跨应用、购买、删除、外发或其他高风险动作；这些都必须在未来按工具和权限另立合同。P9 成为下一候选阶段，但本轮未开始 P9。
