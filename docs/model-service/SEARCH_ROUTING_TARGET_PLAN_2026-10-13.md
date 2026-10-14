# 搜索路由最终整理与实施交接（2026-10-13）

> **2026-10-13 最新状态：用户已明确授权按最终方案实施及公开真实服务验收，模拟器可以直接操作；“不要安装”仅限制真机。** 双端新路由、Luna来源映射与精确接收方授权已实现。逐模型、格式、重开和失败证据见[实施验收报告](../verification/2026-10-13-luna-routing-implementation/REPORT.md)，不能把实现当作全部验收通过。下文选型证据与待实施措辞是13:17方案快照，当前授权与结果以本段、C12及最新状态为准。

## 1. 结论与决策边界

- 用户已确定：OpenRouter 和官方直连能正常工作的自身联网路径恢复并保留，不得再全局换成智谱或其他助手。国内候选只走官方直连；国外搜索助手走 OpenRouter，不要求 OpenAI 官方 Key。
- 本批实测推荐：搜索助手采用 **GPT-6 Luna / OpenRouter / openai/gpt-6-luna**，实际渠道固定 OpenAI，禁止静默 provider fallback。Haiku 没有显示足以抵偿额外费用的收益；Qwen3.8 Flash 官方北京为低价备选评测结果，不能静默启用。
- RC 普通文字：**GPT-6.1 Sol、GPT-6 Astra 保留自身搜索**；Sonnet、Opus、Gemini Flash、Gemini Pro 推荐 Luna 助手。不能再写成“全部 RC 无法自身联网”。
- RC 单／多附件：推荐六款统一以 Luna 检索完整材料中的公开核验事项，再交回原选 RC 模型生成。普通文字通过不证明附件复杂链路已经通过；Sol/Astra 附件自身路径若未来希望保留，须另有同范围完整证据后再调整，不凭本次文字测试放行。
- 以上 RC 附件方案和 Luna 接入均是**待实施目标**，不是用户已经看到的应用行为，也不是“全部模型验收完成”。原选回答模型、Provider、Key 分组和能力配置不得被助手替换。
- 智谱自身 GLM 联网与 GLM-OCR 是独立能力；撤销越界的全局搜索接管不等于删除这些功能、Key 或历史用量。

## 2. 当前 19 模型逐项目标

清单来自当前 `protocol/model-service/model-service-v1.json`，不是外部厂商最新目录。自身搜索指该部署渠道现有工具路径，不把同品牌其他渠道的能力搬过来。未要求联网的本地材料任务不因存在附件就一律强制外发搜索。

| 渠道 / Provider ID | 当前显示模型 | API ID | 普通文字联网目标 | 附件联网目标 |
|---|---|---|---|---|
| OPENROUTER | Claude Opus 5.5 | `anthropic/claude-opus-5.5` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| OPENROUTER | Claude Sonnet 5.5 | `anthropic/claude-sonnet-5.5` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| OPENROUTER | Claude Haiku 5.5 | `anthropic/claude-haiku-5.5` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| OPENROUTER | GPT-6.1 Sol | `openai/gpt-6.1-sol` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| OPENROUTER | GPT-6 Luna | `openai/gpt-6-luna` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| OPENROUTER | Gemini 3.8 Flash | `google/gemini-3.8-flash` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| DEEPSEEK | DeepSeek V4 Pro | `deepseek-v4-pro` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| DEEPSEEK | DeepSeek V4.1 Flash | `deepseek-flash` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| ZHIPU | GLM-5.3 | `glm-5.3` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| ZHIPU | GLM-5.3 Flash | `glm-5.3-flash` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| QWEN | Qwen3.7-Plus | `qwen3.7-plus` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| QWEN | Qwen3.8-Max | `qwen3.8-max` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |
| RELAYCORE_CLAUDE | Opus 5.5 · Kiro正价 | `claude-opus-5.5` | Luna 助手 | Luna 助手（目标建议，完整链路待验） |
| RELAYCORE_CLAUDE | Sonnet 5.5 · Kiro正价 | `claude-sonnet-5-5` | Luna 助手 | Luna 助手（目标建议，完整链路待验） |
| RELAYCORE_GPT | GPT-6.1 Sol · Pro3 | `gpt-6.1-sol` | 自身搜索；补来源回传 | Luna 助手（目标建议，完整链路待验） |
| RELAYCORE_GPT | GPT-6 Astra · Pro3 | `gpt-6-astra` | 自身搜索；补来源回传 | Luna 助手（目标建议，完整链路待验） |
| RELAYCORE_GEMINI | Gemini 3.8 Flash · Gemini | `gemini-3.8-flash` | Luna 助手 | Luna 助手（目标建议，完整链路待验） |
| RELAYCORE_GEMINI | Gemini 3.1 Pro · Gemini | `gemini-3.1-pro` | Luna 助手 | Luna 助手（目标建议，完整链路待验） |
| QWEN | Qwen3.6 Flash | `qwen3.6-flash` | 恢复／保留本渠道自身搜索 | 恢复／保留本渠道自身搜索；完整附件事项规划 |

Qwen3.6 Flash 是现有 Auto 专用候选，也必须纳入路由/回归；本次比较的 Qwen3.8 Flash 是另一个官方候选，不得偷换型号或擅自新增到正式菜单。GLM-OCR 不是第 20 个聊天模型，不用其成功替代图片／扫描件完整流程验收。

## 3. 本次实际证据与推荐原因

### RC 普通文字：24 主请求 + 4 Sol 参数对照

[完整报告](../verification/2026-10-13-search-routing-plan/rc-text/REPORT.md)，[逐项汇总](../verification/2026-10-13-search-routing-plan/rc-text/evaluation.json)。六模型、Android/Mac、简单题和复杂九事项各一轮基线；不是长期成功率统计。

| RC 模型 | Android 简单 / 复杂 | Mac 简单 / 复杂 | 判定 |
|---|---|---|---|
| Sol | 完整回答 / 完整回答，部分缺结构化来源 | 完整回答 / 完整回答，部分缺结构化来源 | 补来源返回参数后四组全部完成，保留自身 |
| Astra | 通过 / 通过 | 通过 / 通过 | 保留自身 |
| Sonnet | 多语言摘要拼接 / 单一长查询无相关结果 | 同类失败 | 不以工具返回了来源作为回答成功，推荐助手 |
| Opus | 通过 / HTTP 504 | 有来源但正文空 / HTTP 504 | 不稳定，推荐助手 |
| Gemini Flash | 空正文 / 空正文 | 无真实搜索证据 / 空正文 | 推荐助手 |
| Gemini Pro | 无真实搜索证据 / 无真实搜索证据 | HTTP 404 / 无真实搜索证据 | 推荐助手 |

Sol 基线都有真实 web_search 完成事件；临时探针增加 `include=["web_search_call.action.sources"]` 后，两端简单/复杂共四组来源、身份与回答齐全，九事项无遗漏。**生产代码未加此参数。** Astra 四组均有真实来源与完整回答。Gemini 未回传实际 modelVersion，身份未验证，不以请求 model 补造。504 费用未知，不按零处理。协议 `nativeSearchPass` 不是业务成功，例如 Sonnet 简单题通过协议门槛却没有完成问题。

### 助手三款同批多附件实测

[完整比较](../verification/2026-10-13-search-routing-plan/assistant-comparison/REPORT.md)。三个公开文件 MD/DOCX/TXT、九个新闻/政策/财报事项、两端实际请求；候选模型不知道标准答案，第二轮倒序验证尾部覆盖。

| 候选 | 最终完整轮 Android / Mac 耗时 | 相应每次费用 | 主要判断 |
|---|---|---|---|
| Luna / OpenRouter | 42.2 / 41.8 秒 | $0.05538 / $0.04488，服务回执 | 官方来源倾向更好，默认推荐；其他轮仍有日期误判或多余利率解释，不是零错误 |
| Haiku 5.5 / OpenRouter | 41.7 / 29.4 秒 | $0.10249 / $0.10086，服务回执 | 8192 输出额度曾耗尽；16384 完整轮仍有政策核验问题，不推荐默认 |
| Qwen3.8 Flash / 官方北京 | 181.1 / 119.9 秒 | 估算 ¥0.07938 / ¥0.05750 | 成本较低但慢，日期与 JSON 问题；只作备选，不自动替换 |

这是 2026-10-13 的小样本费用回执／估算，不是固定报价、当前实时价格保证或全账号实扣账单。OpenRouter 报告费用包含本次搜索费，不再重复加；后续原 RC 模型生成另计。Qwen 未查实扣，旧参数或优惠变化需重核。历史 UNKNOWN 保留，不能提供包含未知请求的精确总价。

本轮普通文字又做 Luna 四组对照：简单 10.9–12.6 秒／每次 $0.01112–0.02194，复杂 36.2–43.7 秒／每次 $0.04566–0.07738；四次回执合计 $0.15610188。均有服务端搜索计数和完整回答，但 message.annotations 为空，逐条原生来源映射未闭合。

### 双端证据边界

Android：emulator-5592，临时 DEX 在 shell UID 的 app_process 运行，调用既有隔离 APK 的实际文档提取器／OfficialProviderChatTransport；真实 HTTPS。不是正式应用 UID、UI、AppContainer／Room 验收。Mac：临时 Rust 原生客户端，复用原凭据 owner 与 DOCX 提取器，reqwest 发真实请求；不是完整生产 transport/UI。原生 picker、长附件、图片/扫描件/OCR/音视频和后台生命周期本轮未验收。**助手单测成功不等于 Luna→RC 最终回答完整业务链通过。**

## 4. 实施结构与所有权（待后续授权）

保留现有分层，不新造第二套运行时模型注册表。统一的是路由语义、任务/来源完整度与验收，接口参数继续按实际渠道/协议适配。

1. **路由决策**：以 Provider + 模型部署 + 普通文字/附件 + 用户联网意图为输入，冻结本次路径；取消“选了独立搜索就覆盖所有模型”的广域优先级。OpenRouter/官方直连优先恢复自己的路径，RC 按本表选择。Auto 先选答案模型再按其路径，不借 Auto 掩盖失败换模型。
2. **完整材料与事项规划**：遍历本次全部附件、所有事项及尾部；以语义和文件身份分段，保留日期数字与单位；不能拿“分析报告”三个字检索，也不能只搜前几项、截断后宣称全部完成。保留项目已有容量与48次/600秒预算定义；不要为通过测试无界扩大或机械降低上下文。
3. **搜索执行**：Luna 经 OpenRouter 当前原生搜索工具执行，返回逐事项可追溯结果。复用现有适配器、取消/超时/限额/流式解码 owner；不要改为 OpenAI 官方接口或 Exa 等未批准第三方。检索源必须独立于模型自行编造的正文 URL。
4. **证据交接与原模型生成**：助手仅检索与整理证据；原选 RC 模型拿到完整材料和明确已核实/未核实项，再生成最终中文回答。必要英文专名保留，不直接拼贴大段英文搜索摘要。来源不足不伪装成功，也不以搜索失败删除已经生成的合法材料分析。
5. **授权与持久化**：发送前简洁披露实际搜索接收方、公开核验材料范围及可能费用；不新增无必要弹窗/常驻按键。旧智谱或 RC Sol 授权不能自动授权 OpenRouter Luna；历史 v2/v3 必须兼容读取，当前发送/重试按精确接收方与材料重新校验。版本是否升级由结构差异决定，不机械升号。
6. **失败／重试／计费**：UNKNOWN 不自动重发；取消、本地保存失败或预算耗尽不继续下一阶段。没有来源、部分覆盖、正文为空分别如实收尾。保留历史失败、原回答身份与每次真实调用费用，消息汇总与逐调用账本口径有明确定义；用户已取消的金额/时间显示疑问不另立改造。
7. **UI与后台**：复用现有状态展示；生成灵动岛仅后台显示，前台隐藏，默认简洁，中断/完成收尾；点击详细版顶部或空白处收简洁，无多余收起按钮。这些是既有用户要求，不能借本次路由改造重新设计或宣称本轮已验。

## 5. 合同与代码落点

- 当前行为仍由 `docs/C12_MODEL_NETWORK_PARITY_CONTRACT.md` 定义；本计划不能提前改写它为“已实施”。未来实现同步 C12、`RELAYCORE_MODEL_CURRENT_CONTRACT.md`、`P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md`，各正文只拥有自己领域。
- 模型架构/维护：`docs/model-service/MODEL_SERVICE_ARCHITECTURE.md`、`MODEL_SERVICE_MATRIX.md`、`MODEL_SERVICE_MAINTENANCE.md`；维护目录 `protocol/model-service/model-service-v1.json` 的来源与例外同步，但不是新增运行时路由数据库。
- 附件合同 `P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md`；历史读兼容、授权与失败恢复由各现有 owner 负责。功能审阅沿设置既有入口更新，不扩大常驻 UI。
- Android 首查 `AutomaticWebSearchPolicy.kt`、`NormalChatOpenRouterExecutor.kt`、Provider adapter / request options / credential / persistence owner。Mac 首查 `desktop/src-tauri/src/lib.rs` 的搜索路由组合根及独立 coordinator/adapter。按符号定位，不能全文读取超大文件。
- 索引 `docs/contracts/CONTRACT_INDEX.md`；模型门 `protocol/scripts/verify-model-service-contract.py`；合同门从现有项目测试脚本定位，禁止新增重复 authority。

## 6. 冲突裁决

| 旧说法／来源 | 新证据与裁决 | 本次处理 |
|---|---|---|
| 智谱独立搜索全局接管，旧状态写“目标完成” | 是旧已安装实现；用户明确拒绝继续覆盖正常路径 | 更新当前状态与交接，旧报告保留；业务合同尚未修改 |
| 所有 RC 均不能自身搜索 | 最新普通文字 Sol/Astra 通过；普通文字与附件分场景 | 以本表为后续目标，撤销笼统结论 |
| 19模型曾全部真实通过 | 当时是智谱服务+原模型生成、特定公开 fixture | 不能证明新 Luna 路由或自身搜索已验收 |
| 有正文 URL／搜索计数就达到来源合同 | 只能分别证明 URL 输出／发生搜索 | Luna annotations 缺口必须解决；不得削弱来源门槛凑通过 |
| 前次覆盖授权仍可用于本轮 | 最新已授权实施、公开验收和模拟器操作 | 正式真机仍不安装 |

## 7. 后续验收顺序与完成门槛

先补最小有决定性的缺口，再扩大矩阵，避免反复付费空转；不重新选一轮助手。

1. 实施前先核对当前工作树、授权范围和现有路由。Luna 来源映射必须做一组真实最小验证：工具是否确实执行、具体事项与可验证来源如何绑定、缺来源如何收尾。不得用模型自填 URL 顶替来源凭证；如果当前渠道不能回传所需证据，应先明确此阻塞与最小可行方案，不能静默改服务。
2. 授权实施后，先完成两端一条生产调用链：公开单附件/多附件→Luna 搜索→原 RC 模型完整回答→来源及费用持久化→重开；Sol 普通文字补 include 参数并复测。新源与旧历史分开，无改写旧授权、旧回答或费用。
3. RC 六款都做最终生产链真实验证；OpenRouter 六款、官方直连七款验证恢复自身工具后没有再调用助手。普通文字简单/复杂、单/多/混合附件正交覆盖。按全19名单列证据，不漏 Auto 专用 Qwen3.6 Flash；OCR 单列。14已支持格式按真实读取器分组、代表模型覆盖，不做19×全部格式无意义重复收费；图片、扫描PDF、音视频如尚未支持须明确提示，不假装读入成功。
4. 覆盖失败/来源空/部分事项/空正文/504/取消/UNKNOWN/费用未知/重开/旧v2-v3历史/显式重试，以及模型身份、材料尾部、精确接收方和预算。合同门、两端相关测试与构建通过后，再按合法隔离方式进行原生业务/UI验收；不用 shell 探针冒充应用 UID。
5. 报告分开列离线、构建、隔离原生、真实服务、正式设备和安装结果。全部请求有终态、非空回答、搜索证明、逐项覆盖且失败真实表达才算对应场景通过。没有完成的写 NOT RUN/UNKNOWN，不能以历史跨版本绿灯替代当前失败。

**禁止：** 任何 connected*AndroidTest；主 OPPO 的 Debug/仪器测试/自动部署/卸载/清数据；未授权私人附件重放；Key进入文件/argv/env/log；静默换答案模型/搜索供应商；自动重发UNKNOWN；reset/clean/整仓stash；提交/推送/真机安装。模拟器操作与本轮公开服务验收已获授权。

## 8. 接手入口与保全

仓库 `/Users/nanzhufeng/Documents/工具开发/Nanfeng_AI`；main，底层 HEAD `dafc64d9b33661dc4f792fd1434ff1ddca9cdfa3`，文档写入前433个dirty路径/156未跟踪，HEAD不是完整WIP快照。双端1.0.29覆盖是08:50旧阶段记录，本次未再次检查设备，未覆盖。

先读 `AGENTS.md`、`docs/PROJECT_STATUS.md`、`docs/CURRENT_HANDOFF.md` 最新入口及本文件；不要全文加载391KB历史交接。当前公开实测证据已从/tmp复制到项目 `docs/verification/2026-10-13-search-routing-plan/{assistant-comparison,rc-text}`。完整临时运行器仍在原/tmp目录，可能被系统清理；关键规范化回执和报告已保全，未复制密钥或原始服务payload。复测前需重新核验现时服务配置与凭据状态，不能复用旧进程/假定它仍运行。

新聊天提示词见 `docs/handoffs/2026-10-13-search-routing-prompt.md`。本次只办理文档交接，不新建或发消息到其他聊天，不修改长期记忆。
