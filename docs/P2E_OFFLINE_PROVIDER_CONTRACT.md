# 南枫 AI P2-E 离线 Provider 合同

日期：2026-08-12  
状态：已实现本地领域合同与 fixture 验证；未联网、未读取真实 Key、未创建 HTTP 请求

## 1. 唯一目标与允许链路

```text
经审核的 Model Registry Snapshot
→ VersionedModelRegistry（当前稳定版 / 上一稳定版）
→ OpenRouterOfflineAdapterContract
├─ OpenRouterJsonCodec：请求编码
└─ OpenRouterJsonCodec：响应解码
→ InvocationRecord
   └─ Task Run → Provider Attempt → Generation → Validation
```

- `VersionedModelRegistry` 是实际 Model ID、能力、价格版本与预设映射的唯一所有者；模型设置仍只保存人类可理解的预设，不保存任意模型 ID。
- P2-E 不含 HTTP client、网络权限使用、真实目录抓取、真实 Key 读取或传输实现。`OpenRouterOfflineAdapterContract` 只在内存中编码和解码字符串。
- 当前 App 不内置任何被宣称为“当前可用”的 OpenRouter Model ID。测试仅使用 `fixture/structured`，它不是 Provider 目录事实，也不进入设置 UI。

## 2. Registry Snapshot

- `ModelRegistrySnapshot` 带有 schema version、catalog version、来源、捕获时间、验证状态、验证时间、模型、能力、价格与预设映射。
- 只有 `VERIFIED`、非空且预设映射完整的 OpenRouter Snapshot 能被发布为当前稳定版；新版本发布时，旧当前稳定版保留为上一稳定版。
- 未验证、空目录、非 OpenRouter 或重复发布的快照一律被 `ModelRegistrySnapshotInvalid` 拒绝；没有已发布快照时解析预设返回 `ModelRegistrySnapshotUnverified`。
- 真实目录后续必须遵循“拉取 → 比较 → 验证 → 发布 → 保留上一稳定版”，并单独取得联网授权；不得把远端即时变化直接覆盖本地稳定版。

## 3. OpenRouter Adapter 编解码合同

- 请求为非流式 `chat/completions` 兼容 JSON：固定 `stream=false`、默认温度 `0.2`、`response_format.type=json_object` 和输出合同版本元数据。
- Adapter 只接受已经由已验证 Snapshot 解析出的预设/实际 Model ID，且模型必须同时支持 Text 与 Structured Output；否则在编码前拒绝。
- 响应仅提取安全的内存字段：Provider request ID、结构化候选正文、Token 用量、费用和价格版本。原始 response JSON 不进入 Invocation、Room、日志或导出。
- JSON 解析失败、缺少首个结构化正文、Token 不是整数或费用不能精确换算为微货币单位时，统一返回 `ProviderResponseFormatInvalid`。

## 4. 用量、费用与 Invocation

| 字段 | `null` | `0` |
| --- | --- | --- |
| Token 用量 | Provider 未返回或该字段未知 | Provider 明确返回零 Token |
| `ProviderCost.totalMicros` | 费用或价格版本未知 | 已验证为免费或本地成本 |

- 已知费用必须同时保存 `priceVersion`、ISO 三位货币和非负微货币值；不得用浮点数存储或把未知写成零。
- `InvocationRecord` 保持安全元数据并附带唯一的 `TaskRun`。被确认前阻止的运行不创建 Provider Attempt；已发生的调用按 `Task Run → Provider Attempt → Generation → Validation` 记录。
- Generation 只保留状态、时间与确定性校验结果，不保留完整 Prompt、完整响应、Key、原图或附件正文。

## 5. 最小验证与停止条件

- Registry 仅发布已验证快照、保留上一稳定版并按预设解析。
- 非流式结构化 JSON 请求编码固定且转义确定；响应编解码能区分缺失值与已验证零费用。
- 无已验证 Snapshot 时 Adapter 在构造任何传输请求前被拒绝；本增量没有传输实现。
- 成功 Invocation 包含完整四层结构，既有确认门禁仍生成没有伪造 Attempt 的 `BLOCKED` Task Run。
- 完成本地单测、Lint、正式签名 Debug/Release 和 API 35 模拟器可行验证后停止。真实目录核验、真实 Key、文字/图片外发、Candidate/Knowledge 正式 UI、账号/同步/Hub/Agent 继续不在本增量。
