# Mercury附件识读完成状态误判修复

## 证据与根因

用户20:19截图显示Mercury 3在13.3秒后“服务未完整结束回答”。主设备当前正式APK SHA256为`e1824c75a1c02c038030de05aaf0c81c549e0fcd71ec8989916c1221bba02af5`，包含该缺陷；版本1.0.31/code95不能区分同版本增量。主设备只读元数据及受限日志未提供该次持久Attempt，因此不将截图的具体物理调用阶段标成已读回。

已确认的代码阻断：OpenRouterChatAdapter使用默认IMMEDIATE流模式。真实SSE解码已经将`finish_reason=stop`解析成COMPLETED，但ProviderSseDecoder在该模式优先返回null，AttachmentSearchInventory的forAnswer严格校验随后拒绝这份完整正文。执行器因此在最终原选模型调用前停止，投影RESPONSE_INCOMPLETE。影响共用Android辅助识读的模型，不等于所有原生附件调用均失败。此前模拟StreamedResponse直接赋完成状态，绕过了故障解码边界；Desktop的Rust实现不经该Android解码器。

## 修复与回归

只调整解码终态判定顺序，保留显式COMPLETED；未弱化读取完整性要求，不把`[DONE]`、正文、用量或EOF单独当作识读完成。保留截断、过滤、流内错误、模型身份和费用校验；不增加重试或切换模型。上一增量删除输入框识读小字保持。

新增回归以公开合成订单文本走实际OpenRouter事件codec→ProviderSseDecoder→AttachmentSearchInventory：修改前1例失败（完整读取被返回Failed），修改后完成正文及尾部usage正确通过；只有DONE、缺少完成、length、content_filter、error仍失败并保留已返回usage，模型错配仍拒绝。五个相关测试类91例通过，0跳过。最终构建／全套结果与覆盖回执见checks.json。

本轮0新Provider请求，不重试旧失败；不将本地线缆回放或安装宣称为主设备原始业务实测。原始故障截图不外发；私有日志、旧APK及红绿回执由本机`/tmp/nfai-mercury-terminal-path.txt`定位，不进入Git。未commit/push/发布。

## 最终交付

- Android全套1542通过／10跳过，0失败／错误；Release Lint零错误，99 Warning／19 Hint。首次并行封包失败，单独assembleRelease重跑35秒成功；不掩盖首次失败。合同114／模型目录22离线门通过。
- OPPO已于2026-10-21T21:46:52+08:00完成原签名保数据覆盖；安装后APK SHA256 `e4a3ade4516137b7c1f3c54940af21bdb9785b24f2963a0be06f9eecc352cd3c`与验签候选一致。appId、首次安装时间、dataDir、CE／DE inode保持；临时包已清理。含本次解码修复与上一增量去字；未卸载／清数据／重试旧消息。
- 仍未确认：截图对应持久Attempt的具体阶段和新消息在主设备的真实服务结果。安装成功不关闭历史联网／来源相关性／其他Provider红灯。
