# P7-E Google、Supabase 与真机外部门 Runbook

本 Runbook 仅在南烛枫给出目标项目与明确授权后执行。当前工作站未检测到 Supabase CLI/link/私有配置，因此不能以此文档或本地 fake 声称已部署、已登录或已同步。

1. 用正式 `com.nanzhufeng.ai` APK 的公开签名信息取得 Release SHA-1；只记录 SHA-1 指纹，不打印 keystore 路径、口令或私钥。确认 Debug/Release 同既定证书边界。
2. 在**南枫 AI 专属** Google Cloud 项目创建 Android OAuth client（package + Release SHA-1）与 Web client；Web client ID 才可进入私有注入。不要复制其他南枫项目的 client、secret、callback 或测试账号。
3. 在目标 Supabase 项目配置 Google Provider 的专属 Web client 与 secret/callback；OAuth testing 仅加入已授权测试用户。token、secret、恢复码、账户、URL 与业务正文不入仓库、日志或截图。
4. 先运行 `scripts/p7e-supabase-readiness.sh --readonly --check-auth`，只记录布尔结果。用户提供明确 target 且授权 mutation 后，才部署 P7-C migration 与 avatar function。
5. 部署前后以只读核验表存在、force RLS/default deny、direct grants、RPC signature/grants、function JWT policy、anon 拒绝。不要读取密文；经单独授权才使用 synthetic non-sensitive envelope 做 commit→readback revision/hash。
6. 真机分别验证：Google identity、恢复码确认、头像私有缓存、空设备恢复、双方非空方向、冲突停写、账号切换、默认退出保留本机。每一步用真实用户 UI、重启/readback 与 envelope hash 记录；模拟器/local fake 不得替代。
7. OPPO 仅在明确授权后用同签名正式 APK 覆盖安装；本 P7-E 当前不操作 OPPO。Desktop macOS 真实跨网络和 Windows Credential Manager/installer 同样是独立外门。
