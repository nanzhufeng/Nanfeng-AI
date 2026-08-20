# 南枫 AI P4-M L3 本地短期操作轨迹合同

日期：2026-08-13  
状态：已实现本地只读元数据闭环；Schema 保持 15。它补齐 P4-D 正文之外的 L3 元数据预览，不是 Provider、Prompt、RunSpec、缓存、语义摘要或任何外发能力。

## 目标与唯一所有者

仅允许从既有、当前 Conversation 的用户发起本地动作谱系读取有限元数据（动作种类、稳定 intent/Invocation ID 的 SHA-256 安全摘要、终态和时间）。`LocalActionTraceDomain → ReadExplicitLocalActionTraceUseCase` 是唯一公开只读入口；它不能直接读取运行日志、命令、诊断、Provider chunk、Authorization、模型正文、附件、URI、路径或缓存前缀。

返回值只能是瞬时的 L3 元数据 IR：固定版本、当前 Conversation ID 安全摘要、最多有限条按时间稳定排序的动作标记，以及来源/内容排除说明。它不是正文、不是 Prompt 片段、不会估算 Token，也不能被任何 Harness、UI 或 DAO 拼接为消息。

## 不变量与禁止项

- 必须先复核 P4-B 当前 Conversation/Project metadata；会话缺失、切换或谱系不一致时整体拒绝，不能回退到其他会话或返回部分轨迹。
- 仅用户明确启用 `LOCAL_L3_METADATA` 且逐项选择时读取；默认关闭，关闭、会话切换和进程重建均丢弃选择与预览。UI 仍使用白色 `#FFFFFFFF` 选择面。
- 不增加 Room 表、不迁移 Schema、不写 Intent/revision/日志/导出/同步；复用既有 P3-C append-only 动作谱系只读投影，不能把运行事件当作 L3 来源。
- 已明确纳入且仅纳入 P3-C 的 `CONTINUE`、`RETRY`、`CHANGE_MODEL`：目标 assistant 必须属于当前会话、Invocation 精确匹配、来源 assistant 同会话、终态为 `COMPLETE`/`FAILED`/`CANCELLED`，且为已验证本地 fixture。用户编辑与切分支没有 append-only 用户动作谱系，无法证明来源，明确排除而不补造新事实。
- 固定 `createdAt DESC`、安全 selector 升序，最多 12 条；每项只显示动作种类、安全 ID 摘要、终态、时间和 `p3c-conversation-action-lineage-vN` 来源版本。任何 P4-B metadata、Project/branch 或两次 lineage 回读变化均整体拒绝。
- L3 是独立元数据 IR，不能伪装成 P4-D 正文，不能进入 P4-J/K、Prompt、RunSpec、Harness、Invocation、Export 或 Eval 生产结论；未来若需 Context Builder 消费，必须另立合同。
- 不读取 Key，不构造 Prompt/RunSpec，不发 HTTP，不产生费用，不外发文本或图片；`OpenRouterEgressPolicy.Disabled` 必须保持。
- 不实现网页/PDF/其他 Adapter、语义摘要、真实缓存、账号、Tool 或 Agent。

## 最低验收与停止点

定向合同已覆盖：默认关闭、只读当前会话、稳定顺序/上限、终态/fixture 门禁、正文/Provider/model/Token/费用/附件/URI/路径/命令/日志排除、越权选择拒绝及 lineage 竞态整体拒绝。P4-I `p4i-baseline-4` 只新增默认关闭、当前会话、顺序/上限、敏感字段排除、竞态拒绝、重建丢弃与 `NO_EGRESS` 的机械事实；不声称模型质量或成本。

验证完成：Android Studio JBR 下定向 P4-M/P4-D/I/K 合同与全量 `:app:testDebugUnitTest --rerun-tasks` 均通过（158 tests、0 failures / 0 errors）；`:app:lintDebug --rerun-tasks` 为 0 errors / 11 既有 warnings。`0.3.0-p4m` / code 32 的正式签名 Debug SHA-256 为 `3a30a1777a5d9b733ce0bbe560e20c2d2873634f769f566394f8523f1c01b5a6`，Release 为 `aefbb38702cc3068c96fac1c001d76254d59003d0221087357a7dc145e5b0f95`；两包 v2/v3 均为 true，证书 SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。仅 API 35 `emulator-5554`：默认关闭→启用→选中 2 条→`LOCAL_L3_METADATA · local-l3-metadata-v1` 预览→关闭/force-stop/冷启动后丢弃完成；无谱系的另一会话启用 L3 后显示零候选，未串入旧会话。未操作 OPPO、未读写 Key、未构造 Prompt/RunSpec、未发 HTTP/文本/图片。

这些本地证据均不替代真实 Provider、费用、OPPO、图标或发布验收。

达到上述 L3 本地元数据预览闭环即停止；语义摘要、真实缓存、Provider、费用、网页/PDF/其他 Adapter、同步、账号、Tool/Agent 和 OPPO 都仍是独立后续工作。
