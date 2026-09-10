# Android → Desktop C-01～C-16 最终独立审计（2026-09-03）

## 最终裁决

**C-01～C-16 的本地视觉、生产 owner、隔离持久化和逐项所需本地证据已经闭环；不能把这项本地结论扩大成“外部服务也全部完成”。** 当前 checkout、关网隔离 Android、Codex In-app Browser 与当前严格签名隔离 macOS bundle 没有再发现未闭环的本地 C 项。准确结论是：

- C-01～C-16 的本地实现、主要 owner 与逐项所需本地证据均已闭环。C-16 的 4 种外观解析 × 3 档字号 × 7 层共 `84` 项，已经在 Android／Browser／Tauri 三端各逐项实渲染，形成 `252/252` 个强制证据槽。
- C-07 的真实选择器取消／导入／App 内预览／同 bundle 重启恢复，C-08 的有效 PNG／PDF／音频／视频／MD／JSON／ZIP／DOCX 正向或显式不支持，C-09 的启用／暂停／结果／失败持久态，C-10 的处理／失败／完成态，以及 C-12 的 Android 带数据深页与同内容 Tauri 深页均已补齐。
- 真实 Provider／网页来源／GLM-OCR 结果与账单、账号／同步、macOS 通知、Developer ID／公证不在本地完成声明内。
- 本次后续新增诊断专用 C-16 原生只读入口、证据审计器和自动采集脚本，并修复 Browser 跟随系统输入未真正进入主题 owner、Browser 证据静态根误指源码目录造成应用图标破图、Tauri 只读命令缺 ACL，以及验收入口一度打断既有启动顺序四处真实覆盖问题；Browser 已从 `desktop/dist` 全量重采并逐槽验证图片加载，没有配置或调用真实 Provider。

本裁决取代 [2026-09-02 最终完成度审计](ANDROID_DESKTOP_FINAL_COMPLETION_AUDIT_20260902.md) 中笼统的“可以收尾”，并以 [C-01～C-16 全状态台账](ANDROID_DESKTOP_VISUAL_PARITY_REAUDIT_20260902.md) 的逐项边界为准。

## 当前事实基线

- 仓库：`main@dd3a445c860c6baa7e00ab37cd5c2c64a8e4af17`。
- 本次闭环开始时已有 tracked dirty `77`、untracked `62`；全部保留，未 reset／clean／checkout／stash。
- Android 事实源：当前源码、四份现行 Android 合同及 2026-09-03 关网隔离 `NanzhufengFindN5Api35` AVD 的逐 C 原尺寸证据；只使用 `emulator-5554`，没有连接或操作 OPPO。
- Desktop 事实源：当前 `desktop/src`、当前测试、`desktop/dist`、最新 macOS bundle 和本轮新建的唯一 `/tmp` 隔离启动。
- 汇总审阅板：`/Users/nanzhufeng/.codex/visualizations/2026/09/03/01a06634-d3d4-75f2-8034-8979e48fa351/nanfeng-ai-final-independent-audit-20260903/all-c01-c16-meta-contact.jpg`。它只用于定位证据，不替代原尺寸单页。
- C-07～C-12 本次闭环证据：`/Users/nanzhufeng/.codex/visualizations/2026/09/03/01a06634-d3d4-75f2-8034-8979e48fa351/nanfeng-ai-c07-c12-closure-20260903/`；含 Android 与 Desktop 两张联系表及关键原尺寸单页。
- C-16 最终 252 槽证据：`/Users/nanzhufeng/.codex/visualizations/2026/09/03/01a066a9-bc6d-7b31-857d-0c0fa2efd991/nanfeng-ai-c16-final-20260903/`；manifest 回读每槽的逻辑 ID、请求／解析明暗、宿主明暗、字号、层、源码指纹、PNG 哈希和 pass，原尺寸证据不由联系表替代。

## 独立重跑结果

| 层级 | 当前结果 | 能证明什么 | 不能替代什么 |
| --- | --- | --- | --- |
| Desktop Node | `236/236` 通过 | C 合同、DOM／CSS、共享 owner 与行为 seam 当前可回放 | 原生 WebView、真实文件／服务 |
| Desktop Rust | `199/199` 通过 | SQLite owner、迁移、失败关闭、幂等／重启边界及 C-16 84 状态入口 | 可见交互与外部 Provider |
| Desktop 静态门 | lint、typecheck、protocol、主题七色通过 | 语法、命令边界、协议与主题 token | 完整用户路径 |
| 可达性清单 | 231 个可见 action、126 个 invoke；断链均为 `0` | 当前没有静态 action／command 断链 | 112 个 action、42 个 invoke 没有直接测试字符引用，不能当作覆盖率 |
| Android JVM | `1085` tests，`0` failure／`0` error／`3` opt-in skipped | 当前 JVM 合同通过 | 三个需显式真实 ZIP 路径的验收、真机 |
| Android build | `:app:assembleSearchAttachmentAcceptance` 通过 | 当前源码可构建独立隔离验收包 | 启动和视觉由下方 AVD 证据证明；不替代 OPPO |
| 本次 C-16 强门 | Android／Browser／Tauri 各 `84/84`，合计 `252/252`；Android 另有 84 份 XML | 四种外观解析、三档字号、七层均实际渲染且可追溯 | 外部 Provider、通知、正式数据根和真机 |

Android 首轮全量重跑曾有 `1` 个红灯：`CurrentRuntimeContextContractTest` 把历史标题“总控方案现行门提升”固定在交接前 96 行。C-12～C-16 追加后标题自然越界，实际三份当前合同仍位于交接顶部。本轮把断言修回真实约束“顶部存在当前合同读取门”，定向与全量重跑均通过。

三个 skipped 均是要求用户显式提供真实 ChatGPT ZIP 路径的 opt-in 验收；没有把它们写成通过。

## 最新 bundle 与隔离 Tauri

- 当前 bundle：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`。
- 版本／身份：`0.6.0-p6d-dev`，Bundle ID `com.nanzhufeng.ai.desktop`；ad-hoc strict codesign 通过，不是 Developer ID／公证包。
- 主程序：`31,061,408` bytes，SHA-256 `2611b4e4c8196b152f43dce95c94c8b32fc3f7a03cc728a93381a721f855d1a1`。
- 本次 C-02～C-06 QA 副本使用 Bundle ID `com.nanzhufeng.ai.desktop.c02c06acceptance.34624.mtl8tuj7` 与唯一根 `/tmp/nanfeng-ai-desktop-c02-c06-acceptance.81YMA5`；C-14 QA 副本使用 Bundle ID `com.nanzhufeng.ai.desktop.c14acceptance.34752.mtl8tyqb` 与唯一根 `/tmp/nanfeng-ai-desktop-c14-acceptance.VhHM1L`。两者均要求诊断 marker 与 `--diagnostic-ui-schema-acceptance`，互斥于其他验收夹具。
- C-07～C-12 QA 副本使用 Bundle ID `com.nanzhufeng.ai.desktop.c07c12acceptance.46345.mtlbsfv2` 与唯一根 `/tmp/nanfeng-ai-desktop-c07-c12-acceptance.YJ7tmX`，同时要求 `NANFENG_AI_DESKTOP_C07_C12_OFFLINE_ACCEPTANCE=1`、精确 `/tmp` 根和 `--diagnostic-ui-schema-acceptance`；ad-hoc strict codesign 通过。该根没有读取正式 Desktop 数据。
- C-16 QA 副本使用 Bundle ID `com.nanzhufeng.ai.desktop.c16acceptance.60115.mtle24j8`、唯一根 `/tmp/nanfeng-ai-desktop-c16-visual-acceptance.ZVKBul`、显式 marker、84 项之一的状态和诊断参数；与其他验收入口互斥。原生窗口固定 1440×900，84 项均逐个启动、截图、AX 回读并精确停止；ad-hoc strict codesign 通过。
- C-02 根回读为 1 workspace／1 conversation，固定 user message `C02-local-visual-fixture`，assistant 为 `FAILED / PROVIDER_NOT_ENABLED`；同根重开仍保持同一内容与侧栏列表。C-14 SQLite `user_version=37`、`integrity_check=ok`、84 张表。两个进程均无子进程、无网络 socket，验收后只终止对应 QA PID。

这个新鲜启动证明当前 exact bundle 能在隔离根进入正常窗口、建库并保持外部桥关闭；它不替代各 C 深层状态的逐页交互。

## C-01～C-16 逐项裁决

| ID | 独立裁决 | 仍需保留的边界 |
| --- | --- | --- |
| C-01 空对话 | **通过（本地同状态）** | Android、Browser、同 hash 隔离 Tauri 空态均有证据。 |
| C-02 有内容对话 | **通过（本地同状态）** | Android／Browser／隔离 Tauri 均为固定消息与 Provider 未启用失败态；Tauri 同根重开保持。 |
| C-03 抽屉／侧栏 | **通过（本地同状态）** | Android 抽屉、Browser 与隔离 Tauri 均显示同一“新对话”列表及一致入口顺序。 |
| C-04 模型根层 | **通过（本地同状态）** | Android／Browser／隔离 Tauri 原尺寸截图与语义树均确认同一根层；未配置 Provider。 |
| C-05 日常候选 | **通过（本地同状态）** | 三端均确认六项当前目录、顺序与会话联网请求状态。 |
| C-06 深度候选 | **通过（本地同状态）** | 三端均确认六项当前目录，末项 `Qwen3.8-Max`，无 `Kimi K3`。 |
| C-07 加号与风格 | **通过（本地全链）** | Android 实际文件选择器取消／导入／预览／重启恢复通过；隔离 Tauri 实际系统选择器取消／PNG 导入／原图预览／同 bundle 重启草稿与附件恢复通过。相机仅验证隔离模拟器安全打开退出与 Desktop 无可用镜头时失败关闭，没有保存镜头内容。 |
| C-08 搜索／预览 | **通过（本地合成资产）** | 隔离 Android 的有效 PNG／PDF／音频／视频／MD／JSON／ZIP／DOCX 均显示成功预览或真实显式边界；Desktop Browser 同内容只读夹具覆盖全部类型，Tauri 共享预览 owner 与持久化 Rust 合同通过。 |
| C-09 定时任务 | **通过（本机持久状态）** | 隔离 Android 与 Tauri 均可见启用、暂停、已保存结果、`PROVIDER_NOT_CONFIGURED` 失败；Tauri 同根重启后四态保持。系统通知与真实 Provider 未验。 |
| C-10 南枫转写 | **通过（本机状态矩阵）** | 隔离 Android、Browser 与 Tauri 均覆盖处理中、凭据缺失失败和带 Markdown／页数／Token／费用的完成态；Tauri 同根重开仍可读。真实 GLM-OCR 未验。 |
| C-11 设置／外观 | **通过（本地同状态）** | Android／Browser／隔离 Tauri 与大字／紫色重启回读证据完整；不包含正式数据根。 |
| C-12 模型／联网／记录 | **通过（本机脱敏深页）** | Android 与 Tauri 均显示同一脱敏 OpenRouter／`openai/gpt-5.6-terra`、输入 1,240／输出 680 Token、实际 `$0.01425`、3 项上下文／1,680 Token、网络失败／约 1.4 秒；Browser 同内容夹具通过。真实 Provider 来源、网页来源与账单未验。 |
| C-13 对话生命周期 | **通过（本地主链）** | 三列表、确认、返回、恢复与重启通过；不可逆永久删除 UI 提交由隔离 Rust 代替，符合非破坏性边界。 |
| C-14 导入导出／本机数据 | **通过（本地主链及原尺寸视觉）** | Android `1140×2616`、Browser `1440×900`、隔离 Tauri `1229×768` 原图已保留；导入导出、本机数据、三类清理与全部删除禁用门均有当前证据。图形界面没有提交删除。 |
| C-15 工作区／项目／知识 | **通过（本地主链）** | Android／Browser／隔离 Tauri 与 project owner 通过；未发送真实 Provider 请求。 |
| C-16 主题／字体／弹层 | **通过（本地三端 84 项全矩阵）** | Android `84/84` 原图＋XML＋JSON、Browser `84/84` 原图＋JSON、严格签名隔离 Tauri `84/84` 原图＋JSON；252 槽 manifest 强审计通过。Android 深色／大字与 Tauri `dark/large/orange/revision 3` 均有重启持久化回读。 |

## 本轮发现并修正的差异

1. 全状态台账同一页一度同时写“C-14／C-15 通过”和“明确不通过”；已统一为逐项真实边界。
2. `ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md` 仍把会话归档／回收站清理写入本机数据页，与当前 `PrivacyDataUi.kt` 及 C-14 AVD 事实冲突；已改为本机数据的三项真实范围，会话清理由对话管理 owner 持有。
3. C-16 过去把 `84` 项逻辑合同、`63` 项 Browser 渲染和代表性原生验收混写成“完整矩阵”；本次已真正补成 Android／Browser／Tauri 各 84 项，并以 252 槽 manifest 防止代表图或重复图顶替缺失状态。
4. C-14 原声明目录只保留联系表；本次后续已在独立目录重新保留三端原尺寸单页，关闭证据保全缺口。
5. Android 文档门测试依赖历史标题位置导致全量红灯；已改为锁定现行合同门并全量复验。
6. C-07 Desktop 草稿图片只渲染缩略图而没有预览动作；现改为语义按钮并复用现有 App 内原图预览 owner，定向测试先红后绿，本机 WebView 实点通过。
7. Android 最初合成 PNG 的 IDAT CRC 无效，真实 Skia 解码日志暴露后替换为 CRC 有效的 32×24 PNG；重新构建、安装并完成缩略图与预览验收。
8. C-12 Desktop 普通调用上下文种子的输入 Token 曾与 Android 的 1,680 不一致；已只修正该脱敏种子并由 Rust、Browser 与原生深页共同回读。
9. C-16 Browser 的 system-light／system-dark 过去仍读取当前 `matchMedia`，宿主输入没有进入生产主题 owner；现由合同输入解析，显式明暗与宿主明暗已独立验证。
10. C-16 第一份当前 Tauri 首态真实暴露只读命令缺 ACL；补最小 permission/capability 后原态重试通过，并增加回归防止再出现 `C16_VISUAL_ACCEPTANCE_GATE` 启动失败。

本次新增的 C-16 Rust／JS 只读入口只在唯一 `/tmp` 根、精确状态与诊断参数同时成立时进入；普通启动返回空状态。除覆盖入口与权限缺口外，联系表和疑点原图没有发现需要继续修改的 C-16 生产视觉差异。

## 尚未达到的最终门槛

若目标是严格写成“C-01～C-16 连同外部服务全部完成”，最小剩余清单是：

1. 若仍要求外部运行态，另行授权 C-09 通知／Provider、C-10 GLM-OCR、C-12 Provider／网页来源／真实费用和账号／同步等链路；本轮没有用夹具冒充它们。

## 未触碰边界

未操作 OPPO、正式 Desktop 数据根、账号、Provider、Key、通知或真实服务；未运行 instrumentation 或任何 `connected*AndroidTest`；未删除既有临时证据或改动用户业务数据。
