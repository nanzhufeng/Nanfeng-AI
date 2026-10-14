# 多附件联网生成最终验收

核验时间：2026-10-13 02:27 +0800。main / dafc64d9b33661dc4f792fd1434ff1ddca9cdfa3＋既有WIP。正式双端未安装、未启动，主设备无操作，无提交或推送。

## 结果与范围

- Android与Mac现用模型各19个，集合精确来自维护manifest，无Fable历史extra。每个都有真实公开双附件4/4查询、原选模型实际返回身份和非空完整回答；跳过项不计为实测。
- 这是冻结生产owner＋真实影响范围补验的完整正向矩阵，并非一次无失败的连续19轮。Mac初始17完成／Gemini Pro失败／Qwen Flash未运行，Gemini兼容补修后两Gemini＋Qwen Flash通过；不删除或改写旧失败。Android2个GLM＋17其他预设完整通过，隔离broker准备失败与空筛选导致的缺凭据记录另保，不算模型API失败或成功。
- 14个声明文档格式fixture、四组多附件，双端真实私有导入→本机文本／Office／PDF／ZIP读取→规划→搜索→原选Sonnet回答均完成。旧fixture TSV与YAML别名错误已校正，未扩张支持声明；Mac扩展名归一化与Android MIME别名范围另由回归验证。
- 修复后的长中文双文件：双端11/11查询、全部尾部实体进入回答；人大／复旦均有相关原生来源。Mac实际数据owner关闭重开后回读11条规范化服务证据。查询执行完成不等于事实已核实。
- Android真实executor最终失败→UNKNOWN→关闭重开不发请求→显式重试，只发送原问题／原附件，复用2条完成搜索事实，后改草稿不进入旧重试。Mac真实SQLite owner重开、旧授权／材料变化拒绝、新v3原模型／文件保持与缓存账本关联验证通过。

## 已证实修复

- 语义边界拆分替代固定字数切块；中文新主体独立，明确承接句沿用最近主体，数字内逗号保全；每个语义子事项保留独立来源额度，不把尾部证据挤掉。全部物理调用先规划，48次／600秒门禁保持。
- HTML标签不作为查询事实正文；14格式实际读取保持完整材料，无法合法处理明确失败。
- 独立服务证据按准确材料、服务ID、engine、计划版本和原已完成调用事实绑定；不伪造modelId，不改写费用／原调用，无UNKNOWN自动重发。正常v3与旧v2读取保持边界，发送和显式重试准确披露原材料。
- Gemini Pro空正文根因对照：同Key／模型／端点／公开证据，max_tokens=8192与32768均返回合法SSE终态但无正文；省略该legacy字段返回完整原模型回答。仅RelayCore Gemini检索后grounded阶段与Android现行profile对齐，其他Provider、普通无附件路径和GLM MAX保持。原空响应未伪造为完成，EMPTY_COMPLETION与JSON格式错误区分。
- Kotlin内存失败用本次命令单worker／编译堆解决。未登记Guava parent经Maven Central字节与发布SHA-1核对后加入精确SHA-256，严格依赖校验保持。

## 最后验证

| 层 | 结论 |
|---|---|
| Android JVM | 1475总／1466通过／9跳过／0失败 |
| Android构建 | lintRelease＋assembleRelease通过；正式未安装 |
| Rust | 418通过／32显式忽略／0失败；最后新增owner回归后全套恢复 |
| Mac构建 | cargo release通过；最后追加仅cfg(test) owner回归不影响生产代码 |
| Desktop | Node543通过／12跳过／0失败；build通过 |
| 维护／合同门 | 19聊天＋1专用＋7凭据；114合同／40领域通过；16＋15反例回归通过 |
| 实际服务 | 19×双端、14格式×双端、中文11项×双端通过；公开限定资料 |

## 边界与清理

Mac原生picker/UI、正式Android UI、后台／休眠／强杀、扫描件／音视频／OCR真实提供商验收均NOT RUN；不声称任意模型原生读取所有媒体。不重放私人原件，不扩大OCR接收方。构建与实测范围分开。

仅emulator-5592／com.nanzhufeng.ai.publicmodelacceptance。最后凭据副本清除、capability消费、bootstrap不存在、reverse0、包精确停止。无connected Android测试、OPPO操作、正式安装／启动／发布。所有paid jobs与broker已结束。

## 证据

- 本目录verification-final.json、mac-current19-final-map.json、android-current19-final-map.json与source-delta-closeout.json持有结论和边界；source-hashes-closeout.json绑定实际工作树，HEAD不是源码快照。
- 完整公开API结果与历史红灯保留在`/tmp/nanfeng-search-final-20261013`；前序证据`/tmp/nanfeng-direct-search-20261012`不改写。scope各阶段具体路径见JSON映射、source delta及REPORT；临时诊断只记录安全形态和计数，无raw payload／CoT／Key。
- 唯一行为正文C12；维护manifest与生成矩阵已同步，无新增常驻按钮。只允许精确任务差异回滚，禁止reset／clean／整仓stash。
