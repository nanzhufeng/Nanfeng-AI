package com.nanzhufeng.ai.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.ai.MockAiTaskRunner
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomCaptureDraftRepository
import com.nanzhufeng.ai.data.local.RoomKnowledgeRepository
import com.nanzhufeng.ai.data.local.RoomInvocationRepository
import com.nanzhufeng.ai.domain.AiTask
import com.nanzhufeng.ai.domain.AiTaskId
import com.nanzhufeng.ai.domain.AiTaskRunResult
import com.nanzhufeng.ai.domain.AttachmentImportRequest
import com.nanzhufeng.ai.domain.AttachmentImportResult
import com.nanzhufeng.ai.domain.AttachmentReadResult
import com.nanzhufeng.ai.domain.AttachmentOpenResult
import com.nanzhufeng.ai.domain.CaptureDraftFactory
import com.nanzhufeng.ai.domain.CaptureGalleryImageResult
import com.nanzhufeng.ai.domain.CaptureGalleryImageUseCase
import com.nanzhufeng.ai.domain.CaptureSourceType
import com.nanzhufeng.ai.domain.CaptureTextDraftResult
import com.nanzhufeng.ai.domain.CaptureTextDraftUseCase
import com.nanzhufeng.ai.domain.CaptureTextInput
import com.nanzhufeng.ai.domain.EgressConsent
import com.nanzhufeng.ai.domain.ExportKnowledgeSnapshotUseCase
import com.nanzhufeng.ai.domain.HarnessProfile
import com.nanzhufeng.ai.domain.GalleryImageSelection
import com.nanzhufeng.ai.domain.InvocationStatus
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelDescriptor
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.RunAiTaskUseCase
import com.nanzhufeng.ai.domain.RestoreCaptureDraftResult
import com.nanzhufeng.ai.domain.RestoreLatestCaptureDraftUseCase
import com.nanzhufeng.ai.domain.SaveKnowledgeItemUseCase
import com.nanzhufeng.ai.domain.SaveKnowledgeResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P2LocalDataContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var drafts: RoomCaptureDraftRepository
    private lateinit var knowledge: RoomKnowledgeRepository
    private lateinit var attachments: AndroidPrivateAttachmentStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        drafts = RoomCaptureDraftRepository(database)
        knowledge = RoomKnowledgeRepository(database)
        attachments = AndroidPrivateAttachmentStore(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `private import copies bytes and validates hash on read`() {
        val sourceBytes = validPngBytes()
        val imported = attachments.import(AttachmentImportRequest(
            input = ByteArrayInputStream(sourceBytes),
            mimeType = "image/png",
            displayName = "fixture.png",
        )) as AttachmentImportResult.Imported

        val restored = attachments.read(imported.attachment) as AttachmentReadResult.Content

        assertTrue(imported.attachment.reference.startsWith("attachments/v1/"))
        assertEquals(sourceBytes.size.toLong(), imported.attachment.byteCount)
        assertNotNull(imported.attachment.sha256)
        assertArrayEquals(sourceBytes, restored.bytes)
    }

    @Test
    fun `verified egress stream rechecks the private copy and exposes no file path`() {
        val sourceBytes = validPngBytes()
        val imported = attachments.import(AttachmentImportRequest(
            input = ByteArrayInputStream(sourceBytes), mimeType = "image/png", displayName = "fixture.png",
        )) as AttachmentImportResult.Imported

        val opened = attachments.openVerified(imported.attachment) as AttachmentOpenResult.Opened
        val streamed = opened.open().use { it.readBytes() }
        assertEquals(sourceBytes.size.toLong(), opened.byteCount)
        assertArrayEquals(sourceBytes, streamed)

        assertTrue(attachments.deletePrivateCopy(imported.attachment))
        assertTrue(attachments.openVerified(imported.attachment) is AttachmentOpenResult.Rejected)
    }

    @Test
    fun `room round trip keeps evidence attachment and knowledge provenance`() {
        val attachment = attachments.import(AttachmentImportRequest(
            input = ByteArrayInputStream(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)),
            mimeType = "image/jpeg",
            displayName = "fixture.jpg",
        )).let { result -> (result as AttachmentImportResult.Imported).attachment }
        val draft = CaptureDraftFactory(clock).fromImage(attachment, "gallery")

        drafts.save(draft)
        val restoredDraft = drafts.findById(draft.id)

        assertNotNull(restoredDraft)
        assertEquals(CaptureSourceType.IMAGE, restoredDraft!!.sourceEvidence.single().sourceType)
        assertEquals("gallery", restoredDraft.sourceEvidence.single().sourceReference)
        assertEquals(attachment.sha256, restoredDraft.attachments.single().sha256)

        val model = ModelDescriptor("mock-vision", "Mock 视觉", ModelCapabilities(true, true, false))
        val task = AiTask(
            id = AiTaskId.new(),
            draftId = draft.id,
            providerId = ProviderId.MOCK,
            model = model,
            harness = HarnessProfile("capture-organize", 1),
            consent = EgressConsent(clock.instant(), ProviderId.MOCK, model.id, draft.egressFingerprint()),
            createdAt = clock.instant(),
        )
        val run = RunAiTaskUseCase(MockAiTaskRunner(clock), RoomInvocationRepository(database), clock)
            .execute(task, draft) as AiTaskRunResult.Success
        assertEquals(InvocationStatus.SUCCEEDED, run.invocation.status)

        val saved = SaveKnowledgeItemUseCase(knowledge, clock)
            .execute(run.candidate, run.invocation, task, draft, userConfirmed = true) as SaveKnowledgeResult.Saved
        val restoredKnowledge = knowledge.findById(saved.item.id)
        val snapshot = ExportKnowledgeSnapshotUseCase(knowledge, clock).execute()

        assertNotNull(restoredKnowledge)
        assertEquals(run.invocation.id, restoredKnowledge!!.provenance.invocationId)
        assertEquals(attachment.reference, restoredKnowledge.attachments.single().reference)
        assertEquals(1, snapshot.protocolVersion)
        assertEquals(saved.item.id, snapshot.items.single().id)
        assertFalse(snapshot.items.single().attachments.single().reference.startsWith('/'))
    }

    @Test
    fun `external uri is not persisted as source evidence`() {
        val draft = CaptureDraftFactory(clock).fromAndroidTextShare("分享内容", "content://untrusted/provider/item")

        drafts.save(draft)
        val restored = drafts.findById(draft.id)

        assertNull(restored!!.sourceEvidence.single().sourceReference)
    }

    @Test
    fun `cancelled gallery selection does not create or replace draft`() {
        val existing = CaptureDraftFactory(clock).fromManualText("保留现有草稿")
        drafts.save(existing)
        val useCase = CaptureGalleryImageUseCase(attachments, CaptureDraftFactory(clock), drafts)

        val result = useCase.execute(null)

        assertEquals(CaptureGalleryImageResult.Cancelled, result)
        assertEquals(existing.id, drafts.findLatest()!!.id)
    }

    @Test
    fun `unsupported gallery content is rejected without persisting draft`() {
        val useCase = CaptureGalleryImageUseCase(attachments, CaptureDraftFactory(clock), drafts)

        val result = useCase.execute(GalleryImageSelection(
            input = ByteArrayInputStream("not-an-image".encodeToByteArray()),
            mimeType = "text/plain",
            displayName = "note.txt",
        ))

        assertEquals(
            CaptureGalleryImageResult.Rejected(com.nanzhufeng.ai.domain.AiTaskError.AttachmentUnsupportedType),
            result,
        )
        assertNull(drafts.findLatest())
    }

    @Test
    fun `gallery selection persists private draft and restores without external uri`() {
        val bytes = validPngBytes()
        val useCase = CaptureGalleryImageUseCase(attachments, CaptureDraftFactory(clock), drafts)

        val saved = useCase.execute(GalleryImageSelection(
            input = ByteArrayInputStream(bytes),
            mimeType = "image/png",
            displayName = "gallery.png",
        )) as CaptureGalleryImageResult.Saved
        val restored = RestoreLatestCaptureDraftUseCase(attachments, RoomCaptureDraftRepository(database)).execute()
            as RestoreCaptureDraftResult.RestoredImage

        assertEquals("android-photo-picker", saved.draft.sourceEvidence.single().sourceReference)
        assertFalse(saved.draft.attachments.single().reference.startsWith("content://"))
        assertEquals(saved.draft.id, restored.draft.id)
        assertTrue(restored.previewBytes.isNotEmpty())
        assertFalse(restored.previewBytes.contentEquals(bytes))
    }

    @Test
    fun `manual blank text is rejected and leaves existing draft unchanged`() {
        val existing = CaptureDraftFactory(clock).fromManualText("保留现有草稿")
        drafts.save(existing)
        val useCase = CaptureTextDraftUseCase(CaptureDraftFactory(clock), drafts)

        val result = useCase.execute(CaptureTextInput("  ", CaptureSourceType.MANUAL_TEXT))

        assertEquals(CaptureTextDraftResult.Rejected(com.nanzhufeng.ai.domain.AiTaskError.CaptureTextBlank), result)
        assertEquals(existing.id, drafts.findLatest()!!.id)
    }

    @Test
    fun `text draft restores as text and does not try to read an image attachment`() {
        val textDraft = CaptureTextDraftUseCase(CaptureDraftFactory(clock), drafts)
            .execute(CaptureTextInput("可恢复的本地文本", CaptureSourceType.MANUAL_TEXT)) as CaptureTextDraftResult.Saved

        val restored = RestoreLatestCaptureDraftUseCase(attachments, drafts).execute()

        assertEquals(textDraft.draft, (restored as RestoreCaptureDraftResult.RestoredText).draft)
    }

    private fun validPngBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(0xFF2E7D32.toInt())
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `android text share adapter keeps only a sanitized package evidence label`() {
        val result = AndroidTextShareAdapter().read(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "从系统分享的文本")
            putExtra(Intent.EXTRA_REFERRER_NAME, "android-app://com.example.sender")
        }) as AndroidTextShareReadResult.Accepted

        val saved = CaptureTextDraftUseCase(CaptureDraftFactory(clock), drafts).execute(result.input)
            as CaptureTextDraftResult.Saved

        assertEquals(CaptureSourceType.ANDROID_TEXT_SHARE, saved.draft.sourceEvidence.single().sourceType)
        assertEquals("com.example.sender", saved.draft.sourceEvidence.single().sourceReference)
        assertEquals(saved.draft.id, drafts.findLatest()!!.id)
    }

    @Test
    fun `duplicate text share intent is consumed once across recreated activity gate`() {
        val adapter = AndroidTextShareAdapter()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "重复生命周期文本")
        }
        val firstGate = AndroidTextShareIntentGate()
        val first = firstGate.consume(adapter.read(intent))
        val recreatedGate = AndroidTextShareIntentGate(firstGate.savedFingerprint())

        assertNotNull(first)
        assertNull(firstGate.consume(adapter.read(intent)))
        assertNull(recreatedGate.consume(adapter.read(intent)))
    }
}
