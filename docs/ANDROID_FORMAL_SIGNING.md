# 南枫 AI Android 正式签名

> 建立日期：2026-08-12  
> 当前状态：legacy 签名已冻结；release v2 初始化脚本与配置门禁已就绪，等待本机交互式生成。

## 固定边界

- 包名：`com.nanzhufeng.ai`。
- legacy 首个正式签名版本：`versionCode=2`，`versionName=0.2.0-p2b`。
- legacy 证书 SHA-256：`889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。
- legacy 的开发验收 Debug 主 APK 与 Release APK 使用同一正式证书；此事实只用于保留和历史核验，不能用于 release v2 构建。
- Debug 仍是可调试构建，Release 是非调试构建；“同证书”不等于二者验证等级相同。
- release v2 使用新证书；历史同包名安装不能通过 `install -r` 覆盖更新。不得卸载、清数据或以设备数据换取验收。

## release v2 密钥保管

- 南枫 AI 的历史 `nanfeng-ai-release.jks` 与既有签名 APK 均进入 **legacy**：保留、不删除、不覆盖，也不再参与后续构建。
- 当前 release 构建仅使用新的 `nanfeng-ai-release-v2.jks` 与 alias `nanfeng-ai-release-v2`；它不复用其他南枫 App 私钥或全局 debug 签名。
- release v2 keystore 主副本与备份均位于仓库外。默认初始化路径为 `~/Library/Application Support/NanzhufengSigning/NanfengAI-Android/nanfeng-ai-release-v2.jks`。
- 仓库不保存 keystore、口令、私钥、Keychain 导出或本机敏感配置。
- 密码仅由本机交互式 `keytool` 输入和受控环境变量或用户级 Gradle 配置使用；不得回显、记录或提交。

## release v2 构建时凭据优先级

构建脚本只按以下顺序读取 release v2 的完整四元配置，任何密码值均不写入仓库、日志或构建产物：

1. 环境变量：`NANFENG_AI_RELEASE_V2_KEYSTORE`、`NANFENG_AI_RELEASE_V2_STORE_PASSWORD`、`NANFENG_AI_RELEASE_V2_KEY_ALIAS`、`NANFENG_AI_RELEASE_V2_KEY_PASSWORD`。
2. 用户级 `~/.gradle/gradle.properties`：`nanfengAi.releaseV2.keystore`、`nanfengAi.releaseV2.storePassword`、`nanfengAi.releaseV2.keyAlias`、`nanfengAi.releaseV2.keyPassword`。

每一层必须同时提供 storeFile、storePassword、keyAlias、keyPassword；部分配置立即失败，不会混用来源。项目根目录的 `gradle.properties` 与 `local.properties` 不承载正式签名口令。两层均不可用时，构建门禁立即失败；不会访问 macOS login Keychain、循环重试、创建替代 keystore 或回退到 Android debug 签名。

## 初始化与迁移影响

- 执行 `scripts/initialize_nanfeng_ai_release_v2_keystore.sh` 时，`keytool` 在本机终端交互式输入新密码；脚本不接收、打印或存储密码。
- 新 keystore 创建后，执行 `scripts/configure_nanfeng_ai_release_v2_gradle_properties.sh` 在本机交互式输入同一套密码；该脚本仅原子写入用户级 `~/.gradle/gradle.properties` 的 release v2 四项配置，拒绝覆盖既有 v2 配置，且不回显密码。
- 新证书有效期为 50 年（18,263 天）。构建成功后用 `apksigner verify --print-certs` 记录 package name、SHA-1、SHA-256 与证书有效期，且不输出密码。
- 新证书与 legacy 证书不同。同包名的历史安装不能用新证书覆盖更新；Google 登录、Firebase Auth、App Links、Play Integrity、签名权限及第三方证书指纹白名单须逐项复核后再发布。

## 构建门禁

- `assembleDebug`、`assembleRelease`、`bundle*`、`package*` 和 `install*` 缺少正式签名时直接失败。
- Debug 产物固定命名为 `南枫AI-开发验收.apk`；Release 产物固定命名为 `南枫AI.apk`。
- 产物冻结后使用 `apksigner verify --print-certs` 核对签名，再记录 APK SHA-256。

## legacy 证据

- Debug 与 Release 均通过 APK Signature Scheme v2/v3 校验，证书 SHA-256 相同。
- 模拟器先记录旧 Android Debug 证书 `0f89bc92cb127895e6881cda9d3c3c641e0efc0f39a9728eedf2585f8e12fdf3`，随后仅在模拟器卸载旧开发包，安装正式签名开发验收包。
- 从模拟器拉回的已安装 APK 与本地 `南枫AI-开发验收.apk` SHA-256 一致。
- OPPO/真实设备尚未安装；不得把模拟器证据写成真机覆盖升级通过。

## legacy 保护边界

1. 不删除、覆盖、导出、猜测或恢复 legacy keystore / 口令。
2. 不将 release v2 产物标注为 legacy 同签名升级包。
3. legacy APK 仅保留为历史证据；release v2 的 `apksigner` 指纹必须单独记录。
