package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionId
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.UsageFactGrade
import com.nanzhufeng.ai.domain.UsageLedgerAppendResult
import com.nanzhufeng.ai.domain.UsageLedgerEntry
import com.nanzhufeng.ai.domain.UsageLedgerEntryId
import com.nanzhufeng.ai.domain.UsageLedgerKind
import com.nanzhufeng.ai.domain.UsageLedgerReadModel
import com.nanzhufeng.ai.domain.UsageLedgerReadModelFactory
import com.nanzhufeng.ai.domain.UsageLedgerRepository
import com.nanzhufeng.ai.domain.UsageLedgerSource
import java.time.Instant
import java.util.concurrent.Callable

/** Local append-only persistence owner. It exposes neither updates nor deletes. */
class RoomUsageLedgerRepository(private val database: NanfengAiDatabase) : UsageLedgerRepository {
    override fun append(entry: UsageLedgerEntry): UsageLedgerAppendResult = database.runInTransaction(Callable {
        val dao = database.usageLedgerDao()
        dao.findById(entry.entryId.value)?.let { existing ->
            val restored = existing.toDomain()
            return@Callable if (restored == entry) UsageLedgerAppendResult.Replayed(restored)
            else UsageLedgerAppendResult.Conflict("相同账本 ID 的事实不一致。")
        }
        dao.findByReplayToken(entry.replayToken)?.let { existing ->
            val restored = existing.toDomain()
            return@Callable if (restored == entry) UsageLedgerAppendResult.Replayed(restored)
            else UsageLedgerAppendResult.Conflict("相同重放标识的账本事实不一致。")
        }
        if (entry.kind == UsageLedgerKind.RECONCILIATION_ADJUSTMENT) {
            val pending = dao.findById(requireNotNull(entry.reconcilesEntryId).value)?.toDomain()
                ?: return@Callable UsageLedgerAppendResult.Conflict("对账目标不存在。")
            if (pending.kind != UsageLedgerKind.STREAM_PENDING || pending.executionId != entry.executionId ||
                pending.reconciliationFingerprint != entry.reconciliationFingerprint
            ) return@Callable UsageLedgerAppendResult.Conflict("对账目标与流式 pending 事实不匹配。")
        }
        dao.insert(entry.toEntity())
        UsageLedgerAppendResult.Appended(requireNotNull(dao.findById(entry.entryId.value)).toDomain())
    })

    override fun entriesForExecution(executionId: ConversationRealTextExecutionId): List<UsageLedgerEntry> =
        database.usageLedgerDao().entriesForExecution(executionId.value).map { it.toDomain() }

    override fun readModelForExecution(executionId: ConversationRealTextExecutionId): UsageLedgerReadModel =
        UsageLedgerReadModelFactory.create(entriesForExecution(executionId))
}

private fun UsageLedgerEntry.toEntity() = UsageLedgerEntryEntity(
    entryId = entryId.value, replayToken = replayToken, executionId = executionId.value,
    conversationId = conversationId.value, branchLeafMessageId = branchLeafMessageId.value,
    invocationId = invocationId.value, attemptId = attemptId.value, kind = kind.name, factGrade = factGrade.name,
    requestedModelId = requestedModelId, actualModelId = actualModelId, inputTokens = inputTokens,
    outputTokens = outputTokens, cachedInputTokens = cachedInputTokens, chargeMicros = chargeMicros,
    budgetMicros = budgetMicros, adjustmentMicros = adjustmentMicros, currencyCode = currencyCode,
    reconciliationFingerprint = reconciliationFingerprint, reconcilesEntryId = reconcilesEntryId?.value,
    source = source.name, occurredAtEpochMs = occurredAt.toEpochMilli(),
)

private fun UsageLedgerEntryEntity.toDomain() = UsageLedgerEntry(
    entryId = UsageLedgerEntryId(entryId), replayToken = replayToken,
    executionId = ConversationRealTextExecutionId(executionId), conversationId = ConversationId(conversationId),
    branchLeafMessageId = MessageNodeId(branchLeafMessageId), invocationId = InvocationId(invocationId),
    attemptId = ProviderAttemptId(attemptId), kind = UsageLedgerKind.valueOf(kind),
    factGrade = UsageFactGrade.valueOf(factGrade), requestedModelId = requestedModelId,
    actualModelId = actualModelId, inputTokens = inputTokens, outputTokens = outputTokens,
    cachedInputTokens = cachedInputTokens, chargeMicros = chargeMicros, budgetMicros = budgetMicros,
    adjustmentMicros = adjustmentMicros, currencyCode = currencyCode,
    reconciliationFingerprint = reconciliationFingerprint, reconcilesEntryId = reconcilesEntryId?.let(::UsageLedgerEntryId),
    source = UsageLedgerSource.valueOf(source), occurredAt = Instant.ofEpochMilli(occurredAtEpochMs),
)
