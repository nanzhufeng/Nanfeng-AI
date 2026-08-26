package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ConversationCostEstimator
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationTitleGenerationId
import com.nanzhufeng.ai.domain.ConversationTitleGenerationRecord
import com.nanzhufeng.ai.domain.ConversationTitleGenerationRecordStore
import com.nanzhufeng.ai.domain.ConversationTitleGenerationStatus
import com.nanzhufeng.ai.domain.ConversationTitleRefinementResult
import com.nanzhufeng.ai.domain.ConversationTitleRefiner
import com.nanzhufeng.ai.domain.ConversationTitleSource
import com.nanzhufeng.ai.domain.LoadModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelResolver
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.ResolvedModelResult
import java.time.Clock
import org.json.JSONObject

/**
 * Sends only the opening user message and its first completed assistant reply to Qwen.  It never
 * receives profile data, later turns or attachments, and it never leaves a local fallback title.
 */
class QwenConversationTitleRefiner(
    private val records: ConversationTitleGenerationRecordStore,
    private val configuration: LoadModelServiceConfigurationUseCase,
    private val credentials: ProviderCredentialStore,
    private val modelResolver: ModelResolver,
    private val transport: ProviderChatTransport,
    private val clock: Clock,
) : ConversationTitleRefiner {
    override fun refine(sourceConversationId: ConversationId, source: ConversationTitleSource): ConversationTitleRefinementResult {
        val requestedAt = clock.instant()
        val preset = ModelPresetId.QWEN_3_7_PLUS
        val providerId = ProviderId.QWEN
        fun record(status: ConversationTitleGenerationStatus, usage: ProviderUsage = ProviderUsage(), safeCode: String? = null) {
            val cost = ConversationCostEstimator.estimate("qwen3.7-plus", usage) ?: ProviderCost()
            records.record(ConversationTitleGenerationRecord(
                ConversationTitleGenerationId.new(), sourceConversationId, requestedAt, status, providerId, "qwen3.7-plus",
                usage, cost, cost.totalMicros?.let { ConversationCostSource.LOCAL_ESTIMATE }, safeCode,
            ))
        }
        val resolved = modelResolver.resolve(preset) as? ResolvedModelResult.Resolved
            ?: return ConversationTitleRefinementResult.Failed("MODEL_UNAVAILABLE")
        val config = configuration.execute(providerId)
            ?: return ConversationTitleRefinementResult.Failed("SERVICE_DISABLED")
        if (!config.settings.enabled || !credentials.hasCredential(providerId)) {
            return ConversationTitleRefinementResult.Failed(if (!config.settings.enabled) "SERVICE_DISABLED" else "CREDENTIAL_MISSING")
        }
        val adapter = QwenChatAdapter()
        val prepared = adapter.prepare(
            model = resolved.model,
            messages = listOf(
                "system" to TITLE_CONTRACT,
                "user" to "用户开头发言：\n${source.userText.take(MAX_SOURCE_CHARS)}\n\n南枫AI开头回答：\n${source.assistantText.take(MAX_SOURCE_CHARS)}",
            ),
            attachments = emptyList(), stream = false,
        ) as? ChatAdapterPrepareResult.Ready
            ?: return ConversationTitleRefinementResult.Failed("REQUEST_UNSUPPORTED")
        val credential = credentials.loadCredential(providerId)
            ?: return ConversationTitleRefinementResult.Failed("CREDENTIAL_MISSING")
        val outcome = try {
            transport.execute(ProviderChatRequest(
                endpoint = "${config.provider.fixedEndpoint}${adapter.endpointPath(ChatRequestOptions.Standard)}",
                jsonBody = prepared.jsonBody, body = prepared.body, expectsStream = false,
                idempotencyKey = "conversation-title-${requestedAt.toEpochMilli()}-${sourceConversationId.value}",
                readTimeoutMillis = adapter.readTimeoutMillis(resolved.model, emptyList(), false),
                maxResponseBytes = ProviderResponseByteBudget.forMaxOutputTokens(256),
            ), credential)
        } finally { credential.fill('\u0000') }
        val reply = when (outcome) {
            is ProviderChatOutcome.HttpResponse -> if (outcome.statusCode in 200..299) adapter.decodeNonStreaming(outcome.responseBody) as? ChatAdapterDecodedResult.Text else null
            else -> null
        } ?: return ConversationTitleRefinementResult.Failed(outcome.safeCode()).also { record(ConversationTitleGenerationStatus.FAILED, safeCode = outcome.safeCode()) }
        val usage = ProviderUsage(reply.inputTokens, reply.outputTokens, cachedInputTokens = reply.cachedInputTokens)
        val title = reply.text.toTitle()
            ?: return ConversationTitleRefinementResult.Failed("RESPONSE_FORMAT").also { record(ConversationTitleGenerationStatus.FAILED, usage, "RESPONSE_FORMAT") }
        record(ConversationTitleGenerationStatus.SUCCEEDED, usage)
        return ConversationTitleRefinementResult.Title(title)
    }

    private fun ProviderChatOutcome.safeCode() = when (this) {
        is ProviderChatOutcome.HttpResponse -> "HTTP_$statusCode"
        ProviderChatOutcome.TimedOut -> "TIMEOUT"
        ProviderChatOutcome.NetworkFailure -> "NETWORK"
        ProviderChatOutcome.ResponseTooLarge -> "RESPONSE_TOO_LARGE"
        ProviderChatOutcome.Cancelled -> "CANCELLED"
        is ProviderChatOutcome.StreamedResponse -> "UNEXPECTED_STREAM"
    }

    private fun String.toTitle(): String? = runCatching {
        val raw = trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val value = JSONObject(raw).optString("title").replace(Regex("\\s+"), " ").trim()
        value.takeIf { it.length in 6..32 && it.none { character -> character in "\n\r。！？!?" } }
    }.getOrNull()

    private companion object {
        const val MAX_SOURCE_CHARS = 4_000
        val TITLE_CONTRACT = """
            你只负责为下方同一对话的开头用户发言与南枫AI开头回答生成一个会话标题。
            标题必须概述“讨论对象 + 用户要解决的任务或结论方向”，让未打开会话的用户也能知道本次讨论主题。
            不得拿回答里的 Markdown 小节、论证步骤、抽象方法词或一句结论片段当标题；不得引入两段文字未明确支持的人名、事实或偏好。
            标题用简体中文，6到32个字符，不要引号、句号、Markdown、编号或省略号。只返回严格 JSON：{"title":"..."}。
        """.trimIndent()
    }
}
