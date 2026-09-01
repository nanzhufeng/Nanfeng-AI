# Desktop 未读与后台运行时验收记录

> 日期：2026-09-01
> 范围：Android read marker／多会话完成语义，以及 macOS 用户退出后的生成排空、完全退出后的提醒与历史整理唤醒。

## 已实现

- schema 33 的 `desktop_conversation_read_markers_v1` 按 workspace／conversation 保存 read 与 completed watermark、最后完成消息 ID 和手动未读时间。
- 旧会话首次迁移默认为已读；完成事件按消息 ID 幂等；Compare retry 或同题新 Attempt 的新完成消息可独立推进水位。
- 左侧列表只显示一个主题色未读点；手动未读只在 pinned 或 recent 各自分区内提前，不跨分区；右键“未读”位于 pin 后。
- 用户切到别的会话后，后台会话的增量／完成只更新自身状态与未读，不抢回当前路由。真正打开会话清除自动和手动水位。
- schema 34 的 `desktop_background_runtime_v1` 只保存 desired／installed／safe code／label／时间，不保存业务内容。
- 普通退出时隐藏窗口并排空在途任务；30 分钟发取消，31 分钟仍未结束则退出并在下一启动恢复 UNKNOWN。
- ACTIVE／RUNNING reminder 或 history library 开启时安装固定 LaunchAgent；headless cycle 只重读 SQLite owner。应用根独占锁阻止前后台同时恢复同一数据库。
- 全量隐私删除、暂停／删除最后计划或关闭最后一个后台消费者后立即撤销系统 agent。

## 验证

| 层级 | 结果 |
|---|---|
| Node UI／路由／权限／源码合同 | 128/128，0 failed，0 skipped／todo |
| Rust owner／迁移／备份／恢复 | 161/161，0 failed，0 ignored |
| launchd feature 隔离实跑 | 1/1，0 failed，0 ignored |
| lint／typecheck／protocol／build／cargo check | 通过 |
| 系统残留复检 | 唯一 `/tmp` agent 的 launchctl service 与 plist 均不存在 |

## 诚实边界

- 普通回答只保证“用户正常退出 UI 后继续排空，完成后进程退出”；强制结束或崩溃不继续网络请求，下一启动恢复 UNKNOWN 且不自动重发。
- 完全退出后的系统唤醒仅服务于用户已确认的提醒和已开启的历史整理，不后台续写任意普通聊天。
- 本轮没有读取正式数据、Key 或真实账号，没有调用真实 Provider，也没有操作 Android 设备。
