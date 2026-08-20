# MM-O4-H Compare Visible Entry Implementation Evidence

**Status: implemented, compile/contract-tested, and partially emulator-visual-verified; current direct-execution semantics are contract-guarded. Real Android service execution remains an external-condition gate.**

## Narrow scope

- Android normal chat's existing model menu adds exactly one open-state row: `对比 ChatGPT + Claude`.
- Android normal Composer adds one nearby `对比` entry in the established small pill family. Its existing model trigger keeps its ordinary tap-to-model-menu behavior; its new long-press invokes the same explicit Compare command.
- Desktop has the corresponding normal-chat P6-G model trigger, so it exposes the same menu row, nearby `对比` entry, and long-press disclosure state. Desktop has no registered Compare execution adapter: every entry truthfully reports that it is unavailable and performs neither Key access nor HTTP.
- Drawer, transcript, normal send action, existing model rows, ordinary `Auto`/local-submit semantics, and existing controls' visual treatment remain unchanged. The new entry closes the existing overlay and directly invokes the Compare command; it does not render a second product confirmation Dialog or change Composer/transcript measurement.

## Decision boundary (2026-08-16)

- New Compare or later feature entries, buttons, and interactions may be added autonomously when they follow the established design system and do not disturb current layout relationships.
- Existing confirmed visible buttons themselves, the established design direction, core layout relationships, and the existing visual language remain frozen. Any change to those requires an explicit new authorization and must stop rather than infer permission.
- This increment is explicitly authorized to add the Compare menu row, nearby entry, and long-press behavior only; it does not authorize a Drawer, transcript, normal-send, or ordinary Auto redesign.

## Confirmed path

1. The command uses the verified OpenRouter registry projection and only the logical ChatGPT + Claude pair.
2. The explicit Compare entry is the product command; it has no second product acknowledgement surface. `CompareExecutionApplicationOwner` retains its scoped acknowledgement internally so execution still requires the same bounded scope.
3. Only after the scoped owner grants does `CompareVisibleExecutionOwner` locally commit the selected draft as the user parent, persist the two-branch session, construct the already-reviewed O4-G Android adapter, and make one batch dispatch.
4. Android empty drafts return before the visible execution owner. Missing verified registry, changed draft, attachment, missing Key, or any store/dispatch mismatch fails closed. Desktop has no execution adapter and reports that state without loading a Key or making HTTP.

## Verification performed

- Android `:app:compileDebugKotlin` and `:app:assembleDebug` — passed. The installed Debug APK SHA-256 is `c453ab353f3b50282e01c82fd88e08e6dc9c5890479cc207f6ec7379a8f17969`.
- Android `MMO4HCompareVisibleEntryContractsTest`, `P3JNormalChatExplicitEgressContractsTest`, and `CompareExecutionApplicationOwnerContractsTest` cover normal local send separation, the menu/nearby/long-press callbacks, direct-command semantics, empty-draft early return, scoped acknowledgement data, and attachment rejection.
- Desktop `npm run lint`, source build, and `desktop/tests/chat-first-ui.test.mjs` cover all three Desktop entry forms and the truthful no-owner state; the no-owner handler contains neither a Tauri invoke nor a Dialog creation.
- Isolated Android emulator only: `emulator-5554` / `sdk_gphone64_arm64`, 1140×2616, density 442, font scale 1.0. The physical `3B157F009E800000` device was enumerated but never targeted.
- Same-viewport UIAutomator and screenshots show the ordinary closed Composer retains its existing add, draft, `自动`, and send controls, with a new nearby `对比` control. The existing model trigger exposes `选择模型：自动；长按对比 ChatGPT + Claude`.
- A real emulator tap on `自动` opened the ordinary menu. Its pre-existing rows remain 144 px high in this viewport; the new final `对比 ChatGPT + Claude` row is also 144 px. No existing candidate was removed or compressed. Restart/readback retained the same normal Composer surface.
- Real emulator tap of the new menu row and a 900 ms long press of `自动` were both performed. The current draft was empty, so both correctly returned to the unchanged closed Composer without mutating a conversation, reading a Key, or making HTTP.
- Evidence: `docs/evidence/mm-o4h-compare-emulator-20260816/chat-closed.png`, `model-menu.png`, `long-press-empty.png`, `restart.png`, and paired UIAutomator XML. The two principal screenshots were visually inspected; the local image contact-sheet utility was unavailable.

## Remaining real-execution gate

- Per the no-self-authored-test-text rule, no text or attachment was injected into the emulator.
- A user-provided, short, non-sensitive draft plus separately authorized configured credentials and HTTP are required to verify Android's real direct execution. This is not a confirmation-dialog test and must not be approximated with a fixture, empty draft, Key read, or HTTP request.

## Not performed

- No OPPO operation, Key read, or HTTP request.
- No real ChatGPT+Claude receipt/Usage evidence or model-output acceptance claim exists yet.
