# Desktop 逐控件图标／卡片审计（2026-09-02）

状态：**当前有效；Android live source、三份 CURRENT 合同、Android／Desktop 独立截图、Desktop shared source 与隔离原生回读已对齐。**

## 裁决与证据规则

- Android 事实源固定为 `ConversationWorkspace.kt`、`NanfengAiApp.kt`、`GlmOcrWorkspace.kt`、`ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md`、`ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md`、`ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md`；旧截图和历史交接不反向定义当前行为。
- Desktop 只同步任务语义、图标含义、白卡／灰 canvas、整容器选中、五层文字和状态层级；保留常驻侧栏、大 Composer、双栏设置、搜索宽画布、转写列表／详情和键鼠密度。
- Desktop 图标全部来自 `desktop/src/icon-source.mjs` 的 vendored Lucide node；SVG 本身 `aria-hidden`，可见文字或父按钮的 `aria-label` 承担语义，避免读屏重复。未发现 emoji、文本符号或临时手绘 glyph 冒充操作图标。
- 三张基线联系表及一张改后联系表都只是**预览对比板，不是 App 同一画面**。只有先查看联系表后，才读取 Android／Desktop 设置各一张疑点原图。

## 页面与控件映射

| 页面／入口 | Android 当前图标／控件 | Desktop 当前图标／控件 | 表面、选择与文字层级 | Desktop 平台适配 | 证据与裁决 |
| --- | --- | --- | --- | --- | --- |
| 主界面／侧栏 | Menu、Search、Schedule、Description、Settings、文件斜笔；图标按钮有内容描述 | `menu/search/clock/fileText/settings/edit`；图标按钮有 `aria-label` | 侧栏是导航画布；搜索是灰胶囊，设置／新对话是独立前景控件；页面标题、会话标题、正文、辅助事实、微标签分层 | 常驻 256px 左栏与大 Composer 保留；紧凑时左栏转为可恢复抽屉 | Android `01-main-final.png`、`02-drawer.png`；Desktop `01-native-main.png` 等独立图；通过 |
| 模型选择 | 模型按钮进入 Auto／Daily／Deep 根层，整行选择，返回层级固定 | 共享模型按钮与 `p6g-model-popover`；`chevronLeft/check`；整行 `aria-selected` | 白色浮层；当前项整行浅主题色，不只给文字／图标上色；标题、模型名、Provider／联网事实分层 | 完整候选列表与宽屏锚定浮层保留 | Android `03-model-root.png`、`04-model-daily.png`；Desktop 原生模型根／候选截图；通过 |
| Composer `+` | PhotoCamera、Image、AttachFile、风格、Public；相机／图片／文件后进入风格与联网 | `plus/image/file/account/globe`；Desktop 不伪造相机 | 白色锚定浮层；风格当前项整行选中；联网为同轮廓 switch；正文／当前值／状态分层 | 保留大输入框、图片／文件 picker；删除无 Desktop owner 的相机 | Android `05-plus-final.png`；Desktop `composer-add`／五风格独立截图；通过 |
| 全屏搜索／预览 | Search、分类、排序、类型、文件／媒体图标及真实大小 | `search/sort/file/image/audio/play/download` 等共享图标 | 灰 canvas 上使用白卡、独立胶囊筛选与选中面；结果标题、摘要、大小／时间、操作分层 | 保留宽搜索画布和系统打开；不复制手机单栏宽度 | Android `06-search.png`、`12-search-file-result.png`、`13-search-file-preview.png`；Desktop 搜索／预览独立图；通过 |
| 提醒 | Schedule／Notifications；列表、表单、确认与失败状态 | `clock/bell`；独立提醒页和表单 | 列表卡、输入卡、状态卡均为白前景；灰 canvas 承载说明；选择和开关服从整轮廓 | 保留宽屏状态画布；系统通知投递／点击仍是外部门 | Android `07-scheduled-tasks.png`、`08-scheduled-task-form.png`；Desktop `08-native-reminder-empty.png` 等；本机 UI 通过 |
| 南枫转写 | Description、UploadFile；当前只创建图片／PDF → GLM-OCR | `fileText/file/image`；当前根只创建文档转写 | 空态与任务卡是白前景；标题、文件事实、Attempt／错误辅助事实分层 | 保留列表＋详情工作台；旧音视频仅历史兼容，不暴露新建 | Android `09-nanfeng-transcribe.png`；Desktop 转写空态／失败详情独立图；通过 |
| 设置四组 | 对话：PersonOutline／Hub／Notifications／conversation bubble；外观：Brightness6／Type／Palette；数据：AccountCircle／ImportExport／Storage／Info；工作区：FolderOpen／Tune | `account/hub/bell/conversation`；`appearance/type/palette`；`account/importExport/data/info`；`folder/tune` | 四张纯白组卡置于 `#ededed` canvas；`4px` canvas 色分隔；选中为整行浅主题色；一级标题、组标题、行标题、当前值、辅助文字五层 | 保留双栏设置；主入口和二级页同时可见，不复制手机单列几何 | 修前 Desktop 将外观／导入与导出／开发与诊断误投影为 palette／单向 import／gear；已按红测改正。改后浏览器和隔离原生独立截图通过 |
| 个性化／五风格 | 五项可见风格固定顺序；`default` 仅内部回退；每项灰卡，当前整卡浅主题色＋边界＋勾 | 同一五项／说明／稳定 ID；`check`；无 visible default | 五张独立浅灰圆角卡；选中整卡；标题、说明、辅助边界分层；弹窗内部滚动 | 保留固定宽 Desktop dialog，不拉成手机物理宽度 | Android／Desktop 五风格预览板与两张最新原生图；代码测试断言恰好五项；通过 |
| 模型与联网二级页 | Hub；实时网页搜索必须使用主题色 Public／地球；Model／Cost／Context／Diagnostics 各自语义 | `hub`；实时网页搜索改为 `globe`；子入口 `hub/data/info/settings` | 网页搜索是单独白卡，说明位于灰 canvas；调用记录是组合白卡；当前一级行整行选中 | 保留双栏与未配置状态；诊断启动只读无凭据投影 | 修前误用 magnifier；改后浏览器／原生 `模型与联网` 图可见地球，AX 回读入口和 switch；通过 |
| ZIP 导入／结果 | 设置 → 导入与导出；ChatGPT／Claude ZIP、结果、工作区交换分别分组 | 设置主入口使用 `importExport`；ZIP 行使用可见动作文字，结果卡使用真实 Provider／批次／回执 | 白色组合卡＋灰 canvas 分组；JSON、ZIP、工作区、备份不混成一个 owner；危险删除保持确认边界 | 使用 macOS picker；v1 工作区和 v2 私有归档入口分开 | Android 当前 ZIP 空态／结果证据；Desktop 真实 v1/v2 picker 与 P6-K identity 测试；通过，破坏性 UI 删除未点穿 |

## 本轮真实修复

1. `外观`：Desktop `palette` 改为与 Android `Brightness6` 同语义的 vendored Lucide `sun-moon`，主题色仍单独使用 palette。
2. `导入与导出`：单向文件导入改为双向 `arrow-right-left`，不再与导入动作本身混淆。
3. `开发与诊断`：通用 gear 改为 `sliders-horizontal`，与 Android `Tune` 一致。
4. `实时网页搜索`：模型页 magnifier 改为与 Android `Public`、Composer 联网入口一致的 globe。
5. `ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md` 删除过期的六风格／visible default 说明，统一为五项可见、`default` 内部回退。

以上图标都从成熟 Lucide 源节点 vendored，不改 launcher／Dock 图标，不新增主页或 Composer 常驻按键。

## 自动与渲染门禁

- 红测：新增 `desktop/tests/icon-control-card-audit.test.mjs`，修复前 3 项中 2 项失败；修复后专项 `3/3`。许可注释从旧 MIT 事实校正为 Lucide ISC 后，全量首次暴露一条硬编码 MIT 的过期测试断言（`150/151`），同步校正该事实后最终 Node `151/151`，0 failed、0 skipped。
- 结构门：lint、typecheck、protocol golden、static build、Rust `179/179`、`cargo check` 全部通过。
- 浏览器目标流：`主页 → 设置 → 四组入口 → 模型与联网 → 地球图标／未启用状态`；页面 URL／title 正确、DOM 非空、无 framework overlay、console error／warn 为 0。700×900 的侧栏展开可恢复，但应用内浏览器在重绘后的第二次坐标点击不可靠，因此不把该次点击写成紧凑设置通过；紧凑布局只沿用已有独立原生截图与自动合同。
- 隔离原生：Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.25946.mtj1d970`，根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.njB4u2`，诊断启动；`quick_check=ok`、schema 37、history `paused=1 / last_dispatched=NULL / updated=0`、background `0/0/NOT_CONFIGURED/0`、read marker／reminder plan／run 均 0。PID 25996 无子进程、无网络 socket，取证后只结束 25996；既有 PID 97950 始终保留。

## 截图与产物

- 证据目录：`/Users/nanzhufeng/.codex/visualizations/2026/09/01/01a05db4-50d5-7131-ae92-29041ef03ac9/icon-control-audit-20260902/`。
- Android、Desktop、五风格基线预览板 SHA-256：`490a0786…`、`a3f3fa79…`、`95940514…`。
- 改后四图预览板 `AFTER-PREVIEW-COMPARISON-BOARD-NOT-A-SINGLE-SCREEN.jpg` SHA-256：`394927a8fc6cbfce08644b59914d3662fa7a0d84ae0151141e54ace8e0ae7768`。其中浏览器设置／模型与联网、隔离原生设置／模型与联网四张 PNG 独立保留。
- 当前 release 主程序 `30,941,152` bytes，SHA-256 `427500aa45679918992a67ba26551e2308304c82f8fee3696e406bcc9b5c9891`；strict codesign 通过，仍为 ad-hoc、`TeamIdentifier=not set`。

## 边界

本轮没有读取正式 Desktop 数据根，没有调用 Provider／账号／云端／通知动作，没有操作 OPPO，没有运行任何 `connected*AndroidTest`。图标逐控件语义已闭环，但不把 Android 与 Desktop 的平台原生尺寸差异写成像素一致；真实 Provider、Team ID 通知点击、Developer ID／公证仍是独立外部门。
