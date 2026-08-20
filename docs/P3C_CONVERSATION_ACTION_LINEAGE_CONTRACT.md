# 南枫 AI P3-C 继续、重试与换模型重答合同

日期：2026-08-12  
状态：本地确定性编排、Schema 5→6；不含真实 Provider、Key、RunSpec、HTTP 或图片调用

## 目标与唯一所有者

```text
UI（只提交稳定 intent）
→ ConversationActionOrchestrator（动作语义、前置条件、目标选择）
→ ConversationRuntimeStateMachine（单次运行投影与终态）
→ RoomConversationRepository（一个事务：lineage + started event + MessageNode + runtime state）
→ Conversation Domain（树、当前叶、上下文路径） / Invocation Ledger（每次尝试的安全元数据）
```

| 概念 | 唯一所有者 | 禁止事项 |
|---|---|---|
| 消息树、分支与 current leaf | Conversation Domain | UI 或 Repository 自行拼路径、覆盖历史消息 |
| continue/retry/change-model 意图与前置条件 | Conversation Action Orchestrator | Runtime 状态机猜测用户动作，UI 直接写 Room |
| 一次运行的事件顺序、部分输出与终态 | Conversation Runtime State Machine | Action 用例修改旧 assistant 正文或伪造终态 |
| 安全尝试谱系 | Invocation Ledger 的 `ConversationAttemptLineage` 扩展 | 在消息、Ledger 或 lineage 保存 Key、正文、Prompt、原始响应、Token/费用原文 |

`ConversationAttemptLineage` 只保存：唯一 Invocation ID、稳定 intent ID、会话、动作种类、来源/目标消息 ID、前一次 Invocation ID、目标 Provider/Model/Harness/已验证 Registry Snapshot 的安全 ID、可空价格版本/币种/单价以及创建时间。`null` 代表价格未知；确定性本地 fixture 的 `0` 代表已知本地零费率，二者绝不混同。

现有 P2-F `InvocationRecord` 仍是已完成 AI Task 的四层账本。本阶段没有真实 Provider Attempt；P3-C 的启动中/终态运行元数据由上述 append-only lineage 与 P3-B runtime state 共同构成 Invocation Ledger 的对话扩展，不伪造网络 Attempt。

## 三种动作与树转移

| 动作 | 接受条件 | 新节点位置 | 关联 |
|---|---|---|---|
| `CONTINUE` | 当前叶是含非空正文的 `CANCELLED` 或 `FAILED` assistant，且没有运行中的状态 | 原部分 assistant 的子节点 | `originMessageId=原部分 assistant`，`previousInvocationId=原 Invocation`；原部分输出永不拼接或改写 |
| `RETRY` | 当前叶是带 Invocation 的终态 assistant，父节点为 user，且没有运行中的状态 | 原 assistant 的同一 user 父节点下的新 assistant 兄弟 | `originMessageId=原 assistant`，`previousInvocationId=原 Invocation`；原回答保持可切回 |
| `CHANGE_MODEL` | 满足 retry 条件，且显式选中的本地 Provider/Model/Harness 通过当前已验证 local-fixture registry | 同一 user 父节点下的新 assistant 兄弟 | 除上述关联外记录目标选择；若来源已有选择，目标 Model/Harness 必须不同 |

三者都会生成新的 Invocation ID、新 message ID 与新的 `RUN_STARTED`；绝不复用旧 Invocation，也不伪装成旧运行的后续事件。`CONTINUE` 的新 assistant 是新运行的续写结果，不修改原部分输出，因此当前路径明确呈现“部分事实 → 续写结果”。`RETRY` 与 `CHANGE_MODEL` 切换 current leaf 至新兄弟版本。

## 选择、门禁与幂等

- 目标选择只能来自当前已验证的本地 fixture `ModelRegistrySnapshot`；它不读取 Key、不访问设置凭据、不构造 Authorization 或网络请求。要求 Text 与 Streaming 能力；不满足在任何网络前拒绝。
- 仅安全 ID 和可空价格元数据进入 lineage。真实 Registry、价格与能力仍由 P2-J/P2-K 的独立合同控制；OpenRouter production egress 继续为 `Disabled`。
- intent 含稳定 `ConversationActionIntentId`。相同 ID 且同一请求指纹重放同一投影；同 ID 不同内容拒绝。UI 不在 Activity 恢复时自动重提交；运行中同类按钮禁用。
- 非当前路径、角色非 assistant、没有部分输出的 continue、非 user 父的 retry/change-model、非终态来源、运行中、目标未验证/能力不符均确定拒绝；没有 lineage、MessageNode、runtime state 或 event 的半成功可见状态。

## 状态与事务表

| 当前 | intent | 条件 | 结果 |
|---|---|---|---|
| 无运行或终态 | continue/retry/change-model | 通过上表前置条件 | 一个事务写 lineage、`RUN_STARTED`、新 assistant 投影、runtime state 和 current leaf |
| `STREAMING` | 任一 P3-C 动作 | 一律拒绝 | 无写入 |
| 已接受 intent 重放 | 同请求指纹 | 回读原投影 | 幂等 `Replayed` |
| 已接受 intent 冲突 | 不同请求指纹 | 拒绝 | 无写入 |
| Room 任一步失败 | 任一动作 | 事务回滚 | 原树、current leaf、runtime 和 lineage 均不变 |

Schema 5→6 只创建 `conversation_attempt_lineages` 和唯一/查询索引，不修改、不删除、不清空 P2、P3-A 或 P3-B 表。迁移失败不得清库。

## 最小可见 UI 与验证

本地对话卡展示当前分支/版本、可切回的历史叶、运行状态和 lineage 的安全选择摘要；在可用状态提供继续、重试与换模型重答。换模型使用 App 自有纯白选择 Dialog，内容面为 `#FFFFFFFF`，按钮/卡片/焦点/ripple/shadow 共享圆角轮廓。所有入口只运行 deterministic fixture。

定向测试必须覆盖三动作成功与拒绝、旧分支不变、兄弟隔离、current leaf/context、Invocation/lineage 唯一性、重复 intent、事务回滚、Room/进程重建、Schema 5→6 保留和敏感列排除；API 35 仅验证本地生命周期与分支切换，不代表真机、OPPO、真实服务、Token/费用或发布验收。
