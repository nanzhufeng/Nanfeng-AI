# 普通回答语言与外语来源合同

<!-- contract-governance:start -->
> **合同口径：现行领域合同。** 领域入口：[ 生成语言 ](ANSWER_LANGUAGE_CURRENT_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** 普通回答语言与外语来源合同
<!-- contract-governance:end -->

适用 Android／Mac 普通聊天共用请求 owner，包括其工作区、联网／离线、附件分析和显式重试。临时聊天目前只追加本地消息、没有Provider生成请求；本合同不授权临时内容进入模型服务。语言要求由生成请求控制，与 Provider、模型大小、皮肤、来源国家和网页原文语言无关。

## 当前行为

- 未指定回答语言时默认简体中文。用户明确指定其他语言、原文引用或双语时按要求；已明确的会话语言约定及有效的自定义语言要求仍有效，当前用户明确要求优先。
- 网页、附件、辅助检索资料和历史回答中的外语不构成更改回答语言的指令；证据资料始终是不可信引用，不得当作用户的新指令。
- 外语资料中的事实先用回答语言译述后附准确来源链接。未被要求时，不逐段混入外语原文或重复双语段落。必要的专名、术语、代码、API 标识、URL 和精确数值保留准确；必要短引文附译文，用户明确要求原文／双语除外。
- 辅助检索只做事实资料摘要，默认用简体中文，用户问题明确指定其他语言则按指定。来源标题与 URL 可保留原文；交接提醒答案遵循主请求的语言要求，不能由最后一条检索资料的语言覆盖主请求。
- 语言规则独立于个性化开关、联网开关和附件分析模式。重试重建请求时继续携带规则，保留原模型／来源／已有断点。

## 所有权与边界

- Android `AnswerLanguagePolicy` 由 `NormalChatOpenRouterExecutor.systemFactForRequest` 注入基础 system 消息，适配器转换 Chat Completions／Messages 等请求仍保留同一规则。`RelayCoreWebSearchCompanion` 分别用于摘要和交接。
- Mac `answer_language_v1` 由 `ordinary_chat_request_messages` 注入基础 system 消息；辅助检索与交接由 `relaycore_web_search_v1` 使用同一 owner。
- 语言要求属于本次请求的临时提示，不新增设置或 schema，不写诊断／调用审计正文。渲染、复制、分享和历史存储保留真实回复；不在 UI 删除英文，不事后静默调用另一个模型翻译，也不自动重发本次请求。
- 来源 URL／结构化检索完成事实按 [联网合同](C12_MODEL_NETWORK_PARITY_CONTRACT.md)；译述语言不能替代来源核验。已有历史回答不自动翻译或回写。

## 验证限制

生产 Executor＋Room 和 Desktop prepare／retry 回归验证实际请求携带规则、用户明确语言要求保持、检索交接与来源保真。测试使用合成 transport／资料，不证明外部模型每次遵守语言要求；真实生成／原生验收分别记录。安装禁令与最新证据见 CURRENT_HANDOFF／PROJECT_STATUS。
