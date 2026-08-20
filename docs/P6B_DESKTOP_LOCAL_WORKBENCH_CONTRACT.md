# P6-B：离线 Desktop 工作台、本地所有权与交换导入导出合同

## 状态与范围

P6-B 建立 Tauri 2 Desktop 的可运行离线基础；它不是 P6 或项目终点。唯一 Desktop 业务真值是 Rust 层拥有的 app-data SQLite，前端只通过受控 Tauri command 读取投影或请求用户发起的动作。禁止打开、复制或迁移 Android Room；`.nfai-backup` 不是 Desktop 输入。

本阶段只处理 `nfai.exchange.v1` 的严格导入、浏览、再导出及 Project / Conversation / Knowledge / Memory / Relation 的只读工作台。Provider、账号、同步、Hub、Agent、HTTP、Prompt、RunSpec、图片发送、成本和模型调用均不在范围内，egress 为 disabled。

## 本地所有权、迁移与恢复

- 数据库位于 app-private `workspace.sqlite3`；schema owner 为 `DesktopWorkspaceStore`，与 Android 表名无关。
- `PRAGMA user_version` 是唯一迁移版本。migration 1 建立 `workspaces`、`workspace_exchange`、`workspace_assets`、`import_journal` 和只保存非敏感布局的 `window_layout`；每个迁移在一个 `BEGIN IMMEDIATE` transaction 中完成并记录版本。
- 交换 IR 原样以 canonical JSON 保存，同时把 workspace id、包 hash、semantic hash、计数和资产 content hash 建索引；稳定 ID、revision、message tree 和 relation 均不重映射、不降级、不从显示名反推。
- 所有写入使用 SQLite transaction。导入在 app-private staging 预检、复制和 hash 校验；候选 workspace 只在最后 transaction 可见。中断或失败绝不留下半 workspace；未提交 staging 在下次启动清理，已提交的内容寻址资产可安全留作无引用 orphan 清理候选。
- 备份只生成 SQLite 一致快照（`VACUUM INTO` 或等价原子快照）和受控 assets allowlist；不把绝对路径、URI、Key、credential、Provider 内容或运行诊断写进领域数据或备份索引。

## 导入合同

1. 用户经系统 file picker 明确选择一个 `.nfai-exchange`，或通过同样受控的 typed `selectedPath` command 传入该单文件路径；无目录选择、无 glob、无 shell。
2. Rust 将文件复制到 app-private staging，限制包大小，严格预检 ZIP、Manifest、entry/hash/size、canonical semantic hash、schema 语义、内容寻址资产、重复 ID、tree/relation 及高敏标记。包内 Markdown/HTML/code 只作为文本 IR，永不执行。
3. UI 显示项目、会话和消息树、Knowledge、Memory、Relation、asset 字节、semantic hash、package hash、来源与敏感性。预检不是导入成功。
4. 用户只能导入到空 workspace；非空 workspace 明确拒绝。可选择创建新的空 workspace 再导入，绝不隐式 merge、覆盖或替换。
5. 导入成功后保存原始包与资产到 app-private allowlist，领域记录中仅存 stable ID、hash、相对 content-addressed key；路径/URI 不进入 IR 或数据库领域列。

## 再导出合同

用户明确选择一个 workspace 与允许范围；P6-B 只支持完整 workspace，未支持的子范围必须禁用并说明。Rust 从 SQLite 真值生成 canonical `nfai.exchange.v1`，逐项计算 hash、写入 app-private staging、同文件回读并再预检后才返回成功。导出文件可被 Android `NfaiExchangeV1Gateway` strict preflight 接受；未改动 imported workspace 的语义 roundtrip 必须精确保真。

## 工作台与无障碍合同

- Expanded：左侧 Workspace / Project / Conversation tree，中间 Conversation 或 Knowledge 主画布，右侧可折叠 Inspector；主画布获得剩余空间。
- Compact window：隐藏可恢复的 Inspector，将左树变成可聚焦 drawer/overlay；不是把手机页面塞进桌面。
- 所有页面覆盖无 workspace、预检、等待、导入中、失败、完成和只读内容状态。错误使用中文“发生了什么 / 影响 / 下一步”。
- 支持键盘焦点与快捷键：`Cmd/Ctrl+O` 打开导入，`Cmd/Ctrl+E` 导出当前 workspace，`Cmd/Ctrl+\\` 切换 Inspector，`Escape` 关闭非破坏性浮层。2.0x 字体/缩放与 compact 下不裁切主操作。
- App 自有 Dialog、Menu、Picker selection face 均为 `#FFFFFFFF`；灰色只用于外部 scrim。交互轮廓统一 hover、focus-visible、pressed 和阴影。

## 安全边界

Tauri capability 只允许窗口基础、dialog 与本合同的 typed command allowlist；不得加入 shell/process、http、updater、global filesystem、外部页面或 remote code。CSP 禁止外部连接；webview 仅加载本地打包资源。Rust command 不接受 SQL、目录、URL、任意目的地或未验证 JSON；错误不回显绝对路径或包内正文。

## P6-B 验收

- Rust：migration、transaction rollback、interrupted import、duplicate/cross-domain ID、恶意 package、asset hash、workspace non-empty refusal、export hash/回读和 cold reopen。
- TypeScript：tree / state projection、快捷键、Inspector 折叠、compact/expanded、2.0x 字体与错误/进度状态。
- 跨实现：golden 由 Rust preflight/import/export 产生的包可被 Node protocol runner 与 Android strict preflight 接受；未修改的 semantic IR 保真。
- 工程：`cargo fmt --check`、clippy、test、check，前端 typecheck/lint/test/build；可行时启动 Tauri/Desktop 并记录开发构建的大小、SHA-256 与启动证据。macOS 开发包如未签名/notarized 必须明确标为未签名开发产物；Windows installer/signing/WebView2 仍是独立债务。

## 后续

P6-C 才裁决完整工作台的 Project / Conversation / Knowledge 写入、模型与成本本地元数据；其任何写入必须另立 transaction、revision、soft-delete/undo 与跨端导出合同。
