# 南枫 AI 当前交接

## 2026-09-10：本机数据保存路径

- 本机数据页显示运行中的数据目录，并提供更改路径。独立 storage-location.json 配置由 app_config_dir 保存，普通启动解析选定目录；显式隔离验收入口继续使用隔离根。
- 下次启动在旧目录独占锁下执行空目录复制，SQLite quick_check 后发布目录；已有有效数据目录直接读取。原目录保留，拒绝相互包含、非本软件数据或失效路径，不静默回退空库。密钥仍由系统钥匙串管理。
- Rust 路径 owner 3 项测试通过；本机数据 Node 合同 4 项通过；macOS bundle 构建验签通过。未迁移用户真实数据，尚未完成原生选目录、重启及重新安装验收。

## 2026-09-10：设置保存并发与重开保留

- 通用设置写入串行排队，每次读取当前数据库 revision，只合并本次修改的字段；外观只写对应字段，开关只写 patch，个性化只写风格／昵称／职业／自定义指令，避免旧草稿覆盖其他开关。全屏编辑保存失败不再关闭。
- 新增连续保存行为回归和磁盘 SQLite 关闭重开逐字段测试。Node 246/246；设置 Rust 6/6；完整 Rust 205 通过、1 个 OAuth localhost callback 读取失败，单独复跑通过。macOS 开发 bundle 构建及签名验证通过。
- 普通开发／测试 bundle 使用固定应用 ID 的 app_data_dir/p6b-workspace，只有显式隔离验收模式走临时根。模型设置已有重开回读回归；没有迁移或清理用户设置与密钥。尚未逐页完成原生 UI 保存／重启与跨安装版本验收，不声称所有独立设置 owner 已完成真实升级验证。

## 2026-09-10：真实 Sonnet 5 短消息验证与运行版本更新

- 用户反馈仍超时后，使用签名后的应用 `--test-live-sonnet-connection`，通过生产凭据 owner 和 `execute_streaming` 发出固定短消息；仅输出计数与耗时，不打开工作区、不发送历史正文或附件、不输出密钥或响应正文。真实结果 `LIVE_PROBE_OK deltas=2 reply_bytes=2 elapsed_ms=2846`，无系统凭据交互。
- 发现用户仍运行 9 月 9 日 23:08 启动的进程，而最新 bundle 为 9 月 10 日 01:08 构建。已通过正常 quit/open 重启至当前 bundle；没有改写历史失败 Attempt，没有清数据。不能从短消息成功推断原附件请求失败原因已确认。
- 原生 UI 自动控制仍超时，未完成输入框点击到回复落库的 UI 验收；附件聊天和长回复尚未真实验证。诊断入口显式执行才外发，不在启动或后台自动运行。

## 2026-09-09：Desktop 账号页继续匹配 Android

- 对照用户手机截图和 `P7DAccountSyncUi.kt`，账号操作改为居中灰底胶囊，48px 高度、8px 间隔；移除操作右箭头、同步行分隔线和常态“已就绪”。头像、姓名、邮箱居中；页面恢复宽屏卡片布局。
- 切换、退出、CloudOff、CloudDone 使用当前 AndroidX Material Rounded 原始路径。未登录仍显示账号管理和同步分组，操作禁用；恢复与安全功能折叠保留，不改变本机账号、恢复码、同步数据或外发行为。
- Desktop Node 244/244、差异空白检查、macOS bundle 构建与签名验证通过；浏览器隔离预览核对账号区域。未验证真实 Google 登录、账号切换、头像网络、云端同步或已安装原生应用，不能称双端真实业务闭环。

> **当前合同读取门（2026-09-03，优先于全文）：** 本文下方的**最新有效交接**与按时间累积的实现、设备与验收记录，只能说明当时事实，不能重新定义当前行为。Android 会话、抽屉、Composer、搜索、文本选择、主题和暗色皮肤只读取 [Android 当前会话界面合同](../ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)；Android 设置首页及二级至四级页面只读取 [Android 当前设置界面合同](../ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)；普通聊天的个性化、Memory、资料库与历史对话上下文只读取 [Android 当前运行时上下文合同](../ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)；南枫转写只读取 [Android 南枫转写当前界面合同](../ANDROID_TRANSCRIPTION_UI_CURRENT_CONTRACT.md)。下方任何历史“当前”措辞与这些合同冲突时一律失效；数据／安全／Provider owner 仍按各自领域合同执行。

## 2026-09-09：Android 记忆摘要全文编辑与替换

- 已接通 `MemorySummaryPage → MemoryViewModel → ManageMemoryUseCase → RoomMemoryRepository`：菜单“编辑摘要”打开预填全文的多行编辑页，支持全选／清空重写／粘贴整篇替换／保存。完整读取不受搜索过滤影响，取消不落库，保存失败保留编辑稿。
- 持久层使用现有 Memory／Revision／Intent 表的同一事务保存新摘要与旧 ACTIVE 全局摘要的软删除，保留历史、暂停项和其他范围；前置修订集合检查拒绝编辑期间的并发新增或修改。没有表结构变更，不需要 schema 迁移。启用开关不变，不调用 Provider。
- 验证：整篇替换的 Room 行为测试先失败后通过；ViewModel 流程覆盖过滤后打开全文、取消、整篇保存、重新编辑及错误保稿；Room 覆盖并发冲突、非法内容、事务回滚与数据库关闭重开。Robolectric SQLite 缺 FTS5，检索层改用同一持久数据库的 JDBC FTS5 回读，以及生产 ContextIndexSchema 的实时触发器用例；显式注册测试驱动消除类加载顺序依赖。
- 最终 `:app:testDebugUnitTest :app:assembleDebug` 通过，XML 汇总 1100 tests、0 failures、0 errors、3 skipped。未运行模拟器视觉／触控、OPPO 真机、正式签名覆盖或真实 Provider；手机实际版本未更新，不能称真机验收。现行入口与行为见 [设置合同](../ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)；检索边界见 [运行时上下文合同](../ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)。

## 2026-09-09：Android 启动误选置顶对话修复

- 根因：Activity 只保存退出时间，短时冷启不进入 fresh 分支；ViewModel 未接收退出 ID，reload 回退到按置顶排序的列表首项。当前修复保存退出 ID／生成连续性元数据，启动按 ID 验证并恢复其 surface；无有效记录时走新对话。现行规则见 [会话合同](../ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md#普通启动与短时恢复)。
- 同时收紧空会话复用，避免已有标题／置顶／未发送草稿被当成新对话；不修改 Room schema 或会话内容。已有 dirty 工作保留。
- 验证：退出 ID 接线检查先失败后通过；新增策略行为测试 7 项、接线检查 1 项通过。`JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug` 通过，XML 汇总 1093 tests、0 failures、0 errors、3 skipped。Debug APK 只用于本地构建证据。
- 边界：未进行模拟器／OPPO 重启验收、正式签名构建或覆盖安装；未调用真实 Provider。本轮不把源码修复写成手机已更新。

## 2026-09-03：C-16 三端 84 项全矩阵最终闭环（当前最高优先级）

- **最终裁决：** [最终独立审计](../ANDROID_DESKTOP_FINAL_INDEPENDENT_AUDIT_20260903.md) 的 C-16 本地缺口已经关闭。系统浅／系统深／显式浅／显式深 × 小／标准／大 × 模型根／日常／深度／加号根／风格／搜索历史／主题选择共 `84` 项，在 Android、Codex In-app Browser 与当前严格签名隔离 Tauri 各逐项实渲染；manifest 强审计为 `84/84 + 84/84 + 84/84 = 252/252`。C-01～C-16 可写成“本地视觉与 owner 已闭环”，不能扩大为 Provider、网页来源、GLM-OCR、账号同步、通知、正式签名或 OPPO 真机闭环。
- **真实修复：** Browser 的 system-light／system-dark 从误读实时 `matchMedia` 改为把合同宿主明暗输入交给生产主题 owner；首次 Browser 取证又真实暴露静态根误指 `desktop/src` 导致应用图标破图，已改为构建后从 `desktop/dist` 提供页面、84 项全量重采，并把全部图片加载成功加入逐槽门禁。Tauri 新增 marker＋唯一 `/tmp` 根＋84 状态＋诊断参数四门同时成立的只读入口，并补独立 ACL permission。首个原生状态真实暴露 ACL 遗漏，修复后原态重试通过；C16 读取位置移到首次 workspace `refresh()` 之后，保住既有启动回读合同。证据审计器现在回读每槽请求、解析态、字号、层、源码指纹、PNG 哈希与 pass，禁止缺槽或代表图顶替。
- **三端证据：** Android 仅用关网 `NanzhufengFindN5Api35` 的 `emulator-5554`、1140×2616／442 dpi 与独立 `com.nanzhufeng.ai.searchattachmentacceptance` 包，84 项各有 PNG＋XML＋JSON；Browser 实际 viewport 1280×720／DPR 2，84 项各有 PNG＋JSON；Tauri Bundle ID `com.nanzhufeng.ai.desktop.c16acceptance.60115.mtle24j8`，只用 `/tmp/nanfeng-ai-desktop-c16-visual-acceptance.ZVKBul`，1440×900 窗口 84 次逐项启动、AX 回读、截图和精确停止。Android 84 张原图哈希均唯一；Browser／Tauri 分别 48／56 个唯一像素哈希，等价解析重复不替代逻辑 ID。
- **持久化与回归：** Android UI 保存深色／大／橙色后 force-stop 冷启仍回读三值，随后仅在 AVD 恢复系统／标准。Tauri UI 保存后隔离 SQLite `dark/large/orange/revision 3`，退出重启同行、revision 与时间戳不变。Desktop Node `236/236`、Rust `199/199`、lint、typecheck、protocol、七主题 computed style、inventory（231 action／126 invoke／断链 0）、build 与 `cargo check` 通过；Android JVM `1085` tests、0 failure／0 error／3 opt-in skipped，`:app:assembleSearchAttachmentAcceptance` 通过。未运行 instrumentation 或任何 `connected*AndroidTest`。
- **产物与边界：** 证据目录 `~/.codex/visualizations/2026/09/03/01a066a9-bc6d-7b31-857d-0c0fa2efd991/nanfeng-ai-c16-final-20260903/`；源码指纹 `1c2172f29971e7d1fcf4a59f23665e8f149d8805a325d1ca6c615deb798f023e`，manifest SHA-256 `b4d3d5a9d678f0cb44f5cf9d9fa12a40587da779a114f1171cfbc303099f0893`（Browser 正式构建重采后连续两次 audit 稳定一致）。当前开发 bundle 主程序 `31,061,408` bytes、SHA-256 `2611b4e4c8196b152f43dce95c94c8b32fc3f7a03cc728a93381a721f855d1a1`，ad-hoc strict codesign 通过，不是 Developer ID／公证包。未操作 OPPO、正式 Desktop 根、Provider、Key、账号、通知或真实服务；既有 dirty／untracked 保留。阶段门为 `HANDOFF / 91.3%`，后续不得把下方旧 C-16“63 Browser＋代表原生”记录当当前事实。

## 2026-09-03：C-07／C-08／C-09／C-10／C-12 最终本地闭环（当前最高优先级）

- **裁决：** [最终独立审计](../ANDROID_DESKTOP_FINAL_INDEPENDENT_AUDIT_20260903.md) 中这五项剩余的本地硬门已经关闭。C-07 实际文件选择器取消／导入／预览／同 bundle 重启，C-08 有效 PNG／PDF／音频／视频／MD／JSON／ZIP／DOCX，C-09 启用／暂停／结果／失败持久态，C-10 处理／失败／完成，以及 C-12 Android 带数据深页与同内容 Tauri 深页均有当前证据。真实 Provider、网页来源、GLM-OCR 结果／账单、系统通知、账号同步仍是明确排除的外部层，不计入本地闭环。
- **最小修复：** Desktop 草稿图片从无动作缩略图改为 `open-image-preview` 语义按钮，复用现有 App 内原图预览 owner，并用 `chat-first-ui.test.mjs` 锁定附件 ID 与可访问名称。Android 离线验收 PNG 替换为 CRC 有效的 32×24 固定字节；C-12 Desktop 脱敏上下文种子的输入 Token 对齐 Android 的 1,680。新增独立 `SearchAttachmentAcceptanceApplication`、Android 合同测试、Desktop C-08 夹具／合同、C-07～C-12 `/tmp` Tauri 种子与专用打包脚本；普通启动不启用这些夹具。
- **Android 实看：** 只使用关网隔离 `emulator-5554` 和独立包 `com.nanzhufeng.ai.searchattachmentacceptance`。C-07 实际 Photo Picker 取消、系统 Documents picker 导入 MD、App 内文本预览、强停冷启恢复和虚拟相机安全退出通过；C-08 八类合成资产均成功预览或显示显式安全边界；C-09 四持久态、C-10 三任务态、C-12 费用／上下文／诊断三深页均逐页实看。没有连接 OPPO，没有 instrumentation 或 `connected*AndroidTest`。
- **Desktop 与 Browser：** Browser `1440×900` 的 C-08／C-09／C-10／C-12 只读夹具逐态实点，console warning／error 为 0。原生副本 Bundle ID `com.nanzhufeng.ai.desktop.c07c12acceptance.46345.mtlbsfv2`，数据根 `/tmp/nanfeng-ai-desktop-c07-c12-acceptance.YJ7tmX`；真实系统选择器取消并导入 `/tmp/nanfeng-valid-fixture.png`，App 内 32×24 原图预览通过，同一 bundle 退出重启后草稿与附件同时恢复。重启后 C-09 四态、C-10 三态和 C-12 的 1,240／680 Token、`$0.01425`、3 项／1,680 Token、网络失败／1.45 秒均可读。ad-hoc strict codesign 通过。
- **自动门与证据：** Desktop Node `235/235`、lint、static build；Android `OfflineVisualAcceptanceContractsTest` 与 `:app:assembleSearchAttachmentAcceptance`；Rust `c07_c12_fixture_persists_reminder_transcription_and_redacted_call_states` 全部通过。两张联系表与关键原尺寸单页位于 `~/.codex/visualizations/2026/09/03/01a06634-d3d4-75f2-8034-8979e48fa351/nanfeng-ai-c07-c12-closure-20260903/`。未读取正式 Desktop 根，未调用 Provider、Key、账号、通知或真实服务；既有 dirty／untracked 全部保留。收口 `context_gate` 为 `HANDOFF / 94.7%`，本轮不再扩张范围。

## 2026-09-03：C-02～C-06 全状态硬门与 C-14 原尺寸证据闭环（当前最高优先级）

- **裁决：** [最终独立审计](../ANDROID_DESKTOP_FINAL_INDEPENDENT_AUDIT_20260903.md) 明确列出的 C-02～C-06 本地三端同状态缺口和 C-14 原尺寸证据保全缺口已关闭。C-02 固定内容失败态、C-03 有会话列表、C-04 模型根层、C-05 日常六候选、C-06 深度六候选，以及 C-14 导入导出／本机数据／三类清理／全部删除禁用门均有当前 Android、Browser、隔离 Tauri 原图。此结论不把 C-07、C-08、C-09、C-10、C-12、C-16 的既有外部或深层边界写成完成。
- **诊断夹具：** 新增 C-02～C-06 原生验收 marker、唯一 `/tmp/nanfeng-ai-desktop-c02-c06-acceptance.*` 根、专属 workspace 与失败对话种子；只在 schema 诊断参数同时存在时启用，并与普通／C-13／C-14／C-15 验收互斥。固定 user message 为 `C02-local-visual-fixture`，assistant 为 `FAILED / PROVIDER_NOT_ENABLED`；同根重开不重置。新增专用 macOS QA 打包脚本，复制并改唯一 Bundle ID 后 ad-hoc 签名，不改变源 bundle 主程序。
- **自动门：** Desktop Node `234/234`、Rust `196/196`、lint、typecheck、protocol、主题 computed style、inventory（action／invoke 断链 0）通过；Android JVM 报告 `1084` tests、0 failure／0 error／3 个真实 ZIP 路径 opt-in skipped，`:app:assembleDebug` 通过。`cargo fmt --check` 仍只报告本轮开始前已存在的 3 处 `lib.rs` 格式漂移，本轮新增片段已按 rustfmt 形状写入，未批量格式化用户大范围 dirty 文件。
- **三端实看：** Android 仅使用关网隔离 `NanzhufengFindN5Api35` 的 `emulator-5554`，原图为 `1140×2616`；Browser 固定 `1440×900`，console warning／error 0、无横向溢出；Tauri 原图为 `1229×768`，C-02 根为 1 workspace／1 conversation，同根重开保持，C-14 SQLite schema 37／84 表／`integrity_check=ok`。两个 QA 进程均无子进程、无网络 socket，验收后已精确关闭；模拟器与本地 HTTP 服务也已关闭。
- **产物与边界：** 原始 PNG、Android XML 与三张联系表位于 `~/.codex/visualizations/2026/09/03/01a06645-e226-7cd0-82c7-07532b18a582/nanfeng-ai-c02-c06-c14-closure-20260903/`。最新开发 bundle 主程序 `31,047,728` bytes、SHA-256 `64ead434df7974acfdcc5d3684a66308afe7a852405dce7c6c9549f2b369acc8`，ad-hoc strict codesign 通过，不是 Developer ID／公证包。未操作 OPPO、正式 Desktop 数据根、账号、Provider、Key、通知或真实服务，未运行 instrumentation 或任何 `connected*AndroidTest`，图形界面没有提交 C-14 删除。

## 2026-09-03：C-01～C-16 最终独立审计初裁（历史记录，已被上方最终闭环裁决取代）

- **历史初裁（当时事实）：** 当时尚不能把 C-01～C-16 整体写成“全部全状态同步完成”。本地业务实现、Node／Rust、Android JVM／build、当时的 exact bundle 和隔离启动门已经通过，也没有发现需要重做的业务 owner；但当时 C-02～C-06 仍缺台账规定的同状态原生硬门，C-07／C-08／C-09／C-10／C-12／C-14／C-16 仍有各自行所述证据缺口或外部边界。此后本地缺口已由上方后续增量关闭；唯一现行总结见 [最终独立审计](../ANDROID_DESKTOP_FINAL_INDEPENDENT_AUDIT_20260903.md)，外部边界仍按该审计保留。
- **独立重跑：** Desktop Node `232/232`、Rust `194/194`、lint、typecheck、protocol、主题七色通过；inventory 为 231 个可见 action、125 个 invoke，断链 0。Android 首轮 `1084` 中 1 个文档门红灯，根因是测试把历史标题固定在交接前 96 行；改为锁定“当前合同读取门”后，定向与全量均为 `1084`、0 failure／0 error／3 个真实 ZIP 路径 opt-in skipped，`:app:assembleDebug` 通过。未运行 instrumentation 或任何 `connected*AndroidTest`。
- **历史审计修正：** 当时的全状态台账已消除 C-14／C-15 行与汇总互相冲突，并把 C-16 的 `84` 项逻辑合同、`63` 项 Browser 实渲染和代表性原生层拆开记录；设置现行合同已按 `PrivacyDataUi.kt` 修正本机数据三项清理范围。C-14 当时的声明目录只保留联系表，故在该初裁中降级为“本地主链通过、原尺寸视觉证据保全不足”。这些历史缺口后来均由上方对应闭环增量补齐。
- **当前 bundle 与隔离启动：** 主程序仍为 `31,029,136` bytes、SHA-256 `845d38678ca0b12c4778d1be2766e3deb5187db5ade0027530d95f9d05cc45bb`，ad-hoc strict codesign 通过，不是 Developer ID／公证包。新建唯一 QA Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.31679.mtl83dxd`，只使用 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.1xM11i/app-data` 与诊断启动；窗口标题正常，SQLite schema 37／84 表／0 workspace／`integrity_check=ok`，无 TCP／UDP socket，精确停止后再次回读完整。
- **边界：** 未操作 OPPO、正式 Desktop 根、账号、Provider、Key、通知或真实服务；既有 dirty／untracked 全部保留。视觉汇总板位于 `~/.codex/visualizations/2026/09/03/01a06634-d3d4-75f2-8034-8979e48fa351/nanfeng-ai-final-independent-audit-20260903/`，只作审阅索引，不替代原尺寸单页。

## 2026-09-03：C-16 主题／字体／弹层全矩阵与原生持久化闭环（当前最高优先级）

- **范围与裁决：** 本轮只处理 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的 C-16，并新增 [C-16 跨端合同](../C16_THEME_FONT_OVERLAY_PARITY_CONTRACT.md)。当前 Android `AppearanceSettings`、`applyAppearancePalette`、三档字体比例和弹层层级是唯一事实；没有用 Desktop 旧样式反推，没有执行最终全量审计。
- **共享 owner 与红灯：** 新增 `desktop-theme-owner.mjs` 统一解析系统／显式浅深、字体、主题色与表面／文字／边界 token；模型、加号、风格、搜索、设置只消费这一 owner。C-16 首轮 `2/6`，修复后 `6/6`；原生重打包又暴露 `build.mjs` 漏复制 owner／fixture 的空白页，补入构建产物红灯后为 `6/7`，最终 `7/7`。
- **自动门与 Browser：** C-16 合同 `84` 项；Browser 精确 `1440×900` 实渲染 `63` 个当前系统／浅／深 × 三档字体 × 七个层组合，溢出失败为 `0`，目标正文／表面最低对比 `8.37:1`。Desktop 完整 Node `232/232`、Rust `194/194`、lint、typecheck、computed style、inventory（0 断链）、protocol、build／bundle 全通过；Android 最小 JVM `32/32` 和 `:app:assembleDebug` 通过，未运行 instrumentation 或任何 `connected*AndroidTest`。
- **隔离 Tauri：** 固定 QA wrapper 只使用 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.c16.ourB0q` 和 `--diagnostic-ui-schema-acceptance`。原生实看深色／大字的设置主题层、加号根层、风格子层和搜索，两级 Escape、Tab 可见焦点和窗口 zoom 通过；退出重启后 AX 仍回读深色／大字。SQLite `dark/large/orange/revision 3`、0 workspace、`integrity_check=ok`，进程 TCP socket `0`、子进程 `0`。无 Provider 的 native 模型按钮按真实能力禁用，未伪造模型成功态。
- **产物与边界：** 证据位于 `~/.codex/visualizations/2026/09/03/01a06614-ce00-7dd2-b8da-1664f33e02de/nanfeng-ai-c16-20260903/`，Browser 联系表 SHA-256 `bc9a81ffa7582caa95a06dde3c0d8edad977e0e61319aee48396dd7ac932c680`。最新开发 bundle 主程序 `31,029,136` bytes、SHA-256 `845d38678ca0b12c4778d1be2766e3deb5187db5ade0027530d95f9d05cc45bb`，ad-hoc strict codesign 通过；不是 Developer ID／公证正式包。未操作 OPPO、正式 Desktop 数据根、账号、Provider、Key 或真实服务；既有 dirty／untracked 全部保留。收口 `context_gate` 为 `HANDOFF / 93.0%`，本任务已停止扩张，最终全量审计交回父任务。

## 2026-09-03：C-15 工作区／项目／知识／开发同状态与项目会话 owner 闭环（当前最高优先级）

- **范围与裁决：** 本轮只处理 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的 C-15，并新增 [C-15 跨端合同](../C15_WORKSPACE_KNOWLEDGE_PARITY_CONTRACT.md)。当前 Android 源码与关网隔离 AVD 共同确认工作模式、Projects、知识、记忆、工作区设置和开发设置的实际层级；没有按 Desktop 旧页面反推，也没有扩展 C-16 或最终审计。
- **共享 owner：** Desktop 抽出 `workspace-view.mjs`，工作导航、项目筛选和空态复用同一 owner；未选项目没有 Composer，选中 `C15_Project` 后才有 Composer，真实发送前仍为 `0 个工作对话`。工作态拒绝无 `projectId` 的普通会话；首条实际发送通过活跃 submit 链和 Rust mutation 原子持久 `conversation.projectId`。设置与工作抽屉到达同一 Projects／知识 owner。
- **红绿与自动门：** C-15 首轮 `0/4`，最终 `4/4`；完整 Desktop Node `225/225`、Rust `194/194`，lint、typecheck、static build 全通过。Android `P6GUnifiedChatFirstUiContractsTest`、`AndroidUserEntryAuditContractsTest`、`SettingsUiSimplificationContractsTest` 和 `:app:assembleDebug` 通过；没有运行 instrumentation 或任何 `connected*AndroidTest`。
- **Browser 与隔离 Tauri：** Browser 精确 `1440×900` 实看七态，页面无溢出，console warning／error 为 0。最新 development bundle 复制为唯一 Bundle ID `com.nanzhufeng.ai.desktop.c15.acceptance.7mgJQw`，只使用 `/tmp/nanfeng-ai-desktop-c15-acceptance.7mgJQw/app-data` 和诊断启动；原生实看工作空态、项目选中态并同根重开。SQLite `integrity_check=ok`，project／knowledge／memory revision 均为 `2`，活动会话／relation 均为 `0`，进程无 TCP／UDP socket。
- **产物与边界：** 证据位于 `~/.codex/visualizations/2026/09/03/01a065e5-85ed-7011-9f89-98d3c56d34d5/nanfeng-ai-c15-20260903/`。最新主程序 `31,029,136` bytes、SHA-256 `e3c505517e50e2103b1e4a82171ff1dc78cc4f21f76ddcb4cf5073240230562c`，ad-hoc strict codesign 通过；不是 Developer ID／公证正式包。未操作 OPPO、正式 Desktop 数据根、账号、Provider、Key 或真实服务；既有 dirty／untracked 全部保留。

## 2026-09-03：C-14 导入导出／本机数据同状态与隔离交换链闭环（当前最高优先级）

- **范围与裁决：** 本轮只处理 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的 C-14，并新增 [C-14 跨端合同](../C14_LOCAL_DATA_PARITY_CONTRACT.md)。当前 Android 源码与隔离 AVD 确认用户入口为“导入工作区／导出工作区”，本机数据按对话与内容／附件／导入概况分组，清理范围为失败任务、知识与记忆回收站、全部本地数据。该当前事实优先于较旧设置合同中已移除回收站清理项的表述。
- **红灯与共享 owner：** 先固定 Browser 聚合夹具、C-14 Node 红灯合同与 Android Room 主线程红灯，再抽出 `local-data-view.mjs` 统一导入导出、库存分组、清理范围和强确认门。Desktop 删除三个可见工程术语，补齐导入个性化资料与南枫转写聚合行。Android 首次全删除预览暴露 `Cannot access database on the main thread`，`PrivacyDataViewModel` 已将预览和删除后 inventory 刷新切到 `Dispatchers.IO`。
- **自动门与 Android 复验：** C-14 `4/4`，完整 Desktop Node `221/221`、Rust `191/191`，Android `PrivacyStorageNavigationContractsTest` `6/6`，`:app:assembleDebug`、lint／typecheck／inventory／protocol／static build 全通过。隔离 `NanzhufengFindN5Api35` 仅有 `emulator-5554`，1140×2616／442dpi，关网后重走“本机数据 → 删除全部本地数据”已进入禁用的强确认态，logcat 无新 FATAL；未输入确认语或提交删除。
- **Browser 与隔离 Tauri：** Browser 精确 `1440×900`，无横向溢出，console warning／error 为 0；错误确认语保持禁用，精确确认语才启用。release bundle 复制为 `com.nanzhufeng.ai.desktop.c14acceptance.16553.mtl43j5g`，仅使用 `/tmp/nanfeng-ai-desktop-c14-acceptance.SLnpAz` 与诊断启动；AX 实看三类清理、失败任务侯选和全删除禁用门。
- **交换包与数据读回：** 隔离根仅 1 workspace／1 个失败导入任务，`integrity_check=ok`，无子进程和已建立 TCP。原生“导出工作区”生成 `/tmp/nanfeng-ai-desktop-c14-bundle.WYfyvk/c14-export-readback.nfai-exchange`（3142 bytes，SHA-256 `0dfc2b6fe911aa4441b0b4dbb131f2df6d4e3a6a82bad9d2da529b60f0997385`），ZIP 全条目检验通过。导入成功与清理持久由同一 Rust preflight／commit owner 的隔离测试覆盖，没有为点击 UI 而破坏唯一根的可重开性。
- **产物与边界：** 证据与联系表位于 `~/.codex/visualizations/2026/09/03/01a065b9-b771-7721-9ff0-8b240b43dc02/nanfeng-ai-c14-20260903/`，联系表 SHA-256 `9b00717f9dc1f6e61cb7193f1aaafc0eb53d9b8c5d81f639f7e41d942a9d3950`。开发 bundle 主程序 `30,966,784` bytes、SHA-256 `d35ee5e7f20877d282fd1e6c61c9153da68a4f6baf1a5092a05bc017923e90c7`，ad-hoc strict codesign 通过；不是 Developer ID／公证正式包。未操作 OPPO、正式 Desktop 数据根、账号、Provider、Key、通知或真实服务，未运行 instrumentation 或任何 `connected*AndroidTest`；C-15／C-16 保持未动。

## 2026-09-03：C-13 对话生命周期同状态与隔离持久链闭环（当前最高优先级）

- **范围与裁决：** 本轮只处理 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的 C-13，并新增 [C-13 跨端合同](../C13_CONVERSATION_LIFECYCLE_PARITY_CONTRACT.md)。当前 Android 源码与隔离 AVD 共同确认：根页固定为收藏／已归档／回收站；收藏显示更新时间且只可取消收藏，归档／回收站显示创建时间且有恢复／删除；已归档删除可恢复，回收站删除不可恢复；行可进入真实会话并返回原列表。
- **共享 owner 修复：** Desktop 抽出 `conversation-lifecycle-view.mjs`，根页、三类列表、说明、空态、时间字段和动作集合不再由设置壳分别拼装。Android 左滑在 Desktop 投影为行尾省略号 disclosure，动作默认不常驻；归档删除确认补回会话标题和“可恢复”事实。Browser 只读 fixture 与原生 `/tmp` fixture 分离，原生 fixture 仅在显式 C13 marker＋唯一临时根＋诊断参数同时成立时启用。
- **红绿与自动门：** C-13 首轮合同 `1/5`，最终 `5/5`；Desktop lint、typecheck、完整 Node `217/217`、Rust `189/189`、static build 均通过。Android `P6DConversationRowAccessibilityContractsTest` 与 `DialogCopyBrevityContractsTest` 共 `90/90`，`:app:assembleDebug` 通过；未运行 instrumentation 或任何 `connected*AndroidTest`。
- **Android 与 Browser 实看：** 隔离 AVD `NanzhufengFindN5Api35` 仅有 `emulator-5554`，1140×2616／442dpi，关闭网络后通过当前 APK 的真实 UI 建立 `C13-Favorite`、`C13-Archived`、`C13-Recycle`；采集根页、三列表、三类动作与可恢复／永久删除确认。Browser 精确 `1440×900`，根页、三列表、动作 disclosure 和两类确认无横纵溢出，console warning／error 为 0。
- **隔离 Tauri：** 当前 release bundle 复制为唯一 Bundle ID `com.nanzhufeng.ai.desktop.c13acceptance.10651.mtl2ilud`，使用 `/tmp/nanfeng-ai-desktop-c13-acceptance.1XN4xr` 和 `--diagnostic-ui-schema-acceptance`。原生 AX／截图确认三列表、动作、批量动作和两类确认；实际恢复 `C13 Archived` 后归档页为空，退出重启后仍在活动列表；收藏会话进入后顶部显示“返回收藏”，返回原页。SQLite 始终 1 workspace、3 conversations、1 favorite，最终归档 revision `3`／`archived=false`、回收站仍 `deleted=true`，`integrity_check=ok`；进程无网络 socket。永久删除真实提交由 Rust 隔离测试完成，原生 UI 只打开确认并取消。
- **产物与边界：** Android／Browser／Tauri PNG、XML 与联系表位于 `~/.codex/visualizations/2026/09/03/01a06594-b8df-7312-b5e6-0ce6d2940666/nanfeng-ai-c13-20260903/`；Browser 证据另位于 `desktop/output/playwright/c13/`。当前开发 bundle 主程序 `30,989,536` bytes、SHA-256 `d6bf12dc013e1295fe7dffeea29a3f950a935c9a938f2aad781dcc3be604e0f0`，ad-hoc strict codesign 通过；不是 Developer ID／公证正式包。结束时隔离 App 和模拟器均已关闭；未操作 OPPO、正式 Desktop 数据根、账号、Provider、Key 或真实服务，既有 dirty／untracked 全部保留。

## 2026-09-03：C-12 模型与联网／调用记录本机主链闭环（当前最高优先级）

- **范围与裁决：** 本轮只处理 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的 C-12，并新增 [C-12 跨端合同](../C12_MODEL_NETWORK_PARITY_CONTRACT.md)。当前 Android `AutomaticWebSearchPolicy` 明确“开即每次普通对话检索”，不按关键词猜测；`ModelSettingsUi` 明确附件保持显式联网、无结构化公开来源即失败关闭。Desktop 已按该事实接通全局／会话 owner，没有根据 Provider 常识扩写能力。
- **请求与来源 owner：** Rust 删除关键词门，DeepSeek／智谱不再因附件静默关闭联网；OpenRouter、Qwen、DeepSeek、智谱只保留代码中实际存在的请求路由。Responses 与 Chat Completions 统一提取、校验、去重公开 `http`／`https` 来源；网页路由完成却没有来源时返回 `WEB_SEARCH_NO_SOURCES`，不把模型正文伪装成联网成功。
- **设置与记录投影：** 全局控件统一为 `role="switch"`＋`aria-checked`，开启文案精确对齐 Android。费用与用量有记录时只保留会话／标题／历史／南枫转写四类 2×2 与四段；提醒／监控放在“其他自动任务”独立事实区。上下文记录补发生时间；连接失败和自动任务诊断补耗时与时间。完全无记录仍保持 Android 当前空态，不伪造金额。
- **红绿与自动门：** C-12 合同首次 `0/4`，最终 `5/5`；Desktop lint、typecheck、完整 Node `212/212`、Rust `187/187`、inventory（229 个可见 action、0 断链）、protocol golden、static build、主题 computed style 均通过。`cargo fmt --check` 只剩本轮开始前同一 `lib.rs` 的 3 处无关 dirty 格式差异，C-12 新增格式已手工消除，未批量格式化覆盖用户改动。Android `AutomaticWebSearchPolicy` 等七类专项共 `27/27`，`:app:assembleDebug` 通过；未运行 instrumentation 或任何 `connected*AndroidTest`。
- **Browser 与隔离 Tauri：** Browser 精确 `1440×900`，模型与联网、模型设置、费用、上下文、诊断五态无溢出，console warning／error 为 0；开关关闭后刷新仍关闭，再恢复开启。合格原生复验用明确 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.AKdmv5` 和 `--diagnostic-ui-schema-acceptance`，AX `on → off → 重启仍 off`；SQLite 0 workspace、`web_search_enabled=0`、revision `2`、`integrity_check=ok`，进程网络 socket 为 0。
- **隔离事故与边界：** 首次复制包仅改 `CFBundleIdentifier`，Tauri 仍按编译期 identifier 取得正式根 `.runtime-owner.lock`；发现后立即终止，未做 UI 操作、账号或 Provider 调用。诊断启动允许 schema migration，因此不能承诺该次对正式根零写入，后续只以显式 `/tmp` 根为合格证据。未操作 OPPO／Android 设备、未读取真实 Key、未调用真实 Provider／网页／账号／通知。现有 Android 截图只覆盖根页；带数据费用／上下文／诊断的 Android 同内容截图和真实 Provider／来源／费用仍独立未验。
- **产物：** Browser／原生 PNG 与联系表位于 `~/.codex/visualizations/2026/09/03/01a0657a-fb01-7603-8db8-850d1886980e/nanfeng-ai-c12-20260903/`。当前开发 bundle 主程序 `30,966,688` bytes、SHA-256 `f270fdee37fe10666f04ed15c3fcf096a31b4e6f00091d96070e068f490244f4`，ad-hoc strict codesign 通过；不是 Developer ID／公证正式包。既有 dirty／untracked 全部保留，未 reset／clean／checkout。

## 2026-09-03：C-11 设置首页、个性化与外观持久化闭环（当前最高优先级）

- **范围与裁决：** 本轮只处理 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的 C-11。以当前 Android `SettingsCategoryList`、`AppearanceSettingsPage`、`AssistantExperienceSettings` 和隔离 Find N5 模拟器为唯一可见事实：设置固定为对话、外观、数据管理、工作区四组；外观为系统（默认）／浅色／深色，字体为小／标准／大，主题为橙／蓝／黑／绿／黄／粉／紫，自定义指令上限为 `8000` 字。
- **共享 owner 修复：** Desktop 的 Browser 与 native 默认能力改为复用已实现能力常量，个性化页不再长期显示“普通发送尚未接入个性化上下文”工程阻断卡、SQLite 实现说明或禁用已实现控件；成功修改外观时不再显示持久层实现通知。自定义指令 Browser、全屏编辑与 Rust 校验统一复用 `8000` 常量。主题选择面只保留一个通用预览表面，七个实际色值直接来自 `THEME_COLORS`，删除七套重复 CSS owner。
- **红绿与自动验证：** 新增 `desktop/tests/c11-settings-appearance-parity.test.mjs`；首轮 `2/4`，追加“不得显示持久层假成功通知”合同后该项按预期红灯，最终 C-11 `5/5`。主题 computed style 七色通过；Desktop `lint`、`typecheck`、完整 Node `207/207`、static build 通过。Rust `185/185` 与 `cargo check` 通过，并守住 `8000` 接受／`8001` 拒绝且拒绝不改库。Android 四组最小 JVM 合同与 `:app:assembleDebug` 通过，未运行 instrumentation 或任何 `connected*AndroidTest`。
- **Android 与 Browser 实看：** 隔离 AVD `NanzhufengFindN5Api35` 仅有 `emulator-5554`，1140×2616／442dpi、Wi-Fi／移动数据关闭；安装当前 checkout debug 包后重采设置四组、个性化、外观、字体与主题选择面。Browser 精确 `1440×900`，四组与三个选择面完整、横向溢出为 0；切到“大／紫色”后真实刷新仍回读，随后恢复“标准／橙色”。应用运行 warning 为 0；控制台另有一个不影响 C-11 的 `/favicon.ico` 404。
- **隔离 Tauri：** release bundle strict codesign 通过后复制为唯一 Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.99982.mtl07v2x`，以 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.zQna4W` 和 `--diagnostic-ui-schema-acceptance` 启动，正式 bundle 未启动。原生 AX／截图确认四组、个性化 `8000` 字及三个选择面；切到“大／紫色”后退出重启仍回读。隔离 SQLite 为 schema 37、0 workspace，`desktop_app_settings` 实值为 `system/large/purple`。
- **产物与边界：** 最新开发 bundle 位于 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，主程序 SHA-256 为 `a894e6a54850b722bbe4254654c91c29c513cf3c0679572bdd6b88124df5cdb3`，ad-hoc strict codesign 通过；不是 Developer ID／公证正式包。Android、Browser、Tauri PNG／XML 与联系表位于 `~/.codex/visualizations/2026/09/03/01a06560-da3b-77d0-8a1c-08497b07c352/nanfeng-ai-c11-20260903/`。未操作 OPPO、正式 Desktop 数据根、Provider、账号或通知；既有 dirty／untracked 全部保留，未 reset／clean／checkout。

## 2026-09-03：C-10 南枫转写当前全状态与隔离导入持久链闭环（当前最高优先级）

- **范围与裁决：** 本轮只处理 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的 C-10，并新增 [Android 南枫转写当前界面合同](../ANDROID_TRANSCRIPTION_UI_CURRENT_CONTRACT.md)。当前 `Nanfeng_AI` Android 的可见真相是 GLM-OCR 图片／PDF → Markdown；Android 当前没有录音入口，Desktop 不恢复新建音频／视频、设置或重试入口，旧任务仅分组只读。
- **共享页面与 owner 修复：** Desktop 空态改为单画布和可用橙色“选择图片或 PDF”，底部没有白色托盘；有任务后保留 Desktop 宽屏列表＋详情。待确认、等待处理／正在转写／正在保存、失败／需恢复和已生成均投影真实持久字段；原文件与结果统一走 App 内共享附件预览，完成态提供复制、TXT／MD／DOCX 与“加入新对话”。完整回归发现旧 GLM-OCR 深层合同漏出后，又补回请求 ID 与安全错误码，没有用新页面覆盖旧持久事实。
- **红绿与自动验证：** 新增 `desktop/tests/c10-transcription-parity.test.mjs`，首轮 `0/4` 按预期红灯，修复后 C-10 定向 `4/4`、转写组合 `8/8`，完整 Node `202/202`；`lint`、`typecheck`、static build 通过。Rust 转写 owner `8/8`，诊断模式／凭据隔离／schema 迁移定向测试和 `cargo check` 通过。Android `GlmOcrContractsTest`、`GlmOcrUiContractsTest`、`GlmOcrRoomMigrationContractsTest` 与 `:app:assembleDebug` 通过；未运行 instrumentation 或任何 `connected*AndroidTest`。
- **Browser 可见验收：** `1440×900` 实点空态 → 选择 → 待确认 → 开始 → 正在转写，并用只读夹具覆盖失败和完成后关闭返回；页面横向溢出为 0，控制台 warning／error 为 0。夹具明确标注不读文件字节、不写 Desktop SQLite、不调用 Provider。五态截图与联系表位于 `~/.codex/visualizations/2026/09/03/01a06547-6299-77e1-8413-2425eb0792a4/nanfeng-ai-c10-20260903/`。
- **隔离 Tauri：** release bundle 复制到唯一 Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.93867.mtkz4v0h`，以 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.jM15Ju` 和 `--diagnostic-ui-schema-acceptance` 启动。真实系统选择器导入 `nanfeng_ai_launcher_icon_master_20260828.png`（1,403,227 bytes），App 内成功预览 1254×1254 原图；SQLite 回读 1 条 `QUEUED`、Attempt 0、请求 0 的任务及私有内容寻址附件，关闭重启后仍存在。诊断模式禁用 Provider 凭据、网络、后台执行和业务恢复；没有读取正式数据根。
- **产物与未验边界：** 最新开发 bundle 位于 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，主程序 31,027,488 bytes、SHA-256 `c251f2d9870d370fa1e0521fd9e71365aeb26d88ebf6d007cce78cfd3a2431ea`，ad-hoc strict codesign 通过；不是 Developer ID／公证正式包。真实 GLM-OCR Provider 的成功结果、服务端失败与账单没有验收，Browser 失败／完成夹具和 Rust owner 测试不能替代它。没有操作 OPPO、账号、通知或正式数据根；既有 dirty／untracked 全部保留，未 reset／clean／checkout。

## 2026-09-03：C-09 定时任务全屏层级与本机持久化主链闭环（当前最高优先级）

- **范围与结论：** 本轮只处理 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的 C-09。Desktop 已按当前 Android 对齐全屏“已计划”、右侧关闭、紧凑通知状态、精确空态、橙色全宽“添加计划”、全屏新建／编辑和返回层级；启用、暂停、结果、失败、编辑、删除确认均有可见状态。单次、指定时间、每周、独立编辑和删除确认是 Desktop 既有本机 owner 扩展，不再冒充 Android 当前能力。
- **Android 当前事实与风险：** 仅启动隔离 Find N5 AVD `NanzhufengFindN5Api35` 的 `emulator-5554`（1140×2616／442dpi），关闭 Wi-Fi／移动数据并安装本 checkout `0.3.0-p10j` debug APK（SHA-256 `73f0fc727f006646bdf871c016f45b57ccf03edeeec25d15d80bf666ef3a1e4b`）。源码与 AVD 共同确认 Android 当前字段只有任务名称、监控要求、每小时／每天和状态；列表／新建层级已采 PNG＋XML。输入阶段出现一次 `Input dispatching timed out` ANR，重启 App 后恢复，因此本轮不声称 Android 已完成保存后的生命周期实测。
- **行为红绿与自动验证：** 新增 `desktop/tests/c09-scheduled-task-parity.test.mjs`，首轮 `0/5` 按预期红灯，修复后 `5/5`。Desktop 完整 Node `198/198`、`lint`、`typecheck`、static build 通过；Rust 默认草稿名／拒绝草稿定向测试通过，`cargo check` 通过；release `.app` 打包与 ad-hoc strict codesign 通过。Android 五组定时任务／通知 JVM 合同与 `assembleDebug` 通过；未运行 instrumentation 或任何 `connected*AndroidTest`。
- **Browser 实看：** `1440×900` 下实点空态 → 新建 → 周期切换 → 保存返回 → 暂停 → 编辑／取消 → 删除确认／取消，并另用只读状态夹具覆盖启用、暂停、结果与失败；notification／主按钮／保存按钮的最终计算样式分别为白色表面、橙色主按钮和橙色保存按钮，横纵溢出与 console warning／error 均为 0。Web 夹具明确标识不会写 Desktop SQLite。
- **隔离 Tauri 主链：** 当前 release bundle 复制到唯一临时 bundle ID，以 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.c09.KeqJM9` 隔离根和 `--diagnostic-ui-schema-acceptance` 启动；原生完成创建 `C09 本机持久计划` → 暂停 → 编辑为 `C09 本机计划已编辑` → 恢复 → 打开删除确认并取消。最终重开确认只有 1 条 ACTIVE／ONCE 计划、0 次运行、0 个待处理草稿；暂停态显示“已暂停，恢复后重新计算下次时间。”，手动新建取消不会遗留默认草稿。诊断进程无子进程、无网络 socket，没有读取正式 Desktop 数据根。
- **产物与边界：** 截图、Android XML 与最终联系表位于 `~/.codex/visualizations/2026/09/03/01a06519-f65e-7842-ae05-70d2f1be5e06/nanfeng-ai-c09-20260903/`。最新开发 bundle 位于 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，主程序 `31,027,488` bytes、SHA-256 `b229666dc3f369bbaedb15ef5423962d986aec72387a13822e213a35cd842e29`，ad-hoc strict codesign 通过；不是 Developer ID／公证正式包。系统通知投递／点击和真实 Provider 执行未验收；没有调用账号或通知、没有操作 OPPO、没有读取正式数据根，也没有 reset／clean／checkout。收口 `context_gate` 为 `HANDOFF / 92.1%`，本线程停止扩张范围。

## 2026-09-03：C-08 全屏搜索与 App 内预览闭环（当前最高优先级）

- **范围与结论：** 本轮只处理 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的 C-08。当前 Android、Desktop `1440×900` Browser 和隔离 Tauri 已覆盖全屏搜索、六分类、查询、文件类型、时间／大小排序、还原、真实结果事实、直接 App 内预览或安全失败，以及关闭后回到同一查询／分类／结果位置。C-02／C-03 的隔离 Tauri 内容态／有会话列表态和 C-04～C-06 的跨端硬门保持原结论；C-07 也未借本轮改写。
- **共享安全预览 owner：** 新增 `desktop-attachment-preview-owner.mjs`，搜索、会话气泡与草稿入口共用图片／PDF／视频／音频／安全文本能力判定；未知或未验证格式只进入 App 内安全边界，不执行文件。C-08 搜索结果删除重复的“系统打开”动作与外部查看器回退，只保留一个直接预览入口。全屏搜索原先高于通用 scrim，导致弹窗虽进入可访问树却在视觉上被搜索页盖住；现将搜索模式 scrim 提到搜索层之上，并以红绿合同锁定。
- **原生资产根因与修复：** 隔离 Tauri 初次验收可在真实 SQLite 搜到 `golden-note.txt`，但无法预览；根因是 acceptance setup 直接提交导入元数据时跳过了生产导入链的 `preflight.assets` 私有资产落盘。Rust 现提取共享 `persist_preflight_assets`，生产导入与隔离验收都先写入已校验内容寻址资产，再提交引用。原生复验显示 `golden-note.txt · text/plain · 43 B`，App 内正文为 `Non-sensitive exchange fixture attachment.`，关闭后仍停在同一搜索结果。
- **Android 当前证据：** 仅使用隔离 Find N5 AVD `NanzhufengFindN5Api35` 的 `emulator-5554`（1140×2616／442dpi），网络关闭；重采空搜索、全部结果、正文命中、文件大小排序、还原月份分组、文本预览和返回状态。固定文本样本可 App 内成功预览；图片／PDF／音频／视频样本字节无效，当前只证明安全失败。结束时已精确关闭模拟器；没有连接或操作 OPPO，没有运行 instrumentation／`connected*AndroidTest`。
- **Browser 与隔离 Tauri 实看：** Browser 使用显式标注的只读 Android 同内容 fixture，不读写 Desktop SQLite；`1440×900` 下确认 `正文 1／附件 6`、正文命中、文件 `2` 项、大小正序、还原月份分组、可见安全边界弹窗及返回保持，console warning／error 为 0。隔离 Tauri 使用唯一 bundle ID 和全新 `/tmp` 数据根，只读真实 SQLite 与私有资产；确认 `正文 1／附件 1`、43 B 文本直接 App 内预览和返回保持，没有读取正式 Desktop 数据根。
- **自动门禁：** C-08 Node 合同首轮 `0/3` 红灯，补入预览层级后完整为 `6/6`；Desktop 完整 Node `192/192`、`lint`、`typecheck`、static build、`cargo check` 通过。Rust 统一搜索、图片／音频／视频／文本预览及隔离资产持久化定向测试通过。Android 五组搜索／预览 JVM 合同、`:app:assembleDebug` 与 `:app:assembleSearchAttachmentAcceptance` 通过。`cargo fmt --check` 仍只报告本轮开始前已 dirty 的同一 Rust 文件三处无关格式差异，本轮未批量格式化以免覆盖用户改动。
- **产物与剩余边界：** 最新开发 bundle 位于 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，strict codesign 通过，主程序 SHA-256 为 `f8701bb711a642722ee7a5fb82acd51148a5369724196e9e287e49ca5a6446be`；它不是 Developer ID／公证正式包。截图、Android XML 与最终联系表位于 `~/.codex/visualizations/2026/09/03/01a064ed-1eaa-7e30-b329-ecdbe733840a/nanfeng-ai-c08-20260903/`。Android 有效正向样本仅覆盖文本；Desktop Tauri 可见正向样本也仅覆盖文本，ZIP／DOCX／MD／JSON／PDF／图片／视频／音频等 MIME 的原生可见成功态仍须有有效固定样本后逐项补验，不能用安全失败或单测冒充。未调用 Provider／账号／通知；既有 dirty／untracked 全部保留，未 reset／clean／checkout。

## 2026-09-03：C-07 Composer 加号、基础风格和实时网页搜索已闭环（当前最高优先级）

- **范围与结论：** 本轮只处理 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的 C-07。当前 Android、Desktop `1440×900` Browser 和隔离 Tauri 已对齐加号根层、五风格子层及普通会话实时网页搜索开关；C-02 隔离 Tauri 内容态、C-03 隔离 Tauri 有会话列表态以及 C-04～C-06 的跨端硬门均保持原结论，没有借本轮改写。
- **共享短暂面板 owner：** Desktop 删除旧 `.composer-add-popover` 实现，加号与模型选择统一复用 `.composer-transient-sheet-*` 的定位、点外关闭、焦点恢复和子层返回生命周期。根层固定相机 → 图片 → 文件 → 基础风格和语气 → 实时网页搜索；附件图标／文字为黑色，风格与地球图标跟主题色。风格子层只有直言不讳、专业可靠、亲和友善、高效务实、风趣搞笑和唯一勾选，不含默认、说明或完成按钮；临时聊天不暴露不能生效的普通会话偏好入口。
- **首问真实链路：** Desktop 空白普通会话不再因缺 conversation ID 丢弃选择。草稿风格／联网值随首条发送进入 Rust；新会话、`p6g_conversation_override` 与请求构建在同一事务边界内完成，首问即消费所选风格与联网工具。既有会话仍即时持久。实时网页搜索控件改为标准 `role="switch"`＋`aria-checked`，修复原生 AX 把橙色开启态读成 `off` 的语义红灯。
- **Android 当前证据：** 仅使用隔离 Find N5 AVD `NanzhufengFindN5Api35` 的 `emulator-5554`（1140×2616／442dpi），网络关闭；安装本 checkout debug APK 后复采根层、专业可靠子层、联网关闭及折叠重开 XML。专业可靠和联网关闭均在重开后保持；结束时已恢复联网开启并精确关闭模拟器。没有连接或操作 OPPO，没有运行 instrumentation／`connected*AndroidTest`。
- **Browser 实看：** `1440×900` 下逐层验证根层顺序、专业可靠单选、`Back`／两段 `Escape`、透明点外关闭、联网 `on → off → 重开仍 off → 恢复 on`；console warning／error 为 0。相机、图片、文件分别显示 Web 预览能力边界且不写 Desktop SQLite、不伪造附件成功。
- **隔离 Tauri：** 最新 release bundle 复制到独立 bundle ID，并以全新 `/tmp` 数据根启动；原生 AX 树确认同一根层顺序、专业可靠唯一选中以及联网 `on → off → 重开仍 off`。隔离库为 37 版 schema、84 张表、0 workspace、0 正式数据；未发送首条消息，因此 0 条 override 符合预期，首问原子持久由 Rust 测试守门。进程无网络 socket；验收后已停止，两个新旧 bundle 和数据根均移入废纸篓。Tauri 原生附件选择器执行本轮未触发，继续作为附件功能链的独立待验项。
- **自动验证与产物：** 新增 `desktop/tests/c07-composer-add-sheet.test.mjs`，首轮 C-07 合同 `0/5` 按预期红灯；原生 switch 语义另出现 `2` 项红灯，修复后定向 `82/82`。Desktop 完整 Node `187/187`、`lint`、`typecheck`、static build 通过；Rust `183/183` 通过并新增首问偏好原子落库／重开测试；Android `testDebugUnitTest` 与 `:app:assembleDebug` 通过。release `.app` 通过 strict codesign，主程序 SHA-256 为 `1bbe8d87152e4c81a0b7e495b5318cbbc7c1e74bf9456d388edf576b104ae7a7`。
- **证据与安全边界：** 截图、Android XML 和两张联系表位于 `~/.codex/visualizations/2026/09/03/01a064c0-5646-76d2-bc9d-f3e382b7fe5b/nanfeng-ai-c07-20260903/`。未读取正式 Desktop 数据根，未操作正式 Desktop 进程，未调用 Provider／账号／通知；既有 dirty／untracked 全部保留，未 reset／clean／checkout。收口 `context_gate` 为 `HANDOFF / 93.0%`，本线程停止扩张范围。

## 2026-09-03：C-01 至 C-03 会话画布、Composer 与侧栏同步（上一增量）

- **范围与结论：** 本轮只完成 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的 C-01～C-03，没有扩张到 C-04 以后页面。C-01 空对话已满足 Android、Desktop `1440×900` Browser 与隔离 Tauri 同状态实看；C-02、C-03 的 Android／Desktop Browser 同状态已通过，但隔离 Tauri 的内容会话／有会话列表仍缺安全 fixture，继续明确标未验收。
- **共享 owner 修复：** `desktop/src/chat-shell.mjs` 删除空态 Hero 图形、“今天想一起做什么？”和持久化技术说明，空态／内容态统一复用底部 Composer；Composer DOM 固定为 `+ → textarea → model/send`，焦点态只在控件内部扩展。侧栏补搜索图标，顺序锁定为品牌 → 搜索 → 定时任务 → 南枫转写 → 分组会话 → 底部设置／新对话；footer 透明，仅两个独立浮动胶囊绘制表面。Browser 计算样式发现功能行原被高优先级通用规则压成 36px，修正共享 selector 后实际为 44px。旧台账所谓 Android“底部白色整栏”与当前 `ConversationNavigationDrawer` 冲突，已撤销。
- **同内容 Browser fixture：** Web 预览只读 fixture 改为 Android 同一 user message `C02-local-visual-fixture`，并投影 `PROVIDER_NOT_ENABLED`／“回答未完成”，用于对齐顶部动作、消息角色、失败事实、唯一消息滚动 owner 与 Composer；它不会写 Desktop SQLite，也不发 Provider 请求。
- **Android 当前证据：** 只启动只读 `NanzhufengFindN5Api35` 模拟器 `emulator-5562`（1140×2616／442dpi），Wi-Fi 与移动数据均关闭；重新安装本 checkout 的 `0.3.0-p10j` debug 包并只清理该隔离模拟器的新鲜 app 数据。C-01 空态、C-02 固定消息＋服务商未启用失败态、C-03 含“新对话”最近列表均有 PNG 与 UIAutomator XML。`adb devices -l` 始终只有该模拟器；未连接、读取或操作 OPPO。验收后已精确关闭模拟器。
- **Desktop Browser 证据：** `1440×900` 下 C-01 hero count 0、空画布 stage 高 812px、Composer 固定于 y=826；C-02 同一 user message、2 条消息、顶部“新对话／更多”、唯一 message-list 与固定 Composer；C-03 搜索图标存在，功能行 44px，设置 96×44、新对话 112×44，footer 无托盘。三态页面横纵溢出均为 0，console warning／error 均为 0。Android／Desktop 各 3 张 PNG、逐态 XML、隔离 Tauri 截图与两张联系表位于 `~/.codex/visualizations/2026/09/02/01a062ed-e634-7293-a65d-38dc75b87e29/nanfeng-ai-c01-c03-20260903/`。
- **隔离 Tauri：** 当前 release bundle 在全新 `/tmp/nanfeng-ai-p6-v2-picker-acceptance.c01c03.*` 根启动，AX 树确认 `tauri://localhost`、空对话、空画布、Composer、品牌／搜索／定时任务／南枫转写／设置／新对话均可达，且“还没有本地会话”证明未读正式库。空根没有 workspace／本地模型目录，发送按钮正确禁用，故没有伪造 C-02 原生内容态；临时根验收后已移入废纸篓。
- **自动验证：** 新增 `desktop/tests/c01-c03-chat-shell-parity.test.mjs`，首轮 4/4 按预期红灯；修复后定向合同全绿。Android `P6GUnifiedChatFirstUiContractsTest`、`ConversationDrawerGeometryContractsTest`、`FBP6041ComposerGlyphContractsTest` 通过，`:app:assembleDebug` 通过；未运行任何 instrumentation／connected 测试。Desktop `lint`、`typecheck`、完整 Node `180/180`、`inventory:audit`、`protocol:test`、static build、`cargo check` 与 release `.app` bundle／ad-hoc codesign 均通过；inventory 仍为 222 个可见 action 全有 handler、125 个 invoke 全有已注册 Rust command。最终主程序 SHA-256 为 `15b9c3348299a353f5d6a064782edcfceda2632b3c82a387a1da35ea5a604561`。
- **安全边界：** 没有读取正式 Desktop 数据根，没有操作已有正式 Desktop 进程，没有调用真实 Provider／账号／通知，没有操作主设备，没有运行任何 `connected*AndroidTest`。当前仍在 `main`、原始 HEAD `dd3a445c860c`；开始时 tracked dirty 60、untracked 17，全部保留，未 reset／clean／checkout。收口 `context_gate` 为 `HANDOFF / 93.4%`，已生成线程级交接，不再扩张范围。
- **下一步断点：** 若要彻底关闭本增量的原生硬门，先用独立本地 fixture 补 C-02 隔离 Tauri 内容态与 C-03 有会话列表态；否则下一视觉增量从 C-07、C-09、C-11、C-12、C-14、C-15、C-16 里单独选一组。C-04～C-06 仍需改后 Android 与隔离 Tauri 同状态证据，不能被本轮 C-01～C-03 截图替代。

## 2026-09-03：C-04 至 C-06 模型 Sheet、候选目录和运行态小字已完成（上一增量）

- **本轮结论：** 只完成再审计第一增量 C-04 至 C-06，没有推进后续页面。Desktop 普通／临时会话的模型选择已统一为 Composer 关联 Sheet：16% 固定遮罩、拖拽条、28px 完整圆角面、根层“自动选择／按任务选择”、日常／深度二级“选择具体模型”、16px 独立卡片、浅橙选中态、橙色文字／勾，以及关闭／返回双层键盘语义。旧 `.p6g-model-popover`／`.p6g-model-option` owner 已删除。
- **目录与运行态真值：** Android `ChatModelRouting` 和 Desktop 共享目录均把深度第六项固定为 `Qwen3.8-Max`。旧 `Kimi K3` 不再进入可见选择或 Auto；历史精确选择值仍保留模型归因并标记 retired，执行时 fail-closed，不静默改成 Qwen。日常／深度小字统一为 `Provider · 当前会话实时联网／未联网`；DeepSeek 使用当前时刻的高峰／低谷价再拼接会话联网状态，并在价格边界自动刷新。
- **行为红绿证据：** 新增 `desktop/tests/c04-c06-composer-model-sheet.test.mjs`；首轮 4 项均按预期红灯，Android 两组定向测试首轮共 3 个断言红灯。修复后 Desktop 定向 `81/81`，完整 Node `175/175`；Android 定向 `14/14`，完整 `testDebugUnitTest` 通过，`assembleDebug` 通过。Desktop `lint`、`typecheck`、`build`、`inventory:audit`、`protocol:test`、`cargo check` 均通过；inventory 为 222 个可见 action 全有 handler，125 个 invoke 全有 Rust command 且已注册。
- **Browser 实看：** 隔离静态预览在 `1440×900` 下逐层实点根层、日常 6 项、深度 6 项；浅色＋标准＋橙色和深色均采图，页面无滚动溢出。第一次 `Escape` 从候选层返回根层，第二次关闭；warning／error console 为 0。6 张 PNG 与联系表位于 `~/.codex/visualizations/2026/09/02/01a062c3-f533-7852-82a9-b84a8b2ea6dd/nanfeng-ai-c04-c06-20260903/`。结束前已恢复系统主题＋标准字体＋橙色，并恢复浏览器视口。
- **严格未验收：** 本轮没有采集 Android 改后同状态截图，也没有启动隔离 Tauri WebView；因此 [Android → Desktop 全状态视觉再审计](../ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 将 C-04 至 C-06 写为“Desktop Browser 实看通过、跨端硬门未验收”，没有冒充最终跨端通过。浏览器预览不会写 Desktop SQLite，故具体模型持久化仍由行为测试守门；未调用真实 Provider。
- **既有无关红灯：** 额外运行 `npm run theme:computed-style` 时，脚本仍要求 `.android-settings-color-preview` 使用唯一 `--settings-dot`，而开始前已 dirty 的设置页 CSS 当前使用七个硬编码颜色类，断言在启动 Chrome 前即失败。该设置页 owner 不属于 C-04 至 C-06，本轮未扩张修改；后续处理 C-11／C-16 时应先基于其原任务上下文收敛，不能把这项失败归因于模型 Sheet。
- **工作区与安全边界：** 当前仍是 `main`、HEAD `dd3a445c860c`；开始时已有 72 个 dirty／untracked 路径，全部保留，未 reset／clean／checkout。没有读取正式 Desktop 数据根，没有操作用户正在运行的 Desktop 进程，没有调用 Provider／账号／通知，没有操作 OPPO，也没有运行任何 `connected*AndroidTest`。`context_gate` 为 `HANDOFF / 94.3%`。
- **下一步断点：** 若继续当前同步任务，先补 C-04 至 C-06 的改后 Android 同状态截图与隔离 Tauri 1440×900 实看；若用户要求直接进入下一增量，再从台账 C-01、C-03、C-07 等未完成项中单独选一组，继续遵守一个增量一个验收，不能沿用本轮 Browser 截图替代原生或其它页面证据。

## 2026-09-02：Desktop 空白／新会话模型选择已按 Android 还原（当前最高优先级）

- **根因与修复：** Desktop 把 `selectedConversationId == null` 错当成模型“未配置”，同时禁用了 Composer 模型按钮；候选又只读取可为空的 P6-G 快照。现已改为读取当前模型服务目录，空白／新会话默认显示 `DS V4`，入口保持可点击，并还原 Android 的“自动／日常／深度 → 完整具体模型列表”两级选择。
- **逐项文案与视觉复核：** 日常／深度共 12 个可选模型现在逐项读取 Android 模型目录的用户说明，不再渲染 `OPENROUTER`、`DEEPSEEK`、`ZHIPU`、`QWEN` 等 Provider／工程标识；二级页标题精确居中，标题下的分组泛化小字已删除，模型名与说明改回可读的正常深色。P6-G `LOCAL` fixture 已移除 UI 安装入口，且候选收集层会剔除历史快照中的 fixture，不能再显示给用户。
- **首次发送所有权：** 新会话手动模型先保存在 draft 状态，首次发送时由 Rust `submit_desktop_ordinary_chat` 在同一 SQLite 事务中创建会话、写入精确模型 override、user message 与 Attempt；提交失败保留草稿与模型选择。已有会话继续使用原 conversation override owner。Provider／Key 是否可用仍是独立门禁，选择成功不伪装联网成功，也不静默换模型。
- **自动门禁：** Desktop Node `169/169`、Rust `182/182`，lint、typecheck、inventory、protocol golden、static build 与 `cargo check` 全通过；inventory 为 222 个可见 action 全有 handler，126 个 invoke 全有已注册 Rust command。
- **可见验收：** 隔离 Web 预览已实点日常与深度全部 12 个候选：6 个日常和 6 个深度均显示逐项用户说明；未出现 Provider ID、旧分组小字或 fixture。`1440×900` 与 `1000×800` 下浮层完整可见，二级标题中心偏移均为 `0px`，说明文字 computed color 为 `rgb(81, 90, 84)`；console warning／error 为 0。此证据不替代 macOS Tauri WebView 原生实看或真实 Provider 请求。
- **最新 App：** 已在不启动 GUI 的前提下重打包 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`；主程序 `31,014,272` bytes，SHA-256 `87190f540f990fd695f8da6eb3bc3e2d41816d41e3058c20fe61b10ded8c12ef`，`codesign --verify --deep --strict` 通过。没有替用户退出或重启正在运行的旧进程，需重新打开该 App 才会载入本轮资源。
- **边界：** 未读取正式 Desktop 数据根，未调用 Provider／账号／通知，未启动或置前用户 GUI，未操作 OPPO，未运行 Android 测试或任何 `connected*AndroidTest`。

## 2026-09-02：Desktop 五项纯后台复核通过；原生实看仍严格待用户确认（当前最高优先级）

- **范围与唯一基准：** 本轮只核对当前 checkout、现行 Android 会话／设置／运行时合同、Desktop 源码、无窗口测试、静态产物和默认 macOS bundle；`1440×900` 仍是唯一产品视觉基准，`1000/700` 只作为同一 IA 的内部防溢出尺寸。没有启动、激活、切换、置前、重启或弹出任何 GUI，也没有操作用户进程 PID `2210`。
- **五项静态合同：** 主题预览继续由唯一 `--settings-dot` owner 输出，完整 CSS 中只有这一处预览类，使用 `background-color` 与禁用 `background-image`，不存在会覆盖它的第二条预览规则；空内容顶栏显示“对话／工作 + 临时聊天”，有内容顶栏切换为同一 `44px` 胶囊内的“新对话 → 更多”，更多菜单继续复用现有生命周期与分享 owner；侧栏标题长按状态机只接收主指针／鼠标左键，阈值后只打开一次，位移、滚动、`pointercancel`、提前抬起与新按压替换都会清理计时器，右键继续进入同一菜单；侧栏 footer 与 Composer dock 都是透明 absolute overlay，只由按钮／Composer 自身绘制白色前景，会话列表与正文分别使用随滚动的 `68px`、`190px` 末端 inset；设置宽屏 CSS 不含 `1180/820` 密度降级、`48px` 行高或 `mobile-home/detail` 第二套 IA。
- **自动验证：** 四项相关定向 Node 测试 `97/97`；完整 Node `168/168`；Rust `181/181`；无窗口 Chrome computed-style 七色依次回读 `rgb(233,113,40)`、`rgb(53,122,232)`、`rgb(37,42,39)`、`rgb(40,112,82)`、`rgb(230,179,53)`、`rgb(226,96,145)`、`rgb(140,99,217)`；lint、typecheck、inventory、protocol golden、static build、`cargo check` 全通过。inventory 当前为 222 个可见 action 全有 handler、127 个 invoke 全有已注册 Rust command；长按 fake clock 同一行连续 `10/10` 无陈旧计时器或导航穿透。
- **默认 bundle 后台核对：** `desktop/dist` 的 21 个启动资源与当前 `desktop/src` 逐文件一致；默认主程序仍为 `2026-09-02 19:26:51`、`31,010,112` bytes、SHA-256 `0ea792895c1733884b1f85002fe95a7b9e5302de437cac77613c083621f6d9a1`，打包时间晚于本轮四项相关源码。Bundle ID `com.nanzhufeng.ai.desktop`，版本 `0.6.0-p6d-dev`，arm64 ad-hoc strict codesign 通过，`TeamIdentifier=not set`；它不是 Developer ID／公证分发包。
- **明确未验收：** computed-style、DOM、事件状态机、测试、静态 build、Rust 回归、bundle 时序与签名都不能替代 macOS Tauri WebView 原生观感和鼠标实交互。本轮没有原生确认七色、两套顶栏、侧栏标题同一行长按、侧栏／Composer 后方内容透出、Provider 四段居中，也没有生成新的 `1440×900` 截图；这些项目继续标记为**待用户自行退出旧进程并打开新 bundle 后确认**。
- **外部与数据边界：** 未读取正式 Desktop 数据根，未调用 Provider／账号／通知，未操作 OPPO，未运行 Android 测试或任何 `connected*AndroidTest`。本轮没有业务代码改动，只有本节后台复核记录。

## 2026-09-02：Desktop 原生控件五项修正已打包；GUI 禁令下原生实看待用户确认（当前最高优先级）

- **主题色红灯与最终代码：** 用户先后在原生 Release 中确认七个圆点结构存在但全部为白色；因此浏览器 DOM 与上一轮 inline `background-color` 均不再作为原生通过证据。当前实现恢复唯一 `--settings-dot` token owner，圆点类只使用 `background-color: var(--settings-dot) !important` 与 `background-image: none !important`，删除冲突的 `background:` 简写。新增无窗口 computed-style 门，七色依次回读为 `rgb(233,113,40)`、`rgb(53,122,232)`、`rgb(37,42,39)`、`rgb(40,112,82)`、`rgb(230,179,53)`、`rgb(226,96,145)`、`rgb(140,99,217)`；这仍不替代 macOS Tauri WebView 实看。
- **顶栏真实状态分支：** 以 Android `hasConversationContent = state.messages.isNotEmpty()` 与 `ConversationShellHeader` 为当前源码事实。空内容态显示居中“对话／工作”和右侧临时聊天；有内容态隐藏两者，右侧改为同一 44px 胶囊中的“新对话 → 更多”。更多菜单复用现有置顶、未读、收藏、同步、查找、归档、删除与 Markdown 分享 owner；没有复制新的生命周期数据真值。
- **会话标题长按：** 新增独立 `conversation-long-press.mjs` 状态机，鼠标主键阈值只打开一次并消费随后 click；位移、滚动、`pointercancel`、提前 `pointerup` 均清计时器，右键继续走同一菜单。菜单中的置顶／归档仍调用现有 lifecycle owner。fake clock／事件序列已覆盖打开一次、替换计时器、导航抑制与四类取消路径。
- **底部托底与 Provider：** 侧栏 footer 和 Composer dock 均改为透明 absolute overlay，只由设置圆钮、新对话胶囊和 Composer 自身绘制白色前景；会话列表使用随滚动的 `68px` 末端 inset，正文使用 `190px` 末端 inset，使内容可延伸到控件后方且最后内容仍可完整越过。Provider 四段继续保持等分、水平／垂直居中与 `44px` 高度。
- **后台门禁：** Node `168/168`（其中同一行连续模拟鼠标长按 `10/10`）；主题 computed-style 七色通过；lint、typecheck、inventory、protocol golden、static build、`cargo check` 全通过。inventory 为 222 个可见 action 全有 handler，127 个 invoke 全有已注册 Rust command。Rust owner 未修改，因此未重复运行 Rust 单测；没有运行 Android 测试或任何 `connected*AndroidTest`。
- **最新默认 Release 与独立 QA 包：** 默认软件已经在不启动任何窗口的前提下重新打包到 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`；主程序时间 `2026-09-02 19:26:51`、`31,010,112` bytes、SHA-256 `0ea792895c1733884b1f85002fe95a7b9e5302de437cac77613c083621f6d9a1`，ad-hoc strict codesign 通过。用户当前 PID `2210` 在重打包前已经启动，仍使用进程内旧资源；遵守 GUI 禁令，不终止、不置前、不自动重启，须由用户自行退出后再打开才会载入新包。隔离 QA 包仍为 `/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a060eb-322b-7dd2-b8da-1664f33e02de/nanfeng-ai-native-control-fixes-20260902/南枫 AI Desktop QA 1903.app`；Bundle ID `com.nanzhufeng.ai.desktop.nativecontrolfixes.1903`，包内 wrapper 固定注入隔离根 `/tmp/nanfeng-ai-native-control-acceptance-1903` 与 `--diagnostic-ui-schema-acceptance`。其 Rust 主程序 SHA-256 `2a482b77000d8bc2f575068fd0b32e3affb234e09e070537611c353d82a49274`，wrapper SHA-256 `c80b46170ef16baea877d10ed355eecd649ff55c14e5f3ea49b0764b1aa9edfd`，可交付 ZIP SHA-256 `f3c2df7801c81f2f8253c794757c46deb03d348f8a4d4dc7fc0d4392ec9e5a1f`。
- **明确未验收：** 用户要求从此不自动打开、激活、切换、置前或弹出任何 GUI。故本轮没有启动 QA 包、没有原生 1440×900 截图、没有执行侧栏同一行长按 10/10，也没有原生确认七色、两种顶栏、底部透出与 Provider 居中。用户当前正常进程 PID `2210`（从仓库默认 Release 路径启动）未终止、未切换、未覆盖。上述原生五项均标记为**待用户自行打开该独立 QA 包确认**；后台打包和 computed-style 不得冒充原生验收。
- **边界：** 未读取正式 Desktop 数据根，未调用 Provider／账号／通知，未操作 OPPO。交接前 `context_gate` 仍为 `HANDOFF / 94.5%`，后续只应接收用户对独立 QA 包的实看反馈，不再扩张本线程范围。

## 2026-09-02：Desktop 只保留 1440×900 最新宽屏设计；紧凑／窄屏产品形态撤销（当前最高优先级）

- **唯一设计：** Desktop 当前唯一产品界面是最新 `1440×900` 宽屏版。`1000×800` 与 `700×900` 只用于内部防溢出／最小可用验收，不是产品变体、紧凑版、窄屏版或第二套 IA，也不进入对外截图。
- **代码纠正：** 删除设置页 `宽 ≤ 1180px`／`高 ≤ 820px` 时降为 `48px` 行高的密度分支；删除 `settingsMobileHome`、`mobile-home/detail` 与 `show-settings-home` 第二套设置首页／详情状态。所有窗口继续渲染同一双栏设置层级，一级行保持 `68px`；小窗口只允许同一表面内部滚动。会话侧栏的离屏抽屉仅为防溢出 fallback，代码命名也已从 compact 改为 fallback。
- **Android 深层对齐：** 设置仍按 Android 当前源码的四组 IA；“模型设置”保留独立 Key 强调卡，“费用与用量／上下文记录／运行诊断”归入“调用记录”。Desktop 同时保留宽屏 `320–420px` 一级栏、`880px` 详情上限和主界面大 Composer，不复制手机像素宽度。
- **验收事实：** `1440×900` 已逐页实看主界面、设置四组、模型与联网、模型设置、提醒、对话管理、导入导出、本机数据、项目与知识、开发与诊断、搜索、定时任务和南枫转写；浏览器 console warning／error 为 0。内部 `1000×800`、`700×900` 计算样式均保留双栏和 `68px` 设置行，页面级横向溢出为 0。
- **当前门禁：** Desktop Node `161/161`、Rust `181/181`、Android 设置／模型专项 `24/24`，lint、typecheck、inventory、protocol golden、static build 与 `cargo check` 全部通过。当前 inventory 为 223 个可见 action 全有 handler，127 个 invoke 均有已注册 Rust command。
- **证据边界：** 对外只保留 13 张 `1440×900` PNG 与 `contact-sheet-wide-only.jpg`，目录为 `/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a060eb-322b-7dd2-b8da-1664f33e02de/nanfeng-ai-wide-only-20260902/`；联系表 SHA-256 为 `5af2c675c75be5626065aca026272612a73a9c66efc6be839884951dd46b1de0`。未触碰正式 Desktop 根／进程、Provider／账号／通知、OPPO，也未运行任何 `connected*AndroidTest`。

## 2026-09-02：Android → Desktop 最终完成度审计收口；总控方案现行门提升（当前最高优先级）

- **当前结论：** 本地、可回放、无外部费用的 Android → Desktop 范围可以收尾。新生成清单覆盖 Android 11 个根路由、22 个设置 destination；撤销第二套设置 action 后，Desktop 当前 223 个可见 action 全有 handler，127 个 invoke 全有 Rust command 且已注册，断链为 0。唯一总结文档见 [Android → Desktop 最终完成度审计](../ANDROID_DESKTOP_FINAL_COMPLETION_AUDIT_20260902.md)。
- **本轮修正：** 新增可执行 inventory 门禁与 2 个测试；删除不可达的旧 `settingsCenterCanvas`／`SETTINGS_REGISTRY`，并把 typecheck／P10-A 测试改为锁定当前模型与 `show-settings` 入口。Android 设置合同补入源码已可达的“Google 账号与同步”并有单测锁定。
- **自动门禁：** Android `1083` 个测试最终复跑 `0` failures／`0` errors／`3` skipped；Desktop Node `161/161`、Rust `181/181`，lint、typecheck、protocol golden、static build、`cargo check`、macOS bundle 与 strict codesign 均通过。
- **最新产物：** 主程序 `31,010,112` bytes，SHA-256 `bd12323c4cc657fa1431fd0abfba7e1084ad224dc8ec2e8e1aac7e44010c1582`；strict codesign 通过，ad-hoc，`TeamIdentifier=not set`。
- **隔离验收：** 唯一 Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.76356.mtjpxht5`，根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.FPGPqz`，schema 37，SQLite `quick_check=ok`，84 张表，子进程／TCP 均 0；验收后精确结束 PID 76462。新的 9 张 Web 实交互、1 张原生截图和联系表位于 `/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a060b7-dbb8-7b13-85e5-35418c0dceaa/nanfeng-ai-final-completion-audit-20260902/`。
- **外部边界：** 真实 Provider／网搜／OCR，Google／南枫云账号，macOS 通知，Developer ID／公证待单独授权；最小授权、可能成本、副作用和回滚已写入最终审计。本轮未触碰正式 Desktop 根／进程，未操作 OPPO，未运行任何 `connected*AndroidTest`。

## 2026-09-02：Desktop 10 条功能链完成度审计收口；相机与两项 UI 合同已闭环（当前最高优先级）

- **当前结论：** 模型选择、Composer、Desktop 大输入布局、提醒／监控、GLM-OCR 转写、标题选择／水位、全局搜索附件、ZIP 身份／墓碑、实时网页搜索设置、历史资料库调度均有 Desktop 本地 owner 和证据。唯一新发现的本地缺口是 Composer 缺“相机”，现已实现 `getUserMedia`、PNG 验证、普通／临时私有附件 owner 和取消不改草稿。详见 [Desktop 10 条功能链当前完成度审计](../DESKTOP_10_FUNCTION_CHAIN_COMPLETION_AUDIT_20260902.md)。
- **合同纠正：** 南枫转写按 Android 当前源码固定官方 GLM-OCR，不加入聊天模型选择；历史 Compare 仅保留可读／可停兼容，不恢复 Composer 入口或重试。
- **历史规则已撤销：** 本节当时引入的 `宽 ≤ 1180 CSS px`／`高 ≤ 820 CSS px` 紧凑设置与 `48px` 行高已由本文顶部最新交接撤销；当前只保留 `1440×900` 宽屏设计，较小尺寸仅作同一设计的内部防溢出门禁。
- **模型层级：** 跟随 Android `ModelSettingsPrimaryEntry`／`ModelSettingsRecordsCard`，“模型设置”已恢复为独立 Key 图标强调卡；“费用与用量／上下文记录／运行诊断”置于独立“调用记录”次级组。Browser 点击主卡已真实进入 Provider／API Key／测试连接页。
- **门禁：** Node `159/159`、Rust `181/181`、lint、typecheck、protocol golden、static build、`cargo check`、macOS bundle 与 strict codesign 通过。当前主程序 `31,010,112` bytes，SHA-256 `076d6f73c2eddab70bfb9369db9ebde638619313db00da877704c4f6bfcd9945`，ad-hoc，`TeamIdentifier=not set`。
- **隔离证据：** 相机 QA 只在 Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.70684.mtjnz95e`、根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.rdyF74` 打开；临时聊天相机弹窗未拍摄即取消，SQLite `quick_check=ok`、schema 37、附件资产／普通引用／临时引用均为 0。响应式截图位于 `/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a06048-b547-73d2-8863-9fbf7aea5ef5/desktop-ten-chain-audit-20260902/`。
- **边界：** 真实 Provider 回答、Google／南枫云账号、macOS 通知授权／点击仍是外部验收。本轮未读写正式 Desktop 根，未拍摄或保存用户镜头画面，未操作 OPPO，未运行任何 `connected*AndroidTest`。

## 2026-09-02：正式 schema 37 本地验收成立；20 条归档阻断已撤销

- **用户补充事实与当前裁决：** 用户确认 `11:09` 后确实操作过 Desktop，并明确“不用刻意恢复”。因此撤销“20 个会话被意外归档／需要恢复”的裁决；保留当前正式库，不执行整库恢复、反归档或删除。正式 schema 37 的本地数据库健康与页面可读验收成立；Provider、账号、通知等外部边界仍不在本轮完成范围。
- **20 条归档审计：** 20 条均为不同会话的 `conversation/archive`，实际前端时间为北京时间 `11:26:40.590–11:26:57.132`；expected revision 与操作前 revision 全部匹配，结果只增加 1，唯一会话字段变化是 `archived: false → true` 与 revision。标题、消息、叶节点、项目、置顶和创建时间逻辑哈希不变。目标按当时持久排序覆盖连续第 `4–23` 行，并从下到上处理，与用户确认的前台会话整理一致。完整脱敏清单见 [Desktop 正式库 20 条会话归档脱敏审计](../DESKTOP_FORMAL_ARCHIVE_INTENT_AUDIT_20260902.md)。
- **来源边界：** 数据库没有操作者列，只能证明 intent 前缀为 `conversation-lifecycle`。当前代码只从会话行归档按钮或右键归档菜单进入该 mutation，startup／background 不会调用；同一时段 session 日志没有成功的 Computer Use 归档动作。故结论是“与用户确认的正常操作一致且无反证”，不把具体点击主体伪装成数据库可证明事实。
- **正式库健康快照：** `quick_check=ok`、`integrity_check=ok`、schema 37、外键检查无结果；普通聊天 Attempt、历史候选、历史 checkpoint、提醒运行和转写任务均为 0，到期提醒为 0，history schedule 仍为 `paused=0 / last_dispatched=NULL`。84 张共同表中 74 张逐行逻辑哈希相同；其余变化可由归档的 exchange／索引／provenance、正常已读水位、附件引用时间、background 更新时间和一次 `tone_override=direct` 用户设置解释，没有独立异常业务链。
- **崩溃根因与代码修复仍成立：** 11:00–11:07 的 `.ips` 属于旧隔离 QA Bundle；它们的 LaunchAgent 在无 mock endpoint 时遗漏隔离 `app_root`，旧二进制回落到正式高版本 schema 后以 `desktop SQLite unavailable` 在 setup hook 中 `SIGABRT`。background plist 现始终写入精确 `NANFENG_AI_DESKTOP_BACKGROUND_ROOT_V1`，background cycle 只在 background mode 消费该根。旧 QA plist 仍保留，未经删除授权不处理。
- **红绿与门禁：** `launch_agent_without_mock_endpoint_keeps_the_exact_workspace_root` 修复前因缺少 root env 失败，修复后通过。完整门禁为 Node `151/151`、Rust `180/180`、lint、typecheck、protocol golden、static build、`cargo check`、macOS bundle 与 strict codesign 全通过；本次复核再跑 background module `3/3`、会话 archive/reopen `1/1` 与 `cargo check` 均通过。主程序 `30,942,016` bytes、SHA-256 `8683046f14df8924c90a76ee9a167564703906998a4c02b655e08bef85c408c9`，仍为 ad-hoc、`TeamIdentifier=not set`。
- **正式 UI 与当前动态状态：** 诊断 PID `55680` 在正式根打开主界面、搜索、提醒、转写、四组设置及深层页，无子进程、无 TCP、无新崩溃报告；14 张截图与联系表位于既有正式验收证据目录。用户随后恢复正常使用，现场 PID `58143` 于 `11:39:06` 由 LaunchServices 启动；本轮没有启动或终止它。正式 background job 当前不在运行，已加载 plist 的精确 root env 指向正式 p6b workspace，最近 exit code 0。
- **备份只作证据：** 事前 online backup `/tmp/nanfeng-ai-production-schema37-evidence-20260902.GXoNMe/workspace.sqlite3` 仍为 `63,787,008` bytes、SHA-256 `7855a9dc9b099d4056d24e2c648001a23c825f759689665115de1e3e44a1324a`、`quick_check=ok`、schema 37；用户明确不要求恢复，本轮也未恢复、反归档或删除任何数据。未触碰 Provider、账号、通知、OPPO 或任何 `connected*AndroidTest`。

## 2026-09-02：24 张 Desktop 问题截图已按共享 owner 收口

- **结果：** 18 张首轮问题图与 6 张补充间距图不再逐页打补丁，统一归并到设置密度与 Assistant Markdown 两个 owner。设置保留 Android 当前四组 IA 与 Desktop 宽屏双栏；Composer 尺寸和会话功能布局未改。
- **设置／语气：** 一级栏 320–420px，设置主行浏览器实测 68px，右侧详情上限 880px、页面间距 20px；五风格弹窗 600×620、恰好 5 项、卡片最小 94px。风格说明明确只改变表达，不改变模型、联网、记忆或资料库功能。
- **外观三值右对齐：** “系统（默认）／标准／橙色”共用最小 108px 右值列；主题色色点和文字属于同一值组。`1440×900` 是唯一产品设计；较小窗口的数据只作内部防溢出证明，不定义第二套视觉。
- **Markdown：** Assistant 正文 15px／1.72、阅读宽度 880px；表格单元格最小 140px，只在表格容器横向滚动。`700×900` 仅验证同一布局可用且无页面级横向溢出，不是窄屏产品方案。
- **门禁与产物：** Node `155/155`、lint、typecheck、protocol golden、static build、macOS bundle 与 strict codesign 通过。主程序 `30,942,016` bytes，SHA-256 `074ba61f27f20072d7765b1971314d41704a263cbb203aec29ba0c272bc254d3`，仍为 ad-hoc、`TeamIdentifier=not set`。逐图矩阵见 [Desktop 24 张问题截图对齐矩阵](../DESKTOP_24_SCREENSHOT_PARITY_MATRIX_20260902.md)。
- **隔离原生与边界：** 唯一 Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.63406.mtjle8e1`、根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.mJpHnp`、diagnostic mode；原生 `tauri://localhost` 设置与五风格弹窗独立实看，SQLite `quick_check=ok`、schema 37，PID 63732 无子进程、无已建立 TCP，验收后只结束该 PID。11 张浏览器 PNG、2 张原生 PNG 与联系表位于 `/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a06048-b547-73d2-8863-9fbf7aea5ef5/desktop-deep-parity-after/`；联系表 SHA-256 `c1a58ed5db106a394413d119caff26a45dfe096cc4ceafbb8f6110081be77cec`。正式 PID 58143 未停止／重启，正式库未读写；未调用 Provider／账号／通知，未操作 OPPO，未运行 `connected*AndroidTest`。

## 2026-09-02：用户已授权关闭旧进程并安全迁移正式库；由新线程执行

- **明确授权：** 用户原文为“允许关闭旧进程并安全迁移正式库”。授权仅覆盖优雅关闭既有正式进程、备份正式 SQLite、使用 `--diagnostic-ui-schema-acceptance` 启动当前最新 bundle、执行 schema 36→37 迁移和正式 UI／数据读回；不扩大到真实 Provider、账号同步、通知投递、恢复备份、删除数据或其他外部动作。
- **迁移前只读事实：** 正式进程 PID `97950`，启动时间 `2026-09-01 23:58:15`，路径为当前项目 bundle；正式库 `/Users/nanzhufeng/Library/Application Support/com.nanzhufeng.ai.desktop/p6b-workspace/workspace.sqlite3` 的 `quick_check=ok`、`user_version=36`，历史资料调度 `paused=0`、`last_dispatched_at_ms=NULL`，当前到期提醒数 `0`。用户授权后尚未关闭进程、备份或写入正式库。
- **当前产物：** `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，主程序 `30,941,152` bytes，SHA-256 `427500aa45679918992a67ba26551e2308304c82f8fee3696e406bcc9b5c9891`，strict codesign 通过、ad-hoc、`TeamIdentifier=not set`。Node `151/151`、Rust `179/179` 及 lint／typecheck／protocol golden／static build／`cargo check` 已通过。
- **安全启动门：** `--diagnostic-ui-schema-acceptance` 仅作用于当前进程，保留 UI、SQLite migration 与只读页面投影；Rust owner 会拒绝 Provider／账号凭据读取、网络任务、通知插件／发送、LaunchAgent、历史整理、提醒派发、周期同步和后台子进程。普通无参数启动行为不变。
- **下一线程固定顺序：** 先用 SQLite online backup 创建带时间戳的 `/tmp` 正式库备份并记录 SHA-256、大小、`quick_check`、schema、关键业务表计数与逻辑指纹；再优雅退出精确 PID `97950` 并确认锁释放。随后用上述显式参数启动当前 bundle，核对正式库 schema 37、`quick_check`、关键数据计数／指纹、调度字段不变、无子进程／TCP／通知，并逐页实看正式数据 UI。诊断完成后精确停止本次进程；不得自动无参数重启。
- **失败与回滚边界：** 任一步异常立即停止并保留备份、日志和当前正式库；未经新的用户指令不得执行整库恢复、删除或重试破坏性动作。
- **线程门禁：** 当前根线程 `context_gate` 为 `HANDOFF`（context 94.7%、effective tokens 2,001,698）；因此本节写入后必须新建续作任务，不在本线程开始正式迁移。

## 2026-09-02：Desktop 逐控件图标／卡片弱证据已闭环

- **结果：** 以 Android 当前 17 图目录、最新双端五风格截图、Android live source／三份 CURRENT 合同和 Desktop shared source 为准，完成主界面、模型、Composer `+`、搜索、提醒、转写、设置四组、个性化五风格、模型联网和 ZIP 导入的逐控件映射。外观、导入与导出、开发与诊断、实时网页搜索四处真实语义偏差已修复；Desktop 继续保留宽屏双栏、大 Composer 和平台原生密度。
- **合同校正：** `ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md` 原有“六项／默认可见”两行与当前 runtime 合同、会话合同、live source 和双端最新截图冲突，现已改为五项可见、`default` 仅内部回退。没有反向把当前 UI 改回旧合同。
- **代码与测试：** `icon-source.mjs` 新增 vendored Lucide `sun-moon`、`arrow-right-left`、`sliders-horizontal`；设置和模型页改用精确映射。新专项先红后绿 `3/3`；最终 Node `151/151`、Rust `179/179`、lint、typecheck、protocol golden、static build、`cargo check`、bundle 与 strict codesign 全部通过。
- **渲染与原生：** 应用内浏览器目标流 `主页 → 设置 → 四组 → 模型与联网` 的 URL／title、非空 DOM、console 和交互通过；改后四张独立 PNG 与预览板位于 `~/.codex/visualizations/2026/09/01/01a05db4-50d5-7131-ae92-29041ef03ac9/icon-control-audit-20260902/`。隔离原生 Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.25946.mtj1d970`、根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.njB4u2`，`quick_check=ok`、schema 37、无子进程／网络 socket；只结束验收 PID 25996，既有 PID 97950 始终保留。
- **产物与边界：** 主程序 `30,941,152` bytes，SHA-256 `427500aa45679918992a67ba26551e2308304c82f8fee3696e406bcc9b5c9891`；仍为 ad-hoc、`TeamIdentifier=not set`。完整表见 [Desktop 逐控件图标／卡片审计](../DESKTOP_ICON_CONTROL_CARD_AUDIT_20260902.md)。未读正式根、未调用 Provider／账号／通知、未操作 OPPO、未运行 `connected*AndroidTest`。

## 2026-09-02：Desktop UI/schema 正式根诊断启动门已完成

- **结果：** 新增唯一显式 CLI 参数 `--diagnostic-ui-schema-acceptance`，内部映射为强类型 `UiSchemaDiagnostic`。它只属于本次进程，不落入设置、schedule、checkpoint 或业务表；无参数启动仍执行原有恢复、background runtime reconcile、历史资料与提醒唤醒，和诊断参数同时传入后台 cycle 参数会直接拒绝。
- **诊断边界：** 模式下仍打开正常 UI、运行 SQLite／P7E／P8 migration 并提供页面只读投影，但跳过本地备份恢复／finalize、Interrupted 业务恢复、附件启动维护、LaunchAgent reconcile、history curation、reminder dispatch、周期账号同步、通知插件／桥与所有自动线程。Provider／账号设置页使用不会触碰系统 credential store 的缺失投影；聊天、Compare、OCR／转写、历史整理、提醒生成／运行、通知授权／发送、账号／云同步和系统附件打开等外部入口由 Rust 二次拒绝。
- **无写入回归：** 行为测试覆盖默认正常、显式抑制、诊断＋后台参数冲突、诊断后下一次无参数恢复 Normal、历史 `last_dispatched_at`／schedule 更新时间与 background runtime 投影重开前后不变，以及会话已读只读投影不 seed／prune。前端先读取 runtime mode，诊断启动不迁移旧设置、不推进已读水位、不运行 due history，也不读取／安装／flush 通知。
- **隔离原生验收：** 新 bundle 只在 `/tmp/nanfeng-ai-p6-v2-picker-acceptance.ui-schema-final.9Ynaex/app-home` 启动诊断模式；`quick_check=ok`、schema 37、settings revision 1、history `paused=1 / last_dispatched=NULL / updated=0`、background `0/0/NOT_CONFIGURED/0`、read marker／reminder plan／reminder run 均为 0。精确 PID 22471 无子进程、`lsof -i` 无 socket；验收后只结束 22471。既有正式根 PID 97950 全程保持运行且未操作，正式数据根没有启动或读取。
- **门禁与产物：** lint、typecheck、protocol golden、Node `148/148`、static build、Rust `179/179`、`cargo check`、macOS bundle 与 strict codesign 全部通过。当前主程序 `30,941,152` bytes，SHA-256 `bddbae703ca2fcf35787dacaa5685f0dd8335c8ab030a55138c758141f6c0b1c`；签名仍为 ad-hoc、`TeamIdentifier=not set`。
- **视觉与外部边界：** 隔离空根真实窗口显示正常主 UI。截图中的通知中心横幅与 macOS 后台活动提示不能归因到诊断进程（同 bundle 的既有 PID 97950 始终存在），故不把横幅当作本轮通知证据；诊断代码已进一步做到不初始化 notification plugin。未调用 Provider／账号／云端／通知动作，未操作 OPPO，也未运行任何 `connected*AndroidTest`。

## 2026-09-02：最后两个无需外部服务的弱项已闭环

- **结果：** GLM-OCR 的隔离验收命令现在直接锁死空凭据，不读取正常 macOS credential store；缺凭据执行会先持久化首次 Attempt，再以 `ZHIPU_API_KEY_MISSING` fail-closed。前端错误文案按图片／PDF 与语音任务分流，32 MiB 响应上限的技术明细也与真实门禁一致。
- **真实 PNG／PDF 原生链：** 唯一 QA App 为 `/tmp/nanfeng-ai-desktop-compare-bundle.WooC0G/南枫 AI Compare 验收.app`，Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.17547.mtiz8pxg`，全新隔离根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.Nds2ZC`。26,672-byte PNG 与 18,107-byte PDF 均经 macOS 原生 picker 创建任务；两者最终均为 `FAILED / ZHIPU_API_KEY_MISSING / Attempt 1 / provider_request_count 0`，取消／重试入口可操作，关闭进程并以同根重启后两任务与错误结构恢复。私有副本 SHA-256 与源文件一致，验收进程无已建立 TCP。
- **确定性大历史：** `/tmp/nanfeng-ai-desktop-large-history.UMKyrp/nanfeng-ai-large-history.nfai-exchange` 为纯合成文本包，package SHA-256 `316f6c7f0ebacdc450f5b0339080b29a858181bb023b73ad9bbd3ffb4f6ba1db`，semantic hash `fe5b591513d9db3d29eff0f95161c2c69afa56bc6515175ee4de0f58688578b0`。经“导入为独立工作区”原生 picker 后 SQLite 回读 800 会话、10,188 消息、单会话最多 600 消息、10,988 搜索索引行。抽屉标题、全局唯一目标、0617 精确命中、长会话 0250 中段查找／关闭、跳到 0599 最新消息均实看；`Large-history-draft-preserved` 在滚动、跳最新、搜索往返与中段精确返回后均未丢失。
- **响应观测：** 原生导入 2.345s、工作区打开 1.762s、600 消息会话打开 2.774s、跳最新 2.179s、精确查询 2.019s、结果打开 1.939s、返回搜索 1.519s；各动作均完成，无崩溃或可见卡死。这些数值包含 Computer Use 动作与截图等待，只作为本机隔离验收观测，不是跨设备性能基准。
- **门禁与产物：** lint、typecheck、protocol golden、Node `147/147`、static build、Rust `176/176`、`cargo check`、macOS bundle 与 strict codesign 全部通过。最终主程序 `30,864,416` bytes，SHA-256 `7ad08a763fd9c90c6c09105139fb17cf24f7708b113c991db3d15c06e15052a6`；仍为 ad-hoc、`TeamIdentifier=not set`。
- **证据与边界：** 7 张独立 PNG 和最终联系表位于 `~/.codex/visualizations/2026/09/01/01a05db4-50d5-7131-ae92-29041ef03ac9/desktop-local-final-gaps-20260902/`。`contact-sheet.jpg` SHA-256 为 `e52cbea5106a248e8c29339ba7f4f59f65b4a1450367c86f03062d65648c5970`，是预览对比板，并非 App 同一画面。未触碰 Provider、正常凭据、账号、通知、正式 Desktop 根、OPPO 或任何 `connected*AndroidTest`。

## 2026-09-02：真实本地多格式深验收完成；四个红灯已修复

- **结果：** 17 项矩阵补入真实本地多格式证据。设置的“导入为独立工作区”与“私有归档 v2 交换包”重新成为两个明确入口；v1 工作区导入现在事务化登记附件 metadata/asset owner；全文索引纳入 Markdown／Code；DOCX 系统打开先做 owner、hash、字节和 Office ZIP 结构复核，再创建 app-private、带 `.docx` 扩展名的内容寻址引用。
- **真实夹具与原生回读：** `/tmp/nanfeng-ai-desktop-local-qa-fixture.20260902` 含 Markdown、PNG、PDF、MP4、DOCX、v1 与 v2 包。唯一 QA 副本为 `/tmp/nanfeng-ai-desktop-compare-bundle.vl7r2i/南枫 AI Compare 验收.app`，Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.13889.mtiyb0gu`，隔离根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.IFhQM5`。SQLite `quick_check=ok`、schema 37；五个真实附件与源文件 byte count／SHA-256 完全一致，搜索索引实际为 FILE 4／24,602 bytes、IMAGE 1／26,672、TEXT 3／2,878、TITLE 2／42、VIDEO 1／100,335。
- **原生行为：** 长 Markdown 的标题、列表、任务、引用、代码、表格、链接和 `LOCAL-QA-END` 完整渲染，script 标签保持惰性文本；Markdown／PNG／PDF／MP4 App 内预览成功；DOCX 由 TextEdit 读出精确夹具正文。搜索命中 `Markdown` 正文与附件，关闭 PDF 后查询和四个附件结果精确恢复。长对话跳到最新再返回时标题、位置与 `Draft-preserved-local` 草稿保留；字体切到 Large 后 SQLite `desktop_app_settings.font_size=large`，返回会话仍保持同一状态。
- **宽窄与 ZIP：** 1440×900 浏览器布局保留 256px 侧栏和 760px 大 Composer；700×900 下侧栏离屏、`打开导航` 可达、Composer 676px，均无横向溢出。原生大字体另行实看；原生自动缩窗不稳定，未冒充窄屏证据。Rust tempfile 集成链精确覆盖 ZIP 首次导入、重复包、累计追加、同 ID 冲突、批次删除墓碑和重导不复活；原生 UI 未点穿破坏性删除。
- **门禁与产物：** lint、typecheck、protocol golden、Node `147/147`、static build、Rust `174/174`、`cargo check`、macOS bundle 与 strict codesign 全部通过，0 failed、0 skipped／ignored。最终主程序 `30,864,368` bytes，SHA-256 `2821dafcff08a845763412bc08cb849c4f2802dd0164fb64640638a74d102b74`；仍为 ad-hoc、`TeamIdentifier=not set`。全仓 `cargo fmt --check` 仍暴露广泛既有格式漂移，本轮没有用批量格式化改写用户工作树。
- **证据：** 20 张独立 PNG 与最终 11 图对比板在 `~/.codex/visualizations/2026/09/01/01a05db4-50d5-7131-ae92-29041ef03ac9/desktop-local-deep-qa-20260902/`。对比板 `contact-sheet-preview-not-single-app-screen.jpg` 的 SHA-256 为 `99833fd701a0b776e25b0cf1cd538a62a559f3e01437fea68cd2a62b5bfc4d4e`，并明确标注“预览对比板，并非 App 同一画面”；修复前红灯截图独立保留但不进入最终板。
- **边界：** 没有调用 Provider、读取凭据或账号、访问 Google／Supabase、点击系统通知、启动正式 Desktop 数据根、操作 OPPO 或运行任何 `connected*AndroidTest`。完整生产根／真实外部服务仍是独立授权边界，不影响本轮本地 owner 与 UI 闭环。

## 2026-09-02：17 项第二轮审计闭环；本轮未对正式数据根冷启动

- **裁决修正：** 当前 Android `SettingsDestination` 与设置合同是四组：对话、外观、数据管理、工作区。上一节依据裁剪截图得出的“三组／移除工作区”结论已撤销；Desktop 已恢复 工作区，并在其中保留 项目与知识、开发与诊断。
- **本轮真实缺口与修复：** Composer 加号菜单此前缺少当前 Android 的会话级 `基础风格和语气` 与会话级实时网页搜索。现已补齐前端状态、Tauri ACL、Rust 持久 owner、schema 37 迁移，以及普通发送／Compare／重试路由。重试会重读当前风格与联网偏好，同时保留原 Provider、模型和 idempotency key，并刷新上下文来源审计。
- **五风格合同：** 可见项严格为 直言不讳、专业可靠、亲和友善、高效务实、风趣搞笑；`default` 只作内部中性回退。会话覆盖优先于全局设置，切换会话、重启和 revision 冲突均由当前实现／测试约束。
- **完整门禁：** lint、typecheck、protocol golden、Node `147/147`、static build、Rust `171/171`、`cargo check`、macOS bundle 与 strict codesign 全部通过，0 failed、0 skipped／ignored。首次全量 Rust 暴露 7 个回归（6 个 schema 36 旧断言、2 个新会话偏好读取边界），修正后全量重跑通过。
- **当前产物：** `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，主程序 `30,826,656` bytes，SHA-256 `bac2deb5e345285d3485a439b49795938783bebe77858cd0ce3c142a59ccd9aa`；strict codesign 通过，仍为 ad-hoc、`TeamIdentifier=not set`。
- **隔离原生验收：** 唯一副本 `/tmp/nanfeng-ai-desktop-compare-bundle.TxB0Kb/南枫 AI Compare 验收.app`，Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.7776.mtiwriyu`，数据根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.vAfRY7`。真实 `tauri://localhost` 窗口覆盖主会话、四组设置与个性化二级页、Composer 偏好弹窗、提醒／转写空态、搜索结果／空态、附件预览 fail-closed；关闭并重启后仍回读 `tone_override=efficient`、`web_search_override=1`。SQLite `quick_check=ok`、schema 37、提醒计划 0、转写任务 0。
- **证据：** 17 项矩阵见 [ANDROID_DESKTOP_DEEP_PARITY_MATRIX_20260901.md](../ANDROID_DESKTOP_DEEP_PARITY_MATRIX_20260901.md)；当前截图与联系表位于 `~/.codex/visualizations/2026/09/01/01a05db4-50d5-7131-ae92-29041ef03ac9/desktop-requirement-audit-20260902/`。
- **边界：** 本轮没有对正式数据根执行新的冷启动，因为当前正式根 `desktop_history_knowledge_schedule_v1.paused=0` 且 `last_dispatched_at` 为空，冷启动可能触发外部模型请求。审计收尾只读进程表时观察到一个 2026-09-01 23:58 已存在的正式 bundle 进程；它早于本线程，没有被本轮启动、操作或终止，也不能作为本轮新可执行文件的正式根验收。真实 Provider、Google／Supabase、Team ID 通知点击、OPPO 与 `connected*AndroidTest` 均未执行；这是一条需要用户授权的外部验收边界，不是尚未修复的本地 UI／owner 缺口。

## 2026-09-02：Desktop 当前 Android 深层同步完成隔离闭环；正式数据根复启仍受边界阻塞

> 历史记录：其中“三组设置／移除工作区”、schema 36、146/146、169/169 与旧 bundle 事实已由上方第二轮审计取代。

- **目标与结论：** 以当前 Android source、三份当前合同和手机多页参考为功能标准，重新审计 Desktop 一级入口、深层页面、失败恢复和持久 owner。新鲜代码／视觉／自动测试／bundle／隔离原生链已闭环；`design-qa.md` 不标 passed，因为本轮禁止真实 Provider／账号，而正式数据根启动会自动执行到期历史资料整理或提醒任务，新 release 因此没有在正式根复启。
- **本轮真实差异与修复：** 南枫转写根层收敛为 Android 当前 GLM-OCR 图片／PDF 单工具，移除新建音视频任务和语音设置入口；旧音视频任务只在“旧版音视频任务 · 仅保留已有记录”下保留可读兼容。Compare 从 webview ACL 与提交／重试 UI 链移除，只保留历史结果读取及已运行旧任务停止。模型设置不再投影 Qwen3-ASR。设置首页只保留对话、外观、数据管理三组；工作区／项目能力仍从 Desktop 工作台进入，不再改写 Android 设置 IA。
- **已核对的其余链路：** Desktop 保留宽屏侧栏、大 Composer、双栏设置和键鼠效率；会话选择与滚动恢复、搜索安全预览／精确返回、提醒、持久联网失败、历史资料整理、回答上下文、ZIP 身份去重／删除墓碑及设置深层页均有当前代码和既有行为测试支撑。本轮未凭空新增 Android 合同没有定义的“标题点击”交互。
- **自动门禁：** lint、typecheck、protocol golden、Node `146/146`、static build、Rust `169/169`、`cargo check`、macOS bundle 与 strict codesign 全部通过，0 failed、0 skipped／ignored。首次 Rust 全量运行暴露 5 个 schema 35→36 旧断言，修正测试期望后全量重跑通过，没有隐藏红灯。
- **当前产物：** `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，主程序 `30,827,824` bytes，SHA-256 `ddcc404bd86ffb23ca88cb4659adf9a63aeabf4437a2a36fec26fe6c7b2c52e7`；strict codesign 通过，仍为 ad-hoc、`TeamIdentifier=not set`。
- **隔离原生验收：** 唯一副本 `/tmp/nanfeng-ai-desktop-compare-bundle.PxY1SO/南枫 AI Compare 验收.app`，Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.3242.mtivop68`，严格数据根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.YnQ6ds`。真实 `tauri://localhost` 窗口中设置只见 Android 三组，转写只见 `图片 / PDF 转 Markdown`、Android 空态和 `选择图片或 PDF`；SQLite `quick_check=ok`、schema 36、目标列 2/2、转写任务 0，stdout/stderr 均为空。
- **证据与边界：** 新联系表位于 `~/.codex/visualizations/2026/09/01/01a05db4-50d5-7131-ae92-29041ef03ac9/desktop-android-parity-20260902/`。旧正式根 schema 36 读回仅证明上一版迁移历史，不证明本轮新可执行文件已在正式根启动。没有操作 OPPO、没有运行任何 `connected*AndroidTest`、没有真实 Provider／Google／Supabase／账单／通知点击。只有在获得允许真实启动或增加安全的“启动时禁止外部任务”验收门后，才能补正式根复启并把 `design-qa.md` 改为 passed。

## 2026-09-02：Desktop“打不开”真实修复完成；全盘 Android 对齐仍在进行

- **目标与结论：** 用户报告最新 Desktop 打不开。真实故障包含两层：macOS 在先前异常退出后反复显示窗口恢复／崩溃提示；正式 SQLite 虽已是 schema 35，但转写模块后来新增的 `provider_request_id`、`page_count` 没有新的前向迁移，初始化因此显示“无法读取语音转写任务”。两层均已修复，不能再沿用上方“最终通过”来结束全盘同步目标。
- **代码修复：** `desktop/src-tauri/src/lib.rs` 在 Tauri Builder 前关闭 AppKit 原生窗口状态恢复（应用自己的页面／领域状态仍由 SQLite owner 恢复）；运行根被已有实例持有时改为正常退出，不再让 setup `Err` 穿过 Tao Objective-C 回调形成 `SIGABRT`。schema 上限提升到 36，并新增 35→36 保数据迁移，幂等补齐两列；回归测试同时覆盖升级后再次冷开不会被误判为“未来版本”。
- **隔离原生验收：** 唯一 Bundle ID `com.nanzhufeng.ai.desktop.launchrecoveryfixed20260902000201`、根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.lHohTD`。首次启动后精确 `SIGKILL` 隔离进程，再用同 Bundle／同根冷启动，主窗口仍为“南枫 AI · 本地工作台”，两次 stdout/stderr 均为 0；NSUserDefaults 两个恢复门均回读 `1`，SQLite 为 schema 36、目标列 2/2。
- **正式数据保留验收：** 迁移前只读核对 `quick_check=ok`、schema 35、目标列 0/2，并备份到 `/tmp/nanfeng-ai-production-before-schema36-20260902.sqlite3`（SHA-256 `bf18d85ebc7e40da21fd8add9f696d39fb3acf27e3dfd41f4f91b1f1c9824256`）。最新包启动后正式根回读 `quick_check=ok`、schema 36、目标列 2/2、设置 revision 仍为 0；stderr/stdout 为空。主界面旧错误条已消失，“南枫转写”真实打开到图片／PDF与音频／视频入口。没有删除、清库、注入业务数据、调用 Provider 或操作 OPPO。
- **当前产物：** `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，主程序 `30,808,448` bytes，SHA-256 `68d77024cbc7ba7484207b5062944679f94dc6b58dad15b799d9350765d8fb1c`，strict codesign 通过，仍为 ad-hoc 开发签名。原生正式根截图为 `/tmp/nanfeng-ai-production-fixed-open-clean.png` 与 `/tmp/nanfeng-ai-production-fixed-transcription.png`。
- **未完成与下一步：** 总目标仍是以 `~/.codex/visualizations/2026/09/01/01a05b3f-bf39-7580-97c0-0c60854681f3/android-current-final-20260901/contact-sheet-primary.png` 和当前 Android source 为准，逐层复核 Desktop 一级入口、二／三级页面、弹窗、表单、结果、失败恢复和持久回读。下一线程先读取本节、项目 `AGENTS.md` 和三份当前 Android 合同，然后运行 `git diff --stat -- desktop/src-tauri/src/lib.rs docs/CURRENT_HANDOFF.md design-qa.md`，生成当前 Android／Desktop 多页联系表并固定剩余差异矩阵；不得把本次能启动冒充全量视觉同步完成。

## 2026-09-01：Android 当前五风格与深层链已同步到 Desktop

- **五风格与原生布局：** Desktop 可见选择只保留直言不讳、专业可靠、亲和友善、高效务实、风趣搞笑，完整复用 Android 当前说明与运行时指令；内部 `default` 只作旧值回退。固定尺寸 Desktop 弹窗内每项是独立灰底圆角卡，选中项为浅橙底、橙色边框／标题／勾号，正常字号内容自然增高并在弹窗内部滚动，不放大宽屏弹窗。
- **失败链：** 无服务商的普通发送现在先提交 User、Assistant 占位和 Attempt，再持久化 `FAILED / PROVIDER_NOT_ENABLED`；原位 `回答未完成` 卡提供中文行动建议和显式重试。全新无附件隔离会话的 SQLite 已读回三者绑定，强停并重开同一隔离根后失败卡仍在，不再用瞬时顶部提示或含缺失附件的旧会话冒充。
- **ZIP 与附件：** schema 35 增加 P6-K 官方身份账本；跨包相同导入去重、更新包仅追加新消息、同 ID 改内容 fail-closed，用户删除的对话／附件墓碑阻止后续 ZIP 复活。批次删除有明确不可复活确认；手动媒体关联命令已进入正式 Tauri capability。
- **搜索与预览：** 文本预览支持复制、经 Rust hash／字节复核后保存完整副本；Desktop 无 Web Share owner 时明确使用剪贴板交付。关闭预览恢复进入前的精确搜索偏移，不再把旧结果强制居中。
- **原生证据：** 最终五风格双端合图为 `~/.codex/visualizations/2026/09/01/01a05d54-b892-77d3-96f6-edfa5bb8c362/android-desktop-five-style-card-comparison-final-20260901.png`；第五项滚动可达截图为同目录 `desktop-native-five-style-cards-scrolled-final-20260901.png`；失败强停重开证据为 `desktop-native-no-provider-failed-after-restart-20260901.png`。隔离根为 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.Z9F04w`，未读取正式 Desktop 数据根。
- **最终门禁：** Node `145/145`、Rust `167/167`，0 failed、0 skipped／ignored；lint、typecheck、static build、macOS bundle 与严格 codesign 通过。主程序 `30,806,944` bytes，SHA-256 `0e1d6afada0b94f7a668e36d95224a7a8ddc49f7b0300695518d4dcf5df07983`，仍为 ad-hoc、`TeamIdentifier=not set`。全仓 fmt 与 `clippy -D warnings` 仍有无关既有债务，本轮未批量改写。
- **边界：** 未调用真实 Provider，未登录 Google／Supabase，未点击系统通知授权或通知，未操作 OPPO，未运行任何 `connected*AndroidTest`。本机 Android → Desktop 对齐通过；这些外部边界仍分层待验。

## 2026-09-01：最新 Android 五种对话风格与手机端截图（取代下方六项旧记录）

- **当前可见选择只有五项：**直言不讳、专业可靠、亲和友善、高效务实、风趣搞笑。“默认”只保留为旧值／未知值的内部中性回退，不再作为第六个可见选择；本文下方所有“六项／默认排第一”的旧记录均已失效。
- **直言不讳已按用户定义完整落地：**可见说明明确事实、证据和现实约束优先，发现错误、情绪化、过度自信或悲观时直接纠正；允许反驳和讨论不同观点，不因用户立场强烈而迎合或妥协，同时禁止羞辱和无依据武断。说明文字不再设置行数上限，卡片自然增高并由弹窗内部滚动承载，不得截断关键含义。运行时继续由同一 `ConversationStyleDefinition.instruction` 注入普通聊天共享请求边界，不是只改文案。
- **隔离模拟器实看：**Release 仅覆盖 `emulator-5588`。选择“直言不讳”后按个性化页右上角保存，强停冷启动仍回读“直言不讳”；重新打开弹窗只有五张灰底圆角卡，选中项为主题色浅底、边框与勾号，语义树完整读到“不因用户立场强烈而迎合或妥协，但不羞辱、不武断”。最新截图：`~/.codex/visualizations/2026/09/01/01a05b3f-bf39-7580-97c0-0c60854681f3/android-current-five-style/android-five-style-final-unclipped-20260901.png`。
- **联网失败可见性补修：**无可用服务商、无官方联网路由或服务商未返回可验证来源时，后台仍 fail-closed，不写入空白完整回答；失败的 Assistant 占位现从持久 runtime 安全错误码生成原位“回答未完成”卡。该卡不再依赖瞬时广播或 Composer 上方提示，重载、键盘状态和冷启动后仍可见。隔离模拟器无凭据实测显示“本次实际接收服务商未启用”，没有生成离线伪答案；截图为 `~/.codex/visualizations/2026/09/01/01a05b3f-bf39-7580-97c0-0c60854681f3/android-current-web-search/web-search-failure-inline-fixed-20260901.png`。
- **ZIP 规则可见性补修：**ZIP 结果页即使尚无批次，也明确说明按官方身份去重，并永久保留用户主动删除的对话／附件删除标记；删除批次确认明确同一或更新 ZIP 以后也不会复活这些内容。空状态截图为 `~/.codex/visualizations/2026/09/01/01a05b3f-bf39-7580-97c0-0c60854681f3/android-current-zip-import/zip-permanent-dedupe-empty-state-20260901.png`。
- **搜索文件真实链路：**在隔离模拟器从系统文件选择器导入一个 Markdown，用纯附件消息落库后进入搜索。“全部”下真实显示文件名、`MD` 类型预览图、提取正文摘要、`4.5 KB` 真实大小与时间；点击进入“本地安全文本预览”，可下载／分享／复制全文，关闭后返回原搜索结果与位置。截图为 `~/.codex/visualizations/2026/09/01/01a05b3f-bf39-7580-97c0-0c60854681f3/android-current-final-20260901/12-search-file-result.png` 与 `13-search-file-preview.png`。
- **当前手机端主参考：**最新 Release 从一级到表单／结果深层的快照集合为 `~/.codex/visualizations/2026/09/01/01a05b3f-bf39-7580-97c0-0c60854681f3/android-current-final-20260901/`，主联系表为其下 `contact-sheet-primary.png`。当前手机端“南枫转写”的可见真相是 GLM-OCR 图片／PDF 选择页，不得继续沿用下方旧 Desktop 记录中的 Qwen 音视频页来反向定义 Android。
- **当前验证：**风格／请求／Composer／设置专项 `35/35`，联网、历史资料整理与 ZIP 永久身份账本专项 `55/55`，新增失败投影与 ZIP UI／Room 专项 `32/32`，均为 0 failed、0 errors、0 skipped；随后强制完整 JVM `1083` tests、0 failures、0 errors、3 个缺少显式真实 ZIP 路径的 opt-in skipped。`lintVitalRelease` 与 `assembleRelease` 通过。当前 APK SHA-256 `229bb767c70d7973d68efb2a3f1086a89bd0aaa110077ec69aa0189c45ca7ec2`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，v2／v3 验签通过。
- **边界：**本轮没有操作 OPPO，没有运行任何 `connected*AndroidTest`，没有真实 Provider 计费请求。Android 当前参考与搜索文件预览已重新完成；Desktop 尚未按这套五项可见选择和更新后的 Android 运行／导入链重新验收，因此 `design-qa.md` 继续保持 blocked，不能沿用下方旧的 Desktop “最终闭环”结论。

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
- **最新产物：** [南枫 AI Desktop.app](../../desktop/src-tauri/target/release/bundle/macos/%E5%8D%97%E6%9E%AB%20AI%20Desktop.app)，版本 `0.6.0-p6d-dev`，主程序 `30,702,352` bytes，SHA-256 `d0934b1a6830044cc0f4bee03a5c98ef040665025a1f4997875497ca383ef9f2`。当前为 ad-hoc、`TeamIdentifier=not set`，不是 Developer ID／公证分发包。
- **完整证据：** [深层差异矩阵](../ANDROID_DESKTOP_DEEP_PARITY_MATRIX_20260901.md) 与 [原生验收记录](../ANDROID_DESKTOP_DEEP_NATIVE_ACCEPTANCE_20260901.md)。未操作 OPPO，未运行任何 `connected*AndroidTest`。


## 历史归档

本文件只保留当前任务与最近有效证据。完整历史已原样保存在 [历史交接归档](../archive/CURRENT_HANDOFF_HISTORY_THROUGH_20260901.md)；仅在追溯旧决策或旧证据时按关键词读取，不作为当前状态全文加载。

归档 SHA-256：`6127808dde4af9d6ebad9cf9364995a363215ffc59b5a3314402619b18da1c39`
