# MM-O4-D Compare 临时内容租约与双分支事务接合证据

日期：2026-08-16  
状态：临时 lease / content-free dispatch 接合已完成；非真实 Provider、HTTP、UI 或设备闭环

## 生命周期审计与结论

MM-O4-A 的 `CompareExecutionApplicationOwner.confirm()` 原本在返回 content-free
`CompareExecutionGrantedPlan` 前释放唯一原文引用。该行为对确认阶段安全，但意味着后续执行无法从
持久化 SHA-256 取回原文；hash 只能用于匹配校验，绝不能成为恢复内容的来源。

现 `CompareExecutionApplicationOwner` 同时是唯一 Compare dispatch/application owner。确认成功后，调用方
仍只拿到 content-free grants；owner 自身在 process memory 中保留唯一 `CompareEphemeralTextLease`，并只在其
同步临界区将同一 `CanonicalContextSnapshotRef`、两支已确认 grant 和同一 lease 交给
`CompareBatchDispatchPort`。port 的合同只有批量 `Accepted` 或整体 `Rejected`，默认实现 fail-closed，故不存在
“先交 branch 1、branch 2 未知”的表面成功。

租约在取消、过期、确认范围变化、Store 拒绝、port 拒绝、port 异常及 accept 后均被释放。dispatch intent 重放或
冲突被拒绝；旧 confirmation 的第二次 dispatch 只能得到 `DISPATCH_RETRY_REQUIRES_FRESH_CONFIRMATION`，不会再次
交付原文。

## Schema 32 接合与 restart 边界

没有 Schema 33。`CompareConversationSessionStore` 复用 Schema 32 的
`compare_branch_runtime_events` 追加两支一致的 `DISPATCH_INTENT_RECORDED`、`DISPATCH_ACCEPTED` 或
`DISPATCH_REJECTED_RETRY_REQUIRED` 事实；payload 只为 ID/hash 派生 fingerprint。Room readback 显式投影：

- 未发生 dispatch：`RETRY_REQUIRED`；
- intent 已记录但进程未拿到 outcome：`PENDING_ACCEPTANCE_RETRY_REQUIRED`；
- 拒绝或异常：`REJECTED_RETRY_REQUIRED`；
- 两支完整接受：`ACCEPTED`。

任何 restart 时原文都不存在，因此前三种状态都要求重新提供 hash 匹配的新文本并重新确认；绝不从 hash 恢复、静默重发
或消费旧授权。Schema 32 既有 `message_nodes`、`Conversation.currentLeafMessageId`、P3
`conversation_runtime_states`、P3-I receipt 和 Usage Ledger 没有被写入或改义。

## 自动合同与构建

执行：

```text
JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home \
JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1 \
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests com.nanzhufeng.ai.domain.CompareExecutionApplicationOwnerContractsTest \
  --tests com.nanzhufeng.ai.data.MMO4CCompareConversationRoomContractsTest
```

结果：**14 tests / 0 failures / 0 errors / 0 skipped**。

新增/回归合同证明：Auto 零调用与原 O4-A 双 grant 合同仍在；同 lease + 同 context + 两 grants 仅经一个 batch port；
accepted 后 one-shot/no replay；rejection、exception、expiry、cancel 与 session mismatch 均释放原文；Schema 32
Room readback 无正文并重建 retry-required/accepted；`currentLeaf` 与 P3 runtime 仍不变；source guard 不存在
`AppContainer`、Android UI、HTTP、API Key 或真实 transport request 接线。

扩展 O4-A/B/C + Room 回归：**42 tests / 0 failures / 0 errors / 0 skipped**；
`./gradlew --no-daemon :app:assembleDebug`：`BUILD SUCCESSFUL`。

## 未证明事项与停止点

本轮只有接口、default fail-closed 和 test fake；没有 OpenRouter/native Provider、Key、HTTP、网络、transport、UI、
AppContainer 注册、真实 branch execution、Provider attempt、P3-I receipt、Usage、assistant 正文、模拟器、OPPO、
Desktop、安装或发布证据。真实 Provider 后的 branch partial success/failure/cancel 仍属于后续独立 branch runtime
增量，且不得回滚 sibling。
