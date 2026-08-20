# 南枫 AI Android / macOS 图标交付

> 日期：2026-08-15（FB-P6-033；FB-P6-035；FB-P6-076 Android 额外缩小 25%）  
> 状态：Desktop 保持已验 0.85 光学资源；Android 已按用户 OPPO 截图改为不可变 master 的 0.6375 直接派生，并通过静态链、正式签名包与 OPPO ColorOS Launcher 实际复核。  
> 系统 Dock AX 通道不可用已如实记录；OPPO/ColorOS OEM Launcher 仍是后续另行授权硬件门，不阻断本次 P6-H 非 OPPO 退出。

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
