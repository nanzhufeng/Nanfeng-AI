# 当前交接

> 当前合同读取门：[全项目合同索引](contracts/CONTRACT_INDEX.md) · [合同治理](CONTRACT_GOVERNANCE_CURRENT.md)。

> 最近增量为 2026-10-28 v1.0.32 正式包发布；此前真实服务、安装与费用记录保留原日期，不代表本轮重新验收。
>
> 全项目状态见 [PROJECT_STATUS.md](PROJECT_STATUS.md)；本页只保留最近阶段与继续工作所需边界。



## 2026-10-28：v1.0.32 双端正式发布完成

- [发布回执](verification/2026-10-28-github-v1.0.32/REPORT.md)：构建源码 `7fc45689117302c01fbb73d0e2eddcaf2014d25d` 已推 main；正式 APK／DMG 已冻结验签，Android 独立模拟器实装读回一致，Mac 精确 DMG 的四份 bundle 内容匹配、独立数据根原生启动及四张真实页面完成。正式 Release 仅两安装包已公开发布，标签准确指向构建源码；两个文件公开回下载 SHA256 匹配，源码三个 CI 检查全成功。README／回执为后续纯文档提交，不重建产物。
- 完整回归已过，原 Rust 云同步红灯只修测试 mock 分段读取；生产云同步不变。无新付费调用、主设备或 `/Applications` 覆盖。截图为空数据未配置 Key，故 Mac 显示“等待可用模型”；隔离窗口已关闭，原应用配置未改。
- 准确字节见回执 artifacts；只沿用最终 `/tmp/nfai-r1032-final/` 两个文件，不重建／重签、不重装主设备、不重发 UNKNOWN。当前发布核对指针 `/tmp/nfai-publish-path.txt`；公开材料已脱敏。

## 2026-10-28：当前Terra真实链路通过，修复包已覆盖

- [验收与安装回执](verification/2026-10-28-terra-live-chain/REPORT.md)：普通编号被宽泛“秘密值”指令误滤；双端读取owner收紧范围，已用同一短音频做真实红绿控制。Android两轮文本、原件PNG/PDF/WAV/MP4混合五值、附件联网2/2官方引用、8会话正文／费用重开与原生显示通过。没有换模型或自动重发；首个Argon IO UNKNOWN原样保留，具体传输根因未证实。
- 原签名正式1.0.31/code95已覆盖OPPO并拉回一致，SHA256 `d3abf0302c2b89688a35a5ba74d279ce8c7c9a9a8ec4f74f709f73adc24da34d`；appId／首次安装／dataDir／CE-DE inode保持，未卸载／清数据／启动主设备App／重试旧消息。不要重复装旧44514c或b91d候选。
- Android1548PASS／10skip、Release／Lint通过；Rust相关21、协议16和23模型目录通过。Desktop本轮真实调用未验，既有全Rust云同步失败未修改。预算上限已获用户批准¥6，保守占用约¥3.696184（含旧UNKNOWN预留¥3，非实际结算）；不再发请求。专用模拟器／AVD已删，临时凭据0、bootstrap消费、反向端口0；私有实际请求／回执／数据库保留于`/tmp/nfai-terra-live-path.txt`所指目录。无commit/push/Release。

## 2026-10-28早前失败记录（历史，当前结果见上）

- [真实验收记录](verification/2026-10-28-terra-live-chain/REPORT.md)：两个新文本请求均经Terra实发、同会话进程重启后上下文正确，正文／计量重开一致。四附件在Argon读取时NetworkFailure(IO,IOException)，原件字节匹配，无有效帧，原选Terra未发出；Attempt为UNKNOWN，不重发。
- 总预算原上限¥3；已知保守占用0.006672＋未知保留3元，非实际扣款。随后用户批准总上限¥6；新的图片/PDF、音频与混合／联网结果见上方当前节，不能把新成功回写为旧UNKNOWN成功。生产403输入未改；私有续作指针`/tmp/nfai-terra-live-path.txt`，旧失败及数据库保留。

## 2026-10-28：Terra正式包已保数据覆盖OPPO

- [安装回执](verification/2026-10-28-gpt-terra-cover/REPORT.md)：原签名、非Debug、1.0.31/code95；安装后拉回APK SHA256 `44514c059995537b3df154fda43f334d7fbb50a4d5587ebeda190327d3ce5487`。appId／首次安装／dataDir／CE-DE inode保持；临时APK已删除。未启动／清数据／重试旧消息／付费调用。不要重复覆盖旧候选；真实服务与Mac主入口仍未验。

## 2026-10-27：日常加入 GPT-6.1 Terra，紧接 Luna 上方

- 双端维护目录、实际选择器、Android 精确注册映射／冷启动种子、Rust 请求与费用估算已同步；23 个聊天部署。独立 ID 保留旧 Terra → Luna 迁移；Auto、默认、后台与历史归因不变。
- [接入与验证记录](verification/2026-10-27-gpt-terra/REPORT.md)：Android1547PASS／10skip，正式编译与Lint通过；JS563PASS／12skip，静态构建通过；Rust465PASS／1既存云同步FAIL／20ignored／21live过滤，Terra请求定向通过。正式候选1.0.31/code95 SHA256 `44514c059995537b3df154fda43f334d7fbb50a4d5587ebeda190327d3ce5487`，原签名、非Debug。10月27日未安装、发布或发送付费请求；10月28日同候选覆盖已完成，见上方回执，不重试旧消息。旧联网和全桌面附件缺口仍保留。

## 2026-10-22：22模型真实四附件／原选回答／重开通过，最终正式包已保数据覆盖主设备

- [报告与逐模型矩阵](verification/2026-10-21-real-attachment-roundtrip/REPORT.md)：Android22模型选定55完整物理调用，88原件字节／110标签金额／身份和COMPLETE消息、COMPLETED runtime与Attempt／22零网络重开一致；Desktop Mercury、Qwen Plus、RC Opus三条新样本真实完成且正文／计量重开一致。不能把历史UNKNOWN改成成功，也不关闭全部联网／来源或全部Desktop22缺口。
- 本轮实际修复：双端观察用途指令避免“材料不可信”被误当无内容；Android首全空白帧缓冲后有效正文一起落盘，Astra同症红绿＋新样本真实完成；Sonnet同Provider已发布响应别名精确映射，原请求ID／原始返回ID和渠道不改。无UI小字／新按钮；预算及Auto夹具错误与产品失败分列。
- 本地最终1543PASS／10skip，Rust468PASS／38ignored，合同114／模型22及正式编译／Lint、原签名验签通过。正式候选1.0.31/code95 SHA256 `b91d232afc0a14aa01a480fa0dbea3a5d09473e0ff91eae0694834279e391988`；已于2026-10-22T08:24:47.786797+08:00[同签名保数据覆盖OPPO并拉回校验一致](verification/2026-10-22-attachment-formal-cover/REPORT.md)。覆盖前实读e4a3ade4旧包；覆盖后为b91d232a最终包，appId／首次安装／dataDir／CE-DE inode保持，远端临时APK已清理。不卸载／清数据／启动应用／重试旧消息，不再重复安装旧候选。
- 私有候选、稳定交付文件指针、完整回执／输入／原生包／数据库／原始网络失败／预算在`/tmp/nfai-real-roundtrip-path.txt`；占用预算含未知保留约12.705元，非实际已扣。没有commit/push/Release或Mac主入口覆盖。


## 继续工作与尚未关闭的范围

1. 先核对当前 checkout、WIP 和 [项目状态](PROJECT_STATUS.md)，按 [合同索引](contracts/CONTRACT_INDEX.md) 定位本次 owner；不要为了接续重做全部合同整理或此前已完成的安装。
2. 最新记录的 Android 22 模型四附件、Desktop 3 条代表路径，只覆盖其报告范围。全部联网／来源相关性、Desktop 全模型及正式原生用户路径仍按对应报告保留缺口；不能从构建或安装推断已验收。
3. 历史 UNKNOWN、TLS／429 失败和未知费用保留，不自动重发原 Attempt，不以新成功样本覆盖原失败。新增付费或设备动作仍按当前任务与授权执行。
4. 最近源码已随 v1.0.32 构建提交推送；正式发布状态见本页最新节。Mac 主入口仍未覆盖。GitHub 产物、某次候选和实际现装包分别核对，不能只按版本号推断一致。
5. 最近记录中的 `/tmp` 指针只是历史材料入口，可能失效；失效不构成重新收费调用或重装的理由。先沿仓库内脱敏报告找证据与实际产物，缺少必要材料时明确报告缺口。

## 历史检索

完整改前原文保存在 [CURRENT_HANDOFF_ARCHIVE_2026-10-23.md](CURRENT_HANDOFF_ARCHIVE_2026-10-23.md)，包含所有历史阶段，未删正文、未改写旧结论。归档与本页位于同一目录，原有相对链接的解析位置保持一致。

需要旧修复、合同阶段或候选 hash 时，在归档内按日期和 1–2 个任务关键词定位相应段落；不要默认加载整个归档。旧记录只作历史证据，可能已被后续阶段部分替代；应按具体范围核对，不能整段机械当作当前状态。
