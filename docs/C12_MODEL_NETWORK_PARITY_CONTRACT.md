# C-12 模型与联网／调用记录跨端合同

<!-- contract-governance:start -->
> **合同口径：现行领域合同。** 领域入口：[ 模型协议与联网 ](C12_MODEL_NETWORK_PARITY_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** 普通发送按渠道/材料选择工具、来源与范围终态；旧独立搜索服务只读兼容
<!-- contract-governance:end -->

日期：2026-09-03
状态：当前普通发送与联网语义；平台／模型维护入口见下方总架构，历史验收保留日期边界。

模型服务的规则层级、平台差异与新增／升级入口统一见[模型服务架构](model-service/MODEL_SERVICE_ARCHITECTURE.md)；当前配置由[维护矩阵](model-service/MODEL_SERVICE_MATRIX.md)与离线门禁核对，不从本合同的历史平台数反推当前目录。

回答语言与外语来源译述统一遵循 [普通回答语言合同](ANSWER_LANGUAGE_CURRENT_CONTRACT.md)，独立于来源原文语言、联网路线和模型选择。

## 1. 当前事实与范围

- 联网以全局设置与会话override解析后的开关为准：开启要求实际搜索，关闭不发起搜索。不得按是否出现“核验”等关键词限制开启的联网，也不得把仅提供可选工具当作已经联网；已执行但证据不足、实际执行失败、未提供执行证明分别保留，不能编造来源或静默改离线。

- OpenRouter Muse的纯文字与本地完整可读文件使用同模型／渠道／Key的Responses原生搜索，媒体仍保留既有Chat Completions路径，不推断媒体已验收。该上游拒绝required与指定工具名，不能照搬Luna参数；原生来源、终态和计量由实际回执裁决。OpenRouter Gemini 3.8 Flash与Gemini 4 Argon开启自身联网时，在原Chat Completions请求中使用web plugin（engine=native）启动原生grounding，并保留搜索指令；不同时发送可被模型跳过的server tool，也不发送不兼容required。文字、多可读文件及媒体共用此序列化规则，禁用／grounded回答不注入。原模型／渠道／Key、附件字节与真实来源成功门保持；plugin是针对这两个精确部署的兼容例外，官方已标记deprecated，后续迁移须先验证同题真实来源，不能仅按推荐接口名回退到可选搜索。
- Haiku 5.5联网开启采用同一OpenRouter Chat Completions请求中的web plugin（engine=exa）在请求入口检索，再由原Haiku作答，不新增Luna／第二回答模型或其他API Key。原因：可选server tool、required及native plugin组合在订单图实测仍会跳过搜索；一次native有来源不能证明开关稳定生效。此路径的结构化URL是OpenRouter检索注入来源，不标成Haiku原生工具事件。关闭／grounded不注入；其他模型已正常的原生、Responses及RC辅助路线不套Haiku参数。
- 所有支持原生媒体的OpenRouter请求，在没有本地可读公共事实计划时保留全部原件与开启的搜索，由原模型一次处理；不先强制提取inventory，不因空公共计划报QUERY_EMPTY或转离线。存在可读公共事实计划的单／多／混合附件仍逐项核验。双端任何模型未提供实际执行证明时保留收到正文、费用及失败状态，不能先将Attempt写COMPLETED再只改变页脚；已证明执行但证据不足不编造来源。关闭时没有这一搜索完成要求。真实服务／设备覆盖见当前交接，不从共用规则推断全部模型和媒体实测通过。
- Chat流式finish_reason=error必须保持失败，尾部usage仍保留；[DONE]不能把服务错误转为成功。Mac完成但空正文保留已返回计量，cost-only已知失败也进入本地重开恢复，未返回金额不填零。 Mac Chat的length／max_tokens／content_filter／sensitive终态同样保留正文及已报告计量；流式等待末尾usage帧再结算，JSON保留已有partial事实，仍失败／联网未核实。已提交但未结账的上述失败可在重开时本地恢复，不新增请求；RC无验证币种的usage.cost不当USD。

- Android 可见与行为事实只读取 `ModelSettingsUi.kt`、`ConversationCostLedgerUi.kt`、`AutomaticWebSearchPolicy.kt` 和 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)。本合同不根据 Provider 品牌或模型常识推测能力。
- 二级页固定为四行连接状态、实时网页搜索、独立重点卡“模型设置”和组合卡“调用记录”；调用记录顺序为费用与用量、上下文记录、运行诊断。
- 平台入口与Provider凭据配置身份分离：OpenRouter、RelayCore、DeepSeek、智谱、Qwen五个平台入口；RelayCore的Claude／GPT／Gemini为独立Provider配置与Key分组。当前身份／端点／默认项看维护矩阵，不在这里维护第二份目录。API Key只保存在各平台私有凭据owner，不进入设置投影、调用记录、诊断、截图或文档。
- 本增量只处理 C-12。Composer 的会话级开关继续由 C-07 override owner 持有；全局开关与会话 override 在请求构建时统一解析，不新建第二套会话状态。

- Android 辅助检索失败也必须进入运行诊断，归属实际检索接收方／模型（Luna辅助调用归OpenRouter，RC GPT自身调用归其GPT配置分组），保留安全错误码、HTTP 状态和耗时；不能归为原回答模型失败或只返回通用错误。禁止保留原始检索 body、材料或凭据。Android HTTP 408／504、TIMEOUT 对应超时，NETWORK 对应网络；UNKNOWN 仍不自动重发。

## 2. 联网语义与来源门禁

- 全局实时网页搜索开启后，每次普通对话都请求当前 Provider 已实现的网页检索，不用关键词猜测是否“需要当前信息”。关闭后不请求网页工具。
- 带附件的普通对话保持显式联网状态；文本接口先通过本机正文投影，支持原生多模态的OpenRouter搜索接口保留模型已声明及Adapter实际支持的原媒体。单／多／混合附件和转换失败边界由[附件合同](P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md)持有，不将“文本投影”套到所有接口。主请求须针对材料具体事件／日期／数字核验，公开链接打不开时用实体关键词寻找原始来源，明确已核实／矛盾／仍未核实，不把记忆当实时证据或把核验工作推回用户。不得因存在附件而静默关闭 DeepSeek 或智谱的网页检索。
- Android 与 Desktop 的 DeepSeek 联网统一使用同服务商 `https://api.deepseek.com/anthropic/v1/messages`，保留所选模型；原生工具为 `web_search_20250305`，`tool_choice` 强制 `web_search`；单请求搜索预算依据当前附件核验事项数在5–12内确定。普通离线聊天仍用 Chat Completions。DeepSeek Responses 会忽略内置 web_search，禁止将它用作联网接口。
- 千问普通联网统一使用Responses原生web_search及结构化来源。Qwen3.8-Max的Responses显式enable_thinking=true、reasoning.effort=low，上限16384；离线Chat字段为max_completion_tokens与low effort，不在跨Provider共用envelope复制。其他Qwen预设保持自己的profile与已实现字段；没有本接口媒体投影时不直接发送媒体。
- 智谱显式联网统一在web_search中设置`search_intent="false"`与`require_search=true`，跳过可不搜索的意图分支，搜索结果必须返回；附件的显式分批事项查询使用同接口官方支持的`search_engine="search_pro"`，普通非分批搜索保留`search_std`；选择在首次发送前完成，不是在审核拒绝后自动换引擎重发。同Provider/Key/原GLM模型及协议不变，搜索工具费用不从Token用量猜为零或当作已结算，按已有账本的实际报告/本机估算等级分别保留；有附件时每批search_query使用本次完整计划中的对应事项原文，不重新解析包裹文本；内部检索批次按[官方联网搜索工具](https://docs.bigmodel.cn/cn/guide/tools/web-search)的search_prompt把原生搜索资料交给该GLM生成核验结果，普通提问不注入逐项格式。所有批次遍历本次所有附件要点，排除文件名及续写控制指令，结果条数至少10。保留原GLM模型与thinking=enabled；仅内部显式附件逐项检索（explicit query且尚未进入grounded最终回答）使用reasoning_effort=low，普通聊天／非分批联网／原选模型最终回答仍保留max。阶段来自执行owner，不能由用户正文触发；流式与非流式一致，不在失败后自动降低effort重发。不以记忆直接生成替代联网。Mac GLM5.3／Flash普通输出预算为32768，避免max推理占满旧8192而无正文；Android保留既有profile能力上限。此上限不是实际用量，截断仍保留失败及部分正文，不自动续发。字段依据[官方对话补全](https://docs.bigmodel.cn/api-reference/模型-api/对话补全)。
- 显式开启联网的请求，只有服务端结构化来源或成功的搜索执行结果才能完成；模型正文自行写出的 URL、请求开关、排队／失败工具状态都不是证据。DeepSeek Messages 要求至少一笔原生检索具有有效来源且`stop_reason=end_turn`；流式还须收到完整`message_stop`，非流式须完整解析JSON封包。两条解析都只展示最后搜索结果之后的正文。单次检索无结果或`web_search_tool_result_error`（包括`max_uses_exceeded`）是工具执行结果，不是整条回答的失败终态：双端保留已取得来源并继续读取后续正文、用量及真实终态；全程没有有效来源、Provider全局错误、缺终态、`pause_turn`或`max_tokens`仍必须失败／未完成。不得因局部工具错误把正常完整回答与已知计量降为失败或未知；也不得依据正文看似完整为旧失败记录伪造终态。
- DeepSeek附件分批的Messages流与非流式解析一致：中间搜索前／搜索间文字不作为最终事实摘要；保留所有真实原生来源和用量，内部检索缓冲只交接最后一次原生结果之后的正文。最终原模型材料回答保持其流式生命周期；没有终态仍不得生成成功证明。
- RC Sol/Astra普通文字自身Responses首次发送即在web_search中设置external_web_access=true、tool_choice=required与include=[web_search_call.action.sources]。system映射developer，完整纯文本块合并为一个input_text，原生非文本投影保序。本条不恢复旧Sol附件助手；当前RC附件使用下节Luna与原RC普通回答接口，失败后不改变协议/参数重发。字段出现、搜索执行、原生来源与完整终态分别验证。
- RC Sol/Astra自身Responses共用现有复杂度分类：简单文字显式`reasoning.effort=low`与`search_context_size=low`，复杂文字两项为`medium`；不再让Sol单独缺少这两项而依赖服务默认值。严格终态与原生来源门继续保留。Android对非completed状态的`response.completed`先解析已返回身份、Token与来源，再保持INCOMPLETE，不能提前丢字段，也不能将失败改成成功。本轮动态真实结果及服务/验收入口缺口见[双端联网一致性检查](verification/2026-10-13-native-search-consistency/REPORT.md)。
- RC GPT自身Responses任务的prompt_cache_key是执行身份/Provider/模型带版本前缀的SHA-256，只发送64位小写十六进制，不含正文/Key/文件名/原ID；它是有界路由元数据，不授权缓存复用或UNKNOWN重发。Luna使用OpenRouter协议，RC附件最终普通Chat Completions及Claude/Gemini回答不注入Responses专属字段。旧附件Responses批次规则仅解释对应旧调用；不保证渠道固定账号或上游成功。
- RelayCore 辅助检索与附件主分析是两个独立阶段。检索失败时，当前附件的已授权原模型仍可生成材料分析，正文与实际用量保留；整体失败状态及 `WEB_SEARCH_NO_SOURCES` 不变，所有外部主张标为未核实。检索仅接收已披露的当前附件核验要点与公开网址，不接收整份文件／文件名／系统／记忆；逐项来源覆盖遵循本合同共用规则，授权版本遵循RelayCore当前合同，不自动换模型、补造来源或重发 UNKNOWN；取消阻止后续主调用。协议入口按 [RelayCore 当前合同](RELAYCORE_MODEL_CURRENT_CONTRACT.md) 的具体模型选择，发送、重试和连接测试一致。
- HTTP、SSE 和 Android 定时监控执行同一完成要求。解码器收到完整的服务端终止事件后立即收尾；若完整终止事件已经到达但连接未关闭或缺少最后一个空行，也按该终止事件处理。JSON截断、非终止事件、读取超时且没有终止事件仍是未完成。联网失败明确保留失败状态及已有正文，不静默回退为离线成功、不自动换 Provider、不自动重发未知结果。历史无证据记录保留原始审计，不能伪造补证；显式重试重新处理原用户请求，不接成无限续写。
- 普通展示来源接受无凭据、具有host的绝对HTTP(S) URL，标题清理换行/方括号并去重。用于辅助外发与逐项绑定的公开URL由AttachmentWebSearchPlan.publicUrl及attachment_web_search_v1::public_url共同规范化：拒绝私有/本机host（含localhost子域和末尾点变体）、userinfo、凭据参数及非法端口；清除fragment、utm_*、gclid/fbclid/msclkid/_ga，保留其他语义查询参数。规范化同时用于事项、检查点与最终来源，不从展示标题推断身份。详情与历史读取遵从[运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)。
- 官方依据：[Responses 能力限制](https://api-docs.deepseek.com/guides/responses_api/)、[原生搜索说明](https://api-docs.deepseek.com/quick_start/agent_integrations/claude_code/)、[官方搜索适配实现](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/web/web-search-deepseek/src/provider.ts)。2026-09-19 实测及安装状态见当前交接；下文旧验收数字是历史阶段记录。

## 当前按渠道和材料冻结搜索路由

- 当前OpenRouter与官方直连聊天部署沿用各自既有联网接口，实际名单唯一读取[维护矩阵](model-service/MODEL_SERVICE_MATRIX.md)；包括新增Muse Wonder和Auto专用Qwen3.6 Flash。存在智谱凭据不改道。RC普通文字Sol/Astra自身Responses搜索；其他RC文字及全部RC单/多附件由OpenRouter GPT-6 Luna检索再交原RC回答。不联网的本地分析不触发助手。
- Luna检索模型固定为`OPENROUTER / openai/gpt-6-luna`，`openrouter:web_search`显式`engine=native`且首次请求`tool_choice=required`，渠道`only=[OpenAI]`、`allow_fallbacks=false`；不改为OpenAI官方直连，不启用其他候选。Luna只接收本次已披露问题及全部附件的公开核验要点；不接收原件、文件名、系统、记忆或前几条私人对话。
- 检索结果使用逐事项正文和原生行内引用，不要求JSON输出以免丢失annotations。事项只接受与服务原生结构化来源匹配的规范化URL；正文自行写出的URL不补造证据。摘要、ID、真实来源与完整终态分别校验。来源的已允许追踪参数规范化同时作用于证据正文、检查点和最终来源投影，不能在交回原模型时漏掉有效来源。
- Sol/Astra普通文字Responses发送`include=[web_search_call.action.sources]`；工具、required、身份、取消和终态规则保持原有合同。附件助手完成后，原选RC模型按自己的普通回答协议、完整材料及当前证据生成，不重复发搜索工具；回答Provider、模型和Key分组保持原值。
- 实际接收方及准确材料授权见P3J；旧Sol／智谱授权不能放行Luna。检索与回答分别记真实调用、币种和金额，OpenRouter回报USD与RC本地CNY估算分别保留，不把未知金额当0或把不同币种直接相加。来源或事项不足准确失败／未完成，合法材料分析可以保留；取消、落库失败和UNKNOWN不引发下一次自动外发。
- 本节定义已接入的路由行为，真实服务／格式／UI覆盖结果只见PROJECT_STATUS及本轮验收报告；源码接入不能推导全部模型已通过。

## 历史独立网页搜索服务（只读兼容）

以下为旧智谱独立服务实现与历史证据解释；新发送／显式重试不选择该全局覆盖路径，收到该旧服务授权时拒绝并要求当前接收方披露。保留GLM自身联网、GLM-OCR、原Key配置、旧来源和用量，不重写旧记录。


- 已有智谱本机凭据时，开启联网且本次有附件的正常发送可在发送前披露 `ZHIPU_WEB_SEARCH / search_pro` 独立搜索服务。搜索服务不是聊天模型，单独记录 serviceId／engineId；原选19聊天模型保持其Provider、API ID、Key分组、实际返回身份及原协议。原选模型的最终grounded回答撤去搜索工具；已取得独立服务证据的RC GPT最终回答使用本平台既有Chat Completions文本协议，不继续走工具Responses入口，作为首次规划的回答阶段而非失败fallback；发送端点、流读取模式、解码、时限及请求体共用该回答阶段的options，不能封装与transport分叉。非附件聊天及不联网的附件分析沿用原路径。不将智谱Key变成全部聊天的前提，不因智谱聊天候选被隐藏而禁用该已披露服务。
- 新服务只接受本次v3材料授权；旧Sol或旧版本grant不能扩大接收方。接收的是全部附件的公共核验要点及必要公开查询，不含文件名、原件、系统或记忆。Android授权指纹包含独立service／engine；Mac独立receipt绑定本次文字与不可变私有附件ID，实际读取仍核验材料hash。正常Send与用户显式重试分别形成当前材料的授权；后台恢复及UNKNOWN不自动重发，失败不静默切服务。无Key时保留原模型既有检索能力；已披露服务发送时失去凭据则准确失败。
- RelayCore Gemini 的检索后 `grounded_answer` 普通回答不发送 legacy `max_tokens`，沿用该Provider默认输出配置并与Android现行无显式上限profile一致；同端点／Key／模型，非失败后恢复。公开同材料对照中8192／32768均合法SSE但零可见正文，省略该字段才返回完整原模型回答。其余Provider预算及无附件／不联网普通路径保持。合法终态与Token报告不证明有可见回答；空正文明确 `EMPTY_COMPLETION`，不归JSON解析失败，不用thought／Token伪造正文。
- 官方接口为 `POST https://open.bigmodel.cn/api/paas/v4/web_search`，Bearer只由既有私有credential owner读取。固定 `search_pro`、`search_intent=false`、`noLimit`、`count=10`、`content_size=high`。查询≤70码点；长事项按标点／语义连接边界拆分，明确承接句沿用最近主体，新中文／英文实体独立；保护数字内逗号，完整预规划，不能机械截掉尾部或把无主题片段当完整查询。无法形成合法查询明确未覆盖。预先计算全部物理请求，超过48次不先发一半；调度总600秒。每次调用有独立ID，取消／本地落库失败阻止下一阶段；UNKNOWN不补发，空结果如实未核实。
- 证明类型为 **ProviderSearchApi**，不能冒充原回答模型的NativeModelTool。接受本接口原生结构化link／content，不从生成正文补造来源；检索执行完成、事项覆盖、事实支持和原回答完整终态分别判断。逐项保留本地附件序号／文件内事项序号，来源附于对应事项。证据摘要跨该事项的最多3个来源共500码点，以事项中的实体、日期、数字定位，不写特定fixture的关键词规则；公开来源规范化与身份沿用本合同。原生结果为空也只证明查询执行，不能写已核实。
- 搜索服务不报告LLM模型或Token；账本使用serviceId／engineId与空模型字段，原回答另记真实模型与用量。已完成调用费用未报告仍未知，不写0；权限、余额或全局服务错误停后续查询。最终回答失败不丢已持久覆盖及搜索调用。官方标价与实收费用分开，核验日期及动态实测见交接。
- 官方来源：[API结构](https://docs.bigmodel.cn/api-reference/工具-api/网络搜索.md)、[服务与价格](https://docs.bigmodel.cn/cn/guide/tools/web-search)。实现owner：Android IndependentWebSearchClient／IndependentAttachmentWebSearch／NormalChatOpenRouterExecutor；Mac independent_web_search_v1及普通聊天持久owner。来源接口与模型协议维护分开，不新增虚构模型preset或常驻按键。

### 历史RC GPT附件Responses请求语义（2026-10-12，旧调用解释）

- RelayCore GPT Responses的普通显式联网及Sol附件辅助检索首次发送即在原生`web_search`工具中设置`external_web_access=true`，保留`tool_choice="required"`，system指令在该Responses接口映射为developer；同一消息的完整纯文本块以双换行连接为单个input_text，不丢失长尾，有原生图像等非文本块时保留其有序类型投影；原模型grounded最终回答撤去工具时也撤去此工具内字段，不在失败后自动改变参数重发。字段格式依据[官方实时联网工具](https://developers.openai.com/api/docs/guides/tools-web-search#live-internet-access)；服务实际搜索、结构化来源及逐项覆盖仍按下文独立验证，显式字段不构成成功证明。
- RelayCore GPT Responses使用同一发送任务的`prompt_cache_key`作为有界路由元数据：由任务执行身份、实际Provider和模型做带版本前缀的SHA-256，wire只带64位小写十六进制，不含Key、用户正文、文件名或原始任务ID。同任务附件所有批次与补查共用值，不同任务／模型分离；每个物理调用的Idempotency-Key、计量及终态仍独立。原选GPT最终回答可保留本任务标识但撤去工具；Claude／Gemini最终原协议不注入此Responses字段。字段不授权缓存复用或UNKNOWN重发、不更换Provider／模型／Key分组，不保证网关固定账号或上游成功。参数格式见[官方缓存文档](https://developers.openai.com/api/docs/guides/prompt-caching)；渠道路由依据是[Sub2API公开实现](https://github.com/Wei-Shaw/sub2api/blob/9591136d45cef26453348ed9532a7ebbce7cf99c/backend/internal/service/openai_gateway_scheduling.go)，不据此断言RelayCore运行该版本；具体控制与来源覆盖结果只写动态证据。

## 附件全范围检索与完成（当前共用规则）

- 本节唯一拥有Android与Mac所有当前聊天模型的附件核验范围、分批／补查和任务完成语义；RelayCore合同仅拥有该渠道的接收方／接口／别名差异，附件合同拥有材料解析与能力边界。
- 编号Markdown多主题材料按完整事实句／分号边界形成独立核验项；标题与最多96码点的段落上下文仅用于定位，每项保留完整待核验断言、日期、关联数字与稳定ID。不得按逗号、小数点或旧智谱70字符限制切碎。章节前的公开事实同样处理；识别与正文移除开头BOM，不因报告标题／问候误建事实项，不仅凭“所有数据”等前缀删除真实断言。来源公开网址绑定到前一公共段落的事实组，私密／个人行及点评／评论分隔标题阻断继承；同一行多个公开URL先分离再修复OCR空格，单URL查询中的嵌套网址保留整体。纯核验要求及不含公共事实线索的评述不单列。普通／结构化材料沿用各自既有解析，本机原件完整交给原选模型。候选提取不是穷尽事实或核实证明，查询完成不代表主张已证实。
- 计划完整遍历本次所有附件的公共候选段落与结构化记录，保留文件边界和稳定事项ID。文件间轮流排列；不以最前12项代表整份材料。编号Markdown章节按本节事实句规则形成子事项；其他普通段落按既有语义边界形成子事项，每个子事项独立保留来源额度；不按固定字数切断实体、数字或日期，不以静默截断减少范围；普通段落无需Markdown编号或一份固定新闻关键词表。
- 每文件仍有600000个码点的材料处理预算；超过预算置limited，整体不得标为全范围完成。私密／个人字段不外发到辅助接收方；原选模型的已授权原件分析不因公共范围过滤而被替换。
- 同一事件／公告的相关事实可共用一次实际搜索及来源，但每个ID仍必须独立返回结论；证据只支持同句部分断言时整项标未核实，不以一条来源证明整个章节。新版附件证据指纹使用`attachment-fact-evidence-v5`，旧`attachment-topic-evidence-v4`回执不能命中新版缓存或证明新版范围；历史回复与账本不回写，不自动重发。
- 单批最多4项；智谱search_query仅容纳一个查询，因此该渠道每批1项，同样遍历完整计划；整项任务最多48次检索调用和600秒调度时限，当前调用仍服从协议的单次／首字／闲置／总流时限。遇到已完成但遗漏／缺证的结果，每项最多再做一次独立查询；UNKNOWN及其他全局服务失败停止后续检索／补查，不自动重发不确定请求。已授权原模型的材料分析是独立阶段，仍按上文保留正文和整体失败状态；取消、授权或本地写入失败则阻止所有后续网络调用。
- 单项状态区分尚未发起、调用失败／未知、检索后证据不足、已核实、与资料矛盾。未查询文本不能通过未核实标记冒充已搜索；多项缺证要转成单项查询，才能区分实际查询和未覆盖。URL必须来自该调用的真实结构化来源；已证明执行的单事项查询可把该响应的原生引用归到唯一事项，保留服务商真实跳转URL，不以正文自写网址替换；结论的正文网址未匹配原生引用时归为证据不足，不因此升级为已核实。多事项仍要求逐项匹配，否则独立补查；主张是否成立不能仅凭URL存在，正文状态与来源同时验证。单项确实完成查询并有原生引用、但未返回逐项结论时，记录“证据不足”，不重写成已核实；无原生证明／空引用／错误事项编号／正文明确未搜索仍不通过。单项确实完成查询后仍缺证是合法结果，不能为通过门禁强行编造已核实。
- 服务正文“部分核实／部分已核实／部分属实”及“已核实（事件部分）／部分判断未核实”等带限定的标题兼容为未完全核实，不能因包含“已核实”子串升级为完整已核实。对于有原生来源的唯一查询，明确对应主题与正确编号但结论措辞不在固定枚举内时可保留为未核实；仍拒绝未搜索、无来源、无关主题和错编号，不提升为已核实。主题匹配不从URL文本推断。工具参数required、HTTP200或正文自称已搜索均不单独构成真实搜索证明。
- 来源证明由transport的结构化来源字段直接交给检索owner，不从展示尾注或正文Markdown链接反推。来源展示标题、列表与应用追加尾注（含加粗／Markdown标题形式的来源块）不占用事实摘要的500码点容量；事实正文仍受该容量约束，不扩大或截断事实来通过。检索提示使用本批次真实事项编号，不能以固定A1示例干扰后续批次。唯一物理查询可兼容服务将编号重置为A1：必须有原生来源、相同主题、查询里的点分版本全部匹配，且只有一行结果；仅绑定回原事项ID，不改变结论或来源可信度，不接受多项错编号。
- 唯一已完成查询的事实正文超过500码点时，若原生来源、事项编号与正文主题匹配，且没有明确未查询／未覆盖的表述，只保留“已执行查询但结论未核实”的固定证据记录和实际来源；不采用超长事实，不扩大容量或截断事实，多项结果不套用此兜底。补查遇到已知缺证／格式失败时不能删除之前已接受的实际查询结果；UNKNOWN仍停止后续调用并使本轮未完成，不伪造补查成功或已核实事实。
- 上下文容量按已经存在的原件、固定指令和实际证据计算；禁止用事项数乘预测检索文本预留而提前拒绝放得下的材料。检索结果到达后按原选模型的实际预算重新装配，只裁减可选历史／记忆，不裁剪原件或实际证据。未知profile沿用安全容量回退，不能套用一个通用证据字数上限。检索前真实原件超限仍无外发；检索已发出、最终回答组合超限明确区分ANSWER_CONTEXT_LIMIT，保留检索与用量，不声称整次没有外发。
- 明确的服务端内容审核拒绝（含智谱sensitive）是CONTENT_FILTER，不是空正文RESPONSE_FORMAT；停止后续检索与补查，不能用同一请求自动探测绕过。已授权原件分析仍服从独立阶段及整体失败规则，真实拒绝不得改写成成功。Responses顶层type=error或明确error对象属于STREAM_PROVIDER_ERROR：按明确失败收尾并停止后续检索／补查，不能忽略成MISSING_COMPLETION或格式缺证补查。HTTP200不改变服务端错误语义；未验证的具体服务错误原因不猜为余额或限流。缺失终态的UNKNOWN规则保持。
- 最終回答使用原选模型和原协议／端点。已执行检索阶段后，可在同一协议内撤掉搜索工具强制项，使用实际证据生成回答；这是内部阶段标记，不来自用户正文、网页、旧receipt或服务自称。没有检索证据不得借此标为联网成功。Sources／工具执行事实与scope完成是不同值，半份材料有来源也不能宣称整份核验完成。
- Android的scope完整性独立于Ready.reply的真实搜索标记；Mac的ScopedCompleted.complete独立于Completed.web_search_verified。范围不完整保存正文、真实来源与费用，持久终态为FAILED／WEB_SEARCH_SCOPE_INCOMPLETE；全无证据沿用WEB_SEARCH_NO_SOURCES。旧记录不从新规则补造覆盖；显式重试重新处理原材料，不将失败核验接成续写，进程中断保持UNKNOWN且不自行付费恢复。
- 每个检索／媒体读取／最终回答物理调用具有独立稳定编号与真实requested／returned模型、usage和费用；不能用一个RETRIEVAL编号覆盖多批。范围计数写入既有上下文审计／SQLite context_sources，不依赖最终回答成功：Mac在计划建立、批次结束及最终调用前保存覆盖；Android最终调用失败时仍将已有上下文绑定到失败消息。最终断网／取消不抹掉已完成检索和计量，未确认的回答用量／联网成功标记不得补造。范围写入失败停止后续调用；失败费用继续由原账本恢复，不新建平行业务数据库。资料解析属于原选接收方调用，不能冒充搜索执行。
- 最终流缺少有效服务端完成事件时，双端保留已有正文及实际用量，Attempt为UNKNOWN且不自动重发；Android对外错误归为RESPONSE_INCOMPLETE，不能归为格式错误或已完成。明确的服务端FAILED／INCOMPLETE终态仍保存失败，不与缺失终态混同。
- 最终回答的待计量调用仅在该阶段开始前登记，不在附件检索开始时提前登记；检索阶段被取消或本地写入失败而未进入最终阶段，不得产生一个未发送回答的未知费用项。已发调用的UNKNOWN用量仍不能当零；计量登记失败必须阻止该阶段发送。
- 同一发送任务的显式重试可复用10分钟内、同材料指纹／同接收模型／同查询及现行语义版本、具有已完成原调用账本的标准化证据。当前模型附件链使用`attachment-fact-evidence-v5`；历史独立服务只按同服务ID＋引擎及自身`independent-semantic-v2`计划版本校验，不与模型附件指纹互换；旧topic/evidence-v4不成为新版有效条件。当前／最新／实时价格等时效查询不复用。持久检查点仅存标准化证据、来源和原调用标识，不存原始Provider payload，不进入备份／同步。旧费用保留，缓存命中不伪造新搜索或新收费；历史授权版本仍可只读，新调用仍检查现行授权。
- TXT／MD／结构化文本、Office、ZIP和可读PDF先用既有本机正文转换；可原生接收的PDF保留原件并使用本机文字层制定检索范围。未能本机读取的图片、扫描PDF、音视频，仅在原选接口已支持时由同一已批准模型提取公共事项，再走共用核验链；不得偷偷新增OCR／转写接收方、送封面替原件或把读取空结果当完整。
- 共用fixture与两端真实coordinator/Adapter/transport/持久owner覆盖长文、多文件、补查、取消、UNKNOWN、计量与scope终态。逐个枚举当前维护manifest部署，不硬编码某次模型总数；相同接口不替代模型路径，离线与真实服务验收分别记录。

## 3. 调用记录投影

- “费用与用量”有记录时固定显示本机累计、四类 2×2 汇总和四段选择：会话、会话标题整理、历史资料整理、南枫转写。提醒草案与定时监控作为“其他自动任务／计划与提醒”保留，不混入四段主分类，也不得丢失。
- 完全无记录时沿用 Android 当前空态，不伪造金额、Token 或摘要。金额必须保留事实等级；未知就是未知。
- 上下文记录只显示实际选中的资料，并固定保留对话、资料数、Provider／模型、最多两项来源、本轮输入 Token 与发生时间。
- 运行诊断保留“连接失败”和“自动与工具任务”两组；主卡显示安全摘要、耗时与发生时间。Prompt、回答、原始 Provider body、Key、Authorization、URI、私有路径不进入投影。

## 4. 历史阶段验收事实（按原日期保留）

- 红灯：新增 `desktop/tests/c12-model-network-parity.test.mjs`，首次 `0/4`，分别锁定开关语义与文案、四类账本、上下文／诊断时间以及 Rust 不得按关键词或附件关闭联网。
- 绿灯：C-12 当前 `5/5`；Desktop 完整 Node `212/212`、Rust `187/187`，lint、typecheck、inventory、protocol golden、static build、主题 computed style 与 release bundle strict codesign 均通过。`cargo fmt --check` 只剩本轮开始前同一 `lib.rs` 的 3 处无关 dirty 格式差异，C-12 两处新增格式差异已手工消除，没有批量格式化覆盖用户改动。Android 七个专项类共 `27/27` 且 `:app:assembleDebug` 通过；没有运行 instrumentation 或任何 `connected*AndroidTest`。
- Browser：`1440×900` 下模型与联网、模型设置、费用与用量、上下文记录、运行诊断共五态无页面溢出，console warning／error 为 0；全局联网开关关闭后刷新仍关闭，随后恢复开启。Browser 使用固定只读投影，不读写 Desktop SQLite、不调用 Provider。
- 隔离 Tauri：合格复验明确注入 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.AKdmv5`，并以 `--diagnostic-ui-schema-acceptance` 关闭自动任务、外部访问和业务恢复。原生开关 `on → off`，退出重启仍为 `off`；SQLite 为 0 workspace、`web_search_enabled=0`、revision `2`、`integrity_check=ok`，运行期网络 socket 为 0。

## 5. 历史C12初期风险与未验收边界（2026-09-03）

- 首次原生探针只修改复制包的 `CFBundleIdentifier`，但 Tauri 数据目录仍使用编译期 identifier，进程取得了正式根 `.runtime-owner.lock`。发现后立即终止，未做 UI 操作、账号或 Provider 调用；诊断启动允许 schema migration，因此不能宣称该次对正式根绝对零写入。后续只使用源码强校验的唯一 `/tmp` root 完成合格复验。
- 本轮没有连接 Android 设备或模拟器；Android 证据为当前源码、现有 `11-model-network.png` 和 `27/27` JVM 合同。因此根页可见层级有 Android／Desktop 对照，带数据的费用／上下文／诊断 Android 同内容截图仍未新增，不把 Desktop fixture 当成 Android 原生截图。
- 未读取或保存真实 Key，未调用真实 Provider、网页、账号、通知或 OPPO。真实网页工具支持、来源格式、费用与余额必须在另一次明确授权、可能产生费用的外部验证中裁决。

## 6. 历史C12初期证据（2026-09-03）

- Browser 与原生 PNG、联系表：`~/.codex/visualizations/2026/09/03/01a0657a-fb01-7603-8db8-850d1886980e/nanfeng-ai-c12-20260903/`
- 当前开发 bundle：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`
- 主程序 SHA-256：`f270fdee37fe10666f04ed15c3fcf096a31b4e6f00091d96070e068f490244f4`；ad-hoc strict codesign 通过，不是 Developer ID／公证正式包。
