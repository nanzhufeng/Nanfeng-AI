# 南枫 AI 总控方案需求—证据完成审计（2026-08-16；2026-08-23 补充当前状态）

## 2026-08-23 去敏外部门 readiness 总表（当前排程唯一入口）

本表仅记录存在性与合同事实：没有读取 Key、凭据、应用私有数据或远端内容，也没有启动 AVD、调用 Provider/HTTP、触及 OPPO 或推送 Git。所有“不可用”均表示本轮**不能**启动对应真实副作用，不否定已存在的本地合同或历史局部证据。

| 阶段/外部门 | 所需外部条件 | 当前可用性 | 最小恢复动作 |
| --- | --- | --- | --- |
| P2/P3 真实 Provider/流 | 合法可用的应用内凭据、已验证目录/价格，以及用户点击发送授权本次非敏感输入 | **不可用**：仓库没有允许的 Provider 环境变量或用户级 Gradle 凭据 schema；应用私有存储未读 | 用户在 App 内完成合法配置后，点击发送即授权本次请求；另行用非敏感输入完成一次真实调用，绝不从环境变量或 Gradle 旁路 |
| P5 OPPO/正式交付 | OPPO 在线、安装前只读身份/数据指纹门与当次明确授权 | **当前正式覆盖已完成**：code 63→64→65 的同证书 `pm install -r --user 0` 返回成功，首次安装时间/CE/DE inode/非 Debug 均不变，设备 `base.apk` 精确匹配本地 code-65；标准 Launcher 冷启动返回 `Status: ok` 并保持 `NanfengAiActivity` 前台 | 停止 OPPO 操作，不重装、重启、改用其他启动方式或清理遗留临时 APK；真实 Provider 调用、可访问性/性能与正式发布门仍单独待验 |
| P5/P11 code-65 产物 | 本地正式 APK、结构验签记录及 P11 发布门 | **已安装且已获 Launcher/前台活动证据**：code-65 `888c8698853744c6752e0f3afea02733a3aeffef264eb90f88f44131ba963644` 的设备 `base.apk` 回读、v2/v3、release-v2 证书与一次标准 Launcher 成功均已记录；P11 发布/可重复性风险仍未关闭 | 后续源码修复必须重新冻结、构建、签名、回读安装；在此之前不得把 code-65 当作可发布终件 |
| P6 Android/Windows | 一台全新 Android 35 AVD 的可用 ADB 注册；Windows 本机验收已由用户明确豁免，不是完成门 | **本独立子链已验收**：`emulator-5610` 已完成 schema 38→39 覆盖升级，并经正常 Settings/导入中心/DocumentsUI 对 strict v2 文件显示非空本机中文拒绝，前后 owner 计数不变；Windows 历史债务保留但非阻塞 | 停止该 AVD 的重复选择/重装/升级；此局部证据不代表完整 P6、发布或 Windows 验收 |
| P7 Google/Supabase | 原为指定 Supabase target、CLI/link、私有客户端配置、受控授权会话及专属 Google OAuth 配置 | **用户明确豁免**：Google 登录、Supabase、云端加密同步与跨设备恢复不是总控完成门；远端条件当前仍未核验 | 保留本地实现和未验证记录为非阻塞债务；不得因豁免而配置、部署、调用远端或声称云同步完成 |
| P8 真实 Agent 工具 | 一个已批准的真实工具、精确目标、权限/风险/预算、成功失败取消幂等恢复合同 | **不可用**：仅有 LOCAL_TEST_ONLY 与只读账本，未有真实工具合同/目标 | 一工具一合同，先完成外部动作/回滚边界和用户确认，不以 fixture 升格 |
| P9 生态接入 | 已指定真实目标应用、稳定公开入口、最小只读授权与版本化 schema | **不可用**：现有参考快照不构成真实目标或稳定入口证据 | 用户提供目标应用身份/版本/公开入口及最小授权后，新建一目标一 Adapter 审计 |
| P10 AI Hub | 至少两个真实应用消费者及隔离、降级、回滚/运维恢复验证对象 | **不可用**：尚无两个真实消费者 | 待两个消费者已真实存在后才立约，不提前建设 Hub |

## 2026-08-23 补充当前状态（优先于以下历史审计）

- **当前规则校正：** 普通聊天与附件不设置逐次外发确认。用户选择附件、预览及草稿阶段只在本机处理；用户点击发送即授权把该准确已提交草稿中的仍存附件或必要解析结果发送给界面明确显示的当前 Provider/模型。切换 Provider 不会静默转发给另一接收方，附件不得进入日志、统计或无关第三方。任何后文“逐次确认”只属于历史 Gate 记录，不得作为当前实现或验收要求。
- **当前 code 65 纠正：** 本文件中 code 52/53/57 及 `c511`/`230cac` 的安装陈述均为历史样本，不能覆盖 `CURRENT_HANDOFF.md` 顶部的 code 65 当前设备事实。code 65 修复了 AppContainer 主线程 Room 写导致的启动崩溃；其正式 APK SHA-256 为 `888c8698853744c6752e0f3afea02733a3aeffef264eb90f88f44131ba963644`。当前源码后续修复尚未重新冻结成新正式产物。

- **P5 已获局部门证：** code-53 正式 APK `230cac…f183954c` 已完成 OPPO 的正式 v2/v3、保留数据覆盖与标准 Launcher 前台活动证据。另在全新 `emulator-5614`，从同正式证书的 code-52（detached `a4eebd1` 单次离线重建）首装后经正常 UI 创建非敏感草稿，仅一次 `install -r` 覆盖到当前 code-57；首次安装时间和 CE/DE inode 不变，force-stop 后标准 Launcher 冷启动成功、草稿仍可见。此为隔离旧版迁移/数据保留/启动证据，不等同于 OPPO、全设备无障碍/性能、发布或 P0–P11 完成。
- **P6 已扩展若干真实子链：** Android v2 已在新空隔离 AVD 经 DocumentsUI 获得 restore、同包 replay、篡改/超限拒绝和非空本机拒绝；修复后 restore→DocumentsUI export 的真文件回读，以及 Android DocumentsUI→隔离 Desktop native Open/Save/replay 均已有局部证据。附件提升中断后零发布、同包 DocumentsUI retry 也已验收。新的 Android 35 `emulator-5610` 先完成同 applicationId、同正式证书的 schema-38/code-51→schema-39/code-52 覆盖升级；普通 Launcher/UI 创建的 2 个 Conversation 和 2 个 Draft 在 startup audit 中按 `schema=39` 读回。随后仅使用唯一既有 strict v2 fixture（`2,346 B`、SHA-256 `d9df67ce64cc325ab35b9f4268c03ed2e956dbeef81e38bf058fb18c16959832`、ZIP 头匹配），经正常 Settings → 数据与导入 → 导入中心 → 完整工作区交换（v2）→ DocumentsUI Downloads 单次选择，显示“当前本机已有数据或待恢复记录，已拒绝覆盖。”；选择前后 audit 均为 `schema=39 project=0 conversation=2 draft=2 knowledge=0 memory=0 relation=0 attachment=0 v2receipt=0 v2provenance=0 v2settings=0`。因此这个非空本机 `LOCAL_TRUTH_PRESENT` 拒绝与无覆盖子链已关闭。P6 总门仍未退出。
- **P11 当前边界：** AAPT2 verification metadata 已无写入回读；当前冻结 code-57 的两次 release build 在 281 个 ZIP 条目和全部解压 payload 上完全一致，整体 SHA 的差异只保留为 APK Signing Block 签名随机性边界。当前 release 仍只获结构/供应链证据，尚无正式发布、回下载或 OPPO 当前版本安装授权；P11 是持续阶段。
- **总控边界不变：** P0–P11 不因任何上述局部证据完成；P2/P3/P4 的真实 Provider、成本/质量与更多真实输入门，P7 账号/同步，P8 真实工具，P9 真实生态目标及 P10 双消费者触发均保持原外部门。

## 2026-08-21 P3 真实执行去内容化复核：仍为 Provider/授权外部门

- **已确认：** §17 P3 的本地 Conversation、preflight、receipt、fail-closed transport 与 P2-M bridge 仍只形成受限合同。普通聊天 production egress owner 不存在；默认 transport 返回安全 `DISABLED_NO_NETWORK` 终态、不发事件、不制造回答。P2-M bridge 和未引用的 Direct composition 均在默认拒绝端口前停止，不是 Provider、Key 或 HTTP 已接通的证据。
- **未关闭且不伪造：** P3 原始退出仍须真实流、停止/失败/重试/换模型、部分计费、真实用量/成本可见追踪、长会话实测及 OpenRouter 充分性判断。它们需要用户明确的非敏感内容/逐次确认、可合法检查的凭据 presence、冻结目录/价格和一次受控真实服务 readback；任何结果都不得自动重试。本轮没有读凭据、Keychain、HTTP、nonce、Provider Attempt、Token、费用、设备或 OPPO，也没有用 mock、DB 注入或假 receipt 替代。
- **排程：** 详见 `P3_REAL_EXECUTION_GATE_AUDIT_20260821.md`。该检查不增加用户功能或功能审阅项，不关闭 P3/P0–P11。P11 的最小本机 Gradle SHA-256 verification metadata 已在无 Gradle 争用窗口生成并离线回读：`gradle/verification-metadata.xml` 含 516 个 component、923 个 artifact SHA-256，文件 SHA-256 `2bc1ea7266a3fbe6ab3adfd5690ea3812d344409793d958207f1bc23ae962add`；它不替代 dependency locking、P11 持续门或 P0–P11 总控退出。

## 2026-08-21 P11 供应链复核与 P6 原始出口校正

- **P6 原始出口校正：** §17 的原始 P6 退出证据是 Android 导出 → Desktop 导入 → 再导出精确保真，以及紧凑/展开、本地文件、更新与异常恢复的独立验证；它**不**把 Android v2 回导/archive/recovery 规定为 P6 必经门。最新 `CURRENT_HANDOFF.md` 顶部已经记录 macOS 上完整 owner 的 Android DocumentsUI → Desktop native Open/Save/re-export 严格子链关闭；Android v2 回导若未来实施只能是独立质量项，不能延后下一总控阶段或被写作 P6 总门。
- **P11 当前增量：** Desktop PDF 预览使用的 `lopdf` 已从 0.35.0 升至 RustSec `RUSTSEC-2026-0187` 修复线 0.42.0；`cargo check --locked` 与 Rust 97/97 回归通过。另已在 Android Studio JBR、离线 `releaseRuntimeClasspath` 下生成并二次回读 Gradle SHA-256 verification metadata。详见 `P11_SUPPLY_CHAIN_REVIEW_20260821.md`。两项均为 P11 持续责任，未关闭 P11 或 P0–P11 总控。

## 2026-08-21 P6 v2 完整 owner Desktop native Open/Save：macOS 独立真实文件子链已关闭

- **新增真实证据：** 全新项目专属 `/tmp` Desktop acceptance bundle/root 由正常设置页的 native Open picker 选择 Android DocumentsUI 真实 3,955 B `.zip`，strict preflight 后写入 private v2 owner；同一 committed record 再由 native Save picker 回导至空输出目录。两次 UI receipt 均为 semantic `fdf9f95ac840…d9ff9`、1 asset。Node strict verifier 在 Android 输入和 Desktop 回导均通过（3 entries / 1 asset）；semantic、8 项 owner-field hash（field-set digest `ead2f34e…4aea1c`）及 asset ledger 全量一致。独立 SQLite v1 三表为 `0/0/0`，v2 import/assets/provenance/journal/receipt 为 `1/1/8/1/1`。
- **兼容修复：** 实际 Android DocumentsUI 普通 `.zip` 曾被 picker 展示却被 Rust 文件名 gate 拒绝。现仅把显式选择的非嵌套 `<stem>.zip` 纳入 v2 输入候选；它仍在任何私有 archive/SQLite 写入前经过完整 strict package preflight，回导输出保持仅 `.nfai-exchange`。这是既有设置二级入口的修复，不增新入口；功能审阅无需新增条目。
- **未扩张结论：** 这只关闭 macOS 上完整 owner 的 Android DocumentsUI → Desktop private import → native re-export/readback 子链；Android v2 import/archive/recovery、Desktop 原生对象恢复、Windows WebView2/安装/签名/缩放/IME、发布、OPPO 及 P6/P0–P11 总门均未关闭。Windows 是外部本机门，不能在 macOS 伪造。

## 2026-08-20 P5-A 紧凑用户入口缺口：双端设置内本地控制面与新空 UI owner 创建已验证，P6 文件链待继续

- **已实现：** `P5A_LOCAL_CONTROL_SURFACE_ENTRY_CONTRACT.md` 将唯一普通入口固定为双端“设置 → 更多本地控制面”；Android 进入既有 `P5ARoute.CONTROL` 并包含 Projects、知识（含关系）与长期 Memory 路径，Desktop 只跳转已有 Projects、知识与关系、长期 Memory work-mode action。双端功能审阅登记“待您判断保留或删减”，建议仅保留设置二级入口、不在聊天主页/Composer/会话详情新增按键。
- **已验证与边界：** Android 定向合同/Debug/正式签名 acceptance build、Desktop lint/Node 65/65/static build/Rust check 均通过。全新 `emulator-5582` 首装 acceptance package 后，从启动器可见 UI 进入 CONTROL，并正常创建最小 Project、两条 Knowledge、RELATED、长期 Memory 和带 DocumentsUI 选择附件的已提交 Conversation；没有 Key/凭据、Provider/HTTP/外部访问、Keychain、DB 或内部导航，也没有操作 OPPO/既有 AVD。`context_gate.py` 已返回 HANDOFF，Android v2 DocumentsUI 导出、Desktop native Open/Save 与 strict readback 留给新线程；不得将当前入口/owner UI 证据写作 P6 完整 owner 文件链、Windows、发布、OPPO 或 §17 P0–P11 完成。

## 2026-08-20 P6 v2 Desktop 真实 native save picker：隔离实例与 Android→Desktop→回导子链已关闭

- **已确认：** 受控临时 bundle 副本使用唯一 `CFBundleIdentifier`、独立可执行路径与新建 acceptance app-data root；源 bundle hash 前后不变，源/副本均 deep/strict ad-hoc 验签。Computer Use 精确定位副本而非已有同 bundle-id 实例。空 root 的 v1/v2 计数均为零，空输出目录为零文件。
- **真实链路：** 用户追加授权只读 `emulator-5558` DocumentsUI 下载目录；实际 1,091 B `.nfai-exchange.zip` 的 device/local SHA-256 均为 `3601aeb…205c53`，不匹配的 2,340 B 自动合同样本被排除。该只读 fixture 由 Desktop 设置 native Open picker 选择并成功私有导入，随后由“回导已提交交换 1”的 native Save picker 写入空输出目录；UI 两次回执均为 semantic `d0c3df7b…6d986`、0 附件。
- **内容无关 readback：** 输出包为 1,023 B、SHA-256 `91fc…1062`；Node strict verifier 通过，semantic hash 与 Android 同为 `d0c3df7b5e9296b5adf4a975077c7d7796381342942c3167cd2acc70c056d986`、entries=2、assets=0。SQLite receipt/provenance 的 project/settings owner-field hash 分别为 `f46d2915…e1f6` / `778838e3…9b22`；v1 三表仍 `0/0/0`，v2 import/journal/receipt/provenance/assets 为 `1/1/1/2/0`。Rust re-export 在 UI 成功前严格重验 canonical package、semantic、全部 field hash 与附件账本。
- **仍未确认且不得推断：** 这只关闭 P6 v2 的最小、非敏感实际文件子链。Android v2 import/archive/recovery、跨端完整对象恢复、Windows、正式发布/Developer ID/notarization、OPPO 与 P6/P0–P11 的其他退出门仍未关闭；macOS 在成功证据取得后锁屏，没有进行自动解锁或额外 UI 操作。

## 2026-08-20 P6 v2 Android DocumentsUI ZIP → Desktop native picker：真实导入/replay 子链已关闭

- **已确认：** 当前严格验签 Desktop bundle 在全新独立 `/tmp` 根，通过设置原生 picker 选择 5558 的实际 `.nfai-exchange.zip` 后成功给出 content-free committed receipt；同一文件第二次选择显示 replay receipt。package/semantic hash 与 Android readback 一致，v2 import/journal/receipt 各 1 条、两项 owner provenance，v1 三表均为零；无 OPPO、5554、5556、Key、网络或正文读取。
- **修正：** 真链首次暴露 Tauri ACL 遗漏，安全拒绝未写入任何 v2/v1 行。补入只允许该单一 v2 command 的 capability 并让 UI显式展示真实拒绝后，重建 bundle 和重跑隔离链成功；没有放宽文件名、manifest、hash、version 或 owner 门禁。
- **后续事实校正：** 此处“没有用户设置入口/尚未以真实文件关闭”已被后续 macOS 隔离验收取代：设置页的 committed-record 回导已经 native Save、strict re-export/readback 得到 semantic、全部 field hash 与 asset ledger 一致。P6、跨端完整对象恢复、发布及 P0–P11仍未退出；Windows 已为用户豁免的非阻塞债务。

## 2026-08-20 P6 v2 DocumentsUI `.zip` 名称兼容：实现/合同已闭合，Desktop native picker 真实跨端链待继续

- **已确认：** Android 的 `application/zip` DocumentsUI 允许将建议 `.nfai-exchange` 显示名保存为 `.nfai-exchange.zip`。Desktop picker 已只为这一个兼容事实增加精确终止名接纳（两种允许形式）和 ZIP 展示过滤；Rust 在读 bytes 前拒绝追加扩展、嵌套协议/ZIP 后缀与路径欺骗，随后仍必须通过 v2 exact manifest、semantic、asset 和 owner-field hash strict preflight。v1 继续由 v2 version/preflight 拒绝，v1 owner/table 不被读取或写入。
- **合同/回执：** Android MIME/建议显示名、Desktop picker 名称规则和 content-free receipt 已纳入 v2 字段保真及 Desktop transaction 合同。名称或 MIME 从不成为 content identity，也不回传或持久化至 receipt；严格 hash 与 manifest 证明不因名称兼容而放宽。
- **仍未确认且不得推断：** 本增量尚未在真实 native picker 选择 Android 实际 `.nfai-exchange.zip`，未产生 Desktop committed receipt、reopen/replay/re-export 或 v1 三表现场 readback；更不关闭 P6、跨端、Windows、发布或 P0–P11。Mac 未解锁时禁止绕过或伪造验收。

## 2026-08-20 P6 v2 Android DocumentsUI 隔离验收：新 AVD 的真实导出子链已关闭

- **隔离与产物：** 因 5556 有既有另一产品前台且输入焦点不可靠，未再触碰 5556。只读 SDK/AVD 核对后新建独占 `NanfengAiP6V2DocumentsUiAcceptance` / `emulator-5558`，不克隆或修改任何既有 AVD。新包 `com.nanzhufeng.ai.p6v2safemptyacceptance` 在该 AVD 首装，显式关闭 P6E fixture；APK 与 installed base.apk SHA-256 一致。OPPO 与 5554 未写入。
- **真实链路：** 新包空数据中仅经本机 UI 创建最小非敏感 Project，随后正常 `设置 → 数据与导入 → 导入中心 → 完整工作区交换（v2） → DocumentsUI SAVE`。范围匿名聚合为对象 1/附件 0，回 App 显示严格 readback receipt；文件 package SHA-256、semantic hash、`project/*` 与 `settings/root` owner-field hash 已分别取证，Node verifier 与 Desktop Rust strict reader 对同一文件只读通过。
- **边界：** DocumentsUI 实际追加 `.zip` 后缀，而 Desktop native picker 目前只接纳 `.nfai-exchange`；strict reader 的通过不覆盖该 picker 文件名兼容缺口。这只关闭 Android v2 的新隔离 AVD 导出/SAF readback/跨端 strict-reader 子链；未验证 Android v2 import/archive/transaction/恢复、Desktop native picker、用户可见 Desktop import/reopen、Windows、发布或 OPPO。P6 与 P0–P11 的原有未退出结论不变。

## 2026-08-20 P6 工作区 v2 Android SAF 导出桥接：本地用户入口/合同已接入，真实文件链未关闭

- **已确认：** Android 已在设置 → 数据与导入提供已审阅的 v2 候选导出入口；范围选择只显示对象聚合计数，严格 mapper/writer 先在内存构成并 preflight package，再一次性写入系统 SAF URI，readback package hash 相等才返回 content-free receipt。失败请求删除未完成文档，且不把路径、名称、正文或附件 bytes 暴露给 UI。Android/Desktop 功能审阅同步保持“待您判断”，建议仅设置二级入口。
- **自动证据：** v2 mapper/writer/scope 与 Android 功能审阅定向 JVM 合同通过；Desktop lint/test 90/90 通过。
- **未确认且不得推断：** 未启动隔离 AVD、DocumentsUI 或真实 SAF provider，未生成用户 package、验证 SAF 删除保证、读回实际文件或交给 Desktop 导入；没有 Android v2 import/archive/transaction/恢复、跨端、Windows、发布或 OPPO 证据。

## 2026-08-20 P6 工作区 v2 Desktop native picker 隔离验收：bundle/root 已就绪，macOS 锁屏使真实链待继续

- **已确认：** picker bridge 已在 `75c28c1` 窄提交；本轮仅新增受限验收根门禁，确保 bundle 启动只能使用新建的项目专属 `/tmp/nanfeng-ai-p6-v2-picker-acceptance.*` 根，不会触碰用户 app-data 或历史 P6-E/P6-H 根。写前与 fixture 生成后均证明验收数据根为零项。定向 Rust 2/2、clippy、check 通过；实际 ad-hoc `.app` 离线重建并 `codesign --verify --deep --strict` 通过。
- **未确认且不得推断：** Computer Use 在第一次读取 GUI 时被 macOS 锁屏阻断，尚未打开任何设置/picker，未选择 fixture，未产生 receipt/workspace/SQLite 业务状态，未做 reopen/replay/re-export 或 v1 不变核对。故只记录 P6 的 bundle 与隔离准备，不关闭 native picker 文件子链，更不关闭 P6、跨端、Windows、发布或 P0–P11。

## 2026-08-20 P6 工作区 v2 Desktop native picker 桥接：本地合同闭合，真实用户文件链仍待隔离验收

- **代码与入口：** Desktop 已登记唯一 v2 command `import_desktop_workspace_exchange_v2_selected` 与 Settings → 数据与导入 → `完整工作区交换（v2）` picker。该桥接只读取一个用户所选 `.nfai-exchange`（regular-file、扩展名、128 MiB 上限）→ strict v2 preflight → 既有 private archive + schema 21 single transaction；没有 v1 staging、目录扫描或路径/正文/byte/显示名回传。失败没有可见 workspace，成功回传 content-free receipt。
- **治理与自动证据：** Android/Desktop 功能审阅均登记“待您判断”，建议只保留 Desktop 设置二级入口、不加聊天/Composer/工作页常驻按键。新增 bridge 合同覆盖 import、replay、receipt 脱敏与 v1 三表零行；Rust library 90/90（Keychain historical self-test 主动排除）、`cargo check`、Desktop UI 90/90、lint/typecheck/static build 与 Android 功能审阅定向单测通过。
- **仍未关闭：** 未在真实 native picker 选择实际 v2 fixture，未做真人文件的 committed reopen/re-export readback；没有 Android v2 用户链、跨端互通、Windows、正式 bundle/发布或任何 OPPO 操作。不能把本地 command/UI 合同写成真实文件、完整对象恢复、备份/同步或 P6/P0–P11 完成。

## 2026-08-20 P6 工作区 v2 Desktop 私有导入内核：生产数据链闭合，真实用户文件链未开始

- **结论：** Desktop 已实现独立的 v2 package reader/preflight、随机 private prepare→fsync/hash readback→archive rename、SQLite schema 20→21 的五张 v2 owner/journal/receipt 表、`BEGIN IMMEDIATE` 单 transaction、committed reopen/replay 与 committed-only re-export readback。它严格接纳 `manifest.json`、`exchange.json` 与引用账本对应的 `assets/<sha256>`；manifest/export canonical equality、IR semantic hash、attachment metadata/hash 与每个 root/asset 的 `ownerFieldHashes` 都必须一致。
- **失败与隔离：** 全部 v2 表在任一 import/asset/provenance/journal/receipt/before-commit failure injection 后为零行，archive 最多留下不可见无 journal 孤儿。v1 `workspaces/workspace_exchange/import_journal` 没有读写；production migration 回归确认 v2 import 后 v1 三表保持零行。已提交包只能完整 readback 后 replay；不允许部分表补齐或中途 resume。
- **自动证据：** v2 内核 4/4；已有 shared v2 IR 与 v1 boundary 各 1/1；Node v2 golden 与 `cargo clippy --lib --tests -- -D warnings` 通过；排除会访问 macOS Keychain 的历史自测后，Rust library 为 89/89。没有网络、Key、picker、UI、设备、数据库注入或真实用户文件验收。
- **仍未关闭：** 内核未注册 Tauri command 或 picker/UI，故完整工作区用户选择、真实 Desktop 文件链、Android↔Desktop v2 正常入口回读、紧凑/展开、Windows 与发布均未开始；设置 → 功能审阅也不得增加条目。下一唯一候选是独立接入并验收 Desktop native picker 的真实文件链，而不是扩大 UI。

## 2026-08-20 当前总控状态（优先于以下历史审计）

### P6 v2 Android owner mapper/package writer 增量（2026-08-20）

`NfaiExchangeV2OwnerMapper` 已形成未注册的 Android 只读 owner→exact-IR 合同：它保留 Project appearance/instruction history、Conversation settings/memory sources、Knowledge source/provenance/history/附件 metadata、Memory title/source/history 与 relationship scope/history，并拒绝 locator、`sourceReference`、私有路径、运行时节点、缺 owner history 与附件内容 hash 不符。其后的 `NfaiExchangeV2PackageWriter` 只读重验账本附件，内存序列化独立 v2 manifest/exchange/content-addressed assets，严格 preflight 后才交给原子有限 output port；失败不调用 port，receipt 无正文/路径/bytes。Android mapper/writer 3 项、shared IR 2 项、Node package reader 和 Desktop Rust strict reader 对同一非敏感临时 package 均通过；无 Room/SQLite 写入、SAF/UI、模拟器或 OPPO 操作。此项只关闭 Android 领域/序列化合同，**不**关闭 P6：Android SAF/UI/持久 archive/import、真实文件链、紧凑/展开与 Windows 门仍未开始，完整工作区入口继续禁止。

以下状态以 `docs/CURRENT_HANDOFF.md` 顶部的当前记录、当前工作树、当前 release APK 与连接设备读回为准。历史段落保留为当时事实，不再作为排程结论。

| 总控项 | 当前事实 | 状态 |
| --- | --- | --- |
| release v2 签名与正式 APK | 新的项目专属 v2 签名已生成；当前源码 release APK 为 `com.nanzhufeng.ai` `51 / 0.3.0-p10a`，SHA-256 `7406d1de5818e013227d7a1ffb4083043e0922f767d013317040bab5f2c41ea2`，v2/v3 签名校验通过。该产物包含功能审阅、Claude 兼容修正与当前版本备份/诊断/Eval 元数据修正，尚未安装到 OPPO。 | 已关闭 |
| OPPO 安装链 | OPPO PKH120 当前只读保留较早 release-v2 APK `fc8f9ac6…`；它不是当前源码 APK `7406d1…`，本轮未覆盖安装。 | 保留数据；当前源码的 OPPO 安装/启动未验证 |
| Desktop P6-K 正式 bundle | 资源封印缺失已修复；最终 ad-hoc bundle 严格验签、原生 WebView、Settings 匿名 aggregate readback 已完成 | 已关闭 |
| Android P6-K 真正入口 | 当前源码以独立 `com.nanzhufeng.ai.p6eacceptancev2` 验收包运行，未覆盖 legacy `com.nanzhufeng.ai` / `com.nanzhufeng.ai.p6eacceptance`。经设置 → 数据与导入 → 导入中心 → 系统 DocumentsUI，已导入两份已授权 ZIP；临时中性来源均在私有暂存后删除。force-stop/cold-start 后只读回匿名 aggregate：ChatGPT 23 对话 / 719 未关联媒体；Claude 162 对话 / 0 未关联媒体；Claude 另有 120 项严格失败，与 Desktop 同源聚合一致。 | 已关闭（隔离 emulator 验收；不等同于 OPPO 导入） |
| Desktop Compare 本地执行边界 | 阶段 1–5 已形成 fail-closed owner、Security.framework credential seam、Settings 安全投影、30 秒 content-free direct-click command，以及未注册的 OpenAI-compatible mock adapter/安全双支 receipt。阶段 5 定向 Rust 合同 5/5 与 `clippy -D warnings` 通过，且仅使用 in-memory credential、mock HTTP 与内存 SQLite。 | 本地 mock-only 合同 | 不等同于真实执行：尚无 UI/Tauri 组合、用户凭据输入或读取、已核验 model/price catalog，亦无 HTTP 授权；用户仍须决定保留与费用上限。 |
| 实包媒体关联 | 两份实包没有可证明的 message-to-asset relation；`UNMAPPED_REJECTED` 是正确安全结果。K8 的人工精确关联功能已实现，但尚未发生用户在 Settings 中明确选择资产和目标消息的真实动作 | 外部用户操作 |
| 真实 Provider / 账号同步 / 生态 | 本地 owner、禁用状态和合同已存在；真实 HTTP、账号、OAuth/发布白名单、同步及生态目标仍分别需要已配置的外部服务和可验证账户/目标 | 外部条件，不得伪报完成 |
| 新增功能审阅与入口建议 | Android 与 Desktop 设置均新增“功能审阅”；当前登记 ZIP 导入、未关联媒体人工关联与 Desktop Compare 联网执行，展示待您判断的去留状态、入口建议与小字理由。今后每项普通用户新功能必须同步登记，默认不增加聊天主页/Composer 常驻按键。 | 已建立规则与双端实现 |

### 原始蓝图 P0–P11 全路线覆盖审计（2026-08-23 补充当前状态）

`MASTER_DEVELOPMENT_BLUEPRINT.md` 第 17 节定义的是总控方案的**完整路线**。本表是唯一的总控排程入口；上表和后文需求矩阵只记录阶段内已获得的证据，不能缩小或替代 P0–P11 的退出门。

### 2026-08-21 P6 v2 完整 owner Android DocumentsUI 增量（Desktop 尚未开始）

- **新增真实证据：** 只在新的 `emulator-5582` 通过正常 Android UI 形成最小非敏感完整 owner，并以 Settings → 数据与导入 → 导入中心 → 完整工作区交换（v2）→ DocumentsUI 实际保存。范围为 Project/Conversation/Knowledge/Memory/Relation = `1/1/2/1/1`、附件 1；App 严格回读 semantic `fdf9f95ac84005a173807779c055f0a3bd2112b01beb9f7bb28763dbe19d9ff9`。对实际保存包的只读 Node strict verifier 也通过（entries 3、asset 1），匿名 owner-field structure 为 8 项、field-set digest `ead2f34eea50ffce928c97d9e117b766de9b2ea07bd52864da1649a6ed4aea1c`。
- **根因修复：** 真实导出先正确拒绝遗留手工 Knowledge 的 `sourceReference=manual`；修复后新的手工 Knowledge 不再写 locator。另修复 repository filter 缺失导致回收站 Knowledge 可能被完整范围纳入的问题。两项均有 Android 定向 JVM 回归，且本次 UI 范围实测为上述准确计数。
- **未扩张结论：** Desktop 的全新独立 acceptance bundle、native Open/Save、private receipt/provenance 与 re-export 比对尚未开始；Android v2 import/archive/recovery、Desktop 原生对象恢复、Windows、正式发布、OPPO 和 P0–P11 总控均仍未退出。context gate 已要求在此停下，下一线程只能继续该 Desktop 独立验收，不得重用历史 bundle/root 或用文件/数据库注入替代 native picker。

| 原始阶段 | 已确认的当前落点 | 仍未关闭的原始退出门 / 下一类工作 | 总控结论 |
| --- | --- | --- | --- |
| P0 总方案与治理冻结 | 蓝图、合同、交接、审计与功能审阅规则都已存在。 | 每次扩展继续维持蓝图、合同、当前事实三者一致；用户对总体方向的持续确认不由旧记录替代。 | 治理基线已建立；持续维护。 |
| P1 Android 工程与可测试领域基础 | Kotlin/Compose 工程、领域端口、结构化错误、构建和定向测试基础已长期运行。 | 保持依赖、静态检查与领域合同的回归门。 | 基础已建立；持续回归。 |
| P2 最小捕获与知识闭环 | 本地捕获、知识、导出、Registry/Invocation 安全事实与 mock/禁用边界已完成多项增量。 | 用户授权的真实非敏感 Provider 文本与图片、目标真机链路、真实保存重启导出及 Key 泄漏检查，均不能由本地 mock 代替。 | 未退出。 |
| P3 Claude 级多模型对话核心 | 对话树、分支、草稿、附件、展示和本地 attempt 谱系已落地。 | 真实流式、停止、失败/重试/换模型、部分计费、真实用量/成本可见追踪、长会话实测与 Provider 充分性判断。 | 未退出。 |
| P4 项目、记忆、上下文与知识完整化 | Projects、Memory、Knowledge、多个单独 Adapter、离线 Eval 与本地上下文控制面均有局部闭环。 | 真实上下文/语义摘要、缓存和成本质量基准、更多 Adapter 的逐个验收、真实 Provider/Harness 回归与用户价值证据；P3 的真实退出门仍是前置。 | 未退出。 |
| P5 Android 产品化与正式交付 | 历史 code-52 正式 APK `c511…` 已完成 v2/v3/同证书核验、OPPO 一次保留数据覆盖及准确 Launcher 前台启动；P5-D 隔离 emulator 的 SAF 备份/恢复链亦有证据。全新隔离 AVD 还已完成从 `a4eebd1` 离线重建的正式 code-52 到当前 code-57 的同证书 `install -r` 覆盖：非敏感 UI 草稿、首次安装时间和 CE/DE inode 保留，cold start/readback 成功；同环境三次强制冷启动为 `1069/930/898 ms`，形成隔离回归基线。 | 全设备可访问性/性能、OPPO 上的当前版本迁移、正式发布/商店交付仍未关闭。当前构建与历史 OPPO 包的字节差异/P11 发布资格仍未收敛，当前包不得作为 OPPO 覆盖或发布物。 | 未退出；仅上述 P5 局部门关闭。 |
| P6 Desktop 与跨端离线体验 | Desktop P6-K bundle/readback、Android 隔离导入和 P6-A 独立文本交换已有证据；v2 已获 Android 空本机 DocumentsUI restore/replay/篡改与超限拒绝/非空拒绝、修复后 restore→DocumentsUI export 真文件回读、Android DocumentsUI→隔离 Desktop native Open/Save/replay，以及附件提升中断→零发布→同包 DocumentsUI retry 的局部证据。新的 Android 35 隔离设备还已得到 schema-38/code-51→schema-39/code-52 同签名覆盖、`schema=39` 启动审计与 2 Conversation/2 Draft 历史 UI readback；随后经普通 Settings/导入中心/DocumentsUI 单次选择 strict fixture，显示非空本机中文 `LOCAL_TRUTH_PRESENT` 拒绝，前后计数不变。P6-L1–L4 保持 content-free 本地精确复用/消息引用有效性边界，未接入执行。 | 原始退出仍要求完整对象的常规跨端导入导出/回读、紧凑/展开和异常恢复的独立验收，及正式发布。Desktop 原生业务对象恢复、用户真实附件等仍未获证；Windows 已由用户豁免为非阻塞债务。 | 未退出；不得将 fixture 或局部文件链写作 P6/P0–P11 完成。 |
| P7 可选账号与端到端加密同步 | P7-A 至 P7-E 的本地协议、状态机、部署工件和 typed restore 主体已完成；Desktop P7-E 现对 candidate 的 canonical identity 做复用/切换前后重读，并使 legacy crash temporary 保持隔离、不阻塞新 candidate。 | 真实 Google、Supabase、OAuth、受控 HTTP、真实 Android/Desktop 跨设备恢复与部署回读均由用户明确豁免为非阻塞债务；不得据此声称已完成真实云同步。 | 不作为总控完成门。 |
| P8 受控 Agent | 本地 ledger、harness、只读 inspect 与本地红队退出已完成；本轮进一步统一双端 plan admission：三类预算按已用+完整计划预检，重复 step intent 在 approval 前 durable fail-closed。 | 每一个将来启用的真实工具必须分别完成成功、失败、取消、审计、幂等与恢复；不得把本地 fixture 说成真实 Agent。 | 本地主体退出；总阶段未退出。 |
| P9 南枫生态协议接入 | LOCAL_TEST_ONLY 集成底座已完成；每个可继续步骤重验合成目标句柄和 expiry，目标重选/过期前不会产生新的本地 receipt。 | 至少一个真实目标应用的稳定入口、权限 UI、用户确认、结果回读、撤销和审计。 | 未开始真实接入。 |
| P10 可选 AI Hub | 尚无两个真实应用消费者，触发条件未成立。 | 仅在触发条件成立后，验证 Hub 的多应用隔离、降级、回滚和运维恢复。 | 未触发，不提前建设。 |
| P11 长期运营与持续演进 | 版本化合同、审计、签名和交接已形成部分运营纪律；AAPT2 metadata 已无写入回读，当前 release build 的非 debuggable/签名结构已核验。 | 当前 APK `d612…` 与历史 code-52 `c511…` 字节不同，必须先收敛可重复性风险，不能用于安装/发布；模型/价格/Provider 复核、迁移、隐私删除、依赖安全、备份演练及 Android/Desktop 发布节奏仍为持续责任。 | 持续阶段，不存在“一次性全部完成”。 |

**总控的真实下一序列：** 先保持 P0/P1 治理与回归；P2–P4 的真实 Provider 与成本/质量证据、P5 的旧 APK 升级迁移/发布门及当前构建字节差异收敛、P6 剩余跨端/Android 正式门、P8 每个真实工具、P9 首个真实生态接入，均按原始依赖逐项推进。Windows 验证及 P7 真实账号/云同步均为用户明确豁免的非阻塞债务。P10 只在两个真实消费者出现后触发；P11 永续执行。任何局部闭环都只能关闭其所属行的一段证据，不能宣布 P0–P11 总控完成。

## 结论

总控方案仍不能标记为“完整落地”。P5 已有历史 code-52 正式签名、OPPO 保留数据覆盖和前台启动证据；P6 已新增 Android DocumentsUI/空本机恢复与 replay/reject、Android→Desktop native Open/Save/replay、journal interruption retry、schema 38→39 同签名覆盖、历史 Conversation/Draft owner UI readback，以及该非空设备经 DocumentsUI 的 `LOCAL_TRUTH_PRESENT` 中文拒绝/计数不变。P6 的剩余完整对象恢复与正式发布仍未验收；Windows 已豁免。当前 `d612…` build 与历史、已在 OPPO 验收的 `c511…` 字节不同，不能用于安装或发布。真实 Provider、真实工具、生态及各阶段外部门仍分别开放；Google/Supabase/云同步已由用户豁免为非阻塞债务。没有以数据库注入、卸载、清数据、Key 或 HTTP 绕过任一门禁。

历史复验记录（非当前排程结论）：Desktop P6-K 定向测试 7/7、Desktop 全量 Rust 库测试 68/68（唯一会触达 macOS Keychain 的既有自测主动过滤）、Desktop UI 合同当前复验 89/89、lint 与 typecheck 均通过。Compare 阶段 5 另有 Rust mock-only 合同 5/5 与 `clippy -D warnings`；没有真实 Keychain 或 HTTP 调用。

## 历史审计的事实源与审计方法

- 当前事实以 `docs/CURRENT_HANDOFF.md` 顶部的 P11/P6 记录及同文件的 2026-08-23 P5 code-52 OPPO 证据为准；`MASTER_DEVELOPMENT_BLUEPRINT.md` 的历史“下一唯一入口”不再可作为当前排程事实。
- P6-K 的产品边界和完成门槛以 `P6K_CHATGPT_CLAUDE_ZIP_IMPORT_ADOPTION_CONTRACT.md` 为准。
- 本轮 Android 验收使用项目专属 release v2 签名的隔离 applicationId；它不触碰 legacy 包或 OPPO 数据。DocumentsUI 与 force-stop/cold-start 均是正常用户路径，聚合查询只用于交叉验证且不读出正文、标题、ID、文件名或账户资料。

## 历史需求—证据矩阵

| 需求 | 当前证据 | 验证等级 | 结论 / 缺口 |
| --- | --- | --- | --- |
| ChatGPT / Claude ZIP 选择即直接导入 | Desktop 已按正常系统 picker 完成真实 ZIP 私有导入、退出重开与安全聚合/receipt 回读；Android 隔离 v2 包也已完成 Settings → DocumentsUI → private staging → cold-start 匿名 readback | 双端真实文件链 | P6-K 主链已关闭；不以此替代媒体/外部能力验收 |
| Conversation + Message Tree 复用既有 owner | 双端均无第二消息真值；Desktop 真包已写既有文本树并重开；Android 同源 Claude 聚合为 162 成功/120 失败，ChatGPT 为 23 成功/494 严格失败 | 双端真实；Android 冷启动读回 | P6-K 主链已关闭 |
| 未关联媒体安全处理 | 实包没有可证明 message-to-asset relation；保持 `UNMAPPED_REJECTED`；人工精确关联有双端合成 owner-to-renderer 合同 | 实包只读安全审计 + 合成验证 | 真实媒体只能由用户在 Settings 明确选择资产与目标消息后验证；不得推测关联 |
| profile / personalization | 白名单 owner 已实现；两份实包均为无可采纳字段的安全结果 | 实包安全聚合 + 双端合成 owner 合同 | 无可写的真实字段，因此不应人为重试或制造写入 |
| P6-K Settings 隐私与撤销恢复 | Android 不显示所选 ZIP 名；撤销失败保留 recovery task/archive；Desktop 与 Android 均有正常 Settings 的匿名 aggregate readback | 双端真实/自动合同 | P6-K Settings 已关闭；真实媒体人工关联仍需用户明确操作 |
| 既定聊天、抽屉、Composer 不回退 | Desktop UI 合同 82/82；含 P6-K 入口、Compare、精确 placeholder、抽屉/Composer 保护；Android 当前源码可完成定向合同与正式 release/lint | 自动 UI 合同 + 当前构建 | 仍须逐项以真实 Android 交互验收，不以构建替代可见行为 |
| Compare 可见入口与 Desktop 本地适配边界 | Android/Desktop 均有模型菜单、Composer `对比`、模型长按三入口；Android 为空草稿先返回。Desktop 保持 UI fail-closed，但底层已有未注册的阶段 5 adapter seam，可在 mock HTTP 与内存 receipt 中写入两支安全状态 | 代码/局部 emulator + Rust mock-only 合同 | 显式 Compare 是直接产品命令，不再有第二次产品确认面；Desktop adapter 仍未组合至 UI/Tauri/真实凭据或 HTTP。Android 非空草稿真实执行同样受用户内容、凭据与 HTTP 门禁，不在本轮执行 |
| 普通聊天真实 Provider | 生产边界、确认合同、账本和失败关闭机制已实现 | 本地合同 | 需要已验证目录、可用凭据、当次可见确认和用户明确非敏感输入；真实 HTTP 未授权执行 |
| P5 备份/迁移等真实 Android 链 | P5-D manifest/preflight 从当前打开的 Room 读取 Schema（37），候选 SQLite 也须一致；完整 1→37 迁移链受定向测试保护。当前以 release-v2 签名隔离包 `com.nanzhufeng.ai.p5dacceptance` 经 Settings/SAF 完成成功导出回读、非敏感变更后的受控替换、force-stop/cold-start readback，installed base.apk 与本地验收 APK SHA-256 一致 | 自动化 + 当前隔离 emulator 正常 UI/readback | P5-D 的当前包 SAF 恢复链已关闭；旧 APK `install -r` 升级迁移、OPPO、发布、云同步与 Provider 仍为独立门槛 |
| P7 同步、P9 生态、P10 联网路径 | 本地协议、禁用状态和 LOCAL_TEST_ONLY/配置表面已实现 | 本地合同 | 仍需要真实账号、目标服务/应用、外部授权及网络；不能借“总控”推定完成 |
| OPPO 验收 | OPPO 当前保留数据安装的是 release v2 `0.3.0-p10a`、SHA-256 `fc8f9ac6…` 的较早正式包；当前源码 release 未覆盖安装 | 安装/版本/包哈希只读核对 | 安装链已存在，但本轮不覆盖安装；当前源码功能、折叠连续性与 OEM 交互仍待按单项真实验收 |

## 历史正式签名失败记录

受控命令为 `:app:assembleDebug`，只使用项目既有正式签名配置与 Android Studio JBR；Gradle 在项目配置阶段停止，提示必须恢复仓库外 keystore 与 macOS 钥匙串口令，或配置既有签名环境。没有读取、打印、导出、创建、替换或输入任何秘密；没有 APK、安装、picker、数据库写入、HTTP 或 OPPO 操作。

## 历史可继续的安全序列

1. 恢复既有签名记录的非交互可用性后，重新生成 APK；对 `emulator-5554` 仅 `install -r` 覆盖，核对本地 APK、安装 `base.apk`、前台 activity 与 UIAutomator package 三方一致。
2. 仅经 Android Settings 正常系统 picker 依次选择两份已授权 ZIP；使用中性临时名，私有暂存后删除临时源，并且不记录正文、外部 ID、附件名或账户资料。
3. force-stop/cold-start 后仅回读 task/receipt/conversation/message/media/profile 的安全聚合；真实媒体不做人工精确关联，除非用户在 UI 中选择具体匿名资产和目标消息。
4. 在不触及任何导入数据的前提下，先诊断并恢复 Desktop 最终 bundle 的白屏/WebView 启动可见性；恢复后才从正常 Settings 路径重做无正文 aggregate readback。真实 Provider、账号/同步、生态目标和 OPPO 仍分别保持独立验收债务。

## 历史不可关闭项

- 正式 Android APK、模拟器真实 ZIP 导入和冷启动读回。
- Desktop 最终 bundle 白屏后的正常 Settings 无正文可见回读。
- 用户在 UI 中进行的实包媒体精确关联（当前没有自动关联依据）。
- 真实 Provider/HTTP、真实账号/同步、真实生态目标和 OPPO。
