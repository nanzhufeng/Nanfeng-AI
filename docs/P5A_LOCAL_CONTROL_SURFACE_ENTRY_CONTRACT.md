# P5-A 更多本地控制面入口合同

## 目标与停止边界

恢复紧凑 Android 和 Desktop 正常用户界面对既有 Project、Knowledge（含关系）与长期 Memory owner 的可达路径，为 P6 完整 owner 文件链重新建立前置条件。

- 本合同只新增可见、可返回的导航入口；不创建、预填、导入、删除或注入任何业务对象。
- 不读写凭据、不调用 Provider、不构造 Prompt/RunSpec、不发 HTTP 或其他外部访问；不触 Keychain、Room/SQLite 注入、启动 extra 或内部 deep link。
- 不增加聊天主页、Composer、会话详情或工作页常驻按键；不操作 OPPO、`5554`、`5556`、`5558`、`5570` 或既有模拟器数据。
- 当前增量不证明 P6、完整 owner 文件链、Windows、Provider、发布或总控方案 §17 P0–P11 已完成。

## 入口与唯一所有者

| 平台 | 正常用户路径 | 页面/导航所有者 | 到达后的业务 owner | 可逆性 |
| --- | --- | --- | --- | --- |
| Android | 设置 → 更多本地控制面 → 打开更多本地控制面 | `P5ANavigationViewModel` / `P5ARoute.CONTROL` | 既有 Project、Knowledge、Memory、Context、Eval 与 Adapter ViewModel/Domain/Room | CONTROL 明确返回设置；业务表单仍按既有取消/返回语义运行 |
| Desktop | 设置 → 更多本地控制面 → Projects / 知识与关系 / 长期 Memory | Desktop `state.pane` 的既有工作模式路由 | 既有 Rust SQLite workspace owner 与表单事务 | 从工作页回到对话/设置不写入数据；所有写入继续经既有显式表单 |

Android `ControlHub` 必须包含 Projects、知识、长期 Memory 三个用户路径；知识页内继续使用既有 Knowledge relation owner。Desktop 入口只跳转到已有 `show-projects`、`show-knowledge`、`show-memory` action，不能添加平行存储、Tauri command 或业务规则。

## 设置审阅与文案

- Android 与 Desktop 均在“设置 → 功能审阅”登记“更多本地控制面”。
- 当前状态固定为“待您判断保留或删减”；小字建议保留设置二级入口，不建议在聊天主页、Composer 或会话详情增加按键，理由是避免将本地管理误解为发送、联网或自动执行。
- 可见文案使用“本地控制面”“长期 Memory”“知识与关系”等中性范围说明；不暴露凭据、Provider 诊断、fixture 或工程控制。

## 布局、状态与验收

- Android 继续由 `P5AAdaptiveScaffold` 统一处理 edge-to-edge safe drawing Insets、紧凑/展开和大字体回退；新设置行与按钮使用既有 `Card`/`Button`、`P5AInteractiveShape` 与约 48dp 触控高度。
- 设置入口只切换 route；不保存第二份业务状态。CONTROL 的返回设置路径和系统返回均不能清空聊天草稿或改变业务 owner。
- 自动验证至少覆盖：Android source contract（入口、三条 owner 路径、无 Provider/凭据/外部访问文案）和 Desktop renderer contract（设置 registry、三条现有 action、功能审阅条目、无 Composer/发送控件）。
- 构建与新空 acceptance GUI 只可在未使用过的新环境中进行；GUI 链必须从可见设置入口创建最小非敏感对象，不得用内部路由或数据注入替代。

## 本轮证据（2026-08-20）

- Android 定向 `AndroidUserEntryAuditContractsTest`、`assembleDebug` 与正式签名 `assembleP6V2FullOwnerAcceptance` 通过；Desktop `npm run lint`、`chat-first-ui.test.mjs` 65/65、`npm run build` 与 `cargo check --manifest-path src-tauri/Cargo.toml` 通过。
- 全新 `NanfengAiLocalControlOwnerAcceptance` 仅以 `emulator-5582` 冷启动，首次安装正式签名 `com.nanzhufeng.ai.p6v2fullowneracceptance`。可见 UI 依次验证启动器 → 对话抽屉 → 设置 → 更多本地控制面 → CONTROL；设置页与 CONTROL 分别显示边界、Projects、知识和记忆路径。
- 同一空环境仅用既有表单/Android DocumentsUI 创建 `OwnerProject`、`KnowledgeOne`、`KnowledgeTwo`、一条人工 RELATED 关系、`OwnerMemory` 和一条含 `owner-attachment.json` 的已提交本地 Conversation 附件。未使用 Activity extra、deep link、Room/SQLite/DB 注入、Provider、HTTP、Keychain、OPPO 或既有模拟器。
- P6 系统导出、Desktop native Open/Save、strict readback 与 Windows 仍未开始；这些对象存在不等于完整 owner 文件链关闭。
