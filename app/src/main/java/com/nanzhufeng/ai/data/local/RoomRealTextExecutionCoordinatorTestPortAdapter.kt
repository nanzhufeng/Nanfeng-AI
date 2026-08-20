package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.ConversationRealTextExecutionId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionRecord
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionState
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionTransitionResult
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorSafeErrorCode
import com.nanzhufeng.ai.domain.RealTextExecutionReadyPlan
import com.nanzhufeng.ai.domain.RealTextExecutionRuntimeReceipt
import com.nanzhufeng.ai.domain.RealTextExecutionRuntimeReceiptPort
import com.nanzhufeng.ai.domain.RealTextExecutionRuntimeReceiptResult
import com.nanzhufeng.ai.domain.RealTextExecutionUsageReservationPort
import com.nanzhufeng.ai.domain.RealTextExecutionUsageReservationResult
import com.nanzhufeng.ai.domain.RealTextUsageReservationPlan
import com.nanzhufeng.ai.domain.UsageFactGrade
import com.nanzhufeng.ai.domain.UsageLedgerAppendResult
import com.nanzhufeng.ai.domain.UsageLedgerEntry
import com.nanzhufeng.ai.domain.UsageLedgerEntryId
import com.nanzhufeng.ai.domain.UsageLedgerKind
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.Callable

/**
 * Android Room test-injection port for the unregistered P3 coordinator.
 *
 * A P3-I receipt must already be prepared by the test fixture. This adapter deliberately does
 * not create Conversation messages, read text deltas back, register with AppContainer, or expose
 * any provider, credential, attachment, HTTP, or UI capability. An outer future owner can place
 * coordinator calls inside one Room transaction; this adapter preserves that transaction boundary.
 */
class RoomRealTextExecutionCoordinatorTestPortAdapter(
    private val database: NanfengAiDatabase,
    private val executions: RoomConversationRealTextExecutionRepository,
    private val usage: RoomUsageLedgerRepository,
) : RealTextExecutionRuntimeReceiptPort, RealTextExecutionUsageReservationPort {

    override fun reserve(plan: RealTextUsageReservationPlan, at: Instant): RealTextExecutionUsageReservationResult =
        appendReservation(plan, at, UsageLedgerKind.BUDGET_RESERVATION, plan.replayToken)

    override fun release(
        plan: RealTextUsageReservationPlan,
        safeErrorCode: String,
        at: Instant,
    ): RealTextExecutionUsageReservationResult = runCatching {
        database.runInTransaction(Callable {
            val token = "p3c-release:${stableId("${plan.replayToken}|$safeErrorCode")}"
            val entries = usage.entriesForExecution(plan.executionId)
            val reservation = entries.firstOrNull { it.kind == UsageLedgerKind.BUDGET_RESERVATION && it.replayToken == plan.replayToken }
                ?: return@Callable rejectedUsage()
            if (!reservation.sameFactIgnoringOccurredAt(plan.toUsageEntry(
                    UsageLedgerKind.BUDGET_RESERVATION, plan.replayToken, reservation.occurredAt,
                ))) return@Callable rejectedUsage()
            val priorRelease = entries
                .firstOrNull { it.kind == UsageLedgerKind.BUDGET_RELEASE }
            if (priorRelease != null && priorRelease.replayToken != token) return@Callable rejectedUsage()
            appendReservation(plan, at, UsageLedgerKind.BUDGET_RELEASE, token)
        })
    }.getOrElse { rejectedUsage() }

    override fun start(plan: RealTextExecutionReadyPlan, at: Instant): RealTextExecutionRuntimeReceiptResult = runCatching {
        database.runInTransaction(Callable {
            val current = matchingRecord(plan) ?: return@Callable rejectedRuntime()
            if (current.state == ConversationRealTextExecutionState.RUNNING) {
                return@Callable RealTextExecutionRuntimeReceiptResult.Replayed(current.toReceipt())
            }
            if (current.state != ConversationRealTextExecutionState.PREPARED) return@Callable rejectedRuntime()
            when (val updated = executions.transition(
                current.request.executionId,
                ConversationRealTextExecutionState.PREPARED,
                ConversationRealTextExecutionState.RUNNING,
                at,
            )) {
                is ConversationRealTextExecutionTransitionResult.Updated ->
                    RealTextExecutionRuntimeReceiptResult.Applied(updated.record.toReceipt())
                is ConversationRealTextExecutionTransitionResult.Rejected -> rejectedRuntime()
            }
        })
    }.getOrElse { rejectedRuntime() }

    /** [delta] is intentionally discarded after validating that the durable receipt is RUNNING. */
    override fun appendPartial(
        plan: RealTextExecutionReadyPlan,
        delta: String,
        at: Instant,
    ): RealTextExecutionRuntimeReceiptResult = runCatching {
        database.runInTransaction(Callable {
            val record = matchingRecord(plan) ?: return@Callable rejectedRuntime()
            when (record.state) {
                ConversationRealTextExecutionState.RUNNING -> RealTextExecutionRuntimeReceiptResult.Applied(record.toReceipt())
                ConversationRealTextExecutionState.SUCCEEDED,
                ConversationRealTextExecutionState.FAILED,
                ConversationRealTextExecutionState.CANCELLED -> RealTextExecutionRuntimeReceiptResult.Replayed(record.toReceipt())
                ConversationRealTextExecutionState.PREPARED -> rejectedRuntime()
            }
        })
    }.getOrElse { rejectedRuntime() }

    override fun finish(
        plan: RealTextExecutionReadyPlan,
        state: ConversationRealTextExecutionState,
        safeErrorCode: String?,
        at: Instant,
    ): RealTextExecutionRuntimeReceiptResult {
        if (state !in terminalStates) return rejectedRuntime()
        return transition(plan, null, state, at, safeErrorCode)
    }

    private fun appendReservation(
        plan: RealTextUsageReservationPlan,
        at: Instant,
        kind: UsageLedgerKind,
        replayToken: String,
    ): RealTextExecutionUsageReservationResult = runCatching {
        database.runInTransaction(Callable {
            val candidate = plan.toUsageEntry(kind, replayToken, at)
            usage.entriesForExecution(plan.executionId).firstOrNull { it.replayToken == replayToken }?.let { existing ->
                return@Callable if (existing.sameFactIgnoringOccurredAt(candidate)) {
                    RealTextExecutionUsageReservationResult.Replayed
                } else rejectedUsage()
            }
            when (usage.append(candidate)) {
                is UsageLedgerAppendResult.Appended -> RealTextExecutionUsageReservationResult.Reserved
                is UsageLedgerAppendResult.Replayed -> RealTextExecutionUsageReservationResult.Replayed
                is UsageLedgerAppendResult.Conflict -> rejectedUsage()
            }
        })
    }.getOrElse { rejectedUsage() }

    private fun transition(
        plan: RealTextExecutionReadyPlan,
        expected: ConversationRealTextExecutionState?,
        next: ConversationRealTextExecutionState,
        at: Instant,
        safeErrorCode: String?,
    ): RealTextExecutionRuntimeReceiptResult = runCatching {
        database.runInTransaction(Callable {
            val current = matchingRecord(plan) ?: return@Callable rejectedRuntime()
            if (current.state in terminalStates) {
                return@Callable if (current.state == next && current.safeErrorCode == safeErrorCode) {
                    RealTextExecutionRuntimeReceiptResult.Replayed(current.toReceipt())
                } else rejectedRuntime()
            }
            if (expected != null && current.state != expected) return@Callable rejectedRuntime()
            when (val updated = executions.transition(current.request.executionId, current.state, next, at, safeErrorCode)) {
                is ConversationRealTextExecutionTransitionResult.Updated ->
                    RealTextExecutionRuntimeReceiptResult.Applied(updated.record.toReceipt())
                is ConversationRealTextExecutionTransitionResult.Rejected -> rejectedRuntime()
            }
        })
    }.getOrElse { rejectedRuntime() }

    private fun matchingRecord(plan: RealTextExecutionReadyPlan): ConversationRealTextExecutionRecord? =
        executions.findById(plan.cancellation.executionId)?.takeIf { record ->
            record.request.executionId == plan.cancellation.executionId &&
                record.request.conversationId == plan.usageReservation.conversationId &&
                record.request.invocationId == plan.cancellation.invocationId &&
                record.request.attemptId == plan.cancellation.attemptId &&
                record.request.requestFingerprint == plan.cancellation.requestFingerprint
        }

    private fun RealTextUsageReservationPlan.toUsageEntry(
        kind: UsageLedgerKind,
        replayToken: String,
        at: Instant,
    ) = UsageLedgerEntry(
        entryId = UsageLedgerEntryId("p3c:${kind.name.lowercase()}:${stableId(replayToken)}"),
        replayToken = replayToken,
        executionId = executionId,
        conversationId = conversationId,
        branchLeafMessageId = branchLeafMessageId,
        invocationId = invocationId,
        attemptId = attemptId,
        kind = kind,
        factGrade = UsageFactGrade.ESTIMATED,
        requestedModelId = requestedModelId.value,
        actualModelId = null,
        inputTokens = if (kind == UsageLedgerKind.BUDGET_RESERVATION) estimatedInputTokens else null,
        outputTokens = if (kind == UsageLedgerKind.BUDGET_RESERVATION) reservedOutputTokens else null,
        cachedInputTokens = null,
        chargeMicros = null,
        budgetMicros = budgetMicros,
        adjustmentMicros = null,
        currencyCode = currencyCode,
        reconciliationFingerprint = reconciliationFingerprint,
        reconcilesEntryId = null,
        source = source,
        occurredAt = at,
    )

    private fun ConversationRealTextExecutionRecord.toReceipt() = RealTextExecutionRuntimeReceipt(
        executionId = request.executionId,
        invocationId = request.invocationId,
        attemptId = request.attemptId,
        requestFingerprint = request.requestFingerprint,
        state = state,
        safeErrorCode = safeErrorCode,
    )

    private fun UsageLedgerEntry.sameFactIgnoringOccurredAt(other: UsageLedgerEntry): Boolean =
        copy(occurredAt = other.occurredAt) == other

    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun rejectedRuntime() = RealTextExecutionRuntimeReceiptResult.Rejected(
        RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_RUNTIME_RECEIPT_REJECTED,
    )

    private fun rejectedUsage() = RealTextExecutionUsageReservationResult.Rejected(
        RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_USAGE_RESERVATION_REJECTED,
    )

    private companion object {
        val terminalStates = setOf(
            ConversationRealTextExecutionState.SUCCEEDED,
            ConversationRealTextExecutionState.FAILED,
            ConversationRealTextExecutionState.CANCELLED,
        )
    }
}
