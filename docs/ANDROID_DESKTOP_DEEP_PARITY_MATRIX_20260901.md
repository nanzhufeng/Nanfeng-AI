# Android → Desktop 深层页面与流程差异矩阵（2026-09-01）

状态：**当前有效；本机深层对齐通过，外部真实服务／系统权限分层待验**

## 基线与裁决

- Android 事实源：`P5ARoute`、`SettingsDestination`、`NanfengAiApp`、`ConversationWorkspace`、`ScheduledMonitorViewModel/Dialog`、`GlmOcrWorkspace`，以及三个 Android 当前合同。
- Desktop 事实源：`desktop/src/app.mjs`、`chat-shell.mjs`、`android-settings-shell.mjs`、`desktop-search-page.mjs`、`desktop-reminders-page.mjs`、`desktop-transcription-page.mjs` 与对应 Rust command owner。
- 旧的一级截图、旧 `design-qa.md passed` 和“本地 owner 基本齐全”结论不再作为深层完成证据。
- `GLM_OCR_DOCUMENT_MARKDOWN_CONTRACT.md` 已按当前 live source 修正：Desktop 有独立 owner；真实 Provider 内容、Token 与账单仍不由本机 fixture 冒充。
- Desktop 保留大输入框、常驻侧栏、宽屏双栏、鼠标右键／悬停／键盘效率；只同步任务、状态、字段、恢复与错误语义。

## 页面树与代码级差异

| 域／深层路径 | Android live owner | Desktop live owner | 当前代码裁决 | 必须补齐或验证 |
| --- | --- | --- | --- | --- |
| 主会话空态／长对话／Composer | `ConversationWorkspace` | `renderChatFirstShell` | 本机 fixture 与既有状态测试通过 | 真实 Provider 流式／计费仍属外部验收 |
| 抽屉滚动／标题点击／新对话 | `ConversationDrawerCanvas` | `conversationRows`＋`select-chat/new-chat` | 本机生命周期与返回验收通过 | 超长真实历史的像素级性能继续按专项性能门禁 |
| 会话滑出／菜单 | Android swipe owner | Desktop 右键／行操作 | 平台适配可保留 | 点操作外只关闭菜单；不得误触导航或动作 |
| 模型根层 | `自动／日常／深度` | `p6g-model-picker` | 已有 | 根层返回、遮罩关闭、旧选择 fail closed |
| 日常候选 | 固定 6 项 | `p6g-model-router` | 已有 | 每项选择回读、凭据缺失、重启恢复 |
| 深度候选 | 固定 6 项 | `p6g-model-router` | 已有 | 每项选择回读、凭据缺失、重启恢复 |
| 加号图片 | Composer attachment owner | `pick-composer-image` | 已有 | 选择／取消、私有复制、预览、移除、发送与失败保留 |
| 加号文件 | 统一附件 resolver | `pick-composer-file` | 已有 | 全格式 resolver、完整性、预览、发送、引用计数 |
| 加号网页搜索 | 会话覆盖全局设置 | Composer add menu＋product settings | 已有 | 会话覆盖值、全局值、返回／重启回读、无凭据失败 |
| 全屏搜索六分类 | `ConversationSearchCategory` | `desktop-search-page.mjs` | 原生逐分类与数据态通过 | 真实超大文件库性能仍需数据集专项验收 |
| 搜索排序／文件类型 | 时间／大小／还原＋MD/PDF/ZIP/DOCX/TXT/JSON/其他 | `set-search-sort/file-type` | 已有 | 排序真实生效、切换回顶、返回保留精确锚点 |
| 搜索历史 | 持久历史 owner | `searchHistory*` | 已有 | 输入匹配、手动打开、清空、键盘焦点与重启恢复 |
| 搜索预览／系统打开 | 共享 App 内 resolver | Desktop preview＋系统打开 | 已有平台差异 | 图片/PDF/视频/音频/文本/Office/ZIP逐类打开与失败 |
| 搜索定位／返回 | 三元组精确定位 | `locate-search-attachment`＋scroll snapshot | 部分已有 | 返回必须恢复查询、分类、排序、类型、稳定 ID 与像素锚点 |
| 定时任务列表 | `ScheduledMonitorDialog` | `desktop-reminders-page` | 原生空态／草案／计划通过 | Team ID 通知投递与点击仍属外部验收 |
| 定时任务创建／审阅 | `ScheduledMonitorDraft` | manual draft＋review dialog | 原生填写、确认与列表回读通过 | UNKNOWN／revision 冲突由确定性测试覆盖 |
| 已确认计划编辑 | Android 当前无单独 edit owner；用户本轮明确要求 | `update_desktop_reminder_plan`＋编辑入口 | **P0 已修复并原生回读** | 原子更新、revision 冲突拒绝、保留运行历史；ACL 已纳入正式 bundle |
| 暂停／恢复／删除 | repository＋scheduler | Rust reminder owner | 已有 | 运行中取消、下次时间重算、删除确认、回读 |
| 计划失败／UNKNOWN 重试 | 显式重试 | `retry-reminder-plan` | 已有 | UNKNOWN 不自动重发；新 Attempt 与旧回执可追踪 |
| 系统通知点击 | Android system notification | macOS native bridge | 外部 blocked | Team ID 热／冷真点；本地 UI／mock 不冒充 |
| 南枫转写模式根层 | Android GLM-OCR 单工具＋语音转写参考工程 | Desktop 图片/PDF＋音视频 tabs | 原生双模式与状态文案通过 | SenseVoice 保持验证中，不替代默认 |
| 文档选择／私有导入 | `GlmOcrWorkspaceViewModel` | `pickTranscriptionDocument`＋Rust | 原生 picker 打开／取消通过；门禁有自动测试 | 真实大 PDF 内容不在未授权情况下外发 |
| 文档任务详情 | source→Markdown、页数／Token／费用 | 独立文档投影＋持久任务详情 | **P0 已修复** | 原始文件、页数、Token、实际/估算费用、请求 ID、安全错误码均只投影真实事实 |
| 文档执行／取消／重试 | WorkManager task ID | Desktop transcription commands | 已有，未点穿 | QUEUED/PROCESSING/COMPLETED/FAILED/UNKNOWN/CANCELLED与重启 |
| 文档预览／完整 Markdown | 共享图片/PDF viewer＋完整 Markdown | shared preview＋safe Markdown | 部分已有 | PDF翻页、图片缩放、长文完整性、返回详情锚点 |
| 音视频选择／导入 | Android transcriber owner | Desktop transcription Rust owner | 已有 | 格式、ffprobe/ffmpeg、无语音、缺 Key、长音频 |
| 音视频执行／取消／重试 | 持久队列 | Desktop persistent task | 已有 | 每个状态、检查点、重启恢复、未知结果不重发 |
| 转写导出／继续对话 | TXT/MD/SRT/DOCX＋草稿附件 | `export/continue-chat` | 已有 | 四格式真实回读；继续对话不自动发送；引用计数 |
| 设置一级 IA | 4 组 | `android-settings-shell.home` | 原生一级至三级导航通过 | Desktop 保留宽屏双栏与键鼠焦点模型 |
| 设置层级滚动恢复 | 每个 `SettingsDestination` 独立 scroll state | `settingsScrollPositions` | **P0 已修复** | 子页返回父页恢复独立滚动；父子锚点不串用 |
| 个性化草稿／保存 | 手动保存 | personalization draft | 已有 | 保存失败保留草稿；离开／返回／重启语义 |
| 自定义指令全屏编辑 | 独立四级页 | 全窗口编辑面 | **P1 已修复并原生验收** | 取消即时回滚原草稿；保存后返回；共享 6000 字计数 |
| 记忆摘要全屏页 | `MemorySummaryPage` | 本机 Memory 全屏页 | **P0 已修复并原生验收** | 摘要、更新时间、询问／补充显式选择、刷新、删除、关闭生成与确认 |
| 模型与联网／模型设置 | 固定 Provider 与预设 | model settings owner | 已有 | 每 Provider 保存、显隐、测试、失败、凭据缺失、回读 |
| 费用与用量 | 会话／标题／历史／OCR | Desktop 多 section ledger | 字段／分组仍有差异 | 人民币统一、次数＋费用、耗时、Token、来源与安全状态 |
| 上下文记录 | 仅实际使用来源 | context records | 已有 | 空记录不显示；回答级进入与设置级列表一致 |
| 运行诊断 | 连接失败＋自动工具 | diagnostics page | 部分已有 | 技术详情展开、耗时／时间／费用、长错误有界 |
| 对话管理列表 | 收藏／归档／回收站 | settings lifecycle pages | 部分已有 | 返回原列表与滚动位置；操作带外只关闭 |
| 归档／回收站时间字段 | **创建时间** | `createdAt` | **P0 已修复并原生验收** | 只读会话不暴露 Composer／变更动作；返回原列表 |
| 设置列表→真实会话→返回 | 显式 return destination | `settingsConversationReturn` | **P0 已修复并原生验收** | 返回设置原页、原筛选和滚动锚点 |
| 导入与导出 | JSON/ZIP/工作区/备份恢复 | 对应 picker＋Rust owner | 已有 | 取消、拒绝、失败、重试、回执、恢复后要求重启 |
| 本机数据／清理 | 统一 inventory＋确认 | privacy inventory＋cleanup dialog | 已有，未逐项点穿 | 数量/字节、预览、选择、确认、失败保留与重启回读 |
| 外观／字体／主题选择面 | 选择即持久化 | settings picker | 已有 | 深浅、窄宽、大字体、关闭恢复焦点、跨重启 |
| 工作区／项目／知识 | 列表→详情→编辑层级 | legacy workbench panes/dialogs | 部分平台适配 | 新建／编辑／取消／保存／删除／恢复与返回层级 |
| 开发与诊断 | Context＋离线评测 | connections＋P8 inspect | 已有 | 设置返回栈、空态、加载、执行、导出与失败 |
| Markdown／附件／费用／错误态 | 会话共享 owner | chat shell owners | 部分已有 | 表格/代码/来源/reasoning、所有附件类型、费用未知、失败重试 |

## 本轮 P0 收口

1. Desktop 深层导航栈、每页滚动恢复和“设置列表 → 只读会话 → 原列表”已完成。
2. 归档／回收站已改用创建时间，并以归档 fixture 原生回读。
3. 记忆摘要已升级为真实全屏本机子页；询问与补充必须显式选择。
4. 已确认定时任务具备原子编辑 owner、revision 门禁、持久回读和 Tauri ACL。
5. GLM-OCR 详情字段、流式 Base64 请求、Attempt 与失败持久化已完成。
6. 最新 Release 已用唯一 Bundle ID 和严格前缀隔离根执行原生深层验收；详见 `ANDROID_DESKTOP_DEEP_NATIVE_ACCEPTANCE_20260901.md`。

## 统一验收脚本语义

每个适用流程都按以下顺序记录证据：

`入口 → 填写／选择 → 保存／执行 → SQLite／文件回读 → 返回恢复 → 完整退出与重启恢复`

本机自动化与原生验收覆盖空态、成功、取消、凭据／服务缺失、确认、暂停／恢复和返回恢复；失败／UNKNOWN／revision 冲突由 Rust／Node 确定性测试覆盖。真实 Provider、Google、Supabase 和有 Team ID 的系统通知继续单独分层，不影响本机深层对齐裁决。
