package com.nanzhufeng.ai.domain

import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * A local-only, bounded diagnostic record for a failed Provider attempt.
 *
 * This deliberately lives outside the normal invocation/audit ledger: it may retain a redacted
 * provider error message so a person can fix a bad model, key, region, or request shape without
 * putting credentials, prompts, replies, attachments, or raw response bytes into business data.
 */
data class ProviderDiagnosticRecord(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Instant,
    val providerId: ProviderId,
    val endpointHost: String,
    val apiModelId: String,
    val httpStatus: Int?,
    val errorClass: ProviderDiagnosticErrorClass,
    val redactedBody: String?,
    val requestShape: String,
    val latencyMs: Long?,
    val timeToFirstByteMs: Long? = null,
) {
    init {
        require(id.isNotBlank() && endpointHost.isNotBlank() && apiModelId.isNotBlank())
        require(httpStatus == null || httpStatus in 100..599)
        require(redactedBody == null || redactedBody.length <= 2_000)
        require(requestShape.length <= 512)
        require(latencyMs == null || latencyMs >= 0)
        require(timeToFirstByteMs == null || timeToFirstByteMs >= 0)
    }

    companion object {
        fun endpointHost(endpoint: String): String = runCatching { URI(endpoint).host.orEmpty() }
            .getOrDefault("").take(253).ifBlank { "unknown" }
    }
}

enum class ProviderDiagnosticErrorClass {
    AUTHENTICATION,
    BALANCE,
    RATE_LIMIT,
    MODEL_NOT_FOUND,
    STREAM_REQUIRED,
    INVALID_REQUEST,
    TIMEOUT,
    NETWORK,
    RESPONSE_TOO_LARGE,
    RESPONSE_FORMAT,
    SERVER,
    UNKNOWN,
}

interface ProviderDiagnosticStore {
    /** Inserts one already-redacted local fact and evicts records older than the retention window. */
    fun append(record: ProviderDiagnosticRecord)
    fun recent(limit: Int = 50): List<ProviderDiagnosticRecord>
}

/** Redaction occurs before persistence and before any UI projection. */
object ErrorBodyRedactor {
    private val patterns = listOf(
        Regex("(?i)(bearer\\s+)[A-Za-z0-9._-]{8,}"),
        Regex("(?i)(\\\"(?:api_?key|authorization|token|secret)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")"),
        Regex("(?i)sk-[A-Za-z0-9_-]{8,}"),
    )

    fun redact(raw: String, maxChars: Int = 2_000): String = patterns.fold(raw.take(maxChars)) { text, pattern ->
        pattern.replace(text) { match ->
            if (match.groupValues.size >= 3) match.groupValues[1] + "***REDACTED***" + match.groupValues[2]
            else "***REDACTED***"
        }
    }.trim()
}

fun classifyProviderFailure(status: Int?, redactedBody: String?): ProviderDiagnosticErrorClass {
    val text = redactedBody.orEmpty().lowercase()
    return when {
        status == 401 || status == 403 -> ProviderDiagnosticErrorClass.AUTHENTICATION
        status == 402 || text.contains("insufficient") || text.contains("balance") || text.contains("quota") -> ProviderDiagnosticErrorClass.BALANCE
        status == 429 || text.contains("rate limit") -> ProviderDiagnosticErrorClass.RATE_LIMIT
        text.contains("model not found") || text.contains("model_not_found") || text.contains("model does not exist") -> ProviderDiagnosticErrorClass.MODEL_NOT_FOUND
        text.contains("stream") && (text.contains("required") || text.contains("only support")) -> ProviderDiagnosticErrorClass.STREAM_REQUIRED
        status == 400 || status == 422 -> ProviderDiagnosticErrorClass.INVALID_REQUEST
        status != null && status >= 500 -> ProviderDiagnosticErrorClass.SERVER
        else -> ProviderDiagnosticErrorClass.UNKNOWN
    }
}
