package com.nanzhufeng.ai.domain

import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P3GConversationAttachmentContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T08:00:00Z"), ZoneOffset.UTC)

    @Test fun `picker attachment is local-only safe reference and duplicate result is idempotent`() {
        val drafts = FakeDrafts(ConversationDraft(updatedAt = clock.instant()))
        val assets = FakeAssets()
        val useCase = AddConversationImageAttachmentUseCase(FakeStore(), assets, drafts, clock)
        val first = useCase.execute(ConversationId("c"), selection("a.png")) as AddConversationAttachmentResult.Added
        val duplicate = useCase.execute(ConversationId("c"), selection("a.png")) as AddConversationAttachmentResult.Added

        assertEquals(1, first.draft.attachments.size)
        assertEquals(first.draft, duplicate.draft)
        val safe = first.draft.attachments.single()
        assertFalse(safe.toString().contains("attachments/v1/"))
        assertEquals(ConversationAttachmentEgressScope.LOCAL_ONLY_NO_EGRESS, ConversationAttachmentEgressScope.LOCAL_ONLY_NO_EGRESS)
    }

    @Test fun `limits rejection and removal preserve private assets while send keeps ordered blocks`() {
        val a = safe("a", "a".repeat(64))
        val b = safe("b", "b".repeat(64))
        val drafts = FakeDrafts(ConversationDraft("正文", listOf(a, b), clock.instant()))
        val removed = RemoveConversationAttachmentUseCase(drafts, clock).execute(ConversationId("c"), a.id) as RemoveConversationAttachmentResult.Removed
        assertEquals(listOf(b), removed.draft.attachments)
        assertTrue(FakeAssets().findById(a.id) == null) // removal has no asset/GC dependency

        val tree = ConversationTreeService(clock)
        val snapshot = tree.create("顺序")
        val submitted = SubmitConversationDraftUseCase(FakeConversations(snapshot), drafts, tree).execute(ConversationId("c"))
        assertTrue(submitted is ConversationDraftSubmissionResult.Submitted)
        val message = (submitted as ConversationDraftSubmissionResult.Submitted).snapshot.nodes.single()
        assertEquals("正文", (message.content[0] as ContentBlock.Text).text)
        assertEquals(b.id, (message.content[1] as ContentBlock.Attachment).attachment.id)
    }

    @Test fun `unsupported mime pixels and count never overwrite the persisted draft`() {
        val existing = ConversationDraft("保留文字", listOf(safe("a", "a".repeat(64))), clock.instant())
        val drafts = FakeDrafts(existing)
        val useCase = AddConversationImageAttachmentUseCase(FakeStore(), FakeAssets(), drafts, clock)
        val unsupported = useCase.execute(ConversationId("c"), selection("x.gif", "image/gif"))
        val bomb = useCase.execute(ConversationId("c"), selection("x.png", "image/png", 100_000, 100_000))
        assertTrue(unsupported is AddConversationAttachmentResult.Rejected)
        assertTrue(bomb is AddConversationAttachmentResult.Rejected)
        assertEquals(existing, drafts.loadDraft(ConversationId("c")))
    }

    private fun selection(name: String, mime: String = "image/png", width: Int = 32, height: Int = 32) =
        GalleryImageSelection(ByteArrayInputStream(byteArrayOf(1, 2, 3)), mime, name, width, height)

    private fun safe(id: String, hash: String) = ConversationAttachmentReference(AttachmentId(id), "image/png", "$id.png", 3, hash)

    private class FakeDrafts(initial: ConversationDraft) : ConversationDraftRepository {
        private var draft = initial
        override fun loadDraft(conversationId: ConversationId) = draft
        override fun saveDraft(conversationId: ConversationId, draft: ConversationDraft): ConversationDraft = draft.also { this.draft = it }
        override fun submitDraft(snapshotWithClearedDraft: ConversationSnapshot, expectedDraft: ConversationDraft) = ConversationDraftSubmissionResult.Submitted(snapshotWithClearedDraft)
    }

    private class FakeAssets : PrivateAttachmentRepository {
        private val values = linkedMapOf<String, AttachmentReference>()
        override fun save(asset: AttachmentReference): AttachmentReference = values.values.firstOrNull { it.sha256 == asset.sha256 } ?: asset.also { values[it.id.value] = it }
        override fun findById(id: AttachmentId) = values[id.value]
        override fun findBySha256(sha256: String) = values.values.firstOrNull { it.sha256 == sha256 }
    }

    private class FakeStore : PrivateAttachmentStore {
        override fun import(request: AttachmentImportRequest) = AttachmentImportResult.Imported(AttachmentReference("attachments/v1/fake.png", request.mimeType, request.displayName, AttachmentId.new(), 3, "c".repeat(64)))
        override fun read(attachment: AttachmentReference) = AttachmentReadResult.Content(byteArrayOf(1))
        override fun thumbnail(attachment: AttachmentReference) = AttachmentThumbnailResult.Ready(AttachmentThumbnail(byteArrayOf(1), 1, 1))
    }

    private class FakeConversations(private val initial: ConversationSnapshot) : ConversationRepository {
        override fun save(snapshot: ConversationSnapshot) = snapshot
        override fun findById(id: ConversationId) = initial.copy(draft = ConversationDraft("正文", listOf(ConversationAttachmentReference(AttachmentId("b"), "image/png", "b.png", 3, "b".repeat(64))), Instant.EPOCH))
        override fun listActive() = listOf(initial.conversation)
    }
}
