# 南枫 AI P3 当前执行证据矩阵

日期：2026-08-15  
范围：仅当前源码、P2-M v2/P3 合同与定向 JVM/Room 合同测试，以及 P3-J 普通聊天外发的文档/纯静态守卫；本轮不接线。  
结论：P3 的执行模型、隐私边界、P3-I Room receipt 和测试端口均已实现；**普通聊天到真实 Provider 的生产链路仍未接通**。唯一已接线处是 P2-M 可见确认后的后台 bridge，但它注入默认拒绝的 coordinator，且其结果被 legacy P2-M executor 明确忽略；因此不产生 Provider transport、HTTP、Key bytes、Room runtime receipt 或 Usage Ledger 写入。

## 证据口径

- 当前源码和本次测试优先于历史交接与合同；本次发现的“未注册”需分为“无普通聊天生产注册”和“仅 P2-M 受控 fail-closed bridge”。
- `P2M_REAL_TEXT_EXECUTION_CONTRACT.md` 的 v2 只表示冻结了精确 RunSpec，**不表示执行过 HTTP**。其中保存的 v1 历史是 placeholder credential 在 HTTP 前被拒绝、`ProviderCredentialInvalid`、0 Attempt 的安全事实，不是模型成功、真实费用或 v2 成功。
- 本轮未读取模拟器、设备或 credential store。任何界面“未配置”只能说明该界面状态，**不能推导 Key 已保存、Key 不存在或 Key 有效**。
- fake/stub、Room/Robolectric、构建或安装均不能替代真实模型响应；本矩阵只把它们列在各自等级。

## 当前矩阵

| 概念/唯一 owner | 当前实现事实 | 注册/接线状态 | 自动测试（本轮） | 安装 / UI / 真实 HTTP 证据 | 未通过门与下一步 |
|---|---|---|---|---|---|
| P2-M v2 精确一次执行；既有 `P2MRealServiceExecutor` | v2 RunSpec、single-use nonce、DryRun 和安全终态合同已冻结；现有 v1 历史的 placeholder 在 HTTP 前被拒绝，未创建 Attempt。 | 既有设置确认 executor 是特殊入口；它不是普通聊天发送路径。 | P2-M bridge suite 3/3 通过，覆盖可见确认、preflight 和默认拒绝。 | 本轮无安装/UI/HTTP；v2 合同明确没有 HTTP。 | 需新的用户授权、精确确认、有效且受控的 credential broker、单次真实请求和独立真实证据；不得从“未配置”推断 credential 状态。 |
| P3 Provider transport boundary；`ProviderTransport` | 类型化 route/request、事件正规化、取消与安全 metadata 已实现；默认 `DisabledNoNetworkProviderTransport` 无事件、无假回复。 | **无普通聊天生产注册**；无 AppContainer/VM/Composer/Provider/HTTP 接线。 | boundary suite 5/5 通过。 | 无本轮安装/UI/HTTP。 | 需另立授权的可见确认 owner 与生产 adapter；不能由 local stub 结论替代。 |
| P3 preflight；`RealTextExecutionPreflightOrchestrator` | presence-only credential、已验证 registry、瞬时文本、费用确认与附件安全 summary 生成 `Ready/Blocked`，不写 ledger/Room。 | **仅 P2-M 受控 bridge 构造并调用**；不是普通聊天入口。该 bridge 固定合成 text fixture、空附件。 | preflight suite 5/5 通过；bridge suite 覆盖 ready→默认拒绝。 | 无本轮安装/UI/HTTP；没有 Key bytes 读取证据。 | 合同“未注册”须按当前源码校正为“未在普通聊天注册”；未来生产 egress 仍要另行授权并原子接入最终 P1/P0/P3 owner。 |
| P3-J 普通聊天显式外发确认；未来 `NormalChatRealTextExecutionOwner` | 已冻结普通聊天的唯一 future owner、确认披露、text-only 附件排除、5 分钟 scope/费用失效、运行/恢复、调用记录与接线顺序。 | **不存在、无 AppContainer/VM/Composer/Provider/HTTP 注册**；普通 `submitCurrentDraft()` 仍只提交本地草稿。 | P3-J static suite **2/2** 通过，仅守卫当前本地路径与文档必填门；不构造 P3 production owner。 | 无 UI 改动、安装、Key、HTTP、费用或真实 Provider。 | 只有新的实现授权才可创建该 owner；必须先完成可见确认、P1/P0/P3 一致性和 credential broker 的单独审计，不能以 P2-M bridge 或本合同宣称已接通。 |
| P3 coordinator；`RealTextExecutionCoordinator` | start/partial/terminal、取消、replay/conflict 与 release 规则已实现；默认 reservation/runtime ports 拒绝，默认 transport 是 disabled。 | **仅 P2-M 受控 bridge 实例化**，且 executor 忽略 bridge 结果；故 bridge 在 reserve 前失败，不调用 transport/runtime/ledger。 | coordinator suite 4/4 通过；bridge suite 断言默认 `COORDINATOR_USAGE_RESERVATION_REJECTED`。 | 无本轮安装/UI/HTTP。 | 需独立授权的生产 reservation、runtime receipt、可见确认和 transport 原子编排；当前不能称为 P3 执行已上线。 |
| P3 OpenAI-compatible HTTP infrastructure；`OpenAiCompatibleProviderTransport` | 固定 OpenRouter endpoint、严格 body/SSE/JSON 映射、opaque credential handle 与 disabled client 已实现；源码不含 socket/URLConnection/OkHttp/credential decrypt。 | 未注册：无 AppContainer、VM、UI、Room、Ledger 或附件 owner 引用；默认 client fail-closed。 | HTTP transport suite 7/7 通过（in-memory fake）。 | 无本轮安装/UI/真实或 loopback HTTP。 | 需另行授权的 credential broker 和实际 HTTP client，并以一次受控真实请求单独验收。 |
| P3-I receipt；`ConversationRealTextExecutionRepository` / Room 29→30 | `conversation_real_text_executions`、幂等 prepare、不可逆 terminal 和 migration 已实现；只存无正文的 receipt 事实。 | migration 已随 App database 注册；repository 不被当前 UI/transport 构造。 | domain suite 3/3、Room suite 3/3 通过。 | 无本轮安装/UI/真实模型证据。 | 需生产 owner 把 receipt、Invocation/Usage 与真实 runtime 置于可恢复一致性边界。 |
| P3-I scripted adapter 与 coordinator Room test port | `RoomConversationRealTextTestExecutionAdapter` 和 `RoomRealTextExecutionCoordinatorTestPortAdapter` 实现 scripted event、重开读回、事务回滚及 P0 reservation/release fixture。 | 仅 JVM/Robolectric fixture 显式构造；无 AppContainer、VM、P2-M bridge 或聊天入口注册。 | scripted adapter 4/4、Room test port 4/4 通过。 | 无本轮安装/UI/HTTP；不能作为 production Room 接线或 assistant 成功。 | 保持 test-only；真实链路必须另有 production adapter/owner、设备和服务证据。 |

## 本轮定向验证

命令（JBR 21、`--no-daemon`、`-XX:TieredStopAtLevel=1`）运行 `:app:testDebugUnitTest`，选择 P2-M bridge、P3 provider boundary/preflight/coordinator/P3-I、OpenAI-compatible transport、两类 Room adapter 共 9 个 suite：**38 tests，0 failures，0 errors，0 skipped**。

这只达到“已实现 + 定向契约通过”级别。未执行 `assemble`、lint、安装、模拟器 UI、OPPO、真实 Key/credential readback、真实 HTTP、真实 Provider 响应、Token/费用或发布验证；这些均为**无本轮证据**，不是失败后的重试请求。

## 当前未通过门与停止点

1. P3 普通聊天生产 egress owner、最终可见确认和 P1/P0/P3 原子接线不存在；P2-M 的受控 bridge 不替代它。
2. 已授权、可审计的 credential broker 与一个实际 HTTP client 未接入；不得读取或更换任何现有 credential 以补证据。
3. 没有本轮真实服务、设备/UI 或费用事实；不能把默认 fail-closed、fake、Room adapter、构建或历史安装称为模型成功。

下一步仅在用户明确授权后，单独立项定义并审计“可见确认 → 受控 credential broker → 一次真实 HTTP → 安全 receipt/ledger → UI/设备 readback”的生产 owner；否则保持本矩阵所述 fail-closed 状态，不扩展实现。

## 定位依据

- 合同：`P2M_REAL_TEXT_EXECUTION_CONTRACT.md`、`P2M_P3_BACKGROUND_BRIDGE_CONTRACT.md`、`P3_PROVIDER_TRANSPORT_BOUNDARY_CONTRACT.md`、`P3_REAL_TEXT_EXECUTION_PREFLIGHT_CONTRACT.md`、`P3_REAL_TEXT_EXECUTION_COORDINATOR_CONTRACT.md`、`P3_OPENAI_COMPATIBLE_PROVIDER_HTTP_TRANSPORT_CONTRACT.md`、`P3I_CONVERSATION_REAL_TEXT_EXECUTION_CONTRACT.md`、`P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md`。
- 当前装配：`app/src/main/java/com/nanzhufeng/ai/app/AppContainer.kt` 的 P2-M bridge/coordinator；`RealServiceAcceptance.kt` 在 legacy DryRun 前调用并忽略 bridge result。
- 当前持久化/测试端口：`NanfengAiDatabase.kt` 的 `MIGRATION_29_30`；`RoomConversationRealTextExecutionRepository.kt`、`RoomConversationRealTextTestExecutionAdapter.kt`、`RoomRealTextExecutionCoordinatorTestPortAdapter.kt`。
