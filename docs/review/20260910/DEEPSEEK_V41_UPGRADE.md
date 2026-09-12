# DeepSeek V4.1 Flash 双端升级记录

日期：2026-09-10。代码基线：`5c421e1`；保留此前三个 Desktop 侧栏／菜单修正。此记录只覆盖本次增量，不改写历史审计结论。

## 官方核验与实现

- [官方模型与价格](https://api-docs.deepseek.com/quick_start/pricing/)：当前名称 DeepSeek V4.1 Flash，正式 API ID `deepseek-flash`。旧 `deepseek-v4-flash` 和 vision-exp 名称只是兼容别名，不作为新请求目标。1M 上下文／384K 最大输出在原 Android 目录已经存在，本次未夸大为新增限制。
- [官方视觉协议](https://api-docs.deepseek.com/guides/vision/)：新版支持图片。Android 和 Desktop 实际发送 Chat Completions `image_url` 与 Responses `input_image`，保留原始字节；DeepSeek Pro、PDF、视频和音频不扩张原生能力。客户端继续遵守各自导入格式边界，适配层支持 JPEG／PNG／WebP／GIF。DeepSeek 单图 32 MiB、请求 48 MiB；本机预检保留封装余量。
- 当前 USD 每百万 Token：低谷非缓存输入 0.15／缓存输入 0.003／输出 0.6；高峰分别 0.3／0.006／1.2。UTC 周一至周五 01–04、06–10 为高峰，周末低谷。Android 只在缺少 Provider 结算时使用版本化 token-only 估算；不含工具费用。Desktop 既有 owner 只保存 Provider 返回的费用，本次没有新增未经实现的本机估价。

## 双端贯通与兼容

| 层 | 证据与行为 |
| --- | --- |
| Android 目录 | `app/src/main/assets/model_profiles.json`、`domain/ModelService.kt`、`domain/ChatModelRouting.kt`；完整名称、短名 DS V4.1、说明、原生视觉同步 |
| Android 缓存 | `data/AndroidModelProfileDirectory.kt`；旧 API ID 缓存不能覆盖新 seed；`AndroidModelHealthStore.kt` 为新版 Flash 分离健康键，旧失败不影响新模型 |
| Android 调用 | `ai/ChatProviderAdapters.kt`；原始图片以分段 body 发送，联网 Responses 不丢图片；仍由已披露接收方授权 owner 约束 |
| Android 费用 | `domain/ConversationCostEstimator.kt`、`DeepSeekPricingWindow.kt`；新版独立价格版本，历史账不重写 |
| Desktop 目录与选择 | `desktop_model_service_v1.rs`、`lib.rs`、`android-settings-shell.mjs`、`chat-shell.mjs`；实际 API、设置、Auto／手动选择和当前缓存显示同步 |
| Desktop 图片 | `lib.rs::ordinary_chat_request_messages` 校验私有资产 hash 与当前实际模型；`desktop_ordinary_chat_v1.rs` 保留 Responses 图片内容 |
| 标题与历史整理 | 保留 `DEEPSEEK_V4_FLASH` 优先级／Provider Key／启用设置，映射到新版目录；双端提示同步 |
| 当前文档 | 会话／设置／运行时合同及 Android 矩阵工具同步；历史归档保持原样 |

`DEEPSEEK_V4_FLASH` 和 Android `logical:daily:deepseek-flash` 是已持久化的兼容键，不能把内部 V4 字样误当漏改。Desktop 旧 API 形式的当前选择也显式归一到稳定键。历史消息、Attempt、账本和旧短名 DS V4 原样保留，当前 Composer 单独投影新版名称。

## 文档与代码冲突

1. 旧当前合同称 Flash 为纯文本、页脚 V4 Flash；实际既有页脚已为 DS V4，新版现在为 DS V4.1。当前合同已按代码和官方图片协议修正。
2. 旧双端峰谷代码使用每天同一时段；当前官方限定工作日。双端显示与新版 Flash 估价已修正，历史旧 ID 价目保留。
3. 旧 Flash 别名转发的精确切换时刻未公布。2026-09-10 起旧 Flash 别名不再套已退休本机价目，缺少结算则保持未知；不猜测历史服务版本。
4. 官方公告 Pro 将于 2026-09-14 北京时间 12:00 起转发 V4.1 Flash。此次只升级 Flash，未提前改写 Pro 当前名称、默认预设或历史归因；该未来边界仍待当时核验处理。

## 功能审阅

保留：在既有模型设置、日常选择、Auto、标题／历史整理入口升级；图片理解复用附件入口。无新增常驻按钮。理由：明确替换旧版本并修正真实能力，保留既有选择与数据。

## 验证

最终结果见本目录 `deepseek-v41-verification.json`。新覆盖包括：缓存模型／健康状态升级、双端目录一致、图片原始字节与联网协议、Desktop 私有资产实际投影和 Pro 拒绝、旧选择兼容、周末边界及新版缓存计价。

未安装主设备、未更新正在运行的 macOS 应用、未做原生视觉验收、未读取真实凭据或发送真实 Provider 请求。单测／构建不等于真实服务验收。
