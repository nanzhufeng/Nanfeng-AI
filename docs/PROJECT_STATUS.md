# Project Status

> 当前实现、代码checkpoint与分层验证优先。历史执行过程见[当前交接](CURRENT_HANDOFF.md)；未完成的真实验收不因本地回归或提交关闭。

## Verified

- Last verified: 2026-10-14 13:56:12 +0800（最终回归内容与本地源码checkpoint一致；无新真实验收）
- Verified branch: main
- Code baseline: d519e83ccb6d9d715936f9e9dcc29c3d2f7fd4d9；本地annotated tag checkpoint-search-routing-20261014
- Verification scope: COMMIT / 回归在提交前WORKTREE执行，1189个已验输入与源码checkpoint逐项相同； 1189源码、配置、Schema、应用测试及维护输入hash无漂移；强制Android全JVM及Release Lint、Rust全lib、Desktop全Node及静态build、合同/模型/协议/Supabase静态/Go通过。未安装、未新原生或真实服务验收。
- Verified by: Codex
- Status: Active

## Current

- [本地代码checkpoint与最终回归（已提交）](verification/2026-10-14-code-checkpoint/REPORT.md)：当前生产owner、资源、Schema、公共fixture及依赖测试纳Git；排除原始回答/回执、before、日志和重复源码。只建本地提交与标签，不推送/发布。
- 正常OpenRouter/官方自身联网保持；RC普通文字Sol/Astra自身，另四款文字与六款附件Luna检索后交回原RC模型/渠道/Key；[C12](C12_MODEL_NETWORK_PARITY_CONTRACT.md)与[模型矩阵](model-service/MODEL_SERVICE_MATRIX.md)为当前行为入口。
- 当前维护目录20聊天部署/1独立OCR/7凭据组；Muse经OpenRouter、深度档智谱上方。Muse可读材料Responses及Gemini Flash native web plugin为精确场景例外，非家族统一变更。两款普通文字均有双端实际来源/金额/持久重开证据，范围见[两模型](verification/2026-10-14-gemini-muse-risk-fix/REPORT.md)及[Gemini同题](verification/2026-10-14-gemini-search-repeat/REPORT.md)。
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

| 本轮最终回归 | 结果 | 证据边界 |
|---|---|---|
| Android | 1495 PASS/10 opt-in skip，322套件；强制test任务及Release Lint PASS | JVM/静态，不是应用进程/主设备/Provider |
| Rust | 443 PASS/34 ignored，0失败 | lib/SQLite/运输边界，ignored未运行 |
| Desktop | 551 PASS/12 skip；lint/typecheck/inventory/build PASS | Node/静态前端，未原生Mac UI |
| 维护门 | 43 PASS；114合同/40域/122owner，20聊天+1独立/7组门PASS | 离线一致性，不保证上游服务 |
| 协议/服务 | exchange v1/v2/sync golden、Supabase静态10、Go PASS | 合成/静态/单测，不是生产RPC/RLS部署 |
| 构建/安装/真实服务 | 本轮无正式重签包/安装/付费调用 | 复用既有准确安装/真实报告，不刷新原执行时间 |

[结构化命令/退出状态/跳过项](verification/2026-10-14-code-checkpoint/regression.json)、[源码hash](verification/2026-10-14-code-checkpoint/source-hashes.json)。系统Skill quick_validate缺PyYAML限制保留；经验审计/链接/秘密检查使用标准库校验。源码字符串与预览fixture不能冒充原生验收。

## Verification Gaps

- [Luna初始矩阵](verification/2026-10-13-luna-routing-implementation/REPORT.md)有双端20部署调用、18款多附件完整成功和14格式代表模型正交成功；非20×14全部组合。后续普通文字成功不抹原Pro404/原UNKNOWN或新媒体缺口。
- 正式双端UI/系统picker/后台休眠/强杀、扫描/OCR/音视频真实Provider、Mac页脚系统复制与About旧占位原生可达性。
- 线上Google/Supabase迁移/RLS/RPC/头像及网关、最新1.0.29远端Release/下载hash、Windows/公证未确认。
- 43历史引用缺失及7XML原始取证格式失败已分类保留，不改写冻结原件；历史本机/ignored产物不进入新checkpoint完整证明。

## Sync & Safety

- Git working tree: 651文件变更已提交为源码checkpoint，358新增文件纳Git；排除原始/临时证据保留本地。323个原始/临时证据文件仍未跟踪且字节保留；准确提交与剩余集合见[提交范围](verification/2026-10-14-code-checkpoint/commit-scope.json)。
- Remote sync: LOCAL ONLY；本轮无fetch/push/Release，远端状态不推断。
- Installed sync: 最新业务输入保持10月14日10:49前后正式候选源码；本輪纯文档/模板变化不进入包，本地Git SHA不代表重新安装。
- Rollback point: 本轮源码checkpoint/tag及限定改前副本；禁止全仓reset/clean/stash覆盖排除证据。
- Secrets/config: 原凭据与正式数据不操作；测试opt-in未启用，新增Provider请求0。原始回执/正文/before/日志不进入提交。

## Next

1. 按新要求补最小必要v5逐事实/原模型真实链路，再评估完整原件；先回放已有回执，克制收费。
2. 定位Pro404和Muse新材料边界，不换模型/Key/渠道或自动重发UNKNOWN。
3. 按范围补正式原生/系统入口与线上/发布验证；不要重复已完成复盘、全合同整理或安装。
