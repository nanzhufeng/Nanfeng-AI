package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ErrorBodyRedactor
import com.nanzhufeng.ai.domain.LoadModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.ModelResolver
import com.nanzhufeng.ai.domain.ResolvedModelResult
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass
import com.nanzhufeng.ai.domain.ProviderDiagnosticRecord
import com.nanzhufeng.ai.domain.ProviderDiagnosticStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.classifyProviderFailure
import java.time.Clock
import java.time.Duration

sealed interface ProviderConnectionProbeResult {
    data class Connected(val providerId: ProviderId, val endpointHost: String, val apiModelId: String, val latencyMs: Long) : ProviderConnectionProbeResult
    data class Failed(val providerId: ProviderId, val errorClass: ProviderDiagnosticErrorClass, val httpStatus: Int?) : ProviderConnectionProbeResult
    data class Blocked(val reason: String) : ProviderConnectionProbeResult
}

/** Explicit user-action probe. It never sends conversation text, attachments, or context. */
class ProviderConnectionProbe(
    private val configuration: LoadModelServiceConfigurationUseCase,
    private val modelResolver: ModelResolver,
    private val credentials: ProviderCredentialStore,
    private val transport: ProviderChatTransport,
    private val diagnostics: ProviderDiagnosticStore,
    private val clock: Clock,
) {
    fun execute(providerId: ProviderId): ProviderConnectionProbeResult {
        if (providerId == ProviderId.MOCK) return ProviderConnectionProbeResult.Blocked("本地 Mock 不需要连接测试。")
        val configuration = configuration.execute(providerId)
            ?.takeIf { it.settings.enabled }
            ?: return ProviderConnectionProbeResult.Blocked("请先启用该服务商并保存设置。")
        val modelId = (modelResolver.resolve(configuration.settings.presetId) as? ResolvedModelResult.Resolved)
            ?.model?.takeIf { it.providerId == providerId && it.capabilities.supportsText }?.modelId
            ?: return ProviderConnectionProbeResult.Blocked("该服务商的模型档案尚未确认或不支持文本。")
        val credential = credentials.loadCredential(providerId)
            ?: return ProviderConnectionProbeResult.Blocked("该服务商尚未保存 API Key。")
        val startedAt = clock.instant()
        val endpoint = "${configuration.provider.fixedEndpoint}/chat/completions"
        val request = ProviderChatRequest(endpoint, probeBody(providerId, modelId))
        val outcome = try {
            transport.execute(request, credential)
        } finally {
            credential.fill('\u0000')
        }
        val latencyMs = Duration.between(startedAt, clock.instant()).toMillis().coerceAtLeast(0)
        return when (outcome) {
            is ProviderChatOutcome.HttpResponse -> if (outcome.statusCode in 200..299) {
                ProviderConnectionProbeResult.Connected(providerId, ProviderDiagnosticRecord.endpointHost(endpoint), modelId, latencyMs)
            } else {
                val redacted = ErrorBodyRedactor.redact(outcome.responseBody)
                val errorClass = classifyProviderFailure(outcome.statusCode, redacted)
                append(providerId, endpoint, modelId, outcome.statusCode, errorClass, redacted, latencyMs)
                ProviderConnectionProbeResult.Failed(providerId, errorClass, outcome.statusCode)
            }
            is ProviderChatOutcome.StreamedResponse -> ProviderConnectionProbeResult.Connected(providerId, ProviderDiagnosticRecord.endpointHost(endpoint), modelId, latencyMs)
            ProviderChatOutcome.TimedOut -> failed(providerId, endpoint, modelId, ProviderDiagnosticErrorClass.TIMEOUT, latencyMs)
            ProviderChatOutcome.NetworkFailure -> failed(providerId, endpoint, modelId, ProviderDiagnosticErrorClass.NETWORK, latencyMs)
            ProviderChatOutcome.ResponseTooLarge -> failed(providerId, endpoint, modelId, ProviderDiagnosticErrorClass.RESPONSE_TOO_LARGE, latencyMs)
            ProviderChatOutcome.Cancelled -> ProviderConnectionProbeResult.Blocked("连接测试已取消；没有写入失败诊断。")
        }
    }

    private fun failed(provider: ProviderId, endpoint: String, model: String, error: ProviderDiagnosticErrorClass, latencyMs: Long): ProviderConnectionProbeResult.Failed {
        append(provider, endpoint, model, null, error, null, latencyMs)
        return ProviderConnectionProbeResult.Failed(provider, error, null)
    }

    private fun append(provider: ProviderId, endpoint: String, model: String, status: Int?, error: ProviderDiagnosticErrorClass, body: String?, latencyMs: Long) {
        diagnostics.append(ProviderDiagnosticRecord(
            createdAt = clock.instant(), providerId = provider, endpointHost = ProviderDiagnosticRecord.endpointHost(endpoint),
            apiModelId = model, httpStatus = status, errorClass = error, redactedBody = body?.takeIf { it.isNotBlank() },
            requestShape = "fields=model,messages,max_output;probe=fixed_1_token", latencyMs = latencyMs,
        ))
    }

    private fun probeBody(provider: ProviderId, modelId: String): String {
        val limit = if (provider == ProviderId.OPENROUTER) "max_completion_tokens" else "max_tokens"
        return "{\"model\":\"${modelId.probeJsonEscaped()}\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"$limit\":1,\"stream\":false}"
    }
}

private fun String.probeJsonEscaped(): String = buildString {
    for (char in this@probeJsonEscaped) when (char) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
    }
}
