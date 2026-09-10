package com.nanzhufeng.ai.domain

/** Local navigation metadata only; never replays generation or chooses from drawer order. */
object ConversationAppEntryPolicy {
    const val RETENTION_MILLIS = 15L * 60L * 1_000L

    fun retainedId(
        savedId: String?, lastBackgroundAt: Long, now: Long,
        hadRunningGeneration: Boolean = false,
    ): ConversationId? {
        if (savedId.isNullOrBlank() || lastBackgroundAt <= 0L || now < lastBackgroundAt) return null
        if (!hadRunningGeneration && now - lastBackgroundAt >= RETENTION_MILLIS) return null
        return ConversationId(savedId)
    }

    fun isReusableBlank(snapshot: ConversationSnapshot): Boolean =
        snapshot.conversation.surface == ConversationSurface.CHAT &&
            snapshot.conversation.autoTitlePending && snapshot.conversation.pinnedAt == null &&
            restorableConversation(snapshot.conversation) != null && snapshot.nodes.isEmpty() &&
            snapshot.draft.text.isBlank() && snapshot.draft.attachments.isEmpty()

    fun restorableConversation(conversation: Conversation?): Conversation? =
        conversation?.takeIf { it.deletedAt == null && it.archivedAt == null }
}
