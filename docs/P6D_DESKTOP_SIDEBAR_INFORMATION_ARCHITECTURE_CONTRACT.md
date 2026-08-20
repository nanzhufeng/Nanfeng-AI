# P6-D 双端侧栏生产信息架构增量合同

日期：2026-08-13  
状态：已授权实现；仅限 app-private 隔离 workspace 验收

## 唯一目标

在既有 Desktop chat-first shell 和 Android Compose 对话工作区内，让“对话”和“工作”两种模式共享同一信息架构：顶部功能、置顶会话、内容列表。Conversation 的置顶、取消置顶、归档与恢复必须经各平台 Domain owner 的 typed command 事务持久化，并在重新打开同一隔离 workspace／Activity 后由真实投影读回。

这不是 P6-D 既有长列表/窗口恢复合同的返工，也不是 Provider、同步、跨应用、OPPO、Windows、图标或发布任务；Android 是本轮同步交付对象，不能以“后续规划”替代。

## 概念所有者与入口矩阵

| 入口或消费者 | 是否存在 | 本轮关系 | 唯一所有者/公开路径 | 最小验证 |
| --- | --- | --- | --- | --- |
| 对话模式侧栏 | 存在 | 受影响 | chat shell 投影 → `mutate_desktop_domain` | 顶部、置顶、活动或归档列表均可操作 |
| 工作模式侧栏 | 存在 | 受影响 | 同一 chat shell；当前 workspace 限定 Conversation scope | 保留工作功能入口，同时显示同一三段会话结构 |
| 宽屏 rail | 存在 | 受影响 | CSS `>= 900px` 的侧栏呈现 | rail 可显式折叠/展开，不改变业务状态 |
| 窄屏 drawer | 存在 | 受影响 | chat shell `sidebarOpen` | drawer 打开/关闭、Escape、窄→宽恢复保持既有合同 |
| Android 对话/工作导航 | 存在 | 本轮同步实现 | Compose `ModalNavigationDrawer` / 分层 route → ViewModel → Domain | 模式功能、置顶、活动或归档内容列表均可触控 |
| Android 会话置顶/归档/恢复 | 新入口 | 本轮同步实现 | `ConversationManagementDomain` → Room typed intent/repository | trailing “操作”中置顶/取消置顶、归档/恢复；Activity/restart 读回 |
| Conversation 排序 | 存在 | 受影响 | Rust Conversation Domain 写入的 `pinned/archived/revision/updatedAt` 投影 | active：置顶优先，再更新时间倒序，再 ID 升序 |
| 置顶/取消置顶 | 新入口 | 受影响 | `conversation:setPinned` typed mutation | hover、焦点和键盘均可达；重启读回 |
| 归档/恢复 | 新入口 | 受影响 | `conversation:archive` / `conversation:restore` typed mutation | 明确“已归档”入口；恢复后回到活动内容列表 |
| 行级补充菜单 | 新入口 | 受影响 | Desktop 右键 context menu；Android 长按 modal/bottom sheet → 同一 typed intent | 对话/工作菜单语义差异、取消不写入、重启读回 |
| 项目归属 | 存在 | 受影响 | Conversation typed `ASSIGN_PROJECT/REMOVE_PROJECT` 或 Rust typed mutation | 项目选择层只列真实项目；空/当前/跨项目/取消/重开覆盖 |
| 软删除/回收站 | 存在 | 受影响 | Conversation `SOFT_DELETE/RESTORE_DELETED`；不物理删消息树 | 二次确认、回收站恢复、revision receipt 读回 |
| Composer 常驻“＋” | 新入口 | 受影响 | 同一 action registry；Android 真实图片 picker/private copy | Desktop popover、Android bottom sheet；取消保留草稿 |
| 消息、草稿、滚动位置 | 存在 | 保持 | Conversation owner / chat shell 内存草稿与 scroll map | 不因侧栏操作丢失或重置未提交草稿 |
| Project/Knowledge/Memory/P8 | 存在 | 保持 | 既有 Rust domain / 只读 owner | 仍由工作模式明确进入，不成为会话假数据 |
| Key、Provider HTTP、同步、跨应用、OPPO/Windows、图标 | 存在或后续 | 不受影响 | 本合同外 | 无读取、写入、HTTP、包或图标变更 |

## 状态与不变量

| Conversation 状态 | 可见位置 | 允许动作 | Rust 持久化结果 |
| --- | --- | --- | --- |
| 活动且未置顶 | 内容列表 | 选择、置顶、归档 | `archived=false, pinned=false` |
| 活动且置顶 | 置顶区 | 选择、取消置顶、归档 | `archived=false, pinned=true` |
| 已归档 | “已归档”内容列表 | 恢复 | `archived=true, pinned=false` |

- 归档一定清除置顶；恢复为活动未置顶。已归档会话不得置顶。
- 每个生命周期动作必须携带 stable `intentId`、`workspaceId`、对象 ID 与 `expectedRevision`；同一 intent 重放只回读 receipt，revision 冲突不覆盖。
- 归档绝不删除或改写消息树、附件、Invocation、草稿、工作区或项目归属；恢复不创建副本。
- 前端不缓存或伪造 `pinned/archived` 真值；写入后只刷新 Rust SQLite projection。

## 侧栏与响应式合同

1. 两种模式都按“顶部功能 → 置顶 → 内容列表”排列；底部账号/设置保留为独立固定低频入口，不计为列表段。
2. 顶部功能包含新对话、搜索、明确的“已归档”入口；工作模式在同一段增加现有工作功能入口与 workspace scope，不能把工作模式默认右侧改为 dashboard。
3. 置顶区仅显示活动且 `pinned=true` 的当前 scope Conversation；内容列表默认显示活动未置顶项，打开“已归档”后显示归档项及恢复操作。
4. 行的“置顶/取消置顶”和“归档/恢复”在鼠标 hover 或键盘 focus-within 时出现；按钮有中文 `aria-label`、可 Tab 到达、Enter/Space 可执行，且不触发行选择。
5. 宽屏 rail 可由用户显式折叠为窄栏并恢复；这只是 chat shell 瞬时 UI 状态，不写 SQLite、草稿、浏览器存储或窗口 bounds。窄屏继续使用已有 drawer，不新造第二个导航树。
6. 重用现有图标资源和文字按钮；本轮不新增手写 SVG、图标资产或 launcher/dock 图标改动。

## 行级菜单与 Composer 动作合同

- Desktop 右键/键盘菜单与 Android 长按 popup 使用同一无标题四行格式：只显示图标、动作文字与项目右箭头，删除为错误色；不显示会话标题、“危险操作”、分区 divider 或大按钮。两端都以被触发会话的**可见标题**为锚，菜单 leading edge 与标题左边对齐、优先在标题下方显示、空间不足才向上翻转；不得把整行的日期、hover action 或 sidebar 左上角当锚点。Android popup 必须由会话工作区根层持有，不能嵌在 `ModalDrawerSheet` 内，否则手势虽被识别但面板会被 Drawer 模态层裁切。Android 为 `224dp` × `48dp` 行/`22dp` 图标/`20dp` 圆角/`6dp` 柔影；Desktop 按指针密度映射为 `192px` × `48px` 行/`22px` 图标/`20px` 圆角和双层低透明度柔影。不能出现底部硬切边或放大的卡片比例。Android 选中会话仅使用浅橙背景，文字始终为 `BodyText` 黑色，不能继承主题蓝紫色。hover/focus 快捷置顶/归档保留，菜单只是补充入口。所有写入共享 `intentId + expectedRevision + receipt`，取消绝不产生写入。
- **对话模式菜单仅有**：置顶/取消置顶、重命名、添加到项目、删除。不得出现归档、Finder/目录、复制 ID/深度链接或继续树等工程项。删除必须二次确认，进入软删除回收站，永不物理删除消息树。
- Android 会话重命名是无标题的紧凑单字段 Dialog，不得回退为通用 `AlertDialog` 的大块 title/body 留白。Dialog 必须左对齐抽屉侧，最大 `336dp`、左右各 `12dp` 留边，不能按整个屏幕宽度计算而越出 Drawer；输入面固定 `48dp`，操作行固定 `44dp`，仅保留“会话标题”、取消、保存。标签只装饰边框、不占用编辑高度；横向接近抽屉可用宽度，纵向比旧默认 Dialog 压缩约三分之一。命名校验、取消与 `ConversationManagementAction.RENAME` owner 不变。
- **工作模式菜单独立**：至少保留置顶/取消置顶、重命名、添加/移动/移出项目、归档/恢复和可恢复删除；不实现 Finder、目录、深度链接等非南枫能力。
- “添加到项目”打开项目选择层而非自由文本；空项目明确空态、当前项目明确标示、跨项目为移动、取消不变更；项目有效性与 Conversation revision 由 Domain owner 原子检查。
- Composer 左侧固定“＋”在对话与工作模式位置/语义相同，不能替代发送或模型选择。当前能力矩阵：Android `PickVisualMedia(ImageOnly) → AndroidGallerySelectionReader → app-private copy → Room draft attachment → 移除/重建读回` 为可用；Desktop 当前只存在交换包系统选择器，未注册 Conversation 附件 private-copy owner，故“＋”popover 只能诚实显示无可执行附件动作。相机（Android）、文件选择（Conversation）、插件、语音和深度思考均未注册，不显示假可点击项；后续 attachment action registry 必须双端同步立项。

## 跨端信息架构不变量（本轮双端验收）

- Desktop 与 Android 不是两套产品：两端都以“对话 / 工作”为核心模式，并使用“模式功能区 → 置顶区 → 会话或项目内容区”的同一层级、名称、排序、权限和持久化含义。
- 两端都必须具备工作区范围内对话优先、会话/项目置顶、会话归档/恢复、知识、记忆、受控记录、Composer 模型选择与统筹设置。模型 override、自动策略、权限和安全 metadata 未来也必须跨端同义。
- 仅容器按平台适配：Desktop 采用常驻/可折叠 rail、窄屏 drawer 与 hover/focus 行操作；Android 采用 drawer、分层页面和 trailing action。Android 不得机械压缩 Desktop 宽屏，也不得遗漏对应入口。
- 两端共享 `pinned/archived/revision/expectedRevision/undo` 语义、排序与状态命名；Desktop Rust SQLite 与 Android Room 分别是各自平台的唯一 owner。UI 不直接写库。

## 自动与真实验收

1. Node 合同覆盖三段渲染、排序、归档过滤、hover/focus 操作槽、宽屏 rail 与窄屏 drawer 边界。
2. Rust 单元测试覆盖 `setPinned/archive/restore` 的 expected revision、归档清置顶、重放/重开 SQLite 投影与消息不变。
3. Desktop 前端 typecheck、lint、test、build；Rust fmt、test、clippy、check；Tauri macOS `.app` 构建并仅以 ad-hoc 开发签名说明。
4. Android JVM/Room/UI 合同测试、lint、正式证书 Debug/Release 构建与签名检查；仅 `emulator-5554` 同签名 `install -r`，不清数据、不装 test APK、不操作 OPPO。
5. 真实 macOS `.app` 仅导入或创建明确可删除的 app-private 合成 workspace：会话 hover/键盘置顶、取消、归档、已归档入口恢复、宽屏 rail 折叠、窄屏 drawer、强制关闭后重启读回。不得触及任何既有长期 workspace。
6. Android 实际 Compose drawer 以“模式功能区 → 置顶区 → 会话或项目内容区”呈现；触控操作置顶/取消置顶、归档/恢复，force-stop/relaunch 后从 Room 读回。
7. Desktop `.app` 右键菜单与 Android `emulator-5554` 长按 sheet 覆盖菜单差异、取消、重命名、项目选择、置顶、软删除/恢复及重启读回；Composer “＋”的 Esc/返回/点外取消不得丢失文本或已有草稿附件。

## 停止条件

达到上述双端本地合同即停止。不得将本轮说成真实模型调用、同步、正式 macOS 发布、Windows/OPPO 验收或总蓝图完成。
