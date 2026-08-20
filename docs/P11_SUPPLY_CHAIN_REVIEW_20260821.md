# P11 供应链复核（2026-08-21）

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

## 未覆盖与后续门

- 本机没有预装 `cargo-audit`；临时构建在执行环境时限内未形成可执行结果，因此没有把它写成“全量 RustSec 扫描通过”。本次高风险项由 RustSec 官方公告逐项核验发现并修复。
- Gradle 当前可解析 release 依赖，但尚未启用仓库内的 dependency locking/verification metadata；在单独 P11 变更前，不把此次解析视为可复现供应链锁定。
- RustSec、npm audit 或 Gradle 解析均不能代替 Android/Windows/macOS 目标平台的安装、运行、签名、真实文件、Provider 或设备验收。
