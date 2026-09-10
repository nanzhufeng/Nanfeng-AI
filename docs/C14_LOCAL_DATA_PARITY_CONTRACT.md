# C-14 导入导出／本机数据跨端合同

> 证据日期：2026-09-03。本文只约束 C-14，不扩展 C-15 或 C-16。Android 当前源码与隔离同状态是可见事实源；Desktop 只做宽屏平台适配。

## Android 当前事实

- `导入与导出` 按顺序显示：对话 → `导入 ChatGPT JSON` → `导入 Claude JSON` → `导入结果` → `导入 ChatGPT ZIP` → `导入 Claude ZIP` → `导入结果` → 工作区 → `导入工作区` → `导出工作区` → 本机备份与恢复 → `备份` → `恢复`。
- 不向用户暴露 `导入为独立工作区`、`私有归档 v2 交换包`、`导出当前工作区` 等工程术语；内部版本和重放 owner 不独立占用主入口。
- `本机数据` 先显示总量，再按 `对话与内容`、`附件`、`导入概况` 分组。导入概况包含批次、对话、附件、个性化资料和南枫转写聚合事实。
- `选择清理范围` 当前有三项：`清理失败任务`、`清空知识与记忆回收站`、`删除全部本地数据`。全部删除必须先预览并精确输入 `删除全部本地业务数据`。

## 冲突记录

`ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md` 的较旧表述曾把会话归档／回收站清理写进本机数据页，并移除“知识与记忆回收站”清理项；当前 `PrivacyDataUi.kt`、`PrivacyData.kt` 及隔离 AVD 均明确显示本机数据页的三项范围。2026-09-03 最终独立审计已把现行设置合同修正为同一事实，旧表述不再有效。

## Desktop 共享 owner 与隔离门

- `desktop/src/local-data-view.mjs` 是 C-14 可见层级、聚合清单、清理范围和强确认门的唯一共享渲染 owner。Desktop 保留宽屏双栏、原生文件选择器和键鼠态，但入口名称、分组顺序、数据边界和危险层级不分叉。
- Browser 只消费 `c14-local-data-preview-fixture.mjs` 的合成聚合事实，不读写 SQLite、不提交删除。
- Tauri 只有在 C14 marker、唯一 `/tmp/nanfeng-ai-desktop-c14-acceptance.*` 根与 `--diagnostic-ui-schema-acceptance` 同时存在时才注入失败任务与回收站夹具。隔离根重开不重置清理结果。
- Rust 测试覆盖失败任务选择、回收站清理、全库强确认、过期指纹拒绝、重开持久和 SQLite 完整性。

## 验收矩阵

| 路径 | 必须事实 | 已验证层级 |
| --- | --- | --- |
| 导入／导出 | 用户术语、同序分组、取消无写入、成功有回读回执 | Android AVD，Browser 1440×900，隔离 Tauri，Node，Rust |
| 本机数据 | 只暴露聚合数量／字节与安全分类 | Android AVD，Browser，Tauri AX，Rust SQLite |
| 失败任务 | 先选择当前可清理项，再生成新指纹；状态变化则拒绝 | Browser，Tauri 夹具，Rust 成功／过期拒绝测试 |
| 知识／记忆回收站 | 只清除已软删除项 | Android AVD，Browser，Tauri，Rust 清理后重开 |
| 全部本地数据 | 预览后仍禁用，只有精确确认语可启用提交 | Android AVD，Browser 错误／正确确认语，Tauri 禁用态，Rust 真删除测试 |

## 2026-09-03 证据结果与边界

- Android 首次进入全部删除预览时触发 Room 主线程读取崩溃。`PrivacyDataViewModel` 已将预览与删除后库存刷新统一调度到 `Dispatchers.IO`；同路径复验进入强确认态，logcat 无新 FATAL。
- Desktop C-14 `4/4`，完整 Node `221/221`，Rust `191/191`，Android `PrivacyStorageNavigationContractsTest` `6/6`，Android APK 构建、Desktop lint／typecheck／inventory／protocol／static build 和 macOS bundle 通过。
- Browser 精确 `1440×900`，页面无横向溢出，console warning／error 为 0；错误确认语保持禁用，精确确认语才启用。
- 隔离 Tauri SQLite `integrity_check=ok`。原生导出交换包为 3142 bytes，SHA-256 `0dfc2b6fe911aa4441b0b4dbb131f2df6d4e3a6a82bad9d2da529b60f0997385`，ZIP 全条目检验通过。
- 未在图形界面提交任何删除；真删除成功／失败关闭由隔离 Rust 测试覆盖。未使用 OPPO、正式 Desktop 数据根、账号、Provider、Key、通知、真实外部服务或任何 `connected*AndroidTest`。

## 2026-09-03 原尺寸证据补全

- 当前闭环证据目录：`~/.codex/visualizations/2026/09/03/01a06645-e226-7cd0-82c7-07532b18a582/nanfeng-ai-c02-c06-c14-closure-20260903/`。目录保留 Android `1140×2616`、Browser `1440×900`、隔离 Tauri `1229×768` 的原始 PNG；三张联系表只作审阅索引，不替代原图。
- 三端均单独保留“导入与导出”“本机数据”“选择清理范围”“删除全部本地数据禁用门”状态。Browser 另保留精确确认语启用态；Tauri 只到禁用门并取消，没有提交删除。
- 当前完整回归为 Desktop Node `234/234`、Rust `196/196`；Android JVM 报告 `1084` tests、0 failure／0 error／3 个真实 ZIP 路径 opt-in skipped，`:app:assembleDebug` 通过。最新 macOS bundle 主程序 SHA-256 为 `64ead434df7974acfdcc5d3684a66308afe7a852405dce7c6c9549f2b369acc8`，ad-hoc strict codesign 通过。
- 隔离 Tauri C-14 根 `/tmp/nanfeng-ai-desktop-c14-acceptance.VhHM1L` 回读 `user_version=37`、`integrity_check=ok`、84 张表；进程子进程 0、网络 socket 0。验收后只关闭对应 QA 进程。

## Desktop 数据位置 owner

- 本机数据页显示正在使用的数据目录，并通过现有更改路径动作选择目标；`desktop_storage_location.rs` 在独立 app_config_dir 配置 active／pending，显式隔离验收继续使用隔离根。
- 下次启动在旧目录锁下复制到暂存目录，通过 SQLite quick_check 后发布；保留旧数据，拒绝包含关系、无效目录和不受支持文件。已有有效数据目录读取其现存数据；失败显式停止，不静默回退空库。
- 这不等同真实用户迁移、原生文件选择器或跨安装升级验收；当前层级与剩余边界只读 [当前交接](CURRENT_HANDOFF.md)。

## 存储边界修复（2026-09-10）

- 中断恢复发布暂存目录之前必须先取得源目录排他锁；空／无效 staging 不得发布，失败保留配置与旧数据。
- 已配置 active 目录必须含有效数据库；不能因数据库丢失而当作首次安装创建空库。配置尚不存在的首次启动保留初始化路径。
- 备份切换中断可能暂时缺库：由 `resolve_with_recovery` 在锁内调用既有 `desktop_local_backup_v1::recover_interrupted_switch`，只允许其恢复已落盘 checkpoint，再校验数据库；没有有效恢复结果则停止。已有有效数据库的普通启动不提前争抢运行锁。
- 隔离测试与原生边界见[修复记录](review/20260910/BOUNDARY_FIXES.md)；本轮未迁移真实用户目录。
