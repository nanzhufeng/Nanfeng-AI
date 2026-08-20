# 南枫 AI P4-B 上下文选择所有权与 Memory 边界合同

日期：2026-08-13  
状态：P4 的第二个本地增量；Schema 保持 9。只建立可审计的本地“选择快照”，不构造 Prompt、不读取任何 Key、不发 HTTP 或图片。

## 目标、所有权与公开入口

```text
ConversationRepository + ProjectRepository（只读快照）
→ ContextSelectionDomain（选择语义与排除矩阵）
→ ReadContextSelectionUseCase
→ ContextSelectionSnapshot（只读、metadata-only IR）
```

| 概念 | 唯一所有者 | 公开入口 | 禁止的平行规则 |
|---|---|---|---|
| 当前会话分支的上下文候选选择 | `ContextSelectionDomain` | `ReadContextSelectionUseCase` | UI、Harness、Provider 或 DAO 自行拼接分支/草稿 |
| 项目指令版本引用 | `ProjectInstructionResolution` / Project Domain | `ProjectContextSnapshot` | Conversation 复制项目指令正文或自行选择 revision |
| Memory 是否进入选择集 | 本合同的显式 `NOT_IMPLEMENTED` 边界 | `ContextSourceBoundary` | 根据 `ConversationSettings.memorySources` 自动加载、自动保存或推断记忆 |

P4-B 的唯一生产读取入口是 `ReadContextSelectionUseCase`。它只接收稳定的 `ConversationId`，从 Repository 回读当前快照；不存在的会话或指向不存在 Project 的会话都显式拒绝，不降级到任意项目或全局资料。

## 选择事实与排除矩阵

- `ContextSelectionSnapshot` 仅含 Conversation ID、当前 Branch ID、Project revision/hash 的 `ProjectContextSnapshot`，以及当前根→叶路径中 Message ID、角色、交付状态、ContentBlock 数量和内容 SHA-256。它没有任何消息正文、项目指令正文、Prompt、Token 估算、RunSpec、Authorization、Provider 请求、Invocation 或附件字节字段。
- 当前路径由既有 `MessageTree.contextPath()` 唯一确定；兄弟分支、草稿、归档/软删除会话均不得混入。
- 项目只以当前 revision 的稳定 ID/revision/hash 出现。Project 指令正文仍由 Project Domain 持有；P4-B 不把它复制或拼接进选择快照。
- Memory、Knowledge、检索结果、摘要、压缩、缓存前缀、文件/网页、附件内容与 Tool 结果全部显式记录为排除或未实现。既有 `memorySources` 是保留的版本化引用，P4-B 不读取、更不解析或持久化它。
- 内容 hash 只用于本地一致性审计；它不能替代正文、tokenization、质量评估或未来真实 Context/Payload。

| 来源 | P4-B 状态 | 原因 |
|---|---|---|
| Project instruction revision | `METADATA_ONLY` | 固定版本与优先级，绝不复制正文 |
| Conversation 当前根→叶路径 | `METADATA_ONLY` | 固定将来唯一候选路径，不形成 Prompt |
| 草稿、兄弟分支 | `EXCLUDED` | 不是已提交的当前路径事实 |
| Attachment、Tool result | `EXCLUDED` | 不可信/可能含外发内容；本轮不读正文 |
| Knowledge、检索、摘要、压缩、缓存 | `NOT_IMPLEMENTED` | 未来必须各自有所有者、版本和可追溯合同 |
| Memory（工作/会话/长期） | `NOT_IMPLEMENTED` | 先完成显式 CRUD、来源、scope、暂停与用户控制，不做自动加载/保存 |

## 数据、错误与兼容

- 不新增 Room 表、Migration、导出字段、UI 页面或后台任务；Schema 9 的 Conversation、Project、Knowledge 和 Attachment 事实保持不变。
- `ContextSelectionRejected.MissingConversation` 与 `ContextSelectionRejected.InconsistentProjectReference` 是结构化本地结果；不得以空快照假装成功。
- P4-B 快照是瞬时只读 IR，不持久化、不进入 Invocation Ledger、Conversation Export、Knowledge Export、日志或诊断包。
- 现有 `ReadConversationContextPathUseCase` 保持为 Conversation Domain 的低层路径读取；它不是 P4-B 的公共 Context 选择入口，也不得被 UI/Harness 用来绕过本合同。

## 明确未做与停止条件

- 禁止：真实 Prompt/RunSpec、Provider HTTP、Key、Usage/费用、token 计算、真实图片外发、自动或手动 Memory CRUD、自动检索、摘要/压缩、文件/网页 Adapter、知识搜索/导入导出扩张、OPPO 操作和图标修改。
- 最小定向合同覆盖：当前路径排除 sibling/draft、项目 revision 仅 metadata、Memory/Knowledge/Attachment/Tool 排除、内容正文不泄漏到 IR、缺失会话/Project 显式拒绝。
- 仅在本地单测、全量测试、Lint、正式签名构建与 API 35 模拟器分别有证据时分层报告；它们均不替代真实 Provider、Usage/费用、长会话性能、真实图片外发、OPPO、图标和发布验收。
- 上述本地选择所有权和测试达到后停止。Memory CRUD 或 L0–L3 的真实正文/检索/压缩必须另立后续合同。
