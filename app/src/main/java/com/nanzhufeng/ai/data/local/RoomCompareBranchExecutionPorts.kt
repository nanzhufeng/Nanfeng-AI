package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.CompareBranchReservationState
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionState
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
 * Production-shaped Compare-only Room ports. They deliberately remain unregistered: constructing
 * this object neither loads credentials nor creates an HTTP client. Each execution is bound to a
 * Schema 32 Compare branch before any receipt or Usage fact can be appended.
 *
 * The transient [appendPartial] delta is verified only against the receipt state and is discarded.
 * This owner never writes `message_nodes`, `conversations.currentLeafMessageId`, or P3 runtime
 * rows, so two Compare branches cannot collide with the ordinary per-conversation runtime.
 */
class RoomCompareBranchExecutionPorts(
    private val database: NanfengAiDatabase,
    private val usage: RoomUsageLedgerRepository = RoomUsageLedgerRepository(database),
) : RealTextExecutionRuntimeReceiptPort, RealTextExecutionUsageReservationPort {
    override fun reserve(plan: RealTextUsageReservationPlan, at: Instant): RealTextExecutionUsageReservationResult = safelyUsage {
        val binding = binding(plan) ?: return@safelyUsage rejectedUsage()
        val dao = database.compareConversationDao()
        val existing = dao.executionReceipt(plan.executionId.value)
        if (existing != null && !existing.matches(binding, plan)) return@safelyUsage rejectedUsage()
        val reservation = appendUsage(plan, at, UsageLedgerKind.BUDGET_RESERVATION, plan.replayToken)
        if (reservation is RealTextExecutionUsageReservationResult.Rejected) return@safelyUsage reservation
        if (existing == null) dao.insertExecutionReceipt(CompareBranchExecutionReceiptEntity(
            executionId = plan.executionId.value, sessionId = binding.sessionId, branchId = binding.branchId,
            invocationId = plan.invocationId.value, attemptId = plan.attemptId.value,
            requestFingerprint = plan.executionIdRequestFingerprint(binding),
            state = ConversationRealTextExecutionState.PREPARED.name,
            createdAtEpochMs = at.toEpochMilli(), updatedAtEpochMs = at.toEpochMilli(),
            terminalAtEpochMs = null, safeErrorCode = null,
        ))
        reservation
    }

    override fun release(
        plan: RealTextUsageReservationPlan,
        safeErrorCode: String,
        at: Instant,
    ): RealTextExecutionUsageReservationResult = safelyUsage {
        if (!safeErrorCode.matches(Regex("[A-Z][A-Z0-9_]*")) || binding(plan) == null) return@safelyUsage rejectedUsage()
        val token = "compare-release:${stableId("${plan.replayToken}|$safeErrorCode")}" 
        val entries = usage.entriesForExecution(plan.executionId)
        val reservation = entries.firstOrNull { it.kind == UsageLedgerKind.BUDGET_RESERVATION && it.replayToken == plan.replayToken }
            ?: return@safelyUsage rejectedUsage()
        if (!reservation.sameFactIgnoringOccurredAt(plan.toUsageEntry(UsageLedgerKind.BUDGET_RESERVATION, plan.replayToken, reservation.occurredAt))) {
            return@safelyUsage rejectedUsage()
        }
        val priorRelease = entries.firstOrNull { it.kind == UsageLedgerKind.BUDGET_RELEASE }
        if (priorRelease != null && priorRelease.replayToken != token) return@safelyUsage rejectedUsage()
        appendUsage(plan, at, UsageLedgerKind.BUDGET_RELEASE, token)
    }

    override fun start(plan: RealTextExecutionReadyPlan, at: Instant): RealTextExecutionRuntimeReceiptResult = safelyRuntime {
        val binding = binding(plan) ?: return@safelyRuntime rejectedRuntime()
        val dao = database.compareConversationDao()
        val current = dao.executionReceipt(plan.cancellation.executionId.value)
            ?.takeIf { it.matches(binding, plan.usageReservation) } ?: return@safelyRuntime rejectedRuntime()
        when (ConversationRealTextExecutionState.valueOf(current.state)) {
            ConversationRealTextExecutionState.RUNNING -> RealTextExecutionRuntimeReceiptResult.Replayed(current.toReceipt())
            ConversationRealTextExecutionState.PREPARED -> {
                if (dao.transitionExecutionReceipt(current.executionId, current.state, ConversationRealTextExecutionState.RUNNING.name,
                        at.toEpochMilli(), null, null) != 1) rejectedRuntime()
                else RealTextExecutionRuntimeReceiptResult.Applied(requireNotNull(dao.executionReceipt(current.executionId)).toReceipt())
            }
            else -> rejectedRuntime()
        }
    }

    override fun appendPartial(
        plan: RealTextExecutionReadyPlan,
        delta: String,
        at: Instant,
    ): RealTextExecutionRuntimeReceiptResult = safelyRuntime {
        val binding = binding(plan) ?: return@safelyRuntime rejectedRuntime()
        val current = database.compareConversationDao().executionReceipt(plan.cancellation.executionId.value)
            ?.takeIf { it.matches(binding, plan.usageReservation) } ?: return@safelyRuntime rejectedRuntime()
        when (ConversationRealTextExecutionState.valueOf(current.state)) {
            ConversationRealTextExecutionState.RUNNING -> RealTextExecutionRuntimeReceiptResult.Applied(current.toReceipt())
            ConversationRealTextExecutionState.SUCCEEDED,
            ConversationRealTextExecutionState.FAILED,
            ConversationRealTextExecutionState.CANCELLED -> RealTextExecutionRuntimeReceiptResult.Replayed(current.toReceipt())
            ConversationRealTextExecutionState.PREPARED -> rejectedRuntime()
        }
    }

    override fun finish(
        plan: RealTextExecutionReadyPlan,
        state: ConversationRealTextExecutionState,
        safeErrorCode: String?,
        at: Instant,
    ): RealTextExecutionRuntimeReceiptResult = safelyRuntime {
        if (state !in terminalStates || (state == ConversationRealTextExecutionState.SUCCEEDED) != (safeErrorCode == null)) {
            return@safelyRuntime rejectedRuntime()
        }
        val binding = binding(plan) ?: return@safelyRuntime rejectedRuntime()
        val dao = database.compareConversationDao()
        val current = dao.executionReceipt(plan.cancellation.executionId.value)
            ?.takeIf { it.matches(binding, plan.usageReservation) } ?: return@safelyRuntime rejectedRuntime()
        val currentState = ConversationRealTextExecutionState.valueOf(current.state)
        if (currentState in terminalStates) {
            return@safelyRuntime if (currentState == state && current.safeErrorCode == safeErrorCode) {
                RealTextExecutionRuntimeReceiptResult.Replayed(current.toReceipt())
            } else rejectedRuntime()
        }
        if (dao.transitionExecutionReceipt(current.executionId, current.state, state.name, at.toEpochMilli(), at.toEpochMilli(), safeErrorCode) != 1) {
            rejectedRuntime()
        } else RealTextExecutionRuntimeReceiptResult.Applied(requireNotNull(dao.executionReceipt(current.executionId)).toReceipt())
    }

    private fun binding(plan: RealTextUsageReservationPlan): CompareBranchBinding? {
        val dao = database.compareConversationDao()
        val branch = dao.branchForExecution(plan.executionId.value) ?: return null
        val session = dao.session(branch.sessionId) ?: return null
        return CompareBranchBinding(session.sessionId, branch.branchId, branch.invocationId, branch.attemptId, branch.requestFingerprint,
            session.conversationId, session.parentUserMessageId, branch.providerModelId, branch.providerHandle)
            .takeIf { it.matches(plan) }
    }

    private fun binding(plan: RealTextExecutionReadyPlan): CompareBranchBinding? {
        if (plan.presetId != null || plan.verifiedCompareDeployment == null) return null
        val binding = binding(plan.usageReservation) ?: return null
        return binding.takeIf {
            plan.cancellation.executionId == plan.usageReservation.executionId &&
                plan.cancellation.invocationId == plan.usageReservation.invocationId &&
                plan.cancellation.attemptId == plan.usageReservation.attemptId &&
                plan.cancellation.requestFingerprint == it.requestFingerprint &&
                plan.providerHandle.value == it.providerHandle && plan.modelId.value == it.providerModelId &&
                plan.verifiedCompareDeployment.provider.value == it.providerHandle
        }
    }

    private fun appendUsage(
        plan: RealTextUsageReservationPlan,
        at: Instant,
        kind: UsageLedgerKind,
        replayToken: String,
    ): RealTextExecutionUsageReservationResult {
        val candidate = plan.toUsageEntry(kind, replayToken, at)
        val existing = usage.entriesForExecution(plan.executionId).firstOrNull { it.replayToken == replayToken }
        if (existing != null) return if (existing.sameFactIgnoringOccurredAt(candidate)) {
            RealTextExecutionUsageReservationResult.Replayed
        } else rejectedUsage()
        return when (usage.append(candidate)) {
            is UsageLedgerAppendResult.Appended -> RealTextExecutionUsageReservationResult.Reserved
            is UsageLedgerAppendResult.Replayed -> RealTextExecutionUsageReservationResult.Replayed
            is UsageLedgerAppendResult.Conflict -> rejectedUsage()
        }
    }

    private data class CompareBranchBinding(
        val sessionId: String,
        val branchId: String,
        val invocationId: String,
        val attemptId: String,
        val requestFingerprint: String,
        val conversationId: String,
        val parentUserMessageId: String,
        val providerModelId: String,
        val providerHandle: String,
    ) {
        fun matches(plan: RealTextUsageReservationPlan): Boolean =
            plan.invocationId.value == invocationId && plan.attemptId.value == attemptId &&
                plan.conversationId.value == conversationId && plan.branchLeafMessageId.value == parentUserMessageId &&
                plan.requestedModelId.value == providerModelId
    }

    private fun CompareBranchExecutionReceiptEntity.matches(binding: CompareBranchBinding, plan: RealTextUsageReservationPlan): Boolean =
        sessionId == binding.sessionId && branchId == binding.branchId && invocationId == binding.invocationId &&
            attemptId == binding.attemptId && requestFingerprint == binding.requestFingerprint && binding.matches(plan)

    private fun RealTextUsageReservationPlan.executionIdRequestFingerprint(binding: CompareBranchBinding): String = binding.requestFingerprint

    private fun CompareBranchExecutionReceiptEntity.toReceipt() = RealTextExecutionRuntimeReceipt(
        executionId = com.nanzhufeng.ai.domain.ConversationRealTextExecutionId(executionId),
        invocationId = com.nanzhufeng.ai.domain.InvocationId(invocationId),
        attemptId = com.nanzhufeng.ai.domain.ProviderAttemptId(attemptId),
        requestFingerprint = requestFingerprint,
        state = ConversationRealTextExecutionState.valueOf(state),
        safeErrorCode = safeErrorCode,
    )

    private fun RealTextUsageReservationPlan.toUsageEntry(kind: UsageLedgerKind, replayToken: String, at: Instant) = UsageLedgerEntry(
        entryId = UsageLedgerEntryId("compare:${kind.name.lowercase()}:${stableId(replayToken)}"), replayToken = replayToken,
        executionId = executionId, conversationId = conversationId, branchLeafMessageId = branchLeafMessageId,
        invocationId = invocationId, attemptId = attemptId, kind = kind, factGrade = UsageFactGrade.ESTIMATED,
        requestedModelId = requestedModelId.value, actualModelId = null,
        inputTokens = if (kind == UsageLedgerKind.BUDGET_RESERVATION) estimatedInputTokens else null,
        outputTokens = if (kind == UsageLedgerKind.BUDGET_RESERVATION) reservedOutputTokens else null,
        cachedInputTokens = null, chargeMicros = null, budgetMicros = budgetMicros, adjustmentMicros = null,
        currencyCode = currencyCode, reconciliationFingerprint = reconciliationFingerprint, reconcilesEntryId = null,
        source = source, occurredAt = at,
    )

    private fun UsageLedgerEntry.sameFactIgnoringOccurredAt(other: UsageLedgerEntry): Boolean = copy(occurredAt = other.occurredAt) == other
    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun safelyUsage(action: () -> RealTextExecutionUsageReservationResult): RealTextExecutionUsageReservationResult =
        runCatching { database.runInTransaction(Callable { action() }) }.getOrElse { rejectedUsage() }
    private fun safelyRuntime(action: () -> RealTextExecutionRuntimeReceiptResult): RealTextExecutionRuntimeReceiptResult =
        runCatching { database.runInTransaction(Callable { action() }) }.getOrElse { rejectedRuntime() }
    private fun rejectedUsage() = RealTextExecutionUsageReservationResult.Rejected(RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_USAGE_RESERVATION_REJECTED)
    private fun rejectedRuntime() = RealTextExecutionRuntimeReceiptResult.Rejected(RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_RUNTIME_RECEIPT_REJECTED)

    private companion object {
        val terminalStates = setOf(ConversationRealTextExecutionState.SUCCEEDED, ConversationRealTextExecutionState.FAILED, ConversationRealTextExecutionState.CANCELLED)
    }
}
