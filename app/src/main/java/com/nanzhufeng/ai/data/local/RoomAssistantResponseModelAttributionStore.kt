package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.AssistantResponseModelAttribution
import com.nanzhufeng.ai.domain.AssistantResponseModelAttributionStore
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.NormalChatSendAttemptId
import com.nanzhufeng.ai.domain.ProviderId
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
                existing.modelDisplayName == attribution.modelDisplayName
            ) { "助手回复已有冲突的模型归属。" }
        }
    })

    override fun forMessages(messageIds: Collection<MessageNodeId>): Map<MessageNodeId, List<AssistantResponseModelAttribution>> {
        if (messageIds.isEmpty()) return emptyMap()
        return database.assistantResponseModelAttributionDao()
            .forMessages(messageIds.map(MessageNodeId::value))
            .map(AssistantResponseModelAttributionEntity::toDomain)
            .groupBy(AssistantResponseModelAttribution::assistantMessageId)
    }
}

private fun AssistantResponseModelAttribution.toEntity() = AssistantResponseModelAttributionEntity(
    assistantMessageId = assistantMessageId.value,
    attemptId = attemptId.value,
    providerId = providerId.name,
    receiverProviderId = receiverProviderId.name,
    modelId = modelId,
    modelDisplayName = modelDisplayName,
    recordedAtEpochMs = recordedAt.toEpochMilli(),
)

private fun AssistantResponseModelAttributionEntity.toDomain() = AssistantResponseModelAttribution(
    assistantMessageId = MessageNodeId(assistantMessageId),
    attemptId = NormalChatSendAttemptId(attemptId),
    providerId = ProviderId.valueOf(providerId),
    receiverProviderId = ProviderId.valueOf(receiverProviderId),
    modelId = modelId,
    modelDisplayName = modelDisplayName,
    recordedAt = Instant.ofEpochMilli(recordedAtEpochMs),
)
