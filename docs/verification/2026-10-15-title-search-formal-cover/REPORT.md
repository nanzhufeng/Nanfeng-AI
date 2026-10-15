# 当前标题／联网与改名修复双端正式覆盖

用户授权覆盖安装。Android OPPO 与 Mac 当前正式入口均已按原签名保数据覆盖，版本保持 1.0.29（Android code 93），以精确字节与安装时间识别本次更新。包含标题独立生成／失败兜底、当前联网开关／Haiku搜索修复、双端纯标题不提升排序和 Android 正常改名弹窗；对应业务证据沿用各自报告，不以安装替代。

| 平台 | 安装读回 | 数据保留 |
|---|---|---|
| Android | 2026-10-15T14:34:04+08:00；非Debug、同证书；临时推送后 `pm install -r --user 0` Success；拉回APK与冻结候选SHA-256一致；临时文件已清理 | appId、首次安装时间、dataDir、CE／DE inode均不变；未卸载／清数据。正式数据库受系统权限保护，未绕过权限读取，不冒充完整数据库内容hash |
| Mac | 2026-10-15T14:30:47+08:00；原Apple Development证书；staged验签替换，安装4份bundle文件与候选完全一致；旧bundle保留 | 1599数据文件逐项hash一致，新增／删除／变化均0；未改现有数据或启动应用 |

- Android APK SHA-256：`7527d000d9dd85390290ae1907557b9b4dfe32dcb86c654304842443b7ff3960`；证书SHA-256：`6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- Mac证书SHA-256：`e427918fedfc3842938551595bd6d3cd77666fc3a3a2ccdb67a43bc26684e7f3`；DMG SHA-256：`7788161da166eb8c75e0ca3ea27c7a952cb60477e38040373f9ce3a968a54b87`。
- 冻结1215份源码／配置／测试等输入，安装后0漂移；HEAD仍为d64d71026418b9325931b6cbbb94544b28a81309，本次为未提交WORKTREE；不等同GitHub旧v1.0.29资产。
- 本轮Android全套1509通过／10 opt-in跳过，324套件，0失败／错误，正式构建与Release Lint通过；Mac正式编译／原证书验签／DMG验证通过，Node lint与typecheck通过。Rust449通过／36忽略复用上轮相同冻结输入的结果，没有重复运行。
- 安装阶段0新Provider调用／0新增服务费。未做正式UI手势或新的真实生成／联网／多附件验收；已有真实服务证据与未完成缺口保留，不把覆盖成功写成业务全部验收。无commit／push／GitHub发布。

安全回执见 [receipts.json](receipts.json)。完整候选、旧包、源码与数据hash及构建日志仅存本机私有交付目录，由 `/tmp/nfai-current-cover-path.txt` 定位；仓库不存原始用户数据或凭据。
