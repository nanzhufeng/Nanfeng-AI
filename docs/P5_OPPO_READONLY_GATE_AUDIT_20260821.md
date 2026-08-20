# P5 OPPO 只读门审计（2026-08-21）

## 结论

P5 的 OPPO 正式覆盖安装与可见 UI 验收本轮均**未执行**。当前源码正式 APK 与 OPPO 已安装包同为 `com.nanzhufeng.ai`、`versionCode=51`、`versionName=0.3.0-p10a`，但 APK 字节身份不同；不满足“确有更高版本”的一次 `push -> pm install -r --user 0` 前提。停止于只读证据，不重试、不降级为卸载/清数据，也不启动应用。

## 只读范围

- ADB 设备清单确认 OPPO Find N5：`3B157F009E800000`，产品/型号 `PKH120`；同时存在的模拟器未被操作。
- 仅查询 `com.nanzhufeng.ai` 的 `dumpsys package`、`pm path` 与已安装 `base.apk`；没有运行 `connected*AndroidTest`、Debug/Instrumentation、自动部署或清理。
- 没有读取应用私有文件或数据库。对 `/data/user/0/com.nanzhufeng.ai` 的普通 shell `stat`/`du` 被系统拒绝；没有尝试绕过该保护。

## 安装身份与数据保留指纹

| 项目 | OPPO 已安装包 | 当前源码正式 APK |
| --- | --- | --- |
| 包名 / 版本 | `com.nanzhufeng.ai` / `51` / `0.3.0-p10a` | `com.nanzhufeng.ai` / `51` / `0.3.0-p10a` |
| APK SHA-256 | `fc8f9ac604c57492cabb4b8bc74fe4284623d3a5385b50ad782f798b75252546` | `7406d1de5818e013227d7a1ffb4083043e0922f767d013317040bab5f2c41ea2` |
| 证书 SHA-256 | `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8` | `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8` |
| 签名方案 | v2 / v3 已验证 | v2 / v3 已验证 |

- OPPO `firstInstallTime=2026-08-20 15:15:31`，`lastUpdateTime=2026-08-20 15:21:45`。
- User 0 只读数据身份：`ceDataInode=1459104`、`deDataInode=1433378`，`installed=true`、`hidden=false`、`suspended=false`、`stopped=true`。本轮无写入，故这些值没有前后变化可比较。
- 两份 APK 是同一 release-v2 signer，但同版本不同字节不构成可安全覆盖的“更高版本”。

## P5 与总控边界

P5 §17 仍缺当前源码的正式升级迁移、目标 OPPO 真机可见 UI、数据不丢失与发布/回下载校验。P2/P3 的真实 Provider/成本质量、P6 Windows 原生验收、P7 OAuth/Supabase、P8 真实工具、P9 真实生态目标和 P10 双消费者触发也各自是独立门；本次只读审计不关闭 P5 或 P0–P11。

下一次 OPPO 尝试前，必须先取得一个**更高 versionCode**、同包名、正式 v2/v3 验签通过的 APK，并在安装前再次只读核对设备包/证书/数据 inode。条件满足时最多允许一次 `push -> pm install -r --user 0`；否则继续保持只读。
