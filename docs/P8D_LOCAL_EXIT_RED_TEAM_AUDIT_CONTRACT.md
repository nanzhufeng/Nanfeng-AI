# P8-D 本地退出与红队审计合同

日期：2026-08-13  
状态：P8 的本地退出审计；不授权真实 Provider、外部工具、文件、跨应用、高风险动作、OPPO、Windows 或发布。

## 目标、唯一所有者与范围

P8-D 不新增 Agent 能力。`ControlledAgentRuntime → AgentLedger` 仍是 Android 和 Desktop `LOCAL_TEST_ONLY` harness 的唯一计划、风险、预算、approval、执行、暂停、恢复、取消、回滚与审计所有者。Android production 唯一动作仍为 `p8c_local_ledger_inspect`；Desktop production 唯一 P8 command 仍为 `inspect_p8_agent_runs`，只读且无 executor。

本轮只审计并补齐本地证据。fixture 永远不进入 Android release DI/UI 或 Desktop Tauri executor/UI；它们不访问网络、Key、Provider、真实文件、跨应用或业务数据。

## Requirement → authoritative evidence 矩阵

| P8 退出要求 | Android authoritative evidence | Desktop authoritative evidence | P8-D 判定 |
|---|---|---|---|
| Tool schema / 未知输入失败关闭 | `P8ControlledAgentRuntimeContractsTest`：future schema、unknown tool、risk/permission/budget | `p8_agent_ledger_v1` tests：unknown budget/risk/tool | 通过；编译期 schema 无 JSON 字段反序列化入口，未知版本/工具按不可信契约拒绝 |
| 越权、Risk `UNKNOWN`、外部 effect | Runtime `validSchema`/permission/risk tests | Rust `start/plan/execute` guards 与测试 | 通过；release 不允许 external effect |
| Prompt Injection 是不可信工具数据 | Android input SHA-256 assertion | Rust hash-only fixture/reopen test | 通过；无工具参数可由正文提升权限 |
| success / failure / cancel / timeout / crash | Android fixture success + `fixture_failure/cancel/timeout`，rebuild no-auto-execute | Rust fixture success + failure/cancel/timeout，reopen tests | 通过；crash 以 durable checkpoint 后 reopen/no auto replay 验证，不伪称 OS 级外部工具崩溃 |
| unknown / `0` / exhausted budget | Android `AgentBudget`/P8-B status tests | Rust `ReadOnlyLedgerStatus`/budget tests | 通过；unknown 不是零 |
| approval binding、expiry、one-shot | Android P8-C production controller tests | Harness plan hash/approval tests；Desktop 无 executor | 通过；取消会立即废弃 pending approval |
| pause / resume / cancel、restart/checkpoint | Android P8-A/P8-C and Room tests | Rust replay/pause/resume/cancel/reopen tests | 通过 |
| duplicate intent/event/receipt/replay | Android receipt/event tests | Rust replay conflict/reopen tests | 通过 |
| side-effect idempotency、rollback success/failure | Android reversible fixture + rollback denial tests | Rust reversible receipt + rollback tests | 通过，仅 `LOCAL_TEST_ONLY` reversible fixture |
| stable event sequence / no gaps | Android Room/P8-C sequence readback | Rust inspection sequence test | 通过 |
| audit data minimization | Room entity fields, P8 UI; no body/Key/URI/path | Rust inspection projection, command/ACL/frontend test | 通过 |
| production `local.agent.ledger.self_check` | Android `p8c_local_ledger_inspect`: success/cancel/expiry safety reject/UI/cold readback | 不适用：Desktop production intentionally has no executor | 通过；production tool body has no external failure surface，failure is not fabricated |
| release-surface isolation | `AppContainer` only wires P8-C controller, no fixture registry | exact `allow-inspect-p8-agent-runs`; Node ACL test | 通过 |

## P8-D 修复与停止条件

`P8CProductionLocalAgentController.cancel()` 必须同步废弃同一 Run 的 memory-only approval。取消、过期或重建后不得写入 `PLAN_APPROVED`、Step 或 Receipt；只有新建 plan 后才可再次明确确认。

退出前必须分别记录 Android 定向/全量 JVM、lint、正式 Debug/Release 签名、`emulator-5554` 的同签名覆盖与 base.apk hash；Desktop 的 frontend/Rust/Tauri/ad-hoc verify 与实际 `.app` 只读 inspect。P8-D 只在矩阵逐项成立后声明“P8 本地主体退出”；真实外部工具、Provider 和高风险动作继续为未来授权门。
