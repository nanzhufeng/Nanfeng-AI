package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.ResumableAttachmentGateway
import com.nanzhufeng.ai.domain.ResumableAttachmentGatewayConfiguration
import com.nanzhufeng.ai.domain.ResumableUploadCreateRequest
import com.nanzhufeng.ai.domain.ResumableUploadGatewayResult
import com.nanzhufeng.ai.domain.ResumableUploadRemoteReference
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

/** Keystore-backed implementations must return a copy and never log or persist the returned token. */
interface ResumableAttachmentGatewayCredentialStore { fun loadGatewayCredential(gatewayId: String): CharArray? }

/**
 * Concrete client for the repository's self-hosted `/v1/attachments` contract.  Construction is
 * inert; callers must first have a user-configured, enabled HTTPS gateway and click-send scope.
 */
class AndroidResumableAttachmentGateway(
    private val configuration: ResumableAttachmentGatewayConfiguration,
    private val credentials: ResumableAttachmentGatewayCredentialStore,
) : ResumableAttachmentGateway {
    override fun create(request: ResumableUploadCreateRequest): ResumableUploadGatewayResult = request("POST", "/v1/attachments") { connection ->
        connection.setRequestProperty("Idempotency-Key", request.upload.uploadId.value)
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val body = org.json.JSONObject().apply {
            put("upload_id", request.upload.uploadId.value)
            put("attempt_id", request.upload.normalChatAttemptId.value)
            put("attachment_id", request.upload.attachmentId.value)
            put("provider_id", request.upload.providerId.name)
            put("model_id", request.upload.modelId)
            put("mime_type", request.mimeType)
            put("content_type", request.contentType.name)
            put("sha256", request.upload.sha256)
            put("byte_count", request.upload.byteCount)
        }.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }
    }

    override fun offset(gatewaySessionId: String): ResumableUploadGatewayResult = request("HEAD", sessionPath(gatewaySessionId))

    override fun append(gatewaySessionId: String, offset: Long, bytes: InputStream, byteCount: Long): ResumableUploadGatewayResult = request("PATCH", sessionPath(gatewaySessionId)) { connection ->
        if (offset < 0 || byteCount < 0) return@request
        connection.setRequestProperty("Upload-Offset", offset.toString())
        connection.setRequestProperty("Content-Type", "application/offset+octet-stream")
        connection.setFixedLengthStreamingMode(byteCount)
        connection.outputStream.use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var written = 0L
            while (written < byteCount) {
                val expected = minOf(buffer.size.toLong(), byteCount - written).toInt()
                val read = bytes.read(buffer, 0, expected)
                if (read < 0) throw java.io.IOException("附件流长度变化")
                output.write(buffer, 0, read)
                written += read.toLong()
            }
            if (bytes.read() != -1) throw java.io.IOException("附件流长度变化")
        }
    }

    override fun complete(gatewaySessionId: String): ResumableUploadGatewayResult = request("POST", "${sessionPath(gatewaySessionId)}/complete")

    private fun request(method: String, path: String, write: (HttpsURLConnection) -> Unit = {}): ResumableUploadGatewayResult {
        if (!configuration.enabled) return ResumableUploadGatewayResult.Rejected("UPLOAD_GATEWAY_DISABLED")
        val token = credentials.loadGatewayCredential(configuration.gatewayId) ?: return ResumableUploadGatewayResult.Rejected("UPLOAD_GATEWAY_CREDENTIAL_MISSING")
        return try {
            val connection = (URL(configuration.endpoint.trimEnd('/') + path).openConnection() as? HttpsURLConnection)
                ?: return ResumableUploadGatewayResult.Rejected("UPLOAD_GATEWAY_ENDPOINT_INVALID")
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.doInput = true
            connection.doOutput = method == "POST" || method == "PATCH"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer ${token.concatToString()}")
            try {
                write(connection)
                val status = connection.responseCode
                when {
                    status == HttpURLConnection.HTTP_NO_CONTENT -> sessionResult(connection, gatewaySessionId = path.substringAfterLast('/'))
                    status in 200..299 -> decodeJson(connection.inputStream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty())
                    else -> ResumableUploadGatewayResult.Rejected(errorCode(status))
                }
            } finally { connection.disconnect() }
        } catch (_: java.net.SocketTimeoutException) {
            ResumableUploadGatewayResult.Rejected("UPLOAD_GATEWAY_TIMEOUT")
        } catch (_: java.io.IOException) {
            ResumableUploadGatewayResult.Rejected("UPLOAD_GATEWAY_NETWORK")
        } finally {
            token.fill('\u0000')
        }
    }

    private fun sessionResult(connection: HttpsURLConnection, gatewaySessionId: String): ResumableUploadGatewayResult {
        val offset = connection.getHeaderField("Upload-Offset")?.toLongOrNull()
            ?: return ResumableUploadGatewayResult.Rejected("UPLOAD_GATEWAY_PROTOCOL")
        return ResumableUploadGatewayResult.Session(gatewaySessionId, offset)
    }

    private fun decodeJson(body: String): ResumableUploadGatewayResult = runCatching {
        val value = org.json.JSONObject(body)
        val session = value.getString("session_id")
        if (value.has("url")) {
            val url = value.getString("url")
            if (!url.startsWith(configuration.endpoint.trimEnd('/'))) return ResumableUploadGatewayResult.Rejected("UPLOAD_GATEWAY_REFERENCE_INVALID")
            ResumableUploadGatewayResult.RemoteReference(ResumableUploadRemoteReference(session, url, Instant.parse(value.getString("expires_at"))))
        } else {
            ResumableUploadGatewayResult.Session(session, value.getLong("offset"))
        }
    }.getOrElse { ResumableUploadGatewayResult.Rejected("UPLOAD_GATEWAY_PROTOCOL") }

    private fun sessionPath(sessionId: String): String {
        require(sessionId.matches(Regex("[A-Za-z0-9._:-]{1,120}")))
        return "/v1/attachments/$sessionId"
    }

    private fun errorCode(status: Int) = when (status) {
        401, 403 -> "UPLOAD_GATEWAY_AUTHENTICATION"
        409 -> "UPLOAD_OFFSET_CONFLICT"
        413 -> "UPLOAD_SIZE_DENIED"
        in 500..599 -> "UPLOAD_GATEWAY_SERVICE"
        else -> "UPLOAD_GATEWAY_REJECTED"
    }

    private companion object { const val CONNECT_TIMEOUT_MS = 15_000; const val READ_TIMEOUT_MS = 30_000 }
}
