---
name: nanfeng-ai-development
description: Safely implement Nanfeng AI Android or attachment-gateway changes. Use for new features, behavior fixes, schema changes, Provider integration, Compose UI work, import/export work, or deployment-related code in this repository.
---

# 南枫 AI 开发

1. Read `AGENTS.md`, the live worktree, `docs/CURRENT_HANDOFF.md` top, and exactly the current contract for the affected domain. Treat old P-stage documents, screenshots, hashes, schemas and test counts as historical evidence only.
2. Locate the existing owner before adding code: Compose/ViewModel, domain UseCase/policy, data/Room/private-file adapter, Provider transport, or background scheduler. Extend that owner instead of adding a second truth or putting business policy in a Composable/Activity.
3. Preserve the product boundary: selection and preview stay local; ordinary Send authorizes only the accurately submitted material to the displayed receiver. Do not restore duplicate confirmations, silently change receiver/model, or broaden attachment scope.
4. For every new model or Provider, wire the complete path: catalog, settings/credential presence, Direct/Compare/Auto, capabilities and attachment bridge, context/output budget, attribution, Usage/cost, safe errors, focused tests, and current documentation.
5. For files, reuse private asset identity plus occurrence references, internal preview, search, OCR/transcription, share/download and last-reference cleanup. Do not create a special file path for one screen.
6. For Room changes, add the next forward migration, export the schema, register it in `AppContainer`, and add focused upgrade coverage. Never use destructive migration, database injection, or a fresh fixture to claim a user upgrade is safe.
7. Persist Attempt/receipt/provenance for external or long-running actions. `UNKNOWN` is user-resolved; background work owns durable state and checkpoints rather than a page coroutine.
8. Keep credentials, recovery material, user text, attachment bytes, raw Provider payloads and private paths out of source, Room audit fields, logs, docs and fixtures.
9. Run focused JVM contracts and the relevant compile/build gate. For checkpoints, also run the full JVM suite and classify failures/skips; hand off visual, device and real-service verification as separate evidence.
