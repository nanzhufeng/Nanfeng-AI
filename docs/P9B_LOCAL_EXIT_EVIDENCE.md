# P9-B LOCAL_TEST_ONLY Integration Contract 本地证据

日期：2026-08-13  
结论：P9-B 的双端本地合同基础完成；**没有真实目标应用接入，P9 不退出。**

## 要求到证据矩阵

| 要求 | Android 证据 | Desktop 证据 | 未覆盖边界 |
|---|---|---|---|
| 严格 schema/parser/preflight | `P9BIntegrationContractTest`：未知字段、HIGH_SENSITIVE、UNKNOWN、分页、cross app、expiry 失败关闭 | `p9b_integration_contract_v1` 2 项 Rust tests | 没有真实目标 schema/API |
| 许可→preview→确认→result→readback→revoke | 同一 domain test：状态单调、idempotency request/receipt、cancel 终态 | 同一 Rust test：private SQLite lifecycle/replay/readback/revoke | target 为合成 metadata，不代表真实授权 |
| provenance/revision/hash/target 更新 | Room/domain 覆盖 revision/hash 重验和 `TARGET_UPDATED` | Rust test 覆盖 revision/hash 改变后 `REJECTED/TARGET_UPDATED` | 无真实 source/用户数据 |
| 秘密无关 durable ledger | Schema 20→21，`P9BIntegrationRoomContractsTest` 回读 session/event/receipt | 独立 `p9b-local-test-only.sqlite3` v1 | 无真实跨应用账本/数据库访问 |
| release surface 无 harness | `AppContainer`/Manifest 无 P9-B registry/DI/UI；静态审计 | 无 Tauri command/capability/frontend binding，只有 module declaration | 未来 target UI 只能诚实 disabled |
| 续行目标/权限门 | `P9BIntegrationContractTest`：目标重选或任一步骤到期后，不再 preview、确认、产生 synthetic receipt 或 readback | `p9b_integration_contract_v1`：同一 target/expiry gate 在 `AUTHORIZE/PREVIEW/CONFIRM/RESULT/READBACK` 逐步重验 | synthetic handle/clock 不代表真实目标选择、授权或超时 |

## 自动与构建

- Android：历史全量 `:app:testDebugUnitTest --rerun-tasks`，234 tests、0 failures、0 errors；`lintDebug` 报告 0 issue；正式 Debug/Release 均构建。2026-08-20 本次仅定向 `P9BIntegrationContractTest` 5/5 通过（新增续行 gate），未重跑全量/lint/产物。
- Android 签名：`0.3.0-p9b` / code 50；Debug `e16aaf8de53291183cf4b0c800f59c957f0afb22501a39af3f0093789faee6f9`，Release `6167e373ea9bbf28f87d1dbafb30bae817ab27082c052e17be88d0714a209ac3`；两包 APK Signature Scheme v2/v3 为 true，签名证书 SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- Desktop：历史 frontend typecheck/lint/5 tests/build，Rust fmt/31 tests/clippy/check 与 Tauri app bundle 均通过；ad-hoc `codesign --verify --deep --strict` 通过，TeamIdentifier none，非 Developer ID/notarized。可执行文件 SHA-256：`5583b6c4961a67454ab0d0b9c7b2c1f7473987f13f10467838a7e8990220ab58`。2026-08-20 本次 `cargo test p9b_integration_contract_v1` 3/3 通过；全仓 `cargo fmt --check` 只暴露既有 `lib.rs` 格式债务，未写入，P9-B 单文件格式检查另列。

## 模拟器与图标产物

- 仅 `emulator-5554`：同签名 `install -r --user 0` 前 app data inode `574936`；没有清数据、卸载或 test APK。冷启动 `NanfengAiActivity` 为 `Status: ok`、2.345 s；device `base.apk` 与最终 Debug hash 完全一致。
- 当前 Android/Tauri 共享 PNG 是 RGB，SHA-256 `324764484f27d2695f7a028488c84c82bc941dcafa197fecced9db1e42924c9c`；为满足 Tauri 强制 RGBA，确定性派生 `desktop/src-tauri/icons/nanfeng_ai_icon_rgba.png`（432×432 RGBA，SHA-256 `cfcbc287f4f3a2845cd6a10bd24e9c8b8798652f1e409094186e27b0ecc32dbe`）。`.app` 内嵌 `.icns` 可拆出 alpha PNG。实际 bundle 可启动；未将当前屏幕观察写成 Dock/Finder 图标视觉验收，因 Dock/Finder 未显示且旧进程/系统缓存可能影响结论。

## 明确未验证与下一门

本证据不包含真实目标应用、Manifest query、ContentProvider/Service binding、文件扫描、跨应用/网络调用、Provider/Key、候选写入、OPPO、Windows 或真实目标侧确认。下一步必须先取得一个实际目标应用拥有者发布的稳定公开入口和最小授权合同，再单独建立“一个应用、一个只读 Adapter”的真实 P9 合同。
