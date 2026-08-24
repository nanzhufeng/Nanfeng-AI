# 南枫 AI Final Codex Package v3.0

> 当前总控入口：[南枫 AI 总控开发蓝图](MASTER_DEVELOPMENT_BLUEPRINT.md)  
> 架构重整交接：[南枫AI完整开发档案](南枫AI完整开发档案.md)
> 当前 Android 会话界面规则：[Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)
> GitHub 经验研究：[南枫 AI GitHub 成熟项目参考研究](GITHUB_MATURE_PROJECT_REFERENCE_RESEARCH.md)  
> 启动图标交付：[南枫 AI Android 启动图标交付](LAUNCHER_ICON_DELIVERY.md)  
> 本地数据合同：[南枫 AI P2-A 本地数据与附件合同](P2A_LOCAL_DATA_AND_ATTACHMENT_CONTRACT.md)  
> 当前状态：项目现行版本为 `66 / 0.3.0-p10j`、Room Schema 45；当前增量、设备覆盖与未闭环风险以 [CURRENT_HANDOFF.md](CURRENT_HANDOFF.md) 为准。旧阶段状态仅作历史索引，不能覆盖当前源码与合同。

项目名称：南枫 AI

定位：
个人多模型 AI 工作台。

核心规则：
1. 每个南枫软件拥有自己的 AI Provider Framework。
2. 南枫 AI Hub 是可选增强层，不是基础依赖。
3. 数据默认本地保存，可导出。
4. 不绑定单一模型厂商。
5. 所有模型调用、Token、费用可追踪。

支持方向：
ChatGPT、Claude、DeepSeek、Qwen、Kimi 等多模型统一入口。
