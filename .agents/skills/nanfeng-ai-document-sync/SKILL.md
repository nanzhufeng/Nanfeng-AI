---
name: nanfeng-ai-document-sync
description: Synchronize Nanfeng AI contracts, handoff, audit, decision, and development-archive documents from current code and verified evidence. Use after implementation, regression, checkpoint, delivery, or when documentation conflicts with the repository.
---

# 南枫 AI 文档同步

1. Inventory the live worktree, relevant tests/build artifacts, and Git commit range before editing documentation. Read only the current contract for the affected UI/runtime domain.
2. Classify every statement as code fact, automated verification, visual/manual verification, device/package evidence, real external-service evidence, historical context, or unverified plan.
3. Use current code and reproducible verification to resolve conflicts. Record the conflict and which source wins; never silently rewrite history into a current claim.
4. Put current behavior in the matching current contract, checkpoint/build/device facts at the top of `CURRENT_HANDOFF.md`, long-lived trade-offs in `decision-log.md`, and historical evidence in its existing record.
5. For a user-visible feature, update the Android/Desktop entry audit rather than recreating a user-facing review page. Do not duplicate one rule in several documents.
6. Keep credentials, private data, raw Provider payloads, device-private paths, and user content out of docs. APK hashes and public package/signature metadata are acceptable only when verified.
7. After edits, run Markdown/link or targeted contract checks where available, `git diff --check`, and verify no stale version/schema/test claim remains at an entry-point document.
8. Report changed files, evidence level, unresolved questions, and the rollback commit/path.
