# 南枫 AI P1 任务合同

> 日期：2026-08-12  
> 状态：已完成（本地工程验证）  
> 上位依据：`MASTER_DEVELOPMENT_BLUEPRINT.md` 的 P1 与 `implementation-plan.md` 阶段 1

## 唯一目标

建立可编译、可单测的 Android Kotlin/Compose 最小工程，使捕获、外发确认、AI 候选、知识保存与调用记录拥有唯一领域合同；在不接入真实 Provider 的条件下，使用 Mock Provider 和内存 Repository 验证关键状态门禁。

## 本轮概念与唯一所有者

| 概念 | 唯一所有者 | P1 公开入口 |
|---|---|---|
| Capture Draft 与 Source Evidence | Capture Domain | `CreateCaptureDraftUseCase` |
| 一次外发确认 | AI Task Application | `RunAiTaskUseCase` |
| Generated Candidate | AI Task Domain | `AiTaskRunner` 的结构化结果 |
| Knowledge Item | Knowledge Application | `SaveKnowledgeItemUseCase` |
| Invocation | AI Task Domain | `AiTaskRunner` 的结构化结果 |

## 入口矩阵

| 入口/消费者 | P1 状态 | 说明 | 最小验证 |
|---|---|---|---|
| 手工文本 | 受影响 | 由工厂规范化为草稿 | 来源与正文保留 |
| Android 文本分享 | 受影响 | 先仅实现领域适配，不注册系统 Intent | 分享来源与正文保留 |
| 图片 | 受影响 | 先仅实现领域附件引用，不读文件或上传 | MIME 与来源保留 |
| Mock Provider | 受影响 | 唯一可执行 Provider，实现同一端口 | 未确认任务被拦截 |
| 内存知识库 | 受影响 | P2 Room 的可替换实现 | 确认后写入并可回读 |
| OpenRouter、Keystore、Room、相册、系统分享 Intent、聊天 UI | 不受影响 | 属于 P2 或后续 | 不创建实现或联网路径 |

## 版本基线

| 项目项 | P1 选择 | 2026-08-12 核验与取舍 |
|---|---|---|
| Android Gradle Plugin | 9.3.1 | Google Maven 当前最新稳定版 |
| Gradle Wrapper | 9.5.0 | AGP 9.3 的最低兼容版本；Gradle 当前稳定为 9.7.0，但 P1 不追新到超出必要范围 |
| Kotlin | AGP Built-in Kotlin 2.2.10 | Kotlin 独立最新稳定为 2.4.10；AGP 9 默认内置 Kotlin，官方要求不再应用 `org.jetbrains.kotlin.android`，因此以 AGP 自带版本保持受支持组合 |
| Compose Compiler | `org.jetbrains.kotlin.plugin.compose` 2.2.10 | Kotlin 2.x 要求 Compose Compiler 插件；版本与 AGP Built-in Kotlin 2.2.10 对齐，不应用冲突的 Kotlin Android 插件 |
| compileSdk / targetSdk | 36 | Android 16 的稳定 API level |
| minSdk | 26 | P1 的明确兼容性下限；未来若业务/依赖需要再单独评审 |
| Compose BOM | 2026.06.01 | Google Maven 当前最新稳定 BOM |
| JDK | 17+（本机 Android Studio JBR 21） | AGP 9.3 至少要求 JDK 17 |

版本值不得使用动态 `+`；Provider、模型、价格与能力不进入本阶段代码。

独立“最新版本”不等于可直接组合。P1 只采用官方兼容且能在本机从公开仓库下载、构建通过的版本组合；任何升级必须重新运行构建和定向测试。

## 允许范围

- 单一 `app` Android 模块，按 `app`、`domain`、`data`、`ai`、`ui` 包分层。
- Capture Draft、Source Evidence、Generated Candidate、Knowledge Item、AI Task、Invocation、Provider/Model 能力与结构化错误合同。
- Mock Provider、内存 Repository、P1 状态展示页与 Kotlin/JUnit 领域测试。
- 仅为构建安装 Android API 36 和下载公开 Gradle/Maven 依赖。

## 禁止项

- 不接 OpenRouter 或任何真实 Provider；不读取、保存、请求或测试 API Key。
- 不发送文本、图片、附件或其他用户数据到网络。
- 不引入 Room、Keystore、文件复制、相册/相机、系统分享 Intent、登录、同步、Hub、Desktop 或完整聊天功能。
- 不创建 Git 提交、发布、签名产物或修改 `docs/` 以外的历史资料。

## 最小验收与停止条件

1. 文本、分享与图片输入都能规范化为含来源证据的 Capture Draft。
2. 未确认的 AI Task 返回结构化 `ConsentRequired`，且 Mock Provider 不会被调用。
3. 未经用户确认的 Candidate 不能创建 Knowledge Item。
4. 已确认 Candidate 保存后可从同一 Repository 读取，来源与调用溯源仍存在。
5. Debug 构建和定向 JVM 测试通过；构建失败时不把源码完成表述为可用。

达到以上条件即结束 P1，不提前延长到 P2。

## 完成证据

- `testDebugUnitTest`：6 项测试、0 失败、0 错误。
- `lintDebug`：通过，无 Error；P1 当时的 API/Gradle 更新提示仍待后续版本评审。启动图标已在后续独立任务中交付，详情见 `LAUNCHER_ICON_DELIVERY.md`。
- `assembleDebug`：通过，生成 `app/build/outputs/apk/debug/app-debug.apk`。
- 本阶段未运行模拟器、真实设备、真实 Provider、真实文件路径或发布签名验证；这些证据不得由本地构建结果替代。
