---
name: nanfeng-ai-debugging
description: Diagnose Nanfeng AI Android, Room, Compose, Provider, import/export, gateway, build, or deployment failures with reproducible evidence. Use when a visible behavior, test, build, device overlay, or external request is incorrect or unclear.
---

# 南枫 AI 排错

1. Classify the symptom before changing code: static contract drift, compile/build failure, Room migration/data failure, UI interaction, device/package issue, Provider/network issue, or gateway deployment issue.
2. Read the current owner, its focused tests, the relevant current contract, and the latest `CURRENT_HANDOFF.md` record. Do not infer current behavior from old screenshots, hashes, or historical P-stage text.
3. Make the smallest read-only reproduction. Capture the exact command, input class, failure count, exception, and affected file/owner. Separate a test assertion mismatch from a product failure.
4. For data or egress issues, inspect only safe metadata and code paths. Never extract API keys, private conversation data, attachment bytes, or device databases to diagnose a failure.
5. For UI symptoms, inspect modifier/state ownership and gesture consumption before adding visual patches. For scroll/drawer issues, verify scroll container, inset, auto-follow, and overlay ownership.
6. State the root cause only after evidence converges. If it does not, report competing hypotheses, the safest next observation, and do not ship a speculative fix.
7. Verify a fix at the same evidence tier as the symptom, then update the current contract or handoff only with the actually observed result.
