# Desktop Google 账号与南枫云加密同步交接

日期：2026-09-01

## 本轮已实现

- Google 系统浏览器 OAuth PKCE 与精确 loopback `state` 校验；Google ID Token 再交换为 Supabase 应用会话。正式地址必须 HTTPS，localhost 只能在显式 Mock 模式下使用。
- 账号会话、数据密钥、恢复包装材料仅保存在 App 专属 macOS Keychain service；SQLite 只保存不含 Token／Key／正文／路径的状态、意图、回执、通知与诊断。会话凭据使用有界可变长度适配器，密钥材料仍严格为 32 bytes。
- 恢复码只显示一次，用户明确确认后才允许选择首次同步方向。更换／丢失恢复码时，必须由当前设备解锁旧材料，逐个重包装已选云端对话并回读；中断日志保存在 schema v28，重启后仍显示“继续未完成的重加密”。
- 仅对用户在会话右键菜单中明确选中的纯文本对话做端到端加密同步；附件、工具结果、草稿、未完成消息和已删除内容均在加密前拒绝。云端 revision 冲突停止覆盖；提交结果未知停止静默重发，只允许显式回读对账。
- 新设备只在用户显式加载云端列表、选择文档并输入恢复码后恢复；解密结果总是写入新本机工作区，不覆盖现有数据，同一包重试为幂等回执。
- 设置页已对齐 Android 灰底／亮白卡片层级，字段标题在输入框上方；身份、恢复码、首次方向、已选同步、定期同步、安全通知与诊断均可见。系统通知只在权限已允许时发送固定安全状态，不主动索要权限。

## 验证证据

- Node 全量：`119 passed / 0 failed / 0 skipped`；Rust 全量：`139 passed / 0 failed / 0 ignored`。
- `npm run lint`、`npm run typecheck`、`npm run build`、`cargo check` 和 macOS release bundle 通过。
- localhost 集成覆盖完整 OAuth 交换、精确 state、恢复码一次显示、选中对话上传与回读、版本冲突、未知提交不重发、恢复码轮换以及新设备恢复且不覆盖旧数据。
- 应用内 Browser 实看宽屏与 `720×900` 窄屏，无横向溢出；实点会话右键“设置加密同步”会进入账号页，Web 预览未联网。
- 开发包为 [南枫 AI Desktop.app](../desktop/src-tauri/target/release/bundle/macos/%E5%8D%97%E6%9E%AB%20AI%20Desktop.app)，版本 `0.6.0-p6d-dev`，Bundle ID `com.nanzhufeng.ai.desktop`；`codesign --verify --deep --strict` 通过，但为无 Team ID 的开发签名，不是 Developer ID／公证发行包。

## 边界与后续

- 本轮严格未访问真实 Google／Supabase，未读取真实 Desktop 账号、Keychain 条目或用户数据。生产 RPC（读、提交、列表与头像代理）仍需与真实 Supabase 部署做合同验收；不得把 localhost Mock 写成真实云端已通。
- 由于现有开发包与正式窗口仍共用 Bundle ID，本轮没有启动 Tauri 原生 GUI，避免再次命中正式数据。若继续原生人工 QA，必须同时使用唯一显示名、唯一 Bundle ID 和已证明位于 `/tmp/nanfeng-ai-account-sync-acceptance.*` 的数据根。
- 未运行任何 `connected*AndroidTest`，未连接、安装或操作 OPPO。
