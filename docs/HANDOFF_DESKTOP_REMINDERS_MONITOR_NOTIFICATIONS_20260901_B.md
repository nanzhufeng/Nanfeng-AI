# Desktop 提醒／计划监控／系统通知续作交接 B

## 当前结论

- 本增量的 Rust owner、Desktop UI、通知权限桥、备份／账号同步、Usage Ledger 与专项测试已实现，代码和自动回归均为零失败。
- **整体目标仍未完成，本增量也尚未完成验收。** Browser 宽／窄视觉、唯一 Bundle ID 的 Tauri 原生交互、localhost draft+monitor、系统通知授权／触发／点击路由、跨重启实跑、最终 bundle/codesign、验收矩阵和当前交接文档尚未完成。
- 2026-09-01 上下文门禁返回 `HANDOFF`（94.3%），因此必须换新任务再进入 UI／原生验收；不能把本文件当完成证明。

## 已实现

- schema 32 新增 `desktop_reminder_drafts_v1`、`desktop_reminder_plans_v1`、`desktop_reminder_runs_v1`，迁移可重入。
- 对话建议只在末尾成功 Assistant 且前一条 USER 命中明确未来提醒／持续监控意图时显示；按钮只创建 draft reservation。
- Qwen `QWEN_3_7_PLUS` 仅用于草案 refinement；严格 JSON，不符合意图为 `NOT_ELIGIBLE`。草案可编辑、确认、拒绝；未确认绝不创建 plan。
- OpenRouter `GPT_5_6_TERRA` 用于已确认 plan 的监控执行；复用现有 Provider／credential／streaming／usage owner。支持 ONCE、HOURLY、DAILY、WEEKLY、IANA 时区、DST、本地墙钟、RUN_ONCE／SKIP、暂停／恢复／删除、重启恢复 UNKNOWN 与显式重试。
- 通知 body 固定为不含监控结果正文的安全文案。权限未授予、设置关闭或发送失败均写 content-free suppression receipt；成功发送后才写 `SENT`。点击 extra 只携带 route、plan/workspace/conversation ID。
- 设置页显示草案、计划、模型、Token／费用事实、失败码与操作；Usage Ledger 增加“计划与提醒”分类；诊断页合并提醒诊断。
- 本机备份把运行中提醒状态降级为 `UNKNOWN`，并抑制恢复快照中的待发通知。账号加密同步只携带已确认计划的便携字段，不同步执行结果；第二设备恢复为 `PAUSED`，避免重复监控。

## 本轮验证

- `npm run lint`：通过。
- `npm run typecheck`：通过。
- `npm test`：`123 passed / 0 failed / 0 skipped`，含新增 `desktop-reminders.test.mjs` 3 项。
- `npm run build`：通过。
- `cargo check --manifest-path desktop/src-tauri/Cargo.toml`：通过。
- `cargo test --manifest-path desktop/src-tauri/Cargo.toml`：`154 passed / 0 failed / 0 ignored`。
- 定向备份 roundtrip 与恢复码轮换／新设备计划恢复测试均通过。

## 主要文件

- `desktop/src-tauri/src/desktop_reminders_v1.rs`
- `desktop/src-tauri/src/lib.rs`
- `desktop/src-tauri/src/desktop_local_backup_v1.rs`
- `desktop/src-tauri/src/desktop_account_sync_v1.rs`
- `desktop/src-tauri/src/desktop_app_settings_v1.rs`
- `desktop/src-tauri/Cargo.toml`、`Cargo.lock`
- `desktop/src-tauri/capabilities/default.json`、`permissions/default.toml`、`tauri.conf.json`
- `desktop/src/app.mjs`、`chat-shell.mjs`、`android-settings-shell.mjs`、`chat-shell.css`
- `desktop/tests/desktop-reminders.test.mjs`

仓库原本已有大规模 dirty worktree；不得把这些文件的整个 diff 归因于本轮。继续保持直接当前 checkout，不 reset／clean／checkout、不做全仓格式化。

## 下一任务固定顺序

1. 重新读取项目 `AGENTS.md`、本交接，以及已选三个工程 Skill；随后读取并使用 `frontend-testing-debugging`。如需操作原生 macOS UI，再读取 `computer-use:computer-use`。
2. 先跑 `npm run protocol:test`，并核对 `git diff --check`（只报告，不改无关 dirty）。
3. Browser 宽屏与窄屏实看：提醒设置页、手工草案 dialog、计划卡、失败／UNKNOWN、费用分类、末尾建议有／无两态；控制台和横向溢出必须为零。
4. 构建 release app 的**隔离副本**：唯一应用名、唯一 Bundle ID；启动时只允许：
   - `NANFENG_AI_DESKTOP_ORDINARY_CHAT_ACCEPTANCE=1`
   - 唯一 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.<unique>` 根
   - `http://127.0.0.1:<random-port>` localhost mock
   不得启动正式 Bundle ID，不得触碰正式 app-data。
5. localhost mock 根据请求模型返回：Qwen 草案严格 JSON；Terra 监控 SSE 成功／FAILED／UNKNOWN 场景与 provider usage／actual model。原生实跑至少覆盖：
   - 手工草案确认、一次性到期、重复计划、暂停／恢复／删除；
   - 生成草案→编辑→确认；拒绝无 plan；
   - FAILED 与 UNKNOWN 显式重试；
   - 完整退出／重启后计划仍在，运行中中断为 UNKNOWN；
   - 通知权限 fail closed；授权后成功通知；通知正文无结果；点击准确打开对应 plan；
   - Usage Ledger 与诊断回读。
6. 运行 release bundle、严格 codesign 验证；写 `DESKTOP_REMINDERS_MONITOR_NOTIFICATIONS_ACCEPTANCE_MATRIX_20260901.md`，更新 `ANDROID_DESKTOP_FINAL_COMPLETION_AUDIT_20260901.md` 与 `docs/CURRENT_HANDOFF.md` 顶部。
7. 只有以上全部闭环后，提醒增量才可写“完成”；Android→Desktop 整体仍保持进行中，下一项才是 unread read marker。

## 风险与待验证点

- `window.__TAURI__.notification.onAction` 在 macOS 打包态的事件 payload 形状尚未实跑；代码兼容 `event.notification.extra` 与 `event.extra`，但不能据此写点击闭环。
- 通知权限默认被 schema 32 安全关闭；用户明确开关或“系统通知权限”按钮才请求授权。不得在启动时弹权限。
- Rust scheduler 每秒只 wake owner、每次 claim 一个 due plan；多计划堆积会逐秒排空，需在原生隔离根回读确认。
- 当前系统通知插件从 Cargo 解析为 `2.4.0`；能力已收窄为 permission check/request/notify/listener，没有使用宽泛 `notification:default`。
- 尚未运行真实 Provider、Google、Supabase、真实 Key、正式数据、OPPO 或任何 `connected*AndroidTest`。
