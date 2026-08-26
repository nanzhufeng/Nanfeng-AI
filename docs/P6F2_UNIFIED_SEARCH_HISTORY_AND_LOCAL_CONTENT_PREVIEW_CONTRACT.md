# P6-F2 统一搜索、搜索历史与本地内容预览合同

状态：P6-F2-A Search Index Adapter、P6-F2-B Image Preview Adapter、P6-F2-C PDF Preview Adapter 与 P6-F2-D Video Preview Adapter 已完成（D 的证据：`P6F2D_VIDEO_PREVIEW_ADAPTER_EVIDENCE.md`）。P6-F2-E、P6-G 仍未实施，须独立启动。平台：Desktop + Android 同阶段。

> **当前 Android UI 路由（2026-08-26）：** 本文保留搜索索引、资料类型、安全预览与本地数据边界。Android 搜索首屏目录、月份分组、卡片视觉、状态栏避让、音频播放器、正文来源入口、主题／暗色皮肤和会话内 UI 均以 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 为唯一正文；本文历史浮层或媒体展示数值不得成为第二套 Android UI。

## 目标与参考边界

本合同把南枫知识库中已经证明有价值的“分类搜索、搜索历史、列表日期、对话附件原位呈现与统一预览”迁移为南枫 AI 的 chat-first 能力。源码参考为私有仓库 `nanzhufeng/NanfengKnowledgeBase-Windows` 的 `main@cc56a3d13375eeb9a7d4e772de1a59388a66e050`（2026-08-13），重点事实包括：

- `UnifiedHistoricalSearchScope` 的六类范围；
- `sourceSearchHistory` 的本地规范化、去重、最近优先和独立正文搜索历史；
- `UnifiedNoteListCard` 的右上日期、标题/真实元数据/hover action 空间分工；
- `attachmentPreviewKind`、`AttachmentPreview`、`AttachmentTimelineMediaCard` 的格式路由、图片缩略图、视频代表帧和受控本地预览；
- 搜索、附件、列表和真实桌面验证分层记录，而不是用截图或构建替代真实链路。

只继承这些体验关系和安全做法。明确不继承知识库的三栏工作台、主题/证据/判断业务、五皮肤、资料时间线大弹窗、Windows BAT、正式数据目录或固定 1702×1066 尺寸。南枫 AI 仍保持 ChatGPT/Claude/Codex 式浅层 chat-first 外壳和同一主对话面板。

## 信息架构：搜索不新增复杂工作台

- 对话与工作模式各自继续只有一个紧凑“搜索”入口；不得新增重复“搜索会话”标题、独立媒体中心或另一套左栏。
- 搜索框获得焦点时，只在触发器附近打开轻量历史浮层：顶部为范围，下面为最近搜索；点击空白、`Escape`、Android Back 必须关闭并恢复焦点。
- 提交搜索后，右侧共享主面板切换为“搜索结果”内容；选择结果直接打开所属对话并定位真实消息/附件。返回恢复搜索词、范围、结果滚动位置和原模式，不另起一套工作区。
- 当前模式是默认搜索边界：`对话`只查普通对话；`工作`只查当前工作区拥有的对话与显式项目/知识/记忆引用。跨范围搜索只作为浮层内明确次级选择，不在首屏常驻。
- `TEMPORARY_SESSION`、已清理对象、高敏正文、Key、路径、URI、隐藏 metadata、系统/工具内部过程绝不进入搜索索引或历史。

## 分类搜索与本地索引

可见范围固定为六类，使用紧凑小圆角按钮而非大卡：

1. `全部`：文字消息与安全附件 metadata；
2. `文字`：会话标题、用户/南枫 AI 可见正文；
3. `图片`：受控图片附件名、类型和所属消息；
4. `视频`：完成独立 Video Adapter 后才启用；未完成时不显示假入口；
5. `音频`：完成独立 Audio Adapter 后才启用；
6. `文件`：PDF、Markdown/TXT 和其他受控文件；结果以真实 subtype 标记 `PDF/文本/文件`。

实现所有者是跨端同语义 `LocalSearchIndexAdapter`，Desktop 使用 SQLite 本地索引，Android 使用 Room 本地索引；UI 不直接拼 SQL，也不把全库正文一次性载入内存。排序固定为：标题精确命中 > 当前消息正文命中 > 附件名命中 > 更新时间；同级以稳定 ID 收口。搜索结果必须保存 `conversationId/messageId/attachmentId/contentKind/timestamp/source/provenance` 和可安全显示的 snippet，不保存路径、URI、Key 或隐藏 Provider payload。

索引更新必须跟随唯一 Conversation/Message/Attachment owner 的提交、修订、归档、恢复和删除事务；崩溃后可重建且结果幂等。部分匹配只是搜索命中或 Context candidate，绝不叫 Prompt Cache 命中。

## 搜索历史

- 全局/列表搜索与单个 transcript 内正文查找使用两个独立本地偏好键，互不污染。
- 只在用户真正提交非空查询后记录；trim、连续空白折叠、大小写等价去重，最近优先，最多 10 条。
- 点击历史项只回填，不自动执行搜索；提供逐项删除与“清空”，不把清空当危险数据删除。
- 搜索历史只保存规范化查询词和最近使用顺序，不保存结果、正文、命中片段、账号、工作区内容、附件名或模型信息。
- 临时聊天不记历史；退出账号、切换本地 profile、备份/同步时按各自数据分类合同处理，不默认跨账号合并。

## 会话列表日期与紧凑操作

- 普通会话列表每项右侧显示小号日期元数据：今天为 `HH:mm`，昨天为`昨天`，本年度为`M月d日`，更早为`YYYY/M/D`；可访问名称和详情始终包含本地时区完整时间。
- 日期使用会话最后一条可见消息时间；无消息时用创建时间。不得用导入时间覆盖第三方对话原始时间，也不得用当前设置倒填历史。
- Desktop 未悬停时显示日期；hover/focus 时日期可让位给置顶/归档图标，但行宽高、标题截断与列表位置不变。Android 日期保持可读，操作统一经长按/更多面板，不把多个小图标塞进窄行。
- 置顶区、最近区、项目区共享同一日期格式；排序与日期显示使用同一时间 owner，不能一个按更新时间排序、另一个显示导入时间。

## 对话中的本地附件呈现

附件预览和 Provider 输入是两个完全独立的边界。能在本机显示，不代表可以上传、分享或加入 Prompt。

- `IMAGE`：消息内显示有界缩略图、尺寸和安全文件名；点击进入唯一图片预览器。原图按真实像素读取，默认完整适配，缩放/平移只改变视口，不改写原件。
- `PDF`：消息内显示首屏缩略图或稳定文档卡、页数/大小/安全文件名；点击进入软件内 PDF 阅读器并支持页码定位。PDF JavaScript、表单动作、外部资源和自动链接执行默认禁用；文本提取继续由 P4-N Adapter 单独负责，预览不等于导入知识。
- `VIDEO`：独立 Video Attachment Adapter 完成后，消息内显示本地代表帧、时长和明确播放标记；用户点击后才播放，禁止自动播放、自动上传和后台全库抽帧。最高层播放器复用唯一视口；关闭后回到原消息与滚动位置。
- `AUDIO`：独立 Audio Adapter 完成后只提供原位播放控件、时长和文件信息；默认不设第二层大预览。
- `TEXT`：Markdown/TXT/JSON/CSV 等由受控解码器读取，使用统一安全 Markdown/纯文本渲染；不得让 WebView 直接执行本地 HTML、脚本、工具指令或远端资源。
- `GENERIC FILE`：只显示 inert 文件卡、类型、大小和真实可用动作。不能软件内预览时才提供明确“使用系统打开”后备，不伪造封面、摘要或成功状态。

预览只从 app-private 受控附件 ID 解析真实文件；前端不得接收任意绝对路径。图片懒加载、音视频只预取 metadata、PDF 仅在接近视口时创建阅读实例；单项失败隔离且显示诚实占位，不阻塞整个 transcript。

## 阶段拆分：一个 Adapter 一个验收

固定顺序为：

1. `P6-F Core`：transcript、逐条模型/来源/时间 metadata、消息动作与日期分隔；
2. `P6-F2-A Search Index Adapter`：文字搜索、六类范围壳、搜索历史、结果定位、会话列表日期；
3. `P6-F2-B Image Preview Adapter`：已复用 P6-D2 private-copy，完成原位缩略图、唯一原图预览、失败隔离与双端真实 UI/restart readback；
4. `P6-F2-C PDF Preview Adapter`：已完成安全文档卡、软件内阅读、页码与重启定位；
5. `P6-F2-D Video Preview Adapter`：已完成 picker/private-copy/magic/size/duration/poster/显式播放/owner 位置恢复；
6. `P6-F2-E Audio & Generic File Adapter`：原位音频和其余安全文件回退；
7. `P6-G Model Selection / Auto Router`。

每项必须 Desktop/Android 同阶段交付并分别验收。前一 Adapter 未完成时，后一项不得用 fixture UI 冒充正式能力；但已有安全类型继续保持真实可读。

## 双端入口与验收门

| 能力 | Desktop | Android | 同义结果 |
| --- | --- | --- | --- |
| 搜索历史/范围 | 侧栏搜索触发的轻量浮层，键盘与外点关闭 | drawer/主栏搜索触发的 sheet/浮层，Back/外点关闭 | 最近词、六类范围、清空与回填一致 |
| 搜索结果 | 共享右侧主面板 | 共享主页面/导航目的地 | 打开同一对话与消息/附件锚点 |
| 会话日期 | 行右侧，hover 时给图标让位 | 行右侧小字，长按打开操作面 | 时间 owner 与格式语义一致 |
| 图片/PDF/视频 | 唯一可调预览器/播放器 | 全屏或系统惯例预览页 | 保持来路、滚动、焦点/Back |
| 文件后备 | 明确系统打开/Reveal Adapter | SAF/DocumentsProvider 打开 | 只对受控 ID，失败诚实可见 |

退出必须覆盖：中文/英文/Markdown/代码、空白与超长查询、10 条历史去重、索引重建、归档/恢复/删除、TEMP 零泄漏、导入原始时间、跨日期格式、长列表性能、外点关闭、Activity/窗口重建、图片/PDF/视频/音频/未知文件、损坏文件、超限文件和受控路径边界。

Desktop 需 Node/Rust、lint、SQLite migration/rebuild、最新 ad-hoc strict-signed `.app` 真实搜索/预览/restart；Android 需 Kotlin/Room、lint、既有正式签名 Debug/Release、`emulator-5554 install -r`/base hash、Activity/restart。OPPO Find N5 仍由 `ANDROID_TARGET_DEVICE_PROFILE.md` 的后续真机门单独验收。

## 禁止提前

- 不读 Key、不发 Provider HTTP、不把预览附件外发；
- 不新增媒体中心、知识库式三栏或大资料时间线作为默认入口；
- 不扫描任意本机目录、不执行附件内容、不把本地路径/URI写入索引或 UI；
- 不把搜索命中、相似文本、导入记录或媒体预览冒充 exact cache、模型生成或 Provider usage；
- 不以 Desktop 证据代替 Android，也不以模拟器代替 OPPO 真机结论。
