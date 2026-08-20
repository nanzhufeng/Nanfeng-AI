# MM-O4-B Compare Session 与 Conversation Tree 纯领域接合证据

日期：2026-08-16  
状态：纯 domain IR/adapter plan 已完成；非持久化、非真实 Provider/UI/设备闭环

## 审计与结论

可直接复用的唯一树真值是 `ConversationSnapshot`、`MessageTree`、`ConversationTreeService` 和
`ConversationBranchHistory`：它们已表达 conversation、不可变 MessageNode、parent/sibling、current leaf 与
context path。MM-O4-B 因此不新建 Compare 消息树，也不在内存中复制任何内容；`CompareConversationSessionOwner`
只在 plan 时用 `MessageTree` 验证同一个 conversation、同一个当前 user parent leaf 和同一个 Canonical Context。

当前 P3 的可复用运行事实是 `ConversationRealTextExecutionRequest` 的 content-free execution identity、
`ConversationAttemptLineage` 的 invocation/attempt 谱系和 `ConversationRuntimeStateMachine` 的 assistant 投影规则。
但它们尚不能直接承载 Compare：runtime repository 每个 conversation 只读取一个 `ConversationRuntimeState`，而 state
machine 的 start 会把新 assistant 写成 current leaf，后续事件亦要求该 assistant 仍是 current leaf。因此两个并发
Compare branch 不能在不改 Schema/DAO/runtime 合同的前提下持久化或执行。本轮正确只预留 identity/状态 IR，不调用这些
写路径，也不伪装为真实 execution。

`CompareExecutionGrantedPlan`/grant 现在额外携带同一 text SHA-256、expiry 和 one-shot identity。唯一
`CompareConversationSessionOwner` 只接受未过期且未消费的 grants，输出两个独立的 assistant node、Invocation、
Attempt 和 cancellation identity，绑定同一个 conversation、parent user、context 和 grants。初态为 `RESERVED`；
success/failure/cancel 各自独立，任何失败或取消不会回滚已成功的 sibling。输出始终不改 Conversation current shared
branch，也不创建 MessageNode。

成功 branch 的 follow-up plan 绑定该 branch assistant 为唯一 tail，并把另一个 assistant 列为排除集，要求未来 context
builder 只沿该 branch 构造新的 Canonical Context。adopt 只对一个成功 branch 生成审计 plan，保留所有 sibling，不覆盖
或删除任何 branch。Synthesis 默认没有计划；两个 branch 都成功后的显式请求才会预留第三个独立 assistant/Invocation/
Attempt，并绑定两个 source branch、全新 Canonical Context 与默认未确认、独立的 consent/budget gate；该 plan 不能被当作
任一原 Compare 回答。session、terminal、follow-up、adopt 与 synthesis 都对同一 intent replay，对冲突或第二次意图失败关闭。

## 自动合同与构建

执行：

```text
JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home \
JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1 \
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests com.nanzhufeng.ai.domain.MultiModelOrchestrationContractsTest \
  --tests com.nanzhufeng.ai.domain.MultiProviderModelRegistryContractsTest \
  --tests com.nanzhufeng.ai.domain.DirectExecutionApplicationOwnerContractsTest \
  --tests com.nanzhufeng.ai.domain.OpenRouterVerifiedMultiProviderRegistryProjectionContractsTest \
  --tests com.nanzhufeng.ai.domain.CompareExecutionApplicationOwnerContractsTest \
  --tests com.nanzhufeng.ai.domain.CompareConversationSessionOwnerContractsTest \
  --tests com.nanzhufeng.ai.domain.P3AConversationDomainContractsTest \
  --tests com.nanzhufeng.ai.data.P3AConversationRoomContractsTest \
  --tests com.nanzhufeng.ai.domain.P3BConversationRuntimeContractsTest \
  --tests com.nanzhufeng.ai.data.P3BConversationRuntimeRoomContractsTest \
  --tests com.nanzhufeng.ai.data.P3CConversationActionLineageContractsTest \
  --tests com.nanzhufeng.ai.domain.P3HConversationAttemptHistoryContractsTest \
  --tests com.nanzhufeng.ai.domain.P3IConversationRealTextExecutionContractsTest \
  --tests com.nanzhufeng.ai.data.P3IConversationRealTextExecutionRoomContractsTest \
  --tests com.nanzhufeng.ai.data.P3IConversationRealTextTestTransportAdapterContractsTest \
  --tests com.nanzhufeng.ai.ai.P2MRealTextExecutionBridgeContractsTest \
  --tests com.nanzhufeng.ai.ai.P2MTranscriptBindingContractsTest \
  --tests com.nanzhufeng.ai.domain.P6GModelRouterContractsTest \
  --tests com.nanzhufeng.ai.ui.P6GUnifiedChatFirstUiContractsTest \
  --tests com.nanzhufeng.ai.ui.P3JNormalChatExplicitEgressContractsTest \
  --tests com.nanzhufeng.ai.domain.NormalChatRealTextExecutionOwnerTest

./gradlew --no-daemon :app:assembleDebug
```

结果：**91 tests / 0 failures / 0 errors / 0 skipped**，`assembleDebug` 为 `BUILD SUCCESSFUL`。

新增 7 项 MM-O4-B 合同覆盖：既有 user leaf 上的稳定双 branch identity 且 tree 不变、grant one-shot/idempotent
session replay、分支成功/取消独立与 follow-up sibling exclusion、成功分支唯一 adopt 与 sibling preservation、默认无
synthesis 且两成功支/新 context/新 gate 才可第三 invocation、source 不依赖 Repository/Runtime/transport/Direct/content，
以及 grant expiry fail-closed。MM-O4-A 的 contract 同时回归 text hash/expiry binding。

## 未证明事项与停止点

没有执行模型、没有生成或保存 assistant 内容、没有调用 ConversationTreeService mutation、Room/DAO、P3 Runtime、
Invocation/Usage、Key、HTTP、transport、UI、AppContainer、设备、OPPO 或发布。未读取 app-private verified snapshot，
没有 fixture/UI 猜测。真实 Direct/Compare 仍须既有 P2-J 目录验证路径提供 verified OpenRouter snapshot；Compare 的
双分支写入、部分失败/取消恢复、重启、追问、adopt、synthesis 和 Usage 聚合必须由下一独立、先设计持久化/runtime 接合的
increment 完成，且不得创建第二棵消息树。
