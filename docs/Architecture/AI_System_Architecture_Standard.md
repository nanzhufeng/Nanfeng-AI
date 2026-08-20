# 南枫 AI System Architecture Standard

原则：

每个软件拥有独立AI Provider Framework。

AI Hub为可选增强层。

软件自身负责：
- Provider接口
- Prompt
- 业务上下文
- 基础AI调用

AI Hub负责增强：
- Gateway
- 模型路由
- 成本统计
- Agent Runtime
