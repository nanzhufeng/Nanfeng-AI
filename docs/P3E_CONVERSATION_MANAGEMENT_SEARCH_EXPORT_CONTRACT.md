# 南枫 AI P3-E 会话管理、搜索与本地导出合同

日期：2026-08-13  
状态：P3 的第五个本地增量；不含删除、导入、分享、真实 Provider、Key、HTTP、图片外发、Projects、Memory 或 Knowledge 扩展

> **当前 Android UI 路由（2026-08-24）：** 本文保留会话管理、排序、搜索投影与导出的领域 owner。Android 会话列表、抽屉、搜索目录、卡片、预览、日期、颜色、边距和交互均以 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 为唯一正文；本文的早期 UI 描述不得单独执行。

## 目标、所有权与入口矩阵

```text
UI（稳定 intent / 瞬时筛选）
→ ConversationManagementDomain（重命名、置顶、归档、排序、筛选）
→ ConversationRepository（Room 事务、稳定读取、重建）
→ Search Projection（只读同一 Room / current path）
→ Conversation Export UseCase（稳定 Repository 快照）
→ ConversationExportStore（私有原子包、同文件回读与哈希）
```

| 概念 | 唯一所有者 | 公开入口 | 禁止的平行规则 |
|---|---|---|---|
| 标题、置顶、归档、默认排序与归档筛选 | `ConversationManagementDomain` | `ManageConversationUseCase` | UI、DAO 或导出自行修改状态/排序 |
| 会话持久化、事务和重建读取 | `ConversationRepository` | 管理、搜索、导出用例 | UI 直连 DAO 或维护第二份列表真值 |
| 搜索投影 | `ConversationSearchProjection` | `SearchConversationsUseCase` | FTS/缓存/展示文本成为第二份正文真值 |
| 导出协议与安全映射 | `ConversationExportMapper` | `ExportConversationPackageUseCase` | UI/Renderer/DAO 拼出另一份 payload |
| 文件原子写入与验签 | `ConversationExportStore` | App-private `exports/conversations/v1` | 文件名、内存状态或 ZIP 成功即被报告为成功 |

实际入口：会话列表、工作区管理菜单、搜索结果打开、导出入口和 Activity/进程重建均受影响；删除/回收站、导入/分享、Projects/Memory、真实服务与知识库入口不在范围内。

## 管理语义、幂等与稳定排序

- `ConversationId` 永不因重命名、置顶或归档而改变。标题按 Unicode code point 计数，`trim()` 后必须为 1–120 code points；全空白、控制字符和超界均确定拒绝。标题原样保留（不做 Unicode NFC/NFKC 改写）。
- 每个管理操作含稳定 `ConversationManagementIntentId` 与请求指纹。相同 ID/相同指纹回读原结果；同 ID/不同指纹确定拒绝；失败事务不留下标题、置顶或归档的半更新。
- 置顶和归档都只由明确用户动作触发；取消、返回和搜索/重建不会自动写入。归档不是删除，不改变 Message Tree、Invocation、附件或导出事实；既有 `deletedAt` 只保留软删除语义，不提供入口。
- 默认列表仅含未归档会话，排序为 `pinnedAt != null` 优先、随后 `updatedAt DESC`、最后 `ConversationId ASC`。归档列表独立展示，按 `updatedAt DESC, ConversationId ASC`；筛选状态只改变读取集合，不改变事实。

## 本地搜索

- 只搜索会话标题，以及同一 `ConversationSnapshot` 当前根→叶路径中的 `USER` / `ASSISTANT` `ContentBlock.Text` 正文。System、Tool、隐藏兄弟分支、Key、Prompt、Provider 原始响应、Ledger 安全元数据、展示 IR/缓存均不搜索。
- 查询先 `trim()`，使用 Unicode `lowercase(Locale.ROOT)` 的大小写无关包含匹配；不做兼容归一化、分词或网络索引。空查询返回空结果且不读取/修改任何额外事实。
- 结果可选 `ACTIVE` / `ARCHIVED` / `ALL` 范围。排序为标题命中优先、再会话默认排序、最后命中 `MessageNodeId ASC`；最大 50 条。每项返回稳定 `ConversationId`、可空 `MessageNodeId`、安全摘要和命中种类；打开时重新从 Repository 读取当前路径，不能跳到隐藏历史分支。

## 版本化 Conversation Export

- 独立格式为 `nanfeng-ai.conversation-export` / v1，固定 ZIP `manifest.json` + `conversation.json`；默认仅导出一个会话的当前根→叶路径。全树不是本增量能力，不能隐式包含隐藏分支。
- Payload 包含安全会话元数据、稳定 ID、当前路径有序消息与 ContentBlock、角色、交付状态、版本、Invocation 安全关联和附件逻辑元数据引用。`usage` / `cost` 不属于本协议；没有 Ledger 真实值时不伪造值。
- 明确排除 Key、Authorization、系统凭据、完整内部 Prompt、Provider 原始响应/chunk、内部路径、展示缓存、未确认 Knowledge、附件二进制/原图、隐藏分支和 Ledger 内容。用户拥有的 message text 是允许的正文。
- 包写入 app-private `exports/conversations/v1`：同目录 `.part` 写入、关闭和 `fd.sync()`、原子移动、从最终文件重开、严格 Manifest/Payload 解析和字节/内容/包 SHA-256 校验。取消或失败不得留正式半包；篡改拒绝；重建仅重新验证最近有效包。P3-E 只导出，不导入、不分享。

## 数据、迁移、UI 与验证

- Schema 6 已有 `title`、`archivedAt`、`pinnedAt`、`deletedAt` 和所需会话/消息/附件关联；P3-E 使用 Repository 查询与内存投影，不升级 Schema、不清库。旧 P2/P3 数据以自动迁移及 Schema 6 回读回归证明保留。
- 最小 UI 是列表搜索、活跃/归档筛选、置顶、归档、重命名和导出入口；所有自有浅色 Dialog/Popup 内容面为 `#FFFFFFFF`，控件表面、阴影、ripple/focus 共享轮廓。导出成功只显示真实文件名、短哈希和范围，不宣称系统分享或发布。
- 定向合同覆盖标题边界、幂等/冲突/事务失败、置顶归档排序/重建、中文英文 Markdown/代码搜索、隐藏兄弟隔离、归档范围、当前路径导出、回读/哈希/篡改/原子失败与敏感排除，并回归 P3-A/B/C/D。Lint、正式签名 Debug/Release、v2/v3、API 35 本地生命周期单独报告；真实 Provider、Token/费用、真机/OPPO、图标视觉和发布仍未验收。
