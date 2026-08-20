# P6-H ChatGPT export JSON Adapter 退出证据

> 2026-08-14 closure note: FB-P6-026..032 are CLOSED and FB-P6-033 is PASSED for the authorised
> non-OPPO scope. The historical evidence below plus the closure audit prove the data chain and
> its new Settings/transcript/icon UI exits; this still does not claim OPPO OEM visual acceptance,
> Provider/Key/HTTP work, or later Adapters.

日期：2026-08-14  
范围：唯一 ChatGPT `conversations.json` 本地 Adapter。不是 P6、Provider、账号、同步、OPPO 或发布完成声明。

## FB-P6-033 图标交付（进行中，不是 Adapter 退出声明）

- 用户已明确授权替换软件图标；新的不可变 master、直接派生 Android adaptive/round/legacy 资源与 Desktop ICNS 已通过静态哈希/可见边界审计，详见 `LAUNCHER_ICON_DELIVERY.md`。
- 尚须在新签名 Android 包完成 install/base hash，并在新 strict-signed Desktop `.app` 完成 bundle/Finder/Dock 表面与 matched-size 对照。模拟器不得替代 OPPO/ColorOS 结论，且未操作 OPPO。
- 更新：Android 新 Acceptance 包的本地/设备 `base.apk` SHA-256 均为 `5c28833d0ccfacc910c76011b0038c46ee26afaad5a397339517a49d739a4af2`，v2/v3 正式证书已验；AOSP emulator 抽屉可见新图标。Desktop 新 strict-signed `.app` 内嵌 ICNS 与 master 派生 ICNS 字节一致，Finder 已显示最新包。Dock 的安全读取超时，仍是 FB-P6-033 未关闭子门，且这不影响 OPPO 仍待另行授权的边界。

## FB-P6-026..033 逐项关闭审计（2026-08-14）

- **026 CLOSED：** Desktop/Android 独立 Settings Center、数据子页与唯一 drawer Settings 入口，Desktop splitter pointer/keyboard/double-click/local reopen，双端真实 ChatGPT picker→Alpha/Skip→restart/readback→export hash 均已由下文真实路径覆盖。
- **027 CLOSED：** Desktop `message-bubble` 与 sibling tools、Android message/attachment 分域 long-press、provenance 按需入口、重启读回已验。本轮更正 Desktop share：Tauri 不再走无可见 owner 的 Web Share；实际按钮只复制非敏感正文到本机剪贴板，由用户自行选择外部目标，未外发。
- **028 CLOSED：** Desktop latest app 与 Android actual drawer 均显示单行标题/最右绝对日期；Desktop hover/focus actions 覆盖日期且 DOM/CSS/keyboard 及 Android Compose/时区回归通过。
- **029 CLOSED，030 CLOSED，031 CLOSED，032 CLOSED：** 官方图标映射、message tools 顺序、attachment/message 分域、低对比度 divider、document/media/audio 预览分别有跨端定向回归；Android DocumentsUI/long-press 与 Desktop latest app 真实路径已记录。
- **033 PASSED（非 OPPO）：** master→Android/Desktop 资源链、签名、Android install/base hash、AOSP emulator 近似 surface、Desktop strict-signed app/byte-identical embedded ICNS/Finder surface 均通过。Computer Use 对 `Dock` 与 `com.apple.dock` 两次超时且 `list_apps` 无 Dock AX target；按用户允许，以 Finder+内嵌 ICNS matched-size 作为当前 Desktop 门，严禁外推为 Dock 或 OPPO/ColorOS OEM 视觉通过。

## 实现边界

- Android Room 仅追加 Schema 24→25；Desktop SQLite 仅追加 `user_version` 12→13。两端分别持久化 adapter 私有副本引用、任务/候选状态、receipt 与 imported provenance，不保存外部 URI、绝对路径、Key、token、Cookie、Authorization 或可恢复外部句柄。
- 严格 JSON 解析拒绝重复 key、超深、无效结构与不安全会话；仅以不执行文本映射 user/assistant/tool、树、时间、顺序与 imported model metadata。未恢复本地 JVM 中不可用的 `org.json` 试验方案。
- 确认项通过既有本地 Conversation owner 与 receipt 在同一原子提交中写入 Conversation、Message Tree、provenance 和 receipt；跳过/取消/失败不生成 Conversation。相同 `source conversation ID + package hash` 只回读既有 ConversationId。

## 自动门（本轮复跑）

- Desktop：Node 48 tests、`npm run lint`、静态 build；Rust `cargo fmt --check`、51 lib tests、`cargo clippy -- -D warnings`、Tauri macOS `.app` bundle 全通过。生成的 capability schema 同时含 `allow-p6h-chatgpt-export-import` 与八个最小 P6-H command allowlist。
- Android：`JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` 通过；专用 `:app:assembleAcceptance` 亦通过。P6-H parser、Room migration/task/receipt 合同在 JVM suite 中执行。

## 2026-08-14 UI re-opening revalidation (active, not a completion record)

- Desktop: 48 Node UI tests, lint and static build passed; Rust 51 tests, `cargo fmt --check` and `cargo clippy -- -D warnings` passed. Latest ad-hoc strict-signed bundle executable SHA-256 is `61d6c8d4e1a390e57803804daf22672b0fce1257ba296bba706aa0f4f1a5a7fc`.
- Latest isolated `.app` real UI: the full Settings Center was reached from the drawer’s sole Settings entry and rendered no chat navigation; the data-import child retained the ChatGPT picker/task entry. The main-chat `role=separator` gained real pointer hit/focus, ArrowRight changed `aria-valuenow` 273→285, real drag reached 375, double-click reset to 256, and restart read back 268 from device/local-only storage. Acceptance-only marker was enabled only by `--p6h-acceptance`.
- Android latest acceptance install SHA-256 is `2bf093b52b1ba7bac273b5b36254d2d17149013716f62065cfd8bfe3c40b9599`. A real long press on the imported USER message showed only the app’s Copy / Select text / Share / Edit bottom sheet; Android system Translate/Read-aloud selection controls no longer competed. The drawer→full-screen Settings→data import route was rechecked after restart.
- Android attachment scope was also exercised through DocumentsUI with the non-sensitive `p6e-temporary-attachment.md`: the draft showed a text/document square preview with no filename/MIME below it; long press opened only the attachment sheet containing filename, `text/markdown`, size and Close. The test attachment was then removed from the draft; no message was sent.
- The acceptance app was force-stopped and restarted after that temporary UI exercise; the imported USER and ASSISTANT messages read back and the temporary fixture attachment name did not reappear in the draft.
- The full Android `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` gate and the dedicated `:app:assembleAcceptance` gate passed after the presentation changes. Release SHA-256 is `667b68ad2319c1a98e8d8cf4a1f0c6c64fc683ca1fb7ea19f6ac17cec5bf21e7`; v2/v3 verification passed with the existing CN=Nanzhufeng certificate. This is signing-layer evidence, not a UI completion declaration.
- The latest isolated `.app` used the native Open panel to select the explicitly named `p6h-visible-conversations.json`. A prior exact-name gate was corrected: renamed user copies with a `.json` suffix now still require no symlink, strict JSON preflight and immediate app-private copy. Its targeted Rust regression passed.
- In that same isolated app, `P6H Visible Alpha` was confirmed and `P6H Visible Skip` skipped; after full app restart the task had no pending candidate and Alpha's ordinary Conversation, two role-preserving messages and inert `gpt-local-import` read back. The Settings Center is a separate page reached from the chat drawer's sole Settings entry.
- Android: the actual `acceptance` variant (not the ordinary Debug APK) was rebuilt and installed. Its local and pulled `/data/app/.../base.apk` SHA-256 both equal `a398a5afb83b37788b18c02278eba8e09c7dfe9e699b6f09df6f357527f09b41`; v2/v3 formal signature verification passed. DocumentsUI selected `/sdcard/Download/p6h-visible-conversations.json`; Alpha was confirmed, Skip skipped, then force-stop/restart read back the ordinary conversation and its user/assistant text. The drawer showed `P6H Visible Alpha` with absolute date `2023/11/15` and its sole bottom Settings entry.
- Remaining UI feedback acceptance is intentionally still open in `PRODUCT_FEEDBACK_DECISION_LEDGER.md`; do not use this section to close P6-H.

## Desktop 真实隔离 `.app` 闭环

隔离 app-private root 为 `/tmp/nanfeng-ai-p6h-acceptance-20260814`，只启动当前 bundle 并以 `--p6h-acceptance` 打开。bundle executable SHA-256：

`909bb3e46821f24b5fbbdc1e0c9f7426149ee8d2efd38403a2af2878f0b918b3`

`codesign --verify --deep --strict` 通过；这是 ad-hoc 开发签名，非 Developer ID/notarized/release。

真实路径为 chat-first 左侧 profile → Settings → 数据导入 → ChatGPT：

1. 原生 Open panel 选择非敏感最小 `conversations.json`；应用显示 `P6-H acceptance import: task-read`，候选弹窗显示 Alpha / Skip，并明确“路径、URI、Key 和外部句柄不会显示或保存”。
2. Alpha 确认写入 `conversation-chatgpt-bc54a6844edc282000ba9d00`；Skip 显示 `SKIPPED`，任务为 `COMPLETED`。
3. 完整关闭重开后，普通历史仍有 `P6H Visible Alpha`；会话显示用户与助手消息、原始时间、`gpt-local-import` inert metadata、分支动作及“从 ChatGPT 导入 · 本地静态文本；不关联模型、Provider、费用或调用记录”。
4. 再次由原生 picker 选择同一文件并确认 Alpha，仍返回同一 ConversationId；随后取消该幂等验证任务，未额外生成会话。
5. Settings 原生 Save panel 导出 `tmp/p6h-desktop-acceptance.nfai-exchange`；应用严格回读成功。产物 SHA-256：`ca92730b7b193e6185d4552f2c9071119a7cc38f1b11452933edf859e55c5335`；`unzip -t` 通过，导出 conversations payload 含 `P6H Visible Alpha`、`CHATGPT_EXPORT`、`gpt-local-import`。

Settings pointer dispatch P0 亦已关闭：acceptance-only DOM marker 证明 Settings 路由接收到 `show-connections`，生产默认不启用诊断；CSS stacking/pointer-event 回归由前端测试覆盖。

## Android 模拟器与签名分层

Android 已先前以实际 ADB 触控完成 chat-first drawer 底部 Settings → 数据导入 → OpenDocument 真选 `p6h-visible-conversations.json` → Alpha 确认/Skip 跳过 → force-stop/restart；普通历史、消息、时间、角色、receipt/provenance 与导出/hash 读回均完成。当前最终 APK 又在同一 `emulator-5554` `install -r`，随后 force-stop/restart。

- APK（当前 acceptance 安装变体）：`app/build/outputs/apk/acceptance/南枫AI-开发验收.apk`
- 本地与设备 `base.apk` SHA-256：`2bf093b52b1ba7bac273b5b36254d2d17149013716f62065cfd8bfe3c40b9599`
- `apksigner verify --verbose --print-certs`：v2、v3 通过；签名证书 SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`（CN=Nanzhufeng）。

未读 Key、未构造 Prompt/RunSpec、未发送 HTTP、未调用 Provider/Agent/tool、未操作 OPPO、未改图标。
