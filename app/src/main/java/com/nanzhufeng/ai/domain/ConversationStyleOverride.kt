package com.nanzhufeng.ai.domain

/**
 * Content-free style override owned by one ordinary conversation. A null style inherits the
 * global setting; an explicit value affects only subsequent answers in this conversation.
 */
data class ConversationStyleOverride(
    val conversationId: ConversationId,
    val revision: Long,
    val style: ConversationStyle? = null,
) {
    init {
        require(revision >= 0) { "会话风格设置版本必须非负。" }
    }
}

interface ConversationStyleOverrideStore {
    fun read(conversationId: ConversationId): ConversationStyleOverride
    fun save(value: ConversationStyleOverride): Boolean
}

sealed interface ConversationStyleOverrideMutationResult {
    data class Applied(val revision: Long) : ConversationStyleOverrideMutationResult
    data object Conflict : ConversationStyleOverrideMutationResult
    data object PersistenceFailed : ConversationStyleOverrideMutationResult
}

class ConversationStyleOverrideOwner(private val store: ConversationStyleOverrideStore) {
    fun read(conversationId: ConversationId): ConversationStyleOverride = store.read(conversationId)

    fun effectiveStyle(conversationId: ConversationId, globalStyle: ConversationStyle): ConversationStyle =
        read(conversationId).style ?: globalStyle

    fun setStyle(
        conversationId: ConversationId,
        style: ConversationStyle,
        expectedRevision: Long,
    ): ConversationStyleOverrideMutationResult {
        val current = read(conversationId)
        if (current.revision != expectedRevision) return ConversationStyleOverrideMutationResult.Conflict
        val next = current.copy(revision = current.revision + 1, style = style)
        return if (store.save(next)) {
            ConversationStyleOverrideMutationResult.Applied(next.revision)
        } else {
            ConversationStyleOverrideMutationResult.PersistenceFailed
        }
    }
}
