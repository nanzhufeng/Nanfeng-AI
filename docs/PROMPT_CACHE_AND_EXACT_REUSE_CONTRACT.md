# Prompt Cache 与精确复用合同（未来阶段；未实现）

本合同不编号为 P6-H，避免路线冲突；P6-E 不读取 Key、不构造 Provider 请求或联网。集成时必须复核实时 catalog/capability/policy 表；2026-08-13 官方依据：[Claude](https://platform.claude.com/docs/en/build-with-claude/prompt-caching)、[OpenAI](https://developers.openai.com/api/docs/guides/prompt-caching)、[data controls](https://platform.openai.com/docs/models/default-usage-policies-by-endpoint)。

- 结果层固定为 `LOCAL_EXACT_HIT`（本地精确复用、零网络）、`PROVIDER_PREFIX_HIT`（仍有 Provider 请求/外发）、`MISS`、`INELIGIBLE`、`UNKNOWN`；预计或缺 usage 绝不冒充命中。
- Prompt 顺序：工具/schema → 安全/system/template version → 产品/workspace/project 指令 → 规范排序的显式 Knowledge/Memory Context → root-to-leaf 对话历史 → 当前用户内容及时间/随机/requestId 动态后缀；动态字段不在断点前。
- 本地 exact key 必含 scope/user/workspace、provider、精确 model snapshot、endpoint/API mode、generation params、tool/schema hash、prompt/policy/template version、Context manifest/revision hash、消息树 hash、附件 hash/敏感级别、canonicalization version。跨 Provider/model/project 不共享；编辑仅失效该节点和后代；导入须验证 provenance/scope/model/params/context/policy，文本相似不够；TEMP 零索引。
- Claude Adapter：`tools→system→messages`；稳定指令/知识显式断点、增长对话可自动断点；最多 4 写断点/20 block lookback。默认 5m，只有复用预测覆盖高写价且隐私允许才 1h；首次写 single-flight；以 `cache_creation_input_tokens`/`cache_read_input_tokens` 为事实。
- OpenAI Adapter：对实时能力表确认的 GPT-5.6+ 使用 breakpoint + `prompt_cache_key`，稳定前缀至少 1024 tokens；implicit/explicit 依增长历史与动态后缀；最多 4 新写断点、最近 50 查看、每 key 约 15 rpm 稳定分片、默认 30m TTL。旧模型/24h retention 经能力、隐私、保留门；以 `cached_tokens`/`cache_write_tokens` 为事实。
- 使用 canonical JSON、固定工具序、稳定前缀桶、保留长前缀压缩、single-flight；不付费预热。短 TTL negative cache 仅确定性校验失败，不能缓存瞬态 Provider 错误。
- 模型/Provider/参数、template/system/policy、tool/schema、Context revision、编辑/分支、附件/来源撤销、敏感/egress、用户设置均失效。遥测仅存结果层、snapshot、不可逆 key hash、安全短 ID、token/耗时/估算节省/失效原因/catalog-policy version；不存 Prompt/回答/Key/URI/path。
- 设置包含本地大小/确认清理、Provider TTL/隐私提示/统计；本地清理不承诺清 Provider。P6-F 每条消息预留 cache outcome/usage；P6-G 先 exact，再路由；未来测试覆盖 key、序列化、branch/edit、TTL、single-flight、revoke、TEMP、import provenance、usage 缺失 UNKNOWN。网络缓存只在正式 Provider 阶段实现。
