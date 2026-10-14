# Desktop Compare 真实执行合同

<!-- contract-governance:start -->
> **合同口径：领域／模块限定合同。** 领域入口：[ Desktop历史Compare执行兼容 ](DESKTOP_COMPARE_EXECUTION_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** Desktop Compare 真实执行合同
<!-- contract-governance:end -->

## 当前结论

历史Desktop Compare专项已实现Tauri／HTTP／SQLite执行链及分支数据。当前chat-shell／app不再提供新“对比”Composer入口；既有Compare身份、分支状态与停止／重试兼容仍由原owner保留。不能根据下面旧入口说明重新添加按钮；当前可见表面以Desktop会话合同为准。

## 历史专项目标与Provider（现有分支兼容语义保持）

- Provider 固定为已存在的 `OPENROUTER` 设置、固定 OpenAI-compatible endpoint 与同一 Security.framework scoped credential owner。
- ChatGPT 分支固定为 `GPT_5_6_TERRA`／`openai/gpt-5.6-terra`。
- Claude 分支固定为 `CLAUDE_SONNET_5`／`anthropic/claude-sonnet-5`。
- OpenRouter 未启用或凭据缺失时失败关闭；不得自动换 Provider、换模型或回退普通 Auto。
- localhost mock 只在 `NANFENG_AI_DESKTOP_ORDINARY_CHAT_ACCEPTANCE=1` 且 endpoint 为 `127.0.0.1`／`localhost` 时可达，不是产品能力。

## 一次提交与两条分支

- 一次点击只原子提交一条 USER 消息、一个 `desktop_compare_executions` envelope、两条 sibling Assistant 消息和两条 `desktop_ordinary_chat_attempts`。
- 两分支共享同一个当次上下文快照：当前对话路径、语气、自定义指令、Memory、Knowledge、网页检索判断和附件投影均复用普通聊天 owner。
- 两分支拥有不同 Attempt、idempotency key、requested／actual model、token、费用、错误与终态；Provider 实报费用优先，未知费用不得写 0。
- 相同文字再次点击必须创建新的 execution／USER／Attempt，不得误报“请勿重复发送”，也不得复用旧响应。

## 状态、停止、失败与恢复

- 分支状态沿用普通聊天：`PENDING/RUNNING/COMPLETED/COMPLETED_ACCOUNTING_PENDING/FAILED/UNKNOWN/CANCELLED`。
- execution 聚合为 `RUNNING/COMPLETED/COMPLETED_WITH_FAILURE/ACCOUNTING_PENDING/FAILED/UNKNOWN/CANCELLED`，但 UI 始终显示每条分支的独立事实。
- 任一分支卡片的“停止”停止同一 execution 的两条活动信号；已生成正文保留，不删除 USER 或 sibling。
- FAILED／UNKNOWN 只允许用户明确点击“重试该分支”；重试创建新 Attempt 并保留 Compare lineage，不自动重发 sibling。
- 进程中断把未完成分支恢复为 `UNKNOWN/PROCESS_INTERRUPTED`，刷新 execution 聚合状态；不自动重发。
- 打包环境下前端用只读 workspace 轮询补足流式投影，普通聊天与 Compare 共用；轮询不能把用户从已切换的会话抢回。

## 数据、安全与生命周期

- schema 30 只新增 Compare lineage 字段和 envelope 表；表中不允许正文、Prompt、Key、附件字节、路径或 Provider 原始 payload。
- 正文仍只存在 workspace message tree；附件仍由既有私有复制、SHA-256、引用计数和最后引用清理 owner 管理。
- 搜索、导出、备份、恢复、分支和旧会话回读继续读取同一 message tree；Compare 不创建第二套正文数据库。
- 默认应用数据根、用户 Key、真实 Provider 和 OPPO 不属于 localhost 验收范围。

## 验收口径

专项状态与证据见 [Desktop Compare 验收矩阵](DESKTOP_COMPARE_EXECUTION_ACCEPTANCE_MATRIX_20260901.md)。代码／测试／Browser／原生壳／真实 Provider 必须分层报告；localhost mock 通过不能写成真实 OpenRouter 已验。
