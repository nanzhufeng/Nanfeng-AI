# 南枫 AI P2-C 文本捕获与系统分享合同

日期：2026-08-12  
状态：已实现；自动验证待本阶段完整收口

## 1. 唯一链路与范围

```text
手工文本 / Android ACTION_SEND text/plain
→ CaptureTextInput
→ CaptureDraftFactory
→ CaptureTextDraftUseCase
→ CaptureDraftRepository (Room)
→ 最近草稿恢复 / 捕获页
```

- 手工输入与系统文本分享都只创建 `CaptureDraft`，不创建 Knowledge、Candidate、Invocation 或网络请求。
- `AndroidTextShareAdapter` 仅读取 `ACTION_SEND` 且 MIME 精确为 `text/plain` 的 `EXTRA_TEXT`；不接系统图片分享、文件分享或拍照。
- 来源只保留经过 `android-app://` 与包名格式校验的来源包名标签；URI、路径、分享正文以外的系统元数据不写入来源证据。
- `AndroidTextShareIntentGate` 是 Activity 范围的生命周期门禁：同一分享在 `onCreate`、`onNewIntent` 或 Activity 重建后重复投递时只进入一次用例。

## 2. 状态与恢复

| 情况 | 结果 | Room / UI 行为 |
| --- | --- | --- |
| 手工非空文本 | 保存为 `MANUAL_TEXT` 草稿 | 立即显示文本预览；重建从最近草稿恢复 |
| 系统 `text/plain` 分享 | 保存为 `ANDROID_TEXT_SHARE` 草稿 | 显示文本预览及来源类型；不显示包名或 URI |
| 空白手工或分享文本 | `CaptureTextBlank` | 不写库、不覆盖已有草稿，并提示输入文字 |
| 返回/取消图片选择 | 不创建或覆盖文本草稿 | 现有 Room 草稿原样保留 |
| 最近草稿为文本 | `RestoredText` | 不尝试读取图片附件 |
| 最近草稿为图片 | `RestoredImage` | 保留 P2-B 私有附件校验和预览 |

## 3. 非目标

- 不接真实 OpenRouter 或 API Key，不读取或外发文本/图片。
- 不实现 Candidate、Knowledge 正式 UI、系统图片分享、拍照、账号、同步、Hub 或 Agent。
- 不改动图标资源，也不将模拟器 Launcher 作为图标视觉结论。

## 4. 最小验证

- 空白文本拒绝且保留已有草稿。
- 文本草稿 Room 回读后走 `RestoredText`，图片草稿仍走 `RestoredImage`。
- `ACTION_SEND text/plain` 的来源标签清洗与 Room 回读。
- 相同分享的 Activity 生命周期门禁只消费一次。
- 全量单测、Lint、正式签名 Debug/Release 构建，以及 API 35 模拟器的文字、分享、取消与重启恢复验证分开记录。
