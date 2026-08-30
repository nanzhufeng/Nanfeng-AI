# 南枫 AI 总控方案需求—证据完成审计（2026-08-16；2026-08-28 当前总控门更新）

## 2026-08-28 当前总控门：checkpoint、导入链、验证与冲突裁决

**本节是本文唯一的当前排程入口，优先于下方所有“当前”“已完成”“未完成”、旧 Schema、旧测试计数、旧 APK 哈希和旧设备结论。** 下方 2026-08-27 及更早段落只保留历史证据，不得返向覆盖本节所路由的当前合同、交接和代码。

### A. 当前基线与唯一事实源

- **稳定基线：** `c1c9ae0 feat: make ZIP asset recovery resumable`。它建立在 `6d68ba7 test: restore full Android JVM baseline` 上：保留 Android 会话、搜索、设置、运行时上下文与第三方导入，将 Room 提升到 Schema 56，并把 ZIP 附件恢复迁到持久化后台任务与按会话 checkpoint。用户已明确排除同仓库正在进行的 P2 及后续任务和全部未提交 WIP；本节不得把它们写成已完成。
- **Android 会话／抽屉／搜索／预览／暗色皮肤：** 只读 [Android 当前会话与搜索界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)。任何旧截图、P 阶段数值或静态测试锚点不得恢复已替换行为。
- **Android 设置／开关／卡片／居中模态：** 只读 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)。开关为共享 `SettingsSwitch` 的 `58×28dp`，居中模态使用轻量中性遮罩；不得重新应用历史百分比缩放或局部私有尺寸。
- **普通聊天的个性化、Memory、资料库、历史对话与回答来源：** 只读 [Android 当前运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)。`NormalChatOpenRouterExecutor → LocalContextBroker → ChatAdapter` 是当前普通发送上下文链；临时聊天仍是不调用模型、不读写普通上下文和自动沉淀的本机隔离链。
- **ChatGPT／Claude ZIP 的数据边界：** 只读 [P6-K 导入采纳合同](P6K_CHATGPT_CLAUDE_ZIP_IMPORT_ADOPTION_CONTRACT.md)。实包数量、测试耗时、APK 和设备事实仅读 [当前交接](CURRENT_HANDOFF.md) 顶部“最新有效交接”；根因过程可读 [ChatGPT ZIP 导入附件链路问题开发档案](ChatGPT%20ZIP导入附件链路问题开发档案.md)。
- **长期 owner 与取舍：** 只写入 [决策日志](decision-log.md)；版本、哈希、测试数和临时设备状态不在决策日志重复。新用户可见功能的 Android／Desktop 去留只记录到 [入口审计](ANDROID_DESKTOP_USER_ENTRY_AUDIT_20260816.md) 或对应领域台账，不恢复已删除的“设置 → 功能审阅”页。

### B. 当前已收口的增量（后续复查忽略重做）

| 范围 | 当前结论 | 禁止回退／重复实现 |
| --- | --- | --- |
| 会话阅读与返回栈 | 一键上／下按真实可见一屏步进，长按直达真实边界；手动滚动显示对应方向、静止 3 秒隐藏。收藏、归档、回收站和搜索定位打开对话后保留来源返回栈。 | 不恢复“一键到底”单次跳转、到底反弹、自动抢滚动、无法返回搜索／收藏来源的旧链路。 |
| 搜索、Markdown 与预览 | 搜索标题、分组强调、文件定位、聊天内查找灰卡／白输入面、Markdown 展示归一化和暗色本地文本／PDF 预览已按当前合同收口。 | 不将 Markdown 展示修正回写原消息，不把聊天内查找误当文本预览，不在暗色皮肤恢复浅色外层或白色控件残片。 |
| 设置与通用组件 | 资料库搜索位于启用记忆之后，模型设置无主题色线框，API Key 保存显示成功／失败，实时网页搜索卡保持紧凑，所有实际开关共用 `58×28dp`。居中模态只轻微压暗背景。 | 不再叠加开关百分比放缩，不恢复不可见关闭态、重黑遮罩或开关正常切换成功提示。 |
| 回答来源与临时聊天 | 普通 Assistant 回复可从既有页脚按需查看本次实际上下文来源与原因；临时聊天与普通历史、Memory、资料库、搜索、摘要、费用及 Provider 调用隔离。 | 不按当前设置反推历史回复来源，不把临时聊天伪装成普通模型发送或自动沉淀入口。 |
| ChatGPT JSON／ZIP 导入入口 | JSON 与 ZIP 保持两张独立大卡，各自的“导入结果／查看详情”始终可见；导入详情区分已导入对话、已恢复附件与缺少官方归属候选。 | 不合并两张卡，不用胶囊代替结果信息区，不因任务表为空而隐藏 JSON 结果入口。 |
| ChatGPT 累积对话与附件 | 旧包→新包以 provider source tree append-only 合并，最终 `784` 个去重对话。新包中 `853` 个官方明确归属且 entry 存在的附件全部进入普通 Message Tree；共享 file ID 的多消息引用保留，字节／catalog 去重，current leaf 一定选取恢复路径下的合法后代叶。 | 不从文件名、时间、相邻消息或内容猜配 `822` 个无官方归属候选；不恢复“一个 file ID 只能属于一条消息”或静默吞事务异常的旧实现。 |
| P1 后台恢复生命周期 | `c1c9ae0` 用 Schema 56 保存内容无关 job、进度、失败类型与按会话 checkpoint；后台调度 owner 负责续跑，页面只观察状态和发出重试／取消意图。 | 不把数分钟恢复重新绑回 ViewModel `show/list` 生命周期，不删除 checkpoint 或用 UI 显示值代替持久化事实。 |

### C. 验证、产物和设备边界

| 验证层 | 当前证据 | 可以／不可以宣称 |
| --- | --- | --- |
| 真实 ZIP 定向验收 | `af218e1` 三项实包测试合并耗时 `9m43s`：资产归属、`853/853` 完整 Room 链、旧包→新包 `784` 对话合并均为 `tests=1, skipped=0, failures=0, errors=0`。P1 `c1c9ae0` 又复跑新包附件 Room 链为 `1/0/0/0`、`257.597s`。 | 可声称 P1 的新包附件恢复在隔离 Room 自动验收闭环；旧包合并其余门沿用前一 checkpoint 证据，仍不可声称 OPPO 已恢复或真实 UI 已通过。 |
| 完整 JVM | `c1c9ae0` P1 标准记录为 `819 tests / 0 failures / 0 errors / 3 skipped`。修复前 checkpoint 为 `815 / 60 / 3 / 0`，分类阶段为 `815 / 59 / 3 / 0`，`6d68ba7` 为 `815 / 0 / 3 / 0`；这些只保留为阶段根因证据。 | 可声称 P1 标准 JVM 无失败；不可把 3 个 skip 写成已执行，也不可外推到真实 ZIP、OPPO、Provider 或视觉。 |
| Lint／Release | `lintDebug` 为 `0 errors, 84 warnings, 13 hints`，`assembleRelease` 成功。最新本地 APK 为 `66 / 0.3.0-p10j`，SHA-256 `b0d1008fde0bbbddaaad57d925e09ca067dbbb64407d9783904955a999481929`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，minSdk 26。 | 可声称本地产物可构建、可验签；不可将其写成已安装、已发布或真机业务通过。 |
| OPPO 当前事实 | OPPO Find N5 仍是 SHA-256 `d86b670e050da976aa07151928059b49a9e0936fd7422dbac5883ec683c7c390` 的旧诊断包，当时设置回读为 `1 个 ZIP 导入批次 · 784 个对话已导入 · 0 个附件已恢复`。本 checkpoint 之后未安装、未卸载、未清数据、未运行仪器测试。 | 仅能作为旧包设备快照；不可用来否定新代码的 JVM 结果，也不可用 JVM 结果反过来声称设备已升级。 |

### D. 当前剩余任务与顺序

1. **正在进行任务排除：** P2 数字口径及其后续阶段由另一任务继续，当前未提交代码、Schema、测试与 UI 不属于本次复盘基线；只在各自 checkpoint、完整 JVM 与对应真实门完成后同步本节。
2. **OPPO 附件链真实闭环（需用户再次明确授权）：** 只允许使用后续稳定 checkpoint 的同包名、同证书、非 Debug 正式 APK 保数据覆盖。覆盖后必须回读 APK 哈希、首次安装时间与设置数字，再人工检查恢复续跑、对话附件、搜索分组、预览、定位、下载／分享和返回栈。
3. **维持全量 JVM 门：** [P6-K 全量 JVM 失败分类](P6K_TEST_TRIAGE.md) 的 58 项过时合同／夹具和 1 项测试结构耦合已由 `6d68ba7` 收口，P1 继续保持 `failures=0, errors=0`。后续变更不得以修改产品合同、放宽安全 owner 或删除语义断言换取通过。
4. **当前 UI 真实视口复查：** 按三份当前合同检查浅／深色、Find N5 外屏／内屏、长回复滚动、弹层和系统返回；完成的局部改动不重做，只对与合同或真机不符的点继续收口。
5. **真实 Provider／费用／系统外部链：** 仍是独立验收门，只在用户自有合法配置、明确发送或打开动作下进行；不得由 JVM、Release 构建或截图代替。

### E. 持续同步规则

- 本文只保留“当前范围、唯一事实源、验证边界与下一顺序”，不复制合同中的全部视觉数值或交接中的每项耗时。
- 会话／搜索／预览改动同步会话合同；设置／开关／模态改动同步设置合同；普通上下文／回答来源／临时隔离改动同步运行时合同；ZIP 数据语义改动同步 P6-K 合同。
- 只有版本、测试、Release、设备或跨域结论变化时才同步 `CURRENT_HANDOFF.md` 和本节。只有长期 owner／安全边界变化时才同步 `decision-log.md`。
- 每次复查先比对当前 HEAD、工作树、合同和最新可复现验证；若与本节数字冲突，以实际代码和新验证为准，并同步修正本节，不得在文档之间平均取值。

## 2026-08-28 历史补充：ChatGPT 累积 ZIP 安全合并（已被上方当前总控门取代）

本节仅保留在附件链完整收口前，“旧包→新包去重合并已获 JVM 证据”的阶段事实。它不再定义当前 P6-K 能力、测试数、Release 或设备状态；当前结论只读上方总控门、P6-K 合同和当前交接。

- **当前能力：** 用户选择的累计 ChatGPT ZIP 可先后导入。相同 source conversation ID 且内容不变时复用本地会话；只新增消息时以 source-message provenance 在原树追加，不建立第二份历史。改写／删除旧消息、缺 provenance 或树歧义仍 fail-closed，绝不覆盖本地会话。
- **真实证据：** 用户明确选择的 2026-07／08 两个 ZIP 已在隔离内存 Room 完成旧→新顺序验收：最终 `784` 条有效文本会话无重复，侧栏排序、搜索索引和设置任务回读均通过；无安全文本的对象如实保留 `EMPTY_CONTENT`，非文本／思维节点不进入会话。该结果不是 Android DocumentsUI、app-private staging、真机、Release 或媒体归属验收。
- **新增风险：** 真实包 JVM 导入约耗时 `5 分 7 秒`，当前逐会话事务与索引重建尚未批量化；后续必须先补导入进度／性能与隔离 Android 文件路径验收，不能直接将本地 JVM 结果称为用户设备导入完成。

## 2026-08-27 历史总控门：合同收口、已验证增量与未发布代码分层（已被 2026-08-28 当前门取代）

本节只保留 2026-08-27 当时的合同路由和验证分层证据，不再是排程入口。其中 `796 / 60`、旧 Release 状态和“附件待验收”等描述均已被上方 2026-08-28 当前总控门取代；三份当前合同的路由依然有效。

### 当前事实源与冲突裁决

| 事实类别 | 唯一／优先事实源 | 当前使用边界 |
| --- | --- | --- |
| Android 会话、抽屉、搜索、预览与暗色皮肤 | [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) | `ConversationWorkspace` 的顶栏、滚动、抽屉、收藏／归档／回收站跳转、聊天内查找、搜索定位返回、文件预览、Markdown 投影、弹层与皮肤均按此合同；不得从旧 P 阶段或截图记录取回颜色、尺寸或手势。 |
| Android 设置、开关与居中弹层 | [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md) | 设置 IA、资料库搜索位置、模型入口、网页搜索卡、全局开关 `58×28dp`、关闭态、保存反馈与共享轻量遮罩均以此为准；不以历史百分比缩放或页面私有实现覆盖。 |
| 普通聊天上下文、回答来源与临时聊天 | [Android 当前运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md) | `NormalChatOpenRouterExecutor → LocalContextBroker → ChatAdapter` 是普通发送唯一上下文链；回答来源只读实际绑定 Attempt；临时聊天为最多 24 小时的本机隔离链，不调用模型、不读写普通上下文或自动沉淀。 |
| 实现、定向验证、正式包与设备事实 | [当前交接](CURRENT_HANDOFF.md) 顶部最新有效记录 + 当前工作树 | 只按最近的改动和验证理解。JVM／编译、Release、覆盖安装与真机观察分层，任何一层都不能替代另一层。 |
| 长期所有权取舍 | [决策日志](decision-log.md) | 只存仍生效的 owner 与冲突裁决；不复制版本号、APK 哈希、测试计数或临时 UI 状态。 |
| 蓝图与旧审计 | `MASTER_DEVELOPMENT_BLUEPRINT.md`、本文件后文历史段落 | 用于完整 P0–P11 路线与证据追溯，不能再宣称“当前唯一入口”、当前 APK 或当前验收结果。 |

### 当前已收口范围

| 范围 | 当前结论 | 复查时必须避免的冲突 |
| --- | --- | --- |
| 会话与阅读 | 一键上下阅读按真实滚动边界、短按一屏、长按直达端点，静止 3 秒隐藏；手动滚动不再被自动跟随抢回。收藏、归档、回收站和搜索来源均有回到来源页面的路径。 | 不得恢复固定 dp 跳转、末项顶部对齐、常驻按钮或“进入对话后丢失来源”的旧行为。 |
| 搜索、文本与预览 | 搜索标题／分组、文件定位、聊天内查找高亮及其灰色外卡＋白色输入面已按当前合同收口；Markdown 仅做内存展示归一化；深色本地文本／PDF 外层与控件使用深色语义 surface。 | 不得把聊天内查找误改成文本预览，也不得因修复 CSS 色值或转义符而改变原消息／搜索索引。 |
| 设置与通用组件 | 设置层级、资料库搜索、模型入口、API Key 保存结果、网页搜索卡与所有实际滑块开关已统一；居中模态使用共享轻量 `0.12f` 遮罩。 | 不得再次叠加开关比例变换；不得用重黑 scrim、页面私有开关或私有 Dialog 色值替代共享 token。 |
| 上下文可见性与隔离 | 普通回复可在既有页脚查看本次真实本地上下文来源与原因；临时聊天与普通历史、Memory、资料库、搜索、摘要、费用及 Provider 调用隔离。 | 不得按当前设置或再次检索反推旧回复来源；不得把临时聊天当作普通发送能力或自动沉淀入口。 |

### 当前验证、发布与剩余门

- **已通过的代码／JVM 层：** 本轮会话、搜索、设置、上下阅读、回答级来源、临时隔离、暗色预览与聊天内查找的定向契约测试，以及相应 Debug Kotlin 编译和 `git diff --check`，均以 [当前交接](CURRENT_HANDOFF.md) 顶部逐项记录为准。最近“聊天内查找灰卡与白色输入面”仅获定向 JVM＋Debug 编译验证。
- **全量 JVM 不是绿基线：** 最近完整 `:app:testDebugUnitTest` XML 为 `796` 项、`60` 项失败；复跑还曾在 Android Studio JBR 的 C2 中以 `SIGSEGV` 中断。定向通过不得升级为全量通过，旧静态契约失配与运行时崩溃必须分别归因。
- **正式 APK／OPPO：** 当前有证据的正式签名包为 `0.3.0-p10j`（code 66），其同证书、非 Debug、保留数据覆盖及包哈希读回只在交接对应条目成立。其后仍有未重新构建 Release 的代码与文档增量；因此不得将该已安装包宣称包含本节列出的后续改动，也不得未经新授权再次操作 OPPO。
- **下一复查顺序：** 先以三份当前合同逐页人工复核浅／深色、内外屏、长回复、滚动与弹层；再将全量 JVM 的既有失败分为过时静态锚点、真实回归与 JBR 工具故障；只有用户明确授权后，才冻结新 Release、验签、保数据覆盖并进行包／数据指纹回读。真实 Provider、费用、系统媒体／浏览器及可访问性仍是独立门，不能由构建或截图替代。

### 持续同步规则

- 会话／搜索／预览／暗色行为改动：先更新会话合同；设置／开关／模态改动：先更新设置合同；普通上下文／临时隔离／回答来源改动：先更新运行时上下文合同。
- 涉及当前版本、定向或全量验证、Release／设备状态、跨域影响时，同步更新 `CURRENT_HANDOFF.md` 顶部与本节；本节只汇总结论，不重复各功能的实现细节。
- 不恢复已删除的“设置 → 功能审阅”页面；不在聊天主页、Composer 或会话详情新增未获判断的常驻入口。任何历史条目与本节冲突时，以本节所路由的当前合同、交接和代码为准。

## 2026-08-26 历史总控门（已被 2026-08-28 当前总控门取代）

本节仅保留 2026-08-26 当时的排程、入口和设备证据。其中自称“当前”的子标题、表头或描述都是当时语境，不得当作当前排程、当前 APK、当前测试数或当前设备状态。当前只读本文顶部 2026-08-28 总控门及它所路由的合同与交接。

### 当前事实源与冲突裁决

| 事实类别 | 唯一/优先事实源 | 使用边界 |
| --- | --- | --- |
| Android `ConversationWorkspace` 可见规则 | [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) | 顶栏覆盖、抽屉、文本/Markdown、来源 chip、表格、搜索目录、音频、长截图、Composer、未读点等全部按此合同；旧阶段合同只能保留领域 owner，不得复用旧视觉数值。 |
| Android Settings 可见规则 | [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md) | 设置首页、二级至四级页面、个性化、模型与联网、数据／隐私、外观、主题、卡片、按钮、弹层与手势全部按此合同；旧设置／模型合同只能保留领域 owner。 |
| Android 普通聊天上下文 | [Android 当前运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md) | 用户资料注入，以及已开启的 Memory／资料库按当前问题本机检索、预算裁剪与最小外发全部按此合同；P4 的显式预览／未接发送描述仅为历史证据。 |
| 当前实现与验证事实 | [当前交接](CURRENT_HANDOFF.md) 顶部“最新有效交接” + 当前工作树 | 交接中的代码、定向测试、构建、正式 APK、设备读回和剩余人工验收必须按时间最新的记录理解；下方逐条历史 hash 不能代表当前设备包。 |
| 长期取舍 | [决策日志](decision-log.md) | 只记录仍长期生效的所有权和冲突裁决，不重复版本号、APK 哈希或设备时间。 |
| 蓝图与旧审计 | `MASTER_DEVELOPMENT_BLUEPRINT.md` 与本文件后文历史段落 | 保留全路线和历史证据；不能把过期的“当前”“下一唯一入口”或旧设备状态当作本轮结论。 |

### 本轮概念、所有者与受影响入口

| 概念 | 唯一所有者/公开入口 | 受影响入口 | 当前状态 |
| --- | --- | --- | --- |
| 会话可见体验 | `ConversationWorkspace` + Android 当前会话界面合同 | 正文、抽屉、Composer、消息页脚、来源、表格、等待态、长截图 | 已实现并由当前合同收口；视觉和手势仍须按目标设备人工验收。 |
| 会话已读状态 | `ConversationReadMarkerStore` / `ConversationFoundationViewModel` | 左侧“已置顶”/“最近”会话行 | 本机只保存不透明会话 ID 与已读更新时间水位；首次升级初始化旧历史，未读点在打开会话后清除。 |
| 本地对话/附件检索 | 现有本地目录读取模型与搜索入口 | 左栏搜索、全部/图片/视频/音频/文件、聊天内查找 | 打开即展示并按月份分组；当前会话内查找只扫描已呈现的本地内容。真实设备目录数量、预览和播放仍待人工确认。 |
| 设置与个性化可见体验 | `NanfengAiApp` + Android 当前设置界面合同 | 设置首页、个性化、记忆摘要、自定义指令、外观、模型与联网、隐私／数据 | 页面层级、字体、白卡／深卡、主题色、开关几何与编辑保存路径均已收口；逐页真机排版仍待按用户实际操作确认。 |
| 普通聊天用户资料与本地检索 | `AssistantExperienceSettings` + `NormalChatOpenRouterExecutor` + `LocalContextBroker` | 任一已选模型的普通聊天 | 个性化资料每次调用参与；启用 Memory／资料库后，本机按当前问题、scope 与 token 预算选取完整相关条目，禁止整库外发。P4 的独立预览链不覆盖此 owner。 |
| 正式 Android 覆盖 | 受验签 release APK + Android 包管理器 | OPPO Find N5 的同签名保留数据覆盖 | code `66 / 0.3.0-p10j` 的 `adb install --user 0 -r` 已返回 `Success`，未卸载、未清数据、未运行 Debug/仪器测试；设备随即断开，安装后包哈希与 CE/DE inode 仍待只读回验，因此本轮不能宣称保数据覆盖验收已完成。具体证据只读当前交接。 |

### 验证等级与未关闭风险

- **已实现／定向契约：** 当前会话、设置与运行时上下文均已有唯一合同；`CurrentRuntimeContextContractTest`、`ContextParticipationCopyContractsTest`、`AssistantExperienceSettingsContractsTest` 与 `LocalContextBrokerContractsTest` 共 17 项通过，并已对改动文件执行 `git diff --check`。会话、搜索、侧栏、Composer、暗色皮肤、文件预览／定位、弹窗关闭、个性化、记忆摘要和设置组件继续以对应当前合同为准。
- **正式设备安装：** 当前 Release `66 / 0.3.0-p10j` 已完成同证书、非 Debug候选的安装命令，但设备在返回 `Success` 后断开，安装后 `base.apk` 哈希、更新时间和 CE/DE data inode 未回读。它甚至尚不能作为保数据覆盖验收，更不证明视觉、手势、系统浏览器／媒体或真实 Provider 已被人工实际使用。
- **仍待真机／真实服务人工验收：** 以当前合同逐页确认外屏／内屏的会话、搜索、设置与暗色皮肤；确认附件全屏预览、长按定位、系统浏览器／媒体和系统长截图；以用户实际账号和明确发送操作确认真实模型、费用、来源与检索结果。不得用旧截图、旧 APK hash、JVM 测试或覆盖成功替代。
- **持续同步规则：** 任何会话／搜索视觉改动先更新会话合同；任何设置／个性化视觉改动先更新设置合同；任何普通聊天上下文改动先更新运行时上下文合同。涉及跨域、正式覆盖、当前版本或验收结论时，同步更新当前交接顶部和本节。功能去留只记录在工程决策文档，不恢复已删除的用户可见“功能审阅”入口，也不得新增第二套视觉或运行时规则。

## 2026-08-23 去敏外部门 readiness 总表（历史快照；不再是当前排程入口）

本表仅记录存在性与合同事实：没有读取 Key、凭据、应用私有数据或远端内容，也没有启动 AVD、调用 Provider/HTTP、触及 OPPO 或推送 Git。所有“不可用”均表示本轮**不能**启动对应真实副作用，不否定已存在的本地合同或历史局部证据。

| 阶段/外部门 | 所需外部条件 | 当前可用性 | 最小恢复动作 |
| --- | --- | --- | --- |
| P2/P3 真实 Provider/流 | 合法可用的应用内凭据、已验证目录/价格，以及用户点击发送授权本次非敏感输入 | **不可用**：仓库没有允许的 Provider 环境变量或用户级 Gradle 凭据 schema；应用私有存储未读 | 用户在 App 内完成合法配置后，点击发送即授权本次请求；另行用非敏感输入完成一次真实调用，绝不从环境变量或 Gradle 旁路 |
| P5 OPPO/正式交付 | OPPO 在线、安装前只读身份/数据指纹门与当次明确授权 | **当前正式覆盖已完成**：code 63→64→65 的同证书 `pm install -r --user 0` 返回成功，首次安装时间/CE/DE inode/非 Debug 均不变，设备 `base.apk` 精确匹配本地 code-65；标准 Launcher 冷启动返回 `Status: ok` 并保持 `NanfengAiActivity` 前台 | 停止 OPPO 操作，不重装、重启、改用其他启动方式或清理遗留临时 APK；真实 Provider 调用、可访问性/性能与正式发布门仍单独待验 |
| P5/P11 code-65 产物 | 本地正式 APK、结构验签记录及 P11 发布门 | **已安装且已获 Launcher/前台活动证据**：code-65 `888c8698853744c6752e0f3afea02733a3aeffef264eb90f88f44131ba963644` 的设备 `base.apk` 回读、v2/v3、release-v2 证书与一次标准 Launcher 成功均已记录；P11 发布/可重复性风险仍未关闭 | 后续源码修复必须重新冻结、构建、签名、回读安装；在此之前不得把 code-65 当作可发布终件 |
| P6 Android/Windows | 一台全新 Android 35 AVD 的可用 ADB 注册；Windows 本机验收已由用户明确豁免，不是完成门 | **本独立子链已验收**：`emulator-5610` 已完成 schema 38→39 覆盖升级，并经正常 Settings/导入中心/DocumentsUI 对 strict v2 文件显示非空本机中文拒绝，前后 owner 计数不变；Windows 历史债务保留但非阻塞 | 停止该 AVD 的重复选择/重装/升级；此局部证据不代表完整 P6、发布或 Windows 验收 |
| P7 Google/Supabase | 原为指定 Supabase target、CLI/link、私有客户端配置、受控授权会话及专属 Google OAuth 配置 | **用户明确豁免**：Google 登录、Supabase、云端加密同步与跨设备恢复不是总控完成门；远端条件当前仍未核验 | 保留本地实现和未验证记录为非阻塞债务；不得因豁免而配置、部署、调用远端或声称云同步完成 |
| P8 真实 Agent 工具 | 一个已批准的真实工具、精确目标、权限/风险/预算、成功失败取消幂等恢复合同 | **不可用**：仅有 LOCAL_TEST_ONLY 与只读账本，未有真实工具合同/目标 | 一工具一合同，先完成外部动作/回滚边界和用户确认，不以 fixture 升格 |
| P9 生态接入 | 已指定真实目标应用、稳定公开入口、最小只读授权与版本化 schema | **不可用**：现有参考快照不构成真实目标或稳定入口证据 | 用户提供目标应用身份/版本/公开入口及最小授权后，新建一目标一 Adapter 审计 |
| P10 AI Hub | 至少两个真实应用消费者及隔离、降级、回滚/运维恢复验证对象 | **不可用**：尚无两个真实消费者 | 待两个消费者已真实存在后才立约，不提前建设 Hub |

## 2026-08-23 补充当前状态（历史补充；已被 2026-08-24 当前总控门取代）

- **当前规则校正：** 普通聊天与附件不设置逐次外发确认。用户选择附件、预览及草稿阶段只在本机处理；用户点击发送即授权把该准确已提交草稿中的仍存附件或必要解析结果发送给界面明确显示的当前 Provider/模型。切换 Provider 不会静默转发给另一接收方，附件不得进入日志、统计或无关第三方。任何后文“逐次确认”只属于历史 Gate 记录，不得作为当前实现或验收要求。
- **当前 code 65 纠正：** 本文件中 code 52/53/57 及 `c511`/`230cac` 的安装陈述均为历史样本，不能覆盖 `CURRENT_HANDOFF.md` 顶部的 code 65 当前设备事实。code 65 修复了 AppContainer 主线程 Room 写导致的启动崩溃；其正式 APK SHA-256 为 `888c8698853744c6752e0f3afea02733a3aeffef264eb90f88f44131ba963644`。当前源码后续修复尚未重新冻结成新正式产物。

- **P5 已获局部门证：** code-53 正式 APK `230cac…f183954c` 已完成 OPPO 的正式 v2/v3、保留数据覆盖与标准 Launcher 前台活动证据。另在全新 `emulator-5614`，从同正式证书的 code-52（detached `a4eebd1` 单次离线重建）首装后经正常 UI 创建非敏感草稿，仅一次 `install -r` 覆盖到当前 code-57；首次安装时间和 CE/DE inode 不变，force-stop 后标准 Launcher 冷启动成功、草稿仍可见。此为隔离旧版迁移/数据保留/启动证据，不等同于 OPPO、全设备无障碍/性能、发布或 P0–P11 完成。
- **P6 已扩展若干真实子链：** Android v2 已在新空隔离 AVD 经 DocumentsUI 获得 restore、同包 replay、篡改/超限拒绝和非空本机拒绝；修复后 restore→DocumentsUI export 的真文件回读，以及 Android DocumentsUI→隔离 Desktop native Open/Save/replay 均已有局部证据。附件提升中断后零发布、同包 DocumentsUI retry 也已验收。新的 Android 35 `emulator-5610` 先完成同 applicationId、同正式证书的 schema-38/code-51→schema-39/code-52 覆盖升级；普通 Launcher/UI 创建的 2 个 Conversation 和 2 个 Draft 在 startup audit 中按 `schema=39` 读回。随后仅使用唯一既有 strict v2 fixture（`2,346 B`、SHA-256 `d9df67ce64cc325ab35b9f4268c03ed2e956dbeef81e38bf058fb18c16959832`、ZIP 头匹配），经正常 Settings → 数据与导入 → 导入中心 → 完整工作区交换（v2）→ DocumentsUI Downloads 单次选择，显示“当前本机已有数据或待恢复记录，已拒绝覆盖。”；选择前后 audit 均为 `schema=39 project=0 conversation=2 draft=2 knowledge=0 memory=0 relation=0 attachment=0 v2receipt=0 v2provenance=0 v2settings=0`。因此这个非空本机 `LOCAL_TRUTH_PRESENT` 拒绝与无覆盖子链已关闭。P6 总门仍未退出。
- **P11 当前边界：** AAPT2 verification metadata 已无写入回读；当前冻结 code-57 的两次 release build 在 281 个 ZIP 条目和全部解压 payload 上完全一致，整体 SHA 的差异只保留为 APK Signing Block 签名随机性边界。当前 release 仍只获结构/供应链证据，尚无正式发布、回下载或 OPPO 当前版本安装授权；P11 是持续阶段。
- **总控边界不变：** P0–P11 不因任何上述局部证据完成；P2/P3/P4 的真实 Provider、成本/质量与更多真实输入门，P7 账号/同步，P8 真实工具，P9 真实生态目标及 P10 双消费者触发均保持原外部门。

## 2026-08-21 P3 真实执行去内容化复核：仍为 Provider/授权外部门

- **已确认：** §17 P3 的本地 Conversation、preflight、receipt、fail-closed transport 与 P2-M bridge 仍只形成受限合同。普通聊天 production egress owner 不存在；默认 transport 返回安全 `DISABLED_NO_NETWORK` 终态、不发事件、不制造回答。P2-M bridge 和未引用的 Direct composition 均在默认拒绝端口前停止，不是 Provider、Key 或 HTTP 已接通的证据。
- **未关闭且不伪造：** P3 原始退出仍须真实流、停止/失败/重试/换模型、部分计费、真实用量/成本可见追踪、长会话实测及 OpenRouter 充分性判断。它们需要用户明确的非敏感内容/逐次确认、可合法检查的凭据 presence、冻结目录/价格和一次受控真实服务 readback；任何结果都不得自动重试。本轮没有读凭据、Keychain、HTTP、nonce、Provider Attempt、Token、费用、设备或 OPPO，也没有用 mock、DB 注入或假 receipt 替代。
- **排程：** 详见 `P3_REAL_EXECUTION_GATE_AUDIT_20260821.md`。该检查不增加用户功能或功能审阅项，不关闭 P3/P0–P11。P11 的最小本机 Gradle SHA-256 verification metadata 已在无 Gradle 争用窗口生成并离线回读：`gradle/verification-metadata.xml` 含 516 个 component、923 个 artifact SHA-256，文件 SHA-256 `2bc1ea7266a3fbe6ab3adfd5690ea3812d344409793d958207f1bc23ae962add`；它不替代 dependency locking、P11 持续门或 P0–P11 总控退出。

## 2026-08-21 P11 供应链复核与 P6 原始出口校正

- **P6 原始出口校正：** §17 的原始 P6 退出证据是 Android 导出 → Desktop 导入 → 再导出精确保真，以及紧凑/展开、本地文件、更新与异常恢复的独立验证；它**不**把 Android v2 回导/archive/recovery 规定为 P6 必经门。最新 `CURRENT_HANDOFF.md` 顶部已经记录 macOS 上完整 owner 的 Android DocumentsUI → Desktop native Open/Save/re-export 严格子链关闭；Android v2 回导若未来实施只能是独立质量项，不能延后下一总控阶段或被写作 P6 总门。
- **P11 当前增量：** Desktop PDF 预览使用的 `lopdf` 已从 0.35.0 升至 RustSec `RUSTSEC-2026-0187` 修复线 0.42.0；`cargo check --locked` 与 Rust 97/97 回归通过。另已在 Android Studio JBR、离线 `releaseRuntimeClasspath` 下生成并二次回读 Gradle SHA-256 verification metadata。详见 `P11_SUPPLY_CHAIN_REVIEW_20260821.md`。两项均为 P11 持续责任，未关闭 P11 或 P0–P11 总控。

## 2026-08-21 P6 v2 完整 owner Desktop native Open/Save：macOS 独立真实文件子链已关闭

- **新增真实证据：** 全新项目专属 `/tmp` Desktop acceptance bundle/root 由正常设置页的 native Open picker 选择 Android DocumentsUI 真实 3,955 B `.zip`，strict preflight 后写入 private v2 owner；同一 committed record 再由 native Save picker 回导至空输出目录。两次 UI receipt 均为 semantic `fdf9f95ac840…d9ff9`、1 asset。Node strict verifier 在 Android 输入和 Desktop 回导均通过（3 entries / 1 asset）；semantic、8 项 owner-field hash（field-set digest `ead2f34e…4aea1c`）及 asset ledger 全量一致。独立 SQLite v1 三表为 `0/0/0`，v2 import/assets/provenance/journal/receipt 为 `1/1/8/1/1`。
- **兼容修复：** 实际 Android DocumentsUI 普通 `.zip` 曾被 picker 展示却被 Rust 文件名 gate 拒绝。现仅把显式选择的非嵌套 `<stem>.zip` 纳入 v2 输入候选；它仍在任何私有 archive/SQLite 写入前经过完整 strict package preflight，回导输出保持仅 `.nfai-exchange`。这是既有设置二级入口的修复，不增新入口；功能审阅无需新增条目。
- **未扩张结论：** 这只关闭 macOS 上完整 owner 的 Android DocumentsUI → Desktop private import → native re-export/readback 子链；Android v2 import/archive/recovery、Desktop 原生对象恢复、Windows WebView2/安装/签名/缩放/IME、发布、OPPO 及 P6/P0–P11 总门均未关闭。Windows 是外部本机门，不能在 macOS 伪造。

## 2026-08-20 P5-A 紧凑用户入口缺口：双端设置内本地控制面与新空 UI owner 创建已验证，P6 文件链待继续

- **已实现：** `P5A_LOCAL_CONTROL_SURFACE_ENTRY_CONTRACT.md` 将唯一普通入口固定为双端“设置 → 更多本地控制面”；Android 进入既有 `P5ARoute.CONTROL` 并包含 Projects、知识（含关系）与长期 Memory 路径，Desktop 只跳转已有 Projects、知识与关系、长期 Memory work-mode action。双端功能审阅登记“待您判断保留或删减”，建议仅保留设置二级入口、不在聊天主页/Composer/会话详情新增按键。
- **已验证与边界：** Android 定向合同/Debug/正式签名 acceptance build、Desktop lint/Node 65/65/static build/Rust check 均通过。全新 `emulator-5582` 首装 acceptance package 后，从启动器可见 UI 进入 CONTROL，并正常创建最小 Project、两条 Knowledge、RELATED、长期 Memory 和带 DocumentsUI 选择附件的已提交 Conversation；没有 Key/凭据、Provider/HTTP/外部访问、Keychain、DB 或内部导航，也没有操作 OPPO/既有 AVD。`context_gate.py` 已返回 HANDOFF，Android v2 DocumentsUI 导出、Desktop native Open/Save 与 strict readback 留给新线程；不得将当前入口/owner UI 证据写作 P6 完整 owner 文件链、Windows、发布、OPPO 或 §17 P0–P11 完成。

## 2026-08-20 P6 v2 Desktop 真实 native save picker：隔离实例与 Android→Desktop→回导子链已关闭

- **已确认：** 受控临时 bundle 副本使用唯一 `CFBundleIdentifier`、独立可执行路径与新建 acceptance app-data root；源 bundle hash 前后不变，源/副本均 deep/strict ad-hoc 验签。Computer Use 精确定位副本而非已有同 bundle-id 实例。空 root 的 v1/v2 计数均为零，空输出目录为零文件。
- **真实链路：** 用户追加授权只读 `emulator-5558` DocumentsUI 下载目录；实际 1,091 B `.nfai-exchange.zip` 的 device/local SHA-256 均为 `3601aeb…205c53`，不匹配的 2,340 B 自动合同样本被排除。该只读 fixture 由 Desktop 设置 native Open picker 选择并成功私有导入，随后由“回导已提交交换 1”的 native Save picker 写入空输出目录；UI 两次回执均为 semantic `d0c3df7b…6d986`、0 附件。
- **内容无关 readback：** 输出包为 1,023 B、SHA-256 `91fc…1062`；Node strict verifier 通过，semantic hash 与 Android 同为 `d0c3df7b5e9296b5adf4a975077c7d7796381342942c3167cd2acc70c056d986`、entries=2、assets=0。SQLite receipt/provenance 的 project/settings owner-field hash 分别为 `f46d2915…e1f6` / `778838e3…9b22`；v1 三表仍 `0/0/0`，v2 import/journal/receipt/provenance/assets 为 `1/1/1/2/0`。Rust re-export 在 UI 成功前严格重验 canonical package、semantic、全部 field hash 与附件账本。
- **仍未确认且不得推断：** 这只关闭 P6 v2 的最小、非敏感实际文件子链。Android v2 import/archive/recovery、跨端完整对象恢复、Windows、正式发布/Developer ID/notarization、OPPO 与 P6/P0–P11 的其他退出门仍未关闭；macOS 在成功证据取得后锁屏，没有进行自动解锁或额外 UI 操作。

## 2026-08-20 P6 v2 Android DocumentsUI ZIP → Desktop native picker：真实导入/replay 子链已关闭

- **已确认：** 当前严格验签 Desktop bundle 在全新独立 `/tmp` 根，通过设置原生 picker 选择 5558 的实际 `.nfai-exchange.zip` 后成功给出 content-free committed receipt；同一文件第二次选择显示 replay receipt。package/semantic hash 与 Android readback 一致，v2 import/journal/receipt 各 1 条、两项 owner provenance，v1 三表均为零；无 OPPO、5554、5556、Key、网络或正文读取。
- **修正：** 真链首次暴露 Tauri ACL 遗漏，安全拒绝未写入任何 v2/v1 行。补入只允许该单一 v2 command 的 capability 并让 UI显式展示真实拒绝后，重建 bundle 和重跑隔离链成功；没有放宽文件名、manifest、hash、version 或 owner 门禁。
- **后续事实校正：** 此处“没有用户设置入口/尚未以真实文件关闭”已被后续 macOS 隔离验收取代：设置页的 committed-record 回导已经 native Save、strict re-export/readback 得到 semantic、全部 field hash 与 asset ledger 一致。P6、跨端完整对象恢复、发布及 P0–P11仍未退出；Windows 已为用户豁免的非阻塞债务。

## 2026-08-20 P6 v2 DocumentsUI `.zip` 名称兼容：实现/合同已闭合，Desktop native picker 真实跨端链待继续

- **已确认：** Android 的 `application/zip` DocumentsUI 允许将建议 `.nfai-exchange` 显示名保存为 `.nfai-exchange.zip`。Desktop picker 已只为这一个兼容事实增加精确终止名接纳（两种允许形式）和 ZIP 展示过滤；Rust 在读 bytes 前拒绝追加扩展、嵌套协议/ZIP 后缀与路径欺骗，随后仍必须通过 v2 exact manifest、semantic、asset 和 owner-field hash strict preflight。v1 继续由 v2 version/preflight 拒绝，v1 owner/table 不被读取或写入。
- **合同/回执：** Android MIME/建议显示名、Desktop picker 名称规则和 content-free receipt 已纳入 v2 字段保真及 Desktop transaction 合同。名称或 MIME 从不成为 content identity，也不回传或持久化至 receipt；严格 hash 与 manifest 证明不因名称兼容而放宽。
- **仍未确认且不得推断：** 本增量尚未在真实 native picker 选择 Android 实际 `.nfai-exchange.zip`，未产生 Desktop committed receipt、reopen/replay/re-export 或 v1 三表现场 readback；更不关闭 P6、跨端、Windows、发布或 P0–P11。Mac 未解锁时禁止绕过或伪造验收。

## 2026-08-20 P6 v2 Android DocumentsUI 隔离验收：新 AVD 的真实导出子链已关闭

- **隔离与产物：** 因 5556 有既有另一产品前台且输入焦点不可靠，未再触碰 5556。只读 SDK/AVD 核对后新建独占 `NanfengAiP6V2DocumentsUiAcceptance` / `emulator-5558`，不克隆或修改任何既有 AVD。新包 `com.nanzhufeng.ai.p6v2safemptyacceptance` 在该 AVD 首装，显式关闭 P6E fixture；APK 与 installed base.apk SHA-256 一致。OPPO 与 5554 未写入。
- **真实链路：** 新包空数据中仅经本机 UI 创建最小非敏感 Project，随后正常 `设置 → 数据与导入 → 导入中心 → 完整工作区交换（v2） → DocumentsUI SAVE`。范围匿名聚合为对象 1/附件 0，回 App 显示严格 readback receipt；文件 package SHA-256、semantic hash、`project/*` 与 `settings/root` owner-field hash 已分别取证，Node verifier 与 Desktop Rust strict reader 对同一文件只读通过。
- **边界：** DocumentsUI 实际追加 `.zip` 后缀，而 Desktop native picker 目前只接纳 `.nfai-exchange`；strict reader 的通过不覆盖该 picker 文件名兼容缺口。这只关闭 Android v2 的新隔离 AVD 导出/SAF readback/跨端 strict-reader 子链；未验证 Android v2 import/archive/transaction/恢复、Desktop native picker、用户可见 Desktop import/reopen、Windows、发布或 OPPO。P6 与 P0–P11 的原有未退出结论不变。

## 2026-08-20 P6 工作区 v2 Android SAF 导出桥接：本地用户入口/合同已接入，真实文件链未关闭

- **已确认：** Android 已在设置 → 数据与导入提供已审阅的 v2 候选导出入口；范围选择只显示对象聚合计数，严格 mapper/writer 先在内存构成并 preflight package，再一次性写入系统 SAF URI，readback package hash 相等才返回 content-free receipt。失败请求删除未完成文档，且不把路径、名称、正文或附件 bytes 暴露给 UI。Android/Desktop 功能审阅同步保持“待您判断”，建议仅设置二级入口。
- **自动证据：** v2 mapper/writer/scope 与 Android 功能审阅定向 JVM 合同通过；Desktop lint/test 90/90 通过。
- **未确认且不得推断：** 未启动隔离 AVD、DocumentsUI 或真实 SAF provider，未生成用户 package、验证 SAF 删除保证、读回实际文件或交给 Desktop 导入；没有 Android v2 import/archive/transaction/恢复、跨端、Windows、发布或 OPPO 证据。

## 2026-08-20 P6 工作区 v2 Desktop native picker 隔离验收：bundle/root 已就绪，macOS 锁屏使真实链待继续

- **已确认：** picker bridge 已在 `75c28c1` 窄提交；本轮仅新增受限验收根门禁，确保 bundle 启动只能使用新建的项目专属 `/tmp/nanfeng-ai-p6-v2-picker-acceptance.*` 根，不会触碰用户 app-data 或历史 P6-E/P6-H 根。写前与 fixture 生成后均证明验收数据根为零项。定向 Rust 2/2、clippy、check 通过；实际 ad-hoc `.app` 离线重建并 `codesign --verify --deep --strict` 通过。
- **未确认且不得推断：** Computer Use 在第一次读取 GUI 时被 macOS 锁屏阻断，尚未打开任何设置/picker，未选择 fixture，未产生 receipt/workspace/SQLite 业务状态，未做 reopen/replay/re-export 或 v1 不变核对。故只记录 P6 的 bundle 与隔离准备，不关闭 native picker 文件子链，更不关闭 P6、跨端、Windows、发布或 P0–P11。

## 2026-08-20 P6 工作区 v2 Desktop native picker 桥接：本地合同闭合，真实用户文件链仍待隔离验收

- **代码与入口：** Desktop 已登记唯一 v2 command `import_desktop_workspace_exchange_v2_selected` 与 Settings → 数据与导入 → `完整工作区交换（v2）` picker。该桥接只读取一个用户所选 `.nfai-exchange`（regular-file、扩展名、128 MiB 上限）→ strict v2 preflight → 既有 private archive + schema 21 single transaction；没有 v1 staging、目录扫描或路径/正文/byte/显示名回传。失败没有可见 workspace，成功回传 content-free receipt。
- **治理与自动证据：** Android/Desktop 功能审阅均登记“待您判断”，建议只保留 Desktop 设置二级入口、不加聊天/Composer/工作页常驻按键。新增 bridge 合同覆盖 import、replay、receipt 脱敏与 v1 三表零行；Rust library 90/90（Keychain historical self-test 主动排除）、`cargo check`、Desktop UI 90/90、lint/typecheck/static build 与 Android 功能审阅定向单测通过。
- **仍未关闭：** 未在真实 native picker 选择实际 v2 fixture，未做真人文件的 committed reopen/re-export readback；没有 Android v2 用户链、跨端互通、Windows、正式 bundle/发布或任何 OPPO 操作。不能把本地 command/UI 合同写成真实文件、完整对象恢复、备份/同步或 P6/P0–P11 完成。

## 2026-08-20 P6 工作区 v2 Desktop 私有导入内核：生产数据链闭合，真实用户文件链未开始

- **结论：** Desktop 已实现独立的 v2 package reader/preflight、随机 private prepare→fsync/hash readback→archive rename、SQLite schema 20→21 的五张 v2 owner/journal/receipt 表、`BEGIN IMMEDIATE` 单 transaction、committed reopen/replay 与 committed-only re-export readback。它严格接纳 `manifest.json`、`exchange.json` 与引用账本对应的 `assets/<sha256>`；manifest/export canonical equality、IR semantic hash、attachment metadata/hash 与每个 root/asset 的 `ownerFieldHashes` 都必须一致。
- **失败与隔离：** 全部 v2 表在任一 import/asset/provenance/journal/receipt/before-commit failure injection 后为零行，archive 最多留下不可见无 journal 孤儿。v1 `workspaces/workspace_exchange/import_journal` 没有读写；production migration 回归确认 v2 import 后 v1 三表保持零行。已提交包只能完整 readback 后 replay；不允许部分表补齐或中途 resume。
- **自动证据：** v2 内核 4/4；已有 shared v2 IR 与 v1 boundary 各 1/1；Node v2 golden 与 `cargo clippy --lib --tests -- -D warnings` 通过；排除会访问 macOS Keychain 的历史自测后，Rust library 为 89/89。没有网络、Key、picker、UI、设备、数据库注入或真实用户文件验收。
- **仍未关闭：** 内核未注册 Tauri command 或 picker/UI，故完整工作区用户选择、真实 Desktop 文件链、Android↔Desktop v2 正常入口回读、紧凑/展开、Windows 与发布均未开始；设置 → 功能审阅也不得增加条目。下一唯一候选是独立接入并验收 Desktop native picker 的真实文件链，而不是扩大 UI。

## 2026-08-20 当前总控状态（历史快照；不再作为当前排程结论）

### P6 v2 Android owner mapper/package writer 增量（2026-08-20）

`NfaiExchangeV2OwnerMapper` 已形成未注册的 Android 只读 owner→exact-IR 合同：它保留 Project appearance/instruction history、Conversation settings/memory sources、Knowledge source/provenance/history/附件 metadata、Memory title/source/history 与 relationship scope/history，并拒绝 locator、`sourceReference`、私有路径、运行时节点、缺 owner history 与附件内容 hash 不符。其后的 `NfaiExchangeV2PackageWriter` 只读重验账本附件，内存序列化独立 v2 manifest/exchange/content-addressed assets，严格 preflight 后才交给原子有限 output port；失败不调用 port，receipt 无正文/路径/bytes。Android mapper/writer 3 项、shared IR 2 项、Node package reader 和 Desktop Rust strict reader 对同一非敏感临时 package 均通过；无 Room/SQLite 写入、SAF/UI、模拟器或 OPPO 操作。此项只关闭 Android 领域/序列化合同，**不**关闭 P6：Android SAF/UI/持久 archive/import、真实文件链、紧凑/展开与 Windows 门仍未开始，完整工作区入口继续禁止。

以下状态以 `docs/CURRENT_HANDOFF.md` 顶部的当前记录、当前工作树、当前 release APK 与连接设备读回为准。历史段落保留为当时事实，不再作为排程结论。

| 总控项 | 当前事实 | 状态 |
| --- | --- | --- |
| release v2 签名与正式 APK | 新的项目专属 v2 签名已生成；当前源码 release APK 为 `com.nanzhufeng.ai` `51 / 0.3.0-p10a`，SHA-256 `7406d1de5818e013227d7a1ffb4083043e0922f767d013317040bab5f2c41ea2`，v2/v3 签名校验通过。该产物包含功能审阅、Claude 兼容修正与当前版本备份/诊断/Eval 元数据修正，尚未安装到 OPPO。 | 已关闭 |
| OPPO 安装链 | OPPO PKH120 当前只读保留较早 release-v2 APK `fc8f9ac6…`；它不是当前源码 APK `7406d1…`，本轮未覆盖安装。 | 保留数据；当前源码的 OPPO 安装/启动未验证 |
| Desktop P6-K 正式 bundle | 资源封印缺失已修复；最终 ad-hoc bundle 严格验签、原生 WebView、Settings 匿名 aggregate readback 已完成 | 已关闭 |
| Android P6-K 真正入口 | 当前源码以独立 `com.nanzhufeng.ai.p6eacceptancev2` 验收包运行，未覆盖 legacy `com.nanzhufeng.ai` / `com.nanzhufeng.ai.p6eacceptance`。经设置 → 数据与导入 → 导入中心 → 系统 DocumentsUI，已导入两份已授权 ZIP；临时中性来源均在私有暂存后删除。force-stop/cold-start 后只读回匿名 aggregate：ChatGPT 23 对话 / 719 未关联媒体；Claude 162 对话 / 0 未关联媒体；Claude 另有 120 项严格失败，与 Desktop 同源聚合一致。 | 已关闭（隔离 emulator 验收；不等同于 OPPO 导入） |
| Desktop Compare 本地执行边界 | 阶段 1–5 已形成 fail-closed owner、Security.framework credential seam、Settings 安全投影、30 秒 content-free direct-click command，以及未注册的 OpenAI-compatible mock adapter/安全双支 receipt。阶段 5 定向 Rust 合同 5/5 与 `clippy -D warnings` 通过，且仅使用 in-memory credential、mock HTTP 与内存 SQLite。 | 本地 mock-only 合同 | 不等同于真实执行：尚无 UI/Tauri 组合、用户凭据输入或读取、已核验 model/price catalog，亦无 HTTP 授权；用户仍须决定保留与费用上限。 |
| 实包媒体关联 | 两份实包没有可证明的 message-to-asset relation；`UNMAPPED_REJECTED` 是正确安全结果。K8 的人工精确关联功能已实现，但尚未发生用户在 Settings 中明确选择资产和目标消息的真实动作 | 外部用户操作 |
| 真实 Provider / 账号同步 / 生态 | 本地 owner、禁用状态和合同已存在；真实 HTTP、账号、OAuth/发布白名单、同步及生态目标仍分别需要已配置的外部服务和可验证账户/目标 | 外部条件，不得伪报完成 |
| 新增功能审阅与入口建议 | Android 与 Desktop 设置均新增“功能审阅”；当前登记 ZIP 导入、未关联媒体人工关联与 Desktop Compare 联网执行，展示待您判断的去留状态、入口建议与小字理由。今后每项普通用户新功能必须同步登记，默认不增加聊天主页/Composer 常驻按键。 | 已建立规则与双端实现 |

### 原始蓝图 P0–P11 全路线覆盖审计（2026-08-23 补充当前状态）

`MASTER_DEVELOPMENT_BLUEPRINT.md` 第 17 节定义的是总控方案的**完整路线**。本表是唯一的总控排程入口；上表和后文需求矩阵只记录阶段内已获得的证据，不能缩小或替代 P0–P11 的退出门。

### 2026-08-21 P6 v2 完整 owner Android DocumentsUI 增量（Desktop 尚未开始）

- **新增真实证据：** 只在新的 `emulator-5582` 通过正常 Android UI 形成最小非敏感完整 owner，并以 Settings → 数据与导入 → 导入中心 → 完整工作区交换（v2）→ DocumentsUI 实际保存。范围为 Project/Conversation/Knowledge/Memory/Relation = `1/1/2/1/1`、附件 1；App 严格回读 semantic `fdf9f95ac84005a173807779c055f0a3bd2112b01beb9f7bb28763dbe19d9ff9`。对实际保存包的只读 Node strict verifier 也通过（entries 3、asset 1），匿名 owner-field structure 为 8 项、field-set digest `ead2f34eea50ffce928c97d9e117b766de9b2ea07bd52864da1649a6ed4aea1c`。
- **根因修复：** 真实导出先正确拒绝遗留手工 Knowledge 的 `sourceReference=manual`；修复后新的手工 Knowledge 不再写 locator。另修复 repository filter 缺失导致回收站 Knowledge 可能被完整范围纳入的问题。两项均有 Android 定向 JVM 回归，且本次 UI 范围实测为上述准确计数。
- **未扩张结论：** Desktop 的全新独立 acceptance bundle、native Open/Save、private receipt/provenance 与 re-export 比对尚未开始；Android v2 import/archive/recovery、Desktop 原生对象恢复、Windows、正式发布、OPPO 和 P0–P11 总控均仍未退出。context gate 已要求在此停下，下一线程只能继续该 Desktop 独立验收，不得重用历史 bundle/root 或用文件/数据库注入替代 native picker。

| 原始阶段 | 已确认的当前落点 | 仍未关闭的原始退出门 / 下一类工作 | 总控结论 |
| --- | --- | --- | --- |
| P0 总方案与治理冻结 | 蓝图、合同、交接、审计与功能审阅规则都已存在。 | 每次扩展继续维持蓝图、合同、当前事实三者一致；用户对总体方向的持续确认不由旧记录替代。 | 治理基线已建立；持续维护。 |
| P1 Android 工程与可测试领域基础 | Kotlin/Compose 工程、领域端口、结构化错误、构建和定向测试基础已长期运行。 | 保持依赖、静态检查与领域合同的回归门。 | 基础已建立；持续回归。 |
| P2 最小捕获与知识闭环 | 本地捕获、知识、导出、Registry/Invocation 安全事实与 mock/禁用边界已完成多项增量。 | 用户授权的真实非敏感 Provider 文本与图片、目标真机链路、真实保存重启导出及 Key 泄漏检查，均不能由本地 mock 代替。 | 未退出。 |
| P3 Claude 级多模型对话核心 | 对话树、分支、草稿、附件、展示和本地 attempt 谱系已落地。 | 真实流式、停止、失败/重试/换模型、部分计费、真实用量/成本可见追踪、长会话实测与 Provider 充分性判断。 | 未退出。 |
| P4 项目、记忆、上下文与知识完整化 | Projects、Memory、Knowledge、多个单独 Adapter、离线 Eval 与本地上下文控制面均有局部闭环。 | 真实上下文/语义摘要、缓存和成本质量基准、更多 Adapter 的逐个验收、真实 Provider/Harness 回归与用户价值证据；P3 的真实退出门仍是前置。 | 未退出。 |
| P5 Android 产品化与正式交付 | 历史 code-52 正式 APK `c511…` 已完成 v2/v3/同证书核验、OPPO 一次保留数据覆盖及准确 Launcher 前台启动；P5-D 隔离 emulator 的 SAF 备份/恢复链亦有证据。全新隔离 AVD 还已完成从 `a4eebd1` 离线重建的正式 code-52 到当前 code-57 的同证书 `install -r` 覆盖：非敏感 UI 草稿、首次安装时间和 CE/DE inode 保留，cold start/readback 成功；同环境三次强制冷启动为 `1069/930/898 ms`，形成隔离回归基线。 | 全设备可访问性/性能、OPPO 上的当前版本迁移、正式发布/商店交付仍未关闭。当前构建与历史 OPPO 包的字节差异/P11 发布资格仍未收敛，当前包不得作为 OPPO 覆盖或发布物。 | 未退出；仅上述 P5 局部门关闭。 |
| P6 Desktop 与跨端离线体验 | Desktop P6-K bundle/readback、Android 隔离导入和 P6-A 独立文本交换已有证据；v2 已获 Android 空本机 DocumentsUI restore/replay/篡改与超限拒绝/非空拒绝、修复后 restore→DocumentsUI export 真文件回读、Android DocumentsUI→隔离 Desktop native Open/Save/replay，以及附件提升中断→零发布→同包 DocumentsUI retry 的局部证据。新的 Android 35 隔离设备还已得到 schema-38/code-51→schema-39/code-52 同签名覆盖、`schema=39` 启动审计与 2 Conversation/2 Draft 历史 UI readback；随后经普通 Settings/导入中心/DocumentsUI 单次选择 strict fixture，显示非空本机中文 `LOCAL_TRUTH_PRESENT` 拒绝，前后计数不变。P6-L1–L4 保持 content-free 本地精确复用/消息引用有效性边界，未接入执行。 | 原始退出仍要求完整对象的常规跨端导入导出/回读、紧凑/展开和异常恢复的独立验收，及正式发布。Desktop 原生业务对象恢复、用户真实附件等仍未获证；Windows 已由用户豁免为非阻塞债务。 | 未退出；不得将 fixture 或局部文件链写作 P6/P0–P11 完成。 |
| P7 可选账号与端到端加密同步 | P7-A 至 P7-E 的本地协议、状态机、部署工件和 typed restore 主体已完成；Desktop P7-E 现对 candidate 的 canonical identity 做复用/切换前后重读，并使 legacy crash temporary 保持隔离、不阻塞新 candidate。 | 真实 Google、Supabase、OAuth、受控 HTTP、真实 Android/Desktop 跨设备恢复与部署回读均由用户明确豁免为非阻塞债务；不得据此声称已完成真实云同步。 | 不作为总控完成门。 |
| P8 受控 Agent | 本地 ledger、harness、只读 inspect 与本地红队退出已完成；本轮进一步统一双端 plan admission：三类预算按已用+完整计划预检，重复 step intent 在 approval 前 durable fail-closed。 | 每一个将来启用的真实工具必须分别完成成功、失败、取消、审计、幂等与恢复；不得把本地 fixture 说成真实 Agent。 | 本地主体退出；总阶段未退出。 |
| P9 南枫生态协议接入 | LOCAL_TEST_ONLY 集成底座已完成；每个可继续步骤重验合成目标句柄和 expiry，目标重选/过期前不会产生新的本地 receipt。 | 至少一个真实目标应用的稳定入口、权限 UI、用户确认、结果回读、撤销和审计。 | 未开始真实接入。 |
| P10 可选 AI Hub | 尚无两个真实应用消费者，触发条件未成立。 | 仅在触发条件成立后，验证 Hub 的多应用隔离、降级、回滚和运维恢复。 | 未触发，不提前建设。 |
| P11 长期运营与持续演进 | 版本化合同、审计、签名和交接已形成部分运营纪律；AAPT2 metadata 已无写入回读，当前 release build 的非 debuggable/签名结构已核验。 | 当前 APK `d612…` 与历史 code-52 `c511…` 字节不同，必须先收敛可重复性风险，不能用于安装/发布；模型/价格/Provider 复核、迁移、隐私删除、依赖安全、备份演练及 Android/Desktop 发布节奏仍为持续责任。 | 持续阶段，不存在“一次性全部完成”。 |

**总控的真实下一序列：** 先保持 P0/P1 治理与回归；P2–P4 的真实 Provider 与成本/质量证据、P5 的旧 APK 升级迁移/发布门及当前构建字节差异收敛、P6 剩余跨端/Android 正式门、P8 每个真实工具、P9 首个真实生态接入，均按原始依赖逐项推进。Windows 验证及 P7 真实账号/云同步均为用户明确豁免的非阻塞债务。P10 只在两个真实消费者出现后触发；P11 永续执行。任何局部闭环都只能关闭其所属行的一段证据，不能宣布 P0–P11 总控完成。

## 结论

总控方案仍不能标记为“完整落地”。P5 已有历史 code-52 正式签名、OPPO 保留数据覆盖和前台启动证据；P6 已新增 Android DocumentsUI/空本机恢复与 replay/reject、Android→Desktop native Open/Save/replay、journal interruption retry、schema 38→39 同签名覆盖、历史 Conversation/Draft owner UI readback，以及该非空设备经 DocumentsUI 的 `LOCAL_TRUTH_PRESENT` 中文拒绝/计数不变。P6 的剩余完整对象恢复与正式发布仍未验收；Windows 已豁免。当前 `d612…` build 与历史、已在 OPPO 验收的 `c511…` 字节不同，不能用于安装或发布。真实 Provider、真实工具、生态及各阶段外部门仍分别开放；Google/Supabase/云同步已由用户豁免为非阻塞债务。没有以数据库注入、卸载、清数据、Key 或 HTTP 绕过任一门禁。

历史复验记录（非当前排程结论）：Desktop P6-K 定向测试 7/7、Desktop 全量 Rust 库测试 68/68（唯一会触达 macOS Keychain 的既有自测主动过滤）、Desktop UI 合同当前复验 89/89、lint 与 typecheck 均通过。Compare 阶段 5 另有 Rust mock-only 合同 5/5 与 `clippy -D warnings`；没有真实 Keychain 或 HTTP 调用。

## 历史审计的事实源与审计方法

- 当前事实以 `docs/CURRENT_HANDOFF.md` 顶部的 P11/P6 记录及同文件的 2026-08-23 P5 code-52 OPPO 证据为准；`MASTER_DEVELOPMENT_BLUEPRINT.md` 的历史“下一唯一入口”不再可作为当前排程事实。
- P6-K 的产品边界和完成门槛以 `P6K_CHATGPT_CLAUDE_ZIP_IMPORT_ADOPTION_CONTRACT.md` 为准。
- 本轮 Android 验收使用项目专属 release v2 签名的隔离 applicationId；它不触碰 legacy 包或 OPPO 数据。DocumentsUI 与 force-stop/cold-start 均是正常用户路径，聚合查询只用于交叉验证且不读出正文、标题、ID、文件名或账户资料。

## 历史需求—证据矩阵

| 需求 | 当前证据 | 验证等级 | 结论 / 缺口 |
| --- | --- | --- | --- |
| ChatGPT / Claude ZIP 选择即直接导入 | Desktop 已按正常系统 picker 完成真实 ZIP 私有导入、退出重开与安全聚合/receipt 回读；Android 隔离 v2 包也已完成 Settings → DocumentsUI → private staging → cold-start 匿名 readback | 双端真实文件链 | P6-K 主链已关闭；不以此替代媒体/外部能力验收 |
| Conversation + Message Tree 复用既有 owner | 双端均无第二消息真值；Desktop 真包已写既有文本树并重开；Android 同源 Claude 聚合为 162 成功/120 失败，ChatGPT 为 23 成功/494 严格失败 | 双端真实；Android 冷启动读回 | P6-K 主链已关闭 |
| 未关联媒体安全处理 | 实包没有可证明 message-to-asset relation；保持 `UNMAPPED_REJECTED`；人工精确关联有双端合成 owner-to-renderer 合同 | 实包只读安全审计 + 合成验证 | 真实媒体只能由用户在 Settings 明确选择资产与目标消息后验证；不得推测关联 |
| profile / personalization | 白名单 owner 已实现；两份实包均为无可采纳字段的安全结果 | 实包安全聚合 + 双端合成 owner 合同 | 无可写的真实字段，因此不应人为重试或制造写入 |
| P6-K Settings 隐私与撤销恢复 | Android 不显示所选 ZIP 名；撤销失败保留 recovery task/archive；Desktop 与 Android 均有正常 Settings 的匿名 aggregate readback | 双端真实/自动合同 | P6-K Settings 已关闭；真实媒体人工关联仍需用户明确操作 |
| 既定聊天、抽屉、Composer 不回退 | Desktop UI 合同 82/82；含 P6-K 入口、Compare、精确 placeholder、抽屉/Composer 保护；Android 当前源码可完成定向合同与正式 release/lint | 自动 UI 合同 + 当前构建 | 仍须逐项以真实 Android 交互验收，不以构建替代可见行为 |
| Compare 可见入口与 Desktop 本地适配边界 | Android/Desktop 均有模型菜单、Composer `对比`、模型长按三入口；Android 为空草稿先返回。Desktop 保持 UI fail-closed，但底层已有未注册的阶段 5 adapter seam，可在 mock HTTP 与内存 receipt 中写入两支安全状态 | 代码/局部 emulator + Rust mock-only 合同 | 显式 Compare 是直接产品命令，不再有第二次产品确认面；Desktop adapter 仍未组合至 UI/Tauri/真实凭据或 HTTP。Android 非空草稿真实执行同样受用户内容、凭据与 HTTP 门禁，不在本轮执行 |
| 普通聊天真实 Provider | 生产边界、确认合同、账本和失败关闭机制已实现 | 本地合同 | 需要已验证目录、可用凭据、当次可见确认和用户明确非敏感输入；真实 HTTP 未授权执行 |
| P5 备份/迁移等真实 Android 链 | P5-D manifest/preflight 从当前打开的 Room 读取 Schema（37），候选 SQLite 也须一致；完整 1→37 迁移链受定向测试保护。当前以 release-v2 签名隔离包 `com.nanzhufeng.ai.p5dacceptance` 经 Settings/SAF 完成成功导出回读、非敏感变更后的受控替换、force-stop/cold-start readback，installed base.apk 与本地验收 APK SHA-256 一致 | 自动化 + 当前隔离 emulator 正常 UI/readback | P5-D 的当前包 SAF 恢复链已关闭；旧 APK `install -r` 升级迁移、OPPO、发布、云同步与 Provider 仍为独立门槛 |
| P7 同步、P9 生态、P10 联网路径 | 本地协议、禁用状态和 LOCAL_TEST_ONLY/配置表面已实现 | 本地合同 | 仍需要真实账号、目标服务/应用、外部授权及网络；不能借“总控”推定完成 |
| OPPO 验收 | OPPO 当前保留数据安装的是 release v2 `0.3.0-p10a`、SHA-256 `fc8f9ac6…` 的较早正式包；当前源码 release 未覆盖安装 | 安装/版本/包哈希只读核对 | 安装链已存在，但本轮不覆盖安装；当前源码功能、折叠连续性与 OEM 交互仍待按单项真实验收 |

## 历史正式签名失败记录

受控命令为 `:app:assembleDebug`，只使用项目既有正式签名配置与 Android Studio JBR；Gradle 在项目配置阶段停止，提示必须恢复仓库外 keystore 与 macOS 钥匙串口令，或配置既有签名环境。没有读取、打印、导出、创建、替换或输入任何秘密；没有 APK、安装、picker、数据库写入、HTTP 或 OPPO 操作。

## 历史可继续的安全序列

1. 恢复既有签名记录的非交互可用性后，重新生成 APK；对 `emulator-5554` 仅 `install -r` 覆盖，核对本地 APK、安装 `base.apk`、前台 activity 与 UIAutomator package 三方一致。
2. 仅经 Android Settings 正常系统 picker 依次选择两份已授权 ZIP；使用中性临时名，私有暂存后删除临时源，并且不记录正文、外部 ID、附件名或账户资料。
3. force-stop/cold-start 后仅回读 task/receipt/conversation/message/media/profile 的安全聚合；真实媒体不做人工精确关联，除非用户在 UI 中选择具体匿名资产和目标消息。
4. 在不触及任何导入数据的前提下，先诊断并恢复 Desktop 最终 bundle 的白屏/WebView 启动可见性；恢复后才从正常 Settings 路径重做无正文 aggregate readback。真实 Provider、账号/同步、生态目标和 OPPO 仍分别保持独立验收债务。

## 历史不可关闭项

- 正式 Android APK、模拟器真实 ZIP 导入和冷启动读回。
- Desktop 最终 bundle 白屏后的正常 Settings 无正文可见回读。
- 用户在 UI 中进行的实包媒体精确关联（当前没有自动关联依据）。
- 真实 Provider/HTTP、真实账号/同步、真实生态目标和 OPPO。
