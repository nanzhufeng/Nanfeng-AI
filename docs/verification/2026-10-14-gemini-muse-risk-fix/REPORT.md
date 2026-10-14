# Gemini / Muse 联网与双端遗漏风险修复（2026-10-14）

本轮优先处理两款OpenRouter自身联网，并检查请求、原生证明、错误终态、计量和重开。不是全项目逐行审查；Android/Mac正式包仍为10月13日18:54，未覆盖本轮源码。

- Muse：同短题Chat Completions流式／JSON均HTTP200却无原生来源。required及指定工具名均400，上游脱敏错误明确指出tool_choice不兼容。Responses同模型／原OpenRouter Key返回1个原生来源、正常终态、真实用量与USD费用，Native SQLite重开一致。双端采用该协议处理纯文字／完整本地可读文件；媒体保留既有路径，不扩大接收方或替换模型。Android实际应用进程也返回1个来源、页脚¥0.320162。
- Gemini：旧默认auto只提供工具，不要求模型搜索；新增请求级原生搜索指令，保持自身联网。通用required实际返回finish_reason=error，没有正常回答，所以撤掉控制参数。修复后Android和Mac同短题各2个原生来源、原模型、已知费用与持久化重开；Android¥0.199035，MacUS$0.015158。不能据此保证每次调用必搜，缺实际证明仍准确失败／未核实，不换Luna或自动重发。
- 遗漏风险：双端Chat finish_reason=error不能被[DONE]当成功。Mac完整但空正文保留原用量／费用；Android空Responses保留已返回费用并失败。Mac重开费用恢复纳入上述已知失败与cost-only回执，仍FAILED、联网false，不产生网络请求。公开HTTP→SQLite cost-only100微USD无Token重开通过，旧丢失账单不能补为零。

## 验证

- Android1493PASS/10skip（1503 tests，321 suites），debug构建及隔离正式签名验收APK构建通过；Rust439PASS/34ignored。Node未跑，未改前端；未进行正式设备UI验收。
- 模拟器emulator-5592同证书覆盖隔离包，UID10210保持；正式Android/Mac无安装、无connected测试、卸载或清库。两条原生应用记录Room零网络重开保留正文／原模型／物理Call／金额。临时Bootstrap与加密凭据清理、reverse为空，应用已停止。
- 本轮两次测试准备错误保留：001插桩前置断言失败后误运行了原Gemini基线，已计入实际费用，不能称required控制；010验收cap错误拒绝Utf8Json包装，两个本地失败在actualTransport前，0新增外发，随后仅修正测试包装再运行011。没有把这些失败删掉或计成服务通过。
- 新增收费只用短公开题／1024输出，禁止重复原长报告或旧全矩阵。已知返回金额合计US$0.394326，按软件参考6.72约¥2.649871；四条400及一条修复前Gemini错误回执没有可核定金额，这不是精确总费。实际与本地合成100微单位测试分开。
- 114合同/40领域与25合同工具、20聊天＋1特殊模型／7凭据组门通过；维护目录补两项接口／搜索意图例外，不用生成矩阵代替真实验收。细项与本轮源码hash见[安全证据](receipts.json)。

## 仍待验证

1. 多附件／不同可读文件与媒体、原长报告、系统picker／主设备UI仍未在这次新接口下真实复验。Mac可读文件typed text保真与原生媒体不变已本地验证，不当作真实文件验收。
2. RC Gemini Pro404、旧Mac原报告UNKNOWN、Sonnet来源空位／文件标记问题与后台生命周期保持未闭环；不以此次普通文字成功抹去旧失败。
3. 后续先回放已有真实附件回执、明确受影响接口与预算，再最少付费正交验收；预计>¥3先说明必要性及上限。不得自动重发UNKNOWN、换渠道／Key／模型或回退旧智谱链。
