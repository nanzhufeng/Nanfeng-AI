# 南枫 AI P4-H 本地 Markdown Knowledge 导入/导出与可恢复队列合同

日期：2026-08-13  
状态：P4 的第八个本地增量；Schema 12→13。仅支持 UTF-8 `.md` / `.markdown`，不实现 JSON、PDF、Office、网页、语音、视频、通用文件问答、embedding 或网络能力。

## 唯一链与隐私

```text
用户系统文件选择
→ 受限字节读取 → App 私有 task asset
→ MarkdownKnowledgeAdapter（不可信惰性文本）
→ ImportTask / ImportItem（Room）→ 用户逐项编辑/确认
→ KnowledgeDomain → ManageKnowledgeUseCase → Room Knowledge
```

- 文件选择器只请求 Markdown MIME；外部 `content://`、绝对路径和权限 token 在私有复制后立即丢弃，Room 仅保存稳定 Task/Item ID、受控 storage key、安全显示名、MIME、字节数、SHA-256、状态、版本和失败码。
- `MarkdownKnowledgeAdapter` 不执行 Markdown、HTML、代码、链接或其中的指令；不产生 Memory、Prompt、RunSpec、Invocation 或任何 egress。原文件、任务资产、提取正文和正式 Knowledge 是分层事实。
- 任务状态为 `SELECTED → PRIVATE_COPIED → PARSING → AWAITING_CONFIRMATION → PARTIALLY_COMPLETED/COMPLETED`，并保留 `FAILED/CANCELLED`；退出页面与重建只从 Room 回读，不静默丢弃。逐项可确认、跳过、取消，失败保留并可从私有副本重试。
- 确认默认关闭。确认仍通过 P4-E `KnowledgeDomain → ManageKnowledgeUseCase` 的 title/body/tags/scope、高敏、revision 与范围规则；P4-F 仅是后续只读候选，P4-G 不自动建关系、归并或归档。

## 版本化 Markdown 合同

- 严格 UTF-8 decoder，允许并去除 BOM，换行统一为 LF；最大单文件 512 KiB、最多 100 项、每项正文仍受 P4-E 12,000 code point 上限。空白、坏 UTF-8、超限、未闭合 front matter、空正文和高敏内容安全拒绝。
- 一文件默认一项：首个 ATX 标题为标题（无标题使用稳定“导入 Markdown N”），其余正文原样保留；可选 `---` front matter 仅读取 `tags:` 的逗号列表。多项仅由单独一行 `<!-- nanfeng-ai:knowledge -->` 分隔；代码围栏内同形文本绝不是边界。
- 导出只接受用户明确选择的 ACTIVE 正式 Knowledge；Markdown 文件和旁路版本化 Manifest 均原子写入、`fd.sync()`、最终文件回读和 SHA-256 核验。安全元数据仅含标题、正文、标签、scope；明确排除 URI/路径/Key/Prompt/Provider payload/附件字节/关系推断/已删除项/临时选择。导出文本可由同 Adapter 前检；保真范围为标题、正文、标签，scope 仅作为安全 front matter，来源/附件/关系不保真。

## Schema 与验证

- 12→13 仅追加 `markdown_import_tasks`、`markdown_import_items` 及索引；不 wipe、不 destructive migration、不改写 P1–P4-G 或正式附件，且没有自动清理正式附件的 GC。
- 定向合同覆盖 BOM/换行、多项、代码块、Markdown 惰性正文、空白/畸形/高敏/大小、确认/跳过/取消/重建、私有 URI/路径排除、12→13 旧表保留，以及导出筛选/roundtrip。后续仍须独立完成全量测试、Lint、签名构建、仅 emulator-5554 的真实系统选择器链与真正导出范围 UI 验收。
