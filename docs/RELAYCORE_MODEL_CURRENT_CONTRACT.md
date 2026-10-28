# RelayCore 模型命名与分组合同

<!-- contract-governance:start -->
> **合同口径：现行领域合同。** 领域入口：[ Relay渠道与协作检索 ](RELAYCORE_MODEL_CURRENT_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** RC预设/渠道/别名/历史身份；联网规则归C12，准确授权归P3J
<!-- contract-governance:end -->

状态：双端源码已接入下表六个预设；平台清单已核实，真实 Key 配置／调用／安装另行验收。

## 范围与事实门槛

本合同仅针对新增 RelayCore，其他 Provider 沿用现有名称与行为。具体模型版本、渠道、倍率必须从平台当前清单及接口证据核实；示例不得作为可用模型预设或虚构真实调用记录。

## 唯一显示规范

- 2026-10-10 用户再次确认并要求记住：平台区分统一只用 `RC`；Auto、选框、回答底部不再用 Pro1／Pro2／Pro3、Kiro／Kiro正价等渠道名替代平台。内部API ID、实际返回身份、渠道、倍率、用量和历史记录保留。Astra 最新显示名为 `6 Astra`，选框保留RC角标，回答底部为 `6 Astra · RC`，兼容旧Astra显示；实际 `gpt-6-astra` ID不变。Auto弹窗显示当前可用候选的 `模型名 · RC`；回答底部采用选框短名，例如 Gemini 3.8、Gemini 3.1、6.1 Sol、6 Astra，保留版本/必要区分。明确非RC平台的快照不能从旧渠道文字推成RC。

| 位置 | 格式 | 用户确认的格式示例 |
|---|---|---|
| 模型选择列表主名称 | 具体模型及版本 · 倍率 | `Opus 5.5 · 0.35×` |
| 模型选择列表底部小字 | 只说明核心联网方式，不重复平台或渠道 | `实时联网`／`未联网`；Desktop辅助路线可显示`Luna · OpenRouter 检索` |
| 内屏／展开输入框模型选框（含临时会话） | 具体模型及版本 · 倍率，与列表主名称一致 | `Gemini 3.1 Pro · 0.28×` |
| Android 外屏／紧凑模型选框（含临时会话） | 沿用 OpenRouter 紧凑命名，省略渠道与倍率 | `6.1 Sol`、`6 Astra`、`Gemini 3.1` |
| 回答底部 | 本次模型短名 · RC；完整身份留调用记录 | `Gemini 3.8 · RC`、`6.1 Sol · RC` |

- RelayCore 平台名仅显示在分类入口及二级页标题；模型行下方小字删除重复的 RelayCore 前缀。列表小字仅保留核心联网说明，Kiro正价／Pro3 等真实渠道留在配置与调用详情。
- 用户后续确认外屏完整名称不足，按其他 OpenRouter 模型的标准显示：`Opus 5.5`、`Sonnet 5.5`、`6.1 Sol`、`6 Astra`、`Gemini 3.8`、`Gemini 3.1`；只改变紧凑选框投影。内部 compactName／返回身份／历史渠道保持冻结；新 composerName 专用于外屏选框。
- 双端模型选框使用无底色、无边框、灰色 `RC` 角标区分 RelayCore：角标紧贴**模型文字右上方**，与文字形成一个居中的内容组，不固定在选框右上角。Android普通／临时及外屏／内屏共用组件，名称12sp、角标7sp，间隔2dp、向上3dp；Desktop普通／临时共用caption组，名称11.5px、角标7px（随应用字体缩放），间隔2px、向上3px。保留原宽度及点击区，长文字先省略，角标不独立响应点击。根据真实手动预设或当前Auto候选识别平台；其他Provider不标RC；无障碍说明包含RelayCore，装饰文字不重复朗读。
- Desktop 临时会话只保存本地恢复记录，模型列表小字统一显示 `本地恢复标识`；不依据普通会话的联网开关宣称临时记录已搜索。
- 按 2026-10-10 最新要求，倍率出现在模型选择列表主名称及内屏／展开输入框模型选框；Android 外屏按随后确认的紧凑命名省略倍率，保留原 88dp 宽度；回答底部、详情、通知和导出仍不展示倍率。历史回答快照、实际渠道和计费元数据保留。
- 使用 `×`，不写 `x`；模型、渠道、倍率用 ` · ` 分隔。倍率取平台当前核实值，不从模型等级推算；历史模型身份和渠道记录不得按当前目录重命名。
- 2026-10-10 用户确认：Gemini 名称中已包含 Gemini 时，省略表面的重复 Gemini 渠道；内部渠道、倍率、API ID 与历史归因保留，兼容读取旧显示名。
- 同模型不同渠道形成可辨识的选择项。明确选中的渠道不得自动换成另一渠道后继续沿用原名称。
- 回答名称依据本次返回身份及执行渠道；缺失证据标明未验证，不用请求值补造已验证身份。最新版本和高质量均不得由名称或倍率推断。

## 日常／深度分组

- 模型选择根菜单按“自动、日常、深度、RelayCore”显示。全部六个 RelayCore 模型移入独立二级菜单，用小标题“日常 / 深度”分组，原日常／深度菜单不再重复展示。
- 选择入口不改变精确 API ID、已存 logical ID、内部能力层级和历史回复归因；Auto 顺序遵循 [模型选择与 Auto 合同](P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md)。已选 RelayCore 模型在根菜单标记 RelayCore，二级页标记具体模型，返回先回分类页。

- 平台确认只能提供 `Opus 4.8` 这类旧版本时，允许作为 RelayCore 的日常选项；主名称如实显示版本，不包装成当前旗舰。
- 平台确认提供 `Opus 5.5` 这类当前旗舰版本时，放入深度。
- 其他 RelayCore 模型按核实后的版本和定位确定分组，不按倍率排序或自动判定质量。其他 Provider 的分组不受此合同影响。

## 接入前必须确定的清单

每项确定精确 API 模型 ID、模型显示名、渠道标识、倍率及其计费基准、凭据与渠道绑定方式、协议、分组和验证证据。未确定的项不进入可发送目录。

## 验收

双端分别验证选择列表、输入框和实际回答底部三个位置；验证同模型不同渠道不混淆、列表以外无倍率、旧版本日常／当前旗舰深度、历史身份不被升级重写。模型及费率示例只可用于隔离测试，不替代真实服务可用性验证。

## 当前六个核心预设（2026-10-10 GPT 分组更新）

模型清单来自 2026-10-09 已登录的 RelayCore 模型广场；2026-10-10 按用户授权把 GPT Key 切到 Codex | Pro3（0.25×），原生 Sol 检索已实测通过。其余各项调用仍须逐项验收。每家两个，不新增未列出的 AWS 路线。

| 分组 | 精确 API ID | 选择列表／输入框选框 | 正常匹配回答底部 |
|---|---|---|---|
| 深度 | `claude-opus-5.5` | `Opus 5.5 · 0.35×` | `Opus 5.5 · RC` |
| 日常 | `claude-sonnet-5-5` | `Sonnet 5.5 · 0.35×` | `Sonnet 5.5 · RC` |
| 深度 | `gpt-6.1-sol` | `GPT-6.1 Sol · 0.25×` | `6.1 Sol · RC` |
| 深度 | `gpt-6-astra` | `6 Astra · 0.25×` | `6 Astra · RC` |
| 日常 | `gemini-3.8-flash` | `Gemini 3.8 Flash · 0.28×` | `Gemini 3.8 · RC` |
| 深度 | `gemini-3.1-pro` | `Gemini 3.1 Pro · 0.28×` | `Gemini 3.1 · RC` |

- 模型设置的平台入口固定为五个：OpenRouter → RelayCore → DeepSeek → 智谱 → Qwen，一行等宽、统一紧凑比例。RelayCore 是第二个；内部 Claude／GPT／Gemini 三个配置保存独立 Key，三个内部 Provider ID 不能被当作七个平台入口。
- 平台设置入口及配置身份允许显示 RelayCore；模型列表行不再重复平台名。列表与内屏输入框选框主名称均为模型＋倍率，Android 外屏选框采用下述紧凑命名，页脚按 RC 简写平台，详情与通知保留冻结的实际归因。
- 2026-10-11 附件失败修复：模型广场的同一 `Claude-Kiro正价` 0.35组同时列出 `claude-opus-5-5` 与 `claude-opus-5.5`，基础价／最终价一致。旧连字符ID在现有Key上重复504；点号ID经同一Key的Messages SSE重复附件、联网协作与长材料验证。当前双端目录／profile／连接测试／发送采用 `claude-opus-5.5`；仍为Opus5.5／原Key／原正价组，不换Provider、版本或倍率。不按失败动态切API ID、不自动重发。历史Attempt／已落盘账单保留原ID；显式旧失败重试只对这一个已验证同组别名规范化，其他Provider／版本不放宽。API返回原值保留，价格owner兼容两个历史拼写，CNY规则不变。旧20261009／20261010 fixture保留，当前为20261011 fixture。
- 当前路由与 Key 分组必须匹配：`RELAYCORE_CLAUDE` 对应 Claude-Kiro正价；`RELAYCORE_GPT` 对应 Codex | Pro3；`RELAYCORE_GEMINI` 对应 Gemini。Key 名称不是分组证据；网站若修改绑定，App 配置必须同步核实。不同分组不能共享本机 Key 记录或偷偷切换。
- 三把网站 Key 的推荐名称为 `南枫AI-Claude-Kiro正价`、`南枫AI-GPT-Pro3`、`南枫AI-Gemini`。Claude Key 已按用户确认切到 Claude-Kiro正价；未来若改分组，必须同步核实应用配置，不能把其他分组冒称普通 Kiro。
- 六个候选保留手动 RelayCore 二级菜单；普通聊天 Auto 已按 [模型选择与 Auto 合同](P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md) 纳入文本候选，不加入标题／自动整理／定时监控。联网、工具、图片等未获当前 Relay 协议证据的能力不宣称已支持；普通文本及本机已解析文本沿用 OpenAI 兼容接口，显式要求联网时拒绝离线代替。
- Android 单次返回原始 model ID 保存在回复归因，原请求 ID 仍由 send Attempt 保存；流式返回身份也必须经过 adapter → SSE 聚合 → completed reply。缺失显示“返回模型未验证 · 渠道”；不一致保留回答并告知异常，不自动重发。Desktop 沿用请求快照、实际 model ID 和 Attempt 记录，页脚根据该条消息返回身份与冻结渠道显示，不从当前选择倒推历史。
- 当前名单来自平台列价快照。后续新版本发现、跨渠道质量验证、计费基准和实际结算需要新的接口证据；未实施周期性付费全量探测或自动升级，不把本次固定六项称为完整自动探测系统。
- 功能审阅：保留 RelayCore 低成本接入；复用模型设置、现有模型选择与回答信息入口，无新增常驻按钮。理由：每次使用可以辨认模型和配置渠道，三个分组凭据独立；全部 RelayCore 模型集中到独立二级菜单，日常／深度内部小标题清楚且不重复。

- 倍率是平台基础单价的分组计费系数，最终单价以模型广场为准，不代表速度或质量，也不证明上游身份。未确认币种的 `usage.cost` 不得作为美元实付金额写账；模型返回字段只能证明平台的返回声明，不能独立证明上游真实厂商身份。

金额币种和换算遵循[费用台账合同的RelayCore人民币实际计费规则](AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md#relaycore-人民币实际计费用户确认长期规则)，不从平台美元样式推导真实USD。

## 手动选择的持久身份

- 全部模型及双端遵循 [P6-G 手动选择持久规则](P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md#优先级与目录)，重开和异常不能改写对话的手动选择。
- Android 在手动选择落盘后以及发送／重试外发前，同步提交当前会话入口检查点；检查点写失败阻止外发。该检查点只保存会话ID、时间和生成状态，不改模型或正文。覆盖安装必须先检查后台生成服务，正在生成就停止覆盖，不自动停止请求。

## 当前联网协作与身份

- RC普通文字Sol/Astra保留自身Responses搜索及来源回传；其余四款普通文字、六款联网附件统一使用OpenRouter Luna检索。本次具体路由、原生来源、预算及失败语义唯一正文见[C12](C12_MODEL_NETWORK_PARITY_CONTRACT.md#当前按渠道和材料冻结搜索路由)。所选RC模型仍是最终回答接收方；三个RC凭据分组保持独立。
- 模型菜单联网小字按各平台当前renderer表达状态，Desktop辅助路线可保留`Luna · OpenRouter 检索`；正常与重试Composer不再常驻接收方、材料清单或费用重复说明，不新增同类提示或按键。准确接收方／材料仍由既有授权owner冻结并校验；删除文案不扩大权限。精确授权及历史兼容唯一正文见[P3J](P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md#当前luna辅助接收方授权)。功能审阅复用原入口，保留该按场景路由，理由是正常自身联网不受助手覆盖、复杂附件的检索与回答身份清楚。
- RC普通回答协议保持Claude Messages SSE、GPT/Gemini既有Chat Completions；Gemini仅检索后回答撤去legacy max_tokens。接口能力与实际可用性不同，404、空正文和缺实际返回身份如实保留，不静默换型号或协议。

## 历史同平台Sol协作（旧记录解释）

本节为替换Luna之前的实现及用户讨论，不能作为当前新发送的路径或授权。共用终态、公开来源安全与历史保真规则继续适用。


- Sol与Astra的Responses联网统一使用`tool_choice="required"`，并在原生`web_search`工具中显式发送`external_web_access=true`。system→developer和完整纯文本块投影按C12保持双端一致。同任务分批的匿名路由标识及其隐私／失败边界同由C12持有，不把路由元数据当真实来源或服务成功证明。2026-10-12公开材料单变量控制表明，Sol的type-only选择会被网关丢弃，而required返回真实搜索与原生引用；请求字段本身仍不算执行证据。原生执行／来源门禁不放宽。附件逐项结果只保留匹配真实结构化来源的URL，额外自然语言链接剔除；每个已核实／矛盾事项仍须至少一个真实来源，无来源不得通过。普通Markdown事项标题可使用`[A1]`或`A1`；行首结果标题建立分项，另兼容首行开场句与明确`[A1]`状态相连的流式边界，正文交叉引用不创建新行。嵌套Markdown引用多出的闭括号只在候选URL与原生结构化来源精确匹配时修正，保留URI内合法括号／query身份；未覆盖／重复／额外ID仍拒绝。
- GPT-6.1 Sol／GPT-6 Astra 原生搜索使用 RelayCore Responses 流式接口，普通回答与同平台 Sol 检索均在首字到来后逐步消费；须有明确 completed 终态、完成的 search call 和结构化来源，不因仅有 SSE completed 或自然语言链接就宣称已联网。搜索请求不自动改 Provider 或重发 UNKNOWN。
- GPT-6.1 Sol、GPT-6 Astra 优先使用自身原生搜索；Astra 对简单查询使用 Low effort／low 搜索上下文，复杂查询使用 Medium，保留原模型身份。Claude、Gemini 当前渠道的普通对话原生搜索尚未稳定通过时，由同平台 GPT-6.1 Sol 检索，再交给原选模型生成最终回答；所选模型与 API ID 不替换。最终归因来自原模型实际返回，来源来自完成的检索事实；多批独立计量。
- 模型选择列表小字只说明实际联网方式：Claude／Gemini 当前辅助路线为 `GPT-6.1 Sol 检索 · 附件要点`，GPT 原生路线为 `实时联网`；关闭联网时为 `未联网`。不重复 RelayCore、Gemini 或渠道名。输入框与重试说明不重复检索模型提示；配置身份和调用详情保留真实渠道与检索事实。
- 2026-10-10 用户澄清：只有自身联网路线省略辅助模型备注；当前实际走 Sol 的 RelayCore/Kiro Claude 与 Gemini 保留明确说明。按随后截图反馈，Android／Desktop 输入框上方删除常驻“联网将用…”重复提示及其样式，检索路线仅在模型选择弹窗中解释；重试说明不重复弹窗已说明的检索模型；重试仍按冻结的原调用路线执行。“本次回答信息”及费用记录冻结实际检索模型与执行事实。检索接收本次问题、最多两条必要历史用户文字，以及当前已批准附件中的必要核验要点与公开网址；不发送整份附件、文件名、系统指令、Memory或续写控制指令。私密／凭据／个人配置行排除，原选回答模型保持。
- 协作检索遵从[C12全范围检索](C12_MODEL_NETWORK_PARITY_CONTRACT.md)：公共事项分批完成，缺证按规则补查，随后由原选模型写最终回答；不是整份任务只允许一次API调用。每次物理检索明确结束，上游504／UNKNOWN保留失败与计量，不冒称完成或自动重发。
- 双端共用完整事项计划和共享fixture，不以12项取样替代整份任务。每段线索容量与调度限制遵从C12；公开网址保留页面定位query及原顺序，去已识别追踪参数／fragment，拒绝凭据参数、userinfo或非公开特殊地址。OCR空格只按公开地址规范化，不猜造链接。
- 完整附件公共范围、材料长度预算、私密字段过滤、未取得线索后的材料分析和scope失败收尾遵从C12；授权仍绑定当前准确附件hash与已披露接收方，不发送其他会话、文件名、记忆或隐藏系统内容。历史授权只读兼容，不借本轮编排扩大接收方。
- 来源摘要无论自然分项或JSON均移除嵌入链接，只有匹配真实结构化来源的URL作为来源传递。源标题换行／方括号不能形成额外来源记录；Mac读取格式化记录中实际URL，不把标题中的网址当证据。原生完成、模型身份、逐项状态和来源门禁保持，不自动重发或换模型。
- helper须有真实完成的搜索事件、精确返回身份及适用结构化来源；每批已核实／矛盾项须匹配真实工具来源。缺项／未执行／无关泛化回答由C12编排拒绝并有界补查；单项已实际搜索但证据不足如实保留。每个物理响应内部来源仍有24条预算，整份任务汇总不因旧12项假设截断；Android正文来源投影有256条预算，范围和来源不足不得冒称完整。最终正文使用事项名称，不显示ID／JSON，检索模型记忆不算新证据。
- 已配置且本次已披露的独立搜索服务优先用于附件公共事项，具体service身份与v3材料授权唯一规则见[C12](C12_MODEL_NETWORK_PARITY_CONTRACT.md)。它不是Sol模型或失败fallback；以下Sol协作规则适用于未选择独立服务的既有路径。
- 附件要点扩展纳入本次准确接收方披露：Android normal-chat-egress-v3，Desktop normal-chat-relay-search-v2；旧窄范围receipt不能直接授权新增材料。发送与显式重试共用当前范围，旧Opus同组已验证别名仅按既有规则规范化；不新增常驻按钮或额外确认。
- 历史发送账本的disclosureVersion是不可改写的事实元数据，读取和启动恢复不得用“是否为当前授权版本”拒绝历史记录。Android接受格式有效的normal-chat-egress-vN历史标识（含v1／v2），保留原值；只有新的NormalChatEgressAuthorization使用当前v3。历史账本能读取不产生新的网络授权，未知结果不自动重发。Desktop历史外发版本同样保持原值，新请求仍独立校验当前范围。
- 当前请求包含附件且已批准原模型时，辅助检索失败不阻止原模型读取原附件进行材料分析。明确注入“外部检索未完成、仅分析材料、外部事实未核实”；正文落盘、实测用量保留，但整体仍为 `FAILED / WEB_SEARCH_NO_SOURCES`，不能标为联网成功。普通无附件问题沿用原失败门禁；取消、授权或本地持久化失败不继续调用，UNKNOWN 检索不重发。手动重试重新尝试原问题，不把已完成的材料分析接成续写。
- 两次调用独立记录 Token 与返回身份；回答失败或取消仍保留已完成检索用量。未核实币种的 cost 不写作美元实付，总费用未知时不把主模型金额冒称两次调用总额。详情冻结检索模型，保留原回答模型。
- 回答底部在双端显示 `模型名 · RC`，包含可识别的历史渠道与云端恢复记录；只修改阅读投影，Kiro正价／Pro1／Pro2／Pro3／Gemini 等原始渠道和费率保留于调用账本／回答信息。模型名采用本次返回或冻结事实，缺返回证据仍显示未验证，不用当前选择覆盖旧回答。

- 2026-10-10 前一轮分组复测（下节新复核已补充边界）：正价 Sonnet 的 server_tool_use 后返回空结果且无 citation；当时 Opus 返回 client tool_use。两者继续使用同平台 Sol 检索，不能把搜索事件当完成证据。GPT Sol／Astra 已有原生检索证据；Astra 简单问题 low、复杂问题 medium，原模型生成回答。

- 普通回答的协议：RC Claude 的 Opus／Sonnet 使用同一平台 `/v1/messages` 原生 SSE；原模型仍为回答接收方，Opus medium／Sonnet high 映射到 `output_config.effort`。完整 system、用户材料及断点续写上下文保留，不因协议变化删掉系统指令。Gemini及GPT普通回答继续Chat Completions SSE；GPT原生联网和辅助Sol检索继续Responses SSE。Claude／Gemini开启联网仍走已批准的Sol辅助检索，再由原模型回答，不把Messages普通流式能力当作原生搜索已启用。
- Messages必须收到end_turn及message_stop，Responses必须收到明确的完成事件；单独DONE、自然EOF、incomplete／failed均不能把半截内容标成完整回答。协议终态结束即可收口，不继续等HTTP连接关闭；Chat的finish_reason之后仍接收尾部用量，再以DONE结束。错误和截断保留已生成正文，手动重试使用原模型与原文件，不自动换协议或重发UNKNOWN。未启用的Relay Claude原生搜索探测保留独立JSON合同和来源门禁。
- 双端设置“测试连接”与普通RC Claude使用同一Messages入口，测试仅发送固定hi／1 token，保持原凭据分组。Mac辅助检索取得本次已披露问题、必要历史用户问题及当前附件的有界公共核验要点；材料投影数组中首块为问题，后续文档文字按上节提取，原件／文件名／媒体二进制不进入辅助检索。两端格式与转换边界见[附件合同](P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md)。官方协议格式参考[Messages流式](https://platform.claude.com/docs/en/build-with-claude/streaming)及[effort](https://platform.claude.com/docs/en/build-with-claude/effort)，平台实际可用性以本轮真实调用记录为准。

- OpenRouter Claude 使用 `openrouter:web_search` 的默认 auto 引擎，支持的 Claude 由渠道执行原生搜索，不调用应用的 Sol 辅助模型；[OpenRouter 当前工具文档](https://openrouter.ai/docs/guides/features/server-tools/web-search)说明原生能力取决于具体 endpoint。Anthropic 本身具备[原生搜索工具](https://docs.anthropic.com/zh-CN/docs/agents-and-tools/tool-use/web-search-tool)，不能据此证明 RelayCore/Kiro 渠道完整支持；现有渠道红灯未解除前保持上述已验收辅助路线，不进行猜测性协议切换。

## 历史2026-10-10 原生搜索复核裁决（不恢复Sol助手）

- 用户要求先判定实际路由再处理备注。RelayCore Opus直接Messages曾得到完整搜索来源，证明不能断言“无法自身联网”；进一步带应用system字段及普通对话Adapter复验反复缺少有效公开来源，另有504，不构成稳定普通聊天验收。Sonnet两种工具版本返回空搜索结果；Gemini两种原生工具参数写法均缺少grounding来源。逐项动态结果和费用只写当前交接。
- 当时普通聊天的联网检索继续走已批准的Sol辅助路线，不把Messages普通回答能力当作原生搜索已启用。辅助路线只接收当前已披露的原问题、必要历史用户文字及当前附件核验要点，范围遵循上节。将来只有原生普通调用完整通过才加入验证名单；不能删除system指令以制造通过，不改变风格、上下文或记忆的角色边界。
- 内部Claude Messages适配保留严格来源与完成校验，空搜索事件或非HTTP(S)占位来源不成立。原生探测不会自动转发到Sol或重发UNKNOWN。主流程保留异常收口和显式恢复能力，不能把上游联网失败当成本机崩溃。

## Sonnet响应身份别名（2026-10-22）

同一已配置Claude服务的正式`/v1/models`目录同时发布`claude-sonnet-5-5`与`claude-sonnet-5.5`；本轮真实四附件请求前者，返回后者，正文及终态正常。仅为该Provider增加这对精确响应身份等价，规范到原请求ID`claude-sonnet-5-5`。不改wire请求、预设、渠道、倍率、原始返回值和历史账本，不扩大到其他Provider、其他Claude版本或Opus/Sonnet之间。原身份误报由既有真实回执离线回放校验，不重复付费。证据见[真实附件验收](verification/2026-10-21-real-attachment-roundtrip/REPORT.md)。
