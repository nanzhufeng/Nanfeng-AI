package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.data.local.RoomP6KProfilePersonalizationSettingsOwner
import com.nanzhufeng.ai.data.local.RoomP6KZipImportCommitStore
import com.nanzhufeng.ai.data.local.RoomP6KZipImportTaskRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipManualAssetLinkOwner
import com.nanzhufeng.ai.data.local.RoomP6KZipMappedAssetLinkOwner
import com.nanzhufeng.ai.data.local.RoomPrivateAttachmentRepository
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentImportRequest
import com.nanzhufeng.ai.domain.AttachmentImportResult
import com.nanzhufeng.ai.domain.AttachmentPdfPage
import com.nanzhufeng.ai.domain.AttachmentPdfPageResult
import com.nanzhufeng.ai.domain.AttachmentReadResult
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.AttachmentThumbnail
import com.nanzhufeng.ai.domain.AttachmentThumbnailResult
import com.nanzhufeng.ai.domain.AttachmentVideoMetadata
import com.nanzhufeng.ai.domain.AttachmentVideoMetadataResult
import com.nanzhufeng.ai.domain.AttachmentVideoPreview
import com.nanzhufeng.ai.domain.AttachmentVideoPreviewResult
import com.nanzhufeng.ai.domain.ConversationAttachmentPreviewProjection
import com.nanzhufeng.ai.domain.ManageP6KChatGptZipImportUseCase
import com.nanzhufeng.ai.domain.P6KZipAssetRole
import com.nanzhufeng.ai.domain.P6KZipImportCommitStore
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipManualAssetLinkOwner
import com.nanzhufeng.ai.domain.P6KZipManualAssetLinkResult
import com.nanzhufeng.ai.domain.P6KZipTaskId
import com.nanzhufeng.ai.domain.P6KZipTaskStatus
import com.nanzhufeng.ai.domain.PrivateAttachmentStore
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import com.nanzhufeng.ai.domain.toConversationReference
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * K8 regression: an archive candidate becomes a normal message attachment only after the user
 * chooses an imported target.  This deliberately uses no provider identifiers or real exports.
 */
@RunWith(RobolectricTestRunner::class)
class P6KZipManualAssetLinkRoomContractsTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_020_000L), ZoneOffset.UTC)

    @Test fun `synthetic archive links image video and PDF through the existing message and preview owners`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p6k-manual-media-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val fixture = archiveFixture()
        var database = open(context, name)
        try {
            val attachments = FixtureAttachmentStore()
            val intake = intake(database, context, attachments)
            val staged = fixture.inputStream().use { intake.stage(ThirdPartyZipProvider.CHATGPT, "fixture.zip", "application/zip", it) }
            assertEquals(P6KZipTaskStatus.COMPLETED, staged.status)
            assertEquals(3, staged.assets.size)
            assertTrue(staged.assets.all { it.role == P6KZipAssetRole.UNMAPPED_REJECTED })

            val target = intake.manualLinkTargets(staged.id.value).first()
            val image = staged.assets.single { it.mimeType == "image/png" }
            val failed = intake.linkUnmappedAsset(staged.id.value, image.entryName, target.conversationId.value, "missing-message")
            assertEquals(P6KZipAssetRole.MANUAL_LINK_FAILED, failed.assets.single { it.entryName == image.entryName }.role)
            assertEquals(0, attachments.importCount)
            assertTrue(failed.assets.filterNot { it.entryName == image.entryName }.all { it.role == P6KZipAssetRole.UNMAPPED_REJECTED })

            var linked = failed
            for (asset in failed.assets) {
                linked = intake.linkUnmappedAsset(staged.id.value, asset.entryName, target.conversationId.value, target.messageId.value)
            }
            assertEquals(3, linked.assets.count { it.role == P6KZipAssetRole.MANUAL_LINKED })
            assertEquals(3, attachments.importCount)

            // A duplicate selection is a replay, never a second attachment block.
            val replay = intake.linkUnmappedAsset(staged.id.value, image.entryName, target.conversationId.value, target.messageId.value)
            assertEquals(3, replay.assets.count { it.role == P6KZipAssetRole.MANUAL_LINKED })
            assertEquals(3, attachments.importCount)

            val conversations = RoomConversationRepository(database)
            val targetNode = requireNotNull(conversations.findById(target.conversationId)).nodes.single { it.id == target.messageId }
            val references = targetNode.content.filterIsInstance<com.nanzhufeng.ai.domain.ContentBlock.Attachment>().map { it.attachment }
            assertEquals(listOf("application/pdf", "image/png", "video/mp4"), references.map { it.mimeType }.sorted())
            val projection = ConversationAttachmentPreviewProjection(RoomPrivateAttachmentRepository(database), attachments)
            val imageReference = references.single { it.mimeType == "image/png" }
            val videoReference = references.single { it.mimeType == "video/mp4" }
            val pdfReference = references.single { it.mimeType == "application/pdf" }
            assertNotNull(projection.project(imageReference)?.thumbnail)
            assertNotNull(projection.video(videoReference).poster)
            assertNotNull(projection.pdfPage(pdfReference, 1).page)
            assertTrue(linked.assets.all { asset -> database.p6kZipImportTaskDao().assetLinkReceipt(staged.id.value, asset.entryName) != null })

            database.close()
            database = open(context, name)
            val reopenedTasks = RoomP6KZipImportTaskRepository(database)
            val reopened = requireNotNull(reopenedTasks.find(staged.id))
            assertEquals(3, reopened.assets.count { it.role == P6KZipAssetRole.MANUAL_LINKED })
            val reopenedTarget = RoomConversationRepository(database).findById(target.conversationId)!!.nodes.single { it.id == target.messageId }
            assertEquals(3, reopenedTarget.content.filterIsInstance<com.nanzhufeng.ai.domain.ContentBlock.Attachment>().size)

            assertTrue(intake(database, context, attachments).cancel(staged.id.value))
            assertNull(RoomP6KZipImportTaskRepository(database).find(staged.id))
            assertTrue(RoomConversationRepository(database).listActive().isEmpty())
            assertTrue(staged.assets.all { asset -> database.p6kZipImportTaskDao().assetLinkReceipt(staged.id.value, asset.entryName) == null })
        } finally {
            database.close()
            fixture.delete()
            context.deleteDatabase(name)
        }
    }

    @Test fun `failed batch revoke retains the task and its archive recovery entry`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p6k-manual-revoke-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val database = open(context, name)
        try {
            val tasks = RoomP6KZipImportTaskRepository(database)
            val task = P6KZipImportTask(P6KZipTaskId.new(), ThirdPartyZipProvider.CHATGPT, "fixture.zip", 1, "fixture", P6KZipTaskStatus.COMPLETED, createdAt = clock.instant(), updatedAt = clock.instant())
            tasks.save(task)
            val rejectingCommits = object : P6KZipImportCommitStore {
                override fun confirm(task: P6KZipImportTask, item: com.nanzhufeng.ai.domain.P6KZipImportItem, importedAt: Instant) = error("not used")
                override fun skip(task: P6KZipImportTask, item: com.nanzhufeng.ai.domain.P6KZipImportItem, at: Instant) = false
                override fun deleteBatch(task: P6KZipImportTask, at: Instant) = false
            }
            val intake = AndroidP6KZipIntakeStore(
                context, tasks, ManageP6KChatGptZipImportUseCase(tasks, rejectingCommits, clock),
                RoomP6KProfilePersonalizationSettingsOwner(database), RoomP6KZipManualAssetLinkOwner(database, RoomConversationRepository(database)),
                RoomP6KZipMappedAssetLinkOwner(database, RoomConversationRepository(database)), FixtureAttachmentStore(), RoomConversationRepository(database), clock,
            )
            assertFalse(intake.cancel(task.id.value))
            assertNotNull(tasks.find(task.id))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun intake(database: NanfengAiDatabase, context: Context, attachments: FixtureAttachmentStore): AndroidP6KZipIntakeStore {
        val tasks = RoomP6KZipImportTaskRepository(database)
        val conversations = RoomConversationRepository(database)
        return AndroidP6KZipIntakeStore(
            context, tasks, ManageP6KChatGptZipImportUseCase(tasks, RoomP6KZipImportCommitStore(database, conversations), clock),
            RoomP6KProfilePersonalizationSettingsOwner(database), RoomP6KZipManualAssetLinkOwner(database, conversations), RoomP6KZipMappedAssetLinkOwner(database, conversations), attachments, conversations, clock,
        )
    }

    private fun open(context: Context, name: String) = Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()

    private fun archiveFixture(): File = File.createTempFile("p6k-manual-media-", ".zip").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("conversations.json"))
            zip.write("""[{"id":"fixture","title":"Fixture","create_time":1,"update_time":2,"mapping":{"message":{"parent":null,"message":{"author":{"role":"user"},"content":{"parts":["fixture"]}}}}}]""".toByteArray())
            zip.closeEntry()
            listOf(
                "assets/image.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1),
                "assets/video.mp4" to byteArrayOf(0, 0, 0, 0x20, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d, 1),
                "assets/document.pdf" to "%PDF-1.4\nfixture".toByteArray(),
            ).forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }

    private class FixtureAttachmentStore : PrivateAttachmentStore {
        private val bytes = linkedMapOf<AttachmentId, ByteArray>()
        var importCount = 0
            private set

        override fun import(request: AttachmentImportRequest): AttachmentImportResult {
            val value = request.input.readBytes()
            if (!magicMatches(request.mimeType, value)) return AttachmentImportResult.Rejected(AiTaskError.AttachmentUnsupportedType)
            importCount += 1
            val hash = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
            val asset = AttachmentReference("attachments/v1/$hash", request.mimeType, null, AttachmentId.new(), value.size.toLong(), hash)
            bytes[asset.id] = value
            return AttachmentImportResult.Imported(asset)
        }

        override fun read(attachment: AttachmentReference) = bytes[attachment.id]?.let(AttachmentReadResult::Content)
            ?: AttachmentReadResult.Rejected(AiTaskError.AttachmentNotReady)
        override fun thumbnail(attachment: AttachmentReference) = if (attachment.mimeType == "image/png") AttachmentThumbnailResult.Ready(AttachmentThumbnail(byteArrayOf(1), 1, 1)) else AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        override fun pdfPage(attachment: AttachmentReference, pageNumber: Int) = if (attachment.mimeType == "application/pdf") AttachmentPdfPageResult.Ready(AttachmentPdfPage(AttachmentThumbnail(byteArrayOf(1), 1, 1), pageNumber, 1)) else AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        override fun videoMetadata(attachment: AttachmentReference) = if (attachment.mimeType == "video/mp4") AttachmentVideoMetadataResult.Ready(AttachmentVideoMetadata(AttachmentThumbnail(byteArrayOf(1), 1, 1), 1_000)) else AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        override fun videoPreview(attachment: AttachmentReference) = if (attachment.mimeType == "video/mp4") AttachmentVideoPreviewResult.Ready(AttachmentVideoPreview(AttachmentThumbnail(byteArrayOf(1), 1, 1), 1_000, requireNotNull(bytes[attachment.id]))) else AttachmentVideoPreviewResult.Rejected(AiTaskError.AttachmentUnsupportedType)

        private fun magicMatches(mime: String, value: ByteArray) = when (mime) {
            "image/png" -> value.copyOfRange(0, minOf(value.size, 8)).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            "video/mp4" -> value.size >= 8 && String(value, 4, 4) == "ftyp"
            "application/pdf" -> value.size >= 5 && String(value, 0, 5) == "%PDF-"
            else -> false
        }
    }
}
