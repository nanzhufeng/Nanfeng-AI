# 南枫 AI P4-G 本地 Knowledge 人工关系合同

日期：2026-08-13  
状态：P4 的第七个本地增量；Schema 11→12。只允许用户确认两个既有本地 Knowledge 的关系并可撤销；不读取 Key、不构造 Prompt/RunSpec、不发 HTTP、不产生费用或图片外发。

## 所有权与数据链

```text
Knowledge 详情中的明确用户操作
→ KnowledgeRelationshipDomain → ManageKnowledgeRelationshipsUseCase
→ KnowledgeRelationshipRepository / Room transaction
→ relationships + append-only revisions + idempotency intents
→ 仅 Knowledge UI 的关系列表/审计摘要
```

- `KnowledgeRelationshipDomain` 是关系类型、方向、对称端点规范化、scope、高敏、revision/hash 重验与重复边的唯一解释者。UI、DAO、P4-F 不得自行排序、判断方向或写表。
- 最小类型固定为 `RELATED`、`DUPLICATE_CANDIDATE`、`SUPPORTS`、`CONTRADICTS`。`RELATED`、`DUPLICATE_CANDIDATE`、`CONTRADICTS` 是对称关系并由 Domain 稳定排序端点；`SUPPORTS` 保持用户明确选择的 from→to 方向。
- 只能从两个不同的 `ACTIVE` Knowledge 明确确认；两端必须同为 GLOBAL，或同属一个 Project。跨 Project、GLOBAL/Project 混合、归档、回收站、缺失、自指向、revision/hash 已变化、已存在活动同型边或任一端点命中 `MemoryDomain.sensitiveRejection` 时整体拒绝，绝不静默换绑。
- P4-F 候选只可在确认审计中标示 `P4F_DEDUPLICATION_CANDIDATE` 来源；候选列表、理由和关闭操作都不落库。用户仍须选择每一条关系的类型并点击确认；关闭不产生关系。

## 持久化、撤销与隐私

- Schema 11→12 仅新增 `knowledge_relationships`、`knowledge_relationship_revisions`、`knowledge_relationship_intents` 和必要索引。旧 Knowledge、P1–P4-F 数据不重写、不 wipe，亦不使用 destructive migration。
- 关系表只保存稳定端点 ID、类型、范围、状态、时间、intent ID 和建议来源；不复制 Knowledge 正文、URI、路径、附件、hash 或高敏检测详情。修订与 intent 只保存动作、状态与请求指纹。
- 确认、重新确认已撤销同一关系和撤销均追加审计 revision；撤销为 `REVOKED` 软状态，不删除历史。相同 intent/fingerprint 只回放结果，不追加第二次 revision。
- 关系 UI 可建立、按活动/已撤销筛选、查看端点 ID/类型/修订/建议来源摘要并撤销。所有应用自有 Dialog 的选择面为纯白 `#FFFFFFFF`。

## 明确不做

- 不合并正文、不删除“输家”、不改标签、不批量导入。
- 不自动建立关系，也不由候选、搜索、AI、网页、附件、Tool 或 Provider payload 建立关系。
- 不把任何关系自动注入 Context、Prompt、Export、Provider、同步、日志或诊断；不新增外发路径。

## 验证要求

- 域合同覆盖类型方向/对称性、同 scope、隐藏、自指向、过期 revision/hash、高敏和重复边。
- Room 合同覆盖确认、intent 回放、撤销、审计 revision、Schema 11→12 与旧表保留。
- 回归 P4-E/F 的搜索/归档/回收站/Context 默认关闭/候选关闭丢弃；随后分层运行全量测试、Lint、正式签名 Debug/Release、v2/v3 核验和仅 `emulator-5554` 的真实点击链路。
