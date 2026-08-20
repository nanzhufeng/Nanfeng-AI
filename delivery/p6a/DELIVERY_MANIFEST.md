# 南枫 AI P6-A 本地 Android 交付

- Debug：`南枫AI-开发验收.apk`，正式同证书签名，SHA-256 见 `SHA256SUMS.txt`。
- Release：`南枫AI.apk`，正式同证书签名，SHA-256 见 `SHA256SUMS.txt`。
- 自动门：190 unit tests 通过；`lintDebug` 为 0 errors / 19 warnings（SDK/Gradle/依赖、第三方 BouncyCastle、图标/KTX 建议；没有 warning 指向本轮交换代码）。
- 设备：API 35 `emulator-5554` 同签名 `install -r`，设备 `versionCode=40` / `0.3.0-p6a`，回拉 `base.apk` SHA-256 与 Debug 一致。
- 范围：Android 包验证 P6-A 协议入口，不代表 Windows Desktop installer、Windows signing、P6-B、P6、Provider、账号、同步或发布完成。
