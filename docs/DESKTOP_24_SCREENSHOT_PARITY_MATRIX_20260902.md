# Desktop 24 张问题截图 → Android 当前 owner 对齐矩阵

日期：2026-09-02
状态：**本地代码、浏览器渲染、静态构建与未启动的 macOS bundle 已闭环；正式数据、Provider、账号、通知与 OPPO 不在本增量范围**

## 裁决

- 18 张首轮问题图与 6 张补充间距图并不是 24 套独立布局；根因集中在两个共享 owner：Desktop 设置密度和 Assistant Markdown 阅读排版。
- 设置继续采用 Android 当前四组 IA、灰底亮白卡和整行选中语义；Desktop 保留常驻一级菜单、右侧详情和键鼠宽屏结构，不复制手机像素宽度。
- 本轮没有全局缩放，也没有放大底部 Composer。设置统一由 `--settings-desktop-page-max: 880px`、`--settings-desktop-row-height: 68px` 和局部卡片／字段规则控制；Markdown 统一由 `--chat-reading-width: 880px` 与表格局部滚动控制。
- 语气说明与 Android 当前运行时语义一致：风格只改变表达方式，不改变模型、联网、记忆或资料库功能。

## 24 项逐图归并

| # | 问题图可见对象 | Android 当前 owner／语义 | Desktop 修后 owner | 当前结果 | 验证／边界 |
| --- | --- | --- | --- | --- | --- |
| 1 | 外观／字体／主题值行过小且右值不齐 | 设置列表统一白卡、整行点击、三项值共用右对齐线 | `android-settings-row` 共享 68px 行高；`android-settings-row-value` 共用最小 108px 右值列 | 已闭环 | `1440×900` 是唯一产品视觉；较小窗口只作内部门禁 |
| 2 | 设置一级菜单过窄、信息挤压 | 四组 IA 与固定顺序 | `android-settings-layout` 320–420px 一级栏 | 已闭环 | 所有窗口保持同一双栏 IA；不定义紧凑／窄屏变体 |
| 3 | 记忆摘要页面过空、层级弱 | 全屏摘要、显式操作、正文分段 | 880px 页面、20px 页面间距、28px 内容分段 | 已闭环视觉层 | 本轮未生成或删除真实 Memory |
| 4 | 五风格弹窗窄、说明拥挤 | 恰好五项，`default` 仅内部回退 | 600×620 弹窗、每项至少 94px | 已闭环 | 浏览器实测 5 项、无横向溢出 |
| 5 | 昵称／职业／自定义指令字段像细条 | 标题在框上、明确说明、6000 字上限 | 62px 输入、180px textarea、20–24px 内边距 | 已闭环视觉层 | 消费者／SQLite owner 不变 |
| 6 | 模型与联网入口行太扁 | Provider 状态、网页搜索、四个三级入口 | 共享设置卡与 64–68px 行高 | 已闭环视觉层 | 未调用 Provider |
| 7 | Provider 分段选择过密 | 固定 Provider 次序与完整名称 | 52px 分段容器、44px 选项 | 已闭环视觉层 | 凭据和模型可用性不伪造 |
| 8 | 实时网页搜索行与说明过挤 | 开关与能力说明分层 | 64px 搜索卡、说明留在 canvas | 已闭环 | Web 预览仍明确不会发网络请求 |
| 9 | 模型设置详情卡缺乏呼吸感 | 预设、Key、测试和能力说明分层 | 68px 预设、62px Key、54px 测试卡 | 已闭环视觉层 | 未读取 Keychain、未测试真实服务 |
| 10 | 对话管理三行太小 | 收藏／已归档／回收站 | 共享 64px action row | 已闭环视觉层 | 未执行归档、恢复或删除 |
| 11 | 主题色选择器层级不稳 | 颜色名称、色点、单选状态 | 统一 picker 卡与选中反馈 | 已闭环视觉层 | 主题持久 owner 不变 |
| 12 | Google 账号页卡片信息拥挤 | 账号、同步、恢复与诊断事实分层 | 880px 详情宽度、统一 20px 间距 | 已闭环视觉层 | 未登录、未同步、未读取恢复码 |
| 13 | 导入与导出页面像移动端被拉伸 | 对话／ZIP／工作区／备份四区 | 32px 分组、64px action row、880px 页面 | 已闭环视觉层 | 未打开 picker、未写正式数据 |
| 14 | 关于页两张卡太薄 | 品牌事实与版本事实 | 更大卡片内边距和稳定页面宽度 | 已闭环视觉层 | 只显示本地产物事实 |
| 15 | Project 信息卡层级弱 | 项目事实、作用域、来源 | 共享卡片层级和详情宽度 | 已闭环视觉层 | 未新建／编辑真实 Project |
| 16 | 置顶会话标题／列表识别度不足 | pin 状态与标题仍由会话 owner 管理 | 本轮不改会话功能布局；随 15px 正文与现有图标合同复核 | 已复核，无新增常驻控件 | 不改会话数据与置顶状态 |
| 17 | Markdown 表格列窄、文字拥挤 | Assistant Markdown 结构化渲染 | 140px 最小单元格、12×14px padding、表格局部滚动 | 已闭环 | 4 列夹具无整页横向溢出 |
| 18 | Markdown 长列表／正文太细 | 标题、列表、段落和安全文本语义 | 15px／1.72、880px 阅读宽度 | 已闭环 | 只渲染 Assistant Markdown，User 仍为纯文本 |
| 19 | 补图：设置首页整体密度不足 | 四组 IA 同一视觉层级 | 一级栏 320–420px、68px rows、24px 组距 | 已闭环 | `01-personalization.png` |
| 20 | 补图：模型与联网大片空白、入口过小 | Provider 状态 + 搜索 + 四入口 | 880px 内容上限、20px 页面节奏 | 已闭环 | `02-model-network.png` |
| 21 | 补图：对话管理大片空白、卡片太薄 | 三个生命周期入口 | 64px action row、24px 横向 padding | 已闭环 | `03-conversation-management.png` |
| 22 | 补图：导入导出组间距和行高不足 | 四区顺序不能混用 | 32px 分组、14px 组内间距 | 已闭环 | `04-import-export.png` |
| 23 | 补图：项目与知识入口过小 | 工作区说明 + 两个管理入口 | 宽屏详情卡与 64px action row | 已闭环 | `05-projects-knowledge.png` |
| 24 | 补图：开发与诊断入口过小 | 本次 Context 控制／离线评测 | 宽屏详情卡与 64px action row | 已闭环 | `06-development-diagnostics.png` |

## 自动与渲染证据

- 新增 `desktop/tests/desktop-deep-visual-parity.test.mjs`，覆盖共享设置 token、六个重页面的 Android 当前文案与 Markdown 阅读／表格几何；既有 `desktop-parity.test.mjs` 同步锁定 600×620 五风格弹窗。
- Node 全量 `155/155`；lint、typecheck、protocol golden、static build、macOS bundle 和 strict codesign 通过。
- 浏览器计算样式：唯一产品视觉 `1440×900` 的设置行 `68px`、页面上限 `880px`、页面间距 `20px`；内部 1000／700 门禁同样保留双栏和 `68px` 行高且整页横向溢出 `0`，但不作为截图或第二套设计。语气弹窗 `600×620`、5 项、卡片最小高度 `94px`；Markdown 正文 `15px / 25.8px`，表格单元格最小 `140px`、表格 `overflow-x:auto`、整页横向溢出 `0`。
- 当前公共视觉证据只来自 `/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a060eb-322b-7dd2-b8da-1664f33e02de/nanfeng-ai-wide-only-20260902/` 的 `1440×900` 页面与宽屏联系表；旧目录中的 1000／700 图片仅为历史内部过程证据。
- 11 张浏览器独立 PNG、2 张隔离原生 PNG 与联系表：`/Users/nanzhufeng/.codex/visualizations/2026/09/02/01a06048-b547-73d2-8863-9fbf7aea5ef5/desktop-deep-parity-after/`。`contact-sheet-final.jpg` SHA-256 为 `c1a58ed5db106a394413d119caff26a45dfe096cc4ceafbb8f6110081be77cec`；联系表只是审阅板，不是 App 单一画面。
- 隔离原生 Bundle ID `com.nanzhufeng.ai.desktop.compareacceptance.63406.mtjle8e1`，数据根 `/tmp/nanfeng-ai-desktop-ordinary-chat-acceptance.mJpHnp`，显式 diagnostic mode；原生 `tauri://localhost` 的设置与五风格弹窗已独立截图，SQLite `quick_check=ok`、schema 37，PID 63732 无子进程、无已建立 TCP，验收后已精确停止。

## 未扩大授权的边界

- 当前正式 Desktop PID 58143 未停止、未重启，正式 SQLite 未读取／写入。
- 未调用 Provider，未读取 Provider 凭据，未登录或同步账号，未投递／点击通知。
- 未操作 OPPO，未运行任何 `connected*AndroidTest`。
- 新 bundle 只是 ad-hoc arm64 开发产物，`TeamIdentifier=not set`；没有 Developer ID 签名或公证。
