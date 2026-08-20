# P6-K Desktop 正常 Settings 无正文回读失败矩阵（2026-08-16）

## 结论

macOS Keychain Access GUI 已可操作，Desktop 无正文 Settings readback 不再是“等待解锁”的外部条件。当前最终 bundle 进程和 ad-hoc strict 签名均已核对，但窗口内容连续两次保持纯白，无法进入正常 `Settings → 数据与导入`。因此历史的 Settings 可见 readback 不能关闭当前 bundle 的验收。

| 验收面 | 期望 | 本轮现场 | 结论 |
| --- | --- | --- | --- |
| 最终 bundle 身份 | 运行当前最终 bundle，避免旧进程 | PID `90876` 的可执行文件为 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app/Contents/MacOS/nanfeng-ai-desktop-spike` | 通过 |
| bundle 完整性 | 代码签名可验证 | `codesign --verify --deep --strict` 成功 | 通过 |
| 原生窗口 | 出现 Tauri WebView 与 chat-first shell | 仅原生标题栏“南枫 AI · 本地工作台”，内容区纯白 | 失败 |
| 正常 Settings 路径 | 进入 Settings → 数据与导入 | 无 Accessibility child/UI 控件，无法安全导航 | 未执行，重新打开 |
| P6-K 匿名 aggregate | 仅读取 task/receipt/message/media/profile 的计数与状态 | 未显示、未读取；不以数据库或 command 绕过正常 UI | 未执行，重新打开 |
| 数据不变 | 不触发导入任务 mutation | 未点击窗口、未调用 command、未打开 picker、未重试/删除真实包 | 保持 |

## 本轮边界

- 不输出正文、标题、ID、文件名、账户资料或 Key。
- 不读写 Key、不发 HTTP、不操作 Android、emulator 或 OPPO。
- 下一步仅限先诊断最终 bundle 的白屏/WebView 启动可见性；恢复 UI 前不得用 SQLite、注入或非正常 command 替代本验收。
