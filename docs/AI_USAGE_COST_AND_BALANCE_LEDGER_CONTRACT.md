# AI 用量、成本与余额台账合同

<!-- contract-governance:start -->
> **合同口径：现行领域合同。** 领域入口：[ 当前调用计量与币种 ](AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** 本机物理调用用量/币种/事实等级；余额和跨软件预算仅规划
<!-- contract-governance:end -->

## 当前实现：普通聊天与本机调用记录（2026-10-10）

本节是双端当前费用记录的权威规则；后面的余额、账单对账、预算和跨软件台账仍是未来规划。数据库版本以各存储owner为准，下文70→71是引入计量表的历史迁移，不是当前全库版本。

Gemini 4 Argon（OpenRouter）的新增估算快照由双端计费owner及Desktop投影同步维护，仍遵守供应商结算优先／未知费用不补零；目录核验日期、促销快照和未验边界见[接入记录](verification/2026-10-15-gemini-4-argon/REPORT.md)。

- Android：`ChatCallAccounting`记录一笔实际请求的稳定Call ID、root attempt、会话、ANSWER／RETRIEVAL／BACKGROUND、请求／返回模型、冻结渠道名称、时间、状态、输入／输出／缓存／思考用量、费用来源与已知上下文信息。生产`AndroidDirectChatCallAuditStore`和`AndroidContextSelectionAuditStore`由Room拥有；70→71只增元数据表／归因JSON字段，旧500条preferences／240条上下文文件按稳定ID导入，新记录不沿用旧裁剪上限。区域分库manifest通过显式conversationId保持CHAT／WORK所有权。
- Android计量JSON的时间单位统一为epoch毫秒；新记录和重放记录先按同一精度规范化再比较，纳秒差异不能误判同一Call的归属冲突。Call ID、root attempt、会话、role、Provider、请求模型及毫秒请求时间仍须一致；完成后的用量和金额不可改写。回复归因中的物理Call列表按同一规范比较，支持关闭重开后的幂等补写。
- 普通发送与手动重试共用本机异常收敛：审计pending未落库时不外发；收到完整回答后，单独计量写入失败不阻止正文完成和已知归因保存，并单列本机计量未完成。未预期异常保留收到的部分内容、将未完成发送标为UNKNOWN并结束本机生成态；完整正文不退回失败正文。协程取消保持取消语义，所有已载入的临时凭据在退出时清零；不自动重发UNKNOWN，不换Provider补救本机错误。
- Android助手归因保留主回答独立usage及`accountingCalls`，费用入口先按物理Call展开并以Call ID去重，再按下述检索合并规则生成只读明细；失败／取消／未知调用没有量化事实时仍保留状态。辅助检索成功、主回答失败时，检索计量仍可读。Luna是OpenRouter模型物理调用；旧智谱独立服务的serviceId/engineId与空模型字段仅解释旧记录，不用于新Luna发送。上下文来源在可见回答成功落库后关联本次全部未绑定记录；只存类别／来源标题／预算等元数据，不存正文。
- 双端费用明细按本次提问的稳定root attempt合并同一检索模型／冻结渠道的多笔RETRIEVAL，Luna多次检索只显示一条金额与合计用量；ANSWER仍单独列出，不并入检索。会话统计次数按合并后的明细条数计算。不同root／会话／模型／渠道或缺少root关联的历史记录不按时间猜配。Android使用`mergeConversationCostRecords`，Desktop原生IPC投影保留账本`attemptId`，ESM `groupRetrievalUsageRecords`负责只读分组；不修改物理账本、root页脚总额、后台请求次数或重试策略，不新增提示、入口或按钮。
- 合并检索的原币金额与Token仍逐Call保存；明细及类别／本机合计展开组内原始费用做人民币显示，先换算并加总后取整，不能先逐Call取整再累计。部分费用未知时保留已知金额及未知事实；Token任何成员缺失或求和溢出时该字段保持未知，明确零仍是零。失败／取消／pending状态不被合并改为成功，已完成检索而主回答失败仍可读；无法合计的原币金额不伪造混合币种结算。
- Desktop：复用`usage_ledger_v1`不可变账本，附属`usage_ledger_call_metadata`只保存白名单role／模型名称／reasoning／priceVersion／已知上下文计数；entry与metadata在同一事务写入。主回答和检索各有pending／final，完成事实引用自己的pending；读回只计量final与未对账pending。可见消息`accountingCalls`是同一账本的持久化投影，不创建额外对话消息。回答已保存而账本待恢复时，只恢复本地计量，不重新发送API；旧无metadata账本可补元数据，但原金额／币种／usage不因新价格或渠道被改写。
- 输出上限、服务暂停或 incomplete／failed 终态不能保存为成功。Android非流式解码保留部分正文和已返回用量并失败收敛；失败账缺少量化事实时保持未知，不用失败状态伪造零费用。 DeepSeek单次检索工具错误与回答终态的区分遵循[C12联网合同](C12_MODEL_NETWORK_PARITY_CONTRACT.md#2-联网语义与来源门禁)；原生来源和正常终态成立时保留完整回答及已返回Token，按冻结价格估算的金额仍标为`LOCAL_ESTIMATE`。
- 通用解码保留服务商返回的实际模型标识；OpenRouter裸模型名按本次请求的命名空间定价，禁止误套同名Relay折扣。DeepSeek缓存命中别名、JSON／SSE的缓存／思考Token、原生Messages输入的非缓存＋缓存读／创建总量分别按协议处理；重复usage帧更新事实，绝不逐帧累加。usage没返回就是未知；本地文本估算需保留明确估算来源，不能标成服务商实际用量。
- Android与Mac的RelayCore Chat Completions流式请求显式设置`stream_options.include_usage=true`，正常结束帧之后仍接收尾部usage。Gemini Pro／Flash与GPT的普通Completions路径一致；Claude Messages、GPT Responses沿用各自原生usage协议，非流式不发送stream_options。已返回Token按冻结渠道估算时仍为`LOCAL_ESTIMATE`，不称服务商结算；历史缺用量记录不按正文长度补造费用，检索费与原回答费仍分开记账并合计到页脚。
- 检索物理调用的已返回量化事实与检索成功独立：结算金额、币种、价格来源、缓存／推理Token和实际模型须从transport交给同一调用账本；缺来源、覆盖校验失败或缺终态不能把已返回金额改成本地估算，也不能把失败改为成功。未返回的字段仍未知；部分响应只在内存交接，不保存原始payload。
- Mac正常结束但缺联网证明的SSE及原生Messages／RC Responses JSON回执，按已知`WEB_SEARCH_NO_SOURCES`失败携带已解码回复与量化事实；普通回答与检索owner分别交给同一物理调用账本，失败正文／费用可在本地重开恢复，不再发送请求。失败保持FAILED、联网证明保持false；带回复的错误诊断不能打印生成正文／推理／原始payload。未知网络结果、缺终态、取消和缺量化字段仍不能伪造计量。
- OpenRouter Responses回复（包括空正文失败）保留返回的可确认USD费用。Chat finish_reason=error是失败而非正常stop；Mac相关失败即使只有已返回费用、没有Token，也须本地重开补账，保持FAILED与联网false，不触发网络。旧回执已经丢失的费用不能重建为零。
- 费用优先采用具有可确认币种的接口金额；否则匹配本次冻结模型／渠道、Token、调用时间和价格版本做`LOCAL_ESTIMATE`。Relay无币种的`usage.cost`不能当USD结算；六个已配置模型可按冻结分组价做本地估算。GLM-5.3 中国区直连使用已核验官方 8／28／2 CNY 每百万输入／输出／缓存 Token；只对 2026-10-30T09:16:30Z 及以后发生的调用启用本地估价，之前缺乏价格依据的历史仍未知，不借其他型号或国际渠道价补齐。OpenRouter Gemini 3.8 Flash 从本次目录观察时点 2026-10-30T08:52:02.077Z 起使用 0.825／4.125／0.0825 USD 每百万 Token，之前沿用既有历史版本。两个时点均是本地核验观察边界，不宣称厂商价格真实切换时间；已冻结账本不改写。价格依据及双端边界验证见[本轮复查](verification/2026-10-30-other-model-stream-audit/REPORT.md)。
- 千问自2026-10-10 UTC的新估算统一北京标准价原币种CNY，自动缓存命中按已核实适用价；历史已经保存的金额不重算。阿里云动态促销和真实账单仍可能不同，标准价只是估算。[官方价格](https://help.aliyun.com/zh/model-studio/model-pricing)、[上下文缓存](https://help.aliyun.com/zh/model-studio/context-cache)。DeepSeek时段估算用本次请求时间；跨工作日／周末或重开不改写已保存版本。
- 原币账本的主回答＋检索总金额仅在所有参与调用费用已知且币种相同时合计；任一未知、币种不一致或整数溢出时总额未知。单项已知金额仍单独展示，汇总另标费用未知次数。`0`仅代表明确返回或合法估算的零；不同币种不能直接加总。CNY展示是本地汇率参考，不能当Provider结算或余额。
- 对话回答页脚独立做只读人民币显示：每个物理Call去重后，将已知USD按现有参考汇率换算一次，与CNY金额汇总，固定四位小数；不改原币账本、金额或结算等级。部分调用未知时显示“¥已知部分 + 未知”，无已知金额时不伪造零：Android当前页脚在COMPLETE／FAILED显示“金额未知”，无费用的CANCELLED／PARTIAL不补占位；Desktop非生成Assistant显示“金额未知”，正在生成且无费用时隐藏。两端取消／部分状态的现有投影差异保留，本轮不为文档统一改业务行为。旧Relay名义币种仍按下节只读纠正，不能重复乘汇率。
- 计量与上下文审计仅在对应本机数据区域保存。本次没有新增云端台账／余额查询／账单对账、管理Key权限或跨软件能力；API真实账单、上游是否如实返回身份／usage需要独立授权和证据，协议fixture与本地持久化测试不替代这些事实。

### RelayCore 人民币实际计费（用户确认，长期规则）

- 南烛枫于2026-10-10明确确认：当前RelayCore账户虽显示美元符号／美元样式，实际充值及对应平台名义金额按人民币理解；三组Claude／GPT／Gemini共用此币种规则。界面的`$1`名义金额不是可再乘美元汇率的真实USD，人民币充值金额、余额、消耗、模型费用不能二次换算。
- Relay本地价表保留平台已确认名义数值及冻结分组倍率，原币种明确写`CNY`，价格版本标记`relaycore-cny-nominal-user-confirmed-2026-10-10-*`；人民币显示系数为1。金额是否已结算仍与币种分开，估算不冒称实付；API未明确证明是扣费字段的值仍未知，冲突的USD币种字段不当作真实美元结算。
- 旧`relaycore-verified-group-*`本地估算的USD标签在只读显示／汇总投影中纠正为CNY，数值不乘汇率、不重新算Token、不改原始账本。OpenRouter等真实美元费用保持原币种及一次换算；不得全局把USD都改CNY。
- 只有明确需要展示真实美元参考或判断美元预算时，人民币金额才按注明的汇率除以汇率一次。平台美元样式、真实币种、实际充值金额、名义账户余额及折扣倍率是不同事实；账户或充值兑换规则变更后重新核实，不根据符号猜。

验证入口：Android `ChatAccountingPersistenceTest`／`RetrievalCostGroupingTest`／`ChatCallAccountingTest`／`ProviderAdapterContractsTest`／`ConversationCostEstimatorTest`；Desktop `usage_ledger_v1`白名单事务重开测试、`ordinary_call_accounting_recovers_on_open_and_combines_helper_without_double_counting`实际SQLite owner测试、`chat-call-accounting.test.mjs`／`usage-ledger-display.test.mjs`，以及`fee_ledger_ipc_preserves_root_and_seven_original_calls_after_reopen`。动态结果见CURRENT_HANDOFF，不把历史测试数写成长期门禁。

## 未来规划：余额、账单对账、预算与跨软件台账

不编号 P6-I，避免阶段冲突；不读 Key、不查余额、不发 HTTP。集成时动态复核 [OpenRouter usage](https://openrouter.ai/docs/cookbook/administration/usage-accounting)、[current key](https://openrouter.ai/docs/api/api-reference/api-keys/get-current-key)、[credits](https://openrouter.ai/docs/api/api-reference/credits/get-credits)、[generation audit](https://openrouter.ai/docs/api/api-reference/generations/get-generation)。

- 事实等级：`PROVIDER_SETTLED`、`PROVIDER_RESPONSE_REPORTED`、`LOCAL_ESTIMATE`、`LOCAL_EXACT_ZERO`、`UNKNOWN`；余额：`PROVIDER_ACCOUNT_BALANCE`、`KEY_LIMIT_REMAINING`、`LOCAL_BUDGET_REMAINING`、`UNAVAILABLE`。估算绝不冒充结算或 Provider 余额。
- 每个物理 Provider attempt 追加不可变 entry：ledger/logical invocation/attempt ID、时间、application ID/version/platform/device class、workspace/project/conversation/task/message/branch、feature、provider、requested/actual model snapshot、router tier/rule、fallback/retry、finish/status、安全 request/generation ref、key alias hash、pricing/catalog/currency、输入/cache read-write/uncached/输出/reasoning/tool-web-media-audio usage、reported/upstream/estimated cost、balance ref、latency/cache/egress-sensitivity policy；绝不含 Prompt/回答/Key/URI/path。
- `nanfeng-ai.desktop`、`nanfeng-ai.android` 是稳定 app ID；未来南枫记/八字独立记录，跨端保留原始产生端。任务可多 attempts；以 idempotency/fingerprint 去重，stream 尾包缺 usage 为 `PENDING_RECONCILIATION`，generation 对账后追加 adjustment，不写零/不覆盖。
- UI：模型选择小额度状态；设置→用量支持软件/任务/会话/模型/时间下钻；对话只轻量本次/月度入口。普通 key 只能显示 Key 可用额度；账户 credits 仅 management key，权限不足/离线明确不可获取与上次同步。余额节流/后台/手动刷新且失败不影响对话，凭据层只给 scoped result。
- 原币种为事实；CNY 仅带汇率快照参考。Assistant/Tool 小字可显示模型、tokens、金额、cache savings；本地 exact 为“本地缓存 · $0 Provider”。支持区间与 software/model/provider/feature/task/conversation/cache/online-local 聚合、CSV/JSON 脱敏导出、可配 app-private 保留、月度聚合和 adjustment 链。
- 全局/软件/workspace/model 日月软硬预算，80/90/100 提醒；硬限默认阻止付费 attempt，本地与 exact 继续，人工覆盖明确。Router 用缓存后边际成本+质量；Prompt Cache 只从 ledger 的真实 cache tokens/cost 计算节省，exact zero 不伪造 Provider usage。P6-F 消息 metadata 预留 usage/cost；P6-G 写 attempt 事实。
- 对账链：response→generation/账单导入→adjustment，记录差异、价格、Provider 时区和结算延迟，并分实时估算/已对账。TEMP 可保留无内容的账务事实但不保留消息/Prompt，UI 明示。测试覆盖流/非流、缺 usage、retry/fallback、多 Provider、cache/local zero、cancel、重复、FX、余额权限/离线、跨端、导出脱敏、adjustment、预算。
