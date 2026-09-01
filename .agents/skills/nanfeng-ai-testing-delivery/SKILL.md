---
name: nanfeng-ai-testing-delivery
description: Validate and deliver Nanfeng AI changes with explicit separation of focused JVM tests, full regression, builds, visual checks, real-service checks, and safe Android release overlays. Use before checkpoints, APK delivery, device installation, or acceptance reporting.
---

# 南枫 AI 测试与交付

1. Start with affected focused contracts, then Kotlin compilation/build, then the full JVM suite for a checkpoint or cross-owner change. Record exact tests, failures, errors and skips; a focused pass never cancels a full-suite failure.
2. Classify every full-suite failure as stale contract, known unfinished baseline, new regression, or environment/fixture issue. Opt-in real-data tests require XML proof of `skipped=0`; a successful Gradle task alone is insufficient.
3. Use Android Studio JBR and the repository signing gate. Do not kill shared daemons, weaken signing, alter dependency metadata or clean unrelated build state without a scoped reason.
4. Report evidence separately: code/static, focused JVM, full JVM, lint/build, visual/manual, isolated device, OPPO, and real Provider/remote service. Mark every unrun layer explicitly.
4. Never run `connected*AndroidTest`. For OPPO, do not uninstall, clear data, deploy Debug/instrumentation packages, inject a database, or read private business data.
5. Before an authorized formal overlay, verify package name, version compatibility, non-Debug flag, v2/v3 certificate, current installed identity and data fingerprint. Use only the approved artifact and one data-preserving overlay path; read back the changed package path/APK identity afterward.
6. Treat source review, release build, APK signature, installation, cold start, visual flow, data semantics and Provider success as distinct observations. Never promote one into another.
7. Preserve generated report paths and APK metadata only as current-run evidence; do not commit build outputs or write transient hashes into long-lived rules.
8. For the Go gateway, run `go test ./...` before build/deploy; no tests or unavailable toolchain is a coverage gap, not a pass.
