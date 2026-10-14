# RC Gemini回答金额缺失（2026-10-13，双端检查／部分真实验收）

截图的¥0.077263为Luna检索费，原Gemini Pro回答缺输入／输出Token，故页脚为“已知检索费＋未知”，不是把回答费漏加成零。Android RC Chat Completions没有请求stream_options.include_usage；Mac有该字段。已有实际矩阵中17款返回完整回答的型号，只有RC Gemini Pro／Flash两款缺量化用量；Mac两款既有真实原生记录已有CNY LOCAL_ESTIMATE及SQLite重开。

## 修复与本地证明

- Android RC流式Completions请求显式请求usage，正常结束后继续读取尾部用量，和Mac既有请求一致。Gemini Pro／Flash与GPT普通Completions覆盖；Claude Messages／GPT Responses／正常OpenRouter及官方联网保持原协议，非流式不发送该字段。
- 公开请求→实际SSE解码红绿；两个Gemini冻结价、缓存／推理字段、检索与回答两笔费用及页脚，经Room关闭重开保留。合成例中Pro回答¥0.007952、Flash¥0.002772；这不是用户真实账单。Mac本地HTTP真实transport同量同价通过。
- 完整Android1499总数：1489PASS／10skip，321套件，debug及隔离APK构建；Rust433PASS／34ignored。未改前端，本轮Node未跑。旧无Token记录不能凭正文长度補造历史金额。

## 最小真实验收与费用

- 只更新同证书emulator-5592隔离包，UID保持，正式双端未安装。两款RC只回答1+1，无附件、无Luna或额外搜索。Flash实际完成，572输入／109输出，¥0.000235 LOCAL_ESTIMATE；重开0网络，正文／原模型／物理Call／金额保持。
- Pro新请求HTTP404／not found，原模型、渠道与Key保留，不换模型或反复重试。免费当前Key模型目录HTTP200仍列出Pro与Flash，但目录存在不证明请求成功。Pro本轮真实金额验收未通过，不能用Mac旧成功代替。
- 初次安装前检查脚本因UID字段解析失败，随后错误启动了旧隔离包；两笔均Blocked(WEB_SEARCH_COMPANION_NOT_CONFIGURED)、0物理调用，未外发。该脚本错误已修正为先独立确认验签／安装成功，再启动新run，原失败证据保留。
- Muse Mac按原Key、原OpenRouter模型做一次短公开联网题，1024输出上限、opt-in单步／USD0.1工具循环预算，HTTP200但无结构化来源。年龄403没有复现，联网仍未通过。捕获时探针将该Explicit错误投成UNKNOWN；已修正后续fixture明确失败状态，旧原始receipt不改写。
- 还发现Mac无来源失败链路丢掉已解码用量／金额，只留下pending账；此缺口已按下面说明完成本地修复，但本轮Muse旧回执的丢失事实不能事后重建，实际费用不能精确合计。已知¥0.000235只是RC Flash估算，不能称本轮总费用，也不能称Muse无费用。已停止追加收费调用，不盲目重试原报告或模型矩阵。

## Mac失败计量的零费用修复

- 公开transport红灯捕获：完整回答及usage已收到，却因没有来源仅返回错误码；接收方无法落账。现在正常结束的缺证失败携带已解码回复与量化事实，仍为WEB_SEARCH_NO_SOURCES／FAILED／web proof false；不把失败包装成成功，错误Debug不打印正文、推理或原始payload。
- 同样保留SSE、原生Messages／RC Responses完整JSON缺证失败的事实；普通回答、附件逐项检索及RC companion各自按物理Call记录，原始币种／RC不可信cost规则保持。未结束、取消、无量化字段仍保持原边界，不补造数值。
- 本地HTTP实际transport→普通聊天原生SQLite：缺来源、已返回100输入／20输出／USD100微单位的失败回执在关闭重开后保持正文、FAILED、WEB_SEARCH_NO_SOURCES、联网false及账本金额；这是合成费用，不是用户账单。重开不发送网络请求。包含JSON与SSE的全Rust433PASS／34ignored。
- 没有新增真实服务调用。修复不等于Muse联网已成功，也不能补出修复前已丢失的真实Muse账单。

## 未完成与下一步

1. Mac失败计量本地修复已通过；下一次服务控制必须同时保存安全用量／金额回执，先制定最少请求与费用范围，不能重复整份报告或不留量化证据再试。
2. Gemini OpenRouter旧无证明根因／required协议控制仍未闭环，Muse Mac联网仍失败；年龄确认无需重复。RC Pro 404保留待定位，不能偷偷改Key或模型。
3. 原报告、多附件／不同格式、来源空位和正式UI的新增服务验收仍待成本受控的最小设计。

[无正文汇总](receipts.json)。原始回执、原始模型正文与临时脚本仅在私有/tmp/nanfeng-rc-gemini-usage-20261013。临时宿主加密凭据副本已删、隔离prefs与bootstrap清理、reverse为空、隔离包停止；没有主设备Debug／connected／卸载／清库／commit/push。
