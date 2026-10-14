# GitHub 仓库与发布策略

## 决策

南枫 AI 当前维护为一个公开源码仓库（`nanzhufeng/Nanfeng-AI`，2026-10-14 远端回读确认；早期私有仓库规划已被现状取代），Android、Desktop、共享协议、Supabase 迁移和附件网关一起提交。发布物按平台分别构建、签名、校验和上传；不混合为一个安装包，也不因 macOS 与 Windows 的发行差异复制 Desktop 源码。

`desktop/` 是未来 Windows 的共同代码主线，而不是 macOS 专有代码的备份。Windows 安装器必须在 Windows 原生构建环境或经验证的 Windows CI 中产生；macOS `.app` 不是 Windows 候选包。

## 发布边界

| 范围 | 唯一来源 | 发布形式 | 不可替代的验证 |
| --- | --- | --- | --- |
| Android | `app/` | 独立签名 `.apk` | 签名、同签名保数据覆盖、主设备与真实服务（获得授权时） |
| macOS Desktop | `desktop/` | 独立已签名 `.dmg` | macOS 签名、数据保留、原生交互与服务验收 |
| Windows Desktop | `desktop/` 的同一主线 | Windows 原生构建的 `.exe` | Windows 构建/签名、WebView2、数据路径与真实交互验收 |
| 云端 | `supabase/`、`upload-gateway/` | 独立部署流程 | 迁移、权限、真实服务回执 |

## 截图与发布证据硬门

历史 `docs/evidence/`、历史交接、`desktop/output/` 和任意本机截图都不是“最新结果”的候选来源。README 预览图只能作为仓库内独立文件维护，不能作为 GitHub Release 附件，也不能从 Release 下载地址引用。只有下列条件同时成立的截图，才可作为最新 README 预览候选：

1. 截图在候选包完成构建、签名与 SHA-256 固定之后采集；
2. 截图所在设备已安装或启动该**同一哈希**的候选包，不能以源码日期、文件名或“刚构建过”代替身份校验；
3. 每张截图随同一个与冻结候选包放在一起的本地 `release-evidence/<tag>/manifest.json` 记录平台、包名/Bundle ID、版本、候选包 SHA-256、Git commit、采集时间、设备/系统和截图 SHA-256；
4. 提交前由发布者逐项比对 manifest、源码提交和图片哈希；没有 manifest 的截图一律不作为最新 README 预览提交。

双端发布的 README 每个平台至少展示两张正常界面图，覆盖不同主要界面；预览不能用加载中、诊断模式、错误态或含用户私密正文的画面。

复制 [manifest 示例](../release-evidence/manifest.example.json) 到本次候选包所在目录，填入最终值后，在仓库根目录运行：

```sh
python3 scripts/verify_release_evidence_manifest.py --manifest /absolute/release-evidence/<tag>/manifest.json
```

校验会拒绝哈希不匹配、截图早于候选包、候选包与安装包哈希不同、不是当前最终 commit 或路径逃逸的清单。它不代替人工确认真实设备确实运行了该包。

旧截图若有追溯价值可继续留在历史证据目录，但必须保留其历史定位，不得移动、改名或重用为最新 README 预览。每次新 Release 都重新采集，不复用上次的“通过”截图。已核验且无隐私内容的 README 预览图可复制到 `docs/preview/`；候选包、回拉包和 manifest 本身不进入源码 Git。

## 版本与 GitHub Release

源码提交可使用一个产品级 tag；安装包以平台各自版本号和文件名命名。只有 Android 与 Desktop 都通过各自发布门，才可创建包含多资产的共同 GitHub Release。平台节奏不一致时，使用平台专属 tag 或草稿 Release，不把任一开发/验收包伪装为另一个平台的正式版本。

Release 附件最少包含每个安装包、对应 SHA-256 校验文件，以及平台、版本、签名身份类别和验证边界的简短说明。预览图不属于 Release 附件。

发布前不得上传私钥、keystore、Apple provisioning profile、服务端密钥、恢复材料、真实用户数据库、原始 Provider payload 或包含它们的诊断包。

## 源码推送门

按用户授权发布现有仓库当前可用版本；普通产品包不默认标为 Pre-release。Probe／诊断包不能替代产品包。当前 macOS 沿用用户原 Apple Development 身份，正式 Release 如实注明未公证；此手工交付不表示 Developer ID／公证或 Windows CI 已完成。公开推送前保留本机私有 checkpoint，剔除新提交中的凭据、私有正文与私有材料路径，不上传私有分支或 checkpoint 标签。
1. 把已有工作树变更分为“本次应提交”“其他进行中工作”“本地产物”，再确认首个基线提交的精确内容。
2. 运行 secret scan，并确认 `.env`、`local.properties`、签名材料、私有服务配置和临时 Supabase 状态未被跟踪。
3. 检查已跟踪的历史 APK、截图与验收材料。它们可能有追溯价值，但会增加仓库体积；未经明确确认不移除、不改写，也不上传为正式 Release。
4. 回读既有 remote、默认分支和仓库可见性，按用户授权推送已审核的范围；未经授权不改变仓库可见性或权限。
5. 远端回读默认分支、提交 SHA、仓库可见性和忽略规则生效情况。只有明确授权后才创建 Release 或上传附件。

## CI 与发布自动化

[源码验证工作流](../.github/workflows/source-verification.yml) 不读取私钥、不部署、不连接真实账号，也不产生可分发安装包。它覆盖可在托管环境安全运行的协议、服务静态合同、Go 网关、Desktop 静态/Rust 测试和 Android 编译检查。

正式发布自动化须在单独增量中建立，并先完成 Android 的受控 keystore 注入与签名、macOS Developer ID 签名/公证、Windows 原生安装器与签名，以及远端附件下载回读和 manifest 校验。CI 成功只证明源码检查成功，不证明任一正式包可安装、保留数据或可访问真实服务。

## 重新评估拆仓的条件

仅在 Android、Desktop、云端已拥有独立团队、独立发布节奏且共享协议已形成可版本化发布包时，才评估拆仓。届时必须先定义协议兼容矩阵、跨仓变更顺序、服务迁移治理和回滚策略；不能以复制当前源码作为拆仓起点。
