# P6-D：macOS Tauri GUI 黑箱验收与 Desktop 交付收口合同

## 状态、目标与范围

P6-D 以已经存在的 P6-B/P6-C Rust-owned、app-private SQLite Desktop 为唯一事实源，完成 macOS 实际 Tauri `.app` 的黑箱验收、必要 Desktop 修复、最终 ad-hoc 开发包与本机交付说明。它不是 P6 或总项目终点，也不构成 Windows 交付、macOS Developer ID 签名或 notarization。

本阶段只可触及 Desktop 的窗口、前端呈现、Tauri typed command、现有 Rust SQLite owner、导入导出与非敏感本地交付资料。不得读取或写入 Key、Provider、Prompt、RunSpec、图片、真实费用、Android Room、Android 图标、OPPO、P7/P8、Hub、GitHub 或网络更新。

## 黑箱用户链与成功条件

必须由已构建的 macOS `.app`（不是 browser localhost 或源码断言）完成以下隔离、非敏感 fixture 链：

1. 通过系统 Open picker 选择 `.nfai-exchange` fixture，观察预检、进度、成功或可执行错误；导入后确认三栏投影来自 Rust SQLite。
2. 新建并编辑 Project、Knowledge、Memory，显式创建 relation；正文仅以不执行 text IR 显示。所有正常写入经 typed intent、expected revision 与 SQLite transaction。
3. 用第二个实际窗口或受控并发 action 触发 revision conflict；界面显示中文冲突与安全摘要，不覆盖当前对象。
4. 通过界面执行 undo、redo、软删除、回收站恢复；关闭/重新启动 `.app` 后确认 workspace、领域状态与可逆历史保留，且没有自动重放 intent。
5. 通过系统 Save picker 再导出 `.nfai-exchange`；Rust strict preflight、Node protocol preflight 与 Android `P6AExchangeContractsTest` 只读 strict preflight 均可接受。此 exchange 是 P6-D 的语义备份基线，不暴露或复制 SQLite 文件；同端 checkpoint 需未来独立安全格式/合同。

取消、损坏 fixture、非空 workspace、冲突、失焦或关闭 dialog 均保持真实状态、中文下一步与无部分写入；错误不得显示绝对路径、正文或 secret。

## 窗口、键盘与视觉合同

- Expanded 为左 Workspace/Project/Conversation 树、中间主画布、右 Inspector；Inspector 能独立折叠。Compact 阈值以窗口 CSS 宽度 `<= 900px` 为准：左栏切换为可恢复 drawer，Inspector 顺序收纳，主画布和关键操作不裁切。
- 在实际 `.app` 验证 expanded、compact 和应用 2.0x 缩放/系统可行字体；不得用浏览器模拟或仅源码 CSS 断言替代。
- Tab/Shift-Tab 能在可见控件间顺序移动，焦点始终可见；`Cmd+S` 保存、`Cmd+Z` undo、`Cmd+Shift+Z` redo、Escape 关闭非破坏性浮层。Dialog、Menu 与 Picker selection face 一律纯白 `#FFFFFFFF`，灰色只限外部 scrim。
- 验收覆盖错误、导入/导出进度、三栏/抽屉折叠与键盘操作。若黑箱发现阻断问题，在最小共享层修复后重新构建并复测完整链。

## 安全、恢复与更新边界

- capability 仅保留 `core`、dialog 与已合同化的 typed commands；不增加 shell、process、http、updater、global filesystem 或远程页面。CSP 继续禁止 egress。
- 应用内 About 至少展示版本、ad-hoc/未 notarized 状态、最低 macOS、仅安全摘要的本地数据位置，以及“更新状态：本地静态，未检查网络”。不得显示完整绝对路径、SQLite 文件名、Key 或诊断内容。
- 崩溃/关闭后重启恢复最近 workspace 与安全窗口布局；不丢已提交状态，也不自动重放未完成 intent。窗口恢复不替代 transaction/strict-preflight 验证。

## macOS 开发交付与 Windows 债务

- 最终 macOS 记录精确 app/executable 路径、大小、SHA-256、ad-hoc 签名状态、最低系统、未 notarized 说明、app-private 数据位置说明及卸载与保留数据边界。不得发布或描述为正式签名交付。
- Windows 必须在 Windows 本机另行验证 WebView2（在线/离线）、MSI/NSIS、签名、数据路径、系统 file picker、SQLite 锁/升级、缩放和 IME。本 macOS 不生成或伪装 Windows 包；可保留合同/CI 指引但不得宣称已验收。

## 自动门与退出判定

每次 Desktop 代码变更后运行 Rust `fmt/clippy/test/check`、前端 `typecheck/lint/test/build`、Tauri macOS app build、Node golden/preflight 与 Android 定向 P6A strict preflight；Desktop-only 变更不升 Android version。黑箱证据需记录实际 app、输入/输出、窗口状态、成功与失败恢复、重启、命令和最终包身份。

P6-D 退出只表示 macOS 本机 P6-D 与本地语义备份基线完成。P6 本地主体是否收口取决于上述实际证据；Windows 真实交付与 macOS Developer ID/notarization 始终是独立外部门。其后可按蓝图进入 P7 的本地协议/安全合同设计，但不在 P6-D 实现 P7。
