package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionContract
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionPrepareResult
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionRecord
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionRepository
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionRequest
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionState
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionTransitionResult
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.ProviderAttemptId
import java.time.Instant
import java.util.concurrent.Callable

/**
 * P3-I's isolated receipt owner. No current UI or transport constructs this repository, and it
 * deliberately does not write Conversation nodes or Invocation/Usage rows.
 */
class RoomConversationRealTextExecutionRepository(
    private val database: NanfengAiDatabase,
) : ConversationRealTextExecutionRepository {
    override fun prepare(request: ConversationRealTextExecutionRequest): ConversationRealTextExecutionPrepareResult = database.runInTransaction(Callable {
        val dao = database.conversationRealTextExecutionDao()
        dao.findByIdempotencyKey(request.idempotencyKey)?.let { existing ->
            val restored = existing.toDomain()
            return@Callable if (restored.request == request) {
                ConversationRealTextExecutionPrepareResult.Replayed(restored)
            } else ConversationRealTextExecutionPrepareResult.Conflict("相同幂等键的执行事实不一致。")
        }
        dao.insert(ConversationRealTextExecutionContract.prepared(request).toEntity())
        ConversationRealTextExecutionPrepareResult.Prepared(requireNotNull(dao.findById(request.executionId.value)).toDomain())
    })

    override fun findById(id: ConversationRealTextExecutionId): ConversationRealTextExecutionRecord? =
        database.conversationRealTextExecutionDao().findById(id.value)?.toDomain()

    override fun transition(
        executionId: ConversationRealTextExecutionId,
        expected: ConversationRealTextExecutionState,
        next: ConversationRealTextExecutionState,
        updatedAt: Instant,
        safeErrorCode: String?,
    ): ConversationRealTextExecutionTransitionResult = database.runInTransaction(Callable {
        val dao = database.conversationRealTextExecutionDao()
        val existing = dao.findById(executionId.value)
            ?: return@Callable ConversationRealTextExecutionTransitionResult.Rejected("执行事实不存在。")
        val candidate = ConversationRealTextExecutionContract.transition(existing.toDomain(), next, updatedAt, safeErrorCode)
        val updated = candidate as? ConversationRealTextExecutionTransitionResult.Updated ?: return@Callable candidate
        if (existing.state != expected.name) return@Callable ConversationRealTextExecutionTransitionResult.Rejected("执行状态已变化，请回读后重试。")
        if (dao.transition(executionId.value, expected.name, updated.record.state.name, updated.record.updatedAt.toEpochMilli(), updated.record.terminalAt?.toEpochMilli(), updated.record.safeErrorCode) != 1) {
            return@Callable ConversationRealTextExecutionTransitionResult.Rejected("执行状态未能原子更新。")
        }
        ConversationRealTextExecutionTransitionResult.Updated(requireNotNull(dao.findById(executionId.value)).toDomain())
    })
}

private fun ConversationRealTextExecutionRecord.toEntity() = ConversationRealTextExecutionEntity(
    executionId = request.executionId.value, idempotencyKey = request.idempotencyKey, requestFingerprint = request.requestFingerprint,
    conversationId = request.conversationId.value, userMessageId = request.userMessageId.value,
    assistantMessageId = request.assistantMessageId.value, invocationId = request.invocationId.value,
    attemptId = request.attemptId.value, state = state.name, createdAtEpochMs = request.createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(), terminalAtEpochMs = terminalAt?.toEpochMilli(), safeErrorCode = safeErrorCode,
)

private fun ConversationRealTextExecutionEntity.toDomain() = ConversationRealTextExecutionRecord(
    request = ConversationRealTextExecutionRequest(
        executionId = ConversationRealTextExecutionId(executionId), idempotencyKey = idempotencyKey,
        requestFingerprint = requestFingerprint, conversationId = ConversationId(conversationId),
        userMessageId = MessageNodeId(userMessageId), assistantMessageId = MessageNodeId(assistantMessageId),
        invocationId = InvocationId(invocationId), attemptId = ProviderAttemptId(attemptId), createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    ),
    state = ConversationRealTextExecutionState.valueOf(state), updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    terminalAt = terminalAtEpochMs?.let(Instant::ofEpochMilli), safeErrorCode = safeErrorCode,
)
