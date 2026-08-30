# 南枫 AI Final Codex Package v3.0

> 当前总控入口：[南枫 AI 总控开发蓝图](MASTER_DEVELOPMENT_BLUEPRINT.md)  
> 完整开发档案：[南枫AI完整开发档案](南枫AI完整开发档案.md)
> 跨项目复用边界：[可迁移开发经验](可迁移开发经验.md)
> 当前 Android 会话界面规则：[Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)
> 当前 Android 设置界面规则：[Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)
> GitHub 经验研究：[南枫 AI GitHub 成熟项目参考研究](GITHUB_MATURE_PROJECT_REFERENCE_RESEARCH.md)  
> 启动图标交付：[南枫 AI Android 启动图标交付](LAUNCHER_ICON_DELIVERY.md)  
> 本地数据合同：[南枫 AI P2-A 本地数据与附件合同](P2A_LOCAL_DATA_AND_ATTACHMENT_CONTRACT.md)  
> 当前状态：版本、Room Schema、测试、产物和设备覆盖以稳定提交与 [CURRENT_HANDOFF.md](CURRENT_HANDOFF.md) 顶部为准；本次复盘稳定基线 `c1c9ae0` 的 Room Schema 为 56，明确排除同仓库正在进行的后续任务及其未提交 WIP。旧阶段状态仅作历史索引，不能覆盖稳定代码与合同。

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
