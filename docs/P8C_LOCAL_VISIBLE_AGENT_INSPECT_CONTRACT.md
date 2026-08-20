# P8-C 本地可见受控运行与退出审计合同

日期：2026-08-13  
状态：完成。Android 最小可见本地闭环与 Desktop 用户可达只读 inspect 页面均已验证；P9 尚未开始。

## 唯一所有者与边界

```text
Android Settings → P8ControlledAgentViewModel → P8CProductionLocalAgentController
→ ControlledAgentRuntime → RoomAgentLedger
```

唯一 production action 是 `p8c_local_ledger_inspect`：无用户/模型输入，只读 secret-free P8 ledger aggregate。它声明 `READ_ONLY`、`LOCAL_READ`、`NONE` side effect、预算 `1/1/0`、不可 rollback；不会访问 Provider/HTTP/Key、系统或任意用户文件、跨应用、购买、删除、外发或业务正文。UI 必须固定说明“本地受控运行 / 未连接模型与外部工具”。

`LocalTestOnlyAgentToolRegistry` 仍只用于测试。release Android `AppContainer` 只注册该精确的内建 controller，不能注册 fixture registry；Desktop 新增的 `inspect_p8_agent_runs` 只返回安全 Run/Step/Event/Checkpoint metadata，绝不 start/approve/execute/cancel/resume 或调用工具。

## 状态与恢复

- 用户先“创建本地只读计划”，只写 `PLAN_ACCEPTED`；随后 90 秒内再显式确认，才写 `PLAN_APPROVED` 并执行。
- approval token 绑定 plan（其内绑定 run/tool/input hash），只在内存保存、过期和 one-shot；进程重建丢弃 token，绝不自动 approve 或 execute。
- Run/Step/Event/Checkpoint/Receipt 的真值只来自 ledger。列表固定 stable durable order；`null` 为未知、`0` 为已知零，UI 不用默认值掩盖。
- 用户可对 PENDING/RUNNING/PAUSED Run 显式 pause/resume/cancel；重建只回读，rollback 仅保留旧 test-only reversible fixture 合同，production action 没有 rollback 或外部撤销声明。

## P8-C 停止条件

1. Android 真实 UI 证明空账本、计划、显式确认成功、暂停→重启→恢复、取消→重启和 durable event order。
2. Desktop 需要完成用户可达的只读 inspect 页面，并在实际 `.app` 回读“无 production executor”；不得新增执行 command。
3. 定向与全量 JVM、lint、正式 Debug/Release 签名、同签名 emulator 覆盖/base.apk hash；Desktop frontend/Rust/Tauri/ad-hoc verify 均分别记录。
4. UNKNOWN、budget、injection、duplicate receipt/event、failure/cancel、timeout/crash、rollback 等仍由 P8-A/B test-only harness 回归，不得把 fixture 说成真实工具成功。

P8-C 未满足以上全部条件前，不进入 P9。真实 Provider、外部工具、高风险动作、OPPO/Windows 和发布仍为独立授权门。
