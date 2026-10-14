# Android／Mac 遗漏风险复核（2026-10-13）

结论：复核双端关键发送/授权/附件检索/计量/终态路径，确认并修复两处遗漏：双端辅助公开URL误收localhost子域；Android检索落账丢失服务商结算金额及失败响应的量化元数据。正常自身联网、Luna接收方、原RC回答身份、Auto与Muse菜单均保持。此报告不声称对全软件逐行或全部真实场景完成证明。

## 已修复

| 风险与触发 | 根因和影响 | 修复／双端证据 |
|---|---|---|
| P2：附件公开线索或原生来源含 `http://service.localhost/data`（含末尾点） | 双端辅助公开URL owner只拦.local/.internal，漏localhost子域，可把本机域名当公开检索线索或绑定证据 | Android `AttachmentWebSearchPlan.publicUrl`、Mac `attachment_web_search_v1::public_url`同样拒绝；共享fixture先双端红灯、后绿灯 |
| P1：Android检索响应已有明确币种的结算金额；或响应返回量化字段但来源/终态校验失败 | 成功audit只复制Token和模型，不传reportedProviderCost；失败Result丢失结算、实际模型及缓存/推理Token，boundAudit改用目录估算。真实Executor＋Room合成传输复现9001微USD落为16微USD | 检索Ready/Failed都传已返回量化事实，结算优先于估算，状态仍按原校验失败/UNKNOWN。Room成功和拒绝分支关闭重开保持完整同一Call；Mac既有同类SQLite/账本测试通过，不改其业务 |

[Android检索交接](../../../app/src/main/java/com/nanzhufeng/ai/ai/RelayCoreWebSearchCompanion.kt)、[实际Executor与审计](../../../app/src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt)、[Room回归](../../../app/src/test/java/com/nanzhufeng/ai/ai/NormalChatGenerationStabilityTest.kt)、[共享URL样本](../../../protocol/fixtures/attachment-search-plan-v1.json)。源码按本轮修改前副本独立diff核对，不用HEAD覆盖之前的WIP。

非标准回环表示127.1/十六进制/八进制/整数地址在新增共享样本中已被两端原解析路径拒绝，未虚报为新修复；它们作为回归保留。Mac RC无明确币种的usage.cost清空属于既有正确行为，不能为了金额非空误报USD或合并搜索/回答费用。

## 双端复核面

| 关注点 | Android | Mac | 层与界限 |
|---|---|---|---|
| 路由/身份 | AutomaticWebSearchPolicy、routingSnapshot、Adapter/Companion | ordinary_chat请求、relaycore检索与lib接线 | 核对Sol/Astra文字自身，其余RC及全部RC附件Luna→原模型；维护门20+1部署/7凭据组PASS，未探测上游 |
| 授权/凭据 | 当前草稿/已提交消息fingerprint、精确recipient与secret读取顺序 | prepare_ordinary_chat授权与retrieval grant、配置门 | 当前源码＋已有回归；无真实secret读取或旧授权扩张 |
| 多附件/材料/来源 | 既有bridge/plan/coordinator与公开URL | 文件投影/plan/scoped owner与公开URL | 共享样本、格式与范围回归；原生picker/真实媒体未补 |
| UNKNOWN/取消/写入失败 | 实际Executor、发送Attempt、Room回读 | streaming/scoped owner、SQLite/账本 | 全套现有自动回归；不自动重发UNKNOWN，不改变终态以通过验收 |
| 量化事实/重开 | 本轮新增真实Executor＋Room成功/拒绝/重开断言 | 既有relay_retrieval_usage_survives_failed_answer_and_reopen_without_inventing_cost及全套 | 服务商I/O合成；持久化owner实际运行，不证明真实API返回该金额 |
| 合同一致性 | C12及用量合同同步，114/40检查 | 同一入口与双端owner定位 | 22维护测试；静态/热点语义，不是全软件完整审计 |

## 运行结果

- Android：318套件，1479 tests＝1470 PASS＋9 SKIP，0FAIL/ERROR；`:app:testDebugUnitTest :app:assembleDebug`成功。包含新结算事实测试（成功、拒绝及Room关闭重开）、原生接线/失败/重试/授权相关现有回归。
- Mac Rust：423 PASS＋33 ignored，0FAIL；附件计划专项9PASS、既有检索结算/失败持久化专项1PASS。完整Rust测试编译当前native owner。
- Desktop：544 PASS＋12 SKIP，0FAIL。
- 合同：114/40治理与一致性PASS；模型维护20聊天＋1专用部署/7凭据组PASS；维护测试22PASS。

红灯日志：[Android URL](android-red.log)、[Mac URL](mac-red.log)、[金额](accounting-red.log)。金额红灯XML见`red-xml/`。绿色及全量日志、[Android XML摘要](android-tests-summary.json)同目录保留。

## 未运行／边界

本轮没有付费或云端请求、没有安装、没有设备操作；正式Android/Mac仍为上一轮记录的旧安装版本。构建产物不是安装或业务验收。Pro404、Muse403、普通文字7个非通过样本，以及原生picker/chatUI/生命周期/扫描OCR/音视频真实提供商仍保持原缺口，见[此前真实验收](../2026-10-13-luna-routing-implementation/REPORT.md)。没有以本轮离线绿灯替代这些项目。

只修有红灯或直接数据流证据的风险，没有扩大材料、接收方、联网参数、Retry或Schema。公开URL筛选是辅助检索/逐项证据边界，不是DNS解析或全软件网络地址安全证明。普通展示来源按C12另有既有格式规则，未改正常自身联网。

[源文件保护](source-protection.json)：1041既有保护文件中6个限定文件变化，其余1035不变。所有本轮原件和独立差异见`before-source/*.before`与`source-delta.patch`；只能审阅限定差异回退，禁止全仓reset/clean/stash。未commit/push。
