# 南枫 AI GitHub 成熟项目参考研究

> 研究日期：2026-08-12  
> 研究目的：为总控开发蓝图补充可验证的成熟工程经验；不替代南枫 AI 的产品 Bible、领域规则和当前阶段门。  
> 研究边界：只读取公开仓库、文档、发行与许可证；未复制代码，未 Fork、提交、创建 PR 或接入任何外部服务。

## 1. 结论

没有一个公开项目同时满足南枫 AI 的完整边界：原生 Android 首发、本地业务真值、逐次外发确认、Claude 级多模型对话、知识与可控记忆、调用与成本、未来 Desktop、端到端加密快照、受控 Agent 和南枫生态协议。

因此采用“能力级参考、合同级吸收、自主实现”的路线：

1. 不 Fork 任一产品作为南枫 AI 基底。
2. 不在 P1 引入这些仓库的运行时依赖。
3. 只把经过核验的状态模型、接口边界、失败恢复、测试方法和产品交互写入南枫 AI 的权威合同。
4. AGPL、社区许可证、修改版 Apache 或带企业目录的仓库默认只作设计研究；任何源码复用必须另做文件级许可证审计。
5. Star、发行频率和近期提交只证明项目受到维护，不证明其安全、架构或产品选择适合南枫 AI。

## 2. 成熟度判定方法

候选项目至少满足以下两项才进入主参考：

- 有持续维护、正式 Release、清晰文档或较完整测试资产；
- 已在真实产品中实现南枫 AI 需要的关键状态，而不只是截图或概念 Demo；
- 领域边界、协议、迁移、错误或安全模型可从仓库直接核验；
- 经验能映射到 P1–P11 的明确阶段，不要求提前建设云平台或企业多租户。

仓库活跃度和许可证来自 2026-08-12 GitHub API 快照；功能判断来自仓库 README、Release、代码路径和安全说明。

## 3. 主参考项目与吸收结论

| 项目 | 成熟信号（快照） | 已核验优点 | 南枫 AI 吸收项 | 不照搬项 |
|---|---|---|---|---|
| [RikkaHub](https://github.com/rikkahub/rikkahub) | 6.8k Star、2,400+ 提交、2026-07 有正式发行；Kotlin/Compose/Room | 原生 Android 多 Provider、独立 Provider 实现、模型 Registry、消息分支、MCP、Markdown 与多模态 | P2/P3 的原生移动交互、Provider/Model 分离、分支消息与本地数据库实践 | AGPL-3.0；要求 `google-services.json`；任意自定义 URL/请求体不作为普通用户默认入口；不复制源码 |
| [PocketPal AI](https://github.com/a-ghorbani/pocketpal-ai) | 7.8k Star、77+ 发行、MIT、2026-07 有正式发行 | 端侧模型下载/加载、内存卸载、Token/s 与延迟基准、模型切换、编辑/重试、遥测明确 opt-in | P5/P11 的设备性能基线、模型运行状态、后台/内存生命周期；为未来本地模型保留能力位 | React Native 技术栈和端侧推理不进入 Android V1；不让未来本地模型拖慢 Provider 直连主线 |
| [LibreChat](https://github.com/danny-avila/LibreChat) | 41k+ Star、MIT、持续维护 | 会话中切换端点/预设、编辑/重提/继续形成分支、可恢复流、消息搜索、导入导出、附件和安全沙箱 | P3 的消息树、Invocation 关联、流恢复、草稿恢复、换模型重答、导入导出验收 | Web 多用户部署、Redis 横向扩展和大而全 Agent 不进入移动 V1 |
| [Memos](https://github.com/usememos/memos) | 62k+ Star、4,500+ 提交、MIT、2026-07 v0.30 | 时间线式即时捕获、Markdown 可移植、零遥测、附件独立、Clipper 先预览编辑再保存、稳定 API 与迁移提示 | P2/P4 的低摩擦捕获、Candidate 核对、原文保真、附件关系、版本化 API、迁移前备份和可回导协议 | 自托管服务端、公开/社交可见性不是首版目标；Markdown 只作导出/展示之一，不替代领域真值 |
| [AnythingLLM](https://github.com/Mintplex-Labs/anything-llm) | 64k+ Star、MIT、2026-06 v1.15 | 本地优先 Workspace、文档处理队列与逐文件进度、RAG、模型路由、计划任务运行历史、Telemetry 可关闭 | P4 的 Project/知识范围、长文档异步管线、任务进度、可恢复队列和完整 Run History | Server/Collector 多进程架构和自动定时 Agent 不提前进入 Android；Telemetry 默认保持关闭且不记录内容 |
| [LiteLLM](https://github.com/BerriAI/litellm) | 56k+ Star、核心 MIT、2026-08 v1.96 | 多 Provider Adapter、统一异常、路由/重试/Fallback、能力与价格地图、成本和可观测回调 | P2/P10 的内部统一 IR、Provider 专属映射、版本化价格快照、成功任务成本、有限 Fallback 策略 | 不把所有协议压成最低公分母；不把 Proxy 设为 V1 前置；企业目录不复用；费用仍以真实 Provider 回执为准 |
| [Langfuse](https://github.com/langfuse/langfuse) | 32k+ Star、核心 MIT、2026-08 v4.9 | Trace/Observation/Generation/Score/Dataset/Prompt-Version 分层，可连接 Eval 和费用 | P2/P4 的本地 Invocation 树、Harness 版本、Eval Dataset、Score 与人工反馈分离 | 不在 V1 上传原始 Prompt/响应；远程可观测平台不是默认依赖；企业目录不复用 |
| [Promptfoo](https://github.com/promptfoo/promptfoo) | 24k+ Star、MIT、2026-08 v0.122 | 声明式多模型 Eval、固定数据集、断言、CI 门禁、红队与安全测试 | P4/P8/P11 的离线夹具、确定性断言、模型比较、Prompt Injection 与回归门禁 | 自定义脚本和配置按可信代码处理，隔离执行；不把模型评分当真实用户价值 |
| [Mem0](https://github.com/mem0ai/mem0) | 63k+ Star、Apache-2.0、2026-08 有发行 | Memory add/search/list/get/update/delete/import、作用域实体、历史和摄取控制 | P4 的 Memory CRUD、来源、范围、历史、批量删除、显式摄取与冲突处理 | 不自动把对话交给远程 Memory 服务；记忆候选仍需用户确认，高敏内容默认拒绝 |
| [LangGraph](https://github.com/langchain-ai/langgraph) | 39k+ Star、MIT、2026-08 v1.2.11 | Durable Execution、Interrupt/Human-in-the-loop、短期/长期状态、持久化和分支；恢复时会重放节点 | P8 的 Run Checkpoint、暂停/恢复、审批点、终态和幂等 Side Effect；P3 的类型化流事件可借鉴 | 不在 Android 内直接引入 Python Runtime；重放不能重复外发、写库、购买或删除；模型不是权限所有者 |
| [OpenHands Agent SDK](https://github.com/OpenHands/software-agent-sdk) | 活跃独立 SDK、MIT、2026-08 v1.42 | LOW/MEDIUM/HIGH/UNKNOWN 风险、阈值确认策略、未知风险默认确认、沙箱和纵深防御示例 | P8 的 Tool Risk、Confirmation Policy、UNKNOWN 失败关闭、沙箱边界与终态 | 永不提供普通用户“永远批准/YOLO”默认项；安全分类只是门禁输入，不能代替确定性权限检查 |
| [AppFlowy](https://github.com/AppFlowy-IO/AppFlowy) | 75k+ Star、7,200+ 提交、AGPL-3.0、2026-08 v0.13.2 | 本地数据、跨端、ZIP 备份/导入、回收站恢复、迁移和同步回归测试 | P5–P7 的可恢复删除、导出先于同步、迁移测试、跨端回读和同步状态反馈 | 不复用 AGPL 源码；不共享数据库文件；南枫 AI 仍采用本地真值 + 加密快照，不照搬协同同步模型 |

## 4. 补充观察与反例

| 项目 | 有价值观察 | 限制与决定 |
|---|---|---|
| [Open WebUI](https://github.com/open-webui/open-webui) | 知识/模型/工具权限分层、临时对话、记忆开关；安全说明明确 Tool/Function 创建近似服务器 Root 权限 | 自定义许可证；服务端插件可执行代码。只吸收“工具权限必须当高风险能力”的反例，不作为代码基底 |
| [LobeHub](https://github.com/lobehub/lobehub) | 分支对话、知识库、多 Provider、本地/远程数据库、错误规格 Registry | 社区许可证对派生分发有额外条件；产品已转向 Agent 网络。只吸收错误规格与能力组织经验 |
| [Dify](https://github.com/langgenius/dify) | Workflow/RAG/工具/模型的可视化编排和生产部署经验 | 修改版 Apache，偏云端多人工作流。仅作为 P8/P10 的远期产品对照，不进入 Android/本地数据路线 |

## 5. 对总控蓝图的新增硬合同

### 5.1 对话与流

- 流式输出使用类型化事件包，至少包含 `runId / invocationId / sequence / eventType / timestamp / payloadVersion`；UI 不直接解析 Provider 原始 Chunk。
- 已持久化的完成内容与运行中投影分离；重连或进程重建先读取本地事实，再决定恢复、重试或明确失败。
- 编辑、重试、继续和换模型都产生新节点或新 Invocation，禁止覆盖历史。
- 中途 Fallback 只有在目标协议支持、部分输出语义可解释且用户策略允许时才进行；默认保留部分结果后重试，不假装无缝续写。

### 5.2 知识与长任务

- 捕获入口保持“打开即可输入”，复杂分类放在 Candidate 核对之后。
- 网页、文件和批量资料进入可恢复任务队列：逐项进度、取消、失败原因、重试和已完成量都由真实状态驱动。
- 原文件、提取物、分块、摘要、Embedding 和索引分别有版本；失败不能返回空集合伪装成功。
- Markdown/JSON/附件清单是可移植协议表现层；领域 ID、来源和关系是协议真值。

### 5.3 Provider、成本与可观测性

- Provider Adapter 输出内部 IR 和类型化错误；不能把 OpenAI 兼容字段当所有 Provider 的完整能力。
- Model Registry 同时记录来源、验证状态、能力、价格版本和最后验证时间；价格为 `null` 与价格为 `0` 必须分开。
- Invocation 形成本地层级：`Task Run → Provider Attempt → Generation/Tool Step → Validation/Score`。
- Trace 默认本地且内容最小化；未来接入远程可观测系统必须另行确认发送字段。
- 预算按完整成功任务计算，包含失败、重试、Fallback、缓存和验证成本。

### 5.4 Memory 与 Eval

- Memory 必须支持增、查、改、删、暂停、批量删除、历史与作用域；自动发现只生成候选。
- 每条记忆保存来源、适用 Project/用户范围、版本、状态和最近确认时间；冲突不静默覆盖。
- Eval 使用版本化 Fixture、期望结构、确定性断言和人工评分；Provider/Model/Harness 更新前跑同一回归集。
- 红队配置、外部 Prompt 包和脚本按不可信代码隔离运行，密钥使用最小权限。

### 5.5 Agent

- Agent Run 是可持久化状态机，拥有 Checkpoint、序号、暂停原因、批准记录、终态和幂等键。
- 风险等级包含 `UNKNOWN`；UNKNOWN 默认要求确认或拒绝，不能按低风险放行。
- 恢复/重放期间，外发、写库、消息、购买、删除等 Side Effect 必须通过幂等键和执行回执避免重复。
- 高风险动作同时需要：风险门、确定性权限、参数 Schema、用户确认、隔离执行、结果回读和可用回滚。

### 5.6 许可证与供应链

- 研究链接不是依赖批准。新增库前记录来源、精确版本、许可证、传递依赖、维护状态、数据外发和替代方案。
- AGPL、社区许可证、修改版许可证或企业目录代码默认禁止复制进仓库；如未来确需复用，先完成法律/分发影响评审。
- 文档经验可以转化为独立领域合同和测试，但禁止近似搬运受限源码、资源、品牌或界面资产。

## 6. 对 P1–P11 的调整

| 阶段 | 新增或强化项 |
|---|---|
| P1 | 结构化终态、Invocation 层级预留、Provider 原始事件与内部事件隔离、许可证清单门禁 |
| P2 | 即时捕获 + 核对保存、版本化 Registry/价格、`null ≠ 0`、内部 IR、调用树和安全字段 |
| P3 | 类型化流事件、消息树、草稿恢复、部分输出、重连、分支与中途 Fallback 限制 |
| P4 | 可恢复文档队列、Memory CRUD/历史/范围、Fixture/Dataset/Eval、知识协议可回导 |
| P5 | 真实设备性能基线、后台/内存、电量、升级迁移、回收站和恢复 |
| P6 | 宽屏工作台复用同一领域协议，先导入导出回读，再讨论实时同步 |
| P7 | 同步不能替代备份；加密快照、冲突方向、迁移与回读继续采用南枫专属合同 |
| P8 | Durable Run、Checkpoint、HITL、风险 UNKNOWN 失败关闭、幂等 Side Effect、沙箱 |
| P9 | 目标应用入口返回执行回执和幂等结果，避免 Agent 重放产生重复写入 |
| P10 | LiteLLM/Langfuse 只作为 Hub 的能力参照；Hub 仍可选、可降级、不接触业务明文 |
| P11 | 依赖/许可证/价格/模型/安全公告定期复核，统一回归 Fixture 和真实链路证据 |

## 7. 本次研究未改变的决定

- Android 原生 Kotlin + Jetpack Compose 首发。
- V1 本地单用户、Provider 直连；OpenRouter 为首个真实 Provider，Claude 为第一测试模型族。
- P1 仍只做最小工程、领域合同、Mock Provider 和内存 Repository。
- 账号、同步、Desktop、Agent 与 AI Hub 不提前进入 P1/P2。
- 本地数据库仍是真值；未来云端只保存版本化 AES-GCM 加密快照。
- 真实 Key、真实内容和第三方调用仍需单独明确授权。
