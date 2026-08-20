# MM-O3-B Production Composition Readiness 证据

日期：2026-08-16  
状态：只读目录投影与惰性容器注册完成；未接 UI 或真实 Provider

## 结论

`OpenRouterVerifiedMultiProviderRegistryProjection` 是唯一的 MM-O3-B 目录 adapter。它从当前
`VersionedModelRegistry.currentSnapshot(OPENROUTER)` 读取并只接受 `OPENROUTER_CATALOG`、`VERIFIED`、
存在 catalog SHA-256、且未使用 fallback preset mapping 的快照。它不会读取 credential、settings、P6-G、
UI、P2-M synthetic fixture，也不具备 HTTP client 或目录写入能力。

ChatGPT 与 Claude 是固定的逻辑身份；其 deployment 只由显式 `ModelPresetId` map 连接到已验证 snapshot
内的具体模型。provider-facing model ID、价格、price version、catalog version 和 capturedAt 都从该 snapshot
复制。一个逻辑模型可有多个 OpenRouter deployment；每个 deployment ID 是稳定哈希，因此 Direct 必须带精确
`LogicalModelId + ModelDeploymentId + ProviderHandle` 才能 resolve。`HistoricalDeploymentReference` 读回实际
provider/model/deployment/catalog/price facts。

未验证、非 OpenRouter 目录来源、无 hash、fallback、无逻辑映射、跨逻辑映射歧义都会拒绝；未知价格保留为精确
`PRICE_UNAVAILABLE` 拒绝。投影为只读，publish/rollback 均返回 `READ_ONLY_PROJECTION`。

`AppContainer` 仅通过 `DirectExecutionProductionComposition` 注册惰性 `DirectExecutionApplicationOwner`、其
projection 与 P3 preflight。构造不调用 credential presence/has/load、settings load、Auto Router、HTTP、Provider
probe、runtime、Usage 或 Room 写路径；默认 coordinator 的 transport/runtime/Usage ports 均 disabled。owner 没有
注入 Activity、ViewModel、Workspace 或任何 UI。

## 自动合同

`OpenRouterVerifiedMultiProviderRegistryProjectionContractsTest` 覆盖：

- verified OpenRouter snapshot 的 ChatGPT/多个 Claude 精确部署、动态 Provider model 与历史 target readback；
- unverified、fixture source、fallback、缺失 mapping 的 fail-closed；
- unknown price 的 `PRICE_UNAVAILABLE` 与 read-only mutation 拒绝；
- production composition 构造 0 credential load/presence/has，且 AppContainer 只注册、Activity 未注入 owner。

本轮回归 MM-O1、MM-O2、MM-O3-A2、P3 preflight/coordinator/P3-I（含 Room/test transport adapter）、P2-M
bridge、P6-G 与 P3-J，共 **61 tests / 0 failures / 0 errors / 0 skipped**；`assembleDebug` 为
`BUILD SUCCESSFUL`。

## 未证明事项与下一唯一缺口

未读取或保存 Key，未调用真实 HTTP，未探测 Provider，未写 Usage/Room，未接 UI，也未操作设备、OPPO、安装或发布。
本轮没有读取 app-private 目录，因此不对现场是否已有可投影快照作断言；当时不存在时，Direct 正确保持 readiness
rejection。下一唯一缺口是通过既有 P2-J 目录验证路径获得真实 verified snapshot；不得以 P3-C fixture、P6-G、
展示名或 UI 猜测替代。
