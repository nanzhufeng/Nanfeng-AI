# P6-D Windows 原生交付独立合同

## 前提

本合同不是 macOS 构建的延伸。不得从 macOS 生成、签名或声称验证任何 Windows 安装包；本轮也不产生 MSI、NSIS 或 Windows 伪包。

## Windows 本机必须完成的验证

1. 在真实 Windows 环境以 WebView2 Evergreen 和明确的离线缺失路径启动 Tauri 应用，记录可执行错误与恢复方式。
2. 选择 MSI 或 NSIS 后，在 Windows 本机构建、签名、安装、升级、卸载并验证签名链；未签名包不能称正式交付。
3. 用系统 Open/Save picker 完成非敏感 exchange 导入、拒绝和导出回读；记录用户数据根的安全摘要，不能把 SQLite 文件暴露给用户。
4. 验证 Rust-owned SQLite 的单实例锁、并发、崩溃恢复、schema 升级和旧版回退拒绝；不以复制数据库文件替代语义备份。
5. 在 100%、150%、200% 缩放、中文/英文 IME、Tab/Shift-Tab、快捷键与窄窗下检查三栏/抽屉、焦点、白色 Dialog 与无裁切。

## 不可替代证据与退出

每项必须带 Windows 本机的安装包 hash/size、签名验证、版本、截图或可复现操作记录。macOS P6-D 的 Rust、Node、Android strict-preflight 或 Tauri bundle 成功均不能代替。完成后另写 Windows 验收记录；在此之前它是独立外部门，不影响 macOS 本机 P6-D 的已验证范围。
