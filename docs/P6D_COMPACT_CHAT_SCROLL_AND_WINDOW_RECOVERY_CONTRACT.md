# P6-D 紧凑态消息长列表滚动与窗口恢复合同

## 唯一目标

在既有 Desktop chat-first shell 中，确认并补足紧凑窗口的长消息列表滚动与“窄窗口抽屉 → 展开窗口 → 再次紧凑”恢复行为。此合同只定义前端瞬时展示状态；Conversation 和消息真值继续由现有 Rust `DesktopWorkspaceStore` 拥有。

## 入口矩阵

| 入口/消费者 | 影响 | 唯一所有者/入口 | 最小验证 |
| --- | --- | --- | --- |
| 已选对话的消息长列表 | 受影响 | `.chat-scroll` | 独立垂直滚动，输入区不随消息滚动 |
| 工作模式进入/工作区切换 | 受影响 | 当前 workspace 的 Conversation projection | 默认回到该 scope 最近会话；无会话显示同一输入区的新对话空态，不出现 work-home |
| 紧凑侧栏 drawer | 受影响 | chat shell `sidebarOpen` | `<= 900px` 可打开；恢复到展开宽度时关闭 |
| 展开态主画布 | 受影响 | 共享 chat-first shell | 恢复宽度后仍显示同一会话、同一输入区，不保留隐藏 drawer |
| Rust SQLite Conversation | 不受影响 | `mutate_desktop_domain` / read projection | 本阶段不写入、不迁移、不增加 command |
| Provider、Key、同步、Android、Windows | 不受影响 | 不存在于本合同 | 无读取、HTTP、配置或产物变更 |

## 规则与停止条件

- `.chat-scroll` 是已选会话唯一消息滚动所有者；侧栏历史与输入区各自独立，不以整页滚动替代。
- 对话与工作是 scope/左侧功能集的真实切换，不是“聊天页 / 工作首页”的切换；工作模式默认右侧也必须是当前工作区对话，项目、知识、记忆和受控记录只经明确点击进入二级页。
- shell 重绘前后可恢复当前会话已有滚动位置；该位置只在前端内存中保存，不写 SQLite、草稿、Keychain 或浏览器存储。
- 当窗口从 `<= 900px` 恢复为展开宽度时，打开的 drawer 必须关闭；再次缩窄不得因旧瞬时状态自动重新遮挡聊天画布。
- drawer 关闭控件只在 `< 900px` 且 drawer 已打开时出现在侧栏右上角，使用无可见“关闭”文字的 `×` 图标与 `aria-label="关闭导航"`；常规宽度不显示。该控件唯一效果是关闭 `sidebarOpen`。
- 不实现 macOS 窗口 bounds 持久化、真实 Provider/Key/HTTP、同步、跨端或任何领域写入。实际 `.app` 验证不得为此向用户 workspace 写入测试消息。

## 验收

1. Node 合同证明长消息仍由独立 `.chat-scroll` 容器承载，且 CSS 允许它在固定输入区上方滚动。
2. Node 合同证明重绘滚动位置恢复和“恢复到展开宽度即关闭 drawer”的边界存在。
3. 最新 macOS `.app` 以非写入的既有/隔离会话验证：滚到长列表中部、打开 drawer、恢复展开宽度、再次缩窄；同一会话与输入区仍可见，drawer 保持关闭。

## 本轮结果（2026-08-13）

- 通过：64 条 app-private 隔离合成会话经既有系统选择器与确认导入；没有向用户长期 workspace 写入，也不是数据库注入。可见中段为第 19–23 条，固定输入区持续可见。
- 通过：中段位置在工作模式切换、drawer 打开/关闭、窄→宽与再次缩窄后保持；展开时 drawer 自动收起。
- 通过：宽屏没有关闭控件；仅打开紧凑 drawer 时显示右上无文字 `×`（`aria-label="关闭导航"`），其行为只关闭 drawer。
- fixture 保留为明确命名、可删除的 app-private 验证工作区及其受控交换包，以供重现；不代表用户数据、Provider 输出或任何联网事实。

达到上述证据即停止。它只关闭本合同，不等于 P6、Desktop、P10-A 或总项目完成。
