# Gemini 联网开关仍被跳过：双端修复与验收（2026-10-14）

截图中的OpenRouter Gemini 3.8 Flash原请求只提供可选server tool；模型可对历史问答直接用记忆作答。此前加搜索指令、短题通过不能保证截图同题搜索。官方明确区分[server tool与web plugin](https://openrouter.ai/docs/guides/features/server-tools/web-search)：前者允许0次调用，后者在请求进入时启用搜索。不是靠正文流畅或自写链接判联网。

双端仅对OpenRouter Gemini Flash启用web plugin／engine=native，保持原模型、Key、渠道、附件字节与搜索指令，不同时发server tool或不兼容required。不换Luna，不修改其他模型正常路线；关闭联网／grounded回答不注入。plugin已被官方标记deprecated，此处作为实测兼容例外登记；未来迁移必须验证同题来源，不能仅按推荐接口名恢复可选搜索。

| 验证层 | 结果 | 范围 |
|---|---|---|
| Android请求回归 | 70PASS；全套1494PASS/10skip、Release/Lint成功 | 普通文字／MD+JSON／图片混合、开关与grounded、原始字节；非全格式真实服务 |
| Mac回归 | 442PASS/34ignored，正式bundle／DMG严格验签成功 | 请求体、来源、终态、计量、错误恢复及既有相邻路径 |
| Android真实 | 同原四个问题＋300字短答约束：正常stop、7个服务端来源、原Gemini；¥0.22218 | 隔离应用进程／原Adapter／Room零网络重开正文、身份、账目保持 |
| Mac真实 | 同原四个问题＋同短答约束：正常完成、5个服务端来源、原Gemini；USD0.032051 | 原生产owner／隔离SQLite重开正文、身份、来源、费用一致 |
| 维护门 | 114合同/40领域、20聊天+1特殊模型/7凭据；15合同工具、16模型工具测试PASS | 离线一致性，不能替代业务验收 |

两次未加短答约束的应用验收被测试用2048 Token上限截断，保留OUTPUT_LIMIT失败，不能算联网或正常完成通过。其金额未获可核定回执，不记零；未自动重试UNKNOWN。另有一次原生plugin协议对照成功。共5次Provider请求，3笔可量化回执合计USD0.09673375，按6.72约¥0.6500508；不是精确总费用。之后停止收费请求。

由截断测试暴露的Mac遗漏已修：JSON保存partial／已返回用量，SSE不在length／content_filter事件立即丢掉后续usage；仍失败、联网false。RC无验证币种的usage.cost仍不认作USD。回复提交、记账前退出后的OUTPUT_LIMIT／CONTENT_FILTER也可本地恢复费用。公开HTTP→Native SQLite、100微USD无Token失败重开红绿通过，不产生真实服务请求；旧丢失费用不补造。

Mac在用户最新“不安装”消息之前已完成原证书保数据覆盖，4个实装文件hash与候选一致，1595个数据文件／6069511187字节逐项hash不变，旧bundle备份保留。Android手机当时已断开，用户确认暂时无法连接：没有开始真机安装，现装仍为08:32包；新正式候选已验签并从build目录复制冻结，等待后续明确安装指令。本轮没有connected测试、主设备Debug、卸载、清库、commit/push。

正式主设备UI、新附件／不同格式／媒体实测、本轮不加短答约束的完整同题回答均未验。已有原报告、RC Pro404及旧Mac原件UNKNOWN边界继续保留，不被此次短答或安装替代。[安全回执](receipts.json)、[源码冻结](protected-source-hashes.json)；完整私有取证与待安装APK在/tmp/nanfeng-gemini-search-repeat-20261014。1145源码／配置／静态资产hash收尾无漂移。前端86 owner未变，复用08:33交付Node551PASS/12skip，没有重跑Node或改UI。
