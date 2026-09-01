# Desktop Compare 专项验收矩阵（2026-09-01）

## 结论

真实本地执行链已完成；真实 OpenRouter 账号／Key／计费回读未做，原生 Stop 的按钮点击终态没有在 Computer Use 中闭环。后者已有原生 PARTIAL＋双 Stop 可见证据，并由 localhost transport cancellation 与 SQLite 状态测试覆盖，但仍须如实保留为 UI 自动化边界。

| 项目 | 状态 | 当前证据 | 边界 |
| --- | --- | --- | --- |
| 唯一入口 | 通过 | Composer 仅一个 `open-compare-confirmation`；无模型菜单 Compare、无长按 owner | Android 公开模型目录继续隐藏 Compare |
| 固定目标／无 fallback | 通过 | OpenRouter `GPT_5_6_TERRA` + `CLAUDE_SONNET_5`；缺设置／凭据失败关闭 | 未调用真实 OpenRouter |
| 一 USER＋两 sibling | 通过 | schema 30 transaction 与 Rust 专项测试 | 两分支仍在同一 message tree |
| 共享上下文／Memory／Knowledge／附件 | 通过 | Rust 测试核对同一上下文与真实文本附件投影；mock 日志核对分支请求 | 原生 Golden 附件只有元数据、缺私有字节时正确拒绝，未用它冒充附件成功 |
| 双成功／实际模型／费用 | 通过 | 原生 localhost：ChatGPT `$0.000012`、Claude `$0.000015`，各自显示实际 mock model | mock 费用不代表真实账单 |
| 单失败＋单分支重试 | 通过 | 原生 localhost：Claude `SERVICE_UNAVAILABLE`，明确重试后仅新增 Claude 成功 Attempt | 不自动重试 |
| 双失败 | 通过 | 原生 localhost 两分支均 `SERVICE_UNAVAILABLE` | 每支保留独立重试入口 |
| UNKNOWN | 通过 | 原生 localhost 两分支保留部分文本并显示 `MISSING_COMPLETION`／未自动重发 | 真实断网形态未单独调用 Provider |
| 流式 PARTIAL | 通过 | 唯一 Bundle ID 原生壳实时显示两条增量正文、两个“正在生成”和两个 Stop | 由只读 workspace 轮询刷新，不扩大 Tauri global API |
| Stop 整个 execution | 代码／集成通过；原生点击未闭环 | cancellation signal map 同会话持有两信号；transport cancel 与持久化测试覆盖；原生按钮已实看 | Computer Use 因动态重渲染导致重复 Stop 元素失效，未取得原生点击后的双 `CANCELLED` 回读 |
| 同文新 execution | 通过 | Rust 专项测试确认新 execution／USER／Attempt ID | 不做正文级去重 |
| 重启恢复 UNKNOWN | 通过（测试） | Rust 测试中断后两分支 `PROCESS_INTERRUPTED`，聚合 UNKNOWN | 本轮原生长流未在完成前稳定中断到可读证据 |
| 搜索／导出／备份／分支 | 通过既有 owner | Compare 消息进入同一 message tree，不新增正文表；旧 owner 回归全绿 | 未重新做真实用户大库性能验收 |
| Browser 宽／窄 | 通过 | 1440×900、780×700：各 1 个入口、窄屏无横向溢出、0 error／warn | Web 预览按合同不执行 native Compare |
| 原生隔离 | 通过 | 随机 Bundle ID、随机 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.*` 根、ad-hoc 验签 | 不读取正式 Desktop 根、Key 或用户附件 |

## 回归命令

- `npm run lint`
- `npm run typecheck`
- `npm test`
- `npm run protocol:test`
- `npm run build`
- `cargo check`
- `cargo test compare_ --lib -- --nocapture`
- `cargo test --lib`
- `npm run bundle:macos`
- `npm run prepare:desktop-compare-acceptance`

禁止运行任何 `connected*AndroidTest`；本专项未连接、安装或操作 OPPO。
