# 南枫 AI 可见失败反馈矩阵（第一版）

日期：2026-08-14  
审计范围：只读；用户授权的线程 `019ffef8-8772-7da1-858d-cbced22d7878`、其 source/handoff `019ffc0d-22de-7f63-aa8a-d63a0b40fb94`，当前 `PRODUCT_FEEDBACK_DECISION_LEDGER.md`、`CURRENT_HANDOFF.md`、当前 Android/desktop 源码检索，以及用户最近 7 张失败截图路径。  
审计结论：`PRODUCT_FEEDBACK_DECISION_LEDGER.md` 中的 **CLOSED 仅是历史工程记录，不构成当前可见 UI 完成**。任一行在最新 Desktop `.app` 与 AOSP 正常用户路径未重新通过前，均保持可见失败，禁止以旧截图、fixture、构建、签名或 DB/readback 取代本矩阵的验收门。

## 统一判定规则

- **正常聊天禁止工程 UI**：不得显示 `0.0 秒`、fixture、本地尝试、运行本地 fixture、重试、换模型重答、`LOCAL_RECORD`、安全实现说明或其它验收/调试术语。
- **信息架构**：主面只服务聊天；搜索只保留一个真实输入；导入只在 Settings；导入完成后是普通历史会话；Drawer 不承担设置或验收任务。
- **双端映射**：Desktop hover/focus 的消息与附件信息，Android 一律长按 sheet；非交互状态不占据聊天正文。
- **重新关闭门**：每行先写针对性自动合同，再以最新 Desktop `.app` 与最新正式签名 AOSP 包走真实 UI、完整退出/force-stop、重启读回。截图、AX/UI dump、包 hash 各自只证明其对应层，不能互相替代。OPPO、Key、Provider HTTP 保持本轮范围外。

## 可追溯矩阵

| 优先级/ID | 原始诉求与参考 | 当前源码/台账状态 | 已见失败证据 | 平台 | 重做顺序 | 可验证门 |
| --- | --- | --- | --- | --- | --- | --- |
| P0-01 | 正常聊天不得出现 `0.0`、fixture、本地尝试、重试、换模型、运行 fixture 等工程 UI；用户最新五张失败截图直接指出“满屏错误”。 | Android `ConversationWorkspace.kt:398-402,564-573,988-994` 明确仍渲染“换模型重答”“本地尝试历史”“本地 fixture”“运行本地 fixture”；台账曾将局部反馈 CLOSED。 | 最新失败截图组：`b871…png`、`a319…png`、`3a53…png`、`72ae…png`、`722a…png`；源码与图一致。 | Desktop+Android | 1：先定义 chat-facing projection 的允许字段；删除/隔离工程卡与验收动作；保留真实 owner 于 Settings/受控验收入口。 | 常规会话、导入会话、空会话、临时会话各无禁词；语义树亦无禁词；双端重启读回。 |
| P0-02 | `0.0 秒`不是模型工作时长；未知、零或负值应隐藏。 | `CURRENT_HANDOFF.md` 记录过“已改为 omit”，但本轮截图仍指出可见 `0.0`，故旧结论失效。 | 用户最新失败截图明确包含 `0.0`；这是当前红灯，不接受历史 formatter 说明。 | Desktop+Android | 2：统一 duration projection 的唯一 owner，先过滤再格式化；禁止 UI 层补零。 | 0/负/缺失/fixture/导入均无时长；正值仅来自同消息真实持久 run/import boundary；重启读回。 |
| P0-03 | Drawer 必须纯白。 | `CURRENT_HANDOFF.md` 声称 `ModalDrawerSheet(...Color.White)` 已验证，但不作为当前可见通过。 | 用户截图 `d4b1…png` 指出抽屉色“难看”，要求纯白；现有包/证据时间线不一致。 | Desktop+Android | 3：先锁根抽屉 surface token，消除主题/overlay 叠色；不得仅改一端。 | 明/暗状态的 Drawer 画布至边缘纯白；打开、关闭、重启后截图与像素抽样。 |
| P0-04 | 去掉静态“搜索”标题、安全说明和重复搜索按钮；只留一个真实搜索输入。 | Android `ConversationWorkspace.kt:625-631` 仍有 label“搜索”、supporting 安全说明、最近搜索区及独立“搜索”按钮；台账的 FB-P6-046 CLOSED 与源码冲突。 | 用户截图 `1b58…png` 要求删除左侧标题；最新五图再次认定信息过量。 | Desktop+Android | 4：重做 Drawer 搜索为单一输入 owner；提交由键盘/输入内 affordance，不追加第二按钮或说明文。 | Drawer 只存在一个搜索可见入口及一个 a11y label；输入、清空、历史、结果、重启都可用。 |
| P0-05 | 底部“设置 + 新对话”按参考图同一行、简洁、悬浮于聊天区域；不得做笨重的工程入口。 | 台账曾规定橙色胶囊“新对话”，但用户已明确要求重新审判；当前状态不可采信。 | 用户两张参考：`21c26842…png`、`f63bcd42…jpg`，原话要求“底部、同一行、悬浮、简洁版”。 | Desktop+Android | 5：先画同一 IA 的 drawer footer contract；Settings 与新对话均为真入口，再落地视觉。 | 宽/窄 Desktop、Android 外/内屏：同排、贴底悬浮、可访问、不会遮挡列表；新对话确为空白。 |
| P0-06 | 导入只能在 Settings；导入会话进入普通历史，不能在 Drawer 当成设置/验收数据。 | Android `NanfengAiApp.kt:510-532` 已有 Settings→数据导入路线；但 `ConversationWorkspace.kt:337` 仍显示导入工程解释，当前端到端可见行为未重新验收。 | 用户明确要求删除错误旧验收会话，并指出导入应自动展开为左侧普通历史。 | Desktop+Android | 6：保持 Settings 唯一导入 owner；导入成功的 projection 只写普通会话标题/日期/内容，不带验收标签。 | Settings→选择→确认导入→Drawer 普通历史→打开→重启读回；普通搜索可找、设置入口不污染 Drawer。 |
| P0-07 | 模型胶囊、用户气泡、新对话居中都要重新审判，不能根据旧 CLOSED 原样保留。 | 台账把 FB-P6-040/045/047 CLOSED；当前对用户可见效果已被否决。 | 用户反馈 `56133…png`、`5d149…png`、`f6d167…png`及最新失败组；台账结论与最新主观验收相冲突。 | Desktop+Android | 7：先以参考图重定组件几何/视觉合同，再实施；不在旧 token 上继续微调。 | 每项各一组空态/短文本/长文本/窄窗截图；新 `.app` 与 AOSP 实机路径重启读回。 |
| P1-01 | Android 主页直达聊天；不要底部多菜单；低频功能集中 Drawer 底部 Settings；Desktop/Android IA 对齐。 | FB-P6-023/025 标“实施中/部分证据”，不应说明完成。 | 用户三张参考 `0c020…jpg`、`022a…jpg`、`5aeae…jpg` 明确指出手机“乱七八糟”、与 Desktop 不统一。 | Desktop+Android | 8：先统一根壳导航和对象范围，再修单控件。 | 冷启动、Chat↔Work、Drawer、Settings 返回、草稿/会话连续性；无 bottom nav/根级工程模块。 |
| P1-02 | Assistant 使用开放正文主列；用户在右侧暖橙气泡；日期/时间/模型/动作按消息归属；回底按钮在输入框上方、离底才显示。 | 台账声称多项已关闭，但不能覆盖用户参考 `7d464…jpg` 的总体不符。 | 用户明确“严格参考图片效果”，并指出回底“卡着闪烁不动”（`c4fc…png`）。 | Desktop+Android | 9：先修 transcript projection/scroll owner，再做表面。 | 多角色、长文、离底→点击→到底隐藏、重启回读；固定 composer 不遮挡正文。 |
| P1-03 | 用户消息信息仅 Desktop hover/focus；Android 长按 sheet；AI 信息可常显但不污染正文；三图标前、时间/模型后。 | 旧台账曾 CLOSED；其闭环需重新验。 | 参考 `53925…png`、`ab554…png`、`7a69…png`、`0669…png`、`a3f187…png`。 | Desktop+Android | 10：组件拆成 message content、message actions、attachment overlay 三个 owner。 | Desktop hover/focus/Tab 不跳布局；Android 长按 sheet；copy/share/branch 真动作；时间/模型只显示真实字段。 |
| P1-04 | 附件无外层消息气泡；用户右、AI 左；文件名/MIME 仅预览 overlay（Desktop）/长按（Android），不占下面空间；文档方形、图像/视频按比例。 | 台账标 FB-P6-039 等 CLOSED，但不得据此跳过复核。 | 参考 `8c88…png`、`ec299…png`、`9aeb…png`、`6f95…png`；用户反复澄清“不是一回事”。 | Desktop+Android | 11：先保证 attachment sibling 结构，后调 preview aspect/overlay。 | image/PDF/video/audio/generic、text-only/mixed、多附件、重启读回；默认不显示附件 metadata。 |
| P1-05 | 列表标题在左、绝对日期在右同一行；hover 可暂时覆盖日期；日期不占第二行。 | 台账 FB-P6-028 CLOSED，但需重新可见验证。 | 参考 `aff813…png`、`e02ed…png`。 | Desktop+Android | 12：重做会话 row 的标题/日期/hover action 单一 layout owner。 | 长标题 truncation、日期 right aligned、Desktop hover/Android long-press、重启稳定。 |
| P1-06 | 复用参考中的 Codex/ChatGPT 成熟图标，不自造；图标比例、居中、点击轮廓一致。 | 旧 FB-P6-029 CLOSED，用户之后仍否决图标。 | `acd534…png`、`f6d167…png`，以及“统一使用之前 Codex 图标”的原话。 | Desktop+Android | 13：建立官方 Lucide/Material 映射，再清理残存自造 glyph。 | 每个可见动作有一致轮廓、tooltip/contentDescription、hover/ripple/focus，不含 emoji/手绘图形。 |
| P1-07 | 侧栏与聊天之间 divider 可拖拽；视觉弱 80%，但保留足够 hit target。 | 台账 FB-P6-026/030 历史关闭；需与新根壳一起复核。 | 用户文字要求可拖拽，`a546…png`要求减淡 80%，`1a624…png`要求激活线仅 20%。 | Desktop；Android 无同类常驻 divider | 14：视觉 stroke 与 8px+ hit area 分离，持久化仅 local width。 | pointer、keyboard、double-click reset、窄窗 fallback；视觉不出现多条分割线。 |
| P2-01 | 回收站确认后弹窗立即关闭；返回应用放左侧，不留冗余标题。 | 台账称 FB-P6-037 CLOSED；不以旧 receipt 作为本轮可见完成。 | `39fa…png`、`81ea…png`、`777446…png`。 | Desktop+Android | 15：统一 dialog state owner，成功 receipt 后清 target/dialog。 | 成功/失败/取消/Escape/Back、重启均无 ghost dialog；列表状态一致。 |
| P2-02 | 临时/验收 fixture 不得自动混入正常会话、历史、搜索或导入。 | `CURRENT_HANDOFF.md` 已承认 AOSP 残留 `FBP6043…` 污染，且当前无法证明会话归属。 | 用户要求删除明确旧验收会话，并说明导入与本地对话不应混入；当前 P0 数据污染未消除。 | Desktop+Android | 16：先建立可安全删除/隔离的 acceptance owner；严禁 DB/ADB 猜测式清理。 | 新空白普通会话无 fixture；TEMP/fixture/acceptance 不进入历史/搜索；仅用户授权后处理既有污染。 |
| P2-03 | Settings 独立页面、分组导航，不能照抄或塞进 profile/Drawer；保留搜索及数据导入等真实能力。 | FB-P6-026 历史 CLOSED，但用户提供五张 Settings 参考要求重新按思路审核。 | 用户 5 张 `IMAGE 2026-08-14 10:47:42-51.jpg` 与文字方案。 | Desktop+Android | 17：本矩阵 P0/P1 主聊天收口后，再独立 Settings 视觉/导航重做。 | Settings→子页→返回、搜索、数据导入、配置持久化与重启读回；Drawer 只保留入口。 |
| P2-04 | 内屏可有聊天位置快速导航/进度条；回底功能不能闪烁卡死。 | FB-P6-050 未实施；旧回底实现被用户报告失效。 | `07a2…jpg` 请求内屏进度条；`c4fc…png` 记录闪烁。 | Android 内屏（后续单独授权）；Desktop 可评估等价滚动导航 | 18：不混入本轮 P0 重做；单立 scroll/navigation 合同。 | 仅授权设备/内屏，真实长会话位置变化、隐私预览、折叠连续性；不可用截图或估算伪造。 |

## 执行禁令与起步顺序

1. 在 P0-01 至 P0-07 完成前，禁止把任一 ledger 的 CLOSED 写成当前 UI 可见完成，禁止开始新 Provider、OPPO、图标或发布工作。
2. 第一个实现批次只能处理 **P0-01 → P0-06** 的共享 chat projection、Drawer、footer 与导入入口；不得用对单页的 CSS/Compose 遮盖替代结构清理。
3. 完成后先做 Desktop 与 AOSP 的最新包真实路径，保留失败截图并回填本文件的“已见失败证据/可验证门”；只有通过，才进入 P0-07 与 P1 transcript 重做。
4. `P2-02` 的既有 AOSP 污染会话不得删除、清数据或以 DB/ADB 绕过；需要单独的用户授权或隔离环境。

## 本审计未声称的事项

- 未修改产品代码、数据库、设备数据或测试数据；未构建、安装、调用 Provider、读取 Key、操作 OPPO。
- 未把现有截图或历史包升级为真实验收；本文件是重做队列和关闭门，不是完成证据。
