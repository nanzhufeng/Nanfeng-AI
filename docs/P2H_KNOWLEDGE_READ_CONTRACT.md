# 南枫 AI P2-H 最小 Knowledge 读取合同

日期：2026-08-12  
状态：本地 Knowledge 列表/详情闭环；不包含搜索、图谱、编辑管理、真实导出或真实服务

## 1. 唯一读取路径

```text
已确认保存的 KnowledgeItem
→ KnowledgeRepository
→ ReadKnowledgeLibraryUseCase
→ KnowledgeLibraryViewModel
→ 本地知识列表 / 详情
```

- 读取入口只消费 `KnowledgeRepository` 中已经存在的 `KnowledgeItem`；`GeneratedCandidate`、Capture Draft、Invocation Ledger 和 fixture 都不能被当作列表数据填充。
- 列表稳定按 `createdAt DESC, id DESC` 排序。列表条目只投影标题、正文摘要、来源类型、保存时间与稳定 ID；打开详情时再按 ID 从 Repository 回读完整正式知识。
- Room 继续为业务真值。P2-H 不增加表或字段，因此保持 Schema `3`；不执行无必要的迁移，更不清库。

## 2. 详情、来源与安全边界

- 详情展示正式 Knowledge 的完整正文、来源类型、经过既有清洗的来源引用、来源接收时间、保存时间、Candidate ID、Candidate 保存状态、Invocation ID、Provider/Model/Harness 版本和附件数量。
- `knowledge:${candidateId}` 是稳定 Knowledge 映射；Candidate 的 `SAVED` 状态与该映射均由 Room 重建回读。重复确认保存只回读同一 Knowledge，不会重复创建。
- 文本草稿、系统文本分享、图片草稿分别由 `SourceEvidence.sourceType` 展示，绝不依赖正文猜测来源。图片详情只显示附件数量和“未显示原图或附件正文”的边界。
- 禁止展示或持久化 API Key、完整 Prompt、完整服务响应、原图、附件正文、外部 URI 或绝对路径。P2-H 也不增加任何真实外发能力。

## 3. 状态与恢复

- 空库显示真实空态，明确没有 Mock/示例填充。
- 候选只有在用户确认保存成功后才从 `PENDING_REVIEW` 更新为 `SAVED`；丢弃候选保持 `DISCARDED`，两者都不会被知识列表当成知识。
- Activity/进程与 Room Repository 重建后，列表和详情均重新从本地 Repository 读取；详情返回仅回到同一已加载列表，不重解释或重写领域事实。

## 4. 验证和停止

- 自动契约覆盖：真实空态、确认保存后的来源/正文/安全溯源回读、Repository 重建、最新优先稳定顺序，以及 P2-G 的幂等保存/丢弃回归。
- 本阶段需另行报告单测、Lint、正式签名 Debug/Release 和 API 35 模拟器结果；这些不代表真实 OpenRouter、真机、发布或图标视觉验收。
- 停止于 P2 的最小“保存后再次读取”能力。不得提前实现复杂搜索、图谱、知识编辑管理、真实导出、HTTP、Key、系统图片分享、拍照、账号、同步、Hub 或 Agent。
