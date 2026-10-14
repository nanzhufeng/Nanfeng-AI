# 当前代码 checkpoint 与最终回归（2026-10-14）

本轮按用户要求固化当前代码、补最终回归、做增量合同裁决和正式经验沉淀。已完成的全库复盘、五类Skill主体、模型比较、真实联网与安装不重做；不改业务代码、配置值、Schema或应用测试，不安装、不调用Provider、不推送或发布。新增核心owner、依赖资源、Schema、共享fixture与测试纳入Git。

## 代码与提交范围

- 回归前基线为dafc64d9b33661dc4f792fd1434ff1ddca9cdfa3，实际验证工作树；本地标签`checkpoint-search-routing-20261014`冻结源码提交，最终准确SHA由标签与下方提交回填确定。
- [源码hash](source-hashes.json)绑定1189个业务／配置／Schema／应用测试及维护输入，最终回归前后业务层无漂移。本轮修改仅文档与经验模板/路由，不借checkpoint修功能。
- 所有本轮纳入与排除集合、秘密检查、索引闭包和最终回读见[提交范围](commit-scope.json)。未跟踪原始回答、before、日志、重复源码副本等保留本地，不删除或混入提交。
- 代码checkpoint建立后，必要的薄文档提交只回填已发生的commit与状态；不递归追求文档自身SHA相等，代码基线保持源码checkpoint的完整SHA。annotated tag只标该准确源码点。
- 暂存检查把新增公开PDF的固定宽度xref记录误判为文本尾空格；将已有局部PDF binary属性推广到所有PDF，保留原始fixture字节而不裁掉格式必需空格。只改Git属性，不改业务配置或PDF内容；最终文本空白门重新检查。

## 最终回归

| 验证 | 本轮实际执行结果 | 边界 |
|---|---|---|
| Android JVM | 强制重新执行，322套件/1505测试：1495 PASS、10 opt-in skip、0失败/错误 | 不运行connected、应用进程、真机或Provider验收 |
| Android Release Lint | PASS；JBR、临时6GB堆/2worker，未改Gradle配置 | 没有重打/安装正式APK |
| Rust | cargo test --offline --lib：443 PASS、34 ignored、0失败 | 忽略的真实/原生opt-in不当通过 |
| Desktop Node | 551 PASS、12 skip、0失败；lint/typecheck/inventory/build PASS | 渲染动作与invoke有入口覆盖；83动作/42invoke缺测试引用是路由提示，非已复现缺陷 |
| 维护门 | 合同114/40、模型20+1/7组、合同/模型共43测试PASS | 离线一致性，不证明上游能力 |
| 协议 | exchange v1/v2与sync golden全通过 | 合成载荷，不是真实账号或用户迁移 |
| Supabase／网关 | Supabase/头像静态10 PASS；Go go test -count=1全通过 | 不执行Postgres/RLS或部署服务 |
| 收费与安装 | opt-in环境未启用；新增Provider请求0、安装0 | 不关闭既有真实验收缺口 |

执行命令、时长、退出状态及范围见[结构化回归](regression.json)。本轮不重新签名/封装app与DMG；[既有正式覆盖](../2026-10-14-fact-risk-cover-install/REPORT.md)仍按原候选/原签名/原数据证据成立，不能把本地Git提交变成新安装。

## 增量合同与经验固化

发现C12前段已写v5，后段显式重试却仍写topic/evidence-v4。实际Android/Rust模型附件指纹为attachment-fact-evidence-v5；独立旧服务有自身independent-semantic-v2。合同现分开两域并拒绝旧v4作为新条件，未修改缓存实现或重新执行联网。合同治理补版本控制依赖闭包和提交/真实验收分层，移除一次性“本次用户要求”措辞。

[经验增量审计](../../南枫AI开发经验增量审计.md)用真实反馈—根因—实现—各层验证矩阵固化；context复用完整档案、通用经验复用原文，文档同步Skill仅增量路由一个可复用模板。模板/审计结构、链接、秘密/占位检查使用既有标准校验器；不修改外部Skill或长期记忆。

## 没有扩大结论的范围

新版v5逐事实真实返回/原报告全量、新Muse多附件/媒体、RC Pro附件404、旧Mac原件UNKNOWN及Sol另一题504、正式双端UI/picker/生命周期/扫描媒体、Mac页脚原生复制、线上Google/Supabase/网关、最新远端Release/Windows仍待验。普通文字真实成功保留原范围；本轮回归不替代这些缺口。

历史报告中的本机备份/ignored安装包、原始日志和差异附件有部分只在本地；提交范围单列这些非portable引用，不把它们当新checkpoint的完整证明。当前正式文档与维护入口的源码/配置/fixture依赖须在索引中闭合，原始证据不因提交变成公开材料。

## 暂存快照验证

暂存导出包含651个变更文件，1189个源码/配置/Schema/应用测试及维护输入与已验hash逐项相同，核心新增无遗漏；真实ESM静态声明无缺失。隔离导出快照的合同/owner/模型门、Desktop typecheck/lint/build均PASS。只对暂存集合验证，不依赖工作树中排除的原始证据。经验审计结构与191个本地链接、秘密/占位门通过，PDF binary属性后的暂存空白检查通过。

## 本地 checkpoint 回填

准确源码commit、tag读回、文档提交与排除集合在提交完成后据Git实际结果回填；未发生的操作不提前当成功。完整私有回归日志与原件保留本机隔离目录，标识nanfeng-checkpoint-20261014-bv18srn9；持久证据以本目录结构化结果、源码hash和本地Git对象为准。
