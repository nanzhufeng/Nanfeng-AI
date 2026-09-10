# Desktop 10 条功能链当前完成度审计（2026-09-02）

## 结论

本轮不再把“24 张截图对齐”当作功能完成。以 Android 当前源码、CURRENT 合同和 Desktop 真实 owner 为准，10 条功能链均已经有本地 owner 和自动化证据；本轮唯一新确认的本地缺口是 Desktop Composer 缺少“相机”入口与私有附件写入链，现已用红→绿增量闭环。

“本地完成”不等于“真实外部端到端完成”。Provider 真实回答、Google／南枫云账号、macOS 系统通知授权与点击仍是独立外部验收边界。本轮未读写正式 Desktop 数据根，未操作 OPPO，未运行任何 `connected*AndroidTest`。

## 口径

- **已证实本地**：Desktop 存在实际 owner，自动化或隔离原生证据通过，不依赖外部服务。
- **外部待验证**：本地请求或状态链已完成，但真实 Provider／账号／系统通知未在本轮触发。
- **不是缺口**：Android 当前合同明确不应暴露的功能，Desktop 不伪造入口。

## 10 条功能链

| # | 功能链 | Android 当前标准 | Desktop owner／当前状态 | 最强证据 | 独立边界 |
|---:|---|---|---|---|---|
| 1 | 聊天模型选择 | Composer 为“自动／日常／深度”；手动层显示完整候选 | P6-G 目录、全局默认、会话覆盖、手动候选和实际 Attempt 归因均在；已证实本地 | Node 覆盖三层、完整候选、手动不回落 Auto；实际模型事实从持久化 Attempt 读取 | 真实 Provider 响应未调用 |
| 2 | Composer 附件与偏好 | 顺序是相机→图片→文件→基础风格和语气→实时网页搜索 | 本轮新增真实 `getUserMedia`、PNG 捕获、普通／临时私有附件 owner；取消或权限失败不改草稿 | Node 相机顺序红→绿；Rust 拒绝非 PNG，普通／临时复用内容寻址资产；隔离原生打开弹窗后未拍摄取消，SQLite 附件 0／0／0 | 用户实际镜头画面未采集；这是隐私选择，不是 owner 缺口 |
| 3 | Desktop 大 Composer 与会话布局 | Android 功能顺序为标准，平台尺寸不强行相同 | Desktop 保留大输入框、持久导航和独立滚动 owner；侧栏离屏只作防溢出 fallback | 1440 唯一设计合同、内部最小可用门禁、长会话滚动／草稿保留回归测试 | 无 |
| 4 | 提醒／监控 | 意图建议→草稿复核→创建／编辑／修订冲突／暂停／恢复／删除／重试 | 草稿、计划、运行、诊断、后台唤醒和窄通知路由均在；已证实本地 | Node 行为链＋Rust 持久化＋隔离原生既有 create/edit/pause/resume/delete 证据 | macOS 真实通知授权与点击未触发 |
| 5 | 南枫转写 | Android 当前是固定官方 GLM-OCR 的图片／PDF 转 Markdown，不加入聊天模型选择 | Desktop 保持固定 GLM-OCR，导入、页状态、Attempt、取消、重试、导出和重启恢复均在；已证实本地 | 真 PNG／PDF 隔离原生证据；空凭据持久化 `ZHIPU_API_KEY_MISSING / Attempt 1 / request 0` | 真实智谱 Provider 未调用；“转写模型可选”与当前 Android 合同冲突，不实现 |
| 6 | 会话标题点击与水位 | Android 点击标题进入会话选择；首次打开最新，已访问恢复保存位置 | Desktop 标题进入真实选择，`chatScrollPositions` 和最新水位分流；已证实本地 | 新回归 `conversation title click restores its saved waterline or opens at latest` ＋既有 600 消息隔离原生证据 | 无 |
| 7 | 全局搜索附件 | 真实缩略图、类型／MIME／大小／时间、预览／系统打开、精确列表恢复 | Desktop 共享搜索目录、延迟缩略图、owner/hash 复核、预览和列表 offset 均在；已证实本地 | 真 PNG／PDF／MP4／Markdown／DOCX 多格式隔离验收，尺寸／hash 回读一致 | 系统打开仍受本机关联 App 控制 |
| 8 | ChatGPT／Claude ZIP 身份与删除墓碑 | 官方身份跨 ZIP 去重，用户主动删除不复活 | `p6k_zip_official_identity_ledger` 和 user-delete tombstone 已接入导入／删除／重导入；已证实本地 | Rust tempfile 覆盖首导、重复、追加、冲突、批次删除、重导不复活 | 原生 UI 未点穿破坏性删除；不影响 owner 自动验证结论 |
| 9 | 会话／全局实时网页搜索 | Android 全局默认可被当前会话覆盖，实际发送／重试必须消费有效值 | Desktop 已将持久化值投影到普通发送、历史 Compare 兼容路由和重试；Provider 形状不支持时 fail-closed | Node 请求形状、覆盖优先级、重试继承和不伪造成功状态回归 | 真实网页结果／Provider 费用未调用 |
| 10 | 历史资料库整理 | Android 开关持久化，12h 低频到期窗，候选复核后入库，关闭即取消 | Desktop 设置保存连到 schedule owner，due selection／reserve／checkpoint／retry／review／persisted candidate 和后台唤醒均在；已证实本地 | Node 最小候选与关闭取消＋Rust 时窗、checkpoint、review 与持久化回归 | 真实模型整理内容未调用 |

## 本轮额外 UI 合同

### 唯一宽屏设置与防溢出门禁

- Desktop 当前唯一产品设计为 `1440×900` 宽屏版；`1000×800` 与 `700×900` 只用于内部防溢出／最小可用测试，不定义紧凑版、窄屏版或第二套 IA。
- 设置一级行在所有窗口保持 `68px`，不再由 `宽 ≤ 1180px` 或 `高 ≤ 820px` 降密度。四组一级导航和右侧详情始终同时存在，小窗口只滚动同一表面。
- 渲染实测：`1440×900` 保持宽屏卡片和模型设置层级；`1000×800`、`700×900` 均为双栏、`68px` 行高、页面级横向溢出 `0`。

### 模型与联网层级

- 根据 Android `ModelSettingsPrimaryEntry` 与 `ModelSettingsRecordsCard`，“模型设置”为独立强调卡：44px 橙色 Key 图标、标题、Provider／API Key 说明与橙色进入箭头。
- “费用与用量／上下文记录／运行诊断”只属于后续“调用记录”次级组，不再与主入口四项均分。
- Browser 交互实测已点击主卡，进入真实“模型设置”页并读到 Provider、API Key 与“测试连接”控件。

## 红→绿与门禁

- 相机 Node 红测：`desktop composer camera is a real private-capture path before image and file pickers`。
- 相机 Rust 行为：`camera_capture_accepts_only_window_png_and_reuses_private_attachment_owners`。
- UI 红测：拒绝紧凑断点／48px／第二套设置 IA；模型设置独立主入口与次级记录组。
- 当前纠正后 Node `161/161`，0 fail，0 skip；Rust `181/181`，0 fail，0 ignored；Android 设置／模型专项 `24/24`。
- lint、typecheck、protocol golden、static build、`cargo check`、macOS bundle 与 strict codesign 通过。Protocol semantic hash 为 `ad41c1ee6aa64b9e2f218034dbefccc333c43fb923b874b12ff51ce5972d4031`，package hash 为 `74078bf5bdc1cbe53fbc4ac88029f8039b7aed9f8d0e79ea1f6c59ecf6aeb4ec`。

## 隔离验收与产物

- 相机 QA Bundle ID：`com.nanzhufeng.ai.desktop.compareacceptance.70684.mtjnz95e`；隔离根：`/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.rdyF74`。
- 原生顺序读回为“相机→图片→文件→基础风格和语气→实时网页搜索”。临时聊天打开相机弹窗后，在“正在请求本机相机；尚未拍摄或写入草稿”状态取消；不捕获用户环境画面。
- 隔离 SQLite `quick_check=ok`、schema 37，`desktop_attachment_assets=0`、`desktop_conversation_attachments=0`、`desktop_temporary_attachments=0`。验收后只结束精确 QA PID。
- 渲染 QA URL 为一次性 `http://127.0.0.1:4173/`；page identity、非空 DOM、无 framework overlay、console error/warn 0，模型主入口真实路由通过。验收后已关闭 tab、恢复视口并停止本地 server。
- 当前对外截图：`/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a060eb-322b-7dd2-b8da-1664f33e02de/nanfeng-ai-wide-only-20260902/`，仅含 `1440×900` 页面与宽屏联系表；旧的 1000／700 截图只保留为历史内部证据，不再代表产品设计。
- 当前 bundle：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`。主程序 `31,010,112` bytes，SHA-256 `076d6f73c2eddab70bfb9369db9ebde638619313db00da877704c4f6bfcd9945`，`Identifier=com.nanzhufeng.ai.desktop`，ad-hoc，`TeamIdentifier=not set`。`NSCameraUsageDescription` 已进入并从最终 Info.plist 读回。

## 未在本轮冒充的事实

- 未调用真实 OpenRouter／Qwen／DeepSeek／智谱回答。
- 未登录 Google／南枫云，未读写真实同步数据。
- 未请求或点击 macOS 真实通知。
- 未拍摄、保存或上传用户实际镜头画面。
- 未读写正式 Desktop 数据根，未操作 OPPO，未运行任何 `connected*AndroidTest`。
