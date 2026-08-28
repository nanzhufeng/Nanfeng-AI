# 南枫 AI 完整开发档案

> 复盘日期：2026-08-28；上一代码基线：`da20412`，本档案随当前 checkpoint 增量更新，最新 commit 以 `git log -1` 读回为准。本档案只汇总由当前源码、配置、测试产物、当前合同或 Git 记录支撑的事实，不把历史计划、安装记录或单元测试写成真实服务闭环。
>
> 当前事实读取顺序：`AGENTS.md` → 当前源码 → [CURRENT_HANDOFF.md](CURRENT_HANDOFF.md) 顶部 → 三份当前合同 → 领域合同与历史证据。会话／搜索、设置、普通聊天上下文分别以 [会话合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)、[设置合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)、[运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md) 为唯一正文。

## 0. 2026-08-28 当前 checkpoint 增量

- ChatGPT 累积 ZIP 已形成从严格预检、source-tree 去重合并、官方附件映射、Room Message Tree 挂载到普通搜索／预览链路的单一数据流。旧包→新包真实验收为 `784` 个去重对话；新包官方可归属附件完整 Room 验收为 `853/853`，两项都确认 `skipped=0, failures=0`。
- 最后一个附件不是字节、catalog 或数据库丢失，而是文本 parser 剪枝后把仍有后代的可渲染消息当成当前叶，触发 `当前分支必须指向叶消息`。owner 现在选择官方路径下的合法后代叶，并显式统计会话失败，不再静默降级。
- Room Schema 当前为 `55`，`MIGRATION_54_55` 在产品注册与迁移契约中都有证据。导入 JSON 和 ZIP 保持两张独立大卡，各自结果入口始终可见。
- 当前完整 JVM 是 `815 tests / 60 failures / 3 skipped / 0 errors`，不是全绿。3 个 skip 为需真实 ZIP 环境变量的 opt-in 测试，均已另行真包运行通过；60 个失败依然是后续回归债务。
- `lintDebug` 为 `0 errors, 84 warnings, 13 hints`，`assembleRelease` 通过。最新本地 APK SHA-256 为 `b0d1008fde0bbbddaaad57d925e09ca067dbbb64407d9783904955a999481929`，版本 `66 / 0.3.0-p10j`，正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未安装 OPPO；设备仍是 `d86b...` 旧诊断包且当时附件回读为 `0`。

## 1. 复盘范围与结论

南枫 AI 是当前以 Android 为可构建客户端的本地优先、多模型 AI 工作台：会话、知识、Memory、项目、附件、导入／导出和调用审计先保存在设备，用户可在本机配置后选择模型直接发起 Provider 调用。

原 2026-08-27 只读盘点覆盖：299 个 Android 主文件、213 个 JVM 测试文件、289 个 `docs/` 文件、根 Gradle 配置、Manifest、Room Schema、`upload-gateway/` Go 服务及截至 `da20412` 的 120 个提交。这些是当时快照，不得用来否定上方 2026-08-28 增量的源码、Schema 55 与测试事实。

项目不是“完全验收完成”状态：上一轮完整 JVM 聚合为 **796 项、60 项失败**；本轮复跑又在 245 项完成、60 项失败、1 项 skipped 后因 Android Studio JBR C2 `SIGSEGV` 终止，无法生成新的完整聚合。搜索横滑返回、长文本阅读、视觉细节和真实 Provider／费用路径仍待人工闭环。

## 2. 项目目标与范围

### 已实现方向

- 本地优先的多模型对话：会话树、分支、草稿、运行时、恢复、模型归因与调用诊断。
- 个人工作区：项目、知识、Memory、关系、附件、本地检索、搜索、导入／导出和本机备份／恢复。
- 多 Provider：OpenRouter、Qwen、DeepSeek 的模型槽位、模型解析、传输与实际接收方归因。
- 用户体验：Compose 聊天、抽屉、搜索、消息投影、Markdown、设置、主题／字体、会话生命周期和本地提醒。

### 未可声称的范围

- Google／Supabase、真实 Agent 工具、生态接入和 AI Hub 有接口或历史合同，但没有本轮真实远端验收。
- Android 是 `settings.gradle.kts` 唯一包含模块。仓库没有当前 Android CI 工作流；历史 Desktop 资料不等于当前可构建／可发布 Desktop 产品。
- `upload-gateway/` 是独立 Go 模块，不由根 Gradle 构建；尚无 Go 测试文件，也无部署、TLS、域名或真实上传验收。

## 3. 技术栈、配置与目录

| 层面 | 当前实现与依据 |
| --- | --- |
| Android | Kotlin、AGP 9.3.1、Kotlin/Compose 2.2.10、Compose Material 3、minSdk 26、target/compileSdk 36。 |
| 本地数据 | Room 2.8.4、Schema 55、`1→55` 连续迁移与 schema 导出。 |
| 生命周期 | Activity + Compose + ViewModel；普通发送由数据同步前台服务与持久化运行时状态承接。 |
| 网络／文件 | Provider transport、Android Keystore、App 私有文件、SAF、FileProvider、WorkManager、PDFBox Android。 |
| 测试 | JUnit 4、Robolectric、Room testing、host SQLite JDBC；按 ai/data/domain/ui/app 分组。 |
| 独立网关 | Go 标准库 HTTP 服务，提供 SHA-256 校验、断点续传与短期签名 URL。 |

```text
app/src/main/java/com/nanzhufeng/ai/
├── NanfengAiActivity.kt       系统入口与 ViewModel 装配
├── app/                       AppContainer 与跨 UI 编排 owner
├── ui/                        Compose 页面、组件、导航、ViewModel 与视觉令牌
├── domain/                    模型、UseCase、端口、协议、策略与状态机
├── data/                      Room、Keystore、SAF、文件与 Android adapter
├── ai/                        Provider adapter、传输、解码与执行器
└── background/                普通聊天前台服务执行桥

upload-gateway/                独立 Go 附件服务
docs/                          当前合同、交接、决策、历史证据
.agents/skills/                可重复开发流程
```

## 4. 核心架构与模块

### 入口与装配

`NanfengAiActivity` 创建 `AppContainer` 并将 UseCase／存储 owner 注入各 ViewModel；`NanfengAiApp` 与 `ConversationWorkspace` 负责 Compose 路由和会话 surface。Activity 不直接保存聊天业务内容，Android 具体实现主要集中在 `data/`。

### 会话、运行时和发送 Attempt

Room 保存 Conversation、MessageNode、内容块、草稿、运行时事件、生命周期状态、Attempt 与归因。`NormalChatOpenRouterExecutor` 负责普通发送的主编排：提交草稿、启动 Provider runtime、解析模型、组装上下文、调用 transport、写入回复／归因／审计、取消及重试。

`NormalChatSendAttempt` 是持久化的无正文事实，包含会话、用户消息、Provider、实际 model、idempotency key、状态和安全错误码。状态为 `PENDING`、`SENDING`、`ACCEPTED`、`STREAMING`、`COMPLETED`、`FAILED`、`UNKNOWN`、`CANCELLED`；中断会进入 `UNKNOWN`，只能由用户显式重试或标记失败，不能静默重发。

### 模型和 Provider

`NanfengModelServiceCatalog` 是用户可见模型槽位和 Provider 映射源；`UnifiedModelResolver` 合并 OpenRouter 注册表、精确冷启动回退、Qwen／DeepSeek 模型档案和健康状态。`OfficialProviderChatTransport` 与 `ChatProviderAdapters` 承担协议差异，调用归因与诊断保存实际接收方、模型和去敏状态。

设置与 Key 分离：`AndroidModelServiceSettingsRepository` 保存配置，`ProviderCredentialStore` 保存凭据，Key 不进入 Room。代码路径存在并不证明任何真实 Provider 对话、账单或模型质量已成功。

### 上下文、知识和 Memory

`LocalContextBroker` 经 `RoomLocalContextIndex` 在本机按问题、会话／项目 scope 和 token 预算选取当前路径历史、ACTIVE Memory 与 ACTIVE Knowledge。附件字节、凭据、运行日志、工具输出及同级分支不进入检索包；具体开关和外发语义以运行时上下文合同为准。

### 数据、附件和可移植性

- `NanfengAiDatabase` 为 Schema 54，保存会话、项目、知识、Memory、导入任务／receipt、使用账本、诊断、Attempt、归因与恢复记录；`MIGRATION_53_54` 增加收藏时间。
- `AndroidPrivateAttachmentStore` 保存私有附件；消息保存安全引用。预览、搜索、导入与外发各有独立 owner，不能绕过大小、MIME 与私有副本检查。
- JSON Knowledge、Markdown、PDF、网页文本、ChatGPT／Claude ZIP、南枫知识、v1 会话交换和 v2 工作区交换使用专属 parser／commit owner。v2 先严格预检再原子恢复，非空本机路径拒绝覆盖。

### UI 与设置

`ConversationWorkspace` 是会话／搜索主要 surface；设置由 `NanfengAiApp`、相关 ViewModel 与共享 `SettingsControlDimensions` 组成。会话合同约束抽屉、搜索、长按、文本投影、Composer、阅读控制和手势；设置合同约束层级、弹层、主题、开关和保存反馈。UI 数值不得从旧 P 阶段合同复制。

## 5. 关键数据流

### 普通聊天

```text
Composer / ViewModel
  → 前台服务接收 opaque conversation ID 与 SEND/RETRY
  → Executor 提交草稿并创建 Provider runtime / Attempt
  → LocalContextBroker 在本机选最小上下文
  → ModelResolver 解析实际 Provider 与模型
  → Provider transport / adapter 请求并写入 runtime 事件
  → Room 保存消息、Attempt、模型归因、审计与诊断
  → ViewModel 刷新会话与恢复操作
```

### 导入与恢复

```text
系统选择器 → 私有暂存 → 格式/大小严格预检 → 专属 adapter
  → domain owner / Room transaction → receipt + provenance → UI 安全摘要
```

格式不能互相替代 parser；禁止通过文件名、URL、路径或默认值猜测缺失事实。

## 6. 关键决策及原因

| 决策 | 原因与依据 |
| --- | --- |
| UI 与运行时各自只有一份现行合同 | 历史文档多、视觉迭代快；三份当前合同和 `decision-log.md` 明确旧阶段内容不能反向覆盖。 |
| 本地优先、按需外发 | Room、私有附件、Keystore、去敏审计和 `LocalContextBroker` 均反映这一边界。 |
| 用持久化 Attempt 管理发送 | Provider 结果可能未知；通过原 Attempt 的显式重试与用户放弃避免伪造未外发或静默重复扣费。 |
| 保存实际 Provider／model 归因 | Auto、目录和用户选择会变；助手页脚和账本不能从当前 Composer 反推历史实际路由。 |
| 导入／恢复按协议隔离并 fail-closed | JSON、ZIP、v1、v2 的安全条件不同；万能 parser 会破坏原子性、provenance 和拒绝规则。 |
| 主设备只允许正式包保数据覆盖 | OPPO 有用户数据；签名门禁和 P5 记录要求同证书、非 Debug、一次安全 overlay，永久禁止仪器测试。 |

## 7. 开发过程与 Git 历史

仓库有 120 个提交，起点为 2026-08-20 的 `e56d666 chore: establish Nanfeng AI baseline`。提交主题以 `docs`（31）、`feat`（14）、`fix`（12）和 P6/P11 专项为主，体现合同／证据先行再实现的节奏。

1. **签名与本地基础（8 月 20 日）**：release v2 签名来源、Android 基础构建、数据与 P6 交换链。
2. **导入、恢复与治理（8 月 20–23 日）**：严格 ZIP／JSON／工作区交换、Room 升级、生命周期与可访问性；Desktop 资料多为历史证据。
3. **普通聊天执行链（8 月 23–24 日）**：`d61805c` 接入直接 OpenRouter 发送，`1ded3da` 汇总 Provider chat 与会话 shell；后续扩展到 Qwen／DeepSeek、Attempt、前台服务、归因和模型目录。
4. **体验与设置收口（8 月 25–27 日）**：会话视觉、Markdown、抽屉、搜索、上下阅读、模型设置、Memory／资料库、开关、弹层与生命周期操作持续修正。
5. **当前 checkpoint（8 月 27 日）**：`da20412` 收纳 58 个 Android 文件与 4 个文档变更；它冻结工作树，不是全绿基线。

## 8. 测试、构建、部署与验证边界

| 层级 | 当前事实 |
| --- | --- |
| JVM 测试资产 | 213 个文件：domain 81、data 60、ui 50、ai 18、app 1、根包 3。 |
| 当前完整 JVM | `:app:testDebugUnitTest` XML：815 项、60 failures、0 errors、3 skipped。失败分布于 16 类，其中 `P6DConversationRowAccessibilityContractsTest` 40 项；其余涉及 System Bars、直接执行／预检、Composer／导航、媒体预览、设置、运行时上下文、Claude 导入和工作区 Documents UI。 |
| 真实 ZIP opt-in 验收 | 全量命令的 3 个 skip 是未传环境变量的真包测试。它们已另行用用户指定的旧／新 ZIP 运行：附件 `853/853`、对话去重 `784`，XML 均 `skipped=0, failures=0`。 |
| 已通过的定向门 | 共享 file ID、结构尾部合法叶选择、Schema `54→55` 迁移契约、真实 ZIP 映射／Room 链／旧新包合并通过；不抵消全量 60 项失败。 |
| Lint／Release | `lintDebug` 为 `0 errors, 84 warnings, 13 hints`；`assembleRelease` 成功。 |
| 永久禁区 | 不执行任何 `connected*AndroidTest`。视觉、真实 Provider、费用和账号路径不是 JVM 覆盖面。 |

`app/build.gradle.kts` 只接受完整环境变量或用户级 Gradle 属性提供正式签名；缺失即停止，不回退 Debug 签名。Release 产物为 `app/build/outputs/apk/release/南枫AI.apk`。

当前本地正式 APK 为 `66 / 0.3.0-p10j`，SHA-256 `b0d1008fde0bbbddaaad57d925e09ca067dbbb64407d9783904955a999481929`。本轮没有安装。OPPO 当前仍是 SHA-256 `d86b670e050da976aa07151928059b49a9e0936fd7422dbac5883ec683c7c390` 的旧诊断包，当时业务回读为 `0 个附件已恢复`。本地构建和签名验收不能替代最新真机 UI 与用户数据语义验收。

网关需要受控 HTTPS 域名、持久卷、高熵 token 与独立访问策略；Dockerfile 可构建镜像。没有 Go 测试或部署证据时，它只能视为可部署组件。

## 9. 踩坑、修复与经验

- **静态 UI 契约失配**：大幅 Compose 收口后，旧源码锚点集中产生 41 项失败。静态测试应随 owner 重构同步维护，不能依赖曾经通过。
- **Markdown 色值误判**：`#fff` 和转义符曾造成异常字号及控制符泄漏；展示层修复后不改写持久化正文。
- **一键到底反跳**：按末项定位会让长消息顶部对齐；现按可见阅读区推进并以物理边界停下，自动跟随和手动滚动分离。
- **开关尺寸叠加**：百分比多次推导导致失真；应以共享组件固定几何和语义，而不是入口各自计算。
- **文档漂移**：旧档案和 docs 首页写 Schema 45，旧档案还把 Attempt 列为待做；当前代码是 Schema 54 且已有 Attempt。此次已更新入口和本档案。

## 10. 文档与代码冲突

| 冲突 | 当前结论 | 处理 |
| --- | --- | --- |
| docs 首页与旧档案写 Schema 45/54 | `NanfengAiDatabase` 当前为 Schema 55，并有 `MIGRATION_54_55`、产品注册、迁移契约和 `55.json`。 | 以当前代码与导出 schema 为准。 |
| 旧档案称发送 Attempt 未实现 | `NormalChatSendAttempt.kt`、Room store、Executor 已实现状态、恢复与显式重试。 | 以当前代码为准；仍不把代码路径写成真实 Provider 成功。 |
| 旧档案称全量测试／Lint 已绿 | 当前完整 XML 为 815 / 60 failures / 3 skipped / 0 errors；Lint 已是 0 errors。 | 全量 JVM 仍列为未关闭回归；真实 ZIP opt-in 测试以单独 `skipped=0` 的 XML 为准。 |
| 早期 P3/P4 说未接发送／不自动进入上下文 | 当前 executor 与运行时上下文合同已有普通发送和相关检索 owner。 | 旧文档只保留历史证据，后续读取当前合同与交接顶部。 |
| 早期设备记录显示旧 hash 或未回读 | 当前交接顶部记录 code 66 的包级 hash 回读。 | 只采用最新记录；仍不代替视觉／服务验收。 |

## 11. 已知问题与后续路线

### 优先处理

1. **全量 JVM 与运行环境**：逐类归因 60 failures（优先 41 项 P6D 和直接执行／预检），并单独复现／规避 JBR C2 `SIGSEGV`；只有完成一轮无崩溃的完整套件后才可声明全绿基线。
2. **真机 UI 手工验收**：按当前合同验证搜索横滑返回、长回复上下阅读、设置层级、浅／深色与折叠屏视口。
3. **真实 Provider 最小闭环**：在用户自有合法配置和非敏感输入下验证成功、认证／余额／限流／断网、取消、未知结果与显式重试，并核对实际接收方、来源和费用。

### 设计后再做

4. 继续拆分 `NormalChatOpenRouterExecutor`，但保留 Attempt、运行时和归因事实。
5. 为附件网关补 Go 单元／集成测试、健康检查、删除回收和 TLS 运维证据；此前不默认启用外部附件路径。
6. P7–P10 云同步、Agent、生态与 Hub scaffolding 各自需要用户授权、真实目标、安全与回滚合同，不能因接口存在而上线。
7. 压缩历史文档中的“当前”歧义；新变更只进入当前合同、当前交接和决策日志。

## 12. 关键文件索引

| 主题 | 文件 |
| --- | --- |
| 入口与装配 | `NanfengAiActivity.kt`；`app/AppContainer.kt` |
| 会话与 UI | `ui/ConversationWorkspace.kt`；`ui/ConversationFoundationViewModel.kt`；`ui/NanfengAiApp.kt` |
| 发送与 Provider | `ai/NormalChatOpenRouterExecutor.kt`；`ai/OfficialProviderChatTransport.kt`；`ai/ChatProviderAdapters.kt` |
| Attempt 与模型 | `domain/NormalChatSendAttempt.kt`；`domain/ModelService.kt`；`domain/ResolvedModel.kt` |
| 上下文与数据 | `domain/LocalContextBroker.kt`；`data/local/RoomLocalContextIndex.kt`；`data/local/NanfengAiDatabase.kt` |
| 凭据、附件与备份 | `data/ModelServiceStorage.kt`；`data/AndroidPrivateAttachmentStore.kt`；`data/AndroidLocalBackupRestoreManager.kt` |
| 当前规则与证据 | 三份当前合同、`CURRENT_HANDOFF.md`、`decision-log.md`、`PRODUCT_FEEDBACK_DECISION_LEDGER.md` |
| 附件网关 | `upload-gateway/main.go`；`upload-gateway/README.md` |

回滚仅需恢复本文件与 `docs/README.md` 的上一个 Git 版本；本次不涉及数据迁移、APK、远端部署或设备写入。
