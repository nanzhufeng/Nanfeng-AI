# 南枫 AI 项目理解报告

> 初始基线：Final Codex Package v3.0（2026-08-09）  
> 新增需求：2026-08-12 三份产品、架构与对话体验材料  
> 当前阶段：项目初始化、需求统一与总方案成稿，尚未进入代码实现

## 1. 结论

南枫 AI 是本地优先的个人多模型 AI 工作台，也是个人信息捕获、知识沉淀和未来 Agent 自动化的统一入口。它不是单一 Claude 客户端，也不是 AI Hub 的前端壳。

当前产品路线已经从“Windows 主力端、Android 移动入口”更新为：

> **移动端优先，但架构从第一天支持桌面端。**

手机首先承担随时输入、分享、拍摄、语音、查询和快速决策；桌面端在移动 MVP 稳定后承担知识管理、模型配置、工作流设计、Agent 调试和深度生产。

产品优先级已经进一步确认：先用收窄的“快速捕获与知识沉淀”闭环建立第一份真实价值，随后立即把开发重心转向用户最需要的“Claude 级多模型对话体验”。捕获是第一落点，对话是后续核心主产品，不能把对话长期推迟到知识库或生态功能之后。

系统采用“逻辑 Core + 多 Client + 独立 Provider Framework + 可选 AI Hub”的结构。Core 表示跨客户端共享的领域合同、数据协议和 AI 能力内核，不表示强制远程服务器。每个南枫应用都必须保留本地可独立运行的 Provider Framework；AI Hub 只负责集中增强，不能成为单点依赖。

当前仓库仍只有需求与架构文档，没有源码、构建系统或可验证实现。本文中的功能均属于已确认需求、架构方向或待选方案，不能标记为已实现。

## 2. 需求权威分层

### 2.1 产品定位与阶段路线

当前用户指令与 `南枫 AI 开发路线与产品架构思路.md` 决定移动端优先、桌面端后置的产品路线。旧文档中的“Windows 主力工作端”保留为长期平台定位，不再表示第一开发顺序。

### 2.2 Provider、Harness 与 AI Hub 架构

`南枫_AI_Provider_Framework_AI_Hub_通用架构原则_v1_0_20260812.md` 是此领域的架构基线。它要求严格区分 Model、Provider 与 Harness，并要求应用独立运行、Hub 可选、调用可观测、结果可验证、配置可版本化。

### 2.3 对话体验与工程参考

`Claude 级对话产品：可落地开发方案.docx` 提供对话产品质量标准、记忆系统、缓存、路由、数据模型和工程方案。它的产品体验要求应纳入设计；其中 Claude 专属模型路由、Tauri+BFF+Postgres+Redis+S3 等实现建议不能直接覆盖多 Provider、本地优先和移动端优先基线。

## 3. 已确认的产品事实

- 产品名称：南枫 AI。
- 产品定位：个人多模型 AI 工作台与个人 AI 信息入口。
- 长期目标：连接个人数据、知识资产、AI 模型、自动化能力和南枫系软件，逐步形成个人 AI 操作系统。
- 第一用户任务：用户在手机上快速提交文本、图片、文件、语音、视频或网页内容，获得 AI 反馈，并把有价值的信息沉淀为自己可管理的知识资产。
- 核心价值：多模型统一入口、数据由用户拥有、模型透明、Token 与费用透明、长期知识可积累。
- 第一开发平台：Android 移动端，技术栈已确定为 Kotlin + Jetpack Compose。
- 后续管理平台：Desktop / Web；Windows 仍是主要深度生产与控制中心方向。
- V1 数据与部署策略：本地单用户、Provider 直连、本地安全存储密钥；账号、云同步、远程 BFF 和 AI Hub 均不进入 V1，只保留接口边界。
- 产品不绑定单一模型厂商，模型、价格和能力不可硬编码在业务流程中。

## 4. 统一架构理解

### 4.1 主调用链

```text
Client / Feature
    ↓
Application AI Service
    ↓
Task + Validation Contract
    ↓
AI Harness
    ↓
Provider Adapter
    ↓
Provider / Model
```

- Client / Feature：聊天、分享、拍摄、语音、知识整理等用户入口。
- Application AI Service：业务任务、权限、外发确认和保存语义的唯一所有者。
- Task：描述任务类型、能力要求、预算、延迟和验证合同。
- Harness：负责 Prompt、Context、Memory、Tools、Agent Loop、压缩、缓存、重试、Fallback、解析和验证。
- Provider Adapter：处理各 Provider 的协议、能力、错误、流式事件和用量差异。
- Provider / Model：实际模型服务。

业务功能不得直接调用 Provider HTTP API，也不得让 Adapter 反向决定业务数据是否写入。

### 4.2 Core + Client

Core 应拥有跨客户端一致的：

- Conversation、Message Tree 与 Project 协议；
- Provider、Model、Harness Profile 与 Task 合同；
- Knowledge Item、Memory Item 与来源证据合同；
- Invocation、Usage、Cost 与 Error 合同；
- 导入、导出、迁移和可选同步协议。

Android、Desktop / Web 是 Core 的不同入口和读取模型，不得各自发明另一套业务语义。V1 可以只实现 Android，但公共数据和 AI 合同必须能够被未来桌面端复用。

### 4.3 Provider Framework

“OpenAI Compatible”只是一种兼容入口，不能成为唯一抽象。统一的是上层能力接口和内部事件格式，不是强迫所有 Provider 使用同一套 HTTP 字段。

每个 Provider / Model 都要维护 Capability Matrix，至少覆盖：

- Chat、Streaming、Tool Calling、Structured Output；
- Reasoning、Vision、Embeddings；
- 输入/输出上下文限制；
- 缓存能力；
- 协议和认证方式；
- 价格、状态和更新时间；
- 推荐 Harness Profile。

### 4.4 Harness

Harness 是实际产品能力的一部分。模型评估单位应是“模型 + Provider + Harness + 任务”，而不是只比较模型名。

上下文使用 L0–L3 分层：

- L0：稳定产品规则、Project Bible、领域合同和安全规则；
- L1：当前项目状态、配置版本和重要决策；
- L2：当前任务、相关资料、错误和验证结果；
- L3：最近消息、工具调用和短期操作轨迹。

稳定内容放在前缀，动态内容放在后部；长上下文必须支持检索、裁剪、摘要和压缩。百分比阈值只能作为可调配置，不能未经测试写成永久业务规则。

### 4.5 AI Hub

AI Hub 可提供：

- Provider Registry 与 Model Registry；
- 可选集中 Key Management；
- Routing Policy 与健康检测；
- Telemetry 聚合与成本分析；
- Agent Runtime 等跨应用增强。

AI Hub 不得成为业务数据库、知识库本体、唯一 Provider 客户端、所有 Prompt 的唯一存储、应用启动必需服务或单点认证依赖。

Hub 离线时，应用读取本地最后可用配置并直接调用 Provider，同时向用户明确显示已切换到本地配置。

## 5. 对话产品体验要求

“Claude 级”在本项目中表示体验质量目标，至少包括：

- 流式输出、中断、继续、重试和换模型重答；
- 消息树：编辑历史消息产生分支，支持切换版本；
- 当前 Provider、Model、Harness 和用量可追踪；
- 图片、文件、代码等附件成为可管理的上下文，不把长文件全文无差别塞入 Prompt；
- Projects / 工作空间拥有项目级知识和项目级指令；
- 对话、草稿和历史本地保存，应用重启或页面切换不丢失；
- 网络中断后能够恢复或明确失败，不重复计费和重复写入；
- 会话支持重命名、归档、置顶、搜索、Markdown / JSON 导出；
- 长对话虚拟滚动，Markdown、代码、表格和公式增量渲染稳定；
- 记忆对用户可见、可编辑、可删除、可暂停、可撤销，并显示来源。

具体 TTFT、缓存命中率、成本下降幅度和周数估计属于待基准测试目标，不作为未经验证的完成承诺。

## 6. 移动端 V1 边界与顺序

### 6.1 第一落点：最小捕获与知识沉淀闭环

这一阶段必须主动收窄，只证明南枫 AI 能把一次真实输入转化为用户拥有的知识资产：

- Kotlin + Jetpack Compose 应用骨架与本地数据层；
- 文本输入和 Android 系统分享入口；
- 图片已确定纳入首个多模态闭环；文件、语音、视频和网页解析按后续优先级逐项加入；
- 图片先私有保存并展示本地预览；发送前明确显示 Provider、Model、发送内容和可能费用，只有用户主动确认后才能外发；
- 输入先形成带来源证据的草稿；
- 首个真实 Provider 已确定为 OpenRouter，第一测试模型族为 Claude；具体 Claude Model ID 从开发时的实时目录选择，不写死；
- 用户确认 OpenRouter、实际 Claude 模型、发送内容和费用提示后主动发送；
- AI 输出主题、分类、关联、状态和时间等结构化候选；
- 候选结果可预览、修改和取消，经过 Schema 与确定性规则校验后保存；
- 保存结果、来源、Provider、Model、Prompt 版本、时间和用量可追溯；
- 本地数据具备最小导出协议。

完成门槛不是“支持所有输入形式”，而是一条真实输入能够稳定完成“捕获 → 整理 → 核对 → 本地保存 → 再次读取”。达到后即停止扩充捕获类型，转入对话主产品。

### 6.2 后续核心：Claude 级多模型对话

这是用户后续最需要的能力，应紧随最小捕获闭环进入主开发阶段：

- 流式对话、停止、继续、重试和明确错误恢复；
- Provider / Model 选择、会话模型记录和换模型重答；
- Conversation、Message、Invocation 和 Usage 的本地持久化；
- 编辑历史消息产生分支，并可切换消息版本；
- 草稿恢复、附件引用、长对话滚动和稳定 Markdown 渲染；
- 会话搜索、重命名、归档、置顶与 Markdown / JSON 导出；
- 项目级指令、知识摘要和可见可控的长期记忆；
- Context 分层、压缩、缓存、第一批离线 Eval 与回归测试；
- OpenRouter Adapter + 模拟 Adapter 先证明业务层不绑定具体网络实现；后续按 Claude 原生能力需要增加 Anthropic Adapter。

### 6.3 V1 明确不进入的能力

- 登录、账号中心和多用户体系；
- 云同步和跨设备恢复；
- 远程 BFF、远程 AI Hub 与集中 KMS；
- Desktop / Web 客户端；
- 复杂 Agent 工作流和南枫生态全面整合。

“每日 AI 助手”保留为产品方向，但要在捕获闭环和 Claude 级对话稳定后评估，不能用自动推送或后台写入抢占这两条主线。

## 7. 后续阶段

### Phase 2：知识资产

- 知识库浏览、搜索、编辑、标签、时间线和关联分析；
- 跨来源去重、归并、来源追溯和可导出协议；
- Desktop / Web 控制中心开始复用同一 Core。

### Phase 3：AI Agent

- 自动研究、提醒、分析和受控任务执行；
- Tool Calling 结果验证、任务级路由、Retry 与 Fallback；
- 高风险写入始终由确定性代码校验，并保留预览、撤销或异常确认窗口。

### Phase 4：南枫生态整合

- 南枫记、南枫知识库、南枫智投、南枫八字、视频与本地工具；
- 每个应用继续保有独立 Provider Framework；
- AI Hub 聚合模型目录、策略和安全运行元数据，不接管各应用业务真值。

## 8. 核心概念与唯一所有者

| 概念 | 产品含义 | 建议的唯一所有者 | 主要消费者 |
|---|---|---|---|
| Task | 一次 AI 任务的能力、预算、延迟和验证要求 | Task Domain | Harness、Router、Validation |
| Provider | 第三方模型服务入口 | Provider Registry | Adapter、设置、健康检测 |
| Model | 可选择且可追踪的模型资产 | Model Registry | 选择器、Router、统计 |
| Capability | Provider/Model 实际支持的能力 | Capability Matrix | Harness、Router、UI |
| Harness Profile | Prompt、Context、Tools、缓存与验证策略 | Harness Registry | Task Runner、Benchmark |
| Conversation | 用户拥有的对话资产 | Conversation Domain | 聊天、历史、导出 |
| Message Branch | 可编辑、可切换的消息版本树 | Conversation Domain | 聊天 UI、上下文构建 |
| Invocation | 一次实际调用的安全运行元数据 | Invocation Ledger | 聊天状态、统计、诊断 |
| Knowledge Item | 从输入沉淀的可追溯知识资产 | Knowledge Domain | 搜索、知识库、Agent |
| Memory Item | 可见、可控、带来源的长期用户记忆 | Memory Domain | Context Builder、记忆 UI |
| Price | 带来源、生效时间和币种的计价规则 | Pricing Catalog | Cost Analytics、预算控制 |
| Hub Policy | 是否使用 Hub 及其离线回退策略 | Hub Integration Adapter | Provider 调用链路 |

这些所有者是当前架构合同建议，尚未由代码验证。

## 9. 数据、密钥与隐私边界

- 对话、附件、Prompt、知识、记忆和业务数据默认本地保存；云同步必须可选。
- V1 API Key 只在本机使用 Android Keystore 保护，不进入业务数据库、日志、导出、截图或普通备份；远程 KMS 不进入 V1。
- 原始 Prompt、响应、附件和个人资料不进入长期 Telemetry；默认只记录安全运行元数据。
- 每次向第三方服务外发前，必须显示 Provider、Model、发送内容范围和可能费用，并由用户主动确认。
- 外部网页、附件和 Tool Result 都是不可信输入，不得自动写入长期记忆或直接成为正式业务事实。
- 记忆禁止自动保存密码、密钥、证件、银行卡、他人隐私和其他敏感内容；显式记忆写入必须可见、可撤销。
- 所有查询和记忆操作必须按用户强隔离；路径和文件工具必须防止越权与路径穿越。
- AI 自动分类、摘要或建议是候选结果；金额、收益、账本、删除、文件写入等高风险动作由确定性代码验证。
- AI Hub 默认只接触模型目录、路由策略和安全运行元数据；是否允许接触原始对话内容必须另行明确授权。

## 10. 模型目录与成本

2026-08-12 已用 Anthropic 官方 Claude Platform 文档核验，Word 中的 Fable 5、Opus 5、Sonnet 5、Haiku 4.5、Models API、上下文与基础价格信息具有当前官方依据。

但这些信息仍是可变外部事实，工程必须遵守：

- 模型和能力由 Registry 动态同步并保留版本；
- Provider 更新后先检测、验证，再发布到用户可选目录；
- 价格带币种、来源、生效时间和版本；
- 调用记录保存实际 Provider、Model、Harness、Token 和价格版本；
- 缺失用量字段保存为 `null`，不能伪造；
- 成本比较优先看成功任务成本、重试、Fallback、缓存和返工，不只看每百万 Token 单价。

## 11. 已解决的文档冲突

### Windows 优先与移动优先

采用最新路线：Android 移动端先行，Desktop / Web 后置；Windows 保留为长期主力控制与生产端。

### Core 与本地优先

Core 是逻辑内核和共享协议，不是强制云服务。移动端 V1 必须能在没有远程 Core 或 AI Hub 的情况下使用本地数据和 Provider 配置。

### 云端 BFF 与应用独立调用

BFF/Postgres/Redis/S3 是未来官方级云端增强参考，不是移动 V1 的强制基础。若未来启用，仍需保留本地可用路径和迁移/回退合同。

### 服务端 Key 与本地 Key

本地直连模式使用平台安全存储；Hub/BFF 模式使用服务端 KMS。两者是不同部署模式，不应混成一条隐含依赖。

### Claude 级与多 Provider

采用 Claude 级交互与可靠性目标，但 Provider 层保持多厂商。Claude 专属能力通过 Anthropic Adapter 和 Harness Profile 接入，不进入通用业务层。

### OpenAI Compatible 与原生 Adapter

OpenAI Compatible 可作为常见 Provider 的快速兼容入口；Tool Calling、Reasoning、缓存、多模态或错误语义存在差异时，必须使用独立 Adapter。

## 12. 进入开发前仍需确认

### 会影响工程初始化

- Kotlin、Compose、Android API 和主要依赖的版本基线；
- V1 先在 Android 内实现逻辑 Core，并以版本化协议和合同测试支持未来跨端；是否共享运行时不作为 V1 前置。
- Android 业务数据采用 Room/SQLite、附件采用 App 私有文件目录；具体 Schema、迁移、静态数据加密范围、导出和未来同步字段仍需设计评审。
- OpenRouter 测试 Key、可用额度和允许使用的非敏感测试资料；
- 第一批 Claude 预设的“旗舰 / 均衡 / 快速”映射，以开发时 OpenRouter 实时目录为准；
- 图片以相册选择作为首个实现基线；系统图片分享和拍照分别完成权限、取消、临时文件和真机验证后开放；
- 模型 Registry 的可信来源、同步频率、人工验证与回滚机制；
- Provider 直连模式的网络、证书、超时和错误合同。

### 不阻塞产品理解

- “项目”在移动端的最小形态；
- 每日 AI 助手的生成时机、通知权限和人工确认；
- 记忆自动提取的默认开关与确认策略；
- Desktop / Web 后续采用 PWA、Tauri 或其他容器；
- AI Hub 的部署位置、账户体系和启用阶段。

## 13. 建议的下一步

1. `MASTER_DEVELOPMENT_BLUEPRINT.md` 的 P0–P11 总路线已于 2026-08-12 获用户确认。
2. 下一候选阶段按 `implementation-plan.md` 进入 P1，实时确定 Kotlin / Compose / Android API 版本、逻辑模块边界和本阶段任务合同。
3. 建立 Android 工程、核心领域类型、Mock Provider 和内存 Repository。
4. 用定向测试证明未确认不能发送、Candidate 未确认不能保存、保存后来源与溯源不丢。
5. 再进入本地持久化、图片私有保存和最小导出协议，不提前接真实密钥。
6. 用户明确授权 OpenRouter 测试 Key 和非敏感资料后，验证 Claude 文本与图片调用。
7. 完成最小捕获闭环后停止增加输入类型，立即转入 Claude 级对话主线；后续按总图阶段门继续。

## 14. 当前验证状态

- Git 仓库：已初始化，当前分支为 `main`。
- 初始项目文档：原压缩包 7 份 Markdown 已放入根目录 `docs/`。
- 新增需求原件：2 份 Markdown 和 1 份 DOCX 已原样归档到 `docs/Requirements/`，源文件与归档文件 SHA-256 一致。
- 需求索引：已建立，并记录三份材料的权威角色和冲突解释。
- 决策日志：已记录移动 V1 的产品顺序、Android 原生技术栈和本地直连边界。
- 稳定项目合同：已建立产品简报、架构治理、领域规则和实施计划。
- 总控方案：已建立 `MASTER_DEVELOPMENT_BLUEPRINT.md`，覆盖 P0–P11，并于 2026-08-12 获用户确认。
- 动态交接：已建立并更新 `CURRENT_HANDOFF.md`；P0 已收口，P1 尚未执行。
- 官方事实核验：已核对 Anthropic 当前模型目录、价格和 Models API。
- Word 内容读取：已完成文本提取并生成 16 页渲染；当前无代码修改需求。渲染环境未正确显示源文档中的部分中文字符，因此只确认内容可读取，不宣称原 Word 版式通过视觉验收；原件未被改写。
- 源码修改：无；仓库目前没有源码。
- 构建、测试、模拟器、真实设备、真实 Provider：均未执行，也不在本阶段范围内。
- Git 提交：未创建。
