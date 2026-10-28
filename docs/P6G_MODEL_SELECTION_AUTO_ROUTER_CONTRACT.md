# P6-G Model Selection & Auto Router Contract

<!-- contract-governance:start -->
> **合同口径：现行领域合同。** 领域入口：[ 模型选择与Auto ](P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** 普通会话手动/Auto、下次默认和冻结身份；旧P6-G实验不裁决生产候选
<!-- contract-governance:end -->

状态：本页“优先级与目录”定义普通聊天当前路由。P6GModelRouter及下方P6-G早期规划／UI退出记录是独立实验模块和历史证据，不等同当前普通发送链；动态验证读取CURRENT_HANDOFF最新条目。模型维护统一入口为[总架构](model-service/MODEL_SERVICE_ARCHITECTURE.md)和[当前矩阵](model-service/MODEL_SERVICE_MATRIX.md)。

> **当前 Android UI 路由（2026-09-02）：** 本文仍是 Auto、手动 override、持久化与实际模型归因的领域合同；Android Composer 的显示名、固定宽度、模型面位置、激活视觉、遮罩点按和滑动返回，统一以 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 为准。本文的早期“Dialog/菜单”视觉措辞不构成第二套 UI 规则。

## 优先级与目录

- 已有会话的冻结／手动选择优先；新建会话才读取同数据区“下次默认”，无记录时为Auto。打开已有会话、全局默认变化或失败不重选。旧P6-G实验global default与普通发送初始化分开，不能绕过当前会话身份。
- Android／Mac 每个对话的手动模型ID只有该对话内用户明确选择或清除手动选择才可更新。重开该对话、超时、服务不可用、停止生成、进程重启、全局默认变化或其他对话选模均不得改写；不可用时保留选择并失败关闭，禁止静默改成其他模型。重试执行身份另由原Attempt冻结记录裁决。
- 新建普通／工作对话沿用**同一数据区上一次最后明确选择**的模型，重启后仍保留；无记忆时才默认Auto。最后明确选Auto也作为有效选择保存；Auto本次实际路由、历史消息的实际模型、打开其他对话或服务健康变化都不更新这个默认。TEMP继续隔离。
- 新会话创建时保存自己的模型快照，含明确Auto；恢复未发送草稿不覆盖已有选择。后续其他会话选模不改写已创建会话。保留的模型退役／禁用／缺Key时保留原身份并在发送前明确失败，不改Auto或替代模型。
- Android复用P6GModelSelectionOwner／私有preferences，选模同时提交会话选择与下次默认，CreateConversationUseCase初始化并回读创建结果；默认保存失败时保留可恢复草稿并报告未完成。Mac由Rust区域SQLite保存下次默认，与已有会话选模同一事务；尚无会话的Composer选模经受限IPC直接保存，首条发送把显示的精确选择（含Auto）与会话一同保存。Mac工作区schema44前向新增p6g_composer_default，旧43记录不改写；该设备偏好不外发，同区域本地恢复保留设备当前默认。

- 当前手动菜单与部署身份唯一读取 `ComposerModelRoutingCatalog`、Desktop选择器及[生成维护矩阵](model-service/MODEL_SERVICE_MATRIX.md)；本合同不再维护第二份完整名单。深度档对应顺序为DeepSeek Pro → Muse Wonder → Gemini 4 Argon → 智谱GLM，Muse与Argon均经OpenRouter；RelayCore六项独立二级菜单。Muse与Argon只新增手动入口，既有Auto计划不变；Qwen3.6 Flash仍为Auto专用。退役选择保留精确历史识别，不能静默转Auto或改历史归因。
- 日常档 GPT-6.1 Terra 紧接在 GPT-6 Luna 之前；独立预设 `GPT_6_1_TERRA`、选择 ID `logical:daily:gpt-6.1-terra`，OpenRouter API ID `openai/gpt-6.1-terra`。复用现有聊天、附件、联网与计量 owner；不改变 Luna 默认、Auto／后台计划或旧 `logical:daily:gpt-terra` → Luna 的历史升级映射。能力档案与价格核验／未验范围见[接入记录](verification/2026-10-27-gpt-terra/REPORT.md)。
- 日常档 Mercury 3 紧接 GPT-6 Luna，固定 `MERCURY_3`／`inception/mercury-3`，复用 OpenRouter 配置、Adapter、联网与计量；仅增加手动选择，不改变 Auto／后台默认或历史模型身份。原生输入能力依当前目录，文本文件／可读 PDF 等本机提取沿用材料 owner，不能把不支持的媒体冒充已读取。
- OpenRouter冷启动使用已验证seed的固定API身份；缓存profile只能贡献同一ID的健康／时效信息，不得以错误或旧ID覆盖所选模型。已有官方直连的显式别名刷新语义保持原owner，不将此规则推广为猜测近似型号。
- `Grok 4.1 Fast`（`x-ai/grok-4.1-fast`）在既有目录核验阶段被标为不可发送；当前产品按已维护目录保持退役：保留其逻辑 ID 仅用于旧会话识别与归因，不出现在选择器、Auto 候选或重试发送中。旧选择必须在读取 Key、外发正文前以“模型已不在服务商目录”失败，绝不静默替换。
- `Grok 4.5`（`x-ai/grok-4.5`）与 `Grok 4.6 High`（`x-ai/grok-4.6`）都已从产品模型列表与 Auto 候选移除；持久化旧选择只能失败关闭，不能回退、近似替换或继续外发。历史消息仍保留当时实际模型归因。
- 普通聊天 Auto 使用 `protocol/fixtures/auto-routing-rc-domestic-20261010.json` 的版本化顺序，独立于模型选择列表顺序：日常文本优先 RC Sol、RC Gemini Flash、国内 DeepSeek Flash；显式深度任务优先 RC Astra、RC Opus、RC Sol，再选国内强模型。附件任务优先国内模型；RC 原始多媒体路径未实现，不进入附件计划。OpenRouter 在全部 RC／国内候选之后，仅在前者未启用、缺少凭据、健康不可用或不能处理对应材料时才可在发送前选中。失败后不自动换模型重发。
- Android继续检查实际解析能力/健康；Mac检查启用状态、对应凭据和已实现材料投影。RC文字Sol/Astra自身联网；其他RC文字与全部RC附件需要OpenRouter Luna配置，具体接收方与接口只遵从[C12](C12_MODEL_NETWORK_PARITY_CONTRACT.md)。RC原始媒体不因列表或Luna存在而新增能力；文本搜索接口不能借离线视觉能力发送媒体。Auto接收方发送前冻结，配置变化造成预测不一致须重新披露；失败不换模型重发，重试使用原Attempt身份。
- 显式选择 Auto 的会话保持 Auto，不继承其他会话后续手动选择。关闭任务分类时 Android 使用日常 Auto 顺序；手动模型不受候选计划、任务类型、能力筛选影响，不能使用时明确失败。
- 当前OpenRouter Claude Opus 5.5默认reasoning.effort=medium；Claude Sonnet 5.5与GPT-6.1 Sol默认high，联网不能清除该字段。Haiku／Luna／其他型号不按“深度”标签强加High。旧型号的历史参数不构成当前菜单或默认。Domain稳定层级与catalog真实ID／能力／价格／上下文分开；无法精确映射的部署不可发送。

## P6-G实验编排规划（不替代普通发送当前路由）

1. 合法 exact historical cache 命中；2. 敏感、无同意或离线适合时本地；3. capability；4. sensitivity/egress/cost/budget；5. complexity/risk tier；6. Claude-preferred 合格候选；7. 明确 fallback；8. 必要升级而非无限重试。
- 初始在线调用目标带宽是观测目标而非硬配额：FAST/BALANCED/DEEP/APEX_REVIEW 约 `8/70/20/2`（允许 5–10/65–75/15–25/<=5%），不为凑比例扭曲任务。
- 升级触发器必须可审计：上下文规模、多约束、代码/架构跨度、高风险投资判断、工具步数、解析失败、同任务连续失败、用户最终审查；不按“自信度”单独升级。重要任务优先 BALANCED 产出加 DEEP 独立复核，APEX 只在明确门槛触发。
- 投资、法律、医疗等高风险内容不因模型档位成为业务真值，必须保留不确定性、来源和人工复核。unknown cost 不得当作 0；按本次/日/月预算失败关闭或要求确认。

<a id="逐条真实记录与-p6-f-接口"></a>

## P6-G实验记录与 P6-F 接口

- 每次 Invocation 记录 provider/model ID+display snapshot、catalog/policy version、route reason、tier、候选拒绝/fallback reason、token/cost/elapsed、cache/local/online 来源；不含正文或 Key。
- P6-F 逐条显示实际模型；Auto 显示“自动 · 实际模型”，未知不猜。branch/retry/change-model 均保持 node 独立 snapshot。
- 未来验收覆盖 catalog 变动、手动 override、fallback、unknown cost、预算、Auto、cache/local/online、migration/readback/import/export/branch/retry；实际 Provider/Key/HTTP 另立授权验收。

## P6-G实验模块与历史退出门（2026-08-14）

- 已实现但**尚非 P6-G 完成**：`app/.../P6GModelRouter.kt` 与 `desktop/src/p6g-model-router.mjs` 是无 I/O 的本地 owner；只消费调用方传入的动态 candidate catalog。双方 fake contracts 已覆盖 exact cache/local safety 前置、手动 override 不回落 Auto、Anthropic 优先与 unknown cost 失败关闭。它们不会生成 Invocation、读取 credential、保存 catalog 或发起 HTTP。
- Android 已有 revision 化 app-private `P6GModelSelectionOwner/Storage`；损坏 catalog 收敛为空。Desktop 已有 SQLite migration 12、typed Rust local owner、catalog/global/conversation override/route metadata commands 与最小 ACL，且前端 localStorage store 已删除。Desktop owner 的定向自动验证已覆盖 migration reopen、revision conflict、corruption fail-closed、manual、Auto、unknown cost 与安全 metadata；不含 Provider 或 Invocation。
- **统一壳层前置（FB-P6-023，2026-08-14）**：用户已否定当前 Android bottom-nav/多根级菜单壳层，并明确当前 Desktop 页面不是最终统一方案。因此暂停所有旧壳层上的 P6-G UI 追加、截图、`.app`/模拟器视觉验收和完成声明。先交付同语义统一 chat-first shell：Android 手机无 bottom navigation，顶部菜单/聊天-工作切换/临时入口，单一对话主体与固定 Composer；drawer 仅含搜索、置顶、最近与底部固定设置；各低频功能从设置进入。Desktop 用常驻或可折叠侧栏表达同一顺序和语义，不能另建 Desktop IA。
- **消息投影前置（FB-P6-024，2026-08-14）**：新壳层的 transcript 必须同步采用无气泡 Assistant/南枫 AI 主内容列、右对齐暖橙浅色且有限宽 USER 气泡、居中日期/时间分隔，以及只附属到正确消息的思考时长、消息时间、真实 model metadata、来源和动作；未知信息不猜，也不得用当前模型倒填历史。仅在离开底部时显示 Composer 上方圆形“到最新消息”按钮，点击平滑到底并恢复输入，到底自动隐藏；固定 Composer 不得遮挡内容。Desktop/Android 共享该消息语义，只调整内容列宽/密度；图标优先现有 Material/Lucide 等价资产。它是统一壳层验收前置，不是旧壳层上可补做的 P6-G UI。
- **意图优先前置（FB-P6-025，2026-08-14）**：P6-G 的 catalog、policy 与 owner 仍是领域真值，但不是根级导航的理由。Auto 必须是 normal conversation 的默认、可直接发送状态；Composer 选择器只是该对象的可选 manual override，选择 Auto 即回到 policy，TEMP 不进入 P6-G。Settings 才管理持久 catalog/global policy；route reason/rejected candidates 只在所属消息或明确设置上下文显示。四张参考图的空态、对话态、drawer、角色投影与轻量节奏须同时通过 Intent-first、Progressive Disclosure 与 Object-bound state 审查，详见 `CHAT_FIRST_INTENT_ORGANIZATION_CONTRACT.md`。
- 统一壳层完成后才恢复 P6-G UI 退出门：普通**非 TEMP**会话 Composer 显示当前 `Auto` 或实际手动选择；设置管理 owner-local catalog/preset/global default/policy；短列表为同一纯白选择面并可外点取消；安全 route reason/rejected candidates 只展示真实 owner metadata。TEMP 继续只保留 P6-E local marker，绝不进入 P6-G provider selection。
- 之后的双端真实本地验收仍须从新壳层 UI 改 Auto/全局默认/会话 override，并完整退出或 force-stop 后读回；Auto 实际候选以 snapshot 显示，unknown cost 明确要求确认且不写作零。Android 签名/install 与 Desktop `.app` 另立证据；真实 catalog、Key、HTTP、Provider result、OPPO、图标、账号同步和发布仍为范围外门。

<a id="fb-p6-023024-实施检查点2026-08-14未完成"></a>

## 历史FB-P6-023/024 实施检查点（2026-08-14，未完成）

- Desktop 已将 transcript 纠正为 Assistant 开放主内容列、USER 单独右对齐暖橙 max-width bubble；日期、消息事实 metadata 和动作仍归属消息本身。回底按钮只在离开最新消息时显示，点击平滑回底并保留 Composer 焦点。
- Android 已移除根级 NavigationRail/NavigationBar，启动进入 Conversation workspace；drawer 底部新增真实“设置”入口，工作模式才显示 Project/Knowledge/Memory 等二级集。普通会话 Composer 和“设置→模型”读取同一 `P6GModelSelectionOwner` 的 catalog/global default/conversation override；TEMP 不显示 P6-G selector。
- 本检查点仅记录代码与定向自动验证，不构成双端真 UI、restart/readback、签名/install/base hash 或 P6-G 退出。未读 Key、未发 HTTP、未执行 Provider。
