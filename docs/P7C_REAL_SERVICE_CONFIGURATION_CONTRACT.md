# 南枫 AI P7-C Google/Supabase 可部署服务合同

日期：2026-08-13  
状态：P7-C／P7-F 同步 RPC 已在目标生产项目部署并由 Desktop 登录态只读列表实际回读；Google OAuth 已建立应用账号会话。跨端密文恢复仍需要手机端已有已上传对话与用户原恢复码，是独立验收门。

## 目标与不变量

P7-C 让真实服务在技术上可部署、可审计，但不因为本地编译或静态验证而声称已登录、同步或跨设备恢复。Android Room、Desktop SQLite 仍是各自业务真值；云端只保存 P7-A `nfai.sync.v1` 加密 envelope 或与其字段严格一致的恢复码 wrapping metadata。

认证链固定为：Android Credential Manager 随机 nonce → Google ID Token 仅瞬时交给 Supabase Auth → Supabase `auth.uid()` 是唯一应用账号身份。ID/access/refresh token、Google Client Secret、`service_role`、恢复码明文、data key、Provider Key、业务正文、头像缓存不得写入 Room/SQLite、日志、诊断、fixture、migration 或云端表。

P7-B 仍负责本机 32-byte data key 与 Keystore/Keychain 封装；P7-A 仍是唯一 envelope/AAD/KDF/AES-GCM 格式。`nfai_account_keys.recovery_wrap_metadata` 只是 P7-A `kdf` + `wrappedDataKey` 的字段严格投影，配合记录的 P7-A AAD header（app/document/revision/hash）；它不是新密码格式，且只含密文封装。

## 可部署 Supabase 合同

迁移按顺序为 `supabase/migrations/202608130001_p7c_secure_sync.sql`（P7-C 基础表与四个受保护 RPC）及 `supabase/migrations/202609120002_p7f_list_sync_documents.sql`（P7-F 加密文档列表 RPC）。列表 RPC 不能脱离 P7-C 单独部署。

- `nfai_account_keys` 和 `nfai_sync_documents` 以 `user_id=auth.uid()` 隔离；两表启用并强制 RLS，撤销 anon/authenticated 的直接表权限，因此默认拒绝。
- 账号 key record 只能通过幂等 `nanfeng_sync_put_account_key` 首次写入；metadata hash 不同即拒绝，避免悄然替换恢复材料。
- 文档只能通过 `nanfeng_sync_read_document` 和 `nanfeng_sync_commit_document` 存取。提交用 user/app/document advisory transaction lock，再比较 `expectedRevision`，仅接受 `nextRevision` 的 P7-A v1 envelope，写后返回 revision/hash。过期、匿名、跨用户、越限、未知版本、字段错误和哈希错误均拒绝。
- P7-C 没有删除 RPC：尚无用户可解释的远端删除/墓碑/恢复合同，故不允许远端删除来绕过 revision 语义。P7-D 决定 UX 后再单独立约。
- 生产部署的就绪证明是五个 RPC 同时存在，并都只授予 `authenticated`：`read_account_key`、`put_account_key`、`read_document`、`commit_document`、`list_documents`。只看到 `list_documents` 不构成同步服务可用。
- 真实部署后的只读核验必须确认表、RLS、函数、grant 与匿名拒绝；不读取任何用户 ciphertext。

`google-avatar` Edge Function 只从已验证 JWT 的 Google identity metadata 取头像地址，且只接受 HTTPS `*.googleusercontent.com`。它不接受请求给出的 URL，不接受 userinfo/IP/root domain/非默认端口；每次 redirect 都重新验证，最多 3 跳，整体 8 秒、最多 2 MiB、只返 `image/*`，不写 Storage/数据库/缓存。函数仅是受限读取代理，不保存头像。

## 客户端与 Desktop 边界

Android 仅由 `local.properties` 或同名私有 Gradle 注入读取 `SUPABASE_URL`、`SUPABASE_PUBLISHABLE_KEY`（兼容旧名 `SUPABASE_ANON_KEY`）和 `GOOGLE_WEB_CLIENT_ID`。任一缺失/格式错误则 `P7CServiceConfiguration` 返回 `Disabled`，`P7CDisabledCloudGateway` 不发网络；应用保持离线正常，也没有伪登录/成功 UI。公开 build fields 不能包含 Client Secret 或 `service_role`。

`P7CSupabaseEnvelopeGateway` 仅解析 P7-A preflight 和 envelope receipt。未来认证 owner 才可注入短生命周期 authenticated RPC transport；gateway API 中不接受/保存 token，也不理解 Domain/Room/SQLite 业务对象。

Desktop 的 `p7c_remote_gateway_v1` 只定义同样的 private-config 可用性和 disabled typed gateway，没有 Tauri command、HTTP client、浏览器/OAuth callback、deep link 或远程 capability。macOS 后续需要 Keychain session、callback、logout、离线工作台独立性及真实配置的单独合同；Windows Credential Manager、WebView2/callback 与安装签名另列，不能沿用 macOS 假设。

## Google OAuth 配置 Runbook（不复制任何其他项目配置）

1. 从现有南枫 AI 正式 APK/正式 keystore 安全读取**公开** SHA-1，确认包名为 `com.nanzhufeng.ai`；不得在命令输出、文档或聊天记录 keystore 路径、口令或私钥。
2. 在本应用专属 Google Cloud 项目创建 Android OAuth Client，登记该 package 和该 SHA-1；不要复用任何其他南枫项目 client ID。
3. 创建/核对本应用专属 Web Client，并仅将 Web Client ID 作为 Android private injection；Client Secret 只留 Google/Supabase 服务端配置，不进客户端。
4. 在 Supabase 的 Google Provider 填本应用 Web Client 与 secret，登记 Supabase 提供的 callback；OAuth Testing 仅添加用户明确授权的测试用户。完成品牌、受众、发布/production verification 后才面对生产用户。
5. 有明确目标项目、CLI link 和授权会话时，先只读查 schema，再执行 migration/函数部署；随后只读核验 RLS/RPC/匿名拒绝与无用户密文的 metadata/hash。真实 Google 登录、头像、恢复、跨设备提交/回读仍需要真机与用户授权。

## P7-C 本轮安全审计与验证

本机审计只输出布尔结果：Supabase CLI 缺失、项目 link 缺失、私有客户端配置缺失、授权会话不可验证。因此没有远端读取或写入、没有部署/Edge Function publish、没有 OAuth 调用、没有账号或密文访问。

本地门已通过：Node SQL/头像策略共 5 项、Deno 头像策略 2 项与 `deno check`；Android 全量 `testDebugUnitTest`、`lintDebug`、Debug/Release 构建；Rust fmt/clippy/check 与 15 项测试、Desktop 前端 typecheck/lint/4 项测试/build。Android code 43 / `0.3.0-p7c` Debug/Release 均为既定正式证书 SHA-256 且 v2/v3 校验通过；仅在 `emulator-5554` 同签名覆盖 Debug APK、`ceDataInode` 不变、launcher cold start 后 `stopped=false`。未安装测试 APK，未操作 OPPO。Desktop `.app` build 后已重作 ad-hoc 签名并 `codesign --verify --deep --strict` 通过；无 TeamIdentifier，未 Developer ID/notarized/发布。

## 退出与后续

P7-C 的工件完成不等于真实服务通过。真实退出条件是明确 Supabase target + 已授权 CLI + 私有配置 + Google OAuth 配置齐全后，部署并回读 schema/RLS/RPC/匿名拒绝，继而由真实账号在真机做受控 envelope 提交/回读 hash。P7-D 才能做冲突、自动同步、账号页、头像缓存、切换和退出 UI；P7-E 才能做真实 Android/Desktop 跨设备验收。

## 头像读取上限修复（2026-09-10）

`google-avatar/policy.mjs::readBoundedImage` 在读正文前检查 MIME 与声明长度，声明超限立即取消；未知／虚报长度逐块累计，首个超限块立即取消，不拼接超限响应。只接受非空且不超过 2 MiB 的图片响应。这里限制的是应用接收并保留的正文，不声称进程峰值内存或底层网络缓冲只有 2 MiB。重定向与非成功响应也释放 body。实际 handler 的模拟流测试见[修复记录](review/20260910/BOUNDARY_FIXES.md)，不代表线上函数已部署。
