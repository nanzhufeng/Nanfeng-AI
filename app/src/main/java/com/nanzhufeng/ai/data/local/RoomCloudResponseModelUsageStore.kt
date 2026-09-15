package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.CloudResponseModelUsage
import com.nanzhufeng.ai.domain.CloudResponseModelUsageStore
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderUsage

/** Read owner for portable, content-free answer accounting restored from another device. */
class RoomCloudResponseModelUsageStore(
    private val database: NanfengAiDatabase,
) : CloudResponseModelUsageStore {
    fun recordVerified(conversationId: com.nanzhufeng.ai.domain.ConversationId, usages: Collection<CloudResponseModelUsage>) = database.runInTransaction {
        val dao = database.cloudResponseModelUsageDao()
        usages.forEach { value ->
            val incoming = CloudResponseModelUsageEntity(conversationId.value, value.assistantMessageId.value,
                value.modelId, value.modelDisplayName, value.usage.inputTokens, value.usage.outputTokens,
                value.usage.totalTokens, value.usage.cachedInputTokens, value.usage.reasoningTokens,
                value.cost.priceVersion, value.cost.currencyCode, value.cost.totalMicros, value.costSource?.name)
            val existing = dao.find(conversationId.value, value.assistantMessageId.value)
            if (existing == null) dao.insert(incoming)
            else if (existing.modelId == incoming.modelId && existing.costTotalMicros == null && incoming.costTotalMicros != null) {
                // Unknown is not a settled zero. Repair an earlier partial import,
                // retaining the original model identity and any existing usage facts.
                dao.replace(existing.copy(
                    inputTokens = existing.inputTokens ?: incoming.inputTokens,
                    outputTokens = existing.outputTokens ?: incoming.outputTokens,
                    totalTokens = existing.totalTokens ?: incoming.totalTokens,
                    cachedInputTokens = existing.cachedInputTokens ?: incoming.cachedInputTokens,
                    reasoningTokens = existing.reasoningTokens ?: incoming.reasoningTokens,
                    costPriceVersion = incoming.costPriceVersion, costCurrencyCode = incoming.costCurrencyCode,
                    costTotalMicros = incoming.costTotalMicros, costSource = incoming.costSource,
                ))
            }
        }
    }

    override fun forMessages(messageIds: Collection<MessageNodeId>): Map<MessageNodeId, List<CloudResponseModelUsage>> {
        if (messageIds.isEmpty()) return emptyMap()
        return database.cloudResponseModelUsageDao()
            .forMessages(messageIds.map(MessageNodeId::value))
            .map(CloudResponseModelUsageEntity::toDomain)
            .groupBy(CloudResponseModelUsage::assistantMessageId)
    }
}

internal fun CloudResponseModelUsageEntity.toDomain() = CloudResponseModelUsage(
    assistantMessageId = MessageNodeId(assistantMessageId),
    modelId = modelId,
    modelDisplayName = modelDisplayName,
    usage = ProviderUsage(inputTokens, outputTokens, totalTokens, cachedInputTokens, reasoningTokens),
    cost = ProviderCost(costPriceVersion, costCurrencyCode, costTotalMicros),
    costSource = costSource?.let(ConversationCostSource::valueOf),
)
