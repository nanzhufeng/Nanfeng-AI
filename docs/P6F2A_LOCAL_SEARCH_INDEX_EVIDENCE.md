# P6-F2-A Search Index Adapter 最终证据

状态：已完成（2026-08-14）。P6-F2-B~E、P6-G 仍未实施；不得因本证据自动进入下一 Adapter。

## 范围与安全

- 双端 owner-local Search Index、规范化去重历史（最多 10）、共享搜索结果面板、命中消息定位、会话行本地日期。
- 索引/历史均排除 TEMP、Key、path/URI、隐藏过程与 Provider payload；未读 Key、未发 HTTP、未图片外发、未操作 OPPO、未改图标。

## Desktop 真 UI / restart

- 最终 `.app`：搜索框聚焦显示“最近搜索”；`P6-D` fixture 提交后显示共享“搜索结果”主面板，打开结果定位至第 64 条真实 fixture 消息。
- 历史回填仅填入输入框、不执行；Escape 与外点均关闭浮层并将焦点返还搜索框；会话行可见“昨天”本地日期。
- 最新同一 bundle 的既有 P6-D 合成 fixture 已完成：提交 `p6-d` 后，点击“清空”立即显示“暂无已提交的本地搜索。”且“清空”禁用；随后输入安全单字符 `x` 实际进入搜索框，证明异步 owner 清空后的渲染与回焦可靠。该字符未提交，并已在退出前清空。
- 完整退出并以同一 bundle 重启后，再聚焦搜索框仍显示空历史与禁用“清空”，完成清空后的 UI/restart/readback。此过程没有写入消息、没有提交搜索，也没有读取 Key、发 HTTP 或外发内容。
- 最终 bundle `codesign --verify --deep --strict` 通过；ad-hoc（非 Developer ID/notarized/发布）可执行 SHA-256：`95a1a256277a77b1358f81c2f10976a83e566492b90a62fab2710632c0651214`。

## Android

- 既有可删除 P6-D fixture：搜索输入聚焦后显示历史浮层；提交进入共享“搜索结果”面板并可返回/定位；系统 Back 关闭浮层。此前真 UI dump 已显示 `最近搜索 / 清空 / 关闭 / fixture`，结果面板显示“仅本地安全索引”。
- 最终 Debug `install -r` 后 force-stop/cold-start；产物与 emulator `base.apk` SHA-256 均为 `7c9abc19557a89e62efed6d4912fa553c10f08940f18b9118e3ac44d819243fd`。未清数据、未装 test APK。
- 最终 Release 已单独 `install -r` 并回拉：产物与 `base.apk` 均为 `fd0e32caecd2de05516cc37ee3f527b8b660f6b5f614935a39bf39f40afaf93a`；v2/v3 verified，Signer SHA-256 为 `889ecf3ff4eeb40486e5122c6dc4eaaa04226ce6493d8a946c65275a803e99d5`。最终设备状态已恢复 Debug，二者未混淆。

## 自动门

- Android：264 JVM tests、`lintDebug`、Debug/Release assemble 通过（一次带 `TieredStopAtLevel=1` 的 lint CodeCache 工具失败已移除该 JVM 限制后重跑，非产品 lint 问题）。
- Desktop：Node 33 tests、lint、typecheck、build；Rust fmt、clippy `-D warnings`、38 tests；最新 Tauri `.app` bundle。
- `python3 scripts/check_feedback_ledger.py`：`FEEDBACK_LEDGER_CHECK OK rows=22`。
