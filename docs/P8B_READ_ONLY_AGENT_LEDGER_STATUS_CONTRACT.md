# P8-B 受控本地 Agent harness 与生产只读状态合同

日期：2026-08-13  
状态：纯本地 P8-B 内部 harness + 不可见 production-safe 状态入口；不是 production Agent executor、工具 UI 或外部工具。

## 唯一目标、所有权与入口矩阵

```text
Android AppContainer / Desktop DesktopWorkspaceStore（无 UI、无 Tauri command）
→ P8BProductionReadOnlyAgentLedgerStatus
→ P8-A AgentLedger（Room / private SQLite）
→ secret-free aggregate snapshot
```

| 概念 | 唯一所有者 | 生产入口 | 本阶段禁止 |
|---|---|---|---|
| 受控本地 harness | `ControlledAgentRuntime` / Desktop `AgentLedgerStore` | 仅 unit/Rust test 显式调用 | release DI/UI/Tauri command、自动循环或真实工具 |
| P8-B 不可见状态 | `P8BProductionReadOnlyAgentLedgerStatus` | Android `AppContainer` / Desktop private store 内部状态入口 | UI、Tauri command、任何写入或调度 |
| durable aggregate | P8-A `AgentLedger` | `status` read-only 查询 | 输入/输出正文、Prompt、Key、URI、路径、Provider 数据 |

受影响入口是 Android/desktop P8 私有账本的 test-only plan/approval/executor 与 production 只读状态查询。Conversation、P5/P6/P7 ledger、Provider、Settings、Desktop workbench UI、Tauri invoke handler、同步与恢复均不受影响。

## P8-B 内部受控 executor

`AgentExecutionPlan` 只含稳定 run/plan/step id、Tool ID 和不可信输入的 SHA-256；没有正文、Prompt、路径、URI 或系统句柄。先 `plan`，再取得显式、绑定同一 plan hash 的 `AgentApprovalToken`，才能 `executeApproved`。计划在调用工具前重验 schema v1、`LOCAL_TEST_ONLY`、permission、risk、每一预算及预计 side effect。三类预算必须以“已用 + 完整计划”分别预检；同一计划内 idempotency key 必须唯一。`UNKNOWN`、未知/超额预算、重复 step intent、未知工具、越权或外部副作用均失败关闭，并写入对应的 durable failure Event/Checkpoint。

计划接受和批准都写 P8-A 的 Event/Checkpoint；token 原值不落盘，只审计 plan hash。重建后绝不自动 plan/approve/execute，P8-B 的 `executeApproved` 调用方必须显式重新给出同一安全计划和 token。P8-A 既有直接 fixture `execute` 仅保留旧定向回归、未绑定 production；新 P8-B 计划路径不允许绕过批准。重复 receipt 只回读，不再调用工具；重复 event ID + 同 fingerprint 只回读，冲突 fingerprint 拒绝。fixture failure 写 `TOOL_ERROR` 的失败 Event/Checkpoint；fixture cancel 写 `TOOL_CANCELLED`/`CANCELLED` 的 Event/Checkpoint；两者先提交后报错，不能因错误返回回滚 durable 安全事实。rollback 继续仅允许可回滚 fixture receipt。

仅测试 registry 增加确定性 `fixture_failure` 与 `fixture_cancel` 故障注入，二者无文件、网络、业务写入或外部副作用；它们和 P8-A 三个 fixture 同样不得进入 release DI/UI/Tauri command。

## 固定工具与安全语义

状态入口固定声明 `p8_local_ledger_status`（Tool schema v1）：`READ_ONLY`、`NONE`、`LOCAL_READ`、无 rollback、非 `LOCAL_TEST_ONLY`。它的声明预算固定为 `maxSteps=1`、`maxToolCalls=1`、`maxSideEffects=0`，但本阶段不创建 Run、不会消耗预算，也不执行工具；该预算仅是未来显式受控执行前的合同描述，不能被误称为一次运行。

入口不接受任何用户/模型/网页/附件文本、工具参数或系统句柄。它只返回 schema version 与 Run/Step/Event/Checkpoint/Receipt 的 `Long?` 安全聚合计数，以及固定 `NO_PRODUCTION_EXECUTOR` 可用性；没有 run ID、时间、hash、错误详情、工具结果、输入或输出。`null` 明确表示储存状态未知/不可读，`0` 是已知且为空，二者不得互换或以默认零掩盖未知。

每次 `read()` 严格只读：不创建 Run，不改变预算/状态，不追加 Step/Event/Checkpoint/Receipt，不恢复、取消、重放或回滚既有 Run。P8-A 的 unknown、越权、注入、未知工具、预算、取消、重放、进程重建语义继续由 P8-A runtime/ledger 独占；P8-B 只如实读取其已持久化安全计数。

## 生产禁区

- Android release 只创建 `RoomAgentLedger` 和 P8-B 状态入口；绝不创建 `ControlledAgentRuntime` 或 `LocalTestOnlyAgentToolRegistry`。
- Desktop 保持无 P8 Tauri command、capability、frontend state 或 UI；P8-B 只在 `DesktopWorkspaceStore` 私有持有。
- 不读取 Provider/HTTP/Key、系统或用户文件、跨应用数据；不购买、删除、外发、自动 Agent，也不把 `LOCAL_TEST_ONLY` fixture 注册至 production DI/UI。
- 不新增 Room/SQLite schema、迁移、日志、导出、网络、权限、系统文件访问或业务数据写入。

## 最小验证与停止条件

Android Domain/Room 与 Desktop Rust contract tests 必须证明：空账本返回已知零；计划→approval→成功、approval 拒绝、注入仅 hash、unknown/权限/risk/三类剩余预算/重复 step intent fail-closed、tool failure/cancel、pause/resume、checkpoint/restart、receipt replay、duplicate event/replay conflict 与 rollback 的 durable 语义；状态读本身不增加任何计数，Room/SQLite reopen 后同一聚合回读。P8-A 既有越权、工具错、预算、取消、重放、rollback 合同持续回归。

只完成上述 test-only harness 与不可见生产状态双端闭环即停止。可见 Agent UI、production executor、真实研究/文件/系统工具、Provider、HTTP、外部副作用、P9/P10、OPPO、Windows、发布均需新的独立合同。
