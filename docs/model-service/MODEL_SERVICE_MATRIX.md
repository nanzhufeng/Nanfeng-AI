# 模型与接口维护矩阵

> 自动生成自 `protocol/model-service/model-service-v1.json`；不要手改。
> 这是仓库当前配置与维护规范，不证明外部服务、价格或真实能力始终可用。

返回[总架构](MODEL_SERVICE_ARCHITECTURE.md)或[新增与升级流程](MODEL_SERVICE_MAINTENANCE.md)。

## 平台与凭据分组

| 配置身份 | 平台 | 默认预设 | 离线协议 | 联网协议／路线 |
|---|---|---|---|---|
| `OPENROUTER` | OPENROUTER | `GPT_6_LUNA` | CHAT_COMPLETIONS | CHAT_COMPLETIONS／`OPENROUTER_SERVER_TOOL` |
| `DEEPSEEK` | DEEPSEEK | `DEEPSEEK_V4_PRO` | CHAT_COMPLETIONS | MESSAGES／`DEEPSEEK_MESSAGES` |
| `ZHIPU` | ZHIPU | `GLM_5_3_FLASH` | CHAT_COMPLETIONS | CHAT_COMPLETIONS／`ZHIPU_CHAT_COMPLETIONS` |
| `QWEN` | QWEN | `QWEN_3_7_PLUS` | CHAT_COMPLETIONS | RESPONSES／`QWEN_RESPONSES` |
| `RELAYCORE_CLAUDE` | RelayCore | `RELAYCORE_SONNET_5_5` | MESSAGES | COMPANION／`RELAYCORE_COMPANION` |
| `RELAYCORE_GPT` | RelayCore | `RELAYCORE_GPT_6_1_SOL` | CHAT_COMPLETIONS | RESPONSES／`RELAYCORE_GPT_RESPONSES` |
| `RELAYCORE_GEMINI` | RelayCore | `RELAYCORE_GEMINI_3_8_FLASH` | CHAT_COMPLETIONS | COMPANION／`RELAYCORE_COMPANION` |

## 当前普通聊天模型

| 预设身份 | 模型 | API ID | 凭据身份 | 选择位置 | 专属规则 |
|---|---|---|---|---|---|
| `CLAUDE_OPUS_5_5` | Claude Opus 5.5 | `anthropic/claude-opus-5.5` | `OPENROUTER` | 深度 | OR_OPUS_MEDIUM |
| `CLAUDE_SONNET_5_5` | Claude Sonnet 5.5 | `anthropic/claude-sonnet-5.5` | `OPENROUTER` | 日常 | OR_HIGH |
| `CLAUDE_HAIKU_5_5` | Claude Haiku 5.5 | `anthropic/claude-haiku-5.5` | `OPENROUTER` | 日常 | OR_HAIKU_SEARCH_ENTRY |
| `GPT_6_1_SOL` | GPT-6.1 Sol | `openai/gpt-6.1-sol` | `OPENROUTER` | 深度 | OR_HIGH |
| `GPT_6_LUNA` | GPT-6 Luna | `openai/gpt-6-luna` | `OPENROUTER` | 日常 | 共用规则 |
| `GEMINI_3_8_FLASH` | Gemini 3.8 Flash | `google/gemini-3.8-flash` | `OPENROUTER` | 日常 | OR_GEMINI_SEARCH_INTENT |
| `DEEPSEEK_V4_PRO` | DeepSeek V4 Pro | `deepseek-v4-pro` | `DEEPSEEK` | 深度 | DEEPSEEK_MESSAGES |
| `DEEPSEEK_V4_FLASH` | DeepSeek V4.1 Flash | `deepseek-flash` | `DEEPSEEK` | 日常 | DEEPSEEK_MESSAGES |
| `MUSE_WONDER` | Muse Wonder | `meta/muse-wonder` | `OPENROUTER` | 深度 | OR_MUSE_RESPONSES |
| `GEMINI_4_ARGON` | Gemini 4 Argon | `google/gemini-4-argon` | `OPENROUTER` | 深度 | OR_GEMINI_SEARCH_INTENT |
| `GLM_5_3` | GLM-5.3 | `glm-5.3` | `ZHIPU` | 深度 | GLM_BUDGET, ZHIPU_SCOPE |
| `GLM_5_3_FLASH` | GLM-5.3 Flash | `glm-5.3-flash` | `ZHIPU` | 日常 | GLM_BUDGET, ZHIPU_SCOPE |
| `QWEN_3_7_PLUS` | Qwen3.7-Plus | `qwen3.7-plus` | `QWEN` | 日常 | QWEN_CNY |
| `QWEN_3_8_MAX` | Qwen3.8-Max | `qwen3.8-max` | `QWEN` | 深度 | QWEN_MAX, QWEN_CNY |
| `RELAYCORE_OPUS_5_5` | Opus 5.5 · Kiro正价 | `claude-opus-5.5` | `RELAYCORE_CLAUDE` | RelayCore/DEEP | RC_COMPANION, RC_CLAUDE_EFFORT, RC_OPUS_ALIAS, RC_CNY |
| `RELAYCORE_SONNET_5_5` | Sonnet 5.5 · Kiro正价 | `claude-sonnet-5-5` | `RELAYCORE_CLAUDE` | RelayCore/DAILY | RC_COMPANION, RC_CLAUDE_EFFORT, RC_CNY |
| `RELAYCORE_GPT_6_1_SOL` | GPT-6.1 Sol · Pro3 | `gpt-6.1-sol` | `RELAYCORE_GPT` | RelayCore/DEEP | RC_REQUIRED, RC_CNY, RC_COMPANION |
| `RELAYCORE_GPT_6_ASTRA` | GPT-6 Astra · Pro3 | `gpt-6-astra` | `RELAYCORE_GPT` | RelayCore/DEEP | RC_REQUIRED, RC_ASTRA_ADAPTIVE, RC_CNY, RC_COMPANION |
| `RELAYCORE_GEMINI_3_8_FLASH` | Gemini 3.8 Flash · Gemini | `gemini-3.8-flash` | `RELAYCORE_GEMINI` | RelayCore/DAILY | RC_COMPANION, RC_CNY, RC_GEMINI_GROUNDED_OUTPUT |
| `RELAYCORE_GEMINI_3_1_PRO` | Gemini 3.1 Pro · Gemini | `gemini-3.1-pro` | `RELAYCORE_GEMINI` | RelayCore/DEEP | RC_COMPANION, RC_CNY, RC_GEMINI_GROUNDED_OUTPUT |
| `QWEN_3_6_FLASH` | Qwen3.6 Flash | `qwen3.6-flash` | `QWEN` | Auto候选专用 | QWEN_CNY |

## 独立任务模型

| 预设 | API ID | 用途 |
|---|---|---|
| `GLM_OCR` | `glm-ocr` | DOCUMENT_OCR；不进入普通聊天／Auto |

## 专属差异

| 规则 ID | 适用预设 | 规则摘要／权威合同 |
|---|---|---|
| RC_REQUIRED | `RELAYCORE_GPT_6_1_SOL`, `RELAYCORE_GPT_6_ASTRA` | Sol/Astra普通文字Responses使用required、external_web_access=true及include web_search_call.action.sources；附件路径遵循RC_COMPANION，真实来源和返回身份分别验证。（[合同](../RELAYCORE_MODEL_CURRENT_CONTRACT.md)） |
| RC_COMPANION | `RELAYCORE_OPUS_5_5`, `RELAYCORE_SONNET_5_5`, `RELAYCORE_GPT_6_1_SOL`, `RELAYCORE_GPT_6_ASTRA`, `RELAYCORE_GEMINI_3_8_FLASH`, `RELAYCORE_GEMINI_3_1_PRO` | 普通文字Sol/Astra自身Responses，其余四款及RC六款联网附件由固定OpenRouter Luna原生检索后交回原模型；准确接收方／材料授权，来源、不同币种用量及失败分别保留。（[合同](../RELAYCORE_MODEL_CURRENT_CONTRACT.md)） |
| RC_CLAUDE_EFFORT | `RELAYCORE_OPUS_5_5`, `RELAYCORE_SONNET_5_5` | 普通Messages SSE；Opus medium、Sonnet high，system与完整原材料保留。（[合同](../RELAYCORE_MODEL_CURRENT_CONTRACT.md)） |
| RC_OPUS_ALIAS | `RELAYCORE_OPUS_5_5` | 唯一已验同组别名：claude-opus-5-5→claude-opus-5.5；不是运行时自动fallback。（[合同](../RELAYCORE_MODEL_CURRENT_CONTRACT.md)） |
| RC_ASTRA_ADAPTIVE | `RELAYCORE_GPT_6_ASTRA` | 简单查询low，复杂查询medium，effort与搜索上下文一致，模型身份保持。（[合同](../RELAYCORE_MODEL_CURRENT_CONTRACT.md)） |
| OR_OPUS_MEDIUM | `CLAUDE_OPUS_5_5` | OpenRouter reasoning.effort=medium；不套RC的output_config参数。（[合同](../P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md)） |
| OR_HIGH | `CLAUDE_SONNET_5_5`, `GPT_6_1_SOL` | 当前OpenRouter默认high；联网不能删除reasoning字段。（[合同](../P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md)） |
| QWEN_MAX | `QWEN_3_8_MAX` | Responses enable_thinking=true、reasoning low，上限16384；离线字段由Qwen适配器处理。（[合同](../C12_MODEL_NETWORK_PARITY_CONTRACT.md)） |
| GLM_BUDGET | `GLM_5_3`, `GLM_5_3_FLASH` | thinking enabled；内部显式附件检索low，原选最终回答及普通请求max（C12）；Mac输出预算32768，Android受当前profile约束，预算不冒充用量。（[合同](../C12_MODEL_NETWORK_PARITY_CONTRACT.md)） |
| ZHIPU_SCOPE | `GLM_5_3`, `GLM_5_3_FLASH` | 附件要点作为search_query；关闭意图分支并要求实际搜索及来源。（[合同](../C12_MODEL_NETWORK_PARITY_CONTRACT.md)） |
| DEEPSEEK_MESSAGES | `DEEPSEEK_V4_PRO`, `DEEPSEEK_V4_FLASH` | 联网走anthropic Messages原生搜索，单请求5–12次；离线仍Chat Completions。（[合同](../C12_MODEL_NETWORK_PARITY_CONTRACT.md)） |
| RC_CNY | `RELAYCORE_OPUS_5_5`, `RELAYCORE_SONNET_5_5`, `RELAYCORE_GPT_6_1_SOL`, `RELAYCORE_GPT_6_ASTRA`, `RELAYCORE_GEMINI_3_8_FLASH`, `RELAYCORE_GEMINI_3_1_PRO` | 当前账户名义金额为CNY；usage.cost没有可信结算语义时不作美元实付，不二次换汇。（[合同](../AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md)） |
| QWEN_CNY | `QWEN_3_7_PLUS`, `QWEN_3_8_MAX`, `QWEN_3_6_FLASH` | 新本地估算按冻结北京标准价CNY，历史金额不重算，估算不是结算。（[合同](../AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md)） |
| RC_GEMINI_GROUNDED_OUTPUT | `RELAYCORE_GEMINI_3_8_FLASH`, `RELAYCORE_GEMINI_3_1_PRO` | 检索后grounded普通回答与Android现行profile对齐，不发送legacy max_tokens；同模型／Key／端点，无自动协议恢复。合法完成但无可见正文明确EMPTY_COMPLETION，不伪造回答。（[合同](../C12_MODEL_NETWORK_PARITY_CONTRACT.md)） |
| OR_MUSE_RESPONSES | `MUSE_WONDER` | Muse纯文字与完整本地可读文件经同OpenRouter／同模型／同Key的Responses原生搜索；不发送不兼容required，不转换媒体路线。结构化真实来源与终态仍验收；费用按真实Responses回执保留。（[合同](../C12_MODEL_NETWORK_PARITY_CONTRACT.md)） |
| OR_GEMINI_SEARCH_INTENT | `GEMINI_3_8_FLASH`, `GEMINI_4_ARGON` | 开启自身搜索时在原Chat Completions请求中使用web plugin（engine=native）启动原生grounding，保留搜索指令；不同时发可选server tool或不兼容required。文字／可读文件／媒体共用，禁用或grounded回答不注入；同模型／渠道／Key、来源成功门保持。该plugin兼容例外的后续迁移须同题真实验收。（[合同](../C12_MODEL_NETWORK_PARITY_CONTRACT.md)） |
| OR_HAIKU_SEARCH_ENTRY | `CLAUDE_HAIKU_5_5` | 联网开启使用原模型OpenRouter Chat Completions web plugin engine=exa，在请求入口检索后由原Haiku回答；原生可选工具与required组合实测仍可跳过。无可读公共计划的原生媒体同请求处理，可读材料仍逐项核验，关闭或grounded不注入。（[合同](../C12_MODEL_NETWORK_PARITY_CONTRACT.md)） |
