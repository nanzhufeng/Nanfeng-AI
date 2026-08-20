# P6-E 临时聊天双端退出证据（完成）

- 状态：**P6-E 已于 2026-08-14 完成全部当前授权范围内的双端退出门；下一唯一阶段是 P6-F Core。** 本结论不代表 P6-F、P6-F2、P6-G、Provider、账号同步、OPPO 或总项目完成。
- 边界：全部证据均为本机 private-copy、本地数据库与本地 UI；未读取 Key、未配置 Provider、未发起 HTTP、未外发图片或文件、未操作 OPPO、未修改图标。

## TEMP 真实路径与重启读回

| 平台 | 真实入口与动作 | 发送后 / 重启读回 | 证据 |
|---|---|---|---|
| Android 正式签名 Debug | TEMP → `＋` → DocumentsUI 选择 Markdown；另经 Photo Picker 选择受控 PNG；两项均成为 `TEMPORARY_SESSION` private-copy 后分别发送。 | force-stop、重新启动、进入同一 TEMP 后可见两条带附件的本地消息；NORMAL 未被改写。TEMP model override 也通过真实 UI 写入并在重启后读回。 | `evidence/p6e/android-emulator-p6e-temp-attachments-final.png`，SHA-256 `35683b3719abb4dfb83fe683905acdb82b8df0fb4f934d1a38003e59713c81cb`。 |
| Desktop 最新本地 `.app` | TEMP → `＋` → 原生 Open panel，分别选择受控 Markdown 与 PNG；UI 显示安全 MIME，发送后草稿清空。 | 完整退出并重新启动，默认 NORMAL；切回 TEMP 后仍显示 `P6E_TEMP_FILE_DESKTOP` / `text/markdown` 与 `P6E_TEMP_IMAGE_DESKTOP` / `image/png`，NORMAL 是不同会话内容。本地 model override 同样读回。 | 2026-08-14 macOS AX 与最新 bundle 窗口读回；原生 picker 明示“立即私有复制…不会保存路径或外发”。 |

## acceptance-only 固定 Clock / owner 维护

### Android

- 独立 `com.nanzhufeng.ai.p6eacceptance` 包使用既有正式证书，不覆盖 `com.nanzhufeng.ai` 数据；正式 Debug/Release 的 `BuildConfig.P6E_ACCEPTANCE=false`，不显示时间维护入口。
- 真实路径为“设置 → 数据与存储 → 会话管理 → P6-E 验收维护（仅测试包）”。固定、无参数 harness 只使用 TEMP owner、固定 Clock 与 private-copy owner；UI 回执为 `23h59 保留=true；24h 清理=true；附件=true；消息=true；标识=true`。force-stop/restart 后沿同一路径仍能读回。
- Room readback 为 `conversations/projects/knowledge_items/memories/temporary_conversation_recovery/temporary_conversation_messages/temporary_conversation_attachments = 0/0/0/0/0/0/0`；普通历史、Project、Knowledge、Memory 与 TEMP 残留均为零，本阶段没有搜索或 cache 写入入口。
- 截图 `evidence/p6e/android-acceptance-maintenance-restart-20260814.png`，SHA-256 `090d360140db500672d12f25d103add26e41cc86b6009e0e8317612150be8722`。

### Desktop

- 只有精确环境标识 `NANFENG_AI_P6E_ACCEPTANCE=1` 才使用固定 `/tmp/nanfeng-ai-p6e-acceptance` app-private root；普通生产 app 继续使用既有 app-data root，且不显示 maintenance card。Tauri 命令无时间、owner、路径或内容参数，Rust 还会在无 marker 时拒绝。
- 独立 bundle `南枫 AI P6-E 独立验收.app`（identifier `com.nanzhufeng.p6eacceptance`）消除了普通同名实例混淆。真实 UI 经“设置 → 数据与存储 → 会话管理”运行后显示六项全 true：`23h59 保留`、`24h 清理`、`附件`、`消息`、`标识`、`普通面`；完整退出并重开同一 bundle 后 receipt 仍在。
- receipt 为 `/tmp/nanfeng-ai-p6e-acceptance/p6e-acceptance-receipt.json`，仅含六个布尔值。SQLite 读回 `workspaces/workspace_exchange/workspace_assets/import_journal/domain_intents/object_provenance/model_metadata/conversation_attachments/sync_account_metadata/sync_intents/temporary_recovery/temporary_attachments` 全部为 0。
- 运行截图 `evidence/p6e/desktop-acceptance-maintenance-run-20260814.png`，SHA-256 `d075b22fdf954227df69ebc73582357f7defff175e0bc842a649539187df0d1c`；重启截图 `evidence/p6e/desktop-acceptance-maintenance-restart-20260814.png`，SHA-256 `b2ddbc392e516afc22ac43a7e8122c986297fa00fbc1f9239abecd0e808fa079`。

## Overlay 真实抽样

- Desktop 最新普通 `.app`：TEMP model popover 以 Escape 关闭并还焦 trigger；attachment popover 外点关闭；profile menu 以 Escape 关闭并还焦；conversation 右键菜单以 Escape 关闭；危险删除确认以 Escape 取消，未执行删除。Node topmost overlay 合同同步通过。
- Android 最新正式签名 Debug：drawer 由系统 Back 关闭；TEMP model dialog 由 Back 关闭；TEMP attachment bottom sheet 由 scrim 外点关闭；会话长按 sheet 显示置顶/重命名/加入项目/删除；危险“移入回收站？”确认由 Back 取消，原会话仍在。未清数据、未删除用户对象。

## 当前包表面记录

- Finder：`evidence/p6e/desktop-finder-current-bundle-20260814.png`，SHA-256 `3063fa18abdd0f76e7b78416e02d062ef219feee428ad44c7f9a42d32e73a34b`。
- Dock：`evidence/p6e/desktop-dock-current-bundle-20260814.jpg`，SHA-256 `72519d9fe25179522d0aecb29dd6ffdbf7010b680d8128889ab2adc056e1170e`。
- Android Launcher 模拟器近似记录：`evidence/p6e/android-emulator-launcher-scale150.png`，SHA-256 `9bd2750f0d61b6ecd48f42fa5d27773c2c4f201683d8a1322a6b3e260d003caf`。它不构成 OPPO Find N5 外屏/内屏/折叠或最终硬件图标结论。

## 最终自动回归与产物冻结

- Desktop：`npm run lint`、`npm run typecheck`、`npm test`（31/0）、`npm run build`、`cargo fmt --check`、`cargo clippy --all-targets -- -D warnings`、`cargo test --lib`（37/0）、普通与 acceptance Tauri `.app` bundle 全部通过。普通 executable SHA-256 `884ce50d914e8b3f05116f306ecbc2459bbb3bdef904938c3786957fadf686a1`；acceptance executable SHA-256 `47a324d6e6a046c0faa0462827cd4d2d76ad7f180b6c0297f0c8e3dc33f5e7e4`。两者均通过 ad-hoc `codesign --verify --deep --strict`，但不是 Developer ID、notarized 或发布包。
- Android：最终全量 JVM 为 261 tests / 0 failures / 0 errors；`lintDebug --rerun-tasks` 单 worker 完成；Acceptance/Debug/Release assemble 通过。Debug SHA-256 `b65892a5686fa414bca6feeb013087f05b7ae62fd410ed2662c712ba57ea4f3e`，Release `2d1ad47d87d4cd84b74d2d27b5c3cf4d8a8a182f31eb39191e7d32e818ab87c7`，Acceptance `25afbc07acc664051a5488df508d165a9b4d6b834bb31cdc1c5a601661573cfa`；三包均 v2/v3=true，证书 SHA-256 均为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- Debug `install -r --user 0` 后回拉 `evidence/p6e/android-installed-base-20260814-p6e-final.apk`，hash 与 Debug 完全一致；Acceptance 安装后回拉 `evidence/p6e/android-acceptance-installed-base-20260814-p6e-final.apk`，hash 与 Acceptance 完全一致。

## 退出判断

P6-E 合同要求的 TEMP 隔离、附件 private-copy、model override、23h59/24h owner 维护、双端 restart/readback、普通面零泄漏、真实 overlay 抽样、当前包表面记录、最终测试/静态检查/构建/签名/安装 hash 均已有可复核证据。P6-E 因此完成；路线按总蓝图进入 **P6-F Core**，不得跳到 P6-F2 或 Model Router。
