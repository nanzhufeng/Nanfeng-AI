# 南枫 AI 文档入口

本页只负责路由，不保存版本、Schema、测试数、APK、设备状态或阶段完成声明。

## 默认读取顺序

1. 项目根 `AGENTS.md` 与当前 checkout。
2. [当前交接](CURRENT_HANDOFF.md) 顶部；历史交接只在追溯时从其归档链接按关键词读取。
3. 与任务直接相关的一份现行合同：
   - [Android 会话与搜索界面](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)
   - [Android 设置界面](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)
   - [Android 运行时上下文](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)
   - 数据、Provider、模型、OCR、ZIP、费用等专项合同按文件名精准检索，不批量读取。
4. 长期取舍读取 [决策日志](decision-log.md)；跨项目方法读取 [可迁移开发经验](可迁移开发经验.md)。

## 文档职责

- 当前行为：唯一现行合同。
- 当前 checkpoint、验证和下一步：`CURRENT_HANDOFF.md` 顶部。
- 旧 checkpoint：`docs/archive/`，不作为启动必读。
- 长期架构或产品取舍：`decision-log.md`。
- 完整项目事实：[南枫 AI 完整开发档案](南枫AI完整开发档案.md)。
- 总体路线：[总控开发蓝图](MASTER_DEVELOPMENT_BLUEPRINT.md)；蓝图中的阶段数字不得覆盖当前代码和合同。

## 稳定产品边界

南枫 AI 是本地优先、可导出、支持多 Provider 的个人 AI 工作台。每个南枫软件拥有自己的 Provider Framework，南枫 AI Hub 只是可选增强层；模型调用、Token、费用和失败状态必须可追踪。
