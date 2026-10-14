# 南枫 AI P1 附件外发资格与授权投影合同

<!-- contract-governance:start -->
> **合同口径：领域／模块限定合同。** 领域入口：[ Composer材料与附件桥 ](P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** 南枫 AI P1 附件外发资格与授权投影合同
<!-- contract-governance:end -->

日期：2026-08-15  
状态：纯领域、内存 fail-closed gate；未接入 UI、Provider、HTTP、文件读取、Key、AppContainer、ViewModel 或实际外发

## 目标和边界

P1 只为既有本地 `AttachmentId` 定义将来“可进入显式确认流程”的最小安全判断。它不是上传器、确认 UI、文件读取器、Provider 配置、模型路由、费用结算或授权替代。

```text
既有 ConversationAttachmentReference 的安全元数据
→ AttachmentEgressIntent / capability projection
→ future explicit consent + fee acknowledgement
→ memory-only, single-use authorization summary
→ future owner (本阶段不存在)
```

进程重启丢弃全部授权；没有持久化回读、后台恢复或自动重试。这是有意的 fail-closed 语义。

## 安全输入、资格与绑定

`AttachmentEgressIntent` 只持有：Attachment ID、SHA-256、MIME、大小、推导内容类型、Conversation/Execution ID 以及受限服务商/模型 capability。它刻意不含显示名、URI、path、私有 storage key、文件字节、正文、Prompt、response、Key、credential、Authorization 或 HTTP 数据。

资格必须同时满足：

- capability 明确允许该 MIME、内容类型和大小；P3-G 的本地上限仍为 20 MB。
- 显式 consent 的 intent hash 与当前 Attachment/Conversation/Execution/capability 精确相同。
- 单独费用确认存在且已确认；授予摘要只保存其 hash，不保留费用正文。
- consent 与费用确认时间不得晚于当前时间，授权必须严格早于 `expiresAt`。

## 可撤销、过期与防重放

- `replayToken` 与 authorization ID 均唯一：完全同一 active request 仅返回安全读回；任一不同返回 `REPLAY_CONFLICT`。
- 授权只能处于 `ACTIVE → REVOKED|CONSUMED|EXPIRED`；消费只标记单次 handoff，不会读取附件或外发。
- 已撤销、已消费、过期或进程重启后的授权均拒绝；没有任何补发、续期或恢复路径。
- read/projection 仅返回 `AttachmentEgressSafeSummary`：ID、SHA-256、MIME、大小、内容类型、Conversation/Execution 绑定、服务商/模型、capability/consent/fee hash、过期时间和状态。

## 验证和未覆盖范围

领域合同测试覆盖：显式同意/费用确认、MIME/大小/内容类型能力拒绝、Conversation/Execution 绑定、可撤销、过期、单次消费、replay conflict 与字段白名单。

这不证明用户已确认、模型能接收文件、费用真实有效、Provider 已配置或文件已上传。新增用户确认 UI、读取私有文件、Provider/HTTP/Key 或任何实际外发必须另行授权并单独实施。
