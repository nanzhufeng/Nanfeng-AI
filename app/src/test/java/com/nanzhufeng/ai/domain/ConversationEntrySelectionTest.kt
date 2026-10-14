package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ConversationEntrySelectionTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneOffset.UTC)
    private val tree = ConversationTreeService(clock)
    private class Repository(snapshots: List<ConversationSnapshot>) : ConversationRepository {
        val stored = snapshots.associateBy { it.conversation.id }.toMutableMap()
        val reads = mutableListOf<ConversationId>()
        override fun listActive() = stored.values.map { it.conversation }
        override fun findById(id: ConversationId) = stored[id].also { reads += id }
        override fun save(snapshot: ConversationSnapshot) = snapshot.also { stored[it.conversation.id] = it }
    }
    private fun pinned() = tree.create("Pinned history").let {
        it.copy(conversation = it.conversation.copy(pinnedAt = clock.instant()))
    }

    @Test fun `no selected id never reads the pinned first conversation`() {
        val pinned = pinned()
        val repository = Repository(listOf(pinned))
        assertNull(ConversationRefreshSelection.resolve(repository, null, ConversationSurface.CHAT))
        assertTrue(repository.reads.isEmpty())
    }

    @Test fun `missing retained id never falls back to pinned history`() {
        val repository = Repository(listOf(pinned()))
        assertNull(ConversationRefreshSelection.resolve(repository, ConversationId("deleted"), ConversationSurface.CHAT))
        assertEquals(listOf(ConversationId("deleted")), repository.reads)
    }

    @Test fun `retained id wins over pinned sort order`() {
        val previous = tree.create("Last exit")
        val repository = Repository(listOf(pinned(), previous))
        assertEquals(previous, ConversationRefreshSelection.resolve(repository, previous.conversation.id, ConversationSurface.CHAT))
        assertEquals(listOf(previous.conversation.id), repository.reads)
    }

    @Test fun `new draft lookup does not deserialize historical transcripts`() {
        val history = (1..800).map { tree.create("History $it") } + pinned()
        val draft = tree.create(ConversationAutoTitle.NEW_CONVERSATION_TITLE, autoTitlePending = true)
        val repository = Repository(history + draft)
        val result = CreateConversationUseCase(tree, repository).resumeOrCreateDraft() as ConversationMutationResult.Saved
        assertEquals(draft.conversation.id, result.snapshot.conversation.id)
        assertEquals("Only candidate drafts may load their message tree", listOf(draft.conversation.id), repository.reads)
    }
}
