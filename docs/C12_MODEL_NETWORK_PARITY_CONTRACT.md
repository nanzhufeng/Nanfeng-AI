# C-12 模型与联网／调用记录跨端合同

日期：2026-09-03
状态：Desktop 本机实现与分层验收已完成；真实 Provider、真实网页来源及真实费用继续独立未验收

## 1. 当前事实与范围

- Android 可见与行为事实只读取 `ModelSettingsUi.kt`、`ConversationCostLedgerUi.kt`、`AutomaticWebSearchPolicy.kt` 和 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)。本合同不根据 Provider 品牌或模型常识推测能力。
- 二级页固定为四行连接状态、实时网页搜索、独立重点卡“模型设置”和组合卡“调用记录”；调用记录顺序为费用与用量、上下文记录、运行诊断。
- Provider 配置固定为 OpenRouter、DeepSeek、智谱、Qwen 四个本机配置 owner。API Key 只保存在各平台私有凭据 owner，不进入设置投影、调用记录、诊断、截图或文档。
- 本增量只处理 C-12。Composer 的会话级开关继续由 C-07 override owner 持有；全局开关与会话 override 在请求构建时统一解析，不新建第二套会话状态。

## 2. 联网语义与来源门禁

- 全局实时网页搜索开启后，每次普通对话都请求当前 Provider 已实现的网页检索，不用关键词猜测是否“需要当前信息”。关闭后不请求网页工具。
- 带附件的普通对话先走共享文本／Markdown 投影，再保持显式联网状态。不得因存在附件而静默关闭 DeepSeek 或智谱的网页检索。
- 当前 Desktop 请求 owner 只声明已在源码中实现的路由：OpenRouter server tool、Qwen Responses／Chat Completions 分支、DeepSeek Responses、智谱 Chat Completions。这里的“已实现”只表示请求序列化与本机响应解析存在，不表示真实服务已验证支持、可用或计费正确。
- 联网成功必须伴随 Provider 返回的结构化公开来源。只接受无凭据、具有 host 的绝对 `http`／`https` URL；去重后附在回答下方。声明联网的响应若没有结构化来源，则以 `WEB_SEARCH_NO_SOURCES` 失败关闭，不把模型记忆生成的正文冒充网页结果。

## 3. 调用记录投影

- “费用与用量”有记录时固定显示本机累计、四类 2×2 汇总和四段选择：会话、会话标题整理、历史资料整理、南枫转写。提醒草案与定时监控作为“其他自动任务／计划与提醒”保留，不混入四段主分类，也不得丢失。
- 完全无记录时沿用 Android 当前空态，不伪造金额、Token 或摘要。金额必须保留事实等级；未知就是未知。
- 上下文记录只显示实际选中的资料，并固定保留对话、资料数、Provider／模型、最多两项来源、本轮输入 Token 与发生时间。
- 运行诊断保留“连接失败”和“自动与工具任务”两组；主卡显示安全摘要、耗时与发生时间。Prompt、回答、原始 Provider body、Key、Authorization、URI、私有路径不进入投影。

## 4. 验收事实

- 红灯：新增 `desktop/tests/c12-model-network-parity.test.mjs`，首次 `0/4`，分别锁定开关语义与文案、四类账本、上下文／诊断时间以及 Rust 不得按关键词或附件关闭联网。
- 绿灯：C-12 当前 `5/5`；Desktop 完整 Node `212/212`、Rust `187/187`，lint、typecheck、inventory、protocol golden、static build、主题 computed style 与 release bundle strict codesign 均通过。`cargo fmt --check` 只剩本轮开始前同一 `lib.rs` 的 3 处无关 dirty 格式差异，C-12 两处新增格式差异已手工消除，没有批量格式化覆盖用户改动。Android 七个专项类共 `27/27` 且 `:app:assembleDebug` 通过；没有运行 instrumentation 或任何 `connected*AndroidTest`。
- Browser：`1440×900` 下模型与联网、模型设置、费用与用量、上下文记录、运行诊断共五态无页面溢出，console warning／error 为 0；全局联网开关关闭后刷新仍关闭，随后恢复开启。Browser 使用固定只读投影，不读写 Desktop SQLite、不调用 Provider。
- 隔离 Tauri：合格复验明确注入 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.AKdmv5`，并以 `--diagnostic-ui-schema-acceptance` 关闭自动任务、外部访问和业务恢复。原生开关 `on → off`，退出重启仍为 `off`；SQLite 为 0 workspace、`web_search_enabled=0`、revision `2`、`integrity_check=ok`，运行期网络 socket 为 0。

## 5. 风险与未验收边界

- 首次原生探针只修改复制包的 `CFBundleIdentifier`，但 Tauri 数据目录仍使用编译期 identifier，进程取得了正式根 `.runtime-owner.lock`。发现后立即终止，未做 UI 操作、账号或 Provider 调用；诊断启动允许 schema migration，因此不能宣称该次对正式根绝对零写入。后续只使用源码强校验的唯一 `/tmp` root 完成合格复验。
- 本轮没有连接 Android 设备或模拟器；Android 证据为当前源码、现有 `11-model-network.png` 和 `27/27` JVM 合同。因此根页可见层级有 Android／Desktop 对照，带数据的费用／上下文／诊断 Android 同内容截图仍未新增，不把 Desktop fixture 当成 Android 原生截图。
- 未读取或保存真实 Key，未调用真实 Provider、网页、账号、通知或 OPPO。真实网页工具支持、来源格式、费用与余额必须在另一次明确授权、可能产生费用的外部验证中裁决。

## 6. 证据

- Browser 与原生 PNG、联系表：`~/.codex/visualizations/2026/09/03/01a0657a-fb01-7603-8db8-850d1886980e/nanfeng-ai-c12-20260903/`
- 当前开发 bundle：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`
- 主程序 SHA-256：`f270fdee37fe10666f04ed15c3fcf096a31b4e6f00091d96070e068f490244f4`；ad-hoc strict codesign 通过，不是 Developer ID／公证正式包。
