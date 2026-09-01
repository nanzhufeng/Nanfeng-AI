# 南枫 AI 当前交接

> **当前合同读取门（2026-08-27，优先于全文）：** 本文下方的**最新有效交接**与按时间累积的实现、设备与验收记录，只能说明当时事实，不能重新定义当前行为。Android 会话、抽屉、Composer、搜索、文本选择、主题和暗色皮肤只读取 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)；Android 设置首页及二级至四级页面只读取 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)；普通聊天的个性化、Memory、资料库与历史对话上下文只读取 [Android 当前运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)。下方任何“当前”“固定”“橙色”“Dialog”“功能审阅”“不会自动加入上下文”等历史措辞与这三份合同冲突时一律失效；数据／安全／Provider owner 仍按各自领域合同执行。

## 2026-09-01：当前全量代码 checkpoint、最终回归与正式设备读回

- **代码 checkpoint：** `main` 已在 `242ed1ec82002aabbad7f923f447f134ac84c867` 冻结 Android、Desktop、网关、Room schema 64／65、依赖校验、自动测试和全部核心新增 owner；提交前基线为 `3a9a70db1071`。构建目录、APK、macOS bundle、测试报告与 `output/playwright` 临时截图均未进入 Git。合同、交接、决策和开发档案使用随后独立的文档提交固化，便于分别回退。
- **Android 最终回归：** 强制完整 JVM `1082` tests、0 failures、0 errors、3 个既有 opt-in skipped；`lintDebug` 为 0 errors、97 warnings、19 hints；`lintVitalRelease` 与 `assembleRelease` 通过。最终正式 APK 仍为 `28,116,171` bytes，SHA-256 `90a019a1039f4e51ecb5d370d3740bb296121395fd4b066f1ecef2e91dd6859f`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，v2／v3 验签通过；重建结果与此前冻结包逐字节一致。
- **OPPO 正式覆盖：** 用户明确授权后，`OPPO PKH120` 上现装 `com.nanzhufeng.ai` 以同签名 `pm install -r --user 0` 单次保数据覆盖成功。覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变化；手机回读 `base.apk` 与最终本地 APK 哈希完全一致。冷启动 `NanfengAiActivity` 成功且保持 resumed，限定日志未见 FATAL、Room migration 或 SQLite 异常。没有卸载、清数据、注入数据库或运行任何 `connected*AndroidTest`。
- **Desktop 最终回归：** Node `141/141`、Rust `165/165`，0 failed、0 skipped；lint、typecheck、协议 golden、静态 build、macOS bundle 与严格 codesign 校验通过。当前 `.app` 版本 `0.6.0-p6d-dev`，主程序 `30,702,352` bytes，SHA-256 `d0934b1a6830044cc0f4bee03a5c98ef040665025a1f4997875497ca383ef9f2`；仍为 ad-hoc、`TeamIdentifier=not set`，不冒充 Developer ID／公证分发包。
- **网关与仓库门禁：** `go test -count=1 ./...` 通过；仓库 `git diff --check`、核心新增文件盘点、删除 owner 残留引用检查和高置信密钥扫描无阻断项。旧归档只保留历史引用，运行时代码与测试已明确拒绝被删除的 Desktop Compare 平行 owner。
- **未扩大声明：** 本次没有重新运行真实 ZIP opt-in、真实 Provider／Google／Supabase、远端网关部署或新一轮多页视觉验收；这些层级仍按各自已有证据或待授权状态报告。已完成且源码未变化的隔离模拟器与视觉流程没有重复执行。

## 2026-09-01：Android 对话风格与回答信息全盘审计收口

- **数据清理：**“删除全部本地业务数据”现在清除 App 自有的 26 个业务 SharedPreferences，包括个性化、当前会话风格／联网覆盖、账号会话、自动整理、预览位置和凭据密文；任一清理提交失败不再误报完成。真实 Room + SharedPreferences 行为测试已覆盖。
- **完成事实：**预写的回答模型归因现可在同一事务内补写生成时风格、实际联网、Token 和费用；所有已知字段保持不可改写，冲突会明确失败。“本次回答信息”投影已从 Compose 提取为唯一可测读取模型，覆盖耐久事实优先、旧记录回退、去重和未知语义。
- **兼容与安全：**未知的会话风格覆盖按“无覆盖”继承设置全局值；设置未知值仍回退中性“默认”。只有可解析、带主机名且无 user-info 的 HTTP(S) Provider 来源才能证明实际联网；畸形来源会被丢弃而不会让整条回答解码失败。图片 EXIF 改用 AndroidX 1.4.2，依赖校验仅新增该官方制品哈希。
- **当前验证：**强制完整 JVM `1082` tests、0 failures、0 errors、3 个既有 opt-in skipped；`lintDebug` 0 errors、97 warnings、19 hints（本轮 EXIF、边缘触摸与新风格存储告警已清）；`lintVitalRelease` 与 `assembleRelease` 通过。Release APK `28,116,171` bytes，SHA-256 `90a019a1039f4e51ecb5d370d3740bb296121395fd4b066f1ecef2e91dd6859f`，签名证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **隔离模拟器：**Release 只覆盖 `emulator-5588`，冷启动无 FATAL／Room 迁移异常；Composer 一级直接回读当前“默认”，二级仅显示六个标题，冷启动后当前会话覆盖保留；安装后回读 APK 与本地产物哈希一致。模拟器没有可用的新 Assistant 回复，因此未注入数据实拍回答弹层；该层由行为投影、Room 往返和迁移合同验证。未操作 OPPO，未运行任何 `connected*AndroidTest`，未使用真实 Provider 凭据。
- **仍保留的项目债务：**lint 仍报既有 KTX／依赖版本／Compose 样式提示，以及 PDFBox 转入的 BouncyCastle 1.72 内部 trust-manager 告警；它们不是本次风格／回答信息链的新回归，也未通过隐藏告警或盲升依赖冒充解决。

## 2026-09-01：Android 当前对话风格快捷切换与回答级执行证据

- **当前对话快捷切换：** Composer 加号浮层在相机／图片／文件之后新增“基础风格和语气”，一级行直接显示当前最终风格；二级只显示默认、直言不讳、专业可靠、亲和友善、高效务实、风趣搞笑六个标题和选中勾号。优先级固定为当前会话覆盖高于设置全局值；没有覆盖时直接继承设置里当前选定的风格。选择可在对话中反复切换，只影响后续请求，不改设置总开关、其他会话或旧回答。
- **真实请求链：** 普通发送和显式重试在共享 `requestOne` 边界重读全局设置与当前会话覆盖，生成当次唯一风格指令。成功回答的现有模型归因新增 `conversationStyleId` 与 `webSearchUsed`：只有官方联网路由返回可验证 HTTP(S) 来源并形成完整回答时才记为实际联网；当前开关状态不能冒充最终结果。
- **回答总入口：** 每条 Assistant 回复三点菜单的“本次上下文来源”统一改为“本次回答信息”。弹层依次显示生成时风格、实际联网结果和实际发送的本地上下文；旧回答缺证据时明确“未记录（旧回答）”，风格不在上下文来源区重复。
- **持久化：** Room schema 65 为现有 `assistant_response_model_attributions` 增加两个可空、内容无关字段，`64 → 65` 连续迁移保留旧归因并让旧字段保持未知；上下文 JSON 审计同时保存绑定时结果，作为旧读取路径的兼容补充，不再承担永久唯一事实。
- **当前验证：** 最终完整 JVM `1074` tests、0 failures、0 errors、3 个既有 opt-in skipped，`lintVitalRelease` 与 `assembleRelease` 通过。正式签名 APK 为 `28,066,715` bytes，SHA-256 `9a2fc62686c698b91caf1b431e43b3a3ba4b7d00cc9c27e2f782dfb76406bda4`。Release 仅覆盖安装到隔离模拟器 `emulator-5588`；视觉和 UI 语义回读确认一级直接显示当前最终风格、二级六项仅标题、会话覆盖冷启动后保留。模拟器当前没有历史 Assistant 回复，因此未伪造回答或调用真实 Provider 来实拍“本次回答信息”；该弹层由 UI 合同、Room 往返和 `64 → 65` 迁移测试验证。未操作 OPPO，未运行任何 `connected*AndroidTest`，未使用真实 Provider 凭据。

## 2026-09-01：Android ChatGPT／Claude ZIP 永久去重与删除墓碑

- **独立身份账本：** Room schema 64 新增仅保存 Provider、schema、HMAC 身份键、SHA-256／字节、本机绑定、批次、状态、删除原因和 revision 的永久账本；不保存正文、标题、原始文件名、路径或 URI。旧数据只从现存 exact provenance／occurrence 回填，已被旧版清除且无证据的历史明确记为 legacy coverage gap，不猜。
- **不复活：** 对话移入回收站、永久删除、批次删除及消息内附件引用删除，都在同一 Room 事务先写 `USER_DELETED` 墓碑。后续累积 ZIP 只追加未见官方消息；已删对话或单个附件 occurrence 永久跳过，共享资产字节不因单个引用删除而伤及其他 occurrence。
- **冲突与回执：** 同一官方消息 ID 变更内容、同一官方资产 ID 变更 hash／size 均 fail-closed。批次回执分开记录新增对话／消息／附件、复用字节、已存在、用户已删除、身份冲突与失败；只有零失败才写 `COMPLETED` 时间，进程中断转 `UNKNOWN` 且不自动重放。UI 显示“已跳过此前导入或主动删除的内容”，仅在设置“功能审阅”说明，未增加主页或 Composer 按钮。
- **验证与边界：** 新增的 Room 合同覆盖同 ZIP 两次、用户删除对话后重导、删除单 occurrence 后恢复、共享 file ID 多 occurrence、同 bytes 不同官方 ID、同 ID 不同 hash、跨 Provider 同 source ID 隔离、中断 UNKNOWN 和零失败 completion marker，该专项 XML `8/8, skipped=0`。完整 JVM `1071/1071`，0 failed、0 errors、3 个未提供显式实包路径的 opt-in skipped；当前环境三个 P6-K ZIP 变量均未设置，不冒充实包复验。`lintVitalRelease` 与 `assembleRelease` 通过；Release 覆盖到隔离 `emulator-5592` 后从 schema 63 无清数据启动正常，无 Room migration/FATAL。本增量只改 Android，不改 Desktop；未操作 OPPO，未运行任何 `connected*AndroidTest`。

## 2026-09-01：总控方案现行门提升——Android 六种对话风格真实生效

- **风格定义：** 可见候选固定为默认、直言不讳、专业可靠、亲和友善、高效务实、风趣搞笑六项；默认真正排第一，小字说明为“自然、清晰地回答，按问题复杂度调整详略；先解决当前问题，不刻意强化某一种表达风格”。直言不讳固定先说结论、直接指出问题、减少铺垫；高效务实仍排第五，把回答精简放在该风格的第一优先。空值或未知值回退到真实“默认”。
- **请求链：** 普通发送、附件 OCR／文本投影、官方网页检索、失败重试与旧会话继续全部在共享 `requestOne` 边界重读当前本机设置，再经唯一 `system` 段送往 OpenRouter、千问、DeepSeek 或智谱；不把风格固定在历史会话，不重复注入。
- **界面：** 六项均为统一浅灰圆角卡，显示标题和 2–3 行说明；选中项使用主题色浅底、边界与勾号。对话框允许更高并内部滚动，标题稳定，文字未缩小。
- **验证：** 定向 JVM 覆盖六风格差异、单次注入、4 个 Provider 序列化、重建后持久化、未知旧值回退、共享请求路径和弹窗视觉合同；最终完整 JVM `1071/1071`，0 failed、0 errors、3 skipped，`lintVitalRelease` 与 `assembleRelease` 通过。正式签名 APK 为 `28,050,334` bytes，SHA-256 `16817b0fd9370e8049d9fcef9dd3db4940875b33dc30ff6560b575910a81cc3e`。
- **隔离模拟器：** 与已装包验签同证书后，Release 仅覆盖 `emulator-5592`。语义树回读确认顺序为“默认 → 直言不讳 → 专业可靠 → 亲和友善 → 高效务实 → 风趣搞笑”，默认定义和高效务实“回答精简”均完整可读。选中“默认”并保存后，强停冷启动再次进入个性化页仍回读“默认”。最终截图位于 `~/.codex/visualizations/2026/09/01/01a05bb7-62d2-77c3-b050-f8a8c48b74ef/android-conversation-styles/six-style-picker-default-first.png`。
- **边界：** 只改 Android，不改 Desktop；未操作 OPPO，未运行任何 `connected*AndroidTest`。真实 Provider 回复语义仍需合法密钥下的独立调用，不用单测或构建冒充。

## 2026-09-01：Android 历史资料自动整理冷进程调度与可见终态修复完成

- **根因：** Manifest 删除了 AndroidX Startup Provider，又没有 `Application : Configuration.Provider`；App 冷进程被 `SystemJobService` 直接唤起时，WorkManager 因未初始化而要求重试，Worker 实际没有进入。同时周期工作每次容器重建都使用 `UPDATE`，且模型返回不适合后仍可继续扫描下一对话。
- **修复：** 新增惰性 WorkManager Application 配置，12 小时周期改为 `KEEP`，每窗口最多让一条对话进入 Provider 路由。Provider 调用前持久化预留；已知失败可重试，中断预留转 `UNKNOWN` 并禁止自动重发。每个到期窗口写入独立无正文运行记录，不混入 Provider 费用审计。
- **页面：** “费用与用量 → 历史资料整理”顶部显示“最近自动检查”。无候选明确“本轮未调用模型”；模型配置缺失、网络失败、成功与 `UNKNOWN` 均有独立安全投影。
- **验证：** 定向 JVM `19/19`，0 failed、0 errors、0 skipped；`lintVitalRelease` 与 `assembleRelease` 通过。新的空数据隔离模拟器 `emulator-5592` 真实 UI 开启资料库后，冷杀进程由 JobScheduler 唤起 Worker，得到 `Worker result SUCCESS`、`NO_CANDIDATE / NO_ELIGIBLE_CONVERSATION`，且 Provider 调用审计文件不存在。杀进程重启后页面仍能回读；开关关闭时任务全取消，重开后重建 15 分钟一次任务和 12 小时周期任务；再启 App 后周期 Job ID 与入队基点未变。最新截图为 `~/.codex/visualizations/2026/09/01/01a05bb7-62d2-77c3-b050-f8a8c48b74ef/android-history-curation/latest-auto-check-final.png`。
- **边界：** 本轮没有发起真实 Provider 请求，不冒充真实服务成功；未操作 OPPO，未运行任何 `connected*AndroidTest`。

## 2026-09-01：Android Composer 短模型名识别度优化完成

- **短名规则：** Claude 与 GPT 保留现有短名；DeepSeek V4 Flash／Pro 统一显示 `DS V4`，GLM-5.3／GLM-5.3 Flash 统一显示 `GLM 5.3`，Qwen3.7-Plus／Qwen3.8-Max／Qwen3.6 Flash 分别显示 `Qwen 3.7`／`Qwen 3.8`／`Qwen 3.6`，Gemini 3.7 Flash 显示 `Gemini 3.7`，Kimi K3 与 GLM-OCR 不变。只改共享短名投影；模型目录全名、路由、Provider、字体、胶囊尺寸和交互均未改变。
- **验证：** 短名／字体／页脚／会话可访问性定向 JVM `106/106`，0 failed、0 errors、0 skipped；`lintVitalRelease` 与 `assembleRelease` 通过。Release 覆盖安装到隔离模拟器 `emulator-5588` 后，UI 语义回读确认 `选择模型：DS V4` 与 `选择模型：GLM 5.3`，GLM Composer 截图位于 `~/.codex/visualizations/2026/09/01/01a05bb7-62d2-77c3-b050-f8a8c48b74ef/android-short-model-names/glm-5-3-composer.png`。
- **边界：** 未操作 OPPO，未运行任何 `connected*AndroidTest`；本增量是本地显示投影，不需要真实 Provider 调用。

## 2026-09-01：Android 普通聊天实时网页搜索与状态提示完成

- **真实语义：** 当前会话开关开启后，每次普通对话都选择实际模型所属服务商的官方网页搜索路由；带 OCR／Markdown 等附件时先投影为文本再搜索，不再因附件静默降级。Provider 没有返回可验证的 HTTP(S) 来源时，本次 Attempt 明确失败且不保存为完整回答，不能把模型自述冒充联网成功。
- **界面：** Composer 底部不增加“实时联网／未联网”常驻行。具体模型列表用当前会话状态替换没有判别力的“官方实时联网检索”：OpenRouter／千问／智谱保留服务商前缀，DeepSeek 保留“当前高峰／低谷”前缀。
- **验证：** 最新定向合同 `65/65`，0 failed、0 errors、0 skipped；`lintVitalRelease` 与 `assembleRelease` 通过。Release 覆盖安装到隔离模拟器 `emulator-5588`，UI 回读确认关闭态全为“未联网”、开启态全为“实时联网”，DeepSeek 高峰着色未回归。截图位于 `~/.codex/visualizations/2026/09/01/01a05bb7-62d2-77c3-b050-f8a8c48b74ef/android-web-search/`。
- **边界：** 本轮没有使用真实 Provider 凭据发起计费请求，因此“真实服务端已返回来源”仍待授权凭据下的单次实呼；没有操作 OPPO，也没有运行任何 `connected*AndroidTest`。

## 2026-09-01：Android → Desktop 本机深层对齐与最新 Release 已闭环

- **裁决：** 此节取代紧随其后的“深层验收 blocked”历史节。本机 Android live source → Desktop 深层页面、导航返回、表单、持久回读与主要失败／取消语义已完成；真实 Provider、Google、Supabase、系统通知授权／点按和 Developer ID 公证继续作为外部分层验收，不用本地 UI 或 fixture 冒充。
- **本轮修复：** 设置页独立滚动与“列表 → 只读会话 → 原列表”返回、归档／回收站创建时间、自定义指令全屏取消回滚、真实 Memory 全屏状态链、已确认提醒事务编辑及 ACL、GLM-OCR 详情字段／流式 Base64／Attempt 与失败持久化、v2 导入后的投影刷新。
- **原生证据：** 最终 QA Bundle ID `com.nanzhufeng.ai.desktop.deepqa.finalacl20260901`，独立数据根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.final-acl.CdkKBU`。Computer Use 最终完成提醒“创建 → 编辑 → 改名 → 保存 → 列表回读 `最终 ACL 隔离复验计划`”；此前发现的自定义指令取消残留和提醒 ACL 缺失均在重建后复验通过。
- **自动门禁：** Node `141/141`、Rust `165/165`、lint、typecheck、static build、macOS bundle、严格 codesign 全部通过。一次 localhost OAuth 单测瞬时失败后，单测和全量均重跑通过，未隐藏不稳定事实。
- **最新产物：** [南枫 AI Desktop.app](../desktop/src-tauri/target/release/bundle/macos/%E5%8D%97%E6%9E%AB%20AI%20Desktop.app)，版本 `0.6.0-p6d-dev`，主程序 `30,702,352` bytes，SHA-256 `d0934b1a6830044cc0f4bee03a5c98ef040665025a1f4997875497ca383ef9f2`。当前为 ad-hoc、`TeamIdentifier=not set`，不是 Developer ID／公证分发包。
- **完整证据：** [深层差异矩阵](ANDROID_DESKTOP_DEEP_PARITY_MATRIX_20260901.md) 与 [原生验收记录](ANDROID_DESKTOP_DEEP_NATIVE_ACCEPTANCE_20260901.md)。未操作 OPPO，未运行任何 `connected*AndroidTest`。


## 历史归档

本文件只保留当前任务与最近有效证据。完整历史已原样保存在 [历史交接归档](archive/CURRENT_HANDOFF_HISTORY_THROUGH_20260901.md)；仅在追溯旧决策或旧证据时按关键词读取，不作为当前状态全文加载。

归档 SHA-256：`6127808dde4af9d6ebad9cf9364995a363215ffc59b5a3314402619b18da1c39`
