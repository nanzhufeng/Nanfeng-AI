# Desktop 提醒／计划监控／系统通知验收矩阵

> 日期：2026-09-01
> 当前裁决：**本地 owner 已完成，受信任签名下的系统通知真点仍待验。** ad-hoc 开发包无 Team ID，不能把自动测试或 legacy 通知投递冒充现代 macOS 热／冷点击闭环。

## 所有权与公开路径

| 概念 | 唯一所有者 | 公开路径 | 禁止的平行路径 |
|---|---|---|---|
| 计划与运行 | `desktop_reminders_v1.rs` | 审阅草案 → confirmed plan → due claim → run／usage／notification receipt | 未确认草案直接执行、UNKNOWN 自动重发 |
| 通知发送与点击 | `desktop_reminder_notification_v1.rs` | SQLite pending → Rust send → 固定事件／启动队列 → SQLite 二次校验 → 精确 plan | JS notify/onAction、信任 payload 直接导航 |
| 完全退出后唤醒 | `desktop_background_runtime_v1.rs` | content-free desired state → 固定 LaunchAgent → headless cycle 重读 SQLite | 把会话／计划正文、Prompt、Key 或任意命令写入 plist |
| 正式点击通道 | `UNUserNotificationCenterDelegate` | 有 Team ID 的受信任签名包 response delegate | ad-hoc 包冒充正式冷启动验收 |

## 验收矩阵

| 项目 | 结果 | 证据与边界 |
|---|---|---|
| 草案、计划、DST、错过策略 | 通过 | Rust owner 覆盖确认前不运行、一次性／周期性、pause／resume／delete、FAILED／UNKNOWN 与显式 retry。 |
| payload 白名单 | 通过 | 只序列化 `clickId/route/planId/workspaceId/conversationId`；严格拒绝额外字段；无 body、prompt、path、provider、key、attachment。 |
| 固定路由与精确目标 | 通过 | 只接受 `desktop-reminder-plan`；点击后重读 SQLite，错 route、跨 workspace、conversation 不匹配或 plan 已删均 no-op。 |
| 热／冷启动队列 | 代码与单测通过，签名真点待验 | delegate 在 setup 早期安装；队列有界、clickId 去重；无 Team ID，不能取得现代通知真实点按回调。 |
| 完全退出后调度 | 通过 | ACTIVE／RUNNING plan 或历史整理开启时安装 60 秒固定 LaunchAgent；每次唤醒重新检查 owner；无需要时立即撤销。 |
| launchd 隔离实跑 | 通过 | 唯一 `/tmp` home／root／script／label 完成 bootstrap → marker → bootout；1/1 通过，plist 与 agent 无残留。 |
| 前后台并发 | 通过代码门禁 | 应用数据根 `.runtime-owner.lock` 独占；headless 唤醒遇前台实例立即退出，不会把前台 Attempt 误恢复为 INTERRUPTED。 |
| 全量隐私删除 | 通过代码门禁 | 删除提交后立即重新计算 desired 并撤销 agent；撤销失败只记 content-free `REMOVE_FAILED`。 |
| 通知多条／重复／已删除 | 投递和单测通过 | 多条投递已有隔离证据；重复点击幂等、已删除 no-op 有 Rust／Node 合同；系统层真实重复点按仍待 Team ID。 |
| ad-hoc 原生降级 | 通过（有限） | 授权提示和安全通知投递已实看；legacy 横幅不等于现代通知中心持久冷启动合同。 |
| 受信任签名现代通道 | 阻塞 | 当前 bundle `TeamIdentifier=not set`；未使用真实签名凭据绕过用户边界。 |

## 当前自动门禁

- Node：`128 passed / 0 failed / 0 skipped / 0 todo`。
- Rust 默认：`161 passed / 0 failed / 0 ignored`；launchd feature 验收另为 `1 passed / 0 failed / 0 ignored`。
- lint、typecheck、protocol golden、静态 build、`cargo check`、scoped `git diff --check`：通过。
- Protocol semantic hash：`ad41c1ee6aa64b9e2f218034dbefccc333c43fb923b874b12ff51ce5972d4031`。

## 数据与外部边界

- launchd 验收只使用全新 `/tmp` 路径和唯一 label；未读取正式 Desktop 数据、附件、Key、真实账号或误建会话。
- 未调用真实 Provider／Google／Supabase；未连接、安装或操作 OPPO；未运行任何 `connected*AndroidTest`。

## 下一步

用具有有效 Team ID 的正式或 Apple Development 签名独立验收包，重做“到期投递 → 热启动点按精确 plan → 完整退出 → 冷启动点按精确 plan → 多通知／重复／已删除 no-op”。该项完成前，只能写“本地 owner 完成、外部签名验收待完成”。
