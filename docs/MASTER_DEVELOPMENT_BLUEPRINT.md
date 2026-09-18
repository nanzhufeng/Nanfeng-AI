# 南枫 AI 总控开发蓝图

> 工作区／对话区的数据隔离与人工调用以 [C-15 当前边界](C15_WORKSPACE_KNOWLEDGE_PARITY_CONTRACT.md#2-当前权威规则共用界面数据独立) 为准。下述“不存在独立本地工作区”仅否定本地／联网二选一的产品路径，不表示工作区与对话区可以共用会话数据。

> 2026-08-16 权威产品修订：南枫 AI 是“本地数据 + 联网能力”并列的统一产品，不存在独立本地工作区或“本地优先”路径。用户在应用内选择导入或执行即是操作授权；不得再以产品级二次确认阻断导入、普通执行或 Compare。权限、凭据隔离、最小外发、严格输入校验、费用/错误可见、原子性和 Agent 外部 authority 仍独立有效。

> 2026-08-23 普通聊天与附件外发修订：用户选择附件、预览及草稿阶段只允许本机处理；用户点击“发送”即授权把该准确已提交草稿中的仍存附件或必要解析结果发送给界面明确显示的当前 Provider/模型。普通聊天不得再弹逐条确认、勾选或二次确认；切换 Provider 不得静默转发，删除的附件不得出站，附件不得进入日志、统计或无关第三方。本文早期“逐次外发确认”仅适用于历史结构化高风险 Task，不得套用到当前普通聊天。

> 文档性质：全生命周期方向与阶段门禁的权威总图  
> 当前版本：1.3
> 基线日期：2026-08-12  
> 最近一次计划收口：2026-08-31

> **2026-08-31 唯一当前总控门（优先于本文全部旧阶段与日期记录）：** 当前 Android checkpoint 为 `d6db5bf checkpoint(android): stabilize model and media interactions`，Android Room 当前为 Schema 63。现行实现、回归、Release 和设备边界只读取 [当前交接](CURRENT_HANDOFF.md) 顶部；跨域完成范围、待验门和复查顺序只读取 [总控完成审计](MASTER_PLAN_COMPLETION_AUDIT_20260816.md) 顶部“2026-08-31 当前总控门”。本蓝图只继续负责长期方向、依赖顺序和停止条件，不再用早期 P 阶段清单判断当前代码是否完成。

> **现行合同路由：** Android 会话／搜索／文件操作／主题读取 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)，设置读取 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)，普通聊天上下文读取 [Android 当前运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)，模型选择与 Auto 读取 [P6-G 模型选择合同](P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md)，南枫转写读取 [GLM-OCR 当前合同](GLM_OCR_DOCUMENT_MARKDOWN_CONTRACT.md)，ChatGPT／Claude ZIP 读取 [P6-K 导入合同](P6K_CHATGPT_CLAUDE_ZIP_IMPORT_ADOPTION_CONTRACT.md)，费用读取 [AI 用量与费用合同](AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md)。历史截图、旧 APK、旧 Schema、旧测试数和旧“下一唯一入口”不得覆盖这些现行入口。

> **当前收口边界：** 已纳入同一 Android 产品体系的模型与 Provider、附件解析、南枫转写、统一搜索、私有文件预览、真实 owner 存储统计、费用与用量、会话“待看”和设置入口只做防回退，不重复实现。本 checkpoint 额外冻结：退役模型保留历史归因但请求失败关闭、生成任务在切换对话／界面时持续由会话 owner 管理、草稿与已发送附件共用本地预览投影、原图查看按真实溢出缩放与平移。完整 JVM 已为 `1042 tests / 0 failures / 3 skipped`，`lintVitalRelease` 与 `assembleRelease` 通过；具体产物、已覆盖 OPPO 包和未验的真机／真实 Provider 边界只读当前交接。Desktop 继续保持既有已验证基线，Android 新增能力不得自动写成 Desktop 已同步。

> **历史读取门：** 下方所有 2026-08-30 及更早的“当前状态”“当前计划”“下一唯一入口”和阶段数字都只作历史证据；与上方 2026-08-31 总控门冲突时一律失效。需要追根因时可读取，后续复查和排程不得从中恢复旧行为。

> **2026-08-26 历史计划覆盖层（已被 2026-08-30 总控门取代）：** Android 的当前可见产品以 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 与 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md) 为唯一正文；当前实现、构建、正式包与设备事实只读取 [当前交接](CURRENT_HANDOFF.md) 顶部和 [总控完成审计](MASTER_PLAN_COMPLETION_AUDIT_20260816.md) 的最新“当前总控门”。本文较早的“当前状态”、固定颜色、Dialog、设置入口、模型显示、功能审阅、数据导入或归档／回收站描述均是历史阶段记录，若冲突一律失效。

> **2026-08-26 历史产品收口范围：** 产品主线是“对话／工作双模式 + 连续会话 + 统一 Composer + 左侧会话管理 + 受控模型与联网 + 用户可控个性化／记忆 + 本地数据管理”。设置按“对话、应用与数据、工作区”组织；模型与联网、费用与用量、上下文记录、运行诊断各自分工；已归档与回收站独立管理；数据与存储区分对话导入和工作区导入／导出／备份。可见规则不再在阶段合同、交接或功能审阅入口重复维护。

> 2026-08-16 历史状态：用户已于 2026-08-12 确认总方案；P1 至 P2-M 与 P3-A 至 P3-H 的本地基线已完成，P4-A/P4-B 完成 Projects 与 metadata-only Context 选择，P4-C 完成长时 Memory 的显式本地 CRUD、来源/scope、暂停、软删除、历史和确定性冲突治理（Schema 9→10），P4-D 完成 L0–L2 的逐次显式正文选择与瞬时本机预览（L3 仍未实现）。P4-B metadata snapshot 继续不读取 `memorySources`；P4-D 不构造 Prompt/RunSpec 或 egress。P3 的真实流、Usage/费用、长会话性能与图片外发仍按用户决策并行后置，未通过且不得由 Mock/loopback/模拟器替代；P2-M 的授权动作在 HTTP 前因占位凭据安全阻止；图标已按用户最终确认从连续白底原始母版重做为受控 1.50× 同源资产交付，当前 Finder/Dock 与 Android emulator Launcher 表面已记录，但 OPPO/最终硬件图标结论仍待后续授权。P6 的本地 Desktop 基线持续独立：对话与工作都以真实对话为默认右侧内容，工作区只切换该对话 scope，项目/知识/记忆/记录是显式二级页；work-home/资产仪表盘不得作为工作模式默认。紧凑抽屉的打开、scrim、关闭和重开聊天壳已由最新 macOS `.app` 核验，消息长列表的独立滚动与重绘恢复已有定向合同；真实中段滚动及窄→宽连续 resize 已在本轮最新 bundle 完成。P6-E、P6-F、P6-F2-A~E、P6-G 本地基础与 FB-P6-023/024/025 已于 2026-08-14 完成，下一唯一阶段为 ChatGPT export JSON Adapter。P7-A 的跨端 E2EE 协议与 P7-B 的账号级本机 key/state 基座均已完成，但仍没有真实身份、网络、云端文档或同步。P8 已于 2026-08-13 经 P8-D 完成本地主体退出审计；P9-B 已完成 LOCAL_TEST_ONLY 合同/账本/harness 本地闭环，真实生态接入仍阻塞。最终产品固定为本地离线路径与显式授权的 Provider/账号同步路径并存；当前真实 Key/HTTP 仍待用户后测。
> P6-D 状态修订（2026-08-13）：经用户授权的 app-private 合成 fixture 已完成正式 UI 导入与 macOS 黑箱验证；64 条消息实际中段滚动、重绘恢复、drawer X、窄→宽自动收起与再次紧凑均通过。该本地 UI 阶段到此停止，不代表 P6、Desktop、P10-A 或总项目完成。
> P6 状态修订（2026-08-14）：双端 transcript 的角色/日期/时区/真实 metadata、plain-text copy、Android 明确确认 `ACTION_SEND`、typed idempotent Message Tree branch 与 restart readback 已按 `P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_EVIDENCE.md` 完成；Android 对既有可删除 fixture 的复制按钮实际点击后，系统 clipboard 回读为安全 `text/plain`，force-stop 后入口仍可读。Desktop 原生 share 没有安全 owner，按用户明确许可保持隐藏，未用伪分享替代。P6-F2-A/B/C/D/E 已分别按最终证据完成；E 已覆盖 Desktop native picker/private-copy/显式音频播放/restart 与 Android DocumentsUI/force-stop restart/最终签名 base hash，详见 `P6F2E_AUDIO_AND_GENERIC_FILE_ADAPTER_EVIDENCE.md`。P6-G 本地基础及 FB-P6-023/024/025 统一壳层已按 `FB_P6_023_024_025_P6G_UNIFIED_SHELL_EVIDENCE.md` 完成；下一唯一阶段是 ChatGPT export JSON Adapter。FB-P6-033 现已获得用户明确授权替换 launcher/Dock：仅可用不可变 master `a0335d3c…27f4` 直接派生，静态链通过，新的签名、安装/hash、Finder/Dock 表面仍在执行；未读 Key、未发 HTTP、未操作 OPPO 或发布。
> FB-P6-033 证据更新（2026-08-14）：新 master 的 Android adaptive/round/legacy 与 Desktop ICNS 静态链已通过；Android 新正式 Acceptance 包已 `install -r` 并与设备 `base.apk` 同哈希，AOSP emulator 抽屉只记录近似表面。Desktop 新 `.app` 已 strict-sign，内嵌 ICNS byte-identical 且 Finder 实际表面可见；`Dock`/`com.apple.dock` AX 读取两次超时且无可发现 target，按用户当前授权以 Finder+embedded ICNS 通过本轮非 OPPO Desktop 门。OPPO/ColorOS 仍是后续未授权真机门；FB-P6-026..032 和 P6-H 的全部非 OPPO UI/导入退出门已逐项关闭。
> FB-P6-025 产品哲学（2026-08-14）：南枫 AI 不是工程模块一比一映射的 AI 工具箱，而是用户交代事情后根据意图、对象与上下文组织能力的入口。此原则与 FB-P6-023/024 的四张参考图合并实施和验收：空态画布+唯一 Composer 是意图入口；按需附件/来源/metadata/actions/回底是渐进揭示；drawer 的 search/pinned/recent+底部 Settings 是连续任务与持久治理；聊天/工作只切对象范围与连续语境；轻顶栏和线性图标降低认知负担。模型 Auto 默认可直接发送，Composer 选择器只作当前会话可选 override；持久 policy/catalog 在 Settings。该原则不放宽本地优先、egress/账号/费用/Provider/Agent/tool execution 的独立安全合同，权威合同为 `CHAT_FIRST_INTENT_ORGANIZATION_CONTRACT.md`。
> 适用范围：Android 首发、Claude 级多模型对话、知识资产、Desktop、Agent、南枫生态、账号与同步、可选 AI Hub、发布与长期治理

> 多供应商模型编排优先级更新（2026-08-16）：用户提供的《南枫AI_多供应商模型编排架构_v1.0》已采纳为 P3／P6-G 后续高优先级专项方案，权威适配与实施入口为 `MULTI_PROVIDER_MODEL_ORCHESTRATION_ADOPTION_PLAN.md`。模型执行固定为 `DIRECT / COMPARE / AUTO` 三种同级模式；`Compare > Direct > Auto` 已确认仅为研发／架构优先级，普通对话未指定模型时继续默认 Auto。Provider 已确认采用统一核心、OpenRouter 首部署、原生 Adapter 一个一个验收。Compare MVP 已确认默认且最多比较 ChatGPT 与 Claude 两个逻辑模型，Deployment／Provider 在 Registry 中解析，禁止把动态实际模型 ID 写死。MM-O3-A 已新增未注册的 `DirectExecutionApplicationOwner`：它拥有精确 Direct 解析和一次性确认到 P3 preflight/coordinator 的正向应用边界，UI、AppContainer、Key/HTTP/Room/Usage 注册仍不属于它。当前仍不修改人工确认的 UI 布局，不接线真实 Provider，不读 Key、不发 HTTP。
> **P6-K 最高优先级覆盖（2026-08-16）**：用户要求 ChatGPT／Claude 官方 ZIP 数据包导入为真实本地会话、私有附件与兼容的资料/偏好候选。未知版本拒绝；绝不扫描、解压或读取未由用户明确选择的 ZIP，绝不覆盖会话、Key、Provider、账户或安全设置。P6-H/I/J JSON Adapter 只作为可复用任务模式，不等于 ZIP 支持。详见 `P6K_CHATGPT_CLAUDE_ZIP_IMPORT_ADOPTION_CONTRACT.md`。
> **P6-K K6/K7 状态（2026-08-16）**：Android Room Schema 35→36 与 Desktop SQLite Schema 17→18 各新增一个唯一的本地 profile/personalization owner。仅 `nfai.third-party-profile-personalization/v1` 的七项白名单（显示名、语言、时区、公开简介、自定义指令、主题、通知）可经 owner transaction 写入；task journal 只留状态/计数。owner 同事务写 receipt/provenance，幂等重放、冲突关闭、重开读取与批次撤销均有合成 fixture 合同；不构成账号、同步或 Provider 设置。两份真实包保持 `NO_SAFE_PROFILE_FIELDS / 0`，本轮没有用其重试或写入。K7 对同两包的匿名只读审计确认：ChatGPT 资产 identity 只在导出清单/逻辑文件目录出现，未落进任一已解析 message；Claude 没有媒体。媒体因此继续 `UNMAPPED_REJECTED`，没有附件/预览 owner 接合、UI 改动或真实文件写入。
> **P6-K K8 状态（2026-08-16）**：因 K7 无法自动采纳关系，Android Room **36→37** 与 Desktop SQLite **18→19** 只新增人工精确关联 receipt/provenance。Settings 仅投影匿名 ordinal、MIME、大小、状态及目标消息元数据；用户明确选择一个未关联资产和同批已导入 message 后，owner 才重验 archive entry path/hash/size/MIME、provenance、目标存在性、一次归属、幂等/冲突，并提取到既有 private attachment owner。Android 合成 archive 已证明 PNG/MP4/PDF 的目标 message attachment block、重开回读与既有 preview projection；migration 合同仍只证明 36→37 表结构。批次撤销任一步失败会保留 task/archive recovery entry，避免不可撤销会话。图片、视频、PDF 继续由既有 renderer 在该 message 原位显示；真实包没有自动关联或媒体展示，仍为 `UNMAPPED_REJECTED`。
> **P6-K K9 验收复核（2026-08-16）**：Desktop 已有真实系统 picker→直接提交→完整退出重开→安全 aggregate/receipt 回读（ChatGPT 23/90/23、Claude 162/880/162）；Android 当前是同语义的 strict parser/Room/owner 合同，尚无设备级可见 readback。K9 修复 Android Settings 暴露所选 ZIP 文件名，并令批次删除在 conversation、asset、profile、private archive/journal 全部成功撤销前保留 task 与重试回执。Desktop Node **80/80**、P6-K Rust **7/7**、Android 7 个定向类、lint/typecheck 均通过。真实媒体仍必须由用户明确选择资产和目标消息；不因 K8 或迁移表而自动关联。待 macOS 解锁、Android emulator/host 恢复后仅补各自正常入口的无敏感内容 readback。

## 0. 本文怎样控制后续开发

本文解决的不是“下一步写哪些代码”，而是固定南枫 AI 从第一版到长期形态的完整方向、顺序和停止条件。

后续工作遵循以下关系：

```text
用户当前明确指令 / 安全边界
        ↓
本总控蓝图（长期方向、阶段顺序、跨阶段不变量）
        ↓
产品简报 / 架构治理 / 领域规则 / 决策日志
        ↓
当前阶段实施计划
        ↓
具体任务、代码、测试与交付证据
```

- 本文是路线总图，不替代每一阶段的详细需求、设计合同和测试清单。
- UI 复查先读当前 UI 合同，功能／数据复查再读对应领域合同；总控蓝图只保留长期方向、阶段顺序、范围和停止条件，不复制卡片、颜色、文案、像素或弹窗规则。
- 当前只执行当前阶段，不为远期功能提前创建空模块、空页面或伪实现。
- 任何局部实现若与本文的长期不变量冲突，必须先停下，更新决策日志并重新确认。
- 模型目录、价格、依赖版本、商店规则和外部服务能力属于动态事实；实施时重新核验，不能把本文日期的快照当永久真值。
- 未经用户确认，不得以“总方案已经写好”为理由自动进入代码开发。

## 1. 总结论

南枫 AI 的固定发展路径是：

```text
治理与工程基础
→ 最小捕获与知识沉淀
→ Claude 级多模型对话主产品
→ 知识、项目、记忆与更多输入
→ Android 正式版与长期可维护基线
→ Desktop 控制中心
→ 可选账号与端到端加密同步
→ 受控 Agent
→ 南枫生态协议接入
→ 达到触发条件后再建设可选 AI Hub
```

### 双路径长期不变量（2026-08-13 用户确认）

- 本地离线是默认安全基线而不是产品终态：本地数据默认不外发，断网或未配置时仍提供诚实可用的本地工作路径。
- 联网 Provider 与账号同步是最终产品必须完成的受控路径：仅在用户显式配置、逐次同意、费用/失败可见且权限范围清楚时启用；Provider/同步状态不得伪装成本地成功。
- 两路状态和 UI 必须清楚区分：未配置、离线本地、已授权待发送、联网执行、失败/可恢复和同步冲突各自可见；断网只能降级本地，不能静默扩大缓存、授权或外发。
- 当前真实 OpenRouter Key/HTTP 由用户决定后测，故当前阶段仍不得读 Key/发请求；但真实 Provider/账号同步不再是可以永久跳过的外部门，后续须以独立合同与真实证据完成。

### P10-A 双路径状态与配置底座（2026-08-13）

- Android/Desktop 共享 `ConnectionCapability`、Provider 配置、Credential presence、Catalog freshness、逐次 egress consent、Sync capability 与 degraded reason 的同语义状态词汇；Key 仅可显示存在性，不能进入 UI 状态、日志、SQLite 或同步。
- `LOCAL_OFFLINE` 与 `ONLINE_PROVIDER`、`LOCAL_ONLY` 与 `ENCRYPTED_SYNC` 是两组独立选择和授权。联网失败后可推荐本地继续，但不得把原 ONLINE 请求伪装为 LOCAL 成功或自动外发数据。
- 当前 P10-A 只允许状态/配置面和 release-excluded fake/loopback 合同；真实 Provider/同步仍按 P2/P7 的独立真实服务门验收。
- Desktop 默认信息架构必须 chat-first：参考 ChatGPT/Claude 的轻侧栏、单一对话画布和清晰输入入口；Project/Knowledge/Memory/Inspector、Agent/生态审计与连接详情只能由“工作”或底部设置显式进入，不能再作为默认首页或一级导航堆叠。双路径状态应在输入区/设置中简洁呈现，不另造工程仪表盘。

其中最重要的优先级约束是：

1. 捕获闭环先做，是为了尽早建立用户拥有的数据资产，不是把南枫 AI 定位成单纯收藏工具。
2. 捕获闭环一旦达到退出门槛，立即进入用户最需要的 Claude 级多模型对话，不继续横向扩充所有输入类型。
3. Android 原生先行；未来 Desktop 复用领域语义和版本化协议，不强求复用 Compose UI 代码。
4. V1 本地单用户、Provider 直连；账号、云同步、远程 BFF 和 AI Hub 都不能成为 V1 的前置条件。
5. OpenRouter 是首个真实 Provider，Claude 是第一测试模型族；这不等于永久绑定 OpenRouter 或 Claude。
6. Provider、Model、Harness、Task、Conversation、Knowledge、Memory 和 Invocation 始终分离。
7. 多模型执行的 Direct、Compare、Auto 是同级模式；用户指定目标时 Router 不得介入，Compare 默认不综合且不污染共享上下文，Auto 仅在用户未指定目标时生效。
8. AI 生成内容默认是候选，不静默覆盖用户原文、知识真值或其他南枫应用的业务数据。
9. 每阶段都必须用真实证据退出，不能用页面存在、HTTP 200、构建成功或模拟数据替代完整链路。

## 2. 产品北极星与非目标

### 2.1 产品北极星

南枫 AI 是本地优先的个人多模型 AI 工作台，也是个人信息、对话、知识、记忆、模型和受控自动化的统一入口。用户应当能够：

- 在手机上随时捕获、分享、提问和整理；
- 在 Claude 级对话体验中自由使用和比较不同模型；
- 清楚知道内容发送给谁、使用哪个实际模型、消耗多少、为何失败；
- 把有价值的内容沉淀为自己拥有、可核对、可搜索、可迁移的资产；
- 在桌面端管理更复杂的项目、知识、模型、成本和工作流；
- 在明确授权、可预览和可撤销的前提下，让 Agent 执行受控任务；
- 逐步连接南枫系软件，同时保持各应用的业务数据所有权和独立可运行能力。

### 2.2 永久非目标

- 不做只换皮的单模型聊天套壳。
- 不把“Claude 级”误解为必须复制 Claude 的品牌、界面或内部实现。
- 不让 AI Hub 成为所有南枫应用的业务数据库、知识库本体或启动单点。
- 不把所有 Provider 强行压进同一个 OpenAI 请求结构。
- 不把云端登录或同步设为使用本地核心能力的强制门槛。
- 不把原始 Prompt、附件、图片、个人资料或 API Key 当普通遥测上传。
- 不在没有用户确认和确定性校验时自动写账、删文件、覆盖资料或执行其他高风险动作。
- 不为了“架构完整”提前建设没有生产消费者的空壳模块和远程服务。

## 3. 已确认约束、规划判断与动态决策

### 3.1 已由用户确认，不得自行改动

| 主题 | 已确认方向 |
|---|---|
| 产品顺序 | 先完成包含图片的最小捕获闭环，随后立即转向 Claude 级对话主产品 |
| 首发平台 | Android |
| Android 技术方向 | Kotlin + Jetpack Compose |
| V1 部署 | 本地单用户、Provider 直连 |
| 首个真实 Provider | OpenRouter |
| 第一测试模型族 | Claude |
| V1 排除 | 登录、云同步、远程 BFF、远程 AI Hub、Desktop、复杂 Agent |
| 开工门槛 | 完整总方案先确定，再创建工程或修改代码 |

### 3.2 本蓝图固定的工程判断

| 主题 | 总体方向 | 为什么这样定 |
|---|---|---|
| 本地业务真值 | Android 使用 Room/SQLite；附件使用 App 私有文件目录 | 适合结构化本地数据、关系、迁移、离线与 Android 生命周期 |
| 跨端复用 | 先复用领域合同、数据协议和行为测试，不追求首版共享 UI 或共享运行时 | 避免为了未来 Desktop 拖慢 Android，同时防止语义分叉 |
| 凭据 | Android Keystore 保护的独立凭据存储 | Key 不进入 Room、日志、导出或普通备份 |
| Provider 接入 | Mock + OpenRouter 先行，按能力缺口再加原生 Adapter | 先验证业务闭环，再用真实需求决定第二 Adapter |
| 模型目录 | 远端目录快照 + 能力验证 + 本地最后可用版本 + 回滚 | 模型 ID、价格和能力会变化，业务不能硬编码 |
| 对话存储 | Conversation + Message Tree + Invocation 分离 | 支持分支、换模型重答、历史事实和费用精确追踪 |
| 附件 | 元数据入库、二进制放私有文件目录、内容提取物版本化 | 避免把大文件塞入数据库或 Prompt，并支持未来迁移 |
| AI 结果 | 先 Candidate，人工确认后才成为 Knowledge 或记忆 | 保持来源真值、可解释和可撤销 |
| 云同步 | 本地数据库继续是真值；云端只存版本化 AES-GCM 加密快照 | 云能力不能改变本地优先和隐私承诺 |
| AI Hub | 满足明确触发条件后建设，应用保留本地直连和最后可用配置 | 防止过早引入服务器、运维和单点依赖 |

### 3.3 实施时动态确定，不写死在总方案

- Kotlin、Compose、AGP、Android API 和依赖的具体版本。
- OpenRouter 当前可用的 Claude 实际 Model ID、视觉能力、上下文和价格。
- 第二、第三个 Provider 的具体接入顺序。
- Desktop 使用 PWA、Tauri 或其他容器的最终技术栈。
- 云端部署服务、Supabase 项目和 OAuth 配置。
- AI Hub 的部署位置、运行规模和成本预算。
- TTFT、缓存命中、成功率、成本和耗电的正式 SLO 数值。

这些选择必须在对应阶段开始时，以官方资料、当前真实能力、原型和基准数据重新决策。

### 3.4 GitHub 成熟项目经验怎样进入本项目

2026-08-12 已完成公开 GitHub 成熟项目研究，证据矩阵与许可证边界见 `GITHUB_MATURE_PROJECT_REFERENCE_RESEARCH.md`。研究不改变产品顺序，而是强化每阶段的工程合同：

- Android 产品形态参考 RikkaHub、PocketPal AI 的原生多模型与设备性能经验；不 Fork、不复制 AGPL 源码。
- Claude 级对话参考 LibreChat 的分支、可恢复流、草稿与导入导出经验。
- 捕获与知识参考 Memos、AnythingLLM 的即时输入、核对后保存、可恢复文档队列和 Workspace 范围。
- Provider、成本和运行证据参考 LiteLLM、Langfuse 的 Adapter、价格版本、调用树和 Eval 分层。
- 记忆与回归参考 Mem0、Promptfoo 的显式 CRUD、历史、作用域、Fixture、断言和红队门禁。
- Agent 参考 LangGraph、OpenHands Agent SDK 的 Durable Run、Checkpoint、Human-in-the-loop、风险等级、UNKNOWN 失败关闭和沙箱边界。
- 跨端与恢复参考 AppFlowy 的导入导出、迁移、回收站和同步回归；南枫 AI 仍坚持本地真值与加密快照，不共享数据库文件。

采用原则是“能力级参考、合同级吸收、自主实现”。研究链接不构成依赖批准；AGPL、社区许可证、修改版许可证和企业目录代码默认只作设计研究，任何源码复用必须另做文件级许可证审计。

## 4. 完整能力地图

| 能力域 | 最终目标 | 首次进入阶段 | 数据所有者 |
|---|---|---|---|
| 捕获 | 文本、系统分享、图片，后续文件、网页、语音、视频 | P2 | Capture Domain |
| AI 整理 | 结构化候选、校验、编辑、确认保存 | P2 | Candidate / Knowledge Domain |
| 多模型对话 | 流式、停止、继续、重试、分支、换模型、附件、恢复 | P3 | Conversation Domain |
| Provider 管理 | 凭据、目录、模型预设、能力、健康和 Adapter | P2 | Provider / Model Registry |
| Harness | Prompt、Context、Memory、Tools、解析、验证、缓存、Fallback | P2 起步，P4 完整化 | Harness Registry |
| 调用与成本 | Invocation、Token、费用、错误、缓存和成功任务成本 | P2 | Invocation Ledger |
| 知识资产 | 浏览、详情、搜索、标签、来源、关系、导入导出 | P2 起步，P4 完整化 | Knowledge Domain |
| Projects | 项目知识、项目指令、会话集合和范围隔离 | P3 | Project Domain |
| 记忆 | 会话记忆与长期记忆，可见、可控、可撤销、带来源 | P3/P4 | Memory Domain |
| 上下文 | 分层、检索、裁剪、摘要、压缩、缓存稳定前缀 | P3/P4 | Context Builder |
| Daily Assistant | 用户可控的摘要、提醒与建议 | P4 后评估 | Assistant Task Domain |
| Desktop 控制中心 | 深度对话、知识管理、模型和成本、工作流设计 | P6 | Desktop Client |
| 账号与同步 | Google/Supabase 身份、恢复码、加密快照、冲突选择 | P7，可条件提前 | Account / Sync Domain |
| Agent | 计划、工具、运行、暂停、确认、回滚和审计 | P8 | Agent Runtime |
| 南枫生态 | 各应用通过稳定协议提供数据和动作 | P9 | Integration Contracts |
| AI Hub | 目录、策略、健康、成本聚合和可选 Agent Runtime | P10 | Hub Control Plane |
| 导入导出迁移 | 版本化包、哈希、附件引用、协议升级与回滚 | P2 起步，持续 | Portability Domain |
| 诊断与发布 | 结构化错误、可导出诊断、真实验证、签名交付 | P1 起步，持续 | Diagnostics / Release |

## 5. 总体架构与依赖方向

### 5.1 逻辑架构

```text
Android / Desktop / Share / System Inputs
                    ↓
          Application Use Cases
                    ↓
  ┌─────────────────┼──────────────────┐
  ↓                 ↓                  ↓
Domain Contracts  AI Task Contract  Portability Contract
  ↓                 ↓                  ↓
Repositories      Harness Runtime    Import / Export / Sync
  ↓                 ↓
Room + Files      Provider Adapters
                    ↓
             Provider / Model

可选增强：Local Registries ←→ AI Hub Control Plane
```

固定依赖规则：

- Client 只依赖 Application 与只读领域模型，不直接访问 Room、Keystore 或 Provider HTTP。
- Application Use Case 是外发确认、任务启动、结果校验、候选保存和幂等性的唯一编排者。
- Domain 不依赖 Android UI、具体数据库、具体 Provider 或未来 Hub。
- Harness 使用 Task、Capability、Context 和 Validation 合同，不决定业务数据是否成为正式事实。
- Provider Adapter 负责协议、流式事件、错误和用量映射，不解释“是否保存为知识”。
- Sync、Export 和 Desktop 通过版本化协议读取同一事实，不从 UI 文案反推业务值。
- Hub Adapter 是可选基础设施实现，不能反向成为 Domain 或 App 启动依赖。

### 5.2 Android 首版物理组织原则

开始时只创建有实际职责和测试消费者的模块。推荐逐步形成：

- `app`：Android 入口、导航、依赖装配。
- `core-domain`：领域类型、规则与端口。
- `core-data`：Room、文件、迁移、导入导出实现。
- `core-ai`：Task、Harness、Registry、Adapter、Invocation。
- `core-ui`：稳定令牌、共享状态组件和自适应外壳；有复用后再建立。
- `feature-capture`、`feature-chat`、`feature-knowledge`、`feature-settings`：在对应阶段有真实页面和用例时创建。

不允许为了目录看起来完整而提前创建 Desktop、Hub、Agent 或 Sync 空模块。

### 5.3 Core 的跨端含义

“Core”首先是统一语义，不是必须部署的远程服务，也不要求 Android 与 Desktop 在第一天共享同一个二进制包。

跨端一致性通过四类资产保证：

1. 版本化数据协议与 Schema；
2. 稳定的领域行为和状态机文档；
3. 可移植的 JSON/附件测试夹具和合同测试；
4. Provider/Harness/错误/用量的统一内部 IR。

如果以后出现真正共享 Kotlin Multiplatform 或服务端 Core 的收益，再独立立项，不把它作为 Android V1 前置条件。

## 6. 核心数据资产与演进合同

### 6.1 数据资产

| 资产 | 必须保存的核心事实 | 禁止混入 |
|---|---|---|
| Capture Draft | 原始输入、来源、附件引用、编辑状态、时间 | 已生成知识正文、Provider 凭据 |
| Source Evidence | 来源类型、时间、原引用、贡献字段 | UI 展示回退值 |
| Attachment | 私有文件引用、MIME、大小、哈希、来源、提取版本 | 大二进制直接塞入普通表 |
| Generated Candidate | 任务、结构化候选、验证状态、人工编辑 | 未确认即标记正式知识 |
| Knowledge Item | 用户确认内容、来源链、版本、关系、删除状态 | API Key、完整网络调试正文 |
| Conversation | 标题、当前分支、项目、归档/置顶状态 | 完整消息数组复制体 |
| Message Node | 父节点、角色、内容块、状态、版本、创建者 | 静默覆盖旧节点 |
| Invocation | Provider、Model、Harness、Token、费用、错误、耗时 | Key、原图、完整敏感 Prompt |
| Project | 项目指令、知识范围、对话集合、策略引用 | 全局记忆的隐式副本 |
| Memory Item | 内容、范围、来源、置信、状态、创建方式 | 密钥、证件、银行卡、他人隐私 |
| Tool / Agent Run | 输入摘要、批准、步骤、结果、回滚、审计 | 未授权的外部动作 |
| Provider / Model Snapshot | 来源、实际 ID、能力、价格、验证状态、更新时间 | 业务代码中的永久硬编码 |
| Export / Sync Manifest | 协议版本、文件清单、哈希、引用、创建信息 | Key、Token、普通日志和头像缓存 |

### 6.2 标识、删除和审计

- 核心对象使用稳定、不依赖数据库自增顺序的 ID，以支持导出、跨端和未来同步。
- 每个用户可编辑资产拥有创建时间、修改时间、Schema 版本和来源。
- 删除默认采用可恢复语义；附件垃圾回收必须确认没有有效引用并经过保留期。
- 消息编辑产生新节点；知识修订产生新版本或明确更新记录，不篡改调用历史。
- AI Invocation 是发生过的历史事实；业务内容删除后按隐私策略删除正文引用，但费用与安全元数据的保留规则需明确。

### 6.3 Schema、迁移和兼容

- 数据库迁移、导出协议、Harness Profile 和 Model Snapshot 各自独立版本化。
- 升级前必须备份或具备可恢复迁移路径；失败时不能用清库代替迁移。
- 导出包使用 Manifest + 结构化数据 + 附件目录 + SHA-256 清单。
- 新版本默认读取最近支持的旧版本；不支持时明确提示，不静默丢字段。
- Desktop、Sync 和生态集成只能依赖协议值与稳定 ID，不能依赖 Android 表名、文件绝对路径或中文展示文案。

## 7. Provider、Model、Harness 与路由总方案

本章的 Router 只属于 `AUTO` 模式。`DIRECT` 与 `COMPARE` 由 `MultiModelOrchestrator` 生成明确目标计划，不经过 Auto Router。Logical Model、Deployment 与 Provider Endpoint 分离；Compare 的各分支共享同一 Canonical Context Snapshot，并分别形成 Invocation/Attempt/Usage 事实。详细不变量、适配矩阵和 MM-O1～MM-O6 退出门见 `MULTI_PROVIDER_MODEL_ORCHESTRATION_ADOPTION_PLAN.md`。

### 7.1 概念分离

```text
Task：要完成什么，能力/预算/验证要求是什么
Provider：通过谁调用、怎样认证和传输
Model：实际使用的模型资产与能力
Harness：怎样组织 Prompt、Context、Tools、解析、验证和恢复
Invocation：这一次实际上发生了什么
```

任何页面、网络层或数据表都不能用一个 `model` 字符串代替上述全部概念。

### 7.2 Provider 接入顺序

1. **Mock Provider**：无密钥证明业务合同、状态机和错误恢复。
2. **OpenRouter Adapter**：首个真实 Provider；第一批真实文本和图片优先 Claude。
3. **Anthropic 原生 Adapter 评估门**：只有当 OpenRouter 无法提供目标 Claude 原生能力、可靠性或可解释用量时才进入。
4. **第二个非 Claude 路径**：按用户真实任务、中文能力、成本和国内可用性基准选择，不预先承诺厂商。
5. **更多 Provider**：一次只新增一个，通过完整接入清单后再开放给普通用户。

“首批接入 Provider”指按上述顺序逐个完成真实协议、密钥、能力、错误、用量、成本和回归验收，不是一次把所有模型厂商都开发完。OpenRouter 已把首批真实工程量收敛为一个 Adapter；Claude 是其第一模型验证对象。

### 7.3 Provider 完成清单

每个真实 Provider 必须同时完成：

- 独立 Adapter 和凭据配置；
- 模型目录、能力矩阵、上下文、流式、视觉、工具、结构化输出和推理差异；
- 超时、限流、认证、余额、服务故障、格式和取消的统一错误映射；
- 输入/输出 Token、缓存、费用和缺失值语义；
- 最小真实文本、图片和所声称能力的验证；
- Harness 兼容、基准任务、Fallback 资格和历史记录；
- 本地最后可用目录、失效提示和回滚方案；
- API Key 泄漏扫描与日志审查。

完成一个 Provider 是中等规模集成工作；不是添加一个下拉选项或更换 Base URL。

### 7.4 Model Registry

- Android 模型设置只管理 OpenRouter、Qwen、DeepSeek 三个服务的 API Key、启用、模型与测试连接；普通设置不再以“旗舰／均衡／快速”抽象预设替代真实模型目录。Composer 只显示短模型名，模型选择、设置、费用、上下文与诊断使用完整目录名；具体显示规则只读当前 UI 合同。
- 实际 Model ID、价格、上下文、视觉、流式、工具、结构化输出、缓存和状态来自版本化快照。
- 目录更新采用“拉取 → 比较 → 验证 → 发布 → 保留上一稳定版”，不能远端变化后立刻无验证覆盖。
- 历史 Invocation 永远保存当时实际 Provider、Model ID、快照和价格版本。
- 高级模式未来可开放更多模型与 Harness，但不得以任意文本框替代 Registry 真值。

### 7.5 Harness 成熟路线

| 等级 | 能力 | 进入阶段 |
|---|---|---|
| H0 | 版本化 Prompt、结构化输出合同、解析、确定性校验、统一错误 | P2 |
| H1 | 对话上下文、流式事件、取消、重试、分支、项目指令 | P3 |
| H2 | L0–L3 分层、摘要/压缩、检索、缓存稳定前缀、可见记忆、Fallback | P4 |
| H3 | 工具调用、Agent Loop、预算、风险确认、断点恢复和 Eval | P8 |
| H4 | 跨应用策略、Hub 路由、组合基准和自动 Harness 选择 | P10 |

结构化捕获可使用非流式请求以简化严格解析；Claude 级聊天必须支持真实流式事件。两者不能因为都“兼容 OpenAI”而共用错误的单一调用模式。

### 7.6 路由与 Fallback

- 用户明确选择优先级最高；会话内默认保持模型粘性。
- 自动路由只在用户允许的范围内，根据 Task、Capability、Provider 状态、Harness、预算和敏感度选择。
- 自动升级、降级或换 Provider 必须在结果和调用记录中可见。
- 认证、余额和权限错误不得盲目 Fallback；格式、验证和暂时服务故障按策略处理。
- 预算按“成功任务成本”控制，包含重试、Fallback、缓存和失败浪费，不只比较标价。
- Adapter 把 Provider 原始事件、错误和用量转换为版本化内部 IR；UI、Knowledge 和 Conversation 不解析 Provider 原始 Chunk。
- 价格为 `null` 表示未知，价格为 `0` 表示已验证免费或本地；两者在存储、预算和界面中不得混同。
- 流中断后默认保留部分结果并创建新 Invocation 重试；只有目标协议、上下文语义和用户策略都允许时才可中途 Fallback，不能伪装成无缝续写。

## 8. Claude 级对话的固定产品合同

“Claude 级”是体验与可靠性目标，由以下能力共同构成。

### 8.1 生成生命周期

```text
草稿 → 已提交 → 等待首字 → 流式生成
                         ├→ 用户停止 → 保留部分结果
                         ├→ 网络中断 → 可恢复或明确失败
                         ├→ Provider 失败 → 可重试/可选择 Fallback
                         └→ 完成 → 用量与费用结算
```

- 停止必须真正取消网络与后续写入，不把“按钮变灰”当取消完成。
- 继续、重试、换模型重答都创建新的 Invocation，并清楚关联原消息。
- 部分输出、失败和取消的 Token/费用按 Provider 实际信息记录；无法获得时为 `null`。
- 重启、页面切换和进程回收后，草稿与已完成内容不丢；运行中状态必须恢复为真实可解释状态。
- 流事件采用统一事件包，至少包含 `runId`、`invocationId`、`sequence`、`eventType`、`timestamp` 和 `payloadVersion`；乱序、重复和断档都有明确处理。
- 本地已提交事件是运行事实，UI 只消费投影；重连先从本地最后序号恢复，无法恢复时明确转失败或由用户启动新 Invocation。

### 8.2 消息树

- 编辑用户历史消息创建新分支，不覆盖原分支。
- 助手重试和换模型回答形成同父节点的不同版本。
- Conversation 保存当前分支指针；Context Builder 只读取当前有效路径。
- 分支切换、删除、导出和项目迁移都必须可复现。

### 8.3 附件与长内容

- 附件是独立资产，消息保存引用和使用范围。
- 对话加入附件默认且固定为 local-only/no-egress；附件资产域独占私有路径、二进制、哈希与受控解码，对话草稿/消息/导出仅含稳定 ID 与安全元数据。外发必须另经逐次内容范围、Provider/模型、费用与能力确认，不能由“已加入对话”推断。
- 小附件可直接作为上下文；长文件先解析、分块、索引或摘要，不能无差别全文塞入每轮 Prompt。
- 解析内容被视为不可信输入，不能覆盖系统规则、项目指令或用户授权。
- 原文件、提取文本、摘要和索引分别版本化，能解释某次回答使用了哪一版。

### 8.4 Projects 与指令层级

指令优先级固定为：

```text
当前安全与用户明确指令
→ Project Bible / 领域规则
→ 项目级指令
→ 用户全局偏好
→ 已确认记忆
→ 产品默认
```

Projects 至少管理：项目指令、知识范围、会话集合、默认模型/Harness 和导出边界。项目不能隐式读取其他项目私有资料。

### 8.5 记忆

- 工作记忆：当前任务和近期状态，随上下文压缩；会话记忆：当前 Conversation 内可复用摘要；长期记忆：用户可见、可编辑、可删除、可暂停、可撤销、带来源和适用范围。
- 当前用户入口是“个性化 → 启用记忆／记忆摘要”。记忆摘要先载入用户确认的初始版本，后续只增量整理，不能以空摘要覆盖。
- 自动建议只针对少量明显影响未来回答的个人事实、偏好、长期计划或决策；必须由用户确认后写入。用户输入“记住了”时，结合当前对话整理其明确要保留的内容。默认不自动保存高敏信息、第三方内容、网页指令或工具结果。
- 记忆字段、草稿保存、摘要版式与用户可见交互只读当前设置 UI 合同；领域层继续保证来源、范围、版本、冲突、暂停、删除和撤销语义。

### 8.6 性能目标

- TTFT、总耗时、Token/s、滚动帧率、长对话内存、缓存命中和成功任务成本都建立真实基线。
- “首字小于 800ms”等外部方案数值只作为候选目标；没有真实网络、设备和模型数据前不承诺。
- 长列表使用虚拟化和增量渲染；代码块、Markdown、表格和公式不得因流式更新频繁重排整个会话。

## 9. 知识、输入与个人助理演进

### 9.1 最小知识闭环

```text
文本 / 系统分享 / 图片
→ 本地草稿与私有附件
→ 显示实际 Provider、Model、内容范围和费用提示
→ 用户逐次确认外发
→ AI 结构化候选
→ Schema + 确定性规则校验
→ 用户核对或修改
→ 正式 Knowledge Item
→ 再次读取与导出
```

图片已进入首个闭环。入口范围按风险逐步开放：相册选择作为首选基线，系统图片分享和拍照在同一数据合同下分别完成权限、取消、临时文件与真机验证后开放。

### 9.2 知识资产完整化

捕获闭环后先转对话；知识完整化在对话核心可用后继续，包括：

- 列表、详情、编辑、归档、回收站；
- 全文搜索、标签、时间线和来源筛选；
- 去重候选、关系建议和人工确认归并；
- 对话消息转知识、知识引用回对话；
- Markdown / JSON / 附件导入导出；其中 JSON 先作为独立、版本化的 `nfai.knowledge.json` Adapter（严格 UTF-8/Schema、私有副本、队列、逐项确认、ID 重映射、原子回读 hash）；不得把任何格式解析器泛化为不透明入口。
- 文件、网页、语音、视频逐个输入 Adapter；
- 每种新输入都复用 Capture、Evidence、Attachment、Consent 和 Candidate 合同。
- 文件、网页和批量资料进入可恢复任务队列，真实展示逐项阶段、已完成量、失败原因、取消和重试；页面退出不应让任务静默消失。
- 原文件、提取物、分块、摘要、Embedding 与索引分别版本化；解析或嵌入失败不得用空结果伪装成功。
- 捕获保持“打开即可输入”，复杂标签、关系和分类优先放到 Candidate 核对之后，避免阻塞高频输入。

### 9.3 Daily Assistant

每日摘要、提醒和建议只在数据质量、通知权限和用户价值有证据后进入：

- 默认先生成可预览候选，不自动写长期记忆或其他应用；
- 通知频率、时间和数据范围由用户设置；
- 没有新内容时不制造虚假总结；
- 关闭后停止后台生成和通知，不保留隐形任务。

## 10. UI、信息架构与自适应方向

### 10.1 Android 信息架构

当前 Android 导航采用对话／工作双模式、左侧会话抽屉和独立设置。设置首页按“对话、应用与数据、工作区”分组：对话包含个性化、模型与联网、提醒、对话管理；应用与数据包含外观、数据与存储、隐私与安全、关于；项目与知识、开发与诊断位于工作区且整体置底。具体页面、名称、层级与视觉只读当前设置 UI 合同，不能因早期原型再冻结另一套一级导航。

### 10.2 工作台空间原则

- 对话、列表、知识画布和主编辑区优先获得剩余空间。
- 设置、摘要、输入方式和帮助说明保持紧凑，删除只重复控件含义的文案。
- 空状态仍保留完整工作区骨架和稳定输入位置。
- 运行、等待、停止、失败、取消、重试、完成和离线都拥有真实状态与恢复操作。
- 失败任务留在原位置，显示中文问题和下一步，不静默消失。
- 页面切换即时完成；局部选中、流式、进度和展开使用短促反馈。

### 10.3 紧凑与展开形态

| 形态 | 主要布局 |
|---|---|
| 手机紧凑屏 | 单主画布，历史/项目/详情以独立页面或可恢复层进入 |
| 折叠屏/平板 | 列表 + 主内容双栏，必要时详情第三层，状态与输入不重复 |
| Desktop | 导航/项目 + 主对话/知识画布 + 可折叠详情/模型面板 |

宽屏不是手机页面机械拉宽，手机也不是桌面三栏强压成单列。每种形态建立独立视口合同和截图基线。

### 10.4 视觉与组件约束

- 以南枫自适应工作台负责结构、密度、状态与响应式；以南枫 UI 体系负责皮肤、材质和组件几何。浅色前景为纯白、页面为稍压暗中性灰；暗色为统一深炭灰层级，禁止大面积纯黑、白卡或黑字残留。
- 可交互表面、阴影、按压、水波纹、悬停和焦点服从同一轮廓；单行控件为胶囊，所有黑边框移除。主题色从“外观”全局驱动，用户气泡使用低饱和的更暗主题派生色。
- 正常设置入口通过层级页面进入；只在直接选择值／输入内容时使用完整、可关闭的选择面。保存、复制、删除和提交保留清晰文字或语义图标。
- 当前数值、标题布局、模型短／全名分流、Composer 聚焦展开、搜索皮肤、图标和手势只读两份当前 UI 合同。UI 设计通过、模拟器通过、真机视觉通过和功能链路通过必须分开报告。

## 11. 账号、加密同步与多设备演进

账号与同步不进入 V1，但必须现在固定未来不会破坏本地优先的方向。

### 11.1 固定边界

- Google Credential Manager 只完成 Google 身份确认；Supabase Auth 是应用账号身份。
- 本机 Room 数据库始终是业务真值；云端只保存版本化、AES-GCM 加密的结构化快照。
- Google ID Token、Client Secret、`service_role`、恢复码、API Key、真实账号和业务明文不写日志、不进 Git、不进诊断包。
- 每个南枫应用使用独立 `appId / documentId / AAD`，不能把多个应用业务文档混在一个快照。
- 头像缓存、Provider Key 和本地临时文件不进入业务同步包。

### 11.2 用户数据方向

- 新空设备：登录后使用恢复码解封装密钥，再下载、校验和恢复。
- 本机非空或远端非空：明确选择保留本机、使用远端或按已定义规则合并；禁止静默覆盖。
- 切换账号：先完成恢复码和数据方向选择，后台同步不能绕过。
- 退出：默认退出会话、停止同步并保留本地数据；“退出并清空”独立二次确认。
- 冲突：展示范围和选择，没有可解释合并规则时停止写入。

### 11.3 同步调度

- 只有真实业务变更才触发约 30 秒合并的唯一后台任务；另设低频周期兜底。
- 冷启动、打开账号页、恢复头像或只读身份资料不自动排队业务同步。
- 同步状态真实区分生成快照、比较远端、加密、上传、回读校验、冲突和失败。
- 大库按稳定顺序流式生成快照和哈希，避免在内存保留两份完整大字符串。

### 11.4 进入条件

满足下列条件才启动账号同步阶段：

1. 本地 Schema、导出协议和附件引用已稳定并经过迁移测试；
2. 用户确有跨设备恢复或 Android/Desktop 共享数据需求；
3. 恢复码、冲突、账号切换和退出保留数据的交互合同已确认；
4. 有真实 Supabase、Google OAuth、真机和密文回读验证条件。

## 12. Desktop 控制中心

### 12.1 产品职责

Desktop 不是简单放大版手机，而是深度工作与管理中心：

- 多会话、多项目和多知识源管理；
- 长文写作、代码、文件和多面板对话；
- 模型目录、Harness、调用、成本和基准比较；
- 知识关系、批量导入导出和质量修复；
- Agent 工作流设计、运行观察和审计；
- 本地数据迁移以及启用账号后多设备状态。

手机继续负责快速捕获、查询、分享和轻量确认；两端不强行拥有完全相同的页面密度。

### 12.2 技术决策门

Desktop 开工前比较：

- PWA：部署简单、跨平台，系统能力和本地文件集成需验证；
- Tauri：本地能力强、包体较轻，Rust/前端双栈维护成本需验证；
- 其他原生或跨端方案：只有在真实需求明显优于上述方案时进入。

最终选择依据是本地数据库与附件访问、离线、性能、安全、升级、Windows 体验和维护成本，不按早期资料直接锁定。

### 12.3 数据接入顺序

1. 首先通过版本化导入导出包实现 Android 与 Desktop 精确保真迁移。
2. 再根据真实频率判断是否需要局域网直连或加密云同步。
3. 不通过共享不稳定数据库文件或硬编码 Android 路径实现跨端。

### 12.4 P6-B 已实现基线与下一门

- Tauri 2 Desktop 的 SQLite 是 Desktop 自己的本地真值；只通过 `nfai.exchange.v1` 语义 IR 迁移，不读取 Android Room。
- import 先进入私有 staging 严格 preflight，用户看见项目/会话/知识/记忆/关系/资产摘要后只能新建独立 workspace；非空 workspace 不 merge/覆盖。
- export 从 Desktop SQLite 生成 canonical 包并同文件回读；P6-B 保持 Project/Conversation/Knowledge/Memory/relation 只读，避免在没有 revision、undo、soft-delete 与 transaction 合同前伪造完整 CRUD。
- P6-C 再决定完整本地写入、模型/成本 metadata、workspace 管理和工作台深度；账号同步、Agent、Hub 与真实 Provider 仍不因此提前。
- P6-C 已建立 Desktop-private revision/intent/provenance ledger 与 v1-compatible local writes：显式 Project/Conversation/Knowledge/Memory/relation 写入、optimistic conflict、持久 undo/redo、soft delete/restore 和 manual/fixture-only 模型/成本 metadata。它不改变 `nfai.exchange.v1` 或 Android 写库边界；下一 P6 门仍是真实 macOS GUI/compact/2.0x 与 Windows 原生交付证据，而非 P7 同步、P8 Agent、Provider 或真实计费。

### P6 统一 chat-first shell 修正（2026-08-13，本机 UI/自动合同完成）

- Desktop 默认与工作入口不再允许形成两套皮肤或两套信息架构。`对话 / 工作` 必须是始终可见的真实模式切换：对话模式是个人/通用会话；工作模式是工作区范围内会话，进入、再次点击或切换工作区时右侧必须打开该 scope 最近对话（无会话为同一输入区的新对话空态）。项目、知识、记忆和本地受控记录只在明确点击后替换右侧为二级页；work-home、资产仪表盘、深绿色 legacy workbench、固定 Inspector 或没有明确返回对话路径的第二外壳不再是可接受的生产入口。
- “发送”是对话输入唯一主动作：草稿、Conversation 与 user message 默认自动本地保留，user message 仍由既有 Rust Conversation owner 写入。模型未配置时只可显示“消息已本地记录，配置模型后可生成回答”与“选择模型／未配置”入口；不得伪造 assistant、真实 Provider、费用、Key 或同步成功。
- 本机证明包括 17 项 Node 合同（含工作区范围对话优先、最近会话/空态、禁止 work-home 默认、紧凑 X 与滚动恢复）、Rust 32 tests/strict clippy、离线 Tauri 构建和最新 macOS `.app` 的隔离 workspace 导入→工作默认对话→中段滚动→drawer X/关闭→窄宽恢复→再窄可见路径。fixture 曾揭示旧 work-home 默认，已修复且实机复核；64 条合成非敏感消息在第 19–23 条中段可见，输入区固定。真实 Provider/同步、Windows、Developer ID/notarization、OPPO 和图标继续独立。

## 13. Agent 与工具安全路线

### 13.1 分级能力

| 级别 | 能力 | 默认授权 |
|---|---|---|
| A0 | 只读分析、摘要、搜索、建议 | 可在明确数据范围内执行 |
| A1 | 生成草稿、计划、候选变更 | 结果需用户核对 |
| A2 | 可撤销的本地写入 | 执行前预览，执行后可撤销 |
| A3 | 外部消息、发布、购买、删除、账号或高风险操作 | 每次明确确认，确定性代码校验 |

### 13.2 Agent Runtime

Agent Run 至少保存：目标、范围、Task、模型、Harness、预算、工具许可、步骤、批准、结果、错误、重试、暂停、恢复和回滚信息。

固定规则：

- 工具 Schema 严格验证，未知字段和越权路径拒绝。
- 外部网页、附件和工具返回均是不可信输入，不能提升自身权限。
- 高风险动作由确定性代码执行和校验，模型只生成建议或参数候选。
- 超时、预算耗尽、权限不足和用户取消都进入明确终态。
- Agent 不得借助“自动化”绕过 Provider 外发确认、数据方向选择或其他产品安全门。
- Agent Run 采用可恢复 Checkpoint 与单调事件序号；暂停/恢复会重放的步骤必须与真实 Side Effect 分离。
- 外发、写库、消息、购买、删除和跨应用动作使用幂等键与执行回执，恢复或重试不得重复执行。
- 风险至少包含低、中、高和未知；未知默认要求确认或拒绝，不能按低风险放行。
- 高风险动作需要风险门、确定性权限、参数 Schema、用户确认、隔离执行、结果回读和可用回滚全部成立。

## 14. 南枫生态整合

### 14.1 整合原则

- 每个南枫应用继续拥有自己的业务数据库和 Provider Framework。
- 南枫 AI 通过稳定、最小授权的 Integration Contract 读取摘要或发起候选动作。
- 默认不直接访问其他应用数据库内部表，更不静默改写。
- 跨应用写入先形成预览，用户确认后由目标应用的确定性入口执行。
- 每个连接可以单独启用、暂停、撤销权限和查看最近活动。

### 14.2 建议接入顺序

顺序按“只读价值高、写入风险低、协议成熟度高”评估，不按应用名单一次铺开：

1. 南枫知识库：检索与引用，验证知识协议互通。
2. 南枫记：只读摘要和候选记账，真实写账需严格确认。
3. 南枫下载、视频与本地工具：任务建议和状态读取，执行另行授权。
4. 南枫智投：研究与分析候选，不提供未经验证的自动交易。
5. 南枫八字：读取用户授权的结构化资料与生成候选解释，不改变排盘真值。

具体次序可被真实产品需求调整，但所有接入都必须通过同一权限、来源、预览、审计和撤销门。

## 15. 可选 AI Hub 的触发条件与边界

### 15.1 只有同时出现这些需求才启动 Hub

- 至少两个南枫应用已经稳定使用 Provider Framework；
- 多应用重复维护模型目录、健康、路由或费用已产生可量化成本；
- 本地直连无法满足统一策略或跨设备需求；
- 有服务器、KMS、监控、备份、升级和费用预算；
- 离线和 Hub 故障时的本地回退已经验证。

### 15.2 Hub 可以负责

- Provider / Model Registry 分发；
- 能力、价格、健康和最后验证时间；
- 可选集中路由、预算和 Fallback 策略；
- 经过脱敏的安全运行元数据与成本聚合；
- 获得明确授权后的集中 Key Management；
- 后期可选 Agent Runtime。

### 15.3 Hub 永远不能负责

- 成为所有应用的业务数据库或知识库本体；
- 成为唯一 Provider 客户端或唯一 Prompt 存储；
- 成为应用启动、离线读取或本地核心功能的必需依赖；
- 默认接触原始对话、附件、图片和敏感个人数据；
- 在没有用户可见记录时自动改变 Provider、Model、费用或数据方向。

### 15.4 Hub 故障降级

- App 使用签名/校验后的本地最后可用目录和策略；
- 允许的情况下直连 Provider；
- 向用户明确显示当前使用本地配置或部分功能不可用；
- Hub 恢复后先比较版本和验证，不无条件覆盖本地正在使用的稳定配置。

## 16. 安全、隐私与成本控制

### 16.1 第三方外发

- 每次外发前显示实际 Provider、Model、文字/图片/附件范围和费用提示。
- 确认默认未选中；内容、Provider 或 Model 改变后重新确认。
- 一次授权只覆盖当前请求，不永久授权后续图片或扩大内容。
- 能脱敏的个人标识先脱敏；只发送完成任务所需的最小内容。

### 16.2 凭据与敏感数据

- API Key 使用 Android Keystore 保护，默认隐藏，可主动显隐。
- Key 不进入 Room、日志、截图、业务导出、普通备份、诊断包或同步快照。
- 调试模式也不得输出 Token、Key、完整 Prompt、原图和未脱敏个人资料。
- 远程服务响应先结构化提取安全字段，不把完整大响应当普通日志。

### 16.3 成本

- OpenRouter API 消耗独立记账，不与 ChatGPT、Claude 等会员订阅混为一项。
- Invocation 保存实际价格版本、币种、Token、缓存和费用；缺失字段为 `null`。
- 预算控制同时考虑失败、重试、Fallback、缓存和人工返工。
- 成本展示区分估算与 Provider 最终值，不伪造精确到小数的确定性。

## 17. 全阶段路线与门禁

### P0：总方案与治理冻结（当前阶段）

**目标**

- 统一原始资料、用户选择、冲突解释、长期方向和阶段顺序。

**范围**

- 产品简报、理解报告、总控蓝图、架构治理、领域规则、决策日志、近期实施计划、交接。

**禁止提前**

- 不创建 Android 工程，不写业务代码，不接真实 Key，不发布。

**退出证据**

- 总蓝图覆盖完整能力地图、阶段依赖、数据协议、平台、Provider/Harness、知识/记忆、Agent、生态、Hub、安全、测试、发布、迁移和复评。
- 所有文档不存在顺序与边界冲突。
- 用户确认总体方向。

### P1：Android 工程与可测试领域基础

**前置**

- P0 用户确认；开始时实时确定 Kotlin/Compose/API/依赖版本。

**范围**

- Kotlin/Compose 工程、构建门禁、导航外壳、依赖装配。
- Capture、Evidence、Candidate、Knowledge、AI Task、Invocation 的领域合同。
- Mock Provider、内存 Repository 和结构化错误。
- 结构化终态、Invocation 层级和 Provider 原始事件/内部事件隔离的最小端口。
- 最小主题与状态组件，只服务真实页面。

**禁止提前**

- 不接 OpenRouter，不建设完整聊天 UI，不创建 Hub/Desktop/Sync 空壳。

**保留接口**

- 稳定 ID、Repository 端口、Provider/Harness 端口、类型化流事件、版本字段。

**退出证据**

- 领域与入口测试证明：未确认不外发，未确认 Candidate 不保存，来源不丢，错误有终态。
- Debug 构建、静态检查和定向测试通过。

### P2：Android 最小捕获与知识闭环

**前置**

- P1 领域合同通过。

**范围**

- Room/SQLite、私有附件目录、图片选择基线、文本与系统分享。
- 最小导出协议和迁移测试。
- Keystore 凭据、OpenRouter Adapter、当前 Claude 预设、文本与视觉调用。
- 外发确认、结构化候选、核对编辑、保存、读取、调用与成本记录。
- 捕获、知识最小列表/详情、模型设置、调用记录。
- Model Registry 记录来源、能力、价格版本、验证状态和最后验证时间；价格未知与零成本分开。
- Invocation 形成 `Task Run → Provider Attempt → Generation → Validation` 的本地层级，内容字段最小化。

**禁止提前**

- 不扩展文件、语音、视频和网页解析；不开发账号、同步、Hub、复杂知识图谱。

**保留接口**

- Attachment 提取版本、Conversation 关联位、Project 归属位、Registry 快照、Export Manifest。

**退出证据**

- Mock 完整链路。
- 模拟器文字、分享和图片生命周期链路。
- 用户授权的真实非敏感 OpenRouter Claude 文本与图片链路。
- 保存、重启、再次读取和导出一致；Key 泄漏检查通过。
- 目标真机另列通过；未做真机时不得宣称移动 V1 完成。

**强制停止规则**

- 达到闭环即转 P3，不继续增加输入类型。

### P3：Claude 级多模型对话核心

**前置**

- P2 闭环退出；Conversation/Message Schema 设计评审。

**范围**

- P3-A 已落地版本化 Conversation、MessageNode、ContentBlock、Branch、Draft、Settings、Revision 与 Invocation 关联位；Schema 3→4 只增加本地会话表，未清库。Project/Memory/Tool/checkpoint 仅保留接口。
- P3-B 已落地 `AiRuntimeEvent`、确定性 fixture 和 `ConversationRuntimeStateMachine`；Schema 4→5 只增加事件指纹/顺序与恢复状态表。它不连接真实 Provider，也不把 Provider 原始 payload 写入 UI/Conversation/日志。
- P3-C 已落地 continue/retry/change-model 的确定性本地编排：继续以原 `CANCELLED`/`FAILED` 部分 assistant 为父节点；重试与换模型重答在同一 user 父下新建 assistant 兄弟版本。Schema 5→6 仅增加无正文的 `ConversationAttemptLineage`；每次新尝试均有新 Invocation、稳定 intent、来源/前序 Invocation 与安全 Provider/Model/Harness/Registry 选择，旧回答可切回。详见 `P3C_CONVERSATION_ACTION_LINEAGE_CONTRACT.md`。
- P3-D 已落地 `MessagePresentationRenderer` 的版本化安全展示 IR 与内存局部缓存：只投影已持久化 `ContentBlock`，不解析 Provider chunk、不写回领域真值。最小支持段落、标题、列表、引用、行内/围栏代码和可见但不可执行链接；畸形/未知语法降级为保留原文的纯文本。会话详情使用稳定 key/contentType 的 `LazyColumn`，并以确定性 240 条 fixture 验证身份合同；不伪造性能数值。`ConversationDraftRepository` 复用 Schema 6 的草稿表，保存/重建、附件引用去重和“消息追加 + 成功清草稿”单事务完成；详见 `P3D_PRESENTATION_VIRTUALIZATION_DRAFT_RECOVERY_CONTRACT.md`。
- P3-E 已落地 Conversation Management 本地闭环：`ConversationManagementDomain` 是标题/置顶/归档/排序/筛选的唯一语义所有者，Repository 是持久化入口，搜索仅投影同一 Room 的标题与当前根→叶 user/assistant 正文。Schema 6→7 只增加无正文的管理 intent 幂等事实，不清库；独立 Conversation Export v1 以私有 Manifest + `conversation.json` 原子写入、同文件回读和 SHA-256 校验，默认仅当前路径，绝不混同 Knowledge 包。详见 `P3E_CONVERSATION_MANAGEMENT_SEARCH_EXPORT_CONTRACT.md`。
- P3-F 已落地离线用户消息编辑与完整分支历史：`ConversationBranchHistory` 统一投影当前路径可编辑的纯文本 user 与全部 leaf；编辑经 `EditConversationUserMessageUseCase → ConversationTreeService → ConversationRepository` 创建不可变修订兄弟，原消息不改写。assistant 与 user leaf 均可切回，含附件 user 不提供编辑入口；不新增 Schema、Provider、Usage 或外发。详见 `P3F_CONVERSATION_EDIT_BRANCH_HISTORY_CONTRACT.md`。
- P3-G 已落地对话附件本地引用与草稿/消息生命周期：Photo Picker 经私有复制/资产目录后才进入 Conversation；草稿和消息只保存安全 `AttachmentId` 引用，缩略图受控有界解码。Schema 7→8 仅为剥离 Conversation 路径字段；无 GC、删除、Provider 或外发。详见 `P3G_CONVERSATION_ATTACHMENT_LIFECYCLE_CONTRACT.md`。
- P3-H 已落地 `ConversationAttemptHistoryProjection → ReadConversationAttemptHistoryUseCase`：只读 P3-C append-only lineage 与同一 assistant 消息状态，稳定排序且过滤跨会话/不匹配/非 local-fixture 行；当前 runtime 不污染历史 sibling，未知 Token 与本地零费率不伪装为真实用量/费用。Schema 8 不变；详见 `P3H_CONVERSATION_ATTEMPT_HISTORY_CONTRACT.md`。
- 流式消息、停止、继续、重试、换模型重答。
- Conversation、Message Tree、Invocation、Usage 本地持久化。
- 草稿恢复、错误恢复、Markdown/代码、长列表基础性能。
- 会话重命名、归档、置顶、搜索和导出（P3-E 已完成本地合同与验证；真实服务、真机与发布验收仍独立）。
- 分支编辑和切换（P3-F 已完成本地合同与验证）；图片附件进入对话。
- 当前实际 Provider/Model/Harness/用量的可见追踪。
- 类型化、有序且可去重的流事件；本地事件事实与 UI 投影分离。
- 流中断默认保留部分输出并新建 Invocation；中途 Fallback 必须经过能力与语义门。

**禁止提前**

- 不先建复杂 Agent；不以增加大量 Provider 代替对话质量；不把记忆自动化全部塞入首增量。

**保留接口**

- Project ID、Memory 来源、Tool 消息、内容块类型、断点/恢复字段。

**退出证据**

- 真实流式、停止、失败、重试、换模型和部分计费链路。
- 编辑历史消息不改变原分支；上下文只读取当前路径。
- 进程重建后事实一致；长会话滚动和渲染达到实测基线。
- 判断 OpenRouter 是否足够；若不足，以证据决定是否进入 Anthropic 原生 Adapter。

### P4：项目、记忆、上下文与知识完整化

**前置**

- P3 对话状态机稳定，真实使用出现长上下文和资料管理需求。

**范围**

- P4-A 已落地 Projects、项目指令和知识范围的本地所有权：`ProjectDomain → ProjectRepository/Room` 管理生命周期、revision、会话单归属与 Knowledge scope；`ProjectContextSnapshot / InstructionResolution` 只记录可审计优先级，不构造 Prompt 或网络请求。Schema 8→9 只增项目事实表，未清库。详见 `P4A_PROJECTS_INSTRUCTIONS_KNOWLEDGE_SCOPE_CONTRACT.md`。
- P4-B 已落地 `ContextSelectionDomain → ReadContextSelectionUseCase`：只读 `ContextSelectionSnapshot` 固定当前 Conversation 根→叶路径和 Project revision/hash 的 metadata-only 候选；草稿、兄弟分支、附件、Tool、Knowledge、Memory、检索、摘要/压缩与缓存均明确排除或未实现。Schema 9 不变，不构造 Prompt、RunSpec 或网络请求。详见 `P4B_CONTEXT_SELECTION_OWNERSHIP_CONTRACT.md`。
- P4-C 已落地 `MemoryDomain → ManageMemoryUseCase → MemoryRepository/Room`：只接受明确用户动作，管理稳定 ID、GLOBAL/PROJECT/CONVERSATION 互斥 scope、来源、ACTIVE/PAUSED/DELETED、append-only revision、软删除/批量删除、规范化重复与确定性冲突。高敏正文在写前拒绝且不落库。Schema 9→10 只追加 Memory 表；P4-B 继续不读取 Memory 或正文。详见 `P4C_MEMORY_GOVERNANCE_CONTRACT.md`。
- P4-D 已落地 `ContextBodySelectionDomain → ReadExplicitContextBodyUseCase`：用户逐次选择 L0 GLOBAL Memory、L1 当前 Project 指令/Memory、L2 当前路径 Text/Conversation Memory，生成仅内存的正文预览 IR。选择默认关闭，`memorySources` 未读未写；L3 正文保持排除，P4-M 以独立安全元数据 IR 补齐其本地预览。检索、语义摘要、真实缓存、Prompt/RunSpec 与 egress 均未实现；详见 `P4D_EXPLICIT_CONTEXT_BODY_SELECTION_CONTRACT.md` 与 `P4M_L3_LOCAL_ACTION_TRACE_CONTRACT.md`。
- P4-E 已落地 `KnowledgeDomain → ManageKnowledgeUseCase → Room`：正式 Knowledge 有 ACTIVE/ARCHIVED/DELETED、GLOBAL/PROJECT、标签和 append-only revision；Schema 10→11 只追加 lifecycle/revision/tag 表。搜索是标题/正文/标签/来源的确定性本地投影；Context 中必须先搜索、逐项选择，再以 revision/hash 与 P4-B metadata 重验，绝不自动注入或构造 Prompt。详见 `P4E_LOCAL_KNOWLEDGE_RETRIEVAL_CONTRACT.md`。
- P4-F 已落地 `ManageKnowledgeUseCase → KnowledgeDeduplicationDomain`：仅在用户从活动 Knowledge 详情主动请求时，对同一 GLOBAL 或同一 Project 的活动 Knowledge 生成瞬时、确定性的重复候选。候选只由规范化标题或规范化内容 hash 匹配，不落库、不自动合并、不建立关系、不触碰 Context/Export/Provider；命中高敏正文整体拒绝。Schema 11 不变；详见 `P4F_LOCAL_KNOWLEDGE_DEDUPLICATION_CANDIDATES_CONTRACT.md`。
- P4-G 已落地 `KnowledgeRelationshipDomain → ManageKnowledgeRelationshipsUseCase → Room`：用户只可从两个既有 ACTIVE、同一 GLOBAL/Project 范围的 Knowledge 选择关系类型并确认。RELATED、DUPLICATE_CANDIDATE、CONTRADICTS 由 Domain 对称规范化，SUPPORTS 保持用户方向；确认前重验 endpoint revision/hash 与单一高敏 detector。Schema 11→12 只追加关系、append-only revision/intent 与索引；撤销软删除且可审计。P4-F 仅可标记建议来源，候选本身不落库；关系不会进入 Context、Prompt、Export、Provider 或同步。详见 `P4G_LOCAL_KNOWLEDGE_RELATIONSHIPS_CONTRACT.md`。
- P4-H 已落地单一 `MarkdownKnowledgeAdapter`：系统选择的 `.md/.markdown` 立即私有复制，`ImportTask → Markdown Adapter → Preflight → 用户逐项确认 → KnowledgeDomain` 是唯一链。Schema 12→13 只追加可恢复任务/项目事实；UTF-8/BOM/换行、多项边界、大小、高敏与 Markdown/代码惰性正文均有确定性规则。没有 URI/路径/token、自动关系/归并/Memory/Prompt/egress，也没有其他文件 Adapter。Markdown 导出仅投影明确选择的 ACTIVE 正式 Knowledge，以 Manifest、原子写入、回读和 SHA-256 作为成功条件。详见 `P4H_MARKDOWN_KNOWLEDGE_PORTABILITY_CONTRACT.md`。
- P4-I 已落地版本化离线 Eval 基线：打包只读 `EvalDataset / EvalFixture / EvalCase` 与独立 append-only `EvalRun / CaseResult / DeterministicAssertion / HumanScore`。Schema 13→14 只追加 Eval run/result/score 事实；不复用生产 Conversation/Knowledge/Memory 表，不执行 fixture 文本，不调用模型。自动断言只报告可机械证明的范围/顺序/状态/hash/安全事实；人工相关性、事实性、完整性、安全性、可追溯性评分独立追加且允许未评分。报告为 `OFFLINE_LOCAL` JSON + Manifest + SHA-256，明确不包含真实 Provider/Token/TTFT/cost/cache/质量或价值证据。详见 `P4I_OFFLINE_EVAL_BASELINE_CONTRACT.md`。
- P4-J 已落地 `LocalContextCompressionDomain → ReadLocalContextCompressionUseCase` 的版本化本地抽取式压缩；它只消费 P4-D 本次显式且重验通过的正文，按 Unicode code point 确定性保留前后片段并记录 source/compressed hash 与省略计数，既不是语义摘要也不是 Prompt。P4-K 将该链接入纯白 Context 控制面：只有用户明确选择来源并点击“本机抽取预览”才执行，结果只显示安全元数据且只在内存中存在；`StableContextPrefixMetadataDomain` 仅以已选 L0/L1 的 layer/kind/revision/source hash 生成 `p4k-stable-prefix-metadata-v1` fingerprint 与失效理由。没有 Provider cache、命中率、成本、持久化或网络调用。详见 `P4J_LOCAL_CONTEXT_COMPRESSION_CONTRACT.md` 与 `P4K_LOCAL_CONTEXT_PREVIEW_STABLE_PREFIX_CONTRACT.md`。
- P4-M 已落地独立 `LocalActionTraceDomain → ReadExplicitLocalActionTraceUseCase`：只在用户启用并逐项选择后，读取当前 Conversation 已有 P3-C append-only 的终态 local-fixture `CONTINUE/RETRY/CHANGE_MODEL` 谱系，输出有界、稳定、无正文的 `local-l3-metadata-v1`。它先后重验 P4-B metadata 与 lineage，竞态整体拒绝；编辑与切分支没有可证明的 append-only 用户动作事实，明确排除。Schema 15 不变，不进入 P4-D 正文、P4-J/K、Prompt、RunSpec、Export 或生产 Eval。详见 `P4M_L3_LOCAL_ACTION_TRACE_CONTRACT.md`。
- 工作/会话/长期记忆，可见、可控、来源和冲突处理。
- L0–L3 上下文、检索、摘要、压缩、缓存稳定前缀。
- 文件和网页输入按一个 Adapter 一个验收逐步加入；语音/视频在其后。
- 知识搜索、标签、关系、去重候选、回收站、批量导入导出。
- 基准任务、离线 Eval、成功任务成本和回归集。
- Memory 的显式 CRUD、暂停、批量删除、历史、来源与作用域。
- 文档处理可恢复队列、逐项进度、失败保留和重试；原文件到索引的每层产物独立版本化。
- 版本化 Fixture/Dataset、确定性断言、人工 Score 与红队用例。

**禁止提前**

- 不把自动提取记忆默认静默开启；不一次添加所有输入；不把 Eval 分数等同真实用户价值。

**保留接口**

- Tool Schema、Agent Task、跨端测试夹具和 Sync Manifest。

**退出证据**

- 记忆增删改停和来源完整；高敏记忆拒绝规则通过。
- 长文件不全文重复发送，压缩后回答可追溯。
- 缓存、成本和质量有真实基准，不用估算替代。
- 导入导出经过回读和哈希验证。
- Provider/Model/Harness 更新前后使用同一 Fixture 回归，分数、人工判断和真实用户价值分别报告。

**P4-N PDF 文本 Adapter 增量（本地完成）**：`PdfTextKnowledgeAdapter` 仅接受 `application/pdf` 的受限读取与私有复制，按页抽取文本层并在独立 `pdf_text_import_tasks/pages/items` 中记录原件/逐页提取/候选的版本与 hash；用户逐项确认后才经 KnowledgeDomain 正式写入。它不 OCR、不处理图片、JavaScript、链接、Launch action、嵌入文件或表单动作，不读取/保存 URI/路径/token，不生成 Context、Memory、关系、Prompt、RunSpec 或外发。文件、对象/流标记、混合内存、页数、逐页/总文本和高敏规则都显式安全失败；Schema 15→16 仅追加 PDF 专属表，迁移回归保留旧表。详见 `P4N_PDF_TEXT_KNOWLEDGE_ADAPTER_CONTRACT.md`。全量单测（161）、Lint（0 errors）及正式签名 Debug/Release 已通过；仅 `emulator-5554` 已完成真实 DocumentsUI 两页文本层 PDF 的选择、确认/跳过、force-stop 冷启动恢复和 base.apk hash 回读。它仍不证明 OCR、扫描件、加密/畸形真实样本、OPPO、Provider、成本或 P4 退出。

**P4-O 严格 HTTPS 网页文本快照 Adapter 增量（本地完成）**：`用户明确确认 HTTPS URL → AndroidPublicWebFetcher → web_text_snapshot_tasks/items → KnowledgeDomain` 是唯一链。每次初始与 redirect URL 都重验标准 host 与公共 DNS；最多 3 次 redirect，拒绝 http、userinfo、IP/localhost、私网/保留地址、fragment 与高敏 query。无 Cookie、Authorization、Referer、浏览器会话、JS、子资源、表单或页面内链接。HTML/网页文本是惰性不可信数据，严格有界并确定性剥离动作或隐藏内容；私有 HTML、抽取文本、候选和正式 Knowledge 分层 hash/version/status，Room 无 DNS/IP、凭据、网络 body 日志或完整 query。Schema 16→17 仅追加网页任务/条目表；中断任务明确可重试、同 URL 不自动去重；仅逐项确认经 `KnowledgeDomain` 写入。详见 `P4O_HTTPS_WEB_TEXT_SNAPSHOT_CONTRACT.md`。全量单测（165）、Lint（0 errors）、正式签名 Debug/Release 已通过；仅 `emulator-5554` 已完成最终签名包手工 URL/确认→明确 `DNS_NOT_PUBLIC` 安全失败→force-stop 冷启动。该模拟器把 example.com 解析到保留 `198.18.0.147`，故当前严格公网成功仍是独立网络债务；早期较宽校验下的 example.com 提取/确认历史不作为当前证据。它不证明登录网页、私网/攻击流量、OPPO、Provider、成本或 P4 退出。

**P4-M/P4-N/P4-O 局部完成不等于 P4 退出**：真实 P3 退出门仍后置；语义摘要、真实缓存、其他输入 Adapter、真实 Provider 成本与更高层 Eval 仍未开始。Memory、Knowledge、关系与 L3 元数据均未自动进入 Context 或外发；离线 Eval 也不是 Prompt、真实调用、模型质量或缓存收益证明。

### P5：Android 产品化与正式交付基线

**前置**

- P2–P4 已达到约定的 Android 产品范围。

**范围**

- 自适应手机/折叠屏/平板布局、无障碍、性能、后台与电量。
- 隐私说明、权限最小化、数据管理、诊断导出、崩溃和恢复。
- Release 签名、升级迁移、备份恢复、正式 APK/商店策略。

**P5-A 自适应布局与无障碍基线（已实现，P5-A2 键盘/语义补证完成）**：独立合同为 `P5A_ADAPTIVE_ACCESSIBILITY_BASELINE_CONTRACT.md`。Android UI 壳提供可测 Compact/Expanded 分类、edge-to-edge/safe drawing/IME、route 恢复、底栏/NavigationRail 和同 route 工作区；Domain、Room Schema 17、Provider/egress 均未改变。2026-08-13 P5-A2 确认 API 35 `emulator-5554` 预装并可绑定 TalkBack，真实 accessibility node dump 覆盖 Capture、底栏/更多、网页 Adapter Dialog 与 route live text；发现并修复多行编辑器吞入 Tab，P5-A 根壳及 Adapter/Conversation/Project/Memory Dialog 统一将 Tab/Shift-Tab 映射为焦点移动。真实 keyevent 覆盖 Capture 文本→保存、底栏 Enter 到对话、DPAD 到更多及 Space 激活；自动验证更新为 173 tests、0 failures/errors，Lint 0 errors/14 warnings；重建 code 35 的正式签名 Debug/Release，v2/v3 通过。TalkBack 服务/节点语义并不等同于逐句人工听觉验收，模拟器扬声器监听仍为设备债务；最后 Dialog 修复包也需在下一阶段前补一次 install-r/base.apk 回拉。不得操作 OPPO。

**P5-B 后台、取消/恢复与电量产品化基线（本地完成）**：独立合同为 `P5B_BACKGROUND_CANCELLATION_BATTERY_CONTRACT.md`。`TaskExecutionPolicy/TaskRecoveryAudit` 只统一治理任务 owner、IO/Default、进程死亡映射和用户重试，不合并 Markdown/JSON/PDF/Web/Eval/Conversation 的领域表。Markdown/JSON/PDF/Web 不安全继续的运行态均在冷启动落为 `FAILED(INTERRUPTED)`；fixture `STREAMING` 也明确失败而不重放。PDF 使用逐页 cooperative cancellation/进度与既有 2 MiB mixed-memory；Web 仅用户前台确认单次抓取，网络恢复/后台/重启不自动重试。审计后不引入 WorkManager：没有一个任务同时满足无用户确认、可跨进程幂等重放且必须系统调度；Manifest/依赖无 scheduler/service/receiver/wakelock/alarm/BOOT/polling。自动门为 176 tests、0 failures/errors，Lint 0 errors/14 existing warnings；`0.3.0-p5b`/code 36 的正式签名 Debug/Release v2/v3 通过。仅 `emulator-5554` 已验证前台政策文案、手动 URL `https://localhost` 的 `FAILED` 和 force-stop/冷启动后无自动重试，以及 `install -r`/base.apk hash 回读；真实长任务的用户取消设备时序尚待独立补证，不能由自动合同替代。不得操作 OPPO。

**P5-C 隐私、数据管理与安全诊断（完成，P5-D 前置已满足）**：独立合同为 `P5C_PRIVACY_DATA_DIAGNOSTICS_CONTRACT.md`。`PrivacyDataManager` 在纯白 Settings Dialog 中仅读取安全聚合；四个删除范围均先产生 fingerprint 预览，全部本地业务数据要求强确认短语并保留安装/签名身份。临时/失败任务资产另有默认零选择的 Adapter/安全 ID 摘要/终态/私有文件 count+bytes/失败证据后果清单；只允许 `FAILED/CANCELLED` 且没有 ACTIVE Knowledge 或 export 命名空间引用、存储 key allowlist/canonical/no-symlink 全部通过的任务。选项会被二次预览；按稳定顺序 quarantine 文件、transaction 删除该任务 Room 行、再清 quarantine，部分清理只留下用户明确触发的剩余项 retry。诊断为用户 SAF 选址的 `nanfeng-ai.security-diagnostic` v1 Manifest/payload hash 文件，严格 allowlist 且整体拒绝任何正文、URL/path、凭据或高敏形态；无 telemetry、crash SaaS、Firebase/Sentry/analytics。Manifest 仍只有具有可说明用途的 INTERNET，无传统存储/通知/相机/麦克风权限。真实 API 35 `emulator-5554` 已创建 34 B 非敏感 FAILED Markdown fixture，并只选 1 个 / 34 B Markdown 候选；确认后 `markdown_tasks/private assets` 从 `3/14` 变为 `2/13`，其他历史聚合不变，force-stop 冷启动没有重现。partial 文件失败为真 Room/app-private files + deterministic fake deleter 自动合同，未破坏设备历史数据；不得误报为设备级 partial。`0.3.0-p5c2`/code 38 Debug/Release 的最终测试、Lint、签名、安装回拉证据见当前 handoff。

**P5-D 手工本地备份、恢复、迁移与正式交付基线（当前包 SAF 链已关闭；P5 未退出）**：独立合同为 `P5D_LOCAL_BACKUP_RESTORE_DELIVERY_CONTRACT.md`。用户手工 SAF 导出 `.nfai-backup`，由一致性 SQLite `VACUUM INTO` 快照（不是 WAL/SHM 复制）、版本化 Manifest、受控资产、逐项/Manifest hash 与 SAF 回读组成；高敏/secret、zip-slip/symlink、重复 entry、未知版本、超限/压缩炸弹和 hash 异常整体拒绝。恢复先隔离预检，非空仅显式替换或取消，替换有 checkpoint/staging/rollback 与完全重启边界，不做 merge 或自动重放。Android Auto Backup 和 device transfer 均保持禁止。当前 Schema 37 的 release-v2 签名隔离包 `com.nanzhufeng.ai.p5dacceptance` 已在 `emulator-5554` 经正常 Settings/SAF 完成成功导出回读、非敏感变更后的受控替换及 force-stop/cold-start readback；installed base.apk 与本地验收 APK SHA-256 均为 `e07de2cc1aa4438592bf0aa83d467b3b92c3faaf1941b3d4faf537bb3aad2965`。这不替代旧 APK `install -r` 升级迁移、OPPO、发布、云同步或 Provider 验收。详见 current handoff。

**禁止提前**

- 不把 Debug、模拟器或单次真实请求包装成正式版交付。

**退出证据**

- 构建、单元/集成/UI、目标模拟器、真实服务、目标真机分层通过。
- Release 升级不丢数据；签名、资产名、SHA-256 和回下载校验完成。
- 已知限制、隐私边界和未验证面明确。

### P6：Desktop 控制中心

> P6 已确认反馈的可检索追踪索引是 `PRODUCT_FEEDBACK_DECISION_LEDGER.md`；本节只保留阶段路线，P6-E/P6-F/P6-F2/P6-G 合同分别拥有规则正文。后续 Prompt Cache 与用量/成本/余额台账分别以 `PROMPT_CACHE_AND_EXACT_REUSE_CONTRACT.md`、`AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md` 为唯一规则正文；这些后续项均未实现。

**P6-A 技术决策门与交换协议 v1（完成；P6 未退出）**：ADR-002 已以同一 non-sensitive fixture 完成 PWA/Tauri 受控 spike，选择 Tauri 2 进入 P6-B；PWA 仅保留 File System Access/OPFS/download fallback。`nfai.exchange.v1` 是独立语义迁移合同，严格排除 Key、Provider raw、runtime/diagnostic、route preferences、URI/path 和 Android DB 细节；Android 显式 snapshot export 与无写入 preflight、Desktop memory IR 都复用同一 schema/canonical hash/fixtures。Windows installer/signing/WebView2/真实 picker/SQLite 仍是 Windows 本机债务，不能以本 macOS cargo check 宣称交付。详见 `ADR-002-P6A_DESKTOP_AND_EXCHANGE.md` 与 `NFAI_EXCHANGE_V1_CONTRACT.md`。

**前置**

- Android 数据协议和对话/知识核心稳定；Desktop 技术决策门完成。

**范围**

- 项目、对话、知识、模型、费用和工作流的宽屏工作台。
- 先以导入导出包互通，再评估实时同步。
- Windows 作为深度生产和控制中心重点验证。

**跨端信息架构不变量（2026-08-13）**

- Desktop 与 Android 共享同一产品信息架构：两端均以“对话 / 工作”为核心模式，导航层级均为“模式功能区 → 置顶区 → 会话或项目内容区”。工作区范围内对话优先、会话/项目置顶、会话归档/恢复、知识、记忆、受控记录、Composer 模型选择与统筹设置的名称、领域状态、排序、持久化、自动策略与权限语义必须一致。
- 平台只改变自适应容器：Desktop 使用常驻/折叠侧栏、窄屏 drawer 与 hover/focus actions；Android 使用 drawer、分层页面与长按或 trailing action。不得把 Desktop 宽屏硬塞进手机，也不得以“手机布局”为由省略同一业务入口。
- 任何新增功能合同必须在入口矩阵同时列出 Desktop 与 Android 的入口、状态和验证；从本条生效起，两个平台必须在同一阶段同步落地与验收。一个平台的 build、fixture 或真实路径不证明另一平台；仅真正的平台能力可标“不适用”，布局差异不是功能缺失理由。

**P6-D 双端侧栏增量（进行中）**

- 两端以同一 `Conversation` 生命周期合同实现 `pinned/archived/revision/expectedRevision/undo`：活动置顶优先、再更新时间倒序、再稳定 ID；归档必清除置顶，恢复为活动未置顶，重放只回读 receipt、冲突不得覆盖。Desktop 的 Rust SQLite owner 与 Android 的 Domain/Room owner 分别持久化，UI 不直接写库。
- Desktop 是三段常驻/可折叠 rail，窄屏为 drawer，行操作需 hover/focus/键盘可达；Android 是 Compose drawer/分层导航，同样三段，通过 trailing action/操作面完成置顶、取消置顶、归档与恢复。当前工作区仍只改变对话 scope，不得退回 work-home。
- 新建入口的唯一自适应差异：Desktop 保持侧栏顶部；Android 默认关闭 drawer、左上固定“打开对话导航”，抽屉底部安全区上固定新建按钮，不能随列表滚动或置于顶部。选会话/新建自动关闭，返回/scrim/手势先关闭 drawer，Activity 重建保留 mode/会话/草稿但默认关闭 drawer。
- 验收必须同时包括：Desktop 隔离 app-private workspace 的真实 `.app` 操作、关闭重启读回；Android JVM/Room/UI 可行测试、lint、既有正式证书 Debug/Release、`emulator-5554` 同签名 `install -r` 与 Activity/restart 读回。不得清数据、安装 test APK 或操作 OPPO。
- 会话行的紧凑交互合同：Desktop hover/focus 快捷区和 Android 行 trailing 区只可呈现复用现有图标来源的图标入口，不能常驻“置顶/取消置顶/归档”等动作文字；Desktop 每个图标必须有 `title`、`aria-label`、键盘焦点和足够点击热区，Android 只保留带 `ContentDescription`、48dp 热区的“更多”图标，且长按打开同一操作层。完整 Desktop 右键菜单与 Android 更多/长按 bottom sheet 才显示“图标＋文字”；置顶/归档状态由置顶/归档分区或小状态图标表达。软删除必须在完整菜单的危险分组中使用错误色，并始终进入既有确认，不改领域 owner。双端各自需要自动可访问性合同与真实 UI 证据。
- Composer 与菜单定位补充合同：主动作常驻为圆形图标按钮——可发送/校验失败均为上箭头（失败 disabled），只有 `ConversationRuntimeState` 的非终态且既有 typed cancel owner 可用时才改为方形 stop 并调用/读回真实 cancel；仅本地落盘/附件准备等不可取消状态保留 progress/busy，绝不可用 timer/CSS 假装 Run。当前 Desktop 没有本地 Run/cancel owner，只能显示发送箭头；真实 stop 接线属于未来 Model Execution 阶段。`＋`、model selector、sidebar open/collapse、row more 也遵循紧凑图标/完整菜单文字规则；voice/temporary-chat 未实现则不得显示，核心“对话/工作”保留文字。Desktop context menu 必须以被右键或键盘调用的行 rect 为锚：优先行下方标题左边缘、空间不足 flip 上方、横向 clamp 于 sidebar/app viewport；scroll、resize、模式/选择变化、Escape 和点外关闭或可靠重算。Android 继续 bottom sheet/modal 并显示当前会话标题。Desktop 标题 14px medium、Android 15–16sp，均单行 ellipsis；置顶不放大标题。每端需有状态/无障碍及 anchor/typography 自动合同和真实 UI 证据。

**已授权后续双端路线（不得提前接 Provider/Key/HTTP）**

- **P6-D 最小视觉收敛（2026-08-13）**：置顶/归档统一使用 PushPin/Archive（恢复只在完整菜单），Desktop/Android composer outer shell 在 idle/focus 均不变；仅 inner input 保持单一 1px/1dp 浅中性→`accent-subtle-border` 浅柔橙边线，无 outline/glow/shadow/indicator 叠加。侧栏会话标题固定 12px/12sp 下限、section label 更小。主橙只用于 CTA；绿色只保留成功/已验证，红色只保留危险。该收敛不改变 Conversation、Room、Rust SQLite 或 Provider 边界，P6-E 的入口必须直接复用这些 token。
- **P6-D2 Cross-platform Composer Attachment Adapter（2026-08-13，完成）**：在 P6-E 前，双端 `＋` 只开放真实图片/文件 picker。平台 adapter 先校验 allowlist MIME、magic、20 MB 单项/4 项/40 MB 总量，执行 app-private content-addressed copy、SHA-256 去重和安全 metadata 回读，再由唯一 Draft/Message 路径保存稳定引用；没有 URI/path/Key、Prompt/RunSpec 或 egress。USER bubble 与 attachment chip 统一消费 `accent-orange-soft`+深正文；ASSISTANT/SYSTEM/TOOL/ERROR 保持各自中性/语义 surface，code/quote 内层中性。合同为 `P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md`。最新 `.app` 已修复静态依赖漏打包导致的 blank WebView，并真实完成 native 图片/文件 picker → 私有复制 → 发送 → 重启回读；`emulator-5554` 已完成 DocumentsUI 文件与 Photo Picker 图片 → 私有复制 → 用户消息发送 → force-stop 重启回读，以及同帧 USER bubble、attachment chip、orange composer/发送/新建和模式化对话菜单的可见验证。Android 受控 Room SHA-256 dedup 已经写入、repository 重建与 hash 回读验证；**Android 24h GC 按合同 N/A。** Desktop SQLite v6 由 attachment owner 保存创建/最后引用/最后解除引用/reference count，启动 maintenance 与可注入 Clock 的同一路径已覆盖 23h59m 保留、24h orphan/staging 删除、幂等与 reopen。最新 ad-hoc `.app` 在独立 `/tmp` HOME fixture 真实启动后从 3 assets/1 staging 变为 2/0，保留 referenced/fresh orphan、删除过期两项，重启 aggregate asset SHA-256 为 `a806aa53a0e4eab2b74fec881bf581cdf95762028c6436316a42f907fb4ea412`。P6-E 后续已于 2026-08-14 完成，当前进入 P6-F Core。

1. **Temporary Conversation P6-E（已完成，2026-08-14）**：在当前双端侧栏/Composer “＋”收口后、模型选择之前，按正式 `P6E_TEMPORARY_CONVERSATION_DUAL_PLATFORM_CONTRACT.md` 单独双端交付。以显式 `ConversationKind.NORMAL/TEMPORARY` 区分；TEMPORARY 不进入普通 Conversation 历史、项目/工作区、置顶/归档、搜索、Knowledge/Memory、导入导出、同步、缓存或训练声明。复用 P6-D2 的图片/文件 picker 与 private-copy owner，但附件显式属于 `TEMPORARY_SESSION`；Ghost 直接 NORMAL↔TEMP，返回 NORMAL 只隐藏不删除，最后有意义 mutation 后 24h 自动清理，且不含 URI/path/Key。右上角同义入口、顶栏“临时聊天”、普通与临时草稿/消息/附件/model override 隔离，以及 Desktop `.app`、Android 正式签名/模拟器 restart 证据均为硬门。Composer 的＋/模型为 Desktop 26px SVG box 中约 20–22px glyph/40px target、Android 24dp/48dp；发送独立为 Desktop 30px surface/25px arrow、Android 36dp surface/22.5dp arrow，TEMP 模型仅为本地 override，不得伪装为已配置 Provider。Android 外屏/内屏/折叠真实设备门只路由到 `ANDROID_TARGET_DEVICE_PROFILE.md`，当前不得擅自操作 OPPO。未来在线时仅如实说明仍会发送给选定 Provider，Provider 保留/训练政策不由南枫 AI 保证。**2026-08-14 退出：Android 独立 acceptance 正式签名 applicationId 与 Desktop 独立 acceptance `.app` 均经真实“设置→数据与存储→会话管理”运行；固定 Clock/owner 的 23h59 保留、24h 清理、force-stop/完整退出后 receipt 重启读回和 Room/SQLite ordinary-surface 全零已通过。双端真实 TEMP attachment/model override、overlay 抽样、Finder/Dock/Launcher 当前表面、全量自动门及最终包 hash 均已冻结在 P6-E evidence。生产包没有时间篡改入口，未读 Key、未发 HTTP、未操作 OPPO。P6-E 到此完成，下一唯一阶段是 P6-F Core。**
2. **P6-F Conversation Transcript Presentation & Message Actions（完成，2026-08-14）**：按 `P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_CONTRACT.md` 与最终 `P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_EVIDENCE.md` 交付。双端 transcript 的角色、日期/本地时区、真实来源/metadata、plain-text copy、typed idempotent Message Tree branch 与 restart readback 已完成；Android share 是明确确认后的 `ACTION_SEND`，TEMP 默认禁用 share/branch/export。Desktop 原生 share 因无安全 owner 按用户许可保持隐藏，未伪造或外发。图片与文件仍仅安全本地呈现，VIDEO 与预览继续属于 P6-F2 的逐 Adapter 验收。
3. **P6-F2 Unified Search, History & Local Content Preview（双端、逐 Adapter）**：P6-F2-A Search Index、P6-F2-B Image Preview、P6-F2-C PDF Preview、P6-F2-D Video Preview 与 P6-F2-E Audio/Generic File 均已完成，最终证据依次为 `P6F2A_LOCAL_SEARCH_INDEX_EVIDENCE.md`、`P6F2B_IMAGE_PREVIEW_ADAPTER_EVIDENCE.md`、`P6F2C_PDF_PREVIEW_ADAPTER_EVIDENCE.md`、`P6F2D_VIDEO_PREVIEW_ADAPTER_EVIDENCE.md`、`P6F2E_AUDIO_AND_GENERIC_FILE_ADAPTER_EVIDENCE.md`。P6-F2-E 退出覆盖 Desktop 原生 picker / Android DocumentsUI、private-copy、消息、显式播放或 inert 文本预览、重启读回和最终签名/hash。**这不包含 P6-G**；仍按合同维持 TEMP、Key、path/URI、隐藏过程零进入，预览不等于 Provider 外发。
4. **P6-G Model Selection / Auto Router 本地基础（完成，2026-08-14）**：正式合同为 `P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md`。会话手动 override > 全局默认 > Auto；Auto 先走 exact cache、本地/安全与能力/成本门，再在合格候选中偏好 Claude/Anthropic，OpenAI 仅为明确 fallback/特长候选。Domain 不硬编码未来型号，只使用 `FAST/BALANCED/DEEP/APEX_REVIEW` 与接入时动态 catalog snapshot；unknown cost 失败关闭或要求确认。FB-P6-023/024 的统一壳层与 transcript 已完成双端真实 UI 前置：Android 以安全本机触控/键盘通道、Desktop 以最新唯一 `.app` 完成普通会话 manual fixture→Auto、global policy、Chat/Work scope、TEMP zero-leak（Android）及 restart readback；Desktop 窄窗/drawer/Composer/回底亦已实操。fixture 仅为 app-private `LOCAL` catalog item，不是 Provider。P6-G 的本地 registry/preset、状态与两端 UI 到此退出；仍不读 Key、不发 HTTP，也不得冒充真实 catalog/Provider。
5. **Conversation Import / Reuse — ChatGPT export JSON Adapter（完成，非 OPPO 授权范围）**：合同为 `P6H_CHATGPT_EXPORT_JSON_ADAPTER_CONTRACT.md`，最终证据为 `P6H_CHATGPT_EXPORT_JSON_ADAPTER_EVIDENCE.md`。Android Schema 24→25、Desktop SQLite 12→13 的最小追加迁移，以及严格 parser、private-copy、专属任务机、原子 Conversation/Message Tree/provenance/receipt commit 均已完成。FB-P6-026..032 的独立 Settings、UI/分享/附件/图标语汇反馈，以及 FB-P6-033 的 user master 派生/签名/hash/Finder+embedded ICNS 证据已经逐项关闭。OPPO OEM Launcher 仍是单独未授权硬件门，不回滚本阶段。下一阶段只能由 context gate/HANDOFF 创建，仍未读 Key、未发 HTTP、未操作 OPPO。
   - 2026-08-14：P6-H 非 OPPO 退出后 `context_gate.py` 返回 HANDOFF，已按长期授权启动唯一下一任务 `019ffeab-afec-7e02-9f8a-d82ac2b64f35`：Claude export JSON Adapter。仍是一个 Adapter 一个验收，禁止把 ChatGPT 证据外推为 Claude、知识库 Adapter、Provider 或同步完成。
6. **Claude export JSON Adapter**；7. **南枫知识库对话/知识 export Adapter**：每个 Adapter 单独一阶段/一验收，Desktop 与 Android 同阶段完成各自系统文件选择器、确认/跳过/恢复、重启读回与导出回读。**P6-I 当前先受 FB-P6-034→P0 FB-P6-037→FB-P6-035→FB-P6-036→FB-P6-038/048→FB-P6-039（均已关闭）→040、041、042、043、044、045、046、047、049 的壳层回归队列阻断；FB-P6-049 仅允许最终精确 placeholder“回复 南枫AI”（中间恰一个半角空格），任何早前变体不得保留。逐项关闭前不得继续 Adapter 实施。**只可审计知识库项目的代码/协议以定位复用框架，不读取其真实用户数据。每个导入将第三方包受控本地解析/私有复制并归一化为真实 Conversation + Message Tree，保留 user/assistant/tool、顺序、时间、分支、模型、工作区归属和安全附件引用；进入搜索、置顶、归档、继续对话与显式 Context。必须标 imported，并保存 source system、source conversation/message opaque ID、adapter/version、import time、content hash/package hash provenance；绝不冒充本产品生成或 Provider Invocation。禁止上传、执行 Markdown/HTML/tool 指令，或持久化 path/URI/token/Key。复用 P4-H/L 任务机，覆盖 schema/version/size/未知字段/分支/部分失败/中断恢复/重复幂等/冲突/rollback/reimport/来源撤销与审计。
8. **Prompt Cache / Exact historical reuse**：按 `PROMPT_CACHE_AND_EXACT_REUSE_CONTRACT.md` 区分 `LOCAL_EXACT_HIT`、`PROVIDER_PREFIX_HIT`、`MISS` 与 `UNKNOWN`。仅在 normalized request hash、model snapshot/ID、parameters、explicit context/version hash、policy version 全等且来源未撤销/过期时，才显示“历史缓存/来源”、直接复用且不建 Provider attempt/不计费用。任何部分匹配只是历史检索或 Context candidate，必须显式选择并仍走正常调用，不能称缓存命中。
9. **AI Usage, Cost & Balance Ledger**：按 `AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md` 对每个物理 Provider attempt 写不可变账目，并严格分离 Provider settled/response、local estimate、local exact zero 与 unknown；余额、预算、对账与缓存节省不得冒充事实。该台账不保存 Prompt、回答、Key、URI 或路径。

**禁止提前**

- 不复制一套不同语义；不直接打开不兼容的 Android 数据库文件；不要求账号才能离线使用。

**退出证据**

- Android 导出 → Desktop 导入 → 再导出回读精确保真。
- 紧凑/展开布局独立通过；本地文件、更新和异常恢复真实验证。

### P7：可选账号与端到端加密同步

**P7-A 本地协议与密码学基础（完成；P7 未退出）**：独立合同为 `P7A_LOCAL_E2EE_SYNC_FOUNDATION_CONTRACT.md`。`nfai.sync.v1` 与 P5-D `.nfai-backup`、P6 `nfai.exchange.v1` 完全分离；Android/Rust 对同一 canonical payload/envelope 以 PBKDF2-HMAC-SHA-256 + AES-256-GCM 严格 seal/open/preflight，AAD 绑定 app/document/protocol/schema/revision/payload hash。P7-A `seal` 只接收调用方短生命周期持有的 data key，P7-B 才会生成/保存/轮换每账号 key。每 record 有强制 classification，`HIGH_SENSITIVE` 在 seal/open 均拒绝，禁止字段 detector 仅为补充失败关闭。双端 golden、错误恢复码、header/ciphertext/tag/hash、未知/重复字段、截断、跨 app/document、revision rollback、超限与高敏拒绝均有本地自动覆盖。没有 Credential Manager、Supabase、RLS/RPC、WorkManager、HTTP、账号或假云同步 UI；真实服务和跨设备验收仍是 P7-B 至 P7-E 的外部门。

**P7-B 账号级本机密钥与同步状态机（完成；P7 未退出）**：独立合同为 `P7B_ACCOUNT_KEY_STATE_MACHINE_CONTRACT.md`。Android Room 17→18 与 Desktop SQLite 3→4 仅新增 opaque account ref、key alias/ref/hash、state/revision/direction/error 与 intent receipt，禁止 data key、恢复码、token、业务 payload/ciphertext。Android 在 verified opaque handle 首次进入时以 CSPRNG 建 32-byte data key，经不可导出的 Android Keystore AES-GCM key 封装至 app-private blob；Desktop 由 OS credential-store 抽象承载，macOS 已对随机 app-owned Keychain entry 写→读→删→不存在自检。恢复码确认前严格停在 `AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION`；显式 intent/expectedRevision/receipt 状态机覆盖 direction、conflict、账号切换和保留本机数据退出，冷启动只读 metadata、不调度。P7-B 没有 Credential Manager、Google/Supabase、HTTP、cloud envelope、假登录或假同步成功；P7-C 才可立真实服务配置合同，P7-D/P7-E 仍是交互与真实跨设备外部门。

**P7-C Google/Supabase 可部署服务配置（本地工件完成；真实服务未验证）**：独立合同为 `P7C_REAL_SERVICE_CONFIGURATION_CONTRACT.md`。`supabase/migrations/202608130001_p7c_secure_sync.sql` 建立 `nfai_account_keys`、`nfai_sync_documents`、强制 RLS default-deny、最小 authenticated RPC、advisory lock + expected revision 原子提交、版本/大小/hash/envelope 校验与 audit timestamps；没有 direct table grant 或远端删除 RPC。`google-avatar` 仅从已验证 Google identity metadata 提取 HTTPS `*.googleusercontent.com` 地址，JWT、每跳重验、8 秒、2 MiB、image-only、no-store。Android code 43/`0.3.0-p7c` 增加 private-injection config 和 envelope-only typed gateway；Desktop 只增加无 HTTP/Tauri command 的 disabled typed gateway。Node/Deno、Android 全量 test/lint/Debug+Release、Desktop 前端/Rust/Tauri app build 均通过；Android 两 APK 是既定正式证书且仅在 `emulator-5554` 同签名覆盖冷启动成功，Desktop 最终为 strict-verified ad-hoc build。当前机器没有 Supabase CLI、project link、完整 private config 或可验证授权会话，故没有远端 schema 读取、部署、OAuth、账号或密文访问；真实服务门仍独立。下一阶段 P7-D 只能推进本地冲突/同步/账号 UI 状态，真实 Google/Supabase 继续等待明确 target 与授权。

**P7-D 冲突、自动同步协调器与账号页（本地完成；P7 未退出）**：独立合同为 `P7D_SYNC_COORDINATOR_AND_ACCOUNT_UI_CONTRACT.md`。Android code 44/`0.3.0-p7d` 新增 Room 18→19 secret-free job/receipt ledger、唯一 coordinator、只接受显式 bounded P7-A allowlist producer 的阶段/冲突/回读语义，以及依 configured+verified+READY+recovery/direction guard 才可用的 WorkManager adapter；无配置生产 App 不绑定 producer/session、不 enqueue/HTTP，且禁用 WorkManager 默认 Startup initializer，模拟器 JobStatus 为零。设置页有可返回的“Google 账号与同步”离线详情，纯白内容面、无假身份、disabled 操作；私有头像 cache 按 account+URL hash 隔离但不请求网络。Desktop 只添加离线-disabled coordinator/About 状态，无 remote capability。P7-D 覆盖本地 guard、generation、回读/冲突、avatar cache、UI/return 及 Android/Desktop builds，但没有真实 OAuth、云端提交、P5-D replace、真机身份或跨设备恢复；这些仍由 P7-E 在明确 target/授权后完成。

**P7-E 跨设备恢复与部署验证就绪（本地 typed restore 主体与 Desktop 本地退出门完成，不能宣称 P7 或项目完成）**：合同为 `P7E_CROSS_DEVICE_RESTORE_AND_DEPLOYMENT_READINESS_CONTRACT.md`，可复验本地证据为 `P7E_LOCAL_EXIT_EVIDENCE.md`。P7-A envelope 的独立 `nfai.sync.restore-plan.v1` 现在在 plan 阶段强制 `nfai.sync.semantic-record.v1` typed mapping；Android 的 DAO semantic source → fresh Room candidate typed writer → reference/integrity/semantic cold-open readback → checkpointed atomic switch/rollback 已有本地合同，raw Room table prototype 明确被 gate 拒绝。Desktop `DesktopWorkspaceStore` 持有隔离 workspace owner，并只提供无 Tauri command 的内部 open/read/seal bridge，P6 当前 workspace 不被读取、替换或暴露为 sync target。`LOCAL_TEST_ONLY` fake RPC 仍只在测试，覆盖 expected revision、idempotent receipt、wrong-code/tamper/cross-scope/high-sensitive/interrupt/rollback；P5-D SQLite backup 与 P6 exchange 不混入 sync。本轮完整 Desktop 前端/Rust/Tauri/ad-hoc strict verify 已通过；Android 6 项 owner/writer 定向契约重证 checkpoint、切换后新 Room readback 与 receipt replay。尚缺可见 verified account/recovery/remote envelope 入口、用户授权的真实 process-kill 恢复、Google/Supabase target 与授权、HTTP/OAuth、跨网络、真实 Android/Desktop 和部署回读；它们到位前不能把 code 45/`0.3.0-p7e` 的构建或模拟器说成真实服务退出。

补充本地接合事实：Android `AppContainer` 已将 typed semantic source、P7-A seal/open、P7-B READY/recovery/direction/key-vault gate、P7-D durable remote revision/hash guard、restore plan 和 atomic writer 连接为唯一 non-UI/non-worker owner；普通 UI 仍明确 disabled，不能伪造账号或云成功。secret-free receipt 可让重复 intent 只回读既有结果。该本地接合已由 69 JVM 测试类四分片、lint、正式签名 APK 和同签名模拟器 cold start 验证；Desktop owner bridge 亦补有 reopen isolated-workspace readback。真实 Google/Supabase/OAuth/跨网络/OPPO 与完整 Desktop packaging 仍是独立外部门；P7-E 不能当作项目终点，真实外部门明确后才进入 P8。

**前置**

- 进入条件见 11.4；真实跨设备需求成立。

**范围**

- Google Credential Manager、Supabase Auth、恢复码、AES-GCM 加密快照、RLS/RPC。
- 自动同步、冲突、账号切换、退出保留数据、头像安全缓存。
- Android/Desktop 跨设备恢复。

**禁止提前**

- 不上传业务明文、API Key、Token、恢复码或头像缓存；不静默覆盖本地数据。

**退出证据**

- 空设备恢复、非空方向选择、冲突、切换、退出和回读哈希测试。
- 真实 Google、Supabase、Android 真机和 Desktop 分别验证。

**可调整顺序**

- 若 Desktop 正式使用前就出现明确多设备需求，P7 可在 P6 中后段并行设计，但必须等本地协议稳定后实现，且不能阻塞 Desktop 的本地离线模式。

### P8：受控 Agent

**前置**

- 对话、Projects、知识、记忆、Tool Schema、权限和审计成熟。

**范围**

- 只读研究 → 草稿 → 可撤销本地动作 → 高风险外部动作逐级开放。
- 计划、预算、暂停、恢复、确认、回滚和运行记录。
- Durable Run、Checkpoint、事件序号、风险等级、UNKNOWN 失败关闭、沙箱和幂等 Side Effect。

**禁止提前**

- 不让模型直接执行高风险动作；不因 Agent 名义扩大现有授权。

**退出证据**

- 越权、Prompt Injection、工具错误、预算、取消、重放和回滚测试。
- 每个工具有真实成功、失败、取消和审计链路。
- 暂停后恢复、进程重建和重复事件不会造成外发、写入、购买、删除或跨应用动作重复执行。

**P8-A 当前本地接合（2026-08-13）**

- `P8A_CONTROLLED_AGENT_LOCAL_RUNTIME_CONTRACT.md` 已冻结：`ControlledAgentRuntime → AgentLedger` 是唯一计划/预算/风险/权限/暂停/取消/恢复/rollback 入口。Tool schema v1、未知失败关闭、`null` 未知与 `0` 明确零、单调 Run/Step/Event/Checkpoint/Receipt、稳定 idempotency key 和安全审计字段均已定义。
- Android Room Schema 19→20 仅追加 secret-free Agent ledger；Desktop 追加隔离 `p8-agent-ledger-v1` SQLite `user_version=1`。两者只记录 hash/状态/序号/安全错误，不记录正文、Key、Provider、路径或 URI；P3/P5/P6/P7 账本保持独立。
- 只有定向测试显式创建 `LOCAL_TEST_ONLY` fixture registry（只读 research、草稿候选、可撤销 fixture action）；正式 Android DI/UI 与 Desktop Tauri command 没有 Agent registry/executor。没有 Provider、HTTP、跨应用、系统文件、购买、删除、外发或自动 Agent。
- 当前已覆盖本地 unknown、权限/风险、prompt-injection 作为不可信数据、tool error、预算耗尽、暂停/取消/恢复、checkpoint、replay、rollback、Room migration/readback 和 Desktop reopen。真实外部工具、可见 Agent UI、P9/P10、OPPO/Windows 与真实 Provider 均仍是独立后续门。

**P8-B 当前本地 harness 接合（2026-08-13）**

- `P8B_READ_ONLY_AGENT_LEDGER_STATUS_CONTRACT.md` 冻结为：production Android/Desktop 仅有不可见的 secret-free ledger aggregate 状态入口；没有 registry、executor、UI、Tauri command 或自动 Agent。
- Android/Desktop `LOCAL_TEST_ONLY` harness 新增 explicit plan→approval token→approved execution。plan 只持有 stable tool/idempotency/input hash；未知、预算、risk、permission、未知 tool、外部 effect 全部失败关闭。approval 以 P8 Event/Checkpoint 审计 plan hash，token 原文不落账；reopen 不会自动继续。
- 测试内的 failure/cancel 注入、receipt replay、duplicate event 同指纹回读/冲突拒绝、pause/resume、rollback 与 SQLite/Room reopen 是本地合同证据；fixture 不代表任何真实工具或用户 Agent 成功。

**P8-C 当前可见本地最小闭环（完成，2026-08-13；P9 未开始）**

- Android 已冻结唯一 production-safe 动作 `p8c_local_ledger_inspect`：它无输入、只读 P8 secret-free ledger，声明 `READ_ONLY/LOCAL_READ/NONE` 和预算 `1/1/0`。显式 plan 后再显式 approval；token 仅内存、绑定 plan/tool/input hash、90 秒过期、one-shot，重建不会执行。UI 如实显示本地受控运行、无模型/外部工具、Run/Step/Event/Checkpoint/Receipt、风险/权限、unknown 与 0、pause/resume/cancel。
- Desktop “本地受控记录”是用户可达的只读 inspect 页，唯一调用 `inspect_p8_agent_runs`，返回 safe Run/Step/Event/Checkpoint metadata，不含 executor。精确 Tauri capability 仅允许该 command；实际新 `.app` 两次启动回读已知空账本和“无 production executor”。frontend/Rust/Tauri/ad-hoc、Android 正式签名与 emulator hash readback 已分层完成；Provider/HTTP/Key、真实工具、文件/跨应用、高风险动作、OPPO/Windows、发布和 P9 仍未开始，不能由 P8-C 推定通过。

**P8-D 本地退出与红队审计（完成，2026-08-13；P8 本地主体退出）**

- `P8D_LOCAL_EXIT_RED_TEAM_AUDIT_CONTRACT.md` 对 P8 requirement→authoritative evidence 逐项审计：unknown Tool Schema/risk/permission/budget、untrusted injection、success/failure/cancel/timeout/reopen、approval binding/expiry/one-shot、pause/resume/cancel、checkpoint/replay/idempotency、rollback、稳定 event sequence、audit minimization 和双端 release surface 都必须有精确证据。
- P8-D 修复 Android production cancel 后仍可能消费内存 approval 的缺口；取消或 expiry 后不再写 approval/Step/Receipt。`fixture_timeout` 只存在于 `LOCAL_TEST_ONLY` harness，和 failure/cancel 一样产生明确 durable terminal，不触及外部资源。Android production `p8c_local_ledger_inspect` 有真实本地 success/cancel/safety rejection，但工具体无外部 I/O，故不伪造外部失败；Desktop production 仍只有 inspect，非空只由 internal harness/reopen 证明。
- Android full JVM/lint/formal signed Debug/Release/emulator hash 与 Desktop frontend/Rust/Tauri/ad-hoc/实际 app read-only inspect 均已重新建立；详见 `P8D_LOCAL_EXIT_RED_TEAM_AUDIT_EVIDENCE.md`。因此 P8 可标为“本地主体退出”；真实外部工具、Provider/Key/HTTP、文件/跨应用、购买/删除/外发或其他高风险动作仍必须未来独立授权与验收。P9 可作为下一阶段，但本轮未开始。

### P9：南枫生态协议接入

**前置**

- Integration Contract、权限 UI 和至少一个目标应用稳定入口完成。

**范围**

- 按一个应用一个只读闭环接入，再逐步开放候选写入。

**禁止提前**

- 不跨应用直接改库，不复制密钥，不以全盘权限换便利。

**退出证据**

- 来源、权限、预览、目标应用确认、结果回读、撤销和审计完整。

**P9-B 本地基础（完成，不是目标接入）**：在 P9-A 的真实入口缺失结论下，`P9B_LOCAL_TEST_ONLY_INTEGRATION_CONTRACT.md` 已冻结 schema/parser/preflight/state/secret-free ledger 和合成非敏感 harness。Android 20→21 与 Desktop 私有 SQLite 仅记录 opaque handle、hash/revision/state/safe audit；release 不增加 Manifest query、Provider/Service、target registry、网络、跨应用调用、DI/UI/Tauri command。它只能证明未来 Adapter 的本地验证底座，不能证明任何真实目标应用、权限或 P9 退出。

### P10：可选 AI Hub

**前置**

- 15.1 的触发条件成立，已有至少两个真实应用消费者。

**范围**

- Registry、健康、路由、成本聚合、可选 KMS 和后期 Agent Runtime。

**禁止提前**

- 不迁移业务数据库，不成为唯一调用路径或启动依赖。

**退出证据**

- Hub 正常、慢、离线、配置回滚和本地直连降级均通过。
- 多应用数据隔离、最小遥测和运维恢复通过。

### P11：长期运营与持续演进

**范围**

- 模型目录更新、Provider 退役、价格变更、Harness/Eval 迭代。
- Schema 迁移、隐私删除、依赖升级、安全公告和备份演练。
- Android/Desktop 发布节奏、生态合同兼容和 Hub 容量治理。
- 第三方依赖、许可证、企业目录边界、传递依赖和数据外发行为的定期复核。

**持续门禁**

- 每次版本都声明改变了什么、验证了什么、未验证什么和怎样回滚。
- 外部动态事实定期重新核验；旧模型和旧协议有退役窗口与迁移提示。

## 18. 每一阶段统一执行五步法

无论处于 P1 还是 P10，都按以下五个工程阶段执行；这与产品路线 P0–P11 是两条不同维度。

| 工程阶段 | 必须产物 | 退出依据 |
|---|---|---|
| 1. 需求与合同 | 目标、入口、所有者、状态、失败、禁止项、决策记录 | 需求不冲突，未实现项没有被写成已实现 |
| 2. 实现与定向测试 | 最小实现、领域/入口/迁移/错误测试 | 受影响入口全部覆盖，没有平行真值 |
| 3. 视觉与交互 QA | 页面/状态/视口矩阵、截图、无障碍和交互检查 | UI 规格通过；不代替功能或真机证据 |
| 4. 真实环境验收 | 模拟器、真实 Provider、真实文件、目标真机/桌面 | 真实链路通过；各验证面分开记录 |
| 5. 收尾与交付 | 清理、文档、交接、Release、哈希、回读 | 可复现交付，无秘密、无临时证据冒充正式资产 |

开始阶段前必须有 `CURRENT_HANDOFF.md`；阶段结束后更新验证等级和下一唯一候选任务。

## 19. 测试与证据矩阵

### 19.1 测试层级

- Domain：状态机、规则、权限、Candidate、消息树、记忆和路由。
- Repository：Room 迁移、事务、附件引用、导出导入、删除和恢复。
- Provider Contract：Mock 与真实 Adapter 使用相同上层合同。
- Integration：完整 Use Case、取消、重试、幂等、故障和并发。
- UI：关键页面、状态、输入、系统分享、旋转/重建和无障碍。
- Performance：TTFT、流式、长会话、文件解析、搜索、快照和内存。
- Security：Key 泄漏、路径穿越、Prompt Injection、跨项目/跨账号、日志与导出。
- Eval：捕获 Schema、指令遵循、长上下文、记忆、路由、Fallback 和工具。
- Stream/Replay：事件顺序、重复、断档、重连、部分输出、Checkpoint 重放和 Side Effect 幂等。
- Supply Chain：精确版本、许可证、传递依赖、企业目录隔离、依赖漏洞和数据外发清单。

### 19.2 证据等级

每次交付分别报告：

1. 文档/设计方向；
2. 已实现代码；
3. 领域与入口契约；
4. 构建和静态检查；
5. 自动测试；
6. 模拟器或本地环境；
7. 真实 Provider / 云服务 / 文件；
8. 真实 Android 设备或目标 Desktop；
9. 正式签名、远端资产和回下载验证；
10. 未验证风险。

较低等级永远不能替代较高等级。

### 19.3 Android 设备边界

- 自动化测试限定在指定模拟器。
- OPPO 只允许同签名主 APK 覆盖安装和黑盒验收。
- OPPO 不卸载、不清数据、不安装 `androidTest`/辅助 APK、不运行 instrumentation 或 `connected*AndroidTest`。
- 涉及真实个人资料上传、覆盖、恢复或高风险写入必须另获用户授权。

## 20. 发布、回滚与维护

### 20.1 发布门

- 版本范围、Schema、依赖和动态模型目录已冻结到可复现快照。
- Debug/Release、签名/未签名产物和测试/正式服务明确区分。
- Release 构建、目标测试、真机、升级迁移和回滚验证通过。
- 交付包含源码、README、真实预览、正式资产和 `SHA256SUMS.txt`。
- 远端发布后重新读取资产列表，下载并校验 SHA-256。

### 20.2 回滚

- App 更新回滚不能依赖清库；数据库迁移需前向恢复或备份策略。
- Provider/Model 目录保留上一稳定版；动态配置失败可回滚。
- Harness 和 Prompt 变更保留版本，可将失败任务回放到测试夹具。
- Hub、同步和生态连接失败时可单独禁用，不影响本地核心读取。

### 20.3 退役

- 模型退役不改写历史 Invocation，只提示当前不可重用。
- Provider 退役先停止新配置，再给迁移和导出窗口。
- 数据字段或协议退役先提供读取兼容和迁移统计，不直接删除未知数据。

## 21. 复评触发器

出现以下情况时，不应机械执行旧计划，必须先更新本文和决策日志：

- 用户改变首发平台、本地优先、捕获/对话顺序或 Hub 边界；
- OpenRouter 无法满足目标 Claude 文本、视觉、流式、用量或可靠性；
- 真实使用显示捕获闭环没有价值或对话需求显著改变；
- Android 数据协议无法支持 Desktop 精确保真迁移；
- 出现明确多设备需求，需要调整 Desktop 与同步顺序；
- 需要第二 Provider、原生 Claude 能力或特殊 Harness；
- Provider、平台政策、隐私法律、商店规则或依赖安全发生重大变化；
- 某阶段连续两个增量无法达到退出门槛，或维护成本明显超过价值。

复评必须记录：当前事实、变更原因、受影响所有者、迁移/回滚、放弃方案和重新验收范围。

## 22. 防遗漏检查表

每次新阶段规划和结束时逐项检查：

### 产品与范围

- [ ] 当前工作属于哪一个 P 阶段？
- [ ] 是否满足前置条件？
- [ ] 是否误把远期能力提前加入？
- [ ] 是否仍遵守“捕获闭环后立即转对话”？

### 架构与数据

- [ ] 概念是否有唯一所有者和生产消费者？
- [ ] UI、Adapter、Hub 是否绕过 Application/Domain？
- [ ] 稳定 ID、Schema、来源、版本和迁移是否完整？
- [ ] Android、Desktop、Sync 和生态是否依赖同一协议真值？

### AI 与 Provider

- [ ] Provider、Model、Harness、Task、Invocation 是否分离？
- [ ] 当前 Model ID、价格和能力是否实时核验？
- [ ] 流式、取消、错误、Token、费用和缺失值是否真实？
- [ ] AI 结果是否仍先作为候选？
- [ ] Provider 原始事件是否只在 Adapter 内部，UI 是否只消费版本化内部事件？
- [ ] 价格未知 `null` 与已验证零成本 `0` 是否分开？

### 隐私与安全

- [ ] 外发是否逐次确认并显示真实范围？
- [ ] Key、Token、恢复码、原图和敏感正文是否排除日志/导出/同步？
- [ ] 附件、网页和工具结果是否按不可信输入处理？
- [ ] 删除、覆盖、跨账号和跨应用写入是否可预览、可撤销或二次确认？

### 体验与状态

- [ ] 空、等待、运行、停止、成功、失败、取消、重试和离线是否真实？
- [ ] 紧凑/展开形态是否分别设计？
- [ ] 错误是否给出中文原因和可行动建议？
- [ ] 页面存在是否被误报为完整功能？

### 验证与交付

- [ ] 定向测试、构建、模拟器、真实服务、真机和发布证据是否分开？
- [ ] 失败路径、迁移、恢复、幂等和性能是否覆盖？
- [ ] 正式资产、签名、哈希、远端读取和回下载是否完成？
- [ ] `CURRENT_HANDOFF.md` 是否只留下一个明确下一候选任务？

### 第三方参考与供应链

- [ ] 新依赖是否记录精确版本、许可证、传递依赖、维护状态和替代方案？
- [ ] 是否误把公开仓库研究链接当成源码复用许可？
- [ ] AGPL、社区许可证、修改版许可证和企业目录是否保持只读研究边界？
- [ ] 外部组件的遥测、远程生成、分享和云上传是否逐项核验，而不是依赖一个总开关？

## 23. 当前状态与下一入口

> **2026-08-31 当前状态覆盖。** 本节以下早期阶段清单全部降为历史路线，不再直接参与排程。当前 Android checkpoint 为 `d6db5bf`；当前行为按本文件顶部合同路由读取，当前验证、APK、OPPO 和未验门按 [当前交接](CURRENT_HANDOFF.md) 顶部读取，跨域完成范围按 [总控完成审计](MASTER_PLAN_COMPLETION_AUDIT_20260816.md) 顶部读取。

### 当前已完成且后续只回归

- Android 对话／工作壳、Composer、会话管理、搜索与文件操作已经形成共享入口；ChatGPT／Claude 导入内容、南枫转写原文件与 Markdown、普通附件统一进入搜索、预览、打开、复制／下载／分享和 owner 生命周期。
- 模型服务已形成统一目录、Provider／Adapter、Auto／手动顺序、附件材料桥、标题与历史资料后台路由、调用记录和费用投影；新增模型必须同时经过目录、权限、能力、附件、Auto、设置、账本与回归门，不能只加入选择列表。
- 本机数据统计按活动 owner、唯一受管文件与当前物理字节计算；无 owner 残留单列并只经显式清理处理，南枫转写和导入文件不能再绕开排序、搜索、统计或引用保护。
- 当前 Android Room 为 Schema 63；最终全量 JVM 为 `1042 tests / 0 failures / 3 skipped`，`lintVitalRelease` 与 `assembleRelease` 通过。具体失败项、APK 和设备哈希不在本蓝图重复维护。

### 当前仍需独立关闭的门

1. 4 项历史 JVM 合同失败必须单独判断“产品代码缺陷还是静态合同过时”，不得为追求全绿放宽 owner、安全或当前产品合同。
2. 当前重建 Release 尚未覆盖 OPPO；只有再次获得明确授权后，才可按同包名、同证书、非 Debug、保数据流程覆盖并回读。构建成功不等于设备升级。
3. Qwen、GLM、DeepSeek 等真实 Provider 的时延、推理 Token、账单、搜索终态和附件链仍需用户合法配置下逐项验收；不得用定向 JVM 或估算金额代替。
4. 搜索完整性、实际存储体积、南枫转写预览、费用排版、待看排序／圆点及暗色入口仍需目标 OPPO 视口回读；真机视觉不替代数据与 Provider 证据。
5. Desktop 只保留既有已验证能力；Android 近期新增模块若要跨端，须另建明确范围和证据，不因名称相同自动视为已同步。

### 后续复查固定入口

1. 先核对当前 HEAD、工作树与 `AGENTS.md`，发现未提交并行工作时先隔离范围。
2. 读取本文件顶部 2026-08-30 总控门，只确定长期方向和现行文档路由。
3. 读取 `CURRENT_HANDOFF.md` 顶部，只取得最新构建、测试、APK、设备和外部服务事实。
4. 按受影响领域读取一份现行合同；旧 P 阶段文档、截图和历史哈希只用于追根因。
5. 已完成能力只做防回退验证；只有代码、合同或真实证据证明不一致时才重新实现。
6. 结论冲突时按“当前用户指令 → 当前代码与可复现证据 → 现行合同 → 总控门 → 历史记录”裁决，并同步纠正唯一现行入口。

### 2026-08-16 历史状态覆盖（已失效）

本节以下清单保留总方案早期推进轨迹；其中 Schema、Provider 状态、附件能力、搜索范围、测试数、APK、设备和“下一唯一入口”均不得视为当前事实。

### 历史已完成（截至 2026-08-16）

- Git 仓库和 `docs/` 需求归档已建立。
- 原压缩包与新增三份需求已阅读、统一和保留原件。
- 产品顺序、Android 技术方向、V1 部署边界、OpenRouter 与 Claude 优先级已确认。
- 产品简报、项目理解、架构治理、领域规则、决策日志、近期实施计划和本总控蓝图已形成文档基线。
- 用户已于 2026-08-12 确认本总控蓝图，P0 的产品与架构方向门已通过。
- 已完成 GitHub 成熟项目研究，并把对话、Provider、知识、记忆、Eval、Agent、安全和许可证经验整合到本蓝图。
- 已创建 P1 任务合同、Android 单模块工程、领域合同、Mock Provider、内存 Repository 和定向测试源码。
- P1 的 6 项定向测试、Lint 与 Debug 构建已通过；本地工程级退出门完成。
- P2-A 已完成 Room 与私有附件底座；P2-B 已完成 Photo Picker 与私有图片草稿；P2-C 已完成手工文本和 `ACTION_SEND text/plain`；上述捕获链路已通过自动验证与 API 35 模拟器生命周期验收。
- P2-D 已完成 OpenRouter 固定端点、旗舰/均衡/快速策略、Keystore AES-GCM 凭据、唯一纯白模型设置选择面和结构化错误映射；23 项自动测试、Lint、模拟器重启恢复与正式签名 Debug/Release 构建通过。
- P2-E 已完成本地版本化 OpenRouter Registry Snapshot、上一稳定版保留、非联网 JSON 请求/响应合同、`null ≠ 0` Token/费用语义和 `Task Run → Provider Attempt → Generation → Validation` 运行层级；28 项自动测试、Lint、正式签名 Debug/Release 及 API 35 模拟器同签名覆盖安装与冷启动通过。
- P2-F 已完成本地 Invocation Room Ledger 与调用记录 Dialog：Schema 2 用单事务保存四层结构，稳定 ID 幂等、按完成时间倒序、Schema 1→2 保留旧草稿、进程重建可回读；成功/失败/取消/阻止及 Token/费用未知语义均有本地契约。调用记录绝不保存 Key、完整 Prompt/响应、原图或附件正文；本地 Mock 被明确标为非真实服务。
- P2-G 已完成本地确认面与 Mock 可见闭环：`ConfirmAiRequest` 预览并要求逐次勾选，取消不产生 Attempt；Mock 的实际标识、图片数、文本范围、隐私与 `CNY 0`/未知 Token 语义可见。成功后 Candidate 独立于 Ledger 进入 Schema 3 的待核对状态，Activity/进程重建可恢复；编辑、取消与稳定幂等保存严格晚于用户确认，才会创建 Knowledge。
- P2-H 已完成正式 Knowledge 最小读取：`ReadKnowledgeLibraryUseCase` 只读取 Room Schema 3 已保存事实，列表按保存时间与 ID 稳定倒序，空态不填充 fixture；详情回读完整知识正文、来源类型/安全来源引用、Candidate/Invocation/Provider/Model/Harness 元数据，并明确排除 Key、完整 Prompt/响应、原图与附件正文。Candidate 保存映射与状态可经 Repository 重建回读。
- P2-I 已完成正式 Knowledge 的最小本地导出：`KnowledgeRepository → ExportKnowledgePackageUseCase → KnowledgeExportStore` 只消费已保存 Knowledge，生成 Manifest + `knowledge.json` 的 `.nfai` 文件；临时写入、关闭/同步、原子落地后从同文件解析并核验 SHA-256。附件只保留 ID/MIME/大小/哈希，空库/取消/失败不产生成功文件，进程重建会重新验证最新有效包；Schema 3 未变。
- P2-J 已完成真实 OpenRouter 公开 Models API 的只读核验：固定无认证/无正文 GET 只读取目录，不读取 Key、不构造推理请求、不发送文字/图片/Prompt/附件；白名单化模型字段经 SHA-256 版本化并私有原子持久化，失败保留稳定版，启动重算哈希。API 35 模拟器显示已验证 catalog `d537fc08bb62` 并在冷启动后回读；这不代表真实调用、费用、Token、真机或发布。
- P2-K 已完成真实推理前的传输边界：`ConfirmAiRequest → RunAiTaskUseCase → OpenRouterInferenceAdapter → OpenRouterInferenceTransport` 固定官方 POST、仅内存 Authorization、超时/取消/错误映射、同一幂等 Key 的单次可重试、响应白名单投影，以及 Registry/Provider/Model/逐次 Consent/费用提示/能力/非敏感清洗文本/Credential/egress policy 的全量 HTTP 前门禁。AppContainer 永远注入 `Disabled` policy；全量 56 项自动测试（含 4 项 P2-K fake/loopback）、Lint、正式签名 Debug/Release、v2/v3 验签和 API 35 同签名覆盖冷启动均通过。模拟器只确认“传输准备就绪 · 真实服务尚未授权”，本阶段没有读取真实 Key、官方推理外发或真实服务成功。
- P2-L 已完成真实服务前的离线验收层：版本化 `RealServiceRunSpec` 冻结 Snapshot、合成夹具、输入/输出、费用硬上限、Credential handle、超时/重试、Consent 指纹和预期 Ledger/Candidate/Knowledge 状态；DryRun 无 Transport/Authorization/Key loader，报告零网络、无 Key/正文，未知费用不写作零。单次 nonce 精确绑定 Spec 并在 app-private 重建后拒绝重复。模型设置仅显示验收准备/等待授权与未执行事实；没有真实调用按钮或 policy 变更。
- P2-M 已把用户唯一授权的合成文本 RunSpec 接到专用执行器：DryRun 通过后才原子消费 nonce，并只以 `ExactSingleUseRun` 发起最多一条 Attempt。实际 API 35 执行在 Key 占位检查处 BLOCKED（`ProviderCredentialInvalid`），没有 OpenRouter Attempt、HTTP、Token、费用、Candidate 或 Knowledge；nonce 保持 CONSUMED，不能重试。

### 历史未完成（截至 2026-08-16）

- 已实现但默认禁用 OpenRouter 推理 HTTP Transport；P2-M 的唯一短暂例外已安全阻止。用户已决定把真实 OpenRouter 成功调用、Token/费用与目标真机作为后期独立验收债务，不阻塞 P3 主体开发；Knowledge 搜索/图谱/编辑管理和导入仍未开始。
- 本机当前凭据为明显占位值，尚未配置或使用可用真实 OpenRouter Key。
- 真实 OpenRouter、真实 Android 设备与发布验证仍未执行；模拟器本地设置通过不替代任何真实服务结论。

### 历史下一入口（截至 2026-08-16）

P3-A 至 P3-H 已完成本地 Conversation 底座、确定性事件状态机、继续/重试/换模型谱系、安全展示/草稿恢复、管理/导出、不可变用户消息修订/完整分支历史、安全附件本地引用与只读尝试历史投影；下一候选仍须继续 P3 的未完成增量（真实流适配、真实 Usage 可见追踪与在明确合同下的对话附件外发等），P3-H 不是项目终点。真实 Provider 保持 `Disabled`，只使用 Mock、fixture 与 loopback。真实文本、图片（另行授权）、Token、费用和目标真机保留为后期独立验收债务；搜索、图谱、系统分享图片、拍照、账号、同步、Hub 与 Agent 仍按蓝图阶段推进，不因本决策提前。

真实 OpenRouter Claude 调用仍需单独满足：用户提供或授权本机测试 Key、可用额度和非敏感测试文本/图片。总方案确认不等于真实外发授权。
