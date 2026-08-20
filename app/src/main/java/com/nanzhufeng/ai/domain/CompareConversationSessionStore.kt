package com.nanzhufeng.ai.domain

import java.time.Instant

@JvmInline
value class CompareDispatchIntentId(val value: String) {
    init { require(value.isNotBlank()) }
}

/**
 * Content-free persistence state for the single all-or-nothing Compare hand-off.
 * A process restart has no text lease, so every non-accepted state requires a fresh text and a
 * new confirmation; the stored hash is verification material, never recovery material.
 */
enum class CompareDispatchRecoveryState {
    RETRY_REQUIRED,
    PENDING_ACCEPTANCE_RETRY_REQUIRED,
    REJECTED_RETRY_REQUIRED,
    ACCEPTED,
}

data class CompareDispatchIntent(
    val id: CompareDispatchIntentId,
    val sessionId: CompareConversationSessionId,
    val confirmationId: String,
    val requestFingerprint: String,
    val context: CanonicalContextSnapshotRef,
    val branchGrantIds: List<String>,
) {
    init {
        require(confirmationId.isNotBlank())
        require(requestFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(branchGrantIds.size == CompareExecutionSummaryConfirmation.COMPARE_MVP_TARGET_COUNT)
        require(branchGrantIds.toSet().size == branchGrantIds.size)
    }
}

/**
 * Durable MM-O4-C boundary.  It stores Compare orchestration metadata and references the
 * existing MessageTree; it does not own a parallel message tree, transport, credential or UI.
 */
data class CompareBranchRuntimeReference(
    val branchId: CompareBranchId,
    val executionId: ConversationRealTextExecutionId,
    val usageReservationReplayToken: String,
    val nextExpectedSequence: Long,
    val lastCheckpointSequence: Long?,
    val state: CompareBranchReservationState,
    val safeErrorCode: String?,
    val updatedAt: Instant,
)

data class CompareConversationSessionRead(
    val plan: CompareConversationSessionPlan,
    val runtimeReferences: List<CompareBranchRuntimeReference>,
    val dispatchRecoveryState: CompareDispatchRecoveryState = CompareDispatchRecoveryState.RETRY_REQUIRED,
) {
    init { require(runtimeReferences.map(CompareBranchRuntimeReference::branchId).toSet() == plan.branches.map(CompareConversationBranchPlan::branchId).toSet()) }
}

sealed interface CompareSessionStoreResult<out T> {
    data class Stored<T>(val value: T) : CompareSessionStoreResult<T>
    data class Replayed<T>(val value: T) : CompareSessionStoreResult<T>
    data class Rejected(val reason: CompareConversationSessionRejection) : CompareSessionStoreResult<Nothing>
}

/**
 * The public persistence owner for Compare session plans and their independent branch runtime
 * references.  P3-I receipts and the Usage Ledger retain their existing fact ownership; the
 * stored execution/reservation identifiers here are only future references, not fabricated facts.
 */
interface CompareConversationSessionStore {
    fun persist(plan: CompareConversationSessionPlan): CompareSessionStoreResult<CompareConversationSessionRead>
    fun read(sessionId: CompareConversationSessionId): CompareConversationSessionRead?
    fun recordTerminal(intent: CompareBranchTerminalIntent, at: Instant): CompareSessionStoreResult<CompareConversationSessionRead>
    fun recordDispatchIntent(intent: CompareDispatchIntent, at: Instant): CompareSessionStoreResult<CompareConversationSessionRead>
    fun recordDispatchAccepted(intent: CompareDispatchIntent, at: Instant): CompareSessionStoreResult<CompareConversationSessionRead>
    fun recordDispatchRejected(intent: CompareDispatchIntent, at: Instant): CompareSessionStoreResult<CompareConversationSessionRead>
    fun storeFollowUp(intent: CompareBranchFollowUpIntent): CompareSessionStoreResult<CompareBranchFollowUpPlan>
    fun storeAdoption(intent: CompareBranchAdoptionIntent): CompareSessionStoreResult<CompareBranchAdoptionPlan>
    fun storeSynthesis(intent: CompareSynthesisIntent): CompareSessionStoreResult<CompareSynthesisPlan>
}
