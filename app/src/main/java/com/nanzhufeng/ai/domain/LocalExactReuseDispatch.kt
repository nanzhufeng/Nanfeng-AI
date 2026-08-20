package com.nanzhufeng.ai.domain

/**
 * P6-L3's content-free routing owner. It chooses an existing local response reference or a
 * future normal-dispatch continuation; it owns neither message rendering nor execution.
 */
class LocalExactReuseDispatchOwner(
    private val entries: LocalExactReuseEntryStore,
) {
    fun dispatch(
        key: LocalExactReuseKey?,
        isTemporaryConversation: Boolean,
        nowEpochMs: Long,
        port: LocalExactReuseDispatchPort,
    ): LocalExactReuseDispatchResult {
        val decision = entries.resolve(key, isTemporaryConversation, nowEpochMs)
        val responseMessageId = decision.responseMessageId
        if (decision.outcome == LocalExactReuseOutcome.LOCAL_EXACT_HIT && responseMessageId != null) {
            port.reuseExistingLocalResponse(responseMessageId)
            return LocalExactReuseDispatchResult.Reused(responseMessageId)
        }

        val safeDecision = if (decision.outcome == LocalExactReuseOutcome.LOCAL_EXACT_HIT) {
            LocalExactReuseDecision(LocalExactReuseOutcome.UNKNOWN, reason = "本地精确复用记录缺少消息引用")
        } else {
            decision
        }
        port.continueWithoutReuse(safeDecision)
        return LocalExactReuseDispatchResult.Continued(safeDecision)
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

sealed interface LocalExactReuseDispatchResult {
    data class Reused(val responseMessageId: String) : LocalExactReuseDispatchResult
    data class Continued(val decision: LocalExactReuseDecision) : LocalExactReuseDispatchResult
}
