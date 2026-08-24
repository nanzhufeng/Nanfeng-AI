package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.NormalChatSendAttempt
import com.nanzhufeng.ai.domain.NormalChatSendAttemptId
import com.nanzhufeng.ai.domain.NormalChatSendAttemptStatus
import com.nanzhufeng.ai.domain.NormalChatSendAttemptStore
import com.nanzhufeng.ai.domain.ProviderId
import java.time.Instant
import java.util.concurrent.Callable

class RoomNormalChatSendAttemptStore(private val database: NanfengAiDatabase) : NormalChatSendAttemptStore {
    override fun create(attempt: NormalChatSendAttempt): NormalChatSendAttempt = database.runInTransaction(Callable {
        val dao = database.normalChatSendAttemptDao()
        dao.insert(attempt.toEntity())
        requireNotNull(dao.find(attempt.attemptId.value)).toDomain()
    })

    override fun transition(id: NormalChatSendAttemptId, expected: Set<NormalChatSendAttemptStatus>, next: NormalChatSendAttemptStatus, updatedAt: Instant, safeErrorCode: String?): NormalChatSendAttempt? = database.runInTransaction(Callable {
        val dao = database.normalChatSendAttemptDao()
        if (dao.transition(id.value, expected.map { it.name }, next.name, updatedAt.toEpochMilli(), safeErrorCode) != 1) return@Callable null
        dao.find(id.value)?.toDomain()
    })

    override fun findLatestForConversation(conversationId: ConversationId): NormalChatSendAttempt? =
        database.normalChatSendAttemptDao().latestForConversation(conversationId.value)?.toDomain()

    override fun markFailed(id: NormalChatSendAttemptId, updatedAt: Instant, safeErrorCode: String): NormalChatSendAttempt? =
        transition(
            id,
            setOf(
                NormalChatSendAttemptStatus.PENDING,
                NormalChatSendAttemptStatus.SENDING,
                NormalChatSendAttemptStatus.ACCEPTED,
                NormalChatSendAttemptStatus.STREAMING,
                NormalChatSendAttemptStatus.UNKNOWN,
                NormalChatSendAttemptStatus.FAILED,
            ),
            NormalChatSendAttemptStatus.FAILED,
            updatedAt,
            safeErrorCode,
        )

    override fun markInterruptedAsUnknown(updatedAt: Instant): Int =
        database.normalChatSendAttemptDao().markInterruptedAsUnknown(updatedAt.toEpochMilli())
}

private fun NormalChatSendAttempt.toEntity() = NormalChatSendAttemptEntity(
    attemptId.value, messageId.value, conversationId.value, providerId.name, modelId, idempotencyKey,
    status.name, createdAt.toEpochMilli(), updatedAt.toEpochMilli(), safeErrorCode, egressProviderId?.name,
)

private fun NormalChatSendAttemptEntity.toDomain() = NormalChatSendAttempt(
    NormalChatSendAttemptId(attemptId), MessageNodeId(messageId), ConversationId(conversationId),
    ProviderId.valueOf(providerId), modelId, idempotencyKey, NormalChatSendAttemptStatus.valueOf(status),
    Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs), safeErrorCode, egressProviderId?.let(ProviderId::valueOf),
)
