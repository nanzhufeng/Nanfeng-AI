# Sol/Astra联网一致性检查：中断时状态（2026-10-13 20:42）

本轮没有完成“所有模型全部打通”，正式OPPO/Mac也未覆盖安装。具体客户端修复已落源码，真实失败和验收入口缺口继续保留。

## 已确认与修改

Sol与Astra共用RC Responses接口/解析器，但原来只有Astra显式设置low/medium推理与搜索上下文。现双端Sol复用Astra的既有规则；没有改OpenRouter/官方正常自身联网，也未将Claude/Gemini改成GPT协议或更换原回答模型。

Android存在明确的字段丢失：RC wrapper在非completed状态的`response.completed`分支提前返回，丢掉已返回的Token/模型身份/来源。有效Robolectric红灯分别捕获缺少reasoning参数和input100变null；修复后定向通过，仍保持INCOMPLETE及严格来源/终态门。Rust策略回归红绿及全量425PASS/33ignored。

本次全量Android1488测试中1个文档门失败、9skip；该失败来自前轮费用交接把“当前合同读取门”挤出首3行。现已恢复顶部权威指针，未复跑的全量不能写PASS。最初定向测试缺Robolectric环境产生的mock错误属于夹具设置，已经改正并重新观察有效行为红灯；不把环境失败当业务红灯。

## 真实验证及未解决事项

- Mac旧Sol及Astra同公开问题均成功，说明截图失败未被确定为固定解析根因。新Sol/Astra用截图原文均有完整回答、来源、原模型身份及SQLite重开一致证据。
- Android最初验签工具未设置JBR，安装未发生却继续启动了旧包控制；该001记录只算旧包Go/Firefox问题证据，不能算修复验收。之后补齐JBR，确认同证书并更新隔离emulator-5592验收包。
- 新包002同截图问题：Sol服务返回FAILED，Astra成功；003只增加无正文终态元数据观测，Sol成功141088ms、24来源、输入25868/输出1886。002失败保留，003不能证明上游永不失败，也不能假定参数差异就是截图单次失败的完整根因。
- Android004当前20预设真实AppContainer/Room普通文字：17发送完成，DeepSeek Flash未完成但已收到1525字、42来源和input58101/output3237，需进一步核对Messages终态；Muse403；Sol另一Go/Firefox问题504约60.7秒。当前RC Gemini Pro普通文字已成功，历史附件404仍是独立范围。
- Mac矩阵的四款RC非GPT普通文字得到WEB_SEARCH_UNAVAILABLE，没有发生Luna检索；原因是opt-in owner探针仅在plan非空时走附件执行owner，plan为空错误地直调transport。此项是验收入口缺口，不能当生产路由失败；必须接入生产同款execute_companion及记账/原回答/重开，再补真实证据。Muse实际403属于服务访问拒绝，不能换模型冒充成功。

## 中断与下一步

中断时Android20结果与24份物理运输记录已保存；已精确停止隔离包/controller，清除本次broker reverse、Bootstrap、隔离包临时加密凭据及Mac加密凭据副本。原凭据owner、正式库与主设备未改；没有connected测试、主设备Debug、commit/push。私有原文/回答/回执在`/tmp/nanfeng-native-search-consistency-20261013`，不放Git。

先复跑交接文档门，再修Mac纯文字companion验收入口并核查Android DeepSeek Flash失败的Messages终态。新真实动作使用新receipt且不重发UNKNOWN；修复后再补相关真实链路、完整自动回归和构建。不要恢复HEAD覆盖前轮费用合并等WIP。
