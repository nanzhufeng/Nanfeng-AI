package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationWebSearchOverrideOwnerTest {
    @Test
    fun `conversation override inherits global then changes only the selected conversation`() {
        val store = InMemoryConversationWebSearchOverrideStore()
        val owner = ConversationWebSearchOverrideOwner(store)
        val first = ConversationId.new()
        val second = ConversationId.new()

        assertTrue(owner.effectiveEnabled(first, globalEnabled = true))
        assertFalse(owner.effectiveEnabled(first, globalEnabled = false))

        assertTrue(owner.setEnabled(first, enabled = false, expectedRevision = 0) is ConversationWebSearchOverrideMutationResult.Applied)
        assertFalse(owner.effectiveEnabled(first, globalEnabled = true))
        assertTrue(owner.effectiveEnabled(second, globalEnabled = true))
        assertEquals(1, owner.read(first).revision)
    }

    @Test
    fun `stale write does not replace the current conversation choice`() {
        val owner = ConversationWebSearchOverrideOwner(InMemoryConversationWebSearchOverrideStore())
        val conversationId = ConversationId.new()

        owner.setEnabled(conversationId, enabled = true, expectedRevision = 0)

        assertEquals(
            ConversationWebSearchOverrideMutationResult.Conflict,
            owner.setEnabled(conversationId, enabled = false, expectedRevision = 0),
        )
        assertTrue(owner.effectiveEnabled(conversationId, globalEnabled = false))
    }
}

private class InMemoryConversationWebSearchOverrideStore : ConversationWebSearchOverrideStore {
    private val values = mutableMapOf<ConversationId, ConversationWebSearchOverride>()

    override fun read(conversationId: ConversationId): ConversationWebSearchOverride =
        values[conversationId] ?: ConversationWebSearchOverride(conversationId, revision = 0)

    override fun save(value: ConversationWebSearchOverride): Boolean {
        values[value.conversationId] = value
        return true
    }
}
