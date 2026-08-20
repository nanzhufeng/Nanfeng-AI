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
- 凭据、Provider 诊断、fixture、离线 Eval、工程控制和未具备真实 owner 的能力不得借“功能审阅”进入普通设置。
- 当前登记项：ChatGPT / Claude ZIP 导入（建议保留设置入口、不加对话主页快捷键）；未关联媒体人工关联（建议仅在存在候选时于批次详情提供二级操作）。

## 入口矩阵

| 功能 / 实现 owner | Android 入口 | Desktop 入口 | Settings 入口 | 普通用户适合度 | 缺口 | 风险 | 最小验证 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 本地对话、草稿、附件与会话管理 / `ConversationFoundationViewModel` | 对话 Composer、Drawer、长按会话、搜索 | Chat Composer、侧栏、消息工具 | Android 对话；Desktop 隐私与数据管理 | 是 | 无 | 不得把本地发送改为外发 | 现有聊天 shell 回归、重启读回 |
| Compare 显式直接执行 / `CompareVisibleExecutionOwner` → `CompareExecutionApplicationOwner` | 模型菜单行、Composer `对比`、模型长按 | 同三入口；直接显示 Desktop 不可执行状态 | 不进入设置 | 是（仅显式外发动作） | Android 的非空草稿真实直接执行仍需用户给出的非敏感短文本、已配置凭据与 HTTP 授权；Desktop **没有执行 adapter** | 空草稿不触及 owner；附件拒绝；Desktop 不得假装外发 | Android/Node 合同覆盖三入口、空草稿与 Desktop 无 owner；模拟器关闭态、菜单态、重启；真实外发不在本批 |
| 当前对话导出 / `ConversationFoundationViewModel` | Drawer → 设置 → 对话 → 导出当前对话 | 无同语义 owner | Android 有；Desktop 仅工作区交换包导出 | 是 | Desktop 不应把工作区交换包伪称当前对话导出 | 导出范围必须准确 | Android 既有导出读回；Desktop 仅声明当前能力 |
| 本地工作区交换包 / Desktop Rust workspace exchange owner | 无同类跨端格式 owner | 工作模式导入入口、顶部导出 | Desktop 数据设置已有导出；本批补充导入入口 | 是 | 设置页缺少已实现的导入工作区入口 | 导入必须预检、显式确认、独立工作区，不能覆盖 | Desktop Node 渲染契约、native picker 后续验收 |
| ChatGPT / Claude / 南枫知识库静态会话导入 / 各自 import task owner | 设置 → 数据与导入 | 设置 → 数据 → 三个导入任务 | 双端均有 | 是 | 无 | app-private 副本、逐项确认，正文不执行 | Android/desktop 各自 importer 合同 |
| Markdown、JSON 知识、PDF 文本、网页文本快照 / Android 各自 ViewModel | 已有 picker / Dialog handler，但 `导入与适配` 未渲染普通入口 | 无 Desktop production owner | Android 本批补到设置 → 数据与导入；Desktop 标记不存在 | Android 是；Desktop 不存在 | Android 可用功能此前不可发现；Desktop 不虚构入口 | 外部文本不可信；网页入口仍以既有独立确认 owner 为准 | Android 源码入口合同、assembleDebug |
| 本地备份与恢复 / `LocalBackupRestoreViewModel` | 设置 → 数据与导入 → 备份与恢复 | 无等价 Desktop backup/restore owner | Android 有 | 是 | Desktop 不能把交换包导入写成“恢复备份” | Android 恢复具有替换语义；Desktop 导入独立工作区 | Android 既有合同；Desktop 保持不存在 |
| 项目、知识、记忆、当前 Context / 各自 Android ViewModel；Desktop workspace owner | Android 非聊天 route / `更多` 控制面未由当前 Drawer 普通路径公开 | Desktop 工作模式 | 不应塞入普通聊天设置 | 需单独 IA 决策 | Android 普通用户发现性不足，但不是本批的安全入口缺失 | Context/记忆不能暗中加入 Prompt；不得以工程控制代替产品 IA | 先做独立产品 IA 合同，不直接暴露 |
| 模型本地目录与自动路由 / P6-G owner | Composer 模型菜单；设置模型服务 | Composer 模型菜单；设置模型与路由 | 双端有 | 是（仅本地选择） | 无 | 不得把目录/fixture 当 Provider 或凭据配置 | 现有 P6-G 合同 |
| 凭据、Provider 诊断、离线 Eval、P8 控制与 fixture | 不作为普通入口 | 不作为普通入口 | 不作为普通设置 | 否 | 非缺口 | 暴露会误导真实联网能力或污染用户路径 | 静态搜索/渲染合同确认不出现 |

## 本批实施选择

1. Android：只补四个已有本地 import/snapshot owner 的设置入口，不改变其解析、确认、持久化或网络边界。
2. Desktop：只补既有 workspace exchange 的设置“导入工作区”入口，复用既有 `start-import` → 预检 → 确认链路；不称其为备份恢复。
3. Compare：保留现有三种 Android/Desktop UI trigger；显式 Compare 本身就是产品命令，不再额外显示产品级确认面。Desktop 外发仍明确未实现；Android 的非空草稿真实执行仍需用户提供非敏感短文本、外部凭据与 HTTP 授权。

## 不在本批实施

- Desktop 当前对话导出、Desktop Markdown/JSON/PDF/网页导入：当前没有可复用生产 owner，需各自的协议与数据边界合同。
- Android 项目/知识/记忆/Context 的普通 chat-first IA：需要独立的导航与信息架构决策，不能借本次“入口补齐”把控制面塞进聊天或设置。
- 真实 Compare、Provider、Key、HTTP、OPPO：均不在本批授权内。
