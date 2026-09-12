package com.nanzhufeng.ai.domain

import com.nanzhufeng.ai.data.InMemoryKnowledgeRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticHistoryKnowledgeCurationOwnerTest {
    private val now = Instant.parse("2026-09-01T01:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `one window makes at most one provider call when model finds no durable value`() {
        val conversations = Conversations(listOf(snapshot("new", 4, 180), snapshot("old", 4, 180)))
        val refiner = Refiner { HistoryKnowledgeCurationResult.NotEligible }
        val owner = owner(conversations, refiner, Checkpoints())

        val result = owner.curateNext()

        assertEquals(1, refiner.calls)
        assertEquals(
            HistoryKnowledgeCurationReason.MODEL_NOT_ELIGIBLE,
            (result as AutomaticHistoryKnowledgeCurationResult.NotEligible).reason,
        )
    }

    @Test fun `known network failure keeps source retryable`() {
        val conversations = Conversations(listOf(snapshot("retry", 4, 180)))
        val outcomes = ArrayDeque<HistoryKnowledgeCurationResult>().apply {
            add(HistoryKnowledgeCurationResult.Failed("NETWORK"))
            add(HistoryKnowledgeCurationResult.NotEligible)
        }
        val refiner = Refiner { outcomes.removeFirst() }
        val checkpoints = Checkpoints()
        val owner = owner(conversations, refiner, checkpoints)

        assertTrue(owner.curate(ConversationId("retry")) is AutomaticHistoryKnowledgeCurationResult.Failed)
        assertTrue(owner.curate(ConversationId("retry")) is AutomaticHistoryKnowledgeCurationResult.NotEligible)
        assertEquals(2, refiner.calls)
    }

    @Test fun `interrupted reservation becomes unknown and is never resent automatically`() {
        val conversations = Conversations(listOf(snapshot("unknown", 4, 180)))
        val checkpoints = Checkpoints(initial = Checkpoint.RUNNING)
        val refiner = Refiner { HistoryKnowledgeCurationResult.NotEligible }

        val result = owner(conversations, refiner, checkpoints).curate(ConversationId("unknown"))

        assertTrue(result is AutomaticHistoryKnowledgeCurationResult.Unknown)
        assertEquals(0, refiner.calls)
        assertEquals(Checkpoint.UNKNOWN, checkpoints.current)
    }

    @Test fun `disabled toggle blocks calls and reopening permits a new eligible check`() {
        var enabled = false
        val conversations = Conversations(listOf(snapshot("toggle", 4, 180)))
        val refiner = Refiner { HistoryKnowledgeCurationResult.NotEligible }
        val owner = owner(conversations, refiner, Checkpoints()) { enabled }

        assertTrue(owner.curateNext() is AutomaticHistoryKnowledgeCurationResult.Disabled)
        assertEquals(0, refiner.calls)
        enabled = true
        assertTrue(owner.curateNext() is AutomaticHistoryKnowledgeCurationResult.NotEligible)
        assertEquals(1, refiner.calls)
    }

    @Test fun `local eligibility explains too few messages without calling provider`() {
        val conversations = Conversations(listOf(snapshot("short", 3, 220)))
        val refiner = Refiner { HistoryKnowledgeCurationResult.NotEligible }

        val result = owner(conversations, refiner, Checkpoints()).curate(ConversationId("short"))

        assertEquals(
            HistoryKnowledgeCurationReason.TOO_FEW_MESSAGES,
            (result as AutomaticHistoryKnowledgeCurationResult.NotEligible).reason,
        )
        assertEquals(0, refiner.calls)
    }

    @Test fun `confident candidate is persisted and checkpointed`() {
        val conversations = Conversations(listOf(snapshot("save", 4, 180)))
        val checkpoints = Checkpoints()
        val refiner = Refiner {
            HistoryKnowledgeCurationResult.Draft(
                HistoryKnowledgeCurationDraft(
                    title = "可复用的长期决策规则",
                    body = "这是用户在历史对话中明确确认的长期决策方法。使用时需先核对当前条件，再按已确认的优先级执行；如果关键假设发生变化，应暂停并重新评估。",
                    tags = setOf("长期规则"),
                    providerId = ProviderId.DEEPSEEK,
                    modelId = "deepseek-flash",
                    confidence = 0.93,
                ),
            )
        }

        val result = owner(conversations, refiner, checkpoints).curate(ConversationId("save"))

        assertTrue(result is AutomaticHistoryKnowledgeCurationResult.Saved)
        assertEquals(Checkpoint.PROCESSED, checkpoints.current)
    }

    private fun owner(
        conversations: Conversations,
        refiner: Refiner,
        checkpoints: Checkpoints,
        enabled: () -> Boolean = { true },
    ): AutomaticHistoryKnowledgeCurationOwner {
        val knowledge = InMemoryKnowledgeRepository()
        return AutomaticHistoryKnowledgeCurationOwner(
            settings = { AssistantExperienceSettings(librarySearchEnabled = enabled(), autoHistoryKnowledgeEnabled = enabled()) },
            conversations = conversations,
            readSource = ReadHistoryKnowledgeCurationSourceUseCase(conversations),
            refiner = refiner,
            checkpoint = checkpoints,
            readKnowledge = ReadKnowledgeLibraryUseCase(knowledge, EmptyCandidates),
            manageKnowledge = ManageKnowledgeUseCase(KnowledgeDomain(clock), AppliedKnowledgeManagement(now)),
        )
    }

    private fun snapshot(id: String, count: Int, charsPerMessage: Int): ConversationSnapshot {
        val conversationId = ConversationId(id)
        var parent: MessageNodeId? = null
        val nodes = (0 until count).map { index ->
            MessageNode(
                id = MessageNodeId("$id-message-$index"),
                conversationId = conversationId,
                parentMessageId = parent,
                siblingPosition = 0,
                role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                content = listOf(ContentBlock.Text((if (index % 2 == 0) "用户确认" else "整理回应") + "长期工作方法".repeat(charsPerMessage / 6))),
                createdAt = now.plusSeconds(index.toLong()),
            ).also { parent = it.id }
        }
        return ConversationSnapshot(
            Conversation(conversationId, "历史资料整理", currentLeafMessageId = nodes.last().id, createdAt = now, updatedAt = now.plusSeconds(count.toLong())),
            nodes,
            ConversationDraft(updatedAt = now),
        )
    }

    private class Conversations(private val snapshots: List<ConversationSnapshot>) : ConversationRepository {
        override fun save(snapshot: ConversationSnapshot) = snapshot
        override fun findById(id: ConversationId) = snapshots.firstOrNull { it.conversation.id == id }
        override fun listActive() = snapshots.map { it.conversation }
    }

    private class Refiner(private val result: () -> HistoryKnowledgeCurationResult) : HistoryKnowledgeRefiner {
        var calls = 0
        override fun refine(source: HistoryKnowledgeCurationSource): HistoryKnowledgeCurationResult {
            calls += 1
            return result()
        }
    }

    private enum class Checkpoint { AVAILABLE, RUNNING, PROCESSED, RETRYABLE_FAILED, UNKNOWN }

    private class Checkpoints(initial: Checkpoint = Checkpoint.AVAILABLE) : HistoryKnowledgeCurationCheckpointStore {
        var current = initial
        override fun reserve(conversationId: ConversationId, sourceHash: String): HistoryKnowledgeCurationReservation = when (current) {
            Checkpoint.PROCESSED -> HistoryKnowledgeCurationReservation.ALREADY_PROCESSED
            Checkpoint.RUNNING -> HistoryKnowledgeCurationReservation.UNKNOWN.also { current = Checkpoint.UNKNOWN }
            Checkpoint.UNKNOWN -> HistoryKnowledgeCurationReservation.UNKNOWN
            Checkpoint.AVAILABLE, Checkpoint.RETRYABLE_FAILED -> HistoryKnowledgeCurationReservation.RESERVED.also { current = Checkpoint.RUNNING }
        }
        override fun markProcessed(conversationId: ConversationId, sourceHash: String) { current = Checkpoint.PROCESSED }
        override fun markRetryableFailure(conversationId: ConversationId, sourceHash: String) { current = Checkpoint.RETRYABLE_FAILED }
    }

    private object EmptyCandidates : GeneratedCandidateRepository {
        override fun save(candidate: StoredGeneratedCandidate) = candidate
        override fun findById(id: CandidateId): StoredGeneratedCandidate? = null
        override fun findLatestPendingReview(): StoredGeneratedCandidate? = null
        override fun updateStatus(id: CandidateId, status: CandidateReviewStatus, updatedAt: Instant) = error("unused")
    }

    private class AppliedKnowledgeManagement(private val now: Instant) : KnowledgeManagementRepository {
        override fun mutate(intent: KnowledgeIntent, fingerprint: String): KnowledgeMutationResult {
            val title = requireNotNull(intent.title)
            val body = requireNotNull(intent.body)
            val item = KnowledgeItem(
                id = intent.knowledgeId,
                title = title,
                body = body,
                sourceEvidence = listOf(SourceEvidence(CaptureSourceType.HISTORY_CONVERSATION, now, intent.importReference, setOf("title", "body"))),
                provenance = CandidateProvenance(CandidateId.new(), InvocationId.new(), requireNotNull(intent.generatedProviderId), requireNotNull(intent.generatedModelId), 1),
                createdAt = now,
            )
            val hash = MemoryDomain.sha256("$title\n$body")
            return KnowledgeMutationResult.Applied(
                KnowledgeSnapshot(
                    item,
                    KnowledgeLifecycle(contentHash = hash, updatedAt = now, tags = intent.tags),
                    listOf(KnowledgeRevision(KnowledgeRevisionId.new(), item.id, 1, title, body, KnowledgeStatus.ACTIVE, KnowledgeScope.GLOBAL, null, intent.tags, hash, now)),
                ),
            )
        }
        override fun findSnapshot(id: KnowledgeItemId): KnowledgeSnapshot? = null
        override fun listSnapshots(filter: KnowledgeSearchFilter): List<KnowledgeSnapshot> = emptyList()
    }
}
