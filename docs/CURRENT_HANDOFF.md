# 南枫 AI 当前交接

> **当前合同读取门：** [Android 会话合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)、[设置合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)、[运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)。以下按最近增量记录；历史验收不能覆盖这些当前合同。

## 2026-09-13：Desktop 新对话草稿单 owner 修复与保数据覆盖

- **根因与新方案：** “新对话”动作虽先清空可见输入，但另一条旧事件监听器会在同次点击结束前从 WebView `localStorage` 的通用 `new` 草稿键重新写回文本，形成依赖监听顺序的回填竞态。现在移除 Composer 草稿的浏览器缓存读写与迁移回读；SQLite 是唯一草稿 owner。新对话由单一路由同步清空未绑定 SQLite 草稿，只有选择既有会话才异步恢复该会话的 SQLite 草稿，并以 route generation 丢弃迟到结果。
- **回归：** 先以旧源码观察“单 owner／无浏览器缓存”断言失败，再完成修复；Desktop lint、typecheck、Node 378/378、静态 build 与 `git diff --check` 通过。发送路径同步移除了已废弃浏览器草稿键的引用，避免成功提交后抛出 `ReferenceError`。
- **Desktop 覆盖：** 候选和安装后主程序 SHA-256 均为 `ba02b3b0f5654aebf3ea914662706587246893156b0e536f58249214c6d2e826`，Bundle ID `com.nanzhufeng.ai.desktop`、版本 `0.6.0-p6d-dev`、Team `457B263L9J` 与旧包一致，严格验签通过。覆盖前后 workspace SQLite SHA-256 均为 `e5efe43fc5ee94d3fde7bbe9f9e892f17152c4bc57a1e6b4d36c83a83e24d907`；启动后 `quick_check`／`integrity_check=ok`、87 张表、1 个工作区、7,316 条搜索索引。旧包保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-native-draft-owner-20260913-1315.app`。
- **未替代：** 为保护现有真实草稿，本轮未在正式数据上代用户点击“新对话”；需要由用户手动点击一次确认输入框为空。该缺口不由自动测试、验签或启动回读替代。

## 2026-09-13：流式会话投影、最终回归与双端保数据覆盖

- **Android 流式修复：** 正常聊天前台服务现在把执行器的 `onStreamProgress` 透传为仅含会话 ID／状态的节流进度事件；ViewModel 对当前选中会话只投影已持久化的 transcript、runtime、lineage 与 attribution，不再每个增量完整重载抽屉、设置、模型目录和历史。完整重载与流式投影共享同一代际栅栏，避免旧重载覆盖新文本；终态仍做一次完整刷新。这样与 Desktop 的逐字显示保持同一可见行为，同时不把正文放进广播。
- **最终回归：** Android `:app:testDebugUnitTest :app:lintVitalRelease :app:assembleRelease` 通过；JVM 为 1,133 项、0 failures、0 errors、3 skipped。Desktop lint、typecheck、Node 378/378、Rust `cargo test` 253/253 及协议 golden 均通过；Desktop inventory 没有未处理渲染动作、未实现 Rust command 或未注册 command。91 个动作、41 个 invoke 尚无直接测试引用，属于覆盖缺口而非本轮观察到的功能故障。
- **本地 checkpoint 证据：** 跟踪文件／历史清单固化于 [review/20260913-final-checkpoint](review/20260913-final-checkpoint/)，其范围只涵盖 Git 跟踪文件和本地历史，不读取用户正文、凭据或未跟踪资产。
- **Android 覆盖：** 正式候选为 `com.nanzhufeng.ai` 66／`0.3.0-p10j`，v2／v3 验签有效、证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`、APK SHA-256 `e6807858b472ce395325729d37f484bc453ba61e14ff65f4a7b10ef74b236df8`。OPPO `3B157F009E800000` 只执行一次 `pm install -r --user 0` 并返回 `Success`；拉回 base APK 与候选逐字节一致，首次安装时间仍为 `2026-08-20 15:15:31`。未卸载、清数据、安装 Debug／测试包或运行任何 `connected*AndroidTest`。
- **Desktop 覆盖：** `/Users/nanzhufeng/Applications/南枫 AI Desktop.app` 已以相同 Bundle ID `com.nanzhufeng.ai.desktop`、版本 `0.6.0-p6d-dev`、Team `457B263L9J` 严格验签覆盖；候选及安装后主程序 SHA-256 均为 `d5c1de2bb352e476f473ae1616a4bed8f52b3d1f6012528281db44f832cf6919`。覆盖前旧包保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-stream-audit-20260913-1202.app`；正在运行的窗口须由用户自行退出后重开才会加载新二进制。
- **仍需真实边界验收：** 本轮未触发真实 Provider、Google／Supabase 账号同步或人工原生逐帧视觉验收；3 项 Android opt-in 仍 skipped。这些缺口不被构建、安装或自动测试替代。

## 2026-09-13：双端 Composer 去冗余说明与单模型附件路由（未安装）

- **行为：** Android 与 Desktop Composer 均移除发送授权／计费及附件接收方的小字，附件预览只保留可操作的内容卡；底部模型与发送动作行不再被说明文字挤出可视区域。Android 普通聊天附件路径改为：仅本机 UTF-8／PDF 文本层投影，或原始附件直达当前选中模型；当前模型不支持时不发送并提示更换支持该附件的模型。删除千问 Qwen3.7-Plus／智谱 GLM-OCR 的普通聊天中转调用；独立“南枫转写”能力不受影响。
- **验证：** Desktop Node `chat-first-ui.test.mjs` 95/95、lint、typecheck、静态 build 通过。Android `UniversalChatAttachmentBridgeContractsTest` 与 `P3JNormalChatExplicitEgressContractsTest` 定向 JVM 测试通过，`:app:assembleRelease`（含 `lintVitalRelease`）通过。
- **未替代：** 本轮没有覆盖安装 Android 或 Desktop 正式应用，也未发起真实 Provider 请求；需要在新包覆盖后，以实际图片／PDF草稿确认发送键可见、当前模型直达和不支持附件的原位拦截。

## 2026-09-13：Desktop 长会话交互性能修复与保数据覆盖

- **性能修复：** Desktop 长会话的轻量操作现在原位保留正文和侧栏，不再在菜单、弹层、模型选择等操作中重复序列化／解析整个消息树和会话列表；切换会话时保留侧栏并仅更新选中态与自动已读点。保留正文时，图片缩略图观察器不再因缩略图对象变化而失效，视频／文件预览观察器也不再重复扫描既有正文卡片。工作区确定后，独立的只读启动投影改为并行 IPC 读取。
- **自动验证：** Desktop Node `374/374`、Rust `cargo test --lib` `244/244`、lint、typecheck、静态 build 与 `git diff --check` 通过。845 个会话／500 条消息的纯渲染基准中，完整壳平均约 `133 ms`，正文与侧栏均保留的轻操作约 `0.23 ms`；该数值不替代原生端到端帧率。
- **Desktop 覆盖：** 候选严格验签后覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；Bundle ID `com.nanzhufeng.ai.desktop`、版本 `0.6.0-p6d-dev`、Apple Development Team `457B263L9J` 与旧包一致。候选及安装后主程序 SHA-256 均为 `5f4f475da68d69ac2a1a308c3729bab04d26f625bb2c477221b33a28097a4fe5`。旧包完整保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-performance-20260913-0040.app`。替换前后、启动前常规 workspace SQLite SHA-256 均为 `fd23cc8a08ca83251f806ac7c4020a4a14eb87d10dd0096531a5459169311ac7`；启动新包后 `quick_check=ok`／`integrity_check=ok`、86 张表、1 个工作区、7,310 条索引，原生窗口已回读既有长会话、附件与 Composer。
- **尚未替代：** 本次覆盖与数据回读不替代长会话实际人工操作的帧时间验收，也没有触发真实 Provider；两项仍须独立按正式原生窗口与账号条件验证。

## 2026-09-13：模型完整名称的响应式跨端显示与双端保数据覆盖

- **行为：** Desktop Composer 的普通会话与临时会话均直接显示模型目录 `displayName`，不再套用 `compactModelName`；模型触发器最小宽度为 `176px`，悬停／焦点胶囊跟随完整命中区，当前目录名称的可视上限为 `224px`。Android 外屏仍使用紧凑标签与 `88dp` 控件；内屏／展开宽度从 `600dp` 起改为目录完整名称与 `200dp` 控件。消息页脚继续使用紧凑归因，不与 Composer 的选择事实混为一谈。
- **自动验证：** Android `:app:testDebugUnitTest` 与 `:app:assembleRelease`（含 `lintVitalRelease`）通过；Desktop lint、typecheck、Node 全量 `373/373` 与 Rust `cargo test --lib` `244/244` 通过。新增 Desktop 目录标签回归覆盖当前支持名称不被缩写；Android 契约覆盖外屏紧凑／内屏完整标签与相应宽度。``git diff --check`` 对本轮文件通过。
- **Android 覆盖：** 正式候选 `app/build/outputs/apk/release/南枫AI.apk` 为 `com.nanzhufeng.ai` 66／`0.3.0-p10j`、非 Debug、v2／v3 有效，证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，APK SHA-256 为 `2b3a62ae6d06991bb69de7db4949dbf49cd938c64e995cdb2d8fd46a347d0e0d`。OPPO `3B157F009E800000` 只执行一次 `pm install -r --user 0` 并返回 `Success`；安装后拉回 base APK 与候选逐字节一致，首次安装时间仍为 `2026-08-20 15:15:31`，CE／DE inode 仍为 `1459104`／`1433378`。未使用 `adb install`、卸载、清数据、Debug／仪器包或任何 `connected*AndroidTest`；设备临时 APK 已删除。
- **Desktop 覆盖：** 新 bundle 已严格验签并覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，Bundle ID `com.nanzhufeng.ai.desktop`、Apple Development Team `457B263L9J`、版本 `0.6.0-p6d-dev` 均与旧包一致；候选与安装后主程序 SHA-256 均为 `0156e09a22473628a15deb78d6bfa8a386e5906c342ae1ee8c1faeee093d0f51`。旧 app 可恢复于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-model-label-overlay-20260913-0035.app`。替换前后 workspace SQLite 均为 `quick_check=ok`／`integrity_check=ok`、86 张表、1 个工作区、7,310 条索引、SHA-256 `5fa950345fea42c9a902716f18fb8c29cca843955f505474616824a4972e69bb`；新 bundle 已启动。
- **尚未替代：** 本轮没有在折叠内屏的实际展开状态逐像素读取，也没有触发真实 Provider。代码、构建和保数据覆盖已验；内屏视觉与真实模型调用仍须按各自设备／账号条件单独验收。

## 2026-09-12：选定对话加密同步跨端兼容与双端保数据覆盖

- **格式兼容：** Android 现在严格读取 Desktop 早期 `messages/blocks` 纯文本记录；Desktop 现在严格读取 Android 当前 `nodes/text` 记录。两端仅恢复 `conversation` 记录的文本树，Android 不导入 Desktop 相邻的安全设置／提醒计划，Desktop 对 Android 记录不凭空创建这些数据。未知记录、附件／工具结果、未完成回复、异常字段或同一 ID 的本机对话均拒绝或不覆盖。
- **Android 恢复入口：** “读取云端列表”只展示已预检的加密文档标识，不解密正文；选择条目后仅在恢复保护已就绪时恢复一条对话。恢复成功写入本机，已有同 ID 对话明确显示“未覆盖”。该链路不替换整个 Room 数据库。
- **验证：** Android 新增 Android／Desktop 两套载荷的 Robolectric 解码回归，完整 `:app:testDebugUnitTest` 通过；Desktop Node 全量、Rust `cargo test --lib`（239 项）通过，新增 Android wire→Desktop 消息树回归通过。Android `:app:lintVitalRelease :app:assembleRelease` 通过；候选 `南枫AI.apk` 为 `com.nanzhufeng.ai` 66／`0.3.0-p10j`、非 Debug、v2／v3 有效、证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，SHA-256 `e0e0e4e25efca518b61bfc67634406f188dc4ddf0202bf07825f92eb5cd5d210`。
- **覆盖：** OPPO `3B157F009E800000` 仅执行一次 `pm install -r --user 0` 并返回 `Success`；安装后 base APK 与候选 SHA-256 一致，首次安装时间仍为 `2026-08-20 15:15:31`，未卸载、清数据、安装 Debug／测试包或运行任何 `connected*AndroidTest`。设备临时 APK 已删除。Desktop 已重新构建、strict codesign 后覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；旧 app 可恢复于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-cross-sync-20260912-2350.app`。覆盖前后 Desktop SQLite SHA-256 都是 `ff9a11feb5dc64d587b94ec406fa5d9cb015dd328b2df69083866229f253fafa`，`integrity_check=ok`，新 bundle 已启动。
- **尚未闭环：** 本轮没有连接真实 Google 账号、调用真实 Supabase RPC、解密用户云端文档或触发真实 Provider；因此格式兼容、本机恢复防覆盖和正式包安装已验证，但真实云端双向恢复仍须用户在自己的账号和恢复码下手动完成一次。

## 2026-09-12：双端保数据覆盖安装——输入提示悬停左移修复与复制反馈

- Desktop Composer 的“回复 南枫AI”此前在鼠标悬停时向左偏移 4px；根因是 `#chat-composer:hover` 因更高 CSS 优先级把左右 padding 从 `12px` 覆盖为 `8px`，不是高度或滚动问题。已统一普通／hover／focus 为 `padding: 7px 12px`，新增 `FB-P6-049` 回归。独立 Playwright 渲染在 1280×720 下实测悬停前后均为 `x=315, y=620, height=36, padding=7px 12px`；Desktop lint、typecheck、build 与 `git diff --check` 通过。
- Desktop 已重新构建、严格验签并保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`。候选和安装后可执行文件 SHA-256 均为 `b84fd744eb37c1e6310006c5432cf62f8e3c89d5b4188d54a5e30e1af35ea249`，Bundle ID `com.nanzhufeng.ai.desktop`、Apple Development Team `457B263L9J` 与旧包一致；旧包完整保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-dual-overlay-20260912-2315.app`。覆盖后、启动前 SQLite SHA-256 保持 `933cdfc6799cc5b52ccc90ae7260f583e21c49a88bc2eee215d3b832c50079ae`；启动新包后 `integrity_check=ok`，工作区仍为 1、搜索索引仍为 7,294。运行时写入会改变 SQLite 哈希，不能将启动后的哈希变化误报为数据丢失。原生窗口回读到真实历史会话与 Composer。
- Android 正式 `:app:assembleRelease` 产物 `app/build/outputs/apk/release/南枫AI.apk` 已以 OPPO `3B157F009E800000` 的 `pm install -r --user 0` 同签名覆盖；未使用 `adb install`、Debug／仪器测试、卸载或清数据。安装前后均为 `com.nanzhufeng.ai` 0.3.0-p10j (66)、非 debuggable，首次安装时间保留 `2026-08-20 15:15:31`。候选与设备安装后拉回的 base APK SHA-256 均为 `dcdd83b299386741efd0276de0ef573cbbf2ec333ddab8ee6a1544722519808c`，v2／v3 签名有效、证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。正式沙箱不允许读取内部应用数据文件，因此未声称 Android 业务数据哈希；同签名覆盖、首次安装时间不变和安装后 APK 回读是本轮数据保留证据。临时拉回 APK 已移动到系统废纸篓，可恢复。

## 2026-09-12：Android／Desktop 统一复制图标成功反馈（未重打正式包）

- 复制契约统一为：写入剪贴板成功后，原复制图标在原位变为绿色勾号，保持 1.2 秒后恢复；不再在按钮旁插入“已复制”文字或触发整页重绘，因此不改变正文、代码块或表格的几何与滚动位置。
- 覆盖 Desktop 主会话消息、Markdown 代码块与表格、文本预览、转写全文、恢复码；转写全文与恢复码的纯文字“复制”按钮已改成带无障碍标签的图标按钮。Android 同步覆盖主会话动作、长按菜单、代码块、表格和文本预览；成功态均由原图标替换为 `Check`。
- 验证：Desktop 定向 Node 124/124、lint、typecheck、静态 build、`git diff --check` 通过；独立 `dist` 中实际点击主消息复制，读到勾号路径、`已复制` 标签和 1.3 秒后恢复原复制路径。Android `FB-P6-111`、`FB-P6-179` 定向 JVM 单测通过，且 Debug Kotlin 编译通过。整类 Android 合同测试仍有一条既有、与本轮无关的“多图片下载”源码断言失配（缺少 `.takeIf { target -> target != preview.id }`），未改动该功能；未构建／安装正式 Android 包、未重打／覆盖 Desktop bundle、未操作设备或本机数据。

## 2026-09-12：Desktop 加号菜单复刻 Android 图标与正文色（未重打正式包）

- 加号菜单的相机、图片、文件、基础风格、实时网页搜索改为 Android `ConversationWorkspace` 实际使用的 `PhotoCamera`、`AddPhotoAlternate`、`AttachFile`、`AutoAwesome`、`Public` Rounded 路径；不再调用 Desktop 自有线框的 `camera`／`image`／`file`／`sparkles`／`globe`。
- 菜单正常主文字在浅色主题固定为 Android `BodyText #1E2925`，图标面仍为系统浅灰圆形；橙色仅保留风格、所选风格值和开启的联网图标／开关。深色主题明确回退到自身的正文色，避免把浅色设计规则错误带入深色界面。
- 验证：C07 与相邻 chat-first UI Node 102/102、Desktop lint、typecheck、静态 build、`git diff --check` 通过；Playwright 独立 `dist` 打开加号菜单的截图已核对五个图标、容器与黑色正文。静态预览仅有既有 favicon 404；未重打／覆盖正式 Desktop bundle、未操作本机数据。

## 2026-09-12：Desktop 搜索定位的会话头部与侧栏离开语义（未重打正式包）

- 从全屏搜索定位到会话后，头部顺序改为“会话标题在左、返回搜索在右侧操作位”，不再让“搜索”占据标题主位；搜索结果定位场景仍保留返回入口。
- 用户从左侧栏主动选择任意对话时，现在会清除搜索面板、返回搜索标记、归档临时定位和消息／附件锚点，直接进入正常聊天主界面。设置内收藏／归档／回收站的返回语义不受影响。
- 验证：搜索、侧栏与对话头部定向 Node 44/44、Desktop lint、typecheck、静态 build、`git diff --check` 通过。Playwright 独立 `dist` 可打开全屏搜索；其只读视觉夹具不含文字结果对应的会话，点击时准确显示既有“结果所属会话已变化”，不能据此替代原生 SQLite 搜索定位回读。浏览器另有静态预览缺少 favicon 的既有 404；未重打／覆盖正式 Desktop bundle、未操作本机数据。

## 2026-09-12：Desktop 回复中的 LaTex 公式可读化（未重打正式包）

- 根因是安全 Markdown 投影将 `\\[`／`\\]`、`\\rightarrow` 和上下标语法按普通正文转义，导致导入或新生成回复中的公式边界、箭头及下标裸露。现在显示公式支持 `\\[ ... \\]` 与 `$$ ... $$`，行内公式支持 `\\( ... \\)` 与 `$ ... $`；常见箭头、比较、运算符、希腊字母和上下标投影为安全的可读 HTML，原始 HTML 仍先转义、不会执行。
- 显示公式以居中数学排版呈现，可横向滚动以避免窄窗口裁切；行内公式不再破坏正文行高。未知 LaTex 指令保留可见文本，不猜测或执行扩展语义。
- 验证：Markdown 定向及相邻导入回归 19/19、Desktop lint、typecheck、静态 build、`git diff --check` 通过。独立 `dist` 预览确认应用正常加载、无框架错误，唯一 console error 为静态预览缺少 favicon 的既有 404；未重打／覆盖正式 Desktop bundle、未操作本机数据。当前静态预览夹具不含公式消息，公式视觉采用渲染契约验证，正式历史会话中的同类消息仍待原生 bundle 回读。

## 2026-09-12：Desktop 折叠侧栏的图标展开与底部圆形动作（未重打正式包）

- 折叠轨道由 76px 收紧为 72px。展开态仍保留独立收起箭头；折叠态不再显示独立箭头，南枫 AI 图标本身是带 `展开导航栏` 标签的 48px 按钮，点击后恢复完整侧栏。
- 折叠态底部动作改为纵向 44px 圆形：橙色“新对话”在上，白色“设置”贴底。设置不再继承展开栏的 96px 胶囊，也不再因折叠规则被隐藏；圆形表面、阴影、悬停和键盘焦点均沿用既有按钮体系。
- 验证：定向 Node 9/9、Desktop 静态 build 通过；Playwright 在独立 `dist` 预览执行“展开侧栏 → 折叠 → 点击图标展开”，DOM 与截图均确认两种终态。浏览器唯一 console error 是独立预览缺少 favicon 的 404；未重打／覆盖正式 Desktop bundle、未操作真实本机数据。

## 2026-09-12：Gemini 3.8 Flash 双端替换（未做真实服务或正式包验收）

- 当前 Android 与 Desktop 的 Gemini 日常预设、手动选择、Auto 路由和紧凑显示均改为 `Gemini 3.8 Flash`／`Gemini 3.8`，请求 ID 为 `google/gemini-3.8-flash`。官方 Google 与 OpenRouter 页面已核对该模型及 OpenRouter 介绍期 `USD 0.75 / 3.75 / 0.075`（输入／输出／缓存读取，每百万 token）本地只读估算；服务商实际账单仍优先。
- Android 读取旧 `GEMINI_3_7_FLASH` 设置时迁到 3.8；Desktop 读取旧 SQLite `desktop_provider_settings` 记录时原地替换为 `GEMINI_3_8_FLASH` 并递增 revision。旧会话的模型归因和旧 3.7 账单估算保持原样，不能把历史事实伪造成 3.8。
- 验证：Desktop Node 定向 100/100、Rust 设置迁移 1/1；Android JVM 合同 59/59。`cargo fmt --check` 仍被既有 `desktop_account_sync_v1.rs`、`desktop_docx_preview.rs` 与 `lib.rs` 的非本轮格式差异阻断；本次所改文件的 `git diff --check` 已通过。未请求真实 Provider、未构建／安装正式 Android 包、未重打或覆盖 Desktop bundle，也未操作设备。

## 2026-09-12 最新：附件锚定菜单 / 搜索结果缓存 / 深灰媒体底

- 最新正式包已覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包在 `nanfeng-menu-position-backup-0zpMgx`；替换前后 SQLite SHA256 `f945f223408210d27b4189ef0962a3531f19f0bf1e6f4db2ddb9fe853813e2c1` 一致。Node 全量、Rust 图片路径并发安全回归、macOS bundle、strict codesign 通过。
- 搜索右键/长按改小菜单：仅文件名、快速定位、删除。背景透明无 blur；点外/Esc 关闭。坐标先由附件 DOM 边界取得，必须在插入后通过 element.style.left/top 设置；HTML inline style 被 `style-src 'self'` 阻止是前两版菜单飞到左侧的根因，不放宽 CSP。最新正式视频卡右键实看已锚定对应第三张卡，Esc 通过，快速定位到所属对话通过。
- 主会话附件右键/长按新增信息卡：文件名、类型、已有时长、大小、发送时间、搜索定位/下载/分享；正式视频右键入口及三按钮已回读。时长缓存未命中时当前会省略时长，尚需补按需读取；下载/分享系统终态未执行，不能称完整验收。
- 媒体统一 `--attachment-media-canvas:#303330`，搜索音频与视频/预览共用；尚需用户同视口确认色感。
- 图片读取从 store mutex 迁到 paths worker（原来解码缩放全程占锁）；真实图像校验、缺失文件、持锁时路径读取并发测试通过。
- `search-result-cache.mjs` 有界 12 页：复用结果即时呈现，空查询正文/图片可从已读全部投影，后台仍核对 DB；workspace/current summary 变化清缓存，更新失败标明上次结果。真实图片页显示 1481 项、无整屏等待；正文/图片连续点击已执行，未完成量化 P95 性能验证。不要称所有搜索卡顿已解决。
- 还需处理：缓存失效必须进一步核对所有写入；所有附件类型主菜单时长/下载/分享、长按手势、原位保持；完整手机端差异审计及本清单其余项目仍未完成。最近测试日志 `/tmp/nanfeng-csp-menu-tests.log`。

## 2026-09-12 晚：用户全部反馈与手机端遗漏审计仍未完成

- 最后追加已交付：音量弹层复用播放栏半透明材质/模糊 token、999px 胶囊、无边框重阴影、居中对齐按钮；紧凑竖条保留。已再次保数据覆盖并在正式 `1000160321.mp4` 暂停画面实看通过。最新旧包备份 `nanfeng-volume-surface-backup-brrwZG`；替换前后 DB SHA256 均 `bc6336034a289e62649713eec5b85a7481d246451a073302d0e8c6a3ea406a0b`。全量 Node、macOS build、strict codesign 通过；下文 rENmA2 为上一版备份。

- 当前事实与后续逐项入口见 `docs/DESKTOP_FEEDBACK_CHECKLIST_20260912.md`；其中未勾选项不得沿用下方历史“闭环”措辞。
- 最新已覆盖正式 Desktop：音量使用共享喇叭图标（原本地 icons.audio 不存在）、紧凑竖条 44×128 / 滑块 16、inert 安全 Markdown 预览。视频实看图标及竖条通过；真实 MD 标题和音频右键菜单、删除取消已实测。Node 全量、macOS bundle、strict codesign 通过，替换前后 DB hash 相同，备份在 `nanfeng-volume-backup-rENmA2`。
- 仍需继续：全分类性能计时及真实长按/定位、普通图片无灰底、全手机代码跨入口审计、Android 恢复与安全等；本轮没有真实同步/上传/删除。
- 下一最小缺陷：来源列表的本地 `icons.globe` 同样缺失，切共享图标并加图标引用门禁；随后继续附件验收矩阵。当前 CUA 正式应用停在已暂停的视频预览，小音量弹层展开。
- 上下文闸门 HANDOFF，保留当前脏工作区，不重做已覆盖项、不启动 Android 仪器测试、不把局部完成说成全部完成。

## 2026-09-12：Desktop 视频预览打开即播放并删除重复入口

- 根因是 Desktop 视频预览仍沿用“打开预览后，再点一次开始本地播放”的旧交互合同；这与附件卡点击已经表达播放意图相冲突，也导致左上角多出无意义的二次入口。
- 现在视频预览打开后会先恢复上次播放位置，再主动调用原生播放器 `play()`；同时保留系统播放／暂停、进度和音量控件作为操作与自动播放失败时的回退。左上角“开始本地播放”按钮及其事件分支已删除，标题、关闭按钮和底部文件名／大小／时长未改。
- 新增回归门禁，要求视频元素具备 `autoplay`／`playsinline`，并禁止重新出现 `开始本地播放`、`start-video-preview` 或同类重复提示。Desktop 全量 Node 326/326、lint、typecheck、静态 build、macOS bundle 和严格验签通过；唯一警告是既有未使用的 `desktop_storage_location::resolve`。
- 已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；候选包与已安装可执行文件 SHA-256 均为 `8b8711cda35e271519f2728e7b9708d8c12d2c2c2bc52d38d1c011def5e44821`，旧包保留为 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-video-autoplay-20260912-1131.app`。覆盖后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引、307,867,648 字节。
- 正式原生窗口已从视频搜索打开截图同款 `1000160003.mp4`：播放器从保存的 `0:02` 自动继续，回读时已到 `0:05` 且原生控件显示 `Pause`；可访问性树中不存在左上角旧播放按钮或文案。

## 2026-09-12：Desktop 主会话附件改为真实内容预览

- 主会话附件原先只有图片走真实缩略图；视频、PDF、文本、音频和其他文件仍落到通用灰色上传图标。现在主会话与草稿复用同一个本机附件呈现 owner：图片显示真实缩略图，视频显示真实首帧和居中播放键，PDF 显示真实首页，Markdown／文本显示内容摘要，音频显示深色 MP3 卡、时长和轨道，DOCX 等不可内联渲染类型显示真实格式标识。
- 真实视频仍发灰的第二层根因是 macOS `qlmanage` 会对手机导入的 MP4 无限挂起，既不返回缩略图，也不退出。视频帧 owner 现在优先调用本机 FFmpeg 解码 0.2s 帧，两条解码路径均有硬超时、精确 kill／wait 和格式校验；即使解码器异常也不再长期占用进程或拖住界面。真实 `1000159306.mp4` 已成功产生 `360×640` PNG 预览帧。
- 新增 `FB-P6-181` 覆盖图片／视频／PDF／文本／音频／DOCX 六类主会话卡片，并增加“解码器挂起必须退出”Rust 回归。Desktop 定向 3/3、相邻用例 120/120、Node 全量 326/326、Rust 全量 234/234、lint、typecheck、静态 build、macOS bundle 和严格验签通过；唯一警告是既有未使用的 `desktop_storage_location::resolve`。
- 已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，候选包与已安装可执行文件 SHA-256 均为 `0eee4689e63f315b49cb853cfee1b3e72deda2204a8d4d6450903d075bee27ad`，签名为 `Apple Development` / Team `457B263L9J`。覆盖后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引、307,867,648 字节；旧包保留为 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-main-attachment-previews-20260912-0932.app`（任务前版本）和 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-main-attachment-ffmpeg-20260912-0941.app`（中间版本）。
- 正式原生窗口已在手机导入的真实历史会话中回读：`视频分析说明` 顶部显示 `1000159306.mp4` 真实画面与播放键，`已确认事实（通话内容逐句梳理）` 显示 MP3／0:49／轨道，`Codex秒退根因分析` 显示 MD 标识与真实 Markdown 摘要。另有真实图片会话和自动化六类合同共同覆盖图片／PDF／不支持格式。

## 2026-09-12：Desktop Google 登录改为 Supabase 托管 OAuth + PKCE（待用户选择账号后终态回读）

- 撤回此前“独立 Desktop OAuth Client + loopback 已打通登录”的结论。该方案只修复了 Google 授权页的 `redirect_uri_mismatch` 和本地 callback 读取，却仍由 Desktop 直接请求 Google Token endpoint。对同一公开 Desktop Client 做不含真实凭据的受控坏码探针后，Google 明确返回 `invalid_request: client_secret is missing`；应用原先用 `error_for_status()` 丢弃响应体，界面只能看到笼统的“Google Token 被拒绝”，因此此前没有定位到真实失败点。
- Android 继续由 Credential Manager 取得带 nonce 的 Google ID token，再交给 Supabase `grant_type=id_token`；Desktop 无 Credential Manager，现改为 Supabase 托管 Google OAuth：系统浏览器打开 `/auth/v1/authorize?provider=google`，Desktop 只生成 state 与 PKCE verifier/challenge，本机 callback 收到 Supabase auth code 后再调用 `/auth/v1/token?grant_type=pkce` 建立南枫云会话。Google Web Client Secret 只留在 Supabase 服务端，不读取、不打包、不要求用户提供。
- Supabase `NanFengCloud` 项目已新增并回读受限 redirect allowlist：`http://127.0.0.1:**/oauth/callback**`，只允许 loopback 与固定 callback path，以支持 Desktop 每次随机端口。打包与 Rust 配置已删除 Google Desktop Client ID 依赖，只要求公开 Supabase URL 与 publishable key；登录网络等待仍在 SQLite 锁外，失败不写会话、不改变本机数据或云端数据。
- localhost OAuth mock 已先红后绿，证明授权 URL、state、S256 challenge、callback code、PKCE token exchange、apikey 与会话持久化完整连通，且请求与 bundle 不含 Google Client Secret／Desktop Client ID。Desktop 全量 Node 325/325、lint、typecheck、Rust 233/233、macOS bundle 与严格验签通过；唯一警告为既有未使用的 `desktop_storage_location::resolve`。
- 最新正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-supabase-oauth-pkce-20260912-1008.app`。覆盖后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引、307,867,648 字节；当前真实运行已从应用进入 Google 账号选择页，待用户本人选择账号后继续回读 Supabase 会话和应用内登录终态，不能只凭到达账号选择页宣称闭环。

## 2026-09-12：Desktop 旧手机导入对话回答菜单的惯性滚动竞态修复

- 撤回此前“SVG 子节点冒泡是完整根因”的结论。数据库审计确认 845 个会话、5,135 条消息的 ID 均非空且全局／会话内无重复；实际差异来自旧手机导入对话通常很长、必须滚动，而新建短对话通常没有这条路径。WebKit 在惯性滚动期间可能吞掉后续 `click`，同时原实现监听任意对话 `scroll` 并关闭菜单，残余惯性或程序化滚动会把刚打开的菜单立即关掉。
- 回答三点按钮现在由主键 `pointerdown` 直接打开，键盘无指针 `click` 继续作为可访问回退；普通 `click` 不会重复切换。菜单不再因裸 `scroll` 关闭，只在新的对话区滚轮手势、点外、Esc 或窗口尺寸变化时关闭。
- 新增两条回归门禁；先稳定得到 2 个失败，再完成修复。Desktop 全量 Node 322/322、lint、typecheck、静态 build、macOS bundle 和严格验签通过。候选与已安装可执行文件 SHA-256 同为 `806c23ab330ac46019f7964264c3b0b4091be127b7fe9084276ccfa204519ff7`。
- 已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-assistant-menu-inertia-fix-20260912-0845.app`。原生应用在 8 月超长手机导入对话内连续打开 3 个不同回答菜单，并在滚动后立即点击通过；另取 9 月手机导入对话复测滚动后立即点击也通过。覆盖后 SQLite `integrity_check=ok`、86 张表、845 个会话、7,297 条本地搜索索引，数据库大小保持 307,867,648 字节。

## 2026-09-12：双端复制成功勾号固定在复制按钮左侧

- 根因是 Desktop 的结构化内容复制按钮使用绝对定位，而成功勾号被插入按钮之后并参与普通文档流，因而掉到整个内容框左下角；Android 的消息、信息块和表格复制行也把勾号排在复制按钮之后，与用户要求的方向相反。
- Desktop 现在统一将临时勾号插在对应复制按钮之前；代码块／表格勾号与复制按钮共享相对定位 owner，固定在按钮左侧且不改变内容布局。消息复制成功不再触发一次无意义的整页重绘。Android 的消息操作行、信息块和 Markdown 表格也统一调整为“勾号 → 复制按钮”。
- Desktop 定向 93/93、全量 Node 322/322、lint、typecheck、静态 build、macOS bundle 与严格验签通过；正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-copy-check-alignment-20260912.app`。原生应用点击表格复制按钮后已实看到绿色勾号紧贴按钮左侧；SQLite 覆盖后 `integrity_check=ok`、86 张表、7,297 条本地搜索索引。
- Android 源码与契约测试已同步更新，但本轮 Gradle 在编译前被既有 dependency-verification 门禁拒绝：`kotlinx-coroutines-bom-1.8.0.pom` 缺少校验记录；未绕过校验、未构建安装包、未操作主设备。

## 2026-09-12：Desktop 搜索所有分组统一去重信息层级

- 用户明确要求其他搜索分组也遵守同一标准，不重复无价值内容。现在正文卡只保留会话标题、命中正文片段和右下角大小／时间；文件与音频卡只保留一次文件名、类型图标和右下角大小／时间；图片与视频继续以预览为主，只保留一次名称和右下角大小／时间。不会再在卡片内显示 MIME、应用内预览能力术语、来源标签或附件关联的会话正文。
- 附件点击预览能力未删除：入口仍进入既有 app-owned preview owner，安全边界与不交给系统打开的约束不变；只是从搜索卡的可见信息中移除了工程化提示。分类、文件类型筛选、全局排序、分页、右键／长按菜单和索引数据均未改。
- 新增去重契约并更新 C08／本机数据搜索契约，定向 43/43 与 Desktop 全量 Node 313/313、lint、typecheck、静态 build、macOS bundle 与严格验签通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-search-card-dedup-20260912.app`；覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引，应用已启动。
- 本环境无 Browser 插件与本地 Playwright，不能取得搜索页截图级读回；原生启动、数据连续性及所有渲染契约已验证，像素级搜索页复验仍待可用浏览器或人工在应用内打开搜索页。

## 2026-09-12：Desktop 搜索卡片的大小／日期统一右下对齐

- 用户要求搜索界面所有卡片的“大小 · 日期”统一显示在右下角。正文卡现将元信息从标题右侧移为内容末行的右对齐槽位；通用文件／音频卡通过卡片纵向 owner 将事实栏推至右下；图片与视频卡保留名称在预览图下方左侧、将同一事实行与名称底边对齐到右侧。分类、排序、预览尺寸、标题、摘要和菜单入口均未改。
- 新增静态布局契约覆盖正文、通用附件、图片和视频四条卡片渲染链，避免以后单独改某类卡片又让事实栏漂回标题侧。
- Desktop 全量 Node 312/312、lint、typecheck、静态 build、macOS bundle 与严格验签通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-search-card-facts-corner-20260912.app`；覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引，原生应用已启动并读取到现有会话。
- 本环境没有 Browser 插件且本地无 Playwright，故未生成搜索页的浏览器像素截图；原生自动化可确认应用启动与数据连续性，搜索卡片的截图级复验仍待在可用浏览器或应用内打开搜索页后完成。

## 2026-09-12：Desktop 启动图标主体小幅放大

- 用户只要求 Desktop 图标的白色马头主体再放大一点；保留原有橙色材质、圆角、居中关系与 Android 图标资源不变。macOS 专用导出从原 `1.23×` 提升至 `1.30×`，即相对上一版再增加约 `5.7%`；1024px 母图未被重绘或改色。
- `icon.png`、`nanfeng_ai_icon_rgba.png` 与 `nanfeng_ai_icon.icns` 均由同一 macOS 导出脚本重新生成。静态审计确认 Tauri 配置仍指向该 `.icns`，并确认候选包解出的 1024px 图像与源码 `.icns` 的对应尺寸逐字节相同。
- Desktop lint、typecheck、全量 Node 311/311、macOS bundle 与严格验签通过。候选与已安装包均为 `com.nanzhufeng.ai.desktop`／团队 `457B263L9J`；已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-icon-subject-scale-20260912.app`。覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；应用已启动。
- 当前自动化可确认运行中的原生应用与已安装资源，但无法读取 Dock／Finder 的系统级像素缓存；因此尚未取得真实 Dock 表面的截图级复验。

## 2026-09-12：Desktop 视频搜索卡片改为预览帧优先

- 根因与图片结果相同：视频复用通用附件卡，只显示 46px 播放图标，再重复显示文件名、MIME、应用内预览提示和索引命中的会话正文；同时搜索结果此前没有视频帧缩略图 owner。
- 视频结果现使用独立卡片：176px 预览帧与居中播放标识为主体，底部只保留一次名称和一行“大小 · 时间”，三点操作保留；不再渲染正文摘要、MIME、来源标签或“应用内视频预览”。
- 新增受限的 macOS Quick Look 缩略图 owner：卡片进入可见范围后才对该 workspace 已验证的 MP4 生成一张最大 640px PNG，临时文件立即清理，网页层仅得到这张受限 PNG，不得到视频字节、路径、URI 或解码控制。缩略图失败时保留视频占位图，不影响打开已验证的本地视频。
- 搜索卡回归 12/12、lint、typecheck、Rust 定向视频 owner 测试、macOS bundle 与严格验签通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-video-search-focus-20260912.app`；覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引。应用已重启；本轮未取得视频图片分类的截图级读回。

## 2026-09-12：Desktop 图片搜索卡片改为图片优先

- 根因是图片结果复用了通用附件卡：缩略图固定为 46px，同时将 MIME、应用内预览能力、来源标签和索引命中的会话正文一起渲染，文件名还会在摘要内重复出现。
- 图片结果现使用独立卡片：176px 等比完整预览为主体，底部只保留一次文件名和一行“大小 · 时间”；三点操作保留。图片卡不再显示正文摘要、MIME、来源标签或“应用内图片预览”提示。其他类型附件维持原有事实展示。
- 图片卡定向回归 11/11、lint、typecheck、macOS bundle 与严格验签通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-image-search-focus-20260912.app`；覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引。应用已重启；本轮自动化取得窗口与当前会话树，但尚未能安全驱动无语义搜索入口至图片页做截图级读回。

## 2026-09-12：Desktop “来源网站”列表改为真实可打开链接

- 根因是来源行仅使用 WebView 内的 `<a target="_blank">`；在 Tauri Desktop 中这不等价于交给 macOS 默认浏览器，因而用户看到可读的卡片却不能可靠打开对应网页。
- 保持原有来源弹窗、行高、文字和关闭方式不变，来源行现为具名按钮：点击后调用最小权限的原生命令，将已校验的 HTTP／HTTPS URL 交给 `/usr/bin/open`。该命令拒绝 `file:`、脚本协议、无主机名地址及含账号／密码的 URL；链接页不接收任意命令或本地路径。
- `chat-first-ui` 定向 93/93 通过；Rust URL 安全单测 1/1 通过；macOS bundle 严格验签通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-source-link-open-20260912.app`；覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引。应用已重新启动；当前自动化只能读取到原生窗口层，尚未取得来源行点击后的浏览器读回。

## 2026-09-12：Desktop Google 网页登录回调修复（待用户完成账号授权）

- 根因有两层：Google Cloud 的 `NanFengCloud` 项目此前仅有 Android 与 Supabase Web OAuth Client，Desktop 把后者用于随机 `127.0.0.1` loopback 回调，Google 因而在授权码返回前报 `redirect_uri_mismatch`；另一个连带缺陷是 listener 为等待连接设为非阻塞后，已接受的回调流没有恢复为受超时约束的阻塞读取，可能在浏览器刚连接时偶发“OAuth callback 无法读取”。
- 已在同一 Google Cloud 项目创建独立的“南枫 AI Desktop”Desktop app OAuth Client；仅将公开 Client ID 写入被 Git 忽略的 `local.properties`，没有读取、复制、打包或显示 Client Secret。`bundle-macos.mjs` 现将该 Desktop Client 设为必填，缺失时直接停止打包，避免再次覆盖一个无法网页登录的正式包。
- `callback_code` 现在只对 listener 轮询；接受到受信任 loopback 连接后恢复阻塞读取、设置 10 秒读超时，并累计到完整请求行再解析。状态校验、PKCE、nonce、短暂 SQLite 持锁持久化和失败不写入本机／云端的既有边界不变。
- 先复现 Rust localhost OAuth mock 的 `OAuth callback 无法读取` 红灯，修复后相关 Rust 7/7 通过；Node 309/309、lint、typecheck、macOS 签名 bundle 均通过。候选和正式包验签为同一 `com.nanzhufeng.ai.desktop`／团队；两次保数据覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引。旧包保留为 `南枫 AI Desktop.pre-desktop-oauth-20260912.app` 与 `南枫 AI Desktop.pre-oauth-callback-read-20260912.app`。
- 正式应用已实测“使用 Google 登录”打开 Google 账号选择页，使用独立 Desktop Client 和一次性 `127.0.0.1` 回调端口，未再出现 redirect mismatch。当前账号选择／同意授权必须由用户本人完成；完成后需回读 Desktop 的 Supabase 会话与应用内登录态，不能仅凭网页跳转宣称登录闭环。

## 2026-09-12：Desktop Composer placeholder 悬浮文字跳动

- 用户报告空 Composer 内“回复 南枫AI”在鼠标悬浮时跳动，且明确要求不改变输入框布局。排查确认悬浮事件本身不触发 `render()` 或 `resizeComposer()`；后者只在输入和初始化执行。渲染页中 hover 前后 textarea 盒模型已固定为 `890×36`，原有 CSS 也没有 hover 版的边距／高度差。
- 根因在文字度量未由 Composer 自身完整锁定：textarea 与 placeholder 仍保留 WebKit 的 `text-size-adjust:auto`，而仅锁了外框与部分字号。现仅为 `#chat-composer` 及其 `::placeholder` 固定既有 `14px / 400 / 22px` 与 `-webkit-text-size-adjust: 100%`／`text-size-adjust: 100%`，并将 placeholder opacity 固定为 1；未改宽高、padding、圆角、阴影、栅格或按钮。
- `FB-P6-049` 新增的 hover 字体度量契约先失败后通过；Desktop 全量 Node 309/309、lint、typecheck、静态 build 与 macOS bundle 通过。Playwright 页面在真实 hover 状态下复测文字度量保持 `14px / 400 / 22px / 100%`，textarea 仍为 `890×36`，无控制台错误（之前仅有 favicon 404，静态服务环境不含 favicon）。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包可恢复于 `南枫 AI Desktop.pre-composer-placeholder-stability-20260912.app`；覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引，原生应用已启动。

## 2026-09-12：Desktop 南枫转写／定时任务恢复聊天工作台分栏

- 根因是 `app.mjs` 与 `chat-shell.mjs` 将两个工具入口归入 `utility-standalone`，直接返回独立满屏 canvas；这同时移除了对话侧栏，并让工具页自己的灰色页面底露在白色内容卡周围。
- 现统一为宽屏工作台：左侧始终保留标准对话导航、搜索和固定工具入口，右侧主面板显示“南枫转写”或“定时任务”。两个入口从任何页面进入都会取消工作导航模式；工具页标题改为右侧左对齐标题，不再显示冗余关闭按钮。转写、定时任务的主面板和滚动容器均使用前景白底，避免露出独立灰色外框；项目／知识／记忆原有的独立工作区不受影响。
- 更新后的分栏契约先失败后通过；Desktop 全量 Node 308/308、lint、typecheck、静态 build 和 macOS bundle 均通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包可恢复于 `南枫 AI Desktop.pre-utility-split-pane-20260912.app`。覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；原生应用实际打开“南枫转写”和“定时任务”后均读取到对话导航与右侧工具内容。

## 2026-09-12：Desktop Composer 加号面板按 Android 实现对齐

- 用户要求 Desktop 输入框左侧加号面板完整参考手机端。Desktop 已有相同的动作与会话偏好 owner，差异只在视觉几何：相机／图片／文件现在与 Android 一样使用 52px 行、32px 中性圆形图标面和 18px 图标；“基础风格和语气”与“实时网页搜索”使用各自 56px、16px 圆角的内嵌行，当前风格值为主题色；联网图标、58×28 开关、21px滑块与 30px 行程同步 Android `SettingsSwitch`。根面板保持 Android/合同指定的约 280px 宽、22px 圆角、锚定 Composer、点外关闭、Esc/Back 子层返回和焦点恢复。
- 动作顺序仍为相机、图片、文件、基础风格和语气、实时网页搜索；风格二级页继续仅含默认、直言不讳、专业可靠、亲和友善、高效务实、风趣搞笑及一个当前勾选。普通会话的风格／联网覆盖持久化与新会话首条消息的原子落库不改；临时聊天仍不显示无法生效的两项。
- 更新后的 C07 几何合同先红后绿；Desktop 全量 Node 308/308、lint、typecheck、静态 build 与 macOS bundle 通过。候选及覆盖后的应用严格验签，已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-composer-add-parity-20260912.app`。覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；原生应用已启动。当前 CUA 只读到窗口层，尚未取得“＋”已展开的原生像素读回。

## 2026-09-12：Desktop 对话头部操作胶囊阴影减半

- 用户指定只减弱截图中“新对话／更多”头部操作胶囊的阴影。该组件的独立投影由 `chat-header-content-actions` owner 提供，现从 `rgb(0 0 0 / 10.59%)` 精确减半至 `rgb(0 0 0 / 5.295%)`；偏移、模糊、44px 按钮命中区、分隔线和其他浮层阴影均未改。
- 先更新两条组件契约使其在旧投影下失败，后通过；Desktop 全量 Node 308/308、lint、typecheck、静态 build 和 macOS bundle 均通过。候选及覆盖后的应用严格验签，已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-header-action-shadow-20260912.app`。覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；原生应用已启动。

## 2026-09-12：Desktop 会话选中行标题让位收紧

- 会话行悬停／键盘聚焦时，原实现只将日期设为不可见，网格中的日期列仍占宽；标题因此同时给隐藏日期和右侧三个操作让位，过早被截断。
- 现在日期在操作出现时直接退出布局，标题只保留三个既有 32px 操作按钮所需的 96px 宽度。置顶、收藏、更多三个按钮的尺寸、命中区和键盘显示行为均未改变。
- `FB-P6-028` 已先红后绿；Desktop 全量 Node 308/308、lint、typecheck、静态 build 和 macOS bundle 通过。候选及覆盖后的应用均严格验签，已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-sidebar-title-space-20260912.app`。覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；原生应用已启动。

## 2026-09-12：Desktop 附件预览统一暗底画布

- 用户指出全屏视频预览的白色标题区与竖屏视频两侧灰底割裂。预览共享 owner 现在用 `#101310` 作为完整暗底：遮罩、全屏 dialog、标题区、图片画布、视频 letterbox 留白统一同色；图片和视频帧保持原始像素，不加滤镜。
- 关闭按钮、标题、翻页／播放控件、文件事实和错误文案改为浅色高对比；PDF、文本和音频仍沿用各自真实内容呈现，但它们的外层预览画布也不再露白。
- 新增 `FB-P6-180`，先因缺少共享暗底失败、再通过。Desktop 全量 Node 308/308、lint、typecheck、静态 build 与 macOS bundle 通过；候选及覆盖后的应用均严格验签，已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-dark-preview-20260912.app`。覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；原生应用已正常启动。本轮尚未取得已打开视频／图片时的截图级读回。

## 2026-09-12：Desktop 附件预览去除重复提示与图片工具栏

- 用户指出视频预览的“不会自动播放／上传／外发”、点击提示和图片预览“缩小／适应／放大”工具栏都是无意义噪声。附件预览现只呈现能直接操作或判断内容的事实：图片保留原图、多图切换与手势缩放；PDF 保留页码；视频保留播放、续播位置和文件名／大小／时长；文本保留文件事实和复制／保存／分享。
- 同类清理覆盖图片、PDF、视频、文本加载和完成态的重复“本机私有副本／不会外发／不会执行”说明，并移除全屏搜索空态的“本页面不会外发内容”。错误状态、无障碍加载状态、文件事实及可能改变结果的操作没有删除。
- 先新增视频去除重复提示、图片无工具栏、PDF／文本无重复安全说明和搜索空态回归；旧代码下 `chat-first-ui` 92 项中 2 项如预期失败，清理后该组 92/92 通过；全量 Node 307/307、lint、typecheck、静态 build、macOS bundle 与严格验签均通过。已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；原生读回用户截图中的 `1000159312.mp4` 仅有“开始本地播放”、续播位置和 `1000159312.mp4 · 23880.0 KiB · 9:41`，图片预览仅有标题、关闭和原图。覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条索引；旧包保留为 `南枫 AI Desktop.pre-preview-noise-20260912.app`。

## 2026-09-12：Desktop 搜索分类胶囊与点击卡顿

- 用户截图中的“图片”选中项仍可见为圆角矩形。根因是全屏搜索 tab 的 `999px` 不是强制形状合同，容易被通用按钮层级重新解释；现将该组的选中面、裁切与所有交互状态固定为 `border-radius: 999px !important`。
- 点击分类的真实瓶颈有两层：前端即使已标记 loading，仍同步完整重建搜索 DOM；Rust 又在每次查询时读取、反序列化并计算所有 workspace exchange 的语义哈希。当前数据实测为 1 个工作区、7,297 条索引、7,642,533 字节 exchange JSON，故每次点击都会重复这段无关工作。
- 分类点击现在先原地切换胶囊，下一动画帧再执行索引请求；健康索引只以 `workspaces.semantic_hash` 与 `desktop_local_search_index_state` 比较，只有变更、缺失或版本不一致才读取 exchange 并重建。快速连续点击保持 generation 防旧结果回写。
- 新增前端胶囊／下一帧查询回归及 Rust “健康索引不重解析 exchange”回归：前端定向 10/10、Rust 定向 1/1、Node 全量 306/306、Rust 全量 231/231、lint、typecheck、静态 build、macOS bundle 与严格验签均通过。
- 已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app` 并在真实本机搜索页回读：`图片 → 视频` 后“视频”选中且结果为 66 条；自动化点击返回为 167ms（含原生自动化开销，不作为端到端性能基准）。覆盖后 SQLite `integrity_check=ok`、86 张表、7,297 条索引。旧版本保留为 `南枫 AI Desktop.pre-search-pills-20260912.app` 与 `南枫 AI Desktop.pre-search-pills-close-20260912.app`；未卸载或清除数据。

## 2026-09-12：Desktop 工作根页被聊天栅格压窄

- 用户截图中“工作”标题被拆成竖排、右侧出现大面积空白。根因是 `renderChatFirstShell` 已将工作根页渲染为独立 `workspace-shell`，但 `app.mjs` 只在项目／知识／记忆存在 `workPanel` 时才给外层应用 `workspace-standalone-shell`；工作根页仍被聊天三列栅格分配到左侧单列。
- 新增唯一的 `isWorkspaceRoot(data, pane, selectedWorkProjectId, selectedConversationId)` 判断，Chat shell 与外层 App 都消费它。工作根、项目、知识、记忆现在统一使用全宽 standalone owner；返回路径、工作导航、项目对话与 Composer 语义不变。
- 新增 C15 回归先因缺少根页 shared owner 失败、再通过；C15 定向 10/10、Node 全量 305/305、lint、typecheck、静态 build、macOS bundle 和严格验签通过。已保数据覆盖并正常启动 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；SQLite `integrity_check=ok`、86 张表。原生读回确认“工作”横排居中，右侧内容画布占满剩余窗口。替换前版本保留为 `南枫 AI Desktop.pre-work-root-20260912.app`，未卸载或清除数据。

## 2026-09-12：Desktop 一键置底圆圈黑边

- 用户截图中的深色圆圈边不是阴影：`desktop/src/chat-shell.css` 的 `.chat-scroll-to-latest` 自身硬编码了 `border: 1px solid var(--chat-border) !important`。此前“更多操作”、模型 sheet 与窗口记忆的改动均未触及该控件。
- 现在改为 `border: 0 !important`，保留白色圆形表面、40px 命中区、原有柔和阴影与箭头；滚动显示阈值、平滑置底和 Composer 聚焦均未改。
- 现有 FB-P6-043 回归先因缺少无描边行为失败、再通过；Node 全量 304/304、lint、typecheck、静态 build 和重新生成的 macOS bundle 均通过，现装与候选的 Bundle ID／签名团队一致，覆盖后的应用严格验签。
- 已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app` 并正常启动；覆盖前后工作区 SQLite `integrity_check=ok`、86 张表，数据库大小仍为 307,867,648 bytes。旧 `.app` 完整保留在同目录 `南枫 AI Desktop.pre-20260912.app`，未卸载或清除任何数据。原生窗口读回确认“到最新消息”仅保留柔和阴影、无深色硬边圈。

## 2026-09-12：Desktop 窗口记忆缺失的根因与修复

- 用户反馈“窗口记忆没有”。核对后确认此前不是写入失败：`tauri.conf.json` 只有固定 `1440×900` 默认值，仓库唯一相关测试还明确断言“自动记忆之前的默认窗口尺寸”；Rust 没有窗口状态 owner、原生移动／缩放事件订阅或启动恢复路径。
- 新增 `desktop_window_state_v1`：正常前台启动时在应用配置目录读写设备本地 `window-state.json`，记录普通窗口位置、内部尺寸和最大化状态；移动、缩放和关闭事件节流／原子写入。该状态明确不进入 workspace SQLite、导入导出、备份或加密同步。
- 启动只应用合法尺寸，位置必须仍至少有标题栏区域处在当前任一显示器；外接屏拔除、记录损坏或配置目录不可用时自动保留默认启动，不能让窗口跑到屏外或阻断应用。UI/schema 诊断与后台周期不触碰正式窗口记忆。
- 先新增 `desktop-window-default` 红灯（缺少 owner 文件），后转绿；Node 全量 304/304、lint、typecheck、Rust 全量 230/230、静态 build 与 macOS 候选 bundle 均通过，候选包已严格验签。`cargo fmt --check` 仍因工作树既有 `lib.rs`／`usage_ledger_v1.rs` 非本轮格式差异失败；仅格式化新增 owner 文件。未覆盖安装，也未取得本轮“移动／缩放／退出／重开”原生读回。

## 2026-09-12：Desktop 回答更多操作改为锚定小菜单，模型 sheet 去除背景压暗

- Desktop 回答 footer 的三点“更多操作”此前由全局 `assistant-message-actions` Dialog 加 Scrim 呈现，遮住整个聊天页；现在记录触发按钮的真实矩形，在其下方显示 208px 宽的两行小菜单（空间不足向上翻转并钳制）。菜单无标题／关闭键／页面遮罩，仍只提供“本次回答信息”和“创建分支”；前者会再打开既有详情 Dialog，后者仍走已有分支 owner。
- 该临时菜单的点外关闭会消费本次点击，Esc、窗口滚动与窗口尺寸变化也关闭，以免底层对话在关闭菜单时被误触。此行为只作用于 Desktop，不改 Android 既有 `DropdownMenu`。
- 用户补充的“选择模型”底部 sheet 保持原有层级、关闭按钮和点外 dismiss，仅将 `.composer-model-sheet-scrim` 改为透明，聊天背景不再压暗；不改变模型目录、持久化或联网边界。
- 新增纯锚点计算／渲染回归和透明 scrim 断言。`npm --prefix desktop run lint`、`typecheck`、`test`（303/303）和 `build` 均通过；尚未重打 macOS bundle、未覆盖安装、未做本轮原生视觉读回，也没有触及任何设备数据。

## 2026-09-12：Android 图片预览触控板横滑只切换一张

- 多图原图预览此前把 `preview.id`、图片序列和图片几何作为全屏 `pointerInput` 的 key。触控板一次横滑在第一张切换后仍可能持续发出输入；Compose 因 key 变化重建手势 owner，余下输入会被当成下一次手势，因而一次滑动跨过两张图片。
- 预览视口现在只在真实视口尺寸变化时重建手势 owner。每个新手势开始时才快照当前图片 ID、序列、几何、缩放上限和阈值；切到目标图不会接手尚未结束的旧手势，单次横滑最多切换一张。双指缩放、放大后拖动、长图原比例纵向浏览和原有 56dp 横滑阈值均不改。
- 新增 `P6F2BImagePreviewUiContractsTest` 回归，约束图片／几何变化不能进入 gesture owner key；源码级断言和 `git diff --check` 通过。定向 JVM 任务在 Gradle 配置阶段被依赖校验阻断：Maven `guava-parent-33.4.0-jre.pom`、`junit-bom-5.10.2.module`、`junit-bom-5.11.0-M2.module` 缺少校验哈希；未放宽校验或修改 verification metadata。未构建、未连接模拟器／主设备、未安装或修改设备数据。

## 2026-09-11：Desktop 滚动不再重建输入区

- 截图中的“回复 南枫AI +”被放大、位置跳动，不是独立提示样式错误。根因是消息滚动一旦跨过“是否位于最新消息”阈值，Desktop 会为了显示或隐藏“到最新消息”按钮而对整个应用执行 `render()`；这会销毁并重建 Composer、焦点和所有临时层。
- “到最新消息”现在始终由同一个 Composer dock 节点持有，只通过原地切换 `hidden` 可见性；滚动监听器只同步该按钮，不再重新渲染聊天页面。因此输入框的尺寸、锚点、草稿与临时浮层不会随滚动阈值变化。
- 新增 FB-P6-043 回归：固定验证最新按钮节点在两种状态均存在，且滚动监听器不含 `render()`。Node 267／267、lint、typecheck 与 Rust 226／226 通过。正式 macOS 包已重建、严格验签并在旧进程正常退出后保数据覆盖；SQLite `integrity_check=ok`，保留 7,298 条本地搜索索引。原生长会话连续向上、向下滚动并切换“到最新消息”后，Composer 始终固定在底部，未再出现整块放大或跳位。

## 2026-09-11：Desktop 本机数据分页与工作页独立导航

- “本机数据”中的“全部／正文／图片／视频／音频／文件”此前会把最多 2,000 条本地搜索卡片一次性插入 DOM；当前库有 7,298 条安全索引，WebContent 会持续占用 CPU，表现为点击后卡死。Rust 查询现以 100 条一页返回总数和游标，Desktop 只渲染当前页并提供前后页；手机端的惰性列表语义得以在 Desktop 上保持，不再用一次性全量 DOM 替代。
- 工作、项目、知识、记忆不再复用聊天顶部栏：移除残留的“对话／工作”模式胶囊、临时聊天和聊天操作，改为独立“工作区 / 当前页”页面头部与显式“返回对话”。工作侧栏也只保留工作导航、项目和返回入口，不再夹入定时任务／南枫转写。返回会恢复进入前的普通对话选择。
- Node 267／267、lint、typecheck、Rust 226／226 通过；正式 macOS 包已重新构建、严格验签并在确认旧进程退出后保数据覆盖。原生读回确认：本机数据“全部”只显示 100 条一页，记忆页有独立工作头部且没有聊天模式控件，页面头部“返回对话”可回到原会话。覆盖后 SQLite `integrity_check=ok`，保留 7,298 条本地搜索索引。

## 2026-09-11：Desktop Google 登录回调与卡死根因修复

- 正式 Desktop 发起 Google 授权后，系统浏览器实际返回 `Error 400: redirect_uri_mismatch`。根因不是用户账号：打包脚本把 Android Credential Manager 所需的服务器 Web Client ID 当作 Desktop loopback OAuth Client；Desktop 每次生成 `127.0.0.1` 随机端口回调，Web Client 未登记该 URI，因此 Google 在授权码返回前即拒绝。
- 同时，Tauri 登录命令在打开浏览器前取得共享 SQLite 锁，并在等待 callback 的 180 秒中持有它；400 页面不会回调，其他本机 IPC 因锁等待而表现为软件卡死。现将浏览器授权及头像网络读取拆到无 SQLite 锁阶段，再仅在获得经 Supabase 验证的会话后短暂持锁持久化。失败只回到可重试状态，不写会话、不改本机数据或云端。
- Desktop 不再复用 Android Web Client：仅接受独立 `nanfeng.ai.cloud.googleDesktopClientId` 打包为 `NANFENG_DESKTOP_BUNDLED_GOOGLE_CLIENT_ID`。当前私有配置尚没有该值，因此正式包明确显示“未配置”、不打开浏览器；“查看 Google 登录条件”已补入动作 owner，原生读回确认它说明不会读取账号或上传，而非无响应假按钮。
- Node 265／265、lint、typecheck、Rust 226／226 通过；Rust mock 证明 OAuth 网络阶段可在无 SQLite 写入下完成，随后才持久化会话。正式 macOS 包重建、严格验签并保数据覆盖；覆盖前后 SQLite `integrity_check=ok`，保留 1 个工作区、1,582 个工作区附件与 1,594 个附件资产。真实 Google 完成登录仍缺少 Google Cloud `nanfeng-cloud` 项目中单独创建的 Desktop OAuth Client；控制台凭据页本轮持续加载，未能安全创建或读取该外部配置，不能把本地测试冒充真实登录闭环。

## 2026-09-11：Desktop 临时菜单不再撑满视口

- 正式 macOS 窗口复现：收藏会话的“取消收藏”菜单只含一个动作，却从顶部延伸至接近整页高度。这不是收藏数据或单项样式的问题；共享菜单层把 `span/div` 提升为手动 Popover 时保留了浏览器默认 `inset: 0`，导致其在设置页的网格布局中被拉满。
- 修复共享 owner `installActionMenuLayerOwner`：在测量和锚定前清除各方向定位、固定内容高度与网格行高。故同时覆盖 `.conversation-lifecycle-actions`（收藏／归档／回收站）和 `.memory-reference-menu`（记忆摘要），没有改变动作、权限或持久化链路。
- 新增共享 owner 回归合同；Node 264／264、lint、typecheck 均通过。重新生成并签名 macOS 包后保数据覆盖正式应用；覆盖前后 SQLite `integrity_check=ok`，保留 1 个工作区、1,582 个工作区附件和 1,594 个附件资产。原生读回确认：收藏单项菜单为紧凑浮层；记忆摘要五项菜单也按内容高度展开。点外关闭并消费该次点击的既有行为仍保留。

## 2026-09-11：Desktop 回答成本、来源徽章与打包完整性修复

- 用户在正式 Desktop 看到回答底部孤立的 `i` 与“金额未知”。核对当前 Android `ConversationCostEstimator` 后确认 Desktop 只会显示服务商回传的 `chargeMicros`，漏掉了手机端“实收缺失时按持久模型、Token、时间和价目表做只读本地估算”的链路；这是真实功能缺口，不是文案问题。
- 新增 `desktop-cost-estimator.mjs`，逐项投影 Android 的模型价格规则；服务商实收永远优先，缺失时显示 `≈ ¥…（估算）` 和“本地价目表估算”，不回写账本、不把估算伪装成实收。当前正式记录 `deepseek-flash`（输入 1,063／输出 157）原生回读为 `≈ ¥0.00082（估算）`；总览同时保留“服务商实际金额：金额未知”。
- 回答底部已完全移除来源 `i` 徽章以及死代码／样式；“本次回答信息”和“创建分支”仍只在“更多操作”中。修复过程中发现新估算模块未被静态 build 脚本复制，导致首次包启动空白；已补复制清单和回归测试，防止后续新增启动模块再出现“测试通过、正式包白屏”。
- Node 263／263、lint、typecheck、静态 build、macOS bundle 与严格 codesign 全部通过。正式应用已保数据覆盖并成功启动；覆盖前后 SQLite `integrity_check=ok`，保留 1 个工作区、1,582 个工作区附件和 1,594 个附件资产。盘点确认 225 个 Desktop 可渲染动作均有处理器，130 个本地调用均有 Rust owner 和注册；107 个动作与 43 条调用尚只有间接测试覆盖，仍需逐状态人工审计，不能标作“全端完全验收”。

## 2026-09-11：Desktop 回答“更多操作”标题与分支图标对齐

- 截图对应的是 Desktop 弹层而非 Android。根因是 Desktop 的 `icons` 集合缺少 `branch` 路径，导致“创建分支”虽保留菜单文字却实际输出空 SVG；现补入分支图标，并保留图标与文字作为同一个可点击菜单项。
- “更多操作”标题由通用弹层的 22px 收紧为随 App 字号缩放的 18px／700，菜单行仍保持 48px 点击高度，故只降低视觉噪声而不压缩鼠标和触控命中区。
- Desktop Node 260／260、lint、typecheck、macOS Release bundle 均通过。已验证开发签名并保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；覆盖前后 SQLite 完整性均为 `ok`，保留 1 个工作区和 1,582 个附件。原生弹层读回确认标题已收紧且“创建分支”图标可见。

## 2026-09-11：Android 回答信息弹窗整理

- “本次回答信息”改为清晰的纵向阅读结构：先以“回答设置”呈现基础风格和实时网络，再在存在实际本地材料时显示“本次上下文来源”。默认风格不再把相同的字段名和值重复排成两行；每个来源从统一橙色项目符号起排，长标题在同一阅读列自然换行。
- 移除底部的解释性小字；回答级审计仍只展示已持久化的安全事实，不展示正文、Prompt、附件、Provider 原始请求或凭据，也不新增聊天页或 Composer 常驻入口。
- Assistant 的“更多操作”中，“创建分支”改为正文色、`20dp` 圆润 `CallSplit` 图标；图标和文字仍属于同一个菜单项，不再以 16dp 弱化图标呈现。其专项合同把图标、尺寸和正文色绑定在“创建分支”菜单项内，防止只在其他入口保留图标。
- 定向 JVM 合同测试 2／2 与 Release `assembleRelease` 已通过；没有连接模拟器或主设备，尚未取得本轮真机／模拟器视觉读回，也没有安装或修改任何设备数据。

## 2026-09-11：Desktop 本机数据卡片灰度减半

- 浅色模式“本机数据”分类卡片由 `#F2F3F2` 调整为 `#F9F9F9`，即相对白底保留约一半的原始中性灰对比；悬停与按压同步收浅为 `#F5F6F5`／`#F1F2F1`。深色模式和文字、图标、主题色不变。
- 前端 Node 260／260、lint、typecheck 通过；已签名 macOS 包保数据覆盖，并在原生“本机数据”页确认。覆盖前后 SQLite 完整性均为 `ok`，保留 1 个工作区、1,594 个附件与 7,298 条本地搜索索引。

## 2026-09-11：Desktop 应用图标主体放大

- macOS 仅替换应用 `.icns` 及其 1024px PNG 派生资源：从保留的母版 `desktop/src-tauri/icons/nanfeng_ai_icon_master.png`（SHA-256 `c1bec69ca4939632ff7a447be0e9ef9735b4a7097bd450d68da50394e54f1243`）居中放大 1.23 倍后裁回同尺寸画布，使白色马头主体更接近 macOS 图标可见区；不改 Android 启动器资源。
- 可重复生成脚本为 `desktop/scripts/generate-macos-icon.mjs`，它从母版一次生成 `icon.png`、`nanfeng_ai_icon_rgba.png` 与 `nanfeng_ai_icon.icns`。Node 260／260、lint、typecheck 通过，已签名包保数据覆盖；Finder“应用程序”实际图标视图已确认主体放大，覆盖前后 SQLite 完整性均为 `ok`，保留 1 个工作区、1,594 个附件与 7,298 条本地搜索索引。

## 2026-09-11：Desktop 会话行操作区覆盖标题

- 左侧会话行的置顶、收藏和更多三个操作提升为独立顶层操作区；鼠标悬停或键盘焦点进入行时，标题行在右侧预留 104px，不再与三个操作重叠。标题保持单行省略，操作按钮仍可独立点击。
- 前端 Node 260／260、lint、typecheck 通过。macOS 签名包已保数据覆盖；覆盖前后 SQLite 完整性均为 `ok`，保留 1 个工作区、1,594 个附件与 7,298 条本地搜索索引。原生窗口读回确认长标题在三个按钮左侧截断，未重叠。

## 2026-09-11：Desktop 侧栏对话菜单保存本次点击锚点

- 侧栏三点、右键与长按菜单均在触发时保存实际触点的视口矩形；根节点重绘后直接使用该矩形定位，不再以首个或替换后的会话行重新推断坐标。菜单仍受侧栏边界约束，滚动与窗口尺寸变化时关闭，避免悬空。
- 前端 Node 260／260、lint、typecheck 通过。已签名 macOS 包保数据覆盖，SQLite 覆盖前后完整性为 `ok`，保留 1 个工作区、1,594 个附件与 7,298 条本地搜索索引；原生右键读回显示目标会话 `MATCH法案影响阿斯麦 K`，确认没有回退到列表首项。

## 2026-09-11：Desktop 普通聊天与账号登录按 Android 已验证路径对齐

- Desktop 的四个服务商继续使用各自官方端点与凭据，不把模型 ID 转交给其他服务商。路由、请求体与 Android `ChatProviderAdapters` 对齐：OpenRouter 使用服务端搜索工具；Qwen 在其 Chat Completions／Responses 分支使用各自协议；DeepSeek 网页检索走官方 `/responses`；智谱使用 Chat Completions 搜索工具。
- 已修正两个会导致有效回答被错误标失败的 Desktop 终态差异：所有 Chat Completions 路由在收到可见文本后干净 EOF 可完成；DeepSeek `/responses` 不再因没有来源元数据失败，并与 Android 一样只在完整 JSON 解析后一次性提交终态，避免“已显示回答但一直正在生成”。来源仅在服务商实际返回安全 URL 时附加。
- 已在当前已签名 Desktop 应用的 UI 以 `DS V4.1` 发送最小消息并得到完整回复“南烛枫，OK”；数据库最新 Attempt 为 `COMPLETED`、路由 `DEEPSEEK_RESPONSES`。同一已签名应用的 OpenRouter 服务端检索探针返回 `LIVE_PROBE_OK`（2 个增量、2 字节回答）。这些探针只记录数量、耗时和终态，不记录密钥或回答内容。
- Google 设置页此前在 Finder 启动时缺少 shell 环境，因而把已存在的 Android 公共云配置误判为未配置。macOS 打包现在从项目 `local.properties` 读取与 Android 相同的公开 Supabase URL、publishable key 与 Google client ID，编译进签名包；不读取或写入 client secret。已签名包自检 `ACCOUNT_CONFIG_OK`，设置页已呈现可点击的“使用 Google 登录”。实际 Google 账户授权仍由用户在系统浏览器中选择并完成。
- 本次两次保数据覆盖前后 SQLite 完整性均为 `ok`，保留 1 个工作区、1,594 个附件与 7,295 条本地搜索索引。普通聊天传输回归 13／13 通过；最终包已验签。


## 2026-09-10：Desktop 普通聊天将明确流终态写为完成

- 截图中的 `MISSING_COMPLETION` 已定位到 Desktop SSE 完成判定：原实现只接受 `[DONE]`，忽略 OpenAI 兼容流中已明确给出的 `choices[].finish_reason`。当服务端在该终态后直接关闭连接时，已生成的回答被错误持久化为结果未知。
- 现在非空且非 `null` 的 `finish_reason` 与 `[DONE]` 同等视为明确完成证据；没有 `[DONE]` 且没有明确终态的断流仍为 `MISSING_COMPLETION`，保持不自动重发。新增本机 SSE 回归覆盖这两个分支。
- 未读取密钥、未向真实 Provider 发起测试请求。Rust 223／223、前端 Node 260／260、release `cargo check`、lint、typecheck、生产构建通过；macOS bundle 已验签并保数据覆盖，覆盖前后 SQLite 完整性为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引。


## 2026-09-10：Desktop 联网模型配置提示按真实设置刷新

- 启动提示此前复用了 P10-A 双路径安全合同。该合同为了不读取钥匙串而固定报告未配置，实际 `desktop_provider_settings` 与凭据存在性投影未参与判断，造成“联网模型尚未配置”的假提示。
- 现在启动时先显示“正在读取联网模型配置”，再按已启用且凭据已保存的服务商数量显示配置结果；保存模型设置后同样立即刷新。P10-A 页面改为明确它只表达安全合同，不再将“不读取凭据”表述为“未配置”。
- 本机只核对了设置记录和钥匙串凭据存在性，未读取密钥、未发起 Provider 请求；因此“已配置”不等于“连接已验证”。Desktop Node 260／260、lint、typecheck、生产构建通过；macOS bundle 已验签并保数据覆盖，覆盖前后 SQLite 完整性为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引。

## 2026-09-10：Desktop 右上角对话菜单锚点修复

- 右上角三点菜单在点击时保存实际触发按钮的视口坐标；菜单重绘后及下一渲染帧都从该坐标向左展开。此前重绘后重新查询锚点可能失败，导致菜单落回左侧会话区域；侧栏行尾菜单仍各自按行定位。
- 菜单节点明确记录 `header`／`sidebar` 来源，标题菜单不会参与侧栏边界计算。Desktop Node 259／259、lint、typecheck、生产构建通过；macOS bundle 已验签并保数据覆盖，SQLite 完整性为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引。

## 2026-09-10：Desktop 会话行快捷归档改为收藏

- 左侧会话行悬停后的第二个快捷操作由“归档”改为“收藏”：未收藏显示空心书签，已收藏显示实心书签并提供“取消收藏”。操作接入已有本地收藏持久化链；归档仍保留在三点菜单和对话管理中。
- Desktop Node 259／259、lint、typecheck、生产构建通过。macOS bundle 已验签并保数据覆盖，SQLite 完整性为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引。

## 2026-09-10：Desktop 会话列表滑条贴右侧

- Desktop 左侧会话列表的滚动容器向右延展侧栏原有 12px 内边距，并以同等右内边距保留会话正文区域。滑条现在靠近导航栏右缘，标题和日期的可用宽度不缩小；该规则仅在 901px 以上 Desktop 视口生效。
- Desktop Node 259／259、lint、typecheck、生产构建通过。macOS bundle 已验签并保数据覆盖，覆盖前后 SQLite 完整性为 `ok`，工作区、附件及搜索索引数量保持不变。

## 2026-09-10：Desktop 本机数据分类按键灰底还原

- 本机数据页“对话与内容”“附件”下的分类按键曾被浅色主题兜底覆盖为近白 `#FCFCFC`。现恢复为中性灰 `#F2F3F2`，悬停／按压分别使用 `#EAEBEA`／`#E2E4E2`；只影响这些未选中分类按键，不改变主题色或深色模式。
- 为该层级增加静态样式合同。Desktop Node 259／259、lint、typecheck、生产构建通过；macOS bundle 已验签并保数据覆盖，前后 SQLite 完整性为 `ok`，工作区、附件与搜索索引数量保持不变。

## 2026-09-10：Desktop 基础风格和语气弹窗完整呈现

- 风格选择弹窗由固定 620px 高／600px 宽改为 760px 宽、最高占满视口减 48px 的容器。六张说明卡在常规 Desktop 窗口中完整呈现；小窗口由弹窗自身滚动，不再从底部裁切。
- 弹窗与卡片统一使用稳定盒模型、固定两列网格、预留滚动槽及说明文字最小行高；悬停仅改变背景色，不会因边框、滚动条或文字换行改变卡片尺寸和排版。
- Desktop Node 258／258、lint、typecheck、生产构建通过。macOS bundle 已验签并保数据覆盖；覆盖前后数据库完整性为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引，原生启动读回通过。

## 2026-09-10：Desktop 对话更多菜单标准密度

- 对话标题和侧栏行尾共用的临时菜单已统一为 180px 宽、13px／600 文字、18px 图标、42px 行高，并相应收紧内边距与图标文字间距；删除项仍保留既有语义色。此改动只调整共享菜单密度，不改变菜单行为或项目范围。
- Desktop Node 258／258、lint、typecheck、生产构建均通过。macOS bundle 已严格验签、保数据覆盖并启动；覆盖前后 `workspace.sqlite3` 完整性均为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引。原生 AX 读回确认已导入会话、附件预览、标题和侧栏“对话更多操作”入口均正常可用。

## 2026-09-10：Android 手机备份已迁入 Desktop 可见工作区

- **已完成的可见迁移：** 已从已校验的 Android P5-D 备份创建唯一 Desktop 工作区 `workspace-android-p5d-20260910`（`Android 手机数据（2026-09-10）`）。SQLite 读回为 840 条完整对话、5,122 条消息、12 条知识、7 条记忆和 1,582 个私有附件；语义哈希为 `17438bc74fa214036938153939dd9fe6dee178d78a8692fe2a70c7a78cacb08c`。1,582 个附件均已逐项回读 SHA-256，Desktop SQLite `integrity_check` 为 `ok`，应用重启后可显示手机来源会话与本地附件预览。
- **搜索与旧数据清理：** 已重建 Desktop 本地搜索投影：7,288 条记录（4,767 条正文、1,681 条附件），索引语义哈希与工作区一致。用户要求删除的 3 个旧 Desktop 工作区及 454 条旧会话索引已在外键核对通过后删除；删除前快照位于 [`desktop-before-visible-import/p6b-workspace`](/Users/nanzhufeng/Library/Application%20Support/NanfengMigrationBackups/20260910-phone-to-desktop/desktop-before-visible-import/p6b-workspace)。当前数据库仅保留该 Android 工作区。
- **来源与可重做工具：** 原始 3.3 GB 备份、迁移收据和删除记录均在 [`NanfengMigrationBackups/20260910-phone-to-desktop`](/Users/nanzhufeng/Library/Application%20Support/NanfengMigrationBackups/20260910-phone-to-desktop)。可重复执行的流式校验／映射工具是 [`migrate-android-p5d-backup.py`](../desktop/scripts/migrate-android-p5d-backup.py)；它不覆盖同名工作区，支持只重建搜索索引。
- **设置事实：** P5-D 格式没有 SharedPreferences 或加密 Provider 凭据，因此本次备份没有手机外观、个性化、联网／模型选择或密钥可迁入。Desktop 原有设置没有被清除；当前没有连接 Android 设备，不能将缺失于源包的手机设置编造为同步完成。后续若需逐项同步手机设置，须先由手机导出包含安全设置快照的新备份；Provider 密钥仍必须在 Desktop 单独配置。

## 2026-09-10：Desktop Google 登录未配置状态

- 截图中的“使用 Google 登录”不可点击并非命中层级错误：Desktop 仅在原生运行且同时有 `NANFENG_SUPABASE_URL`、`NANFENG_SUPABASE_PUBLISHABLE_KEY` 与 Google Desktop Client ID 时才允许系统浏览器 OAuth。当前运行配置缺失，因此状态为 `NOT_CONFIGURED`；不得用空配置打开浏览器、伪造会话或上传本机数据。
- 未配置状态已改为可点击的“查看 Google 登录条件”。点击只说明缺少南枫云地址、公开访问密钥与 Google Desktop Client ID，并明确不会打开浏览器、读取账号或上传数据；配置完整后同一位置仍显示正式“使用 Google 登录”。
- Desktop Node 257／257、lint、typecheck 通过；最新 macOS bundle 已严格验签并保数据覆盖到 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`。覆盖后 Desktop 数据根和 `workspace.sqlite3` inode 保持。未启动 Google／Supabase、未读取账号或任何凭据；已运行窗口需完全退出后重新打开才加载新 bundle。

## 2026-09-10：手机迁移源与 Desktop 反复覆盖安装的数据保护

- **已保全的迁移源：** Android 已成功完成一份本机备份并经设备端 SAF 回读校验。副本保存在用户级、应用包外的 [`phone-source/nanfeng-ai-local-backup_2.zip`](/Users/nanzhufeng/Library/Application%20Support/NanfengMigrationBackups/20260910-phone-to-desktop/phone-source/nanfeng-ai-local-backup_2.zip)，大小 3,305,997,003 bytes；Android 端与本机副本 SHA-256 均为 `adb946273b75374a77d7fc624fa49f935b3178147d9568c2ae4b32afa2df224b`。ZIP 结构、逐项哈希和 SQLite `integrity_check` 已独立核对，数据库共 129 张表。不得删除此迁移源或先清空手机数据。
- **Desktop 安装边界：** 正常 Desktop 业务根为 `app_data_dir()/p6b-workspace`，当前实路径是 `~/Library/Application Support/com.nanzhufeng.ai.desktop/p6b-workspace`；路径选择配置独立位于 `app_config_dir()/storage-location.json`。两者均不在 `南枫 AI Desktop.app` 包内，重复替换／安装 `.app` 不会覆盖它们。当前 `workspace.sqlite3` 已在该根内；安装前的 Desktop 快照也保留在同一迁移目录的 `desktop-before/`。
- **失败关闭与回滚：** 数据根迁移只会先锁定旧根、复制至相邻 staging、验证 SQLite，再原子发布；旧根始终保留。已定向验证迁移后重启仍使用新根且源目录仍在、既有目标不覆盖、旧根被占用或目标数据库缺失时停止、空 staging 不发布。正式 Desktop 包更新也要在替换应用包前后核对数据根与快照，不能使用卸载、清目录或重置应用数据作为“测试”。
- **格式边界（已处理可见数据，设置仍待新来源）：** P5-D `.nfai-backup` 是 Android 本机恢复格式，故意排除 Provider 凭据、路由偏好及设备诊断，也不含 SharedPreferences。当前迁移工具已将其可验证业务数据投影为 Desktop 可见会话并完成旧会话清理；手机外观、个性化、联网／模型选择等设置并不在该源包内，不能补写或伪造。v2 工作区交换仍只导出安全的 `uiLanguage`／`theme` 与语义数据，Desktop v2 import 目前仍先进入可校验的私有归档。

## 2026-09-10：Desktop 输入框鼠标提示稳定

- 修复 Desktop 底部输入框随鼠标移动出现并重定位原生提示的问题：移除 textarea 的 `title`，保留 `aria-label`，并以 `aria-description` 提供“可直接粘贴或拖入图片、PDF、视频和文件”的无障碍说明。因此鼠标经过不再触发跟随指针的 WebKit 提示，键盘／辅助功能仍可获得操作说明。
- 专项合同测试、Desktop lint、255 项 Node 测试、静态生产渲染验证通过。Chromium 在 1280×780 下多次移动鼠标前后输入框为 `x=395, y=680, 748×36`，无 `title`、无控制台告警；实际 macOS 应用已重新打包、严格验签、覆盖并启动，AX 读回输入框保留 placeholder 和无障碍说明、不再暴露 Help tooltip。旧应用暂存 `/tmp/nanfeng-desktop-before-composer-tooltip-20260910.app`。

## 2026-09-10：手机灰底还原、电脑服务商与卡片阴影

- 用户纠正：手机设置灰底不在减弱范围。已恢复 `#EDEDED` 并保数据覆盖，仍保留记忆摘要输入框删除。APK 回读 SHA-256 `e2a9f0058d73ada8ee7085be99e32989b88260577baabab67c110830fa8e4567`，与减弱灰底前版本字节完全相同，28,247,385 bytes；同签名非 Debug，首装时间／CE／DE 目录 inode 保持。启动 Activity 成功。
- 电脑端服务商条回调到 85% 宽，轨道 46px／按钮 40px，名称 14px／700；至少 420px 可用范围保护四标签。
- 电脑设置普通输入框／文本域、旧设置分组、调用记录分段和独立清理卡补同款轻阴影；复用 18px 等既有轮廓，不给密钥卡内部输入再叠第二层阴影。浏览器用生产渲染器验证浅／深色 × 1280／780（原生最小宽度）共 8 状态，字段阴影／轮廓、四服务商字体／无截断和密钥内部无重影均通过。
- 255 项 Node、lint、macOS bundle 构建及严格验签通过；电脑端已覆盖并重启，安装字节与构建相符，覆盖过程数据表计数和附件哈希保持。原包暂存 `/tmp/nanfeng-desktop-before-card-shadow-20260910.app`；本轮原生 AX 只确认启动，阴影逐像素结果属于浏览器证据。

## 2026-09-10：设置灰底减弱与 Desktop 移除摘要输入框

- 双端浅色设置背景 `#EDEDED` → `#FAFAFA`；Desktop 另收窄 12 处普通灰卡／路径底等表面，保持灰度差约 30%。主题主按钮／选中态、深色和文字保持；浏览器读回验证普通卡、选中卡、主按钮与路径底，深色未被浅色覆盖。
- Desktop 摘要输入框及提交选择弹窗、状态／处理分支与底部预留移除；实际安装版 AX 确认记忆摘要页面无输入框，右上角编辑入口保留。
- Desktop Node 255 项、lint／bundle 严格验签通过；Android 23 项相关 JVM 测试、Release 构建通过。双端已覆盖并启动。Desktop 安装字节与构建一致，更新期间表计数／附件哈希保持；原 bundle 暂存 `/tmp/nanfeng-desktop-before-settings-light-20260910.app`。
- OPPO 同签名非 Debug 包：28,247,386 bytes，SHA-256 `e4b0b15a5f17f2cdd48aadb4166248388620bd8da227a26ac1fac91ecf94aa67`，版本 0.3.0-p10j／66；安装回读字节一致，首装时间／CE／DE 目录 inode 保持，冷启动成功。未卸载／清数据。灰底逐页真机像素矩阵未重做，浏览器与原生 AX 不替代该证据。

## 2026-09-10：左侧栏轻灰底

- Desktop 浅色侧栏由白色改为中性浅灰 `#F5F5F5`，右侧保持 `#FFFFFF`；统一主题 owner 与初始 CSS，深色令牌不变。七主题投影及浏览器计算色读回通过；后续全套复核发现旧 C16 仍假设 Desktop 侧栏与 Android 同为白色，已在“设置灰底”增量中明确平台差异并恢复 255 项通过。
- 已重新构建、严格验签并覆盖电脑端，AX 确认启动；目标 bundle 字节与构建一致，覆盖过程本机数据表计数及附件哈希保持。原包暂存 `/tmp/nanfeng-desktop-before-sidebar-gray-20260910.app`，未重复安装手机。

## 2026-09-10：移除记忆摘要底部输入框

- Android `MemorySummaryPage` 移除“询问或更新”及对应提交选择弹窗、输入状态和 UI 回调；正文仅保留导航栏安全距离与 24dp 底部间距。右上角编辑／刷新等原操作保留，不改记忆存储。设置合同与两组既有 UI 合同测试同步。
- 2 项相关 JVM 测试通过，Release 构建成功；OPPO 已完成同签名非 Debug 正式包覆盖，安装包回读一致，首次安装时间和 CE／DE 数据目录 inode 保持。产物 28,247,385 bytes，SHA-256 `e2a9f0058d73ada8ee7085be99e32989b88260577baabab67c110830fa8e4567`，版本 0.3.0-p10j／66。未卸载／清数据，未运行仪器测试；此页真机视觉未重新截图验收。

## 2026-09-10：三个行末按钮独立点亮

- 修复整行透明背景规则压住单按钮反馈的问题；保留整行灰底，三个按钮各自使用主题浅底＋主题图标色，按下加深，复用原轮廓。
- 实际会话行渲染器＋完整 CSS 的 Chromium 检查：浅色／深色 × 3 按钮，共 24 项悬停／按下／键盘焦点／相邻按钮未点亮检查通过；既有 255 项 Node 通过。此项仅改 Desktop CSS，不改已覆盖手机 APK。
- 最新 macOS bundle 再次构建／严格验签后已覆盖用户 Applications 并启动；目标与构建包字节一致，更新过程数据表计数和 assets 哈希不变，AX 确认三个独立入口。安装前包暂存 `/tmp/nanfeng-desktop-before-row-feedback-20260910.app`；按钮逐态原生截图仍未获取。机器检查见 [反馈验证](review/20260910/row-action-feedback-verification.json)。

## 2026-09-10：双端覆盖更新

- macOS 最新验签 bundle 已复制到用户 Applications 的「南枫 AI Desktop.app」并启动；安装内容与构建包一致。现有数据库 84 张表计数、12 份 assets 哈希及数据根／数据库 inode 在更新及启动后保持。实际附件图片打开、Escape 返回原会话通过；截图工具不可用，不新增像素验收结论。
- OPPO PKH120 已用同签名非 Debug 正式包保数据覆盖，版本 0.3.0-p10j／66；28,263,769 bytes，SHA-256 `a76cfec266d6e97e3e6b36d0fec352aad9206afcad5a1404e2b02c4f5d00c731`。安装后拉回 APK 字节一致，首次安装时间及 CE／DE 目录 inode 保持，冷启动主 Activity 成功。未卸载／清数据／部署测试包；临时 APK 已清理。
- Android 首次增量 packageRelease 失败；携带 stacktrace 复验构建通过。安装后不再生成或修改该产物。上述启动证据不代替所有业务功能／Provider 验收。

## 2026-09-10：全屏文件预览与全局层级修复

- 六类预览统一应用视口全屏，修复分割线穿透；背景 inert／焦点／Escape 隔离，滚动容器菜单使用 top-layer popover。额外发现 PDF iframe 与 CSP 冲突导致原生空白，改为私有 PDF 当前页本机绘制 PNG。
- Node 255／255、Rust 221／221、lint／typecheck／build 通过；Chromium 39 项（双窗口、六类三态及层级边界），隔离 WKWebView 7 项（六类就绪预览＋菜单）通过。真实合成 PDF 页面非空及上下内容已检查，合成音视频时长均 2 秒。
- 最新 macOS bundle 构建与严格签名验证通过；未覆盖安装、未用真实用户数据运行，WKWebView 验证不等于完整 Tauri IPC 原生端到端。Android 本轮未改动／验收。详见 [修复与未验边界](review/20260910/PREVIEW_LAYER_FIXES.md)。

## 2026-09-10：搜索附件整卡高亮

- 附件卡高亮与预览点击范围扩展到整卡，取消内部局部灰底；三点菜单保持独立命中。255 项前端测试及 build 通过；未覆盖运行中的 macOS bundle，原生点击与视觉未验。

## 2026-09-10：搜索结果主题色高亮

- 搜索文字卡片移除明显边框；悬停／键盘聚焦改为主题色浅底和标题强调，按下稍加深。255 项前端测试与静态 build 通过；未覆盖运行中的 macOS 应用，原生视觉未验。

## 2026-09-10：Android 风格名称字号

- 六项风格名称由 titleLarge 缩为加粗 titleMedium，介于顶部 titleLarge 和说明 bodyMedium 之间；选中／未选中保持同字号。Debug Kotlin 编译与两组相关测试通过，未覆盖安装或做真机视觉验收。

## 2026-09-10：保存路径卡片布局

- 路径卡片移至本机数据总览下方；标题／更改路径按钮同排，完整路径在浅灰独立区域换行显示，删除截图小字说明。255 项前端测试与静态 build 通过，渲染函数读回确认顺序、文案移除和动作保留；未更新正在运行的 macOS bundle，原生视觉未验。

## 2026-09-10：Desktop 服务商切换缩小

- 按截图将服务商分段卡居中缩至 75% 宽度，轨道 52→40px、按钮 44→34px、字号 13→11px；窄窗口保护四个标签。沿用既有配色和轮廓。源码、81 项相关前端测试和静态 build 已验证；headless 浏览器读回超时，未形成渲染验收；未覆盖运行中的 macOS 应用。

## 2026-09-10：DeepSeek V4.1 Flash 双端升级

- Android／Desktop 完整名称、API `deepseek-flash`、当前缓存选择、短名、标题／历史整理提示同步；持久键保留，历史调用不改写。原生图片和联网图片请求已贯通，周末峰谷修正。见 [升级记录](review/20260910/DEEPSEEK_V41_UPGRADE.md)。
- 最终 Android JVM 1113／0／0／3；Debug、Release、Lint 通过；Desktop Node 255／255、Rust 218／218、build／lint／typecheck 通过。Release 初次增量打包异常，带堆栈复验通过，根因未重现；Lint 101 warnings／19 hints。正式 APK 已验签，未安装。见 [机器验证记录](review/20260910/deepseek-v41-verification.json)。
- 保留本次之前的侧栏整行悬停、顶部菜单锚点、三点最右侧修正；当前 macOS 运行应用未覆盖。未做主设备、原生视觉或真实 Provider 验收。官方公告 Pro 9 月 14 日转发边界仍待核验，未提前修改当前 Pro。

## 2026-09-10：四项边界修复

- 用户授权从复盘转入业务修复，基线 `4bf3e00`。按顺序修复目录恢复锁顺序、已配置缺库失败关闭与备份回滚兼容、头像流式上限、Android 已披露接收方的 v2 授权快照；见[修复记录](review/20260910/BOUNDARY_FIXES.md)。此前“未改业务／尚未修复”仅适用于各自历史阶段。
- Rust 全套 216 通过；Android JVM 1109／0／0／3，三个真实 ZIP opt-in 跳过；头像策略／静态合同 7/7，实际 handler 五个模拟场景通过。Debug／Release／Lint 全通过，正式 APK 验签成功、未安装；Lint 保留 101 warnings／19 hints。产物信息见[修复验证](review/20260910/boundary-fix-verification.json)。
- 未安装主设备、未读取真实数据或凭据、未发真实 Provider 请求、未部署在线函数；原生与真实服务验收仍分开。原始发现与红灯保留，新修复不改写历史结论。

## 2026-09-10：完整复盘与边界审查续验

- 已更新完整开发档案、可迁移经验、长期 AGENTS 与五个项目 Skill；全量文件与 Git 清单见 [审计记录](review/20260910/verification.json)。本阶段复盘材料纳入独立本地文档 checkpoint（提交主题 `docs: consolidate full project retrospective and boundary evidence`），业务 checkpoint 仍为 `c4aad94`；未推送或发布。
- [边界复核](review/20260910/BOUNDARY_REVIEW.md)：隔离 Rust 探针 4 通过／2 红灯，确认恢复锁顺序与已配置目录缺库问题；模拟头像 handler 两例均确认超限响应完整读取后才拒绝。Android 接收方授权缺少绑定为源码发现，真实调度未验。
- 本阶段没有修复业务代码、部署或接触真实用户数据。既有全套通过不覆盖上述新增红灯；后续修复应逐项建立行为回归。

## 2026-09-10：最终回归与正式增量固化

- **代码 checkpoint：** `c4aad94`，覆盖上次 `dd3a445` 后累积的 Android／Desktop 源码、测试、Room Schema 66、依赖与验收工具；本地提交，未推送或发布。临时截图、测试报告、APK 与 bundle 不进入代码提交。
- **当前合同读取门：** [Android 会话合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)、[设置合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)、[运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)、[转写合同](ANDROID_TRANSCRIPTION_UI_CURRENT_CONTRACT.md)、[Desktop 会话合同](DESKTOP_CHAT_FIRST_UI_CONTRACT.md)、[本机数据合同](C14_LOCAL_DATA_PARITY_CONTRACT.md)。这些合同约束行为，历史验收仅证明发生时的版本。
- **Android 增量：** 启动恢复使用退出时实际会话 ID 与时间／生成状态，不再回退置顶首项；记忆摘要支持整篇编辑、并发修订检查、事务替换与失败保稿；设置的六风格全屏选择、居中粗体标题／较小说明已实现；Schema 65→66 保存发送授权时间与披露版本，不记录正文。
- **Desktop 增量：** 设置 patch 串行保存及 SQLite 关闭重开；独立数据路径配置与下次启动迁移；20 个共享动作使用 Android 原始矢量；Composer 根据真实 scrollHeight 在 36–190px 间伸缩。历史 C-01～C-16 已验项不重复宣称本轮新验收。

### 本轮最终回归

| 层级 | 当前结果 |
| --- | --- |
| Android JVM | 1102 tests，0 failures，0 errors，3 skipped；跳过的是需要用户真实 ZIP 路径的 opt-in 测试 |
| Android Debug 构建 | `:app:assembleDebug` 通过，只作本地构建证据 |
| Desktop Node | 251/251 通过 |
| Desktop Rust | 209/209 通过，无 ignored；历史 OAuth callback 单例失败本轮全套未复现 |
| 静态与协议 | lint、typecheck、protocol golden、inventory、静态 build 通过；inventory 的 43 个 invoke 无直接测试引用仍是覆盖提示，不冒充全部入口行为验收 |
| 七主题 computed style | 首轮旧检查失败；修正为生产设置渲染器＋完整 CSS 后，七色 RGB 与无背景图覆盖全部通过 |

- 文档回归曾发现精简入口缺少“当前合同读取门”标识，补回后完整 JVM 再跑仍为 1102／0／0／3；Desktop Node 再跑 251/251。新入口链接检查与最终差异空白检查通过。
- 详细命令和本轮日志保留于 `/tmp/nanfeng-final-20260910/`，摘要已固化在本交接；临时日志可能随系统清理，不能作为唯一长期结论。
- **Release/Lint 收口：** `:app:lintRelease :app:assembleRelease` 通过；Lint 0 errors、101 warnings、19 hints，保留为既有质量债，不称零告警。APK 为 `com.nanzhufeng.ai`，versionCode 66／0.3.0-p10j，非 Debug，正式签名验证通过，28,247,380 bytes；SHA-256 `9b287e107363e7dfe5bac00e674cca6bd3e3e7bc81e1ee3a2c77d9a91142e169`，与引用任务已安装产物一致，无须重复覆盖。
- 本轮没有重打 Desktop 原生 bundle；引用任务已完成相应构建，本次完整 Rust 测试与静态 build 不替代最新原生窗口验收。

### 已有证据与仍未验收项

- 引用任务 `南枫AI 33 - Android` 的 2026-09-10 覆盖记录已经完成：同签名正式包，安装后 APK hash 一致，首次安装时间和 CE／DE 数据目录标识保持。此次没有重复安装，也没有再次读取手机业务数据；该历史安装证据不代表本轮手机视觉验收。
- 原生 Desktop 全图标同步尚未完成；20 个共享图标的替换不等于所有图标已统一。最新 Composer 与 Android 全屏风格页尚未在本轮原生实看。
- Desktop 数据路径仍待原生选目录、重启和重新安装验证；设置虽有本地落盘回归，仍未覆盖所有独立 owner 的跨安装升级。未迁移用户真实数据。
- 本轮未运行模拟器、OPPO、任何 connected Android 测试、真实 Provider、Google／Supabase、通知或远程网关。既有 Sonnet 短消息成功只证明该次探针，不证明附件／长回复／输入到落库全链。
- 下一步若继续产品验收，应从上述未验项选择一个独立增量；当前代码／回归／文档 checkpoint 已收口，不重做历史已完成项。

### 历史证据索引

- [本轮前完整交接归档](archive/CURRENT_HANDOFF_BEFORE_20260910_CHECKPOINT.md)：保留原有全部独有记录，仅调整相对链接以适配归档目录。
- [完整开发档案](南枫AI完整开发档案.md) 与 [可迁移开发经验](可迁移开发经验.md)：追加本轮长期事实；2026-09-01 及之前的数字、快照均为历史。
