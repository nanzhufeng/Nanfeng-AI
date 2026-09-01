# 南枫 AI 完整开发档案

> 复盘对象：南枫 AI 手机端（Android）。归档复盘日期：2026-08-31。
> 本文主体归档基线：`09f519f6f96e195583a04a03d689d7cbabeaeb64`（`docs: refresh current master control gate`）；代码冻结点：`b7e1c2f`；测试／合同收口点：`1f7f356`。
> 本档案依据当前跟踪文件清单、源码结构扫描、关键 owner 全文／定向读取、全部 JVM 测试结果、正式构建结果和完整 Git 提交主题生成。历史计划、截图、旧 APK 与旧测试数字只作为发生时证据；与当前代码或本轮验证冲突时，以代码和本轮验证为准。

## 0. 2026-09-01 正式增量沉淀

- **当前冻结点：** `242ed1ec82002aabbad7f923f447f134ac84c867` 是本轮 Android、Desktop、网关、Room schema 64／65、测试和核心新增文件的本地代码 checkpoint；此前 `09f519f` 主体复盘继续作为 2026-08-31 的历史结构快照，不再承担当前版本、Schema、测试数或设备状态。
- **Android 新增长期事实：** 对话风格由“设置里当前选定值”提供全局默认，当前会话只保存可缺省的显式覆盖；覆盖高于设置且只影响后续回答。完整 Assistant 回复随既有归因冻结生成时风格和实际联网结果，只有官方联网路由返回可验证 HTTP(S) 来源才能记为已实际联网。Composer 与回复页脚的短模型名只是显示投影，目录全名、路由和历史归因不被改写。
- **Android 数据演进：** Schema 64 引入不含正文的 ZIP 永久身份账本与用户删除墓碑；Schema 65 为现有回答归因增加可空风格／联网事实。迁移连续、旧记录保持未知，不从当前设置倒推历史。历史资料自动整理的冷进程调度由惰性 WorkManager 配置和持久运行记录接管。
- **最终验证与设备：** Android JVM `1082 / 0 failures / 0 errors / 3 skipped`，Lint 0 errors、Release 构建和 v2／v3 验签通过；最终 APK SHA-256 `90a019a1039f4e51ecb5d370d3740bb296121395fd4b066f1ecef2e91dd6859f`。OPPO PKH120 同签名覆盖后首次安装时间与 CE／DE inode 均未变化，回读 APK 哈希一致且冷启动无 FATAL／迁移异常。
- **Desktop 与网关：** Desktop 已形成独立 Tauri owner 的对话、模型设置、搜索、转写、提醒、历史资料、账号同步和本机备份路径；Node `141/141`、Rust `165/165`、lint、typecheck、协议 golden、静态 build 和 macOS bundle 通过。`.app` 仍是 ad-hoc，不是 Developer ID／公证包。Go 网关 `go test -count=1 ./...` 通过，但生产部署仍未验证。
- **证据边界：** 本轮没有用真实 Provider／Google／Supabase 或真实 ZIP opt-in 扩大声明；已完成且源码未变化的模拟器／视觉流程未重复执行。下面第 1–12 节保留 2026-08-31 复盘正文，出现的旧 HEAD、Schema、测试数和风险只按历史日期读取。

## 0.1 2026-08-31 checkpoint 增量（历史）

- **当前覆盖事实：** 归档基线之后，草稿附件与对话附件统一复用本地预览投影；Grok 退役选择保留历史归因但在请求边界以 `MODEL_NOT_FOUND` 失败关闭；图片查看恢复完整图缩放和按真实溢出的平移。这些行为的唯一现行正文仍是当前会话／设置／运行时合同及领域 Provider 合同。
- **最终验证：** `:app:testDebugUnitTest` 为 `1042 tests / 0 failures / 3 skipped`；`:app:assembleRelease`（含 `lintVitalRelease`）通过。Release APK 为 `28,017,564` bytes，SHA-256 `8279335eef23b7aff39fbf08ff367e2a7d3ec8b0325f04f5d0e3062aabe95593`，正式证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **验收边界：** 本次未运行任何 `connected*AndroidTest`，未再安装或操作 OPPO，也未使用用户 Key 请求真实 Provider。历史 OPPO 同签名覆盖与逐字节回读只证明安装闭环；真机图片手势和真实 Provider 回复仍需显式人工验收。

## 1. 复盘范围、方法与结论

### 1.1 检查范围

- Git 当前跟踪 `1084` 个文件；按顶层计数：`app/ 651`、`docs/ 296`、`desktop/ 65`、`protocol/ 16`、`scripts/ 10`、`artwork/ 10`、`.agents/ 10`、`delivery/ 6`、`supabase/ 5`、`upload-gateway/ 4`，其余为 Gradle、仓库规则与根配置。
- Android 主源码：`app/src/main/java` 下 `296` 个 Kotlin 文件，约 `68,775` 行；包分布为 `domain 118`、`data 98`、`ui 55`、`ai 21`、`app 2`、`background 1` 和根 Activity。
- Android JVM 测试目录：`258` 个文件，其中 `255` 个 Kotlin 文件、约 `24,670` 行；`app/src/androidTest` 当前没有测试文件。
- Room：`63` 份导出 Schema、当前 Schema 63、`120` 个 `@Entity`、`35` 个 DAO 接口、`62` 段连续前向迁移（`1→2` 至 `62→63`）。
- Git：当前 `main` 有 `138` 个提交，历史从 2026-08-20 的 `e56d666` 到 2026-08-30 的 `09f519f`；无 tag、无配置 upstream。
- 手机端是本档案主体。`desktop/`、`protocol/`、`supabase/` 与 `upload-gateway/` 只检查其和 Android 的边界，不把它们写成已经同步完成的手机功能。

### 1.2 复盘方式

1. 先读取 `AGENTS.md`、总控蓝图、完成审计、当前交接、三份 Android 当前合同和领域合同路由。
2. 对全部跟踪文件、源码／测试目录、扩展名、体量、Room entity／DAO／migration、Gradle／Manifest、密钥特征、危险 API、TODO 与 Git 历史做结构化扫描。
3. 对 Activity、Compose 根、`AppContainer`、Room 数据库、普通聊天执行器、Provider adapters、附件桥、导入恢复、搜索、费用、同步与设置 owner 定向核对。
4. 本轮实际运行完整 JVM、`lintVitalRelease`、`assembleRelease`、APK 身份与证书验证；未运行任何 `connected*AndroidTest`，未连接、安装或写入主设备，未调用真实 Provider。

### 1.3 总结论

南枫 AI 手机端已经形成可正式签名构建的单模块 Android 应用。产品由“本机数据 owner”和“联网模型能力”并列组成：会话、附件、知识、Memory、导入记录、调用账本和恢复状态由本机 Room／私有文件 owner 保存；用户点击发送后，准确提交的正文、附件或解析材料才交给界面所示 Provider／模型。它不是纯离线工具，也不应恢复旧的普通聊天逐条二次确认。

当前代码覆盖多 Provider 对话、会话树与恢复、统一附件解析、ChatGPT／Claude ZIP 导入、南枫转写、搜索、文件预览／分享／下载、费用账本、项目／知识／Memory、Google／Supabase 账号同步基座、提醒／计划监控与本机数据管理。归档基线当时保留的 4 项静态断言漂移已在上方 checkpoint 增量中收口为完整 JVM 通过；真实 ZIP opt-in、真机视觉、真实 Provider／账单及远端同步仍须按各自授权和证据层验收，不能由本次构建代替。

## 2. 项目目标与产品边界

### 2.1 当前目标

- 提供连续、多模型、可恢复、可审计的 Android 对话体验。
- 让对话、文件、知识、Memory、项目、导入资料和调用记录进入同一个可搜索、可迁移的个人工作体系。
- 在 Direct、Compare、Auto 三种模型路径中保存实际 Provider、实际模型、Attempt、Token 与费用事实。
- 对图片、PDF、视频、Markdown、Office 等材料统一使用应用内 owner 解析、预览和引用；最终回答仍由用户选择的模型完成。
- 对外发、凭据、同步、导入、恢复和 Agent authority 保留清楚的接收方、失败、回滚和数据边界。

### 2.2 不能从当前仓库声称的结果

- 代码存在不等于真实 Provider、实时搜索、OCR、Google 登录、Supabase 同步或费用扣款已经在本轮成功。
- `desktop/` 是独立 JavaScript/Tauri 实现；Android 新增能力不会自动同步到 Desktop。
- `supabase/` 是函数、迁移和静态合同，不证明目标线上项目已部署。
- `upload-gateway/` 是独立 Go 组件；没有 `_test.go` 和本轮部署证据。
- `androidTest` 为空；JVM／Robolectric 合同不能替代真机手势、折叠屏、系统选择器、媒体播放和视觉验收。

## 3. 架构与技术栈

### 3.1 构建与平台

| 项目 | 当前事实 | 依据 |
| --- | --- | --- |
| 工程 | Gradle 单模块 `:app`，根项目名 `Nanfeng AI` | `settings.gradle.kts` |
| Android | applicationId `com.nanzhufeng.ai`，minSdk 26，compile/targetSdk 36 | `app/build.gradle.kts`、APK badging |
| 版本 | code 66，`0.3.0-p10j` | `app/build.gradle.kts`、本轮 APK |
| UI | Kotlin、Jetpack Compose、Material 3、Compose BOM `2026.06.01` | `app/build.gradle.kts` |
| 生命周期 | Activity、ViewModel、Compose、前台 Service、WorkManager | Activity、Manifest、`background/` |
| 数据 | Room 2.8.4、App 私有文件、SAF、FileProvider、Android Keystore／私有配置 | Gradle、`data/`、Manifest |
| 认证／同步 | Android Credentials、Google ID、Supabase 配置与本机同步 owner | Gradle、`data/`、`supabase/` |
| 文件 | PDFBox Android、本机文本抽取、统一附件桥、内部预览／分享 owner | Gradle、附件与 OCR owner |
| 测试 | JUnit 4、Robolectric 4.16.1、Room testing、host SQLite JDBC | `app/build.gradle.kts`、`app/src/test` |

Release 为非 Debug，当前 `isMinifyEnabled=false`。任何可安装构建都要求正式签名配置完整；配置只从完整环境变量或用户级 `~/.gradle/gradle.properties` 的项目命名空间读取，缺失即失败，不读取 Keychain、不生成替代签名。

### 3.2 目录与职责

```text
app/src/main/java/com/nanzhufeng/ai/
├── NanfengAiActivity.kt       Android 入口、系统回调、根 ViewModel 装配
├── app/                       AppContainer 与跨域组合根
├── ui/                        Compose 页面、弹层、导航、状态投影、ViewModel
├── domain/                    领域模型、UseCase、策略、协议、状态机和端口
├── data/                      Room、私有文件、SAF、凭据、同步和 Android adapter
├── ai/                        Provider adapter、传输、流式解码、发送执行与附件桥
└── background/                普通聊天前台生成服务

app/src/main/res/              主题、图标、FileProvider、备份与资源
app/src/main/assets/           模型档案等只读资产
app/src/test/                  JVM、Robolectric、Room、静态合同与 opt-in 验收
app/schemas/                   Room 1–63 导出 schema
docs/                          当前合同、决策、交接、历史证据与开发档案
.agents/skills/                项目级可复用开发流程
desktop/ protocol/             独立 Desktop 与跨端交换协议
supabase/ upload-gateway/      远端同步合同与独立附件网关
```

### 3.3 依赖方向与组合方式

```text
NanfengAiActivity
  → AppContainer（手工依赖注入／composition root）
  → NanfengAiApp / ConversationWorkspace / ViewModels
  → domain UseCase、owner、policy、port
  → data / ai / background adapter
  → Room、私有文件、WorkManager、系统 API、Provider HTTP
```

项目没有使用 Hilt/Koin；`AppContainer` 手工创建数据库、repositories、导入 owner、模型 resolver、Provider adapters、上下文 broker、账本、同步、计划任务与 ViewModel 依赖。优点是依赖边界显式；代价是 `AppContainer.kt` 已达 874 行，组合根继续扩张会降低可审查性。

## 4. 核心模块

### 4.1 会话、消息树与生命周期

- `RoomConversationRepository` 持有会话、Message Tree、内容块、草稿、附件引用、分支、归档／删除／置顶／收藏和搜索投影。
- 会话不是扁平文本列表；用户消息、助手消息、分支当前叶、编辑 lineage 和 Compare branch 都有独立事实。
- “待看”只保存会话 ID 与设置时间，在置顶／最近各组内排序；左侧主题色圆点在打开会话后消失，可反复设置。
- `ConversationWorkspace.kt` 是抽屉、搜索、正文、Composer、文件、来源、手势和大量弹层的主 surface，目前 10,842 行，是最大的单文件维护债务。

### 4.2 普通聊天执行、Attempt 与恢复

`NormalChatOpenRouterExecutor` 负责普通发送的总编排：提交草稿、解析选择、准备附件、组装上下文、解析实际模型、调用 adapter、流式更新、保存回复、归因、Usage、错误、取消和重试。

每次外部发送由持久化 `NormalChatSendAttempt` 记录。恢复／重试保留原 Provider、model 和 idempotency key；`UNKNOWN` 结果不得静默重发或改投。前台服务只接收不含正文的会话标识和动作，实际正文从 owner 读取。

### 4.3 模型、Provider 与路由

- 当前 Provider 配置面包含 OpenRouter、DeepSeek、智谱、Qwen；设置顺序为 OpenRouter、DeepSeek、智谱、Qwen。
- `model_profiles.json` 有 7 个原生直连档案：Qwen3.7-Plus、Qwen3.8-Max、Qwen3.6 Flash、DeepSeek V4 Pro、DeepSeek V4 Flash、GLM-5.3、GLM-5.3 Flash。
- OpenRouter 具体模型由注册表／中心目录解析，不能把 `model_profiles.json` 误认为全部可选模型清单。
- Direct 使用用户明确选择，Compare 建立独立 branch，Auto 只在用户未指定具体目标时路由。历史消息页脚与费用必须读取实际归因，不能从当前选择反推。
- Qwen3.8-Max 的目录上限与单次产品预算是不同概念：模型档案保存能力上限，执行策略另限制推理档位、单次输出和生命周期；文档不得把两者混写。

### 4.4 统一附件、预览与南枫转写

`UniversalChatAttachmentBridge` 先判断最终模型是否原生接受材料；不原生支持时，本机读取文本／Office／PDF 文本层，扫描 PDF 或图片可交给 GLM-OCR，图片／视频材料可由 Qwen3.7-Plus生成忠实 Markdown 投影，再交给用户选择的最终模型。桥接接收方与调用费用独立记账；桥不可用时在最终外发前失败，不能伪造“所有模型原生全格式”。

私有附件字节由 `AndroidPrivateAttachmentStore` 管理，Room 保存 asset 与 occurrence 引用。会话、搜索、南枫转写和导入资料复用统一的文本／文件入口、内部预览、复制、下载、分享和引用计数；网页来源仍通过系统浏览器打开，不能与本地文件内部预览混为一谈。

南枫转写由 GLM-OCR 任务、调度器、原始文件和生成 Markdown 组成；生成文件纳入统一搜索时间线、存储统计和费用账本，不再作为孤立子系统。用户点击开始即授权该文件交给界面标明的 OCR 服务，不保留旧勾选二次确认。

### 4.5 搜索、导入与恢复

- 搜索覆盖正文、图片、视频、音频和文件；ChatGPT 导入消息正文进入同一全文索引，导入／南枫转写文件按正常时间线和统一大小／时间排序。
- ChatGPT／Claude ZIP 使用严格 inventory、source tree、source identity、provenance、receipt 与专属 commit owner。无法由官方字段归属的附件不能用文件名、时间或邻近消息猜配。
- ZIP 恢复任务持久化状态、进度、失败类型和按会话 checkpoint，由 WorkManager／后台 owner 续跑，不绑定页面生命周期。
- Markdown、JSON Knowledge、PDF 文本、网页文本、南枫知识、v1 会话交换和 v2 工作区交换各用自己的 parser／owner；文件格式不能互相代替。

### 4.6 知识、Memory、项目与上下文

项目、知识、知识关系、Memory、版本、标签、来源与 scope 均有 Room owner。`LocalContextBroker` 按当前会话／项目范围、开关和 token 预算选择历史、ACTIVE Memory 与 ACTIVE Knowledge；附件字节、凭据、运行日志和同级分支不直接进入上下文。普通聊天的准确外发语义以 `ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md` 为准。

### 4.7 费用、诊断、提醒与同步

- Invocation、Provider attempt、generation、validation、usage、模型归因和安全诊断分表保存；真实 Provider 回传优先，本机估算保留价目版本与估算标记。
- 会话、标题整理、历史资料整理和南枫转写分别归类；费用与用量页不能把不同任务混成同一行事实。
- 定时监控、提醒草案和通知开关有独立 owner；关闭通知不等于暂停任务或删除结果。
- Google／Supabase 代码包含账号、元数据、加密同步／恢复基座，但真实登录、远端读写、冲突恢复仍是独立验收门。

## 5. 关键数据流

### 5.1 普通发送

```text
Composer 提交准确草稿
  → Room 写用户消息与 Attempt
  → LocalContextBroker 选择最小上下文
  → Model Resolver 确定实际 Provider/model
  → UniversalChatAttachmentBridge 标准化材料
  → Provider adapter/transport 流式执行
  → Room 写 runtime、助手消息、归因、Usage、费用和安全错误
  → ViewModel/Compose 投影完成、失败、取消或可恢复状态
```

### 5.2 文件与转写

```text
系统选择器
  → App 私有副本 + MIME/大小/完整性校验
  → 统一 asset identity 与 occurrence 引用
  ├─ 内部预览／复制／下载／分享
  ├─ 搜索时间线与存储统计
  ├─ 发送时附件桥
  └─ GLM-OCR → Markdown 文本文件 → 同一文件体系
```

### 5.3 ZIP 导入与恢复

```text
用户选择 ZIP
  → 私有暂存与严格预检
  → Provider 专属 parser / source tree
  → provenance 去重与 Room 事务
  → 官方 file ID 映射
  → 持久化后台恢复 job + 会话 checkpoint
  → 普通消息树／搜索／预览 owner
```

### 5.4 本机存储统计

```text
活动消息／草稿／知识／临时会话／上传／转写／未完成 ZIP 引用
  → 按唯一私有 asset 去重
  → 读取当前物理文件长度
  → 分类统计
无活动 owner 但仍存在的文件 → 单列待清理残留，仍计入总量
```

## 6. 关键决策及原因

| 决策 | 原因 | 主要依据 |
| --- | --- | --- |
| 本机数据与联网能力并列 | 产品既要保有本机资产，也要真实使用外部模型；不能把联网写成伪功能或把本机保护误写为纯离线定位 | 总控蓝图、Room、Provider executor |
| 点击普通发送即授权当前接收方 | 附件选择／预览阶段不外发；发送时不再增加重复确认，同时禁止静默换 Provider | 总控蓝图、Provider adapter、Attempt |
| 外部动作持久化 Attempt／receipt | 进程中断和网络超时会造成未知结果；UI loading 不能证明请求事实 | Attempt、runtime、import receipt |
| 实际 Provider/model/Usage 单独归因 | Auto、目录和用户选择会变化，历史账本不能由当前 UI 反推 | attribution、usage ledger |
| 附件能力用桥接而非伪造原生能力 | 文本模型能力不同；必须保留最终模型选择并披露材料接收方与费用 | `UniversalChatAttachmentBridge` |
| 导入按格式隔离并 fail-closed | JSON、ZIP、v1、v2 的身份、原子性和回滚规则不同 | import owners、P6-K 合同 |
| 长任务持久化后台续跑 | 页面生命周期短于 ZIP 恢复／OCR／同步任务 | WorkManager、recovery job |
| 存储按活动 owner 与当前物理字节 | receipt 的历史大小不等于文件仍存在；共享字节不能重复计数 | `AndroidPrivacyDataManager`、决策日志 |
| 当前行为只有少数现行合同 | 296 份 docs 中含大量阶段证据；旧“当前”措辞容易反向覆盖代码 | 总控门、当前合同、交接顶部 |
| 主设备只做验签后的保数据正式覆盖 | OPPO 保存用户数据，Debug／仪器包、卸载或清数据风险不可接受 | 根规则、签名合同、历史设备证据 |

## 7. 开发过程与 Git 历史

当前 138 个提交集中在 2026-08-20 至 2026-08-30，主要前缀为 `docs`、`fix`、`feat`、`test` 和 checkpoint。完整提交主题已检查，关键演进如下：

1. **8 月 20 日：正式签名与基线接管。** `e56d666` 建立仓库基线，随后固定 release v2 凭据来源、版本与 OPPO 保数据覆盖门。
2. **8 月 20–23 日：导入、交换与本机领域基座。** Room、知识、Memory、项目、会话交换、ZIP、Desktop/Tauri、协议 golden 和安全 owner 逐步建立。
3. **8 月 23–24 日：普通聊天真实执行链。** `d61805c` 接入普通聊天 OpenRouter，`1ded3da` 形成 Provider chat 与会话 shell，之后扩展 Attempt、前台服务、归因与恢复。
4. **8 月 25–27 日：会话、搜索、设置与上下文收口。** Compose 视觉、抽屉、Markdown、阅读控制、模型设置、文件、Memory／资料库和生命周期持续迭代；`da20412` 是阶段 checkpoint。
5. **8 月 28 日：真实 ZIP 与统一媒体。** `af218e1`、`6d68ba7`、`c1c9ae0` 收口导入、完整 JVM 和可续跑恢复；`2fec04c` 前后把导入媒体、搜索、预览和文件 ownership 统一。
6. **8 月 30 日：多模型、附件桥、南枫转写、费用、存储和会话交互整合。** `b7e1c2f` 冻结 216 个文件的集成基线，`1f7f356` 把过时合同失败从 12 项收敛到 4 项已知基线，`09f519f` 更新总控读取门。

仓库无 tag、无 upstream，说明当前 checkpoint 主要是本地可追溯点，不是远端 Release 版本。历史提交中的设备 hash、Schema、测试数量和“下一入口”必须带日期读取。

## 8. 测试、构建与部署

### 8.1 本轮自动验证（执行于 2026-08-30，HEAD `09f519f`）

| 层级 | 命令／结果 | 能证明什么 | 不能证明什么 |
| --- | --- | --- | --- |
| 完整 JVM | `testDebugUnitTest`：`1013 tests / 4 failed / 3 skipped` | 当前代码可编译；绝大多数 domain/data/Room/Robolectric/静态合同执行 | 不能写成全绿；不能证明真机、视觉或真实服务 |
| 已知失败 | PDF renderer cache；统一 Dialog scrim；Dialog 内向边缘手势；设置统一画布 | 失败集与 checkpoint 一致，新增失败为 0 | 四项仍未完成，不能忽略为通过 |
| opt-in skip | 新 ZIP 完整附件、旧→新 ZIP 合并、官方资产映射各 1 项 | 默认套件正确阻止未显式提供真实包 | 本轮没有执行真实 ZIP 数据门 |
| Release Lint | `lintVitalRelease` 通过 | Release vital lint 无阻断错误 | 不等于 `lintDebug` 全量警告清零 |
| 正式构建 | `assembleRelease` 通过 | 正式签名 APK 可生成 | 不等于安装、启动、视觉或 Provider 成功 |
| APK 验证 | v2/v3 签名通过、单一签名者、非 Debug 构建 | 候选身份和证书可核对 | 本轮没有覆盖设备 |

本轮 APK：`app/build/outputs/apk/release/南枫AI.apk`，`27,984,647` bytes，SHA-256 `426cd63d2f16a5fc69bd76d5a8415dfa976e93795541967e51e4668ef3106c7b`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。该候选未安装。当前交接中最近一次已记录的 OPPO 覆盖包是另一字节 hash；不能把本轮候选写成设备现装包。

### 8.2 部署方式与门禁

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew --no-daemon --max-workers=1 lintVitalRelease assembleRelease
```

- 正式签名：优先完整 `NANFENG_AI_RELEASE_V2_*` 环境变量；备用为用户级 `nanfengAi.releaseV2.*` Gradle 属性。
- 输出：`app/build/outputs/apk/release/南枫AI.apk`。
- 主设备：只有用户明确授权后，先只读核对包名、版本、非 Debug、v2/v3 证书和现装数据指纹，再执行单次同签名 `pm install -r --user 0`，安装后读回 APK 身份。
- 永久禁止：任何 `connected*AndroidTest`、卸载、清数据、Debug／仪器包、数据库注入或读取用户私有业务数据。
- 本轮没有设备、远端服务、生产网关或商店部署动作。

## 9. 踩坑与修复

- **旧静态合同反向锁定旧 UI。** 集成 checkpoint 首次完整 JVM 有 12 项失败，其中 8 项是已完成产品行为与旧源码锚点冲突；仅更新合同后变为当前 4 项历史失败。教训：先按现行合同分类，不为“变绿”恢复旧产品。
- **ZIP 当前叶误判。** “最后可渲染消息”不一定是 source tree 合法叶；修复为沿官方路径选择合法后代叶并显式统计失败，避免空 Outcome 冒充成功。
- **长恢复绑定页面。** ZIP 资产恢复曾依赖 ViewModel 协程；现迁到持久化 job + WorkManager + 会话 checkpoint。
- **共享附件身份混淆。** 同一 file ID 可在多条消息出现；字节 asset 去重、occurrence 单独挂载，删除只在最后真实引用消失后清字节。
- **存储统计使用历史 receipt。** 删除媒体后旧 byteCount 仍被计入；现改为活动 owner 去重并读取当前物理长度，孤儿文件单列残留。
- **模型格式能力被误解。** “默认支持所有文件”不能通过把 capability 全设 true 实现；现用统一附件桥保持最终模型选择，并分开记录 OCR／材料模型接收方与费用。
- **Qwen3.8-Max 时间与费用过宽。** 模型能力上限曾被当成单次请求预算；现由执行策略单独限制 reasoning effort、输出预算与总生命周期，历史 Usage 不反推未返回的 reasoning token。
- **文件入口割裂。** 南枫转写、ZIP 文件、搜索附件曾有不同点击／排序／预览逻辑；当前代码和合同收敛到统一文件 owner，网页链接仍明确属于浏览器路径。
- **文档漂移。** 旧档案叠加 `c1c9ae0/Schema 56/819 tests` 与当前增量，容易误读。本次改为单一现行基线，并保留冲突表而不是继续顶部打补丁。

## 10. 文档、代码与验证冲突

| 冲突 | 实际结论 | 处理方式 |
| --- | --- | --- |
| 旧档案正文以 `c1c9ae0`、Schema 56、819 tests 为稳定现状 | 当前 HEAD 为 `09f519f`，Room 63，本轮 1013/4/3 | 本档案整体重写；旧数字只留开发历程 |
| 旧档案／旧 AGENTS 把产品概括为“本地优先” | 总控现行门要求本机数据与联网能力并列；代码同时有本机 owner 与真实 Provider executor | AGENTS 改为并列能力和准确发送授权边界 |
| 总控早期文字要求普通发送逐次确认 | 2026-08-23 覆盖门和当前代码规定点击发送即授权当前接收方 | 旧要求降为历史，不恢复二次确认 |
| Manifest 顶部注释仍称 Provider inference 被 `OpenRouterEgressPolicy.Disabled` 阻止 | 当前普通聊天有 `NormalChatOpenRouterExecutor` 与四类 Provider adapter；该注释描述旧 P2 路径，不是当前普通聊天事实 | 记录为待清理代码注释；本轮不改业务文件 |
| 早期自动标题只由 Qwen 写入 | 当前决策与代码路由为 DeepSeek V4 Flash → GLM-5.3 Flash → Qwen3.6 Flash | 以当前路由 owner 和 8 月 30 日决策为准 |
| `model_profiles.json` 中 Qwen3.8-Max 能力上限为 131072，而决策写单次 16384 | 一个是目录能力上限，一个是产品执行预算 | 文档分开描述，不修改档案上限伪造能力 |
| 历史交接含多个不同 APK hash | Git 信息进入产物后重建字节可变化 | 只把本轮 hash写为未安装候选；设备事实读取最新独立记录 |
| 旧文档有“全部测试已绿” | 当前本轮完整 JVM 仍有 4 failure、3 skip | 以本轮 XML 和 Gradle 结果为准 |

## 11. 已知问题与后续路线

### 11.1 已确认问题

1. 完整 JVM 仍有 4 项合同失败：PDF renderer cache、Dialog scrim、Dialog 内向边缘手势、设置画布。
2. 3 个真实 ZIP opt-in 验收本轮未执行；历史成功记录不能替代当前 HEAD 复跑。
3. `androidTest` 为空；折叠屏、系统返回、文件选择器、媒体播放、长文本和暗色皮肤需要隔离设备／人工门。
4. 本轮没有真实 Provider 延迟、reasoning token、费用、联网搜索、OCR、账号同步或错误恢复验收。
5. `ConversationWorkspace.kt` 10,842 行、`NanfengAiApp.kt` 3,596 行、`NanfengAiDatabase.kt` 3,429 行、`ConversationFoundationViewModel.kt` 2,231 行；边界清楚但实现集中，审查与回归成本高。
6. Release 未启用 minify；产物体积和混淆／裁剪策略尚未形成独立发布决策。
7. `upload-gateway` 没有 Go 测试与生产部署回读；Supabase 也没有本轮远端状态证据。
8. 296 份历史文档仍含大量旧“当前”措辞；现行读取门能裁决，但继续维护的认知成本高。
9. 仓库无 tag、无 upstream；当前 checkpoint 不是远端正式发布记录。

### 11.2 建议路线

1. 不改产品行为地逐项修复并回归当前 4 项失败；每项先确认合同是否仍现行。
2. 在显式真实 ZIP 路径下复跑 3 个 opt-in 测试，确认 XML `skipped=0`，不输出正文或文件内容。
3. 在隔离模拟器／专用验收环境做当前合同的浅深色、折叠屏、返回栈、文件预览和长回复验证；永久不运行 connected test。
4. 用户授权后再以本轮之后的稳定同签名 Release 覆盖 OPPO，独立核对包级身份、数据保留和人工交互。
5. 用用户合法配置做最小真实 Provider 矩阵：成功、认证失败、余额／限流、断网、取消、UNKNOWN、显式重试、附件桥、Token 和账单归因。
6. 按 owner 拆分巨型 Compose／ViewModel／组合根文件；先建立行为合同与性能基线，不以删功能或改布局换取体积下降。
7. 为 Go 网关、远端同步和 Desktop 分别建立自己的测试／部署门，不能由 Android 构建替代。
8. 逐步将旧 P 阶段文档标为 Historical／Deprecated；现行规则只保留路由，不复制到多个入口。

## 12. 证据索引

| 主题 | 当前证据 |
| --- | --- |
| 总控与当前边界 | `docs/MASTER_DEVELOPMENT_BLUEPRINT.md`、`docs/MASTER_PLAN_COMPLETION_AUDIT_20260816.md`、`docs/CURRENT_HANDOFF.md` |
| 会话／设置／上下文 | `docs/ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md`、`docs/ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md`、`docs/ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md` |
| 入口与组合 | `NanfengAiActivity.kt`、`app/AppContainer.kt`、`ui/NanfengAiApp.kt` |
| 会话与发送 | `RoomConversationRepository.kt`、`NormalChatOpenRouterExecutor.kt`、`NormalChatSendAttempt.kt` |
| 模型与附件 | `ModelService.kt`、`model_profiles.json`、`ChatProviderAdapters.kt`、`UniversalChatAttachmentBridge.kt` |
| Room 与迁移 | `data/local/NanfengAiDatabase.kt`、`AppContainer.kt`、`app/schemas/1.json`–`63.json` |
| 导入与恢复 | `P6KThirdPartyZipInventory.kt`、P6-K Room owners、`P6K_CHATGPT_CLAUDE_ZIP_IMPORT_ADOPTION_CONTRACT.md` |
| OCR／文件 | `GlmOcr.kt`、`GlmOcrWorkspace.kt`、`AndroidPrivateAttachmentStore.kt`、`GLM_OCR_DOCUMENT_MARKDOWN_CONTRACT.md` |
| 搜索与存储 | `ConversationWorkspace.kt`、`RoomConversationRepository.kt`、`AndroidPrivacyDataManager.kt`、`decision-log.md` |
| 费用与归因 | `ConversationCostEstimator.kt`、Usage/Invocation Room owners、`AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md` |
| 测试 | `app/src/test/`、`app/build/test-results/testDebugUnitTest/`（本轮生成，未跟踪） |
| 构建与签名 | `app/build.gradle.kts`、`AndroidManifest.xml`、`docs/ANDROID_FORMAL_SIGNING.md` |
| Git | `e56d666`、`1ded3da`、`af218e1`、`6d68ba7`、`c1c9ae0`、`2fec04c`、`b7e1c2f`、`1f7f356`、`09f519f` |

本轮只修改文档、根项目规则和 `.agents/skills/`，没有修改 Android／Desktop／网关业务代码、Room Schema、配置值或测试行为。
