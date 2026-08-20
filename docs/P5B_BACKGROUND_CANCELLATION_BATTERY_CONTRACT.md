# 南枫 AI P5-B Android 后台、取消/恢复与电量产品化基线合同

日期：2026-08-13  
状态：P5 的第二个独立增量；不是 P5 或项目终点。Schema 保持 17。

## 目标与唯一治理入口

`TaskExecutionPolicy` 是 P5-B 对所有可长运行本地任务的唯一治理入口；它只定义执行所有权、生命周期、恢复、网络与电量边界，不合并 Markdown、JSON、PDF、Web、Eval 或 Conversation 的领域表、ID 与语义。`TaskRecoveryAudit` 只将不可能安全续跑的持久运行态映射为稳定终态，不重放输入或副作用。

| 任务 | Owner / Dispatcher | Activity 重建 | 进程死亡 / force-stop | 重试与副作用 |
| --- | --- | --- | --- | --- |
| Markdown / JSON 导入 | 对应 Adapter；`Dispatchers.IO` | ViewModel 不清除时继续；取消协作传播 | `PARSING` 变 `FAILED(INTERRUPTED)` | 仅用户点击，从私有副本解析；不重复确认 Knowledge |
| PDF 文本导入 | PDF Adapter；`Dispatchers.Default` 解析、`IO` 文件/Room | 同上，逐页检查取消、显示真实页数 | `PREFLIGHT/EXTRACTING` 变 `FAILED(INTERRUPTED)` | 仅用户点击，从私有副本；确认幂等 |
| Web 文本快照 | Web Adapter；`Dispatchers.IO` | 同上；取消关闭连接/流 | `QUEUED/FETCHING/EXTRACTING` 变 `FAILED(INTERRUPTED)` | 必须重新勾选用户确认；绝不自动网络重试或重放抓取 |
| 离线 Eval | Eval Owner；`Dispatchers.Default` | ViewModel 不清除时继续 | 不持久化 RUNNING；不伪造续跑或生成半成品 | 用户重新运行，append-only 新 run |
| Conversation local fixture 流 | Runtime state machine；ViewModel coroutine | Activity 旋转/resize 不取消 | `STREAMING` 追加 `FAILED(INTERRUPTED)`，保留部分输出 | 用户明确 RETRY 新建 Invocation，绝不重放旧 Intent/Provider |

短的 Capture 保存、导出、设置和 Room 回读是前台协程；不注册为后台任务，也不保持进程存活。

## Android 后台与电量决策

本阶段**不引入 WorkManager**。审计后没有任何同时满足“无用户逐项确认、跨进程后可安全幂等重放、无需网络人工授权、必须由系统延迟调度”的任务：所有导入均需逐项确认，网页需一次性前台确认且不得自动重试，Eval 与 fixture 流不得伪造继续。因而也不得加入 WorkManager/JobScheduler、前台服务、WakeLock、Alarm、BOOT receiver、周期轮询或保活。

Web 只在用户处于前台并明确确认后单次访问；网络变化、后台、冷启动和系统恢复均不自动继续。超时或用户取消必须断开 `HttpsURLConnection` 与输入流。没有 Provider HTTP、Prompt、RunSpec、Key、费用或图片外发；`OpenRouterEgressPolicy.Disabled` 保持。

## 取消、恢复与 UI

- 用户取消先标记持久任务为 `CANCELLED`，再取消所属协程；Adapter 在边界检查取消并且最终写入前复核取消态，因此迟到结果不能覆盖 `CANCELLED` 或写入 Knowledge。
- 完成后的取消是稳定 no-op；重复重试不会并发运行同一 task，已确认项依赖既有 intent 指纹，不会重复写 Knowledge。
- 轻量 UI 直接显示“仅在前台处理”“取消中/已取消”“被系统中断，可手动重试”；进度来自阶段或页数，错误以中文说明下一步。App-owned Dialog/选择面保持 `#FFFFFFFF`。
- Activity 旋转和 resize 只重组 Compose，不清除 Activity-scoped ViewModel；`onCleared` 取消尚未完成的协程并持久化 `CANCELLED`，不让后台隐藏工作继续。

## 验收与禁止项

自动合同覆盖政策矩阵、无 WorkManager/receiver/service/wakelock 合同、旋转与 ViewModel clear、进程/force-stop 映射、取消竞态、完成后取消、重复 retry、导入/PDF 不重复 Knowledge、Eval 不伪造继续及 Web 网络失败无自动重试。真实验证仅用 `emulator-5554`：取消、恢复、Web 失败、force-stop/冷启动；最终恢复 portrait / 1.0x / Capture。

不修改图标，不操作 OPPO，不清数据，不初始化 Git，不做 P5-C/P5-D/P6+。TalkBack 人工听觉仍是独立债务。
