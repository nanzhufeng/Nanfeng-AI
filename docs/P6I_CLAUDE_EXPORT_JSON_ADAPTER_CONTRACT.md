# 南枫 AI P6-I Claude export JSON Adapter 合同

日期：2026-08-14  
状态：实施中；本文件是 P6-I 的唯一合同，不复用或重新打开 P6-H。

## 目标与唯一链

```text
Desktop 原生文件选择器 / Android OpenDocument
→ 有界 UTF-8 读取与 app-private 原始 JSON 副本
→ ClaudeExportJsonAdapter（惰性、严格、不执行）
→ adapter 专属 ImportTask / ImportItem
→ 用户逐会话确认 / 跳过 / 取消 / 重试
→ Conversation owner 的单一事务
→ Conversation + Message Tree + CLAUDE_EXPORT provenance + receipt
```

仅支持 Claude 个人 data export 中用户自行解压并明确选中的顶层 `conversations.json`。不接受 ZIP、`users.json`、`projects.json`、目录扫描、浏览器配置、账号数据或 API/Compliance 响应；原文件可改名但必须以 `.json` 结尾。最大 32 MiB、最多 200 会话、每会话最多 2,000 条消息、JSON 最大 24 层、单个文本字段最多 120,000 code points。

## 严格 Claude v1 映射

- 顶层必须为数组。每个会话必须有安全 `uuid`、可空安全 `name`、ISO-8601 `created_at`/`updated_at` 和非空 `chat_messages` 数组；未知字段安全忽略，不能降低已声明字段的类型要求。
- 每条消息必须有安全 `uuid`、`sender`、ISO-8601 `created_at` 和 `text` 或 `content` 文本块。仅 `human` 映射 `USER`，`assistant` 映射 `ASSISTANT`；`tool` 仅在有安全文本摘要时映射 `TOOL`，其他 sender、非文本 content、空文本、无效时间或重复 source ID 是该会话可见失败，不影响同包其他候选。
- `parent_message_uuid` 可为 `null` 或同会话安全 UUID；它建立 Message Tree。根必须唯一、不允许悬空父、环或不可达节点。兄弟顺序以原 `chat_messages` 数组顺序稳定保存；不猜测 current leaf，也不将附件、artifact、thinking、tool input、HTML/Markdown 解释或执行。
- 会话/消息原 ID 永不作为本地 ID。成功 commit 写 `sourceSystem=CLAUDE_EXPORT`、source conversation/message opaque ID、`claude-export-json`/v1、导入时间、content hash 与 package hash；可见 UI 明示“从 Claude 导入 · 本地静态文本”，绝不成为 Provider Invocation、model override、路由、费用或调用记录。

## 安全、私有副本与恢复

- 外部 URI、绝对路径、权限 token、Key、Authorization、cookie、附件字节与可恢复外部句柄不持久化；私有副本成功后立即丢弃它们。任务只保存 ID、storage key、安全显示名/MIME/大小、package hash、adapter/version、状态与安全失败码。
- 状态为 `SELECTED → PRIVATE_COPIED → PARSING → AWAITING_CONFIRMATION → PARTIALLY_COMPLETED / COMPLETED`，另有 `FAILED`、`CANCELLED`。`PARSING` 重启后标记 `FAILED/INTERRUPTED`，只从 app-private 副本重试；取消选择不建任务。
- 同一 `source conversation UUID + package hash` 重放回读既有 ConversationId；相同 source ID 但不同 package hash 显示 `CONFLICT_REIMPORT`，必须再次确认。确认项与 Conversation/Message Tree/provenance/receipt 在同一原子事务；跳过、取消、失败和中断绝不生成会话。来源撤销仅标记 provenance/revoked，不删除或改写用户本地编辑。
- TEMPORARY 不显示入口、不接收结果、不进入导入导出。成功后是普通本地会话，可被已有历史、搜索、置顶/归档、继续对话和显式 Context 使用。

## 平台边界与验收

- Android 仅 `ActivityResultContracts.OpenDocument` 的 JSON MIME；Desktop 仅 Tauri native dialog。Settings Center → 数据 → 数据导入保持 chat-first 的独立入口，不在主对话或 Work 中新增复杂面板。
- Android Schema 27→28、Desktop SQLite `user_version` 13→14 仅新增 P6-I 私有 task/item/message/provenance/receipt 表与索引；25→26 已由既有自动标题使用，不能重占。不改写 P6-H、Conversation、TEMP、P6-G 或已完成 adapter 表，禁止 destructive migration/wipe。
- 自动合同必须覆盖 UTF-8/BOM、大小、深度、重复 key、无效 JSON、顶层/字段类型、ISO 时间、树/顺序、未知字段、非文本内容、部分失败、private-copy 排除、确认/跳过/取消/中断重试、幂等/冲突、rollback、迁移、重启与 provenance 不进入 Invocation/route/egress。
- 最终退出必须分别在 Desktop 最新隔离 `.app` 和 Android `emulator-5554` 完成：系统 picker → private copy → Alpha 确认/Skip 跳过 → 完整关闭/force-stop 重开读回 → Settings 导出 → 严格回读与 SHA-256。Android 另需正式签名 `install -r` 与实际 `base.apk` hash。测试/构建/解析成功均不能替代该门。

## 明确不在本阶段

不读 Key、不构造 Prompt/RunSpec、不发送 Provider HTTP、不执行模型、Agent 或 tool；不操作 OPPO、不改图标、不实现知识库 Adapter、同步、发布或实际 Claude 账号导出动作。 

## 2026-08-15 实施检查点：纯严格解析器与领域任务机（未完成 P6-I）

- Android domain 已新增无 I/O 的 `ClaudeExportJsonAdapter`、私有资产/任务/原子 commit 端口与定向 JVM 合同。它只消费调用方给出的字节：严格拒绝非 UTF-8、重复 key、过深/超长 JSON 与非数组包；对包内各会话分别校验 UUID、ISO 时间、`chat_messages` 上限、sender、纯文本 `text/content`、重复 message UUID 与唯一可达树。
- 合法项只输出 source UUID、父 UUID、数组稳定的同级顺序、`USER/ASSISTANT/TOOL`、惰性文本、时间与内容 hash；未知字段不执行。单个会话错误保留为 item failure，不吞没相邻合法候选；包级语法/安全错误整体失败关闭。
- 纯领域任务机现在可由 opaque private-asset 和原子 Conversation commit ports 驱动 `SELECTED → PRIVATE_COPIED → PARSING → AWAITING_CONFIRMATION`，并定义确认/跳过/取消/重试/中断恢复及 item-level 状态；没有 Android URI、外部路径、Key 或 DAO 入口。
- Android 已接入 app-private `claude-export-import-assets/v1`、Room 27→28 独立 queue/item/message/provenance/receipt 表、唯一原子 Conversation commit owner 与 Settings → 数据导入 → `OpenDocument` JSON 入口。安全 AOSP API 35 fixture 已实际完成 DocumentsUI 选择 → Alpha 确认 / Skip 跳过 → `COMPLETED` → force-stop/cold-start 后任务详情读回；普通历史回读 Alpha 的 USER/ASSISTANT 文本和“从 Claude 导入 · 本地静态文本”标记。Debug APK 与设备 `base.apk` SHA-256 同为 `4ce329b1b60d860e75f70f0018ff40e5ee6ec7e4a0f6d8279fce97eb934a3c1b`，v2/v3 与既定证书通过。
- Android 尚未完成本 Adapter 的 Settings 导出严格回读；Desktop 对等实现、Desktop/Android 共同最终证据、OPPO、Provider/Key/HTTP 与发布也仍不在此检查点。故不得将其写作 P6-I、Claude 导入或任何 Provider 能力完成。
