# 南枫 AI P5-D 本地交付清单

- `南枫AI-开发验收.apk`：正式证书签名的 Debug 验收包；code 39。
- `南枫AI.apk`：正式证书签名的 Release 包；code 39。
- `SHA256SUMS.txt`：上述精确 APK 的 SHA-256。
- `SIGNING_CERTIFICATE.txt`：仅公开证书指纹与 v2/v3 校验结论。

本地验证已完成：P5-D 合同测试、全量单测、Lint、双包构建与签名检查。此目录不代表 GitHub Release、商店发布、OPPO 验收、真实 Provider 验收或云同步。

安装边界：仅允许同包名、同证书的覆盖安装；不以卸载、清数据或重签名绕过升级。备份恢复是手工 SAF 工作流，替换恢复完成后必须手动完全重启 App，避免旧 Room/container 引用继续运行。
