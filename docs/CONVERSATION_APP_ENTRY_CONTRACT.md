# 会话启动合同

<!-- contract-governance:start -->
> **合同口径：现行领域合同。** 领域入口：[ 会话启动与恢复 ](CONVERSATION_APP_ENTRY_CONTRACT.md)；[全项目口径与维护索引](contracts/CONTRACT_INDEX.md)。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。
> **适用范围：** 会话启动合同
<!-- contract-governance:end -->

适用 Android 与 macOS Desktop 的普通会话入口。快捷方式、分享与深链继续由各自明确目标处理。

- 首次空数据启动即使没有工作区和退出记录，也正常完成配置／模型元数据读取并显示新对话；缺失退出记录不能解引用为空的入口对象。
- 普通启动默认进入新对话；不增加准备本地数据、转圈、倒计时或品牌过渡页，不人为设置停留时间。读取本地数据仍需实际执行，不宣称零启动耗时。
- 退出记录完整、时间有效且间隔小于 15 分钟时，可恢复实际退出的会话；到达 15 分钟进入新对话。缺少 ID、记录不完整、时间异常、目标已删除或归档时不恢复。任何入口都不得以列表首项／置顶项替代缺失的选择。
- 返回前台需要新对话时，先同步清除旧会话正文及选择，再解析新对话。短时返回保留实际选择，不经过另一条历史会话。
- 异步工作区、正文或元数据结果只能更新发起它的当前导航；期间发生明确导航或启动选择变更时，旧结果不能覆盖新页面。所有挂起读取完成后、最终发布前须再次核对归属。
- 退出时存在生成任务则保留该次连续性；进程重建只回读持久状态，不自动补发。
- 正文和草稿由原存储负责。Desktop 仅按 CHAT／WORK 分开保存工作区 ID、会话 ID、退出时间与生成状态；不得将正文写入浏览器导航记录。Android 保留其已有 surface 恢复机制；本轮未改 Desktop 初始 CHAT 区域选择。
- 新对话草稿的一小时保留遵守 [新对话草稿保留合同](NEW_CONVERSATION_DRAFT_RETENTION_CONTRACT.md)。Android 实现细节见 [Android 会话合同](ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md#普通启动与短时恢复)。

双端时间、缺失记录及生成连续性边界共用 `protocol/fixtures/conversation-entry-policy-v1.tsv`；平台异步发布归属分别由实际 ViewModel 与 Desktop 导航 owner 回归验证。自动测试不替代 OPPO／macOS 原生首帧验收。

- 新建／恢复未发送新对话时的默认模型遵循[P6-G当前选择合同](P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md)：继承最后明确选择并固定，重开已有选择不重置Auto。
