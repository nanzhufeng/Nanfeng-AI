# 南枫 AI 当前交接

> **当前合同读取门（2026-08-27，优先于全文）：** 本文下方的**最新有效交接**与按时间累积的实现、设备与验收记录，只能说明当时事实，不能重新定义当前行为。Android 会话、抽屉、Composer、搜索、文本选择、主题和暗色皮肤只读取 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)；Android 设置首页及二级至四级页面只读取 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)；普通聊天的个性化、Memory、资料库与历史对话上下文只读取 [Android 当前运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)。下方任何“当前”“固定”“橙色”“Dialog”“功能审阅”“不会自动加入上下文”等历史措辞与这三份合同冲突时一律失效；数据／安全／Provider owner 仍按各自领域合同执行。

## 2026-08-31：OPPO 保数据覆盖（主题色调色盘 Release）

- **候选与构建：** `:app:assembleRelease`（含 `lintVitalRelease`）通过。当前正式候选 [南枫AI.apk](../app/build/outputs/apk/release/%E5%8D%97%E6%9E%ABAI.apk) 为 `28,017,559` bytes，SHA-256 `8ac61ede4db14ee7d49c0dd571ddefd7c89e382139daa34085d85d8e2bb014e4`，包名 `com.nanzhufeng.ai`、code `66`、`0.3.0-p10j`、非 Debug，v2/v3 证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。主题色提交 `f596b8b` 的隔离快照缺少当前工作树中配套的模型／会话源码而不能独立编译，因此本包忠实构建自当前工作树；未改写或提交既有未提交改动。
- **覆盖门禁与结果：** 用户授权目标唯一为 OPPO PKH120 / Android 16 `3B157F009E800000`。覆盖前现装包为 `28,017,564` bytes、SHA-256 `8279335eef23b7aff39fbf08ff367e2a7d3ec8b0325f04f5d0e3062aabe95593`，同包名、版本、非 Debug 和同一 v2/v3 正式证书。候选推送至 `/data/local/tmp` 后设备端 SHA-256 一致，只执行一次 `pm install -r --user 0`，返回 `Success`；覆盖后按新 `pm path` 回拉 `base.apk`，与候选逐字节一致。
- **数据与边界：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均不变；覆盖后 `lastUpdateTime=2026-08-31 20:00:45`，用户 0 仍为 `installed=true`、`stopped=false`。未卸载、未清数据、未读取私有业务内容、未启动 App、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`。设备在安装和回读完成后断开调试连接，故 `/data/local/tmp/nanfeng-ai-release-overlay-20260831.apk` 尚待其恢复连接后精确删除；本机校验副本与隔离工作树已清理。

## 2026-08-31：设置“主题色”语义与图标收口

- **可见语义：** 设置“外观”分组的原“强调色”统一更名为“主题色”，行首使用 Material 圆角调色盘图标；选择面标题同步更新。右侧仍显示当前颜色文字与小色点，行高、触控热区、选项、即时生效和深浅皮肤逻辑不变。
- **owner 与验证：** 内部 `AccentColor` 及其已持久化的用户偏好保持不动，继续驱动全局主题令牌、气泡、选中态、保存、发送、开关和文本选择色。`AppearanceFontSizeContractsTest`、`SettingsUiSimplificationContractsTest` 通过；未安装或操作 OPPO，尚无本轮真机视觉回读。

## 2026-08-31：最终回归与 checkpoint 证据

- **本次增量：** 当前 checkpoint `d6db5bf` 收口 Composer 草稿附件与已发送附件共用本地预览投影、退役 Grok 选择的历史归因与 `MODEL_NOT_FOUND` 失败关闭、以及全屏原图的“可缩小到完整图／按真实溢出自由查看”手势边界。旧会话不被静默换模型；预览不改变附件外发；图片没有固定尺寸或固定位置规则。
- **最终 JVM：** `:app:testDebugUnitTest` 为 `1042 tests / 0 failures / 3 skipped`。本轮修正的 5 项均为已演进源码与静态文本断言漂移：Provider continuation API、动态交接页首屏、调用记录字段、工具调用失败分支和 Registry 门禁；没有改写对应运行逻辑。
- **正式构建与边界：** `:app:assembleRelease`（含 `lintVitalRelease`）通过。现有正式 APK [南枫AI.apk](../app/build/outputs/apk/release/%E5%8D%97%E6%9E%ABAI.apk) 为 `28,017,564` bytes，SHA-256 `8279335eef23b7aff39fbf08ff367e2a7d3ec8b0325f04f5d0e3062aabe95593`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本 checkpoint 未运行 `connected*AndroidTest`，未安装或操作 OPPO，未使用用户 Key 请求真实 Provider；既有同签名保数据覆盖证据仍只证明安装与字节一致，不替代南烛枫的真机手势和真实回复回读。

## 2026-08-31：图片原图缩小与自由查看恢复

- **根因与恢复：** 草稿缩略图修复未改动全屏 `ImagePreviewDialog`；当前图片查看器却把单指拖动错误限制为“必须先放大”，使按宽度显示的纵向长图无法在原始比例上下查看。现恢复为：长图在原始比例就可上下拖动，双指可缩小到整张图片完整落入视口，双击仍以触点为中心放大 `2.5×`，所有放大后溢出方向都可连续单指拖动；位置只按真实图片边界收束，不再用固定比例或固定位置锁死。
- **验证与覆盖：** `P6F2BImagePreviewUiContractsTest` 与受影响的会话附件回归通过，`:app:assembleRelease`（含 `lintVitalRelease`）通过。正式包 [南枫AI.apk](../app/build/outputs/apk/release/%E5%8D%97%E6%9E%ABAI.apk) 为 `28,017,564` bytes，SHA-256 `8279335eef23b7aff39fbf08ff367e2a7d3ec8b0325f04f5d0e3062aabe95593`，v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。已在 OPPO `3B157F009E800000` 用 `pm install -r --user 0` 同签名覆盖成功，回拉 `base.apk` 与候选逐字节一致；首装时间及 `ceDataInode=1459104`／`deDataInode=1433378` 不变。未读取私有图片、未启动 App、未运行 `connected*AndroidTest`；真机手势体感仍需南烛枫以任意图片实际回读。

## 2026-08-31：Grok 4.6 High 从可选模型移除

- **选择与路由：** `Grok 4.6 High` 已从 Composer 深度列表、设置模型列表和 Auto 候选移除；和此前的 Grok 4.1 Fast／4.5 一样，不再形成普通聊天请求。保留其稳定逻辑 ID 仅为历史消息归因与旧选择识别；旧会话或旧全局默认会在读取 Key、准备附件和外发正文前以 `MODEL_NOT_FOUND` 失败关闭，绝不静默改为其他模型。
- **验证与覆盖：** 模型路由、Provider 门禁、OpenRouter 目录和内置档案的 5 组定向 JVM 合同通过；`:app:assembleRelease`（含 `lintVitalRelease`）通过。正式包 [南枫AI.apk](../app/build/outputs/apk/release/%E5%8D%97%E6%9E%ABAI.apk) 为 `28,017,558` bytes，SHA-256 `56e4e072b6ec87fdd58a804fcc6a8e7a017441121c01c7975b311dfcc56752d1`，v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。已在 OPPO `3B157F009E800000` 用 `pm install -r --user 0` 同签名覆盖成功，回拉 `base.apk` 与候选逐字节一致；首装时间与 `ceDataInode=1459104`／`deDataInode=1433378` 未变。未卸载、未清数据、未启动 App、未部署 Debug／仪器包、未运行 `connected*AndroidTest`，也未使用用户 Key 请求 Provider。

## 2026-08-31：Composer 草稿附件真实预览

- **修复：** 草稿附件此前只查 `attachmentPreviews` 缓存，首次选择的图片没有发起投影，因此会长期显示通用图片图标。Composer 现与已发送气泡共用 `ConversationAttachmentPreviewProjection`：在本地已验证资产上生成图片缩略图、PDF 首页、视频海报、音频语义播放器或安全文本／Office 摘要；草稿与对话卡复用同一尺寸、圆角和裁切，仅保留草稿专属的移除按钮。
- **边界与验证：** 本地预览不上传、外发或改写附件。`P6DConversationRowAccessibilityContractsTest`、`P6F2DVideoPreviewUiContractsTest`、`P6F2EAudioAndTextPreviewUiContractsTest` 合计 `101 tests / 0 failures`；`:app:assembleRelease`（含 `lintVitalRelease`）通过。正式候选为 [南枫AI.apk](../app/build/outputs/apk/release/%E5%8D%97%E6%9E%ABAI.apk)，`28,017,560` bytes，SHA-256 `15e673b9160ce5dff40d0795f9e9d503f9566416bb6950776661acc3afdf522c`，v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。已在 OPPO `3B157F009E800000` 同签名覆盖成功；覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 不变，未卸载、未清数据、未部署 Debug／仪器包、未运行 `connected*AndroidTest`。尚未以用户私有附件做真机视觉验收。

## 2026-08-31：对话生成期间的导航连续性

- **真实 owner 与状态投影：** 普通对话请求继续由 `NormalChatGenerationForegroundService` 按会话持有；新增活动会话集合，切换对话、对话／工作界面、设置或搜索只更换 UI 投影，不触发取消、重启或重复发送。切回正在生成的会话会恢复对应 `isSending`、Room 增量与停止入口；非当前会话完成只刷新列表／未读，不抢回页面。进入后台时若存在生成任务，回到应用不再因长后台间隔自动创建新对话。
- **多会话与可见反馈：** 服务注册表可同时独立跟踪多个会话，每个会话仍最多一个活动请求；侧栏标题前显示主题色进度环。通知保持无标题／正文内容，只显示单个状态或活动数量，批量动作明确为“停止全部生成”；Composer 的停止动作仍只取消当前会话。终态广播调整为注册表清理后只发送一次，避免刚完成的进度环被并发 reload 短暂复活。
- **验证与边界：** 新增合同及并发注册表测试为 `2 tests / 0 failures / 0 errors / 0 skipped`；扩展相关回归执行 `99 tests`，其中 `98` 通过，唯一失败是未触碰的 `NormalChatOpenRouterExecutor` 既有静态测试仍查找已更名的 `retainReasoning`，不属于本次导航改动。`:app:assembleRelease`（含 `lintVitalRelease`）通过；正式 APK [南枫AI.apk](../app/build/outputs/apk/release/%E5%8D%97%E6%9E%ABAI.apk) 为 `28,017,555` bytes，SHA-256 `38475cb3d099369948bd72071853a2a33d1179413b6c34d9b023362ad6b7a58a`，v2/v3 正式证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未连接或安装 OPPO、未运行 `connected*AndroidTest`、未用真实 Provider 发请求；真实多会话／后台／系统杀进程行为仍需后续显式设备验收。

## 2026-08-31：OPPO 保数据覆盖（K3／Grok 精确目录与网络诊断 Release）

- **2026-08-31 当前修复：** OpenRouter 实时目录已不含 `x-ai/grok-4.1-fast`，故该旧逻辑路由只保留为“已下线”的历史选择；发送与重试均在读取 Key、外发正文前以 `MODEL_NOT_FOUND` 停止，不能回退 Auto 或近似模型。`Grok 4.5` 与 `Grok 4.6 High` 都已从选择器、Auto 与设置模型列表移除；持久化的旧选择同样在读取 Key、外发正文前失败关闭，不能静默替换。历史消息仍显示其当时实际模型归因。传输失败诊断仍仅保留 DNS／TLS／连接／协议／I/O 类别，不保存异常原文、Key、正文或响应。
- **覆盖门禁与结果：** 用户授权的唯一目标为 OPPO PKH120 / Android 16 `3B157F009E800000`。候选与现装均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug，v2/v3 正式证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。候选 [南枫AI.apk](../app/build/outputs/apk/release/%E5%8D%97%E6%9E%ABAI.apk) 为 `28,017,560` bytes，SHA-256 `9a80bab03d953c1c2d07acac07bdf1d3074cb47a71df67a31a2681774cff76c3`；设备临时副本哈希一致，只执行一次 `pm install -r --user 0` 并返回 `Success`。覆盖后按新 `pm path` 拉回 `base.apk`，哈希和字节均与候选一致。
- **数据与验收边界：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均不变；覆盖后 `lastUpdateTime=2026-08-31 15:16:15`，用户 0 仍为 `installed=true`、`stopped=false`。设备临时 APK 和本机预检目录均已精确清理；未卸载、未清数据、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`。本轮未使用用户 Key 发送真实 K3／Grok 请求；覆盖闭环不替代真实账户／Provider 回复验收。

## 2026-08-31：OPPO 保数据覆盖（上下文记录与运行诊断列表 Release）

- **覆盖门禁：** 目标唯一为 OPPO PKH120 / Android 16 `3B157F009E800000`。现装与候选均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug，v2/v3 正式证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。覆盖前现装包为 `28,001,174` bytes，SHA-256 `da0653dcb0e5b4b584df059739faac2abea322cf7f9d7a33e6594497dc64af11`。
- **构建与覆盖：** 包含列表层级收敛改动的 `:app:assembleRelease`（含 lint vital）通过。候选 [南枫AI.apk](../app/build/outputs/apk/release/%E5%8D%97%E6%9E%ABAI.apk) 为 `28,017,558` bytes，SHA-256 `55624758863290b367a82cc828761c41d05e62403678e0548bb26a6f727e95ca`。设备临时副本哈希一致，只执行一次 `pm install -r --user 0` 并返回 `Success`；覆盖后重新读取变化后的 `pm path`，回拉 `base.apk` 与候选逐字节一致。
- **数据保留与边界：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均不变；覆盖后 `lastUpdateTime=2026-08-31 14:36:34`，用户 0 仍为 `installed=true`、`stopped=false`。设备临时 APK 与本机回读校验副本已精确清理。未卸载、未清数据、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`；本轮未启动 App，安装闭环不代替列表视觉与交互的用户真机体感确认。

## 2026-08-31：上下文记录与运行诊断列表层级收敛

- **上下文记录：** 只展示实际使用了资料的回答，不再把“未加入／未检索”的空记录混入列表。每张 18dp 白卡统一为“对话标题与资料数量 → Provider／模型 → 最多两条资料类型和标题 → 本轮输入 Token／右下时间”，卡片间固定 10dp 间距，长标题单行省略，保留真实数量与 Token 精度。
- **运行诊断：** 页面优先展示近 7 天“连接失败”，再展示“自动与工具任务”，两组都显示范围说明与记录数量。连接失败和调用记录共用“对象／模型与状态 → Provider → 关键结果 → 耗时／右下时间”的层级；Token 与费用拆行。Harness 版本、请求 ID、脱敏错误正文和安全错误码默认折叠进“技术详情”，不再与用户可执行信息争夺主视觉。
- **验证边界：** `ModelSettingsUiContractsTest`、`SettingsUiSimplificationContractsTest`、`P2MVisibleConfirmationContractsTest` 合计 `22 tests / 0 failures / 0 errors / 0 skipped`，Debug 主代码与测试代码完成编译，定向 `git diff --check` 通过。本节改动后续已由上方 14:36 的正式 Release 保数据覆盖到 OPPO；真机外屏、展开态、深色皮肤和大字体视觉仍待南烛枫实际体感确认。

## 2026-08-31：OPPO 保数据覆盖（Grok OpenRouter Release）

- **覆盖门禁：** 目标唯一为 OPPO PKH120 / Android 16 `3B157F009E800000`。现装与候选均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug，v2/v3 正式证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。安装前现装包 `28,001,051` bytes，SHA-256 `b672dee93eb7b5e4508c4608ddcf1271fda672242ab45d820106c3d00287d6d5`。
- **构建与覆盖：** 当前 Grok 接入代码的 `:app:assembleRelease`（含 lint vital）通过。候选 [南枫AI.apk](../app/build/outputs/apk/release/南枫AI.apk) 为 `28,001,174` bytes，SHA-256 `da0653dcb0e5b4b584df059739faac2abea322cf7f9d7a33e6594497dc64af11`。候选与设备临时包哈希一致，只执行一次 `pm install -r --user 0` 并返回 `Success`；覆盖后重新读取变化后的 `pm path`，拉回 `base.apk` 与候选逐字节一致。
- **数据保留与边界：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均不变；覆盖后 `lastUpdateTime=2026-08-31 14:10:03`，用户 0 仍为 `installed=true`、`stopped=false`。设备临时 APK 与本机校验副本已精确清理。未卸载、未清数据、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`；本轮未启动 App，也未使用用户 OpenRouter Key 发起真实 Grok 调用。

## 2026-08-31：OpenRouter Grok 4.1 Fast / Grok 4.6 High 完整接入

- **入口与排序：** OpenRouter 新增精确预设 `x-ai/grok-4.1-fast` 与 `x-ai/grok-4.6`。Composer“日常”把 `Grok 4.1 Fast` 固定放在 `GPT-5.6 Terra` 下方，说明为“实时 · X · 搜索”；“深度”把产品预设 `Grok 4.6 High` 固定放在 `GPT-5.6 Sol` 下方，说明为“旗舰 · 深度 · Agent”。两项同步进入设置 OpenRouter 模型目录、普通直发、Auto 的完整候选集合与实际模型归因，不新增独立 API Key 或可编辑端点。
- **请求与能力：** 4.1 Fast 默认明确发送 `reasoning.enabled=false`；4.6 High 默认发送 `reasoning.effort=high`。两项单次产品输出预算均封顶 `65,536` Token，不把 Provider 能力上限当作默认生成目标；文本、图片、PDF／文件、流式、工具、结构化输出继续由 OpenRouter Adapter 和已核验目录统一处理。需要当前信息时沿用 OpenRouter `openrouter:web_search` 插件；xAI 模型由 OpenRouter 自动补充 `x_search`，应用不伪造独立 X 工具协议。
- **目录与费用诚实边界：** 4.6 当前公开目录为 500K 上下文、最大输出能力 450K，并按输入少于 200K 的 `$2/$6/$0.5` 与达到 200K 后的 `$4/$12/$1`（输入／输出／缓存读取，每百万 Token）做版本化本地估算；OpenRouter 实际 `usage.cost` 始终优先，联网搜索费不从 Token 猜测。4.1 官方产品页确认 2M 上下文、图像／文件与可开关推理，但实时 `/models` 当前未返回固定费率，故本地不编造费用，目录未映射时按既有 fail-closed 规则显示不可用。
- **验证边界：** 模型排序与 Auto 完整性、OpenRouter 请求体、图片／实时搜索组合、目录精确映射、离线能力档案和费用分档的 5 组定向 JVM 合同通过，Debug 主代码与测试代码完成编译。未使用用户 OpenRouter Key 发起真实 Grok 调用，未构建 Release、未安装或操作 OPPO、未运行任何 `connected*AndroidTest`；真实账户可用性、Provider 账单和真机选择面仍需后续显式验收。

## 2026-08-31：OPPO 保数据覆盖（搜索与启动 I/O 优化 Release）

- **覆盖门禁：** 目标仅为 OPPO PKH120 / Android 16 `3B157F009E800000`。候选与现装均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug，v2/v3 正式证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`；覆盖前现装 APK 为 `27,984,644` bytes，SHA-256 `4f4344e7f13764e6f1e32e9eeba981fd30174995a42a9dccaf330bda8efdf464`。
- **构建与覆盖：** 当前工作树的定向 6 组 JVM 契约、Debug Kotlin、Release Kotlin、lint vital 与 `:app:assembleRelease` 通过。候选 `app/build/outputs/apk/release/南枫AI.apk` 为 `28,001,051` bytes，SHA-256 `b672dee93eb7b5e4508c4608ddcf1271fda672242ab45d820106c3d00287d6d5`；设备临时包哈希一致，只执行一次 `pm install -r --user 0`，返回 `Success`。安装后重新读取变化后的 `pm path` 并拉回 `base.apk`，与候选逐字节一致，签名仍为同一正式证书。
- **数据保留与边界：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变；覆盖后 `lastUpdateTime=2026-08-31 02:40:40`，用户 0 仍为 `installed=true`、`stopped=false`。设备 APK 临时文件与本机回读校验目录均已精确清理；未卸载、未清数据、未读取私有业务内容、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`。安装闭环不替代搜索与知识库流畅度的用户真机体感确认。

## 2026-08-31：历史失败、真实 ZIP 与网关测试收口

- **四项历史失败：** 知识库与 ZIP 导入确认面已改回共享 Dialog owner；统一遮罩、外部关闭和内向边缘手势重新由 `P5ADialogDismissBehavior` 接管。PDF renderer 合同改为验证当前“完整 SHA-256 校验 + 元数据缓存 + 活跃 descriptor”实现，设置根画布抽成独立路由策略。四个原失败类合计 `110 tests / 0 failures`。
- **真实 ZIP：** 用户明确选择的 2026-07 与 2026-08 ChatGPT ZIP 已运行三项 opt-in 验收，XML 为 `3 tests / 0 failures / 0 errors / 0 skipped`；未输出正文／附件名，未复制 ZIP，未接触设备。一次完整 JVM 为 `1024 tests / 3 failures`，三项均为已演进实现的静态锚点漂移，修正后对应三类定向回归通过；用户要求停止重复的大包全量扫描，因此没有把第二次中断的完整 JVM 写成全绿。
- **结构与网关：** 搜索历史/PDF 缓存策略、设置根画布策略、Room 搜索轻量投影已从巨型 owner 拆到独立文件。Go 网关补齐认证、幂等、续传、哈希完成、签名下载与过期拒绝测试，并修复多字段错误共用 JSON tag 的真实协议缺陷；`go vet ./...`、`go test -cover ./...` 通过，覆盖率 `54.5%`。
- **仍需决策：** 仓库仍没有 remote、upstream 或 tag；当前工作树混有既有未提交改动，且没有可确认的远端仓库名称／可见性，不能安全创建或标记发布。Compose、ViewModel 与数据库巨型文件只完成首批低风险拆分，未宣称债务清零。

## 2026-08-30：总控方案现行门提升（历史；已由 2026-08-31 总控门取代）

- 2026-08-30 当时的总控门曾以 `b7e1c2f` 与 `1f7f356` 为 checkpoint；现已由 2026-08-31 的 `d6db5bf` 总控门取代。该历史条目只保留当时的裁决过程，不得作为后续复查入口。
- [总控完成审计](MASTER_PLAN_COMPLETION_AUDIT_20260816.md) 顶部已重建当前完成范围、验证边界、独立待验门和后续复查顺序；旧 `c1c9ae0`、Schema 56、819 项 JVM、旧 APK／OPPO 状态继续保留在 2026-08-28 历史门，不得返向覆盖当前 Schema 63 与最终回归。
- 本次只修正文档权威层和冲突裁决，没有修改运行时代码、Schema、APK 或设备。后续先读 HEAD／工作树，再读总控门、本文顶部和受影响的一份现行合同；已完成范围只做防回退验证，不重复实现。

## 2026-08-30：当前代码 checkpoint、最终回归与增量档案固化

- **代码冻结：** 从 `2fec04c` 之后累积的 Android 主代码、Room Schema 61–63、模型／附件／转写／搜索／存储／对话交互、定向合同与项目文档共 `216` 个文件冻结为本地 checkpoint `b7e1c2f` (`checkpoint(android): freeze integrated product baseline`)。未夹带构建目录或强特征密钥，暂存前 `git diff --check` 通过。
- **当时的最终回归：** 首次全量 JVM 为 `1013 tests / 12 failures / 3 skipped`；其中 8 项是仍锁定旧行为的静态／Room 合同（完成 ZIP receipt、待看排序／圆点，菜单行数，统一搜索时间线，费用分类语义色），只更新合同后定向 `8/8` 通过。再次全量严格收敛为 `1013 tests / 4 failures / 3 skipped`，新增回归为 0；保留的 4 项仍是旧基线已记录的 PDF renderer cache、统一 Dialog 遮罩、Dialog 内向边缘手势和设置画布合同。本历史数字已由文首当前 checkpoint 的 `1042 / 0 / 3` 取代。
- **Release 与设备边界：** `:app:lintVitalRelease` 与 `:app:assembleRelease` 通过。重建后的 code 66 / `0.3.0-p10j` 非 Debug 候选为 `app/build/outputs/apk/release/南枫AI.apk`，`27,984,644` bytes，SHA-256 `0dba16156f36e76a23fe52748ed0416c7126db1e3d6b207b896e24605a7adfe0`，v2/v3 证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。该候选因嵌入新 checkpoint 信息而与已安装 APK 字节不同，本次未再获得覆盖授权；OPPO 当前仍是上一次已验证并保数据覆盖的 SHA-256 `4f4344e7f13764e6f1e32e9eeba981fd30174995a42a9dccaf330bda8efdf464`。
- **正式增量沉淀：** [完整开发档案](%E5%8D%97%E6%9E%ABAI%E5%AE%8C%E6%95%B4%E5%BC%80%E5%8F%91%E6%A1%A3%E6%A1%88.md) 只新增当前 checkpoint 摘要与读取边界，[可迁移开发经验](%E5%8F%AF%E8%BF%81%E7%A7%BB%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C.md) 只补充三条本轮新结论，[决策日志](decision-log.md) 只固化“活动 owner + 当前物理字节”的存储统计口径；已完成的模块说明不再复制。本轮不直接修改长期记忆生成文件。

## 2026-08-30：OPPO 保数据覆盖（待看左侧圆点 Release）

- **覆盖门禁：** 唯一在线设备为 OPPO PKH120 / Android 16 `3B157F009E800000`。候选与现装均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug，v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。覆盖前现装 APK 为 `27,984,638` bytes，SHA-256 `b19eef17db1efbc11bec2a14e08dbfae648daa294b8c4a2380cd89f5c794e72c`。
- **覆盖与回读：** 候选 `app/build/outputs/apk/release/南枫AI.apk` 为 `27,984,644` bytes，SHA-256 `4f4344e7f13764e6f1e32e9eeba981fd30174995a42a9dccaf330bda8efdf464`。推送后设备临时包哈希一致，只执行一次 `pm install -r --user 0`，返回 `Success`。安装后重新读取变化后的 `pm path` 并拉回 `base.apk`，与候选逐字节一致，仍为非 Debug、v2/v3 同证书。
- **数据保留与清理：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变；覆盖后 `lastUpdateTime=2026-08-30 23:14:14`，用户 0 仍为 `installed=true`、`stopped=false`。设备临时 APK 已精确删除，本机回读核验目录已移入废纸篓。未卸载、未清数据、未读取私有业务内容、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`；安装闭环不代替用户对左侧圆点与待看排序的真机交互确认。

## 2026-08-30：会话“待看”状态与标题左侧圆点

- **交互与排序：** 会话长按菜单新增“待看”，状态只保存会话 ID 与设置时间，不保存正文。普通列表在“已置顶”与“最近”两组内分别置顶，工作列表在所属项目内置顶，不跨组改变原归属。同一对话可反复设为待看，最新设置优先。
- **圆点与清除：** 待看与未读共用一个主题色圆点外观，但状态独立；圆点位于标题文字左侧，置顶行固定为“置顶图标 → 圆点 → 标题”，日期右侧不再放点。只有真正点击进入该行时清除待看；长按、滑出操作带、弹出菜单或点其他行不清除。
- **验证与产物：** 待看持久化、显式清除、普通／置顶／工作列表排序、标题左侧圆点顺序与搜索／存储回归合计 `25 tests / 0 failures / 0 errors / 0 skipped`；`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，`27,984,644` bytes，SHA-256 `4f4344e7f13764e6f1e32e9eeba981fd30174995a42a9dccaf330bda8efdf464`，v2/v3 签名通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未安装或操作 OPPO，未运行任何 `connected*AndroidTest`；真机列表视觉与触摸交互仍需后续显式验收。

## 2026-08-30：搜索正文补全与本机实际占用去重

- **搜索正文：** 空关键词“正文”不再只取每个对话的一条摘要；本地索引覆盖全部用户／Assistant 文本分支，ChatGPT JSON／ZIP 与 Claude 导入对话沿用正常时间线和来源标签。旧版只建了当前分支的局部索引时按对话就地重建；点击隐藏分支命中会先切换到确定的目标叶再定位消息。
- **本机数据根因与修复：** 旧概览按附件目录行分类，已永久删除会话遗留的 ZIP ownership receipt 会让已删媒体继续占在视频／图片中，文件当前长度、目录历史大小和来源概况也没有形成一个统一口径。现在分类只统计仍有真实 owner 的唯一文件，实际字节读取当前受管文件；无 owner 但仍在磁盘的历史残留单列并计入总量，用户可从既有清理范围明确删除。永久删除会话同时移除派生导入 receipt，并在最后引用消失时删除私有文件；捕获草稿、知识附件和南枫转写均纳入引用保护，不能被误清。原 ZIP 若仍保留，只按压缩包物理大小计入“其他导入资料”一次。
- **验证边界：** 搜索完整分支／旧索引修复与本机数据实际长度、残留隔离、显式残留清理、永久会话删除附件回收已纳入本次 `25 tests / 0 failures` 的 Room + App 私有文件定向回归，Release 构建与签名信息同上。未读取或修改 OPPO 私有业务数据，未覆盖安装，未运行任何 `connected*AndroidTest`；真机实际视频体积仅能在后续同签名覆盖后按新口径回读，不把预期的“1 GB 多”伪写为已验证事实。

## 2026-08-30：Qwen3.8-Max 联网时限、费用四宫格与暗色来源入口收口

- **Qwen3.8-Max 联网路线：** 纯文本实时信息请求不再进入曾出现 5 分钟只有工具进度、无终态正文的千问 Responses 链，固定改走官方 Chat Completions 联网，显式发送 `enable_search=true`、强制检索、`reasoning_effort=low` 与 `max_completion_tokens=16384`，整体生命期硬上限为 3 分钟。其他千问纯文本预设继续使用 Responses，附件仍沿用既有 Chat Completions 解析路线；失败不静默换模型或重复计费。
- **费用摘要与暗色链接：** “本机累计”继续独立显示；`会话、会话标题整理、历史资料整理、南枫转写` 四类摘要合并为一张 24dp 圆角 2×2 组合卡，用横／纵细分隔线和四个语义图标分区，原有模型、调用／Token 次数、实际金额、本地估算及金额精度全部保留。Assistant 来源链接胶囊与 ChatGPT 导入来源标记删除固定 `#EDEDED`，统一消费主题 `NeutralSystemSurface`、正文色和低对比描边，暗色皮肤不再出现亮白入口。
- **验证与边界：** 千问路由／Adapter、费用摘要、来源入口共 `51 tests / 0 failures`；`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。一次扩大到既有 `P6DConversationRowAccessibilityContractsTest` 的宽回归命中 2 项历史 Dialog／搜索时间线静态合同失败，不在本轮费用或来源胶囊区段；本轮新增定向合同均通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，`27,984,654` bytes，SHA-256 `f37911f200c256e8e18b98f3ddda5caae680f99e54e3fdcfb306437f639d2d00`，v2/v3 签名通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未使用用户 Key 发起真实千问请求，未安装或操作 OPPO，未运行任何 `connected*AndroidTest`；真实千问耗时、2×2 卡片窄屏排版和暗色来源入口仍需真机显式验收。

## 2026-08-30：OPPO 保数据覆盖（模型／费用／转写统一收口 Release）

- **覆盖门禁：** 目标唯一锁定 OPPO PKH120 / Android 16 `3B157F009E800000`，当时无其他 ADB 设备。候选与现装包均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug、v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`；覆盖前现装包为 `27,968,230` bytes、SHA-256 `60298f738ce0d061e51acd0e086538b96044fa4fd1e8e4fbce74c36d0cc7851b`。
- **覆盖与字节回读：** 候选 `app/build/outputs/apk/release/南枫AI.apk` 为 `27,984,638` bytes、SHA-256 `b19eef17db1efbc11bec2a14e08dbfae648daa294b8c4a2380cd89f5c794e72c`。只推送到 `/data/local/tmp/nanfeng-ai-code66-ui-unification-20260830.apk`，设备端哈希一致；只执行一次 `pm install -r --user 0`，返回 `Success`。安装后重新读取变化后的 `pm path` 并拉回 `base.apk`，与候选逐字节一致，仍为非 Debug、v2/v3 同证书。
- **数据保留与边界：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变化；覆盖后 `lastUpdateTime=2026-08-30 20:58:42`，用户 0 仍为 `installed=true`、`stopped=false`。设备临时 APK 已精确删除，本机前后校验目录已移入废纸篓。未卸载、未清数据、未读取私有业务内容、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`；覆盖与字节闭环不替代用户对最新费用摘要、模型顺序和 PDF 翻页性能的真机交互确认。

## 2026-08-30：模型选择、费用摘要、转写预览与搜索时间线统一收口

- **模型选择：** 根层“自动／日常／深度”和进入子页后的“日常／深度”标题统一使用 `Bold`。DeepSeek 行只将动态“当前高峰／当前低谷”片段保留主题色粗体，“官方实时联网检索”恢复普通辅助色。设置页 Provider 顺序固定为 `OpenRouter → DeepSeek → 智谱 → Qwen`，千问排在最后；智谱不再在模型选择框下方单列 GLM-OCR 卡片，展开同一模型选择浮层后，在聊天预设下方显示保留原说明的 GLM-OCR 信息项，该项不可选为聊天预设，也不进入 Composer。
- **转写预览与授权：** 南枫转写 PDF 点击后立即进入共享查看器加载壳，不再等待首页栅格化；补齐与对话／搜索相同的 4 页／24 MiB 有界页缓存、相邻页预取、旧请求代际失效和翻页优先。成功打开不遗留“正在阅读本地 PDF；不会外发”信息条，文本预览删除底部重复操作说明。选择文件仍只做本机私有复制，开始卡删除重复勾选，用户明确点击“开始转写”才外发。
- **搜索时间线：** 南枫转写的原文件和 Markdown 结果不再生成单独“南枫转写”分组；默认排序按各自真实时间与全部普通附件共同进入年月时间线，显式时间／大小排序继续对全部结果全局生效。点击、定位、预览、长按和 owner 边界保持原有统一逻辑。
- **费用摘要与账号按钮：** “本机累计、会话、会话标题整理、历史资料整理、南枫转写”改为五张同规格全宽前景摘要卡，统一标题、留白、左标签／右数值对齐；次数、Token 和金额不再挤成长句，全部金额值使用主题色粗体，统计事实无删减。Google 账号管理中的“切换 Google 账号／退出登录”改为相同灰色填充底、无描边，尺寸、图标和行为不变。
- **验证与产物：** 南枫转写 UI、共享 PDF／文本预览、DeepSeek 选择提示、搜索时间线、模型标题粗体、模型设置、费用摘要和账号页共 `44 tests / 0 failures`；`:app:compileReleaseKotlin`、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 / `0.3.0-p10j` Release 为 `app/build/outputs/apk/release/南枫AI.apk`，`27,984,638` bytes，SHA-256 `b19eef17db1efbc11bec2a14e08dbfae648daa294b8c4a2380cd89f5c794e72c`；v2/v3 签名通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未请求真实 Provider，未安装或操作 OPPO，未运行任何 `connected*AndroidTest`；真机页面与实际 PDF 翻页性能仍需后续显式验收。

## 2026-08-30：GLM-5.3 聊天模型完整接入

- **模型、设置与权限：** 新增独立 `ModelPresetId.GLM_5_3`，固定智谱官方直连 `glm-5.3` 与 `https://open.bigmodel.cn/api/paas/v4`；设置“智谱”下同时显示 `GLM-5.3`、`GLM-5.3 Flash` 和独立 GLM-OCR 说明，共用既有智谱加密 API Key，默认仍为 Flash。普通聊天只校验实际接收 Provider 的启用与凭据，不把设置页当前 Flash 预设误当作旗舰模型权限门。
- **路由与调用：** “深度”顺序更新为 `Claude Fable 5 → Claude Opus 5 → DeepSeek V4 Pro → GPT-5.6 Sol → GLM-5.3 → Qwen3.8-Max`。`GLM-5.3` 同步进入普通、复杂、附件三条 Auto 候选，并都位于 `Qwen3.8-Max` 上方；三条 Auto 的契约现在强制覆盖全部聊天预设，防止以后新增模型只出现在设置或手动列表。智谱 Adapter 对旗舰与 Flash 都显式发送 `thinking.type=enabled`、`reasoning_effort=max`，官方网页检索继续使用 Chat Completions `web_search`；思考／正文、Token、耗时、失败诊断和 Assistant 精确模型归因沿用现有统一链。
- **附件与调用记录：** `MD` 等 UTF-8 文件本机完整转文本；PDF 优先本机文本层，扫描 PDF／图片可走 GLM-OCR，图片／视频等可走 Qwen3.7-Plus Markdown 桥，再交给 `GLM-5.3` 作最终回答，桥接不改变最终模型。调用记录显示 `GLM-5.3`、实际智谱接收方和返回的 Token／耗时／状态。当前智谱中国区公开价目源未可靠提供旗舰直连费率，因此不套用 Flash 或国际渠道价格；未返回官方金额时如实显示未知。
- **验证与产物：** 模型服务、设置目录、Auto／手动顺序、Profile、智谱请求、联网、共享凭据、MD／PDF／图片／视频桥接、调用归因与费用诚实边界定向 `96 tests / 0 failures`。全量 JVM 为 `1001 tests / 4 failures / 3 skipped`，四项仍是既有 PDF renderer cache、统一 Dialog scrim／边缘手势与设置画布合同，本轮相关测试均通过。`:app:compileReleaseKotlin`、`:app:lintVitalRelease`、`:app:assembleRelease` 通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，`27,968,266` bytes，SHA-256 `3eb7295597e2dd831d42bf62f081df2402954808602411bd5c98746231949703`，v2/v3 证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未使用用户 Key 发起真实智谱请求，未安装或操作 OPPO，未运行任何 `connected*AndroidTest`；真实账户权限、真实账单与真机选择面仍需后续显式验收。

## 2026-08-30：OPPO 保数据覆盖（DeepSeek 实时峰谷提示 Release）

- **覆盖门禁：** 目标唯一锁定 OPPO PKH120 / Android 16 `3B157F009E800000`。候选与现装包均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug、v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`；覆盖前现装包为 `27,935,481` bytes、SHA-256 `4a19a3cff2073badf006910600c5097eacd8ade1068e542b49058b9b3d57635d`。
- **覆盖与字节回读：** 只推送冻结候选到 `/data/local/tmp/nanfeng-ai-deepseek-pricing-code66.apk`，设备端与本地 SHA-256 均为 `60298f738ce0d061e51acd0e086538b96044fa4fd1e8e4fbce74c36d0cc7851b`；只执行一次 `pm install -r --user 0`，返回 `Success`。安装后按新 `pm path` 拉回 `base.apk`，与候选同为 `27,968,230` bytes、同一 SHA-256，`cmp` 逐字节一致，仍为非 Debug、v2/v3 同证书。
- **数据保留与边界：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变化；覆盖后 `lastUpdateTime=2026-08-30 20:02:20`，用户 0 仍为 `installed=true`、`stopped=false`。设备临时 APK 和本机校验副本均已精确清理。未卸载、未清数据、未读取私有业务内容、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`；安装字节闭环不替代南烛枫在模型选择面回读主题色粗体“当前低谷／当前高峰”。

## 2026-08-30：DeepSeek 模型选择实时峰谷提示与费用口径统一

- **官方规则与唯一 owner：** 依据 DeepSeek 当前官方价目，UTC 每日 `01:00–04:00`、`06:00–10:00` 为高峰，即北京时间 `09:00–12:00`、`14:00–18:00`；其他时段为低谷。新增纯领域 `DeepSeekPricingWindow`，模型选择面与本机费用估算共同消费它，避免 UI 和账本各自判断。
- **选择面：** “日常”的 V4 Flash 与“深度”的 V4 Pro 小字改为主题色粗体 `当前低谷／当前高峰 · 官方实时联网检索`。打开着的选择面会在下一个精确边界自动刷新；计算只读当前 `Instant` 和 UTC，不受设备时区变化影响。没有新增主页、Composer 或设置常驻入口。
- **费用与历史：** 2026-08-17 00:00（北京时间）起，DeepSeek V4 Flash／Pro 的 token-only 本机估算按同一峰谷状态选取官方缓存输入、非缓存输入和输出价格；Provider 实际结算继续优先。生效前记录继续使用旧价目版本，避免用新价格改写历史。
- **验证与边界：** 峰谷边界、下一次刷新、费用版本、UI 主题色粗体与联网入口定向 `25 tests / 0 failures`，既有 `FB-P6-108` 模型选择面合同也单独通过；包含旧全类的宽回归为 `114 tests / 1 failure`，唯一失败是既有 `FB-P6-110` 统一 Dialog 内向边缘手势合同，本次模型选择相关项通过。`:app:lintVitalRelease` 与 `:app:assembleRelease` 通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，`27,968,230` bytes，SHA-256 `60298f738ce0d061e51acd0e086538b96044fa4fd1e8e4fbce74c36d0cc7851b`，v2/v3 签名通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未发起真实 DeepSeek 请求、未安装或操作 OPPO、未运行任何 `connected*AndroidTest`。

## 2026-08-30：全模型统一附件解析与对话错误隔离

- **已确认根因：** `MD` 不是不可读；旧 `DeepSeekChatAdapter` 和 `ZhipuChatAdapter` 对任何附件直接返回 `AttachmentUnsupported`，而已有的本机 UTF-8 文本内联只被 OpenRouter／千问使用。同时 UI 只保存一个全局 `sendError`，没有失败会话 ID，因此切换对话后仍会显示前一对话的错误。
- **统一解析链：** `MD/TXT/JSON/CSV/XML/YAML/HTML` 与 `DOCX/XLSX/PPTX` 在 Provider 之前完整转为本机文本，包含千问 Responses 实时搜索等文本专用协议；PDF 在当前协议支持时发送完整原件，其他路由先读文本层、扫描件调用 GLM-OCR；图片、视频等在目标模型或联网协议不原生支持时，由 Qwen3.7-Plus 生成忠实 Markdown 材料投影，图片在千问未配置时可用 GLM-OCR 兜底。原选模型不变，仍负责最终回答；不将所有 Provider 的原生能力伪改为多模态支持。
- **告知、账本与失败边界：** Composer 在有二进制材料时预先说明“本机先解析；必要时经千问 Qwen3.7-Plus／智谱 GLM-OCR”。桥接调用独立记录实际 Provider、模型、Token、费用与结果，不保存文件字节、桥接 Prompt 或中间 Markdown。桥接不可用时保留原附件并阻止最终请求，不发首页／封面／缩略图，不静默换最终模型。`sendErrorConversationId` 使 Composer 错误只在失败会话投影，切换对话立即隔离。
- **验证与产物：** Provider Adapter、Markdown 本机投影、Qwen 图片桥接、GLM-OCR PDF 桥接、联网协议强制投影、对话错误隔离、外发与上下文审计定向 JVM 通过。全量 JVM 为 `986 tests / 4 failures / 3 skipped`，4 项仍是既有 PDF renderer cache、统一 Dialog 遮罩／边缘手势与设置画布合同，本轮链路全部通过。`:app:lintVitalRelease` 与 `:app:assembleRelease` 通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，`27,951,857` bytes，SHA-256 `b162f90e4f3fe7be2080e32bb8eef38883c86fe4a3c17528a97d36bd9a5e18a0`，v2/v3 证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未调用真实 Provider，未安装或操作 OPPO，未运行任何 `connected*AndroidTest`。

## 2026-08-30：Qwen3.8-Max 长时推理与高费用根因修复

- **已确认根因：** 该次本机归因为输入 `83,273`、输出 `17,242`、耗时 7 分 39 秒，平均约 `37.6 Token/s`。当时的 Qwen3.8-Max 请求未发送 `reasoning_effort`，使服务端采用默认 `xhigh`；Responses 同时允许 `131,072` 组合输出 Token 和 10 分钟生命期。因此这不是单纯的网络等待，而是应用让高推理档位在过宽预算内持续生成。历史归因未存 `reasoning_tokens`，所以无法诚实还原这 `17,242` 中究竟多少是推理，不从总金额伪造精确拆分。
- **请求收口：** Qwen3.8-Max 的 Chat Completions 与 Responses 两条协议都显式锁定 `reasoning_effort=low`；组合输出上限收至 `16,384`，Responses 整体生命期由 10 分钟收至 5 分钟。其他千问预设不受影响，失败后仍不静默重投或换模型。
- **可审计费用：** Responses 和 Chat Completions 终态的 `reasoning_tokens` 已贯通 Provider 解码、归因 Room 和费用页；新记录显示“总输出（推理 · 正文）”，旧记录显示“推理明细未返回”。Room 版本由 `62→63`，可空列迁移保留旧输出和已记账金额。北京直连费用版本改为官方人民币输入 `¥12/M`、输出 `¥36/M`、缓存输入 `¥1.5/M`；旧记录的 `¥1.497056` 不改写，若完全无缓存按新官方价目复算则为 `¥1.619988`。
- **验证与产物：** 千问 Adapter、Responses SSE、推理 Token 传递、费用、模型能力与 `62→63` 迁移定向 JVM 全部通过。干净全量 JVM 为 `980 tests / 4 failures / 3 skipped`，四项是既有 PDF renderer cache、统一 Dialog scrim／边缘手势和设置画布静态合同，本轮新增链路均通过。`:app:lintVitalRelease` 与 `:app:assembleRelease` 通过；code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，`27,951,866` bytes，SHA-256 `4156d6518d7cabf2e7b507e541565c8964fb0dde1a8facea2b4cd9c3af6f0545`，v2/v3 证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未使用用户 Key 发起真实千问请求，未安装或操作 OPPO，未运行任何 `connected*AndroidTest`；真实 Provider 修复后的耗时和账单仍需后续显式验收。

## 2026-08-30：OPPO 保数据覆盖（“深度”模型顺序 Release）

- **覆盖门禁：** 明确锁定 OPPO PKH120 / Android 16 `3B157F009E800000`，同时在线的 `emulator-5554` 未参与。候选与现装包均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug、v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`；覆盖前现装 `base.apk` 为 `27,935,477` bytes，SHA-256 `0d4c5fe8fb51225d1304297baeb6cc2ba67cec52a8b87270bccf00c3deefc934`。
- **覆盖与回读：** 只将冻结候选推送到 `/data/local/tmp/nanfeng-ai-deep-order-20260830.apk`，设备端与本地 SHA-256 均为 `4a19a3cff2073badf006910600c5097eacd8ade1068e542b49058b9b3d57635d`；只执行一次 `pm install -r --user 0`，返回 `Success`。安装后重新读取变化后的 `pm path` 并拉回 `base.apk`，设备与候选均为 `27,935,481` bytes、同一 SHA-256，`cmp` 逐字节一致，仍为非 Debug、v2/v3 同证书。
- **数据不变与边界：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变；覆盖后 `lastUpdateTime=2026-08-30 18:23:13`，用户 0 仍为 `installed=true`、`stopped=false`。设备临时 APK 与本机校验副本已精确清理。未卸载、未清数据、未读取私有业务内容、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`；本次只证明正式包安全覆盖与数据目录指纹不变，不替代“深度”选择面真机视觉和真实 Provider 调用验收。

## 2026-08-30：“深度”手动模型顺序调整

- `ComposerModelRoutingCatalog.deep` 固定调整为 `Claude Fable 5 → Claude Opus 5 → DeepSeek V4 Pro → GPT-5.6 Sol → Qwen3.8-Max`，模型选择面按该单一目录顺序呈现。本次只调整“深度”手动列表，不改变 Auto 路由、日常列表、已有会话 override、Provider 或模型调用参数。`P6GModelRouterContractsTest` 与 `ProviderAdapterContractsTest` 定向通过，`:app:lintVitalRelease` 与 `:app:assembleRelease` 通过；全量 JVM 仍为 `977 tests / 4 failures / 3 skipped`，四项是既有 PDF renderer cache、统一 Dialog scrim／边缘手势和设置画布静态合同，本轮顺序与 Provider 项已通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，`27,935,481` bytes，SHA-256 `4a19a3cff2073badf006910600c5097eacd8ade1068e542b49058b9b3d57635d`，v2/v3 签名通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未安装或操作 OPPO，未运行任何 `connected*AndroidTest`。

## 2026-08-30：南枫转写应用内统一预览与费用分页

- **共享查看 owner：** 删除南枫转写专用的 `AndroidGlmOcrSourceOpener` 及 `ACTION_VIEW + FileProvider` 第三方跳转。转写详情从 `GlmOcrTaskOwner.sourceReference` 回读同一持久附件引用，经 `ConversationAttachmentPreviewProjection` 完整性校验后，直接复用对话／搜索的 `ImagePreviewDialog` 与 `PdfPreviewDialog`；图片双击、双指和拖动、PDF 缩放／翻页与页码恢复均保持同一实现。
- **相关入口审计：** 对话附件、已上传文件、全屏搜索附件与南枫转写原文件均由同一私有附件预览投影解析图片／PDF；剩余 `ACTION_VIEW` 只用于 App 自身主屏快捷方式和明确的外部网页 URL，不再承载本地文件查看。
- **费用与用量：** 页面直接读取既有 `InvocationRepository` 中 `glm-ocr:*` 调用，将转写人民币估算纳入本机累计，增加上方“南枫转写”汇总和第四个切换按钮；逐条显示 GLM-OCR、时间、耗时、输入／输出 Token、状态与可用金额，不复制第二份账本。
- **验证与产物：** GLM-OCR 领域、共享预览 UI、费用分页及受影响的图片／PDF／视频／音频／文本预览合同定向通过；`:app:lintVitalRelease` 与 `:app:assembleRelease` 通过。全量 JVM 为 `977 tests / 4 failures / 3 skipped`，四项仍是既有 PDF renderer cache、统一 Dialog scrim／边缘手势和设置画布静态合同，本轮新增及受影响项已通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，`27,935,480` bytes，SHA-256 `fa338d75b6b0f7c8e6c2e4016218544d7b4ad3acfd6662bda76fb984bba8c7dd`，v2/v3 签名通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未请求真实智谱、未安装或操作 OPPO、未运行任何 `connected*AndroidTest`。

## 2026-08-30：OPPO 保数据覆盖（ZIP 依赖清理修复 Release）

- **覆盖门禁：** 目标为 OPPO PKH120 / Android 16，序列号 `3B157F009E800000`；同时在线的 `emulator-5554` 未被使用。候选与现装包均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug、v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。覆盖前现装 `base.apk` 为 `27,919,096` bytes，SHA-256 `5d5249c5ee295a7ad04618caa875137d50e39505f12e9cd4d44263655c571eac`。
- **覆盖与回读：** 只将候选包推送到 `/data/local/tmp/nanfeng-ai-code66-zip-cleanup-20260830.apk`，设备端与本地 SHA-256 均为 `0d4c5fe8fb51225d1304297baeb6cc2ba67cec52a8b87270bccf00c3deefc934`；随后只执行一次 `pm install -r --user 0`，返回 `Success`。安装后重新读取实际 `pm path`并拉回 `base.apk`，与候选均为 `27,935,477` bytes、同一 SHA-256，`cmp` 逐字节一致，仍为非 Debug、v2/v3 同证书。
- **数据不变与边界：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变；覆盖后 `lastUpdateTime=2026-08-30 17:09:35`，用户 0 仍为 `installed=true`。设备与本机校验临时包已精确清理。未卸载、未清数据、未读取私有业务内容、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`。本次只验证安全覆盖和包／数据目录指纹；未替用户点击“整理并删除”，因此真实 10 个 ZIP 依赖的内置与原包删除仍由用户显式确认后执行。

## 2026-08-30：ZIP 原始包“仍有 10 个依赖”清理链修复

- **根因：** “尚未全部内置”的计数会包含已提交归属关系、但历史附件目录行缺失的候选；旧清理器却只遍历仍存在的目录行，这类依赖永远不会减少。另一个分叉是去重附件可能仍指向已不在的旧 ZIP，虽然当前保留 ZIP 内有同哈希、同大小字节，旧实现也不会尝试恢复。
- **真实修复：** `AndroidImportedZipPackageCleanup` 改为以稳定 `attachmentId` 去重建立全局恢复计划；目录行缺失时用已持久化的候选哈希／大小／MIME 和 ZIP 原字节重建同一 ID，旧来源不在时可从其他保留 ZIP 的完全一致条目恢复。每项仍先流式写入、校验字节数与 SHA-256、`fsync`，再事务切换附件目录；所有候选都重新读回为 `attachments/v1` 后才允许隔离并删除原包。缺少可校验来源、任一哈希／大小不符或全局仍有依赖时，全部原包保留。
- **验证边界：** 定向合同新增“缺失目录行恢复”、“旧 ZIP 不在时从当前 ZIP 恢复去重附件”、“任一附件无可验证来源就保留所有原包”，并保留原有成功与未完成任务门禁；ZIP 清理、任务删除与压缩包附件存储共 `9 tests / 0 failures / 0 errors / 0 skipped`。Release Kotlin、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。全量 JVM 为 `975 tests / 4 failures / 3 skipped`，失败仍是既有 PDF renderer cache、统一 Dialog scrim／边缘手势和设置画布静态合同，本轮 ZIP 清理定向项全部通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，`27,935,477` bytes，SHA-256 `0d4c5fe8fb51225d1304297baeb6cc2ba67cec52a8b87270bccf00c3deefc934`，v2/v3 签名通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未安装或操作 OPPO，未用用户真实 10 个附件执行不可逆清理，也未运行任何 `connected*AndroidTest`。

## 2026-08-30：南枫转写统一调用账本与全局搜索贯通

- **调用账本：** GLM-OCR 不再只把 Token／费用留在 `glm_ocr_tasks`。每次真实 Provider 尝试或外发前门禁均由 `GlmOcrTaskOwner` 写入既有 `InvocationRepository`，统一记录智谱／`glm-ocr`、状态、开始／完成时间、Attempt、输入／输出 Token、人民币费用和安全失败类型；设置调用详情再从 OCR 任务 owner 关联显示原文件名、页数、服务商请求 ID 与安全错误码。账本仍不保存文件正文、Prompt、原始请求／响应、Key、URI 或私有路径。
- **搜索与定位：** 既有全屏搜索新增 OCR 文档投影：原始 JPG／PNG／PDF 与生成 Markdown 进入“全部／图片／文件”及文件类型筛选；Markdown 正文通过已校验私有流逐行匹配，不复制第二份正文索引。结果继续复用现有白色附件卡与预览投影，点击后直接选择对应 OCR 任务并打开“南枫转写”详情，不伪造会话／消息锚点。
- **验证与产物：** GLM-OCR 领域／UI、会话搜索、统一调用账本与费用显示定向 JVM 通过；Debug／Release Kotlin、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。全量 JVM 为 `972 tests / 4 failures / 3 skipped`，失败仍位于既有 PDF renderer cache、统一 Dialog scrim／边缘手势和设置画布静态合同，不在本轮 OCR 调用账本或搜索链。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，`27,935,486` bytes，SHA-256 `d27c916d288293df1fc5a2bcbf1783851ec335a5d0ec76939df224e0cbfbb6b9`；v2/v3 签名通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未安装或操作 OPPO，未请求真实智谱，未运行任何 `connected*AndroidTest`；真实调用记录与设备搜索视觉仍需后续显式授权下验收。

## 2026-08-30：OPPO 保数据覆盖（标题／历史资料统一路由 Release）

- **覆盖门禁：** 明确目标为 OPPO PKH120 `3B157F009E800000`，全程以序列号隔离同时在线的模拟器。候选与现装包均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug、v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。覆盖前现装包为 `27,869,949` bytes，SHA-256 `462baf2ddc182b4acc60f497d4c2d1b687a8285d6cac33276424acecb7586df9`。
- **覆盖与回读：** 只将候选包推送到 `/data/local/tmp/nanfeng-ai-code66-title-history-route-20260830.apk`，设备端与本地 SHA-256 同为 `5d5249c5ee295a7ad04618caa875137d50e39505f12e9cd4d44263655c571eac`；随后只执行一次 `pm install -r --user 0`，返回 `Success`。安装后拉回的 `base.apk` 与本地候选均为 `27,919,096` bytes、同一 SHA-256，仍为非 Debug、v2/v3 同证书。
- **数据不变与边界：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变；覆盖后 `lastUpdateTime=2026-08-30 14:05:33`，用户 0 仍为 `installed=true`、`stopped=false`。设备临时 APK 与本机校验目录已精确清理。未卸载、未清数据、未读取私有业务数据、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`；本次只验证安全覆盖与数据指纹，未替用户发起真实 Provider 请求。

## 2026-08-30：会话标题与历史资料整理统一模型顺序

- **真正路由 owner：** 新增 `TitleAndHistoryRefinementRouting`，会话自动标题与手动／自动历史资料整理均只读取同一顺序：`DeepSeek V4 Flash → GLM-5.3 Flash → Qwen3.6 Flash`。前一项未启用、无凭据、模型／Adapter 不可用或请求／格式失败才进入后一项；OpenRouter、DeepSeek V4 Pro、Qwen3.7-Plus 不再参与这两个后台任务。普通聊天、`Auto`、提醒草案和定时监控不受影响。
- **历史资料链路纠正：** 原实现写死 Qwen Provider、Qwen Adapter、Qwen 审计和草稿归因，已改为逐候选解析 Provider、共享 Adapter 选择和实际 Provider／模型归因。GLM 命中时审计明确记录 `max`，DeepSeek／Qwen 记录 `low`。“整理当前对话”确认面也从旧“固定千问”改为显示真实三段顺序。
- **验证与产物：** 标题、历史资料、Provider Adapter、后台重试与设置／运行时相关定向 JVM 共 `46 tests / 0 failures / 0 errors / 0 skipped`；`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,919,096` bytes，SHA-256 `5d5249c5ee295a7ad04618caa875137d50e39505f12e9cd4d44263655c571eac`，v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未发起真实 Provider 请求、未安装或操作 OPPO、未运行任何 `connected*AndroidTest`。

## 2026-08-30：“本次上下文来源”弹窗文字精简

- 删除弹窗顶部“仅显示本次实际使用的本地来源”说明，并删除每一项下方的“已使用……”理由小字；弹窗只保留“本次上下文来源”主标题、单行来源标题与“知道了”。
- 个性化来源行从“个性化资料 · 昵称、职业／角色”精简为实际参与字段“昵称、职业／角色”；自定义指令行只显示“自定义指令”，不再追加“已保存的自定义指令”。审计数据、来源参与事实、页脚入口、隐私边界与关闭交互均不变。
- `AnswerContextDisclosureUiContractsTest` 与 `AnswerContextDisclosureContractsTest` 共 `5 tests / 0 failures / 0 errors / 0 skipped`；Debug Kotlin、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,902,726` bytes，SHA-256 `d4743ada1cfc251b6ab76f64da088a4f205a2d313ca1ac775eaf919df5faba3b`，v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未安装或操作 OPPO、未运行 `connected*AndroidTest`；真机视觉仍待后续覆盖后回读。

## 2026-08-30：Qwen3.8-Max 联网超时与工具轨迹泄漏根因修复

- **真正根因：** 投资／当期事实问题会正确路由到千问 `/responses` 内置 `web_search`，但普通聊天把这条长时工具调用强制为非流式，又继承 90 秒 socket 读取上限；因此截图会在精确 `1分30秒` 进入未知超时，即使 Provider 仍在搜索／推理也收不到最终正文。旧 Chat Completions 兼容路径还可能把 `<tool_use>` / `<tool_result>` 当作普通文本，这就是上一次“等很久后出现原始工具内容”的同一协议分层问题。
- **唯一路径修复：** Qwen Responses 联网现改为官方语义 SSE，只投影 `response.output_text.delta` 为可见正文，`response.reasoning_text.delta` 仍单独保留，搜索／工具状态一律不进正文。只有收到 `response.completed` 才允许进入完成终态；`failed` / `incomplete` / 无终态 EOF 都保留为失败，不把半截文本当成答案。Provider 结构化来源在终态原子并入最终可见回复；请求明确 `store=false`，不使用 Responses 默认服务端保存。
- **时限与恢复：** 这条 SSE 的单次无数据读取窗口为 180 秒，整体生命期硬上限为 10 分钟；工具状态与正文增量均可持续驱动真实进展，不再等整包结果。超时／断线后仍不自动重投；用户点“重试”时继续复用原 Attempt 与幂等键，不静默换模型。
- **验证与产物：** Provider Adapter、Responses SSE framing、自动联网路由、完成持久化与普通发送入口定向合同共 `56 tests / 0 failures / 0 errors / 0 skipped`；Debug Kotlin、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,902,730` bytes，SHA-256 `b3b1b978df913a358205732b489326008dd49e31d6cf49fecaa5545964751043`，v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未使用用户 Key 发起真实千问请求，未安装或操作 OPPO，未运行 `connected*AndroidTest`；真实 Provider 与真机闭环仍需另行授权后验收。

## 2026-08-30：GLM-OCR 独立文档转 Markdown 工作区

- **产品位置：** 左侧栏在“定时任务”正下方保留“南枫转写”入口，页内同名标题真正居中。主页为铺满画面的亮白内容面，直接列出全部转写，已删除重复的“Markdown 结果”大标题、说明小字和按内容高度生成的独立白卡；“选择图片或 PDF”是底部居中的独立悬浮按钮。GLM-OCR 在智谱模型设置中正常展示并复用同一加密 API Key，但以 `DOCUMENT_OCR` 用途从 Composer、Auto、普通聊天及定时任务候选中硬隔离。
- **真实 owner：** 新增 Room `glm_ocr_tasks` 与 `61→62` 迁移，来源／结果均引用现有私有附件目录并受引用计数保护。文件选择后只在本机安全复制；用户逐文件确认并点“开始转换”后，WorkManager 才调用固定智谱 `layout_parsing` 接口。JPG/PNG 限 10 MB、PDF 限 50 MB；请求以校验后的文件流分段 Base64 写出，结果只保存完整 Markdown、请求 ID、页数、Token、费用估算和安全状态，不持久化凭据、请求正文、原始响应或布局明细。超限／持久化失败清理本次无引用副本；未知超时／网络状态不自动重试，避免重复计费。
- **结果与原文件：** 每条列表可点击进入详情；详情固定呈现“原始文件”与“完整文字”。原始图片／PDF 通过私有存储完整性校验后，复用对话／搜索的 App 内图片与 PDF 预览，不再跳转系统查看器；Markdown 按块惰性显示全文，已删除 200,000 字符预览截断与预览弹窗。长按已完成结果仍可创建真实新会话并把对应 `.md` 作为未发送附件加入 Composer 草稿，不自动发送。
- **验证：** OCR UI／领域定向 JVM 全部通过，`:app:lintVitalRelease` 和 `:app:assembleRelease` 通过。全量 JVM 最近一次为 `969 tests / 3 failures / 3 skipped`，三项均是工作树既有的 PDF 渲染器缓存、统一弹窗遮罩与全局弹窗边缘手势契约失败。隔离 AVD `NanfengAiP5PerfAccessibility` 以同证书 `install -r` 保数据覆盖；实际读回铺满白色主界面、无重复标题的空态、底部悬浮选择、任务列表、待确认详情，并证实原始 JPG 真实打开到 Google Photos。返回层级又以两侧分别实滑验收：详情页从右边缘向内滑回到列表，列表页从左边缘向内滑回到对话上一级。未勾选发送确认，未发真实 Provider 请求，未操作 OPPO，未运行任何 `connected*AndroidTest`。
- **产物：** code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,919,111` bytes，SHA-256 `36163c17c72b93b8e48b92c80190b40359547be43e4a67e3f6bcdb26df2d75e4`；v2/v3 签名通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。权威行为读取 [GLM-OCR 南枫转写当前合同](GLM_OCR_DOCUMENT_MARKDOWN_CONTRACT.md)。

## 2026-08-30：OPPO 保数据覆盖（模型字重与 Auto 路由 Release）

- 唯一连接设备为 OPPO PKH120 `3B157F009E800000`。候选与现装包均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug，v2/v3 正式证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`；覆盖前设备 `base.apk` 为 `27,869,942` bytes，SHA-256 `a33db2341a378a799476150b0a9576d97e628be6d295b22abecae6a86d54e43f`。
- 只推送候选 APK 到 `/data/local/tmp/nanfeng-ai-code66-bold-model.apk`，设备端 SHA-256 与本地候选同为 `462baf2ddc182b4acc60f497d4c2d1b687a8285d6cac33276424acecb7586df9`；随后只执行一次 `pm install -r --user 0`，结果为 `Success`。安装后拉回的设备 `base.apk` 与本地 `app/build/outputs/apk/release/南枫AI.apk` 均为 `27,869,949` bytes、同一 SHA-256，仍为非 Debug、v2/v3 同证书。
- 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变；安装后 `lastUpdateTime=2026-08-30 12:34:07`，用户 0 仍为 `installed=true`、`stopped=false`。设备临时 APK 与本机校验目录已精确清理。未卸载、未清数据、未读取私有业务数据、未部署 Debug／仪器包、未运行任何 `connected*AndroidTest`；模型字重的真机实际观感仍需南烛枫打开 App 回读。

## 2026-08-30：Composer 模型短名与选择面主标题统一加粗

- 主界面底部 Composer 右侧的短模型名由 `Normal` 调整为 `Bold`，普通会话与临时聊天继续共用 `ComposerModelEntry`；既有 `13sp`、圆润字体、`88dp × 48dp` 入口、内部 `36dp` 胶囊、按压与模型选择逻辑均不改变。
- 模型选择根层的“自动／日常／深度”三个主标题与具体模型标题统一为 `Bold`；“Auto · …”“日常问答与轻量任务”“复杂推理与专业分析”等说明文字仍保持辅助层级。删除只为区别具体模型而存在的 `labelIsModelName` 分支，避免相同层级再次分叉。
- `ModelNameTypographyContractsTest` 与两组模型选择面定向契约共 `5 tests / 0 failures / 0 errors / 0 skipped`；`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,869,949` bytes，SHA-256 `462baf2ddc182b4acc60f497d4c2d1b687a8285d6cac33276424acecb7586df9`，v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未安装或操作 OPPO、未运行任何 `connected*AndroidTest`；真机实际字重仍待获得覆盖授权后回读。

## 2026-08-30：Auto 路由按新模型重排并显式化

- `Auto` 现由中央模型目录维护普通文本、复杂推理、附件三份穷尽式顺序，不再从模型展示顺序隐式补齐。普通文本首选 `DeepSeek V4 Flash`，复杂推理首选 `GPT-5.6 Sol`，附件首选 `Qwen3.7-Plus`；按用户最后确认，`Gemini 3.7 Flash` 固定放在附件路线最后。完整顺序已写入 Android 当前会话界面合同。
- 复杂升档使用共用纯函数：输入达到 2400 字符，或明确出现深度分析、完整方案、架构／重构／审计、根因／权衡、复杂调试、多步骤推理、投资／估值／风险传导等信号才升档；普通“分析一下”不升档。旧的资料库总条目数触发与已移除设置对应的隐藏 `qualityEscalationEnabled` 门均不再参与自动选型。
- 实际发送前只从已启用、有凭据、精确可解析、支持文本且非 `UNAVAILABLE` 的候选中选择；附件继续匹配实际能力，同能力池里 `DEGRADED` 排到健康／未知候选之后。发送后 Provider 失败不会静默改投另一模型，避免重复计费和重复回答；手动选择具体模型仍始终优先。
- 本轮模型路由及显式外发相关定向 JVM 共 `59 tests / 0 failures / 0 errors / 0 skipped`；按最终 Gemini 顺序再次执行核心路由 `16 tests / 0 failures`。`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,869,951` bytes，SHA-256 `797fbd14ec4c839717c8da5e1dbbb6b95f1744e7c67aa56c52fc025396f7191f`，v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未请求真实 Provider、未安装或操作 OPPO、未运行任何 `connected*AndroidTest`。

## 2026-08-30：删除“对比”与“图像 / 视频 / PDF”模型选择分组

- 模型选择面公开目录只保留 `自动选择 → 日常 / 深度`。`ComposerModelRoutingCatalog.groups/choices` 不再包含 Compare 与 Multimodal 候选，根层高度随四个任务分组缩减为两个任务分组，不留下空白托盘。
- Compare／Multimodal 枚举仅作为历史持久值和底层能力兼容保留，不再是用户可选项；旧 `logical:compare:*`／`logical:media:*` ID 统一由目录 owner 回退到 `auto`，UI 的选中态也读取规范化后的 choice。底层 Auto 附件能力路由、Provider 能力与既有历史数据不删除。
- 模型目录、分层选择面、Compare 公开入口移除与旧 ID 回退共 `21 tests / 0 failures / 0 errors / 0 skipped`；相关四类整类初跑共 109 项时只有既有 `FB-P6-110` 全局 Dialog 外点／边缘滑动合同失败，本次精确测试已排除该无关失败。`:app:assembleRelease` 与 `lintVitalRelease` 通过。code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,869,936` bytes，SHA-256 `2b722145bf04b3ae0021342887a115d3d1d71402e092d885c30dcf03128c583e`，v2/v3 证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未安装或操作 OPPO，未运行任何 `connected*AndroidTest`。

## 2026-08-30：“日常”模型手动选择顺序调整

- “日常”选择面按 `Claude Sonnet 5 → DeepSeek V4 Flash → GPT-5.6 Terra → GLM-5.3 Flash → Qwen3.7-Plus → Gemini 3.7 Flash` 展示。唯一顺序 owner 仍为 `ComposerModelRoutingCatalog.daily`，`P6GCuratedModelCatalog` 继续从该目录投影，不新增第二套 UI 排序。
- 本次只移动既有 `ComposerModelChoice`，稳定 ID、Provider 路由、模型能力、`Auto`、自动标题、当前会话 override、短名与其他模型分组均不改变。
- `P6GModelRouterContractsTest` 定向 `10 tests / 0 failures / 0 errors / 0 skipped`；`:app:assembleRelease` 与 `lintVitalRelease` 通过。code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,869,952` bytes，SHA-256 `62d515e7eeb5322727801c771a60ad31c6998e5c959dc61c9ff0e85637b8c838`，v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未安装或操作 OPPO，未运行任何 `connected*AndroidTest`。

## 2026-08-30：消息／会话“分享”与 Markdown 导出合并（代码／定向 JVM／Lint／Release 已验证）

- **唯一可见语义：** Assistant 页脚第二个操作保留原分享图标、“分享”可访问名与现有位置，但回调统一改为原“导出 Markdown”的完整实现：只把该条当前可见 Assistant 内容重建为 `.md`，通过 `FileProvider` 交给系统分享／保存。三点菜单内的“导出 Markdown”已删除，本次上下文来源与“创建分支”仍保留原有语义。
- **会话弹窗同步：** 聊天／工作两种会话操作弹窗都只保留一条分享图标与“分享”，其实际回调统一到当前会话路径 Markdown 文件分享；相邻的“导出 Markdown”行已删除，弹窗行数与高度计算同步减一。对话未当前选中时仍先打开目标对话并要求用户再次选择分享，禁止用旧会话内容套新标题导出。附件自身的“分享”仍传递原文件，未被改成 Markdown。
- **验证与产物：** `P6DConversationRowAccessibilityContractsTest` 定向 `4 tests / 0 failures`，覆盖页脚第二位、重复菜单清理、会话弹窗 owner 和紧凑行数；Debug／Release Kotlin、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,853,570` bytes，SHA-256 `84db52de30531c522c09382c19af33208701679bfe2cce2baa16f7737246b700`，v2/v3 正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未安装 OPPO、未运行 `connected*AndroidTest`；当前设备仍是上一个 `a33db234…` 包，不含此 UI／入口整合。因未安装新包，两个弹窗的真机可见尺寸和系统分享面仍待回读。

## 2026-08-30：OPPO 保数据覆盖（“架南烛枫”完整修复 Release）

- **安装前门禁：** 唯一连接设备为 OPPO PKH120 `3B157F009E800000`。候选包与现装包均为 `com.nanzhufeng.ai` code 66 / `0.3.0-p10j`、非 Debug，v2/v3 正式证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。覆盖前设备 `base.apk` SHA-256 为 `d578f7dd80d7f01ea420157f99e60000625a7d9f2919afa037052529460ffa35`。
- **覆盖与字节回读：** 已将正式包推送到精确临时路径，并且只执行一次 `pm install -r --user 0`，结果为 `Success`。安装后拉回的设备 `base.apk` 与本地 `app/build/outputs/apk/release/南枫AI.apk` 均为 `27,869,942` bytes，SHA-256 均为 `a33db2341a378a799476150b0a9576d97e628be6d295b22abecae6a86d54e43f`；设备回读仍为非 Debug、v2/v3 同证书。`lastUpdateTime=2026-08-30 01:37:50`，临时 APK 与本机校验副本均已精确清理。
- **数据不变证据：** 覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变，用户 0 仍为 `installed=true`、`stopped=false`。未卸载、未清数据、未读取私有业务数据、未部署 Debug／仪器包，也未运行 `connected*AndroidTest`。
- **尚未替代的验收：** 本条只证明正式包已安全覆盖且数据指纹未变，没有替南烛枫发起真实 GLM 请求。“架南烛枫”的真机／真实服务闭环仍需南烛枫新建对话发起一条 GLM-5.3 Flash 首条回复并回读。

## 2026-08-30：“架南烛枫”思考尾字泄漏完整修复（纠正旧结论）

- **真正根因：** 2026-08-29 的旧修复有两个漏洞。一是清理只比对 reasoning 的字面最后一段；当思考实际以“…回答框架。”结束、Provider 只把“架”重复到正文时，末尾句号会使严格 suffix 比对失败。二是更关键的状态所有权错误：原始 SSE 增量已把“架南烛枫…”写入运行时消息，旧代码先以该未清理文本执行 `RuntimeCompleted`，再绕过运行时直接修改 Room 消息。当前 UI／自动标题可能已消费旧终态，因而数据后写不能保证当屏纠正。
- **共享 owner 修复：** `RuntimeCompleted` 现显式携带 Provider 分层、尾字清理和首条称呼兜底后的 `finalVisibleText`，由 `ConversationRuntimeStateMachine` 在同一终态事件中原子替换原始流文本、标记完成并生成标题。终态后的可选操作只保留独立 reasoning 块，不再二次改写可见正文。尾字比对同时允许忽略 reasoning 末尾的 Unicode 标点、Markdown 装饰和空白，但仍只在“确认来自思考尾部的重叠 + 立即紧接已配置昵称”时删除，正常“架构调整”不会被裁剪。
- **回归证据：** 新增真实失败序列：先投影原始流文本“架南烛枫，直接给结论。”，完成事件必须原子收口为“南烛枫，直接给结论。”；另覆盖 reasoning=“先形成回答框架。”与泄漏“架”的标点边界。`AssistantExperienceSettingsContractsTest`、`P3BConversationRuntimeContractsTest`、`NormalChatCompletionPersistenceContractsTest`、`ProviderSseDecoderContractsTest` 与 `ProviderAdapterContractsTest` 共 `50 tests / 0 failures`；Debug／Release Kotlin、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。
- **产物与尚未验证边界：** code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,869,942` bytes，SHA-256 `a33db2341a378a799476150b0a9576d97e628be6d295b22abecae6a86d54e43f`，v2/v3 签名证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未发起真实 GLM Provider 请求、未安装 OPPO、未运行 `connected*AndroidTest`；只有获得新的覆盖授权并由南烛枫用真实 GLM 首条回复回读后，才能宣称真机／真实服务闭环。本条明确取代 2026-08-29 “流式思考尾字泄漏到昵称前修复”的完成性结论。

## 2026-08-30：图片原图预览真实测量根因修复（隔离模拟器真实触控已验证）

- **纠正上一次误判：** 上一条图片手势记录只解决了手势 owner 和状态累计，没有验证 Compose 的最终测量结果。隔离模拟器上用同一张用户截图复现后确认：旧实现的 `Modifier.size(renderedWidth, renderedHeight)` 仍会被全屏父容器最大约束钳回视口大小，但位移却按 `2.5×` 图片计算；所以双击后只剩一条约 `285px` 宽的图片可见区，视觉上并未真正放大。
- **完整修复：** 全屏稳定视口继续是唯一手势 owner，图片渲染改为独立 `OriginalImageZoomLayer`；它用 `Constraints.fixed` 按实际放大后的像素尺寸测量图片，再在稳定视口中按计算坐标放置。不再出现“位移按放大尺寸算、图片却被压回原尺寸”的分裂。
- **真实动态验证：** 新建的 Find N5 外屏基线隔离 AVD（`1140×2616 / 442dpi`）通过应用照片选择器加入用户提供的同一张截图。修复后双击从初始图片区 `[0,1187][1140,1506]` 变为占满视口宽度的放大区 `[0,948][1140,1745]`；第二次双击精确回到初始区域。另用两个 MT slot 注入真实双指展开，随后再单指拖动，截图与边界均确认缩放和位移已生效。这是运行时触控验收，不是源码 token 断言。
- **自动验证与产物：** `P6F2BImagePreviewUiContractsTest` 为 `6 tests / 0 failures`；Debug Kotlin、Release Kotlin、`:app:lintVitalRelease`、`:app:assembleRelease` 通过。code 66 / `0.3.0-p10j` 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,869,946` bytes，SHA-256 `43e970c2fc3a89124654bf7b93ecbaa35abf0f2c96e9843125c2624f4391d82b`，v2/v3 签名证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本条未安装 OPPO，未运行 `connected*AndroidTest`；OPPO 仍是上一个未含此测量修复的版本，必须重新获得覆盖授权才能使用本包。

## 2026-08-30：GLM-5.3 Flash 明确锁定 Max 推理（代码／定向 JVM／Lint／Release 已验证）

- **请求合同：** 智谱 `ZhipuChatAdapter` 在且仅在 `modelId=glm-5.3-flash` 时，为普通文本与官方联网 Chat Completions 请求明确写入 `thinking.type=enabled` 和 `reasoning_effort=max`；不再依赖智谱服务端默认档位。字段由 Provider Adapter 自有扩展点追加，Qwen、DeepSeek 与 OpenRouter 的共享请求体不会收到该参数。模型目录同步标记 GLM reasoning 能力，附件与其他未核验能力边界不变。
- **自动验证：** `ProviderAdapterContractsTest` 与 `ModelProfileAssetContractsTest` 共 `33 tests / 0 failures`，覆盖普通 GLM 请求、带 `web_search` 的 GLM 请求、DeepSeek 不泄漏 Max 字段以及模型目录能力；Release Kotlin、`:app:lintVitalRelease`、`:app:assembleRelease` 和定向 `git diff --check` 通过。
- **产物与边界：** code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，大小 `27,853,563` bytes，SHA-256 `df9bef08e6c77ac543550e05571ab53cb5830a12c662f1a052a7c9f09d60c3d0`，v2/v3 签名证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未发起真实智谱请求、未安装 OPPO、未读取设备或运行 `connected*AndroidTest`；设备仍是上一版图片手势包，需另获覆盖授权后才能使用明确锁定 Max 的新请求。

## 2026-08-30：OPPO 保数据覆盖安装（图片原图预览手势完整重构 Release）

- **安装与字节回读：** OPPO `3B157F009E800000`（PKH120）已使用 `pm install -r --user 0` 同签名覆盖 code 66／`0.3.0-p10j` 非 Debug Release。候选与安装后设备 `base.apk` 的 SHA-256 均为 `d578f7dd80d7f01ea420157f99e60000625a7d9f2919afa037052529460ffa35`；正式证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，设备 `lastUpdateTime=2026-08-30 00:06:24`。
- **数据保留与清理：** 安装前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变化，设备仍为 `stopped=false`。没有卸载、清数据、读取私有业务数据、部署 Debug／仪器包或运行 `connected*AndroidTest`；设备临时 APK 与本机证书校验副本均已精确清理。
- **真实验收边界：** 本次确认的是正式包、安全覆盖、安装字节与数据保留，不冒充图片手势已在真机闭环。仍需南烛枫在图片实际像素区域手工回读：双击放大到 `2.5×`、再次双击复位、双指连续缩放、放大后单指拖动。

## 2026-08-30：图片原图预览手势所有者完整重构（代码／定向 JVM／Lint／Release 已验证）

- **此前卡点：** 前两轮只验证了手势函数和静态源码 token，没有证明真实命中。实际实现把 `detectTransformGestures` 与 `detectTapGestures` 同时挂在会随倍率持续改变尺寸、位置的 `Image` 节点；两个识别器会竞争，节点命中区域也在手势中变化。连续双指事件还从 Compose 重组后的滞后 `zoom` 读取，导致倍率可能反复写回约 `1.x` 而不是逐帧累计。这正是代码“看起来有双击／缩放”但真机没有可用操作的共同根因。
- **完整修复：** 尺寸稳定的全屏预览视口成为唯一手势所有者；首次按下时用最新图片矩形判定是否命中，统一处理图片双击 `2.5×`／再次双击复位、双指中心缩放、放大后单指拖动，以及原倍率下的多图左右切换。`Image` 只负责按原图重测和绘制，不再持有任何 `pointerInput`。一次手势内用本地 `gestureZoom / gesturePlacedX / gesturePlacedY` 即时累计，再同步到 Compose，避免高频事件等待重组。黑色留白单击仍切换预览控件；顶部操作和批量下载链未改变。
- **自动验证：** `P6F2BImagePreviewUiContractsTest` 与图片批量下载定向契约通过，覆盖连续两段手动 pinch 从 `1.0× → 1.5× → 2.25×`、放大后拖动、双击触点保持与第二次双击复位；Debug／Release Kotlin、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。更宽的图片／会话回归为 `98 tests / 1 failure`，唯一失败是无关的 `KnowledgeLibraryUi.kt` 旧 Material `AlertDialog` 契约；图片相关项目全部通过。
- **产物与设备边界：** 最终 code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `d578f7dd80d7f01ea420157f99e60000625a7d9f2919afa037052529460ffa35`，大小 `27,853,556` bytes，v2/v3 签名证书 SHA-256 仍为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。该实现记录完成时只读核对了 OPPO；随后已按上方独立安装记录完成保数据覆盖。安装不替代南烛枫对图片实际像素区四项手势的手工回读。

## 2026-08-29：流式思考尾字泄漏到昵称前修复（代码／定向 JVM 已验证）

- **根因与修复：** V4 Flash 的个别流式回复会把折叠“思考过程”的末字（截图中的“架”）重复送到正文开头，形成“架南烛枫”。流式阶段现只保存 Provider 正文；完成后与独立 reasoning 一起写入最终消息。仅当正文开头与 reasoning 尾部（最多 16 字）严格重叠且其后立即为已配置昵称时，移除重叠，再补首条昵称。正常正文、非昵称开头和不相等的文本不会被猜测或裁剪；自动标题也在这次最终写入之后启动。
- **验证边界：** `AssistantExperienceSettingsContractsTest` 与 `NormalChatCompletionPersistenceContractsTest` 共 `15 tests / 0 failures`；Debug Kotlin 与定向 `git diff --check` 通过。未运行 `connected*AndroidTest`、未构建／安装 APK、未发起真实 Provider 请求或操作 OPPO；需在后续正式包上由南烛枫回读一次 V4 Flash 的真实回复。

## 2026-08-29：OPPO 保数据覆盖（流式完成链 Release 已安装）

- **安装与回读：** OPPO `3B157F009E800000`（PKH120）已以 `adb install -r --user 0` 覆盖 code 66／`0.3.0-p10j` 非 Debug Release。候选与设备 `base.apk` SHA-256 均为 `e9174f96383893406b6aaf1eddfad63a0f578e1640787b6f69d24f97fb21564b`；正式证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **数据边界：** 安装前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变化，设备为 `stopped=false`。没有卸载、清数据、读取私有业务数据、部署 Debug／仪器包或运行 `connected*AndroidTest`；本地 APK 校验副本已清理。未替用户发起真实 Provider 请求或回读该修复后的真实流式界面。

## 2026-08-29：流式完成链与本机保存错误分层（代码／定向 JVM／Lint／Release 已验证）

- **根因与修复：** Provider reasoning 之前在流式正文完成前写入；该可选折叠补充一旦本地写入失败，会把正文完成、自动标题和费用归因一起误报为 `LOCAL_SAVE`。现在正文先完成并持久化，标题与费用归因继续作为主完成链执行；reasoning 仅在之后尝试保存，失败只显示非阻塞的“思考过程未能保留”提示，不再撤销正文、标题或费用。
- **失败边界：** 原有笼统“请先不要重复发送”已删除。主回复保存、费用／Token 归因、发送 Attempt 三类本机失败分别说明真实影响；同题再次发送从不按正文去重，仍是新的模型请求。非流式路径也改为先保存可见 Assistant 正文，再写费用归因，避免生成孤立的费用记录。
- **验证与产物：** `NormalChatCompletionPersistenceContractsTest`、`P3BConversationRuntimeContractsTest`、`AssistantResponseModelAttributionRoomContractsTest` 通过，Debug Kotlin 编译、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `e9174f96383893406b6aaf1eddfad63a0f578e1640787b6f69d24f97fb21564b`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。全量 JVM 为 `938 tests / 3 failed / 3 skipped`；失败为既有 `AndroidPrivateAttachmentStorePdfRenderSizeTest`、`CenteredDialogScrimContractsTest` 与 `P6DConversationRowAccessibilityContractsTest`，未运行真实 Provider、`connected*AndroidTest` 或操作 OPPO。

## 2026-08-29：Provider 思考过程分层、首条称呼兜底与智谱联网（代码／定向 JVM 已验证）

- **V4 Flash 输出收口：** DeepSeek／Qwen Responses 解码器不再把 `reasoning`／`analysis` 项与 `message.output_text` 扁平拼接。最终答复和思考过程以不同内容块持久化；思考过程在回复顶部以默认收起的“思考过程”中性圆角矩形显示，点击后才展示。已保存的旧 DeepSeek 混合英文规划记录不改写原对话，但在满足“英文计划轨迹 + 明确中文结论开场”的高置信形态时，同样在内存阅读层自动折叠。新链路的过程内容不参与复制、搜索、自动标题、后续上下文、Memory／资料库或普通 Markdown 导出；Room 使用已有文本列的 `REASONING` kind，无新增列或 destructive migration。
- **称呼统一：** 原有系统提示保留，并在各 Provider 共用的最终展示路径加上首条成功回复的昵称兜底；流式请求只暂存开头极短片段，以避免重复称呼，同时不牺牲后续实时输出。用户本条消息明确要求其他称呼时不覆盖。
- **智谱联网闭环：** `GLM-5.3 Flash` 普通聊天与定时监控接入智谱官方 Chat Completions `web_search` 工具（`search_std`、返回搜索结果）；Composer 的模型说明同步标注官方实时检索。联网开启但有附件时仍不会伪装为已发送不支持的混合请求。OpenRouter、千问、DeepSeek、智谱四条路由均以对应的官方协议单独序列化，来源仅使用 Provider 返回的结构化 URL。
- **验证边界：** `ProviderAdapterContractsTest`、`AutomaticWebSearchPolicyTest`、`AssistantExperienceSettingsContractsTest`、`P3DMessagePresentationContractsTest`、`ScheduledMonitorRoomContractsTest` 与比较入口契约通过；`:app:lintVitalRelease`、`:app:assembleRelease`、定向 `git diff --check` 通过。完整 JVM 在既有 `AndroidPrivateAttachmentStorePdfRenderSizeTest` 失败后又被 Android Studio JBR `SIGSEGV` 中断，不能宣称全绿；此前还存在 `KnowledgeLibraryUi.kt` 旧 Material `AlertDialog` 造成的居中弹窗契约失败。code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `2a43d1cea560461aeab52107a47ce913d1549e44db462fba82980dc13af9f7b7`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行 `connected*AndroidTest`、未安装设备。

## 2026-08-29：真实 DOCX 读取与搜索快速定位收口（代码／定向 JVM／Lint／Release 已验证）

- **DOCX 根因与修复：** Office 预览器此前把规范 OOXML ZIP 内无内容的目录项（例如 `word/`、`_rels/`）误判为不安全路径，因而会把有效的 DOCX 误显示为“文档内容无法安全读取”。路径门禁现只忽略一个末尾目录分隔符，再继续严格拒绝绝对路径、空中间段、`.`／`..`、冒号和 XML 外部实体；实际文档 XML 仍按原有大小与安全解析上限读取，不执行宏、链接或嵌入内容。
- **快速定位根因与修复：** 搜索侧已携带 `ConversationId + MessageNodeId + AttachmentId`，但聊天侧把消息索引直接当作 LazyColumn 索引，漏算 ChatGPT／Claude 导入来源提示行，并可能被旧会话的“自动跟随最新”立即覆盖。定位现先关闭该旧跟随，以真实 LazyColumn 索引滚到所属消息，再由精确附件锚点带入可见区并短暂高亮；返回搜索仍保留用户离开前的搜索词、筛选、排序与像素位置。
- **验证与产物：** `OfficeOpenXmlTextExtractorTest`（包含真实 Office 目录项）、`ConversationSearchAttachmentPreviewUiContractsTest` 与 `ConversationSearchSurfaceContractsTest` 通过，Debug Kotlin 编译、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `bc31aa2975e2e8ade2e09b29832540923b273069a3a9d95455071f94f6a01b5c`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未覆盖安装设备、未运行 `connected*AndroidTest`；真实 OPPO 仍需以此新包回读同一 DOCX 与快速定位动作。

## 2026-08-29：费用明细三段直接切换（代码／定向 JVM／Lint／Release 已验证）

- **交互：** “费用与用量”汇总下方的分类控件改为一个 `56dp` 高的白色分段胶囊：“会话”“会话标题整理”“历史资料整理”横向等宽并列，直接点击即切换列表。当前项为主题橙色白字，其他项为白底深色字；不再出现下拉输入框、箭头或弹出菜单。
- **范围：** 汇总数值仍在控件上方，三类记录的数据、费用计算和筛选语义不变；只替换错误的控件形式，使固定的三类费用可一眼比较、一次点按切换。
- **验证与产物：** `ConversationCostLedgerSummaryContractsTest`、Debug Kotlin 编译、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `9c1bb8169a1505cfbeed170510834ab0e4c1cc76ac8d7e10de875666901e4e9a`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未覆盖安装设备、未运行 `connected*AndroidTest`。

## 2026-08-29：移除个性化无效成功提示（代码／定向 JVM／Lint／Release 已验证）

- **交互：** 个性化页右上角确认与全屏“自定义指令”确认仍保存同一份本机设置，但保存成功后不再显示“已保存至本机。”中央提示、Toast 或行内重复文案；开关和已填写字段本身就是即时状态反馈。超限或保存失败继续在原页面显示错误，不静默吞掉失败。
- **实现：** `AssistantExperienceSettingsViewModel` 不再生成成功 `notice`，应用根部的 `CenteredPersonalizationSaveNotice` 已删除；个性化页面不再传递或等待成功 notice。模型、导入、下载、同步等需要明确结果的其他成功／失败提示没有被本轮移除。
- **验证与产物：** 设置精简与开关几何定向 JVM 通过，Debug Kotlin 编译、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `00253bf6c3db59617d5a35d96ce2957378a46990d835e4b7038b67849dfb8312`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未覆盖安装设备、未运行 `connected*AndroidTest`。

## 2026-08-29：文件分类控件宽度与独立排序底色修复（代码／定向 JVM／Lint／Release 已验证）

- **根因与修复：** 文件分组左侧“全部类型”没有明确宽度，而其内部内容使用 `fillMaxSize`；Compose 因而把该灰色胶囊测量成整条排序行，视觉上合并了时间／大小／还原的底色，也让左侧文字没有可见宽度。类型触发器现固定为 `128dp × 36dp` 的独立胶囊；右侧时间、大小、还原继续各为独立 `72dp × 36dp` 胶囊，之间保留页面底色间隔。
- **验证与产物：** 搜索表面与附件预览定向 JVM 共 `8 tests / 0 failures`，Debug Kotlin 编译、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `5a17bd2b53d2314feb64cbb13ca2d68e9dd87018c864d958b40f0ac9002a41ec`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未覆盖安装设备、未运行 `connected*AndroidTest`；真实 OPPO 视觉仍待新包覆盖后回读。

## 2026-08-29：OPPO 保数据覆盖安装（图片预览手势事件隔离 Release）

- **安装与候选：** OPPO `3B157F009E800000`（PKH120）已使用 `pm install -r --user 0` 同签名覆盖 code 66／`0.3.0-p10j` 非 Debug Release。候选与安装后设备 `base.apk` 的 SHA-256 均为 `e968209da0c4c36f0e66816477aa2be8053d7c89afcd39d2abd4aa50cc187b2e`；正式证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **数据保留：** 安装前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均一致；设备仍为 `stopped=false`。未卸载、未清数据、未读取私有业务数据、未部署 Debug／仪器包；设备临时安装文件已清理。
- **真机边界：** 本次只核验覆盖链与数据不变性，未替用户点按图片预览。仍需在会话图片上实际确认：双击放大、再次双击还原、双指缩放与放大后的单指拖动；未运行 `connected*AndroidTest`。

## 2026-08-29：图片预览手势事件隔离（代码／定向 JVM／Lint／Release 已验证）

- **修复：** 图片原图预览已有缩放、单指位移与触点中心双击放大逻辑，但全屏黑色画布的单击工具栏层被错误挂在图片父节点，可能与子图片的 `detectTransformGestures`／双击 recognizer 竞争并吞掉手势。该单击层现为图片之后的独立黑色背景 sibling：黑色留白单击仍切换顶栏，图片实际像素区域只处理双指缩放、单指拖动和双击。
- **交互：** 图片区域双击以触点为中心放大至 `2.5×`，已放大时再次双击复位；缩放受原图最大倍率约束，手指拖动仅在放大内容超出视口后移动。关闭、下载、分享、多图切换与其他文件预览均未改变。
- **验证与产物：** `P6F2BImagePreviewUiContractsTest` 通过，覆盖双击放大／复位、触点保持、双指手势和黑色留白不竞争事件；Debug Kotlin 编译、`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `e968209da0c4c36f0e66816477aa2be8053d7c89afcd39d2abd4aa50cc187b2e`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。OPPO 当前仍是旧 SHA-256 `6bc1c7913bc16aaf478a30fb9544003420732d5b780f85775d5cfe1f2233bf07`，本轮未覆盖安装或执行 `connected*AndroidTest`，因此尚无真实手势回读。

## 2026-08-29：会话附件与搜索附件统一预览／打开能力（代码／定向 JVM／Lint／Release 已验证）

- **根因与修复：** 附件 domain owner 早已能安全提取 DOCX／XLSX／PPTX，但会话消息卡保留了一份只含 TXT／Markdown／JSON／CSV 的旧格式名单；搜索页则会把其余安全格式交给同一个本地文本 owner，造成“搜索可开、会话内不显示且点击无响应”。会话消息卡与 Composer 草稿卡现均直接调用 `isSafeTextAttachment(mimeType, displayName)`：Office 文档、XML／YAML／HTML 与可安全判别的通用二进制扩展名都显示本地惰性文本预览，普通点击进入同一只读预览；图片、PDF、视频、音频、ZIP 和未知／不安全格式仍保留各自的受限处理，不执行文件内容。
- **同类保证：** 搜索、会话、草稿与 ZIP 内可读条目使用同一 domain MIME／文件名安全判定和同一 `ConversationAttachmentPreviewProjection`。新增 UI 契约覆盖两个会话入口共享该判定，以及搜索与会话打开均归到 `attachmentPreview.text`；Office extractor 单测实际覆盖 DOCX、XLSX、PPTX 的安全文本提取。
- **验证与产物：** 搜索返回位置、搜索预览、会话附件预览与 Office extractor 共 `19 tests / 0 failures`；`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `6da064d8487b7c826ffe6763c23e570a06355d30f214defcd14802eb87321cb1`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行 `connected*AndroidTest`，未安装或操作设备；构建与定向测试不替代真机对实际 DOCX 的视觉回读。

## 2026-08-29：历史资料库即时开关、费用分类切换与设置标题居中（代码／定向 JVM 已验证）

- **历史资料库：** 开关不再显示“开启历史资料库？”二次确认。拨到开会即时同时持久化资料库调用与低频自动沉淀；拨到关则即时同时关闭两者，不删除既有资料或原对话。页面保留范围与数据发送边界说明，但不再对即时开关追加成功或阻断提示。
- **搜索筛选：** “全部类型、时间、大小、还原”统一为 `36dp` 高的紧凑组；三个右侧排序控件固定 `72dp` 宽，四项均降为辅助标签字号，文字和图标在完整 surface 内水平、垂直居中。左侧类型和右侧排序的锚点不变。
- **费用与设置顶部：** 费用页在“会话／会话标题整理／历史资料整理”三个汇总之后加入与模型预设同形的选择面，切换后只显示对应明细；提醒草案、定时监控记录继续保留为其他自动任务。设置首页“设置”以 `headlineMedium` 粗体严格居中，返回按钮不参与标题定位。
- **同类排查与验证：** 已枚举设置、模型、同步与 Composer 的所有 Switch：仅历史资料库曾弹出开启确认，现已移除；其余均直接更新状态。搜索、开关、费用分类与首页标题共 6 项定向合同通过，`:app:lintVitalRelease` 与 `:app:assembleRelease` 通过。code 66 非 Debug Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `45f7084d8f52791f9f7bc88ef864b8ec997ba8a0d172147e87051c48b9377a79`，v2/v3 签名有效，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行 `connected*AndroidTest`，未安装设备。

## 2026-08-29：OPPO 保数据覆盖安装（个性化开关职责分离 Release）

- **设备与候选：** OPPO `3B157F009E800000`（PKH120）已从 `com.nanzhufeng.ai 0.3.0-p10j / code 66` 同签名覆盖到本轮候选；安装前后均为非 Debug，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **字节与数据保留：** 设备覆盖后的 `base.apk` 已拉回验证，SHA-256 与本地 Release 完全一致：`6bc1c7913bc16aaf478a30fb9544003420732d5b780f85775d5cfe1f2233bf07`。`firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均不变，`stopped=false`。未卸载、清数据、读取私有业务数据或部署 Debug／仪器测试包。
- **清理与未覆盖范围：** 本次设备临时 APK 和本机校验证副本均已删除。未运行 `connected*AndroidTest`，也未替用户点按记忆开关、历史资料库确认或长摘要键盘状态；保数据安装不替代这些真实交互验收。

## 2026-08-29：个性化开关与右上角保存职责分离（代码／定向 JVM／Lint／Release 已验证）

- **职责：** “启用记忆”与“历史资料库”开关各自点击后立即持久化；历史资料库开启仍保留原有范围说明与一次确认。右上角确认只保存昵称、职业、更多信息、自定义指令与对话风格，不再携带或回写任一开关。
- **防回写：** 开关变更会同步进页面草稿，但不令顶部确认变为未保存状态；即使用户有未保存的文字编辑，之后确认也会以最新已保存的开关状态为准，避免旧草稿覆盖刚刚选择的开关。
- **验证与产物：** `SettingsUiSimplificationContractsTest`、`AssistantExperienceSettingsContractsTest`、`:app:lintVitalRelease`、`:app:assembleRelease` 与 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `6bc1c7913bc16aaf478a30fb9544003420732d5b780f85775d5cfe1f2233bf07`，v2/v3 签名有效，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行 `connected*AndroidTest`、未安装设备。

## 2026-08-29：记忆摘要底部输入框遮挡收口（代码／定向 JVM／Lint／Release 已验证）

- **界面：** 记忆摘要的内容区现在按底部输入框的实际测量高度增加可滚动末端 inset；最后一段摘要可完整滚动到输入框上方，不会被覆盖。输入框继续是覆盖在原画布上的单一白色悬浮面，不新增整条承托底或改变记忆摘要信息结构。
- **键盘：** 输入框加入 IME 避让；键盘出现时，其总高度会重新测量并同步给内容区，底部文字不会在键盘或输入框下被截断。
- **验证与产物：** `MemorySummaryUiContractsTest`、`:app:lintVitalRelease`、`:app:assembleRelease` 与 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `2c8885e3dd34430c1966f9589195771dea456782839bc837af87c1cd46d02158`，v2/v3 签名有效，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行 `connected*AndroidTest`、未安装设备；构建与合同不替代真实长摘要和键盘状态的屏幕回读。

## 2026-08-29：搜索附件打开复用已验证状态与 Google 头像显示（代码／定向 JVM／Lint／Release 已验证）

- **搜索附件性能：** 搜索可见行的缩略图、文本首屏、PDF／音视频元数据完成首次 SHA-256 校验后，用户点击同一个应用私有附件会复用受 `引用 + 哈希 + 字节数 + 路径 + 修改时间` 约束的进程内验证状态，不再重复完整扫描。文本文件直接复用搜索列表已准备的安全文本预览；ZIP 物化缓存同样受这条状态链约束。修改、替换、大小或修改时间变化会立即失效并重做完整校验，首次访问、大小门禁和不安全文件拒绝规则均未放宽。
- **Google 头像：** “Google 账号与同步”现在在已登录状态显示 Google 账号实际头像，而非固定账户图标。按南枫记的成熟链路，页面先读取仅限当前账号与头像 URL 的私有缓存，随后优先经带当前 Supabase JWT 的 `google-avatar` 函数刷新；函数不可用时只允许无重定向的 `https://*.googleusercontent.com` 图片直连，8 秒超时、`image/*` 校验、2 MiB 流式上限。没有头像或读取失败时才显示姓名首字母；退出或切换账号会删除该账号缓存。未保存 Google ID Token，也未向同步快照写入头像缓存。
- **验证与产物：** 搜索附件／私有数据／搜索界面定向 JVM 通过；头像缓存、代理／直连约束与账号 UI 定向 JVM 通过。`:app:lintVitalRelease` 与 `:app:assembleRelease` 通过，`git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `d47963d293b70480e651e7076d16ddc4768adcaf40f35c84f3a3b64a682e3514`，v2/v3 签名有效，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行 `connected*AndroidTest`、未覆盖安装设备、未发起真实 Google 登录、真实头像或云同步调用；真实账号头像仍需在真机登录后确认。

## 2026-08-29：智谱 GLM-5.3 Flash 金额与历史账本补齐（代码／定向 JVM／Lint／Release 已验证）

- **价格与展示：** `GLM-5.3 Flash` 已进入与其他模型同一套版本化本机估算链，官方限时价按输入 ¥0.40／百万 Token、输出 ¥1.40／百万 Token、缓存命中 ¥0.115／百万 Token 估算；限时价截止 8 月 31 日后自动切换为输入 ¥0.80、输出 ¥2.80、缓存命中 ¥0.23 的标准价。智谱人民币金额直接展示为 `≈ ¥…（估算）`，不会错误走美元换算。
- **历史回填与覆盖面：** 已存在的普通 GLM 回复不迁移、不改写：只要本机留有模型、发生时间及输入／输出 Token，读取页脚与“费用与用量”时会按当日价目即时补算。账本同步纳入已返回 Token 的定时监控和历史资料整理调用；累计金额与明细均不保存 Prompt、回复、附件、Key 或 Provider 原始回包。
- **能力边界：** 官方模型具备更广的多模态和工具能力，但 Android 当前智谱 Adapter 只会真实发送文本；目录继续只声明当前客户端可传输的能力，不把尚未接通的输入或工具伪装为可用。
- **验证与产物：** 费用估算、历史回填、模型目录与费用页定向 JVM 合同通过；全量 JVM 为 `921 tests / 2 failures / 3 skipped`，仅存未触及的 `CenteredDialogScrimContractsTest` 和 `P6DConversationRowAccessibilityContractsTest`。`:app:lintVitalRelease`、`:app:assembleRelease`、`git diff --check` 与 v2/v3 签名验证通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `bb299f69af652c0938c316691443b5b774d7f95bb4d3642fb562345973dec42c`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行 `connected*AndroidTest`、未覆盖安装任何设备、未发起真实模型调用。

## 2026-08-29：多行资料库确认弹层改为圆角矩形（代码／定向 JVM／Lint／Release 已验证）

- **界面：** “开启历史资料库？”含多段隐私、模型和 Token 说明，改用共享 `24dp` 多行圆角矩形面，不再复用单行控件的 `999dp` 胶囊。合同同时明确：胶囊只用于单行操作卡／控件；多行卡、说明面、确认弹层和长文本承载面禁止胶囊。
- **验证与范围：** 新增 `PillShapeScopeContractsTest`，并与资料库开关、设置精简的定向 JVM 契约一同通过；`:app:lintVitalRelease`、`:app:assembleRelease`、`git diff --check` 与 v2/v3 签名验证通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `b4c3dc1f8f147259e6932866e1532223754a28b9d586ad0cdba1207b4381a86e`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。全量 JVM 为 `919 tests / 2 failures / 3 skipped`，既有失败仍仅为未触及的 `CenteredDialogScrimContractsTest` 与 `P6DConversationRowAccessibilityContractsTest`。本轮未改变资料库开关语义、模型外发边界或任何设备安装，未运行 `connected*AndroidTest`。

## 2026-08-29：历史资料库统一写入与调用总开关（代码／定向 JVM／Lint／Release 已验证）

- **统一语义：** 设置只保留“历史资料库”一个可见开关；开启时经一次确认，同时允许低频自动整理历史对话和普通聊天按相关性调用已沉淀资料。关闭时同时取消后续 WorkManager 整理任务和资料库检索，不删除已保存资料、原始对话、Memory 或自定义指令。手动“整理当前对话”仍为独立的一次性、可编辑候选流程。
- **安全迁移：** 旧版“资料库调用已开、自动整理尚未确认”的两项持久偏好保留为内部迁移证据，但有效状态为两者同时为真；因此升级不会开始任何新的模型外发，用户需通过这个总开关明确确认一次。启动时同步取消遗留后台任务，普通发送、自动整理 owner、完成对话入队和投资来源审计均只读取同一个有效状态。
- **自动验证与产物：** 设置、运行时上下文、统一总开关共 4 个定向 JVM 类通过；`:app:lintVitalRelease`、`:app:assembleRelease`、定向 `git diff --check` 和 v2/v3 签名验证通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `b5292e8da4dca9074e73dcff1ea5208e375ea77665dcc03c90dc769db869bc8b`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **全量与未覆盖范围：** 全量 JVM 为 `918 tests / 2 failures / 3 skipped`；剩余失败是未触及的 `CenteredDialogScrimContractsTest` 与 `P6DConversationRowAccessibilityContractsTest`，不属于历史资料库链。未运行任何 `connected*AndroidTest`，也未覆盖安装 OPPO 或进行真实模型外发／后台整理验收。

## 2026-08-29：OPPO 保数据覆盖安装（ZIP 目录浏览 Release）

- **安装：** OPPO `3B157F009E800000`（PKH120）已通过 `pm install -r --user 0` 覆盖 code 66 非调试 Release；未卸载、清数据、安装 Debug／仪器包或读取私有业务数据。
- **门禁与回读：** 安装前后均为 `com.nanzhufeng.ai`、`0.3.0-p10j`／code `66`、非 Debug，设备与本地 APK 的 SHA-256 同为 `294196af20f517dc53ef12db214ec8b0e44adc9a7cc50c5a417f7da393511456`；正式证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。`firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 均未变化。设备临时安装 APK 与本机签名校验副本均已删除。
- **未覆盖范围：** 未运行任何 `connected*AndroidTest`，也未替用户点击或验收 ZIP 文件夹进入、文件预览、不同皮肤或深层返回；覆盖安装与哈希一致不能替代这些交互验收。

## 2026-08-29：ZIP 压缩包目录可钻取浏览（代码／定向 JVM／Lint／Release 已验证）

- **真实操作：** “压缩包内容”不再只是扁平展示。根目录与各级文件夹只显示当前层直接子项；点击文件夹进入，关闭／返回先回到上级，再回外层 ZIP。没有显式目录记录的 ZIP 也会从安全文件路径推导出可进入的目录；文件条目继续复用图片、PDF、音视频、文本、Office 与内层 ZIP 的既有受限预览。
- **安全边界：** 普通目录路径和内层 ZIP 路径分开保存；每次只返回当前层的直接子项，不把压缩包整棵解压到磁盘。绝对路径、`..`、控制字符、异常目录段、超过 10000 个扫描项及超过 2000 个当前层条目的情况仍拒绝或明确截断；点击文件前的既有大小、摘要、魔数与压缩比门禁不变。
- **自动验证与产物：** ZIP 索引与界面合同各 `1 test / 0 failures / 0 errors`，定向 `git diff --check` 通过；`:app:lintVitalRelease` 与 `:app:assembleRelease` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `294196af20f517dc53ef12db214ec8b0e44adc9a7cc50c5a417f7da393511456`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **全量与未覆盖范围：** 本轮全量 JVM 为 `917 tests / 4 failures / 3 skipped`；失败均来自未触及的居中弹层、运行时上下文、会话行无障碍与设置开关源码合同，ZIP 两项定向测试均通过。未运行任何 `connected*AndroidTest`，未安装或操作 OPPO，也未对真实 ZIP 的目录点击、深层返回与不同皮肤做人工回读。

## 2026-08-29：回答上下文来源的未使用文案收口（代码／定向 JVM／Lint／Release 已验证）

- **文案语义：** 没有额外检索结果的“本次上下文来源”改为“本次未调用你的记忆、资料库或历史对话；仅使用本轮输入、当前对话路径及固定系统规则。”它只说明这一次回答实际未使用这些来源；不表示资料未保存、相关功能已关闭或没有固定系统上下文。
- **自动验证与产物：** `AnswerContextDisclosureContractsTest` 通过；`:app:lintVitalRelease` 报告 `No issues found`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `ebeb12457f11d31ec11fdcb82615d9b6e2e65638a33aeeda1285fc6376c08f8d`，签名证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **未覆盖范围：** 未安装或操作 OPPO，未运行任何 `connected*AndroidTest`，也未对真实 Provider 请求重放来源审计；定向契约与构建不能替代实际回复的屏幕回读。

## 2026-08-29：ZIP 原始包内置状态与安全删除提示（代码／全量 JVM／Lint／Release 已验证）

- **判断方式：** “本机数据”在 ZIP 清理入口旁读取真实附件引用状态，而不是把“导入完成”等同于可删除：未完成任务显示“请保留 ZIP 原始包”；仍有已归属附件引用 ZIP 时显示“尚未全部内置”及数量；全部资料脱离 ZIP 时显示“可以安全删除 ZIP 原始包”；清理成功后显示“导入资料已内置”。若仍有依赖但原包不见，明确提示重新导入，不伪报已内置。
- **删除门禁：** “整理并删除”仍先流式复制依赖附件至受管本机目录，逐项核验大小和 SHA-256、切换稳定引用，最后才删除原包；任一环节失败均保留原包。已经完全内置时才提供直接删除原包的确认说明。
- **自动验证与产物：** 导入清理／设置定向 JVM 合同通过；Android 全量 JVM `910 tests / 0 failures / 0 errors / 3 skipped`，`:app:lintVitalRelease` 报告 `No issues found`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `469ad3613a1a8d0ade2d79a6f3a97d6dc0d8b1d55200f4f9917ce2cb483fe404`，签名证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **未覆盖范围：** 未安装或操作 OPPO，未运行任何 `connected*AndroidTest`，也未读取用户实际导入状态或删除用户 ZIP；因此“当前这一个 ZIP 能否删除”仍应以更新后页面显示的状态为准。

## 2026-08-29：文件筛选左置与排序右侧固定（代码／全量 JVM／Lint／Release 已验证）

- **布局：** 文件分组的格式触发器固定在左侧，仅显示当前值与下箭头，删除“类型”前缀；时间、大小、还原始终是右侧排序组。全部、图片、视频、音频和文件分组共用这一右侧锚点，因此切换分组不会移动排序组位置。
- **边界：** 保留胶囊形状、排序／筛选逻辑、滚动复位、预览与文件能力；正文分组仍不显示该操作行。
- **自动验证与产物：** 搜索布局定向合同通过；Android 全量 JVM `910 tests / 0 failures / 0 errors / 3 skipped`，强制重新执行的 `:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `13307efb69d561a8dca0397a23ccc080ea423b43bd274b4acd8f2ec9c76461c7`，签名证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **未覆盖范围：** 未安装或操作 OPPO，未运行任何 `connected*AndroidTest`，也未做各分类切换后的目标视口视觉／触控回读。

## 2026-08-29：文件类型低频格式并入其他（代码／全量 JVM／Lint／Release 已验证）

- **筛选层级：** 文件“类型”下拉只保留 MD、PDF、ZIP、DOCX、TXT、JSON 与“其他”。CSV、XML、YAML、HTML、XLSX、PPTX 均从下拉移除并统一归入“其他”，因此选择“其他”会精确筛出这些格式及原有未单列格式。
- **能力边界：** 仅改变本地搜索筛选投影；各格式的 MIME／后缀识别、受限预览、下载／分享和安全边界均未删除或降级。
- **自动验证与产物：** 搜索筛选定向合同通过；Android 全量 JVM `910 tests / 0 failures / 0 errors / 3 skipped`，`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `f2c0a084244c4482142f8db8b6e0ddbeb8a7eeaac7258a7a5f8b7a0f8bcb5186`，签名证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **未覆盖范围：** 未安装或操作 OPPO，未运行任何 `connected*AndroidTest`，也未做目标视口的实际菜单视觉／触控回读。

## 2026-08-29：搜索分类与排序胶囊对齐（代码／全量 JVM／Lint／Release 已验证）

- **布局与形状：** 附件搜索的“时间”“大小”“还原”固定在同一条 `40dp` 高排序行、同一基线和相同间距；“还原”不再上浮为单独一行。搜索类别、时间、大小、还原及文件“类型”触发器统一为胶囊形状，表面与按压命中轮廓一致；下拉菜单本身仍保持列表菜单形态。排序、类型过滤、月份默认分组、滚动复位和定位逻辑不变。
- **自动验证与产物：** 搜索界面定向合同通过；Android 全量 JVM `910 tests / 0 failures / 0 errors / 3 skipped`，`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `04b0a0c4809a58d220766b65bb3d4c3a3977d81dd2057c119b727fa04a40bc4a`。
- **未覆盖范围：** 未安装或操作 OPPO，未运行任何 `connected*AndroidTest`，也未在目标视口完成截图后的视觉／触控回读；自动回归与构建不能替代实际屏幕验收。

## 2026-08-29：设置入口图标按功能语义重设（代码／全量 JVM／Lint／Release 已验证）

- **图标语义：** “模型与联网”改为节点连接 `Hub`，表达模型服务与联网能力；“导入与导出”改为双向文件流 `ImportExport`；“本机数据”改为本地存储 `Storage`。三项均保留现有圆端 Material 图形语言、行高、点击面、分组顺序及既有正文／数据管理中性灰色，不新增说明或改变任何路由、数据与联网行为。
- **自动验证与产物：** 图标语义合同和本机数据导航合同通过；Android 全量 JVM `910 tests / 0 failures / 0 errors / 3 skipped`，`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `29bfe50d46791d1781aaace482ef5496f937f530203af2952dd6581c44c008d3`，签名证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **未覆盖范围：** 未安装或操作 OPPO，未运行任何 `connected*AndroidTest`，也未完成浅／深皮肤、不同字号和目标视口的真实视觉／触控回读；构建与源码合同不能替代这些验收。

## 2026-08-29：导入结果入口与详情信息精简（代码／全量 JVM／Lint／Release 已验证）

- **入口：** JSON、ZIP 两张导入卡的“导入结果”统一收为单行入口，只保留名称和右箭头；删除批次、对话、附件等重复统计小字及“查看详情”文字。空状态仍保留入口，不影响 JSON／ZIP 各自独立结果页。
- **详情：** 每个批次只保留来源、状态、已导入对话数，以及非零的未导入／已跳过数；ZIP 额外保留附件恢复状态、已恢复数和非零的源包缺少／未关联数。附件推断、回退命名、来源记录和个性化资料等内部细节不再显示。重试附件恢复与删除本批次仍保留。
- **数据边界：** 仅改变 UI 投影；导入任务、附件恢复、失败／跳过计数、原始文件隐私、批次删除和重试 owner 均未改变。
- **自动验证与产物：** 导入 UI 与设置精简定向合同通过；Android 全量 JVM `909 tests / 0 failures / 0 errors / 3 skipped`，`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `ac6be267d22aae01d2ec37012ebb0a5064f0d00d73e07caefd12287a95848e63`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **未覆盖范围：** 未安装或操作 OPPO，未运行任何 `connected*AndroidTest`，也未完成浅／深皮肤、空／完成／异常批次和大字体的目标视口视觉回读。

## 2026-08-29：模型名统一加粗与 Composer 唯一例外（代码／全量 JVM／Lint／Release 已验证）

- **统一字重：** 模型设置的当前预设和下拉候选、Composer 模型选择面与“换模型重答”、Assistant 回复页脚、消息操作信息、附件发送对象、费用／调用／上下文／诊断记录、知识溯源及生成确认中的具体模型名统一使用 `Bold`。复合元数据只加粗模型名片段，Provider、时间、金额、说明和状态保持原层级。
- **唯一例外：** 主界面底部 Composer 的短模型名（普通与临时聊天共用）明确改为正常字重；既有 `13sp`、圆润字体、宽度、胶囊轮廓和短名规则均不改变。Assistant 回复页脚不属于该例外，短模型名继续加粗。
- **自动验证与产物：** 新增统一富文本字重与跨入口源码合同，更新旧 Composer 粗体合同；Android 全量 JVM `909 tests / 0 failures / 0 errors / 3 skipped`，`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `a412a8b2726f0b2b62b16765edc20e258f7c00998a05ae85dfca8aa3b132aba8`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **未覆盖范围：** 本轮未安装或操作 OPPO，未运行任何 `connected*AndroidTest`，也未做浅色／深色、不同字号和目标视口的真实视觉回读；自动合同与构建不能替代实际屏幕字重验收。

## 2026-08-29：Google 登录 OAuth 配置与取消误报修正（云端／代码／全量 JVM／Lint／Release 已验证）

- **Google Cloud：** 已在 `nanfeng-cloud` 创建 `南枫 AI Android` OAuth 客户端，绑定包名 `com.nanzhufeng.ai` 与正式签名 SHA-1 `2A:B7:0D:EE:32:BC:61:F0:59:63:80:CD:32:8F:A6:73:C0:C8:61:49`；Data Access 已保存 `openid`、`userinfo.email`、`userinfo.profile` 三项非敏感基础 scope，并在保存后回读。现有 Supabase Web Client ID 与 App 配置一致，Supabase Google Provider 已启用。
- **App 提示：** Credential Manager 返回取消类异常时不再断言用户主动取消，统一显示“Google 未完成授权，请重试。”；登录 nonce、Google ID Token 换取 Supabase 会话、Keystore 会话保护及同步范围均未改变。
- **自动验证与产物：** 提示合同定向测试通过；Android 全量 JVM `907 tests / 0 failures / 0 errors / 3 skipped`，`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `58faf0be98e6da6a03a52d31c5cef3d7b565dc31483a599fc61dd6f78850ab77`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **未覆盖范围：** Google Cloud 提示新配置可能需要 5 分钟至数小时生效；本轮没有替用户执行真实 Google 账号登录、Supabase 会话创建、密文同步或跨设备恢复，也未安装或操作 OPPO、未运行任何 `connected*AndroidTest`。真实登录仍需在更新后的正式包中人工重试，不能由云端回读、JVM 或构建冒充。

## 2026-08-29：DeepSeek V4 Flash 官方直连预设（代码／全量 JVM／Lint／Release 已验证）

- **完整入口：** DeepSeek 现有官方直连服务增加 `DeepSeek V4 Flash`／`deepseek-v4-flash` 预设；设置仍只有一个 DeepSeek 服务商入口、同一固定官方端点与独立加密 API Key，默认预设继续是 V4 Pro。Composer“日常”二级选择面增加完整名称，Composer 与 Assistant 页脚短名为 `V4 Flash`；不改变 `Auto`、自动标题、已有会话选择或服务商数量。
- **模型与费用边界：** 冷启动模型资料按官方当前文档记录 1M 上下文、384K 最大输出、文本、思考、JSON、工具与流式能力，图片、PDF、视频和音频保持不支持。版本化本机估算采用当前公开的缓存命中 `$0.0028`、缓存未命中 `$0.14`、输出 `$0.28`／百万 token；Provider 实际结算优先，峰谷价格、联网工具、优惠和缓存写入未披露时不伪装成实际扣费。
- **验证与产物：** 模型目录、冷启动资料、Composer 路由、短名、费用与 DeepSeek 官方 Adapter 定向合同通过；Android 全量 JVM `906 tests / 0 failures / 0 errors / 3 skipped`，`:app:lintVitalRelease`、`:app:assembleRelease` 与定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `ec77f98676a2fbe7783aa063e36189b01f3c214d11d250d3cb8d5c15410e6a94`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **未覆盖范围：** 未读取或使用 DeepSeek API Key，未发起真实 DeepSeek 请求，未做目标视口视觉／触控验收；未运行任何 `connected*AndroidTest`，未安装或操作 OPPO。Google 登录的后续配置与修正以本文上方更新为准。

## 2026-08-29：OPPO 保数据覆盖安装（最新 Release 已验证）

- **安装：** OPPO `3B157F009E800000`（PKH120）已使用 `pm install -r --user 0` 覆盖 code 66 非调试 Release；没有卸载、清数据、数据库注入或 Debug／仪器包部署。
- **门禁与回读：** 安装前后包名均为 `com.nanzhufeng.ai`、version `0.3.0-p10j`／code `66`，证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，`firstInstallTime=2026-08-20 15:15:31` 与 `ceDataInode=1459104 / deDataInode=1433378` 不变。设备回读 APK 与本地 `app/build/outputs/apk/release/南枫AI.apk` 的 SHA-256 同为 `19761a512ae3e24cd4ae2302fa340a2d7350e07f4b252c9d41eb9978a8b357e8`；远端临时安装文件及本地只读校验副本已清理。
- **未覆盖范围：** 未运行任何 `connected*AndroidTest`，也未替用户点击或验收模型设置、来源提示和智谱真实服务；覆盖安装与哈希一致不等同于上述交互或真实 API 链路验收。

## 2026-08-29：会话导入来源整行提示（代码／定向 JVM／Release 已验证）

- **界面：** ChatGPT／Claude／ChatGPT ZIP 的会话来源提示统一改为完整可用宽度的低对比主题色灰底条，文字水平居中；它仍只陈述来源，不是入口或操作按钮。
- **验证与边界：** `AssistantGeneratedImageGroupUiContractsTest` 通过，Release 编译、`lintVitalRelease`、签名验证与定向 `git diff --check` 通过。最新 code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `19761a512ae3e24cd4ae2302fa340a2d7350e07f4b252c9d41eb9978a8b357e8`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行任何 `connected*AndroidTest`，未安装或操作 OPPO，尚未做真机视觉回读。

## 2026-08-29：智谱 GLM-5.3 Flash 官方直连与四项紧凑服务商切换（代码／全量 JVM／Release 已验证）

- **完整入口：** 新增独立 `ZHIPU` Provider、固定官方 Base URL `https://open.bigmodel.cn/api/paas/v4`、`GLM-5.3 Flash` 预设、独立加密 API Key、启用／测试连接、状态行、调用记录和 Composer“日常”手动模型选择。解析后的模型路由、冷启动目录资料、失败归因和显示短名均已识别该 Provider；不改变 `Auto`、自动标题或已有会话选项。
- **能力边界：** 当前采用 OpenAI 兼容文本流式调用。图片、PDF、音视频、工具／网页搜索均明确拒绝或不声明支持，不伪装为已发送；本机没有冻结的智谱价格表，因此金额保持未知，不虚构人民币金额。`glm-5.3-flash` 的真实账户可用性及服务端功能仍需用户填入 API Key 后通过“测试连接”和实际发送确认。
- **顶部布局：** Provider 切换仍是唯一的灰色胶囊轨道，不做四个独立大按钮；四项均放在同一紧凑分段栏内，轨道内边距 `3dp`、选项视觉高度 `32dp`、标签统一 `labelMedium` 单行省略，选中态继续使用当前主题色和白字。
- **导入来源条：** ChatGPT／Claude／ChatGPT ZIP 的会话来源提示改为完整可用宽度的低对比灰底条，文字水平居中；它仍只是静态来源事实，不新增操作或改变导入数据链。
- **验证与交付边界：** 智谱模型、路由、资料资产、OpenAI 兼容请求与设置 UI 定向 JVM `59 tests / 0 failures / 0 errors / 0 skipped`；Android 全量 JVM `903 tests / 0 failures / 0 errors / 3 skipped`，`:app:assembleRelease`、`lintVitalRelease` 和定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `f7d6a8797fe0bb666f004fdb7594a3f73a31bfe7246e90163dba4a2b58b75e20`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行任何 `connected*AndroidTest`，未安装或操作 OPPO，未以真实智谱凭据发起联网调用；不得将构建与合同测试写成真实服务或视觉／触控验收。

## 2026-08-29：OpenRouter Fable 5 加入主聊天选择（代码／全量 JVM／Release 已验证）

- **可见入口：** `Claude Fable 5` 加入 Composer 模型选择面的“深度”组首项；选择后仍经既有 OpenRouter 预设、目录核验／冷启动回退、实际调用归因与人民币费用展示链，不新增独立入口或视觉风格。
- **不变边界：** 不改 `Auto` 默认、既有会话的已选模型、OpenRouter API Key 或自动标题的 Fable 5 排除策略；真实服务可用性仍以本机已验证的 OpenRouter 模型目录与发送结果为准。
- **验证与交付边界：** 定向 `P6GModelRouterContractsTest` `10 tests / 0 failures / 0 errors / 0 skipped`，Android 全量 JVM `900 tests / 0 failures / 0 errors / 3 skipped`，`:app:assembleRelease` 与定向 `git diff --check` 通过。Gradle 仅对本轮进程使用 Android Studio JBR 21 与 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `f63f6b0468287c167329e8145e6d87aff9636eb6a7645974d1688f7b2f93ffe5`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行任何 `connected*AndroidTest`，未安装或操作 OPPO，未以真实 OpenRouter 凭据调用 Fable 5；因此真实目录可用性、发送和屏幕交互仍待实际授权验收。

## 2026-08-29：搜索附件双列排序与文件类型下拉（代码／全量 JVM／Release 已验证）

- **排序形式：** 删除原单一“默认排序”胶囊，改为同一紧凑栏的“时间”“大小”两列与“还原”。时间、大小首次点击均为倒序，再点切为正序；当前列文字和箭头使用当前主题色，“还原”恢复按月分组及组内时间的默认浏览。显式排序仍是全局稳定顺序，并继续按真实 `timestampEpochMs`／`byteCount` 排序。
- **类型筛选：** 仅“文件”分组在排序栏右侧显示“类型”下拉，支持全部类型、MD、PDF、ZIP、DOCX、XLSX、PPTX、TXT、JSON、CSV、XML、YAML、HTML 与其他。类型依据持久引用的 MIME 及同一份中文句点／全角句点文件名后缀归一规则识别；未单列扩展名统一为“其他”。图片、视频、音频、全部和正文不显示此下拉。
- **位置与数据边界：** 切换排序或文件类型均清除旧锚点、滚回当前文件网格顶部；预览返回与精确定位使用当前过滤后的命中集。只改本地 UI 投影，不改搜索索引、附件引用、真实大小、预览、共享文件计数或删除链。
- **验证与边界：** 定向搜索回归 `11 tests / 0 failures / 0 errors / 0 skipped`；Android 全量 JVM `900 tests / 0 failures / 0 errors / 3 skipped`，`:app:assembleRelease`、`lintVitalRelease` 与定向 `git diff --check` 通过。首次完整测试进程遇到 Android Studio JBR 的 `SIGSEGV`，仅为重试 Gradle 进程使用 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` 后通过，未写入项目配置。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `7ade58f368bce03faa86de648859be1e8684e4c15ec4135e717ea387a5095a8a`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行任何 `connected*AndroidTest`，本轮未安装或操作 OPPO，尚未完成目标视口的真实点击、长列表、大字体和深色皮肤视觉回读。

## 2026-08-29：金额统一换算人民币与侧栏显示创建时间（代码／全量 JVM／Release 已验证）

- **人民币展示：** Assistant 页脚、“费用与用量”汇总／明细和调用记录全部统一显示 `¥` 人民币金额，不再显示美元符号、ISO 币种微单位或混币种直接相加。底层 Provider 原币金额、ISO 币种、价格版本及实际／估算来源保持不变，只在展示层换算；不支持的未知币种明确显示无法换算，不冒充人民币。
- **汇率版本：** 使用 ECB 2026-08-27 同日参考价 `1 EUR = 1.1645 USD`、`1 EUR = 7.8258 CNY` 交叉计算，冻结为 `1 USD = 6.7203091455560326320 CNY`。费用页只显示一条简洁基准“美元按 1 美元 ≈ ¥6.7203 换算（2026-08-27）”；实际美元账单换算写“约”，本机估算仍写 `≈` 与“估算”。主界面页脚按合同只保留金额本身，并固定四位小数。
- **侧栏时间：** 普通左侧栏每条对话标题右侧日期从 `updatedAt` 改读 `createdAt`；新增消息、重命名、置顶等更新不再改变这里显示的日期。最近列表仍沿用既有最新更新时间排序，排序事实与展示字段没有混用。
- **验证与边界：** 人民币换算、汇总、页脚、调用记录及侧栏创建时间共 `111 tests / 0 failures / 0 errors / 0 skipped`；Android 全量 JVM `899 tests / 0 failures / 0 errors / 3 skipped`，`:app:assembleRelease`、`lintVitalRelease` 与定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `1f17d1957a446886324430f3794dcb579619882df0e5f0598ea050016e5ea45d`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行任何 `connected*AndroidTest`，本轮未安装或操作 OPPO，也未完成真机金额排版与侧栏日期视觉回读。

## 2026-08-29：本机数据归位、子入口返回层级与草稿精简（代码／全量 JVM／Release 已验证）

- **信息架构：** “导入与导出”只保留导入、导出、备份和恢复；原“清理与删除”入口及页面统一更名为“本机数据”，并接回本机数据概览及原有清理、删除和失败重试能力。设置“关于”改用语义正确的 `Info` 图标，未另造视觉样式。
- **返回层级：** 从“本机数据”进入搜索、记忆、知识库或项目时，现有设置导航栈保留“本机数据”为父级；系统返回及系统左右滑返回先回到“本机数据”，不直接退出到主界面。搜索关闭／返回仅在本机数据来源下消费该父级回退，普通搜索入口行为不变。
- **信息精简：** 本机数据概览删除没有准确明细入口的“草稿”行；真实草稿数据、会话内容和清理 owner 均未删除或迁移。
- **验证与边界：** 相关定向回归 `113 tests / 0 failures / 0 errors / 0 skipped`；Android 全量 JVM `894 tests / 0 failures / 0 errors / 3 skipped`，Release 编译、`lintVitalRelease` 与打包通过，定向 `git diff --check` 通过。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `80b09122589e149a74b1e1d6c6f44da470e5d88566a128825ed41c696e030cd2`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未运行任何 `connected*AndroidTest`，本轮未安装或操作 OPPO，也未完成真机左右滑与页面视觉回读，不得把自动回归写成真机手势闭环。

## 2026-08-29：PDF 快速翻页、压缩包内文件继续打开与常用文档预览（代码／全量 JVM／Lint／Release 已验证）

- **PDF 翻页速度：** 同一阅读会话只在首次打开时完整校验 SHA-256，后续翻页在附件身份、大小、摘要和文件修改时间均未变化时复用已验证的 `PdfRenderer`。界面保留最多 4 页、总计不超过 24 MiB 的压缩页 LRU，延迟 90 ms 预取相邻页；显式点击优先，预取不替换当前可见页。继续使用 `2880px / 600 万像素 / 8 MiB` 单页清晰度门禁，未以降低分辨率换速度。
- **ZIP 可继续打开：** 压缩包目录行现在可点击，图片、PDF、音视频、文本、Markdown／JSON／CSV／XML／YAML／HTML、DOCX／XLSX／PPTX 与内层 ZIP 分别复用已有预览。返回会回到原压缩包层级；子文件预览不会把外层 ZIP 误当成子文件下载／分享。
- **安全边界：** 只读取用户点击的一个条目，不展开整棵目录，不执行宏、链接、脚本或嵌入对象。拒绝绝对路径、`..`、重复安全路径映射，限制最多 10000 个扫描条目、5 层内嵌 ZIP、单条 20 MiB 与 200 倍压缩比。OOXML 只有界读取必要 XML，禁用 DOCTYPE、外部实体、DTD 和外部 Schema。旧式二进制 `.doc/.xls/.ppt` 没有伪装成新版格式。
- **格式入口：** 文件选择与魔数校验新增现代 Office、ZIP、XML、YAML、HTML 及常见日志／配置／代码文本后缀；中文句号或全角句点加空格的历史文件名仍经统一后缀规则识别。预览延用应用现有白卡／中性画布、字体、颜色、圆角和操作栏，没有新造一套文档风格。
- **验证与交付边界：** Android 全量 JVM `894 tests / 0 failures / 0 errors / 3 skipped`，最终 ZIP 安全收紧后的定向回归与 Release 重建亦通过；`:app:lintDebug` 与 `:app:assembleRelease` 通过，未运行任何 `connected*AndroidTest`。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `4e9b106d3ee1330488da231d271d499d3eec13c2d4a4444fe240ad3724384b87`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未覆盖安装 OPPO，也未完成真机 PDF 连续翻页手感、各格式样本与深层 ZIP 继续打开的人工验收，不得把编译与自动回归写成真机视觉／手势闭环。

## 2026-08-29：搜索输入命中历史后自动打开并定位（代码／全量 JVM／Lint／Release／OPPO 保数据覆盖已验证）

- **输入联动：** 搜索界面底部输入关键词时，按当前列表范围读取本地搜索历史；优先匹配规范化后的完整相等项，其次匹配最近的前缀项，再匹配最近的包含项。存在命中时直接复用既有历史弹窗，并将对应历史词滚动到可见位置、用当前主题色短促突出；输入继续变化且不再命中时，自动打开的弹窗随即收起。
- **交互边界：** 自动弹窗不抢输入焦点、不收起键盘；手动点击历史按钮打开后仍由用户点外、关闭或返回收起，不会被后续无匹配输入强制关闭。点击历史词继续沿用既有填入和搜索流程。搜索防抖只刷新结果，不再把每个暂停输入片段写入历史；键盘提交和历史选择才记录搜索词，避免联想历史被中间态污染。
- **UI 统一：** 没有新增浮层样式，继续复用搜索页既有历史弹窗、主题颜色、圆角、字体缩放、点外关闭和键盘避让；最多显示六行并允许内部滚动。
- **自动验证：** 定向回归覆盖完整相等／空白归一、前缀、包含、自动弹窗定位及防抖不写历史；Android 全量 JVM `888 tests / 0 failures / 0 errors / 3 skipped`，`:app:lintDebug` 与 `:app:assembleRelease` 通过。未运行任何 `connected*AndroidTest`。
- **Release 与 OPPO：** code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `8fc490c37a7c85a9bba0c1c71d5d48627cf2ed0387bfa13daa6bf6581bc8a77d`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。已对 OPPO `3B157F009E800000` 执行同包名、同签名 `pm install -r --user 0`；安装后设备回读 APK 与 Release 哈希一致，`firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104 / deDataInode=1433378` 均未变化。应用进程存活且 `com.nanzhufeng.ai/.NanfengAiActivity` 位于前台，当前进程日志未发现 `FATAL EXCEPTION`／`AndroidRuntime`。
- **待人工确认：** 尚未在真机搜索页实际输入多组历史关键词，人工检查弹窗出现时机、滚动定位、主题高亮和输入法连续输入手感；覆盖安装与自动证据不能冒充这部分视觉／手势验收。

## 2026-08-29：PDF 正常阅读、异常句点文件名兼容与 ZIP 内容查看（代码／全量 JVM／Lint／Release 已验证）

- **PDF 阅读器：** 打开后固定本次已验证附件引用，翻页不再依赖可能刷新的会话投影；异步页渲染有代次保护和加载态。底部上一页／下一页与页面手势区分离，避让系统导航栏并保证层级；拟合比例下可左右滑页，支持双指缩放和拖动，但不引入图片专属的双击缩放。
- **PDF 清晰度：** `PdfRenderer` 的约 72 DPI 原始页不再被禁止放大；改为在 `2880px` 边长、600 万像素和 8 MiB 页缓存上限内高分辨率光栅化，切页时回收旧 Bitmap。A4 `595×842` 样例已证明会放大渲染且不超过像素／边长门禁。
- **异常文件名：** 历史导入附件即使 MIME 为 `application/octet-stream`，也可结合文件名安全识别 ASCII 句点、中文句号 `。`、全角句点 `．` 以及句点后空格；只允许 Markdown／TXT／JSON／CSV 进入有界 UTF-8 文本查看，其他二进制文件仍失败关闭。截图中 `粘贴的 markdown (1)。 md` 形式已有回归覆盖。
- **ZIP 内容查看：** 搜索文件卡不再显示“本地缩略图不可用”，点击进入与现有文件查看器统一的本地内容清单，显示安全路径、文件类型、真实大小和总项数，保留下载／分享／关闭。共享导入 ZIP 中的内层 ZIP 会先验证外层引用和内层哈希再列目录；最多显示 2000 项，不自动解压、不执行条目。
- **自动验证与交付边界：** Android 全量 JVM `886 tests / 0 failures / 0 errors / 3 skipped`，`:app:lintDebug` 与 `:app:assembleRelease` 通过；未运行任何 `connected*AndroidTest`。code 66 非调试 Release 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `6f4e25bc30290a144c32c589b81e883c624f237f1a21d7ccb4b338ca4ad8ba10`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。当前 `adb devices -l` 无设备，因此未对 OPPO 覆盖安装，也未做真机页面点击、清晰度和压缩包视觉回读；不得将构建写成真机验收。

## 2026-08-29：Google 账号与手动选择对话同步（代码／全量 JVM／Release／OPPO 保数据覆盖已验证）

- **直接同步：** 普通与工作对话的长按菜单新增“同步到南枫云”。已登录且恢复保护就绪时，点击后直接同步当前一条对话，没有二次确认弹窗；只有云端提交与回读成功才进入手动选中记录。
- **定期边界：** 账号页手动开启的 12 小时定期同步只遍历当前账号已手动同步成功的对话，不扫描其他对话，不扩大到记忆、项目、知识、设置或其他本机数据。退出登录取消工作但保留本机对话和选中记录。
- **安全与 UI：** 只上传一条对话的文本消息树；附件、工具结果和未完成草稿整体拒绝，不做部分同步。恢复码明文不持久化，token 与派生包装材料由 Android Keystore 保护；未知云端版本冲突停止。账号页复用现有白卡、主题色、字体、圆角、图标和共享开关。
- **验证与边界：** Android 全量 JVM `876 tests / 0 failures / 0 errors / 3 skipped`、`lintDebug`、`assembleDebug` 与 `assembleRelease` 通过。code 66 非调试 Release APK 为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `349ee3ae9edb7d3a1be1ea372dd6482215167f605492a5dbd7be928654f3ed23`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。已对 OPPO `3B157F009E800000` 执行同签名 `install -r`；安装前后 `ceDataInode=1459104 / deDataInode=1433378` 不变，首次安装时间不变，设备内 APK 与 Release 字节哈希一致，Activity 处于前台且进程存活，未发现相关崩溃。当前环境没有本应用专用 Supabase URL、publishable key 和 Google Web Client ID，因此未做真实 OAuth、密文上传／回读、跨设备发现与恢复；未运行任何 `connected*AndroidTest`。详见 [P7-F 手动选择对话同步合同](P7F_SELECTED_CONVERSATION_SYNC_CONTRACT.md)。

## 2026-08-29：左侧栏底部设置图标改为正文色（代码／定向 JVM／Debug 编译已验证）

- **界面：** 普通、工作和临时左侧栏底部的设置齿轮统一从强调色派生色改为正文色；浅色皮肤呈现正常黑色，深色皮肤自动呈现可读白色，不写死黑色。
- **不变：** 圆形白色／深色前景面、冻结阴影、按压与水波纹轮廓、大小、位置、命中区和设置路由全部不变；删除不再使用的专用强调色令牌，避免后续又被主题色切换覆盖。
- **验证：** `P6DConversationRowAccessibilityContractsTest` `88 tests / 0 failures / 0 errors / 0 skipped`；Debug Kotlin 编译通过，唯一警告仍为既有 `LocalClipboardManager` 弃用。Gradle 仅对本轮进程使用 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置。尚未做隔离模拟器视觉验收；未运行任何 `connected*AndroidTest`，未安装或操作 OPPO。

## 2026-08-29：设置数据类分组与入口更名（代码／定向 JVM／Debug 编译已验证）

- **名称：** 设置首页分组“应用与数据”改为“数据管理”，原“存储”入口及详情标题改为“导入与导出”。为避免分组和子入口同名，原只承担删除、清理、失败重试和风险确认的“数据管理”子入口及详情标题同步改为“清理与删除”。
- **边界：** 图标、分组顺序、组合卡几何、路由枚举、导入导出、备份恢复、本机概览、删除与清理数据链全部不变，只收口可见名称。
- **验证：** `P6DConversationRowAccessibilityContractsTest` 88 项、`SettingsUiSimplificationContractsTest` 14 项与 `SettingsCategoryCardContractsTest` 1 项，共 `103 tests / 0 failures / 0 errors / 0 skipped`；Debug Kotlin 编译通过。Gradle 仅对本轮进程使用 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置。尚未做隔离模拟器视觉验收；未运行任何 `connected*AndroidTest`，未安装或操作 OPPO。

## 2026-08-29：搜索附件时间／大小双向排序（代码／定向 JVM／Debug 编译已验证）

- **默认不变：** “默认排序”继续使用现有按月分组及组内时间顺序，不改旧结果结构。“全部／图片／视频／音频／文件”增加一个紧凑的排序胶囊，“正文”不显示。
- **双向全局排序：** 可切换“时间从新到旧／时间从旧到新／大小从大到小／大小从小到大”。显式排序跨月份全局生效并隐藏月份标题，分别消费真实 `timestampEpochMs` 和附件 `byteCount`；只是 UI 投影，不修改搜索索引、附件引用或删除链。
- **位置与统一性：** 切换排序后返回顶部并清除旧锚点；预览返回、搜索快速定位及主界面反向定位的索引都按当前排序计算。胶囊、前景下拉面、字号、圆角和选中主题色全部复用搜索现有 UI 令牌，没有新建彩色卡或独立视觉风格。
- **自动验证：** `ConversationSearchSurfaceContractsTest` 5 项、`ConversationSearchAttachmentPreviewUiContractsTest` 1 项、`MainAttachmentSearchLocateUiContractsTest` 3 项、`ConversationPopupLayerContractsTest` 1 项与 `P6DConversationRowAccessibilityContractsTest` 88 项，共 `98 tests / 0 failures / 0 errors / 0 skipped`；Debug Kotlin 编译通过。Gradle 仅对本轮进程使用 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置。
- **边界：** 尚未在隔离模拟器实际点击下拉菜单、切换四种顺序或验收大字与深色皮肤，不得称为视觉／手势验收完成。未运行任何 `connected*AndroidTest`，未安装或操作 OPPO。

## 2026-08-29：搜索文件卡底部大小与时间同排（代码／定向 JVM／Debug 编译已验证）

- **界面：** 搜索图片／视频／音频／文件网格卡及“全部”附件行的底部事实栏统一为真实占用大小靠左、所属消息时间靠右，时间格式为 `M月d日 HH:mm`。网格卡维持既有 `204dp` 高度、白卡、圆角和字体层级，命中摘要不再占用底栏；列表形态的命中摘要仍保留在事实栏上方。
- **数据：** 大小继续读取持久附件引用的 `byteCount`，时间读取同一搜索命中的 `timestampEpochMs`，不改搜索排序、定位三元组、预览、长按删除或共享文件引用链。
- **自动验证：** `ConversationSearchAttachmentPreviewUiContractsTest` 1 项、`ConversationSearchSurfaceContractsTest` 3 项、`MainAttachmentSearchLocateUiContractsTest` 3 项、`ConversationPopupLayerContractsTest` 1 项与 `P6DConversationRowAccessibilityContractsTest` 88 项，共 `96 tests / 0 failures / 0 errors / 0 skipped`；Debug Kotlin 编译与定向 `git diff --check` 通过，唯一编译警告仍为既有 `LocalClipboardManager` 弃用。Gradle 仅对本轮进程使用 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置。
- **边界：** 尚未在隔离模拟器或目标真机实际检查两列窄卡、长文件名和大字体下的底栏对齐，不得称为视觉验收完成。未运行任何 `connected*AndroidTest`，未安装或操作 OPPO。

## 2026-08-29：主界面附件弹窗反向精确定位搜索（代码／定向 JVM／Debug 编译已验证）

- **范围：** 本次只改主界面对话消息中的附件长按弹窗；搜索结果原有“快速定位／删除”长按弹窗未改。主弹窗新增“搜索定位”，与“下载／分享”组成三个同规格胶囊；文件名、一次类型／真实大小和发送时间保留，重复类型行删除，弹窗复用瞬时菜单字号、亮白前景、主题色和既有点外关闭。
- **精确目标：** 跳转携带 `ConversationId + MessageNodeId + AttachmentId`，分类与搜索索引共享同一 MIME owner。搜索页打开对应图片／视频／音频／文件分组后，按三元组滚到该条确切引用并复用现有短暂主题色渐变；共享文件被多条消息引用时不再只按附件 ID 命中第一条。草稿尚未进入搜索索引，因此不显示无效的“搜索定位”。
- **自动验证：** `MainAttachmentSearchLocateUiContractsTest` 3 项、`ConversationSearchSurfaceContractsTest` 3 项、`ConversationSearchAttachmentPreviewUiContractsTest` 1 项、`ConversationPopupLayerContractsTest` 1 项与 `P6DConversationRowAccessibilityContractsTest` 88 项，共 `96 tests / 0 failures / 0 errors / 0 skipped`；`:app:compileDebugKotlin` 通过，唯一编译警告仍为既有 `LocalClipboardManager` 弃用。Gradle 仅对本轮进程使用 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置。
- **边界：** 尚未在隔离模拟器或目标真机实际长按、跳转和回读布局，不得称为视觉／手势验收完成。未运行任何 `connected*AndroidTest`，未安装或操作 OPPO。

## 2026-08-29：返回搜索保留原文件位置（代码／定向 JVM／Debug 编译已验证）

- **位置 owner：** 全部、正文各保留独立 `LazyListState`，图片、视频、音频、文件各保留独立 `LazyGridState`；六份状态提升到搜索全屏页显隐之上，从预览或快速定位返回时复用原索引与像素偏移，不重建为顶部。
- **文件锚点：** 点击附件时记录稳定 `AttachmentId`。返回后先检查原文件是否仍在可见区；只有结果变化导致它不可见时，才按包含月份标题的真实 Lazy item 索引滚回该文件。更换搜索词、历史词或分类会清除旧锚点，避免误跳。
- **自动验证：** `ConversationSearchSurfaceContractsTest` 3 项、`ConversationSearchAttachmentPreviewUiContractsTest` 1 项与 `P6DConversationRowAccessibilityContractsTest` 88 项，共 `92 tests / 0 failures / 0 errors / 0 skipped`；新增纯 Kotlin 索引样例真实覆盖正文标题、附件总数与跨月标题的 Lazy item 累计。`:app:compileDebugKotlin` 与定向 `git diff --check` 通过。Android Studio JBR 21 仅对本轮 Gradle 进程使用 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置；唯一编译警告仍是既有 `LocalClipboardManager` 弃用。
- **边界：** 尚未在隔离模拟器中实际滚动到中段并往返搜索，不得称为视觉／手势验收完成。未运行任何 `connected*AndroidTest`，未安装或操作 OPPO。

## 2026-08-29：存储概览钻取、搜索分组直达与记忆总入口（代码／定向 JVM／Debug 编译已验证）

- **信息架构：** “本机数据”概览从“数据管理”移入“存储”，与导入、备份处于同一个真实存储页；“数据管理”只保留 ZIP 原始包整理、清理范围、确认删除与失败重试，不再重复显示概览。
- **真实钻取：** 对话／消息分别直达现有搜索“全部／正文”，图片／视频／音频／文件直达同名搜索分组，范围设为所有未删除本地对话；搜索页、卡片、预览、快速定位与引用安全删除链全部复用现有 owner。知识和项目直达原页面；记忆进入新的轻量总入口，再分流到“记忆摘要”和“个性化与资料库搜索”。草稿、其他导入资料与导入概况没有准确明细页，仍为无箭头的静态数据。
- **界面统一：** 可点与静态数据行共用设置的中性浅色面、`14dp` 圆角、最小 `48dp` 行高及当前字体缩放；只有真实入口显示 `18dp` 右箭头。删除“按类别统计数量与实际内容大小”的重复小字，未新建彩色卡、专用搜索或新弹窗风格。
- **自动验证：** 新增 `PrivacyStorageNavigationContractsTest` 4 项，并回归设置简化、设置滚动／卡片、搜索页／附件预览和会话可访问性，共 `111 tests / 0 failures / 0 errors / 0 skipped`；`:app:compileDebugKotlin` 与定向 `git diff --check` 通过。使用 Android Studio JBR 21，仅对本轮 Gradle 进程设置 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置；唯一编译警告仍是既有 `LocalClipboardManager` 弃用。
- **边界：** 未执行目标视口视觉回读、完整 JVM、Lint、Release、隔离模拟器或设备验收；没有运行任何 `connected*AndroidTest`，没有安装或操作 OPPO，不得称为真机视觉或完整业务闭环。

## 2026-08-29：数据概览精简与设置入口更名（代码／定向 JVM／Debug 编译已验证）

- **概览精简：** “本机数据”卡删除“本地记录”整组、“设置与密钥”及 API Key 状态、底部大小／ZIP 说明；总大小同步只汇总仍可见的对话内容、附件与导入资料，避免隐藏分项后总数无法对账。后台模型调用、本地检查、密钥与 ZIP 清理 owner 均保留，未删除任何真实数据。
- **设置名称：** 设置首页及详情页标题将“数据与存储”统一改为“存储”，“隐私与安全”统一改为“数据管理”；图标、入口顺序、页面内容、路由及数据链不变。
- **自动验证：** `SettingsUiSimplificationContractsTest` 14 项、`AndroidUserEntryAuditContractsTest` 7 项与 `P6DConversationRowAccessibilityContractsTest` 88 项共 109 项全部通过（0 failure／0 error／0 skipped），覆盖冗余概览隐藏、新旧名称互斥、首页与详情标题统一；`compileDebugKotlin` 与 `git diff --check` 通过。使用 Android Studio JBR 21，并仅对本轮 Gradle 进程设置 `-XX:TieredStopAtLevel=1`，未写入项目配置。
- **边界：** 尚未执行目标视口视觉回读、完整 JVM、Lint、Release 或设备验收；未运行任何 `connected*AndroidTest`，未安装或操作 OPPO，不得称为真机视觉闭环。

## 2026-08-29：左侧计划入口改为时钟图标“定时任务”（代码／定向 JVM／Debug 编译已验证）

- **入口：** 删除“＋ 已计划”的中性灰胶囊，改为抽屉画布上的无常态背景导航行；左侧使用 Material Rounded `Schedule` 圆形时钟图标，名称改为“定时任务”，整体左对齐。
- **统一性：** 入口使用正文色、`24dp` 图标、`16sp / 20sp / SemiBold` 文字和 `48dp` 命中高度；搜索胶囊、抽屉背景、上下渐变、入口顺序、打开计划页的 owner 及所有计划任务数据链不变。
- **自动验证：** `ConversationDrawerEdgeFadeContractsTest` 3 项、`AndroidUserEntryAuditContractsTest` 7 项与 `P6DConversationRowAccessibilityContractsTest` 88 项共 98 项全部通过（0 failure／0 error／0 skipped），覆盖旧入口移除、时钟图标／新名称、透明常态表面、左对齐与既有抽屉交互；`compileDebugKotlin` 与 `git diff --check` 通过。使用 Android Studio JBR 21，并仅对本轮 Gradle 进程设置 `-XX:TieredStopAtLevel=1`，未写入项目配置；唯一编译警告仍是既有 `LocalClipboardManager` 弃用。
- **边界：** 尚未取得实现后的同视口截图，不能把代码和构建称为与参考图逐像素匹配；完整 JVM、Lint、Release 与设备验收亦未执行。未运行任何 `connected*AndroidTest`，未安装或操作 OPPO。

## 2026-08-29：主界面底部模型名统一加粗（代码／定向 JVM／Debug 编译已验证）

- **界面：** 普通会话与临时聊天共用的 `ComposerModelEntry` 将短模型名字重由 `SemiBold` 统一为 `Bold`；`13sp` 字号、圆润字体、短名称、胶囊、宽度、位置、主题色与 `48dp` 命中面不变。
- **范围：** 仅调整主界面底部 Composer 模型名，不改变模型选择面、Assistant 回复页脚、模型路由、当前会话 override 或全局默认设置。
- **自动验证：** `P6DConversationRowAccessibilityContractsTest` 88 项全部通过（0 failure／0 error／0 skipped），覆盖共享模型入口的 `Bold` 字重及既有 Composer 几何；`compileDebugKotlin` 与 `git diff --check` 通过。使用 Android Studio JBR 21，并仅对本轮 Gradle 进程设置 `-XX:TieredStopAtLevel=1`，未写入项目配置；唯一编译警告仍是既有 `LocalClipboardManager` 弃用。
- **边界：** 尚未执行目标视口视觉回读、完整 JVM、Lint、Release 或设备验收；未运行任何 `connected*AndroidTest`，未安装或操作 OPPO，不得称为真机视觉闭环。

## 2026-08-29：左侧会话标题允许阿拉伯数字并统一选中主题色（代码／定向 JVM／Debug 编译已验证）

- **标题格式：** 自动标题生成提示与本机确定性解析门同时允许 `0–9` 阿拉伯数字，可保留 `GPT5模型选择`、`2026科技趋势` 等原文明确对象；长度、空泛标题拒绝、标点／Markdown／emoji 等安全格式限制不变。
- **选中态：** 选中会话保留既有主题色浅底，仅标题文字改为当前主题色；日期、置顶图标、未读点、行高、字号、圆角和交互不变，不新增局部配色或组件。
- **自动验证：** `ConversationTitleFormatContractsTest` 4 项、`ConfiguredConversationTitleRefinerContractsTest` 4 项与 `P6DConversationRowAccessibilityContractsTest` 88 项共 96 项全部通过（0 failure／0 error／0 skipped），覆盖数字解析、标题提示词和选中标题主题色；`compileDebugKotlin` 与 `git diff --check` 通过。使用 Android Studio JBR 21，并仅对本轮 Gradle 进程设置 `-XX:TieredStopAtLevel=1`，未写入项目配置；唯一编译警告仍是既有 `LocalClipboardManager` 弃用。
- **边界：** 尚未执行目标视口视觉回读、完整 JVM、Lint、Release 或设备验收；未运行任何 `connected*AndroidTest`，未安装或操作 OPPO，不得称为真机视觉闭环。

## 2026-08-29：Composer 输入文字关联全局字体大小（代码／定向 JVM／Debug 编译已验证）

- **根因与修正：** App 的 Compose Typography 已消费 `LocalAppTextScale`，但 Composer 使用原生 Android `EditText` 并写死 `16sp`，因此未随设置变化。当前原生输入框显式读取同一全局倍率，标准档保持 `16sp`，小／大档分别为 `12.8sp / 19.84sp`；factory 与 update 均应用，切换设置后现有输入框即时更新。
- **范围：** 已输入文字与“回复 南枫AI”占位文字共用原生 textSize 和自然行高；Composer 外层、阴影、单行／展开高度、附件／模型／发送控件及触控面不变。
- **自动验证：** `AppearanceFontSizeContractsTest` 5 项与 `P6DConversationRowAccessibilityContractsTest` 88 项共 93 项全部通过（0 failure／0 error／0 skipped），`compileDebugKotlin` 与 `git diff --check` 通过。使用 Android Studio JBR 21 并仅对本次 Gradle 进程设置 `-XX:TieredStopAtLevel=1`，未写入项目配置；仍只有既有 `LocalClipboardManager` 弃用警告。
- **边界：** 尚未完成三档字号的目标视口视觉回读、完整 JVM、Lint、Release 或设备验收；未运行任何 `connected*AndroidTest`，未安装或操作 OPPO，不得称为真机视觉闭环。

## 2026-08-29：Assistant 多图从左到右升序（代码／定向 JVM／Debug 编译已验证）

- **排序 owner：** 当前消息多图块的高序号在前，新增唯一 `ascendingAssistantImageOrder` 将其转换为低序号在前。会话缩略图、默认大图、全屏预览左右滑、当前索引和批量下载均消费这份升序序列，不用反向 `Row` 做表面修补。
- **视觉：** 缩略图保持既有 `48dp`、间距、圆角、主题色选中边框和横向滚动，只改变从左到右的内容次序；默认选中升序第一张。
- **自动验证：** `AssistantGeneratedImageGroupUiContractsTest`、`P6F2BImagePreviewUiContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 共 `96 tests / 0 failures / 0 errors / 0 skipped`，覆盖 `7…1 → 1…7`、会话图片组与全屏预览共享排序、用户附件保留原顺序，以及既有缩放／会话交互；`:app:compileDebugKotlin` 与 `git diff --check` 通过。仅使用 Android Studio JBR 21 和本轮进程级 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置；唯一编译警告仍是既有 `LocalClipboardManager` 弃用。
- **边界：** 尚未执行目标数据视觉回读、完整 JVM、Lint、Release、隔离模拟器或设备覆盖；未触碰 OPPO，也未运行任何 `connected*AndroidTest`，不得称为真实设备顺序已确认。

## 2026-08-29：图片原图预览区域手势与触点双击缩放（代码／定向 JVM／Debug 编译已验证）

- **交互：** 保留图片原图的双指缩放与单指位移，并把这两类手势从整屏黑色画布收回实际图片节点。图片区域双击以触点对应的原图位置为中心放大到 `2.5×`，任何已放大状态再次双击还原初始比例和位置；单击仍控制既有顶部操作显隐。
- **边界：** 黑色留白、关闭／下载／分享控件和 PDF、视频、音频、文本等其他文件预览不响应图片缩放或双击；图片切换、原图解码、下载与分享链不改。
- **自动验证：** `P6F2BImagePreviewUiContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 共 `93 tests / 0 failures / 0 errors / 0 skipped`，覆盖触点中心保持、`2.5×` 放大、再次双击复位、手势节点位于实际 `Image` 之后及既有会话交互；`:app:compileDebugKotlin` 与 `git diff --check` 通过。仅使用 Android Studio JBR 21 和本轮进程级 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置；唯一编译警告仍是既有 `LocalClipboardManager` 弃用。
- **边界：** 尚未执行隔离模拟器手势、目标视口视觉回读、完整 JVM、Lint、Release 或真机手感；未触碰 OPPO，也未运行任何 `connected*AndroidTest`，不得称为真实设备交互完成。

## 2026-08-29：上下文记录与运行诊断时间统一到右下角（代码／定向 JVM／Debug 编译已验证）

- **界面：** “上下文记录”与“运行诊断”的每张记录卡不再把时间插在标题下方；两页共用同一辅助小字组件，把真实发生时间作为卡片最后一项右对齐。运行诊断展开技术详情时，时间仍处于整张卡片右下角。
- **统一性：** 保留现有卡片、内边距、信息顺序、字体与辅助色，只统一时间槽位，不增加新图标、背景或说明。
- **自动验证：** `ModelSettingsUiContractsTest` 为 `3 tests / 0 failures / 0 errors / 0 skipped`，覆盖两个页面消费共享时间组件、时间位于记录内容末尾及 `Alignment.End`；`:app:compileDebugKotlin` 与 `git diff --check` 通过。本轮显式使用 Android Studio JBR 21，并仅对 Gradle 进程设置 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置。
- **边界：** 尚未执行目标视口视觉回读、完整 JVM、Lint、Release 或设备覆盖；未触碰 OPPO，也未运行任何 `connected*AndroidTest`，不得称为已完成视觉或交付验收。

## 2026-08-29：删除“关于”页重复数据与隐私提示（代码／定向 JVM／Debug 编译已验证）

- **界面：** 删除“关于”大卡底部“数据与隐私”标题、说明及其前置分隔条；大卡只保留品牌说明与真实版本／构建号。“隐私与安全”及“数据与存储”的正式入口、页面和数据能力不变。
- **统一性：** 沿用现有大卡、章节间距、字体、颜色与唯一分隔条，不增加替代文案或新组件；设置当前合同同步为两模块结构。
- **自动验证：** `AndroidUserEntryAuditContractsTest` 与 `SettingsUiSimplificationContractsTest` 共 `21 tests / 0 failures / 0 errors / 0 skipped`；`:app:compileDebugKotlin` 与 `git diff --check` 通过。终端首次因未配置 Java 未启动 Gradle，随后显式使用 Android Studio JBR 21，并仅对本轮进程设置 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，未写入项目配置。
- **边界：** 尚未执行目标视口视觉回读、完整 JVM、Lint、Release 或设备覆盖；未触碰 OPPO，也未运行任何 `connected*AndroidTest`，不得称为已完成视觉或交付验收。

## 2026-08-29：搜索附件真实大小、统一弹窗与引用安全删除（代码／完整 JVM／Lint／Release／隔离模拟器／OPPO 覆盖与 ZIP 回读已验证）

- **入口盘点与显示：** Android 全屏搜索共有三种附件消费者：“全部”页文件行、图片／视频／文件分类的网格卡、音频分类文件行。三者现在统一直接格式化 `ConversationAttachmentReference.byteCount`，显示真实持久大小；不读取缩略图、文本预览或媒体缓冲区来猜大小。普通点击仍打开本地预览；长按复用原文件操作面，在同一弹窗内提供“快速定位／删除”。两枚图标统一为 `20dp`，文字使用既有瞬时菜单缩放，原卡片材质、间距、行高与触控面不变。
- **统一确认与文案：** 删除确认沿用标准居中弹窗，标题“删除”，正文只保留“仅移除这条消息中的附件。”，按钮为“取消／确认删除”；主按钮跟随当前皮肤主题色并使用共享 `P5AInteractiveShape` 胶囊。同步盘点现有弹窗源码，删除或压缩了回收站、记忆、项目归属、Knowledge 导出／关系、ZIP 清理、计划任务、导入任务、双路径与本地审计中的重复解释和内部实现术语；新增 `DialogCopyBrevityContractsTest` 防止旧长文案回流。真实状态、数量、费用和不可恢复后果仍保留。
- **消息保留与引用 owner：** `ConversationMessageAttachmentRepository → DeletePersistedConversationAttachmentUseCase → PrivateAttachmentRepository` 是唯一删除链。Room 事务重验当前 conversation/message/attachment/hash，只移除目标 `ContentBlock.Attachment`、对应 ZIP occurrence receipt 并重建既有搜索索引；消息节点和会话都不删除。仅附件消息保留原节点并显示“附件已删除”。过期搜索命中、消息／hash 已变化时整体拒绝。
- **共享文件清理：** 私有目录按普通草稿、普通消息、临时聊天、ZIP occurrence receipt 与 `PENDING/UPLOADING/FAILED/UNKNOWN` 可重试上传统计引用；任一引用仍在就只移除当前消息引用，最后一个引用消失后才删除受管文件与目录行。物理删除失败不会伪装成功，目录行保持以便后续维护；ZIP archive-backed 附件只清该受管资产，原始 ZIP 仍由独立“删除 ZIP 原始包”链负责。
- **隔离模拟器验收：** 新增独立 `searchAttachmentAcceptance` build type 与后缀包 `com.nanzhufeng.ai.searchattachmentacceptance`，只通过正式 store/repository owner 写入固定非敏感 fixture。全新临时 AVD `emulator-5588` 为 `1140×2616 / 442dpi / font scale 1.0`；“全部、图片、视频、音频、文件”均回读真实 `12.1 KB / 229.1 KB / 33.8 KB / 1.2 MB / 543 B`。可见取消不改数据；两条消息共享的 `12,345-byte PNG` 第一次删除后附件数 `6→5` 且实体仍在，第二次删除后 `5→4` 且实体消失，其他四个文件仍在。先前同一 fixture 已验证两个“附件已删除”消息节点、所属会话及冷启动回读均保留。最终截图同时确认“快速定位／删除”、两枚 `20dp` 图标、紧凑字体、主题色胶囊、标题“删除”和按钮“确认删除”。
- **自动回归：** `SearchAttachmentDeletionRoomContractsTest` 覆盖两条消息共享同一资产、精确清 ZIP 回执、消息／会话保留、最后引用才清物理文件、临时聊天与可重试上传保留资产、删除失败保留目录；UI 文案与统一性另由新增合同覆盖。最终完整 JVM 为 `856 tests / 0 failures / 0 errors / 3 skipped`；3 个 skip 仍是需要用户真实 ChatGPT ZIP 环境变量的 opt-in 验收，不冒充执行。Android Studio JBR 21 默认 C2 在测试进程两次原生崩溃于 `Node::uncast`，随后仅对本轮测试进程设置 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`，完整套件真实执行并全绿，未写入项目配置。
- **Lint／Release：** `lintDebug --rerun-tasks` 成功，`0 Error / 90 Warning / 15 Hint`；`:app:assembleRelease --rerun-tasks` 成功。产物 `app/build/outputs/apk/release/南枫AI.apk` 为 `0.3.0-p10j (66)`、25,906,295 bytes、4 个 DEX，APK SHA-256 `7134c73ccad659846626611d3f42053cd8973f587e38c57e8c602a2034483e4c`；v2/v3 正式签名通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **OPPO 保数据覆盖：** 用户后续明确授权后，唯一连接设备 `3B157F009E800000 / PKH120` 由旧正式包 SHA-256 `9e8d0a6a98c209864d0871c86f472698134df59c5c491827bb2ed9cebf802e7a` 同签名覆盖为当前 `7134c73ccad659846626611d3f42053cd8973f587e38c57e8c602a2034483e4c`。覆盖前后版本均为 `0.3.0-p10j (66)`，证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，`DEBUGGABLE` 未出现；使用 `/data/local/tmp` + `pm install -r --user 0`，未卸载、未清数据。`ceDataInode=1459104`、`deDataInode=1433378`、首次安装时间 `2026-08-20 15:15:31` 均保持，设备回读 `base.apk` 与本地产物逐字节一致。两次正式冷启动为 `255ms / 233ms`，Activity 正常且进程存活。
- **真实 ZIP 内置完成回读：** OPPO 设置 → 数据与存储显示 `1 个导入批次 · 784 个对话已导入 · 1615 个附件已恢复`；详情页显示 `CHATGPT ZIP · 批次 1 / 已完成 / 784 个对话已导入 / 1615 个附件已恢复到原对话 / 附件恢复已完成`。强制停止并冷启动后重新进入同一路径，以上完成状态再次原样回读，证明已持久化。详情另如实显示 `1` 个官方引用对应源文件缺失、`60` 个文件缺少可确认对话归属；这是导出包／归属证据边界，不是本地恢复未完成，不得猜配或伪装为成功。
- **边界：** 本轮没有运行任何 `connected*AndroidTest` 或仪器测试，没有 Debug／测试 APK 自动部署，没有读取聊天正文、数据库或附件内容；设备 UI 回读只限定设置与 ZIP 导入状态。没有真实 Provider 调用。

## 2026-08-28：“最近”批量编辑图标与大数据切换流畅度（代码／定向 JVM／Debug 编译已验证，完整回归待续）

- **视觉：** “最近”右侧批量编辑铅笔从 `19dp` 精确缩小 30% 为 `13.3dp`；`36dp` 点击面、分组位置、普通 `36dp` 与批量 `44dp` 行高、底部操作与功能路由全部不变。
- **性能根因与修正：** 普通抽屉原为 `Column + verticalScroll`，打开抽屉即组合全部历史会话；切换批量编辑时又会使每条会话改变行高和选择控件，数百条数据下产生明显延迟。现改为带稳定 key/content type 的 `LazyColumn`，只组合可见行；置顶、最近、顶部预留、底部可滚动 inset 与悬浮按钮布局保持。批量候选 ID 集合也使用 `remember` 缓存，全选不再每次重建。
- **当前验证：** `P6DConversationRowAccessibilityContractsTest` 定向 `88 tests / 0 failures`；`:app:compileDebugKotlin` 通过，仅有既有 `LocalClipboardManager` 弃用警告；`git diff --check` 通过。上下文闸门为 `HANDOFF (94.6%)`，未继续完整 JVM、Lint、Release 或真机手感，不得冒充完整闭环。

## 2026-08-28：隐私数据分类统计与 ZIP 原始包安全清理（代码／定向 JVM 已验证，完整回归与真机待完成）

- **统计根因与修正：** 旧隐私概览只扫描 `attachments/v1`，因此 ZIP 内已恢复到普通对话的附件没有进入“附件与导入资料”，截图只显示约 `24.9 MB`。当前统计改读 `private_attachment_assets` 的统一目录表，按图片、视频、音频、文档与其他文件汇总逻辑数量和字节；仍处于 ZIP 整理阶段的已归属附件也正常计入。对话、消息、草稿、记忆、知识、项目、模型调用与本地检查记录同时以中文分组显示数量和内容字节。
- **ZIP 存储决策：** ZIP 映射只保留为导入阶段的官方归属识别，不再作为用户主动清理后的长期字节后端。概览不展示、也不计入原始 ZIP 大小；导入概况单独显示已导入对话和已导入附件，避免与物理原包混算。
- **安全清理链：** 新增“删除 ZIP 原始包”。执行时在 `Dispatchers.IO` 上以 `64 KiB` 缓冲逐个流式复制已归属附件，校验 entry、大小和 SHA-256，以哈希命名写入 `attachments/v1`，随后按稳定附件 ID 原子切换目录表；全部引用都变为正常受管附件后，才将原包原子移入隔离区并删除。任务／附件恢复未完成、校验失败或状态变化时原包保留；中断后已完成的逐项目录更新可重放，未归属候选只在原包成功隔离后移除。
- **当前验证：** `compileDebugKotlin` 通过；`ImportedZipPackageCleanupContractsTest`、`P5CTaskDeletionRoomContractsTest`、`SettingsUiSimplificationContractsTest` 定向 JVM 通过，覆盖归档附件统计、原 ZIP 大小不进入 aggregate、受管附件回读、未归属候选清理及未完成恢复时拒绝删除。尚未完成完整 JVM、Lint、Release 构建或 OPPO 覆盖，不得把本节称为真机闭环。

## 2026-08-28：本地文本预览 Markdown 排版、状态栏与有效提示（代码／JVM／Lint／Release 已验证，真机待授权覆盖）

- **阅读投影：** 本地安全文本预览不再用等宽纯文本直接显示 Markdown 控制字符，而是复用会话的安全 Markdown parser，呈现标题、正文、引用、列表、强调、行内／块级代码和表格；链接只显示标签文字，不可执行。下载、分享和存储原文件不改写。
- **克制字号：** 预览正文为 `15sp / 23sp`，三级标题为 `19sp / 18sp / 17sp`，保留层次但不复制 Assistant 阅读页的 `26sp` 大标题跳变；现有文件名、下载、分享、关闭、正文卡和整体布局不移动。
- **系统栏与提示：** 浅色预览以纯白 Surface 延伸到状态栏并显示黑色系统图标；深色皮肤继续使用对应前景面和浅色图标。底部说明改为“长按可选择复制；下载或分享仍使用未修改的原文件。”，删除 UTF-8、HTML、脚本等低价值实现说明。
- **自动验证：** 聚焦 Markdown／文本预览／暗色 surface 契约 `23 tests / 0 failures / 0 errors / 0 skipped`；完整 JVM `851 / 0 / 0 / 3 skipped`；Debug Kotlin、`lintDebug`、`assembleRelease` 与 `git diff --check` 通过。Lint 报告为 0 Error、90 Warning、15 Hint。
- **当前 Release：** `app/build/outputs/apk/release/南枫AI.apk`，`0.3.0-p10j (66)`，25,889,921 bytes，4 个 DEX，v2/v3 正式签名通过；APK SHA-256 `3cd3a638f73a2b71f4ccc4068be1fca3960ca3bf6ac58a7f638c603648cb46e5`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **设备边界：** 本轮没有覆盖 OPPO、没有运行仪器测试。OPPO 当前仍安装上一节性能验收包 `9e8d0a6a98c209864d0871c86f472698134df59c5c491827bb2ed9cebf802e7a`；不得把它的冷启动／帧统计冒充为本次文本排版视觉验收。需要用户再次授权后才能同签名保数据覆盖并按截图入口复验。

## 2026-08-28：Android 会话流畅度优化与 OPPO 保数据覆盖（代码／JVM／Lint／Release／系统帧证据已验证）

- **性能实现：** Room 会话快照、列表记忆来源和导入来源已消除逐消息／逐会话 N+1；大目录按 900 条分批规避 SQLite 参数上限。文本／附件搜索改用当前分支数据库投影；`MessageTree`、ViewModel 派生状态和 Compose 可见区附件预览已收敛，媒体解码与文件选择读取移出主线程。功能、入口、布局和视觉合同不变。
- **自动验证：** 完整 JVM `850 tests / 0 failures / 0 errors / 3 skipped`；`lintDebug` 通过（0 Error、90 Warning、15 Hint）；`assembleRelease` 与 `git diff --check` 通过。性能契约证明 80 条消息回读只执行一次内容块批量查询，30 个会话列表只执行一次记忆来源批量查询，搜索不逐会话重建快照。
- **最终产物：** `app/build/outputs/apk/release/南枫AI.apk`，`0.3.0-p10j (66)`，25,889,916 bytes，4 个 DEX，v2/v3 正式签名通过；APK SHA-256 `9e8d0a6a98c209864d0871c86f472698134df59c5c491827bb2ed9cebf802e7a`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **OPPO 覆盖与数据保留：** OPPO PKH120 / Android 16 / API 36 使用 `/data/local/tmp` + `pm install -r --user 0` 完成同签名正式包覆盖；未卸载、未清数据、未安装 Debug／仪器包。覆盖前后 `ceDataInode=1459104`、`deDataInode=1433378` 和首次安装时间 `2026-08-20 15:15:31` 均保持不变；设备回读 APK 与本地产物逐字节一致。
- **运行证据：** 两次正式包冷启动 `229ms / 186ms`，Activity 前台且进程存活；前后台恢复 `19ms`。六次合成纵向滑动产生 291 帧，0 janky frame，50/90/95/99 分位 `5/5/5/6ms`，慢 UI 线程／位图上传／绘制命令均为 0。
- **未冒充边界：** 合成输入另记录 288 次 high-input-latency，不能替代真实手指触控；为保护私有聊天数据，本轮未抓 UI 文本／截图／数据库，未按内容识别特定长会话、搜索结果或附件。真实内容下的搜索与附件主观手感仍待用户确认。完整证据见 [Android 流畅度优化交付记录](ANDROID_PERFORMANCE_OPTIMIZATION_20260828.md)。

## 2026-08-28：P1 可续跑附件恢复稳定基线与项目复盘（代码／JVM／真实 ZIP／Desktop／协议已验证）

- **稳定基线：** `c1c9ae0 feat: make ZIP asset recovery resumable`，建立在 `6d68ba7` 的标准 JVM 基线上。P1 将 ZIP 附件恢复迁入持久化后台任务，Room Schema 56 保存内容无关状态、进度、失败类型和按会话 checkpoint，支持退出页面后续跑与显式重试；没有放宽模型精确路由、ZIP 归属、安全 MIME、私有复制、迁移或设备门禁。
- **Android 验证：** P1 标准全量记录为 `819 tests / 0 failures / 0 errors / 3 skipped`。3 个 skip 是需要用户旧／新 ChatGPT ZIP 的 opt-in 测试，不计为已执行；新包附件 Room 链另行运行到 `tests=1, skipped=0, failures=0, errors=0`，耗时 `257.597s`，仍不等于 OPPO 已恢复。
- **其他本机验证：** Desktop `npm test` 为 `93 passed`，`lint/typecheck/build` 通过；Tauri `cargo test --locked` 为 `97 passed`；v1/v2 exchange 与 sync 三组协议 golden 通过。本机没有 Go 工具链，附件网关未运行 `go test`，也没有生产部署证据。
- **排除与文档边界：** 用户已明确排除同仓库正在进行的 P2 及后续任务和全部未提交 WIP。本复盘只冻结 [完整开发档案](%E5%8D%97%E6%9E%ABAI%E5%AE%8C%E6%95%B4%E5%BC%80%E5%8F%91%E6%A1%A3%E6%A1%88.md) 与 [可迁移开发经验](%E5%8F%AF%E8%BF%81%E7%A7%BB%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C.md) 到 `c1c9ae0`、123 个提交；本次没有安装设备、没有运行仪器测试、没有调用真实 Provider 或远端服务。

## 2026-08-28：当前代码 checkpoint 与 ChatGPT ZIP 附件链收口（代码／真实 ZIP／Lint／Release 已验证，OPPO 待授权覆盖）

- **最后 1 个附件的根因已确认并修复：** 部分 ChatGPT 官方当前节点以不可渲染的结构记录收尾，文本 parser 最后保留的可渲染消息仍有后代。旧 owner 把该节点当成 `currentNodeId` 后触发 `IllegalArgumentException: 当前分支必须指向叶消息。`。现在优先保留官方路径下既有合法叶节点，否则确定性选择最新后代叶节点。同一官方 file ID 的多消息引用仍保留，附件字节／catalog 不重复。
- **真实包闭环：** checkpoint 提交后重跑 `P6KUserSelectedChatGptZipAttachmentChainAcceptanceTest` 读取 `ChatGPT_20260827.zip` 得到 `853/853`，XML 为 `tests=1, skipped=0, failures=0, errors=0`，耗时 `249.963s`。旧包→新包的真实 Room 合并仍为 `784` 个去重对话，XML 同样为 `skipped=0, failures=0`，耗时 `320.925s`；三项真实包验收合并命令耗时 `9m43s`。`822` 个缺少官方对话归属的候选仍不猜配。
- **诊断已收口：** 产品代码不再用 `getOrDefault(Outcome())` 静默伪装会话事务成功，结果单独统计 `failedConversationCount`；临时 `P6KAssetRecovery` Log、异常堆栈回调和 mapper 诊断构造参数已移除。v2 完成标记只在映射非空、全部官方 entry 已挂载且会话失败数为 `0` 时写入。
- **最终回归边界：** 完整 JVM 为 `815 tests / 60 failures / 3 skipped / 0 errors`，60 项分布于 16 个历史契约类，其中 40 项为 `P6DConversationRowAccessibilityContractsTest`。3 个 skip 都是未在全量命令传真实 ZIP 环境变量的 opt-in 验收，它们已按真实包分开运行并确认 `skipped=0`。不将当前 checkpoint 声称为全绿基线。
- **Lint／Release：** `lintDebug` 为 `0 errors, 84 warnings, 13 hints`，`assembleRelease` 通过。产物 [南枫AI.apk](../app/build/outputs/apk/release/南枫AI.apk) 为 `66 / 0.3.0-p10j`，SHA-256 `b0d1008fde0bbbddaaad57d925e09ca067dbbb64407d9783904955a999481929`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，最低 API 26。
- **设备边界：** 本次没有安装设备、没有运行任何仪器测试。OPPO 仍是 SHA-256 `d86b670e050da976aa07151928059b49a9e0936fd7422dbac5883ec683c7c390` 的旧诊断包，当时 UI 回读仍为 `0 个附件已恢复`。只有用户再次明确授权后，才能同签名保数据覆盖并做真实 UI 回读。
- **历史边界：** 本节的 checkpoint 测试为 `60 / 16`，入口同步阶段为 `59 / 15`；二者先被 `6d68ba7` 的 `815 / 0 / 3 / 0` 取代，再由顶部 P1 的 `819 / 0 / 3 / 0` 增量记录接续，只保留根因与回滚证据。旧 2026-08-27、08-26 总控段也不得使用其 `796 / 60`、旧 APK 或旧“当前”描述覆盖顶部最新交接。

## 2026-08-28：ChatGPT ZIP 附件原生链路恢复与 JSON 结果入口补齐（代码／JVM 已验证，真机待覆盖）

- **实包关系复查：** 2026-08-27 ChatGPT ZIP 的当前导出路径上共有 `855` 条 attachment 记录、`854` 个唯一 ID；`853` 个对应 entry 实际存在，其中 `851` 个有 `conversation_asset_file_names.json` 官方显示名。这与 2026-08-16 K7“实包没有可证明 message↔asset 关系”的旧文档冲突；以新包实际字段、映射测试和回读为准。其余候选仍无官方归属，不从文件名或时间猜配。
- **原生链路：** `P6KChatGptZipAssetMapper` 只读取官方源 ID；`RoomP6KZipMappedAssetLinkOwner` 在同一 Room 事务内恢复被文本 parser 跳过的“仅附件”用户节点，并写入普通 `ContentBlock.Attachment`。所以导入附件与自带附件共用对话卡、当前消息路径、图片／视频／音频／文件搜索分组、预览、定位、搜索返回、下载和分享。大文件保留在原 app-private ZIP，按需 hash 校验并流式交付，不再复制约 `3.7 GB` 附件本体。
- **旧批次自动升级：** 应用启动后的 ZIP 任务回读会对保留的旧官方 ZIP 执行一次映射；已成功的 entry 标记 `SOURCE_MAPPED`，未归属项保持未关联。现有 OPPO 数据必须在新正式包同签名覆盖、启动并完成一次回读后，才能宣称已从 `1675` 个原候选中实际恢复 `853` 个；本次尚未进行真机覆盖，不把 JVM 结果冒充设备结果。
- **导入结果 UI：** JSON 和 ZIP 仍是两张独立大卡，两张卡的“导入结果 / 查看详情”均改为始终可见；任务表为空时如实显示 `0 个导入批次`。先前交接中“均有入口”的结论与用户截图冲突，根因是 JSON 结果行被任务非空条件隐藏，现已删除该条件。ZIP 详情改为分别显示“已恢复附件”和“缺少官方归属”，不再把全部称为待处理媒体。
- **验证：** 本段的阶段性验收已被上方“当前代码 checkpoint”替代；最新真实 Room 结果为 `853/853`，最新 Release SHA-256 为 `b0d1008fde0bbbddaaad57d925e09ca067dbbb64407d9783904955a999481929`。未运行任何仪器测试，未安装 OPPO。

## 2026-08-28：两个 ChatGPT 累积 ZIP 去重与导入链（真实包 JVM 验收已通过）

- **OPPO 真实导入与可见性（同日历史快照）：** 用户在 OPPO 上以系统 DocumentsUI 选择 `ChatGPT_20260827.zip` 后，ZIP 批次实际完成：`784` 个对话、`12` 条无可显示正文、`1675` 个当时尚未映射的资产候选；来源对话按原 `updatedAt` 排序。数据与存储当时保持 JSON、ZIP 两张独立大卡，但后续用户截图证实 JSON 结果行会因任务表为空而消失；不得再引用本段旧“均有入口”结论，以上方最新交接和设置合同为准。隐私总览当时统计总量为 `4.59 GB`，其中原始 ZIP `4.57 GB`。搜索命中会显示“从 ChatGPT ZIP 导入”来源标签，打开会话也显示相同内容无关说明。当时正式同签名覆盖已回读 APK 哈希与首次安装时间；未卸载、清数据或重复导入。

- **实现：** ChatGPT ZIP archive／total 上限升为 `6 GiB`，仍保持 `256 MiB` 单 entry、路径、重复 entry 与压缩炸弹门禁。候选仅保留可安全呈现的文本 string 部件；多模态对象、thoughts／reasoning recap 不显示也不阻断整段对话，完全没有安全文本的对象如实标记 `EMPTY_CONTENT`。无文本结构节点会折叠到最近的文本祖先。
- **跨包语义：** 新增 source-message→本地 message provenance。旧包先导入后，新包内同 source conversation 且内容不变时复用现有会话；只新增消息时在原会话追加，既不改变旧节点也不产生重复会话。旧消息被改写／删除、provenance 缺失或树有歧义则安全 `CONFLICT_REIMPORT`，保留旧本地会话。最新累计批次接管 provenance，故删除旧批次不会删除已由新批次引用的会话。
- **真实包验收：** 两份由用户明确选择的 2026-07／08 ChatGPT ZIP 依次以旧→新顺序导入到隔离内存 Room：旧包 `517` 条中 `506` 条有效文本会话、`11` 条 `EMPTY_CONTENT`；新包 `796` 条中 `784` 条有效文本会话、`12` 条 `EMPTY_CONTENT`。最终为 `784` 个无重复本地会话；共享会话复用本地 ID，新增消息走 append-only 合并；左侧会话列表按 `updatedAt` 降序、搜索索引命中、两条设置导入任务回读均通过。真实包回归耗时约 `5 分 7 秒`，当前逐会话事务与索引重建是性能风险，尚未做进度／批量优化。
- **该 JVM 阶段的验证边界：** `P6KUserSelectedChatGptZipMergeAcceptanceTest`（真实 ZIP、无正文输出）、`P6KChatGptZipCommitRoomContractsTest`、`P6KThirdPartyZipInventoryContractsTest` 和 Debug Kotlin 编译通过。这条记录是真机导入之前的自动验证边界；同日后续 OPPO 导入事实见上一条历史快照，两者不再互相否定。附件自动恢复仍须以上方最新交接的新正式包覆盖验收为准。

## 2026-08-27：总控方案历史同步（已被 2026-08-28 当前总控门取代）

- **历史边界：** 该记录只证明 2026-08-27 当时已建立会话、设置、运行时上下文、实现／验证、长期决策和历史蓝图的分层路由。它不再是总控入口，当前只读本文顶部和总控文档顶部 2026-08-28 门。
- **防冲突边界：** 总控门只汇总当前范围、验证分层、正式 APK 与未发布代码的界限；功能视觉值仍只在三份当前合同，具体测试／包／设备事实仍只在本交接最新条目。不得把 code 66 的历史正式覆盖写成包含后续未发布 UI／上下文增量，也不得以定向 JVM 通过宣称全量 JVM 或真机已通过。

## 2026-08-27：聊天内查找灰卡与白色输入面（JVM／Debug 编译已验证）

- **修正：** 用户截图对应的是“在聊天中查找”弹层，而非本地文件文本预览。该弹层整卡现使用一阶中性灰承托，标题、字段说明和动作直接位于灰卡上；只有关键词输入框内部保留纯白输入面。深色皮肤沿用语义色，呈现深灰弹层与对应前景输入面，不保留突兀的白色残片。
- **自动验证：** `ConversationFindInChatUiContractsTest`（含灰卡容器契约）与 Debug Kotlin 编译通过，`git diff --check` 通过。未运行仪器测试、未构建 Release、未安装或操作 OPPO；浅色／深色实际视口仍待人工确认。

## 2026-08-27：暗色本地文本／PDF 预览 surface 收口（JVM／Debug 编译已验证）

- **根因与修正：** “本地安全文本预览”把文件顶部操作的 `dark` 硬写为 `false`，正文阅读面也硬编码浅灰，导致暗色皮肤出现白色下载、分享、关闭控件和大块白色文本面。PDF 预览外层与顶部操作存在同一遗漏。现在两者都由当前 `ForegroundSurface` 派生深浅状态；暗色时使用炭灰预览画布、浅灰正文与半透明白色操作胶囊。PDF／图片页面本身的真实白色内容不做反相。
- **自动验证：** `P6F2EAudioAndTextPreviewUiContractsTest`（含新增文本／PDF 深色 surface 契约）与 Debug Kotlin 编译通过，`git diff --check` 通过。单独运行既有 `P6F2CPdfPreviewUiContractsTest` 仍在它的旧全文件 `http://`／`https://` 静态字符串断言失败；该字符串位于未改动的来源站点显示逻辑，不能归因于本次皮肤修正。未运行仪器测试、未构建 Release、未操作 OPPO；暗色真机视觉仍待人工确认。

## 2026-08-27：回答级上下文来源说明与临时聊天隔离（JVM／Debug 编译已验证）

- **普通回答：** 上下文审计新增 Attempt→实际 Assistant 消息绑定。已成功落库的回答在既有页脚可按需查看“本次上下文来源”：只显示实际加入的本地资料类别、标题和本机匹配原因，不显示来源正文、Prompt、附件、Provider 原始请求或凭据。无额外资料时明确说明仍使用本轮输入、当前对话路径和固定系统规则；失败、取消或未生成回复的请求不会显示为回答来源。
- **临时聊天：** 明确固化为独立本机离线恢复链，最多 24 小时；发送只追加临时本地消息，不连接模型服务，不读取／外发 Memory、资料库、普通历史或附件正文，不进入普通搜索、自动标题、记忆摘要、上下文记录或费用记录。返回普通聊天只切换视图，不复制临时内容。
- **自动验证：** `AnswerContextDisclosureContractsTest`、`AnswerContextDisclosureUiContractsTest`、`CurrentRuntimeContextContractTest` 与既有 `P6ETemporaryConversationContractsTest` 共 11 项通过，Debug Kotlin 编译通过，`git diff --check` 通过。未运行仪器测试；没有重建 Release、没有安装或操作 OPPO，不能将此前 APK 覆盖记录当成本次功能验收。完整 JVM 套件仍不是全绿基线，历史失败与 JBR C2 崩溃边界见下一节。

## 2026-08-27：本轮增量 checkpoint 与最终回归边界

- **冻结范围：** 本地 checkpoint 收纳当前 Android 会话、搜索、设置与数据层的全部增量，以及三份当前合同、入口审计和本交接记录；不推送远端、不创建 Release，也不把历史已完成条目重复沉淀。当前正式签名 APK 仍为 `app/build/outputs/apk/release/南枫AI.apk`，SHA-256 `d7e387593002e6062f7da856d1503cbcf0a6985d388f25e586c973a472e98eca`；已完成的同签名 OPPO 保数据覆盖证据见下一节，文档变更本身不改变 APK。
- **最终 JVM 回归：** 上一轮完整 `:app:testDebugUnitTest` XML 为 `796` 项、`60` 项失败，因此本 checkpoint **不是全绿基线**。失败分布于 15 个既有／静态契约类：其中 `P6DConversationRowAccessibilityContractsTest` 含大量旧源码锚点，其余包括 System Bars、直接执行／预检、Composer／导航、媒体预览、设置卡与工作区交换契约；还包括 `ClassCastException`、`ExceptionInInitializerError` 和断言失败。2026-08-27 复盘重跑时，Android Studio JBR 21.0.10 的 C2 编译器在 `android.database.sqlite.SQLiteProgram.<init>` 发生 `SIGSEGV`，测试进程在 `245` 项完成、`60` 项失败、`1` 项 skipped 后以 exit `134` 终止，未产生新的完整聚合。两类问题都须独立归因，不能将定向通过写成全量通过。
- **已通过的针对性验证：** `ConversationSearchAttachmentPreviewUiContractsTest`、`ConversationSearchSurfaceContractsTest`、`SettingsUiSimplificationContractsTest`、`SettingsSwitchGeometryContractsTest`、`ModelSettingsUiContractsTest`、`FBP6043ScrollToLatestContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 已通过；Debug Kotlin 与正式 Release 构建已通过，未运行任何仪器测试。真实设备上最新“搜索进入对话后横滑返回”及视觉／手感仍待人工验收，不能由上述 JVM 结果替代。

## 2026-08-27：搜索文件定位可横滑返回，搜索层级与资料库说明收口（JVM／OPPO 已验证）

- **搜索来源返回：** 搜索页长按文件并选择“快速定位到对应对话”、或在“全文／正文”直接点击结果进入对话后，均只在该临时来源路径上保留返回标记；进入目标对话后，向左或向右横滑超过 `48dp` 且明显横向即可回到原搜索页面。实现改为读取嵌套列表／抽屉已消费后的原始位移，不再因手势竞争漏掉左滑；查询词、分类、结果和本地预览继续由搜索 owner 保持，普通对话、自动滚动和其他来源不接管该手势。
- **搜索视觉与说明对齐：** 搜索标题提升为 `titleLarge / Bold`；分类文字统一 `SemiBold`，选中项使用当前主题色。个性化中“资料库搜索”下的说明文字从误设的 `18dp` 左右缩进改为与同页其他说明一致的 `4dp`；开关、检索范围和保存语义不变。
- **自动／设备验证：** `ConversationSearchAttachmentPreviewUiContractsTest`、`ConversationSearchSurfaceContractsTest` 与 `SettingsUiSimplificationContractsTest` 通过，Debug Kotlin 与正式 Release 构建通过，未运行仪器测试。正式签名 Release 已同签名覆盖 OPPO Find N5（`3B157F009E800000`），`pm install -r --user 0` 返回 `Success`；本地产物与设备实际 `base.apk` SHA-256 均为 `d7e387593002e6062f7da856d1503cbcf0a6985d388f25e586c973a472e98eca`，版本 `66 / 0.3.0-p10j`、非 Debug、证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。首次安装时间仍为 `2026-08-20 15:15:31`，仅更新时间为 `2026-08-27 22:45:59`；未卸载、未清数据，设备临时 APK 已清理。横滑与真实搜索页恢复仍待目标视口手动验收。

## 2026-08-27：一键上下阅读按可见一屏推进，并只在真实边界停止（JVM／OPPO 已验证）

- **根因与实现：** 原先固定 `360dp` 步进不足一屏；更关键的是，到达末端时自动跟随使用 `scrollToItem(last)` 将一条很长的最后消息顶部对齐，视觉上便会从底部反跳到上方。现在一次点击精确推进当前 `LazyList` 视口高度减去悬浮 Composer 遮挡后的可见阅读高度；最终一次由滚动边界自然截断，并只在 `canScrollForward == false` 的真实物理末端隐藏。自动跟随改为补齐至真实末端，绝不将末行顶部对齐；新流式内容不会被当成用户离开底部的手势，用户手动拖动仍会立即关闭跟随。普通会话与工作会话共用该末端语义。
- **上下手势与入口：** 底部按钮短按继续仅推进一整页阅读区且不重新开启自动跟随，长按显式直达真实底部并恢复跟随。顶部新增同一组件的上箭头按钮，圆心与菜单按钮同一行：短按向上一整页，长按直达真实开头，两种上行手势均保持关闭自动跟随。两端均不常驻：用户手动向下／向上阅读立即显示对应方向，手势惯性仍刷新计时；画面停止滚动满 `3 秒` 自动收起，程序自动滚动不触发出现；各自到达物理边界也立即隐藏。
- **本次自动／设备验证：** 上下方向、3 秒空闲收起、短按／长按与共享按钮纳入 `FBP6043ScrollToLatestContractsTest`，并与 `P6GUnifiedChatFirstUiContractsTest` 通过，Debug Kotlin 与正式 Release 构建通过，未运行仪器测试。正式签名 Release 已同签名覆盖 OPPO Find N5（`3B157F009E800000`），`pm install -r --user 0` 返回 `Success`；本地产物与设备实际 `base.apk` SHA-256 均为 `a6152d822e2f32ebb7e75dcb6b1953c3a8f0c36385e57e7f984bb2f8152bf773`，版本 `66 / 0.3.0-p10j`、非 Debug、证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。首次安装时间仍为 `2026-08-20 15:15:31`，仅更新时间为 `2026-08-27 22:23:42`；未卸载、未清数据，设备临时 APK 已清理。3 秒计时、顶部位置、长按时长与长回复／手动拖动后的真实手感仍待目标视口人工验收。
## 2026-08-27：全局开关 58×28、可见关闭态与紧凑网页搜索卡（JVM／OPPO 已验证）

- **根因与实现：** 先前错误地多次以百分比变换开关尺寸，造成比例失真。现不再推导，APP 内实际滑块开关统一由共享 `SettingsSwitch` 直接绘制为 `58×28dp` 轨道与 `21dp` 白色圆拇指，保留 `48dp` 高可访问点击面、`Role.Switch` 语义、主题色开态和同胶囊轮廓的按压反馈；设置、模型与联网、Composer 联网入口全部复用该组件。
- **关闭态补正：** 自定义浅色主题的 `surfaceVariant` 与白色设置卡相同，导致关闭态轨道不可见；现改为 `onSurface` 的 `16%` 透明度浅灰，浅／深皮肤均能明确辨认关闭状态而不添加重描边。
- **普通切换不打扰：** 实时网页搜索全局开关、当前会话联网开关与通知／提醒开关在成功保存后只更新自身状态，不再弹出居中、行内或会话成功提示；失败仍保留原因。个性化表单的显式“保存”成功反馈保持不变。
- **全局关闭态与高度：** 所有实际滑块开关均复用该共享组件；关闭态固定为 `onSurface` 派生的 `16%` 可见浅灰，禁止再与白卡／深色卡融为一体。模型与联网页的“实时网页搜索”卡将上下内边距从 `13dp` 收至 `4dp`，在保留开关完整 `48dp` 命中面的同时将卡高压至约 `56dp`。
- **自动验证：** `SettingsSwitchGeometryContractsTest` 与 `ModelSettingsUiContractsTest` 通过，Debug Kotlin 编译同轮通过；全 UI 目录已无 Material 3 `Switch` 的实际导入。未运行仪器测试、未卸载／清数据；目标视口仍需确认浅／深皮肤下的实际长度、高度、拇指居中、关闭态和按压手感。
- **OPPO 保数据覆盖：** 已以正式签名 Release `app/build/outputs/apk/release/南枫AI.apk` 同签名覆盖 OPPO Find N5（`3B157F009E800000`），`pm install -r --user 0` 返回 `Success`。本地产物及安装后实际 `base.apk` 的 SHA-256 均为 `36405888709a5a2e1a1e38beae7661455e95898fa97f5793f58a29337635cc0e`；版本仍为 `66 / 0.3.0-p10j`，非 Debug，签名证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。首次安装时间保持 `2026-08-20 15:15:31`，仅更新时间变为 `2026-08-27 21:36:16`；未卸载、未清数据，设备临时 APK 已清理。尚未冷启动或手动检查开关的真实视觉。

## 2026-08-27：居中模态统一改为轻量背景压暗（JVM 已验证）

- **实现：** 所有应用内居中 `AlertDialog`／`Dialog` 回归共享 Dialog owner，并在窗口层统一设为中性 `0.12f` 背景压暗；不改底部 Sheet、系统文件选择器或全屏媒体预览自身的关闭语义。Memory 与隐私清理页移除对平台 Dialog 的绕过，确保也继承同一弱遮罩、点外关闭和内收边缘滑动规则。
- **自动验证：** 新增 `CenteredDialogScrimContractsTest` 通过，Debug Kotlin 编译同轮通过。完整 `P6DConversationRowAccessibilityContractsTest` 在当前已有大量并行改动的工作树中有 40 条无关旧源码锚点失败，未作为本条功能结论。未运行仪器测试、未安装／卸载／清数据，也不操作 OPPO；目标视口仍需确认浅／深皮肤下均为轻量压暗。

## 2026-08-27：保存 API Key 显示成功或失败结果（JVM 已验证）

- **根因与实现：** 保存用例已返回本机成功／失败状态，但模型设置页未渲染成功 notice，失败也只以普通文字出现。现在 API Key 写入成功显示“API Key 已安全保存在本机。”及“尚未测试连接”的下一步；Keystore／格式／持久化等失败以同位置错误卡显示原因和建议。保存模型设置但未编辑 Key 也会显示独立的保存成功卡；保存不触发 Provider HTTP。
- **自动验证：** `ModelSettingsUiContractsTest` 通过，Debug Kotlin 编译同轮通过。未运行仪器测试、未安装／卸载／清数据，也不操作 OPPO；未写入真实 Key、未执行连接测试或 Provider HTTP，真实 Keystore 写入与错误提示仍需在用户自己的配置路径手工确认。

## 2026-08-27：模型设置入口移除主题色线框（JVM 已验证）

- **实现：** “模型与联网”的“模型设置”重点卡删除主题色描边，保留纯白／深色卡面、圆角、零投影、主题色钥匙图标与进入箭头；进入模型设置、API Key、模型与连接等既有功能不变。
- **自动验证：** `ModelSettingsUiContractsTest` 通过，Debug Kotlin 编译同轮通过。未运行仪器测试、未安装／卸载／清数据，也不操作 OPPO；浅／深色实际入口层级仍待目标视口确认。

## 2026-08-27：个性化资料库搜索前移为普通设置（JVM 已验证）

- **实现：** “资料库搜索”从表单底部的可折叠“高级”区移至“启用记忆”说明正下方，移除“高级”标题、箭头与展开状态；原白色／深色开关卡、页面 canvas 上的说明、默认值、草稿保存路径与本机检索边界不变。
- **自动验证：** `SettingsUiSimplificationContractsTest` 通过，Debug Kotlin 编译同轮通过。未运行仪器测试、未安装／卸载／清数据，也不操作 OPPO；目标视口仍需确认长表单滚动中的正常阅读顺序。

## 2026-08-27：侧栏顶部灰色软渐变加强（JVM 已验证）

- **实现：** 仅将顶部 `128dp` 软渐变的中段 alpha 从 `0.90 / 0.62 / 0.22` 调为 `0.96 / 0.76 / 0.36`；完整端仍为 `1.00`，底部 `112dp` 渐变、固定品牌行、搜索／已计划卡、滚动视口和无硬边原则均不变。
- **自动验证：** `ConversationDrawerEdgeFadeContractsTest` 通过，Debug Kotlin 编译同轮通过。未运行仪器测试、未安装／卸载／清数据，也不操作 OPPO；实际外屏需确认加强后仍是连续软淡出，没有硬边或压暗固定品牌行。

## 2026-08-27：一键到底固定步进，手动滚动不再被抢走（JVM 已验证）

- **根因与实现：** 旧按钮先按 Assistant 回复定位，再补齐回复末端，故会先上跳再下跳；现改为每次固定向下 `360dp`，只以 `canScrollForward` 判断真实末端，未到底可持续点击，到底才消失。普通对话和工作会话的手指拖动都会立刻关闭自动跟随，防止流式更新或先前状态在手动阅读时把列表擅自拉走；发送与用户自己回到真实底部的既有跟随语义保留。
- **自动验证：** `FBP6043ScrollToLatestContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，Debug Kotlin 编译同轮通过。未运行仪器测试，不安装／卸载／清数据，也不操作 OPPO；目标视口仍需手动验证连续点击的固定步进、真实底部消失和手动拖动期间的流式回复。

## 2026-08-27：会话 Markdown 色值误判与控制符残留修复（JVM 已验证）

- **根因与实现：** 展示层的“缺空格标题”归一化曾把 CSS `#fff` 误判为一级标题，造成中段文字异常放大；模型转义的 `\#`／`\*` 和未闭合反引号也可能漏到读者界面。现在 CSS 色值保持普通文本，转义 Markdown 在内存投影中按语义处理，残余控制符在合并相邻文本后移除；持久化会话正文、搜索索引与导出内容均不改写。
- **自动验证：** `P3DMessagePresentationContractsTest`（含 CSS 色值、转义强调和残留符号样本）及 `UserMessageTypographyContractsTest` 通过，Debug Kotlin 编译通过；未运行仪器测试、未安装／卸载／清数据，也未操作 OPPO。目标视口下的长篇真实回答观感仍待手工确认。

## 2026-08-27：收藏、归档与回收站可打开原对话（JVM 已验证）

- **实现：** 收藏列表取消常驻“取消收藏”按钮，改为与归档／回收站一致的左划操作带；三类列表的未滑出行均可直接打开所属真实会话。进入会话时保存来源生命周期列表；系统返回时恢复到收藏、已归档或回收站的原路由与当前列表投影，不误回普通对话。任一操作带已滑出时，点击同一行、其他行或空白均只收起操作带，不会误打开会话或执行取消收藏／恢复／删除。
- **自动验证：** `P6DConversationRowAccessibilityContractsTest.favorite archive and recycle rows open their conversation and return to the originating lifecycle list` 与 `:app:compileDebugKotlin` 通过；未运行仪器测试、未安装／卸载／清数据，也未操作 OPPO。左划手感、真实返回和浅／深色观感仍待目标视口手工确认。

## 2026-08-27：自动会话标题仅接受中英文总结短句（JVM 已验证）

- **实现：** 标题整理的提示词与本机 JSON 结果校验同步收紧：标题仅可由汉字、英文字母组成；英文单词之间最多保留一个普通空格。数字、标点、Markdown、emoji、括号、连字符、下划线及其他符号一律拒绝；非法结果不会消耗自动资格。标题必须是“明确对象 + 具体意图／问题／任务”的紧凑短语，原文术语优先；“继续说”“总结”“更新文档”等无对象泛词在模型提示和本机校验中均被拒绝。标题生成优先已配置的 `Qwen3.6 Flash`，再回退 `Qwen3.7-Plus`、`5.6 Luna`、`5.6 Terra` 与已配置的 DeepSeek，明确禁止 `5.6 Sol`、Fable 5、Opus 5。新安装默认将千问显示为待配置的标题候选，但不会伪造 Key；无可用凭据、服务未启用、模型不可用和格式不合法都会留下安全失败记录。首条只有附件时，不读取或外发附件的类型、文件名、内容或本体，只将南枫AI首条回复用于标题；服务失败后在下一次完成回复继续重试；人工重命名仍优先。
- **自动验证：** 强制重跑的 `ConversationTitleFormatContractsTest`（Robolectric 标题 JSON 解析）、`ConfiguredConversationTitleRefinerContractsTest`（千问优先、轻量回退与安全失败记录）和 `P6IConversationAutoTitleContractsTest`（附件首发与可重试标题来源）通过；Debug Kotlin 编译通过。未运行仪器测试，不安装／卸载／清数据，也不操作 OPPO。真实 Provider 返回格式与标题内容仍需在已配置账号下手工确认。

## 2026-08-27：用户消息 Markdown 使用克制的独立字号层级（JVM 已验证）

- **实现：** 用户消息继续保留 Markdown 的标题、段落、列表、引用、强调与代码语义，但不再沿用南枫AI开放阅读列的页面级标题比例。气泡正文固定 `15sp / 23sp / Normal`；三级标题仅 `18sp / 17sp / 16sp`，对应 `26sp / 24sp / 23sp` 行高与 `SemiBold / Medium / Medium`，不使用 `ExtraBold`。列表缩至 `6dp` 项距，注释为 `13sp / 19sp`。南枫AI正文的 `16sp / 25sp`、一级 `26sp / 35sp / ExtraBold` 和 `8dp` 列表节奏不变；两套均继续跟随全局字体档。
- **自动验证：** `UserMessageTypographyContractsTest` 与 Debug Kotlin 编译通过。历史 `P6DConversationRowAccessibilityContractsTest` 中有多处与本次字号无关的静态源码锚点已不匹配当前工作树，未作为本次功能通过依据；未运行仪器测试、未安装／卸载／清数据，也不操作 OPPO。实际长 Markdown 在目标视口的阅读密度仍待手工确认。

## 2026-08-27：新对话固定进入最近列表首位（JVM 已验证）

- **根因与实现：** Room 的活跃会话查询已按 `updatedAt` 倒序，但“新对话”此前会复用旧的空白会话，未更新其时间，因而仍显示在旧位置。现在用户明确点击“新对话”会创建新的空白会话，先切回活跃列表，并以该新会话 ID 重新加载；它因此固定成为“最近”第一条。置顶区不变。
- **自动验证：** `NewConversationRecentListContractsTest` 与 Debug Kotlin 编译通过；未运行仪器测试，不安装／卸载／清数据，也不操作 OPPO。实际抽屉排序待同视口手工确认。

## 2026-08-27：Assistant 页脚短模型名与设置返回滚动位置（JVM 已验证）

- **模型名：** Assistant 回复页脚从完整目录名改为与 Composer 相同的短模型名（如 `Claude Opus 5 → Opus 5`）。模型选择、模型设置、调用／费用／上下文记录仍保留完整名称，归因和计费数据不改。
- **设置滚动：** 设置根在组合分支之外按页面层级保留独立 `ScrollState`。二级及更深页返回一级时，一级菜单恢复离开前的滚动位置；子页滚动位置不会误带回父级。
- **自动验证：** `SettingsScrollAndFooterModelContractsTest` 与 Debug Kotlin 编译通过；未运行仪器测试，不安装／卸载／清数据，也不操作 OPPO。实际设置层级返回和不同屏幕高度下的恢复位置待手工确认。

## 2026-08-27：Assistant 页脚首个操作对齐正文（JVM 已验证）

- **实现：** Assistant 页脚保持首个操作的 `36dp` 可点击面，但将其图形视觉向左回退 `10dp`，与上方正文 `24dp` 左缘精确对齐；分享、更多菜单、时间、模型和金额位置不变。
- **自动验证：** `AssistantFooterActionAlignmentContractsTest` 与 Debug Kotlin 编译通过；未运行仪器测试、未安装／卸载／清数据，也不操作 OPPO。最终同视口像素对齐待手工确认。

## 2026-08-27：左侧栏南枫 AI 品牌行放大（JVM 已验证）

- **实现：** 固定左侧栏品牌标题改为 Android `sans-serif-rounded` 的 `22sp / 28sp / Bold`，图标同步从 `28dp` 放大至 `36dp`。滚动内容顶部预留改为图标与标题行高中的较大值，首个搜索／已计划控件不会在默认状态下被覆盖；会话行、日期、底部控件不变。
- **自动验证：** `ConversationDrawerFloatingHeaderContractsTest`、`AppearanceFontSizeContractsTest` 与 Debug Kotlin 编译通过；未运行仪器测试、未安装／卸载／清数据，也不操作 OPPO。浅／深皮肤与极大系统字号下的实际观感仍待手工确认。

## 2026-08-27：对话 ZIP 导入限定系统文件类型（JVM 已验证）

- **根因与实现：** 数据与存储的“导入 ChatGPT ZIP／导入 Claude ZIP”此前向 DocumentsUI 请求 `*/*`，导致各类文件都显示。现只请求 `application/zip` 与 `application/x-zip-compressed`；选中后既有 app-private 暂存、文件名、大小与 ZIP 目录安全检查不变。
- **自动验证：** `P6KZipImportUiContractsTest` 与 Debug Kotlin 编译通过；未运行仪器测试、未安装／卸载／清数据，也不操作 OPPO。不同系统文件管理器对 MIME 的实际筛选观感待手工确认。

## 2026-08-27：左侧栏会话标题默认 14sp（JVM 已验证）

- **实现：** 共享 `ConversationNavigationRow` 的会话标题从 `16sp / 20sp` 调整为按全局字号档位缩放的 `14sp / 18sp / Normal`；置顶、最近及普通会话行同步生效。日期、分组标签、搜索／已计划、行高、卡片间距和触控面不变。
- **自动验证：** `ConversationDrawerEdgeFadeContractsTest` 与 Debug Kotlin 编译通过；未运行仪器测试、未安装／卸载／清数据，也不操作 OPPO。实际左栏同视口字号观感待手工确认。

## 2026-08-27：一键到底按回复边界继续阅读（JVM 已验证）

- **根因与实现：** 一个长篇 Assistant 回复在 `LazyColumn` 中只占一项，旧逻辑只要看见最后一项的顶部便错误认定整条会话结束，故按钮小步移动后消失。现改由 `LazyListState.canScrollForward` 判断真实末端；点击优先补完当前未读的 Assistant 回复，否则停在下一条 Assistant 回复结尾。不存在下一条回复时才前往真实列表末端，途中按钮持续可点。
- **范围：** Android 普通对话与工作会话共用真实末端判断；不改变按钮材质、层级、Composer、消息数据或 Provider 语义。Desktop 尚无同等 owner，未宣称完成。
- **自动验证：** `FBP6043ScrollToLatestContractsTest` 与 Debug Kotlin 编译通过；不运行仪器测试、不安装／卸载／清数据，也不操作 OPPO。实际长篇回复的连续点击节奏仍待同视口手工确认。

## 2026-08-27：聊天内查找正文胶囊高亮（JVM 已验证）

- **实现：** 已有“在聊天中查找”提交关键词并定位当前匹配后，全文的可见命中同步投影为主题色文字、浅中性灰圆角胶囊底；跨行命中按每行独立圆角绘制，不形成整行色带。高亮只读取当前查找 query，关闭查找即消失，不改 Room 消息、搜索索引、文本选择或发送内容。
- **跨端与入口：** Android 已启用；Desktop 尚无同等 owner。只增强现有查找弹层与导航条，不增加聊天主页、Composer 或设置常驻按键。
- **自动验证：** `ConversationFindInChatUiContractsTest` 与 Debug Kotlin 编译通过；未运行仪器测试、未构建／安装 Release、未操作 OPPO；同视口浅／深色观感待手工确认。

## 2026-08-27：单条 Assistant 回复导出 Markdown（JVM 已验证）

- **实现：** 每条 Assistant 回复页脚保留复制、分享及三个点；“导出 Markdown”与“创建分支”统一收进三个点弹层，导出固定排在首项。点击导出时仅把该条已渲染的语义内容重建为 `.md`，写入临时分享目录并经 `FileProvider` 交给系统保存／分享；不带出 Prompt、Provider 原始回包、凭据、其他对话消息或附件字节。
- **跨端与入口：** Android owner 已启用；Desktop 尚无同等导出 owner，未展示伪入口。入口限定在单条 Assistant 页脚的次级菜单，不增加聊天主页、Composer、会话长按菜单或设置常驻按钮。
- **自动验证：** 单条回复 Markdown 导出／三个点菜单排序的定向 JVM 合同与 Debug Kotlin 编译通过。未运行仪器测试、未构建／安装 Release、未操作 OPPO；系统保存目标的实际选择由用户设备上的系统分享面决定。

## 2026-08-27：会话长按菜单导出 Markdown（JVM 已验证）

- **实现：** 侧栏会话长按菜单在“分享”后新增“导出 Markdown”文件下载图标。导出当前会话的标题、当前路径上的用户／Assistant 可见内容和显示时间，写入临时分享目录并经 `FileProvider` 交给系统保存／分享；隐藏分支、Prompt、Provider 原始回包、凭据、其他会话和附件字节均不导出。
- **边界：** 会话菜单只可导出当前已打开的真实路径；若长按的不是当前会话，先打开目标会话并要求再次点击，绝不以目标标题错误导出此前会话内容。单条 Assistant 回复的页脚导出保持独立，不改变其范围。
- **跨端与验证：** Android owner 已启用，Desktop 尚无同等 owner。会话菜单 Markdown 导出断言与 Debug Kotlin 编译通过；未运行仪器测试、未构建／安装 Release、未操作 OPPO。

## 2026-08-27：材料标签统一加粗（JVM 已验证）

- **实现：** 会话阅读投影将单独成行的 `<图片>`、`<PDF>`、`<视频>`、`<音频>`、`<文件>` 统一识别为同一种加粗标签；模型是否自行写 Markdown 不再影响字重。仅改内存中的展示投影，不改原始消息、附件、搜索索引或发送内容。
- **范围：** 这是已有材料标签的视觉一致性修正，不新增用户入口或 Desktop 功能。
- **自动验证：** `P3DMessagePresentationContractsTest` 通过。未运行仪器测试、未构建／安装 Release、未操作 OPPO；实际字号与字重观感仍待同视口确认。

## 2026-08-27：普通聊天重试恢复生成态（JVM 已验证）

- **实现：** 点击“发送未完成”的“重试”后，ViewModel 同帧清除失败决策面；执行器保留原发送 attempt／幂等编号、模型和服务商，但在原用户消息下创建新的持久化 Assistant `PARTIAL` 占位节点。它由既有消息模板生成对应的图片／文档／文本初步回复，并在本次明确重试期间显示“南枫AI 继续生成…”。首个流式片段直接写入该节点，旧失败节点留在历史分支，不与新输出混排。
- **失败边界：** 新一轮请求被拒绝、失败或取消时，占位节点分别写入真实终态；没有成功就不伪造回复。再次可重试时才重新显示失败面；正常发送与其他会话的生成提示不改变。
- **自动验证：** `:app:compileDebugKotlin` 与 `P3JNormalChatExplicitEgressContractsTest` 通过。未运行仪器测试，未构建／安装 Release、未操作 OPPO；实际重试的瞬时关闭、模板衔接与真实 Provider 流式到达仍待用户在设备上确认。

## 2026-08-27：会话收藏与提醒白卡（JVM/Release 已验证）

- **实现：** “提醒”页保留三项独立、即时生效的开关和真实说明，但在灰白页面底上合并为一张高明度白色大卡；卡片使用共享圆角、`1dp` 克制短阴影，三行内缩 `20dp`，以细分割线分隔。未变更任一开关的本机持久化、通知权限、对话尾部建议或未读水位语义。
- **收藏：** 会话长按菜单与左侧会话行右滑操作带都在“置顶／取消置顶”正下方提供“收藏／取消收藏”。收藏时间写入本机 Room schema `53→54`，不改变普通列表排序、不读取或外发正文；归档或移入回收站时自动取消。设置 → 对话管理同步新增“收藏”模块，可查看并取消收藏；Desktop 没有同等 owner，未展示伪入口。
- **自动验证：** `P3EConversationManagementExportContractsTest`（含收藏持久化和 `53→54` 迁移）、`P5DLocalBackupRestoreContractsTest`、`AndroidUserEntryAuditContractsTest`、`SettingsUiSimplificationContractsTest` 通过；Release 已重新构建并验签。未运行仪器测试，未安装或操作 OPPO；菜单行顺序、白卡层级与收藏页真机观感仍待同视口人工确认。

## 2026-08-27：搜索文件长按快速定位（JVM/Release 已验证）

- **实现：** 搜索文件普通点击仍只打开本地预览；长按改为参考会话菜单的醒目白色圆角操作面，显示当前文件名与唯一的“快速定位到对应对话”大图标操作行。点击后复用既有 `ConversationSearchHit(conversationId, messageNodeId)`，关闭搜索页并滚动到所属会话的对应消息；点空白或返回关闭，不新增确认／取消步骤。
- **自动验证：** `ConversationSearchAttachmentPreviewUiContractsTest`、`ConversationFindInChatUiContractsTest` 与 `ConversationRenameDialogFocusContractsTest` 已通过；正式 Release 重新构建和验签通过。未运行仪器测试，未安装或操作 OPPO；长按、弹面比例和实际滚动落点仍待后续保数据覆盖后的同视口手工确认。

## 2026-08-27：重命名自动编辑与抽屉全宽（JVM/Release 已验证）

- **实现：** 紧凑会话重命名弹层出现后，标题字段自动获取焦点、唤起系统键盘，并全选当前标题；用户后续输入不再反复全选。弹层左对齐且与当前抽屉同宽：窄屏最多 `320dp`，展开内屏取可用宽度的 `2/3`，移除左右外边距和独立宽度上限。保存／取消／点外关闭与既有手动重命名写入不变。
- **自动验证：** `ConversationRenameDialogFocusContractsTest` 与正式 `:app:assembleRelease` 已通过。未运行仪器测试，未安装或操作 OPPO；键盘唤起、全选与抽屉全宽的同视口实机观感仍待下一次用户授权的保数据覆盖后确认。

## 2026-08-27：聊天内查找输入层级（JVM/Release 已验证）

- **实现：** “在聊天中查找”将字段标题移至输入框上方；输入区外层改为中性灰承托，内部保持白色／当前深色前景色，明确区分可输入面与弹层白面。删除空态的重复本地范围提示；无匹配、匹配数量、键盘搜索与定位行为不变。
- **自动验证：** `ConversationFindInChatUiContractsTest` 与正式 `:app:assembleRelease` 已通过。未运行仪器测试，未安装或操作 OPPO；最终同视口视觉仍待下一次用户授权的保数据覆盖后手工确认。

## 2026-08-27：费用与用量展示模型耗时（JVM/Release 已验证）

- **实现：** 普通回答的费用明细在 Token/金额来源下新增“模型耗时”。它只投影关联 `normal_chat_send_attempts` 的已完成本机边界（创建至完成），因此既可显示现有完成记录，又不增加 Prompt、回复、附件、Key 或原始 Provider 回包的存储。
- **边界：** 非完成、缺失或非正耗时不会倒推或伪造，固定显示“模型耗时未记录”；会话标题与提醒草案的独立记录尚无相同请求边界，不显示伪耗时。
- **自动验证：** `AssistantResponseModelAttributionRoomContractsTest`、`ConversationCostLedgerSummaryContractsTest`、`P5DLocalBackupRestoreContractsTest` 与正式 `:app:assembleRelease` 已通过；尚未安装或操作 OPPO，实际页面排版待下一次用户授权的保数据覆盖后手工确认。

## 2026-08-27：增量正式 checkpoint（优先于下方历史记录）

- **确认的侧栏根因与实现：** 左侧栏顶部横跨全宽的白／灰硬边不是独立灰层，而是 `verticalScroll` 在 `statusBarsPadding` 与顶部 padding 之后才建立、导致视口被提前裁小并露出父级基底。普通抽屉固定采用 `fillMaxSize → conversationEdgeGrayFade(ConversationDrawerBaseSurface) → verticalScroll → statusBarsPadding → content padding`；渐变只绑定滚动视口，固定品牌行无白／灰底，搜索和“已计划”恢复自身中性灰表面。用户已在 OPPO 上确认该视觉效果正确。
- **自动回归：** `ConversationDrawerEdgeFadeContractsTest`、`ProviderAdapterContractsTest`、`ProviderSseDecoderContractsTest`、`AutomaticWebSearchPolicyTest`、`EvidenceFirstAnalysisPolicyTest`、`P3JNormalChatExplicitEgressContractsTest` 均通过。未运行 `connected*AndroidTest`，也未把 OPPO 当作测试设备。
- **产物与设备：** Release 候选为 [南枫AI.apk](../app/build/outputs/apk/release/南枫AI.apk)，SHA-256 `208cfcd5b4bdfe43240710019a5c6c5ec1e41e3a3698c99cb7e4302d6159e377`，包为非 Debug `66 / 0.3.0-p10j`、签名证书 `6d1d56ec…e198d8661f8`。已成功同签名覆盖 OPPO，覆盖后即时读到版本仍为 `66 / 0.3.0-p10j`、首次安装时间保持 `2026-08-20 15:15:31`、更新时间为 `2026-08-27 01:40:23`。随后设备断开，故本次 checkpoint 不声称已重新拉回设备 `base.apk` 与候选逐字节比对；设备重连后如需再次正式交付，先做只读哈希核对。
- **边界：** 侧栏视觉已有用户真机确认；图片、文档、视频、音频的 Provider 实际理解效果仍依赖用户真实模型、Key 与网络，自动测试只证明请求序列化、解析与错误边界正确。

## 2026-08-26：最新有效交接（优先于下方所有记录）

- **当前正文与同步规则：** `ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md`、`ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md`、`ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md` 分别是会话／搜索、设置／个性化、普通聊天上下文的唯一正文。任何后续改动先更新其对应当前合同；涉及多个域或覆盖安装时，再同步本节和 `MASTER_PLAN_COMPLETION_AUDIT_20260816.md` 的顶部当前门。下方历史 P4、视觉、安装哈希和旧阶段“未实现”描述仅作可追溯证据。
- **已收口的 Android 体验：** 会话／搜索、侧栏、暗色皮肤、附件全屏预览与长按定位、弹窗点外关闭、Composer 紧凑输入区、浮层阴影及一键到底层级、个性化和记忆摘要页面、设置字体／白卡／开关／主题色均以当前 UI 合同为准。冻结阴影为顶部浮层 `54dp / 0x1B000000 / 下偏 3dp`、Composer `84dp / 1dp / 0x22000000 / 对称`；不得从历史记录恢复旧参数。
- **普通聊天真实上下文：** 非空且已保存的自定义指令独立参与每次普通调用；个性化开启时，昵称、职业／角色和关注方向额外参与，新对话首个成功回复自然称呼昵称。已开启的 Memory 与资料库在设备本机按当前问题、scope、去重和 token 预算选取完整相关条目后发送；不整库外发、不读取附件字节／凭据／运行日志／同级分支。Memory 仍必须显式创建，自动行为仅指已开启后的相关性检索。
- **记忆摘要操作已拆分：** “删除记忆”只软删除已保存摘要，且删除记录阻止冷启动自动回填初始摘要；“关闭记忆摘要生成和应用”只关闭后续摘要生成和普通聊天的 Memory 检索／发送，保留已存摘要与直接填写的个性化资料。两项各自确认，不能再用一个操作同时删除并关闭。
- **实时网页搜索：** Android 在“设置 → 模型与联网”将“实时网页搜索”放在“模型设置”上方；开关立即持久化，开启时仅在当前信息请求中自动选择当前 Provider 的已实现官方搜索路线并保留来源，关闭则所有普通聊天（含深度与材料分析）不发送网页工具。主界面 Composer 左侧“添加”浮层底部另有“当前对话联网”开关：首次继承全局值，随后按会话 ID 本机持久覆盖并供普通发送实际选路，只影响当前会话、不修改设置默认或其他会话。Desktop 尚无同等普通聊天联网 owner，登记为待实现，不展示伪开关。
- **2026-08-27 联网误触发修复：** 深度模型选择、附件分析及“查找附件文档／找原因”等本地任务均不再隐式附带网页工具；仅明确的当前信息或联网／网页请求才走官方检索路线。服务端已经 2xx 但未给出可展示正文时，只保存 HTTP 状态、模型、请求形状和安全 `RESPONSE_FORMAT` 分类，不保存原始回包、提示、附件或 Key。
- **2026-08-27 附件外发合同修复：** OpenRouter 混合附件统一为图片 `image_url`、PDF `file.file_data`、视频 `video_url`、音频 `input_audio` 与安全文本文件（Markdown/TXT/JSON/CSV）完整 UTF-8 文本；文本文件不得再伪装为 PDF `file_data`，未知二进制文件必须在本机明确拒绝，不能将其与有效图片／媒体一起交给 Provider 后伪称已发送。
- **验证与正式覆盖：** `ProviderAdapterContractsTest` 覆盖图片 + Markdown 及图片、PDF、视频、音频、Markdown 五类混合请求，连同流式解析、联网选路与普通发送链路测试通过，Release 构建通过。OPPO Find N5 已以 `pm install -r --user 0` 保数据覆盖；候选与设备实际 `base.apk` SHA-256 均为 `af2d6aba257cf75e0a9ac7aae15ff52b2ec24f4ee68fcf9cdedae41abb9287b2`，包仍为非 Debug `66 / 0.3.0-p10j`、v2/v3 证书 `6d1d56ec…e198d8661f8`，首次安装时间保持 `2026-08-20 15:15:31`，更新时间为 `2026-08-27 01:19:07`。未卸载、未清数据、未运行仪器测试，设备临时 APK 已删除；真实 Provider 对每种媒体的理解能力仍需用户在 App 内按实际模型手工确认。
- **验证与正式覆盖：** `AutomaticWebSearchPolicyTest`、`EvidenceFirstAnalysisPolicyTest`、`ProviderAdapterContractsTest`、`ProviderSseDecoderContractsTest`、`P3JNormalChatExplicitEgressContractsTest` 通过，Release 构建通过。候选与 OPPO Find N5 实际 `base.apk` 的 SHA-256 均为 `5a8ef722284e4220ee330a71dae054cf02e410421246a489cd26a97eb05e1838`；仍为非 Debug 的 `66 / 0.3.0-p10j`，v2/v3 证书 `6d1d56ec…e198d8661f8`，已用 `pm install -r --user 0` 保数据覆盖，首次安装时间保持 `2026-08-20 15:15:31`，更新时间为 `2026-08-27 01:09:45`。未卸载、未清数据、未运行仪器测试，设备临时 APK 已删除；真实 Opus／附件发送仍需用户在 App 内手工确认。
- **设置开关几何：** 所有设置开关统一从 `62×22dp` 调整为 `74.4×15.4dp`：长度增加 20%，高度压缩 30%。个性化、提醒、资料库搜索、实时网页搜索和模型服务启用均复用同一尺寸令牌；业务状态、主题色、胶囊轮廓和命中反馈不变。
- **全局字体大小：** Android 的“设置 → 外观”提供“小／标准／大”三档；标准为原始字级，小／大按全局 `Typography` 的 `90%／110%` 即时且本机持久化，所有正文、设置与全屏页同步变化，而控件几何和触控热区不变。Desktop 尚无同一外观 owner，明确待实现且不展示伪设置。
- **关于页信息层级：** 品牌说明、版本信息、数据与隐私提示均使用各自独立的前景白卡（暗色皮肤为同层级深卡）置于灰白／深色 canvas 上；不改变只读文本、版本／构建号事实或入口范围，也不合成大白板。
- **费用与用量完整估算：** OpenRouter 的回包 `usage.cost` 继续作为唯一“实际金额”。服务商未回传金额时，当前目录的全部模型（含千问三档和 DeepSeek V4 Pro）以版本化、按 token 的本地价目表显示 `≈ …（估算）`；旧的 token-only 归属记录读取时也即时补算，因此不会继续显示“金额未返回”。DeepSeek 的响应若带回缓存命中 token，会按其单独的缓存输入价计入估算。估算不包含响应未披露的联网工具、优惠或缓存写入费用，不能作为服务商账单对账结果。
- **提醒草案重构：** 已废弃从关键词命中“智能汽车／政策／简报”等类别模板直接生成提醒的逻辑。只有开头用户消息明确要求未来提醒／持续跟踪时才显示尾部入口；点击后以 Qwen3.7-Plus 仅整理开头一问一答为严格 JSON 的可编辑草案。失败或不适合监控不降级为模板。调用费用、Token、状态和 opaque 会话 ID 在费用页“提醒草案整理”分组单独记录；不保存发送的正文或附件。Desktop 不显示等效伪入口。
- **会话标题重构：** 此段为旧实现记录，现以本页顶部与当前会话合同的多模型、可重试规则为准。
- **网络调用恢复：** 已撤销今晚 18:00 后引入的持续生成网关、双模式、网关任务补拉和后台恢复方案；普通聊天恢复原有“本机前台服务持有一次设备直连 Provider 请求”的调用链。服务只保留用户停止、短时 WakeLock 和本机安全状态回传，不再注册网络恢复回调或 `RESUME` 命令。没有网关端点、网关 token、服务端任务或隐式重试；真实网络调用仍须以用户在 App 内配置的 Provider Key 和实际发送验证。
- **最新正式覆盖：** 2026-08-26 已构建 Release `66 / 0.3.0-p10j`，候选 [南枫AI.apk](../app/build/outputs/apk/release/南枫AI.apk) SHA-256 为 `4644d753ea0634a72e152476115a8138d3c0112561f0fd622947422547877dda`，非 Debug，v2/v3 证书为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。OPPO Find N5 安装前的 `base.apk` 为 `27d51cbfde3d46f93de71fd47c8c4ce2e7b68466b7b4872c0fa723bae8a0233d`，同一证书；已通过 `adb shell pm install -r --user 0` 覆盖成功。安装后设备 `base.apk` 回读哈希与候选完全一致，首次安装时间保持 `2026-08-20 15:15:31`，更新时间为 `2026-08-26 22:05:07`；未卸载、未清数据、未运行 Debug／仪器测试，设备临时 APK 已删除。Android shell 无权读取 CE/DE 数据目录，故不能主张 inode／数据库指纹已回读；包级保数据覆盖证据已完整，应用内真实发送与页面视觉仍待手工验证。
- **Composer IME 收起回折与正式覆盖：** 键盘收起时 Composer 现在立即从多行编辑态回到紧凑单行，草稿文字保留；`ComposerImeCollapseContractsTest` 与 Release 构建通过。OPPO Find N5 已用同签名 `pm install -r --user 0` 覆盖本次正式包 `66 / 0.3.0-p10j`；候选与设备实际 `base.apk` 的 SHA-256 均为 `3fd80020a965dd5ff8acc17028f3506372b07c7c2ee50205ad6f55703372447e`，v2/v3 证书仍为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，首次安装时间保持 `2026-08-20 15:15:31`，更新时间为 `2026-08-26 22:29:39`。未卸载、未清数据、未运行 Debug／仪器测试，设备临时 APK 已删除；Android shell 仍无权回读 CE/DE 数据目录，页面行为待用户在 OPPO 手工确认。
- **抽屉软边渐变恢复与正式覆盖：** 普通左侧栏的滚动列表恢复上下同画布色软渐变，固定应用标题和底部控件位于渐变之上；硬边遮挡白／灰卡继续禁止。OPPO Find N5 已用同签名 `pm install -r --user 0` 覆盖正式包 `66 / 0.3.0-p10j`；候选与设备实际 `base.apk` 的 SHA-256 均为 `2fc8b939c2b28f817e43559220eb9c05650c55cf58136f223ec0770f7d935e60`，v2/v3 证书仍为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，首次安装时间保持 `2026-08-20 15:15:31`，更新时间为 `2026-08-26 22:44:22`。未卸载、未清数据、未运行 Debug／仪器测试，设备临时 APK 已删除；Android shell 仍无权回读 CE/DE 数据目录，渐变实际观感待用户在 OPPO 手工确认。
- **设置组合卡与顶部渐变加强的正式覆盖：** 设置首页、数据与存储、工作区和关于页的合并卡统一为全宽正常圆角矩形，并以 `4dp` 页面底色横条分隔；抽屉只将顶部软渐变增强为完整端 `1.00`、中段 `0.90 / 0.62 / 0.22`，范围仍为 `128dp`，底部 `112dp` 曲线不变，硬边灰底继续禁止。OPPO Find N5 已用同签名 `pm install -r --user 0` 覆盖正式包 `66 / 0.3.0-p10j`；候选与设备实际 `base.apk` 的 SHA-256 均为 `7085e0e0ab1a192a407faff634e3a578ea14b68d43cea83e555a3e5a859acb69`，候选非 Debug，v2/v3 证书仍为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，首次安装时间保持 `2026-08-20 15:15:31`，更新时间为 `2026-08-26 23:16:41`。未卸载、未清数据、未运行 Debug／仪器测试，设备临时 APK 已删除；Android shell 仍无权回读 CE/DE 数据目录，页面实际观感待用户在 OPPO 手工确认。
- **资料库搜索说明外置与抽屉字体统一的正式覆盖：** “资料库搜索”的固定说明移至开关白卡下方的页面 canvas；左侧栏会话标题、顶部“搜索”与“已计划”统一使用随全局字体缩放的 `16sp / 20sp` 基线。OPPO Find N5 已用同签名 `pm install -r --user 0` 覆盖正式包 `66 / 0.3.0-p10j`；候选与设备实际 `base.apk` 的 SHA-256 均为 `2b2a8d2ce467b7a6215131ca4abc2c97dffad3cc20798c4940f1672b911b6935`，候选非 Debug，v2/v3 证书仍为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，首次安装时间保持 `2026-08-20 15:15:31`，更新时间为 `2026-08-26 23:57:18`。未卸载、未清数据、未运行 Debug／仪器测试，设备临时 APK 已删除；Android shell 仍无权回读 CE/DE 数据目录，页面实际观感待用户在 OPPO 手工确认。
- **抽屉会话标题改为常规字重的正式覆盖：** 左侧栏会话标题保持 `16sp / 20sp`，仅将字重由 `Medium` 改为与主界面正文一致的 `Normal`；搜索、“已计划”、会话行几何、触控和缩放规则均不改。OPPO Find N5 已用同签名 `pm install -r --user 0` 覆盖正式包 `66 / 0.3.0-p10j`；候选与设备实际 `base.apk` 的 SHA-256 均为 `b66e323541b07c227a39311312def453531dd2eb1db139db21eccd84e8266aaa`，候选非 Debug，v2/v3 证书仍为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，首次安装时间保持 `2026-08-20 15:15:31`，更新时间为 `2026-08-27 00:07:28`。未卸载、未清数据、未运行 Debug／仪器测试，设备临时 APK 已删除；Android shell 仍无权回读 CE/DE 数据目录，页面实际观感待用户在 OPPO 手工确认。
- **抽屉固定上下渐变视口的正式覆盖：** 顶部 `128dp` 与底部 `112dp` 渐变均保留，但改为固定在 App 图标／名称下方的列表可视窗口中，不再跟随列表内容滑走；该无命中绘制层不延伸到标题上方，因此不再以不透明灰色在标题区域形成直线硬边。OPPO Find N5 已用同签名 `pm install -r --user 0` 覆盖正式包 `66 / 0.3.0-p10j`；候选与设备实际 `base.apk` 的 SHA-256 均为 `3270ffe8c3d815cf56457589c89b0b64a56903cd8a52187e04846fd90e000d3f`，候选非 Debug，v2/v3 证书仍为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，首次安装时间保持 `2026-08-20 15:15:31`，更新时间为 `2026-08-27 00:17:49`。未卸载、未清数据、未运行 Debug／仪器测试，设备临时 APK 已删除；Android shell 仍无权回读 CE/DE 数据目录，抽屉实际观感待用户在 OPPO 手工确认。
- **删除抽屉独立硬边灰层的正式覆盖：** 已删除普通左侧栏中独立于滚动内容的全屏 `Box` 灰色绘制层；它此前从应用图标下方开始覆盖搜索、已计划和会话列表，形成硬边并冲白内容。当前包不再包含该独立 `Box`；侧栏真实视觉待后续按主界面列表自身渐变结构继续收口。OPPO Find N5 已用同签名 `pm install -r --user 0` 覆盖正式包 `66 / 0.3.0-p10j`；候选与设备实际 `base.apk` 的 SHA-256 均为 `85acc1ebf294d3b57b1a5dce99ed54157b092fcbf94259ac674ab8082fcaa31f`，候选非 Debug，v2/v3 证书仍为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，首次安装时间保持 `2026-08-20 15:15:31`，更新时间为 `2026-08-27 00:34:01`。未卸载、未清数据、未运行 Debug／仪器测试，设备临时 APK 已删除；Android shell 仍无权回读 CE/DE 数据目录。
- **仍需如实保留：** 覆盖与自动合同不等于所有页面的真机视觉验收，也不等于用户真实账号／Provider 调用成功。后续只在用户实际操作时确认当前目标界面的视觉、系统文件预览／浏览器／媒体行为和真实模型结果；不得以旧截图、旧 hash、JVM 测试或安装成功替代。

## 2026-08-26：顶部与 Composer 强阴影对比版（JVM 已验证）

- **修正：** 顶部浮动圆钮／操作胶囊使用 `34dp` 的中性扩散阴影；底部 Composer 采用更重的 `48dp` 阴影。环境／聚光透明度分别提升到 `0x2E`／`0x42`，作为刻意明显的视觉对比版；白色表面、圆角、触控面、按压／水波纹、布局锚点和无描边规则不变。
- **自动验证：** 阴影专属 JVM 合同断言与 Debug Kotlin 编译通过，`git diff --check` 通过。完整 UI 合同类中另有 35 项既有失败，均在本次四个阴影令牌之外；未运行仪器测试、未安装模拟器或 OPPO。当前没有可用隔离 AVD，实际视觉效果待在目标设备手工确认。
- **OPPO 覆盖：** 已用正式 Release `南枫AI.apk` 同签名执行 `pm install -r --user 0`；候选与设备实际 `base.apk` 的 SHA-256 均为 `134953c8060c78ddcd593df7577963bd18285627063d8a4c924d70152b131d49`，签名证书均为 `6d1d…8661f8`。包版本仍为 `66 / 0.3.0-p10j`，首次安装时间仍为 `2026-08-20 15:15:31`；未运行仪器测试、未卸载或清数据。请在 OPPO 上手工打开会话主页确认阴影观感。
- **二次修正与 OPPO 覆盖：** 真机截图确认上述 elevation 阴影不可见后，改用可控 `dropShadow`：顶部为 `8dp` 半径／`3dp` 下偏移／`0x40` 黑色，Composer 为 `12dp` 半径、`1dp` 扩散、`5dp` 下偏移／`0x52` 黑色；圆形、胶囊与无托底规则不变。新的正式包和设备实际 `base.apk` SHA-256 均为 `819ae580cbec745418dd3e02e54690ab3445ac386f671674f4c81678df52bd5a`，签名仍为 `6d1d…8661f8`，版本仍为 `66 / 0.3.0-p10j`，首次安装时间仍为 `2026-08-20 15:15:31`。定向 JVM 阴影断言、Debug Kotlin 编译和 Release 构建通过；未运行仪器测试、未卸载或清数据。待用户在 OPPO 会话主页手工确认实际视觉效果。
- **半径加大与 OPPO 覆盖：** 按用户反馈，顶部 `dropShadow` 半径由 `8dp` 扩至 `18dp`（2.25 倍），Composer 由 `12dp` 扩至 `28dp`（约 2.33 倍）；其余颜色、扩散、下偏移、形状和无托底规则不变。正式包与设备实际 `base.apk` SHA-256 均为 `07f66d0f93eefca9c5536fb24462961d27d819694f3a6fbbfe93bc0aeee3c09d`，签名仍为 `6d1d…8661f8`，版本仍为 `66 / 0.3.0-p10j`，首次安装时间仍为 `2026-08-20 15:15:31`。定向 JVM 阴影断言、Debug Kotlin 编译和 Release 构建通过；未运行仪器测试、未卸载或清数据。待用户在 OPPO 会话主页手工确认实际视觉效果。
- **半径再次翻倍与 OPPO 覆盖：** 按用户明确要求，顶部 `dropShadow` 半径由 `18dp` 翻倍至 `36dp`，Composer 由 `28dp` 翻倍至 `56dp`；颜色、扩散、下偏移、形状和无托底规则不变。正式包与设备实际 `base.apk` SHA-256 均为 `0c5afffb3c988e99298aec1cd1da0a3675027584d9f4037951b61c1c3e7f094e`，签名仍为 `6d1d…8661f8`，版本仍为 `66 / 0.3.0-p10j`，首次安装时间仍为 `2026-08-20 15:15:31`。定向 JVM 阴影断言、Debug Kotlin 编译和 Release 构建通过；未运行仪器测试、未卸载或清数据。待用户在 OPPO 会话主页手工确认实际视觉效果。
- **半径扩大 1.5 倍、阴影降强与最终层级修正：** 顶部半径 `36dp → 54dp`，Composer `56dp → 84dp`；随后先将阴影 alpha 调至原来的 60%，再按用户要求整体再降 30%，最终为顶部 `0x1B`、Composer `0x22`。按钮自身一直是 `ForegroundSurface` 白底；压暗的根因是它在正文内层，无法凭子层 `zIndex` 跨过随后绘制的 Composer 阴影。现将一键到底提升为与 Composer 同一外层、在 Composer 后绘制的覆盖层，并保留 `zIndex(1f)`。Composer 阴影保持 `84dp`／`1dp`／`0x22`，去除 `5dp` 向下偏移，改为上下对称；顶部阴影半径和向下偏移不变。正式包与设备实际 `base.apk` SHA-256 均为 `e7cb5c1626f93a9cd92b35abc575aaf573a27d89537cdc46532256fb93c96b8f`，签名仍为 `6d1d…8661f8`，版本仍为 `66 / 0.3.0-p10j`，首次安装时间仍为 `2026-08-20 15:15:31`；定向 JVM 阴影断言、Debug Kotlin 编译和 Release 构建通过，未运行仪器测试、未卸载或清数据。
- **焦点态输入区下缘收紧：** 截图确认空草稿焦点态在文字下方有多余留白。仅将该全宽文本区的最小高度由 `36dp` 收为 `28dp`，上方 `10dp` 间距不变；发送／停止、模型和附件继续使用独立 `60dp` 操作行，因此文字不会挤占右侧发送按钮或其触控面。正式包与设备实际 `base.apk` SHA-256 均为 `ec28bbf07e2f64f418ca48a24b739b4a97c852ae1e46b7d947a300870bd6e365`，签名仍为 `6d1d…8661f8`，版本仍为 `66 / 0.3.0-p10j`，首次安装时间仍为 `2026-08-20 15:15:31`；焦点态 Composer JVM 合同、Release 构建通过，未运行仪器测试、未卸载或清数据。
- **左侧栏底部操作复用冻结阴影：** 普通、工作和临时侧栏的设置圆钮，以及普通侧栏的新对话胶囊，均复用顶部浮层的 `dropShadow`（`54dp`／`0x1B000000`／向下 `3dp`）。按钮尺寸、颜色、圆形／胶囊形状、按压和业务行为不变，不增加托底。正式包与设备实际 `base.apk` SHA-256 均为 `19bc8763ef3dc09f0943a67645c8aa3fa1726cf9ec480d5702429ed0462446a4`，签名仍为 `6d1d…8661f8`，版本仍为 `66 / 0.3.0-p10j`，首次安装时间仍为 `2026-08-20 15:15:31`；阴影 JVM 合同、Release 构建通过，未运行仪器测试、未卸载或清数据。

## 2026-08-25：附件菜单文字与图标改为纯黑（OPPO 已覆盖）

- **修正：** Composer 左侧加号打开的附件菜单中，“相机”“添加图片和视频”“添加文件”三项的图标和文字统一为纯黑 `#000000`；白色面板、圆角、阴影、行高、命中面和模型选择弹层均不改。
- **验证与设备：** `P6DConversationRowAccessibilityContractsTest`、Debug Kotlin 编译与目标文件 `git diff --check` 通过；正式 Release SHA-256 为 `49f43e25084a8a39b73a915825bbd2e1aec273336eeaafccbcb3fdd2928ef6ec`，已同签名覆盖 OPPO Find N5。安装后回读 `base.apk` 与证书 `6d1d…8661f8` 一致，版本仍为 `66 / 0.3.0-p10j`，首次安装时间不变；未运行仪器测试、未清数据或卸载。

## 2026-08-25：Composer 左侧加号缩短三分之一（OPPO 已覆盖）

- **修正：** 左侧“添加附件”加号的横、竖可见线由 vector 的 `24` 单位收回到 `16` 单位；在原有 `30dp` 承载内，实际可见长度由 `22.5dp` 收为 `15dp`。圆端、圆角、`2.4` 线宽、`48dp` 圆形触控面、布局和阴影均保持不变。
- **验证与设备：** `FBP6041ComposerGlyphContractsTest`、`P6DConversationRowAccessibilityContractsTest` 与 Debug Kotlin 编译通过；Release 完成后 APK SHA-256 为 `fc4f78951278686ae83cd707b7d9e30191468f305a82cda2cab32b342110dd2c`，已同签名覆盖 OPPO Find N5。回读 `base.apk` 哈希和证书 `6d1d…8661f8` 匹配，版本仍为 `66 / 0.3.0-p10j`，首次安装时间不变；未运行仪器测试、未清数据或卸载。

## 2026-08-25：Composer 模型名恢复短名（OPPO 已覆盖）

- **规则：** 底部 Composer 是唯一使用短模型名的入口：`Claude Sonnet 5 → Sonnet 5`、`Claude Opus 5 → Opus 5`、`GPT-5.6 Sol / Terra / Luna → 5.6 Sol / Terra / Luna`，其余已收录模型也移除厂商／家族前缀。普通与临时对话共用这一规则。
- **不变：** 打开 Composer 后的模型选择弹层、设置、调用记录与助手消息归因继续使用完整目录名，例如 `Claude Sonnet 5`、`GPT-5.6 Terra`；真实模型 ID、Provider、路由和存储均不改写。
- **验证与设备：** `P6GModelRouterContractsTest`、`P6DConversationRowAccessibilityContractsTest`、Debug Kotlin 编译与目标文件 `git diff --check` 通过；干净目录正式 Release 的 SHA-256 为 `11b24ec26c93bb507e3ccc851533db99e09fbfbbd1ef2581c9cc950efe0f65e1`，已同签名覆盖 OPPO Find N5。安装后回读 `base.apk` 哈希与证书 `6d1d…8661f8` 均匹配，版本仍为 `66 / 0.3.0-p10j`，首次安装时间未改变；未运行仪器测试、未清数据或卸载。

## 2026-08-25：正式覆盖后的冷启动崩溃恢复（OPPO 已验证）

- **根因与修正：** 费用 Schema 46→47 已创建 `costTotalMicros + recordedAtEpochMs` 索引，但实体声明遗漏该索引，Room 校验将已升级的本机库判为不一致；补齐实体索引声明及新库／迁移回归断言。随后发现空会话在“对话提醒建议”开启时，`indexOfLast` 返回 `-1` 仍被作为消息下标读取；现明确将“没有已完成助手消息”返回为无建议，避免可选尾部控件阻断整个聊天页 Compose。
- **自动验证：** `AssistantResponseModelAttributionRoomContractsTest`、`AndroidUserEntryAuditContractsTest` 与 Debug Kotlin 编译通过；随后从干净目录完成正式 `:app:assembleRelease`。候选 APK SHA-256 为 `7888aca58dfdbab71a7c9e82662a72cab1167bfa2b8bf38465d5e056bb78d463`，签名证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **OPPO 覆盖与冷启动：** 已使用同签名 `pm install -r --user 0` 覆盖 OPPO Find N5；安装后回读 `base.apk` 哈希和证书均匹配，版本仍为 `66 / 0.3.0-p10j`，首次安装时间仍为 `2026-08-20 15:15:31`。一次真实启动后 `com.nanzhufeng.ai` 进程 PID `21554` 保持存活，Activity 记录的 `base.apk` 指向上述新包；未运行仪器测试、未清数据、未卸载。

## 2026-08-25：全档位材料证据优先分析（JVM 已验证，真实服务待验）

- **修正：** `EvidenceFirstAnalysisPolicy` 成为普通聊天共享 owner：附件消息只要用户明确要求分析、解读、核实、判断或评估，就要求所有已接入模型按“直接结论 → 已核验事实/材料观点/推理 → 反例或条件 → 风险与下一步”输出，不再让 Claude Sonnet 5 等普通档退化成截图逐段整理。OpenRouter 的任意模型（含 Claude Sonnet 5、Claude Opus 5）统一启用 `openrouter:web_search`；千问所有档位统一使用可携带材料的 Chat Completions 联网检索；原有深度联网路由不降级。
- **边界：** 普通看图、转写、翻译或未明确要求分析的附件消息不自动联网。DeepSeek 尚无“材料 + Responses 网页检索”序列化 owner，因此不会伪造联网；该组合如仍可走普通材料路由，系统指令会明确未取得实时来源。共享规则提高任务标准，但不虚构不同模型完全相同的推理能力。调用记录继续如实写入实际检索路由，公开来源只接受 Provider 结构化返回。
- **自动验证与设备：** `EvidenceFirstAnalysisPolicyTest`、`ProviderAdapterContractsTest`、`P3JNormalChatExplicitEgressContractsTest`、`AndroidUserEntryAuditContractsTest`、Debug Kotlin 编译、`git diff --check` 与正式 `:app:assembleRelease` 已通过。候选 Release（SHA-256 `ab819411…46aac6da`）于 2026-08-25 17:23 以同签名 `pm install -r --user 0` 覆盖到 OPPO Find N5；安装后实际 `base.apk` 哈希、v2/v3 证书 `6d1d…8661f8` 与候选一致，版本仍为 `66 / 0.3.0-p10j`，首次安装时间保持 `2026-08-20 15:15:31`。未调用用户 Key，未运行仪器测试；仍需用户手动以 Claude Sonnet 5 和一张含可核验市场/数据主张的截图确认真实检索、来源 chip 与回答质量。

## 2026-08-25：Composer 添加附件圆润加号加长（JVM 已验证）

- **修正：** Composer 左侧“添加附件”保留现有 `48dp` 圆形触控面和行为，但将可见加号替换为专用圆端／圆角 vector；横、竖线各为旧 Material glyph 可见长度的约两倍，`30dp` 承载中的可见线长为 `22.5dp`。不增加外圈、托盘、硬边或额外阴影。
- **自动验证：** `FBP6041ComposerGlyphContractsTest`、`P6DConversationRowAccessibilityContractsTest`、Debug Kotlin 编译与 `git diff --check` 已通过。未运行仪器测试、未安装模拟器或 OPPO；外屏／内屏观感待手工确认。

## 2026-08-25：普通聊天 OpenRouter 费用与用量（JVM 已验证，真实服务待验）

- **实现：** 普通聊天现将回包 `usage.cost` 以 USD micro 写入对应助手消息的本地归属记录；非流式完整回包与流式最后 SSE 事件都经同一 decoder 收集。OpenRouter 真实金额优先，合法 `$0` 保留为零金额；服务未给金额时，仅对用户方案中有校准价目的模型以 `≈ $…（估算）` 显示，未知不伪造为零。每条助手消息底部在具体模型名后显示同一金额；设置 → AI 模型服务新增“费用与用量”，按消息列出 Token、实际/估算来源及本机累计。
- **数据与边界：** Schema 46→47 仅为 `assistant_response_model_attributions` 增加 Token、费用、版本与来源字段/索引，迁移不改写历史消息、模型归属或费用。所有金额使用整数 microUSD；不保存 Prompt、回复、附件、Key、原始 Provider JSON 或 generation ID。本期没有调用用户 Key 的额外账单、generation 补查、在线 `/models` 价目表、人民币换算或对账接口。
- **自动验证：** Debug Kotlin 编译、`ProviderAdapterContractsTest`、`AssistantResponseModelAttributionRoomContractsTest`、`P5DLocalBackupRestoreContractsTest`、`P6FTranscriptPresentationContractsTest`、`P6DConversationRowAccessibilityContractsTest`、`ModelSettingsUiContractsTest`、`AndroidUserEntryAuditContractsTest`、`SettingsUiSimplificationContractsTest` 与 `git diff --check` 已通过。未运行仪器测试、未安装到模拟器或 OPPO；真实 OpenRouter 实际金额必须在用户自行发起一条真实对话后确认。

## 2026-08-25：对话底部模型名与选择面统一（JVM 已验证）

- **修正：** Composer、普通／临时对话的模型入口和助手消息页脚统一使用中心模型目录的具体显示名；例如固定显示 `GPT-5.6 Terra`，不再缩写为 `5.6 Terra`，比较模式显示真实的两项模型名而非“对比”。助手归因页脚不再拼接 `模型：`、`OpenRouter`、千问、DeepSeek 或“官方实时检索”等路由说明。旧消息中已有的 `模型：… · OpenRouter`、`Google:` 或接收方尾缀也会实时规范为同一目录名，持久化的原始 model ID、Provider 与接收方事实不改写。
- **边界：** 附件发送前的“发送至：服务商 · 模型”说明、调用记录、诊断与外发确认仍保留服务商／接收方，这是隐私、费用和可追溯事实，不属于底部模型名称。
- **自动验证：** `P6GModelRouterContractsTest`、`P6FTranscriptPresentationContractsTest`、`P6DConversationRowAccessibilityContractsTest`、Debug Kotlin 编译与目标文件 `git diff --check` 通过。未重建 Release、未安装或操作 OPPO；已安装设备上的实际模型标签仍需经下一次用户授权的保留数据正式覆盖后确认。

## 2026-08-25：Android 系统长截图恢复正文候选（JVM 已验证）

- **根因与修正：** `ModalNavigationDrawer` 在关闭时只是移出画布，其内部侧栏仍保有纵向滚动语义；Compose 的 Scroll Capture 候选选择不会可靠排除该屏外节点，ColorOS 可能因此把侧栏而非正文 `LazyColumn` 作为捕获对象，导致聊天区长截图直接不可用。现将关闭侧栏明确标记为不可访问语义，从系统捕获发现树排除；捕获开始后仍暂停聊天／工作区自动跟随最新消息，并临时去除仅供屏幕观感的正文边缘遮罩，避免它覆盖每个拼接片段。侧栏打开、普通阅读、辅助功能可访问状态及截图结束后的既有视觉过渡不变。
- **自动验证：** 离线 `P6GUnifiedChatFirstUiContractsTest`、Debug Kotlin 编译与目标文件 `git diff --check` 通过。未运行仪器测试、未安装到模拟器或 OPPO；ColorOS 实机仍需在一条超过一屏的真实对话中手动确认截图工具出现“长截图”、从正文而非侧栏开始并可拼接到末尾。

## 2026-08-25：对话管理拆分已归档与回收站（JVM 已验证）

- **实现：** 设置 → 对话管理改为清晰的管理主页：已归档与回收站各自为独立白卡入口，并进入各自独立的二级页面。两页只读取对应 lifecycle projection，分别提供标题、语义说明、空态、会话列表和“恢复”操作；当前对话导出也另成一组，不再与生命周期列表混排。
- **语义边界：** 归档仍不删除消息或附件；回收站仍不物理删除消息树。恢复归档调用既有 `UNARCHIVE`，恢复回收站调用既有 `RESTORE_DELETED`；离开设置时依旧回到普通活动列表，不改变 Room、搜索或导出事实。
- **自动验证：** 离线 `SettingsUiSimplificationContractsTest`、`P6DConversationRowAccessibilityContractsTest` 与 Debug Kotlin 编译通过。未运行仪器测试、未安装到模拟器或 OPPO；目标设备的窄屏、内屏和大字体实际排版仍待手工确认。

## 2026-08-25：Android 启动器图标枫叶改为白色实心（Release 已验证）

- **实现：** Android 新增 `nanfeng_ai_launcher_q_rounded_scale90_leaf_filled_source.png` 母版；枫叶由白色描边改为白色实心，AI 字样同步改为橙色，以保持白叶上的可读性。`drawable-nodpi` 前景和 mdpi—xxxhdpi 的普通／圆形 legacy 图标均从同一母版导出；Manifest、adaptive 层、圆角、比例和透明角不变，Desktop 不受影响。
- **自动验证：** `audit_launcher_icon.py` 已确认新母版与前景哈希一致、adaptive 双层、普通／圆形 launcher 引用及所有 legacy 密度资源完整；正式 `:app:assembleRelease` 成功，APK SHA-256 为 `f30c0201c19f50d6a7466a1d85b1cbb8da5d6a16d73e50b1e777f1a4c7033377`，v2/v3 证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。未安装到 OPPO；真实 ColorOS Launcher 截图仍待用户授权的保留数据正式覆盖后取得，不能以预览或资源尺寸替代。

## 2026-08-25：设置新增通知与提醒独立控制（JVM 已验证）

- **实现：** 设置首页新增“通知与提醒”二级入口，并将三个既有提醒 surface 分别接到本机持久化开关：关闭“计划监控结果通知”后，WorkManager 仍执行计划并落库结果，但不会发送系统通知；关闭“对话提醒建议”后，不再显示高价值完成对话尾部的“添加提醒 / 监控”；关闭“对话未读提醒”后，不再显示左侧对话列表的橙色标记。默认全开以保持升级前既有行为，未读项明确只是列表标记，并非伪造系统推送。
- **功能审阅：** 已按本轮要求从设置 → 功能审阅移除通知与提醒、计划监控与对话提醒两段重复文字提示；可操作的“通知与提醒”页是唯一用户入口。跨端 owner 状态仍保留在 `ANDROID_DESKTOP_USER_ENTRY_AUDIT_20260816.md`，Desktop 未实现故不显示伪开关。
- **自动验证：** 离线 `NotificationReminderSettingsContractsTest`、`SettingsUiSimplificationContractsTest`、`AndroidUserEntryAuditContractsTest`、Debug Kotlin 编译与目标文件 `git diff --check` 通过。未运行仪器测试、未重建 Release、未安装到模拟器或 OPPO；系统通知权限、后台实际到点执行与折叠屏实际触控仍待目标设备手工确认。

## 2026-08-25：聊天日期分隔线与灰底统一（JVM 已验证）

- **修正：** `TranscriptDateDivider` 的左右线继续共用同一 `SubtleDivider`，其颜色从近白 `#FAFBFA` 收敛为现有中性细边 `NeutralBorder = #D8DEDA`；首条日期和后续跨日期分隔线不再在加深灰底上显白。
- **边界：** 只改变日期分隔线颜色，不改日期文本、行高、间距、消息排序、滚动或顶部/底部渐变。
- **自动验证：** 离线 `P6DConversationRowAccessibilityContractsTest`、Debug Kotlin 编译与目标文件 `git diff --check` 通过；未重建 Release、未安装或操作 OPPO，日期线的目标视口视觉仍待手工确认。

## 2026-08-25：顶部与 Composer 阴影分级加强（JVM 已验证）

- **修正：** 顶部悬浮圆钮和右上操作胶囊的宽幅中性阴影由 `10dp` 提升至 `30dp`；底部 Composer 继续使用相同圆角与低透明度中性灰，但单独提升至 `42dp`，获得更稳的悬浮层级。
- **边界：** 没有改动白色表面、圆角、触控面、按压/水波纹、布局锚点或静态 Material 阴影；不新增黑边、托底或硬阴影。
- **自动验证：** 离线 `P6DConversationRowAccessibilityContractsTest`、Debug Kotlin 编译与目标文件 `git diff --check` 通过；未重建 Release、未安装或操作 OPPO，阴影强度和内外屏视觉仍待目标设备手工确认。

## 2026-08-25：Composer 与模型选择面分离 GPT 名称（JVM 已验证）

- **修正：** Composer 的窄模型位继续显示 `5.6 Terra / Sol / Luna`；打开后的模型选择二级菜单、模型目录和设置列表恢复完整 `GPT-5.6 Terra / Sol / Luna`。两处不再共用一条会导致弹窗缩写的展示名规则。
- **边界：** 请求 ID、路由、Provider、持久化归因和当前选择的逻辑 ID 不变；消息页脚仍使用紧凑元信息，不扩大其阅读噪音。
- **自动验证：** 离线 `P6GModelRouterContractsTest`、`OpenRouterColdStartResolverContractsTest`、`P6FTranscriptPresentationContractsTest`、`P6DConversationRowAccessibilityContractsTest` 与 Debug Kotlin 编译通过；未重建 Release、未安装或操作 OPPO，弹窗的最终视觉仍待目标设备手工确认。

## 2026-08-25：会话自动标题改为完整概述（JVM 已验证）

- **修正：** 自动标题不再按 30 显示宽度截断并存储省略号；优先取完成回答中的完整主题标题，缺失时将首条用户任务整理为“对象 + 任务”。悬空转折、省略语句和纯首句复制均被拒绝；例如“图里的数据是真的，但有几个重要…”会归为“图表数据真实性与关键问题核对”。
- **历史边界：** 已有标题不批量重写，避免误覆盖用户手动重命名；抽屉单行若因物理宽度裁剪，仅属于渲染，不污染 Room 标题。
- **自动验证：** 离线 `P6IConversationAutoTitleContractsTest`、Debug Kotlin 编译与目标文件 `git diff --check` 通过；未操作 OPPO，尚未覆盖安装。

## 2026-08-25：来源网站弹窗扩展为全宽阅读面（JVM 已验证）

- **修正：** “来源网站”不再使用 Material 默认窄宽 `AlertDialog`；改为共享的全宽 Dialog 壳，左右只留 `16dp` 安全边距，来源卡获得完整可用宽度。
- **边界：** 纯白表面、24dp 圆角、最多 420dp 的内部来源滚动、点外关闭、内侧边缘关闭、系统返回、关闭按钮和 `ACTION_VIEW`／`CATEGORY_BROWSABLE` 外部浏览器打开路径不变。
- **自动验证：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 Debug Kotlin 编译通过；尚未在 OPPO 目标视口进行视觉确认，未安装新包。

## 2026-08-25：Composer 停止生成图标改为白色实心方块（JVM 已验证）

- **修正：** 右下角橙色发送圆面在存在可停止的本地生成时，由空心 `Outlined.Stop` 改为同尺寸白色实心 `Filled.Stop`；避免橙色透过方框中心而误读成加载中的空心标记。
- **边界：** 只改变停止状态的 glyph 绘制；`ConversationRuntimeState` 的非终态判断、停止生成点击、48dp 命中面、36dp 橙色圆面、普通提交时的加载环及无障碍描述均不变。
- **自动验证：** 离线 `P6DConversationRowAccessibilityContractsTest`、`FBP6041ComposerGlyphContractsTest` 与 Debug Kotlin 编译通过；尚未安装或在 OPPO 真机触发真实生成状态。

## 2026-08-25：Android 对话右侧滚动位置条恢复为可见（JVM 已验证）

- **根因与修正：** 右侧 `TranscriptScrollIndicator` 本身没有被删除，但被 `offset(x = 4dp)` 推出会话画布的右缘；在当前系统安全边距下，细滑块可能被裁掉。现收回 `LazyColumn` 专属的 `6dp` 右侧轨道，并将轨道／滑块透明度由 `5%／28%` 调整为 `8%／38%`，保持细、低对比但持续可辨。
- **边界：** 仍只由既有 `LazyListState` 驱动；无可滚动内容时隐藏，正文、附件、操作目标、左右 `24dp` 视觉留白、Composer 和滚动手势均不改变。
- **自动验证：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 Debug Kotlin 编译通过；尚未在 OPPO 实机确认长对话中的可见性，也未安装新包。

## 2026-08-25：DeepSeek V4 Pro 恢复官方实时网页检索（待真实 Key 验证）

- **根因与修正：** 旧实现停留在 DeepSeek `/chat/completions`，并沿用当时“不支持”的菜单与计划任务规则。DeepSeek 官方现已将 `deepseek-v4-pro` 纳入 `/responses`，且支持服务端 `web_search`；DeepSeek 深度选择现在直连其自身 `/responses`，声明并强制 `web_search`，不再把 DeepSeek 的模型 ID 转发到千问。
- **可见与持久化：** 深度模型菜单改为“DeepSeek · 官方实时联网检索”；普通会话、调用归属、诊断和已计划监控都记录 DeepSeek 作为实际接收方。Provider 返回的公开来源仍只从结构化响应写入现有来源卡；不从模型正文猜测 URL。
- **协议边界：** DeepSeek Responses 的 SSE 是语义事件协议，当前 transport 仅消费 Chat Completions SSE；本次联网请求因此明确使用非流式 `/responses`，待全量语义 SSE 解码实现前不会假装流式。未使用用户 Key 发起真实请求，需由用户手工以已配置 DeepSeek Key 核验一次联网回答与来源。

## 2026-08-25：Composer 模型入口收窄为 88dp（JVM/Release 已验证）

- **修正：** 底部 Composer 的模型选择入口由 `120dp` 收窄为固定 `88dp`；短名如 `3.7 Flash` 不再占据输入栏右侧过大的胶囊区域，按钮仍保留 `48dp` 高触控面与完整模型选择逻辑。
- **自动验证：** 离线 `P6DConversationRowAccessibilityContractsTest`、Debug Kotlin 编译、`git diff --check` 与正式 `:app:assembleRelease` 通过。未运行仪器测试、未安装到 OPPO；实际外屏/内屏的文字余量与点按观感仍待手工确认。

## 2026-08-25：模型显示名移除冗余 GPT 前缀（JVM/Release 已验证）

- **修正：** 用户界面中的 `GPT-5.6 Terra / Sol / Luna` 统一显示为 `5.6 Terra / Sol / Luna`；覆盖模型选择、Composer、Auto/临时兜底、助手页脚、历史尝试和计划说明。旧消息的已保存归因在渲染时同样去除前缀。
- **边界：** `openai/gpt-5.6-*` 的精确请求 ID、预设 ID 与持久化原始归因不改，确保此显示修复不会影响联网请求或历史事实。
- **自动验证：** 离线 `OpenRouterColdStartResolverContractsTest`、`P6GModelRouterContractsTest`、`P6FTranscriptPresentationContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 通过，覆盖目录显示、选择顺序、旧页脚归因与 Composer 规则；Debug Kotlin 编译、`git diff --check` 与正式 `:app:assembleRelease` 成功。未运行仪器测试、未安装到 OPPO；目标设备实际页面仍待手工确认。

## 2026-08-25：个性化自定义指令扩展为 8,000 字符（JVM/Release 已验证）

- **修正：** 直接在“设置 → 个性化”编辑的“回答偏好与自定义指令”上限由 `2,000` 提升为 `8,000` Unicode 字符，输入框显示实时计数。完整保存内容只在用户开启个性化后拼入普通模型调用的系统指令；不会被本机摘要、静默截断，也不会进入调用审计或诊断。
- **范围：** 本机直接编辑字段与第三方 ZIP 的严格版本化资料导入是两条独立路径；后者的格式/读取边界不因本次本机设置调整而放宽。
- **自动验证：** 离线 `AssistantExperienceSettingsContractsTest` 与 `SettingsUiSimplificationContractsTest` 通过，覆盖 8,000 字符完整模型指令和界面额度提示；Debug Kotlin 编译、`git diff --check` 与正式 `:app:assembleRelease` 成功。未运行仪器测试、未安装到 OPPO；实际长文本输入、保存后重开与真实模型响应仍待用户在目标设备手工确认。

> 面向下一位架构负责人的完整现状、联网配置、导入/导出边界与重构顺序见：[南枫AI完整开发档案](南枫AI完整开发档案.md)。它不含任何凭据、对话正文或设备私有数据。

> **当前 UI 读取门（2026-08-26）：** 下方按时间保留历史实现与验收记录，旧段落中的颜色、行高、顶栏、抽屉、搜索、Markdown、来源、表格、Composer、设置、弹窗、卡片和图标数值不能当作当前规则重用。所有 Android `ConversationWorkspace` 的可见规则只读取 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)；所有 Android 设置及其二级至四级页面的可见规则只读取 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)；领域 owner 继续按对应合同执行。

## 2026-08-25：普通聊天后台继续生成改为真实前台服务 owner（JVM/Release 已验证）

- **根因：** 旧 `NormalChatGenerationForegroundService` 只展示通知；普通聊天的 `execute/retry` 仍由 `ConversationFoundationViewModel.viewModelScope` 执行，后台通知不等于请求由服务持有。
- **修复：** 草稿先安全落库，服务随后作为实际网络、取消和流式持久化 owner；仅传递会话 opaque ID 与 SEND/RETRY。服务结束只广播安全状态，Activity 回前台或收到状态后从 Room 重投影。停止在 socket 建立前也会被记录并应用；同进程 Activity 重建不会把服务仍在执行的 Attempt 错标为 PROCESS_INTERRUPTED。
- **失败边界：** 系统杀进程、强制停止和真实网络中断仍不会伪造“继续完成”或自动重发；不确定结果保留 UNKNOWN，用户才能以原 idempotency key 明确重试。通知、Intent、广播、调用日志均不含正文、附件、回复或 Key。
- **UI：** 发送失败卡的标题、说明与重试按钮已统一以同一中轴居中。
- **自动验证：** 离线 `NormalChatBackgroundExecutionContractsTest`、`P3JNormalChatExplicitEgressContractsTest`、`P5BAndroidExecutionManifestContractsTest`、正式 `:app:assembleRelease` 与变更文件 `git diff --check` 通过；候选 APK v2/v3 签名证书为 `6d1d56ec…e198d8661f8`。未运行仪器测试、未操作 OPPO，未使用用户 Key 进行真实 Provider 或后台切换验证。

## 2026-08-25：会话／工作／临时会话共用灰底与 Composer 锚点（JVM/Release 已验证）

- **统一画布：** `ConversationWorkspaceCanvas = #F2F2F2` 是普通对话、工作区和临时对话唯一的中性灰画布；不再让普通对话单独显示白底，也不修改白色输入框、顶栏按钮或弹层。
- **临时对话 Composer：** 临时会话底部输入框现与正式对话使用同一 `navigationBarsPadding + imePadding + 18dp 水平／6dp 底部` 锚点；键盘打开时会随 IME 一起上移，不再固定在手势区。
- **自动验证：** 离线 `P6DConversationRowAccessibilityContractsTest`、`P6GUnifiedChatFirstUiContractsTest` 与 Debug Kotlin 编译通过；正式 `:app:assembleRelease` 已完成。未进行新的设备覆盖或真机视觉验收，需由用户确认灰度和键盘状态的实际观感。

## 2026-08-25：Android 音频正文短条与稳定拖动（JVM/Release 已验证）

- **几何：** 正文音频预览恢复为固定 `176×42dp` 紧凑短条；完整本地播放器继续使用横向 `20dp` 留白的固定居中窗口，并压缩播放核心的垂直占用。正文预览不得因完整播放器的尺寸要求而拉长。
- **拖动与稳定性：** 音频进度轨可点按或拖动；拖动期间只保存临时位置，播放轮询不覆盖它，松手才对本地 `MediaPlayer` 提交一次 seek。音频弹层的下载、分享、关闭操作关闭自动隐藏，正文拖动不再切换顶栏 chrome。
- **复查收口：** 旧版同时叠加点击与拖动识别器，短拖可能产生竞争；现改为单一 pointer owner，在抬手才提交 seek、取消则恢复真实位置。自动播放文案已与实际 `MediaPlayer` 准备完成后的行为一致；右上三个动作改为单一 `44/68/44dp` 固定操作组，标题保留独立间隔。
- **自动验证：** 离线 `P6F2EAudioAndTextPreviewUiContractsTest`、`P6DConversationRowAccessibilityContractsTest` 与 `P6F2DVideoPreviewUiContractsTest` 通过，包含 Debug Kotlin 编译和变更文件 `git diff --check`。正式 `:app:assembleRelease` 成功，候选包 v2/v3 签名已核验。未运行仪器测试、未操作模拟器或 OPPO；拖动手感、窄屏动作命中与实际播放器解码仍待目标设备手工确认。

## 2026-08-25：Android 正文文本附件可读封面（JVM/Release 已验证）

- **修正：** `text/plain`、Markdown、JSON 与 CSV 的正文附件卡复用已校验、受限的 UTF-8 首段投影，以 TXT／MD／JSON／CSV 标识和至多四行真实内容呈现；不再把文本走入通用空白缩略图。
- **失败状态：** 无法读取、缺失或校验不一致时仍保留格式标识并显示具体本地原因；不读取路径、不执行任何文本内容，也不把全文放进卡片。
- **自动验证：** 离线 `P6F2EAudioAndTextPreviewContractsTest`、`P6F2EAudioAndTextPreviewUiContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 通过，包含 Debug Kotlin 编译和变更文件 `git diff --check`。正式 `:app:assembleRelease` 成功；候选包 v2/v3 签名已核验。未运行仪器测试、未操作模拟器或 OPPO，正文卡实际视觉仍待目标设备手工确认。

## 2026-08-25：普通聊天联网回归与多模态附件直传（JVM 已验证）

- **根因与恢复：** “禁用 Pro”改动曾把旧的 OpenRouter Terra/Sol 缓存映射整体拒绝；普通文本因此会在发请求前触发目录重校验，网络或目录暂不可达时看起来像所有模型失效。现在只对该已知旧 `-pro` 缓存拼写规范化为精确的标准 `openai/gpt-5.6-terra` / `openai/gpt-5.6-sol` 请求 ID，未知近似名称继续拒绝；不再让显示/迁移规则阻断现有普通聊天联网链路。
- **附件外发：** 当前私有副本、大小、数量和 MIME 安全门保持不变；普通聊天将已接受的图片、PDF、视频、MP3/WAV/M4A 音频及安全文本文件统一带到适配层。OpenRouter 音频使用真实 `input_audio`（原始 Base64 与格式）负载，图片/PDF/视频/文件也不再被本地 capability 布尔值提前拒绝。DeepSeek 的官方直连仍没有已实现的多模态请求合同，不能伪装为已支持；Qwen 仍由其兼容 Chat Completions 适配器实际接收并由服务端给出模型级结果。
- **失败可见性：** Provider 失败后，Composer 下方继续保留明确错误；若本机存在可安全重试的发送记录，恢复卡仍是唯一重试入口，不再只留下空白 Assistant 占位。
- **自动验证：** 离线 `ProviderAdapterContractsTest`、`AttachmentReferenceInstructionTest`、`P2JOpenRouterRegistryContractsTest`、`P3JNormalChatExplicitEgressContractsTest`、`P6GModelRouterContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，包含 Debug Kotlin 编译和定向 `git diff --check`。未运行仪器测试、未操作模拟器或 OPPO，未以用户 Key 发起真实 Provider 请求；重新安装正式包后需用一条纯文本和一条 MP3 由用户手工确认真实服务响应。

## 2026-08-25：模型联网路由、来源与附件能力收口（JVM 已验证）

- **DeepSeek 路由真值（已被本页顶部 2026-08-25 更新）：** DeepSeek V4 Pro 仍绝不携带到千问 `/responses`，但已改用 DeepSeek 自身 `/responses` 的官方 `web_search`；深度菜单和已计划监控均走该直接路线并记录 DeepSeek 为接收方。
- **可核验来源：** OpenRouter 联网请求改为读取带 `url_citation` 的最终响应；千问深度联网统一使用 Responses 并声明 `web_search` 工具，不向默认思考的 Qwen3.8-Max 发送不兼容的 `tool_choice=required`。Provider 返回的公开来源被规范成 Markdown 链接写入会话回答与计划简报，复用现有会话来源 chip，不保存 Provider 原始 JSON。
- **Qwen 文本附件：** Markdown、TXT、JSON、CSV 不再错误编码为 Qwen Chat Completions 不接受的 `file` content part；其完整 UTF-8 正文以单独 user text message 发给 Qwen。图片/视频仍走各自的原始多模态 part；不能被 Qwen 官方端点接收的类型继续返回可见 Provider/能力错误，不伪造成功。
- **附件路由：** 自动选择补充音频/普通文件事实：有已知能力匹配模型时优先选择；没有已知能力时不做本地假性限制，仍完整发送并将 Provider 拒绝转成可见错误。图片、PDF、视频原有完整原件发送规则不变。
- **目录可用性：** OpenRouter 没有任何本机目录快照且公开目录 GET 暂时失败时，已固定的标准文本预设使用精确的冷启动 ID 发出；不猜测 Pro/Fast/近似模型、不用于附件、不覆盖已有目录映射。目录一旦存在但无法解析，仍保持 fail-closed，避免把过期模型静默改发。
- **验证边界：** 离线 `ProviderAdapterContractsTest`、`CapabilityAwareAutoModelRouterTest`、`OpenRouterColdStartResolverContractsTest`、`P3JNormalChatExplicitEgressContractsTest`、`P2JOpenRouterRegistryContractsTest`、`ModelProfileRefreshContractsTest` 与 `ScheduledMonitorRoomContractsTest` 通过。未以用户 Key 发起真实 Provider 请求，尚需用已授权测试 Key 分别复验 OpenRouter、千问与 DeepSeek 的真实文字、联网来源和失败提示。

## 2026-08-25：Android 对话本地视频可拖动进度（JVM 已验证）

- **根因与修正：** 视频底部时间轴此前只是绘制进度，顶层单一手势 owner 只处理单击跳转；持续拖动没有被识别。现拖动时间轴时只维护临时 `scrubPositionMillis` 与时间预览，手指松开时才对现有 `VideoView` 提交一次 seek，播放轮询在拖动中不会覆盖该预览位置；取消拖动不跳转。
- **边界：** 继续仅播放既有私有副本的本地视频。中央播放/暂停、双击切换、顶栏、边缘退出、下载/分享和关闭位置保存不变；不增加入口、不上传、不外发、不后台播放。视频实际拖动手感、解码表现与折叠屏状态连续性仍需在目标设备人工验证。
- **自动验证：** 离线 `P6F2DVideoPreviewUiContractsTest` 与 `AttachmentTransferCompatibilityContractsTest` 通过，包含 Debug Kotlin 编译。未运行仪器测试、未操作模拟器或 OPPO。

## 2026-08-25：Android 已计划真实新闻监控与对话尾部提醒（JVM/Release 已验证）

- **真实 owner：** Schema 46 新增 `scheduled_monitor_tasks` / `scheduled_monitor_runs`；`ScheduledMonitorRepository` 是唯一持久化 owner，`ScheduledMonitorExecutor` 是唯一后台模型外发 owner，`AndroidScheduledMonitorScheduler` 为每个 ACTIVE 任务维持一条联网受限的唯一 WorkManager 请求。创建、完成、失败、暂停、删除都经同一 repository/scheduler；Worker 在下一次排程和通知前重新回读任务，避免用户运行中暂停/删除仍被旧活跃快照继续排程。
- **外发与成本边界：** 用户只可从左栏搜索下的“已计划”手动添加，或从最后一条完成 Assistant 回答后的“添加提醒 / 监控”进入表单。后者只保留会话 opaque ID/标题作本机来源，绝不读取或发送消息、附件、Memory 或 Context。每次只发送用户在表单确认的任务名称/监控要求，使用 GPT-5.6 Terra 标准档与实际服务商官方网页检索；服务、凭据、模型或响应失败均写入本机安全错误码，不能伪造简报。结果通知需要 Android 通知权限，通知正文不含监控结果。
- **用户界面与跨端：** “已计划”打开独立全屏计划页，可查看结果、暂停、恢复、删除与手动添加；Desktop 尚没有同等后台/网络/通知 owner，因此只在功能审阅登记为待实现，不展示伪入口。Android 当前会话合同、功能审阅、架构所有权表、领域规则和决策日志已同步。
- **自动验证：** 离线 `:app:compileDebugKotlin`、`AndroidUserEntryAuditContractsTest`、`ScheduledMonitorRoomContractsTest` 与正式 `:app:assembleRelease --rerun-tasks` 通过；未运行仪器测试、未操作模拟器或 OPPO，未验证真实 Provider、WorkManager 触发、系统通知授权或真机视觉。Release 产物位于 `app/build/outputs/apk/release/南枫AI.apk`，尚未安装。

## 2026-08-25：Android 计划监控建议收紧与要求自动整理（JVM 已验证）

- **行为：** `ScheduledMonitorSuggestionPolicy` 只在当前完整的一问一答具备明确跟踪信号且命中高价值主题时，才允许会话尾部显示“添加提醒 / 监控”。普通开发、闲聊、一次性解释和未给出价格/估值阈值的个股讨论均不显示。已命中时仅在设备本地把当前问答整理成 20 字内的概括名称、具体可执行的监控要求和小时/每日频率；用户仍可编辑、取消或手动创建。
- **模板：** 内建每日重点简报、具体股票/指数阈值、Codex 额度与官方更新、AI 需求/电力/Agent、中美科技政策、海外账号、宁苏居住、智能汽车与 AI 影视/VFX 模板。每日简报的中美科技政策要求写明官方/媒体/生效三类时间、法律状态、合规期、适用对象和分层产业影响，优先美国官方一手来源。
- **隐私与外发：** 建议阶段不调用模型、不落库对话正文、不读取附件、Memory 或 Context；创建后仍只有用户保存确认的任务名称和监控要求可进入 `ScheduledMonitorExecutor`。没有真实 Provider/Key 测试，本次未验证实际网页检索、WorkManager 触发、系统通知或真机视觉。
- **自动验证：** `ScheduledMonitorSuggestionPolicyTest`（4 tests）和 `ScheduledMonitorRoomContractsTest` 离线通过；未运行仪器测试，未操作模拟器或 OPPO。

## 2026-08-25：Android 模型设置拆分运行信息入口（JVM 已验证）

- **界面：** `ModelSettingsDialog` 的配置页继续保留自动路由、三服务商、预设、开关、目录核验、用户主动连接测试和本机 Key，不删除任何配置能力；调用记录、上下文选材、连接诊断从长表单中移出，分别以明确入口进入。调用记录复用已有 `InvocationLedger` 的独立 owner；上下文选材显示最近 50 条去内容化审计，标题改为简洁的“上下文选材”。
- **隐私：** 调用记录、上下文选材和诊断均只读取现有本机安全元数据；Key、对话正文、附件、提示词、完整响应和原始请求不会进入任一页面。调用记录入口先关闭设置页再打开既有记录页，避免双层弹窗与不稳定布局。
- **自动验证：** Debug Kotlin 编译和 `AndroidUserEntryAuditContractsTest` 离线通过；尚未做目标设备视觉验收，未操作模拟器或 OPPO。

## 2026-08-24：Android 对话正文四边渐隐磨砂（已覆盖并以长对话真机画面确认）

- **视觉规则：** 会话正文仅在物理屏幕的**顶部横向区域**与**底部横向区域**使用独立的 `108dp` 模糊图层：顶部 `76dp`、底部 `64dp`（收在 Composer／手势区），以互补的 `DstOut`／`DstIn` 渐隐蒙版连续交接，清晰正文退场、模糊正文接替。左右从顶部到底部都属于中间阅读区，必须完整清晰，禁止任何纵向侧边雾带；顶栏按钮、滚动指示、跳至最新、Composer 和空白画布不受影响。
- **根因与收口：** 最初只是向完整清晰正文叠加模糊副本，因此锐利字形会遮住模糊；随后又在同一绘制帧将图层效果复位，RenderThread 实际提交成了清晰层。现清晰层和模糊层分离，模糊层不在绘制帧中复位；且绝不绘制全宽／全高灰色底，避免空白页灰幕、静态底条和硬矩形裁切边。
- **自动验证：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 `SystemBarsAppearanceContractsTest` 通过；正式 `:app:assembleRelease` 和 `git diff --check` 通过。未运行仪器测试。
- **正式覆盖与实机核验：** OPPO Find N5（`PKH120`，Android 16）预检确认候选与现装包同为 `66 / 0.3.0-p10j`、非 Debug，v2/v3 证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。候选及安装后实际 `base.apk` SHA-256 均为 `c869ad72ea2a480c5c284feb1b9b89a8f48a251d6a4444c3ce168b7a8a772346`；`pm install -r --user 0` 返回 `Success`，`firstInstallTime` 保持 `2026-08-20 15:15:31`，仅 `lastUpdateTime` 更新为 `2026-08-24 22:28:43`。未卸载、未清数据、未部署 Debug／仪器包，设备临时 APK 已清理。设备已有长对话截图 [`/tmp/nanfeng-ai-edge-frost-verified.png`](/tmp/nanfeng-ai-edge-frost-verified.png) 已确认四边正文实际进入强模糊、中心清晰，且空白画布没有灰色承托面。

## 2026-08-24：Android 会话系统栏与沉浸式正文边界（已完成）

- **系统栏：** `NanfengAiActivity` 明确采用透明浅色系统栏，并在窗口重新获得焦点时再次请求浅色外观标志；ColorOS 不再把顶部状态栏和底部导航手势图标自动恢复为白色。会话浅灰画布上的时间、网络、电量和手势条均以深色（纯黑）显示。
- **会话画布：** 仅会话路由不再在根布局消费顶部／底部安全区，移除了顶部和底部多余的灰白承托带；顶栏、正文和 Composer 均为独立层。Transcript 使用 `LazyColumn` 的可滚动 `104dp` 起始 inset，因此首段内容完整落在顶栏按钮下方，而非被按钮遮住或通过固定白条下推。末尾 `96dp` 也属于可滚动内容清空区；Composer 自己消费导航手势区，最后一行可完整越过输入框显示。
- **自动验证：** 离线 `SystemBarsAppearanceContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 通过；正式 `:app:assembleRelease` 成功，`git diff --check` 通过。未运行仪器测试。
- **正式覆盖与真机画面：** OPPO Find N5（`PKH120`，Android 16）安装前回读的正式包与候选均为 `66 / 0.3.0-p10j`、非 Debug，v2/v3 证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。候选及安装后实际 `base.apk` SHA-256 均为 `72e4a45bdc8539f99565ad40e207dfed54eed9cff41cb2a3cfb0fdbf12d4dea5`；`pm install -r --user 0` 返回 `Success`，`firstInstallTime` 保持 `2026-08-20 15:15:31`，仅 `lastUpdateTime` 更新为 `2026-08-24 21:55:41`。未卸载、未清数据、未部署 Debug／仪器包，设备临时 APK 已清理。真机截图 [`/tmp/nanfeng-ai-conversation-system-bars.png`](/tmp/nanfeng-ai-conversation-system-bars.png) 显示纯黑状态栏图标、无整条顶／底底板及首段避开顶栏。

## 2026-08-24：Android 南枫 AI 启动器图标替换与 OPPO 覆盖（已完成）

- **图标资产与修正：** 最初用户 JPEG 原件保留于 `app/src/main/icon-source/nanfeng_ai_launcher_source.jpg`（SHA-256 `9d81a42b59d18c8517beb88943649cc554c8305d7c3c7b518479b21ca169336e`）。当前 Android 母版 `nanfeng_ai_launcher_q_rounded_scale90_tuned_source.png`（SHA-256 `1e7aff38fce15afdd39df431402b78765330be8afd5a24bb9e81a993b665bf3f`）在 Q 版粗圆标记基础上将白色主体约缩小 10%，并把橙底从 `#D87652` 轻调为 `#E1764E`；已用 `retone_nanfeng_ai_launcher_background.swift` 校准色彩空间，确保是轻微而非过度提亮。`prepare_nanfeng_ai_launcher_foreground.swift` 只去除外缘连通白色画布，`13.5dp` inset 继续保留已验证的 ColorOS 安全留白；Desktop 未触及。
- **静态与构建验证：** `audit_android_launcher_icon.py app` 通过（Manifest 图标／圆形图标、两个自适应 XML 和所有图层均存在）。离线 `:app:assembleRelease` 成功；候选 `app/build/outputs/apk/release/南枫AI.apk` 为 `com.nanzhufeng.ai / 66 / 0.3.0-p10j`，非 Debug，v2/v3 正式签名证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，候选 SHA-256 为 `a32abe4eb0a8320d5ea4c604a5e7e5df16d6bb6a301c8faade0e23f6ed1ef66e`。
- **正式覆盖与真机视觉：** OPPO Find N5（`PKH120`，Android 16）安装前回读的现装包与候选证书一致；使用 `pm install -r --user 0` 返回 `Success`，设备实际 `base.apk` 回读亦为 `a32abe4eb0a8320d5ea4c604a5e7e5df16d6bb6a301c8faade0e23f6ed1ef66e`，`firstInstallTime` 保持 `2026-08-20 15:15:31`，仅 `lastUpdateTime` 更新为 `2026-08-24 21:44:05`。未卸载、未清数据、未部署 Debug／仪器包，设备临时 APK 已清理。稳定的 ColorOS Launcher 截图 `/tmp/nanfeng-ai-launcher-q-scale90.png` 显示主体尺寸缩小、橙黄底轻微提亮且图案四边完整；用户仍可据此决定是否继续调节视觉尺寸。

## 2026-08-24：Android 设置二次规划与真实个性化/记忆调用（JVM 已验证）

- **信息架构：** 设置首页现在只保留八个直达二级入口：个性化、记忆与上下文、模型与联网、对话管理、项目与知识、数据与存储、隐私与安全、功能审阅与关于。导入任务、系统文件选择器以及既有 Projects／知识／Memory 管理弹层仍是必要的操作终点，不再额外套设置层级；旧紧凑路由回退时统一归属“项目与知识”。
- **真实调用边界：** 个性化资料默认关闭，只有保存且用户开启后才作为本次普通模型调用的系统指令；不写入调用审计或诊断。相关长期 Memory 默认保持升级前的召回行为，但用户关闭后普通聊天不检索也不发送长期 Memory；当前问题、知识库和会话历史的既有预算规则不被该开关误伤。
- **视觉边界：** 参考截图只用于菜单层级，不改变当前 Android 的 `PageBackground = #F7F7F7` 灰色页面画布、`ForegroundSurface = #FFFFFF` 白色前景卡片／输入面／弹层约束。
- **跨端登记：** Desktop 的功能审阅已如实登记两项 Android 已完成而 Desktop 待实现的能力，且将“更多本地控制面”改名为“项目、知识与记忆”；Desktop 没有新增伪开关，也没有被写成已接入普通模型调用。
- **自动验证：** 离线 `:app:testDebugUnitTest` 定向执行 `AssistantExperienceSettingsContractsTest`、`SettingsUiSimplificationContractsTest` 与 `AndroidUserEntryAuditContractsTest` 通过；Desktop `chat-first-ui` 65 项通过；`git diff --check` 通过。未运行仪器测试。
- **正式覆盖：** OPPO Find N5（`PKH120`，Android 16）已将新正式候选 `app/build/outputs/apk/release/南枫AI.apk` 覆盖安装。候选与安装前原包均为 `66 / 0.3.0-p10j`、非 Debug，v2/v3 签名证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。候选 SHA-256 为 `2798954ffeb3c3b19579f3fc1384aa29b2e1b2cb07c74452327542d6e8426a63`；`pm install -r --user 0` 返回 `Success`，设备新 `base.apk` 回读同 hash。`firstInstallTime` 保持 `2026-08-20 15:15:31`，仅 `lastUpdateTime` 更新为 `2026-08-24 20:06:05`；未卸载、未清数据、未部署 Debug/仪器包，设备临时 APK 已清理。
- **剩余验收：** 覆盖安装和字节核验不替代设置页实际视觉/触控验收；需在 OPPO 手工检查灰色画布、白色卡片/弹层、二级返回，以及个性化和记忆开关的可理解性。

## 2026-08-24：会话 UI 合同、未读提示与正式覆盖（已完成）

- **合同收口：** `ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md` 是 Android 会话可见规则唯一正文；会话管理、搜索/预览、历史自适应与附件合同均已加单向路由，历史交接数值不能反向覆盖当前 UI。
- **未读提示：** 本机 `conversation_read_markers_v1` 仅保存不透明会话 ID 与已读更新时间水位。首次升级建立既有历史水位；之后未打开会话收到新 Assistant 内容时，日期右侧显示主题色小圆点，打开即清除。未保存标题、正文、附件、Provider 或凭据；Android 设置 → 功能审阅已登记，Desktop 没有同等 owner 前不展示伪提醒。
- **自动验证：** `P6DConversationRowAccessibilityContractsTest` 通过，含 Android Debug Kotlin 编译和 `git diff --check`。
- **正式覆盖：** OPPO Find N5（`PKH120`，Android 16）只读预检确认现装与候选均为 `66 / 0.3.0-p10j`，非 Debug；候选与原包 v2/v3 证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。正式候选 `app/build/outputs/apk/release/南枫AI.apk` SHA-256 为 `b7bde7aed225dde50e74a279ca28cd4e4b6b5b5d85dec2524334d9a6304be72a`；`pm install -r --user 0` 返回 `Success`，设备实际 `base.apk` 回读同 hash。未卸载、未清数据、未运行 Debug/仪器测试；`firstInstallTime` 保持 `2026-08-20 15:15:31`，仅 `lastUpdateTime` 更新为 `2026-08-24 18:18:36`。设备临时 APK 已清理。
- **剩余验收：** 同签名覆盖和字节核验不代替人工视觉/交互验收；需在设备确认来源 chip、表格复制、未读点出现/打开消失、长截图、搜索目录和音频播放。

## 2026-08-24：抽屉轻灰、圆润 24dp 发送标记与正式覆盖（已完成）

- **视觉实现：** 左侧抽屉底层保持专属的轻灰 `#F4F4F4`，不再误用白色前景令牌，也不回到此前偏暗的底色。Composer 的橙色发送圆面保留；白色发送标记改为本地 `ic_nanfeng_send_rounded`，使用圆角端点／连接和 `1.6` 宽度描边，去除旋转 Material 纸飞机的尖锐折角；图标本体按确认值由 `18dp` 放大到 `24dp`。发送、停止与加载状态的实际行为、尺寸和可访问描述不变。
- **自动验证：** `FBP6041ComposerGlyphContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 通过，包含 Android Debug Kotlin 编译和 `git diff --check`。
- **正式覆盖：** OPPO Find N5（`PKH120`，Android 16）只读预检确认现装包与候选均为 `66 / 0.3.0-p10j`、非 Debug，v2/v3 正式签名证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。候选 `app/build/outputs/apk/release/南枫AI.apk` SHA-256 为 `2b28f2743ad09dbf8c26f2782fbef55b1976d09026a3ccd37ec0c3d1e1a28174`；以 `pm install -r --user 0` 返回 `Success`，设备实际 `base.apk` 哈希一致。未卸载、未清数据、未运行 Debug／仪器测试；`firstInstallTime` 保持 `2026-08-20 15:15:31`，数据目录仍为 `/data/user/0/com.nanzhufeng.ai`。设备临时 APK 已移除。
- **剩余验收：** 覆盖安装与字节核验不代替视觉验收；需在外屏／内屏人工确认轻灰抽屉与圆润发送图标的实际观感，以及长截图是否确实到正文末尾。

## 2026-08-24：Android 左侧抽屉画布与前景表面分离（JVM 已验证）

- **根因与修正：** 前一轮为了恢复前景白卡与按钮，新增的共享 `ForegroundSurface` 被错误用于 `ModalDrawerSheet`；抽屉本应是导航画布，却被归类为一整张白色前景卡，因此任何白卡提亮都会连带提亮整块左栏。现新增专属 `ConversationDrawerCanvas = #F4F4F4` 并只供抽屉最底层使用；它只比主页面底略深，避免回到此前过暗的左栏。搜索框、选中对话行、设置圆钮和“新对话”仍各自保持原有亮面/强调色。
- **防回归：** 视觉合同改为同时锁定“灰色抽屉画布”和“纯白前景控件”，删除旧的“抽屉容器必须为 `ForegroundSurface`”断言，避免后续白卡修复再次污染导航底层。
- **验证边界：** `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，包含 Android Debug Kotlin 编译与 `git diff --check`。尚未重建 Release、覆盖安装或操作 OPPO，实际外屏/内屏对比仍待正式候选包人工确认。

## 2026-08-24：Android 对话系统长截图滚动所有权（JVM 已验证）

- **根因与修正：** 系统长截图通过 Compose 的 `LazyColumn` 滚动语义逐段取图；对话页原有“跟随最新消息”协程同时可能执行 `scrollToItem(last)`。两条滚动控制并发时，系统捕获会被拉回最新位置，造成长截图在正文中途结束。现通过 `LocalScrollCaptureInProgress` 在系统捕获期间暂停普通聊天和工作区的所有自动回到最新消息逻辑；用户主动发送、普通阅读和截图结束后的既有跟随行为不变。
- **依赖判断：** 当前 Compose UI `1.11.4` 已内建 Android 12+ Scroll Capture，旧的 `ComposeFeatureFlag_LongScreenshotsEnabled` 已从该版本移除，不能再添加已失效的开关。没有因这一个问题升级整个 Compose/compileSdk 工具链。
- **验证边界：** `P6GUnifiedChatFirstUiContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 通过，包含 Android Debug Kotlin 编译与 `git diff --check`。未运行仪器测试、未重建 Release、未覆盖安装或操作 OPPO；ColorOS 实际长截图仍需在正式候选包上手动从同一长对话验证，确认能拉至真实末尾。

## 2026-08-24：Android 本地搜索目录默认展示（JVM 已验证）

- **根因与修正：** 搜索页此前只在手动输入并提交关键词后运行查询，分类 Tab 在空关键词时也只是清空结果，因此“全部／图片／视频／音频／文件”首次打开都没有内容。现在打开搜索即浏览当前本机对话目录；“全部”显示最近对话与附件，媒体和文件 Tab 直接显示对应的附件卡片网格，并按消息所属月份分组；关键词输入约 `180ms` 后直接过滤，不必另按提交。
- **完整性：** 关键词索引无命中时会回退到当前可见消息树的本地投影，避免旧设备的非空但不完整索引让已有正文变成“找不到”。三点菜单中的当前聊天查找改为 NFKC、忽略换行/连续空格及大小写的文本匹配；命中仍只在当前会话内跳转。
- **布局与参考：** 全屏搜索标题栏加入状态栏避让。实现参考南枫知识库“空关键词直接消费完整目录、分类仅改变筛选与呈现”的搜索/附件目录逻辑；Android 仅复用该关系，不复制桌面端来源业务、文件恢复或 WebView 实现。
- **目录预览：** 卡片不再使用空白文件占位：图片与视频使用已校验缩略图，PDF 用同一受校验 `PdfRenderer` 路径渲染首页，TXT／Markdown／JSON／CSV 展示惰性、受限且不执行的真实开头片段；无可用预览时会明确显示本地原因。列表行和网格卡使用同一规则。
- **音频播放器：** 搜索目录和正文附件统一显示音频格式、播放按钮、进度轨和时长；点击音频即打开完整播放器并开始本机播放，播放器可暂停、实时刷新进度，关闭时保存位置并释放临时副本。旧“开始本地播放／继续播放／安全说明”等附加小字已移除。
- **启动落点：** 正常冷启动或后台超过 `15` 分钟后回到 App 时，默认进入可复用的空白“新对话”；短时切后台会保持当前历史、草稿和阅读位置。后台时间同时写入轻量本地偏好，因此系统回收进程后短时重开也不切走；对话桌面快捷方式、分享、深链等显式入口保持原目标。
- **正文层级：** 对话阅读区整体由旧的 `1.10x` 调整为 `0.99x`，即相对截图中的当前视觉缩小约 `10%`；一级／二级／三级标题改为 `26/22/18sp` 的 ExtraBold／Bold 层级，正文保持约 `16sp`，列表和表格正文降为约 `15sp`，强调内容用 `ExtraBold`，链接升级为 `SemiBold`。顶栏、输入栏、图标和触控面积不参与缩放。
- **验证边界：** `P3EConversationManagementExportContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 通过，包含 Android Debug Kotlin 编译与 `git diff --check`。未重建 Release、覆盖安装、操作模拟器或 OPPO；真实设备上的目录数量、缩略图、PDF 首页与状态栏视觉仍待人工确认。

## 2026-08-24：Android Markdown 表格完整网格（JVM 已验证）

- **布局修正：** 表格不再由内容宽度决定表头灰底。整张表以当前阅读宽度均分列宽；可用宽度不足最低列宽时才横向滚动。表头灰底现铺满全部列，表格具备完整的横向行分隔与纵向列分隔，外框保持细灰蓝边线。
- **单元格对齐：** 表头和正文均在各自单元格内水平、垂直居中；多行内容随本行最高单元格居中，不再沿左边或顶部散开。
- **验证边界：** `P6DConversationRowAccessibilityContractsTest` 通过，包含 Android Debug Kotlin 编译。没有重建 Release、覆盖安装、操作模拟器或 OPPO；实际窄屏横向滚动与视觉效果仍待人工确认。

## 2026-08-24：Android 前景白面恢复（JVM 已验证）

- **分层修正：** `PageBackground` 继续仅作为最底层的 `#F7F7F7` 页面画布；新增统一 `ForegroundSurface = #FFFFFF`，并用于 Material surface-container、左侧抽屉、设置分类卡和共享 `WhiteCard`。此前设置分类卡硬编码的 `#F3F4F3` 已移除，避免白卡/按钮被误染成底层灰。
- **边界与验证：** 中性系统 scrim 仍只在模态打开时压暗其背后的页面；不改变用户右侧橙色气泡、文本、按钮行为或顶栏覆盖规则。`P6DConversationRowAccessibilityContractsTest` 通过，包含 Android Debug Kotlin 编译与 `git diff --check`。未操作模拟器或 OPPO，目标视口亮度仍待人工确认。
- **正式覆盖：** 已将当前正式候选 `app/build/outputs/apk/release/南枫AI.apk`（`66 / 0.3.0-p10j`，SHA-256 `878fb5b592da2382d9a552bbc261faedf857359128f0eb32c7571aeaed6dade0`）同签名覆盖至 OPPO Find N5（`PKH120`，Android 16）。候选与原包证书 SHA-256 均为 `6d1d56ec…d8661f8`；`pm install -r --user 0` 返回 `Success`，首次安装时间保持 `2026-08-20 15:15:31`，设备实际 `base.apk` 同 hash。未卸载、未清数据、未运行仪器测试；已清理设备和本机的临时核验文件。安装不替代设置页与对话页的人工视觉确认。

## 2026-08-24：Android 列表标记首行基线与层级缩进（JVM 已验证）

## 2026-08-24：Android 来源入口归属正文（JVM 已验证）

- **根因与修正：** 纯 URL 行与只含 URL 的列表项此前会成为独立 Markdown 内容块；渲染时 URL 文字隐藏、仅留下来源图标，于是形成连续的空图标行。现在解析层会将这些来源附着到此前对应的段落、标题、引用或最近列表项；正文只保留一个行尾来源图标，点击后仍打开白色来源网站弹窗，站点卡继续以 `ACTION_VIEW` 交给系统浏览器。
- **边界：** 没有可读前文可归属的孤立 URL 会保留，避免静默丢失来源；`P3DMessagePresentationContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 通过，包含 Android Debug Kotlin 编译。尚未重建 Release 或覆盖 OPPO。

- **根因与修正：** 无序点及有序数字原先由 `Row(Alignment.Top)` 顶端对齐，且使用较小的默认文字规格，和正文 `16sp / 26sp` 的第一行基线脱节；解析也丢弃了 Markdown 的前置缩进。两类标记现均使用正文同一字号/行高，并通过 `alignByBaseline()` 与本项正文第一行对齐。顶层列表先缩进 `12dp`，每个实际嵌套层级再推进 `16dp`；两空格与四空格的 Markdown 缩进都会归一为逐级层次。窄列 `14–22dp` 和正文间距 `4dp` 保持不变。
- **验收边界：** `P3DMessagePresentationContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 通过，包含 Android Debug Kotlin 编译。仅修改 Markdown 列表项的层级与垂直排版，不改段落、标题、右侧用户气泡或顶栏覆盖规则；实际阅读页视觉仍待确认，尚未重建 Release 或覆盖 OPPO。

## 2026-08-24：Android 首段生成等待态（JVM 已验证）

- **可见状态：** 空白的 PARTIAL 助手消息不再只有一行“正在生成”。根据已持久化的用户输入，在左侧开放正文列显示固定、可替换的前情概括和明确准备阶段：PDF、图片、音视频、文件、链接、代码问题、分析问题与普通提问各有本地文案；首个正式字符到达后概括立即消失，仅保留紧凑继续生成提示。
- **真实性边界：** 文案只表达“梳理/准备”，不伪称已搜索、已读取附件或已经得出结论；不存入助手正文、不进入导出/复制、不读取附件字节、不触发 Provider/模型请求。
- **验证边界：** `P6FTranscriptPresentationContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 通过，包含 Android Debug Kotlin 编译。未重建 APK、未操作模拟器或 OPPO；等待态目标视口视觉仍需后续人工确认。

## 2026-08-24：Android 对话延后本地概括标题（JVM 已验证）

- **时机与来源：** 新会话继续以“新对话”占位并保留 `autoTitlePending`。首条 USER 消息不再截取开头改名；首条 ASSISTANT 完整回复落库后，优先提取回复中的 Markdown 标题或中文章节标题，再回退到用户文本概括或附件类型加文件名。默认显示宽度约 13 个汉字，必要时最多约 15 个汉字。
- **边界：** 全过程仅消费已持久化的对话文本和安全附件显示名；不读取附件字节、不发出额外 Provider/模型请求、不增加 token 消耗。流式回复仅在 `RuntimeCompleted` 后改名；失败、取消、手动重命名、导入历史与升级前既有会话都不会被覆盖。
- **验证边界：** `P6IConversationAutoTitleContractsTest`、`P6IConversationAutoTitleRoomContractsTest` 与 `P3BConversationRuntimeContractsTest` 通过，包含 Android Debug Kotlin 编译。未重建 APK、未操作模拟器或 OPPO；首次真实回复后的左栏标题仍待自然使用时视觉确认。

## 2026-08-24：Android 对话来源图标与系统浏览器（JVM 已验证）

- **正文收口：** `InlinePresentation.Link` 不再把域名、完整 URL 或箭头写入段落、列表项或表格单元的正文流；每个含来源的对应内容末尾只嵌入一个 `24dp` 外部来源图标。来源图标是可访问的交互控件，内容描述准确给出可打开的站点数量。
- **真实操作：** 点击图标打开只包含该处去重来源的白色“来源网站”弹窗，站点卡显示标签和完整 URL；点击站点卡用 `Intent.ACTION_VIEW` + `CATEGORY_BROWSABLE` 交给系统浏览器，系统没有处理器时显示中文失败提示并保留弹窗。没有把外部网站内容拉入 App、没有请求 Provider、没有读取 Key 或写入历史消息。
- **验证边界：** 离线 `P3DMessagePresentationContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 通过，包含 Android Debug Kotlin 编译。未重建 APK、未操作模拟器或 OPPO；系统浏览器实际选择与视觉需后续目标设备人工确认。

## 2026-08-24：Android 对话正文 Markdown 排版与信息完整性（JVM 已验证）

- **明确保留：** 左侧菜单圆钮及右侧新对话／更多胶囊的悬浮覆盖关系是既定视觉规则；长正文从控件下方经过属于正常表现，本轮没有调整顶栏、可滚动 viewport 或安全留白。
- **正文格式：** Markdown parser 升至版本 4：`---` 解析为分隔线而不再显示原始字符；列表中的空行不会把同一列表拆为多块；反引号内的普通文本保留等宽显示但不再显示反引号，完整 HTTP 地址仍是可点击链接。标题改为稳定的阅读级层级；相邻列表项缩至 `4dp`。
- **列表与气泡：** 有序/无序符号共用 `14–22dp` 的窄列和 `4dp` 正文间隔，直接服务本项正文的悬挂缩进，不能与段落或标题正文强行对齐。长 USER 消息气泡由 50% 圆角改为固定 `24dp`，避免高内容时曲线裁切首尾文字；内容宽度、右对齐、浅橙色与长按边界保持。
- **验证边界：** 离线 `P3DMessagePresentationContractsTest` 与 `P6DConversationRowAccessibilityContractsTest` 通过，包含 Android Debug Kotlin 编译；`git diff --check` 待本轮最终汇总复核。未重建 APK、未操作模拟器或 OPPO，目标视口视觉仍需后续人工确认。

## 2026-08-24：左栏会话行适度恢复与间距放宽（JVM 已验证）

- **布局：** 用户确认此前普通会话行过紧后，行高从 `36dp` 恢复约 `1/5` 至 `44dp`；标题、日期、单行截断、左右 `5dp` 内容边距、批量编辑 `52dp` 和右滑操作带的同高联动均保持。可滚动列表的组内行间距从 `4dp` 放宽到 `8dp`，搜索、分区标题、设置和新对话的独立布局不变。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 通过，包含 Android Debug Kotlin 编译；`git diff --check` 通过。未重建 APK、未操作模拟器或 OPPO，实际目标视口观感仍需后续视觉验收。

## 2026-08-24：最终回归、文档固化与本地 checkpoint（已完成）

- **最终自动验证：** 全量 `:app:testDebugUnitTest` 的 629 项通过，`:app:lintDebug` 无 error；本轮先发现并修正两条旧测试断言（Composer glyph 的格式敏感匹配、附件批量选择后统一 reload），定向回归也通过。
- **正式产物：** 现有正式候选 `app/build/outputs/apk/release/南枫AI.apk` 仍为 `66 / 0.3.0-p10j`，v2/v3 签名通过，SHA-256 `55ed5ad39b168f60da81da3a3999978108e1add817dfa8502311802fde1757bf`；本轮没有运行代码/资源变化后重签名，不重复覆盖 OPPO。
- **文档增量：** 完整开发档案更新到 code 66/Schema 45，README 增加当前事实路由，决策日志登记唯一 Android 会话 UI 合同；历史已完成合同不重写，仅增加优先级路由。
- **checkpoint：** 当前 `main` 已有本地 `feat: complete provider chat and conversation shell checkpoint`，共 138 个文件；提交前已完成 staged 范围、空白与高风险凭据字面量检查。提交 ID 以当前 `main` HEAD 为准，避免后续文档 amend 使交接中的硬编码 hash 失真。未 push、未创建 PR/Release，也没有为本次文档/测试固化重复覆盖 OPPO。

## 2026-08-24：当前 Android 会话界面合同归并（JVM 已验证）

- **唯一正文：** 新增 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)，集中记录本轮已确认的顶栏状态、无色相底层、左栏行密度与右滑三键、点外部收起、主屏标准右滑、Composer/模型名、模型面、输入长按菜单与消息页脚规则。
- **冲突消解：** `CHAT_FIRST_INTENT_ORGANIZATION_CONTRACT.md`、`P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_CONTRACT.md` 与 `P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md` 已加显式路由：它们继续各自的信息架构、消息领域和模型路由职责，但早期 Android UI 数值与视觉不再形成第二套规则。
- **防回归：** `P6DConversationRowAccessibilityContractsTest` 新增 FB-P6-111，锁定唯一合同、旧合同路由与已实现的关键 UI 锚点；离线定向测试 74 项通过，`git diff --check` 通过。本轮没有运行代码或资源变更，因此不重建 APK、不重复覆盖 OPPO。

## 2026-08-24：左栏会话行纵向留白、侧滑交界与主屏右滑（正式包已覆盖 OPPO）

- **布局：** 普通会话行从 48dp 收到 36dp，标题和日期的字号、行高、左右边距和 4dp 行间距均不变；移除固定垂直 padding，改由整行垂直居中，让文字以外的上下留白从约 32dp 收到约 20dp（约减少 2/5）。
- **联动：** 右滑露出的三图标操作带直接复用当前行高，普通会话保持 36dp，批量选择行仍为原 52dp，不会因收紧普通行改变批量编辑的复选框空间。操作带与右侧白色会话卡只以两块表面的自然交界分隔，移除末端额外的 1dp 深色竖线。
- **收起逻辑：** 左栏自己持有展开状态。展开后，三个快捷图标之外的任意点击（空白、标题、搜索、其他会话、批量编辑、设置和新对话）都会只先关闭操作带；快捷图标自身不受拦截，仍执行置顶、重命名或删除。
- **主屏侧栏手势：** 移除会话画布上“至少 56dp 且夹角不超过 50°”的自定义强制门槛，恢复 Material 标准抽屉手势；主屏对话区正常右滑即可打开左侧栏，纵向消息滚动仍由嵌套滚动协商。
- **覆盖与验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，`git diff --check` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk` 的 v2/v3 签名校验通过，SHA-256 为 `55ed5ad39b168f60da81da3a3999978108e1add817dfa8502311802fde1757bf`。OPPO Find N5（`PKH120`，Android 16）同签名 `pm install -r --user 0` 覆盖返回 `Success`，没有卸载、清数据、Debug/仪器部署或 `connected*AndroidTest`；版本仍为 `66 / 0.3.0-p10j`，`firstInstallTime` 保持 `2026-08-20 15:15:31`，仅更新至 `2026-08-24 14:15:59`。设备实际 `base.apk` 与本地候选哈希一致，设备临时 APK 已清理。仍需真机手动确认主屏右滑与点外部收起的实际手势竞争。

## 2026-08-24：模型二级面遮罩直接关闭（JVM/Release 已验证）

- **交互分流：** 模型二级菜单的系统返回、标题返回和左右向内滑继续先回到一级菜单；遮罩空白区域点击不再复用该层级返回，而是直接关闭整个模型选择面。附件菜单原有直接关闭语义不变。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，`git diff --check` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk` 构建成功，v2/v3 签名校验通过，SHA-256 为 `8c788f739e2c06cc5d2569166d16690f116f48c12dbb49b263ebabf62d611add`。该候选尚未覆盖安装 OPPO、未运行仪器测试。

## 2026-08-24：Fast 路由门禁正式包覆盖 OPPO（已完成）

- **覆盖边界：** OPPO Find N5（`PKH120`，Android 16）上的 `com.nanzhufeng.ai` 经只读预检确认候选为正式、不可调试的 `66 / 0.3.0-p10j`；使用设备临时路径及 `pm install -r --user 0` 同签名覆盖。没有卸载、清数据、Debug/仪器部署、`connected*AndroidTest` 或真实模型请求。
- **安装回读：** 系统返回 `Success`，`firstInstallTime` 保持 `2026-08-20 15:15:31`，仅 `lastUpdateTime` 更新为 `2026-08-24 13:55:28`。设备实际 `base.apk` 与本地正式包 SHA-256 均为 `f58f5de4649102ce894f19361d92c9fbf2ec45b062e17a2fd6af41676fa11631`；设备临时 APK 已清理。
- **剩余验收：** 覆盖与字节一致证明本轮门禁已进入设备，但不代替联网实测。下一次实际选择 Claude Opus 等 OpenRouter 模型发送时，应确认页脚显示标准实时实体名且不会再出现 `(Fast)`；历史 `(Fast)` 归因保持不改。

## 2026-08-24：禁止 Fast 变体的真实模型路由（JVM/Release 已验证）

- **根因与显示：** Composer 选择的是逻辑预设，助手页脚显示的是当次实际模型归因；旧目录在同一逻辑预设存在多个实体变体时可能按排序选中 `:fast`，所以历史会显示 `Claude Opus 5 (Fast)`。历史归因保持真实，不以改文案掩盖。
- **路由门禁：** 所有 OpenRouter 预设统一排除实体 ID 含 `:fast` 或显示名含 `(Fast)` 的变体；该规则不影响像 Gemini Flash 这类独立正式模型系列。即使设备存有旧 Fast 映射，解析层也会拒绝它，发送前触发目录刷新；如果目录中没有可用标准实时版，请求被拒绝而不会回退或消耗在 Fast 变体上。
- **验证边界：** 离线 `P2JOpenRouterRegistryContractsTest`（标准变体优先及旧 Fast 缓存拒绝）、`P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，`git diff --check` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk` 构建成功，v2/v3 签名校验通过，SHA-256 为 `f58f5de4649102ce894f19361d92c9fbf2ec45b062e17a2fd6af41676fa11631`。该候选未覆盖安装 OPPO、未用真实 Key 发起服务请求、未运行仪器测试。

## 2026-08-24：底层提亮与助手页脚图标对齐（JVM/Release 已验证）

- **底层：** 主页面与左栏共同使用的无色相底层 `PageBackground` 由 `#F1F1F1` 提亮至 `#F7F7F7`；保留与纯白前景的可辨层级，不改文本、边框、橙色操作或覆盖层遮罩。
- **助手页脚：** 复制、分享以创建分支图标的实际绘制尺寸为基准，图标从 20dp 收到 16dp；创建分支维持原 20dp 矢量尺寸。三个操作的点击面统一从 44dp 收到 36dp，图标间距从 2dp 收到 1dp，给时间和模型信息留出空间且点击面不重叠。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，`git diff --check` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk` 构建成功，v2/v3 签名校验通过，SHA-256 为 `3c5e7706bc1ba397d8da6d4bb60e438d97c9f4ba905381865d47d76ca182d43f`。该候选未覆盖安装 OPPO、未运行仪器测试。

## 2026-08-24：批量操作栏与模型选区密度收口（JVM/Release 已验证）

- **批量编辑：** 底部批量操作栏取消静态阴影与 tonal 抬升，保持和其余白色控件一致的柔和表面；栏高从 54dp 收到 50dp。批量状态下会话行从 68dp 收到 52dp，仅比普通 48dp 单行略高。全选图标由 18dp 收到 16dp，其他动作和软删除流程不变。
- **模型选区：** 模型显示继续按最长简写模型名固定，不自适应；固定宽度由 104dp 收到 84dp，使左右冗余留白各再减少约 2/5，文字、触控高度和右侧对齐逻辑不变。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，`git diff --check` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk` 构建成功，v2/v3 签名校验通过，SHA-256 为 `9da94b7bc65703cfa9840e8ad8a9b21c79706fea04c4d92797075f4203f378a7`。该候选未覆盖安装 OPPO、未运行仪器测试。

## 2026-08-24：顶部状态分流与控件可读性（JVM/Release 已验证）

- **状态规则：** 左侧三横线始终是侧栏入口，始终显示；不随会话是否有内容而隐藏。当前可见消息非空时，顶部仅显示左侧栏入口、右侧新对话与更多操作，不显示中间“对话 / 工作”切换；空对话才显示该切换及右侧临时对话入口。
- **视觉：** 顶部图标与文字的中性灰由 `#767676` 收到正常深度 `#3F3F3F`，在浅灰底与白色前景上保持清晰；未恢复硬边、描边或投影。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，`git diff --check` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk` 构建成功，v2/v3 签名校验通过，SHA-256 为 `5d2f0781cfb9e6deb74878ae64c30c9a4799b18fe17a2d421ae0e46aa703a415`。该候选未覆盖安装 OPPO、未运行仪器测试。

## 2026-08-24：左栏会话行紧凑横向密度（JVM/Release 已验证）

- **布局：** 左栏可滚动会话区的行间距从 8dp 收到 4dp；左右内容边距按要求从 16dp 收到 5dp，使会话栏几乎填满抽屉宽度。行高、标题层级、底部设置和橙色新对话的位置不变。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 通过，`git diff --check` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk` 构建成功，v2/v3 签名校验通过，SHA-256 为 `d6d65aed6136ba68e9cca59a8b65083ded179a15f5510ce4b92e591c59a05e53`。该候选未覆盖安装 OPPO、未运行仪器测试。

## 2026-08-24：左栏会话操作带直线分隔与点按收起（JVM/Release 已验证）

- **视觉与命中：** 三个图标继续共享 132×48dp 操作带，但带本身和每个图标命中面改为矩形；外层会话行只保留自身外侧圆角，删除图标右边以 1dp 垂直中性线和右侧会话正文直接分隔，不再产生独立圆角按钮的收口。展开行的正文点击先收起操作带而不选中会话；其他会话本来会在选择时收起，左栏空白也新增为透明的收起目标，三个图标点击仍直接执行既有动作。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，`git diff --check` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk` 构建成功，v2/v3 签名校验通过，SHA-256 为 `459ee29509dc2d70372d95d6bc40320f1a2b92c279550fa4916636fa36f0fc11`。该候选未覆盖安装 OPPO、未运行仪器测试；仍需真机手动确认空白区点按与右滑收起的实际手势竞争。

## 2026-08-24：Composer 的 GPT 模型名保留 5.6 版本号（JVM/Release 已验证）

- **展示规则：** Composer 显示层不再从 `GPT-5.6 Terra/Sol/Luna` 删除完整的 `GPT-5.6 ` 前缀，而是仅删除 `GPT-`，因而显示为 `5.6 Terra`、`5.6 Sol`、`5.6 Luna`。这保留了用于区分代际的版本号，同时仍不展示冗余家族名；Claude、Gemini、Qwen、DeepSeek 的既有精简规则和所有实际 ID、路由、Provider 设置不变。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 通过，`git diff --check` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk` 构建成功，v2/v3 签名校验通过，SHA-256 为 `cb7ea0445b458aabc8af59f9f120a0432aefd25c2c82b2acb7160ecd99327589`。该候选未覆盖安装 OPPO、未运行仪器测试。

## 2026-08-24：模型选择面右侧锚定（JVM/Release 已验证）

- **根因与实现：** 旧模型面以“模型标签右缘减去完整菜单宽度”计算 X 坐标；336dp 选择面比 Composer 的模型标签宽，窄屏上会被夹到左侧最小边距。模型根层与任一二级分类层现统一以 Composer 的 trailing 边计算右对齐，纵向仍以模型标签的上缘为锚点；附件菜单不受影响。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，`git diff --check` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk` 构建成功，v2/v3 签名校验通过，SHA-256 为 `397557907603a3ed019549d73f79cc0a10b6e90587eb0ebfcc9f00ef08abaed0`。未覆盖安装 OPPO、未运行仪器测试；仍需真机手动确认外屏与内屏的右侧锚定观感。

## 2026-08-24：模型选择面右侧锚定正式包覆盖 OPPO（已完成）

- **覆盖边界：** OPPO Find N5（`PKH120`，Android 16）上的 `com.nanzhufeng.ai` 经只读预检确认候选与原包同为 `66 / 0.3.0-p10j`、v2/v3 正式证书 SHA-256 指纹一致；采用设备临时目录后 `pm install -r --user 0` 覆盖，没有卸载、清数据、Debug/仪器部署或 `connected*AndroidTest`。
- **安装回读：** 系统返回 `Success`，`firstInstallTime` 仍为 `2026-08-20 15:15:31`，仅 `lastUpdateTime` 更新为 `2026-08-24 13:13:02`。设备实际 `base.apk` 与本地正式包 SHA-256 均为 `397557907603a3ed019549d73f79cc0a10b6e90587eb0ebfcc9f00ef08abaed0`；设备临时 APK 已清理。
- **剩余验收：** 覆盖和字节一致不代替实际页面观感，仍需在外屏/内屏手动打开模型根层及二级层，确认它们均贴右侧而非左侧。

## 2026-08-24：左栏会话右滑操作带与设置控件收口（JVM/Release 已验证）

- **右滑操作带：** 普通状态不再组合/绘制操作带；会话白卡完整覆盖固定的 48dp 单行高度。用户向右滑时，132dp 的操作带才随露出进度出现；松手达到既有 42% 阈值才固定展开。三个动作只保留可访问的图标（置顶、重命名、删除），统一在同一 48dp 行高内，不单独拉高对话栏；批量选择没有侧滑操作，保持其原有复选框高度。
- **左栏设置：** 普通、工作区和临时会话左栏的圆形设置入口明确固定为零 tonal/shadow elevation，去除静态硬边和阴影；保留白色面、圆形轮廓与正常按下反馈，不改左栏布局及橙色“新对话”。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 通过，`git diff --check` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk`（`66 / 0.3.0-p10j`）构建成功，v2/v3 签名校验通过，SHA-256 为 `2cef07af940368d73cc40d6b4c5a09f477ab1dd102f36f806fbd579c28cff3b0`。该候选未覆盖安装 OPPO、未运行仪器测试；仍需真机手动确认右滑跟手过程与外/内屏的视觉观感。

## 2026-08-24：输入框原生长按菜单、提示文案居中与前景去硬阴影（JVM/Release 已验证）

- **输入框：** Composer 的草稿输入改由原生 `EditText` 承担，ColorOS 因而会根据当前剪贴板和选中文本提供白色系统上下文菜单（粘贴、全选、自动填充等可用项），不再只暴露自动填充气泡。保持多行输入、草稿回写和无内容泄漏的边界；剪贴板为空时系统可合理省略“粘贴”。`回复 南枫AI` 关闭字体预留并采用垂直居中重力，最小内容高与同一行信息对齐，修正视觉上偏下的问题。
- **视觉：** 顶部控制、模式胶囊、Composer、左栏设置和普通会话行保留亮白表面，但移除静态描边与阴影；中性灰 `#F1F1F1` 底层负责层级区分。弹窗/菜单仍保留必要的浮层关系，左栏“新对话”继续是橙色。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过；正式包 `app/build/outputs/apk/release/南枫AI.apk`（`66 / 0.3.0-p10j`）构建成功，v2/v3 签名校验通过，SHA-256 为 `28a799690f4879a48d41ba2251593af89c91a73ef40c737fdb65b2b789f2a5c8`。该候选尚未覆盖安装 OPPO，未运行仪器测试；真机仍需手动确认 ColorOS 实际菜单内容与外屏/内屏居中观感。

## 2026-08-24：右上新对话图标语义修正（JVM/Release 已验证）

- **实现：** 有内容会话右上“新对话”不再使用仅代表编辑的 `Icons.Outlined.Edit`，改为左栏橙色“新对话”已使用的 `ic_lucide_file_pen` 图标。该资源是圆角方框加笔，路径声明圆形 line cap 与 round join；仍调用原 `onCreateConversation`，没有改动新建逻辑、触控区或更多菜单。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，正式候选 `app/build/outputs/apk/release/南枫AI.apk` 已构建并经 v2/v3 正式证书校验，SHA-256 为 `d46cef4537131aa53c6bae052f92f6ddd6ab743a69526374bdf002e5129d174e`。此候选未安装 OPPO；设备上仍是上一轮中性灰背景包，需下一次同签名覆盖后才能做真机图标观感验收。

## 2026-08-24：对话与左栏的中性灰背景层级（JVM/Release/OPPO 已验证）

- **视觉实现：** 会话主画布和抽屉承托面统一为无色相的 `#F1F1F1`，前景的 Composer、顶部控制、设置入口、会话行和 Dialog 继续为亮白。顶部胶囊、圆形控制、Composer 与左栏设置按钮使用同一 `#E2E2E2` 细描边、`#767676` 图标和低短阴影；不再以近黑轮廓补层级。左栏“新对话”显式保持 `AccentOrange`，不被本次白卡规则覆盖。
- **边界：** 只改 Android 对话页、抽屉及对应全局页面底的视觉令牌；不改变导航、手势、按键可达性、消息、模型、弹窗逻辑或任何 Provider/Key/网络行为。
- **验证边界：** 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，Debug Kotlin 编译通过。正式包 `app/build/outputs/apk/release/南枫AI.apk`（版本 `66 / 0.3.0-p10j`）经 v2/v3 正式证书校验，以同签名 `pm install -r --user 0` 覆盖至 OPPO Find N5；首次安装时间仍为 `2026-08-20 15:15:31`，未卸载或清数据，设备实际 APK 与本地 SHA-256 均为 `f8e580cc370a3b9c6e112580f265a5b0a34475ccb7efcfa7d124d2ce72cff2d5`。未运行仪器测试；真机仍需手动比较外屏/内屏实际白卡与灰底对比、系统栏及按压反馈。

## 2026-08-24：助手消息底部信息与模型手动选择收口（JVM/Release 已验证）

- **助手消息页脚：** 复制、分享、创建分支共用一个 44dp 点击面和 20dp、低对比度图标；不再给分支单独着色。助手页脚整体居左，三个图标、时间和模型名只占同一行。展示层去掉“模型：”以及 `OpenRouter` 接收方冗余，保留实际模型的简短名称；消息长按菜单和可追溯 attribution 原值不改。
- **模型偏好语义：** 选择“自动”才允许内容参与自动路由；选择任意具体模型后，后续内容固定走该模型，不会跳回自动。每次选择同时写入当前会话和全局 Composer 默认值，所以新会话延续上次手动或自动选择；既有发送记录的 route attribution 不被改写。
- **验证边界：** 离线 `P6GModelRouterContractsTest`、`P3JNormalChatExplicitEgressContractsTest`、`P6DConversationRowAccessibilityContractsTest`、`P6GUnifiedChatFirstUiContractsTest` 通过，Debug Kotlin 编译通过；本地正式候选 `app/build/outputs/apk/release/南枫AI.apk` 已构建，v2/v3 正式证书校验通过，SHA-256 为 `3d474edb60df0e0e4d8db02c3c08b204e970c6de522be88febc19245536c1470`。未覆盖安装 OPPO、未运行仪器测试；真机仍需手动确认一行在折叠屏外屏/内屏的视觉密度。

## 2026-08-24：OPPO Find N5 正式包同签名覆盖安装（已完成）

- **设备与包：** OPPO Find N5（`PKH120`，Android 16）上的 `com.nanzhufeng.ai`，从 `66 / 0.3.0-p10j` 同版本正式签名包覆盖更新至本轮构建；未运行任何 `connected*AndroidTest`、Debug/仪器部署、卸载或清数据。
- **签名与字节：** 本地 `app/build/outputs/apk/release/南枫AI.apk` 经 v2/v3 正式证书校验；SHA-256 为 `29247259947dd507a8f375d52fe226023afb71558c49ae14818673a2e92649e1`，覆盖后设备 `/data/app/.../base.apk` 回读 SHA-256 完全一致。系统 `firstInstallTime` 保持 `2026-08-20 15:15:31`，仅 `lastUpdateTime` 更新为 `2026-08-24 03:16:23`，数据保留边界成立。
- **验证边界：** 这证明同签名覆盖与字节一致，不代替本轮弹窗近满宽和复制触感的真实交互验收；后续仅需在该设备手动确认长文本编辑、复制触感和系统触感关闭后的静默行为。

## 2026-08-24：Android 对话复制成功触感（JVM 已验证）

- **实现：** 对话内的消息弹窗复制、助手结果快捷复制、代码块和表格复制收敛至唯一 `rememberConversationCopyTextAction`。它仅在 `LocalClipboardManager.setText` 正常返回后，调用系统 `performHapticFeedback`：Android 11+ 使用确认触感，旧版本回退为键盘轻触。系统关闭触感或设备不支持时由系统自然忽略，无需新增权限。
- **边界：** 不把点按本身或复制失败误作成功；不读取、上传、记录剪贴板内容，不新增提示、入口或网络调用。Desktop 没有同一 Android 系统触感 owner，不能称为跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（共享复制 owner、写入后触感、Android 版本回退和四个复制入口）与 `P6GUnifiedChatFirstUiContractsTest` 通过，Debug Kotlin 编译同轮通过。本轮正式 APK 已同签名覆盖至 OPPO，未运行仪器测试；安装不证明实体马达反馈，仍需手动确认触感强度与系统“触感反馈”开关关闭后的静默行为。

## 2026-08-24：Android 编辑消息/创建分支弹窗近满宽（JVM 已验证）

- **实现：** “编辑消息”不再借用 Material 默认窄 `AlertDialog`，改为全窗口 Dialog 中的独立白色编辑面；卡片距离左右屏幕各 12dp，因而在手机上接近满宽。保留 24dp 圆角、白色表面、原有取消/创建分支动作和编辑文本；文字框从 3 行起，最多 580dp 高，超长内容仍由文字框本身承载。
- **边界：** 仅调整 Android Compose 该编辑分支弹窗的阅读宽度和自适应高度；不改编辑结果、分支创建、消息、附件、Provider、Key、网络或其它确认弹窗。仍复用全局弹窗空白点击和两侧向内滑关闭逻辑。Desktop 没有同一 owner，不能称为跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（近满宽 surface、3 行起始编辑框、既有动作路由）与 `P6GUnifiedChatFirstUiContractsTest` 通过，Debug Kotlin 编译同轮通过。本轮正式 APK 已同签名覆盖至 OPPO，未运行仪器测试；真实设备仍需手动核对外屏长文本编辑、软键盘与折叠屏宽度。

## 2026-08-24：Android 全部弹窗的空白点击与侧边向内滑关闭（JVM 已验证）

- **实现：** 新增唯一的同包 `AlertDialog` / `Dialog` 包装器；全部现有 Android Compose 标准和自定义 Dialog 自动复用 Material/Compose 原有 `onDismissRequest`（含空白遮罩点击），并在所属 Dialog 窗口根部以非消费式监听附加边缘关闭。Composer 的同窗口模型/附件覆盖层另复用同一阈值的全屏遮罩 modifier。手势仅在左/右可见边缘 56dp 内起手、向内横移至少 72dp、且横向位移至少为纵向的 1.3 倍时关闭；普通弹窗内的滚动、图片缩放、视频控制和横向内容不会被该监听消费。
- **关闭语义：** 原来加载/运行中的知识、导入、导出、离线 Eval 和调用记录面现在也允许关闭其界面，任务本身不被伪装为取消，仍由既有本地 owner 继续或可从原入口重新查看。Memory 冲突面关闭只丢弃本次尚未写入的候选，不会隐式并存、覆盖或删除已有记录。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（共享包装器、56/72dp 阈值、横向比例、所有 UI 源不再绕开包装器、无空 `onDismissRequest`）和 `P6GUnifiedChatFirstUiContractsTest` 通过。未生成/安装 APK、不操作模拟器或 OPPO；真实设备仍需验证系统边缘返回区与应用内近边缘起手的实际分界、图片预览缩放和模型长列表滚动。

## 2026-08-24：Android Composer 回车换行与草稿精确保留（JVM 已验证）

- **根因与实现：** `ComposerDraftTextField` 明确使用多行 `ImeAction.Default`，发送仍只由可见发送按钮触发。更关键的是，普通会话的 `ConversationDraftPolicy.normalize` 过去会对每次异步保存执行 `trim()`；用户按下回车后尚未输入下一字的末尾 `\n` 会被保存回写删除，造成“回车无反应”。草稿现在按编辑器原值保存，因此前导/尾随空白与所有换行在输入、异步回写和本机重读时一致保留。
- **审计范围：** 临时会话草稿本来就逐字保存，不走该裁剪；会话编辑、手工文本、Knowledge、Memory、项目指令、导入正文等多行 `OutlinedTextField` 未指定 `singleLine`，也没有拦截 Enter。搜索、标题、重命名、标签、URL、确认文本等单行字段维持原有单行/搜索语义。
- **验证边界：** Android Studio JBR 离线 `P3DConversationDraftRoomContractsTest`（含行尾换行持久化回读）、`P6DConversationRowAccessibilityContractsTest`（Composer 多行 IME 合同）与 `P6GUnifiedChatFirstUiContractsTest` 均通过，`git diff --check` 通过。未生成/安装 APK、不操作模拟器或 OPPO；真实设备仍需验证系统键盘按回车后光标进入下一行、再次输入和切换会话后草稿保持一致。

## 2026-08-24：Android Composer 分层模型选择面（JVM 已验证）

- **实现：** `ComposerMenuOverlay` 保持同一无 Popup 的 Composer 同级 overlay owner，但模型选择面从 248dp 扩至 336dp，使用 16% 中性遮罩、28dp 圆角高明度白卡、14dp 阴影与 36×4dp 顶部把手。根层为“选择模型”，分为“自动选择”和“按任务选择”；分类层显示“选择具体模型”，顶部关闭/返回控制固定，选项以 64dp 圆角行展示名称、用途说明、右箭头或橙色勾选，长列表仅在面内滚动。
- **边界：** 所有 `ComposerModelSlot`、具体 `ComposerModelChoice`、Auto/对比/手动持久化与深度检索接收方说明均复用既有 owner；只是更换布局和视觉层级。遮罩外点击、关闭和返回不发送、不中断草稿、不读 Key、不调用 Provider。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（新增分层模型面合同）与 `P6GUnifiedChatFirstUiContractsTest` 通过，Debug Kotlin 编译通过。未生成/安装 APK，未操作模拟器或 OPPO；目标设备仍需检查 336dp 面宽、长模型名、深度检索说明和折叠屏的实际视觉层级。

## 2026-08-24：Android 有内容对话的右上新对话与完整菜单（JVM 已验证）

- **实现：** 只要当前普通或工作会话已有消息，`ConversationShellHeader` 就隐藏居中的“对话 / 工作”切换，改在右上显示一个 44dp 高白色胶囊，含新对话（编辑图标）与更多（三点）两个触控面；空会话保留原中间切换和临时聊天入口。更多 Popup 显示当前会话标题以及分享、置顶、项目归属、已上传文件、聊天内查找、添加到主屏幕、归档/恢复、删除八项图标操作。
- **真实 owner：** 分享走 Android 系统纯文本 chooser；已上传文件只投影当前消息中的真实本地附件并复用现有预览；查找只遍历当前已呈现消息并滚到结果；置顶、项目、归档和删除继续委派既有 `ConversationManagementAction`。Android O+ 主屏快捷方式只携带 `CONVERSATION_SHORTCUT_ID_EXTRA`，启动后 `openConversationShortcut` 只打开仍存在、未归档、未进回收站的对应会话；桌面最终添加仍由系统确认。
- **边界：** 不新增 Composer 按键、不读取 Key、不触发 Provider、不会上传附件；删除依旧进入回收站。临时恢复会话没有同一持久会话/管理 owner，保持原有临时顶部结构。Desktop 没有同一 Android Compose/Launcher owner，不能宣称跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，Debug Kotlin 编译通过。未生成/安装 APK，未操作模拟器或 OPPO；真实设备仍需验证系统 pin-shortcut 确认、系统分享面、长标题菜单定位与折叠屏顶部触控。

## 2026-08-24：Android 对话初始内容避开顶部悬浮栏（JVM 已验证）

- **根因：** 普通、工作和临时会话的 `LazyColumn` 只有 Composer 所需的 86dp 底部内容内边距；菜单、对话/工作切换和临时聊天按钮作为 `Box` 同级悬浮层，不占列表布局行，因此首条内容会直接从顶部控件下方经过。
- **实现：** 三个列表统一使用 `ConversationTranscriptContentPadding`（顶部 64dp、底部 86dp）。顶部距离是随 `LazyColumn` 一起移动的内容 inset，不增加白色背景、固定安全区或新的布局行；短内容从顶部控件下方开始，内容足够长时继续上滑即可进入控件下方区域。
- **边界：** 顶部仍是三个独立的悬浮交互表面；Composer、跳到最新、右侧位置条、消息/会话数据、附件、Provider、Key 与网络语义均不变。Desktop 没有同一 Compose owner，不能宣称跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 均通过，Debug Kotlin 编译同轮通过。未生成/安装 APK，未操作模拟器或 OPPO；实际短对话首条位置、超长对话上滑和折叠屏视觉仍需后续目标设备验收。

## 2026-08-24：Android 设置分层返回栈（JVM 已验证）

- **根因：** 设置原先只有单个 `settingsDestination`，但“导入中心”和“更多本地控制面”会直接改全局 `P5ARoute`；这些 route 没有同一父级路径与系统返回 owner，深层页的 Back 可能跳出设置到对话抽屉。
- **实现：** `SettingsNavigationEntry` 以 `route + destination` 保存明确栈：设置主页 → 分类 → 导入/控制 route → 子工作区。`openSettingsLevel` 只在进入下一层时压栈；顶部返回、系统 Back、页内返回和设置边缘返回统一调用 `returnFromSettings`，先弹出当前一层并恢复父 route/分类。`ADAPTERS` 中的 ChatGPT、Claude、南枫知识导入任务被识别为更深一层，先回任务列表，再允许 route 退回导入分类。栈只剩根设置主页时，才重置对话管理筛选并回到对话抽屉。
- **边界：** 不新建业务数据、不改变导入、备份、模型、隐私、Provider、Key、网络、会话或系统返回手势语义；只修正 Android Compose 设置导航所有权。Desktop 没有同一 owner，不能宣称跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（设置栈、导入任务优先返回与既有设置语义）、`SettingsUiSimplificationContractsTest`、`AndroidUserEntryAuditContractsTest`、`P5AAdaptiveNavigationContractsTest` 均通过，Debug Kotlin 编译同轮通过。未生成/安装 APK，未操作模拟器或 OPPO；真实设备仍需覆盖五层以上路径、弹层与折叠屏返回手感。

## 2026-08-24：Android 左侧抽屉严格横向手势（JVM 已验证）

- **根因：** `ModalNavigationDrawer` 默认开启打开手势，模型菜单和长正文的垂直拖动只要带少量右移，就可能被抽屉竞争并打开；这使 70–80° 的近乎竖直手势仍会误入导航。
- **实现：** 抽屉关闭时禁用 Material 默认打开拖动，画布改由 `openConversationDrawerOnStrictHorizontalGesture` 单一 owner 处理：仅累计右移至少 56dp，且 `abs(vertical) ≤ horizontal × 1.19`（相对水平不超过 50°）时才请求打开；超过该角度或子控件已消费手势时立即放弃，原始事件继续交给正文、模型菜单等垂直滚动容器。抽屉打开后继续启用 Material 原生关闭拖动与遮罩。
- **边界：** 左上角菜单按钮、返回关闭、模型弹窗、正文/菜单上下滚动、系统边缘手势、会话数据、Provider、Key、网络与发送语义均不改变，不新增入口或按键。Desktop 没有同一 Compose drawer owner，不能宣称跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（50° 比例、56dp 阈值、子控件优先与抽屉状态合同）和 `P6GUnifiedChatFirstUiContractsTest` 均通过，Debug Kotlin 编译同轮通过。未生成/安装 APK，未操作模拟器或 OPPO；模型菜单、长正文和折叠屏上的真实拖动手感仍需后续目标设备视觉验收。

## 2026-08-24：Android 对话右侧位置条按真实内容高度定位（JVM 已验证）

- **根因：** 原 `transcriptScrollMetrics` 把 `LazyColumn` 的位置折算为“首个可见气泡索引 / 假定每屏四项”。一个长消息在同一索引内滚动时，只能贡献很小的比例，视觉上近乎停住；跨到下一气泡才会发生明显跳变，不能表达真实阅读位置。
- **实现：** 新增会话作用域的 `rememberTranscriptMeasuredItemHeights`：从 `LazyListLayoutInfo.visibleItemsInfo` 记录 Compose 实测行高。位置条以实测行高、实测项间距、首项的实际像素偏移和 viewport 高度计算范围及进度，内容包括文字换行、日期分隔、媒体及操作行；未知的懒加载行仅以已测量行高的中位数作稳定估计，并限制为不高于 viewport 三分之一，避免一个刚出现的超长气泡把所有未知项错估为同样长。长气泡一旦进入视口，其完整测量高度立即进入进度模型；到真正没有可继续滚动的位置才置为 100%。位置条初始可见、停止滚动 1.2 秒后以 180ms 淡出，任何用户或惯性滚动都会立即取消淡出并恢复显示。
- **边界：** 仅替换 Android 普通对话 `LazyColumn` 的非交互式视觉位置指示，不改变消息数据、滚动手势、跳到最新消息、发送、附件、Provider、Key 或网络语义，不新增入口或按键。Desktop 没有同一 Compose owner，不能宣称跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（实测行高、长气泡像素偏移和旧按项计数回归合同）与 `P6GUnifiedChatFirstUiContractsTest` 均通过，Debug Kotlin 编译同轮通过。未生成/安装 APK，未操作模拟器或 OPPO；实际外屏超长文本、折叠屏和整段未曾进入视口的历史内容的视觉连续性仍需后续目标设备验收。

## 2026-08-24：Android Composer 具体模型名完整显示（JVM 已验证）

- **实现：** `ComposerModelEntry` 从 64dp 扩至 216dp，标签文字调整为 13sp。普通与临时 Composer 均由实际 `ModelPresetId` 列表生成标签，因此只显示具体模型名；自动模式显示当前本地能力路由的实际模型，不再写入 `Auto`/路由说明；对比模式用两个具体模型名以 `/` 连接，完整保留双模型归属。
- **边界：** 点击标签仍仅打开原有模型菜单；Provider/官方联网说明仍在模型菜单和消息归属显示，未伪装为模型名。输入、发送、附件、模型选择持久化、Provider、Key 与网络语义不变，不新增按键。Desktop 没有同一 Android Compose owner，不能宣称跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（具体模型标签、双模型分隔、216dp 宽度与 13sp 字体合同）和 `P6GUnifiedChatFirstUiContractsTest` 均通过，Debug Kotlin 编译同轮通过，`git diff --check` 通过。未生成/安装 APK，未操作模拟器或 OPPO；实际外屏输入宽度、双模型全量显示和折叠屏布局仍需后续目标设备视觉验收。

## 2026-08-24：Android 全文件预览顶部操作栏单击切换（JVM 已验证）

- **实现：** 图片、PDF、视频、音频和文本预览统一通过 `rememberFilePreviewChromeState` 管理顶部关闭、下载、分享按钮：内容区单击切换显示/隐藏，显示后沿用 12 秒自动收起。视频的中央播放/暂停、底部时间轴和顶部栏均使用同一状态；视频单击已隐藏的顶部区域只会显示栏，不会误发下载/分享/关闭。
- **边界：** 顶部按钮显示时仍各自执行关闭、下载或分享；PDF 翻页、视频双击播放/暂停与边缘滑动关闭、图片缩放、文本选择和音频播放不改变。不新增入口、按键、外发、Provider、Key 或附件 owner。Desktop 没有同一 Android 预览 owner，不能宣称跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6F2DVideoPreviewUiContractsTest`（新增全文件共享单击状态）、`P6F2CPdfPreviewUiContractsTest`、`P6F2EAudioAndTextPreviewUiContractsTest`、`AttachmentTransferCompatibilityContractsTest` 与 `P6F2BImagePreviewUiContractsTest` 的原图画布方法均通过，Debug Kotlin 编译同轮通过，`git diff --check` 通过。完整 `P6F2BImagePreviewUiContractsTest` 的另一条草稿投影旧合同当前仍期待 ViewModel 已不存在的三条 notice，和本次预览手势无关，未改动附件数据链。未生成/安装 APK，未操作模拟器或 OPPO；各文件的真实单击命中、图片缩放冲突和折叠屏视觉仍需后续目标设备验收。

## 2026-08-24：Android 抽屉与对话正文文字放大（JVM 已验证）

- **实现：** 新增唯一的 `ConversationTextScale`（`1.1f`）Typography 映射。它包裹 `ModalDrawerSheet`，因此普通、项目、临时抽屉与批量编辑等所有抽屉文字一致放大；它只包裹 `MessageBubble` 的 `textContent`，因此已发送消息的标题、段落、引用、列表、代码块、表格和安全摘要按同一比例放大。段落/列表、代码和表格的显式行高也同步扩大。
- **边界：** Composer 输入、图标、触控目标、附件预览、消息操作、领域数据、Provider、Key 和发送逻辑不变；不新增入口或按键。Desktop 没有同一 Android Compose owner，不能宣称跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（补充统一 1.1 倍排版、显式行高与会话行文字合同）和 `P6GUnifiedChatFirstUiContractsTest` 均通过，`git diff --check` 通过；Debug Kotlin 编译同轮通过。未生成/安装 APK，未操作模拟器或 OPPO；实际抽屉长标题截断、对话长段落和折叠屏观感仍需后续目标设备视觉验收。

## 2026-08-24：Android 用户消息胶囊气泡（JVM 已验证）

- **实现：** 普通、工作区和临时会话复用的 `RightAlignedUserBubble` 改为 `RoundedCornerShape(50)`，因此用户已发送的文字表面为真正的 50% 圆角胶囊。内容仍按自身宽度测量，长文本仍限定在可用区域的 82%；原有橙色语义、原地文字选择、边缘长按操作与独立附件预览组均未改变。
- **边界：** 仅变更 Android Compose 的用户文本视觉形状，不新增入口或按键，不读取/上传内容，也不改动消息、附件、Provider、Key、发送或删除逻辑。Desktop 没有同一 owner，不能宣称跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（补充胶囊形状与内容驱动宽度合同）和 `P6GUnifiedChatFirstUiContractsTest` 均通过，`git diff --check` 通过；Debug Kotlin 编译同轮通过。未生成/安装 APK，未操作模拟器或 OPPO；圆角实际观感与折叠屏宽度仍需后续目标设备视觉验收。

## 2026-08-24：Android 左侧会话行右滑管理入口（JVM 已验证）

- **实现：** 普通对话与工作区抽屉的会话行现在支持向右滑动，露出置顶/取消置顶、重命名、删除三个带图标和文字的高对比按钮；抽屉仅允许同时展开一条。按钮没有新的领域 owner：置顶仍调用 `ConversationManagementAction.PIN/UNPIN`，重命名仍进入既有紧凑输入框，删除仍进入既有“移入回收站”确认弹窗；原长按 Popup 保留。
- **边界：** 行点击仍只打开会话，删除不会一键执行或物理删除；手势不读取/上传内容、不改变 Provider、Key、附件或现有会话管理语义。Desktop 没有同一 Android 手势 owner，不能宣称跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（新增右滑三按钮、已有长按 Popup、颜色与无障碍合同）和 `P6GUnifiedChatFirstUiContractsTest` 均通过，Debug Kotlin 编译同轮通过。未生成/安装 APK，未操作模拟器或 OPPO；实际抽屉滚动、折叠屏和右滑手感仍待后续目标设备视觉验收。

## 2026-08-24：Android 相机附件改为完整原图链（JVM 已验证）

- **根因：** 普通与临时对话此前均使用 `TakePicturePreview()`；系统只回传预览 Bitmap，ViewModel 再以 JPEG 92 压缩后存入附件链，因此相机拍摄内容先天低清，私有复制并不能恢复原图。
- **实现：** 两个 Composer 相机入口均改为 `TakePicture(Uri)`，输出先写入 App 受控 `cache/camera_capture` 的 FileProvider URI。普通和临时 owner 均用现有 `AndroidGallerySelectionReader.openConversationVisual` 读取完整原始流，复用像素/MIME/私有复制/hash/草稿校验；无预览 Bitmap 或二次 JPEG 压缩路径。成功、拒绝、取消和会话切换均会删除该受控临时源。
- **边界：** 相机打开和本地私有复制不上传、不外发；只有现有用户点击发送才按既有规则处理仍在准确草稿中的附件。Desktop 没有 Android Camera owner，不能称为跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（完整 URI 拍摄、原图私有导入、临时源清理、无预览压缩回退）、`P6MAttachmentMultiSelectUiContractsTest` 与 `P3GConversationAttachmentContractsTest` 均通过，Debug Kotlin 编译同轮通过。未生成/安装 APK，未操作模拟器或 OPPO。真实设备仍需对同场景原相机照片与草稿预览的像素/清晰度进行视觉验证。

## 2026-08-24：Android 普通对话抽屉批量编辑与回收（JVM 已验证）

- **实现：** “最近”标题右侧新增批量编辑铅笔。编辑态为普通对话抽屉内的临时状态：置顶和最近列表均显示复选框，底部在设置/新对话上方显示“全选、删除 N、完成”；编辑期间会关闭行右滑并禁用长按，以免多个管理入口冲突。完成仅退出编辑，不写入会话。
- **批量真值：** 删除前显示一次“移入回收站”确认。确认后 `ConversationFoundationViewModel.softDeleteConversations` 去重已选会话，并逐条复用 `ManageConversationUseCase + SOFT_DELETE` 的独立、幂等 intent；成功、并发版本拒绝和全部失败均以中文 notice 如实汇总。不会物理删除消息、附件或调用关联，回收站恢复保持既有 owner。
- **边界：** 当前仅适用于普通对话的“最近”抽屉，不把项目工作区或临时会话错误混入批量选择；不读取/上传内容、不触碰 Provider、Key、附件或现有单条删除语义。Desktop 无同一 Android owner，不能称为跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest`（新增批量编辑、复选框、全选/删除计数、确认、软删除 owner 与长按隔离）和 `P6GUnifiedChatFirstUiContractsTest` 均通过，Debug Kotlin 编译同轮通过，`git diff --check` 通过。未生成/安装 APK，未操作模拟器或 OPPO；实际复选框对比度、长列表滚动和折叠屏底部锚定仍待目标设备视觉验收。

## 2026-08-24：Android 视频预览点击即播放（JVM 已验证）

- **实现：** `VideoPreviewDialog` 的本地受控副本准备完成后，会先恢复既有位置，再直接调用 `start()` 并同步为播放状态；因此点击会话或 Composer 中的视频卡进入预览后，不再需要第二次点击播放。中央暂停、底部进度、双击切换、边缘滑动关闭和关闭时的位置保存仍由原有单一手势/预览 owner 负责。
- **边界：** 仅播放已通过现有私有副本链交给预览的本地视频；不新增入口、不上传、不外发、不后台播放，也不改变下载或分享行为。Desktop 没有同一 Android `VideoView` owner，不能写作跨端完成。
- **验证边界：** Android Studio JBR 离线 `P6F2DVideoPreviewUiContractsTest`（5 项）与 `AttachmentTransferCompatibilityContractsTest`（3 项）均通过，且 `compileDebugKotlin` 同轮通过。未生成/安装 APK，未操作模拟器或 OPPO，真实首帧与声音仍需后续目标设备验收。

## 2026-08-24：Android Composer 图片/视频与文件多选（JVM 已验证）

- **实现：** 普通聊天和临时会话的“添加图片和视频”均改用 Android `PickMultipleVisualMedia`，限定现有每草稿最多 4 项并允许 `ImageAndVideo`；“添加文件”均改用 `OpenMultipleDocuments`。视觉选择器返回的图片仍先作像素安全检查，视频仍限定 MP4；文件则继续只允许现有音频、PDF 与安全文本类型。
- **批次真值：** 每个 URI 都通过现有本地 reader、私有复制、hash/大小/MIME 校验和草稿 owner；同批有效项不会因另一项失败而回滚，重复项不再重复添加，超限或拒绝项以中文汇总。选择本身不发送给 AI 或第三方。
- **系统 UI 边界：** 截图中的格子勾选框由 ColorOS Photo Picker/DocumentsUI 绘制；多选契约会让其显示选择控件和数量，但 App 不可安全重绘或加深该系统控件。App 内入口明确改为“添加图片和视频”。
- **验证边界：** Android Studio JBR 离线 `P6MAttachmentMultiSelectUiContractsTest`（3 项）、`P3GConversationAttachmentContractsTest`（3 项）与 `P6DConversationRowAccessibilityContractsTest`（65 项）均通过，`compileDebugKotlin` 同轮通过。未生成/安装 APK，未操作模拟器或 OPPO，系统弹窗勾选框的实际可见性与对比度仍待目标设备验收。

## 2026-08-24：Android Composer 系统文字工具条按焦点自动收起（JVM 已验证）

- **实现：** 对话画布仅在未被消息、链接或控件消费的空白点击上清除 Composer 焦点；切换会话/表面与 Activity `ON_STOP` 同样清焦点。因此 Android/ColorOS 原生“粘贴 / 自动填充 / AI 写作”工具条不会作为应用状态回到前台。现有南枫自有附件/模型菜单继续使用白色 `Surface`。
- **启动键盘：** `NanfengAiActivity` 的系统软键盘模式改为 `adjustResize|stateAlwaysHidden`，每次首次显示或从后台获得窗口焦点时默认保持键盘关闭；用户点输入框后仍可正常打开键盘。该标志与既有 `ON_STOP` 清焦点互补，未新增任何 UI 入口。
- **颜色边界：** 原生文字工具条由 ColorOS/输入法控制，Android App 不能安全指定它的背景色；没有伪造或接管原生菜单，以免丢失系统粘贴、选择与 AI 写作动作。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，Debug Kotlin 编译通过。未生成/安装 APK，未操作模拟器或 OPPO；实际 ColorOS 工具条收起和配色仍需后续目标设备视觉验收。

## 2026-08-24：Android Composer 按文本自然展开至十行（JVM 已验证）

- **实现：** `ConversationComposerDock` 从固定 `60dp` 行改为 `60dp → 224dp` 的内容驱动高度；`ComposerDraftTextField` 使用 1–10 行边界与内部垂直滚动。外层白色悬浮胶囊、附件/模型/发送控制仍为同一表面，且控件按底边对齐；既有 `floatingComposerHeight` 继续测量实际高度供浮层位置使用。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过。未生成/安装 APK，也没有在 OPPO 或目标视口实际输入十行以上文本，因此视觉和键盘滚动仍待后续验收。

## 2026-08-24：助手消息创建分支具备可见成功反馈（JVM 已验证）

- **实现：** 助手消息尾部将原“移动文件”图标替换为 AutoMirrored 分叉路径图标；分支复制并切换到新本地 Conversation 后，`ConversationBranchCreationUi` 发出一次性屏幕事件，Composer 上方显示“已创建分支 / 已打开新的本地对话”，2.8 秒后自动收起。反馈具备 `LiveRegionMode.Polite`，不增加常驻入口、确认弹窗或网络行为。
- **验证边界：** Android Studio JBR 离线 `P6DConversationRowAccessibilityContractsTest` 与 `P6GUnifiedChatFirstUiContractsTest` 通过，Debug Kotlin 编译通过。未生成/安装 APK、未操作模拟器或 OPPO，因此尚未完成同视口的实际视觉验收。

## 2026-08-24：三条官方实时检索链已完成 Android 代码与 JVM 合同接线（未做真实账号/API 验收）

- **三条明确路径：** 复杂推理菜单中的 OpenRouter 模型使用 OpenRouter 的 `openrouter:web_search` 服务端工具；千问模型使用千问官方 Chat Completions 的 `enable_search=true`；DeepSeek V4 Pro 使用千问官方 `/responses` 的 `web_search` 工具，并以 DeepSeek V4 Pro 作为回答模型。DeepSeek 这条不再伪称“DeepSeek 官方检索”。
- **接收方真值与恢复：** `NormalChatSendAttempt.egressProviderId` 保存实际网络接收方，恢复重试沿用原 Attempt 的同一接收方和 idempotency key，不能因当前 Composer 切换而把历史 DeepSeek→千问请求改回 DeepSeek 直连。调用审计、诊断和上下文选择记录实际接收服务商；助手答案底部对该路线显示“模型：DeepSeek V4 Pro · 通义千问官方实时检索”。
- **界面与失败：** 深度模型菜单逐项显示“OpenRouter / 千问官方实时联网检索”或“DeepSeek 回答 · 千问官方实时检索”；缺少或禁用千问配置时，提示本次**实际接收服务商**未配置，避免错误引导到 OpenRouter。
- **验证边界：** Android Studio JBR 离线 `ProviderAdapterContractsTest`、`P6FTranscriptPresentationContractsTest`、`AssistantResponseModelAttributionRoomContractsTest`、`P5DLocalBackupRestoreContractsTest`、`P3JNormalChatExplicitEgressContractsTest` 共 35 项通过，Debug Kotlin 编译通过。未读取或使用任何 Key、未发真实 Qwen/DeepSeek/OpenRouter API、未生成/安装 APK、未操作 OPPO；因此尚不能把此处写作三家账号的实时服务验收。

## 2026-08-24：Android 助手结果底部已显示实际模型归属（JVM 已验证）

- **实现：** 新增无正文 `AssistantResponseModelAttribution`，以 `assistantMessageId + attemptId` 保存实际 Provider、模型 ID、固定显示名与时间。普通流在生成占位消息后、请求发出前绑定；对比和按原编号恢复在新助手消息落库前绑定每个实际 Attempt。`ConversationTranscriptPresentation` 只消费这个消息归属（本地 fixture 继续消费其已有谱系）；旧、导入或未绑定记录明确显示“模型信息未记录”，绝不从当前 Composer、Auto 或后续目录刷新猜测。
- **UI：** `AssistantMessageActionRow` 保留现有复制/分享/分支/时间右对齐，将模型单列置于同一底部右侧，避免长模型名压缩或遮挡操作。没有增加聊天主页、Composer、会话详情或设置常驻入口。
- **迁移：** Room schema `44 → 45` 为 Attempt 增加 `egressProviderId`，并新建含逻辑回答 Provider 与实际 `receiverProviderId` 的 `assistant_response_model_attributions`；同时把先前遗漏注册的 `43 → 44` 附件续传迁移补入正式 builder。两张表均不含对话正文、附件字节、URL、Provider 原始响应或 Key。
- **验证边界：** Android Studio JBR、离线 `P6FTranscriptPresentationContractsTest`、`AssistantResponseModelAttributionRoomContractsTest`、`P5DLocalBackupRestoreContractsTest`、`P3JNormalChatExplicitEgressContractsTest` 与 Debug Kotlin 编译通过。未生成 APK、未安装或操作 OPPO、未读取 Key、未发真实 Provider 请求；Desktop 尚无相同发送归属 owner，登记为待实现而非跨端完成。

## 2026-08-23：Android 对话、项目工作区与临时对话的左侧导航已分离（JVM 已验证）

- **根因与修复：** `ConversationFoundationViewModel.reload()` 曾把无 surface 过滤的总会话列表回填给抽屉；点开工作会话又强制写回 `CHAT`。现在普通/工作各按 `ConversationSurfaceRepository` 投影并保留独立选中 ID，工作区为空时不再隐式创建“工作”会话；临时恢复状态则切换为只含其自身说明和退出动作的抽屉，不复用任何持久列表。
- **项目树：** 工作抽屉的根入口为“项目”，提供项目列表菜单、创建项目，以及每个项目的置顶/编辑/归档菜单与“新建工作对话”。新建工作对话经既有 `CreateConversationUseCase` 持久化准确的 `projectId + WORK`；创建/管理项目仍经 `ProjectViewModel → ManageProjectUseCase`。未归属或已归档项目下的历史工作会话仍以明确分组展示，不被 UI 静默隐藏。
- **边界：** 仅借鉴 Codex 的项目分类信息架构，不接入外部文件夹、资源管理器或永久工作树；未触碰 Provider、Key、网络、OPPO 或既有本机数据。Desktop 尚未有同等项目抽屉，不能称为跨端完成。
- **验证：** Android Studio JBR、离线 `P6DConversationRowAccessibilityContractsTest`、`P6GUnifiedChatFirstUiContractsTest`、`FBP6042TopBarOwnershipContractsTest` 与 `P6JConversationSurfaceRoomContractsTest` 通过；其中 Room 回归实际建立项目、创建 `WORK` 会话并回读相同 `projectId`。尚未生成安装包或做隔离模拟器/真机视觉验收。

## 2026-08-23：OPPO 正式 code 63→64→65 同签名覆盖与启动修复（数据保留）

## 2026-08-23：自托管附件中转的可恢复协议骨架（未部署、未接入正式发送）

## 2026-08-23：OpenRouter 受控最小真实连通性验收（非 APK、无附件）

## 2026-08-23：会话 Markdown 行内强调修复（待正式包视觉验收）

- **根因与修复：** 会话安全 Markdown parser 原先只投影代码、链接与纯文本，导致模型输出的 `**粗体**` 标记作为普通字符显示。现将其升级为明确的 `InlinePresentation.Strong`，并将闭合的 `*斜体*` 作为独立 `Emphasis`；Compose 使用 `FontWeight.Bold` 与斜体 SpanStyle 渲染。未闭合、连续异常的星号不吞字、不改写原消息，且在同段后续仍可识别的强调继续有效；代码/链接路径保持优先和原有安全边界。
- **验证边界：** Android Studio JBR、离线 `P3DMessagePresentationContractsTest` 通过，覆盖截图所示双星号、单星号、未闭合标记与后续合法强调。当前只完成领域/Compose 编译级验证，尚未构建新正式 APK 或在 OPPO 同视口观察实际粗体字重。

- **真实服务证据：** 用户在当前受控会话明确提供其 OpenRouter 凭据后，仅以固定非敏感短句、`openai/gpt-4.1-nano`、`max_tokens=2`、非流式 `POST /api/v1/chat/completions` 进行一次最小调用；HTTP 状态为 **200**，总耗时约 **1.03 秒**。终端未打印或保存 credential、请求正文或响应正文。
- **结论边界：** 这证明该 credential、当前网络和 OpenRouter 基础 Chat API 在该时点可用；不证明已安装 Android APK 的设置读取、UI 提交、SSE、当前预设模型、Attempt 持久化、图片/PDF/视频或自托管中转已经在真实设备成功。后续真实 App 验收仍必须在正式同签名包内、以用户明确允许的非敏感材料执行。

- **已实现：** Android 新增每附件独立的 `ResumableAttachmentUpload` 真值与 Room 43→44 迁移：它绑定既有普通聊天 Attempt、附件 SHA-256、Provider/模型、已确认 offset 和不含 URL/令牌的网关 session ID。中断后的 coordinator 必须先查询 server offset，再从该位置重读经私有 store 验证的源文件继续；不新建聊天 Attempt、不换 Provider/模型、不从零静默重传。上传完成得到的短时 HTTPS URL 只在内存中交给 OpenRouter Adapter；OpenRouter 图片/PDF/视频各自按 URL part 序列化，Qwen Adapter 明确拒绝该 URL 路径，避免跨 Provider 格式污染。
- **自托管服务：** 新增仓库根目录 `upload-gateway/`，提供有状态的 `POST/HEAD/PATCH/complete/DELETE` offset 合同、长度/SHA-256 完整校验、短时 HMAC 签名下载 URL 与过期清理。服务没有 Provider API Key；端点、持久卷、网关 token、URL TTL 和大小上限全部为部署环境变量，未硬编码进 App 或 Git。实际部署仍需要用户控制的 HTTPS 域名、持久卷与运行时机密；本机没有 Go、gofmt、Docker 或 Podman，故不能把源码合同称作运行中的网关。
- **Android 验证：** Android Studio JBR 离线 `ResumableAttachmentUploadCoordinatorContractsTest`、`P5DLocalBackupRestoreContractsTest` 与 `ProviderAdapterContractsTest` 已通过。它们证明 offset 续传、不持久化 URL、Room 全迁移链与 OpenRouter URL 不会回退成 Base64；没有真实网关、Provider、Key、附件或 OPPO 被访问。
- **仍待、不得误称完成：** 将用户已部署的网关 endpoint/token 通过 Keystore 安全配置接入 Android 设置，并把 coordinator 真正接到普通发送的 exact Attempt；部署后用非敏感图片/PDF 做真实 OpenRouter 端到端验收。视频 URL 只能在网关配置和当前模型/底层 Provider 都明确支持时开启。Desktop 没有同等普通发送 owner，仍只登记为待实现。

- **根因与修复：** code 64 覆盖后标准 Launcher 启动即退出；系统 crash buffer 确认 `AppContainer` 构造期在主线程执行 `RoomNormalChatSendAttemptStore.markInterruptedAsUnknown`，Room 正确拒绝主线程数据库写入。code 65 将该恢复移到 `NanfengAiActivity` 的 `lifecycleScope(Dispatchers.IO)`，保持中断 Attempt 转 `UNKNOWN` 的语义。
- **产物与门禁：** 当前正式 APK 为 `com.nanzhufeng.ai / code 65 / 0.3.0-p10i`，SHA-256 `888c8698853744c6752e0f3afea02733a3aeffef264eb90f88f44131ba963644`；v2/v3 验签通过，证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。两次覆盖前后 `firstInstallTime=2026-08-20 15:15:31` 与 CE/DE inode `1459104/1433378` 均不变。
- **设备回读与严格边界：** code 65 的 `pm install -r --user 0` 返回 `Success`，设备回读 `base.apk` SHA-256 与本地产物完全一致；标准 Launcher 冷启动 `Status: ok`、`TotalTime=216ms`，进程仍在且前台焦点为 `NanfengAiActivity`。未运行 `connected*AndroidTest`、未卸载、清数据、读取私有业务数据、使用 Key 或发起 Provider 请求；这不替代真实联网、附件或 UI 业务验收。

## 2026-08-23：OPPO 正式 code 66 覆盖、流式附件请求体与恢复取消（数据保留）

- **本轮实现：** 普通 Chat 附件在发送前由私有 store 重验长度与 SHA-256 后，以不暴露路径的 InputStream 分段 Base64 写入固定长度 HTTPS 请求体；不再把源文件、Base64 和完整 JSON 同时留在内存。Qwen PDF 与 OpenRouter 图片各自保留独立 Adapter 序列化。原编号重试同样进入活动连接表，因此停止可实际断开重试请求并终止原 Attempt。
- **本机构建：** Android Studio JBR、`--offline --no-daemon` 下 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleRelease` 通过；161 份 JVM XML 无 failures/errors。release APK 为 `com.nanzhufeng.ai / code 66 / 0.3.0-p10j`，SHA-256 `2db830263e27c87fcfd620d50bdc626509402d2e8641239fa4cb9b8c07841dcd`，v2/v3 验签通过，证书 SHA-256 保持 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **OPPO 覆盖回读：** `3B157F009E800000` 从 code 65 同签名 `pm install -r --user 0` 到 code 66 返回 `Success`；覆盖前后 `firstInstallTime=2026-08-20 15:15:31`、CE/DE inode `1459104/1433378` 不变。设备 `base.apk` SHA-256 与上述本地产物一致；标准 Launcher 冷启动 `Status: ok`、`TotalTime=177ms`，焦点为 `NanfengAiActivity`。仅删除本次 `/data/local/tmp/nanfeng-ai-0.3.0-p10j-code66.apk` 推送临时文件；未运行 `connected*AndroidTest`、未卸载/清数据、未读业务数据、未用 Key 或请求 Provider。

## 2026-08-23：发送恢复竞态、混合 Tool Call 假成功与事实源冲突修复（待重新冻结 APK）

- **Attempt 恢复：** `AppContainer` 继续不在主线程写 Room。`NanfengAiActivity` 现先创建 `ConversationFoundationViewModel`，再在 `Dispatchers.IO` 将中断的 `PENDING/SENDING/ACCEPTED/STREAMING` Attempt 标记为 `UNKNOWN`；只要有变更便在主线程重新加载该 ViewModel。因此首次会话加载不会永久漏掉“上次发送结果未知 / 按原编号重试 / 标记失败”提示。
- **恢复取消语义：** “按原编号重试”同样注册到会话的活动连接表；停止会先断开其 HTTPS/SSE，再把原 Attempt 标记为 `CANCELLED`。它不再因恢复路径缺少新 Runtime 占位而成为不可停止的后台请求。
- **Tool Call 真实状态：** Adapter 对非 SSE 的正文、reasoning、usage 与 tool call 继续独立解析。普通聊天既没有已批准的工具注册表，也不会伪装执行：即使服务端同时返回正文与 `tool_calls`，也统一标记 `TOOL_CALL_UNSUPPORTED` 并保留 Attempt 失败事实，不能把未执行动作显示为完成回答。
- **长回复完整性：** Adapter 不再以固定字符数截断已接受的模型正文；模型档案的输出上限继续作为请求参数，正常回复完整进入会话。非 SSE 原始回包的内存上限从模型档案最大输出推导（含 UTF-8/JSON 余量，最高 8 MiB），不能再用固定 1 MiB 限制大模型的正常回复；这与用户可见正文的完整性分开。
- **当前规则事实源：** `MASTER_PLAN_COMPLETION_AUDIT_20260816.md`、`MASTER_DEVELOPMENT_BLUEPRINT.md`、本开发档案与项目理解报告已统一：附件选择/预览/草稿仅本机处理；用户点击发送即授权当前准确已提交附件给界面显示的 Provider/模型；普通聊天不得恢复早期逐条确认。旧 code 52/53/57 与逐次确认文字均只作历史记录，不得作为当前实现门禁。
- **验证及发布边界：** Android Studio JBR、`--offline --no-daemon` 下全量 `:app:testDebugUnitTest` 通过；161 份 XML 报告无 failures/errors，`P3JNormalChatExplicitEgressContractsTest` 为 6/6。此后源码已变，code 65 APK 仍是已安装、已验证的最后正式产物；不得把本次源改动宣称已进入 OPPO，下一包必须递增 version、重新构建、验签、同签名覆盖与 byte 回读。未运行 `connected*AndroidTest`、未操作 OPPO、未用 Key 或请求 Provider。

## 2026-08-23：模型能力与长附件传输复核（待重新冻结 APK）

- **已纠正的真实档案偏差：** Qwen3.7-Plus、Qwen3.6-Flash 为 1M 上下文 / 64K 输出，Qwen3.8-Max 为 1M / 128K；DeepSeek V4 Pro 的官方请求 ID 保持 `deepseek-v4-pro`，档案已由过期的 64K / 8K 修正为 1M / 384K，并保留流式、推理、结构化输出和函数调用能力。Auto 仍只从中心模型目录读取角色优先级，手动选择不因 Auto 偏好而丢失该模型能力。
- **完整文件与长回复：** Qwen 原生 PDF 的 `file_data` 保持完整文件，首次响应期限为 300 秒（其它流式请求 90 秒）；图片/视频/PDF 仍只在点击发送后出站。普通回复不再以 12,000 字符静默截断；非 SSE 原始回包内存上限按当前 `ResolvedModel.maxOutputTokens` 推导，避免固定 1 MiB 误拒大模型的正常长答。
- **附件内存边界：** 普通 Chat 的图片、PDF、视频不再先读成 `ByteArray`、再扩成完整 Base64 JSON；私有附件先校验大小与 SHA-256，随后经不暴露路径的 InputStream 分段 Base64 写入 HTTPS 请求体，并以精确 `Content-Length` 防止源文件发送中途变短/变长。此改动不把预览页或视频封面替换成原件，也不新增中转接收方。
- **本机验证与剩余边界：** `ProviderAdapterContractsTest`、`P3JNormalChatExplicitEgressContractsTest`、`ModelProfileAssetContractsTest` 通过；随后全量 `:app:testDebugUnitTest --offline --no-daemon` 通过。没有 Provider Key、真实文件、真机数据或 OPPO 被访问；code 65 APK 不含本节源码，仍须后续正式构建和同签名覆盖验收。

## 2026-08-23 D1：普通联网发送的可诊断、可取消与原子状态链（未做真实账号验收）

- **已落地：** 普通发送采用标准 SSE；静默流的读超时为 90 秒，HTTP 非 2xx 会保留受限、脱敏的错误摘要。模型 ID、HTTP 状态、端点主机、请求形状和耗时进入本机 `debug_call_log`（7 天保留），不记录 Key、正文、附件或原始回包；模型设置提供用户主动触发的固定 `hi` / 1-token 连接自检。
- **一致性与停止：** 普通单模型发送把用户消息、清空草稿、`PARTIAL` 助手占位、运行状态和 `RUN_STARTED` 事件放在同一 Room 事务；停止按钮会先断开当前 HTTPS/SSE 连接，再写入 `CANCELLED` 状态。一次点击只外发一次，自动路由仅在本机选择模型，失败不再静默换模型或跨服务商重发。
- **验证：** 离线定向 `:app:testDebugUnitTest` 覆盖 `P3BConversationRuntimeRoomContractsTest`（4）、`ProviderSseDecoderContractsTest`（1）、`ProviderDiagnosticsContractsTest`（2）、`P3JNormalChatExplicitEgressContractsTest`（3），共 10 项、0 失败；未运行任何 `connected*AndroidTest`，未安装 APK、未操作 OPPO，未发出真实 Provider 请求。
- **仍待：** 必须由用户在正式 App 中用自身配置执行“模型设置 → 测试连接”（固定 `hi`）和一次非敏感短文本普通发送，才能确认具体 Key、区域端点与实时模型 ID 的真实闭环；不要把本轮 JVM/Room/SSE 合同验证写成已真实联网成功。

## 2026-08-23 D2：普通聊天附件按“点击发送”授权，并传递完整文件（未做真实账号验收）

- **已落地：** 附件选择、私有复制、预览与草稿保存仍只在本机进行；用户点击发送后，仅将仍在该准确已提交草稿中的附件发送给本次选定服务商。删除后的附件、历史/资料库附件、预览海报以及日志/诊断均无出站路径。Composer 会在附件旁持续显示本次接收方，不增加确认弹窗或常驻按键。
- **完整性与模型门：** 图片按原始私有字节生成 `image_url`；PDF 按完整 `application/pdf` 原始字节生成 `file.file_data`，不再把首页渲染图代替文档；视频按完整原始字节生成 `video_url`，不再把封面图代替视频。OpenRouter 与 Qwen 均各自通过独立 Adapter 构造其 OpenAI-compatible Chat Completions 合同，不能再由 Qwen 继承 OpenRouter Adapter。PDF/视频一律由 `ResolvedModel` 的实际能力门决定：当前本机 Qwen 目录仅将 Qwen3.8-Max 标为 PDF 可用，不能把页面预览冒充全文；Qwen 原生 PDF 请求的首次响应期限按官方 300 秒设置，其他普通流式请求仍为 90 秒；DeepSeek 直连仍明确拒绝不支持的原始附件。
- **验证与边界：** Android Studio JBR 离线定向 JVM 合同 `P3JNormalChatExplicitEgressContractsTest`（3）与 `P3GConversationAttachmentContractsTest`（3）均 0 失败，且 Debug Kotlin 编译通过；未运行 `connected*AndroidTest`、未安装 APK、未操作 OPPO、未请求真实 Provider。尚未以真实账号/具体文件验证当前实时模型的文件大小、时长与供应商侧解析限制；失败必须如实显示，不能称作“已完整解析”。

## 2026-08-23 D3：Token-aware 本地检索、完整附件预算与可恢复模型目录（未做真实账号验收）

- **发送预算：** `ContextBudget` 先为当前用户文字、完整附件的本地输入估算和附件提示保留容量，再为近期原文与检索资料分配剩余 Token。`ResolvedModel.tokenizerId` 现随预算传入每个本地条目的估算；未知 tokenizer 只能使用明确标注的保守估算，不能伪称为服务商计费 Token。图片/PDF/视频的计划系数属于模型档案 `attachmentInputTokenEstimate`，不再散落在聊天执行器。当前用户文字不能被裁掉；固定输入本身超过所选模型上下文时，本机显示 `CONTEXT_LIMIT` 并停止，不创建网络请求。附件仍是完整原始文件，不会降级为 PDF 首页或视频封面。Qwen3.7-Plus、Qwen3.6-Flash 为 1M 上下文 / 64K 输出，Qwen3.8-Max 为 1M / 128K；DeepSeek V4 Pro 为 1M / 384K，并保留其工具调用、推理与流式能力。Qwen 普通 Chat 档案只声明文本/图片/视频，不把独立音频模型能力错误套入其中。
- **检索与历史：** `RoomLocalContextIndex` 使用 SQLite FTS5 维护 Memory、Knowledge 和历史索引；触发器负责增量同步，普通发送不遍历全库。索引写入中文二字检索项、按项目/会话范围过滤、跨资料去重，旧会话另存本地抽取式滚动摘要；最终只装入完整资料条目。索引异常时不回退为全量扫描，模型设置的去内容化选材诊断明确显示“本次未注入本地资料”。
- **模型目录与隐私：** Qwen/DeepSeek 档案超过 24 小时时仅请求对应服务商的模型列表，以本机既有能力档案更新 ID 可用性与时间；不携带对话、附件或 Prompt，也不静默换模型。只有模型档案中显式提供、且目录唯一命中的替代 ID 才能更新实际请求 ID，绝不按名称猜测或降级到同系列模型。缓存只能保留动态模型 ID、健康状态和检查时间；能力、上下文、输出上限、tokenizer 与附件预算一律重新合并最新已验证档案，防止旧缓存继续声称已被收紧的能力。OpenRouter 目录读取上下文和最大输出元数据。`context-selection-audit-v1.json`、模型目录缓存、健康记录和调用审计已纳入“删除全部本地业务数据”。
- **验证与边界：** Android Studio JBR、`--offline --no-daemon` 下定向 JVM 测试 `LocalContextBrokerContractsTest`（5）、`ModelProfileRefreshContractsTest`（2）、`LocalContextFtsMigrationContractsTest`（1）和 `ModelProfileAssetContractsTest`（1）通过；FTS5 合同在 host SQLite 实际执行建表、回填和触发器，不再因 Robolectric 缺失 FTS5 而跳过。未运行 `connected*AndroidTest`、未安装 APK、未操作 OPPO，未发起真实模型或文件请求；Qwen/DeepSeek 实时目录字段与供应商文件解析上限仍必须由用户自身账号在 App 内验证。

## 2026-08-23 D4：普通发送 Attempt 的显式恢复与 Provider Adapter 分界（未做真实账号验收）

- **Attempt 恢复：** 启动时中断的 `PENDING/SENDING/ACCEPTED/STREAMING` Attempt 仍标记为 `UNKNOWN`。会话内只对 `UNKNOWN/FAILED` 显示“按原编号重试 / 标记失败”：重试必须复用原始 `attemptId → providerId → modelId → idempotencyKey`，绝不偷偷换服务商、模型或新建编号；服务端去重能力未获证明时，界面明确提示可能重复调用或扣费。标记失败只关闭恢复提示，不删除 Attempt 事实。
- **恢复范围：** 原始用户消息与私有附件仍在已提交会话记录中，故网络中断后的完整请求可按原编号重建和再次发送；这不是伪称已支持每个服务商的二进制分片上传。真正的文件上传 offset/resume 只能在某服务商公开支持可恢复上传会话时，按该服务商专属协议再实现，不能向当前单次 Chat Completions POST 编造断点续传。
- **Adapter：** Qwen 已拥有独立请求构造；非 SSE 的 OpenAI-compatible JSON 改用独立严格 JSON codec，解析文本、reasoning、tool call 参数与 usage，不再依赖名称或结构都属于 OpenRouter 的 codec。SSE 传输层现在只负责事件分帧，各 Adapter 自己把 data JSON 投影成统一增量与用量，普通聊天不再硬性要求模型支持流式：无流能力的模型走同一 Adapter 的非 SSE 解码。普通聊天尚不执行 Provider tool call；无论 SSE 或非 SSE 收到纯工具调用都必须明确失败，不能把它伪装成回答。
- **验证：** 离线 `:app:testDebugUnitTest --offline --no-daemon` 全量通过；新增 `ProviderAdapterContractsTest` 覆盖 Qwen Adapter 独立性、非 SSE 文本/reasoning/tool call/usage 与模型档案附件预算，`ModelProfileRefreshContractsTest` 覆盖显式替代 ID，`P3JNormalChatExplicitEgressContractsTest` 覆盖原编号重试/标记失败入口。未运行 `connected*AndroidTest`、未安装 APK、未使用 Key 或发起真实 Provider 请求。

## 2026-08-23：正常对话兼容精细价格字段，OPPO 已覆盖 code 63

- **已修复：** 普通聊天错误复用了结构化任务的 OpenRouter 解码器；当服务返回的 `usage.cost` 具有微元以下精度时，旧代码会把整份成功回复误判为格式错误并丢弃。现在正文优先解析；价格精度或非整数 token 元数据不兼容时，只将对应审计字段记为未知，不影响用户看到回复。同时兼容上游返回的 typed text parts。
- **重复消息说明：** 截图中的两条相同用户消息分别来自两次已提交的发送尝试：第一次在旧错误链中已经实际外发但回包未显示，第二次是重试。历史记录不删除，以免把已经发生的真实外发伪造成未发生；修复后新的发送只会提交一次并正常显示回复。
- **本机与 OPPO：** 新增精细价格与 typed text 回包回归测试；全量 `:app:testDebugUnitTest :app:lintDebug :app:assembleRelease --offline --no-daemon` 通过（单测无失败、Lint 0 errors）。正式 APK 为 `com.nanzhufeng.ai / code 63 / 0.3.0-p10g`，v2/v3 同一 release 证书通过。OPPO `3B157F009E800000` 同签名由 code 62 覆盖到 code 63，未卸载、未清数据，`firstInstallTime=2026-08-20 15:15:31` 不变；设备 base APK 与本机 SHA-256 均为 `ae49929f59c1d33f7f35155c4b6696912ef2946f5ea7a3764a0066a8c6462931`，冷启动 `Status: ok`（181 ms）。

## 2026-08-23：普通发送自动刷新过期 OpenRouter 目录，OPPO 已覆盖 code 62

- **已修复：** 设备已保存但过期的 OpenRouter 模型目录，原先只会在目录完全缺失时刷新；因此 Auto 选中 `GPT-5.6 Terra` 时可能被错误拦截为“当前预设模型不可用”。现在发送前只要该逻辑模型没有精确映射，App 就会先进行不含对话内容的公开目录刷新，再按精确 `provider → model_id` 重新解析。手动选择仍不偷偷降级或换模型。
- **本机验证：** Android Studio JBR 离线执行 `:app:testDebugUnitTest :app:lintDebug :app:assembleRelease --offline --no-daemon`；单测无失败，Lint 为 0 errors。正式 APK 为 `com.nanzhufeng.ai / code 62 / 0.3.0-p10f`，v2/v3 签名通过，证书 SHA-256 仍为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **OPPO 覆盖：** `3B157F009E800000` 已由 code 61 同签名覆盖到 code 62，未卸载、未清数据、未读取私有数据、未运行仪器测试。`firstInstallTime` 仍为 `2026-08-20 15:15:31`；回读设备 base APK 与本机正式包 SHA-256 一致（`b8bf2313df821de5f771346952fe2bd59f042ba898c4bfb1d069cd2b56728d5a`），标准 Activity 冷启动 `Status: ok`（196 ms）。

## 2026-08-23 P12：统一全库上下文、多服务商逻辑模型、发送链修复与 OPPO code-57→60 正式覆盖（历史记录，已由 D3 覆盖上下文细节）

- **当前产品行为（历史，已由 D3 替换）：** 此段曾描述全库遍历和字符上限，现已不再是当前实现。以 D3 的 FTS5 增量索引、模型 Token 预算、完整附件预留和索引故障可见语义为准。
- **模型与审计：** OpenRouter 用于 GPT/Claude/Gemini，Qwen 与 DeepSeek 使用固定官方兼容端点；普通发送没有逐次确认。每次真实请求只在本机保存 Provider、endpoint、model_id、alias、reasoning level、时间、输入/输出 token 和结果状态，不保存提示词、回复正文、附件或密钥。`LocalContextBrokerContractsTest` 覆盖 Memory、知识库、历史对话会进入同一请求，且附件/Tool 正文不会进入；`P6GModelRouterContractsTest` 与 `P2ModelServiceContractsTest` 同轮通过。
- **发送链修复：** 之前 Composer 把输入保存为异步任务，用户紧接着点发送时执行链可能读到旧草稿；并且本地提交后等待网络完成才刷新 UI，造成文字仍在输入框、像没有发送。现在以 mutex 串行化草稿写入与发送：先保存按钮下的准确文本、原子提交用户消息/清空草稿、立即刷新对话，再等待 Provider。真实失败仅在 Composer 上方显示明确原因；自动兜底若全部被配置/凭据门拦下会返回原始门禁原因，不再笼统显示服务失败。
- **构建与 OPPO：** `:app:assembleRelease --offline --no-daemon` 与定向单测成功；正式 APK 为 `com.nanzhufeng.ai / code 60 / 0.3.0-p10d`，v2/v3 签名通过，release 证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，APK SHA-256 为 `5dc2680ad2c5e238b21545a9e3edb5a65531402d71684791c22ec874a5ed16be`。OPPO `3B157F009E800000` 安装前为 code 59，同证书、非 Debug；仅 `adb install -r` 覆盖到 code 60，返回 Success。未卸载、清数据、读取私有数据或运行 `connected*AndroidTest`；标准 `NanfengAiActivity` 冷启动为 Status ok（549 ms），未见本 App 崩溃。
- **真实业务边界：** 尚未在本轮以真实用户库与真实模型完成端到端对话验收，因此不能把构建、安装或启动写成 Provider 对话已成功。用户可直接在正式 App 的 AI 模型设置中填写各 Provider 本地凭据，再发送非敏感短文本验证；凭据不得进入源码、Git、日志、构建产物或对话记录。

## 2026-08-23 P5：OPPO code-53→57 正式保留数据覆盖与前台启动已验收；真实调用交由用户

- **安装前门禁：** OPPO `3B157F009E800000` 为 `device`，现装 `com.nanzhufeng.ai` code 53、`firstInstallTime=2026-08-20 15:15:31`、CE/DE inode=`1459104/1433378`。当前 main 的唯一正式 APK 为 code 57、SHA-256 `9a38926d68b6e4f4b3539be9783618a6486cf1f9c7f3f08a62f2845af5b73b61`，非 Debug，v2/v3 与设备 code-53 `base.apk` 均为 release-v2 证书 `6d1d…8661f8`。未读取应用私有数据或任何密钥。
- **唯一写入与回读：** 仅一次 push 到 `/data/local/tmp/nanfeng-ai-0.3.0-p10a-code57.apk`，仅一次 `pm install -r --user 0` 返回 `Success`，随后清理该临时文件。安装后 package 为 code 57，首次安装时间与 CE/DE inode 完全不变；设备 `base.apk` SHA-256 精确匹配本地 code-57 APK。一次标准 `ACTION_MAIN`/`CATEGORY_LAUNCHER` 启动返回 `Status: ok`、`NanfengAiActivity` 冷启动前台。到此立即停止 OPPO 命令。
- **真实调用边界：** 用户将自行在已经打开的正式 App 内配置并发起真实调用；本任务不读取、记录、输出或上传 Provider/API Key、token、私钥、对话正文、响应、费用或业务数据。真实调用的成功/失败、用量与用户可见结果须由用户在 UI 内自行确认，不能由安装或启动替代。

## 2026-08-23 P11：冻结 code-57 的两次正式构建 ZIP 内容一致；整体 SHA 差异仍限于签名块

- **同 revision 样本：** 在没有任何工作树/提交变动的 code-57 冻结提交上，常规 `assembleRelease --offline --no-daemon` 与一次 `--rerun-tasks` 都成功。APK A/B 的 SHA-256 分别为 `c4c39839f935051b8820271feadc9641c0ac95fcaa61242356190d9faa3f1f00` / `9890b198949b1b1dc4b167a99458cec96b216ac180e41f4b81e60e82a2b0ea7e`，均为 `23,605,208 B`、281 ZIP entries，v2/v3 验签通过。
- **内容结论与边界：** 两包 ZIP entry 名称/顺序相同，按同顺序串联的全部解压 payload 逐字节相同。因此当前 source/DEX/resources/assets 的 ZIP 内容在冻结 revision 可重复；整体 SHA 差异不能据此写作业务或字节码差异，仍只保留为 Android APK Signing Block 的签名随机性边界。未安装、发布、上传或触碰 OPPO；这不替代正式发布、回下载或 P0–P11 完成。

## 2026-08-23 P5：隔离正式 code-52→57 覆盖升级、草稿保留与冷启动已验收

- **两包及环境：** 旧包从 detached `a4eebd1` 在 Android Studio JBR、`ANDROID_HOME`、`--offline --no-daemon` 下唯一重建；为 `com.nanzhufeng.ai / code 52 / 0.3.0-p10a`、SHA-256 `cf26307a392baf8df46ef12b4130d8bb0939d7dbca2b874fcf43fef35b0db1d8`。新包为当前 main code 57 正式产物；两包 v2/v3 均通过、同 release-v2 证书。只使用全新 Android 35 `NanfengAiP5ReleaseUpgradeMigration` / `emulator-5614`，不触 OPPO。
- **真实升级与数据保留：** 首装 code 52 后仅经正常 Launcher/UI 创建非敏感 Composer 草稿 `P5UpgradeFact`，不发送、不调用 Provider/网络。随后只执行一次 `adb -s emulator-5614 install -r` 覆盖到 code 57；没有清数据或卸载。`firstInstallTime` 保持 `2026-08-23 10:46:48`，CE/DE inode 保持 `573570` / `401708`。force-stop 后标准 Launcher 冷启动成功（`Status: ok`，约 1.04 秒），正常 UI 仍显示草稿和“会话草稿”语义。
- **隔离性能基线：** 在保留该隔离数据的 code 57 上，三次 `force-stop` 后标准 Launcher 冷启动均为 `Status: ok`，`TotalTime` 依次为 `1069 ms / 930 ms / 898 ms`（平均约 `966 ms`）。这是 Android 35 SwiftShader 隔离模拟器的启动回归基线，不可推定为 OPPO 或全部设备性能。
- **边界：** 这是隔离、非敏感的旧版→当前正式包迁移与草稿保留证据；不代表 OPPO、真实用户数据、完整无障碍/性能、商店发布或 P0–P11 完成。code 57 未获 P11 发布资格，禁止安装到 OPPO 或发布。

## 2026-08-23 P6：schema 38→39 同签名升级、非空本机 DocumentsUI `LOCAL_TRUTH_PRESENT` 拒绝及数据保留已验收

- **隔离与冻结安装身份：** 只使用新的 Android 35 `NanfengAiP6Schema38UpgradeRecovery` / `emulator-5610`；专属 package `com.nanzhufeng.ai.p6v2schema38upgradeacceptance` 首装前不存在。旧 schema-38 code 51 包 `/tmp/nanfeng-ai-schema38-upgrade.hCuijb/app/build/outputs/apk/p6V2Schema38UpgradeAcceptance/南枫AI-开发验收.apk` 的 SHA-256 为 `111d57a3349ad9303225ba1102d325ee5238802e8dec302070241f7ff4552917`，当前 schema-39 code 52 包 `app/build/outputs/apk/p6V2Schema38UpgradeAcceptance/南枫AI-开发验收.apk` 为 `996d8b2c1396eccb4cfe752636c5905d52c608f7707b382149ca6d315bd05a4a`；两包 v2/v3 均通过，正式证书 SHA-256 同为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。首装 51 后设备 `base.apk` 哈希匹配旧包；普通 Launcher/UI 显式创建两个最小非敏感 Conversation，并保存一个非敏感 Draft，没有发送消息或触发 Provider/网络。
- **真实覆盖与 owner 读回：** 对 51 数据仅执行 code 52 `adb -s emulator-5610 install -r`；没有清数据或卸载。设备安装后的 `base.apk` 哈希匹配 52 包。再经系统 Launcher 启动新包，`P6V2Schema38UpgradeAcceptance` 去内容化 startup audit 记录 `schema=39 project=0 conversation=2 draft=2 knowledge=0 memory=0 relation=0 attachment=0 v2receipt=0 v2provenance=0 v2settings=0`；正常 UI 抽屉显示两个历史 Conversation，Composer 读回旧 Draft。因此真实 schema 38→39 同签名升级与 Conversation/Draft owner 保留已关闭；这不证明完整 P6、真实用户数据、发布或 OPPO。
- **strict fixture 与普通用户路径：** 只读 glob 精确命中唯一既有 `/tmp/nanfeng-ai-p6-v2-local-truth-fixture.oZEEju/local-truth-fixture.nfai-exchange`；`2,346 B`、SHA-256 `d9df67ce64cc325ab35b9f4268c03ed2e956dbeef81e38bf058fb18c16959832`、ZIP 头 `50 4b 03 04` 均精确匹配，才将同一 bytes 仅推送至 `emulator-5610` 公共 Downloads，设备侧大小/哈希回读相同。经普通 Launcher → 设置 → 数据与导入 → 导入中心 → 完整工作区交换（v2）→ 选择 v2 交换包并恢复 → DocumentsUI Downloads，仅选择该文件一次；应用显示中文脱敏结果“当前本机已有数据或待恢复记录，已拒绝覆盖。”
- **拒绝后的只读计数证明：** 选择前与单次不清数据的进程重启后、再次由 Launcher 启动时，`P6V2Schema38UpgradeAcceptance` startup audit 均为 `schema=39 project=0 conversation=2 draft=2 knowledge=0 memory=0 relation=0 attachment=0 v2receipt=0 v2provenance=0 v2settings=0`。因此 `conversation=2,draft=2` 与其余计数均不变，非空本机没有被 merge/upsert/覆盖/删除，也未新增 receipt/provenance/settings。没有第二次 DocumentsUI 选择、`connected*AndroidTest`、DB/SQL、Activity extra、deep link、网络、Provider、Key、清数据、卸载、覆盖安装或 OPPO 操作。此独立子链已关闭；完整 P6、发布与 OPPO 仍不由此推出。

## 2026-08-23 范围变更：Windows 验证不再是总控完成门

- 用户已明确要求“跳过 Windows 验证”。因此，Windows 原生安装、WebView2、签名、picker、缩放与 IME 的本机验收不再阻塞本项目 P0–P11 的完成判定；历史 Windows 未验证记录保留为非阻塞平台债务，不得伪造为已验收。Android、OPPO、Provider、账号同步、真实工具、生态目标、发布与其余合同门保持不变。

## 2026-08-23 范围变更：Google/Supabase 与云同步不再是总控完成门

- 用户已明确要求跳过 Google 登录、Supabase、云端加密同步及跨设备恢复验证。因此，P7 的真实 OAuth、远端部署、受控 HTTP 与跨设备回读不再阻塞本项目总控完成判定；本地 P7 协议/状态机实现与历史未验证记录保留为非阻塞债务，绝不写作真实云同步已经验收。
- 此范围变更不授权读取、上传、提交、同步或输出任何 API Key、token、私钥或等价认证秘密；该类秘密只能本地受控使用。

## 2026-08-23 P5 OPPO Launcher 异常已最小恢复：code-53 的标准 Launcher 成功且活动在前台

- **已确认的真机事实：** 在新的、单独授权的最窄诊断中，OPPO `3B157F009E800000` 为 `device`。`com.nanzhufeng.ai` 仍为 code `53`、无 `DEBUGGABLE` 标记；User 0 为 installed/default enabled，CE/DE inode 仍是 `1459104` / `1433378`。没有重新安装或重验签：此前的同 release-v2 signer、code-52→53 保留数据覆盖与设备 `base.apk` 精确回读仍是本次的安装身份事实。
- **解析与唯一启动：** Package Manager 对 `ACTION_MAIN` + `CATEGORY_LAUNCHER` + package 的 `resolve-activity` 精确返回唯一 component `com.nanzhufeng.ai/.NanfengAiActivity`。因此只执行一次授权的标准 `am start -W`；返回 `Status: ok`、Activity 为该 component，并提示 intent 交付给已在最前的实例。只读 `dumpsys activity activities` 后，`mCurrentFocus` 与 `mFocusedApp` 均为 `NanfengAiActivity`。没有读取 UI、业务数据或会话，也没有第二次启动。
- **停止点：** code-53 Launcher 解析/前台活动异常已恢复并获得真机证据；立即停止 OPPO 命令。未删除此前远端临时 APK，未执行安装、卸载、清数据、push/pull、截图、业务操作、Debug/Instrumentation 或 `connected*AndroidTest`。这不构成 P5、发布、Provider/账号或 P0–P11 完成；余下 P5 门仍是旧 APK 升级迁移、全设备可访问性/性能和正式发布/回下载。

## 2026-08-23 P5 Launcher 异常：code-53 APK 离线审计确认旧 component 正确；不触设备

- **离线结论：** 最终正式 APK `app/build/outputs/apk/release/南枫AI.apk`（SHA-256 `230cac90c0e54231a73650c1fc1e0a9f3890a03c0e8c1c5150d39a77f183954c`）的真正且唯一 `MAIN`/`LAUNCHER` Activity 是 **`com.nanzhufeng.ai.NanfengAiActivity`**。此前使用的相对 component `com.nanzhufeng.ai/.NanfengAiActivity` 按 Android 规则正是同一完整 component；它没有改名，也不是错误引用。
- **四方证据：** `aapt dump badging` 给出 `launchable-activity: name='com.nanzhufeng.ai.NanfengAiActivity'`；`aapt dump xmltree`、Android Studio JBR 下的 `apkanalyzer manifest print` 都显示该 activity `android:exported="true"`，同一 intent-filter 同时含 `android.intent.action.MAIN` 与 `android.intent.category.LAUNCHER`。源码 `app/src/main/AndroidManifest.xml` 的 `.NanfengAiActivity` 与 Kotlin `package com.nanzhufeng.ai`/`class NanfengAiActivity` 一致；三个 release generated manifest（main merged、merged、packaged）也都解析为相同完整类名，无 `activity-alias`。`applicationId`、namespace、APK package 均为 `com.nanzhufeng.ai`；release `isMinifyEnabled=false`，且没有 release mapping 输出，因此不存在 R8 改写入口类名的路径。
- **异常边界与最小恢复建议：** 这次离线证据不能解释 OPPO 当时“unable to resolve Intent”的设备侧原因，也不推翻那次严格停止。若南烛枫另行授权一个新的、只读且独立界定的主设备恢复任务，最小显式启动命令应为 `adb -s <OPPO_SERIAL> shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.nanzhufeng.ai/.NanfengAiActivity`；本任务没有也不得执行它、任何 ADB 命令、安装、清理或远端临时 APK 删除。由于最终 APK 已有正确 launcher，不提出源码/manifest 修复。

## 2026-08-23 P5 code-53：OPPO 正式保留数据覆盖已写入；Launcher 解析异常，按门禁停止

- **已确认：** 在 `3B157F009E800000` 的完整只读门通过后，现装 `com.nanzhufeng.ai` 从 code 52 以同 release-v2 证书精确覆盖至 code 53。安装前后 `firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378` 不变；安装后只读 pull 的 `base.apk` SHA-256 为 `230cac90c0e54231a73650c1fc1e0a9f3890a03c0e8c1c5150d39a77f183954c`，与本地正式 APK 精确相同，v2/v3 均通过且证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`；包仍非 Debug。
- **唯一写入与停止：** 只执行一次 push 到 `/data/local/tmp/nanfeng-ai-0.3.0-p10a-code53.apk` 与一次 `pm install -r --user 0`，返回 `Success`。随后标准 package launcher intent 报“unable to resolve Intent”，未得到 `Status: ok`；按明确停止门禁，没有重试安装、没有改用其他启动方式、没有再查询设备，也没有清理远端临时 APK。不要把本条写作正常启动、完整 P5、发布或 P0–P11 完成。
- **唯一后续边界：** 需要南烛枫另行决定是否以新的、只读且单独界定的启动诊断处理该 Launcher 异常；此前不得对 OPPO 执行任何操作。详见 `P5_OPPO_READONLY_GATE_AUDIT_20260821.md`。

## 2026-08-23 P0–P11 外部门 readiness：全项阻断；仅作去敏只读审计并停止

- **本轮边界：** 只读检查 ADB 当前设备清单、Git remote 是否存在、仓库声明的配置入口、P7 本地 readiness 脚本、P8–P10 合同、既有 P6 失败交接与现有 code-53 APK；没有 Provider/HTTP、Supabase/Google 远端、Key/凭据值、应用私有数据、AVD 启动、安装/卸载/清数据、`connected*AndroidTest` 或 push。开始时工作树为 0 改动。
- **现场结论：** ADB 当前无已连接设备，OPPO `3B157F009E800000` 不在清单；Git remote 不存在。P2/P3 没有允许的项目专属 Provider 环境变量或用户级 Gradle 凭据 schema，应用私有加密凭据仍未读取。P7 的 Supabase CLI、project link、私有客户端配置、认证会话和指定 target 均为缺失；P8 没有已批准的真实工具合同/目标，P9 没有真实目标应用与稳定入口证据，P10 未有两个真实消费者。P6 只能确认当前无 ADB 设备，既有三个新 Android 35 AVD 的注册失败仍是唯一证据，Windows 本机门未具备。
- **code-53 只读记录：** `app/build/outputs/apk/release/南枫AI.apk` 仍存在，package/versionCode=53，大小与交接记录一致；其 SHA-256 与记录一致，使用 Android Studio JBR 的 Build Tools 36.0.0 `apksigner` 只读核验 v2/v3 通过。此事实不授予安装、覆盖、发布或重建权限：OPPO 不在线，且 P11 的可重复性/发布边界仍须独立收敛。
- **唯一恢复路由：** 总控表已更新至 `MASTER_PLAN_COMPLETION_AUDIT_20260816.md` 顶部。任一外部门到位时，都只可按该表所列的最小恢复动作另开独立增量；不得把配置存在、产物存在、fixture 或本地合同写成真实 Provider、同步、Agent、生态、Windows、OPPO 或 P0–P11 完成。

## 2026-08-23 P4：全量本机候选审计，无可独立实现的遗留合同；未触设备、网络、Provider 或 Key

- **审计范围与现场：** 开始时工作区 0 改动；本轮只读核对 `MASTER_PLAN_COMPLETION_AUDIT_20260816.md` 的 P4 行、`MASTER_DEVELOPMENT_BLUEPRINT.md` §17、`P4A` 至 `P4O` 全部合同，以及 Android 生产/测试目录。P4-A–O 均已有唯一领域链、`AppContainer` 生产装配和定向合同测试；典型 owner 为 `ProjectDomain`、`ContextSelectionDomain`、`MemoryDomain`、`KnowledgeDomain`、`LocalContextCompressionDomain`、`JsonKnowledgeAdapter`、`PdfTextKnowledgeAdapter` 与 `ManageWebTextSnapshotUseCase`。对应 `P4A…P4O` 的 domain 或 Room contracts 均仍在当前源树，未发现“合同已立但尚未编码/测试”的 P4 子项。
- **已本机闭合：** Projects/Memory/显式 Context/Knowledge 检索与关系、Markdown/JSON/PDF portability、离线 Eval、抽取式压缩/稳定前缀元数据、L3 本地 action trace 及严格 HTTPS 网页 Adapter 的代码和本地合同都已在各自合同范围内收口；P4-O 的公共 DNS/HTTPS 成功仍是网络验收债务，不能由本机代码替代。
- **剩余门的精确分类：** 语义摘要需要真实 Provider 或另行明确、可证明的本地语义引擎；真实缓存、成本/质量基准、Provider/Harness 回归、长上下文与用户价值都依赖真实执行/服务，当前禁止触碰。后续“更多 Adapter”尚未指定具体格式、用户任务与保真边界；按“一 Adapter 一合同”原则属于产品选择，不能从现有 P4 事实推定实现。P3 的真实退出门同样是 P4 的前置外部门。
- **停止点：** 本轮没有代码、测试或构建变更，也没有运行 ADB/AVD/OPPO、`connected*AndroidTest`、网络、Provider、Key/账号或凭据读取。下一步只有在南烛枫指定一个具体本地 Adapter/本地语义引擎的用户任务，或单独授权真实 Provider/Harness 验收后，才可新立一个 P4 合同；不得以此审计宣称 P4 或 P0–P11 完成。

## 2026-08-23 P5：已提交 code-53 的唯一离线正式构建通过本机门；未触设备，严格停止

- **冻结输入与唯一构建：** 干净 `main` 的构建提交为 `a861de330b54846fb1b769b6041f45e941ff9ee1`（开始时工作区 0 改动）；正式 `versionCode` 为 **53**、`versionName` 为 `0.3.0-p10a`。确认无 Gradle/GradleDaemon 争用后，使用 Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`ANDROID_HOME=/Users/nanzhufeng/Library/Android/sdk`、`--offline --no-daemon`，仅运行一次 `:app:assembleRelease` 并成功（51 tasks，3 executed、48 up-to-date）。四项用户级 `nanfengAi.releaseV2.*` 备用签名属性仅核对为 non-empty，未读取或输出秘密。
- **最终 APK 本机核验：** 产物为 `app/build/outputs/apk/release/南枫AI.apk`（23,605,202 B），SHA-256 为 `230cac90c0e54231a73650c1fc1e0a9f3890a03c0e8c1c5150d39a77f183954c`。Android SDK Build Tools 36.0.0 的 `aapt` 证实 `com.nanzhufeng.ai / versionCode 53 / versionName 0.3.0-p10a`，且 `application-debuggable` marker 缺失；同一工具链的 `apksigner` 证实 v2/v3 均为 true，证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。构建后 Gradle 进程数为 0。
- **P11 证据边界：** 先前 `edb0…` APK 在 code-53 提交前生成，只能保留为旧本机预检。P11 已证明同一 revision 的 APK Signing Block 可变；因此本轮 `230cac…` 与旧 SHA 不同既不被写作业务差异，也不被用作拒绝当前 package/version/non-debug/v2/v3/cert 只读证据。本轮未进行第二次构建或两包比较。
- **设备与下一条安全动作：** 未连接、查询或操作 OPPO/AVD；未执行安装、卸载、清数据、push/pull、Launcher、网络、Provider、Key 或 `connected*AndroidTest`。待 OPPO `3B157F009E800000` 重新连接后，必须从完整只读前置门开始，先核对现装 code 52、非 Debug、同 release-v2 证书、`firstInstallTime` 与 CE/DE inode；如届时仍需要重建 APK，须另立交接并重新走其构建/核验边界。当前不得称为 OPPO 覆盖、设备启动、数据保留、发布或 P5/P0–P11 完成。

## 2026-08-23 P11：同一冻结 revision 的 ZIP 完全相同，但 APK Signing Block 单一 pair 仍令字节不可复现；不安装、不发布

- **本轮结果：** 干净 `main` 的 `290297de4f42c3d79a59b2cf7426c772bef1e471` 被 detached 到新的项目专属临时 worktree；无外部 Gradle/GradleDaemon。Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`ANDROID_HOME`、`--offline --no-daemon` 下，常规构建 A（1m58s）与唯一 `--rerun-tasks` B（1m57s）都成功。A/B 均为 23,605,206 B，但 SHA-256 分别为 `11990ecd7ac50a2143abe38eb25e09e7ab8d1d46e87cdcba96b095bdecaf4c9c` / `1ddf57b941101ae8f61d6eb812a79f88bae0abd60c0418b811a9a83b76449609`，故冻结 revision 在此环境仍不可字节重复。
- **精确差异：** 281 个 ZIP entry 的顺序、payload、原始压缩流、local header、ZipInfo 元数据逐项全同；`apksigner` 的 v2/v3、单 RSA-4096 signer 与证书结构全同。只有 12,288 B APK Signing Block 的 pair `0x504b4453` value 不同：A/B 长度均 6,456 B，6,430 byte offsets 不同；其余三个 pair 与 block 外 bytes 全同。完整可复查产物、构建日志、entry TSV、签名输出与 block 摘要已保留在项目外证据目录；详情见 `docs/P11_SUPPLY_CHAIN_REVIEW_20260821.md`。
- **过程偏差与停止：** 因 detached worktree 不含未跟踪 `local.properties`，最初一次 `assembleRelease` 在 SDK 配置阶段失败、未生成 APK；随后以环境变量补足 SDK 后才得到 A/B 两次成功包。因此成功产物构建为两次，但字面命令调用为三次，必须保留此事实。未触及当前工作树、Gradle 缓存、源码/依赖、设备/AVD/OPPO、安装、网络、Provider、Key、凭据或 `connected*AndroidTest`；没有清理临时 worktree。不得把结果解释为 `d612`、`3f` 或 `c511`，不得安装、覆盖、发布或实施修复；若继续须另立任务、先明确是否接受此调用次数偏差。

## 2026-08-23 P11：冻结 APK 已缺失；修正 Git revision 混淆，未锁定 DEX 根因，禁止安装/发布

- **本轮事实：** `main` 干净；没有启动 Gradle、改源/依赖、设备/AVD/OPPO、安装、网络、Provider、Key、凭据或 `connected*AndroidTest`。原记录的 `/tmp/nanfeng-ai-p11-repro.q3kyxQ/` 与 `/tmp`/`/private/tmp` 有限深度的南枫 AI/P11 APK 检索均未找到两份冻结件。仓库当前 release APK 仅为 `3f1408…`，不能复原两个样本的 ZIP/DEX/profile 比较。
- **已收敛：** `d612…` 出自 `a4eebd1`，`3f1408…` 出自 `5dbfc47`；`app/`、所有 Gradle 配置与 `gradle/` 的 Git tree object 相同，提交之间只改三份文档。`META-INF/version-control-info.textproto` 中的 Git revision 因此必然不同，不能被归入 DEX 非确定性。已记录的三份 DEX 和两份 baseline profile 的大小/内容差异仍未解释；release 未启用 R8，只有 D8/Kotlin/KSP 发射或尚未记录的构建输入是候选，不能在样本缺失时断言“仅排序”或“语义变化”。
- **停止点：** 不存在有证据支持的安全局部 deterministic 设置，故本轮无代码/Gradle 修复。下一独立任务须在**同一 Git revision**保留两份项目专属临时 APK，比较 DEX header/section、definition/code 映射和 profile 语义投影后才评估局部设置；重现验证还须再分离为另一任务。当前及历史 P11 APK 均**不得安装、覆盖或发布**。

## 2026-08-23 P11：同源 release 强制重建未字节复现；不安装、不发布，停在 DEX 生成差异

- **已执行的唯一实验：** 在 `main` `5dbfc47fa7ee`、干净工作树、无外部 Gradle/GradleDaemon 后，保护性复制 `d612c417985f4e21623239b2722c63bcb3d60bf6dbf63f90e98c01e1f7db9af0`（23,605,205 B）到项目专属 `/tmp/nanfeng-ai-p11-repro.q3kyxQ/`；Android Studio JBR + `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` + `--offline --no-daemon` 下只运行一次 `:app:assembleRelease --rerun-tasks`，无 `clean`。daemon 记录 `BUILD SUCCESSFUL in 1m 53s`；复制的第二包为 `3f1408b74efbc7ccb64e4bed569d154f907234cebc2e48637b79ca96d7675f2e`（23,605,202 B）。
- **确证的收敛结果：** 两包 `apksigner` v2/v3 均通过、单一 release 证书 SHA-256 同为 `6d1d56ec…8661f8`；均为 281 entries，ZIP 名称/顺序/时间戳相同。但 CRC/解压内容不同的 9 项明确包含 `classes.dex`、`classes2.dex`、`classes3.dex`、两份 baseline profile、`version-control-info.textproto` 及三份 `META-INF` 签名条目；`resources.arsc`、`AndroidManifest.xml` 内容相同。两包 APK Signing Block 大小均为 12,288 B、内容 hash 不同。这是实际 DEX/profile 产物差异，不是仅签名或时间戳差异；完整细节在 `docs/P11_SUPPLY_CHAIN_REVIEW_20260821.md`。
- **严格停止：** 未触及 OPPO/AVD、安装/卸载/清数据、`connected*AndroidTest`、Provider、网络、Key 或签名凭据值；没有改依赖或源码。`git diff --check` 通过，除本交接与 P11 文档外无改动。历史 `c511…` 文件不在工作区，绝不从当前实验猜测其原因。当前 APK 均不得安装、覆盖或发布；下次若获授权，只能以独立增量定位 DEX/baseline-profile/version-control-info 的非确定性，先不实施修复。

## 2026-08-23 P11：AAPT2 当前清单回读与一次正式 Release 重建通过；产物字节差异待独立收敛

- **当前事实：** 当前 `main` 为 `a4eebd1` 且开始与结束均为干净工作树。AAPT2 macOS JAR/POM SHA-256 已由既有提交 `08cd46c` 写入；本轮在无 Gradle JVM 争用时，用 Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`--offline --no-daemon` 对 `:app:processReleaseResources` 先后执行带/不带 `--write-verification-metadata sha256` 的回读，两次均成功，XML 有效且 metadata/worktree 均无新增 diff。
- **正式构建与结构核验：** 随后仅一次 `:app:assembleRelease --offline --no-daemon` 成功（1m22s）。输出 APK 为 `com.nanzhufeng.ai / 52 / 0.3.0-p10a`，不含 `application-debuggable` 标记，Android Studio JBR `apksigner` 验证 v2/v3 为真、release-v2 证书摘要仍为 `6d1d56ec…8661f8`；本次 APK SHA-256 为 `d612c417985f4e21623239b2722c63bcb3d60bf6dbf63f90e98c01e1f7db9af0`。
- **不可跳过的风险与停止点：** 此 hash 与历史交接记录的 code-52 产物 `c511…` 不同，而旧精确 bytes 不在当前工作区，故尚不能收窄 APK 字节差异；本轮不把它当作可覆盖或可发布产物。没有读取凭据、没有操作 OPPO/AVD、安装、卸载、清数据、`connected*AndroidTest`、Provider、网络或 Key。P11 仅确认当前离线构建与供应链回读；任何正式设备或发布动作须先独立处理该可重复性风险并重新走相应门禁。

## 2026-08-23 P6：schema 38→39 独立同签名升级验收包与静态合同已就绪；新 Android 35 AVD 未能注册，真实链未执行

- **两份冻结验收 APK：** 旧包来自 detached 临时工作树的 schema-38 提交 `822f3f4`，只为验收将专属 package versionCode 调为 **51**、新增同名 build type，未改业务逻辑；产物为 `/tmp/nanfeng-ai-schema38-upgrade.hCuijb/app/build/outputs/apk/p6V2Schema38UpgradeAcceptance/南枫AI-开发验收.apk`，SHA-256 `111d57a3349ad9303225ba1102d325ee5238802e8dec302070241f7ff4552917`。当前 migration 包为 `app/build/outputs/apk/p6V2Schema38UpgradeAcceptance/南枫AI-开发验收.apk`，versionCode **52**，SHA-256 `996d8b2c1396eccb4cfe752636c5905d52c608f7707b382149ca6d315bd05a4a`。两包均为 `com.nanzhufeng.ai.p6v2schema38upgradeacceptance`，`apksigner` v2/v3 通过，同一正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`；不得拿它们作正式发布包。
- **最窄代码与静态门：** 当前包新增 `P6V2Schema38UpgradeAcceptance`，仅精确 BuildConfig + applicationId 开关时，在 Room 打开后向 logcat 写 `schema=39` 及 project/conversation/draft/knowledge/memory/relation/attachment/v2 receipt-provenance-settings 的去内容化计数；无 UI、无 mutation、无标题/正文/URI/path/展示名。`P6V2Schema38UpgradeAcceptanceContractsTest`、`WorkspaceExchangeV2OwnerMapperContractsTest`、`WorkspaceExchangeV2DocumentsUiContractsTest` 在 Android Studio JBR、`--offline --no-daemon` 下通过；后两者仍只证明代码/合同，不是设备升级或 DocumentsUI 证据。`P6_ANDROID_V2_ATOMIC_RESTORE_CONTRACT.md` 与 Android/Desktop 功能审阅均已写清：验收诊断没有普通入口、没有新按键；既有 v2 设置二级入口仍只在空本机恢复、非空本机必须显示原有中文脱敏拒绝。
- **精确设备阻断：** 未复用已有 AVD、OPPO 或 Desktop root。新建 `NanfengAiP6Schema38UpgradeAcceptance`（端口 5598）后，QEMU 在 ADB 注册前退出；该实例无包安装。随后新建 `NanfengAiP6Schema38UpgradeAcceptanceFinal`（端口 5600），QEMU 启动后超过两分钟无 5600/5601 listener、无 `adb devices` 注册、CPU 归零；已停止并保留、不再复用。最后新建 `NanfengAiP6Schema38UpgradeAcceptanceVerified`（端口 5602）并观察 **5 分 07 秒**，启动日志复现 `adb protocol fault (couldn't read status length)`，5602/5603 始终无 listener、`adb devices` 为空、QEMU CPU 约 0%，故已停止并保留。三台都没有安装 APK、没有 UI、没有 DB/SQL/command 注入、没有清数据/卸载、没有 DocumentsUI、Provider、网络或 Key；这属于宿主 Emulator/ADB 阻断，不能归因于迁移包。
- **下一唯一安全动作：** 先恢复一个**全新** Android 35 AVD 的可用 ADB 注册；不得复用上述两台或任何既有 AVD。只有确认 package 不存在后，才安装旧版同签名 51 包，经过正常 Launcher/UI 显式创建最小非敏感旧事实；再以 52 包覆盖升级、从新包 logcat 读取仅计数审计并以正常 UI 读回历史 owner。随后仍在该非空本机，仅走设置 → 数据与导入 → 导入中心 → 完整工作区交换（v2）→ DocumentsUI 选 strict v2 fixture，验证中文脱敏 `LOCAL_TRUTH_PRESENT` 拒绝及前后计数不变。当前不得称 schema 38→39 真升级、历史 owner 保留、DocumentsUI 非空拒绝或 P6/P0–P11 已验收。

## 2026-08-23 P6：Android v2 journal 附件提升中断→同包 DocumentsUI 重试已在新空 AVD 验收；仍非完整 P6

- **隔离、输入与最窄诊断：** 仅新建 Android 35 `NanfengAiP6V2JournalInterruptAcceptanceFinal` / `emulator-5560`，独立包名 `com.nanzhufeng.ai.p6v2journalinterruptacceptance`；未复用或修改 `5574`、Desktop roots、其他 AVD 或 OPPO。验收 build type 仅在该准确 applicationId 启用一次性钩子：首个附件从 staging 提升为 final 后，先 fsync 无内容 marker，再由该进程自身 `killProcess`；无 UI 入口、无 URI/path/body/Key/网络记录。输入为同一台 AVD Downloads 中的 non-sensitive strict v2 fixture，2,340 B，SHA-256 `fb81be2ad662d871f48b8c7044bf79f7bd56b536983bd9b7d06a0fdf0b675174`，semantic `ae8039c083529c42c5a72274fb633fc5f2fcbfefc2c6e33d8da4bbab33b10cf1`。
- **真实中断与未发布状态：** 只经正常 Launcher → 设置 → 数据与导入 → 导入中心 → 完整工作区交换（v2）→ “选择 v2 交换包并恢复” 打开 DocumentsUI 并选择该文件。logcat 实测 `stage=ATTACHMENT_PROMOTED_FIRST marker=durable action=kill-process`，系统返回 Launcher。首次正常重新打开后，验收包自己的仅计数 startup audit 为 `journal=1 owners=0 attachments=0 receipt=0 provenance=0 settings=0`；私有文件只读盘点为一个提升后的附件候选、无 staging 文件，marker 仍在。即无半成品 owner、asset ledger、receipt、provenance 或 settings 发布。
- **唯一恢复路径与严格成功：** 未提供或使用其他恢复入口；从同一应用、同一设置页再次经 DocumentsUI 选择同一 exact package，UI 显示 `已严格恢复：ae8039c08352… · 5 项对象 · 1 项附件`。这是 production atomic store 的 strict package/owner-field hash、asset-ledger 与 typed readback 成功结果；成功后私有 journal staging 目录仍为 0 文件、final attachment 为 1，验收 marker 作为一次性中断证据保留且不会再次终止。误选“完整备份”端口时 strict preflight 曾安全拒绝该 exchange fixture，未触及 journal；随后才切换到正确的 v2 exchange 入口。
- **代码、合同与自动门：** 新增独立 acceptance build type、精确 BuildConfig/applicationId gate、content-free startup audit 和静态合同测试；`P6_ANDROID_V2_ATOMIC_RESTORE_CONTRACT.md` 与 `ANDROID_DESKTOP_USER_ENTRY_AUDIT_20260816.md` 同步为无常驻入口的受控诊断边界。Android Studio JBR、离线无 daemon 下 `P6V2JournalInterruptAcceptanceContractsTest` 与 `WorkspaceExchangeV2OwnerMapperContractsTest` 均通过；正式签名验收 APK 的 v2/v3 已核验。首台独立尝试 AVD 在诊断最初同步读 Room 时启动即失败，未产生 journal/owner/receipt 后即保留不再触碰；钩子改为后台计数审计后才新建上述 final AVD 取得本证据。
- **严格边界与下一步：** 无 `connected*AndroidTest`、无 Activity extra/deep link、DB/SQL/业务 command 注入、清数据/卸载、Provider、网络或 Key；只用可见 UI 与 DocumentsUI。此增量仅关闭 Android v2 fixture 的真实“附件提升中断→零发布→同包 retry”门；不证明真实用户附件、历史全库迁移、Desktop 原生对象恢复、Windows、发布、P6 或 P0–P11 完成。

## 2026-08-23 P6：Android 5574 DocumentsUI 真文件 → 新隔离 Desktop native Open/Save/replay 已验收；仍非完整 P6

- **输入与隔离：** 仅只读检查 `emulator-5574`，验收 applicationId `com.nanzhufeng.ai.p6v2fullowneracceptance` 仍在；DocumentsUI Download 保留 `nanfeng-ai-workspace-v2.nfai-exchange.zip`（2,452 B）。设备 `sha256sum` 与只读 pull 到新的 `/tmp/nanfeng-ai-p6-v2-picker-input.R4Sqd5` 均为 `62975440c4e30aff5a6ea817e69265921efbc87b6e8e35c49206247e25ad99a7`。新建、初始为空的 Desktop bundle/root 为专属验收副本，不复用主 bundle、既有 root、其他 AVD 或 OPPO；副本 deep/strict 验签通过。
- **真实用户链：** 只通过独立 Desktop 的 `设置 → 数据导入 → 完整工作区交换（v2）→ 选择 v2 交换包` 打开 native Open picker，选择上述 Android DocumentsUI `.nfai-exchange.zip` 后 UI 显示 content-free `已私有导入并回读：4840bd0bf153… · 1 项附件`。随后从唯一已提交私有记录经 native Save picker 保存到新的空输出目录，UI 显示 `已从私有记录回导并严格回读：4840bd0bf153… · 1 项附件`；同一 native Open picker 再选原输入，UI 显示 `已验证重放回执`。
- **严格 readback：** 输入与回导 output 均由 Node strict verifier 通过（semantic hash `4840bd0bf15365ef4c84c60572cdb1646fa027534f4a019b49537cce07f8b828`、3 entries、1 asset）。output 是 2,349 B、SHA-256 `89a72a7ae43026ee59bdb148922e7fd70ed291bc468a188e2dc0fa3269b8426d`，与输入 bytes 不同但全量 7 项 owner-field hashes、asset ledger（id/entry/hash/7 B/classification）以及 semantic hash 均相同。隔离 SQLite 只读回读为 v2 imports/assets/provenance/journal/receipts = `1/1/7/1/1`，receipt 与 provenance field hashes 一致；v1 workspaces/workspace_exchange/import_journal 均为 `0/0/0`。
- **安全升级差异：** Android restore 后的完整范围 re-export 已保守将二进制附件与 exchange sensitivity 标为 `HIGH_SENSITIVE`；Desktop 输入与回导均保持该等级，未降级。Knowledge `sourceEvidence.contributedFields` 输入/输出同为 `[body,title]`；此前相对 non-sensitive restore fixture 的 Knowledge owner hash 变化属于该分类升级，不是 source-field 丢失。Desktop 只保存独立 private v2 archive/receipt/provenance，未恢复或创建 Desktop 原生 Project/Conversation/Knowledge/Memory owner。
- **边界与下一步：** 未运行 `connected*AndroidTest`，未写入 Android 业务数据、未操作其他 AVD/OPPO，未使用 Activity extra、DB/command 注入、Provider、网络或 Key。此增量关闭本轮 macOS v2 DocumentsUI→Desktop native Open/Save/replay 证据；Android 用户真实附件/中断恢复、Desktop 原生业务对象恢复、Windows、发布及 P0–P11 其余门仍分别开放，不能据此宣称 P6 或总控完成。

## 2026-08-23 P6：Android v2 新空 AVD 恢复→导出 DocumentsUI 真文件闭环；仍非完整 P6

- **本次唯一修复：** `08b3d0d` 修复的空 `conversation_drafts` 占位行已在新的独立验收环境实际生效；本轮再发现并修复 `sourceEvidence.contributedFields` 以逗号写入、而 Room 以字段分隔符读取导致严格 writer 认为 `title,body` 是非法单字段的问题。`AndroidWorkspaceExchangeV2AtomicRestoreStore` 现以 Room 一致的安全字段分隔符持久化；`WorkspaceExchangeV2OwnerMapperContractsTest` 增加 concrete Room restore 后用生产 repository/source 重新 mapper+strict writer 的合同。
- **自动与构建：** Android Studio JBR、离线、无 daemon 的定向 `WorkspaceExchangeV2OwnerMapperContractsTest` **10/10** 通过；独立 applicationId `com.nanzhufeng.ai.p6v2fullowneracceptance` 验收包为 `app/build/outputs/apk/p6V2FullOwnerAcceptance/南枫AI-开发验收.apk`，SHA-256 `169ea3794db188cee97070e70f0eee1a8fa0f9da0976910cc8a51a830e1a2e57`，v2/v3 与正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8` 已验签。
- **真实隔离 AVD：** 新建 Android 35 `NanfengAiP6V2RestoreExportFixAcceptance` / `emulator-5574`，未复用、清理或覆盖既有 AVD；安装前独立包不存在，安装后的已装 base APK 哈希与本地产物一致。仅通过正常 Launcher 与 `设置 → 数据与导入 → 导入中心 → 完整工作区交换（v2）` 操作 DocumentsUI：选择既有 2,340 B、non-sensitive strict fixture（SHA-256 `fb81be2ad662d871f48b8c7044bf79f7bd56b536983bd9b7d06a0fdf0b675174`）后，UI 显示 `已严格恢复：ae8039c08352… · 5 项对象 · 1 项附件`；完整范围显示 `1/1/1/1/1/1`，再由同一入口保存至 DocumentsUI Downloads 后 UI 显示 `完整工作区 v2 已严格回读：4840bd0bf153… · 5 项对象 · 1 项附件`。
- **真文件严格回读：** DocumentsUI 实际生成 `nanfeng-ai-workspace-v2.nfai-exchange.zip`（2,452 B）；设备与拉回主机 SHA-256 均为 `62975440c4e30aff5a6ea817e69265921efbc87b6e8e35c49206247e25ad99a7`。Node strict verifier 对该 exact bytes 通过，semantic hash `4840bd0bf15365ef4c84c60572cdb1646fa027534f4a019b49537cce07f8b828`、1 asset、3 entries；Desktop Rust strict reader 对该 exact bytes **1/1** 通过。项目、对话、记忆、关系、settings owner fields 和 asset ledger（ID/entry/SHA-256/byteCount）保持对应；Knowledge owner hash 只因完整范围 planner 对二进制附件一律保守标为 `HIGH_SENSITIVE` 而改变，`sourceEvidence.contributedFields` 仍逐项为 `body`、`title`，不是数据丢失。
- **设备与停止边界：** 先前新建的 `emulator-5572` 在发现导出缺陷后保留、未安装修复包，不能作为成功证据；保留的 `emulator-5588` 与 OPPO 均未触及。本轮无 `connected*AndroidTest`、无业务 DB/Activity-extra/deep-link 注入、无 Provider、Key 或网络；受 `run-as` 读取截断与无 sqlite3 权限限制，未取得新 AVD 的 DB 计数，未绕过该限制，真文件/UI/严格 reader 为本轮证据。没有发布、Desktop/Windows 实机或 P0–P11 总控完成。
- **下一安全停止点：** 此增量只关闭修复后 Android 新空 AVD 的 restore→export 真文件链；进程中断恢复、用户真实数据/附件、Desktop native picker、Windows、发布，以及 P0–P11 其余门仍未关闭。任何下一步须另行授权。

## 2026-08-23 P6：Android v2 OpenDocument 空 AVD恢复、重放与含非空拒绝的真文件链已闭合；仍非完整 P6

- **用户入口与唯一链路：** Android 仅在 `设置 → 数据与导入 → 导入中心 → 完整工作区交换（v2）` 显示“选择 v2 交换包并恢复”；`ActivityResultContracts.OpenDocument` 只接收用户明确选择的单 URI，UI 不读流、不解析 ZIP/JSON。`AndroidWorkspaceExchangeV2OpenDocumentRestorePort` 流式限制 128 MiB、忽略 URI/path/name/MIME/正文，仅将有限 bytes 与 package-hash 派生的 opaque intent ID 传到 `WorkspaceExchangeV2AtomicRestoreOwner → NfaiExchangeV2PackageReader → AndroidWorkspaceExchangeV2AtomicRestoreStore`。ViewModel/UI state 仅投影 outcome、semantic hash 截断和匿名对象/附件数；原始错误、正文、资产 bytes 与 locator 都不出现。
- **空本机可达性修复：** 首次 chat-first 壳曾以 `LaunchedEffect` 自动创建“新对话”，使新装 App 在用户进入设置前即被 restore store 视为 `LOCAL_TRUTH_PRESENT`。已移除该隐式写入；抽屉内的“新对话”仍是显式用户操作，首启在此之前保持真正空本机。此修复是空本机恢复合同的必要配套，不新增聊天/Composer 常驻入口。
- **功能审阅与文档：** Android 与 Desktop 的“功能审阅”均登记为“待您判断是否保留”：Android 仅空本机严格恢复，Desktop 保持 v2 私有导入；建议都只保留设置二级入口，不加聊天、Composer 或工作页按键。`P6_ANDROID_V2_OPEN_DOCUMENT_RESTORE_CONTRACT.md` 记录单 URI、脱敏状态、replay/recovery 及验收门。
- **定向自动验证：** Android Studio JBR、`--offline --no-daemon`：`WorkspaceExchangeV2OwnerMapperContractsTest` **10/10**、`WorkspaceExchangeV2DocumentsUiContractsTest` **2/2**、`AndroidUserEntryAuditContractsTest` **3/3**、`P6GUnifiedChatFirstUiContractsTest` **5/5** 通过；Desktop `npm test` **93/93** 通过。`assembleP6V2SafEmptyAcceptance` 成功，APK `app/build/outputs/apk/p6V2SafEmptyAcceptance/南枫AI-开发验收.apk` SHA-256 `10f55a95719d3ebe991e963cbfe43b759651314bca4a7f7105e7f1b7cd05c7b6`，v2/v3 签名和正式证书 SHA-256 `6d1d…8661f8` 已核验。
- **真实隔离 AVD：** 新建且仅本轮使用 `NanfengAiP6V2RestoreOpenDocumentAcceptance` / `emulator-5588`，安装前包不存在；新安装并正常打开后只读 DB owner counts 为 Project/Conversation/Knowledge/Memory/Relation/receipt = **0/0/0/0/0/0**。正常 UI 到设置并实际进入 `com.google.android.documentsui`，从 Download 选择 2,340 B 的本地生成 non-sensitive strict v2 fixture（SHA-256 `fb81be…75174`）；返回 App 显示 `已严格恢复：ae8039c08352… · 5 项对象 · 1 项附件`。再次从相同设置入口经 DocumentsUI 选择同一实际文件，返回显示 `已验证相同恢复回执：ae8039c08352… · 未重复写入对象或附件`。再经 DocumentsUI 选择单字节篡改的 2,340 B 包（SHA-256 `6aa96f…8f534`）及安全生成的 129 MiB（135,266,304 B）包（SHA-256 `9efc5d…d5066`），两次均显示 `所选交换包未通过完整校验，未读取或覆盖本机数据。`。三次之后只读 DB readback 均为 Project/Conversation/Knowledge/Memory/Relation/asset = **1/1/1/1/1/1**，receipt/provenance/settings = **1/7/1**；没有重复写入。未运行 `connected*AndroidTest`，未操作 OPPO、Provider、Key、网络或业务 DB 注入。
- **真实非空拒绝与最窄修复：** 为取得不同的严格包，正常设置导出先暴露恢复 store 漏写 `conversation_drafts` 空行：UI 如实拒绝 `会话缺少草稿记录：conversation-v2-01`，没有写出包。已在同一 atomic transaction 补齐空、无附件 draft 并纳入 typed readback；定向 `WorkspaceExchangeV2OwnerMapperContractsTest`（Debug Robolectric）通过，且 strict writer 生成与原包不同的 2,346 B non-sensitive 包（SHA-256 `d9df67…59832`）。该新包随后由当前保留数据的同一 `emulator-5588` 经设置 → DocumentsUI 实际选择，显示 `当前本机已有数据或待恢复记录，已拒绝覆盖。`；选择前后只读 DB counts 都是 Project/Conversation/Knowledge/Memory/Relation/asset = **1/1/1/1/1/1**，receipt/provenance/settings = **1/7/1**。为保护该 AVD 的既有恢复数据，修复后的源码未重新安装到它；恢复后再导出的真实设备验收仍待独立隔离环境。
- **严格停止与下一步：** 这只关闭 Android 设置单文件 → strict reader → 空本机 atomic restore 的真实 success、同包 replay、篡改/oversize 与 nonempty-local 拒绝链。进程杀死 journal 恢复、真实历史 schema-38 全库升级、修复后的恢复→再导出真机链、用户真实附件、紧凑/展开视觉、Desktop/Windows/发布与 P0–P11 均未关闭；下一增量必须独立授权，不能把 fixture evidence 称作这些结论。

## 2026-08-23 P6：Android v2 schema 38→39 与 journal 失败重试合同已闭合；仍未接 UI/SAF

- **持久实现：** `AndroidWorkspaceExchangeV2AtomicRestoreStore` 是 `WorkspaceExchangeV2AtomicRestoreOwner` 的唯一 concrete store。schema **38→39** 增加 content-free v2 receipt、逐 owner/asset provenance 和安全 settings metadata；`AppContainer` 只装配 owner，不暴露 SAF、ViewModel、worker 或常驻 UI 入口。
- **38→39 与可恢复边界：** `MIGRATION_38_39` 仅追加三张 v2 metadata 表，历史 schema-38 事实保持不变，完整 migration 链已连续到 39。store 先严格重验 canonical IR，再建立 app-private journal staging，逐附件同步写入并 hash 回读；同一次 Room transaction 复核空本机/intent、提升附件、写 Project/Conversation/Message/Knowledge/Memory/Relationship/settings/provenance/receipt 并作 typed readback。普通失败回退已提升文件且零 receipt/provenance；同 intent 同 package 只有在 database 仍空、无其他 journal，且 staged 文件名属于 receipt asset ledger、final 文件逐项 hash 匹配该 ledger 时才自动清理未发布候选并从头重试，任何歧义继续 `RECOVERY_REQUIRED`。
- **不覆盖与重放：** 本机任一业务 owner、private attachment、receipt/provenance/settings 或 journal 均阻断恢复；相同 intent+包只回读完整 content-free receipt，异包 intent 冲突，绝不二次写入。
- **定向验证：** Android Studio JBR、`--offline --no-daemon` 下：`WorkspaceExchangeV2OwnerMapperContractsTest` **9/9**、`P6KZipImportRoomContractsTest` **5/5**、`P5DLocalBackupRestoreContractsTest` **4/4** 全绿。新增 Robolectric Room 合同覆盖 schema-38 实例的既有事实保留与 39 三表存在、完整 migration 链，以及“附件提升后受控中断”零对象/零 receipt/零 provenance、journal 存在、同包安全回收重试、journal 清除与完整 receipt/provenance 发布。`git diff --check` 通过。
- **严格停止：** 未接 Settings/OpenDocument/ViewModel、没有用户选文件、模拟器或 OPPO 验收；未验证真实历史 schema-38 全库的升级、真实进程杀死后的 journal 恢复、真实用户附件以及 UI 中文失败提示。不能称 Android v2 恢复、P6 或 P0–P11 完成。下一最小增量如获独立授权，才可为既有 owner 接单独的 SAF/UI 合同与真实文件验收；不得绕过当前 empty-local 与 journal 门。

## 2026-08-23 P6：Android v2 原子恢复唯一领域 owner 已立约；持久写入仍未接入

- **唯一所有者与入口：** 新增 `WorkspaceExchangeV2AtomicRestoreOwner.restore`。它只接收有界 package bytes 与 opaque intent ID，且必先经 `NfaiExchangeV2PackageReader` strict preflight；自身不接受或触及 URI/path、SAF、UI、Room/DAO、Context、网络、Provider 或 Key。
- **不覆盖门：** 只有 atomic store 报告本机业务真值为空才允许提交；已存在对象、私有附件、v2 provenance 或恢复 journal 都以 `LOCAL_TRUTH_PRESENT` 停止，绝不 merge/upsert/替换/删除。相同 intent 仅可回读相同 package+semantic hash receipt；不同包是 `INTENT_CONFLICT`。reader 拒绝在任何本机读取/commit 前停止。
- **原子边界合同：** 未来唯一 store 的 `commitEmptyLocal` 必须在一个 Room transaction 或等价可恢复边界中，二次重验空本机/intent，写完整 typed workspace、私有附件 staging/hash readback、provenance、content-free receipt 与 typed readback；失败不得发布成功 receipt，candidate 只能按 recovery journal 保存。完整合同见 `P6_ANDROID_V2_ATOMIC_RESTORE_CONTRACT.md`。
- **定向验证：** Android Studio JBR、`--offline --no-daemon` 下 `WorkspaceExchangeV2OwnerMapperContractsTest` **7/7** 通过：有效包只调用一次 atomic commit、篡包零本机观察/零写、非空本机零 commit、同 intent 重放不重写、intent 冲突及 recoverable commit 失败不返回成功。`git diff --check` 通过。
- **严格停止：** 还没有 concrete Room atomic store、schema/DAO、private attachment staging、Settings/OpenDocument/ViewModel、模拟器或真实用户文件路径；不可称 Android v2 已导入/恢复或 P6/P0–P11 完成。下一步只可实现并定向验证 concrete Room atomic store（含 full typed mapping、附件/receipt/provenance 同一恢复边界与重开 readback），再另立 UI/SAF 增量。

## 2026-08-23 P6：Android v2 严格 package reader 基础已闭合；尚未导入或恢复

- **唯一所有者：** 新增纯内存 `NfaiExchangeV2PackageReader`，是 Android `nfai.exchange.v2` bytes 的唯一 strict preflight reader；`NfaiExchangeV2PackageWriter` 已在发布前回读自身 package 时复用该 reader，避免 writer 与未来导入各自解释 ZIP/manifest/IR/asset ledger。
- **范围与安全：** reader 只接收调用方提供的有限 bytes，逐项验证 ZIP 安全、精确 manifest、canonical IR/semantic hash、附件账本/bytes hash 与 owner-field hash；结果仅含 content-free receipt、canonical IR 和本次调用的内存附件 bytes。不读写 SAF、Room、私有 staging/文件、URI/path、网络、Provider 或 Key。
- **定向验证：** Android Studio JBR、`--offline --no-daemon` 下 `WorkspaceExchangeV2OwnerMapperContractsTest` 4/4 通过：writer 输出经 reader 获得同一 receipt，附件字节在当前调用内一致，篡改 ZIP 整体拒绝。`git diff --check` 通过。
- **严格停止：** 尚无 Settings/OpenDocument、ViewModel、Room transaction、staging、冲突/恢复语义、模拟器或真实用户文件路径；不能称 Android v2 导入、双向恢复、P6 或 P0–P11 完成。下一步必须先为 Android 恢复定义独立的原子所有者与“不覆盖现有本机真值”边界，再接 UI。

## 2026-08-23 P5：code-52 正式构建、OPPO 数据保留覆盖与前台启动已验收

- **产物与本机验证：** `app/build/outputs/apk/release/南枫AI.apk` 是 `com.nanzhufeng.ai / versionCode=52 / versionName=0.3.0-p10a`，SHA-256 为 `c5112374643319617e8cffa1e32fab94605bd4cccadda6fbb6d390f99fd23f91`。Android Studio JBR 下 `apksigner` 验证 v2/v3 为真，release-v2 证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **构建证据：** 外部 Gradle 8.7 任务退出后，先提交 AAPT2 metadata 最窄修复 `08cd46c`；随后仅一次 Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`--offline --no-daemon` 的 `:app:assembleRelease` 成功（51 个 task，14 执行）。四项 `nanfengAi.releaseV2.*` 备用属性只以非空状态核验，未读取或输出值。
- **OPPO 覆盖前后：** 目标 `3B157F009E800000` 安装前为 code 51；`firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378`。同包名、较高 versionCode 与同证书门均通过后，仅一次 `push -> pm install -r --user 0` 成功。安装后为 code 52，首次安装时间及两个 inode 不变；从设备只读拉回的 installed `base.apk` SHA-256 与本地产物完全相同。
- **前台验收与边界：** 精确 Launcher `com.nanzhufeng.ai/.NanfengAiActivity` 返回 `Status: ok`，并成为 OPPO `mCurrentFocus`/`mFocusedApp`。未抓取屏幕或 UI 文本、未读取会话/私有数据、未运行 `connected*AndroidTest`、未卸载或清数据；仅移除 `/data/local/tmp/nanfeng-ai-0.3.0-p10a-code52.apk` 推送临时文件。
- **总控边界与下一步：** 这关闭 P5 的当前正式构建、验签、一次数据保留 OPPO 覆盖及前台启动门；不等于发布回下载、Provider、Windows、OAuth/Supabase、真实工具/生态目标、P10 双消费者或 P0–P11 总控完成。下一条工作应从 `MASTER_DEVELOPMENT_BLUEPRINT.md` §17 选择一个仍未关闭的最小真实增量，并先重新核对实时外部门。

## 2026-08-23 P11：AAPT2 verification metadata 最窄闭环；P5 build 门已恢复

- **当前事实：** 外部 Gradle 8.7 的 wrapper、worker 与 daemon 已不在进程列表；仅保留与该任务无关的空闲 Kotlin daemon。当前工作树基于 `041bb14`，只修改 `gradle/verification-metadata.xml`。
- **精确变更：** 使用 Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`--offline --no-daemon` 对 `:app:processReleaseResources` 执行一次 `--write-verification-metadata sha256`，只新增 `com.android.tools.build:aapt2:9.3.1-15703166` 的 macOS JAR 与 POM SHA-256；未升级依赖、未访问签名值、网络、设备或应用数据。
- **回读：** 同一 `:app:processReleaseResources --offline --no-daemon` 无写入任务通过（14 项均已是最新）；XML diff 仅 8 行新增，`git diff --check` 通过。
- **下一唯一门：** 提交此 P11 最窄增量后，才可用相同 JBR/offline/no-daemon 运行一次 code-52 `:app:assembleRelease`。若成功才继续验签、OPPO 只读复核与最多一次 `pm install -r --user 0` 数据保留覆盖；任一门失败即停止，不循环重试。

## 2026-08-21 总控 P0–P11：交接卡（当前恢复点）

- **当前目标：** 只以 `docs/MASTER_DEVELOPMENT_BLUEPRINT.md` §17 的 P0–P11 为“总控方案”。P6 本机 Android→Desktop 实链、P11 本机供应链项均有已证实增量；P0–P11 整体仍未完成，不能以构建、审计或单一验收替代总控退出。
- **当前事实源：** 工作树在 `d02e70b6d88afe0642dabae52b7f115a24deaaec` 后干净。P5 已把 `versionCode` 从 51 升至 52；P11 已补过 release/KSP verification metadata，但 `:app:assembleRelease --offline --no-daemon` 的唯一一次尝试在 AAPT2 macOS JAR/POM 的两个未登记 SHA-256 条目处停止，未生成本轮 APK、未验签、未操作 OPPO。
- **已完成：** P6 完整 owner 的 Android DocumentsUI→Desktop native Open/Save 严格语义/字段/附件链已闭合；P11 修复 Desktop `lopdf` 高危 PDF 解析依赖、生成 release runtime/KSP verification metadata 并完成各自无写入回读；P2/P3 已严格确认 Provider 凭据/真实服务外部门且未发 HTTP；P5 已对 OPPO 做过只读包/证书/数据指纹核验。
- **未完成与外部门：** P2–P4 的真实 Provider/成本质量，P5 的 code-52 正式构建、一次数据保留 OPPO 覆盖与发布回读，P6 Windows 原生验收，P7 OAuth/Supabase，P8 真实工具，P9 真实生态目标，以及 P10 双消费者触发均未关闭。OPPO 禁止 Debug/Instrumentation/清数据/卸载；只允许经正式验签、更高 versionCode、同证书的单次 `pm install -r --user 0` 数据保留覆盖。
- **当前阻塞：** 另一项目 `NanzhufengVideoDownloader-Android` 的 Gradle 8.7 正运行 `:app:testReleaseUnitTest :app:stageFormalReleaseArtifacts`，相关 wrapper/daemon/Kotlin daemon/test worker 长时间未退出。按无 Gradle 争用门禁，南枫 AI 不能并发写 metadata、构建或中断对方进程。
- **下一条安全命令：** 在确认该外部 Gradle 完全退出后，使用 Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`--offline --no-daemon`，仅为 `aapt2-9.3.1-15703166-osx.jar` 与同版本 POM 写入 SHA-256 verification metadata；以同一 AAPT2/release task 无写入回读并提交。之后才可重新进行一次 code-52 `:app:assembleRelease`，并依次 `apksigner`、OPPO 只读指纹复核、一次覆盖安装和可见 UI 验收。任一门失败即停止，绝不循环重试。
- **关键提交：** `3f67852`、`ae3d8ce`（P6 实链）；`04b9c1f`、`734ec23`、`a00c708`、`d02e70b`（P11/P5 供应链与停止证据）；`9cee1b8`、`7973f18`（OPPO 只读门与 code-52 准备）。

## 2026-08-21 P5：新的单次正式 build 在 AAPT2 verification 处停止

- **事实：** P11 KSP metadata 增量已提交为 `a00c708` 并无写入回读成功后，按授权只执行一次 code 52 的正式 `:app:assembleRelease --offline --no-daemon`（Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`）。正式签名 fallback 只以四项用户级项目命名属性的非空状态确认完整，未读/打印其值或访问 Keychain。
- **停止原因与范围：** `:app:kspReleaseKotlin` 已通过；` :app:processReleaseResources` 的 detached configuration 随后发现不同的未登记工件：Google `com.android.tools.build:aapt2:9.3.1-15703166` 的 macOS JAR 与 POM。此任务只授权原先三个 KSP 工件，故未写入这两个 AAPT2 SHA-256、未重试 assemble。没有可用本轮 APK、验签、OPPO 读取/写入、安装、启动或可见 UI 验收。
- **唯一下一步：** 只有新的、明确范围的 P11 授权补齐并无写入回读这两个 AAPT2 条目，才可再申请正式 build。P5 与 P0–P11 绝不因 KSP 局部修复而称完成。

## 2026-08-21 P11：KSP dependency verification 缺口已最窄补齐并回读

- **精确范围与修改：** 基于失败的 `:app:kspReleaseKotlin` 输出，只在 `gradle/verification-metadata.xml` 增加三个 detached-configuration 工件的 SHA-256：`kotlinx-coroutines-core-jvm-1.6.4.jar`、`symbol-processing-aa-embeddable-2.2.10-2.0.2.jar` 与同版本 POM。diff 为单文件 11 行新增；XML 有效，秘密模式检查和 `git diff --check` 均干净。未升级依赖、未放宽/关闭验证、未访问 Keychain、签名值、网络、设备或应用数据。
- **实际回读：** Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`--offline --no-daemon` 下，写入命令仅为 `:app:kspReleaseKotlin --write-verification-metadata sha256`；同一 release KSP 任务随后不带写入参数成功，证明当前元数据已实际应用。清单 SHA-256 为 `beb92e5168e69ed2396e97ac52a6b48b07372885d17299b0cdd1dca7ca82bd21`。
- **严格边界与唯一下一步：** 预检看见两个其他 Gradle 版本的空闲 daemon；本轮仍以 `--no-daemon` 单次 daemon 执行并停止。该局部 P11 门不等于 P11 或 P0–P11 完成。元数据提交后，唯一允许的后续动作是对 code 52 执行一次新的正式 `:app:assembleRelease --offline --no-daemon`；只有成功后才做 APK、OPPO 只读门与一次数据保留覆盖安装。

## 2026-08-21 P5 OPPO：正式签名 fallback 完整，已递增版本号待单次构建

- **签名结论：** 四项 `NANFENG_AI_RELEASE_V2_*` 环境变量不存在；按“环境变量优先、用户级项目命名属性备用”的正式规则，仅检查 `~/.gradle/gradle.properties` 中四项 `nanfengAi.releaseV2.*` 的非空布尔状态，均完整。未读取/输出任何值，未访问 Keychain。
- **最窄实现与构建停止：** `app/build.gradle.kts` 已从 `versionCode=51` 递增至 `52`，`versionName` 仍为 `0.3.0-p10a`；没有用户功能或功能审阅改动。随后仅一次 `:app:assembleRelease --offline --no-daemon` 在 `:app:kspReleaseKotlin` 因 Gradle dependency verification 缺少 3 个 detached-configuration 工件校验条目而失败。未更新校验清单或重试，未生成本轮 APK、验签、安装、启动或 UI 验收；OPPO 仍未再次读取或写入。
- **下一唯一门：** P11 必须在独立授权任务中修复并回读该 dependency-verification 缺口，之后才能以当前 code 52 重新申请一次正式构建。构建成功后，只有 package 一致、code 大于 51、v2/v3、release-v2 证书一致且 OPPO package/data fingerprint 正常，才可一次 `push -> pm install -r --user 0`；任一门失败即停止。见 `P5_OPPO_READONLY_GATE_AUDIT_20260821.md`。

## 2026-08-21 P5 OPPO：同版本字节差异触发只读停止；未安装、未启动或操作数据

- **只读结论：** OPPO Find N5 `3B157F009E800000` 上的 `com.nanzhufeng.ai` 为 `51 / 0.3.0-p10a`，已安装 `base.apk` SHA-256 为 `fc8f9ac6…52546`；当前源码正式 APK 为同样的 `51 / 0.3.0-p10a`、SHA-256 `7406d1de…1ea2`。两者均为同一 release-v2 证书（SHA-256 `6d1d56ec…611f8`）且 `apksigner` v2/v3 通过，但当前 APK **不是更高版本**，故不满足授权中的唯一覆盖安装前提。
- **主设备保护：** 本轮只有 `adb devices`、目标包 `dumpsys`/`pm path` 和 installed `base.apk` 只读拉回；没有 `connected*AndroidTest`、Debug/Instrumentation、自动部署、清理、卸载、清数据、迁移、DB 注入、安装或 UI 启动。User 0 的 `ceDataInode=1459104`、`deDataInode=1433378`、首次/最后安装时间均已记录；应用私有目录受系统权限保护，未绕过读取。
- **P5 停止门：** 当前源码的 OPPO 覆盖安装/可见 UI/升级迁移/数据不丢失和发布回下载仍未验证；不能用同版本同证书推断可安全升级。只有出现更高 `versionCode`、同包名、正式 v2/v3 验签通过的 APK，才重新走一次安装前只读复核并最多执行一次 `push -> pm install -r --user 0`。详见 `P5_OPPO_READONLY_GATE_AUDIT_20260821.md`。

## 2026-08-21 P3 真实对话执行：一次去内容化复核后保持外部门；P11 Gradle 元数据已完成本机回读

- **P3 结论：** §17 P3-A～H 的本地对话主体、preflight、receipt 与 fail-closed transport 合同仍在；原始退出所需的真实流/停止/失败/重试/换模型、部分计费、真实用量/成本、长会话实测与 OpenRouter 充分性判断均未获得本轮证据。只读源码/合同复核确认普通聊天没有 production egress owner，`DisabledNoNetworkProviderTransport` 不发事件或假回复；P2-M bridge 与未引用的 Direct composition 都在默认拒绝端口前停止。详情见 `P3_REAL_EXECUTION_GATE_AUDIT_20260821.md`。
- **严格边界：** 未读/探测应用私有凭据，未访问 Keychain，未构造 Authorization、HTTP、nonce、Provider Attempt、Token、费用、Candidate 或 Knowledge；未运行 Android/模拟器/OPPO。未以 DB/命令注入、假回执或启动参数替代真实出口，未新增产品功能、常驻入口或功能审阅条目。
- **P11 本机闭环：** 确认系统无活动 Gradle/GradleDaemon 后，使用 Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`--offline --no-daemon` 对 `:app:releaseRuntimeClasspath` 运行 `--write-verification-metadata sha256`，生成仓库内 `gradle/verification-metadata.xml`；随后以相同离线 release 解析（不带写入参数）回读通过。清单为 Gradle 原生 XML，当前含 516 个 component、923 个 artifact SHA-256，文件 SHA-256 为 `2bc1ea7266a3fbe6ab3adfd5690ea3812d344409793d958207f1bc23ae962add`。未升级依赖、未构建/安装 APK、未操作设备、Keychain 或密钥。
- **严格边界：** 该清单固定本机已解析工件的 SHA-256 校验，不等同 dependency locking、冷缓存/CI 全变体复现、依赖安全扫描或 P11/P0–P11 完成；P2–P9 的真实外部门及 P11 的持续运营门保持不变。详见 `P11_SUPPLY_CHAIN_REVIEW_20260821.md`。

## 2026-08-21 P2 Provider/成本质量：最小真实健康门因凭据/授权外部门停止

- **结论：** 当前工作树干净，HEAD 已包含 `04b9c1f` 与 `94838b7`。依 `P2M_REAL_TEXT_EXECUTION_CONTRACT.md` 审核唯一的 `p2m-openrouter-text-v2` 最小文本路径后，正式签名的用户级项目命名配置为完整；但允许检查的项目专属 Provider 环境变量不存在，用户级项目命名属性也没有 Provider 凭据 schema。Android 应用私有加密凭据存储本轮没有被读取、解密、导出、记录或探测，故不存在可合法用于本轮的 Provider 凭据/可见逐次同意。没有发行或消费 nonce、没有构造 Authorization、没有 HTTP、没有 Provider Attempt、Token、费用、Candidate 或 Knowledge 写入。
- **非敏感事实与边界：** 唯一 Provider 是 OpenRouter，固定 `POST https://openrouter.ai/api/v1/chat/completions`；实际模型只能由已保存、启用且已验证的 OpenRouter preset 解析，本轮未读取应用私有实际选择值。若未来重新满足门，范围仍限合成文本、`stream=false`、`temperature=0.2`、结构化 `title/body`、USD 0.01 上限与 `retryCount=0` 的至多一次 Attempt。图片、附件、用户内容、实际质量、真实 Token/费用、真实保存/重启/导出、真机及 OPPO 仍完全未验证。
- **本地回归：** Android Studio JBR + `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` + `--no-daemon` 下，P2-K transport 5/5、P2-L acceptance 9/9、P2-M bridge 3/3，共 **17/17**、0 failures/errors/skipped。它们只证明 DryRun 零网络/零 Key bytes、单次 nonce 与零重试 fail-closed 合同；未启动 Android 设备、未访问 Provider 网络，不能替代真实健康、成本或质量证据。详情见 `P2_PROVIDER_REAL_GATE_EVIDENCE_20260821.md`。
- **下一步：** 仅在用户明确配置并允许检查凭据存在性、同一冻结 RunSpec 在应用可见确认页获得逐次同意时，才可通过 P2-M 发出一次合成非敏感文本请求；成功或失败均立刻停止。P2 图片与完整 P2 出口仍需独立合同。此条不宣称 P2 或 P0–P11 完成，也不新增用户功能/常驻入口，故功能审阅无需变化。

## 2026-08-21 P11 供应链复核：Desktop PDF 解析高危项已修复；总控下一门仍受外部条件约束

- **已完成与提交：** `04b9c1f fix(p11): patch desktop PDF parser advisory` 将 Desktop `lopdf` 从 `0.35.0` 升至 RustSec `RUSTSEC-2026-0187` 的修复线 `0.42.0`。`cargo check --locked`、`cargo clippy -- -D warnings` 与 Rust `97/97` 均通过；`cargo fmt --check` 只暴露既有 `desktop/src-tauri/src/lib.rs` 大范围格式债务，未写入格式化改动。完整清点、npm（0 production dependencies / 0 vulnerabilities）、Android release runtime 解析和未覆盖面见 `P11_SUPPLY_CHAIN_REVIEW_20260821.md`。
- **P6 原始出口校正：** §17 的 P6 退出只要求 Android 导出 → Desktop 导入 → 再导出精确保真，以及紧凑/展开、本地文件、更新与异常恢复的独立验收；**不要求** Android v2 回导/archive/recovery。最新 macOS 完整 owner DocumentsUI→Desktop native Open/Save/re-export 已闭合；Android v2 回导最多是独立质量增量，不能再当作 P6 或 P0–P11 排程前置。
- **严格停止与下一步：** `context_gate.py` 已在本阶段返回 `WARN` 并生成交接卡；不得在本线程展开新功能。P2–P4 真实 Provider/流/成本质量、P5 当前源码 OPPO/发布、P6 Windows 原生验收、P7 OAuth/Supabase、P8 真实工具、P9 真实目标均仍是各自外部门；P10 尚未满足双消费者触发条件。若下一线程继续 P11 的 Gradle 供应链完整性，应在无其他 Gradle 争用时独立生成并验证仓库内 dependency verification metadata；本轮该生成未产出文件，绝不记为完成。未读 Key、未发 Provider HTTP、未操作 OPPO、`5554/5556/5558/5570` 或 `5582`。

## 2026-08-21 P6 v2 完整 owner：Android DocumentsUI → Desktop native Open/Save 独立验收已闭合；P6 总门仍未退出

- **真实 Desktop 子链：** 新生成且 deep/strict 验签的 macOS acceptance bundle 只使用全新项目专属 `/tmp` root，经 Desktop 设置 → 数据导入 → 完整工作区交换（v2）打开原生 Open picker，实际选择 Android 的 3,955 B 普通 `.zip`；strict preflight、private archive/transaction 与 content-free UI receipt 成功。随后由同一已提交私有记录打开原生 Save picker，在新的空输出目录回导 3,851 B `.nfai-exchange`，UI 两次显示 semantic `fdf9f95ac840…d9ff9` 与 1 项附件。
- **真实缺陷与最窄修复：** Android DocumentsUI 实际普通 `.zip` 被 Desktop picker filter 展示却在 Rust 读 bytes 前拒绝，因旧 gate 只接纳 `.nfai-exchange` / `.nfai-exchange.zip`。现在普通 `<stem>.zip` 只作为显式输入候选；空 stem、嵌套/追加后缀仍拒绝，且 name 永不成为身份或持久化豁免，必须完整通过 exact manifest、semantic、owner-field hash 与 asset ledger strict preflight。回导继续只接受 `.nfai-exchange`。这是既有设置二级入口的兼容修复，不新增用户功能或常驻入口，功能审阅无需新增条目。
- **严格去内容化读回：** 输入/输出 Node strict verifier 均为 entries `3`、assets `1`、semantic `fdf9f95ac84005a173807779c055f0a3bd2112b01beb9f7bb28763dbe19d9ff9`；8 项 owner-field hash 的 field-set digest 为 `ead2f34eea50ffce928c97d9e117b766de9b2ea07bd52864da1649a6ed4aea1c`，receipt 与所有 provenance 一致。独立 root 的 v1 `workspaces/workspace_exchange/import_journal` 为 `0/0/0`；v2 `imports/assets/provenance/journal/receipts` 为 `1/1/8/1/1`。输入 package SHA-256 `54b48a470033bde62761a6030b4fdf7a6dfb70ee8bef3f0393f38324f910c37c`，回导 package SHA-256 `acb46537e30227cfbe538ca8eff4c06b953bbdecc8dfc3936bd270fd34a06ede`；byte package 不同但不是语义或字段差异。
- **严格边界与下一步：** 未触碰 OPPO、Android 设备或任何禁止的 AVD，未用 DB/command 注入、Keychain、Provider 或网络；此前失败的独立根未导入/回导且未复用。此 macOS 子链不证明 Android v2 import/archive/recovery、Desktop 原生业务对象恢复、Windows WebView2/安装/签名/缩放/IME、发布、P6 完成或 §17 P0–P11 完成。Windows 是当前该原始出口的外部门，本机不得伪造；在开始其他 P0–P11 子任务前先运行 context gate 并从总控审计选择未受外部门阻断的最窄候选。

## 2026-08-21 P6 v2 完整 owner：Android DocumentsUI 真实导出与严格去内容化读回已闭合；Desktop 独立 native Open/Save 因 context gate 待新线程

- **真实 Android 链：** 仅在新建的 `NanfengAiLocalControlOwnerAcceptance` / `emulator-5582`（`com.nanzhufeng.ai.p6v2fullowneracceptance`）经可见正常 UI 完成：Settings → 数据与导入 → 导入中心 → 完整工作区交换（v2）→ DocumentsUI。范围弹窗实测为 `项目 1 · 对话 1 · 知识 2 · 记忆 1 · 关系 1 · 附件 1`；DocumentsUI 保存后 App 显示严格回读 `fdf9f95ac840… · 6 项对象 · 1 项附件`。输出 `nanfeng-ai-workspace-v2-owner-acceptance.zip` 3,955 B，只读 pull package SHA-256 为 `54b48a470033bde62761a6030b4fdf7a6dfb70ee8bef3f0393f38324f910c37c`；Node strict verifier 通过：semantic `fdf9f95ac84005a173807779c055f0a3bd2112b01beb9f7bb28763dbe19d9ff9`、entries 3、assets 1。
- **字段/账本读回：** 只输出匿名结构：roots 为 Project/Conversation/Knowledge/Memory/Relation = `1/1/2/1/1`，`ownerFieldHashes` 为 8 项（project、conversation、knowledge×2、memory、relation、settings、asset），asset=1，field-set digest `ead2f34eea50ffce928c97d9e117b766de9b2ea07bd52864da1649a6ed4aea1c`。package 只含 `manifest.json`、`exchange.json` 和一条 content-addressed asset；未打印正文、标题、ID、路径或附件 bytes。
- **本轮收敛的真实缺陷：** 首次 UI 导出安全拒绝 `知识 sourceReference 不能进入 v2 交换。`。根因是手工 Knowledge 把 `sourceReference` 硬编码为 `manual`，与 v2 locator 拒绝合同冲突；现改为 null。另修复 `RoomKnowledgeRepository.listSnapshots` 忽略 filter、使回收站 Knowledge 泄入“完整工作区范围”的错误。定向 JVM `P4EKnowledgeRoomContractsTest` + `WorkspaceExchangeV2OwnerMapperContractsTest` 通过；最终正式签名验收 APK SHA-256 `dc2724421404161ff7b5e626fc96835ac381f038104e3a0bc8c02dcc50ff6869`，v2/v3 verify 通过，仅对 5582 覆盖安装且 `firstInstallTime` 未变。
- **严格边界：** 未触碰 OPPO、`5554/5556/5558/5570`，未运行 instrumentation/connected test/自动部署或清理，未使用 Activity extra、deep link、Room/SQLite/DB 注入、Provider/HTTP/Keychain。旧 relation 由可见 UI 撤销、旧 Knowledge 由 UI 移入回收站，后以两条新的非敏感 Knowledge 建立一条 RELATED；这不是 P6、Android import/recovery、Desktop 原生对象恢复、Windows、发布或 P0–P11 完成声明。
- **唯一下一步：** `context_gate.py` 于 00:17 返回 HANDOFF（94.3%），故本线程不启动 Desktop。新线程先读 `desktop/scripts/prepare-p6-v2-picker-acceptance.mjs`，创建全新项目专属 Desktop bundle/root；随后仅经 Desktop Settings 的 native Open picker 选择 `/tmp/nanfeng-ai-p6-v2-owner-acceptance.Vv2U3s/android-v2-owner-acceptance.zip`，native Save picker 回导，再对独立 root 的 receipt/provenance/v1-zero 做 content-free semantic、全量 owner-field 和 asset-ledger 比对。当前两处源码修复未提交，不得覆盖。

## 2026-08-20 P5-A 更多本地控制面：双端设置入口、构建与新空 UI owner 创建已验证；P6 文件链因 context gate 待新线程继续

- **已实现与自动门：** 新合同 `P5A_LOCAL_CONTROL_SURFACE_ENTRY_CONTRACT.md` 固定“设置 → 更多本地控制面”作为唯一普通入口。Android 仅由该二级页进入既有 `P5ARoute.CONTROL`，并补齐 Projects、知识（含关系）与长期 Memory 用户路径；CONTROL 可返回设置。Desktop 在同名设置页仅调用已有 `show-projects`、`show-knowledge`、`show-memory` 工作页 action。Android `AndroidUserEntryAuditContractsTest`、`assembleDebug`、正式签名 `assembleP6V2FullOwnerAcceptance`、Desktop lint/65 个 Node UI 测试/静态 build/Rust check 均通过。
- **新空 UI evidence：** 仅新建 `NanfengAiLocalControlOwnerAcceptance` / `emulator-5582`，安装 `com.nanzhufeng.ai.p6v2fullowneracceptance`。可见启动器 → 对话抽屉 → 设置 → 更多本地控制面 → CONTROL 显示无 Provider/外部访问边界及 Projects/知识/记忆路径；同一空数据仅经正常表单和 DocumentsUI 创建 `OwnerProject`、两条手工 Knowledge、一条人工 RELATED、`OwnerMemory` 和含 `owner-attachment.json` 的已提交 Conversation 附件。未操作 OPPO、`5554/5556/5558/5570` 或既有数据，未用 Activity extra、deep link、Room/SQLite、DB 注入、Provider、HTTP 或 Keychain。
- **唯一下一步：** 从该仍运行、已唯一写入上述非敏感对象的 `emulator-5582`，仅用设置 → 数据与导入 → 完整工作区交换（v2）→ DocumentsUI 导出，取得 content-free readback 后再转独立 Desktop native Open/Save 与 strict semantic/owner-field/asset-ledger readback；Windows 不在 macOS 伪造。`context_gate.py` 已因 91.7% 返回 HANDOFF，当前线程不得继续该 P6 scope。

## 2026-08-20 P6 v2 真实完整 owner 文件链：原紧凑 UI 入口缺口已由 P5-A 独立合同修复；新环境文件链待继续

- **只读 GUI 复查：** macOS 已解锁，隔离验收包在 `emulator-5570` 前台可读。实际用户 UI 的对话抽屉只呈现“设置”和“新对话”；“设置”只呈现 AI 模型、对话、数据与导入、功能审阅、隐私。未通过 Activity extra、deep link、SharedPreferences、DB、shell 业务 command 或任何其他内部路由切换页面，也未创建任何验收对象。
- **真实缺口：** 源码和实际 UI 一致表明 `P5ARoute.CONTROL` 仅有枚举、渲染与说明，缺少所有用户可点击的进入路径；`P5ARoute.PROJECTS`、`MEMORY`、`KNOWLEDGE` 只在 ControlHub 内部作为后续目标。故当前紧凑用户 UI 无法到达 Project、手工 Knowledge 或长期 Memory 的正常生产表单，进一步也无法从 UI 创建其上的 RELATED 关系。当前对话/附件路径本身可见，但不能单独填充其余 owner 后将这条不完整数据称为完整 owner 文件链。
- **修复后边界：** 入口缺口不是 DocumentsUI、Desktop picker 或字段保真失败；已按独立 P5-A 合同恢复可见、可返回、双端功能审阅登记的紧凑 UI “更多本地控制面”入口，且已在新的 `emulator-5582` 正常 UI 创建最小 owner。完整 owner 的 Android DocumentsUI → Desktop native Open/Save 回导仍没有开始，所有 P6、Windows 和 P0–P11 未完成结论保持不变；后续不得用启动 Intent、内存 ViewModel、fixture、Room/SQLite 或 command 注入替代新环境 UI 链。

## 2026-08-20 P9-B 续行授权收紧：本地合成目标仍非生态接入

- **修复与所有权：** `P9BLocalTestOnlyHarness` 与 Desktop `p9b_integration_contract_v1` 现在在 `AUTHORIZE/PREVIEW/CONFIRM/RESULT/READBACK` 每步重新绑定同一 opaque `appHandle`、未过期 grant；目标重选或 expiry 立即安全终止，不再触碰 synthetic target、确认或创建新的 local receipt。`CANCEL/REVOKE` 保持可用以收束。receipt 仍只属于 `LOCAL_TEST_ONLY` 合成 metadata，绝不表示目标应用结果、写入或外部副作用。
- **合同与验证：** `P9B_LOCAL_TEST_ONLY_INTEGRATION_CONTRACT.md`、`P9B_LOCAL_EXIT_EVIDENCE.md` 已同步。Android 定向 `P9BIntegrationContractTest` 5/5 通过；Desktop `cargo test p9b_integration_contract_v1` 3/3、单文件 `rustfmt --check` 与 `cargo clippy --lib -- -D warnings` 通过。全仓 `cargo fmt --check` 仅报告既有 `desktop/src-tauri/src/lib.rs` 格式债务，未写入。本轮未运行 Android 全量/lint/构建、Desktop frontend/bundle、GUI 或安装。
- **仍未关闭：** 没有目标应用的公开稳定入口、权限 UI、真实目标侧确认、跨应用读取、真实结果归属、真实超时或撤销。因此 P9 真实只读闭环仍未开始；候选写入继续未授权。没有网络/Provider/Keychain/文件/DB 注入，也没有操作 OPPO 或 `5554/5556/5558/5570`。

## 2026-08-20 P8 本地计划准入双端收紧：只强化 test-only 合同，未开启真实工具

- **本轮边界：** 只修改 `ControlledAgentRuntime` 与 Desktop `AgentLedgerStore` 的 `LOCAL_TEST_ONLY` plan admission。批准前必须把 Run 已用量与完整计划分别对照 steps、tool calls、side effects 三类预算；同一计划的 step idempotency key 重复时，在 approval 前写 durable failure Event/Checkpoint 并拒绝，绝不产生 receipt 或执行。没有新增 Agent UI、Tauri executor/capability、Settings entry、Provider/HTTP/Key、文件、跨应用、购买、删除、外发或业务数据写入。
- **合同与证据：** `P8B_READ_ONLY_AGENT_LEDGER_STATUS_CONTRACT.md` 和 P8-D red-team matrix 同步为上述双端规则。Android 定向 `P8ControlledAgentRuntimeContractsTest`、`P8BReadOnlyAgentLedgerStatusContractsTest`、`P8AgentLedgerRoomContractsTest` 通过；Desktop `p8_agent_ledger_v1::tests` 7/7、`cargo clippy --lib -- -D warnings` 和 `rustfmt --check src/p8_agent_ledger_v1.rs` 通过。未启动、安装或写入 OPPO、`emulator-5554/5556/5558/5570`，未执行 GUI、DB 注入、网络或 Keychain 操作。
- **功能审阅：** 本轮没有新增用户可见功能或常驻入口，Android/Desktop “设置 → 功能审阅”无需新增条目。
- **仍未关闭：** P8 仍只完成本地主体。真实研究、文件/系统、跨应用、Provider/HTTP、外发、购买、删除等每个工具都必须另立合同并完成真实 success/failure/cancel/audit/idempotency/recovery 链；本轮 fixture 和本地回执不代表真实 Agent。P7 的真实身份/OAuth/HTTP、远端部署/跨设备恢复也继续未关闭；macOS 解锁后仅可按既有 P6 5570 正常 UI 续跑，不触该 AVD。

## 2026-08-20 P7-E Desktop 隔离候选身份与崩溃遗留门：本地代码/合同通过，P7 真实服务仍未启动

- **本轮边界：** 仅收紧 `p7e_isolated_workspace_v1` 的 app-private staging。候选目录名现在必须与重读的 canonical semantic hash 一致；既有候选复用、切换前与切换后均验证 header 和全部 typed semantic record。任一内容或 header 篡改均在写入 P7-E isolated workspace 前拒绝；不读取/修改 P6 workspace，不增加 Tauri command、设置入口、OAuth、HTTP、Keychain、账号、恢复码或网络。
- **崩溃与删除边界：** 旧式 `.hash.tmp` 遗留目录保留原样，新的 temporary candidate 使用 process-scoped 路径，故可安全重建且不把遗留/并发目录当成删除目标；仅本次自建 candidate 在失败时可被删除。这个增量不新增用户可见功能，Android/Desktop “设置 → 功能审阅”无需新增条目。
- **本地验证：** Rust 定向 `p7e_isolated_workspace_v1` 7/7 通过（含 legacy temporary 不阻塞、staged content 篡改拒绝、原有原子切换/回滚/P6 隔离）；`rustfmt --check` 与 `cargo clippy --lib -- -D warnings` 通过。未写 OPPO、`emulator-5554/5556/5558/5570`，未做 DB 注入、GUI、HTTP 或 Keychain 操作。
- **仍未关闭：** 这只加固 P7-E 本地隔离恢复候选，不证明真实 Google/Supabase/OAuth、受控 HTTP、部署回读、真实 Android/Desktop 跨设备恢复或 P7/P0–P11 完成。P6 在 macOS 解锁后仍按既有 5570 正常 UI 续跑顺序恢复，本轮没有触碰该 AVD。

## 2026-08-20 P6 v2 真实完整 owner 文件链：隔离环境和唯一验收包就绪，macOS 锁屏阻断正常 UI 创建

- **本轮边界：** 目标仍是 §17 P6 的真实完整 owner 文件链，而不是 P6 或 P0–P11 完成声明。只读盘点已确认 Project、Conversation、手工 Knowledge、长期 Memory、Knowledge 关系和 DocumentsUI 文件附件均有正常生产 UI；完整范围会从 Conversation 的已提交消息节点和 Knowledge 收集附件，故附件必须经正常 UI 加入草稿后本地发送进入消息树。未发现需要或允许使用 DB/SQL/command 注入的对象路径。
- **新隔离环境：** 既有 AVD 均不复用、不修复。已基于本机 Android 35 Google APIs arm64-v8a 镜像新建 `NanfengAiP6V2FullOwnerAcceptance`，并仅以新的 `emulator-5570` 冷启动；没有对既有模拟器或用户设备写入。新增唯一 build type `p6V2FullOwnerAcceptance`，applicationId 为 `com.nanzhufeng.ai.p6v2fullowneracceptance`，不改生产包、数据模型或用户入口。产物 `app/build/outputs/apk/p6V2FullOwnerAcceptance/南枫AI-开发验收.apk` 使用正式 v2/v3 签名，SHA-256 为 `210eb393953a66f08dade69c7d6df972a9d0db673c7f06b28279350ce2a674af`；首次定向安装后 pull 回的 installed `base.apk` SHA-256 相同，版本为 `51 / 0.3.0-p10a-p6-v2-full-owner-acceptance`。
- **真实 UI 停止点：** 将验收包启动到新模拟器后，Computer Use 返回“Mac is locked，无法自动解锁”。因此尚未通过 UI 创建任一 Project/Conversation/Knowledge/Memory/Relation/附件，尚未打开 Android 设置范围或 DocumentsUI，未产生交换文件、Desktop private import、native Save 回导、receipt/replay/v1 readback 或布局/异常恢复证据。没有以 shell、Room、SQLite、fixture 或文件注入伪造任何对象或 UI 验收。
- **安全续跑顺序：** 用户手动解锁 macOS 后，只在 `emulator-5570` 中经正常 UI 创建最小无敏感 Project、Conversation（含本地发送附件）、两条手工 Knowledge、RELATED 关系及长期 Memory；随后设置 → 完整工作区 v2 → DocumentsUI 导出，独立 Desktop bundle 的 native Open picker 导入、native Save picker 回导，最后仅比较 semantic、全量 owner-field hashes、asset ledger、receipt/replay 与 v1 三表不变。Windows 本机验收仍为外部门，不在 macOS 伪造。

## 2026-08-20 P6 原规格证据矩阵与完整 owner 跨端自动门（HEAD 673f2d6）

- **总控口径：** “总控方案”只指 `MASTER_DEVELOPMENT_BLUEPRINT.md` §17 的 P0–P11 完整路线；本轮 `docs/P6_EVIDENCE_MATRIX_20260820.md` 只核对其中的 P6 原始范围和出口，不能替代或缩小总控方案。矩阵确认：Android v2 import/archive/recovery 与将 Desktop v2 private record 恢复为 Desktop 原生业务对象均不是 §17 的原始 P6 退出门，故未擅自实现或改写为本轮范围。
- **已补的最大安全出口：** 当前实际 v2 Android DocumentsUI→Desktop native Open/Save 文件链只含 Project + safe settings、0 附件；完整 Project/Conversation/Knowledge/Memory/Relation/附件组合此前仅有分端本地合同。新增 `scripts/verify-p6-v2-owner-fidelity-cross-platform.sh`：Android `WorkspaceExchangeV2OwnerMapperContractsTest` 以非敏感 fixture 写出完整 owner package，Node strict verifier 复验 package/semantic/asset ledger，Desktop `p6_workspace_exchange_v2` strict reader 必须读入 5 个 root、1 个附件及 Project/Conversation/Knowledge/Memory/Relation/settings/asset 的 field hash。脚本的临时包在退出时删除，不触及 picker、SQLite workspace、用户数据、设备、Keychain、网络或 Provider。
- **本轮验证：** Android Studio JBR + `TieredStopAtLevel=1` 的定向 JVM 类通过；Node 输出 semantic hash `ae8039c083529c42c5a72274fb633fc5f2fcbfefc2c6e33d8da4bbab33b10cf1`、3 entries/1 asset；Desktop Rust 定向 strict-reader test 1/1 通过。未写 OPPO、`emulator-5554/5556/5558`，未读 Key、未发 HTTP、未做 DB 注入。
- **仍未关闭：** P6 不是完成态。完整 owner 的真实 Android DocumentsUI→Desktop native picker/readback、受影响紧凑/展开与异常恢复的独立平台回归、Windows WebView2/安装/签名/SQLite/缩放/IME 本机验收仍各自待证；Windows 不在 macOS 伪造。下一步不得把本轮自动门外推为 Android v2 import/recovery、Desktop native object restore、备份/同步、OPPO、发布或 P0–P11 完成。

## 2026-08-20 P6 v2 Desktop 设置内回导：独立 bundle 的真实 Android→Desktop→native save picker 链已关闭

- **验收实例隔离：** 新增 `desktop/scripts/prepare-p6-v2-picker-acceptance.mjs` 和 `npm run prepare:p6-v2-picker-acceptance`，只复制已验签开发 bundle 到新的项目专属临时 `.app`、改副本 `CFBundleIdentifier` 与 `CFBundleExecutable`、对副本 ad-hoc 重签并输出隔离元数据。此次副本 identifier 为 `com.nanzhufeng.ai.desktop.p6v2pickeracceptance.37537.mt1m33x8`，副本可执行路径独立；源 bundle 可执行 SHA-256 在副本制作前后同为 `8858dff03bfffd5690b262d6c2ddc81140e49de6935f823a15897431d27e387b`。主 bundle、release 签名、Keychain 和既有同 identifier 实例均未改动；v2 import/re-export 两项 ACL 都在 capability 和生成 schema 内。
- **唯一实际输入：** 用户本轮明确授权仅只读 `emulator-5558` 的 DocumentsUI 保存目录。未启动/安装/写入/UI/数据库访问该 emulator；实际 `.nfai-exchange.zip` 为 1,091 B，设备端与 pull 后只读 fixture SHA-256 均为 `3601aeb36f00f94288c5df89c8c20b5f53aa0e4a2c8f65529e308fa90f205c53`。此前定位到的 2,340 B `android-v2-contract.nfai-exchange`（`fb81…5174`）不匹配，已排除。
- **真实 native UI 与 readback：** 空隔离 root 初值 v1 `0/0/0`、v2 `0/0/0/0/0`，空输出目录为 0 文件。Computer Use 以独立 identifier 精确定位副本，经设置 native Open picker 选中只读 Android 实际包，UI 回执为 `d0c3df7b5e92… · 0 项附件`，随后出现唯一“回导已提交交换 1”。该条目打开 native Save picker 并写入空输出目录后，UI 回执为“已从私有记录回导并严格回读：`d0c3df7b5e92… · 0 项附件`”。输出唯一文件为 1,023 B、SHA-256 `91fc13be723cef67a16b8ebaaea270f2964011a978929542b627d367ce731062`；Node strict verifier 的 semantic hash 为 `d0c3df7b5e9296b5adf4a975077c7d7796381342942c3167cd2acc70c056d986`、entries=2、assets=0。receipt/provenance 的 project/settings field hash 分别是 `f46d29158ead355ddc57ff331ecf02f30ba498d62fe6c965d98b8e9375fae1f6` / `778838e3f038975396707ec0a192eb9dbf94dd262bc5c354e746628fe4959b22`；最终 v1 仍 `0/0/0`，v2 为 `1/1/1/2/0`。无 DB/command 注入。完成后 macOS 锁屏，未尝试自动解锁或额外 GUI 操作。
- **自动门与边界：** `npm run lint`、Desktop Node UI `92/92`、Rust v2 root gate/re-export 定向合同各 `1/1` 通过；源/副本均 `codesign --verify --deep --strict`，`plutil -lint` 通过。此项只关闭 P6 v2 的最小非敏感 Android DocumentsUI→Desktop import→committed re-export 实际文件子链；不等同于 Android v2 import/archive/recovery、跨端完整对象恢复、Windows、正式签名/发布、OPPO，亦不关闭 P6 或总控 §17 P0–P11。

## 2026-08-20 P6 v2 Desktop 设置内受限回导：代码/自动门与 bundle 已通过，真实 native save picker 被既有同 Bundle ID 实例阻断

- **实现：** Desktop “设置 → 数据与导入 → 完整工作区交换（v2）”现在只枚举 import、journal、receipt 三者完整的 private v2 record（匿名 root/附件计数），用户选择一项后才打开 native save picker。新最小 capability 只允许该 committed-record list 与一个 `workspace-v2-*` 的 canonical `.nfai-exchange` 回导；Rust 从已提交 canonical `exchange_json` 和内容寻址 private assets 只读重建，`.part` 原子发布后重跑 strict preflight 并比对 semantic hash、全量 owner-field hash 与附件账本。没有 v1 表、普通 workspace、聊天/Composer、Provider、Keychain 或路径/正文/显示名/附件 bytes 投影。
- **功能审阅与合同：** Android/Desktop “设置 → 功能审阅”同步写明 Desktop 仅可在设置二级入口从已提交 private record 经系统保存位置回导；不新增聊天主页、Composer 或工作页按键，且不表示原生对象恢复、备份或同步。字段保真、Desktop transaction 与入口审计合同同步了 exact output、receipt、v1 隔离和失败语义。
- **自动/构建：** 新 Rust 设置回导合同通过；Rust library `93/93`、`cargo check`、`clippy --lib --tests -D warnings` 通过；Desktop lint/typecheck、Node UI `92/92`、static build 通过；Android Studio JBR + `TieredStopAtLevel=1` 的 `AndroidUserEntryAuditContractsTest` 通过。`cargo fmt --check` 仍只报告仓库既有大范围格式债务，未重排无关历史代码。`CARGO_NET_OFFLINE=true cargo tauri build --bundles app` 通过；随后仅对刚生成开发 bundle 做 ad-hoc 重签，`codesign --verify --deep --strict` 通过，主可执行 SHA-256 `8858dff03bfffd5690b262d6c2ddc81140e49de6935f823a15897431d27e387b`。
- **真实 UI 验收停止点：** 复用 `/tmp/nanfeng-ai-p6-v2-picker-acceptance.09id6Q`（启动前已有 3 个 private 文件）并新建独立输出目录。两次启动 bundle 时，macOS accessibility 都只定位到既有同 Bundle ID 的非隔离实例；发现后立即停止，未点击、未选择记录、未打开保存窗口，也未将该实例用作本轮验收。输出目录保持 `0` 文件；未用 command、SQLite 或文件注入替代 native save picker。因此回导的真实系统保存、输出 readback 以及与 Android 最初 hash 的 UI 路径对照仍**未验收**，不得以本轮自动/构建结果声称 P6 或 P0–P11 完成。
- **下一停止门：** 只有能可靠将 accessibility/窗口焦点绑定到此隔离 acceptance root 的 Desktop 实例后，才从设置二级入口选择已提交 v2 record → 原生保存窗口 → content-free receipt，并以输出 strict reader 核对 Android `d0c3df7b…6d986`、两项 owner-field hash 与 v1 三表不变；否则保持本条 UI 验收缺口。

## 2026-08-20 P6 v2 Android DocumentsUI ZIP → Desktop native picker：真实跨端导入与 replay 已闭合，实际回导待独立入口

- **真实链路：** 只读从 `emulator-5558` 的 DocumentsUI 下载目录取得实际 `nanfeng-ai-workspace-v2.nfai-exchange.zip`（1,091 B；package SHA-256 `3601aeb…205c53`），在新建且起始零项的 `/tmp/nanfeng-ai-p6-v2-picker-acceptance.09id6Q` 根中，用当前 ad-hoc 严格验签 Desktop bundle 的设置 → 数据与导入 → 完整工作区交换（v2）→ 原生 picker 选择该文件。UI 返回 content-free receipt，semantic hash `d0c3df7b…6d986`、附件 0；第二次同一 picker 选择返回“已验证重放回执”。未打开或输出任何 package 正文。
- **结果与隔离：** SQLite 只读 reopen 证明 `exchange_v2_imports/journal/receipts=1/1/1`、owner provenance=2（Project + settings），receipt package/semantic hash 与 Android 一致、owner-field-hashes JSON 已持久；v1 `workspaces/workspace_exchange/import_journal=0/0/0`。本轮的临时 login-session 环境变量和只属于验收根的 Desktop 进程均已移除；OPPO、5554、5556 未触及。
- **修正：** native picker 首次真实执行暴露 capability 漏登记而安全拒绝（`not allowed by ACL`）；已只新增 `allow-import-desktop-workspace-exchange-v2-selected`，并让 UI 显示真实脱敏拒绝而非泛化状态。名称兼容仍只接纳两种精确终止名，严格 v2 preflight 不变。
- **验证：** Node v2 golden、Desktop lint、91/91 Node UI tests、Rust picker bridge 与 clippy、Android DocumentsUI UI contract 均通过；当前 bundle deep/strict codesign 通过，主可执行 SHA-256 `ffa500e8…e4bec9`。实际已提交数据的回导文件尚无设置入口，本轮未用 SQL/文件注入伪造 re-export；现有 Rust re-export 自动合同仍不替代这一真实回导缺口。

## 2026-08-20 P6 v2 DocumentsUI `.zip` 名称兼容：代码/合同与自动门已接入，真实 Desktop picker 待 Mac 解锁

- **决策与实现：** Android 继续以 `application/zip` 创建文件，并固定建议显示名 `nanfeng-ai-workspace-v2.nfai-exchange`；DocumentsUI 实际追加 `.zip` 被定义为允许的系统命名行为，而非协议版本或内容标识。Desktop picker 的展示过滤器增加 ZIP，但 Rust 最终门禁只接受非空 `<stem>.nfai-exchange` 或 `<stem>.nfai-exchange.zip`，拒绝路径片段、追加扩展、`.zip.nfai-exchange` 与嵌套后缀；通过名字门禁后仍必须走既有 v2 strict preflight、私有 archive 和 SQLite transaction。没有为兼容名放宽 ZIP entry、manifest、semantic hash、asset hash、owner-field hash 或 v1/v2 version 门禁。
- **回执与治理：** Android 建议名/MIME、Desktop 允许名、拒绝样例和 content-free receipt 语义已写入 `P6_WORKSPACE_EXCHANGE_V2_FIELD_FIDELITY_CONTRACT.md` 与 Desktop transaction 合同。显示名、MIME、path、正文和附件 bytes 不进入 Android SAF 成功回执或 Desktop committed/replay 回执；文件名变化不产生语义身份。功能仍只在双端设置二级入口，功能审阅不新增常驻按键。
- **自动验证待本轮运行：** Node golden 新增两种允许名与投毒名拒绝；Desktop Rust bridge 新增 `.nfai-exchange.zip` 严格导入/replay 及投毒名拒绝；Android UI 合同新增 MIME、建议名和无显示名/MIME回执字段检查。尚未因此执行任何 picker、SQLite 注入、AVD 写入、OPPO 或网络动作。
- **下一停止门：** Mac 解锁后，在唯一空的 `NANFENG_AI_P6_V2_PICKER_ACCEPTANCE_ROOT` 内，用已由 Android 5558 导出的实际 `.nfai-exchange.zip` 仅经 Desktop 设置 → 数据与导入 → 完整工作区交换（v2）→ native picker 导入；读取 content-free receipt，再核对 reopen/replay/re-export semantic + field hash 与 v1 三表不变。若仍锁屏，不猜测或绕过，记录阻断并转其他本地 P6 工作。

## 2026-08-20 P6 工作区 v2 Android DocumentsUI 隔离验收：真实导出/readback 与 Desktop strict reader 已完成

- **5556 保护与新隔离环境：** `emulator-5556` 的输入焦点曾转入既有 `com.nanzhufeng.videodownloader`，因此未再对它执行 UI 或写入。只读 SDK/AVD 核对后，不克隆、不修改、不启动任何既有 AVD，而是以已存在的 Android 35 Google APIs arm64-v8a image 新建 `NanfengAiP6V2DocumentsUiAcceptance`，唯一 serial 为 `emulator-5558`。OPPO 与 `emulator-5554` 未被写入。
- **受控包与空数据：** 仅增加 `p6V2SafEmptyAcceptance` build type；新 application ID 为 `com.nanzhufeng.ai.p6v2safemptyacceptance`，显式 `P6E_ACCEPTANCE=false`，不改变 debug/release 或正式签名配置。5558 安装前该包不存在；本地 APK 与 installed `base.apk` SHA-256 同为 `b1a694044c51b1e39303e78450e4ae92d978c3ea97c8fcf1f4472877399955a0`，version `51 / 0.3.0-p10a-p6-v2-saf-empty-acceptance`。仅在这个新包自身数据中通过本机 Projects 表单创建最小非敏感 Project；没有对话、知识、记忆、关系或附件。
- **真实用户链与回读：** 正常进入 `设置 → 数据与导入 → 导入中心 → 完整工作区交换（v2）`，范围对话匿名显示 `项目 1 / 对话、知识、记忆、关系、附件各 0`。真实 `com.google.android.documentsui` CreateDocument 保存后返回 App，UI 显示“完整工作区 v2 已严格回读：d0c3df7b5e92… · 1 项对象 · 0 项附件”。DocumentsUI 写入文件仅含 `manifest.json` 与 `exchange.json`，大小 1,091 bytes；Android port 的写后 readback 已成功，未输出正文或附件 bytes。
- **内容无关交叉验证：** 导出文件 package SHA-256 为 `3601aeb36f00f94288c5df89c8c20b5f53aa0e4a2c8f65529e308fa90f205c53`，semantic hash 为 `d0c3df7b5e9296b5adf4a975077c7d7796381342942c3167cd2acc70c056d986`。root counts 为 `projects=1, conversations=0, knowledge=0, memory=0, relations=0`；owner-field namespaces 仅 `project/*`（`f46d29158ead355ddc57ff331ecf02f30ba498d62fe6c965d98b8e9375fae1f6`）与 `settings/root`（`778838e3f038975396707ec0a192eb9dbf94dd262bc5c354e746628fe4959b22`）。Node strict verifier 通过；Desktop Rust strict reader 对同一文件只读通过（1/1），不替代 Desktop native picker 的后续验收。
- **仍未关闭：** DocumentsUI 因 `application/zip` 将默认名实际保存为 `.nfai-exchange.zip`，而 Desktop native picker 当前只接纳扩展名恰为 `.nfai-exchange`；同一 bytes 的 strict reader 已通过，但该实文件不能替代未来 Desktop picker 验收，需另行决定兼容策略。Android v2 导入/archive/transaction/恢复、跨端用户导入、Desktop native picker、Windows、正式发布和 OPPO 均未开始或未完成；P6 与 P0–P11 不得据此宣称完成。macOS 解锁后，Desktop 只能从其独立设置 picker 恢复。

## 2026-08-20 P6 工作区 v2 Android SAF 导出桥接：代码/合同已接入，真实 DocumentsUI 链尚未验收

- **已实现：** Android 在 `设置 → 数据与导入` 增加已审阅范围内的“完整工作区交换（v2）”候选入口。用户先显式查看完整范围的匿名聚合计数（项目、对话、知识、记忆、关系、附件）并选择系统保存位置；`WorkspaceExchangeV2ScopePlanner` 形成 exact all-owner selection，附件一律标记 `HIGH_SENSITIVE`，不静默降级或省略 owner。`AndroidWorkspaceExchangeV2ExportPort` 只调用既有 v2 mapper/package writer：完整内存序列化与 strict preflight 成功后才写一次 SAF URI，随后 readback SHA-256 必须等于 package receipt；写入或 readback 失败请求删除未完成文档，且不显示成功回执、路径、文件名、正文或附件 bytes。
- **双端治理：** Android 与 Desktop 的 `设置 → 功能审阅` 同步为“待您判断是否保留”；建议只保留双端设置二级入口，不增加聊天、Composer 或工作页按键，且不称为备份、云同步或原生对象恢复。v1 当前文本会话 SAF 出口没有改动，v2 不接入导入、Room transaction/archive、网络、Provider、Keychain 或任何聊天按钮。
- **本地验证：** Android Studio JBR + `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` + `--no-daemon` 下，v2 mapper/writer/scope 定向 JVM 合同 4/4 与 Android 功能审阅静态合同通过；Desktop `npm run lint` 与 `npm test` 90/90 通过。未启动 emulator、DocumentsUI、ADB 或 OPPO，未产生用户 package，也未验收 SAF provider 的删除语义或真实 Android 文件 readback。
- **下一停止门：** 仅可在空隔离 AVD 通过正常 Settings → 数据与导入 → 范围 → DocumentsUI 保存一份非敏感 v2 fixture，再做 content-free receipt/readback 和 Desktop strict reader/import 的独立验证；不得以 JVM port、内部 URI、旧 v1 文件或 Desktop 结果替代，也不得操作 OPPO。Android v2 导入/持久 archive/事务/恢复仍未开始。

## 2026-08-20 P6 工作区 v2 Android package writer：本地序列化与跨端 reader 合同完成，Android 用户链继续禁止

- **已实现：** 新的未注册 `NfaiExchangeV2PackageWriter` 仅消费既有 `NfaiExchangeV2OwnerMapper` 的只读 owner→exact-IR 链；随后只对 IR 账本明确引用的附件重新只读核验 bytes/hash，在内存中生成 `manifest.json + exchange.json + assets/<sha256>`。它严格重验 IR semantic hash、canonical manifest/export equality、每个 manifest 文件 hash/长度、会话与 Knowledge 的统一 attachment 账本、content-addressed assets、未知 entry 与全部 `ownerFieldHashes`。输出被限制为一次性原子 port；preflight 失败或 mapper 拒绝时 port 不会被调用，receipt 只含 package/semantic hash、来源/敏感枚举、计数与 field hash。
- **跨端定向验证：** Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` 和 `--no-daemon` 下，mapper/writer 合同 3/3、既有 v2 IR 2/2 通过；测试生成的非敏感临时 package 被 Node `verify-v2-package.mjs` 和 Desktop Rust strict reader 先后只读接受。共享 Node v2 golden 继续通过。未运行模拟器、OPPO、SAF、文件 picker、Room/SQLite 写入、网络、Provider 或 Keychain。
- **停止门：** Android 仍没有 v2 SAF/UI、持久 package/archive、导入/事务、功能审阅新增条目或真实文件验收；不能将测试 port 或临时 fixture 称为用户导出、跨端用户互通、备份/同步或对象恢复。macOS 解锁后，Desktop native picker 的原定隔离验收仍须恢复，不能用此 Android 合同替代。

## 2026-08-20 P6 工作区 v2 Desktop native picker 隔离验收：正式开发 bundle 已重建，macOS 锁屏阻断 GUI（未伪验收）

- **已完成的安全准备：** `75c28c1` 已窄提交 picker bridge；随后为本轮加入仅验收用的 `NANFENG_AI_P6_V2_PICKER_ACCEPTANCE_ROOT`，它只接纳新建的 `/tmp/nanfeng-ai-p6-v2-picker-acceptance.*` 根，不能回退到用户 app-data 或 P6-E/P6-H 根。创建后先证明数据根为零项；无敏感 v2 fixture 与数据根分离，fixture 写入后数据根仍为零项。定向 Rust 合同 2/2、`cargo clippy --lib --tests -- -D warnings`、`cargo check` 通过；`rustfmt --check src/lib.rs` 只报告仓库既有大范围格式债务，未重排。
- **实际 bundle：** 已用离线 `npm run bundle:macos` 重建 `南枫 AI Desktop.app`，深度严格验签通过；它是 ad-hoc 开发签名（非 Developer ID/notarized），主可执行 SHA-256 为 `ddac7fe18f209170ad25df957f37d958578fdc25f7ca98e603f93e874fd327c2`。以该 bundle 主进程和新根启动后，Computer Use 读取 macOS GUI 时收到“Mac is locked，需手动解锁”的系统结果。
- **停止门：** 尚未进入设置、打开或选择 picker、导入 fixture、读取 receipt、重开/重放或回导；数据根没有业务数据，绝不以启动、命令、SQLite 或路径写入替代。Mac 解锁后只能继续同一已启动 bundle 的“设置 → 数据与导入 → 完整工作区交换（v2）”系统 picker 链，再做 content-free receipt、committed reopen/replay、re-export field/semantic hash 与 v1 三表零行核对。

## 2026-08-20 P6 工作区 v2 Desktop native picker 桥接：代码与本地合同完成，真实 picker 文件验收待隔离环境

- **已实现：** `import_desktop_workspace_exchange_v2_selected` 是唯一注册的 v2 Tauri command；Desktop Settings 的“数据与导入 → 完整工作区交换（v2）”先经 native picker 选择一个 `.nfai-exchange`，再在 Rust 中只读取该文件（类型、regular-file 与 128 MiB 限制）→ v2 strict preflight → `p6_workspace_exchange_v2` private archive/SQLite transaction。它不接受 v1 staging ID、不读写 `workspaces/workspace_exchange/import_journal`、不扫描目录，也不把路径、显示名、正文或附件 bytes 放进 receipt/错误；失败不产生可见 workspace，成功只返回 content-free hash/计数/workspace ID/replay receipt。
- **设置治理：** Desktop 入口仅位于设置二级页；Android 保持无 v2 UI/SAF 入口。双端“设置 → 功能审阅”同步登记“完整工作区交换（v2）”为待您判断，明确建议不增加聊天、Composer 或工作页常驻按键，也不称为备份、云同步或 Desktop 原生对象恢复。
- **本地验证（2026-08-20）：** 新 Rust picker-bridge 合同验证 selected fixture 的 import/replay、content-free serialization 和 v1 三表零行；Desktop Rust library 为 90/90（主动跳过会触发 macOS Keychain 的历史自测）、`cargo check` 通过；Desktop Node UI 90/90、lint、typecheck 与 static build 通过；Android Studio JBR 下 `P6DConversationRowAccessibilityContractsTest` 通过。`cargo fmt --check` 仍会报告仓库既有大范围格式差异，未对无关历史代码批量重排。
- **停止门与下一唯一候选：** 未在真实 Desktop native picker 中选择 fixture，未进行人工 readback/re-export，也没有跨端、Windows、发布、Android v2 owner/package/UI 或 OPPO 结论。若隔离 Desktop 环境可用，只能通过正常 Settings picker 选择 v2 fixture，并在成功后以真实 receipt 与 committed reopen/re-export readback 核对；不可用时如实记录，禁止 command/SQLite/文件注入伪验收。

## 2026-08-20 P6 工作区 v2 Desktop 私有导入内核：preflight/archive/SQLite/reopen/re-export 已实现，用户入口仍未开放

- **已实现：** `desktop/src-tauri/src/p6_workspace_exchange_v2.rs` 是未注册的独立 v2 内核。它只读取独立 `packageVersion: 2` / `exchangeVersion: 2` 的 `manifest.json + exchange.json + assets/<sha256>`，将 manifest/export canonical equality、完整 v2 IR semantic hash、会话 `ASSET_REF` 与 Knowledge attachments 的统一账本、每项元数据/bytes SHA-256、未知条目和 128 MiB 限制全部 fail-closed。receipt 只保存 package/semantic hash、枚举、计数和 `ownerFieldHashes`，不保存正文、路径或 bytes。
- **私有持久化与恢复：** archive 仅写入 `exchange-v2/archives/<packageHash>/`；随机 `.prepare-*`、逐文件 fsync/hash 回读和原子 rename 全在 SQLite transaction 前完成。schema 20→21 仅新增 `exchange_v2_imports/assets/owner_provenance/import_journal/import_receipts`；以 `BEGIN IMMEDIATE` 原子写入。任一写入注入失败均 rollback 为五表零行；pre-commit 无 resume，post-commit 按 journal、canonical IR、field hash 与 archive bytes 复验后幂等 replay。回导只从 committed canonical IR 和 archive bytes 写 `.part` 后 rename，并回读 semantic/全部 field hash/asset identity。
- **v1 隔离与验证：** v2 不读写 `workspaces/workspace_exchange/import_journal`，生产 migration 回归确认 schema 21 后 v1 三表仍为零。Desktop 定向内核 4/4、既有 v2 IR 与 v1 boundary 各 1/1、`cargo clippy --lib --tests -- -D warnings`、新增模块 `rustfmt --check`、Node v2 golden 均通过；`cargo test --lib -- --skip macos_keychain_self_test_uses_and_cleans_only_a_random_app_owned_entry` 为 89/89。未运行会触发 macOS Keychain 的历史自测；未调用网络、Key、picker、设备或真实用户数据。
- **停止门与下一唯一候选：** 内核刻意没有 Tauri command、native picker、UI、SAF 或“功能审阅”入口，尚不能宣称完整工作区交换的真实文件链已关闭。下一步只能先为该私有内核接入正常 Desktop picker 的最小真实文件链并独立验收；仍保持 v1 隔离，且在真实链完成前不增加用户可见入口。

## 2026-08-20 P6 工作区 v2 Desktop 原子导入合同：事务/receipt/rollback/reopen/re-export 已冻结，生产实现仍未开始

- **结论：** `P6_WORKSPACE_EXCHANGE_V2_DESKTOP_IMPORT_TRANSACTION_CONTRACT.md` 已冻结独立 v2 package、private archive、五张 SQLite owner/journal/receipt 表、单一 `BEGIN IMMEDIATE` 可见性、ownerFieldHashes、无中途 resume、commit 后重开幂等和回导 readback。v2 继续与 v1 `workspaces/workspace_exchange/import_journal` 隔离；新增 Desktop 定向回归证明 v2 IR 不能误入 v1 package writer/importer，且不会创建 workspace/journal。
- **失败关闭与数据边界：** package/IR/asset preflight、archive prepare、任意 transaction 写入和模拟中断均规定为零 SQLite 可见状态；未引用 private archive 最多是可维护孤儿，绝不成为可见半工作区。attachment 必须同时由会话/Knowledge 元数据账本、manifest 与 bytes SHA-256 证明；receipt 只存 hash/计数，不含正文、路径或 bytes。
- **停止门与下一唯一候选：** 这是合同与 v1/v2 隔离测试，不是 v2 package/staging/migration/importer 或真实文件链。下一步仅可按合同实现 v2 preflight + private archive + SQLite failure-injection；仍不得开放完整工作区 UI/SAF/功能审阅。

## 2026-08-20 P6 工作区 v2 Android owner→IR mapper：字段闭合，生产导入仍未开始

- **结论：** 新增未注册的 `NfaiExchangeV2OwnerMapper`。它只通过既有 `NfaiExchangeWorkspaceSource` 读取显式选中的 Project、Conversation、Knowledge、Memory、Relationship 与附件 owner，生成经 `NfaiExchangeV2Ir` 复验的 exact v2 JSON。Project appearance/instruction history、Conversation settings/memory sources、Knowledge source/provenance/history+附件 metadata、Memory title/source/history 与 relation scope/history 现在都不再依赖 v1/default/文件名猜测。
- **失败关闭与数据边界：** mapper 拒绝 `sourceReference`、定位符/URI/路径式 memory source、疑似凭据/诊断/route 字段、草稿/WORK/已删除/运行时节点/ToolResult、缺 owner history、跨 scope 依赖、附件 owner 不一致及内容 hash 不符。附件只在内存中只读核验 byte/hash，既不把私有 `reference` 放入 IR，也不保留 bytes、生成 package、写 Room/SQLite 或注册 UI/SAF。
- **本地验证：** Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` 与 `--no-daemon` 下，`WorkspaceExchangeV2OwnerMapperContractsTest`（2/2）和既有 `NfaiExchangeV2IrContractsTest`（2/2）通过。范围是 Android 领域/IR 合同；未运行模拟器、OPPO、SAF、文件导出、网络、Provider、Key 或数据库写入。
- **停止门与下一唯一候选：** v2 仍没有 package/asset archive、Desktop v2 staging/SQLite transaction/import journal migration、重开/回导或真实文件链。因此不得开放“完整工作区”选择、SAF 或功能审阅条目；下一步仅可在独立合同下设计 Desktop v2 staging/asset archive/原子 importer，且必须保留现有 v1 路径隔离。

## 2026-08-20 P6 工作区 v2 字段保真/拒绝矩阵：双端共享 IR 合同已闭合，生产 mapper/import 未开始（历史记录，已由上方 mapper 增量取代）

- **结论：** 已新增 `P6_WORKSPACE_EXCHANGE_V2_FIELD_FIDELITY_CONTRACT.md`，按 Android 与 Desktop 的现有 owner 对 Project、Conversation、Knowledge、Memory、Relationship、附件和 safe settings 逐字段列出 v1 状态、v2 目标、可逆性及降级/拒绝。v2 明确保留 Project appearance+instruction history、Conversation settings+memory sources、Knowledge source/provenance/history+附件 metadata、Memory 标题/来源/concept/history 与 Relationship scope/project/time/history；路径/URI、`sourceReference`、picker token、Key、Provider raw payload/runtime/diagnostic/route preference 仍一律拒绝。
- **最小协议实现与验证：** `protocol/nfai.exchange.v2.schema.json`、共享 v2 golden 与 Node canonical validator 固定 semantic hash `aaeafcfb…1c0ef8`。Android `NfaiExchangeV2Ir` 与 Desktop `validate_exchange_v2_ir` 对同一 golden 独立验证并共同覆盖 locator 和缺 owner history 的 fail-closed；Android Studio JBR 下 `:app:testDebugUnitTest --tests NfaiExchangeV2IrContractsTest --no-daemon` 通过（2/2），Desktop `cargo test v2_owner_fidelity_golden_is_shared_and_rejects_lossy_or_locator_fields --lib` 通过（1/1），Node `protocol/scripts/run-v2-golden.mjs` 通过。测试仅解析 IR，无 Room/SQLite、附件 bytes、SAF、picker、网络、Provider、Key、模拟器或 OPPO 操作。
- **尚未完成/停止门：** v2 尚无 Android owner→IR mapper、ZIP/package、Desktop v2 staging/asset archive/SQLite transaction/import journal migration、重开/回导或真实文件链；Desktop 仍不能把 v2 说成已恢复成原生 owner。故完整工作区 SAF/选择 UI 与文案继续阻止；没有新用户功能，功能审阅不新增条目。下一唯一候选是先实现 Android 只读 mapper 与完整的 v2 attachments/source/history fail-closed 合同，再接 Desktop 原子 importer；P6 与 P0–P11 全路线仍未完成。

## 2026-08-20 P6 工作区 v1 表示审计：协议兼容，但不能宣称完整对象保真

- **结论：** 现场审查确认 Android mapper 输出可进入 Desktop 的既有 `validate_exchange` → private staging → asset/package archive → 单一 SQLite transaction → provenance/`import_journal` 原子链；根对象、闭包、body hash、消息树、资产内容寻址和安全 settings 均兼容。然而 `nfai.exchange.v1` 是语义 IR，不表示 Project 颜色/图标/instruction revisions、Conversation settings/memory sources、Knowledge/Memory/Relationship 的来源与完整 history、Memory 标题/来源/concept hash、Relationship scope/project/timestamps 或 Knowledge 附件，Desktop 无法猜回这些事实。
- **修正与定向验证：** mapper 现在把 Android Knowledge owner 的 title+body lifecycle hash 转为 v1 规定的 body-byte hash，并在写包前拒绝没有 v1 安全等价的 `SUPPORTS`/`DUPLICATE_CANDIDATE`/`CONTRADICTS` relationship；Android Studio JBR 下 `:app:testDebugUnitTest --tests WorkspaceExchangeExportContractsTest --no-daemon` 通过（4/4）。本机还复验 Desktop `import_is_atomic_and_export_roundtrips_semantics`（1/1）及 `protocol/scripts/run-golden.mjs`（semantic hash `ad41c1…2d4031`）；这些是领域/协议/原子导入合同，不是 Android UI、SAF、模拟器或真实文件验收。
- **停止门与下一步：** 不接 Android 全对象选择 UI、SAF 导出或“完整工作区”文案；否则会将语义投影误称逐字段保真。先建立 v2 字段保真/拒绝矩阵及双端 schema/IR/原子导入合同，再接现有 Desktop native picker 的真实跨端文件链。现有 P6-A 单文本 SAF 出口保持不变；没有新增用户能力，故本轮不变更双端功能审阅。无 Room 写入、SAF、文件、HTTP、Key、模拟器或 OPPO 操作；P6 与 P0–P11 均未完成。

## 2026-08-20 P6 工作区对象交换 mapper：领域合同已闭合，真实全对象链未开始

- **结论：** 新增 `ExportWorkspaceExchangeUseCase` 与 `P6_WORKSPACE_EXCHANGE_MAPPER_CONTRACT.md`。它从既有 Project、Conversation、Knowledge、Memory、Relationship 与私有附件 owner 只读构造 `nfai.exchange.v1`，并复用唯一 canonical hash/gateway。选择现在显式包含 object IDs、attachment IDs 及每个附件敏感级别；没有从 Project/关系/目录隐式扩大 scope。
- **失败与恢复边界：** scope/relationship endpoint/attachment 引用必须形成完整选择闭包；草稿、WORK/已删除对话、ToolResult、缺 leaf、附件不可读或 hash 不符、未选依赖，以及当前 v1 无法表示的 Knowledge 附件均在写包前失败关闭。Desktop 既有 private staging、asset/package archive、SQLite transaction、import journal 与 package/semantic hash receipt 保持不变；本轮没有新增 Android import、SAF/UI、Room 写入、HTTP、Key、模拟器或 OPPO 操作。
- **本地验证：** `WorkspaceExchangeExportContractsTest` 3/3、既有 `ConversationExchangeExportContractsTest` 2/2 与 `P6AExchangeContractsTest` 1/1 经 Android Studio JBR、`--no-daemon` 与 `TieredStopAtLevel=1` 通过；protocol golden 保持既有 semantic/package hash；Desktop `import_is_atomic_and_export_roundtrips_semantics` 1/1 通过。Android Lint 当前 SARIF 65 条均为 `none`、0 Error/Warning；原 wrapper 调用在最终退出信息返回前超时，故这只是静态 SARIF 证据，不冒充 Gradle 完整任务成功。
- **总控边界：** 这只关闭 P6 五类对象的 v1 语义 mapper 合同；它不等于 Android domain 的完整字段保真，具体表示停止门见上方新审计。不关闭 P6 的 Android/desktop 真实完整对象导入导出、真实 UI/readback、紧凑/展开、Windows 或发布门；P0–P11 均未完成。

## 2026-08-20 P6-A Android→Desktop 真实文本交换验收：已关闭本增量

- **真实用户路径与数据边界：** 未触碰 `emulator-5554` 或 OPPO。发现 `NanfengAiP6AExchangeTemporary` 是仅 4 KiB、无 userdata/snapshot 的项目专属 API 35 AVD 后，以固定 `emulator-5556` 启动；缺失的 `devices.xml` 只阻断 `avdmanager create avd`，不阻断该已配置镜像。空机上只用正常 Composer 创建中性文本会话，再经设置 → 对话 → “导出当前文本会话到 Desktop” → DocumentsUI 输出。首次 UI 实测暴露 UUID 数字前缀被 stable-ID validator 错拒；Android 与 Desktop 已同步放宽为首字符可为小写字母或数字，仍限制长度与字符集。此前失败遗留的 0 B DocumentsUI 文件原样保留，成功包另名写入。
- **跨端结果：** Android DocumentsUI 成功生成并严格回读 1,930 B `.nfai-exchange`；Desktop 在空的 P6-E 隔离根经正常 native picker 严格预检并导入新独立工作区，再经正常导出 picker 生成 1,692 B 回导包。Android 原包 SHA-256 为 `fcf81ba84197a6c8cfe82c9d0f137869637963e2b0fc42fd9be0b4face6fb673`，Desktop 新包为 `531f8974852c66b0809906d83591a94983d5ed3cb8c2a7b4f67ada07cb13ebf4`；字节包不同是重导出预期，二者 `semanticHash` 均为 `e49f216d043843639218a973a7fc4dbe5f8c9056d418c699e53d5afb3a460562`。Desktop UI 先被已过时同 bundle 进程遮蔽为白屏；仅终止本轮空隔离根的旧进程后，重建 bundle 正常可见。另修复 `desktop-compare-execution-owner.mjs` 未被复制到 `dist` 的真实白屏根因，并以 UI 89/89 回归保护。
- **当前 APK 四方证据与自动门：** `acceptanceV2` APK 为 `app/build/outputs/apk/acceptanceV2/南枫AI-开发验收.apk`，SHA-256 `5d4d03c6e887efc3f794dc954ff753d5fd33a698b3e4ebcc0ddd60d8959fef78`，v2/v3 验签且证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`；`emulator-5556` 的 `com.nanzhufeng.ai.p6eacceptancev2` installed `base.apk` 同哈希、`dumpsys` top activity 与 UIAutomator XML package 均为该 exact package。`firstInstallTime` 在覆盖前后保持不变。Android `ConversationExchangeExportContractsTest`、`P6AExchangeContractsTest`、`lintAcceptanceV2`（SARIF 0 error）通过；Desktop stable-ID/原子导入回导合同、UI 89/89、lint、`clippy -D warnings` 与最终 bundle strict codesign 通过。没有 Key、HTTP、Provider、数据库注入、卸载、clear、OPPO 或现有模拟器数据操作。
- **总控结论：** 这只关闭 P6 的“单个活动独立文本会话 Android SAF 输出 → Desktop 导入 → 回导语义 hash”门；完整项目/Knowledge/Memory/关系/附件 mapper、紧凑/展开、Windows、发布、P2–P5/P7–P11 的其余退出门仍未关闭。

## 2026-08-20 P6-A 跨端实链尝试：安全停止，未形成验收（当前）

- **已获得的低层证据：** 当前 `acceptanceV2` 由提交 `484f169` 重新生成：`app/build/outputs/apk/acceptanceV2/南枫AI-开发验收.apk`，32,211,314 B，SHA-256 `7d32aa6483b5a59e9e1e2d03e62cc9b85464a1acafbc36d060ca8f535924eede`；v2/v3 验签通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。只对指定 `emulator-5554` 的隔离包 `com.nanzhufeng.ai.p6eacceptancev2` 以 `pm install -r --user 0` 覆盖，`firstInstallTime` 保持不变；cold-start 能进入正常 App 壳及既有设置→导入与适配页面。Android `ConversationExchangeExportContractsTest` 2/2、`P6AExchangeContractsTest` 1/1、`lintDebug`（无 Error/Fatal）通过；Desktop `import_is_atomic_and_export_roundtrips_semantics`、P6-J 回导合同、`clippy -D warnings` 与功能审阅 UI 89/89 通过。
- **安全停止原因：** 从该模拟器的正常对话导航发现其含有并非本轮创建的既有会话，故立即停止，不读取正文、不清库、不卸载、不删除或再写入其数据。此前对 Composer 的临时非敏感输入未得到可靠的持久化/回读证据，不能将其表述为已保存的导出源。为建立新 AVD 而调用 `avdmanager create avd` 亦失败：本机 API 35 `google_apis/arm64-v8a` system image 缺失 `devices.xml`；未创建 AVD、未复用其他 AVD。
- **结论与下一安全命令：** 没有 DocumentsUI 导出、没有 Android 产生的 `.nfai-exchange`、没有 Desktop 实际导入或再次导出，因此 P6 的 Android→Desktop→回导哈希门仍完全未关闭；上述只可作为构建/覆盖/自动合同证据。先修复本机可用的临时 AVD system image 或由用户提供明确空白模拟器，再以固定隔离序列号执行：正常新建非敏感文本会话 → Settings→对话→DocumentsUI 保存 → Desktop 正常 picker 导入 → Desktop 导出并严格回读 semantic/package SHA-256。不得触及 OPPO 或含既有数据的模拟器。

## 2026-08-20 总控推进：P6-A Android 文本会话 SAF 交换导出（当前）

- **结论：** 为避免继续堆叠 P6-L1–L4 的未注册复用门，本轮接入既有 P6-A 共享交换协议的 Android 输出链。`ExportConversationExchangeUseCase` 是唯一语义 mapper，只从既有 `ConversationRepository.findById` 读取当前活动 `CHAT` 的独立文本会话；它拒绝 Project、草稿、附件、工具结果、WORK/TEMP、已归档/删除或空消息。`AndroidConversationExchangeExportPort` 以私有 staging 调用 `NfaiExchangeV1Gateway`，只经用户选择的 SAF URI 输出并读回 SHA-256；`withComputedSemanticHash` 是唯一 canonical hash 路径。该入口位于设置 → 对话 → “导出当前文本会话到 Desktop”，Desktop 沿用已有 workspace exchange import，不新增聊天/Composer 常驻按键。
- **验证与严格边界：** Android 新领域合同覆盖可封包/严格预检与草稿、Project、WORK/TEMP、附件/工具结果失败关闭；既有 Android P6-A shared golden contract 同跑通过。设置入口合同与 `lintDebug` 通过（SARIF 无 error）；Desktop feature-review UI 合同 89/89 通过。Android/Desktop 设置 → 功能审阅均登记“跨端文本会话交换”。没有启动 Android app、DocumentsUI、Desktop 实际导入、HTTP、Key、安装、emulator 或 OPPO；不宣称完整工作区跨端保真、备份/同步、成本节省或 P6 退出。

## 2026-08-20 总控推进：P6-L4 双端本地消息引用有效性门（当前）

- **结论：** L4 补上 L3 的消息引用安全前置。Android `LocalExactReuseResponseReferenceVerifier` 与 Desktop `ResponseReferenceVerifier` 必须以 `scopeId + responseMessageId` 返回 `VALID`，命中才可交回复用分支；`MISSING`、`SCOPE_MISMATCH`、`UNREADABLE` 或无效 scope 一律降为 `UNKNOWN` 并进入普通 continuation。非命中不会查询消息引用。
- **验证与严格边界：** Android 与 Desktop 定向合同覆盖有效/三种无效引用和非命中不查询。L4 未读取/复制正文，不改 renderer、Conversation/Message Tree、Provider Attempt 或 Usage/Cost Ledger，未注册到 AppContainer、Tauri/UI、P2/P3 dispatch 或 Provider adapter；没有 UI/新用户功能，故不新增功能审阅登记或常驻入口。没有 Key、HTTP、安装、emulator 或 OPPO 操作，也没有真实复用、节省或 Provider cache 声明。

## 2026-08-20 总控推进：P6-L3 双端本地精确复用分派门（当前）

- **结论：** L3 在 L2 持久索引之上新增唯一 content-free 分派门。Android `LocalExactReuseDispatchOwner` 与 Desktop `local_exact_reuse_v1::dispatch` 对 `LOCAL_EXACT_HIT` 仅交出既有 `responseMessageId` 的复用分支，且不会调用普通分派 continuation；`MISS`、`INELIGIBLE`、`UNKNOWN` 只传递安全 decision。任何不带消息引用的损坏命中降级为 `UNKNOWN`。
- **验证与严格边界：** Android L3 定向合同覆盖 hit 与非 hit 互斥；Desktop Rust L3 定向合同覆盖同一分派事实。L3 不渲染/复制正文、不创建 Conversation/Message Tree、不建 Provider Attempt、不写 Usage/Cost Ledger；未注册至 Android AppContainer、Desktop Tauri/UI、P2/P3 dispatch 或 Provider adapter。没有 UI 或新用户功能，故不新增功能审阅登记/常驻入口；没有 Key、HTTP、安装、emulator 或 OPPO 操作，也没有真实复用、节省或 Provider cache 声明。

## 2026-08-20 总控推进：P6-L2 双端本地精确复用持久化边界（当前）

- **结论：** L1 的 content-free 精确键索引现在有 Android Room 37→38 与 Desktop workspace SQLite 19→20 的单独持久表。它只保存精确键安全字段、既有本地 `responseMessageId`、创建/过期/撤销状态；同一条目重放一致、重启读回、撤销和过期/撤销清理均由 Android `RoomLocalExactReuseEntryStore` / Desktop `SqliteLocalExactReuseStore` 回到 L1 owner 判定。损坏记录为 `UNKNOWN`，不猜测命中。
- **验证与严格边界：** Android `LocalExactReuseRoomStoreContractsTest`、L1 合同、37→38 迁移和完整历史迁移链通过，`lintDebug` 与 `assembleDebug` 通过；Desktop L2 Rust 重启/撤销/清理合同、受影响 P6-J workspace 重启回归与 `clippy -D warnings` 通过。此增量没有接普通聊天执行、P2/P3、renderer、Provider cache、设置 UI 或 Usage/Cost Ledger；不会产生真实复用、Provider 请求节省或缓存命中声明。没有 Key/HTTP、安装、emulator 或 OPPO 操作；没有新增用户可见功能，所以不新增功能审阅登记或常驻入口。

## 2026-08-20 总控推进：P6-L1 双端本地精确复用安全索引（当前）

- **结论：** Android `LocalExactReuseIndex` 与 Desktop `local_exact_reuse_v1::LocalExactReuseIndex` 新增同语义的 content-free 索引：只有完整相同的 scope/provider/model/endpoint/参数/tool/context/tree/attachment/template/policy/request hash，且非 TEMP、非高敏、未撤销、未过期时才返回既有 `responseMessageId` 与 `LOCAL_EXACT_HIT`。缺键/非法键为 `UNKNOWN`，其余不匹配为 `MISS`，TEMP/高敏为 `INELIGIBLE`；不保存 Prompt、回答、Key、路径、Provider event 或网络状态。
- **验证与边界：** Android `LocalExactReuseV1ContractsTest` 与功能审阅 UI 合同通过；Desktop Rust 定向 2/2、`clippy -D warnings`、Node UI 89/89、lint/typecheck 通过。双端设置 → 功能审阅均新增“本地精确复用”去留与入口建议：不加聊天/Composer 常驻按键。L1 当时尚未持久化；该缺口已由上方 L2 处理，但仍未接入普通聊天执行、renderer、Provider cache 或用量台账，不能说已经复用或节省成本；没有 Keychain、HTTP、安装、emulator 或 OPPO 操作。

## 2026-08-20 总控推进：Desktop Compare 阶段 5 mock-only adapter 与安全 receipt（当前）

- **结论：** `desktop_compare_execution_v1` 已建立为未注册的固定 OpenAI-compatible adapter seam：它只接受阶段 4 的一次性 direct-click command、已核验的 ChatGPT/Claude provider model + price catalog 与短作用域文本，并为两支写入 content-free runtime receipt。未知/空 model 或未知价格在 catalog 构造时失败关闭；空草稿、无效/过期点击也在触及 fake credential、mock HTTP 或 receipt 前拒绝。每支 receipt 仅含 execution id、logical/provider model、success/failure category、HTTP status（如有）、耗时和记录时点；测试 SQLite 仅为 `:memory:`。
- **验证与边界：** `cargo test desktop_compare_execution_v1 --lib` 为 5/5，`cargo clippy --lib -- -D warnings` 通过；随后 Desktop `npm test` 为 89/89、lint 与 typecheck 通过，三个既有入口仍由 fail-closed owner 接管。adapter 测试只用 in-memory credential、mock HTTP port 与内存 SQLite；source contract 确认无 Tauri command、真实 HTTP client 或 Security.framework/`/usr/bin/security` 调用。本模块未注册到 Desktop UI、Tauri command、真实 credential store、用户数据库、备份或同步，因此当前 UI 仍不可执行；没有 Keychain 读取/写入/枚举/重置/self-test、HTTP、安装或 OPPO 操作。

## 2026-08-20 总控推进：P5-D 隔离 SAF 导出、替换恢复与冷启动验收（当前）

- **结论：** P5-D 的当前 Schema 37 真实链已仅在 `emulator-5554` 的隔离签名包 `com.nanzhufeng.ai.p5dacceptance` 关闭：经 Settings → 数据与导入 → 本地备份与恢复 → DocumentsUI 完成 SAF 导出及回读；随后创建专用非敏感验收草稿、导入同一备份并明确选择替换本地，force-stop/cold-start 后草稿未回流，界面恢复可发送。没有合并、数据库注入、`clear data` 或对 legacy/OPPO 包的操作。
- **可追溯验证：** 前台 activity、UIAutomator package 与 installed `base.apk` 均为上述隔离 applicationId；安装的 `base.apk` 与本地 `p5dAcceptance` APK SHA-256 同为 `e07de2cc1aa4438592bf0aa83d467b3b92c3faaf1941b3d4faf537bb3aad2965`。产物为 `51 / 0.3.0-p10a-p5d-acceptance`，继续使用 release-v2 证书。构建 `:app:assembleP5dAcceptance --no-daemon` 成功。
- **边界：** 这是 emulator 隔离 app-data 的 P5-D 真实链，不等同于 OPPO、旧 APK 升级迁移、云同步、Provider 或发布验收；没有 HTTP、真实 Keychain、Key 读写或 OPPO 安装/清除/重置。

## 2026-08-20 总控推进：P5-D 当前 Schema 备份守卫（当前）

- **结论：** `AndroidLocalBackupRestoreManager` 不再把 `.nfai-backup` manifest 与预检的 Room Schema 硬编码为 17，而是读取当前已打开数据库的版本（现在为 37）。恢复在关闭 Room、替换前额外核验候选 SQLite 的 user version；manifest、候选 DB 或计数任一不匹配均整体拒绝，避免以当前包装入旧 Schema。
- **验证与边界：** `P5DLocalBackupRestoreContractsTest` 4/4 通过，覆盖 manifest 的实际 Schema 与完整 1→37 migration chain；`:app:assembleDebug` 通过。没有导出、恢复、安装、SAF/模拟器操作、Key、HTTP 或 OPPO 操作。当前 P5-D 仍缺隔离 `emulator-5554` 正常 SAF 导出、受控恢复和冷启动读回，不能由本地测试替代。

## 2026-08-20 总控推进：Desktop Compare 阶段 4 直接点击命令门（当前）

- **结论：** 三个既有 Compare 入口现在统一传入当前点击时点；仅在凭据、固定模型、价格和 composition readiness 全部满足后，owner 才会签发一个 30 秒的 content-free direct-click command。它只包含固定 provider、ChatGPT/Claude logical pair 与时效；无点击、未来/过期点击、空草稿、附件、未知模型/价格均失败关闭。当前没有 command consumer 或 transport，所以 default Desktop UI 仍只显示未执行状态。
- **验证与边界：** Desktop Node 定向 67/67 与 lint 通过；未增加 Composer 常驻按钮、Dialog、Tauri invoke、Keychain、HTTP、SQLite/receipt 或 branch 写入。下一阶段前须 context gate；真实 HTTP 仍未授权。

## 2026-08-20 总控推进：Desktop Compare 阶段 3 AI 模型服务安全投影（当前）

- **结论：** Desktop Settings 新增唯一的“AI 模型服务”二级页，且只在此页呈现 Compare 的固定逻辑 preset（ChatGPT、Claude）、OpenRouter OpenAI-compatible 身份以及 `NOT_CHECKED` / `BLOCKED` 状态。provider-facing model 和价格明确是“等待目录核验／价格未知，禁止执行”；页面无 Key 输入、显示、Keychain 探测或网络行为。Android/Desktop 的“功能审阅”同改为阶段 1/2 已完成、仍待用户去留判断的真实状态。
- **验证与边界：** Desktop Node 88/88、lint、typecheck、静态 build 通过；Android `P6DConversationRowAccessibilityContractsTest` 通过。当前 Compare 仍未配置、未联网、不可执行；没有真实 Keychain、HTTP、SQLite/备份/同步、安装或 OPPO 操作。下一阶段前须 context gate。

## 2026-08-20 总控推进：Desktop Compare 阶段 2 Security.framework 凭据边界（当前）

- **结论：** 新增未注册的 `desktop_compare_credentials_v1`：固定 Compare 的 app-owned OpenRouter service/account，凭据仅可经未来用户发起的 Settings 动作保存或在 future transport 的同一短作用域读取；macOS 实现直接调用 `security-framework 3.5.1`。Scoped read 的临时副本以 `Zeroizing` 析构清零，连 future transport 回调异常展开时也不例外。它没有 Tauri command、前端 Key 状态、SQLite/备份/同步写入、HTTP 或本轮实际 Keychain 调用。既有 P7 macOS store 也已移除 `/usr/bin/security` 和会写读删随机条目的 self-test，改为同一 Security.framework 直接 API。
- **验证与边界：** Rust 全量库测试 71/71、`cargo clippy -- -D warnings` 通过，所有 Compare 凭据测试只使用 in-memory fake；静态检索确认 `src-tauri/src` 无 `/usr/bin/security`、`security -w` 或 `macos_keychain_self_test`。本轮没有读取、写入、枚举、删除或重置真实 Keychain，没有 HTTP、app 安装或 OPPO 操作。下一阶段前须 context gate；凭据输入只可在最终 Settings UI 由用户亲自完成。

## 2026-08-20 总控推进：Desktop Compare 阶段 1 唯一状态 owner（当前）

- **结论：** Desktop Compare 已由 `DesktopCompareExecutionOwner` 统一三个既有显式入口的 readiness/拒绝决定，固定未来 OpenRouter OpenAI-compatible 边界；owner 只接收 `hasText`、附件数和安全 readiness facts，不能接受或保存草稿正文、附件、响应或 Key。空草稿、附件、无凭据存在性、未知模型/价格及未组合 transport 均明确失败关闭；当前仍没有 native credential adapter、Settings 凭据输入、HTTP、SQLite receipt 或 branch persistence。
- **验证与边界：** Desktop Node owner mock-only 4/4、chat-first UI 60/60 与 lint 通过；“设置 → 功能审阅”的 `Desktop Compare 联网执行` 已同步改为“阶段 1 仅有 fail-closed 状态 owner”。没有读取/写入 Keychain、SQLite、备份或同步，未发 HTTP、未安装 app、未触碰 OPPO。完整阶段合同见 `DESKTOP_COMPARE_EXECUTION_CONTRACT.md`；阶段 2 之前必须通过 context gate。

## 2026-08-20 总控推进：运行时交付元数据当前版本修正（当前）

- **结论：** `AndroidPrivacyDataManager`、`AndroidLocalBackupRestoreManager` 与 `RunOfflineEvalUseCase` 不再把当前 release 的 manifest/诊断/Eval report 版本硬编码为历史 `0.3.0-p5d` / `0.3.0-p4m`，统一注入 `BuildConfig.VERSION_NAME`。因此当前 `0.3.0-p10a` 生成的本地备份、诊断与离线 Eval 报告会如实携带当前版本，恢复预检和故障定位不再误指向历史交付。
- **验证：** `AppContainerRuntimeVersionContractsTest`、`P5DLocalBackupRestoreContractsTest`、`P4IOfflineEvalContractsTest` 与 Settings 定向合同通过；全量 `:app:testDebugUnitTest` 为 0 failures，`:app:lintDebug` 为 `0 errors`；`:app:assembleRelease` 通过，正式 APK SHA-256 `7406d1de5818e013227d7a1ffb4083043e0922f767d013317040bab5f2c41ea2`，v2/v3 验签通过，证书 SHA-1 `2ab70dee32bc61f0596380cd328fa673c0c86149`、SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **边界：** 该修正不导出、恢复、覆盖或读取任何用户数据；OPPO 仍保留安装的较早 v2 `fc8f9ac6…` 包，本轮不安装。当前 P5 的真实 SAF 成功导出、受控恢复与冷启动验收仍须在隔离数据环境按正常 UI 链完成。

## 2026-08-20 总控推进：Desktop Compare 外部执行取舍已进入设置审阅（当前）

- **结论：** Desktop Compare 的真实联网执行不是当前无 owner 文案能够替代的微调：它需要服务商、模型、费用、凭据安全存储、固定 endpoint transport、调用账本与分支持久化的完整方案。因此已同步登记到 Android/Desktop 的“设置 → 功能审阅”，状态为待用户判断保留，不新增 Composer 常驻按钮，建议只复用现有“对比”操作并把配置/状态放在“设置 → AI 模型服务”。
- **验证与边界：** Desktop 82/82 UI 合同与 Android `P6DConversationRowAccessibilityContractsTest` 通过；未新增 HTTP、Keychain 读取、凭据输入、模型调用、账本写入或设备安装。当前 Desktop Compare 仍如实 fail-closed，不能把审阅登记说成执行 adapter 已完成。

## 2026-08-20 总控推进：Compare 直接执行合同与矩阵校正（当前）

- **结论：** 当前 Android 与 Desktop Compare 已是三入口（模型菜单行、Composer `对比`、模型长按）的显式直接产品命令；不再存在第二次产品确认面。Android 空草稿在触及 `CompareVisibleExecutionOwner` 前返回；Desktop 没有 Compare execution adapter，三个入口均如实显示不可执行，且 handler 不创建 Dialog、不调用 Tauri command、不读 Key、不发送内容。普通本地发送与模型点按保持原语义。
- **本轮最小收口：** 新增 Android 空草稿先于 owner 调用的定向静态契约，并收紧 Desktop Node 契约为 no-owner handler 不含 `invoke` 或 Dialog 创建；`MMO4HCompareVisibleEntryContractsTest`、Desktop `npm run lint` 与 `chat-first-ui.test.mjs`（60/60）均通过。同步修正 `ANDROID_DESKTOP_USER_ENTRY_AUDIT_20260816.md`、`MASTER_PLAN_COMPLETION_AUDIT_20260816.md` 与 MM-O4-H 证据中已过时的“待确认面”描述。未改 Compose/Desktop UI、Provider、Key、HTTP、Room/SQLite、导入批次或 OPPO。
- **Desktop 正常 UI/readback：** 以最终严格验签 bundle 的 P6-E 隔离 `/tmp` 根启动，原生 Accessibility tree 实际读到 Composer 的模型菜单、`对比`与模型长按三个 Compare 入口；只点击空草稿的 `对比 ChatGPT + Claude`，页面即时显示“Desktop execution owner 不可用／未读取 Key 或发送内容”，无确认 Dialog。隔离根含历史受控验收会话，本轮未再读取或报告任何会话文本、标题或附件；未访问正式 app-data、Key、HTTP、导入批次或 OPPO。该证据只关闭 Desktop 的无 owner 可见 fail-closed 读回，不替代 Android UI、Desktop adapter 或真实执行。
- **仍待外部条件：** Android 非空草稿的真实 direct execution 必须由用户提供非敏感短文本，并另有已配置凭据和 HTTP 授权；Desktop execution adapter 尚不存在。两者均不能由空草稿、fixture、构建或历史 emulator 画面伪报关闭。

## 2026-08-20 总控推进：Android P6-K 闭环与功能审阅规则（当前）

- **Android P6-K 真实闭环：** 为避免覆盖 emulator 内 legacy `com.nanzhufeng.ai` 与 `com.nanzhufeng.ai.p6eacceptance`，已新增并使用 release-v2 签名的隔离包 `com.nanzhufeng.ai.p6eacceptancev2`。只经正常 UI 的 设置 → 数据与导入 → 导入中心 → ChatGPT / Claude ZIP → Android DocumentsUI 完成两份已授权 ZIP 导入；每份以中性临时名传输，私有暂存后立即删除共享 Download 临时源。未卸载/clear/覆盖 legacy 包，未触碰 OPPO、Key、HTTP 或导入正文。
- **匿名 readback：** 同一隔离包 force-stop/cold-start 后，设置页恢复 `CLAUDE 导入批次 1 · COMPLETED` 与 `CHATGPT 导入批次 2 · COMPLETED`。可见 aggregate 为 Claude **162** 个对话、**0** 个未关联媒体、`NO_SAFE_PROFILE_FIELDS (0)`；ChatGPT **23** 个对话、**719** 个未关联媒体、`NO_SAFE_PROFILE_FIELDS (0)`。仅 Room 聚合交叉验证显示 Claude **120** 个严格失败项（119 `EMPTY_CONTENT`、1 `INVALID_TREE`），与 Desktop 同源结果一致；未读取/输出对话正文、标题、ID、文件名或账户资料。
- **兼容修正：** Android Claude parser 现与 Desktop 同样接受缺失/`null` 的 `name` 与 `parent_message_uuid`，缺失标题规范为“未命名 Claude 对话”。此前 153 成功/129 失败的差异已消除；定向 Android 契约测试通过。
- **新增功能审阅规则：** 新增项目 `AGENTS.md` 路由到 `docs/ANDROID_DESKTOP_USER_ENTRY_AUDIT_20260816.md` 的唯一规则正文：每个面向普通用户的新功能必须同改动登记 Android 与 Desktop “设置 → 功能审阅”，显示去留状态、现有入口、是否建议新增常用界面按键及小字理由。默认只给设置二级入口。当前 Android/ Desktop 已实际登记 ZIP 导入与未关联媒体人工关联；Android 隔离包正常 Settings 路径可见该页。
- **入口文案防回退：** Desktop 的 ZIP 审阅项已与 Android 对齐为“设置 → 数据与导入 → 导入中心”，不再显示历史“第三方 ZIP 导入”路径；Desktop 全量 UI 合同 82/82 与 Android Settings 定向合同均覆盖该路径。
- **当前交付产物：** `:app:assembleRelease` 已重新通过，正式 APK `app/build/outputs/apk/release/南枫AI.apk` SHA-256 为 `7406d1de5818e013227d7a1ffb4083043e0922f767d013317040bab5f2c41ea2`；v2/v3 验签通过，仍为既有 release v2 证书（SHA-1 `2ab70dee32bc61f0596380cd328fa673c0c86149`、SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8）。本产物未安装到 OPPO 或 legacy/emulator 包。
- **当前总控结论：** P6-K Android 主链与 Desktop P6-K 均已关闭；总控方案整体仍未关闭。后续只推进矩阵中的独立欠项：用户明确选择的实包媒体人工关联、真实 Provider/账号同步/生态外部条件，以及尚未完成的双端 UI/readback。不得重复安装、不得把隔离 emulator 或 OPPO 安装扩展为全部验收。

## 2026-08-20 Android release v2 签名迁移（当前）

- **决策与边界：** 用户明确停止 legacy keystore 的恢复、猜测与 macOS Keychain 操作。历史 `nanfeng-ai-release.jks`、legacy APK 与其证据保留且不覆盖；当前南枫 AI release 构建改用独立 `nanfeng-ai-release-v2.jks` / `nanfeng-ai-release-v2` alias，不影响其他项目或全局 debug 签名。
- **实现：** `app/build.gradle.kts` 只从完整的项目专属环境变量 `NANFENG_AI_RELEASE_V2_*` 读取，或从用户级 `~/.gradle/gradle.properties` 的 `nanfengAi.releaseV2.*` 读取；部分配置或两层均缺失时立即中文失败。已移除 macOS Keychain 的读取与重试路径。`scripts/initialize_nanfeng_ai_release_v2_keystore.sh` 以交互式 `keytool` 创建 4096-bit RSA、SHA256withRSA、18,263 天的 JKS，拒绝覆盖既有 v2 文件，不读/写/打印密码。
- **当前验证：** `nanfeng-ai-release-v2.jks` 已由用户在本机交互式 `keytool` 成功创建（3,878 bytes）；两份本机脚本 `bash -n` 通过，用户级 release v2 四项配置齐全。较早 release-v2 APK `fc8f9ac604c57492cabb4b8bc74fe4284623d3a5385b50ad782f798b75252546` 已完成 v2/v3 验签（signer SHA-1 `2ab70dee32bc61f0596380cd328fa673c0c86149`、SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，公开证书有效期至 2076-08-20）。OPPO PKH120 当前只读保留该较早 v2 包；本次当前源码 APK 是 `7406d1de5818e013227d7a1ffb4083043e0922f767d013317040bab5f2c41ea2`，未安装到 OPPO。不得以旧包、Keychain 或 debug 签名替代当前源码验证；更不得为复验覆盖、卸载或清除 OPPO 数据。
- **外部绑定审计：** 工程仅发现未启用的 Google web client 配置入口；未发现 Firebase Auth / Firebase 配置、Android Google Sign-In 实现、`assetlinks.json` / `autoVerify` App Links、Play Integrity SDK 或自定义签名权限。release v2 证书生成后，仍须在真实 Google OAuth / 云端配置和发布渠道逐项复核 SHA-1 / SHA-256 白名单，不能以源码检索代替外部系统验收。

## 2026-08-20 Desktop 最终 bundle 白屏修复与 Settings 匿名回读

- **根因与修复：** 现场复核发现 `南枫 AI Desktop.app` 的 `codesign --verify --deep --strict` 实际失败，报资源封印缺失；其 Mach-O 仅有 linker ad-hoc 签名、bundle 缺少 `Contents/_CodeSignature/CodeResources`。Tauri 正常产出 `.app` 后未自动封签。已新增 `desktop/scripts/bundle-macos.mjs` 与 `npm run bundle:macos`：先重建静态前端与 Tauri app，再对唯一最终 bundle 运行 `codesign --force --deep --sign -`，并在脚本内严格验签。该脚本本轮实际完成，最终包显示 `Signature=adhoc`、`Sealed Resources version=2`、`TeamIdentifier=not set`；它仍是本地开发包，未 notarize、未发布。
- **真实可见验收：** 以隔离 acceptance 根启动，原生 Accessibility tree 已出现 `tauri://localhost` WebView、chat-first 壳层和 Settings；由此证明先前纯白窗口已恢复。随后启动同一最终 bundle 的正常 app-data 根，仅通过 Settings → 数据页读取 P6-K 匿名状态：当前为 `CLAUDE / PARTIALLY_COMPLETED`，已导入 `162`、失败 `120`、跳过 `0`、未关联媒体 `0`、个性化 `NOT_EVALUATED_NO_REGISTERED_SCHEMA (0)`。未打开会话、未读取/输出正文、标题、ID、附件名、账户资料或 Key；未打开 picker、重试、跳过或删除导入批次，也未触碰 Android、HTTP 或 OPPO。
- **质量复验：** 修正 7 处 Clippy 问题（等价附件上限比较、ZIP 返回类型别名、消息树 map 遍历、P6-J format 借用与 `TransitionResult` 的内部 `Box<Record>`），`cargo clippy -- -D warnings` 通过；68 项不依赖 Keychain 的 Rust 测试通过。完整 69 项仅 `macos_keychain_self_test_uses_and_cleans_only_a_random_app_owned_entry` 失败：它只使用随机 app-owned 临时条目、失败后仍尝试删除，失败码为 `P7-B local state rejected`，与 login Keychain 当前认证失败一致，不是业务测试回归。`cargo fmt --check` 仍因 `lib.rs` 的既有全文件紧凑排版失败；本轮未对未跟踪的数千行历史代码做机械重排。
- **最终包复核：** 上述质量修正后再次运行 `npm run bundle:macos`，重新构建、ad-hoc 封签并严格验签；新进程 Accessibility 仅作布尔校验，确认 `tauri://localhost` WebView、聊天壳层和 Settings 入口都存在。
- **后续：** Desktop 最终 bundle 白屏与 P6-K 正常 Settings 无正文回读已关闭。Android 的最新正式 APK、系统 picker ZIP 导入与冷启动仍单独受既有 Keychain 签名门禁阻断；不得以 Desktop 成功或旧 Android APK 替代。

## 2026-08-20 Android 源码门复验与正式签名门禁

- **源码验证：** 在不读取签名材料的前提下，`JAVA_HOME=Android Studio JBR` 与 `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` 下的 `:app:testDebugUnitTest :app:lintDebug` 完成：JUnit 聚合 `523` 项、`0` failures、`0` errors、`0` skipped；Lint SARIF error 数为 `0`。未执行任何 APK 打包、安装、Picker、HTTP、emulator 或 OPPO 操作。
- **当前阻断：** 同轮只读 `security show-keychain-info login.keychain-db` 返回 `SecKeychainCopySettings` 认证失败；既有 `:app:assembleDebug` 同样在正式签名 guard 前失败。这确认阻断仍是当前 macOS login Keychain 无法被本会话解锁，不是 Android 源码或 Gradle 缺口。不得尝试密码、导出/写入环境变量、替换 Keychain 条目或新建 keystore。

## 2026-08-16 P6-K Desktop 正常 Settings 无正文回读：白屏失败（当前停止点）

- **结论：** 用户已确认 macOS Keychain Access GUI 可操作，故不再把 Desktop 无正文 Settings readback 归为“等待解锁”的外部缺口。但本轮核对的唯一运行进程 `90876` 确为最终 bundle `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app/Contents/MacOS/nanfeng-ai-desktop-spike`，bundle `codesign --verify --deep --strict` 通过，窗口仍连续两次只显示原生标题栏和纯白内容区；Accessibility tree 没有 Tauri WebView、Settings 或任何交互节点。因而无法经正常 Settings → 数据与导入路径读取任何 P6-K aggregate。
- **无写入证据：** 本轮只执行 PID/path/codesign 核对与两次只读窗口树/截图；没有点击窗口、调用 Tauri command、打开 picker、重试/删除真实包，或读写 task、receipt、conversation、message、media、profile、Key、HTTP、emulator、OPPO。
- **验收状态：** 历史的 Desktop Settings 可见 readback 不能覆盖当前最终 bundle 的白屏现场；P6-K Desktop 正常 UI/readback 重新打开，详见 `P6K_DESKTOP_SETTINGS_READBACK_FAILURE_MATRIX_20260816.md`。下一步必须先在不触及导入数据的边界内诊断并恢复该最终 bundle 的 WebView/启动可见性，再从正常 Settings 路径重新读回匿名 aggregate。

## 2026-08-16 直接执行规则与受控签名复测（当前）

- **产品规则已落地：** Android 与 Desktop 的 ChatGPT/Claude/知识库 JSON 选择、P6-K ZIP、普通本地发送、Compare 和 Desktop 交换包选择均不再出现产品级二次确认；入口—owner—安全保留矩阵见 `DIRECT_ACTION_ENTRY_MATRIX_20260816.md`。既定聊天/抽屉/Composer 布局、颜色、控件关系未改。Android Compare 保持 scoped grant、draft scope、附件拒绝、receipt/dispatch owner；Desktop 尚无 Compare execution owner 时直接显示真实不可执行状态，不读 Key、不发内容。
- **非盲删边界：** P3-J normal external-send authority 没有 `ConversationWorkspace` 的实际调用点；P8 Agent authority 也不是本轮可见产品 confirmation，均保留。Markdown/PDF/版本化 Knowledge 编辑性导入和禁止 HTTP 的 Web snapshot 已列为审计边界，未在本轮擅自扩展。
- **定向验证：** Android P6-H/P6-I/P6-J direct-import 与 Compare 合同通过；Desktop UI 合同 **80/80**、lint/typecheck/static build 通过。未 install、未生成可安装 APK、未操作 emulator/OPPO、未读 Key、未发 HTTP、未用 DB 造验收。
- **GUI 后受控签名复测：** 用户已在 Keychain Access 打开目标既有条目并见“允许所有应用”，但 GUI 显示密码同样报“无法用提供的密码解锁该钥匙串或仅允许特定用户账户”，未出现输入框。随后全新 `--no-daemon :app:assembleDebug` 仍在项目正式签名 guard 前停止，且无交互式密码/解锁路径。此为 macOS Keychain 解锁/账户认证层的精确复现；未读取、打印、导出、猜测或写入任一秘密，未使用任何其他项目条目。

## 2026-08-16 P6-K Android 真实 ZIP 验收：签名门禁阻断（当前停止点）

- **本轮无绕过复验：** 既有正式签名链再次执行 `:app:assembleDebug`，仍在配置阶段要求仓库外 keystore 与 macOS 钥匙串口令的非交互读取。未读取、打印、导出、输入、创建或替换秘密；没有生成 APK、安装、picker、HTTP、OPPO、卸载、clear 或数据库注入。Desktop P6-K Rust **7/7**、Desktop 全量 Rust 库 **68/68**（过滤唯一会触达 macOS Keychain 的既有自测）、Desktop UI 合同 **80/80**、lint/typecheck/static build 均通过。全总控需求—证据矩阵见 `MASTER_PLAN_COMPLETION_AUDIT_20260816.md`。
- **签名根因诊断（2026-08-16）：** 仓库外 JKS 路径存在；目标 Keychain 条目可通过无值 metadata lookup 精确定位，但同一条目的秘密读取和 login Keychain settings 读取均被 macOS 认证策略拒绝。故阻塞点在当前 Keychain 解锁/ACL 认证状态，不在 JKS 路径、Gradle `providers.exec`、alias 或 taskGraph 门禁。现有 Gradle 已固定从该唯一条目读取、只将口令保留于当前构建内存；不能以明文属性、临时 export、替换 Keychain 条目或新密钥“修复”。在系统恢复该既有条目的非交互读取前，任何新 Gradle daemon 同样会拒绝签名构建。

- **授权范围与未执行项：** 用户已授权 emulator-5554 的真实 ChatGPT / Claude ZIP 经 Android Settings 系统 picker 直接导入、`install -r` 覆盖、force-stop/cold-start 与无正文回读；明确不允许读取 Key/HTTP，且不得 clear、卸载、操作 OPPO 或强制 host 重启。本轮未运行 picker、未复制 ZIP 到 emulator、未安装 APK、未导入或删除任一 task/archive/receipt。
- **只读现场证据：** emulator 为 `device`；既有 `com.nanzhufeng.ai` 为 versionCode 51 / versionName 0.3.0-p10a，installed `base.apk` SHA-256 已记录于本轮终端证据。Room 当前有 3 个旧 P6-K task（ChatGPT 与 Claude 各 1 个 `AWAITING_CONFIRMATION`、另 1 个 `NOT_ZIP`），0 import receipt、0 provenance、719 `UNMAPPED_REJECTED` asset candidate、3 个 `NOT_EVALUATED_NO_REGISTERED_SCHEMA / 0` profile candidate；尚无 K8 asset-link receipt 表。数据全程只按聚合查询，未输出正文、ID、附件名、profile 值或 archive path。
- **精确阻断：** `JAVA_HOME=… ./gradlew --no-daemon :app:assembleDebug` 在编译前被项目的正式签名 guard 拒绝：需要仓库外 keystore 与 macOS 钥匙串口令，或 `NANFENG_AI_KEYSTORE` / `NANFENG_AI_KEYSTORE_PASSWORD`。当前用户禁止触碰 Key，故不能安全构建“最新 APK”，也不能用时效未知的旧 artifact 代替；未尝试读取、设置或绕过任何签名值。
- **唯一下一步：** 仅在用户提供一个不违反“不可读 Key”边界的、已完成签名的当前 APK，或明确授权受控签名操作后，按原顺序执行 `install -r`→base.apk hash equality→Settings picker 逐包直接导入→移除临时 picker 源→force-stop/cold-start→dumpsys/UIAutomator/package/hash 三方一致→无正文 aggregate/receipt readback。当前旧 task/archive/receipt 全部保留，不能用 DB 注入或清库替代。
- **受控签名授权复验（2026-08-16）：** 用户已授权使用既有正式签名链，但仍禁止读取/打印/导出/改写/新建 keystore、keychain 或签名环境变量，也不得输入密码。再次直接运行 `JAVA_HOME=… ./gradlew --no-daemon :app:assembleDebug` 仍在配置阶段被同一 guard 拒绝；没有交互式密码或解锁提示，未触碰任何签名材料。按“失败即停止”未执行 install、emulator、UI、临时副本、picker 或真实 ZIP，全部 task/archive/receipt 保持原状。下一步只能由用户预先恢复既有签名链可用性，不能由本任务规避。

## 2026-08-16 P6-K Android 最终源码验证（签名授权前）

- **最小修正：** 源码审计发现 Settings 的“查看导入结果”和人工关联 target 曾渲染 `item.candidate.title` / `target.conversationTitle`。这违背“Settings 不显示内容”的既定口径；现均改为匿名“对话 N”，仍只显示角色、消息序号、MIME、大小、状态与可重试回执。没有改 importer、Room owner、聊天 renderer 或 normal chat/Compare。
- **owner / 入口审计：** `OpenDocument` ZIP picker → `P6KZipImportViewModel.selected` → `AndroidP6KZipIntakeStore.stage` 是唯一入口；选中即严格 inventory→私有 staging→profile allowlist owner（无可采纳字段时 `NO_SAFE_PROFILE_FIELDS / 0`）→`ManageP6KChatGptZipImportUseCase.importAll`，没有产品级确认。未关联媒体只能由用户显式 asset+已提交 target message 经 `RoomP6KZipManualAssetLinkOwner` 写既有 attachment owner；batch cancel 先撤销 conversation/asset/profile owner，任一步失败保留 task/archive 以重试。P6-K 未改 normal submit 或 Compare owner。
- **验证结果：** `:app:testDebugUnitTest` 的全部 7 个 P6-K 定向类通过，并实际运行 `kspDebugKotlin`、`compileDebugKotlin` 与 unit-test Kotlin compile；未触发正式签名门禁。覆盖 strict inventory、36→37 migration（仅结构）、direct commit/Claude limit、profile owner、PNG/MP4/PDF manual link→reopen→renderer、duplicate/failure isolation/retry/revoke、匿名 Settings UI。没有模拟器、UI、真实 ZIP 或签名材料操作。
- **待验证：** 签名授权后的唯一工作仅剩 latest signed APK 的 `install -r`、真实 picker 直接导入与 force-stop/cold-start readback；任何签名门禁仍必须停止，不能绕过。

## 2026-08-16 产品行为纠正：本地数据与联网能力并列，用户操作直接执行

- 用户已明确撤销“本地优先/独立本地工作区/产品级二次确认”方向。选择导入或执行后直接运行；安全继续由权限、凭据隔离、严格 parser、最小外发、原子性、回执/幂等、错误可见和 Agent authority 管理，不能误写为离线隔离或无安全约束。
- P6-K2 改为 ChatGPT ZIP 严格 candidate 自动逐项提交；Claude/profile/未关联媒体仍不导入。真实 ZIP 本轮只读格式审计，未写 App 数据。Compare/普通执行的旧确认 UI 与 application owner 是受影响债务，需在不改既定布局下改为直接执行状态，不能继续称为产品授权门。

## 2026-08-16 P6-K3/K4 Desktop 离线完成点（模拟器/OPPO 全部暂停）

- Desktop K0 审计结论：Settings→数据的原 ZIP 入口只做 SQLite 16 inventory；真实会话/消息/搜索/树的唯一 owner 是 `workspace_exchange.exchange_json` 与既有 renderer/index。现 Schema **16→17** 只追加 P6-K task/item/message/asset/profile/provenance/receipt journal，绝不创建第二套 Conversation/Message schema。
- Desktop Settings 的 ChatGPT / Claude ZIP 现为“选择即直接导入”：native picker→private archive→2 GiB/4 GiB/256 MiB bounded ZIP→版本化 v2 strict parser→逐会话 transaction 写入现有 TEXT message tree→receipt/provenance/reindex。ChatGPT 缺旧 `children` 时仅按同 mapping parent edge 重建；Claude 仅唯一缺失 local parent 可规范为 root。失败项保留 error、可 retry/skip；批次删除软删除该批 conversation 并移除 receipt/provenance/private archive。
- 媒体没有稳定 message association 时只记录 `UNMAPPED_REJECTED` 统计，不复制、无附件/预览；profile 固定 `NOT_EVALUATED_NO_REGISTERED_SCHEMA / 0`，不读取原文、不写个性化。未触碰 Key/HTTP/OPPO/emulator，未用任一真实 ZIP 写入 Desktop DB 或输出其内容。
- 自动证据：Rust `cargo test p6k --lib` **3/0**（ChatGPT 缺 children、Claude unique missing root、private/restart/idempotent/skip/soft-delete/unmapped media），全 Rust library **65/65**、`cargo check`、`cargo build` 通过；Desktop lint/typecheck、P6-K UI 合同 **59/59** 与 static build 通过。尚未启动 Desktop native window；下一离线验证只能使用现有合成 fixture 做正常 picker/完整退出重开/消息树文本渲染的可见验收。真实 Desktop ZIP 仍待用户授权的正常 picker 验收。
- **K3/K4 Desktop native 可见验收停止点（2026-08-16）：** 已在源码工作区（排除构建产物）与 `/tmp` 检索 `.zip`，未发现可由系统 picker 选择的既有 P6-K 合成 ZIP；当前 Rust P6-K 合同仅在测试运行时临时生成归档，不能冒充正常 picker fixture。本续接未启动 Desktop、未打开 picker、未创建 fixture、未读两份真实 ZIP，也未读/写 Key 或 HTTP。待提供或显式允许纳入一个版本化、无害的现有 fixture 后，才执行独立 acceptance root 的“选择→完整退出重开→会话列表/消息树文本”可见验收；在此之前不得把静态/Rust 通过写成 native UI 闭环。
- **K3/K4 Desktop 实包授权后的执行阻断（2026-08-16）：** 用户随后明确授权将两份指定实包直接经正常 Desktop ZIP picker 导入、无需确认。执行前的 `npm run lint`、`npm run typecheck`、`npm run build`、`cargo test p6k --lib`（3/3）与 `cargo tauri build --bundles app` 均通过；全量 Node UI 为 78/80，两个既有 FB-P6-041/026 CSS 断言失败，非 P6-K 导入链。正常 `com.nanzhufeng.ai.desktop` 包已启动，但本机 Computer Use 对 Tauri WebView 的坐标点击两次均返回 `AXError.notImplemented`，因而未能进入设置、未打开系统 picker，也未调用 ZIP stage/commit 命令。两份归档仅做文件大小与 SHA-256 核验，未枚举或输出内容；未写入 app-private P6-K task/receipt/conversation/message，未读写聊天正文、ID、附件名、账户资料、Key、HTTP、emulator 或 OPPO。不得用非正常 picker 注入替代本授权链；待恢复本机 Desktop UI 自动化能力或由用户完成 picker 选择后，再从同一正常窗口继续直接导入、重启回读与无正文截图。
- **K3/K4 Desktop 实包正常 picker 执行与重开回读（2026-08-16，当前）：** 先发现白屏来自同 Bundle ID 的旧二进制进程，而非 WebView/assets；只优雅关闭已核对的旧 PID 后启动最终严格签名包，正常设置页与 ZIP picker 可见。实际 native 路径另补齐 P6-K Tauri capability（单独、最小的 `allow-p6k-zip-import`），并将跨候选父消息改为安全 item-level 拒绝、继续其余候选；解析失败后立即删除未登记 private archive。两份用户已授权 ZIP 均只经系统 picker 选择：ChatGPT 建立一份可删除 `PARTIALLY_COMPLETED / CANDIDATE_REJECTED` 私有批次（517 个候选安全拒绝；0 Conversation、0 Message、0 receipt；719 `UNMAPPED_REJECTED`；profile `NOT_EVALUATED_NO_REGISTERED_SCHEMA / 0`）；严格合同不允许把跨候选父引用猜成 root。Claude 在严格会话数量边界前拒绝，未建立 task/receipt/业务写入；其唯一未跟踪 private archive 已按 task 引用核对后删除。最终 bundle 通过 `cargo test p6k --lib` 3/3、lint/typecheck/static build、`cargo tauri build --bundles app` 与 strict codesign；完整退出重开后正确 `com.nanzhufeng.ai.desktop` 设置页回读上述 ChatGPT 批次，截图无正文。未打印/保存聊天正文、标题、外部 ID、附件名、账户资料或 Key；未触碰 HTTP、emulator、OPPO。当前下一步不是放宽真实数据边界，而是用户决定是否为 ChatGPT 跨候选 parent 或 Claude 会话数量扩容提供版本化合同依据；保留 ChatGPT 批次可由现有“删除导入批次”软删除。
- **K5 授权实包兼容修复与实际提交（2026-08-16，当前最高事实）：** 用户明确把两份实包作为格式证据并授权继续，不以合同选择为停止点。Desktop P6-K 新增单 ZIP 全局 ChatGPT 图归一化：跨 numbered-entry/candidate parent 仅在 source node/message ID 全局唯一、parent 可唯一解析、无环、可达且 parent 时间不晚于 child 时合并为稳定 graph source；仅结构节点的 parent 会折叠到最近可导入文本祖先；合并片段标为 `SKIPPED`，不可解析项单独 `FAILED`，其余继续。旧的全失败 ChatGPT private task 由正常“重试”在 app-private archive 内重解析并提交 **23 Conversation / 90 Message / 23 receipt**，仍有 **494** 严格失败项、**719** `UNMAPPED_REJECTED`、profile `NOT_EVALUATED_NO_REGISTERED_SCHEMA / 0`。Claude 实包仅作安全结构统计：archive **11,251,218 bytes**，根会话数组 **282**、JSON **38,402,217 bytes**；P6-K 专属版本化边界为 **64 MiB / 1,000 conversations / 2,000 messages-per-conversation / 2,000 candidates / 100,000 messages**，不改变 P6-H/I 的旧限制。经正常 ZIP picker 重选后 Claude 直接提交 **162 Conversation / 880 Message / 162 receipt**，**120** 严格失败项；不导入媒体/profile。合成 Rust 覆盖跨文件 parent 合并、不可解析隔离、幂等、batch soft-delete/reopen 与 282 Claude fixture，`cargo test p6k --lib` **5/5**；lint/typecheck/static build、正式 bundle 与 strict codesign 均通过。完整 `cargo test --lib` 为 **66/67**，唯一既有 `sync_state_v1` macOS Keychain self-test 因 `P7-B local state rejected` 失败，不重复运行且未读取任何 Key 值。导入后已优雅退出重开正确 bundle，SQLite receipt/aggregate 回读稳定；最后的正常设置页无正文截图被 macOS 锁屏阻断，待用户解锁后仅补该可见 readback。未触碰 OPPO、emulator、HTTP；未输出正文、外部 ID、附件名或账户资料。

## 2026-08-16 P6-K7 实包 message↔asset 只读审计完成（当前停止点）

- **结论：** 没有可采纳的稳定 message↔asset relation，现有资产全数保持 `UNMAPPED_REJECTED`；不改 Android/Desktop 附件 owner、Preview、数据库 schema、消息 UI 或已导入会话。
- **两包匿名只读证据：** ChatGPT：733 safe entries、14 JSON、719 non-JSON assets、7,629 parsed mapping messages；message scope 内 exact ZIP-entry-path refs **0**、exact asset SHA-256 refs **0**。全包可见的资产 identity 只在 export manifest/logical-file catalog，不能成为 message owner。Claude：4 safe JSON entries、0 non-JSON assets，故不存在 asset pair。没有输出或写入正文、外部 ID、附件名、路径、账户资料或 Key。
- **规则已固化：** 仅“provider 已解析 message scope 中的完整 entry 或 SHA-256 direct identity、可重解析复验且不依赖文件名”的 pair 才能进入下一个 adapter contract；当前没有满足项。详细方法与安全统计见 `docs/P6K_MEDIA_RELATION_READONLY_AUDIT_20260816.md`。
- **验证/边界：** 本轮只运行 private staging 只读审计与现有 task aggregate readback；未重试 package、未写 profile、未复制资产、未触碰 OPPO/emulator/HTTP/Key。下一步不是接附件，而是等待新的 versioned provider message↔asset schema 证据；若再开启，先写 parser-first mapper→asset-owner→receipt/revoke 合成合同。

## 2026-08-16 P6-K9 全量验收审计（当前停止点；未启动 UI/未操作设备）

| 用户验收要求 | 唯一 owner / 正常入口 | 当前证据 | 验证层级 | 仍缺什么 |
|---|---|---|---|---|
| ChatGPT / Claude ZIP 选择即导入 | Android `AndroidP6KZipIntakeStore.stage`；Desktop `stage_p6k_zip_import_selected` | Desktop 已以正常系统 picker 导入 ChatGPT **23 / 90 / 23 receipts** 与 Claude **162 / 880 / 162 receipts**，完整退出重开后安全 aggregate/receipt 回读；Android strict parser/Room 合同 | Desktop 真实文件 + 重开；Android 自动化 | Android 正常 picker、重启和可见 readback，须 emulator/host 恢复 |
| 文本会话与安全排版 | 既有 Conversation + Message Tree / Desktop `workspace_exchange` renderer | 两份 Desktop 实包已写既有 TEXT tree 且数据库重开；P6-K 不建第二 render path | Desktop 数据/重开；Android parser/Room | macOS 解锁后不含正文的正常 UI readback；Android UI |
| 图片、视频、PDF 原位附件与人工精确关联 | `PrivateAttachmentStore` / Desktop attachment owner；K8 target-message receipt | 合成 PNG/MP4/PDF 已覆盖 target attachment block、replay/reopen、既有 image/video/PDF projection、duplicate/conflict/retry/failure isolation/batch revoke | 双端合成 owner→renderer 链 | 实包仍无自动 relation；只能由用户在 Settings 明确选未关联媒体及目标 message 后再重开验证 |
| 基础资料与个性化白名单 | `RoomP6KProfilePersonalizationSettingsOwner` / Desktop profile owner | 仅七项 canonical 白名单；实包 `NO_SAFE_PROFILE_FIELDS / 0`，无值写入；owner replay/revoke 合同 | 实包安全拒绝 + 自动化 | 无可采纳实包字段；Android UI readback |
| Settings、状态、回执与撤销 | P6-K task/receipt/provenance owner | Desktop 正常 Settings 已回读批次/回执/aggregate；Android Settings 现只显示匿名“导入批次”，不泄漏 ZIP 名。K9 令 Android 仅在 conversation、asset、profile、archive/journal 全部撤销后删 task；失败保留 task/private archive 并显示重试提示 | Desktop 真实回读；Android source/Room/UI 合同 | Android 正常 UI/reopen；锁屏解除后补 Desktop 无正文截图 |
| Desktop / Android 语义对等 | 同一 strict manifest、task、receipt、owner/revoke contract | 两端均 direct stage、幂等、冲突关闭、失败隔离、重开与 batch revoke；schema 分别 36→37、18→19 | 自动化/代码审计 | Android 设备级正常入口 |
| 本地数据与联网能力并列、无产品级二次确认 | 既有 normal chat / Compare owner；P6-K direct stage | ZIP 选择后直接执行；P6-K 未改 Provider/HTTP/Key 或 egress；普通发送仍为自动本地持久化，Compare 保持既有明确状态 | 源码 + Desktop Node 合同 | 真实已配置 Provider 的联网验收不在本轮授权范围 |
| Compare/normal chat 不回退、已确认 UI 保护 | 既有 chat shell 与 renderer | P6-K 未改会话/消息 renderer；K9 恢复底部侧栏层级保护，发送 glyph 保持既定 16px（0.8），并校正过时的 20px 测试断言 | Desktop Node 全量回归 | macOS 解锁后的可见 readback；本轮未启动 UI |

- **本轮安全修正：** `P6KZipImportUi` 不再把 `task.displayName`（所选 ZIP 文件名）放进 Settings；Android `cancel` 先完整撤销 owner 与私有归档/journal，最后才删除 task，任一失败保留可恢复任务并给出重试回执。没有改变真实包的 `UNMAPPED_REJECTED` 结论，也没有从文件名猜 owner。
- **本轮验证：** Android 7 个 P6-K 定向类通过；Desktop `cargo test p6k --lib` **7/7**；Desktop Node **80/80**、`npm run lint`、`npm run typecheck` 均通过。此前 K8 的 Room 36→37 migration 仍仅是表结构证据，不在此冒充媒体 renderer 闭环。
- **只能等待的外部条件：** (1) macOS 解锁后，用已导入 Desktop bundle 补无正文 Settings/readback；(2) emulator 恢复或 host 强制重启后，执行 Android 正常 picker→重启→readback；(3) 用户在 Settings 明确指定实包未关联媒体及目标消息，才可验证真实 PNG/MP4/PDF 人工链。不得以 DB 注入、文件名推测或非正常 picker 代替。

## 2026-08-16 P6-K8 未关联媒体人工精确关联完成（当前停止点）

- **范围与结论：** K7 的实包拒绝结论不变；K8 不是自动推断。Android 与 Desktop 在 Settings 增加必要的 metadata-only 操作入口：用户显式选一个未关联媒体和一条同 ZIP 已导入 message 后，立即精确关联，不再二次确认。文件名、正文、外部 ID、archive path 与媒体内容均不在 Settings 输出；聊天既有布局和 renderer 未改。
- **唯一 owner / 迁移：** Android Room **36→37** 增加 asset link receipt/provenance 并由 `RoomP6KZipManualAssetLinkOwner` 与既有 `PrivateAttachmentStore`、Conversation tree owner 串接；Desktop SQLite **18→19** 增加 `p6k_zip_asset_link_receipts` 并写既有 `desktop_attachment_assets`、`desktop_conversation_attachments`、`workspace_exchange`。二者均严格检查候选 entry path、size、SHA-256、magic MIME、同批 provenance、真实 target message、单资产归属、幂等 replay/冲突/失败隔离；只有成功后提取一项到 private attachment owner。Android batch delete 现要求 conversation、asset receipt 与 profile revoke 全部成功才删除 task/archive；任一步失败保留 recovery entry，而非留下不可撤销会话。
- **验证：** Android 新增 `P6KZipManualAssetLinkRoomContractsTest`：合成 ZIP 的 PNG/MP4/PDF → 指定 imported message attachment block → replay/reopen → receipt → batch delete，并从该 block 调用既有 image/video/PDF projection；还覆盖失败候选可重试且不影响其余候选、批次 revoke 失败保留 task。`P6KZipImportRoomContractsTest` 继续通过，但其中 36→37 migration 只证明表结构与旧候选保留，不作为媒体闭环结论；既有 P6F2 image/video/PDF domain/UI preview contracts 亦通过。Desktop `cargo test --lib -- --skip sync_state_v1::tests::macos_keychain_self_test_uses_and_cleans_only_a_random_app_owned_entry` 为 **68 passed / 1 filtered**，仅跳过既有 P7-B macOS Keychain 环境自测，未读取 Key 值；`node --check desktop/src/app.mjs` 与 `desktop/src/chat-shell.mjs` 通过。未操作 UI（macOS 锁屏）、OPPO、emulator 或 HTTP。
- **真实包状态：** 两份实包均未执行 K8 人工动作，因而没有新媒体提取或展示，继续保持 `UNMAPPED_REJECTED`；不能因为 K8 功能存在而重试写 profile 或从文件名猜归属。下一步仅在用户实际明确选择关联目标时做正常 Settings 操作与重开回读；否则保持现状。

## 2026-08-16 P6-K6 profile / personalization owner 已完成（历史停止点）

- 用户要求将第三方账户基础显示资料和个性化作为原生兼容能力，同时严格排除登录态、邮箱/电话、支付、安全、token/cookie/device/session 与无法证明语义的字段。本轮不操作 Desktop 窗口、emulator、OPPO、HTTP、Key，也不对实包写入。
- 已对两份授权实包执行一次**只读且无值输出**的结构分类：ChatGPT 2 个候选资料 JSON、Claude 1 个；两包均未出现可证明的显示名/语言/时区/公开简介/自定义指令/主题/通知白名单类别，ChatGPT 候选还命中敏感类别。因此真实包本轮的正确结果是 `NO_SAFE_PROFILE_FIELDS / 0`，不是猜测映射。
- 新增 Android `ThirdPartyProfilePersonalization` 与 schema `nfai.third-party-profile-personalization/v1`：仅允许显示名、语言、时区、公开简介、自定义指令、主题和通知开关，严格 size/type/unknown-key 拒绝；Desktop Rust 使用同名的精确 sidecar envelope。两端 P6-K task 只持久化状态与字段计数；`MAPPED_PENDING_OWNER_COMMIT` 仅在 mapper→owner 的短暂内存交接存在，正常回读为 `OWNER_COMMITTED`、`NO_SAFE_PROFILE_FIELDS`、`REJECTED_UNSAFE_PROFILE_SCHEMA` 或冲突状态，Settings 不显示值。
- **已完成 owner 闭环：** Android Room **35→36** 的 `RoomP6KProfilePersonalizationSettingsOwner` 与 Desktop SQLite **17→18** 的 `p6k_profile_personalization_settings` 分别是唯一 native profile owner。它们只存 canonical 白名单值，不存原 profile/外部 ID/账户或敏感字段；同一 owner transaction 写 profile candidate 状态、receipt/provenance、幂等重放或 conflict，P6-K 批次删除会撤销当前由该 task 写入的 profile 并清除该批 audit。没有新账户、同步或 Provider/Key 设置系统。
- 定向证据：Desktop `cargo test p6k --lib` **6/6**（新增 fixture 的 mapper→owner→read→retry/reopen→revoke）；Android `P6KProfilePersonalizationOwnerRoomContractsTest` 与 `P6KZipImportRoomContractsTest` 通过，另 `ThirdPartyProfilePersonalizationContractsTest` 通过。未运行 emulator/OPPO/HTTP/Key，不对两份真实 ZIP 重试或写 profile；它们保持 `NO_SAFE_PROFILE_FIELDS / 0`。当时下一候选是 K7 的只读 message↔asset 审计；其拒绝结论已记录在本文件顶部，仍不可从文件名猜附件。

## 2026-08-16 P6-K1 ChatGPT ZIP 严格候选映射与 Room 恢复已实现（未确认、未写业务数据）

- 依据最新 OpenAI 官方说明：个人数据导出为含聊天及账户数据的 ZIP；OpenAI 的 Edu / 会话迁移说明明确 ZIP 可有根 `conversations.json`（大包还可能是编号 JSON）与会话资产。K1 只登记**第一条**可验证变体：根 `conversations.json` 存在，且其字节仍严格通过既有 P6-H `ChatGptExportJsonAdapter` 合同。编号 JSON、其他 JSON、profile/personalization、未知资产关联和全部 Claude ZIP 继续 fail-closed，Claude 为 `WAITING_FORMAT_EVIDENCE / UNKNOWN_MANIFEST_SCHEMA`。
- Android Schema **33→34** 新增仅 P6-K 使用的 `p6k_zip_import_tasks/items/messages/asset_candidates/profile_candidates`；`RoomP6KZipImportTaskRepository` 是候选恢复唯一持久化 owner。K0 的 app-private properties journal 在 IO 路径一次迁为该队列，保留 archive 但不伪造候选，随后删除旧 journal。Room 不含外部 URI/path/picker token，也没有 Conversation、MessageNode、Attachment、Provider、Key 或设置写入。
- `P6KChatGptZipCandidateMapper` 只在 private ZIP 上逐 entry 流式读取：`conversations.json` 产生 ChatGPT conversation/message candidate IR；媒体仅产生 entry path、SHA-256、size、MIME、`UNMAPPED_REJECTED` 及空 source association 候选，绝不从文件名推断归属、复制导出或写业务资产。profile candidate 固定 `NOT_EVALUATED_NO_REGISTERED_SCHEMA / 0`，不读取 profile 原文。
- Settings 仍为既有 ZIP 预检入口；ChatGPT 安全状态现在可显示“待确认候选”，但尚无确认/跳过写入动作，故正常历史、搜索、预览和资料均不变。ViewModel 的 legacy migration、Room list/write 全在 IO，避免 Activity 初始化或 UI 主线程 Room 访问。
- 自动：`P6KThirdPartyZipInventoryContractsTest`（安全 ZIP、zip-slip/bomb、严格 ChatGPT root mapping、Claude/非严格拒绝）与 `P6KZipImportRoomContractsTest`（33→34 保留旧表）通过；`assembleDebug` 成功。未读取用户 ZIP、未调用 picker、未运行 emulator/OPPO、未读 Key/HTTP、未写真实 Conversation/Attachment/Profile；K1 仍不是 K2 确认事务或真实导入闭环。
- **下一安全前置：** 用户提供脱敏 ChatGPT ZIP/manifest，才可验证实际 entry shape、媒体 ID ownership 和确认面；Claude 需要官方版本化 ZIP 文件表或脱敏 manifest 后才可实现。不能用 ChatGPT 格式或非官方样例替代 Claude 证据。

## 2026-08-16 P6-K ChatGPT / Claude 官方 ZIP 导入：0 号审计完成（最高优先级；未实现 ZIP 导入）

- 用户新增最高优先级：用户明确选择的 ChatGPT/Claude 数据导出 ZIP 未来须导入真实 `Conversation + Message Tree`；附件以私有副本和既有原位预览呈现；profile/personalization 只能成为兼容且独立确认的本地候选，绝不覆盖 Key、Provider、账号或设置。
- 本轮已审计 P6-H ChatGPT、P6-I Claude、P6-J 知识库 JSON Adapter 与 P3-A/G/E Message Tree、私有附件、Preview owner，以及 Android/Desktop JSON-only picker/task/receipt 差异。结论：三者均只支持“用户已解压的 JSON”，没有 ZIP manifest、资产归属、富文本 IR 或 profile mapping；P3/P6-F2 资产/预览 owner 可复用，但不能令 JSON 表推定支持 ZIP。
- 官方依据：OpenAI 说明个人数据导出为含聊天及账户数据的 ZIP，Edu 文档进一步列出 `conversations.json` 或编号会话 JSON、资产和账户/会话元数据；Anthropic 只确认导出含 conversation/user data，尚无可据以实现的 Claude ZIP 文件表或 schema。因此当前 registry 没有可接受版本；`UNKNOWN_VERSION` 必须在任何解压/任务/UI确认前拒绝。
- 新增权威合同 `P6K_CHATGPT_CLAUDE_ZIP_IMPORT_ADOPTION_CONTRACT.md`，并同步总控蓝图、多供应商计划、architecture governance、decision log、feedback ledger（FB-P6-092）。合同含入口矩阵、K0–K5 有界 ZIP intake/manifest/媒体归属/非破坏确认事务/消息附件与预览/profile边界/双端恢复路线。
- 最小 Parser-first 回归仅锁定旧 JSON 的拒绝边界：Android `P6HChatGptExportJsonContractsTest` **4/0**、`P6IClaudeExportJsonContractsTest` **6/0**；Desktop Rust `json_only_chatgpt_and_claude_entrypoints_reject_zip_before_any_private_copy` **1/0**。未创建或读取用户 ZIP、未调用 picker、未解压、未读 Key/HTTP、未触碰 OPPO，也未提供 ZIP UI 入口。
- **下一步仅限 K0：** 取得官方/用户明确选择包的可审计版本证据后，先实现 Android/Desktop 一致的 metadata-only format registry 与 envelope rejection 合同；不得直接通用解压、接入 profile 或开始真实导入。

## 2026-08-16 P6-K0 ZIP intake / private copy / inventory 已实现（仍未导入内容）

- Android 新增 `ThirdPartyZipInventoryPolicy`、`AndroidP6KZipIntakeStore` 与 Settings → 数据导入中的“导入 ChatGPT ZIP / 导入 Claude ZIP”。系统 picker 仅请求 ZIP MIME；输入流只复制到 app-private `p6k-zip-import/v1`，再读取 central-directory metadata。任务只持久化 hash、显示名、安全 entry 名/大小/MIME 与 status；既没有 Room Conversation 写入，也没有附件/profile/Key/Provider 路径。
- Desktop SQLite 15→16 新增独立 P6-K task/entry metadata 表，Tauri native ZIP picker 复用同样 private-copy/inventory 边界；任务可在重开后读取，清除副本会删 private archive 与 task metadata。两端安全拒绝 path traversal、重复 entry、archive/entry/total 解压上限、压缩炸弹/未知 compression、symlink 与非 ZIP；未知 manifest 固定 `WAITING_FORMAT_EVIDENCE / UNKNOWN_MANIFEST_SCHEMA`。
- 验证：Android `P6KThirdPartyZipInventoryContractsTest` 通过且 `assembleDebug` 成功；Desktop Rust P6-K 2 tests/0 failures、`cargo check` 通过；Desktop lint/build 与 `chat-first-ui.test.mjs` 59/0 通过。无 emulator/OPPO/真实 ZIP 读取、无 Conversation/Attachment/Profile/import commit。
- **K1 唯一前置：** 用户提供一份脱敏 ZIP，或可公开且版本化的 ChatGPT/Claude manifest/schema；先登记 provider+version+manifest evidence，之后才实现 JSON/媒体 ownership 与 mapping。当前普通用户可看到并使用“ZIP 预检”，但它明确不声称“实际导入已可用”。

## 2026-08-16 MM-O4-H Compare 可见入口实现（部分 emulator 视觉已验收，确认面与真实服务未验收）

- 用户后续授权已覆盖最初最小范围：Android 与 Desktop 正常聊天新增模型菜单 `对比 ChatGPT + Claude`、Composer 附近 `对比`，既有模型控件普通点按仍开模型菜单、长按 Compare。Drawer、消息区、普通发送、普通 Auto 默认、既有按钮本身和既定视觉语言不改。
- 已新增 `CompareVisibleExecutionOwner`，只从显式 Compare UI 入口申请既有 MM-O4 汇总确认；确认面为已有根层 Dialog 层级，披露 ChatGPT/Claude 第三方收件人、text-only、2 次调用、保守总预算、默认未勾选/一次性/五分钟。确认后才本地提交所选草稿为 user parent、持久化双 branch session，并一次 batch 接到既有 O4-G adapter；未确认时不读 Key、不发 HTTP。目录、草稿、附件、会话、Key 或 dispatch 任一不满足即 fail-closed。
- Android assemble、定向 Compare/普通发送/附件拒绝合同、Desktop lint/build 与 57 项 chat-shell 测试通过。Debug APK（SHA-256 `c453ab…f17969`）已只安装到 `emulator-5554`，同视口关闭态、普通模型菜单和重启读回已取证：旧候选未挤压，新 menu 行与旧行同为 144px。实体 OPPO 未操作，未读 Key、未发 HTTP。空草稿下已真实点按 Compare menu 行和长按模型按钮，均安全不弹确认；为不自行构造文本，尚不能验证两入口进入同一确认 Dialog、取消/5 分钟/附件拒绝的现场交互。详见 `MM_O4H_COMPARE_VISIBLE_ENTRY_IMPLEMENTATION_EVIDENCE.md`。
- **下一前置：** 等用户给出一条短、非敏感文本后，仅用该文本分别验证两个入口进入同一确认面（不按确认、不读 Key、不发 HTTP）；完成才进入用户已授权的全量入口缺口审计和分批补齐。

## 2026-08-16 多供应商模型编排优先级恢复点

- **新最高优先级：** 用户提供的《南枫AI_多供应商模型编排架构_v1.0》已纳入总控；权威采纳、冲突适配、分期与问题清单见 `MULTI_PROVIDER_MODEL_ORCHESTRATION_ADOPTION_PLAN.md`。
- **MM-O1 首批领域核心已完成：** 新增纯领域 `MultiModelOrchestration.kt` 与合同测试，固定 Direct／Compare／Auto 同级、Router 仅 Auto 可调用、Compare 同一 Canonical Context Snapshot、默认无综合和仅显式采用、无静默 fallback。Compare MVP 的上限现为确认的 **2**；第三目标必须被拒绝。`MultiModelOrchestrationContractsTest` 为定向 JVM 合同的一部分。该批未读 Key、未发 HTTP、未写数据库、未修改 UI，也未修改下方 P3-J WIP 文件。
- **MM-O2 首个 Registry Adapter 已完成：** 新增纯领域 `MultiProviderModelRegistry.kt` 与 `MultiProviderModelRegistryContractsTest`，将 Logical Model／Deployment／Provider／provider-facing model ID 显式分离；OpenRouter 是首个且唯一 `OPENROUTER_OPENAI_COMPATIBLE` Adapter 身份。目录升级、单个稳定上版回滚、未知价格／失效部署 fail-closed、历史实际 Provider／模型 ID／部署 ID／目录版本／价格版本 readback，以及确认的 ChatGPT／Claude 两逻辑模型 Compare 首组均由合同覆盖；不接线既有 Transport、P6-G Router、Runtime、Usage Ledger、Conversation Tree、UI、Room 或 P3-J WIP。与 MM-O1、P6-G Router 一起为 **16 tests / 0 failures / 0 errors / 0 skipped**，Gradle `BUILD SUCCESSFUL`；详见 `MM_O2_MULTI_PROVIDER_REGISTRY_EVIDENCE.md`。这不是 Key、HTTP、真实 Provider、UI、设备或发布闭环。
- **MM-O3-A2 Direct 生产前安全债务已完成（非真实闭环）：** `ConservativeInputBillingBudget` 成为 Direct confirmation 与 P3 reservation 的唯一纯领域保守输入计费上界：使用 UTF-8 字节上界，不把它或任何本地值冒充 Provider 实际 token。`DirectExecutionConfirmation.scopeFingerprint` 以 SHA-256 绑定 request id、execution/context、完整部署与目录/价格/币种、文本 SHA-256、输出上限和最大预算，确认、日志及结果均不含原文。取消、过期、范围变化、确认和 preflight 终态立即从有界 `issued` map 释放原始请求；仅保留有 TTL/容量上限的 content-free replay 拒绝事实。Registry 的未知价格明确为 `PRICE_UNAVAILABLE`。本轮不修改 UI、P3-J WIP、AppContainer、Key/HTTP/Room/Usage、设备、OPPO 或发布；定向 JVM/Debug 编译证据见 `MM_O3A_DIRECT_APPLICATION_BOUNDARY_EVIDENCE.md`。普通聊天 UI 仍未接通 Direct，未证明真实 Provider/HTTP/Usage/重启/视觉/设备闭环。
- **MM-O3-B Production Composition Readiness 已完成（非真实闭环）：** 新增只读 `OpenRouterVerifiedMultiProviderRegistryProjection`：仅投影当前 `OPENROUTER_CATALOG + VERIFIED + catalog SHA-256 + 非 fallback` 的 `VersionedModelRegistry` 快照；ChatGPT/Claude 只由显式 preset 映射形成逻辑身份，Provider-facing model ID、pricing/price version、catalog version 均逐字段来自该已验证快照。P3-C `LOCAL_FIXTURE`、P6-G `curated:*`、展示名和 UI 不参与投影；未验证、无 hash、fallback、无映射及未知价格均 fail-closed。`AppContainer` 只惰性注册 Direct owner/preflight/projection，构造不读 Key/settings、不发 HTTP、不调用 Auto、不写 Runtime/Usage/Room，也不传给 Activity/ViewModel/Workspace。**本轮未读取 app-private 目录，故未断言现场是否已有可投影 snapshot；若当时不存在，Direct 正确 readiness-rejected。**下一唯一缺口是既有 P2-J 目录验证路径产生可投影的真实 verified snapshot。证据见 `MM_O3B_PRODUCTION_COMPOSITION_READINESS_EVIDENCE.md`。
- **MM-O4-A Compare 纯 domain/application 核心已完成（非真实闭环）：** 新增未注册的唯一 `CompareExecutionApplicationOwner` 及 8 项合同。它先调用 `MultiModelOrchestrator.Compare`（Auto 零调用），固定只接受 ChatGPT + Claude 两个不同逻辑模型和不同 deployment、同一 Canonical Context/文本输入，逐支经 Registry 精确解析实际 Provider/模型/目录/价格。共享 `ConservativeInputBillingBudget` 计算两个 UTF-8 保守上限，只在同币种下安全求和；附件、无目录/未验证投影、未知价格、第三目标、重复、非首组、跨币种和溢出全部失败关闭。它只产生一张默认未勾选、5 分钟、一次消费的汇总确认，绑定 request/context/text hash、两收件人和各支/总预算；确认后只给两个 content-free、稳定 branch grants，默认不综合/不采用、不调用 Direct 第二确认、无 transport/Key/Room/Usage/UI/AppContainer。取消、过期、确认、目录变化或单目标替换立即释放原文并使整体失效，只留 TTL/容量受限 replay facts。定向回归 **74 tests / 0 failures / 0 errors / 0 skipped**，`assembleDebug` 成功；证据见 `MM_O4A_COMPARE_APPLICATION_CORE_EVIDENCE.md`。真实 Direct 仍受 verified OpenRouter snapshot 外部门限制，且本轮没有后台网络、app-private 读取、fixture/UI 猜测或任何真实 Compare egress。
- **MM-O4-B Compare Session/Conversation Tree 纯领域接合已完成（非真实闭环）：** 新增唯一未注册 `CompareConversationSessionOwner` 及 7 项合同；MM-O4-A grants 额外以 content-free text SHA-256、expiry 与 one-shot identity 绑定。owner 只读取调用方提供的 `ConversationSnapshot` 并用既有 `MessageTree` 验证同一 conversation、当前 user parent、同一 Canonical Context 与两个 grants；它不复制或修改 MessageNode、current leaf，也不建平行树。两支分别预留 assistant/invocation/attempt/cancel identity 与状态；成功、失败、取消互不回滚。成功分支的 follow-up plan 显式排除 sibling；adopt 只可选择一个成功支且保留另一支；默认 synthesis 不存在，显式 synthesis 要两成功支、新 context 及默认未确认的独立 consent/budget gate，并产生第三新 invocation/branch plan。重放同 intent replay，冲突/二次/过期拒绝。审计确认现有 P3 Runtime/Room 每 conversation 只支持一个 current runtime，且要求 current leaf，故本轮正确只产出纯 IR/未来 adapter plan，不改 Schema/DAO、Runtime、UI/AppContainer、Key/HTTP/Usage/设备/发布。回归 **91 tests / 0 failures / 0 errors / 0 skipped**，`assembleDebug` 成功；证据见 `MM_O4B_COMPARE_CONVERSATION_TREE_EVIDENCE.md`。真实 Direct/Compare 仍受 verified OpenRouter snapshot 外部门限制，未使用后台网络、app-private 读取、fixture/UI 猜测绕过。
- **MM-O4-C Compare 持久化与 Runtime 基座已完成（非真实闭环）：** Schema **31→32** 新增唯一 `CompareConversationSessionStore` / `RoomCompareConversationSessionStore`，只追加 Compare session、双 branch identity、独立 runtime/checkpoint、append-only runtime event 及 terminal/follow-up/adopt/synthesis typed intent 事实。每 branch 独立持有 assistant/invocation/attempt/future execution/cancel/reservation reference；它们不是 P3-I receipt 或 Usage 事实。现有 P3 `conversation_runtime_states` 对 conversation 的唯一索引与 current-leaf runtime 合同未改；Compare 不硬复用 P3 runtime、不插入无内容 reserved `message_nodes`、不改变 `Conversation.currentLeafMessageId` 或建第二棵树。session/terminal/follow-up/adopt/synthesis 可重启读回；同 intent replay、冲突 fail-closed；sibling success/failure/cancel 独立，follow-up 排除 sibling、adopt 保留 sibling、synthesis 仍为新 invocation/new context/new gate。`AppContainer` 仅追加生产 Room builder 的 `MIGRATION_31_32` 注册，没有注册 Compare Store/执行器或 UI。Schema migration 与相关回归 **20 tests / 0 failures / 0 errors / 0 skipped**，`assembleDebug` 成功；证据见 `MM_O4C_COMPARE_PERSISTENCE_RUNTIME_EVIDENCE.md`。未读 Key、未发 HTTP、未生成消息正文、未写 P3-I/Usage、未改 UI/Auto 默认、未操作设备或发布。
- **MM-O4-D Compare 临时内容租约与双分支接合已完成（非真实闭环）：** 生命周期审计确认旧 `confirm()` 会在返回 content-free grants 前释放原文，因而 hash 不可恢复内容。现 `CompareExecutionApplicationOwner` 是唯一 application/dispatch owner：确认后仅它保留 process-memory-only lease，并在同一临界区向 `CompareBatchDispatchPort` 交出同一 Canonical Context、两个 grants 与一份 one-shot lease；端口只能全量 accept 或整体 reject，默认 fail-closed。取消、过期、范围变化、Store 拒绝、port 拒绝/异常及 accept 后均释放原文；旧 intent 不重放、不重复交付。Schema **32** 不升级，`CompareConversationSessionStore` 复用已有 append-only branch runtime event 记录两支一致的 dispatch intent/accepted/rejected-retry-required 事实并 restart readback；任何非 accepted restart 状态无原文，必须 fresh text + fresh confirmation，绝不 hash 恢复或静默重发。没有预插 assistant、没有改 `currentLeaf`/第二树/P3 runtime/P3-I receipt/Usage。定向 **14 tests**、扩展 O4/Room **42 tests** 均为零失败，`assembleDebug` 成功；证据见 `MM_O4D_COMPARE_TRANSIENT_LEASE_DISPATCH_EVIDENCE.md`。未接 Provider、Key、HTTP、真实 transport、UI、设备或发布。
- **MM-O4-E OpenRouter Compare 双分支执行 Adapter Readiness 已完成（非真实服务闭环）：** 唯一链路为 owner 从 Schema 32 session readback 注入双 branch execution/invocation/attempt/cancel/reservation identity → `OpenRouterCompareBatchDispatchAdapter` → 同一 `RealTextExecutionCoordinator` → 既有 `OpenAiCompatibleProviderTransport`。adapter 一次 take lease 并在内存构造两条 request；实际 provider-facing model ID逐支取 grant，限定两个 openrouter verified ChatGPT+Claude deployment，Auto/Router 零调用，legacy `OpenRouterInferenceAdapter` 未使用。旧 `ReadyPlan.presetId` 与动态 Compare deployment 冲突已通过 mutually-exclusive content-free `verifiedCompareDeployment` target 解决，旧 P3 preflight/settings/current-leaf 语义不改。默认 opaque disabled credential、disabled HTTP、disabled receipt/Usage fail-closed；没有 Key load/decrypt、真实 HTTP、AppContainer/UI 注册、production Room test-port 或假 receipt/Usage。batch accept 只表示两支由一个 coordinator 接管，partial terminal 独立且不回滚 sibling；Schema/树/current leaf 不变。相关 JVM/Room **51 tests / 0 failures / 0 errors / 0 skipped**，`assembleDebug` 成功；证据见 `MM_O4E_OPENROUTER_COMPARE_ADAPTER_READINESS_EVIDENCE.md`。真实服务仍须当次可见确认、用户授权资料、production receipt/Usage 事务和受审查平台 credential/HTTP adapter。
- **MM-O4-F Compare production receipt/Usage Room 边界已完成（仍非真实服务闭环）：** Schema **32→33** 仅追加 `compare_branch_execution_receipts`。新的未注册 `RoomCompareBranchExecutionPorts` 是 Compare 专属 `RealTextExecutionRuntimeReceiptPort`／`RealTextExecutionUsageReservationPort`：每一次 reserve/start/partial/finish/release 都先以 Schema 32 branch 的 execution、invocation、attempt、请求指纹、会话、父 user 节点和 provider-facing model 绑定校验，再原子追加 receipt 或既有 immutable Usage Ledger 预留／释放事实。它不复用或注册 `RoomRealTextExecutionCoordinatorTestPortAdapter`，不写 `message_nodes`、`Conversation.currentLeafMessageId`、P3 `conversation_runtime_states`，不保存 delta、原文、模型响应或 Key；两个分支可独立留存 receipt 与账本。`AppContainer` 只注册 migration，未构造 Store/ports/adapter、未启用 HTTP 或 Key 读取。新 Room 契约 **3 tests / 0 failures**；连同 O4-A/C/E 与 coordinator 回归共 **24 tests / 0 failures / 0 errors / 0 skipped**，`assembleDebug` 成功；证据见 `MM_O4F_COMPARE_RECEIPT_USAGE_ROOM_EVIDENCE.md`。下一唯一缺口是受审查的 Android credential/HTTP platform adapter；该工作不得注册到 UI 或触发真实请求，真实 egress 仍须当次可见确认及用户明确授权资料。
- **MM-O4-G Android Compare credential/HTTP platform boundary 已完成（仍非真实服务闭环）：** 新的未注册 `AndroidOpenRouterComparePlatformAdapter` 只从 Compare owner 在未过期、已确认后签发的 ChatGPT+Claude 双 branch grants 创建；构造不读取 Key、不打开连接。它以同一对象作为 opaque credential handle 与 `OpenAiCompatibleHttpClient`，固定 OpenRouter HTTPS POST、受限请求头、8s/30s timeout 与 1 MiB response 上限。只有 grant 中精确的 provider-facing model 可在每批一次性出站；取消、过期、handle/模型错配、重复模型、缺凭据或异常均在连接前/安全结果中失败关闭，临时 `CharArray` 在 finally 清零。`OpenAiCompatibleProviderTransport` 新增安全 `CredentialUnavailable`／`ResponseTooLarge` outcome 映射；没有 UI／`AppContainer`／Activity／ViewModel 引用，也没有 Key read、HTTP、Room 写入或真实 Compare。4 个新合同加 O4-E/O4-F/transport 回归为 **17 tests / 0 failures / 0 errors / 0 skipped**，`assembleDebug` 成功；证据见 `MM_O4G_ANDROID_COMPARE_PLATFORM_BOUNDARY_EVIDENCE.md`。**现场确认普通聊天确认面仍为 `EGRESS_UNREGISTERED`／`isConfirmable=false`，Compare 更无已注册 UI，故没有合法 app-visible consent；不得进行 ChatGPT+Claude 实测。** 下一唯一候选是审查并接合不改布局的 Compare 可见确认/执行公开入口，随后才可在用户授权的非敏感文本和明确次数/预算下做少量实测。
- **MM-O4-H Compare 可见确认/执行入口审查：BLOCKED（无代码改动）：** Chronicle 当前帧为无关应用，故未作为南枫 AI 视觉证据；改以项目同视口 Composer 基线和当前 Compose/冻结合同审查。现有根层 `NormalChatExplicitEgressConfirmationDialog` 虽是不会影响 transcript/drawer/composer 测量的白色 Dialog sibling，但只承载 `EGRESS_UNREGISTERED`、`isConfirmable=false` 的普通聊天占位，确认按钮无执行回调。关闭态 Composer 只保留现有发送键和 64dp model entry；其锚定菜单严格为“自动 + 当前 P6-G 候选”。普通发送被静态合同固定为本地提交，且 `P3JNormalChatExplicitEgressContractsTest` 明确要求不触发外发确认。把 Compare 放入模型菜单会改变浮层高度；重用发送键、模型 entry 或普通确认槽位会改变已确认控件语义/Auto 默认，均违反 UI 冻结和本次停止条件。故没有可复用的显式 Compare trigger，未改 UI/Owner/Room/AppContainer，未做构建/视觉 QA/实测，未读 Key/未发 HTTP/未触碰 OPPO。静态守卫 **4 tests / 0 failures / 0 errors / 0 skipped**；证据见 `MM_O4H_COMPARE_VISIBLE_ENTRY_GATE_EVIDENCE.md`。继续前需要用户明确指定一个允许承载 Compare 的既有槽位，或明确授权新增一个不影响关闭态的触发入口及其打开态视觉合同。
- **线程卫生停止点：** MM-O3-A 结束后 `context_gate.py` 返回 **HANDOFF**（context 71.1%、effective tokens 248,014）；交接卡为 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-15T23-55-11-01a00622-6094-7612-b15f-795d7e2f2716.md`。后续必须切换任务后再开始下一阶段；HANDOFF 不是产品测试失败，也不授权继续接线。
- **既有底层全部保留：** Provider Transport、OpenAI-compatible core、Model Registry、P6-G Router、Conversation Tree、P3 Runtime/Receipt、Usage Ledger 均作为增量复用基座，不做批量回退。
- **UI 冻结：** 原方案中的多列、Tabs、Provider 设置页只采纳语义，不直接复制布局。人工确认过的 Drawer、Composer、消息区、按钮位置/尺寸/颜色/层级/表面继续不可改；Android 与 Desktop 同样适用。
- **UI 新入口决策（2026-08-16，覆盖此前“最小 UI”误读）：** Compare 或其他新功能可按既有设计系统自主新增入口、按钮和交互；当前明确授权：模型菜单行 `对比 ChatGPT + Claude`、Composer 附近 `对比` 入口、普通聊天既有模型控件“点按仍开模型菜单／长按进入 Compare”。这不授权改动已确认的显性按钮本身、既定设计方向、核心布局关系或既有视觉语言；若后续需求会触及这些冻结对象，必须停止并取得新授权。Drawer、消息区、普通发送和普通 Auto 默认继续不动。
- **P3-J WIP 隔离：** 下方暂停点所列 7 个未验证文件继续保持未完成状态；MM-O1 不修改、不接线、不借其声称真实外发完成。
- **三项产品参数已确认：** `Compare > Direct > Auto` 仅作研发／架构优先级，普通对话未指定模型时继续默认 Auto；Provider 采用统一核心、OpenRouter 首部署、原生 Adapter 一个一个验收；Compare MVP 默认且最多比较 ChatGPT 与 Claude 两个逻辑模型。实际 Provider-facing 模型 ID 只在 Deployment Registry 解析，不写死到合同或 UI。
- **续接任务：** `01a00622-6094-7612-b15f-795d7e2f2716`（南枫AI 23 - 多模型编排续接）。MM-O3-A 已在纯应用边界停止；下一增量须单独授权，不得把未注册 Direct owner 接到普通聊天 UI、增加真实 Provider/Key/HTTP/Room/Usage/重启路径，或改普通对话 Auto 默认、Compare、Desktop/Android 冻结 UI、OPPO 或发布。

## 2026-08-15 暂停点（用户要求先收尾）

- **当前状态：暂停，不再继续实现。** 用户要求先整理进度，以备后续续接；本节优先于下方历史“下一项”文字。
- **最近已验证完成：** P2-M v2 已新增独立 RunSpec／fixture／nonce／Consent 指纹，保留 v1 历史只读；P2-L 9 项、P2-M bridge 3 项、transcript 3 项及 P2-K 单次失败不重试合同通过。正式签名 Debug 已在短 ASCII 路径构建并对 `emulator-5554` 执行保留数据 `install -r`；本地 APK 与设备 `base.apk` SHA-256 均为 `2c043329…a03153f95`，证书仍为 `889ecf3f…803e99d5`。设置页安全状态为“尚未配置”，因此没有进入确认页、没有读取／修改 Key、没有消费 v2 nonce、没有 HTTP Attempt 或费用。未操作 OPPO／真机。
- **P3 证据审计：** `P3_EXECUTION_EVIDENCE_MATRIX_20260815.md` 已区分 P2-M 受控 fail-closed bridge、普通聊天未接通、test-only Room adapter 与真实服务证据缺口；9 个 JVM／Room suite 共 **38 tests / 0 failures / 0 errors / 0 skipped**。`P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md` 与静态守卫 **2/2** 通过。
- **当前未验证 WIP（不得写成完成）：** `NormalChatRealTextExecutionOwner.kt`、`ConversationFoundationViewModel.kt`、`ConversationWorkspace.kt`、`AppContainer.kt`、`NanfengAiActivity.kt`、`P3JNormalChatExplicitEgressContractsTest.kt`、`NormalChatRealTextExecutionOwnerTest.kt` 已被最新 P3-J 任务写入，但用户要求暂停后未运行测试、编译、安装或模拟器视觉验证。实现意图是把确认面作为根层 `Dialog` sibling，不改 Composer／Drawer／消息区几何；当前源码仍让 `submitCurrentDraft()` 执行本地提交，同时打开默认不可确认的内存模态，这一交互是否正确必须先审查，不能直接保留或继续扩展。
- **UI 冻结继续生效：** 人工确认过的 Drawer、Composer、消息区、按钮位置／尺寸／颜色／层级／表面不得改。P3-J 只声称“源码意图未改几何”，没有同视口截图或 Compose／模拟器证据，故视觉门仍未通过。
- **恢复时的唯一顺序：** ① 先审查上述 7 个 WIP 文件及“本地发送 + 不可确认模态”行为；② 决定保留、修正或撤销该未验证增量，但不得批量回退既有 P3/P2-M 底层；③ 跑 owner 单测、P3-J 静态合同、相关 ViewModel／UI 回归与 Debug 编译；④ 在同一模拟器视口只读比较关闭态 Drawer／Composer／消息列表几何，再验证模态的取消、默认未勾选、5 分钟过期与附件拒绝；⑤ 只有用户通过现有模型设置保存非占位 OpenRouter Key 后，才可经既有可见确认执行一次 v2（0 retry、USD 0.01 上限），不得注入 Key 或重复确认。
- **续接任务参考：** P3-J 暂停线程 `01a0060a-3497-73c3-a800-c623621f9972`；P2-M v2 准备 `01a005f0-ace8-78c3-8cd1-9fd8937a42d7`；模拟器受控真测前置 `01a005f6-f69a-7fa2-bc22-febfea1da08c`；P3 证据审计 `01a005fc-cd21-7ae2-8a34-7c4b8e0c8467`。

## P3 当前执行证据指针（2026-08-15，docs-only）

- 当前源码/定向合同审计见 `P3_EXECUTION_EVIDENCE_MATRIX_20260815.md`：P3 domain、P3-I Room receipt 与 test ports 已实现，普通聊天到真实 Provider 的生产链路仍未接通。P2-M 仅有可见确认后的受控 bridge，注入默认拒绝 coordinator 且结果被 legacy executor 忽略；它不是真实 Provider/HTTP/Key/Room runtime/Usage 成功证据。
- 本轮 9 个定向 JVM/Room suite 为 **38 tests / 0 failures / 0 errors / 0 skipped**；未做构建、安装、模拟器/OPPO/UI、credential readback 或真实 HTTP。界面“未配置”不推导 Key 是否保存或有效。
- **P3-J 普通聊天外发合同（docs + 纯静态守卫，未接线）：** `P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md` 冻结未来唯一 `NormalChatRealTextExecutionOwner`、根 overlay 的不改布局确认槽位、服务商/模型/文本类别/费用披露、未勾选一次确认、text-only 附件强排除、取消/过期/重复/失败/恢复与安全调用记录。`P3JNormalChatExplicitEgressContractsTest` **2/2** 通过。当前 `submitCurrentDraft()` 仍仅本地提交；没有该 owner、AppContainer/VM/Composer/Provider/HTTP/Key 生产注册，不得宣称已接通或真实调用。

## P6-J 南枫知识库全部 JSON Adapter（2026-08-15，CLOSED，非 OPPO）

- **已完成且验证通过：** Android/ Desktop 严格 `IntelligenceRecord[] → sourceText.chat_messages` parser、逐记录 `NOT_CONVERSATION_RECORD`、app-private 原始 JSON 副本、独立 task/item/message/provenance/receipt、Android Schema **28→29**／Desktop SQLite **14→15**、确认同事务、read-latest 与受限 retry。两端现在都会丢弃 `sender=tool`、thinking 和 tool 内容块，合成 fixture/定向 parser 测试已通过。Desktop Rust P6-J 隔离测试覆盖 private copy、重开、错误 workspace 回滚、确认幂等、Skip 与严格交换包 semantic/package hash readback。
- **本轮双端真实 UI 门：** Desktop 临时独立 bundle（`com.nanzhufeng.ai.desktop.p6jacceptance`、ad-hoc 签名、`/tmp` acceptance root）已经 native Open panel 选择合成 JSON → Alpha 确认 → 第二有效会话 Skip → 完整进程重开 → 原生 Save panel 导出，SHA-256 `281be57728dd34016170b511a0b30986a34586f67543fe9e874cc5b3b7338b81`；中途发现并修复仅 P6-J command capability 漏列，未扩大权限。Android `emulator-5554` 已经 DocumentsUI 选择同一 fixture → Alpha 确认 → 第二有效会话 Skip → force-stop/cold-start readback → **设置 → 对话 → 导出当前对话**严格读回，成功包 SHA-256 `126b9b350bec5fecf1c7a6d7e5991638422352ed5b058958455b730b765e5923`，manifest/payload 只含 Alpha。完整命令、失败诊断和读回文本见 `P6J_NANFENG_KNOWLEDGE_EXPORT_ADAPTER_EVIDENCE.md`。
- **关闭判断：** 合同的自动门与双端真实 UI 门已全部满足，故 P6-J 正确标记为 **CLOSED**；不因总计划仍有后续工作而保留 active。该关闭不扩展到 Key、Provider、HTTP、Agent/tool、同步、OPPO、发布或真实知识库数据。
- **硬边界：** 只接收知识库运行版“全部 JSON”的 `IntelligenceRecord[]`；不读取知识库数据库、附件、路径或真实数据；不触碰 Key、Provider、HTTP、Agent、同步、OPPO、发布。P6-J 的表/存储键/provenance 与 Claude/ChatGPT 完全独立。
- **下一明确未完成 increment（仅定位，尚未开始）：** `FB-P6-050` 双端对话位置导航真实验收；详见 `PRODUCT_FEEDBACK_DECISION_LEDGER.md`。其后才是 `FB-P6-051`。暂停的 `FB-P6-044` 仍禁止 Key/HTTP/Provider，不是下一步。

## 当前权威状态（2026-08-14）

- **2026-08-15 P6-I Claude export JSON Adapter 已完成（非 OPPO）：** 已完成 strict parser／领域任务机、Android app-private `claude-export-import-assets/v1` 与 Room **27→28**、Desktop SQLite **13→14** 的独立 task/item/message/provenance/receipt 迁移，且各自以系统 picker、Alpha 确认／Skip 跳过、完整重开与导出严格读回复核。Desktop 隔离 root 的最新功能窗口与 Android `emulator-5554` 均读回普通历史的 Alpha USER/ASSISTANT 文本及“从 Claude 导入 · 本地静态文本”；Desktop 导出 semantic hash `da4a1d2e…`、文件 SHA-256 `3f8ccf1c…a6cd`，Android current-path export SHA-256 `80dc623e…e471`、设备 `base.apk` SHA-256 `4ce329b1…a1b`。完整证据见 `P6I_CLAUDE_EXPORT_JSON_ADAPTER_EVIDENCE.md`。**下一 Adapter 必须由 context gate 后单独进入南枫知识库对话／知识 export；仍不得前跳 Provider、Key、HTTP、同步、Agent、OPPO 或发布。**

- **2026-08-15 全软件复查后的门禁修复：** 已将 4 个失效的源码位置／旧文案测试更新为现行共享组件的行为契约；新增 Schema 18→27 连续升级保留测试、折叠屏真实 Compose 容器尺寸契约，以及媒体保存 API／MP4 标志位／视频点击无障碍契约。对话抽屉、内屏轨道及长按 Popup 不再读取整块 Display 的 `screenWidthDp/screenHeightDp`，而是读取 `LocalWindowInfo.current.containerSize`；内屏仍为可用宽度的 2/3。Android 10 以下不再触碰 scoped MediaStore 或伪报保存成功，而是给出明确的系统版本提示；MP4 重封装使用正确的 codec key-frame 标志。启动器 adaptive icon 已补 monochrome 层，不改变现有彩色前景、白色背景或 10% 主体缩放。全量 JVM 为 **371 tests / 0 failures**，`assembleDebug` 通过，fresh `lintDebug` 为 **0 errors / 60 warnings / 6 hints**；当前 Debug 已保留数据 `install -r` 到 Find N5，package `com.nanzhufeng.ai` 为 code 51 / `0.3.0-p10a`。**未关闭的真实链路：** 图片／视频的 ColorOS「照片」主时间线排序仍需用户实际触发一次新下载后可见确认；不得用自动测试、安装成功或旧的 `DATE_TAKEN` 表述代替该验收。

- **2026-08-15 普通模型设置收敛：** 用户明确要求不展示联网核验或真实请求能力。已从正常设置页移除“公开模型目录”核验区、真实服务状态卡与“一次真实文本请求”确认弹窗；`NanfengAiApp` 不再接入这些入口。模型预设与本机 Key 配置继续保留，但不会自动联网。受控底层 owner 保持在非正常 UI 路径，便于未来有独立授权时审计；正常 UI 契约明确禁止上述入口回归。全量 JVM 371/0 与 Debug assemble 通过；Find N5 保留数据覆盖安装待本轮包读回。

- **FB-P6-107 Android 搜索/历史胶囊高度回调与 IME 间距（OPPO 内屏真机闭环）：** 用户认为 34dp 过度压缩后，搜索与历史胶囊统一回调到 `42dp`（较原 `56dp` 紧凑 25%），圆角同步为 `21dp`；搜索文本采用单行 `labelLarge`，图片/视频等动态提示完整显示。结果区底部预留与历史 Popup 锚点同步为 `78dp / 68dp`，不留旧高度空白。键盘可见时胶囊组在 IME inset 之上增加 `8dp` 间距，避免紧贴键盘；键盘关闭仍为 `14dp` 常规留白。定向 UI 合同及 `assembleDebug` 通过。OPPO Find N5 内屏 `2248×2480` 实测：未聚焦时“搜索全部内容”文本 bounds `[188,2369][419,2397]`、历史 bounds `[1837,2355][1915,2411]` 均完整；聚焦后搜索组 bounds `[44,1308][1465,1440]`，截图显示胶囊与系统键盘之间保留小间距且无裁切。未写入会话、草稿或搜索历史。

- **FB-P6-106 Android 内屏左侧对话位置线收窄靠边（实现/构建/OPPO 安装闭环，视觉截图待复核）：** 仅在内屏显示的 `TranscriptPositionRail` 由左侧 `8dp` 改为 `2dp`，静止宽度由 `38dp` 缩至 `28dp`，内部起点由 `10dp` 缩至 `4dp`；最强位置线由 `28dp` 缩至 `20dp`，其余层级按 `15/11/8/6/4dp` 等比例缩短。每个纵向槽由 `10dp + 3.5dp` 调整为 `9dp + 3dp`，总高度约缩短 13%，避免遮挡正文和媒体；悬停/焦点时的预览行为和唯一 `LazyListState` 未改变。定向 UI 合同及 `assembleDebug` 通过，OPPO Find N5 保留数据覆盖安装且前台 package 已读回。安装后内屏截图通道返回黑帧、但 UIAutomator 仍正常读取对话内容；视觉截图需在下一次亮屏采集后补充，不能以当前黑帧代替验收。

- **FB-P6-105 Android 搜索历史空白关闭与 IME 对齐（OPPO 内屏 IME 实测）：** 搜索历史打开时，在结果区上叠加无可见底色的全尺寸点击层；点击 Popup 外任意空白处即关闭历史，不会遮盖或误触历史卡自身的条目/清空/关闭按钮。底部搜索与历史胶囊组改为只消费 IME inset；键盘可见时移除原本的额外 `14dp` 底部间距，胶囊底边直接贴合键盘顶边，键盘隐藏时仍保留正常安全留白。定向 UI 合同及 `assembleDebug` 通过。OPPO Find N5 内屏 `2248×2480` 实测 TextField 聚焦后 bounds 由 `[44,2286][1465,2441]` 上移至 `[44,1299][1465,1454]`，截图显示胶囊组紧贴系统键盘上沿、未出现双重上移或底部空隙；未写入会话、草稿或搜索历史。

- **FB-P6-104 Android 搜索页关闭返回左侧栏（OPPO 内屏交互闭环）：** 全屏搜索从左侧栏进入后，左上角 × 与 Dialog Back 统一走同一个 `onDismiss`：先关闭搜索页，再把抽屉展开状态写回唯一 owner，并即时打开 `DrawerState`；不再关闭后落回对话画布。定向 UI 合同及 `assembleDebug` 通过。OPPO Find N5 内屏实际操作“左栏搜索 → 搜索页 → 系统返回（同一关闭回调）”后 UIAutomator 读回 `关闭导航菜单 / 南枫 AI / 搜索 / 最近 / 设置 / 新对话`，证明左侧栏仍展开；未改动会话或草稿数据。

- **FB-P6-103 Android 全屏搜索悬浮胶囊与分类自适应（内/外屏真机视觉闭环）：** 搜索分类在 Find N5 内屏按内容组整体居中，外屏改为六个等宽可点击位，`全部 / 正文 / 图片 / 视频 / 音频 / 文件` 一屏完整可见且无需横滑；不再让外屏末项裁切。底部移除整条白色承载面，改为在结果内容区上方悬浮的两个浅灰大胶囊，结果列表预留 `92dp` 底部空间，不被控件遮挡。搜索提示由筛选状态唯一派生：全部为“搜索全部内容”，其余为“搜索正文 / 图片 / 视频 / 音频 / 文件”，没有“本地”字样或被裁切的长提示。定向 UI 合同及 `assembleDebug` 通过，并以 OPPO Find N5 内屏 `2248×2480` 实测：分类组居中、选择“图片”后底部显示“搜索图片”；外屏 `1140×2616` 实测六类完整显示。覆盖安装保持数据，设备 APK hash 读回见本轮命令记录。

- **FB-P6-102 Android 图片/视频保存到 ColorOS「照片」主时间线（实现/构建/OPPO 安装闭环，真实新媒体待用户触发）：** 针对用户截图所指的 ColorOS「照片」主时间线，图片与视频下载不再写入应用独立相册路径：统一分别写入 `MediaStore.Images` / `MediaStore.Video` 的标准 `DCIM/Camera`，并写入当前 `DATE_TAKEN`，以便系统按时间直接收录到「照片」顶部；PDF、音频和文本仍写入 `Downloads/南枫 AI`。本轮只读核验到了此前文件已分别位于 `Pictures/南枫 AI/` 与 `Download/南枫 AI/`，均为已完成状态；由此将 ColorOS 对独立相册的呈现差异与目标主时间线分离处理。定向 UI 合同（63 项）及 `assembleDebug` 已通过；OPPO Find N5 保留数据覆盖安装、启动及 base.apk 回读通过，APK/base.apk SHA-256 同为 `05a5ba942347cc13fc751893c2e2526530e5be503b4284c3cbe5c3b7a2d8b98d`。本轮未人为再下载媒体，避免写入用户图库；待用户触发任一图片或视频下载后，可在「照片」页顶部直接确认。

- **FB-P6-101 Android 内屏抽屉 2/3 宽度（OPPO 内屏真实闭环）：** 抽屉不再固定复用外屏 `320dp`：运行时窗口宽度 `>=600dp` 时采用可用宽度的 `2/3`，紧凑外屏继续保持 `320dp` 单手宽度；路由、抽屉状态、会话/草稿 owner 和 Drawer 内容完全复用，不新增第二套导航。定向 UI 合同（63 项）及 `assembleDebug` 通过。OPPO Find N5 内屏 `2248px` 实测打开抽屉，其纯白承载面 bounds 为 `[0,107][1499,2435]`，宽度 `1499px / 2248px = 66.7%`，未受 Material 默认窄抽屉上限截断；APK/base.apk SHA-256 同为 `8bd7a8dbcd13c1cf985e042cbd5bf12e3575d2675441fea7039721576432751d`。仅打开抽屉与读取层级，未写入会话或草稿。
- **FB-P6-100 Android 文件预览下载/分享与 AI 多图批量下载（OPPO 安装闭环，真实文件外发待用户触发）：** 图片、视频、PDF、音频、文本等所有本地文件预览右上角统一提供圆形下载图标与“分享”按钮；图片/PDF/视频为左上关闭、右上文件操作，音频/文本在标题行保留同组紧凑操作。长按任一附件的原位白色信息 Popup 也同步加入底部分隔后的“下载 / 分享”双操作行，锚点和不推动相邻内容的规则不变。下载/分享均先由 ViewModel 按附件 ID 显式读取并校验本地原字节；图片写入 `MediaStore.Images`、视频写入 `MediaStore.Video`，在系统图库呈现；PDF/音频/文本写入 Downloads。分享通过仅限 cache `shared_attachments` 的 `FileProvider` URI 交给系统分享面板，未暴露私有路径。单图点击下载直接保存；同一条 AI 消息含多张图片时，图片预览点击下载显示紧凑“下载当前图片 / 同时下载 N 张”浮层，批量逐张保存到图库。定向 UI 合同（62 项）及 `assembleDebug` 通过；OPPO Find N5 保留数据覆盖安装、强停冷启和包读回通过，APK/base.apk SHA-256 同为 `d932ab09caab06955af49688ddb8bc024153992adc34e2408b6c3d161054cb91`。本轮未点下载或分享，不产生图库、下载目录或外发副作用；真实系统授权/选择面板和文件可见态待用户自行触发后复核。
- **FB-P6-099 Android 对话右侧滚动条外沿、低透明度与平滑同步（OPPO 内屏闭环）：** 右侧条改为占用对话内容之外的独立 `14dp` 安全槽，并向外侧偏移 `12dp`，不再压在消息、附件或其操作目标上；轨道收为 `1dp / 5%` 黑色透明度，滑块为 `3dp / 28%` 正文色，弱化常驻存在感。位置和高度仍只读取同一 `LazyListState`，并以 `110ms LinearOutSlowInEasing` 分别插值；滑块长度只由整段会话总量决定，不再随当前可见的长短附件变化。定向 UI 合同、Debug assemble 均通过；OPPO Find N5 内屏保留数据覆盖安装，APK 与回拉 `base.apk` SHA-256 同为 `914622d1cc1e8b2a5d4335bffbbfd93878cf72d45f8c27b37b17b38adddd40e9`。真机 UIAutomator 读回滑块外沿坐标为 `x=2211..2219`（屏宽 `2248`），一次正常上滑前后由 `y=329..697` 平滑移动到 `y=796..1164`，高度保持 `368px`；未写入会话或草稿。
- **FB-P6-098 Android 抽屉标题 App 图标（OPPO 启动/抽屉真实闭环）：** 左侧抽屉“南枫 AI”标题前加入 `28dp`、`8dp` 圆角的紧凑 App 图标，标题和图标保持 `8dp` 间距，不挤压搜索、会话列表或底部操作。Compose 不直接加载 Android adaptive-icon XML（该资源会在运行时抛出不支持异常），而是复用启动器同源的 `nanfeng_ai_icon_foreground_image` PNG，以 `Fit` 保留原始图标比例。新增抽屉标题图标契约；`P6DConversationRowAccessibilityContractsTest`、`P6F2DVideoPreviewUiContractsTest` 定向 JVM（59 项）与 `assembleDebug` 通过。APK SHA-256 `8cac8499b85a8b107abb1a28eebb301f9c86aed12c7018c7cf3c525250be2b1b` 已对 OPPO Find N5 保留数据 `install -r`，回拉 `base.apk` 同 hash；强停冷启后 `NanfengAiActivity` 为前台，实际打开抽屉读回“南枫 AI / 搜索 / 最近 / 新对话”，无崩溃。未写入会话、草稿或设置。
- **FB-P6-097 Android 对话滚动位置双轨同步（OPPO 内屏真实闭环）：** 对话 `LazyColumn` 的 `LazyListState` 现在是右侧连续滚动条与左侧内屏 12 段位置轨道的唯一状态 owner。右侧 `TranscriptScrollIndicator` 根据首个可见项、项内偏移和可见比例实时移动滑块；左侧轨道在没有鼠标悬停/键盘焦点时，按同一滚动进度高亮当前分段，悬停/焦点仍只临时覆盖为 Desktop 的局部预览包络。两者均不创建第二个滚动容器、不持久化预览，也不影响消息/Composer 布局。`P6DConversationRowAccessibilityContractsTest`、`P6F2DVideoPreviewUiContractsTest` 定向 JVM（59 项）与 `assembleDebug` 通过；APK SHA-256 `b759ef3a37a44e127c93b0ffee16d5f50721a9ba9499ad8a15fda8d35305f62b` 已对 OPPO Find N5 内屏保留数据 `install -r`，回拉 `base.apk` 同 hash。实机从当前会话上滑一次后，UIAutomator 的“对话滚动位置”滑块范围由 `[2159,1433][2170,2169]` 移至 `[2159,1709][2170,2169]`；左侧 12 个“跳转到对话位置”节点仍在同一会话空间。未写入会话、草稿或设置。
- **FB-P6-096 Android 视频控制、附件长按信息与视频卡区分（实现/构建/OPPO 安装闭环，真实视频可见态待复核）：** 原生 `MediaController` 会抢先消费底部区域的边缘手势，已替换为视频窗口自身的轻量控制层，并由最上层透明手势 owner 统一接收事件。控制层显示 `12s`，用 `44%` 黑色半透明、顶部 `22dp` 圆角面板轻遮视频底部以明确分层；单击视频画面以同一套开关切换整套控制 chrome：底部进度条/时长与暂停态中央播放键同时显示或隐藏；底部播放按钮、中央播放键与 `GestureDetector` 双击视频画面分别可播放/暂停。左、右物理边缘向内滑超过阈值会在 `ACTION_MOVE` 立即关闭窗口，不先消费为隐藏控制栏。消息附件、Composer 草稿附件与 USER 文本的长按 Popup 统一以 `detectTapGestures` 回调的实际手指位置为唯一锚点，优先在该点正上方展开；只有顶端安全边距不足时才贴边。附件信息 Popup 在被长按附件自身的零尺寸浮层中发射，消息菜单也由根 Popup 脱离列表测量，均不能推动相邻图片、视频或文本。文件信息为压缩后的“类型 · 时长（若有） · 大小 · 发送于 M月d日 HH:mm”；不再堆叠技术 MIME，草稿明确显示“待发送”。视频缩略图为独立圆角媒体卡（消息 `16dp`、草稿 `18dp`、裁切填充与中性描边），会话内视频卡进一步统一为较矮的 `128dp` 高；图片继续按自然比例 `Fit` 预览，中央播放标与时长保留。`P6F2DVideoPreviewUiContractsTest`、`P6DConversationRowAccessibilityContractsTest` 定向 JVM（59 项）与 `assembleDebug` 通过；APK SHA-256 `4091bb45bafbfeb767806dfe622e96eeb3fc76ad7b64d84a680f2413b708b070` 已对 OPPO Find N5 内屏（`2248×2480`、override `442dpi`）保留数据 `install -r`，回拉 `base.apk` 同 hash。冷启后当前用户会话不含可安全复用的视频，因此这一版的手势、附件/文本 Popup 及 `128dp` 高卡真实可见态，仍待现有媒体自然进入时只读复核；不得写入播放位置、会话或草稿。
- **FB-P6-095 Android 内屏轨道与搜索历史隔离（实现/构建/OPPO 安装闭环，内屏可见态待物理展开复核）：** 普通对话左侧出现“吃吃吃”并非搜索页的 `SearchHistoryPanel`，而是轨道将首条消息作为 `rememberSaveable` 的默认预览；现已删除该持久预览状态。内屏轨道严格复用当前 Desktop 的 12 条、`3.5dp` 间距与 idle `5.33dp` 浅灰短线；仅鼠标悬停或键盘焦点临时形成 `28/20/14/10/7/5.33dp` 的双侧收敛和白色预览卡，点击只滚动既有 `LazyListState`，立即回 idle。搜索历史同时锁为搜索页的唯一 owner：离开搜索页时主动关闭，Back 也只会在搜索页历史打开时接管。`P6DConversationRowAccessibilityContractsTest` 定向 JVM 与 `assembleDebug` 通过，APK SHA-256 `8e9e5c007cf76403eb080ce89bd6fd3f44fa9f7d3345b87421b064e88748cd66` 已对 OPPO Find N5 保留数据 `install -r`、强停冷启，回拉 `base.apk` 同 hash；外屏主对话 readback 无任何“吃吃吃”卡。设备当时未物理展开，内屏 HWC 截图为熄屏黑帧，故不得将外屏读回写为内屏视觉关闭；待用户展开内屏后仅复核轨道 idle 与 hover/focus，勿写入会话或搜索记录。
- **FB-P6-091 Android 全屏统一搜索（实现/构建闭环，实机待验）：** 侧栏顶部搜索入口视觉高度从 `56dp` 收为 `38dp`，点击后不再在侧栏下方展开“最近搜索”，而是打开完全覆盖原对话的独立白底搜索页。范围固定为同一 owner 下的`全部 / 正文 / 图片 / 视频 / 音频 / 文件`；正文复用既有本地安全索引，附件从所有未删除会话的当前可见路径投影中检索，按 MIME 分类并回到所属会话，图片和视频使用既有受控缩略图/代表帧投影，绝不扫描外部路径或读取 Provider。底部为 `2:1` 的本机搜索框和“历史”触发区，历史只在用户点击后以白色浮层展开。此方向借鉴南枫知识库的“统一范围、正文附件并行、唯一预览 owner”方法，不复制其三栏工作台、主题业务或桌面时间线外观。定向 JVM 覆盖图片/音频类别、当前路径隔离与全部范围；`compileDebugKotlin`、定向 JVM 通过。尚未安装到 OPPO 或写入任何真实会话，最终触摸与可见媒体卡待隔离验收。
- **FB-P6-092 Android 附件直显预览（实现/构建/OPPO 冷启闭环，媒体可见态待验）：** 图片、视频、PDF 附件不再使用 `accent-orange-soft` 气泡。消息附件统一由 `AttachmentPreviewChip` 直显内容；图片和视频使用本体缩略图/代表帧，PDF 仅保留中性纸张边界作为点击目标。视频缩略图和草稿代表帧统一叠加半透明播放圆标与真实时长；展开页第一屏也保留全屏“视频 · 时长”与播放层，首次轻触即开始播放并隐藏该层，避免被误认成图片。图片展开为黑底原图画布（保留缩放/拖动），视频为黑底 `VideoView + MediaController` 原生播放面，PDF 直接渲染当前页，只保留关闭与翻页；删除标题、规格、说明和额外“开始播放”按钮。Composer 草稿附件同步去黄，使用白底中性描边。`P6F2B/P6F2C/P6F2D/P6D` 定向 JVM 共 60 项与 `compileDebugKotlin` 通过；本机构建 APK SHA-256 `55900e90ea3b001fc6bf528413c60df7723314601554b763a11a1d47890d4938` 已对 OPPO Find N5（内屏 `2248×2480`、`442 dpi`）`install -r` 并强停冷启，未清数据。设备当前无可安全复用的媒体附件，未导入用户照片、视频或 PDF，故真实点击的媒体画布/播放器/PDF 页可见态待用户允许使用隔离 fixture 后完成；不得写为最终视觉验收。
- **FB-P6-093 Android 启动器图标缩小 10%（OPPO Launcher 闭环）：** 用户明确要求手机端图标主体缩小 10%。不改 `432×432` 原始前景 PNG（SHA-256 `3010ba5b…fbf1b`）、白色连续背景或 manifest 引用；adaptive foreground 改为四边 `5.4dp` inset，恰为 `90%` 边长，mdpi—xxxhdpi legacy `ic_launcher`/`ic_launcher_round` 也重采样至 `90%` 后居中白底补齐。静态 launcher audit 与 `assembleDebug` 通过；Debug APK SHA-256 `327742a78be7fc6dafdb69ac51b35ab5ba021c559827950ed48dae0fd30cde14` 已在 OPPO Find N5 保留数据 `install -r`。真实 Launcher（内屏）截图确认 Dock 图标主体变小且无白圈、黑角、裁切或失真；未清数据、未卸载。
- **FB-P6-094 Desktop 混合消息附件顺序（实现/Node/Tauri bundle 闭环，真实附件窗口待验）：** Desktop `messageList` 的 DOM 顺序从“文字气泡 → 附件”改为“附件 → 文字气泡”，`chat-message-attachments` 间距也由 `margin-top` 改为 `margin-bottom`；USER 保持右对齐、ASSISTANT 保持左对齐。该修复同时纠正视觉、Tab/屏幕阅读器顺序，不用 CSS `order` 倒置语义。`chat-first-ui.test.mjs` 56/56、lint、static build 与 `cargo tauri build --bundles app` 通过。最新 `.app` 只读打开时当前用户会话没有附件，未切换会话、未导入 fixture、未改草稿或消息，故目标“图片在上、文字在下”的真实窗口可见态待一个现有或明确授权的隔离附件 fixture 复核；不得写为视觉关闭。
- **FB-P6-090 Android 直接系统分享（实现/构建闭环）：** Assistant 常显动作行与消息长按菜单的“分享”现在均立即以 `ACTION_SEND` / `Intent.createChooser` 打开系统分享面板，内容保持为当前安全呈现的纯文本；已删除“通过系统分享？”预览、说明、取消和二次“打开系统分享”按钮。系统目标选择或取消仍由 Android 负责，不会改写会话。定向 UI 合同与 `assembleDebug` 通过。
- **FB-P6-089 Android AI 信息格式、单块复制与链接表格（实现/构建闭环）：** Assistant 消息的安全 Markdown 投影升级为统一阅读层级：一级/二级标题、正文、间距稳定的有序/无序列表、满高引用线、闭合代码块及新增 pipe 表格。代码和数据表都以浅中性灰白信息面承载，右上角提供真实的单块复制图标；整条 Assistant 的复制/分享/分支动作继续保留。原始 `https` 链接也会转为实际可点击、无下划线的域名 `↗` 形式，保留 Markdown 命名链接语义；不使用额外主题色或伪造来源。受控本地 AI fixture 已覆盖标题、列表、引用、表格、原始链接和代码块。定向 JVM（解析、格式、复制、UI 合同）、`assembleDebug` 通过；未向 OPPO 现有会话写入 fixture，因此最新实机可见态待自然使用或隔离验收时补核。
- **FB-P6-088 Android 首条消息自动标题（实现/迁移/构建闭环）：** 左侧栏不再预写无意义的“本地对话”。新建会话先以中性“新对话”占位，并持久化 `autoTitlePending`；首次本地提交时，在消息追加与草稿清理的同一 Room 事务内，用首条用户信息生成一次紧凑本地标题（清理 Markdown/礼貌前缀、优先首句、按显示宽度截断；纯链接使用域名，纯附件使用图片/视频/音频/文件对话）。手动重命名立即关闭资格；导入与升级前的既有会话迁移为 `false`，不会被追溯改名；生成成功也关闭资格并递增管理 revision。实现没有调用模型、Provider 或网络。定向 JVM 覆盖标题规则、手动/导入保护、Room 原子读回和 25→26 迁移，且 `assembleDebug` 通过；为保护 OPPO 已有会话，尚未发送测试内容做可见真机写入验收。
- **FB-P6-087 Android 导航快照、侧边退出与抽屉胶囊（OPPO 真实闭环）：** 路由与对话左侧栏展开态已收敛为唯一可恢复 `P5AUiState(route, conversationDrawerOpen)`，由 Navigation ViewModel 的 SavedState 与 Activity `p5a_ui` 偏好同步持久化；不再把设置、抽屉或一次侧滑当作互相冲突的临时补丁。用户只是回桌面再进 App 时，严格恢复当时快照：设置页仍回设置，关闭的对话仍回正文，打开的侧栏仍保持侧栏。只有在设置页从左/右物理边缘退出时，快照才原子切成“对话 + 侧栏展开”；反过来，侧栏从左/右物理边缘退出只关闭侧栏并保留当前对话。底部没有手势排除，继续由系统 Home/最近任务处理。`ModalDrawerSheet` 固定 `320dp`，Find N5 实际左栏约 `691px`、右侧可见内容约 `449px`；底部“新对话”改为完整半圆橙色胶囊（`CircleShape`），不再呈方形。定向 JVM 合同、`assembleDebug` 与 v2/v3 签名通过；OPPO Find N5 保留数据覆盖安装后实测：设置→Home→启动器回设置，设置左右物理边缘退出→侧栏→Home→启动器仍为侧栏；侧栏左/右物理边缘退出均回当前对话正文。APK 与回拉 `base.apk` SHA-256 同为 `738a5e1c7e3ee620b38958530a266a276e0a04fee3cd3c8a07000cfaa8821df7`；未点击任何写操作，未改写会话、草稿、模型或设置。
- **FB-P6-086 Android Composer 模型菜单文字居中（OPPO 真实闭环）：** 输入框模型选择菜单中的模型文字改为菜单行的几何中心；选中勾号独立固定在右侧，不再通过 Row 权重把文字推向左侧。菜单的白色表面、锚点、尺寸、Auto/手动选择语义及 owner 不变。定向 JVM 合同与 `assembleDebug` 通过；OPPO Find N5 保留数据覆盖安装后实际打开模型菜单，“自动”文字居中且橙色勾号仍在右侧。APK 与回拉 `base.apk` SHA-256 同为 `aa44a6df201b4c3ea057e616e4078ab2d854d747b95f9a90ced55f2bf1f86700`；未选择模型、未改写会话或设置。
- **FB-P6-085 Android 设置分层（OPPO 真实闭环）：** 设置首页不再堆放模型、会话、导入、隐私等完整卡片，只显示四项中性分类入口：模型服务、对话与存储、数据导入、隐私与安全。每项进入独立二级页，左上返回先回分类首页；模型页保留既有模型设置、调用记录、P6-G 本地目录与一次真实请求确认入口，对话页保留归档/回收站/导出 owner，数据页再进入既有导入流程，隐私页继续打开既有隐私与数据 owner。没有改变 Key、Provider、Room、导入、导出、隐私或真实服务写入语义。定向 JVM 合同和 `assembleDebug` 通过；OPPO Find N5 保留数据覆盖安装，四个首页分类、四个二级入口及二级返回分类首页均实际读回。APK 与回拉 `base.apk` SHA-256 同为 `3d940fdf0a2b14f3afc2237c1c4a7f14664910ff158d8e1c8e81a593831b2fed`；未点击导入/导出/保存/隐私或真实服务动作。
- **FB-P6-083 Android 原地选择、气泡菜单与引用竖线（OPPO 真实闭环）：** 两种长按能力均保留且不抢手势。USER 气泡始终按正文自然宽度收紧，不允许手势层参与测量而撑成最大宽度；它在内容测量后才以 `matchParentSize` 覆盖既有几何。长按正文中央仍交给 Android 原生原地文本选择；长按既有上、下 `12dp` 留白，或左右各四分之一的边缘空白/黄色边缘文字区，打开既有白色锚定消息菜单（复制、选择文本、编辑消息、分享）。附件长按既有菜单路径不变。引用左竖线由 `3dp` 收为 `2dp`，以 `IntrinsicSize.Min + fillMaxHeight` 完整覆盖全部引用行，仍为深色、缩进、斜体且无彩色底。定向 JVM、`assembleDebug`、v2/v3 签名均通过；OPPO Find N5 保留数据覆盖安装，APK 与回拉 `base.apk` SHA-256 同为 `e2beeaca361a9396a55add63f4fa1f6a6f178b7474e60e13bb36ef61263f5430`。真机长按右侧既有边缘已读回四项白色菜单；中部正文长按未触发菜单，保留原生选区。未点击任何菜单项、未改写会话或草稿。
- **FB-P6-082 Android 编辑分支弹窗去冗余并缩小操作（实现/安装闭环）：** 用户要求编辑分支弹窗删除全部小字、标题简洁，并把取消/创建分支同步缩小约 2/5。标题已从“编辑消息并创建分支”收为“编辑”，删除本机分支说明、输入标签与输入辅助说明，只保留编辑区和两个必要操作；取消调整为 `44×30dp`，创建分支为 `76×32dp`，文字 `13sp`。定向 JVM 与 `assembleDebug` 通过；OPPO Find N5 保留数据覆盖安装，APK/base.apk SHA-256 同为 `1ec31727534c992a7f7de075a84052f76505a1d49830226cfae59cd45e2e4d22`。当前可见会话没有可编辑用户消息，未切换会话或写入测试内容，因此此弹窗最新真机截图待自然进入编辑路径时补验。
- **FB-P6-081 Android 引用版式与可点击链接（OPPO 安装闭环）：** 用户澄清黄色 USER 气泡必须保留，问题是其中引用文字的蓝灰底。引用改为深色左竖线、`10dp` 缩进与斜体正文，不再使用蓝灰填充；其最终 `2dp` 宽与全行高覆盖由 FB-P6-083 固化。链接改为 Compose `LinkAnnotation.Url` 的真实可点击强化色文字，去掉下划线。图片、视频、音频、文本附件呈现恢复原合同，未再改动。定向 JVM 与 `assembleDebug` 通过，OPPO Find N5 外屏保留数据覆盖安装，APK 与回拉 `base.apk` SHA-256 同为 `b6b94ef344397c72e255ac9c8fe659c7f64c6b125ac5e1b60bec1b4963fd5779`。当前真实会话没有可用于非破坏性展示的结构化 Quote/Link fixture，因此 Quote 的真机可见态待已有此类消息自然出现后复核；未写入会话、未点开外部链接。
- **FB-P6-080 反馈澄清（已纠正，不作为最终 UI）：** “去掉黄底”最初被误解为 USER 气泡及附件底；用户随后明确黄色消息气泡要保留，目标是黄色气泡内的蓝灰引用文本格式。该误改已在 FB-P6-081 前完整回退，不影响附件 owner、预览或文件内容。
- **FB-P6-079 Android 弹窗白底与宽文本选择（OPPO 真实闭环）：** 用户要求弹窗统一纯白，并将“选择文本”阅读面左右加宽。Material elevated surface-container 全局锁为纯白、主题 tint 透明，`选择文本` 直接使用 `Color.White` 自绘宽 Dialog；左右各 `16dp`、文本区最多 `580dp` 并滚动、底部“完成”始终留在面内。OPPO 实测截图 `/tmp/nanfeng-ai-fb-p6-079-wide-selection.png`：弹窗宽约占可用屏幕 92%，纯白，完成按钮可见；仅进入/关闭查看，不改写会话内容。其它 Popup/Sheet 亦显式白底或继承全局白色 elevated surface。
- **FB-P6-078 Android 消息长按紧凑浮层（OPPO 真实闭环）：** 按用户图一与补充要求，Android 消息长按从占半屏的 `ModalBottomSheet` 改为消息实际边界锚定的白色 `Popup`：固定 `224dp` 宽、`46dp` 行高、`20dp` 圆角、`6dp` 低阴影，优先贴在选中消息右侧下方、空间不足翻到上方；不再固定在屏幕底部，也不加主题橙。复制、选择文本、编辑消息、分享统一显式使用正文黑 `BodyText`，时间保持中性灰；编辑仍只对可编辑用户消息显示，既有复制/文本选择/系统分享/本地分支 owner 不变，Desktop 未触碰。定向 JVM、`assembleDebug`、`lintDebug` 通过；OPPO Find N5 外屏保留数据覆盖安装，代码 APK 与回拉 `base.apk` SHA-256 同为 `c33399e6f5be9e538d19b90b4516b3ff40d7ac510dd42460140c16b21bbd12a2`，实际长按用户消息后浮层右缘对齐该消息且因下方空间不足正确翻至上方，截图 `/tmp/nanfeng-ai-fb-p6-078-message-popup.png`。仅打开、长按和截图，未点击菜单项、未改写会话或草稿。
- **FB-P6-077 Android CTA 橙色同步（OPPO 正常态闭环）：** 用户确认 Desktop 已单独提亮，要求不要再改 Desktop、只补 Android 未同步项。Android 原 `AccentOrange #BD5B08` 已替换为 Desktop 当前主橙 `#E97128`；同一共享令牌的 hover `#D86520`、pressed `#C2581A`、soft `#FFF1E5`、focus `#EFBD94`、disabled `#F4C8AA` 一并对齐。抽屉“新对话”、Composer 发送和 Material `primary` 都消费 `AccentOrange`，不会改变布局、会话 owner、图标、Provider 或 Desktop 文件。定向 JVM、`assembleDebug`、`lintDebug` 均通过；OPPO Find N5 外屏保留数据覆盖安装，最新 APK 与回拉 `base.apk` SHA-256 同为 `b678564067869a8c108b04f9e6f7fb624da51addec126ed9cc5402118af126b5`，实际截图 `/tmp/nanfeng-ai-fb-p6-077-drawer.png` 已确认新对话正常态提亮。为保护现有草稿，未写入文本来触发发送按钮；发送的同令牌链已由源码合同覆盖，Android 按压/禁用真实视觉尚未单独截图。
- **FB-P6-076 Android IME 与 ColorOS 图标比例（OPPO 真实闭环）：** 用户 OPPO 截图暴露 Activity 已按键盘 `adjustResize` 后，根 Scaffold 又叠加 `imePadding()`，同一 IME 高度被扣两次，Composer 因而飞到屏幕中部；现 Manifest 显式锁定 `adjustResize`，根层删除第二 owner，仅保留安全区 inset。用户同时明确 Android Launcher 前景相对 Desktop 过大并要求缩小约 25%；Android adaptive/round/legacy 现从不可变原件直接按 `0.6375 = 0.85 × 0.75` 光学比例生成，Desktop 继续保持已验 `0.85` 资源不动。定向 JVM、`lintDebug`、`assembleDebug` 与两套图标静态审计通过；正式签名 Debug SHA-256 `b9d6dca44f97b0012e8126ddff269ccf539d2cbf89e22d8ae762aec48d5da538` 已在 OPPO Find N5 外屏保留数据覆盖安装，回拉 `base.apk` 同 hash。Sogou IME 打开时 EditText 从 `[217,2372]–[746,2505]` 等尺寸平移到 `[217,1490]–[746,1623]`，恰好随系统 resize 上移 `882px`；Composer 完整操作组停在键盘正上方，重回应用后恢复关闭态原坐标。ColorOS Launcher 已实际显示缩小后的前景，连续白底、原图阴影与字标无新增包装；截图 `/tmp/nanfeng-ai-fb-p6-076-ime.png`、`/tmp/nanfeng-ai-fb-p6-076-ime-closed.png`。未卸载、未清数据、未读 Key、未发 Provider。
- **新交接覆盖：所有 UI 反馈必须双端同步（2026-08-14，进行中，不是项目暂停）：** 用户要求暂停扩展，只交接全部未收口 UI 反馈。普通对话、临时对话、工作区必须是同一套纯白 chat-first 壳：顶栏、底部 Composer、`＋ / 自动模型 / 发送`、锚定浮层、阴影和可访问语义一致；数据 owner 可不同但不得分叉为第二套界面。默认普通页必须只有留白和 Composer，绝不显示“还没有本地对话”、fixture、验证、Provider、TEMP 内部字段或其他工程说明；空态也不得删 Composer。`＋` 固定相机/添加图片/添加文件，TEMP 也必须走 TEMP private-copy owner；模型入口统一“自动/可用模型”，TEMP 不得显示“临时”或手输模型 dialog。所有既有反馈与最新截图的逐项清单以 `docs/PRODUCT_FEEDBACK_DECISION_LEDGER.md` 的 FB-P6-001..070 为索引，历史 CLOSED 仅作历史证据，不得跨平台沿用。
- **FB-P6-075 Android 三态消息阅读宽度统一（实施完成、模拟器待复核）：** 用户最新三图确认 NORMAL 是正确基准，TEMP 与 WORK 却各自使用固定 `680dp` 的替代气泡，造成长用户消息近乎铺满屏幕。现移除 WORK 独立投影及项目标题，WORK 直接复用 NORMAL 的 `MessageBubble`；TEMP 与 NORMAL 复用 `RightAlignedUserBubble`，统一为内容驱动、右对齐、最大 `0.82 ×` 可用宽度的暖橙气泡。顶栏与 Composer owner/几何不改，TEMP private-copy 与 WORK 当前会话 owner 不合并。定向 JVM 合同通过；最新 Debug 包尚待连接模拟器后，以同一长消息依次核对 NORMAL/TEMP/WORK 的右边缘与最大宽度。未操作 Desktop、OPPO、Provider 或会话写入。
- **FB-P6-063 Desktop USER 气泡宽度（真实闭环）：** 用户截图确认短文本 bubble 仍被拉成长方框。根因是 `chat-message-content` 同时容纳 bubble 与 hover tools/metadata；即使外层为 `fit-content`，block 级 `.chat-message-bubble` 仍会随外层工具宽度扩张。现将外层设为右对齐 flex column，并强制 bubble 本体 `width: fit-content; max-width: 100%`，短文本只保留文字与 padding，长文本才达原有 82%/530px 上限。Desktop lint、定向 Node contract、全量 Node 66/66、static build、Tauri bundle/ad-hoc strict verify 均通过；新 bundle 只读滚动到实际“谷歌”短消息，窗口可见 bubble 已紧凑匹配文字。可执行 SHA-256 `b0b860c3…`；未写入/删除会话，未改 Android、未操作 OPPO、Key 或 Provider。
- **FB-P6-064 Android 会话长按菜单视觉收紧（真实闭环）：** 用户指出 anchored popup 的底部阴影有硬边且整体偏大。现只收紧表面与内容几何：宽 `224dp`、行高 `48dp`、图标 `22dp`、正文 `15sp`、圆角 `20dp`，白色 surface 的 elevation 从 `12dp` 降为 `6dp`；锚点、动作集合、确认/软删除与所有 owner 不变。定向 JVM 合同、Debug assemble 均通过；`emulator-5554` 覆盖安装后，对“本地对话”仅作 1.3 秒长按，实际看到缩小后的 popup 与柔和连续阴影，未点击任何菜单项。当前 APK/设备 `base.apk` SHA-256 同为 `ff95a36d…`，截图 `/tmp/nanfeng-ai-fb-p6-064-compact-menu.png`；未改 Desktop，未操作 OPPO、Key 或 Provider。
- **FB-P6-065 Desktop/Android 会话菜单统一（Desktop 实施、窗口复核待重开）：** 用户截图确认 Desktop 新四行格式已生效，但也暴露菜单位置被错误 clamp 到左上。根因是 Tauri 右键/键盘事件期间侧栏 bounds 可瞬时为零；现失效侧栏自动回退窗口/根容器实际尺寸，再以会话行 right edge 右对齐、下方优先、空间不足上翻。菜单继续为图标、置顶/重命名/添加到项目（右箭头）/红色垃圾桶删除，`192px` 宽/`48px` 行/`22px` 图标/`20px` 圆角与低透明度柔影；Android 继续为当前已验 `224dp` 对应比例。Desktop 定向 Node 46/46、lint、static build、Tauri `.app` build/ad-hoc strict verify 已通过，最新可执行 SHA-256 `dc530cab…`；用户改变独立窗口后已停止进一步输入，需完全退出并重开新包后只读复核，不以静态通过冒充可见关闭。未执行任何菜单写入，未操作 OPPO、Key 或 Provider。
- **FB-P6-066 Android 抽屉选中行文字色（真实闭环）：** 用户截图指出浅橙选中行“本地对话”错误继承主题蓝紫色。现 `ConversationNavigationRow` 的 `Surface` 显式 `contentColor = BodyText`（`#1E2925`），浅橙仅保留为选中背景。定向 JVM 合同、Debug assemble 通过；`emulator-5554` 覆盖安装后打开抽屉，实际标题为黑色。当前 APK SHA-256 `ac86d216…`，截图 `/tmp/nanfeng-ai-fb-p6-066-drawer-black-row.png`；未操作 Desktop、OPPO、Key 或 Provider。
- **FB-P6-067 重命名弹窗精简（实施完成）：** Android 与 Desktop 都已移除“重命名会话”标题和字符说明，只保留“会话标题”输入、取消与保存；命名校验和既有写入 owner 未改。Android 定向 JVM/Debug assemble 与 Desktop 定向 Node 47/47、lint/static build 已通过；未保存写入。
- **FB-P6-068 双端发送后直接置底（Android 真实闭环、Desktop 自动门）：** Android NORMAL/TEMP 都在点击发送时记录消息数，等本地提交成功追加后仅通过现有 `LazyListState.scrollToItem` 跳至最后一条；Desktop 正常对话在本地保存成功后，以同一 conversation ID 标记下一次 restore，直接将唯一 scroll owner 置底。两端都不受此前历史阅读位置影响。Android 定向 JVM/Debug assemble 通过；`emulator-5554` 正常 Composer 发送 `FB-P6-068-auto-bottom` 后，新消息直接位于末条，草稿清空且没有“到最新消息”按钮。Desktop 定向 Node 48/48、lint/static build 通过，但未接管用户已有窗口做交互。Android 本地 APK 与回拉 `base.apk` SHA-256 均为 `49d52a8c…`；截图 `/tmp/nanfeng-ai-fb-p6-068-after-send.png`。无 Provider/网络、OPPO 未操作。
- **FB-P6-069 Android 重命名表单比例与裁切（真实闭环）：** 已将默认 AlertDialog 的隐式 title/body 留白替换为专用紧凑 Dialog，并修正首次错误按全屏 `94%` 计算导致越出 Drawer 的问题。现在 Dialog 左对齐抽屉，最大 `336dp`、左右 `12dp` 留边；输入为 `48dp` 满高可编辑行、操作行 `44dp`，标签仅覆盖边框，不再消耗有效输入空间。定向 JVM/Debug assemble 通过；`emulator-5554` 覆盖安装后，从抽屉长按“本地对话”进入重命名实际读回，完整输入框和两个操作均在抽屉内，未保存写入。截图 `/tmp/nanfeng-ai-fb-p6-069-rename-fit.png`；无 Provider/网络、OPPO 未操作。
- **FB-P6-070 Desktop Composer 发送说明删除（实际复核）：** 已删除输入区下方“发送时自动保存到当前工作区，不调用模型。配置模型后可生成回答。”整行；输入、模型、发送与真实本地保存 owner 未改。专用 Node UI 合同、Desktop Node 49/49、lint/static build、`cargo tauri build --bundles app` 通过；2026-08-14 已实际打开最新 `.app`，确认 Composer 直接贴底、旧说明未显示。未调用 Provider 或改动本地会话、OPPO。
- **FB-P6-071 Desktop 侧栏品牌副标题删除（实际复核）：** 已移除“南枫 AI”下方重复的“对话与工作”；品牌按钮仍回到对话，不改顶部对话/工作切换、Android 或任何会话数据。Desktop Node 50/50、lint/static build、Tauri bundle/ad-hoc strict verify 通过；独立最新 `.app` 实际窗口确认副标题不存在。
- **FB-P6-072 Desktop 会话菜单标题锚点（实际复核）：** 根因是旧实现使用完整会话行（含日期与 hover actions）的 right edge，菜单受侧栏 clamp 后会漂到左上。现在只读取 `.chat-history-select > span` 的可见标题 rect，菜单 leading edge 对齐标题左边、优先在标题下方、空间不足才上翻。Desktop Node 50/50、lint/static build、Tauri bundle/ad-hoc strict verify 通过；最新正式开发包对“逻辑回归”右键实际打开菜单，Escape 关闭，无会话写入。
- **FB-P6-073/074 Desktop 位置轨道常驻卡片与视觉密度（实际复核）：** 取消首条静态 `is-active`、active 常驻预览与点击后冻结的长线包络；预览和双侧条形包络仅 hover/键盘 focus 显示，指针点击只跳转并立即恢复 idle。轨道 idle `5.33px`、行距 `3.5px`、首条无默认激活；交互峰值 `28px`，两侧 `20/14/10/7/5.33px` 连续快速收敛，预览卡距最长线 `16px`，hit target 仍是 `28px`。Desktop Node 51/51、lint/static build、Tauri bundle/ad-hoc strict verify 通过；最新隔离 `.app` 首屏确认无预览卡、首条与其余短线一致且整体居中，点击跳至第 44 条后不遗留长线或卡片。
- **FB-P6-061/062 Android 会话长按菜单（真实闭环、Desktop 未改动）：** 根因是旧 `ConversationNavigationRow` 在 `ModalDrawerSheet` 内直接创建 `ModalBottomSheet`，长按手势虽然送达但弹层被 Drawer 模态层裁切，用户看不到任何菜单。现改为该行上报自身 bounds，根层 Popup 以该行右侧/下方为锚点、空间不足自动翻转；四行菜单仅保留图标、置顶/重命名/添加项目（右箭头）/红色删除，不显示会话标题、“会话操作”“危险操作”、divider 或大按钮。工作模式的归档/恢复继续由同一菜单条件投影。定向 JVM、Debug assemble、正式签名 Debug `71396329…` 的 v2/v3、`install-r` 与 AOSP 1.3 秒长按通过；force-stop/relaunch 后再次长按读回同一 anchored popup，回拉 `base.apk` hash 相同。截图 `/tmp/nanfeng-ai-fb-p6-062-anchored-conversation-menu.png`。未点击任何写操作，未操作 OPPO、Desktop、Key 或 Provider。
- **FB-P6-060 图片预览归位（Android 真实闭环、Desktop 待实施）：** 正常、工作和临时草稿图片不再在输入框外另起预览/“移除”区域；缩略图、右上角关闭、点击本地原图、长按事实都在同一个 Composer 白色外壳内。TEMP 仍由隔离 private-copy owner 投影，其关闭只移除 TEMP 草稿引用。三项定向 JVM 合同、Debug assemble、正式签名 Debug `a00c4b5c…` 的 v2/v3/证书/`install-r`/base.apk hash 均通过；AOSP 实测 NORMAL 选图→预览、WORK 复用、TEMP 选图→restart→Ghost 恢复 readback，随后正常移除两处测试草稿并 restart 无残留。截图在 `/tmp/nanfeng-ai-fb-p6-060-in-composer.png`。未操作 OPPO、Desktop、Key 或 Provider；Desktop 同语义尚未实施，不能标为双端关闭。
- **当前源码/包只是一份 Android 局部 WIP，绝不能误称完成：** 本轮 Android 已补 TEMP 的统一模型入口、TEMP 相机 owner、普通空态自动创建空本地会话以显示 Composer；最新 Debug `app/build/outputs/apk/debug/南枫AI-开发验收.apk` SHA-256 `e24c5a61a6fb0c0a168d2336050ee50308f7e01d740b77113d9904cda62f365f`，v2/v3、既定正式证书。AOSP 真实点到 TEMP `＋` 后显示相机/添加图片/添加文件，并点进系统 Camera；普通/工作在空内容时均见同一 Composer。没有 Desktop 同步实现或真实 `.app` 验收；普通自动空会话、TEMP 真相机回传/private-copy、三态消息/附件/restart readback 也都未关门。用户已要求交接，下一任务先审计并补双端，不得继续声明进度、Provider/Key/HTTP 或 OPPO。
- **下一任务唯一入口：** 完整读取本文件顶部、`/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/2026-08-14-nanfeng-ai-ui-feedback-dual-platform-handoff.md`、ledger、P6F contract/evidence；先跑 `context_gate.py`，再按“默认 NORMAL → TEMP → WORK”的三态、Android + Desktop 双端逐项解决和验收。HANDOFF 是线程卫生，不是项目暂停；UI 反馈清零后才恢复总蓝图。

- **最新可见反馈执行状态（优先于下方历史 P0 记录）：** 用户已明确授权模拟器数据可自由修改/删除，并要求 UI 反馈先全部收口。当前正式签名 Debug `app/build/outputs/apk/debug/南枫AI-开发验收.apk`（SHA-256 `b528631d55973d4231d52c920aa98d2bf51361c0d5a547a11b4faf63dbf1af51`，v2/v3、既定证书、设备 base.apk 同 hash）已 install-r、force-stop/restart。Android Drawer 为纯白、单一搜索输入/IME 搜索、底部同一行但两个完全独立 surface：设置白色圆形按钮靠左，方框斜笔“新对话”橙色胶囊靠右且有独立阴影；正常 transcript 不再显示 fixture/retry/工程历史；Composer 已移除常驻附件/草稿说明，空态仅“回复 南枫AI”。FB-P6-050 已在 AOSP 2248×2480 近似内屏真实显示位置轨道与预览，点击第二位置后预览切换为第二条并使用同一 `LazyListState`；Desktop 预览卡已扩大为 320px/三行级内容承载以贴近用户参考图，但仍待真实窗口交互；默认外屏尺寸 force-stop/restart 后不显示轨道。用户报告的“到最新消息”已用普通 Composer 保存 22 条模拟器消息后重测：离底显示、点击到末尾、按钮立即隐藏，无闪烁卡住。Desktop 模型与发送键已修复为右侧同一组，模型紧贴发送键左边，模型胶囊为紧凑中性面（不再是突兀白色描边按钮），按压/hover/focus 都使用同一圆角轮廓；Desktop Drawer 现为左侧 44px 独立设置圆键、右侧 44px/22px 圆角带阴影新对话胶囊；最新 ad-hoc strict-signed `.app` 可执行 SHA-256 `31f6264cc8d5ce991425c82227708b14a3ac28a70c868f68be59af6112ffc26e`，但 040/050/051 的 Desktop 人工交互 readback 因已有同 bundle-id 窗口未完成；为保护用户 Desktop 数据，不杀现有进程、不操作该窗口。OPPO 未操作，050/051 不得写为双端关闭。下一优先动作是逐项审计剩余可见反馈；HANDOFF 绝不是暂停。
- **Desktop 隔离可见复核补充：** 最新 `.app` 的独立临时副本已实际启动且未触碰既有用户窗口/数据；真实 AX 与截图确认模型在发送键左侧、位置轨道、左设置/右新对话及精确 placeholder。Computer Use 在尝试进一步交互前报告用户改变临时窗口，故遵守用户优先原则停止该窗口的后续动作；040/050/051 仍保留最新交互/readback 门，不将本次可见性冒充为关闭。
- **FB-P6-056/057 Composer 与临时状态（实施中）：** Android 已将＋/模型菜单从会触发焦点/IME 重协商的 `Popup` 移为会话根容器内 fixed-size sibling overlay；其 placement-only offset 定位到各自按钮正上方，完全不参与固定 `60dp` Composer dock 的测量。AOSP 实际＋/模型开关前后草稿/发送 bounds 保持不变；＋是相机、图片、文件，模型未选灰/当前橙，底部仍有 `10dp` 软阴影。普通会话幽灵改为灰色，真实进入临时窗口后才为橙色。当前正式签名 Debug SHA-256 `ef7f79a9bd98e2bf7e5235049a563d1f5f768972821a4c0fe560c426263c5bc4` 已 v2/v3 verify、install-r/force-stop/restart，Android 全量单测通过；Desktop `.app` 锚定交互仍待。不得将本条写为 CLOSED，且不得读 Key、发 Provider HTTP、操作 OPPO。

- **P0：af45c6d5 指定包的 Android 验收环境污染（2026-08-14，未关闭）：** 当前工作区 Debug 与已安装 `emulator-5554` 原为未登记的 `0357ec47f0ac3d160d6b523858ad3089a51312061f18c49ac4361a3635cd2a09`。按本阶段指定包，以 `docs/evidence/p6f-043-android-white-installed-base.apk`（SHA-256 `af45c6d5abd88fbef48744218b64bbb42613c30e582444c1f5c894e9b74d0e94`，`0.3.0-p10a`/code 51、v2/v3、既定正式证书）执行 `install -r --no-incremental`、force-stop 与显式冷启动 `NanfengAiActivity` 后，首屏直接读到 `P6F045-049-AOSP-CONTROLLED-20260814`、`AB`、短样本和长样本。Drawer 的所属行仅为泛称“本地对话”，不能在不触碰用户数据的前提下证明整会话为可删除验收对象；未执行删除、清数据、DB/ADB 绕过或新建写入。故 045–049 不能在该现场重新获得“无污染”关闭，FB-P6-050 不得开始。需要用户明确授权清理该会话，或提供可隔离的 AOSP 数据环境；OPPO、Key、Provider HTTP 继续未触及。
- **FB-P6-040—043 最新包真实复核（2026-08-14）：** 040：Desktop 与 AOSP 均经正常 UI 的 Auto→本地 fixture→重启读回→Auto→重启读回；041：最新双端实际 Composer 显示缩小 `+` 与向上 send glyph，hit/surface 未缩小；042：Desktop 顶栏只保留对话/工作/Ghost、Settings 仍在 sidebar footer，AOSP 顶栏无 Settings；043：两端各新建可识别的 `P6F043-…-CONTROLLED-20260814` 普通本地会话，以 Composer 正常保存长消息，真实离底后显示“到最新消息”，点击到末尾稳定隐藏且 Composer 可用，重启后读回并再次点击隐藏。Desktop 会话经现有确认式“移入回收站”软删除、退出重开后不可见；AOSP 会话同样从已验证含标识的最近列表进入“移入回收站”，force-stop/restart 后正常会话面不再显示标识。Desktop 最新 ad-hoc strict-signed `.app` 为 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，可执行 SHA-256 `baa7b45bfbf1f9c616da90846bcea86d8c1816bebb3a22f7fc12f239301bf5d2`。AOSP 的 043 功能链历史包为 `cddef540…`；Drawer 最终视觉已改由新正式签名 Debug `af45c6d5abd88fbef48744218b64bbb42613c30e582444c1f5c894e9b74d0e94`（v2/v3、既定证书）完成 `install -r`、force-stop/restart、前台身份和回拉 `base.apk` 一致性核验，实际截图确认抽屉纯白、无偏紫；不得再将 `cddef540…` 作为 Drawer 最终 UI 证据。首次 AOSP 重启曾在 ART/JIT 与并发 uiautomator 注册冲突期间触发系统 ANR；单通道充分等待冷启动后正常读回，故此环境干扰保留在 evidence，未作源码改动。以上仅为 Desktop+AOSP 本地 UI 验收，不是 OPPO、Provider、044 或 050 结论。
- **优先级覆盖与当前可见审计（2026-08-14）：** 用户已明确暂停 044 的 Provider/凭据关闭门；044 保持 **暂停、非 CLOSED、nonce 1/1 已消费**，不得发 HTTP、读写 Key、重试或操作 OPPO，但不再阻断 045→046→047→049→050（050 仍只允许 Desktop + Android 内屏）。最新 Desktop `.app` AX 已读回精确 placeholder `回复 南枫AI`；AOSP 现有会话仍含 9 条 `FBP6043localacceptance*`，均为验收污染而非用户功能证据；用户截图所示 `0.0 秒` 已追至 Android 非正 duration 格式化路径并改为 omit。040–043 旧“CLOSED”证据因此进入最新包复核，043 另带污染回归；不得用旧测试/截图掩盖。**045 已 CLOSED：** AOSP 最新正式签名包与最新 Desktop ad-hoc strict-signed `.app` 均完成真实 UI、完整退出/重开与 readback；Desktop 使用 P6-H 隔离 acceptance root，以正常 UI 新建 2 字/短句/长句的受控会话，截图确认短泡紧凑、长文仅达 responsive 上限后换行且均右对齐。验收读回完成后该会话出现用户写入，故保留、不再删除；没有触碰任何未知会话或 DB。受控 AOSP 新建会话不再自动插入 fixture；此前已创建的纯 045 验收会话均经正常软删除移入回收站并重启确认不可见。现存 FBP6043 会话没有逐消息删除 owner，且无法证明整会话不含用户数据，故未作整会话删除或 DB 绕过。
- **FB-P6-046 CLOSED（最新包复核；当前 Desktop 输入名称已按用户改为“搜索”）：** Desktop 重建、ad-hoc strict-sign 与完整退出重开后，原生 AX 只保留搜索输入和“执行本地搜索”动作；AOSP 最新正式签名 Debug force-stop/restart 后，drawer 中“搜索”只出现两处（输入 label、提交动作），不存在独立静态标题，且本地/回拉 base.apk SHA-256 同为 `b33efdfa5d2857e5779af20af0304d597267f3cfaffbf5e3373a50902c00b41c`。不影响查询、历史、键盘或 a11y。下一项为 FB-P6-047。
- **FB-P6-047 CLOSED（最新包复核）：** Desktop sidebar 的 `new-chat` 已从 `plus` 改用已存在的 Lucide 文档＋斜笔 glyph；Android 使用同一轮廓的本地 vector。AOSP 最新正式签名 Debug 截图确认其在既有橙色胶囊内与“新建本地对话”整体居中；Desktop strict-signed `.app` 完整退出重开 AX 仍为可访问“新对话”按钮。未点击创建，故无会话写入。下一项为 FB-P6-049。
- **FB-P6-049 CLOSED（最新包复核）：** Desktop placeholder 为精确“回复 南枫AI”，aria-label 仍为“输入内容”；Android 空草稿截图显示同一精确 hint，并显式保留“会话草稿”语义标签。AOSP 最新正式签名 Debug install-r、force-stop/restart 后核对，APK SHA-256 `cddef5404a9587dac77e1663850b4490d652fb1cbca299cf4071a9eacd70a8ea`。044 继续暂停；045 和 040–043 仍待各自最新包复核。

### 新线程安全检查点（2026-08-14）

- **当前可见反馈优先（进行中，不是项目暂停）：** 用户要求先完成所有已给 UI 反馈，后续总蓝图仅在此清单逐项实证后恢复。最新源码已移除 Android 正常 transcript 中的 fixture/重试/换模型/尝试历史工程控制；Android/ Desktop 侧栏移除冗余搜索说明和第二搜索按钮，底部改为紧凑设置图标＋既有方框斜笔“新对话”同一行浮层。AOSP 正式签名 Debug 实际 install-r/force-stop/restart 已确认 Android 抽屉；Desktop 当前仅 Node/static build，`.app` 真交互未关闭。用户授权的模拟器旧验收记录中，仅一条可见“本地开发会话”已通过正常长按→删除→移入回收站→重启流程处理；不触碰 Desktop/导入会话。FB-P6-050 已实施：Desktop 轨道与 Android ≥600dp 内屏轨道只驱动既有 scroll owner，AOSP 2248×2480 近似内屏已见语义节点；OPPO/折叠连续性未验。不得用该近似替代 OPPO。

- `context_gate.py` 已返回 **HANDOFF**（context 88.7%、effective tokens 986,436），指定交接卡为 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/2026-08-14-nanfeng-ai-fb-p6-039-ui-checkpoint.md`。交接是线程卫生门，不是项目暂停；新线程先读本文件、交接卡和当前源码，以源码为唯一事实源。
- **FB-P6-039 已 CLOSED：** Desktop `messageList` 和 Android `MessageBubble` 都将 attachment preview group 从文字 surface 拆为 sibling；USER 右侧、Assistant 左侧，mixed 不删除 USER 文本 bubble 或 Assistant 开放正文，message tools/metadata 仍在整条消息外，附件 hover/focus/long-press owner 保留。Desktop Node 52/52、Android `P6DConversationRowAccessibilityContractsTest`、最新 ad-hoc signed Desktop `.app` AX + 完整退出重开、AOSP `emulator-5554` Debug `install -r` + force-stop/restart 截图已通过。详细证据见 `P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_EVIDENCE.md`；AOSP 不是 OPPO 证据。
- **FB-P6-040 历史 CLOSED 已被当前 Desktop 回归覆盖：** Android 的 48dp hit target / 38.4dp capsule / 16.8sp label 既有验收仍有效；Desktop 40px hit target / 32px capsule / 12px label 的历史验收不再代表当前可见状态。Desktop 已修为模型与发送键同属右侧 action group、模型紧贴左边，且将突兀白色描边改为紧凑中性胶囊；Node 64/64、lint/typecheck、重签名包完成，但未接管用户现有窗口，真实重开/readback 前仍是实施中。无 Key/HTTP/Provider；AOSP 不是 OPPO。
- **FB-P6-041 已 CLOSED：** 双端仅缩 add/idle-send glyph 到既有尺寸的 0.8，并使成熟纸飞机 glyph 内部逆时针 90°朝上；Desktop add/send 为 20.8px/20px，Android 为 19.2dp/18dp。40px/48dp hit、send surface、focus/ripple、P6-040 model capsule、真实 stop 与业务 owner 均未改。Desktop latest ad-hoc `.app` 完整退出/重开 composer 读回，Android AOSP API 35 signed Debug `install -r`/force-stop/cold start/base hash 均通过；完整证据在 `P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_EVIDENCE.md`。AOSP 非 OPPO；下一唯一实施项为 FB-P6-042。
- **FB-P6-042 已 CLOSED：** Desktop 顶栏已移除重复 Settings，只保留对话/工作和既有 Ghost；Settings 仍唯一在 sidebar footer。Android header 本来无 Settings，专用 semantics 合同锁定其仅有菜单/对话-工作/Ghost、Settings 仍在 drawer footer。latest Desktop ad-hoc full-exit/reopen AX 与 AOSP signed Debug force-stop/cold-start readback 均通过；完整证据在 `P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_EVIDENCE.md`。AOSP 非 OPPO；下一唯一实施项为 FB-P6-043。
- **下一条安全命令：** `cd '/Users/nanzhufeng/Documents/工具开发/Nanfeng_AI' && rg -n -m 30 'chat-composer-icon|ComposerControlGlyphSize|ComposerSendGlyphSize' desktop/src/chat-shell.mjs desktop/src/chat-shell.css app/src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt`

- **FB-P6-044 受约束阻断检查点（已交接，未 CLOSED）：** Android `ConversationTranscriptPresentation` 的 `workDurationLabel` 只读同一 assistant Invocation 的 persisted `TaskRun` 或 Runtime checkpoint 正边界，`ConversationWorkspace` 将它置于 assistant open body 之前；Desktop `assistantWorkDuration` 只读 assistant `run/import` boundary，`chat-message-work-duration` 位于正文之前。两端都不从 message `createdAt`、当前模型或未知值推断。专项源码核查已确认：ChatGPT 导入的 Android/Deskstop message schema 都只有文本、创建时间与 importedModel，没有 duration；Desktop 没有对话 `run/import` 写入 owner；Android 可见 `LOCAL_DETERMINISTIC_FIXTURE` 所有完成事件同刻，且按合同不能当真实模型/导入。故无需也不得以该 fixture 做“正值”双端 UI；现有 AOSP/ Desktop missing/zero omit→restart/readback 仍有效。下一安全动作仅能是接收一份现有 Adapter 可保真的真实 duration 来源，或用户明确授权独立真实模型/导入 Adapter；此前不得伪造、不得进入 045，AOSP 非 OPPO。`context_gate.py` 本轮为 HANDOFF，交接卡：`/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-14T15-26-54-019fff2a-ad04-7c62-abb9-f1237d0e079f.md`。

- **FB-P6-023/024/025 与 P6-G 本地 UI 检查点（已完成，不是项目终点）：** Android 的真实 `adb shell input` UI 路径已完成 drawer/search/IME、Settings、Chat→Work current-scope、ordinary conversation 手动 fixture→Auto、global FAST→Auto、TEMP 零泄漏 force-stop/restart；Desktop 最新唯一 ad-hoc `.app` 已完成宽/窄窗 Chat→Work scope、drawer、回底/Composer、Settings fixture、ordinary conversation 手动 fixture→Auto 的关闭→重开读回。fixture 是 app-private `LOCAL` catalog item，不是 Provider。`design-qa.md` 已在全部 P0–P2 关闭后标 `passed`。

- **FB-P6-025 已与 023/024 合并验收：** 南枫 AI 是意图与对象驱动的交代入口，不是工程模块工具箱。Chat/Work 已实际只投影当前会话对象 scope，不再把工程模块提升为日常入口；根壳、drawer、Composer 与 Settings 均维持这一边界。权威正文为 `CHAT_FIRST_INTENT_ORGANIZATION_CONTRACT.md`；不回滚既有 P6-G owner，也不授权 Key、HTTP、Provider、Agent 或 tool execution。

- **Android 最新真实检查点（已完成本地 UI 门）：** `emulator-5554` 的正式签名 Debug 已 `install -r` 后 force-stop→冷启动到 `NanfengAiActivity`；本地与回拉 `base.apk` SHA-256 同为 `abd505f98c6adc97bcdded7d100af3a0890093da2d399788cdee05e5908e59e8`。Computer Use 不能附着 emulator，故使用真实 ADB 本机触控/键盘通道并明确记录，不以静态 UI dump/screenshot 冒充交互。

- **本轮自动/包检查（本地 UI 门已退出，仍不构成交付）：** Release 的旧 `mergeReleaseJavaResource` 表象已收敛为 JBR CodeCache 耗尽；在 `gradle.properties` 固化 `-XX:ReservedCodeCacheSize=320m`、单 worker 下，Release 实际执行并通过。Android 283 JVM tests、`lintDebug`、Debug assemble 均通过；Debug 为 v2/v3 与正式证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。Desktop Node lint/42 tests/static build 与 Tauri bundle 通过；bundle 后以 ad-hoc 重签，`codesign --verify --deep --strict` 通过，最终 executable SHA-256 `091c855fc608048f46b075292e30b44a026c99d2bef26a32d034d646d6d5fab2`（非 Developer ID/notarized/release）。详情见 `FB_P6_023_024_025_P6G_UNIFIED_SHELL_EVIDENCE.md`。

- **本轮阶段交接（2026-08-14）：** `context_gate.py` 返回 `HANDOFF`（context 88.7%、effective tokens 599,588），交接卡为 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-14T08-49-21-019ffdbe-b474-71a2-bec5-83441d91566c.md`。已按总蓝图创建可见任务 `019ffde4-bf36-7051-bde9-309e1889262c`，唯一继续 ChatGPT export JSON Adapter；HANDOFF 是线程卫生门，不是项目暂停或 Provider 授权。

- **P6-F Core、P6-F2-A~E（Search、Image、PDF、Video、Audio/Generic File）与 P6-G 本地基础均已完成。** 下一唯一阶段是 ChatGPT export JSON Adapter；Provider、账号同步、OPPO 与发布仍未授权/实施，不得把局部阶段完成写成 P6、P10-A 或总项目完成。
- **P6-H ChatGPT export JSON Adapter（非 OPPO 退出门已关闭，2026-08-14）：** 严格 parser、Android Schema 24→25、Desktop SQLite 12→13、两端 app-private copy/专属任务机/原子 Conversation+Message Tree+provenance+receipt、独立 Settings Center、transcript/附件/图标/UI 反馈均已按 `P6H_CHATGPT_EXPORT_JSON_ADAPTER_EVIDENCE.md` 逐项审计关闭。Desktop 分享 P0 已修为本机安全正文复制并用最新 `.app` 实测；不外发。FB-P6-033 按当前授权通过，Dock AX 无可用 target 已记录，OPPO 仍不操作且不是本阶段非 OPPO 阻断门。context gate 已执行并已创建 Claude 后续任务；P6-H 不授权 Provider、账号同步、Key/HTTP、OPPO 或发布。
- **FB-P6-033 当前证据：** master/static audit、Desktop Node/Rust/bundle/ad-hoc strict sign、ICNS byte-identical bundle/Finder 表面、Android JVM/lint/Debug+Release+Acceptance、v2/v3 正式证书和 `install -r`/base hash 已通过。AOSP emulator 抽屉仅作资源近似截图；Dock 的系统 AX 通道两次超时并无 discoverable target，按用户本轮判断以 Finder+embedded ICNS 完成本轮 Desktop 门；OPPO OEM Launcher 仍是后续未授权硬件门，详见 `LAUNCHER_ICON_DELIVERY.md`。
- **阶段切换（2026-08-14）：** `context_gate.py` 返回 `HANDOFF`（context 88.9%、effective tokens 2,737,014）；已按长期授权创建并启动唯一可见任务 `019ffeab-afec-7e02-9f8a-d82ac2b64f35`，进入 Claude export JSON Adapter。新任务必须先读本文件、总蓝图与 P6-H 最终证据；P6-H 不因 OPPO 未授权而回滚，也不授权 Key/HTTP/Provider/同步/Agent/发布。
- **P6-G 本地 UI 已退出；不等于真实模型服务。** Android 的 revision 化本地 `P6GModelSelectionOwner/Storage` 与 Desktop SQLite migration 12/typed Rust owner/Tauri commands/最小 ACL 已完成 catalog、global default、ordinary-conversation override 与安全 route metadata；本轮的真实双端 UI/restart/readback 已由顶部当前状态及 `FB_P6_023_024_025_P6G_UNIFIED_SHELL_EVIDENCE.md` 覆盖。不得将本地 fixture 或自动路由验证升级为 Key、HTTP、Provider、OPPO 或发布授权。
- **`FB-P6-024` 再锁 transcript 硬门，仍是新统一壳层的前置而非已实现 UI。** Assistant/南枫 AI 消息无气泡并使用主内容列，USER 仅右对齐暖橙浅色 max-width 气泡；每条消息的日期/时间、思考时长、真实 model metadata、来源与动作必须归属正确消息，未知不猜/不倒填历史。仅离底时显示 Composer 上方“到最新消息”圆形按钮，平滑回底并恢复输入、到底即隐藏，且固定 Composer 不得遮挡内容。Desktop/Android 同语义，只调宽度/密度；这条门与前三张参考图一起由新统一壳层任务接力，旧壳层不得实现或验收。
- **本轮阶段交接（2026-08-14）：** `context_gate.py` 已返回 `HANDOFF`（context 75.3%），交接卡为 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-14T08-36-09-019ffdb2-9ee3-7b93-a2da-2feecfe1b400.md`。已按长期授权创建可见任务 `019ffdbe-b474-71a2-bec5-83441d91566c`，只继续 Android 实际交互与 Desktop 窄窗/最终 QA 缺口；不得把本节已关闭的构建/签名链或局部可见性升级为阶段完成。
- 本轮结构纠偏后 `context_gate.py` 返回 **HANDOFF**（context 87.7%、effective tokens 305,416）：交接卡为 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-14T07-30-51-019ffd76-d7d9-7cb1-a4e7-6a82807050f9.md`。下一任务只能先完成 `FB-P6-023` 统一壳层合同与双端实现；不得继续旧壳层 P6-G UI、视觉验收、正式包或完成声明。
- P6-F2-A 最终证据为 `P6F2A_LOCAL_SEARCH_INDEX_EVIDENCE.md`；P6-F2-B 最终证据为 `P6F2B_IMAGE_PREVIEW_ADAPTER_EVIDENCE.md`：双端 owner-local/private-copy 缩略图与唯一原图预览、失败隔离、真实 UI/重启读回、正式签名/install/base hash 均完成。TEMP/Key/path/URI/隐藏过程零进入；预览不等于 Provider 外发。
- P6-E 最终证据正文为 `P6E_TEMPORARY_CONVERSATION_EVIDENCE.md`：Desktop 独立 acceptance `.app` 已通过真实“设置 → 数据与存储 → 会话管理”固定卡运行、完整退出/重启 receipt 读回与 SQLite ordinary-surface 全零；Android 独立 `.p6eacceptance` 正式签名包已通过同一路径的 23h59 保留/24h 清理、force-stop/restart 与 Room 全零读回。正式包默认均不暴露时间篡改入口。
- TEMP 图片/文件 private-copy→消息→重启读回、Android model override 真 UI/重启读回、双端 overlay 真实抽样、当前 Desktop Finder/Dock 与 Android Launcher 模拟器近似记录均已完成。Launcher 记录不构成 OPPO Find N5 或最终硬件图标结论；未操作 OPPO，未再修改图标。
- 最终自动门：Desktop lint/typecheck/31 Node tests/build、Rust fmt/clippy/37 tests、普通与 acceptance Tauri bundle 全绿；Android 261 tests、fresh `lintDebug`、Acceptance/Debug/Release assemble 全绿。Android 三包均 v2/v3 与正式证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`，Debug/Acceptance 安装后回拉 base hash 分别与本地产物一致。完整 hash 与路径见 P6-E evidence。
- P6-F 最终证据正文为 `P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_EVIDENCE.md`：Android transcript/copy/explicit-confirmation `ACTION_SEND`、typed branch/restart readback，以及 Desktop transcript/typed branch/restart readback 已完成。Android 本轮实际点击复制后，clipboard 回读为安全 `text/plain` fixture；force-stop/cold-start 仍恢复入口。Desktop 原生 share 无安全 owner，按用户明确许可保持隐藏并如实记录；没有伪分享或外发替代。P6-F 未读 Key、未发 HTTP、未操作 OPPO、未改图标或发布。
- P6-F2-D 最终证据为 `P6F2D_VIDEO_PREVIEW_ADAPTER_EVIDENCE.md`；P6-F2-E 最终证据为 `P6F2E_AUDIO_AND_GENERIC_FILE_ADAPTER_EVIDENCE.md`。P6-F2-E 已完成双端受控 Audio/Generic owner、Desktop 原生 picker / Android DocumentsUI 私有复制、显式动作与重启读回、正式签名/hash 冻结。**这只结束 P6-F2-E，不结束 P6 或总项目；后续唯一阶段是 P6-G。**
- 本轮 `context_gate.py` 返回 **HANDOFF**（context 93.6%、effective tokens 632,309），交接卡为 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-14T06-29-20-019ffd3e-8332-7fb3-b971-ad0934f14d12.md`。已创建可见精确任务 `019ffd6b-d678-7031-95cb-62e4d733ab34`，只继续 P6-G 本地 Model Selection / Auto Router；HANDOFF 是线程卫生门，不是项目暂停。
- 阶段切换 `context_gate.py` 于 2026-08-14 返回 **HANDOFF**（context 94.2%、effective tokens 1,450,621），交接卡为 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-14T00-55-47-019ffc0d-22de-7f63-aa8a-d63a0b40fb94.md`。已按用户长期授权创建可见任务 `019ffc52-1e85-7b00-acc4-7f08266ad152`，由该任务开始 P6-F Core 并继续总蓝图；HANDOFF 只是线程卫生门，不是项目暂停。
- 本文件后续保留的 P6-E “进行中/未完成”段落是按日期排列的历史检查点，已被本节与最终 P6-E evidence 覆盖，不能再当作当前状态。

## 本轮反馈事实索引（2026-08-13）

- 唯一追踪正文：`PRODUCT_FEEDBACK_DECISION_LEDGER.md`；现有 `FB-P6-001` 至 `FB-P6-024`。`FB-P6-019` Prompt Cache、`FB-P6-020` 用量/成本/余额台账与 `FB-P6-022` 统一搜索/历史/本地内容预览均为**后续合同登记**，未实现、未读 Key、未发 HTTP。`FB-P6-021` 收口 Desktop 约 20–22px 可见 glyph/40px 与 Android 24dp/48dp composer 图标/点击区，TEMP 模型只为本地安全标识；`FB-P6-023` 将新统一 chat-first shell 设为 P6-G UI 与真实 UI 验收的前置硬门，`FB-P6-024` 将 transcript 投影、逐条 metadata、离底回底按钮与 Composer 遮挡保护追加为同一新壳层硬门。运行 `python3 scripts/check_feedback_ledger.py` 验证索引结构。
- 真正架构/产品决策只在 `decision-log.md` 简要引用 ledger；规则正文在 P6-E/P6-F/P6-F2/P6-G 合同，总路线在 `MASTER_DEVELOPMENT_BLUEPRINT.md`。launcher 图标已按 FB-P6-018 从原始连续白底母版重做为主体 1.50×（scale120 保留回滚），像素与 Android 静态链已验；当前 Launcher/Dock/Finder 表面已记录，但 emulator 不构成最终硬件视觉结论。不读 Key、不发 HTTP/图片外发。
- Android 真实设备与折叠验收唯一正文为 `ANDROID_TARGET_DEVICE_PROFILE.md`：OPPO Find N5 外屏/内屏/折叠切换仍是后续独立硬件门；当前只有模拟器近似证据，未获授权不得操作 OPPO。该硬件门不回滚已由用户限定为非 OPPO 范围的 P6-E 本地退出结论。

## 已完成阶段：P6-F2-D Video Preview Adapter（2026-08-14）

- P6-F 已达成全部双端退出门，最终合同与证据分别为 `P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_CONTRACT.md`、`P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_EVIDENCE.md`。P6-F2-A/B/C/D/E 已分别完成；P6-F2 合同为 `P6F2_UNIFIED_SEARCH_HISTORY_AND_LOCAL_CONTENT_PREVIEW_CONTRACT.md`。完整顺序仍为 P6-F2-A~E → P6-G → ChatGPT JSON → Claude JSON → 南枫知识库 JSON → exact cache → 蓝图其余路线。
- P6-F2 仍必须逐 Adapter 交付 Search Index、Image、PDF、Video、Audio/Generic File；不会把前一 Adapter 的“已完成”外推。它不读取 Key、不发 Provider HTTP、不改图标；TEMP 继续不参与 share/branch/export。
- P6-F2 来自南枫知识库私有源码 `main@cc56a3d13375eeb9a7d4e772de1a59388a66e050` 的只读参考审计：复用六类分类搜索、本地规范化去重历史、会话行右侧日期、图片缩略图/视频代表帧/PDF与文件受控预览的方法；明确不复制知识库三栏工作台、主题业务、五皮肤、大资料时间线或固定视口。搜索仍是单一入口并复用主对话面板，预览绝不等于 Provider 外发。
- P6-F2-A/B/C/D/E 已分别退出；P6-F2-E 证据见 `P6F2E_AUDIO_AND_GENERIC_FILE_ADAPTER_EVIDENCE.md`。下一阶段是 P6-G，不能将 preview Adapter 完成外推为 Provider/Key/HTTP、OPPO 或发布授权。

## P6-E 历史实施记录（已于 2026-08-14 完成）

### 2026-08-14 历史交接安全检查点（当时未完成；现已由最终退出证据覆盖）

- Android 已完成但尚未真实验收的 acceptance-only 维护骨架：`app` 的 `acceptance` build type 使用独立 `.p6eacceptance` applicationId、正式 signing 和 `BuildConfig.P6E_ACCEPTANCE=true`；Debug/Release 固定 false。仅 acceptance 包可在既有“设置 → 数据与存储 → 会话管理”显示 P6-E 固定验收卡，生产 UI 不出现时间编辑入口。
- `P6ETemporaryMaintenanceAcceptanceHarness` 不接收时间/路径/内容参数：使用固定 Clock 和既有 TEMP owner/private attachment owner 写入 TEMP 消息、`local.p6e-acceptance`、private-copy 文本附件，验证 23h59 保留与 24h 清理；卡仅保存五个布尔回执，以便 acceptance app 重启后显示。它尚未通过 acceptance APK 真安装、UI、force-stop/restart/SQLite readback 验收，也尚无专用 harness JVM 用例，不能视为 P6-E exit gate。
- Desktop acceptance-only 已实现：精确 env marker `NANFENG_AI_P6E_ACCEPTANCE=1` 才选择固定 `/tmp/nanfeng-ai-p6e-acceptance` app-private root；普通 app-data root 与 UI 不变。无参数 Tauri status/run command 与仅 acceptance 可见的 Settings card 使用固定 owner Clock，通过同一 `prune_temporary_at` 写入/重开读取六布尔 receipt；Rust reopen test 已断言 23h59 保留、24h record/last private asset 清理和普通 workspace=0。当前 macOS 存在用户先前启动的同名实例，自动化不能可靠锁定新 acceptance 窗口，所以该 card 的独立 `.app` UI/restart 截图仍未验收，不能混用旧窗口事实。
- Android acceptance 已完成真实路径：独立 `.p6eacceptance` 正式签名 APK（v2/v3、证书 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`）以 `install -r` 安装，不覆盖原 `com.nanzhufeng.ai` 数据；通过“设置→数据与存储→会话管理”固定卡运行并 force-stop/restart 回读 23h59/24h、附件、消息、model override 五项 true。Room app-private readback 的 ordinary Conversation/Project/Knowledge/Memory 与 TEMP 三张表均为 0；专用 harness test 已通过。截图与 APK hash 见 `P6E_TEMPORARY_CONVERSATION_EVIDENCE.md`。
- 已通过：Android acceptance assemble、定向 P6-E/harness JVM、正式 Debug/Release assemble、Debug `install -r` 与 device `base.apk` hash 冻结；Desktop Node lint/typecheck/30 tests/build，Rust fmt/clippy/full 37 tests及 Tauri `.app` bundle。正式 Debug/Release 与回拉 base hash见 P6-E evidence；Android `lintDebug` 单 worker JBR 重跑在 lint model 后未返回最终结果，必须在下一轮单独重试且不得标绿。随后继续 overlay/Dock/Launcher gate，不得进入 P6-F/P6-F2、Provider、Key、HTTP、图片外发、OPPO、发布或图标修改。
- `context_gate.py`（2026-08-14）返回 **HANDOFF**：context 92.3%、effective tokens 1,222,519。已生成交接卡 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/p6e-checkpoint-2026-08-14.md`；按门禁当前任务停止扩展并切换新任务继续。

### 本轮增量（2026-08-13；P6-E 未完成）

- 已补双端 TEMP `model override` 的本地恢复字段与 UI：Android 仅写入 temporary recovery owner；Desktop `.app` 已实测 Ghost → 临时模型标识 → 保存 `local.fixture-v1` → 再打开读回。该字段不选择模型、不读 Key、不发 Provider/HTTP；Desktop 实测发现其 ID 校验没有允许 Android 已允许的 `.`，已统一为 ASCII 字母数字及 `._:-`，并补 Rust/Kotlin/Node 回归。
- 已按 FB-P6-021 逐项最终值将 composer 切为既有线性 SVG/Material 语义图标：＋/模型保持 Desktop 26px SVG box 中约 20–22px visible glyph/40px hit target、Android 24dp/48dp；发送使用独立 token，surface 固定 Desktop 30px/Android 36dp，arrow/未来真实 stop 保持 Desktop 25px/Android 22.5dp，透明外层命中区仍为 40px/48dp。TEMP 模型入口展示 `AutoAwesome`/模型语义与本地 override 可访问名称，发送 idle 保持向上箭头。无 composer 对 launcher 图标的改动；本轮另按 FB-P6-018 处理 launcher 主体 1.50×，真实新包与 emulator 可见/focus/disabled/click 复核仍待完成。
- 本轮自动证据：Desktop Node（含 temporary）29/0、typecheck/lint/static build；Rust temporary 定向测试、fmt、clippy `-D warnings` 与 Tauri `.app` bundle。Android P6-E/Room/UI 定向 JVM 通过。当前 Android Debug/Release SHA-256 分别为 `86650b6b031a0bf743b2957d032cefe51d403a33966ee5bbebbf24e4eb63f223` / `27c85f430b40d3d05643ed1b0211c455da363c5e8c0cafafa697650ca4c4e49a`，均 v2/v3、正式证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`；已 `install -r` 并回拉 `docs/evidence/p6e/android-installed-base-20260813-p6e-model.apk`，hash 与 Debug 一致。最新 Desktop `.app` 已 ad-hoc strict verify，executable SHA-256 `d2ed178ae6e600cbd5d9fbd238f4b9ff4d628abb70259f5f42e12d13e5050d2a`，不是 Developer ID/notarized/发布包。
- 仍缺且必须逐项补齐：双端以应用实际 maintenance 入口、app-private 受控 Clock fixture 证明 23h59 保留/24h 清理并重启读回、普通会话零泄漏；双端新包 TEMP 图片与文件 private-copy→消息→重启读回；Android TEMP model override 真 UI/重启读回；全类 nested/menu/confirmation overlay 的外点关闭（危险确认仅按合同例外）；当前 Desktop Dock/Finder 与 Android Launcher 表面证据；本轮新源码的全量 Android JVM/lint 及完整 Rust 回归最终记录。P6-F 尚未开始。
- **上下文门（2026-08-13）**：`context_gate.py` 返回 `HANDOFF`（context 92.6%、effective tokens 314787）；自动交接卡为 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-13T23-23-10-019ffbb8-5905-79e2-9c61-88f03cfdf7b6.md`。按门禁立即停止本阶段扩展；下一任务只从本文件、交接卡、P6-E 合同和当前源码继续，不得进入 P6-F 或任何 Provider/Key/HTTP、同步、OPPO、发布或图标修改。

- **P6-E 收口 IA 硬规则（已登记，实施/验收未完成）**：双端侧栏/drawer 移除 archive/recycle，迁至真实“设置 → 数据与存储 → 会话管理”的既有 archived/soft-delete owner 投影；workspace 管理迁“设置 → 工作区管理”，本地受控记录迁“设置 → 数据与隐私”。对话与工作都只保留单一 placeholder 为“搜索”的紧凑搜索；work 侧栏固定为 workspace selector → Project/Knowledge/Memory → 搜索 → pinned/recent，右侧仍为当前 workspace conversation。不得隐藏无去处或改变 lifecycle。临时入口改为 Lucide Ghost（MIT）共享语义，accessibility 名为“临时聊天”。
- 正式合同为 `P6E_TEMPORARY_CONVERSATION_DUAL_PLATFORM_CONTRACT.md`，已替代草案。**附件不再错误禁用**：临时聊天必须复用 P6-D2 Desktop native picker 与 Android Photo Picker/DocumentsUI/private-copy，但 attachment scope 固定为 `TEMPORARY_SESSION`，不能挂入普通 Conversation/draft。
- 当前源码：Desktop SQLite `user_version=7` 独立 `desktop_temporary_recovery` / `desktop_temporary_attachments`，Android 使用独立 temporary recovery owner；均不写 workspace exchange、普通 Conversation、搜索、导出、项目、Knowledge、Memory、同步或缓存。两端右上 Ghost 直接切换 NORMAL↔TEMP，返回 NORMAL 保留恢复记录，24h 只从有意义 mutation 计算；临时附件复用既有 private-copy picker 但保持 `TEMPORARY_SESSION` 隔离。旧“清除/退出确认”fixture 不能作为最终证据。
- 当前自动证据：Desktop Node 28/0、typecheck/lint/static build 已通过；Android P6-D/P6-E 定向 JVM（含 Composer/overlay/设置会话管理）已通过。**这不是 P6-E 完成证据。**完整 Android JVM/lint/正式 Debug+Release 签名及 install/readback、本轮 Desktop Rust/Tauri `.app`、双端 Ghost/TTL/IA/overlay/composer 真 UI 仍需从新包复核。
- **本轮新增 P6-E 真实复核（仍未使阶段完成）**：Android `emulator-5554` 的正式签名 Debug 已仅以 `install -r` 覆盖；冷启动停留在 `NanfengAiActivity`，NORMAL→TEMP 的 `content-desc=临时聊天` 可达且无弹窗，TEMP 草稿 `P6_TEMP_DRAFTE` 与 NORMAL 草稿 `p6d2-final-draft` 相互隔离，切回与 force-stop/cold start 后均可恢复 TEMP。TEMP XML 明示“最长 24 小时；不会进入普通历史、搜索、项目或导出”；app-private Room 的最小审计为 temporary recovery 1、临时消息 1、临时附件 0。Android 设置页实际有“数据与存储 → 会话管理”，drawer XML 无 archive/recycle，scrim 外点与系统 Back 均关闭 drawer；composer idle/focus 截图保存于 `docs/evidence/p6e/`。本轮完整 `:app:testDebugUnitTest` 与 `:app:lintDebug` 在单 worker、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` 下成功；未加该隔离时 Android Studio JBR test executor SIGSEGV（`hs_err_pid30107.log`），这是工具故障而非绿色结果。Debug SHA-256 `ae899d8078964b6b0bae7b5d9f0aea91390897cc7a69dbcd6b60838e134e2205`、Release `3078c0064d73b288c9fec10cbd59bcd90d4e825a9a8a40dbf390c0314d38df78`，两者均 v2/v3 与正式证书 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`；回拉 `docs/evidence/p6e/android-installed-base-20260813.apk` hash 与 Debug 一致。
- Desktop 最新唯一进程 PID 30803 来自 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，严格 ad-hoc 验签通过、可执行 SHA-256 `3b24d6ebbae0e49b4a1d6e01e93e1c50e6283dafc8d811927b26f8b96108adab`。真实 AX 操作确认 Ghost 直切无 modal、NORMAL/TEMP 草稿隔离且再入恢复、设置“数据与存储 → 会话管理”、sidebar 无 archive/recycle section（行级“归档会话”仍保留）、短入口可见标签由源码/静态合同固定为“模型/账号/数据/设置”而完整 a11y 名保留、profile overlay 外点/Escape 关闭并还焦 trigger。仍未把这组抽样证据写成 P6-E complete：TEMP 真附件/model override 的双端 UI 取证、全类 nested/confirmation overlay 抽样、Desktop/Android 受控 Clock 通过**应用 UI 维护路径**的 23h59/24h 证据，以及新 Dock/Launcher 表面截图仍是缺口。
- **上下文门（2026-08-13）**：`context_gate.py` 对当前 Codex session 返回 `HANDOFF`（context 91.2%、effective tokens 991869），自动交接卡为 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-13T22-20-12-019ffb7e-b447-7cf0-8bd0-0e074b92c416.md`。下一任务必须从本文件、该交接卡与 P6-E 合同继续，先补上条明确缺口；不得进入 P6-F、Provider、Key、HTTP、OPPO 或图标修改。
- 本轮新图标包证据：Android 单 worker 全量 JVM 83/0/0、`lintDebug`、Debug/Release 均通过；Debug/Release hash 分别为 `275f81464a8ad3d20d63ff67ed362211f8c5506b5953a3aa8618d2c7aef544b8` / `e6f7b440b1c9287332fb8f257e888b7d8dc8fcfd2a0e8d69f405ecbe5721a55a`，v2/v3 与正式证书 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5` 通过；`emulator-5554 install -r` 后拉回 `base.apk` 与 Debug hash 一致。Desktop Rust fmt、36 tests、clippy `-D warnings`、Tauri app bundle 通过，重签后的 `.app` strict ad-hoc verify 通过，executable hash `3b24d6ebbae0e49b4a1d6e01e93e1c50e6283dafc8d811927b26f8b96108adab`。这仅证明包/签名/字节，不代替 Launcher/Dock 或 P6-E 真实 UI。
- 不得声称双端完成，不得进入 Provider、Key、HTTP、同步、OPPO 或真实用户数据。图标仅完成新母版/静态资源链，真实 Launcher/Dock 表面验收仍待。

## 当前唯一实施项：P6-D 双端侧栏信息架构（进行中）

### P6-D2 Cross-platform Composer Attachment Adapter（完成；2026-08-13）

- 合同：`P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md`。Android 已从 Photo Picker 扩展到 DocumentsUI；Desktop `＋` 已从诚实占位改为 native dialog → Rust private copy。允许 JPG/PNG/WebP/PDF/TXT/Markdown/JSON/CSV；MIME+magic、20 MB 单项/4 项/40 MB、SHA-256 去重、安全 metadata、消息引用均为双端链路，不存 URI/path/Key，不发 HTTP。**24h orphan GC 仅属于 Desktop attachment owner；Android 本阶段为 GC N/A，没有 GC 承诺、实现或缺陷。**
- 视觉前置已补：Android `chatRoleVisual` 和 Desktop CSS 以共享 orange-soft 仅映射 USER bubble/attachment chip；ASSISTANT/SYSTEM/TOOL/ERROR 为独立中性/语义 surface，code/quote 仍中性。新增 Android contrast/role contract 与 Desktop Node role mapping contract。此前 P6-D 自动回归不等于真实视觉回归。
- 当前自动证据：Android `compileDebugKotlin`、P6-D accessibility/role contrast contract、P3-G regression、P6-D2 Room SHA-256 dedup（写入 → repository 重建 → hash 回读）已通过；`lintDebug` 为 0 errors / 24 warnings。Desktop typecheck/lint/25 Node tests/static build，Rust fmt/34 tests/clippy `-D warnings` 已通过。新增 `attachment_maintenance_uses_owner_metadata_clock_and_is_idempotent`：同一 Rust owner 以显式 Clock 覆盖 referenced 保留、fresh orphan 保留、expired orphan/expired staging 清除、最后引用解除后 23h59m/24h、metadata 不含 source path、幂等和 reopen；SQLite user_version=6。构建回归继续保证 `chat-shell.mjs → icon-source.mjs` 静态依赖复制到 `dist`。
- 当前真实证据（只覆盖实际观察到的项目）：最新 ad-hoc 签名 `.app` 已从新进程启动并显示 chat shell（非旧包）；native Open panel 成功选择 PNG 与 Markdown，Rust 私有复制后 composer 显示两个 orange-soft chip，发送为纯本地 message，完整退出重启仍回读两项引用。`emulator-5554` 的同签名 Debug `0.3.0-p10a` 已安装，并回拉 `base.apk` 与本地 SHA-256 `1ee0a3c39f96abe3bef5f203b05cc692c23da45b984d1dba05f1bddd06c687bf` 一致（v2/v3，证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`）。DocumentsUI Markdown 与 Photo Picker PNG 的既有 private-copy → send → force-stop/cold-start readback 仍在；本轮实际复核了橙色 USER bubble/attachment chip、1dp orange composer、可发送 orange icon、抽屉底部 orange“新建本地对话”，以及用户文本+Markdown 附件本地发送后 force-stop readback。最新截图：`/tmp/nanfeng-ai-p6d2-android-final-composer-user.png`（SHA-256 `f658e6d6e8e36b272d1880533acd7d29f3c4bb27990297ab7494701382d74866`）和 `/tmp/nanfeng-ai-p6d2-android-final-navigation.png`（SHA-256 `1618949386340bb61147ffc916dc7d6de28cecc5e22608388c6049d04988ae99`）。对话模式完整菜单的最新截图 `/tmp/nanfeng-ai-p6d2-android-final-conversation-menu.png`（SHA-256 `ae5c935fd30deb8f3c703f28e89f1f66a69814523e2cc508d592ffc712498e5e`）只含置顶、重命名、添加到项目、危险删除；归档/恢复只保留工作模式菜单。
- Desktop 真实 24h 门已完成：最新 ad-hoc strict-verified `.app`（`Identifier=com.nanzhufeng.ai.desktop`、`TeamIdentifier=not set`、executable SHA-256 `f00dc52cc9b5cf446e88ad67a89c7de8265d5e02da1ad1c9d59edfbd5801eeaf`，非 Developer ID/notarized/发布包）在全新隔离 HOME `/tmp/nanfeng-ai-p6d2-app-fixture-20260813` 启动同一 maintenance path。受控 app-private fixture 启动前为 3 assets/1 staging，启动后为 2/0；SQLite 读回 referenced asset `reference_count=1`、fresh orphan `reference_count=0`，已过 24h orphan 与 interrupted staging 均删除；metadata 安全字段中 path token 数为 0。再完整退出/重开后仍为 2/0、SQLite 2/0，asset aggregate SHA-256 `a806aa53a0e4eab2b74fec881bf581cdf95762028c6436316a42f907fb4ea412` 不变。未打开或改动任何长期 workspace、真实用户文件、Key 或网络。`context_gate.py` 为 `OK`。**P6-D2 已完整完成；下一唯一任务是 P6-E 双端临时聊天完整生命周期。**

- 权威合同：`P6D_DESKTOP_SIDEBAR_INFORMATION_ARCHITECTURE_CONTRACT.md`（标题虽沿用 P6-D 文件名，正文已升级为双端合同）。Desktop 与 Android 必须在同一阶段交付“模式功能区 → 置顶区 → 会话或项目内容区”，共享 Conversation `pinned/archived/revision/expectedRevision/undo`、排序、状态名称与持久化语义。归档清除置顶，恢复为活动未置顶；UI 只调用各平台 typed Domain owner，不直写库。
- Desktop：三段侧栏、宽屏可折叠 rail、窄屏 drawer、hover/focus/键盘可达的置顶/取消置顶/归档/恢复，限定可删除的 app-private 合成 workspace；真实 `.app` 的 click/hover/focus/折叠/重启读回必须与 Rust SQLite receipt/revision 一致。
- Android：真实 Compose `ModalNavigationDrawer` / 分层导航，触控 trailing “操作”提供置顶/取消置顶、归档/恢复与明确归档入口；Domain/Room 持久化、Activity/force-stop 重启读回、JVM/Room/UI 可行测试、lint、既有正式证书 Debug/Release、`emulator-5554` 同签名 `install -r` 都是本阶段门。不得清数据、装 test APK、操作 OPPO，且不得以 Desktop 结果替代 Android 验收。
- 布局差异的唯一例外：Desktop“新对话”仍在侧栏顶部功能区；Android 主界面默认 drawer 关闭，左上角固定“打开对话导航”，drawer 内按“模式功能区 → 置顶 → 最近会话/工作内容”滚动，底部安全区上固定“新建本地对话”。选会话/新建自动关闭 drawer；返回/scrim/手势优先关闭 drawer 且不丢文字/图片草稿；重建恢复 mode/会话但默认关闭 drawer。
- 本轮严格不接 Provider/Key/HTTP、同步、跨应用、Windows、OPPO 或图标；不接触既有长期 workspace。

## P6-D 双端侧栏本轮收口（2026-08-13；已达本地真实 UI/重启读回，P6-E 尚未开始）

- **后续视觉微调已开始（P6-E 前置，不改变 P6-D 生命周期）：** Desktop 已引入 `accent-orange` semantic tokens，替换 Composer focus/send/new chat/selected row 的绿色交互；Android `AccentOrange` / `AccentOrangeSoft` 成为 Material primary 与 Composer 1dp focus token，绿色 `BrandGreen` 保留成功语义。双端自动合同覆盖 PushPin/Archive、single 1px/1dp composer outline、12px/12sp row title 与低对比分区 label。尚未完成真实 `.app` / emulator 可见回归，亦尚未开始 temporary domain/recovery；不得把这段自动验证称为 P6-E 完成。

- Android 修复：实际 emulator 验收发现抽屉打开后系统返回会退出整个对话 Dialog，而非优先关闭抽屉。修复落在 `ConversationWorkspaceDialog` 的唯一 Dialog dismiss owner：drawer 打开时只关闭 `drawerState`，否则才关闭工作区；没有触及 Room、Conversation Domain、Key 或网络路径。最终同签名 Debug 覆盖后，cold start → 对话 → 进入本地对话 → 打开 drawer → Back 仍停留在“本地对话”，并回到“打开对话导航 / 当前：会话 / 会话草稿”。
- Android 真实持久化：仅对既有明确标为“本地开发会话”的开发 fixture 执行 置顶 → force-stop/cold start 读回（置顶区与“取消置顶”）→ 归档 → force-stop/cold start 读回（已归档入口）→ 恢复 → force-stop/cold start 读回（活动会话）。drawer 默认关闭、左上固定打开、展开后有“对话 / 工作”、置顶区、内容区、底部“新建本地对话”；未清数据、未装 test APK、未操作 OPPO。
- Android 自动与产物：初始全量 JVM 240 tests / 0 failures / 0 errors；最终 UI 微修后的全量重跑在 Android Studio JBR test executor 触发 SIGABRT（exit 134、无断言失败，`app/hs_err_pid99035.log`），故不把最终全量重跑称为绿色。最终 `lintDebug` 为 0 errors / 24 warnings；Debug 与 Release 分别为 SHA-256 `bc70bd0477b2061e45dd749a472e85bc8f855df9e00ea88c1ef897a6033adf67` / `0fa7ac611caeb2d9a6bbef4bc226d5d80fdf976c92cce299fdf46642f91a0c09`，均为 v2/v3、证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。`emulator-5554` 在 `install -r --user 0` 后 code 51 / `0.3.0-p10a`、`ceDataInode=574936` 不变；回拉 `base.apk` 与最终 Debug hash 一致。
- Desktop 自动与真实 UI：最新 frontend typecheck/lint/static build、Node 19/0、Rust fmt/33 tests/clippy `-D warnings`/check、Tauri app build 均通过。最新 `.app` 对隔离 `P6-D 隔离长列表验证` workspace 实际显示三段 sidebar、行级 hover/focus 操作、固定 Composer 与对话/工作模式；右键实际弹出对话专属菜单“置顶 / 重命名 / 添加到项目 / 删除”。对该隔离会话置顶后完整退出再启动，新进程仍在置顶区且动作为“取消置顶”，证明 Rust SQLite readback；未触及任何长期 workspace。
- **P6-D 最后 UI 密度硬规则（2026-08-13；本轮优先收口）：** 空间拘谨的 Desktop 会话行 hover/focus 快捷区仅图标、必须复用现有图标来源，且每项有 `title`/`aria-label`、键盘焦点和足够热区；Android 行不得常驻“置顶/归档”等动作文字，只保留有 `ContentDescription` 与 48dp 热区的“更多”图标，点击和长按都打开同一完整操作层。Desktop 右键和 Android modal/bottom sheet 才以“图标＋文字”展示操作；置顶/归档状态由分区或小状态图标表达，软删除必须在危险分组、错误色并进入既有确认。此为 UI/无障碍呈现收敛，`Conversation` 生命周期、Room/Rust owner 与写入语义一律不变。恢复后需新增双端自动无障碍合同及真实 UI/restart 证据；不可把本段旧证据误称为这条规则的验收。
- **本轮扩展硬门（Composer、紧凑入口与菜单定位）：** Composer 常驻主操作改为有 `title`/`aria-label` 或 `ContentDescription` 的圆形图标：发送/无效为上箭头，Android 仅在已持久化 `ConversationRuntimeState` 非终态时改为方形 stop，并且只调用既有 typed `stopLocalStream → RuntimeCancelled → reload`；`isSending` 是本地草稿提交 busy，保持 progress，不能伪装可停止 Run。Desktop 当前没有 Run/cancel owner，只有发送箭头，真实 stop 进入未来 Model Execution。`＋`、模型选择、打开/折叠侧栏、row more 同样图标化；voice 和 temporary chat 未实现，不显示；“对话/工作”不图标化。Desktop context menu 以行/title rect 计算下方/上翻/横向 clamp，sidebar scroll/resize/模式切换/选行/Escape/点外关闭或重算，键盘 ContextMenu/Shift+F10 取 focused row rect；Android bottom sheet 保持会话标题。标题收敛为 Desktop 14px medium、Android bodyLarge（约 16sp）单行 ellipsis，置顶不放大。需要新增 anchor、typography、状态矩阵、accessibility 自动合同及新 `.app`/emulator 真实 UI 证据。
- **本轮密度规则证据（2026-08-13）：** Desktop Node 20/0（含 row-only icon、title/aria、Composer 发送箭头、anchor 下方/flip/clamp、scroll/resize/keyboard 与 document-capture Escape/点外关闭合同）、lint/typecheck/static build、Tauri bundle 均通过；最新 `.app` 已 ad-hoc strict verify，可执行 SHA-256 `a87f2662c56bb1a015d98284c6242c2c6fdcbdcf9c626322f0487be56d338244`。重启唯一最新实例后 AX 实测：会话行操作只以名为“置顶会话/归档会话”的图标按钮暴露，Composer 是 disabled 的“发送消息”图标按钮，模型/加号为图标入口；右键行后真实出现“置顶/重命名/添加到项目/删除”完整菜单，Escape 后回到主窗口（菜单关闭）。Android 新 `P6DConversationRowAccessibilityContractsTest` 3/0、compile/lint/Debug/Release 通过；正式 Debug SHA-256 `3a48fa65feb4883ba52ebfe435ebe6de7a419aa9b33bd3cb688302e7a9397df7`、Release `35836454c03bd283ba3ab3dc538e26f2d504e8c999e9bf197df91b7508ae7872`，v2/v3=true、证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`；仅 `emulator-5554` `install -r --user 0` 后 cold start，回拉 `base.apk` hash 与 Debug 相同。实机 UI：行只显示“打开会话操作”更多图标；bottom sheet 显示当前标题“本地开发会话”、图标＋文字操作和红色“危险操作/删除”。未清数据、未读 Key、未发 HTTP、未操作 OPPO；Desktop 没有 Run/cancel owner，故未伪造 stop。
- Desktop 最新开发包：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app` 已重新 ad-hoc 签名并 `codesign --verify --deep --strict` 通过；`Identifier=com.nanzhufeng.ai.desktop`、`TeamIdentifier=not set`，可执行 SHA-256 `9db10f3977c52c45a60f8817c1a4beee80b3e5087ce2b6922f60e301c3a7f51e`。它不是 Developer ID/notarized 或发布包。
- 仍不扩大：P6-E 临时聊天、模型选择、Provider/Key/HTTP、同步、OPPO、Windows、发布均未开始或未验收。P6-D 的历史 macOS GUI 债务仍是可视双窗口 `REVISION_CONFLICT`；不把 Rust 集成测试替代为该 GUI 证据。

## 已授权的唯一后续队列（不可插入当前侧栏验收）

1. **双端临时聊天 P6-E**：正式合同 `P6E_TEMPORARY_CONVERSATION_DUAL_PLATFORM_CONTRACT.md`。当前双端侧栏/Composer ＋完成后立即进入；显式 `ConversationKind.TEMPORARY`、复用 P6-D2 图片/文件 picker/private-copy attachment owner 且 attachment scope 固定为 `TEMPORARY_SESSION`、隔离 app-private recovery、主动清除不进回收站、异常最多 24h、普通历史/搜索/项目/知识/记忆/导出/同步/缓存零泄漏，右上角相同入口与双端 restart/readback 是硬门。
2. **双端 P6-F Conversation Transcript Presentation & Message Actions**：按 `P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_CONTRACT.md` 单独实现与验收。角色 transcript 不用连续色块；AI 左侧/USER 右侧、SYSTEM/TOOL/ERROR 独立语义；时间、来源、真实 elapsed、import/cache/temporary 不得冒充新生成。复制/分支/系统分享均只能接入真实 owner 或独立 Adapter；临时聊天禁用 share/branch/export。图片/文件 inert 呈现，VIDEO 是单独 Adapter 阶段。
3. **双端 Model Selection / Auto Router 本地基础**：使用 `nanfeng-ai-model-service` skill。Composer 仅显示当前模型名或“自动”，附近 popover/listbox 选择当前 conversation；设置页独立管理 Provider、catalog/preset、全局默认与成本/质量策略。会话人工 override > 全局默认 > Auto，override 只影响当前 conversation，改回 Auto 才恢复策略。Auto 作为版本化纯领域合同，依次筛 capability（TEXT/VISION/CODE/TOOL/长上下文）、敏感/egress、registry/配置可用、context limit、known cost/budget，再按复杂度/质量选择合格项中成本最低且延迟可接受者；图片仅 vision；高风险/unknown cost/无候选/能力不足失败关闭或要求确认。仅本地 registry/preset、状态、两端 UI 与 fake tests；policy version/reason/候选拒绝原因入不含 Prompt/Key 的安全 metadata/ledger，和 Invocation 分离。
4. **双端 Conversation Import / Reuse，逐 Adapter 单独验收**：先 ChatGPT export JSON，再 Claude export JSON，再南枫知识库对话/知识 export（只可读其代码/协议，绝不读实际用户数据）。每项在 Desktop/Android 同阶段实现系统文件选择、确认/跳过/恢复、重启读回与导出回读。受控本地解析/私有复制后归一化为真实 Conversation + Message Tree，可搜索、置顶、归档、工作区归属、继续对话和显式 Context；保留 source system、source conversation/message ID 或 opaque handle、adapter/version、import time、content/package hash，明确 marked imported，绝不伪装为 Provider Invocation。禁止上传/执行 HTML/Markdown/tool 指令，或存 path/URI/token/Key；复用 P4-H/L，覆盖 schema/version/size/未知字段/分支/部分失败/中断恢复/幂等/reimport/冲突/rollback/来源撤销和审计。
5. **双端 Exact historical cache reuse**：只有 normalized request hash + model snapshot/ID + parameters + explicit context/version hash + policy version 全等且来源有效时才显示“历史缓存/来源”，不创建 Provider attempt/不计成本；部分匹配仅是历史检索/Context candidate，必须显式选择并仍走正常调用。提供失效、撤销、来源跳转、审计和是否采用的控制。

## P6-D 工作区范围对话优先（实现已修正；新 macOS bundle 待复核）

- 2026-08-13 的权威 UI 解释已写入 `DESKTOP_CHAT_FIRST_UI_CONTRACT.md`、总蓝图和 `design-qa.md`：`对话`是个人/通用会话；`工作`是当前工作区范围内的同一真实对话。进入/再次点击工作、以及选择工作区后，右侧回到该 scope 最近会话；没有会话即显示相同输入区的新对话空态。工作区是对话 scope，项目/知识/记忆/受控记录必须经明确点击才替换为二级管理页。
- 隔离 fixture 的正式 UI 导入实际暴露了旧 `work-home` 默认概览。已将 chat shell 路由改为 `work`，并让 `refresh`、工作模式切换和工作区选择都解析当前工作区最近会话；`work-home` 不再是工作模式默认。左侧工作导航现列出当前可选 workspace scope。
- 自动验证：Desktop 17/0 Node tests、typecheck、lint、static build 已通过，新增覆盖工作范围对话优先、最近会话/空态、scope 切换、禁止 `work-home` 默认，以及 drawer X 的渲染/状态边界。此变更没有新增 Rust command、SQLite migration、Key/Provider/HTTP、同步或跨端行为。
- 实际证据：系统选择器严格预检后导入命名为 `P6-D 隔离长列表验证 · 可删除` 的 app-private 合成工作区（64 条非敏感文本、非用户数据/Provider 输出；语义 hash `1367f27106464d90f74095806af05924b68e779dea15b465e6fa13925903f3c3`，包 hash `1dee3bd4641a201bfe967a51b0b6380f388649cd64c847b5858e527f9aac9b1c`）。重建后的 `.app` 点击“工作”后直接显示该 workspace 的会话与输入区；没有回到 work-home。
- 紧凑导航的左上“关闭”文字已移除并实机复核：宽屏不出现关闭控件；窄窗口打开 drawer 后右上仅为小型无文字 `×`（Accessibility 为 `关闭导航`）。该按钮关闭 drawer 后，工作模式、workspace、会话、消息中段和固定输入区均保留；drawer 打开后窄→宽会自动关闭，再次缩窄仍关闭。

## P6-D 紧凑态消息列表与窗口恢复（本地实现与隔离 fixture macOS 黑箱闭环；不等于后续阶段）

- 新最小合同：`P6D_COMPACT_CHAT_SCROLL_AND_WINDOW_RECOVERY_CONTRACT.md`。消息列表唯一滚动所有者明确为 `.chat-scroll`；会话历史、消息和固定输入区不再共享整页滚动。当前会话的 scrollTop 只在 chat shell 内存中跨重绘恢复，不写 SQLite、草稿、Keychain 或浏览器存储。
- 宽度从 `<= 900px` 回到展开宽度时，`sidebarOpen` 会立即清除，避免用户再次缩窄窗口时残留的 drawer 自动遮挡画布。它只治理瞬时 shell 状态，不持久化 macOS window bounds、会话选择或领域数据。
- 自动验证：Desktop Node 17/0（含 48 条组件合同、工作区范围对话优先、独立 scroll owner、scroll restore、drawer X 与 compact→expanded close），typecheck、lint、static build，Rust fmt/32 tests/clippy `-D warnings`/check 已通过。Node exchange golden 与 Android `P6AExchangeContractsTest` 的最终只读回归仍在本阶段收口时复跑；Android 无版本或安装变更。
- 实际 macOS：最新离线 bundle 使用正式系统选择器/确认导入隔离 fixture，不触碰既有长期 workspace。工作区内的 64 条合成长会话实际滚至第 19–23 条，固定输入区仍可见；工作切换重绘、drawer 打开/关闭、窄→宽→再窄均回到同一中段位置。宽屏无 close；抽屉打开时右上是无文字 `×`，关抽屉后不改变工作 scope 或会话。
- 最新离线包：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app` 已 ad-hoc 重签并 strict verify；可执行 SHA-256 `48b3194d5167d74e9a7896a3f51b887f96305cc9449a4329e8c0109919beccd9`，`Identifier=com.nanzhufeng.ai.desktop`、`TeamIdentifier=not set`。它不是 Developer ID/notarized/发布包。
- 明确未做：未读写 Key、未构造 Authorization/Prompt/RunSpec、未发 Provider/Google/Supabase HTTP、未产生费用或图片调用，未操作 OPPO、Windows、同步、跨应用或图标。P6-D/本地 UI 仍不是项目终点。

## Desktop chat-first 紧凑窗口可恢复侧栏（完成本地 P6-D UI/实际 macOS 可见核验；不等于 Provider 或同步完成）

- 以 `DESKTOP_CHAT_FIRST_UI_CONTRACT.md` 的紧凑视口缺口为唯一目标，统一 shell 现在在 CSS 宽度 `<= 900px` 将对话/工作侧栏收为可恢复 drawer。主区保留同一顶部“对话 / 工作”、路径状态与固定输入区；侧栏打开后只覆盖主画布并使用中性灰 scrim，未新建第二套导航或深色 workbench。
- 新 UI 瞬时状态唯一由 Desktop chat shell 的 `sidebarOpen` 拥有：打开、显式“关闭导航”、遮罩点击、工作/会话/设置导航以及 Escape 都可关闭；它不进入 SQLite、草稿、Conversation、工作区、Keychain、浏览器存储或联网状态。对话与工作继续共用同一浅色 token、白色输入面和键盘焦点轮廓。
- 自动验证：Desktop Node tests 14/0（新增 drawer render/CSS/state contract），typecheck、lint、static build，Rust fmt、32 tests 与 clippy `-D warnings` 均通过；交换 golden 与 Android `P6AExchangeContractsTest` 定向 strict preflight 也通过。无 Android 版本或产物变更。
- 实际 macOS：最新 bundle 在窄窗口中可见“打开导航”；打开后可见对话侧栏和灰色 scrim，显式关闭及 Escape 都回到未遮挡聊天画布。验收未导入、未发送或写入真实 workspace，验收实例已关闭。
- 最新离线包：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app` 已 ad-hoc 重签并 strict verify；可执行 SHA-256 `ed73aac0cd336674e1ab52d30a447aa2b2a759ca83ac4b121f8b87ed15e9a439`，`Identifier=com.nanzhufeng.ai.desktop`、`TeamIdentifier=not set`。它不是 Developer ID/notarized/发布包。
- 明确未做：未读取/写入 Key，未构造 Authorization、Prompt/RunSpec 或任何 Provider/Google/Supabase HTTP；未产生费用或图片调用，未操作 Android/OPPO/Windows，未修改图标。

## Desktop 统一 chat-first shell 与自动本地发送（完成本机 UI/自动合同；不等于真实模型调用）

- 最新用户方向已替代此前“chat 外壳 + legacy 工作台”方案：当前运行时只渲染同一浅色 chat-first shell。顶部 `对话 / 工作` 是真实模式切换并始终可用；对话模式左侧仅为新对话、搜索和会话历史，工作模式左侧仅为工作区、项目、知识、记忆和本地受控记录。右侧永远是同一主面板；不再加载或暴露深绿色旧侧栏、固定 Inspector 或第二套信息架构。
- 工作模式内的知识与记忆已拆为独立入口：知识页只显示 Knowledge/relationship，记忆页只显示 Memory。现有 Project/Knowledge/Memory/relation/Rust revision、导入导出、撤销/重做与 P8 只读 owner 未被重写；它们只是以统一浅色面板投影。
- 输入主动作现为“发送”，而非要求用户理解的“保存”。发送先通过既有 Rust `mutate_desktop_domain` 创建/追加 user message；当前未配置模型时，真实状态为“消息已本地记录，配置模型后可生成回答”，不伪造 assistant 回复或联网成功。模型入口显示“选择模型 · 未配置”；不读 Key、不构造 Authorization/HTTP、不产生费用或图片调用。草稿由 chat shell 使用当前 workspace/conversation key 自动本机持久化，发送成功才清除；消息与会话继续由 Rust SQLite owner 持久化。
- 新合同：`DESKTOP_CHAT_FIRST_UI_CONTRACT.md` 已更新为该权威解释。前端 lint/typecheck/static build、13 Node tests（含统一模式、发送/草稿、搜索和无假模型输出）全绿；Rust fmt、32 tests、clippy `-D warnings` 全绿。新 Tauri app 已离线构建、ad-hoc 重签与 strict verify；可执行 SHA-256 `07db41c48d7fa4b8fb5e18b1af47cd8b7f373a2ea4f3e9b89a0969f2743af69f`，`Identifier=com.nanzhufeng.ai.desktop`、`TeamIdentifier=not set`，非 Developer ID/notarized/发布包。
- 实际 `.app` 先审计到旧深绿色 workbench（旧进程/旧包，不能作新方向证据）；已关闭该由本轮启动的旧实例，重新打开上述唯一最新 bundle。Accessibility tree 与实际截图确认：对话模式有“选择模型 · 未配置 / 发送 / 自动本地记录”输入区；工作模式切换左侧功能集且保留同一顶栏；知识和记忆入口各自显示独立右侧面板；可由顶部“对话”返回。为保护现有长期 workspace，本轮未向真实 workspace 写入测试 user message，因此发送的 Rust 真实写入/重启读回仍由既有 typed contract，不伪报为本轮黑箱写入。
- 图标视觉验收继续暂停；未操作 OPPO、未读写 Key、未触发 Provider/Google/Supabase/同步、未操作 Windows。

## Desktop chat-first 方向纠正（完成默认入口与真实 `.app` 视觉闭环；不等于真实联网完成）

- 用户确认 Desktop 不应再以复杂工程工作台作为默认首页，而应直接复用 ChatGPT/Claude 的成熟对话信息架构。合同、实现与证据：`DESKTOP_CHAT_FIRST_UI_CONTRACT.md`、`DESKTOP_CHAT_FIRST_UI_EVIDENCE.md`、根目录 `design-qa.md`。
- 默认入口现为浅色轻侧栏 + 单一聊天画布 + 空态居中输入区 + 会话历史 + 底部账号/设置。现有 Project/Knowledge/Memory/relation/Inspector、导入导出、撤销重做和 P8 只读账本没有删除，只在用户明确点“工作”后出现。此前新增的“连接路径”不再是一级导航，双路径状态收进输入区和设置。
- 本地记录复用现有 Rust `mutate_desktop_domain`：没有当前会话时创建 Conversation + firstMessage，已有会话时按 expected revision 追加 user 节点；空白拒绝，回车保存、Shift+Enter 换行。页面没有构造 Prompt/RunSpec、没有 Provider 请求。真实 webview textarea 仍缺稳定 macOS Accessibility 指针黑箱，因此自动 typed wiring + Rust reopen 是当前证据，不伪报为新的 GUI 重启读回。
- 双路径仍是最终产品方向：本地路径始终可用；Provider 和加密同步分别显示配置状态。当前 `.app` 显示“联网未配置”，没有读取本机 OpenRouter Key、没有 Authorization/HTTP、没有费用或图片调用、没有 Google/Supabase。
- 前端 lint/typecheck/11 tests/build 全绿；Rust fmt、32 tests、clippy `-D warnings` 全绿；offline Tauri build 通过。最终 `.app` 已 ad-hoc 重签且 strict verify 通过，13 MiB，可执行 SHA-256 `b3a6e7f949d2c9451e1245cd71a83e4508132578fda3783a9a124a07d053e6a3`，`Identifier=com.nanzhufeng.ai.desktop`、`TeamIdentifier=not set`，非 Developer ID/notarized/发布包。
- 实际最新 `.app` 已打开并截图；参考与实现合并比较先发现空态输入框过低、状态重复两个 P2，修复后第二轮无剩余 P0/P1/P2，`design-qa.md` 为 `passed`。紧凑窗口、账号菜单实际截图和 native textarea 指针自动化仍是后续证据；图标视觉验收继续暂停，未据模拟器/缓存窗口评价图标。

## P10-A 双路径 A：跨端联网产品路径合同、状态与配置表面（完成本地状态闭环；绝非真实联网/同步成功）

- 合同：`P10A_DUAL_PATH_CONNECTION_CAPABILITY_CONTRACT.md`。最终产品方向固定为两组独立路径：模型执行 `LOCAL_OFFLINE` / `ONLINE_PROVIDER`，数据 `LOCAL_ONLY` / `ENCRYPTED_SYNC`。本地始终可用；联网模型与账号同步分别配置、分别授权、分别降级，绝不会由一个开关外发全部本地数据。
- 同语义状态：Android `ConnectionCapabilitySnapshot` 与 Desktop `ConnectionCapability` 都明确 Provider configuration、Credential presence（只布尔）、Catalog freshness、逐次 egress consent、Sync capability 与 `NO_CREDENTIAL` / `CATALOG_*` / `NETWORK_UNVERIFIED` / `UNKNOWN_COST` / `MODEL_UNAVAILABLE` / `SYNC_NOT_CONFIGURED` 等 degraded reason。online 被阻止不会伪造成 local 成功；取消、重复 intent、unknown cost、重建和独立 sync 皆有合同覆盖。
- Android：`ReadConnectionCapabilityUseCase → DualPathConnectionViewModel → Settings` 复用 P2-D Keystore 的仅存在性读取、Registry 与 P7 配置状态，未解密或读取 Key，`OpenRouterEgressPolicy.Disabled` 未改变。正式界面新增“连接与数据路径”纯白状态面；`emulator-5554` 实际 UI 冷启动并打开后回读 `LOCAL_OFFLINE / LOCAL_ONLY`、`ONLINE_PROVIDER`、Key presence `PRESENT`（仅存在性）、`ENCRYPTED_SYNC_NOT_CONFIGURED` 和 `NETWORK_UNVERIFIED / EGRESS_CONSENT_REQUIRED / SYNC_NOT_CONFIGURED`。这不是 Key/网络/同步成功。
- Desktop：`dual_path_contract_v1` 提供 OS credential-store presence 抽象；release 固定 `NoCredentialStore`，不读 Keychain 值、不写 SQLite/前端/log、不加 HTTP plugin。只读 `read_dual_path_status` Tauri command 现在由 chat-first 输入区/设置读取，不再注入一级“连接路径”导航；实际新 `.app` 显示 Provider 未配置、加密同步未配置与本地可用。fake loopback 仅 Rust `cfg(test)`；不存在 release fake transport DI。
- 自动验证：Android 全量 JVM `237 tests / 0 failures / 0 errors`，含新 `DualPathConnectionContractsTest`；Lint 0 errors（既有 warnings）。Desktop 最新 frontend typecheck/lint/11 tests/build 通过；Rust fmt、32 tests、clippy `-D warnings` 通过；最新 Tauri bundle 已 ad-hoc 重签并 `codesign --verify --deep --strict` 通过。
- Android 产物：版本 `0.3.0-p10a` / code 51，正式 Debug SHA-256 `a8cd9657ddf01940dd37451fd5bc1426b81cffdfb7df99cb6d2bd93f8fd52c51`，Release `4882c164e94211400be75a812e473e2d21d0f30aef0cf7d3f116c9577d14b6c6`；两者 v2/v3=true，证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。仅 `emulator-5554` 以 `install -r --user 0` 同签名覆盖，`ceDataInode=574936` 保留；cold start `Status: ok` / 2.023 s，设备 version code 51，回拉 `base.apk` hash 与最终 Debug 相同。未清数据、未装 test APK、未操作 OPPO。
- Desktop 产物：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app` 已真实 `open -n` 并完成 chat-first 实际截图与同屏比较；最新可执行 SHA-256 `b3a6e7f949d2c9451e1245cd71a83e4508132578fda3783a9a124a07d053e6a3`。`Identifier=com.nanzhufeng.ai.desktop`、`Signature=adhoc`、`TeamIdentifier=not set`，非 Developer ID/notarized/发布包。
- 明确未做：没有读取本机 OpenRouter Key、没有 Authorization/Provider/OpenRouter HTTP、没有费用/图片调用、没有真实 Google/Supabase/OAuth 或跨网络同步、没有 OPPO/Windows。fake/loopback 绝不可称为联网成功；最终项目前仍需另立真实 Key/HTTP 小额文本验收。

## P10-B 双路径选择反馈（本地完成；绝非联网、配置保存或同步成功）

- 合同：`P10B_DUAL_PATH_SELECTION_FEEDBACK_CONTRACT.md`。P10-B 将 P10-A 的只读状态变为两端可操作但纯瞬时的选择反馈：`LOCAL_OFFLINE / LOCAL_ONLY` 明确继续本地；请求 `ONLINE_PROVIDER` 只明确显示阻止原因及配置、模型、费用、逐次 consent 的后续门槛。它不接收或发送正文、图片、模型 ID、Key 或账号信息。
- Android：`DualPathConnectionViewModel` 只复用 `ConnectionPathGuard` 生成内存 `PathSelectionResult`；关闭/重开清掉反馈，状态仍从 P10-A use case 重读。纯白连接 Dialog 增加“继续本地工作”和“查看联网前提”，本地结果明确“没有调用模型或同步”，联网结果仍是 `OnlineBlocked`，从未创建 Task、Invocation、Provider Attempt、sync task 或持久化记录。
- Desktop：现有 `read_dual_path_status` 与 capability 不变；连接页在前端新增同语义的“继续本地工作”与“查看联网前提”反馈，未增加 Tauri command、capability、SQLite、Keychain、HTTP plugin 或浏览器存储。`show-work` 仍仅进入既有本地工作区。
- 自动验证：Android 全量 JVM `238 tests / 0 failures / 0 errors`，新增 guidance 不可能成为 `OnlineReady` 的合同；`lintDebug` 通过（既有 warnings）；`assembleDebug` 通过。Desktop typecheck/lint/11 tests/build、Rust fmt/32 tests/clippy `-D warnings` 通过。未安装 APK、未清模拟器、未读 Key、未调用 Provider/OpenRouter HTTP、未产生费用或图片调用、未操作 Google/Supabase/OAuth/跨网络、OPPO 或 Windows。
- P10-B 到此停止。它不是 Key 配置、实时目录、费用估算、实际文本/图片请求、账号、同步或真实服务验收；fake/loopback/页面按钮均不得表述为联网成功。
- `context_gate.py` 于本阶段结束返回 `WARN`（context 61.9%，effective tokens 204,219），已填写交接卡：`/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-13T17-39-05-019ffa7d-568a-79c3-a0c8-fbc32b51f166.md`。按门禁，本阶段完成后不在本线程开启下一阶段。

## P9-B 本地 Integration Contract 与 LOCAL_TEST_ONLY harness（本地完成；绝非真实生态接入）

- 合同与证据：`P9B_LOCAL_TEST_ONLY_INTEGRATION_CONTRACT.md`、`P9B_LOCAL_EXIT_EVIDENCE.md`。P9-A 已确认没有可核验真实目标入口；因此 P9-B 只允许版本化 `nfai.integration-contract` v1 的本地基础，固定 opaque app/subject handle、`READ_ONLY_PREVIEW`/`READ_ONLY`、source provenance/revision/hash、bounded pagination、expiry、preview/result/readback/revoke/audit 与 idempotent receipt。`UNKNOWN`、高敏、未知字段/版本、超限、跨 app、过期、取消后继续和目标更新全部失败关闭；`null` 不等于 `0`。
- Android 仅增加 secret-free P9-B Room ledger 的 Schema 20→21（session/event/receipt）；Desktop 使用独立 `p9b-local-test-only.sqlite3`。两端均不保存正文、URI/path、token/Key、数据库句柄或真实目标数据，也不复用 P6/P7/P8 ledger。
- harness 只在测试显式注入合成非敏感 target metadata，覆盖许可→只读 preview→确认→result→readback→revoke 和 replay/cancel/expiry/target update/cross app/越权/高敏/unknown/分页拒绝。release Android DI/UI/Manifest 与 Desktop Tauri command/capability/frontend 不注册 target、harness 或 adapter；没有 Provider、网络、文件、Provider/Service binding 或跨应用调用。
- 本地实现与验证：Android `P9BContractParser → P9BLocalTestOnlyHarness → RoomP9BIntegrationLedger`，Schema 20→21 只追加三张 P9-B 账本表；Desktop `p9b_integration_contract_v1` 只在 Rust test 注入 target，release 仅声明未绑定模块。P9-B 定向 Android domain/Room 为 5 tests；全量 JVM 234 tests、0 failures/errors；Lint 0 issues。正式 `0.3.0-p9b` / code 50 Debug SHA-256 `e16aaf8de53291183cf4b0c800f59c957f0afb22501a39af3f0093789faee6f9`，Release `6167e373ea9bbf28f87d1dbafb30bae817ab27082c052e17be88d0714a209ac3`；两者 v2/v3 均为 true，证书 SHA-256 均为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- `emulator-5554` 仅执行同签名 `install -r --user 0`，覆盖前 app data inode 为 `574936`；未清数据、未装 test APK。`NanfengAiActivity` cold start `Status: ok` / 2.345 s，设备 version code 50 / `0.3.0-p9b`，回拉 `base.apk` hash 与最终 Debug 完全一致。
- Desktop frontend typecheck/lint/5 tests/build，Rust fmt/31 tests/clippy/check/Tauri app build 均通过。新 `.app` 已 ad-hoc 重签并 strict verify，TeamIdentifier none、非 Developer ID/notarized；可执行文件 SHA-256 `5583b6c4961a67454ab0d0b9c7b2c1f7473987f13f10467838a7e8990220ab58`。原 Android/Tauri 共享 PNG 的 RGB 编码不被 Tauri 接受，故保留当前共享资源 SHA-256 `324764484f27d2695f7a028488c84c82bc941dcafa197fecced9db1e42924c9c`，由其确定性生成 `desktop/src-tauri/icons/nanfeng_ai_icon_rgba.png`（432×432 RGBA，SHA-256 `cfcbc287f4f3a2845cd6a10bd24e9c8b8798652f1e409094186e27b0ecc32dbe`）供 Tauri bundle。实际新进程可启动；嵌入 `.icns` 拆出 PNG 有 alpha。当前屏幕未显示 Dock/Finder，且旧进程可能缓存图标，所以不能把窗口观察写成 Dock/Finder 图标视觉通过；未操作 OPPO。
- P9-B 到此停止。无真实目标入口的事实不变：它不是 Adapter、真实授权、跨应用读取或 P9 退出证据。真实 P9 仍需一个实际目标应用的稳定公开入口、目标侧最小授权/确认/revoke/readback 与独立真实验收合同；候选写入继续未授权。

## 产品双路径方向（已确认；不改变当前 P9-B 授权）

- 最终产品必须同时提供本地离线路径与经明确配置/同意后的联网 Provider、账号同步路径；本地默认不外发，断网时降级本地，两路状态/UI 必须清楚可辨。
- 当前用户决定真实 OpenRouter Key 调用后测：本轮不得读取 Key、构造 Authorization、发真实请求或把 fixture 当真实 Provider。Provider/账号同步从此是后续必须验收的产品路线，不再表述为可永久跳过的外部门。

## P9-A 最小只读生态入口合同与现状审计（完成；生态接入仍阻塞，P9 真实闭环未开始）

- 合同与差距报告：`P9A_READ_ONLY_ECOSYSTEM_ENTRY_AUDIT_CONTRACT.md`。按总蓝图优先候选只审计南枫知识库，结果是当前 Mac 仅有 `NanfengKnowledgeBase-Reference/project-reference` 的参考文档快照；其项目规则明确实际 Windows 工程位于 `C:\\Users\\Administrator\\Documents\\软件开发\\nanfeng-intelligence`。参考快照不是可验证的真实目标应用，也没有声明供南枫 AI 调用的版本化只读 API/URI/intent/ACL、最小权限、撤销和结果回读合同。
- 南枫 AI 现场静态审计：Android 仅有入站 `ACTION_SEND text/plain` 捕获和 AndroidX Startup provider；没有 `ContentResolver`、外部 ContentProvider、跨应用服务绑定或目标应用 intent 查询。Desktop 也只有 P8 `inspect_p8_agent_runs` 本地只读命令。没有新增 Adapter、协议、Manifest query、UI、数据库迁移、fixture 或任何跨应用读取。
- P9-A 因缺少明确、稳定、最小授权的目标公开入口而在合同/差距报告停止。未读取任何其他应用数据库、用户文件、私有配置或运行状态；未触发网络/Provider/Key、跨应用写入或高风险动作；保留 `emulator-5554` 数据，未装 test APK，未操作 OPPO/Windows。
- 下一安全动作不是实施：请南烛枫指定目标应用的稳定公开入口，或提供可审计的实际目标源码/已安装入口与明确最小只读授权；随后再为一个 Adapter 建立独立合同。不得把本次审计、参考快照或 P8 fixture 表述为 P9 接入完成。

## P8-D 本地退出与红队审计（完成；P8 本地主体退出，P9 未开始）

- 合同与证据：`P8D_LOCAL_EXIT_RED_TEAM_AUDIT_CONTRACT.md`、`P8D_LOCAL_EXIT_RED_TEAM_AUDIT_EVIDENCE.md`。P8-D 对总蓝图退出要求逐项建立 requirement→authoritative evidence 矩阵；Tool schema/unknown、UNKNOWN risk、权限、injection、预算、approval binding/expiry/one-shot、pause/resume/cancel、checkpoint/restart、event/receipt replay、idempotency、rollback、event sequence 与审计最小化均有 Android/Desktop 当前代码和定向证据，不再用“测试存在”概括覆盖。
- 修复一个 production gap：`P8CProductionLocalAgentController.cancel()` 现在废弃同 Run 的 memory-only approval；P8-D 合同证明过期或取消后不会写 `PLAN_APPROVED`、Step 或 Receipt。新增的 `fixture_timeout` 仅为 `LOCAL_TEST_ONLY`，两端都落为 `FAILED / TOOL_TIMEOUT`，没有外部资源。
- Android production 唯一 `p8c_local_ledger_inspect` 已具备真实本地 success、cancel 与安全拒绝（expiry/approval required）的 durable 链；它是无输入、只读 ledger 的工具体，且没有外部 I/O，故不伪造“真实外部失败”。Desktop production 仍只有 `inspect_p8_agent_runs` read-only command；非空 P8 ledger 没有合法 UI 产生路径，因此非空仅由 Rust internal harness/reopen 自动证据证明。
- 最终 Android：全量 229 JVM tests 0 failure/error；lint 0 errors / 24 warnings；`0.3.0-p8d` / code 49 正式 Debug SHA-256 `1f818087835e859213a2930533aae01cd5573380cdd82844235f8a578857085d`，Release `3485e5773427afd0c6b9de9d8f3575c81c3e26e28e57c664600ed7b63c130259`，v2/v3 与既定证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5` 通过。仅 `emulator-5554` 同签名 `install -r --user 0`，`ceDataInode=574936` 保留；cold start 后 Settings → 受控本地运行回读 Run 2 / Step 1 / Event 7 / Receipt 1，设备 base.apk 与 Debug hash 一致；未清数据/未装 test APK/未操作 OPPO。
- 最终 Desktop：frontend typecheck/lint/5 tests/build、Rust fmt/29 tests/clippy/check、Tauri app build 均通过。新 app 实际打开并进入只读 inspect：`READ_ONLY · LOCAL_READ · NONE`、无 production executor、已知空账本，无模型/外部工具。ad-hoc strict verify 通过，13 MiB、executable SHA-256 `c3f23ced261782540dd3d74111fb907ea02b167a29a7c1eece13ee68eb53d229`、TeamIdentifier none；非 Developer ID/notarized/发布。
- 结论：P8 是“本地主体退出，真实外部工具/Provider/高风险动作仍为未来授权门”。P9 为下一唯一候选，但本轮未开始；不得由 P8 fixture、模拟器或 read-only UI 宣称真实外部工具、跨应用或高风险通过。

## P8-C 本地可见受控运行与退出审计（完成；P9/项目未开始）

- 合同与证据：`P8C_LOCAL_VISIBLE_AGENT_INSPECT_CONTRACT.md`、`P8C_LOCAL_VISIBLE_AGENT_EVIDENCE.md`。Android production 的唯一 owner 是 `P8CProductionLocalAgentController → ControlledAgentRuntime → RoomAgentLedger`；唯一动作 `p8c_local_ledger_inspect` 无输入、只读 ledger、`READ_ONLY/LOCAL_READ/NONE`、预算 `1/1/0`，固定标注“本地受控运行 / 未连接模型与外部工具”。它绝不读取 Key/Provider/HTTP/文件/跨应用数据，不购买、删除、外发或调用 fixture。
- Android API35 `emulator-5554` 已同签名覆盖 code 48（`ceDataInode=574936` 保留），真实 UI 路径证明空账本→计划→pause→force-stop→restart→resume→cancel→restart，以及新的 plan→显式 approval→本地成功 receipt；详情显示稳定 Event 序号、风险/权限和预算 `0` 与 unknown 的分离。Debug base.apk 回拉 hash 与本地包相同。完整记录见 P8-C evidence。
- Desktop “本地受控记录”页现为用户可达的唯一 P8 UI：只调用 `inspect_p8_agent_runs`，只显示 safe Run/Step/Event/Checkpoint metadata、`READ_ONLY/LOCAL_READ/NONE`、无模型/外部工具和无 production executor。Tauri ACL 只允许精确 `allow-inspect-p8-agent-runs`，没有新增 executor command、fixture registry、Provider/HTTP/Key、文件或跨应用路径。
- 最终验证：Desktop frontend typecheck/lint/5 tests/build、Rust fmt/29 tests/clippy/check、Tauri `.app` build、ad-hoc strict verify 均通过；实际新 `.app` 两次打开并回读空账本/无 production executor。Android 全量 JVM rerun 226 tests 无失败 XML、lint 0 errors / 24 warnings，正式 Debug/Release v2/v3 与既有正式证书通过；`emulator-5554` 同签名 `install -r --user 0` 未清数据，`ceDataInode=574936` 保留，code 48/base.apk hash 回读一致。完整事实见 P8-C evidence。
- P8-C 合同已满足；P9 没有开始。真实 Provider、外部工具、高风险动作、OPPO/Windows、Developer ID/notarization/发布继续为独立授权门。

## P8-B 受控本地 Agent harness 与 production-safe 状态入口（本地完成；P8/项目未结束）

- 合同与证据：`P8A_CONTROLLED_AGENT_LOCAL_RUNTIME_CONTRACT.md`、`P8A_LOCAL_EXIT_EVIDENCE.md`。唯一 owner 为 `ControlledAgentRuntime → AgentLedger`，Android `RoomAgentLedger` 与 Desktop `p8_agent_ledger_v1::AgentLedgerStore` 使用同语义 Run/Step/Event/Checkpoint/Receipt。Schema v1 的 Tool 必须显式 risk/permission/side-effect/rollback；`UNKNOWN`、未知预算、越权和外部副作用都失败关闭，`null` 未知与 `0` 明确零严格区分。
- Android Schema 19→20 仅添加五张 secret-free Agent ledger 表及索引，迁移不删改 P1–P7 数据；每次接受 step 与 event/checkpoint/receipt/Run 计数同事务，重放 receipt 不重复执行。账本没有 input/output 正文、Prompt、Provider、Key、Token/费用、URI、路径或文件字节。Desktop 用独立 app-private `p8-agent-ledger-v1/agent-ledger.sqlite3`（`user_version=1`），不复用 P6/P7 SQLite。
- 当前只有测试显式构造的 `LOCAL_TEST_ONLY` fixture registry：只读 research、草稿 candidate、可回滚 fixture action。release Android `AppContainer` 不构造 runtime/registry，Desktop 无 P8 Tauri command/UI；无 Provider、HTTP、Key、系统文件、跨应用、购买、删除、外发、自动 Agent 或假成功。
- 验证：Android P8 定向 domain/Room 5 tests 与全量 JVM 222 tests 均通过；覆盖 prompt injection 仅数据/hash、未知、权限/风险、tool exception、预算、取消/暂停/恢复、checkpoint、replay、rollback、重建与 19→20 旧表保留。`lintDebug` 0 errors / 23 warnings；`0.3.0-p8a` / code 46 Debug SHA-256 `d99d1f584285b540f9206522f76caf42cee372f627c2195917fbea911ac56670`，Release `5982db89a92ab7533a51d0e6dbb59b10688e57ec4d9bcd11603aa56275f70cee`，v2/v3 与正式证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5` 通过。仅 emulator-5554 同签名覆盖、`ceDataInode=574936` 保留，cold start 1.905s、base.apk hash 一致；未清数据/未装 test APK/未操作 OPPO。
- Desktop 前端 typecheck/lint/4 tests/build、Rust fmt/25 tests/clippy/check 与 Tauri app build 均通过；最终 app 已 ad-hoc strict verify，13 MiB、executable SHA-256 `303654de1e364faf30498bcad2d41aae947b709fc4a3b37ca5474ae8b3b57cf2`、TeamIdentifier none，非 Developer ID/notarized/发布。真实外部工具、可见 Agent UI、Provider/费用、P9/P10、OPPO/Windows 都未验证且必须新合同。
- P8-B 合同与证据：`P8B_READ_ONLY_AGENT_LEDGER_STATUS_CONTRACT.md`、`P8B_LOCAL_EXIT_EVIDENCE.md`。P8-A durable ledger 不重做；P8-B 只在 Android/Desktop `LOCAL_TEST_ONLY` harness 增加 explicit plan→approval token→approved execution，plan 只保存 stable Tool/intent/input hash，批准只审计 plan hash。unknown、预算、risk/permission、未知 tool 和 external effect 失败关闭；failure/cancel 必须先提交 Event/Checkpoint/终态再返回，reopen 不自动继续。
- production 接合仍严格无 executor：Android `AppContainer` 只构造 `RoomAgentLedger → P8BProductionReadOnlyAgentLedgerStatus`；Desktop 只在 `DesktopWorkspaceStore` 有私有 status method。没有 `LocalTestOnlyAgentToolRegistry`、`ControlledAgentRuntime`、UI、worker、Tauri command/capability 或 frontend state；状态仅安全 aggregate，`null` unknown 与 `0` known-empty 分离。
- 验证：Android 全量 JVM 225 tests 0 failure/error；P8 定向覆盖 plan/approval、注入 hash、unknown/permission/risk/tool/budget、failure/cancel、pause/resume/checkpoint/restart、receipt/event replay、rollback 与 Room。`lintDebug` 0 errors / 23 warnings；`0.3.0-p8b` / code 47 Debug SHA-256 `d0b101ee3a4064e9efa538db161cbe927a0f2671a3e39e85ac7b1f789cef4939`，Release `667d496537563d3cc4eaf7d24b8261cf4adacf5d2d9b768c37ecdd4b843cbb30`，v2/v3 与正式证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5` 通过。仅 `emulator-5554` 同签名覆盖、`ceDataInode=574936` 保留，cold start 1.924s、base.apk hash 一致；未清数据/未装 test APK/未操作 OPPO。
- Desktop frontend typecheck/lint/4 tests/build、protocol golden、Rust fmt/28 tests/clippy/check、Tauri app build 均通过；最终 app ad-hoc strict verify，13 MiB、executable SHA-256 `f3bcad6577e794fc57dc1e388156a52106410cc62f5d7eabf14c5f463234b691`、TeamIdentifier none，非 Developer ID/notarized/发布。没有真实工具、可见 Agent UI、Provider/费用、P9/P10、OPPO 或 Windows 证据。
- 下一候选必须另立合同；不可把 test-only harness 或不可见 status 表述为 real Agent。production tool/UI/文件或系统访问、跨应用、外部 side effect、Provider、P9/P10 都仍禁止提前接入。
- `context_gate.py` 本轮返回 `WARN`（context 62.4%，effective tokens 234,336），已自动生成交接卡：`/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-13T15-39-35-019ffa0f-ed26-7e51-8b45-da7735bf515c.md`；按门禁在本阶段结束，不在此线程开启下一 P8 子阶段。

## P7-E 跨设备恢复与部署验证就绪（本地双端 typed restore 主体完成；真实服务与真实入口未完成）

- 2026-08-13 追加生产容器接合：`AppContainer` 现在唯一持有 `AndroidP7ESemanticSnapshotSourceAdapter → P7EGuardedRestoreOwner → P7-A seal/open → P7ERestorePlanCoordinator → AndroidP7ESemanticAtomicRestoreWriter`。owner 只接受 future auth boundary 生成的 `VerifiedAccountHandle`，并在写前依次复核 P7-B `READY`、已确认的单一方向、Keystore vault 可用性、P7-D durable remote revision/hash 与固定 document；错误账号、未确认 recovery/方向、冲突、过期 revision 或错误 document 均在 writer 前拒绝。它只允许当前安全的空本机恢复或本机→空远端 seal，非空替换仍必须未来显式 UI 确认，不能由 UI/worker/raw Room 直写。
- `AndroidP7ERestoreReceiptStore` 只持久 opaque account ref、document、revision、payload hash、outcome；不存 envelope、正文、recovery code、key、token 或路径。重复 intent 读回同 receipt，不再次 stage/commit。Settings 继续只是诚实 disabled 准备态：“当前未配置，恢复入口保持禁用”，没有 fake login/cloud/network。
- 新定向 `P7EGuardedRestoreOwnerContractsTest` 覆盖 typed source→P7-A seal、verified/READY/direction/revision 的写前拒绝，以及 commit receipt replay；既有 typed Room writer 覆盖 candidate/readback、dangling reference、interrupt/rollback。69 个 Android JVM 测试类按 4 分片全通过；`lintDebug` 为 0 errors / 21 warnings；code 45 `0.3.0-p7e` Debug/Release 重建，v2/v3 和证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5` 均通过。Debug SHA-256 `f39811116c7c5d570c3fb97e10cefdfaa5c8cd20e1365d0c7d9eccda0569ca1e`；Release `fe30c367d53d5a4177a7d2a90947ef03afb6b215ad0c6a468763f7c3f753f830`。仅 `emulator-5554` 同签名覆盖、`ceDataInode=574936`、cold start 1.745s、base.apk hash 与 Debug 一致；未清数据、未装 test APK、未操作 OPPO。
- Desktop owner bridge 的定向 Rust test 已加入真实 reopen 后的 isolated-workspace semantic readback，并确认 P6 workspace 仍可读、无新增 Tauri command。本轮已完整重跑 Desktop 前端 `typecheck/lint/test(4)/build`、P7-A Node golden、Rust `fmt/test(23)/clippy/check` 与 `CARGO_NET_OFFLINE=true cargo tauri build --bundles app`；新 `.app` 已重作 ad-hoc 并 `codesign --verify --deep --strict` 通过。可执行文件 SHA-256 仍为 `def5299c2250ad7465680c270138d1dc58e7fe96b1407410d5c4af0dfa579101`，13 MiB、TeamIdentifier none、非 Developer ID/notarized/发布。完整命令与边界见 `P7E_LOCAL_EXIT_EVIDENCE.md`。

- 合同：`P7E_CROSS_DEVICE_RESTORE_AND_DEPLOYMENT_READINESS_CONTRACT.md`；真机/服务 runbook：`P7E_GOOGLE_SUPABASE_DEVICE_RUNBOOK.md`。P7-E 严格拆为两层：Android production 只有独立的 `nfai.sync.restore-plan.v1` plan/coordinator 与 checkpointed atomic allowlist-writer 接口，绝不接受 Room/SQLite handle、直接写 Room、清库或复用 P5-D SQLite backup；`LOCAL_TEST_ONLY` fake cloud 只在 Android test/Rust `cfg(test)`，无 release DI/UI/network/Tauri command。
- 本轮完成共同 `nfai.sync.semantic-record.v1` mapper 的 production restore 闭合：`P7ERestorePlanCoordinator` 在 plan 前强制从 P7-A record 反序列化并重建 semantic record，raw/P5-D/P6 或原始持久化字段、Provider/Prompt/RunSpec/运行数据、token/credential、asset/path/URI 和高敏整体拒绝。`AndroidP7ESemanticSnapshotSource` 以 typed DAO 读取 Project、Conversation tree、Knowledge、Memory、relation 与空 safe settings，遇到附件、Tool、草稿或缺失 append-only revision 整体失败。新的 `AndroidP7ESemanticAtomicRestoreWriter` 只消费这些语义 records，在 app-private fresh Room candidate 中用 typed DAO 写入，先检查所有 project/conversation/tree/knowledge/relation/memory 引用，再 semantic source cold-open readback；非空替换以 `VACUUM INTO` checkpoint、候选数据库及其 WAL/SHM/journal sidecar 同组切换、失败/中断 rollback 实现。它已由 `AppContainer` 的唯一 guarded owner 接合，但 writer 本身不接受 UI、账号或网络输入；成功后仍要求调用者重建 container/process。旧 `AndroidP7EAllowlistWorkspaceOwner` raw Room table prototype 仍 unbound/deprecated，回归测试现只证明它在 semantic plan gate 被拒绝，不能作为 writer 或退出证据。
- 本地闭环：Android test fake RPC 只保存 opaque P7-A envelope + account/document scope/revision/hash/intent receipt；覆盖 A seal→fake commit→B edit→A plan/atomic readback，以及 stale revision、重复 worker/intent、remote update + local dirty、wrong recovery code、tamper、cross document/account、HIGH_SENSITIVE、nonempty direction、interrupt、rollback 和默认本机保留。Desktop Rust test 从 Android shared known-answer envelope open，修改后以 expected revision commit，重放不递增且 stale/other-account/wrong-code 被拒绝；当前 Rust 全量为 23 tests。
- Desktop：`DesktopWorkspaceStore` 现拥有 app-private `p7e-isolated-workspace-v1` owner，并仅有内部 `open/read/seal` bridge；它只创建显式命名隔离 workspace，P6 `workspaces`/`workspace.sqlite3` 不变，且没有新增 Tauri command、capability、UI、HTTP 或 recovery input。owner integration test 证明既有 P6 workspace 可读、P7-E workspace 只在隔离 root 出现，并以 P7-A open/seal 回读同一 semantic records。真实 account/recovery owner、P7-A real document path 与可见 Desktop flow 尚未绑定。
- 限制：Android writer 已由 `AppContainer` 接入唯一 non-UI/non-worker owner，但当前没有可见 verified account/recovery/remote envelope 入口；在不伪造账号、不直接写 Room 的合同下，不能做真实应用进程强杀恢复。当前允许的 durable 证据是切换后关闭旧 Room、全新 Room semantic readback，以及 checkpoint rollback 的 6 项定向 Android 合同；它仍不能作为真实用户恢复入口或宣称 P7-E/项目退出。Desktop 同样只有内部 owner bridge。真实 Google/Supabase、OAuth、HTTP、跨网络 envelope、真机与最终人工恢复仍是明确外部门。
- 部署准备：`scripts/p7e-supabase-readiness.sh` 默认 dry-run/readonly，只输出 CLI/link/完整 private-config/auth 的布尔与未验证门，不读取或打印 URL/key/token/account/ciphertext。当前结果为 CLI=false、link=false、private config=false、auth=false；未 deploy/link/login/HTTP/OAuth/远端读写。P7-C SQL/头像 policy static tests 与 Node sync golden 通过；真实 target、用户授权、Google OAuth 和真机跨网络仍是外部门。
- 本轮 typed writer/owner bridge 验证：Android 68 JVM 测试类按 4×17 分片均通过（不把单 VM 结论替代为全绿）；P7-A/P7-E 定向 semantic/raw-rejection/typed-writer 组合通过，`lintDebug` 为 0 errors / 21 warnings，Debug/Release assemble 通过。Debug SHA-256 `f858d5a0c19426cb71ba15c49b243610e2f87202570817f1da0a11be9fee17f4`，Release `97ecbf4c610cfe48179c4bdd8fb1af70014356d2b0ce886d37a51b4908230cde`，两包 v2/v3 与既定证书 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5` 均通过。仅 `emulator-5554` 同签名 Debug 覆盖，`ceDataInode=574936` 不变、cold start 1.794 s、`stopped=false`，回拉 base.apk 与本地 Debug hash 一致。Settings→Google 账号与同步仍显示“尚未配置 / 离线可用”及没有登录、同步或网络请求；未装 test APK、未清数据、未操作 OPPO。
- Desktop：前端 typecheck/lint/4 tests/build、Rust fmt/23 tests/clippy/check、Tauri `.app` build 均通过；最终 `.app` 重新 ad-hoc 签名并 strict verify，TeamIdentifier none，非 Developer ID/notarized，executable SHA-256 `def5299c2250ad7465680c270138d1dc58e7fe96b1407410d5c4af0dfa579101`。Windows 仍独立外部门。

## P7-D 冲突、自动同步协调器与账号页（本地完成；真实服务未验证）

- 合同：`P7D_SYNC_COORDINATOR_AND_ACCOUNT_UI_CONTRACT.md`。P7-D 只新增 explicit allowlist 的 `P7DSyncCoordinator` 合同、secret-free Room 18→19 job/receipt ledger、WorkManager adapter、private avatar cache owner 和 Android/desktop offline state；业务 Room/SQLite 仍是真值。没有全库序列化、业务明文/envelope bytes/token/recovery/data key/URL/path/头像/Provider/Prompt/诊断落账。
- 调度/冲突：唯一 coordinator 只在 configured + verified session + P7-B READY + recovery/direction confirmed 时允许 mutation 延迟 30 秒与 12h periodic；cold start/账号页/头像只允许 periodic guard、绝不排 one-time。worker 只定义 snapshot/check/encrypt/commit/readback/complete/conflict/failed/interrupted 状态，提交后必须 hash 回读；远端改变+本地 pending 停 CONFLICT，运行中新 generation 不被旧完成吞掉。当前生产 App 没有真实 session/source，因此没有 coordinator binding、enqueue、HTTP 或假登录。
- UI：设置新增可返回“Google 账号与同步”详情；当前无配置显示“尚未配置 / 离线可用”，无姓名/邮箱/头像/成功，立即同步/切换/退出均 disabled。页面和卡/弹层遵守低色相底、`#FFFFFFFF` 自有表面、48dp 同形交互。头像 cache 仅 private account+URL hash 隔离、2 MiB/原子写/登出删除，未实现网络请求。
- Desktop：`p7d_sync_coordinator_v1` 与 About 离线入口只显示未配置/无 HTTP；无 Tauri command/capability/browser OAuth。Desktop 仍可离线使用。
- 验证：Android P7-D unit/UI/avatar 定向通过；本轮较早全量 `testDebugUnitTest`、`lintDebug`、Debug/Release 构建通过，最终代码的 lint/Debug+Release 亦通过。最终全量 JVM rerun 两次在 Android Studio JBR test VM 发生 SIGSEGV（无断言失败，系统无备用 JDK），故不把该环境崩溃表述为最终全量绿色；P7-D 定向 test 与最终 production compile/package 均绿色。前端 typecheck/lint/4 tests/build、Rust fmt/clippy/check/16 tests、Tauri app build 通过。模拟器 `emulator-5554` 同签名覆盖后 Settings→详情→返回显示无配置离线、无假身份；精确 JobStatus 筛选为 0，cold restart 后仍为 0，`ceDataInode=574936`、`stopped=false`。未安装 test APK、未操作 OPPO。Desktop `.app` 已 ad-hoc重签 strict verify，无 TeamIdentifier。
- 外部门：本机仍无 Supabase CLI/link/私有配置/可验证授权会话；未读写远端、未 OAuth、未 HTTP、未真实身份/头像。P7-E 才在明确 target 与授权后做真实 Google/Supabase、P5-D 原子 restore、真机与 Android/Desktop 跨设备验收；P7-D 不替换本机或清库。

## P7-C Google/Supabase 可部署服务配置（本地工件完成；真实服务未验证）

- 合同：`P7C_REAL_SERVICE_CONFIGURATION_CONTRACT.md`；部署工件：`supabase/migrations/202608130001_p7c_secure_sync.sql`、`supabase/functions/google-avatar/`。认证链固定 Credential Manager nonce → Google ID Token 瞬时提交 Supabase Auth → `auth.uid()`；令牌、Client Secret、`service_role`、恢复码、data key、业务明文和头像缓存均禁止落盘/日志/fixture/云端。
- Backend：两张表只存 P7-A envelope 或严格字段投影的密文 wrapping metadata；两表 force RLS + revoke direct table access，authenticated 只可执行最小 read/put-key/read-document/commit RPC。commit 用 advisory lock 和 expectedRevision，严格拒绝异常 protocol/schema/hash/bytes/envelope；P7-C 没有删除 RPC。Edge Function 只代理 verified Google identity 的 HTTPS 子域头像，JWT、redirect 重验、8 秒、2 MiB、image-only、no-store。
- 验证：Node SQL/头像策略 5 项、Deno 头像策略 2 项与 typecheck、Android 全量 `testDebugUnitTest`/`lintDebug`/Debug+Release 构建、Desktop 前端 typecheck/lint/4 tests/build 与 Rust fmt/clippy/check/15 tests/Tauri `.app` build 均通过。Debug/Release 均为既定正式证书 SHA-256 的 v2/v3；仅 `emulator-5554` 同签名 Debug 覆盖，`ceDataInode=574936` 不变且 launcher cold start 后 `stopped=false`。Desktop build 后重作 ad-hoc并 strict verify，通过且无 TeamIdentifier（非 Developer ID/notarized）。未安装测试 APK、未操作 OPPO。
- 审计：本机 Supabase CLI/link/private config 均缺失、CLI login 不可验证；没有远端读取或写入、没有 migration/function deploy、没有 OAuth/真实账号/密文访问。真实外部门为明确 target、授权会话、Google OAuth 配置、私有 injection、远端 schema/RLS/RPC/anon 拒绝回读，以及真机账户/envelope hash 验收。
- 下一阶段：P7-D 冲突/自动同步/账号页可先做纯本地状态与 UI；真实 Google/Supabase 仍不能伪报通过。

## P7-B 账号级本机密钥、恢复确认与状态机（完成；P7/项目未结束）

- 合同：`P7B_ACCOUNT_KEY_STATE_MACHINE_CONTRACT.md`。P7-B 只接受未来认证边界的 opaque verifiedAccountId/handle；未认证状态不创建 vault 或同步文档，不读取系统账号、Credential Manager、Google/Supabase token 或网络。没有假登录、假昵称/头像、云端 revision 或“同步成功” UI。
- Android：Room Schema 17→18 新增仅 secret-free 的 `sync_account_metadata`、`sync_intents`；只保存 accountRef hash、key alias/ref/hash、state/revision/direction/error 和 expectedRevision/result receipt。`AndroidP7BAccountVault` 首次 verified account 以 CSPRNG 生成 32-byte data key，使用不可导出 Android Keystore AES-GCM key 封装到 app-private `p7b-vault` blob；Room/SharedPreferences 不会写 data key、恢复码、token、payload/ciphertext。`withUnsealedDataKey` 只在 READY callback 内给短生命周期 key，并 finally 清零；Keystore/blob 缺失或 hash 不符失败关闭为 `KEY_MATERIAL_UNAVAILABLE`，不会重生或覆盖 key。
- Desktop：SQLite `user_version=3→4` 仅新增同等 metadata/intent tables；`sync_state_v1` 有不含 Tauri command/HTTP 的持久状态 transition 与 OS `CredentialStore` 抽象。macOS 已使用随机 `com.nanzhufeng.ai.p7b.selftest.*` 临时 service/account 实行 keychain 写→读→删→查询不存在，未枚举、读取或修改既有用户凭据；测试输出不含 key。Windows Credential Manager 的实际可用性仍为 P7-C/Windows 外部门。
- 状态/守卫：`SIGNED_OUT`、`AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION`、`DIRECTION_REQUIRED`、`READY`、`SYNCING`、`CONFLICT`、`FAILED`、`SIGNED_OUT_KEEP_LOCAL` 均已定义。确认恢复码前绝不 seal/排队/提交方向；local+remote 非空固定停 CONFLICT；退出默认保留业务数据；cold start 仅恢复 metadata、不会建 vault/解封/调度。所有 transition 使用 stable intentId、expectedRevision、事务和幂等 receipt，重放不重复生成 key。
- 已验证：P7-A Node golden；Desktop Node typecheck/lint/test/build、Rust fmt、13 tests、clippy -D warnings、check、Tauri macOS app build；Android P7-B state/vault/schema 定向与全量 `testDebugUnitTest`、`lintDebug`、Debug/Release 构建通过（既有两条 Kotlin test warning 和 native strip 提示，P7-B 无 lint error）。`0.3.0-p7b`/code 42 Debug/Release 都经现有正式证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5` 的 v2/v3 核验；`emulator-5554` 同签名覆盖、冷启动成功，`ceDataInode=574936` 不变、`stopped=false`。未安装测试 APK、未操作 OPPO。最终 Tauri `.app` 已在 build 后以 `codesign --force --deep --sign -` 重作 ad-hoc 开发签名，`codesign --verify --deep --strict` 通过；未嵌入 entitlements，未改权限配置。可执行文件 SHA-256 为 `4188bf65e7b3745a6287d52ada2b66d6a48a5d4953cefd8ac5ed343dc50e6b11`；签名为 `adhoc`、无 TeamIdentifier，非 Developer ID、未 notarized、未发布。
- 下一唯一阶段 P7-C：先写 Google/Supabase 真实服务配置、RLS/RPC/cloud envelope 合同；仍须在真实账号、服务和网络授权到位后才接 Credential Manager/Auth/HTTP。P7-B 不能作为真实账号、恢复码展示、云端同步、冲突合并或跨设备恢复证据。

## P7-A 本地跨端 E2EE 快照协议（完成；P7/项目未结束）

- 合同：`P7A_LOCAL_E2EE_SYNC_FOUNDATION_CONTRACT.md`。`nfai.sync.v1` 是本地、无账号、无网络的端到端加密 snapshot 基座，与 P5-D `.nfai-backup`（本机 SAF/SQLite 恢复）和 P6 `nfai.exchange.v1`（语义迁移）严格分离；两端只消费显式 allowlist records，从不读 Room/SQLite、账号、Provider、路径、密钥配置或网络。
- 协议与密码学：canonical payload/envelope 以 PBKDF2-HMAC-SHA-256（210,000）派生恢复码 wrapping key，并用 AES-256-GCM 分别封装 data key 和加密 payload；AAD 绑定 appId/documentId/protocol/schema/revision/payload hash。P7-A `seal` **仅接收调用方短生命周期持有的 32-byte data key**，绝不假称账号级 key 已生成/保存/轮换；每账号的 CSPRNG 生成和 Keystore/桌面安全存储属于 P7-B。随机 salt/两个不同 nonce 属于本次 envelope，known-answer fixture 与 production API 分离。
- 高敏与失败关闭：record 强制 `classification`，仅 `NORMAL` 可进入 payload；`HIGH_SENSITIVE` 在 Android/Rust seal/open 都整体拒绝，统一 detector 的禁止字段扫描仅防御未分类的错误构造。严格拒绝未知/重复字段、截断、错误 base64、KDF/nonce/大小/版本异常、wrong recovery code、header/ciphertext/tag/hash 篡改、跨 app/document、revision rollback 和超限；失败不返回部分 records 或写库。
- 共享 fixture：`protocol/fixtures/nfai.sync.v1.golden.json` 与 `protocol/scripts/run-sync-golden.mjs` 固定非敏感 known-answer。Android `P7ASyncContractsTest` 和 Rust `sync_v1` 都 seal/open 同一 golden；因此 golden 同时证明 Android→Desktop 与 Desktop→Android 的精确 envelope/payload 互操作，非真实账号或云端证据。
- 已验证：Node golden；Android 定向 `P7ASyncContractsTest`；全量 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --rerun-tasks` 成功（既有两条 Kotlin test warning、native strip 提示，无 P7-A Lint error）；Rust `fmt`、9 tests、`clippy -D warnings`、`check` 成功。Android 版本为 `0.3.0-p7a`/code 41，Debug/Release 都经现有证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5` 的 v2/v3 核验；`emulator-5554` 同签名 `install -r --user 0` 后冷启动成功、`ceDataInode=574936` 未变、`stopped=false`。未操作 OPPO。
- 后续唯一 P7 路线：P7-B 每账号 data-key 的生成/安全存储、恢复码确认和本机状态机；P7-C Google/Supabase 配置与真实 RLS/RPC；P7-D 冲突/自动同步/账号页；P7-E 真实 Android/Desktop 跨设备验收。不得把 P7-A 的 fixture、构建或 emulator 安装描述为真实 Google/Supabase/同步完成。

## P6-D macOS Tauri GUI 黑箱与 Desktop 产品化（本机主体完成；真实 GUI 证据仍有两项债务）

- 合同与记录：`P6D_MACOS_GUI_ACCEPTANCE_AND_DESKTOP_DELIVERY_CONTRACT.md`、`P6D_MACOS_GUI_BLACKBOX_EVIDENCE.md`、`P6D_MACOS_DEVELOPMENT_DELIVERY.md`；Windows 独立合同为 `P6D_WINDOWS_NATIVE_DELIVERY_CONTRACT.md`。P6-D 只补本地 Desktop，不读取/调用 Key、Provider/OpenRouter、Prompt/RunSpec、图片或费用，不改 Android version/Room、OPPO、图标、P7/P8、Hub、GitHub。
- 实际 `.app` 黑箱：系统 picker 已覆盖 duplicate import 的可执行中文错误、隔离 fixture 导入、Project/Knowledge/Memory/relation 创建、Cmd+Z/Cmd+Shift+Z、软删/回收站恢复、系统 Save picker 导出、强制终止后重启保留 workspace/revision/history且不重放未保存 intent。导出文件 3591 B，Rust/Node strict preflight semantic `4962c40914b533c5be6ee175e6569b9a3f6b850ef03ca06bb0cf87c0c1adc0f5`、8 entries、无高敏；Android 定向 `P6AExchangeContractsTest` 通过。
- 黑箱修复：补了 Project/Memory/relation 及 workspace switch/导入后自动选中的真实 GUI；修复刚创建对象 undo 时 before snapshot 缺对象被错误拒绝，Rust 增加对应回归，最终 Rust tests 7 项通过。另令 REVOKED relation 不再出现在活动关系列表。About 仅显示安全摘要/本地静态更新，不暴露路径或 SQLite。
- 最终 macOS app：`0.6.0-p6d-dev`、13 MiB、executable SHA-256 `a5c4879fa60f77496c68f6fdb0e3c853119449afc887d1efea72f7cfc5a1a353`；`codesign --verify --deep --strict` 通过，ad-hoc/TeamIdentifier none，macOS 11.0+，未 Developer ID/notarized、未发布。
- 自动门：前端 typecheck/lint/test/build（4 tests）、Rust fmt/test（7）/clippy/check、`cargo tauri build --bundles app` 与 Android P6A 定向 strict preflight 均通过。P6-D 是 Desktop-only，未升 Android version。
- Compact 已以仅验收用 820px 宽实际 Tauri `.app` 窗口验证抽屉覆盖与恢复入口，之后已删除测试配置、恢复默认 1440px 并重建最终包。诚实剩余：两实例实际进程可启动但可访问性路由只稳定暴露一个窗口，故可视 `REVISION_CONFLICT` 留作独立 GUI 债务，机制由真实 SQLite owner deterministic stale-revision 集成测试覆盖；Escape 注入也未取得可归因关闭证据。Windows 原生 WebView2/MSI/NSIS/签名/路径/picker/SQLite 锁升级/缩放/IME 与 macOS Developer ID/notarization 均独立外部门。P6 不因此误称总项目结束；下一仅可补这些 P6 本机证据，或在它们明确延期后按蓝图只立 P7 本地协议/安全合同，不实施同步。
- 本轮结尾 `context_gate.py` 为 `HANDOFF`（context 94.7%，有效 Token 499,216）；交接卡：`/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-13T11-33-25-019ff92e-8f55-7ca0-9a01-151316954a4c.md`。下一线程只读本 handoff、P6-D 合同/证据、ADR-002 与当前代码，不回读旧聊天全文。

## P6-C Desktop 本地领域写入、可逆动作与模型/成本 metadata 工作台（本地实现完成；P6/项目未结束）

- 独立合同：`P6C_DESKTOP_LOCAL_WRITE_WORKBENCH_CONTRACT.md`。Desktop 继续只由 Rust `DesktopWorkspaceStore` 写入 app-private SQLite；migration `user_version=3` 追加 `domain_intents`、`object_provenance`、`model_metadata` 与 replay receipt revision，不依赖 Android Room 表名。typed `mutate_desktop_domain` 是 Project / Conversation tree、Knowledge、Memory、relation 的唯一写链，所有 intent 有 stable `intentId`、`expectedRevision`、事务 rollback、append-only before/after exchange ledger 与重放幂等；revision 不符返回可见 `REVISION_CONFLICT`，不覆盖。
- P6-C local domain：Rust 生成新对象 stable ID，更新会递增 revision/contentHash；imported 对象保留 private `IMPORTED` provenance、origin semantic hash 和 imported revision，本地编辑标记 `LOCAL_EDIT` 而不污染 exchange v1。Knowledge/Memory scope 必须命中活动 project/conversation；relation 仅显式创建/撤销，检查两端活动、同 scope，`RELATED` 规范对称端点，另两类保持有向；无自动 relation、对象去重或 Memory 生成。正文以 escaped text IR 呈现，不执行 Markdown/HTML/code。
- 软删除/历史：Project/Conversation archive、Knowledge/Memory `DELETED`、relation `REVOKED` 均生成新 revision；回收站可 restore，P6-C 不开放永久删除。Undo/redo 由持久 action ledger 重建，重启后可见；undo/redo 生成新 revision 而不改历史，新的正常写入截断 redo 分支。导入、导出和 metadata 不进入领域 undo。P6-C 的 Rust tests 覆盖 migration/reopen、冲突、重放、undo/redo、soft delete/restore、relation scope、metadata secret 拒绝与 export strict preflight。
- exchange 与 metadata：未修改 `nfai.exchange.v1` schema/packageVersion 或 Android app version；本地可写事实映射回已有 revision/status/archived 字段，Desktop-private provenance/ledger/model metadata 不导出。Node golden 仍为 semantic `ad41c1ee6aa6…4031`、package `74078bf5bdc…b4ec`/3135 B；Android Studio JBR 下仅 `:app:testDebugUnitTest --tests com.nanzhufeng.ai.domain.P6AExchangeContractsTest --rerun-tasks` 成功，仍只有 Android strict preflight，绝不写 Android Room。模型/成本面只允许本地 manual/fixture/unknown metadata（capability、price version、currency、source、updatedAt）；Key/endpoint/raw payload/chunks 和真实费用均拒绝，`null` 仍是未知而非 0。
- UI：三栏工作台增加 Knowledge 编辑/保存/取消、明显撤销/重做、冲突文案、软删除回收站恢复、模型与成本 metadata 面板；`Cmd/Ctrl+S`、`Z`、`Shift+Z` 已接入，compact drawer、focus-visible、纯白 app-owned Dialog 和不执行文本 IR 保留。前端 `typecheck/lint/test/build` 通过（4 tests）；Rust `cargo fmt --check`、`clippy -D warnings`、`test`（6 tests）与 `check` 通过。Browser localhost 仍不可用，未将静态前端或 process startup 伪报为完整 browser visual QA。
- macOS：新的 `0.6.0-p6c-dev` app bundle 已由 `cargo tauri build --bundles app` 创建于 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，13 MiB；最终 executable SHA-256 `8882c8aac760286aba1c3f4ed5a0bf9a661fc89817e54498d3189afbd110ee41`。已由 `open -n` 启动，进程 `nanfeng-ai-desktop-spike` 可见；codesign `TeamIdentifier=not set`，故仅是 ad-hoc 开发包，未 Developer ID/notarized、不是正式 macOS 交付。自动 Rust 链在全新 app-private temp workspace 验证导入→create/edit→conflict→undo/redo→soft delete/restore→cold reopen→export→Rust strict preflight；仍缺真实 Tauri GUI 人工逐控件、2.0x/compact 截图验收和以系统 picker 完成 P6-C 写入链的黑箱证据。Windows installer/signing/WebView2/SQLite 性能债务继续保留。
- 下一 P6 阶段：只补 P6-C 的真实 macOS GUI 黑箱路径（现有非敏感 golden → 专用 workspace → Project/Knowledge/Memory/relation → edit → undo/redo → soft delete/restore → 关闭重启 → export → Node/Android preflight），并完成 expanded/compact/2.0x 的可视化与键盘焦点证据；随后另立 Windows 原生 build/signing/WebView2/SQLite 性能合同。不得把这些证据缺口扩展为 P7 同步、P8 Agent、Hub、Provider、Prompt/RunSpec 或真实费用。

## P6-B 离线 Desktop 工作台、本地所有权与交换导入导出（完成；P6/项目未结束）

- 独立合同：`P6B_DESKTOP_LOCAL_WORKBENCH_CONTRACT.md`。Desktop 真值为 Rust `DesktopWorkspaceStore` 所有的 app-private SQLite `workspace.sqlite3`，migration 以 `PRAGMA user_version=1` 管理 `workspaces`、canonical exchange IR、asset index、import journal 与仅非敏感布局表；不读取、不迁移、也不依赖 Android Room 表名或路径。启动会清理未提交 staging；内容寻址 asset / 原包先落 app-private allowlist，再由 SQLite transaction 原子暴露 workspace，因此中断最多留下无引用 blob，绝不留下半 workspace。
- Rust strict preflight 对 `.nfai-exchange` 限制 ZIP/entry/解压大小、Manifest 逐项 hash/bytes、未知 entry、重复/跨域 stable ID、message tree、relation、content hash、asset content address、semantic hash 与禁用字段；Markdown/HTML/code 只作为不可信文本 IR。非空 workspace 没有 import target：每次只允许明确创建新独立 workspace，重复包拒绝，绝不 merge / overwrite / replace。
- 再导出只支持当前 workspace 完整范围：SQLite IR → canonical package → app-private/用户所选 `.nfai-exchange` 临时写入 → 同文件 Rust strict preflight；stable ID、revision、tree、relation、asset hash 与 semantic IR 保真。已用 macOS 原生系统 picker 导入 golden，再用系统 save picker 写入 `/tmp/nanfeng-ai-p6b-ui-roundtrip.nfai-exchange`；Rust 界面显示回读 `ad41c1ee6aa6…`，Node `preflightPackage` 与 golden `canonicalJson` 精确相等。
- 工作台为 Expanded 三栏：左 Workspace / Project / Conversation tree，中间 Conversation 或 Knowledge / Memory 文本画布，右可折叠 Inspector；compact 窗口把侧树改可恢复 drawer 并顺序放入 Inspector，不把手机页面拉宽。`Cmd/Ctrl+O`、`Cmd/Ctrl+E`、`Cmd/Ctrl+\\` 与 Escape 已接通；所有 app 自有 Dialog/Pickers 为纯白。P6-B 明确只读 Project、Conversation、Knowledge、Memory、relation；没有先行写入 CRUD，因此不存在未合同化的 revision/undo/soft-delete。
- 安全：capability 仅 `core`、dialog 与五个 typed Rust commands；无 shell/process/http/updater/global filesystem plugin，CSP `connect-src 'self'`，不加载远程页面。初版浏览器桥接问题已修复为 Tauri 2 `window.__TAURI_INTERNALS__.invoke`，native app 不再回退 Web fixture；真实 app `tauri://localhost` 已显示 Rust SQLite 空态、导入预检、导入后的三栏内容及 Inspector。
- 自动：前端 `typecheck/lint/test/build` 全通过（3 UI/security/compact keyboard tests）；Rust `cargo fmt --check`、`clippy -D warnings`、`test`（4 tests：migration/reopen、atomic import/export、corrupt/duplicate ID、interruption rollback）和 `check` 均通过。`protocol/scripts/run-golden.mjs` 仍得到 semantic `ad41…4031` / package `74078…b4ec` / 3135 B；Android Studio JBR 下仅重新运行跨端 `:app:testDebugUnitTest --tests com.nanzhufeng.ai.domain.P6AExchangeContractsTest --rerun-tasks` 成功。协议未变，未升 Android version、未重跑无关全量/Lint/签名、未安装 emulator/操作 OPPO。
- macOS：`cargo tauri build --bundles app` 成功，开发包为 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`（13 MiB）；最终 executable SHA-256 `ff47ea8607f6d463aa901c4a37cc14fb4f852d512cd4a85b883a1ea1ec058d58`。它是 **ad-hoc 开发签名**（TeamIdentifier none），未 notarized、不是正式 macOS 交付。通过真实关闭再启动，SQLite 中 `P6-A 交换夹具`、tree、asset 和 Inspector hash 仍在。内置 Browser 对终端 localhost 返回 `ERR_CONNECTION_TIMED_OUT`，而终端 `curl` 为 200；因此浏览器渲染 QA 未通过该路径，改以实际 Tauri webview/accessibility tree 验证，不将其伪报为 Browser 通过。
- P6-C 才裁决完整本地工作台：Project / Conversation / Knowledge 写入、revision、undo / soft-delete、模型/成本 local metadata 与更深的多 workspace navigation；任何写入必须另立 transaction、历史和跨端导出合同。Windows installer/signing/WebView2、macOS Developer ID/notarization、真正多窗口/2.0x 人工视觉、全键盘顺序和 Windows SQLite 性能仍是独立债务。无 Provider、账号、同步、Hub、Agent、Prompt、RunSpec、图片、费用或 egress。

## P6-A Desktop 技术决策门 + nfai.exchange.v1（完成；P6/项目未结束）

- ADR-002 已选择 **Tauri 2** 作为 P6-B 的本地 Desktop 工作台基础；PWA 仅为 File System Access / OPFS / 下载 fallback 的能力边界，不能成为 Desktop 真值 DB。依据、官方核验日期、版本锁定、比较、复审条件和 Windows debt 在 `ADR-002-P6A_DESKTOP_AND_EXCHANGE.md`。无 Electron/Flutter/.NET 的项目证据优于 Tauri，故未建立第三实现路线。
- 新增 `protocol/nfai.exchange.v1.schema.json`、golden fixture、确定性 ZIP/canonical hash runner；`NFAI_EXCHANGE_V1_CONTRACT.md` 是交换协议正文。它与 P5-D `.nfai-backup` 明确分离：不共享 Android Room、表名、路径或中文文案；只交换 projects/conversations/message tree/knowledge/memory/relations/safe settings/asset refs。Key/credential/Provider raw/Prompt/RunSpec/runtime/diagnostic/route preference/URI/path/Keystore 全部排除；内容不可信且不执行。
- Android `NfaiExchangeV1Gateway` 要求显式 `NfaiExchangeExportSelection` 与安全快照，导出或 app-private staged strict preflight 均不写生产 Room；非空库不自动合并，导入落库/替换是后续独立合同。Desktop spike 是 memory IR + 宽屏工作台，不打开 Android DB；唯一 Rust command 只将用户 dialog 选取的受限 `.nfai-exchange` 暂存私有 app data，无 shell/广域 fs/SQL/network/updater plugin，CSP 禁止 egress。
- 实证：同一 golden 在 Desktop 已 byte-for-byte roundtrip，semantic `ad41c1ee6aa64b9e2f218034dbefccc333c43fb923b874b12ff51ce5972d4031`、package `74078bf5bdc1cbe53fbc4ac88029f8039b7aed9f8d0e79ea1f6c59ecf6aeb4ec`；Android explicit export→preflight 得到相同 semantic hash。Desktop protocol/lint/type boundary/test/build/Rust cargo check 均通过；Android Studio JBR 下 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --rerun-tasks` 通过，190 tests / 0 failures，Lint 0 errors / 19 warnings（SDK/Gradle/依赖、第三方 BouncyCastle、图标/KTX 建议；没有任何 warning 指向本轮交换代码）。`0.3.0-p6a` / code 40 的正式签名 Debug SHA-256 `b5af8f081f090f3a1177a23ca127bc2f1ec11592c820f1a1b88e975e6d141593`、Release `48119b5bd6586cec38f3ea47ed0fb3a4b7bd5c71329d98c6fa2c0c417ccd83e0`，v2/v3 true、证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。Debug 已同签名 `install -r` 到 `emulator-5554`，设备 code 40/version `0.3.0-p6a`，回拉 `base.apk` hash 一致；交付固定在 `delivery/p6a/`。
- Windows `.msi`、Windows signing/WebView2、真实 picker、updater、Windows SQLite/附件性能与正式 Desktop 包均未验证；不称为 Windows 交付。下一安全动作是 P6-B 的选定栈本地 Desktop 工作台基础，保留无账号启动、无 egress，不能进入 P7/同步/账号/Hub/Agent/真实 Provider。

- 本轮结尾 `context_gate.py` 返回 `HANDOFF`（context 74.8%，有效 Token 286,954）；交接卡已创建于 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-13T10-20-07-019ff8eb-71f6-7b22-977f-6943f102929f.md`。下一线程只读本 handoff、ADR-002、交换合同与现场，不回读旧聊天全文。

## P5-D 本地备份、恢复、迁移与交付基线（本地实现完成；P5/项目未结束）

- 独立合同：`P5D_LOCAL_BACKUP_RESTORE_DELIVERY_CONTRACT.md`。`AndroidLocalBackupRestoreManager` 是唯一所有者；用户只能经 SAF Create/OpenDocument 导出/导入。`.nfai-backup` 固定为 Manifest v1 + SQLite `VACUUM INTO` 一致性快照 + 受控私有资产，逐 entry/Manifest/SAF 回读 SHA-256；绝不复制运行中的 WAL/SHM。只纳入业务 Room 与 attachments/Markdown/JSON/PDF/Web 私有资产；Provider 凭据/引用、路由偏好、诊断、导出、registry、令牌、签名与 Keystore 绝不入包。存在高敏 secret、未知根、symlink、path traversal、重复 entry、hash/version/schema/大小异常或压缩炸弹时整体拒绝。
- 恢复先 app-private 隔离复制与只读 preflight，展示版本/Schema/表计数/资产字节/本地冲突；空库可恢复，非空只能强确认“替换本地”或取消，不做 merge。替换先建 app-private recovery checkpoint，候选 staging 完整性/计数/SQLite `integrity_check` 通过后才关闭 Room 切换；失败尝试 rollback，进程中断稳定为 INTERRUPTED/FAILED 且不自动继续。成功后明确要求完全重启 App，避免旧 Container/Repository/Room 引用；P5-B 冷启动仍只把运行态降级为 `FAILED(INTERRUPTED)`，不重放网络、Provider 或写入。
- Auto Backup 继续 fail-closed：`allowBackup=false`，`data_extraction_rules.xml` 与 `backup_rules.xml` 均排除 database/file/sharedpref/root 和 device-transfer。自动合同新增真实 ZIP roundtrip/资产白名单/高敏整体拒绝/1→17 迁移链和 Auto Backup Manifest；`testDebugUnitTest`、`lintDebug` 均成功，Lint 仍只有既有 14 warnings、0 errors。
- 交付已冻结于 `delivery/p5d/`：`0.3.0-p5d` / code 39 的正式签名 Debug/Release，SHA-256 分别为 `82d0cbeee934ebdfec07a6602fed2e7729dada17d378dabe645ad4f436fa115c` / `ebfb48ae884e98113f9813868637ea0dcbc9058913799123bce652565ecc57b6`；v2/v3 true，公开证书 SHA-256 仍为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。Debug 已同签名 `install -r` 到 `emulator-5554`，设备 code 39/`0.3.0-p5d`，回拉 `base.apk` 与 Debug hash 一致；最终 UI 回到 portrait/1.0x/Capture。
- 真实 SAF：模拟器已从纯白 Settings → 隐私与数据 → 备份与恢复 → DocumentsUI Downloads 执行导出。当前长期验收库触发共享高敏门禁，导出整体拒绝，未生成外流包；这符合合同，未读取/清理/绕过旧数据。因此“真实 SAF 成功导出→非空替换→force-stop”的成功证据仍是独立债务。现有 `tmp/p4n-pull.ncbwyE/base.apk` 是正式同证书 P4-N/code33、Schema 16 的历史起点（在 P4-O 前）；但 `emulator-5554` 已有更高 schema 的长期数据，不能无卸载/清数据安全降级创建旧 fixture，故真实 16→17 APK 覆盖迁移也保持设备债务，不能把自动迁移链或签名核验写成该证据。未操作 OPPO、GitHub、商店、Provider、Prompt/RunSpec/图片/费用或云同步。

## 当前目标与停止点

- P5-C 隐私、数据管理与安全诊断已建立独立合同 `P5C_PRIVACY_DATA_DIAGNOSTICS_CONTRACT.md` 并完成首个本地实现，**但不是 P5 或项目终点**。`PrivacyDataManager` 是 Settings 中“隐私与数据”纯白 Dialog 的唯一清单、范围预览、删除协调和用户发起诊断导出入口。清单仅读取聚合 count/bytes：Conversation/message/draft/invocation、Projects、Memory、Knowledge/relation、四类 Adapter task/私有资产、Eval、Capture 附件；凭据仅显示存在性，不读取 Key。文案以当前可验证能力为准：本地优先、Provider inference `OpenRouterEgressPolicy.Disabled`、Web Adapter 仅用户确认公共 HTTPS、无后台 telemetry/upload/sync。
- P5-C 删除面已有四个预览范围：临时/失败任务资产、Eval run/report、Knowledge/Memory 回收站和全部本地业务数据。全部范围须输入“删除全部本地业务数据”，不会清 Android Keystore/安装签名身份；预览 fingerprint 变化拒绝执行；Room 使用 transaction，文件移动到 app-private pending 根后才删除，失败回滚/报告 partial。根路径以 canonical path、受控目录及非 symlink 限制。**仍需补强**：当前 emulator 已有历史 FAILED Adapter 任务，无法隔离为仅本轮 fixture，故依用户“不删除现有长期验收数据”边界，本轮没有执行真实删除；需要下一 P5-C 收口加入受控 task 精确选择或全新隔离 emulator session 后补证，不能把代码或单测称作该真机删除验收。
- P5-C 诊断由用户从系统 SAF CreateDocument 明确选址：私有 `.part → fsync → atomic move` 后写入 URI，回读 SHA-256 才成功。文件为 `nanfeng-ai.security-diagnostic` v1 envelope（Manifest + payload SHA-256）；allowlist 只含 app/version/schema/egress、API/窗口摘要、能力开关、聚合数量、受控 error code。正文/标题/Prompt/Key/token/Authorization/URL/URI/path/附件字节/外部 IP/数据库一律不在 schema，红队模式整体拒绝。API 35 `emulator-5554` 已真实打开 DocumentsUI Downloads、保存 `nanfeng-ai-security-diagnostic.json`，app 显示回读短 hash `f6420cff1bb5…`；拉回 Downloads 文件 SHA-256 为 `f6420cff1bb5ea8fcb05fc4a5fc2d56c7bd8b1e21f349e36d1e208036a6c6826`，无敏感字段匹配。force-stop 后 Settings 正常恢复且没有自动导出/删除；最终恢复 Capture。未操作 OPPO、未读写 Key、未构造 Prompt/RunSpec、未发 Provider HTTP/图片。
- P5-C 自动与包：新增 P5-C allowlist/高敏/强确认与 Manifest permission 合同；Android Studio JBR 下全量 `:app:testDebugUnitTest --rerun-tasks` 为 181 tests、0 failures/errors，`lintDebug` 为 0 errors/14 existing warnings。`0.3.0-p5c` / code 37 的最终正式签名 Debug/Release 构建成功；Debug SHA-256 `2a4f5959e01c37f202702645b01638465ac2eedbb57540e59fad5b05d21e95c1`，Release `6aed2e198c3110eb1f208c20dd64484052df1db8f59bc9dab5bef509a3a24bd9`；v2/v3 true，证书 SHA-256 仍为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。同签名 Debug `install -r` 到 `emulator-5554`，设备 code 37 / `0.3.0-p5c`，回拉 base.apk hash 与最终 Debug 一致。
- P5-C 当前停止点：不得称为完整 P5-C 退出。还缺真实隔离 fixture 的成功删除/partial retry、事务/文件协调与 orphan active-reference 边界的 Android integration tests、Dialog 中安全清单全量分页/大小的可用性检查；不能提前进入 P5-D。下一安全动作是只补 P5-C 的这些缺口，不接备份、同步、Provider/telemetry 或 OPPO。
- P5-C2（2026-08-13）已补齐并可作为 P5-C 退出证据：临时/失败任务资产不再全选删除；Dialog 先列出 Adapter、12 位 SHA-256 安全 ID 摘要、终态、私有文件计数/字节与“失败证据会随任务删除”后果，默认零选择。仅 `FAILED/CANCELLED` 且无 ACTIVE Knowledge、无 export 命名空间引用、storage key 固定 allowlist、canonical 越根/任意 symlink 均拒绝的任务可被选择。所选任务再次 fingerprint 预览后，按 Adapter/ID 稳定排序执行“app-private quarantine → Room transaction 删除 task/items/pages → 清 quarantine”；清理失败不会冒充成功，而是留下仅用户点击的 FAILED 剩余项重试，重复确认对已不存在的同一选择为幂等 no-op，Activity/进程重建不自动删除或重试。新增 Robolectric 真 Room + app-private 文件合同：一项取消 fixture 精确删除且另一历史任务/文件仍存在；强制 file delete 失败后 Room 仅删一次、quarantine 留存，再由明确 retry 清除。
- P5-C2 emulator-5554 真实证据：通过产品 Markdown DocumentsUI 选择仅含标题的 34 B 非敏感 `p5c-device-fixture.md`，正常形成 FAILED 私有副本；隐私页所有候选默认未选，仅选 `MARKDOWN · 6571f7763b8c · FAILED · 1 个 / 34 B`，二次预览为 `selected_markdown_tasks 1` 后确认。清单从 `markdown_tasks 3 / private assets 14` 精确变为 `2 / 13`，Capture/Conversation/message/draft/invocation/Project/Memory/Knowledge/relation/JSON/PDF/Web/Eval 聚合均保持原值；force-stop 冷启动后 fixture 未回现。设备没有执行 partial 文件失败（为保持历史数据与设备可控性），该层仅由上述真实 Room + fake deleter 自动合同证明；不得把它说成设备级文件失败。P5-C 已可收口；下一候选是只制定/实现 P5-D 备份恢复合同，不提前接同步、Provider、telemetry、P6+ 或 OPPO。
- P5-C2 最终自动与交付：Android Studio JBR 下全量 `:app:testDebugUnitTest --rerun-tasks` 为 185 tests、0 failures/errors；`:app:lintDebug --rerun-tasks` 为 0 errors / 15 warnings（SDK/Gradle/依赖、第三方 BouncyCastle、既有图标/KTX 建议；本轮不改图标）。`0.3.0-p5c2` / code 38 的正式签名 Debug SHA-256 `94a369b6ce94081c6745557cf1ac5202576bd86f70b364634b5adbaf592fd1e6`，Release `005778a31fee39b15d5c0f9245540e1a72035f5df2c89afd17739bae37937463`；v2/v3 true，证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。正式 Debug 已同签名 `install -r` 至 emulator-5554，设备 code 38/version `0.3.0-p5c2`，回拉 base.apk SHA-256 与 Debug 完全一致。最终恢复 portrait / 1.0x / Capture；未操作 OPPO、未读写 Key、未构造 Provider/Prompt/RunSpec、未发 egress、未创建测试 APK。
- 总目标：继续按 `MASTER_DEVELOPMENT_BLUEPRINT.md` 推进；P5-B（Android 后台、任务取消/恢复与电量产品化基线）已完成本地实现与分层验收，它不是 P5 或项目终点；下一阶段为独立合同的 P5-C 隐私/诊断。
- P5-B 合同与所有权：`P5B_BACKGROUND_CANCELLATION_BATTERY_CONTRACT.md`、`TaskExecutionPolicy`、`TaskRecoveryAudit` 是唯一应用级执行治理入口；它们只审计 Markdown/JSON/PDF/Web/Eval/Conversation local fixture 的 owner、dispatcher、恢复和重试边界，不合并任何 Adapter 的任务表或领域语义。冷启动只将 Markdown/JSON `PARSING`、PDF `PREFLIGHT/EXTRACTING`、Web `QUEUED/FETCHING/EXTRACTING` 置为 `FAILED(INTERRUPTED)`；local fixture 的 `STREAMING` 追加安全 `FAILED(INTERRUPTED)`，保留部分输出。没有任何自动重放、自动 Knowledge 写入、网页重抓、Provider/Intent 重放或伪造 Eval 继续。
- P5-B Android/电量判断：明确**不引入 WorkManager**。所有可能长运行的任务都需要用户逐项确认或不允许安全重放：导入需用户确认 Knowledge，网页必须在前台重新确认，Eval/fixture 不得伪造继续。因此 Manifest/依赖仍无 WorkManager、Service、Receiver、WakeLock、JobScheduler/Alarm/BOOT receiver 或周期轮询；Web 仅可在前台一次性运行，网络恢复、后台与重启均不自动重试。PDF adapter 增加 `TaskCancellation` 的逐页协作取消、2 MiB mixed-memory 上限和真实页进度持久化；IO/Default 与 Room 边界保持非主线程。
- P5-B 可见状态：Markdown/JSON/PDF/Web/Eval 卡片均明确“仅在前台”“系统中断后手动重试”或“不自动重试”；App 自有对话/选择面保持纯白。取消是稳定终态，完成后取消是 no-op；确认继续沿既有 intent 指纹，不能重复写 Knowledge。
- P5-B 自动验证：Android Studio JBR 下 `:app:testDebugUnitTest --rerun-tasks` 为 176 tests、0 failures/errors；新增 P5-B policy/recovery、完成后取消稳定和 Manifest/依赖反保活合同。`:app:lintDebug --rerun-tasks` 为 0 errors / 14 existing warnings（SDK/Gradle/依赖、旧 KTX/图标建议；不改图标）。
- P5-B 正式签名：`0.3.0-p5b` / code 36 的 Debug/Release 均由现有正式证书签名；v2/v3 为 true，证书 SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。Debug SHA-256 `920d9e1502b5aeacd078c5485ed5d815b22b548cb5b76a2aae3024922648278a`；Release SHA-256 `68028a7373dd0889d01648e300c862c2e1ed9bcec453c7714389d00652bb1285`。
- P5-B emulator 证据：仅 API 35 `emulator-5554`，同签名 Debug `install -r` 到 code 36 后确认 Capture 启动；Adapters 页面可见前台/中断/手动重试及网页无自动重试文案。用户前台输入 `https://localhost`、勾选确认后得到持久 `FAILED`；force-stop 冷启动、进入网页任务面后仍为 `FAILED · https://localhost`，没有自动网络重试。最终恢复 portrait / font 1.0 / Capture；从设备回拉 `base.apk` SHA-256 与最终 Debug 完全一致。未操作 OPPO、未读写 Key、未构造 Prompt/RunSpec、未发 Provider HTTP 或产生模型费用/图片。
- P5-B 诚实剩余项：自动合同覆盖取消竞态、重复 retry、完成后取消、恢复映射与不重复 Knowledge；PDF 的逐页协作取消已实现。此次模拟器会话中 local fixture 流在可见停止触发前已自然 `COMPLETED`，故“真实长任务用户取消”的设备级时序证据仍待下一独立 emulator 会话补证，不能用单测或源码替代。真实网络成功、OPPO、TalkBack 人工听觉、图标、真实 Provider/成本与发布同样仍独立债务。
- P5-A 当前实现状态：已新增 `P5A_ADAPTIVE_ACCESSIBILITY_BASELINE_CONTRACT.md`、`P5AAdaptiveUi.kt` 和 `P5AAdaptiveNavigationContractsTest.kt`。Activity 采用 edge-to-edge；自有可测 Compact/Expanded 分类（`<840dp` 或 font scale `>=1.5` 为 Compact）；手机底栏/宽屏 NavigationRail；同一 route 的有界双栏工作台；safe drawing/cutout/IME insets；显式 Intent/deep link (`nanfengai://workspace/<route>`)；以及 `SavedStateHandle` 加 app-private route token 的冷启动/force-stop 恢复。Capture、Conversation、Knowledge、Projects、Memory、Context、Eval、Settings/Provider 与 Adapter 控制面继续复用现有 ViewModel/Room；Schema 17、Domain、Provider 与 egress 均未改变。
- P5-A 自动验证：Android Studio JBR 21.0.10 下，`JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' PATH="$JAVA_HOME/bin:$PATH" ./gradlew :app:testDebugUnitTest :app:lintDebug --rerun-tasks` 通过；全量 135 tests、0 failures / 0 errors。新增 7 条 P5-A 合同覆盖尺寸/大字体分类、route 保存/显式 Intent/未知回退/持久 route、导航 label、纯白选择面与同形状 token、Compact 的“更多”选中语义。Lint 为 0 errors / 14 warnings（SDK/Gradle/依赖、旧 KTX/图标建议；不改图标）。项目没有 `androidTest` 用例，故 `preDebugAndroidTestBuild` 为 SKIPPED；未创建或安装 helper/test APK。
- P5-A 正式签名：`0.3.0-p5a` / code 35 的 Debug/Release 均以现有正式证书签名，APK Signature Scheme v2/v3 均为 true，证书 SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。Debug SHA-256 `266d3abea604c23b6f4432a1ecd75e25b9495d8798035644e435a72a46bd41ed`；Release SHA-256 `eef0d4010b27c95d5068917361035423be0b65e5491582598bce704df787cca2`。
- P5-A 模拟器证据：只操作 API 35 `emulator-5554`（1140×2616 / 442 dpi）。同签名 Debug `install -r` 到 code 35 后冷启动 1.787s；回拉设备 `base.apk` SHA-256 与上述 Debug 包一致。已见证 compact portrait 的 1.3x bottom navigation（捕获/对话/知识/设置/更多）与无裁切滚动、2.0x + IME 下输入/保存区可通过同一内部滚动区完整到达、landscape expanded NavigationRail + Capture 双栏、深链 `control` 的 Projects/Memory/Context/Eval/Adapter 可达面，以及 deep link `knowledge` 后 force-stop/普通启动仍恢复知识 route。已将模拟器恢复为 portrait / font 1.0 / Capture；未操作 OPPO。
- P5-A 诚实剩余项：UI 已有 heading、live region、稳定 label、语义 state、`48dp` 最小目标和统一圆角交互 token；但尚未在真实 TalkBack 下逐项朗读，也未建立完整 Tab/DPAD traversal 的 instrumentation 证据。2.0x + IME 时保存操作可滚动到键盘上方，但不是固定悬浮按钮；这两项是 P5-A 可访问性外部验证债务，不能以源码或旧 APK 代替。真实 Provider/Key/成本、OPPO、图标与发布仍独立后置。
- P5-A 下一安全动作：在不改变既有本地主线的前提下，先补 emulator-5554 的 TalkBack/键盘焦点外部验证，之后再按总蓝图进入 P5-B；不得操作 OPPO，且不得读取 Key、构造 Prompt/RunSpec 或触发 Provider HTTP。
- P5-A2 无障碍补证（2026-08-13）：`emulator-5554` 已确认预装 Android Accessibility Suite TalkBack 15.0.0.639625893，并以系统可见 `Enabled services` / `Bound services` 验证 spoken/haptic/audible 服务可绑定；无未知 APK 下载或安装。真实 UiAutomator node dump 覆盖 Capture 手工文本、保存、底部导航/更多、网页 Adapter Dialog 的 URL/复选框/风险提示/动作以及 route live 文本，装饰图没有独立 label。发现多行编辑器会吞入 Tab 的真实键盘缺陷后，新增 P5-A 根壳和 Dialog 字段/容器的 Tab/Shift-Tab 焦点移动；Capture 从已聚焦文本框按 Tab 会进入后续保存动作且 node 文本不含制表符，Web Dialog 的 URL→复选框为真实焦点链。DPAD 右移至“更多”并以 Space 打开控制面、底栏对话以 Enter 打开均由真实 keyevent/node dump 证实。TalkBack 语义服务可用，但本桌面会话未录制/人工监听模拟器扬声器，故听觉逐句文案仍保留为真人设备债务；不得把 node/service 证据称作听觉验收。
- P5-A2 自动/包证据：新增第 8 条 P5-A 合同，覆盖 Tab/Shift-Tab 方向；全量 `:app:testDebugUnitTest --rerun-tasks` 为 173 tests、0 failures/errors，`lintDebug` 为 0 errors/14 existing warnings。重建 `0.3.0-p5a` / code 35 正式签名 Debug/Release，v2/v3 与现有证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5` 通过；最终 Debug SHA-256 `a3d69cef3472262139b858854906b8a88ebf816119982bb39c35f0e13a35060a`、Release SHA-256 `ce148fc4e2f8617da416cf8179f90d23d17f0b5ee1077e4d4253017c70c4436a`。最终 Debug 已同签名 `install -r`，设备回拉 `base.apk` SHA-256 与 Debug 完全一致。模拟器已恢复 portrait / 1.0x / Capture；未操作 OPPO、Key、Provider 或 egress。
- 本轮完成：P4-O 独立严格 HTTPS 网页文本快照 Adapter 已收口。`用户确认 URL → AndroidPublicWebFetcher → web_text_snapshot_tasks/items → KnowledgeDomain` 只抓取一个公共 HTTPS 主 HTML，逐项确认才写入；完整合同为 `P4O_HTTPS_WEB_TEXT_SNAPSHOT_CONTRACT.md`。P4-O 不是 P4 或项目终点。
- P3 判断：P3-A–H 本地 Conversation 基线稳定；真实流、真实 Usage/费用、真实长会话性能与真实图片外发仍是用户明确后置的独立验收债务。它们未通过，不能由 Mock/loopback/模拟器替代，但不阻塞 P4 本地主体。
- 安全停止点：不得读取/要求/写入 Key，不签发真实 RunSpec，不发 Provider HTTP，不产生费用或图片外发。`OpenRouterEgressPolicy.Disabled` 保持；只操作 `emulator-5554`，不操作 OPPO；图标验收暂停且不修改。

## P4-A 已实现事实

- `ProjectDomain → ManageProjectUseCase → RoomProjectRepository` 是 Project 生命周期、标题/说明、置顶/归档、项目指令 revision、会话成员和 Knowledge scope 的唯一语义/持久化链。Project 使用稳定 ID；标题 trim 后 1–120 Unicode code points，说明最多 2,000；颜色/图标只是项目语义，和 launcher icon 无关。
- 项目指令追加 `source=USER`、连续 revision、创建时间与 SHA-256；同内容幂等，空内容是可审计的“清空当前项目指令”revision，不是未知值。无 Prompt、Provider 原始响应、Key、Authorization 或路径字段。
- Conversation 只持有可空 `projectId`，不复制项目指令；归入/移出是明确用户操作，保留消息树、Invocation、附件和现有 Conversation Export。一个 Conversation 同时最多一个 Project。项目归档不级联 Conversation/Knowledge，本阶段无删除入口。
- `knowledge_project_scopes` 仅存 Knowledge ID → 可空 Project ID（`null` 为 global）；不复制内容，不开启自动检索、Memory 或知识管理扩张。
- Schema 8→9 显式只增 `projects`、`project_instruction_revisions`、`project_intents`、`knowledge_project_scopes` 与索引；复用 `conversations.projectId`，不清库、不重建、不改写 P1–P3 数据。
- `ProjectContextSnapshot / InstructionResolution` 是本地只读 IR，固定 `SYSTEM > SAFETY > PROJECT > CONVERSATION > CURRENT_USER`，记录版本、来源和 hash；不构造 Prompt、RunSpec、Authorization、HTTP 或 Invocation 历史。附件、网页和文档均是不可信数据，不能成为项目指令。
- UI 已有 Projects 空态/列表、创建、置顶、归档/恢复、详情、指令编辑和 revision 摘要；Conversation 工作区有明确的归入/移出 Project 选择。自有 Dialog/选择面为 `#FFFFFFFF`，没有账号/共享/云/仪表盘或自动 Memory。

## P4-B 已实现事实

- `ContextSelectionDomain → ReadContextSelectionUseCase` 是当前会话 Context 选择的唯一公开本地入口。它从 `ConversationRepository` 和 `ProjectRepository` 只读回读，生成瞬时、无持久化的 `ContextSelectionSnapshot`；不存在 Conversation 或指向不存在 Project 时显式拒绝，绝不降级读取任意项目或全局资料。
- 快照只保存 Conversation/当前 Branch、Project revision/hash 与当前根→叶路径的 Message ID、角色、交付状态、ContentBlock 数量、内容 SHA-256。它不含消息/项目指令正文、Prompt、Token、RunSpec、Authorization、Provider 请求、Invocation、附件字节、URI 或路径。
- 草稿、兄弟分支、附件和 Tool 结果为显式 `EXCLUDED`；Knowledge、Memory、检索、摘要/压缩和缓存前缀为显式 `NOT_IMPLEMENTED`。既有 `ConversationSettings.memorySources` 没有被读取、解析、保存或自动加载。
- P4-B 不改 Room/Schema（仍为 Schema 9）、Migration、Export、UI、后台任务或 egress。`ReadConversationContextPathUseCase` 保持 Conversation Domain 的低层兼容读取，不是 P4-B 的 Context 公共入口。

## P4-C 已实现事实

- `MemoryDomain → ManageMemoryUseCase → RoomMemoryRepository` 是长期 Memory 的唯一语义/写入链；Memory UI 只提交用户明确的创建、编辑、暂停/恢复、单项/批量软删除与冲突决策。没有自动提取、静默保存或自动 Context 注入。
- `MemoryId`、revision、intent 与 conflict 是稳定 UUID。正文/标题、`GLOBAL/PROJECT/CONVERSATION` 互斥 scope、`USER_CONFIRMED/MANUAL_IMPORT` 来源、`ACTIVE/PAUSED/DELETED` 状态、来源稳定 ID/摘要、时间与 hash 为独立本地事实；P4-C UI 只产生 `USER_CONFIRMED`。
- 同 scope 中规范化标题/正文 hash 相同即返回既有项；同 scope 同规范化标题但正文不同会保存 PENDING conflict，必须由用户选择保留已有、并存或作为既有项新 revision。编辑、状态变化和删除均追加 revision；删除不清正文历史，但普通活动列表不显示 deleted 项正文。
- 密码、API Key、Authorization/Bearer、恢复码、完整支付卡号等模式在 Domain 写入前拒绝；拒绝只返回安全错误码，不创建 Memory、revision、intent 或 conflict，因此被拒正文不落库。第三方网页、Tool、附件、Provider payload、未确认 AI 候选均没有正式写入入口。
- Schema 9→10 只新增 `memories`、`memory_revisions`、`memory_intents`、`memory_conflicts` 和索引，所有 Project/Conversation 外键为 `NO ACTION`；不清库、不重建、不改写 P1–P4-B。Project/Conversation 归档、移出或生命周期变化不静默删除 Memory。
- UI 已有 Memory 列表/空态、搜索、scope/status 筛选、创建/编辑、暂停/恢复、删除/批量删除确认、revision/来源摘要和确定性冲突选择；App 自有 Dialog 全为 `#FFFFFFFF`，并明确“不会自动加入对话上下文”。没有开发后门、导出、分享、上传或同步。
- P4-B `ContextSelectionDomain` 和 `ConversationSettings.memorySources` 未改动：Context metadata snapshot 仍只输出 Conversation/Project metadata；它本身继续不读取 Memory，也不会构造 Prompt。

## P4-D 已实现事实

- `ContextBodySelectionDomain → ReadExplicitContextBodyUseCase` 是唯一 L0–L3 正文选择入口。它先复核 P4-B 的当前会话/Project metadata，再只读 Conversation、Project、Memory Repository，生成瞬时 `ExplicitContextBodySnapshot`；没有 Room 写入、Schema/Migration、Export、Invocation、Prompt、RunSpec、Key 或 HTTP 路径。
- 所有控制默认关闭且只保存在 Context 控制 ViewModel 内存中。L0 仅允许逐项选择 `ACTIVE GLOBAL` Memory；L1 允许当前 Project 最新非空指令和同 Project `ACTIVE` Memory；L2 允许当前根→叶 Text 与同 Conversation `ACTIVE` Memory；L3 正文明确排除，P4-M 另以独立元数据 IR 提供本机预览。
- `ConversationSettings.memorySources` 没有被读取、解析、写回或迁移。暂停、删除、缺失、其他 Project 或其他 Conversation 的 Memory 都不进入候选集；任何越权 ID 使本次请求整体拒绝，绝不返回部分正文。
- 当前路径只投影 `ContentBlock.Text`；草稿、兄弟分支、Attachment、URI、字节与 Tool result 都保持排除。项目 revision/当前路径在 metadata 与正文重读间变化时显式拒绝，避免旧 metadata 与新正文混合。
- P4-D 复用 `MemoryDomain.sensitiveRejection` 的单一高敏规则；密码、API Key、Authorization/Bearer、恢复码或完整支付卡号命中时整体拒绝，正文不被持久化、导出、记账或诊断复制。
- UI 从当前本地对话卡打开纯白 `#FFFFFFFF` Context 控制面，明确“所有项默认关闭”“不会发送给 Provider”；用户仅可逐项勾选后本机预览，关闭、重开或进程重建都丢弃选择。没有发送、保存、导出、分享或同步入口。

## P4-E 已实现事实

- `KnowledgeDomain → ManageKnowledgeUseCase → KnowledgeManagementRepository/Room` 是 Knowledge 标题/正文、GLOBAL/PROJECT scope、标签、`ACTIVE/ARCHIVED/DELETED` 和 append-only revision 的唯一管理链；P2 保存后的正式 Knowledge 仍从同一 Repository 读取和导出。
- Schema 10→11 仅向 `knowledge_items` 追加 lifecycle/hash 字段，并新增 `knowledge_revisions`、tag 与 revision-tag 关系/索引。迁移不 wipe：旧行生成 revision 1；Android SQLite 不假定 SHA 函数，旧空 hash 在本地 Domain 回读时重算。
- 搜索不使用 FTS、embedding 或 vector DB。标题、正文、标签、来源按 `Locale.ROOT` 的确定性子串匹配，默认 `ACTIVE`、`updatedAt DESC, KnowledgeId ASC`、最多 50 条、snippet 最多 240 字符；中文、英文、Markdown/代码均直接可检索。
- Knowledge 列表提供搜索与活动/归档/回收站筛选，详情显示标签、scope、revision 数，并提供归档/恢复和移入/恢复回收站的明确动作。Context 控制面中的 Knowledge 默认不列出也不选中：仅用户输入查询后显示当前 GLOBAL/Project 的 ACTIVE metadata/snippet，再逐项勾选。
- `ReadExplicitContextBodyUseCase` 保存用户选中项的 revision/hash 预期值；预览时重读 P4-B metadata 和 Knowledge，重新验证 ACTIVE、GLOBAL/当前 Project、revision/hash 与单一高敏 detector。任何越权、隐藏、变更或敏感命中都整体拒绝；选择只在 ViewModel 内存中，关闭/切换/重建即丢弃。

## P4-E 已验证等级

- 自动测试：全量 `:app:testDebugUnitTest --rerun-tasks` 通过，121 tests、0 failures / 0 errors；新增 P4-E 域/Room 合同覆盖中文/英文/Markdown/代码/标签搜索、隐藏状态、修订、归档/回收站、Room 重建与写前高敏拒绝。P4-A–D 回归仍通过。
- Lint：`:app:lintDebug --rerun-tasks` 为 0 errors / 11 warnings（既有 SDK、Gradle/依赖和图标建议；图标视觉继续暂停）。
- 正式签名构建：`0.3.0-p4e`（code 24）的 Debug 与 Release 通过；两包同为现有正式证书（SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`）签名。Debug SHA-256 `b30cb4810e51ec9c0212acba77b15fe25ecd7ee3751aab9855fceb9d79688c04`；Release SHA-256 `9809ef7d6859180452b41b037d63e8cff51875ba2ebee78e4882eaca5c6a3ae4`。
- API 35 `emulator-5554`：同签名 Debug `install -r` 覆盖安装后，以 `NanfengAiActivity` 冷启动成功（1.794s）；未操作 OPPO、未读取 Key、未发 HTTP/RunSpec。P4-E UI 的完整人工点击验收仍应在下一轮模拟器会话中保留为可见性补充，不把冷启动替代为交互验收。

## P4-F 已实现事实

- `ManageKnowledgeUseCase.duplicateCandidates → KnowledgeDeduplicationDomain` 是 P4-F 的唯一公开入口。用户只能从一条 `ACTIVE` Knowledge 详情明确发起；结果是本机内存中的只读列表，关闭详情、切换条目或进程重建即丢弃。
- 候选仅比较同一 `GLOBAL` 或同一 `PROJECT` scope 的 `ACTIVE` Knowledge；归档、回收站和其他 Project 全部排除。只接受“规范化标题相同”或“规范化标题/正文的 SHA-256 相同”两个确定性理由，排序固定为精确内容、标题、`updatedAt DESC`、Knowledge ID，最多 20 条。
- 锚点缺失/隐藏会明确拒绝；锚点或任一已匹配候选命中既有单一高敏 detector 时整体拒绝，绝不返回部分列表。候选界面只显示既有标题、修订和理由，不显示正文、附件、来源 URI 或 hash。
- P4-F 不新建表、不迁移 Schema（保持 11）、不写 Relationship/Merge/Import/Revision，也不改动 Project、Conversation、Memory、Context、Export、Invocation、同步、日志或诊断。它不读取 Key、不构造 Prompt/RunSpec、不发 HTTP、不产生费用或图片外发；`OpenRouterEgressPolicy.Disabled` 保持。

## P4-F 已验证等级

- 定向合同：`P4FKnowledgeDeduplicationContractsTest` 与 `P4FKnowledgeDeduplicationRoomContractsTest` 通过，覆盖同 scope/活动状态、规范化精确内容、标题候选、固定排序、高敏整体拒绝、缺失/隐藏锚点及 Room 查询不追加 revision。
- 全量自动测试：Android Studio JBR 下 `:app:testDebugUnitTest --rerun-tasks` 通过，125 tests、0 failures / 0 errors；P1–P4-E 回归继续通过。
- Lint：`:app:lintDebug --rerun-tasks` 为 0 errors / 11 warnings（既有 SDK、Gradle/依赖和图标建议；图标视觉继续暂停）。
- 正式签名构建：`0.3.0-p4f`（code 25）的 Debug 与 Release 通过；两包同为现有正式证书（SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`）签名，v2/v3 均通过。Debug SHA-256 `109c7354324e8be0cb746793aa7c6a36da2732f037189df8abafb9a770d2b06b`；Release SHA-256 `310c502e9cb533fabc133fb550c4430ee040d0fbd9292da1c224f1fa910215bf`。
- API 35 `emulator-5554`：同签名 Debug `install -r` 覆盖安装后，以 `NanfengAiActivity` 冷启动成功（1.976s），已回读 code 25 / `0.3.0-p4f`。未操作 OPPO、未读取 Key、未发 HTTP/RunSpec，也未为验收加入开发后门；P4-F 的模拟器完整人工点击验收仍是独立的可见性补充。

## P4-G 已实现事实

- `KnowledgeRelationshipDomain → ManageKnowledgeRelationshipsUseCase → KnowledgeRelationshipRepository/Room` 是关系类型、方向/对称性、同范围、endpoint revision/hash、高敏与幂等的唯一链。RELATED、DUPLICATE_CANDIDATE、CONTRADICTS 对称规范化；SUPPORTS 保留用户明确方向。
- 仅两个 ACTIVE 且同为 GLOBAL 或同 Project 的 Knowledge 可确认。缺失、隐藏、跨范围、自指向、过期 revision/hash、活动重复边或任一端点命中既有单一高敏 detector 时整体拒绝；P4-F 仅记录为建议来源，关闭候选不写关系。
- Schema 11→12 只新增 `knowledge_relationships`、append-only revisions、intent 和索引；不复制正文、URI、路径、附件或敏感值，也不触碰 Context、Prompt、Export、Provider、同步或 egress。撤销为 REVOKED 软状态并追加审计 revision。
- Knowledge 详情提供“建立本地关系”和“查看关系”；白色关系选择/审计 Dialog 可选类型、筛选活动/撤销记录和撤销关系。P4-E 可逆链同时修复两处真实缺陷：空列表仍保留状态筛选；归档/回收站列表改为直接从 P4-E 过滤快照投影，避免被 P2 ACTIVE-only 读取模型遮蔽。编辑保存被拒现显示中文原因并保留编辑器。

## P4-G 当前验证等级

- 定向合同：P4-G Domain 与 Room 合同通过，覆盖对称/方向、同范围、隐藏/自指向/过期/高敏/重复边、confirm 回放、撤销审计与 Schema 11→12 的旧表保留。
- 完整回归与正式签名构建：Android Studio JBR 下 `:app:testDebugUnitTest --rerun-tasks` 通过，130 tests、0 failures / 0 errors；`:app:lintDebug --rerun-tasks` 成功；Debug/Release assemble 成功。`0.3.0-p4g`（code 26）Debug SHA-256 `ef768e6bc91054fbb858380a760448b3a2b687e79d4eec5c6d5bc837473c9c8f`，Release SHA-256 `95c43e24ad581e609ecf36f6082796261fccc354902f3bdaf6a292a49d045feb`；两包同为现有正式证书（SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`），v2/v3 均通过。
- 模拟器：仅操作 API 35 `emulator-5554`。已用真实可见点击完成 P4-E 搜索、归档→归档筛选恢复、移入回收站→回收站筛选恢复；P4-F 仅本机只读候选显示“未发现”，关闭不建立关系；Context Knowledge 打开默认未勾选/空查询不列项，显式搜索 `P4` 后勾选 Alpha 并仅本机预览 1 条正文（明确“没有构造 Prompt 或发送内容”）；P4-G 以 `SUPPORTS` 建立 Alpha→Mock，活动列表显示审计修订 1，撤销后活动筛选为空、已撤销筛选显示审计修订 2。最终 Debug `install -r` 覆盖安装，冷启动 1.675s；从设备回拉 base.apk 与上述 Debug SHA-256 一致。未操作 OPPO、未读取 Key、未发 HTTP/RunSpec。

## P4-H 已实现事实

- 只新增 `MarkdownKnowledgeAdapter`：系统 OpenDocument 只请求 Markdown 类型，受限读取后立即复制到 App 私有 `markdown-import-assets/v1`。Task/Item Room 仅存稳定 ID、安全显示名、MIME、大小、SHA-256、受控 storage key、状态/失败/版本，绝不保存外部 URI、绝对路径或权限 token。
- Markdown 是惰性不可信文本：严格 UTF-8/BOM/LF，单文件 512 KiB、最多 100 条，ATX 标题、正文和可选 tags front matter 的确定性解析；多项只由单独一行 `<!-- nanfeng-ai:knowledge -->` 分隔，代码围栏内同形文本保持正文。空白、畸形、坏编码、超限和高敏安全拒绝；不执行链接/HTML/代码或内容指令。
- `ImportTask` 可持久保留已选择、私有复制、解析、待确认、部分完成、完成、失败、取消、逐项状态、原因与重试；关闭页面/重建可回读。每项默认未确认，可编辑标题/正文/标签、确认、跳过、取消或失败重试；只有确认通过 `ManageKnowledgeUseCase` 才写正式 Knowledge。
- Schema 12→13 仅追加 `markdown_import_tasks` / `markdown_import_items` 与索引。P4-F 没有自动合并，P4-G 没有自动关系/归档；没有 Memory/Context/Prompt/RunSpec/Provider/HTTP/Key 路径。
- Markdown 导出只接受 UI 中用户逐项勾选的 ACTIVE 正式 Knowledge，并生成版本化 manifest、原子 `.md` 写入、同文件回读和 SHA-256；不含 URI、路径、Key、Prompt、Provider payload、附件字节、关系推断、已删除项或临时选择，可回到同 Adapter 前检。最小 UI 已有导入入口、系统选择、持久任务列表/详情、逐项编辑/确认/跳过/取消/失败重试和 Markdown 导出范围确认；完整导出可见链仍须在模拟器独立复核，不能把代码接口误写成完整用户验收。
- 模拟器可见链发现并修复导入 UI 的真实断点：未先点“编辑此项”即“确认写入”时，旧 UI 会把显示的解析原值错误提交为空值。现在显式记录正在编辑的 Item；未编辑确认提交该 Item 的解析 title/body/tags，编辑态只作用于当前 Item，重载/切换任务清空暂存编辑态。

## P4-I 已实现事实

- `P4IOfflineEvalDataset → RunOfflineEvalUseCase → EvalRunRepository/Room` 是唯一离线 Eval 运行链。Fixture/Dataset 是打包、版本化、只读的非生产测试材料；运行、case result、确定性 assertion 与人工评分只追加到独立 Eval 表，绝不与 Conversation、Knowledge、Memory 共表或复用写入语义。
- 基线登记当前分支与草稿/附件/Tool 排除、Project/Memory/Knowledge scope、Knowledge 不自动 Context、高敏拒绝、revision/hash、Markdown/代码/长内容安全边界、流去重/恢复与本地安全渲染；中文、英文与恶意指令文本仅是 fixture 事实，不执行其中命令。
- 自动断言仅为可机械证明的事实覆盖与 `NO_EGRESS`，不把 Mock/fixture 输出或字符串相等伪装为回答质量，也不产生 Token、TTFT、cost、缓存收益、模型质量或用户价值。红队含 prompt injection、高敏、越 Project/Conversation、URI/路径/附件、自动关系/导入、旧 hash/revision 与畸形流的惰性边界。
- 人工评分从自动断言分离，可按相关性、事实性、完整性、安全性、可追溯性追加 `1..5` 或未评分；只存本地别名、rubric、时间与有限备注，不存账号或敏感身份。
- Schema 13→14 仅追加 `offline_eval_runs`、`offline_eval_case_results`、`offline_eval_assertions`、`offline_eval_human_scores` 及索引；没有 fixture 正文、生产正文、Key、Prompt、Provider payload、URI、路径或附件字节。无 wipe、无 P1–P4-H 改写。
- Eval UI 有明确“离线 Eval 基线”入口，显示运行/通过/失败、case/assertion、人工评分、两次同 dataset 运行比较与导出；App 自有 Dialog 是 `#FFFFFFFF`。报告为 app-private 原子 JSON + Manifest + SHA-256 并可回读检测篡改，强制标 `OFFLINE_LOCAL` / `realServiceVerified=false`。

## P4-H 当前验证等级

- 自动测试：定向 `P4HMarkdownPortabilityContractsTest`、`P4HMarkdownImportRoomContractsTest` 通过；修复后全量 `:app:testDebugUnitTest --rerun-tasks` 为 136 tests、0 failures / 0 errors。覆盖 BOM/换行、多项/代码、空白/编码/高敏/大小、DocumentsUI 的 `.md (1)` 展示后缀、安全导出选择与同 Adapter roundtrip、私有 Task 重建、逐项确认/跳过/取消及 Schema 12→13 旧表保留。
- 静态与构建：修复后 `:app:lintDebug` 通过（0 errors，既有 11 warnings）；`0.3.0-p4h`（code 27）正式签名 Debug/Release 均通过。Debug SHA-256 `45a6203219a06999829abdacb0e273c3219e7cfaf290a1d73a3bd5872fdc6c0f`；Release SHA-256 `5b9d97d7bcf5428d0ece46740f62fcc9ad7d0628037069ba9ba3918ab0d839df`。两包 v2/v3 均通过，证书 SHA-256 仍为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- 模拟器：仅 `emulator-5554` 同签名 Debug `install -r`，从 `NanfengAiActivity` 冷启动通过（1.655s），设备回读为 code 27 / `0.3.0-p4h`。真实可见闭环已完成：关闭 Capture 提示后，在系统 DocumentsUI 的 Downloads 中选择 `nanfeng-p4h-visible-chain.md`；返回任务详情，第一项 Alpha 未编辑直接“确认写入”后显示“已写入”，第二项 Beta 显式“跳过”后任务显示“完成”；强制停止/冷启动后任务列表与详情仍回读“完成 / Alpha 已写入 / Beta 已跳过”。随后在 Markdown 导出范围 UI 只勾选 Alpha，界面显示“已原子写入、回读并校验 SHA-256：knowledge-20260812T210849329575Z.md · ae2b189fc9744823979bb6c90b6d12771d5fd2f0b34b454b388e25ecd1f496b1”。未操作 OPPO、未读取 Key、未发 HTTP/Prompt/RunSpec，也未实现其他 Adapter。

## P4-I 当前验证等级

- 定向：`P4IOfflineEvalContractsTest` 与 `P4IOfflineEvalRoomContractsTest` 通过，覆盖 baseline 版本/红队/NO_EGRESS、自动断言非质量语义、未评分人工分、同 dataset 比较、append-only Room 重建、13→14 旧 Markdown 表保留、报告 JSON/Manifest 回读 SHA-256 与篡改拒绝。
- 全量自动测试：Android Studio JBR 下 `:app:testDebugUnitTest --rerun-tasks` 通过，143 tests、0 failures / 0 errors；P1–P4-H 回归继续通过。
- Lint：`:app:lintDebug --rerun-tasks` 为 0 errors / 11 warnings（既有 SDK、Gradle/依赖和图标建议；图标视觉继续暂停）。
- 正式签名构建：`0.3.0-p4i`（code 28）Debug/Release 均通过，v2/v3 均为 true；两包证书 SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。Debug SHA-256 `f8ddefcf0742f038b4d248904511afbb05e32af0c6f53aab854f4db3aad174a3`；Release SHA-256 `6239745b7def1fbe70d17f4659eb5a93a425fdb68330e2fb2bc117cebf97537f`。
- 模拟器：仅 API 35 `emulator-5554`。同签名 Debug `install -r` 后以 `NanfengAiActivity` 冷启动成功（1.637s），设备回读 code 28 / `0.3.0-p4i`。可见链已确认“离线 Eval 基线”卡、白色 Dialog、`OFFLINE_LOCAL` 边界、开始本地回归、两次 `p4i-baseline-1 · 6/6 通过`、current-branch / markdown-render / red-team-untrusted 自动事实、未评分人工评分、同 dataset “无确定性差异”比较及 app-private JSON/Manifest 文件创建。最终签名重装后的 force-stop/冷启动再开 Eval，已回读既有运行；未操作 OPPO、未读取 Key、未发 HTTP/Prompt/RunSpec。

## P4-J 已实现事实

- `LocalContextCompressionDomain → ReadLocalContextCompressionUseCase` 是 P4-J 压缩策略、确定性排序、Unicode code point 截断、前后原文抽取、截断计数、来源/压缩 hash 与结构化拒绝的唯一链。它只委托 `ReadExplicitContextBodyUseCase`，没有直接读取 Conversation、Project、Memory、Knowledge Repository 或 `memorySources` 的路径。
- P4-J 只支持版本化 `p4j-extractive-v1` 的本地抽取式压缩；不是语义理解、模型摘要、Prompt 生成或模型质量证据。未截断正文逐 code point 原样保留；截断只保留前后片段，并以 `original/retained/omittedCodePointCount` 明示损失，绝不伪装为完整原文。
- P4-D 的所有控制继续默认关闭。空 `ExplicitContextBodyRequest` 产生零压缩条目；草稿、兄弟分支、附件、Tool、URI、路径、网页、运行事件、缓存前缀、自动 Knowledge/Memory 与 `memorySources` 均没有 P4-J 读取入口。
- P4-D 的缺失会话、Project 不一致、过期、越权 Memory/Knowledge 与高敏拒绝映射为 P4-J 整体结构化拒绝；P4-J 还在压缩前复核单一高敏 detector。它不增加 Room 表、Schema/Migration、持久化、缓存、Export、Invocation、Prompt、RunSpec、Key、HTTP 或 egress，Schema 保持 14，`OpenRouterEgressPolicy.Disabled` 保持。
- 本增量未接入 AppContainer/UI；因此没有声称 Context 控制面存在“压缩”按钮、没有模拟器可见交互验收，也没有把冷启动冒充为该交互的验收。版本 `0.3.0-p4j`/code 29 仅交付已验证的领域链与既有 App 冷启动。

## P4-J 当前验证等级

- 定向合同：`P4JLocalContextCompressionContractsTest` 通过，覆盖默认零来源/legacy `memorySources` 隔离、仅显式当前路径、来源/压缩 hash、Unicode 代理对不拆分的前后抽取、稳定排序、高敏整体拒绝与 P4-D 缺失会话拒绝映射。
- 全量自动测试：Android Studio JBR 下 `:app:testDebugUnitTest --rerun-tasks` 通过，148 tests、0 failures / 0 errors；P1–P4-I 回归继续通过。
- Lint：`:app:lintDebug --rerun-tasks` 为 0 errors / 11 warnings（既有 SDK、Gradle/依赖、图标和 KTX 建议；图标视觉继续暂停）。
- 正式签名构建：`0.3.0-p4j`（code 29）的 Debug/Release 均通过；两包 v2/v3 均为 true，同为现有正式证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。Debug SHA-256 `022693f68fa5b7450debef00681bd5054380df0723ca0515988dc6acf05dc64e`；Release SHA-256 `fdd60c621b06d94cdfc3277f9c65a36accecf4f6e469616cbc7bf20e6b69d4bf`。
- 模拟器：仅 API 35 `emulator-5554`。同签名 Debug `install -r` 后以 `NanfengAiActivity` 冷启动成功（1.670s），设备回读 code 29 / `0.3.0-p4j`；未操作 OPPO、未读取 Key、未发 HTTP/Prompt/RunSpec。P4-J UI 本机预览仍未实现，故无可见交互链证据。

## P4-K 已实现事实

- `ReadLocalContextPreviewUseCase → ReadLocalContextCompressionUseCase → StableContextPrefixMetadataDomain` 是 P4-K 的唯一组合链。P4-J 继续独占正文压缩，P4-K 只消费其安全结果；AppContainer 只公开本机读取入口，未增加 Room 表、Migration、缓存、Export、Invocation、`memorySources`、Eval 写入、日志、诊断、Prompt、RunSpec、Key 或 HTTP 路径，Schema 保持 14，`OpenRouterEgressPolicy.Disabled` 保持。
- Context 控制面仍然默认所有 P4-D 来源关闭；只有用户明确勾选后点击“本机抽取预览”才执行。`COMPACT/BALANCED/EXPANDED` 预算档位只映射至 P4-J Domain 的有界 policy；UI 不含 `take()`、重排或截断算法。关闭、会话切换和进程重建均清空选择、预览与先前 prefix metadata。
- 预览固定标 `EXTRACTIVE_LOCAL · p4j-extractive-v1` 并明确不是语义摘要/Prompt/Provider 请求。逐条只显示 layer/kind、source ID 的 SHA-256 安全摘要、原始/保留/省略 code point 数、source/compressed SHA-256 短摘要和截断状态；不显示标题、正文、压缩片段、URI、路径或附件字节。P4-D 的越权、过期、高敏、缺失会话或 Project 竞态仍整体拒绝且无部分结果。
- `StableContextPrefixMetadataDomain` 只对本次显式选择的 L0/L1 GLOBAL/PROJECT Memory、Knowledge 和 Project 指令元数据稳定排序，生成 `p4k-stable-prefix-metadata-v1` fingerprint；它不含正文，也不伪造尚未物化为可选正文的 SYSTEM/SAFETY 输入。来源集合、revision/source hash、format 变化只形成未来可用的失效理由；本轮没有 Provider cache、命中率、Token、成本、延迟或收益结论。
- P4-I dataset 升级为 `p4i-baseline-2`：新增默认关闭、显式来源、Unicode 截断、hash/顺序、原子拒绝、预览不持久化和 stable-prefix fingerprint/失效 metadata 的 `NO_EGRESS` 本地事实；没有摘要质量、缓存收益、真实用户价值或模型质量断言。完整合同为 `P4K_LOCAL_CONTEXT_PREVIEW_STABLE_PREFIX_CONTRACT.md`。

## P4-K 当前验证等级

- 定向合同：`P4JLocalContextCompressionContractsTest`、`P4KLocalContextPreviewContractsTest`、`P4IOfflineEvalContractsTest` 通过，覆盖默认零来源/legacy `memorySources` 隔离、Unicode code point 前后抽取、稳定排序/hash、高敏原子拒绝、P4-K prefix 指纹/失效与有界预算解释；没有正文或 cache/egress 路径。
- 全量自动测试：Android Studio JBR 下 `:app:testDebugUnitTest --rerun-tasks` 通过，151 tests、0 failures / 0 errors；P1–P4-J 回归继续通过。
- Lint：`:app:lintDebug --rerun-tasks` 为 0 errors / 11 warnings（既有 SDK/Gradle/依赖、图标 monochrome 与 KTX 建议；图标视觉继续暂停）。
- 正式签名构建：`0.3.0-p4k`（code 30）Debug/Release 通过。Debug SHA-256 `4cb51313f870423f455c39e70597a4c8c6faeaf9d4e5e5fc13ada293f141f0a1`；Release SHA-256 `28efc25ecdf3c0b233b4a64180a9695dbbcb06894602abe69aafdb0693cefb45`。两包 v2/v3 均为 true、同为现有正式证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- 模拟器：仅 API 35 `emulator-5554`。同签名 Debug `install -r` 后完成默认关闭→勾选 L2 当前路径→“本机抽取预览”→关闭→force-stop/冷启动的可见链；预览显示 `EXTRACTIVE_LOCAL · p4j-extractive-v1 · 2 条`、安全来源摘要/code point/hash 和 `p4k-stable-prefix-metadata-v1`，无正文。冷启动 1.613s，设备回读 code 30 / `0.3.0-p4k`，回拉 base.apk SHA-256 与 Debug 包一致。未操作 OPPO、未读取 Key、未发 HTTP/Prompt/RunSpec。

## 已验证等级

- 定向合同：`P4DExplicitContextBodyContractsTest`（5 tests）与 P4-B/P4-C 回归已通过，覆盖默认零正文/legacy `memorySources` 无效、L0/L1/L2 显式 scope、L3 正文排除、路径 Text-only、草稿/兄弟/附件/Tool 排除、暂停/越权/高敏拒绝及缺失会话/Project；P4-A–P4-C 继续回归。
- Room/迁移：P4-C 已将 Schema 提升为 10，并只注册显式 `MIGRATION_9_10`；P1–P4-B 事实仍以现有迁移/回归保护。
- 全量自动测试：Android Studio JBR 下 `:app:testDebugUnitTest --rerun-tasks` 通过，118 tests、0 failures / 0 errors；P1–P4-C 回归继续通过。
- Lint：`:app:lintDebug --rerun-tasks` 报告为 0 errors / 11 warnings（SDK/Gradle/依赖更新、图标 monochrome 与 KTX 建议；图标视觉继续暂停）。
- 正式签名构建：`0.3.0-p4d`（code 23）的 Debug 与 Release（Android Studio JBR、Release KSP）通过。Debug SHA-256 `22aee8e600da92f2d401a73f474883966499c9a7fb70f9c22acdf6d7984df84a`；Release SHA-256 `f02332fb191838ff483cd21e7ff20457f527f2aaa008849a857ec7302ada07b9`。两包均由同一正式证书（SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`）签名，v2/v3 均通过。
- API 35 `emulator-5554`：同签名 Debug `install -r` 覆盖到 code 23 后冷启动成功。该历史 P4-D UI 验证当时 L3 正文未提供，用户仅勾选 L2 后预览当前路径 2 条 Text 并显示“不构造 Prompt 或发送内容”；关闭后强制停止/冷启动再打开，checkbox 仍为未勾选。P4-M L3 元数据需以本轮 code 32 重新验收。未操作 OPPO、未读取 Key、未发 HTTP/RunSpec，也未为验收加入开发后门。

## 仍然独立的验收债务

- 真实 Provider/Key、真实文本与图片外发、真实流停止/失败、真实 Token/费用、真实长会话性能、目标真机/OPPO、图标视觉和发布均未通过。
- P4 后续还包括语义模型摘要、真实缓存、JSON/附件/网页等各自独立 Adapter、完整 Markdown 导出范围 UI、真实 Provider 成本；P4-K/P4-M 均仍未构造 Prompt/RunSpec 或真实调用，Knowledge/Memory/关系/L3 元数据均没有自动进入 Context、导出或同步。不可将任一局部增量当成 P4 或总方案终点。

## P4-L 最终实现与验证（完成）

- `JsonKnowledgeAdapter → json-knowledge-import-assets/v1 → json_knowledge_import_tasks/items → KnowledgeDomain → ManageKnowledgeUseCase → Room Knowledge` 是唯一 JSON 读取/私有复制/逐项确认链。Schema 14→15 仅追加 JSON 专属任务表，不复用 Markdown 队列；严格 UTF-8、大小、深度、字符串、重复 key、字段、版本、类型、高敏与 URI/path 均拒绝，JSON 文本惰性且不执行。
- 导入项始终重映射为新 Knowledge ID；没有用户明确 Project 映射的 PROJECT 请求在确认页明示并确定性降级 GLOBAL。正式确认只经 `CREATE_JSON_IMPORT` 的 `KnowledgeDomain → ManageKnowledgeUseCase`，P4-F/G 不被调用；JSON 导出只接受用户勾选的 ACTIVE 正式 Knowledge，并以原子写、同文件回读和 SHA-256 Manifest 校验为成功条件。
- 自动：Android Studio JBR 下 `:app:testDebugUnitTest --rerun-tasks` 通过，155 tests、0 failures、0 errors；`:app:lintDebug --rerun-tasks` 通过，0 errors / 11 个既有 warnings（SDK/Gradle/依赖、图标和 KTX 建议；图标验收继续暂停）。Debug/Release 均重新组装成功。
- 签名：`0.3.0-p4l` / code 31 Debug SHA-256 `a95865d62255af756c273d1e3c0547c69b971dd107815d6f8a9859327ac9bb14`，Release SHA-256 `d8588cfc1429277a19683f860fdbb4e659b200460ae631ce185aebfcc6ce61d4`；两包 APK Signature Scheme v2/v3 均为 true，证书 SHA-256 均为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。正式 Debug 已同签名 `install -r` 覆盖 `emulator-5554`，设备回读 code 31 / `0.3.0-p4l`，回拉 `base.apk` SHA-256 与 Debug 一致；最终冷启动 1.741s。
- 模拟器真实可见链：仅 API 35 `emulator-5554`。DocumentsUI Downloads 选择新的严格两项 fixture `p4l-final-visible-chain.json` 后，私有任务显示 `AWAITING_CONFIRMATION`；在详情明确确认 `P4L Final Alpha`、明确跳过 `P4L Final Beta`，界面显示 `COMPLETED / CONFIRMED / SKIPPED`。force-stop/冷启动后任务列表与详情回读完全相同。JSON 导出面只勾选刚确认且 ACTIVE 的 Alpha，其余项均未勾选；界面显示“已原子写入、回读并校验 SHA-256：`knowledge-20260812T222057795391Z.json` · `c7e1057b7025c4a9744315fbeeb7dd0048c3f32132c7a7dda0e39d4a3e030b9b`”。同一导出精确副本在模拟器 Downloads 中再次由 DocumentsUI 选择后，同 Adapter 创建 `AWAITING_CONFIRMATION` 且仅投影 Alpha 为 `PENDING_CONFIRMATION`；该前检任务显式取消，未产生重复 Knowledge。未用数据库注入、开发后门或旧 APK；未操作 OPPO、未读取/写入 Key、未构造 Prompt/RunSpec、未发 Provider HTTP、未产生费用或外发文本/图片，`OpenRouterEgressPolicy.Disabled` 保持。

## P4-M 本地实现与验证（完成）

- `LocalActionTraceDomain → ReadExplicitLocalActionTraceUseCase` 是唯一入口：先后两次重读 P4-B metadata 与 P3-C lineage；Conversation/Project/branch 或 lineage 改变整体拒绝。只纳入 `CONTINUE`/`RETRY`/`CHANGE_MODEL` 的当前会话、同会话 assistant、Invocation 精确匹配、`COMPLETE`/`FAILED`/`CANCELLED`、已验证 local fixture 谱系；编辑与切分支无 append-only 动作事实，明确排除。固定 `createdAt DESC`、安全 selector 升序、最多 12 条。
- Context 白色控制面增加默认关闭的 `LOCAL_L3_METADATA`：用户启用、逐项选择后才显示格式、动作种类、安全 ID 摘要、终态、时间、来源版本和排除说明。选择/预览只在 ViewModel 内存；关闭、切换会话、force-stop/重建丢弃。L3 绝不进入 P4-D 正文、P4-J/K、Prompt、RunSpec、Harness、Invocation、Export 或生产 Eval 结论。
- Schema 仍为 15，无 Room 表/迁移、Intent/revision/log/导出/同步写入；`OpenRouterEgressPolicy.Disabled` 保持。定向 P4-M、P4-D/I/K 与全量 `:app:testDebugUnitTest --rerun-tasks` 通过（158 tests、0 failures / 0 errors）；`:app:lintDebug --rerun-tasks` 0 errors / 11 既有 warnings。`0.3.0-p4m`/code 32 正式 Debug SHA-256 `3a30a1777a5d9b733ce0bbe560e20c2d2873634f769f566394f8523f1c01b5a6`，Release `aefbb38702cc3068c96fac1c001d76254d59003d0221087357a7dc145e5b0f95`；v2/v3 为 true，同一证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。仅 `emulator-5554` 已完成默认关闭→启用→逐项选择 2 条→预览→关闭/force-stop/冷启动丢弃；另一无谱系会话启用 L3 显示零候选，未串数据；设备 code 32/base.apk 回读与 Debug hash 一致。未操作 OPPO、未读写 Key、未构造 Prompt/RunSpec、未发 HTTP/文本/图片。P4-M 不是 P4 或项目终点。

## P4-N 本地实现与验证（完成）

- 新增独立 `PdfTextKnowledgeAdapter` 与 PDFBox-Android 固定依赖：仅 `application/pdf`、`%PDF-` 头、5 MiB、5,000 对象标记、500 stream/filter 标记、80 页、每页 12,000/合计 120,000 Unicode code points 和 2 MiB mixed memory；加密、提取禁止、畸形、无文本层、超限与高敏都安全失败，不写正式 Knowledge。
- `pdf-text-import-assets/v1`、`pdf_text_import_tasks`、`pdf_text_import_pages`、`pdf_text_import_items` 与 Markdown/JSON 完全独立。Schema 15→16 仅追加三张 PDF 专属表和索引；私有原件/逐页提取/候选/正式 Knowledge 分层记录 hash、版本与状态。确认唯一经 `CREATE_PDF_TEXT_IMPORT → KnowledgeDomain → ManageKnowledgeUseCase`；没有自动 Context、Memory、关系、去重合并、Prompt、RunSpec、Invocation 或 egress。
- 最小 UI 已接入 PDF 卡、系统 OpenDocument `application/pdf`、纯白任务 Dialog、实际阶段/页计数、安全失败、取消/重试、逐页预览/编辑/确认/跳过；外部 URI、路径和 token 不显示也不落库。PDFBox 初始化固定在 AppContainer，避免运行时隐式资源前置。
- 已通过：PDF 边界合同 2 tests、Schema 15→16 Room 迁移回归 1 test，和全量 `:app:testDebugUnitTest --rerun-tasks`（161 tests、0 failures / 0 errors）；`:app:lintDebug` 为 0 errors / 14 warnings，Debug/Release 均构建成功。`0.3.0-p4n`/code 33 正式 Debug SHA-256 `ffca9198cc8b7cfa8247b0d5e4916fbc29e9913ea99324a3f53c101f0bc90939`，Release `41c05a2c166b8df7f355bab600ddea836004d50f627aaf4e296251cb8fe7d32c`；v2/v3 为 true，证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。仅 `emulator-5554` 完成真实 DocumentsUI 选择两页文本 PDF→2/2 页预览→确认第 1 页、跳过第 2 页→完成态；force-stop 冷启动后重建为已保存内容，安装设备 base.apk 回读与 Debug hash 一致。未操作 OPPO、未读写 Key、未构造 Prompt/RunSpec、未发 HTTP/文本/图片。P4-N 不是 P4 或项目终点。

- `context_gate.py` 于 2026-08-13 最终返回 `HANDOFF`（context 94.3%，有效 Token 552,494），已创建本轮交接卡：`/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-13T06-55-55-019ff830-7d27-7391-93e6-ce744262f485.md`。下一阶段不得把 L3 接入 P4-D/P4-J/K 或发送链；语义摘要、真实缓存、Provider/费用、网页/其他 Adapter、同步/账号、Tool/Agent 仍是各自新合同且当前用户禁止提前实现。

## P4-O HTTPS 网页文本快照（完成）

- 新增唯一 `WebTextSnapshotAdapter` 链：用户在纯白控制面手工输入并勾选确认后，才由 `AndroidPublicWebFetcher` 读取一个公共 HTTPS 主 HTML。拒绝 `http/file/content/data/javascript`、userinfo、IP literal、localhost、fragment、私网与高敏 query；每次 redirect 都重验 URL/DNS 公网性，最多 3 次。无 Cookie、Authorization、Referer、浏览器会话、JS、资源、表单或网页内链接跟随。
- Schema 16→17 仅追加 `web_text_snapshot_tasks/items`。任务仅保存无 query 的安全 URL 摘要、host、私有 storage key、版本/hash/status/失败码；没有 Cookie、凭据、DNS/IP、网络 body 日志或完整 URL query/fragment。HTML 原件放入 `web-text-snapshots/v1`，确定性剥离 script/style/form/隐藏/动作内容后只生成惰性可见文本候选；网页文本不进入 Context、Memory、关系、Prompt、RunSpec、Provider、导出或同步。
- 状态机为 `QUEUED/FETCHING/EXTRACTING/AWAITING_CONFIRMATION/COMPLETED/FAILED/CANCELLED`。中断任务在 UI 的 IO 恢复路径明确标为 `FAILED(INTERRUPTED)`，不会在 Activity 主线程扫描 Room 或伪装完成；同 URL 仍是独立显式任务，不自动合并。唯一正式写入为 `CREATE_WEB_TEXT_SNAPSHOT → KnowledgeDomain → ManageKnowledgeUseCase`。
- 自动验证：新增 P4-O URL/高敏/惰性 HTML/同 URL显式语义合同与 16→17 迁移保留回归；全量 `:app:testDebugUnitTest --rerun-tasks` 165 tests、0 failures / 0 errors。`:app:lintDebug --rerun-tasks` 通过，0 errors / 14 既有 warnings。最初模拟器冷启动暴露同步恢复导致 Room 主线程保护崩溃，已改为 ViewModel IO 恢复并用全量门重验。
- 正式签名：`0.3.0-p4o` / code 34。Debug SHA-256 `ecfd5fde18522b79917b4bccdcc3fa6d5f2884d45cbcf90f33a859436fad9ab1`，Release `aeadded5758621ca1519056f5ccc3ffcc8b194c2f68497f442bf2be775058609`；两包 v2/v3 为 true，证书 SHA-256 均为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- 仅 API 35 `emulator-5554`：早期未包含保留地址段拒绝时，`https://example.com` 可见链曾到达 `AWAITING_CONFIRMATION → Example Domain → CONFIRMED → COMPLETED`，且 force-stop/冷启动回读完成态；该历史现象不再作为当前严格网络成功证据。最终签名 Debug 在新增完整保留地址拒绝后，手工输入 `https://example.com?x=2`、勾选并确认抓取，真实显示 `FAILED / DNS_NOT_PUBLIC`；模拟器 DNS 实测把 example.com 映射为保留 `198.18.0.147`，因此安全失败正确。最终包已 force-stop/冷启动；当前严格公网成功是独立设备网络债务，不能以旧成功或后门替代。回拉 `base.apk` SHA-256 与 Debug 相同。未操作 OPPO、未读写 Key、未构造 Prompt/RunSpec、未发 Provider HTTP、未产生模型费用。

## 下一安全阶段

- P4 后续必须另立单一合同；不得把 P4-O 扩展为 OCR/图片、网页批量爬取、系统分享/拍照、语音/视频、Context/Memory、Provider 或 egress。

## 下一条安全命令

```bash
sed -n '1,180p' /Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-13T06-30-19-019ff819-0cd9-7b23-8f46-68eee3db8359.md
```

## 2026-08-30 Qwen3.8-Max 联网工具轨迹与无限生成修复

- **失败样本证据：** 用户导出的 `/Users/nanzhufeng/Downloads/新对话-02.md` 为 1,790 行、257,100 bytes，包含 1,193 个完整 `<tool_use>` / `<tool_result>` 开闭标签；正文从第一行即进入 `web_search` 工具协议，文件末尾仍停在检索循环，没有最终答复。截图同时证明该请求运行 17 分 15 秒仍处于生成态。
- **根因：** 普通文本聊天原先把 Qwen 实时问题路由到流式 Chat Completions `enable_search`，系统提示又要求模型“先使用网页检索”；Qwen3.8-Max 因而把内部工具协议生成为普通 `content`。应用的 SSE owner 未区分这类 XML 与用户正文，逐块持久化；`HttpsURLConnection.readTimeout` 只是单次静默读期限，持续工具输出会无限刷新它。
- **主修复：** 无附件的 Qwen 实时请求改走官方 `/responses` 与内建 `web_search`，由 Provider 完成工具闭环并只从 `message/output_text` 投影最终答复。系统事实改为“服务端已启用检索，直接形成最终答复”，明确禁止输出、模拟或重复工具协议。带附件的 Chat Completions 兼容路径保留，但整段缓冲后原子剥离精确 `<tool_use>` / `<tool_result>` 块；只有工具轨迹而无轨迹后最终正文时返回 `RESPONSE_FORMAT`，不会写成成功消息。Responses 的 `output_text` 也应用同一终态过滤。
- **终止边界：** 通用 SSE 新增总流时限与解码文本字节上限，不能再靠持续垃圾输出绕过静默超时。Qwen Chat Completions 联网兼容路径为 180 秒总时限；PDF 因官方首次理解窗口保留 420 秒总时限、300 秒单次读期限。
- **验证：** `AutomaticWebSearchPolicyTest` 6 项、`ProviderSseDecoderContractsTest` 4 项、`ProviderAdapterContractsTest` 30 项，共 40 项定向 JVM 测试 0 失败；覆盖文本走 Responses、附件回退、跨 SSE delta 的 XML 隔离、工具轨迹后最终正文、工具轨迹-only 拒绝和活跃输出总时限。`:app:lintDebug` 通过。全量 JVM 共 946 项，4 项失败、3 项跳过；失败分别位于 PDF renderer 复用、答案上下文脚注、居中 Dialog scrim 与对话行无障碍合同，均不在本次 Provider/路由/传输修改范围，不能写作全量通过。
- **Release 产物：** `0.3.0-p10j` / code 66，`app/build/outputs/apk/release/南枫AI.apk`，27,869,948 bytes，SHA-256 `7f287f4455ce3d4464310bdac6e494702be8ff64f8bf6e97b4113a8883f0701c`；非 debuggable，APK Signature Scheme v2/v3 验证通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **未越界：** 未执行真实 Qwen/Provider 请求，未使用用户 Key，未产生费用；未运行任何 `connected*AndroidTest`，未操作或覆盖安装 OPPO。真实服务闭环与目标设备实看仍需用户明确授权／覆盖安装后单独验收。
## 2026-08-23 普通聊天联网修复：D1/D3 与持久助手占位进行中，尚未达到真实 Provider 闭环

- **已写入且已定向 JVM 编译：** 新增独立 Room `debug_call_log`（schema 39→40）；只保存 Provider、host、实际模型 ID、HTTP 状态、错误分类、脱敏错误体、字段形状与延时，不保存 Key、提示词、回复、附件或原始 payload；7 天/500 条上限。模型设置新增最近三条本机诊断和“测试连接（仅发送固定 hi）”，后者只能由用户主动点击，固定为 1-token 探针，不读取对话/附件/上下文。Android/ Desktop 功能审阅已登记 Android 入口与 Desktop 未具备对应 owner 的不展示边界。
- **发送主链进行中：** 普通单模型聊天现在请求 SSE、90 秒无 chunk 才超时，SSE 文本块接入既有 `ConversationRuntime` 的 `PARTIAL` assistant 节点并逐块持久化/刷新；失败节点原位保留。当前 `SubmitConversationDraftUseCase` 与 Provider runtime start 仍是相邻事务而非同一原子事务；Compare 双路持久 attempt、停止取消、进程中断 `UNKNOWN_OUTCOME`、目录五级解析/Qwen 区域配置、原生 PDF/多帧视频、前台服务、预算/FTS 与真实服务/设备验收均尚未完成，不能写作整个方案交付。
- **当前验证：** Android Studio JBR、`--offline --no-daemon` 下 `ProviderDiagnosticsContractsTest`、`ProviderSseDecoderContractsTest`、`P3BConversationRuntimeContractsTest`、`P3JNormalChatExplicitEgressContractsTest` 与 `P6GModelRouterContractsTest` 通过；未运行 `connected*AndroidTest`，未读取 Key、未发 HTTP、未操作 emulator/OPPO。`git diff --check` 仅报告本轮前已存在的 `docs/README.md:4` trailing whitespace，未修改该文件。

## 2026-08-23 会话尾部对齐与生成中反馈（待视觉验收）

- `ConversationWorkspace` 的助手操作行（复制、分享、分支和时间）现作为一个整体贴右侧排列，避免右侧附件/对话与左侧尾部信息割裂；未改动用户长按操作边界，也未新增常驻按键。
- 持久 `PARTIAL` 助手消息现在显式显示“南枫 AI 正在生成…”与轻量呼吸进度环；出现首段流式文本后保持“正在继续生成…”。只有仍在进行中的本地消息会显示此状态，完成、失败或取消的历史消息不会被伪装成生成中。
- 已通过 Android Studio JBR、`--offline --no-daemon` 下 `P6DConversationRowAccessibilityContractsTest` 和 `P6GUnifiedChatFirstUiContractsTest`。未运行 `connected*AndroidTest`，未构建/安装新 APK、未操作 emulator/OPPO；仍需以正式包在目标设备做视觉验收。

## 2026-08-23 对话左侧位置横线移除（待视觉验收）

- 已删除仅在宽屏显示的 `TranscriptPositionRail` 多段横线导航。它会压在消息区左侧，且与普通对话阅读无关；现在不再创建、渲染或响应这组横线。
- 右边缘细滚动位置提示保持不变，仍使用同一 `LazyListState`，不占用消息正文或附件的左侧空间。
- Android Studio JBR、`--offline --no-daemon` 下 `P6DConversationRowAccessibilityContractsTest` 与 `FoldableContainerSizingContractsTest` 通过。未运行 `connected*AndroidTest`，未构建/安装新 APK、未操作 emulator/OPPO。

## 2026-08-30 南枫转写统一附件体系与真实空间统计

- **真实崩溃根因：** OPPO `ApplicationExitInfo`/`AndroidRuntime` 记录证明，详情页点击原始 PDF 时由 Compose Main 直接执行 `GlmOcrTaskOwner.sourceReference → RoomGlmOcrTaskRepository.find`，触发 Room `Cannot access database on the main thread`。现将任务/附件解析放入 `Dispatchers.IO`，并以 generation + selected task 双重校验丢弃过期结果；图片与 PDF 都继续使用应用内共享查看器，没有 `ACTION_VIEW` 或第三方跳转。
- **结果文件统一：** 详情页不再读取并裸排完整 Markdown；只展示标准 Markdown 文件入口。点击后进入共享安全文本查看器，复用复制全文、下载、分享与系统栏/返回层级；“加入新对话”保留为南枫转写唯一附加能力。原始图片/PDF 同样启用共享附件下载/分享 owner。
- **搜索统一：** 普通点击南枫转写来源/Markdown 直接走 `openSearchAttachment`，不再默认跳转定位；长按才显示定位与删除。删除先移除 OCR task lineage，再按标准引用计数分别清理来源与结果，其他位置仍引用时保留共享字节。显式时间/大小排序改为普通会话附件 + OCR 文件的同一全局序列，文件类型筛选、预览、返回锚点和传输引用也包含 OCR 文件。
- **本机数据事实源：** 附件分类不再 `SUM(private_attachment_assets.byteCount)`。现在逐行校验当前受管文件是否真实存在并使用 `File.length()`；缺失文件不计数。ZIP-backed 条目保持可检索计数，但其逻辑解压大小不再逐项累加，实际 ZIP/导入目录只在“其他导入资料”按磁盘文件计一次。新增南枫转写任务/文件汇总，OCR 来源与 Markdown 仍通过同一附件目录计数，不重复加入总额。
- **验证：** 定向 32 项 JVM 回归通过（OCR domain/UI、统一搜索排序与锚点、真实存储统计、ZIP 清理）。全量 `:app:testDebugUnitTest` 执行 988 项，981 通过、4 失败、3 跳过；4 项仍为既有 PDF renderer 静态合同、全局 Dialog import/scrim、全局 Dialog swipe 与设置 canvas 静态合同，本次定向链全部通过，不能写作全量通过。`:app:assembleRelease` 成功；`0.3.0-p10j` / code 66 APK `app/build/outputs/apk/release/南枫AI.apk`，27,968,235 bytes，SHA-256 `f2e75b8accbae964a6b8914b34d7185e2bf6b35591007b0f94aa6a823c5cc905`，非 Debug 正式签名 v2/v3 通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **边界：** 未运行任何 `connected*AndroidTest`，未安装 APK，未改动 OPPO 数据，未请求 Provider/Key。真机点击、排序视觉与本机数据数值刷新仍需用户明确要求覆盖安装后验收。
# 2026-08-31 Kimi K3 完整接入与 Session Sticky

- OpenRouter 新增 `Kimi K3`（`moonshotai/kimi-k3`），设置说明为“复杂分析 · Agent · 长上下文”；深度模式以 K3 替换 Qwen3.8-Max，Qwen3.8-Max 本身仍保留在千问设置和历史兼容中。
- K3 为显式 opt-in，不进入 Auto。既有会话首次切入 K3 时只从当前用户轮建立 Provider 上下文；已有 K3 回答后只续接 K3 阶段，避免继承其他模型的压缩／标准化历史。
- OpenRouter K3 Adapter 将 `reasoning_content` 与结构化 `tool_calls` 解析为隐藏消息协议块并在后续 K3 请求中回放；这些字段不进入搜索、标题、知识提取或通用交换，Provider raw envelope 仍不落库。保存 tool call 不代表工具已执行。
- 目录、1M context profile、OpenRouter registry 精确映射、冷启动精确 ID、费用估算和模型健康归因均已接入；官方公开价格采用本次核验的 USD 2.55/M input、12.75/M output、0.256/M cache read，Provider 回传 `usage.cost` 仍优先。
