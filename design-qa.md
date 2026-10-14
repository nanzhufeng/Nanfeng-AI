# Model Settings Design QA

- Verified at: 2026-10-11 17:04 +0800
- Source visual truth: `docs/design/model-settings/selected-integrated-segment.png` (1207×1303 raster). User-approved updates: smaller density, intermediate-gray integral left rail, constant OR-six-row card height, compact family rail, real current model catalog, independent model/Key operations, GLM-OCR and detailed purposes, shorter heading.
- Implementation screenshots: `/tmp/nanfeng-settings-implementation-20261011/rc-phone-current.png`, `openrouter-phone-current.png`, `zhipu-phone-current.png`, `rc-dark-phone-current.png`, `openrouter-dark-phone-current.png`, `openrouter-scaled-phone-current.png`, `rc-desktop-current.png`.
- Latest full-view comparison evidence: `/tmp/nanfeng-settings-implementation-20261011/visual-contact-final.jpg`; final screenshots `openrouter-final-typography.png`, `rc-final-typography.png`, `zhipu-final-typography.png`, `zhipu-dark-final-typography.png`, all 412×947 CSS px at 120% font size. They supersede older typography and left-rail paint; previous desktop structural evidence retained.
- Previous full-view comparison evidence: `/tmp/nanfeng-settings-implementation-20261011/visual-contact-current.jpg`; source and seven final views together. Phone screenshot 412×947 pixels／CSS px; desktop 1200×960. The source is an ideation raster, not a literal CSS-pixel specification; compare shared structure, palette, integrated family selector and proportions, then normalize to user-approved compact layout.
- State: synthetic fixture using actual renderer/CSS/assets, RC Claude and OR/Zhipu selection; light/dark, 100%/120% font size. No actual Key or Provider calls. Latest user corrections: model names/purpose notes both reduced one step, left rail changed to intermediate gray, repeated enable-control subtitles removed, OCR selectable for its own probe.

## Findings

No remaining actionable P0/P1/P2 within browser-rendered scope. Native runtime verification remains a separate gap.

- Typography: actual app font stack and fallbacks retained, 12px model names in semibold, 9px purposes at 1.4 line height, stable hierarchy and room for wrapping; RC three-family tabs 11px. 120% names and descriptions fit, no horizontal document overflow. Source's larger reference density intentionally compressed by user request.
- Spacing/layout: one rounded frame with intermediate-gray left rail and foreground right pane, five platform rows, six OR rows in 376px fixed frame, current list no internal scroll. Other platforms preserve frame height; RC header 38px independently. Two-action footer retained. Desktop includes actual existing settings primary rail.
- Colors/tokens: left rail blends 50% foreground card with 50% current settings canvas; right remains foreground surface, same outer rounded card; selected tint follows existing accent tokens, dark surfaces use existing dark tokens; no added color system. Logos preserve official colors, other controls keep app semantics.
- Assets: eight official source logos recorded in `docs/design/model-settings/official-icons.json`; identical aspect-preserved RGBA PNGs on Android/Desktop, <=96px, packaged into frontend dist. No handcrafted replacement marks. Original eye control retained.
- Copy: heading “服务商与模型”; current catalog and purpose notes; GLM-OCR labeled shared-Key attachment-to-Markdown capability, selectable as a settings-only test target and excluded from ordinary chat presets. No stale Fable/Kimi fixtures or channels masquerading as extra platforms.

## Comparison history

1. [P2] Old shared CSS applied saturated independent tab pills and oversized RC family rail. Fixed scoped selector, shared tinted track, 32px thumb／38px rail; final RC light/dark views and measured heights confirm.
2. [P2] Preview's inherited shell min-width clipped a narrow component-only fixture. Fixed fixture viewport isolation; actual settings shell behavior retained. Latest phone full views have no horizontal document overflow.
3. [P2] Fixed left column was too narrow at 120% typography; user's screenshot confirmed OpenRouter outside selected background. Replaced Desktop fixed track with intrinsic content width at stable weights, Android rail width with TextMeasurer using actual label width. Final scaled phone range bounding-box test: all five labels/icons inside their own button; OR6 list still clientHeight=scrollHeight=432.

4. User-directed follow-up: right model typography 13/10 → 12/9, left rail pure foreground → intermediate gray, enable-control captions removed, OCR read-only note → settings test radio. Current contact sheet verifies all four views, actual OCR selection and button feedback observed, no new actionable P0/P1/P2.

## Interactions and limitations

Provider/family selection, immediate model selection, per-group visibility switch, key reveal, keyboard arrows, dirty-Key gating and disabled Save without Key edits checked; GLM-OCR radio activation and test button feedback checked in fixture and real-owner Node tests. Console error check empty. SQLite reopen/CAS and Android domain tests cover persistence; browser clicks alone make no native persistence claim. Native Android/Mac UI and real Provider tests not run; not installed. Browser test action explicitly reports preview/no request, not simulated connectivity success. OCR request shape and chat-default preservation verified by Kotlin/Rust and real JS owner tests.

## Implementation checklist

- [x] Complete chosen design and later user corrections on Android/Desktop.
- [x] Check typography, spacing, tokens, official assets and copy against source and current screenshots.
- [x] Fix actionable layout and state findings; verify current evidence.
- [x] Record independent automated/native-build scope and remaining runtime gaps.

final result: passed
