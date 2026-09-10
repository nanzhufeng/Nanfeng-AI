# Desktop 设置与 Android 当前合同对齐矩阵

日期：2026-09-01
状态：当前实现与 2026-09-01 最终验收清单；只以 Android live source 与 `ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md` 为项目事实

## 边界与布局裁决

- Desktop 唯一布局差异是左侧持续显示 Android 设置一级菜单，右侧显示选中的二／三／四级页面。
- UI 只读 Rust/Tauri 投影；SQLite 拥有非敏感设置，macOS Keychain 只拥有 Provider 凭据，业务 SQLite/受管文件拥有对话、附件、导入、备份、转写、费用和诊断事实。
- 未在 Desktop 建立业务消费者的 Android 设置保留对齐位置，但禁用控件并写明原因；不得用可点开关、Toast 或 `localStorage` 冒充完成。
- 字段标题位于输入框上方；不使用 floating label。页面继续使用灰底、亮白卡、统一胶囊／圆角轮廓和同轮廓的 hover／pressed／focus 反馈。

## 2026-09-02 宽屏密度补充

- 24 张问题截图已归并为共享设置密度 owner；详见 [24 张问题截图矩阵](DESKTOP_24_SCREENSHOT_PARITY_MATRIX_20260902.md)。
- 当前宽屏一级栏为 `clamp(320px, 30vw, 420px)`，设置主行 68px，详情内容上限 880px、页面间距 20px；原有 20px 白卡圆角、灰底 canvas 和整行选中态不变。
- 外观／字体大小／主题色共用 `minmax(108px, auto)` 右值列和同一个 `justify-self:end` 值组；主题色色点在文字左侧，色点和文字整体右对齐，三段文字右边缘仍严格共线。1440px 与 700px 浏览器几何回读 spread 均为 0px。
- 700×900 浏览器实测为 `300px + 400px` 两栏、无整页横向溢出；五风格弹窗为 600×620、5 张最小 94px 卡片。该变化没有放大 Composer，也没有改变任何 SQLite／Keychain owner。

## 逐项矩阵

| Android 一级 → 二/三/四级条目 | Desktop 显示位置／文案／顺序 | 控件 | 真实 owner | 持久化 | 验证状态 | 当前缺口／裁决 |
| --- | --- | --- | --- | --- | --- | --- |
| 对话 → 个性化 | 左侧“对话”第 1 项；右侧“个性化” | 草稿 + 右上角保存 | `desktop_app_settings_v1` + ordinary chat owner | SQLite 单行 revision | Rust/Node/隔离 Tauri 已覆盖 | native 真值已移至 SQLite；主要可见字段已有普通发送消费者。 |
| 个性化 → 启用记忆 | 顶部单行卡，说明放 canvas | 开关 | 同上 | SQLite | Rust/Node 已覆盖 | 已启用并驱动本机相关 Memory 选择；审计不复制正文。 |
| 个性化 → 历史资料库 | 紧跟记忆说明 | 开关 | 同上 | SQLite | Rust/Node 已覆盖 | Desktop 已有 Knowledge 本地检索半链，但无 Android 同级自动沉淀 owner；继续禁用并说明。 |
| 个性化 → 基础风格和语气 | 记忆区之后 | 统一白色选择面 | 同上 | SQLite | Rust/Node 已覆盖 | 已由普通发送消费。 |
| 个性化 → 记忆摘要 | “记忆摘要”三级入口 | 导航行 | 现有 Memory 工作区 | 业务 SQLite | 局部已实现 | 现有 Desktop 页是 Memory CRUD，尚不是 Android 的摘要读取/软删除/生成开关合同；页面说明平台差异。 |
| 个性化 → 你的昵称／你的职业／自定义指令 | 按 Android 当前可见顺序 | 标题上置的单行/五行输入 | `desktop_app_settings_v1` + ordinary chat owner | SQLite | Rust/Node/隔离迁移已覆盖 | 已由普通发送消费；旧 `localStorage` 值只作首次迁移输入，迁移后删除旧键。Android 当前页面不显示“更多信息”，但 domain／仓库／请求仍保留 `interests`（“关注方向”）兼容字段；Desktop 尚未保存或消费它，属于导入／旧值保真缺口而非可见 UI 缺项。 |
| 对话 → 模型与联网 | 左侧第 2 项 | 导航页 | 现有 Provider/usage/context/diagnostic owner | SQLite + Keychain + 用量账本 | 已有定向覆盖，待全量回归 | 保留四行连接事实、实时网页搜索和四个三级入口。 |
| 模型与联网 → 实时网页搜索 | 连接状态下方 | 开关 + canvas 说明 | `desktop_app_settings_v1` + ordinary chat transport | SQLite | Rust/Node 已覆盖 | native 已启用并按 Provider route 消费；本轮未调用真实 Provider。本机全局搜索继续是独立能力。 |
| 模型与联网 → 模型设置 | 唯一重点卡 | 四 Provider 分段、启用、预设、Key、保存、测试 | `desktop_model_service_v1` + Security.framework | SQLite `desktop_provider_settings` + Keychain | Rust 已有定向覆盖 | 保留 OpenRouter → DeepSeek → 智谱 → Qwen；不增加可编辑端点或任意模型。 |
| 模型设置 → GLM-OCR／Qwen3-ASR 能力说明 | 对应 Provider 模型选择面底部 | 不可选信息项 | 现有 GLM-OCR/语音转写 owner | SQLite 任务 + 共享 Keychain 凭据 | 已实现 | 不新建第二套设置或把它们加入聊天预设。 |
| 模型与联网 → 费用与用量 | “调用记录”组合卡第 1 行 | 四区汇总 + 分段明细 | `usage_ledger_v1` + 转写/OCR attempt | 独立 SQLite 用量账本 | 已实现会话部分 | 待核对标题整理、历史整理、语音/OCR 分类读取；未知金额不伪造。 |
| 模型与联网 → 上下文记录 | “调用记录”组合卡第 2 行 | 本机列表 | 已有 context selection audit 投影 | 业务 SQLite | 已实现空/有记录投影 | 不把“未加入”空记录显示成使用事实。 |
| 模型与联网 → 运行诊断 | “调用记录”组合卡第 3 行 | 失败/自动任务列表 | Provider diagnostics + invocation ledger | SQLite | 已实现基础投影 | 保持安全摘要，不显示 Key、正文、Prompt、URI 或私有路径。 |
| 对话 → 提醒 | 左侧第 3 项 | 三行组合卡 | `desktop_app_settings_v1` + 平台能力投影 | SQLite | Rust/Node/隔离 Tauri 回读 | Desktop 尚无计划监控通知、对话尾部提醒建议、未读水位消费者；禁用全部开关并说明，不伪造系统通知。 |
| 对话 → 对话管理 → 收藏／已归档／回收站 | 左侧第 4 项，右侧三级列表 | 导航、取消收藏、恢复、移入回收站、永久删除、批量清理 | 现有对话生命周期 owner + `desktop_app_settings_v1` 收藏表 | 业务 SQLite | Rust/Node/原生重启已覆盖 | native 收藏已移至工作区级 SQLite，校验对话存在且幂等；原生右键收藏和重启恢复已实测，Web 预览才使用 `localStorage`。 |
| 外观 → 外观／字体大小／主题色 | 左侧“外观”组，直接显示当前值 | 统一选择面 | `desktop_app_settings_v1` | SQLite | Rust/Node/原生重启与冲突已覆盖 | native 真值已落 SQLite，仍立即投影全局 token，小/标准/大保持 80%/100%/124%；双实例 stale revision 明确拒绝且可见。 |
| 数据管理 → Google 账号与同步 | 左侧第 1 项 | 状态卡；无可点登录/同步按钮 | 尚无 Desktop Google/Supabase 生产 owner | 不持久 token/恢复码 | 未做外部闭环 | 真实显示“未建立，本机数据不变”；不复制 Android 账号、Client ID 或恢复码。 |
| 数据管理 → 导入与导出 → JSON/ZIP 对话导入及结果 | 对话区：ChatGPT/Claude JSON，再是 ChatGPT/Claude ZIP，各有结果入口 | 原生文件选择 + 持久任务列表 | 现有 JSON/ZIP 导入 owner | SQLite + 受管私有文件 | 已有定向覆盖 | 只显示安全批次统计，ZIP picker 仅接受 ZIP。 |
| 导入与导出 → 工作区导入／导出 | “工作区”区 | 原生 picker/save | v2 workspace exchange owner | SQLite + 受管 package | 已有定向覆盖 | 不把交换包冒充本机备份或云同步。 |
| 导入与导出 → 本机备份与恢复 | 独立区：备份／恢复 | 原生 picker/save + 预检 + 强确认 + 重启边界 | `desktop_local_backup_v1` | SQLite 一致快照 + 受管文件 | Rust 已有完整测试 | 凭据、本机 Provider 设置等设备状态继续排除/保留。 |
| 数据管理 → 本机数据 → 概览与内容钻取 | 右侧“本机数据” | 真实数量/字节 + 存在真实页的导航行 | Desktop privacy inventory + 全局搜索/项目/知识/转写 owner | SQLite + 受管文件实际长度 | Rust/Node 已有部分覆盖 | 不统计无 owner 残留为用户内容；转写与导入附件不重复计费。 |
| 本机数据 → 清理与删除 | 概览下方“选择清理范围” | 预览、逐项选择、确认文字、失败保留重试 | Desktop privacy deletion owner | SQLite + 受管文件 + Keychain 清理回执 | Rust 已有定向覆盖 | 移除过时的“清空知识与记忆回收站”UI；已归档/回收站统一从对话管理清理。 |
| 数据管理 → 关于 | 左侧最后一项 | 品牌 + 版本组合卡 | Tauri runtime/package metadata | 无业务写入 | Browser/隔离 Tauri 已回归 | 显示当前版本/平台/架构；Android 当前无“检查更新”设置，Desktop 不新增假更新按钮。 |
| 工作区 → 项目与知识 | 左侧工作区第 1 项 | 组合卡：管理 Projects／管理知识库 | 现有 Project/Knowledge owner | 业务 SQLite | 已实现 | 继续复用当前工作区，不建平行数据链。 |
| 工作区 → 开发与诊断 | 左侧工作区第 2 项 | 组合卡：本次 Context 控制／离线评测 | 现有 Context/离线评测 owner | SQLite/内存态按各自合同 | 已实现入口 | 删除 Desktop 额外的“功能审阅 · 南枫转写”卡；Android 当前合同明确不展示功能审阅入口。 |
| 搜索 | 设置内只有“实时网页搜索”开关；本机全局搜索在左侧/搜索页 | 非额外设置 | 现有派生索引 owner | SQLite schema 23 | 已验收的前序增量 | Android 当前无“搜索设置”页，不新增。 |
| 南枫转写（语音）／GLM-OCR | 左侧独立工作页；设置只在 Provider 选择面显示能力边界，费用页读账本 | 非额外设置 | 现有 transcription/GLM-OCR owner | SQLite + 受管文件 + 共享 Provider Keychain | 已有前序验收 | SenseVoice 继续显示“验证中／保留实验”，不伪造可用。 |
| 附件/缓存 | 本机数据概览、搜索钻取和清理确认 | 非额外设置 | 共享附件引用/删除/隔离区 owner | SQLite + 受管文件 | 已有前序测试 | Android 当前无通用“缓存大小”开关或清缓存按钮；不伪造。 |
| 更新 | 关于页仅显示版本事实 | 无 | 不存在 | 无 | 未调用更新服务 | Android live source/当前合同没有更新设置；不实现。 |
| 功能审阅 | 当前设置不显示 | 无 | 源码中只有未调用历史 Composable | 无 | 不适用 | 当前合同明确首页不增加“功能审阅”入口；Desktop 删除额外审阅卡。未来面向用户的新功能仍先进产品决策，不因本矩阵自动新增入口。 |

## 本轮执行结果

1. SQLite 设置 owner 已接管外观、个性化、提醒和实时网页搜索的 native 持久化；原 `localStorage` 只作一次可控迁移输入。
2. 没有 Desktop 运行时消费者的开关与表单均禁用并显示原因；SQLite 仍保留兼容字段，不伪造已生效。
3. native 收藏已迁入 SQLite；读写按工作区隔离，仅接受真实存在的对话 ID，重复操作幂等。
4. 过时的 Desktop 额外功能审阅卡和过时清理入口已移除；底层已有 owner 与 Android 业务数据未删除。
5. Browser 在 `1280×800`、`820×900` 与原生最小窗口 `780×560` 逐页实看；修复设置模式继承 `620px` 最小高度造成的整窗滚动。10 个一级页、10 个二／三级页、3 个外观选择器均无横向溢出，字段标题上置，无 floating label，控制台无 app error／warn。
6. 隔离 Release Tauri 完成默认值、SQLite 更新、完整退出重启、收藏恢复和 UI/数据库双向回读；正式 Desktop 用户数据未读取。
7. 双实例 stale revision 被 Rust 拒绝，数据库保持新 revision；同时修复设置壳遗漏错误状态的问题，页面现明确显示冲突及刷新建议。
8. 同 Bundle ID 临时 WebKit 夹具证明旧两条 localStorage 只迁入一次：首次写入 SQLite revision `1` 后旧键均删除，第二次启动值和 revision 不变。
9. `npm run lint`、`npm run typecheck`、Node `115/115`、protocol golden、`cargo check`、Rust `124/124`、静态 build、macOS Release bundle 与严格 codesign 均通过；无失败、跳过或 ignored。Release 为 ad-hoc arm64 开发包，未做 Developer ID 签名或公证，`spctl` 拒绝，不能冒充正式分发包。
10. 本轮未调用 Google／Supabase、Provider、系统通知或更新服务；无消费者能力继续禁用并显示原因。Desktop 设置增量已完成，但 Android → Desktop 整体仍需继续审计聊天／流式、附件、Provider／账号同步与诊断模块。

## 2026-09-01 最终审计校正

- “个性化”“启用记忆”“基础风格和语气”“昵称／职业／自定义指令”现已有 Desktop 普通聊天真实消费者，不再属于禁用占位；首轮昵称兜底、最小 Memory 选择与回答级来源审计已有 Rust/Node 证据。
- Android 当前个性化页面不再显示“更多信息”，但 live domain、偏好仓库和请求指令仍保留 `interests`／“关注方向”；Desktop 尚未保存或消费这个隐藏兼容字段。该项按导入与旧值保真缺口交给后续，不误写成页面漏项。
- “实时网页搜索”现已有 Android 同形 Provider route 与 Responses／SSE 解码，native capability 已启用；Web 预览仍保持禁用说明，因为它不读取 Desktop SQLite 或凭据。
- “上下文记录”现读取 schema 29 的 `desktop_ordinary_chat_context_sources`；回答页和设置页只显示类型／标题／安全模型事实，不复制正文。
- “运行诊断”现读取普通聊天近 7 天 FAILED／UNKNOWN 安全摘要；自动与工具任务区仍只显示真实 invocation 记录，不生成占位。
- “历史资料库”继续禁用：相关 Knowledge 的本地检索半链已存在，但 Android 同级低频自动整理、checkpoint、Qwen 调用、费用、失败重试与用户治理尚未完成。
- “提醒”的计划监控通知、对话提醒建议、未读提示继续全部禁用。原生 QA 发现兼容字段为真时禁用开关仍呈橙色，本轮已修为能力不可用时强制显示灰色关闭，同时保留 SQLite 原值。
- 最新验证为 Node `120/120`、Rust `143/143`、0 skipped／ignored；Browser `1280×800`、`820×720`、`780×720` 与唯一 Bundle ID Release Tauri 均已验收。整体完成裁决见 [Android → Desktop 最终完成审计](ANDROID_DESKTOP_FINAL_COMPLETION_AUDIT_20260901.md)。
