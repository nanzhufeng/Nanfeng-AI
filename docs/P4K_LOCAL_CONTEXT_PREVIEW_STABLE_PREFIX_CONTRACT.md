# 南枫 AI P4-K 本机抽取预览与稳定前缀元数据合同

日期：2026-08-13  
状态：P4 的第十一个本地增量；Schema 保持 14。P4-K 把 P4-J 已冻结的抽取式压缩接入 Context 控制面，并只建立稳定前缀的本地元数据 IR；没有 Prompt、RunSpec、Provider cache、HTTP、Key、费用或外发。

## 唯一链与范围

```text
用户明确勾选 P4-D 来源（默认全关、仅 ViewModel 内存）
→ ReadLocalContextPreviewUseCase
→ ReadLocalContextCompressionUseCase（P4-J）
→ StableContextPrefixMetadataDomain（P4-K）
→ EXTRACTIVE_LOCAL 瞬时预览
```

`ReadExplicitContextBodyUseCase` 仍独占候选、scope、revision/hash 重读与高敏拒绝；`LocalContextCompressionDomain` 仍独占预算、排序、Unicode code point 截断、前后抽取与 hash。UI 只能选择有界 `COMPACT/BALANCED/EXPANDED` 档位，数字解释只能由 P4-J Domain 的 `toPolicy()` 完成，禁止 UI `take()`、重排或自行截断。

空选择必须得到零条结果；未点击“本机抽取预览”不得读取正文。关闭、重开、会话切换或进程重建必须丢弃选择、压缩预览与前缀元数据。任何 P4-D 的缺失会话、Project 竞态、过期 revision/hash、越权/隐藏来源或高敏拒绝，都整体映射为 P4-K 安全错误，绝不显示部分条目。

## 预览与隐私

预览固定标记 `EXTRACTIVE_LOCAL / p4j-extractive-v1`，明确不是语义摘要、回答质量或 Provider 请求。每条只显示 layer/kind、source ID 的 SHA-256 安全摘要、原始/保留/省略 code point 数、source/compressed SHA-256 短摘要与截断状态；不得显示 URI、路径、附件字节、标题、正文或压缩片段。

预览和其选择不写 Room、Export、Invocation、`ConversationSettings.memorySources`、Eval、日志、诊断或缓存；不会自动替换当前 Context，更不会进入发送链。所有 App 自有 Dialog/选择面内容底色为 `#FFFFFFFF`。

## 稳定前缀元数据 IR

`StableContextPrefixMetadataDomain` 只能基于本次明确选择且 P4-J 已安全压缩的 L0/L1 `GLOBAL_MEMORY`、`GLOBAL_KNOWLEDGE`、`PROJECT_INSTRUCTION`、`PROJECT_MEMORY`、`PROJECT_KNOWLEDGE` 元数据生成 `p4k-stable-prefix-metadata-v1`。它稳定排序并哈希 format、layer、kind、stable source ID、revision 与 source hash；不含正文、压缩正文、Prompt 或 Provider cache key。SYSTEM/SAFETY 尚未作为可选正文物化，因此不伪造其输入或 fingerprint 成分。

元数据比较仅产生未来缓存设计可使用的失效理由：来源集合变化、revision/source hash 变化、format 变化。P4-K 不持久化这份 IR，不创建 Provider cache、不报告命中率、Token、成本、延迟或收益，也不声称可复用真实前缀。

## 离线 Eval 与退出证据

P4-I dataset 升级为 `p4i-baseline-2`，只新增机械事实：默认关闭、显式来源、Unicode 截断、hash/顺序、原子拒绝、预览不持久化、前缀 fingerprint/失效元数据与 `NO_EGRESS`。它不衡量摘要质量、缓存收益、真实用户价值或模型表现。

最低证据为 P4-J/P4-K 定向合同、全量单测、Lint、现有正式签名 Debug/Release、v2/v3 证书核验及 API 35 `emulator-5554` 的默认关闭→明确选择→本机抽取预览→关闭/force-stop/重开丢弃链。不得操作 OPPO。上述本地证据不替代真实 Provider、费用、语义摘要、真实 cache、目标真机、图标或发布验收；P4-K 不是 P4 或项目终点。
