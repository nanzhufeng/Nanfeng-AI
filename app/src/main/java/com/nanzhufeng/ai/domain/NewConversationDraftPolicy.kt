package com.nanzhufeng.ai.domain

import java.time.Instant

/** New-conversation text only. Reading a draft never renews its lifetime. */
object NewConversationDraftPolicy {
    const val RETENTION_MILLIS = 60L * 60L * 1_000L

    fun isUnsentNew(snapshot: ConversationSnapshot): Boolean =
        snapshot.nodes.isEmpty() && snapshot.conversation.pinnedAt == null &&
            snapshot.conversation.archivedAt == null && snapshot.conversation.deletedAt == null &&
            (snapshot.conversation.autoTitlePending || snapshot.conversation.title == ConversationAutoTitle.NEW_CONVERSATION_TITLE)

    fun isExpired(snapshot: ConversationSnapshot, now: Instant): Boolean =
        isUnsentNew(snapshot) && snapshot.draft.text.isNotEmpty() &&
            !now.isBefore(snapshot.draft.updatedAt.plusMillis(RETENTION_MILLIS))
}
