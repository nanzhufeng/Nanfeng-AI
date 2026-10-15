# Project Status

> 当前实现、代码checkpoint与分层验证优先。历史执行过程见[当前交接](CURRENT_HANDOFF.md)；未完成的真实验收不因本地回归或提交关闭。

## Verified

- Last verified: 2026-10-15T14:35:04+08:00（双端当前修复正式保数据覆盖与读回）
- Verified branch: main
- Code baseline: d64d71026418b9325931b6cbbb94544b28a81309；Argon／标题／联网修复均为未提交WORKTREE增量，生产输入与验证范围见本轮报告。
- Verification scope: WORKTREE / 纯标题保留排序（双端保存／重开、Android等活动时间同步、38相关测试／Debug、Rust449／列表7通过）；开关语义与完成门统一、Haiku请求入口检索、标题独立短任务；本地回归／Debug／Mac编译PASS，Haiku及标题双端真实原生owner通过，双端当前增量正式覆盖及读回PASS；正式业务UI缺口保持。
- Verified by: Codex
- Status: Active

## Current

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

- 新v5逐事实真实Provider返回、完整原件范围与原模型长答：旧原件部分覆盖/回放不替代新版服务验收。
- Muse新Responses多可读附件/媒体及正式双端UI/picker/生命周期/扫描媒体；普通文字已通过，不能扩大到所有材料。

## Blocked

- 真实服务旧失败仍未闭环：RC Gemini Pro附件回答404、旧Mac原件UNKNOWN、Sol另一题504。本地checkpoint不因此阻塞，但不能宣称全部真实链路通过；未知结果不自动重发。

## Tests

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

- 新标题三场景已双端真实生成／落库／重开；正式原生UI的主进度／标题刷新与本增量正式覆盖未做。注入超时不证明DeepSeek或Qwen当前上游故障。

- 新增Argon尚未做真实Provider生成／来源／媒体、双端原生菜单验证；本地回归不能替代。

- [Luna初始矩阵](verification/2026-10-13-luna-routing-implementation/REPORT.md)有双端20部署调用、18款多附件完整成功和14格式代表模型正交成功；非20×14全部组合。后续普通文字成功不抹原Pro404/原UNKNOWN或新媒体缺口。
- 正式双端UI/系统picker/后台休眠/强杀、扫描/OCR/音视频真实Provider、Mac页脚系统复制与About旧占位原生可达性。
- 线上Google/Supabase迁移/RLS/RPC/头像及网关、Windows/公证未确认。v1.0.29远端Release及双端资产下载hash已核验，见发布回执。
- 43历史引用缺失及7XML原始取证格式失败已分类保留，不改写冻结原件；历史本机/ignored产物不进入新checkpoint完整证明。

## Sync & Safety

- 最新标题／进度／联网修复为LOCAL ONLY，未提交／推送／正式安装；现装仍12:33／12:34的OpenRouter Argon正式包，不含本增量。

- 2026-10-15：Argon源码／合同增量LOCAL ONLY，未提交／推送，正式双端已保数据覆盖；底层HEAD保留，原始未跟踪证据未处理。历史已发布范围仍见下方，不代表此次增量已同步远端。

- Git working tree: 651文件变更已提交为源码checkpoint，358新增文件纳Git；排除原始/临时证据保留本地。323个原始/临时证据文件仍未跟踪且字节保留；准确提交与剩余集合见[提交范围](verification/2026-10-14-code-checkpoint/commit-scope.json)。
- Remote sync: main 与 v1.0.29 已推送／正式发布，三个资产下载hash一致，云端源码三项PASS；原私有checkpoint不推送，结果见发布回执。
- Installed sync: 10月15日双端正式已覆盖仅OpenRouter Argon源码增量，精确候选读回及数据保留见最新安装报告；本地Git SHA仍不代表未提交WORKTREE。
- Rollback point: 本轮源码checkpoint/tag及限定改前副本；禁止全仓reset/clean/stash覆盖排除证据。
- Secrets/config: 本安装阶段原应用凭据未改、0新增Provider请求，正式数据保留；此前获授权创建的RC反重力网站Key未接入应用，3次404/504调用费用UNKNOWN与暂缓决定单独留档。临时凭据通道已关闭；原始回执/正文/before/日志不进入提交。

## Next

1. 按新要求补最小必要v5逐事实/原模型真实链路，再评估完整原件；先回放已有回执，克制收费。
2. 定位Pro404和Muse新材料边界，不换模型/Key/渠道或自动重发UNKNOWN。
3. 按范围补正式原生/系统入口与线上/发布验证；不要重复已完成复盘、全合同整理或安装。
