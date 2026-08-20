# P6-K7 实包媒体关系只读审计

日期：2026-08-16  
范围：两份由用户已选择、位于 Desktop app-private staging 的第三方 ZIP；不重新选择、复制、删除、重试或提交。

## 结论

没有可证明的稳定 `message ↔ asset` 关系。所有现有媒体候选继续为 `UNMAPPED_REJECTED`，不进入 Android `PrivateAttachmentRepository`、Desktop attachment owner、Message attachment block 或既有 Preview。

## 匿名统计

| Provider | 安全条目 | JSON | 非 JSON 资产 | ChatGPT mapping message 对象 | message scope 内完整 entry 引用 | message scope 内 SHA-256 引用 | 结论 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| ChatGPT | 733 | 14 | 719 | 7,629 | 0 | 0 | 拒绝映射 |
| Claude | 4 | 4 | 0 | — | 0 | 0 | 无资产，拒绝映射 |

ChatGPT 的资产完整 entry 与 SHA-256 虽会在导出文件清单/逻辑文件目录中出现，但两者不是已解析 mapping message 的一部分；不能把“包内目录列出同一文件”误作消息所有权。Claude 没有非 JSON 资产可供关联。

## 方法和采纳门槛

- 只流式计算非 JSON entry 的 SHA-256；JSON 只在进程内解析以检测与资产完整 entry/SHA-256 的精确相等关系。
- 不输出、不写入正文、外部 ID、附件名、ZIP entry path、账户资料或 Key；审计结果只保留上述安全聚合。
- 可采纳关系必须同时满足：位于 provider 已解析的 message scope；以完整 entry path 或 SHA-256 为直接 identity；可在私有副本重解析中复验；不依赖文件名、逻辑目录、文本片段、时间邻近性或模糊匹配。
- 未满足时不得复制资产、生成 receipt/provenance、建立 attachment block 或显示预览。新 provider/versioned relation schema 先补 parser-first 合成 mapper→owner→receipt/retry/revoke 合同，再另行审计。
