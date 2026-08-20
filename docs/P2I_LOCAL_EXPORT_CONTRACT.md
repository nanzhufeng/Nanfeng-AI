# 南枫 AI P2-I 最小本地 Knowledge 导出合同

日期：2026-08-12  
状态：已实现并经真实应用私有文件回读验证；不包含导入、分享、系统目录选择、联网或真实服务

## 1. 唯一导出路径

```text
已确认保存的 KnowledgeItem
→ KnowledgeRepository
→ ExportKnowledgePackageUseCase
→ KnowledgeExportStore
→ App 私有 .nfai 文件
→ 同文件 ZIP/Manifest/Payload 回读与 SHA-256 校验
→ KnowledgeExportViewModel / 导出状态 UI
```

- 唯一数据源是 `KnowledgeRepository.listAll()` 中的正式 `KnowledgeItem`，并以 `createdAt DESC, id DESC` 固定排序。`GeneratedCandidate`、Capture Draft、Invocation Ledger、fixture 和 UI 展示文本均不能成为导出来源。
- Room 仍是业务真值；导出是版本化可移植副本，不写入 Room，因此 Schema 保持 `3`，没有 Migration、更没有清库。

## 2. 包格式、安全边界与重复语义

- 文件为应用私有 `exports/knowledge/v1/*.nfai` ZIP 包，固定只有 `manifest.json` 和 `knowledge.json`。
- Manifest 固定记录格式名 `nanfeng-ai.knowledge-export`、协议版本 `1`、导出 ID、生成时间、Knowledge 数量/Schema 版本、附件策略、明确排除字段、载荷文件名、字节数和 SHA-256。
- Payload 记录稳定 Knowledge ID、保存时间、Knowledge Schema、标题和正文、来源类型/安全来源引用/贡献字段、Candidate/Invocation/Provider/Model/Harness 溯源，以及附件的逻辑 ID、MIME、大小与 SHA-256。
- 附件策略为“仅元数据引用”：不输出附件二进制、原图、显示名、本地相对/绝对路径或外部 URI；来源引用和字段名再次按安全字符规则收窄。
- 不输出 API Key、完整 Prompt、完整服务原始响应、Ledger 内容字段、Candidate 草稿字段或未授权附件正文。Candidate/Invocation 只保留已属于正式 Knowledge 的稳定溯源 ID 与安全元数据。
- 每次成功导出创建新导出 ID 与新文件名，绝不覆盖既有包；同一知识事实可产生多个各自独立验证的包。

## 3. 原子性、回读、失败与恢复

- 写入顺序为：同目录 `.part` 临时文件 → ZIP 关闭并 `fd.sync()` → `ATOMIC_MOVE` 落地 → 从该最终文件重开 ZIP → 校验 Manifest/载荷字节数/SHA-256 → 解析字段。只有完整回读成功才返回成功状态。
- 取消发生在写入前、写入中或移动前时返回 `Cancelled`；临时文件会清理，未留下成功包。写入、回读、格式或完整性失败不会以成功状态展示；若最终文件已落地但回读失败会删除该文件。
- UI 仅显示已验证的文件名、应用私有相对位置、大小、SHA-256、Knowledge 数与附件引用边界。空 Knowledge 不创建文件；失败与取消明确说明没有成功文件。
- Activity/进程重建后，导出页只从应用私有目录寻找并重新验证最新有效包；不会把内存状态当作成功证据。该行为不涉及用户选择、系统共享目录或任何网络目录。

## 4. 验证与停止

- 自动文件级合同覆盖：空库、单/多 Knowledge、稳定排序、三类来源、Unicode/换行、安全来源/附件清洗、Candidate 不混入、重复不覆盖、取消、输出目录失败、篡改载荷哈希、同目录 Store 重建后的真实回读。
- API 35 模拟器以正式签名 Debug 覆盖安装后，真实保存并回读一份 `.nfai` 包；冷启动后导出页仍显示同一文件的校验结果。
- 停止于本地导出文件与回读校验。不得在本合同下添加导入、系统分享、系统目录选择、HTTP、真实 Key、真实 Provider、账号、同步、Hub、Agent、复杂 Knowledge 管理或图标修改。
