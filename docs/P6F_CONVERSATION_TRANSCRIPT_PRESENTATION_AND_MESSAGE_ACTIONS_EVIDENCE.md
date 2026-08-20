# P6-F Conversation Transcript Presentation & Message Actions Evidence

日期：2026-08-14  
状态：P6-F Core 与 FB-P6-034 角色化 actions 回归已关闭，范围仍不进入 Provider、账号、同步、OPPO、图标或发布。

## 可见反馈增量（2026-08-14，实施中）

- **FB-P6-060（Android 真实闭环；Desktop 未实施）：** `DraftComposer` 不再在 Dock 外渲染附件行；`ConversationComposerDock` 以同一白色 `Surface` 承载缩略图行和固定控制行，`ComposerAttachmentPreview` 提供缩略图、右上角关闭、点击本地预览和长按附件事实。TEMP 通过 `ConversationAttachmentPreviewProjection.referenceFor/project` 产生同一有界预览，并仅由 `TemporaryConversationDomain.removeDraftAttachment` 移除草稿引用；普通/工作继续使用原 normal owner。定向 `P6DConversationRowAccessibilityContractsTest`、`P6F2BImagePreviewUiContractsTest`、`P6F2BImagePreviewContractsTest` 均通过，Debug assemble 成功。正式签名 Debug `app/build/outputs/apk/debug/南枫AI-开发验收.apk` SHA-256 `a00c4b5c2530037f898df89a0243ee8ad011fc756b307cdd60b12fa1831db040`，v2/v3=true、既定证书 SHA-256 未变，AOSP `emulator-5554` `install -r --no-incremental` 后回拉 `base.apk` 同 hash。真实照片选择器选择图片后，NORMAL 的 screenshot `/tmp/nanfeng-ai-fb-p6-060-in-composer.png` 显示缩略图/关闭在同一白色 Composer 内；WORK 切换仍读到同一 preview；NORMAL force-stop/restart 读回预览。TEMP 也真实选择图片，force-stop/restart 后重新进入 Ghost 读到同一 Composer 内 preview/关闭。最后使用正常关闭动作移除 NORMAL/TEMP 两处测试草稿并重启，UI tree 不再含 preview/关闭，避免遗留验收数据。未操作 OPPO、Desktop、Key 或 Provider；Desktop 同语义仍待实施，故不关闭双端反馈。

- **交接证据边界（2026-08-14）：** 用户要求全部 UI 反馈改为 Desktop + Android 同步验收；因此以下所有单端 AOSP、Desktop static、测试、构建与截图都只保留为局部证据，不能关闭 ledger 项。最新 Android 局部 WIP Debug SHA-256 为 `e24c5a61a6fb0c0a168d2336050ee50308f7e01d740b77113d9904cda62f365f`（v2/v3、既定证书），全量 JVM 326/326 通过；AOSP clean install 后已局部观察 NORMAL、WORK、TEMP 的 Composer，以及 TEMP `＋→相机/添加图片/添加文件→系统 Camera`。Desktop 未同步验收，三态真实消息/附件/restart readback 也未齐；此处不作 CLOSED 结论。完整交接清单见 `docs/CURRENT_HANDOFF.md` 顶部和 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/2026-08-14-nanfeng-ai-ui-feedback-dual-platform-handoff.md`。

- FB-P6-058：Android 完整 `:app:testDebugUnitTest` 在单 worker、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` 下通过 326/326（未隔离的一次 JBR SIGSEGV 为测试运行时故障，未作为产品通过依据）。最新正式签名 Debug `app/build/outputs/apk/debug/南枫AI-开发验收.apk` SHA-256 `48ec7cdc882da1aa21f7dfba616df8c1bd836a0f5ee98fae85cc05abb6cb4b81`，v2/v3=true、既定证书 SHA-256 未变；已 `install -r --no-incremental`，并在用户授权可清理的 AOSP 模拟器执行 `pm clear` 后真实启动。Ghost 进入 TEMP 的 UI tree 只有统一“会话草稿 / 添加附件 / 发送消息 / 自动”，空 transcript 无任何内部草稿/附件/模型文本；点击“自动”显示其按钮正上方的简洁列表，当前“自动”橙色勾选。截图：`/tmp/nanfeng-ai-fb-p6-058-final-clean-temp.png`、`/tmp/nanfeng-ai-fb-p6-058-temp-model-menu.png`。未读 Key、未发 Provider HTTP、未操作 OPPO；Desktop、TEMP 真附件/消息与 restart readback 尚未关闭。

- FB-P6-052/053/055/056/057：Android 全量 `:app:testDebugUnitTest` 通过。当前正式签名 Debug `app/build/outputs/apk/debug/南枫AI-开发验收.apk` SHA-256 为 `ef7f79a9bd98e2bf7e5235049a563d1f5f768972821a4c0fe560c426263c5bc4`，既定证书 SHA-256 未变，已 `install -r --no-incremental`、force-stop/restart。FB-P6-056 已删除 Android `Popup`：菜单成为会话根容器内不参与 Composer 测量的 fixed-size sibling overlay。AOSP 实际触发＋显示“相机 / 添加图片 / 添加文件”，模型显示“自动”橙色勾选、fixture 灰色；打开前后草稿 bounds 保持 `[217,2362][746,2429]`、发送 bounds 保持 `[982,2371][1032,2421]`。FB-P6-057 实际普通会话幽灵为灰色，点击后进入临时窗口幽灵为橙色。未发 Provider HTTP、未读 Key、未操作 OPPO。

- FB-P6-056 Desktop 同步：Node 65/65、lint、static build 与 `cargo check` 已通过；新的 Tauri `.app` 已 bundle 于 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`、ad-hoc strict verify 通过，主可执行 SHA-256 `0b9f4a8062ce103ffe7ac522cc02d5237fa526c12f3046c5345f8e692ecaa4c3`。为避免接管用户正在使用的同 bundle-id 窗口，创建独立 bundle-id 的临时副本，只验证其可启动并随后关闭；这不代替按钮点击或 readback，故 Desktop 交互门仍待。

- Android：定向 `P6DConversationRowAccessibilityContractsTest` 通过。正式签名 Debug `app/build/outputs/apk/debug/南枫AI-开发验收.apk` 的 SHA-256 为 `b528631d55973d4231d52c920aa98d2bf51361c0d5a547a11b4faf63dbf1af51`，已 `install -r --no-incremental`、force-stop/cold-start；设备 `base.apk` 回读同 SHA-256。v2/v3=true，证书 SHA-256 为既定 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。AOSP 以 2248×2480 尺寸近似内屏：真实 transcript 的“跳转到对话位置 1/2、2/2”语义出现，默认预览卡显示第一段；实际点击第二段后卡片变为第二段，且跳转仍由同一 `LazyListState` 完成。恢复默认尺寸、force-stop/cold-start 后外屏不渲染轨道，Composer 只保留精确“回复 南枫AI”，无常驻附件/草稿说明。Drawer 搜索输入已只保留一个入口，并绑定 IME Search/Enter 到既有搜索 owner；AOSP 不构成 OPPO 结论。
- Android 抽屉实际截图已确认纯白、无冗余安全说明/第二搜索按钮，底部显示同一行悬浮设置图标与“新对话”方框斜笔胶囊；普通 transcript 不再渲染 fixture、重试、换模型或尝试历史。长按一条模拟器旧验收会话后出现置顶、重命名、添加到项目、删除；用户明确授权下只将其中一条命名为“本地开发会话”的模拟器旧验收记录移入回收站，force-stop/restart 后正常列表剩余同名记录为另一条，未触碰 Desktop 或导入会话。
- Android “到最新消息”真实交互复测：在同一模拟器普通会话通过 Composer 正常保存 `latestprobe1` 至 `latestprobe22`，真实手势离开底部后按钮出现；点击按钮后页面滚到 `latestprobe22`，UI tree 不再含“到最新消息”，无闪烁/卡住。该数据仅在用户明确可自由修改的模拟器中生成；未触碰 Desktop、Provider、Key 或 OPPO。
- FB-P6-051：正式签名 Debug `南枫AI-开发验收.apk` SHA-256 `b528631d55973d4231d52c920aa98d2bf51361c0d5a547a11b4faf63dbf1af51`（v2/v3、既定证书）已 `install -r`、force-stop/restart，设备 `base.apk` 同 hash。实际 Drawer 截图显示设置白色圆形 surface 靠左、橙色“新对话”胶囊靠右，二者各自独立 hit surface；新对话有 6dp 自身阴影，不存在共同白色 tray。Desktop 按用户参考提高为 44px 独立圆形设置键与 44px/22px 圆角的新对话胶囊；专用 action 独立性合约和 Android 专用合同均通过。Desktop Node 64/64、lint/typecheck 通过，最新 ad-hoc strict-signed `.app` 可执行 SHA-256 `31f6264cc8d5ce991425c82227708b14a3ac28a70c868f68be59af6112ffc26e`；当前用户 Desktop 窗口未操作，故 Desktop 人工交互 readback 仍待。
- FB-P6-040 Desktop 右侧回归：专用 Node contract 已将模型/发送同组及顺序锁定；模型仅显示文字、默认是紧凑中性面而非突兀白色描边框，hover/pressed/focus 都在相同 11px 轮廓内完成。`npm test` 64/64、lint/typecheck/static build 通过。最新 ad-hoc strict-signed `.app` 可执行 SHA-256 `31f6264cc8d5ce991425c82227708b14a3ac28a70c868f68be59af6112ffc26e`。为保护正在使用的同 bundle-id Desktop 窗口，未接管、杀死或写入其数据；因此本机窗口的人工截图/readback 仍待，不能把构建代替交互验证。
- Desktop：Node 64/64、lint/typecheck/static build 通过；最新 `.app` 已重新 bundle、ad-hoc 签名且 strict verify 通过（可执行 SHA-256 `31f6264cc8d5ce991425c82227708b14a3ac28a70c868f68be59af6112ffc26e`）。轨道默认显示当前段预览，点击后切换 active preview 并只滚动既有 message-list owner；预览卡改为最大 320px、多行承载。系统中已有同 bundle identifier 的运行实例，隔离副本无法启动第二个真实窗口；为避免触碰用户 Desktop 数据，未杀进程或操纵该窗口。因此新位置轨道的 Desktop 人工交互/readback 仍待，不能写为 Desktop visible closure。
- Desktop 隔离可见复核：从上述最新 `.app` 复制到临时 app、改用独立 bundle identifier 后成功启动，未接管既有用户窗口或数据。真实 AX 同时读到左下“设置”、右下“新对话”、12 个“跳转到对话位置”按钮、精确 placeholder、紧贴发送键左侧的“选择模型：自动”；实拍截图 SHA-256 `7b379617c0818d614b3086bbbbb3c611e35e94212d1fd7816ecc19e9d8be76f2`。Computer Use 随后报告用户改变该临时窗口状态，故未继续发起点击/写入；040/050/051 的最新 Desktop 交互/readback 仍不以截图或静态测试代替。

## FB-P6-040 Composer 单一模型胶囊（历史验收；Desktop 右侧布局回归已重新打开）

- Desktop：`chat-composer-actions` 保持 40px selector hit target，只有 centered `::before` 32px capsule 承担 hover/focus/pressed surface，label 为 12px；selector 在 DOM 中紧邻 send 左侧。专用 Node contract、全量 `npm test` 53/53、lint、typecheck 与 static build 通过。typecheck 同时把已过期的“模型与联网 / 本地与联网”静态期望收敛为当前真实 Settings copy“模型选择与自动路由 / 本地可用”，未改变产品表面。
- 最新 Desktop `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app` 已重新 bundle、ad-hoc 签名且 `codesign --verify --deep --strict` 通过；可执行 SHA-256 `11a80b0be2a77c5ccdf8fc5b12c29b1eb490a924e916d70fea6336dac1ae6d54`。本机真实 AX 路径：Auto → `本地确定性 fixture（仅验收）` → 完整退出/重开仍显示 fixture；随后选回 Auto → 再次完整退出/重开仍显示 Auto。真实截图显示模型胶囊位于发送键左侧；这是本地 ad-hoc 开发包，不是发布或 OPPO 证据。
- Android：`P6DConversationRowAccessibilityContractsTest` 定向 JVM、`lintDebug`、Debug assemble 通过。正式签名 Debug `南枫AI-开发验收.apk` SHA-256 `62332cddfd856c2c1e2464e2a1b90bf11975a588bb39ea23202c96e0b73f3616`，v2/v3=true，证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。AOSP API 35 `emulator-5554`（1140×2616）以 `install -r --no-incremental` 覆盖；实际 tap 打开“当前会话模型”并选择本地 fixture，force-stop/restart 读回该名称，再选 Auto 并 force-stop/restart 读回“自动”。最终截图 `/tmp/nanfeng-ai-fb-p6-040-android.png` SHA-256 `db9b65d16f35ac0fe7d8dabc093db352bb0565eeafc06afee94dfc6427887543` 显示 48dp selector hit box 内的 38.4dp 胶囊紧邻发送键左侧。回拉 `base.apk` SHA-256 与本地 Debug 完全一致。AOSP 不是 OPPO 证据。

## FB-P6-041 Composer glyph 0.8 与向上纸飞机（已关闭）

- Desktop：新增 `fb-p6-041-composer-glyph.test.mjs`，精确断言 add 20.8px、send 20px、官方纸飞机来源、`rotate(-90deg)`/中心 transform，以及 40px hit 与 30px surface 不变；既有 composer 回归已同步移除被 041 覆盖的 25px glyph 期望。`npm test` 54/54、lint、typecheck、static build 通过；Rust `fmt --check`、clippy `-D warnings`、51 tests 通过。最新 `南枫 AI Desktop.app` 重新 bundle、ad-hoc 重签并由 `codesign --verify --deep --strict` 验证；可执行 SHA-256 `0200c748ad3808d017e25393cfb8153f4b277a5f61b32a5e6adfcb0e9815d779`。真实 AX/视觉路径显示 add、Auto 与 send 保持同一 composer 顺序；完整退出再启动后同一 composer 仍可读。此为本地 ad-hoc 开发包，不是 Developer ID、notarized 或发布证据。
- Android：新增 `FBP6041ComposerGlyphContractsTest`，定向 JVM 与既有 `P6DConversationRowAccessibilityContractsTest` 均通过；它们锁定 add 19.2dp、idle Material `AutoMirrored.Send` 18dp、内部 `rotationZ = -90f`、48dp hit、36dp surface 和 stop 22.5dp 不变。`lintDebug` 与 Debug assemble 通过。正式 Debug `南枫AI-开发验收.apk` SHA-256 `45c0a6eed213d71ae42e674398591835d8afcc1319c041802ac86d86fd690b22`，v2/v3=true，既定证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。AOSP API 35 `emulator-5554` 用 `install -r --no-incremental` 覆盖，force-stop 后冷启动至 `NanfengAiActivity`，实际 composer 显示缩小 plus、Auto 与朝上纸飞机；设备回拉 `base.apk` SHA-256 与本地 APK 完全一致。截图 SHA-256 `246734208bb03146cd4904fdaaa7050f1962c49da7944388b2e94d00922f83fa`。AOSP 非 OPPO，未读 Key、未发 HTTP、未改 Provider/同步/知识库 Adapter、图标或发布。

## FB-P6-042 顶栏 Settings 唯一归属（已关闭）

- Desktop：新增 `fb-p6-042-topbar-ownership.test.mjs`，锁定顶栏仍有 chat/work 与 Ghost、没有 `show-settings`，而 sidebar footer 保留唯一 Settings owner。全量 Node 55/55、lint、typecheck、static build 通过。最新 ad-hoc strict-signed `.app` 的全新进程 AX 只列出“对话 / 工作 / 临时聊天”，顶部无 Settings；左侧仍有“设置”。可执行 SHA-256 `94b3215f8464769cb5d3e33a7974462040dca6075e21150c0ae23555f721724c`，非 Developer ID/notarized/发布包。
- Android：新增 `FBP6042TopBarOwnershipContractsTest`，锁定 header 无 `P5ARoute.SETTINGS`、drawer 底部保留 Settings，且只留菜单、chat/work 与既有 Ghost。该项没有 Android production source 改动，故复用 FB-P6-041 的正式签名 Debug（SHA-256 `45c0a6eed213d71ae42e674398591835d8afcc1319c041802ac86d86fd690b22`、v2/v3、既定证书）和 `emulator-5554` force-stop/cold-start readback；AOSP 非 OPPO。

## FB-P6-039 附件 sibling preview group（已关闭）

- Desktop `messageList` 先分离 `textBlocks` 与 `attachmentBlocks`；`chat-message-bubble` 仅承载文本，`chat-message-attachments` 是同级节点。USER group 右对齐、Assistant group 左对齐；message actions/metadata 在两者之后。Node 52/52 覆盖 mixed、text-only、attachment-only 与 multi-attachment，且断言附件不回流到 bubble；附件 hover/focus overlay 仍由原 `chat-attachment-info-overlay` owner 负责。
- Android `MessageBubble` 同样拆分 `textContent` / `attachmentContent`：USER 文本 `Surface` 和附件组是 `Column` siblings，Assistant 保持开放正文再放左对齐附件组和既有常显 action row。`P6DConversationRowAccessibilityContractsTest` 通过，且 attachment `combinedClickable` 的 long-press `ModalBottomSheet` 未改变。
- 最新 Desktop `南枫 AI Desktop.app` 重新 bundle、ad-hoc 重签，`codesign --verify --deep --strict` 通过；可执行 SHA-256 为 `a21811387183f2a26f7a5161ca85cbbf2b0ae807c3f9eb2c66bbfe6f0a528440`。隔离副本 `/tmp/nanfeng-ai-fb-p6-039.iaDP14/南枫 AI FB-P6-039.app` 的实际 AX 树显示混合 message 的正文、独立“本地附件预览”组、其后外置 actions；attachment-only audio/text 也只有预览组。完整退出重开后同一 AX tree 仍有 `讲话稿 → 本地附件预览 → 图片/文本`、PDF/video/audio/text preview owners，证明本地重启 readback；这是本地开发 ad-hoc 包，不是发布、Provider 或 OPPO 证据。
- Android `emulator-5554` 的 Debug `南枫AI-开发验收.apk` SHA-256 为 `ea130708a977dc6e79db3e9f17286b278a0b371b1652f5460f9f1fb8370df555`，`install -r --no-incremental` 后 force-stop/restart。实际 AOSP 截图 `/tmp/nanfeng-ai-fb-p6-039-android.png`（SHA-256 `d2bf3b4c49f94e16bb36f53b192a7949c25c0e6d1a8eda56b13e833b932a29f5`）显示 USER 右侧独立图片/视频预览 surface 与 Assistant 左侧 Markdown 正文，未出现覆盖整条 mixed message 的外层大 bubble；系统 dump 同时保留 image/video 的 attachment content descriptions。未操作 OPPO。

## FB-P6-037 Desktop 回收站确认状态机（已关闭）

- `desktop/src/recycle-confirmation.mjs` 把确认开始、失败恢复与 receipt 成功完成拆为可执行的纯状态转移；`chat-first-ui.test.mjs` 覆盖 target 不匹配、重复点击、失败保留可恢复错误和成功后清除 dialog 的转移，Desktop Node 50/50 与 lint 通过。
- 以最新 `南枫 AI Desktop.app` ad-hoc strict-signed bundle 的隔离副本，真实新建本地验收会话后执行右键删除→“移入回收站”：receipt 返回后 modal/backdrop 消失、目标从列表消失、当前 selection 回退到有效会话并回焦“新对话”。退出该副本并重开后 AX readback 无 `移入回收站？` 且无该验收目标；这不是 OPPO、Provider 或外发证据。

## FB-P6-036 Desktop splitter 单一 visual stroke（已关闭）

- active token 是旧 3px 的精确 20%，即 0.6px；8px interaction width、role=separator、pointer capture、Arrow、double-click reset 与 local persistence 未改。唯一 `::after` 是 visual owner，hover/focus/active/drag 强制透明 hit box、0 border/outline/shadow。
- 本机 Playwright computed scan 实测 hover/focus `::after` 为 `0.59375px`（0.6px 的设备像素栅格化）、left 3.7px、橙色；divider 本体为 8px、0 border/outline、透明背景。最新 ad-hoc signed `.app` 的实际 focus 与 drag 截图通过；AX drag value 从 268 改为 342，未缩小可拖区域。

## FB-P6-038/048 Desktop Settings IA（已关闭）

- 最新 ad-hoc signed `.app` 实际打开 Settings：AX 只有左侧“设置分类”与其中顶部“返回应用”，右侧无“设置中心 / 隐私与数据管理”标题层；`本机数据与默认行为` 不在 DOM/AX tree。风险/数据范围说明、设置项、状态和操作入口仍保留。
- 点击返回应用回到原 `P6-D 隔离长列表验证会话`；退出该隔离实例重开后仍在同一 chat selection，Settings 未幽灵恢复且重复说明未出现。

## FB-P6-034 角色化工具可见性回归（已关闭）

- Desktop Node 49/49 与 Android `P6DConversationRowAccessibilityContractsTest` 已通过。Desktop 仅 Assistant `.chat-message-tools` 常显；USER 仍 hover/focus。Android Assistant 正文下使用 Material 复制、分享、分支和真实时间/model 行，USER 仍是 app-owned long-press sheet，附件 long-press 独立。
- Android `emulator-5554` 已重建 Debug、`install -r` 后运行 deterministic fixture，截图可见 Assistant 开放正文下复制→分享→分支→时间；force-stop/restart 回到同一普通会话路径。是 AOSP emulator，非 OPPO/ColorOS 证据。
- Desktop 最新 `.app` 已重新 bundle、ad-hoc strict verify，并在实际窗口看到 Assistant 开放正文下的复制→分享→分支→时间行；完整退出该精确进程、重开后同一持久化 transcript 行仍可见。Desktop 与 Android 分别取证，未操作 OPPO。

## 已实现与唯一所有者

- Android 由 `ConversationTranscriptPresentation` / `MessagePresentationRenderer` 提供 message-owned metadata，`ConversationWorkspace` 仅投影：角色 surface、日期分隔、本地时区的完整时间、可访问名称、纯文本复制与既有分支 owner。
- Desktop `chat-shell.mjs` 只投影 message-owned metadata 和 action；Rust `branchFromMessage` 是 typed intent、revision 检查、幂等前缀复制与 SQLite reopen readback 的唯一分支 owner。
- copy 的输出是呈现块的 plain text；不包含 metadata、Key、Token、path、URI 或隐藏字段。TEMPORARY_SESSION 不进入 normal action surface，因此没有 share、branch 或 export。

## 自动验证

- Android：`JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` 下运行 `:app:testDebugUnitTest :app:lintDebug --rerun-tasks`；80 suites、264 tests、0 failures、0 errors；lint 无 error（既有 warning 未扩大）。
- Desktop frontend：`npm test` 32/0，`npm run lint`、`npm run typecheck` 通过。
- Desktop Rust：`cargo fmt --check`、`cargo clippy -- -D warnings`、`cargo test` 38/0 通过，其中包括 `p6f_branch_from_message_is_typed_idempotent_and_survives_reopen`。
- `python3 scripts/check_feedback_ledger.py`：`FEEDBACK_LEDGER_CHECK OK rows=22`。

## Android 实际 UI 与安装读回

- 只使用既有可删除的“本地对话”fixture；未清数据、未新建伪数据、未安装测试 APK。
- `emulator-5554` 启动后进入该对话，实际点击可见的“复制”。系统 clipboard service 回读为 `text/plain`，值精确为 `请运行本地确定性 fixture。`（35 UTF-8 bytes）；安全断言确认不含 Key、Token、Authorization、`content://`、`file://`、路径或反斜杠。
- 随后 `am force-stop com.nanzhufeng.ai` 并冷启动；入口仍显示 4 个本地对话和同一“进入本地对话”路径，证明未因复制破坏持久化 readback。
- 当前安装包 version `0.3.0-p10a` / code 51；本地 Debug `app/build/outputs/apk/debug/南枫AI-开发验收.apk` 与从设备回拉的 `base.apk` SHA-256 均为 `4692db7b855af9bee2972994c5d3333280b83f707aa638ec9a16925389036d95`。复制 UI 截图：`/tmp/nanfeng-ai-p6f-android-copy.png`，SHA-256 `a9d89aeecb2b950858dcdd369a0da764879c2fc5e0237e8cf958c8b877e9a7e0`。

## 分享、Desktop 包与边界

- Android 的明确确认 `ACTION_SEND` chooser 和取消无写入/无外发已在本阶段先前验收；本次只补唯一缺失的 copy 实际剪贴板抽查。
- Desktop 原生系统 share 没有安全且可验证的 owner。按用户明确许可，入口保持隐藏；未用 Web share、浏览器跳转或其他外发替代，不把它叙述为已通过 Desktop share 验收。
- Desktop 最终开发包为 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`。本轮发现原 02:32 签名资源完整性漂移，已仅对该本地开发包重作 ad-hoc 签名；`codesign --verify --deep --strict` 通过，Identifier `com.nanzhufeng.ai.desktop`、TeamIdentifier `not set`，可执行 SHA-256 `e3e0ae9ae4f578d22988d59869bcea034fc415dac349260772f716a6ef776b30`。这不是 Developer ID、notarized 或发布包。
- 未读 Key、未发 Provider HTTP、未操作账号/同步、未外发图片、未操作 OPPO，也没有改动图标或发版。

## 退出结论与下一步

## 040—043 最新包真实复核（2026-08-14）

- Desktop 包：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，`codesign --verify --deep --strict` 通过；可执行 SHA-256 `baa7b45bfbf1f9c616da90846bcea86d8c1816bebb3a22f7fc12f239301bf5d2`。040 的 normal UI 选择 fixture 与 Auto 均在完整退出/重开后读回；041 的 add/send glyph 与原 surface 同屏可见；042 顶栏只含对话/工作/Ghost，Settings 只在 sidebar footer。043 用正常 UI 新建 `P6F043-DESKTOP-CONTROLLED-20260814-0`，保存 14 条受控本地消息；离底按钮出现、点击后 AX 无“到最新消息”而 Composer 仍存在。退出/重开后该会话仍在 sidebar，重新选中读回 19 个标识；重启后再次回底并隐藏。最后仅以右键菜单→删除→确认“移入回收站”软删除，退出/重开后无该标识且无 stale dialog。
- AOSP：`emulator-5554`，`com.nanzhufeng.ai/.NanfengAiActivity`，version `0.3.0-p10a` / code 51。正式签名 Debug 的 v2/v3=true、证书为既定 SHA-256；`app/build/outputs/apk/debug/南枫AI-开发验收.apk` 与回拉 `docs/evidence/p6f-043-android-installed-base.apk` 均为 `cddef5404a9587dac77e1663850b4490d652fb1cbca299cf4071a9eacd70a8ea`。Drawer→新建本地对话创建空白普通会话，Composer 正常保存 `P6F043-CONTROLLED-20260814-*`，未写入 fixture 或 DB；离底控件实际位于 `[538,1935][604,2001]`。点击后 `p6f-043-android-after-tap.xml` 与 `p6f-043-android-restart-after-tap.xml` 都不含“到最新消息”、仍有 Composer；force-stop/restart 的 `p6f-043-android-restart-retry.xml` 重新读到受控消息和离底控件，第二次点击仍稳定隐藏。以已验证标识重新选择会话后，经 Drawer 长按→删除→“移入回收站”确认；`p6f-043-android-deleted-restart.xml` 无标识且正常 Composer 可用。
- 首次 AOSP 立即重启时曾看到系统 ANR；logcat 同时记录 347 skipped frames、首屏约 15.4 秒及并发 `uiautomator` 注册异常。关闭该系统对话后采用单通道、等待 22 秒的 restart，受控 transcript 正常读回；该环境干扰和失败样本保留，未以其作为通过证据，也未改动产品源码。AOSP 不是 OPPO，未读写 Key、未发 HTTP、未触及 044 或 050。
- Drawer 纯白视觉修正的最终 AOSP 包：定向 `P6DConversationRowAccessibilityContractsTest` 通过，`app/build/outputs/apk/debug/南枫AI-开发验收.apk` 的 SHA-256 为 `af45c6d5abd88fbef48744218b64bbb42613c30e582444c1f5c894e9b74d0e94`；v2/v3=true，证书 SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。`emulator-5554` 已以该包 `install -r --no-incremental`，force-stop/restart 后前台为 `com.nanzhufeng.ai/.NanfengAiActivity`，回拉 `docs/evidence/p6f-043-android-white-installed-base.apk` 同 hash。正常 UI 打开 Drawer 的 `p6f-043-android-white-drawer.xml` / `.png`（SHA-256 分别为 `25b511c054e41a4414047e210ccf9f5a0e181223297d28e6f23ca075d553773c` / `726103adfaf7486ce7d86d06cae101dd1b6869e3330805e4e7471015d9e36747`）显示抽屉纯白、无偏紫。`cddef540…` 是前序 043 功能证据身份，明确不作最终 Drawer 视觉证据。

## FB-P6-043 CLOSED 证据（2026-08-14）

- Desktop 修复了截图确认的“点击后闪烁不动”：最新 ad-hoc strict-signed `.app` 完整退出重开后，离底部时 AX 可见“到最新消息”，实际点击后等待动画完成，AX 回读该按钮不存在且 Composer 仍可见。可执行 SHA-256：`2035a3a22e6bf0862622345b6f985853239e3ddbc798c7b7a4a197433fe4a0ab`；Desktop Node 57/57、043 专测、lint/typecheck/build 均通过。
- Android 043 Compose owner/anchor 定向 JVM 合同、Debug lint/assemble 已通过。AOSP `emulator-5554` 仅安装正式签名 Debug：`install -r`、force-stop/restart 及 UI readback 完成；本地 APK 与回拉 `base.apk` SHA-256 同为 `7acc3c5252bc18d4506eea6650afdba517e5f9874a66fc1bdf687ca78270265d`。通过正常可见 Composer 本地保存 18 条非敏感验收消息（无数据库注入、无 Provider）；真实下拉离底后按钮位于 `[538,1935][604,2001]`，点击后 UI dump 不再含“到最新消息”。force-stop/restart 后再次读到该按钮并再次点击，仍稳定隐藏。AOSP 非 OPPO。

## FB-P6-044 实施与可行性核查证据（2026-08-14，受约束阻断）

- Android：`ConversationTranscriptPresentation` 仅将匹配 assistant message 的持久化 `InvocationRecord.taskRun` 或同一 `ConversationRuntimeState` 的正 elapsed 投影为可空 `workDurationLabel`；UI 在开放正文前、左侧输出该 label，Assistant action/time/model 行不变。BLOCKED、零值、缺失 ledger/runtime 与 USER 均为 null/omit，不读取 Key、不建 RunSpec、不发 HTTP。`P6FTranscriptPresentationContractsTest` 覆盖 persisted 2 秒、runtime 2 秒、无 ledger 与 USER omit；`P6DConversationRowAccessibilityContractsTest` 锁定 label 在正文上方而不在 action row。
- Desktop：`assistantWorkDuration` 只接受 assistant message 的 persisted `run/import` 正 duration 或完整 start/end boundary；不读取 createdAt。`chat-message-work-duration` 在 open body 前呈现；User、0、缺失、无效 boundary 均不生成 DOM。Node FB-P6-044 contract 通过。
- 自动验证已完成：Desktop lint/typecheck/static build、`npm test` 58/58，Rust fmt/clippy/51 tests；Android 定向 JVM 28 tests、`lintDebug`、Debug/Release assemble。Desktop 最新 ad-hoc strict-signed `.app` executable SHA-256 `d8f93f063ce689ffded975188cccdbd386dd0b3aff8cd076ba00a00aac80c68b` 已完整退出/重开，既有 assistant transcript 仍有 actions/time 且无“用时/用时未知”。AOSP `emulator-5554` 安装最新正式签名 Debug，force-stop/restart 后同样 omit；本地 APK 与回拉 `base.apk` SHA-256 均为 `9e63839a36fb55aa228075239cf21a505c956823a1c450422d0448af015edd40`，证书 SHA-256 为既定值，v2/v3=true。AOSP UI 还实际触发本地 deterministic fixture；其持久化 start/end 为零 elapsed，故仍 omit，未将它伪装为 Provider 时长。正值真实 run/import 样本尚未获授权/存在，不能关闭 044 或开始 045。
- 044 专项可行性核查（只读源码、无 DB 注入/Provider HTTP/P6-I）：Android `ChatGptImportMessage` 与 Room commit、Desktop `ChatGptExportMessage` 与 SQLite migration 都只保留消息文本、创建时间和 imported model，未解析、未存储也未投影 duration；而 Desktop shell 的 `assistantWorkDuration` 虽会读取 `message.run/import`，Rust 当前没有给对话消息写入这些对象的 owner。Android 唯一可见本地流 `DeterministicFixtureStreamingAdapter` 以同一 clock instant 产生每个事件，且合同明令 fixture/local record 不得冒充真实模型。结论：现有授权范围内没有能经正常 UI 产生并重启读回的合规正值真实 run/import；因此未再做双端“正值”UI 以免把 fixture 或人为等待误作模型时长。`context_gate.py` 于本轮返回 HANDOFF，精确交接卡见 `/Users/nanzhufeng/.codex/docs/codex-workflow/handoffs/rollout-2026-08-14T15-26-54-019fff2a-ad04-7c62-abb9-f1237d0e079f.md`。
- 2026-08-14 受控验收实现：Android 活跃 Settings 仅新增 `P2MRealServiceStatusCard`，经白色 `P2MRealServiceConfirmationDialog` 展示当前已保存服务商/预设/实际模型、费用上限、固定非敏感文本与默认未勾选确认框；发送中禁重复。`P2MRealServiceReadinessUseCase` 只读配置、凭据存在性和已验证目录，RunSpec 动态绑定当前保存 preset，`retryCount=0`；`NanfengAiActivity` 的旧外部 P2-M Intent 入口已移除。定向 P2-M 合同测试、`lintDebug`、Debug assemble 均通过。
- AOSP API 35 `emulator-5554`：正式签名 Debug APK SHA-256 与设备 `base.apk` 均为 `bd74d2886baffaa906a5c96c4ed0142c4781beac9ed4f29ebfb37efd49cfbc5a`，v2/v3=true，证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`；以 `install -r --no-incremental` 安装、force-stop/restart 后读回 status card。正常 UI 已验证确认页的白色表面、已保存服务商/预设/实际模型、费用、未勾选确认框和确认按钮；请求正文未写入截图、日志、证据或本文件。
- 用户授权后仅点击一次确认：持久化安全 metadata 与 restart/readback 显示 `BLOCKED`、`ProviderCredentialInvalid`、`attempts=0`、无正值 duration。故实际 Provider HTTP/Authorization/响应为 **0**，没有隐式或第二次重试；单次 nonce 为 **1/1 已消费**。该 P2-M TaskRun 未绑定同一 transcript assistant message，Desktop 也未请求且不得伪造，044 仍不 CLOSED；045/046/047/049/P6-I 未开始。若继续，须先经既有可见配置路径保存可用凭据，再获新的单次验收和消息 owner 绑定授权。
- 2026-08-14 owner 补齐：`P2MTranscriptBindingContractsTest` 覆盖成功 TaskRun→唯一 assistant Invocation 关联→`用时 2 秒` 投影、重新构造 owner 的稳定重放、失败/取消零 message，以及成功但零 elapsed 的 omit。`P6FTranscriptPresentationContractsTest` 与既有 P2-M/可见确认合同一并通过；`lintDebug`、Debug assemble 通过。`P2MTranscriptOwner` 不依赖 transport、credential store 或 nonce，`ModelSettingsViewModel.refresh` 只执行成功 ledger 的本地重放，因而进程重建不会追加第二条 assistant。
- AOSP API 35 `emulator-5554` 安装最新正式签名 Debug（APK 与 `base.apk` SHA-256 均为 `755f60c3a1adeabe01d11c71900899ba232a34c5562fa3319697749e6fb9bc7d`；v2/v3=true；既定证书）后 force-stop/restart，现有失败记录仍读为 `BLOCKED / ProviderCredentialInvalid / 未获得正值`。没有以 DB 注入构造成功样本、没有 Provider 调用、重试、Key 操作或 OPPO。成功 owner 的 restart 重放已由 `ModelSettingsViewModel.refresh` 接合并由 JVM 确定性 owner 合同覆盖；真实 UI 正值仍等待新的、另行授权的单次请求。

## 2026-08-14 可见反馈重新审计与 FB-P6-045（已关闭）

- 用户暂停 044 的 Provider/凭据关闭门：没有新 Provider HTTP、Key 读取/写入、重试或 OPPO 操作；044 的 nonce 仍为 1/1 已消费。可见队列按 045→046→047→049→050 推进。
- 最新 Desktop `.app` AX 仍见旧 placeholder `输入内容…`；AOSP 当前默认可见会话有 9 条 `FBP6043localacceptance*`。两者均已作为回归/污染缺陷登记，040–043 的旧关闭证据改为复核中，不以旧静态测试或截图覆盖。
- 根因修复：`createDevelopmentConversation()` 现在只创建空白本地会话，不再自动写入 deterministic fixture；非正真实服务 duration 返回 null/omit，不能显示 `0.0 秒`。`P6DConversationRowAccessibilityContractsTest` 覆盖这两个边界。
- FB-P6-045：Android User bubble 使用 content-driven `BoxWithConstraints` + 82% responsive max，没有视觉 min-width；Desktop 的 `fit-content` + max-width 规则由专用 Node contract 锁定。Android 最新正式签名 Debug 证书为既定 SHA-256、v2/v3=true；AOSP 真实 UI 的 2-char/short/long 用户文本 bounding widths 依次为 52px、423px、787px，long 在 max 内多行且右对齐。force-stop/restart 后三条仍一致；本地 APK 与回拉 base.apk SHA-256 均为 `b33efdfa5d2857e5779af20af0304d597267f3cfaffbf5e3373a50902c00b41c`。
- 两个 `FBP6045` 受控会话均通过已有可见“移入回收站”软删除，force-stop/restart 后不可见。新建空白会话经 UI 复测不再含 fixture，随后同样软删除。旧 FBP6043 会话没有安全的逐消息删除 owner，且未证明为专属验收会话，故未删除。
- 自动验证：Desktop lint/typecheck、Node 59/59、Tauri `npm run build && cargo tauri build --bundles app` 通过；Android 定向 JVM、assembleDebug、lintDebug（0 errors）通过。Desktop 最新 `.app` 已重建并由原生 AX 加载。
- **Desktop 真实关闭：** 最新 bundle `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app` 已 `codesign --verify --deep --strict`；可执行 SHA-256 为 `baa7b45bfbf1f9c616da90846bcea86d8c1816bebb3a22f7fc12f239301bf5d2`。以其 ad-hoc 签名隔离副本在现有 `/tmp/nanfeng-ai-p6h-acceptance-20260814` acceptance root 中，通过正常 UI 选择新对话并保存受控的 2 字、短句、长句，视觉截图确认三条 USER bubble 均右对齐，短文本未被最小宽度拉宽，长句仅到 responsive 上限才折行。截图为 `/tmp/nanfeng-ai-fb-p6-045.kMIkFQ/fb-p6-045-desktop-bubbles.png`（SHA-256 `64f38e1fdb2a67db0dbc91482f094399d7e10607c2d884064b30fcabd1c68570`）。完整退出该副本、确保无第二个该副本实例后重开，正常列表重新选择该受控会话，AX 逐条读回三种文本与精确 placeholder `回复 南枫AI`。此路径未读写 Key、未发 Provider HTTP、未操作 OPPO。
- 重开读回完成后，该会话被用户通过窗口写入，因此不再满足“可安全删除”的验收专属条件；已停止删除操作并保留。未删除任何不能严格证明为受控的会话，未作 DB 注入或 DB 绕过。
- FB-P6-046 CLOSED：移除 Desktop 已失效的 `.chat-search-wrap > span` 标题样式，并锁定 DOM 不含该标题节点；输入 placeholder/aria-label、执行动作、搜索历史保留。Desktop Node 60/60、latest strict-signed `.app` 退出重开 AX 通过。Android drawer 的唯一两个“搜索”文本是 OutlinedTextField label 与提交按钮；force-stop/restart 后仍为两处，正式签名 base.apk hash 与本地一致。 
- FB-P6-047 CLOSED：Desktop 新对话从 `icons.plus` 切为既有 Lucide `icons.edit`（文档＋斜笔）；Android 使用同一轮廓 `ic_lucide_file_pen`，不改变按钮 owner 或文字。Desktop Node 61/61、strict-signed `.app` 退出重开 AX 通过；AOSP 最新包截图确认 icon/text 在既有橙色胶囊内居中，未触发创建写入。

## P0：指定 af45c6d5 包的现场污染复核（2026-08-14，未关闭）

- 复核前现场事实：工作区 Debug APK 为未登记 SHA-256 `0357ec47f0ac3d160d6b523858ad3089a51312061f18c49ac4361a3635cd2a09`，设备 `base.apk` 同 hash；设备为 AOSP `emulator-5554`，包 `com.nanzhufeng.ai`，`0.3.0-p10a` / code 51。
- 按指定证据包 `docs/evidence/p6f-043-android-white-installed-base.apk`（SHA-256 `af45c6d5abd88fbef48744218b64bbb42613c30e582444c1f5c894e9b74d0e94`）执行 `install -r --no-incremental`。该 APK 的 v2/v3=true、证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`，后以 `am force-stop` 与显式 `NanfengAiActivity` 冷启动确认前台。
- 可见结果与用户期望相反：首屏已包含 `P6F045-049-AOSP-CONTROLLED-20260814`、`AB`、短样本与长样本；打开纯白 Drawer 后，当前会话仅呈现“本地对话”这一泛称。无法依据 UI 证明该整会话没有用户写入，故未调用“删除/移入回收站”、未清数据、未 DB 注入或绕过，也未新建任何会话。该 P0 使当前现场不能作为 045–049 无污染 UI/force-stop/restart/readback 证据，亦不满足 050 前置。
- 未读写 Key、未发 Provider HTTP、未操作 OPPO；这是 AOSP 现场状态记录，不是 OPPO、Provider 或发布结论。
