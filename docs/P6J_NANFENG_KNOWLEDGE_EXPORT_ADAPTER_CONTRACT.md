# 南枫 AI P6-J 南枫知识库对话／知识 export Adapter 合同

日期：2026-08-15  
状态：**CLOSED（非 OPPO 退出门，2026-08-15）**；本文件是 P6-J 的唯一合同，不复用 P6-H/P6-I 的格式断言。

## 来源与目标

只审计只读跨电脑复用包中的代码／协议：知识库 `export_all_json` 输出完整 `IntelligenceRecord[]`，每条记录的 `sourceText` 才可能承载惰性 `chat_messages` 会话正文。P6-J 只接受用户从知识库导出的 JSON 文件；不读取知识库数据库、资料、附件、备份、路径、凭据或运行版数据。

```text
Desktop 原生文件选择器 / Android OpenDocument
→ 有界 UTF-8 读取与 app-private 原始 JSON 副本
→ NanfengKnowledgeExportJsonAdapter（严格、惰性、不执行）
→ P6-J 专属 task/item
→ 用户逐项确认 / 跳过 / 取消 / 重试
→ Conversation owner 单一事务
→ Conversation + Message Tree + NANFENG_KNOWLEDGE_EXPORT provenance + receipt
```

## P6-J v1 可接受格式

- 顶层必须是非空 JSON 数组，最大 32 MiB、200 条记录、24 层、单一字符串 120,000 code points；只接受 `.json` 与 JSON MIME。ZIP、Markdown/DOCX、便携备份、SQLite、附件、目录和非 JSON 一律拒绝。
- 每条候选必须是知识库 `IntelligenceRecord` 的最小安全投影：有限正整数 `id`、安全非空 `title`、ISO-8601 `createdAt`／`updatedAt`、以及 JSON 字符串 `sourceText`。未知 record 字段忽略，绝不解析 `localPath`、URL、来源、笔记、判断、摘要、token 或模型账本。
- `sourceText` 顶层必须是对象，且有非空 `chat_messages` 数组。`sender` 仅 `human`／`user`→USER、`assistant`→ASSISTANT；`tool` 行整体丢弃，绝不变成消息正文。保留下来的消息按可见顺序形成线性 Message Tree。优先采集可见 `content` 的 `text` 块（无 type 或 `type=text`）；`thinking`、tool input/use、HTML、Markdown 指令、图片及其他非文本块均丢弃，不执行、不解释。没有可见文本则回退顶层 `text`；过滤后没有可见 USER/ASSISTANT 消息则该记录可见失败。
- 源会话 ID 为 `knowledge-record-<record id>`，消息 ID 为 `knowledge-record-<record id>-message-<ordinal>`；它们是 provenance opaque ID，不会成为本地 ID。消息时间优先严格 ISO `created_at`，缺失时使用 record `createdAt`。P6-J v1 不导入附件字节、外部 URI 或路径，也不猜测分支。

## 状态、安全与验收门

- 状态、私有副本、重试、幂等／冲突、单项确认事务、来源撤销及恢复语义与 P6-I 相同，但 storage key、Room 表、Desktop SQLite 表和 provenance/receipt 必须 P6-J 专属，不能共用 Claude 或 ChatGPT 表。
- 成功会话标明“从南枫知识库导入 · 本地静态文本”，不关联模型、Provider、费用或调用记录；TEMPORARY 不显示入口或接收结果。知识型但非会话记录是 item-level `NOT_CONVERSATION_RECORD`，绝不伪造 Conversation。
- Android Schema 28→29、Desktop SQLite 14→15 只追加 P6-J 私有表与索引。最终闭环必须分别完成 Desktop 隔离最新 app 与 Android `emulator-5554`：系统 picker → Alpha 确认／知识记录 Skip → 完整关闭或 force-stop 重开读回 → Settings 导出严格回读与 SHA-256。不得触碰 OPPO。
- 验收只能使用合成 fixture；当前闭环 fixture 为 `p6j-nanfeng-knowledge-export-acceptance.json`，含两个有效静态会话：Alpha 必须确认、第二项必须 Skip。导出 payload 必须只含 Alpha 的可见 USER/ASSISTANT 文本，且排除 Skip、`sender=tool`、tool block 与 thinking。

## 明确排除

不读取 Key 或 Provider，不构造 Prompt/RunSpec、不发 HTTP、不执行模型／Agent／tool；不读取知识库真实用户数据，不导入知识库附件／备份／数据库，不做同步、发布或 OPPO 验收。
