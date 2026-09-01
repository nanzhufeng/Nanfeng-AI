# Android → Desktop 全方位同步交付 · 2026-09-01

## 本轮落地

- 主界面与侧栏：搜索 → 定时任务 → 南枫转写；会话日期固定使用创建时间；列表滚动位置和会话正文位置分别恢复。
- 模型：Composer 显示 Android 同口径短名称；弹层根级为自动／日常／深度，日常与深度进入完整候选列表；移除多出的可见 Compare 入口。
- Composer：保留 Desktop 独有的大输入框、宽屏信息密度和键鼠操作；加号菜单接入真实图片、文件和实时网页搜索入口，不伪造相机。
- 搜索：为图片、视频、音频、PDF、压缩包、文本及普通文件补齐预览图／类型图、真实大小、应用内预览或系统打开方式。
- 定时任务：增加独立宽屏任务页，覆盖通知状态、计划空态、暂停／重试／删除和新增入口。
- 南枫转写：默认图片／PDF → GLM-OCR Markdown，保留音频／视频 → Qwen3-ASR；Desktop 用任务列表＋详情双栏，不复制手机单栏尺寸。
- 文本：Assistant Markdown 安全渲染覆盖标题、列表、任务列表、代码、引用、表格、链接与搜索高亮；用户文本保持纯文本；费用统一折算成人民币显示。
- 设置：同步 Android 分组、图标、卡片语言和字段标题上置，同时保留 Desktop 双栏信息架构。

## 截图证据

- Android 当前基线：`/Users/nanzhufeng/.codex/visualizations/2026/09/01/01a05b3f-bf39-7580-97c0-0c60854681f3/android-current/`。
- Desktop 当前结果：`/Users/nanzhufeng/.codex/visualizations/2026/09/01/01a05b3f-bf39-7580-97c0-0c60854681f3/desktop-current/`。
- 合并逐屏对照：同目录的 `android-desktop-compare-1-20260901.png` 与 `android-desktop-compare-2-20260901.png`。

## 验证

- lint、typecheck、protocol golden、Node `135/135`、静态 build、Rust `163/163`、`cargo check` 全部通过，零失败、零跳过／ignored。
- 最新 macOS release bundle 已生成并通过严格 codesign 校验；仍是 ad-hoc 开发签名，不是 Developer ID／公证发行包。
- 使用唯一验收 Bundle ID 与独立 `/tmp` 数据根完成原生 Computer Use 逐页操作和截图；模型截图只使用无密钥、无网络的本地 catalog fixture。

## 边界

- 本轮未调用真实 Provider、真实账单、Google／Supabase 或系统通知真实点击；这些外部链路不能由 UI 截图替代。
- 未运行任何 `connected*AndroidTest`，未连接、安装或操作 OPPO。
