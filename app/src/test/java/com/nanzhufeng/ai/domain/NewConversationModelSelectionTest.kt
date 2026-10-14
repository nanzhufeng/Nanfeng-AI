package com.nanzhufeng.ai.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class NewConversationModelSelectionTest {
    private class SelectionStore : P6GModelSelectionStore {
        var global = P6GGlobalDefault(0, null)
        val overrides = mutableMapOf<ConversationId, P6GConversationOverride>()
        var rejectSave = false
        override fun readCatalog() = P6GCuratedModelCatalog.snapshot()
        override fun saveCatalog(value: P6GLocalCatalogSnapshot) = true
        override fun readGlobalDefault() = global
        override fun readConversationOverride(conversationId: ConversationId) = overrides[conversationId] ?: P6GConversationOverride(conversationId, 0, null)
        override fun saveGlobalDefault(value: P6GGlobalDefault) = true.also { global = value }
        override fun saveConversationOverride(value: P6GConversationOverride): Boolean {
            if (rejectSave) return false
            overrides[value.conversationId] = value
            return true
        }
        override fun saveComposerModelSelection(conversation: P6GConversationOverride, global: P6GGlobalDefault): Boolean {
            if (rejectSave) return false
            overrides[conversation.conversationId] = conversation
            this.global = global
            return true
        }
        override fun appendRouteMetadata(value: P6GRouteMetadata) = true
    }
    private class Repository : ConversationRepository {
        val rows = mutableMapOf<ConversationId, ConversationSnapshot>()
        override fun listActive() = rows.values.map { it.conversation }
        override fun findById(id: ConversationId) = rows[id]
        override fun save(snapshot: ConversationSnapshot) = snapshot.also { rows[it.conversation.id] = it }
    }
    private val model = "logical:relaycore:sonnet-5.5"
    private fun owner(store: SelectionStore) = P6GModelSelectionOwner(store, P6GModelRouter())

    @Test fun `new conversation freezes last explicit choice and reopening cannot follow later picks`() {
        val store = SelectionStore()
        // Use catalog's exact current RC identity, never a provider response's actual model.
        val selected = ComposerModelRoutingCatalog.choices.first { it.routes.first() == ModelPresetId.RELAYCORE_SONNET_5_5 }.id
        val owner = owner(store)
        assertTrue(owner.setComposerModelSelection(ConversationId("previous"), selected, 0, 0) is P6GSelectionMutationResult.Applied)
        val repository = Repository()
        val create = CreateConversationUseCase(ConversationTreeService(Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC)), repository) {
            check(owner.initializeConversationModel(it) is P6GSelectionMutationResult.Applied)
        }
        val created = create.execute() as ConversationMutationResult.Saved
        val id = created.snapshot.conversation.id
        assertEquals(selected, store.readConversationOverride(id).modelId)
        assertEquals(1L, store.readConversationOverride(id).revision)
        assertTrue(owner.setComposerModelSelection(ConversationId("other"), null, 0, 1) is P6GSelectionMutationResult.Applied)
        assertEquals(id, (create.resumeOrCreateDraft() as ConversationMutationResult.Saved).snapshot.conversation.id)
        assertEquals(selected, store.readConversationOverride(id).modelId)
        val next = (create.execute(surface = ConversationSurface.WORK) as ConversationMutationResult.Saved).snapshot.conversation.id
        assertNull(store.readConversationOverride(next).modelId)
        assertEquals(1L, store.readConversationOverride(next).revision)
    }
    @Test fun `first launch and remembered Auto stay Auto after another manual pick`() {
        val store = SelectionStore(); val owner = owner(store); val id = ConversationId("auto")
        owner.initializeConversationModel(id)
        store.global = P6GGlobalDefault(3, null, "logical:daily:claude-sonnet")
        owner.initializeConversationModel(id)
        assertNull(effectiveComposerModelId(store.readConversationOverride(id), store.global))
    }
    @Test fun `unavailable remembered model is retained exactly without auto substitution`() {
        val store = SelectionStore(); store.global = P6GGlobalDefault(4, null, "retired-exact-id")
        val owner = owner(store); val id = ConversationId("new")
        owner.initializeConversationModel(id)
        assertEquals("retired-exact-id", store.readConversationOverride(id).modelId)
        assertEquals(P6GRouteSource.REJECTED, owner.evaluate(id, P6GRouteRequest(P6GModelTier.BALANCED)).source)
    }
    @Test fun `failed default freeze rejects creation and retry can recover the saved unsent draft`() {
        val store = SelectionStore(); store.rejectSave = true
        val owner = owner(store); val repository = Repository()
        val create = CreateConversationUseCase(ConversationTreeService(Clock.systemUTC()), repository) {
            check(owner.initializeConversationModel(it) is P6GSelectionMutationResult.Applied)
        }
        assertTrue(create.resumeOrCreateDraft() is ConversationMutationResult.Rejected)
        assertEquals(1, repository.rows.size)
        store.rejectSave = false
        assertTrue(create.resumeOrCreateDraft() is ConversationMutationResult.Saved)
        assertEquals(1, repository.rows.size)
    }
}
