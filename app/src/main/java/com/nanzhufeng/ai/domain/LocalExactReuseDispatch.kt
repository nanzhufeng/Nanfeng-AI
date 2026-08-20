package com.nanzhufeng.ai.domain

/**
 * P6-L4's content-free routing owner. It only reuses a response reference after the future
 * message owner proves that the reference is current and belongs to the same scope.
 */
class LocalExactReuseDispatchOwner(
    private val entries: LocalExactReuseEntryStore,
    private val referenceVerifier: LocalExactReuseResponseReferenceVerifier,
) {
    fun dispatch(
        key: LocalExactReuseKey?,
        isTemporaryConversation: Boolean,
        nowEpochMs: Long,
        port: LocalExactReuseDispatchPort,
    ): LocalExactReuseDispatchResult {
        val decision = entries.resolve(key, isTemporaryConversation, nowEpochMs)
        val responseMessageId = decision.responseMessageId
        if (decision.outcome == LocalExactReuseOutcome.LOCAL_EXACT_HIT && responseMessageId != null && key != null) {
            if (referenceVerifier.verify(key.scopeId, responseMessageId) == LocalExactReuseResponseReferenceState.VALID) {
                port.reuseExistingLocalResponse(responseMessageId)
                return LocalExactReuseDispatchResult.Reused(responseMessageId)
            }
            return continueWithUnknown(port, "本地精确复用消息引用不可用")
        }

        val safeDecision = if (decision.outcome == LocalExactReuseOutcome.LOCAL_EXACT_HIT) {
            LocalExactReuseDecision(LocalExactReuseOutcome.UNKNOWN, reason = "本地精确复用记录缺少消息引用")
        } else {
            decision
        }
        port.continueWithoutReuse(safeDecision)
        return LocalExactReuseDispatchResult.Continued(safeDecision)
    }

    private fun continueWithUnknown(port: LocalExactReuseDispatchPort, reason: String): LocalExactReuseDispatchResult.Continued {
        val decision = LocalExactReuseDecision(LocalExactReuseOutcome.UNKNOWN, reason = reason)
        port.continueWithoutReuse(decision)
        return LocalExactReuseDispatchResult.Continued(decision)
    }
}

/**
 * A future explicit owner may adapt the continuation to normal execution. This P6-L3 owner
 * remains unregistered and carries no Prompt, response body, credential, transport or ledger.
 */
interface LocalExactReuseDispatchPort {
    fun reuseExistingLocalResponse(responseMessageId: String)
    fun continueWithoutReuse(decision: LocalExactReuseDecision)
}

/** Content-free authority check only; it must not project or copy message text. */
interface LocalExactReuseResponseReferenceVerifier {
    fun verify(scopeId: String, responseMessageId: String): LocalExactReuseResponseReferenceState
}

enum class LocalExactReuseResponseReferenceState { VALID, MISSING, SCOPE_MISMATCH, UNREADABLE }

sealed interface LocalExactReuseDispatchResult {
    data class Reused(val responseMessageId: String) : LocalExactReuseDispatchResult
    data class Continued(val decision: LocalExactReuseDecision) : LocalExactReuseDispatchResult
}
