# P10-B 双路径选择反馈合同

日期：2026-08-13  
状态：本地、瞬时的路径选择反馈；不含 Key 读取/写入、Provider HTTP、Prompt、RunSpec、费用、账号或跨网络同步

## 唯一目标与所有者

P10-A 已交付“当前配置状态是什么”；P10-B 只让用户在 Android 与 Desktop 的连接面明确得到两种选择反馈：

```text
本地继续 → LOCAL_OFFLINE / LOCAL_ONLY → 明确可继续本地工作
请求联网说明 → ONLINE_PROVIDER → 明确阻止原因与下一步
```

Android 的唯一所有者为 `ConnectionPathGuard` 的本地结果投影，经 `DualPathConnectionViewModel` 交给连接 Dialog。Desktop 只消费既有 `read_dual_path_status` 的 typed 字段，在前端本地生成同语义提示；不得新建 Tauri command、transport 或配置存储。

## 允许范围

- 本地选择只确认 `LOCAL_OFFLINE / LOCAL_ONLY` 仍可用；它不创建消息、任务、Invocation、Provider Attempt、同步任务或持久化记录。
- 联网按钮只显示当前 `degradedReasons`，并固定补充逐次 consent、未知费用与模型尚未就绪三个门槛；它不接收正文、附件、模型 ID、Key 或账号资料。
- 关闭、重开或进程重建后，选择反馈丢失；P10-A 的状态重新读取仍是唯一事实。
- Android Dialog 继续使用纯白 `#FFFFFFFF` 内容面、统一 16dp 圆角控件和既有安全滚动区。Desktop 保持现有 macOS 设置页的紧凑卡片与键盘可点击按钮。

## 明确禁止项

- 不读取或显示已有 Key；不写入 Key、Provider 配置、SQLite 或浏览器存储。
- 不构造 Authorization、Prompt、RunSpec、请求正文、Provider Attempt、费用、网络探测、HTTP、Google/Supabase/OAuth 或同步队列。
- 不把“本地继续”表示为 ONLINE 成功；不把按钮、fixture 或 loopback 表述为联网成功。
- 不改变 P2 `OpenRouterEgressPolicy.Disabled`、P7 同步所有者、Android release DI 或 Tauri capability。

## 入口矩阵与最小验证

| 入口 | 影响 | 唯一结果 | 最小验证 |
| --- | --- | --- | --- |
| Android Settings → 连接与数据路径 | 受影响 | 内存 `PathSelectionResult` | local ready、online blocked、关闭丢失 |
| Desktop 本地与联网页 | 受影响 | 前端本地状态提示 | 本地继续、联网原因、无 Tauri 写命令 |
| Capture / Conversation / Invocation / P7 sync | 不受影响 | 无新增调用 | 既有测试与静态搜索 |

自动合同必须证明：local 选择不是联网成功；联网结果始终保留 ONLINE 阻止原因；未知费用/模型和逐次 consent 不会被配置状态掩盖；关闭不保留选择。UI 可见验收仅证明本地反馈，不证明 Key、Provider、费用或同步成功。

## 停止条件

P10-B 到此只交付双路径的可操作、诚实反馈。任何真实配置保存、Key、目录刷新、费用估算、文本/图片外发、Google/Supabase/OAuth、账号、同步、OPPO、Windows 和发布均需要新的独立合同与授权。
