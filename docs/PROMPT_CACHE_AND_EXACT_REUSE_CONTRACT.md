# Prompt Cache 与精确复用合同（P6-L3 本地精确分派门已实现；Provider 缓存仍未实现）

本合同不编号为 P6-H，避免路线冲突；P6-E 不读取 Key、不构造 Provider 请求或联网。集成时必须复核实时 catalog/capability/policy 表；2026-08-13 官方依据：[Claude](https://platform.claude.com/docs/en/build-with-claude/prompt-caching)、[OpenAI](https://developers.openai.com/api/docs/guides/prompt-caching)、[data controls](https://platform.openai.com/docs/models/default-usage-policies-by-endpoint)。

## P6-L1：双端本地精确复用索引（2026-08-20）

- Android `LocalExactReuseIndex` 与 Desktop `local_exact_reuse_v1::LocalExactReuseIndex` 是唯一 owner。它们只接收已经计算的 canonical request hash、各项安全 hash、scope/provider/model/endpoint/template/policy 元数据与既有本地 `responseMessageId`；不接收 Prompt、回答、Key、URI/path、Provider event 或网络状态。
- 仅非临时、非高敏感且字段完整的同一精确键可记录与命中。撤销、过期、任何字段变化、临时会话或高敏感请求均不命中；缺键或非法键为 `UNKNOWN`，绝不推断为命中。命中只返回既有本地消息引用，并明确“不请求 Provider”。
- 本阶段是**领域索引和双端合同**，尚未持久化、尚未接入 P3/P2 执行、消息 renderer、Provider cache 或用量台账；不得把它写成普通聊天已经复用、真实节省成本或 Provider cache 命中。

## P6-L2：双端 content-free 持久化与重启读回（2026-08-20）

- Android Room 37→38 与 Desktop workspace SQLite 19→20 只新增 `local_exact_reuse_entries`。其列仅包含 L1 精确键的安全字段、既有 `responseMessageId`、创建/过期时间和撤销状态；不保存 Prompt、回答、Key、URI/path、Provider event、网络状态、token 或金额。
- `RoomLocalExactReuseEntryStore` 与 Desktop `SqliteLocalExactReuseStore` 仍回到 L1 owner 判定，支持相同条目的幂等记录、重启后读回、撤销和过期/撤销清理；损坏或不能重建的本地记录为 `UNKNOWN`，不会推断命中。
- 本增量没有接入普通聊天、P2/P3 dispatch、renderer、Provider prefix cache、设置 UI 或用量/成本台账。因此没有真实复用、网络节省、Provider cache 命中或用户可见新功能，也不需要新增常驻入口。

## P6-L3：双端本地精确分派门（2026-08-20）

- `LocalExactReuseDispatchOwner` / `local_exact_reuse_v1::dispatch` 是唯一 content-free 分派门：`LOCAL_EXACT_HIT` 只交给 `reuseExistingLocalResponse(responseMessageId)`，不会调用普通分派 continuation；`MISS`、`INELIGIBLE`、`UNKNOWN` 只把原有安全 decision 交给 continuation。损坏的 `LOCAL_EXACT_HIT` 若缺消息引用，一律降为 `UNKNOWN`。
- 此门不渲染、不复制消息正文、不创建 Conversation/Message Tree、不建 Provider Attempt、也不写 Usage/Cost Ledger。它未注册到 Android AppContainer、Desktop Tauri/UI、P2/P3 执行或任何 Provider adapter；continuation 是未来显式 egress owner 的端口，不是当前执行能力。
- 定向合同只证明命中不会进入普通分派，非命中不会伪造命中。它不产生普通聊天复用、Provider 请求节省、缓存费用节省或用户可见功能；不增加设置条目或聊天/Composer 常驻入口。

- 结果层固定为 `LOCAL_EXACT_HIT`（本地精确复用、零网络）、`PROVIDER_PREFIX_HIT`（仍有 Provider 请求/外发）、`MISS`、`INELIGIBLE`、`UNKNOWN`；预计或缺 usage 绝不冒充命中。
- Prompt 顺序：工具/schema → 安全/system/template version → 产品/workspace/project 指令 → 规范排序的显式 Knowledge/Memory Context → root-to-leaf 对话历史 → 当前用户内容及时间/随机/requestId 动态后缀；动态字段不在断点前。
- 本地 exact key 必含 scope/user/workspace、provider、精确 model snapshot、endpoint/API mode、generation params、tool/schema hash、prompt/policy/template version、Context manifest/revision hash、消息树 hash、附件 hash/敏感级别、canonicalization version。跨 Provider/model/project 不共享；编辑仅失效该节点和后代；导入须验证 provenance/scope/model/params/context/policy，文本相似不够；TEMP 零索引。
- Claude Adapter：`tools→system→messages`；稳定指令/知识显式断点、增长对话可自动断点；最多 4 写断点/20 block lookback。默认 5m，只有复用预测覆盖高写价且隐私允许才 1h；首次写 single-flight；以 `cache_creation_input_tokens`/`cache_read_input_tokens` 为事实。
- OpenAI Adapter：对实时能力表确认的 GPT-5.6+ 使用 breakpoint + `prompt_cache_key`，稳定前缀至少 1024 tokens；implicit/explicit 依增长历史与动态后缀；最多 4 新写断点、最近 50 查看、每 key 约 15 rpm 稳定分片、默认 30m TTL。旧模型/24h retention 经能力、隐私、保留门；以 `cached_tokens`/`cache_write_tokens` 为事实。
- 使用 canonical JSON、固定工具序、稳定前缀桶、保留长前缀压缩、single-flight；不付费预热。短 TTL negative cache 仅确定性校验失败，不能缓存瞬态 Provider 错误。
- 模型/Provider/参数、template/system/policy、tool/schema、Context revision、编辑/分支、附件/来源撤销、敏感/egress、用户设置均失效。遥测仅存结果层、snapshot、不可逆 key hash、安全短 ID、token/耗时/估算节省/失效原因/catalog-policy version；不存 Prompt/回答/Key/URI/path。
- 设置包含本地大小/确认清理、Provider TTL/隐私提示/统计；本地清理不承诺清 Provider。P6-F 每条消息预留 cache outcome/usage；P6-G 先 exact，再路由；未来测试覆盖 key、序列化、branch/edit、TTL、single-flight、revoke、TEMP、import provenance、usage 缺失 UNKNOWN。网络缓存只在正式 Provider 阶段实现。
