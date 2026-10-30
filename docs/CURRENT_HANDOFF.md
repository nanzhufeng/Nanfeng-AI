# 当前交接

> 当前合同读取门：[全项目合同索引](contracts/CONTRACT_INDEX.md) · [合同治理](CONTRACT_GOVERNANCE_CURRENT.md)。

> 最近增量为 2026-10-30 共用失败边界／合同架构复查。用户已确认现装 Opus 打通；此前22模型真实回执与本轮故障回归分别绑定各自源码，不能混用。原始 EOF 关闭方仍未知。
>
> 全项目状态见 [PROJECT_STATUS.md](PROJECT_STATUS.md)；本页只保留最近阶段与继续工作所需边界。



## 当前：共用失败边界修复，最终回归通过

- 目标：按代码、合同、架构检查遗漏并修复，保留已打通路由。发现 UNKNOWN 在清点／分批检索丢失而继续付费回答、流超时丢已知计量、原始 SSE 大小门漏项，以及验收预算没有网络前落盘预留。双端停止边界和 Android Transport 已补；没有新增 UI、切换 Provider／Key／模型或改 schema。
- Android 最后补齐清点 HTTP408／504、超限与缺失终态后的全套结果1578 PASS／10 skip；Rust472 PASS／38 ignored。正式候选SHA256 `c6ac19be33a6a77ad0b31698057b5982180635fcd3593c28f00ffc6c77d97507`，Release／Lint及原签名通过；准确构建／验签／源码hash见[最终回执目录](verification/2026-10-30-risk-boundary-audit/REPORT.md)，禁止把中间构建作为最终包。
- 本轮新增费用0，旧账与未知费用不重置。之前¥10授权中的新增实测占用仍¥0.221136；本轮没有真实请求。没有覆盖主设备、提交、推送或发布。手机仍是用户确认已通的 Opus 修复包。
- 剩余边界：新候选真实服务／隔离重开未运行；Desktop中途IO／空闲超时和Android取消／超限未扩展partial计量结果类型；缺失金额保留未知，不写零。详细冲突、红绿证据和后续边界见报告，不把这项审计写成所有异常／媒体／联网已验证。
- 接续先核对本目录source-hashes与candidate回执、Git WIP；已有全套回归不无故重跑。主设备覆盖须使用最终验签包并沿用保数据边界；不重试旧UNKNOWN请求。保持原来未跟踪文件及历史证据。

## 前轮：其他 22 模型文本附件真实验证完成

- 用户新增授权 ¥10；独立于旧保守累计 ¥4.412992。两轮 22 模型／22 次真实物理调用，文本＋Markdown 四项事实、HTTP200／COMPLETED、请求／返回模型身份通过；最终候选重开全部 22 会话正文／身份／费用完全一致，零网络调用。新增保守占用 ¥0.221136，无未知预留。不是所有图片／音视频／联网组合通过。
- 已补非流式完整响应关闭异常、附件清点／检索辅助断流计量遗漏；不改路由、不换 Key、不重发 UNKNOWN。双端 Gemini 当前估价、GLM-5.3 官方观察时点估价和 Desktop Terra 漏支已补，历史账本不改写。
- 最终 Android 1570 PASS／10 skip，Release／Lint／验收包成功；Rust相关6、模型协议16、合同治理29通过。正式候选 1.0.32/code96、SHA256 `d9ff370b6e2fc5cec9669a79933d30610e2a746454cbd3ceb3a639015597855d`，同签名／非Debug，尚未覆盖主设备。当前手机仍上一 Opus 修复包，用户已确认其打通。
- 首轮7项预算预检拦截及1项Auto夹具错误均未外发；第二轮仅对这8项使用新材料，已通过。不可重跑两个已提交runId。临时凭据0、bootstrap消费、reverse0、源凭据不变，私有DB及完整回执保留；只退出本轮自有SDK37模拟器，保留AVD数据，不碰其他设备。
- 真实联网搜索／原生图片PDF音视频／Desktop本轮实测未运行。测试预算拒绝无法可靠估价的工具／媒体，不能为“全绿”禁用门禁。详见[本轮报告](verification/2026-10-30-other-model-stream-audit/REPORT.md)及矩阵；没有新安装／提交／发布。

## 前轮历史：正式覆盖完成，新候选真实 Opus 联网完成，原始关闭方未知

- 用户已批准本轮总上限 ¥4 并连接 OPPO。准确正式包 1.0.32/code96、SHA256 `9b9aac2e6c16c125a18aa999c194e08a3a18e1549a2269c2ddddecaec4e52a12` 已同签名保数据覆盖，回读准确；appId／dataDir／firstInstallTime／CE-DE inode 相同，非 Debug。见 [覆盖回执](verification/2026-10-30-opus-eof/phone-cover-check.json)。不重复安装，不重试旧请求。
- 最新 SDK37 验收 APK `106ebff314f501289031dba877ebaa6474b8a3d97e664126985dfa19d596a4b8` 使用生产 Executor／Adapter／Transport，单个新 Opus 原生联网请求 HTTP200、COMPLETED、219 帧、首帧4213ms、回答1767字符、5个原生来源；保存后零网络重开正文／模型／费用相等。见 [真实回执](verification/2026-10-30-opus-eof/candidate-live-check.json)。输出上限4096、隔离身份，不冒充主手机完整业务验收或原故障归因。exec session28136已成功结束，禁止重跑该 runId。
- 本次真实费 USD0.251424，四次已知合计 USD0.551624；本次 App 显示约¥1.689647。既有保守预算算法按USD×8后累计¥4.412992，超过¥4；单次¥1预留未覆盖服务端原生搜索带来的51501输入Token，预留检查不等于硬成本上限。已明确停止新增收费，不能重置累计、降低预留、借汇率变化或从手机手动补测绕过额度。下一付费动作前需修正预算控制并取得覆盖范围的授权；现有候选不会因此重建。
- 私有证据位于既有指针下 `candidate-final/live/`，含真实请求／回执／新数据库备份和重开证据；`live/budget.json` 已同步最大累计，旧原始证据保留。临时凭据清除、bootstrap消费、reverse端口0、来源凭据hash未变。自有 SDK37 已按准确 AVD 身份正常关闭，保留数据；不碰其他模拟器。
- 176项相关回归与Release/Lint证据仍匹配当前源码；本轮未再改业务代码，未提交／发布。主手机目前前台为其他App，未接管操作或触发收费，当时手机新对话未验证；随后用户明确反馈“打通了”，作为用户确认记录，不等同 Codex 在手机上执行的验收。
- 原始11:03 EOF／499关闭方仍UNKNOWN，不能由四条成功逆推原失败原因。已有三个异常处理缺陷的红绿修复、当前真实成功与历史根因分别报告；目标不标完成。支持草稿未发送，用户此前无明确发送授权，不重复询问。

## 2026-10-30 当前：保留架构的异常处理修复候选

- 用户要求彻查代码、合同、架构并保留已打通链路。生产 Transport 的清理异常覆盖结果与流中断丢计量均已红灯复现；原写法从 `1ded3da7` 已存在，不足以归因原始手机 EOF。未换 HTTP 客户端／协议／模型／Provider／Key／超时／后台 owner。
- 仅在原 Transport 保留已解码失败事实并明确输入流清理所有权；原 Executor 将已知主调用计量送原账本，UNKNOWN 不重发。未扩大思考持久化边界、未改 schema、未放宽附件完成判据。
- 新增 `ProviderStreamInterruptionTest.kt` 与 `NormalChatGenerationStabilityTest` 的生产 Opus + Room 故障／重开回归。155 PASS／0 fail／0 skip，三项静态合同门 PASS；[回执](verification/2026-10-30-opus-eof/stream-regression-final.json)绑定候选源码 hash。同步 C12；[完整审查边界](verification/2026-10-30-opus-eof/REPORT.md)。
- **仍未解决原始 11:03 请求的关闭发起方**。此前三条真实成功发生在本候选前，不能冒充新包验收。Mac 字节流失败丢计量为待复现静态风险，超时／取消保留中途计量不在本候选覆盖内。未新付费、未覆盖手机、未提交或发布；不要把候选回归通过称作原故障彻底修好。

## 2026-10-30：Opus EOF 未闭环，诊断旧缓存修复

- [现场报告](verification/2026-10-30-opus-eof/REPORT.md)：现装精确d3abf030包，11:03真实请求为OpenRouter Opus 5.5，11.2秒 `TRANSPORT_IO_EOFException`。没有HTTP状态和传输阶段证据，具体关闭方、正文是否送达、费用仍未知，禁止自动重发旧请求。
- 现场诊断页先显示旧记录，重开后出现今天唯一失败；入口未刷新ModelSettingsViewModel投影已证实。当前WORKTREE两份UI文件仅修本地诊断刷新，不探测Provider；不等于EOF修复。编译／定向测试见报告checks，尚未打包、覆盖、提交或发布。
- 取证私有指针 `/tmp/nfai-opus-diagnostic-path.txt`。APK回读、页面截图与设备网络元数据保留；主设备只做页面导航和一次确认无活动Service后的重开，未清数据、配置、重试或收费调用。
- 后续必须补原请求的服务端／传输阶段证据或可归因复现，才能实施网络根因修复；当前手机shell公开目录HTTP200只证明当时shell可达。不要把升级1.0.32、诊断刷新或测试绿灯当作业务恢复。

## 2026-10-30 续查：三条真实Opus完成，原499取消仍待归因

- [增量报告](verification/2026-10-30-opus-eof/REPORT.md)／[真实回执](verification/2026-10-30-opus-eof/live-checks.json)：新单轮联网＋同进程两轮均由准确Opus真实完成；全部三条正文保存、当前叶及费用重开一致，重开网络0。验收脚本修nodes无序lastOrNull误取旧答案并支持有界两轮；生产Transport未改。
- 原服务端记录已找到：手机Android17 PKH120、11:03、上游先200，57输出全部思考，随后cancelled／499，费用USD0.02812；服务端责任字段undeterminable。不是密钥／模型被拒绝，但具体关闭方仍UNKNOWN，不是已修复。不要凭三条成功抹去原失败。
- 私有证据仍由`/tmp/nfai-opus-diagnostic-path.txt`路由，live目录包含原请求编号、服务端证据、三份实际回执、数据库、零网络重开及支持草稿。三次保守预算¥2.401600／¥3，停止新增收费。支持排查说明未发送；用户要求不再追问，没有明确发送授权，不得自动提交或重复询问。浏览器取证登录已成功，不需再让用户登录。
- 临时凭据／bootstrap／反向端口已清理，原凭据未改；专用5606模拟器和AVD已删除，私有数据库保留；无正式打包、覆盖、提交、发布。原始手机消息／费用记录未改。下一步需边缘连接记录判定关闭发起方，不能猜测性换传输或扩大自动重试。

## 2026-10-30 再次续查：未发现后台／进程退出原因

- [最新补查](verification/2026-10-30-opus-eof/REPORT.md)：系统UsageStats证明11:03失败期间App处于前台且FGS运行；双方进程退出记录均无事故时间退出。VPN旧logcat不可得。手机SDK37与控制环境SDK35不同。
- onAccepted／onTextDelta／Room／runtime／进度回调链未找到11秒关闭或直接EOF来源，广义catch不足以证明原故障由本地回调触发；不以改诊断、拉长超时、换协议或自动重试冒称修复。OpenRouter Responses无状态，不能使用OpenAI后台续取假设。
- 本次新增付费／生产改动／安装均0，原关闭发起方和根因修复继续UNRESOLVED。私有事故时间UsageStats与退出信息已留存，不需重复取同一只读证据。

## 2026-10-28：v1.0.32 双端正式发布完成

- [发布回执](verification/2026-10-28-github-v1.0.32/REPORT.md)：构建源码 `7fc45689117302c01fbb73d0e2eddcaf2014d25d` 已推 main；正式 APK／DMG 已冻结验签，Android 独立模拟器实装读回一致，Mac 精确 DMG 的四份 bundle 内容匹配、独立数据根原生启动及四张真实页面完成。正式 Release 仅两安装包已公开发布，标签准确指向构建源码；两个文件公开回下载 SHA256 匹配，源码三个 CI 检查全成功。README／回执为后续纯文档提交，不重建产物。
- 完整回归已过，原 Rust 云同步红灯只修测试 mock 分段读取；生产云同步不变。无新付费调用、主设备或 `/Applications` 覆盖。截图为空数据未配置 Key，故 Mac 显示“等待可用模型”；隔离窗口已关闭，原应用配置未改。
- 准确字节见回执 artifacts；只沿用最终 `/tmp/nfai-r1032-final/` 两个文件，不重建／重签、不重装主设备、不重发 UNKNOWN。当前发布核对指针 `/tmp/nfai-publish-path.txt`；公开材料已脱敏。

## 2026-10-28：当前Terra真实链路通过，修复包已覆盖

- [验收与安装回执](verification/2026-10-28-terra-live-chain/REPORT.md)：普通编号被宽泛“秘密值”指令误滤；双端读取owner收紧范围，已用同一短音频做真实红绿控制。Android两轮文本、原件PNG/PDF/WAV/MP4混合五值、附件联网2/2官方引用、8会话正文／费用重开与原生显示通过。没有换模型或自动重发；首个Argon IO UNKNOWN原样保留，具体传输根因未证实。
- 原签名正式1.0.31/code95已覆盖OPPO并拉回一致，SHA256 `d3abf0302c2b89688a35a5ba74d279ce8c7c9a9a8ec4f74f709f73adc24da34d`；appId／首次安装／dataDir／CE-DE inode保持，未卸载／清数据／启动主设备App／重试旧消息。不要重复装旧44514c或b91d候选。
- Android1548PASS／10skip、Release／Lint通过；Rust相关21、协议16和23模型目录通过。Desktop本轮真实调用未验，既有全Rust云同步失败未修改。预算上限已获用户批准¥6，保守占用约¥3.696184（含旧UNKNOWN预留¥3，非实际结算）；不再发请求。专用模拟器／AVD已删，临时凭据0、bootstrap消费、反向端口0；私有实际请求／回执／数据库保留于`/tmp/nfai-terra-live-path.txt`所指目录。无commit/push/Release。

## 2026-10-28早前失败记录（历史，当前结果见上）

- [真实验收记录](verification/2026-10-28-terra-live-chain/REPORT.md)：两个新文本请求均经Terra实发、同会话进程重启后上下文正确，正文／计量重开一致。四附件在Argon读取时NetworkFailure(IO,IOException)，原件字节匹配，无有效帧，原选Terra未发出；Attempt为UNKNOWN，不重发。
- 总预算原上限¥3；已知保守占用0.006672＋未知保留3元，非实际扣款。随后用户批准总上限¥6；新的图片/PDF、音频与混合／联网结果见上方当前节，不能把新成功回写为旧UNKNOWN成功。生产403输入未改；私有续作指针`/tmp/nfai-terra-live-path.txt`，旧失败及数据库保留。

## 2026-10-28：Terra正式包已保数据覆盖OPPO

- [安装回执](verification/2026-10-28-gpt-terra-cover/REPORT.md)：原签名、非Debug、1.0.31/code95；安装后拉回APK SHA256 `44514c059995537b3df154fda43f334d7fbb50a4d5587ebeda190327d3ce5487`。appId／首次安装／dataDir／CE-DE inode保持；临时APK已删除。未启动／清数据／重试旧消息／付费调用。不要重复覆盖旧候选；真实服务与Mac主入口仍未验。

## 2026-10-27：日常加入 GPT-6.1 Terra，紧接 Luna 上方

- 双端维护目录、实际选择器、Android 精确注册映射／冷启动种子、Rust 请求与费用估算已同步；23 个聊天部署。独立 ID 保留旧 Terra → Luna 迁移；Auto、默认、后台与历史归因不变。
- [接入与验证记录](verification/2026-10-27-gpt-terra/REPORT.md)：Android1547PASS／10skip，正式编译与Lint通过；JS563PASS／12skip，静态构建通过；Rust465PASS／1既存云同步FAIL／20ignored／21live过滤，Terra请求定向通过。正式候选1.0.31/code95 SHA256 `44514c059995537b3df154fda43f334d7fbb50a4d5587ebeda190327d3ce5487`，原签名、非Debug。10月27日未安装、发布或发送付费请求；10月28日同候选覆盖已完成，见上方回执，不重试旧消息。旧联网和全桌面附件缺口仍保留。

## 2026-10-22：22模型真实四附件／原选回答／重开通过，最终正式包已保数据覆盖主设备

- [报告与逐模型矩阵](verification/2026-10-21-real-attachment-roundtrip/REPORT.md)：Android22模型选定55完整物理调用，88原件字节／110标签金额／身份和COMPLETE消息、COMPLETED runtime与Attempt／22零网络重开一致；Desktop Mercury、Qwen Plus、RC Opus三条新样本真实完成且正文／计量重开一致。不能把历史UNKNOWN改成成功，也不关闭全部联网／来源或全部Desktop22缺口。
- 本轮实际修复：双端观察用途指令避免“材料不可信”被误当无内容；Android首全空白帧缓冲后有效正文一起落盘，Astra同症红绿＋新样本真实完成；Sonnet同Provider已发布响应别名精确映射，原请求ID／原始返回ID和渠道不改。无UI小字／新按钮；预算及Auto夹具错误与产品失败分列。
- 本地最终1543PASS／10skip，Rust468PASS／38ignored，合同114／模型22及正式编译／Lint、原签名验签通过。正式候选1.0.31/code95 SHA256 `b91d232afc0a14aa01a480fa0dbea3a5d09473e0ff91eae0694834279e391988`；已于2026-10-22T08:24:47.786797+08:00[同签名保数据覆盖OPPO并拉回校验一致](verification/2026-10-22-attachment-formal-cover/REPORT.md)。覆盖前实读e4a3ade4旧包；覆盖后为b91d232a最终包，appId／首次安装／dataDir／CE-DE inode保持，远端临时APK已清理。不卸载／清数据／启动应用／重试旧消息，不再重复安装旧候选。
- 私有候选、稳定交付文件指针、完整回执／输入／原生包／数据库／原始网络失败／预算在`/tmp/nfai-real-roundtrip-path.txt`；占用预算含未知保留约12.705元，非实际已扣。没有commit/push/Release或Mac主入口覆盖。


## 继续工作与尚未关闭的范围

1. 先核对当前 checkout、WIP 和 [项目状态](PROJECT_STATUS.md)，按 [合同索引](contracts/CONTRACT_INDEX.md) 定位本次 owner；不要为了接续重做全部合同整理或此前已完成的安装。
2. 最新记录的 Android 22 模型四附件、Desktop 3 条代表路径，只覆盖其报告范围。全部联网／来源相关性、Desktop 全模型及正式原生用户路径仍按对应报告保留缺口；不能从构建或安装推断已验收。
3. 历史 UNKNOWN、TLS／429 失败和未知费用保留，不自动重发原 Attempt，不以新成功样本覆盖原失败。新增付费或设备动作仍按当前任务与授权执行。
4. 最近源码已随 v1.0.32 构建提交推送；正式发布状态见本页最新节。Mac 主入口仍未覆盖。GitHub 产物、某次候选和实际现装包分别核对，不能只按版本号推断一致。
5. 最近记录中的 `/tmp` 指针只是历史材料入口，可能失效；失效不构成重新收费调用或重装的理由。先沿仓库内脱敏报告找证据与实际产物，缺少必要材料时明确报告缺口。

## 历史检索

完整改前原文保存在 [CURRENT_HANDOFF_ARCHIVE_2026-10-23.md](CURRENT_HANDOFF_ARCHIVE_2026-10-23.md)，包含所有历史阶段，未删正文、未改写旧结论。归档与本页位于同一目录，原有相对链接的解析位置保持一致。

需要旧修复、合同阶段或候选 hash 时，在归档内按日期和 1–2 个任务关键词定位相应段落；不要默认加载整个归档。旧记录只作历史证据，可能已被后续阶段部分替代；应按具体范围核对，不能整段机械当作当前状态。
