# 其他模型联网复查与修复

截至 2026-10-15T17:49:33+08:00，main / WORKTREE，底层 HEAD `5a52aa2a8928cc46bad136cb608fd1eb3b62517e`。用户已确认 Haiku 问题解决；本轮继续复查当前21个聊天模型并修正下列遗漏。联网／协议／别名修复已双端原签名保数据覆盖；末尾追加的Android思考保存修复亦已在USB重连后同证书保数据覆盖，精确APK读回一致。未提交／推送／发布。

## 已修正

1. **DeepSeek 最终回答被重复要求搜索。** Android Messages 流式与非流式解码没有区分检索阶段和已有检索回执后的最终回答，完整正文可能被判缺少来源／未完成。现在传入执行阶段选项：检索仍必须有真实来源，最终阶段仍要求完整终态，引用由原任务回执合入，不伪造新搜索。适用于 Pro／Flash；非流式主执行器、读取与协作入口均传递选项。
2. **Mac 非流式协议分发遗漏。** DeepSeek Messages、千问 Responses及RC Claude普通Messages曾落入Chat Completions的choices解码，产生格式错误。现在按请求实际协议解码；正文、终态、身份和计量仍校验，RC未验证币种的cost仍不作结算。相关原始单测使用了错误封包，已更新为真实Messages格式，身份与金额断言保留。
3. **RC Opus 既有别名误判。** 附件读取会拒绝同组已确认的 `claude-opus-5.5`／`claude-opus-5-5` 返回；Android最终身份提示也会错误称换模型。双端读取及Android最终提示复用已有规范化规则，未添加新别名、型号或渠道；不同Sonnet返回仍提示真实不匹配。

4. **完成后的思考保存入口误用。** Android 普通 `save(snapshot)` 不允许改写已存在消息，补写 Provider 思考因而失败。现在由完成运行专用 Room owner 同事务核对 invocation／事件／assistant／checkpoint，再追加可选思考或工具协议；保持正文、终态、费用、标题、草稿和活动时间，同值不重写，冲突／其他消息拒绝。普通消息不可变规则保留，无 Schema 变更。公开 executor→Room→重开回归先红后绿；此前两次付费回执不回写、不重复收费。

当前行为归 [C12](../../C12_MODEL_NETWORK_PARITY_CONTRACT.md)。普通OpenRouter流式与握手前有限恢复沿用上轮实现，没有通过换模型／Key／渠道兜底。

## 验证层级

- 当前21模型使用生产冷启动resolver／目录、路由策略与Adapter，联网开关各一次，共42种请求准备检查通过；这不是21款当前上游可用性的真实证明。
- 上述解码与别名问题均在生产公开边界先复现红灯，再修正转绿；JSON与SSE、来源要求、原模型、终态、用量以及不同型号仍被识别均覆盖。
- 最终 Android 1525 PASS／10 opt-in skip、326套件，正式构建与Release Lint通过；Rust457 PASS／36 ignored、0失败。21＋1／7与114／40维护门通过。
- Android真实独立应用使用原官方渠道、公开Markdown＋DOCX、每文件1事项、生产executor／HTTP／Room：DeepSeek Flash 12.7秒，GLM Flash 92.3秒，均2/2并有正文和来源。实际结果为 `SentWithNotice(REASONING_NOT_SAVED)`，该提示随后定位为本地不可变保存入口误用，原始结果保留；末尾追加修复只做本地执行器／Room重开验证，不冒充新真实服务思考保存回执。
- RC Gemini Flash仅1+1基础调用通过，无附件／搜索／Luna，因此不称联网业务通过。
- 以上三条正文／金额／联网状态重开一致，重开0网络调用。两条官方业务合计5次物理调用，RC Flash1次；页脚均为LOCAL_ESTIMATE，已展示估算小计 ¥0.056647，不能称Provider精确结算总费。RC Pro另有一条未确认请求及未知金额，未计零。
- 模拟器代理始终原 `null`；完成后清理临时凭据、一次性能力并停止隔离包，保留失败及账本。主设备没有Debug／仪器测试／connected任务，也没有自动触发生成。

## RC Gemini Pro 未闭环

历史同原Key／渠道／API ID有HTTP404。当前只读 `/models` HTTP200仍列出 `gemini-3.1-pro`，也列出preview；这不能证明生成成功，更不是把普通型号换成preview的依据。

本轮隔离监督脚本曾把第一条Flash阶段文件当整轮完成，提前停止下一条Pro控制。已保留中断样本：Room中Pro为SENDING，服务结果与费用未知；未重复发送。等待条件已修正为全部目标行＋最终cleanup完成，后续官方双模型完整结束。残留隔离加密凭据副本已精确移除，主应用凭据／正式数据没有变动。该Pro不得计为通过，也不能凭旧404断言本次仍返回404。

## 逐模型范围

| 模型预设 | 请求API ID | 本轮请求准备 | 本轮真实／用户证据 |
|---|---|---|---|
| `CLAUDE_OPUS_5_5` | `anthropic/claude-opus-5.5` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `CLAUDE_SONNET_5_5` | `anthropic/claude-sonnet-5.5` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `CLAUDE_HAIKU_5_5` | `anthropic/claude-haiku-5.5` | 本地2/2 | 用户已确认问题解决，本轮不重复收费 |
| `GPT_6_1_SOL` | `openai/gpt-6.1-sol` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `GPT_6_LUNA` | `openai/gpt-6-luna` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `GEMINI_3_8_FLASH` | `google/gemini-3.8-flash` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `DEEPSEEK_V4_PRO` | `deepseek-v4-pro` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `DEEPSEEK_V4_FLASH` | `deepseek-flash` | 本地2/2 | 附件联网 2/2项检索结果；重开一致 |
| `MUSE_WONDER` | `meta/muse-wonder` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `GEMINI_4_ARGON` | `google/gemini-4-argon` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `GLM_5_3` | `glm-5.3` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `GLM_5_3_FLASH` | `glm-5.3-flash` | 本地2/2 | 附件联网 2/2项检索结果；重开一致 |
| `QWEN_3_7_PLUS` | `qwen3.7-plus` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `QWEN_3_8_MAX` | `qwen3.8-max` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `RELAYCORE_OPUS_5_5` | `claude-opus-5.5` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `RELAYCORE_SONNET_5_5` | `claude-sonnet-5-5` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `RELAYCORE_GPT_6_1_SOL` | `gpt-6.1-sol` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `RELAYCORE_GPT_6_ASTRA` | `gpt-6-astra` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |
| `RELAYCORE_GEMINI_3_8_FLASH` | `gemini-3.8-flash` | 本地2/2 | 基础调用通过，非联网验收；重开一致 |
| `RELAYCORE_GEMINI_3_1_PRO` | `gemini-3.1-pro` | 本地2/2 | 未闭环：历史404；本轮控制中断且未知，未重发；目录仍列出ID |
| `QWEN_3_6_FLASH` | `qwen3.6-flash` | 本地2/2 | 本轮未新增收费复核；保留历史证据与缺口 |

旧资料只作历史范围参考：[Luna逐模型](../2026-10-13-luna-routing-implementation/REPORT.md)、[Gemini／Muse修复](../2026-10-14-gemini-muse-risk-fix/REPORT.md)。未新增全模型×全部附件格式付费矩阵，不能把本地42项或代表型号通过扩大为全部实时链路通过。

## 正式覆盖

- OPPO v1.0.30／94、非Debug、同原证书，实际安装APK拉回字节与候选一致；应用身份、首次安装时间及数据目录身份保持，无卸载／清数据，正式受保护数据库未绕权读取。
- 已覆盖的协议修复 APK SHA-256：`00a5ccec9919e6cfe85ec570e922a7bdc44a5edcc473bc97b1d95ca3371c4b58`。
- Mac v1.0.30 原证书覆盖，安装文件与候选一致；1595个数据文件新增／删除／改变均0，旧app保留可恢复。
- 最终1180份源码／配置／测试快照漂移0，最终候选已验签。Mac协议修复已覆盖；思考保存仅改变Android编译输入，Mac无需再重打。
- 未自动启动两端正式应用或生成主设备测试；新修复在主设备上的新增业务仍未验收。用户对Haiku的确认作为单独用户反馈证据保留。

原始回执、失败、状态库、日志、完整输入hash和安装／数据hash仅在本机私有目录；仓库只留[安全验证摘要](verification.json)及[源码摘要](source-hashes.json)。

### 最后追加的Android思考保存包

正式v1.0.30／94、非Debug、同证书，正式构建及Release Lint通过；SHA-256：`021e81e46089152221a9730b61f855f9fa2108086c6726babff9a1c243a134ca`。手机USB重连后已完成此最后增量的原签名保数据覆盖，实际APK拉回字节一致，应用身份、首次安装时间及数据目录身份保持；未启动应用或触发生成。此前协议修复覆盖证据独立保留。最新隔离APK可重开三条真实旧记录，正文／金额不变、0模型调用；不会补造旧记录丢失的思考。公开executor→Room→重开、幂等及拒绝其他消息／冲突内容的最新本地回归通过，未重复收费。
