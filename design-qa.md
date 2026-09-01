# Android → Desktop Visual Sync QA · 2026-09-01

## Comparison target

- Android live-source reference screenshots: `/Users/nanzhufeng/.codex/visualizations/2026/09/01/01a05b3f-bf39-7580-97c0-0c60854681f3/android-current/`.
- Desktop implementation screenshots: `/Users/nanzhufeng/.codex/visualizations/2026/09/01/01a05b3f-bf39-7580-97c0-0c60854681f3/desktop-current/`.
- Combined comparison inputs inspected at original resolution:
  - `android-desktop-compare-1-20260901.png` — main, model root, plus menu, search.
  - `android-desktop-compare-2-20260901.png` — scheduled tasks, 南枫转写, settings.
- Desktop evidence comes from the latest 2026-09-01 release bundle copied to a unique acceptance Bundle ID and a fresh `/tmp` data root. It does not read the normal Desktop data root.

## Desktop adaptation rule

- Synchronize capability, information hierarchy, icon meaning, card language, state wording, model grouping, and owner behavior.
- Preserve Desktop-only geometry: the large multiline Composer, persistent left navigation, wide search canvas, two-pane settings, two-pane transcription workbench, pointer/keyboard density, and resizable navigation.
- Do not add a fake camera item on Desktop. The plus menu keeps image, file, and live web search because those are the applicable Desktop capabilities.

## Visible comparison result

- Main: Chat/Work identity, short `V4 Flash` model label, warm user bubble, fixed bottom Composer, and shared orange/gray/white design language align. The Desktop Composer remains deliberately larger.
- Model picker: root is `Auto / Daily / Deep`; Daily and Deep enter complete candidate lists instead of silently selecting the first model. The obsolete visible Compare entry is absent.
- Plus menu: image, file, and live web search use the matching icon/card language and real Desktop handlers.
- Search: shared categories, sort/reset controls, bottom search field, file-type preview tile, real size/time, and explicit `预览`/`系统打开` action are present.
- Scheduled tasks: the mobile hierarchy is retained while Desktop uses a wide status canvas and persistent navigation.
- 南枫转写: GLM-OCR image/PDF is the default mode; Qwen audio/video remains a separate tab. Desktop keeps the task list/detail split and settings row needed for wide-screen operation.
- Settings: the same grouped IA and white-card language is presented as a Desktop two-pane settings workbench.

## Interaction and implementation evidence

- Native Computer Use opened and captured main, model root, Daily candidates, plus menu, search, scheduled tasks, both transcription modes, and settings.
- Model availability in screenshots is provided by an app-private local catalog fixture with no credential, endpoint, HTTP request, or provider call.
- Final gate: lint, typecheck, Node `141/141`, static build, Rust `165/165`, macOS bundle, and strict codesign passed with no final failed or ignored tests.
- macOS release bundle and strict codesign verification passed. The development bundle is still ad-hoc and is not a Developer ID/notarized distribution build.
- No `connected*AndroidTest`, OPPO access, real Provider request, Google/Supabase request, or real billing/notification click was performed for this visual pass.

## Deep parity closure

- The revoked first-level judgment has been replaced by a code-level Android/Desktop route matrix and native deep-flow evidence.
- Native Computer Use exercised model roots, add/search paths, reminder create/edit/pause/resume/delete-confirmation, both transcription modes and picker cancellation, settings second/third-level pages, fullscreen custom instructions, local Memory actions, archived/recycle lifecycle, read-only conversation return, import picker, account/local-data/about/diagnostics pages, and search filtering/history/attachment location.
- The final ACL rebuild was rechecked in a unique bundle and strict ordinary-chat acceptance root: create a plan, reopen its confirmed edit form, change the title, save, and read back `最终 ACL 隔离复验计划` from the list.
- Real Provider content, Google/Supabase accounts, OS notification authorization/clicks, destructive confirmations, and Developer ID/notarization remain separate external acceptance boundaries.

final result: passed for local Android → Desktop deep parity; external boundaries remain unverified
