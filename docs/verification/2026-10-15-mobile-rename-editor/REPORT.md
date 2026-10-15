# Android 改名编辑框恢复

- 用户要求：恢复手机改名时正常的输入框编辑，并保留纯标题变更不提升排序。
- 根因：`ConversationWorkspace.kt` 的 `InlineConversationTitle` 进入裸 `BasicTextField`，没有正常改名弹窗、输入边框和确认／取消控件。修改前实际 Compose 组件测试因找不到“重命名”弹窗及“确认”按钮失败。
- 修复：双击标题、菜单及滑出操作带继续走原改名入口，恢复 `AlertDialog`／`OutlinedTextField`；全选原标题、请求输入焦点与软键盘。确认／IME Done 保存非空标题，取消／系统返回／外部关闭不保存。按侧栏是否打开使侧栏与主界面菜单改名请求互斥，避免重复弹窗；保存继续调用既有 RENAME owner，不改排序、同步或数据库。
- 验证：Robolectric Native Graphics 下4项实际Compose交互测试通过（双击、焦点／全选、IME、确认、取消、空标题拦截及原单击／长按）；相关UI合同、Room标题排序回归合计97项通过，0失败／0跳过，Android Debug构建通过。旧“Android禁止弹窗”两项静态合同断言已按用户最新要求更新，未删测试；静态检查不冒充原生手势验收。
- 合同：Android恢复弹窗；Mac仍按当前原位编辑规则。双端纯标题不提升排序的规则继续生效。
- 边界：未安装、未运行connected测试、未动主设备或真实数据；没有Provider调用／服务费。真机视觉与实际软键盘展示NOT RUN，无commit／push。其他WIP及既有真实验收缺口保留。

结构化结果见 [verification.json](verification.json)，输入绑定见 [source-hashes.json](source-hashes.json)。
