# MM-O3-A Direct 生产应用边界证据

日期：2026-08-16  
状态：MM-O3-A2 纯 domain/application 安全收敛完成；Direct 尚未接入普通聊天 UI 或真实 Provider

## 结论

`DirectExecutionApplicationOwner` 是 MM-O3-A 唯一新增的 Direct 应用 owner。它先要求
`MultiModelOrchestrator` 生成 `DIRECT` plan，再经 `MultiProviderModelRegistry` 精确解析 Logical
Model、Deployment、Provider 与 provider-facing model ID；不引用 P6-G Router，且没有 fallback、
模型替换或 Provider 替换分支。

确认在内存中一次性绑定：request id、execution fingerprint、Canonical Context hash、Logical Model、Deployment、
Provider、provider-facing model ID、text-only 类别、目录/价格版本、币种、文本 SHA-256、输出上限、最大预算和 5 分钟过期；`scopeFingerprint` 是不含原文的 SHA-256。未勾选、取消、重复消费、过期、附件、目录变更、未知价格和失效部署均失败关闭。确认后的唯一 P3
路径为既有 `RealTextExecutionPreflightOrchestrator`（仅 credential presence）再到既有默认拒绝的
`RealTextExecutionCoordinator`；没有 Key bytes、HTTP client、Room、Usage Ledger 或 AppContainer
生产注册。

`ConservativeInputBillingBudget` 是 Direct confirmation 与 P3 reservation 唯一共享的纯领域上界：以 UTF-8
字节作为保守输入计费 token 上界，故中文、空白和 emoji 不会获得“每四字符一个 token”的乐观折减。该值只用于
预算保守上界，不是 Provider tokenizer 结果、实际 token 或实际费用。安全算术溢出和未知价格统一失败为
`PRICE_UNAVAILABLE`，绝不以零值或确认绕过。

原文只在待确认的 `issued` 项中暂存。取消、过期、目录/范围变化，以及进入 confirm 后的 preflight/coordinate
终态都会立即移除该项；随后最多保留 256 条、10 分钟 TTL 的 content-free terminal/replay facts，用于拒绝重复
消费。确认对象、结果和该 replay facts 均不含原文。

## P3-J WIP 审查与隔离判断

| 文件 | 审查事实 | 本次判断 |
|---|---|---|
| `NormalChatRealTextExecutionOwner.kt` | 仅生成未注册、不可确认的 disclosure，且没有 confirm/send API | 保留，非 MM-O3-A 入口 |
| `ConversationFoundationViewModel.kt` | `submitCurrentDraft()` 仍只调用本地 `submitDraft.execute`；confirmation 仅为内存态 | 保留，不注入 Direct owner |
| `ConversationWorkspace.kt` | 原 `onSubmit` 先请求 confirmation、再本地提交，造成已清理草稿后仍显示不可确认模态 | **最小隔离修正** |
| `AppContainer.kt` | 只注入旧 fail-closed normal owner，没有 Direct/P3 transport 注册 | 保持不动 |
| `NanfengAiActivity.kt` | 只将旧 fail-closed owner传入 ViewModel Factory | 保持不动 |
| `P3JNormalChatExplicitEgressContractsTest.kt` | 原静态守卫未禁止 Composer 同时触发 confirmation | 增加仅行为接线/布局 token 守卫 |
| `NormalChatRealTextExecutionOwnerTest.kt` | 覆盖旧 owner 的 unregistered/附件/过期语义 | 保持不动并回归 |

实际 P3-J 修改的行级职责：

- `ConversationWorkspace.kt:615-622`：仅移除 `onRequestNormalChatExternalSendConfirmation()` 回调；保留
  `DraftComposer`、WORK/CHAT 计数与唯一 `onSubmitDraft()`。没有任何 Composable 结构、测量、位置、
  尺寸、颜色、圆角、阴影、文案或 Dialog 外观改动。
- `P3JNormalChatExplicitEgressContractsTest.kt:17-45`：锁定 Composer 不再触发 confirmation，仍执行
  本地提交，并锁定既有 bottom overlay、18dp padding、36dp token 与 `DraftComposer` 结构。
- `P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md`：记录上述审查和隔离；保留但未注册的
  Dialog 不得被视为 MM-O3-A 可见入口。

## 自动验证

```text
JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home \
JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1 \
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests com.nanzhufeng.ai.domain.DirectExecutionApplicationOwnerContractsTest \
  --tests com.nanzhufeng.ai.domain.RealTextExecutionPreflightContractsTest \
  --tests com.nanzhufeng.ai.domain.MultiModelOrchestrationContractsTest \
  --tests com.nanzhufeng.ai.domain.MultiProviderModelRegistryContractsTest \
  --tests com.nanzhufeng.ai.domain.P6GModelRouterContractsTest \
  --tests com.nanzhufeng.ai.domain.NormalChatRealTextExecutionOwnerTest \
  --tests com.nanzhufeng.ai.ui.P3JNormalChatExplicitEgressContractsTest

./gradlew --no-daemon :app:assembleDebug
```

结果：定向 JVM 合同为 **36 tests / 0 failures / 0 errors / 0 skipped**，`assembleDebug` 为 `BUILD SUCCESSFUL`。Direct/Preflight 合同覆盖精确部署解析、完整 scope fingerprint、Auto 零调用、
中文/空白/emoji UTF-8 上界、算术溢出、Direct confirmation 与 P3 reservation 的同一预算 owner、附件/失效部署/未知价格的 presence 前拒绝、未勾选/取消/过期/重复消费后的原文释放、缺凭据仅 presence、目录变更失效，以及默认 coordinator 在 Usage reservation 拒绝处停止、没有 transport。

## 未证明事项

普通聊天 UI 尚未接入 Direct owner；没有真实 Provider、HTTP、Key 读取、Usage、Room receipt、重启
readback、Android/Desktop 视觉、设备、OPPO、签名安装或发布证据。自动测试和 Debug 编译不替代其中任何一项。
