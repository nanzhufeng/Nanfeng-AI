# 南枫 AI 附件中转网关

这是可自托管的临时附件网关，不持有 OpenRouter、Qwen 或 DeepSeek 的 API Key。App 在用户点击发送后将附件断点上传到网关；网关完成 SHA-256 校验后返回短时签名 HTTPS URL，App 再把该 URL 发送给当前选择的模型服务商。

它只适用于模型服务商明确支持 HTTPS URL 输入的附件类型。当前 OpenRouter 官方文档确认图片和 PDF 可使用 URL；视频 URL 支持随底层服务商变化，因此 Android 只有在网关配置对该类型明确开放时才允许走此路径。

## 部署前必须配置

以下环境变量没有默认值，避免端点、访问密钥或存储目录写入应用源码：

- `NANFENG_ATTACHMENT_GATEWAY_DATA_DIR`：仅该服务账户可读写的持久卷目录；不要使用临时容器文件系统。
- `NANFENG_ATTACHMENT_GATEWAY_TOKEN`：为这一个私有网关生成的高熵随机值。它必须以 Android Keystore 加密方式配置给 App，不能写入 Git、日志或截图。
- `NANFENG_ATTACHMENT_GATEWAY_PUBLIC_BASE_URL`：反向代理后的公开 HTTPS 根地址，例如 `https://attachments.example.com`。它必须能被获授权的模型服务商在短时签名有效期内访问。

可选项：`NANFENG_ATTACHMENT_GATEWAY_LISTEN`（默认 `:8080`）、`NANFENG_ATTACHMENT_GATEWAY_MAX_MIB`（默认 150，上限 1024）、`NANFENG_ATTACHMENT_GATEWAY_RETENTION_SECONDS`（默认 3600，范围 300–86400）、`NANFENG_ATTACHMENT_GATEWAY_REFERENCE_TTL_SECONDS`（默认 300，范围 60 至 retention）。生产环境使用 TLS 反向代理、受限持久卷、最小权限服务账户和独立访问日志留存策略。

## API 合同

全部写操作使用 `Authorization: Bearer <gateway-token>`；网关只保存该 token 的 SHA-256 指纹。服务端不记录请求正文、文件名、Provider Key 或模型回复。

1. `POST /v1/attachments` 创建或按 `upload_id` 幂等恢复会话；请求含 Attempt/附件稳定 ID、Provider/模型、MIME、类型、大小和 SHA-256。
2. `HEAD /v1/attachments/{id}` 返回 `Upload-Offset`。
3. `PATCH /v1/attachments/{id}` 携带 `Upload-Offset` 和该 offset 后的原始字节。偏移不一致返回 `409` 和真实 offset。
4. `POST /v1/attachments/{id}/complete` 校验最终长度和 SHA-256，返回短时签名 `url` 与 `expires_at`。
5. `DELETE /v1/attachments/{id}` 可由 App 在用户取消/删除时立即清理；过期清理由服务端定期执行。

客户端必须将 `upload_id` 绑定到既有 `normalChatAttemptId + attachmentId`，重启时先 HEAD，再以原 Attempt、Provider、模型和 offset 继续；不得为同一聊天消息偷偷生成新 Provider 请求。

## 本地构建

```sh
cd upload-gateway
go test ./...
docker build -t nanfeng-ai-attachment-gateway .
```

部署本身需要你控制的 HTTPS 域名、持久卷和上述运行时机密；本仓库不包含也不会创建这些外部资源。
