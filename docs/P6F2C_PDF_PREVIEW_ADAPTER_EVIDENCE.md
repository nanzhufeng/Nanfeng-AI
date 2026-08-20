# P6-F2-C PDF Preview Adapter 证据

状态：完成（2026-08-14）。范围仅为 Desktop + Android 的受控 PDF 文档卡、软件内阅读、页码与重启读回；没有进入 P6-F2-D Video、P6-F2-E Audio/Generic File、P6-G、Provider、Key、HTTP、OPPO 或图标。

## 实现与安全边界

- Desktop 的 `read_desktop_pdf_preview` 只接受 `workspaceId + attachmentId + pageNumber`，先按 workspace owner 查询附件 metadata、重验私有副本 SHA-256/大小，再由 `lopdf` 读取页数；只返回安全 display metadata、受控页码与本机 data URL。SQLite 只持久化 attachment ID + 页码，前端无 path/URI 或任意文件读取。
- Desktop 阅读层使用 `sandbox=""`、`referrerpolicy="no-referrer"` 的内嵌阅读面，并明确声明脚本、表单动作、外部资源与自动链接不执行。外层 pointerdown 曾误关闭页码按钮，已将 `.dialog` 纳入同一 app-owned transient-layer 白名单，并新增 Node 回归断言。
- Android 仅通过当前 conversation 的 matching private attachment owner 调用 `PdfRenderer`。单页渲染限制为 1440px 边与 4 MP；缺失、校验失败或损坏返回诚实不可用。`SharedPreferences` 仅存 attachment ID + 页码。
- PDF 卡不再显示图片缩略图专属的“本地缩略图不可用”残留。两端没有解析正文、执行 PDF JS/表单、外发附件或读取 Key。

## 自动门

- Desktop：`npm test` 35/35、`npm run lint`、`npm run build`、`cargo fmt --check`、`cargo test --lib` 39/39、`cargo clippy -- -D warnings` 均通过；`cargo tauri build --bundles app` 成功。
- Android：`:app:testDebugUnitTest`、定向 `*P6F2C*`、`:app:lintDebug`、`:app:assembleDebug`、`:app:assembleRelease` 均通过。lint 生成正式 HTML/SARIF 报告；既有 `LocalClipboardManager` deprecation 仅为既有警告，不是 PDF 失败。

## 真实 UI、原生 picker 与重启读回

- Desktop 最新 strict ad-hoc `.app`：在隔离 HOME 的真实 macOS 原生 Open picker 点击“添加文件”，用“前往文件夹”输入 `tmp/pdfs/p4n-visible-text-layer.pdf` 并确认 Open。应用随后显示私有草稿附件，点击发送后消息卡可见“在应用内阅读本地 PDF”。点击后实际显示“本地 PDF 阅读”、第 1/2 页、sandbox 说明；点击“下一页”后为第 2/2 页。完整退出该精确 bundle 进程并用同一隔离 HOME 重启，再从同一消息卡打开，实际仍为第 2/2 页。该链路不使用数据库注入或静态 fixture 替代 picker。
- Android `emulator-5554`：真实 DocumentsUI 选入 `P6F2C_LOCAL_PREVIEW.pdf`，私有复制后卡片显示“PDF · 点击在应用内阅读”；阅读器实际显示第 1/2 页并切到第 2/2 页。force-stop/restart 后，附件卡与第 2 页位置仍可读回。原始 XML/截图位于 `docs/evidence/p6f2c/`（含 `android-pdf-reader-page2.xml`、`android-restart-reader.xml`）。最终 Debug 覆盖安装后重新进入本地对话，`android-final-pdf-card.xml` 仍只显示 PDF 阅读入口且不含“本地缩略图不可用”；未操作 OPPO Find N5。

## 签名与哈希冻结

- Desktop：`南枫 AI Desktop.app` 经 `codesign --verify --deep --strict` 通过；可执行文件 SHA-256 为 `6808c1ab2102adb5e6fd95c07d3c89c3e19115b4d443b292bb8c8ee1c94ec5e6`。这是 ad-hoc 开发签名，不是 Developer ID/notarized 发布包。
- Android Debug：v2/v3 verify 通过，正式证书 SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`；APK SHA-256 为 `d2d0f17607fad42de9d9fd213e5f92766db06d725ed724c0f12d8aeac1fd15a4`。`emulator-5554 install -r` 后拉回 `docs/evidence/p6f2c/android-installed-base-20260814-p6f2c-final.apk`，哈希完全一致。
- Android Release：v2/v3 verify 通过、同一正式证书；APK SHA-256 为 `1dc5062a2d2380c6d43b808f73107ac0763489d2d6976dec2460ace06f5c97c7`。
