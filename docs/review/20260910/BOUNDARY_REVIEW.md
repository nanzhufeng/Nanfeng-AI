# 2026-09-10 边界复核

基于业务 checkpoint `c4aad94`、文档基线 `ef76fc4`。仅增加审计文档与隔离探针，未修改业务实现。所有数据为临时合成 SQLite、附件或模拟 HTTP 响应；没有访问主设备、真实 Provider、用户数据、凭据或部署服务。

## 结果与证据范围

| 编号 | 结论 | 证据与限制 |
| --- | --- | --- |
| B1 | Desktop 中断恢复先发布暂存目录，后尝试获取源目录锁 | 生产 owner 隔离复现；不是实际用户数据损坏记录 |
| B2 | 已配置数据目录丢失数据库后仍被接受 | 生产 owner 隔离复现；后续创建空库是调用链推导，尚未原生启动验收 |
| B3 | 头像函数先读取完整响应，再执行 2 MiB 检查 | 实际 handler＋模拟 8 MiB 响应复现；不是网络流或峰值内存测量 |
| B4 | Android 发送授权未绑定披露的 Provider／model | 源码确认缺失绑定；未复现真实调度时序或错误接收方发送 |

### B1：恢复分支的锁顺序

[desktop_storage_location.rs](../../../desktop/src-tauri/src/desktop_storage_location.rs) 的 `resolve` 第 58–61 行，在目标不存在而 staging 存在时校验并 rename；第 63–64 行才获取旧目录排他锁。合成“发布前中断”现场并持有源锁，调用返回错误，但目标已发布、staging 已消失、旧数据库仍保留。普通迁移分支对照测试正确拒绝且未更改目标。

实测输出：`returned_error=true target_published=true staging_remaining=false source_retained=true`。建议后续修复把恢复动作纳入排他所有权范围，再验证中断与并发路径；本轮没有修改实现，也没有验证所有目标目录竞争。

### B2：已选目录缺库

同一 owner 第 80 行对已有配置仅验证目录属性；不会再次确认数据库存在和完整性。先选入有效合成目录，再仅删除该临时数据库，`resolve` 仍成功，附件保留：`accepted=true attachment_retained=true`。

[lib.rs](../../../desktop/src-tauri/src/lib.rs) 第 18567–18594 行将解析结果传入启动 store；`open_for_startup`（第 4762 行起）执行 migrate，`connection` 第 4991 行使用可创建文件的 `Connection::open`，migrate 第 6231 行起处理版本 0。这支持“可能创建空工作区”的判断，但未运行原生应用确认空白 UI。建议后续区分首次初始化与既有存储丢失，并在后者失败关闭、保留恢复入口。

### B3：头像读取上限晚于读取

[index.ts](../../../supabase/functions/google-avatar/index.ts) 第 25–26 行读取 Content-Length 后仍先 `await response.arrayBuffer()`，再调用 [policy.mjs](../../../supabase/functions/google-avatar/policy.mjs) 的上限检查。分别模拟带／不带 Content-Length 的 8 MiB 图片，两次均在读取 8,388,608 bytes 后返回 502；策略上限是 2,097,152 bytes。最终拒绝有效，但不构成读取阶段的资源上限。

探针替换认证客户端、环境值与 fetch，只导入实际 handler；没有授予 Deno 网络权限，也未建立服务器。建议后续提前拒绝已声明超限响应，并对未知长度流累计限额；仅检查头部不能解决无头或虚报长度。

### B4：Android 披露与执行之间缺少接收方快照

[NormalChatEgressAuthorization.kt](../../../app/src/main/java/com/nanzhufeng/ai/domain/NormalChatEgressAuthorization.kt) 第 9–32 行保存授权时间、披露版本及正文／附件指纹，没有 Provider／model。
[ConversationFoundationViewModel.kt](../../../app/src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt) 第 1881–1935 行先授权，再异步保存并启动执行；第 781 行模型选择没有 isSending 门禁。
[NormalChatBackgroundExecution.kt](../../../app/src/main/java/com/nanzhufeng/ai/background/NormalChatBackgroundExecution.kt) 第 50–118 行传递／恢复授权信息，没有接收方或选择修订快照。
[NormalChatOpenRouterExecutor.kt](../../../app/src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt) 第 265、291–293、453 行读取当前模型选择；授权匹配仅校验消息。
[ConversationWorkspace.kt](../../../app/src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt) 第 7938 行披露 modelLabel，但第 7944–7964 行模型入口未绑定发送禁用状态。

因此，点击披露与后续执行之间没有在上述链路冻结接收方。现有静态合同通过不能证明调度竞争不存在；本轮没有真实网络或 Android 时序测试，不宣称已经发送给错误模型。建议后续把披露的有效接收方及选择版本绑定本次授权，并在凭据／网络访问前校验。

## 可重复执行

从仓库根目录执行。Cargo 依赖需要已在本机缓存；所有构建产物写入临时目录。

```sh
probe_target=$(mktemp -d)
CARGO_TARGET_DIR="$probe_target" cargo test --offline --manifest-path docs/review/20260910/boundary-probes/storage/Cargo.toml -- --nocapture
deno run --no-config --no-check --no-remote --import-map=docs/review/20260910/boundary-probes/import-map.json docs/review/20260910/boundary-probes/avatar-probe.ts
```

[存储探针](boundary-probes/storage/src/lib.rs) 直接引用生产模块：原有 3 项＋普通锁对照 1 项通过，新增安全期望 2 项失败，退出码 101。这是确认缺陷的红灯，不能与既有完整 Rust 209 项通过混为一谈。[头像探针](boundary-probes/avatar-probe.ts) 在两种响应均复现“读取后拒绝”时退出 0；该退出码表示问题复现成功，不表示修复。相对路径版本已重新执行，得到相同结果。

本轮未修复上述问题。仍待验证：Android 调度竞争、Desktop 原生恢复交互／跨安装、迁移其他并发路径、头像真实流式资源占用，以及完整档案列出的真实服务与原生验收项。
