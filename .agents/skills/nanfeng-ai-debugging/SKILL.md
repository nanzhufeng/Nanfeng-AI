---
name: nanfeng-ai-debugging
description: Diagnose Nanfeng AI Android, Room, Compose, Provider, import/export, gateway, build, or deployment failures with reproducible evidence. Use when a visible behavior, test, build, device overlay, or external request is incorrect or unclear.
---

# 南枫 AI 排错

1. Classify the symptom before changing code: stale contract, compile/build, Room migration/data, private-file/reference count, search/index, Compose state/gesture, package/signature, Provider/streaming/budget, sync/account, or gateway deployment.
2. Read the active owner, its focused tests, the matching current contract, and `CURRENT_HANDOFF.md` top. Do not infer current behavior from an old screenshot, APK hash, schema, historical P-stage paragraph or memory.
3. Reproduce with the smallest read-only path and record the exact command, affected owner, safe input class, failure/skip count, exception and timing. Separate assertion drift, environment/fixture absence, code regression and external-service behavior.
4. For model latency or cost, trace selected preset → actual Provider/model → request options → context/attachment bridge → streaming terminal event → returned Usage/Reasoning → persisted estimate. Never infer reasoning tokens or billing from duration alone.
5. For storage or missing files, compare active owners, unique asset identities, occurrence references and current physical length. Historical receipt byte counts do not prove a file still exists; never delete bytes while a real reference remains.
6. For UI symptoms, inspect state ownership, modifier order, gesture consumption, inset, scroll container, auto-follow and overlay dismissal before adding a visual patch. A dismissal must consume the triggering input.
7. Inspect only safe metadata and code paths. Never extract keys, private conversation text, attachment bytes, raw Provider bodies or a device database for diagnosis.
8. State root cause only after evidence converges. Otherwise report bounded hypotheses and the safest next observation; do not ship a speculative fix.
9. Verify a fix at the same evidence tier as the symptom, then update only the current contract/decision/handoff supported by the observed result.
