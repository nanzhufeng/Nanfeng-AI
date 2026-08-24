package com.nanzhufeng.ai.domain

/**
 * Local-only retrieval index. It returns bounded metadata/body rows already ranked by SQLite;
 * callers never enumerate the full Memory, Knowledge or conversation corpus during a send.
 */
interface LocalContextIndex {
    fun searchActiveMemories(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int): List<LocalContextIndexHit>
    fun searchActiveKnowledge(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int): List<LocalContextIndexHit>
    fun searchActiveHistory(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int): List<LocalContextIndexHit>
    fun activeKnowledgeCount(): Int
    fun status(): LocalContextIndexStatus
}

data class ContextRetrievalScope(
    val conversationId: ConversationId,
    val projectId: String?,
)

/** Deliberately content-free: this only explains whether local retrieval was available. */
enum class LocalContextIndexStatus { AVAILABLE, UNAVAILABLE }

data class LocalContextIndexHit(
    val stableId: String,
    val kind: String,
    val title: String,
    val body: String,
    val updatedAtEpochMs: Long,
    val rank: Double,
)
