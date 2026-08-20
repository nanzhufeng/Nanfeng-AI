# P6-F2-E Audio / Generic File Adapter 证据

状态：完成（2026-08-14）。这是 P6-F2 的第五个、也是最后一个 preview Adapter；**不是 P6、P6-G、Provider、账号同步或总蓝图的完成声明**。

## 范围与实现

- Audio 只接受 MP3/WAV/M4A；Desktop 以 `workspaceId + attachmentId`、Android 以 attachment ID 作为唯一播放 owner。两端都先验证 app-private 副本的长度/哈希，再给播放器字节；无路径、URI、Key、HTTP、自动播放或外发。
- Generic 只新增安全文本（TXT、Markdown、JSON、CSV）；预览严格 UTF-8、去 BOM、最多前 128 KiB，并仅以 inert plain text 呈现。篡改、缺失、坏 UTF-8、跨 workspace/非 owner 均失败关闭。
- Android DocumentsUI 对 WAV 常报告 `audio/x-wav`；picker 改为 `audio/*`，reader 只将带 `.wav`/`.m4a` 的已知别名归一化，随后仍由既有 canonical MIME + magic-byte 白名单拒绝。未将 picker 放宽为可导入任意音频。
- P7-D 的离线零队列 manifest 现移除合并的 Startup Provider；它原本在启动前崩溃且与当前阶段无关。未来受配置和验证的同步运行时仍须显式初始化，未在本阶段启用 WorkManager、Key 或网络。

## 自动门

- Desktop：`npm run lint`、`npm run typecheck`、`npm test` 37/37、`npm run build`、`npm run tauri:check` 通过；Rust `cargo fmt --check`、`cargo test` 42/42、`cargo clippy -- -D warnings`、`cargo check` 通过。新增 Rust owner 测试覆盖 WAV magic、非零位置重开、跨 workspace、缺失、BOM、128 KiB 截断、篡改和坏 UTF-8。
- Android：`:app:testDebugUnitTest` 通过；分片 `lintAnalyzeDebug`、`lintAnalyzeDebugAndroidTest`、`lintAnalyzeDebugUnitTest` 与 `lintReportDebug` 均通过。当前 lint 报告为 33 warnings、3 hints、无 Error。`:app:assembleDebug`、`:app:assembleRelease` 通过。

## 真实 UI / restart 读回

- Desktop：严格签名的新 `.app` 真实 macOS Open picker 已选中 WAV（不再被 OS UTI 过滤灰显）→ Rust private copy → 草稿 → 本地消息。明确点“开始本地播放”后可访问树进入 Pause，显示 2 秒实际时长；关闭并完整退出同一 `.app` 后重开显示“将从 0:01 继续播放”。Markdown 同样经原生 picker 发送，预览将 `<script>alert(1)</script>` 与链接作为文本显示，并明确说明不渲染/不执行。
- Android `emulator-5554`：真实 DocumentsUI 中 `p6f2e-tone.wav` 和 `p6f2e-inert.md` 都为 enabled；Audio 完成私有复制→消息→明确播放→关闭→force-stop/restart，重开显示“将从 0:02 继续播放”。Generic 独立完成 DocumentsUI→私有复制→消息→force-stop/restart，重开仍显示 inert 文本。最终 XML 留存为 `docs/evidence/p6f2e-android-audio-restart.xml`、`docs/evidence/p6f2e-android-text-restart.xml`。
- 模拟器是 Android 近似验证，不构成 OPPO Find N5、折叠或 OEM 验收；本阶段未操作 OPPO。

## 签名与冻结

- Desktop：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app` 已重新 bundle，`codesign --verify --deep --strict` 通过；`Identifier=com.nanzhufeng.ai.desktop`、`Signature=adhoc`、`TeamIdentifier=not set`。可执行文件 SHA-256：`05a3afa58f1abd37f650b14439a95203b65daacda42f096e34f2f20a7a89c19e`。这是开发 ad-hoc 包，不是 Developer ID/notarized/发布包。
- Android Debug：`app/build/outputs/apk/debug/南枫AI-开发验收.apk` SHA-256：`0566f8ab1e8f7c7ad021bad09cfe6b90ae1f31982ac6c668d82f53a29e0b4da2`；v2/v3 verify 通过，`emulator-5554` 以 `install --no-incremental -r --user 0` 安装后拉回 `base.apk` 完全一致。
- Android Release：`app/build/outputs/apk/release/南枫AI.apk` SHA-256：`2d284461828740bd237d480161ed6f07407c656704b9b5012fe2471270f3e591`；v2/v3 verify 通过。两包使用既有正式证书，未在文档记录 keystore 或密码。
