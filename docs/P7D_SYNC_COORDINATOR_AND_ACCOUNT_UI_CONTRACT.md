# 南枫 AI P7-D 冲突、自动同步协调器与账号页合同

日期：2026-08-13  
状态：P7-D 本地协调器、账本、离线账号页与 Desktop 离线入口；真实身份、Google OAuth、Supabase HTTP、远端恢复和跨设备验收仍属 P7-E 外部门。

## 真值、数据边界与快照

Android Room 与 Desktop SQLite 各自仍为业务真值。唯一 `P7DSyncCoordinator` 只接收显式的、稳定排序、有界的 `P7DSnapshotSource` allowlist，不得读取/序列化全库、对象图或未知表，更不得把 `Room` 全库 `encodeToString` 成第二份大字符串。生产 source 必须在 IO dispatcher 流式产出 `project`、`conversation`、`knowledge`、`memory`、`relation`、`safe_settings` 的 `NORMAL` record，再由 P7-A seal 生成 envelope；取消作为控制流原样传播。

Provider Key/ref、Prompt/RunSpec、诊断、URI/path、头像缓存、Adapter 私有资产、恢复码、token、data key、`HIGH_SENSITIVE` record 和未合同化表一律排除。P7-D Room 19 只存 account hash、generation、stage/error、local/remote revision/hash、intent receipt 与 app-private staging envelope 的 opaque ref/hash/bytes；不存业务正文、envelope bytes、HTTP payload、秘密或路径。

## 唯一协调器与调度守卫

`P7DSyncCoordinator` 是唯一可同步业务写入的调度裁决点：真实 allowlist business mutation 才递增 generation 并申请约 30 秒 unique one-time work；12 小时 periodic 仅作兜底。两类任务均要求网络，且必须同时满足：P7-C 配置完整、瞬时 verified session、P7-B `READY`、恢复码确认与方向明确。当前 App 没有私有配置或会话，故真实生产路径为 disabled：零 WorkManager enqueue、零 worker 网络、零 HTTP、零假登录。

冷启动、登录会话恢复、打开账号页、刷新头像均不得申请 one-time sync；它们最多恢复安全 metadata，并在全部 guard 满足时启用 periodic。正在运行时产生新 generation 必须保留下一代 work，成功的旧 generation 不得吞掉它。唯一 job name 按 opaque account ref 隔离；退出/切换取消该账号 one-time 与 periodic work。

Worker 阶段只能为：生成结构化快照、检查云端版本、加密、提交、回读验证、完成、冲突、失败/中断。网络等待只能处于检查/提交/回读，不能伪显示为“正在生成”。提交后必须回读同 revision/hash 才能标记完成；远端有变化且本地仍有未同步 generation 时进入 `CONFLICT` 并停写，不自动合并或覆盖。

## 方向、恢复、账号与退出

方向只由显式确认决定：空本机+远端可进入恢复计划；本机非空+远端空可确认上传；双方非空显示安全的计数/hash/时间预览，默认取消，只能选“保留本机并更新云端”或“用云端替换本机”。P7-D 不实施本机替换：真正 replace 必须后续接入 P5-D 原子 checkpoint/恢复链并另行完成失败回滚验证，因此本轮只持久化安全 plan/guard，不清库、不覆盖。

账号切换先通过 typed auth-owner 清除 Credential Manager 候选记忆，再取消任务并重新走恢复码/方向，不允许后台绕过。默认退出只取消任务、清该账号头像 cache/短期 session、保留本机业务数据与 P7-B vault；“退出并清空”仅保留红色二次确认计划，不执行删除。

## 账号页、头像与 Desktop

设置页新增可返回“Google 账号与同步”详情。配置缺失时固定显示“尚未配置 / 离线可用”，不显示姓名、邮箱、头像、云端成功或伪同步；登录、立即同步、切换和退出操作均 disabled 并说明需要私有配置与已验证账号。配置与真实身份齐全后才可显示身份卡、同步状态和操作。

页面底为低饱和灰白，账号卡/操作组/状态卡及所有 Dialog/DropdownMenu 内容面为 `#FFFFFFFF`；灰色只用于页面底与 scrim。交互目标至少 48dp，Surface、阴影、ripple、focus 与轮廓共享同一 shape。账号页状态只显示“已同步 / 正在同步 / 需处理冲突 / 失败”、上次成功时间和简短加密范围，不显示工程 revision。打开页面本身绝不调度。

头像 cache owner 以 account hash + validated URL hash 隔离，先读私有 cache，后在 verified session 下调 P7-C JWT proxy，最后才对严格 `https://*.googleusercontent.com` 直连 fallback；8 秒、2 MiB、每跳重验或 no-redirect、原子写入、退出删除。无真实 identity 绝不请求；cache 不入 snapshot/backup。

Desktop 只实现同等 coordinator/state contract 与离线-disabled入口，不启用 Tauri HTTP capability、浏览器 OAuth callback 或远程 command。macOS 继续可离线使用；Windows 的 credential/callback/installer仍独立。

## 验证与退出

自动覆盖：启动/账号页/头像读取不排队、真实 mutation 才 30 秒、运行中新 generation、periodic guard、恢复码/方向、回读 hash、冲突、账号切换、退出保留/取消、worker 幂等/中断、敏感数据不落账、头像策略/cache 隔离、纯白/disabled/返回 UI。无配置模拟器验收必须可见“尚未配置 / 离线可用”、无假身份、force-stop 后精确 JobStatus 为零。当前验证中 P7-D 定向 Android 测试、lint/Debug+Release、Node/Rust/Desktop 门均通过；最终全量 JVM rerun 受 Android Studio JBR SIGSEGV 阻断，非断言失败，需在稳定/备用 JDK 环境补跑。

Android production source 变更升 code 44 / `0.3.0-p7d`；Debug/Release 都须既定正式证书，同签名仅 `emulator-5554` 覆盖。Desktop 完整前端/Rust/Tauri build 后重作 ad-hoc并 strict verify。真实 Google/Supabase、远端 restore/replace、真机身份/头像、Android/Desktop 跨设备仍由 P7-E 逐项验收，不能由本合同或本地测试替代。
