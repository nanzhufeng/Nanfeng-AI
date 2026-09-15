# Desktop 普通聊天最终对齐交接

日期：2026-09-01

## 本轮已实现

- 普通发送在 Rust SQLite 同一 transaction 内写入 USER、Assistant `PARTIAL` 占位和 Attempt；相同正文每次点击仍是独立 Attempt。
- 固定 Provider/端点/模型目录，发送后才从应用私有加密凭据 owner scoped 读取 API Key；Direct 不可用时 fail closed，Auto 对齐 Android 普通顺序，一次 Attempt 不换路由。
- OpenAI-compatible SSE 增量写回，停止时最多 100 ms 轮询取消并丢弃迟到增量；明确失败、结果未知、取消分开持久化。
- 显式重试保留原 Provider、原模型、原请求指纹和原幂等键，建立新 Assistant 兄弟分支；上下文已改变时拒绝伪重试。
- 进程中断后保留增量并恢复为 `UNKNOWN / PROCESS_INTERRUPTED`，不自动重发。
- 私有附件每次外发前重验 SHA-256；文本、受支持图片和 PDF 使用精确投影，其他类型在传输前阻止。
- 回复正文先提交，再追加 Provider-reported usage/cost 账本；账本失败只转为 `COMPLETED_ACCOUNTING_PENDING`，不回滚回复。reasoning 仅在完成后保留。
- UI 显示流式、停止、失败、未知、取消、重试、实际模型、安全错误码与 Provider 报告费用。

## 验证证据

- Node：116 passed，0 failed，0 skipped。
- Rust：132 passed，0 failed，0 ignored。
- `npm run lint`、`npm run typecheck`、`npm run build`、`cargo check`、本轮文件 `git diff --check` 通过。
- Release `.app` 已构建，开发签名后通过 `codesign --verify --deep --strict`。
- 未运行任何 `connected*AndroidTest`，未连接或操作 OPPO，未读取 Key，未调用真实 Provider。

## 未完成与安全事件

- 本地 Mock 传输和状态机已由 Rust 测试完整覆盖，但本轮不宣称 Tauri 人工窗口验收通过。
- Computer Use 因正式与验收 App 使用相同 Bundle ID，命中了既有正式数据窗口。已立即停止 GUI 操作，并终止本轮启动的验收进程与 Mock server。
- 只读核对确认：正式 SQLite 被新增 1 个会话 `conversation-chat-create-mthqap1s-77j3l2da`，标题／唯一消息均为“普通聊天本地 Mock 流式验收”，revision 1；没有 Attempt 表写入、没有 Key 读取、没有 Provider 请求。
- 不擅自删除该会话。如用户授权清理，应先再做精确 revision/引用预检，只删除该 stable ID，然后回读工作区语义 hash 和历史表。
- 后续原生 QA 必须先生成唯一 Bundle ID 和唯一显示名，并用 PID/命令行/环境变量确认命中临时根；在没有该隔离前禁止继续 Computer Use。
