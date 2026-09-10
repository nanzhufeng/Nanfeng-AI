# C-16 主题／字体／弹层跨端合同

## 范围与唯一事实源

- 本增量只处理 C-16，不改写 C-01～C-15，不代替最终全量审计。
- Android 当前源码是唯一产品事实：`AppearanceSettings.kt` 定义系统／浅色／深色与小 `0.80`／标准 `1.00`／大 `1.24`；`NanfengAiApp.kt` 的 `applyAppearancePalette`、`LocalAppTextScale` 与当前弹层组件定义表面、字号与层级。Desktop 旧样式不能反推 Android。
- 当前 C-16 矩阵是：系统（分别解析浅／深）／显式浅色／显式深色 × 小／标准／大 × 模型根层／日常／深度／加号根层／基础风格和语气／搜索与历史／设置与主题选择层，共 `84` 个合同项。

## 共享 owner 与失败关闭

- `desktop-theme-owner.mjs` 是 Desktop 生产主题唯一投影入口：它从已有 `appearanceProjection` 解析模式、字体和主题色，然后一次写入页面、设置、抽屉、前景层、助手／系统消息、搜索、控件、正文、次要文字、占位、边界和分割语义 token。
- 模型、加号、风格、搜索和设置层只消费这些语义 token；搜索删除了脱离 App 显式设置的 `prefers-color-scheme` 分叉，因此浅色系统上显式深色不再错渲染为浅色搜索。
- Browser 只接受显式 `c16ThemePreview` 查询参数；native 不读取该查询参数，而由 Rust 在 `NANFENG_AI_DESKTOP_C16_VISUAL_ACCEPTANCE=1`、精确 `/tmp/nanfeng-ai-desktop-c16-visual-acceptance.*` 根、84 项之一的状态值和 `--diagnostic-ui-schema-acceptance` 同时成立时返回只读状态。任一条件缺失或与其他验收夹具并用均失败关闭。
- Browser 的 `system-light`／`system-dark` 必须把合同里的宿主明暗输入交给生产主题 owner，不能退回当前浏览器 `matchMedia`；native 只读命令有独立 Tauri ACL permission。两条验收入口均不调用 Provider、账号或网络。
- 静态构建必须携带共享 owner 和 fixture。原生首包曾因 `scripts/build.mjs` 漏复制两文件呈现空白页；补入独立红灯合同后修复，不允许 Browser 源码通过掩盖 bundle 缺文件。

## 红绿与验收

- C-16 最终证据门定义 `84` 个唯一逻辑 ID，并强制 Android／Browser／Tauri 各 `84` 槽，共 `252` 槽。每槽必须有 PNG 和 JSON；Android 另有 UIAutomator XML。审计回读请求模式、宿主明暗、解析模式、字号、层、源码指纹、PNG 哈希和 `pass=true`，缺槽、零字节、未知文件或指纹漂移均失败。
- Android 使用关网隔离 `NanzhufengFindN5Api35`、`emulator-5554`、`1140×2616`／442 dpi 与独立 `com.nanzhufeng.ai.searchattachmentacceptance` 包，四种外观解析 × 三档字体 × 七层实际 UI 共 `84/84`；每项同时保存原图、XML 与 JSON，语义门 `84/84`，未运行 instrumentation 或任何 `connected*AndroidTest`。
- Codex In-app Browser 实际 CSS viewport 为 `1280×720`、DPR 2；首次取证服务误以 `desktop/src` 为静态根，导致模型类画面左上应用图标真实破图。改为先构建并从 `desktop/dist` 提供页面后完整重采 `84/84`，每槽新增全部图片 `complete && naturalWidth > 0` 门；请求态、DOM 外观／字号、目标弹层、可见 token 和横向溢出门全部通过。48 个唯一像素哈希；其余重复只发生在解析结果等价的 system-light／light 或 system-dark／dark，不替代逻辑 ID。
- Tauri 使用唯一严格签名副本 `com.nanzhufeng.ai.desktop.c16acceptance.60115.mtle24j8`、`/tmp/nanfeng-ai-desktop-c16-visual-acceptance.ZVKBul` 与 1440×900 窗口逐项启动；`84/84` 原生截图和 AX token 门通过，56 个唯一像素哈希。首态曾暴露只读命令缺 ACL，补最小 permission/capability 后原态重试通过；既有首次工作区回读顺序也由回归锁定。
- Android UI 保存深色／大／橙色后 force-stop 冷启仍从设置页回读三值。Tauri 真实 UI 保存后隔离 SQLite 为 `dark / large / orange / revision 3`；退出并重启同一严格签名包后行值、revision 与时间戳不变。只读 C16 状态在 native settings 回读后覆盖视觉，因此 Tauri 重启持久化以 Rust-owned SQLite 和成功启动裁决，不用 fixture 像素冒充。
- 完整回归：Desktop Node `236/236`、Rust `199/199`、lint、typecheck、protocol golden、七主题 computed style、inventory（231 个可见 action、126 个 invoke、断链 0）、static build 与 `cargo check` 通过；Android JVM `1085` tests、0 failure／0 error／3 个真实 ZIP 路径 opt-in skipped，`:app:assembleSearchAttachmentAcceptance` 通过。

## 产物与边界

- 最终证据目录：`~/.codex/visualizations/2026/09/03/01a066a9-bc6d-7b31-857d-0c0fa2efd991/nanfeng-ai-c16-final-20260903/`。源码指纹 `1c2172f29971e7d1fcf4a59f23665e8f149d8805a325d1ca6c615deb798f023e`；manifest SHA-256 `b4d3d5a9d678f0cb44f5cf9d9fa12a40587da779a114f1171cfbc303099f0893`（Browser 正式构建重采后连续两次 audit 稳定一致）。Android／Browser／Tauri 总联系表 SHA-256 分别为 `570212e3cb9bc40c76c59b263ea0682028ed435434426591cfea207856a100d5`、`1c286b505400e0f4f5bc1fc95daf8d9012eb743480a27e661a72b4e309905a51`、`519148d40bd1d89aadea4799f93c15f878416791fee7e7f34d01036e13cddab0`。
- 最新开发 bundle 主程序 `31,061,408` bytes，SHA-256 `2611b4e4c8196b152f43dce95c94c8b32fc3f7a03cc728a93381a721f855d1a1`，ad-hoc strict codesign 通过；它不是 Developer ID／公证正式包。
- 本轮未连接或操作 OPPO，未读写正式 Desktop 数据根，未使用账号、Provider、Key、通知或真实服务；既有 dirty／untracked 保留。C-16 的本地三端 84 项视觉合同已闭环，但不扩大为外部服务、正式签名或真机结论。
