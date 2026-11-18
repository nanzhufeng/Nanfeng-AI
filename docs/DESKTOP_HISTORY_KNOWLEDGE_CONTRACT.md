# Desktop 历史资料库与隐藏 interests 当前合同

<!-- contract-governance:start -->
> **合同口径：现行领域合同。** 领域入口：[ Mac历史资料与隐藏字段 ](DESKTOP_HISTORY_KNOWLEDGE_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** Desktop 历史资料库与隐藏 interests 当前合同
<!-- contract-governance:end -->

## 结论

Desktop “历史资料库”是一个低频、可恢复、自动提取并保留来源的本地 owner。开启历史资料库授权后，高置信输出先持久化到资料候选，再通过既有domain mutation入Knowledge并更新独立自动Memory；手动编辑、暂停、删除的记忆保留。Android 当前页面已隐藏但 domain 仍保留的 `interests`（关注方向）必须跨导入、持久化、备份和账号同步保真；Desktop 页面不得重新显示该字段。

## interests 保真

- SQLite 真值为 `desktop_portable_personalization_v1`，与设备专属 `desktop_app_settings` 分离。
- 普通聊天仅在“启用记忆”开启时把非空 `interests` 作为“关注方向”加入请求；回答上下文审计只记录类型和标题，不复制字段正文。
- P6-K profile `publicBio` 仅在本地 `interests` 为空时一次性补入，不覆盖现有值。
- 本机备份／恢复保留该表；Desktop当前已选对话的direct封包可附属携带既有`safe_settings/profile-interests`兼容记录；它不是恢复码加密或独立设置同步，Android恢复对话时忽略该邻接记录。原币种／正文／隐藏字段的本机和可移植保真不变。
- UI 继续只显示昵称、职业和自定义指令，不新增 `interests` 输入框。

## 自动整理来源

- 应用启动、每5分钟的进程内唤醒及窗口重新聚焦时检查到期任务；并发唤醒串行合并，SQLite仍持有资格、检查点和费用窗口，退出应用后不运行。
- 全局最多每 12 小时 dispatch 一次；已处理的最近96条不会阻挡更旧未处理来源；开关关闭时调度暂停并取消本进程活动请求。
- 只选择一个会话的 `currentLeafId` 当前分支；只接受 `delivery=COMPLETE` 的 USER／Assistant 文本。
- 带 `attemptId` 的 Assistant 必须对应普通聊天 Attempt `COMPLETED`。
- 附件、工具内容、兄弟分支、FAILED、CANCELLED、UNKNOWN、PARTIAL 和不完整回复全部排除。
- 至少 4 条消息、500 字符；请求文本最多 6,000 字符。原始整理正文不写入 checkpoint 或候选来源表。

## dispatch、模型与账本

- 网络请求前必须提交 reservation 与 checkpoint；同一 workspace／conversation／source hash 唯一。
- 固定路由顺序为 `DEEPSEEK_V4_FLASH`、`GLM_5_3_FLASH`、`QWEN_3_6_FLASH`，只使用已启用且有 credential 的现有模型服务 owner；localhost 验收使用显式 mock endpoint。
- 输出预算上限 768 Token；关闭思考，响应必须是严格 JSON，`confidence >= 0.86` 才能形成候选。
- 记录 requested／actual provider model、Token、费用事实与 cost source，并追加现有 Usage Ledger；未报告费用保持未知，不写 0。

## 生命周期

`RESERVED → RUNNING → PENDING_REVIEW → ACCEPTING → ACCEPTED` 是正常路径。PENDING_REVIEW同时是自动落盘的可恢复检查点；启用开关时自动完成入库，无需逐条点击。既有手动审阅入口保留。

- 无长期价值进入 `NOT_ELIGIBLE`。
- 确定失败进入 `FAILED`；进程中断或提交结果不确定进入 `UNKNOWN`。
- `UNKNOWN`只允许用户显式重试；已明确终止的OUTPUT_LIMIT仅retry_count=0可恢复一次；旧RESPONSE_FORMAT在可选type兼容策略迁移时允许retry_count<=1的记录再恢复一次。新策略解析失败记RESPONSE_FORMAT_V2，不进入旧策略恢复；始终遵守12小时窗口、来源hash不变及网络前预留；其他FAILED只允许用户显式重试；重试复用原 checkpoint，但创建新 Attempt，且必须重新核对来源 hash 未变化。
- 用户可通过既有审阅入口处理候选；已拒绝/删除候选不会自动采纳。自动Memory按来源对话稳定ID更新，domain intent journal与expected revision保护人工编辑；导入后来源修订不能证明为自动时保守保留。
- 接受动作调用现有Knowledge domain mutation owner；自动摘要调用Memory mutation owner，不清理或替换其他摘要。删除已接受的候选记录不得连带删除 `knowledge_id` 指向的 Knowledge。

## UI 与数据恢复

- 设置 → 个性化仅保留一个“历史资料库”开关和当前 Android 说明。
- 工作区 → 项目与知识 → 管理知识库中，“资料候选”独立位于现有本地知识之前；未采纳候选不得混入 Knowledge 列表。
- 候选显示状态、来源会话／消息数、Provider／实际模型、Token／费用、checkpoint 和安全失败原因。
- 本机恢复把 `RESERVED`／`RUNNING` 候选转为 `UNKNOWN/BACKUP_RESTORED`，不自动继续；删除和接受回执随 SQLite 备份保真。
- schema 30 升 31 时，旧的未确认 `history_library_enabled=true` 安全迁移为关闭；用户需重新明确开启。

## 验收边界

localhost、唯一 Bundle ID 和独立 `/tmp` 根只证明本地 Tauri／SQLite／UI／mock transport 闭环；不证明真实 Provider、Key、账单、正式用户数据或系统通知。不得运行任何 `connected*AndroidTest`，不得触碰 OPPO。

自动提取的本地落盘失败先恢复已有候选，再选下一来源；不会因补写Memory重新调用Provider。关闭记忆开关时不新增或更新自动Memory；历史整理暂停时不自动采纳已有候选。普通聊天的启用记忆/相关性检索门继续生效。格式不完整、敏感信息或置信度超出0..1的结果拒绝写入。模型顺序保持DeepSeek Flash→GLM Flash→Qwen Flash；2026-11-02价格核验与本轮验证见项目状态，模型维护目录不因本次改变。

2026-11-02格式兼容：严格JSON可含一个可选`type`短字符串（最多64字符），该分类元数据被忽略，不进入摘要、不执行其中内容；其他未知字段、类型错误及敏感内容仍拒绝。实际AUTO_DUE/MANUAL_RETRY执行前后投影到最多128条无正文回执（history-curation-receipts-v1.json），只含计数、状态、用量和内容指纹，不是第二业务状态、不控制调度。

旧格式失败恢复：候选选择先处理RESPONSE_FORMAT且retry_count<=1的已知失败来源，retry_count=1的旧策略遗留优先；再沿原最近更新时间顺序处理其余来源，避免新聊天长期挤后旧失败。来源hash须仍匹配；恢复次数、12小时窗口及UNKNOWN不重试规则不变，新格式失败仍使用RESPONSE_FORMAT_V2阻止循环。
