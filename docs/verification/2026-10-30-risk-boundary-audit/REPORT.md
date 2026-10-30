# 2026-10-30 共用失败边界、合同与架构复查

## 范围与结论

本轮在用户确认 Opus 现装包已打通、其他 22 模型已有真实 Markdown 附件回执的基础上，检查共用 Transport → 清点／识读 → 分批检索 → 最终回答 → 计量／验收预算链。不是全仓每行语义审核，也不是所有模型、媒体、联网组合的新增真实验收。

确认并修复下列问题。保持原 Provider、模型 ID、协议、Key、Auto 顺序和 UI；不以静默换模型、重发未知请求或增加提示处理失败。没有新增收费、主设备操作、安装或发布。

## 问题、证据与裁决

| 问题 | 代码／旧合同冲突 | 修复与行为验证 |
|---|---|---|
| 未知检索失败继续发最终回答 | Android executor 与 Desktop companion 将“检索失败仍可分析附件”泛化到 UNKNOWN。Android 分批 coordinator 还会把 unknown 丢成字符串，有先前结果时返回 Ready；旧测试直接期待第二次调用。与 C12 的 UNKNOWN 不追加请求冲突 | 双端增加统一停止判定，清点、分批、补检与最终回答均保留原失败。已完成检查点不抹去；显式新重试仍沿用已有缓存。故障测试由实际两次请求转为一次，Desktop 最终回答回调若被执行即失败 |
| 流超时抹去已收到用量 | Android SSE 解码器遇 SocketTimeout／总时限异常直接抛出，外层压成 TimedOut，无 partial 计量 | 已解码事实随内部 NetworkFailure 保留，failureCode 仍为 TIMEOUT；Executor 记 UNKNOWN 和已知费用，原账本接收计量，未知部分不填零，不生成后续请求 |
| SSE 响应大小门只限制可见正文 | reasoning、tool、注释及没有换行的长行可以绕过正文大小检查 | 在 UTF-8 reader 之前按原始响应字节计数。超限保持 ResponseTooLarge、流只关闭一次；合成长 reasoning／comment 用例已红灯复现 |
| 清点的未知状态不完整 | 清点结果缺 unknown，HTTP408／504、缺失终态、读超限与检索 owner 分类不一致 | 将 unknown 从清点保留到执行器；不允许缺终态进入 Ready。检索响应超限同样停止后续调用；已知明确拒绝仍按既有规则处理 |
| 实测预算可能漏记在途／较早未知费用 | 验收 Application 旧实现调用返回后才写预算，且后续成功会清空 unknownReserveMicros | 测试专用 Ledger 在实际网络前持久化预留，完成且费用明确时只结算本笔。模拟重开保留预留、较早未知不被新成功清零、实际费用超过预留仍如实记录。不是服务端硬费用上限 |
| 架构文档声称旧动态能力优先 | MODEL_SERVICE_ARCHITECTURE 与 Android mergeCachedProfiles 当前固定种子覆盖部署／能力／上下文限制的实现冲突 | 以代码为准修正文档；缓存只补健康、观察时间和允许别名。没有改运行时目录，也不把模型目录出现等同媒体实测 |

原始红灯摘要见 [red-check.json](red-check.json)。先修上层后仍红灯，进一步定位到分批聚合丢失 unknown；没有通过放宽断言将失败变绿。原测试中把 HTTP504 当“已知失败可继续分析”的三个夹具改为明确 HTTP503，408／504 另测停止边界。四个已有成功清点夹具省略协议终态，现明确提供 stop；生产缺终态拒绝逻辑没有放宽。

## 实现位置

- Android：`OfficialProviderChatTransport.kt`、`AttachmentSearchInventory.kt`、`AttachmentWebSearchCoordinator.kt`、`RelayCoreWebSearchCompanion.kt`、`NormalChatOpenRouterExecutor.kt`；使用 NetworkFailure 的辅助 owner 同步 TIMEOUT 分类。
- Desktop：`desktop_ordinary_chat_v1.rs` 的 `Failure::stops_followup`、`relaycore_web_search_v1.rs`、`attachment_web_search_execution_v1.rs`。保持既有 quantified 失败计量，不改变数据库或 IPC schema。
- 验收工具：新增 `PublicAcceptanceBudgetLedger.kt` 及测试，`PublicModelAcceptanceApplication.kt` 接入先预留后发送。
- 唯一行为正文：[C12](../../C12_MODEL_NETWORK_PARITY_CONTRACT.md)；架构说明：[MODEL_SERVICE_ARCHITECTURE](../../model-service/MODEL_SERVICE_ARCHITECTURE.md)；收费验收流程：[MODEL_SERVICE_MAINTENANCE](../../model-service/MODEL_SERVICE_MAINTENANCE.md)。

## 验证

最终结果见 [final-check.json](final-check.json)，源码／测试／配置绑定见 [source-hashes.json](source-hashes.json)，共784个文件、收尾漂移0。验证范围为当前 WORKTREE，底层 HEAD 见 [worktree.json](worktree.json)，不是已提交代码。

- Android 全套单测1578 PASS／10 skip／0失败，见 [android-tests.json](android-tests.json)。正式 Release、PublicModelAcceptance 和 Release Lint 全部成功。
- Rust 全套472 PASS／38 ignored／0失败；本轮未生成新的 macOS 正式 bundle。
- 模型协议16、合同治理15、一致性14项测试通过；三项静态门覆盖114合同／40域及23聊天模型，见 [contract-checks.json](contract-checks.json)。静态门不是全合同语义或真实服务证明。
- 正式 Android 1.0.32/code96、非Debug、原签名通过，SHA256 `c6ac19be33a6a77ad0b31698057b5982180635fcd3593c28f00ffc6c77d97507`；验收包 SHA256 `3036da5c4ee6969b4b84b9a48718195c3d00b0a383b9ba4b64f61559089c6a14`。详见 [candidate.json](candidate.json)。两包冻结于本机私有交付目录，未安装主设备／未发布。
- 限定文件 diff 检查通过；全局工作流审计无硬错误。原有 AGENTS 长度软警告及无关 GSAP UI 元数据提示不在本轮修改范围。

中间通过结果不能替代最后补充用例后的回归。只保留脱敏摘要和 hash，不收录原始 Provider payload、用户正文、密钥、设备私有路径或原始失败 XML。

本轮无真实收费调用；此前新增额度 ¥10 中占用 ¥0.221136、未知预留 0 的实测账不改写，旧轮累计也不重置。上一轮真实 22 模型回执及零网络重开属于此前准确候选，参见 [上一轮报告](../2026-10-30-other-model-stream-audit/REPORT.md)，不得直接当作本轮新源码的真实服务验收。

## 保留的边界与未确认项

- 本轮新增代码的真实服务、隔离原生重开、手机新对话未运行；原生图片／PDF／音视频、真实联网搜索和 Desktop 真实链路不写全通过。故障注入验证的是停止、计量和资源边界。
- 原 11:03 EOF／499 的连接关闭发起方仍 UNKNOWN。上述可复现代码缺陷不证明其就是原事故原因；用户确认现装 Opus 可用与历史根因分开记录。
- Desktop 仍用现有 `Failure::Unknown { code }` 表示中途 IO／空闲超时，没有 Android 式 partial 用量载荷；本轮修复其不再继续收费调用，未将其描述为已保存所有中断费用。Android 用户取消／响应超限同样未扩展为携带 partial 费用的结果类型，缺失金额仍不能记零。跨端完整失败事实持久化需要独立覆盖取消竞争、重开和计量恢复，不能通过将 UNKNOWN 改成明确失败强行结账。
- 主设备仍使用用户已确认的上一 Opus 修复包；本轮候选未覆盖。没有提交、推送或 GitHub Release 更新。
