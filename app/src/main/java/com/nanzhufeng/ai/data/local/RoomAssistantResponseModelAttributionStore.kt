package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.AssistantResponseModelAttribution
import com.nanzhufeng.ai.domain.AssistantResponseModelAttributionStore
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.ConversationStyle
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.NormalChatSendAttemptId
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.withAvailableLocalCostEstimate
import java.time.Instant
import java.util.concurrent.Callable

/** Room owner for visible-response provenance; it never stores prompt, attachment or response text. */
class RoomAssistantResponseModelAttributionStore(
    private val database: NanfengAiDatabase,
) : AssistantResponseModelAttributionStore {
    override fun record(attribution: AssistantResponseModelAttribution) = database.runInTransaction(Callable {
        val dao = database.assistantResponseModelAttributionDao()
        val existing = dao.find(attribution.assistantMessageId.value, attribution.attemptId.value)
        if (existing == null) {
            dao.insert(attribution.toEntity())
        } else {
            require(existing.providerId == attribution.providerId.name &&
                existing.receiverProviderId == attribution.receiverProviderId.name &&
                existing.modelId == attribution.modelId &&
                existing.modelDisplayName == attribution.modelDisplayName &&
                compatible(existing.conversationStyleId, attribution.conversationStyle?.persistedId) &&
                compatible(existing.webSearchUsed, attribution.webSearchUsed) &&
                compatible(existing.webSearchRequested, attribution.webSearchRequested)
            ) { "助手回复已有冲突的模型归属。" }
            require(
                compatible(existing.inputTokens, attribution.usage.inputTokens) &&
                    compatible(existing.outputTokens, attribution.usage.outputTokens) &&
                    compatible(existing.totalTokens, attribution.usage.totalTokens) &&
                    compatible(existing.cachedInputTokens, attribution.usage.cachedInputTokens) &&
                    compatible(existing.reasoningTokens, attribution.usage.reasoningTokens) &&
                    compatible(existing.costPriceVersion, attribution.cost.priceVersion) &&
                    compatible(existing.costCurrencyCode, attribution.cost.currencyCode) &&
                    compatible(existing.costTotalMicros, attribution.cost.totalMicros) &&
                    compatible(existing.costSource, attribution.costSource?.name)
            ) { "助手回复已有冲突的费用事实。" }
            // Model provenance is written before transport for streaming visibility. A completed
            // response may enrich that exact row once; an unknown retry can never erase it.
            check(dao.enrichCompletedFacts(
                attribution.assistantMessageId.value, attribution.attemptId.value,
                attribution.conversationStyle?.persistedId, attribution.webSearchUsed, attribution.webSearchRequested,
                attribution.usage.inputTokens, attribution.usage.outputTokens, attribution.usage.totalTokens,
                attribution.usage.cachedInputTokens, attribution.usage.reasoningTokens,
                attribution.cost.priceVersion, attribution.cost.currencyCode, attribution.cost.totalMicros,
                attribution.costSource?.name,
            ) == 1) { "助手回复完成事实未能写入本机。" }
        }
    })

    override fun forMessages(messageIds: Collection<MessageNodeId>): Map<MessageNodeId, List<AssistantResponseModelAttribution>> {
        if (messageIds.isEmpty()) return emptyMap()
        return database.assistantResponseModelAttributionDao()
            .forMessages(messageIds.map(MessageNodeId::value))
            .map(AssistantResponseModelAttributionEntity::toDomain)
            .map(AssistantResponseModelAttribution::withAvailableLocalCostEstimate)
            .groupBy(AssistantResponseModelAttribution::assistantMessageId)
    }

    override fun listCostedNewestFirst(): List<AssistantResponseModelAttribution> {
        val entities = database.assistantResponseModelAttributionDao().listCostedNewestFirst()
        if (entities.isEmpty()) return emptyList()
        val durations = database.normalChatSendAttemptDao().completedDurations(entities.map { it.attemptId })
            .associate { it.attemptId to it.modelDurationMillis() }
        return entities.map { entity ->
            entity.toDomain().withAvailableLocalCostEstimate()
                .copy(modelDurationMillis = durations[entity.attemptId])
        }
    }
}

private fun <T> compatible(existing: T?, incoming: T?): Boolean =
    existing == null || incoming == null || existing == incoming

/** The completed Attempt is the sole timestamp owner; absent/interrupted attempts stay unknown. */
private fun CompletedNormalChatAttemptDuration.modelDurationMillis(): Long? =
    (updatedAtEpochMs - createdAtEpochMs)
        .takeIf { it > 0L }

private fun AssistantResponseModelAttribution.toEntity() = AssistantResponseModelAttributionEntity(
    assistantMessageId = assistantMessageId.value,
    attemptId = attemptId.value,
    providerId = providerId.name,
    receiverProviderId = receiverProviderId.name,
    modelId = modelId,
    modelDisplayName = modelDisplayName,
    conversationStyleId = conversationStyle?.persistedId,
    webSearchUsed = webSearchUsed,
    webSearchRequested = webSearchRequested,
    recordedAtEpochMs = recordedAt.toEpochMilli(),
    inputTokens = usage.inputTokens,
    outputTokens = usage.outputTokens,
    totalTokens = usage.totalTokens,
    cachedInputTokens = usage.cachedInputTokens,
    reasoningTokens = usage.reasoningTokens,
    costPriceVersion = cost.priceVersion,
    costCurrencyCode = cost.currencyCode,
    costTotalMicros = cost.totalMicros,
    costSource = costSource?.name,
)

private fun AssistantResponseModelAttributionEntity.toDomain() = AssistantResponseModelAttribution(
    assistantMessageId = MessageNodeId(assistantMessageId),
    attemptId = NormalChatSendAttemptId(attemptId),
    providerId = ProviderId.valueOf(providerId),
    receiverProviderId = ProviderId.valueOf(receiverProviderId),
    modelId = modelId,
    modelDisplayName = modelDisplayName,
    conversationStyle = conversationStyleId?.let(ConversationStyle::fromPersistedId),
    webSearchUsed = webSearchUsed,
    webSearchRequested = webSearchRequested,
    recordedAt = Instant.ofEpochMilli(recordedAtEpochMs),
    usage = com.nanzhufeng.ai.domain.ProviderUsage(inputTokens, outputTokens, totalTokens, cachedInputTokens, reasoningTokens),
    cost = com.nanzhufeng.ai.domain.ProviderCost(costPriceVersion, costCurrencyCode, costTotalMicros),
    costSource = costSource?.let(ConversationCostSource::valueOf),
)
