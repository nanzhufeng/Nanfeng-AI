package com.nanzhufeng.ai.domain

import java.time.Instant

/** New-conversation text only. Reading a draft never renews its lifetime. */
object NewConversationDraftPolicy {
    const val RETENTION_MILLIS = 60L * 60L * 1_000L

    fun isCandidate(conversation: Conversation): Boolean =
        conversation.pinnedAt == null && conversation.archivedAt == null && conversation.deletedAt == null &&
            (conversation.autoTitlePending || conversation.title == ConversationAutoTitle.NEW_CONVERSATION_TITLE)

    fun isUnsentNew(snapshot: ConversationSnapshot): Boolean =
        isCandidate(snapshot.conversation) && snapshot.nodes.isEmpty()

    fun isExpired(snapshot: ConversationSnapshot, now: Instant): Boolean =
        isUnsentNew(snapshot) && snapshot.draft.text.isNotEmpty() &&
            !now.isBefore(snapshot.draft.updatedAt.plusMillis(RETENTION_MILLIS))
}
