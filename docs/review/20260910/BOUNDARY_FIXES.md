# 四项边界问题修复

日期：2026-09-10。修复前 checkpoint：`4bf3e00`。用户明确要求按顺序修复；本轮从文档审查进入业务修复。原始发现和红灯保留在 [BOUNDARY_REVIEW.md](BOUNDARY_REVIEW.md)，不改写历史验证结果。

## 1. Desktop 中断恢复锁顺序

`desktop_storage_location::resolve_with_recovery` 先校验源目录并取得 `.runtime-owner.lock`，再调用既有恢复 owner、检查 staging、发布目标和保存配置。源锁不可得时不发布 staging；空 staging 也明确拒绝。原失败探针现通过，普通迁移持锁拒绝对照仍通过。

依据：[实现与行为测试](../../../desktop/src-tauri/src/desktop_storage_location.rs)。

## 2. 已配置目录缺少数据库

首次未配置的启动允许初始化；已配置 active 必须含有效数据库，不能因文件丢失静默创建空工作区。合法备份切换中断可能暂时缺库，因此先在锁内调用原备份 checkpoint 回滚，再校验数据库；恢复缺失或失败则停止。

新增测试覆盖：已选目录数据库删除但附件保留、已配置空目录拒绝、未配置首次启动、空 staging 不发布、恢复回调持有排他锁。另在 [desktop_local_backup_v1.rs](../../../desktop/src-tauri/src/desktop_local_backup_v1.rs) 用真实 checkpoint owner 构造中断，验证目录解析后数据库中原工作区标题仍为“备份前”，phase 为 `ROLLED_BACK_INTERRUPTED`。这是临时合成库测试，没有迁移真实用户目录。

启动接线见 [lib.rs](../../../desktop/src-tauri/src/lib.rs) 的 `desktop_storage_location::resolve_with_recovery` 调用。

## 3. 头像响应流式限额

[policy.mjs](../../../supabase/functions/google-avatar/policy.mjs) 的 `readBoundedImage` 在读正文前检查类型／声明长度，逐块累计，首个超限块立即取消。未知长度与虚报长度同样受限；空正文拒绝，合法 2 MiB 图片通过。[index.ts](../../../supabase/functions/google-avatar/index.ts) 使用此 owner，并释放重定向／错误响应 body。

[策略测试](../../../supabase/functions/google-avatar/policy.test.mjs) 覆盖超限、虚报、空正文、类型错误、读取中断、精确上限；[实际 handler 模拟流测试](../../../supabase/tests/google-avatar-handler.test.ts) 验证声明 8 MiB 时零次正文读取，无长度／虚报 1 byte 时读取三个 1 MiB 块后取消，1 byte／2 MiB 合法正文返回 200。首个超限块由传输层交付后才可检查；不能把接受上限写成整个进程或网络缓冲的硬内存上限。

复验命令（仓库根）：

```sh
node --test supabase/functions/google-avatar/policy.test.mjs supabase/tests/p7c_static_contract.test.mjs
deno run --no-config --no-check --no-remote --import-map=supabase/tests/google-avatar-import-map.json supabase/tests/google-avatar-handler.test.ts
```

Deno 没有网络权限；认证、环境与 fetch 使用合成夹具，没有启动服务器或部署函数。import-map 使用测试专属的合成认证 stub，不依赖线上 SDK 或真实账号。

## 4. Android 接收方授权冻结

- [NormalChatRoutingSnapshot](../../../app/src/main/java/com/nanzhufeng/ai/domain/NormalChatRoutingSnapshot.kt) 复用原能力／健康路由策略；配置与 credential presence 在后台读取成一份本地快照，不读取 secret 或请求目录服务。
- [ConversationWorkspace](../../../app/src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt) 显示完整 Provider／模型披露，点击使用同一渲染状态创建 v2 授权；不是在异步 ViewModel 中重新选择。模型入口发送时禁用。临时／工作界面不借此获取普通外发授权。
- [授权对象](../../../app/src/main/java/com/nanzhufeng/ai/domain/NormalChatEgressAuthorization.kt) 指纹绑定正文、附件、逻辑选择、Provider 与实际 API 模型 ID；[ViewModel](../../../app/src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt) 在保存前检查会话与草稿，拒绝过时点击。
- [后台服务](../../../app/src/main/java/com/nanzhufeng/ai/background/NormalChatBackgroundExecution.kt) 完整传递选择、预设和模型 ID。缺字段、旧授权版本、无效枚举等无法构造有效授权。
- [Executor](../../../app/src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt) 移除选择 owner 依赖，只消费批准路线；目录刷新前后都核对实际 Provider／模型 ID，变化则在材料桥、secret 读取与正文网络前阻止。Auto 不在排队后重新择优；网络失败也不替换接收方。
- [旧审计兼容](../../../app/src/main/java/com/nanzhufeng/ai/domain/NormalChatSendAttempt.kt) 允许读取 v1／v2 审计，但新授权对象只接受 v2。没有结构变化或 schema bump。

[七项领域行为测试](../../../app/src/test/java/com/nanzhufeng/ai/domain/NormalChatEgressAuthorizationTest.kt) 覆盖：排队后改选、Auto 与披露一致、序列化字段往返、Provider／模型 ID 变化拒绝、旧审计可读、可用性变化不改已批准路线、健康优先与显式选择。现有接线合同同步验证 Executor 不读实时选择、授权校验先于附件读取；这些测试不是 Android 原生服务时序或真实 Provider 验收。

## 验证与未验层

- Desktop 完整 Rust：216 通过，0 失败、0 ignored；包含目录 owner 与真实备份 checkpoint 协作测试。
- Android 完整 JVM：1109 tests，0 failures、0 errors、3 skipped；跳过的是真实 ZIP 输入 opt-in。Debug、Release 构建与 lintRelease 均通过；Lint 0 errors、101 warnings、19 hints，均为已有数量。正式 APK 非 Debug，证书 SHA-256 与原正式身份一致；本轮未安装。
- 头像策略与 Supabase 静态合同：7/7；Deno 实际 handler 的五个模拟场景通过。
- 未运行主设备、模拟器、原生窗口、真实 Provider／计费／Google、在线 Edge 部署或真实用户迁移。现有 13 个未跟踪截图／Playwright 配置保留。
- 代码、测试和本地构建层已修复四项；原生外观、真实系统调度与在线环境表现不能由本轮自动验证替代。

机器可读结果见 [boundary-fix-verification.json](boundary-fix-verification.json)。

首次 `packageRelease` 增量打包任务失败，诊断重跑及最终四任务确认均通过；未改签名或依赖，原失败根因未复现。该构建异常保留在验证 JSON，不能伪称已确定根因。
