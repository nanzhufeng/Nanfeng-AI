# 南枫 AI P3-H 会话本地尝试历史投影合同

日期：2026-08-13  
状态：P3 的第八个本地增量；只读既有 Room 事实，不含 Provider、Key、Authorization、RunSpec、HTTP、图片外发、Usage/费用推断或 Schema 迁移

## 目标、所有权与入口矩阵

```text
ConversationSnapshot + ConversationAttemptLineage（既有本地 Room 事实）
→ ConversationAttemptHistoryProjection（唯一安全读取模型）
→ ConversationFoundationViewModel
→ 本地工作区「本地尝试历史」
```

| 概念 | 唯一所有者 | 公开入口 | 禁止的平行规则 |
|---|---|---|---|
| P3-C 动作尝试的本地历史 | `ConversationAttemptHistoryProjection` | `ReadConversationAttemptHistoryUseCase` | UI 按当前 runtime、消息文本或列表索引自行猜测动作、模型或状态 |
| 谱系真值与重建 | `ConversationActionRepository` / `RoomConversationRepository` | `lineagesForConversation` | UI 缓存或写入另一份历史；重新提交 intent |
| 助手消息终态 | `MessageNode.deliveryState` | 同一 Snapshot 读取 | 把当前 `ConversationRuntimeState` 误用于历史 sibling |
| Token、费用、真实 Provider 事实 | P2-F Invocation Ledger / 后续真实 Provider 合同 | 本增量无入口 | 将 fixture 零费率、缺失 Token 或安全选择 ID 伪装为真实 Usage、费用或 Provider Attempt |

| 入口/消费者 | 是否存在 | 影响 | 最小验证 |
|---|---|---|---|
| P3-C continue/retry/change-model | 是 | 读取其 append-only lineage，不改变写入 | Room 回读顺序与动作/选择一致 |
| P3-B 首次 fixture、停止/失败事件 | 是 | 仅作为无 lineage 的助手消息，不伪造历史条目 | 不生成虚构动作历史 |
| 草稿、Photo Picker、附件 | 是 | 不受影响；绝不读取路径、缩略图或字节 | 历史投影无附件字段 |
| 搜索、导出、Ledger、真实 Provider | 存在/后续门 | 不受影响 | 不扩大搜索/导出/账本或 egress 范围 |
| OPPO、图标、账号/同步/Projects/Memory | 不在本增量范围 | 不存在 | 不新增入口 |

## 固定读取语义

- `ConversationAttemptHistoryProjection` 只接受同一 `ConversationSnapshot` 与该 conversation 的 `ConversationAttemptLineage` 集合。每条 lineage 必须引用该会话中的、角色为 `ASSISTANT` 且 Invocation ID 相同的 `createdMessageId`；任何缺失、跨会话、角色不符或 Invocation 不符的行一律忽略，不让不可信/旧数据影响 UI。
- 条目稳定按 `createdAt DESC`、再 `invocationId ASC`；它不是当前路径选择器，也不改变 current leaf、草稿、消息树、运行状态或 intent。
- 显示字段仅有：动作种类、`LOCAL_DETERMINISTIC_FIXTURE` 标识、已验证本地 fixture 的模型显示名/安全 Model ID、Harness 名称及版本、Registry Snapshot 安全 ID、创建时间、由目标助手 `MessageNode.deliveryState` 翻译的本地状态。不得显示 intent、Invocation、消息 ID、前序 ID、正文、Prompt、Provider 原始内容、Key、Authorization、路径、URI、EXIF、附件或二进制。
- P3-C local fixture 的价格元数据为已知本地零费率，界面必须明确“本地 fixture · 不产生 Provider 费用”；它不等于真实计费。Token 只有在当前活跃 runtime 已保存为非空值时才显示为“本地事件 Token”；`null` 显示“Token 未知”，绝不显示为 0；P3-H 不为历史条目回填、估算或写入 Token。
- 历史为空时显示“尚无由继续/重试/换模型产生的本地尝试”；P3-B 首次 fixture 没有 P3-C lineage，不补造条目。切换分支、归档、冷启动只重新读取同一 Room 快照，历史仍完整可见，但不提供修改/重放/删除控件。

## 边界、UI 与验证

- `OpenRouterEgressPolicy.Disabled` 不变。本增量不读取/要求/写入 Key，不签发真实 RunSpec/Authorization，不构造 Provider HTTP，不发送文字或图片，不改变 P2-M nonce，也不新建 Invocation/Provider Attempt/Candidate/Knowledge。
- 工作区仅增加紧凑只读「本地尝试历史」区；所有 App 自有浅色表面保持 `#FFFFFFFF`，不新增第二个选择面。固定说明“本地历史，不代表真实 Provider、Token 或费用”。
- Schema 8 不变，不新增表或迁移。定向合同覆盖：稳定排序、Room 重建、历史 sibling 与当前 runtime 隔离、无 lineage 条目、跨会话/错误关联过滤、零费率与未知 Token 语义、敏感字段排除。全量 P1/P2/P3-A–G 回归、Lint、正式签名 Debug/Release、API 35 生命周期验证仍分别执行。

**停止条件：** 达到只读本地历史闭环即停止；真实流、Provider/Usage、附件外发、Projects、Memory、Knowledge 搜索、OPPO、图标视觉与发布保持独立后续门。
