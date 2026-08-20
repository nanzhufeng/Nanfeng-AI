# 南枫 AI P3 RealTextExecutionCoordinator 合同

日期：2026-08-15  
状态：纯 core、未注册、默认 fail-closed；只可由 fake/stub 与 Android Room 测试端口验证

## 范围与顺序

`RealTextExecutionCoordinator` 只接收已经由 P3 preflight 生成的 `ReadyPlan`。它按下列顺序协调未来端口：

```text
ReadyPlan + cancellation
→ usage reservation port
→ injected transport normalized events
→ runtime receipt port (start → partial* → terminal)
→ failure/cancel release
```

它不会重新取得预检的瞬时文本，因此也不能把 ReadyPlan 直接变成 P3 Provider HTTP 请求。将来的可见确认 owner 如需桥接 ephemeral text、opaque credential provision 和既有 Provider transport，必须另立授权增量。

## Fail-closed 与隐私

- 默认 usage reservation port 拒绝；因此默认实例不会调用 transport、runtime 或任何网络。
- 默认 transport 返回 `DISABLED_NO_NETWORK`，没有 HTTP、模型、endpoint、credential、Key、Authorization、文件或附件能力。
- coordinator、其结果和 runtime receipt 只保留 P3 execution/invocation/attempt/fingerprint、route、受限终态和安全错误码；不得保留输入/输出正文、Key、credential、URI/path、附件、payload、token、费用或账本条目。
- `TextDelta` 只作为 runtime receipt port 的一次调用参数；端口实现不得将它放进 receipt。core 没有生产 Room/SQLite/真实账本接线，也不改 schema；下文唯一例外是未注册的 Android Room 测试端口。

## 终态、取消与幂等

- 正常流严格为 `Started → TextDelta* → Completed`。缺少 Start、重复 Start、或者 transport result 与计划绑定/终态不一致，安全失败关闭。
- 失败、取消、禁用 transport 和 runtime receipt 拒绝均请求 release；没有自动重试、后台续发或假回复。
- 同一 execution 的相同安全计划在同一 core 实例内只回放终态，不再次 reserve/start/transport/release；任一安全计划字段不同则冲突。该缓存仅进程内，重启恢复是未来持久 owner 的独立问题。
- 该 core 没有普通聊天 AppContainer、ViewModel、Compose/UI、Composer、发送按钮、Provider、HTTP、Key、附件读取/外发或生产账本/数据库接线。唯一例外是 P2-M 既有可见确认后的后台桥接：它只注入默认拒绝的 ports，不能改变 P2-M UI/结果或发起真实执行。

## Android Room 测试注入端口

- `RoomRealTextExecutionCoordinatorTestPortAdapter` 同时实现 runtime receipt 与 usage reservation port，但只能由 Android Room/Robolectric 合同 fixture 显式构造；它没有 AppContainer、ViewModel、P2-M bridge 或聊天入口注册。
- fixture 必须先建立 P3-I `PREPARED` receipt；port 只以 plan 的 execution/invocation/attempt/fingerprint 与既有 receipt 比对后转换 `PREPARED → RUNNING → terminal`。`TextDelta` 只校验 `RUNNING` 并在调用栈中丢弃，不创建 assistant message、不保存正文、更不续发。
- reservation/release 各自向 P0 append-only ledger 追加 `BUDGET_RESERVATION`/`BUDGET_RELEASE`；同一 reservation 或 release 重放回 `Replayed`，任一不同事实或第二种 release 都安全拒绝。端口可被一个外层 Room transaction 包裹，回滚时 receipt 转换与 ledger append 一同回滚。
- 重建端口只读既有 receipt 与 ledger，不自动执行 transport、重试或终态化。它不是 P2-M 默认拒绝端口的替代品，也不改变 P0 当前无 production caller 的边界。

## 验证边界

定向 JVM fake/stub 合同验证 start→partial→terminal、失败/取消 release、同计划 replay/冲突、默认拒绝和敏感字段排除；Android Room/Robolectric port 合同额外验证重建 readback 与外层事务回滚。它们不证明真实 Provider、费用、附件 egress、UI、设备或发布。
