# P6-I Claude export JSON Adapter 退出证据

日期：2026-08-15  
范围：唯一 Claude personal export `conversations.json` 本地 Adapter；不是 Provider、Key、HTTP、Agent、同步、OPPO 或发布完成声明。

## 自动门

- Android：以 Android Studio JBR 和 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` 运行 P6-I parser、Room 和 UI 定向 JVM 测试，`BUILD SUCCESSFUL`。
- Desktop：`cargo test --lib claude_ -- --nocapture` 为 2/2；覆盖严格 parser 的坏候选隔离、private copy、Alpha 确认、Skip、原子提交和 SQLite `user_version=14` 重开读回。
- 原始 macOS bundle 的 `codesign --verify --strict --deep` 通过。为不关闭已有用户窗口，真实 GUI 使用了隔离根中的临时副本：仅改临时 bundle-id 并 ad-hoc 重签以让 macOS 自动化能区分两个同 bundle-id 实例；源码、原 bundle、用户 app-data 均未改。该副本只用于功能验收，不能冒充原 bundle 的签名交付证据。

## Desktop 真实隔离链路

隔离 root：`/tmp/nanfeng-ai-p6i-acceptance-20260815`。空 root 缺少 Conversation owner 时，确认界面真实回显 `workspaceId` 缺失；因此先用原生 `Cmd+O` 导入仓库内非敏感 `protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange` 建立隔离 owner，全程未直写 SQLite。

1. Chat-first Settings → 数据导入 → Claude 对话，macOS 原生 Open panel 选择 `app/src/test/resources/p6i-visible-conversations.json`（SHA-256 `a151e4905d1238c75602c2d9b686b3ed80ac0c223e5c309961d6e88a0a8f3cc1`）。
2. UI 显示 `AWAITING_CONFIRMATION`，并明确外部路径、URI、Key 和句柄不会显示或保存；确认 `P6I Claude Alpha`，跳过 `P6I Skip`，终态为 `COMPLETED`。
3. 完整关闭并重开同一隔离 root。普通历史回读 Alpha、两条 role-preserving 文本及“从 Claude 导入 · 本地静态文本”；SQLite 读回 `user_version=14`、Alpha `CONFIRMED`、Skip `SKIPPED`。
4. Settings 原生 Save panel 导出 `p6i-desktop-roundtrip.nfai-exchange`。应用显示严格回读 semantic hash `da4a1d2eb93724341749484373f0175f4843067247d005db381f2fda98febf8f`；产物 SHA-256 为 `3f8ccf1cf21543524bca90ee1732ff7f241d5d2198d90e6ccaedc8242752a6cd`。外部 `unzip -t`/manifest/payload 复核只含 Alpha、`CLAUDE_EXPORT` 与两条 fixture 文本，Skip 未被导出。

## Android emulator-5554 真实链路补证

- 既有 P6-I 实机链的 DocumentsUI 选取、Alpha/Skip、force-stop/cold-start readback 和正式签名安装仍以合同/当前 handoff 为事实源；本轮只补 Settings 导出严格回读，未操作 OPPO。
- Settings → 对话与存储 → 导出当前对话生成 `nanfeng-ai.conversation-export` v1，`scope=current-path-only`。应用内 verifier 失败时会删除产物；产物保留即表示严格 readback 成功。
- 回拉产物 SHA-256：`80dc623e22084e69f781c2bd47db2a6a61271f02c89a282b9a7a676112f2e471`。ZIP `unzip -t` 通过；manifest 的 `conversation.json` entry SHA-256 为 `c6969312f509752b0ae43d40c4f6d4bf6ae9824cbee4428e4ffae41150453ef6`，payload 读回 Alpha 的 USER/ASSISTANT 文本。
- 完整 force-stop/cold-start 后从 drawer 打开 Alpha，UIAutomator 读回“从 Claude 导入 · 本地静态文本”、两条 fixture 文本与精确 placeholder `回复 南枫AI`。前台 activity 与 UI XML 均为 `com.nanzhufeng.ai`；设备 `base.apk` SHA-256 为 `4ce329b1b60d860e75f70f0018ff40e5ee6ec7e4a0f6d8279fce97eb934a3c1b`。
- 本轮试过 Settings 的完整 SAF 备份，但它因为现有 emulator 数据触发高敏保护而拒绝，未生成文件；该拒绝保留为正确的安全行为，未被误报为导出成功，也未影响上面的当前对话严格导出。

## 不在本阶段

未读取 Key、未构造 Prompt/RunSpec、未发送 HTTP、未执行模型/Agent/tool、未操作 OPPO、未接入知识库 Adapter、同步或发布。
