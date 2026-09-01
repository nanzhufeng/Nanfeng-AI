# 南枫 AI Provider Framework / AI Hub 通用架构原则

> 版本：v1.0  
> 日期：2026-08-12  
> 适用范围：南枫知识库、南枫记、南枫智投、南枫八字、南枫电脑管家及后续所有包含 AI 能力的南枫系软件  
> 核心定位：**每个应用拥有自己的 AI Provider Framework；南枫 AI Hub 是可选增强层，而不是基础依赖。**

---

# 0. 核心结论

南枫系软件后续不得再把“接入某个模型 API”理解为：

```text
base_url
api_key
model
```

三个参数替换即可完成模型接入。

真正影响 AI Agent / Coding Agent / 工具型 AI 能力的，不只是模型本身，而是：

```text
Agent 实际能力
≈
模型能力
× Harness 适配质量
× 上下文工程
× Tool Calling 适配
× 缓存策略
× 任务验证机制
```

因此，南枫系 AI 架构必须明确拆分为：

```text
Application
    ↓
Agent / AI Harness
    ↓
Provider Adapter
    ↓
Model / Provider
```

其中：

- **Application**：业务逻辑。
- **Agent / AI Harness**：模型如何思考、调用工具、组织上下文、压缩历史、重试和验证。
- **Provider Adapter**：处理 OpenAI / Anthropic / DeepSeek / Kimi / Qwen 等协议差异。
- **Model / Provider**：实际大模型服务。
- **南枫 AI Hub**：作为跨应用的统一增强层，负责密钥、路由、模型目录、价格、观测、统计和策略管理，但不得成为单点基础依赖。

---

# 1. 第一原则：Provider ≠ Model ≠ Harness

必须把三个概念严格分开。

## 1.1 Model

Model 只是模型本身，例如：

```text
GPT-5.x
Claude
DeepSeek
Kimi
Qwen
本地模型
```

它决定：

- 基础推理能力
- 编码能力
- 多模态能力
- 长上下文能力
- 输出质量上限
- 推理速度
- 原生工具能力

但它不等于最终用户实际体验。

---

## 1.2 Provider

Provider 是模型服务提供方或 API 服务入口。

例如：

```text
OpenAI
Anthropic
DeepSeek 官方 API
Moonshot
阿里云百炼
OpenRouter
本地 Ollama / vLLM
其他兼容代理
```

同一个模型通过不同 Provider 调用时，可能存在：

- API 协议不同
- 参数支持不同
- Tool Calling 行为不同
- Reasoning 字段不同
- 缓存机制不同
- 上下文限制不同
- 价格不同
- 稳定性不同
- 限流策略不同

因此不得只通过 `model_name` 判断模型能力。

---

## 1.3 Harness

Harness 是应用真正驱动模型完成任务的运行层。

它至少包括：

```text
System Prompt
Tool Definitions
Tool Calling Loop
Context Construction
Memory Injection
Context Compaction
Retry Strategy
Validation
Fallback
Caching Strategy
Output Parsing
Error Recovery
```

同一个模型放进不同 Harness 中，可能产生完全不同的：

- 任务完成率
- Token 消耗
- 缓存命中率
- 工具调用稳定性
- 幻觉率
- 返工次数
- 最终成本

因此：

> **不能用“模型强不强”替代“模型 + Harness 是否适配当前任务”的判断。**

---

# 2. 总体架构

推荐统一架构：

```text
┌───────────────────────────────┐
│         Application           │
│  南枫知识库 / 南枫记 / 智投等   │
└──────────────┬────────────────┘
               │
               ▼
┌───────────────────────────────┐
│       AI Service Layer        │
│  业务任务定义 / 权限 / 验证     │
└──────────────┬────────────────┘
               │
               ▼
┌───────────────────────────────┐
│         AI Harness            │
│                               │
│ Prompt / Context / Memory     │
│ Tools / Agent Loop            │
│ Retry / Validation            │
│ Compaction / Cache Strategy   │
└──────────────┬────────────────┘
               │
               ▼
┌───────────────────────────────┐
│       Provider Adapter        │
│                               │
│ OpenAI Adapter                │
│ Anthropic Adapter             │
│ DeepSeek Adapter              │
│ Kimi Adapter                  │
│ Qwen Adapter                  │
│ Local Adapter                 │
└──────────────┬────────────────┘
               │
               ▼
┌───────────────────────────────┐
│      Provider / Model         │
└───────────────────────────────┘
```

南枫 AI Hub 位于旁路：

```text
                   ┌──────────────────┐
                   │  南枫 AI Hub     │
                   │                  │
                   │ Provider Registry│
                   │ Model Registry   │
                   │ Key Management   │
                   │ Cost / Telemetry │
                   │ Routing Policy   │
                   │ Health Check     │
                   └───────┬──────────┘
                           │ 可选增强
                           │
Application → Local AI Framework → Provider
```

必须保证：

```text
AI Hub 不在线
≠
应用 AI 功能彻底不可用
```

---

# 3. 第二原则：每个应用必须可独立运行

每个南枫系应用必须拥有自己的最小 AI Provider Framework。

最小能力至少包括：

```text
Provider 配置
API Key 配置
Model 选择
请求发送
响应解析
错误处理
基础日志
超时
重试
基础 Tool Calling
```

例如：

```text
南枫知识库
├─ AI Provider Framework
│  ├─ OpenAIAdapter
│  ├─ AnthropicAdapter
│  ├─ DeepSeekAdapter
│  └─ LocalAdapter
└─ 可选连接 AI Hub
```

AI Hub 只增强：

```text
统一密钥管理
统一 Provider 注册
统一模型目录
统一价格
智能路由
统一调用统计
跨应用成本分析
健康检测
集中策略更新
```

但不能替应用完成最基本的 API 调用能力。

---

# 4. 第三原则：不要把所有模型硬塞进“OpenAI Compatible”

“OpenAI Compatible”只能作为兼容入口，不得作为南枫框架的唯一抽象。

错误设计：

```text
class AIClient:
    base_url
    api_key
    model
```

所有 Provider 都通过同一套 JSON 硬发请求。

这种设计短期开发快，但容易造成：

- Tool Calling 字段不兼容
- reasoning / thinking 丢失
- Responses API 能力被降级
- 多模态接口异常
- 流式输出行为不一致
- JSON Schema 行为不一致
- 缓存能力无法使用
- Provider 特有能力无法接入
- 错误码被统一吞掉
- 后续维护困难

正确方向：

```text
BaseProviderAdapter
├─ OpenAIAdapter
├─ AnthropicAdapter
├─ DeepSeekAdapter
├─ KimiAdapter
├─ QwenAdapter
└─ LocalAdapter
```

统一的是上层能力接口：

```text
generate()
stream()
tool_call()
structured_output()
embed()
vision()
reasoning()
```

而不是强行统一底层 HTTP 请求格式。

---

# 5. Provider Capability Matrix

每个 Provider / Model 必须维护能力描述，而不能只记录模型名。

建议结构：

```yaml
provider: deepseek
model: deepseek-v4-flash

capabilities:
  chat: true
  streaming: true
  tool_calling: true
  structured_output: true
  reasoning: true
  vision: false
  embeddings: false

context:
  max_input_tokens: unknown
  max_output_tokens: unknown

cache:
  supported: true
  automatic: true
  cache_hit_usage_field: true

api:
  openai_chat_compatible: true
  anthropic_compatible: true
  responses_api: partial
```

必须允许：

```text
同一个 Provider
不同模型
具有不同 Capability
```

禁止：

```text
Provider 支持某能力
→ 默认所有模型都支持
```

---

# 6. Harness Profile

不同模型应允许绑定不同 Harness Profile。

示例：

```yaml
harness_profile:
  id: deepseek-coding-default

  prompt_strategy:
    system_template: deepseek_system_v2
    tool_instructions: deepseek_tools_v1

  context:
    strategy: hierarchical
    compaction_threshold: 0.75

  tools:
    schema_style: openai
    parallel_calls: true

  retry:
    max_attempts: 2

  validation:
    require_tool_result_check: true

  cache:
    stable_prefix: true
```

这样可以做到：

```text
同一个业务任务
+
不同模型
=
不同 Harness Profile
```

而不是只有：

```text
model = xxx
```

---

# 7. 上下文必须分层，不允许无限堆历史

南枫系 AI 应统一采用分层 Context 设计。

推荐四层：

## L0：长期稳定事实

例如：

```text
Project Bible
架构原则
固定业务规则
长期用户偏好
数据结构定义
工具说明
安全规则
```

特点：

- 变化少
- 适合作为稳定 Prompt 前缀
- 适合缓存

---

## L1：当前项目状态

例如：

```text
当前版本
Git Commit
当前模块
已完成工作
当前已知问题
重要技术决策
```

变化频率中等。

---

## L2：当前任务 Working Context

例如：

```text
本轮目标
正在修改的文件
相关代码
当前错误
测试结果
```

这是 Agent 最需要关注的上下文。

---

## L3：短期操作轨迹

例如：

```text
刚刚执行的工具调用
最近日志
最近命令
最近修改
```

只应保留必要窗口。

---

推荐结构：

```text
Stable Context
↓
Project Context
↓
Task Context
↓
Recent Actions
```

而不是：

```text
全部历史聊天
+
全部文件
+
全部日志
+
所有工具输出
```

无差别塞入上下文。

---

# 8. Context Rot 防护

长上下文不是免费能力。

即使模型支持几十万甚至上百万 Token，也不能默认认为上下文越多越好。

上下文过长可能导致：

```text
重要信息注意力下降
旧信息和新信息冲突
模型反复引用失效决策
工具结果被淹没
Token 成本上升
延迟增加
缓存效率下降
```

因此必须提供：

```text
Context Compaction
Context Summarization
Context Pruning
Relevant Retrieval
```

推荐压缩策略：

```text
达到 Context Window 60%：
开始评估压缩

达到 70%：
压缩旧工具输出

达到 75%：
压缩旧消息

达到 80%：
只保留关键事实 + 当前任务
```

具体比例应由模型实际测试调整，不得写死为业务规则。

---

# 9. 缓存必须成为一等架构能力

Prompt Cache / KV Cache 不能被视为“后续性能优化”。

对于 Agent 类应用：

```text
缓存策略
=
成本架构的一部分
```

尤其当调用包含大量：

```text
System Prompt
Tool Schema
Project Bible
固定规则
长历史前缀
```

时。

---

# 10. Cache-aware Context Construction

客户端无法直接控制 Provider GPU 上的 KV Cache，但可以通过构建稳定 Prompt 前缀提高缓存命中。

必须遵守：

```text
稳定内容放前面
变化内容放后面
```

推荐顺序：

```text
1. System Prompt
2. Agent Rules
3. Tool Definitions
4. Stable Project Context
5. Stable User Context
6. Current Project State
7. Current Task
8. Recent Messages
9. Latest Input
```

---

# 11. 禁止破坏缓存的无意义变化

以下内容必须尽量保持稳定：

```text
Tool 顺序
JSON 字段顺序
Prompt 模板
System Prompt
工具描述
固定 Context
消息序列格式
```

应避免：

```text
每次生成随机 ID 写进 System Prompt
每轮重排 Tool 定义
时间戳插入 Prompt 头部
动态格式化稳定规则
随机改变 JSON serialization
```

这些行为可能让服务端无法识别共享 Prompt Prefix。

---

# 12. 缓存指标不能只看“命中率”

禁止把：

```text
Cache Hit Rate = 98%
```

当作唯一优化目标。

真正需要优化的是：

```text
Task Success Rate
+
Cache Hit
+
Total Tokens
+
Total Cost
+
Retry Count
+
Tool Error Rate
+
Latency
```

即：

> **用更低成本、更少返工稳定完成任务。**

---

# 13. Harness Telemetry

所有南枫系 AI Framework 必须提供统一调用观测。

建议记录：

```text
request_id
timestamp

application
feature
task_type

provider
model
harness_profile

input_tokens
cache_hit_tokens
cache_miss_tokens
output_tokens
reasoning_tokens

tool_calls
tool_errors

retry_count
fallback_count

latency_ms
time_to_first_token

cost_input
cost_output
cost_total

success
validation_passed
error_type
```

如果 Provider 不提供某字段：

```text
null
```

不得伪造或估算成确定值。

## 13.1 模型保真续接合同（Kimi K3 基线）

统一 Provider Framework 只统一边界、观测和失败语义，不得把所有模型回包降格为
`assistant.content`。对需要协议连续性的模型，Adapter 必须解析并保留其续接字段。

- Kimi K3 使用 OpenRouter 固定模型 ID `moonshotai/kimi-k3`。
- K3 Assistant 消息的 `reasoning_content` 与结构化 `tool_calls`（call id、函数名、参数）必须作为消息所属的隐藏续接协议保存；不得合并进可见正文、搜索索引、标题、知识提取或通用交换包。
- 后续 K3 请求必须按原顺序回放可见 `content`、`reasoning_content` 与 `tool_calls`。Provider 原始 envelope、headers、Key 和未解析 raw body 仍不得落库。
- K3 首次用于已有的非 K3 会话时，从当前用户轮开始建立新的 Provider 上下文，不继承该会话此前被压缩或标准化的模型历史；K3 已经回答后，只续接 K3 阶段的上下文。
- K3 采用会话手动 override 的 sticky 语义：一次明确选择同时固定当前会话和后续新会话默认值。K3 不进入 Auto 候选，防止系统在无明确选择时跨模型切入。
- `tool_calls` 被保存不等于工具已执行。没有已批准工具 owner/结果回传闭环时，不得把纯工具调用伪装成完成答案。

## 13.2 Grok OpenRouter 预设合同

- `Grok 4.1 Fast` 固定使用 `x-ai/grok-4.1-fast`，默认显式发送 `reasoning.enabled=false`；`Grok 4.6 High` 是固定模型 `x-ai/grok-4.6` 的产品推理预设，默认显式发送 `reasoning.effort=high`。产品名中的 `High` 不得拼进 Provider 模型 ID。
- 两项均经 OpenRouter Adapter 的统一文本／图片／PDF 请求、流式解析、结构化工具、Token、实际 `usage.cost`、失败诊断和模型归因链；不得新增第二份 Key、旁路端点或把 Grok ID 送往其他 Provider。
- 单次产品输出预算默认封顶 65,536 Token，和 Provider 最大能力分离。实时检索仍只由用户设置与当前信息意图触发 `openrouter:web_search`；xAI 的 `x_search` 由 OpenRouter 插件协议自动补充，应用不得自行伪造未核验工具字段。
- 目录必须只按上述精确 ID 映射。目录缺失、模型下线或能力不满足时失败关闭，不得用 Grok 4 Fast、Grok 4.5、Grok 4.20 或其他相似名称代替。
- Provider 返回费用始终优先。只有 OpenRouter 当前公开价目可精确版本化时才允许 Token 本地估算；搜索调用费、动态路由价或 4.1 Fast 的不确定费率不得猜测为确定金额。

---

# 14. AI 任务成本应按“任务完成成本”计算

以后比较模型，不优先比较：

```text
$/1M Token
```

而优先比较：

```text
Cost Per Successful Task
```

推荐指标：

```text
CST = 总调用成本 / 成功完成任务数量
```

进一步：

```text
Effective Task Cost
=
模型调用成本
+
重试成本
+
Fallback 成本
+
错误返工成本
```

例如：

模型 A：

```text
单 Token 贵
成功率高
一次完成
```

模型 B：

```text
单 Token 便宜
频繁返工
工具调用错误
```

模型 B 未必更便宜。

---

# 15. 模型评估必须基于“模型 + Harness”

南枫系内部 Benchmark 单位不应是：

```text
GPT vs Claude vs DeepSeek
```

而应该是：

```text
GPT + GPT Harness
Claude + Claude Harness
DeepSeek + DeepSeek Harness
```

测试维度建议：

```text
任务成功率
首轮完成率
工具调用成功率
结构化输出成功率
幻觉率
Token 消耗
Cache Hit
延迟
调用成本
返工次数
```

---

# 16. Tool Calling 必须验证，不允许“调用成功 = 任务成功”

Agent Tool Calling 至少要包含：

```text
Tool Request
↓
Tool Execution
↓
Tool Result
↓
Result Validation
↓
Next Step
```

不能：

```text
模型调用工具
↓
HTTP 200
↓
认为任务完成
```

尤其涉及：

```text
账单写入
投资金额
文件修改
Git 操作
数据库写入
用户数据删除
```

必须增加业务层验证。

---

# 17. 高风险任务必须由确定性代码兜底

AI 不负责确定性计算的最终真值。

例如南枫智投：

```text
金额计算
收益率
仓位
估值公式
资产汇总
汇率换算
```

必须由确定性代码计算。

AI 负责：

```text
理解
分类
解释
提取
判断
生成建议
```

而不是直接作为最终账本和金融计算引擎。

---

# 18. 南枫记等自动写入场景的原则

如果 AI 可以自动写账：

必须提供：

```text
AI 提取
↓
Schema Validation
↓
金额/时间/商户规则校验
↓
重复记录检测
↓
短暂用户确认窗口
↓
无操作自动提交
```

自动化不是取消验证。

而是：

> 把人工确认从“每笔必须操作”变成“异常时才操作”。

---

# 19. Provider Fallback 不能简单按模型排名切换

错误示例：

```text
GPT 失败
→ Claude
→ DeepSeek
→ Qwen
```

正确方式是根据：

```text
任务类型
能力要求
Provider 状态
模型 Harness
成本预算
数据敏感级别
```

做路由。

例如：

```text
结构化提取
→ 低成本稳定模型

复杂代码修改
→ Coding Harness 最强模型

OCR 后语义纠错
→ 多模态 / 文本理解强模型

投资研究
→ 强推理模型

简单分类
→ Flash 模型
```

---

# 20. Fallback 前必须判断失败类型

失败必须区分：

```text
NETWORK_ERROR
TIMEOUT
RATE_LIMIT
AUTH_ERROR
PROVIDER_ERROR
MODEL_ERROR
TOOL_ERROR
SCHEMA_ERROR
VALIDATION_ERROR
CONTEXT_OVERFLOW
```

例如：

```text
AUTH_ERROR
```

不应该自动切另一个模型然后继续。

```text
RATE_LIMIT
```

可以切 Provider。

```text
SCHEMA_ERROR
```

可能应该先重试同一模型。

```text
CONTEXT_OVERFLOW
```

应该压缩 Context，而不是立即更换 Provider。

---

# 21. AI Hub 的正确职责

南枫 AI Hub 建议负责：

## Provider Registry

```text
Provider 名称
Endpoint
协议
认证方式
健康状态
```

## Model Registry

```text
Model ID
Provider
能力
价格
Context Window
速度
稳定性
推荐 Harness
```

## Key Management

统一管理：

```text
API Key
余额
权限
状态
```

但应用本地仍必须允许独立填写 API Key。

## Routing

可根据：

```text
任务
成本
性能
健康状态
能力
```

选择 Provider。

## Telemetry

聚合所有南枫系应用调用数据。

## Cost Analytics

统计：

```text
每日成本
每应用成本
每模型成本
每任务成本
缓存节省
失败成本
```

---

# 22. AI Hub 不得承担的职责

AI Hub 不应成为：

```text
业务数据库
账本数据库
知识库本体
唯一 Provider 客户端
所有 Prompt 的唯一存储
应用启动必需服务
单点认证依赖
```

否则 AI Hub 一旦：

```text
损坏
升级失败
配置错误
网络异常
```

会导致所有南枫系应用同时失效。

---

# 23. 本地优先原则

南枫系 AI Framework 默认：

```text
Local First
Cloud Optional
AI Hub Optional
```

本地应保存：

```text
Provider 配置
Harness 配置
任务规则
最近调用日志
必要模型元数据
```

AI Hub 可同步增强，但不能取代本地基本能力。

---

# 24. Provider Adapter 推荐接口

建议：

```typescript
interface AIProviderAdapter {
  getCapabilities(): ModelCapabilities

  generate(
    request: GenerateRequest
  ): Promise<GenerateResponse>

  stream(
    request: GenerateRequest
  ): AsyncIterable<StreamChunk>

  validateConfig(): Promise<ProviderHealth>

  estimateCost?(
    usage: TokenUsage
  ): CostEstimate
}
```

Provider 特有能力允许：

```typescript
interface DeepSeekAdapter extends AIProviderAdapter {
  getCacheUsage(): CacheUsage
}
```

不要为了所谓“统一”删除 Provider 特有能力。

---

# 25. Harness 推荐接口

```typescript
interface AIHarness {
  buildContext(task: Task): Context

  buildPrompt(task: Task): Prompt

  selectTools(task: Task): ToolDefinition[]

  runAgentLoop(task: Task): Promise<AgentResult>

  compactContext(context: Context): Context

  validate(result: AgentResult): ValidationResult
}
```

---

# 26. 模型与 Harness 解耦

配置建议：

```yaml
model:
  provider: deepseek
  id: deepseek-v4-flash

harness:
  profile: deepseek-general-v1
```

后续可以：

```yaml
model:
  provider: deepseek
  id: deepseek-v4-pro

harness:
  profile: deepseek-coding-v2
```

无需修改业务代码。

---

# 27. 统一任务模型

推荐所有 AI 请求都抽象为 Task：

```yaml
task:
  id: xxx

  type: classify

  requirements:
    reasoning: low
    tools: false
    structured_output: true

  constraints:
    max_cost: 0.01
    max_latency_ms: 10000

  validation:
    schema: transaction.schema.json
```

这样 AI Hub 才能未来实现真正的：

```text
Task-based Routing
```

而不是只靠用户手工选择模型。

---

# 28. 应用默认必须允许手工模型选择

即使未来有自动路由，也必须保留：

```text
Provider
Model
Harness Profile
```

的高级设置入口。

原因：

- 便于 Debug
- 便于 Benchmark
- 便于用户控制成本
- 避免路由黑盒
- 方便 Provider 故障切换

---

# 29. 模型调用日志不得只记录 Prompt

建议日志分层：

```text
Metadata Log
Usage Log
Tool Log
Error Log
Debug Prompt Log
```

Prompt / 用户数据可能包含敏感信息。

因此默认：

```text
Metadata 长期保留
Prompt Debug 日志短期保留
```

并允许用户关闭完整 Prompt 日志。

---

# 30. Git 与 AI 开发流程结合

涉及 Codex / Coding Agent 修改南枫项目时继续遵守：

```text
修改前：
检查 Git 状态

重要修改前：
Commit / Backup

修改：
小步完成

每一步：
运行测试

故障：
先定位根因
一次只改变一个主要变量

完成：
更新文档
记录架构决策
```

Harness 不得为了提高“自动化程度”绕开这一原则。

---

# 31. AI 开发任务必须验证结果

Coding Agent 完成后至少验证：

```text
Build
Lint
Unit Test
关键功能测试
Git Diff
```

不能只依赖：

```text
模型说“已完成”
```

---

# 32. 禁止的架构反模式

以下设计原则上禁止：

## 32.1 Universal OpenAI Client

```text
所有模型统一 base_url + api_key
```

## 32.2 AI Hub Hard Dependency

```text
AI Hub 不启动
→ 所有应用 AI 功能不可用
```

## 32.3 Model-only Benchmark

```text
只比较模型
不比较 Harness
```

## 32.4 Unlimited Context

```text
历史越多越好
```

## 32.5 Blind Fallback

```text
失败 → 换模型
```

## 32.6 Tool Call = Success

```text
工具返回 → 自动判定成功
```

## 32.7 Token Price = Real Cost

```text
只比较 $/1M Token
```

## 32.8 Cache Afterthought

```text
缓存以后再优化
```

---

# 33. 统一 Benchmark Framework

建议未来建立：

```text
南枫 AI Benchmark
```

每个典型任务固定测试集。

例如：

## 南枫知识库

```text
笔记分类
主题归并
知识抽取
摘要
跨笔记问答
```

## 南枫记

```text
支付截图提取
账单分类
商户识别
重复记录检测
```

## 南枫智投

```text
财报提取
公司分析
估值解释
风险总结
投资逻辑结构化
```

记录：

```text
Success Rate
First-pass Success
Token Usage
Cache Hit
Cost
Latency
Retries
Tool Errors
```

---

# 34. 模型选择原则

模型选择排序：

```text
1. 能否稳定完成任务
2. Harness 是否成熟
3. 工具调用稳定性
4. 错误率 / 返工率
5. 成本
6. 速度
7. 单 Token 价格
```

不能反过来只看价格。

---

# 35. AI 成本优化优先级

推荐优化顺序：

```text
① 减少失败和返工
② 提高缓存命中
③ 控制 Context
④ 减少无意义 Tool Calling
⑤ 任务分级使用不同模型
⑥ 最后才比较单 Token 价格
```

因为 Agent 成本最大的浪费往往不是贵模型，而是：

```text
错误调用
重试
重复上下文
无意义工具调用
失败后的返工
```

---

# 36. 推荐目录结构

```text
src/
  ai/
    core/
      types/
      task/
      errors/
      telemetry/

    providers/
      base/
      openai/
      anthropic/
      deepseek/
      kimi/
      qwen/
      local/

    harness/
      core/
      profiles/
      context/
      memory/
      tools/
      validation/
      retry/
      cache/

    routing/
      policy/
      fallback/
      capability/

    config/
      providers/
      models/
      harness/

    telemetry/
      usage/
      cost/
      cache/
      errors/
```

应用业务层：

```text
features/
  xxx/
    ai_service.ts
```

不得直接调用 Provider HTTP API。

---

# 37. AI Hub 与应用通信建议

应用优先：

```text
Application
→ Local Provider Framework
→ Provider
```

开启 AI Hub 增强后：

```text
Application
→ Local Provider Framework
→ AI Hub Policy
→ Provider
```

或者：

```text
AI Hub
→ 下发 Model / Provider / Price / Routing 元数据
```

但真正任务请求仍允许应用直接调用 Provider。

---

# 38. AI Hub 离线降级

如果 AI Hub：

```text
不可访问
```

应用应该：

```text
读取本地 Provider 配置
↓
使用最后可用配置
↓
直接调用 Provider
```

界面提示：

```text
AI Hub 当前不可用
已切换本地 AI 配置
```

而不是：

```text
AI 功能不可用
```

---

# 39. 配置版本化

Provider / Harness 配置必须支持版本：

```text
deepseek-coding-v1
deepseek-coding-v2
```

避免直接修改同一配置导致历史任务不可复现。

Telemetry 必须记录：

```text
harness_profile_version
```

---

# 40. 决策可回溯

每次 Harness 大调整应记录：

```text
为什么修改
解决什么问题
修改前指标
修改后指标
测试数据
是否回滚
```

重要决策同步写入 Project Bible / ADR。

---

# 41. 第一阶段实施优先级

## P0

必须先做：

```text
Provider Adapter 抽象
Capability Matrix
Harness 层
统一错误类型
Telemetry 基础
应用独立 Provider 配置
AI Hub 非强依赖
```

---

## P1

随后加入：

```text
Context 分层
Context Compaction
Cache Telemetry
任务级 Validation
Retry Strategy
Fallback Strategy
```

---

## P2

再实现：

```text
Task-based Routing
模型 Benchmark
成本自动优化
Harness 自动选择
跨应用 AI 调用统计
```

---

# 42. 开发验收标准

任何新 Provider 接入必须至少完成：

- [ ] 独立 Adapter
- [ ] Capability Matrix
- [ ] Streaming 测试
- [ ] Tool Calling 测试
- [ ] Structured Output 测试
- [ ] Reasoning 能力测试
- [ ] Context 限制测试
- [ ] 超时测试
- [ ] Rate Limit 测试
- [ ] 错误码映射
- [ ] Token Usage 记录
- [ ] Cost 计算
- [ ] Cache Usage 记录（若支持）
- [ ] Harness Profile
- [ ] Benchmark 测试
- [ ] Fallback 行为验证

---

# 43. 最终统一原则

南枫系软件未来 AI 架构统一遵循以下原则：

1. **模型不是 Agent。**
2. **Provider 兼容不等于能力兼容。**
3. **Harness 是模型实际能力的一部分。**
4. **不同模型允许不同 Harness。**
5. **每个应用必须拥有独立 AI Provider Framework。**
6. **南枫 AI Hub 是增强层，不是基础依赖。**
7. **Provider Adapter 必须处理协议差异。**
8. **不要把所有模型硬塞进 OpenAI Compatible。**
9. **上下文必须分层。**
10. **长上下文必须支持压缩。**
11. **缓存是架构能力，不是后期优化。**
12. **稳定内容放前，动态内容放后。**
13. **Cache Hit 不是唯一 KPI。**
14. **模型评估单位必须是“模型 + Harness”。**
15. **真正要比较的是任务完成成本，而不是 Token 单价。**
16. **Tool Calling 必须有结果验证。**
17. **高风险数据必须有确定性代码兜底。**
18. **Fallback 必须理解失败原因。**
19. **Telemetry 必须覆盖成本、缓存、工具、重试和成功率。**
20. **架构修改必须可测试、可回滚、可追溯。**

---

# 44. 给 Codex 的执行原则

在实现或重构南枫 AI Provider Framework / AI Hub 时：

```text
不要推翻现有项目。

先阅读：
Project Bible
现有 AI Provider 代码
AI Hub 协议
Git 历史
相关 ADR

然后：

1. 判断当前 Provider 层、Harness 层、业务层是否耦合。
2. 优先通过新增抽象层逐步解耦。
3. 不进行一次性大重构。
4. 每次只完成一个独立架构目标。
5. 每一步运行现有测试。
6. 修改前检查 Git 状态。
7. 重要修改前 Commit 或建立可回滚点。
8. 发现失败先定位根因，不同时改变多个变量。
9. 不删除现有用户需求。
10. 本文档中的架构优化不得覆盖用户已经明确确认的产品需求。
11. 如果架构原则与已有明确需求冲突，以明确产品需求为准。
12. 修改完成后同步更新 Project Bible / ADR / 开发文档。
```

---

# 45. 一句话架构定义

> **南枫 AI Provider Framework 的目标不是“让所有模型用同一种方式调用”，而是“让每种模型在保持自身能力的前提下，以统一、可观测、可替换、可验证、可回滚的方式服务于南枫系应用”。**

南枫 AI Hub 的目标则是：

> **集中增强，而不是集中依赖。**
