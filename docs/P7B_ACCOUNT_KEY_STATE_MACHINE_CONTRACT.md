# 南枫 AI P7-B 账号级本机密钥与同步状态机合同

日期：2026-08-13  
状态：P7 的第二个独立增量；只建立本机安全基座，不连接 Google、Supabase、HTTP 或任何云端文档，不能声称登录或同步完成。

## 范围与认证边界

P7-B 唯一认证输入是未来认证层给出的 opaque `verifiedAccountId`（以及不持久化的 verified identity handle）。它不读取系统账号、不调用 Credential Manager，也不接受 email、ID token、access token 或可伪造的 UI 账号信息。`SIGNED_OUT` 状态不得创建账号 vault、Keystore/keychain entry、同步文档或调度任务。

本机业务数据库仍是业务真值。P7-B 不保存 cloud ciphertext/payload，不构造或上传 P7-A envelope；P5-D 备份与 P6 交换格式均不可替代 P7-B 状态。没有“同步成功”、云端 revision、头像、姓名或假登录界面。

## 密钥与恢复码

首次对已验证账号创建 vault 时，用 CSPRNG 生成唯一 32-byte data key，并立即使用每账号、不可导出的本机 wrapping key AES-GCM 封装到 app-private vault blob。Room/SQLite 只能保存 opaque account ID、key alias/ref、包装版本、状态/revision/hash/intent/receipt 和安全时间；禁止保存 data key、恢复码、Google/Supabase token、业务 payload/ciphertext、Prompt/RunSpec、Provider credential/ref、URI/path 或诊断。

Android wrapping key 由 Android Keystore 产生（AES-GCM、不可导出、随机 nonce）；key alias 只含 app 固定前缀与 account-id SHA-256 的截短安全引用。Desktop 依赖 `CredentialStore` 抽象：macOS 实现只能使用 app-owned keychain service/account，Windows 需独立原生凭据库证明后才能声明可用。P7-B self-test 只能写入随机临时 service/account，读回后删除并验证不存在；绝不枚举或读取既有凭据。

恢复码只在创建时由调用方获取并首次展示；确认前状态固定为 `AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION`，绝不调用 P7-A seal、创建未来调度或转换为 READY。P7-B 不保存恢复码明文、派生 key 或恢复码包装；真正的 recovery-code wrapping/key escrow 和跨设备恢复属于 P7-C 的服务合同。Keystore/keychain 缺失、失效、用户认证变化、包装 blob 不存在/篡改或解封失败，全部失败关闭为 `FAILED(KEY_MATERIAL_UNAVAILABLE)`，不生成替代 key、不覆盖本机数据。

P7-A data key 仅通过 `withUnsealedDataKey` 传入回调，回调结束的 `finally`/drop 立即清零可变数组；调用者不能把 key 放入 Room/SQLite、缓存、日志或异常文本。托管运行时无法保证所有复制完全擦除，故 API 只给最短生命周期并禁止返回 key。

## 显式状态机与方向守卫

状态仅为：`SIGNED_OUT`、`AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION`、`DIRECTION_REQUIRED`、`READY`、`SYNCING`、`CONFLICT`、`FAILED`、`SIGNED_OUT_KEEP_LOCAL`。P7-B 不调度同步；`SYNCING` 仅为后续 P7-C 预留的显式状态，不能由冷启动或 UI 假设进入。

- `authenticate(intentId, expectedRevision, verifiedAccountId)`：仅 verified input；同 intent 重放同 receipt，不重复生成 key。新 vault → `AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION`；已确认 vault → `DIRECTION_REQUIRED`。
- `confirmRecoverySaved` 只能从确认等待态进入 `DIRECTION_REQUIRED`；未确认时 seal/schedule/方向提交全部拒绝。
- `chooseDirection` 只在 `DIRECTION_REQUIRED` 且明确 local/remote fact 后执行：empty-local+remote 可标记未来 restore；nonempty-local+remote 必须显式停在冲突/方向选择；P7-B 不下载、不覆盖、不合并。
- `markConflict` 只能从 READY/SYNCING 停写到 `CONFLICT`；`beginFutureSync` 仅为状态合同，若本阶段调用始终拒绝并不产生网络副作用。
- `signOutKeepLocal` 仅停止未来调度并进入 `SIGNED_OUT_KEEP_LOCAL`，不清业务数据、不删除 vault；危险清本机数据只定义为未来 guard，不在 P7-B 实施。
- 冷启动只读取 metadata 并恢复相应 guard，绝不自动创建 vault、解封 key、确认恢复码或排队同步。

每个 state mutation 必须有 stable `intentId`、`expectedRevision`、事务和幂等 receipt。重复/中断 intent 不重复生成 data key、不跳过确认、不改写业务数据；revision 不符失败为 `REVISION_CONFLICT`。账号切换必须先执行保留本机数据的 signed-out guard，再以新的 verified account 重新走确认和方向选择。

## 持久化与迁移

Android Room 17→18 仅新增 `sync_account_metadata` 与 `sync_intents`：状态、revision、key alias/ref、wrapped blob ref/hash、direction fact、last safe error、intent/receipt；不重写旧业务表。Desktop SQLite `user_version` 3→4 仅新增同类 metadata/intent 表。两端皆不可保存秘密或业务正文，并以 schema migration 证明既有业务数据保持。

Android 最小 Settings 仅可显示“Google 账号与同步：尚未连接；本机安全基础已就绪”的未连接状态，不能显示伪账号、头像、云端 revision 或成功。若不增加 UI，Domain/Room/Rust 合同即为权威。所有 app-owned Dialog 内容面保持纯白。

## 验收与外部门

自动测试覆盖：未认证拒绝、同账号幂等、不同账号隔离、token/recovery/data-key 不落盘、Keystore/keychain alias、恢复码确认门、方向/冲突/切换/退出、冷启动不排队、重复/中断 intent、P7-A 短生命周期 callback 和 17→18/3→4 迁移。若 Android 生产代码变更，版本为 code 42 / `0.3.0-p7b`，Debug/Release 延用正式证书；仅 `emulator-5554` 同签名覆盖/冷启动，绝不操作 OPPO。

macOS Keychain 自检只能使用 app-owned 随机 temporary service/account，写→读→删→确认不存在；Windows Credential Manager 可用性、真实 Google/Supabase、RLS/RPC、cloud envelope、WorkManager、冲突合并、账号页、头像和跨设备恢复均为 P7-C 至 P7-E 的独立门。
