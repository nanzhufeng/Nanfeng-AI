# ADR-002：P6-A Desktop 技术决策门与交换协议 v1

- 状态：接受；P6-A 完成，不代表 P6 或项目完成。
- 日期：2026-08-13（技术、依赖与版本资料当日核验）
- 决策：P6-B 以 **Tauri 2 + 原生 HTML/CSS/ESM 工作台 + Rust 受控本地边界** 继续；PWA 只保留为协议查看/手工导入导出 fallback，不作为 Desktop 本地真值所有者。没有其他方案出现足以推翻两者的证据。

## 问题与不可变边界

Windows 是深度生产中心，必须无需账号启动、离线可用、拥有自己的未来本地库与私有附件空间；它不能读 Android Room、Android 路径或数据库表。P5-D `.nfai-backup` 是 Android 一致 SQLite 快照恢复合同；本 ADR 的 `nfai.exchange.v1` 是跨端语义迁移合同，二者不得互用。

## 受控 spike 结果

同一非敏感 fixture 已被确定性封包、Desktop 预检、再封包；Desktop 输出与输入 byte-for-byte 相同，语义 hash 为 `ad41c1ee6aa64b9e2f218034dbefccc333c43fb923b874b12ff51ce5972d4031`，包 hash 为 `74078bf5bdc1cbe53fbc4ac88029f8039b7aed9f8d0e79ea1f6c59ecf6aeb4ec`。Android 显式选择快照后导出，并对其自身包完成严格 preflight，得到相同 semantic hash；没有生产 Room 写入。

Desktop 宽屏 spike 有项目导航、主会话摘要与可折叠安全详情。Tauri 路径完成 Rust `cargo check`：前端仅以 dialog 选取 `.nfai-exchange`，唯一 Rust command `stage_preflight_selected_exchange` 将受大小/ZIP 魔数限制的选择文件写入私有 app-data staging；capability/permission 只允许该 command，Cargo 没有 shell、文件系统广域、SQL、网络或 updater plugin。PWA 路径仅验证 File System Access feature gate、OPFS capability gate 与 download fallback；尚未把任意一种浏览器存储当作正式 Desktop DB。

## 比较与结论

| 维度 | PWA | Tauri 2 | 结论 |
|---|---|---|---|
| Windows 文件/附件 | 用户授权 picker 可用，能力与浏览器相关，必须 fallback | native dialog + Rust 可将唯一选择复制至私有数据目录 | Tauri 更可控 |
| 离线与 DB 所有权 | OPFS 可作 origin 私有空间，但浏览器存储/运行环境是额外变量 | Desktop 自己拥有 app-data；P6-B 再实作本地 SQLite，不接 Android DB | Tauri |
| 安全/CSP | Web origin/CSP；不可把 capability 当系统权限 | capability、command scope 与 CSP 可最小授权；本 spike 无 shell | Tauri |
| 包体/性能 | 无安装包但受浏览器/安装体验限制 | 使用系统 WebView，保留前端生态与原生边界 | Tauri |
| 更新/签名交付 | Web 部署与浏览器控制 | 可做 Windows installer/签名；当前未验证 | Tauri，交付债务保留 |
| 无账号启动、协议未来性 | 可行 | 可行，且同一 ESM/schema fixture 已复用 | 两者可行 |
| 维护成本 | 单栈最低 | 增加 Rust，但换取明确文件/数据所有权 | Tauri 代价可接受 |

Electron、Flutter、.NET 等没有针对本项目的证据显示能同时改善 Tauri 的 Windows 本地边界、包体/系统 WebView、前端复用与受控 Rust IPC；P6-A 不引入第三条实现路线。

## 官方资料与锁定面

本次只以官方资料核验：Tauri 2 的 [概览与系统 WebView/前端兼容性](https://tauri.app/start/)、[permissions/capabilities](https://v2.tauri.app/security/permissions/)、[CSP](https://v2.tauri.app/security/csp/)、[dialog](https://v2.tauri.app/plugin/dialog/) 与 [Windows installer / WebView2 / 跨编译限制](https://v2.tauri.app/distribute/windows-installer/)；PWA 的 [Chrome File System Access 文档](https://developer.chrome.com/docs/capabilities/web-apis/file-system-access) 与 [OPFS 文档](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system)；Rust SHA-256 依赖来自 [RustCrypto sha2 官方仓库](https://github.com/RustCrypto/hashes/tree/master/sha2)。核验日为 2026-08-13。

`desktop/src-tauri/Cargo.toml` 仅声明 Tauri major 2、dialog major 2、serde 1、sha2 0.10；本机 `Cargo.lock` 锁定实际解析版本。Rust 本机为 1.97.1；Node 为 26.4.0。不要把这些本机版本推断成 Windows 正式发布支持范围。

## 复审条件与债务

重新评估选型若：(1) Windows 原生构建/签名/installer 或 WebView2 离线安装无法达到可用性；(2) P6-B 的 SQLite/附件性能或访问控制无法满足；(3) PWA 在所有目标浏览器上实测满足等价的文件、离线、更新和支持成本；或 (4) 多端团队维护 Rust 已有持续不可接受成本。

本 macOS 会话只通过静态 Desktop build 与 `cargo check`，没有 Windows installer、Windows 签名、Windows WebView2、真实文件 picker 交互、自动更新或 Windows SQLite 性能证据。官方文档指出 MSI 只能在 Windows 创建，跨编译仅为 caveated NSIS last resort；这些都保留为 Windows 本机债务。P6-B 的下一步仅是选定栈的本地工作台基础与其独立真实验证，不启动账号、同步、Hub、Agent、Provider 或 P7+。
