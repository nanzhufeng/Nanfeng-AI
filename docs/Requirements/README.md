# 南枫 AI 需求来源索引

本目录保存用户于 2026-08-12 补充确认的南枫 AI 开发需求原件。原文件按原名、原格式归档，未改写正文。

## 来源与使用方式

| 文件 | 需求角色 | 使用边界 |
|---|---|---|
| `南枫_AI_Provider_Framework_AI_Hub_通用架构原则_v1_0_20260812.md` | Provider、Harness、Telemetry、AI Hub 的通用架构基线 | 约束南枫 AI 及其他南枫系应用；业务需求冲突时，以明确产品需求为准 |
| `南枫 AI 开发路线与产品架构思路.md` | 当前产品路线与阶段规划 | 将首发路线更新为移动端优先、桌面端后置；其中技术栈属于建议，需在工程初始化前确认 |
| `Claude 级对话产品：可落地开发方案.docx` | 对话体验、记忆、缓存、路由、数据模型与工程实现参考 | “Claude 级”表示体验质量目标，不把产品变成 Claude 专用客户端；具体模型、价格、API 和服务端技术必须实时核验 |

## 冲突解释

1. `Core + Client` 中的 Core 是共享领域合同与能力内核，不等于必须联网的中心服务器。
2. AI Hub 是可选增强层；Hub 离线时，应用仍通过本地 Provider Framework 调用 Provider。
3. Word 方案中的 BFF、Postgres、Redis、S3/MinIO 是云端增强参考，不直接成为移动端 V1 的强制依赖。
4. “Claude 级”只约束流式体验、消息分支、附件、记忆可控性和可靠性，不覆盖多 Provider 产品定位。
5. 模型目录、价格、上下文、缓存与 API 能力属于可变配置，必须由版本化 Registry 管理，禁止写死在业务代码中。

## 2026-08-12 官方事实核验

已通过 Anthropic 官方 Claude Platform 文档核对：Claude Fable 5、Opus 5、Sonnet 5、Haiku 4.5、Models API、当前上下文与价格信息存在。实现时仍须再次读取官方目录，因为模型生命周期、价格和能力会变化。

- 模型目录：<https://platform.claude.com/docs/en/about-claude/models/overview>
- 价格：<https://platform.claude.com/docs/en/about-claude/pricing>
- Models API：<https://platform.claude.com/docs/en/api/models>

