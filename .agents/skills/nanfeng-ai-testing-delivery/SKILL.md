---
name: nanfeng-ai-testing-delivery
description: Validate Nanfeng AI Android, Desktop, protocol, and service changes with layered evidence. Use before checkpoints, delivery, regression claims, or native acceptance.
---

# 南枫 AI 测试交付

共同入口：[长期规则](../../../AGENTS.md)、[当前交接](../../../docs/CURRENT_HANDOFF.md)、[架构与证据索引](../../../docs/南枫AI完整开发档案.md)。当前用户授权及安全边界优先；本 Skill 不自动授权部署、安装或真实数据操作。

## 先确定验证层

1. 读取 AGENTS、受影响 owner／合同及当前交接，列明测试范围、环境、预期结果和不能证明的层。使用合成数据或明确授权输入，保护正式工作区。
2. Android 使用 Android Studio JBR；先定向 JVM，再相关构建；checkpoint／跨 owner 修改需要完整 JVM。读取 JUnit XML 的 tests／failures／errors／skipped，记录实际套件，不只看 Gradle 退出码。
3. `scripts/verify-test-xml.sh` 会把任何 skip 判为非零，适合要求真实输入全执行的门；默认全套 opt-in skip 应单列，不误报为业务失败或通过。定向测试会覆盖同一报告目录，先保存全套摘要／XML 或最终恢复完整报告。

## 按平台选择命令

4. Android：`./gradlew :app:testDebugUnitTest`，定向可用 `--tests`；相关 `:app:assembleDebug`，正式候选 `:app:lintRelease :app:assembleRelease`。不杀共享 Gradle、不清锁、不改验证 metadata 掩盖构建失败。
5. Desktop：`npm --prefix desktop test`、`run lint`、`run typecheck`、`run inventory:audit`、`run build`；Rust 用 `cargo test --manifest-path desktop/src-tauri/Cargo.toml`。主题变化补 `run theme:computed-style`，相关 native bundle 用 `run bundle:macos`。遵从 package.json 当前脚本，不使用不存在的 React／Vite 入口。
6. 协议按变更运行 `protocol/scripts/run-golden.mjs`、`run-v2-golden.mjs`、`run-sync-golden.mjs`。跨语言 owner 保真用 `scripts/verify-p6-v2-owner-fidelity-cross-platform.sh`；它只证明合成包兼容，不证明系统 picker 或真实用户迁移。
7. 网关在 upload-gateway 执行 `go test -count=1 ./...`；Supabase 使用 `node --test supabase/tests/p7c_static_contract.test.mjs supabase/functions/google-avatar/policy.test.mjs`。SQL 静态测试不替代真实 Postgres／RLS／RPC／Edge Function。
8. 格式／脚本检查按真实解释器和文件类型执行；历史 UI dump 可能夹带日志，先区分业务配置与历史证据。源码字符串测试、模拟 transport 和预览 fixture 必须明确分类。

## 原生与交付门

9. 永久不运行任何 connected Android 测试。主设备禁止 Debug／仪器测试、自动部署、卸载和清数据；同 ID／同签名的 Debug 包也不能覆盖。只有明确授权才对正式包做版本／非 Debug／证书／数据指纹预检、一次保数据覆盖及读回。
10. Desktop 原生验收确认隔离根／bundle ID，不使用正式库制造夹具；系统 Keychain 不作为普通只读状态检查的 secret 来源。新 bundle 的实际显示、SQLite 重开和系统回调分别验证。
11. 真实 Provider／Google／同步／通知与部署另需对应授权；使用最小材料，记录安全结果，不自动重试未知外部动作。LaunchAgent 若必要，按全局唯一 label 与精确 cleanup 规则执行并核验残留。

## 报告

12. 分别列源码／静态、定向／全量测试、Lint／构建、浏览器、隔离原生、主设备、真实服务。对失败分类为回归、旧基线、陈旧合同或环境；skip 和未运行不可省略。
13. 产物验证记录路径、版本、非 Debug、签名类型／hash及对应源码点；构建、签名、安装、冷启、视觉和实际业务各有独立结论。证据写交接，不写长期规则；不提交构建物、凭据或用户数据。
