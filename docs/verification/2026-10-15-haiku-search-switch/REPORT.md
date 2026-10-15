# Haiku联网与全模型开关语义修复（2026-10-15）

当前WORKTREE，HEAD仍为`d64d71026418b9325931b6cbbb94544b28a81309`。仅保留原有未提交增量，不提交／推送／正式覆盖安装。

## 用户规则与首个错误分叉

用户最终明确：联网由开关决定，开启就要实际执行搜索，关闭不搜索；不是按核验关键词判断，也不是只提供可选工具。“默认关闭／仅允许模型决定／只对核验措辞搜索”候选均已撤回。当前行为唯一正文是[C12](../../C12_MODEL_NETWORK_PARITY_CONTRACT.md)。

已确认两处应用分叉：

1. 原生图片进入强制inventory，未提取到公开事项即QUERY_EMPTY，随后原模型离线分析仍保留失败。这不是另换两个模型，也不能从截图断言Haiku服务整体不可用。
2. Android无搜索证明的完整回答曾只改变提示／外层结果，Attempt仍可能先写COMPLETED；Mac非流式通用JSON路径也未统一校验联网证明。双端现均在完成前判断，正文和费用保留；已有辅助检索资料的grounded最终回答不重复要求调用工具。

原截图没有同次原始服务回执，无法追溯该次究竟未执行、执行失败或来源未透传。此次用同类无公共事件的公开合成订单图复现，不冒充恢复了原截图的服务记录。

## 有效实现与协议裁决

- 开关统一到共用指令／证明门，不使用核验关键词筛选。Android所有路线共用`WebSearchGroundingPolicy`，覆盖维护目录21聊天部署；Mac覆盖流式与非流式。仅携带工具不算已执行，已证明搜索但证据不足不编造已核实。
- 无本地可读公共计划的、已支持的OpenRouter原生媒体保留原件，走同一请求的搜索与回答；存在可读公共计划的单／多／混合文件仍按原范围分批。未新增其他模型的媒体能力。
- Haiku同一OpenRouter Chat Completions使用`web plugin / engine=exa`入口检索并交原Haiku回答，不新增Luna、第二回答模型、Key或直接调用其他搜索API。返回结构化URL属于OpenRouter检索注入来源，不能标为Haiku原生工具事件。
- 真实候选对照证明：server tool＋required仍可无来源；native plugin一次有1来源，但Android及native＋required仍会跳过。不能把一次成功当稳定证明。有效入口方案双端各5结构化来源。原生工具／插件候选结果见[candidate-summary.json](candidate-summary.json)，失败未改成成功。
- 其他模型保持各自正常原生／Responses／RC Luna路线，不机械复制Haiku字段；统一的是开关、实际证明及终态，不声称21款全部重新收费验收。

OpenRouter官方说明[server tool](https://openrouter.ai/docs/guides/features/server-tools/web-search)：工具可由模型选择调用，web plugin在请求入口执行搜索；plugin已deprecated，因此本项是有实测依据的精确兼容例外，未来替换必须复验原场景。原有Gemini原生plugin例外未改engine，Muse Responses不加入已知不兼容required，Sol/Astra及RC接收方保持。

## 真实验收

使用仓库[公开合成订单PNG](../../../app/src/publicModelAcceptance/assets/public-search-fixtures/public-sample-order.png)，无个人数据。不是用户原附件、不是正式UI／系统picker验收。

| 平台 | 路径 | 结果 |
|---|---|---|
| Android | 隔离模拟器独立包，AppContainer→生产executor→实际HTTP→Room→进程重启回读 | Sent；原Haiku；1物理调用；5结构化来源；页脚¥0.055631；USD8278µ；正文／联网标记／金额重开一致，回读0网络 |
| Mac | 原生Rust生产普通聊天owner→实际HTTP→隔离SQLite→关闭重开 | COMPLETED；原Haiku；5结构化来源；USD7968µ；来源／正文／账本／完成状态重开一致 |

[安全结构化回执](live-summary.json)只保存字段／计量／计数，不保存Provider原始payload或用户正文。Android验收APK与正式包身份不同，SHA256记录在回执；主设备和正式Mac未安装。后续共用指令／完成门的整理只跑本地验证，没有为相同Haiku请求重复付费。

两条有效请求合计实收USD0.016246；前面候选另有真实费用，按候选回执保留，不能只报最终两次。Android第一次验收记录helper把流式媒体请求体当紧凑JSON解析，故障发生在transport返回后；该条金额UNKNOWN，不填0、不算业务通过。已修helper、使用新独立用例，未重发历史UNKNOWN用户任务。

## 本地验证与边界

[最终验证](verification.json)、[最终源码hash](source-hashes.json)记录实际运行、跳过和绑定范围。回归包含：开关on/off、21维护部署、无关键词文本／图片路由、Haiku原图保留、混合可读报告保留、缺证明的正文／费用／失败状态、grounded回答不重复搜索，以及标题／异步主完成既有回归。

已完成[标题真实双端验证](../2026-10-15-title-native-live/REPORT.md)继续有效：Qwen首选、DeepSeek独立和注入Qwen超时后的真实备用，共6次实际标题；不是Haiku联网证明，也不是正式主回答UI验收。

未做：其他21款的本轮全量付费矩阵、用户原始订单同次服务回执、全部附件格式／媒体／系统picker／生命周期及正式双端UI；不以此次短图成功关闭旧原件覆盖、Pro404、旧UNKNOWN／504等缺口。正式安装／发布另行记录。

凭据内存桥、一次性控制文件、此次ADB reverse和隔离应用进程已精确清理；原有其他测试数据及其他reverse不清理。无卸载／清数据，无connected AndroidTest，无真机操作。
