# 南枫 AI Android / macOS 图标交付

> 当前 Android 图标来源与规则以“2026-08-24 当前 Android 替换”为准；下方 2026-08-15 条目仅保留历史交付证据，不能再作为资源输入、颜色、缩放或验收结论。

## 2026-08-25 Android 枫叶白色实心填充

- **母版：** 在保留 `nanfeng_ai_launcher_source.jpg` 原件不变的前提下，新增 Android 专用母版 `app/src/main/icon-source/nanfeng_ai_launcher_q_rounded_scale90_leaf_filled_source.png`（1254 × 1254、RGBA、SHA-256 `442cf528f54abf16ef8bfd7922cfd408b104c2f233e487692bbd291a94b95ef2`）。按用户明确指示，枫叶内部改为纯白实心；为使白叶上的 AI 保持可读，AI 字样改为同一橙色。圆角、原有比例、边距、透明角与 Android 适配层不变。
- **Android 包装：** `drawable-nodpi` 前景与 mdpi—xxxhdpi 的 `ic_launcher`／`ic_launcher_round` 全部从这份母版一次导出；Manifest 的 `icon`／`roundIcon` 与两份 adaptive XML 没有改动。Desktop 资源未触及。
- **验证边界：** 静态资源审计与正式构建只能证明资源链，尚未重新在 OPPO Launcher 以保留数据的正式覆盖包验收。

## 2026-08-24 当前 Android 替换

- **母版：** 用户提供的最初原件 `app/src/main/icon-source/nanfeng_ai_launcher_source.jpg`（1254 × 1254、RGB JPEG、SHA-256 `9d81a42b59d18c8517beb88943649cc554c8305d7c3c7b518479b21ca169336e`）完整保留，不被覆盖。当前 Android 母版为 `app/src/main/icon-source/nanfeng_ai_launcher_q_rounded_scale90_tuned_source.png`（1254 × 1254、RGBA PNG、SHA-256 `1e7aff38fce15afdd39df431402b78765330be8afd5a24bb9e81a993b665bf3f`）：在获授权的 Q 版粗圆白色标记基础上，以图标中心缩小主体约 10%；只把橙色背景从上一版的 `#D87652` 轻调为 `#E1764E`，提高亮度和饱和度而不改变圆角、构图、外缘留白或识别结构。
- **Android 包装：** Manifest 的 `android:icon` 与 `android:roundIcon` 仍共同指向 `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round`；API 26+ 两套 adaptive XML 共享同一组背景/前景。背景是与当前母版一致的连续不透明 `#E1764E`；`scripts/prepare_nanfeng_ai_launcher_foreground.swift` 只将与画布外缘连通的白色画布转为透明，绝不触碰独立的白色标记。前景层以对称 `13.5dp` inset 交给 ColorOS 的 adaptive mask，作为可测量的 OEM 归一化补偿，避免裁到图案边缘且不改变其内部比例。
- **兼容资源：** mdpi 至 xxxhdpi 的 `ic_launcher` 与 `ic_launcher_round` 都直接从新版母版一次性等比导出；Desktop 图标资源不随本次 Android 替换改动。
- **真机验收：** 静态资源链、构建和签名只能证明包装正确；已在 OPPO Find N5 以保留数据的同签名覆盖方式回到真实 Launcher。稳定截图 `/tmp/nanfeng-ai-launcher-q-scale90.png` 显示主体缩小后仍清晰、白色标记四边完整，橙黄底轻微提亮且无 ColorOS 裁切或新增托盘；仍需用户按个人审美确认最终视觉大小。

## 历史记录：2026-08-15（不再作为当前输入）

## 不可变原件与保真边界

- 唯一交付 master 为 `artwork/source/nanfeng_ai_launcher_icon_master_20260814.png`，权限为只读；SHA-256 为 `a0335d3c581fbfe155c02a6d7cbcfb6509606604e9136e705c79311441eb27f4`。
- 原件为 874 × 904、RGBA、8-bit PNG。Alpha 可见边界为完整画布 `(0,0)–(873,903)`；以 4% 非白阈值作视觉材质抽样得到 `(6,4)–(869,899)`。这不是裁切指令：边缘的近白材质与原生阴影属于图稿。
- FB-P6-035 唯一允许的例外已实现为 `artwork/source/nanfeng_ai_launcher_icon_master_20260814_optical_085.png`：1024 × 1024 RGBA、SHA-256 `cd753ed202485816f592c6ac0f1f42eb7062b2f70cec03b6b5e7ff67828052ed`。它从不可变原件一次性生成：完整原件（含黑 N、白叶、橙色部分、原生阴影）整体等比 85%，同一光学中心置于连续纯白方形画布；未裁切、拉伸、重绘、改色、加托盘/边框/外阴影。
- Desktop 继续只从该 0.85 derivative 直接生成，不从任一 legacy/ICNS/PNG 二次派生。
- FB-P6-076 只改变 Android 包装：adaptive foreground 与全部 legacy/round 密度直接从不可变原件按 `0.6375 = 0.85 × 0.75` 光学比例导出。Desktop 不随 Android 的 OEM 补偿变化。
- 旧历史原件与旧 scale120/scale150 文件保留为历史证据，绝不覆盖，也绝不再作为本次资源输入。

## 派生资源

- `scripts/generate_scale150_launcher_icon.sh` 是唯一生成入口；`--android-only` 只重建 Android，避免无关改写 Desktop。
- Android Manifest 继续同时使用 `@mipmap/ic_launcher` 与 `@mipmap/ic_launcher_round`。API 26+ 的两份 adaptive XML共用 `#FFFFFFFF` 背景和 432 × 432 RGBA 前景；legacy/round 密度仍覆盖 mdpi 至 xxxhdpi，全部保持同一 `0.6375` 主体比例。
- Desktop 的 1024 RGBA 与 runtime PNG 都等于 derivative，ICNS SHA-256 `627cb86852fc73dcb0a6a8e4753a5fd41a29741ee0a0022b54cc1f685a0c86e6`；Tauri bundle 显式链接该 ICNS。

## 已验证

- FB-P6-076 的静态命令改为 `python3 scripts/audit_launcher_icon.py --source artwork/source/nanfeng_ai_launcher_icon_master_20260814.png --candidate app/src/main/res/drawable-nodpi/nanfeng_ai_icon_foreground_image.png --expected-scale 0.304646 --android-root app --desktop-root desktop/src-tauri`；它验证 Android 约为旧可见线性比例的 75%，同时保持 Desktop 1024 RGBA 与 ICNS/Tauri 链不变。
- FB-P6-076 实际结果：Android 432 前景 SHA-256 `3010ba5bcf34a828fbbd534d877a20c1b4118d80e9edba446d457a15a29fbf1b`，可见比例 `0.3043×/0.3042×`；Desktop RGBA `cd753ed…` 与 ICNS `627cb868…` 未变化。正式签名 Debug 与 OPPO 回拉 `base.apk` SHA-256 均为 `b9d6dca44f97b0012e8126ddff269ccf539d2cbf89e22d8ae762aec48d5da538`，ColorOS Launcher 截图为 `/tmp/nanfeng-ai-fb-p6-076-ime-closed.png`。
- 静态审计不会把 master 自带的非纯白边缘或烘焙阴影“归一化”为纯白，因此不会因检测工具而破坏用户图稿。
- Desktop：Node 50/50、lint、静态 build 与 `CARGO_NET_OFFLINE=true cargo tauri build --bundles app` 通过。最新 `.app` ad-hoc strict-sign 通过；bundle 内受控 ICNS byte-identical。Finder 实际显示今天 13:47 的 20.7 MB `.app` 和新图标。
- Android：Debug/Release/Acceptance assemble 通过，三者 v2/v3、CN=Nanzhufeng 正式证书（SHA-256 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`）。正确的 Acceptance package `com.nanzhufeng.ai.p6eacceptance` 使用 `install -r --no-incremental`，本地 APK 与同 package 回拉 `base.apk` SHA-256 均为 `e8c72a1c986125c710b20ac4da122b2cd10c13c7ac91ea5c4dd3f2cd023ad214`；AOSP Launcher 截图仅为近似表面。

## 尚待分层取证

- FB-P6-076 的 Android Debug、正式证书、`install -r`、设备 `base.apk` 哈希与 OPPO ColorOS Launcher 已完成；Release/Acceptance 未因本轮 Android 光学调整重新构建，不能从 Debug 结果外推。
- 新 ICNS 的 bundle、ad-hoc strict sign、matched-size 与 Finder 表面已完成。Computer Use 对 `Dock` 和 `com.apple.dock` 的读取均超时，且可发现的 app 列表没有 Dock AX target；按当前用户授权以 Finder+byte-identical 内嵌 ICNS 关闭本轮 Desktop 图标门。该工具限制不得被外推为 Dock 或 OPPO/ColorOS 视觉结论。
- OPPO Find N5 外屏已在保留数据覆盖安装后复核本轮 adaptive mask、缓存刷新与可见比例；内屏 Launcher、其他图标形状和其他 OEM 仍未验证。
