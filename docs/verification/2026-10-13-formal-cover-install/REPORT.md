# Android／Mac正式保数据覆盖（2026-10-13 17:06 +0800）

用户明确授权“覆盖安装”后，最新工作树正式1.0.29已覆盖OPPO PKH120和 `/Applications/南枫 AI Desktop.app`，包含Luna路由、Muse入口及本轮风险修复。HEAD不是全部WIP快照；1041已验证源文件hash在构建和安装期间无漂移。

| 平台 | 构建／签名与覆盖 | 安装后读回与保数据 |
|---|---|---|
| Android | lintRelease/assembleRelease成功；com.nanzhufeng.ai／1.0.29／93非Debug、原证书；推送临时APK后pm install -r --user 0返回Success | 实装APK SHA-256等于候选；UID10611、首次安装时间、CE1459104/DE1433378 inode及dataDir不变，设备临时APK已删除 |
| Mac | Release app/DMG、原Apple Development叶证书/Identifier/Team、严格验签及DMG校验成功；staged候选先验签，原bundle备份后精确替换 | 安装后4个bundle文件hash等于候选；整个现有应用数据根1595文件／6069511187字节SHA-256完全一致，增加/删除/变化均0 |

现装版本原本也是1.0.29；本轮以精确包字节和更新时间证明更新，不只看相同版本号。没有卸载、清数据、Debug／仪器包部署或connected测试。Mac安装前未运行，旧bundle在[安装记录](mac-install.json)所列本轮独立备份路径保留；其他备份不动。

## 冻结产物

- APK SHA-256：`da8e6d2447e12df3faf5f5026fc969cb97bc6f995260d42226dc5111e1db6d78`；证书：`6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- DMG SHA-256：`9509d02571d79c62b9b400dd96f70d68db0771f081113514919f7019f7ec6612`；Mac原叶证书：`e427918fedfc3842938551595bd6d3cd77666fc3a3a2ccdb67a43bc26684e7f3`。实际覆盖使用同一签名app目录，DMG校验不替代安装验证。
- [产物与精确路径](artifacts.json)、[Android安装读回](android-after.json)、[Mac数据保留](mac-data-preservation.json)、[Mac包hash](mac-candidate-files.json)、[源码hash](protected-source-hashes.json)和双端构建日志同目录。

## 验收边界

本轮完成正式覆盖、包字节/签名及以上保数据读回，没有启动软件、触发业务迁移或真实Provider请求。Android非Debug私有库访问被拒绝，未绕过；只能证明UID/首装/数据目录保留，不宣称私有库全文hash或迁移业务通过。Mac数据文件内容逐项不变，不等于新包启动与业务验收。

之前自动回归Android1470PASS/9skip、Rust423PASS/33ignored、Desktop544PASS/12skip见[风险修复](../2026-10-13-dual-platform-risk-followup/REPORT.md)；公开真实链路18/20、Pro404、Muse403、普通文字7个非通过及原生picker/UI/媒体缺口见[真实证据](../2026-10-13-luna-routing-implementation/REPORT.md)，没有被安装成功替代。没有commit/push或发布Release。

Mac私有数据文件明细仅保存在仓库外临时证据；此处只存汇总，不复制用户正文、文件名或凭据。冻结后不重新打包或签名；若重建，必须重新安装、校验并更新证据。
