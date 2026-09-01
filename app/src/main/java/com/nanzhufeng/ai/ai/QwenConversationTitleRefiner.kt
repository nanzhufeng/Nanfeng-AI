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
import com.nanzhufeng.ai.domain.ModelResolver
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.ResolvedModelResult
import java.time.Clock
import org.json.JSONObject

/**
 * Sends only the opening user message and its first completed assistant reply to a configured,
 * economical background model. It never receives profile data, later turns or attachments.
 *
 * Title and history-library refinement intentionally share one fixed candidate order. This keeps
 * these background jobs on the user's low-cost direct providers without changing normal chat or
 * Auto routing.
 */
class ConfiguredConversationTitleRefiner(
    private val records: ConversationTitleGenerationRecordStore,
    private val configuration: LoadModelServiceConfigurationUseCase,
    private val credentials: ProviderCredentialStore,
    private val modelResolver: ModelResolver,
    private val transport: ProviderChatTransport,
    private val clock: Clock,
) : ConversationTitleRefiner {
    override fun refine(sourceConversationId: ConversationId, source: ConversationTitleSource): ConversationTitleRefinementResult {
        val requestedAt = clock.instant()
        fun record(
            status: ConversationTitleGenerationStatus,
            providerId: ProviderId? = null,
            modelId: String? = null,
            usage: ProviderUsage = ProviderUsage(),
            safeCode: String? = null,
        ) {
            val cost = modelId?.let { ConversationCostEstimator.estimate(it, usage, requestedAt) } ?: ProviderCost()
            records.record(ConversationTitleGenerationRecord(
                ConversationTitleGenerationId.new(), sourceConversationId, requestedAt, status, providerId, modelId,
                usage, cost, cost.totalMicros?.let { ConversationCostSource.LOCAL_ESTIMATE }, safeCode,
            ))
        }
        val preflightFailures = mutableListOf<Pair<ProviderId, String>>()
        val configuredProviders = TitleAndHistoryRefinementRouting.candidates.map { it.providerId }.distinct()
            .mapNotNull { providerId ->
                val config = configuration.execute(providerId)
                when {
                    config == null -> {
                        preflightFailures += providerId to "SERVICE_DISABLED"
                        null
                    }
                    !config.settings.enabled -> {
                        preflightFailures += providerId to "SERVICE_DISABLED"
                        null
                    }
                    !credentials.hasCredential(providerId) -> {
                        preflightFailures += providerId to "CREDENTIAL_MISSING"
                        null
                    }
                    else -> providerId to config
                }
            }
            .toMap()
        if (configuredProviders.isEmpty()) {
            preflightFailures.forEach { (providerId, safeCode) ->
                record(ConversationTitleGenerationStatus.FAILED, providerId, safeCode = safeCode)
            }
            return ConversationTitleRefinementResult.Failed("NO_CONFIGURED_TITLE_MODEL")
        }
        TitleAndHistoryRefinementRouting.candidates.filter { it.providerId in configuredProviders }.forEach { candidate ->
            val config = checkNotNull(configuredProviders[candidate.providerId])
            val resolved = modelResolver.resolve(candidate.preset) as? ResolvedModelResult.Resolved
            if (resolved == null || resolved.model.providerId != candidate.providerId) {
                record(ConversationTitleGenerationStatus.FAILED, candidate.providerId, NanfengModelServiceCatalog.preset(candidate.preset).displayName, safeCode = "MODEL_UNAVAILABLE")
                return@forEach
            }
            val adapter = ChatProviderAdapters().adapter(candidate.providerId)
            if (adapter == null) {
                record(ConversationTitleGenerationStatus.FAILED, candidate.providerId, resolved.model.modelId, safeCode = "ADAPTER_UNAVAILABLE")
                return@forEach
            }
            val prepared = adapter.prepare(
                model = resolved.model,
                messages = listOf(
                    "system" to TITLE_CONTRACT,
                    "user" to source.promptInput(),
                ),
                attachments = emptyList(), stream = false,
            ) as? ChatAdapterPrepareResult.Ready
            if (prepared == null) {
                record(ConversationTitleGenerationStatus.FAILED, candidate.providerId, resolved.model.modelId, safeCode = "REQUEST_UNSUPPORTED")
                return@forEach
            }
            val credential = credentials.loadCredential(candidate.providerId)
            if (credential == null) {
                record(ConversationTitleGenerationStatus.FAILED, candidate.providerId, resolved.model.modelId, safeCode = "CREDENTIAL_MISSING")
                return@forEach
            }
            val outcome = try {
                transport.execute(ProviderChatRequest(
                    endpoint = "${config.provider.fixedEndpoint}${adapter.endpointPath(ChatRequestOptions.Standard)}",
                    jsonBody = prepared.jsonBody, body = prepared.body, expectsStream = false,
                    idempotencyKey = "conversation-title-${requestedAt.toEpochMilli()}-${sourceConversationId.value}-${candidate.preset.name}",
                    readTimeoutMillis = adapter.readTimeoutMillis(resolved.model, emptyList(), false),
                    maxResponseBytes = ProviderResponseByteBudget.forMaxOutputTokens(256),
                ), credential)
            } finally { credential.fill('\u0000') }
            val reply = when (outcome) {
                is ProviderChatOutcome.HttpResponse -> if (outcome.statusCode in 200..299) adapter.decodeNonStreaming(outcome.responseBody) as? ChatAdapterDecodedResult.Text else null
                else -> null
            }
            if (reply == null) {
                record(ConversationTitleGenerationStatus.FAILED, candidate.providerId, resolved.model.modelId, safeCode = outcome.safeCode())
                return@forEach
            }
            val usage = ProviderUsage(reply.inputTokens, reply.outputTokens, cachedInputTokens = reply.cachedInputTokens)
            val title = parseConversationTitleResponse(reply.text)
            if (title == null) {
                record(ConversationTitleGenerationStatus.FAILED, candidate.providerId, resolved.model.modelId, usage, "RESPONSE_FORMAT")
                return@forEach
            }
            record(ConversationTitleGenerationStatus.SUCCEEDED, candidate.providerId, resolved.model.modelId, usage)
            return ConversationTitleRefinementResult.Title(title)
        }
        return ConversationTitleRefinementResult.Failed("ALL_CONFIGURED_TITLE_MODELS_FAILED")
    }

    private fun ProviderChatOutcome.safeCode() = when (this) {
        is ProviderChatOutcome.HttpResponse -> "HTTP_$statusCode"
        ProviderChatOutcome.TimedOut -> "TIMEOUT"
        is ProviderChatOutcome.NetworkFailure -> "NETWORK"
        ProviderChatOutcome.ResponseTooLarge -> "RESPONSE_TOO_LARGE"
        ProviderChatOutcome.Cancelled -> "CANCELLED"
        is ProviderChatOutcome.StreamedResponse -> "UNEXPECTED_STREAM"
    }

    private fun ConversationTitleSource.promptInput(): String = if (userText.isBlank()) {
        "首条用户消息仅含附件。不要读取或推断附件内容；只根据下方南枫AI开头回答生成标题：\n${assistantText.take(MAX_SOURCE_CHARS)}"
    } else {
        "用户开头发言：\n${userText.take(MAX_SOURCE_CHARS)}\n\n南枫AI开头回答：\n${assistantText.take(MAX_SOURCE_CHARS)}"
    }

    private companion object {
        const val MAX_SOURCE_CHARS = 4_000
        val TITLE_CONTRACT = """
            你只负责为下方同一对话的开头用户发言与南枫AI开头回答生成一个会话标题。
            标题必须是“明确对象 + 具体意图、问题或任务”的紧凑短语，让未打开会话的用户立即知道讨论什么、要做什么。
            优先复用原文明确出现的主体、产品、组织或术语，并用准确动作收束，例如“模型差异与费用分析”“产品设计范式冲突”“视频内容分析”“API Key与模型选择”。
            不得把回答里的 Markdown 小节、论证步骤、抽象方法词或一句结论片段当标题；不得引入两段文字未明确支持的人名、事实或偏好。
            禁止只写“继续说”“分析”“总结”“问题”“请求”“聊天”“对话”“更新文档”等没有讨论对象的空泛标题；无法同时确认对象和意图时返回空 title，不得猜测。
            标题必须是 6 到 13 个字符的一句话总结，优先约 8 个字符，在不丢失明确对象和意图的前提下越精炼越好。只能使用汉字、英文字母或阿拉伯数字；英文短语的单词之间允许一个普通空格。严禁标点、引号、Markdown、编号符号、emoji、括号、斜杠、下划线、连字符和任何其他符号。只返回严格 JSON：{"title":"..."}。
        """.trimIndent()
    }

}

/**
 * The generated title is a plain-language drawer label. Keep the acceptance rule local and
 * deterministic so provider formatting cannot leak symbols, code, or identifiers into it.
 */
internal fun parseConversationTitleResponse(rawResponse: String): String? = runCatching {
    val raw = rawResponse.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val title = JSONObject(raw).optString("title").replace(Regex("\\s+"), " ").trim()
    title.takeIf {
        it.length in 6..13 &&
            it !in GENERIC_TITLES &&
            it.split(' ').all { word -> word.isNotEmpty() && word.all(Char::isConversationTitleCharacter) }
    }
}.getOrNull()

private val GENERIC_TITLES = setOf("继续说", "分析", "总结", "问题", "请求", "聊天", "对话", "更新文档", "事实核验", "工程观点")

private fun Char.isConversationTitleCharacter(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || code in 0x4E00..0x9FFF
