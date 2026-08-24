package com.nanzhufeng.ai.domain

/**
 * One local context broker for every ordinary chat model.
 *
 * It has full read access to the user's locally stored, non-deleted conversations, ACTIVE
 * memories and ACTIVE knowledge.  Egress remains deliberately small: it ranks locally, sends
 * only selected text excerpts, and never reads attachment bytes, credentials, runtime logs or
 * sibling branches.  Model choice changes the provider, never this access boundary.
 */
class LocalContextBroker(
    private val index: LocalContextIndex,
) {
    data class Package(
        val messages: List<Message>,
        val selectedKnowledgeCount: Int,
        val selectedHistoryCount: Int,
        val selectedMemoryCount: Int,
        val availableKnowledgeItemCount: Int,
        val selectedSources: List<ContextSelectionSource>,
        val effectiveBudget: ContextBudget,
        val status: AssemblyStatus,
    )

    enum class AssemblyStatus { READY, INPUT_TOO_LARGE, INDEX_UNAVAILABLE }

    data class Message(val role: MessageRole, val text: String, val source: ContextSelectionSource? = null)

    fun activeKnowledgeCount(): Int = index.activeKnowledgeCount()

    fun assemble(
        current: ConversationSnapshot,
        userMessage: String,
        budget: ContextBudget = ContextBudget.safeDefault(),
        attachmentInputTokens: Int = 0,
        fixedInstructionTokens: Int = 0,
    ): Package {
        val tokenizerId = budget.tokenizerId
        val effectiveBudget = budget.reserveFixedInput(
            ModelTokenEstimators.estimate(tokenizerId, userMessage) + attachmentInputTokens + fixedInstructionTokens,
        ) ?: return Package(
            messages = emptyList(), selectedKnowledgeCount = 0, selectedHistoryCount = 0,
            selectedMemoryCount = 0, availableKnowledgeItemCount = index.activeKnowledgeCount(),
            selectedSources = emptyList(), effectiveBudget = budget,
            status = AssemblyStatus.INPUT_TOO_LARGE,
        )
        val queryTerms = terms(userMessage)
        val scope = ContextRetrievalScope(current.conversation.id, current.conversation.projectId)
        val allCurrentMessages = MessageTree(current.conversation, current.nodes).contextPath()
            .mapNotNull { node -> node.text()?.let { Message(node.role, it) } }
            .takeLast(CURRENT_PATH_MESSAGE_LIMIT)
        val currentUserIndex = allCurrentMessages.indexOfLast { it.role == MessageRole.USER && it.text == userMessage }
        val currentPath = allCurrentMessages.filterIndexed { index, _ -> index != currentUserIndex }

        val memory = index.searchActiveMemories(queryTerms, scope, MEMORY_LIMIT).map(::candidate)
        val library = index.searchActiveKnowledge(queryTerms, scope, KNOWLEDGE_LIMIT).map(::candidate)
        val history = index.searchActiveHistory(queryTerms, scope, HISTORY_LIMIT).map(::candidate)

        val sourceMessages = (memory + library + history)
            .distinctBy { it.title.normalizedDedupKey() + "\u0000" + it.body.normalizedDedupKey() }
            .sortedWith(compareBy<Candidate> { it.score }.thenByDescending { it.updatedAt }.thenBy { it.title })
            .map { candidate ->
                val text = "[本地${candidate.kind}：${candidate.title}]\n${candidate.body}"
                Message(MessageRole.SYSTEM, text, ContextSelectionSource(candidate.kind, candidate.id, candidate.title, ModelTokenEstimators.estimate(tokenizerId, text)))
            }
            .fitToTokenBudget(effectiveBudget.retrievalTokens, tokenizerId, prioritizeLatest = false)
        val dialogMessages = currentPath.fitToTokenBudget(effectiveBudget.historyTokens, tokenizerId)
        val mandatoryUser = userMessage.trim().takeIf { it.isNotBlank() }?.let { Message(MessageRole.USER, it) }
        val messages = ((sourceMessages + dialogMessages).fitToTokenBudget(effectiveBudget.availableContextTokens, tokenizerId) + listOfNotNull(mandatoryUser))
        return Package(
            messages = messages,
            selectedKnowledgeCount = messages.count { it.source?.kind == "知识库" },
            selectedHistoryCount = messages.count { it.source?.kind == "历史对话" },
            selectedMemoryCount = messages.count { it.source?.kind == "记忆" },
            availableKnowledgeItemCount = index.activeKnowledgeCount(),
            selectedSources = messages.mapNotNull(Message::source),
            effectiveBudget = effectiveBudget,
            status = if (index.status() == LocalContextIndexStatus.AVAILABLE) AssemblyStatus.READY else AssemblyStatus.INDEX_UNAVAILABLE,
        )
    }

    private data class Candidate(val id: String, val kind: String, val title: String, val body: String, val updatedAt: Long, val score: Double)

    private fun candidate(hit: LocalContextIndexHit) = Candidate(hit.stableId, hit.kind, hit.title, hit.body, hit.updatedAtEpochMs, hit.rank)

    /** Adds complete entries only. No selected Memory/Knowledge/history record is character-cut. */
    private fun List<Message>.fitToTokenBudget(budget: Int, tokenizerId: String, prioritizeLatest: Boolean = true): List<Message> {
        var remaining = budget.coerceAtLeast(0)
        val candidates = if (prioritizeLatest) asReversed() else this
        val selected = candidates.mapNotNull { message ->
            val cost = ModelTokenEstimators.estimate(tokenizerId, message.text)
            if (cost > remaining) null else message.takeIf { it.text.isNotBlank() }?.also { remaining -= cost }
        }
        return if (prioritizeLatest) selected.asReversed() else selected
    }

    private fun MessageNode.text(): String? = content.filterIsInstance<ContentBlock.Text>()
        .joinToString("\n") { it.text }.trim().takeIf { it.isNotBlank() }

    private fun terms(text: String): Set<String> = text.lowercase()
        .split(Regex("[^\\p{L}\\p{N}_-]+"))
        .map(String::trim)
        .filter { it.length >= 2 }
        .take(24)
        .toSet()

    private fun String.normalizedDedupKey(): String = lowercase().replace(Regex("\\s+"), " ").trim()

    private companion object {
        const val CURRENT_PATH_MESSAGE_LIMIT = 24
        const val MEMORY_LIMIT = 6
        const val KNOWLEDGE_LIMIT = 8
        const val HISTORY_LIMIT = 8
    }
}
