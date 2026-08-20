# P10-A 双路径连接能力、状态与配置表面合同

日期：2026-08-13  
状态：本地状态/配置面与 fake/loopback 合同；不含真实 Key、Provider HTTP、Google/Supabase/OAuth 或跨网络同步

## 目标与唯一所有者

```text
Android: Keystore credential presence + Model Registry status + P7 configuration presence
      → ReadConnectionCapabilityUseCase → DualPathConnectionViewModel → Settings
Desktop: OS credential-store presence abstraction + static local policy
      → dual_path_contract_v1 → typed read_dual_path_status → Connection path page
```

`ConnectionCapabilitySnapshot` / Desktop `ConnectionCapability` 是配置状态的唯一读模型。它不能读取或解密 Key，不能构造 Authorization、HTTP、Prompt、RunSpec、Provider Attempt、同步队列或 SQLite 凭据列。

## 两条独立路径

| 维度 | 本地路径 | 联网路径 |
| --- | --- | --- |
| 模型执行 | `LOCAL_OFFLINE`，始终可用 | `ONLINE_PROVIDER`，需单独配置、目录、逐次 consent、费用与网络门 |
| 数据路径 | `LOCAL_ONLY`，本地真值 | `ENCRYPTED_SYNC`，与 Provider 独立配置/授权，绝不由模型开关自动外发 |

本地回退表示“本地继续可用”，不是把已选择的 ONLINE 请求伪造成 LOCAL 成功。失败、取消、目录过期、Key 缺失、未知费用、模型不可用和网络未验证均保留 ONLINE 的阻止原因；用户必须显式重新选择本地或修复后重试。

## 统一状态词汇

- `ConnectionCapability`：`LOCAL_OFFLINE_READY`、`ONLINE_CONFIGURATION_REQUIRED`、`ONLINE_NETWORK_UNVERIFIED`。
- `ProviderConfiguration`：`NOT_CONFIGURED`、`DISABLED`、`READY_FOR_GUARD`。
- `CredentialPresence`：`PRESENT` / `MISSING`，只表示安全条目存在性，不显示或导出值。
- `CatalogFreshness`：`CURRENT`、`STALE`、`NOT_AVAILABLE`；过期不是模型可用成功。
- `EgressConsentState`：`REQUIRED_PER_INTENT`、`GRANTED_FOR_CURRENT_INTENT`、`CANCELLED`。
- `SyncCapability`：`LOCAL_ONLY_READY`、`ENCRYPTED_SYNC_NOT_CONFIGURED`、`ENCRYPTED_SYNC_READY`。
- `DegradedReason`：`NO_CREDENTIAL`、`PROVIDER_DISABLED`、`CATALOG_UNAVAILABLE`、`CATALOG_STALE`、`NETWORK_UNVERIFIED`、`EGRESS_CONSENT_REQUIRED`、`UNKNOWN_COST`、`MODEL_UNAVAILABLE`、`SYNC_NOT_CONFIGURED`。

## 安全与 release 边界

- Android 复用 P2-D Keystore 存在性检查和 P2-D～P2-M 的现有 Provider/RunSpec/预算/ledger 所有权；本阶段不改变 `OpenRouterEgressPolicy.Disabled`，不读 Key。
- Desktop 仅定义 `OsCredentialStore` presence 抽象，release 为 `NoCredentialStore`；无 Keychain value 读取、SQLite Key 列、前端 Key 字段、日志或 HTTP 依赖。
- fake/loopback 是 Android/JVM 与 Rust `cfg(test)` 的 guard contract，不注册至 Android release DI 或 Tauri production command。
- 本阶段没有真实 Provider 或同步成功，不能用状态页、fixture、loopback、构建或模拟器替代真实小额文本验收。

## 合同验证

- Android：local 选择、online guard、unknown cost、cancel、replay、rebuild/sync guard；不把 ONLINE blocked 当 LOCAL success。
- Desktop：in-memory credential-store 与 loopback guard 覆盖 local→online、cancel、unknown cost、duplicate intent、rebuild 与未配置 sync。
- 前端：可见“连接路径”、`LOCAL_OFFLINE / LOCAL_ONLY`、`ENCRYPTED_SYNC`、typed status command；不引入 HTTP plugin。

## 当前停止条件

P10-A 到此只交付可见、诚实的双路径配置和状态底座。真实 Key/HTTP、小额文本、真实费用/取消/重试、真实 Google/Supabase/OAuth、跨网络同步、OPPO 和 Windows 都是后续独立验收门。
