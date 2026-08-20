# 南枫 AI P4-A Projects、项目指令与知识范围合同

日期：2026-08-13  
状态：P4 的第一个本地增量；Schema 8→9。只建立 Projects、指令审计和范围所有权，不构造 Prompt、不调用模型、不读取 Key、不发 HTTP 或图片。

## 目标、所有权与入口

```text
Projects UI / Conversation workspace（稳定 intent）
→ Project Domain（生命周期、指令 revision、范围语义）
→ ProjectRepository（Room 单事务、幂等、重建）
→ projects / project_instruction_revisions / project_intents / knowledge_project_scopes
→ ProjectContextSnapshot / InstructionResolution（只读本地 IR）
```

| 概念 | 唯一所有者 | 公开入口 | 禁止的平行规则 |
|---|---|---|---|
| Project 生命周期、标题、说明、置顶/归档、项目指令、成员范围 | `ProjectDomain` | `ManageProjectUseCase` | Conversation/UI/DAO 自己解释项目规则或复制指令 |
| 本地事务、intent 重放、跨进程回读 | `ProjectRepository` / Room | `RoomProjectRepository` | UI 缓存作为项目真值 |
| 会话归入/移出/迁移 | `ManageProjectUseCase` → Project Repository | Conversation 工作区明确操作 | 通过标题、搜索或页面选择隐式迁移 |
| Knowledge 可见范围 | `knowledge_project_scopes` | Project Repository | Project 复制 Knowledge 正文、自动检索或自动记忆 |
| 指令优先级审计 IR | `ProjectInstructionResolution` | `ProjectContextSnapshot` | UI、附件、网页或文档内容成为项目指令 |

## 数据、空值与迁移

- `ProjectId`、Project Instruction Revision ID 和 Project Intent ID 都是稳定 UUID，不能使用 Room 行号或中文标题。标题 trim 后为 1–120 Unicode code points；说明最多 2,000；控制字符拒绝。颜色/图标只是列表语义，绝不涉及 launcher icon。
- 项目指令是用户拥有的本地内容，最多 12,000 code points。每次实质变更追加一条 `source=USER`、时间、连续 revision 和 SHA-256；同内容不产生无意义 revision。空内容是可审计的“移除当前项目指令”revision，不等于未知或默认指令。
- Conversation 只保存一个可空 `projectId`；它不复制项目说明或指令。归入、移出和迁移是稳定 intent 的显式用户动作，保持消息树、Invocation、附件和既有 Conversation Export 不变。项目归档不级联归档 Conversation 或 Knowledge；本阶段没有项目删除入口。
- Knowledge scope 只保存稳定 Knowledge ID 到可空 Project ID 的关联；`null` 是 global scope。P4-A 不复制 Knowledge 正文、不做自动检索、Memory、摘要、压缩或文件/网页 Adapter。
- Schema 8→9 只新建 `projects`、`project_instruction_revisions`、`project_intents` 和 `knowledge_project_scopes` 及索引；复用既有 `conversations.projectId`。迁移不清库、不重建、不改写 P1–P3 数据。外键均为 `NO ACTION`，未来软删除/回收必须另立合同。

## 幂等、排序与安全 IR

- 创建、资料更新、置顶、归档/恢复、指令 revision、会话归属与 Knowledge scope 都有 stable intent + SHA-256 fingerprint。相同 ID/相同 fingerprint 回读原结果；同 ID/不同 fingerprint 拒绝；写入失败必须事务回滚，重建从 Room 回读。
- 活动与归档项目分开：活动排序为 pinned 优先、`updatedAt DESC`、`ProjectId ASC`；归档为 `updatedAt DESC`、`ProjectId ASC`。项目归档不会隐式修改成员资产。
- `ProjectContextSnapshot` 仅含 Project ID、可空 revision ID/revision/hash。`InstructionResolution` 固定记录 `SYSTEM > SAFETY > PROJECT > CONVERSATION > CURRENT_USER` 的优先级和来源/安全 hash；P4-A 不生成真实 Prompt、不创建 RunSpec、Authorization、Provider Request 或账本事实。
- 项目指令永远不能覆盖 system/safety。附件、网页、文档和 Tool 结果均是不可信数据，不能提升成项目指令。后续对话调用只可在当次上下文快照读取最新 Project revision；既有 Invocation/回答历史不回填或伪造 revision。

## UI、导出与未做项

- 最小 UI 提供 Projects 空态、创建、活动/归档、置顶、归档/恢复、详情、指令编辑与最近 revision 摘要；Conversation 工作区提供明确的归入/移出选择并显示当前归属。所有 App 自有 Dialog/选择面是纯白 `#FFFFFFFF`，按钮/ripple/focus/shadow 服从同一圆角。
- P4-A 不导出 Project 包：未来最小合同只能是 Manifest、Project 元数据、指令 revisions 和成员稳定 ID，且不得含 Conversation/Knowledge 全文或附件字节。当前不分享、不上传。
- 禁止提前：真实 Provider/Key/Usage/费用、Memory、自动检索/摘要/压缩、文件/网页 Adapter、账号/同步、协作、Tool/Agent、图片外发、系统分享和上传。

## 验证与停止条件

- 定向合同覆盖标题边界、排序、归档、revision/空指令/hash、IR 优先级与安全覆盖、Room 重建、intent 重放/冲突、Schema 8→9 保留和敏感字段排除。
- P1–P3 全量回归、Lint、正式签名 Debug/Release、v2/v3 与 API 35 生命周期分别报告。真实服务/Usage、真实长会话性能、真实图片外发、OPPO、图标视觉和发布不因 P4-A 本地结果而通过。
- 达到上述本地 Projects 基础即停止；P4-A 不是 P4 或总方案终点。
