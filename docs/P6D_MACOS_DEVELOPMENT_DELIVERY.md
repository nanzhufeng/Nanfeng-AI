# P6-D macOS 开发交付清单

| 项目 | 已验证值 |
| --- | --- |
| 包 | `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app` |
| 包大小 | 13 MiB |
| 可执行文件 SHA-256 | `a5c4879fa60f77496c68f6fdb0e3c853119449afc887d1efea72f7cfc5a1a353` |
| 版本 | `0.6.0-p6d-dev` |
| 最低系统 | macOS 11.0（binary `LC_BUILD_VERSION minos 11.0`） |
| 签名 | `codesign --verify --deep --strict` 通过；`Signature=adhoc`、`TeamIdentifier=not set` |
| 发布状态 | 未 Developer ID、未 notarized、未发布；仅本机开发/验收包 |

## 数据和卸载边界

应用数据只在 app-private 容器中由 Rust SQLite owner 管理；应用内只说明该边界，不能显示绝对路径或数据库文件名。语义备份的最小基线是用户明确保存的 `.nfai-exchange`，不暴露 SQLite 文件，也不把 exchange 误称为同端 checkpoint。卸载应用不会主动删除用户本地数据；清除/迁移/同端 checkpoint 需要未来独立安全合同。

## 交付限制

此文件不是发布公告，也不包含 Windows 交付。Windows 需要按 `P6D_WINDOWS_NATIVE_DELIVERY_CONTRACT.md` 在 Windows 本机重新构建和验收；macOS Developer ID/notarization 也仍是外部门。
