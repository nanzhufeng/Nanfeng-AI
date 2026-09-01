# 南枫 AI Android → Desktop 最终完成审计（2026-09-01）

## 结论

**当前声明范围内的本地产品 owner 已基本同步，但 Android → Desktop 全功能真实闭环仍不能标记为完成。**

本轮已补齐此前最后两个本地结构性缺口：按会话未读水位，以及应用退出后的生成排空、提醒／历史整理系统唤醒。仍未完成的是需要外部权限或真实服务的验收：有 Team ID 的 macOS 通知热／冷真实点按、真实 Provider／Key／账单、真实 Google／Supabase 双设备恢复、真实 Qwen 长音频与 SenseVoice 质量。Compare 原生 Stop 点击后的双 `CANCELLED` 也仍只有 transport／SQLite 自动证据。

审计基线只取当前 checkout 的 Android live source、三个 Android 当前合同、当前 Provider adapter 与 Desktop 当前实现。旧交接只用于定位，不覆盖 live source。

## 本轮新增闭环

| 能力 | Desktop 当前真实行为 | 持久化／安全边界 | 验证 |
| --- | --- | --- | --- |
| 对话未读 | 每个 workspace／conversation 保存 last-read 与 latest-completed watermark；后台会话完成后显示一个主题色圆点；真正打开清除自动＋手动未读；仅观察当前路由不清手动标记 | opaque ID 与毫秒水位进入设备本地 SQLite；不进备份恢复覆盖或账号同步；重复完成事件幂等，同题新 Attempt 独立推进 | Rust owner／重开／备份测试；Node 排序、DOM 顺序、菜单、设置开关与路由不抢占合同 |
| 多会话生成 | A 会话生成时可切到 B；A 的完成事件只更新持久化和未读，不抢回 B；每个会话保留独立取消信号 | 普通聊天 Attempt、正文和终态继续由既有 SQLite owner 管理 | Node 路由合同＋Rust普通聊天与未读集成测试 |
| 用户退出时的在途生成 | 普通退出请求先隐藏窗口，当前进程继续排空已发出的普通聊天／历史整理／提醒；完成后才完全退出 | 30 分钟后统一发取消，留 1 分钟落盘；仍未返回则退出，下一启动按既有合同恢复为 UNKNOWN，绝不自动重发 | Rust全量回归＋Node静态运行时门禁；未调用真实 Provider |
| 完全退出后的提醒／历史整理 | 有 ACTIVE／RUNNING 提醒或历史整理开启时，macOS LaunchAgent 每 60 秒以固定参数唤醒 headless cycle；无需要时立即移除 | plist 只含固定可执行文件、固定参数、应用数据根和安全日志；不含会话正文、计划正文、Prompt、Key 或 Provider payload；进程根锁阻止与前台实例并发恢复 SQLite | `/tmp` 唯一 label／home／root 的真实 launchd 安装→执行→撤销 1/1；撤销后无 agent／plist 残留 |
| 全量隐私删除 | `ALL_LOCAL_BUSINESS_DATA` 提交后立即重新计算后台需求并撤销 LaunchAgent | 删除已提交后，系统撤销失败只写 content-free `REMOVE_FAILED`，不把成功删除误报为失败 | Node源码门禁＋Rust隐私删除既有回归 |
| schema | schema 34：schema 33 为未读水位，schema 34 为后台运行状态 | 两张表均只存安全元数据；旧库增量迁移，高版本继续 fail closed | Rust migration／reopen／全量测试 |

## Android live source 逐域矩阵

| 领域 | 状态 | 已确认事实 | 尚未闭环的验证边界 |
| --- | --- | --- | --- |
| 设置 IA／外观／字段层级 | 已完成 | Desktop 保留宽屏一级／二级并列差异；字段标题上置；灰底亮白卡；SQLite revision 真值 | 480px 低于 Tauri `minWidth=780`，不纳入 Desktop 支持口径 |
| Direct／Auto 普通聊天 | 本地核心链完成 | 原子 USER＋Assistant＋Attempt、流式、停止、FAILED／UNKNOWN、显式重试、附件哈希、usage／cost；普通退出会先排空 | 强制结束／崩溃后仍恢复 UNKNOWN；真实 Provider／Key 未验 |
| 个性化／Memory／历史资料库 | 本地核心链完成 | 语气、昵称、职业、自定义指令、隐藏 interests、相关 Memory／Knowledge、12 小时整理、候选审阅、checkpoint 与显式重试均有真实消费者 | 真实 Provider／费用未验 |
| 网页搜索 | 代码与本地合同完成 | OpenRouter、智谱、Qwen、DeepSeek 请求形状与来源投影对齐 Android | 真实 current-info Provider 回读未验 |
| Compare | 本地核心链完成 | 单入口、固定双模型、一 USER＋两 sibling Attempt、独立 stop／error／retry／usage／cost、中断 UNKNOWN | 真实 OpenRouter／Key；原生点击双 Stop 后的双 `CANCELLED` 回读 |
| 提醒／计划监控／通知 | owner 完成，外部验收未完成 | 草案审阅、持久计划、DST／错过策略、scheduler、FAILED／UNKNOWN、显式重试、通知发送／安全点击桥、Usage Ledger、备份／同步、完全退出后 LaunchAgent 唤醒 | ad-hoc 包无 Team ID，现代通知热／冷真实点按仍不能验；详见专项矩阵 |
| 对话未读 | 已完成 | read/completion/manual watermark、单圆点、分区内手动未读排序、打开即清、重启一致、开关只控显示 | 未做正式用户数据或真实多窗口人工验收；自动合同已覆盖并发路由 |
| 后台／多会话连续生成 | 已完成 macOS 对应语义 | UI 退出时排空当前生成；完全退出后仅由固定 LaunchAgent 唤醒已确认提醒与历史整理；进程锁避免前后台竞争 | macOS 不伪装成 Android FGS；强制结束不继续普通回答，恢复 UNKNOWN |
| 模型设置／凭据／费用 | 本地核心链完成 | 固定端点、Provider scoped Key、退役模型 fail closed、实报费用优先、未知不写 0 | 真实 Provider 计费未验 |
| 附件／搜索／预览／删除 | 已完成既有范围 | 私有复制、SHA-256、真实大小、全局索引、预览、引用计数、最后引用清理 | 未声明的真实多模态 Provider 能力不扩写 |
| Google 账号／南枫云 E2EE | 代码完成、外部未验 | PKCE、恢复码、选中会话加密同步、冲突与未知提交语义 | 真实部署、真实账号、双设备恢复 |
| 南枫转写 | owner 存在、外部未验 | Qwen3-ASR 任务／取消／重试／时间轴／导出；SenseVoice 保持实验 | 真实 Qwen、长音频、账单、SenseVoice 质量／资源 |
| 诊断／隐私／备份／导入导出 | 已完成已声明范围 | 安全诊断、聚合隐私、原子备份恢复、JSON／ZIP／交换包 | 不把未调用外部服务或用户真实大库性能写成已验 |

## 本轮验证证据

- Node：`128 passed / 0 failed / 0 skipped / 0 todo`；lint、typecheck、protocol golden、静态 build 通过。
- Rust 默认集：`161 passed / 0 failed / 0 ignored`；`cargo check` 通过。
- macOS 系统级隔离验收：启用 `launchd-acceptance` feature，仅用唯一 `/tmp` home／root／script／label，`1 passed / 0 failed / 0 ignored`；结束后 `launchctl` 与 `/tmp` 均无该验收 agent 残留。
- 最新 Release bundle 已从当前源码重建：[南枫 AI Desktop.app](../desktop/src-tauri/target/release/bundle/macos/%E5%8D%97%E6%9E%AB%20AI%20Desktop.app)。`CFBundleIdentifier=com.nanzhufeng.ai.desktop`，ShortVersion／BundleVersion 均为 `0.6.0-p6d-dev`，主程序为 arm64、`30,629,280` bytes，SHA-256 `000b2d14b35446e6793e000ab890f915833dbd06063734964f6631966feb2d22`。严格 codesign 通过；签名为 ad-hoc、`TeamIdentifier=not set`，Gatekeeper `spctl` 拒绝，因此仅是本机开发 Release，不是 Developer ID／公证分发包。
- bundle 不携带平行 helper 或静态 LaunchAgent plist；固定后台入口位于同一主程序。产物 strings 回读包含 schema 34 的两张新表、`.runtime-owner.lock`、退出排空线程和 `--nanfeng-background-cycle-v1`；隔离副本实际以该参数运行并返回 0。
- 唯一显示名“南枫 AI 未读后台验收 8243”、Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.8243.mti5l5f2`、独立 `/tmp` HOME 与数据根完成 GUI 启动烟测：进程运行、SQLite `integrity_check=ok`、schema 34、未读表／后台表存在、`unread_indicators=1`、无计划时后台状态为 `NOT_REQUIRED`。正常退出及 headless cycle 后无进程、LaunchAgent、plist 或临时目录残留。
- scoped `git diff --check` 通过。仓库其他既有 Rust 文件仍不满足全库 `cargo fmt --check`，本轮没有借机格式化或覆盖用户的大量在途改动。
- 未运行任何 `connected*AndroidTest`，未连接、安装或操作 OPPO；未读取正式 Desktop 数据、附件、Key、真实账号或误建会话；未调用真实 Provider／Google／Supabase。

## 后续优先级

1. 用有效 Team ID 的独立验收包完成提醒热／冷真实点按、重复／已删除 no-op；这只补外部验收，不再改平行通知 owner。
2. 经明确授权后分别补真实 Provider／计费、Google／Supabase 双设备、Qwen 长音频；每项独立报告。
3. 用稳定原生自动化补 Compare Stop 点击后的双 `CANCELLED`，并对未读点和多会话完成做一次唯一 Bundle ID 的人工视觉复验。

因此当前状态应写为：**Desktop 的本地 Android 对齐 owner 已基本齐全；受签名、真实账号与真实外部服务约束的端到端验收仍在进行中。**
