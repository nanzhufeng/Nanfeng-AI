# 南枫 AI 决策日志

> **当前 UI 决策读取门（2026-08-26，优先于全文）：** [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 与 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md) 是仅有的 Android 可见规则正文。下方早期决策只保存当时的取舍及领域边界；其中与模型短名、主题色、暗色皮肤、卡片、弹窗、设置层级、归档／回收站、导出或功能审阅有关的旧视觉措辞不得反向覆盖当前合同。

## 决策：本机存储概览以活动 owner 与当前物理字节为唯一口径（2026-08-30）

- 当前选择：图片、视频、音频、文档只统计仍被活动会话消息、草稿、知识、临时会话、可续传上传、南枫转写或未完成 ZIP 任务引用的唯一私有文件，字节使用当前文件实际长度。已完成／失败／取消 ZIP 的 occurrence receipt、link provenance、导入概况和历史 byteCount 不再构成活动 owner。
- 残留边界：磁盘中仍存在但已无任何活动 owner 的受管文件单列为“待清理残留文件”，仍计入本机总量，但不冒充为仍在对话中的视频／图片。用户显式选择清理时才删除；任何真实引用存在时一律保留，最后引用消失时才能清字节。
- 不采用方案：不使用 ZIP 解压前大小、导入时目录总和、Room 历史 byteCount 或 receipt 数量写概览；不把残留隐藏为 0，也不为了让数字变小而绕过引用保护。
- 重新评估条件：未来新增附件 owner、导入形态或跨设备存储时，必须同时扩展引用计数、删除链和存储概览合同；不得只把新文件加进列表。

## 决策：DeepSeek 峰谷状态与本机估算共用 UTC 时间 owner（2026-08-30）

- 当前选择：V4 Flash 与 V4 Pro 的模型选择行用主题色粗体显示“当前低谷／当前高峰”，并保留官方实时联网检索说明。唯一时间规则为官方 UTC 高峰 `01:00–04:00`、`06:00–10:00`；选择面在边界自动刷新，不读取或依赖设备时区。
- 费用一致性：2026-08-17 00:00（北京时间）起，本机 DeepSeek token-only 估算使用同一峰谷 owner 和记录时间选择官方费率；Provider 实际回传仍优先。生效前的历史记录保留旧版本价目，不用今日价格改写历史。
- 入口边界：只增强既有 Composer 模型选择二级列表，不新增主页、设置或 Composer 常驻按钮；Desktop 尚无同一 Android Compose 选择面，不伪装同步完成。

## 决策：普通聊天用统一附件解析层解锁所有文本模型（2026-08-30）

- 当前选择：不把所有 Provider 的原生能力伪改为多模态支持，而是在最终回答模型之前建立应用统一解析层。`MD/TXT/JSON/CSV/XML/YAML/HTML` 及 Office Open XML 本机转文本；PDF 能原生读取时保留完整原件，其他协议先读本机文本层、扫描件用 GLM-OCR；目标模型或联网协议不原生支持的图片、视频等由 Qwen3.7-Plus 产生忠实 Markdown 材料投影。用户手动选定的模型始终负责最终回答。
- 安全与费用边界：发送前显示必要的桥接接收方；桥接调用独立写入调用账本，不保存源文件字节、解析 Prompt 或中间 Markdown。无可用解析服务时阻止最终请求并保留原附件，不发首页、封面、缩略图或空材料，也不为失败静默换模型。
- 状态边界：Composer 瞬时错误必须与失败的 `ConversationId` 绑定；切换、新建或打开其他对话后不继续投影前一对话的错误。回到原对话时仍以该对话的持久运行时／重试卡为真实恢复依据，不靠全局内存错误猜测。

## 决策：Qwen3.8-Max 显式限制推理成本并保留可审计拆分（2026-08-30）

- 当前选择：千问官方直连的 `Qwen3.8-Max` 固定使用 `reasoning_effort=low`，推理加正文单次上限为 `16,384` Token，Responses 整体生命期为 5 分钟。这只收紧 Qwen3.8-Max，不改变其他千问预设、模型排序、Auto 路由或已有会话 override。
- 依据：旧请求未发送推理档位，又将 `131,072` 作为组合输出上限并允许 10 分钟；官方当前合同会将省略的档位解释为 `xhigh`。用户已有记录为输入 `83,273`、输出 `17,242`、耗时 7 分 39 秒，说明之前的应用侧预算过宽。
- 费用与历史边界：新调用按北京地域官方人民币价目估算，并在 Provider 返回时持久化 `reasoning_tokens`。旧记录只有总输出，保留当时已记账金额和“推理明细未返回”，不回写、不猜测历史推理 Token。

## 决策：会话标题与历史资料整理共用低成本模型顺序（2026-08-30）

- 当前选择：两个后台文本整理任务共用唯一候选表 `DeepSeek V4 Flash → GLM-5.3 Flash → Qwen3.6 Flash`；只在前一项未启用、缺少凭据、不可用或本次失败时继续，千问始终排在最后。
- 固定边界：这不改变普通聊天、`Auto`、提醒草案或定时监控的模型选择。历史资料候选与无正文审计必须记录真实命中的 Provider、模型和推理档位；GLM-5.3 Flash 继续使用已锁定的 `max`。
- 取代关系：本决策取代 2026-08-26 “会话自动标题只由千问整理结果写入”中的模型与 Provider 顺序；原来的最小外发、标题验收、人工重命名优先和安全元数据边界保留。

## 决策：DeepSeek V4 Flash 复用官方直连并进入日常模型选择（2026-08-29）

- 当前选择：`deepseek-v4-flash` 作为 DeepSeek 官方直连的第二个预设，复用既有固定端点、独立加密 API Key、模型目录、调用归因、连接测试和费用链；Composer“日常”二级选择面增加完整名称，消息页脚使用短名 `V4 Flash`。
- 固定边界：DeepSeek 默认预设仍为 V4 Pro，`Auto`、自动标题和已有会话选择不迁移；V4 Flash 只在用户手动选择或模型设置中明确保存后使用。当前目录按官方资料记录 1M 上下文、384K 最大输出、文本、思考、JSON 与工具能力；不声明图片、PDF、视频或音频输入。
- 费用边界：本机按记录发生时适用的官方版本化 token 单价生成估算，Provider 回传的实际结算始终优先；自 2026-08-17 00:00（北京时间）起，峰谷费率由上方共享 UTC 时间 owner 选择。联网工具费用、优惠和缓存写入未被响应明确披露时不得写成实际扣费。

## 决策：ChatGPT 累积 ZIP 按 source tree append-only 合并（2026-08-28）

- 当前选择：ChatGPT 旧导出先进入本地后，更新的累计导出以 provider source conversation ID 去重；完全相同的可见消息树复用原会话，只有新增 source message 才在同一本地树追加。source-message→local node provenance 是这条合并路径的唯一身份桥，最新累计批次接管 provenance。
- 固定边界：消息的内容 identity 不采纳临时遍历序号；会话 identity 仍涵盖完整可见消息顺序。改写／删除旧 source message、缺失 provenance、歧义 source tree 或树校验失败一律冲突关闭，现有本地对话、草稿、附件、设置、Key、Provider 与用户修改均不覆盖。非文本多模态对象、thoughts／reasoning recap 不进入正文；只有明确字符串文本进入会话。
- 不采用方案：不以 ZIP 整包 hash 做跨版本去重，不为同一来源另建“新版本”会话，不用标题、时间或文件名猜重，也不把无法安全投影的内容转为空白文本／伪附件。旧批次删除后已由新批次接管的会话不受影响；删除最新 owner 批次才走既有可恢复软删除。

## 决策：ChatGPT 附件的唯一字节身份、多消息引用与合法当前叶分层（2026-08-28）

- 当前选择：同一官方 file ID 可被多个 source message 引用；映射保留官方 `currentPath` 中的精确 occurrence，唯一 asset entry 只保留第一个确定性 owner 作 catalog 元数据 owner，附件字节不重复。恢复 Message Tree 时不把“最后可渲染官方消息”默认视为叶节点；先保留官方恢复路径下已有合法叶，否则确定性选择最新后代叶。
- 已确认事实与依据：用户 2026-08-27 真实 ChatGPT ZIP 包含 `853` 个官方明确归属且 entry 存在的唯一附件，其中存在共享 file ID。旧 owner 对某个结构尾部会话触发 `当前分支必须指向叶消息`；改为合法后代叶选择后，真实 Room 验收为 `853/853`，XML `skipped=0, failures=0`。
- 固定边界：不从文件名、时间、相邻消息或内容猜配 `822` 个无官方归属候选；会话事务失败必须进入结构化 summary，不得以空 `Outcome()` 伪装成功；v2 完成标记只在映射非空、全部唯一 entry 已挂载且 `failedConversationCount == 0` 时写入。
- 重新评估条件：只有未来需要对每个 occurrence 做独立删除、重放或审计，才引入独立 occurrence 表和迁移；不为了表结构完美而重写已经真实包验收的当前链路。

## 历史决策：对话底部模型名只使用中心目录的具体名称（2026-08-25；已由 2026-08-26 当前合同覆盖）

- 当前选择：Composer、普通／临时对话和助手消息页脚共用 `NanfengModelServiceCatalog` 的具体模型名称；`GPT-5.6 Terra / Sol / Luna` 保持完整，Claude、Gemini、Qwen 与 DeepSeek 名称也不再被截断。比较选择直接显示其中的两项具体名称，不显示泛化的“对比”。
- 固定边界：服务商、实际接收方、模型 ID 与网页检索路线继续写入既有归因、调用记录、诊断和外发确认；这些事实从底部模型名称移除而不删除。旧归因文本只在读取时规范显示，Room 中的历史事实不迁移、不覆盖。
- 不采用方案：不继续维护 Composer 缩写、逐个厂商 `removePrefix` 或每个页面各自清洗字符串；也不因视觉精简而从发送前的服务商与费用告知中移除真实接收方。

## 决策：关闭侧栏不得参与 Android 系统长截图候选（2026-08-25）

- 当前选择：关闭的 `ModalNavigationDrawer` 在语义树中显式标为不可访问，确保 Compose Scroll Capture 只从当前可见的正文滚动容器选择候选；捕获期间继续冻结自动跟随最新消息，并移除每段都会参与根视图重绘的正文边缘遮罩。
- 固定边界：侧栏打开时恢复全部辅助功能和正常滚动语义；正文 `LazyColumn` 继续使用 Compose 原生 Scroll Capture，不引入私有截图、图片拼接、存储权限或会话内容复制。截图结束后回到现有柔和边缘过渡与普通自动跟随策略。
- 不采用方案：不通过禁用侧栏滚动、删除视觉过渡、增加“应用内长截图”按键或在截图开始时把对话跳到末尾来规避问题；这些做法会损害正常导航、阅读或对话事实。

## 决策：归档与回收站使用独立入口和独立列表（2026-08-25）

- 当前选择：设置 → 对话管理只承担说明、两个生命周期入口和当前对话导出；“已归档”与“回收站”各自进入独立设置层级，只呈现对应会话及其恢复操作。这样用户不必先在下拉菜单区分状态，也不会在同一列表里误把归档与回收站当成相同结果。
- 固定边界：归档恢复仍为 `UNARCHIVE`，回收站恢复仍为 `RESTORE_DELETED`；现有本机消息树、附件、调用关联、搜索 scope 与导出范围不被重写。退出设置仍恢复活动会话 scope，避免 lifecycle projection 进入日常抽屉。
- 不采用方案：不保留“查看：已归档／回收站”的下拉切换，不在同一张密集卡中混放两类列表和导出操作，也不为视觉整理增加新数据状态或物理删除入口。

## 决策：Android 枫叶采用白色实心，AI 反相为橙色（2026-08-25）

- 当前选择：按用户明确指示，Android 启动器图标把枫叶内部填为纯白；AI 字样使用同一橙色以在白色叶片上保持可读。只改变这一组内部色彩关系，保持橙色底、圆角、主体比例、留白、透明角和 adaptive／legacy 双链不变。
- 固定边界：原始 JPEG 永不覆盖；新 Android 母版独立命名，所有 Android 资源仅从这份母版一次导出。Desktop 图标不随此 Android 专用调整改动。
- 不采用方案：不让白色 AI 消失在白色实心叶片内，不给图标添加白色托盘、黑色外框或额外阴影，也不通过裁切/缩放掩盖 ColorOS 自适应图标问题。

## 决策：提醒控制集中于设置且逐项真实门控（2026-08-25）

- 当前选择：设置 → 通知与提醒作为唯一用户控制面，提供计划监控结果系统通知、对话尾部提醒建议、对话列表未读标记三项独立开关；全部默认开启以保持升级前行为。每次修改立即写入本机。计划监控 worker 在发送通知前读取开关；对话与抽屉根据同一持久化状态即时重组，不以隐藏设置项或只改说明文字代替真实抑制。
- 固定边界：关闭计划监控通知不会暂停任务、删除结果或改变网络外发范围；关闭对话建议不自动删除已有任务；关闭未读提醒不改变已读水位且不把列表标记伪装成系统推送。系统通知权限仍由 Android 独立管理。Desktop 没有对应 durable owner，因此不显示伪开关。
- 不采用方案：不把三种语义不同的提醒绑为一个总开关，不在聊天主页、Composer、会话详情或计划列表重复增加开关；按用户本轮决定，功能审阅不再重复呈现这两类提醒的文字卡，实际控制只放在通知与提醒页。

## 决策：聊天日期分隔线使用会话中性细边令牌（2026-08-25）

- 当前选择：普通和工作聊天都通过同一个 `TranscriptDateDivider` 绘制日期左右线；其 `SubtleDivider` 统一映射为 `NeutralBorder (#D8DEDA)`，与灰色会话画布保持可读但克制的分层。
- 固定边界：首条日期和所有跨日期分隔符不得分叉出白色、局部硬编码或不同透明度；日期文本、间距、消息顺序、渐变和滚动所有权不随颜色修复改变。
- 不采用方案：不靠加白灰承托底、隐藏顶端日期线或对第一条消息单独调色来补救。

## 决策：会话顶部与 Composer 以同形状的柔和阴影分两级抬升（2026-08-25）

- 当前选择：顶部圆钮和右上操作胶囊统一使用 `30dp` 的低透明度中性灰宽幅阴影，作为此前 `10dp` 的三倍加强；底部 Composer 使用相同阴影颜色、裁切和圆角规则，但提升至 `42dp`，在灰色会话画布上更明确地悬浮。
- 固定边界：白色表面、触控面积、布局锚点、按压/水波纹和焦点反馈不变，且都服从各自圆形或胶囊轮廓；禁止以深色描边、黑色硬阴影、矩形阴影或额外白灰承托底替代。
- 不采用方案：不让 Composer 与顶部控件继续被同一固定 elevation 绑死，不提高阴影色的不透明度来制造脏边，也不将 Surface 的 Material 阴影与自定义阴影叠加。

## 决策：会话自动标题只由首轮问答的千问整理结果写入（2026-08-26）

- 当前选择：首轮完整回答后，不再从回答的 Markdown／编号小节、某一句结论或用户首句本机截取标题。只向 Qwen3.7-Plus 发送开头的一条用户发言和紧随其后的第一条完整南枫AI回答，要求严格 JSON 返回 6–32 字的“讨论对象 + 任务／结论方向”概述；例如海南同学聚会的讨论应概述为“海南大学同学聚会：陵水与海口的体验和性价比选择”，而不能是“体验对比”。
- 固定边界：不发送后续对话、附件、个性化资料或历史内容；原始问答、提示词、模型回复和生成标题均不进入调用记录。仅记录 opaque 会话 ID、状态、模型、Token、估算费用和安全错误，并在“费用与用量 → 会话标题整理”独立展示。
- 失败与数据保护：千问未配置、网络失败或结构不合法时，标题保持“新对话”，不降级为残句／局部小节；手动重命名会先清除自动标题资格，千问返回期间再次手动重命名也优先保留人工标题。既有会话不批量改写，因为历史自动标题与人工标题无可靠来源区分。

## 决策：普通聊天使用设备直连 Provider（2026-08-26）

- 当前选择：用户点按发送或重试后，界面先将草稿安全写入本机；随后 `NormalChatGenerationForegroundService` 成为普通聊天 `execute/retry`、网络连接和流式落库的唯一 Android owner。服务只接收会话 opaque ID 与 SEND/RETRY，不携带正文、附件、回复或 API Key；它以数据同步前台服务通知维持本机直连 Provider 的一次请求，并只向前台广播 opaque ID、运行状态和安全错误码。
- 固定边界：返回应用会从持久化 runtime/attempt 重新投影，而不是靠 Activity 内存猜测完成状态；用户停止会发往服务 owner，早于 socket 建立的停止也会被 executor 记住。进程被系统终止、应用被强制停止或网络真实断开时，不承诺伪恢复或自动重发：持久 Attempt 保持 UNKNOWN，仍需用户以原编号明确重试，避免重复计费。
- 不采用方案：不保留持续生成网关、双执行模式、服务端任务补拉或服务端临时正文；也不保留“前台服务只显示通知、ViewModel 实际联网”的伪后台模式，不把请求正文、凭据或回复文本放进 Intent/通知/广播，不以后台失败静默吞掉前台错误提示。

## 决策：对话提醒不再使用主题模板，改为千问受限整理（2026-08-26）

- 当前选择：本机只负责严格判断“用户是否明确要求未来提醒／持续跟踪”，不再以“价格、动态、新闻”等宽泛词或产品类别套用长模板。符合门槛时，用户点击对话尾部入口才调用 Qwen3.7-Plus；它只收到本对话开头的一条用户发言与紧随的第一条完成南枫AI回答，并只能输出结构化的可编辑标题、监控要求与频率。解析失败、未配置千问或模型认为不适合监控时，一律不创建草案。
- 已确认事实与依据：旧本机分类规则会将无关讨论错误归入“智能汽车动态”，把预设模板显示为对话建议，不能解释其与当前讨论的关系。
- 关键推理与权衡：Qwen 的小范围结构化整理比关键词分类更能保持主题与触发条件一致；调用限定在显式点击后，完整对话和附件不外发。每次调用仅保存会话 ID、模型、Token、费用来源和安全状态，并在“设置 → 模型与联网 → 费用与用量”的“提醒草案整理”分组单独显示；不保存两段源文本。
- 放弃方案及原因：不再用本机类别模板作为成功草案或在模型失败时降级回模板，因为它再次制造与用户对话无关的任务。
- 风险与待验证项：真实 Qwen 配置、网络失败和 JSON 格式异常必须在真实账号下验证；自动测试只能验证门槛、账本、迁移与不保留正文，不能证明生成质量。

## 决策：DeepSeek V4 Pro 使用自身 Responses 原生网页检索（2026-08-25）

- 当前选择：DeepSeek V4 Pro 的深度请求直连 `https://api.deepseek.com/responses`，以官方 `web_search` 工具并指定 `tool_choice: {type: web_search}` 发起服务器端实时检索；普通会话和已计划监控均记录 DeepSeek 为实际接收方。
- 固定边界：不得把 DeepSeek 模型 ID 转发给千问或 OpenRouter；不从模型正文伪造来源。DeepSeek Responses 的语义 SSE 暂未接入当前 Chat Completions stream transport，因此本条路径以非流式完成，取得最终结构化结果后再显示。
- 不采用方案：不继续停留在旧 `/chat/completions` 后把菜单标成不支持；不因当前 transport 尚未支持 Responses SSE 而降级成离线回答。

## 决策：用户自定义指令保留完整的 8,000 字符本机额度（2026-08-25）

- 当前选择：设置 → 个性化的“回答偏好与自定义指令”将单字段上限从 `2,000` 提升到 `8,000` Unicode 字符，并显示当前字符计数。只要用户开启个性化，保存的内容将完整地作为本次普通模型调用的系统指令组成部分；不进行本机摘要、裁剪或写入调用审计／诊断。
- 固定边界：该额度只适用于用户直接在南枫 AI 个性化设置中编辑的本机偏好；昵称、职业与关注方向保持各自较短字段上限。第三方 ZIP 的严格资料白名单仍按其独立版本化格式与读取预算处理，不能借此放宽外部导入面。
- 不采用方案：不要求用户把长期背景拆成多个字段，不在保存时静默丢弃尾部内容，也不为了节约上下文将用户原文替换成模型生成摘要。

## 决策：Composer 缩写 GPT 名，模型选择面保留完整名称（2026-08-25）

- 当前选择：Composer 的窄模型位与助手页脚使用紧凑名：`GPT-5.6 Terra / Sol / Luna` 显示为 `5.6 Terra / Sol / Luna`；打开后的模型选择面、模型目录和设置列表保留完整的 `GPT-5.6 Terra / Sol / Luna`。
- 固定边界：`openai/gpt-5.6-terra` 等 provider-facing ID、预设枚举与持久化的原始归因事实不修改，避免因纯显示改动影响联网路由或历史真实性。
- 不采用方案：不让弹窗复用 Composer 的缩写，也不把 Composer 扩宽后强塞完整模型名；不把模型版本缩成无法区分 Terra/Sol/Luna 的简称。

## 决策：周期新闻监控仅从高价值对话生成本地可编辑建议（2026-08-25）

- 当前选择：用户可从左栏手动新建；对话尾部只在当前一问一答本地命中“明确长期追踪信号 + 高价值主题”时提供“添加提醒 / 监控”。本地规则只从当前用户问题和最后一条完成助手答复整理一个短名称、具体监控要求和频率的可编辑草案，范围限于每日重点简报、具体投资阈值、AI 算力/电力/Agent、Codex 额度与官方更新、中美科技政策、海外账号政策、南京/苏州居住、智能汽车及 AI 影视/VFX 的重大动态；普通开发、闲聊、一次性解释和无阈值的个股讨论不产生入口。来源会话仍只以 opaque ID 写入任务；实际后台请求只含用户保存确认后的任务名称与监控要求，默认使用 5.6 Terra 标准档和对应服务商的官方网页检索。DeepSeek V4 Pro 也使用其自身已核验的官方 Responses 网页检索，不伪装成千问接收方。任务、运行结果、实际接收服务商、模型、Token、安全错误码和 Provider 返回的公开来源由 Schema 46 Room owner 保存，单任务以网络受限的唯一 WorkManager 链串行调度。
- 固定边界：首次创建、恢复、暂停、删除和失败都会通过同一 repository/scheduler；暂停或删除立即取消唯一后台任务。系统通知只在用户授予 Android 通知权限后发送“本次完成”提示，点按回到任务页；通知不包含结果正文。模型服务未配置、凭据缺失、网络/响应失败时保存安全错误并按周期再次安排，不伪造完成结果。
- 固定边界：建议规则不请求模型、不持久化对话正文、不读取附件/Memory/Context；用户保存前可修改或取消，绝不自动建任务。实际后台请求不携带对话正文或附件，只使用用户确认的任务字段。每日重点简报的美国对中国科技政策部分必须优先官方原始来源、分开官方发布时间/媒体首次发布时间/生效时间、标注精确法律状态，并区分已确认直接影响、合理推测外溢和市场情绪。
- 不采用方案：不把完整对话、附件、Memory 或 Context自动转成新闻监控 prompt；不做无真实执行 owner 的“计划”视觉列表；不隐式采用 Pro 模型、不能在 Composer 增加常驻入口，也不在 Desktop 没有同等 owner 时显示伪开关或任务状态。

## 决策：Android 会话界面只保留一个现行合同（2026-08-24）

- 当前选择：[Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 是 Android `ConversationWorkspace` 的唯一视觉和交互正文，覆盖顶栏分流、材质、左栏会话行、右滑快捷操作、主屏开侧栏、Composer、模型选择面、文本选择菜单及消息页脚。
- 固定边界：`CHAT_FIRST_INTENT_ORGANIZATION_CONTRACT.md` 继续定义信息架构，`P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_CONTRACT.md` 继续定义消息/附件语义，`P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md` 继续定义 Auto/手动选择和路由，`P2D_MODEL_SETTINGS_CONTRACT.md` 继续定义设置凭据；它们不得以历史 Android UI 数值覆盖当前合同。
- 审计路由：会话 UI 的当前规则只读该合同；当前实现、测试、正式 APK 与设备证据只读 `CURRENT_HANDOFF.md` 顶部和总控审计的最新“当前总控门”。总控蓝图和历史审计只保留路线或当时证据，不能作为当前 APK、安装状态或下一步的反向来源。
- 不采用方案：不在多个阶段合同中同步维护尺寸、颜色、滑动和外点行为；不以截图或安装记录替代明确合同；不把视觉合同扩展为 Provider、凭据或真实外发成功声明。

## 决策：实时检索只走已核验的 Provider/模型合同（2026-08-25）

## 决策：Android 设置也只保留一份现行视觉合同（2026-08-26）

- 当前选择：[Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md) 是 Android 设置首页及二级至四级页面的唯一视觉与交互正文；它与 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 共用主题、皮肤、圆角、图标与滑动操作规则。
- 固定边界：`SETTINGS_CENTER_CONTRACT.md`、`P2D_MODEL_SETTINGS_CONTRACT.md`、费用、上下文、导入、隐私和对话管理合同继续拥有各自数据与安全语义，但不得用历史 Dialog、固定橙色、白卡、黑边、旧文案或页面结构覆盖当前 Android UI。
- 不采用方案：不在历史阶段合同反复同步视觉数值；不把普通设置入口重新做成弹窗；不恢复用户已删除的“功能审阅”入口。

## 决策：实时检索只走已核验的 Provider/模型合同（2026-08-25）

- 当前选择：复杂推理的 OpenRouter 模型通过 `openrouter:web_search` 发送并读取最终 `url_citation`；千问深度模型通过官方 Responses 声明 `web_search` 并读取 `web_search_call.action.sources`。Qwen3.8-Max 默认思考模式不发送不兼容的 `tool_choice=required`，由 Provider 按 `auto` 选择已声明的工具。DeepSeek V4 Pro 通过自身官方 Responses 声明并强制 `web_search`。Markdown/TXT/JSON/CSV 以完整 UTF-8 文本消息发送给 Qwen，绝不伪装为其 Chat Completions 不接受的 `file` content part。任何 DeepSeek 请求都不转发给千问。
- 固定边界：`NormalChatSendAttempt.egressProviderId`、调用诊断和助手归属记录保存实际网络接收方；用户可见来源只接受 Provider 返回的公开 URL，再规范成现有 Markdown 来源 chip，绝不从模型正文猜测或保存 Provider 原始 JSON。已计划监控不得降级为离线回答。
- 不采用方案：不把所有供应商硬塞进一个 Chat Completions/Responses 结构；不因当前 Composer 选择变动而改变未知 Attempt 的 receiver；不让未启用 Provider 工具的普通对话凭系统提示词假装取得实时来源。

## 决策：材料分析是全模型共享的证据优先路径（2026-08-25）

- 当前选择：只要用户提交材料并明确要求分析、解读、核实、判断或评估，`EvidenceFirstAnalysisPolicy` 即在普通聊天执行层生效，而不是按 Claude Sonnet、Claude Opus 或“深度”槽位分叉。它要求结论优先、已核验事实/材料观点/推理分层、反例或失效条件、风险与下一步，拒绝把截图的版面和文字逐段复述为最终答案。该请求会启用实际可用的检索协议：OpenRouter 的所有已接入模型统一走 `openrouter:web_search`；千问所有档位统一走可携带材料的 Chat Completions 检索；已有深度路由不降级。DeepSeek 的材料序列化尚不支持其 Responses 检索时，保留非联网材料路径，并强制提示“未取得实时来源”。
- 固定边界：只有“有材料 + 用户明确要分析”才触发；普通看图、转写、翻译和没有分析意图的附件消息不增加联网、费用或新的外发。来源只采信 Provider 返回的结构化公开链接；模型能力差异仍真实存在，规则提高任务方式与证据标准，不承诺 Sonnet 与 Opus 的推理质量完全相同。
- 不采用方案：不以厂商/模型名称 hard-code 提示词，不对每条图片强制联网，不以页面转述冒充联网分析，也不让未获协议支持的 DeepSeek 图片请求伪造实时资料。

## 决策：助手结果尾部只显示消息绑定的实际模型路由（2026-08-24）

- 当前选择：普通发送创建 `NormalChatSendAttempt` 后，以助手消息 ID + Attempt ID 持久化无正文的 `AssistantResponseModelAttribution`。普通流在请求开始前绑定持久助手占位；对比和按原编号重试在创建新的助手消息前绑定原 Attempt。尾部展示持久化的模型显示名与接收服务商，而非当前 Composer、Auto 规则或后来刷新的模型目录。
- 固定边界：该表只含助手消息 ID、Attempt ID、Provider、实际 model ID、显示名和记录时间；不保存请求正文、回复、附件、URL、凭据或原始响应。未具有精确归属的历史/导入消息显示“模型信息未记录”，不能从相邻用户消息、时间或当前设置推断。
- 不采用方案：不以“Auto”“深度”“对比”等逻辑槽位替代真实 model ID；不把调用诊断的最近一条记录套给会话消息；不在消息页新增操作按钮；Desktop 在拥有对应真实发送和消息归属 owner 前不仿制此标签。

## 决策：普通对话、项目工作区与临时对话必须有独立导航投影（2026-08-23）

- `CHAT`、`WORK` 与临时恢复不是同一列表的不同标题：普通和工作会话各按 `ConversationSurfaceRepository` 的持久化 surface 读取并保留各自选中会话；临时对话仅由 `TemporaryConversationDomain` 管理，不展示或写入持久会话列表。
- 工作区的树形入口以既有 `ProjectDomain` 为唯一项目所有者。创建项目、编辑、置顶、归档经 `ManageProjectUseCase`；在项目中创建工作对话时，持久化同一 `projectId + WORK` 会话，不建立平行文件夹或依赖外部目录。
- 借鉴 Codex 的信息架构，不复制其本机资源管理、永久工作树或隐藏访问权限；归档或异常项目归属的既有工作对话必须仍可见，不能因 UI 分类而静默丢失。

## 决策：P6 完整工作区交换先以 v2 字段保真/拒绝 IR 阻止静默降级（已确定）

- 当前选择：`nfai.exchange.v1` 继续只用于既有语义投影；P6 完整对象必须先经过 v2 的逐字段矩阵与共享 canonical semantic-hash IR。Project appearance/instruction history、Conversation settings/memory sources、Knowledge source/provenance/history/attachment metadata、Memory title/source/concept/history 及 Relationship scope/project/time/history 是 v2 的必保留事实。
- 固定安全边界：v2 不接受 path、URI、picker token、`sourceReference`、credential、Provider raw payload、runtime、diagnostic 或 route preference。任何 owner history/依赖/内容寻址附件无法验证，或 Desktop 无法原子持久化，均拒绝整包；不从 v1、默认值或文件名推断丢失事实。
- 不采用方案：不把 v2 schema/golden 当作 Android mapper、Desktop SQLite import、SAF、备份或真实跨端验收；在 Android/desktop完整 mapper、asset archive、transaction/journal、重开和回导合同闭合前，不新增完整工作区入口或功能审阅条目。
- 重新评估触发条件：Android 只读 mapper 可以产生 exact v2 IR，Desktop 以版本化 staging/asset archive/transaction/journal 导入并完成中断回滚和真实回导后，才评估设置二级入口及双端功能审阅。

## 决策：Android v2 恢复只允许设置 OpenDocument 的单文件、空本机路径（2026-08-23）

- 采用：`设置 → 数据与导入 → 完整工作区交换（v2）` 内的 OpenDocument 只消费用户明确选择的一个 URI，受控 bridge 有界读取 bytes 后直达 strict reader → atomic restore owner。UI 不读取流、不解析 ZIP/JSON、不持久化 URI/path/name 或包正文；UI state 只含 content-free outcome、semantic hash 前缀和匿名计数。
- 采用：以 exact package SHA-256 派生 opaque intent ID，使用户重新选择同一 byte package 时才可能回收同一未发布 journal；非空本机、不同 intent/package 或任何未知 journal 不覆盖、不合并、不删除，按拒绝或 `RECOVERY_REQUIRED` 停止。
- 不采用：聊天/Composer/工作页入口、目录扫描、多文件选择、SAF/UI 自行解析、用新 intent 绕过同包 journal、将 Android 恢复描述成备份或云同步。
- 配套修正：chat-first 首次启动不得自动写入空“新对话”，否则用户正常打开设置前本机已非空而恢复入口不可用。保留抽屉中的显式新对话动作，不新增常驻按键。
- 配套修正：v2 不携带未发送草稿，但 atomic restore 必须为每个已恢复 Conversation 写入空、无附件的本机 draft 占位并在 typed readback 验证。否则恢复虽可提交，却会使既有严格 writer 因缺少 snapshot 必需记录而拒绝重新映射；这不是放宽 v2 的草稿可移植范围，也不新增入口。

## 决策：用户操作直接执行，安全约束不以二次确认表达（已确定）

- 当前选择：用户在应用内选择导入或执行即直接运行；ChatGPT ZIP 在严格预检/解析后自动逐项原子导入，普通聊天与 Compare 不再要求应用内二次确认。
- 固定边界：保留系统权限、私有 staging、严格格式/大小校验、最小必要外发、凭据不曝光、费用/错误可见、幂等/回执/冲突和 Agent 外部 authority；这不授权静默外部副作用或绕过平台权限。
- 影响：旧 `AWAITING_CONFIRMATION` 仅可作为内部短暂处理状态，不能成为用户产品闸门；历史确认 UI/合同须逐步迁移为直接执行状态与可读结果。

## 决策：第三方 ChatGPT／Claude ZIP 只按已登记版本导入，资料与偏好仅经唯一安全 owner 写入（已确定）

- 当前选择：P6-K 是当前最高优先级。用户在系统 picker 选择 ZIP 即直接运行严格预检、候选映射与每项原子提交；没有应用内逐项确认。ChatGPT 根或实包编号 `conversations*.json` 必须逐 entry 通过 P6-H 严格 parser；Claude 根 `conversations.json` 必须通过 P6-I 严格 parser。2026-08-16 两份授权实包在该既有严格树合同下均得到零候选，故 fail-closed、零 Conversation 写入；需要最小化的结构证据才能扩展各自 parser，不能猜测兼容。
- 已确认事实与依据：P6-H/I/J 都只支持用户自行解压后选择的 JSON；P3 的 Conversation/Attachment/Preview 已能提供私有资产、树和媒体展示，但没有 ZIP entry ownership、富文本 IR 或 profile 映射 transaction。OpenAI 官方资料确认 ZIP 可能含根 `conversations.json`、assets 和 account metadata；Anthropic 官方资料确认其导出含 conversations/user data，但仍不足以作为 Claude ZIP 目录/字段的实现合同。
- 固定安全边界：ZIP 路径、URI、picker token、Key、cookie、密码、支付、组织权限、Provider/模型/同步设置不进入业务表或日志；拒绝 zip-slip、加密/多盘、炸弹、重复规范化路径、未知压缩法/版本和 manifest 不一致。会话、附件、provenance/receipt 只由专属 commit owner 原子新建；不 merge/覆盖已有 Conversation、附件、Key 或设置。K6 只接受 `nfai.third-party-profile-personalization/v1` 的七项白名单；Android/Desktop 各有一个 native profile owner，task 不保存值，owner transaction 负责 canonical value、receipt/provenance、幂等、冲突与批次撤销。
- 不采用方案：不盲目解压；不把 P6-H/I 的 JSON fixture 当 ZIP；不从文件名推断附件归属；不把 ChatGPT 格式套给 Claude；不将用户 profile/personalization 伪装为南枫 AI 账号同步或自动覆盖本地偏好。
- 重新评估触发条件：获得一份用户明确选择、可合法审计的脱敏 ZIP 或官方版本化格式说明后，先验证实际 ChatGPT entry 与媒体 stable ownership，或添加 Claude provider+version+manifest evidence；两者都必须先补 Parser-first 合同，之后才分别实现 Android/Desktop 确认事务与真实闭环。

## 决策：K7 只采纳 message-scope 的直接资产 identity，导出文件目录不是归属（已确定）

- 已确认事实：两份用户已选择的实包以只读匿名统计审计。ChatGPT 有 719 个非 JSON 资产，但其精确 entry-path/SHA-256 值只位于导出清单和逻辑文件目录，7,629 个已解析 mapping message 对象内为 0；Claude 没有非 JSON 资产。
- 当前选择：未出现“解析 message scope 内的精确完整 entry 或 SHA-256 identity”即保持 `UNMAPPED_REJECTED`。不从文件名、逻辑目录、导出清单、文本片段或时间邻近性推断任何消息归属。
- 影响：不写 Android/ Desktop attachment asset、Message attachment block、asset receipt/provenance 或 Preview owner；不改变现有聊天布局。未来只有 versioned provider relation schema 加合成 mapper→owner→receipt/retry/revoke 覆盖，才能重新评估。

## 决策：Compare Android 平台 Key/HTTP 只绑定已确认的精确双分支授权（已确定）

- 当前选择：`AndroidOpenRouterComparePlatformAdapter` 是唯一 Android credential/HTTP 平台边界；它只能由未过期的已确认 ChatGPT+Claude `CompareExecutionGrantedPlan` 构造，作为同一对象注入 `OpenAiCompatibleProviderTransport` 的 opaque credential handle 与 HTTP client。构造不读取 Key、不打开连接；每个 grant 中精确的 provider-facing model 仅可调用一次。
- 已确认事实与依据：Compare owner 的 grants 已绑定 context/text SHA-256、catalog/price/预算与五分钟期限，Schema 33 的 `RoomCompareBranchExecutionPorts` 已能逐 branch 写入安全 receipt 和 Usage reservation/release；旧 P3 test port、普通 Settings preset 和 legacy inference adapter 都不符合动态 Compare deployment/branch binding。
- 固定安全边界：固定 OpenRouter HTTPS POST、受限请求头、timeout 和 response 上限；取消、过期、handle/模型错配、重复、缺 Key 与异常均失败关闭。Key 只在平台 HTTPS 调用栈短暂以 `CharArray` 存在并 finally 清零；不记录文本、响应、Key 或 Authorization。该 adapter 不被 `AppContainer`、Activity、ViewModel 或 UI 注册。
- 不采用方案：不让普通聊天的 `EGRESS_UNREGISTERED` 展示确认充当 Compare 授权；不以 grant/hash 直接恢复原文；不允许批次内重试、任意 model/endpoint/header，或为测试绕过可见确认直接注入 Key/HTTP。
- 重新评估触发条件：不改已确认布局的 Compare 可见确认/执行公开入口通过定向与视觉验收，且用户在当次以非敏感文本明确确认 ChatGPT+Claude、费用/次数上限与外发范围；届时才可注入该 adapter 并做少量真实验证。

## 决策：Compare 原文只以临时租约交给一次批量双分支接收（已确定）

- 当前选择：`CompareExecutionApplicationOwner` 在汇总确认后继续唯一持有 process-memory-only 原文 lease；仅在同一同步临界区将同一 Canonical Context、两已确认 grants 和同一 lease 交给 `CompareBatchDispatchPort`。端口只支持 all-or-nothing batch acceptance，默认 fail-closed。
- 已确认事实与依据：MM-O4-A 的 grants 与 Schema 32 都只含 text SHA-256；hash 无法且不得恢复原文。若确认时就释放原文，后续执行不能在不降低隐私边界的前提下发生；若逐支交付，会产生第一支已接收、第二支不明的不可审计状态。
- 固定安全边界：取消、过期、确认范围变化、Store 拒绝、port 拒绝/异常和 accept 后均释放 lease。Schema 32 复用 append-only runtime event 持久化 content-free dispatch intent/outcome；进程重启的任何非 accepted 状态都是 `retry required`，必须 fresh text + fresh confirmation，绝不 hash 恢复、静默重发或重用旧 grant。
- 不采用方案：不落原文到 Room/log/Usage/receipt/handoff；MM-O4-D 不以 dispatch metadata 为由增加 Schema 33；不把 batch fake 当真实 Provider 执行；不预插 assistant、不改 current leaf、不写 P3-I receipt/Usage 或将 branch partial result 回滚 sibling。
- 重新评估触发条件：独立真实 Provider execution 增量能证明 batch acceptance 与其本地 outbox/branch runtime 事务一致，并获得逐第三方 egress、credential 与实际结果事实的授权。

## 决策：Compare OpenRouter 执行复用 P3 coordinator，不伪装为普通 preset（已确定）

- 当前选择：`OpenRouterCompareBatchDispatchAdapter` 是唯一 adapter readiness 接合层。它使用 `CompareBatchDispatch` 的两支 Store identity、同一临时文本和 grant 中的实际 provider-facing model ID，经同一 `RealTextExecutionCoordinator` 复用 `OpenAiCompatibleProviderTransport`；不接 legacy OpenRouter 推理路径。
- 已确认事实与依据：P3 `RealTextExecutionReadyPlan.presetId` 是普通 Settings/Registry 的 preset 事实，Compare deployment 来自当前 grant/registry，强行映射会把动态 target 写成错误的设置语义。新增 mutually-exclusive content-free `verifiedCompareDeployment` target 后，旧 P3 preflight 与 current-leaf 前提不被改义。
- 固定安全边界：默认 credential handle、HTTP client、receipt port 与 Usage port 均 disabled；adapter 不读取/解密 Key、不注册生产 composition、不产生假 receipt/Usage。batch accepted 只表示两个 coordinator execution 已接管；终态按 branch 独立，partial failure/cancel 不回滚 sibling。
- 不采用方案：不新增第二套 HTTP/JSON/error mapping/Usage/receipt owner；不注册 `RoomRealTextExecutionCoordinatorTestPortAdapter`；不升级 Schema、不预插 assistant、不改 shared current leaf；不以测试内容或历史消息作为真实外发资料。
- 重新评估触发条件：用户在应用内完成当次可见确认并提供授权的非敏感资料，同时已有 production-ready 逐 branch receipt/Usage 事务实现和受审查 credential/HTTP platform adapter。

## 决策：Compare receipt 只以独立 Schema 33 分支事实落库（已确定）

- 当前选择：`RoomCompareBranchExecutionPorts` 只对已存在的 Schema 32 Compare branch 追加 `compare_branch_execution_receipts`，并复用既有 immutable Usage Ledger 保存预算预留/释放。它不把两个 branch 硬塞进 P3 每 conversation 唯一 runtime/receipt，也不创造第二个 HTTP 或账本协议。
- 已确认事实与依据：协调器需要可重放的 `PREPARED/RUNNING/terminal` receipt，而 Schema 32 branch projection只有 RESERVED/终态，不能可靠表达运行态；P3 test port 同时依赖普通 conversation execution record，不能作为生产 Compare adapter。
- 固定安全边界：receipt 先逐项核对 branch 的 execution/invocation/attempt/请求指纹、会话、父 user 节点与 provider-facing model；partial delta 只在调用栈中存在。Schema 33 不含文本、响应、Key、HTTP 元数据或 MessageNode mutation。AppContainer 只注册 migration，仍不构造 Store/ports/adapter。
- 不采用方案：不复用 `RoomRealTextExecutionCoordinatorTestPortAdapter`，不更新 P3 receipt、`conversation_runtime_states`、`Conversation.currentLeafMessageId` 或 message tree；不因有 Room port 而宣称已读取 Key、可联网或真实执行。
- 重新评估触发条件：独立受审查的 Android credential/HTTP adapter 可在当次可见确认、用户授权文本和明确 egress policy 下被注入；此前保持 disabled/default unregistered。

## 决策：Direct production composition 只投影已验证 OpenRouter 目录（已确定）

- 当前选择：`OpenRouterVerifiedMultiProviderRegistryProjection` 是 `VersionedModelRegistry` 到多供应商 Logical Model/Deployment Registry 的唯一只读生产投影。它只接受当前 `OPENROUTER_CATALOG + VERIFIED + SHA-256 + 非 fallback` 快照，且 ChatGPT/Claude 仅由显式 preset 映射识别。
- 已确认事实与依据：P3-C fixture 是 `MOCK/LOCAL_FIXTURE`，P6-G `curated:*` 是本机显示选择 ID，均不代表可外发的 Provider-facing model ID。现有 AppContainer 有 OpenRouter 持久化目录槽位，但当前未保证已有可投影快照。
- 固定安全边界：provider-facing ID、价格/版本和 catalog version 必须原样取自已验证 snapshot；未知价格、无映射、fallback 映射、未验证或不支持来源拒绝。AppContainer 只构造惰性 owner，不能读取 Key/settings、HTTP、探测 Provider、写 Room/Usage 或注入 UI。
- 不采用方案：不从模型展示名、P6-G、UI、P2-M 合成 fixture 或测试数据推测部署；不在无目录时预置假模型；不把只读 projection 变成目录发布/回滚 owner。
- 重新评估触发条件：真实 P2-J 验证目录可稳定产生映射不足的 snapshot，或新增 Provider Adapter；届时先为该 Provider 增加独立 projection 与实际 target 合同，不能放宽现有 OpenRouter 投影。

## 决策：Direct 与 P3 的输入费用只使用保守预算上界（已确定）

- 当前选择：`ConservativeInputBillingBudget` 是 Direct confirmation 与 P3 reservation 的唯一输入计费 owner。它以 UTF-8 字节数作为保守 token 上界，并以精确安全算术计算最大预算；该数值绝不表述为 Provider tokenizer 结果、实际 token 或实际费用。
- 已确认事实与依据：旧的 `(text.length + 3) / 4` 会将中文等多字节文本折为乐观字符数，可能使 Direct 确认上限低于 P3 预留。共享领域 owner 使两处使用同一预算语义，并在溢出时失败关闭。
- 固定安全边界：Confirmation 只携带完整范围的 SHA-256 fingerprint，不含原文；其绑定 request id、execution/context、逻辑模型、部署、Provider、provider-facing model ID、catalog/price/currency、text SHA-256、输出上限和预算。待确认原文只可停留在有界短期 map，取消、过期、范围变化和确认/preflight 终态必须释放，之后只保留有 TTL/容量上限的 content-free replay 拒绝事实。
- 不采用方案：不猜测各 Provider 的精确 tokenizer，不把本地预算写成 Usage 实绩，不允许未知价格以确认绕过，也不通过 UI、Key、HTTP、Room 或 Usage 改动修复此债务。
- 重新评估触发条件：引入经验证、Provider 明示的预报价语义，或真实 Usage 对账合同需要新增独立事实字段；仍不得回写或改称本地保守上界为实际用量。

## 决策：采纳 Direct／Compare／Auto 同级多供应商模型编排（已确定，产品参数已确认）

- 当前选择：正式采纳《南枫AI_多供应商模型编排架构_v1.0》，以 `MultiModelOrchestrator` 统一产出 `DIRECT / COMPARE / AUTO` 三种同级计划；Compare 和 Direct 不经过 Auto Router，Auto 只在用户未指定目标时工作。
- 已确认事实与依据：用户明确要求优先加入总控并优先落地；现有工程已有 Provider Transport、Model Registry、Conversation Tree、Runtime Receipt 与 Usage Ledger，可增量复用而无需推倒重建。
- 关键推理与权衡：`Compare > Direct > Auto` 先解释为研发与架构优先级，保留当前普通对话未指定模型时的 Auto 默认表面；先做纯领域核心与 OpenRouter 首部署，再按一个 Adapter 一个验收扩充服务商，以避免 UI、网络、凭据和数据库同时变化。
- 固定安全边界：用户精确选择不得被 Router 或 fallback 覆盖；Compare 所有分支使用同一 Canonical Context Snapshot，默认不综合、不自动写入共享上下文；跨 Provider fallback 必须事先明确授权；Key 不进入编排计划、数据库、日志或遥测。
- 不采用方案：不以原方案设置页示意图覆盖现有 UI；不一次建设五家未经真实验收的 Provider；不新建与 `ConversationTreeService` 平行的比较消息树；不以 OpenAI-compatible 为理由合并各 Provider 身份。
- 首批实施：MM-O1 只增加纯领域类型、编排计划和合同测试，不修改 UI、不读 Key、不发 HTTP、不接线当前未验证 P3-J WIP。详细计划见 `MULTI_PROVIDER_MODEL_ORCHESTRATION_ADOPTION_PLAN.md`。
- 用户确认记录（2026-08-16）：`Compare > Direct > Auto` 仅作研发／架构优先级，普通对话未指定目标时继续默认 Auto；Provider 按统一核心、OpenRouter 首部署、原生 Adapter 一个一个验收；Compare MVP 默认且最多同时比较 ChatGPT 与 Claude 两个逻辑模型，并继续采用一次汇总确认、分支独立记账。动态 Provider-facing 实际模型 ID 不写死，由 Deployment Registry 解析。
- 重新评估触发条件：用户要求改变默认入口、MVP Provider 数量或 Compare 上限，或真实 Provider 能力证明 Logical Model/Deployment 映射不足。

## 决策：Compare 以独立 branch runtime 持久化，不复用 P3 的单 current runtime（已确定）

- 当前选择：Schema 32 以 `CompareConversationSessionStore` 为 Compare 持久化唯一 owner。它只追加 session、双 branch identity、独立 runtime/checkpoint、append-only runtime event 和 typed follow-up/adopt/synthesis/terminal intent；每 branch 独立引用 assistant、Invocation、Attempt、未来 execution、cancel 与 usage reservation identity。
- 已确认事实与依据：现有 `conversation_runtime_states` 对 `conversationId` 唯一，且 `ConversationRuntimeStateMachine` 的 start 会切换 shared current leaf，后续事件亦要求该 leaf 仍为当前。因此让 Compare 双 branch 硬走 P3 runtime 会互相覆盖，不能满足部分失败／取消保留 sibling 的合同。
- 固定安全边界：Schema 32 不插入无内容 `message_nodes`，不改 `Conversation.currentLeafMessageId`，不改旧 P3 runtime/index、P3-I receipt 或 Usage Ledger 事实定义；execution/reservation 在 branch 表中只是 future reference，不代表模型已执行或已发生用量。
- 不采用方案：不创建第二棵消息树；不以每 conversation 两条 P3 state 或覆盖 current runtime 的方式实现 Compare；不以计划写入伪造 P3 receipt、Usage reservation、Provider attempt、文本或 HTTP 事实。
- 重新评估触发条件：独立的真实 Compare execution 增量已获得外发授权、content lifecycle 与 P3-I/Usage 逐 branch 事务合同，并能证明 MessageTree mutation 不改变 shared current leaf。

## 决策：P6 反馈采用单一可消费索引，合同与路线各自保留唯一正文（已确定）

- 当前选择：`PRODUCT_FEEDBACK_DECISION_LEDGER.md` 是 P6 用户反馈、覆盖关系、阶段所有者、状态和复评条件的唯一索引；P6-E/P6-F/P6-F2/P6-G 合同拥有规则正文，`MASTER_DEVELOPMENT_BLUEPRINT.md` 只拥有路线。
- 依据与边界：对应 `FB-P6-001` 至 `FB-P6-022`；索引仅为后续复盘/交接准备，不授权复盘 UI、Provider、凭据读取或外部调用。
- 重新评估条件：出现新的跨阶段反馈类别，或静态完整性检查不能覆盖新增索引字段时，先更新索引与检查脚本，再变更实现。

## 决策：最终产品采用本地安全底座与受控联网双路径（已确定）

- 当前选择：模型执行固定区分 `LOCAL_OFFLINE` / `ONLINE_PROVIDER`，数据固定区分 `LOCAL_ONLY` / `ENCRYPTED_SYNC`；两条路径独立配置、独立授权、独立降级。
- 已确认事实与依据：用户已明确离线不是最终产品终态，Provider 与账号同步均为后续必须真实验收的产品路径；当前仍未授权读取 Key 或发真实 HTTP。
- 关键推理与权衡：本地路径必须始终可用，而联网路径必须保留 Key presence、目录、费用、逐次 consent、网络与同步状态，避免“一键联网”自动外发所有本地数据。
- 放弃方案及原因：不继续将 Desktop 表述为纯离线最终产品；不以 fake/loopback、配置页或模拟器替代真实 Provider/同步成功；不在当前阶段接入 Key、HTTP、OAuth 或云端。
- 风险与待验证项：真实 Key/HTTP 小额文本、Provider cancel/retry/费用、Google/Supabase 身份、跨网络同步、OPPO 和 Windows 均未完成。
- 重新评估触发条件：未来真实服务合同要求改变状态语义、凭据存储边界或同步授权范围时，先更新本决策与专属合同。

## 决策：先完成捕获闭环，随后以 Claude 级多模型对话为核心（已确定）

- 当前选择：第一落点采用“快速捕获与知识沉淀”，输入范围确定包含文本、Android 系统分享和图片；完成后立即把开发重心转向“Claude 级多模型对话体验”。
- 已确认事实与依据：用户明确选择捕获与知识沉淀优先，确认最小闭环包含图片，同时说明后续的 Claude 级对话才是自己最需要的能力。
- 关键推理与权衡：捕获闭环能尽早形成南枫 AI 独有的数据资产价值；但它不能演变为长期占用全部开发资源的知识采集工具。首个闭环必须主动收窄，避免延误对话主产品。
- 放弃方案及原因：不选择先完整打磨所有 Claude 级功能，因为会推迟首个可用闭环；不选择两条线同时完整开发，因为会造成范围失控和验证口径分叉。
- 风险与待验证项：图片会增加权限、私有保存、预览、压缩、视觉模型能力判断和第三方外发确认，但仍属于可控范围；文件、语音、视频和网页解析不得同时扩入首个闭环。
- 重新评估触发条件：最小捕获闭环不能产生可保存、可追溯的知识资产，或者真实使用证明用户只需要对话、不需要知识沉淀。

## 决策：Android 首版使用 Kotlin 与 Jetpack Compose（已确定）

- 当前选择：Android 首版采用原生 Kotlin + Jetpack Compose。
- 已确认事实与依据：用户在原生 Android、React Native、Web/PWA 三个方向中明确选择原生 Android。
- 关键推理与权衡：原生方案更适合系统分享、文件、语音、本地数据库、后台任务、Android Keystore 和真实设备体验；桌面端以后复用领域协议，不强求复用 UI 代码。
- 放弃方案及原因：React Native + Expo 不作为首版技术栈；Web/PWA 不作为移动端先行方案。
- 风险与待验证项：未来 Desktop / Web 无法直接复用 Compose UI，需要依靠稳定领域合同、数据协议和 Provider/Harness 接口控制重复实现。
- 重新评估触发条件：Android 原生无法满足目标平台，或未来明确要求 iOS 与 Android 同期开发并证明跨端收益高于迁移成本。

## 决策：V1 本地单用户、Provider 直连（已确定）

- 当前选择：V1 采用本地单用户、本地业务数据、本机安全存储 API Key、应用直连 Provider；只预留账号、云同步、远程 BFF 和 AI Hub 的接口边界。
- 已确认事实与依据：用户明确选择 V1 排除账号、云同步和远程 AI Hub。
- 关键推理与权衡：先验证核心产品价值，避免服务器、账号、同步、运维和云成本拖慢移动端闭环；同时保持未来扩展路径。
- 放弃方案及原因：V1 不同时建设可选 AI Hub，也不加入账号、跨设备同步或远程 BFF。
- 风险与待验证项：Provider 直连要求严格保护本地密钥，并处理各 Provider 的网络、限流和错误差异；未来同步必须设计可迁移协议，不能重新定义本地真值。
- 重新评估触发条件：移动端核心闭环和 Claude 级对话达到验收后，出现明确的跨设备同步、统一路由、集中成本或多应用协同需求。

## 决策：OpenRouter 作为首个真实 Provider，优先测试 Claude（已确定）

- 当前选择：V1 首个真实 Provider 使用 OpenRouter，第一批真实文本与图片调用优先测试 Claude 模型。
- 已确认事实与依据：用户明确指定 OpenRouter 优先，并指定 Claude 为第一测试模型族。
- 关键推理与权衡：OpenRouter 可以先验证统一模型入口、Claude 文本与视觉能力、用量和费用链路，减少首个闭环同时接入多个官方账户的成本；业务层仍通过 Provider Adapter，不直接依赖 OpenRouter 请求格式。
- 放弃方案及原因：V1 不同时接入多家真实 Provider；不把某个 Claude Model ID 写死在业务代码中。
- 风险与待验证项：开发时必须从 OpenRouter 当前目录确认可用 Claude 型号、视觉能力、价格、上下文与路由状态；OpenRouter 兼容调用不能替代未来对 Anthropic 原生能力的独立验证。
- 重新评估触发条件：OpenRouter 无法稳定提供目标 Claude 模型、图片能力、用量信息或错误语义，或者 Claude 级对话需要 OpenRouter 未暴露的 Anthropic 原生能力。

## 决策：完整总方案先于任何代码实现（已确定）

- 当前选择：在创建 Android 工程或业务代码前，先完成覆盖整个南枫 AI 生命周期的总控开发方案；现有 V1 实施计划只能作为总方案中的近期子计划。
- 已确认事实与依据：用户明确要求先固定整体方向与完整执行顺序，防止后续遗忘步骤、局部开发破坏总体架构或遗漏长期能力。
- 关键推理与权衡：总方案必须同时锁定产品阶段、架构演进、数据协议、平台路线、Provider/Harness、知识与记忆、Agent、生态整合、AI Hub、测试、真机、发布和复评门槛；每个阶段只实现当前增量，但不得失去后续兼容路径。
- 放弃方案及原因：不直接从 V1 工程骨架开工；不把短期 Backlog 当作完整路线；不为追求一次性完整而同时开发所有阶段。
- 风险与待验证项：总方案如果只列功能清单、没有依赖和停止条件，仍会失去方向控制作用；需要建立可检查的阶段门、所有权表、数据演进和遗漏检查表。
- 重新评估触发条件：产品定位、首发平台、本地优先原则或 AI Hub 边界发生用户明确变更时，先更新总方案和决策日志，再调整实施顺序。

## 决策：Android 本地真值与跨端复用方式（已确认）

- 当前选择：Android 使用 Room/SQLite 保存结构化业务真值，附件二进制放在 App 私有文件目录；未来 Desktop 优先复用领域语义、版本化导入导出协议和合同测试，不把共享 UI、共享数据库文件或共享运行时作为 V1 前置。
- 已确认事实与依据：用户已选择 Kotlin + Jetpack Compose、本地单用户和移动端先行，同时要求整体方案不能因局部实现破坏长期跨端方向。
- 关键推理与权衡：Room、私有文件和版本化协议能先满足 Android 离线、迁移和生命周期要求，也能避免为未来 Desktop 过早引入云 Core 或跨端框架；代价是 Desktop 可能需要独立实现客户端数据层。
- 放弃方案及原因：不让 Desktop 直接打开 Android 数据库文件；不为代码复用优先改回 React Native；不在 V1 建立强制远程 Core。
- 风险与待验证项：具体 Schema、数据库静态加密范围、附件清理、协议字段和迁移性能需在 P1/P2 评审与实测。
- 重新评估触发条件：出现明确的多端同步需求，或行为合同不足以避免 Android/Desktop 语义分叉。
- 确认记录：用户于 2026-08-12 确认总控开发蓝图，本决策随总图转为已确认基线。

## 决策：Desktop、同步、Agent、生态与 AI Hub 的长期顺序（已确认）

- 当前选择：Claude 级对话、项目/记忆/知识和 Android 产品化完成后，依次建设 Desktop 控制中心、可选端到端加密同步、受控 Agent、南枫生态；只有至少两个应用稳定使用 Provider Framework 且集中治理收益有证据时，才建设可选 AI Hub。
- 已确认事实与依据：用户要求先固定完整方向；原始资料同时要求移动优先、桌面深度工作、本地优先、Agent 演进和 Hub 可选。
- 关键推理与权衡：顺序先证明个人核心价值和本地协议，再承担跨端、云端、自动化和集中运维复杂度；P7 同步允许在真实多设备需求出现时与 Desktop 中后段协调，但不能改变离线本地模式。
- 放弃方案及原因：不在 V1 同时建设 Desktop、账号、同步、Agent 和 Hub；不让 Hub 成为南枫应用的业务数据库或启动依赖。
- 风险与待验证项：Desktop 技术栈、真实跨设备频率、服务器预算、生态应用优先级和 Hub 触发时点必须在各阶段重新核验。
- 重新评估触发条件：用户明确改变阶段优先级，或真实使用、外部服务和维护成本证明当前顺序不合理。
- 确认记录：用户于 2026-08-12 确认总控开发蓝图，本顺序随总图转为已确认基线。

## 决策：P2-A 采用 Room 初始 Schema、私有内容寻址附件与领域导出快照（已确定）

- 当前选择：结构化 Capture Draft、Evidence、Attachment 元数据与 Knowledge Item 由 Room Schema 1 保存；图片二进制先导入 `filesDir/attachments/v1`，以 SHA-256 内容名、MIME、大小与逻辑存储键关联；导出使用版本化领域快照，不暴露 Android 绝对路径。
- 已确认事实与依据：产品基线要求 Android 本地数据库为业务真值、附件进入 App 私有目录、导出可供未来 Desktop 精确保真；P2-A 合同已明确外部 URI 不能成为业务真值。
- 关键推理与权衡：Room 的关系表与导出 Schema 可保留来源、顺序、稳定 ID 和调用溯源；内容寻址副本避免外部临时 URI 失效且便于完整性校验。代价是后续需独立实现垃圾回收、回收站、Migration 和真实导出包，不能在首次写库时偷偷删除或迁移数据。
- 放弃方案及原因：不把图片 BLOB 塞入业务表；不将 `content://`、`file://` 或绝对路径保存到 Room/导出；不以 UI 展示文本作为导出真值；不引入 Room KAPT，因为 AGP Built-in Kotlin 不兼容。
- 技术例外：KSP 2.2.10-2.0.2 目前需设置 `android.disallowKotlinSourceSets=false` 才能和 AGP Built-in Kotlin 协作生成源码；它是明确记录的临时兼容开关，不是退回 Kotlin Android 插件或 KAPT 的授权。
- 风险与待验证项：真实相册/系统分享输入、临时 URI 权限、私有附件清理、Schema 升级、真实导出文件、设备重启及真机恢复尚未验证。
- 重新评估触发条件：升级 AGP、KSP 或 Room；新增删除/回收站、导入恢复、真实导出包或非图片附件；或发现当前领域合同无法支持跨端回读。

## 决策：GitHub 成熟项目采用能力级参考而非产品 Fork（已确定）

- 当前选择：公开 GitHub 项目只按能力吸收成熟状态模型、接口、失败恢复、测试和交互经验；南枫 AI 不 Fork 任一现成 AI 工作台，也不在 P1 引入这些项目的运行时依赖。
- 已确认事实与依据：2026-08-12 研究了 RikkaHub、PocketPal AI、LibreChat、Memos、AnythingLLM、LiteLLM、Langfuse、Promptfoo、Mem0、LangGraph、OpenHands Agent SDK、AppFlowy 等项目；没有单一项目同时满足原生 Android、本地业务真值、逐次外发确认、知识/记忆、加密快照、受控 Agent 和南枫生态边界。
- 关键推理与权衡：Fork Web/Server 产品会带入多用户、云端、插件执行和运维复杂度；Fork AGPL 或自定义许可证项目还会扩大分发约束。自主领域合同可保留南枫 AI 的产品顺序，同时复用成熟项目已经验证的消息树、类型化流、文档队列、调用树、Memory CRUD、Eval、Checkpoint 和风险确认思想。
- 放弃方案及原因：不以 Star 数决定技术选型；不把 OpenAI 兼容层当统一业务模型；不复制 AGPL、社区许可证、修改版许可证或企业目录源码；不把 LiteLLM/Langfuse 服务设为 V1 前置。
- 风险与待验证项：文档中的经验仍需在 Kotlin/Android、本地数据和目标真机上重新实现并验证；未来新增具体依赖时必须核验当时许可证、版本、遥测、传递依赖和维护状态。
- 重新评估触发条件：未来决定直接嵌入、分发、修改或托管某个外部项目，或许可证/商业模式发生变化时，先完成文件级法律与供应链评审。
- 证据：`GITHUB_MATURE_PROJECT_REFERENCE_RESEARCH.md` 与 `MASTER_DEVELOPMENT_BLUEPRINT.md` 3.4。

## 决策：P2-B 使用 Photo Picker 后立即私有复制（已确定）

- 当前选择：相册图片只通过 Android Photo Picker 单选入口进入；不申请整库媒体权限。系统回调 URI 只用于本次打开，随后立即流式复制到 App 私有附件目录并写入 Room 草稿。
- 已确认事实与依据：Android 官方 Photo Picker 支持单图选择且无需整库运行时权限；项目既有合同要求外部 URI 不成为长期真值，Activity 重建后必须从 Room 与私有附件恢复。
- 关键推理与权衡：立即复制可避免临时 grant、设备重启或来源应用变化导致草稿失效；代价是占用 App 私有空间，因此设置 20 MB 单图上限，并把删除、回收站与垃圾回收留给独立高风险生命周期设计。
- 放弃方案及原因：不保存 `content://` 到 Room；不为一次选图申请 `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE`；不把页面预览成功当成草稿保存成功；不在 P2-B 同时接拍照或系统图片分享。
- 风险与待验证项：Photo Picker 回退实现、OEM 相册、真实 HEIC/WebP、大图内存、Activity/进程重建与 OPPO 视觉仍需模拟器和真机验证。
- 重新评估触发条件：需要多选、后台长期上传、非图片附件，或私有空间占用与清理策略进入实现阶段。

## 决策：首个可安装版本即固定正式签名（已确定）

- 当前选择：南枫 AI 从 `versionCode=2` 起固定独立正式证书，Debug 开发验收主 APK 与 Release APK 使用同一证书，后续只允许同签名覆盖升级。
- 已确认事实与依据：用户明确把“所有开发软件提前正式签名”设为跨项目标准，目的是避免默认 debug 证书安装后补签名造成覆盖失败、卸载和数据迁移风险。
- 关键推理与权衡：每 App 独立 keystore 可隔离泄漏风险；仓库外 keystore、系统钥匙串口令、备份和证书指纹共同保证恢复性。代价是本机构建依赖受控签名资产，因此 Gradle 对所有可安装构建启用 fail-closed 门禁。
- 放弃方案及原因：不等 Release 阶段才生成证书；不把 keystore/口令提交仓库；不复用 Android 默认 debug 证书；不在真实设备遇到签名不一致时卸载或清数据绕过。
- 风险与待验证项：OPPO 尚未安装；正式设备同签名覆盖和数据保留仍需单独验收。keystore 灾难恢复需要定期核对备份与证书指纹。
- 重新评估触发条件：证书轮换、Google Play App Signing、包名变更、跨平台 CI 或密钥托管策略变化。

## 决策：P2-F 以追加式 Room Ledger 保存安全 Invocation 事实（已确定）

- 当前选择：Invocation 的唯一持久化所有者为 `InvocationRepository`；`RunAiTaskUseCase` 写入 `InvocationRecord → TaskRun → ProviderAttempt → Generation → Validation`。Schema 2 用单一事务写完整层级，并以稳定 ID 做同内容幂等。
- 已确认事实与依据：总蓝图要求调用历史保留实际 Provider、Model、Harness、价格、Token、费用、错误和终态，同时禁止 Key、完整 Prompt/响应、原图和附件正文进入本地账本。P2-E 已有四层领域合同，P2-F 负责将其持久化和可见化。
- 关键推理与权衡：把账本从 Capture、Knowledge、Provider Adapter 和 UI 分离，才能让进程重建、失败诊断、成本读取和未来重试共享一套事实；代价是新增 Schema 2 与显式 Migration，但避免以清库或 UI 记录替代运行历史。
- 放弃方案及原因：不把完整请求/响应存入 Room；不让 UI 直写 DAO；不把确认门禁伪造成 Provider Attempt；不以未知 Token/费用的零值掩盖缺失；本阶段不提供删除入口，避免未经授权的历史清除。
- 风险与待验证项：真实 Provider 多 Attempt/重试、真实取消、实际 Token/费用与长期保留/用户删除策略均未验证；真实服务必须另获联网、Key 和非敏感资料授权。
- 重新评估触发条件：引入流式运行、重试/Fallback、多 Provider、用户可见删除/导出或任一真实服务字段无法映射到当前安全合同。
# 2026-08-13 — ADR-002 P6-A Desktop 与交换协议

- 选择 Tauri 2 进入 P6-B；PWA 只作能力受限 fallback。依据、官方核验来源、spike 证据、复审条件与 Windows 债务见 `ADR-002-P6A_DESKTOP_AND_EXCHANGE.md`。
- `nfai.exchange.v1` 是独立的版本化语义迁移包，不是 P5-D backup/Room DB；权威 schema、canonical hash、预检/敏感/升级规则见 `NFAI_EXCHANGE_V1_CONTRACT.md`。

## 决策：普通聊天以消息归属记录持久化请求级费用（2026-08-25）

- 当前选择：普通聊天的唯一费用 owner 为 `assistant_response_model_attributions`。它沿用已绑定的 `assistantMessageId + attemptId`，只增加 Token、USD micro、价格版本和来源；初始模型归属可先写入，最终回包只允许同一行由未知单调补全为金额事实。
- 已确认事实与依据：OpenRouter 官方 Usage Accounting 在非流式完整响应或流式最后一个 SSE 事件返回 `usage.cost`；该值是账户实际扣费。普通聊天此前只把 Token 写入安全调用摘要，未将费用与可见助手消息关联。
- 关键推理与权衡：先使用 `usage.cost`，包括合法的零金额；只有金额字段不存在时才按用户提供的校准价目表估算，并在 UI 明示“估算”。持久化使用 USD micro 整数，展示层再格式化，不使用浮点金额；本期不抓取网页、不调用用户 Key 的额外账单接口，也不把日/月聚合账单伪装成单条精确费用。
- 放弃方案及原因：不另建无消息关联的聊天账本；不以当前 Composer、当前价目表或当前 Provider 覆盖历史；不把缺失费用默认写为 `$0`；不保存 generation 原始回包、提示词、回复、附件或 Key。
- 风险与待验证项：generation ID 异步补查、在线 `/models` 价格缓存、非 OpenRouter 官方直连的完整价目表和账期对账尚未实现；真实 OpenRouter 服务回包需在用户已配置 Key 的真实发送中手工确认。
- 重新评估触发条件：加入多 attempt/fallback 合并费用、generation 补偿任务、实时汇率/人民币展示、在线价目表或用户可删/导出账本时，先扩展专用成本领域合同。

## 决策：云同步以手动成功对话为唯一选中集（2026-08-29）

- 当前选择：对话长按菜单的同步动作在登录与恢复保护就绪后直接同步该对话；只有云端提交和回读成功才写入手动选中记录。设置中用户显式开启的定期同步，每 12 小时只重试当前 Google/Supabase 账号的这些记录。
- 已确认事实与依据：用户明确要求“点击对话的同步要直接同步”，同时“定期同步要同步我所有手动同步过的内容”，目的是追踪这些对话后续更新，不是自动上传全部本机数据。
- 关键推理与权衡：用本机无正文回执表记录账号、对话 ID、文档 ID、修订号和哈希，既能让定期任务精确重访已选对话，又不保存云端明文或扩大数据范围。未知云端版本默认冲突停止，安全性高于自动覆盖。
- 放弃方案及原因：不定期扫描全部对话；不默认同步记忆、知识、项目、设置或附件；不用“进入账号页”代替用户已要求的直接同步；不把未成功的尝试当作后台同步授权。
- 风险与待验证项：当前没有本应用真实 Google/Supabase 配置，所以身份、云端密文往返、定期任务的真实网络失败恢复和跨设备恢复仍需独立验收。含附件或工具结果的对话当前整体拒绝上传，不能称为完整对话同步。
- 重新评估触发条件：未来实现用户可见的“停止同步”、附件密文参照、跨设备文档发现／恢复或冲突解决时，先扩展 P7-F 合同，不改变“手动成功才进入选中集”的默认边界。
