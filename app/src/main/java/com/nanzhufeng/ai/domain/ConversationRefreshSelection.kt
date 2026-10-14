package com.nanzhufeng.ai.domain

/** Resolves the explicitly requested transcript independently of drawer ordering. */
object ConversationRefreshSelection {
    fun resolve(repository: ConversationRepository, requested: ConversationId?, surface: ConversationSurface): ConversationSnapshot? {
        if (requested == null) return null
        return repository.findById(requested)?.takeIf { it.conversation.surface == surface }
    }
}
