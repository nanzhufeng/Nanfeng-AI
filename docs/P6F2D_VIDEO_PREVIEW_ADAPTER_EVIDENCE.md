# P6-F2-D Video Preview Adapter 证据

状态：完成（2026-08-14）。范围仅为 Desktop + Android 的受控 MP4 picker/private-copy、代表帧/时长、显式本地播放与播放位置重启读回；没有进入 P6-F2-E Audio/Generic File、P6-G、Provider、Key、HTTP、OPPO 或图标。

## 实现与安全边界

- Desktop picker 只把 `selectedPath/fileName/claimedMime` 交给 Rust import owner；Rust 以 `ftyp` ISO-BMFF 品牌而非扩展名单独承认 `video/mp4`，私有副本按大小与 SHA-256 重验。前端播放命令只接受 `workspaceId + attachmentId + positionMillis`，能力 ACL 只授予 `read_desktop_video_preview`，不接收路径。
- Rust 返回已重验的 app-private 数据字节；WebView 将其转换为 `blob:` URL。CSP 明确为 `media-src 'self' data: blob:`，没有 `file:`、路径或任意 asset 读取能力。此前 WebKit 将 data URL 判为 Invalid 的根因是 CSP 未允许媒体 data/blob；转换后在真实 `.app` 播放器可用。
- Desktop 视频卡显示代表帧、时长和“点击播放 · 仅本地”。播放器不会自动播放；只有明确“开始本地播放”才调用 `video.play()`。`timeupdate` 只采集实际 `video.currentTime` 的非零、未结束值；关闭按钮、Escape 和外点统一进入 typed `closeVideoPreview`，在清空 DOM 前经 owner flush 并核对返回的 position，失败时预览保持打开且错误可见。
- Android DocumentsUI 只通过当前消息所属 private-copy attachment owner 读取 MP4；metadata/代表帧使用 `MediaMetadataRetriever`，本轮将 API 29 才可用的 `use` 隐式 AutoCloseable 改为 minSdk 26 兼容的 `try/finally { release() }`。播放、关闭与 force-stop/restart 均只使用 attachment ID 的 owner 位置记录。

## 自动门

- Desktop：`npm test` 36/36、`npm run lint`、`npm run typecheck`、`npm run build`、`cargo fmt --check`、`cargo test --lib` 41/41、`cargo clippy -- -D warnings` 与 `cargo tauri build --bundles app` 全通过。新增 Node 合同覆盖 `timeupdate`、关闭前保存、Escape/外点同一关闭 owner；新增 Rust 合同覆盖 workspace-scoped attachment ID 写入 1237ms、重开读回和跨 workspace 拒绝。
- Android：`:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`、`:app:assembleRelease` 通过。`lintDebug` 无 Error；保留 32 Warnings/3 Hints，其中 `AndroidP7ERestoreReceiptStore.kt:22` 的 `ApplySharedPref` 是 P7-E 所有者警告、与 Video adapter 无直接依赖，未把全量 lint 写成零警告。

## 真实 UI、原生 picker 与重启读回

- Desktop 最新 bundle 以真实 macOS Open picker 选择受控 6 秒 H.264 Baseline 无音轨 fixture `tmp/p6f2d/P6F2D_LOCAL_PREVIEW_6S.mp4`（SHA-256 `2f249ece664b8906a9b652ef1dceffc886c4a2a715a8ac2d8935466da071dec1`，186,288 bytes）。实际完成 native picker → private-copy → 草稿卡 → 发送消息卡 → 显式播放。关闭后 SQLite owner 行为 `attachment-2f249ece664b8906a9b652ef` 写入真实 `1237ms`，小于 6000ms，未通过数据库注入伪造。
- 完整终止同一 `.app` 再启动后，从同一消息卡打开实际显示“将从 0:01 继续播放”；实际播放器 URL 为 `blob:tauri://localhost/...`。再点击“开始本地播放”后可访问树显示 Pause、Elapsed: 2 seconds、Duration: 6 seconds，证明恢复后的真实媒体元素可播放；seek 在 `loadedmetadata` 后执行并 clamp 到时长。
- Android `emulator-5554` 已以真实 DocumentsUI 选择 MP4、私有复制、发送后显示视频草稿/消息卡，打开并播放，再 force-stop/restart 读回卡与播放器；原始 UI 证据为 `docs/evidence/p6f2d-android-imported-final.xml`、`p6f2d-android-sent-final.xml`、`p6f2d-android-restart-card.xml`、`p6f2d-android-restart-player.xml` 与 `p6f2d-android-player.png`。本轮最终 Debug 覆盖安装后再次 force-stop/relaunch，安装的 base.apk 见下节。模拟器不替代 OPPO Find N5，且本阶段未操作 OPPO。

## 签名与哈希冻结

- Desktop：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app` 的 `codesign --verify --deep --strict` 通过；`Identifier=com.nanzhufeng.ai.desktop`、`Signature=adhoc`、`TeamIdentifier=not set`。可执行文件 SHA-256：`66e8463407867f11a61faf65274608058257a1f104a572a609ab258f08b77559`。这是开发 ad-hoc 签名，不是 Developer ID/notarized/发布包。
- Android Debug：`app/build/outputs/apk/debug/南枫AI-开发验收.apk` SHA-256：`48c9caa7885f91937f4b0b128d63786dec476bd6feb570733b8ba6c5cd13878f`；v2/v3 均 verify，正式证书 SHA-256：`889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。`emulator-5554 install -r` 后回拉 `docs/evidence/p6f2d-android-final-installed-base.apk`，SHA-256 完全一致。
- Android Release：`app/build/outputs/apk/release/南枫AI.apk` SHA-256：`8b1a4ca5144f5cf83f1b1cb81eb9acb9347887b1077bfd2ef4be09429861ea7d`；v2/v3 均 verify，使用同一正式证书。
