# P6-K ChatGPT ZIP 附件口径契约

状态：当前权威契约。统计事实来自用户明确选择的
`ChatGPT_20260827.zip`，只读取结构与匿名计数，不记录标题、正文或文件名。

## 逻辑键

附件 occurrence 的唯一逻辑键固定为：

```text
(sourceConversationId, sourceMessageId, entryName)
```

- 同一消息中，`message.metadata.attachments[].id` 与 content part 的
  `asset_pointer` 指向同一 entry 时只算一个 occurrence。
- 同一 entry 出现在不同消息时是不同 occurrence，不能按 entryName 合并。
- `metadata.attachments` 是附件清单的权威来源。content pointer 可以印证该清单，
  或补入 ZIP 中确实存在的 entry；悬空的 content-only pointer 不伪装成缺失附件。

## 已验证真实包口径

- 官方附件引用记录：855 条。
- 唯一附件 ID：854 个。其中一个 ID 出现在两个不同消息位置，因此 occurrence 仍为 855 条。
- ZIP 内实际存在且可恢复：853 个唯一附件。
- 官方有引用但 ZIP 中缺失：1 个唯一附件；这是 ChatGPT 导出侧缺失，不是本地恢复丢失。
- 具备官方显示名：851 个；其余 2 个使用安全的文件 ID 回退名。
- ZIP 附件候选：1675 个；扣除 853 个有官方归属的附件后，822 个保持未自动关联。

## UI 必须分别展示

1. 已恢复到原对话的附件数。
2. 官方有引用但导出包中缺少文件的数量，并明确不是本地丢失。
3. 缺少官方对话归属、未自动关联的候选数。
4. 缺少官方显示名、使用文件 ID 回退命名的数量。

后台进度以当前可恢复的唯一附件数为分母；原始引用记录、occurrence、唯一 ID、
实际存在文件不能复用同一个数字。P4 的 receipt 与 provenance 必须使用上述
occurrence 逻辑键，才能正确承载同一附件出现在多个消息的情况。
