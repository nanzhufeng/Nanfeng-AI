# P5-A Android 自适应布局与无障碍产品化基线合同

> **当前 Android 会话 UI 路由（2026-08-24）：** 本文保留历史自适应/无障碍基线和非会话页面边界。Android `ConversationWorkspace` 的导航、抽屉、顶栏、Composer、表面层级、文字和手势统一以 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 为准；本文早期 NavigationBar/NavigationRail 壳层不能反向覆盖当前会话 UI。

## 目标与边界

本合同只为既有 Android 本地优先产品建立可测试的紧凑/展开 UI 壳层、可访问性与窗口连续性基线。它不改变任何 Domain、Room Schema（保持 17）、Repository、Provider、RunSpec、egress、导入导出语义或 launcher icon。

- `OpenRouterEgressPolicy.Disabled` 保持；不读取、写入或测试 Key，不构造 Prompt/RunSpec，不发 Provider HTTP。
- 只允许 `emulator-5554` 的自动化与可见验收；不得操作 OPPO，不安装任何 test/helper APK 到 OPPO。
- 所有 app-owned light Dialog、DropdownMenu、Popup 与 picker selection 内容面为 `#FFFFFFFF`；灰色只可用于外部 scrim。
- 不做 P5-B（后台/电量）、P5-C（隐私/数据管理/诊断）、P5-D（备份恢复/发布）、P6–P8 或图标工作。

## 唯一所有者与不变量

| 事实 | 唯一所有者 | P5-A 规则 |
| --- | --- | --- |
| Capture 草稿/恢复 | `CaptureViewModel` | 布局与重建不得复制或清空草稿。 |
| 当前对话、分支与草稿 | `ConversationFoundationViewModel` / Room | 宽窄切换、旋转、Intent/Activity 重建仍指向同一对话事实。 |
| Project、Memory、Knowledge、Context、Eval、导入任务 | 各既有 ViewModel 与 Domain/Room | 壳层只提供到达入口，不能创建第二份筛选、选择或 Room 语义。 |
| 当前一级导航目标 | `P5ANavigationViewModel`（`SavedStateHandle`）+ Activity app-private route token | 使用稳定 route；Activity 重建、force-stop 冷启动后恢复，深链接/Intent 仅在明确 route 存在时覆盖；不保存任何业务事实。 |
| 瞬时 Dialog/选择面 | 既有 ViewModel / Compose state | 可按既有合同在关闭时丢弃；布局切换不得把它们变成持久业务状态。 |

## 视口合同

| 形态 | 条件 | 导航 | 内容 |
| --- | --- | --- | --- |
| Compact | 可用宽度 `< 840dp`，或字体缩放导致有效内容不足 | 底部 NavigationBar，纯白承载面、整块选中态 | 单主任务路径；工作区/列表/输入在安全区与 IME 之间滚动或避让。 |
| Expanded | 可用宽度 `>= 840dp` 且字体缩放 `< 1.5` | 左侧 NavigationRail，整块选中态 | 同一个 route 的主区 + 有界辅助控制区；页面不并列复制，不新增第二状态。 |
| Large font fallback | fontScale `>= 1.5` | 强制 Compact | 保留完整触控区和可滚动内容，避免双栏挤压或文本裁切。 |

一级 route 固定覆盖 Capture、Conversation、Knowledge、Projects、Memory、Context、Eval、Settings/Provider 与 Adapter 控制面。Capture 在主页面保留现有快速入口；其它 route 用同一既有 Dialog/控制面打开相同业务入口。Expanded 的辅助栏只能显示当前 route 的说明/高频入口，不能保存独立选择。

## Insets、输入与状态连续性

- Activity 采用 edge-to-edge，根壳统一消费 status/navigation/cutout/IME Insets；页面不得同时以固定设备尺寸、`imePadding` 与手动位移处理同一键盘。
- 关键输入/提交操作在 IME 出现时始终位于键盘上方；IME 关闭立即回到原位置，不留下背景空白。
- 旋转、window resize、multi-window、折叠展开、Activity recreate、force-stop 冷启动和明确 Intent route 恢复当前 route；Capture/Conversation 草稿仍由既有真实所有者恢复。
- 页面内容设最大可读宽度、内部滚动与安全底距，不以缩小字体或删除字段规避溢出。

## 可访问性合同

- 所有导航、图标按钮、输入、进度、错误与禁用态有可朗读 label/role/state；装饰图 `contentDescription = null`。
- 页面标题和工作区标题标记为 heading；错误、异步恢复与进度使用 live announcement，不只以颜色传达。
- 控件最小触控目标约 `48dp`；Tab/DPAD 焦点按视觉阅读顺序，所有主要动作可键盘触达。
- `1.0x`、`1.3x`、`2.0x` 字体下文字可滚动且不裁切；高字体比例回落到 Compact。
- 圆角、pill、circle 的 visible surface、shadow、pressed/ripple、hover、focus 与 selectable indication 使用同一个 Shape；不可在圆角表面外层放未裁切 `clickable`。
- 选择面、Dialog、Popup 的白色 surface 与外部 scrim 分层，且内容可滚动、焦点可离开。

## 验证合同

1. 纯逻辑测试：窗口分类、字体回退、route 保存/恢复、显式 Intent route 解析、不可识别 route 安全回退。
2. Compose/Robolectric 可行测试：导航 label/selected state、heading、触摸几何/形状 token、白色 selection surface、Compact/Expanded 的 route 一致性与草稿入口连续性。
3. 全量 unit、Lint、Debug/Release 正式签名构建；connected/instrumentation 只在 `emulator-5554` 且不触及 OPPO。
4. 可见模拟器验收：phone portrait/landscape、tablet/foldable-like 大窗口；`1.3x/2.0x`、IME 开关、旋转/resize、force-stop 恢复、同签名 `install -r` 与 `base.apk` hash 回读。

## 非完成声明

P5-A 只是 P5 的自适应/无障碍基线，不证明 P5、P4、真实 Provider、真实成本、OPPO、图标、Release 发布、备份恢复或真实服务出口已完成。
