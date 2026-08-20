# 南枫 AI P4-N 本地 PDF 文本资料 Adapter 合同

日期：2026-08-13  
状态：P4 的第十三个本地增量；计划 Schema 15→16。P4-N 只处理带文本层的 `application/pdf`，不是 P4 或项目终点。

## 唯一链与所有权

```text
系统 ACTION_OPEN_DOCUMENT（仅 application/pdf）
→ 受限读取、立即复制到 pdf-text-import-assets/v1
→ PdfTextKnowledgeAdapter（逐页、惰性不可信文本）
→ pdf_text_import_tasks / pages / items（Room）
→ 用户逐项预览、确认或跳过
→ KnowledgeDomain → ManageKnowledgeUseCase → Room Knowledge
```

- `PdfTextKnowledgeAdapter` 是 PDF 二进制、页数、对象/流预检、加密、文本抽取和文本上限的唯一解释者；Markdown 与 JSON Adapter 不得调用或扩展它，反之亦然。
- 系统选择器只请求 `application/pdf`。外部 URI、绝对路径和权限 token 仅用于本次受限读取，私有副本完成后立即丢弃；Room、日志和 UI 只允许安全显示名、稳定 ID、受控 storage key、MIME、大小、哈希、状态、版本、计数和安全失败码。
- PDF 内的正文、JavaScript、链接、Launch action、嵌入文件、图片、表单和注释都是不可信数据。不得执行、打开、跟随、提取图片/附件或把任何文本当作指令；本 Adapter 不做 OCR。

## 有界解析与安全失败

- 原文件最大 5 MiB；预检最多 5,000 个对象标记、500 个 stream/filter 标记；PDFBox 以内存上限 2 MiB 的 mixed mode 打开。最多 80 页，每页最多 12,000 Unicode code points、合计最多 120,000；抽取阶段按页持久化进度，任一超限立即安全失败。
- 必须有 `%PDF-` 头；加密、不可提取、畸形、密码保护、无页、无文本层、对象/资源超限、读取/私有复制/抽取异常或高敏正文均为安全失败。失败绝不产生正式 Knowledge，也不保留部分候选。
- 私有原件哈希、抽取版本与哈希、逐页文本哈希、分块/候选版本与哈希、最终 Knowledge ID/状态独立保存。相同私有副本哈希且所有版本相同才能从已完成的页/候选事实恢复；版本或哈希变化使下游产物失效并从私有原件重新处理。此为本地可证明复用，不是 Provider cache、语义摘要或外发。

## 可恢复任务与用户确认

- 任务状态：`SELECTED → PRIVATE_COPIED → PREFLIGHT → EXTRACTING → AWAITING_CONFIRMATION → PARTIALLY_COMPLETED/COMPLETED`，并持久保留 `FAILED/CANCELLED`。进度来自真实页数/已抽取页数与阶段，不由 UI 计时伪造。
- 取消、返回、Activity/进程重建、失败和重试均只依赖 Room + 私有副本；重试不重新请求外部 URI。取消不会删除已持久化任务诊断或私有副本，也不会写正式 Knowledge。
- 每页是一个候选项，默认 `PENDING_CONFIRMATION`。用户可预览抽取文本、编辑标题/正文/标签、确认或跳过；唯一正式写入路径是 `KnowledgeDomain → ManageKnowledgeUseCase` 的 `CREATE_PDF_TEXT_IMPORT`。不自动创建 Context、Memory、关系、去重合并、Prompt、RunSpec、Invocation 或任何 egress。

## 最小 UI、验证与停止点

- Knowledge 页面提供 PDF 导入入口、纯白 `#FFFFFFFF` 任务 Dialog/选择面、阶段/页进度/安全失败原因、取消/重试和逐项确认。不得显示或记录外部路径、URI 或 token。
- 自动测试必须覆盖严格 MIME/头、加密/畸形/无文本/高敏/上限、惰性 JS/链接/嵌入语义、分层 hash 与复用/失效、取消/重试/重建、逐项确认/跳过、Schema 15→16 和 P4-H/P4-L 回归；再分层完成 Lint、正式签名 Debug/Release、v2/v3、仅 emulator-5554 的 DocumentsUI、可见确认/取消或失败、force-stop/重建、`install -r` 和 APK 回拉 hash。
- 不读取 Key、不构造或发送 Prompt/RunSpec、不发 Provider HTTP、不产生费用；`OpenRouterEgressPolicy.Disabled` 保持。不得操作 OPPO、图标、网页/OCR/图片/语音/视频、账号/同步/Hub/Tool/Agent/Desktop/P5+。
