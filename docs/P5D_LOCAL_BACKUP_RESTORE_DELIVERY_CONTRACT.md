# 南枫 AI P5-D 本地备份、恢复与交付合同

日期：2026-08-13  
状态：P5 的第四个独立增量；不是 P5 或项目终点。备份 owner 从实际打开的 Room 数据库读取当前 Schema，不另存硬编码副本；本合同不得以历史 Schema 号限制后续已验证迁移。

## 目标与唯一所有者

`AndroidLocalBackupRestoreManager` 是用户手工本地备份、SAF 导出/导入、严格 preflight、恢复 checkpoint、替换与失败状态的唯一所有者。UI 只呈现该结构化状态，不直接读取数据库、文件或 URI。它不接入云同步、Google Auto Backup、Provider、Prompt、RunSpec、网络或 OPPO。

## 包格式与保密边界

导出的 `.nfai-backup` 是 ZIP，根目录固定为 `manifest.json`、`database/nanfeng-ai.snapshot` 和受控 `assets/<logical-key>`。数据库文件由 SQLite `VACUUM INTO` 从已打开的数据库产生一致性快照，绝不把运行中的 `-wal/-shm` 直接复制并宣称一致。Manifest v1 记录 app/version、Room schema、范围、各表计数、资产大小、每一 entry 的 SHA-256/大小和总 manifest hash。

只允许备份当前 CHAT／WORK 区域的业务 Room 数据及 `attachments/v1`、Markdown/JSON/PDF/Web、ChatGPT/Claude/南枫知识导入包与 P6K ZIP 私有来源资产；不可递归打包另一区域目录。导出、诊断、registry、P2-M 证据/令牌、缓存、恢复工作目录、路由偏好、Provider API Key/token/credential bytes/credential reference、签名与任何 Android Keystore 身份一律不进入包。业务正文、草稿和用户命名的附件可能包含“API Key”等普通文字，属于用户明确选择的本机业务数据，不能因此误拒绝整包；路径逃逸、symbolic link、未知受控根、格式/hash/Schema 不一致仍整体拒绝。

## 生成与 preflight

备份先写 app-private staging，逐项 fsync、SHA-256、封装 ZIP，再从同一 ZIP 回读 Manifest、entry hash、重复 entry 与总大小；成功后才通过用户 SAF `CreateDocument` 写入，并从该 URI 回读全包 SHA-256。完整本机附件库的压缩包上限为 32 GiB，仍逐条校验路径、数量、SHA-256 与 SQLite 快照；校验失败只显示结构化原因，不显示业务内容。导入先由 SAF `OpenDocument` 立刻隔离复制到 app-private inbox，随后在不改库的条件下预检：版本、Schema、表计数、资产大小、缺失/冲突/不支持项、总大小/entry 上限、zip-slip/symlink/重复 entry/压缩炸弹/hash/manifest。

## 恢复语义

空本地库可恢复；非空库只能明确选择“替换本地”或取消，P5-D 不做合并。替换前在 app-private recovery checkpoint 生成当前一致性 DB 快照和资产副本；候选包解压进 staging、完整性复验、SQLite 完整性检查和 schema 核验通过后，才在关闭 Room 的明确重启边界下切换。第一项文件替换前即进入可回滚状态；数据库或任一资产替换失败都恢复 checkpoint，原本没有资产时也删除本次部分导入的资产，另一区域保持不变；进程中断保持 `INTERRUPTED`，不自动继续，用户只可重试或取消。恢复后运行态统一按 P5-B 映射为 `FAILED(INTERRUPTED)`，不重放 Provider/网络/写入。恢复成功要求用户重启 App，避免任何 Repository/Room/container 保留旧库引用。

## Android Auto Backup 与交付

`allowBackup=false` 且 `dataExtractionRules/fullBackupContent` 均拒绝所有 app 数据；不得让 Room、私有资产、SharedPreferences 或 Keystore 静默进入 Google 云备份。P5-D 最终构建必须提升版本/code，Debug/Release 同正式证书，v2/v3 验证、SHA256SUMS、仅公开指纹的 SIGNING_CERTIFICATE 与本地交付清单齐全。不得上传 GitHub、商店或操作 OPPO。

## 验收边界

自动覆盖 manifest/hash roundtrip、manifest 与当前打开数据库 Schema 一致、空库恢复、非空取消/替换、rollback/interrupt、资产引用、运行态降级、恶意 ZIP/高敏拒绝、Auto Backup 规则和从初始版本到当前 Schema 的完整连续迁移链。预检同时要求 manifest 与候选 SQLite 都精确匹配当前 Schema。真实验收只在 `emulator-5554`：先成功导出并回读，再创建专用非敏感变更，强确认恢复并核对回到备份状态；force-stop 后不自动恢复。迁移使用同正式证书旧 APK `install -r` 升级且不卸载、不清数据。P5-D 不能证明 P5 整体退出、OPPO、发布、云同步或真实 Provider。

2026-08-20 已按该边界以独立 `com.nanzhufeng.ai.p5dacceptance` 包完成 SAF 导出/回读、受控替换与 force-stop/cold-start readback；该验收包的本地与 installed `base.apk` SHA-256 均为 `e07de2cc1aa4438592bf0aa83d467b3b92c3faaf1941b3d4faf537bb3aad2965`。未安装、清除、重置或读取 OPPO/legacy app-data，未触及 Keychain 或 HTTP。旧 APK 迁移仍是独立门槛。
