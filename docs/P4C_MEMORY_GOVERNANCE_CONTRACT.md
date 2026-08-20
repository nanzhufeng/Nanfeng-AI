# 南枫 AI P4-C 长期 Memory 本地治理合同

日期：2026-08-13  
状态：P4 的第三个本地增量；Schema 9→10。只完成长期 Memory 的显式本地治理，不构造 Prompt、不读取 Key、不发 HTTP、图片或任何 Provider 请求。

## 所有权与明确边界

```text
Memory UI（明确用户动作）
→ MemoryDomain → ManageMemoryUseCase → MemoryRepository / Room
→ memories / memory_revisions / memory_intents / memory_conflicts
```

| 概念 | 唯一所有者 | 公开入口 | 禁止的平行规则 |
|---|---|---|---|
| Memory 标题/正文、状态、scope、来源、敏感门禁与 revision | `MemoryDomain` | `ManageMemoryUseCase` | UI、DAO、Project/Conversation、AI 候选自行判断或写库 |
| 本地事务、intent 重放、重复/冲突事实和重建 | `MemoryRepository` / Room | `RoomMemoryRepository` | UI 缓存或 `memorySources` 作为真值 |
| Context 的 Memory 边界 | P4-B `ContextSelectionDomain` | `ReadContextSelectionUseCase` | 本阶段把 Memory 正文或 ID 加入 `ContextSelectionSnapshot` |

P4-C 不读取、解析、写入或迁移既有 `ConversationSettings.memorySources`；P4-B 对 `MEMORY = NOT_IMPLEMENTED` 的 metadata-only 快照保持原样。不存在自动提取、静默保存、自动注入 Context、检索、摘要、压缩、分享、上传、同步、导出、Prompt、RunSpec、Authorization、Invocation 或 egress。

## 数据与安全合同

- `MemoryId`、Revision、Intent、Conflict 都是稳定 UUID。正文与标题是用户明确确认的本地事实；每个 revision 追加完整可审计快照，不原地改写。
- scope 严格为 `GLOBAL`、`PROJECT`、`CONVERSATION` 之一：Project/Conversation 关联必须存在，且二者互斥。Project/Conversation 的归档、移出或未来删除不会静默删除 Memory。
- P4-C UI 只产生 `source=USER_CONFIRMED`；`MANUAL_IMPORT` 是保留枚举，尚无导入入口。来源有稳定 ID 与可解释摘要。
- `ACTIVE`、`PAUSED`、`DELETED` 为唯一状态。暂停/恢复/删除都追加 revision；删除为软删除，普通可用列表不显示 deleted 正文，历史仍可审计。
- 密码、API Key、Authorization/Bearer、恢复码、完整支付卡号模式在 `MemoryDomain` 写入前拒绝。拒绝结果只返回安全错误码；不创建 Memory、revision、intent 或 conflict，因此不保存被拒正文。
- 第三方网页、工具结果、附件、Provider payload、未确认 AI 候选不能作为正式 Memory 输入。表结构不存在 Key、Prompt、Provider payload、URI、路径、附件字节或外部内容列。

## CRUD、幂等与冲突

- 用户可手工创建、查看、搜索、按 scope/status 筛选、编辑（新 revision）、暂停/恢复、单项软删除与确认后的批量软删除。
- 每个动作使用 stable intent + SHA-256 fingerprint：相同 ID/相同 fingerprint 回读；相同 ID/不同 fingerprint 拒绝。
- 同 scope 的规范化标题/正文 hash 重复时幂等返回既有 Memory；同 scope 的相同规范化标题但不同内容会生成 PENDING conflict，不静默覆盖。用户只能明确选择保留已有、并存新项或作为既有项新 revision。
- 排序为 `updatedAt DESC, MemoryId ASC`。搜索只读取同一 Room 的当前标题/正文；不存在自动 Context 读取。

## Room、UI 与验证

- Schema 9→10 仅新增 `memories`、`memory_revisions`、`memory_intents`、`memory_conflicts` 和索引；不清库、不重建、不改写 P1–P4-B 数据。外键均为 `NO ACTION`，避免 Project/Conversation 生命周期静默清除 Memory。
- UI 提供白色 `#FFFFFFFF` 的管理 Dialog、空态、搜索、scope/status 筛选、创建/编辑、暂停/恢复、历史与来源摘要、单项/批量删除确认、确定性冲突选择。所有页面明确“不会自动加入对话上下文”。
- 本阶段不做 Memory 导出；未来导出必须另立本地原子包、用户显式选择、回读/哈希及篡改拒绝合同，且禁止分享/上传/同步。
- 定向测试覆盖 CRUD/revision、scope、状态、搜索、重复、冲突、批量删除、Repository 重建、高敏正文拒绝无落库、Project/Conversation scope 合法性、Schema 9→10 以及 P4-B Memory 仍未进入 Context。全量回归、Lint、正式签名 Debug/Release、v2/v3 与 API 35 emulator 分层报告；均不替代真实 Provider、费用、OPPO、图标或发布验收。

达到上述本地治理闭环即停止。P4-C 不是 P4 或总方案终点；L0–L3 正文选择、检索、摘要/压缩、Knowledge 完整管理、文件/网页 Adapter、Eval、真实成本和真实 P3 债务仍独立后续推进。
