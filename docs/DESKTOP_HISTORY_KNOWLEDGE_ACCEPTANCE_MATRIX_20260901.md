# Desktop 历史资料库与 interests 验收矩阵（2026-09-01）

| 项目 | 状态 | 证据 | 边界 |
| --- | --- | --- | --- |
| 隐藏 `interests` 持久化 | 通过 | SQLite 独立 portable 表、设置 roundtrip、旧值长度／NUL 门禁 | 页面不显示字段 |
| 普通聊天消费 | 通过 | Rust 请求断言含“关注方向”；回答审计只含标题、不含正文 | 仅启用 Memory 时使用 |
| P6-K profile 导入 | 通过 | `publicBio` 映射与回读测试 | 只补空值，不覆盖已有值 |
| 本机备份／恢复 | 通过 | portable 值回读；活动候选恢复为 `UNKNOWN` | 设备专属设置仍不覆盖 |
| Google 加密同步 | 通过 | `safe_settings/profile-interests` 加密记录、恢复和 hash 回读测试 | 仅随用户明确选中的纯文本对话 |
| 12 小时低频调度 | 通过 | schedule、cooldown、开关暂停／取消、启动与周期 wake | 不承诺进程退出后后台常驻 |
| 当前成功分支选择 | 通过 | current leaf、4 条消息、成功 Attempt、兄弟分支／FAILED／CANCELLED／UNKNOWN／PARTIAL 排除测试 | 附件和工具内容不发送 |
| checkpoint／幂等 | 通过 | reservation 先提交；source hash 唯一；显式重试新 Attempt／同 checkpoint | UNKNOWN 不自动重发 |
| 严格模型输出 | 通过 | malformed、低 confidence、NOT_ELIGIBLE 测试 | 输出上限 768 Token |
| 候选审阅 UI | 通过 | Browser 宽／窄屏；原生显示来源、模型、Token、checkpoint；编辑标题后接受 | Web 预览不读 SQLite |
| Knowledge 接受 | 通过 | localhost 原生候选从 `PENDING_REVIEW` 到 `ACCEPTED`，新增独立 `ACTIVE` Knowledge；domain intent 回读 | 未调用真实 Provider |
| 删除语义 | 通过（自动） | Rust 测试确认删除已接受候选仍保留 `knowledge_id` | GUI 删除未执行，避免无即时确认的本机删除 |
| 用量与费用 | 通过 | 候选保存实际模型／Token；独立 Usage Ledger 回读 `FINAL_MEASURED` | mock 未报告费用，保持未知 |
| schema 30 → 31 | 通过 | 迁移测试安全关闭旧未确认开关；独立 owner 的当前版本边界全量回归 | 高版本继续 fail closed |
| 自动回归 | 通过 | Node `120/120`；Rust `148/148`；lint、typecheck、protocol golden、build、cargo check | 0 failed；0 skipped／ignored |
| Browser 视觉 | 通过 | `1440×960` 双栏；`390×844` 单栏菜单／详情可恢复，修复原竖排裁切 | Web capability 诚实禁用 |
| 原生隔离 | 通过 | Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.81079.mthyir5e`；根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.OJOmyC` | localhost only；正式数据、账号、Key、OPPO 未触碰 |

## 原生回读摘要

- 候选：`PENDING_REVIEW`，4 条来源消息，`OPENROUTER / fixture-history-actual`，输入 41、输出 67、费用未报告。
- 用户把标题编辑为“历史资料库隔离验收（已核对）”并确认；候选变为 `ACCEPTED`，Knowledge 变为 `ACTIVE r1`。
- SQLite 候选回执：`knowledge-history-accept-30a3f58ad43326606`；domain intent：`history-accept-30a3f58ad433266066f05b034ee228f5`。
- 隔离签名副本可执行 SHA-256：`76e3a00a83d7e1ae2cb8233a41ca276d9189d7f1888a831ffe410edc5f3794a8`。

## 未覆盖

- 未使用真实 DeepSeek／智谱／Qwen／OpenRouter Key 或账单。
- 未读取正式 Desktop 数据、用户附件或真实账号；未调用 Google／Supabase。
- 未运行 `connected*AndroidTest`，未连接、安装或操作 OPPO。
