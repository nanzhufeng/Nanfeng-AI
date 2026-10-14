# DeepSeek双端终态修复与其他模型复核（2026-10-13）

截图中“有长回答却未完成／金额未知”的客户端误判已修复。实际回执证明，服务可先返回有效来源，再返回单次搜索额度耗尽，最后正常完成回答。用户要求继续核查其他模型；Sol/Astra同题已完成，Muse403已确定为服务端要求账号先完成18+年龄确认，仍不能宣称20款全通或所有请求稳定通过。正式OPPO/Mac未覆盖，仍是18:54旧包。

## 原因与修复

- Android原生Messages SSE把每一笔空／错误工具结果锁成整条FAILED；Mac在同一位置立即WEB_SEARCH_NO_SOURCES退出。现在保留先前来源，继续解析正文、Token和真实终态；原模型、服务、检索额度和重试策略不变。
- 双端JSON解析存在同类问题，也已统一为收集真正有效的原生来源。Android仅DeepSeek自身联网声明最终来源必须存在，全部失败仍FAILED／Incomplete并保留用量；普通Messages无搜索的回答保持原有语义。Mac要求来源的调用仍拒绝全程无有效来源，非搜索调用不伪造联网成功。
- `max_tokens`、`pause_turn`、全局Provider错误、缺终态和来源全空继续失败。旧失败回答不能只因正文完整就批量改成成功；不根据新价格重算历史金额。现行行为归[C12联网合同](../../C12_MODEL_NETWORK_PARITY_CONTRACT.md#2-联网语义与来源门禁)，费用归[台账合同](../../AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md)。

## 真实服务与持久化证据

| 层／样本 | 结果 | 验收边界 |
|---|---|---|
| Android截图同题旧实现 | RESPONSE_INCOMPLETE，48来源、输入69055／输出3565却被锁为FAILED | 真红，保留原回执 |
| Android截图同题流式修复 | 正常完成；五笔有结果搜索后，第六笔`max_uses_exceeded`，接着end_turn＋message_stop；¥0.068897，页脚四位¥0.0689 | 本机冻结价格估算LOCAL_ESTIMATE，不是提供商结算；Room零网络重开金额／正文／原模型／物理Call保持 |
| Mac截图同题 | DeepSeek Flash完整回答与原生来源；原生SQLite重开正文、来源、计量一致 | Rust owner，不是正式聊天UI |
| Android两个现用DeepSeek型号 | Flash／Pro实际联网完成，¥0.033830／¥0.136348；两会话零网络重开通过 | 公开文字问题，不替代附件矩阵 |
| Mac DeepSeek Pro | 实际联网完成，来源与SQLite重开一致 | 与Flash相同原服务，未替换模型 |
| Mac四款RC文字 | Luna检索→原Sonnet／Opus／Gemini Flash／Pro回答全部完成；原生来源在正文与两笔模型费用重开后保持 | 修复opt-in探针漏走companion；原生产分支未改。旧探针把回答自身native_sources为空当成失败，原exit101保留；按Luna真实原生来源零网络复核四项通过，没有重复付费发送 |
| OpenRouter Sonnet补核 | 原渠道联网证明、实际金额和零网络重开通过 | 旧矩阵无证明记录保留；没有修改自身联网路线 |

真实回执只保存无正文元数据：[DeepSeek](receipts.json)、[四款RC文字](rc-text-receipts.json)、[20型号文字样本索引](current-model-text-evidence.json)。私有正文与原Provider回执在`/tmp/nanfeng-deepseek-terminal-fix-20261013`；不入Git。

## 自动验证与边界

- Android全量1494项：1485PASS／9skip／0fail，assembleDebug与独立验收APK构建通过；流式、JSON、局部失败前后顺序、正常Messages无强制搜索、Token／模型保存及真正未完成有回归。
- Rust428PASS／33ignored／0fail。新增测试经有效红绿；JSON公开HTTP边界和原生SSE边界均覆盖。第一次JSON测试曾因fixture错误失败，修正后得到WEB_SEARCH_NO_SOURCES的真实红灯，再修生产解析变绿，不能把fixture错误作为根因证明。
- JSON新增边界是自动验证；真实服务证据为上述实际流式调用，不能把HTTP fixture当成提供商返回JSON的真实验收。晚补的最终来源强制校验已用新隔离包同截图题实际核验：50真实来源，末次max_uses_exceeded后end_turn＋message_stop完整，¥0.064549 LOCAL_ESTIMATE；该会话与最新Sol/Astra三条记录零网络重开通过。
- 本轮未改业务ESM、数据库Schema、菜单模型、价格、材料、账号同步或正常自身联网路线。前轮费用检索合并、Sol策略与本轮修复均尚未正式覆盖。Node本轮未重跑；前轮551PASS／12skip是历史层证据。
- 合同门已通过：25合同工具测试、114合同／40领域、模型20＋1／7凭据组；C12补入两个真实解析owner，合计122引用。源码增量与hash在同目录；临时凭据副本、隔离prefs、Bootstrap及reverse已清理并核验，隔离包已停止（[清理记录](cleanup.json)）；正式数据与原凭据owner未改，无connected测试、主设备Debug、清数据、卸载、commit或push。

## 待验证与仍有风险

- 20型号中19款已有双端真实文字联网成功样本，输入不全相同，不能推导每个问题永久成功。Muse双端403未通过；Sol既有成功样本与另一公开问题服务端504并存。
- 既有RC Gemini Pro附件404、Mac原周报NETWORK／UNKNOWN不自动重发；文字成功不能替代这些附件缺口。多附件／14格式历史证据仍按原报告范围适用；正式UI、picker、生命周期、媒体／扫描件未跑项目继续保留。
- 最新Sol／Astra同公开问题均完成（15.7／19.9秒），各24原生来源；实际wire两者均low推理、low搜索上下文、external_web_access=true、required、stream=true、store=false。原504回执继续保留，不能保证服务永久无超时。
- Muse最新正常App请求仍403，公开目录与当前Key允许目录均列出该模型；无搜索／64输出Token控制仍403。服务明确要求账号先完成18+年龄确认，这不是输出上限、Key无效或搜索工具参数的证据。没有代为声明年龄、修改账号条件或换模型绕过。用户需在OpenRouter本人账号完成确认后，再授权新的真实调用。[服务核查元数据](service-followup.json)不含原错误消息／原Prompt。
- 官方[Guardrails文档](https://openrouter.ai/docs/guides/features/guardrails/overview)说明403可来自访问与账号规则，不能仅凭该状态判Key无效；本次年龄条件由实际服务回执确认，不是从文档推测。官方[Muse页面](https://openrouter.ai/meta/muse-wonder)可作为账号确认入口；页面或目录列出模型不等于当前账号已满足调用条件。
