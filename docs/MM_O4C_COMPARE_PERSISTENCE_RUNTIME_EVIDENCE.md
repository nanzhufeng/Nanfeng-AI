# MM-O4-C Compare 持久化与 Runtime 基座证据

日期：2026-08-16  
状态：Schema 32 / Room 合同已完成；非真实 Provider、UI 或设备闭环

## 结论

`CompareConversationSessionStore` 是 Compare session 持久化的唯一公开 owner，Room 实现为
`RoomCompareConversationSessionStore`。它以 Schema **31→32** 追加最小的 content-free Compare 事实：

- session 与一次性 confirmation identity；
- 两个 branch 各自的 assistant、Invocation、Attempt、future execution、cancel 与 reservation identity；
- branch 各自的 runtime/checkpoint 投影及 append-only runtime event；
- terminal、follow-up、adopt 与 synthesis 的 typed idempotency facts。

`AppContainer` 的生产 Room builder 已显式注册 `MIGRATION_31_32`；除这一项数据库迁移接线外，
没有注册 Compare Store、执行器、UI、Provider 或其他生产消费者。

所有 assistant 只是既有 `MessageTree` 的未来 identity，本增量**不**向 `message_nodes` 插入无内容节点；
也不改变 `Conversation.currentLeafMessageId`。因此 `message_nodes`/`message_content_blocks` 仍是唯一消息树真相，
且 Compare 不会新建平行树。

现有 P3 runtime 保持原样：`conversation_runtime_states` 的 `conversationId` 唯一约束、
`ConversationRuntimeStateMachine` 的 current-leaf 前提、P3-I receipt 和 Usage Ledger 均未被重写。Compare
branch 的 `executionId` 与 reservation token 是未来逐 branch 接合的引用，不是已发生的 P3-I receipt、Usage、
Provider attempt、文本或网络事实。

## 自动合同

执行：

```text
JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home \
JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1 \
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests com.nanzhufeng.ai.data.MMO4CCompareConversationRoomContractsTest \
  --tests com.nanzhufeng.ai.domain.CompareConversationSessionOwnerContractsTest \
  --tests com.nanzhufeng.ai.data.P3BConversationRuntimeRoomContractsTest \
  --tests com.nanzhufeng.ai.data.P3IConversationRealTextExecutionRoomContractsTest \
  --tests com.nanzhufeng.ai.data.UsageLedgerRoomContractsTest \
  --tests com.nanzhufeng.ai.data.P6JUpgradeChainRoomContractsTest
```

结果：**20 tests / 0 failures / 0 errors / 0 skipped**。

`./gradlew --no-daemon :app:assembleDebug`：`BUILD SUCCESSFUL`。

覆盖：

1. Schema 31→32 migration 已注册到生产 Room builder；它保留代表性的 Conversation、旧 P3 runtime、P3-I receipt 与 Usage 行，并保留旧 runtime 唯一索引；无 destructive migration。
2. 双 branch identity / execution / reservation reference 彼此独立，持久化重建后可读回。
3. `Conversation.currentLeafMessageId`、既有 `message_nodes` 和旧 P3 runtime state 在 Compare session/terminal 后保持不变；没有 reserved assistant node。
4. 成功、失败、取消独立终态；一个 sibling 不覆盖另一个。
5. session 与 terminal 的相同 intent replay、冲突 fail-closed；follow-up 排除 sibling，adopt 保留 sibling，synthesis 只在双成功后创建独立 invocation/new context/new gate。

## 未证明事项与停止点

未读/写 Key，未发 HTTP，未调用 OpenRouter 或原生 Provider，未生成 assistant 内容，未创建 P3-I receipt 或 Usage
事实，未改 UI/AppContainer/普通 Auto 默认，未启动或安装 Android/OPPO/其他设备，也未发布。真实 Compare 执行仍需一个
独立授权增量，以 content lifecycle、逐 branch P3-I/Usage 事务和真实 egress consent 为前提；不能把本证据、Room
重启读回或构建成功写成 Provider、UI 或设备闭环。
