package com.nanzhufeng.ai.domain

/**
 * A local override for one ordinary conversation. A null value deliberately inherits the
 * global setting, while an explicit true/false never changes another conversation or the
 * settings-page default.
 */
data class ConversationWebSearchOverride(
    val conversationId: ConversationId,
    val revision: Long,
    val enabled: Boolean? = null,
) {
    init {
        require(revision >= 0) { "会话联网设置版本必须非负。" }
    }
}

interface ConversationWebSearchOverrideStore {
    fun read(conversationId: ConversationId): ConversationWebSearchOverride
    fun save(value: ConversationWebSearchOverride): Boolean
}

sealed interface ConversationWebSearchOverrideMutationResult {
    data class Applied(val revision: Long) : ConversationWebSearchOverrideMutationResult
    data object Conflict : ConversationWebSearchOverrideMutationResult
    data object PersistenceFailed : ConversationWebSearchOverrideMutationResult
}

/** One owner for the Composer's current-conversation web-search state and normal-send routing. */
class ConversationWebSearchOverrideOwner(private val store: ConversationWebSearchOverrideStore) {
    fun read(conversationId: ConversationId): ConversationWebSearchOverride = store.read(conversationId)

    fun effectiveEnabled(conversationId: ConversationId, globalEnabled: Boolean): Boolean =
        read(conversationId).enabled ?: globalEnabled

    fun setEnabled(
        conversationId: ConversationId,
        enabled: Boolean,
        expectedRevision: Long,
    ): ConversationWebSearchOverrideMutationResult {
        val current = read(conversationId)
        if (current.revision != expectedRevision) return ConversationWebSearchOverrideMutationResult.Conflict
        val next = current.copy(revision = current.revision + 1, enabled = enabled)
        return if (store.save(next)) {
            ConversationWebSearchOverrideMutationResult.Applied(next.revision)
        } else {
            ConversationWebSearchOverrideMutationResult.PersistenceFailed
        }
    }
}
