# 南枫 AI 完整开发档案

> 复盘日期：2026-08-28；稳定代码基线：`c1c9ae06615f8df4d9c94f01579963f4e2bb45d6`（`feat: make ZIP asset recovery resumable`）。用户已明确要求排除同仓库正在进行的后续任务及其未提交工作树；本档案不把这些 WIP 当成当前完成事实。本档案只汇总由稳定提交、配置、测试记录、当前合同或 Git 记录支撑的事实，不把历史计划、安装记录或单元测试写成真实服务闭环。
>
> 本档案事实读取顺序：`AGENTS.md` → `c1c9ae0` 稳定源码／配置／测试 → [CURRENT_HANDOFF.md](CURRENT_HANDOFF.md) 顶部 → 三份当前合同 → 领域合同与历史证据。会话／搜索、设置、普通聊天上下文分别以 [会话合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)、[设置合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)、[运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md) 为唯一正文；被排除任务完成后须另建增量更新，不能悄悄混入本次结论。

> **2026-08-30 增量读取门：** 上述 `c1c9ae0` 章节仍是 2026-08-28 的冻结复盘，不改写。已完成的后续工作以本档案下方“2026-08-30 当前 checkpoint 增量”、[CURRENT_HANDOFF.md](CURRENT_HANDOFF.md) 顶部和三份当前合同为准；不用增量章节反向改写历史测试数字或设备证据。

## 0A. 2026-08-30 当前 checkpoint 增量

- **当前代码冻结：** 本地 checkpoint 为 `b7e1c2f` (`checkpoint(android): freeze integrated product baseline`)，包含从 `2fec04c` 之后已完成的 Android 主代码、Room Schema 61–63、新增测试与当前合同。该冻结是当前项目事实的新起点；本档案后续不重复展开已在当前合同和专项合同中已确定的页面文案与单次验收数字。
- **增量范围：** 模型服务已扩展到 OpenRouter、DeepSeek、智谱与 Qwen 的统一设置／路由，附件经统一解析桥交给最终回答模型；南枫转写、导入附件、搜索、预览、调用账本和本机存储收敛到共享 owner；会话“待看”、选中对话云同步、标题／历史资料低成本路由和全模型文件材料组装已进入定向合同。精确行为仍只读取各当前合同与 `GLM_OCR_DOCUMENT_MARKDOWN_CONTRACT.md`、`P7F_SELECTED_CONVERSATION_SYNC_CONTRACT.md` 等专项合同。
- **最终自动证据：** 8 项随当前行为过期的静态／Room 合同已更新并定向全通过；全量 JVM 为 `1013 tests / 4 failures / 3 skipped`，比首次回归的 12 项失败减少 8 项，新增回归为 0。4 项保留失败是已有未完成基线：PDF renderer cache、统一 Dialog 遮罩、Dialog 内向边缘手势和设置画布；3 项 skip 仍是需要显式真实样本的 opt-in 门，不写为通过。`:app:lintVitalRelease` 与 `:app:assembleRelease` 通过。
- **产物与真机边界：** 重建候选为 code 66 / `0.3.0-p10j`、`27,984,644` bytes、SHA-256 `0dba16156f36e76a23fe52748ed0416c7126db1e3d6b207b896e24605a7adfe0`，非 Debug、v2/v3 正式证书为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。该重建包没有再次安装；OPPO 当前安装的仍是上一次同签名保数据覆盖并回读的 SHA-256 `4f4344e7f13764e6f1e32e9eeba981fd30174995a42a9dccaf330bda8efdf464`。构建成功不替代未完成的 4 项合同、opt-in 真实样本或新候选的再次真机覆盖。

## 0. 2026-08-28 稳定 checkpoint 增量

- ChatGPT 累积 ZIP 已形成从严格预检、source-tree 去重合并、官方附件映射、Room Message Tree 挂载到普通搜索／预览链路的单一数据流。旧包→新包真实验收为 `784` 个去重对话；新包官方可归属附件完整 Room 验收为 `853/853`，两项都确认 `skipped=0, failures=0`。
- 最后一个附件不是字节、catalog 或数据库丢失，而是文本 parser 剪枝后把仍有后代的可渲染消息当成当前叶，触发 `当前分支必须指向叶消息`。owner 现在选择官方路径下的合法后代叶，并显式统计会话失败，不再静默降级。
- P1 稳定基线把附件恢复从页面生命周期迁到可续跑的后台任务：Room Schema 为 `56`，`MIGRATION_55_56` 新增不含正文的恢复任务表；任务保存状态、进度、失败类型和按会话 checkpoint，退出页面后不再依赖 ViewModel 协程存活。导入 JSON 和 ZIP 仍保持两张独立大卡，各自结果入口始终可见。
- `af218e1` checkpoint 时完整 JVM 是 `815 / 60 failures / 3 skipped / 0 errors`；入口同步阶段为 `815 / 59 / 3 / 0`。`6d68ba7` 将标准套件恢复为 `815 / 0 / 3 / 0`；P1 `c1c9ae0` 增加恢复任务与测试后，稳定验证记录为 `819 / 0 / 3 / 0`。3 个 skip 是需真实 ZIP 环境变量的 opt-in 测试，不能计为已执行；P1 另行用新包完成附件 Room 链 `tests=1, skipped=0, failures=0, errors=0`，耗时 `257.597s`，仍不能外推为 OPPO 已恢复。
- `lintDebug` 为 `0 errors, 84 warnings, 13 hints`，`assembleRelease` 通过。最新本地 APK SHA-256 为 `b0d1008fde0bbbddaaad57d925e09ca067dbbb64407d9783904955a999481929`，版本 `66 / 0.3.0-p10j`，正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未安装 OPPO；设备仍是 `d86b...` 旧诊断包且当时附件回读为 `0`。

## 1. 复盘范围与结论

南枫 AI 是当前以 Android 为可构建客户端的本地优先、多模型 AI 工作台：会话、知识、Memory、项目、附件、导入／导出和调用审计先保存在设备，用户可在本机配置后选择模型直接发起 Provider 调用。

本次按稳定 Git 清单检查了代码、文档、配置、测试和历史，再对入口、数据库、发送、上下文、导入、附件、Desktop、协议、网关与部署边界逐项核对。`c1c9ae0` 共 `1008` 个跟踪文件：Android 主源码 `303` 个、JVM 测试 `223` 个、`docs/` `292` 个；另有 Desktop/Tauri、协议、Supabase 合同与独立 Go 网关。Git 有 `123` 个稳定提交，起点为 `e56d666`。这些数量不包含用户明确排除的后续未提交 WIP；历史档案中的 `299/213/289/120` 只保留为旧快照。

项目仍不能称为“全部真实验收完成”：Android 标准 JVM 已无失败，P1 新包附件链也有 `skipped=0` 记录，但旧包→新包、资产清单等其他 opt-in 分层门没有在本复盘任务中对 P1 checkpoint 独立重跑；OPPO 尚未覆盖 P1 Release，真实 Provider／费用、折叠屏浅深色与长文本手势仍需人工闭环。Desktop 的 Node 93 项、Rust 97 项、协议 golden、本地 lint/typecheck/build 本次均通过，但 Desktop 仍标为 spike/dev，不能据此宣称已形成发布产品。

## 2. 项目目标与范围

### 已实现方向

- 本地优先的多模型对话：会话树、分支、草稿、运行时、恢复、模型归因与调用诊断。
- 个人工作区：项目、知识、Memory、关系、附件、本地检索、搜索、导入／导出和本机备份／恢复。
- 多 Provider：OpenRouter、Qwen、DeepSeek 的模型槽位、模型解析、传输与实际接收方归因。
- 用户体验：Compose 聊天、抽屉、搜索、消息投影、Markdown、设置、主题／字体、会话生命周期和本地提醒。

### 未可声称的范围

- Google／Supabase、真实 Agent 工具、生态接入和 AI Hub 有接口或历史合同，但没有本轮真实远端验收。
- Android 是 `settings.gradle.kts` 唯一包含模块。仓库没有当前 Android CI 工作流。`desktop/` 含可独立测试和构建的 JavaScript/Tauri 2 spike，但其版本仍为 `0.0.1`、Tauri 产品版本为 `0.6.0-p6d-dev`，不属于根 Gradle，也没有本次发布或安装证据。
- `upload-gateway/` 是独立 Go 模块，不由根 Gradle 构建；尚无 Go 测试文件，也无部署、TLS、域名或真实上传验收。

## 3. 技术栈、配置与目录

| 层面 | 当前实现与依据 |
| --- | --- |
| Android | Kotlin、AGP 9.3.1、Kotlin/Compose 2.2.10、Compose Material 3、minSdk 26、target/compileSdk 36。 |
| 本地数据 | Room 2.8.4、稳定基线 Schema 56、`1→56` 连续迁移与 schema 导出。 |
| 生命周期 | Activity + Compose + ViewModel；普通发送由数据同步前台服务与持久化运行时状态承接。 |
| 网络／文件 | Provider transport、Android Keystore、App 私有文件、SAF、FileProvider、WorkManager、PDFBox Android。 |
| 测试 | JUnit 4、Robolectric、Room testing、host SQLite JDBC；按 ai/data/domain/ui/app 分组。 |
| Desktop spike | 原生 JavaScript UI + Node `node:test`；Tauri 2、Rust 2021、SQLite（rusqlite bundled）、AES-GCM 与本地文件 owner。 |
| 交换协议 | `protocol/` 的 v1/v2 exchange、sync schema、golden fixture 与 Node 验证脚本。 |
| 云端合同 | `supabase/` 保存 Google avatar function、SQL migration 与 P7-C 静态安全合同；没有本次远端部署证据。 |
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

desktop/                       JavaScript/Tauri 2 Desktop spike 与双层测试
protocol/                      v1/v2 exchange、sync schema、fixture 与 golden 验证
supabase/                      P7-C 静态合同、函数与数据库迁移
upload-gateway/                独立 Go 附件服务
docs/                          当前合同、交接、决策、历史证据
.agents/skills/                可重复开发流程
scripts/、delivery/            验证、交付与证据辅助脚本
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

- `NanfengAiDatabase` 稳定基线为 Schema 56，保存会话、项目、知识、Memory、导入任务／receipt、使用账本、诊断、Attempt、归因与恢复记录；`MIGRATION_54_55` 增加 ChatGPT ZIP provenance／附件归属结构，`MIGRATION_55_56` 增加内容无关的 `p6k_zip_asset_recovery_jobs`，两段都在 `AppContainer` 注册。
- `AndroidPrivateAttachmentStore` 保存私有附件；消息保存安全引用。预览、搜索、导入与外发各有独立 owner，不能绕过大小、MIME 与私有副本检查。
- JSON Knowledge、Markdown、PDF、网页文本、ChatGPT／Claude ZIP、南枫知识、v1 会话交换和 v2 工作区交换使用专属 parser／commit owner。v2 先严格预检再原子恢复，非空本机路径拒绝覆盖。

### UI 与设置

`ConversationWorkspace` 是会话／搜索主要 surface；设置由 `NanfengAiApp`、相关 ViewModel 与共享 `SettingsControlDimensions` 组成。会话合同约束抽屉、搜索、长按、文本投影、Composer、阅读控制和手势；设置合同约束层级、弹层、主题、开关和保存反馈。UI 数值不得从旧 P 阶段合同复制。

### Desktop、协议、Supabase 与附件网关

`desktop/` 是独立的 chat-first Desktop spike：JavaScript 层负责静态 shell 与本地命令边界，Tauri/Rust 层负责 SQLite、交换包、附件、同步与 Agent 账本等本机 owner。`protocol/` 通过 schema、fixture 与 golden hash 约束 Android／Desktop 交换语义。`supabase/` 当前只有函数、迁移和静态安全合同，不能推断线上项目已部署。`upload-gateway/` 只承担带授权、SHA-256、分片续传和短期签名 URL 的附件字节中转；其 README 明确要求 HTTPS、持久卷和高熵 token，仓库未提供生产基础设施。

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

ChatGPT 累积 ZIP 的稳定链路是：严格 ZIP inventory → provider source tree 解析 → source conversation/message provenance 去重 → `P6KChatGptZipAssetMapper` 只按官方 file ID 建立归属 → `AndroidP6KZipAssetRecoveryScheduler` 排入后台恢复 → `RoomP6KZipMappedAssetLinkOwner` 按会话 checkpoint 续跑并补入普通 Message Tree → 普通会话、搜索分组、预览、下载与分享复用同一附件 owner。无官方归属的 `822` 个候选保持未关联，不按时间、文件名或相邻消息猜配。

Desktop v2 交换通过 `protocol/` 的 schema/golden 与 Tauri 私有 SQLite owner预检、提交、重放和再导出；Android 与 Desktop 的实现可以共享协议语义，但构建、安装和真实文件选择器验收仍是两个独立验证层。

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

稳定基线有 123 个提交，起点为 2026-08-20 的 `e56d666 chore: establish Nanfeng AI baseline`。截至导入 checkpoint 的提交主题以 `docs`（31）、`feat`（14）、`fix`（12）和 P6/P11 专项为主，体现合同／证据先行再实现的节奏；`6d68ba7` 收口测试基线，`c1c9ae0` 收口 P1 可续跑附件恢复。

1. **签名与本地基础（8 月 20 日）**：release v2 签名来源、Android 基础构建、数据与 P6 交换链。
2. **导入、恢复与治理（8 月 20–23 日）**：严格 ZIP／JSON／工作区交换、Room 升级、生命周期与可访问性；同时形成 Desktop/Tauri spike、跨端 v1/v2 协议与 golden 验证，未形成当前生产发布证据。
3. **普通聊天执行链（8 月 23–24 日）**：`d61805c` 接入直接 OpenRouter 发送，`1ded3da` 汇总 Provider chat 与会话 shell；后续扩展到 Qwen／DeepSeek、Attempt、前台服务、归因和模型目录。
4. **体验与设置收口（8 月 25–27 日）**：会话视觉、Markdown、抽屉、搜索、上下阅读、模型设置、Memory／资料库、开关、弹层与生命周期操作持续修正。
5. **8 月 27 日 checkpoint**：`da20412` 收纳会话、设置与运行时上下文增量；它是历史冻结点，不是全绿基线。
6. **8 月 28 日导入链 checkpoint**：`a5afcc7`、`da20412` 之后继续完成真实 ChatGPT 累积 ZIP 去重、附件原生 Message Tree 挂载、Schema 55 与导入结果入口，最终由 `af218e1` 固化；正确性有实包测试证据，OPPO 仍未覆盖该包。
7. **8 月 28 日 JVM 基线恢复**：`6d68ba7` 依据当前合同更新 58 项过时合同／夹具，并把 Assistant 页脚金额格式函数移入独立纯 Kotlin owner，消除 1 项 Android 类初始化耦合；标准全量回到 0 failures，未改变 Provider、ZIP 归属、MIME、迁移或设备安全门。
8. **8 月 28 日 P1 可续跑恢复**：`c1c9ae0` 引入持久化恢复 job、WorkManager 调度、按会话 checkpoint、失败／重试状态和 Schema 56；标准 JVM 记录为 `819 / 0 / 3 / 0`，新包 `853/853` Room 链为 `skipped=0`。后续数字口径、三表模型与真机阶段属于用户明确排除的正在进行任务。

## 8. 测试、构建、部署与验证边界

| 层级 | 当前事实 |
| --- | --- |
| Android JVM 测试资产 | 稳定基线 223 个文件；P1 新增恢复 job 和 ViewModel 恢复契约。 |
| 稳定 Android JVM | P1 `:app:testDebugUnitTest` 验证记录为 `819 tests / 0 failures / 0 errors / 3 skipped`。`docs/P6K_TEST_TRIAGE.md` 的 59 项分类是修复前证据，已在文件顶部标为历史。为避免干扰用户明确排除的后续任务，本复盘没有再清理共享 build 目录独立重跑。 |
| 真实 ZIP opt-in 验收 | 全量命令的 3 个 skip 是未传环境变量的真包测试。`af218e1` 已留下旧／新包资产、附件 `853/853`、对话去重 `784` 的 `skipped=0, failures=0` 记录；P1 `c1c9ae0` 又单独复跑新包附件 Room 链为 `1/0/0/0`、`257.597s`。旧包合并与资产 inventory 未在 P1 后由本复盘独立重跑。 |
| 已通过的 Android 定向门 | 共享 file ID、结构尾部合法叶选择、Schema `54→55` 迁移契约、真实 ZIP 映射／Room 链／旧新包合并、当前运行时上下文入口合同通过；标准全量当前也为 0 failures。 |
| Lint／Release | `lintDebug` 为 `0 errors, 84 warnings, 13 hints`；`assembleRelease` 成功。 |
| Desktop JavaScript | 本次 `npm test` 为 `93 passed / 0 failed / 0 skipped`；`npm run lint`、`npm run typecheck`、`npm run build` 通过。构建输出是本地 static spike，不是发布包。 |
| Desktop Rust/Tauri | 本次 `cargo test --locked` 为 `97 passed / 0 failed`，另有 main/doc-tests 0 项；只证明本机 Rust owner 与跨端 fixture，不证明 macOS 安装、签名或 native picker 真机链。 |
| 协议 golden | `run-golden.mjs`、`run-v2-golden.mjs`、`run-sync-golden.mjs` 均通过并输出稳定 semantic/package hash。 |
| Go 网关 | 源码和 Dockerfile 已检查；仓库没有 `_test.go`。本机没有 `go` 命令，因此本次不能执行 `go test ./...` 或镜像构建。 |
| 永久禁区 | 不执行任何 `connected*AndroidTest`。视觉、真实 Provider、费用和账号路径不是 JVM 覆盖面。 |

`app/build.gradle.kts` 只接受完整环境变量或用户级 Gradle 属性提供正式签名；缺失即停止，不回退 Debug 签名。Release 产物为 `app/build/outputs/apk/release/南枫AI.apk`。

当前本地正式 APK 为 `66 / 0.3.0-p10j`，SHA-256 `b0d1008fde0bbbddaaad57d925e09ca067dbbb64407d9783904955a999481929`。本轮没有安装。OPPO 当前仍是 SHA-256 `d86b670e050da976aa07151928059b49a9e0936fd7422dbac5883ec683c7c390` 的旧诊断包，当时业务回读为 `0 个附件已恢复`。本地构建和签名验收不能替代最新真机 UI 与用户数据语义验收。

网关需要受控 HTTPS 域名、持久卷、高熵 token 与独立访问策略；Dockerfile 描述了镜像构建方式，但本次未实际构建。没有 Go 测试、当前 Go 工具链或部署证据时，它只能视为待验证的可部署组件。

## 9. 踩坑、修复与经验

- **静态 UI 契约失配**：大幅 Compose 收口后，旧源码锚点曾集中产生 58 项失败，其中 P6D 单类为 40 项。`6d68ba7` 依据当前合同更新夹具与稳定语义 owner 后已清零；教训是不能依赖曾经通过，也不能为测试变绿而恢复旧视觉。
- **Markdown 色值误判**：`#fff` 和转义符曾造成异常字号及控制符泄漏；展示层修复后不改写持久化正文。
- **一键到底反跳**：按末项定位会让长消息顶部对齐；现按可见阅读区推进并以物理边界停下，自动跟随和手动滚动分离。
- **开关尺寸叠加**：百分比多次推导导致失真；应以共享组件固定几何和语义，而不是入口各自计算。
- **ZIP 当前叶误判与静默降级**：文本 parser 的“最后可渲染节点”不一定是 source tree 叶节点；旧实现还会用空 Outcome 吞掉逐会话事务失败。现按官方路径选择合法后代叶，并单列 `failedConversationCount`，避免把失败伪装成成功。
- **真实包测试被跳过**：Gradle 全量未传旧／新 ZIP 环境变量时，opt-in 测试会合法 skip。必须检查 XML 的 `skipped=0`，不能只看任务成功；实包测试已单独完成。
- **文档漂移**：旧档案和 docs 首页曾写 Schema 45/54/55，旧档案还把 Attempt 列为待做；稳定基线代码是 Schema 56 且已有 Attempt。此次按稳定提交、schema 与测试记录更新入口。

## 10. 文档与代码冲突

| 冲突 | 当前结论 | 处理 |
| --- | --- | --- |
| docs 首页与旧档案写 Schema 45/54/55 | `c1c9ae0` 的 `NanfengAiDatabase` 为 Schema 56，并有 `MIGRATION_54_55`、`MIGRATION_55_56`、产品注册、迁移契约和 `56.json`。 | 以稳定提交与导出 schema 为准；排除后续未提交 Schema WIP。 |
| 旧档案称发送 Attempt 未实现 | `NormalChatSendAttempt.kt`、Room store、Executor 已实现状态、恢复与显式重试。 | 以当前代码为准；仍不把代码路径写成真实 Provider 成功。 |
| checkpoint 交接写 815 / 60，分类文档写 815 / 59 | 两者分别是 `af218e1` 提交时和入口文档同步后的真实快照；`6d68ba7` 为 815 / 0 / 3 / 0，P1 `c1c9ae0` 为 819 / 0 / 3 / 0。 | 60 与 59 保留为带时间的根因证据；稳定排程采用 P1 的 0 failures，并继续区分 opt-in。 |
| 旧档案称全量测试／Lint 已绿 | P1 标准 JVM 记录为 819 / 0 failures / 3 skipped / 0 errors；Lint 最近为 0 errors。 | 可以声称 P1 标准 JVM 无失败；不能把 3 个 skip 写成已执行，也不能外推到真机、Provider 或视觉。 |
| 旧档案把 Desktop 仅称为历史资料 | 当前仓库有 65 个 Desktop 跟踪文件，Node 93 项与 Rust 97 项本次通过。 | 正确描述为可测试的 spike/dev；因无当前安装、签名、发布与真实 native picker 证据，仍不能称为生产 Desktop。 |
| 早期 P3/P4 说未接发送／不自动进入上下文 | 当前 executor 与运行时上下文合同已有普通发送和相关检索 owner。 | 旧文档只保留历史证据，后续读取当前合同与交接顶部。 |
| 早期设备记录显示旧 hash 或未回读 | 当前交接顶部记录 code 66 的包级 hash 回读。 | 只采用最新记录；仍不代替视觉／服务验收。 |

## 11. 已知问题与后续路线

### 优先处理

1. **保持 Android JVM 门**：P1 标准全量已达 `failures=0, errors=0`，后续改动必须维持；3 个真实 ZIP opt-in 测试仍要在明确样本下分别确认 `skipped=0`，不能因普通全量成功而省略。
2. **真机 UI 手工验收**：按当前合同验证搜索横滑返回、长回复上下阅读、设置层级、浅／深色与折叠屏视口。
3. **真实 Provider 最小闭环**：在用户自有合法配置和非敏感输入下验证成功、认证／余额／限流／断网、取消、未知结果与显式重试，并核对实际接收方、来源和费用。

### 设计后再做

4. 继续拆分 `NormalChatOpenRouterExecutor`，但保留 Attempt、运行时和归因事实。
5. 为附件网关补 Go 单元／集成测试、健康检查、删除回收和 TLS 运维证据；此前不默认启用外部附件路径。
6. P7–P10 云同步、Agent、生态与 Hub scaffolding 各自需要用户授权、真实目标、安全与回滚合同，不能因接口存在而上线。
7. 压缩历史文档中的“当前”歧义；新变更只进入当前合同、当前交接和决策日志。
8. 为 Desktop 补真实 macOS bundle／签名／native picker 验收，为 Supabase 补目标项目与远端部署回读；在此之前保持 spike／未部署状态。
9. **排除项**：本次不审计、不修改、不固化同仓库正在进行的 P2 及后续任务；其未提交代码、Schema、测试与 UI 数字只能在各自 checkpoint 后另行同步。

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
| ChatGPT ZIP 导入 | `domain/P6KChatGptZipAssetMapper.kt`；`data/P6KZipAssetRecoveryScheduling.kt`；`data/local/RoomP6KZipMappedAssetLinkOwner.kt`；`docs/P6K_CHATGPT_CLAUDE_ZIP_IMPORT_ADOPTION_CONTRACT.md` |
| Desktop 与协议 | `desktop/src/`；`desktop/src-tauri/src/`；`desktop/tests/`；`protocol/` |
| 云端合同 | `supabase/functions/google-avatar/`；`supabase/migrations/`；`supabase/p7c_static_contract.sql` |
| 附件网关 | `upload-gateway/main.go`；`upload-gateway/README.md` |

本次复盘只更新文档与项目级流程文件，不修改业务代码，不涉及数据迁移、APK、远端部署或设备写入。若需撤销，只恢复本次列出的文档／流程文件；不要覆盖用户或并行任务的其他工作树改动。
