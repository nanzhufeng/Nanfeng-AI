# P6-F2-B Image Preview Adapter 证据

状态：完成（2026-08-14）。范围仅为 Desktop + Android 的 owner-local/private-copy 图片缩略图与唯一原图预览；没有进入 P6-F2-C PDF、视频、音频、通用文件或 P6-G。

## 实现与安全边界

- Desktop 仅以 `workspaceId + attachmentId` 调用 `read_desktop_image_preview`。Rust owner 再查询 workspace-owned metadata、重验 SHA-256/大小、用受限解码读取 JPEG/PNG/WebP，返回安全 display metadata 与 data URL；前端无任意文件读取、绝对 path 或 URI。
- Android 的 `ConversationAttachmentPreviewProjection` 只接受 matching private attachment owner metadata；缩略图和显式点击后的原图均在 app-private copy 上读取。缺失、哈希/metadata 不一致、损坏和超过像素限制均为诚实不可用状态。
- 双端默认完整适配；Desktop 可缩放并以滚动平移、Android 可双指缩放/拖动。均只改变当前视口，不改写原件或持久化预览状态。
- Android 真 UI 发现“新增草稿后只刷新 draft、未刷新 preview projection”的缺口，已将图片/文件新增和移除统一改为 owner reload，避免新私有图片卡片不可点。
- 未读 Key、未发 Provider HTTP、未外发图片、未操作 OPPO、未改图标；TEMP、path/URI、隐藏过程未写入搜索/历史/预览 metadata。

## 自动门

- Desktop：`npm run lint`、`npm test`（34/34）、`cargo fmt --check`、`cargo test --lib`（39/39）通过；新增 Rust owner-private image preview/missing-asset test 与 Node image UI/permission contract。
- Android：`:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:assembleRelease` 通过；新增 projection safety test 与 UI reload/viewport contract。
- Desktop bundle：`npm run build && CARGO_NET_OFFLINE=true cargo tauri build --bundles app` 通过；`codesign --force --deep --sign -` 后 `codesign --verify --deep --strict` 通过。

## 真实 UI 与重启读回

- Desktop 最新 `.app`：既有隔离 `p6-d` fixture 的 `nanfeng_ai_icon_foreground_image.png` 实际出现“预览本地图片”；点击后 AX 显示“本地图片预览”、`432 × 432`、`仅本地原图`、缩放/滚动视口控制；Escape 关闭。完整结束该精确新进程并重启后，同一缩略图入口仍可读回。
- Android `emulator-5554`：通过真实“进入本地对话 → 添加到草稿 → 从相册添加图片”选入本机图片，画面明确显示“图片已加入本地会话草稿；不会发送给 AI 或第三方”、缩略图与“仅本地引用，未外发”；点击后实际显示“本地图片预览”、原图、`仅本地原图` 与“双指缩放或拖动只改变当前视口”。关闭后 `am start -S` 重启并回到对话，缩略图和安全 metadata 仍在。
- Android 仅使用 emulator；未操作 OPPO Find N5。

## 签名与哈希冻结

- Desktop：`南枫 AI Desktop.app` strict ad-hoc verify 通过；可执行文件 SHA-256：`3131cab3b4611fa26d41b15b33963cc6f5e2fa67714f32d0d4996e3594d8a5f1`。
- Android Debug：v2/v3 verify 通过，正式证书 SHA-256：`889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`；APK SHA-256：`6b71493fcef4ef27b8932f0cd41dbacd5ec75168132607b92e166e10421231f3`。`emulator-5554 install -r` 后拉回 `base.apk` 的 SHA-256 相同。
- Android Release：v2/v3 verify 通过、同一正式证书；APK SHA-256：`69663b36247be6a6f7b8097f24f32eeaee7ad0f1136b6ae70b24027fec615623`。
