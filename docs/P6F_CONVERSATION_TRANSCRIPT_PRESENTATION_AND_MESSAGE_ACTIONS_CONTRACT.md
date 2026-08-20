# P6-F Conversation Transcript Presentation & Message Actions Contract

状态：数据/动作 owner 与 FB-P6-027..034 presentation UI 门均已关闭。平台：Desktop + Android 同阶段；仍不外推为 Provider、账号或后续 Adapter 完成。

## 2026-08-14 用户交接覆盖：双端统一先于后续阶段

- 本合同及 ledger 中所有用户可见反馈都必须同步落在 Desktop 与 Android；任何一端的静态检查、构建、截图或模拟器近似都不能替代另一端的实际交互、退出重开与 readback。
- NORMAL、TEMPORARY、WORK 三个入口只能共享同一 shell 和同一 Composer：纯白留白、固定底部 `＋ / 模型 / 发送`、相同的模型菜单/附件菜单轮廓和阴影。数据 owner 可以不同；视觉结构、可访问标签和行为入口不得分叉。空态禁止工程说明、fixture 字样、内部 ID/数量/路径/生命周期文案，禁止以删除 Composer 代替空白内容。
- Composer 下方不得常驻显示“发送时自动保存”“不调用模型”“配置模型后可生成回答”等实现说明；发送的真实本地 owner 和模型边界继续由行为、必要的即时状态与设置承载，不复制成输入区装饰文字。
- NORMAL 与 TEMPORARY 从 Composer 成功发送本地草稿后，必须立即由各自唯一 transcript `LazyListState` 置底；即使用户发送前正在阅读历史消息，也不能停留在旧位置或要求其额外点击“到最新消息”。只在本地消息追加完成后执行一次直接 `scrollToItem`，不伪造消息、重建列表或触发 Provider。
- `＋` 的用户可见顺序固定为相机、添加图片、添加文件；两端每一项必须接入自己的真实受限 private-copy owner。模型入口永远显示“自动”或真实 catalog display name，TEMP 不能出现另一套“临时”模型文案或 dialog。
- 本轮尚未达到上述 Desktop+Android 双端关闭门；接手任务必须先把最新代码与用户截图逐项对照，按 ledger 逐项重新验收，再恢复蓝图阶段。

## 2026-08-14 可见反馈增量（FB-P6-050，实施中）

- Desktop 位置轨道与 Android 内屏轨道都只读取当前 transcript 的既有 message 列表；点击只驱动既有 Desktop message-list scroll owner 或 Android `LazyListState`，不新建滚动真值、不持久化位置或预览正文。
- Desktop 预览与条形包络都只在位置标记 hover 或键盘 focus 时取当前已显示消息的短文本切片；点击只驱动既有 transcript scroll owner 的跳转与无障碍 current，不得留下黑色峰值、长线包络或预览卡。初始状态没有任何视觉 active/current，首条短线必须与其他 idle 短线一致；滚动只更新无障碍 current，不能把旧点击位置冻结为视觉状态。轨道在 hover/focus 时以当前线 `28px` 深色为峰值，向两侧按 `20/14/10/7/5.33px` 与同步变浅的灰阶快速而连续地收敛，形成图二式双侧条形包络；离开后全部回到 `5.33px` idle，不能只亮一条后突然消失。轨道行距为 `3.5px`，较原 7px 精确减半；预览卡从轨道左端 `44px` 开始，与最长激活线保留 `16px` 间距；保留原 `28px` hit target。整组在 transcript 区域垂直居中，不贴顶。Android 内屏选中预览同样是 transient UI，不写入数据库、搜索、导出、Provider 或日志。Android 的 600dp 阈值是内屏语义防线，外屏完全不渲染；AOSP 2248×2480 近似内屏已验证预览和点击同一 `LazyListState`。最终 OPPO Find N5 折叠连续性仍需另行设备验收，Desktop `.app` 仍须人工交互 readback。

## FB-P6-052/053/055 顶栏与移动 Composer（实施中）

- Android 顶栏的菜单与临时入口以全宽 Row 各守左右；分段模式胶囊作为同一全宽 Box 的 `Alignment.Center` 子节点，因此以整块屏幕中心定位。正常聊天 canvas 不得再渲染本地身份/联网状态说明。
- 移动 Composer 只有一个 `28dp` 圆角白色外层与阴影，不允许再画内层输入框。＋、草稿字段、模型文字入口、发送依次同列；模型默认无额外方框，点击仍只能打开既有当前会话模型 dialog。模型文字位于发送键紧左；无已实现语音 owner 时不得仿制麦克风或语音动作。

## FB-P6-056 Composer 按钮锚定弹窗与固定 Dock（实施中）

- Android 的 Composer 外框是固定 `60dp` 测量 dock；＋/模型菜单必须是会话根容器内固定尺寸的 sibling overlay，以 placement-only offset 对齐各自按钮正上方。不得使用会触发独立焦点/窗口与 IME 重协商的 `Popup`，也绝不可参与 dock 或输入框的大小、位置、IME inset 与阴影计算。开关前后草稿/发送语义 bounds 必须保持一致。
- ＋菜单没有标题或解释段落，顺序固定为带成熟 Material 图标的“相机 / 添加图片 / 添加文件”，文字与图标均为中性灰。相机通过系统 `TakePicturePreview` 实际启动，结果编码后沿既有受限私有图片附件 owner 写入；不得假装为相册或外发。
- 模型菜单仅显示可用模型行和当前勾选；未选中中性灰，当前项橙色。两个菜单都在触发按钮正上方，弹窗保留柔和阴影，必须为 shadow 留出空间而无矩形裁切硬边。

## FB-P6-057 临时聊天入口状态色（实施中）

- 普通会话顶部幽灵入口必须用 `SecondaryText` 中性灰；进入 `TemporaryConversationPane` 后，该窗口内同一临时聊天退出入口才使用 `AccentOrange`。颜色只投影真实 `temporaryRecovery` 状态，不改变进入、退出、恢复或无障碍语义。

## FB-P6-058 临时会话的统一消息与模型入口（实施中）

- `TemporaryConversationRecovery.messages` 是当前用户在临时会话已提交的本地消息，不得伪装为 Assistant/南枫AI 或系统信息。其文字必须复用 USER 的右对齐、暖橙色、内容自适应气泡；`attachmentIds` 只能映射为附件图标，禁止显示内部 ID、附件数量、草稿/验收文案、路径或 MIME。
- 临时空 transcript 保持空白，不添加“开始临时对话”或安全/生命周期说明；底部草稿、添加附件、发送的可见文案与 accessibility 描述复用普通会话，临时状态仅由已激活的 Ghost 颜色与退出动作表达。
- TEMP 与 NORMAL 必须复用 `ComposerModelEntry` 和根级 `ComposerMenuOverlay`：入口标签永远是“自动”或 catalog display name，模型列表定位于自身按钮正上方，未选中灰、当前项橙色勾选。选择结果只交给原有 TEMP recovery owner，不能写普通 conversation override、Provider 配置或发送 HTTP。

## FB-P6-060 Composer 内图片草稿预览（实施中）

- 新增图片后，缩略图和右上角关闭动作必须成为同一个白色 `ConversationComposerDock` surface 的内层内容，位于固定 `60dp` 控制行上方；不得在 Composer 外另起横向预览行、文字“移除”按钮、卡片或气泡。多附件可以在这一个 surface 内横向滚动。
- NORMAL 与 WORK 由 `DraftComposer` 传入同一个 `ComposerAttachmentPreview`；TEMP 使用同一组件与同一尺寸、点击/长按语义，但 TEMP 的 opaque attachment ID 仍只经 TEMP private-copy owner → safe reference → bounded projection 读取，不能流入普通 Conversation、路径、URI、Provider 或导出。
- 点击图片继续仅打开既有本地原图预览；长按才显示名称、MIME、大小。关闭只移除当前草稿引用：NORMAL 走原有 `RemoveConversationAttachmentUseCase`，TEMP 走 `TemporaryConversationDomain.removeDraftAttachment`，均不删除可能被其他引用持有的私有资产。
- `＋`/模型 overlay 仍是 Composer 根容器 sibling；菜单开闭不得重测 Dock 或改变 IME/控制行位置。附件加入或移除本身可按真实内容改变同一白色外壳高度，不能被误描述为菜单导致的跳动。

## FB-P6-051 Drawer 底部独立动作（实施中）

- 设置与新对话仍在 Drawer 底部同一行，但各自是独立可访问与按压 surface：Android/ Desktop 设置为靠左的单独白色圆形按钮；新对话保持靠右的独立橙色胶囊和既有方框斜笔 glyph，并有自身阴影。
- 两者以 `SpaceBetween` / `justify-content: space-between` 分居两侧；禁止公共 tray、共同背景、连体圆角、共享 shadow 或把任一点击热区借给另一按钮。动作 owner、标签、键盘/长按语义不变。

## FB-P6-040 Desktop Composer 右侧分组回归修复（实施中）

- Desktop 的单一模型选择与发送键共同属于 Composer 的右侧 primary action group；模型在发送键紧左，不得再给发送键单独 `margin-left:auto` 而把模型留在左侧。
- 模型的 40px 点击热区、缩小 20% 的视觉胶囊和放大后的居中文字不变；本次只修复组级布局 owner。

## 目标与顺序

P6-F 在 P6-E 后、P6-F2 与 Model Selection / Auto Router 前。固定后续顺序为：P6-F Core Transcript → P6-F2 分类搜索/历史与逐附件预览 Adapter → Model Selection/Auto Router → ChatGPT JSON → Claude JSON → 南枫知识库 JSON → exact cache → 蓝图其余路线。P6-F2 的唯一规则正文为 `P6F2_UNIFIED_SEARCH_HISTORY_AND_LOCAL_CONTENT_PREVIEW_CONTRACT.md`。P6-F 只改善真实 Message Tree/本地记录的呈现与消息级动作，不接 Key、Provider HTTP、图标或账号。

## FB-P6-034 角色化工具可见性（已关闭）

- Assistant/南枫 AI 的外置工具行始终可见，顺序固定复制→分享→分支→真实时间→实际 model；缺失 model 只隐藏 model，不倒填。行在开放正文之后，不能被 bubble 或附件 overlay 收编。
- USER 继续 Desktop hover/focus、Android long-press；附件名/MIME/大小仅在自身 Desktop overlay / Android long-press sheet。Desktop/Android 的真实 UI/restart/readback 已完成。

## FB-P6-037 Desktop 回收站确认（已关闭）

- 可恢复 soft-delete 的唯一 dialog owner 在同一确认状态对象：确认后立即进入 submitting、防止重复提交；只有 typed domain/IPC receipt 成功才清 dialog/target/error、刷新列表及 selection 并回焦新对话入口。
- receipt 失败保留原 target 与 dialog，解除 submitting 并显示可恢复错误；取消/Escape 只关闭 overlay、绝不触发 mutation。重启不得恢复已成功会话的 stale selection 或幽灵 dialog。

## FB-P6-039 附件 sibling preview group（CLOSED，2026-08-14）

- 附件预览组脱离整条 message bubble/card，按 role 对齐：USER 右侧、Assistant 左侧。mixed message 仍分别保留 USER 文本气泡和 Assistant 开放文本，不能把附件布局反馈扩大成文字样式改变。
- 图片/视频比例、文档方形、音频紧凑卡等附件自身内容 surface 继续保留；message tools/metadata 是整条消息 sibling，附件 metadata 仍是 attachment-owned overlay/sheet。
- Desktop `chat-message-attachments` 与 Android `attachmentContent` 都只能作为文本 surface 的 sibling；text-only、attachment-only、mixed 和 multi-attachment 均保持这个边界。Desktop USER 组自右对齐、Assistant 组自左对齐；Android 使用相同 `Alignment.CenterEnd/CenterStart` 投影。最新包的重启 readback 仍保留 attachment-owned overlay/long-press，而没有重新包回消息正文。

## FB-P6-040 Composer 单一模型胶囊（历史验收；Desktop 右侧布局回归已在本文件顶部重新打开）

- 普通会话仅保留一个 Composer 内模型选择器，顺序固定为输入框 → 模型选择器 → 发送；TEMP 继续只显示 P6-E 本地标识，绝不进入 P6-G 选择语义。模型按钮紧贴发送键左侧，不在顶栏、侧栏或其他位置复制入口。
- Desktop 的 40px 命中区保持不变，内层可视胶囊为 32px（0.8）并在 hover/focus/pressed 时始终由同一胶囊 surface 表达；标签从原 10px 变为 12px（1.2），长名截断。Android 的 48dp 点击区保持不变，内层胶囊为 38.4dp（0.8），标签为 16.8sp（1.2）；两端均使图标和标签在胶囊中居中。
- 选择只调用既有 P6-G 当前会话 override owner：Auto 与本地 fixture 的选择、完整退出/重开或 force-stop/restart 均由原 owner 读回；不读取 Key、不构造 RunSpec、不发 HTTP、不执行 Provider。

## FB-P6-041 Composer glyph 0.8 与向上纸飞机（CLOSED，2026-08-14）

- 唯一改动是 add 与 idle-send 的内部 glyph token/transform：Desktop add 从 26px 到 20.8px，send 从 25px 到 20px；Android add 从 24dp 到 19.2dp，send 从 22.5dp 到 18dp。两端均为精确 0.8，中心不移。
- Desktop 保持既有官方纸飞机 SVG；Android 使用既有 Material `AutoMirrored.Send`。两端 send glyph 都只对内部 glyph 施加逆时针 90° transform，最终朝上；不得旋转按钮盒、surface、ripple、focus 或热区，也不得使用手绘 SVG/CSS art。
- Desktop 40px hit target、30px send surface、model 40px hit/32px capsule/12px label 均不变；Android 48dp hit target、36dp send surface、model 48dp hit/38.4dp capsule/16.8sp label 均不变。真实 stop 仍是既有独立 22.5dp token，不属于 idle-send glyph。
- 不改 provider、model owner、Key、HTTP、同步、知识库 Adapter、OPPO、图标或发布。每端新增专用 glyph contract，且必须以最新 Desktop ad-hoc `.app` 与 AOSP signed Debug restart/readback 分别验收；AOSP 不替代 OPPO。

## FB-P6-044 Assistant 真实模型工作时长（受约束阻断，已实施一次本机验收）

- Duration 只由消息级已持久化的 run/import metadata owner 提供；Assistant/南枫 AI 有可靠值时在正文上方左侧以低噪声格式显示，USER 永不显示。Android 只接受同一 message Invocation 所属、非 BLOCKED 且正值的 TaskRun start/end，或同一 message 已持久化 Runtime checkpoint 的正 elapsed；Desktop 只接受已持久化 message `run`/`import` boundary 的正 duration 或有效 start/end。历史或导入缺失可靠值直接 omit，不能由 createdAt、消息顺序或当前模型推断。
- 与正文下方常显复制→分享→分支→发送时间→实际模型行严格分开；运行中仅可显示已经存在的本地计时，完成 receipt 后冻结最终值。当前没有真实运行时计时 owner 时不临时起表。不得因此引入 Provider HTTP、Key 或真实 RunSpec。
- 时长格式为中性“用时 …”，零、负值、无效时间、BLOCKED 与角色不符一律 omit。未知不显示“用时未知”，也不进入 User accessibility/metadata surface。
- 2026-08-14 可行性核查：当前 ChatGPT export adapter 的跨端 message 协议仅含 source id、parent、role、text、createdAt 与 importedModel；commit 仅将这些字段写为 MessageNode，故现有正常导入 UI 没有可保真的 import duration。Android 可见的 `LOCAL_DETERMINISTIC_FIXTURE` 是测试源：所有完成事件使用同一 `t`，且 fixture/local record 不得冒充真实模型；它不能作为本条的正值样本。Desktop 没有 conversation TaskRun/runtime 写入 owner，shell 只消费尚无生产者的 persisted `message.run`/`message.import`。因此不启用 Provider、不提前实施 P6-I 时不存在合规的双端正值验收路径；不得以取消 fixture、UI 等待、createdAt 或测试构造值替代。
- 2026-08-14 已按后续明确授权实施 Android 唯一可见路径：`Settings → 一次真实文本验收 → 确认页`。该页为白色，显示当前已保存服务商、预设和实际模型、费用上限及固定非敏感文本；确认框初始未勾选，未确认、配置缺失或发送中均不能发送。RunSpec 只接受当前已保存的、已验证的 preset，`retryCount=0`；旧外部 Intent 已删除。本机仅经 Keystore 包装检查凭据，绝不向 UI、日志、证据或仓库输出 Key/正文/响应。
- 该路径的唯一 nonce 已在 AOSP 可见确认后消费（**1/1**），但 Adapter 在本机凭据有效性阶段返回 `ProviderCredentialInvalid`：安全 ledger 为 `BLOCKED`、`attempts=0`、`duration=0`。这证明没有 Provider HTTP、Authorization 或响应，也没有正值 duration；不得重试或补发。Desktop 未发请求且不得伪造。若继续，必须先让用户通过既有可见配置路径保存可用凭据并另获新的单次真实请求授权；045、046、047、049 与 P6-I 继续禁止。
- 2026-08-14 已补齐本地 `P2MTranscriptOwner`：仅当既有 P2-M Invocation 与 TaskRun 都是 `SUCCEEDED` 时，才以确定性 conversation/message ID 创建或重放一条 assistant `MessageInvocationReference`，从而让既有 `ConversationTranscriptPresentation` 读取同一 TaskRun 的正边界。该 transcript 只保存固定安全验收摘要，不复制 Provider 请求或响应；失败、取消、BLOCKED 不创建 message，成功但零 elapsed 仍由 UI omit。它不写 P3-B Runtime event（该事件安全模型不承载真实 Provider/凭据事实）、不接触 Key、配置、nonce、transport 或重试。ViewModel 在重启 refresh 时只重放此本地绑定，防止成功记录因进程中断丢失 owner。
- 本轮真实记录依旧为 `ProviderCredentialInvalid`，所以没有可见正值样本，044 仍未 CLOSED。接下来的唯一前提仅是用户通过既有可见设置保存可用凭据并另行授权新的单次验收；045、046、047、049 与 P6-I 继续禁止。

## FB-P6-045 USER 文本气泡 intrinsic sizing（CLOSED，2026-08-14）

- USER 文本气泡只由真实文本内容与 responsive max-width 决定：短文本紧凑，长文本在达到可用宽度上限后换行，始终右对齐；没有视觉 min-width，也不会由附件、工具栏或 metadata 反向撑宽。
- Desktop 的外层 message owner 可包含 hover tools/metadata，但 `.chat-message-bubble` 本体必须独立 `width: fit-content; max-width: 100%` 并由右对齐 column 承托，禁止同级工具反向撑宽短文本气泡；整体仍使用 `max-width: min(82%, 530px)`。Android 使用 `BoxWithConstraints`、82% responsive max 与 `wrapContentWidth(Alignment.End)`。两端的 2 字、短句和长句必须表现为不同的内容宽度。
- 退出门为最新 Desktop ad-hoc strict-signed `.app` 与 AOSP 正式签名 Debug 各自用正常 UI 创建受控消息，完成完整退出/重开 readback；不以 CSS/Compose 单测、数据库注入、旧截图或 fixture 当替代。受控数据若在读回后发生用户写入，必须保留，不能再删除。
- **P0 复核覆盖（2026-08-14）：** 用户指定的 Drawer 白色正式包 `af45c6d5…` 在当前 AOSP 数据环境安装、冷启动后直接选择到旧 `P6F045-049-AOSP-CONTROLLED-20260814` transcript；Drawer 仅暴露泛称“本地对话”，不具备安全证明其会话范围全为验收数据的 owner。不得以标签匹配、DB/ADB 或清数据删除，也不得将此现场写为 045–049 的无污染复核通过。先获得用户明确清理授权或隔离数据环境，才可创建新的受控会话并进入后续反馈或 050。

## 后续登记：对话位置导航（FB-P6-050，未实施）

## 2026-08-14 可见反馈队列优先级覆盖与验收隔离

- 用户暂停 044 的 Provider/凭据关闭门；044 的 nonce、Key、HTTP、重试与 OPPO 边界不变，但它不再阻断 045→046→047→049→050 的本地可见反馈修复。
- 普通“新建本地对话”必须空白创建；任何 deterministic fixture 只能由显式既有 task action 产生，不能作为默认/User transcript 种子。
- 受控 AOSP 可见验收只能使用可严格识别的临时会话；结束时经已有“移入回收站”软删除并 force-stop/restart 验证不可见。没有逐消息删除 owner 时，不能因标签匹配而删除整段无法证明为验收专属的会话，也不得 DB 绕过。
- `0`、负值和缺失 duration 均是 omit，不得在状态卡、transcript 或 accessibility 中格式化为 `0.0 秒`。
- 040–043 旧证据需在最新 Desktop `.app` 与 AOSP 正常 UI/readback 复核；现有 FBP6043 样本是污染缺陷，不得再作 043 成功证据。

### 2026-08-14 最新包复核记录（040—043）

- 040、041、042 已在最新 Desktop ad-hoc strict-signed `.app` 与 AOSP 正式签名 Debug 的实际 UI/readback 重新完成；具体只覆盖 composer model owner、glyph/surface 和顶栏 Settings owner，不外推为 OPPO 或 Provider 结论。
- 043 仅使用新建、可识别的 `P6F043-…-CONTROLLED-20260814` 普通本地会话：Desktop 和 AOSP 各自从真实 Composer 保存足量消息，离底显示“到最新消息”，点击后到末尾稳定隐藏且 Composer 保持可用；重启读回后再次复核。两端均经现有确认式回收站流程软删除该受控会话并重启确认不再正常可见。没有使用 `FBP6043localacceptance*`、fixture、数据库注入或未知会话删除。
- AOSP 首次重启受 ART/JIT 首屏编译与并发 `uiautomator` 注册冲突影响而出现系统 ANR；后续单通道、充分等待的 force-stop/restart 正常读回。该现象保留为环境干扰证据，不以一次失败掩盖，也未扩张为源码改动。
- AOSP Drawer 视觉后续修正为 `ModalDrawerSheet(drawerContainerColor = Color.White)`：定向 owner 合同测试通过；以新正式签名 Debug `af45c6d5abd88fbef48744218b64bbb42613c30e582444c1f5c894e9b74d0e94` 完成 `install -r`、force-stop/restart、`base.apk` 同 hash 及实际 Drawer 截图，白色抽屉无紫色底。此前 `cddef540…` 只保留 043 功能 readback 的历史身份，不能作为该视觉结论。

- 严格排在 FB-P6-044、045、046、047、049 后。Desktop 将从真实 transcript scroll owner 投影位置并允许安全预览/跳转；Android 仅 OPPO Find N5 内屏，外屏不得显示。
- 必须另立 scroll owner、内容预览隐私和折叠连续性合同；不得持久化预览正文、不得通过截图/缓存/估算位置伪造滚动进度。当前不实现、不操作 OPPO。
- 当前 AOSP 指定包复核有上述 P0 数据污染，故即使 045–049 的历史记录均为 CLOSED，也不满足再次开始 050 的前置现场；禁止用旧截图或当前受污染 transcript 替代。

## Transcript 真值与呈现

- P6-F 继承 P6-E 收口 IA：单一“搜索”字段，archive/recycle 只在 Settings 会话管理；work 侧栏为 compact workspace selector、Project/Knowledge/Memory、搜索、pinned/recent，低频 workspace/controlled-record 管理只在真实 Settings 二级页。

- 每个 turn 是独立可操作边界，不使用 USER/ASSISTANT 两个连续大色块。ASSISTANT/NANFENG_AI 左侧开放正文或极浅中性 surface；USER 右侧克制 accent-soft 浅橙 bubble；连续角色可视觉分组但不可粘连。
- 长正文须有最大阅读宽度、行高与段落/list/code/quote/table/link 层级；代码与引用为中性内层。SYSTEM/TOOL/ERROR 只能使用独立语义样式，绝不冒充用户或 AI。
- AI 显示南枫 AI 标识/名称及真实来源 `LOCAL`、`IMPORTED`、`Provider + model`；USER 显示“你”或真实本地用户信息，账号未实现时不伪造头像/昵称。
- 跨日期插入日期分隔。消息下方常驻信息只允许用户友好本地时间与真实模型；任一事实缺失即隐藏该项，不显示 `LOCAL_RECORD`、时区后缀、`模型未知`、`用时未知`、`inert` 或其他工程占位。完整审计事实仍只在持久 provenance/receipt 中，按需以小来源徽标/图标展开。
- 只有 Invocation/Run ledger 真有 start/end/elapsed 时才可在按需详情显示 duration；它不是常驻 metadata。import/cache/fixture/local record 不冒充新生成或真实模型。
- 附件自身信息（文件名、MIME/类型、大小）与整条消息的时间、模型、复制/分享/分支等信息严格分域：Desktop 仅在 attachment preview 的 hover/focus 内部 overlay 显示前者，绝不在预览下方占高度；Android 仅 attachment long-press sheet 显示前者。消息工具仍只在 bubble/open body 之外的 message-level hover/focus tools（Desktop）或整条消息 long-press sheet（Android）出现。默认预览可为无文字的安全缩略图/成熟图标资产，但不得伪造 ownership/state。overlay 采用明显透出的低不透明度底色、紧凑 1–2 行且字体不得低于可读下限。
- document-like（TXT/Markdown/PDF/Office/普通文件）使用正方形中性预览；图片与视频以真实 intrinsic aspect ratio 在最大宽高内 `Fit/contain`，不可强裁正方形；音频使用紧凑横卡。无缩略图时不得伪称真实画面。
- 非关键分隔线以若隐若现的层级提示为准，约比此前低 80% 对比度；不得连带降低正文、字段或控件边框。Desktop splitter 的 1px visual stroke 与较宽 hit target 分离，并只在 hover/focus/active/drag 时增强。

## Message Actions 与隔离

- AI 至少支持真实的复制、系统分享、从此处创建分支；USER 至少支持复制、真实编辑/重试（仅既有 owner 支持时）、从此处分支。操作必须使用成熟图标库的图标按钮，Desktop 提供 tooltip，Android 提供 contentDescription/长按说明；禁止文本按钮、Emoji 或手绘 SVG。Desktop DOM、视觉和 Tab 顺序一律为复制→分享→分支（按角色/既有语义裁剪）→真实时间→实际模型；不得使用 CSS `order` 让语义顺序与视觉顺序分离。Android action sheet 以动作在前、真实时间/模型详情在后。
- 分支必须由选中 Message Tree node 建立新的 Conversation/branch，保留前缀 lineage/provenance；typed intent + revision + idempotency，重启回读，不复制伪文本或隐藏字段。
- copy 输出 plain text；rich/Markdown 是明确次级选项。不得复制 Key、path、URI 或隐藏 metadata。
- share 是独立 Adapter：Android `ACTION_SEND` chooser；Desktop 使用原生系统 share picker，若安全 owner 当前不可用则提供真实可操作的系统分享菜单/复制分享内容，绝不伪造外发或生成公网链接。先展示明确 payload/附件预览，用户点击并确认后才外发；取消无写入/无外发。每个平台独立验收。
- TEMPORARY_SESSION 默认禁用 share、branch、export，也不得藉由 action 创建普通 Conversation、History、Project、Search、Cache 或 Sync 记录。
- Desktop 用 hover/focus 紧凑图标栏与完整文字菜单；Android 用点按/长按/overflow 的图标+文字完整菜单，并满足屏幕阅读器。

## 附件与媒体

- IMAGE 展示安全缩略图、尺寸与点击查看原图；FILE 用 inert 卡片显示安全名称、类型、大小和真实 open/share 动作。PDF、IMAGE、VIDEO、AUDIO 与通用文件的最终原位/最高层行为由 P6-F2 按“一个 Adapter 一个验收”实现；未知类型仅 inert 文件卡。
- VIDEO 仍是独立 Attachment Adapter：系统 picker → private copy → magic/size/duration/thumbnail → 封面/时长 → 系统播放器；不得自动播放、上传或执行。其完成前不得伪装视频入口。
- 图片/视频预览不代表 Provider egress；P6-F 不发 HTTP。

## 双端退出门

## FB-P6-043 回到底部

- 控件只在消息滚动 owner 不在末尾时存在，固定锚定 Composer 顶部并与 content/Composer 水平居中；点击必须驱动该唯一 owner 到末尾，末尾抵达后隐藏。
- Desktop 的平滑滚动期间保留该 owner 的跳转 guard，忽略中间“尚未到底”事件，禁止重绘打断动画或造成闪烁；Android 由同一 `LazyListState` 执行末项动画。
- 真正关闭须分别在最新 Desktop `.app` 和有足够长消息的 AOSP 会话完成 scroll-away → tap → end/hide → restart/readback；AOSP 不代表 OPPO。

- 覆盖长文 Markdown/code/table/quote/link、跨日期/时区、未知与真实 elapsed、normal/imported/cache/temporary、全部角色、图片/文件/视频占位/多附件/失败占位、宽窄屏与屏幕阅读器。
- action 覆盖 copy、share 取消、branch、restart/readback、temporary 零泄漏；每个新增 Adapter 单独自动/真实验收。
- Desktop：Node/Rust、lint、最新 ad-hoc strict-signed `.app` 真路径；Android：Kotlin/Room、lint、Debug/Release 正式签名、`emulator-5554` 真路径与 `install -r`/base APK hash。
- 无 Key 读取、无 Provider HTTP、无图标修改；任何 share 必须用户明确点击和确认。

## P6-F 完成记录（2026-08-14）

- 双端 transcript、日期/本地时区、真实 metadata、纯文本复制、Message Tree 分支和重启读回均按本合同完成；TEMP 不显示 share/branch/export，未创建普通会话或其他泄漏记录。
- Android 系统分享为已验收的明确确认后 `ACTION_SEND` chooser；取消不写入、不外发。Desktop 未具备可安全调用且可验证的原生系统 share owner，用户已明确许可保持该入口隐藏；它不以网页、伪分享或不受控外发替代。
- 本阶段未读取 Key、未发 Provider HTTP、未作图片外发、未操作 OPPO、未改图标或发布。
- FB-P6-027..032 关闭补证：Desktop 外置 tools 的 DOM/视觉/Tab 顺序、附件 overlay/type/弱 divider、官方图标映射均有 Node 回归；新 strict-signed `.app` 实际点击分享后只把非敏感正文复制到本机剪贴板。Android 实际 long-press message action sheet、attachment long-press sheet、DocumentsUI 附件与 force-stop readback 已验。没有外发、URI/path/Key 泄漏或 OPPO 操作。
