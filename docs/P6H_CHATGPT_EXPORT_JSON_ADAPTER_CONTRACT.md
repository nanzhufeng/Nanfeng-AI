# 南枫 AI P6-H ChatGPT export JSON Adapter 合同

日期：2026-08-14  
状态：已完成（非 OPPO 退出门，2026-08-14）。数据/任务机及独立 Settings Center → 数据 → 数据导入的双端真实路径均已由最终证据重新关闭；不因 OPPO 未授权重开。详见 `P6H_CHATGPT_EXPORT_JSON_ADAPTER_EVIDENCE.md` 与 `CURRENT_HANDOFF.md` 的当前权威状态；下一 Adapter 为 P6-I Claude export JSON。

## 目标与唯一链

```text
Desktop 原生文件选择器 / Android OpenDocument
→ 有界 UTF-8 读取与 app-private 原始 JSON 副本
→ ChatGptExportJsonAdapter（惰性、严格、无执行）
→ adapter 专属 ImportTask / ImportItem
→ 用户逐会话确认 / 跳过 / 取消 / 重试
→ ConversationRepository / DesktopWorkspaceStore
→ 真实 Conversation + Message Tree + imported provenance
```

- 仅接受用户明确选择的 ChatGPT data-export JSON 顶层数组（原始导出通常名为 `conversations.json`；用户可对副本改名但必须保留 `.json`）；不自动扫描目录、ZIP、浏览器配置或账号数据。最大包 32 MiB、最多 200 个会话、每会话最多 2,000 个 mapping 节点、最大 24 层 JSON、单文本块最多 120,000 code points。
- 外部 URI、绝对路径、token、Key、Authorization、cookie、HTML/Markdown 可执行语义、附件字节、Provider 原始响应和 tool 指令没有持久化位置。正文、tool 名称与安全文本结果一律作为不执行文本；未知 content type、空内容或无法安全映射的节点标为该会话的可见跳过理由，绝不猜补。
- 私有副本完成后立即丢弃 `content://`、Desktop 原路径、权限 token 与可恢复文件句柄。任务表只保存 task/item ID、private storage key、安全显示名、MIME、字节数、content/package SHA-256、adapter/version、状态和安全失败码。
- 不读取 Key、不构造 Prompt/RunSpec、不发送 HTTP、不执行 Provider/Agent/tool、不操作 OPPO、不修改 launcher/Dock 图标；导入 provenance 不是 Provider Invocation，不能被模型、费用或真实来源 UI 冒充。

## ChatGPT v1 映射与保真

- 以会话 `id`、`title`、`create_time`、`update_time` 和 `mapping` 为输入。mapping 的 `parent` / `children` 建立 Message Tree；只映射 `user`、`assistant`、`tool` 三类 author role（`system` 与未知角色不进入正式树，记录为可见安全跳过）。父节点不存在、环、重复 source ID、无根、孤立 current leaf 或时间无效均拒绝该会话，不影响同一包其他候选。
- `content.parts` 的 string 顺序合并为惰性 Text；tool 只存安全可见文本摘要，不执行 function/tool 调用；空 assistant 节点不创建“已生成”假消息。`metadata.model_slug` 只作为 imported model metadata，不能成为本产品的 model override 或 route/Provider 事实。
- 确认后每个会话新建本地 ConversationId / MessageNodeId；保留稳定 `sourceSystem=CHATGPT_EXPORT`、source conversation/message opaque ID、adapter ID/version、importedAt、contentHash、packageHash。原 ID 永不作为本地 ID；不覆盖或合并普通会话。
- source conversation ID + package hash 形成可重放 receipt：相同内容的重复确认回读原 imported conversation；同一 source ID 但不同 hash 创建明确 `CONFLICT_REIMPORT` 候选，必须用户再次确认；取消、跳过、失败和中断不产生 Conversation。来源撤销只标记 provenance/revoked，不删除或篡改用户已编辑的本地 Conversation。

## 任务、恢复与跨端入口

- 状态：`SELECTED → PRIVATE_COPIED → PARSING → AWAITING_CONFIRMATION → PARTIALLY_COMPLETED / COMPLETED`，另有 `FAILED`、`CANCELLED`。进程重启只从专属持久任务和私有副本恢复；`PARSING` 中断转为 `FAILED/INTERRUPTED`，可重试。
- Android 只用 `ActivityResultContracts.OpenDocument`（JSON MIME）；Desktop 只用 Tauri native dialog。选择取消不写任务；副本、解析或确认失败保留原位中文原因和可执行“重试/跳过/取消”动作。
- TEMPORARY 永远没有导入入口，也不接收导入结果；成功会话进入普通历史、搜索、置顶/归档、继续对话与显式 Context，且 UI 明示“从 ChatGPT 导入”。

## 数据与验收门

- Android Schema 24→25、Desktop SQLite user_version 12→13 仅追加本 Adapter 的 task/item/provenance/receipt 表和索引；不 wipe、不 destructive migration、不改变既有 Conversation、P6-G catalog、TEMP 或已完成 Adapter 表。
- 自动合同至少覆盖：UTF-8/BOM、大小/深度/重复 key、结构/角色/content type、分支与顺序、未知字段安全忽略、部分失败、private-copy 路径排除、确认/跳过/取消/中断重试、幂等/冲突 reimport、rollback、迁移、重启回读及 provenance 不进入 Invocation/route/egress。
- 真正退出还需两端各自：系统文件选择器 → 私有复制 → 会话确认/跳过 → 关闭/force-stop 重开读回 → 导出回读；Android 仅 `emulator-5554`、正式签名 install/base hash，Desktop 为最新隔离 `.app`。未达到前不得以 fixture、解析成功、JVM/Node/Rust 测试或构建宣称阶段完成。

## 2026-08-14 已完成的数据与任务机证据（Settings UI 门重新打开）

- Android Schema 24→25 与 Desktop SQLite 12→13 均为仅追加迁移；两端严格 JSON、private-copy、专属任务机、原子 Conversation/Message Tree/provenance/receipt 写入、恢复、确认/跳过/取消/重试均有自动合同。`org.json` JVM 未实现桩方案没有恢复或接入。
- Android 已以 `emulator-5554` 的 DocumentsUI 完成最小非敏感 `conversations.json` 真选、Alpha 确认与 Skip 跳过、force-stop/restart 后普通会话/树/时间/角色/来源读回，以及导出/hash；最终 APK `install -r` 后回拉 `base.apk` 与本地 hash 一致，正式签名 v2/v3 通过。
- Desktop 最新隔离 acceptance `.app` 已以原生 Open panel 真选同类 fixture，显示 `stage-succeeded` / `task-read` 诊断与候选弹窗；Alpha 确认后写入 `conversation-chatgpt-bc54a6844edc282000ba9d00`，Skip 已保存。重启后普通历史、用户/助手消息、时间、分支动作和“从 ChatGPT 导入”provenance 均可见；同一包重复确认回读同一 ConversationId。应用导出已严格回读、zip 完整性与 SHA-256 均核验。完整可复核记录见 `P6H_CHATGPT_EXPORT_JSON_ADAPTER_EVIDENCE.md`。
