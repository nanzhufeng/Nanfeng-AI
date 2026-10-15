# 自动标题双端真实验收

2026-10-15，WORKTREE；底层HEAD d64d71026418b9325931b6cbbb94544b28a81309。

Android应用进程（emulator-5592独立验收包）及Mac原生Rust标题owner，各验证3项：仅千问Flash真实生成；仅DeepSeek Flash真实生成；千问首选本地注入TIMEOUT、不发请求，随后DeepSeek真实生成。六项标题均合法、pending=false，写入独立数据库并关闭重开保留。每端3次真实生成，共6次；两次TIMEOUT是故障注入，不能当作服务商真实超时。主回答是公开合成开头交流，不是本轮真实生成；标题类／运输／次级任务／持久化使用当前生产owner。Mac抽出原有标题工作函数供生产与验收共用，策略未变。

账本估算分别为CNY 0.000866与USD 0.000170，应用固定汇率口径合计约¥0.002；均为LOCAL_ESTIMATE，供应商结算金额UNKNOWN，不冒充已扣款。实际模型／Token／错误及重开标题见[Mac](mac-title-results.json)、[Android](android-title-results.json)与[汇总](verification.json)。材料均为简短公开合成设置问题，无用户正文／附件外发。

只更新隔离模拟器验收包，未安装正式Android/Mac。Mac仅使用现有私有凭据owner读取已配置QWEN/DEEPSEEK，独立数据库；AndroidKey只经一次性本机内存通道进入进程，测试结束清零并停止隔离进程，无新增API Key文件。一回能力文件已删除、桥已退出、精确ADB reverse已移除。没有connected Android测试、OPPO操作、用户库写入、commit/push或发布。

边界：验证了标题真实生成与写回／重开，不等同于正式UI采图、主设备安装或进度动画的原生UI验收。普通联网不强制使用的新需求另行处理，不能由此报告关闭。
