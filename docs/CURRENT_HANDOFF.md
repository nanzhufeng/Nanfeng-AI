# 南枫 AI 当前交接

## 2026-09-10：完整复盘与边界审查续验

- 已更新完整开发档案、可迁移经验、长期 AGENTS 与五个项目 Skill；全量文件与 Git 清单见 [审计记录](review/20260910/verification.json)。本阶段复盘材料纳入独立本地文档 checkpoint（提交主题 `docs: consolidate full project retrospective and boundary evidence`），业务 checkpoint 仍为 `c4aad94`；未推送或发布。
- [边界复核](review/20260910/BOUNDARY_REVIEW.md)：隔离 Rust 探针 4 通过／2 红灯，确认恢复锁顺序与已配置目录缺库问题；模拟头像 handler 两例均确认超限响应完整读取后才拒绝。Android 接收方授权缺少绑定为源码发现，真实调度未验。
- 本阶段没有修复业务代码、部署或接触真实用户数据。既有全套通过不覆盖上述新增红灯；后续修复应逐项建立行为回归。

## 2026-09-10：最终回归与正式增量固化

- **代码 checkpoint：** `c4aad94`，覆盖上次 `dd3a445` 后累积的 Android／Desktop 源码、测试、Room Schema 66、依赖与验收工具；本地提交，未推送或发布。临时截图、测试报告、APK 与 bundle 不进入代码提交。
- **当前合同读取门：** [Android 会话合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)、[设置合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)、[运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)、[转写合同](ANDROID_TRANSCRIPTION_UI_CURRENT_CONTRACT.md)、[Desktop 会话合同](DESKTOP_CHAT_FIRST_UI_CONTRACT.md)、[本机数据合同](C14_LOCAL_DATA_PARITY_CONTRACT.md)。这些合同约束行为，历史验收仅证明发生时的版本。
- **Android 增量：** 启动恢复使用退出时实际会话 ID 与时间／生成状态，不再回退置顶首项；记忆摘要支持整篇编辑、并发修订检查、事务替换与失败保稿；设置的六风格全屏选择、居中粗体标题／较小说明已实现；Schema 65→66 保存发送授权时间与披露版本，不记录正文。
- **Desktop 增量：** 设置 patch 串行保存及 SQLite 关闭重开；独立数据路径配置与下次启动迁移；20 个共享动作使用 Android 原始矢量；Composer 根据真实 scrollHeight 在 36–190px 间伸缩。历史 C-01～C-16 已验项不重复宣称本轮新验收。

### 本轮最终回归

| 层级 | 当前结果 |
| --- | --- |
| Android JVM | 1102 tests，0 failures，0 errors，3 skipped；跳过的是需要用户真实 ZIP 路径的 opt-in 测试 |
| Android Debug 构建 | `:app:assembleDebug` 通过，只作本地构建证据 |
| Desktop Node | 251/251 通过 |
| Desktop Rust | 209/209 通过，无 ignored；历史 OAuth callback 单例失败本轮全套未复现 |
| 静态与协议 | lint、typecheck、protocol golden、inventory、静态 build 通过；inventory 的 43 个 invoke 无直接测试引用仍是覆盖提示，不冒充全部入口行为验收 |
| 七主题 computed style | 首轮旧检查失败；修正为生产设置渲染器＋完整 CSS 后，七色 RGB 与无背景图覆盖全部通过 |

- 文档回归曾发现精简入口缺少“当前合同读取门”标识，补回后完整 JVM 再跑仍为 1102／0／0／3；Desktop Node 再跑 251/251。新入口链接检查与最终差异空白检查通过。
- 详细命令和本轮日志保留于 `/tmp/nanfeng-final-20260910/`，摘要已固化在本交接；临时日志可能随系统清理，不能作为唯一长期结论。
- **Release/Lint 收口：** `:app:lintRelease :app:assembleRelease` 通过；Lint 0 errors、101 warnings、19 hints，保留为既有质量债，不称零告警。APK 为 `com.nanzhufeng.ai`，versionCode 66／0.3.0-p10j，非 Debug，正式签名验证通过，28,247,380 bytes；SHA-256 `9b287e107363e7dfe5bac00e674cca6bd3e3e7bc81e1ee3a2c77d9a91142e169`，与引用任务已安装产物一致，无须重复覆盖。
- 本轮没有重打 Desktop 原生 bundle；引用任务已完成相应构建，本次完整 Rust 测试与静态 build 不替代最新原生窗口验收。

### 已有证据与仍未验收项

- 引用任务 `南枫AI 33 - Android` 的 2026-09-10 覆盖记录已经完成：同签名正式包，安装后 APK hash 一致，首次安装时间和 CE／DE 数据目录标识保持。此次没有重复安装，也没有再次读取手机业务数据；该历史安装证据不代表本轮手机视觉验收。
- 原生 Desktop 全图标同步尚未完成；20 个共享图标的替换不等于所有图标已统一。最新 Composer 与 Android 全屏风格页尚未在本轮原生实看。
- Desktop 数据路径仍待原生选目录、重启和重新安装验证；设置虽有本地落盘回归，仍未覆盖所有独立 owner 的跨安装升级。未迁移用户真实数据。
- 本轮未运行模拟器、OPPO、任何 connected Android 测试、真实 Provider、Google／Supabase、通知或远程网关。既有 Sonnet 短消息成功只证明该次探针，不证明附件／长回复／输入到落库全链。
- 下一步若继续产品验收，应从上述未验项选择一个独立增量；当前代码／回归／文档 checkpoint 已收口，不重做历史已完成项。

### 历史证据索引

- [本轮前完整交接归档](archive/CURRENT_HANDOFF_BEFORE_20260910_CHECKPOINT.md)：保留原有全部独有记录，仅调整相对链接以适配归档目录。
- [完整开发档案](南枫AI完整开发档案.md) 与 [可迁移开发经验](可迁移开发经验.md)：追加本轮长期事实；2026-09-01 及之前的数字、快照均为历史。
