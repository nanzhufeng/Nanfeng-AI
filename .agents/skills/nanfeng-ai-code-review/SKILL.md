---
name: nanfeng-ai-code-review
description: Review Nanfeng AI changes for data safety, Provider egress, Room migrations, import/export integrity, UI contract drift, and regression risk. Use for code review, checkpoint review, architecture review, or before committing an Android or gateway change.
---

# 南枫 AI 代码审查

1. Establish scope from `git diff --stat`, affected owners, and current contracts; preserve unrelated worktree changes.
2. Check ownership: UI delegates to ViewModel/UseCase, domain owns policy, data owns Android/Room/Keystore/SAF, and provider adapters do not decide user-data writes.
3. Check egress: credentials, attachments, context, URLs, request/response bodies, audit records, and error strings must remain within their declared privacy boundary. Require actual receiver/model attribution for outbound calls.
4. Check persistence: every entity change has a forward migration, DAO/transaction behavior, upgrade coverage where needed, and no destructive fallback or hidden test-only write path.
5. Check imports/restores for strict parsing, private staging, atomic owner transaction, provenance/receipt, replay behavior, and fail-closed handling of unknown fields, paths, URLs, and assets.
6. Check UI changes against the three current contracts and entry audit. Ensure actions do not accidentally trigger when dismissing a swipe band or overlay.
7. Check tests honestly: identify stale static anchors separately from behavior regressions, name missing coverage, and do not accept build success as device or service proof.
8. Report findings with severity, file/line evidence, impact, and a minimal safe remediation; do not modify code unless the review request includes a fix.
