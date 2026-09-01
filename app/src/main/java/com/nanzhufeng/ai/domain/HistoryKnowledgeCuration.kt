package com.nanzhufeng.ai.domain

/**
 * A user-confirmed source slice from the local conversation archive.  It is deliberately
 * text-only: attachments, tool payloads, provider requests and sibling branches stay local.
 */
data class HistoryKnowledgeCurationSource(
    val conversationId: ConversationId,
    val conversationTitle: String,
    val transcript: String,
    val messageCount: Int,
    /** Automatic runs use a smaller bounded slice; the manual path remains full-fidelity. */
    val automatic: Boolean = false,
) {
    init {
        require(conversationTitle.isNotBlank())
        require(transcript.isNotBlank())
        require(messageCount > 0)
    }
}

/** A model proposal, not a saved Knowledge item. The user must review it before any write. */
data class HistoryKnowledgeCurationDraft(
    val title: String,
    val body: String,
    val tags: Set<String>,
    val providerId: ProviderId,
    val modelId: String,
    /** The model must be decisive before an opted-in automatic run can persist a candidate. */
    val confidence: Double = 1.0,
)

sealed interface HistoryKnowledgeCurationResult {
    data class Draft(val value: HistoryKnowledgeCurationDraft) : HistoryKnowledgeCurationResult
    /** The source has no durable preference, decision, project fact or other reusable value. */
    data object NotEligible : HistoryKnowledgeCurationResult
    data class Failed(val safeCode: String) : HistoryKnowledgeCurationResult
}

/** Manual execution is confirmed per run; automatic execution is gated by the dedicated user setting. */
fun interface HistoryKnowledgeRefiner {
    fun refine(source: HistoryKnowledgeCurationSource): HistoryKnowledgeCurationResult
}

sealed interface ReadHistoryKnowledgeCurationSourceResult {
    data class Available(val source: HistoryKnowledgeCurationSource) : ReadHistoryKnowledgeCurationSourceResult
    data object MissingConversation : ReadHistoryKnowledgeCurationSourceResult
    data object NoText : ReadHistoryKnowledgeCurationSourceResult
}

/** Prepares a bounded visible path locally; it performs no model call and never reads attachments. */
class ReadHistoryKnowledgeCurationSourceUseCase(
    private val conversations: ConversationRepository,
) {
    fun execute(conversationId: ConversationId): ReadHistoryKnowledgeCurationSourceResult {
        val snapshot = conversations.findById(conversationId) ?: return ReadHistoryKnowledgeCurationSourceResult.MissingConversation
        val allMessages = MessageTree(snapshot.conversation, snapshot.nodes).contextPath()
            .asSequence()
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .mapNotNull { node ->
                node.content.filterIsInstance<ContentBlock.Text>()
                    .joinToString("\n") { it.text.trim() }
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?.let { text -> "${if (node.role == MessageRole.USER) "用户" else "南枫AI"}：$text" }
            }
            .toList()
        val messages = if (allMessages.size <= MAX_MESSAGES) allMessages else
            allMessages.take(FIRST_MESSAGE_COUNT) + listOf("[中间消息已本机省略]") + allMessages.takeLast(LAST_MESSAGE_COUNT)
        val completeTranscript = messages.joinToString("\n\n")
        val transcript = if (completeTranscript.length <= MAX_TRANSCRIPT_CHARS) completeTranscript else
            completeTranscript.take(HEAD_TRANSCRIPT_CHARS) + "\n\n[中间内容已本机省略]\n\n" + completeTranscript.takeLast(TAIL_TRANSCRIPT_CHARS)
        return transcript.takeIf(String::isNotBlank)
            ?.let { ReadHistoryKnowledgeCurationSourceResult.Available(HistoryKnowledgeCurationSource(conversationId, snapshot.conversation.title, it, allMessages.size)) }
            ?: ReadHistoryKnowledgeCurationSourceResult.NoText
    }

    private companion object {
        const val MAX_MESSAGES = 80
        const val FIRST_MESSAGE_COUNT = 24
        const val LAST_MESSAGE_COUNT = 56
        const val MAX_TRANSCRIPT_CHARS = 14_000
        const val HEAD_TRANSCRIPT_CHARS = 5_000
        const val TAIL_TRANSCRIPT_CHARS = 8_960
    }
}

/** Content-free local checkpoint. It never stores a transcript, model response, API key or prompt. */
interface HistoryKnowledgeCurationCheckpointStore {
    fun reserve(conversationId: ConversationId, sourceHash: String): HistoryKnowledgeCurationReservation
    fun markProcessed(conversationId: ConversationId, sourceHash: String)
    fun markRetryableFailure(conversationId: ConversationId, sourceHash: String)
}

enum class HistoryKnowledgeCurationReservation { RESERVED, ALREADY_PROCESSED, UNKNOWN }

enum class HistoryKnowledgeCurationReason {
    MISSING_CONVERSATION,
    NO_TEXT,
    TOO_FEW_MESSAGES,
    SOURCE_TOO_SHORT,
    SOURCE_TOO_LONG,
    ALREADY_PROCESSED,
    EXISTING_SOURCE,
    MODEL_NOT_ELIGIBLE,
    LOW_CONFIDENCE,
    DUPLICATE_CONTENT,
    NO_ELIGIBLE_CONVERSATION,
}

sealed interface AutomaticHistoryKnowledgeCurationResult {
    data object Disabled : AutomaticHistoryKnowledgeCurationResult
    data class NotEligible(val reason: HistoryKnowledgeCurationReason) : AutomaticHistoryKnowledgeCurationResult
    data class Duplicate(val reason: HistoryKnowledgeCurationReason) : AutomaticHistoryKnowledgeCurationResult
    data class NotConfident(val reason: HistoryKnowledgeCurationReason = HistoryKnowledgeCurationReason.LOW_CONFIDENCE) : AutomaticHistoryKnowledgeCurationResult
    /** A provider call may have escaped, so this source is never resent automatically. */
    data class Unknown(val safeCode: String) : AutomaticHistoryKnowledgeCurationResult
    data class Saved(val knowledgeId: KnowledgeItemId) : AutomaticHistoryKnowledgeCurationResult
    data class Failed(val safeCode: String) : AutomaticHistoryKnowledgeCurationResult
}

/**
 * The model decides semantic value. This local gate only avoids repeated, tiny or overlong egress.
 * It intentionally sends no attachment, tool result, sibling branch or unrelated history.
 */
class AutomaticHistoryKnowledgeCurationOwner(
    private val settings: () -> AssistantExperienceSettings,
    private val conversations: ConversationRepository,
    private val readSource: ReadHistoryKnowledgeCurationSourceUseCase,
    private val refiner: HistoryKnowledgeRefiner,
    private val checkpoint: HistoryKnowledgeCurationCheckpointStore,
    private val readKnowledge: ReadKnowledgeLibraryUseCase,
    private val manageKnowledge: ManageKnowledgeUseCase,
) {
    /** One window handles one conversation; its bounded provider fallback uses the shared refinement route. */
    fun curateNext(): AutomaticHistoryKnowledgeCurationResult {
        var interruptedSourceSeen = false
        conversations.listActive()
            .sortedByDescending(Conversation::updatedAt)
            .take(MAX_CANDIDATE_SCAN)
            .forEach { conversation ->
                when (val result = curate(conversation.id)) {
                    AutomaticHistoryKnowledgeCurationResult.Disabled -> return result
                    is AutomaticHistoryKnowledgeCurationResult.Duplicate -> when (result.reason) {
                        HistoryKnowledgeCurationReason.ALREADY_PROCESSED,
                        HistoryKnowledgeCurationReason.EXISTING_SOURCE -> Unit
                        else -> return result
                    }
                    is AutomaticHistoryKnowledgeCurationResult.NotEligible -> when (result.reason) {
                        HistoryKnowledgeCurationReason.MISSING_CONVERSATION,
                        HistoryKnowledgeCurationReason.NO_TEXT,
                        HistoryKnowledgeCurationReason.TOO_FEW_MESSAGES,
                        HistoryKnowledgeCurationReason.SOURCE_TOO_SHORT,
                        HistoryKnowledgeCurationReason.SOURCE_TOO_LONG -> Unit
                        else -> return result
                    }
                    is AutomaticHistoryKnowledgeCurationResult.Unknown -> interruptedSourceSeen = true
                    else -> return result
                }
            }
        return if (interruptedSourceSeen) AutomaticHistoryKnowledgeCurationResult.Unknown("INTERRUPTED_AFTER_RESERVATION")
        else AutomaticHistoryKnowledgeCurationResult.NotEligible(HistoryKnowledgeCurationReason.NO_ELIGIBLE_CONVERSATION)
    }

    fun curate(conversationId: ConversationId): AutomaticHistoryKnowledgeCurationResult {
        if (!settings().historyLibraryEnabled) return AutomaticHistoryKnowledgeCurationResult.Disabled
        val source = when (val read = readSource.execute(conversationId)) {
            ReadHistoryKnowledgeCurationSourceResult.MissingConversation -> return AutomaticHistoryKnowledgeCurationResult.NotEligible(HistoryKnowledgeCurationReason.MISSING_CONVERSATION)
            ReadHistoryKnowledgeCurationSourceResult.NoText -> return AutomaticHistoryKnowledgeCurationResult.NotEligible(HistoryKnowledgeCurationReason.NO_TEXT)
            is ReadHistoryKnowledgeCurationSourceResult.Available -> read.source
        }
        if (source.messageCount < MIN_MESSAGES) return AutomaticHistoryKnowledgeCurationResult.NotEligible(HistoryKnowledgeCurationReason.TOO_FEW_MESSAGES)
        if (source.transcript.length < MIN_CHARS) return AutomaticHistoryKnowledgeCurationResult.NotEligible(HistoryKnowledgeCurationReason.SOURCE_TOO_SHORT)
        if (source.transcript.length > MAX_SOURCE_CHARS) return AutomaticHistoryKnowledgeCurationResult.NotEligible(HistoryKnowledgeCurationReason.SOURCE_TOO_LONG)
        val sourceHash = MemoryDomain.sha256("${source.conversationTitle}\n${source.transcript}")
        if (readKnowledge.list().any { item -> item.sourceEvidence.any { it.sourceReference == "conversation:${conversationId.value}" } }) {
            checkpoint.markProcessed(conversationId, sourceHash)
            return AutomaticHistoryKnowledgeCurationResult.Duplicate(HistoryKnowledgeCurationReason.EXISTING_SOURCE)
        }
        when (checkpoint.reserve(conversationId, sourceHash)) {
            HistoryKnowledgeCurationReservation.ALREADY_PROCESSED -> return AutomaticHistoryKnowledgeCurationResult.Duplicate(HistoryKnowledgeCurationReason.ALREADY_PROCESSED)
            HistoryKnowledgeCurationReservation.UNKNOWN -> return AutomaticHistoryKnowledgeCurationResult.Unknown("INTERRUPTED_AFTER_RESERVATION")
            HistoryKnowledgeCurationReservation.RESERVED -> Unit
        }
        val compact = source.compactForAutomatic()
        return when (val curated = refiner.refine(compact)) {
            HistoryKnowledgeCurationResult.NotEligible -> AutomaticHistoryKnowledgeCurationResult.NotEligible(HistoryKnowledgeCurationReason.MODEL_NOT_ELIGIBLE).also { checkpoint.markProcessed(conversationId, sourceHash) }
            is HistoryKnowledgeCurationResult.Failed -> AutomaticHistoryKnowledgeCurationResult.Failed(curated.safeCode).also { checkpoint.markRetryableFailure(conversationId, sourceHash) }
            is HistoryKnowledgeCurationResult.Draft -> {
                val draft = curated.value
                if (draft.confidence < MIN_CONFIDENCE) return AutomaticHistoryKnowledgeCurationResult.NotConfident().also { checkpoint.markProcessed(conversationId, sourceHash) }
                val canonicalDraft = canonicalContent(draft.title, draft.body)
                if (readKnowledge.list().any { canonicalContent(it.title, it.summary) == canonicalDraft }) {
                    return AutomaticHistoryKnowledgeCurationResult.Duplicate(HistoryKnowledgeCurationReason.DUPLICATE_CONTENT).also { checkpoint.markProcessed(conversationId, sourceHash) }
                }
                val similarExisting = readKnowledge.list().any { item ->
                    item.title.trim().equals(draft.title.trim(), ignoreCase = true) &&
                        lexicalSimilarity(item.summary, draft.body) >= SIMILARITY_REVIEW_THRESHOLD
                }
                when (val saved = manageKnowledge.execute(KnowledgeIntent(
                    id = KnowledgeIntentId.new(), action = KnowledgeIntentAction.CREATE_HISTORY_CONVERSATION,
                    knowledgeId = KnowledgeItemId.new(), title = draft.title, body = draft.body,
                    tags = draft.tags + if (similarExisting) setOf("可能重复") else emptySet(),
                    scope = KnowledgeScope.GLOBAL, importReference = "conversation:${conversationId.value}",
                    generatedProviderId = draft.providerId, generatedModelId = draft.modelId, generatedAutomatically = true,
                ))) {
                    is KnowledgeMutationResult.Applied, is KnowledgeMutationResult.Replayed -> {
                        checkpoint.markProcessed(conversationId, sourceHash)
                        AutomaticHistoryKnowledgeCurationResult.Saved((saved as? KnowledgeMutationResult.Applied)?.snapshot?.item?.id ?: (saved as KnowledgeMutationResult.Replayed).snapshot.item.id)
                    }
                    is KnowledgeMutationResult.Rejected -> AutomaticHistoryKnowledgeCurationResult.Failed(saved.code.name).also { checkpoint.markRetryableFailure(conversationId, sourceHash) }
                }
            }
        }
    }

    private fun HistoryKnowledgeCurationSource.compactForAutomatic(): HistoryKnowledgeCurationSource {
        if (transcript.length <= AUTO_TRANSCRIPT_CHARS) return copy(automatic = true)
        val head = transcript.take(AUTO_HEAD_CHARS)
        val tail = transcript.takeLast(AUTO_TRANSCRIPT_CHARS - AUTO_HEAD_CHARS)
        return copy(transcript = "$head\n\n[中间内容已本机省略]\n\n$tail", automatic = true)
    }

    private fun canonicalContent(title: String, body: String): String =
        MemoryDomain.sha256("${title.trim().lowercase()}\n${body.trim().lowercase().replace(Regex("\\s+"), " ")}")

    /** Similarity only adds a review tag; it never suppresses a potentially distinct user fact. */
    private fun lexicalSimilarity(left: String, right: String): Double {
        val leftTerms = left.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) .filter { it.length > 1 }.toSet()
        val rightTerms = right.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) .filter { it.length > 1 }.toSet()
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) return 0.0
        return leftTerms.intersect(rightTerms).size.toDouble() / leftTerms.union(rightTerms).size
    }

    private companion object {
        const val MIN_MESSAGES = 4
        const val MIN_CHARS = 500
        const val MAX_SOURCE_CHARS = 14_000
        const val AUTO_TRANSCRIPT_CHARS = 6_000
        const val AUTO_HEAD_CHARS = 2_000
        const val MIN_CONFIDENCE = 0.86
        const val SIMILARITY_REVIEW_THRESHOLD = 0.92
        const val MAX_CANDIDATE_SCAN = 96
    }
}
