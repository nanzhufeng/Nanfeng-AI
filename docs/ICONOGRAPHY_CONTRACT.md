# 图标语汇合同

> **当前 Android UI 路由（2026-08-26）：** 本文保留图标资产来源、语义映射、可访问名称与 launcher 资产边界。圆润／Q 版外观、暗浅色颜色、设置入口图标区分和实际尺寸以 [Android 当前会话界面合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md) 与 [Android 当前设置界面合同](ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md) 为准；本文不得反向指定尖锐轮廓或过黑外观。

状态：FB-P6-029 CLOSED；FB-P6-033 PASSED（非 OPPO）。常用动作沿用成熟资产，launcher/Dock 仅可由 FB-P6-033 的不可变用户 master 派生。

## 唯一来源

- Desktop：`desktop/src/icon-source.mjs` 只承载 Lucide（MIT）官方 SVG 节点的受控映射；不得新画 path、CSS art、emoji 或裁切截图。
- Android：只使用 AndroidX Material Symbols / Material Icons 的已发布资产；`ic_lucide_ghost.xml` 是既有 Lucide 官方资产，不是手绘替代。
- 每个图标都有可访问名称；Desktop 使用 `title`/`aria-label`，Android 使用 `contentDescription`。视觉 size 与 hit target 可按平台调整，语义不得漂移。
- Launcher/Dock 不属于动作图标映射。Desktop 仍只由 `artwork/source/nanfeng_ai_launcher_icon_master_20260814.png`（SHA-256 `a0335d3c581fbfe155c02a6d7cbcfb6509606604e9136e705c79311441eb27f4`）派生。Android 当前来源是用户明确授权的 `app/src/main/icon-source/nanfeng_ai_launcher_q_rounded_scale90_leaf_filled_source.png`（SHA-256 `442cf528f54abf16ef8bfd7922cfd408b104c2f233e487692bbd291a94b95ef2`）：仅把枫叶内部填为白色实心，并将其上的 AI 字样改回同一橙色以保持可读；不得再裁切、拉伸、改变其余比例、添加托盘、外框或伪阴影。

## P6-H 映射

| 语义 | Desktop | Android |
| --- | --- | --- |
| 置顶 / 取消置顶 | Lucide `pin` / `pin-off` | Material `PushPin` |
| 归档 / 恢复 | Lucide `archive` / `archive-restore` | Material `Archive` / `Unarchive` |
| 复制、分支、分享、编辑 | Lucide `copy` / `git-branch` / `share-2` / `square-pen` | Material `ContentCopy` / 现有分支语义 / `Share` / `Edit` |
| 设置、菜单、更多、回底 | Lucide `settings` / `menu` / `ellipsis` / `arrow-down` | Material `Settings` / `Menu` / `MoreVert` / `KeyboardArrowDown` |
| 新对话、搜索、抽屉 | Lucide `message-circle` + `plus` / 成熟浏览器输入控件 / `panel-left` | Material `ChatBubbleOutline`、`Add`、既有 Search、`Menu` |

## 验收

1. 代码审计无手绘 SVG、emoji 或文本假图标作为常用动作。
2. Desktop/Android 图标语义、outline stroke 与 optical size 在同一动作上对齐；不从参考图裁剪位图。
3. 自动映射测试与真实 UI 对照覆盖：pin、archive、copy、branch、share、edit、select-text、settings、search、new chat、drawer、more、scroll-to-bottom。
