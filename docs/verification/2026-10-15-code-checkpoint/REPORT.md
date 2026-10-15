# 当前代码 checkpoint 与最终回归（2026-10-15）

本轮只补准确源码闭包、最终回归、增量合同验收与正式沉淀；已完成复盘/五类Skill主体/真实模型比较/安装不重做。基线为 `5a52aa2a8928cc46bad136cb608fd1eb3b62517e`，用户已授权建立本地checkpoint，不推送/发布，不触发Provider、主设备或正式数据动作。

## 源码与核心新增

- [输入hash](source-hashes.json)冻结1222份业务、配置、资源、Schema、应用测试和维护输入。除下述两份陈旧Node测试断言，本次不改产品源码/配置/Schema；已有联网/协议/Room/设置WIP准确纳Git。
- 新增核心为 `CurrentModelNetworkReadinessTest.kt` 与 `ProviderTlsConnectionRecoveryTest.kt`：前者走生产resolver/profile/policy/adapter检查当前manifest搜索开关与身份，后者走生产transport边界验证握手前有限恢复、证书失败与已发正文不重试。二者不调用真实服务。
- [提交范围](commit-scope.json)列全部选中路径和排除统计。323份原始回执/临时before/日志/旧重复材料/设计草案保留本地，未删除、不混入提交；14份近期安全报告/摘要与两份新增测试纳入。
- 代码提交与本地标签 `checkpoint-model-network-20261015` 绑定准确源码；发生后在本页回填。后续薄文档提交仅记录已发生事实，不递归追求文档自身SHA相等。

## 本轮实际最终回归

| 检查 | 本轮结果 | 边界 |
|---|---|---|
| Android JVM | 强制全量重跑：1535总/326套件，1525 PASS、10 opt-in skip、0失败错误 | 无connected、仪器、应用进程或Provider验收 |
| Android Release Lint | PASS；JBR/离线/临时6GB堆、2worker | 未改Gradle配置，不重打或安装正式APK |
| Desktop Rust | offline cargo test --lib：457 PASS、36 ignored、0失败 | 真实/原生opt-in不计通过 |
| Desktop Node | 566总：554 PASS、12 skip、0失败；lint/typecheck/inventory/static build PASS | 初次2失败是仍要求隐藏设置入口的旧断言，原始红灯保留 |
| 维护 | 45 tests；合同114/40、模型21+1/7组门PASS | 离线一致性，不证明上游或CI当前状态 |
| 协议 | exchange v1/v2与sync golden全部PASS | 合成载荷，不是真实用户迁移 |
| Supabase／网关 | 10项既有.mjs静态/头像策略PASS；Go package tests PASS | 不执行真实Postgres/RLS/Edge部署 |
| 原生/收费/安装 | 本轮0新Provider、0安装/启动；签名候选沿原报告复用 | 不刷新已有真实证据日期或关闭业务缺口 |

结构化结果与时长见[regression.json](regression.json)。原签名/实装字节/数据保持沿[前轮覆盖证据](../2026-10-15-other-model-network-review/REPORT.md)与其候选hash，本地Git提交不变成新安装或新发布。

## 合同与增量沉淀

- [前轮114合同统一](../2026-10-15-contract-unification/REPORT.md)与[完整复盘](../2026-10-15-project-retrospective/REPORT.md)已完成，纳入原修改，不重做。C12/P3B继续拥有全主题、阶段来源、真实协议、终态与完成后可选元数据追加边界。
- 本轮设置合同仅补主菜单负向验收：隐藏开发与诊断，WORK关闭时不出现高级空组；底层实现/图标保留不要求入口恢复，“运行诊断”仍按现有模型页入口验收。
- 实际全套Node红灯暴露两条旧断言，现修正为验证隐藏状态和当前可见图标，产品行为未动；不能为测试绿灯恢复废弃入口。
- [增量正式审计](../../南枫AI开发经验增量审计.md)只追加本轮反馈/根因/owner/验证和排除集合，旧10月14审计明确归历史。AGENTS、五类Skill及通用经验主体不重复重写。

## 索引闭包与回读

暂存导出1839份跟踪文件，87个选中变更/20个新增均在索引；1222份输入逐项hash与已验证工作树一致。独立导出目录的合同/owner/模型门及Desktop typecheck/lint/build六门全部PASS，不读取被排除的323份原始/临时证据。核心owner/声明/fixture/测试必须从索引可读取，不能靠未跟踪原始证据构建。无正文审计不纳入真实Provider payload、用户材料或私有路径。

## 未关闭的缺口

RCPro历史404/本轮中断未知及未知费用、旧Mac原件UNKNOWN/Sol另一题504，更广v6主题/全部模型格式/原生UI/picker/生命周期/媒体、线上Google/Supabase/网关、Windows/公证仍按原范围待验。未知请求不自动重发。CI未接入一致性审计的既有配置缺口仅记录，本轮未改CI；历史7XML/37 before-docs引用失败保留原件。
