package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ConversationAppEntryPolicyTest {
    private val exitAt = 1_800_000_000_000L
    private val previous = Conversation(ConversationId("last-exit"), "Last exit", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test fun shortRestartRestoresExactlyTheExitId() {
        assertEquals(previous.id, ConversationAppEntryPolicy.retainedId("last-exit", exitAt, exitAt + 60_000L))
        assertEquals(previous, ConversationAppEntryPolicy.restorableConversation(previous))
    }
    @Test fun timeoutBoundaryOpensFreshChat() {
        assertEquals(previous.id, ConversationAppEntryPolicy.retainedId("last-exit", exitAt, exitAt + 899_999L))
        assertNull(ConversationAppEntryPolicy.retainedId("last-exit", exitAt, exitAt + 900_000L))
    }
    @Test fun missingLegacyOrInvalidClockMetadataNeverSelectsHistory() {
        assertNull(ConversationAppEntryPolicy.retainedId(null, exitAt, exitAt + 100L))
        assertNull(ConversationAppEntryPolicy.retainedId("", exitAt, exitAt + 100L))
        assertNull(ConversationAppEntryPolicy.retainedId("last-exit", 0L, exitAt))
        assertNull(ConversationAppEntryPolicy.retainedId("last-exit", exitAt, exitAt - 1L))
    }
    @Test fun generationContinuityPreservesExitWorkspaceWithoutReplaying() {
        assertEquals(previous.id, ConversationAppEntryPolicy.retainedId("last-exit", exitAt, exitAt + 3_600_000L, true))
        assertNull(ConversationAppEntryPolicy.retainedId(null, exitAt, exitAt + 3_600_000L, true))
    }
    @Test fun deletedArchivedOrMissingExitConversationRequiresFreshChat() {
        assertNull(ConversationAppEntryPolicy.restorableConversation(null))
        assertNull(ConversationAppEntryPolicy.restorableConversation(previous.copy(deletedAt = Instant.EPOCH)))
        assertNull(ConversationAppEntryPolicy.restorableConversation(previous.copy(archivedAt = Instant.EPOCH)))
    }
    @Test fun freshEntryNeverReusesNamedPinnedOrUnsentDrafts() {
        val blank = ConversationSnapshot(previous.copy(autoTitlePending = true), emptyList(), ConversationDraft(updatedAt = Instant.EPOCH))
        assertTrue(ConversationAppEntryPolicy.isReusableBlank(blank))
        assertFalse(ConversationAppEntryPolicy.isReusableBlank(blank.copy(conversation = previous)))
        assertFalse(ConversationAppEntryPolicy.isReusableBlank(blank.copy(conversation = blank.conversation.copy(pinnedAt = Instant.EPOCH))))
        assertFalse(ConversationAppEntryPolicy.isReusableBlank(blank.copy(draft = blank.draft.copy(text = "unsent draft"))))
    }
    @Test fun workSurfaceAndPinnedExitArePreservedWhenActuallySelected() {
        val work = previous.copy(surface = ConversationSurface.WORK, pinnedAt = Instant.EPOCH)
        assertEquals(work, ConversationAppEntryPolicy.restorableConversation(work))
    }
}
