# v1.0.29 双端发布候选验证

- 发布版本 Android 1.0.29/code93、macOS 1.0.29；在此前完整回归基础上重新执行 Android Release Lint／签名构建和 macOS bundle，均通过。1189 个生产、配置、Schema、测试与维护输入保持相同；本轮不改业务代码。
- 冻结构建基准 8d434cbdaad834dd1df47ddfd5c8ad7131d48d13。公开 main 对新提交中的五处私有材料路径脱敏；原历史保留在本机 private-checkpoint-20261014 分支及 checkpoint 标签，不推送它们。后续公开发布提交含文档和预览，生产输入相同；不把包内构建 SHA 冒充公开发布 SHA。
- APK SHA-256：e9dce94a999a059e187541d00045f2cd0f063871c912233360fde016aec6a73a；原 Android 证书，非 Debug。
- DMG SHA-256：afd0a8460f7489463a5ca87b8ce7598ce0f2113f6b4a50f3523ee3e16d91daab；原 Apple Development 证书，strict codesign 和 DMG verify 通过，未公证。只读挂载 DMG 内 4 个应用文件与实际启动的冻结 app 完全相同。
- Android 仅 emulator-5592 保数据覆盖，实装 APK 读回 hash 相同，原证书／UID／首次安装时间／dataDir 保持。正常新对话与设置各一图；不执行仪器测试、不连接主设备。
- Mac 经用户明确授权，先 clone 备份 1595 个数据文件，直接启动冻结候选 app、不覆盖 Applications；进程路径核验、正常空对话与模型联网设置各一图，隐去私人会话侧栏。采图后正常退出。
- Mac 数据文件总数保持1595；三份 SQLite 文件在正常启动后变化。195 个业务库表对比发现正常后台本地资料候选／检查点各新增1条、附件资产与计划/运行状态更新、两个空搜索检查点表建立；会话／消息／费用账本等既有记录未变化。不是“所有文件 hash 不变”；完整快照和表比对留在本机。
- 四张预览联系表检查无正文、密钥或账号标识；每端两张绑定精确冻结包。外部本地 manifest 随候选保留，提交前执行 manifest 脚本与图片 hash 核对。
- 未主动发送问题、连接测试或新付费 Provider 请求；不重复模型比较或长报告。界面采集不关闭逐事实 Provider、Pro404、旧 UNKNOWN/504、媒体／系统 picker／生命周期或线上云端验收缺口。
- 上传范围只有 main、v1.0.29 产品标签、APK、DMG 与 SHA256SUMS.txt；323 个原始／临时证据及本机私有 checkpoint 保留本地。远端发布与资产下载校验由后续发布回执记录。

## 远端完成回执

- 正式 Release：[v1.0.29](https://github.com/nanzhufeng/Nanfeng-AI/releases/tag/v1.0.29)，非草稿／非 Pre-release；main 与产品标签源码点 a69447ded567beff03f57b351900470c7e0dcc46 读回一致。
- 三个资产名称／大小／下载 URL 核验，APK／DMG／SHA256SUMS.txt 全部从 GitHub 下载并逐项 SHA-256 一致；预览未进入 Release 资产。
- [云端源码检查](https://github.com/nanzhufeng/Nanfeng-AI/actions/runs/38532410221) 三项全通过；原生与真实业务缺口保留。
- 上传遇到 HTTP EOF／408／连接重置，保留同一草稿补传后再公开；未改代理系统设置、未重复打包。结构化结果见 [release-receipt.json](release-receipt.json)。
