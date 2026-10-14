# 全项目合同一致性检查（2026-10-13）

结论：114份合同、40个领域完成登记、结构、适用范围与实现入口核对；修正11类已识别冲突（10份合同正文及1份架构治理文档），其余合同只同步范围标注。既有业务源码、配置、Schema、fixture及测试1041文件内容无变化。当前Luna链、正常自身联网、Muse菜单和Auto行为均沿用本轮检查前实现。

## 当前规则与历史边界

- 领域唯一行为入口仍由[登记表](../../../protocol/contracts/contract-registry-v1.json)与[生成索引](../../contracts/CONTRACT_INDEX.md)决定；分类未改变：{'CURRENT': 24, 'SCOPED': 78, 'EXPERIMENTAL': 8, 'HISTORICAL': 4}。新增[40领域源码定位](../../contracts/DOMAIN_OWNER_MAP.md)含108个路径引用，负责查找实现，不另起业务规则。
- 当前20部署、Muse手动深度入口、RC文字Sol/Astra自身与其他RC/全部RC附件Luna后原模型回答，统一指向C12/P6G及生成模型矩阵。冻结会话选择、明确接收方与原模型身份、物理调用计量和旧记录兼容保持原实现。
- P3J预备链、P6G实验规划、旧Sol助手、旧智谱独立服务和旧验收不能恢复为当前生产规则。P7A/P7B原envelope/恢复材料的局部合同不能再作为direct同步资格门；隐藏WORK仍保留原数据隔离和独立owner。

## 已识别冲突与裁决

| 文件 | 旧口径风险与统一结果 | 实现核对 |
|---|---|---|
| [P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md](../../../docs/P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md) | 旧Auto/深度名单及Sol助手与现行部署冲突；当前正文或历史范围已明确 | [ChatModelRouting.kt](../../../app/src/main/java/com/nanzhufeng/ai/domain/ChatModelRouting.kt)、[auto-routing-rc-domestic-20261010.json](../../../protocol/fixtures/auto-routing-rc-domestic-20261010.json)、[chat-shell.mjs](../../../desktop/src/chat-shell.mjs)、[NormalChatRoutingSnapshot.kt](../../../app/src/main/java/com/nanzhufeng/ai/domain/NormalChatRoutingSnapshot.kt) |
| [ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md](../../../docs/ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) | 现行界面正文仍复制旧Auto链/退役候选/Relay重复分组；当前正文或历史范围已明确 | [ChatModelRouting.kt](../../../app/src/main/java/com/nanzhufeng/ai/domain/ChatModelRouting.kt)、[chat-shell.mjs](../../../desktop/src/chat-shell.mjs)、[auto-routing-rc-domestic-20261010.json](../../../protocol/fixtures/auto-routing-rc-domestic-20261010.json) |
| [C12_MODEL_NETWORK_PARITY_CONTRACT.md](../../../docs/C12_MODEL_NETWORK_PARITY_CONTRACT.md) | 旧Sol附件协议、固定19/6数量与当前Luna来源归属冲突；当前正文或历史范围已明确 | [AutomaticWebSearchPolicy.kt](../../../app/src/main/java/com/nanzhufeng/ai/ai/AutomaticWebSearchPolicy.kt)、[AttachmentWebSearchPlan.kt](../../../app/src/main/java/com/nanzhufeng/ai/ai/AttachmentWebSearchPlan.kt)、[attachment_web_search_v1.rs](../../../desktop/src-tauri/src/attachment_web_search_v1.rs)、[desktop_ordinary_chat_v1.rs](../../../desktop/src-tauri/src/desktop_ordinary_chat_v1.rs) |
| [P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md](../../../docs/P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md) | 旧v1预备布局/禁止链以同级标题越过历史边界；当前正文或历史范围已明确 | [NormalChatEgressAuthorization.kt](../../../app/src/main/java/com/nanzhufeng/ai/domain/NormalChatEgressAuthorization.kt)、[relaycore_web_search_v1.rs](../../../desktop/src-tauri/src/relaycore_web_search_v1.rs) |
| [RELAYCORE_MODEL_CURRENT_CONTRACT.md](../../../docs/RELAYCORE_MODEL_CURRENT_CONTRACT.md) | 旧末尾复核裁决仍用现行口吻恢复Sol助手；当前正文或历史范围已明确 | [relaycore_web_search_v1.rs](../../../desktop/src-tauri/src/relaycore_web_search_v1.rs)、[RelayCoreWebSearchCompanion.kt](../../../app/src/main/java/com/nanzhufeng/ai/ai/RelayCoreWebSearchCompanion.kt) |
| [AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md](../../../docs/AI_USAGE_COST_AND_BALANCE_LEDGER_CONTRACT.md) | 计量迁移数字和旧服务空模型字段需与当前Luna物理调用分开；当前正文或历史范围已明确 | [ChatCallAccounting.kt](../../../app/src/main/java/com/nanzhufeng/ai/domain/ChatCallAccounting.kt)、[NanfengAiDatabase.kt](../../../app/src/main/java/com/nanzhufeng/ai/data/local/NanfengAiDatabase.kt)、[lib.rs](../../../desktop/src-tauri/src/lib.rs) |
| [P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md](../../../docs/P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md) | 输入Adapter局部无HTTP限制不能扩展到普通发送全链；当前正文或历史范围已明确 | [NormalChatOpenRouterExecutor.kt](../../../app/src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt)、[attachment_document_text_v1.rs](../../../desktop/src-tauri/src/attachment_document_text_v1.rs) |
| [P7A_LOCAL_E2EE_SYNC_FOUNDATION_CONTRACT.md](../../../docs/P7A_LOCAL_E2EE_SYNC_FOUNDATION_CONTRACT.md) | 旧envelope格式与direct生产资格边界未在正文入口明确；当前正文或历史范围已明确 | [P7FConversationSyncWireFormat.kt](../../../app/src/main/java/com/nanzhufeng/ai/data/P7FConversationSyncWireFormat.kt)、[sync_v1.rs](../../../desktop/src-tauri/src/sync_v1.rs) |
| [P7B_ACCOUNT_KEY_STATE_MACHINE_CONTRACT.md](../../../docs/P7B_ACCOUNT_KEY_STATE_MACHINE_CONTRACT.md) | 旧恢复码状态机规则容易重新成为生产同步门；当前正文或历史范围已明确 | [P7FManualConversationSync.kt](../../../app/src/main/java/com/nanzhufeng/ai/data/P7FManualConversationSync.kt)、[p7d_sync_coordinator_v1.rs](../../../desktop/src-tauri/src/p7d_sync_coordinator_v1.rs) |
| [C15_WORKSPACE_KNOWLEDGE_PARITY_CONTRACT.md](../../../docs/C15_WORKSPACE_KNOWLEDGE_PARITY_CONTRACT.md) | 隐藏WORK与保留隔离能力应分开，不能从旧验收恢复入口；当前正文或历史范围已明确 | [ProductScope.kt](../../../app/src/main/java/com/nanzhufeng/ai/domain/ProductScope.kt)、[product-scope.mjs](../../../desktop/src/product-scope.mjs) |
| [architecture-governance.md](../../../docs/architecture-governance.md) | 独立P2 MultiModelOrchestrator不能声明为普通生产唯一owner；当前正文或历史范围已明确 | [NormalChatOpenRouterExecutor.kt](../../../app/src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt)、[lib.rs](../../../desktop/src-tauri/src/lib.rs) |

## 维护整理

- 所有合同头部及索引显示登记的适用范围；旧记录保留原文件、格式、字段与证据。P6G标题进一步标注实验/历史，并保留旧标题锚点。模型完整名单不再在UI与P6G重复维护，引用真实catalog、共享Auto计划及生成矩阵。
- 新增离线一致性工具：登记表覆盖、唯一领域入口、实现路径、生成索引/映射漂移和已裁决旧规则回归检查。工具只识别明确列举的冲突，不自动裁决新语义；日期本身不能把仍有效的条款降为历史。
- 日常维护：修改领域/模块边界时更新登记scope；移动实现时更新owner map；行为变化仍走领域合同、源码与对应平台验收。重复执行下列门禁，生成文件只由工具更新。

```sh
python3 protocol/scripts/verify-contract-governance.py --write
python3 protocol/scripts/audit-contract-consistency.py --write
python3 protocol/scripts/verify-model-service-contract.py
python3 -m unittest discover -s protocol/tests -p 'test_contract*.py'
```

## 分层验证

| 检查 | 本轮结果 | 证明范围 |
|---|---|---|
| 合同治理与一致性 | 114/114，40/40 PASS | 文件登记/范围/唯一入口、108源码引用、生成结果与已知旧条款 |
| 合同维护工具测试 | 22 PASS | 历史嵌套、日期保留、旧助手回归、缺源码、异常登记与表格安全等 |
| 模型维护门与测试 | 20聊天+1专用部署、7凭据组；16 PASS | 现有catalog/manifest/矩阵/Auto菜单一致，不证明服务可用 |
| Desktop现有测试 | 544 PASS / 12 SKIP，0FAIL | 原有离线行为回归；不代替Provider和原生UI |
| 合同本地锚点 | 无缺失候选、无本轮新断链 | Markdown标题/显式id静态核对；不访问网络 |
| 原文件保护 | 1041/1041 SHA-256一致 | 已列既有业务源码/配置/Schema/fixture/测试保持内容；新增维护测试单列 |
| 全仓结构清单 | 1410 tracked条目；另补当前未跟踪业务hash | 文件/格式检查，非逐行语义或应用验收 |

## 边界与后续

覆盖了全部登记合同正文的结构/范围和冲突关键词检查，以及40领域实现入口与本轮热点条款的人工核对；不是逐句完整语义证明，不承诺不存在其他潜在技术债。后续新增行为必须从领域入口和真实owner核对，不能仅因静态门通过宣称全功能通过。

7份旧UI XML证据尾部带命令输出，严格XML解析为junk after document element；原样保留，见[结构摘要](structure-summary.json)。它们不是当前应用资源或新业务失败。没有为通过扫描改写历史证据。

本轮无Provider/云端请求、无设备操作、无安装，也未重建软件。上一轮真实验收与正式安装状态保持原结论：多附件18/20完成，RC Pro404、Muse403及普通文字7个非通过样本仍有缺口；原生picker/UI、生命周期及扫描媒体验收仍未补。见[实现与真实验收报告](../2026-10-13-luna-routing-implementation/REPORT.md)。离线测试不替代这些缺口。

## 可复核与回退

[逐合同114项结果](contracts-review.json)、[11项裁决及源码](conflicts.json)、[业务保护结果](business-preservation.json)、[原文件hash](protected-business-hashes.json)。本轮独立差异与修改前文档保存在`source-delta.patch`和`before-docs/*.before`；只能按文件审阅回退本轮差异，不得全仓reset/clean/stash或覆盖既有WIP。
