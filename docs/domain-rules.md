# 南枫 AI 领域规则

> 本文保存南枫 AI 独有且稳定的业务规则。实现细节、动态进度和具体模型目录不属于本文。

## 1. 输入与草稿

1. 文本输入、Android 系统分享和图片都先规范化为 Capture Draft。
2. Capture Draft 必须区分用户输入、系统来源元数据、附件引用和 AI 后续生成字段。
3. 输入适配器不得直接写入正式 Knowledge Item，也不得自行调用 Provider。
4. 用户取消草稿时，不创建知识、调用或成功记录；已复制的临时私有附件按保留策略处理。

## 2. 图片

1. 图片进入业务流程后先复制到 App 私有空间，记录来源与 MIME 类型，再用于预览或外发。
2. 不把外部临时 URI 当作长期业务真值。
3. 图片发送前必须验证所选模型支持 Vision；不支持时在网络请求前给出可行动提示。
4. 图片不得因为选择完成而自动上传。
5. AI 识图结果只能进入 Generated Candidate，不得直接成为正式知识或其他业务事实。

## 3. 第三方外发确认

1. 每次外发前明确显示 Provider、实际 Model、将发送的文字与图片范围、可能费用。
2. 确认框默认未选中；用户主动确认后才可发出请求。
3. 一次确认只授权当前请求，不永久授权后续图片、附件或扩大后的内容。
4. 用户修改待发送内容、Provider 或 Model 后，需要重新确认。
5. 默认只发送完成当前任务所需的最小内容，能脱敏的个人标识先脱敏。
6. 真实 Provider 请求还必须显示并确认当前价格版本/货币及费用未知或估算语义；未确认的费用提示不能以 `0` 代替。
7. 真实服务验收必须绑定版本化 RunSpec、合成非敏感夹具、已验证 Registry Snapshot、预算上限和一次性 nonce；同一 nonce 不得用于不同 Spec 或重建后的第二次调用。
8. P2-M 一次真实文本验收只覆盖当次版本化 RunSpec 中冻结的合成非敏感夹具、已验证 OpenRouter 模型、费用上限和 `retryCount=0`；nonce 一旦发行或消费不得为相同 RunSpec 重新发行。v1 的 `p2l-text-organize-v1` 已消费且保持历史只读；现行 v2 详情以 `P2M_REAL_TEXT_EXECUTION_CONTRACT.md` 为准。图片与附件仍未授权。

## 4. Provider 与 Model

1. Provider、Model 和 Harness 是不同概念，不得用一个 Model 字符串替代三者。
2. V1 首个真实 Provider 为 OpenRouter，第一测试模型族为 Claude。
3. 具体 Claude Model ID、价格、上下文和能力来自实现时的当前 Registry，不作为永久业务规则。
4. 用户界面使用可理解的“旗舰 / 均衡 / 快速”预设，不把任意 Model ID 文本框作为普通入口。
5. 切换 Provider 或 Model 不删除其他本地配置，也不改写历史调用记录。
6. OpenRouter Adapter 只是一个实现；业务层不得依赖 OpenRouter 专有响应结构。

## 5. API Key

1. OpenRouter API Key 仅在本机安全存储，由 Android Keystore 保护。
2. Key 默认隐藏，允许用户主动显隐；不得以明文进入 Room、日志、截图、业务导出、调用记录或普通备份。
3. Key 不存在或不可解密时，状态为“待配置”，不得用空值尝试真实请求。
4. 鉴权失败不自动切换 Provider，也不泄露服务端原始响应或 Key 片段。
5. P2-K 生产 Adapter 默认关闭，在用户另行授权前不得读取 Key、构造 Authorization 或尝试推理 HTTP。
6. P2-L DryRun 只能询问 Credential 是否存在；不得通过该接口读取、解密、测试或记录 Key 内容。

## 6. AI 请求与结果

1. 每个 AI 请求拥有唯一请求编号、任务类型、Provider、Model、Harness 版本和确认时刻。
2. 结构化整理使用固定且版本化的输出合同；解析失败不得把原始 JSON 静默保存为知识正文。
3. AI 返回结果先成为 Generated Candidate；用户可以修改、取消或确认保存。
4. 保存只创建或更新 AI 专属结果，不覆盖用户原始输入、附件、人工编辑或其他来源事实。
5. 保存后的结果保留 Provider、Model、Prompt/Harness 版本、请求编号、生成时间和可用 Token 用量。

## 7. 知识保存与读取

1. Knowledge Item 只有在用户确认保存后产生。
2. 正式知识必须保留原始输入引用、图片引用、生成候选、用户最终编辑和溯源元数据之间的关系。
3. 列表、详情、搜索和导出读取同一事实；展示回退文本不得写回协议真值。
4. 删除、覆盖和未来迁移必须有明确用户动作和可恢复路径。
5. P2 最小列表只显示已确认保存的 Knowledge Item，按保存时间倒序和稳定 ID 排序；Candidate、草稿、账本与 fixture 不得冒充知识。
6. P2 详情可显示完整正式正文、来源类型/安全来源引用、Candidate/Invocation ID 与 Provider/Model/Harness 元数据；不得显示 Key、完整 Prompt、完整响应、原图或附件正文。

## 8. 调用记录与成本

1. 成功、失败、取消和确认门禁阻止都创建安全 Invocation Record；门禁阻止只保留 `BLOCKED` Task Run，绝不伪造 Provider Attempt。
2. 调用记录允许保存：时间、耗时、Provider、Model、Harness 版本、成功/失败、错误分类、输入/输出 Token、费用与价格版本。
3. 调用记录禁止保存：API Key、原始 Prompt、完整响应、原图、未脱敏个人资料或附件正文。
4. Provider 未返回的 Token 或费用字段保存为 `null`，不得伪造确定值。
5. OpenRouter API 消耗作为独立模型服务成本记录，不与 ChatGPT 或 Claude 会员订阅混为一项。
6. Invocation Ledger 是追加式本地运行事实；本阶段不提供删除入口。未来的保留或删除必须由用户动作和显式事务策略定义，且不得随着草稿、附件或知识删除而静默抹去账本。

## 9. 错误与重试

1. 错误至少区分鉴权、余额、限流、超时、网络、Provider 故障、格式、Schema、验证和上下文溢出。
2. 鉴权与余额错误不得盲目换模型重试。
3. Schema 或验证错误可以按任务合同重试或更换模型，但必须记录重试次数。
4. 失败不得产生正式 Knowledge Item；草稿和用户编辑必须保留。
5. 用户提示给出下一步，不展示密钥、原始敏感正文或不可理解的完整服务端错误。
6. 对可重试的 `408`、`429`、`5xx` 最多复用同一幂等 Key 重试一次；鉴权、余额、上下文溢出、格式和取消不得盲目重试。
7. 真实验收前的 DryRun 没有 Attempt；只有未来已授权且实际开始的调用才可消费 nonce 并创建 Attempt。

## 10. 对话与记忆

1. 对话、消息和分支是用户拥有的本地数据资产。
2. 编辑历史消息产生新分支，不静默改写既有分支。
3. Context Builder 只读取当前 Conversation 的根→当前叶路径；兄弟分支、草稿和已软删除会话不得混入。
4. 消息角色只允许 system、user、assistant、tool；内容块顺序稳定。部分输出只允许 assistant 并保存 checkpoint，不得伪装为完整成功。
5. 每条 assistant/tool 消息只保存安全 Invocation ID 关联；Provider/Model/Harness/Usage 的运行真值继续由 Invocation Ledger 所有，消息不复制 Key、Prompt、原始响应、Token 或费用。
6. Conversation 的 Project ID、Memory 来源、Tool 安全摘要、附件引用和恢复字段可保存为版本化合同，但 P3-A 不提前启用 Project、Memory、Tool 或 Agent 行为。
7. 长期记忆必须可见、可编辑、可删除、可暂停、可撤销，并保留来源。
8. 外部网页、图片、附件和工具结果不得自动写入长期记忆。
9. 密码、密钥、证件、银行卡和他人隐私不得自动进入长期记忆。
10. 会话归档、置顶和删除必须有明确用户动作；删除默认软删除，不能静默级联删除 Ledger 或仍被其他资产引用的附件。
11. 运行事件只接受版本化 `AiRuntimeEvent`；Provider chunk/JSON、完整 Prompt、原始响应和认证材料不得进入 UI、Conversation、Room runtime 事件表或日志。
12. 每个事件具稳定 eventId、Invocation、Message、sequence、时间和安全元数据；同指纹重放安全，同序号不同内容、断档、乱序和终态后事件必须拒绝。
13. 流式部分输出只保存在 assistant 消息；停止/失败保留部分并显式标为 `CANCELLED`/`FAILED`，无输出不能标记 `COMPLETE`。继续只能从当前 `CANCELLED`/`FAILED` 且有部分正文的 assistant 创建新子 assistant，不拼接或改写原部分事实；重试与换模型重答从当前 assistant 的同一 user 父创建新兄弟版本，旧回答始终可切回。
14. continue/retry/change-model 每次都创建新的 Invocation、稳定 intent 和安全 Attempt Lineage，记录前序 Invocation、来源/新消息及 Provider/Model/Harness/已验证 Registry 的安全 ID；不得复用旧 Invocation，也不得把正文、Key、Prompt、原始响应或认证材料写入 Lineage。价格未知为 `null`，不得以零代替。
15. Message Presentation 只投影已持久化 ContentBlock：Markdown/代码/链接、HTML 与未知语法均视为不可信输入；链接只可见不可自动打开，HTML/JS/URI handler/工具调用不得执行。展示 IR 与缓存不持久化且不得回写消息真值。
16. Conversation Draft 保存时去首尾空白、最多 12,000 字符；空草稿可以保存但不能发送。文本与附件引用独立判定，附件按稳定 ID 去重。发送成功才在同一事务清草稿；失败、取消、返回和重建均保留草稿。
17. P3-D 长会话使用稳定 MessageNode ID 的虚拟列表，当前路径以外的兄弟分支不得混入。没有真实设备和服务数据时不得编造 TTFT、帧率、内存、Token 或费用结论。
18. P3-E 的标题/置顶/归档/默认排序/筛选由 Conversation Management Domain 唯一解释；标题 trim 后为 1–120 Unicode code points，空白/控制字符/超界拒绝，ConversationId 不变。默认活动列表为 pinned、`updatedAt DESC`、`ConversationId ASC`；归档不删除或改写消息树、附件、Invocation。
19. 本地搜索只读同一 Room 的标题和当前根→叶路径 user/assistant 文本；trim + `Locale.ROOT` lowercase，空查询无结果，最多 50 条。隐藏兄弟、Key、Prompt、Provider 原始响应、Ledger 安全元数据和展示缓存一律不搜索。
20. Conversation Export 是独立 v1 的私有 Manifest + `conversation.json` 协议，默认仅当前路径；只含安全会话/消息/ContentBlock/Invocation 关联与附件元数据。Key、Authorization、内部 Prompt/路径、Provider 原始响应/chunk、缓存、隐藏分支、附件字节与 usage/cost 一律排除；只导出，不导入/分享。
21. P3-F 仅允许编辑当前路径上的纯文本 user 消息；编辑不可改写原节点，只能创建同父修订兄弟并切换 current leaf。全部 leaf（包括尚未生成 assistant 的 user leaf）必须可见、可切回；含附件/Tool 内容的 user 消息不提供编辑入口，且编辑不创建 Invocation、Usage、费用或 Provider Attempt。
22. P3-G 的 Attachment Domain 独占 App 私有路径、哈希、MIME、大小与受控读取；Conversation Draft/Message 只保存 `AttachmentId`、安全 MIME/显示名/大小/SHA-256，绝不保存 URI、grant、路径、EXIF、二进制或缩略图。加入对话固定为 local-only/no-egress，不是图片外发同意。
23. 对话图片仅允许 JPEG/PNG/WebP，最多 4 张、单张 20 MB、总计 40 MB、原图 40,000,000 像素；缩略图受私有 Repository 控制、最大边 512 且最多 2 MB。取消、拒绝、发送失败和重建不丢既有草稿；移除引用不删除资产，P3-G 不做 GC/二进制删除。
24. P3-H 的本地尝试历史只由 `ConversationAttemptHistoryProjection` 读取同会话 P3-C lineage 与对应 assistant MessageNode；缺失、跨会话、非 assistant、Invocation 不符或非本地 fixture 的行不得显示。稳定按创建时间倒序/Invocation ID 升序；当前 runtime 只能在 Invocation/Message 精确相同时提供本地事件 Token，`null` 显示未知，fixture 零费率不得伪装为真实 Provider 费用。历史只读，不重放、不删除、不写入任何 Provider/Invocation/Candidate/Knowledge 事实。
25. P4-A 的 Project Domain 是项目生命周期、标题/说明、置顶/归档、项目指令 revision、会话集合与 Knowledge scope 的唯一语义所有者。标题 trim 后 1–120 Unicode code points，说明最多 2,000；颜色/图标仅是项目列表语义，绝不影响 launcher 图标。
26. 项目指令仅为用户拥有的本地内容；每次实质更新追加 `source=USER`、连续 revision、时间与 SHA-256。空指令是明确的、可回读的清空 revision；相同内容不静默覆盖也不制造新 revision。不得保存 Prompt、Provider 原始响应、Key、Authorization 或外部路径。
27. Conversation 仅保存可空 `projectId`，每次同时最多属于一个 Project。归入/移出/迁移均须稳定 intent 的明确用户操作，保持消息树、Invocation、附件与既有导出不变。项目归档不级联归档 Conversation/Knowledge；P4-A 无项目删除入口。
28. Knowledge scope 只保存稳定 Knowledge ID 与可空 Project ID 的关联；`null` 为 global。不得复制正文、自动检索、自动记忆或将附件/网页/文档/Tool 结果提升为项目指令。
29. P4-G 的 Knowledge Relationship 只能由用户从两个既有 ACTIVE Knowledge 明确确认；`KnowledgeRelationshipDomain` 独占方向、对称性、scope、revision/hash 重验、高敏拒绝与重复边语义。RELATED、DUPLICATE_CANDIDATE、CONTRADICTS 为对称关系，SUPPORTS 保持用户选择的方向。
30. 两端只能同为 GLOBAL 或同属一个 Project；跨 Project、GLOBAL/Project 混合、隐藏、缺失、自指向、过期 endpoint revision/hash、活动重复边或任一端点高敏均整体拒绝。P4-F 仅是建议来源，用户仍逐项选类型并确认；关闭/拒绝不写关系。
31. 关系是独立本地审计事实：只保存端点稳定 ID、类型、范围、状态、intent/revision 与时间，撤销为软状态并追加修订。关系不得复制正文、URI、路径、附件或敏感值，也不得自动进入 Context、Prompt、Export、Provider、同步或诊断。
29. P4-A 的 `ProjectContextSnapshot / InstructionResolution` 是纯本地审计 IR：固定 `SYSTEM > SAFETY > PROJECT > CONVERSATION > CURRENT_USER`；项目指令不能覆盖系统或安全规则。它不得构造真实 Prompt、RunSpec、Authorization、Provider HTTP 或 Invocation 历史。
30. P4-B 的 `ContextSelectionDomain → ReadContextSelectionUseCase` 是唯一 Context 选择入口：它仅输出当前 Conversation 根→当前叶路径与 Project revision/hash 的 metadata-only 快照；草稿、兄弟分支、附件、Tool、Knowledge、Memory、检索、摘要/压缩与缓存均不得隐式混入。快照不含正文、Prompt、Token、RunSpec、Authorization、Provider 请求、Invocation 或附件字节；缺失会话/Project 必须显式拒绝，不能降级为任意全局资料。
31. P4-C 的 `MemoryDomain → ManageMemoryUseCase → MemoryRepository` 是长期 Memory 的唯一语义/写入链。Memory 只能由用户明确创建、编辑、暂停/恢复、删除、批量删除或解决确定性冲突；不得由 AI、网页、附件、Tool、Provider payload 或 `memorySources` 自动产生、静默保存或自动进入 Context。
32. Memory 必须使用稳定 ID、append-only revision、`GLOBAL/PROJECT/CONVERSATION` 互斥 scope、来源稳定 ID/摘要与 `ACTIVE/PAUSED/DELETED` 状态。Project/Conversation 的归档、移出或生命周期变化不级联删除 Memory；删除是软删除且历史保持审计。
33. P4-D 的 `ContextBodySelectionDomain → ReadExplicitContextBodyUseCase` 是唯一 L0-L2 正文选择入口：所有来源默认关闭，GLOBAL/当前 Project/当前 Conversation 的 ACTIVE Memory 只能由用户逐项选择；绝不读取或写入 `memorySources`。L2 只投影当前根→叶的 Text，草稿、兄弟、附件、Tool 和 L3 操作轨迹正文保持排除。
34. `ExplicitContextBodySnapshot` 是瞬时本机 IR，不是 Prompt、Provider payload 或 RunSpec；不得持久化、导出、记账、同步或记录诊断正文。选择前后必须重验 P4-B 当前会话/项目一致性，敏感正文整体拒绝，生产 egress 始终 Disabled。
35. P4-E 的 KnowledgeDomain 是正式 Knowledge 的编辑、标签、GLOBAL/PROJECT scope、`ACTIVE/ARCHIVED/DELETED` 与 append-only revision 唯一所有者；搜索只能读取同一 Room 真值的标题/正文/标签/来源投影，按稳定顺序返回有界 snippet，不使用自动检索、embedding 或 vector DB。
36. Knowledge 进入 Context 默认关闭。用户必须先搜索、逐项选择，随后在预览前重验 ACTIVE、GLOBAL/当前 Project、revision/hash、P4-B metadata 与高敏门禁；关闭、会话切换和进程重建都丢弃选择，绝不写入 `memorySources`、Room、导出或账本。
37. P4-J 的本地 Context 摘要/压缩只能消费 P4-D 已由用户逐项选择且安全重验通过的瞬时正文；`LocalContextCompressionDomain → ReadLocalContextCompressionUseCase` 独占策略、预算、截断和来源证据。它是版本化抽取式压缩，不得声称模型语义理解、生成事实或回答质量。
38. 压缩条目必须保留稳定来源 ID、layer/kind、原始 hash/revision、压缩 hash、原始/保留/省略 Unicode code point 数和策略版本；只保留确定性前后原文片段。草稿、兄弟、附件、Tool、URI/路径、网页、运行事件、缓存前缀与 `memorySources` 均不得读取或自动混入。
39. P4-J 输出只在内存中短暂存在，不写 Room、Schema、Export、Ledger、日志、诊断或缓存，也不是 Prompt、Provider payload 或 RunSpec。任何 P4-D 拒绝、无效策略/预算、重复来源或压缩后高敏命中必须整体拒绝；`OpenRouterEgressPolicy.Disabled` 保持。
40. P4-M 的 `LocalActionTraceDomain → ReadExplicitLocalActionTraceUseCase` 是 L3 唯一元数据入口：先后重验 P4-B Conversation/Project/branch metadata 与 P3-C lineage；仅当前会话、同会话 assistant、Invocation 精确匹配、终态 local fixture 的 `CONTINUE/RETRY/CHANGE_MODEL` 可由用户明确选择。编辑与切分支没有 append-only 用户动作谱系，必须排除，不能猜测或补写。
41. `LocalActionTraceSnapshot` 只保留格式、Conversation/intent/Invocation 的 SHA-256 安全摘要、动作种类、终态、时间和来源版本，固定顺序且最多 12 条；不含任何正文、Provider/model payload、runtime event/chunk、Token/cost、附件、URI、路径、命令、日志、网页或缓存前缀。它不属于 P4-D 正文，不得进入 P4-J/K、Prompt、RunSpec、Harness、Invocation、Export、同步、日志、诊断或生产 Eval；默认关闭且选择/预览仅存 ViewModel 内存。
33. 密码、API Key、Authorization/Bearer、恢复码和完整支付卡号等高敏正文必须在写库前拒绝；不得保存被拒正文、Prompt、Key、Provider payload、URI、路径或附件字节。相同 scope 的规范化内容重复幂等；同概念不同内容必须产生待用户选择的 conflict，不能静默覆盖。

## 11. 本地优先与 AI Hub

1. V1 为本地单用户、Provider 直连，不实现登录、云同步、远程 BFF 或远程 AI Hub。
2. 本地业务数据库始终是 V1 真值。
3. 未来 AI Hub 只能增强模型目录、路由、健康与安全运行统计，不能成为业务数据库或应用启动依赖。
4. 未来云能力不得静默改变本地数据、密钥或外发确认语义。

## 12. 导出

1. V1 提供版本化的最小导出协议。
2. 导出保留用户最终内容、来源、附件引用、Provider/Model 溯源和必要协议版本。
3. API Key、原始诊断、未脱敏请求与响应不进入业务导出。
4. 导出协议值与 UI 展示文本分离，以便未来 Desktop / Web 精确保真读取。
5. P2-I 的本地包固定使用 Manifest + `knowledge.json` + SHA-256；重复导出不得覆盖旧包，成功必须以最终文件回读为准，附件只输出逻辑 ID/MIME/大小/哈希元数据。

## 13. 跨端与迁移

1. Android、Desktop、未来同步和南枫生态只能依赖稳定 ID、版本化协议和领域语义，不依赖 Android 表名、绝对文件路径或中文展示文案。
2. 导出包必须包含 Manifest、结构化数据、附件引用、协议版本和哈希清单；导入后通过回读和哈希确认。
3. Schema 升级失败不得以清库代替迁移；不支持的旧协议要明确提示并保留原数据。
4. Desktop 不直接把 Android 数据库文件当跨端共享协议。

## 14. 未来账号与加密同步

1. 账号与同步不进入 V1；启用后本机 Room 数据库仍是业务真值，云端只保存版本化 AES-GCM 加密快照。
2. Google Token、Client Secret、`service_role`、恢复码、Provider Key、头像缓存和业务明文不得进入云端文档、日志或诊断包。
3. 空设备恢复、本机非空、远端非空、账号切换、冲突和退出清空都需要明确数据方向，禁止后台静默覆盖。
4. 退出默认只停止会话和同步并保留本地数据；清空本地数据是独立危险动作。

## 15. Agent 与南枫生态

1. Agent 按只读分析、草稿候选、可撤销本地写入和高风险外部动作逐级开放权限。
2. 工具结果、网页和附件是不可信输入，不能提升权限或自动写入长期记忆。
3. 高风险参数和结果由确定性代码校验，执行前预览，执行后保留审计与可用回滚。
4. 其他南枫应用继续拥有自己的业务数据库和 Provider Framework；南枫 AI 不跨应用直接改库。
5. 跨应用写入由目标应用的稳定入口执行，必须保留来源、用户确认、结果回读和撤销边界。
