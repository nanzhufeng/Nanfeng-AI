# 2026-10-30 其他模型共用链路复查

状态：其余 22 模型的新文本＋Markdown 附件对话、完整终态、身份与保存重开全部通过；不扩大为原生媒体及联网搜索已通过。完整结果见 [all22-matrix.json](all22-matrix.json)。

## 基准与授权

- 用户确认 Opus 已打通，这是用户主设备反馈；历史 11:03 EOF 由哪一跳先关闭仍无确证，不倒改事故结论。
- 用户授权本轮新增实测上限 ¥10。旧保守累计 ¥4.412992 单独保留，新总门限 ¥14.412992；不重发原消息，不重试 UNKNOWN。
- Android SDK37 独立验收应用，沿用生产 Executor／Adapter／Transport 与各模型原配置；没有改模型、渠道、Key 分组或生产 Auto 优先级。没有主设备安装、清数据、UI 自动化或 connected Android 测试。

## 已证实问题与修复

| 首个错误分叉 | 修复 owner | 行为证据 |
| --- | --- | --- |
| 非流式 HTTP 正文已读完，关闭输入流 IOException 覆盖正文 | OfficialProviderChatTransport | 完整读取＋关闭失败仍交 Adapter；读取期间失败仍为 NetworkFailure |
| 附件清点的 NetworkFailure 丢弃已返回用量 | AttachmentSearchInventory、NormalChatOpenRouterExecutor | 失败 partial 进入原计量账本，不当作材料或完整回答 |
| 搜索辅助请求的 NetworkFailure 丢弃已返回用量 | RelayCoreWebSearchCompanion、ProviderResponseProjection | UNKNOWN 保留已知事实，不自动重试、不启动最终回答 |

三项红灯复现见 [red-check.json](red-check.json)。23 个目录模型三类协议的合成完成／断流检查，以及 22 个可选模型联网开关两种情况下的图片故障计量回归已通过；这些是本地行为测试。

新增共用投影只保留收到的正文／用量／模型／费用事实，不是终态或证据校验器。C12 领域正文已同步。

## 验证

- 最终第一候选：Android JVM 1577 项，1567 通过／10 skip／0 失败；Release、Lint、独立验收包构建通过。详见 [round1-build-tests.json](round1-build-tests.json)。
- 14 个模型真实 Markdown 附件问答完整返回，样本四项事实均正确，HTTP200／COMPLETED，返回身份完全匹配。14 会话进程重启后正文／模型／费用一致，重开 transport 网络调用 0。详见 [real-text-round1.json](real-text-round1.json)。
- 第一轮其余 8 项未发出：6 个 RelayCore 因验收器错误要求缺失上下文元数据而被拦；GLM-5.3 缺少当前估价；Auto-only Qwen3.6 Flash 的验收预期与实际 Auto 选择冲突。没有把验收器拦截当成上游失败；修正后只对这 8 项使用第二份新样本，全部 HTTP200／COMPLETED、四项事实及准确模型身份通过，见 [real-text-round2.json](real-text-round2.json)。
- 最终两轮合计 22 个模型、22 次真实物理调用，没有回放／自动重试。第一批 14 个的网络 owner／Adapter 在第二候选保持不变，第二候选只追加估价／验收前置修正；不为了重建重复收费。最终候选重开全部 22 会话，正文／身份／费用显示完全相等，重开网络调用 0。
- 最终 JVM 1580 项：1570 PASS／10 skip／0 failure；Release／Lint／独立验收包通过。Rust 相关 6、模型协议 16、合同治理 29 通过，见 [build-tests.json](build-tests.json)。当前正式候选非 Debug、同签名 1.0.32/code96；精确 hash 见 [candidate-identity.json](candidate-identity.json)，未覆盖主设备。
- 本轮新增保守占用 ¥0.221136；未知预留 0，旧轮累计与本轮授权分开。不是精确人民币扣款账单，使用收到的费用或真实 Token 本地估价并保守换算 USD。预算预检是保守估算，不是供应商强制账单封顶；无法建立费用边界的原生工具／媒体调用本轮不发送。
- 临时凭据 0、bootstrap 已消费、reverse 端口 0、原凭据 hash 不变；数据库私有备份保留。见 [cleanup-reopen.json](cleanup-reopen.json)。

## 附加风险

- OpenRouter 当前公开目录 10/10 固定 ID 可见，只证明目录存在。现价证据 [openrouter-catalog.json](openrouter-catalog.json)，来源 [OpenRouter models](https://openrouter.ai/api/v1/models)。Gemini 3.8 Flash 旧估价低于现价，已补双端按观察时间生效的估价边界；不改历史账本，不把观察时点当成厂商价格生效时点。
- Desktop Terra 估价分支遗漏已补；实际 Provider 返回费用仍优先于本地估价。
- GLM-5.3 已补双端从本轮观察时点开始的官方本地估价；历史未知价格仍未知。当前人民币文本价格已从 [智谱官方定价](https://docs.bigmodel.cn/cn/guide/start/pricing) 核验；安全结构化事实见 [zhipu-pricing.json](zhipu-pricing.json)。

## 冲突与裁决

- 旧费用合同称 GLM-5.3 当前直连价未知；本轮官方当前价格已可核验，双端与费用合同同步，设置合同改为引用同一权威。历史无价格依据的时段不回填为已知。
- 第一轮验收器把 provider context 缺失等同于文本预算无上界；实际文本已完整序列化，可按完整 UTF-8 字节、额外 framing 预留与输出 cap 计价，不再用未知 context 阻断，也不把声明 context 作为截小实际输入费用的手段。
- 旧交接仍写 Opus 手机新对话未验；最新用户明确确认打通，已更正为用户确认。历史 EOF 关闭发起方未知仍保留。

## 不能推出的结论

本轮 Markdown 问答不证明原生图片／PDF／音视频识读，也不证明联网搜索。费用边界未建立的调用被门禁阻止，不凭旧证据或本地回归写通过。Desktop 真实服务、本轮新候选主设备安装／业务验收均未运行。当前没有提交、推送或 GitHub 发布。
