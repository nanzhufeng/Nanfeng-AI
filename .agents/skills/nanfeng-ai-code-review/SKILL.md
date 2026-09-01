---
name: nanfeng-ai-code-review
description: Review Nanfeng AI changes for data safety, Provider egress, Room migrations, import/export integrity, UI contract drift, and regression risk. Use for code review, checkpoint review, architecture review, or before committing an Android or gateway change.
---

# 南枫 AI 代码审查

1. Establish scope from a bounded `git diff --stat`, affected owners and current contracts; preserve unrelated worktree changes and review without editing unless a fix was requested.
2. Check ownership: UI delegates to ViewModel/UseCase, domain owns policy, data owns Android/Room/private files/credentials, ai owns Provider protocol, and adapters do not decide user-data writes.
3. Check egress: credentials, attachments, context, URLs, request/response bodies, audit records, and error strings must remain within their declared privacy boundary. Require actual receiver/model attribution for outbound calls.
4. Check persistence: every entity change has a forward migration, DAO/transaction behavior, upgrade coverage where needed, and no destructive fallback or hidden test-only write path.
5. Check imports/restores for strict parsing, private staging, atomic owner transaction, provenance/receipt, replay behavior, and fail-closed handling of unknown fields, paths, URLs, and assets.
6. For a model change, require catalog/settings/credentials, Direct/Compare/Auto, capability bridge, budgets, attribution, Usage/cost, errors and documentation to agree. Reject fake universal native capability or silent fallback.
7. For file/search/OCR changes, require one asset identity/reference/delete model and consistent preview, sorting, open, copy, download and share behavior across entrances.
8. Check UI changes against the three current contracts, relevant specialized contract and entry audit. Dismissing a swipe band, menu or overlay must not trigger the underlying action.
9. Check tests honestly: separate stale static anchors from behavior regressions, name missing device/service coverage, and do not accept build success as product acceptance.
10. Report findings ordered by severity with file/line evidence, impact and minimal safe remediation; if there are no findings, state residual verification gaps.
