# 南枫 AI 意图优先组织合同

日期：2026-08-14  
状态：FB-P6-025 已登记；与 FB-P6-023/024 共同实施、共同验收

> **Android UI 路由（2026-08-24）：** 本文继续定义 intent-first 信息架构；当前 Android 会话界面的颜色、尺寸、顶栏状态、抽屉行、Composer、模型面和手势以 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 为唯一正文。本文较早的 Android 视觉描述不再单独形成冲突规则。

## 产品定义

南枫 AI 不是把 Conversation、Provider、Model、Search、Files、Memory、Tools 与 Settings 并列陈列的 AI 工具箱；它是用户把事情交代进去后，依照意图、对象与已知上下文组织本地能力的入口。工程模块只保留其领域 owner，不能因此获得根级导航席位。

## 强制原则

1. **Intent-first：** 默认根界面只突出当前对话和固定 Composer；用户不必预先理解模块、模式、工作区、数据源或模型。
2. **Progressive Disclosure：** 附件、搜索、来源、预览、编辑、工具和安全 metadata 只在任务或对象需要时出现；不得同时变成首页 Tab、Dashboard、卡片或按钮墙。
3. **Object-bound state：** 模型 override、上下文、附件、project/workspace 关联和工具适用性绑定 conversation/project/file/task 等真实对象；不要求“工作区→项目→模式→数据源→模型”的串行预选。
4. **Every-entry burden of proof：** 新入口默认先判断能否由意图或当前对象承担。只有用户必须管理的持久策略、隐私、安全、数据生命周期、账号/费用与明确 override 才进入 Settings。
5. **Auto-by-default：** P6-G 默认 `Auto`，可直接发送；Composer 的模型控件仅是当前 normal conversation 的可选 override，选回 Auto 即恢复 policy。catalog 与全局 policy 在 Settings，TEMP 独立且不泄漏。
6. **安全不变：** 自动组织不是提前联网、Agent、Provider 或 tool execution 的授权。本地优先；所有 egress、账号、费用和外部副作用继续受独立合同与明确同意控制。

## 统一 IA 与对象入口

| 表面 | 用户目的 | 允许内容 | 明确禁止 |
| --- | --- | --- | --- |
| 对话根与 Composer | 交代或继续当前事情 | 对话、空态、当前对象的输入/附件/可选模型 override | 工程模块目录、Dashboard、模型/数据源前置步骤 |
| Drawer / Desktop 侧栏 | 延续已开始的任务 | search、pinned、recent；底部仅 Settings 入口 | Provider/Model/Files/Memory/Tools 的模块总目录或 Settings 内容 |
| 聊天 / 工作 | 切换当前对象范围与连续工作语境 | scope 对应的会话、按需对象页和返回同一对话 | 选择“能力模式”、第二套 workbench 或主页 |
| 消息及对象上下文 | 完成本轮任务 | 来源、附件、预览、编辑、消息动作、route metadata | 将所有可用能力常驻铺开 |
| Settings | 持久治理 | 独立 Settings Center 内的已存在模型 catalog/policy、隐私、安全、数据、生命周期、明确管理 override | 日常任务菜单堆放、profile 浮层长列表、无返回对话路径，或未授权 account/Provider/sync/费用能力 |

## 四图合并验收映射

| 权威参考结构 | 同时成立的产品原则 | 验收判定 |
| --- | --- | --- |
| 空态的大面积对话画布与唯一 Composer | Intent-first | 是交代任务的入口，不是空 Dashboard；没有工程模块卡片或预选流程。 |
| 对话态的附件、来源、逐消息 metadata/actions、回底按钮 | Progressive Disclosure / Object-bound state | 只随对应消息、附件或离底状态出现；不得常驻成为根级功能。 |
| Drawer 的 search/pinned/recent 与底部 Settings | 延续任务 / 持久治理 | Drawer 不是工程模块目录；Settings 不承担日常任务入口。 |
| USER 暖橙 bubble 与 Assistant 开放正文 | 角色区分 | 是消息语义投影，不是功能卡片化。 |
| 聊天 / 工作 | Object-bound state | 真实改变对象范围或连续工作语境，不迫使用户选择工具或能力模式。 |
| 极轻顶栏、线性图标、留白 | 降低认知负担 | 结构、节奏与可识别性服务任务进入，不能只作表面仿制。 |

## FB-P6-027..032 补充规则

- transcript 默认只投影正文；Desktop hover/focus 与 Android 长按才显示各自允许的消息动作和真实 metadata。
- 会话导航始终单行：标题左、绝对日期右；禁止相对日期与第二行日期。Desktop hover/focus 可用操作图标替代日期，Android 用长按菜单。
- 常用动作只能使用 `docs/ICONOGRAPHY_CONTRACT.md` 的官方图标映射；不改变 launcher/Dock 图标。
- 附件信息只在 Desktop 附件 hover/focus 的预览内部低遮挡 overlay，或 Android 附件 long-press sheet 中出现；整条消息 tools 永远是 bubble/open-body 外的另一作用域。document-like 为正方形，图片/视频保持 intrinsic aspect，音频为紧凑横卡。
- Desktop message tools 的 DOM、视觉与 Tab 顺序统一为复制→分享→分支→真实时间→实际模型，整行仅 hover/focus 可见；Android long-press 先给可执行动作，再给低优先详情。
- 非关键分隔线只提供若隐若现的层级，Desktop 主聊天 splitter 的视觉线与宽命中热区分离；拖拽/焦点时才增强。
| 独立 Settings Center、数据子页 | 持久治理 / Progressive Disclosure | Desktop 分组+搜索+内容列；Android 全屏首页→子页；导入不留在 profile 浮层。 |
| 消息下方轻量时间/真实模型与按需来源 | Progressive Disclosure / Object-bound state | 不常驻 LOCAL_RECORD、未知值、时区/ownership/inert 工程文本；底层 provenance 不删除。 |

视觉 QA 必须同时比较四图的结构/节奏与本合同的 Intent-first、Progressive Disclosure、Object-bound state；任何一项失败，`design-qa.md` 不得标为 passed。

## FB-P6-042 顶栏 Settings 唯一归属（CLOSED，2026-08-14）

- Desktop 移除 chat/work switch 旁的 `show-settings` DOM 入口；Settings 只由侧栏底部的既有 `show-settings` 进入 Settings Center。Android 顶栏语义继续只有 drawer、对话/工作及既有 Ghost 右侧主动作；Settings 只在 drawer 底部。
- 不移除临时聊天 Ghost，不新增 overflow，不改变对话/工作切换、Settings route、焦点顺序、drawer 或持久化 owner。宽窄布局都不得出现幽灵 Settings Tab/semantics。
- 验收须锁定 Desktop 顶栏 absence + sidebar presence、Android header absence + drawer presence，并以最新 Desktop ad-hoc app 完整退出重开和 AOSP signed Debug force-stop/restart 分别观察；AOSP 不替代 OPPO。
