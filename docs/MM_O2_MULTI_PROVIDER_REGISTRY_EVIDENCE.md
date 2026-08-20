# MM-O2 Logical Model / Deployment / Provider Registry 证据

日期：2026-08-16  
状态：纯领域／Registry 增量已完成；非真实 Provider 或 UI 闭环

## 本次范围

- 新增 `MultiProviderModelRegistry.kt`，把 `LogicalModelId`、`ModelDeploymentId`、`ProviderHandle` 和 Provider-facing `providerModelId` 分离。
- 已确认的 Compare MVP 首组仅为 ChatGPT 与 Claude 两个逻辑模型；`CompareMvpLogicalModels` 不保存动态实际模型 ID，具体 Deployment 仍须由目录解析。
- 首个且唯一 Adapter 身份为 `OPENROUTER_OPENAI_COMPATIBLE`；只保存固定 endpoint 元数据，不读取 Key、不探测服务、不发 HTTP。
- 目录仅支持调用方显式发布、保留一个稳定上版并可交换回滚；不接入既有 Transport、P6-G Router、P3 Runtime、Conversation Tree、Usage Ledger、UI、Room 或任一 P3-J WIP 文件。
- 解析在部署无效、逻辑模型不匹配、Provider 不匹配、失效部署或输入／输出价格未知时失败关闭；不替换目标，不 fallback。
- `HistoricalDeploymentReference` 是 content-free 历史读回投影，固定实际 Provider、实际模型 ID、部署 ID、目录版本和价格版本；本次不写数据库 Schema。

## 自动合同

执行：

```text
JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home \
JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1 \
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests com.nanzhufeng.ai.domain.MultiProviderModelRegistryContractsTest \
  --tests com.nanzhufeng.ai.domain.MultiModelOrchestrationContractsTest \
  --tests com.nanzhufeng.ai.domain.P6GModelRouterContractsTest
```

结果：`BUILD SUCCESSFUL`；**16 tests / 0 failures / 0 errors / 0 skipped**。

覆盖：OpenRouter 首部署的三层映射、目录升级／回滚、未知价格与失效部署 fail-closed、历史事实不被升级改写、ChatGPT／Claude 两逻辑模型 Compare 首组，以及第三 Compare 目标被拒绝。既有 MM-O1 与 P6-G Router 回归同批通过。

## 未证明的事项

这仅证明 JVM 领域合同。它不证明 Provider 可用、凭据有效、HTTP 成功、真实成本、Android／Desktop UI、P3-J 交互、Room 持久化、设备安装、OPPO 或发布闭环。上述能力仍需单独授权、接线和验收。
