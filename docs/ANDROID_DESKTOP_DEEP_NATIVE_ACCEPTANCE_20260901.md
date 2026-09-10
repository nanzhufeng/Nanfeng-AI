# Android → Desktop 深层原生验收记录（2026-09-01）

状态：**隔离本机深层对齐与正式 schema 37 本地数据／UI 验收通过；外部真实服务／系统权限另行验收**

## 2026-09-02 正式数据根补验与停止裁决（优先于下方历史记录）

- 用户截图中的崩溃来自旧隔离 QA LaunchAgent：无 mock endpoint 时 plist 没有保留隔离 `app_root`，旧二进制回落到正式 schema 37 根后以 `desktop SQLite unavailable` 在 setup hook 中失败并 `SIGABRT`。LaunchAgent 现始终写入精确根，background cycle 只在 background mode 读取该值；新增回归已完成先红后绿。
- 正式 schema 37 根通过 `--diagnostic-ui-schema-acceptance` 打开，主界面、搜索、提醒、转写、设置与深层页面均可渲染；诊断 PID 无子进程、无 TCP、无新崩溃报告。Node `151/151`、Rust `180/180` 和完整 bundle 门禁通过，当前 executable SHA-256 为 `8683046f14df8924c90a76ee9a167564703906998a4c02b655e08bef85c408c9`。
- 正式诊断前 online backup 为 `/tmp/nanfeng-ai-production-schema37-evidence-20260902.GXoNMe/workspace.sqlite3`，`quick_check=ok`、schema 37、SHA-256 `7855a9dc9b099d4056d24e2c648001a23c825f759689665115de1e3e44a1324a`。本线程开始时正式库已经是 schema 37，因此不能声称本线程保存了 schema 36 迁移前快照。
- 用户确认事前 backup 后实际操作过 Desktop，并明确不需要恢复。20 条 `archive` intent 全部满足 revision CAS、只改变 `archived`／revision、内容哈希不变，且目标为连续行从下到上的前台整理顺序；当前代码和 session 日志均没有自动启动归档的反证，因此原阻断裁决撤销。
- 正式库健康检查为 `quick_check=ok`、`integrity_check=ok`、schema 37、外键检查无结果；普通聊天 Attempt、历史候选／checkpoint、提醒运行、转写任务均为 0。14 张安全截图与联系表位于 `/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a06014-85d8-7901-b462-a3ef1f15e8e7/desktop-formal-schema37-acceptance/`；正式根本地数据与 UI 证据成立，外部服务仍按分层边界未验。

## 2026-09-02 第二轮当前 Android 校正（优先于下方历史截图措辞）

- 当前 release 主程序为 `30,826,656` bytes，SHA-256 `bac2deb5e345285d3485a439b49795938783bebe77858cd0ce3c142a59ccd9aa`；lint、typecheck、protocol golden、Node `147/147`、static build、Rust `171/171`、`cargo check`、bundle 与 strict codesign 均通过，0 failed、0 skipped／ignored。
- 最终唯一副本为 `/tmp/nanfeng-ai-desktop-compare-bundle.TxB0Kb/南枫 AI Compare 验收.app`，Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.7776.mtiwriyu`，隔离根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.vAfRY7`。
- Computer Use 在真实 `tauri://localhost` 窗口复验：设置首页有当前 Android 的对话、外观、数据管理、工作区四组；个性化二级页、Composer 五风格／联网偏好弹窗、提醒与转写空态、搜索结果／空态、附件预览 fail-closed 均有当前证据。下方 2026-09-01 的 Qwen 音视频入口、SenseVoice 状态和旧设置裁决只证明历史版本，不能再定义当前 UI。
- 旧音视频任务及底层定义仍为历史数据兼容，不再提供新建入口；Compare 历史可读、运行中旧任务可停止，但 webview 不再有提交或重试权限。
- 隔离 SQLite `quick_check=ok`、schema 37、`tone_override=efficient`、`web_search_override=1`、提醒计划 0、转写任务 0；关闭并使用同一 Bundle／同一根重启后偏好仍可见。新 release 未在正式数据根复启，因为启动可能自动运行真实提醒／历史任务；在本轮禁止 Provider／账号的边界下不冒充正式根闭环。

## 1. 构建与隔离

- 源码 Release：`desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`
- 版本：`0.6.0-p6d-dev`；正式 Bundle ID：`com.nanzhufeng.ai.desktop`
- 主程序：`30,806,944` bytes；SHA-256 `0e1d6afada0b94f7a668e36d95224a7a8ddc49f7b0300695518d4dcf5df07983`
- 签名：严格 `codesign --verify --deep --strict` 通过；仍为 ad-hoc，`TeamIdentifier=not set`，不是 Developer ID／公证分发包。
- 最终原生 QA 副本：`/tmp/nanfeng-ai-final-acl-qa.66665M/南枫 AI Desktop Final ACL QA.app`
- 最终 QA Bundle ID：`com.nanzhufeng.ai.desktop.deepqa.finalacl20260901`
- 最终数据根：`/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.final-acl.CdkKBU`
- 五风格／失败持久化复验数据根：`/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.Z9F04w`
- 根内实际生成独立 `workspace.sqlite3`、agent ledger 与 owner lock；正式 Desktop 数据根未读取。

## 2. 自动门禁

- `npm run lint`：通过。
- `npm run typecheck`：通过。
- `npm test`：`145/145` 通过，0 failed、0 skipped。
- `cargo test`：`167/167` 通过，0 failed、0 ignored。
- 一次全量 Rust 运行中的 localhost OAuth 回调测试瞬时失败；该单测立即单独重跑通过，随后全量 `165/165` 再跑通过，未把瞬时结果隐藏为通过。
- `npm run build`、`npm run bundle:macos`、严格 codesign：通过。
- 全仓 `cargo fmt --check` 仍会命中大量无关既有 Rust 格式差异；`cargo clippy --all-targets -- -D warnings` 仍有 15 个既有严格告警。本轮没有为过门禁而批量改写用户工作树，也不把这两项冒充通过。

## 3. 原生 Computer Use 深层证据

- 设置：二／三级页、模型设置、Google 未配置态、本机数据、关于、开发与诊断、导入与工作区均实际打开；未填写凭据。
- 自定义指令：全窗口编辑，输入后取消立即回滚到进入前草稿；曾发现“取消仍显示编辑草稿”，修复后重建复验通过。
- Memory：空态、本机查询、询问／补充显式选择、刷新／删除／关闭确认入口均实际打开；查询明确显示未调用模型／网络。
- 会话生命周期：从设置进入归档列表，回读创建时间，打开无 Composer／无变更动作的只读会话，再精确返回归档列表；恢复后回到活动会话；回收站空态已检查。
- 搜索：全屏六分类、时间／大小／还原、MD/PDF/ZIP/DOCX/TXT/JSON/其他类型面、`golden` TXT 结果、历史、当前会话查找与附件定位均实际操作；系统外部打开未强行触发。
- 定时任务：手工草案、字段填写、确认创建、持久列表、暂停、恢复、删除确认与取消均通过。首轮编辑暴露 `update_desktop_reminder_plan` 未进入 Tauri ACL；补权限并重建后，在最终隔离实例完成“创建 → 编辑 → 改名 → 保存 → 列表回读 `最终 ACL 隔离复验计划`”。
- 南枫转写：图片／PDF 模式、原生文件选择器打开／取消、Qwen 音视频模式、SenseVoice“验证中／保留实验”状态均实际检查；未伪造任务或真实 Provider 结果。
- v2 导入：原生 macOS picker 导入成功并显示内容无关的 hash／asset receipt；v2 owner 按合同保持与 v1 当前工作区隔离，不虚构为当前 v1 会话。
- 五种风格：以 Android 当前截图为参考，Desktop 固定尺寸弹窗改为内部滚动；五项都是独立灰底圆角卡，选中项浅橙底、橙色边框／标题／勾号，说明文字保持正常字号。顶部与底部原生截图分别证明无裁字和第五项可达，最终双端合图为 `android-desktop-five-style-card-comparison-final-20260901.png`。
- 无服务商失败：在全新无附件会话提交纯文本。SQLite 回读 `desktop_ordinary_chat_attempts` 为 `FAILED / PROVIDER_NOT_ENABLED`，并确认同一会话 JSON 同时存在绑定的 User 与 Assistant 消息 ID；强停进程并使用同一隔离根重开后，原位 `回答未完成`、中文行动建议与 `重试原 Attempt` 仍可见。

## 4. 代码级闭环

- 设置拥有独立页面滚动快照、精确会话返回目的地和只读生命周期页。
- 已确认提醒拥有事务更新、expected revision 冲突拒绝、下次执行重算、运行历史保留及正式 ACL。
- GLM-OCR 拥有文档详情真实字段投影、安全迁移、32 MiB 响应上限、分段 Base64 JSON 请求、Attempt 前置持久化和读取／解析失败持久化。
- Memory 摘要与自定义指令均为真实全屏子页，不再是跳转壳或内联替代。
- P6-K ZIP 使用官方 Provider／会话／消息身份账本跨包去重，支持累计追加并在冲突时 fail-closed；用户删除墓碑阻止后续 ZIP 复活。文本预览可复制、保存经 hash 复核的完整副本，并在 Desktop 无 Web Share owner 时明确退化为剪贴板；关闭预览恢复精确搜索滚动偏移。

## 5. 未冒充完成的边界

- 未调用真实智谱／OpenRouter／DeepSeek／Qwen Provider，未产生真实 Token／账单／长文结果。
- 未登录 Google，未连接 Supabase，未执行真实跨设备同步。
- 未点击 macOS 通知授权；有 Team ID 的热／冷通知点按仍待外部验收。
- 未执行删除最终确认、全量本机清理等破坏性动作，只验收确认面与取消。
- 未操作 OPPO，未运行任何 `connected*AndroidTest`、Debug 或仪器测试。
