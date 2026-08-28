---
name: nanfeng-ai-development
description: Safely implement Nanfeng AI Android or attachment-gateway changes. Use for new features, behavior fixes, schema changes, Provider integration, Compose UI work, import/export work, or deployment-related code in this repository.
---

# 南枫 AI 开发

1. Read `AGENTS.md`, `docs/CURRENT_HANDOFF.md`, the matching current contract, and the live worktree before editing. Treat old P-stage documents as historical evidence only.
2. Locate the existing owner before adding code: UI/ViewModel, domain UseCase, data adapter, Provider transport, Room store, or background service. Extend that owner instead of putting business logic in a Composable or Activity.
3. For user-visible changes, update the matching current contract and entry audit in the same change. Do not create a persistent chat or settings entry without an explicit product decision.
4. Preserve local-first boundaries: no credentials in source, Room, logs, exports, or test fixtures; use the existing private attachment, credential, audit, import, and restore owners.
5. For Room changes, add a forward migration and focused upgrade coverage. Never use destructive migration, database injection, or a test fixture to claim a real user upgrade is safe.
6. For Provider work, persist the actual receiver/model and attempt state. Do not silently retry `UNKNOWN`, change provider/model during recovery, or turn a UI confirmation into an egress authorization bypass.
7. Run the smallest relevant JVM contracts and compile gate. Hand off visual, real Provider, or device verification as separate evidence rather than implying it from code.
