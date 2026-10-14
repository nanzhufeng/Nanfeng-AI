# Luna 路由实施与真实验收（2026-10-13，验收记录）

当前目标路由已实现，Luna原生annotations和逐事项来源交回已修复。正式Android/Mac未安装；仅更新emulator-5592上的独立验收包。不能将离线绿灯、旧智谱链或Luna探针当作本轮全部真实验收。

## 实现与权威正文

- 既有OpenRouter6／官方7保持各自工具路径；新发送不再根据智谱Key全局接管。旧独立搜索历史保留只读，旧授权不能触发新链。
- RC Sol／Astra普通文字保留自身Responses及`include=web_search_call.action.sources`；其余四款文字及RC所有附件由OpenRouter GPT-6 Luna固定OpenAI原生搜索，之后原RC回答（原模型／渠道／Key组）。Luna不接收旧私有历史，仅本次问题和公开核验线索。
- 非JSON原生引用；来源URL规范化之后绑定事项并随回答保留，正文URL不能单独提升为真实来源。覆盖与事实支持分开。
- 双端发送及显式重试绑定准确接收方、当前或原重试问题和附件；无自动供应商、答案模型或UNKNOWN重发。
- 行为唯一正文：[C12](../../C12_MODEL_NETWORK_PARITY_CONTRACT.md)；授权：[P3J](../../P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md)；RC：[现行合同](../../RELAYCORE_MODEL_CURRENT_CONTRACT.md)。功能审阅已同步，无新增常驻按键。

## 当前20模型逐项证据

| 模型预设 | Android多附件 | Mac多附件 | Android普通文字 |
|---|---|---|---|
| `CLAUDE_OPUS_5_5` | 2/2＋原回答 | 2/2＋来源/原回答 | 搜索已验证＋回答 |
| `CLAUDE_SONNET_5_5` | 2/2＋原回答 | 2/2＋来源/原回答 | 搜索已验证＋回答 |
| `CLAUDE_HAIKU_5_5` | 2/2＋原回答 | 2/2＋来源/原回答 | SentWithNotice(notice=WEB_SEARCH_NOT_VERIFIED) |
| `GPT_6_1_SOL` | 2/2＋原回答 | 2/2＋来源/原回答 | 搜索已验证＋回答 |
| `GPT_6_LUNA` | 2/2＋原回答 | 2/2＋来源/原回答 | 搜索已验证＋回答 |
| `GEMINI_3_8_FLASH` | 2/2＋原回答 | 2/2＋来源/原回答 | 搜索已验证＋回答 |
| `DEEPSEEK_V4_PRO` | 2/2＋原回答 | 2/2＋来源/原回答 | 搜索已验证＋回答 |
| `DEEPSEEK_V4_FLASH` | 2/2＋原回答 | 2/2＋来源/原回答 | Failed(code=RESPONSE_INCOMPLETE) |
| `MUSE_WONDER` | Failed(code=AUTHENTICATION) | Explicit { code: "AUTHORIZATION", http_status: Some(403) } | NOT RUN；Mac短文字控制403 |
| `GLM_5_3` | 2/2＋原回答 | 2/2＋来源/原回答 | Failed(code=RESPONSE_INCOMPLETE) |
| `GLM_5_3_FLASH` | 2/2＋原回答 | 2/2＋来源/原回答 | Failed(code=RESPONSE_INCOMPLETE) |
| `QWEN_3_7_PLUS` | 2/2＋原回答 | 2/2＋来源/原回答 | 搜索已验证＋回答 |
| `QWEN_3_8_MAX` | 2/2＋原回答 | 2/2＋来源/原回答 | 搜索已验证＋回答 |
| `RELAYCORE_OPUS_5_5` | 2/2＋原回答 | 4/4＋来源/重开 | 搜索已验证＋回答 |
| `RELAYCORE_SONNET_5_5` | 2/2＋原回答 | 2/2＋来源/重开 | 搜索已验证＋回答 |
| `RELAYCORE_GPT_6_1_SOL` | 2/2＋原回答 | 4/4＋来源/重开 | 搜索已验证＋回答 |
| `RELAYCORE_GPT_6_ASTRA` | 2/2＋原回答 | 4/4＋来源/重开 | Failed(code=NETWORK) |
| `RELAYCORE_GEMINI_3_8_FLASH` | 2/2＋原回答 | 2/2＋来源/重开 | Failed(code=WEB_SEARCH_COMPANION_FAILED) |
| `RELAYCORE_GEMINI_3_1_PRO` | Failed(code=SERVICE) | Explicit { code: "INVALID_REQUEST", http_status: Some(404) } | Failed(code=WEB_SEARCH_COMPANION_FAILED) |
| `QWEN_3_6_FLASH` | 2/2＋原回答 | 2/2＋来源/原回答 | 搜索已验证＋回答 |

## 当前真实结果

- Android实际安装独立包，应用UID10210、AppContainer／正式运输器／Room／模型设置与原生加密凭据，当前19模型双MD＋DOCX：18款2/2完成并有原回答；RC Gemini Pro原回答阶段HTTP404。详见`android-appcontainer-current19-1.results.json`及physical/cleanup。Qwen3.6 Flash按Auto专用模型实际执行。
- Android来源/用量重开完整链：`android-installed-luna-sonnet-1.results.json`使用真实Room/executor ports，Luna及Sonnet身份、USD/CNY分别记账、重开回答相同且零网络；这是应用UID原生owner链，完整AppContainer矩阵另列，不能混称UI/picker。
- Android14格式正交：Sonnet前两组4/4通过；第三组Luna4/4后原回答网络中断，第四组TLS中断，保留UNKNOWN、不重发该尝试。独立Opus新场景第三组4/4、第四组2/2通过。全部14格式现有真实成功证据，失败样本仍保留。各`.results/.physical/.cleanup.json`可定位读取类型和实际调用。
- Mac14格式：实际DesktopWorkspaceStore导入／准备发送／Luna／原回答／SQLite重开：Sol(TXT/MD/JSON/CSV)4/4；Astra(XML两MIME/YAML/YML)4/4；Opus(HTML/DOCX/XLSX/PPTX)4/4；Sonnet(PDF/ZIP)2/2。全部有真实来源、原模型身份、检查点和分币种调用账本。
- Mac Gemini Flash实际文件owner2/2及来源重开通过；原合成消息投影EMPTY_COMPLETION保留，不能据此改生产协议或删除失败。DeepSeek Pro实际文件owner2/2通过；早前Node/CMake场景1/2保留。
- Mac普通文字Astra自身Responses有24条原生来源并保持gpt-6-astra；Sol初次测试错误强行进入附件协调器，得到PARTIAL0/0，不是生产自身路由失败，修正测试分支后另一双事项问题出现INCOMPLETE_RESPONSE UNKNOWN；独立单事项简单文字控制完成、自身原生来源20条。复杂文字UNKNOWN保留，不重发。
- Mac普通13修复后真实多附件运输矩阵13/13均2/2、有原生来源和原模型回答，见`mac-normal13-final.json`。Android普通文字19矩阵12款有真实搜索证明；Haiku提示WEB_SEARCH_NOT_VERIFIED，DeepSeek Flash/GLM两款RESPONSE_INCOMPLETE，RC Astra网络UNKNOWN，RC Gemini两款Luna阶段失败。本轮附件18/19不能抹去这些文字缺口。

## 用户追加：Muse Wonder / OpenRouter

- 当前聊天部署增至20（OpenRouter7／官方7／RC6）。新增`MUSE_WONDER → meta/muse-wonder`，双端深度顺序为Opus、Sol、DeepSeek Pro、Muse Wonder、GLM、Qwen Max；默认模型和既有Auto顺序保持。
- 身份/参数来自[OpenRouter官方模型页](https://openrouter.ai/meta/muse-wonder/)及Endpoints API（`muse-endpoints.json`）：1,048,576 context；上游申明text/image/video/file/audio、tools/reasoning，metadata不等于全部实际能力验收。原生能力/费用来源已映射，健康UNKNOWN，不标为已通过。
- Mac JSON+XLSX真实owner两次物理原渠道调用403，另一个不联网短文字控制也403（`muse-access-control.json`）。没有绕过访问控制或改其他模型。Android新包实际AppContainer个案也HTTP403，结果AUTHENTICATION；双端均没有原回答。最终门禁和原生重开见下文。
- 原19矩阵是新增前这一版本的实际证据；Muse独立补验，不能把19/19或18/19称为最新20款全部通过。

## 凭据隔离与重开收口

- Mac配置门误依赖RC GPT Key已红绿修复。仅OpenRouter＋RC Claude两组原生加密凭据的新JSON/ZIP真实链完成2/2；`unusedRelayGptCredentialPresent=false`。回答、来源、物理账目重开前后完全一致，见`mac-owner-exact-credentials1.json`。临时加密副本由本轮TempDir清理，正式凭据只读。最终门禁423通过/33ignored及Mac候选重构建验签见logs/rust-closeout-final.log、mac-closeout-final-bundle.log。
- 首次全部消息JSON比较失败，是测试漏调用生产的完成记账阶段；重开恢复把pending变为measured，正文与来源均未丢失。测试补齐`complete_ordinary_chat_accounting`后再核对正文和accountingCalls，最终两者均相同。初始回执保留。
- 隔离凭据复制准备曾发生测试器同锁重入；精确终止测试进程，尚未发出Provider请求，修复后继续准备。该记录不是模型UNKNOWN，不计API成功或失败。

## 失败和边界

- RC Gemini Pro：Android/Mac当前原渠道ChatCompletions404；同模型原生generateContent也404，模型列表存在不能证明部署可用。没有换模型。Luna成功不掩盖原回答失败。
- 初始Mac Sonnet HTTP200流中断UNKNOWN、Android格式网络/TLS失败、旧消息投影Gemini空回答、DeepSeek部分覆盖，以及缺来源的旧回执均保留，不计绿灯。
- 正式真机安装／正式Mac覆盖、原生picker及聊天UI、后台/休眠/强杀、图片/扫描PDF/OCR提供商/音视频实际验收未运行。
- Mac服务矩阵为真实HTTPS生产运输器与协调器；真实14格式与重开由独立owner链覆盖。不能将命令行矩阵称为Mac正式UI验收。

## 离线与构建（独立证据层）

- Android JUnit1478总、1469通过、9 opt-in skip、0错误失败；lintRelease＋assembleRelease通过；原正式证书验签通过。
- Rust423通过、33ignored、0失败；Node544通过、12skip、0失败；lint/typecheck/build通过；Mac app/DMG构建、原开发证书签名和严格验签通过。
- 模型合同门20+1+7、负向门16测试通过；领域合同114/40、协议golden/v2/sync通过。
- 两个明确红绿点：真实Luna注解映射；带UTM来源交回原回答消失。精确接收方／材料改变拒绝、历史授权拒绝与旧实现相邻回归单列，不能代替真实业务结果。

## 保全与授权

main/HEAD dafc64d9b33661dc4f792fd1434ff1ddca9cdfa3；HEAD不是434路径既有WIP快照。逐文件修改前副本在`/tmp/nanfeng-luna-routing-20261013/before`；未reset/clean/stash/commit/push。无connected测试／OPPO测试／正式安装。仅公开合成材料，无私人原件。Key仅经一次性内存broker交给原生owner；不进argv/env/明文文件/规范化回执。运行结束读回隔离包credentialEntries=0、bootstrap=0、reverse=0、精确包已停止，见`final-isolation-cleanup.json`。45个公开测试会话原生AppContainer/Room重开，回答、模型与物理调用数保留，networkCalls=0，见`android-reopen-all45-1.json`。

本轮源码/规范/证据为LOCAL ONLY，无正式安装。未通过与未运行项保持开放，不能据本报告宣称全部现用模型或所有材料场景通过。

## 冻结证据

`current20-real-evidence-map.json`逐项对应当前manifest；`source-hashes.json`与`source-delta.patch`只覆盖本轮拥有的源码/规范改动，相对修改前WIP，不是全仓HEAD差异。`build-artifacts.json`记录正式候选和隔离APK/DMG的hash；构建物不纳入提交。`logs/`保存相关门禁、明确红绿与签名/构建证据。当前437个Git状态条目（277修改/160未跟踪）；未提交或推送。

本輪双端20款均有真实服务调用记录，多附件18款有完整成功，Pro404与Muse403保留；14格式成功采用代表模型正交，普通文字仍有7个Android非通过样本。不存在“全部现用模型/所有场景已经通过”的结论。
