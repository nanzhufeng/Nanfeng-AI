# P8-A 受控 Agent 本地基础合同

日期：2026-08-13  
状态：仅本地 durable runtime 基座；不是自动 Agent、Provider、P9 跨应用或 P10 Hub。

## 目标、所有权与入口矩阵

```text
显式 AgentRunRequest（无 UI 自动入口）
→ ControlledAgentRuntime（唯一计划/预算/权限/风险/状态所有者）
→ AgentLedger（Android Room / Desktop SQLite，同语义）
→ LOCAL_TEST_ONLY tool registry（仅定向测试，未注册到 release DI/UI）
```

| 概念 | 唯一所有者 | 公开入口 | 本阶段禁止 |
|---|---|---|---|
| Tool Schema / 风险 / 权限 /预算门 | `ControlledAgentRuntime` | `start/execute/pause/resume/cancel/rollback` | UI、模型文本、Provider 或 Tool 自行扩大权限 |
| durable Run/Step/Event/Checkpoint/Receipt | `AgentLedger` | 仅 Runtime 写入 | 平行日志、Preferences、临时内存充当真值 |
| Android ledger | `RoomAgentLedger` | `agent_runs` 等 Schema 19→20 表 | URI、路径、Key、Prompt、Provider payload、正文入账 |
| Desktop ledger | `p8_agent_ledger_v1::AgentLedgerStore` | app-private `p8-agent-ledger-v1/agent-ledger.sqlite3` | Tauri command、capability、UI、HTTP、跨应用动作 |
| LOCAL_TEST_ONLY tools | test-only registry / Rust tests | 显式 fixture 注入 | release DI/UI 注册、真实文件/系统/网络动作 |

受影响入口仅为 Android/desktop 的本地 persistence 与定向合同。现有 Conversation runtime、P5 background recovery、P6 workbench ledger、P7 sync/restore、Provider、Settings、Desktop command/UI 都不受影响；它们不能读取、写入或恢复 P8 Run。

## 版本化 schema 与安全字段

Tool Schema 固定 `version=1`，每个 Tool 声明不可省略的 `id`、version、`RiskLevel`、`SideEffectClass`、required permission、`localTestOnly` 与 rollback support。未知 version/工具/风险/副作用/permission 全部失败关闭。

| 枚举 | P8-A 允许 | 失败关闭 |
|---|---|---|
| RiskLevel | `READ_ONLY`、`LOCAL_REVERSIBLE` | `UNKNOWN`、`EXTERNAL_HIGH` |
| SideEffectClass | `NONE`、`LOCAL_REVERSIBLE` | `UNKNOWN`、`EXTERNAL` |
| Permission | `LOCAL_READ`、`LOCAL_DRAFT`、`LOCAL_REVERSIBLE_FIXTURE` | `UNKNOWN`、`EXTERNAL` |

`AgentRun` 保存 stable run/idempotency key、schema、状态、风险上限、授权、三个预算、已用计数、checkpoint 序号、安全错误码和时间。`AgentStep` 保存稳定 sequence、tool/idempotency key、输入 SHA-256、结果 SHA-256、终态、安全错误码、风险/副作用；`AgentEvent` 是单调 sequence + eventId/fingerprint；`AgentCheckpoint` 保存下一个 step 和状态 hash；`AgentSideEffectReceipt` 以 idempotency key 唯一，保存 outcome/result hash/rollbackAvailable。

不得持久化输入/输出正文、Prompt、RunSpec、Provider 内容、Token/费用、Key、Authorization、账号、URI、路径、文件字节、系统句柄或跨应用凭据。输入文本一律是不可信数据，只算 hash；不能成为工具选择、权限、参数或指令来源。

`null` 表示预算未知，启动即拒绝；`0` 是明确零预算，允许创建但不允许消耗相应资源。未知不降级成零，也不默认无限。

## 状态、原子性与重建

- Run 只可 `PENDING → RUNNING → PAUSED ↔ RUNNING`，或明确到 `CANCELLED/FAILED/ROLLED_BACK`；终态不可取消或恢复。
- 每次接受 step 在单事务内写 Run 计数、Step、Event、Checkpoint 与可用 Receipt；读取固定按 sequence 升序。重复 receipt 直接回读，不再次执行。
- 预算在调用工具前检查：step/tool-call/side-effect 任一耗尽，写 `BUDGET_EXHAUSTED` 的失败状态/事件/checkpoint，不生成 receipt。
- pause/cancel/resume 均追加 event/checkpoint；重建只从 durable snapshot 读回，不自动调度、重放 intent 或启动 tool。
- rollback 只接受同 Run、`LOCAL_REVERSIBLE` 且 receipt 明确 `rollbackAvailable=true` 的本地 fixture；它追加 `ROLLBACK_COMPLETED` 和 checkpoint，绝不伪造对外撤销。
- Android Schema 19→20 仅追加五张 Agent 表/索引；Desktop P8 私有 SQLite 以 `user_version=1` 建表。两端均不删除、重写或复用 P3/P5/P6/P7 ledger；未知未来 schema 拒绝打开。

## P8-A 内建工具与严格禁区

只有测试显式注入的三项 schema v1 fixture：`fixture_research`（只读）、`fixture_draft`（本地草稿候选）和 `fixture_reversible_action`（可审计可回滚 fixture）。它们不读取用户正文、不读取 Key、不打开文件、不发 HTTP，也不写业务 Room/Desktop workspace。

生产默认没有 Agent tool registry、没有 executor DI、没有 UI 或 Tauri command；因此 App 诚实地保持“无 Provider、无外部工具”。P8-A 不实现模型规划、自动循环、真实研究、任意系统文件访问、购买、删除、外发、跨应用、Provider/OpenRouter、图片、费用、P9 或 P10。

## 最小验证与停止条件

定向 Android 合同覆盖：未知预算/风险/tool、权限拒绝、prompt-injection 文本只作为 hash、tool exception、预算耗尽、cancel/pause/resume、checkpoint、receipt replay、rollback、Room readback 和 19→20 旧 sync 表保留。Desktop Rust 合同覆盖同等的 schema/migration、未知/预算、replay、pause/resume/cancel、rollback 和 reopen readback。

本合同达到上述本地闭环即停止。真实 Provider、真实文件/系统动作、跨应用、网络、OPPO、Windows、真实用户 Agent UI 与发布均为后续单独合同，不能由 fixture、构建或本地 ledger 宣称完成。
