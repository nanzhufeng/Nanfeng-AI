# v1.0.30 双端发布

- 构建源码点 `7c4245666bf9bf1d1bac1247a09c9793442053fa`；Android 1.0.30/code94、macOS 1.0.30，原 Android 与 Apple Development 签名，非 Debug。Mac 未公证，签名类型与既有正式包一致。
- 本轮只推进版本元数据；与刚完成完整回归的业务输入一致：Android1509通过／10 opt-in跳过、Rust449通过／36忽略。重新执行 Android Release 构建／Lint与 Mac 正式 bundle／验签／DMG检查，合同114／40、模型维护21+1／7及列表投影7项通过；1176输入冻结后0漂移，不重复收费模型矩阵。
- Android精确APK只在隔离emulator-5592保数据覆盖，已装APK拉回hash一致；不操作OPPO。Mac直接启动同身份冻结正式bundle采图，DMG只读挂载4文件与启动包一致，不重签为验收身份。
- README更新四张当前正式包独立预览，每端两张；仅裁去原生采集窗口顶栏并等比缩放，应用画面不改写。Mac侧栏已收起，设置采模型与联网页，无私人正文／账号／密钥；Android为隔离模拟器空对话及设置。截图只证明所示界面，不能替代联网／多附件／媒体／生命周期验收。
- Mac启动前已clone备份数据；190个数据库表逐项行hash不变，包括会话／消息和费用记录。5表仅正常启动元数据变化：1576附件身份保持、引用时间更新，历史资料候选／检查点各新增1条，调度和运行时记录更新。不能写成全部文件hash不变；未删改原有对话或恢复整库，采图进程已退出，未新增Provider调用。
- 上传只包含源码main／产品标签、APK、DMG与SHA256SUMS；原始／临时证据继续留本机。既有业务缺口、404／UNKNOWN／504记录保持。公共候选身份与hash见 [candidate.json](candidate.json)，远端完成后另附回执。

## 远端完成回执

- 正式 Release：[v1.0.30](https://github.com/nanzhufeng/Nanfeng-AI/releases/tag/v1.0.30)，非草稿／非预发布；产品标签准确指向 `0494e0f23069c89e9fec2c0eb359e992a30b6670`，与构建源码点仅有预览／文档差异，生产输入0漂移。
- 三个附件名称／大小／公开下载URL读回一致，APK／DMG／SHA256SUMS均从GitHub下载并逐项SHA-256一致；预览仅在README，未进入附件。
- [云端源码检查](https://github.com/nanzhufeng/Nanfeng-AI/actions/runs/38696770697) 三项全部通过。此后只同步文档回执，使用文档提交跳过重复云端编译，适用代码验证仍绑定上述准确源码点。
- 大文件上传遇到EOF，保留同一草稿与冻结文件，只补缺失附件；显式沿用系统已有本机代理并换用传输工具后完成。不修改系统代理／账户配置、不重打包、不重复覆盖主设备。安全结果见 [release-receipt.json](release-receipt.json)。
