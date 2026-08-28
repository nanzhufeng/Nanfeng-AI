---
name: nanfeng-ai-testing-delivery
description: Validate and deliver Nanfeng AI changes with explicit separation of focused JVM tests, full regression, builds, visual checks, real-service checks, and safe Android release overlays. Use before checkpoints, APK delivery, device installation, or acceptance reporting.
---

# 南枫 AI 测试与交付

1. Start with the affected focused contracts, then Kotlin compilation, then the full JVM suite when the change or checkpoint requires it. Record exact counts; a focused pass never cancels a full-suite failure.
2. Use Android Studio JBR and the project's signing gate for Gradle work. Do not kill shared Gradle daemons, weaken signing checks, or regenerate dependency metadata without a separately scoped reason.
3. Report evidence in five columns: code/static review, focused tests/build, full JVM, visual/manual, and real device/provider. Mark unrun columns as unrun.
4. Never run `connected*AndroidTest`. For OPPO, do not uninstall, clear data, deploy Debug/instrumentation packages, inject a database, or read private business data.
5. Before an authorized formal overlay, verify package name, higher version when applicable, non-Debug flag, v2/v3 signing certificate, and target identity. Use only the approved release artifact and a single data-preserving overlay path; read back package/APK identity afterward.
6. Treat a release build, signature check, install success, and cold start as distinct observations. Do not call an overlay a visual or Provider acceptance.
7. For the Go gateway, run `go test ./...` before build/deploy; absence of tests is a coverage gap, not a pass.
