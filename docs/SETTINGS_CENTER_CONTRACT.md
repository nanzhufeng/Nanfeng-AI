# 南枫 AI Settings Center 历史与领域边界合同

> **当前 Android 设置 UI 读取门（2026-08-26）：** 本文保留 Desktop Settings、Registry、导入路径及历史验收边界。Android 设置首页、二级至四级页面、文案、层级、皮肤、卡片、按钮、图标、弹窗和手势只读取 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)。本文及其关闭证据不得反向覆盖当前 Android UI。

日期：2026-08-14  
状态：FB-P6-026 CLOSED；P6-H Settings UI 退出门已由双端真实路径关闭。

## 定位与边界

Settings 是独立页面/路由，不是聊天画布上的弹窗、profile 浮层或抽屉内长列表。chat-first 左抽屉底部只保留单一“设置”入口；明确“返回应用/返回对话”路径始终可见。

三层不可混淆：

| 层 | 位置 | 当前允许内容 | 禁止内容 |
| --- | --- | --- | --- |
| 当前任务快捷控制 | 会话/Composer | 当前会话模型 override、临时对话等已有对象状态 | 全局默认、导入管理、复杂能力目录 |
| Settings Center | 独立页面 | 已存在的本地默认、数据、生命周期和状态入口 | 伪造 Provider、账号、同步、Agent、知识库管理或费用统计 |
| 专用管理中心 | 未来独立路由 | 知识库、工作流、Agent 等复杂管理 | 挤入 Settings 长列表 |

所有设置定义均具有 `id/path/title/description/type` 与显式 `scope`（account/device/workspace/conversation）、`syncPolicy`（sync/local-only/secret）及 `risk`（normal/sensitive/destructive）。本轮仅建立 Registry/路由骨架和已存在事实；不写入未授权的 account/sync/secret 或新的业务 owner。

## 平台布局

- **Desktop：** 全页 Settings Center。左侧分组导航、顶部搜索、右侧内容；普通内容列保持可读宽度。设置项为标题、简短说明、作用域/状态和右侧控件或箭头的单行结构；危险项独立危险区。ChatGPT 是“数据 → 导入、同步与存储/数据导入”中的一项，而非 profile menu action。
- **Android：** 独立全屏 Settings 首页，顶栏返回与标题，分组列表逐级进入子页。不得把 Desktop 左侧栏压缩为手机长列表。ChatGPT 同样位于“数据”子页。

## Desktop 聊天侧栏可调分隔线

Desktop chat shell 的侧栏和主对话/内容区之间必须有可拖拽 resize divider，不是固定宽度。它以 pointer drag 实时重排；键盘以 `role=separator`、`aria-orientation=vertical`、`aria-valuemin/max/now` 调节，双击恢复默认，并在 hover/focus/active 共享同轮廓反馈。宽度在 device/local-only 偏好中持久化，关闭重开读回，绝不同步账号。合理 min/max clamp；窄窗口自动回到既有 compact drawer，且不得影响 Composer、消息滚动或回到底动作。Android/手机不实现拖拽分栏。Settings Center 的 Desktop 分类栏可合理独立或共享宽度；本条的核心是主聊天 sidebar ↔ conversation divider。

## P6-H 唯一路径

`chat drawer 底部设置 → Settings Center → 数据 → 数据导入 → ChatGPT 对话 → 系统 picker → app-private copy → 候选/确认/跳过 → 原子 commit → restart/readback → export/hash`。

候选和逐项动作仍由既有 P6-H task owner 持久化；Settings 只承担入口、状态与返回路径。路径、URI、Key、token 和外部句柄不显示或持久化。

## 退出门

1. 两端 Settings Center 路由均真实可达，且 profile/drawer 不再承载导入 UI。
2. Desktop 具备分组侧栏、搜索、数据子页与明确返回；Android 具备全屏首页、逐级数据子页、顶栏返回。
3. Registry 回归覆盖 scope/sync/risk、ChatGPT path 和只暴露已存在设置；Desktop divider 覆盖 clamp、pointer/keyboard/double-click、本地重开读回与 compact 回退。
4. P6-H 原有的系统 picker、任务机、确认/跳过、关闭/force-stop 重开、provenance 与 export/hash 在新路径重新验收。
5. 不读 Key、不发 HTTP、不操作 OPPO、不改图标；本合同不授权下一 Adapter。

## 关闭证据（2026-08-14）

- Desktop 最新隔离 `.app`：drawer 唯一 Settings → 全页 Settings Center → 数据导入 → ChatGPT native picker → Alpha 确认/Skip 跳过 → 重开/readback/export hash；分组、搜索、返回路径与 Registry 回归均通过。
- 同一 Desktop app：divider 实际 pointer、ArrowRight、double-click reset、local-only 重开和 compact clamp 已验。
- Android acceptance：drawer 底部 Settings → 全屏首页 → 数据导入 → DocumentsUI → Alpha/Skip → force-stop/restart/readback/export hash 已验。未读 Key、未发 HTTP、未操作 OPPO。

## FB-P6-038 / FB-P6-048 Desktop 信息层级收口（CLOSED，2026-08-14）

- Desktop 右侧内容区不再保留“设置中心 / 隐私与数据管理”的常驻双层大标题；Settings 左侧分类栏顶部的“返回应用”是唯一退出入口。右侧仅投影当前分类的真实设置内容，compact 下入口仍保留。Android 保持自身全屏标题与返回语义。
- 页/分区标题下只重复当前含义的装饰性灰色说明（包括“本机数据与默认行为”）从 DOM/AX tree 和布局移除并回收空间；不删除风险、数据范围、操作后果、错误、保留期限或必要无障碍说明。
- 最新 signed Desktop `.app` 已实际 Settings → 返回应用 → chat selection 恢复，完整退出重开后 Settings 不幽灵恢复；该确认不扩大 P6-H 范围。
