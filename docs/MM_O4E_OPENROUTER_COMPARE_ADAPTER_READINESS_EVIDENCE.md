# MM-O4-E OpenRouter Compare 双分支执行 Adapter Readiness 证据

日期：2026-08-16  
状态：Adapter readiness 已完成；非真实 OpenRouter、HTTP、Key、UI 或设备闭环

## 审计结论与唯一链路

可复用的唯一执行链为：

```text
CompareExecutionApplicationOwner
  -> CompareBatchDispatch (same context / two grants / one lease / Schema 32 identities)
  -> OpenRouterCompareBatchDispatchAdapter
  -> one RealTextExecutionCoordinator
  -> OpenAiCompatibleProviderTransport(OPENROUTER)
```

`CompareExecutionApplicationOwner` 从 `CompareConversationSessionStore.read()` 只注入 content-free
session/branch execution、invocation、attempt、cancel 与 reservation identities。adapter 一次 `take()` 同一
lease，仅在调用栈内创建两条 `ProviderTransportRequest`；provider-facing model ID 逐支取自 grant，未写死
ChatGPT/Claude API model ID。adapter 只接受两个 OpenRouter、ChatGPT+Claude、不同 deployment 的 verified grant，
不调用 Auto Router 或 legacy `OpenRouterInferenceAdapter`。

## P3 接合与冲突处理

审计确认旧 `RealTextExecutionReadyPlan.presetId` 是 Settings/普通 Registry preset 的事实，P3 preflight 还带有
credential presence、setting 与 current-leaf 语义；Compare 的 grant deployment 不能强塞为该 preset。本增量保留
普通 P3 语义，新增 mutually-exclusive `verifiedCompareDeployment` content-free target，供 coordinator idempotency
fingerprint 使用。它保存 logical/deployment/provider/catalog/price metadata，不保存正文、Key 或 endpoint。

每支 coordinator plan 复用 Schema 32 的 assistant/invocation/attempt/execution/cancel/reservation identities；其
terminal 映射为独立 `CompareBranchTerminalIntent`，因此成功/失败/取消不回滚 sibling。batch `Accepted` 只表示两支
都已交给同一个 coordinator，不代表两个模型均成功。

## 凭据、HTTP、receipt 与 Usage 边界

`ProtectedProviderCredentialHandle` 仍为 opaque；默认使用
`DisabledOpenRouterProtectedCredentialHandle`、`DisabledOpenAiCompatibleHttpClient` 与既有 disabled receipt/Usage
ports。构造和测试不调用 `ProviderCredentialStore.loadCredential`，不解密 Key，不注册 AppContainer HTTP client，默认
不会联网。现有 `RoomRealTextExecutionCoordinatorTestPortAdapter` 保持 test-only，未注册为生产事实 owner；本增量不写
P3-I receipt 或 Usage。未来 production-ready adapter 必须单独实现既有 port 的逐 branch 原子 reservation/release/finish
合同，不能以本次 disabled outcome 冒充实际服务事实。

## 自动合同与构建

执行：

```text
JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home \
JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1 \
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests com.nanzhufeng.ai.ai.OpenRouterCompareBatchDispatchAdapterContractsTest \
  --tests com.nanzhufeng.ai.domain.CompareExecutionApplicationOwnerContractsTest \
  --tests com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorContractsTest \
  --tests com.nanzhufeng.ai.ai.OpenAiCompatibleProviderTransportContractsTest \
  --tests com.nanzhufeng.ai.data.MMO4CCompareConversationRoomContractsTest \
  --tests com.nanzhufeng.ai.domain.CompareConversationSessionOwnerContractsTest \
  --tests com.nanzhufeng.ai.domain.RealTextExecutionPreflightContractsTest \
  --tests com.nanzhufeng.ai.data.P3IConversationRealTextExecutionRoomContractsTest \
  --tests com.nanzhufeng.ai.data.RoomRealTextExecutionCoordinatorTestPortAdapterContractsTest \
  --tests com.nanzhufeng.ai.data.UsageLedgerRoomContractsTest
```

已证明：同一 lease 仅取一次、两 branch 的 model/identity route、disabled no-network、opaque credential、provider/model
mismatch fail-closed、same dispatch one-shot、partial success/failure 不回滚 sibling、Schema 32 / receipt / Usage 不含正文、
source guard 无 UI、legacy adapter、credential load、真实 HTTP 或生产 test-port registration。

结果：**51 tests / 0 failures / 0 errors / 0 skipped**；
`./gradlew --no-daemon :app:assembleDebug`：`BUILD SUCCESSFUL`。

## 未证明事项与停止点

未读取/解密 Key，未发真实 HTTP，未读取历史正文或测试正文作外发，未接 app-visible confirmation，未创建 production
receipt/Usage port，未形成真实 Provider attempt/response/Usage/费用事实。未修改 UI/Desktop/文案/布局、设备、OPPO、安装、
发布或 Schema。真实服务验收仍须用户当次在应用内可见确认与授权资料，并另行完成 production receipt/Usage 事务和平台
credential/HTTP adapter 审计。
