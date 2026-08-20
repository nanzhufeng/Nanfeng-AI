# Desktop Compare 联网执行合同

状态：阶段 5 已建立未注册的固定 OpenAI-compatible adapter 与安全双分支 receipt/status（2026-08-20）；**未配置、未联网、不可执行**。

## 范围与唯一归属

- `desktop/src/desktop-compare-execution-owner.mjs` 的 `DesktopCompareExecutionOwner` 是 Desktop Compare readiness 与显式执行决定的唯一 state owner。它目前只接收 `hasText`、`attachmentCount` 与安全 readiness facts，不能接收、保存、输出或记录草稿正文、附件、响应或 API Key。
- 当前三个既有入口（模型菜单、Composer `对比`、模型长按）仍是唯一产品动作入口；不增加 Composer 常驻按键、不增加第二次产品确认面。
- 默认边界固定为 OpenRouter 的 OpenAI-compatible `POST /chat/completions`。该常量只是未来 adapter 的受审查固定目标，本阶段没有 HTTP client、Tauri command、网络连接或凭据读取。
- 阶段 5 的 `desktop_compare_execution_v1` 仅接收一次性 direct-click command、已核验的 ChatGPT/Claude provider model + price catalog 和短作用域文本。固定 endpoint 只经未注册的 `OpenAiCompatibleCompareHttpPort` 传递；production 没有 HTTP client、Tauri command 或 UI 组合。未知/空 provider model 或未知价格在构造 catalog 时失败关闭。
- 每支执行仅追加 `execution_id`、logical/provider model、success/failure category、HTTP status（如有）、耗时和记录时点；SQLite 表没有正文、Key、请求或响应列。临时 draft 与 JSON body 使用 `Zeroizing`，凭据仍只能经既有 scoped credential boundary 在同一调用栈传给 mock/future port，绝不进入 receipt。

## 失败关闭顺序

1. 空草稿：`EMPTY_DRAFT`；不触及任何配置或执行状态。
2. 含附件：`ATTACHMENTS_NOT_SUPPORTED`；Compare MVP 只接受 text-only。
3. 没有安全凭据存在性：`CREDENTIAL_NOT_CONFIGURED`。
4. 固定 ChatGPT + Claude preset 未经验证，或任一价格未知：`MODEL_OR_PRICE_UNVERIFIED`。
5. 任一未来 native transport / receipt / branch adapter 未组合：`EXECUTION_NOT_COMPOSED`。

任何失败都不得退化为 generic model textbox、自动外发、Auto 路由、重试、背景请求或内容日志。

## 后续阶段门

- 阶段 2 才可增加 macOS `Security.framework` 直接 API 的 app-owned credential adapter；严禁 `security -w`、命令行参数携带秘密、枚举、重置、真实读取或 self-test。测试只用 in-memory fake，真实凭据输入只能由用户在最终 Settings UI 动作完成。
- 阶段 3 已实现 Settings → AI 模型服务的固定逻辑 Compare preset（ChatGPT、Claude）与 `NOT_CHECKED` / `BLOCKED` safe projection。provider-facing model 与价格在目录核验前明确显示为未知并失败关闭；Key 不得进入 SQLite、备份、同步、日志、前端状态或调用记录。
- 阶段 4 已把既有显式 Compare 动作组合为一条 30 秒、一时点的 content-free direct-click command；command 只携带固定 provider、ChatGPT/Claude logical pair 与时效，不携带正文或 Key。无点击、过期、未来时间、未知模型/价格和未组合 transport 都失败关闭；当前没有 consumer，因此 `GRANTED` command 不会产生 transport。
- 阶段 5 已接入未注册的固定 endpoint transport seam、双支状态与内容安全 receipt；测试只用 in-memory credential 和 mock HTTP port。真实 HTTP、真实 Keychain read、最终 Settings 凭据输入与 UI 组合仍须另有用户对非敏感文本、凭据和当次执行的授权。

## 验收

- Node mock-only 单测覆盖默认关闭、空草稿/附件优先拒绝、未知模型/价格、未组合 transport 和 source 无 I/O/content/key surface。
- Rust `desktop_compare_credentials_v1` 已把 Compare 的固定 app-owned service/account 封装为 Security.framework 直接 API；作用域内仅有 presence、用户提供 secret 的未来保存和 one-shot scoped read，secret 使用 `Zeroizing` 临时副本，并在回调异常展开时仍析构清零。它没有 Tauri command、Settings 输入或生产组合。旧 P7 adapter 同步移除 `/usr/bin/security` 与真实 Keychain self-test，改用相同的直接 API。
- Rust mock-only 合同覆盖未知 model/price 的前置拒绝、固定 endpoint 的双支独立 status，以及 SQLite 只含七个安全元数据列；无真实 Keychain 或网络调用。
- 本阶段不读取或写入 macOS Keychain，不更新备份/同步，不安装任何 app，不访问 OPPO，不发 HTTP。SQLite 只在 Rust `:memory:` mock test 中验证 receipt schema；未打开或变更用户 app database。
