# Android → Desktop 最终完成度审计（2026-09-02）

> **历史快照：** 本文记录 2026-09-02 当时的本地完成度，已由 [2026-09-03 最终独立审计](ANDROID_DESKTOP_FINAL_INDEPENDENT_AUDIT_20260903.md) 重新裁决。不得再用本文的“可以收尾”覆盖 C-01～C-16 全状态台账中的未验收硬门。

## 结论

**当前本地、可回放、无外部费用的 Android → Desktop 范围可以收尾。** Desktop 已覆盖 Android 当前可达根路由、设置层级与本地业务 owner；删除第二套设置 action 后，当前生成清单证明 223 个可见 Desktop action 均有 handler，127 个前端 invoke 均存在已注册 Rust command，无断链。

Desktop 当前唯一产品设计是最新 `1440×900` 宽屏界面。`1000×800` 与 `700×900` 只作为内部防溢出／最小可用门禁；它们不是紧凑版、窄屏版、产品变体或第二套 IA，也不进入当前对外截图证据。

不能宣称“全部真实外部端到端完成”：真实 Provider 回答、Google／南枫云账号同步、macOS 系统通知授权／点击与 Developer ID／公证都需新的外部授权或账号状态。这些不是本地代码缺口，本轮未伪造通过。

## 审计口径

- **强证据：** 真实 UI 交互，命中 Rust／SQLite owner，并有重启、读回或状态机证据。
- **中证据：** 行为测试命中公共缝隙，隔离原生数据根证明 schema／owner／失败语义。
- **弱证据：** 仅源码字符、静态 Web 预览或清单引用；不单独用来判定功能完成。
- **外部待验：** 需真实 Provider、账号、系统权限、签名身份或可计费请求；未授权前停在 fail-closed。

## 可达面清单

### Android 真实入口

- 11 个 `P5ARoute`：`CAPTURE`、`CONVERSATION`、`OCR`、`KNOWLEDGE`、`PROJECTS`、`MEMORY`、`CONTEXT`、`EVAL`、`SETTINGS`、`ADAPTERS`、`CONTROL`。
- 22 个 `SettingsDestination`：首页、外观、个性化、记忆摘要、提醒、模型与模型配置、费用、上下文记录、运行诊断、对话管理／收藏／归档／回收站、工作区、开发、数据存储、JSON／ZIP 导入结果、本机备份、关于、隐私。
- Google 账号与同步是独立全屏 `P7DAccountSyncScreen`，不是 `SettingsDestination`；本轮已把该入口补回 Android 当前设置合同表。
- 会话内还包含 Composer 附件／相机／语气／联网，模型分组，搜索、长按、文本选择、预览、已读水位，提醒草案／计划，南枫转写等深层状态。

### Desktop 生成清单

`npm run inventory:audit` 从 Android enum、Desktop 渲染 action、action dispatcher、`invoke`、Rust `#[tauri::command]`与 `generate_handler!` 重新生成：

| 项目 | 结果 |
| --- | ---: |
| Android 根路由 | 11 |
| Android 设置 destination | 22 |
| Desktop 设置页 | 20 |
| Desktop 可见 action | 223 |
| 有 handler 的 action | 223 / 223 |
| Desktop `invoke` command | 127 |
| Rust command／已注册 command | 163 / 163 |
| invoke 缺 Rust command | 0 |
| invoke 缺注册 | 0 |
| Desktop dialog kind | 26 |

清单还会报告“未被测试文件字符直接引用”，但 Rust 行为测试多从 owner 函数入口验证，不以 command 字符出现为前提；因此该数字只是弱证据索引，不作为未测比例。

## 重点功能链

| 用户旅程 | Desktop UI → handler → owner | 持久化／失败语义 | 证据 | 结论 |
| --- | --- | --- | --- | --- |
| 模型选择 | Composer Auto／Daily／Deep，模型设置 Provider 分段→ P6-G 与 model-service command | catalog snapshot、conversation override、provider-scoped credential presence；未配置失败不伪造回答 | Rust 重启／退役模型／OCR 非聊天测试 + Browser 点击 | 本地完成；真 Provider 待验 |
| Composer 全入口 | 相机、图片、文件、五种语气、实时网页搜索、模型、发送／停止／重试 | 普通与临时会话私有附件 owner；发送先写 message／Attempt，失败保留可操作状态 | Node 行为测试 + Rust 相机／附件／Attempt 测试 | 本地完成；真生成待 Provider |
| 提醒全生命周期 | 草案→审阅编辑→确认计划→暂停／恢复／删除／显式重试 | draft、plan、run 分表；DST、missed run、unknown、幂等和安全点击路由 | Rust reminder 状态机测试 + Browser fail-closed | 本地完成；系统通知待验 |
| 南枫转写 | 左栏南枫转写→文档／图片→GLM-OCR 任务／详情／导出 | 任务、分段、attempt、request id、token、cost、safe error 持久；旧语音结果只读 | Rust 分块请求／回复变体／DOCX 导出／重启测试 | 本地 owner 完成；真 OCR 请求待验 |
| 全局多格式搜索 | 6 分类，文件类型／大小排序，附件预览／长按 | 安全索引不写搜索历史；预览只读 owner 私有字节；最后引用才删字节 | Node 全屏搜索行为测试 + Rust 统一搜索／附件删除测试 | 完成 |
| 已读水位 | 点标题恢复水位／新对话到最新，未读点／手动未读 | workspace-scoped marker，completion／retry／duplicate 独立水位，重启保留 | Rust read-marker 测试 + Node 滚动行为测试 | 完成 |
| ZIP 去重／墓碑 | ChatGPT／Claude ZIP →预检／结果／恢复控件 | 官方身份去重，跨包稳定；用户删除墓碑阻止后续包复活；路径穿越失败关闭 | Rust ZIP inventory／dedup／tombstone／rollback 测试 | 完成 |
| 网页搜索消费 | 全局值／会话 override→每次发送重读 | 只影响请求形状；不把设置值宣称为已联网；Responses／安全去重来源单独解码 | Rust provider request-shape／来源测试 + Node toggle 测试 | 本地消费完成；真搜索待 Provider |
| 历史资料库调度 | 个性化→历史资料库，开发与诊断投影调度状态 | reservation／checkpoint／attempt／proposal／review 分离；必须人工接受才进知识 | Rust 中断／显式重试／审阅／选择测试 | 本地完成；生成 proposal 待 Provider |
| 全部深层设置 | 个性化、记忆、模型、费用、上下文、诊断、提醒、对话、账号、导入、本机数据、备份、关于、工作区 | 每页显示 native capability 真实性；无 owner 的 Web 预览显式禁用；本地备份需预检／强确认／重启 | Node 深层设置测试 + Browser 全一级点击 + Rust owner 测试 | 本地完成；账号真同步待验 |
| 暗色／字号／主题 | 外观三项统一值列，每项可保存、正规化并重启读回 | device-local setting，非业务交换数据 | Node 几何／持久化测试 + `1440×900` 产品渲染；1000／700 仅内部防溢出 | 完成 |
| 响应式／错误恢复 | 唯一 `1440×900` 宽屏设计；1000／700 只验证同一 IA 最小可用；loading／empty／error／unknown／retry | 设置始终双栏、68px 行高；失败保留本地 owner 与显式重试，不自动重发 | Browser 1440 实渲染 + 内部几何门禁 + Node 错误状态测试 + Rust unknown／reopen 测试 | 完成 |

## 本轮修正

1. 新增 `audit-completion-inventory.mjs` 与 2 个行为测试，将可见 action 与 invoke 断链变成自动门禁。初次红灯暴露了不可达的 `settingsCenterCanvas`，已删除该旧设置画布和未使用的 `SETTINGS_REGISTRY`。
2. 删除死代码后，更新 typecheck 与 P10-A 测试，改为锁定当前可达的模型选择与 `show-settings` 入口，不再靠旧文案取绿。
3. Android 当前设置合同补入源码早已可达的“Google 账号与同步”，并用 `SettingsUiSimplificationContractsTest` 锁定。
4. 删除 `宽 ≤ 1180px`／`高 ≤ 820px` 的 `48px` 设置密度分支和 `mobile-home/detail` 第二套设置状态；把会话侧栏的离屏行为明确命名为 fallback。新增行为测试锁定唯一宽屏密度、同一双栏 IA 与内部最小可用门禁。

## 验收证据

### 自动门禁

- 本次唯一宽屏纠正门禁：Android 设置／模型专项 `24/24`、Desktop Node `161/161`、Rust `181/181`；lint、typecheck、inventory、protocol golden、static build 与 `cargo check` 全部通过。
- Android 指定设置合同测试：通过。
- Android `:app:testDebugUnitTest`：`1083` 个测试，`0` failures，`0` errors，`3` skipped；最终复跑通过。
- Desktop Node：`161/161`。
- Desktop Rust：`181/181`。
- lint、typecheck、protocol golden、static build、`cargo check`：通过。
- protocol golden：semantic SHA-256 `ad41c1ee6aa64b9e2f218034dbefccc333c43fb923b874b12ff51ce5972d4031`，package SHA-256 `74078bf5bdc1cbe53fbc4ac88029f8039b7aed9f8d0e79ea1f6c59ecf6aeb4ec`。

### 最新 macOS bundle

- 路径：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`
- 主程序：`31,010,112` bytes，SHA-256 `bd12323c4cc657fa1431fd0abfba7e1084ad224dc8ec2e8e1aac7e44010c1582`。
- `codesign --verify --deep --strict`：通过。`Identifier=com.nanzhufeng.ai.desktop`，`Signature=adhoc`，`TeamIdentifier=not set`。

### 隔离原生验收

- Bundle ID：`com.nanzhufeng.ai.desktop.compareacceptance.76356.mtjpxht5`。
- 数据根：`/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.FPGPqz`。
- 显式参数：`--diagnostic-ui-schema-acceptance`；诊断启动不读 Provider／账号凭据，不启动通知、历史调度、提醒派发或后台子进程。
- SQLite：`quick_check=ok`、`integrity_check=ok`、schema `37`、84 张表，foreign-key check 无结果。
- 空根读回：reminder plan／run、transcription task、read marker、selected sync 均为 `0`。
- 运行边界：子进程 `0`，TCP connection `0`；验收后精确结束 PID `76462`，退出后 SQLite 再次 `quick_check=ok`。

### 视觉证据

目录：`/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a060eb-322b-7dd2-b8da-1664f33e02de/nanfeng-ai-wide-only-20260902/`

- 13 张最新 static build 实交互截图全部为 `1440×900`：主界面、设置首页、模型与联网、模型设置、提醒设置、对话管理、导入导出、本机数据、项目与知识、开发与诊断、搜索、定时任务、南枫转写。
- 当前联系表 `contact-sheet-wide-only.jpg` 只汇总上述宽屏图，SHA-256 为 `5af2c675c75be5626065aca026272612a73a9c66efc6be839884951dd46b1de0`；`1000×800` 与 `700×900` 未生成／未混入公共视觉证据，只保留计算样式门禁结果。
- 原图实看无横向溢出、裁切、重叠或断层，console warning／error 为 0。Web 预览中依赖 Rust／SQLite 的功能如实 fail-closed。
- 旧审计目录中的 1000／700 图片只属于历史内部过程证据，不再代表当前产品设计。

## 仍需南烛枫单独授权的外部验收

| 边界 | 最小授权 | 可能费用／副作用 | 回滚 |
| --- | --- | --- | --- |
| 真实 Provider／网页搜索／GLM-OCR | 授权指定 Provider 与模型，用一条无敏感短 prompt 或一份合成小文档，并给出单次成本上限 | 按已配置账户实际 token／工具调用计费；内容会传给指定 Provider | 删除 QA 会话，撤销或更换 API Key，禁用 Provider |
| Google／南枫云同步 | 授权 QA 账号与一个指定的合成会话，不默认全库 | OAuth token 与加密文档会进入账号／云端；服务端可能有套餐或流量成本 | 登出、在账号端撤销授权，获得当次删除授权后删除 QA 云文档 |
| macOS 通知授权／点击 | 允许已签名 QA build 请求通知，创建一个合成短提醒并点击一次 | Notification Center 增加一条记录，系统权限状态改变 | 删除 QA 计划，在系统设置关闭通知 |
| Developer ID／Apple 公证 | 授权使用对应 Developer ID 身份并将指定产物上传 Apple 公证 | 如未具备账号可涉及 Apple Developer 资格成本；产物哈希与签名信息会上传 Apple | 本地仍可保留 ad-hoc QA build；证书可撤销，已完成的公证不应宣称可“撤回” |

## 未触碰边界

本轮未读写或重启正式 Desktop 进程／数据根，未读取 Provider／账号凭据，未发送网络请求或通知，未操作 OPPO，未运行任何 `connected*AndroidTest`。
