# 南枫 AI P2-G 本地确认、Mock 与候选核对合同

日期：2026-08-12  
状态：本地 Mock 可见闭环；没有 HTTP、真实 Key、真实模型目录或真实外发

## 1. 唯一链路与状态边界

```text
Capture Draft
→ ConfirmAiRequest（只生成预览；无副作用）
→ 用户勾选本次确认
→ RunAiTaskUseCase
→ InvocationRepository / Room Ledger
→ PersistGeneratedCandidateUseCase / GeneratedCandidateRepository
→ 核对编辑
→ SaveCandidateReviewUseCase
→ KnowledgeRepository
```

- 只有确认按钮在勾选后才创建带一次性 `EgressConsent` 的 `AiTask`。关闭、返回、未勾选或仅浏览确认面不会调用 Runner、不会写 Attempt 或 Ledger，草稿保持原样。
- P2-G 固定使用 `ProviderId.MOCK`、`本地均衡预设`、实际标识 `mock-local-balanced-v1` 与 Harness `capture-organize-mock v1`。这是本机替身而非 OpenRouter、非目录事实、非真实服务。
- `RunAiTaskUseCase → InvocationRepository` 仍是唯一 Ledger 写链路。成功、失败、取消和阻止终态保持 P2-F 语义；`BLOCKED` 没有 Attempt。成功 Mock 的 Task Run、Attempt、Generation、Validation 在同一 Room 事务内落账。

## 2. 确认面与成本语义

确认面固定显示：Provider 的 Mock/非真实服务标签、预设和实际模型标识、当前草稿文字范围、图片数量、隐私边界与费用语义。

- 本机 Mock 不连接网络、不读取凭据、不发送文字或图片；确认仍是产品语义的本地演练，不能被误写成真实外发授权或服务调用。
- Mock 已验证本地成本写为 `CNY 0`；这只表示该本地替身无第三方费用，绝不表示真实模型免费。
- Mock 不估算 Token，因此 Token 为 `null`/“未知”；`null` 绝不显示成 `0`。

## 3. Candidate 与 Knowledge 严格分离

- `generated_candidates` 是独立 Room 表，保存本地可核对候选、草稿/调用溯源与 `PENDING_REVIEW`、`SAVED`、`DISCARDED` 状态；它不是 Ledger 表，也不是 Knowledge 表。
- 成功调用先写安全 Ledger，再保存 `PENDING_REVIEW` Candidate；进程或 Activity 重建后，从 Room 恢复最近待核对候选。
- 核对面允许编辑标题和正文；“取消候选”改为 `DISCARDED`，不会删除草稿或调用记录，也不会创建 Knowledge。
- 只有用户勾选保存确认且 `SaveCandidateReviewUseCase` 成功时才创建 Knowledge。Knowledge ID 由 Candidate ID 稳定派生，使重试/重建下的保存幂等；它仍保留原草稿来源、附件引用与 Invocation 溯源。

## 4. Schema、恢复与停止

- Room Schema `2 → 3` 的 `MIGRATION_2_3` 仅增加 `generated_candidates` 和索引；保留既有 Capture、Knowledge 与 Invocation Ledger。
- UI 以 `isRunningAi` 防止同一 Activity 的重复提交；已完成的候选以 Room 待核对状态跨 Activity/进程恢复。Mock 是同步本地执行，P2-G 不伪造可中断的网络运行。
- P2-G 停止于本地 Mock 的确认→账本→候选→核对→保存最小闭环。不得由此宣称真实 Provider、真实 Token/费用、真实文字/图片外发、真机、发布或图标视觉验收已通过。
