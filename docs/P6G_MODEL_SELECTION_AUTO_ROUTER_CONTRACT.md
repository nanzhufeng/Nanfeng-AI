# P6-G Model Selection & Auto Router Contract

状态：本地 UI/重启读回退出已完成（2026-08-14）；不等于真实模型服务。P6-F2-E 后的 P6-G 仅实现本地、纯领域的路由合同；不读取 Key、不发 HTTP、不执行 Provider 调用。最新证据与阶段切换以 `CURRENT_HANDOFF.md` 的 2026-08-14 当前权威状态为准。

> **当前 Android UI 路由（2026-09-02）：** 本文仍是 Auto、手动 override、持久化与实际模型归因的领域合同；Android Composer 的显示名、固定宽度、模型面位置、激活视觉、遮罩点按和滑动返回，统一以 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 为准。本文的早期“Dialog/菜单”视觉措辞不构成第二套 UI 规则。

## 优先级与目录

- 会话手动 override > 全局默认 > Auto；手动选择绝不经过自动路由。
- “深度”手动选择固定为 `Claude Fable 5.1 → Claude Opus 5 → GPT-6 Astra → DeepSeek V4 Pro → GPT-5.6 Sol → GLM-5.3 → Qwen3.8-Max`。Fable 5.1 位于首项；历史 `logical:deep:claude-fable` 保留为旧版 Fable 5 的精确路由，不能静默升级。`Qwen3.8-Max` 为第七项；`Kimi K3` 不再进入 Composer 选择面或 Auto。历史 `logical:deep:kimi-k3` 只保留为已移出选择的精确旧值，发送前失败关闭，不静默改成 Qwen、Auto 或其他模型；既有消息继续保留当时实际模型归因。
- `Grok 4.1 Fast`（`x-ai/grok-4.1-fast`）已从 OpenRouter 当前公开目录下线：保留其逻辑 ID 仅用于旧会话识别与归因，不出现在选择器、Auto 候选或重试发送中。旧选择必须在读取 Key、外发正文前以“模型已不在服务商目录”失败，绝不静默替换。
- `Grok 4.5`（`x-ai/grok-4.5`）与 `Grok 4.6 High`（`x-ai/grok-4.6`）都已从产品模型列表与 Auto 候选移除；持久化旧选择只能失败关闭，不能回退、近似替换或继续外发。历史消息仍保留当时实际模型归因。
- Auto 在合格候选中优先 Claude/Anthropic，OpenAI/ChatGPT API 是明确 fallback/特长候选：Claude 不可用、限流/超时、能力/上下文/工具/结构化输出不匹配、成本/延迟越界或用户手选时才可切换。
- 选中或 Auto 实际路由到 `Claude Fable 5.1`、`Claude Opus 5`、`GPT-6 Astra`、`GPT-5.6 Sol` 时，OpenRouter 请求必须明确带 `reasoning.effort=high`；联网路由只能添加官方搜索工具，不能清除该参数。其他深度模型不因这一规则被强加 High。Domain 只持久化稳定层级 `FAST / BALANCED / DEEP / APEX_REVIEW`；catalog snapshot 在接入时动态映射真实 model ID/display name/capability/price/context/status，未通过精确映射即不可外发。

## 路由顺序与安全门

1. 合法 exact historical cache 命中；2. 敏感、无同意或离线适合时本地；3. capability；4. sensitivity/egress/cost/budget；5. complexity/risk tier；6. Claude-preferred 合格候选；7. 明确 fallback；8. 必要升级而非无限重试。
- 初始在线调用目标带宽是观测目标而非硬配额：FAST/BALANCED/DEEP/APEX_REVIEW 约 `8/70/20/2`（允许 5–10/65–75/15–25/<=5%），不为凑比例扭曲任务。
- 升级触发器必须可审计：上下文规模、多约束、代码/架构跨度、高风险投资判断、工具步数、解析失败、同任务连续失败、用户最终审查；不按“自信度”单独升级。重要任务优先 BALANCED 产出加 DEEP 独立复核，APEX 只在明确门槛触发。
- 投资、法律、医疗等高风险内容不因模型档位成为业务真值，必须保留不确定性、来源和人工复核。unknown cost 不得当作 0；按本次/日/月预算失败关闭或要求确认。

## 逐条真实记录与 P6-F 接口

- 每次 Invocation 记录 provider/model ID+display snapshot、catalog/policy version、route reason、tier、候选拒绝/fallback reason、token/cost/elapsed、cache/local/online 来源；不含正文或 Key。
- P6-F 逐条显示实际模型；Auto 显示“自动 · 实际模型”，未知不猜。branch/retry/change-model 均保持 node 独立 snapshot。
- 未来验收覆盖 catalog 变动、手动 override、fallback、unknown cost、预算、Auto、cache/local/online、migration/readback/import/export/branch/retry；实际 Provider/Key/HTTP 另立授权验收。

## 当前实现与 P6-G 退出门

- 已实现但**尚非 P6-G 完成**：`app/.../P6GModelRouter.kt` 与 `desktop/src/p6g-model-router.mjs` 是无 I/O 的本地 owner；只消费调用方传入的动态 candidate catalog。双方 fake contracts 已覆盖 exact cache/local safety 前置、手动 override 不回落 Auto、Anthropic 优先与 unknown cost 失败关闭。它们不会生成 Invocation、读取 credential、保存 catalog 或发起 HTTP。
- Android 已有 revision 化 app-private `P6GModelSelectionOwner/Storage`；损坏 catalog 收敛为空。Desktop 已有 SQLite migration 12、typed Rust local owner、catalog/global/conversation override/route metadata commands 与最小 ACL，且前端 localStorage store 已删除。Desktop owner 的定向自动验证已覆盖 migration reopen、revision conflict、corruption fail-closed、manual、Auto、unknown cost 与安全 metadata；不含 Provider 或 Invocation。
- **统一壳层前置（FB-P6-023，2026-08-14）**：用户已否定当前 Android bottom-nav/多根级菜单壳层，并明确当前 Desktop 页面不是最终统一方案。因此暂停所有旧壳层上的 P6-G UI 追加、截图、`.app`/模拟器视觉验收和完成声明。先交付同语义统一 chat-first shell：Android 手机无 bottom navigation，顶部菜单/聊天-工作切换/临时入口，单一对话主体与固定 Composer；drawer 仅含搜索、置顶、最近与底部固定设置；各低频功能从设置进入。Desktop 用常驻或可折叠侧栏表达同一顺序和语义，不能另建 Desktop IA。
- **消息投影前置（FB-P6-024，2026-08-14）**：新壳层的 transcript 必须同步采用无气泡 Assistant/南枫 AI 主内容列、右对齐暖橙浅色且有限宽 USER 气泡、居中日期/时间分隔，以及只附属到正确消息的思考时长、消息时间、真实 model metadata、来源和动作；未知信息不猜，也不得用当前模型倒填历史。仅在离开底部时显示 Composer 上方圆形“到最新消息”按钮，点击平滑到底并恢复输入，到底自动隐藏；固定 Composer 不得遮挡内容。Desktop/Android 共享该消息语义，只调整内容列宽/密度；图标优先现有 Material/Lucide 等价资产。它是统一壳层验收前置，不是旧壳层上可补做的 P6-G UI。
- **意图优先前置（FB-P6-025，2026-08-14）**：P6-G 的 catalog、policy 与 owner 仍是领域真值，但不是根级导航的理由。Auto 必须是 normal conversation 的默认、可直接发送状态；Composer 选择器只是该对象的可选 manual override，选择 Auto 即回到 policy，TEMP 不进入 P6-G。Settings 才管理持久 catalog/global policy；route reason/rejected candidates 只在所属消息或明确设置上下文显示。四张参考图的空态、对话态、drawer、角色投影与轻量节奏须同时通过 Intent-first、Progressive Disclosure 与 Object-bound state 审查，详见 `CHAT_FIRST_INTENT_ORGANIZATION_CONTRACT.md`。
- 统一壳层完成后才恢复 P6-G UI 退出门：普通**非 TEMP**会话 Composer 显示当前 `Auto` 或实际手动选择；设置管理 owner-local catalog/preset/global default/policy；短列表为同一纯白选择面并可外点取消；安全 route reason/rejected candidates 只展示真实 owner metadata。TEMP 继续只保留 P6-E local marker，绝不进入 P6-G provider selection。
- 之后的双端真实本地验收仍须从新壳层 UI 改 Auto/全局默认/会话 override，并完整退出或 force-stop 后读回；Auto 实际候选以 snapshot 显示，unknown cost 明确要求确认且不写作零。Android 签名/install 与 Desktop `.app` 另立证据；真实 catalog、Key、HTTP、Provider result、OPPO、图标、账号同步和发布仍为范围外门。

## FB-P6-023/024 实施检查点（2026-08-14，未完成）

- Desktop 已将 transcript 纠正为 Assistant 开放主内容列、USER 单独右对齐暖橙 max-width bubble；日期、消息事实 metadata 和动作仍归属消息本身。回底按钮只在离开最新消息时显示，点击平滑回底并保留 Composer 焦点。
- Android 已移除根级 NavigationRail/NavigationBar，启动进入 Conversation workspace；drawer 底部新增真实“设置”入口，工作模式才显示 Project/Knowledge/Memory 等二级集。普通会话 Composer 和“设置→模型”读取同一 `P6GModelSelectionOwner` 的 catalog/global default/conversation override；TEMP 不显示 P6-G selector。
- 本检查点仅记录代码与定向自动验证，不构成双端真 UI、restart/readback、签名/install/base hash 或 P6-G 退出。未读 Key、未发 HTTP、未执行 Provider。
