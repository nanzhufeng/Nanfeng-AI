# Desktop chat-first UI correction evidence

## Outcome

The Desktop default entry is now a ChatGPT/Claude-style conversation shell: lightweight light sidebar, single chat canvas, centered empty-state composer, conversation history and a bottom profile/settings entry. The existing Project/Knowledge/Memory/relation/Inspector workbench remains available only after the explicit “工作” switch.

The local and online routes remain both visible and independent. The composer presents the local record path, while “模型与联网” shows Provider and encrypted-sync state. The current package truthfully says Provider is not configured; it does not read the saved OpenRouter Key or perform HTTP.

## Code ownership

- `desktop/src/chat-shell.mjs`: chat-first information architecture, conversation selection, local/online display mapping and settings/profile surfaces.
- `desktop/src/chat-shell.css`: scoped light chat shell; legacy workbench CSS is preserved for work mode.
- `desktop/src/app.mjs`: default `pane: 'chat'`, local conversation create/append through the existing typed `mutate_desktop_domain` command, chat/work/settings routing and native dual-path status readback.
- `desktop/tests/chat-first-ui.test.mjs`: conversation sorting/selection, local path states, dual-path mapping, default entry and source/build boundary checks.

## Automated evidence

- Frontend `lint`, `typecheck`, 11 Node tests and static build: passed.
- Rust `cargo fmt --check`, 32 tests and `cargo clippy --all-targets -- -D warnings`: passed.
- `CARGO_NET_OFFLINE=true cargo tauri build`: passed.
- No `tauri-plugin-http` was added; no Key, Authorization, Provider request, Google/Supabase or image call occurred.

## Actual app and visual evidence

- Actual app: `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`.
- Final executable SHA-256: `b3a6e7f949d2c9451e1245cd71a83e4508132578fda3783a9a124a07d053e6a3`.
- Bundle size: 13 MiB.
- Re-signed ad-hoc after bundling; `codesign --verify --deep --strict` passed. `Identifier=com.nanzhufeng.ai.desktop`, `Signature=adhoc`, `TeamIdentifier=not set`; this is not Developer ID, notarized or published.
- Final real Tauri capture: `/tmp/nanfeng-ai-chat-first-v2.png`.
- Source/implementation combined comparison: `/tmp/nanfeng-ai-chat-first-comparison-v2.png`.
- The actual app opened in chat mode and Chat → Work switching exposed the legacy governed workbench only after explicit selection.

## Honest limits

- The actual Tauri webview is not exposing its HTML textarea through the current macOS accessibility tree. Native textarea pointer automation/readback was therefore not promoted as a passed black-box result; typed command wiring plus Rust persistence/reopen tests are the current evidence.
- Compact-window capture, profile-menu visual state and restart-after-new-chat black-box remain follow-up Desktop evidence.
- Real OpenRouter text execution remains intentionally deferred by the user; image execution is not authorized.
