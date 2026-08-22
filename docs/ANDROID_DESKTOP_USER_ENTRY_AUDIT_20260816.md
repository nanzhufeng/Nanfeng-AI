# Android 与 Desktop 用户入口审计（2026-08-16）

## 任务契约

- 目标：让已经实现且适合普通用户的本地功能，从 Android、Desktop 或其设置中心有可定位入口；不把凭据、Provider 诊断、fixture、离线 Eval 或工程控制带入正常聊天/设置。
- 范围：当前 Android Compose 与 macOS Desktop chat-first 壳；Compare 的可见入口及其不外发边界一并审计。
- 不改：已人工确认的 Drawer、消息区、普通发送、普通 `自动` 语义、既有显性按钮及核心布局关系；不读 Key、不发 HTTP、不操作 OPPO。
- 判定：`缺口` 只表示产品入口，不把尚无跨端 owner、真实服务或安全合同的能力包装成可用功能。

## 新增功能审阅规则（2026-08-20 起生效）

- 每一项面向普通用户的新功能，必须在同一改动中登记到 Android 与 Desktop 的“设置 → 功能审阅”；缺少任一端不得标记交付。
- 每条登记必须写明：功能名称、当前去留状态（默认“待您判断保留或删减”）、现有设置入口、是否建议在常用界面新增按键，以及一条小字建议和理由。
- 默认仅提供设置二级入口；若建议在聊天、Composer、会话详情或工作页新增按键，必须在“功能审阅”中明确写出建议位置和误触/隐私影响，待用户判断后才可加。
- 凭据、Provider 诊断、fixture、离线 Eval、工程控制不得借“功能审阅”进入普通设置；未具备真实 owner 的候选能力仅可作为“待您判断”的审阅条目，不得展示为可用配置或执行入口。
- 当前登记项：ChatGPT / Claude ZIP 导入（建议保留设置入口、不加对话主页快捷键）；未关联媒体人工关联（建议仅在存在候选时于批次详情提供二级操作）；Desktop Compare 联网执行（待您判断，阶段 1–5 已完成 fail-closed owner、Security.framework 边界和未注册 mock-only adapter/安全 receipt；建议复用既有“对比”操作，不新增 Composer 常驻按钮；固定预设与状态仅由设置 → AI 模型服务承载）；本地精确复用（待您判断，仅双端离线精确键/既有消息引用索引，未接入普通聊天或 Provider；建议暂不增加常驻按键，未来真实复用、用量和清理完整后只在设置提供控制）；跨端文本会话交换（待您判断，Android 仅导出当前活动、未归属项目且无草稿/附件/工具结果的文本会话，Desktop 复用既有工作区导入；建议仅保留设置二级入口，不加聊天或 Composer 按键，且不把它写成备份、云同步或完整工作区保真）；完整工作区交换（v2）（待您判断，Android 从设置 → 数据与导入选择单个 v2 包，仅空本机可恢复，已有本机数据或待恢复记录均脱敏拒绝；Desktop 从设置选择单一 v2 包私有导入；建议只保留双端设置二级入口，不加聊天、Composer 或工作页按键，且不把它写成备份、云同步或对已有本机数据的覆盖）。schema 38→39 覆盖升级计数审计仅为隔离验收包内无 UI、无内容的工程诊断；不属于 Android 或 Desktop 面向用户的功能审阅项，也不新增入口或按键。
- 更多本地控制面（待您判断，Android 设置二级页进入既有 CONTROL，Desktop 设置二级页分别进入已有 Projects、知识与关系、长期 Memory；建议仅保留设置二级入口，不在聊天主页、Composer 或会话详情新增按键，避免将本地管理误解为发送、联网或自动执行）。

## 入口矩阵

Desktop 的 v2 已提交回导仍为“设置 → 数据与导入”的二级入口：用户只可选择匿名已提交私有记录，再经 native save picker 输出。Android/Desktop 功能审阅均写明此限制；不新增聊天主页、Composer 或工作页按键，且 Android 本轮不新增导入/回导能力。

| 功能 / 实现 owner | Android 入口 | Desktop 入口 | Settings 入口 | 普通用户适合度 | 缺口 | 风险 | 最小验证 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 本地对话、草稿、附件与会话管理 / `ConversationFoundationViewModel` | 对话 Composer、Drawer、长按会话、搜索 | Chat Composer、侧栏、消息工具 | Android 对话；Desktop 隐私与数据管理 | 是 | 无 | 不得把本地发送改为外发 | 现有聊天 shell 回归、重启读回 |
| Compare 显式直接执行 / `CompareVisibleExecutionOwner` → `CompareExecutionApplicationOwner` | 模型菜单行、Composer `对比`、模型长按 | 同三入口；直接显示 Desktop 不可执行状态 | 双端设置 → 功能审阅（仅去留审阅）；Desktop 设置 → AI 模型服务（仅 preset/status） | 是（仅显式外发动作） | Android 的非空草稿真实直接执行仍需用户给出的非敏感短文本、已配置凭据与 HTTP 授权；Desktop 只有未注册的 mock-only adapter，尚无 UI/Tauri/真实凭据/HTTP 组合 | 空草稿不触及 owner；附件拒绝；Desktop 不得假装外发 | Android/Node 三入口合同；Desktop 阶段 5 Rust mock adapter/内存 receipt 合同；真实外发不在本批 |
| 当前对话导出 / `ConversationFoundationViewModel` | Drawer → 设置 → 对话 → 导出当前对话 | 无同语义 owner | Android 有；Desktop 仅工作区交换包导出 | 是 | Desktop 不应把工作区交换包伪称当前对话导出 | 导出范围必须准确 | Android 既有导出读回；Desktop 仅声明当前能力 |
| 本地工作区交换包 / Android `ExportConversationExchangeUseCase` + SAF output port；Desktop Rust workspace exchange owner | 设置 → 对话 → 导出当前文本会话到 Desktop（仅当前活动、未归属项目、无草稿/附件/工具结果） | 工作模式导入入口、顶部导出 | Android/Desktop 设置 → 功能审阅；Desktop 数据设置已有导出 | 是（Android 仅窄范围文本会话） | Android 尚无项目、Knowledge、Memory、关系、附件或导入 mapper；完整跨端出口仍未关闭 | 严格预检、SAF 回读；Desktop 导入为独立工作区，不能覆盖 | Android 领域/入口合同；Desktop Rust preflight/re-export；真实 Android→Desktop 文件链待后续 |
| 完整工作区交换（v2） / Android scope→mapper/package→SAF export 与 OpenDocument→strict reader→atomic restore owner；Desktop `p6_workspace_exchange_v2` private owner | 设置 → 数据与导入 → 完整工作区交换（v2）→ 显式范围 → 系统保存位置；或选择单个 v2 包，仅空本机恢复 | 设置 → 数据与导入 → 完整工作区交换（v2） | Android/Desktop 设置 → 功能审阅 | 候选；Android v2 导出 + 空本机受控恢复 + Desktop v2 私有导入 | Android 已在两台项目专属 AVD 完成 DocumentsUI 恢复、同包 replay、篡改/超限拒绝、非空拒绝，以及修复后新空 AVD 的恢复→完整范围导出→同字节严格回读。Desktop native picker、跨端 committed reopen/re-export、Windows/发布仍待验收 | Android 导入只读取用户选中单 URI 的有限 bytes；strict preflight 后才进入 atomic owner，非空或 unknown recovery 一律不覆盖；完整范围二进制附件保守标为高敏感；仅独立 journal-interrupt 验收包可在首个附件提升后以一次性 app-private marker 受控终止，普通 UI 不新增入口或诊断；Desktop 只容许精确 `.nfai-exchange` 或 `.nfai-exchange.zip` | Android 定向 bridge/owner/UI 合同 + 两台项目专属空 AVD DocumentsUI 真文件链；Desktop Rust/UI 合同，native picker 跨端真实链待后续 |
| ChatGPT / Claude / 南枫知识库静态会话导入 / 各自 import task owner | 设置 → 数据与导入 | 设置 → 数据 → 三个导入任务 | 双端均有 | 是 | 无 | app-private 副本、逐项确认，正文不执行 | Android/desktop 各自 importer 合同 |
| Markdown、JSON 知识、PDF 文本、网页文本快照 / Android 各自 ViewModel | 已有 picker / Dialog handler，但 `导入与适配` 未渲染普通入口 | 无 Desktop production owner | Android 本批补到设置 → 数据与导入；Desktop 标记不存在 | Android 是；Desktop 不存在 | Android 可用功能此前不可发现；Desktop 不虚构入口 | 外部文本不可信；网页入口仍以既有独立确认 owner 为准 | Android 源码入口合同、assembleDebug |
| 本地备份与恢复 / `LocalBackupRestoreViewModel` | 设置 → 数据与导入 → 备份与恢复 | 无等价 Desktop backup/restore owner | Android 有 | 是 | Desktop 不能把交换包导入写成“恢复备份” | Android 恢复具有替换语义；Desktop 导入独立工作区 | Android 既有合同；Desktop 保持不存在 |
| 更多本地控制面 / Android `P5ANavigationViewModel` + `P5ARoute.CONTROL`；Desktop 既有 work-mode route | 设置 → 更多本地控制面 → 打开更多本地控制面 → Projects、知识（含关系）、长期 Memory | 设置 → 更多本地控制面 → Projects / 知识与关系 / 长期 Memory | 双端设置 → 功能审阅 | 是；仅可达既有本地 owner | Context 与离线 Eval 仍仅 Android CONTROL 现有范围，Desktop 不虚构相应 production owner | 不读凭据、不调用 Provider、不外发；不以内部 route/DB 注入创建对象 | `P5A_LOCAL_CONTROL_SURFACE_ENTRY_CONTRACT.md`、Android/Node UI 定向合同、后续全新 acceptance GUI |
| 模型本地目录与自动路由 / P6-G owner | Composer 模型菜单；设置模型服务 | Composer 模型菜单；设置模型与路由 | 双端有 | 是（仅本地选择） | 无 | 不得把目录/fixture 当 Provider 或凭据配置 | 现有 P6-G 合同 |
| 凭据、Provider 诊断、离线 Eval、P8 控制与 fixture | 不作为普通入口 | 不作为普通入口 | 不作为普通设置 | 否 | 非缺口 | 暴露会误导真实联网能力或污染用户路径 | 静态搜索/渲染合同确认不出现 |

## 本批实施选择

1. Android：只补四个已有本地 import/snapshot owner 的设置入口，不改变其解析、确认、持久化或网络边界。
2. Desktop：只补既有 workspace exchange 的设置“导入工作区”入口，复用既有 `start-import` → 预检 → 确认链路；不称其为备份恢复。
3. Compare：保留现有三种 Android/Desktop UI trigger；显式 Compare 本身就是产品命令，不再额外显示产品级确认面。Desktop 真实外发仍明确未实现（阶段 5 adapter 未注册）；Android 的非空草稿真实执行仍需用户提供非敏感短文本、外部凭据与 HTTP 授权。

## 不在本批实施

- Desktop 当前对话导出、Desktop Markdown/JSON/PDF/网页导入：当前没有可复用生产 owner，需各自的协议与数据边界合同。
- Android 项目/知识/记忆/Context 的普通 chat-first IA：需要独立的导航与信息架构决策，不能借本次“入口补齐”把控制面塞进聊天或设置。
- 真实 Compare、Provider、Key、HTTP、OPPO：均不在本批授权内。
