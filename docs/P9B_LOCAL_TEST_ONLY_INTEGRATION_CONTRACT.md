# 南枫 AI P9-B 本地 Integration Contract 与 LOCAL_TEST_ONLY harness 合同

日期：2026-08-13  
状态：本地基础合同；**不是任何真实目标应用接入，也不是 P9 退出。**

## 唯一目标与停止边界

在 P9-A 已确认“没有可核验真实目标应用入口”的事实下，冻结双端同语义的版本化 Integration Contract、秘密无关的本地账本，以及只使用合成非敏感元数据的 `LOCAL_TEST_ONLY` end-to-end harness。它为未来“一个应用、一个只读 Adapter”的可验证闭环准备底座；不拥有目标应用数据、业务语义或跨应用权限。

本合同到本地 parser/preflight/state/ledger/harness 与定向验证完成即停止。真实 P9 仍需目标应用拥有者提供稳定公开入口、最小授权和真实授权链；本地 harness 永远不能标为目标接入完成。

## 版本化 schema 与隐私边界

固定 envelope：`format=nfai.integration-contract`、`version=1`、`mode=LOCAL_TEST_ONLY`。request 只允许：opaque `requestId/idempotencyKey/appHandle/subjectHandle`、`READ_ONLY_PREVIEW` capability、`READ_ONLY` permission、classification、source provenance、bounded page 和可空 expiry。response/preview/result/readback 只允许 revision、SHA-256、item count、bounded cursor、state、safe error、receipt hash；所有对象均拒绝未知字段。

| 字段/概念 | 规则 |
|---|---|
| opaque handles | 仅稳定受限字符串；不是包名、URI、路径、数据库/Repository 句柄、binder 或 token |
| provenance | 只允许 `LOCAL_TEST_ONLY`、正 revision 与 64 位小写 SHA-256；source hash/revision 不符整体拒绝 |
| pagination | limit 必须 1–25；cursor 仅 opaque 或 null；不支持全量枚举 |
| classification | 只有 `NON_SENSITIVE` 可继续；`HIGH_SENSITIVE` 与 `UNKNOWN` 一律拒绝 |
| `null` | 仅表示可空 expiry/cursor/尚未产生的 preview/result；不是 `0`，计数 `0` 是明确零 |
| 禁止字段 | path、URI、token、credential、Authorization、Key、数据库句柄、正文/body/content、文件字节、真实目标 ID 映射 |

未知 schema/version/field/enum、越界分页、过期 permission、跨 app handle、来源 revision/hash 不符、敏感/未知 classification、取消后继续、错误状态重放和目标更新都 fail-closed，返回版本化 safe error code。

## 唯一所有者、状态机与账本

```text
untrusted JSON
→ P9BContractParser / preflight
→ P9BLocalTestOnlyHarness (tests only, injected synthetic target)
→ P9BIntegrationLedger (Android Room 20→21 / Desktop private SQLite v1)
```

状态只能为：`REQUESTED → AUTHORIZED → PREVIEWED → CONFIRMED → RESULT_READY → READBACK_VERIFIED → REVOKED`；`REQUESTED/AUTHORIZED/PREVIEWED/CONFIRMED/RESULT_READY → CANCELLED`；授权过期为 `EXPIRED`；拒绝为 `REJECTED`。终态不恢复、不自动重试、不自动授权。result receipt 以 idempotency key 唯一；同 key 只读回既有 session/receipt，不再次生成动作。

`AUTHORIZE`、`PREVIEW`、`CONFIRM`、`RESULT` 与 `READBACK` 在每一步都重新核对同一 opaque `appHandle` 与 expiry；一旦目标选择已变化或授权已过期，立刻以 `APP_SCOPE_DENIED` 或 `PERMISSION_EXPIRED` 落入终态，绝不再调用 synthetic target、确认、产生新的 local receipt 或 readback。`CANCEL`/`REVOKE` 不受此继续门阻挡，确保用户仍能安全收束已有本地 session。`RESULT_READY` 的 hash/receipt 只归属于 `LOCAL_TEST_ONLY` harness 的合成 metadata，不代表目标应用结果、写入或外部副作用。

账本只保存 opaque handle、capability/permission/classification、source/preview/result hash、revision、bounded pagination、state、safe error 和单调 event fingerprint。Android 新增三张 P9-B 表并通过 `MIGRATION_20_21` 保留 P8 Agent 表；Desktop 是独立 `p9b-local-test-only.sqlite3`，不复用 P6 workspace/P8/P7 ledger。两端都不记录正文、路径、URI、token、Key、数据库句柄或真实目标数据。

## LOCAL_TEST_ONLY harness 与生产禁区

harness target 只能合成非敏感 metadata：固定 app/subject handle、revision、hash、item count 和 cursor。它覆盖请求→许可→只读 preview→用户确认→result→目标 readback→revoke，以及未知字段、超限、HIGH_SENSITIVE、UNKNOWN、任一步骤过期、目标重选、idempotent replay、cancel、target revision update、cross-app 和越权拒绝。readback 只核对同一 synthetic revision/hash，绝不写入 target、复制 Key 或绕过目标确认。

- Android `AppContainer`、Manifest、release UI 和 release DI 不创建 P9-B target registry/harness，不新增 query、ContentResolver、Provider、Service binding、文件扫描、网络或跨应用调用。
- Desktop 没有 P9-B Tauri command、capability、frontend state、target registry 或 workspace binding；Rust target 仅在 `cfg(test)` 构造。
- 如未来新增生产 UI，只能显示 disabled：**“尚未配置目标应用入口”**；本轮没有此 UI，以免暗示已连接成功。

## 入口矩阵与最小验证

| 入口/消费者 | P9-B 事实 | 验证 |
|---|---|---|
| Android release/Manifest/UI/DI | 不受影响；无 Adapter/registry/跨应用表面 | 静态审计 + 构建 |
| Android domain/Room | 同语义 parser/preflight/state/secret-free ledger | domain、Room readback、20→21 migration |
| Desktop release/Tauri/frontend | 不受影响；无 command/capability/UI | 静态审计 + Rust release checks |
| Desktop Rust test | private SQLite 和 synthetic target | parser、lifecycle/replay/readback/revoke、unknown/target update |
| 真正目标应用 | 不存在于本轮 | 明确标记未验证，不读取任何外部数据 |

真实 P9 重新启动时，必须为**一个具体目标应用**另立 Adapter 合同，写明公开 API/IPC identity、schema 兼容、最小权限 grant/expiry/revoke、目标侧确认、source provenance、preview/result/readback、审计、用户取消、错误、真实数据保护和验收。候选写入仍完全不在本合同范围内。
