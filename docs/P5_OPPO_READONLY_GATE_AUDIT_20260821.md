# P5 OPPO 只读门审计（2026-08-21）

## 2026-08-23 code-53 Launcher 恢复：Package Manager 完整解析与一次标准启动成功

- **本增量授权与只读门：** 仅对 OPPO Find N5 `3B157F009E800000` 执行包管理器/焦点读取，未安装、卸载、清数据、push/pull、删除远端临时 APK、读取业务数据或截图，也没有运行 `connected*AndroidTest`。`adb get-state` 为 `device`；`dumpsys package` 的去敏记录显示 `com.nanzhufeng.ai` 为 `versionCode=53`，无 `DEBUGGABLE` flag，User 0 为 `installed=true`、`enabled=0`（默认启用）且 CE/DE inode 仍为 `1459104` / `1433378`。先前已完成的 release-v2 签名和保留数据覆盖证据本轮不重做、不安装。
- **解析门：** `cmd package resolve-activity --brief --components --user 0` 对 `ACTION_MAIN` + `CATEGORY_LAUNCHER` + package `com.nanzhufeng.ai` 精确返回 `com.nanzhufeng.ai/.NanfengAiActivity`，与冻结 APK 的唯一 exported Launcher component 完全一致；因此满足唯一启动前提。
- **唯一启动与焦点：** 只执行一次 `am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.nanzhufeng.ai/.NanfengAiActivity`。framework 返回 `Status: ok`、`Activity: com.nanzhufeng.ai/.NanfengAiActivity`；“delivered to currently running top-most instance”表示目标已在前台，非启动失败。随后仅一次 `dumpsys activity activities` 读取到 `mCurrentFocus` 和 `mFocusedApp` 均为该 Activity。未读取 UI、会话或应用私有内容。
- **结论与边界：** 此前的“unable to resolve Intent”已由当前 Package Manager 完整解析和一次标准 Launcher 成功恢复；code-53 的 Launcher/前台活动门现有真实 OPPO 证据。它不证明业务功能、Provider/账号、性能、可访问性、旧版本升级迁移、发布回下载、P5 完成或 P0–P11 完成。停止全部 OPPO 操作；遗留远端临时 APK 保持不删。

## 2026-08-23 code-53 Launcher 异常离线诊断：旧 component 正确，无源码修复；设备保持零接触

- **审计范围：** 只读分析已冻结的 `app/build/outputs/apk/release/南枫AI.apk`、源码 manifest/Kotlin、release merged/packaged manifest 和 release Gradle 配置；未运行构建、测试、ADB、AVD、OPPO 命令或任何清理，未修改 APK、源码或版本。
- **最终 APK 事实：** SHA-256 `230cac90c0e54231a73650c1fc1e0a9f3890a03c0e8c1c5150d39a77f183954c` 的 APK 经 Build Tools 36.0.0 `aapt dump badging` 明确报出唯一 `launchable-activity: com.nanzhufeng.ai.NanfengAiActivity`。`aapt dump xmltree` 和在 Android Studio JBR 下运行的 `apkanalyzer manifest print` 均确认 APK package/applicationId 是 `com.nanzhufeng.ai`，该 activity 为 `exported=true`，并在同一 filter 中同时声明 `MAIN` 和 `LAUNCHER`；没有 `activity-alias`。
- **源码与合并对应：** 源码 `AndroidManifest.xml` 的 `.NanfengAiActivity` 结合 `package com.nanzhufeng.ai` 的 `NanfengAiActivity` 类，解析为该完整类名。`processReleaseMainManifest`、`processReleaseManifest` 和 `processReleaseManifestForPackage` 三份 generated manifest 都为 `com.nanzhufeng.ai.NanfengAiActivity`、`exported=true`、相同 MAIN/LAUNCHER filter。`app/build.gradle.kts` 中 namespace/applicationId 均为 `com.nanzhufeng.ai`；release `isMinifyEnabled=false`，无 mapping 产物，故不存在 shrink/obfuscation 导致 launcher component 改写的路径。
- **结论与严格恢复边界：** 此前的 `com.nanzhufeng.ai/.NanfengAiActivity` 与完整类名完全等价，未变更且不是错误 component；最终 APK 自身也并非缺少可解析的 MAIN/LAUNCHER。因此不实施任何 manifest/代码修复。离线证据无法判定 OPPO 当时“unable to resolve Intent”的远端原因；如需恢复，必须由南烛枫另开独立、只读主设备任务。该任务中可采用的最小显式启动命令建议为 `adb -s <OPPO_SERIAL> shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.nanzhufeng.ai/.NanfengAiActivity`，但本任务没有执行，也不得据此返回 OPPO、重试启动、安装或删除遗留临时 APK。

## 2026-08-23 code-53 正式保留数据覆盖：安装与字节回读通过；Launcher 解析异常，严格停止

- **安装前只读门：** OPPO Find N5 `3B157F009E800000` 为 `device`，设备与主机 epoch 相同。目标 `com.nanzhufeng.ai` 为 code `52`、没有 `DEBUGGABLE` 标记、`firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378`；已安装 `base.apk` 为 `c5112374…fd23f91`，v2/v3 均通过且证书 SHA-256 为 `6d1d56ec…8661f8`。本地冻结正式 APK `南枫AI.apk` 为 `com.nanzhufeng.ai / 53 / 0.3.0-p10a`、`230cac90…f183954c`，非 Debug、v2/v3 通过且证书相同；精确远端临时路径在写入前不存在。
- **唯一写入：** 仅一次 `adb push` 到 `/data/local/tmp/nanfeng-ai-0.3.0-p10a-code53.apk`，随后仅一次 `pm install -r --user 0`，系统返回 `Success`。没有 `adb install`、Debug/Instrumentation、`connected*AndroidTest`、卸载、清数据、私有数据/数据库读取或业务 UI 读取。
- **安装后只读回读：** 目标包为 code `53`、仍无 `DEBUGGABLE`，`firstInstallTime` 与两项 data inode 均未变；设备时间仍与主机一致。从新的 installed `base.apk` 只读 pull 的 SHA-256 为 `230cac90…f183954c`，与本地安装文件精确一致，v2/v3 与 release-v2 证书摘要也保持一致。
- **异常停止：** 标准 `ACTION_MAIN` + `CATEGORY_LAUNCHER` 的 package launcher intent 返回“unable to resolve Intent”，没有得到 `Status: ok`。此前最近一次只读窗口焦点仍是系统 Launcher；失败命令因异常退出，未取得安装后焦点。依门禁，未重试安装、未改用其他启动方式、未再读取设备，也未执行远端临时 APK 清理；该路径的剩余状态不作推断。
- **边界：** 本条只证明一次 code-52→53 同证书覆盖、安装后包字节身份与 data inode 保留；不证明 Launcher 正常启动、业务可用、Provider/账号/外部服务、发布或 P5/P0–P11 完成。

## 2026-08-23 code-52 正式覆盖与数据保留验收

- **前置：** 本地正式 APK 为 `com.nanzhufeng.ai / 52 / 0.3.0-p10a`，SHA-256 `c5112374643319617e8cffa1e32fab94605bd4cccadda6fbb6d390f99fd23f91`；Android Studio JBR `apksigner` 证实 v2/v3 为真、证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。OPPO `3B157F009E800000` 只读复核仍为 code 51，`firstInstallTime=2026-08-20 15:15:31`、`ceDataInode=1459104`、`deDataInode=1433378`，且从已安装 `base.apk` 只读计算的证书摘要相同。
- **唯一写入：** 在更高 versionCode、同包名和同证书门全部通过后，仅一次将该正式 APK 推送到 `/data/local/tmp/nanfeng-ai-0.3.0-p10a-code52.apk` 并执行 `pm install -r --user 0`，系统返回 `Success`；没有 `adb install`、Debug/Instrumentation、`connected*AndroidTest`、卸载、清数据、数据库访问或自动化测试。
- **安装后回读：** package 为 code 52，首次安装时间与两个 inode 不变。设备 installed `base.apk` 只读拉回后 SHA-256 与本地产物精确相同，证书摘要保持 release-v2 值。Launcher `com.nanzhufeng.ai/.NanfengAiActivity` 启动返回 `Status: ok` 并成为窗口焦点；未抓取/读取 UI 或会话内容。推送临时 APK 已从 `/data/local/tmp` 移除。
- **边界：** 此证据关闭正式升级、数据 inode 保留和前台启动，不证明业务会话逐项可用、Provider/账号/外部服务或发布回下载；P0–P11 仍未整体完成。

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

## 2026-08-21 P5 正式签名来源校正与构建前状态

- **签名来源：** 项目专属环境变量四项均不存在，但这不构成停止条件：正式优先级是“完整环境变量优先；完整用户级 `~/.gradle/gradle.properties` 的 `nanfengAi.releaseV2.*` 属性备用”。随后只检查该四项用户级属性的非空布尔状态，四项完整；未读取或输出任何属性值、未访问 Keychain。
- **最窄版本变动：** 因正式 fallback 可用，`app/build.gradle.kts` 已将 `versionCode` 从 `51` 递增为 `52`；`versionName` 保持 `0.3.0-p10a`，没有用户功能变动。
- **单次构建结果与停止：** 已以 Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`--offline --no-daemon` 单次执行 `:app:assembleRelease`。任务在 `:app:kspReleaseKotlin` 失败，原因是 `gradle/verification-metadata.xml` 缺少 3 个 detached configuration 工件的校验条目：`kotlinx-coroutines-core-jvm-1.6.4.jar`、`symbol-processing-aa-embeddable-2.2.10-2.0.2.jar` 及其 POM。按本轮“失败即停止、不重试”，未更新校验清单、未再次构建、未生成可用于本轮的 APK、未执行 `apksigner`、未运行测试或操作 OPPO。
- **后续门：** 此构建依赖校验缺口须由独立、授权的 P11 任务先处理并回读；之后才可用当前 code `52` 重新申请一次 P5 正式构建。成功后仍须重新核对 APK package、versionCode 大于 `51`、v2/v3、release-v2 证书以及 OPPO package/data fingerprint，所有条件都成立才可执行一次数据保留覆盖安装。

## 2026-08-21 P5 后续正式构建：KSP 门通过后在 AAPT2 verification 停止

- **先决 P11 已满足：** 提交 `a00c708` 仅补齐原 KSP 输出指定的三个 SHA-256 verification 条目，并以相同 JBR/离线/no-daemon 的 `:app:kspReleaseKotlin` 无写入回读成功。没有升级依赖、放宽验证、访问 Keychain 或操作设备。
- **一次构建与新停止点：** 正式 signing fallback 的四项属性仅以非空布尔状态确认完整，随后用 Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`--offline --no-daemon` 执行一次新的 `:app:assembleRelease`。KSP 已通过，但 `:app:processReleaseResources` 的 `:app:detachedConfiguration1` 报告另一个未登记 source：Google `com.android.tools.build:aapt2:9.3.1-15703166` 的 `aapt2-9.3.1-15703166-osx.jar` 与 POM。构建失败，未生成可用于本轮的 APK。
- **严格停止：** 这两个 AAPT2 工件不是本授权任务的“唯一 P11 KSP 缺口”，故不更新 metadata、不重复 assemble，不运行 `apksigner`，不读取/写入 OPPO、安装、启动、卸载、清数据或做任何 UI 验收。P5 与 P0–P11 仍未完成；若继续，须以新的、单独范围的 P11 供应链授权处理并回读该 AAPT2 缺口。
