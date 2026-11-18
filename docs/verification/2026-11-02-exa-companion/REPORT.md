# Exa协作检索候选 · 2026-11-02

本轮在原main checkout、HEAD `1837908b57ff445af157b374d2905ddd17d15b8a`及全部WIP上增量实现；不提交、推送或发布。候选1.0.39/code103；Mac现装已保数据覆盖1.0.39；手机后续按“覆盖安装”指令已保数据覆盖1.0.39/code103并正常启动。构建、离线回放与真实Exa验收分别记录，不能相互替代。

## 行为与所有者

- Android由`RelayCoreWebSearchCompanion`、`NormalChatOpenRouterExecutor`及`ChatProviderAdapters`实现；Mac沿`relaycore_web_search_v1`、`desktop_ordinary_chat_v1`和`attachment_web_search_execution_v1`。普通发送／显式重试、普通文字／附件复用原入口，无新增常驻按钮。两端设置“功能审阅”同步原入口、保留建议与限量理由。
- Luna／OpenRouter模型、推理渠道OpenAI和原Key保持，检索工具改为Exa Fast。每物理请求：`max_uses=2`、`max_results=3`、`max_total_results=6`、`max_characters=1500`、`tool_choice=required`。普通问题既有最多两物理检索、首笔费用未知或超过10000微USD不补查的门禁保持；附件既有范围／分批预算不扩大。
- 参数依据为[OpenRouter官方server-tool文档](https://openrouter.ai/docs/guides/features/server-tools/web-search)，2026-11-02核验。真实代表短题已验证服务接受当前字段并返回结构化来源；不能据此推导全部问题／材料矩阵。次数／条数／长度限额不是人民币总费用硬上限，不保证每题低于0.1元。
- Android发送授权升v4，Desktop外层及独立Luna／Exa grant升`normal-chat-luna-exa-search-v2`，拒绝旧native授权，历史记录原值保留。Mac证据缓存增加引擎版本盐；Android指纹随v4变化。读旧记录不触发外发。
- 只有工具标记、没有结构化公开来源时，Android不再返回Ready；正文自写URL不能补证。最终回答仍为原选模型／Provider／Key，保留完整问题、所有计划事项、有效来源和明确缺口。取消、UNKNOWN、本地写入失败继续阻止后续外发。真实Android回执另暴露“缺口／资料充分、无关键缺口”标题未被解析的问题；双端现保守归为unverified，仍拒绝未查询、无来源、错ID，不升级为事实。
- 使用既有物理调用账本，服务回报检索USD与RC回答CNY分别保留，不二次换汇、未知不补零。无schema迁移，无正式数据库改写。

## 本轮证据

1. 红灯：Android新期望捕获native参数及旧v3授权；Node捕获旧Luna grant；Rust捕获native参数。第二轮红灯捕获无结构化来源却Ready，以及Mac旧native缓存仍可命中。
2. 绿灯：定向Android 87通过／4跳过；Rust范围执行25通过。共享`openrouter-exa-companion-v1.json`明确为合成fixture。Android使用真实流transport解码、执行器、Room，验证全部5事项→原选Opus回答→检索8500微USD与回答CNY分别入账→关闭重开内容／Attempt／账本一致。Rust消费同一fixture，验证精确限量、原Key与原模型、有效来源和8500微USD检索计量。
3. 完整回归：最终Android1602通过／12跳过；Rust485通过／46ignored；Node567通过／12跳过；Desktop lint、typecheck、inventory、build通过；模型维护16项通过、23聊天／1专项／7凭据分组一致。
4. 首轮完整回归发现4个当前grant fixture仍硬编码v3，以及1个Rust全模型断言仍期望native；修正当前测试输入，保留旧版本明确拒绝断言后复验通过。没有放宽来源、终态或授权门禁。
5. 最终Android release／lintRelease、同原正式签名、非debuggable通过；Mac原叶证书、DMG校验通过。旧真实回执两端定向离线重放各1通过，未运行旧五请求回放器。最终产物以`artifacts.json`为准，第一版候选不再用于覆盖。
6. 用户在本聊天明确批准一次合计¥1真实验收，随后要求已授权范围直接继续。Mac实际Exa→原Opus回答覆盖3/3事项；Android实际序列化请求经本机native凭据桥发送，真实流解码→原Opus回答→Room关闭重开通过。Android首轮因缺口标题失败，仅发生2次已知完成检索、未发送回答；用这两份真实回执离线修复并重放后，只新发尚未执行的原模型回答，没有再次搜索。该证据不是主设备UI自动化或手机真实点击验收。
7. 本轮共3次真实检索＋2次回答；已回报检索总USD0.029555。¥1账本占用¥0.63644，含RC回答未知实扣的¥0.40保守预留；8仅是测试预算转换率，不能当真实汇率或给RC二次换汇。所有请求终态已知，UNKNOWN请求0，未对后台逐笔实扣。原生凭据未离开owner；原始wire仅在私有诊断目录，测试桥已退出且无监听残留。
8. Mac正常退出后原签名覆盖1.0.39、正常启动，CHAT/WORK的8＋1个exchange行语义摘要、26＋0个Attempt数量一致；原UNKNOWN2条保持。手机最初离线，后续重新可读并按“覆盖安装”指令完成原签名1.0.39/code103保数据覆盖；安装后APK哈希精确匹配，firstInstallTime／UID／数据目录及CHAT-WORK诊断摘要不变，正常启动通过。ai-2回读PAUSED；原自然观察baseline/events保持，只在私有副本更新Mac期望版本，无新定时任务。

## 完整接续确认

已核对53的manifest SHA256 `997918da0564dd639701115bc73b7c1216b54ada42ed2aac9cf3fcb511efa1c1`，9项source_files哈希、checkout／branch／HEAD均吻合。原178项WIP保留，未发现另一写者。原20项逐项继承如下；差异是本轮已实际推进Exa离线实现，真实层仍待验。

| ID | 接续结论与适用边界 |
|---|---|
| R01 | 沿用历史资料→自动记忆实现及原报告；不重做。 |
| R02 | pending：双端连续两自然周期、实际Memory修订与手动指纹仍缺证。 |
| R03 | 沿用自动更新、保护手动编辑／停用／删除合同；自然层边界不变。 |
| R04 | 保持DeepSeek Flash→GLM-5.3 Flash→Qwen3.6 Flash；不用于替换普通回答。 |
| R05 | pending：原FAILED／RESPONSE_FORMAT／retry1正式来源恢复入库仍待正常窗口；离线修复不等于入库。 |
| R06 | 沿用双端1.0.38安装／签名／数据回执，不重复安装相同字节；本轮双端最终新候选均已原签名保数据覆盖并正常启动。 |
| R07 | pending：完整scope与离线最终对话已通过；真实短题代表协议已通过；手机UI、完整问题矩阵及旧截图精确Attempt仍缺证。 |
| R08 | 已授权，不重复询问；本轮双端Exa限量／授权版本实现，真实代表短题已通过，完整矩阵和手机UI不宣称全验。 |
| R09 | 离线来源→全部材料→原模型最终回答→费用／重开已验证；代表短题真实服务／Android实际wire已过；手机实体UI及Mac已装App内实际发送仍未验。 |
| R10 | 沿用RC实际CNY修补，不再次换汇。 |
| R11 | 沿用RC六模型离线价格／别名证据，不冒充六模型实时实扣全验。 |
| R12 | pending：App页脚与后台逐笔实扣未对账；旧近3元为预算缓冲，旧五次USD0.368465账不重跑。 |
| R13 | 沿用普通问题至多两物理检索及首笔费用门禁；本轮新增每请求Exa参数限量。 |
| R14 | 继续先旧回执／离线；先离线的约束已执行；本轮后续在用户明确¥1授权内完成5个物理调用。 |
| R15 | ai-2保持PAUSED，不恢复或创建替代项目定时跟进；软件自动记忆调度不改。 |
| R16 | UNKNOWN不重发、不静默换模型／Key／渠道；停止边界继续有效。 |
| R17 | pending：原Opus499／EOF发起方、跨端partial计量、全部模型／材料／联网矩阵、线上同步／网关、Windows／公证等历史缺口继续沿原归档。 |
| R18 | 保护全部WIP／未跟踪／忽略／旧1.0.37工作树及准确1.0.38证据，不reset／clean／stash。 |
| R19 | 沿用完整归档与精简入口；当前文档只更新新能力和未验边界。 |
| R20 | 本聊天完成全部ID、unknowns、constraints、do_not_repeat确认，已实际写入双端业务owner并完成测试；无仅口头接手。 |

## 未验与继续条件

- 真实代表短题服务已通过；未验证手机实体UI、Mac已装App内实际发送、原复杂金融问题与全部材料矩阵，不用短题通过关闭这些缺口。本轮¥1真实测试已完成，不再自动开启另一轮收费；原有UNKNOWN仍不重发。
- 主设备仍禁止connected类测试、Debug／仪器／uiautomator／UI自动化／卸载清数据。候选构建不扩大安装授权，不使用正式库制造夹具。
- 自动记忆原自然窗口为Mac 2026-11-03 01:18起，休眠可延后。保留既有private baseline/events，不改时间／正式库、不force-run、不恢复ai-2。
- 不运行两个已消耗旧付费入口，不设置`NFAI_QUESTION_REPLAY_ANSWER=1`，不运行会覆盖旧`replay-result.json`的旧五请求完整回放器。旧真实回执仅定向只读解析。
- 原截图精确Attempt、首笔native非硬金额限制、后台扣费及历史未知不因新合成测试关闭；真实金融正文／原始响应／凭据不进入Git。

回滚依据：本轮改前源码／配置／合同逐文件快照和哈希保存在私有诊断基线；`source-manifest.json`仅列公开仓库相对路径与哈希。需要回退时只处理本轮路径并先核验当前哈希，不能用整个工作树清理代替。
