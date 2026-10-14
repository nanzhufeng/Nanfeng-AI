# Android／Mac正式保数据覆盖（2026-10-14）

用户明确授权双端覆盖并保留现有数据。本次已将当前工作树正式1.0.29覆盖OPPO PKH120与Mac；包含联网／失败计量／Luna检索费用合并和页脚原位复制修复。完成时间：Android 2026-10-14 08:32:03 +0800，Mac 2026-10-14 08:29:06 +0800。

| 平台 | 正式覆盖及读回 | 数据保留证据 |
|---|---|---|
| Android | 原证书、com.nanzhufeng.ai／1.0.29／93、非Debug；推送临时APK后pm install -r --user 0返回Success；实装APK SHA-256与冻结候选完全一致；临时APK已删除 | UID10611、首次安装时间2026-08-20 15:15:31、dataDir与CE1459104／DE1433378 inode不变 |
| Mac | 原Apple Development叶证书及bundle ID；staged候选严格验签后精确替换；实装4个bundle文件hash与候选一致；DMG校验通过 | 全数据根1595文件／6069511187字节逐项SHA-256一致，增加／删除／变化均0；安装前软件未运行 |

旧版本也为1.0.29，本次按实际包字节及Android更新时刻确认更新，不只按版本号判断。Mac旧bundle保存在 `/Applications/.Nanfeng-AI-before-cover-20261014-6d01a77a3b2e.app`，此前备份未动。双端未卸载、未清库、未部署Debug／仪器测试，也没有connected测试。

## 冻结产物与构建

- APK：`/Users/nanzhufeng/Documents/工具开发/Nanfeng_AI/app/build/outputs/apk/release/南枫AI.apk`；SHA-256 `ed8757d1c32fbed1cd7466308b20c858618da9d307ab7251fd04061e78912ba0`；原证书 `6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- DMG：`/Users/nanzhufeng/Documents/工具开发/Nanfeng_AI/desktop/src-tauri/target/release/bundle/dmg/Nanfeng-AI-macOS-1.0.29.dmg`；SHA-256 `4b42c2f5a97a2e90c6b521d65030acff25746a4579d9dd93409f99fd13204b60`；Mac原叶证书 `e427918fedfc3842938551595bd6d3cd77666fc3a3a2ccdb67a43bc26684e7f3`。实际覆盖使用同一签名app目录。
- Android本次完整JUnit：1493PASS／10skip、0fail／0error，321套件；lintRelease／assembleRelease成功。初次Release打包因2GB Java堆内存不足失败，临时使用6GB堆与2个worker后成功，未改业务代码或项目配置，也未安装失败轮产物。
- Desktop本次Node：551PASS／12skip、0fail；lint／typecheck／正式Release app与DMG成功。Rust439PASS／34ignored复用前阶段结果，两个本阶段变更Rust owner的hash重新核对一致，本次未重跑完整Rust测试。
- HEAD `dafc64d9b33661dc4f792fd1434ff1ddca9cdfa3` 不是WIP快照；1144源码／配置／静态资产hash在构建、安装和收尾无漂移。此次只交付现有WIP与更新安装记录，未commit／push／发布Release。

## 验收边界

本轮只完成正式签名覆盖和以上数据核对，未启动正式软件、发送业务请求或调用收费模型。Android正式私有数据库未读取、未绕过权限，UID／首装／目录inode不能当私有库全文哈希证明。Mac数据文件逐项不变不能替代启动、迁移或业务验收。

Gemini／Muse双端普通文字证据见[前阶段修复](../2026-10-14-gemini-muse-risk-fix/REPORT.md)，页脚隔离原生Android及Desktop浏览器复制见[页脚报告](../2026-10-14-footer-text-copy/REPORT.md)。原长报告、Muse新可读多附件／媒体、RC Pro历史404、旧Mac原件UNKNOWN及正式UI／picker／生命周期等验收缺口继续保留；本次安装不将其改为通过。

[安全回执](receipts.json)、[冻结源码hash](protected-source-hashes.json)、[Mac候选bundle hash](mac-candidate-files.json)。完整安装／构建取证在仓库外 `/tmp/nanfeng-formal-overlay-20261014`；私有数据文件名和逐项hash仅留该私有目录，未进入Git或报告。
