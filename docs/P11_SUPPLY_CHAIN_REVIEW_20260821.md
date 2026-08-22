# P11 供应链复核（2026-08-21）

## 2026-08-23 AAPT2 当前回读与一次正式 Release 构建

- 当前 `main` `a4eebd1` 的 `gradle/verification-metadata.xml` 已含 `08cd46c` 写入的 `aapt2-9.3.1-15703166-osx.jar` 与同版本 POM SHA-256。Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`--offline --no-daemon` 下，`:app:processReleaseResources` 的带写入/无写入两次回读均通过；带写入模式没有产生 metadata diff，XML 校验有效。
- 随后只执行一次同条件的 `:app:assembleRelease`，1m22s 成功。APK 结构为 `com.nanzhufeng.ai / versionCode 52 / versionName 0.3.0-p10a`、无 `application-debuggable` 标记；Android Studio JBR `apksigner` 验证 v2/v3 为真，release-v2 证书 SHA-256 摘要为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本次输出 SHA-256 为 `d612c417985f4e21623239b2722c63bcb3d60bf6dbf63f90e98c01e1f7db9af0`。
- 该 SHA-256 与旧交接中的 code-52 产物 `c511…` 不同；旧精确文件不在当前工作区，不能把差异归因为时间戳或其他因素。本轮未进行安装、设备读取、网络、Provider 或凭据访问，且不将此 APK 用于覆盖安装、发布或 P0–P11 完成声明。后续设备/发布前必须先独立收窄可重复性差异并重新走相应门禁。

## P11 KSP verification metadata 补齐与回读（后续增量）

- 上一次正式 `:app:assembleRelease --offline --no-daemon` 的 `:app:kspReleaseKotlin` 精确报告缺少三个 detached-configuration 工件：`kotlinx-coroutines-core-jvm-1.6.4.jar`、`symbol-processing-aa-embeddable-2.2.10-2.0.2.jar` 与同版本 POM。
- 使用 Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`、`--offline --no-daemon`，仅对 `:app:kspReleaseKotlin` 执行 `--write-verification-metadata sha256`。清单 diff 仅为 11 行：上述三个工件各新增一个 Gradle 生成的 SHA-256；没有升级依赖、禁用或放宽验证，也没有访问 Keychain、签名值、设备或网络。
- 随后以相同 JBR/离线/no-daemon 条件、不带写入参数执行 `:app:kspReleaseKotlin`，任务成功并实际应用 verification metadata；XML 解析有效。更新后 `gradle/verification-metadata.xml` SHA-256 为 `beb92e5168e69ed2396e97ac52a6b48b07372885d17299b0cdd1dca7ca82bd21`。
- 预检观察到两个其他 Gradle 版本的空闲 daemon；本次仍使用 `--no-daemon` 单次 daemon，结束后停止，未共享该守护进程。此记录不把该局部校验回读称为 P11 或 P0–P11 完成。

## 结论与边界

这是 §17 P11 的一次本机依赖安全复核，不是 P0–P11 总控完成、P6 退出、正式发布或 Windows/OPPO/Provider 验收。

审计时当前基线为 `ae3d8ce`。P2–P9 的真实外部门仍分别保持：Provider/成本与真实流、OPPO/发布、Windows 原生验收、OAuth/云端、真实 Agent 工具及真实生态目标；本轮没有以本地检查代替其中任何一项。

## 已修复的高风险依赖

- Desktop 的 `lopdf` 从 `0.35.0` 升至 `0.42.0`，锁文件同步更新。
- 原因：RustSec `RUSTSEC-2026-0187` 指出 `lopdf <= 0.41.0` 对深层嵌套的非可信 PDF 会发生不可捕获的栈溢出；本项目的 `DesktopWorkspaceStore::pdf_preview` 会对经过本地 owner/hash 检查的用户附件调用 `Document::load_mem`，因此该风险在产品边界内。
- 该更新只更换解析库版本；未放宽附件的 workspace/ID/MIME/SHA-256/字节数校验，也未增加网络、Provider、Keychain、用户入口或持久化路径。

## 本机证据

| 面 | 命令或来源 | 结果 |
| --- | --- | --- |
| Desktop Rust 锁定依赖 | `cargo tree --locked --depth 1` | 直接依赖已盘点；升级后 lock 固定为 `lopdf 0.42.0`。 |
| Rust 官方安全公告 | RustSec `RUSTSEC-2026-0187` | 修复线为 `>=0.42.0`；升级目标精确满足。 |
| Desktop 编译 | `cargo check --manifest-path desktop/src-tauri/Cargo.toml --locked` | 通过。 |
| Desktop 回归 | `cargo test --manifest-path desktop/src-tauri/Cargo.toml --locked` | 97/97 通过。 |
| Desktop 格式 | `cargo fmt --check` | 未通过：`desktop/src-tauri/src/lib.rs` 存在既有大范围格式债务；本轮未运行写入格式化，也未把它误报为依赖升级失败。 |
| Desktop 前端 | `npm audit --omit=dev --package-lock=false --json` | 无运行时依赖，0 漏洞。 |
| Android release 依赖 | `:app:dependencies --configuration releaseRuntimeClasspath --no-daemon` | 成功解析当前 release 运行时树；本轮未更新 Android 依赖，也未执行安装/设备操作。 |
| Android release SHA-256 校验清单 | Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` 下运行 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath --write-verification-metadata sha256 --offline --no-daemon` | 确认无活动 Gradle/GradleDaemon 后生成原生 `gradle/verification-metadata.xml`。当前为 516 个 component、923 个 artifact SHA-256；清单文件 SHA-256 为 `2bc1ea7266a3fbe6ab3adfd5690ea3812d344409793d958207f1bc23ae962add`。不升级依赖、不构建/安装 APK、不触及设备、Keychain 或密钥。 |
| Android release SHA-256 回读 | 同一 JBR/`JAVA_TOOL_OPTIONS` 下运行 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath --offline --no-daemon` | 成功；未带 metadata 写入参数的 release runtime 解析实际应用清单完成校验。 |

## 未覆盖与后续门

- 本机没有预装 `cargo-audit`；临时构建在执行环境时限内未形成可执行结果，因此没有把它写成“全量 RustSec 扫描通过”。本次高风险项由 RustSec 官方公告逐项核验发现并修复。
- 仓库现有 SHA-256 verification metadata，但它不是 dependency locking，也不证明冷缓存、CI、所有 build variant 或未来依赖解析均已复现；这些仍须按版本和发布环境持续复核。
- RustSec、npm audit 或 Gradle 解析均不能代替 Android/Windows/macOS 目标平台的安装、运行、签名、真实文件、Provider 或设备验收。
