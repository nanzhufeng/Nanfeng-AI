# P6-D macOS 实际 GUI 黑箱证据

## 环境与边界

- 被测对象：`南枫 AI Desktop.app`，Tauri `tauri://localhost` 实际 webview；未以 browser localhost 代替。
- 数据：仅临时、非敏感 exchange fixture；未读取 Key、未调用 Provider/OpenRouter、未产生费用、未处理 Prompt/RunSpec 或图片。
- SQLite：所有导入、领域写入、历史和 preflight 均走已有 Rust `DesktopWorkspaceStore` typed command；未直接打开或复制 SQLite 文件。

## 已实际通过的链

1. 通过 macOS 系统 Open picker 导入 fixture。先选已导入包，界面显示“已导入为独立工作区；不执行隐式合并”的可恢复中文错误；再选隔离 fixture 成功，并修复了导入后未自动选中新 workspace 的 GUI 缺口。
2. 在实际窗口创建 Project、Knowledge、Memory 和显式 RELATED relation；Project/Memory/relation 原先没有可达 GUI，已补入同一 typed-command 边界。正文保持 text IR。
3. 实际窗口执行 Cmd+Z/Cmd+Shift+Z 的 relation undo/redo。黑箱发现“撤销刚创建对象”在 before snapshot 不含对象时被错误拒绝；Rust 修复为只在 snapshot 含该对象时调整 revision，并增加 `undo_and_redo_of_a_created_object_do_not_require_it_in_the_before_snapshot` 回归测试。
4. 实际窗口软删除 Knowledge，打开回收站并恢复，得到 `r3`；relation redo 后为 `r2`。强制终止后重启 `.app`，当前 workspace、`r3` 和 `r2` 均仍在，未打开未保存编辑 dialog，未自动重放 intent。
5. 通过系统 Save picker 导出 exchange。物理文件为 3591 B；Rust GUI 回读、Node `preflightPackage` 均得到 semantic hash `4962c40914b533c5be6ee175e6569b9a3f6b850ef03ca06bb0cf87c0c1adc0f5`、8 entries、`hasHighSensitiveData=false`。Android 定向 `P6AExchangeContractsTest` 也通过；它是跨端严格协议门，不把 Android Room 写入说成 Desktop 导入验收。
6. Expanded 三栏、Inspector 折叠/展开、纯白 About dialog、Tab/Shift-Tab 焦点落在 dialog 关闭控件、应用内 2.0x 缩放状态均在实际窗口验证。另以仅验收用的 820px 宽 Tauri `.app` 配置启动真实窗口，确认 compact drawer 覆盖主画布并保留“打开导航”恢复入口；随后删除该测试配置并重建默认 1440px 最终包。About 只显示版本、ad-hoc/未 notarized、macOS 11+、app-private 安全摘要和本地静态更新状态；不显示绝对路径或 SQLite 文件名。

## 诚实未闭合项

- 两个实际 app 进程可启动，但此 macOS 自动化会话的可访问性路由只能稳定操作一个窗口，未取得“第二窗口提交、第一窗口可见 `REVISION_CONFLICT`”的完整 GUI 证据。冲突机制由真实 Rust SQLite owner 的确定性 stale-revision 集成测试覆盖；它不等同于可视双窗口验收，留作独立 macOS 自动化债务。
- Escape 已有前端合同实现，但本自动化键盘注入未获得可归因的关闭结果，需与 compact 一并在下一次可控 macOS 窗口会话补证。

因此 P6-D 的本机功能、恢复、交换、expanded/compact、2.0x 和 macOS 开发包已收口到已列范围；“可视双窗口冲突/Escape”的真实 GUI 证据不是已通过项。

## 2026-08-13 双端侧栏追加证据

- 最新 Tauri `.app` 已重新构建并以 ad-hoc 签名 strict verify；可执行 SHA-256 `9db10f3977c52c45a60f8817c1a4beee80b3e5087ce2b6922f60e301c3a7f51e`。真实 `tauri://localhost` Accessibility tree 显示“新对话 → 已归档 → 回收站”、置顶段、会话段、行级“置顶会话 / 归档会话”、Composer 的“添加到草稿 / 选择模型·未配置 / 发送”。
- 对唯一 app-private 合成 workspace 的 `P6-D 隔离长列表验证会话` 实际右键，系统可见对话专属菜单为“置顶 / 重命名 / 添加到项目 / 删除”，无归档或工程路径项；置顶后完整退出并重新打开新进程，置顶段仍投影该会话且操作变为“取消置顶”。未在该追加链删除、归档或写入任何长期 workspace。
