# Desktop 提醒／计划监控／系统通知续作交接 C

## 2026-09-01 原生点击桥实施结论（优先于下方旧状态）

- 已新增项目内 Rust 通知 owner：受信任签名包优先使用 `UNUserNotificationCenterDelegate`，现代注册明确失败时才转 `NSUserNotificationCenterDelegate` 开发降级。前端已删除 JS plugin notify/onAction 依赖，发送前只重读 SQLite pending 投影，点击后再以固定 route 和 plan/workspace/conversation 不透明 ID 做 SQLite 二次校验。
- delegate 早期安装、固定 Tauri event、有界冷启动队列、handled click ID 去重、窗口 show/unminimize/focus、错 route／跨 workspace／已删 plan no-op 均已实现。通知 body 保持固定安全文案，userInfo 不含结果、Prompt、路径、Key 或 Provider payload。
- 唯一隔离 App `com.nanzhufeng.ai.desktop.nativeclick.build1788231149` 和 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.gCOVYf` 完成 macOS 授权提示、多条计划到期、localhost 安全回复、多个 `reminder-run-*` 的 `usernoted Delivering/Presenting`、完整退出与同根冷启动。legacy 降级通知在 macOS 26 没有持久进入通知中心，因此不能代替冷启动真点验收。
- **P0 仍未闭环。** 当前开发 bundle 为 ad-hoc，`TeamIdentifier=not set`，macOS `usernoted` 拒绝现代 UN client 注册。本轮没有使用真实签名凭据绕过用户边界，所以没有把热／冷真实点按写成通过，也没有开始 unread read marker。
- 当前自动门禁：Node `124/124`，Rust `157/157`，0 failed、0 skipped/ignored；lint、typecheck、protocol、build、`cargo check`、macOS bundle、严格 codesign、`git diff --check` 通过。最终开发 bundle 可执行文件 SHA-256 `c7acd23e5cf657de9d9ebe7ed1857102477b1532d586e4d2fe5387a7f1551883`。
- 逐项状态见 [Desktop 提醒／计划监控／系统通知验收矩阵](DESKTOP_REMINDERS_MONITOR_NOTIFICATIONS_ACCEPTANCE_MATRIX_20260901.md)。唯一下一步是取得有效 Team ID 的独立签名验收包，实做到期投递、热点、冷点、多通知、重复点击和已删目标 no-op；全部通过后才能宣告提醒增量完成。

## 历史结论（已被上节取代）

- 提醒草案、计划持久化、scheduler、通知发送、安全回执、Usage Ledger、Browser 宽窄屏与原生 localhost 成功链已经验收；启动读取被通知监听拖垮的问题已定位并修复。
- **提醒增量仍不能写完成，Android → Desktop 总目标也仍未完成。** P0 缺口是项目内 macOS 原生通知点击桥：当前 Tauri notification 2.4.0 的 desktop Rust 插件未注册 `register_listener`，JS `onAction` 在 macOS 会拒绝。仅捕获该错误、纯函数路由测试或 `SENT` 回执都不能冒充真实点击可用。
- 2026-09-01 上下文门禁为 `HANDOFF`（94.7%），本线程必须停止扩张；下一任务先完成原生点击桥，再补剩余原生失败态，最后才可进入 unread read marker。

## 本线程已完成与已验证

- Browser 使用实际 `desktop/dist` 加临时 Tauri mock，在 `1440×900` 与 `390×844` 验收提醒设置、手工草案、生成草案、编辑／确认／拒绝、暂停／恢复／删除、FAILED／UNKNOWN／显式重试、通知权限、Usage Ledger、诊断及对话建议有／无两态；控制台 0 error／warn，宽窄屏均无横向溢出。
- 修复窄屏“新建草案”按钮换行：`desktop/src/chat-shell.css` 为提醒 section header 按钮增加不收缩与 `white-space: nowrap`；390px 回读按钮为单行且页面无横向溢出。
- 唯一 Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.88850.mti0sw0t`、独立根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.hWOjwf` 与 localhost `127.0.0.1:59366` 完成原生手工确认、暂停／恢复、到期执行、完整退出／重启持久化、通知开关、系统通知发送和 Usage Ledger 回读。`系统通知验收` 与 `通知点击路由验收` 均回读 `SUCCEEDED + SENT`；实际 mock 模型为 `gpt-5.6-terra-mock`，每次输入 128／输出 32，费用 `$0.000321`。
- macOS 通知正文保持固定安全文案，不包含监控结果；`原生通知成功` 在设置关闭时回读 `SUPPRESSED + SETTING_DISABLED`，证明 fail-closed 分支。
- 启动错误精确定位为 `REMINDER_NOTIFICATION_LISTENER`：原代码让 `window.__TAURI__.notification.onAction` 的拒绝中断全部后续启动读取。现已增加安全阶段码，监听失败只降级并继续启动；全新 Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.92872.mti1j2cz`、根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.RxDnR1` 的最终隔离包实看完整启动、无启动错误。
- 新增 `desktop/src/reminder-notification-routing.mjs`，只接受 route、plan/workspace/conversation 四个字符串 ID，兼容直接 extra、嵌套 notification.extra 与 JSON 字符串，拒绝／丢弃其他字段；构建清单已显式复制该模块，避免打包空壳。
- 最终开发 bundle：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`；ad-hoc 签名，严格 `codesign` 通过，不是 Developer ID／公证发行包。主可执行文件 SHA-256 `97785d18bbc4e7ac33abae18fe89b8fc3b62ced5ab693995923c6a089d78bf95`。

## 自动门禁

- `npm run lint`：通过。
- `npm run typecheck`：通过。
- `npm test`：`124 passed / 0 failed / 0 skipped / 0 todo`。
- `npm run build`：通过，包含 `reminder-notification-routing.mjs`。
- `npm run protocol:test`：通过；semantic hash `ad41c1ee6aa64b9e2f218034dbefccc333c43fb923b874b12ff51ce5972d4031`。
- `cargo check`：通过。
- `cargo test`：`154 passed / 0 failed / 0 ignored`。
- `npm run bundle:macos`、`codesign --verify --deep --strict`：通过。
- `git diff --check`：通过。

## 历史 P0 计划（已被上节取代）

1. 重新读取项目 `AGENTS.md`、本交接、固定三个工程 Skill、`frontend-testing-debugging` 与 `computer-use:computer-use`；先跑 protocol gate 和 `git diff --check`。
2. 不再依赖 `window.__TAURI__.notification.onAction` 作为 macOS owner。当前官方 desktop 插件初始化只注册 notify／request_permission／is_permission_granted；`register_listener` 不在 desktop invoke handler 中。
3. 在项目内实现最小 macOS 原生桥，优先方案是由 Rust owner 统一发送通知并安装 `UNUserNotificationCenterDelegate`（或等价原生实现）：
   - userInfo 只携带固定 route 与不透明 plan/workspace/conversation ID，不含标题以外的结果正文、Prompt、路径、Key 或 Provider payload；
   - 点击后向 `main` window 发一个固定 Tauri event；前端继续复用 `reminderNotificationExtra` 与 `routeToReminderPlan`；
   - 点击需激活／显示窗口，切到设置 → 提醒，选择并滚动到精确 plan；无效 route／ID 必须 no-op；
   - 安装 delegate 或发送失败不得阻断工作区启动，必须落 content-free safe code／诊断；
   - 不可与现有 plugin delegate 争抢而导致通知发送或权限回归。若必须替换发送 owner，先删掉重复 plugin notify 路径，保持一次发送／一次回执。
4. 增加 Rust／Node 集成测试：delegate payload 白名单、错误 route no-op、精确 plan 事件、窗口激活事件、重复点击幂等、失败不阻断启动；保留现有纯路由测试。
5. 使用全新 Bundle ID、全新 `/tmp` 根和 localhost mock 做原生真点：计划到期 → 系统通知出现 → 点击 → App 激活并准确打开对应 plan。回读 `SENT` 与所选 plan ID；通知正文不得包含结果。
6. 再补原生 FAILED、UNKNOWN 显式重试、运行中完整退出恢复 UNKNOWN，以及重复计划／暂停／恢复／删除的原生证据。Rust 单测已经覆盖不等于这些原生交互已验。
7. 只有 P0 与上述原生缺口闭环后，才写 `DESKTOP_REMINDERS_MONITOR_NOTIFICATIONS_ACCEPTANCE_MATRIX_20260901.md`，更新最终审计并将提醒增量标为完成；之后才能进入 unread read marker。

## 当前改动与工作树边界

- 本线程触及：`desktop/src/app.mjs`、`desktop/src/chat-shell.css`、`desktop/src/reminder-notification-routing.mjs`、`desktop/scripts/build.mjs`、`desktop/tests/desktop-reminders.test.mjs`、`desktop/tests/p6k-desktop-startup-readback.test.mjs` 及本交接／顶部路由／最终审计。
- 仓库原本已有大规模 dirty worktree，尤其 `app.mjs`、`chat-shell.css` 等包含前序增量；不得把整个 diff 归因于本线程，不 reset／clean／checkout、不做全仓格式化。
- 未读取正式 Desktop 数据、用户附件、Key 或真实账号；未调用真实 Provider／Google／Supabase；未连接、安装或操作 OPPO；未运行任何 `connected*AndroidTest`。

## 临时资源状态

- 本线程结束前已按精确会话／PID 停止最终隔离 App、旧提醒验收 App、localhost mock 与 Browser 临时 HTTP 服务；没有终止正式 `com.nanzhufeng.ai.desktop`。
- 临时目录保留作证据；续作不要用宽泛删除命令。若重新启动验收，继续使用全新 Bundle ID、全新 `/tmp` 根与随机 localhost 端口。
