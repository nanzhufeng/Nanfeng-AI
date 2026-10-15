# OpenRouter Gemini 4 Argon 双端正式保数据覆盖

用户在暂缓RC Argon后明确要求仅配置OpenRouter，并授权覆盖安装。2026-10-15已对OPPO PKH120和Mac完成正式同签名覆盖，只包含此前OpenRouter Argon增量，RC新型号／凭据分组接入代码已撤回。默认模型和Auto不变，无schema变化。

| 平台 | 实际安装与读回 | 数据保留证据 |
|---|---|---|
| Android | 2026-10-15 12:34:33 +0800；com.nanzhufeng.ai／1.0.29／93，非Debug、原证书；临时推送后`pm install -r --user 0`返回Success，已装APK拉回hash与精确冻结候选一致；设备临时APK已清理 | UID、首次安装时间、dataDir及CE／DE inode均保持；未卸载／清数据／主设备Debug／connected测试 |
| Mac | 2026-10-15 12:33:02 +0800；com.nanzhufeng.ai.desktop／1.0.29，原Apple Development证书；staged验签替换、4份bundle文件与冻结候选一致 | 1599数据文件／6069584915字节逐项SHA-256一致，新增／删除／变化均0；原bundle保留 |

## 精确产物与源码

- APK SHA-256：`1b4b2c3b6b2366c4f729e02a31a2dda19bffd92402012fb47aa583c1a98c67eb`；证书SHA-256：`6d1d56ec5ae2d554f1085f2859d6bf19a9d3a8f0e5c0e96507cf4e198d8661f8`。
- Mac证书SHA-256：`e427918fedfc3842938551595bd6d3cd77666fc3a3a2ccdb67a43bc26684e7f3`；DMG SHA-256：`20059749abed7f40101402f95314334aa216e50db86ee0661a4ccc8577d0fb05`。
- 冻结产物及旧bundle均保留于本机私有交付目录。版本号保持1.0.29，以Android更新时刻与精确APK字节、Mac逐文件读回确认本次覆盖，不能只凭版本号判断。
- 冻结1153份生产源码／配置／静态输入，覆盖后逐项0漂移；OpenRouter接入前轮23份已验证输入hash仍全一致。源码基准HEAD仍是`d64d71026418b9325931b6cbbb94544b28a81309`，本次模型增量为未提交WORKTREE，现装不再等同GitHub v1.0.29发布资产。
- APK内profile核对精确`OPENROUTER / google/gemini-4-argon`，无已撤回RC反重力组；Mac实际安装二进制有同一OpenRouter精确ID且无该RC组。深度档Muse→Argon→智谱，原模型／原生联网与Auto计划保持。

## 验证层与边界

恢复后的Android1497通过／10 opt-in跳过、Rust444通过／34忽略、桌面定向26通过、维护16及114合同／40域、21聊天＋1独立／7组门通过；Android正式构建与Release Lint、Mac正式编译／原证书验签／DMG验证通过。前轮OpenRouter全套Node553通过／12skip适用于相同冻结输入。

本安装阶段0新增Provider请求，未启动正式应用做新模型UI／真实生成／联网或多附件／媒体验收；安装、源码与本地回归不替代这些业务验证。Android数据保留只覆盖可读安装及目录元数据，未绕过权限读取正式数据库，不将其冒充完整数据库指纹。Mac完整数据文件hash保持也不代替启动后的功能验收。

RC暂缓及3次404／504失败、费用UNKNOWN继续见[暂缓记录](../2026-10-15-rc-argon-deferred/REPORT.md)，未追加比较或自动重发。已创建独立网站Key保留但未接入软件，不改变现有三组RC凭据。无commit／push／GitHub发布。

[安全回执](receipts.json)；完整源码hash、before／after数据指纹、构建日志与正式冻结包仅在上述私有目录保存，不包含API Key。
