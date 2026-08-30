package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.P6KZipAssetOccurrenceReceiptEntity
import com.nanzhufeng.ai.data.local.P6KZipAssetLinkProvenanceEntity
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.data.local.RoomCaptureDraftRepository
import com.nanzhufeng.ai.data.local.RoomPrivateAttachmentRepository
import com.nanzhufeng.ai.data.local.ResumableAttachmentUploadEntity
import com.nanzhufeng.ai.data.local.TemporaryConversationAttachmentEntity
import com.nanzhufeng.ai.data.local.TemporaryConversationRecoveryEntity
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentImportRequest
import com.nanzhufeng.ai.domain.AttachmentImportResult
import com.nanzhufeng.ai.domain.AttachmentReadResult
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.AttachmentThumbnailResult
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.CaptureDraftFactory
import com.nanzhufeng.ai.domain.ConversationAttachmentReference
import com.nanzhufeng.ai.domain.ConversationListScope
import com.nanzhufeng.ai.domain.ConversationPurgeResult
import com.nanzhufeng.ai.domain.ConversationSearchCategory
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.DeletePersistedConversationAttachmentResult
import com.nanzhufeng.ai.domain.DeletePersistedConversationAttachmentUseCase
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.PrivateAttachmentStore
import com.nanzhufeng.ai.domain.PrivateAttachmentCleanupResult
import com.nanzhufeng.ai.domain.SearchConversationAttachmentsUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchAttachmentDeletionRoomContractsTest {
    private lateinit var database: NanfengAiDatabase
    private val clock = Clock.fixed(Instant.parse("2026-08-28T16:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NanfengAiDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `deleting search hits preserves messages and shared bytes until the final reference`() {
        val conversations = RoomConversationRepository(database)
        val assets = RoomPrivateAttachmentRepository(database)
        val privateStore = RecordingPrivateStore()
        val reference = ConversationAttachmentReference(
            id = AttachmentId("shared-attachment"),
            mimeType = "image/png",
            displayName = "shared.png",
            byteCount = 3,
            sha256 = "a".repeat(64),
        )
        assets.save(
            AttachmentReference(
                reference = "attachments/v1/${reference.sha256}.png",
                mimeType = reference.mimeType,
                displayName = reference.displayName,
                id = reference.id,
                byteCount = reference.byteCount,
                sha256 = reference.sha256,
            ),
        )
        val tree = ConversationTreeService(clock)
        val first = tree.append(
            tree.create("共享附件"),
            AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Attachment(reference))),
        )
        val saved = conversations.save(
            tree.append(first, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Attachment(reference)))),
        )
        val firstMessage = saved.nodes.first()
        database.p6kZipImportTaskDao().insertAssetOccurrenceReceipt(
            P6KZipAssetOccurrenceReceiptEntity(
                taskId = "zip-task",
                entryName = "shared.png",
                sourceConversationId = "source-conversation",
                sourceMessageId = "source-message",
                conversationId = saved.conversation.id.value,
                messageId = firstMessage.id.value,
                attachmentId = reference.id.value,
                linkedAtMs = clock.instant().toEpochMilli(),
            ),
        )
        database.p6kZipImportTaskDao().insertAssetLinkProvenance(
            P6KZipAssetLinkProvenanceEntity(
                attachmentId = reference.id.value,
                taskId = "zip-task",
                entryName = "shared.png",
                sha256 = reference.sha256,
                conversationId = saved.conversation.id.value,
                messageId = firstMessage.id.value,
                importedAtEpochMs = clock.instant().toEpochMilli(),
            ),
        )
        val search = SearchConversationAttachmentsUseCase(conversations)
        val delete = DeletePersistedConversationAttachmentUseCase(conversations, assets, privateStore, clock)

        val firstHit = search.browse(ConversationSearchCategory.IMAGE, ConversationListScope.ACTIVE)
            .first { it.messageNodeId == firstMessage.id }
        val firstResult = delete.execute(firstHit) as DeletePersistedConversationAttachmentResult.Removed

        assertEquals(1, firstResult.sharedReferenceCount)
        assertTrue(!firstResult.privateFileDeleted)
        assertTrue(privateStore.deleted.isEmpty())
        assertEquals(0, database.p6kZipImportTaskDao().assetOccurrenceReceiptCount("zip-task"))
        val afterFirst = conversations.findById(saved.conversation.id)!!
        assertEquals(2, afterFirst.nodes.size)
        assertEquals("附件已删除", (afterFirst.nodes.first().content.single() as ContentBlock.Text).text)
        assertNotNull(assets.findById(reference.id))

        val lastHit = search.browse(ConversationSearchCategory.IMAGE, ConversationListScope.ACTIVE).single()
        val lastResult = delete.execute(lastHit) as DeletePersistedConversationAttachmentResult.Removed

        assertEquals(0, lastResult.sharedReferenceCount)
        assertTrue(lastResult.privateFileDeleted)
        assertEquals(listOf(reference.id), privateStore.deleted)
        assertNull(assets.findById(reference.id))
        val afterLast = conversations.findById(saved.conversation.id)!!
        assertEquals(2, afterLast.nodes.size)
        assertEquals("附件已删除", (afterLast.nodes.last().content.single() as ContentBlock.Text).text)
        assertTrue(search.browse(ConversationSearchCategory.IMAGE, ConversationListScope.ACTIVE).isEmpty())
    }

    @Test
    fun `permanent conversation purge completes final attachment file cleanup`() {
        val privateStore = RecordingPrivateStore()
        val conversations = RoomConversationRepository(database, privateStore)
        val assets = RoomPrivateAttachmentRepository(database)
        val reference = ConversationAttachmentReference(
            id = AttachmentId("purged-conversation-attachment"),
            mimeType = "video/mp4",
            displayName = "removed.mp4",
            byteCount = 4,
            sha256 = "c".repeat(64),
        )
        assets.save(
            AttachmentReference(
                reference = "attachments/v1/${reference.sha256}.mp4",
                mimeType = reference.mimeType,
                displayName = reference.displayName,
                id = reference.id,
                byteCount = reference.byteCount,
                sha256 = reference.sha256,
            ),
        )
        val tree = ConversationTreeService(clock)
        val saved = conversations.save(
            tree.append(tree.create("永久删除"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Attachment(reference)))),
        )
        val deleted = conversations.save(
            saved.copy(conversation = saved.conversation.copy(deletedAt = clock.instant(), revision = saved.conversation.revision + 1)),
        )

        assertEquals(
            ConversationPurgeResult.Deleted,
            conversations.permanentlyDelete(deleted.conversation.id, deleted.conversation.revision),
        )
        assertEquals(listOf(reference.id), privateStore.deleted)
        assertNull(assets.findById(reference.id))
    }

    @Test
    fun `temporary chat and retryable upload references both prevent physical cleanup`() {
        val assets = RoomPrivateAttachmentRepository(database)
        val privateStore = RecordingPrivateStore()
        val attachment = AttachmentReference(
            reference = "attachments/v1/${"b".repeat(64)}.png",
            mimeType = "image/png",
            displayName = "still-shared.png",
            id = AttachmentId("still-shared"),
            byteCount = 3,
            sha256 = "b".repeat(64),
        )
        assets.save(attachment)
        database.temporaryConversationRecoveryDao().apply {
            upsert(TemporaryConversationRecoveryEntity("temporary", 1L, 1L, "", null, 1))
            insertAttachments(
                listOf(TemporaryConversationAttachmentEntity("temporary", "DRAFT", "draft", 0, attachment.id.value, "TEMPORARY_SESSION")),
            )
        }
        database.resumableAttachmentUploadDao().insert(
            ResumableAttachmentUploadEntity(
                uploadId = "upload",
                normalChatAttemptId = "attempt",
                attachmentId = attachment.id.value,
                providerId = "OPENROUTER",
                modelId = "model",
                gatewayId = "gateway",
                sha256 = attachment.sha256!!,
                byteCount = attachment.byteCount!!,
                gatewaySessionId = null,
                acknowledgedBytes = 0,
                status = "UNKNOWN",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
                safeErrorCode = "PROCESS_INTERRUPTED",
            ),
        )

        val retainedByBoth = assets.deleteIfUnreferenced(attachment.id, privateStore::deletePrivateCopy)
        assertEquals(PrivateAttachmentCleanupResult.Retained(2), retainedByBoth)
        database.temporaryConversationRecoveryDao().deleteAttachments("temporary")
        val retainedByUpload = assets.deleteIfUnreferenced(attachment.id, privateStore::deletePrivateCopy)
        assertEquals(PrivateAttachmentCleanupResult.Retained(1), retainedByUpload)

        database.resumableAttachmentUploadDao().transition(
            uploadId = "upload",
            expected = listOf("UNKNOWN"),
            next = "CANCELLED",
            gatewaySessionId = null,
            acknowledgedBytes = 0,
            updatedAtEpochMs = 2L,
            safeErrorCode = null,
        )
        assertEquals(
            PrivateAttachmentCleanupResult.DeleteFailed,
            assets.deleteIfUnreferenced(attachment.id) { false },
        )
        assertNotNull(assets.findById(attachment.id))
        assertEquals(
            PrivateAttachmentCleanupResult.Deleted,
            assets.deleteIfUnreferenced(attachment.id, privateStore::deletePrivateCopy),
        )
        assertEquals(listOf(attachment.id), privateStore.deleted)
    }

    @Test
    fun `legacy capture draft reference prevents attachment cleanup`() {
        val assets = RoomPrivateAttachmentRepository(database)
        val privateStore = RecordingPrivateStore()
        val attachment = AttachmentReference(
            reference = "attachments/v1/${"d".repeat(64)}.png",
            mimeType = "image/png",
            displayName = "capture.png",
            id = AttachmentId("capture-owned"),
            byteCount = 3,
            sha256 = "d".repeat(64),
        )
        assets.save(attachment)
        RoomCaptureDraftRepository(database).save(CaptureDraftFactory(clock).fromImage(attachment, "gallery"))

        assertEquals(
            PrivateAttachmentCleanupResult.Retained(1),
            assets.deleteIfUnreferenced(attachment.id, privateStore::deletePrivateCopy),
        )
        assertTrue(privateStore.deleted.isEmpty())
        assertNotNull(assets.findById(attachment.id))
    }

    private class RecordingPrivateStore : PrivateAttachmentStore {
        val deleted = mutableListOf<AttachmentId>()
        override fun import(request: AttachmentImportRequest): AttachmentImportResult = error("not used")
        override fun read(attachment: AttachmentReference): AttachmentReadResult = error("not used")
        override fun thumbnail(attachment: AttachmentReference): AttachmentThumbnailResult = error("not used")
        override fun deletePrivateCopy(attachment: AttachmentReference): Boolean = true.also { deleted += attachment.id }
    }
}
