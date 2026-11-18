# Gemini 4 Hydrogen 接入

2026-11-14，南枫 AI Android 与 Mac，`main` 底层 HEAD `1837908b57ff445af157b374d2905ddd17d15b8a` 加本地工作树。

## 部署与兼容

- OpenRouter 日常档在原位置使用 `GEMINI_4_HYDROGEN`，API ID 为 `google/gemini-4-hydrogen`。原有逻辑入口、Auto 顺序及 OpenRouter 凭据组沿用。Android 目录仅接受精确标准模型，新快照可以在旧 3.8 部署消失后通过校验并成为当前快照。Mac 从 SQLite 读取当前会话和新会话模型选择时应用旧 OpenRouter 3.7／3.8 的迁移映射。
- RelayCore 的实际 Gemini Key 只读目录由前序交接核验，成功返回旧 `gemini-3.8-flash`，未列出 Hydrogen。其选模、Auto 顺序、Key 和人民币费率沿用。已保存消息、冻结 Attempt 和账本继续使用当时的模型身份。
- OpenRouter 公开目录声明 1,048,576 上下文及文字、图片、PDF 和音视频输入，双端复用 Chat Completions 和正常的附件授权。Android 以 65,536 作为标准同步输出上限并尊重更低的目录上限，Mac 普通聊天沿用 8,192。沿用现有终态、来源、取消和 UNKNOWN 处理。开启自身联网时使用现有 Gemini 原生 plugin，已有检索证据的回答禁用二次检索。[OpenRouter 模型目录](https://openrouter.ai/google/gemini-4-hydrogen) · [供应端目录](https://openrouter.ai/api/v1/models/google/gemini-4-hydrogen/endpoints)。
- 每百万输入／输出／缓存读取 Token 的标准本地估价为 1.50／7.50／0.075 USD，版本 `openrouter-gemini-4-hydrogen-2026-11-14-v1`。实际可确认的 Provider 金额优先。[OpenRouter 公开费率](https://openrouter.ai/google/gemini-4-hydrogen)。

## 验证

- Android 用 JDK 21 运行 `:app:testDebugUnitTest :app:assembleDebug`，1,602 通过、12 跳过，开发 APK 编译通过。覆盖精确 ID、旧目录迁移、设置读取、附件准备、原生检索请求与费率。首轮 JDK 17 无法运行 Robolectric SDK 36，已改用本机 JDK 21。交接页恢复合同读取门标题，原有的入口合同测试通过。
- 模型维护目录及生成矩阵一致，23 个聊天模型、1 个专用模型、7 个凭据组。维护工具 16 项通过，含领域与治理共 45 项通过。
- Mac 前端 lint、typecheck、静态构建通过。Node 全套 568 通过、12 跳过。Rust 全套 486 通过、46 忽略，ARM64 原生开发构建通过。SQLite 重开回归验证旧 OpenRouter 当前会话和新会话默认选择迁移，实际路由选择精确 Hydrogen ID。
- 上述为覆盖前的本地测试。Hydrogen 真实聊天、真实附件及 Google 联网来源未验，本次无新推理费用。

## 本地开发产物

- `app/build/outputs/apk/debug/南枫AI-开发验收.apk`。SHA-256 `3f6c0c23b98e7f379cb210b82d67cbc60350745ab3f2e5232ab471af93ca5f57`。包内模型档案与当前源码字节一致。
- `desktop/src-tauri/target/debug/nanfeng-ai-desktop-spike`。SHA-256 `0db7740a8094a4b2c6a8ce319ac410e8e29d9f025e966668eada1faa5d0db496`。Mac 静态资源见 `desktop/dist`。

## 1.0.40 保数据覆盖

- Android 采用 JDK 21 正式 Release 构建，`:app:assembleRelease :app:lintRelease` 通过。首次离线 Lint 缺缓存，使用正常依赖解析后通过。OPPO 同证书覆盖 1.0.39/code103 → 1.0.40/code104，APK 不可调试，系统回读的 APK 哈希与候选一致，正常启动并恢复到前台。首装时间、UID、数据目录和 CE／DE inode 保持，CHAT 自动整理诊断摘要和无障碍配置摘要一致。WORK 诊断文件本轮不可读，覆盖前后均未取得业务数据摘要。设备临时 APK 已清理。
- Mac Release DMG 校验通过，使用现装 Apple Development 叶证书，Bundle ID 继续为 `com.nanzhufeng.ai.desktop`。从 `/Applications` 正常退出并覆盖后，1.0.40 启动并出现应用窗口，CHAT 和 WORK 的会话数据及 Attempt 指纹相同，保留原 1.0.39 应用备份。
- Android 正式 APK SHA-256 `a6f75d4a0c605bdb793e63b513d661691a2406dd83d5ac9c9d20aa702291bc42`。Mac DMG SHA-256 `9b8a7d792fb8c15f85aeab25e1bbf505b64b42d040cfb38f3b3973b0089d3306`，现装可执行文件 SHA-256 `18353a9a523893bd0c73778b2f377e7d3b9c73459b6d6d4524e7f97e0c007f92`。均为本机安装产物。
