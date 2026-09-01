# 南枫 AI P6-K ChatGPT / Claude ZIP 导入采纳合同

日期：2026-08-16  
状态：**Android 已实现 ZIP→严格文本→原子 Conversation/Message Tree 提交，并于 2026-08-28 补齐官方源关系附件恢复。2026-09-01 的 Room Schema 64 再增加独立、不含内容的永久身份账本与 `USER_DELETED` 墓碑：已导入内容即使业务行被用户删除，也不得被后续累积 ZIP 复活。稳定基线 `c1c9ae0`／Room Schema 56 又将数分钟附件恢复迁到持久化后台 job：状态、进度、失败类型与按会话 checkpoint 均不含正文，页面退出后可续跑和显式重试。新实包复查发现，`message.metadata.attachments[].id` 可与 ZIP entry 精确对应，`conversation_asset_file_names.json` 提供原始显示名；这推翻了 K7“全部无可证明关系”的旧结论。只有当前导出路径上、ID 唯一且 entry/hash/size 一致的附件才恢复为普通 `ContentBlock.Attachment`；无官方归属的 entry 仍不猜测。Desktop 仍保持其现有人工精确关联边界，不得由 Android 结论冒充已同步。**

## 0. 结论与范围

用户明确选择的 ChatGPT 或 Claude 官方数据导出 ZIP，直接导入为南枫 AI 的真实 `Conversation + Message Tree`。Android 对 ChatGPT 官方导出中有精确 source-message ID 归属的图片、视频、PDF、音频和文件，写入既有附件目录与消息树，从而复用原生对话渲染、搜索分组、预览、定位、下载与分享链路。附件本体保留在 app-private 导入 ZIP 中，按需 hash 校验后流式打开，不额外常驻复制数 GiB。无官方归属或冲突的 entry 继续保持未关联，不从文件名、时间或相邻消息猜测。账户资料与偏好默认不导入，不得覆盖帐号、Key、Provider、模型、同步或现有设置。

本合同不把“官方数据导出”为“稳定公开 JSON schema”的断言。OpenAI 官方说明确认个人导出是含聊天和其他账户数据的 ZIP；其 Edu 文档还明确大包可为 `conversations.json` 或多个编号会话 JSON，并可含会话资产与账户/会话元数据。Anthropic 官方说明 Claude 导出包含 conversation data 与 user data，但未公开本合同可据以实现的 ZIP 文件表、媒体路径或版本 manifest。因此 v1 不猜测目录、字段或关联关系：只有已登记的 provider+formatVersion+manifest 指纹才可进入解析；未知、缺失、冲突或超限包在清单阶段失败关闭。

格式依据：OpenAI [个人数据导出说明](https://help.openai.com/en/articles/7260999-how-do-i-export-my-data)、[ChatGPT Edu 导出说明](https://help.openai.com/en/articles/20001279-exporting-data-from-a-chatgpt-edu-workspace)；Anthropic [Claude 数据导出说明](https://support.anthropic.com/en/articles/9450526-how-can-i-export-my-claude-data)。这些说明只证明导出包类别与部分 ChatGPT 文件变体，不替代 provider 版本化 manifest/schema。

0 号增量没有读取、解压、枚举或复制任何用户 ZIP；也没有访问 Key、Provider、HTTP、OPPO、用户资料、现有数据库或设置。

## 1. 既有能力审计

| 实现 owner | Android 入口 | Desktop 入口 | Settings 入口 | 普通用户适合度 | 已有能力 / 缺口 | 风险 | 当前验证 |
|---|---|---|---|---|---|---|---|
| P6-H `ChatGptExportJsonAdapter` + 专属 task/receipt | `OpenDocument` 仅 `.json` | native dialog 仅 `.json` | 数据导入 | 适合“已解压 conversations.json” | 树、文本、逐项确认、私有 JSON 副本、幂等已存在；ZIP、编号 JSON 汇聚、账户资料、附件及排版 IR 均缺失 | 将官方 ZIP/资产当作已支持，或把其数据执行 | Android/desktop JSON 合同与历史证据；本轮新增 ZIP 拒绝回归 |
| P6-I `ClaudeExportJsonAdapter` + 专属 task/receipt | `OpenDocument` 仅 `.json` | native dialog 仅 `.json` | 数据导入 | 适合“已解压 conversations.json” | 严格 `chat_messages` 文本树和确认已存在；Claude ZIP manifest、资产、profile/personalization 均无依据且未实现 | 把未公开 schema 猜成稳定格式 | Android parser/任务机与 Desktop JSON owner 已审计；本轮新增 ZIP 拒绝回归 |
| P6-J `NanfengKnowledgeExportJsonAdapter` | `.json` | native dialog `.json` | 数据导入 | 不适用于第三方 ZIP | 是可复用的独立 adapter/task/receipt 模式；不是 ChatGPT/Claude ZIP owner | 混用来源表、receipt 或 provenance | 仅作为模式参照 |
| `ConversationRepository` / Desktop workspace exchange mutation | 已确认项写入正常历史；Android 可在导入 provenance 事务内恢复附件消息节点 | 已确认项写入 workspace `conversations` | 无直接入口 | 适合 | 真正的 Message Tree、来源标记、搜索/管理可复用；Android 普通消息不可变规则未放宽，只有已验证导入恢复 owner 可调整 source-confirmed 路径 | 平行会话真值或覆盖原会话 | Android Room 恢复合同测试；既有 P3/P6 JSON readback |
| `PrivateAttachmentRepository` / Desktop attachment asset owner | 现有 picker 私有副本；Android ZIP 资产使用不透明 archive-backed key | `import_desktop_conversation_attachment` | 无直接入口 | 适合（内部 owner） | Android 已复用 hash、MIME、引用、搜索与媒体预览；大文件按需校验并以 stream 交给播放/下载/分享 | zip-slip、压缩炸弹、路径/URI 泄漏、重复拷贝数 GiB | P6K ZIP archive attachment storage/Room 合同测试 |
| `ConversationAttachmentPreviewProjection` / Desktop image/pdf/video/audio/text preview | 消息附件预览与长按信息 | 原位 preview dialog/hover | 无直接入口 | 适合 | 图片、PDF、视频、音频、文本已有受控预览；Office/未知 MIME 仅安全文件卡，格式化文本 IR 不足 | 外部路径、HTML/Markdown 执行、全文件进 UI | P6-F2 A–E 合同 |
| Profile / personalization | 无第三方导入入口 | 无第三方导入入口 | 仅本产品既有设置 | 不适合直接自动导入 | 无跨服务商 profile/preferences 映射 owner；不得新建“同步设置”假象 | 覆盖用户本地偏好、搬入个人资料/安全字段 | 无；必须单独合同 |

### Desktop K3/K4 增量入口矩阵（2026-08-16）

| 入口 | 唯一 owner / 真值 | 直接结果 | 恢复与删除 | 明确不做 |
| --- | --- | --- | --- | --- |
| Settings → 数据 → ChatGPT ZIP / Claude ZIP | `DesktopWorkspaceStore::stage_p6k_zip_import_selected`；真实会话仍只写 `workspace_exchange.exchange_json` | 私有暂存→有界 ZIP 预检→provider v2 strict parser→逐会话事务提交；当前消息列表/树/搜索复用既有 renderer owner | `p6k_zip_import_*` task/provenance/receipt 可重开；失败项 retry/skip；删除批次把该批 conversation 标为 `deleted=true` 并移除本批 receipt/provenance | 不建立第二套 Conversation/Message schema；不保存外部路径；不复制无稳定归属媒体；不写 profile/personalization |
| 媒体 / profile 状态 | P6-K task projection，仅作安全统计 | 媒体逐 entry hash 后固定 `UNMAPPED_REJECTED`；profile 固定 `NOT_EVALUATED_NO_REGISTERED_SCHEMA / 0` | 状态随 task 回读，批次删除级联移除 | 不把文件名当消息归属，不伪造附件预览，不读取或保留 profile 原文 |

## 2. P6-K 分期架构与唯一所有权

### K0：格式登记与无副作用预检（已实现，2026-08-16）

- 唯一 owner：`ThirdPartyExportZipFormatRegistry`（纯领域）与 provider 专属 `ChatGptZipManifestAdapter` / `ClaudeZipManifestAdapter`。它们只接受调用方给出的有限 entry metadata/manifest bytes，不持有外部路径、URI、picker token、Key 或数据库。
- 当前仅登记“官方说明存在 ZIP、但官方未给出可实现 manifest schema”的事实，故 registry 没有可导入版本。所有 ZIP 版本为 `UNKNOWN_VERSION`，不进入解压、文本 JSON parser、会话/附件/profile 写入或 UI 确认。
- 最小安全门：用户显式选择 ZIP 后，先在隔离私有 staging 读取 central-directory 元数据；拒绝 symlink、绝对/`..` 路径、重复规范化路径、加密/多磁盘、超 entry 数、超单 entry/总解压大小、超压缩比、未知压缩法、无 manifest、重复 manifest、manifest/hash/version 不匹配。禁止全量解压、禁止写到用户目录。
- Android `AndroidP6KZipIntakeStore` 仅保存 app-private archive、task properties 与 entry metadata（安全 entry name、compressed/uncompressed bytes、MIME 推断、package hash）；Desktop SQLite 16 新增独立 `p6k_zip_import_tasks` / `p6k_zip_import_entries`。两端都不保存外部 path/URI，并停在 `WAITING_FORMAT_EVIDENCE / UNKNOWN_MANIFEST_SCHEMA`；取消/清除删除私有 archive 与 metadata，不产生 receipt、Conversation、Attachment 或 profile。

### K1：已登记 ZIP intake / manifest 与媒体归属（已实现）

- **已实现 ChatGPT 变体：** OpenAI 官方说明所列 ZIP 根 `conversations.json` 与实包验证的编号 `conversations-*.json`。entry 必须逐个通过既有 P6-H `ChatGptExportJsonAdapter`；其他 JSON 不进入 mapping。
- **P6-K graph normalization：** 一个用户选择 ZIP 是唯一 graph scope。编号 entry/candidate 的跨 parent 只在 source node/message ID 全局唯一、可唯一解析、全路径无环/可达且 parent 时间不晚于 child 时合并；只含结构节点的 parent 可折叠至最近可导入文本祖先。不可解析项单独拒绝，已合并片段标记为跳过，绝不把外部/歧义 parent 猜成 root。
- **已实现 Claude 变体：** 实包验证的 ZIP 根 `conversations.json` 数组，逐个通过既有 P6-I `ClaudeExportJsonAdapter`；其 38.4 MiB / 282 对话证据使严格边界调整为 64 MiB / 1,000 对话。没有公开媒体 manifest，故不推断附件关联。
- K1 只持久化 `p6k_zip_import_tasks/items/messages/asset_candidates/profile_candidates`，不复用 P6-H/I/J 表、provenance 或 receipt。
- ZIP 预检上限以实包证据收窄设定为 **6 GiB archive、6 GiB total、256 MiB single entry**。2026-08 用户选择的累积 ChatGPT ZIP 为约 4.91 GB archive／5.17 GB total，最大单 entry 仍约 247 MB；entry 只流式 hash，不解压到业务资产。
- P1 采用两阶段读取：前台只保存受限 ZIP 目录元数据；后台打开 ZIP 后先从官方 JSON 建立 source-confirmed 归属，再只对命中的 entry 流式校验 path/hash/size/MIME。2026-08-27 新包中，当前路径上 `metadata.attachments` 共 `855` 条记录、`854` 个唯一 ID；其中 `853` 个 entry 实际存在，`851` 个还有官方原始显示名。Android 只恢复这 `853` 个 source-confirmed 附件；其余 `822` 个候选与 `1` 个缺失 entry 不自动关联。禁止从文件名、时间或相邻消息猜关联。
- profile candidate 固定为 `NOT_EVALUATED_NO_REGISTERED_SCHEMA / 0`，不解析/保留 profile 原文。
- Android 早期 Schema 33→34→35 建立 K0 journal；稳定基线已连续迁移到 Schema 56。`RoomP6KZipImportTaskRepository` 保存导入事实，`RoomP6KZipAssetRecoveryJobRepository` 保存内容无关恢复状态；旧 app-private properties journal 在 IO 下迁入 Room 后删除，archive 本身保持 private staging。Settings 入口显示导入进度／恢复状态／结果与“删除导入批次”，而非确认按钮。

### K2：直接、原子、可恢复提交（已实现）

- `RoomP6KZipImportCommitStore` 是唯一 commit owner。选择→私有 staging→预检→候选→直接逐会话事务写 `Conversation + Message Tree + provenance + receipt`；失败项零会话写入，未关联资产/profile 零业务写入。
- 重复包仍按 source ID+package hash 幂等回读。**累积导出的跨包去重**以 provider source conversation ID 为主键：同源且会话级语义 hash 相同，只复用原本地会话并把最新批次 receipt/provenance 转移为 owner；同源且仅新增消息时，借助 source-message→本地 node provenance 在原会话内追加新节点，绝不新建重复会话。单条消息 identity 不含遍历序号，避免新增节点使后续旧节点的临时序号变化而误冲突；会话 hash 仍包含完整可见消息顺序。
- 任一旧 source message 被改写／删除、source→node provenance 缺失或歧义、树无法维持，均标为 `CONFLICT_REIMPORT` 并保持现有本地会话不变；不覆盖用户可见历史、草稿、附件、Key、Provider 或设置。删除旧批次不会删除已转移给新累计批次的会话；删除最新 owner 批次才按既有软删除和 receipt/provenance 撤销规则处理。
- ChatGPT 的 `multimodal_text` 只接纳明确 string 文本部件；非文本对象、`thoughts`／`reasoning_recap` 等不形成消息，也不能因单个非文本节点否决整段对话。结构但无文本的节点折叠到最近可导入文本祖先；完全没有可安全文本的对话保留为 `EMPTY_CONTENT`，不伪造空会话。
- `AndroidP6KZipAssetRecoveryScheduler` 是 P1 后台调度 owner；`RoomP6KZipMappedAssetLinkOwner` 每完成一个 source conversation 就更新 checkpoint。中断保留已提交会话与 job，续跑从下一个会话开始且不得重复挂载；失败只保存枚举化原因与时间，不保存正文、路径、文件名或外部 ID。
- Desktop Schema 16→17 仅追加 P6-K task/item/message/asset/profile/provenance/receipt journal；其 commit 将安全 title、TEXT blocks、parent/sibling/time 写入既有 `workspace_exchange`，重建既有本地搜索索引。没有新的会话/消息业务表或另一条渲染路径。

### K2.1：永久身份账本与删除墓碑（Android 已实现，2026-09-01）

- 对话身份为 `provider + export schema + official conversation ID`；消息与附件 occurrence 使用同一 scope 下的官方稳定 ID。无稳定 ID 时只允许版本化、可解释的强内容指纹与 source occurrence；任何冲突 fail-closed，不使用标题、文件名、时间或相邻位置猜测。
- `p6k_import_identity_ledger` 仅保存 provider/schema、HMAC-SHA-256 外部身份键、安全 SHA-256／size、本机绑定、批次、identity quality、state、delete reason 与 revision；禁止正文、标题、原始文件名、entry/path、URI 或 picker token。生产 HMAC secret 为随机 app-private 值，不进入账本或导入回执。
- 用户删除对话、回收站永久删除、批次删除或删除单个消息附件引用时，先在同一 Room 事务写 `USER_DELETED` 墓碑，再更新业务行／清理派生引用。对话删除墓碑所有后代 occurrence；删除单 occurrence 不墓碑共享资产字节或其他消息引用。当前不提供“恢复已删导入身份”按钮。
- 流程严格为 preflight→metadata inventory→identity diff→逐会话原子 commit→同事务 ledger/reference→completion marker。同对话的更新 ZIP 只追加未见官方消息；同消息 ID 内容改变或同资产 ID 的 hash/size 改变都是 identity conflict。相同资产 bytes 可被不同官方 ID 或多个显式 occurrence 安全复用。
- `p6k_import_batch_receipts` 记录 `imported_new_conversations/messages/attachments`、`reused_asset_bytes`、`skipped_existing`、`skipped_user_deleted`、`identity_conflicts`、`failed`。只有零失败才写 `COMPLETED + completedAt`；进程中断转 `UNKNOWN` 且不自动重放。Settings 只显示这些内容无关计数和“已跳过此前导入或主动删除的内容”。
- 升级回填只从仍存在的 exact provenance/message provenance/occurrence receipt 生成 HMAC 身份；旧版已删且证据不存在的历史固定标记 `legacyCoverageGap=true`，不伪造墓碑或猜测条目。

### K3：消息、排版、附件与预览

- `ConversationRepository` / Desktop workspace conversation mutation 仍是唯一消息树真值；节点保留角色、顺序、父子、时间、来源 opaque ID。新增受限 `ImportedRichTextBlock` 只保存安全、可渲染的文本层级（段落、代码、列表、引用、行内 emphasis/link label）；HTML、脚本、样式、远端 fetch、tool/thinking/执行指令一律文本化或 item-level 跳过。
- 附件只通过 `PrivateAttachmentRepository` / Desktop asset owner 私有复制，Message `ContentBlock.Attachment` 只持有 ID、MIME、安全显示名、大小、hash。既有 P6-F2 projection 负责 image/PDF/video/audio/text；未知/Office 先是安全文件卡，不假装可原位预览。
- Android 搜索删除只由 `ConversationMessageAttachmentRepository → DeletePersistedConversationAttachmentUseCase → PrivateAttachmentRepository` 处理：先校验当前路径上的精确 conversation/message/attachment/hash，原子移除该消息附件块和对应 ZIP occurrence receipt，并重建既有搜索索引；绝不删除消息节点、会话或其他引用。附件目录同时统计普通草稿、普通消息、临时聊天、ZIP occurrence receipt 与 `PENDING/UPLOADING/FAILED/UNKNOWN` 可重试上传；仅引用总数归零时删除受管私有文件与目录行。仅附件消息以“附件已删除”文本占位保留原节点；文件删除失败时保留目录行并如实报告，不能伪装物理清理成功。
- 每项归属缺失、MIME/content mismatch、重复 hash 的策略由专属 asset receipt 决定；无归属资产不导入、不成为孤儿文件。

### K4/K6：profile / personalization 映射

- `ThirdPartyProfilePersonalization` 是唯一 canonical IR；其 `nfai.third-party-profile-personalization/v1` sidecar 只允许显示名、语言、时区、公开简介、自定义指令、主题和通知开关。unknown key、超限、错误类型或敏感字段使**资料项**拒绝，不阻断安全文本会话。
- 两端 task 仅持久化状态与映射计数；`MAPPED_PENDING_OWNER_COMMIT` 只在 mapper→owner 的内存交接存在，正常回读为 `OWNER_COMMITTED`、`NO_SAFE_PROFILE_FIELDS`、`REJECTED_UNSAFE_PROFILE_SCHEMA`、`CONFLICT_PROFILE_REIMPORT` 或 `CONFLICT_PROFILE_OWNER`。Settings 只显示这些安全状态。2026-08-16 两份实包的只读分类均为 `NO_SAFE_PROFILE_FIELDS / 0`，不得以文件名或弱字段名猜测。
- 已批准 canonical 值已接入每端唯一 native settings owner：Android `RoomP6KProfilePersonalizationSettingsOwner` / Room 35→36 与 Desktop `p6k_profile_personalization_settings` / SQLite 17→18。owner transaction 同时写 value、profile candidate 状态、provenance 与 receipt；同 package/same canonical hash 只重放，不同 hash 或当前 owner 属于其他 task 则 conflict 关闭；删除 P6-K 批次会撤销该 task 的当前 profile 并清除其 receipt/provenance。profile task 不持久化值，真实包不因此重试。
- 绝不导入或覆盖密码、会话 cookie、MFA、付款/订阅、地址、联系人、组织/工作区权限、API Key、Provider endpoint、模型选择、联网许可、同步、账户身份或安全设置。原始 profile 字段不持久化；未映射字段显示“未导入”而不是静默丢失。

### K5：双端验证、恢复与真实包门

- Android：系统 ZIP picker→私有 staging→候选确认/跳过→force-stop/cold start→树/附件/预览/readback；Desktop：最新隔离 `.app` native picker→同链→完整退出重开。合成版本化 fixture 用于 parser/迁移/恢复；真实 ZIP 只在用户明确选中具体文件后运行，并先显示不写入的包摘要。
- Android 的真实验收只安装通过项目既有正式签名链构建的最新 APK，并只可 `install -r` 覆盖；签名链缺少既有外部材料时立即停止，不读取/导出/改写/新建密钥或环境变量、不输入密码，也不得以旧 APK、卸载、clear 或 DB 注入替代。
- Desktop 本次实包回归：先以合成跨文件 graph、隔离失败、重开、幂等与 soft-delete 证明归一化；再从已有 private ChatGPT batch 的 normal retry 直接提交，并从正常 picker 重选 Claude。回读只报告 task/receipt/conversation/message/media/profile 的安全聚合，永不截图或输出正文、ID、账户资料或附件名。
- 退出要分别覆盖 unknown-version reject、zip-slip/炸弹、部分会话/附件失败、取消、事务 rollback、重复/reimport、每种媒体预览、profile skip、迁移和 restart。OPPO 仍不在授权范围。
- P1 稳定记录：标准 JVM `819 tests / 0 failures / 0 errors / 3 skipped`；新包附件 Room 链 `tests=1, skipped=0, failures=0, errors=0`、`257.597s`。2026-08-31 又以用户明确选择的两份本机 ZIP 运行资产归属、跨包合并和附件全链三项 opt-in，结果为 `3 tests / 0 failures / 0 errors / 0 skipped`；不输出正文／附件名，不复制 ZIP。以上证明 checkpoint 代码与隔离 Room，不证明 OPPO 后台调度、系统约束或实际 UI 已闭环。

### K7：实包 message↔asset 只读采纳判定（已完成，2026-08-16）

- 审计只在两份已由用户选择并仍位于 Desktop app-private staging 的 ZIP 副本上运行；读取过程只计算 archive/entry/类型计数、资产 SHA-256 与 JSON 内的精确值关系，不输出或持久化正文、外部 ID、附件名、路径或账户资料。
- ChatGPT：733 个安全条目、14 个 JSON、719 个非 JSON 资产；7,629 个已解析 mapping message 对象中，精确 ZIP-entry-path 引用和精确资产 SHA-256 引用均为 0。资产精确值仅落在导出文件清单和逻辑文件目录，后者不是 message owner，不能推断或反向关联消息。
- Claude：4 个安全条目且均为 JSON，非 JSON 资产为 0；因此不存在可采纳的 message↔asset pair。
- 结论：没有一个 pair 同时满足“位于 provider 已解析 message scope、以资产完整 entry 或 SHA-256 作直接 identity、可在重解析中复验且不依赖文件名”的门槛。所有现有候选保持 `UNMAPPED_REJECTED`、空 source association，不复制字节、不写 attachment receipt/provenance、不调用 Android `PrivateAttachmentRepository` 或 Desktop attachment owner，也不改变消息 UI/预览。只有新的 provider+versioned message-scope identity 合同和合成 mapper→owner→receipt/revoke 覆盖，才可重新开启该增量。

### K8：未关联媒体人工精确关联（已实现，2026-08-16）

- staging 仍只保存 private archive 与匿名 manifest；不因解析、重开或 Settings 展示而批量解压、复制任何媒体。Settings 仅显示匿名媒体序号、MIME、大小和状态，以及匿名对话序号、角色、消息序号；不显示 entry 文件名、会话标题、正文、外部 ID 或内容。
- 用户必须先明确选择一个未关联资产和同一 ZIP 已导入会话中的一条真实 message；执行即关联，没有二次确认。owner 重验 task/provenance、entry path、大小、SHA-256、magic MIME、目标 message、每资产单次归属与每消息附件上限；相同 receipt 重放，改目标冲突关闭，失败只标记该资产可重试。
- Android `RoomP6KZipManualAssetLinkOwner` 只经既有 `PrivateAttachmentStore`/Conversation owner 写入；Desktop `p6k_zip_asset_link_receipts` 只经既有 `desktop_attachment_assets`/`desktop_conversation_attachments` 与 `workspace_exchange` 写入。两端复用已有 image/video/PDF attachment renderer，不建第二套媒体 UI。删除 batch 会撤销 receipt/provenance、软删除其会话并删除 private archive；Android 只有 Conversation、asset receipt 与 profile owner 全部撤销成功才删除 task/archive，任一失败保留完整 recovery entry 供用户重试，不能留下不可撤销会话。
- 合成 fixture 覆盖 PNG、MP4、PDF 在目标消息原位的附件 block、replay/reopen、重复/冲突关闭、失败资产不影响其余候选、batch delete 与失败撤销保留 recovery entry；Android 另从该 message block 回读既有 image/video/PDF projection。Room 36→37 migration 仅证明表结构与旧候选保留，不能替代这一媒体 owner/renderer 链。真实包没有触发人工操作，继续 `UNMAPPED_REJECTED`，不读取/展示其媒体内容。
- K9 验收审计补充：Android Settings 对 task 只显示 provider 与匿名批次序号，禁止显示选择的 ZIP 文件名；删除必须在 conversation、asset receipt、profile、private archive 与 legacy journal 全部成功撤销后才删除 task。任一失败保留 task/archive recovery entry，并显示可重试回执。该恢复规则不构成真实媒体关联证据。

## 3. 历史缺口与当前顺序

下方 K0–K5 是早期实施顺序，不能再被解释为全部仍未实现。稳定提交 `c1c9ae0` 已完成 P0 标准 JVM 收口和 P1 可续跑恢复；P2 及后续正在进行任务由用户明确排除，只有形成独立 checkpoint 后才更新本合同。

1. **最高优先级：K0 format registry + metadata-only envelope parser**。先为 OpenAI/Anthropic 各自建立可审核格式证据；没有 registry entry 不实现解压或 UI 承诺。
2. K1 的有界 ZIP staging 与 manifest/asset ownership；需要 Android 与 Rust 各自实现、同一拒绝矩阵。
3. K2 的专属 task/schema/atomic commit；不能从 P6-H/I JSON 表演进覆盖式复用。
4. K3 的富文本安全 IR、attachment receipt 与媒体 preview 接合；先覆盖 JPEG/PNG/WebP、PDF、视频、音频、UTF-8 text，其他 MIME 不伪装。
5. K4 仅在列出南枫 AI 实际可持久化目标后设计，默认全部 skip。
6. K5 的真实选择闭环，须用户明确选择具体 ZIP；当前不得索取、扫描或解压 ZIP。

## 4. 0 号增量验收

- 已审计 P6-H/I/J 的 JSON-only contract、Android/desktop task/receipt、P3-A/G/E 的 Message Tree/Attachment/Preview owner，以及双端 ZIP 缺口。
- 已证实当前 JSON parser 接口只接受字节 JSON，ZIP 不是被支持的替代输入；本增量以 Android parser 合同锁定原始 ZIP 签名仍被拒绝，防止文档优先级更新意外扩大旧入口。
- 未创建 ZIP 文件、未调用用户 picker、未读取任何用户 ZIP、未修改现有用户数据或设置。

K0 代码验证使用合成、无害 ZIP：Android strict inventory contracts 覆盖安全清单、路径遍历、重复大小写条目和压缩炸弹；Desktop Rust 覆盖同类清单、私有副本不保存外部路径、SQLite 重开与清除。真实 ZIP 必须由用户之后在系统 picker 明确选择；K1 前还需要脱敏样本 ZIP 或可公开的 provider/version/manifest schema。
