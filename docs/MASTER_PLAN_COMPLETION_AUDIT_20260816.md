# 南枫 AI 总控方案需求—证据完成审计（2026-08-16）

## 2026-08-20 当前总控状态（优先于以下历史审计）

以下状态以 `docs/CURRENT_HANDOFF.md` 顶部的当前记录、当前工作树、当前 release APK 与连接设备读回为准。历史段落保留为当时事实，不再作为排程结论。

| 总控项 | 当前事实 | 状态 |
| --- | --- | --- |
| release v2 签名与正式 APK | 新的项目专属 v2 签名已生成；当前源码 release APK 为 `com.nanzhufeng.ai` `51 / 0.3.0-p10a`，SHA-256 `7406d1de5818e013227d7a1ffb4083043e0922f767d013317040bab5f2c41ea2`，v2/v3 签名校验通过。该产物包含功能审阅、Claude 兼容修正与当前版本备份/诊断/Eval 元数据修正，尚未安装到 OPPO。 | 已关闭 |
| OPPO 安装链 | OPPO PKH120 当前只读保留较早 release-v2 APK `fc8f9ac6…`；它不是当前源码 APK `7406d1…`，本轮未覆盖安装。 | 保留数据；当前源码的 OPPO 安装/启动未验证 |
| Desktop P6-K 正式 bundle | 资源封印缺失已修复；最终 ad-hoc bundle 严格验签、原生 WebView、Settings 匿名 aggregate readback 已完成 | 已关闭 |
| Android P6-K 真正入口 | 当前源码以独立 `com.nanzhufeng.ai.p6eacceptancev2` 验收包运行，未覆盖 legacy `com.nanzhufeng.ai` / `com.nanzhufeng.ai.p6eacceptance`。经设置 → 数据与导入 → 导入中心 → 系统 DocumentsUI，已导入两份已授权 ZIP；临时中性来源均在私有暂存后删除。force-stop/cold-start 后只读回匿名 aggregate：ChatGPT 23 对话 / 719 未关联媒体；Claude 162 对话 / 0 未关联媒体；Claude 另有 120 项严格失败，与 Desktop 同源聚合一致。 | 已关闭（隔离 emulator 验收；不等同于 OPPO 导入） |
| Desktop Compare 本地执行边界 | 阶段 1–5 已形成 fail-closed owner、Security.framework credential seam、Settings 安全投影、30 秒 content-free direct-click command，以及未注册的 OpenAI-compatible mock adapter/安全双支 receipt。阶段 5 定向 Rust 合同 5/5 与 `clippy -D warnings` 通过，且仅使用 in-memory credential、mock HTTP 与内存 SQLite。 | 本地 mock-only 合同 | 不等同于真实执行：尚无 UI/Tauri 组合、用户凭据输入或读取、已核验 model/price catalog，亦无 HTTP 授权；用户仍须决定保留与费用上限。 |
| 实包媒体关联 | 两份实包没有可证明的 message-to-asset relation；`UNMAPPED_REJECTED` 是正确安全结果。K8 的人工精确关联功能已实现，但尚未发生用户在 Settings 中明确选择资产和目标消息的真实动作 | 外部用户操作 |
| 真实 Provider / 账号同步 / 生态 | 本地 owner、禁用状态和合同已存在；真实 HTTP、账号、OAuth/发布白名单、同步及生态目标仍分别需要已配置的外部服务和可验证账户/目标 | 外部条件，不得伪报完成 |
| 新增功能审阅与入口建议 | Android 与 Desktop 设置均新增“功能审阅”；当前登记 ZIP 导入、未关联媒体人工关联与 Desktop Compare 联网执行，展示待您判断的去留状态、入口建议与小字理由。今后每项普通用户新功能必须同步登记，默认不增加聊天主页/Composer 常驻按键。 | 已建立规则与双端实现 |

### 原始蓝图 P0–P11 全路线覆盖审计（2026-08-20）

`MASTER_DEVELOPMENT_BLUEPRINT.md` 第 17 节定义的是总控方案的**完整路线**。本表是唯一的总控排程入口；上表和后文需求矩阵只记录阶段内已获得的证据，不能缩小或替代 P0–P11 的退出门。

| 原始阶段 | 已确认的当前落点 | 仍未关闭的原始退出门 / 下一类工作 | 总控结论 |
| --- | --- | --- | --- |
| P0 总方案与治理冻结 | 蓝图、合同、交接、审计与功能审阅规则都已存在。 | 每次扩展继续维持蓝图、合同、当前事实三者一致；用户对总体方向的持续确认不由旧记录替代。 | 治理基线已建立；持续维护。 |
| P1 Android 工程与可测试领域基础 | Kotlin/Compose 工程、领域端口、结构化错误、构建和定向测试基础已长期运行。 | 保持依赖、静态检查与领域合同的回归门。 | 基础已建立；持续回归。 |
| P2 最小捕获与知识闭环 | 本地捕获、知识、导出、Registry/Invocation 安全事实与 mock/禁用边界已完成多项增量。 | 用户授权的真实非敏感 Provider 文本与图片、目标真机链路、真实保存重启导出及 Key 泄漏检查，均不能由本地 mock 代替。 | 未退出。 |
| P3 Claude 级多模型对话核心 | 对话树、分支、草稿、附件、展示和本地 attempt 谱系已落地。 | 真实流式、停止、失败/重试/换模型、部分计费、真实用量/成本可见追踪、长会话实测与 Provider 充分性判断。 | 未退出。 |
| P4 项目、记忆、上下文与知识完整化 | Projects、Memory、Knowledge、多个单独 Adapter、离线 Eval 与本地上下文控制面均有局部闭环。 | 真实上下文/语义摘要、缓存和成本质量基准、更多 Adapter 的逐个验收、真实 Provider/Harness 回归与用户价值证据；P3 的真实退出门仍是前置。 | 未退出。 |
| P5 Android 产品化与正式交付 | 项目专属 v2 签名、当前 release APK、P5-D 隔离 emulator 的 SAF 备份/恢复链已获得证据。 | 当前源码 APK 的 OPPO 安装/启动/折叠验收、旧 APK 升级迁移、全设备可访问性与性能、正式发布/商店交付。不得覆盖现有 OPPO 数据。 | 未退出。 |
| P6 Desktop 与跨端离线体验 | Desktop P6-K 原生 bundle/readback 与 Android 隔离导入主链已关闭；P6-L1/L2 已新增双端 `LOCAL_EXACT_HIT / MISS / INELIGIBLE / UNKNOWN` 的 content-free 本地精确复用索引及持久表（Android Room 37→38 / Desktop SQLite 19→20），P6-L3/L4 再以未注册的双端分派与消息引用有效性门保证命中仅交回同 scope 的有效既有消息引用、不会进入普通分派。P6-A 已以空白项目专属 AVD 的正常 Composer → Settings → DocumentsUI 输出 1,930 B 活动独立文本包；Desktop 空隔离根经 native picker 导入为新工作区并再次导出 1,692 B 包，双方 `semanticHash` 均为 `e49f216d043843639218a973a7fc4dbe5f8c9056d418c699e53d5afb3a460562`。本轮 Android `ExportWorkspaceExchangeUseCase` 已以显式 scope/关系/附件选择和私有 bytes hash mapper 覆盖 Project、Conversation、Knowledge、Memory、Relationship 与消息附件；它只通过 JVM/gateway 合同，不含 UI 或真实文件。 | 原始跨端退出仍要求完整对象在 Android/Desktop 正常入口的真实导入导出/回读、紧凑/展开独立验收与异常恢复；Knowledge 自带附件尚无 v1 关联位置而失败关闭。P6-L4 仍未接入执行、renderer、Provider cache 或台账，因而没有复用/节省事实；Windows/正式签名发布证据也未在当前总控表中闭合。 | 未退出；P6-A 单文本交换实链与全对象 mapper 的领域合同已关闭。 |
| P7 可选账号与端到端加密同步 | P7-A 至 P7-E 的本地协议、状态机、部署工件和 typed restore 主体已完成。 | 真实 Google、Supabase、OAuth、受控 HTTP、真实 Android/Desktop 跨设备恢复与部署回读。 | 未退出。 |
| P8 受控 Agent | 本地 ledger、harness、只读 inspect 与本地红队退出已完成。 | 每一个将来启用的真实工具必须分别完成成功、失败、取消、审计、幂等与恢复；不得把本地 fixture 说成真实 Agent。 | 本地主体退出；总阶段未退出。 |
| P9 南枫生态协议接入 | LOCAL_TEST_ONLY 集成底座已完成。 | 至少一个真实目标应用的稳定入口、权限 UI、用户确认、结果回读、撤销和审计。 | 未开始真实接入。 |
| P10 可选 AI Hub | 尚无两个真实应用消费者，触发条件未成立。 | 仅在触发条件成立后，验证 Hub 的多应用隔离、降级、回滚和运维恢复。 | 未触发，不提前建设。 |
| P11 长期运营与持续演进 | 版本化合同、审计、签名和交接已形成部分运营纪律。 | 模型/价格/Provider 复核、迁移、隐私删除、依赖安全、备份演练及 Android/Desktop 发布节奏是持续责任。 | 持续阶段，不存在“一次性全部完成”。 |

**总控的真实下一序列：** 先保持 P0/P1 治理与回归；P2–P4 的真实 Provider 与成本/质量证据、P5 当前源码 OPPO 和发布门、P6 跨端/Windows 正式门、P7 真实账号同步、P8 每个真实工具、P9 首个真实生态接入，均按原始依赖逐项推进。P10 只在两个真实消费者出现后触发；P11 永续执行。任何局部闭环都只能关闭其所属行的一段证据，不能宣布 P0–P11 总控完成。

## 结论

总控方案仍不能标记为“完整落地”，但 Android P6-K 签名、正常系统 picker、私有暂存删除、冷启动与匿名回读均已关闭；Desktop P6-K bundle/readback 与 Compare 阶段 5 的 mock-only adapter/receipt 边界亦已关闭。剩余项分别是用户明确选择的真实媒体人工关联、Compare model pair/cost cap 决定、真实 Provider/账号/同步/生态条件，以及尚未完成的跨端 UI/readback 项。没有以旧 APK、数据库注入、卸载、清数据、Key 或 HTTP 绕过任一门禁。

本轮已完成的无外部条件复验：Desktop P6-K 定向测试 7/7、Desktop 全量 Rust 库测试 68/68（唯一会触达 macOS Keychain 的既有自测主动过滤）、Desktop UI 合同当前复验 89/89、lint 与 typecheck 均通过。Compare 阶段 5 另有 Rust mock-only 合同 5/5 与 `clippy -D warnings`；没有真实 Keychain 或 HTTP 调用。

## 事实源与审计方法

- 当前事实以 `docs/CURRENT_HANDOFF.md` 顶部的 P6-K/K9 记录为准；`MASTER_DEVELOPMENT_BLUEPRINT.md` 的历史“下一唯一入口”不再可作为当前排程事实。
- P6-K 的产品边界和完成门槛以 `P6K_CHATGPT_CLAUDE_ZIP_IMPORT_ADOPTION_CONTRACT.md` 为准。
- 本轮 Android 验收使用项目专属 release v2 签名的隔离 applicationId；它不触碰 legacy 包或 OPPO 数据。DocumentsUI 与 force-stop/cold-start 均是正常用户路径，聚合查询只用于交叉验证且不读出正文、标题、ID、文件名或账户资料。

## 需求—证据矩阵

| 需求 | 当前证据 | 验证等级 | 结论 / 缺口 |
| --- | --- | --- | --- |
| ChatGPT / Claude ZIP 选择即直接导入 | Desktop 已按正常系统 picker 完成真实 ZIP 私有导入、退出重开与安全聚合/receipt 回读；Android 隔离 v2 包也已完成 Settings → DocumentsUI → private staging → cold-start 匿名 readback | 双端真实文件链 | P6-K 主链已关闭；不以此替代媒体/外部能力验收 |
| Conversation + Message Tree 复用既有 owner | 双端均无第二消息真值；Desktop 真包已写既有文本树并重开；Android 同源 Claude 聚合为 162 成功/120 失败，ChatGPT 为 23 成功/494 严格失败 | 双端真实；Android 冷启动读回 | P6-K 主链已关闭 |
| 未关联媒体安全处理 | 实包没有可证明 message-to-asset relation；保持 `UNMAPPED_REJECTED`；人工精确关联有双端合成 owner-to-renderer 合同 | 实包只读安全审计 + 合成验证 | 真实媒体只能由用户在 Settings 明确选择资产与目标消息后验证；不得推测关联 |
| profile / personalization | 白名单 owner 已实现；两份实包均为无可采纳字段的安全结果 | 实包安全聚合 + 双端合成 owner 合同 | 无可写的真实字段，因此不应人为重试或制造写入 |
| P6-K Settings 隐私与撤销恢复 | Android 不显示所选 ZIP 名；撤销失败保留 recovery task/archive；Desktop 与 Android 均有正常 Settings 的匿名 aggregate readback | 双端真实/自动合同 | P6-K Settings 已关闭；真实媒体人工关联仍需用户明确操作 |
| 既定聊天、抽屉、Composer 不回退 | Desktop UI 合同 82/82；含 P6-K 入口、Compare、精确 placeholder、抽屉/Composer 保护；Android 当前源码可完成定向合同与正式 release/lint | 自动 UI 合同 + 当前构建 | 仍须逐项以真实 Android 交互验收，不以构建替代可见行为 |
| Compare 可见入口与 Desktop 本地适配边界 | Android/Desktop 均有模型菜单、Composer `对比`、模型长按三入口；Android 为空草稿先返回。Desktop 保持 UI fail-closed，但底层已有未注册的阶段 5 adapter seam，可在 mock HTTP 与内存 receipt 中写入两支安全状态 | 代码/局部 emulator + Rust mock-only 合同 | 显式 Compare 是直接产品命令，不再有第二次产品确认面；Desktop adapter 仍未组合至 UI/Tauri/真实凭据或 HTTP。Android 非空草稿真实执行同样受用户内容、凭据与 HTTP 门禁，不在本轮执行 |
| 普通聊天真实 Provider | 生产边界、确认合同、账本和失败关闭机制已实现 | 本地合同 | 需要已验证目录、可用凭据、当次可见确认和用户明确非敏感输入；真实 HTTP 未授权执行 |
| P5 备份/迁移等真实 Android 链 | P5-D manifest/preflight 从当前打开的 Room 读取 Schema（37），候选 SQLite 也须一致；完整 1→37 迁移链受定向测试保护。当前以 release-v2 签名隔离包 `com.nanzhufeng.ai.p5dacceptance` 经 Settings/SAF 完成成功导出回读、非敏感变更后的受控替换、force-stop/cold-start readback，installed base.apk 与本地验收 APK SHA-256 一致 | 自动化 + 当前隔离 emulator 正常 UI/readback | P5-D 的当前包 SAF 恢复链已关闭；旧 APK `install -r` 升级迁移、OPPO、发布、云同步与 Provider 仍为独立门槛 |
| P7 同步、P9 生态、P10 联网路径 | 本地协议、禁用状态和 LOCAL_TEST_ONLY/配置表面已实现 | 本地合同 | 仍需要真实账号、目标服务/应用、外部授权及网络；不能借“总控”推定完成 |
| OPPO 验收 | OPPO 当前保留数据安装的是 release v2 `0.3.0-p10a`、SHA-256 `fc8f9ac6…` 的较早正式包；当前源码 release 未覆盖安装 | 安装/版本/包哈希只读核对 | 安装链已存在，但本轮不覆盖安装；当前源码功能、折叠连续性与 OEM 交互仍待按单项真实验收 |

## 本轮正式签名失败记录

受控命令为 `:app:assembleDebug`，只使用项目既有正式签名配置与 Android Studio JBR；Gradle 在项目配置阶段停止，提示必须恢复仓库外 keystore 与 macOS 钥匙串口令，或配置既有签名环境。没有读取、打印、导出、创建、替换或输入任何秘密；没有 APK、安装、picker、数据库写入、HTTP 或 OPPO 操作。

## 可继续的安全序列

1. 恢复既有签名记录的非交互可用性后，重新生成 APK；对 `emulator-5554` 仅 `install -r` 覆盖，核对本地 APK、安装 `base.apk`、前台 activity 与 UIAutomator package 三方一致。
2. 仅经 Android Settings 正常系统 picker 依次选择两份已授权 ZIP；使用中性临时名，私有暂存后删除临时源，并且不记录正文、外部 ID、附件名或账户资料。
3. force-stop/cold-start 后仅回读 task/receipt/conversation/message/media/profile 的安全聚合；真实媒体不做人工精确关联，除非用户在 UI 中选择具体匿名资产和目标消息。
4. 在不触及任何导入数据的前提下，先诊断并恢复 Desktop 最终 bundle 的白屏/WebView 启动可见性；恢复后才从正常 Settings 路径重做无正文 aggregate readback。真实 Provider、账号/同步、生态目标和 OPPO 仍分别保持独立验收债务。

## 不可关闭项

- 正式 Android APK、模拟器真实 ZIP 导入和冷启动读回。
- Desktop 最终 bundle 白屏后的正常 Settings 无正文可见回读。
- 用户在 UI 中进行的实包媒体精确关联（当前没有自动关联依据）。
- 真实 Provider/HTTP、真实账号/同步、真实生态目标和 OPPO。
