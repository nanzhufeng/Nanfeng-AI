# 对话底部文字选择与复制（2026-10-14）

用户要求对话底部信息像正文一样正常长按复制。Android时间／模型／金额纳入同一个SelectionContainer，保持原有同行与窄屏金额另起一行的布局，动作按钮仍在选择区外。Desktop页脚及子元素允许原生文字选择，metadata区域不再被消息contextmenu截获；正文消息菜单保持原行为。不新增按钮／说明，不修改费用、模型、数据或发送链。

- Android两项既有页脚定向测试通过，隔离APK构建并验签；emulator-5592仅保数据覆盖独立验收包，UID10210保持。真实聊天界面长按¥0.3202→系统Copy→粘贴到未发送输入框，内容完全一致，随后清空测试草稿。720×1280手机宽度下，单独金额行可以选中；恢复原1600×1800尺寸、关闭隔离包并删除设备XML。
- Desktop十项相关测试、ESM语法和静态build通过。Playwright使用实际renderChatFirstShell生成的metadata、当前CSS与当前contextmenu回调做本地浏览器验收：跨行拖选时间／模型／金额，原生contextmenu不被阻止，正文菜单仍执行；未测试原生Mac WebView。首个拖选坐标在两行之间造成空选区，调整到实际两行文字后通过，未改业务实现绕过。
- 本轮UI变化未新增源码字符串测试；复用框架的原生选择行为，用真实交互验证。未运行全Android／Rust／Node套件，前轮全量数字仍有其源码边界，不能把这次定向结果写成全套重跑。无付费Provider、主设备安装、connected测试、清库、卸载或提交／推送。
- 私有改前副本／HTML／截图／XML／日志在/tmp/nanfeng-footer-copy-20261014；只发布[安全结果和源码hash](receipts.json)。浏览器验收会话与本地HTTP进程已关闭。
