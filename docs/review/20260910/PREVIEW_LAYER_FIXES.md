# Desktop 全屏预览与层级修复

日期：2026-09-10。针对用户截图中的预览裁切、分割线穿透及全部表面层级检查；保留当前工作树其他增量，不改用户数据。

## 已确认问题与修复

| 问题 | 依据 | 当前修复 |
| --- | --- | --- |
| 普通预览 scrim 20，分割线 45；只有搜索模式另有 scrim 60 | `desktop/src/styles.css`、`chat-shell.css` 修改前差异，用户截图 | 全局 scrim 1000；分割线 25，不依赖当前页面模式 |
| 六类预览使用受最大尺寸限制的普通 dialog | `app.mjs` 六个预览渲染函数、`image-preview.css` | 共用全视口预览类；标题／关闭固定，内容占剩余空间，长文内部滚动 |
| 菜单 30 低于侧栏底部 40；滚动容器裁切不能靠子元素 z-index 修复 | CSS 清单及实际 elementFromPoint／底部菜单探针 | 上下文菜单 100；展开 Composer 100；生命周期／记忆菜单用原生 popover 保留 DOM owner 并限制窗口边界 |
| 弹窗视觉覆盖不保证背景快捷键和焦点隔离 | 顶层 Escape／Tab、嵌套设置选择器关闭行为检查 | `modal-layer-owner.mjs` 统一背景 inert、焦点循环、Escape 消费及背景菜单关闭 |
| PDF 容器完整但 WKWebView 内容空白 | 原生样本截图；原 `<iframe>` 与生产 `frame-src 'none'`／`object-src 'none'` 冲突 | 保留严格 CSP，Rust 校验私有附件后由 CoreGraphics 输出当前页 PNG，前端只展示像素 |
| CoreGraphics drawing transform 只缩小不放大，首版 raster 内容缩在中央 | 本机 SDK `CGPDFPage.h` 注释及实际 PNG | 显式按页尺寸缩放 CTM，再应用 CropBox／旋转变换；测试覆盖上下区域墨迹，不能以非空中央小图冒充完整页面 |

## 检查范围与结果

- 扫描 `desktop/src/*.css` 所有 z-index 声明，按局部控件、侧栏、搜索页、菜单、Composer、设置选择器、全局模态层检查。当前清单和源码 SHA-256 固化在 [机器记录](preview-layer-verification.json)。这是完整声明清单加重点交互验证，不是所有业务状态的逐一原生验收。
- Chromium 39 项通过：1280×800／640×480 两窗口，图片／PDF／视频／音频／文本／不支持格式边界六类的就绪、加载、错误；检查全视口、内容边界、关闭可见、无整体溢出、分割线命中、背景 inert、Tab／Escape；另验上下文菜单、嵌套选择器和底部菜单。
- 独立非持久 WKWebView 7 项通过：六类就绪表面及底部菜单 top layer。PDF 用真实合成 PDF 经实际 Rust renderer 输出 PNG，加载成功且人工检查顶部／底部内容；视频和音频是本机生成的 2 秒样本，metadata 读回 2 秒，无自动播放。
- Rust 221／221：新增 3 项覆盖非空页面／上下内容／2048 边界、损坏文件与不存在页码、真实私有附件 owner 的 PNG 输出／页码恢复／跨工作区与篡改拒绝。
- Node 255／255，lint、typecheck、静态 build 通过。macOS bundle 构建及 `codesign --verify --deep --strict` 通过；构建产物在 `desktop/src-tauri/target/release/bundle/macos/南枫 AI Desktop.app`。
- 工作流审计无硬错误；既有全局 AGENTS 软长度和 GSAP UI metadata 提示保持，本次不扩张修复范围。

## 可重复验证入口

- `npm --prefix desktop test`、`npm --prefix desktop run lint`、`npm --prefix desktop run typecheck`、`npm --prefix desktop run build`。
- `cargo test --manifest-path desktop/src-tauri/Cargo.toml --lib`。
- `desktop/scripts/check-preview-layer-layout.mjs` 使用 Playwright；可用 `PLAYWRIGHT_MODULE_PATH` 指向已安装模块。`PREVIEW_MEDIA_DIR` 接收合成 `page.png`、`video.mp4`、`audio.wav`，`PREVIEW_FIXTURE_DIR` 输出隔离 HTML。不提供媒体目录时只证明布局，不证明真实媒体解码。
- PDF 样本位于 `desktop/src-tauri/tests/fixtures/preview-page.pdf`；设置 `NANFENG_PDF_TEST_PNG` 后运行 `desktop_pdf_page_render` Rust 测试，可将测试页面输出到指定临时位置。音视频样本用 ffmpeg 的 color／sine lavfi 生成，H264 MP4／PCM WAV，2 秒。
- `swiftc desktop/scripts/check-preview-webkit.swift -framework Cocoa -framework WebKit -o <临时 runner>`；运行 `<runner> <上述 HTML 目录> <PDF 截图输出路径>`。独立窗口退出自动关闭，数据存储 nonPersistent，无用户目录／凭据读取。

## 文档冲突与剩余验证边界

- 旧 P6-F2 文档的“已完成 PDF 阅读”不能证明当前生产 CSP 下可见；保留历史记录，当前行为由新合同节覆盖。旧音频原位限定与已存在的 Desktop 音频预览实现也已标记历史范围。
- 本轮全屏指应用内容视口，不强制进入 macOS 独立全屏空间。未知文件显示真实不支持原因；UTF-8 128 KiB 读取上限保留。
- PDF 当前为本机栅格页，未提供文本选择；非 macOS renderer 明确返回未实现。未覆盖全部 PDF 变体、加密文档、字体和视频／音频编解码组合。
- 未替换已安装应用，未使用真实用户文件、凭据或服务；隔离 WKWebView 与 Rust owner 分层通过，不等于最新 Tauri bundle 的完整 IPC／重启／真实数据端到端验收。Android 本轮未修改、未运行主设备测试。
