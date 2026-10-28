# Mercury 与遗漏修复双端正式覆盖

用户本轮明确授权“覆盖安装”。Android OPPO 和 Mac 已用最新复查候选完成同签名保数据覆盖，包含 Mercury 3 日常 Luna 下方入口、Android Argon 输入框简称及[四项遗漏修复](../2026-10-21-mercury-risk-review/REPORT.md)。未重编译或重签已冻结包，没有新Provider请求／费用。

| 平台 | 安装与读回 | 数据保留 |
|---|---|---|
| Android | 2026-10-21T10:15:03+08:00；1.0.31/code95，非Debug；安装前拉回现装APK与候选逐一验签，证书一致；仅临时推送后`pm install -r --user 0`，Success；安装后拉回APK与候选SHA256一致；远端临时APK已删除 | appId、首次安装时间、dataDir及CE／DE inode前后一致；未卸载／清数据。正式DB内容受系统权限保护，未绕过读取，不冒称整库hash不变 |
| Mac | 2026-10-21T10:21:27+08:00；1.0.30→1.0.31；只读挂载精确DMG，包名和原叶证书匹配；staged验签后替换，安装4份bundle文件逐项hash匹配；DMG已卸载 | 覆盖前后1595份私有数据文件逐项hash一致，新增／删除／改变0；原bundle保留。安装前后应用均未运行，没有通过启动触发迁移或业务 |

- Android APK SHA256：`71fa25020f36d70abbb6f3db1a4120026c2334855b1677cef1b059a8237419f9`；正式证书SHA256：`6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- Mac DMG SHA256：`e4d26640ebfbe8940bdf84d590eef7b430390992e9961c17da3424426489b9b3`；原Apple Development叶证书SHA256：`e427918fedfc3842938551595bd6d3cd77666fc3a3a2ccdb67a43bc26684e7f3`。
- 复用刚通过的Android1535 PASS／10skip与正式Lint，Node558／12skip、Rust460／36ignored及双端正式构建；1001份冻结输入安装后0漂移。没有重复收费、重试UNKNOWN或运行connected Android仪器测试，没有主设备UI dump／无障碍接管。
- 安装不等同业务实看／全模型联网验收；原生外屏、实际操作／重开以及历史TLS／429缺口保留。没有启动应用或付费生成，没有commit／push／GitHub发布。

安全回执见[receipts.json](receipts.json)。完整旧APK／旧Mac bundle、数据hash、安装日志和精确候选在本机私有交付目录，由 `/tmp/nfai-mercury-cover-path.txt` 定位。
