# 南枫 AI P7-E 本地退出门证据

日期：2026-08-13  
范围：仅补齐 P7-E 的本地 Desktop 构建/签名门，并复核 Android typed restore 的 durable checkpoint、进程重建读取边界。不是 Google、Supabase、OAuth、HTTP、真机跨网络或发布证据。

## 唯一所有者与入口边界

- Android 的唯一 production 链为 `AppContainer → AndroidP7ESemanticSnapshotSourceAdapter → P7EGuardedRestoreOwner → P7ERestorePlanCoordinator → AndroidP7ESemanticAtomicRestoreWriter`。普通 UI/worker 没有可见恢复入口；未来 verified-auth boundary 才能提供 `VerifiedAccountHandle`。
- Desktop 的唯一 P7-E owner 为 `DesktopWorkspaceStore` 内部 `p7e-isolated-workspace-v1` bridge。它没有 Tauri command、capability、UI、HTTP 或 recovery input，且只写显式命名的隔离 workspace；P6 `workspace.sqlite3` 不是恢复目标。

## 本轮实际通过

- Desktop 前端：`npm run typecheck`、`npm run lint`、`npm run test`（4/4）及 `npm run build` 通过。
- 跨端协议：`node ../protocol/scripts/run-sync-golden.mjs` 通过；payload SHA-256 为 `d10aa4552c67f3bb84f55d25040309f19fb946ef49c5b56139164e7a3a2fc316`，envelope SHA-256 为 `30b545147a467b5d982b551d91e15aadc46879eb67fe09df36fa8839eacb0a0d`。
- Desktop Rust（均以 `CARGO_NET_OFFLINE=true` 执行）：`cargo fmt --check`、`cargo test`（23/23）、`cargo clippy -- -D warnings`、`cargo check` 通过。P7-E 用例覆盖隔离 workspace reopen/readback、原子切换、确认前拒绝替换、staging rollback、P6 shape/secret-like 字段拒绝，以及无 Tauri entrypoint。
- macOS 包：`CARGO_NET_OFFLINE=true cargo tauri build --bundles app` 通过，随后对 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app` 重作 `codesign --force --deep --sign -`，`codesign --verify --deep --strict --verbose=2` 通过。包为 13 MiB；可执行文件 SHA-256 为 `def5299c2250ad7465680c270138d1dc58e7fe96b1407410d5c4af0dfa579101`；`Identifier=com.nanzhufeng.ai.desktop`、`Signature=adhoc`、`TeamIdentifier=not set`。它不是 Developer ID 签名、未 notarized、未发布。
- Android（Android Studio JBR）：`P7EAndroidSemanticAtomicRestoreWriterContractsTest` 与 `P7EGuardedRestoreOwnerContractsTest` 定向重跑 6/6，0 failures/0 errors。前者证明 fresh candidate → typed DAO → switch 后新 Room semantic readback、悬空关系写前拒绝，以及 confirmed replacement 中断后 checkpoint 回滚；后者证明 verified/READY/direction/revision 守卫和 durable receipt replay。

## 审计结论与未覆盖边界

- 切换成功时 writer 关闭旧 Room，之后以全新 Room 打开并按 semantic records 读回；`AndroidP7ERestoreReceiptStore` 使用独立 app-private Preferences，因此 receipt 提交不依赖被关闭的业务 Room。当前代码无需额外 production 补强。
- 不能在当前无配置的 App 中安全地执行“真实 process-kill 后恢复”：没有合法 verified-account/recovery/remote envelope 入口，伪造登录、直接写 Room 或数据库注入均违反 P7-E 合同。上述 cold-open/readback 是当前允许的本地证据；真实强杀恢复仍待未来明确授权的真机/账号/服务门。
- 未发生 Google/Supabase/OAuth/网络、Key/Provider HTTP、OPPO、图标、清数据、test APK 或 Git 操作。

## 真实外部门

明确 Supabase target 与授权 CLI/session、私有配置、本应用 Google OAuth、用户授权的真机测试、部署后 schema/RLS/RPC/anon 拒绝回读，以及 Android/Desktop 跨网络加密 envelope readback 后，才能声明 P7-E 真实退出；这些条件齐备后按蓝图进入 P8。
