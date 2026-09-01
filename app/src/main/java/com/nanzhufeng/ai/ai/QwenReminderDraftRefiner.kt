package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ConversationCostEstimator
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.LoadModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelResolver
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.ReminderDraftGenerationId
import com.nanzhufeng.ai.domain.ReminderDraftGenerationRecord
import com.nanzhufeng.ai.domain.ReminderDraftGenerationRecordStore
import com.nanzhufeng.ai.domain.ReminderDraftGenerationStatus
import com.nanzhufeng.ai.domain.ReminderDraftRefinementResult
import com.nanzhufeng.ai.domain.ReminderDraftRefiner
import com.nanzhufeng.ai.domain.ResolvedModelResult
import com.nanzhufeng.ai.domain.ScheduledMonitorCadence
import com.nanzhufeng.ai.domain.ScheduledMonitorDraftSource
import com.nanzhufeng.ai.domain.ScheduledMonitorSuggestion
import java.time.Clock
import org.json.JSONObject

/**
 * An explicit, narrow Qwen call that turns one opening dialogue pair into an editable reminder
 * draft. It never receives the rest of the transcript or attachments and never falls back to a
 * category template when Qwen cannot make a grounded draft.
 */
class QwenReminderDraftRefiner(
    private val records: ReminderDraftGenerationRecordStore,
    private val configuration: LoadModelServiceConfigurationUseCase,
    private val credentials: ProviderCredentialStore,
    private val modelResolver: ModelResolver,
    private val transport: ProviderChatTransport,
    private val clock: Clock,
) : ReminderDraftRefiner {
    override fun refine(sourceConversationId: ConversationId, source: ScheduledMonitorDraftSource): ReminderDraftRefinementResult {
        val requestedAt = clock.instant()
        val preset = ModelPresetId.QWEN_3_7_PLUS
        val providerId = ProviderId.QWEN
        fun record(
            status: ReminderDraftGenerationStatus,
            usage: ProviderUsage = ProviderUsage(),
            safeCode: String? = null,
        ) {
            val cost = ConversationCostEstimator.estimate("qwen3.7-plus", usage, requestedAt) ?: ProviderCost()
            records.record(ReminderDraftGenerationRecord(
                ReminderDraftGenerationId.new(), sourceConversationId, requestedAt, status, providerId, "qwen3.7-plus",
                usage, cost, cost.totalMicros?.let { ConversationCostSource.LOCAL_ESTIMATE }, safeCode,
            ))
        }

        val resolved = modelResolver.resolve(preset) as? ResolvedModelResult.Resolved
            ?: return ReminderDraftRefinementResult.Failed("MODEL_UNAVAILABLE").also { record(ReminderDraftGenerationStatus.FAILED, safeCode = "MODEL_UNAVAILABLE") }
        val config = configuration.execute(providerId)
            ?: return ReminderDraftRefinementResult.Failed("SERVICE_DISABLED").also { record(ReminderDraftGenerationStatus.FAILED, safeCode = "SERVICE_DISABLED") }
        if (!config.settings.enabled || !credentials.hasCredential(providerId)) {
            return ReminderDraftRefinementResult.Failed(if (!config.settings.enabled) "SERVICE_DISABLED" else "CREDENTIAL_MISSING")
                .also { record(ReminderDraftGenerationStatus.FAILED, safeCode = if (!config.settings.enabled) "SERVICE_DISABLED" else "CREDENTIAL_MISSING") }
        }
        val adapter = QwenChatAdapter()
        val prepared = adapter.prepare(
            model = resolved.model,
            messages = listOf(
                "system" to REFINEMENT_CONTRACT,
                "user" to "用户开头发言：\n${source.userText.take(MAX_SOURCE_CHARS)}\n\n南枫AI开头回答：\n${source.assistantText.take(MAX_SOURCE_CHARS)}",
            ),
            attachments = emptyList(), stream = false,
        ) as? ChatAdapterPrepareResult.Ready
            ?: return ReminderDraftRefinementResult.Failed("REQUEST_UNSUPPORTED").also { record(ReminderDraftGenerationStatus.FAILED, safeCode = "REQUEST_UNSUPPORTED") }
        val credential = credentials.loadCredential(providerId)
            ?: return ReminderDraftRefinementResult.Failed("CREDENTIAL_MISSING").also { record(ReminderDraftGenerationStatus.FAILED, safeCode = "CREDENTIAL_MISSING") }
        val outcome = try {
            transport.execute(ProviderChatRequest(
                endpoint = "${config.provider.fixedEndpoint}${adapter.endpointPath(ChatRequestOptions.Standard)}",
                jsonBody = prepared.jsonBody, body = prepared.body, expectsStream = false,
                idempotencyKey = "reminder-draft-${requestedAt.toEpochMilli()}-${sourceConversationId.value}",
                readTimeoutMillis = adapter.readTimeoutMillis(resolved.model, emptyList(), false),
                maxResponseBytes = ProviderResponseByteBudget.forMaxOutputTokens(1_024),
            ), credential)
        } finally { credential.fill('\u0000') }
        val reply = when (outcome) {
            is ProviderChatOutcome.HttpResponse -> if (outcome.statusCode in 200..299) adapter.decodeNonStreaming(outcome.responseBody) as? ChatAdapterDecodedResult.Text else null
            else -> null
        } ?: return ReminderDraftRefinementResult.Failed(outcome.safeCode()).also { record(ReminderDraftGenerationStatus.FAILED, safeCode = outcome.safeCode()) }
        val usage = ProviderUsage(reply.inputTokens, reply.outputTokens, cachedInputTokens = reply.cachedInputTokens)
        val suggestion = reply.text.toScheduledMonitorSuggestion()
        if (suggestion == null) {
            record(ReminderDraftGenerationStatus.NOT_ELIGIBLE, usage)
            return ReminderDraftRefinementResult.NotEligible
        }
        record(ReminderDraftGenerationStatus.SUCCEEDED, usage)
        return ReminderDraftRefinementResult.Draft(suggestion)
    }

    private fun ProviderChatOutcome.safeCode() = when (this) {
        is ProviderChatOutcome.HttpResponse -> "HTTP_$statusCode"
        ProviderChatOutcome.TimedOut -> "TIMEOUT"
        is ProviderChatOutcome.NetworkFailure -> "NETWORK"
        ProviderChatOutcome.ResponseTooLarge -> "RESPONSE_TOO_LARGE"
        ProviderChatOutcome.Cancelled -> "CANCELLED"
        is ProviderChatOutcome.StreamedResponse -> "UNEXPECTED_STREAM"
    }

    private fun String.toScheduledMonitorSuggestion(): ScheduledMonitorSuggestion? = runCatching {
        val raw = trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val value = JSONObject(raw)
        if (!value.optBoolean("eligible", false)) return@runCatching null
        val title = value.optString("title").trim().take(20)
        val instruction = value.optString("instruction").trim().take(800)
        val cadence = when (value.optString("cadence")) {
            "HOURLY" -> ScheduledMonitorCadence.HOURLY
            "DAILY" -> ScheduledMonitorCadence.DAILY
            else -> return@runCatching null
        }
        if (title.length < 2 || instruction.length < 12) null else ScheduledMonitorSuggestion(title, instruction, cadence)
    }.getOrNull()

    private companion object {
        const val MAX_SOURCE_CHARS = 4_000
        val REFINEMENT_CONTRACT = """
            你只负责将下方给出的同一对话的开头用户发言与南枫AI回答整理为“可编辑的周期提醒草案”。
            不得使用任何预设主题、用户资料、历史对话或常识补全；两段文字未明确支持的对象、条件、频率不得写入。
            仅当用户明确提出未来持续跟踪、提醒、定期查看或变化通知，并且有可长期追踪的对象时，eligible 才能为 true。一次性比较、旅行建议、解释、闲聊、开发讨论、泛泛“动态/价格”都必须为 false。
            eligible=true 时：title 不超过20个汉字；instruction 只写监控对象、用户明确的触发/关注变化、通知筛选条件，不超过300个汉字；cadence 只能 HOURLY 或 DAILY，未明确时 DAILY。
            只返回严格 JSON，不要 Markdown：{"eligible":true,"title":"...","instruction":"...","cadence":"DAILY"}；不符合条件返回 {"eligible":false,"title":"","instruction":"","cadence":"DAILY"}。
        """.trimIndent()
    }
}
