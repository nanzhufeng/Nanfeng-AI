# 南枫 AI 当前交接

## 2026-09-15：首次 GitHub 发布

- 私有仓库：[Nanfeng-AI](https://github.com/nanzhufeng/Nanfeng-AI)，主线 `0089fdf`。
- 预发布：[v2026.09.15-initial](https://github.com/nanzhufeng/Nanfeng-AI/releases/tag/v2026.09.15-initial)：Android `0.3.0-p10j` APK、macOS `0.6.0-p6d-dev` ZIP 和 `SHA256SUMS.txt` 已回下载校验。
- Android 图为本次候选包在隔离模拟器重新采集；Mac 未截图，避免误拍已有数据。
- GitHub Actions `34954792798` 三项源码检查通过；不代表 Windows 构建、macOS 公证、主设备或真实服务验收。

## 2026-09-15：用户要求的完整项目复盘（仅文档／流程）

- 基线 `ff9fd33`，全量清单 1,344 路径／154 本地提交；结构检查和定向语义核查见 [复盘报告](review/20260915-project-retrospective/REVIEW.md)。更新开发档案、长期 AGENTS、五类项目 Skill、可迁移经验，新增逐文件结构检查脚本；未改业务／配置／测试，未重新安装或访问真实服务，未提交。
- 当前档案修正 Room 69／Desktop 42、目录统计和 direct 数据流；P7-F 首部加密承诺与当前 direct 实现仍冲突，记录后留待独立安全合同处理。当前前端 14／Android 7 失败照实保留，不能用历史全绿覆盖。没有修复用户真实历史标题或重新验证结算／删除闭环。
- 非文档／流程的 976 个跟踪路径已比对，业务字节未变，原有图标源缺失与未跟踪截图保持。新跑协议、Supabase 9 项、Go、静态入口／命令检查；此前同源码最终回归复用，验证摘要见 [validation.json](review/20260915-project-retrospective/validation.json)。

## 2026-09-15：双端覆盖完成，当前代码 checkpoint 收口

- 代码 checkpoint：`50f4b32`（101 个源码／测试／schema／协议／服务文件）。本节及领域合同随后以独立文档 checkpoint 固化；未推送远端。Supabase 两套本地静态合同 5 passed（`/tmp/nanfeng-checkpoint-supabase.log`），不代表本轮部署了服务端迁移。
- 文档检查：修复经验页一处已迁移 ViewModel 链接；历史 20260910-phone-to-desktop 的三个外部备份链接当前不可达，保留其历史记录，不声称备份仍存在。本次 Desktop 回滚包使用上方日期对应的新路径。工作流审计无 hard error；全局 AGENTS 体积软警告未作范围外修改。

- 本节覆盖下方历史“未安装”状态；不改写当时记录。标题独立版本、列表读取解耦和删除传播已包含在本次双端包中。安装不等于真实双端同步全部验收通过；本轮没有改名、读云端合并或删除用户真实会话来测试。
- Android：OPPO 正式包保数据覆盖成功，包名 `com.nanzhufeng.ai`，66 / `0.3.0-p10j`。新旧证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`；安装后拉回 APK 与候选包 SHA-256 均为 `dc8a4fafda7c1b1fbf8ddd75d214cd7dedeee8d8c41fbe4870f86e1c4f614a6c`。首装时间 2026-08-20 15:15:31、CE inode 1459104、DE inode 1433378 未变，更新时间 2026-09-15 16:16:10。未卸载、清数据或自动启动，临时设备 APK 已移除；未据此声称 Android 正文逻辑哈希或运行时迁移已实机验收。
- Desktop：原位覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，Bundle ID `com.nanzhufeng.ai.desktop`，沿用原 Apple Development 身份，Team `457B263L9J`，新旧 designated requirement 一致，严格 codesign 验证通过。候选与安装后执行文件 SHA-256 均为 `e29fa3e55a4322ac4b1670961055e6a73a53bfb7972c924e43e0c633580d9ffd`。备份为同目录 `南枫 AI Desktop.pre-cloud-delete-20260915-1617.app`。本次无需输入密码、未导出私钥或修改钥匙串 ACL；仍使用原签名私钥，不承诺未来永不提示，也不是 Developer ID 公证发行。
- Desktop 保数据证据：旧进程退出且无 WAL 后 immutable 读取，quick_check 为 ok，4 个 workspace_exchange／858 条会话。覆盖前后按 workspace_id 排序的 exchange_json 流 SHA-256 同为 `f51e0fcc9ecd18fa931917bc8bdace916b22cddc1d84cb62b053674bda3518fa`。启动后既有会话正文可见；数据库整体哈希因启动写入发生变化，不能称数据库逐字节未变。没有移动或清除数据目录。
- 已完成构建证据复用：Android lintRelease + assembleRelease 成功（`/tmp/nanfeng-dual-cover-release.log`）；Desktop 签名 bundle 成功（`/tmp/nanfeng-dual-cover-desktop-build.log`）。产物已冻结，不为文档再次构建或覆盖。
- 回归边界：Android 本增量全量 1186 tests / 7 failed / 3 skipped，相关六套 52 tests 无失败；7 项既有 UI 合同失败未掩盖。最终冻结源码重跑 Rust 全量 280 passed / 0 failed / 1 ignored（`/tmp/nanfeng-checkpoint-final-rust.log`）；前端 436 tests / 410 passed / 14 failed / 12 skipped（`/tmp/nanfeng-checkpoint-final-js.log`）。失败含多行标题旧断言、旧 store-lock 断言及其他 UI 断言，不为消除红灯改用户要求。真实服务、大库延迟、历史标题分歧的人工确认与双端实际删除闭环仍不能由安装替代。
- 本次正式沉淀只增补项目合同、决策、开发档案和经验，不重复历史全库复盘，不修改长期记忆或全局规则。核心新源码、测试、Room 67–69 schema 和共享 fixture 纳入代码 checkpoint；临时截图、输出、私有配置与安装包不入 Git。

> **当前合同读取门：** [Android 会话合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)、[设置合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)、[运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)。以下按最近增量记录；历史验收不能覆盖这些当前合同。

## 2026-09-15：已选对话删除传播与云端列表清理（源码／隔离验收，未安装）

- 用户要求已同步对话再次删除后清理对应云端副本，云端列表只保留真实成员。范围为有明确同步身份的逐对话删除／远端成员核对；不清空账号，不删除无来源孤立文档，不删除另一端本机正文。本轮没有真实账号读写／删除／安装。
- 已修复：Desktop 完整身份可判定的 list 对照请求前回执，仅清理未更新且已缺失的当前账号选择与非 UNKNOWN 任务；返回 cloudConversationKeys 给前端裁掉幽灵缓存。Android 同样核对完整封包身份并事务清理未变回执／置顶；旧格式保留身份用于核对但不恢复。畸形身份、网络失败、账号变化不按空列表清理；仍在云端但恢复冲突的条目保留。
- 防复活：已有回执但远端明确不存在时停止并清理选择，不按 expected revision 0 重传。Desktop 新 continue_selected_conversation 和 continuation IPC 标志区分后台／回复续同步与手动新建；Android 后台 syncInternal 要求仍有选中身份，防止旧任务捕获目标后重新创建。只有新的明确手动同步可再次建立副本；参与设备必须双端升级，旧客户端行为不在保证内。
- 本机删除传播：Desktop softDelete／permanentDelete 与 DELETE_PENDING 任务同事务保存，沿原任务 owner 启动／30 秒重试；失败保留 DELETE_PENDING，永久删除后仍可清理远端。只有本机明确 deleted 或缺少本机记录但有明确删除任务才走远端删除，不把一般读取缺失当作删除授权。Android 沿原 WorkManager 变化监听，删除前重新读取远端 revision，避免旧回执 CAS 永久失败。任务和回执清理不触碰正文。
- 行为证据：JS 新用例先红后绿，完整空清单清缓存／不完整保留／有效但冲突的行保留；修正同文件旧排序断言为服务器已返回顺序（不改排序实现）。Rust 公开同步／列表用例验证本机删除→远端清理、远端删除→续同步不重建、再次明确同步才重建、其他账号不受清理、本机正文保留、坏身份不清理；新增真实 store 软删→失败退避→永久删→重开仍有删除任务。
- 回归：Desktop Rust 全量 280 passed / 1 ignored；其后删除重试保持 stage 的微调以新增定向测试复验。前端相关 31 passed / 12 retired skipped，build 通过。Android 全量 1186 tests / 7 failed / 3 skipped（仍为前轮 UI 合同），其中账号与 P7-F data 六套 52 tests / 0 failed / 0 skipped；Android 真实 HTTP→Room 多设备删除仍未实机验证。没有发布或覆盖旧安装，不能说用户当前双端已生效。

## 2026-09-15：云端列表稳定性／等待链路审计与第一轮修复（未实机验收）

- 已确认代码分叉：Android UI 读取前逐条检查／迁移已选文档，失败直接退出；列表 owner 已获得完整封包却丢弃、逐条重新联网；Desktop 读取前进行旧 direct 文档上传并可能第二次 list；单文档恢复错误通过 `?` 中断整批；前端在展示前等待账号回读与旧置顶上传。读取用例耦合写入、恢复和非必要展示刷新，原合同未定义这些故障边界。
- 已实现：双端读取移除迁移上传前置步骤（明确 sync 仍处理旧源）；Android 同一 list 封包直接进入原恢复校验／合并，置顶也复用封包；Desktop IPC 转后台阻塞线程，前端重复点击 single-flight；移除显示前账号回读／整页 refresh／置顶迁移写入；逐文档恢复隔离失败并返回 failedCount、界面提示部分失败与旧格式跳过。Desktop 快速连接／服务失败最多重试一次，长超时、鉴权、限流、格式和写入不重试。
- 红绿证据：隔离 Rust 公开 `restore_all_remote_conversations` 输入坏文档后跟有效文档，原代码 Err 云端文档标识无效，新代码保留一条有效恢复并报告一次失败；实际前端生产分支在账号刷新 Promise 未完成时原来不显示列表，新代码已显示，测试先红后绿。追加公开批次临时失败恢复／永久错误不重试测试。Android 新流水线目前有编译及相关 wire／UI 合同覆盖，没有完整账号 owner HTTP→Room 注入行为测试，不夸大验证层。
- 本轮验证：Rust 全量 279 passed / 1 ignored；前端相关 29 passed / 12 retired skipped；Desktop build 通过。Android 全量 1186 tests / 7 failed / 3 skipped，7 项仍为既有 UI scrim／稍后看／新会话／无障碍／PDF 合同；账号 UI 21 项无失败。前端全量 434 tests / 407 passed / 15 failed / 12 skipped，其中存在多行标题旧断言、旧 store-lock 断言、列表排序等不一致；未声称全绿，未改无关样式或标题规则。
- 未完成／需继续：Desktop 每条恢复仍会扫描其他工作区同源副本并解析整份 exchange JSON，列表后还读取工作区投影；真实大库耗时未量测，不能认为长等待已全部消除。RPC 返回整份封包而非分页索引；RPC 级畸形响应仍可能整批失败。须补真实 gateway 层失败隔离／Android 可注入 seam、隔离大库基线和双端正式安装验收。无真实账号读取／同步／标题操作，无安装、无主设备测试。
- 当前规则唯一正文见 P7-F 合同“云端列表读取边界”。不因这些优化恢复、猜测或改写用户历史标题。

## 2026-09-15：标题独立版本与持久续同步已实现（源码／隔离验收；未覆盖安装）

- 接续下方审计，已实现而非仅报告：双端 `titleRevision` 独立逻辑版本、Android 68→69 nullable 前向迁移、Room 读写、自动／手动标题、双端 wire 保真、读写共享合并、同版本冲突保护。具体当前规则唯一放在 P7-F 合同“标题事实与续同步”。迁移不会修改任何旧标题或给旧记录编造版本。
- 红灯：通过真实 Android `ConversationManagementDomain.apply` 构造一端 RENAME、另一端随后 PIN，再走上传合并，旧实现返回旧标题，实际断言失败。独立版本接入后该用例通过，并补了慢时钟、并发改名保护。
- Desktop：本地／云端重命名共用已有选中续同步，缓存回读修复保留；新增标题待同步任务与手动／自动／undo／redo 标题事务原子保存。重启仍有任务；未选对话不入队；完成旧上传不清掉后一次改名任务。任务重试有退避，UNKNOWN 走现有核对，标题冲突停止自动覆盖。
- Android：`Conversation.titleRevision` 与 `ConversationEntity.titleRevision` 双向映射；普通持久化仅在标题真实改变时推进版本，验证过的云端合并保留远端版本；DAO 更新参数已补齐。Room 测试验证 repository 重建、非标题修改、远端版本 7 接收及下一次本地改名变 8；68→69 迁移保留旧标题且版本为 null；历史迁移清单已补全。
- 共享协议回归：`protocol/fixtures/title-sync-v1.json` 六组独立事实，Android/JVM 与 Desktop/Rust 各自执行读取和上传两条路径。包括晚置顶、时钟偏差、版本较旧、并发冲突、legacy 冲突、同文字新版本，两端通过。无真实账号／会话写入。
- 最终验证：Desktop Rust 全量 **278 passed / 1 ignored**，前端关联 **27 passed / 12 retired skipped**；Android 标题／Room／迁移／已选同步五组 **44 passed / 0 failed / 0 skipped**。Android 全量较早在本增量快照上 **1185 tests / 7 failed / 3 skipped**，失败为已有 UI 静态合同（居中 scrim、稍后看、新会话列表、侧栏无障碍 3 项、PDF），不伪称全绿；新增迁移清单失败已修正。Release Kotlin 编译、Desktop build、受影响文件 diff 空白检查通过。全量 Android 后新增共享 fixture 已在最终定向组通过，不能据此推算全量新总数；不能以安装包替代真机。
- 当前没有正式打包／覆盖安装，没有读取合并、重新命名或回滚真实会话。仍需处理全量 UI 门禁、正式双端升级及真实路径验收；历史已分歧标题需先给出有来源的清单并确认，不由代理选择值。已有旧安装仍是旧行为，不能说用户正在运行的软件已修好。

## 2026-09-15：标题同步代码／合同／架构审计（未完成整体修复，不可发布为已解决）

### 数据边界

- 用户要求修复同步，不授权替用户决定标题。前轮读取真实云端导致部分本机标题回退；另通过正式重命名入口修改过一条置顶标题。用户明确反对后停止真实标题写入、回滚、读取合并和同步操作。本轮仅源码、隔离 SQLite／JVM／模拟网关验证；未安装或触发主设备操作。
- 已移除源代码中的真实会话 ID 硬编码临时云端探针。保留隔离回归，不用用户会话作为写入测试夹具。

### 已定位的分叉与当前改动

| 层 | 证据／症状 | 当前结论 |
|---|---|---|
| Desktop 入口 | `app.mjs::mutateConversationLifecycle` 原来仅 `cloudWorkspaceId` 存在才上传，本地列表重命名只落库 | 与已选身份跨列表续同步合同冲突；现复用 `syncSelectedContinuation`，未选对话仍不上传。生产动作 IPC 替身回归先红后绿 |
| Desktop 投影 | 保存后 `refresh()` 不重新读取 `cloudConversations` | 原云端行继续显示旧标题；现从持久化投影替换目标行并更新缓存。同步失败与本机保存成功分开报告 |
| 双端读取／上传 | 上传比较时间／revision，读取无条件接收远端标题 | 隔离回归复现旧远端覆盖新本机标题；现两条路径共用各端 `incomingTitleIsNewer`／`incoming_title_is_newer`，保留更晚本机标题及其版本。Desktop 公开同步／恢复回归额外验证保留后继续上传，不误判 UP_TO_DATE |
| 同版本冲突 | 同一更新时间及语义版本可对应两个标题；没有因果证据判定谁新 | 当前双端均拒绝覆盖，保留原值。此为止损，不是历史冲突已恢复；不能按模型前缀、标题长度或指定设备猜赢家 |

### 尚未修复的核心架构缺口

1. **没有标题独立版本。** `ConversationModels.kt::Conversation` 仅提供会话级 `updatedAt/revision`；`ConversationManagementDomain.apply` 的 RENAME、PIN、ARCHIVE、项目变化等共用这两个字段。同步 wire 也只携带会话时间和 revision。静态可构造：A 改标题，B 保留旧标题后置顶，B 的会话时间更晚，于是当前比较器仍可能把 B 的旧标题当作新标题。尚未新增该交错场景的端到端回归，不能称已排除。
2. **合同缺少标题冲突语义。** P7-F 只规定“按最新内容”合并，未定义标题级因果信息、同版本冲突、时钟偏差、并发改名与旧客户端兼容。仅统一比较函数不等于补齐合同。
3. **Desktop 续同步依赖前端入口接线。** 手动操作及回复完成分别调用同步；`mutate_desktop_domain` 仅写本机。Android 则由 Room invalidation 调度 WorkManager。需统一为持久化变更与待同步任务的用例边界，验证离线、退出、重启、自动标题完成；当前未证明这些路径可靠。
4. **服务端文档 CAS 不能判断标题新旧。** `nanfeng_sync_commit_document` 验证整份文档 expected revision／envelope。客户端先读取最新文档版本，再提交语义陈旧标题，仍可能通过 CAS。没有证据说明是某个模型的标题分支或网络传输漏字段。
5. **文档漂移。** P7-F 的“业务内容在本机加密后才交给云端”“尚未完成真实账号登录”与当前 direct envelope 实现及前轮真实回查记录不一致。此项不是已证实的标题丢失原因；应单独校正来源与适用版本，不能无审计改安全承诺。

### 下一增量与验收门

- 先定义版本化标题事实／变更因果元数据（不能继续借用置顶／正文的时间），明确旧记录无元数据时不得猜测历史标题。覆盖 Android domain／Room／wire、Desktop domain／exchange／wire、两端统一合并及旧客户端门禁；通过单一协议 fixture 验证双向往返。
- 必测：离线改名后读取、另一端仅置顶／新增正文、并发改名、时钟偏差、同版本不同标题、失败重试、进程退出重启、旧版客户端、读写后再次读写、UI 缓存。未补齐这些前，不以当前比较器止损补丁宣称架构修复。
- 本轮最终定向结果：Desktop 同步 Rust **27 passed / 1 ignored**；前端生产动作与续同步 **7 passed**；Android wire／selected contracts／Room **25 passed / 0 failed / 0 skipped**。读取旧标题的回归实际先红后绿；同版本冲突保护断言通过。Android 同步 owner + Room 完整跨设备闭环、正式打包安装与真机未验证。
- 不自动恢复或重新命名用户真实记录。历史冲突修复必须先提供有来源的原值与拟修改清单，取得明确确认。

## 2026-09-15：问题存在、回答缺失的底层修复与 12:56 OPPO 覆盖

- **实际证据：** 只读 immutable Desktop 磁盘快照（不包含 WAL，不能当作实时云端证据）显示 MATCH 对话同 ID 的原始导入副本有 user + assistant 两条 COMPLETE/TEXT 消息，回答文本长度 3181；cloud 工作区副本仅一条 user，当前叶子也退回问题。两个 cloud 工作区副本都仅一条消息。原始回答仍在，尚不能据此证明手机首次漏传发生在哪个版本/步骤。
- **底层错误一：** 双端云端读取合并把“远端缺少某消息”当作删除依据，删除/过滤本机已有正文并退回远端叶子。本协议没有逐消息删除凭据。新增前缀载荷回归先红（Desktop 2 条变 1 条，Android merged snapshot 丢回答），后绿；移除缺失即删除规则，双端读写复用后代叶子选择，避免已保留回答被祖先问题隐藏。不是 MATCH 特判。
- **底层错误二：** Desktop 旧安装产生同 conversation ID 多工作区副本，回执可绑定残缺副本。新增 `recover_local_conversation_occurrences` 在读取/上传时按稳定 ID、已存在消息 ID/父节点/角色/正文核对，仅补缺失消息，不覆盖当前同 ID 消息；SQL 排除绑定其他账号回执的副本。隔离测试复现云端也无完整副本、原始本地副本仍完整时的恢复先红后绿。当前最多检查 32 个候选；真实多副本大库扫描性能、不同账号/分支碰撞还需更完整回归，不能宣称已全盘验收。
- **验证：** 定向 Android 39 tests / 0 failed / 0 skipped；Desktop Rust 同步 26 passed / 1 ignored；Desktop build 通过。Android 全量 1181 tests / 7 failed / 3 skipped，失败仍为此前 UI 合同，未冒充全量通过。单独 lintRelease、assembleRelease 成功。
- **主设备：** 用户明确回复“现在保数据覆盖”。OPPO `3B157F009E800000` 原包 SHA-256 `f1e4a21a7118f964f41dee08468a00856ba9b809f032332fa9e6d4ea93df59c6`；新正式包 `66 / 0.3.0-p10j`、非 Debug，签名证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8` 相同。2026-09-15 12:56:29 经临时推送 + `pm install -r --user 0` 覆盖成功，安装后拉回 APK 哈希 `be78dccea8f8a4bf5d6b11ed6574a751cf6cacd744577e12290e94a0023f90ac` 与候选一致。首装时间 `2026-08-20 15:15:31`、ceDataInode `1459104`、deDataInode `1433378` 不变；临时设备 APK 清理并核验。没有卸载/清数据/自动启动/仪器测试。哈希已冻结，不再重建此 APK。
- **未完成：** Desktop 新代码未正式签名部署、旧完整回答未通过新修复链路回传真实云端、手机升级后未实际读取验收。首个漏传位置尚缺手机源数据/对应远端历史载荷证据，不能把已确认的扩散机制说成全部来源已经查清。其他前轮遗留：Desktop portable modelUsage 恢复再上传保真、模型/金额空值补齐与同时间冲突、退出后续同步。需继续完成，不是“全部修复”。

## 2026-09-15：云端列表正文持续更新（代码与隔离回归通过，双端实机未闭环）

- **已复现并修复：** Android `mergeRemoteAdditionsForLocalCommit` 与 Desktop `merge_remote_additions_for_local_commit` 原先只在远端消息 revision 更大时更新已有消息。同 ID / 同 revision、远端对话 updatedAt 更晚的新正文被跳过。新增 Room 保存后回读和 Rust SQLite 回读测试先失败；两端现按较新的对话时间更新已有消息，时间相同再比较消息 revision。保留本机附件/运行块与独立新增消息，不修改用户真实数据库。设备时钟与字段级并发仍非已验收事项。
- **续同步入口缺口：** Desktop 原生成完成路径未触发选中对话续同步，定时任务只每 12 小时运行。新增 `selected-conversation-continuation.mjs` 并接入普通回复完成/失败重试的落库后路径；以现账号 `syncedConversationKeys` 限定范围，不依赖本地/云端列表。UNKNOWN 进入既有核对，不将同步失败伪装成发送失败，不恢复已发送草稿。此为窗口运行路径，不宣称所有进程退出/后台补账情形已验证。
- **验证：** 本轮 Android Room 合同 13 passed；Rust 同步 26 passed / 1 ignored，含同 ID 同 revision 第二次/第三次正文更新经封装、模拟 CloudGateway、恢复和 SQLite 回读；前端同步与续同步 23 passed / 12 skipped，`node --check src/app.mjs` 通过。不是实际远端或 Android 真机端到端测试。此前全量 JVM 7 项 UI 失败没有在本轮解决。
- **候选包：** `assembleRelease` 成功；APK `app/build/outputs/apk/release/南枫AI.apk` SHA-256 `d8f3d6ee6af9ebcc62dc0646b74473c41383ad2c293709dc2876be3eee145411`，apksigner 验证成功，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未覆盖主设备，最后 adb 仅 emulator-5592。Desktop 新代码未签名部署，不能把旧正式应用行为当新版本证据。
- **继续验收：** OPPO 连接后按授权覆盖，再核对真实云端列表连续追加/更新、标题、每条回答原模型和金额；Desktop 需先完成允许的正式签名交付。进一步审计同时间冲突、Desktop 恢复 portable modelUsage 的再上传保真、后台退出与补账后续同步，不能承诺已经全链路成功。

## 2026-09-15：10:48 手机旧标题与金额缺失续查（新代码未部署双端）

- **用户实际结果：** 手机模型名已出现，但多个标题仍旧，金额缺失。上一包 `f1e4a21a…` 已于 10:36:04 保数据覆盖 OPPO；安装后设备 APK 哈希一致，`ceDataInode=1459104` / `deDataInode=1433378` 与首装时间保持不变。这不等于同步验收通过。
- **已确认金额分叉：** Desktop 页脚 `projectedMessageCost` 会从原始/备用用量计算历史估算金额，但 Rust `portable_model_usage` 只传 `chargeMicros`；只读源快照中 7 条有模型消息的 6 条金额为空。此外 `usage` 对象存在但输入/输出为空时，Rust 未采用 `estimatedUsage`，与页脚选择不一致。现已修正备用用量选择。此 Desktop 改动只有 Rust 测试证据，尚未打包/部署。
- **Android 修复：** 对历史无金额载荷，使用消息原始时间、原模型及已传用量，复用既有版本化估算器恢复历史显示金额，来源明确为 `LOCAL_ESTIMATE`，不覆盖 Provider 已知金额；恢复后金额入 Room 并供再同步使用。测试固定 DS 原用量 5083/3361，对应 USD 2779 微单位，Android 六位显示 `¥0.018676`，与 Desktop 四位 `¥0.0187` 是同一金额。对只有模型、金额为空的旧 Room 行，`RoomCloudResponseModelUsageStore.recordVerified` 允许补齐原先未知金额，不清空或改写已有金额，不改原模型身份。
- **双端标题修复：** 本机上传前合并原来无条件保留本机标题，旧副本可能覆盖远端新标题。Android 和 Desktop 现在比较对话 `updatedAt`，采用较新对话的标题/当前叶子，保留独立新增消息并集；相同或不可比较时间仍保留本机。不宣称设备时钟冲突或字段级并发都已解决。
- **真实电脑操作：** 使用正式应用的既有“同步到南枫云”入口，逐条同步截图所示缺少新前缀的 6 条对话：置顶 DS、最近 DS、Sonnet、Terra、另一条 DS、Gemini；六次均在 UI 观察到“同步成功”。没有创建新对话或更改标题正文。此前磁盘快照的 8 条 RETRY_WAIT 是历史证据，不能再断言这 6 条目前仍上传失败；尚无手机端再次读取这 6 条后的证据。
- **回归与候选：** Android 本轮 29 tests / 0 failures / 0 errors / 0 skipped；Desktop 同步 Rust 26 passed / 1 ignored。先红后绿覆盖标题方向、空金额补齐、备用用量和历史金额。Android `assembleRelease` 与验签通过，`66 / 0.3.0-p10j` 非 Debug；`app/build/outputs/apk/release/南枫AI.apk` SHA-256 `1eec7965e3d87c1dd8b41f30da17d6cd04933bb01b0531c6993c1837e9f2b287`，证书仍为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。上一轮全量的 7 项 UI 合同失败/3 skipped 未在本轮解决，不称全量通过。
- **未完成/交付边界：** 新 APK 尚未覆盖；最后设备列表只有 emulator-5592，OPPO 断开。Desktop 新代码尚未部署；现有 bundle 脚本默认查询 Keychain，环境未提供 `NANFENG_DESKTOP_SIGNING_IDENTITY`，本轮未调用该签名回退。后续需按受控签名规则完成 Desktop 交付，并在 OPPO 连接后按授权同签名覆盖，再核对六条标题、全文及金额。不要把本轮 UI 上传成功或新包构建说成已完成双端回读。

## 2026-09-15：手机读取再次失败后的复现修复（尚未覆盖主设备）

- **不能沿用旧完成结论：** 用户 02:10 截图为 6 条完整性失败、2 条保存失败。之前的解码测试绕过了真实封包入口，打包和覆盖不能证明这 8 条已恢复。本轮未取得每条真实远端记录和 OPPO 错误日志，以下是可复现代码缺陷，不冒充逐条真机归因。
- **封包阻断已复现并修复：** `NfaiSyncSensitivityDetector` 的通用 `token` 禁用规则把 `modelUsage.inputTokens/outputTokens` 等合法结算字段也拒绝；`openDirect` 又将其混同于完整性错误。Android 现在采用与 Desktop 相同的固定模型用量白名单及数值/字符串上限，未知字段和凭据仍拒绝。测试先红后绿，覆盖 seal/open 和凭据拒绝；错误码区分格式、绑定、哈希与载荷字段校验。
- **已有消息保存冲突已复现并修复：** Room 的非空父节点/兄弟顺序唯一键会在两条既有消息交换顺序时逐条冲突。验证云端更新现在在同一事务内暂时释放待更新行的父关联，再写入最终合法关系；不删节点、不清数据。普通本地消息不可变写入合同保持不变。上传前合并远端新增内容同样改用已验证合并入口，避免普通 `save` 拒绝既有消息更新。
- **正文和汇总已复现并修复：** Android `nodes.text` 错套 4096 字的标识限制；正文改由封包字节上限控制、不截断。成功更新与失败并存时，汇总此前漏算 `updatedCount`；现在正确报告部分成功。
- **只读数据佐证：** Desktop 当前存储快照中，已选同步对话的消息统计包含 7 条带 `modelSnapshot`/`usage` 的消息，最大文本块为 9097 字。只读取计数/长度，未输出正文或秘密；该快照不证明服务端与手机当前内容一致。
- **贯通验证：** 新增 Room 测试经过封包 seal/open、解码、旧标题/正文合并、数据库读回、模型用量存储读回和页脚投影，断言长正文完整、`DS V4.1` 与 `CNY 18700` 微单位金额保留。54 项同步/Room/页脚定向回归通过。共享同步 golden 通过。最终全量 JVM 为 1177 tests / 7 failures / 0 errors / 3 skipped；余下失败位于 5 个 UI 源码合同套件（未读排序、居中弹层、会话行、PDF 预览、空会话列表），不能宣称全套通过。迁移清单补齐 66→67→68，提示断言改为分阶段错误后均通过。
- **构建与候选：** `lintRelease`、`assembleRelease` 通过。`app/build/outputs/apk/release/南枫AI.apk` 为 `com.nanzhufeng.ai`、`66 / 0.3.0-p10j`、非 Debug；SHA-256 `f1e4a21a7118f964f41dee08468a00856ba9b809f032332fa9e6d4ea93df59c6`，v2/v3 验签通过，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- **真实验收阻断：** 当前 `adb devices -l` 只有 `emulator-5592`，无 OPPO。已请求用户重新连接；本轮没有安装、启动、卸载、清数据或运行 connected 测试。连接后需先核对正式签名和主设备身份，再按授权保数据覆盖；回读真实失败记录的分阶段结果，双向核对标题、全文、模型和结算金额。尚不能称 8 条真实对话恢复或双端闭环。

## 2026-09-15：双端完整对话合并、未知提交继续核对与正式覆盖

- **同步语义：** 两端在每次本机同步前先打开并校验当前直接云端载荷，将对端新增的已完成文本消息并入本机快照，再以刚读到的 revision 提交。标题、当前叶子和本机非文本块保留明确的本机动作；对端新增消息不会因旧回执被覆盖丢失。云端读取仍以经校验的云端完整文本树更新本机。附件、工具和运行时块不上传，但其后的完整文本子消息会重新挂接到最近的可移植父消息，不能再因结构节点或数组顺序丢失。
- **模型／金额：** 已完成回答的模型和结算金额只允许缺失时从已校验云端记录补齐；既有本机事实不可被后续载荷清除或改写。Android 在把远端新增节点并入本机后先持久化这些事实，再生成下一份上传载荷，避免 Desktop→Android→云端的合并过程中丢掉页脚。
- **未知提交：** `UNKNOWN` 先只读回查，绝不重发原密文；若云端已变化，旧尝试标为 `SUPERSEDED`，随后走“读取－完整合并－新提交”路径，不再将正常跨端更新显示为“冲突／已停止同步”。
- **验证与交付：** Desktop `npm run lint`、`typecheck`、5 个同步 Rust 定向回归（包含远端新增并集、未知提交后的继续合并、附件结构子节点）通过；Android 四组定向 JVM 回归通过，`assembleRelease` 通过。Desktop 新 bundle 已严格验签并原位覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，主程序 SHA-256 `57e6755b2759a330f95a25605d27b655b8b0922187bab0e92da2d2fa8f7d7667`；旧包在 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-sync-union-20260915-0205.app`，覆盖前后 workspace SQLite SHA-256 `6f370022fe3e624fc69fb88bc8f8288c139c5b4373a404a84132e16fdd1532fc`，`quick_check=ok`。Android `app/build/outputs/apk/release/南枫AI.apk` SHA-256 `2dbf4582ceba4cb86a1d82636ea22bbb705dbcfa14e8ffe8456e7e734edd7774`，v2／v3 签名和既有证书通过，已对 OPPO `3B157F009E800000` 保数据覆盖；设备 base APK 哈希一致，首次安装时间仍为 `2026-08-20 15:15:31`。
- **仍须真实账号回读：** 当前 Desktop 旧进程仍在运行，用户正常退出后重新打开才会加载新二进制。随后在同一账号下用既有真实会话完成一次 Desktop 同步、Android 读取云端列表、Android 新增一条完成消息后 Desktop 同步的回读，核对标题、完整文本树、模型和金额；不得记录正文、恢复码、密文或 token。构建、覆盖与模拟网关回归不能代替此真实服务验收。

## 2026-09-14：Android 跨端回答模型／金额 Room 还原与再同步保留（待真实账号回读）

- **根因与边界：** Desktop 的便携 `modelUsage` 故意不携带 Android 本机 Provider 路由或 attempt ID；不能伪造为 `assistant_response_model_attributions`，否则会污染本地调用审计。此前 Android 严格接受该字段，却只恢复消息树，未持久化和投影给回答页脚。
- **实现：** 新增无正文、无服务商路由、无凭据、无 attempt 的 `cloud_response_model_usages` Room 表（67→68）。`P7FConversationSyncWireFormat.decodeWithModelUsage` 只接受 assistant 消息上的完整、非负且费用来源自洽的 `modelUsage`；恢复在会话保存后以会话 + 消息 ID 幂等持久化，冲突拒绝而不覆盖。Android 回答页脚优先本机 Provider 归属，缺失时投影跨端模型及原始金额；再次同步同样优先本机归属、否则原样回传跨端事实。
- **自动验证：** `:app:testDebugUnitTest --tests P7FConversationSyncWireFormatTest --tests P7FManualConversationSyncRoomContractsTest` 通过：协议 `5/5`，Room `3/3`，覆盖严格解析、页脚投影和 67→68 表结构。`git diff --check` 通过。主源码 `:app:compileDebugKotlin` 通过。
- **正式候选：** `app/build/outputs/apk/release/南枫AI.apk`，`66 / 0.3.0-p10j`，SHA-256 `23f6062d075cfdcdba6e788ebd10a9942f983a892de9f6f4cfd42f678a3fbae7`；v2/v3 签名校验通过，证书 SHA-256 仍为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。本轮未覆盖、启动、卸载、清数据或运行任何 `connected*AndroidTest`。
- **仍待真实验收：** 用户使用正式 Desktop 先“读取云端列表”，再以本正式 Android 包保数据覆盖后手动“读取云端列表”；只记录两端数量、置顶集合、顺序、模型／金额是否显示、打开／继续和取消同步仅删除云端副本。不得记录或输出会话正文、恢复码、密文或 access token；在该真实账号回读前，不得称双端同步已打通。

## 2026-09-14：共享云端列表、旧直接记录受控重写与模型金额载荷（待真实账号回读）

- **目标与当前边界：** 目标仍是同一 Google 账号下 Android 与 Desktop 的云端会话数量、置顶和顺序一致；“读取云端列表”只能合并／更新，不能重置已有云端列表或把云端置顶写进本地会话。两端的新正式包已保数据覆盖，但尚未由用户完成本轮真实账号的双端读取回读，因此绝不能称已打通。
- **已确认根因：** 旧实现把各端缓存、各端本地置顶和服务端列表顺序混作同一真相；Desktop 虽已有共享置顶边车，却未接入可见云端操作。旧直接会话还有重试失败分支或重复消息 ID；哈希相同会被提前判为 `UP_TO_DATE`，永远不会重写为当前可读格式。另一个数据丢失点是跨端便携载荷未承载模型、token 与结算金额。
- **本轮实现：** `cloud-conversation-list-v1` 成为唯一共享列表展示记录，只保存置顶 ID；Android 和 Desktop 都提交后回读确认，云端置顶不会触碰 `conversation.pinned`。读取按服务端返回顺序合并，不清空未返回的缓存行；Desktop 在显式“读取云端列表”中，只对当前本机有同账号同 document ID 来源、且确实需要修复的旧直接记录做原 document 覆写，再重新列出，绝不删会话或另建副本。需要重写的旧记录不会再走错误的 `UP_TO_DATE` 早退。
- **模型／金额边界：** 两端便携消息现可携带不含服务商、路由、端点或凭据的 `modelUsage`（模型 ID／显示名、token、币种、金额、版本、来源）。Desktop 已把 Android 载荷归一到可显示的模型／金额；Android 当前只允许并保留该字段用于后续再同步，尚未把 Desktop 传来的元数据写进 Room 并显示。这个 Android 可见还原缺口仍待实现，不能承诺手机端已恢复模型或金额。
- **代码入口：** Android：`app/src/main/java/com/nanzhufeng/ai/data/P7FManualConversationSync.kt`、`P7FConversationSyncWireFormat.kt`；Desktop：`desktop/src-tauri/src/desktop_account_sync_v1.rs`、`desktop/src-tauri/src/lib.rs`、`desktop/src/app.mjs`、`desktop/src/cloud-conversation-list-merge.mjs`。新线程先读这些文件与本交接，不按标题去重、不读取或记录会话正文、恢复码、密文、access token。
- **已验证：** Android 定向 JVM 的 `P7FConversationSyncWireFormatTest`、`P7ASyncContractsTest`、`P7DAccountSyncUiContractsTest`、`P7FManualConversationSyncRoomContractsTest` 通过；Release 已构建，候选 [`南枫AI.apk`](../app/build/outputs/apk/release/%E5%8D%97%E6%9E%ABAI.apk) SHA-256 `b644075a07795929145f6f744a6ca785a5c66ea2d9053d46ff4f1d3398996469`，同证书保数据覆盖 OPPO 后拉回 base APK 与候选一致，未启动 App。Desktop 的 lint、`node --check`、`cargo check`，以及“旧格式重写”和“模型金额载荷”Rust 定向测试通过；候选已严格验签并原位覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`。覆盖前后 workspace SQLite SHA-256 均为 `6ccf391a0fcc8d6e120246e2c65fd9fdd3b3d90af5a5138c7d1c032a40f75798` 且 `quick_check=ok`；上一包为 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-current-sync-20260914-1911.app`。
- **下一步真实验收：** 用户先完全退出并重开 Desktop，进入“云端”点击一次“读取云端列表”；它会在有对应本机来源时受控升级旧直接记录并重新列出。再由用户在手机正式包点击“读取云端列表”。核对两端数量、同一置顶集合、顺序、打开／继续与取消同步仅删云端副本；若手机仍显示不同数量，保留只含 document ID、数量、阶段和异常类的诊断，不输出内容。随后补齐 Android 的 `modelUsage` Room 持久化与 UI 投影，再做同样回读。

## 2026-09-14：双端本地已同步会话云端标识

- **语义：** 仅在“本地”会话列表中，存在当前账号持久同步回执的会话在标题前显示云端图标；云端列表本身不重复标记。该标记只来自账号隔离的 `workspaceId + conversationId` 回执，不改变或复制会话内容。
- **实现与证据：** Android 直接复用 `syncedConversationIds`，Desktop 账号投影新增回执键而非只返回数量；同步、取消同步和云端读取后立即刷新该投影，侧栏保留优化也会在键变化时重绘。Desktop Node 定向 `2/2`、Rust `21 passed / 0 failed / 1 ignored`、Android `P7DAccountSyncUiContractsTest` 和 `assembleRelease` 均通过。
- **产物：** Desktop 已同 Bundle ID 原位覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，严格验签通过，主程序 SHA-256 `4db68aad8c2f76643b2a870cd35d608087cb512d63e157d124b61bdd85f52aee`，上一包保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-cloud-marker-20260914-1600.app`；SQLite `quick_check=ok` 且覆盖前后 SHA-256 一致。Android 正式包为 [`南枫AI.apk`](../app/build/outputs/apk/release/%E5%8D%97%E6%9E%ABAI.apk)，SHA-256 `29741bfbfd3c282b7c8c38582109cbea5a7fe802feafa2ab84f3abe1b8e95795`，本轮未安装或启动主设备。

## 2026-09-14：Desktop 同步误报、全链路锁竞争与侧栏视觉收敛（待重开后真实云端回读）

- **根因与修复：** 已确认的提交在“回读暂时失败／滞后”时被前端当作硬失败；现在保留为 `VERIFYING`，先显示“后台核对”，只读核对成功后显示成功，绝不自动重复上传。同步、取消、核对、云端列表恢复与周期同步不再持有全局 Desktop store 锁等待网络；云端列表改为复用 `list` 回包，不再逐条二次 `read`，同工作区侧栏投影也由每行两次 IPC 降为每工作区两次。
- **视觉收敛：** 可见结构分割线（侧栏拖拽线及设置双栏）统一为 `1px`；批量选择框以高于通用 `.chat-first button` 的选择器强制 `20×20px`，未选中也显示可见浅灰方框，选中为浅橙底＋橙色勾和 `2px` 直角，不再被通用按钮 `min-height` 拉成竖向胶囊。
- **自动与交付证据：** Desktop 同步 Node 回归 `15 passed / 0 failed / 12 retired skipped`，同步 Rust 定向 `21 passed / 0 failed / 1 ignored`，`npm run lint`、`npm run typecheck`、`cargo check` 和已签名 macOS bundle 通过。已同 Bundle ID `com.nanzhufeng.ai.desktop` 原位覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；候选和安装后主程序 SHA-256 为 `044b26a7ed141beb1a9e1a2e5cc944a6091778a5db4b5e1e0594e1929ba395cb`，旧包保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-sync-ui-20260914-1427.app`，用户 SQLite `quick_check=ok` 且覆盖前后数据库 SHA-256 一致。
- **待验收：** 正常退出后重开本包，云端同步一条会话并观察“后台核对／成功”状态不再误报失败；读取多条云端对话时观察首帧和列表恢复耗时。再进入批量编辑与设置双栏，确认方形未选框、浅橙选中态及 `1px` 分割线。

> **14:55 追加正式包：** 因上一包的批量框仍被通用按钮最小高度拉成长方形，已在选择器层级修复并新增“空选择仍输出 `aria-checked=false` 方框”回归；悬停和按压也不会再移除未选框边框。定向回归、`npm run lint`、构建与签名通过；已原位覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，主程序 SHA-256 为 `8725f311023a0703a64e2f49dcf4442a207cb6e960ebf8a61f926e003ec90cc4`，上一包可恢复于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-square-checkbox-hover-20260914-1455.app`，SQLite `quick_check=ok` 且覆盖前后 SHA-256 一致。须正常退出正在运行的旧进程再打开。

## 2026-09-14：Desktop 云端读取增量合并修复（待重开后实机回读）

- **根因：** “读取云端列表”把读取结果直接赋值给 `state.cloudConversations`，这会把当前列表整表替换，丢失云端列表独有的 `cloudPinned` 与未出现在本次读取结果中的现有条目。重复点击时，较早请求的迟到回包还可能覆盖较新的读取、取消同步或退出账号状态。
- **修复：** 读取现在按 `workspaceId + conversationId` 合并：同一条刷新云端字段但保留已有云端置顶和位置；新条目追加；已有条目不会因一次读取消失。读取代次防止旧回包覆盖新读取、明确取消同步、批量移除或退出账号后的列表。
- **证据与交付：** 新增可执行合并回归，先红后绿，断言“已置顶 + 仅缓存条目 + 新云端条目”合并后仍为原顺序，置顶不丢失；云端置顶、读取和侧栏合同回归 `3/3` 通过，`npm run lint`、构建、打包、严格验签通过。已同 Bundle ID 原位覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，候选和安装后主程序 SHA-256 均为 `bf77c7154ad7734ee32049ccd1df95f7db610702522e5e0535083b0b511b47ec`；旧包保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-cloud-merge-20260914-1430.app`，SQLite `quick_check=ok`。
- **待验收：** 正常退出后重开本包，在云端先置顶一条、再点击读取，确认该条仍在“置顶”，其余现有行不消失，新云端行仅追加。

## 2026-09-14：Desktop 侧栏工具按钮真圆修复（待重开后截图验收）

- **根因与修复：** 侧栏的通用 `.chat-first button` 设置了 `min-height: 34px`，其选择器优先级高于工具按钮的 `26px` 声明，导致手写笔和云端读取按钮被纵向拉成椭圆。工具按钮现在以同等优先级强制 `26 × 26px` 的最小／最大尺寸、零内边距和 `50%` 圆角；白底、图标居中和既有柔和阴影保持不变。
- **证据与交付：** 该视觉合同先红后绿；与云端置顶及批量编辑回归共 `3/3` 通过，`npm run lint`、`npm run build && npm run bundle:macos`、候选和安装包的严格签名校验均通过。已同 Bundle ID 原位覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，候选和安装后主程序 SHA-256 均为 `85903fbdb9662fe6c30e72760267e442dd1dd85f6fa0fd88aefed9359ddb9698`；旧包保留在 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-true-circle-20260914-1400.app`，SQLite `quick_check=ok`。
- **待验收：** 安装时旧 Desktop 进程仍在运行，必须正常退出后重新打开，才会加载这一包；之后只需回看右侧两个按钮是否为相同直径的白色圆形。

## 2026-09-14：Desktop 云端置顶误写本地修复（待重开后可见验收）

- **已确认根因：** 云端行复用了“恢复到本机工作区”的对象；旧置顶路径先执行 `mutate_desktop_domain(setPinned)`，再把该本机内容同步回云端。因此在云端列表点置顶，实际先改了本地会话。截图中本地“置顶”出现 KFK 行而没有可见取消入口，是同一问题的可见后果。
- **修复：** 云端列表置顶改为账号隔离的云端列表展示状态，缓存只保留 `workspaceId`、`conversationId` 与 `cloudPinned`，绝不读取或写入恢复工作区的 `conversation.pinned`。云端读取／重开仍保留该云端列表顺序；本地列表继续只看自己的 `conversation.pinned`。任意已置顶行的操作区现常显，直接提供“取消置顶”，不再依赖鼠标悬停。
- **证据与交付：** 新增“cloud-list pin is a presentation action and never mutates the local conversation”回归：先红后绿；连同云端行、批量编辑合同为 `3/3` 通过，`npm run lint` 通过。Desktop 已重新构建、签名校验并原位覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；候选与安装后主程序 SHA-256 均为 `6df41539062f1d0904bb221e20a7ba4d1b7af8b5e70c5f0485456f1225746e45`，同 Bundle ID `com.nanzhufeng.ai.desktop`。原包保留在 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-cloud-pin-20260914-1325.app`。覆盖未改写数据库、会话或云端文档。
- **待验收：** 当前正在运行的是覆盖前的旧 Desktop 进程；用户正常退出再打开后，在云端点置顶／取消置顶，确认本地列表不再变化，并确认本地置顶行右侧立即可点“取消置顶”。

## 2026-09-14：直接同步角色兼容、Desktop 云端操作与正式原位更新（待用户可见验收）

- **Android 根因与修复：** Desktop 直接同步写出的消息角色是小写 `user`／`assistant`，Android 解码曾只接受 Kotlin 枚举的大写名称，因而完整性通过后仍在恢复阶段失败。`P7FConversationSyncWireFormat` 现显式接收两端的小写协议角色并保留未知角色的安全拒绝；不降低任何内容哈希或载荷校验门。
- **Desktop 交互修复：** 云端行的置顶、收藏、取消同步和批量删除现在在操作后恢复操作前的本地／云端列表、滚动位置和当前工作区；只有用户点击云端行本身才打开该会话。批量编辑操作栏固定在侧栏底部，行选框即时呈现；手写笔与云端读取按钮统一为浅白圆底和两层柔和阴影。同步点击先在下一帧显示“正在同步到南枫云…”，原生回执后显示“同步成功，已回读校验”或“云端已是最新”，不再因前置重复读账号状态阻塞首帧。
- **自动与交付证据：** Android `P7FConversationSyncWireFormatTest`、`P7ASyncContractsTest`、`P7DAccountSyncUiContractsTest` 定向通过；Desktop 侧栏批量合同、云端操作不跳转合同和同步反馈合同定向通过，`npm run lint` 通过。Desktop `npm run build && npm run bundle:macos` 成功；候选及已装应用均为 Bundle ID `com.nanzhufeng.ai.desktop`、Team `457B263L9J`，严格验签通过。已将新包原位更新至 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-sync-ui-20260914-1255.app`。更新前后用户 SQLite `quick_check=ok`，工作区 `1`、聊天尝试 `16`、已选同步记录 `7`、同步通知 `18`，未启动或改写数据。
- **Desktop 真实回读（2026-09-14）：** 已从刚原位更新的正式包正常启动；初始短暂空列表后既有本地历史恢复。真实账号“读取云端列表”后显示 1 条置顶加 5 条最近云端会话，当前本地会话未被跳转；直接打开其中一条后正文、模型及可编辑输入框均可用。云端批量编辑即时显示每行选框和固定在左下的“全选／删除／完成”操作栏，退出后仍停留云端列表。读取前后 SQLite `quick_check=ok`，工作区 `1`、聊天尝试 `16`、已选同步记录 `7`、同步通知 `18`。本轮没有删除、上传或改写云端对话。
- **仍待真实验收：** Android 正式包需由用户手动执行读取云端列表、直接打开／继续和取消同步保留本地。Desktop 的“取消同步只删除云端副本”已有上一轮真实回读证据；本轮不重复删除用户云端数据。没有 Android 真实账号回读，不得称“同步彻底打通”。

## 2026-09-14：Android 直接同步旧记录迁移提示修复（待用户手动验收）

- **已确认根因：** 手机上的“该对话正在由原设备迁移为直接同步，请稍后刷新列表。”并不代表存在迁移任务、进度或自动重试。Android 把不能以直接格式打开的记录统一伪装成该文案；同时旧的、已经手动同步且本机仍有来源对话的回执，即使远端仍为旧格式，也会被错误当作“内容未变”而跳过重写。
- **本轮修复：** 用户点“读取云端列表”时，只先迁移当前账号已存在回执、且本机仍保留源对话的旧格式记录；不扫描、上传或删除其他本地对话，缺少源对话或无关联云端记录也不处理。同步 owner 只有远端已为直接格式且本机内容哈希一致时才可判定无需写入。格式/完整性不通过的直接记录改为诚实提示“云端对话完整性校验未通过，本机内容未改动”，移除没有完成条件的“稍后刷新”。
- **自动与正式包证据：** `P7DAccountSyncUiContractsTest` 为 12/12 通过；`assembleRelease`（含 `lintVitalRelease`）通过，`git diff --check` 通过。新候选为 `app/build/outputs/apk/release/南枫AI.apk`，包 `com.nanzhufeng.ai`、`66 / 0.3.0-p10j`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。已与 OPPO 已装包逐项核对后执行 `pm install -r --user 0`；覆盖后 `ceDataInode=1459104`、首次安装时间不变。未卸载、清数据、启动 App 或运行任何 `connected*AndroidTest`。
- **仍待真实验收：** 用户手动启动这次覆盖后的正式包，在“Google 账号与同步”点“读取云端列表”，确认不再显示旧迁移等待文案，并继续验证云端页、原对话打开/继续和“取消同步”仅移除云端副本。本轮尚未读取、输出或保存会话正文、恢复码、密文或 access token。

## 2026-09-14：Desktop → Android 直接同步内容哈希兼容修复（待用户手动验收）

- **已确认代码层根因：** Desktop 的直接同步用 `serde_json` 输出字符串，Android 却使用 `JSONObject.quote` 重算内容哈希；后者会额外转义 `</` 及 `U+2000..U+20FF`。因此 Desktop 已提交、服务端原样返回的直接信封可在 Android 预检通过，却会在打开时因 payload 字节数／哈希不一致被拒绝。截图中的“云端对话完整性校验未通过，本机内容未改动。”正是这个保护门生效，不能靠重试绕过。
- **本轮修复：** 直接同步的 Android 新写入和读取改用与 Desktop `serde_json` 一致的 JSON 字符串规范化；读取同时只在旧 Android 自己的历史字节数与哈希精确匹配时兼容旧拼写，绝不跳过完整性校验。新增回归覆盖 Desktop 风格的 `</script>` 与 Unicode 分隔符信封。
- **自动与正式包证据：** `P7ASyncContractsTest`（含新跨端字符串边界）与 `P7DAccountSyncUiContractsTest` 定向通过；`assembleRelease`（含 `lintVitalRelease`）通过。正式包再次验签为 `com.nanzhufeng.ai`、`66 / 0.3.0-p10j`、证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，并已用 `pm install -r --user 0` 同签名保数据覆盖 OPPO。覆盖后 `ceDataInode=1459104` 和首次安装时间不变；未启动 App、未卸载或清数据。
- **仍待真实验收：** 覆盖会使 Android 回到桌面，必须由用户手动打开正式包后点“读取云端列表”。只有该真实账号链路读到原会话并继续／取消同步保留本地后，才可称同步闭环。

## 2026-09-14：双端直接云同步与侧栏统一交接（进行中，切换新线程）

- **当前目标与完成定义：** 用户要求“彻底打通同步功能才能结束”。完成不能用构建或静态测试替代：Desktop 与 Android 都必须在正式包中保留本地会话、读取同一账号的云端列表、直接打开/继续原始会话、云端取消同步只移除云端副本，并由真实设备与账号链路分别证明。
- **本轮实现：** Android/ Desktop 的云端列表均改为原始本地会话的筛选投影，读取完成后直接显示云端页，不再出现“恢复为新工作区”等中间操作。云端行复用本地的打开、继续、编辑、置顶、收藏、删除、日期和批量删除；唯一差异是云端菜单提供“取消同步”，并保留本地对话。同步载荷保留置顶、归档与收藏，Qwen/智谱等末尾未完成回复不会覆盖此前 `COMPLETE` 对话前缀；列表缓存按账号持久化，读取过一次后重开仍显示。30 秒的变更合并同步与 12 小时后备同步均已接入。
- **界面合同：** 两端侧栏中“本地/云端”居中并占左栏三分之一；手写笔（批量删除）和读取云端图标固定在最右、使用浅白圆形底并一起缩小；“已置顶/最近”标题保持正常尺寸。设置“恢复与安全”严格保留手机端样式的两个大胶囊：`更换恢复码 / 已丢失`、带云下载图标的`读取云端列表`，删除其余解释性小字，仅保留最近同步时间。
- **当前正式产物与回滚：** Android release 为 [`南枫AI.apk`](../app/build/outputs/apk/release/%E5%8D%97%E6%9E%ABAI.apk)，包 `com.nanzhufeng.ai`，`66 / 0.3.0-p10j`，证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。Desktop 候选已经以同 Bundle ID `com.nanzhufeng.ai.desktop`、相同 Apple Development Team `457B263L9J` 原位覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，覆盖前版本完整保留在 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-cloud-sidebar-compact-20260914-0110.app`；覆盖后 SQLite `quick_check`/`integrity_check` 均为 `ok`。运行中的旧 Desktop 进程须正常退出再打开，才能对最新紧凑侧栏做像素级回读。
- **本轮自动证据：** Android `P7DAccountSyncUiContractsTest` 与 Release `assembleRelease`（含 `lintVitalRelease`）通过；Desktop `desktop-account-sync.test.mjs` 为 13 passed / 0 failed / 12 retired skipped；Desktop bundle 已 `codesign --verify --deep --strict` 通过。`git diff --check` 对本轮同步/侧栏文件通过。
- **真实 Desktop 证据：** 已用应用内“读取云端列表”实际读取当前已登录账号，云端页切换成功并显示 6 条云端会话。SQLite 回执核对显示它们为 6 个不同 `conversation_id`（标题相同不等于同一对话），因此不得按标题去重、合并或删除。该验证没有上传、删除或改写任何云端文档。
- **未完成 / 风险：** OPPO `3B157F009E800000` 当前不在 `adb devices -l` 中（仅有 `emulator-5592`，严禁对模拟器安装）。已创建心跳自动化 `oppo`：OPPO 重连为 `device` 后，先核对候选与设备包名/版本/签名，再只做同签名保数据覆盖安装；禁止卸载、清数据、启动应用或任何 `connected*AndroidTest`。Android 真机读取云端列表、直接打开/继续和取消同步保留本地的可见验收仍待设备重连完成。真实 Desktop 云端读取已通过，但需重开刚覆盖的包后复核紧凑侧栏视觉。
- **2026-09-14 后续实测（Desktop 取消同步已闭环，Android 未闭环）：** OPPO 已重连为 `device`。候选 APK 与已装包均为 `com.nanzhufeng.ai` `66 / 0.3.0-p10j`，并由本地与设备 base APK 的 `apksigner` 回读确认同一 SHA-256 证书 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8` 后，执行 `pm install -r --user 0` 成功。覆盖前后 `ceDataInode=1459104`、首次安装时间不变、`stopped=false`；临时 APK 已清理；未启动 Android App，未运行任何 `connected*AndroidTest`。Desktop 已正常退出旧进程并从已覆盖包重新启动；短暂空侧栏后，SQLite `quick_check=ok` 的既有本地会话恢复可见。应用内再次真实读取当前账号云端列表，显示 6 个同标题但独立的云端行，未按标题合并；直接打开其中一条后仍可在本地侧栏和原会话中继续。随后对该条执行“取消同步”：本地 SQLite 仍为 `855` 个会话、`5,158` 条消息且 `integrity_check=ok`，已选同步回执由 `7` 降为 `6`；重新读取真实云端列表为 `5` 条，证明仅移除了该云端副本，其他 `5` 条未受影响。尚未完成：Android 可见链路仍受“不可启动主设备 App”门禁限制，需由用户手动启动已覆盖的正式包后再验收读取、直接打开/继续与取消同步保本地。
- **下一线程第一步：** 先运行 `adb devices -l`，仅在 `3B157F009E800000` 显示为 `device` 时执行正式 APK 的同签名保数据覆盖；同时正常重开已覆盖的 Desktop，通过“云端”页验证居中三分之一胶囊、右侧两个圆形图标及直接打开原对话。不得读取、输出或保存恢复码、会话正文、密文或 access token。

## 2026-09-13：南枫云真实提交 400 的生产根因修复与双端保数据更新

- **根因与生产修复：** 已认证 Desktop 的真实加密提交此前稳定返回 PostgreSQL `2201B`。根因是云端 envelope 校验函数把密文长度写入单个正则重复范围，超出 PostgreSQL 正则引擎支持的上限；这与 Google 登录、恢复码、网络或 Android 代码无关。P7-G 已在生产 SQL Editor 成功部署：密文校验改为固定字符集正则加独立长度判断，并已刷新 PostgREST schema。迁移不读取、记录或展示任何恢复材料、会话正文或密文。
- **真实端到端证据：** 部署后，以 Desktop 已登录的应用私有会话对一条已由用户选择同步的对话运行实际提交路径，完成加密提交、受认证云端回读 hash/revision 校验和本机同步回执写入；复跑同一受控探针确认回执保留。此前错误状态不再被伪装成成功。
- **客户端防回归：** Desktop 将所有 4xx 服务体仅在内存中收敛为已定义协议哨兵或受限错误码，绝不写入日志、诊断或用户提示；`2201B` 也有明确的安全状态。提交失败文案与读取失败文案分开，避免将提交问题错误写成“读取”。P7-G 静态迁移 4/4、Desktop 同步前端 17/17、Rust 账户同步 19/19 通过。
- **覆盖与边界：** Desktop 当前正式包已同 Bundle ID/同开发团队验签后保数据覆盖，旧包保留为 `南枫 AI Desktop.pre-sync-validator-20260913-1741.app`，新包已原生启动。Android 正式包继续为 `com.nanzhufeng.ai` 66 / `0.3.0-p10j`，已在 OPPO 做同签名保数据覆盖；未卸载、清数据、启动设备 App 或运行任何仪器测试。下一项且仅剩的真实端侧验收，是用户在 Android 的“读取云端列表”中读到该条目并按自身恢复保护完成恢复；不得代填或记录恢复码。

## 2026-09-13：南枫云同步生产基础层补齐与 Desktop 真实只读回读（未写入用户云端数据）

- **真实根因：** 目标生产项目此前只存在 P7-F 的 `nanfeng_sync_list_documents`，却漏掉 P7-C 的两张加密同步表及四个基础读写 RPC。列表函数没有可用底座，PostgREST 不能暴露完整同步能力，Desktop 因而持续显示“南枫云同步服务暂不可用”。这不是手机端代码、模型、macOS Keychain 或恢复码错误。
- **生产修复：** 已按顺序部署 P7-C 基础迁移、P7-F 列表迁移，并刷新 PostgREST schema。生产库只读核验显示 `read_account_key`、`put_account_key`、`read_document`、`commit_document`、`list_documents` 五个 RPC 均存在，且每个只授予 `authenticated`；直接表访问仍为默认拒绝。未读取、输出或改写任何用户密文、恢复码或对话正文。
- **客户端真实验证：** Desktop 在应用内完成 Google/Supabase 会话后，点击“读取云端列表”已由旧的服务不可用转为“当前账号没有可恢复的云端对话”。这证明受登录态保护的真实列表 RPC 已成功执行；当前账号尚无手机端手动同步上传的可恢复对话，故没有进入恢复码/导入步骤。
- **空云端迁移补齐：** Keychain 退役后若旧确认标记仍在、应用私有恢复材料不存在且云端确实为空，Desktop 过去只有“输入已有恢复码”死路。现在候选包在真实登录态下会再次读取云端列表，并且仅在云端为空、本机无已选同步回执、无恢复码轮换时开放“建立新的恢复码”；它不会读取 Keychain、改写任何对话或上传数据。新命令已纳入最小 Tauri ACL。真实候选端已验证：读空列表 → 自举 → 显示“创建恢复码”。
- **当前真实状态：** 已生成一次性恢复码，桌面 SQLite 仍为 `AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION` / `recovery_confirmed=0`；没有伪装成完成、没有写入任何用户云端数据。必须由用户实际保存该码并在应用中点击确认，才能安全生成应用私有材料、选择一条本机对话并上传；之后 Android 以同一恢复码登录读取并恢复。Android 针对 P7-C RPC、P7-F 已选对话同步与 Room owner 的 JVM 回归本次通过；无需为已部署的 RPC 基础层另改 Android 源码。
- **防回归：** `p7e-supabase-readiness.sh` 现在明确检查 P7-C 基础迁移、P7-F 列表迁移及五个 RPC 合同是否齐全；Runbook 禁止“只部署列表接口”，要求部署后回读五个 authenticated RPC。

## 2026-09-13：Desktop 账户同步凭据改为应用私有存储（候选包已启动，未覆盖安装）

- **根因：** Provider API Key 已迁入应用私有存储，但 Google 会话、恢复包装材料和同步数据密钥仍由 macOS Keychain 适配器保存；因此云端读取可弹出系统“登录”钥匙串密码框。这既违背了“软件自身管理凭据”的产品边界，也会把同步可用性错误地依赖到系统授权 UI。
- **修复：** 删除账户同步与 P7-B 状态路径中的 Keychain 调用和依赖；新增 `account-sync-credentials-v2` 应用私有 AES-GCM 存储，安装本地密钥与加密记录均在 Desktop 私有根目录，权限收紧为目录 `0700`、文件 `0600`。会话刷新、云端列表、同步提交、恢复码和定期同步均改走同一私有 owner。
- **迁移边界：** 为保证新包绝不读取系统钥匙串，旧的 Keychain 会话不会被迁移或探测；已经存在于应用私有目录的会话可继续使用，私有目录不存在或无有效会话时才需在应用内重新完成 Google 登录。不会请求 macOS 钥匙串密码，也不会把新会话、恢复材料或数据密钥写入钥匙串。
- **验证：** 私有存储加密回归、Rust library `259/259`、Desktop Node `389/389`、lint、`cargo check` 和 `git diff --check` 通过；候选包严格验签并已原生启动，未出现钥匙串授权弹窗。未进行云端写入；重新登录后才可继续做真实只读云端列表验证。

## 2026-09-13：Desktop 会话标题区渐隐与品牌副标题收敛（未覆盖安装）

- **标题区：** 顶部正文渐隐从 `112px / 40%` 调整为 `148px / 46%`，并加重中段不透明度；滚动正文接近标题区时会平滑淡出，标题栏和右侧操作仍位于独立可点击层，未采用会遮挡交互的实心蒙层。
- **标题文案：** 已移除当前会话标题上方冗余的“南枫 AI”品牌副标题，仅保留实际会话标题，不影响侧栏品牌、搜索页、设置页或应用名称。
- **验证：** Desktop Node `389/389`、lint 与 `git diff --check` 通过。未覆盖安装；需要由新包的原生窗口确认最终观感。

## 2026-09-13：Desktop 回答联网事实与上下文来源去重（未覆盖安装）

- **根因：** “本次回答信息”把上下文来源审计误当作联网事实，并且投影中根本没有 `webSearch` 字段；即使同一 Answer 已有完成 Attempt，前端也会把 `undefined` 显示成“未记录（旧回答）”。来源列表又逐项平铺同类审计数据，默认标题相同的多条 Memory 因而看起来像重复记忆。
- **修复：** Attempt 新增 `web_search_verified` 迁移字段；`web_search_route` 持久化“是否请求联网”，响应中有服务商工具／安全来源证据才持久化“已实际联网”。弹窗现在明确区分“本次未启用联网”“已请求联网（服务商未返回核验依据）”和“已实际联网（服务商返回核验依据）”，不再用“旧回答”掩盖投影缺字段。开启联网但服务商没有返还任何可核验工具／来源证据的回复会以 `WEB_SEARCH_NO_SOURCES` 失败，而不会保存为无法核验的成功回答。
- **来源收敛：** 弹窗按来源类别合并同名条目；同标题同正文的 Memory／资料库在请求组装阶段只进入一次，避免同步重试或历史导入的不同 ID 重复消耗上下文。不同正文的资料仍保留，只以一条类别行展示其标题。
- **验证：** Desktop Node `389/389`、普通聊天 Rust 定向 `27/27`、`cargo check`、lint 与 `git diff --check` 通过。未触发真实 Provider、未读取用户正文、未覆盖安装；原生真实联网／来源证据仍须在新包手动确认。

## 2026-09-13：Desktop 图片预览触控板切换、缩放与回闪修复（未覆盖安装）

- **已证实根因：** 图片预览每次前后切换都会先把 `imagePreview` 替换为 loading 对象并重绘，已显示图片被同步卸下，原图读取／解码完成后才重新插入，因而出现回闪。横滑手势的 `lockUntil` 一旦设置即永久吞掉连续事件，且略带纵向分量的横滑会落入通用 wheel 缩放，分别造成“几次没有反应”和“横滑误放大”。
- **修复：** 切图改为保留当前已解码画面，在后台读取、预解码下一张后才原子替换；相邻图以最多两张的有界缓存预热，快速反向或连续切换会复用同一读取结果，不复制整组图片。触控板以单一手势 owner 判定轴向、一次手势只切一张、空闲后恢复下一次切换；普通双指滚动不再缩放，只有 macOS 的 Ctrl-wheel pinch 可以缩放。相邻原图失败时保留当前画面并显示错误，不清空预览。
- **验证：** 新增横滑恢复、斜向横滑不进入缩放、pinch 放行的 Node 回归；定向 `3/3`、Desktop Node 全量 `386/386`、lint、typecheck、静态 build 与 `git diff --check` 通过。本项尚未覆盖安装或在原生 WebKit 进行人工触控板验收；不会以静态验证代替该步骤。
- **预览导航统一：** Android 图片预览新增与顶栏同一材质的 `52dp` 圆形上一项／下一项控件及当前位置；PDF 前后页也改复用该控件。Desktop 图片预览同步提高为 `52px` 高对比圆形、完整 hover／focus／disabled 状态。仅处理媒体预览中的分页切换，列表行尾“进入”箭头维持轻量导航语义。新增 Desktop 视觉合同 `4/4`、Android `P6F2BImagePreviewUiContractsTest 10/10` 通过；未覆盖安装。
- **图片组选中态：** Desktop 回答图片缩略图此前被通用 `button:hover` 规则清掉主题色边框，悬停时视觉上像取消选择。现将选中态自身设为普通、hover、focus 的单一 outline owner，始终保留主题色 `2px` 线框、轻底色与焦点可见性；另以 `1px` 轻上浮及低透明暖色投影表示激活，未选缩略图仍只显示轻量 hover。`fb-p6-080` 定向 `3/3`、lint、typecheck、静态 build 与 `git diff --check` 通过；未覆盖安装。

## 2026-09-13：Desktop 搜索结果精确附件定位修复（未覆盖安装）

- **双端主界面反向定位补充：** 从会话内附件“搜索定位”进入搜索页时，Desktop 旧代码会把附件名写进关键词并切换至对应媒体类别，Android 则切换至对应媒体类别；两者都会把完整搜索页错误收窄。现统一为清空关键词、进入“全部”目录，保留普通分页结果；Desktop 会按 `workspace + conversation + message + attachment` 精确扫描后续页，找到目标才居中滚动并以主题色短暂闪烁，Android 同样按已有三元锚点在完整目录内滚动并闪烁。目标已从索引删除时保留完整结果且给出诚实提示，绝不伪造单条筛选结果。
- **回归补充：** Desktop 新增主界面附件反向定位合同回归，Node `386/386`、lint、typecheck、静态 build 通过；Android `MainAttachmentSearchLocateUiContractsTest` 与 `ConversationSearchSurfaceContractsTest` JVM `11/11` 通过。未覆盖安装，未改写真实会话、索引或附件。

- **已证实根因：** 本地搜索索引本来就为每条附件结果持久化了精确 `message_id + attachment_id`；当前工作区索引实际有图片 1,481、视频 66、音频 7、文件 128 条且均带附件 ID。旧前端却只以 `messageId` 查找并滚动 `.chat-message`，完全丢弃 `attachmentId`；同一消息含多张图片或多个附件时，因此只能回到笼统消息位置。
- **修复：** 新增单一锚点 owner，在所属消息内按精确附件 ID 选择目标，滚动与闪烁都施加到该附件卡；文本命中仍定位消息本身，附件在定位前已被删除时也只安全回退至所属消息。回答图片组会先选择检索命中的那张图片，再执行滚动，避免回到同组默认首图。图片、视频、音频、PDF、ZIP 与其他文件共用这一附件锚点，不再各自实现。
- **回归：** 附件精确命中、文本/已删除附件回退、图片组选择、静态构建 import 图均有回归；定向 28/28、Desktop Node 385/385、lint、typecheck、静态 build 与 `git diff --check` 通过。未改写索引、附件、会话或已安装包；真实 UI 点击验收待新包覆盖后以既有图片附件确认。

## 2026-09-13：Desktop 大视频首帧与媒体分段读取修复（未覆盖安装）

- **已证实根因：** 旧 `read_desktop_video_preview` 在每次打开时持有可变 `DesktopWorkspaceStore`，完整读取并 SHA-256 扫描整个 MP4，再解析时长；`nfai-media` 协议遇到无 `Range` 的 WebKit 首次探测还会读入整段视频。真实工作区中单个 MP4 可达约 76 MiB，因此点击到首帧会被重复全文件 I/O、内存复制和 store 锁竞争拖慢，而非播放控件或网络问题。
- **修复：** 视频打开改为独立只读 SQLite 连接，仅校验工作区归属、摘要形状、私有文件精确长度及最多 `64 KiB` 的 ISO-BMFF 头；导入时的完整摘要校验仍是私有副本入库门。时长不在头部的 tail-moov MP4 不再等待全文件扫描，由 WebKit 的分段 metadata 读取回填。媒体 URI 协议也直接读取不可变 `store_paths`，不再抢可变锁；无 `Range` 的 GET 返回首个最大 `2 MiB` 的 `206` 分段，后续数据仍由播放器按需读取，绝不再整段复制。
- **回归：** 新增视频首帧性能边界测试；Desktop Node `382/382`、Rust library `255/255`、lint、typecheck 与 `git diff --check` 通过。未改写数据库、附件或已安装 Desktop 包，亦未对用户真实视频执行播放操作；实际硬件解码首帧仍需以新包在用户视频上确认。

## 2026-09-13：Desktop 发送回执路由、应用私有模型凭据与保数据覆盖

- **并发路由修复：** 新会话发送不再根据“同一工作区里任意 `PARTIAL` 会话”猜测选中目标。前端为每次 Send 创建无正文的 `clientSubmissionId`，Rust 只把该回执随同同一次 Attempt 的运行时事件回传；只有工作区、会话和回执三者都匹配，前端才首次选中该会话并滚至最新。后续流式、计费和标题事件不能再次触发滚动；轮询只刷新持久化投影，不再参与路由认领。
- **模型凭据统一：** 已显示或未保存的 API Key 离开“模型配置”页面即从前端状态抹除。Provider API Key 的唯一实际 owner 是应用私有 AES-GCM 加密存储；历史系统凭据兼容类型永久 fail-closed，已移除其 Keychain 读取／写入／授权代码，不存在 Provider Key 的系统授权回退。
- **自动验证：** Desktop lint、typecheck、Node `380/380`、Rust library `255/255` 通过，包含“错误回执不可劫持新会话”“同一回执只滚动一次”“离开设置页清空密钥草稿”和“退役系统凭据入口拒绝”的回归；`git diff --check` 通过。未发起真实 Provider、Google/Supabase 云同步请求或读取密钥。
- **Desktop 覆盖：** 候选与安装后主程序 SHA-256 均为 `1605e1fa1a190bd600b6cc959394a517342d39b6b2e6c2d6f7e3196cef343c31`；Bundle ID `com.nanzhufeng.ai.desktop`、版本 `0.6.0-p6d-dev`、Team `457B263L9J` 严格验签通过。覆盖前、覆盖后数据库 `quick_check`／`integrity_check=ok`、87 表、1 工作区、7,322 搜索索引；既有原生会话与 Composer 已回读。被覆盖包保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-submission-receipt-entropy-20260913-1345.app`；未触碰 Android。

## 2026-09-13：Desktop 自动会话标题与 Android 规则对齐修复与保数据覆盖

- **真实证据：** 用户新建会话的标题调用账本显示 DeepSeek `deepseek-flash` 已成功返回并计入 `2,463/256` Token，却被记成笼统的 `TITLE_NOT_APPLIED`，会话仍为“新对话”且 `autoTitlePending=true`。该旧状态证明不是模型未调用、不是标题合同不一致；旧 `.ok()` 链把本地提交失败、合同不合格与用户手动改名全部吞成同一个结果，无法诊断或恢复。
- **修复：** Desktop 写连接改为 WAL，使前端运行时快照不再阻塞标题写入；标题提交以 `IMMEDIATE` 事务重读会话并原子保存。它沿用 Android 的首条 root user + 首条完整 assistant 成对资格、日常 DeepSeek V4.1 Flash→GLM 5.3 Flash→Qwen 3.6 Flash 的固定后台路由、手动改名优先和失败保留 pending 规则。标题提交同步更新语义 hash 与本地搜索索引，避免界面、搜索和跨端交换版本分叉。
- **诊断语义：** 只有 `TITLE_MANUAL_OVERRIDE` 才表示用户改名优先；服务商 JSON 不符合合同为 `TITLE_CONTRACT`；本机写入异常为 `TITLE_LOCAL_PERSISTENCE`。不再把三者伪装为“未应用”。旧请求正文／返回标题不落审计库，因此已经被旧包丢弃的那一次不能凭空恢复；pending 保留，下一次符合 Android 时机的完整回复会按同一规则再次生成。
- **回归与覆盖：** Desktop Node 378/378、Rust `cargo test --lib` 253/253、标题定向持久化／WAL／语义 hash／搜索索引回归、lint、typecheck、静态 build 与 `git diff --check` 通过。候选和安装后主程序 SHA-256 均为 `d232c1c5e47537d83596782fb3624e4722b8611f51b0eba9e531fbcf771e11e2`；Bundle ID `com.nanzhufeng.ai.desktop`、版本 `0.6.0-p6d-dev`、Team `457B263L9J` 严格验签通过。旧包保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-title-wal-20260913-1242.app`。覆盖前数据库为 rollback journal，启动新包后实测 `journal_mode=wal`；`quick_check`／`integrity_check=ok`、87 张表、1 工作区、7,316 条搜索索引，原生窗口已回读既有对话与 Composer。未触发新的真实 Provider 请求。

## 2026-09-13：Desktop 新对话草稿单 owner 修复与保数据覆盖

- **根因与新方案：** “新对话”动作虽先清空可见输入，但另一条旧事件监听器会在同次点击结束前从 WebView `localStorage` 的通用 `new` 草稿键重新写回文本，形成依赖监听顺序的回填竞态。现在移除 Composer 草稿的浏览器缓存读写与迁移回读；SQLite 是唯一草稿 owner。新对话由单一路由同步清空未绑定 SQLite 草稿，只有选择既有会话才异步恢复该会话的 SQLite 草稿，并以 route generation 丢弃迟到结果。
- **回归：** 先以旧源码观察“单 owner／无浏览器缓存”断言失败，再完成修复；Desktop lint、typecheck、Node 378/378、静态 build 与 `git diff --check` 通过。发送路径同步移除了已废弃浏览器草稿键的引用，避免成功提交后抛出 `ReferenceError`。
- **Desktop 覆盖：** 候选和安装后主程序 SHA-256 均为 `ba02b3b0f5654aebf3ea914662706587246893156b0e536f58249214c6d2e826`，Bundle ID `com.nanzhufeng.ai.desktop`、版本 `0.6.0-p6d-dev`、Team `457B263L9J` 与旧包一致，严格验签通过。覆盖前后 workspace SQLite SHA-256 均为 `e5efe43fc5ee94d3fde7bbe9f9e892f17152c4bc57a1e6b4d36c83a83e24d907`；启动后 `quick_check`／`integrity_check=ok`、87 张表、1 个工作区、7,316 条搜索索引。旧包保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-native-draft-owner-20260913-1315.app`。
- **未替代：** 为保护现有真实草稿，本轮未在正式数据上代用户点击“新对话”；需要由用户手动点击一次确认输入框为空。该缺口不由自动测试、验签或启动回读替代。

## 2026-09-13：流式会话投影、最终回归与双端保数据覆盖

- **Android 流式修复：** 正常聊天前台服务现在把执行器的 `onStreamProgress` 透传为仅含会话 ID／状态的节流进度事件；ViewModel 对当前选中会话只投影已持久化的 transcript、runtime、lineage 与 attribution，不再每个增量完整重载抽屉、设置、模型目录和历史。完整重载与流式投影共享同一代际栅栏，避免旧重载覆盖新文本；终态仍做一次完整刷新。这样与 Desktop 的逐字显示保持同一可见行为，同时不把正文放进广播。
- **最终回归：** Android `:app:testDebugUnitTest :app:lintVitalRelease :app:assembleRelease` 通过；JVM 为 1,133 项、0 failures、0 errors、3 skipped。Desktop lint、typecheck、Node 378/378、Rust `cargo test` 253/253 及协议 golden 均通过；Desktop inventory 没有未处理渲染动作、未实现 Rust command 或未注册 command。91 个动作、41 个 invoke 尚无直接测试引用，属于覆盖缺口而非本轮观察到的功能故障。
- **本地 checkpoint 证据：** 跟踪文件／历史清单固化于 [review/20260913-final-checkpoint](review/20260913-final-checkpoint/)，其范围只涵盖 Git 跟踪文件和本地历史，不读取用户正文、凭据或未跟踪资产。
- **Android 覆盖：** 正式候选为 `com.nanzhufeng.ai` 66／`0.3.0-p10j`，v2／v3 验签有效、证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`、APK SHA-256 `e6807858b472ce395325729d37f484bc453ba61e14ff65f4a7b10ef74b236df8`。OPPO `3B157F009E800000` 只执行一次 `pm install -r --user 0` 并返回 `Success`；拉回 base APK 与候选逐字节一致，首次安装时间仍为 `2026-08-20 15:15:31`。未卸载、清数据、安装 Debug／测试包或运行任何 `connected*AndroidTest`。
- **Desktop 覆盖：** `/Users/nanzhufeng/Applications/南枫 AI Desktop.app` 已以相同 Bundle ID `com.nanzhufeng.ai.desktop`、版本 `0.6.0-p6d-dev`、Team `457B263L9J` 严格验签覆盖；候选及安装后主程序 SHA-256 均为 `d5c1de2bb352e476f473ae1616a4bed8f52b3d1f6012528281db44f832cf6919`。覆盖前旧包保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-stream-audit-20260913-1202.app`；正在运行的窗口须由用户自行退出后重开才会加载新二进制。
- **仍需真实边界验收：** 本轮未触发真实 Provider、Google／Supabase 账号同步或人工原生逐帧视觉验收；3 项 Android opt-in 仍 skipped。这些缺口不被构建、安装或自动测试替代。

## 2026-09-13：双端 Composer 去冗余说明与单模型附件路由（未安装）

- **行为：** Android 与 Desktop Composer 均移除发送授权／计费及附件接收方的小字，附件预览只保留可操作的内容卡；底部模型与发送动作行不再被说明文字挤出可视区域。Android 普通聊天附件路径改为：仅本机 UTF-8／PDF 文本层投影，或原始附件直达当前选中模型；当前模型不支持时不发送并提示更换支持该附件的模型。删除千问 Qwen3.7-Plus／智谱 GLM-OCR 的普通聊天中转调用；独立“南枫转写”能力不受影响。
- **验证：** Desktop Node `chat-first-ui.test.mjs` 95/95、lint、typecheck、静态 build 通过。Android `UniversalChatAttachmentBridgeContractsTest` 与 `P3JNormalChatExplicitEgressContractsTest` 定向 JVM 测试通过，`:app:assembleRelease`（含 `lintVitalRelease`）通过。
- **未替代：** 本轮没有覆盖安装 Android 或 Desktop 正式应用，也未发起真实 Provider 请求；需要在新包覆盖后，以实际图片／PDF草稿确认发送键可见、当前模型直达和不支持附件的原位拦截。

## 2026-09-13：Desktop 长会话交互性能修复与保数据覆盖

- **性能修复：** Desktop 长会话的轻量操作现在原位保留正文和侧栏，不再在菜单、弹层、模型选择等操作中重复序列化／解析整个消息树和会话列表；切换会话时保留侧栏并仅更新选中态与自动已读点。保留正文时，图片缩略图观察器不再因缩略图对象变化而失效，视频／文件预览观察器也不再重复扫描既有正文卡片。工作区确定后，独立的只读启动投影改为并行 IPC 读取。
- **自动验证：** Desktop Node `374/374`、Rust `cargo test --lib` `244/244`、lint、typecheck、静态 build 与 `git diff --check` 通过。845 个会话／500 条消息的纯渲染基准中，完整壳平均约 `133 ms`，正文与侧栏均保留的轻操作约 `0.23 ms`；该数值不替代原生端到端帧率。
- **Desktop 覆盖：** 候选严格验签后覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；Bundle ID `com.nanzhufeng.ai.desktop`、版本 `0.6.0-p6d-dev`、Apple Development Team `457B263L9J` 与旧包一致。候选及安装后主程序 SHA-256 均为 `5f4f475da68d69ac2a1a308c3729bab04d26f625bb2c477221b33a28097a4fe5`。旧包完整保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-performance-20260913-0040.app`。替换前后、启动前常规 workspace SQLite SHA-256 均为 `fd23cc8a08ca83251f806ac7c4020a4a14eb87d10dd0096531a5459169311ac7`；启动新包后 `quick_check=ok`／`integrity_check=ok`、86 张表、1 个工作区、7,310 条索引，原生窗口已回读既有长会话、附件与 Composer。
- **尚未替代：** 本次覆盖与数据回读不替代长会话实际人工操作的帧时间验收，也没有触发真实 Provider；两项仍须独立按正式原生窗口与账号条件验证。

## 2026-09-13：模型完整名称的响应式跨端显示与双端保数据覆盖

- **行为：** Desktop Composer 的普通会话与临时会话均直接显示模型目录 `displayName`，不再套用 `compactModelName`；模型触发器最小宽度为 `176px`，悬停／焦点胶囊跟随完整命中区，当前目录名称的可视上限为 `224px`。Android 外屏仍使用紧凑标签与 `88dp` 控件；内屏／展开宽度从 `600dp` 起改为目录完整名称与 `200dp` 控件。消息页脚继续使用紧凑归因，不与 Composer 的选择事实混为一谈。
- **自动验证：** Android `:app:testDebugUnitTest` 与 `:app:assembleRelease`（含 `lintVitalRelease`）通过；Desktop lint、typecheck、Node 全量 `373/373` 与 Rust `cargo test --lib` `244/244` 通过。新增 Desktop 目录标签回归覆盖当前支持名称不被缩写；Android 契约覆盖外屏紧凑／内屏完整标签与相应宽度。``git diff --check`` 对本轮文件通过。
- **Android 覆盖：** 正式候选 `app/build/outputs/apk/release/南枫AI.apk` 为 `com.nanzhufeng.ai` 66／`0.3.0-p10j`、非 Debug、v2／v3 有效，证书 SHA-256 为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，APK SHA-256 为 `2b3a62ae6d06991bb69de7db4949dbf49cd938c64e995cdb2d8fd46a347d0e0d`。OPPO `3B157F009E800000` 只执行一次 `pm install -r --user 0` 并返回 `Success`；安装后拉回 base APK 与候选逐字节一致，首次安装时间仍为 `2026-08-20 15:15:31`，CE／DE inode 仍为 `1459104`／`1433378`。未使用 `adb install`、卸载、清数据、Debug／仪器包或任何 `connected*AndroidTest`；设备临时 APK 已删除。
- **Desktop 覆盖：** 新 bundle 已严格验签并覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，Bundle ID `com.nanzhufeng.ai.desktop`、Apple Development Team `457B263L9J`、版本 `0.6.0-p6d-dev` 均与旧包一致；候选与安装后主程序 SHA-256 均为 `0156e09a22473628a15deb78d6bfa8a386e5906c342ae1ee8c1faeee093d0f51`。旧 app 可恢复于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-model-label-overlay-20260913-0035.app`。替换前后 workspace SQLite 均为 `quick_check=ok`／`integrity_check=ok`、86 张表、1 个工作区、7,310 条索引、SHA-256 `5fa950345fea42c9a902716f18fb8c29cca843955f505474616824a4972e69bb`；新 bundle 已启动。
- **尚未替代：** 本轮没有在折叠内屏的实际展开状态逐像素读取，也没有触发真实 Provider。代码、构建和保数据覆盖已验；内屏视觉与真实模型调用仍须按各自设备／账号条件单独验收。

## 2026-09-12：选定对话加密同步跨端兼容与双端保数据覆盖

- **格式兼容：** Android 现在严格读取 Desktop 早期 `messages/blocks` 纯文本记录；Desktop 现在严格读取 Android 当前 `nodes/text` 记录。两端仅恢复 `conversation` 记录的文本树，Android 不导入 Desktop 相邻的安全设置／提醒计划，Desktop 对 Android 记录不凭空创建这些数据。未知记录、附件／工具结果、未完成回复、异常字段或同一 ID 的本机对话均拒绝或不覆盖。
- **Android 恢复入口：** “读取云端列表”只展示已预检的加密文档标识，不解密正文；选择条目后仅在恢复保护已就绪时恢复一条对话。恢复成功写入本机，已有同 ID 对话明确显示“未覆盖”。该链路不替换整个 Room 数据库。
- **验证：** Android 新增 Android／Desktop 两套载荷的 Robolectric 解码回归，完整 `:app:testDebugUnitTest` 通过；Desktop Node 全量、Rust `cargo test --lib`（239 项）通过，新增 Android wire→Desktop 消息树回归通过。Android `:app:lintVitalRelease :app:assembleRelease` 通过；候选 `南枫AI.apk` 为 `com.nanzhufeng.ai` 66／`0.3.0-p10j`、非 Debug、v2／v3 有效、证书 SHA-256 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`，SHA-256 `e0e0e4e25efca518b61bfc67634406f188dc4ddf0202bf07825f92eb5cd5d210`。
- **覆盖：** OPPO `3B157F009E800000` 仅执行一次 `pm install -r --user 0` 并返回 `Success`；安装后 base APK 与候选 SHA-256 一致，首次安装时间仍为 `2026-08-20 15:15:31`，未卸载、清数据、安装 Debug／测试包或运行任何 `connected*AndroidTest`。设备临时 APK 已删除。Desktop 已重新构建、strict codesign 后覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；旧 app 可恢复于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-cross-sync-20260912-2350.app`。覆盖前后 Desktop SQLite SHA-256 都是 `ff9a11feb5dc64d587b94ec406fa5d9cb015dd328b2df69083866229f253fafa`，`integrity_check=ok`，新 bundle 已启动。
- **尚未闭环：** 本轮没有连接真实 Google 账号、调用真实 Supabase RPC、解密用户云端文档或触发真实 Provider；因此格式兼容、本机恢复防覆盖和正式包安装已验证，但真实云端双向恢复仍须用户在自己的账号和恢复码下手动完成一次。

## 2026-09-12：双端保数据覆盖安装——输入提示悬停左移修复与复制反馈

- Desktop Composer 的“回复 南枫AI”此前在鼠标悬停时向左偏移 4px；根因是 `#chat-composer:hover` 因更高 CSS 优先级把左右 padding 从 `12px` 覆盖为 `8px`，不是高度或滚动问题。已统一普通／hover／focus 为 `padding: 7px 12px`，新增 `FB-P6-049` 回归。独立 Playwright 渲染在 1280×720 下实测悬停前后均为 `x=315, y=620, height=36, padding=7px 12px`；Desktop lint、typecheck、build 与 `git diff --check` 通过。
- Desktop 已重新构建、严格验签并保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`。候选和安装后可执行文件 SHA-256 均为 `b84fd744eb37c1e6310006c5432cf62f8e3c89d5b4188d54a5e30e1af35ea249`，Bundle ID `com.nanzhufeng.ai.desktop`、Apple Development Team `457B263L9J` 与旧包一致；旧包完整保留于 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-dual-overlay-20260912-2315.app`。覆盖后、启动前 SQLite SHA-256 保持 `933cdfc6799cc5b52ccc90ae7260f583e21c49a88bc2eee215d3b832c50079ae`；启动新包后 `integrity_check=ok`，工作区仍为 1、搜索索引仍为 7,294。运行时写入会改变 SQLite 哈希，不能将启动后的哈希变化误报为数据丢失。原生窗口回读到真实历史会话与 Composer。
- Android 正式 `:app:assembleRelease` 产物 `app/build/outputs/apk/release/南枫AI.apk` 已以 OPPO `3B157F009E800000` 的 `pm install -r --user 0` 同签名覆盖；未使用 `adb install`、Debug／仪器测试、卸载或清数据。安装前后均为 `com.nanzhufeng.ai` 0.3.0-p10j (66)、非 debuggable，首次安装时间保留 `2026-08-20 15:15:31`。候选与设备安装后拉回的 base APK SHA-256 均为 `dcdd83b299386741efd0276de0ef573cbbf2ec333ddab8ee6a1544722519808c`，v2／v3 签名有效、证书 SHA-256 均为 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。正式沙箱不允许读取内部应用数据文件，因此未声称 Android 业务数据哈希；同签名覆盖、首次安装时间不变和安装后 APK 回读是本轮数据保留证据。临时拉回 APK 已移动到系统废纸篓，可恢复。

## 2026-09-12：Android／Desktop 统一复制图标成功反馈（未重打正式包）

- 复制契约统一为：写入剪贴板成功后，原复制图标在原位变为绿色勾号，保持 1.2 秒后恢复；不再在按钮旁插入“已复制”文字或触发整页重绘，因此不改变正文、代码块或表格的几何与滚动位置。
- 覆盖 Desktop 主会话消息、Markdown 代码块与表格、文本预览、转写全文、恢复码；转写全文与恢复码的纯文字“复制”按钮已改成带无障碍标签的图标按钮。Android 同步覆盖主会话动作、长按菜单、代码块、表格和文本预览；成功态均由原图标替换为 `Check`。
- 验证：Desktop 定向 Node 124/124、lint、typecheck、静态 build、`git diff --check` 通过；独立 `dist` 中实际点击主消息复制，读到勾号路径、`已复制` 标签和 1.3 秒后恢复原复制路径。Android `FB-P6-111`、`FB-P6-179` 定向 JVM 单测通过，且 Debug Kotlin 编译通过。整类 Android 合同测试仍有一条既有、与本轮无关的“多图片下载”源码断言失配（缺少 `.takeIf { target -> target != preview.id }`），未改动该功能；未构建／安装正式 Android 包、未重打／覆盖 Desktop bundle、未操作设备或本机数据。

## 2026-09-12：Desktop 加号菜单复刻 Android 图标与正文色（未重打正式包）

- 加号菜单的相机、图片、文件、基础风格、实时网页搜索改为 Android `ConversationWorkspace` 实际使用的 `PhotoCamera`、`AddPhotoAlternate`、`AttachFile`、`AutoAwesome`、`Public` Rounded 路径；不再调用 Desktop 自有线框的 `camera`／`image`／`file`／`sparkles`／`globe`。
- 菜单正常主文字在浅色主题固定为 Android `BodyText #1E2925`，图标面仍为系统浅灰圆形；橙色仅保留风格、所选风格值和开启的联网图标／开关。深色主题明确回退到自身的正文色，避免把浅色设计规则错误带入深色界面。
- 验证：C07 与相邻 chat-first UI Node 102/102、Desktop lint、typecheck、静态 build、`git diff --check` 通过；Playwright 独立 `dist` 打开加号菜单的截图已核对五个图标、容器与黑色正文。静态预览仅有既有 favicon 404；未重打／覆盖正式 Desktop bundle、未操作本机数据。

## 2026-09-12：Desktop 搜索定位的会话头部与侧栏离开语义（未重打正式包）

- 从全屏搜索定位到会话后，头部顺序改为“会话标题在左、返回搜索在右侧操作位”，不再让“搜索”占据标题主位；搜索结果定位场景仍保留返回入口。
- 用户从左侧栏主动选择任意对话时，现在会清除搜索面板、返回搜索标记、归档临时定位和消息／附件锚点，直接进入正常聊天主界面。设置内收藏／归档／回收站的返回语义不受影响。
- 验证：搜索、侧栏与对话头部定向 Node 44/44、Desktop lint、typecheck、静态 build、`git diff --check` 通过。Playwright 独立 `dist` 可打开全屏搜索；其只读视觉夹具不含文字结果对应的会话，点击时准确显示既有“结果所属会话已变化”，不能据此替代原生 SQLite 搜索定位回读。浏览器另有静态预览缺少 favicon 的既有 404；未重打／覆盖正式 Desktop bundle、未操作本机数据。

## 2026-09-12：Desktop 回复中的 LaTex 公式可读化（未重打正式包）

- 根因是安全 Markdown 投影将 `\\[`／`\\]`、`\\rightarrow` 和上下标语法按普通正文转义，导致导入或新生成回复中的公式边界、箭头及下标裸露。现在显示公式支持 `\\[ ... \\]` 与 `$$ ... $$`，行内公式支持 `\\( ... \\)` 与 `$ ... $`；常见箭头、比较、运算符、希腊字母和上下标投影为安全的可读 HTML，原始 HTML 仍先转义、不会执行。
- 显示公式以居中数学排版呈现，可横向滚动以避免窄窗口裁切；行内公式不再破坏正文行高。未知 LaTex 指令保留可见文本，不猜测或执行扩展语义。
- 验证：Markdown 定向及相邻导入回归 19/19、Desktop lint、typecheck、静态 build、`git diff --check` 通过。独立 `dist` 预览确认应用正常加载、无框架错误，唯一 console error 为静态预览缺少 favicon 的既有 404；未重打／覆盖正式 Desktop bundle、未操作本机数据。当前静态预览夹具不含公式消息，公式视觉采用渲染契约验证，正式历史会话中的同类消息仍待原生 bundle 回读。

## 2026-09-12：Desktop 折叠侧栏的图标展开与底部圆形动作（未重打正式包）

- 折叠轨道由 76px 收紧为 72px。展开态仍保留独立收起箭头；折叠态不再显示独立箭头，南枫 AI 图标本身是带 `展开导航栏` 标签的 48px 按钮，点击后恢复完整侧栏。
- 折叠态底部动作改为纵向 44px 圆形：橙色“新对话”在上，白色“设置”贴底。设置不再继承展开栏的 96px 胶囊，也不再因折叠规则被隐藏；圆形表面、阴影、悬停和键盘焦点均沿用既有按钮体系。
- 验证：定向 Node 9/9、Desktop 静态 build 通过；Playwright 在独立 `dist` 预览执行“展开侧栏 → 折叠 → 点击图标展开”，DOM 与截图均确认两种终态。浏览器唯一 console error 是独立预览缺少 favicon 的 404；未重打／覆盖正式 Desktop bundle、未操作真实本机数据。

## 2026-09-12：Gemini 3.8 Flash 双端替换（未做真实服务或正式包验收）

- 当前 Android 与 Desktop 的 Gemini 日常预设、手动选择、Auto 路由和紧凑显示均改为 `Gemini 3.8 Flash`／`Gemini 3.8`，请求 ID 为 `google/gemini-3.8-flash`。官方 Google 与 OpenRouter 页面已核对该模型及 OpenRouter 介绍期 `USD 0.75 / 3.75 / 0.075`（输入／输出／缓存读取，每百万 token）本地只读估算；服务商实际账单仍优先。
- Android 读取旧 `GEMINI_3_7_FLASH` 设置时迁到 3.8；Desktop 读取旧 SQLite `desktop_provider_settings` 记录时原地替换为 `GEMINI_3_8_FLASH` 并递增 revision。旧会话的模型归因和旧 3.7 账单估算保持原样，不能把历史事实伪造成 3.8。
- 验证：Desktop Node 定向 100/100、Rust 设置迁移 1/1；Android JVM 合同 59/59。`cargo fmt --check` 仍被既有 `desktop_account_sync_v1.rs`、`desktop_docx_preview.rs` 与 `lib.rs` 的非本轮格式差异阻断；本次所改文件的 `git diff --check` 已通过。未请求真实 Provider、未构建／安装正式 Android 包、未重打或覆盖 Desktop bundle，也未操作设备。

## 2026-09-12 最新：附件锚定菜单 / 搜索结果缓存 / 深灰媒体底

- 最新正式包已覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包在 `nanfeng-menu-position-backup-0zpMgx`；替换前后 SQLite SHA256 `f945f223408210d27b4189ef0962a3531f19f0bf1e6f4db2ddb9fe853813e2c1` 一致。Node 全量、Rust 图片路径并发安全回归、macOS bundle、strict codesign 通过。
- 搜索右键/长按改小菜单：仅文件名、快速定位、删除。背景透明无 blur；点外/Esc 关闭。坐标先由附件 DOM 边界取得，必须在插入后通过 element.style.left/top 设置；HTML inline style 被 `style-src 'self'` 阻止是前两版菜单飞到左侧的根因，不放宽 CSP。最新正式视频卡右键实看已锚定对应第三张卡，Esc 通过，快速定位到所属对话通过。
- 主会话附件右键/长按新增信息卡：文件名、类型、已有时长、大小、发送时间、搜索定位/下载/分享；正式视频右键入口及三按钮已回读。时长缓存未命中时当前会省略时长，尚需补按需读取；下载/分享系统终态未执行，不能称完整验收。
- 媒体统一 `--attachment-media-canvas:#303330`，搜索音频与视频/预览共用；尚需用户同视口确认色感。
- 图片读取从 store mutex 迁到 paths worker（原来解码缩放全程占锁）；真实图像校验、缺失文件、持锁时路径读取并发测试通过。
- `search-result-cache.mjs` 有界 12 页：复用结果即时呈现，空查询正文/图片可从已读全部投影，后台仍核对 DB；workspace/current summary 变化清缓存，更新失败标明上次结果。真实图片页显示 1481 项、无整屏等待；正文/图片连续点击已执行，未完成量化 P95 性能验证。不要称所有搜索卡顿已解决。
- 还需处理：缓存失效必须进一步核对所有写入；所有附件类型主菜单时长/下载/分享、长按手势、原位保持；完整手机端差异审计及本清单其余项目仍未完成。最近测试日志 `/tmp/nanfeng-csp-menu-tests.log`。

## 2026-09-12 晚：用户全部反馈与手机端遗漏审计仍未完成

- 最后追加已交付：音量弹层复用播放栏半透明材质/模糊 token、999px 胶囊、无边框重阴影、居中对齐按钮；紧凑竖条保留。已再次保数据覆盖并在正式 `1000160321.mp4` 暂停画面实看通过。最新旧包备份 `nanfeng-volume-surface-backup-brrwZG`；替换前后 DB SHA256 均 `bc6336034a289e62649713eec5b85a7481d246451a073302d0e8c6a3ea406a0b`。全量 Node、macOS build、strict codesign 通过；下文 rENmA2 为上一版备份。

- 当前事实与后续逐项入口见 `docs/DESKTOP_FEEDBACK_CHECKLIST_20260912.md`；其中未勾选项不得沿用下方历史“闭环”措辞。
- 最新已覆盖正式 Desktop：音量使用共享喇叭图标（原本地 icons.audio 不存在）、紧凑竖条 44×128 / 滑块 16、inert 安全 Markdown 预览。视频实看图标及竖条通过；真实 MD 标题和音频右键菜单、删除取消已实测。Node 全量、macOS bundle、strict codesign 通过，替换前后 DB hash 相同，备份在 `nanfeng-volume-backup-rENmA2`。
- 仍需继续：全分类性能计时及真实长按/定位、普通图片无灰底、全手机代码跨入口审计、Android 恢复与安全等；本轮没有真实同步/上传/删除。
- 下一最小缺陷：来源列表的本地 `icons.globe` 同样缺失，切共享图标并加图标引用门禁；随后继续附件验收矩阵。当前 CUA 正式应用停在已暂停的视频预览，小音量弹层展开。
- 上下文闸门 HANDOFF，保留当前脏工作区，不重做已覆盖项、不启动 Android 仪器测试、不把局部完成说成全部完成。

## 2026-09-12：Desktop 视频预览打开即播放并删除重复入口

- 根因是 Desktop 视频预览仍沿用“打开预览后，再点一次开始本地播放”的旧交互合同；这与附件卡点击已经表达播放意图相冲突，也导致左上角多出无意义的二次入口。
- 现在视频预览打开后会先恢复上次播放位置，再主动调用原生播放器 `play()`；同时保留系统播放／暂停、进度和音量控件作为操作与自动播放失败时的回退。左上角“开始本地播放”按钮及其事件分支已删除，标题、关闭按钮和底部文件名／大小／时长未改。
- 新增回归门禁，要求视频元素具备 `autoplay`／`playsinline`，并禁止重新出现 `开始本地播放`、`start-video-preview` 或同类重复提示。Desktop 全量 Node 326/326、lint、typecheck、静态 build、macOS bundle 和严格验签通过；唯一警告是既有未使用的 `desktop_storage_location::resolve`。
- 已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；候选包与已安装可执行文件 SHA-256 均为 `8b8711cda35e271519f2728e7b9708d8c12d2c2c2bc52d38d1c011def5e44821`，旧包保留为 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-video-autoplay-20260912-1131.app`。覆盖后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引、307,867,648 字节。
- 正式原生窗口已从视频搜索打开截图同款 `1000160003.mp4`：播放器从保存的 `0:02` 自动继续，回读时已到 `0:05` 且原生控件显示 `Pause`；可访问性树中不存在左上角旧播放按钮或文案。

## 2026-09-12：Desktop 主会话附件改为真实内容预览

- 主会话附件原先只有图片走真实缩略图；视频、PDF、文本、音频和其他文件仍落到通用灰色上传图标。现在主会话与草稿复用同一个本机附件呈现 owner：图片显示真实缩略图，视频显示真实首帧和居中播放键，PDF 显示真实首页，Markdown／文本显示内容摘要，音频显示深色 MP3 卡、时长和轨道，DOCX 等不可内联渲染类型显示真实格式标识。
- 真实视频仍发灰的第二层根因是 macOS `qlmanage` 会对手机导入的 MP4 无限挂起，既不返回缩略图，也不退出。视频帧 owner 现在优先调用本机 FFmpeg 解码 0.2s 帧，两条解码路径均有硬超时、精确 kill／wait 和格式校验；即使解码器异常也不再长期占用进程或拖住界面。真实 `1000159306.mp4` 已成功产生 `360×640` PNG 预览帧。
- 新增 `FB-P6-181` 覆盖图片／视频／PDF／文本／音频／DOCX 六类主会话卡片，并增加“解码器挂起必须退出”Rust 回归。Desktop 定向 3/3、相邻用例 120/120、Node 全量 326/326、Rust 全量 234/234、lint、typecheck、静态 build、macOS bundle 和严格验签通过；唯一警告是既有未使用的 `desktop_storage_location::resolve`。
- 已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，候选包与已安装可执行文件 SHA-256 均为 `0eee4689e63f315b49cb853cfee1b3e72deda2204a8d4d6450903d075bee27ad`，签名为 `Apple Development` / Team `457B263L9J`。覆盖后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引、307,867,648 字节；旧包保留为 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-main-attachment-previews-20260912-0932.app`（任务前版本）和 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-main-attachment-ffmpeg-20260912-0941.app`（中间版本）。
- 正式原生窗口已在手机导入的真实历史会话中回读：`视频分析说明` 顶部显示 `1000159306.mp4` 真实画面与播放键，`已确认事实（通话内容逐句梳理）` 显示 MP3／0:49／轨道，`Codex秒退根因分析` 显示 MD 标识与真实 Markdown 摘要。另有真实图片会话和自动化六类合同共同覆盖图片／PDF／不支持格式。

## 2026-09-12：Desktop Google 登录改为 Supabase 托管 OAuth + PKCE（待用户选择账号后终态回读）

- 撤回此前“独立 Desktop OAuth Client + loopback 已打通登录”的结论。该方案只修复了 Google 授权页的 `redirect_uri_mismatch` 和本地 callback 读取，却仍由 Desktop 直接请求 Google Token endpoint。对同一公开 Desktop Client 做不含真实凭据的受控坏码探针后，Google 明确返回 `invalid_request: client_secret is missing`；应用原先用 `error_for_status()` 丢弃响应体，界面只能看到笼统的“Google Token 被拒绝”，因此此前没有定位到真实失败点。
- Android 继续由 Credential Manager 取得带 nonce 的 Google ID token，再交给 Supabase `grant_type=id_token`；Desktop 无 Credential Manager，现改为 Supabase 托管 Google OAuth：系统浏览器打开 `/auth/v1/authorize?provider=google`，Desktop 只生成 state 与 PKCE verifier/challenge，本机 callback 收到 Supabase auth code 后再调用 `/auth/v1/token?grant_type=pkce` 建立南枫云会话。Google Web Client Secret 只留在 Supabase 服务端，不读取、不打包、不要求用户提供。
- Supabase `NanFengCloud` 项目已新增并回读受限 redirect allowlist：`http://127.0.0.1:**/oauth/callback**`，只允许 loopback 与固定 callback path，以支持 Desktop 每次随机端口。打包与 Rust 配置已删除 Google Desktop Client ID 依赖，只要求公开 Supabase URL 与 publishable key；登录网络等待仍在 SQLite 锁外，失败不写会话、不改变本机数据或云端数据。
- localhost OAuth mock 已先红后绿，证明授权 URL、state、S256 challenge、callback code、PKCE token exchange、apikey 与会话持久化完整连通，且请求与 bundle 不含 Google Client Secret／Desktop Client ID。Desktop 全量 Node 325/325、lint、typecheck、Rust 233/233、macOS bundle 与严格验签通过；唯一警告为既有未使用的 `desktop_storage_location::resolve`。
- 最新正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `/Users/nanzhufeng/Applications/南枫 AI Desktop.pre-supabase-oauth-pkce-20260912-1008.app`。覆盖后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引、307,867,648 字节；当前真实运行已从应用进入 Google 账号选择页，待用户本人选择账号后继续回读 Supabase 会话和应用内登录终态，不能只凭到达账号选择页宣称闭环。

## 2026-09-12：Desktop 旧手机导入对话回答菜单的惯性滚动竞态修复

- 撤回此前“SVG 子节点冒泡是完整根因”的结论。数据库审计确认 845 个会话、5,135 条消息的 ID 均非空且全局／会话内无重复；实际差异来自旧手机导入对话通常很长、必须滚动，而新建短对话通常没有这条路径。WebKit 在惯性滚动期间可能吞掉后续 `click`，同时原实现监听任意对话 `scroll` 并关闭菜单，残余惯性或程序化滚动会把刚打开的菜单立即关掉。
- 回答三点按钮现在由主键 `pointerdown` 直接打开，键盘无指针 `click` 继续作为可访问回退；普通 `click` 不会重复切换。菜单不再因裸 `scroll` 关闭，只在新的对话区滚轮手势、点外、Esc 或窗口尺寸变化时关闭。
- 新增两条回归门禁；先稳定得到 2 个失败，再完成修复。Desktop 全量 Node 322/322、lint、typecheck、静态 build、macOS bundle 和严格验签通过。候选与已安装可执行文件 SHA-256 同为 `806c23ab330ac46019f7964264c3b0b4091be127b7fe9084276ccfa204519ff7`。
- 已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-assistant-menu-inertia-fix-20260912-0845.app`。原生应用在 8 月超长手机导入对话内连续打开 3 个不同回答菜单，并在滚动后立即点击通过；另取 9 月手机导入对话复测滚动后立即点击也通过。覆盖后 SQLite `integrity_check=ok`、86 张表、845 个会话、7,297 条本地搜索索引，数据库大小保持 307,867,648 字节。

## 2026-09-12：双端复制成功勾号固定在复制按钮左侧

- 根因是 Desktop 的结构化内容复制按钮使用绝对定位，而成功勾号被插入按钮之后并参与普通文档流，因而掉到整个内容框左下角；Android 的消息、信息块和表格复制行也把勾号排在复制按钮之后，与用户要求的方向相反。
- Desktop 现在统一将临时勾号插在对应复制按钮之前；代码块／表格勾号与复制按钮共享相对定位 owner，固定在按钮左侧且不改变内容布局。消息复制成功不再触发一次无意义的整页重绘。Android 的消息操作行、信息块和 Markdown 表格也统一调整为“勾号 → 复制按钮”。
- Desktop 定向 93/93、全量 Node 322/322、lint、typecheck、静态 build、macOS bundle 与严格验签通过；正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-copy-check-alignment-20260912.app`。原生应用点击表格复制按钮后已实看到绿色勾号紧贴按钮左侧；SQLite 覆盖后 `integrity_check=ok`、86 张表、7,297 条本地搜索索引。
- Android 源码与契约测试已同步更新，但本轮 Gradle 在编译前被既有 dependency-verification 门禁拒绝：`kotlinx-coroutines-bom-1.8.0.pom` 缺少校验记录；未绕过校验、未构建安装包、未操作主设备。

## 2026-09-12：Desktop 搜索所有分组统一去重信息层级

- 用户明确要求其他搜索分组也遵守同一标准，不重复无价值内容。现在正文卡只保留会话标题、命中正文片段和右下角大小／时间；文件与音频卡只保留一次文件名、类型图标和右下角大小／时间；图片与视频继续以预览为主，只保留一次名称和右下角大小／时间。不会再在卡片内显示 MIME、应用内预览能力术语、来源标签或附件关联的会话正文。
- 附件点击预览能力未删除：入口仍进入既有 app-owned preview owner，安全边界与不交给系统打开的约束不变；只是从搜索卡的可见信息中移除了工程化提示。分类、文件类型筛选、全局排序、分页、右键／长按菜单和索引数据均未改。
- 新增去重契约并更新 C08／本机数据搜索契约，定向 43/43 与 Desktop 全量 Node 313/313、lint、typecheck、静态 build、macOS bundle 与严格验签通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-search-card-dedup-20260912.app`；覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引，应用已启动。
- 本环境无 Browser 插件与本地 Playwright，不能取得搜索页截图级读回；原生启动、数据连续性及所有渲染契约已验证，像素级搜索页复验仍待可用浏览器或人工在应用内打开搜索页。

## 2026-09-12：Desktop 搜索卡片的大小／日期统一右下对齐

- 用户要求搜索界面所有卡片的“大小 · 日期”统一显示在右下角。正文卡现将元信息从标题右侧移为内容末行的右对齐槽位；通用文件／音频卡通过卡片纵向 owner 将事实栏推至右下；图片与视频卡保留名称在预览图下方左侧、将同一事实行与名称底边对齐到右侧。分类、排序、预览尺寸、标题、摘要和菜单入口均未改。
- 新增静态布局契约覆盖正文、通用附件、图片和视频四条卡片渲染链，避免以后单独改某类卡片又让事实栏漂回标题侧。
- Desktop 全量 Node 312/312、lint、typecheck、静态 build、macOS bundle 与严格验签通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-search-card-facts-corner-20260912.app`；覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引，原生应用已启动并读取到现有会话。
- 本环境没有 Browser 插件且本地无 Playwright，故未生成搜索页的浏览器像素截图；原生自动化可确认应用启动与数据连续性，搜索卡片的截图级复验仍待在可用浏览器或应用内打开搜索页后完成。

## 2026-09-12：Desktop 启动图标主体小幅放大

- 用户只要求 Desktop 图标的白色马头主体再放大一点；保留原有橙色材质、圆角、居中关系与 Android 图标资源不变。macOS 专用导出从原 `1.23×` 提升至 `1.30×`，即相对上一版再增加约 `5.7%`；1024px 母图未被重绘或改色。
- `icon.png`、`nanfeng_ai_icon_rgba.png` 与 `nanfeng_ai_icon.icns` 均由同一 macOS 导出脚本重新生成。静态审计确认 Tauri 配置仍指向该 `.icns`，并确认候选包解出的 1024px 图像与源码 `.icns` 的对应尺寸逐字节相同。
- Desktop lint、typecheck、全量 Node 311/311、macOS bundle 与严格验签通过。候选与已安装包均为 `com.nanzhufeng.ai.desktop`／团队 `457B263L9J`；已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-icon-subject-scale-20260912.app`。覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；应用已启动。
- 当前自动化可确认运行中的原生应用与已安装资源，但无法读取 Dock／Finder 的系统级像素缓存；因此尚未取得真实 Dock 表面的截图级复验。

## 2026-09-12：Desktop 视频搜索卡片改为预览帧优先

- 根因与图片结果相同：视频复用通用附件卡，只显示 46px 播放图标，再重复显示文件名、MIME、应用内预览提示和索引命中的会话正文；同时搜索结果此前没有视频帧缩略图 owner。
- 视频结果现使用独立卡片：176px 预览帧与居中播放标识为主体，底部只保留一次名称和一行“大小 · 时间”，三点操作保留；不再渲染正文摘要、MIME、来源标签或“应用内视频预览”。
- 新增受限的 macOS Quick Look 缩略图 owner：卡片进入可见范围后才对该 workspace 已验证的 MP4 生成一张最大 640px PNG，临时文件立即清理，网页层仅得到这张受限 PNG，不得到视频字节、路径、URI 或解码控制。缩略图失败时保留视频占位图，不影响打开已验证的本地视频。
- 搜索卡回归 12/12、lint、typecheck、Rust 定向视频 owner 测试、macOS bundle 与严格验签通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-video-search-focus-20260912.app`；覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引。应用已重启；本轮未取得视频图片分类的截图级读回。

## 2026-09-12：Desktop 图片搜索卡片改为图片优先

- 根因是图片结果复用了通用附件卡：缩略图固定为 46px，同时将 MIME、应用内预览能力、来源标签和索引命中的会话正文一起渲染，文件名还会在摘要内重复出现。
- 图片结果现使用独立卡片：176px 等比完整预览为主体，底部只保留一次文件名和一行“大小 · 时间”；三点操作保留。图片卡不再显示正文摘要、MIME、来源标签或“应用内图片预览”提示。其他类型附件维持原有事实展示。
- 图片卡定向回归 11/11、lint、typecheck、macOS bundle 与严格验签通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-image-search-focus-20260912.app`；覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引。应用已重启；本轮自动化取得窗口与当前会话树，但尚未能安全驱动无语义搜索入口至图片页做截图级读回。

## 2026-09-12：Desktop “来源网站”列表改为真实可打开链接

- 根因是来源行仅使用 WebView 内的 `<a target="_blank">`；在 Tauri Desktop 中这不等价于交给 macOS 默认浏览器，因而用户看到可读的卡片却不能可靠打开对应网页。
- 保持原有来源弹窗、行高、文字和关闭方式不变，来源行现为具名按钮：点击后调用最小权限的原生命令，将已校验的 HTTP／HTTPS URL 交给 `/usr/bin/open`。该命令拒绝 `file:`、脚本协议、无主机名地址及含账号／密码的 URL；链接页不接收任意命令或本地路径。
- `chat-first-ui` 定向 93/93 通过；Rust URL 安全单测 1/1 通过；macOS bundle 严格验签通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-source-link-open-20260912.app`；覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引。应用已重新启动；当前自动化只能读取到原生窗口层，尚未取得来源行点击后的浏览器读回。

## 2026-09-12：Desktop Google 网页登录回调修复（待用户完成账号授权）

- 根因有两层：Google Cloud 的 `NanFengCloud` 项目此前仅有 Android 与 Supabase Web OAuth Client，Desktop 把后者用于随机 `127.0.0.1` loopback 回调，Google 因而在授权码返回前报 `redirect_uri_mismatch`；另一个连带缺陷是 listener 为等待连接设为非阻塞后，已接受的回调流没有恢复为受超时约束的阻塞读取，可能在浏览器刚连接时偶发“OAuth callback 无法读取”。
- 已在同一 Google Cloud 项目创建独立的“南枫 AI Desktop”Desktop app OAuth Client；仅将公开 Client ID 写入被 Git 忽略的 `local.properties`，没有读取、复制、打包或显示 Client Secret。`bundle-macos.mjs` 现将该 Desktop Client 设为必填，缺失时直接停止打包，避免再次覆盖一个无法网页登录的正式包。
- `callback_code` 现在只对 listener 轮询；接受到受信任 loopback 连接后恢复阻塞读取、设置 10 秒读超时，并累计到完整请求行再解析。状态校验、PKCE、nonce、短暂 SQLite 持锁持久化和失败不写入本机／云端的既有边界不变。
- 先复现 Rust localhost OAuth mock 的 `OAuth callback 无法读取` 红灯，修复后相关 Rust 7/7 通过；Node 309/309、lint、typecheck、macOS 签名 bundle 均通过。候选和正式包验签为同一 `com.nanzhufeng.ai.desktop`／团队；两次保数据覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引。旧包保留为 `南枫 AI Desktop.pre-desktop-oauth-20260912.app` 与 `南枫 AI Desktop.pre-oauth-callback-read-20260912.app`。
- 正式应用已实测“使用 Google 登录”打开 Google 账号选择页，使用独立 Desktop Client 和一次性 `127.0.0.1` 回调端口，未再出现 redirect mismatch。当前账号选择／同意授权必须由用户本人完成；完成后需回读 Desktop 的 Supabase 会话与应用内登录态，不能仅凭网页跳转宣称登录闭环。

## 2026-09-12：Desktop Composer placeholder 悬浮文字跳动

- 用户报告空 Composer 内“回复 南枫AI”在鼠标悬浮时跳动，且明确要求不改变输入框布局。排查确认悬浮事件本身不触发 `render()` 或 `resizeComposer()`；后者只在输入和初始化执行。渲染页中 hover 前后 textarea 盒模型已固定为 `890×36`，原有 CSS 也没有 hover 版的边距／高度差。
- 根因在文字度量未由 Composer 自身完整锁定：textarea 与 placeholder 仍保留 WebKit 的 `text-size-adjust:auto`，而仅锁了外框与部分字号。现仅为 `#chat-composer` 及其 `::placeholder` 固定既有 `14px / 400 / 22px` 与 `-webkit-text-size-adjust: 100%`／`text-size-adjust: 100%`，并将 placeholder opacity 固定为 1；未改宽高、padding、圆角、阴影、栅格或按钮。
- `FB-P6-049` 新增的 hover 字体度量契约先失败后通过；Desktop 全量 Node 309/309、lint、typecheck、静态 build 与 macOS bundle 通过。Playwright 页面在真实 hover 状态下复测文字度量保持 `14px / 400 / 22px / 100%`，textarea 仍为 `890×36`，无控制台错误（之前仅有 favicon 404，静态服务环境不含 favicon）。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包可恢复于 `南枫 AI Desktop.pre-composer-placeholder-stability-20260912.app`；覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引，原生应用已启动。

## 2026-09-12：Desktop 南枫转写／定时任务恢复聊天工作台分栏

- 根因是 `app.mjs` 与 `chat-shell.mjs` 将两个工具入口归入 `utility-standalone`，直接返回独立满屏 canvas；这同时移除了对话侧栏，并让工具页自己的灰色页面底露在白色内容卡周围。
- 现统一为宽屏工作台：左侧始终保留标准对话导航、搜索和固定工具入口，右侧主面板显示“南枫转写”或“定时任务”。两个入口从任何页面进入都会取消工作导航模式；工具页标题改为右侧左对齐标题，不再显示冗余关闭按钮。转写、定时任务的主面板和滚动容器均使用前景白底，避免露出独立灰色外框；项目／知识／记忆原有的独立工作区不受影响。
- 更新后的分栏契约先失败后通过；Desktop 全量 Node 308/308、lint、typecheck、静态 build 和 macOS bundle 均通过。正式签名包已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包可恢复于 `南枫 AI Desktop.pre-utility-split-pane-20260912.app`。覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；原生应用实际打开“南枫转写”和“定时任务”后均读取到对话导航与右侧工具内容。

## 2026-09-12：Desktop Composer 加号面板按 Android 实现对齐

- 用户要求 Desktop 输入框左侧加号面板完整参考手机端。Desktop 已有相同的动作与会话偏好 owner，差异只在视觉几何：相机／图片／文件现在与 Android 一样使用 52px 行、32px 中性圆形图标面和 18px 图标；“基础风格和语气”与“实时网页搜索”使用各自 56px、16px 圆角的内嵌行，当前风格值为主题色；联网图标、58×28 开关、21px滑块与 30px 行程同步 Android `SettingsSwitch`。根面板保持 Android/合同指定的约 280px 宽、22px 圆角、锚定 Composer、点外关闭、Esc/Back 子层返回和焦点恢复。
- 动作顺序仍为相机、图片、文件、基础风格和语气、实时网页搜索；风格二级页继续仅含默认、直言不讳、专业可靠、亲和友善、高效务实、风趣搞笑及一个当前勾选。普通会话的风格／联网覆盖持久化与新会话首条消息的原子落库不改；临时聊天仍不显示无法生效的两项。
- 更新后的 C07 几何合同先红后绿；Desktop 全量 Node 308/308、lint、typecheck、静态 build 与 macOS bundle 通过。候选及覆盖后的应用严格验签，已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-composer-add-parity-20260912.app`。覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；原生应用已启动。当前 CUA 只读到窗口层，尚未取得“＋”已展开的原生像素读回。

## 2026-09-12：Desktop 对话头部操作胶囊阴影减半

- 用户指定只减弱截图中“新对话／更多”头部操作胶囊的阴影。该组件的独立投影由 `chat-header-content-actions` owner 提供，现从 `rgb(0 0 0 / 10.59%)` 精确减半至 `rgb(0 0 0 / 5.295%)`；偏移、模糊、44px 按钮命中区、分隔线和其他浮层阴影均未改。
- 先更新两条组件契约使其在旧投影下失败，后通过；Desktop 全量 Node 308/308、lint、typecheck、静态 build 和 macOS bundle 均通过。候选及覆盖后的应用严格验签，已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-header-action-shadow-20260912.app`。覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；原生应用已启动。

## 2026-09-12：Desktop 会话选中行标题让位收紧

- 会话行悬停／键盘聚焦时，原实现只将日期设为不可见，网格中的日期列仍占宽；标题因此同时给隐藏日期和右侧三个操作让位，过早被截断。
- 现在日期在操作出现时直接退出布局，标题只保留三个既有 32px 操作按钮所需的 96px 宽度。置顶、收藏、更多三个按钮的尺寸、命中区和键盘显示行为均未改变。
- `FB-P6-028` 已先红后绿；Desktop 全量 Node 308/308、lint、typecheck、静态 build 和 macOS bundle 通过。候选及覆盖后的应用均严格验签，已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-sidebar-title-space-20260912.app`。覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；原生应用已启动。

## 2026-09-12：Desktop 附件预览统一暗底画布

- 用户指出全屏视频预览的白色标题区与竖屏视频两侧灰底割裂。预览共享 owner 现在用 `#101310` 作为完整暗底：遮罩、全屏 dialog、标题区、图片画布、视频 letterbox 留白统一同色；图片和视频帧保持原始像素，不加滤镜。
- 关闭按钮、标题、翻页／播放控件、文件事实和错误文案改为浅色高对比；PDF、文本和音频仍沿用各自真实内容呈现，但它们的外层预览画布也不再露白。
- 新增 `FB-P6-180`，先因缺少共享暗底失败、再通过。Desktop 全量 Node 308/308、lint、typecheck、静态 build 与 macOS bundle 通过；候选及覆盖后的应用均严格验签，已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`，旧包保留为 `南枫 AI Desktop.pre-dark-preview-20260912.app`。覆盖前后 workspace SQLite `integrity_check=ok`、86 张表、7,297 条本地搜索索引；原生应用已正常启动。本轮尚未取得已打开视频／图片时的截图级读回。

## 2026-09-12：Desktop 附件预览去除重复提示与图片工具栏

- 用户指出视频预览的“不会自动播放／上传／外发”、点击提示和图片预览“缩小／适应／放大”工具栏都是无意义噪声。附件预览现只呈现能直接操作或判断内容的事实：图片保留原图、多图切换与手势缩放；PDF 保留页码；视频保留播放、续播位置和文件名／大小／时长；文本保留文件事实和复制／保存／分享。
- 同类清理覆盖图片、PDF、视频、文本加载和完成态的重复“本机私有副本／不会外发／不会执行”说明，并移除全屏搜索空态的“本页面不会外发内容”。错误状态、无障碍加载状态、文件事实及可能改变结果的操作没有删除。
- 先新增视频去除重复提示、图片无工具栏、PDF／文本无重复安全说明和搜索空态回归；旧代码下 `chat-first-ui` 92 项中 2 项如预期失败，清理后该组 92/92 通过；全量 Node 307/307、lint、typecheck、静态 build、macOS bundle 与严格验签均通过。已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；原生读回用户截图中的 `1000159312.mp4` 仅有“开始本地播放”、续播位置和 `1000159312.mp4 · 23880.0 KiB · 9:41`，图片预览仅有标题、关闭和原图。覆盖前后 SQLite `integrity_check=ok`、86 张表、7,297 条索引；旧包保留为 `南枫 AI Desktop.pre-preview-noise-20260912.app`。

## 2026-09-12：Desktop 搜索分类胶囊与点击卡顿

- 用户截图中的“图片”选中项仍可见为圆角矩形。根因是全屏搜索 tab 的 `999px` 不是强制形状合同，容易被通用按钮层级重新解释；现将该组的选中面、裁切与所有交互状态固定为 `border-radius: 999px !important`。
- 点击分类的真实瓶颈有两层：前端即使已标记 loading，仍同步完整重建搜索 DOM；Rust 又在每次查询时读取、反序列化并计算所有 workspace exchange 的语义哈希。当前数据实测为 1 个工作区、7,297 条索引、7,642,533 字节 exchange JSON，故每次点击都会重复这段无关工作。
- 分类点击现在先原地切换胶囊，下一动画帧再执行索引请求；健康索引只以 `workspaces.semantic_hash` 与 `desktop_local_search_index_state` 比较，只有变更、缺失或版本不一致才读取 exchange 并重建。快速连续点击保持 generation 防旧结果回写。
- 新增前端胶囊／下一帧查询回归及 Rust “健康索引不重解析 exchange”回归：前端定向 10/10、Rust 定向 1/1、Node 全量 306/306、Rust 全量 231/231、lint、typecheck、静态 build、macOS bundle 与严格验签均通过。
- 已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app` 并在真实本机搜索页回读：`图片 → 视频` 后“视频”选中且结果为 66 条；自动化点击返回为 167ms（含原生自动化开销，不作为端到端性能基准）。覆盖后 SQLite `integrity_check=ok`、86 张表、7,297 条索引。旧版本保留为 `南枫 AI Desktop.pre-search-pills-20260912.app` 与 `南枫 AI Desktop.pre-search-pills-close-20260912.app`；未卸载或清除数据。

## 2026-09-12：Desktop 工作根页被聊天栅格压窄

- 用户截图中“工作”标题被拆成竖排、右侧出现大面积空白。根因是 `renderChatFirstShell` 已将工作根页渲染为独立 `workspace-shell`，但 `app.mjs` 只在项目／知识／记忆存在 `workPanel` 时才给外层应用 `workspace-standalone-shell`；工作根页仍被聊天三列栅格分配到左侧单列。
- 新增唯一的 `isWorkspaceRoot(data, pane, selectedWorkProjectId, selectedConversationId)` 判断，Chat shell 与外层 App 都消费它。工作根、项目、知识、记忆现在统一使用全宽 standalone owner；返回路径、工作导航、项目对话与 Composer 语义不变。
- 新增 C15 回归先因缺少根页 shared owner 失败、再通过；C15 定向 10/10、Node 全量 305/305、lint、typecheck、静态 build、macOS bundle 和严格验签通过。已保数据覆盖并正常启动 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；SQLite `integrity_check=ok`、86 张表。原生读回确认“工作”横排居中，右侧内容画布占满剩余窗口。替换前版本保留为 `南枫 AI Desktop.pre-work-root-20260912.app`，未卸载或清除数据。

## 2026-09-12：Desktop 一键置底圆圈黑边

- 用户截图中的深色圆圈边不是阴影：`desktop/src/chat-shell.css` 的 `.chat-scroll-to-latest` 自身硬编码了 `border: 1px solid var(--chat-border) !important`。此前“更多操作”、模型 sheet 与窗口记忆的改动均未触及该控件。
- 现在改为 `border: 0 !important`，保留白色圆形表面、40px 命中区、原有柔和阴影与箭头；滚动显示阈值、平滑置底和 Composer 聚焦均未改。
- 现有 FB-P6-043 回归先因缺少无描边行为失败、再通过；Node 全量 304/304、lint、typecheck、静态 build 和重新生成的 macOS bundle 均通过，现装与候选的 Bundle ID／签名团队一致，覆盖后的应用严格验签。
- 已保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app` 并正常启动；覆盖前后工作区 SQLite `integrity_check=ok`、86 张表，数据库大小仍为 307,867,648 bytes。旧 `.app` 完整保留在同目录 `南枫 AI Desktop.pre-20260912.app`，未卸载或清除任何数据。原生窗口读回确认“到最新消息”仅保留柔和阴影、无深色硬边圈。

## 2026-09-12：Desktop 窗口记忆缺失的根因与修复

- 用户反馈“窗口记忆没有”。核对后确认此前不是写入失败：`tauri.conf.json` 只有固定 `1440×900` 默认值，仓库唯一相关测试还明确断言“自动记忆之前的默认窗口尺寸”；Rust 没有窗口状态 owner、原生移动／缩放事件订阅或启动恢复路径。
- 新增 `desktop_window_state_v1`：正常前台启动时在应用配置目录读写设备本地 `window-state.json`，记录普通窗口位置、内部尺寸和最大化状态；移动、缩放和关闭事件节流／原子写入。该状态明确不进入 workspace SQLite、导入导出、备份或加密同步。
- 启动只应用合法尺寸，位置必须仍至少有标题栏区域处在当前任一显示器；外接屏拔除、记录损坏或配置目录不可用时自动保留默认启动，不能让窗口跑到屏外或阻断应用。UI/schema 诊断与后台周期不触碰正式窗口记忆。
- 先新增 `desktop-window-default` 红灯（缺少 owner 文件），后转绿；Node 全量 304/304、lint、typecheck、Rust 全量 230/230、静态 build 与 macOS 候选 bundle 均通过，候选包已严格验签。`cargo fmt --check` 仍因工作树既有 `lib.rs`／`usage_ledger_v1.rs` 非本轮格式差异失败；仅格式化新增 owner 文件。未覆盖安装，也未取得本轮“移动／缩放／退出／重开”原生读回。

## 2026-09-12：Desktop 回答更多操作改为锚定小菜单，模型 sheet 去除背景压暗

- Desktop 回答 footer 的三点“更多操作”此前由全局 `assistant-message-actions` Dialog 加 Scrim 呈现，遮住整个聊天页；现在记录触发按钮的真实矩形，在其下方显示 208px 宽的两行小菜单（空间不足向上翻转并钳制）。菜单无标题／关闭键／页面遮罩，仍只提供“本次回答信息”和“创建分支”；前者会再打开既有详情 Dialog，后者仍走已有分支 owner。
- 该临时菜单的点外关闭会消费本次点击，Esc、窗口滚动与窗口尺寸变化也关闭，以免底层对话在关闭菜单时被误触。此行为只作用于 Desktop，不改 Android 既有 `DropdownMenu`。
- 用户补充的“选择模型”底部 sheet 保持原有层级、关闭按钮和点外 dismiss，仅将 `.composer-model-sheet-scrim` 改为透明，聊天背景不再压暗；不改变模型目录、持久化或联网边界。
- 新增纯锚点计算／渲染回归和透明 scrim 断言。`npm --prefix desktop run lint`、`typecheck`、`test`（303/303）和 `build` 均通过；尚未重打 macOS bundle、未覆盖安装、未做本轮原生视觉读回，也没有触及任何设备数据。

## 2026-09-12：Android 图片预览触控板横滑只切换一张

- 多图原图预览此前把 `preview.id`、图片序列和图片几何作为全屏 `pointerInput` 的 key。触控板一次横滑在第一张切换后仍可能持续发出输入；Compose 因 key 变化重建手势 owner，余下输入会被当成下一次手势，因而一次滑动跨过两张图片。
- 预览视口现在只在真实视口尺寸变化时重建手势 owner。每个新手势开始时才快照当前图片 ID、序列、几何、缩放上限和阈值；切到目标图不会接手尚未结束的旧手势，单次横滑最多切换一张。双指缩放、放大后拖动、长图原比例纵向浏览和原有 56dp 横滑阈值均不改。
- 新增 `P6F2BImagePreviewUiContractsTest` 回归，约束图片／几何变化不能进入 gesture owner key；源码级断言和 `git diff --check` 通过。定向 JVM 任务在 Gradle 配置阶段被依赖校验阻断：Maven `guava-parent-33.4.0-jre.pom`、`junit-bom-5.10.2.module`、`junit-bom-5.11.0-M2.module` 缺少校验哈希；未放宽校验或修改 verification metadata。未构建、未连接模拟器／主设备、未安装或修改设备数据。

## 2026-09-11：Desktop 滚动不再重建输入区

- 截图中的“回复 南枫AI +”被放大、位置跳动，不是独立提示样式错误。根因是消息滚动一旦跨过“是否位于最新消息”阈值，Desktop 会为了显示或隐藏“到最新消息”按钮而对整个应用执行 `render()`；这会销毁并重建 Composer、焦点和所有临时层。
- “到最新消息”现在始终由同一个 Composer dock 节点持有，只通过原地切换 `hidden` 可见性；滚动监听器只同步该按钮，不再重新渲染聊天页面。因此输入框的尺寸、锚点、草稿与临时浮层不会随滚动阈值变化。
- 新增 FB-P6-043 回归：固定验证最新按钮节点在两种状态均存在，且滚动监听器不含 `render()`。Node 267／267、lint、typecheck 与 Rust 226／226 通过。正式 macOS 包已重建、严格验签并在旧进程正常退出后保数据覆盖；SQLite `integrity_check=ok`，保留 7,298 条本地搜索索引。原生长会话连续向上、向下滚动并切换“到最新消息”后，Composer 始终固定在底部，未再出现整块放大或跳位。

## 2026-09-11：Desktop 本机数据分页与工作页独立导航

- “本机数据”中的“全部／正文／图片／视频／音频／文件”此前会把最多 2,000 条本地搜索卡片一次性插入 DOM；当前库有 7,298 条安全索引，WebContent 会持续占用 CPU，表现为点击后卡死。Rust 查询现以 100 条一页返回总数和游标，Desktop 只渲染当前页并提供前后页；手机端的惰性列表语义得以在 Desktop 上保持，不再用一次性全量 DOM 替代。
- 工作、项目、知识、记忆不再复用聊天顶部栏：移除残留的“对话／工作”模式胶囊、临时聊天和聊天操作，改为独立“工作区 / 当前页”页面头部与显式“返回对话”。工作侧栏也只保留工作导航、项目和返回入口，不再夹入定时任务／南枫转写。返回会恢复进入前的普通对话选择。
- Node 267／267、lint、typecheck、Rust 226／226 通过；正式 macOS 包已重新构建、严格验签并在确认旧进程退出后保数据覆盖。原生读回确认：本机数据“全部”只显示 100 条一页，记忆页有独立工作头部且没有聊天模式控件，页面头部“返回对话”可回到原会话。覆盖后 SQLite `integrity_check=ok`，保留 7,298 条本地搜索索引。

## 2026-09-11：Desktop Google 登录回调与卡死根因修复

- 正式 Desktop 发起 Google 授权后，系统浏览器实际返回 `Error 400: redirect_uri_mismatch`。根因不是用户账号：打包脚本把 Android Credential Manager 所需的服务器 Web Client ID 当作 Desktop loopback OAuth Client；Desktop 每次生成 `127.0.0.1` 随机端口回调，Web Client 未登记该 URI，因此 Google 在授权码返回前即拒绝。
- 同时，Tauri 登录命令在打开浏览器前取得共享 SQLite 锁，并在等待 callback 的 180 秒中持有它；400 页面不会回调，其他本机 IPC 因锁等待而表现为软件卡死。现将浏览器授权及头像网络读取拆到无 SQLite 锁阶段，再仅在获得经 Supabase 验证的会话后短暂持锁持久化。失败只回到可重试状态，不写会话、不改本机数据或云端。
- Desktop 不再复用 Android Web Client：仅接受独立 `nanfeng.ai.cloud.googleDesktopClientId` 打包为 `NANFENG_DESKTOP_BUNDLED_GOOGLE_CLIENT_ID`。当前私有配置尚没有该值，因此正式包明确显示“未配置”、不打开浏览器；“查看 Google 登录条件”已补入动作 owner，原生读回确认它说明不会读取账号或上传，而非无响应假按钮。
- Node 265／265、lint、typecheck、Rust 226／226 通过；Rust mock 证明 OAuth 网络阶段可在无 SQLite 写入下完成，随后才持久化会话。正式 macOS 包重建、严格验签并保数据覆盖；覆盖前后 SQLite `integrity_check=ok`，保留 1 个工作区、1,582 个工作区附件与 1,594 个附件资产。真实 Google 完成登录仍缺少 Google Cloud `nanfeng-cloud` 项目中单独创建的 Desktop OAuth Client；控制台凭据页本轮持续加载，未能安全创建或读取该外部配置，不能把本地测试冒充真实登录闭环。

## 2026-09-11：Desktop 临时菜单不再撑满视口

- 正式 macOS 窗口复现：收藏会话的“取消收藏”菜单只含一个动作，却从顶部延伸至接近整页高度。这不是收藏数据或单项样式的问题；共享菜单层把 `span/div` 提升为手动 Popover 时保留了浏览器默认 `inset: 0`，导致其在设置页的网格布局中被拉满。
- 修复共享 owner `installActionMenuLayerOwner`：在测量和锚定前清除各方向定位、固定内容高度与网格行高。故同时覆盖 `.conversation-lifecycle-actions`（收藏／归档／回收站）和 `.memory-reference-menu`（记忆摘要），没有改变动作、权限或持久化链路。
- 新增共享 owner 回归合同；Node 264／264、lint、typecheck 均通过。重新生成并签名 macOS 包后保数据覆盖正式应用；覆盖前后 SQLite `integrity_check=ok`，保留 1 个工作区、1,582 个工作区附件和 1,594 个附件资产。原生读回确认：收藏单项菜单为紧凑浮层；记忆摘要五项菜单也按内容高度展开。点外关闭并消费该次点击的既有行为仍保留。

## 2026-09-11：Desktop 回答成本、来源徽章与打包完整性修复

- 用户在正式 Desktop 看到回答底部孤立的 `i` 与“金额未知”。核对当前 Android `ConversationCostEstimator` 后确认 Desktop 只会显示服务商回传的 `chargeMicros`，漏掉了手机端“实收缺失时按持久模型、Token、时间和价目表做只读本地估算”的链路；这是真实功能缺口，不是文案问题。
- 新增 `desktop-cost-estimator.mjs`，逐项投影 Android 的模型价格规则；服务商实收永远优先，缺失时显示 `≈ ¥…（估算）` 和“本地价目表估算”，不回写账本、不把估算伪装成实收。当前正式记录 `deepseek-flash`（输入 1,063／输出 157）原生回读为 `≈ ¥0.00082（估算）`；总览同时保留“服务商实际金额：金额未知”。
- 回答底部已完全移除来源 `i` 徽章以及死代码／样式；“本次回答信息”和“创建分支”仍只在“更多操作”中。修复过程中发现新估算模块未被静态 build 脚本复制，导致首次包启动空白；已补复制清单和回归测试，防止后续新增启动模块再出现“测试通过、正式包白屏”。
- Node 263／263、lint、typecheck、静态 build、macOS bundle 与严格 codesign 全部通过。正式应用已保数据覆盖并成功启动；覆盖前后 SQLite `integrity_check=ok`，保留 1 个工作区、1,582 个工作区附件和 1,594 个附件资产。盘点确认 225 个 Desktop 可渲染动作均有处理器，130 个本地调用均有 Rust owner 和注册；107 个动作与 43 条调用尚只有间接测试覆盖，仍需逐状态人工审计，不能标作“全端完全验收”。

## 2026-09-11：Desktop 回答“更多操作”标题与分支图标对齐

- 截图对应的是 Desktop 弹层而非 Android。根因是 Desktop 的 `icons` 集合缺少 `branch` 路径，导致“创建分支”虽保留菜单文字却实际输出空 SVG；现补入分支图标，并保留图标与文字作为同一个可点击菜单项。
- “更多操作”标题由通用弹层的 22px 收紧为随 App 字号缩放的 18px／700，菜单行仍保持 48px 点击高度，故只降低视觉噪声而不压缩鼠标和触控命中区。
- Desktop Node 260／260、lint、typecheck、macOS Release bundle 均通过。已验证开发签名并保数据覆盖 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`；覆盖前后 SQLite 完整性均为 `ok`，保留 1 个工作区和 1,582 个附件。原生弹层读回确认标题已收紧且“创建分支”图标可见。

## 2026-09-11：Android 回答信息弹窗整理

- “本次回答信息”改为清晰的纵向阅读结构：先以“回答设置”呈现基础风格和实时网络，再在存在实际本地材料时显示“本次上下文来源”。默认风格不再把相同的字段名和值重复排成两行；每个来源从统一橙色项目符号起排，长标题在同一阅读列自然换行。
- 移除底部的解释性小字；回答级审计仍只展示已持久化的安全事实，不展示正文、Prompt、附件、Provider 原始请求或凭据，也不新增聊天页或 Composer 常驻入口。
- Assistant 的“更多操作”中，“创建分支”改为正文色、`20dp` 圆润 `CallSplit` 图标；图标和文字仍属于同一个菜单项，不再以 16dp 弱化图标呈现。其专项合同把图标、尺寸和正文色绑定在“创建分支”菜单项内，防止只在其他入口保留图标。
- 定向 JVM 合同测试 2／2 与 Release `assembleRelease` 已通过；没有连接模拟器或主设备，尚未取得本轮真机／模拟器视觉读回，也没有安装或修改任何设备数据。

## 2026-09-11：Desktop 本机数据卡片灰度减半

- 浅色模式“本机数据”分类卡片由 `#F2F3F2` 调整为 `#F9F9F9`，即相对白底保留约一半的原始中性灰对比；悬停与按压同步收浅为 `#F5F6F5`／`#F1F2F1`。深色模式和文字、图标、主题色不变。
- 前端 Node 260／260、lint、typecheck 通过；已签名 macOS 包保数据覆盖，并在原生“本机数据”页确认。覆盖前后 SQLite 完整性均为 `ok`，保留 1 个工作区、1,594 个附件与 7,298 条本地搜索索引。

## 2026-09-11：Desktop 应用图标主体放大

- macOS 仅替换应用 `.icns` 及其 1024px PNG 派生资源：从保留的母版 `desktop/src-tauri/icons/nanfeng_ai_icon_master.png`（SHA-256 `c1bec69ca4939632ff7a447be0e9ef9735b4a7097bd450d68da50394e54f1243`）居中放大 1.23 倍后裁回同尺寸画布，使白色马头主体更接近 macOS 图标可见区；不改 Android 启动器资源。
- 可重复生成脚本为 `desktop/scripts/generate-macos-icon.mjs`，它从母版一次生成 `icon.png`、`nanfeng_ai_icon_rgba.png` 与 `nanfeng_ai_icon.icns`。Node 260／260、lint、typecheck 通过，已签名包保数据覆盖；Finder“应用程序”实际图标视图已确认主体放大，覆盖前后 SQLite 完整性均为 `ok`，保留 1 个工作区、1,594 个附件与 7,298 条本地搜索索引。

## 2026-09-11：Desktop 会话行操作区覆盖标题

- 左侧会话行的置顶、收藏和更多三个操作提升为独立顶层操作区；鼠标悬停或键盘焦点进入行时，标题行在右侧预留 104px，不再与三个操作重叠。标题保持单行省略，操作按钮仍可独立点击。
- 前端 Node 260／260、lint、typecheck 通过。macOS 签名包已保数据覆盖；覆盖前后 SQLite 完整性均为 `ok`，保留 1 个工作区、1,594 个附件与 7,298 条本地搜索索引。原生窗口读回确认长标题在三个按钮左侧截断，未重叠。

## 2026-09-11：Desktop 侧栏对话菜单保存本次点击锚点

- 侧栏三点、右键与长按菜单均在触发时保存实际触点的视口矩形；根节点重绘后直接使用该矩形定位，不再以首个或替换后的会话行重新推断坐标。菜单仍受侧栏边界约束，滚动与窗口尺寸变化时关闭，避免悬空。
- 前端 Node 260／260、lint、typecheck 通过。已签名 macOS 包保数据覆盖，SQLite 覆盖前后完整性为 `ok`，保留 1 个工作区、1,594 个附件与 7,298 条本地搜索索引；原生右键读回显示目标会话 `MATCH法案影响阿斯麦 K`，确认没有回退到列表首项。

## 2026-09-11：Desktop 普通聊天与账号登录按 Android 已验证路径对齐

- Desktop 的四个服务商继续使用各自官方端点与凭据，不把模型 ID 转交给其他服务商。路由、请求体与 Android `ChatProviderAdapters` 对齐：OpenRouter 使用服务端搜索工具；Qwen 在其 Chat Completions／Responses 分支使用各自协议；DeepSeek 网页检索走官方 `/responses`；智谱使用 Chat Completions 搜索工具。
- 已修正两个会导致有效回答被错误标失败的 Desktop 终态差异：所有 Chat Completions 路由在收到可见文本后干净 EOF 可完成；DeepSeek `/responses` 不再因没有来源元数据失败，并与 Android 一样只在完整 JSON 解析后一次性提交终态，避免“已显示回答但一直正在生成”。来源仅在服务商实际返回安全 URL 时附加。
- 已在当前已签名 Desktop 应用的 UI 以 `DS V4.1` 发送最小消息并得到完整回复“南烛枫，OK”；数据库最新 Attempt 为 `COMPLETED`、路由 `DEEPSEEK_RESPONSES`。同一已签名应用的 OpenRouter 服务端检索探针返回 `LIVE_PROBE_OK`（2 个增量、2 字节回答）。这些探针只记录数量、耗时和终态，不记录密钥或回答内容。
- Google 设置页此前在 Finder 启动时缺少 shell 环境，因而把已存在的 Android 公共云配置误判为未配置。macOS 打包现在从项目 `local.properties` 读取与 Android 相同的公开 Supabase URL、publishable key 与 Google client ID，编译进签名包；不读取或写入 client secret。已签名包自检 `ACCOUNT_CONFIG_OK`，设置页已呈现可点击的“使用 Google 登录”。实际 Google 账户授权仍由用户在系统浏览器中选择并完成。
- 本次两次保数据覆盖前后 SQLite 完整性均为 `ok`，保留 1 个工作区、1,594 个附件与 7,295 条本地搜索索引。普通聊天传输回归 13／13 通过；最终包已验签。


## 2026-09-10：Desktop 普通聊天将明确流终态写为完成

- 截图中的 `MISSING_COMPLETION` 已定位到 Desktop SSE 完成判定：原实现只接受 `[DONE]`，忽略 OpenAI 兼容流中已明确给出的 `choices[].finish_reason`。当服务端在该终态后直接关闭连接时，已生成的回答被错误持久化为结果未知。
- 现在非空且非 `null` 的 `finish_reason` 与 `[DONE]` 同等视为明确完成证据；没有 `[DONE]` 且没有明确终态的断流仍为 `MISSING_COMPLETION`，保持不自动重发。新增本机 SSE 回归覆盖这两个分支。
- 未读取密钥、未向真实 Provider 发起测试请求。Rust 223／223、前端 Node 260／260、release `cargo check`、lint、typecheck、生产构建通过；macOS bundle 已验签并保数据覆盖，覆盖前后 SQLite 完整性为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引。


## 2026-09-10：Desktop 联网模型配置提示按真实设置刷新

- 启动提示此前复用了 P10-A 双路径安全合同。该合同为了不读取钥匙串而固定报告未配置，实际 `desktop_provider_settings` 与凭据存在性投影未参与判断，造成“联网模型尚未配置”的假提示。
- 现在启动时先显示“正在读取联网模型配置”，再按已启用且凭据已保存的服务商数量显示配置结果；保存模型设置后同样立即刷新。P10-A 页面改为明确它只表达安全合同，不再将“不读取凭据”表述为“未配置”。
- 本机只核对了设置记录和钥匙串凭据存在性，未读取密钥、未发起 Provider 请求；因此“已配置”不等于“连接已验证”。Desktop Node 260／260、lint、typecheck、生产构建通过；macOS bundle 已验签并保数据覆盖，覆盖前后 SQLite 完整性为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引。

## 2026-09-10：Desktop 右上角对话菜单锚点修复

- 右上角三点菜单在点击时保存实际触发按钮的视口坐标；菜单重绘后及下一渲染帧都从该坐标向左展开。此前重绘后重新查询锚点可能失败，导致菜单落回左侧会话区域；侧栏行尾菜单仍各自按行定位。
- 菜单节点明确记录 `header`／`sidebar` 来源，标题菜单不会参与侧栏边界计算。Desktop Node 259／259、lint、typecheck、生产构建通过；macOS bundle 已验签并保数据覆盖，SQLite 完整性为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引。

## 2026-09-10：Desktop 会话行快捷归档改为收藏

- 左侧会话行悬停后的第二个快捷操作由“归档”改为“收藏”：未收藏显示空心书签，已收藏显示实心书签并提供“取消收藏”。操作接入已有本地收藏持久化链；归档仍保留在三点菜单和对话管理中。
- Desktop Node 259／259、lint、typecheck、生产构建通过。macOS bundle 已验签并保数据覆盖，SQLite 完整性为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引。

## 2026-09-10：Desktop 会话列表滑条贴右侧

- Desktop 左侧会话列表的滚动容器向右延展侧栏原有 12px 内边距，并以同等右内边距保留会话正文区域。滑条现在靠近导航栏右缘，标题和日期的可用宽度不缩小；该规则仅在 901px 以上 Desktop 视口生效。
- Desktop Node 259／259、lint、typecheck、生产构建通过。macOS bundle 已验签并保数据覆盖，覆盖前后 SQLite 完整性为 `ok`，工作区、附件及搜索索引数量保持不变。

## 2026-09-10：Desktop 本机数据分类按键灰底还原

- 本机数据页“对话与内容”“附件”下的分类按键曾被浅色主题兜底覆盖为近白 `#FCFCFC`。现恢复为中性灰 `#F2F3F2`，悬停／按压分别使用 `#EAEBEA`／`#E2E4E2`；只影响这些未选中分类按键，不改变主题色或深色模式。
- 为该层级增加静态样式合同。Desktop Node 259／259、lint、typecheck、生产构建通过；macOS bundle 已验签并保数据覆盖，前后 SQLite 完整性为 `ok`，工作区、附件与搜索索引数量保持不变。

## 2026-09-10：Desktop 基础风格和语气弹窗完整呈现

- 风格选择弹窗由固定 620px 高／600px 宽改为 760px 宽、最高占满视口减 48px 的容器。六张说明卡在常规 Desktop 窗口中完整呈现；小窗口由弹窗自身滚动，不再从底部裁切。
- 弹窗与卡片统一使用稳定盒模型、固定两列网格、预留滚动槽及说明文字最小行高；悬停仅改变背景色，不会因边框、滚动条或文字换行改变卡片尺寸和排版。
- Desktop Node 258／258、lint、typecheck、生产构建通过。macOS bundle 已验签并保数据覆盖；覆盖前后数据库完整性为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引，原生启动读回通过。

## 2026-09-10：Desktop 对话更多菜单标准密度

- 对话标题和侧栏行尾共用的临时菜单已统一为 180px 宽、13px／600 文字、18px 图标、42px 行高，并相应收紧内边距与图标文字间距；删除项仍保留既有语义色。此改动只调整共享菜单密度，不改变菜单行为或项目范围。
- Desktop Node 258／258、lint、typecheck、生产构建均通过。macOS bundle 已严格验签、保数据覆盖并启动；覆盖前后 `workspace.sqlite3` 完整性均为 `ok`，保留 1 个工作区、1,582 个附件和 7,288 条搜索索引。原生 AX 读回确认已导入会话、附件预览、标题和侧栏“对话更多操作”入口均正常可用。

## 2026-09-10：Android 手机备份已迁入 Desktop 可见工作区

- **已完成的可见迁移：** 已从已校验的 Android P5-D 备份创建唯一 Desktop 工作区 `workspace-android-p5d-20260910`（`Android 手机数据（2026-09-10）`）。SQLite 读回为 840 条完整对话、5,122 条消息、12 条知识、7 条记忆和 1,582 个私有附件；语义哈希为 `17438bc74fa214036938153939dd9fe6dee178d78a8692fe2a70c7a78cacb08c`。1,582 个附件均已逐项回读 SHA-256，Desktop SQLite `integrity_check` 为 `ok`，应用重启后可显示手机来源会话与本地附件预览。
- **搜索与旧数据清理：** 已重建 Desktop 本地搜索投影：7,288 条记录（4,767 条正文、1,681 条附件），索引语义哈希与工作区一致。用户要求删除的 3 个旧 Desktop 工作区及 454 条旧会话索引已在外键核对通过后删除；删除前快照位于 [`desktop-before-visible-import/p6b-workspace`](/Users/nanzhufeng/Library/Application%20Support/NanfengMigrationBackups/20260910-phone-to-desktop/desktop-before-visible-import/p6b-workspace)。当前数据库仅保留该 Android 工作区。
- **来源与可重做工具：** 原始 3.3 GB 备份、迁移收据和删除记录均在 [`NanfengMigrationBackups/20260910-phone-to-desktop`](/Users/nanzhufeng/Library/Application%20Support/NanfengMigrationBackups/20260910-phone-to-desktop)。可重复执行的流式校验／映射工具是 [`migrate-android-p5d-backup.py`](../desktop/scripts/migrate-android-p5d-backup.py)；它不覆盖同名工作区，支持只重建搜索索引。
- **设置事实：** P5-D 格式没有 SharedPreferences 或加密 Provider 凭据，因此本次备份没有手机外观、个性化、联网／模型选择或密钥可迁入。Desktop 原有设置没有被清除；当前没有连接 Android 设备，不能将缺失于源包的手机设置编造为同步完成。后续若需逐项同步手机设置，须先由手机导出包含安全设置快照的新备份；Provider 密钥仍必须在 Desktop 单独配置。

## 2026-09-10：Desktop Google 登录未配置状态

- 截图中的“使用 Google 登录”不可点击并非命中层级错误：Desktop 仅在原生运行且同时有 `NANFENG_SUPABASE_URL`、`NANFENG_SUPABASE_PUBLISHABLE_KEY` 与 Google Desktop Client ID 时才允许系统浏览器 OAuth。当前运行配置缺失，因此状态为 `NOT_CONFIGURED`；不得用空配置打开浏览器、伪造会话或上传本机数据。
- 未配置状态已改为可点击的“查看 Google 登录条件”。点击只说明缺少南枫云地址、公开访问密钥与 Google Desktop Client ID，并明确不会打开浏览器、读取账号或上传数据；配置完整后同一位置仍显示正式“使用 Google 登录”。
- Desktop Node 257／257、lint、typecheck 通过；最新 macOS bundle 已严格验签并保数据覆盖到 `/Users/nanzhufeng/Applications/南枫 AI Desktop.app`。覆盖后 Desktop 数据根和 `workspace.sqlite3` inode 保持。未启动 Google／Supabase、未读取账号或任何凭据；已运行窗口需完全退出后重新打开才加载新 bundle。

## 2026-09-10：手机迁移源与 Desktop 反复覆盖安装的数据保护

- **已保全的迁移源：** Android 已成功完成一份本机备份并经设备端 SAF 回读校验。副本保存在用户级、应用包外的 [`phone-source/nanfeng-ai-local-backup_2.zip`](/Users/nanzhufeng/Library/Application%20Support/NanfengMigrationBackups/20260910-phone-to-desktop/phone-source/nanfeng-ai-local-backup_2.zip)，大小 3,305,997,003 bytes；Android 端与本机副本 SHA-256 均为 `adb946273b75374a77d7fc624fa49f935b3178147d9568c2ae4b32afa2df224b`。ZIP 结构、逐项哈希和 SQLite `integrity_check` 已独立核对，数据库共 129 张表。不得删除此迁移源或先清空手机数据。
- **Desktop 安装边界：** 正常 Desktop 业务根为 `app_data_dir()/p6b-workspace`，当前实路径是 `~/Library/Application Support/com.nanzhufeng.ai.desktop/p6b-workspace`；路径选择配置独立位于 `app_config_dir()/storage-location.json`。两者均不在 `南枫 AI Desktop.app` 包内，重复替换／安装 `.app` 不会覆盖它们。当前 `workspace.sqlite3` 已在该根内；安装前的 Desktop 快照也保留在同一迁移目录的 `desktop-before/`。
- **失败关闭与回滚：** 数据根迁移只会先锁定旧根、复制至相邻 staging、验证 SQLite，再原子发布；旧根始终保留。已定向验证迁移后重启仍使用新根且源目录仍在、既有目标不覆盖、旧根被占用或目标数据库缺失时停止、空 staging 不发布。正式 Desktop 包更新也要在替换应用包前后核对数据根与快照，不能使用卸载、清目录或重置应用数据作为“测试”。
- **格式边界（已处理可见数据，设置仍待新来源）：** P5-D `.nfai-backup` 是 Android 本机恢复格式，故意排除 Provider 凭据、路由偏好及设备诊断，也不含 SharedPreferences。当前迁移工具已将其可验证业务数据投影为 Desktop 可见会话并完成旧会话清理；手机外观、个性化、联网／模型选择等设置并不在该源包内，不能补写或伪造。v2 工作区交换仍只导出安全的 `uiLanguage`／`theme` 与语义数据，Desktop v2 import 目前仍先进入可校验的私有归档。

## 2026-09-10：Desktop 输入框鼠标提示稳定

- 修复 Desktop 底部输入框随鼠标移动出现并重定位原生提示的问题：移除 textarea 的 `title`，保留 `aria-label`，并以 `aria-description` 提供“可直接粘贴或拖入图片、PDF、视频和文件”的无障碍说明。因此鼠标经过不再触发跟随指针的 WebKit 提示，键盘／辅助功能仍可获得操作说明。
- 专项合同测试、Desktop lint、255 项 Node 测试、静态生产渲染验证通过。Chromium 在 1280×780 下多次移动鼠标前后输入框为 `x=395, y=680, 748×36`，无 `title`、无控制台告警；实际 macOS 应用已重新打包、严格验签、覆盖并启动，AX 读回输入框保留 placeholder 和无障碍说明、不再暴露 Help tooltip。旧应用暂存 `/tmp/nanfeng-desktop-before-composer-tooltip-20260910.app`。

## 2026-09-10：手机灰底还原、电脑服务商与卡片阴影

- 用户纠正：手机设置灰底不在减弱范围。已恢复 `#EDEDED` 并保数据覆盖，仍保留记忆摘要输入框删除。APK 回读 SHA-256 `e2a9f0058d73ada8ee7085be99e32989b88260577baabab67c110830fa8e4567`，与减弱灰底前版本字节完全相同，28,247,385 bytes；同签名非 Debug，首装时间／CE／DE 目录 inode 保持。启动 Activity 成功。
- 电脑端服务商条回调到 85% 宽，轨道 46px／按钮 40px，名称 14px／700；至少 420px 可用范围保护四标签。
- 电脑设置普通输入框／文本域、旧设置分组、调用记录分段和独立清理卡补同款轻阴影；复用 18px 等既有轮廓，不给密钥卡内部输入再叠第二层阴影。浏览器用生产渲染器验证浅／深色 × 1280／780（原生最小宽度）共 8 状态，字段阴影／轮廓、四服务商字体／无截断和密钥内部无重影均通过。
- 255 项 Node、lint、macOS bundle 构建及严格验签通过；电脑端已覆盖并重启，安装字节与构建相符，覆盖过程数据表计数和附件哈希保持。原包暂存 `/tmp/nanfeng-desktop-before-card-shadow-20260910.app`；本轮原生 AX 只确认启动，阴影逐像素结果属于浏览器证据。

## 2026-09-10：设置灰底减弱与 Desktop 移除摘要输入框

- 双端浅色设置背景 `#EDEDED` → `#FAFAFA`；Desktop 另收窄 12 处普通灰卡／路径底等表面，保持灰度差约 30%。主题主按钮／选中态、深色和文字保持；浏览器读回验证普通卡、选中卡、主按钮与路径底，深色未被浅色覆盖。
- Desktop 摘要输入框及提交选择弹窗、状态／处理分支与底部预留移除；实际安装版 AX 确认记忆摘要页面无输入框，右上角编辑入口保留。
- Desktop Node 255 项、lint／bundle 严格验签通过；Android 23 项相关 JVM 测试、Release 构建通过。双端已覆盖并启动。Desktop 安装字节与构建一致，更新期间表计数／附件哈希保持；原 bundle 暂存 `/tmp/nanfeng-desktop-before-settings-light-20260910.app`。
- OPPO 同签名非 Debug 包：28,247,386 bytes，SHA-256 `e4b0b15a5f17f2cdd48aadb4166248388620bd8da227a26ac1fac91ecf94aa67`，版本 0.3.0-p10j／66；安装回读字节一致，首装时间／CE／DE 目录 inode 保持，冷启动成功。未卸载／清数据。灰底逐页真机像素矩阵未重做，浏览器与原生 AX 不替代该证据。

## 2026-09-10：左侧栏轻灰底

- Desktop 浅色侧栏由白色改为中性浅灰 `#F5F5F5`，右侧保持 `#FFFFFF`；统一主题 owner 与初始 CSS，深色令牌不变。七主题投影及浏览器计算色读回通过；后续全套复核发现旧 C16 仍假设 Desktop 侧栏与 Android 同为白色，已在“设置灰底”增量中明确平台差异并恢复 255 项通过。
- 已重新构建、严格验签并覆盖电脑端，AX 确认启动；目标 bundle 字节与构建一致，覆盖过程本机数据表计数及附件哈希保持。原包暂存 `/tmp/nanfeng-desktop-before-sidebar-gray-20260910.app`，未重复安装手机。

## 2026-09-10：移除记忆摘要底部输入框

- Android `MemorySummaryPage` 移除“询问或更新”及对应提交选择弹窗、输入状态和 UI 回调；正文仅保留导航栏安全距离与 24dp 底部间距。右上角编辑／刷新等原操作保留，不改记忆存储。设置合同与两组既有 UI 合同测试同步。
- 2 项相关 JVM 测试通过，Release 构建成功；OPPO 已完成同签名非 Debug 正式包覆盖，安装包回读一致，首次安装时间和 CE／DE 数据目录 inode 保持。产物 28,247,385 bytes，SHA-256 `e2a9f0058d73ada8ee7085be99e32989b88260577baabab67c110830fa8e4567`，版本 0.3.0-p10j／66。未卸载／清数据，未运行仪器测试；此页真机视觉未重新截图验收。

## 2026-09-10：三个行末按钮独立点亮

- 修复整行透明背景规则压住单按钮反馈的问题；保留整行灰底，三个按钮各自使用主题浅底＋主题图标色，按下加深，复用原轮廓。
- 实际会话行渲染器＋完整 CSS 的 Chromium 检查：浅色／深色 × 3 按钮，共 24 项悬停／按下／键盘焦点／相邻按钮未点亮检查通过；既有 255 项 Node 通过。此项仅改 Desktop CSS，不改已覆盖手机 APK。
- 最新 macOS bundle 再次构建／严格验签后已覆盖用户 Applications 并启动；目标与构建包字节一致，更新过程数据表计数和 assets 哈希不变，AX 确认三个独立入口。安装前包暂存 `/tmp/nanfeng-desktop-before-row-feedback-20260910.app`；按钮逐态原生截图仍未获取。机器检查见 [反馈验证](review/20260910/row-action-feedback-verification.json)。

## 2026-09-10：双端覆盖更新

- macOS 最新验签 bundle 已复制到用户 Applications 的「南枫 AI Desktop.app」并启动；安装内容与构建包一致。现有数据库 84 张表计数、12 份 assets 哈希及数据根／数据库 inode 在更新及启动后保持。实际附件图片打开、Escape 返回原会话通过；截图工具不可用，不新增像素验收结论。
- OPPO PKH120 已用同签名非 Debug 正式包保数据覆盖，版本 0.3.0-p10j／66；28,263,769 bytes，SHA-256 `a76cfec266d6e97e3e6b36d0fec352aad9206afcad5a1404e2b02c4f5d00c731`。安装后拉回 APK 字节一致，首次安装时间及 CE／DE 目录 inode 保持，冷启动主 Activity 成功。未卸载／清数据／部署测试包；临时 APK 已清理。
- Android 首次增量 packageRelease 失败；携带 stacktrace 复验构建通过。安装后不再生成或修改该产物。上述启动证据不代替所有业务功能／Provider 验收。

## 2026-09-10：全屏文件预览与全局层级修复

- 六类预览统一应用视口全屏，修复分割线穿透；背景 inert／焦点／Escape 隔离，滚动容器菜单使用 top-layer popover。额外发现 PDF iframe 与 CSP 冲突导致原生空白，改为私有 PDF 当前页本机绘制 PNG。
- Node 255／255、Rust 221／221、lint／typecheck／build 通过；Chromium 39 项（双窗口、六类三态及层级边界），隔离 WKWebView 7 项（六类就绪预览＋菜单）通过。真实合成 PDF 页面非空及上下内容已检查，合成音视频时长均 2 秒。
- 最新 macOS bundle 构建与严格签名验证通过；未覆盖安装、未用真实用户数据运行，WKWebView 验证不等于完整 Tauri IPC 原生端到端。Android 本轮未改动／验收。详见 [修复与未验边界](review/20260910/PREVIEW_LAYER_FIXES.md)。

## 2026-09-10：搜索附件整卡高亮

- 附件卡高亮与预览点击范围扩展到整卡，取消内部局部灰底；三点菜单保持独立命中。255 项前端测试及 build 通过；未覆盖运行中的 macOS bundle，原生点击与视觉未验。

## 2026-09-10：搜索结果主题色高亮

- 搜索文字卡片移除明显边框；悬停／键盘聚焦改为主题色浅底和标题强调，按下稍加深。255 项前端测试与静态 build 通过；未覆盖运行中的 macOS 应用，原生视觉未验。

## 2026-09-10：Android 风格名称字号

- 六项风格名称由 titleLarge 缩为加粗 titleMedium，介于顶部 titleLarge 和说明 bodyMedium 之间；选中／未选中保持同字号。Debug Kotlin 编译与两组相关测试通过，未覆盖安装或做真机视觉验收。

## 2026-09-10：保存路径卡片布局

- 路径卡片移至本机数据总览下方；标题／更改路径按钮同排，完整路径在浅灰独立区域换行显示，删除截图小字说明。255 项前端测试与静态 build 通过，渲染函数读回确认顺序、文案移除和动作保留；未更新正在运行的 macOS bundle，原生视觉未验。

## 2026-09-10：Desktop 服务商切换缩小

- 按截图将服务商分段卡居中缩至 75% 宽度，轨道 52→40px、按钮 44→34px、字号 13→11px；窄窗口保护四个标签。沿用既有配色和轮廓。源码、81 项相关前端测试和静态 build 已验证；headless 浏览器读回超时，未形成渲染验收；未覆盖运行中的 macOS 应用。

## 2026-09-10：DeepSeek V4.1 Flash 双端升级

- Android／Desktop 完整名称、API `deepseek-flash`、当前缓存选择、短名、标题／历史整理提示同步；持久键保留，历史调用不改写。原生图片和联网图片请求已贯通，周末峰谷修正。见 [升级记录](review/20260910/DEEPSEEK_V41_UPGRADE.md)。
- 最终 Android JVM 1113／0／0／3；Debug、Release、Lint 通过；Desktop Node 255／255、Rust 218／218、build／lint／typecheck 通过。Release 初次增量打包异常，带堆栈复验通过，根因未重现；Lint 101 warnings／19 hints。正式 APK 已验签，未安装。见 [机器验证记录](review/20260910/deepseek-v41-verification.json)。
- 保留本次之前的侧栏整行悬停、顶部菜单锚点、三点最右侧修正；当前 macOS 运行应用未覆盖。未做主设备、原生视觉或真实 Provider 验收。官方公告 Pro 9 月 14 日转发边界仍待核验，未提前修改当前 Pro。

## 2026-09-10：四项边界修复

- 用户授权从复盘转入业务修复，基线 `4bf3e00`。按顺序修复目录恢复锁顺序、已配置缺库失败关闭与备份回滚兼容、头像流式上限、Android 已披露接收方的 v2 授权快照；见[修复记录](review/20260910/BOUNDARY_FIXES.md)。此前“未改业务／尚未修复”仅适用于各自历史阶段。
- Rust 全套 216 通过；Android JVM 1109／0／0／3，三个真实 ZIP opt-in 跳过；头像策略／静态合同 7/7，实际 handler 五个模拟场景通过。Debug／Release／Lint 全通过，正式 APK 验签成功、未安装；Lint 保留 101 warnings／19 hints。产物信息见[修复验证](review/20260910/boundary-fix-verification.json)。
- 未安装主设备、未读取真实数据或凭据、未发真实 Provider 请求、未部署在线函数；原生与真实服务验收仍分开。原始发现与红灯保留，新修复不改写历史结论。

## 2026-09-10：完整复盘与边界审查续验

- 已更新完整开发档案、可迁移经验、长期 AGENTS 与五个项目 Skill；全量文件与 Git 清单见 [审计记录](review/20260910/verification.json)。本阶段复盘材料纳入独立本地文档 checkpoint（提交主题 `docs: consolidate full project retrospective and boundary evidence`），业务 checkpoint 仍为 `c4aad94`；未推送或发布。
- [边界复核](review/20260910/BOUNDARY_REVIEW.md)：隔离 Rust 探针 4 通过／2 红灯，确认恢复锁顺序与已配置目录缺库问题；模拟头像 handler 两例均确认超限响应完整读取后才拒绝。Android 接收方授权缺少绑定为源码发现，真实调度未验。
- 本阶段没有修复业务代码、部署或接触真实用户数据。既有全套通过不覆盖上述新增红灯；后续修复应逐项建立行为回归。

## 2026-09-10：最终回归与正式增量固化

- **代码 checkpoint：** `c4aad94`，覆盖上次 `dd3a445` 后累积的 Android／Desktop 源码、测试、Room Schema 66、依赖与验收工具；本地提交，未推送或发布。临时截图、测试报告、APK 与 bundle 不进入代码提交。
- **当前合同读取门：** [Android 会话合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md)、[设置合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md)、[运行时上下文合同](ANDROID_RUNTIME_CONTEXT_CURRENT_CONTRACT.md)、[转写合同](ANDROID_TRANSCRIPTION_UI_CURRENT_CONTRACT.md)、[Desktop 会话合同](DESKTOP_CHAT_FIRST_UI_CONTRACT.md)、[本机数据合同](C14_LOCAL_DATA_PARITY_CONTRACT.md)。这些合同约束行为，历史验收仅证明发生时的版本。
- **Android 增量：** 启动恢复使用退出时实际会话 ID 与时间／生成状态，不再回退置顶首项；记忆摘要支持整篇编辑、并发修订检查、事务替换与失败保稿；设置的六风格全屏选择、居中粗体标题／较小说明已实现；Schema 65→66 保存发送授权时间与披露版本，不记录正文。
- **Desktop 增量：** 设置 patch 串行保存及 SQLite 关闭重开；独立数据路径配置与下次启动迁移；20 个共享动作使用 Android 原始矢量；Composer 根据真实 scrollHeight 在 36–190px 间伸缩。历史 C-01～C-16 已验项不重复宣称本轮新验收。

### 本轮最终回归

| 层级 | 当前结果 |
| --- | --- |
| Android JVM | 1102 tests，0 failures，0 errors，3 skipped；跳过的是需要用户真实 ZIP 路径的 opt-in 测试 |
| Android Debug 构建 | `:app:assembleDebug` 通过，只作本地构建证据 |
| Desktop Node | 251/251 通过 |
| Desktop Rust | 209/209 通过，无 ignored；历史 OAuth callback 单例失败本轮全套未复现 |
| 静态与协议 | lint、typecheck、protocol golden、inventory、静态 build 通过；inventory 的 43 个 invoke 无直接测试引用仍是覆盖提示，不冒充全部入口行为验收 |
| 七主题 computed style | 首轮旧检查失败；修正为生产设置渲染器＋完整 CSS 后，七色 RGB 与无背景图覆盖全部通过 |

- 文档回归曾发现精简入口缺少“当前合同读取门”标识，补回后完整 JVM 再跑仍为 1102／0／0／3；Desktop Node 再跑 251/251。新入口链接检查与最终差异空白检查通过。
- 详细命令和本轮日志保留于 `/tmp/nanfeng-final-20260910/`，摘要已固化在本交接；临时日志可能随系统清理，不能作为唯一长期结论。
- **Release/Lint 收口：** `:app:lintRelease :app:assembleRelease` 通过；Lint 0 errors、101 warnings、19 hints，保留为既有质量债，不称零告警。APK 为 `com.nanzhufeng.ai`，versionCode 66／0.3.0-p10j，非 Debug，正式签名验证通过，28,247,380 bytes；SHA-256 `9b287e107363e7dfe5bac00e674cca6bd3e3e7bc81e1ee3a2c77d9a91142e169`，与引用任务已安装产物一致，无须重复覆盖。
- 本轮没有重打 Desktop 原生 bundle；引用任务已完成相应构建，本次完整 Rust 测试与静态 build 不替代最新原生窗口验收。

### 已有证据与仍未验收项

- 引用任务 `南枫AI 33 - Android` 的 2026-09-10 覆盖记录已经完成：同签名正式包，安装后 APK hash 一致，首次安装时间和 CE／DE 数据目录标识保持。此次没有重复安装，也没有再次读取手机业务数据；该历史安装证据不代表本轮手机视觉验收。
- 原生 Desktop 全图标同步尚未完成；20 个共享图标的替换不等于所有图标已统一。最新 Composer 与 Android 全屏风格页尚未在本轮原生实看。
- Desktop 数据路径仍待原生选目录、重启和重新安装验证；设置虽有本地落盘回归，仍未覆盖所有独立 owner 的跨安装升级。未迁移用户真实数据。
- 本轮未运行模拟器、OPPO、任何 connected Android 测试、真实 Provider、Google／Supabase、通知或远程网关。既有 Sonnet 短消息成功只证明该次探针，不证明附件／长回复／输入到落库全链。
- 下一步若继续产品验收，应从上述未验项选择一个独立增量；当前代码／回归／文档 checkpoint 已收口，不重做历史已完成项。

### 历史证据索引

- [本轮前完整交接归档](archive/CURRENT_HANDOFF_BEFORE_20260910_CHECKPOINT.md)：保留原有全部独有记录，仅调整相对链接以适配归档目录。
- [完整开发档案](南枫AI完整开发档案.md) 与 [可迁移开发经验](可迁移开发经验.md)：追加本轮长期事实；2026-09-01 及之前的数字、快照均为历史。
