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
    /** Memory and Knowledge retrieval each have an explicit user-owned switch; current-path history remains available. */
    data class RetrievalPolicy(
        val includeRelevantMemory: Boolean = true,
        val includeRelevantKnowledge: Boolean = true,
    )

    data class Package(
        val messages: List<Message>,
        val selectedKnowledgeCount: Int,
        val selectedHistoryCount: Int,
        val selectedMemoryCount: Int,
        val availableKnowledgeItemCount: Int,
        val selectedSources: List<ContextSelectionSource>,
        val effectiveBudget: ContextBudget,
        val status: AssemblyStatus,
        val includedCurrentPathMessageCount: Int = 0,
        val investmentDecisionContext: Boolean = false,
    )

    enum class AssemblyStatus { READY, INPUT_TOO_LARGE, INDEX_UNAVAILABLE }

    data class Message(
        val role: MessageRole,
        val text: String,
        val source: ContextSelectionSource? = null,
        val isCurrentPathMessage: Boolean = false,
        val messageId: MessageNodeId? = null,
    )

    fun activeKnowledgeCount(): Int = index.activeKnowledgeCount()

    fun assemble(
        current: ConversationSnapshot,
        userMessage: String,
        budget: ContextBudget = ContextBudget.safeDefault(),
        attachmentInputTokens: Int = 0,
        fixedInstructionTokens: Int = 0,
        policy: RetrievalPolicy = RetrievalPolicy(),
    ): Package {
        val investmentDecisionContext = InvestmentContextPolicy.matches(userMessage)
        val tokenizerId = budget.tokenizerId
        val effectiveBudget = budget.reserveFixedInput(
            ModelTokenEstimators.estimate(tokenizerId, userMessage) + attachmentInputTokens + fixedInstructionTokens,
        ) ?: return Package(
            messages = emptyList(), selectedKnowledgeCount = 0, selectedHistoryCount = 0,
            selectedMemoryCount = 0, availableKnowledgeItemCount = index.activeKnowledgeCount(),
            selectedSources = emptyList(), effectiveBudget = budget,
            status = AssemblyStatus.INPUT_TOO_LARGE,
        )
        val queryTerms = terms(userMessage) + InvestmentContextPolicy.additionalTerms(investmentDecisionContext)
        val scope = ContextRetrievalScope(current.conversation.id, current.conversation.projectId)
        val allCurrentMessages = MessageTree(current.conversation, current.nodes).contextPath()
            .mapNotNull { node -> node.text()?.let { Message(node.role, it, isCurrentPathMessage = true, messageId = node.id) } }
            .takeLast(CURRENT_PATH_MESSAGE_LIMIT)
        val currentUserIndex = allCurrentMessages.indexOfLast { it.role == MessageRole.USER && it.text == userMessage }
        val currentPath = allCurrentMessages.filterIndexed { index, _ -> index != currentUserIndex }

        val memory = if (policy.includeRelevantMemory) {
            index.searchActiveMemories(queryTerms, scope, MEMORY_LIMIT).map(::candidate)
        } else {
            emptyList()
        }
        val library = if (policy.includeRelevantKnowledge) {
            index.searchActiveKnowledge(queryTerms, scope, KNOWLEDGE_LIMIT).map(::candidate)
        } else {
            emptyList()
        }
        val history = index.searchActiveHistory(queryTerms, scope, HISTORY_LIMIT).map(::candidate)

        // Confirmed secondary knowledge is the compact, durable layer over the full history.
        // It gets the first context budget; raw historical turns are a fallback when that layer
        // has no relevant entry or leaves room.  This avoids repeatedly shipping whole old chats.
        val sourceMessages = (library + memory + history)
            .distinctBy { it.title.normalizedDedupKey() + "\u0000" + it.body.normalizedDedupKey() }
            .sortedWith(compareBy<Candidate> { it.sourcePriority }.thenBy { it.score }.thenByDescending { it.updatedAt }.thenBy { it.title })
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
            includedCurrentPathMessageCount = messages.count(Message::isCurrentPathMessage),
            investmentDecisionContext = investmentDecisionContext,
        )
    }

    private data class Candidate(
        val id: String,
        val kind: String,
        val title: String,
        val body: String,
        val updatedAt: Long,
        val score: Double,
        val sourcePriority: Int,
    )

    private fun candidate(hit: LocalContextIndexHit) = Candidate(
        hit.stableId,
        hit.kind,
        hit.title,
        hit.body,
        hit.updatedAtEpochMs,
        hit.rank,
        when (hit.kind) {
            "知识库" -> 0
            "记忆" -> 1
            else -> 2
        },
    )

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

private object InvestmentContextPolicy {
    private val topicMarkers = setOf(
        "投资", "资产配置", "仓位", "估值", "买入", "卖出", "定投", "基金", "etf", "股票", "a股",
        "沪深", "中证", "a500", "pe", "pb", "股息", "回撤", "宽基",
    )

    fun matches(text: String): Boolean = text.lowercase().let { normalized -> topicMarkers.any(normalized::contains) }
    fun additionalTerms(isInvestmentDecisionContext: Boolean): Set<String> =
        if (isInvestmentDecisionContext) setOf("投资", "投资体系", "资产配置", "估值", "仓位", "风险") else emptySet()
}
