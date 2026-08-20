# P10-B 双路径选择反馈证据

日期：2026-08-13  
范围：本地选择反馈；不证明 Key、Provider、费用、账号或同步成功。

## 已实现

- Android `DualPathConnectionViewModel` 将本地选择和联网前提说明投影为内存 `PathSelectionResult`；Dialog 关闭即清除。
- Desktop 连接页只在前端展示本地继续或联网前提反馈；未改变 Tauri command/capability。
- 两端均明确：`LOCAL_OFFLINE / LOCAL_ONLY` 可继续，本地并不等于 ONLINE 成功；ONLINE 未启动且仍需配置、模型、费用、逐次 consent。

## 自动证据

| 层 | 命令/检查 | 结果 |
| --- | --- | --- |
| Android 定向 + lint | `:app:testDebugUnitTest --tests DualPathConnectionContractsTest :app:lintDebug` | 通过；Lint 0 errors（既有 warnings） |
| Android 全量 | `:app:testDebugUnitTest --rerun-tasks` | 238 tests，0 failures，0 errors |
| Android 构建 | `:app:assembleDebug` | BUILD SUCCESSFUL；未安装 |
| Desktop 前端 | `npm run typecheck && npm run lint && npm test && npm run build` | 11 tests 通过 |
| Desktop Rust | `cargo fmt --check && cargo test && cargo clippy -- -D warnings` | fmt、32 tests、clippy 通过 |

## 明确未验证/未执行

- 未做 Android 模拟器 UI 操作：用户要求不安装 test APK、不清模拟器，本轮也未安装任何 APK。
- 未读取已有 Key，未写入任何 Key 或 Provider 配置；未构造 Authorization、Prompt、RunSpec 或 Provider Attempt。
- 未发 Provider/OpenRouter HTTP，未产生费用、图片调用、真实网络、Google/Supabase/OAuth 或跨网络同步。
- 未操作 OPPO、Windows、Git、发布或签名产物冻结。
