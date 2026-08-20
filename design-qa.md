# FB-P6-023/024/025 Unified Chat-first Design QA

## Comparison target

- Source visual truth: `/tmp/nanfeng-ai-chat-shell-reference-contact-sheet-4.jpg` (four user-supplied references; the contact sheet was inspected first).
- Product truth: `docs/CHAT_FIRST_INTENT_ORGANIZATION_CONTRACT.md`; visual fidelity and Intent-first / Progressive Disclosure / Object-bound state are one combined gate.
- Intended implementation states: Android Chat root + drawer + Composer and Desktop wide/narrow Chat root + transcript + Composer.
- Required comparison viewport: Android emulator outer-screen approximation and current Desktop `.app`, in the same chat/transcript state. No source density normalization is needed until those captures exist.

## 已实现并通过本地视觉/操作验收

- Desktop code now uses an open Assistant content column, a right-aligned bounded warm-orange USER bubble, message-owned metadata, and an off-bottom-only round return-to-latest action.
- Android code removes root `NavigationBar`/`NavigationRail`, opens into the conversation workspace, routes low-frequency entries to drawer Settings, and projects P6-G local model selection from its owner in Composer/Settings.
- Existing product icon sources and Material/Lucide-equivalent icons are used; no launcher/Dock asset was changed.

## Required fidelity surfaces

- Typography, layout rhythm, colors/tokens, icons/assets, and app copy已在重建的 Android outer-screen emulator approximation 与当前 Desktop `.app` 的同态 chat/transcript 操作面复核。
- 已实际复核 Assistant 开放列、USER 限宽/换行、固定 Composer clearance、drawer 底部 Settings、Chat/Work 的对象 scope 改变、离底回底与 Composer focus；Android 另复核 drawer search/IME，Desktop 另复核窄窗 drawer。
- 参考结构/节奏与产品语义均成立：一个 Composer 的空/对话画布是 Intent 入口而非 Dashboard；metadata、附件、动作与箭头是对象归属的渐进披露；drawer 承接任务与持久治理；Chat/Work 不再把能力模块变成任务开始前目录；角色投影没有卡片化。

## Comparison history

- Android（emulator-5554，2026-08-14）：正式签名 Debug `install -r` 后真实触控 drawer/search/IME、Settings、Chat/Work、普通会话手动 fixture → Auto、global FAST → Auto 与 TEMP 零泄漏的 force-stop/restart readback 均通过；回拉 `base.apk` hash 与产物一致。Computer Use 不可附着 emulator，因此这些是安全 ADB 本机 UI 操作，不是静态截图/UI dump 声称。
- Desktop（当前唯一 ad-hoc bundle，2026-08-14）：真实 Computer Use 完成宽窗 Chat/Work、Settings 本地 fixture、普通会话 fixture → Auto 的完整退出/重开回读，以及窗口拖拽到窄窗后的 drawer 开/关、回底、Composer 本地保存。模型 popover 曾有裁切 P0，已改为可访问原生 select 并重新构建后实操通过。
- 可复核路径、包 hash/签名边界与不越界声明见 `docs/FB_P6_023_024_025_P6G_UNIFIED_SHELL_EVIDENCE.md`。这是本地 UI QA 通过，不等于联网模型或发布验收。

final result: passed
