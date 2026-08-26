# 南枫 AI P4-D L0-L3 Context 正文选择与显式控制合同

日期：2026-08-13  
状态：P4 的第四个本地增量；Schema 保持 10。只生成瞬时、本机的正文选择 IR，不构造 Prompt、RunSpec、Authorization 或 Provider 请求；不读取 Key、不发 HTTP、不产生费用或图片外发。

> **历史阶段定位（2026-08-26）：** 本文只保留 P4-D“用户逐项勾选后的本机预览 IR”合同，仍不构造 Prompt 或 Provider 请求。它不是当前普通聊天的 `LocalContextBroker`，不能以“默认关闭／不会发送给 Provider”否定已启用开关后的普通聊天相关性检索；现行规则只见 [Android 当前运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)。

## 目标、所有权与入口

```text
Context 控制面（每次明确勾选，不持久化）
→ ReadExplicitContextBodyUseCase
→ ContextBodySelectionDomain（L0-L2 正文、L3 正文排除语义）
→ ConversationRepository + ProjectRepository + MemoryRepository（只读）
→ ExplicitContextBodySnapshot（瞬时正文 IR）
```

| 概念 | 唯一所有者 | 公开入口 | 禁止的平行规则 |
|---|---|---|---|
| L0-L3 正文候选、scope 校验、显式选择与排除矩阵 | `ContextBodySelectionDomain` | `ReadExplicitContextBodyUseCase` | UI、Harness、DAO 或 Provider 自行拼正文/按 title 推断 Memory |
| P4-B metadata 选择与当前路径一致性 | `ContextSelectionDomain` | `ReadContextSelectionUseCase` | P4-D 改写 P4-B snapshot 或绕过缺失会话/项目拒绝 |
| Project 指令正文与 revision | Project Domain | `ProjectRepository` | Conversation 复制/缓存 Project 指令正文 |
| 长期 Memory 正文、scope、状态和敏感门禁 | Memory Domain | `MemoryRepository` | `memorySources`、UI 缓存或 AI 自动决定 Memory 注入 |
| 用户本次控制状态 | Context 控制 UI 的 ViewModel 内存状态 | 每次打开后明确勾选 | 写入 Room、ConversationSettings、Export、Invocation 或日志 |

## L0-L3 与来源矩阵

| 层 | 可用来源 | 进入条件 | 明确不做 |
|---|---|---|---|
| L0 稳定上下文 | `GLOBAL` 且 `ACTIVE` 的 Memory | 用户本次逐项勾选；ID 必须仍在当前候选集 | 自动选择、`memorySources`、系统/安全 Prompt 正文 |
| L1 项目上下文 | 当前 Project 的非空最新指令；同一 Project 的 `ACTIVE` Memory | 用户分别勾选项目指令或具体 Memory；Project 必须与当前 Conversation 一致 | 归档/其他 Project、Knowledge 检索、摘要或缓存 |
| L2 当前会话上下文 | 当前根→叶路径的 Text 块；同一 Conversation 的 `ACTIVE` Memory | 用户分别勾选当前路径或具体 Memory | 草稿、兄弟分支、Attachment 字节/URI、Tool 结果 |
| L3 短期操作轨迹正文 | 无 | `EXCLUDED`；P4-M 仅有独立安全元数据 IR | runtime 日志、命令、诊断、Provider chunk、缓存前缀，以及任何 L3 元数据伪装的正文 |

所有选择默认关闭；即使存在 `ConversationSettings.memorySources` 或可用 Memory，也不会产生正文条目。Memory 的 `PAUSED`、`DELETED`、不存在、其他 Project 或其他 Conversation scope 都不得进入候选集；请求这类 ID 必须整体显式拒绝，不返回部分正文。

## 正文 IR、安全与兼容

- `ExplicitContextBodySnapshot` 只在内存中存在，含稳定来源 ID、层、正文、角色（仅会话文本）、revision/hash 与本次显式选择摘要；它不是 Prompt，绝不估算 Token、压缩、排序为 Provider messages 或写入任何账本。
- P4-D 先通过 P4-B 的缺失会话/不一致 Project 拒绝，再从同一当前快照读取正文。Project revision 或当前分支在两次读取间发生变化时，必须显式拒绝，不得把旧 metadata 与新正文混合。
- 当前路径只投影 `ContentBlock.Text`；Attachment 与 ToolResult 不读取，草稿与兄弟分支不读取。无文本的消息不虚构空正文。
- Project 指令、Memory 和会话文本中的密码、API Key、Authorization/Bearer、恢复码或完整支付卡号模式必须在返回 IR 前拒绝；拒绝不保存被拒正文、选择或诊断副本。
- P4-C Memory 的状态、revision、冲突、删除和来源语义不变；P4-D 只读 `ACTIVE` 的当前版本，不自动更新 `lastConfirmedAt`，不写 intent、revision 或 conflict。
- 不增加 Room 表、Schema/Migration、Export 字段、后台任务、网络或 egress。`OpenRouterEgressPolicy.Disabled` 保持；AppContainer 不创建可执行 RunSpec。

## UI 与验证

- Context 控制面只能从当前本地对话打开，白色 `#FFFFFFFF`，每一来源有可读范围说明。用户先逐项勾选，才可“本机预览本次正文”；关闭、重开、会话切换或进程重建均丢弃选择，绝不静默恢复。
- 预览必须明确显示“仅本机瞬时预览，不会发送给 Provider”；不提供发送、导出、保存为 Memory、分享、同步或复制为 Prompt 入口。
- 定向合同至少覆盖：默认零正文与 `memorySources` 无效、L0/L1/L2 显式 scope 成功、L3 正文排除（P4-M 元数据不伪装正文）、当前路径仅文本且排除草稿/兄弟/附件/Tool、暂停/删除/越权 Memory 拒绝、敏感正文整体拒绝、缺失会话/Project 与重读不一致拒绝、无持久化和 P4-B 回归。
- 分别报告：定向/全量测试、Lint、正式签名 Debug/Release、API 35 `emulator-5554`。这些本地证据不替代真实 Provider、Token/费用、图片外发、长会话性能、OPPO、图标或发布验收。

达到上述本机选择与预览闭环即停止。P4-D 不是 P4 或总方案终点；检索、摘要/压缩、缓存、Knowledge、文件/网页、Eval、Prompt/RunSpec 和真实 egress 均是独立后续合同。
