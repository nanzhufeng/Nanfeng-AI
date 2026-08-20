# 南枫 AI P2 Provider 真实健康门证据

日期：2026-08-21
状态：**外部门阻止；未尝试 Provider HTTP。**

## 结论

本轮按 `P2M_REAL_TEXT_EXECUTION_CONTRACT.md` 审核了唯一允许的最小文本链路。正式签名的用户级项目命名配置完整，但允许检查的 Provider 凭据来源不完整：项目专属 Provider 环境变量不存在，用户级项目命名属性也没有 Provider 凭据 schema。Provider 凭据的产品所有者是 Android 应用私有加密存储；本轮没有读取、解密、导出、记录或探测该存储，更没有触碰 OPPO 或既有/禁用 AVD。

因此没有一个可在本轮合法使用的、已验证的 Provider 凭据或应用可见逐次同意，不能发行/消费新的真实 RunSpec nonce，也不能构造 `Authorization`、请求或 Provider Attempt。按照 fail-closed 合同，停止于凭据/授权外部门，而不是把本地测试称为健康成功。

## 非敏感配置与成本边界

| 项目 | 已核验事实 |
| --- | --- |
| Provider | OpenRouter |
| 固定端点 | `POST https://openrouter.ai/api/v1/chat/completions` |
| 模型来源 | 仅已保存、启用且已验证的 OpenRouter preset 解析；本轮未读取应用私有实际选择值 |
| 可尝试范围 | 仅 `p2m-openrouter-text-v2` 合成文本，`stream=false`、`temperature=0.2`、结构化 `title`/`body` |
| 尝试上限 | 最多 1 条 HTTP Attempt，`retryCount=0` |
| 费用上限 | USD 0.01；未返回费用必须保持未知，不能记作零 |

上述范围没有实际执行。图像、附件、用户内容、真实 Token/费用、模型质量、Candidate/Knowledge 写入、真实重启导出、目标真机与 OPPO 均不在本轮证据内。

## 本地合同回归

使用 Android Studio JBR、`JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` 和 `--no-daemon` 执行：

```text
:app:testDebugUnitTest
  P2KOpenRouterInferenceTransportContractsTest  5/5
  P2LRealServiceAcceptanceContractsTest          9/9
  P2MRealTextExecutionBridgeContractsTest        3/3
```

共 17 项，0 failures、0 errors、0 skipped。范围是本地 fake/离线合同：P2-L 验证 DryRun 的零网络/零 Key bytes，P2-M 验证仅一条 Attempt 的桥接边界，P2-K 验证 `ExactSingleUseRun` 的零重试约束。测试没有启动 Android 设备、没有访问 Provider 网络，不能证明凭据有效、账号余额、模型响应、质量、Token 或成本。

## 后续门

若未来存在用户明确配置且可合法检查其**存在性**的凭据，并且应用内可见确认页对同一冻结 RunSpec 重新给出逐次同意，才可经 P2-M 的一次性 nonce 执行一次合成非敏感文本请求。无论成功或失败都必须在该 Provider 尝试后立即停止；图片和 P2 全部出口仍需独立合同与证据。
