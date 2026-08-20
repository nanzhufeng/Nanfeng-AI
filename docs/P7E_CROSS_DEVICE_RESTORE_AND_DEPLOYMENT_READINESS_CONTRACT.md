# 南枫 AI P7-E 跨设备加密恢复、冲突与部署验证就绪合同

日期：2026-08-13  
状态：进行中；本阶段先完成可重复的本地双端隔离闭环与不含秘密的部署验证器。真实 Google/Supabase、真机 OAuth、跨网络和发布仍是外部门，不因本地 fake 通过而完成。

## 目标与唯一范围

P7-E 的本地主体证明 Android 与 Desktop 能以 P7-A `nfai.sync.v1` envelope 在**显式测试隔离**中往返：设备 A 产生 allowlist 语义快照并 seal，`LOCAL_TEST_ONLY` fake cloud 按 P7-C RPC 的 read/commit/revision 规则保存 opaque envelope，设备 B 只恢复到全新隔离 workspace；B 修改、seal、以 expected revision 提交后，A open 后形成受控的原子恢复或停在冲突。它不实现 Google 登录、Supabase HTTP、Credential Manager、真实头像、网络、Provider、Prompt/RunSpec 或 P8。

生产 Android Room 与 Desktop SQLite 仍各自为业务真值。P7-E sync payload 与 P5-D `.nfai-backup`、P6 `nfai.exchange.v1` 的格式、writer、生命周期严格分离；不得复制 SQLite 包、直接写 Room、清库或把 fake 纳入 release DI/UI/网络 capability。

## P7-E 版本化安全快照与限制

- 唯一 payload 为 P7-A `nfai.sync.v1`，仅明确 allowlist：Project、Conversation tree、Knowledge、Memory、relation、safe settings。未知、遗漏无法完整映射的业务项必须在 snapshot planning 明确失败，不能静默丢弃。
- P7-E 共同业务映射固定为 `nfai.sync.semantic-record.v1`：每个 P7-A record 的 `content` 必须精确为 `format/semanticVersion/kind/id/revision/value`，其中 `value` 只含该稳定业务对象的结构化语义状态。它不是 `.nfai-backup`、`nfai.exchange` 或任意 Room/SQLite 行包；未知字段、持久化句柄/路径/asset、Provider/Prompt/RunSpec/运行诊断、token/credential 与高敏正文一律整体拒绝。Android 与 Desktop 必须以同一 fixture 和 strict parser 验证这层映射，且各平台 writer 只能消费已验证的语义 record。
- record 按 `kind/id/revision` 稳定排序并逐项 canonical hash；最多 10,000 records、plaintext/ciphertext 各最多 1 MiB。生成在 IO/worker 边界，取消/中断原样传播，不构造整库双份 JSON 字符串。
- `HIGH_SENSITIVE` 或 detector 命中时，planning/seal 作为整体失败：不得留下 envelope、fake-cloud document、job 或 receipt。危险/未知正文仅作为惰性文本 IR，绝不执行。
- fake-cloud 只存 opaque P7-A envelope、account/app/document scope、revision/hash/byte count 和 idempotency receipt；绝不解密、打印或持久化业务正文、密钥、恢复码、token、URL 或真实账户。实现、常量、测试名和产物必须带 `LOCAL_TEST_ONLY`，release production container 不得引用它。

## 恢复与冲突语义

### Android

Android P7-E restore 有独立 `restore plan` 与 allowlist writer，不复用 P5-D SQLite-file writer。production restore 只经 plan → preflight/open → checkpoint → staged allowlist write → transaction/filesystem atomic switch → full process restart/readback；UI 或 harness 不可直接写 Room 或清库。

空本机可恢复；非空替换先由用户明确确认、建立 P5-D 一致性 checkpoint，失败回滚，不 merge。恢复中断为 `INTERRUPTED`，不自动继续；没有完整 mapping、完整性错误、错误恢复码、篡改、旧 revision 或写入异常均保持旧本机真值。测试 adapter 仅在 app-private temporary database/workspace 使用同一 restore-plan/writer contract，不能成为 production shortcut。

### Desktop

Desktop 只能将 sync document open/restore 到全新、显式命名的隔离 workspace；默认不覆盖当前 workspace。替换现有 workspace 必须由未来明确 UI 确认并经 staging/SQLite transaction 原子切换，本阶段 harness 不触碰当前 workspace。stable ID、revision、conversation tree、relation、content hash 与不可信文本 IR 必须保真。

两端遵守 P7-C expected-revision RPC 语义：重复 commit/worker/intent 返回原 receipt 而不递增 revision；stale revision 拒绝；remote 更新同时 local dirty 时停写 `CONFLICT`；读回同 revision/hash 才可标记成功。账号切换、退出保留本机、两账户/两文档隔离均不允许跨 scope 写入。

## 最小本地矩阵

`LOCAL_TEST_ONLY` harness 必须覆盖 Android → Desktop → Android 与 Desktop → Android → Desktop，且至少包含：

1. 空设备恢复、本地非空+远端空的显式上传、双方非空方向选择；
2. remote update + local dirty 停写冲突、stale revision、重复 commit/worker/intent 与中断；
3. wrong recovery code、header/ciphertext/hash 篡改、高敏整体拒绝、cross-app/document 与两个账号/文档隔离；
4. Android/desktop 新隔离目标的 restore、B 修改后 A 回读、每一步 readback hash；
5. 账号切换与默认退出保留本机业务真值。

fixture 仅使用非敏感 synthetic records、temporary private roots 与随机测试 key/recovery code；日志只输出 case 名、scope hash 截短、revision、record count 和 SHA-256，不能输出 plaintext/envelope/key/recovery code。

## 部署预检与真实外部门

提供默认 `dry-run`/只读的预检脚本，只输出下列布尔状态：Supabase CLI、project link、private config、auth session 是否可验证。没有明确 target 与用户授权，脚本不得 deploy、link、login、SQL mutation、Edge Function publish 或 HTTP 写入。

得到明确 target/授权后，另执行部署前后只读验证：migration/schema、RLS force/default deny、grants、RPC signature、function/JWT policy、anon 拒绝，以及经用户授权的 envelope revision/hash 回读；任何脚本都不得读取/打印 ciphertext、账号、URL、key 或 token。Google/OAuth 真机 runbook 固定检查正式 package、公开 Release 签名 SHA-1、专属 Android/Web OAuth client、testing user、Supabase callback、恢复码、头像缓存、退出和账号切换；严禁复制其他项目配置。

## 验收与版本

- 自动：P7-E Android/domain/data 及 Desktop Rust/Node harness、P7-A golden、P7-C static policy、部署预检 dry-run；并分片列出全量 Android JVM 测试类以调查 JBR SIGSEGV，任何 SIGSEGV 均不记为测试通过。
- 构建：Android production 更改升 `versionCode 45` / `0.3.0-p7e`，Debug/Release 同现有正式证书 `889ecf3f…e99d5`；只在 `emulator-5554` 做同签名覆盖、冷启动和无配置安全页回归，不清数据、不装 test APK、不操作 OPPO。Desktop 完整门及 Tauri app 后 ad-hoc strict verify；Windows 独立。
- 真实退出门：明确 Supabase target + 授权 CLI/session + 私有配置 + 本应用 Google OAuth + 用户授权的真机测试，完成部署回读与 Android/Desktop 跨网络 encrypted envelope 回读。缺任一项时仅可报告“本地闭环完成 / 真实服务待验证”。
