# 2026-10-30 当前项目完整复盘

## 基准与交付

本轮复盘起点及收尾业务基线为 `0c5afbdbf61c7d50fd8f257720f3b92763901083`，业务代码checkpoint `2c22021c861ce2e74022e94cf116dfa47ada1201`。本次仅修改文档和项目Skill；没有业务代码、配置、schema、应用测试改动，也没有安装、收费请求、Git提交／推送或发布。

已重整[完整开发档案](../../南枫AI完整开发档案.md)、[AGENTS](../../../AGENTS.md)、[五类Skill](../../../.agents/skills)、[可迁移经验](../../可迁移开发经验.md)，并同步项目状态与交接。现有五类Skill的名字、独立职责和UI元数据保留，只补可复用的失败边界、语义审查及复盘流程，不再创建同义Skill。

## 全量检查与证据层级

| 层 | 实际覆盖 | 不能证明的部分 |
|---|---|---|
| 文件 | 1,954已跟踪＋323非ignored未跟踪＝2,277；2,158文本／119二进制；逐项hash、类型、大小、行数 | ignored私有配置／数据库／缓存未读取；二进制未逐个视觉验收 |
| 结构／适用语法 | 全部清单项参与；Python15、JSON438、XML87、Node216、Bash5、Zsh1项通过，共762 | 1,389项不支持语法检查、119非文本；不编译Kotlin／Rust或执行SQL；7历史XML格式失败 |
| 文档链接 | 319份Markdown的常见内联本地链接 | 初始37缺链均为before-docs历史快照；不改冻结原件。外链、绝对本机路径及片段锚点不当作便携链接已验证 |
| Git | 202本地ref可达提交、200当前HEAD祖先，2026-08-20至10-30；每条提交路径及增删统计 | 未fetch／未读远端不可达历史；主题不能推导工时、动机、部署成功或Git之前过程 |
| 语义 | 组合根、存储／迁移、模型／附件／搜索／计量、备份、同步、后台、CI／部署等关键owner | 定向语义检查，并非全仓逐行证明；历史测试／真实回执分别绑定源码，不扩写全媒体／OS通过 |

全量清单：[summary.json](summary.json)、[files.json](files.json)、[history.json](history.json)、[history-scope.json](history-scope.json)、[structure.json](structure.json)。定向语义检查：[semantic-review.json](semantic-review.json)。文档引用：[links-before.json](links-before.json)，收尾以[verification.json](verification.json)为准。

本地两条支线checkpoint／文档提交未在HEAD祖先链，不能将相似主题计为两轮主线开发。119个二进制均只做身份扫描。七个格式失败均位于`docs/evidence/p6f2d-android-*.xml`的历史取证；不因它们失败修改应用资源或篡改证据。

## 文档与规则裁决

[conflicts.json](conflicts.json)逐项记录旧来源、代码依据、裁决和状态。主要新修正：产品版本1.0.31→1.0.32/code96；Desktop主库44→45；聊天部署22→23；目录和Git数量更新；当前源码、已装候选及历史发布分层。旧UNKNOWN聚合与profile优先级冲突已在前轮修复，本轮只复核，不冒称新业务修复。

direct同步服务端可读payload与旧encrypted命名分别解释，未更改信任模型。AGENTS去除将未来所有“不安装”解释为仅主设备的一次性口径，保留当前授权、保数据和主设备禁测等长期边界；没有把版本、测试数、hash或临时故障写入长期规则。

## 验证

- 45项模型／合同测试及114合同／40域／23聊天模型静态门通过，见[maintenance-checks.json](maintenance-checks.json)。这些门不是所有合同语义已证一致。
- 本轮补跑Supabase策略／合同10项通过；Go网关本地测试通过。没有验证生产Postgres／RLS／RPC／头像部署或在线网关。
- 五个Skill的YAML frontmatter、名称、必需字段、UI默认提示和路由校验通过。Python quick_validate所需PyYAML不可用，未安装全局依赖；使用Ruby YAML.safe_load实际解析及结构检查，见[skill-checks.json](skill-checks.json)。不将结构校验声称为独立agent行为评估。
- 同代码已有Android1578 PASS／10skip、Rust472 PASS／38ignored、Node563 PASS／12skip及签名候选回执；本轮核对原784输入hash后引用，没有重复编译或收费。旧数字注明原执行阶段，详见[checkpoint](../2026-10-30-code-checkpoint/REPORT.md)。
- 收尾逐项比对修改前清单，确认原有非授权文件内容和存在状态不变、HEAD不变；新文件限定在复盘证据目录，具体变更见[changes.json](changes.json)。

## 仍未确认／未完成项

1. 原11:03 EOF／499关闭发起方仍未知；新成功不能倒推旧事故责任，未重发原UNKNOWN。
2. Desktop中途IO／空闲超时及Android取消／超限未统一携带partial计量；未知金额不写零。本轮只记录，不改业务类型。
3. 当前checkpoint的新真实服务／隔离原生重开／主设备对话及全媒体／联网组合未验；旧22模型等回执按原候选使用。
4. Supabase／Google／头像／网关线上状态、Windows／公证和新checkpoint公开发布未核验；源内声明与历史发布回执不能代替当前外部检查。
5. 主入口文件集中、83个action／41个invoke无测试引用仍是维护／覆盖提示；不是已证明功能错误。本轮未扩展成重构任务。
6. CI未接入合同一致性脚本／测试，且未覆盖完整应用／原生／付费服务验收；本轮不改CI配置。
7. 7历史XML、37迁移快照缺链和不支持语法检查的项如实保留；不宣称所有文件语义、渲染或线上功能全部通过。

## 后续维护

当前行为继续由适用合同持有；版本化历史保留在Git和原报告。新故障按精确入口、候选和首个错误分叉处理；需要代码修改或外部动作时另按任务范围执行。未提交的本轮文档和323份原有历史材料均保留，不清理工作树来制造干净状态。
