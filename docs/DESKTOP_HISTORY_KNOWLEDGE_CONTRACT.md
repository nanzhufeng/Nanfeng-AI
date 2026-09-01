# Desktop 历史资料库与隐藏 interests 当前合同

## 结论

Desktop “历史资料库”是一个低频、可恢复、先审阅后入库的本地 owner。模型输出只能进入“资料候选”，未经用户确认不得进入长期 Memory 或 Knowledge。Android 当前页面已隐藏但 domain 仍保留的 `interests`（关注方向）必须跨导入、持久化、备份和账号同步保真；Desktop 页面不得重新显示该字段。

## interests 保真

- SQLite 真值为 `desktop_portable_personalization_v1`，与设备专属 `desktop_app_settings` 分离。
- 普通聊天仅在“启用记忆”开启时把非空 `interests` 作为“关注方向”加入请求；回答上下文审计只记录类型和标题，不复制字段正文。
- P6-K profile `publicBio` 仅在本地 `interests` 为空时一次性补入，不覆盖现有值。
- 本机备份／恢复保留该表；Google 账号同步把它作为 `safe_settings/profile-interests` 加密记录随用户明确选择的对话同步。
- UI 继续只显示昵称、职业和自定义指令，不新增 `interests` 输入框。

## 自动整理来源

- 全局最多每 12 小时 dispatch 一次；开关关闭时调度暂停并取消本进程活动请求。
- 只选择一个会话的 `currentLeafId` 当前分支；只接受 `delivery=COMPLETE` 的 USER／Assistant 文本。
- 带 `attemptId` 的 Assistant 必须对应普通聊天 Attempt `COMPLETED`。
- 附件、工具内容、兄弟分支、FAILED、CANCELLED、UNKNOWN、PARTIAL 和不完整回复全部排除。
- 至少 4 条消息、500 字符；请求文本最多 6,000 字符。原始整理正文不写入 checkpoint 或候选来源表。

## dispatch、模型与账本

- 网络请求前必须提交 reservation 与 checkpoint；同一 workspace／conversation／source hash 唯一。
- 固定路由顺序为 `DEEPSEEK_V4_FLASH`、`GLM_5_3_FLASH`、`QWEN_3_6_FLASH`，只使用已启用且有 credential 的现有模型服务 owner；localhost 验收使用显式 mock endpoint。
- 输出预算上限 768 Token；响应必须是严格 JSON，`confidence >= 0.86` 才能形成候选。
- 记录 requested／actual provider model、Token、费用事实与 cost source，并追加现有 Usage Ledger；未报告费用保持未知，不写 0。

## 生命周期

`RESERVED → RUNNING → PENDING_REVIEW → ACCEPTING → ACCEPTED` 是正常路径。

- 无长期价值进入 `NOT_ELIGIBLE`。
- 确定失败进入 `FAILED`；进程中断或提交结果不确定进入 `UNKNOWN`。
- `FAILED`／`UNKNOWN` 只允许用户显式重试；重试复用原 checkpoint，但创建新 Attempt，且必须重新核对来源 hash 未变化。
- 用户可在待核对状态编辑标题、正文、标签并确认，或 `REJECTED`；可删除候选记录为 `DELETED`。
- 接受动作只调用现有 Knowledge domain mutation owner。删除已接受的候选记录不得连带删除 `knowledge_id` 指向的 Knowledge。

## UI 与数据恢复

- 设置 → 个性化仅保留一个“历史资料库”开关和当前 Android 说明。
- 工作区 → 项目与知识 → 管理知识库中，“资料候选”独立位于现有本地知识之前；未采纳候选不得混入 Knowledge 列表。
- 候选显示状态、来源会话／消息数、Provider／实际模型、Token／费用、checkpoint 和安全失败原因。
- 本机恢复把 `RESERVED`／`RUNNING` 候选转为 `UNKNOWN/BACKUP_RESTORED`，不自动继续；删除和接受回执随 SQLite 备份保真。
- schema 30 升 31 时，旧的未确认 `history_library_enabled=true` 安全迁移为关闭；用户需重新明确开启。

## 验收边界

localhost、唯一 Bundle ID 和独立 `/tmp` 根只证明本地 Tauri／SQLite／UI／mock transport 闭环；不证明真实 Provider、Key、账单、正式用户数据或系统通知。不得运行任何 `connected*AndroidTest`，不得触碰 OPPO。
