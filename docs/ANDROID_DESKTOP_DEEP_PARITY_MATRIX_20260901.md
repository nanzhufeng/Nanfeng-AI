# Android → Desktop 深层页面与流程差异矩阵（2026-09-01）

状态：**当前有效；代码／隔离本地深层对齐及正式 schema 37 本地数据／UI 验收通过；外部服务／系统权限仍分层待验**

## 2026-09-02 10 条功能链与当前 UI 层级补验（优先于下方历史行）

- 完整逐项审计见 [Desktop 10 条功能链当前完成度审计](DESKTOP_10_FUNCTION_CHAIN_COMPLETION_AUDIT_20260902.md)。当前本地 owner 已覆盖模型选择、Composer、大输入布局、提醒／监控、GLM-OCR 转写、标题／水位、全局附件搜索、ZIP 身份／墓碑、网页搜索设置和历史资料库调度。真实 Provider、账号、系统通知仍为外部验收。
- Composer 现按 Android 当前顺序显示“相机→图片→文件→基础风格和语气→实时网页搜索”。相机通过 `getUserMedia`、PNG 验证和普通／临时私有附件 owner；隔离原生打开后在拍摄前取消，附件三类表计数均为 0。下方“Desktop 不加相机”类历史措辞失效。
- Desktop 唯一产品设计为 `1440×900` 宽屏版，设置一级导航固定 `68px`。旧的 `宽 ≤ 1180 CSS px`／`高 ≤ 820 CSS px` 与 `48px` 紧凑密度已撤销；`1000×800`、`700×900` 只作内部防溢出／最小可用门禁。
- “模型设置”现对齐 Android `ModelSettingsPrimaryEntry`，用独立橙色 Key 强调卡进入真实 Provider／API Key／测试连接页；“费用与用量／上下文记录／运行诊断”位于后续“调用记录”次级组，不再四项均分。
- 当前纠正门禁为 Node `161/161`、Rust `181/181`、Android 设置／模型专项 `24/24`，lint、typecheck、inventory、protocol golden、static build 与 `cargo check` 通过。本增量未重新打 macOS bundle。公共截图只位于 `/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a060eb-322b-7dd2-b8da-1664f33e02de/nanfeng-ai-wide-only-20260902/`，全部为 `1440×900`。

## 2026-09-02 正式 schema 37 根补验（优先于下方“正式根未启动”历史措辞）

- 正式根已用显式 `--diagnostic-ui-schema-acceptance` 实际打开。主界面、抽屉、Composer、模型、六分类搜索、提醒、转写、四组设置及多个深层页均可渲染；诊断 PID 无子进程、无 TCP，普通聊天 Attempt、历史候选和提醒运行均为 0。
- 旧隔离 QA LaunchAgent 缺少精确数据根导致其旧二进制回落到正式 schema 37 根并崩溃；LaunchAgent／background cycle owner 已按红绿测试修复，Node `151/151`、Rust `180/180` 及完整构建门禁通过。
- 用户确认事前 backup 之后实际操作过 Desktop，并明确不需要恢复。新增 20 条 `archive` intent 全部是 revision 正确、内容不变的前台 lifecycle mutation，且按连续行从下到上处理，与正常会话整理一致；没有 startup／background 自动归档路径或成功的 Computer Use 归档证据，原阻断裁决撤销。
- 事前 schema 37 backup 为 `/tmp/nanfeng-ai-production-schema37-evidence-20260902.GXoNMe/workspace.sqlite3`，`quick_check=ok`，SHA-256 `7855a9dc9b099d4056d24e2c648001a23c825f759689665115de1e3e44a1324a`，只作审计证据，不用于恢复。完整脱敏差分见 [20 条会话归档审计](DESKTOP_FORMAL_ARCHIVE_INTENT_AUDIT_20260902.md)。
- 14 张独立截图与联系表位于 `/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a06014-85d8-7901-b462-a3ef1f15e8e7/desktop-formal-schema37-acceptance/`。正式根本地数据／页面证据已成立；Provider、账号、通知、OPPO 与 `connected*AndroidTest` 仍未触碰。

## 2026-09-02 24 张问题截图共享 owner 收口

- 逐图矩阵见 [Desktop 24 张问题截图 → Android 当前 owner 对齐矩阵](DESKTOP_24_SCREENSHOT_PARITY_MATRIX_20260902.md)。18 张首轮图与 6 张补图已归并为设置密度与 Assistant Markdown 两个共享修复面，避免 24 处局部补丁互相漂移。
- 设置保留 Android 当前四组 IA 与 Desktop 宽屏主从结构：一级栏 320–420px，主行 68px，详情上限 880px、间距 20px；五风格弹窗 600×620、5 张 94px 卡片。较小窗口仍渲染同一双栏 IA，不定义第二套页面。
- 外观三行右值已收进同一最小 108px 值组；主题色色点与“橙色”整体右对齐，三段文字的右边缘共线。对外只以 `1440×900` 作为产品视觉证据。
- Assistant Markdown 使用 880px 阅读宽度、15px／1.72 正文；表格单元格最小 140px，仅表格容器横向滚动。Composer 尺寸与会话功能布局未改。
- 新专项与全量门禁通过：Node `155/155`、lint、typecheck、protocol golden、static build、macOS bundle、strict codesign。11 张浏览器图、2 张隔离原生图及联系表位于 `/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a06048-b547-73d2-8863-9fbf7aea5ef5/desktop-deep-parity-after/`。
- 隔离原生使用唯一 Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.63406.mtjle8e1`、新鲜 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.mJpHnp` 与 diagnostic mode；`tauri://localhost` 设置／五风格弹窗实看，SQLite `quick_check=ok`、schema 37，进程无子进程、无已建立 TCP，验收后精确停止。正式 PID 58143 与正式 SQLite 未操作；未调用 Provider、账号或通知，未操作 OPPO，也未运行 `connected*AndroidTest`。

## 基线与裁决

- Android 事实源：`P5ARoute`、`SettingsDestination`、`NanfengAiApp`、`ConversationWorkspace`、`ScheduledMonitorViewModel/Dialog`、`GlmOcrWorkspace`，以及三个 Android 当前合同。
- Desktop 事实源：`desktop/src/app.mjs`、`chat-shell.mjs`、`android-settings-shell.mjs`、`desktop-search-page.mjs`、`desktop-reminders-page.mjs`、`desktop-transcription-page.mjs` 与对应 Rust command owner。
- 旧的一级截图、旧 `design-qa.md passed` 和“本地 owner 基本齐全”结论不再作为深层完成证据。
- `GLM_OCR_DOCUMENT_MARKDOWN_CONTRACT.md` 已按当前 live source 修正：Desktop 有独立 owner；真实 Provider 内容、Token 与账单仍不由本机 fixture 冒充。
- Desktop 保留大输入框、常驻侧栏、宽屏双栏、鼠标右键／悬停／键盘效率；只同步任务、状态、字段、恢复与错误语义。
- 2026-09-02 新增默认关闭的 `--diagnostic-ui-schema-acceptance` 进程级验收门：正式根可以在未来获授权后只执行 UI／schema／只读页面验收，不运行 background cycle、历史整理、提醒投递、周期同步、通知插件，不读取 Provider／账号 credential store；本轮仍只用新鲜 `/tmp` 根验证，未启动正式根。

## 2026-09-02 第二轮 17 项逐项审计（当前有效）

浏览器列只证明 webview 结构与可操作状态，不替代 SQLite／Tauri；原生列全部来自唯一 Bundle ID 与 `/tmp` 隔离根，不读取正式 Desktop 数据。

本轮补充使用 `/tmp/nanfeng-ai-desktop-local-qa-fixture.20260902` 的真实 Markdown、PNG、PDF、MP4、DOCX 和 v1/v2 交换包。多格式深验收隔离根为 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.IFhQM5`；`quick_check=ok`、schema 37，六个附件的 byte count 与 SHA-256 均和源夹具逐字节一致。截图目录为 `/Users/nanzhufeng/.codex/visualizations/2026/09/01/01a05db4-50d5-7131-ae92-29041ef03ac9/desktop-local-deep-qa-20260902/`；`contact-sheet-preview-not-single-app-screen.jpg` 是明确标注的预览对比板，并非 App 同一画面，20 张独立 PNG 仍原样保留。

图标弱证据已由 [Desktop 逐控件图标／卡片审计](DESKTOP_ICON_CONTROL_CARD_AUDIT_20260902.md) 补齐：主界面、模型、Composer `+`、搜索、提醒、转写、设置四组、个性化五风格、模型联网和 ZIP 导入均完成 Android live source → Desktop shared icon／surface／selection／typography／scroll owner 映射。设置的外观、导入与导出、开发与诊断，以及模型页实时网页搜索四个实际语义偏差已按红测修复，并以改后浏览器与隔离原生独立截图复验。

最后两个本地弱项使用全新的 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.Nds2ZC` 补验：真实 PNG/PDF 均经 macOS 原生 picker 进入 GLM-OCR owner；800 会话、10,188 消息、单会话最多 600 消息的确定性交换包经“导入为独立工作区”原生 picker 进入。对应 7 张独立截图与预览对比板位于 `/Users/nanzhufeng/.codex/visualizations/2026/09/01/01a05db4-50d5-7131-ae92-29041ef03ac9/desktop-local-final-gaps-20260902/`；`contact-sheet.jpg`（SHA-256 `e52cbea5106a248e8c29339ba7f4f59f65b4a1450367c86f03062d65648c5970`）同样只是预览对比板，并非 App 同一画面。

| # | 要求 | Android 权威源 | Desktop 当前 owner | 自动测试 | 当前浏览器证据 | 当前隔离原生证据 | 缺失证据／边界 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 图标 | `ICONOGRAPHY_CONTRACT.md`、`ConversationWorkspace.kt`、`NanfengAiApp.kt`、`GlmOcrWorkspace.kt` | `icon-source.mjs`、`chat-shell.mjs`、`android-settings-shell.mjs`、CSS icon tokens | 新增逐控件红测；专项 `3/3`、全量 Node `151/151`，成熟 Lucide／禁 emoji／四处精确语义映射通过 | 主屏、设置、模型、加号、五风格与改后模型联网截图可见 | 主屏、设置、模型联网、提醒、转写、搜索、预览均实看；改后设置／模型页用新诊断 bundle 独立回读 | 已完成逐控件语义／表面／整行选择／读屏 owner 映射；Desktop 平台原生尺寸不做无意义像素复制 |
| 2 | 卡片尺寸／设计 | `ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md`、`NanfengAiApp.kt` | `chat-shell.css`、`desktop-shell.css` | 卡片结构、五风格滚动与选中态断言通过 | 主屏／设置／模型／五风格联系表 | 四组设置、五风格 popup、空态联系表 | Desktop 宽屏尺寸按平台保留，不复制手机像素宽度 |
| 3 | 缺失信息 | `ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md`、回答信息合同 | context source／usage／diagnostics owners | context、usage、safe error 投影测试通过 | 本地 fixture 不伪造来源／费用 | 未配置模型显示真实“等待可用模型”；失败显示本地缺失附件 | 真实 Provider 来源、Token、账单待授权外部验收 |
| 4 | 文本格式 | 会话合同、Android Markdown renderer | safe Markdown renderer、纯文本 user renderer | 标题／列表／任务／代码／引用／表格／链接／高亮测试通过 | `1440×900` 产品渲染无横向溢出；700 仅内部门禁 | 2,799-byte 长 Markdown 的标题、列表、任务、引用、代码、表格、链接与结尾标记完整实看；原始 script 标签保持惰性文本 | 真实 Provider 流式增量仍待外部验收，本地完整内容已闭环 |
| 5 | 主界面 | `ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md`、`ConversationWorkspace.kt` | `renderChatFirstShell` | Chat/Work、Composer、导航、临时聊天状态测试通过 | `01-main.png` | 主会话、附件 tile、大 Composer 实看 | 本轮未对正式根执行新冷启动 |
| 6 | 模型选择 | `P2D_MODEL_SETTINGS_CONTRACT.md`、`ConversationWorkspace.kt` | `p6g_model_selection.rs`、model picker renderer | 根层、候选、revision、凭据缺失、重启测试通过 | `03-model-root.png` | 本轮只读“等待可用模型”；候选原生沿用前一隔离深层证据 | 真实 Provider 可用性与实际模型响应待外部验收 |
| 7 | Compare | 当前 Android source 无提交入口；历史记录只读 | `desktop_compare_*` 只读／停止兼容，webview 无 submit/retry ACL | ACL 拒绝、历史读取、运行中停止测试通过 | 当前模型根无 Compare 提交入口 | 本轮无新 Compare 执行 | 真实 Compare 请求明确不在当前 Android 对齐范围 |
| 8 | Composer 加号 | 会话合同与 `ConversationWorkspace.kt` | `chat-shell.mjs`、`app.mjs`、`p6g_model_selection.rs` | 图片／文件、会话五风格、会话联网、revision／重启测试通过 | 根菜单与五风格子页实看 | 原生 picker 实选 v1 工作区包并成功导入；v2 私有归档入口独立可达，v1 误投 v2 时严格拒绝 | 未发送真实 Provider 请求 |
| 9 | 布局 | Android 会话／设置合同 | shell CSS、splitter、settings two-pane owner | 唯一宽屏密度、fallback、splitter、scroll snapshot 测试通过 | `1440×900`：256px 侧栏、760px Composer；1000／700 只验证同一 IA 防溢出 | 原生宽屏、大字体设置与会话实看；字体只放大信息文本，不放大卡片／页面几何 | 小窗口结果不是产品变体或窄屏设计；多显示器仍待专项验收 |
| 10 | 提醒 | `ScheduledMonitorViewModel`、`ScheduledMonitorDialog` | `desktop_reminders.rs`、`desktop-reminders-page.mjs` | 创建／编辑／revision／暂停／恢复／删除／UNKNOWN 测试通过 | 浏览器可达，SQLite 明确不读的失败提示 | `08-native-reminder-empty.png`，计划数 0 | Team ID 通知授权、投递、热／冷点击待外部验收 |
| 11 | 转写 | `GLM_OCR_DOCUMENT_MARKDOWN_CONTRACT.md`、`GlmOcrWorkspace` | `desktop_transcription.rs`、transcription page | 迁移、导入、Attempt、缺凭据失败、隔离验收不读正常凭据、取消／重试测试通过 | 当前 GLM-OCR 根结构通过 | 真实 26,672-byte PNG 与 18,107-byte PDF 经原生 picker 创建；均投影 GLM-OCR、真实大小、Attempt／请求数。无凭据启动后持久化 `FAILED / ZHIPU_API_KEY_MISSING / Attempt 1 / 请求 0`，取消／重试入口可用，重启后两任务恢复；私有副本 SHA-256 与源文件一致 | 隔离模式在命令 owner 内锁死空凭据，测试证明不调用正常 credential loader；进程无已建立 TCP。未调用真实 GLM-OCR、未产生成本 |
| 12 | 会话标题／滚动 | 会话合同、drawer/scroll owner | conversation rows、read marker、scroll snapshot | 标题、精确返回、未读水位、scroll-to-latest 测试通过 | 主屏标题与位置导航实看 | 确定性包含 800 会话／10,188 消息／单会话 600 消息，SQLite 索引 10,988 行；标题与 0799→0787 抽屉行实看。草稿 `Large-history-draft-preserved` 经滚离、跳最新、精确搜索往返与 0250 中段查找关闭后均保留；最新 0599 与中段 0250 均实看 | 原生导入 2.345s、工作区打开 1.762s、600 消息会话打开 2.774s、跳最新 2.179s、精确查询 2.019s、结果打开 1.939s、返回搜索 1.519s；均完成且无崩溃／卡死。不是跨硬件性能基准 |
| 13 | 文件搜索／预览 | 会话搜索合同、统一 resolver | `desktop_search.rs`、`desktop-search-page.mjs`、safe preview | 六分类、类型、排序、hash／字节复核、精确返回测试通过；新增导入附件 owner、Markdown/CODE 索引与 Office extension-bearing 私有引用回归 | 搜索宽窄布局和控制可达性通过 | Markdown、PNG、PDF、MP4 均 App 内真实预览；DOCX 经 hash/ZIP 结构复核后由 TextEdit 读出精确正文；关闭 PDF 后查询与四附件结果精确恢复 | ZIP 浏览不在当前预览合同；系统打开只放行校验过的 DOCX |
| 14 | 五种风格 | `ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md`、`ConversationStyleDefinition` | `conversation-tone.mjs`、Composer tone page、p6g preferences | 可见恰好五项、无 visible default、发送／重试重读、重启测试通过 | `04-composer-tone-five.png` | 选择 高效务实 后重启仍回读；SQLite `efficient` | 真实模型语气效果不由本地 fixture 冒充 |
| 15 | 历史资料库 | `ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md` | history knowledge scheduler／candidate/checkpoint owners；UI/schema diagnostic startup mode | paused、checkpoint、去重、上下文最小投影；诊断重开保持 `last_dispatched`／schedule／checkpoint owner 不变，后续无参数恢复 Normal | 个性化页可见开关与说明 | 新鲜 `/tmp` 根诊断启动后 history `paused=1 / last_dispatched=NULL / updated=0`，无自动 curation；正式根未启动 | 诊断门现已可用于未来获授权的正式根只读验收；真实模型整理仍待外部授权 |
| 16 | 联网失败 | 会话合同的 fail-closed／retry 语义 | ordinary chat Attempt／retry／diagnostics owners；诊断 no-credential／no-network command guard | 无 Provider、请求失败、同 idempotency retry、偏好重读；显式诊断抑制、参数冲突、无持久传播、无 DB 启动写入测试通过 | 搜索页显示“Web 预览不会读取 Desktop SQLite”而非伪结果 | 诊断 PID 无子进程、无网络 socket；Provider／账号页面用 no-credential 投影，通知插件未初始化；隔离 DB 的 background/read-marker/reminder 状态未变 | 无附件 `PROVIDER_NOT_ENABLED` 持久卡已有前一隔离证据；本轮不做真实请求，正式根仍未启动 |
| 17 | ZIP 永久去重／墓碑 | `P6K_ZIP_ATTACHMENT_OCCURRENCE_CONTRACT.md` | p6k official identity ledger／asset link receipts | `p6k_official_identity_deduplicates_across_packages_and_user_delete_never_resurrects` 逐步覆盖首次导入、重复包、累计追加、同 ID 冲突、批次删除墓碑与重导不复活 | 无安全必要的浏览器可变入口；只保留导入 UI | v2 真实本地包经原生 picker 严格预检并形成 5 附件私有归档回执 | 破坏性删除未在原生 UI 点穿；墓碑以 tempfile SQLite 确定性集成链闭环，超大真实 ZIP 性能另验 |

当前补充证据目录：`/Users/nanzhufeng/.codex/visualizations/2026/09/01/01a05db4-50d5-7131-ae92-29041ef03ac9/desktop-local-deep-qa-20260902/`。最终对比板 SHA-256 为 `99833fd701a0b776e25b0cf1cd538a62a559f3e01437fea68cd2a62b5bfc4d4e`；修复前 fail-closed 截图也独立保留，但没有混入最终板。上一轮证据继续位于同一 visualization 根下的 `desktop-requirement-audit-20260902/`。

## 页面树与代码级差异

| 域／深层路径 | Android live owner | Desktop live owner | 当前代码裁决 | 必须补齐或验证 |
| --- | --- | --- | --- | --- |
| 主会话空态／长对话／Composer | `ConversationWorkspace` | `renderChatFirstShell` | 本机 fixture 与既有状态测试通过 | 真实 Provider 流式／计费仍属外部验收 |
| 抽屉滚动／标题点击／新对话 | `ConversationDrawerCanvas` | `conversationRows`＋`select-chat/new-chat` | 本机生命周期与返回验收通过 | 超长真实历史的像素级性能继续按专项性能门禁 |
| 会话滑出／菜单 | Android swipe owner | Desktop 右键／行操作 | 平台适配可保留 | 点操作外只关闭菜单；不得误触导航或动作 |
| 模型根层 | `自动／日常／深度` | `p6g-model-picker` | 已有；Compare 提交／重试 UI 与 ACL 已移除 | 根层返回、遮罩关闭、旧选择 fail closed；历史 Compare 只读兼容 |
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
| 南枫转写模式根层 | Android GLM-OCR 单工具 | Desktop 单一“全部转写”图片/PDF 工作台 | **P0 已按当前 Android 收敛** | 旧音视频任务只在兼容历史区可见，不提供新建／设置入口 |
| 文档选择／私有导入 | `GlmOcrWorkspaceViewModel` | `pickTranscriptionDocument`＋Rust | 原生 picker 打开／取消通过；门禁有自动测试 | 真实大 PDF 内容不在未授权情况下外发 |
| 文档任务详情 | source→Markdown、页数／Token／费用 | 独立文档投影＋持久任务详情 | **P0 已修复** | 原始文件、页数、Token、实际/估算费用、请求 ID、安全错误码均只投影真实事实 |
| 文档执行／取消／重试 | WorkManager task ID | Desktop transcription commands | 已有，未点穿 | QUEUED/PROCESSING/COMPLETED/FAILED/UNKNOWN/CANCELLED与重启 |
| 文档预览／完整 Markdown | 共享图片/PDF viewer＋完整 Markdown | shared preview＋safe Markdown | 部分已有 | PDF翻页、图片缩放、长文完整性、返回详情锚点 |
| 旧音视频选择／导入 | 当前 Android 无可见入口 | Desktop legacy Rust owner | 仅为已保存任务兼容，不再创建 | 不删除旧任务字节／状态；不得重新暴露为当前能力 |
| 旧音视频执行／取消／重试 | 当前 Android 无可见入口 | Desktop persistent legacy task | 历史任务可读；既有运行任务可停 | 不自动重发，不冒充 Android 同步能力 |
| 转写导出／继续对话 | 当前 GLM-OCR 结果；旧任务兼容 | `export/continue-chat` | 已有 | 真实结果格式回读；继续对话不自动发送；引用计数 |
| 设置一级 IA | 对话／外观／数据管理／工作区 4 组 | `android-settings-shell.home` | **P0 已同步并原生复验** | Desktop 保留宽屏双栏与键鼠焦点模型 |
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
| 工作区／项目／知识 | Android 设置首页 `WORKSPACE` 组 | Desktop workbench panes/dialogs＋设置入口 | 当前四组 IA 中保留 | 从 Work 模式验收新建／编辑／取消／保存／删除／恢复 |
| 开发与诊断 | Android 设置首页 `WORKSPACE` 组 | compatibility routes＋P8 inspect | 当前四组 IA 中保留 | 开发流程单独验收，不向普通用户伪造外部服务状态 |
| Markdown／附件／费用／错误态 | 会话共享 owner | chat shell owners | 部分已有 | 表格/代码/来源/reasoning、所有附件类型、费用未知、失败重试 |

## 本轮 P0 收口

1. Desktop 深层导航栈、每页滚动恢复和“设置列表 → 只读会话 → 原列表”已完成。
2. 归档／回收站已改用创建时间，并以归档 fixture 原生回读。
3. 记忆摘要已升级为真实全屏本机子页；询问与补充必须显式选择。
4. 已确认定时任务具备原子编辑 owner、revision 门禁、持久回读和 Tauri ACL。
5. GLM-OCR 详情字段、流式 Base64 请求、Attempt 与失败持久化已完成。
6. 最新 Release 已用唯一 Bundle ID 和严格前缀隔离根执行原生深层验收；详见 `ANDROID_DESKTOP_DEEP_NATIVE_ACCEPTANCE_20260901.md`。
7. 2026-09-02 第二轮校正了南枫转写、Compare、Qwen3-ASR 模型投影和设置四组 IA，并补齐 Composer 会话级五风格／联网偏好。
8. 真实本地夹具追加修复了工作区导入与 v2 私有归档入口混用、v1 导入附件未登记 preview owner、Markdown/CODE 未进全文索引、DOCX 系统打开缺少安全扩展名引用四个红灯。
9. UI/schema diagnostic startup gate 已把 migration／页面读取与 background cycle、历史整理、提醒、通知、凭据和网络分离；最终 Node `148/148`、Rust `179/179`、lint、typecheck、protocol golden、static build、`cargo check`、macOS bundle 与 strict codesign 全部通过。

## 统一验收脚本语义

每个适用流程都按以下顺序记录证据：

`入口 → 填写／选择 → 保存／执行 → SQLite／文件回读 → 返回恢复 → 完整退出与重启恢复`

本机自动化与原生验收覆盖空态、成功、取消、凭据／服务缺失、确认、暂停／恢复和返回恢复；失败／UNKNOWN／revision 冲突由 Rust／Node 确定性测试覆盖。新 release 尚未在正式数据根复启；真实 Provider、Google、Supabase 和有 Team ID 的系统通知继续单独分层，不能由隔离验收替代。
