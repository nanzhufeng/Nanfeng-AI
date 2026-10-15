# 南枫 AI P3-B 类型化流事件与本地对话运行状态机合同

<!-- contract-governance:start -->
> **合同口径：领域／模块限定合同。** 领域入口：[ 消息与动作领域 ](P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** 规范化运行事件、Room原子投影与完成后可选元数据追加；fixture不定义生产外发开关
<!-- contract-governance:end -->

日期：2026-08-12  
状态：运行事件与 Room 原子投影的限定合同；Schema 4→5及本地 fixture 验收是2026-08-12阶段事实，不是当前整机联网状态

## 目标、边界与所有权

2026-10-15：完成后的可选 Provider 思考／工具协议只经 `retainCompletedProviderContinuation` 追加到已完成的对应 assistant。Room 同事务核对 invocation、事件、消息和完成 checkpoint；不得改正文、终态、费用、标题、草稿或活动时间。同值重放无变化，冲突内容／其他消息拒绝。仅增加内容修订供同步观察，不增加 Schema 或伪造新运行事件。追加失败仍保留已完成正文／标题／费用并如实提示；普通 `save(snapshot)` 的消息不可改写规则保持。

```text
DeterministicFixtureStreamingAdapter
→ AiRuntimeEvent（版本化、规范化）
→ ConversationRuntimeStateMachine（唯一投影/终态所有者）
→ RoomConversationRepository（同事务事件事实 + MessageNode + checkpoint + runtime state）
→ ViewModel（观察状态、调用 UseCase）
```

| 概念 | 唯一所有者 | 明确不做 |
|---|---|---|
| 规范化事件与安全边界 | AI Event Protocol / `AiRuntimeEvent` | UI、Room 或日志解析 Provider chunk/JSON |
| 投影、顺序、停止、恢复和终态 | `ConversationRuntimeStateMachine` | Conversation Tree 或 UI 自行判定成功/失败 |
| 树、分支、当前路径 | `ConversationTreeService` / `MessageTree` | runtime 改写兄弟分支 |
| 原子持久化与重建 | `RoomConversationRepository` | Repository 解释分支或 Provider 语义 |
| 每次尝试的唯一 Invocation | Invocation ID / 后续 `Invocation Ledger` | 正文进入 Ledger |

确定性 fixture 路径不读取 Key、不发送 HTTP，其安全元数据声明零网络、零凭据读取、零 Provider 原始负载保留。现行普通聊天由 [P3-J](P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md) 的已注册执行器将规范化事件交给同一运行状态／Room owner；Provider 协议、联网、来源与计量分别归 C12 和费用合同，不能从 fixture 的 Disabled 默认值推断生产外发关闭。

## 版本化事件协议

每个 `AiRuntimeEvent` 都有 `schemaVersion`、稳定 `eventId`、`invocationId`、`conversationId`、`messageId`、非负 `sequence`、`emittedAt` 和 `AiRuntimeSecurityMetadata`。最低事件集为：

| 事件 | 投影 |
|---|---|
| `RUN_STARTED` (sequence 0) | 创建空的 `PARTIAL` assistant MessageNode 和 Invocation 关联位 |
| `CONTENT_DELTA` | 只把规范化文本追加至该 assistant 消息正文 |
| `USAGE_UPDATED` | 只更新运行态的可选用量；真实用量真值仍归 Ledger |
| `CHECKPOINT` | 更新 MessageNode 与 runtime state 的可恢复下一序号 |
| `COMPLETED` | 仅已有非空输出时转 `COMPLETE` |
| `FAILED` | 保留已有输出，节点转 `FAILED`，安全错误码受限为大写代码 |
| `CANCELLED` | 保留已有输出，节点转 `CANCELLED` |

事件事实表只保存 ID、顺序、类型、时间、来源与 payload SHA-256 指纹；不保存 delta 正文、Prompt、Provider 原始响应、Key、Authorization、敏感头、图片或附件正文。正文唯一保存在 MessageNode 的规范化内容块中。

## 状态转换与幂等

```text
无运行 --started(0)--> STREAMING --completed--> COMPLETED
                              |--failed-------> FAILED
                              `--cancelled----> CANCELLED
```

- `STREAMING` 只接受精确 `nextExpectedSequence`；断档和乱序拒绝。
- `eventId` 或 `(invocationId, sequence)` 的同指纹重放安全回读；同序号不同指纹拒绝。
- `COMPLETED`、`FAILED`、`CANCELLED` 互斥、不可逆；终态后新事件拒绝。
- 完成前若没有输出，`COMPLETED` 拒绝，避免把未完成结果伪装成功。
- runtime 只投影当前分支叶；分支已切换时后续事件拒绝，兄弟分支不会被污染。
- 用户停止生成 `CANCELLED` 事件；后续重试必须新建 `InvocationId` 和新的 assistant 节点。P3-B 不提供完整重试/换模型 UI。

## Room Schema 4→5 与恢复

新增 `ai_runtime_events`（event ID + invocation/sequence 唯一索引 + 指纹）和 `conversation_runtime_states`（每会话当前运行、checkpoint、状态、可选用量和安全错误码）。`MIGRATION_4_5` 只创建表/索引，绝不删除、重建或清空 P2/P3-A 表。

每次被接受事件在一个 Room 事务中同时写入事件事实、MessageNode 投影、Conversation 当前叶/更新时间和 runtime state。Activity 或进程重建时 Repository 重新读取 MessageNode 与 runtime state，因此可显示部分、完成、失败或取消的真实状态；没有内存事件队列作为真值。

<a id="最小可见-ui-和验证等级"></a>

## 历史2026-08-12最小可见 UI 和验证等级

本地对话卡可创建本地会话、启动确定性流、观察逐段输出、停止并保留部分输出；所有按钮只调用 ViewModel UseCase。卡片与按钮使用现有白色表面、同一圆角 shape 和匹配的 ripple/focus/shadow，不新增 Dialog/Popup。

- 已实现/定向契约：开始→delta→checkpoint→完成、停止、失败、重复重放、同序号冲突、乱序、终态拒绝、分支隔离、Room 重建、Schema 4→5 保留与敏感列排除。
- 待验证：完整 P3 重试/换模型 UI、真实 Provider 流、真实 Token/费用、真实图片、真机/OPPO、发布。fixture、Room 和模拟器不能替代这些门。
