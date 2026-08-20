# MM-O4-A Compare 纯 domain/application 核心证据

日期：2026-08-16  
状态：纯 domain/application 增量已完成；非真实 Provider、UI 或设备闭环

## 结论

`CompareExecutionApplicationOwner` 是唯一新增的 Compare application owner，当前没有注册到
`AppContainer`、Activity、ViewModel、Workspace 或任一 UI。它只使用 `MultiModelOrchestrator` 的
`COMPARE` plan 与 `MultiProviderModelRegistry` 的精确解析；没有 `DirectExecutionApplicationOwner`、
Direct 二次确认、credential、HTTP、transport execution、Runtime、Usage Ledger、Room 或数据库 Schema 依赖。

MVP 只接受两个目标：ChatGPT 与 Claude，且二者必须是不同 Logical Model、不同 Deployment。一个
`CompareExecutionApplicationRequest` 只携带一份 `CanonicalContextSnapshotRef` 与一份临时 text-only 输入，
请求顺序生成稳定的 `branch:1` / `branch:2`。`AutoRoutingPort` 未被 Compare 调用；附件、第三目标、重复
逻辑模型/部署、非 ChatGPT+Claude、无目录/未验证投影、未知价格均在确认前失败关闭，不替换分支或 Provider。

每支都以共享 `ConservativeInputBillingBudget` 对同一 UTF-8 文本和输出上限计算保守上界，覆盖 CJK、空白、
emoji 与安全算术。两个分支只有在相同 ISO 币种时才用安全加法产生汇总上限；跨币种明确拒绝，分支或汇总溢出
同样拒绝。该上界不是 Provider tokenizer、实际 token 或实际费用。

唯一汇总确认默认未勾选、5 分钟到期且一次消费；它只保存 request id/fingerprint、context hash、text SHA-256、
实际 Provider/model/deployment、catalog/price/currency、两支预算和总预算。确认后的结果仅为两个
content-free、独立的 `CompareBranchExecutionGrant`；默认 `NOT_REQUESTED` synthesis 与
`EXPLICIT_ADOPTION_ONLY` shared context。它们不能调用 transport，也不触发 Direct owner 的第二次确认。
每个 grant 及返回的 granted plan 还携带相同的 text SHA-256、confirmation expiry 与稳定 one-shot identity，
供 MM-O4-B 在不接触原文的前提下验证后续 session 绑定。

待确认原文只存在于有界 `issued` map。取消、过期、确认、目录/价格变化、或确认前任一目标替换，都会立即释放
原文并使整个汇总失效；此后仅保留 TTL/容量受限的 content-free replay 拒绝事实。单目标变化不允许部分静默替换，
必须重新发起完整 Compare。

## 自动合同与构建

执行：

```text
JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home \
JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1 \
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests com.nanzhufeng.ai.domain.CompareExecutionApplicationOwnerContractsTest \
  --tests com.nanzhufeng.ai.domain.MultiModelOrchestrationContractsTest \
  --tests com.nanzhufeng.ai.domain.MultiProviderModelRegistryContractsTest \
  --tests com.nanzhufeng.ai.domain.DirectExecutionApplicationOwnerContractsTest \
  --tests com.nanzhufeng.ai.domain.OpenRouterVerifiedMultiProviderRegistryProjectionContractsTest \
  --tests com.nanzhufeng.ai.domain.RealTextExecutionPreflightContractsTest \
  --tests com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorContractsTest \
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

结果：**74 tests / 0 failures / 0 errors / 0 skipped**，`assembleDebug` 为 `BUILD SUCCESSFUL`。

新增 Compare 合同的 8 项覆盖：Auto 零调用与同一 Context/text hash、两实际收件人/目录/价格预算、附件/第三目标/
重复/非首组拒绝、目录不可用与未知价格、跨币种与溢出、确认后仅生成两个 content-free grants、取消/过期/重复消费/
原文释放、确认前单目标替换整体失效、以及目录变化不得局部静默替换。回归覆盖 MM-O1、MM-O2、MM-O3-A2、
MM-O3-B、P3/P2-M、P6-G 和 P3-J。

## 未证明事项

本轮未读取或写入 Key，未发 HTTP，未读取 app-private verified snapshot，未通过 fixture、P6-G、展示名或 UI 猜测
目录状态；未改 UI/P3-J/AppContainer、Room Schema、Usage、设备、OPPO、安装或发布。真实 Direct 仍需既有 P2-J
目录验证路径提供 verified OpenRouter snapshot；Compare 也尚未具备两支真实执行、部分失败、分别取消、重启恢复、
分支追问、Usage 聚合或任何真实 Provider/UI/设备证据。
