# 南枫 AI P8-C 本地可见受控运行证据（完成；P9 未开始）

日期：2026-08-13  
范围：Android 的内建本地账本自检与 Desktop 只读 ledger inspect seam；不是 Provider、真实外部 Agent、P9、P10、OPPO、Windows 或发布证据。

## 已确认

- Android 唯一 production owner 为 `P8CProductionLocalAgentController`，只执行 `p8c_local_ledger_inspect`。它无 UI/模型输入，approval token 绑定 plan/tool/input hash、90 秒 expiry、one-shot；token 不落盘，重启不会自动执行。内建工具只读取 secret-free P8 ledger aggregate；action 的安全摘要明确“未连接模型与外部工具”。
- API 35 `emulator-5554` 同签名 `install -r --user 0`（未清数据，`ceDataInode=574936`）可见路径已确认：Settings → 受控本地运行 → 空账本 `0/0/0/0` → 创建计划；显示 `PENDING`、`PLAN_ACCEPTED`、风险/权限和 `0/1,0/1,0/0`。pause→force-stop→冷启动后仍为 `PAUSED` / `#0 PLAN_ACCEPTED,#1 PAUSED`；resume 后 `#2 RESUMED`；cancel→restart 后为 `CANCELLED/#3 CANCELLED`。另一新 Run 经显式确认后为 `RUNNING`、`PLAN_ACCEPTED/PLAN_APPROVED/STEP_SUCCEEDED`、`Step 1/Receipt 1`，没有模型或外部工具成功声明。
- Desktop 已增加“本地受控记录”导航页，唯一读取 `inspect_p8_agent_runs`。页面仅投影 secret-free Run/Step/Event/Checkpoint metadata，并固定显示 `READ_ONLY · LOCAL_READ · NONE`、“未连接模型与外部工具”及“无 production executor”；没有计划、批准、执行、pause/resume/cancel 或工具控件。Tauri capability 只新增 `allow-inspect-p8-agent-runs → inspect_p8_agent_runs`，未授予 executor command。
- Desktop frontend `typecheck`、lint、5 个 Node tests、build 均通过；新增 P8-C test 覆盖入口、唯一 command、固定边界文案和 executor 缺失。Rust `fmt --check`、29 tests、clippy `-D warnings`、check 通过，原有 P8-C test 继续证明重复 inspect 不写入且 event sequence 稳定。Tauri app 已用 `CARGO_NET_OFFLINE=true cargo tauri build --bundles app` 重建。
- 新 `.app` 位于 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`，已 ad-hoc 重签并 `codesign --verify --deep --strict` 通过；13 MiB、TeamIdentifier `not set`、可执行文件 SHA-256 `ee0e0ae137b04dd4651a24f0d9f2ca2a9ab65d0dd4a91f56dc78ac36abca5512`。它不是 Developer ID、notarized 或发布产物。
- 从该实际 `.app` 两次冷启动后点击“本地受控记录”回读：页面显示“账本当前为空 / 已知空账本”，并显示“已读取本机 P8 安全账本 metadata；无 production executor”。没有产生 Run、计划或工具执行；这不是 `LOCAL_TEST_ONLY` fixture 成功或外部副作用证据。
- Android 重新执行 `:app:testDebugUnitTest --rerun-tasks`（226 tests、无失败 XML）、`:app:lintDebug --rerun-tasks`（0 errors / 24 warnings）与正式 `assembleDebug :app:assembleRelease`。`0.3.0-p8c` / code 48 Debug SHA-256 仍为 `1a623ff538697c9f370bf6b79ed29f4bf0d6317704d3ec1ad54f9f59e6caf5bc`，Release 为 `30ced687cf00bb004d0447b67e8a8de3ae34748c0644dfd9a50bd3d397c1ebb9`；两包 v2/v3 true，证书 SHA-256 均为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- 仅 API 35 `emulator-5554` 以正式 Debug `install -r --user 0` 覆盖；未清数据、未安装 test APK。`ceDataInode` 安装前后均为 `574936`，设备回读 code 48 / `0.3.0-p8c`；回拉 `base.apk` SHA-256 与 Debug 完全一致。

## 仍不属于 P8-C

- failure/cancel/timeout/crash、UNKNOWN、budget、prompt injection、duplicate event/receipt、rollback 的完整语义继续由 P8-A/B `LOCAL_TEST_ONLY` harness 覆盖；它们不是 production tool 成功或外部副作用证据。
- Provider/HTTP/Key、真实外部工具、文件/跨应用路径、高风险动作、OPPO、Windows、Developer ID/notarization/发布和 P9 均未开始或验证，必须另立合同与授权。
