package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P6ETemporaryConversationContractsTest {
    @Test fun `temporary owner is idempotent isolated and expires at exactly 24 hours`() {
        val store = InMemoryTemporaryStore()
        val start = Instant.parse("2026-08-13T00:00:00Z")
        val owner = TemporaryConversationDomain(store, Clock.fixed(start, ZoneOffset.UTC))
        val first = owner.enterOrRestore()
        assertEquals(first.id, owner.enterOrRestore().id)
        owner.updateDraft("temporary only", emptyList())
        assertEquals("temporary only", store.readActive()?.draftText)
        assertTrue(store.readActive()!!.messages.isEmpty())

        val beforeExpiry = TemporaryConversationDomain(store, Clock.fixed(start.plusSeconds(24 * 60 * 60 - 1), ZoneOffset.UTC))
        assertEquals(first.id, beforeExpiry.readRecovery()?.id)
        val atExpiry = TemporaryConversationDomain(store, Clock.fixed(start.plusSeconds(24 * 60 * 60), ZoneOffset.UTC))
        assertNull(atExpiry.readRecovery())
        assertNull(store.readActive())
    }

    @Test fun `offline messages and attachment references stay under temporary session owner`() {
        val store = InMemoryTemporaryStore()
        val owner = TemporaryConversationDomain(store, Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC))
        val attachment = AttachmentId.new()
        owner.enterOrRestore()
        owner.updateDraft("hello", listOf(attachment))
        val afterSend = owner.appendOfflineMessage()
        assertEquals("hello", afterSend.messages.single().text)
        assertEquals(listOf(attachment), afterSend.messages.single().attachmentIds)
        assertTrue(afterSend.draftAttachmentIds.isEmpty())
        assertEquals(ConversationKind.TEMPORARY, ConversationKind.TEMPORARY)
        assertEquals(TemporaryAttachmentScope.TEMPORARY_SESSION, TemporaryAttachmentScope.TEMPORARY_SESSION)
    }

    @Test fun `model override is an isolated temporary recovery field`() {
        val store = InMemoryTemporaryStore()
        val owner = TemporaryConversationDomain(store, Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC))
        owner.enterOrRestore()
        assertEquals("local.fixture-v1", owner.updateModelOverride("local.fixture-v1").modelOverrideId)
        assertEquals("local.fixture-v1", owner.readRecovery()?.modelOverrideId)
        assertEquals("", owner.readRecovery()?.draftText)
        assertTrue(owner.readRecovery()?.messages.orEmpty().isEmpty())
        assertNull(owner.updateModelOverride(null).modelOverrideId)
    }

    @Test fun `acceptance harness uses the temporary owner fixed clock and removes its private copy`() {
        val recovery = InMemoryTemporaryStore()
        val privateStore = AcceptancePrivateStore()
        val assets = AcceptanceAssets()
        val receipt = P6ETemporaryMaintenanceAcceptanceHarness(recovery, privateStore, assets).run()

        assertTrue(receipt.retainedAt23h59)
        assertTrue(receipt.removedAt24h)
        assertTrue(receipt.attachmentRemovedAt24h)
        assertTrue(receipt.messageWasPresentBeforeExpiry)
        assertTrue(receipt.modelOverrideWasPresentBeforeExpiry)
        assertNull(recovery.readActive())
        assertTrue(privateStore.deleted)
    }

    private class InMemoryTemporaryStore : TemporaryConversationRecoveryStore {
        private var value: TemporaryConversationRecovery? = null
        val deleted = mutableListOf<AttachmentId>()
        override fun readActive() = value
        override fun save(record: TemporaryConversationRecovery): TemporaryConversationRecovery = record.also { value = it }
        override fun delete(id: TemporaryConversationId): List<AttachmentId> = value?.takeIf { it.id == id }?.let { record ->
            value = null
            (record.draftAttachmentIds + record.messages.flatMap { it.attachmentIds }).distinct().also { deleted += it }
        }.orEmpty()
    }

    private class AcceptanceAssets : PrivateAttachmentRepository {
        private val values = linkedMapOf<String, AttachmentReference>()
        override fun save(asset: AttachmentReference) = asset.also { values[it.id.value] = it }
        override fun findById(id: AttachmentId) = values[id.value]
        override fun findBySha256(sha256: String) = values.values.firstOrNull { it.sha256 == sha256 }
        override fun removeIfUnreferenced(id: AttachmentId) = values.remove(id.value)
    }

    private class AcceptancePrivateStore : PrivateAttachmentStore {
        var deleted = false
        override fun import(request: AttachmentImportRequest) = AttachmentImportResult.Imported(
            AttachmentReference(
                reference = "attachments/v1/p6e-acceptance",
                mimeType = request.mimeType,
                displayName = request.displayName,
                id = AttachmentId("p6e-acceptance-attachment"),
                byteCount = 27,
                sha256 = "a".repeat(64),
            ),
        )
        override fun read(attachment: AttachmentReference) = AttachmentReadResult.Content(byteArrayOf(1))
        override fun thumbnail(attachment: AttachmentReference) = AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        override fun deletePrivateCopy(attachment: AttachmentReference) = true.also { deleted = true }
    }
}
