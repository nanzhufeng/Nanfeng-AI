package com.nanzhufeng.ai.domain

import java.security.MessageDigest

/** Durable area identity is independent of project membership and of the currently visible page. */
object ConversationDataArea {
    fun databaseName(surface: ConversationSurface): String = when (surface) {
        ConversationSurface.CHAT -> "nanfeng-ai.db"
        ConversationSurface.WORK -> "nanfeng-ai-work.db"
    }
    fun cloudDocumentId(surface: ConversationSurface, conversationId: String): String {
        val identity = if (surface == ConversationSurface.WORK) "WORK:$conversationId" else conversationId
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "conversation-${digest.take(40)}"
    }
    fun cloudListDocumentId(surface: ConversationSurface): String = when (surface) {
        ConversationSurface.CHAT -> "cloud-conversation-list-v1"
        ConversationSurface.WORK -> "cloud-work-conversation-list-v1"
    }
}
