# 南枫 AI P2-F 本地 Invocation Room Ledger 合同

日期：2026-08-12  
状态：已实现本地 Room Ledger、Schema 1→2 迁移与最小调用记录 UI；未连接 HTTP、未读取真实 Key、未外发文字或图片

## 1. 唯一目标与所有权

```text
RunAiTaskUseCase
→ InvocationRepository
→ RoomInvocationRepository（单一事务）
→ Invocation Ledger Dialog（只读投影）
```

- `RunAiTaskUseCase` 是本阶段唯一公开写入口。它在 Provider Runner 返回后持久化终态；确认门禁返回的 `BLOCKED` 同样写入，但不调用 Provider。
- `InvocationRepository` 是查询与稳定 ID 幂等的唯一端口；UI 不接触 DAO 或数据库。
- 本阶段 Ledger 是本地追加式运行事实，不提供删除入口。未来的用户删除/保留策略必须独立授权和设计，不能让草稿、附件或知识删除静默删除账本。

## 2. 数据层级、事务与字段边界

一次记录由一个 Room 事务写入：

```text
InvocationRecord
└─ TaskRun（唯一）
   └─ ProviderAttempt（按 position 有序，0..n）
      └─ Generation（可选）
         └─ GenerationValidation（唯一）
```

- Schema 2 包含 `invocation_records`、`invocation_task_runs`、`provider_attempts`、`generations`、`generation_validations`；父子外键为 `CASCADE`，但当前没有删除 DAO/UI。
- Schema 1→2 的显式 `MIGRATION_1_2` 仅新增 Ledger 表和索引，保留已有 Capture/Knowledge 数据；绝不清库升级。
- 稳定 ID 为 `InvocationId`、`TaskRunId`、`ProviderAttemptId`、`GenerationId`、`ValidationId`。同一 Invocation ID 再写入时只接受内容完全相同的幂等回读，冲突 ID 被拒绝。
- 查询按 `completedAt DESC, id DESC`；每次从 Room 重建完整层级，进程重建不依赖内存历史。
- 保存 Provider/Model、Harness 版本、Registry Snapshot ID、价格版本、结构化错误代码、开始/结束时间与毫秒耗时所需时间点。已知费用使用 ISO 币种及微货币；`null` 是未知，`0` 是已验证免费/本地成本。

## 3. 终态与安全

| Task Run 终态 | Attempt | UI 表示 |
| --- | --- | --- |
| `SUCCEEDED` | 至少一个成功 Attempt，成功 Generation/Validation | 成功 |
| `FAILED` | 至少一个实际失败 Attempt | 失败与安全错误摘要 |
| `CANCELLED` | 至少一个已开始的取消 Attempt | 已取消 |
| `BLOCKED` | 必须为空 | 已阻止（未发起调用） |

以下数据无对应表列、不会进入 Ledger、日志或调用记录 UI：API Key、完整 Prompt、完整响应、原图、附件正文、未脱敏个人资料。

## 4. 最小可见 UI

- 捕获页提供“调用记录”入口；Dialog 内容面为纯白，外部 scrim 才是灰色。
- 空态如实显示尚无本地记录及“当前未发出任何真实服务请求”。
- 每行显示真实终态、时间、Provider/Model、Harness 版本、耗时、Attempt 数、输入/输出 Token、费用和安全错误摘要。
- `MOCK` 显示为“本地 Mock（非真实服务）”；未连接的 OpenRouter 显示为“未验证真实连接”。fixture 不进入 UI。

## 5. 本阶段验证与停止

- P2-F 定向测试覆盖四层事务往返、`null ≠ 0`、成功/失败/取消、门禁无 Attempt、稳定 ID 幂等/重建、敏感列排除及 Schema 1→2 旧草稿保留。
- 仍需分开执行全量单测、Lint、正式签名 Debug/Release 和 API 35 模拟器安装/可见 UI/重启回读验证。
- 停止于本地 Ledger 与 UI 闭环；不得据此宣称真实 OpenRouter、真实模型目录、真实 Key、实际费用或外发验证通过。
