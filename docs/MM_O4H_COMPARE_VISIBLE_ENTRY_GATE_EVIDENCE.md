# MM-O4-H Compare Visible Entry Gate Evidence

## Result

**BLOCKED before implementation.** No existing Android control can explicitly start Compare without changing an already frozen control’s semantics or an existing open surface’s geometry.

## Visual/context evidence

- Chronicle was verified running, but its current fresh frame showed an unrelated application. It was deliberately excluded as Nanfeng AI visual evidence.
- Project evidence `docs/evidence/p6e/android-composer-idle-20260813.png` and `docs/evidence/p6f-040-043-android-current.png` show the preserved normal Composer: its send control and model entry are the only relevant controls in the closed state. They do not show a Compare trigger.
- `UI_LAYOUT_FREEZE_BASELINE.md` freezes the Composer’s fixed dock and requires anchored menus not to change Composer measurement or position. It also prohibits changing an existing control’s independent click surface without new explicit visual authorization.

## Current-source evidence

- `ConversationWorkspace.kt` keeps the root `NormalChatExplicitEgressConfirmationDialog` outside transcript/drawer/composer measurement, but it renders only `NormalChatExternalSendConfirmation`. That source owner reports `EGRESS_UNREGISTERED`, `isConfirmable=false`, and the confirm button has no execution callback.
- The visible normal Composer has one 64dp model entry and one send control. Its model menu is exactly `自动 + P6-G catalog candidates`; adding Compare there changes the menu height. Reusing the model entry changes its existing selection semantic.
- The send path invokes only `submitCurrentDraft()`; it remains local. The existing P3-J static contract explicitly forbids calling the external-confirmation callback from that path, so converting it to Compare would change the ordinary Auto/local-send default.

## Verification and protected scope

- `P3JNormalChatExplicitEgressContractsTest`: 4 tests, 0 failures, 0 errors, 0 skipped.
- No source outside this evidence/handoff update was changed. No UI/owner/Room/AppContainer implementation, build, emulator visual QA, Android install, OPPO operation, Key read, HTTP request, ChatGPT/Claude Compare, Usage fact or receipt was performed.

## Required decision before continuation

The user must either identify an existing UI slot whose interaction may explicitly become “Compare ChatGPT + Claude”, or grant a new trigger’s closed/open-state visual contract. Until then, the existing root Dialog cannot be legally reached as Compare consent.
