# Android → Desktop 全状态视觉再审计

状态：**2026-09-03 最终独立审计已完成。当前 C-01～C-16 的本地视觉、生产 owner、隔离持久化和逐项所需本地证据均已闭环；C-16 为 Android／Browser／Tauri 各 `84/84`，合计 `252/252`。真实 Provider／网页来源／GLM-OCR、账号／同步、macOS 通知、Developer ID／公证与 OPPO 真机仍是独立外部边界；详见 [最终独立审计](ANDROID_DESKTOP_FINAL_INDEPENDENT_AUDIT_20260903.md)。**

这份台账替代“历史截图／旧测试通过即可视为同步完成”的判断。第 3～4 节保留闭环过程中的带日期历史发现；第 5 节及其汇总记录当前裁决。历史段落中的“未复验／未验收”只说明当时缺口，不得覆盖 2026-09-03 最终独立审计的现行结论。

## 1. 唯一证据优先级

1. 当前 Android 实机／模拟器的同状态可见截图；用户本轮提供的真实 APP 截图优先于任何旧截图。
2. 当前 Android `ConversationWorkspace.kt`、设置现行合同及对应领域 owner。
3. 当前 Desktop 同状态、同内容、同目标视口的浏览器与隔离原生截图。
4. 历史截图、既有 `PASS`、旧矩阵、测试数量和打包记录仅作线索，不能定义当前视觉结果。

任何一项 Android 可见层级不能用设置页文案、路由 ID、Provider 内部字段或“看起来合理”的推断替代。Desktop 可保留侧栏、宽画布、指针／键盘密度等桌面适配，但必须在该单元写出明确的适配理由；不能借桌面适配另造组件。

## 2. 每个状态的硬门

每个单元必须同时具备：

- Android 同状态截图和实际可见文本；
- Android 可见组件／状态 owner；
- Desktop 同状态截图；
- 容器、锚点、层级、卡片、文字、图标、选中态、滚动边界、空／失败态的差异结论；
- 交互路径和回归 seam；
- 需要保留的 Desktop 平台差异及理由。

缺任一项只能写“未验收”。同一状态的截图必须同一页面、同一选择、同一内容和同一可比较视口；不能把联系表或另一页截图当作通过证据。

## 3. 已建立的首轮基线

### A-01 Composer 模型选择根层 — **Android／Browser／隔离 Tauri 本地同状态通过**

- Android 参考：当前 Android 参考图 `03-model-root.png`，以及本轮用户提供的深度子层实拍。当前 Android `ConversationWorkspace.kt` 的 `ComposerModelPickerHeader`、`ComposerModelPickerSectionLabel`、`ComposerModelOverlayRow` 是可见 owner。
- Desktop 修复：普通会话和临时会话统一改用 Composer 关联的 `.composer-model-sheet`，根层具固定遮罩、拖拽条、完整圆角面、“自动选择／按任务选择”节标题、三张独立卡和浅橙选中反馈；旧 `.p6g-model-popover` owner 已删除。
- 实看结果：深色与浅色 `1440×900` 均完整显示且无页面溢出；点击遮罩／关闭钮可整层关闭，二级层第一次 `Escape` 返回根层，第二次 `Escape` 关闭。Browser console warning／error 为 0。
- 后续闭环：已补当前 Android `1140×2616`、Browser `1440×900`、隔离 Tauri `1229×768` 的 C-04 根层原图与语义树；三端层级、选择反馈和关闭语义一致，未配置真实 Provider。

### A-02 Composer 模型具体候选 — **Android／Browser／隔离 Tauri 本地同状态通过**

- Android 参考：本轮用户提供的深度候选实拍；当前 `ConversationWorkspace.kt` 子层在标题下显示“选择具体模型”，每个候选使用 `ComposerModelOverlayRow`。
- Desktop 修复：日常／深度子层都恢复“选择具体模型”，各 6 张浅灰圆角卡，共用同一 Sheet owner；小字由共享运行态投影生成 `Provider · 实时联网／未联网`，DeepSeek 由当前时刻计算“当前高峰／当前低谷”再拼接会话联网状态。
- 目录修复：深度第六项固定为 `Qwen3.8-Max`；旧 `Kimi K3` 从可见目录和自动路由候选移出，历史精确选择值仍保留归因但 fail-closed，不静默映射成 Qwen。
- 实看结果：深／浅色日常 6 项、深度 6 项均完整可达，未出现能力介绍、内部 route ID 或 `Kimi K3`；选中、hover、focus、返回与滚动边界由 DOM／CSS 行为测试和截图共同守门。后续已补当前 Android／Browser／隔离 Tauri 的 C-05／C-06 原图与语义树，深度末项均为 `Qwen3.8-Max`。

### A-03 会话主画布与 Composer — **C-01／C-02 Android／Browser／隔离 Tauri 通过**

- Android 当前证据：隔离 `NanzhufengFindN5Api35` 模拟器重新构建并采集 `android-c01-empty.png` 与 `android-c02-content.png`。C-02 使用固定 user message `C02-local-visual-fixture`；Wi-Fi／移动数据关闭且服务商未启用，真实结果是“回答未完成”，没有成功 Provider 调用。
- Desktop 修复：删除空态 Hero 图形、欢迎标题和持久化技术说明；空态／内容态共用固定底部 Composer。Browser 预览 fixture 使用同一 user message 与 `PROVIDER_NOT_ENABLED` 失败事实；内容态顶部为新对话／更多，正文保持唯一消息滚动 owner。
- 实看结果：`1440×900` Browser 的 C-01／C-02 均无页面溢出，console warning／error 为 0；C-01 的隔离 Tauri 空库为安静画布与底部 Composer。后续新增仅诊断启用的 C-02 原生夹具，隔离 Tauri 真实 SQLite 为 1 workspace／1 conversation，显示固定 user message 与 `PROVIDER_NOT_ENABLED` 失败态；同根退出重开仍保持。

### A-04 Composer 加号、基础风格和实时网页搜索 — **历史阶段：面板三端通过，原生附件选择器当时未复验；当前 C-07 本地全链已通过**

- Android 当前证据：仅启动隔离 `NanzhufengFindN5Api35` 模拟器 `emulator-5554`（1140×2616／442dpi），网络关闭；复采根层、专业可靠子层、联网关闭和折叠后重开状态的 PNG／UIAutomator XML。根层顺序为相机 → 图片 → 文件 → 基础风格和语气 → 实时网页搜索；风格子层只有固定五项和单一勾选。
- Desktop 修复：删除旧 `.composer-add-popover` 实现，和模型选择共用 `.composer-transient-sheet-*` 生命周期；加号面板保持 280px 宽、22px 圆角、透明点外关闭层。附件图标／文字为黑色，风格与联网图标使用主题色；风格子层固定五项，没有“默认”、说明或完成按钮。普通会话联网开关使用 `role="switch"`＋`aria-checked`，临时聊天不暴露无效的会话偏好入口。
- 行为闭环：空白新会话可先选择风格或联网状态；首条消息在同一事务中创建会话并落库 override，再构建真实请求，避免首问丢失选择。既有会话继续即时持久；二级层 `Back`／第一次 `Escape` 回根层，第二次 `Escape` 关闭。
- 历史实看结果（闭环前）：Browser `1440×900` 的根层、专业可靠子层和联网开关开／关均通过，console warning／error 为 0；相机／图片／文件在 Web 预览中分别显示能力边界且不伪造成功。隔离 Tauri AX 树确认同一顺序、专业可靠唯一选中、联网开关 `on → off`，折叠重开仍为 `off`；隔离根无网络 socket、无正式数据。当时尚未触发 Tauri 原生附件选择器，所以该项在该阶段保持待验。
- 当前补证与裁决：后续已完成 Android 实际选择器取消／导入／App 内预览／重启恢复，以及隔离 Tauri 实际系统选择器取消／PNG 导入／原图预览／同 bundle 重启恢复。C-07 当前为本地全链通过；相机仅证明隔离模拟器安全打开退出和 Desktop 无镜头时失败关闭，没有保存镜头内容。

## 4. 证据索引

- Android 当前参考：`~/.codex/visualizations/2026/09/01/01a05b3f-bf39-7580-97c0-0c60854681f3/android-current-final-20260901/`。主联系表为 `contact-sheet-primary.png`；本轮仅对疑点打开了 `01-main.png`，确认它实际是设置页而不是主会话页。
- Android 辅助联系表：`~/.codex/visualizations/2026/09/01/01a05db4-50d5-7131-ae92-29041ef03ac9/desktop-android-parity-20260902/android-current-multipage-contact.jpg`。
- Desktop 当前参考：`~/.codex/visualizations/2026/09/02/01a06283-aaf4-79b0-9864-727e23cea7d3/nanfeng-ai-visual-reaudit-20260902/desktop-current-*.png`，共 30 张；联系表为 `contact-sheet-desktop-01.jpg` 与 `contact-sheet-desktop-02.jpg`。
- C-04 至 C-06 改后 Desktop 参考：`~/.codex/visualizations/2026/09/02/01a062c3-f533-7852-82a9-b84a8b2ea6dd/nanfeng-ai-c04-c06-20260903/`；深／浅色根层、日常、深度共 6 张 `1440×900` PNG，联系表为 `contact-sheet-c04-c06.jpg`。
- C-01 至 C-03 当前证据：`~/.codex/visualizations/2026/09/02/01a062ed-e634-7293-a65d-38dc75b87e29/nanfeng-ai-c01-c03-20260903/`。Android 与 Desktop 各 3 张主截图及对应联系表；Android 另有逐态 UIAutomator XML，Desktop 另有隔离 Tauri `tauri-c01-empty.png`、`tauri-c02-blocked-send.png`、`tauri-c03-sidebar.png`。
- C-07 历史阶段证据：`~/.codex/visualizations/2026/09/03/01a064c0-5646-76d2-bc9d-f3e382b7fe5b/nanfeng-ai-c07-20260903/`。包含 Android 根层／风格／联网关闭 PNG 与 XML，Desktop Browser `1440×900` 根层／风格／联网关闭截图，隔离 Tauri 根层／风格截图，以及 `c07-android-desktop-contact-sheet.jpg`、`c07-android-tauri-contact-sheet.jpg`；它证明当时的面板与偏好状态，不单独承担最终附件链裁决。
- C-12 历史阶段证据：`~/.codex/visualizations/2026/09/03/01a0657a-fb01-7603-8db8-850d1886980e/nanfeng-ai-c12-20260903/`。包含 Desktop Browser `1440×900` 模型与联网／模型设置／费用／上下文／诊断五态、联系表、隔离 Tauri 空根与模型联网页；当时 Android 尚未补带数据深页截图。
- C-07～C-12 当前闭环证据：`~/.codex/visualizations/2026/09/03/01a06634-d3d4-75f2-8034-8979e48fa351/nanfeng-ai-c07-c12-closure-20260903/`。包含 Android 与 Desktop 联系表及关键原尺寸单页；C-07、C-08、C-09、C-10、C-12 的最终本地裁决以该目录和 [最终独立审计](ANDROID_DESKTOP_FINAL_INDEPENDENT_AUDIT_20260903.md) 为准。
- C-16 当前 252 槽证据：`~/.codex/visualizations/2026/09/03/01a066a9-bc6d-7b31-857d-0c0fa2efd991/nanfeng-ai-c16-final-20260903/`。Android／Browser／Tauri 各 `84/84`；manifest 逐槽回读逻辑 ID、请求／解析明暗、宿主明暗、字号、层、源码指纹、PNG 哈希和 pass，原尺寸证据不由联系表替代。
- 采集条件：Desktop 本地预览 `1440×900`；主题／字体对照时明确切换为系统浅色＋标准＋橙色，并另采深色和大字体。浏览器不能执行的原生链路只记录失败关闭状态，不能代替隔离 Tauri 验收。

结论标记：**不通过**表示已有同状态或明确 owner 证据可确认差异；**部分不通过**表示部分状态可裁决、部分仍缺证；**未验收**表示缺 Android 或 Desktop 同状态实看证据，绝不等同于没有差异。

## 5. C-01 至 C-16 完整差异台账

| ID | 当前证据与 owner | 裁决及完整差异 | 交互路径／回归 seam | 允许保留的 Desktop 适配 |
| --- | --- | --- | --- | --- |
| C-01 空对话 | 当前 Android `android-c01-empty.png`＋UIAutomator；`ConversationWorkspace`、`ConversationShellHeader`、`ConversationComposerDock`。Desktop `desktop-c01-empty.png`、`tauri-c01-empty.png`；`renderChatFirstShell`。 | **同状态实看通过。** Desktop 已删除 Hero、欢迎标题与实现说明，空态为安静画布；空态和内容态均使用同一底部 Composer。Browser `1440×900` 无溢出、console 0；隔离 Tauri 空库同态。 | 新建对话 → 空画布；`c01-c03-chat-shell-parity.test.mjs` 锁定无 Hero／固定 Composer，Browser DOM 与原生 AX 树共同守门。 | 宽屏保留常驻侧栏、760px Composer 阅读宽度与键鼠焦点；不新增空态文案。
| C-02 有内容对话 | 2026-09-03 当前 Android `android-c02-content.png`＋UIAutomator；Desktop Browser `browser-c02-content.png`、隔离 Tauri `tauri-c02-content.png`／`tauri-c02-reopen.png`；owner 同 C-01。三端 user message 均为 `C02-local-visual-fixture`，Provider 均为未启用失败语义。 | **本地同状态实看通过。** 顶部新对话／更多、日期、右侧 user 气泡、回答未完成、消息工具与固定 Composer 均可见；Browser 无溢出、console 0。诊断夹具只在唯一 `/tmp` 根、显式 marker 与 schema 诊断参数同时存在时注入；Tauri 同根重开保持同一失败内容。 | 打开固定会话 → 对齐同一 user message 与失败态 → 验证顶部动作、唯一消息滚动 owner、Composer → 退出重开回读；`c02-c06-native-acceptance.test.mjs` 与 Rust fixture tests 守门。 | 保留宽屏阅读宽度、常驻侧栏和位置导航；消息角色、失败事实与 Composer 层级不分叉。
| C-03 抽屉／侧栏 | 2026-09-03 当前 Android `android-c03-drawer.png`＋UIAutomator；Desktop Browser `browser-c03-sidebar.png`、隔离 Tauri `tauri-c03-sidebar.png`；`ConversationNavigationDrawer` 与 `renderChatFirstShell`。 | **本地同状态实看通过。** 顺序均为品牌 → 搜索 → 定时任务 → 南枫转写 → 最近“新对话” → 底部设置／新对话。Android 当前 owner 是两个独立浮动底部控件；Desktop 宽屏常驻侧栏保持同一入口与信息层级。 | 展开／收起、同名“新对话”列表、搜索、定时任务、转写与底部两控件；DOM、Android XML、Tauri AX 与原尺寸截图共同守门。 | 宽屏常驻、可调宽度和指针／键盘态；入口顺序及独立浮动表面跟 Android。
| C-04 模型根层 | 2026-09-03 当前 Android `android-c04-model-root.png`；Desktop Browser `browser-c04-model-root.png`、隔离 Tauri `tauri-c04-model-root.png`；当前 Compose owner 与 `.composer-model-sheet`。 | **本地同状态实看通过。** 三端均为 Composer 触发的根层，含自动／日常／深度层级、遮罩、拖拽条、完整圆角面与选中反馈；未配置真实 Provider。 | Composer 模型入口 → 根层 → 关闭；DOM／Android XML／Tauri AX、键盘焦点、遮罩关闭和二段 Escape 共同守门。 | Sheet 在宽屏锚定 Composer 并限制宽度；保留指针 focus／hover，不改变 Android 层级。
| C-05 日常候选 | 2026-09-03 当前 Android `android-c05-model-daily.png`；Desktop Browser `browser-c05-model-daily.png`、隔离 Tauri `tauri-c05-model-daily.png`。 | **本地同状态实看通过。** 三端均显示当前 6 个候选及同序卡片，状态小字明确是 Provider 与当前会话联网请求设置；关网 AVD 与无 socket Tauri 未把请求设置冒充联网成功。 | 模型根层 → 日常 → 六个候选 → 返回根层；目录顺序、运行态联网变化、选择落库和重启恢复由行为测试继续守门。 | 保留横向留白和鼠标 hover；卡片语义及运行态小字不变。
| C-06 深度候选 | 2026-09-03 当前 Android `android-c06-model-deep.png`；Desktop Browser `browser-c06-model-deep.png`、隔离 Tauri `tauri-c06-model-deep.png`；当前 `ChatModelRouting` 与 Desktop shared owner。 | **本地同状态实看通过。** 三端 6 项顺序一致，末项为 `Qwen3.8-Max`，无 `Kimi K3`；DeepSeek 与其他 Provider 小字按当前 owner 显示，旧 Kimi 只作历史归因并 fail-closed。 | 根层 → 深度 → 六个候选 → 返回；目录、ID、遗留值拒绝、选择落库和重启恢复由测试守门。 | 可为宽屏增大卡宽；候选目录、顺序和状态文案不得平台分叉。
| C-07 加号与风格 | Android `android-c07-{root-professional,style-professional,root-web-off}.png`＋UIAutomator 与后续实际选择器闭环证据；`ComposerMenuOverlay`。Desktop Browser／隔离 Tauri 同态截图及实际系统选择器／重启证据；`renderComposerAddSheetFrame`、`.composer-transient-sheet-*` 与会话 override owner。 | **通过（本地全链）。** 历史阶段只完成面板、风格和联网偏好，原生附件选择器当时未复验；2026-09-03 后续已补 Android 实际文件选择器取消／导入／App 内预览／重启恢复，以及隔离 Tauri 实际系统选择器取消／PNG 导入／原图预览／同 bundle 重启草稿与附件恢复。旧 Popover 已删除；根层顺序、五风格单选、联网开关与首问原子落库仍由共享 owner 守门。相机仅验证隔离模拟器安全打开退出与 Desktop 无镜头时失败关闭，没有保存镜头内容。 | 加号 → 根层 → 五风格 → 返回／Escape → 联网切换 → 原生选择器取消／导入 → App 内预览 → 同 bundle 重启恢复；Node／Rust 行为测试、Browser DOM／console、Android XML／实际选择器与 Tauri AX／实际选择器共同守门。 | 宽屏保持 280px 锚定 Composer、指针 hover／focus 与键盘导航；不改变入口层级、顺序、选择和开关语义。
| C-08 全屏搜索／预览 | 2026-09-03 隔离 Android 的有效 PNG／PDF／音频／视频／MD／JSON／ZIP／DOCX 与 UIAutomator；Desktop Browser 同内容只读夹具、隔离 Tauri；`renderDesktopSearchPage`、`desktop-attachment-preview-owner.mjs` 与 Rust 私有附件 owner。 | **通过（本地合成资产）。** 历史阶段的图片／PDF／音频／视频固定样本字节无效，ZIP／DOCX／MD／JSON 也缺正向样本，所以当时只裁决文本预览主链；后续已用有效固定资产逐项补齐，隔离 Android 八类均显示成功预览或真实显式边界，Desktop Browser 同内容覆盖全部类型，Tauri 共享预览 owner 与持久化 Rust 合同通过。搜索空态、六分类、排序、结果事实、App 内预览、遮罩层级及关闭后返回原位置均保持通过。 | 侧栏搜索 → 六分类 → 查询 → 时间／大小排序 → 还原 → 结果 → 八类资产 App 内预览／显式安全边界 → 关闭返回；`c08-search-preview-parity.test.mjs`、Android JVM 合同、Rust 搜索／预览／资产持久化测试、Browser DOM／console 与隔离 Android／Tauri 原尺寸证据共同守门。 | 宽屏保留更宽结果区、常驻侧栏和键盘焦点；分类、排序、真实事实、App 内预览、安全失败及返回状态不得平台分叉。
| C-09 定时任务 | 2026-09-03 当前 Android Find N5 隔离 AVD 重采列表／新建 PNG＋UIAutomator；Desktop `1440×900` Browser 与隔离 Tauri；Android `ScheduledMonitor`／WorkManager、Desktop `renderDesktopRemindersPage` 与 Rust `desktop_reminder` owner。 | **当前 Android 层级与 Desktop 本机计划主链通过，外部触发仍未验收。** 当前 Android 实际只有名称、监控要求、每小时／每天、启用／暂停、下次时间及最近结果／错误；没有单次、指定时间、每周、独立编辑入口或删除确认。Desktop 已对齐全屏“已计划”、右侧关闭、紧凑通知控件、精确空态和橙色全宽主按钮，并保留既有本机 owner 的单次／指定时间／每周、编辑与删除确认作为 Desktop 扩展，不反向声称为 Android 能力。Browser 只读夹具覆盖空态、创建、启用／暂停／结果／失败、编辑返回与删除确认；隔离 Tauri 真实 SQLite 完成创建 → 暂停 → 编辑 → 恢复，取消删除后仍为 1 条 ACTIVE／ONCE 计划，0 次运行、0 个待处理草稿。 | 抽屉定时任务 → 已计划 → 新建／编辑 → 保存 → 暂停／恢复 → 删除确认／取消 → 返回；`c09-scheduled-task-parity.test.mjs`、Android JVM 合同、Rust owner 测试、Browser DOM／console 与隔离 Tauri AX／SQLite 共同守门。系统通知投递／点击及真实 Provider 执行另列外部验证。证据位于 `~/.codex/visualizations/2026/09/03/01a06519-f65e-7842-ae05-70d2f1be5e06/nanfeng-ai-c09-20260903/`。 | 宽屏保留 820px 内容上限、键鼠焦点以及单次／指定时间／每周扩展；Android 当前入口、列表／表单层级、状态事实、主按钮与返回语义不得分叉。
| C-10 南枫转写 | 当前 Android `GlmOcrWorkspace.kt`／`GlmOcr.kt`、`09-nanfeng-transcribe.png` 与 [当前转写合同](ANDROID_TRANSCRIPTION_UI_CURRENT_CONTRACT.md)；Desktop `renderDesktopTranscriptionPage`、共享附件预览 owner、Rust `desktop_transcription_v1`。 | **当前产品层级、状态矩阵与本机导入持久链通过；真实 Provider 独立未验。** Desktop 空态已收敛为 Android 单画布，底部橙色“选择图片或 PDF”可用且没有白色托盘或录音入口；宽屏任务＋详情双栏只在有任务后出现。Browser `1440×900` 覆盖空态、待确认、处理、失败、完成和关闭返回，控制台 0 warning／error。隔离 Tauri 通过真实系统选择器导入 1.3 MB PNG，App 内预览原图，SQLite 记录保持 `QUEUED／Attempt 0／请求 0`，关闭重启后仍回读。Browser 失败／完成为明确只读夹具，Rust 合同覆盖缺凭据持久失败和 Markdown 完成；未调用真实 Provider，故不声称服务端结果／失败或账单已实测。 | 侧栏转写 → 选择图片／PDF → 待确认 → 原文件预览 → 开始／处理中 → 完成或失败重试 → 加入新对话／关闭返回；`c10-transcription-parity.test.mjs`、Android JVM 合同、Rust owner、Browser DOM／console、隔离 Tauri AX／SQLite 共同守门。证据位于 `~/.codex/visualizations/2026/09/03/01a06547-6299-77e1-8413-2425eb0792a4/nanfeng-ai-c10-20260903/`。 | 宽屏双栏、较大预览和键鼠态可保留；入口、橙色主动作、持久状态、失败语义、共享 App 内预览和返回层级不得平台分叉。
| C-11 设置首页／外观 | 2026-09-03 当前 Android 隔离 AVD `android-c11-*` PNG／UIAutomator；`SettingsCategoryList`、`AppearanceSettingsPage`、`AssistantExperienceSettings`。Desktop `1440×900` Browser `desktop-c11-*`、隔离 Tauri `tauri-c11-*`；`renderAndroidSettingsShell`、`DesktopParityPreferences`、Rust `desktop_app_settings_v1`。 | **同状态实看与本机持久化通过。** 两端均为对话 → 外观 → 数据管理 → 工作区四组，外观内固定外观／字体大小／主题色；个性化不再出现工程阻断卡、SQLite 说明或伪成功通知，已实现控件可用。当前 Android 可见事实为外观 `系统（默认）／浅色／深色`、字体 `小／标准／大`、主题 7 色、自定义指令 `8000` 字；Desktop 共享同一目录与字数常量。Browser 真实刷新与隔离 Tauri 退出重启均回读“大／紫色”，Tauri SQLite 为 schema 37、0 workspace、`large/purple`。 | 设置 → 四组 → 外观／字体／主题 → 返回；C-11 Node 红绿合同、主题 computed style、Android JVM／build、Browser DOM／刷新、Rust 落库与隔离 Tauri 重启共同守门。证据位于 `~/.codex/visualizations/2026/09/03/01a06560-da3b-77d0-8a1c-08497b07c352/nanfeng-ai-c11-20260903/`。 | 双栏、固定左栏、右侧详情和桌面密度；不得显示工程实现说明替代用户状态。
| C-12 模型与联网／调用记录 | 当前 Android `ModelSettingsUi.kt`、`ConversationCostLedgerUi.kt`、`AutomaticWebSearchPolicy.kt`、带数据深页原图与 [C-12 合同](C12_MODEL_NETWORK_PARITY_CONTRACT.md)。Desktop Browser 五态、隔离 Tauri 同内容深页；`renderAndroidSettingsShell`、`DesktopParityPreferences`、Rust ordinary-chat／usage owner。 | **通过（本机脱敏深页）。** 历史阶段只有 Android 根页、Desktop 五态与本机开关持久化，Android 带数据深页当时未补；后续 Android 与 Tauri 已显示同一脱敏 OpenRouter／`openai/gpt-5.6-terra`、输入 1,240／输出 680 Token、实际 `$0.01425`、3 项上下文／1,680 Token、网络失败／约 1.4 秒，Browser 同内容夹具通过。全局 switch、每次普通对话检索、附件联网边界、四类账本与诊断事实时间仍由当前 owner 守门。真实 Provider 来源、网页来源和账单没有验，不属于本地闭环声明。 | 模型与联网 → 全局开关 → 模型设置 → 四 Provider → 费用／上下文／运行诊断；C-12 合同、Android 带数据深页、Browser 同内容夹具、Rust owner 与隔离 Tauri 深页／重启共同守门。真实 Provider／网页来源／费用另行授权。 | Provider 表单横向展开、宽屏双栏与键鼠态；开关语义、入口顺序、四类账本、来源失败门和事实时间不得平台分叉。
| C-13 对话生命周期 | 2026-09-03 当前 Android 隔离 AVD `android-c13-*` PNG／UIAutomator；`ConversationManagementSettingsCard`、`FavoriteConversationListSettingsCard`、`ConversationLifecycleListSettingsCard`。Desktop `1440×900` Browser、隔离 Tauri；`conversation-lifecycle-view.mjs` 与 Rust lifecycle／favorite owner；[C-13 合同](C13_CONVERSATION_LIFECYCLE_PARITY_CONTRACT.md)。 | **同状态实看与本机生命周期主链通过。** 根页标题、说明和顺序一致；收藏显示更新时间，归档／回收站显示创建时间；行级动作分别为取消收藏或恢复／删除，批量清理保持 Android 当前能力。Desktop 用省略号 disclosure 适配 Android 左滑，动作默认不常驻。两端已实看带标题的可恢复删除确认和不可恢复永久删除确认。隔离 Tauri 实际恢复归档后退出重启仍为活动会话，收藏会话打开后可返回收藏页；SQLite `integrity_check=ok`、1 workspace／3 conversations／1 favorite、无网络 socket。永久删除提交由 Rust 隔离测试执行，原生 UI 只检查确认并取消。 | 设置 → 对话管理 → 收藏／已归档／回收站 → 展开动作 → 确认／取消 → 打开会话／返回 → 恢复 → 重启；C-13 `5/5` 红绿合同、Android `90/90` JVM、Node `217/217`、Rust `189/189`、Browser DOM／console 和 Tauri AX／SQLite 共同守门。证据位于 `~/.codex/visualizations/2026/09/03/01a06594-b8df-7312-b5e6-0ce6d2940666/nanfeng-ai-c13-20260903/`。 | 保留宽屏双栏设置、鼠标／键盘省略号 disclosure 和焦点态；动作集合、时间字段、确认层级、可恢复边界及返回位置不得平台分叉。
| C-14 导入导出／本机数据 | 2026-09-03 当前 Android 隔离 AVD PNG／UIAutomator；`PrivacyDataUi.kt`、`PrivacyDataViewModel.kt`。Desktop `1440×900` Browser、隔离 Tauri；`local-data-view.mjs`、Rust privacy／exchange owner 与 [C-14 合同](C14_LOCAL_DATA_PARITY_CONTRACT.md)。 | **本机聚合、隔离交换主链与原尺寸视觉硬门通过。** Desktop 可见入口为“导入工作区／导出工作区”，两端均覆盖对话与内容／附件／导入概况，以及失败任务、知识与记忆回收站、全部本地数据三类清理。Android `1140×2616`、Browser `1440×900`、Tauri `1229×768` 的导入导出、本机数据、清理范围与全部删除禁用门原图已单独保留。Browser 错误确认语禁用、精确确认语启用；Tauri SQLite 完整且无 socket。图形界面未提交删除，真删除与过期拒绝由 Rust 隔离测试覆盖。 | 设置 → 导入与导出／本机数据 → 导入、导出、清理、删除 → 回读／拒绝；完整 Node `234/234`、Rust `196/196`、Android JVM `1084`（0 failure／0 error／3 opt-in skipped），Browser console 0。当前原图位于 `~/.codex/visualizations/2026/09/03/01a06645-e226-7cd0-82c7-07532b18a582/nanfeng-ai-c02-c06-c14-closure-20260903/`。 | 宽屏双栏、原生路径选择和键鼠态可保留；入口术语、分组顺序、聚合数据边界、预览指纹与强确认层级不分叉。
| C-15 工作区／项目／知识／开发 | 2026-09-03 当前 Android 关网隔离 AVD PNG／UIAutomator；`WorkspaceSettingsCard`、`DevelopmentDiagnosticsSettingsCard` 与 workspace owner。Desktop `1440×900` Browser、隔离 Tauri；`workspace-view.mjs`、ordinary-chat Rust owner 与 [C-15 合同](C15_WORKSPACE_KNOWLEDGE_PARITY_CONTRACT.md)。 | **同状态实看与项目会话 owner 通过。** 工作模式不再落回普通会话：未选项目显示 Android 当前空态且无 Composer，选中 `C15_Project` 后才显示 Composer，真实发送前仍为 `0 个工作对话`；无 `projectId` 的普通会话不进入工作态。Projects／知识／记忆和设置入口复用同一 owner；首条发送原子持久 `conversation.projectId`。Browser 七态无溢出、console 0；隔离 Tauri 同根重开后 fixture 仍在，SQLite 完整、活动会话／relation 为 0、无网络 socket。 | 底部工作 → 选择项目 → 项目／知识／记忆 → 设置工作区／开发 → 返回同一 owner；C-15 `4/4`、Android 最小 JVM／build、Node `225/225`、Rust `194/194`、Browser DOM／console 与 Tauri AX／SQLite 重开共同守门。证据位于 `~/.codex/visualizations/2026/09/03/01a065e5-85ed-7011-9f89-98d3c56d34d5/nanfeng-ai-c15-20260903/`。 | 保留 Desktop 宽屏侧栏与键鼠态；项目选择、空态、Composer 出现条件、会话 project owner 及项目／知识／记忆语义不得平台分叉。 |
| C-16 主题／字体／弹层 | Android 当前 `AppearanceSettings`、`applyAppearancePalette`、`LocalAppTextScale` 与弹层层级为唯一事实；Desktop `c16-theme-matrix-fixture.mjs`、`desktop-theme-owner.mjs`、C-16 合同及三端 252 槽 manifest。 | **通过（本地三端 84 项全矩阵）。** 历史阶段只有 `84` 项逻辑合同、`63` 项 Browser 渲染和代表性原生层，完整跨端视觉硬门当时未验；后续已补成系统浅／系统深／显式浅／显式深 × 小／标准／大 × 七层，在 Android／Browser／严格签名隔离 Tauri 各 `84/84`，合计 `252/252`。每槽均有原图与 JSON，Android 另有 XML；manifest 强审计通过。Android 深色／大字与 Tauri `dark/large/orange/revision 3` 均有重启持久化回读。 | 系统（浅／深）／显式浅色／显式深色 × 小／标准／大 × 模型根／日常／深度／加号／风格／搜索／设置；三端逐槽原图、状态 JSON、Android XML、源码指纹、PNG 哈希、AX 回读和重启持久化共同守门。证据见 [C-16 合同](C16_THEME_FONT_OVERLAY_PARITY_CONTRACT.md) 与 252 槽目录。 | 桌面保留 hover／focus 和宽屏布局；色彩 token、层级、选中反馈和可读性只有一个主题 owner，显式 App 选择不受系统媒体查询反向覆盖。

### 台账裁决汇总

- **当前裁决：** C-01～C-16 的本地视觉、生产 owner、隔离持久化和逐项所需本地证据均已闭环。C-07 是本地选择器／预览／重启全链，C-08 是八类有效合成资产，C-12 是两端同内容脱敏深页，C-16 是三端各 `84/84`、合计 `252/252`。
- **历史缺口：** C-07 原生附件选择器、C-08 非文本有效样本、C-12 Android 带数据深页、C-16 从 `63` 项 Browser＋代表原生层扩为三端 84 项，均曾在较早阶段明确待验；这些记录保留用于说明发现与补证过程，不再代表当前状态。
- **当前外部边界：** C-09 系统通知／真实 Provider、C-10 真实 GLM-OCR、C-12 真实 Provider／网页来源／账单，以及账号／同步、Developer ID／公证、正式数据根和 OPPO 真机仍未验；它们不属于本地 C-01～C-16 闭环声明。

## 6. 禁止再次发生的验收漏洞

- 不得由用户指出后才新增矩阵行；先完成 C-01 至 C-16 的采集与裁决。
- 不得用 DOM 可见、Node 测试通过、编译通过或旧 bundle 截图替代同状态视觉证据。
- 不得把 Android 设置页的文案搬到 Composer、把内部 Provider／route ID 直接显示，或用一个“通用 Popover”承载本应是 Sheet／卡片的 Android 选择面。
- 任一“Desktop 适配”必须保留 Android 的信息层级、组件语义、状态文字与选择反馈；仅允许变化导航容器、窗口密度和输入方式。

## 7. 后续执行边界

1. C-01～C-16 的本地回归继续使用当前 Android 源码、有效固定资产、专用诊断夹具、隔离数据根和原尺寸证据，不接真实 Provider，也不以联系表替代单页。
2. C-07 原生选择器与 C-08 八类资产已闭环；后续只有在 owner 或合同变化时才重跑对应本地链，不再把已关闭的历史缺口列为当前待办。
3. C-09 通知／Provider、C-10 GLM-OCR、C-12 真实 Provider／网页来源／账单，以及账号／同步属于独立外部验证，必须另行授权，不能由本地夹具结论外推。
4. C-16 三端 252 槽已经完成；Developer ID／公证、正式数据根和 OPPO 真机仍属于独立交付或设备验证，不在本台账的本地完成声明内。
