package com.nanzhufeng.ai.domain

/**
 * A one-shot process-memory-only capability. It is created only by the Compare application owner
 * and is invalid immediately after [take]. It must never be persisted, logged, returned, or
 * retained by an implementation after batch acceptance returns.
 */
class CompareEphemeralTextLease internal constructor(
    private var input: ProviderTransportEphemeralTextInput?,
) {
    fun take(): ProviderTransportEphemeralTextInput? = input.also { input = null }
    internal fun release() { input = null }
}

/** Content-free Schema 32 identities for exactly one future branch execution. */
data class CompareBatchBranchExecutionHandle(
    val branchId: CompareBranchId,
    val conversationId: ConversationId,
    val parentUserMessageId: MessageNodeId,
    val assistantMessageId: MessageNodeId,
    val invocationId: InvocationId,
    val attemptId: ProviderAttemptId,
    val executionId: ConversationRealTextExecutionId,
    val cancellationId: CompareBranchCancellationId,
    val usageReservationReplayToken: String,
) {
    init {
        require(usageReservationReplayToken.isNotBlank())
        require(listOf(assistantMessageId.value, invocationId.value, attemptId.value, executionId.value, cancellationId.value).distinct().size == 5)
    }
}

/** The two grants must be accepted as one batch; a branch-by-branch acceptance API is forbidden. */
data class CompareBatchDispatch(
    val sessionId: CompareConversationSessionId,
    val canonicalContext: CanonicalContextSnapshotRef,
    val grants: List<CompareBranchExecutionGrant>,
    val branchExecutionHandles: List<CompareBatchBranchExecutionHandle>,
    /** Confirmed scope metadata; retained only during this call, never persisted with the lease. */
    val outputTokenLimit: Long,
    val textLease: CompareEphemeralTextLease,
) {
    init {
        require(grants.size == CompareExecutionSummaryConfirmation.COMPARE_MVP_TARGET_COUNT)
        require(grants.map(CompareBranchExecutionGrant::branchId).toSet().size == grants.size)
        require(grants.all { it.contextHash == canonicalContext.contentHash })
        require(branchExecutionHandles.map(CompareBatchBranchExecutionHandle::branchId).toSet() == grants.map(CompareBranchExecutionGrant::branchId).toSet())
        require(outputTokenLimit in 1..16_384)
    }
}

sealed interface CompareBatchDispatchAcceptance {
    data object Accepted : CompareBatchDispatchAcceptance
    data object Rejected : CompareBatchDispatchAcceptance
}

/**
 * A future execution adapter may implement this port only after its own Provider/credential/egress
 * authorization increment. This MM-O4-D boundary deliberately has no Provider, Key, HTTP, UI, or
 * transport implementation.
 */
fun interface CompareBatchDispatchPort {
    fun accept(batch: CompareBatchDispatch): CompareBatchDispatchAcceptance
}

object FailClosedCompareBatchDispatchPort : CompareBatchDispatchPort {
    override fun accept(batch: CompareBatchDispatch): CompareBatchDispatchAcceptance =
        CompareBatchDispatchAcceptance.Rejected
}
