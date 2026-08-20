# P6 证据矩阵（2026-08-20，HEAD 673f2d6）

## 判定口径

本矩阵只核对 `MASTER_DEVELOPMENT_BLUEPRINT.md` §17 的 P6 原始范围与退出门。它不是“总控方案”的替代名称：总控方案始终是 §17 的 P0–P11 完整路线。

`完成` 仅指本行所列范围已有相应真实或自动证据；不把本地、模拟器、macOS、Windows、OPPO 或外部服务证据互相替代。`外部/设备` 指必须在指定环境才能关闭的门，不因本地实现而降低。

| 原始 P6 范围 / 出口 | 当前证据 | 状态 | 未关闭或禁止外推 |
| --- | --- | --- | --- |
| P6-A 技术决策与 v1 协议 | ADR-002、共享 schema/canonical hash、Android/ Desktop strict-preflight 合同均已存在。 | 完成（协议基线） | 不证明 Windows、完整字段保真或同步。 |
| Android 文本会话 → Desktop import → 再导出 | `CURRENT_HANDOFF.md` 的 P6-A 非敏感隔离链记录 Android DocumentsUI 输出、Desktop native Open/Save 与相同 `semanticHash`。 | 完成（单个独立文本会话） | 不扩大为完整 Project/Knowledge/Memory/Relation/附件。 |
| v1 全对象 mapper | Android `ExportWorkspaceExchangeUseCase` 与定向合同已实现。 | 仅本地，且有意受限 | v1 无法表示若干 owner 历史/来源/附件字段；禁止将它说成完整对象精确保真或开放完整范围 UI。 |
| v2 字段保真与 Android writer | v2 schema、共享 golden、全 owner mapper/package writer、scope 与 SAF bridge 均已实现；mapper 覆盖 Project、Conversation、Knowledge、Memory、Relation、safe settings 和附件。 | 仅本地（完整 owner 组合） | Android 真正 DocumentsUI 链当前只观察到 Project + settings、0 附件；不等于完整对象真机/用户文件验收。 |
| v2 Android DocumentsUI → Desktop native Open → private import → native Save 回导 | `CURRENT_HANDOFF.md` 顶部记录隔离 AVD 实际 `.nfai-exchange.zip`、Desktop 隔离 bundle 的 native Open/Save、semantic 与两项 field hash 一致，v1 表保持 0。 | 完成（最小非敏感包） | 只含 Project + settings、0 附件；不代表 Android v2 import/恢复、完整对象恢复、备份或同步。 |
| 本轮补强：完整 owner 自动跨端 gate | `scripts/verify-p6-v2-owner-fidelity-cross-platform.sh` 使 Android mapper 写出的非敏感完整 owner package 必须通过 Node preflight 与 Desktop strict reader，断言 5 个 root、附件和每个 owner field hash。 | 完成（自动跨端协议） | 不是 picker、SAF、SQLite commit/re-export UI 或外部设备证据。 |
| Desktop 控制中心、chat-first 与 macOS 本地恢复 | P6-D/E/F/F2/G、H/I/J/K 各自合同和隔离 macOS/Android证据已记录；对话、导入、预览与本地恢复不能代替跨端完整对象出口。 | 分项完成 / 需以各自 evidence 核对 | 当前源码的 macOS Developer ID/notarization、Windows 安装交付不因此完成。 |
| 紧凑/展开与异常恢复 | P6-D 合同要求 Desktop `.app` 与 Android 分别验收；历史隔离 macOS/模拟器证据只覆盖其明确场景。 | 分项完成 | 仍需随将来影响布局/恢复的 P6 改动逐平台回归；不得由静态 CSS 或单端截图替代。 |
| Windows 深度生产与控制中心 | `P6D_WINDOWS_NATIVE_DELIVERY_CONTRACT.md` 已冻结 Windows 本机 WebView2、签名/安装、picker、SQLite 锁/升级、100/150/200% 缩放与 IME 门。 | 需要外部 / Windows 本机 | 本机不得生成、签名或伪造 MSI/NSIS；没有 Windows 实机和签名环境即不能关闭。 |

## 原规格边界复核

- Android v2 **导入/archive/transaction/恢复**不是 §17 P6 的原始退出门。它可在未来另立合同，但不能因为 v2 导出已存在而被擅自加入本轮范围。
- Desktop v2 private import/re-export 是 content-preserving private owner；它**不是** Desktop 原生业务对象恢复。§17 也没有把这种恢复列为 P6 出口，因此不能以此扩大实现或声称已经恢复。
- 当前最大的安全可执行增量是完整 owner 的 Android writer → Desktop strict-reader 自动门。它把最小真实文件链无法覆盖的字段保真变成连续的跨端回归门，同时不新建用户入口、不写设备或用户数据。

## 未关闭的 P6 原始出口

P6 仍未退出。完成原始 P6 还需要在允许的真实环境中保持 Android 正常导出、Desktop 正常导入与再导出回读的精确保真，并完成受影响布局/异常恢复的独立平台验证；Windows 交付则只能由 Windows 本机的签名、安装和运行记录关闭。真实 Provider、同步、OPPO、发布和 P0–P11 的其余阶段均是独立门。
