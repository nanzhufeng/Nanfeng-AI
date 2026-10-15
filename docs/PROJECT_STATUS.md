# Project Status

> 当前实现、代码checkpoint与分层验证优先。历史执行过程见[当前交接](CURRENT_HANDOFF.md)；未完成的真实验收不因本地回归或提交关闭。

## Verified

- Last verified: 2026-10-15T19:17:47+08:00（v1.0.31三资产公开下载校验及GitHub三项CI）
- Verified branch: main
- Code baseline: fe504fec14b53b7bd2093d5b9929e99248695705；v1.0.31最终构建源码，后续仅发布证据/预览文档。
- Verification scope: COMMIT / 双端正式构建与原证书、Android隔离精确APK读回、Mac最终DMG应用独立数据根冷启及四预览；新Node555/12skip与typecheck/lint，合同/模型门PASS。Android JVM/Rust/协议等复用同日checkpoint的未变输入。无主设备/收费动作。
- Verified by: Codex
- Status: Active

## Current

- [v1.0.31发布候选](verification/2026-10-15-github-v1.0.31/REPORT.md)：双端原签名正式包与四张当前预览完成；Mac空数据恢复入口已修。正式Release三资产上传/公开下载hash一致，标签CI三项SUCCESS；主设备不重装。

- [代码checkpoint与最终回归](verification/2026-10-15-code-checkpoint/REPORT.md)：准确纳入当前联网/协议/思考/设置/合同WIP与两份新增测试；最终全套通过。仅修两条陈旧Node断言，323份原始/临时证据保留本地，本地源码d92b0da/标签checkpoint-model-network-20261015已读回；无推送/发布/安装/新收费。

- [全项目完整复盘](verification/2026-10-15-project-retrospective/REPORT.md)：更新[开发档案](南枫AI完整开发档案.md)、AGENTS、五类Skill与[可迁移经验](可迁移开发经验.md)。纠正旧版本/模型数/证据版本与Git/WIP归属，区分已发布包和同版本覆盖包；45维护测试通过，业务输入不变。

- [全项目合同统一](verification/2026-10-15-contract-unification/REPORT.md)：114合同／40领域全量扫描与热点裁决，18份正文、8项范围标签、4个领域owner索引已统一；旧恢复入口任务归历史，保留原兼容与验收事实。29维护测试通过，业务输入不变，无功能修改／新收费／发布。

- [其他模型联网复查与正式覆盖](verification/2026-10-15-other-model-network-review/REPORT.md)：21模型42请求准备通过；修DeepSeek最终阶段重复要求来源、Mac千问／DeepSeek／RC Claude非流式协议分发及RC Opus既有别名误判。DeepSeek Flash／GLM Flash双附件2/2及重开通过，RC Flash仅基础调用通过。联网／协议已双端原签名覆盖，另修Android完成后思考保存入口（JVM重开通过，正式包已保数据覆盖）；RC Pro历史404／本轮中断未知仍未闭环，未重发或换模型。

- [Haiku TLS／流式修复与双端正式覆盖](verification/2026-10-15-haiku-tls-streaming/REPORT.md)：实际主设备记录为TLS握手失败；目录流式误判已修，握手前有限重连不扩展到已发请求；隔离 Android 保持原网络设置真实样例 76.8 秒完成并重开，最终1519 JVM／10 skip及正式检查通过。双端原签名覆盖，OPPO设置隐藏入口实看通过；原失败未重发；后续用户确认Haiku问题解决（用户反馈），不扩展为其他模型主设备验收。

- [正式设置隐藏开发与诊断](verification/2026-10-15-hide-development-settings/REPORT.md)：Android／Desktop 设置导航已移除，空“高级”组同时隐藏，诊断实现与数据保留；19 JVM＋8 Desktop检查及双端相关构建通过。已随本轮连接修复正式覆盖，OPPO设置实看通过。

- [附件不限主题联网修复与正式覆盖](verification/2026-10-15-attachment-search-all-topics/REPORT.md)：读图请求体、主题筛选、原件／提取／证据混淆及无关来源已修；双端混合 PNG＋Markdown 实际相关搜索／重开通过。OPPO94／双端1.0.30已原签名覆盖，Android精确APK读回与数据目录身份保持、Mac1595数据文件0变化；未发布，正式UI及全模型格式矩阵未验。

- [v1.0.30正式发布](verification/2026-10-15-github-v1.0.30/REPORT.md)：双端原签名正式包、Android隔离实装读回及Mac精确包正常采图完成，README四张当前预览；[Release](https://github.com/nanzhufeng/Nanfeng-AI/releases/tag/v1.0.30)三附件上传并下载核对通过，云端三项全通过；标签0494e0f，构建源码7c42456。主设备仍上一轮1.0.29同业务修复包，本轮不重装。

- [当前修复双端正式覆盖](verification/2026-10-15-title-search-formal-cover/REPORT.md)：OPPO原签名非Debug覆盖，精确APK读回、应用身份／首次安装时间／数据目录标识保持；Mac原证书覆盖，1599数据文件0变化。包含当前标题／联网和改名增量；未新增Provider调用或正式UI业务验收。

- [Android改名编辑框恢复](verification/2026-10-15-mobile-rename-editor/REPORT.md)：标题双击／菜单恢复有边框输入与取消／确认弹窗，全选／焦点／键盘请求齐全；97相关测试与Debug通过，保留标题不提升排序。已随本次正式覆盖安装，真机UI未验。

- [纯标题变更保留位置](verification/2026-10-15-title-order/REPORT.md)：Android／Mac 手动改名、自动标题保留排序活动时间，标题版本／同步／索引仍更新，实际内容更新仍正常提升；本地生产保存／重开与回归通过，无新付费，已随本次覆盖安装。原生 UI 未实测。

- [联网开关统一与Haiku修复](verification/2026-10-15-haiku-search-switch/REPORT.md)：以开关实际联网，21聊天部署共用完成证明门；Haiku同OpenRouter请求入口检索、原模型回答，双端公开订单短图各5结构化来源／费用／重开通过。不外推全模型新付费矩阵、所有媒体或正式UI。

- [自动标题与主完成修复](verification/2026-10-15-auto-title-completion/REPORT.md)：双端独立调度标题，千问Flash→DeepSeek Flash有限失败兜底（含超时／网络），非思考短请求，保护手动标题／新草稿。源码／本地回归及[双端三场景真实标题](verification/2026-10-15-title-native-live/REPORT.md)通过，共6次真实短调用；已随本次覆盖进入现装包，真实标题证据不代替正式UI验收。

- [10月15日正式覆盖](verification/2026-10-15-openrouter-argon-cover/REPORT.md)：Android／Mac已原签名保数据覆盖OpenRouter Argon增量，RC新增撤回；安装与数据读回通过，未新增收费生成或正式UI验收。

- 用户最新确定仅保留OpenRouter Argon；RC Argon暂缓，本轮接入代码已完整撤回。网站独立Key已创建但不接入，High404／普通版两种形态504记录见[暂缓回执](verification/2026-10-15-rc-argon-deferred/REPORT.md)，RC无后续付费或实施任务；随后OpenRouter双端安装已单独完成。

- [Gemini 4 Argon双端接入](verification/2026-10-15-gemini-4-argon/REPORT.md)：OpenRouter实时部署，深度档Muse之后／智谱之前；默认与Auto不变，原生搜索参数和费用同步。源码及本地双端正式覆盖已完成；GitHub v1.0.29尚不含该未发布增量。

- [v1.0.29 发布候选](verification/2026-10-14-github-release/REPORT.md)：双端正式构建／签名、模拟器精确实装与 Mac 授权正式包正常启动采图完成，数据保留核对；README 四张当前预览更新。公开发布不改变既有真实业务验收边界。

- [本地代码checkpoint与最终回归（已提交）](verification/2026-10-14-code-checkpoint/REPORT.md)：当前生产owner、资源、Schema、公共fixture及依赖测试纳Git；排除原始回答/回执、before、日志和重复源码。只建本地提交与标签，不推送/发布。
- 正常OpenRouter/官方自身联网保持；RC普通文字Sol/Astra自身，另四款文字与六款附件Luna检索后交回原RC模型/渠道/Key；[C12](C12_MODEL_NETWORK_PARITY_CONTRACT.md)与[模型矩阵](model-service/MODEL_SERVICE_MATRIX.md)为当前行为入口。
- 当前维护目录21聊天部署/1独立OCR/7凭据组；Muse经OpenRouter、深度档智谱上方。Muse可读材料Responses及Gemini Flash native web plugin为精确场景例外，非家族统一变更。两款普通文字均有双端实际来源/金额/持久重开证据，范围见[两模型](verification/2026-10-14-gemini-muse-risk-fix/REPORT.md)及[Gemini同题](verification/2026-10-14-gemini-search-repeat/REPORT.md)。
- 检索费用按同次提问合并展示、回答单列，物理Call账本保留；页脚为本次已知总费及未知事实。失败/取消不抹已返回usage，UNKNOWN不自动重发。[费用分组](verification/2026-10-13-retrieval-cost-grouping/REPORT.md)、[失败计量](verification/2026-10-13-rc-gemini-usage/REPORT.md)。
- 新事实粒度/来源绑定、v5检查点与五类解析遗漏修复已双端本地验证；同原件planner66项/初始17批一致，仅离线计划。合同缓存条款已分开模型v5和旧独立服务semantic-v2。[风险报告](verification/2026-10-14-attachment-fact-risk-review/REPORT.md)。
- 可见页脚原位选择复制已实现；Android隔离系统Copy/窄屏证据、Desktop浏览器渲染证据成立，原生Mac仍未验。[复制报告](verification/2026-10-14-footer-text-copy/REPORT.md)。
- [10月14日最新正式覆盖](verification/2026-10-14-fact-risk-cover-install/REPORT.md)：Android/Mac1.0.29（Android93）原证书覆盖与候选读回，Android安装元数据保持、Mac1595数据文件hash不变。包含当前业务修复；本轮不重装，覆盖不等于正式UI/业务验收。
- [全合同统一](verification/2026-10-14-contract-implementation-unification/REPORT.md)、[完整复盘](verification/2026-10-14-project-retrospective/REPORT.md)已完成，不重做；本轮仅[经验增量审计](南枫AI开发经验增量审计.md)、小范围合同裁决及准确提交基准固化。

## In Progress

- 当前v6逐事实证据与原模型长答的更广真实验收仍有缺口；旧v5或局部原件回放不替代新版服务证据。
- Muse新Responses多可读附件/媒体及正式双端UI/picker/生命周期/扫描媒体；普通文字已通过，不能扩大到所有材料。

## Blocked

- 真实服务旧失败仍未闭环：RC Gemini Pro历史404／本轮控制中断结果未知、旧Mac原件UNKNOWN、Sol另一题504。本地checkpoint不因此阻塞，但不能宣称全部真实链路通过；未知结果不自动重发。

## Tests

最终checkpoint（2026-10-15）：Android1535总/1525PASS/10skip/326套件及ReleaseLint；Rust457/36ignored；Node554/12skip与lint/typecheck/inventory/build；维护45、Supabase静态10、三golden及Go通过。暂存快照1222输入一致/六门PASS，见[回归](verification/2026-10-15-code-checkpoint/regression.json)。

完整复盘（2026-10-15）：45协议/治理维护单测、合同/模型门、5类Skill与UI元数据、交付链接/隐私与保真核验。全清单2156、HEAD历史189/全refs191；7历史XML失败及37迁移快照缺链保留。应用测试/构建/真实服务本轮未重跑，见[复盘回执](verification/2026-10-15-project-retrospective/REPORT.md)。

合同统一（2026-10-15）：29维护测试／0失败；登记、语义哨兵／owner、模型门、链接与hash保真通过。全库结构扫描保留7份历史XML证据格式失败；应用构建、真实服务与设备验收本任务未重跑，见[合同报告](verification/2026-10-15-contract-unification/REPORT.md)。

其他模型复查（2026-10-15）：Android1525 PASS／10 skip、326套件，正式构建／Release Lint通过；Rust457 PASS／36 ignored；21模型42请求准备与21+1/7、114/40门PASS，代表真实服务与未闭环项见[报告](verification/2026-10-15-other-model-network-review/REPORT.md)。

附件不限主题增量（2026-10-15）：Android1513 PASS／10 skip、324套件、Debug／正式构建／Release Lint；Rust455 PASS／36 ignored、cargo check、Mac正式包验签／DMG通过；21＋1／7及114／40门PASS。真实混合样例、网络失败、费用、重开与安装边界见[报告](verification/2026-10-15-attachment-search-all-topics/REPORT.md)。

联网收口（2026-10-15）：Android1506 PASS／10 skip、323套件、Debug构建；Rust448 PASS／36 ignored、cargo check；Node553 PASS／12 skip、lint（前端未再改）；模型16／21+1/7、合同治理15／114/40门PASS。[实际结果](verification/2026-10-15-haiku-search-switch/verification.json)。Haiku真实双端短图／重开单独见报告，主设备／正式UI未测。

自动标题增量（2026-10-15）：Android1502 PASS／10 skip、Debug；Rust446 PASS／34 ignored、cargo check；Node553 PASS／12 skip、lint；维护16与21+1/7门PASS。[实际结果](verification/2026-10-15-auto-title-completion/verification.json)。0新Provider请求，无安装。

Argon增量（2026-10-15）：Android1497 PASS/10 skip＋Debug构建；Rust444 PASS/34 ignored＋Mac编译；Node553 PASS/12 skip＋lint/typecheck/build；维护16与21+1/7门通过。[本轮结果](verification/2026-10-15-gemini-4-argon/verification.json)。以下表格保留10月14日发布基准的历史范围。

| 本轮最终回归 | 结果 | 证据边界 |
|---|---|---|
| Android | 1495 PASS/10 opt-in skip，322套件；强制test任务及Release Lint PASS | JVM/静态，不是应用进程/主设备/Provider |
| Rust | 443 PASS/34 ignored，0失败 | lib/SQLite/运输边界，ignored未运行 |
| Desktop | 551 PASS/12 skip；lint/typecheck/inventory/build PASS | Node/静态前端，未原生Mac UI |
| 维护门 | 43 PASS；114合同/40域/122owner，20聊天+1独立/7组门PASS | 离线一致性，不保证上游服务 |
| 协议/服务 | exchange v1/v2/sync golden、Supabase静态10、Go PASS | 合成/静态/单测，不是生产RPC/RLS部署 |
| 发布增量 | 双端正式构建/验签及正常采图PASS，Android仅模拟器保数据覆盖，Mac授权启动；0主动付费请求 | GitHub三项CI与资产下载hash PASS；不替代既有真实业务缺口 |

[结构化命令/退出状态/跳过项](verification/2026-10-14-code-checkpoint/regression.json)、[源码hash](verification/2026-10-14-code-checkpoint/source-hashes.json)。系统Skill quick_validate缺PyYAML限制保留；经验审计/链接/秘密检查使用标准库校验。源码字符串与预览fixture不能冒充原生验收。

## Verification Gaps

- 本轮正式保数据覆盖已完成，但正式UI／用户原始材料未实测；不是21模型×全部格式的真实矩阵，模型生成内容仍出现单位／表述错误，不能将联网通过等同事实准确性通过。

- 新标题三场景已双端真实生成／落库／重开；正式原生UI的主进度／标题刷新与本增量正式覆盖未做。注入超时不证明DeepSeek或Qwen当前上游故障。

- 新增Argon尚未做真实Provider生成／来源／媒体、双端原生菜单验证；本地回归不能替代。

- [Luna初始矩阵](verification/2026-10-13-luna-routing-implementation/REPORT.md)有双端20部署调用、18款多附件完整成功和14格式代表模型正交成功；非20×14全部组合。后续普通文字成功不抹原Pro404/原UNKNOWN或新媒体缺口。
- 正式双端UI/系统picker/后台休眠/强杀、扫描/OCR/音视频真实Provider、Mac页脚系统复制与About旧占位原生可达性。
- 线上Google/Supabase迁移/RLS/RPC/头像及网关、Windows/公证未确认。v1.0.29远端Release及双端资产下载hash已核验，见发布回执。
- 43历史引用缺失及7XML原始取证格式失败已分类保留，不改写冻结原件；历史本机/ignored产物不进入新checkpoint完整证明。

## Sync & Safety

- 本轮源码checkpoint d92b0daf70597f7447b6dc7ad6649938692f8f5b 与本地tag已读回；同步观察为LOCAL ONLY，未push。该同步快照取最终文档提交前，当时仅7份文档待回填提交，业务输入1222项无漂移；最终文档HEAD由Git读取，不要求本字段与自身提交SHA相等。

- 联网、流式／TLS、设置与合同增量已纳入源码checkpoint及v1.0.31发布候选；原双端覆盖与候选hash按原安全报告保留，不由新Git提交或同版本GitHub包推断新安装。

- 2026-10-15发布完成：相关源码/合同已推main，v1.0.31三资产正式发布且公开下载验证通过；323份原始/临时未跟踪证据保留。历史v1.0.30已发布范围与本地checkpoint不同，不能据同版本号推断远端已有此次修复。

- Git working tree: 当前源码/合同/核心新增测试、发布文档及四张当前预览均已提交；本摘要回填前仅回执文档变化。323份原始/临时证据仍未跟踪且逐项hash保留，无新增产物入Git。
- Remote sync: main与v1.0.31正式标签已推送，标签1ffacaf36bd5a5b29c6e9f45ceb7933a1ec1e5e4；三资产远端/公开下载hash一致、标签CI三项PASS。构建源码fe504fec14b53b7bd2093d5b9929e99248695705；随后纯文档回执薄提交不重建/移动标签。私有checkpoint标签未推送。
- Installed sync: 主设备保留10月15日已授权覆盖的1.0.30修复包；本次1.0.31仅隔离模拟器实装及Mac精确候选独立数据根启动，不冒称主设备已更新。
- Rollback point: 本轮源码checkpoint/tag及限定改前副本；禁止全仓reset/clean/stash覆盖排除证据。
- Secrets/config: 本安装阶段原应用凭据未改、0新增Provider请求，正式数据保留；此前获授权创建的RC反重力网站Key未接入应用，3次404/504调用费用UNKNOWN与暂缓决定单独留档。临时凭据通道已关闭；原始回执/正文/before/日志不进入提交。

## Next

1. 下一次实现变更先按合同索引反查同领域入口／子合同与生产owner，沿已建立回归维护，避免重复全库整理。
2. 后续按新证据处理RC Pro与Muse材料缺口，不换模型／Key／渠道，不自动重发UNKNOWN。
3. 仅在后续任务需要时补正式原生／系统入口及线上验证，不重复已完成合同整理或安装。
