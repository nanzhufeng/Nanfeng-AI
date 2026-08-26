# 南枫 AI 项目约束

- 用户可见的新功能须遵守 [新增功能审阅规则](docs/ANDROID_DESKTOP_USER_ENTRY_AUDIT_20260816.md)：同一改动同步记录 Android／Desktop 的去留状态、入口建议和必要理由，但**不得**恢复已删除的用户可见“设置 → 功能审阅”入口；工程决策写入相应合同或 `PRODUCT_FEEDBACK_DECISION_LEDGER.md`。未明确建议前，不得擅自增加聊天主页、Composer 或会话详情的常驻按键。
- 当前阶段状态、交付边界和未闭环项以 [总控完成审计](docs/MASTER_PLAN_COMPLETION_AUDIT_20260816.md) 为准。
- 普通聊天的个性化、Memory 与资料库上下文只以 [Android 当前运行时上下文合同](docs/ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md) 为正文；`P4-*`、`domain-rules.md`、`implementation-plan.md` 与 `architecture-governance.md` 中“显式选择／不构造 Prompt／不自动进入 Context”的阶段描述仅保留其历史或独立本机预览流程，绝不得覆盖当前普通发送链路。
