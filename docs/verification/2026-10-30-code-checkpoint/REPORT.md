# 2026-10-30 当前代码 checkpoint 与增量正式沉淀

## 最终基准

本地检查点标签：`checkpoint-model-service-20261030`，指向代码及本文档收口后的提交；不推送远端。

- 代码 checkpoint：`2c22021c861ce2e74022e94cf116dfa47ada1201`，本地 main，前基线 `096341f8d6241927ccd2ae08b56f7339416ae933`。本次76文件提交纳入既有未提交实现、6份新增核心源码／测试及35份当日脱敏验证证据；随后只提交文档收口，不改业务代码、配置、schema或应用测试。
- 完整文件清单：[checkpoint-files.json](checkpoint-files.json)。六份新增核心为 `ProviderResponseProjection.kt`、`ProviderStreamInterruptionTest.kt`、`PublicAcceptanceBudgetPolicy.kt`／测试、`PublicAcceptanceBudgetLedger.kt`／测试，均已实际进入代码提交。
- 最终验证：[regression.json](regression.json)。已复核上一轮[784份输入hash](../2026-10-30-risk-boundary-audit/source-hashes.json)，漂移0；不重复已完成的完整Android／Rust及正式构建。补跑Desktop前端、45维护测试、三项golden和静态合同门，均通过。
- 精确Android正式候选仍为1.0.32/code96、非Debug、原证书，SHA256 `c6ac19be33a6a77ad0b31698057b5982180635fcd3593c28f00ffc6c77d97507`。本轮核对现有字节，没有因文档commit重建；产物内构建提交信息不会自动变成新checkpoint。候选未覆盖手机／未发布，见[产物身份](../2026-10-30-risk-boundary-audit/candidate.json)。

## 已完成项的复用与新增工作

| 事项 | 处理 | 证据与边界 |
|---|---|---|
| 代码／合同修复 | 原有修复完整纳Git，未重复改业务逻辑 | [风险复查](../2026-10-30-risk-boundary-audit/REPORT.md)：UNKNOWN停止、超时计量、SSE字节门、预算预留、profile优先级 |
| Android／Rust／正式包 | 复用准确输入的最终结果 | Android1578 PASS／10skip，Rust472 PASS／38ignored；Release／验收包／Lint／签名通过 |
| Desktop与协议门 | 本轮新增执行 | Node563 PASS／12skip，lint/typecheck/inventory/build通过；模型16＋合同29测试与v1/v2/sync golden通过 |
| 文档固化 | 更新当前事实和经验，保留历史日期 | [开发档案](../../南枫AI完整开发档案.md)、[经验审计](../../南枫AI开发经验增量审计.md)、[可迁移经验](../../可迁移开发经验.md)、[决策](../../decision-log.md)及状态／交接 |
| 已完成规则／Skill | 复用，不重写长期规则 | AGENTS及五类项目Skill无新增改动；工作流审计无硬错误，既有软警告不扩展治理 |

静态inventory的未注册调用和未处理可见动作均0；另有83个action／41个invoke没有测试引用，仍是覆盖提示，不能把扫描PASS解释为全交互行为覆盖。golden为合成包，不是生产用户数据、线上同步或真实云服务验收。

## 合同与冲突固化

C12、费用账本、Android设置合同及模型架构／维护流程已在代码checkpoint同步；本轮复查114合同／40域，保持唯一行为正文，不复制进AGENTS。已知失败可分析材料不再涵盖UNKNOWN；停止边界必须穿过清点／聚合／最终调用；固定种子与动态缓存各自权威以当前实现为准。旧成功夹具缺终态、以504模拟明确拒绝的冲突及修正依据保留在原[红绿证据](../2026-10-30-risk-boundary-audit/red-check.json)。

## 排除与尚未确认

- 323份已有未跟踪历史取证／before快照和设计图保留本地，未删除、未为了清洁状态加入提交；安装包、数据库、原始Provider回执和私有配置不纳Git。排除材料不是本次核心依赖。
- 本轮没有新增付费请求、手机安装、原生重开、macOS正式bundle、云端发布或推送。以前22模型真实Markdown附件成功绑定旧候选；新checkpoint的真实服务／原生媒体／联网组合未因此通过。
- 原11:03 EOF／499关闭发起方仍UNKNOWN；Desktop中途IO／空闲超时及Android取消／超限的partial计量类型缺口保留，未知费用不写零。静态审计与checkpoint不关闭这些问题。
- 恢复入口是本次代码commit及其文档收口commit，可在独立worktree查看；不使用reset／clean／stash破坏当前或排除材料。本轮仅本地提交，不自动发布。
