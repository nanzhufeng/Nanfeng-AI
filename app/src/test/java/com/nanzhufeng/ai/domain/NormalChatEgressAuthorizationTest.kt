package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class NormalChatEgressAuthorizationTest {
    private val routing = NormalChatRoutingSnapshot(
        ModelPresetId.entries.associateWith { ResolvedModel(NanfengModelServiceCatalog.providerFor(it), "api-${it.name}", it.name,
            ModelCapabilities(true, true, true, supportsPdf = true, supportsVideo = true, supportsAudio = true),
            null, ModelHealth.AVAILABLE, null, null) },
        ModelPresetId.entries.toSet(), true,
    )
    private val draft = ConversationDraft(text = "hello", updatedAt = Instant.EPOCH)
    private fun message(text: String = draft.text) = MessageNode(
        MessageNodeId("message"), ConversationId("conversation"), null, 0,
        MessageRole.USER, listOf(ContentBlock.Text(text)), Instant.EPOCH,
    )

    @Test fun `queued send retains disclosed recipient after selection changes`() {
        var selection = ComposerModelRoutingCatalog.daily.first().id
        val approval = NormalChatEgressAuthorization.forUserSend(draft, 1, selection, routing)
        selection = ComposerModelRoutingCatalog.deep.first().id
        assertNotEquals(selection, approval.recipientChoiceId)
        assertEquals(ComposerModelRoutingCatalog.daily.first().routes, approval.recipientPresets)
        assertTrue(approval.matches(message()))
        assertFalse(approval.copy(recipientChoiceId = selection).matches(message()))
        assertFalse(approval.copy(recipientModelIds = approval.recipientModelIds.mapValues { "changed-api-id" }).matches(message()))
    }

    @Test fun `automatic disclosure and authorization share one frozen route`() {
        val approval = NormalChatEgressAuthorization.forUserSend(draft, 1, null, routing)
        assertEquals(routing.presets(draft, null), approval.recipientPresets)
        assertEquals(approval.recipientPresets, routing.presets(draft, ComposerModelRoutingCatalog.auto.id))
        assertFalse(approval.matches(message("changed")))
    }

    @Test fun `service receipt round trip preserves material and recipient binding`() {
        val approval = NormalChatEgressAuthorization.forUserSend(draft, 1, ComposerModelRoutingCatalog.deep.first().id, routing)
        val restored = NormalChatEgressAuthorization(approval.approvedAtEpochMs, approval.draftFingerprint,
            approval.disclosureVersion, approval.recipientChoiceId,
            approval.recipientPresets.map { ModelPresetId.valueOf(it.name) }, approval.recipientModelIds.toMap())
        assertEquals(approval, restored)
        assertTrue(restored.matches(message()))
    }

    @Test fun `recipient gate rejects another provider or preset and legacy live authorization`() {
        val approval = NormalChatEgressAuthorization.forUserSend(draft, 1, ComposerModelRoutingCatalog.daily.first().id, routing)
        val preset = approval.recipientPresets.single()
        assertTrue(approval.allowsRecipient(preset, NanfengModelServiceCatalog.providerFor(preset), "api-${preset.name}"))
        assertFalse(approval.allowsRecipient(preset, ProviderId.MOCK, "api-${preset.name}"))
        assertFalse(approval.allowsRecipient(ComposerModelRoutingCatalog.deep.first().routes.first(), ProviderId.OPENROUTER, "other-model"))
        assertThrows(IllegalArgumentException::class.java) { approval.copy(disclosureVersion = NormalChatEgressAuthorization.LEGACY_DISCLOSURE_VERSION) }
        assertThrows(IllegalArgumentException::class.java) { approval.copy(recipientPresets = emptyList()) }
    }

    @Test fun `historical audit receipts stay readable without authorizing a new send`() {
        val attempt = NormalChatSendAttempt(
            attemptId = NormalChatSendAttemptId("old"), messageId = MessageNodeId("message"),
            conversationId = ConversationId("conversation"), providerId = ProviderId.OPENROUTER,
            modelId = "historical-model", idempotencyKey = "historical-key",
            status = NormalChatSendAttemptStatus.COMPLETED, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            egressDisclosureVersion = NormalChatEgressAuthorization.LEGACY_DISCLOSURE_VERSION,
        )
        assertEquals("normal-chat-egress-v1", attempt.egressDisclosureVersion)
    }

    @Test fun `auto availability changes after click cannot reroute the approved request`() {
        val first = routing.presets(draft, null).single()
        val alternate = ComposerModelRoutingCatalog.daily.first().routes.single()
        val localView = routing.copy(usablePresets = setOf(alternate))
        val approval = NormalChatEgressAuthorization.forUserSend(draft, 1, null, localView)
        assertEquals(listOf(alternate), approval.recipientPresets)
        assertEquals(listOf(first), routing.presets(draft, null))
        assertTrue(approval.matches(message()))
        assertFalse(approval.allowsRecipient(alternate, NanfengModelServiceCatalog.providerFor(alternate), "changed-after-refresh"))
    }

    @Test fun `auto preview preserves health preference and explicit selection`() {
        val first = routing.presets(draft, null).single()
        val degraded = routing.copy(models = routing.models + (first to routing.models.getValue(first).copy(health = ModelHealth.DEGRADED)))
        assertNotEquals(first, degraded.presets(draft, null).single())
        val manual = ComposerModelRoutingCatalog.daily.first()
        assertEquals(manual.routes, degraded.copy(usablePresets = emptySet()).presets(draft, manual.id))
        assertFalse(routing.copy(models = emptyMap()).canAuthorize(draft, manual.id))
    }
}
