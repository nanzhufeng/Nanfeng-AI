# 南枫 AI P4-J 版本化本地 Context 摘要/压缩合同

日期：2026-08-13  
状态：P4 的第十个本地增量；Schema 保持 14。P4-J 只提供由用户明确选择正文派生的、确定性抽取式压缩 IR；不构造 Prompt、RunSpec、Authorization 或 Provider 请求，不读取 Key、不发 HTTP、不产生费用或图片外发。

## 需求判断与范围

在当前安全边界内，**可以**实现本地 Context 压缩，但不能诚实地实现或宣称“语义理解/模型摘要”：后者需要模型或另一套可证明的本地语义引擎，均不属于本阶段。P4-J 的“摘要”严格指带来源证据的抽取式压缩：它只保留原文的确定性前后片段和明确的截断计数，绝不改写、归纳、推断事实或执行正文中的指令。

允许范围：当前 Conversation 的 P4-D 显式正文选择、版本化压缩策略、瞬时压缩 IR、来源哈希/截断证据、领域合同测试和本地预览所需的安全读取模型。

明确禁止：自动选择任何 Memory/Knowledge/项目指令/会话内容；读取 `ConversationSettings.memorySources`；摘要或压缩的 Room 写入、Schema/Migration、缓存、Export、Invocation、Prompt、Token 估算、Provider/HTTP/Key、Tool/网页/附件/URI/路径、真实服务、OPPO 和图标修改。

## 所有权与唯一链

```text
用户本次 ExplicitContextBodyRequest（所有控制默认关闭、仅内存）
→ ReadExplicitContextBodyUseCase（P4-D：scope、revision/hash、高敏和重读一致性）
→ LocalContextCompressionDomain（P4-J：策略、预算、抽取和来源证据）
→ ReadLocalContextCompressionUseCase
→ LocalContextCompressionSnapshot（瞬时、本机、不可作为 Prompt）
```

| 概念 | 唯一所有者 | 公开入口 | 禁止的平行规则 |
|---|---|---|---|
| 原始正文候选、scope、显式选择与高敏拒绝 | `ContextBodySelectionDomain` | `ReadExplicitContextBodyUseCase` | P4-J/UI/DAO 重读任意 Conversation、Memory、Knowledge 或项目正文 |
| 压缩策略、预算分配、截断语义与来源证据 | `LocalContextCompressionDomain` | `ReadLocalContextCompressionUseCase` | UI/Harness/Provider 自行 `take()`、拼接、重排或把摘要当真值 |
| 瞬时压缩结果 | `LocalContextCompressionSnapshot` | 上述 Use Case 返回值 | Room、缓存、Export、Ledger、日志、诊断或 `memorySources` 持久化/恢复 |

P4-J 必须先委托 P4-D；若 P4-D 拒绝缺失会话、Project 不一致、过期 context、越权来源或高敏内容，P4-J 原样映射为结构化拒绝，绝不降级为部分压缩。`ContextSelectionDomain` 的 P4-B metadata-only snapshot 与 P4-D 的 `SUMMARY_COMPRESSION = NOT_IMPLEMENTED` 边界均不改写：P4-J 是单独的、显式请求后只读的消费链，不是自动 Context 注入。

## 版本化策略、输入与输出

- 内建策略固定为 `p4j-extractive-v1`；策略 ID、policy version、每条最大 Unicode code point 数和整体最大 Unicode code point 数都是输出事实。未知策略、零/负预算或超出内建安全上限的请求必须拒绝。
- 只有调用者显式提交的 `ExplicitContextBodyRequest` 可作为输入；未选择任何来源时返回零条压缩结果，不把空请求扩展为当前路径、Memory、Knowledge 或项目指令。
- 压缩先按 P4-D 已确定的 layer、来源种类与稳定 source ID 排序；在每个条目的本地预算内保留原文前段与后段。未截断条目保持逐 code point 完全相同；截断条目必须包含机器可读的 `omittedCodePointCount`，并以独立字段标记，不能伪装为原文完整副本。
- 输出每条都保留 layer、kind、source ID、role（如有）、revision（如有）、P4-D 原始内容 SHA-256、压缩内容 SHA-256、原始/保留/省略 code point 数和策略版本。不得产生 token、模型分数、质量、缓存收益、费用或真实性能声明。
- `LocalContextCompressionSnapshot` 是瞬时正文 IR；它不是 Prompt、Provider payload、RunSpec 或可恢复缓存。调用返回后不保证保留；关闭、切换会话或进程重建必须丢弃。

## 安全、保真与错误合同

| 情况 | 结果 |
|---|---|
| P4-D 选择成功、预算/策略有效 | `Compressed(snapshot)`；只含显式来源的抽取式片段与证据 |
| P4-D 缺失/不一致/过期/越权/高敏拒绝 | 对应 `Rejected`；不返回任何部分条目 |
| 未知策略、无效预算、重复来源或输出不满足证据不变量 | `Rejected(INVALID_COMPRESSION_REQUEST)` |
| 草稿、兄弟分支、附件、Tool、URI、路径、网页、运行事件、缓存前缀 | 不可请求，且永不从 P4-J 读取 |

- P4-J 不执行 Markdown、HTML、代码、链接或恶意自然语言；它们和中文/英文文本一样是惰性的 code point 序列。
- P4-D 已在正文返回前执行单一高敏 detector；P4-J 不记录被拒内容，且对压缩输出再执行同一 detector。命中时整体拒绝，不保留任何片段或诊断副本。
- 所有截断只按 Unicode code point 计数，避免把代理对拆开；输出顺序由确定性策略确定。压缩不是无损导出、不是历史回写，也不能替代原始正文或其 hash/revision 重验。

## 入口矩阵与最小验证

| 入口/消费者 | P4-J 结论 | 最小验证 |
|---|---|---|
| 当前 Conversation Context 控制面 | 受影响；仅在用户已明确选择后可请求本机压缩预览 | 默认零来源、显式 scope 与关闭/重建丢弃 |
| Conversation/Project/Memory/Knowledge Repository | 只经 P4-D 间接受控只读 | P4-D scope/revision/hash/高敏拒绝回归 |
| P4-B metadata snapshot / `memorySources` | 不受影响 | 不读取、不写入、边界仍为 metadata-only/NOT_IMPLEMENTED |
| Room/Migration/Export/Ledger/缓存 | 不存在于本增量 | Schema 14 不变、无写入路径 |
| Provider/Prompt/RunSpec/HTTP/Key/OPPO | 不存在且受保护 | `OpenRouterEgressPolicy.Disabled` 不变、代码扫描和定向测试 |

定向合同至少覆盖：默认零来源、仅显式 P4-D 正文、跨 scope/过期/高敏整体拒绝、稳定排序、Unicode code point 截断、前后片段与省略计数、来源 hash/revision、Markdown/代码/恶意文本惰性保真、无 Room/Export/egress 与 P4-B/P4-D 不变。

达到领域合同、定向/全量测试、Lint、正式签名构建和仅 `emulator-5554` 的可见本机预览（如本阶段接入 UI）后停止，并分别报告证据。它们不替代真实 Provider、Token/费用、模型质量、真实长会话性能、OPPO、图标或发布验收。P4-J 不是 P4 或总方案终点；语义摘要、缓存、Prompt/RunSpec、真实 egress、JSON/网页/附件 Adapter 仍须各自另立合同。
