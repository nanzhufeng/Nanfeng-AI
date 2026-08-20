# 南枫 AI P7-A 本地跨端端到端加密快照基础合同

日期：2026-08-13  
状态：P7 的第一个独立增量；只建立无账号、无网络、跨端可互操作的密码学和数据协议，不代表 P7、同步或项目完成。

## 定位与唯一边界

`nfai.sync.v1` 是未来账号同步的加密快照协议。Android 的 `NfaiSyncV1Gateway` 与 Desktop Rust 的 `sync_v1` 是唯一 seal/open/preflight 实现；它们只接收调用方已显式构造的 allowlist 结构化记录，绝不读取账号、Room、Desktop SQLite、文件路径、Provider 或网络。

本机 Android Room 与 Desktop SQLite 仍各自是业务真值。此协议不是 P5-D 的 `.nfai-backup`（SQLite/受控资产/SAF 的本机恢复包），也不是 P6 的 `.nfai-exchange`（用户语义迁移包）；三者 format、导入语义、所有者、生命周期和文件扩展名均不得混用。P7-A 不创建账号或同步成功 UI，不接 Credential Manager、Supabase Auth/RLS/RPC/Edge Function、WorkManager 或 HTTP。

## 版本化数据模型

明文 payload 为 canonical UTF-8 JSON：`format: "nfai.sync.payload"`、`protocolVersion: 1`、`schemaVersion: 1`、`appId`、`documentId`、严格单调正整数 `revision` 和 `records`。record 仅允许 `kind/id/revision/classification/content`，kind 仅为 `project`、`conversation`、`knowledge`、`memory`、`relation`、`safe_settings`，classification 仅能为 `NORMAL` 或 `HIGH_SENSITIVE`；content 是结构化 JSON。调用方必须明确传入这份 allowlist 快照，不能直接序列化数据库、对象图或任意 map。

加密 envelope 为 canonical UTF-8 JSON：`format: "nfai.sync.envelope"`、`protocolVersion`、`schemaVersion`、`appId`、`documentId`、`revision`、`payloadHash`、`payloadByteCount`、`kdf`、`wrappedDataKey`、`payload`。云端可见信息只限这些协议/版本、app/document 标识、revision、hash/长度、KDF 参数、salt、nonce 和 ciphertext 封装元数据；所有业务内容只在 `payload.ciphertext` 中。

payload/envelope、错误消息、日志和测试输出一律拒绝或不包含 Key、Provider credential/ref、Token、Prompt、RunSpec、运行/诊断数据、URI/path、头像缓存、恢复码明文、设备私钥以及 `HIGH_SENSITIVE` 分类。Android/Rust 同一 classification 规则是主拒绝依据：任何 `HIGH_SENSITIVE` record 在 seal 与 open 都整体拒绝；递归禁止字段 detector 只是针对未正确分类的第二道失败关闭防线，不能替代分类。未知字段、重复字段、非安全整数、错误 base64url、空/截断输入、未知版本或任何长度超限必须失败关闭。

## 密钥、AAD 与生命周期

未来每账号应有一个随机 256-bit data key，**但 P7-A 尚无账号或本机密钥状态，绝不冒充已完成账号级生成、保存或轮换**。P7-A production `seal` 只接收调用方短生命周期持有的 32-byte data-key handle；P7-B 才能用平台 CSPRNG 生成每账号 data key 并放入 Keystore/桌面安全存储。P7-A 对每个 envelope 用平台 CSPRNG 产生 16-byte salt 和两个互不相同的 12-byte nonce。恢复码以 UTF-8 原文只在调用期间存在，使用 PBKDF2-HMAC-SHA-256、210,000 iterations 和随机 salt 派生 256-bit wrapping key；恢复码从不持久化、回显、记录或写入 fixture。

data key 以 AES-256-GCM 封装，payload 另以同一 data key、独立随机 nonce 的 AES-256-GCM 加密。两次 GCM 都使用同一 canonical AAD：`format/appId/documentId/protocolVersion/schemaVersion/revision/payloadHash`。因此跨 App、跨 document、协议/Schema 降级、header/hash 篡改和旧 revision 重放均认证失败或被 preflight/open 拒绝。调用方必须把可接受的最低 revision 传入 `open`；小于它的 envelope 以 `REVISION_ROLLBACK` 拒绝。

nonce 在相同 AES key 下绝不复用；生产随机材料与 deterministic known-answer fixture 是不同 API。实现仅使用 Android JCA `PBKDF2WithHmacSHA256`/`AES/GCM/NoPadding` 和 Rust 经审计的 `pbkdf2`/`aes-gcm`，不自造密码算法。临时 key、derived key、明文 payload 与解密结果以最短生命周期保存，`finally`/drop 后清零可变 byte array；不得假称托管语言可保证复制或 GC 内存完全擦除。

## Strict preflight、大小和原子性

envelope 最大 2 MiB，ciphertext 最大 1 MiB，payload plaintext 最大 1 MiB，record 最多 10,000，单个 content 深度最多 32；KDF 只接受固定 v1 算法、固定 iterations 与 16-byte salt，GCM nonce 固定 12 bytes，data-key ciphertext 固定 48 bytes。preflight 只验证 envelope 原始 JSON、精确字段、类型、版本、大小、base64、KDF、nonce、hash 和声明 app/document/revision，不解密也不修改状态。

`seal` 先完整构造与验证 plaintext、加密至内存、再一次性返回完整 envelope；取消/中断作为控制流原样传播，绝不返回半容器或标记为网络成功。`open` 先 strict preflight，再验证预期 app/document/最低 revision，解封 data key、认证解密 payload、验证 plaintext byteCount/hash、strict parse payload、逐项 allowlist/高敏检查；任一步失败不返回部分 records，不覆盖任何本机数据，也不触发恢复/同步。

## 互操作 fixture 与验收

`protocol/fixtures/nfai.sync.v1.golden.json` 固定 `NORMAL` 非敏感 payload、恢复码替代测试字符串、固定调用方 data key/salt/nonces，仅用于 known-answer；其 envelope 是确定性 fixture，绝不作为生产随机源。Node runner 校验 fixture canonical/hash；Android 和 Rust 都必须：

1. strict seal 使用相同 fixture material 生成与 golden 字节完全相同的 envelope；
2. strict open golden 恢复与 canonical payload 完全相同；
3. 将对方产生的同一 golden 作为输入完成 open，形成 Android→Desktop 与 Desktop→Android 的精确互操作证据；
4. 覆盖错误恢复码、header/ciphertext/tag/hash 篡改、未知/重复字段、截断、跨 app/document、revision rollback、大小超限与高敏拒绝。

P7-A 自动门包括 Android 定向/全量风险相称单测、lint/build，Rust fmt/test/clippy/check，Node fixture 检查。若 Android production source 改动，版本升至 code 41，并继续由仓库外现有正式证书签名；仅可在 `emulator-5554` 同签名覆盖和冷启动，绝不操作 OPPO。Desktop 仅本地无网络验证，不等同于 Developer ID/notarization。真实 Google/Supabase、账号/会话、本机密钥保存、冲突/自动同步/账号页和真实跨设备验收仍分别属于 P7-B、P7-C、P7-D、P7-E。

## 后续阶段

- **P7-B**：每账号 CSPRNG data-key 生成、平台本机 data-key 会话/Keystore 或桌面安全存储、恢复码展示确认和同步状态机；仍无真实服务。
- **P7-C**：经单独配置与安全审计后接入 Google/Supabase、RLS/RPC 和真实加密容器回读。
- **P7-D**：冲突方向选择、自动同步调度、账号页、头像安全缓存、切换和退出。
- **P7-E**：Android/Desktop 真实跨设备、真实账号/服务和人工验收；P7-A 的本地 fixture 不能代替这些外部门。
