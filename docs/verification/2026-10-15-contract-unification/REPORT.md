# 全项目合同冲突复核与统一

复核时间：2026-10-15T18:26:19+08:00；基线 `5a52aa2a8928cc46bad136cb608fd1eb3b62517e`，验证范围为含未提交实现的 WORKTREE。

本轮只修改合同正文、治理元数据、索引、审计脚本和维护测试，未改变现有功能。旧模型修复WIP保留；用户补答重连后的Android正式补丁覆盖另见[原模型复查记录](../2026-10-15-other-model-network-review/REPORT.md)，不计为合同验收。

## 覆盖与裁决原则

- 114份登记合同、40个领域全部清点：CURRENT 24、SCOPED 77、EXPERIMENTAL 8、HISTORICAL 5；检查全文登记/范围/引用、章节和已知冲突断言，反查全部领域owner。18份正文修正，8项范围标签细化，4个领域补入已有生产/存储owner。
- 冻结2154份跟踪及非ignored未跟踪文件，包含1819份跟踪与335份WIP；完整清单/结构扫描与自然语言语义复核分开。热点裁决沿现行源码与原测试入口，不因日期较新就覆盖隐私、安全、格式或平台边界。
- 1234份非文档、非本轮治理工具/元数据/维护测试的输入逐文件hash比对，变动0、缺失0、新增业务文件0；HEAD未变。没有通过修改功能来消除合同冲突。

## 具体冲突与裁决

| 范围 | 旧断言／冲突 | 当前裁决 | 正文 | 实现核对点 |
|---|---|---|---|---|
| 设置／WORK／旧控制面 | 旧表格仍列工作区与开发诊断；P5-A要求重新恢复旧入口 | 当前设置只显示三组；高级仅随WORK开关，开发诊断不随之恢复；旧恢复任务归历史 | ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md、C15_WORKSPACE_KNOWLEDGE_PARITY_CONTRACT.md、P5A_LOCAL_CONTROL_SURFACE_ENTRY_CONTRACT.md | NanfengAiApp.kt、android-settings-shell.mjs、ProductScope.kt |
| 运行状态与生产外发 | P3-B把fixture Disabled写成生产OpenRouter固定状态 | fixture无网络与生产规范化事件接入分开；保留完成元数据追加和终态不变式 | P3B_RUNTIME_EVENT_STATE_MACHINE_CONTRACT.md | NormalChatOpenRouterExecutor.kt、RoomConversationRepository.kt |
| 数量账本平台差异 | P0称双端均无production caller | Android独立Room内核与普通计量分开；Mac既有生产账本/IPC已接线 | P0_USAGE_LEDGER_CONTRACT.md | AppContainer.kt、lib.rs、usage_ledger_v1.rs |
| 显式预览与普通检索 | P4-E默认关闭被读成所有Context禁止检索；运行时合同复制千问参数 | 默认关闭只约束显式本机预览；普通聊天沿已保存开关与LocalContextBroker；协议参数只路由C12 | P4E_LOCAL_KNOWLEDGE_RETRIEVAL_CONTRACT.md、ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md | KnowledgeManagement.kt、LocalContextBroker.kt、ChatProviderAdapters.kt |
| 搜索／媒体／列表日期 | P6-F2仍称E/G未实施，且写今天/昨天与末消息日期 | 删除失效实施结论；Mac按现有createdAt/fallback字段显示绝对日期；排序与显示不混同 | P6F2_UNIFIED_SEARCH_HISTORY_AND_LOCAL_CONTENT_PREVIEW_CONTRACT.md | chat-shell.mjs::conversationLocalDate、desktop-attachment-preview-owner.mjs |
| 账号旧文案 | P7-B只允许显示未连接，弹层永久纯白 | 旧文案限定为当时；当前账号与direct资格归P7-F，弹层消费当前主题 | P7B_ACCOUNT_KEY_STATE_MACHINE_CONTRACT.md | P7FManualConversationSync.kt、desktop_account_sync_v1.rs |
| Agent与普通聊天 | P8-A称App整体无Provider且无UI | 未接模型/外部工具只约束Agent；保留P8-B/C既有只读状态/inspect | P8A_CONTROLLED_AGENT_LOCAL_RUNTIME_CONTRACT.md | P8ControlledAgentRuntime.kt、p8_agent_ledger_v1.rs |
| Claude JSON当前接线 | 早期未完成检查点否定Desktop实现 | 声明现有双端owner，旧检查点归历史；未取得的UI退出证据不补造 | P6I_CLAUDE_EXPORT_JSON_ADAPTER_CONTRACT.md | AppContainer.kt、ClaudeExportJson.kt、lib.rs |
| 精确复用增量 | L1称尚未持久化，与已实现L2冲突 | L1类型、L2存储、L3分派、L4引用校验分清；仍不宣称普通聊天复用或Provider缓存命中 | PROMPT_CACHE_AND_EXACT_REUSE_CONTRACT.md | RoomLocalExactReuseEntryStore.kt、local_exact_reuse_v1.rs |
| 交换v1/v2与旧metadata | P6-C泛称Android无导入写库、价格/catalog不联网 | 前者限定旧v1，当前v2恢复接线继续；旧metadata限制不禁止真实模型目录/计量 | P6C_DESKTOP_LOCAL_WRITE_WORKBENCH_CONTRACT.md | AndroidWorkspaceExchangeV2ExportPort.kt、WorkspaceExchangeV2AtomicRestore.kt、desktop_model_service_v1.rs |
| 预览与选取容量 | 预览Adapter复制旧统一容量，容易成为另一份限制 | 选取/类型容量只读P6-D2及实际owner，能选取不冒充能播放/解码 | P6F2E_AUDIO_AND_GENERIC_FILE_ADAPTER_CONTRACT.md | ConversationAttachments.kt、attachment-document-types.mjs |
| UI重复规则 | 产品/TEMP重复旧hover、橙色和固定尺寸；C16单端事实覆盖双端 | 产品保留IA；TEMP保留隔离与24小时生命周期；当前视觉只看平台合同/主题owner，旧参考明确归历史 | CHAT_FIRST_INTENT_ORGANIZATION_CONTRACT.md、P6E_TEMPORARY_CONVERSATION_DUAL_PLATFORM_CONTRACT.md、C16_THEME_FONT_OVERLAY_PARITY_CONTRACT.md | ProductScope.kt、TemporaryConversation.kt、desktop-theme-owner.mjs |
| 测试安全边界 | 旧自适应合同允许模拟器connected/instrumentation | 改为永久禁止connected任务；使用现有本地与非仪器化隔离路径，Schema17标历史 | P5A_ADAPTIVE_ACCESSIBILITY_BASELINE_CONTRACT.md | AGENTS.md、项目测试交付Skill |

历史文件名、协议字段、旧检查点及验收事实保持可读；原引用标题不因改成历史子章节而丢失。历史分类只归档一次入口恢复任务，不删除保留owner、数据或仍需兼容的格式。

## 防止复发

- 一份领域规则只在索引指定的入口维护；平台UI、联网参数、容量、账本和兼容格式不再由旧子合同重复裁决。
- 新增8类精确旧规则哨兵，防止已修正断言重新出现在现行正文；加强明确历史事实/范围、子章节及同级当前章节的识别。围栏支持tilde和不同长度backtick，代码内伪标题不再吞掉后续现行规则。
- 初始新增维护回归实际出现10个失败；修正文档和审计实现后29项维护测试通过。现有断言只证明已复核的特定冲突，不冒充自然语言全语义证明。
- 重生成合同范围头、领域索引和owner索引；维护步骤和业务输入不变门见[合同治理](../../CONTRACT_GOVERNANCE_CURRENT.md)。

## 验证与保留缺口

- PASS：合同登记/引用/生成一致性、owner与语义哨兵、模型维护21+1/7、29项维护测试、git diff --check；11个Markdown锚点预检无失效。完整逐文件安全摘要见[review.json](review.json)。
- 全库结构检查：JSON 363、XML 87、Python 15、Node 213、shell 6项语法通过；7份历史Android原生XML证据存在ParseError，保留原件，未通过改历史证据伪造全绿；其余文件的路由/清单扫描不等于编译或语义测试。
- 本次未重新编译应用、运行应用全量测试、收费调用、真实同步/部署或UI验收；应用输入未变，原验证仍按原时间/源码范围引用，不能写成本轮重测。前序RC Gemini Pro及全模型/格式真实链路缺口仍开放。
- 没有发现可由本轮机器门禁继续报告的已知冲突，不代表每句自然语言、所有行为或外部服务均已证明正确。

## 交付与回滚

- 未提交、推送、发布或迁移数据。原脏工作区及用户资产保留。
- 本机审计快照保留每份合同原文、治理文件before、原始红灯、结构结果与输入hash。只对本轮列出的合同/治理文件做逐文件恢复，不使用reset/clean或恢复整个工作树。
- 业务恢复点不是本轮文档基线：本次无需回滚应用或数据库。正式包安装回执与文档快照分别保管。
