# Opus 普通对话断流排查（2026-10-30）

## 已确认事实

- 11:03 的手机失败记录在运行诊断重载后可见：OpenRouter、`anthropic/claude-opus-5.5`、11.2 秒、`TRANSPORT_IO_EOFException`；会话页显示11.3秒、没有回答、费用未知。这是实际请求记录，不根据输入框当前模型推断。
- 现装正式包1.0.31/code95，拉回SHA256为 `d3abf0302c2b89688a35a5ba74d279ce8c7c9a9a8ec4f74f709f73adc24da34d`，与10月28日已验修复包完全一致。新版1.0.32发布本身不能消除这次失败。
- 手机当前存在 Clash Meta VPN。无凭据公开目录检查返回HTTP200、证书验证0；仅为当前shell连通性，不能证明历史请求、应用UID通路或收费生成恢复，更不能据此归责VPN。
- 系统近期日志未保存原异常堆栈。现有Transport在网络异常时仅记录异常类型，诊断HTTP状态固定为空；无法区分这次EOF来自等待响应头、响应流读取或关闭，也无法从现存记录确定请求字节是否已被服务端接受。该初始阶段尚不知道关闭方与原请求费用；后续服务端取证补出了费用与取消状态，见下节。

## 已修复的独立问题

运行诊断页读取ModelSettingsViewModel初始化时的recentDiagnostics；页面入口仅刷新调用账本，因此同一进程新产生的失败不会立即显示。手机先显示10月19日等旧记录，重开后出现今天唯一失败，代码与现场一致。

入口现在同时调用只读reloadDiagnostics，异步读取已保存诊断并更新投影，不探测Provider、不读取密钥、不改模型与网络设置、不添加UI提示。此修复不代表上述EOF已修复。

## 初始诊断阶段的验证与操作边界

- 相关JVM回归与正式编译结果见checks.json；不运行connectedAndroidTest。
- 设备操作仅包元数据／APK回读、屏幕取证及用户授权的诊断页面导航。确认应用没有活动Service后重开一次以读取持久诊断；旧失败保留，没有点击重试、生成请求、安装、清数据、修改VPN或配置。
- 初始诊断阶段无新收费请求，原错误未重发。后续新增真实调用见下节；它们不能关闭原始故障。

## 继续排查：真实服务及服务端原记录

- 在独立 Android 35 验收包中复用生产 Executor、Adapter 与 `OfficialProviderChatTransport`，实际发送三个新 Opus 请求：单轮官网财报发布日期核验，以及同一进程、同一会话连续两轮财报核验／追问。没有回放、换模型或重试原 UNKNOWN。为收费测试将输出上限压至4096；这与原手机 Android 17／网络及完整参数不等同。
- 三次均为准确 Opus 身份、HTTP200、COMPLETED，分别129／119／105个有效帧，真实正文638／639／318字符，结构化来源4／3／2条；第二轮保留第一轮核验代号。传输头、读流、关闭均正常，没有复现EOF。详细脱敏计数见 [live-checks.json](live-checks.json)。
- 验收脚本原用无排序nodes的lastOrNull选择答案，两轮测试暴露它可能重复拿到第一轮正文／费用。现改为持久化currentLeafMessageId，并补出全部已保存答案供核对；此问题属于验收读取，不是生产对话EOF。只读重开确认两会话／三条服务实际正文均完整保存，当前叶节点对应最终回答，模型／费用保留，重开网络调用0。原始错误验收行保留私有，不覆盖造绿。
- 成功包构建完成，初次验收扩展编译因filter声明顺序失败，已修正后成功。生产Transport没有变更；原诊断刷新回归70项及Release Kotlin编译保持原报告范围，不宣称本轮重新跑全量。未做正式包／主设备覆盖、原生界面验收、提交或发布。
- 新三次保守预算占用¥2.401600，上限¥3，无新增未知费用；临时凭据0、bootstrap已消费、反向端口0，原凭据文件哈希不变。验收数据库／实际请求／回执私有留存。

### 原请求的服务端证据

登录后的OpenRouter日志中，2026-10-30T03:03:27.845Z记录与手机时刻、Opus及Android17 PKH120 User-Agent对应；API Key身份也与新控制样本一致。上游为Claude Platform on AWS，初始HTTP200、单次上游尝试、301ms；最终记录cancelled=true／上游状态499，6745输入Token、57输出Token且全部是reasoning，费用USD0.02812。没有最终正文生成成功的证据。原手机本地未知费用没有被手动篡改成已结算。

服务端明确给出error_ownership=undeterminable、cause=unknown；I/O logging当时未开启，未为排查开启它。客户端仅存EOF类名、没有对应HTTP阶段／堆栈，故不能确定究竟哪一跳先关闭。生产显式Stop走Cancelled分支，读超时90秒、流总时长300秒；未找到11秒主动取消逻辑，不能用延长超时或自动重发掩盖问题。三次成功也不证明原手机链路已修复。

[OpenRouter流取消说明](https://github.com/OpenRouterTeam/docs/blob/main/api_reference/streaming.mdx)及[Cloudflare 499说明](https://developers.cloudflare.com/support/troubleshooting/http-status-codes/4xx-client-error/error-499/)支持“取消记录不能单独定位故障来源”的边界；需要请求关联的边缘字节／断开原因进一步收敛。已准备不含密钥、正文、附件的私有支持排查草稿，用户要求不再追问，尚无明确发送授权，因此未发送。

**状态：真实新对话完整返回与保存重开已验；原EOF关闭发起方和根因修复仍未完成。** 不发布、不重装、不把本轮称为已修复。

### 手机端只读补查

- ConnectivityService持久诊断中11:02–11:04有网络阻止记录，但对应输入法等其他UID；南枫AI的UID为10611，不能将那些BLOCKED行误判为本App断流原因。Clash外部files目录为空，未取得当时的代理连接关闭日志。现有证据仍不足以归责VPN或系统策略。
- 只做既有代理App页面导航／截屏，未切换节点、改网络、开启日志或发请求；无关截图已精确删除，不进入交付。私有传输／服务端证据及数据库保留。专用emulator-5606与本轮创建的AVD已精确关闭删除，原设备及其他模拟器不动。

## 再次续查：系统生命周期与回调路径（2026-10-30）

- 当前手机仍为1.0.31/code95，lastUpdateTime为10月28日15:13:30，实际SDK37；前次真实控制环境为SDK35。未把跨OS／跨网络成功宣称为手机修复。
- 手机UsageStats仅筛选本App／Clash及事故时间：本App11:03:00恢复前台，11:03:25启动NormalChatGenerationForegroundService，11:03:37结束服务，直到11:04:12才离开前台。没有“切后台发生在断流前”的证据。
- ApplicationExitInfo：本App事故后的最近退出是11:12:10的已记录只读取证重开；VPN最新退出记录在10月22日，均没有11:03进程退出记录。不能将本次归因于系统杀App或VPN。
- VPN现存logcat最早仅保留11:54以后，无法覆盖事故时刻。原始系统元数据私有保存，不保留无关App使用记录。
- 沿onAccepted、onTextDelta、Room状态事务、runtime.append与进度广播检查，没有找到11秒主动关闭设置；读取90秒、流总时长300秒。Transport确实把回调异常也捕获为NetworkFailure，但这条回调链未找到直接抛EOFException的实现，不能仅凭这一宽泛catch就称它为原故障根因。没有实施猜测性网络更改。
- 服务端日志刷新仍只有原11:03失败和前次三条成功，未出现新的可关联失败。现有历史记录不能还原缺失的客户端异常堆栈或边缘连接关闭发起方。
- 同时核对[OpenRouter Responses API](https://openrouter.ai/docs/api_reference/responses/overview)：其接口为stateless，store=true／previous_response_id会被拒绝。不能直接套用OpenAI的background／retrieve恢复方案，更不能把重新生成冒称原请求续传。

本次新增收费调用0、生产业务代码改动0、覆盖安装0；原EOF根因仍未确认，修复及用户手机业务验收未完成。


## 2026-10-30 代码、合同与架构复核增量（候选源码，未交付）

### 历史与职责核对

- `git blame` 将生产 transport 的输入流 `use` 收尾路径定位到 `1ded3da7`（2026-08-24）。`d92b0da` 增量是正文尚未写出时的一次 TLS 恢复；`7fc4568` 对该文件的增量为安全错误码与显式完成标记。此次未更换 HTTP 客户端、端点、模型、Provider、Key、请求封包、超时或后台所有权；未发现这些近期提交能直接解释原 11:03 EOF 的证据。不能由此证明所有历史改动均无缺陷。
- 实际普通发送 owner 是 `OfficialProviderChatTransport` → `NormalChatOpenRouterExecutor` → 原 Room / 调用账本。历史 `P3_OPENAI_COMPATIBLE_PROVIDER_HTTP_TRANSPORT_CONTRACT.md` 限定的是默认 Disabled 的独立 core，不能用其“未接线”阶段状态否定当前生产链；当前规则按 C12、运行时上下文及模型服务架构裁决。
- 生命周期证据仍见上节：事故时 App 在前台、FGS 运行，未发现进程被杀或 11 秒本地主动超时。没有据此改变后台服务、网络设置或延长超时。

### 已复现的本地缺陷与最小修复

1. **完整结果被清理异常覆盖**：收到完整回答、协议完成与用量后，输入流关闭抛 EOF，外层 `runCatching` 将其替换成 `NetworkFailure`。生产 transport 的回归先失败于“parsed completion must survive resource cleanup”。输入流现由 transport 单点关闭；已确认终态不被关闭 IO 异常覆盖，未确认终态仍未知。取消优先，FAILED／INCOMPLETE 不因 DONE 或清理而变成功。
2. **断流抹去已知计量**：真实 Executor + Room owner 配合合成 socket，在收到部分正文和明确费用后抛 EOF，旧实现将 9001 微美元丢为 null。原测试已记录在 [stream-regression-red.json](stream-regression-red.json)。现将暂态解码事实附在网络失败结果上，由既有主调用账本持久化；保留 UNKNOWN、原身份、原可见部分及零自动重发，错误字符串不含正文／思考。没有改 schema 或新增计量 owner。
3. 收尾补测另捕获“未完整回答也忽略关闭失败”的风险，已限制为有明确协议终态才忽略清理 IO；不使用 EOF 或部分正文推断完成。

### 覆盖边界与仍未确认项

- 回归覆盖生产传输／解码、普通发送、Room 保存与重开，替换的仅是外部 socket；它是故障注入，不是手机或真实 Provider 的本次复现。
- 原始手机请求只有 57 个思考 Token，没有完整可见回答证据。因此，不能把清理异常缺陷直接认定为原故障根因，不能宣称这次 499／EOF 已修好。此前三条真实成功均发生在本增量之前，不重新标记为候选验收。
- 已确认的超时类型仍沿用原 TimedOut 分支；本增量不声称所有超时、取消与响应大小失败都能保留中途计量。思考内容只在暂态错误结果中保留，未扩大完成后思考补写的数据库合同。
- Desktop `desktop_ordinary_chat_v1.rs` 的 `bytes_stream` 错误分支也直接返回 `Failure::Unknown`，存在相同已解码计量被丢弃的静态风险；尚未故障注入验证、未修改 Mac。它不是 Android 原始 EOF 的归因证据。
- 合同门禁 114 合同／40 域及维护目录 23 聊天模型已通过静态检查，只证明索引／声明一致，不等于逐行审计或全模型真实服务可用。
- 本增量未新增收费请求、未自动重试旧请求、未覆盖手机、未提交或发布。最终回归状态以新增回执为准。

最终定向回归：155 PASS／0 fail／0 skip（含静态合同断言），Debug 生产源码与测试源码编译通过，三项合同门再次通过。详见 [stream-regression-final.json](stream-regression-final.json)。Release 构建、候选真实服务和主设备验收均 NOT RUN。回归曾拦截将普通 DONE 放宽为显式完成的尝试，最终已移除该行为变化，附件原有严格终态测试通过。


## 持续目标增量：本地异常归属与 Android 17 环境

- 用户目标仍为“彻底解决才能结束”，未完成。上一阶段产生了代码修复与红绿证据，归类为 progress；本阶段未重定义完成条件。
- 新红灯证明 `onAccepted`／`onTextDelta` 本地 IO 异常被 `runCatching` 错当 NETWORK（[红灯](callback-regression-red.json)）。现将异常交还既有 Executor 本地中断 owner，保留部分内容、UNKNOWN、凭据擦除与一次请求，不冒充 Provider 故障；明确用户取消仍优先。
- 新红灯证明流中断前已收到 HTTP 200，却在持久诊断中写成 null（[红灯](stage-regression-red.json)）。现保存已知 HTTP 状态与有界本地操作阶段，阶段不推断哪一跳先关闭，不记录原异常正文、Header、凭据或用户材料，也不回填旧记录。
- 最新相邻回归 **176 PASS／0 fail／0 skip**，包括 Transport、Adapter、SSE、Executor／Room、附件失败、历史整理与旧调用边界；三项合同门通过，[精确源码及回执](stage-regression-final.json)。此前 155／156 是历史分阶段测试数，不能覆盖后续源码。
- 官方 SDK37 系统镜像已就绪，创建任务独立 AVD；在其上安装隔离验收包，恢复本任务原有控制数据库并零网络重开，两会话／三条真实回答的正文、归因与账本和旧回执一致，见 [SDK37 回执](sdk37-reopen-check.json)。该 APK 只含前两处修复，不含后来回调归属／阶段增量；SDK37 重开不是手机联网或最新包完整验收。
- 再次只读查看 OpenRouter 原 generation 与 upstream timeline，仍只有初始 200／301ms、后续 cancelled／499、ownership undeterminable；未发现额外关闭方记录。未开启 I/O logging，未向第三方发送支持消息。
- 本阶段 USB 清单没有 OPPO。未新增收费调用：此前保守预算仍 ¥2.401600／¥3，剩余不足验收器既有 ¥1 单次未知费用预留，不降低门槛或偷偷扩大预算。原请求未重试。
- 候选正式构建／Release Lint 正在执行；尚未安装主设备、提交或发布。后续必须核对实际构建结果与签名，不能把启动或离线回归当根因闭环。


## 2026-10-30 候选构建与 SDK37 最终隔离检查

- `assembleRelease + lintRelease + assemblePublicModelAcceptance` 成功，构建 7m38s；Release Lint 0 Error／0 Fatal／113 Warning，未声称零警告。生产与测试源码 hash 仍匹配 `stage-regression-final.json`。
- 正式候选 1.0.32/code96，准确 SHA 与签名见 [candidate-build-check.json](candidate-build-check.json)。签名与事故手机原 APK 相同；自有 SDK37 安装后准确 APK 回读一致，非 Debug，冷启动状态 OK。主手机未连接，因此没有主设备安装；没有提交或发布。
- 最新验收 APK 已含回调异常及操作阶段修复；两会话三回答重开与此前保存的正文／模型／费用逐项相等，0 网络调用，见 [sdk37-final-reopen-check.json](sdk37-final-reopen-check.json)。这是原生持久化验收，不是新的真实 Provider 请求。
- 新增收费 0；保守累计 ¥2.401600／¥3 未变。因已有验收器最低单次预留 ¥1，已询问本轮上限提高至 ¥4，当前尚未获回复，不能借用旧 Terra 授权或降低 guard。原始关闭责任仍未知，新包真实完整返回未验，目标不标完成。


## 用户批准 ¥4 后：正式覆盖与新候选真实服务

- 取得提高本轮上限至¥4及手机连接回复后，仅复用冻结正式包；主设备先拉回现装包验签，随后按 `pm install -r --user 0` 覆盖。准确新包回读一致，appId／dataDir／firstInstallTime／CE-DE inode保留，未卸载、清数据或重发原请求。见 [phone-cover-check.json](phone-cover-check.json)。
- 最新验收包运行于自有SDK37隔离环境，使用生产调用owner及新真实Opus原生搜索；没有重放、假答或改Provider。新样本明确COMPLETED、HTTP200、219有效帧、5原生来源，返回1767字符；重开正文、模型、费用一致且网络0。输出上限4096与主手机原请求参数不完全相同，不能扩大为手机原场景已复现／归因。见 [candidate-live-check.json](candidate-live-check.json)。
- 新调用用量51501输入／1271输出，已知费用USD0.251424，本次App折算¥1.689647。四次已知服务费用合计USD0.551624；既有验收器USD×8保守预算占用累计¥4.412992，超过授权上限¥4，因此立即停止后续收费。并未将预算占用冒称人民币实际结算金额。
- 新发现的验收工具风险：`PublicModelAcceptanceApplication.kt` 用发送前固定`callReserve`比较累计预算，原生服务端搜索会产生发送前不可见输入；¥1预留无法保证请求终态实际计费低于剩余上限。当前记录该缺口并停止继续付费，不静默提高上限、重置累计、降低guard或换更便宜模型。该风险不是原手机EOF的证据，不通过重建已装候选掩盖。
- 源码摘要仍匹配176项回归阶段；本轮没有业务代码更改。真实服务的单次完整返回证明新候选此样本链路可用，不能证明历史断连的关闭方；主手机已安装但未操作其新会话。原目标仍未完全闭环。
