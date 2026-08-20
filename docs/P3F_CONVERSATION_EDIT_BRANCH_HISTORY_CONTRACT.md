# 南枫 AI P3-F 用户消息编辑与分支历史合同

日期：2026-08-13  
状态：P3 的第六个本地增量；复用 Schema 7，不含 Provider、Key、Authorization、HTTP、图片/附件外发、Projects、Memory 或删除

## 目标、所有权与入口

```text
当前路径上的纯文本 User Message
→ ConversationBranchHistory（可编辑性与全部 leaf 分支历史投影）
→ EditConversationUserMessageUseCase / SwitchConversationBranchUseCase
→ ConversationTreeService（不可变修订与 current leaf）
→ ConversationRepository（Room 原子保存与重建）
→ 本地工作区编辑面与分支选择
```

| 概念 | 唯一所有者 | 公开入口 | 禁止的平行规则 |
|---|---|---|---|
| 可编辑性与分支叶历史 | `ConversationBranchHistory` | 工作区读取投影 | UI 以索引、展示缓存或仅 assistant leaf 判断分支 |
| 用户消息修订 | `ConversationTreeService` | `EditConversationUserMessageUseCase` | UI 或 DAO 原地改写既有消息 |
| 当前分支切换 | `ConversationTreeService` | `SwitchConversationBranchUseCase` | UI 直接修改 current leaf |
| 持久化与重建 | `ConversationRepository` | 两个用例 | UI 维护另一份树或分支真值 |

## 本地语义与边界

- 仅当前根→叶路径上的、内容全为 `ContentBlock.Text` 的 `USER` 消息可显示编辑入口。带附件、Tool 内容、隐藏兄弟与 assistant/system/tool 消息不出现编辑入口；这不是删除附件或扩大附件能力的授权。
- 确认编辑后，原节点永不改写；同父创建新的 `USER` 兄弟，`MessageRevision` 指向原节点，选中新叶并更新时间。取消、关闭、空白文本、读取失败或事务失败均不改变任何本地事实。
- 分支历史从同一 `ConversationSnapshot` 的所有叶节点投影，稳定按 `createdAt ASC, MessageNodeId ASC`。叶节点既可能是 assistant，也可能是刚编辑、尚未继续生成的 user；两者都必须可切回。当前路径仍由 `MessageTree.contextPath()` 唯一决定。
- P3-F 不自动创建 assistant、Invocation、Runtime Event、Candidate、Usage、费用或 Provider Attempt；不会读取 Key、构造 Authorization、发送 HTTP 或外发文字/图片。`OpenRouterEgressPolicy.Disabled` 保持不变。

## UI 与验证

- 编辑面为纯白 `#FFFFFFFF` 的 App 自有 Dialog，24dp 圆角；确认与取消控件、按压/focus/ripple 与各自圆角一致。页面只说明“本机创建新分支”，不宣称已重答或连接 Provider。
- 定向合同覆盖：不可变原节点、修订谱系、当前路径隔离、所有 leaf（含 user leaf）可见且可切回、带附件用户消息不可编辑、取消/空白拒绝不写入，以及 Room 重建后分支事实一致。
- Schema 7 不变，不新增迁移。全量单测、Lint、正式签名包、模拟器、真实 Provider、真机/OPPO、图标视觉与发布依旧分层报告；P3-F 的本地结果不替代其中任何一项。
