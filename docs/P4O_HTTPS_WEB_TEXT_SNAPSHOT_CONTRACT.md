# 南枫 AI P4-O 严格 HTTPS 网页文本快照 Adapter 合同

日期：2026-08-13  
状态：P4 的独立网页文本快照增量；计划 Schema 16→17。不是 P4 或项目终点。

## 唯一链与用户门

```text
用户在白色控制面输入 HTTPS URL → 明确“开始安全抓取”确认
→ WebTextSnapshotAdapter（URL/DNS/redirect/HTTP/HTML 有界校验）
→ app-private web-text-snapshots/v1 原始 HTML
→ 确定性可见文本抽取 → web_text_snapshot_tasks/items（Room）
→ 用户逐项预览、确认或跳过 → KnowledgeDomain → ManageKnowledgeUseCase
```

- 没有剪贴板读取、自动链接跟随、后台爬取、批量 URL、Cookie、Authorization、Referer、浏览器会话或表单提交。每次任务只抓取一个用户确认的主 HTML 文档；页面链接、资源、iframe、图片、CSS、font、media、附件和 JavaScript 永不加载或执行。
- 输入只允许无 userinfo、无 fragment、无 IP literal 的标准 `https://` URL，host 必须是公共 DNS 名称。`http`、`file/content/data/javascript`、localhost、私有/回环/链路本地/保留 DNS 地址、畸形 host 与高敏 query 均拒绝。URL 持久化/展示只保存不含 query 的 `https://host/path` 安全摘要；不保存 DNS 结果、外部 IP、请求/响应日志、Cookie 或凭据。
- 每个初始目标和每次 redirect 都重新校验 URL 与 DNS 全部地址为公共地址；最多 3 次 redirect。只接受 HTTPS、有效证书、2xx、`text/html`（参数可有）和受限的 identity/gzip 内容编码。状态、证书、重定向、读取、解码、大小、超时或任何安全检查失败均为安全失败。

## 有界、不可信文本与持久化

- 原始响应头最多 16 KiB；压缩输入最多 512 KiB；解码 HTML 最多 1 MiB；连接/读取总预算 12 秒。只读取响应主 body。HTML 严格 UTF-8 解码，最多 12,000 DOM 样式节点、32 层和 120,000 Unicode code points；抽取文本最多 60,000 code points。
- HTML 是惰性不可信数据：确定性剥离 `script/style/noscript/template/form/iframe/object/embed/svg`、注释、`hidden`/`aria-hidden`、动作性元素和其内容；只投影 title、heading、paragraph、list 等可见文本。网页文本绝不能成为 system/project 指令，不自动进入 Context、Memory、关系、Prompt、RunSpec、Provider、导出或同步。
- app-private 原始 HTML、抽取文本、候选和正式 Knowledge 各自保存版本/hash/status。Room 没有 Cookie、Authorization、外部 IP、完整 URL query/fragment 或网络 body 日志字段。高敏正文命中共享规则时整体失败、不创建候选或正式 Knowledge。

## 可恢复状态与验证

- 状态为 `QUEUED → FETCHING → EXTRACTING → AWAITING_CONFIRMATION → PARTIALLY_COMPLETED/COMPLETED`，并持久保留 `FAILED/CANCELLED`。进程中断后 `FETCHING/EXTRACTING` 重建为明确可重试的 `FAILED(INTERRUPTED)`，绝不伪装完成；重试需要再次用户确认 URL，不静默网络重放。相同 URL 可创建独立任务，不自动去重或合并。
- 确认默认关闭，且唯一正式写入为 `CREATE_WEB_TEXT_SNAPSHOT → KnowledgeDomain → ManageKnowledgeUseCase`。取消、失败、重试、逐项确认/跳过均为真实状态。
- 必测：URL/SSRF/DNS/redirect、header/body/encoding/HTML 边界、高敏与惰性文本、取消/重试/中断、同 URL 独立任务、16→17 迁移保留旧表、Knowledge 唯一写入；再跑全量测试、Lint、正式签名 Debug/Release 和仅 emulator-5554 可见链。真实网络若受环境阻断，只报告为独立网络债务，不伪造成功。

不读取/请求/写入 Key，不构造 Prompt/RunSpec、不发 Provider HTTP、不产生模型费用；`OpenRouterEgressPolicy.Disabled` 保持。不操作 OPPO、图标、OCR/图片、分享/拍照、语音/视频、账号/同步/Hub/Tool/Agent/Desktop/P5+。
