---
name: nanfeng-ai-document-sync
description: Synchronize Nanfeng AI contracts, handoff, audit, decision, and development-archive documents from current code and verified evidence. Use after implementation, regression, checkpoint, delivery, or when documentation conflicts with the repository.
---

# 南枫 AI 文档同步

1. Inventory the live worktree, tracked-file structure, relevant tests/build artifacts and Git commit range before editing. Read only the current contract for the affected UI/runtime domain plus its specialized owner contract.
2. Classify every statement as code fact, automated verification, visual/manual verification, device/package evidence, real external-service evidence, historical context, or unverified plan.
3. Use current code and reproducible verification to resolve conflicts. Record the conflict and which source wins; never silently rewrite history into a current claim.
4. Put current behavior in the matching current contract, checkpoint/build/device facts at `CURRENT_HANDOFF.md` top, long-lived trade-offs in `decision-log.md`, consolidated project facts in `南枫AI完整开发档案.md`, and cross-project lessons in `可迁移开发经验.md`.
5. For a user-visible feature, update the Android/Desktop entry audit rather than recreating a user-facing review page. Do not duplicate one rule in several documents.
6. Keep credentials, private data, raw Provider payloads, device-private paths, and user content out of docs. APK hashes and public package/signature metadata are acceptable only when verified.
7. When rebuilding the development archive, replace layered obsolete baselines with one current reading gate; retain old schemas/tests/hashes only in dated history and an explicit conflict table.
8. After edits, run Markdown/link or focused contract checks, `git diff --check`, Skill validation and a bounded stale-claim scan across entry documents.
9. Report changed files, evidence tiers, unresolved questions and the checkpoint/rollback path. Never edit generated memory files as part of project documentation.
