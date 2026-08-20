# 南枫 AI Android 正式签名

> 建立日期：2026-08-12  
> 状态：已配置、已构建、模拟器已安装校验

## 固定边界

- 包名：`com.nanzhufeng.ai`。
- 首个正式签名版本：`versionCode=2`，`versionName=0.2.0-p2b`。
- 证书 SHA-256：`889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- 开发验收 Debug 主 APK 与 Release APK 使用同一正式证书；后续不得回退为 Android 默认 debug 证书。
- Debug 仍是可调试构建，Release 是非调试构建；“同证书”不等于二者验证等级相同。
- OPPO 等正式设备只能使用同包名、同证书主 APK 覆盖更新；签名不一致时停止，不卸载、不清数据绕过。

## 密钥保管

- 每个 App 使用独立 keystore；南枫 AI 不复用其他南枫 App 私钥。
- keystore 主副本和备份均位于仓库外；口令保存在 macOS 钥匙串，Gradle 不写入口令。
- 跨平台构建可通过 `NANFENG_AI_KEYSTORE` 与 `NANFENG_AI_KEYSTORE_PASSWORD` 提供等价安全配置。
- 仓库不保存 keystore、口令、私钥、Keychain 导出或本机敏感配置。
- 恢复时必须先核对证书 SHA-256；证书不符不能构建可安装包。

## 构建时凭据优先级

构建脚本只按以下顺序读取正式签名配置，任何密码值均不写入仓库、日志或构建产物：

1. 环境变量：`NANFENG_AI_KEYSTORE`、`NANFENG_AI_KEYSTORE_PASSWORD`。
2. 用户级 `~/.gradle/gradle.properties`：`nanfengAi.keystore`、`nanfengAi.storePassword`。
3. macOS Keychain：仅作为可选回退，service `com.nanzhufeng.ai.signing`、account `keystore-password`。

项目根目录的 `gradle.properties` 与 `local.properties` 不承载正式签名口令。三层均不可用时，构建门禁立即失败并给出中文配置说明；不得循环重试、创建替代 keystore 或回退到 Android debug 签名。

## 构建门禁

- `assembleDebug`、`assembleRelease`、`bundle*`、`package*` 和 `install*` 缺少正式签名时直接失败。
- Debug 产物固定命名为 `南枫AI-开发验收.apk`；Release 产物固定命名为 `南枫AI.apk`。
- 产物冻结后使用 `apksigner verify --print-certs` 核对签名，再记录 APK SHA-256。

## 当前证据

- Debug 与 Release 均通过 APK Signature Scheme v2/v3 校验，证书 SHA-256 相同。
- 模拟器先记录旧 Android Debug 证书 `0f89bc92cb127895e6881cda9d3c3c641e0efc0f39a9728eedf2585f8e12fdf3`，随后仅在模拟器卸载旧开发包，安装正式签名开发验收包。
- 从模拟器拉回的已安装 APK 与本地 `南枫AI-开发验收.apk` SHA-256 一致。
- OPPO/真实设备尚未安装；不得把模拟器证据写成真机覆盖升级通过。

## 恢复与灾难边界

1. 从仓库外主副本或加密备份恢复 keystore。
2. 从 macOS 钥匙串或受控秘密管理恢复口令。
3. 用 `apksigner` 对测试 APK 核对证书 SHA-256 必须与本页一致。
4. 构建并在隔离模拟器验证同签名覆盖与数据保留。
5. 只有自动门禁和模拟器通过后，才进入 OPPO 同签名覆盖验收。

keystore 遗失且没有可用备份时，现有安装链无法继续同签名升级；不得新建密钥后冒充同一升级链。
