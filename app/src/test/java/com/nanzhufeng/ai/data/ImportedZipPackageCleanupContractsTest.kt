package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.GlmOcrTaskEntity
import com.nanzhufeng.ai.data.local.PrivateAttachmentAssetEntity
import com.nanzhufeng.ai.data.local.P6KZipAssetLinkProvenanceEntity
import com.nanzhufeng.ai.data.local.P6KZipAssetOccurrenceReceiptEntity
import com.nanzhufeng.ai.data.local.RoomPrivateAttachmentRepository
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentOpenResult
import com.nanzhufeng.ai.domain.ImportedZipCleanupResult
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportedZipPackageCleanupContractsTest {
    private lateinit var context: Context
    private lateinit var database: NanfengAiDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() {
        database.close()
        File(context.filesDir, "p6k-zip-import/v1").deleteRecursively()
        File(context.filesDir, "attachments/v1").deleteRecursively()
        File(context.filesDir, "p5c-pending-delete/zip-archives").deleteRecursively()
    }

    @Test
    fun `inventory separates unreferenced physical bytes and uses current file length`() {
        val hash = "a".repeat(64)
        database.privateAttachmentAssetDao().insert(
            PrivateAttachmentAssetEntity(
                attachmentId = "actual-size-fixture",
                storageKey = "attachments/v1/$hash.png",
                mimeType = "image/png",
                displayName = "actual.png",
                byteCount = 9_999_999L,
                sha256 = hash,
            ),
        )
        val manager = AndroidPrivacyDataManager(context, database, "test")
        var aggregate = manager.inventory().aggregates.associateBy { it.key }.getValue("attachment_images")
        assertEquals(0L, aggregate.count)
        assertEquals(0L, aggregate.byteCount)

        val actual = File(context.filesDir, "attachments/v1/$hash.png").also {
            it.parentFile!!.mkdirs()
            it.writeBytes(ByteArray(321))
        }
        aggregate = manager.inventory().aggregates.associateBy { it.key }.getValue("attachment_images")
        assertEquals(0L, aggregate.count)
        assertEquals(0L, aggregate.byteCount)
        var orphaned = manager.inventory().aggregates.associateBy { it.key }.getValue("orphaned_attachment_files")
        assertEquals(1L, orphaned.count)
        assertEquals(actual.length(), orphaned.byteCount)

        database.glmOcrTaskDao().upsert(
            GlmOcrTaskEntity(
                taskId = "ocr-live-reference",
                sourceAttachmentId = "actual-size-fixture",
                resultAttachmentId = null,
                sourceDisplayName = "actual.png",
                sourceMimeType = "image/png",
                sourceByteCount = actual.length(),
                sourceSha256 = hash,
                status = "COMPLETED",
                requestId = null,
                pageCount = 1,
                inputTokens = null,
                outputTokens = null,
                costCnyMicros = null,
                safeErrorCode = null,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )
        aggregate = manager.inventory().aggregates.associateBy { it.key }.getValue("attachment_images")
        orphaned = manager.inventory().aggregates.associateBy { it.key }.getValue("orphaned_attachment_files")
        assertEquals(1L, aggregate.count)
        assertEquals(actual.length(), aggregate.byteCount)
        assertEquals(0L, orphaned.count)
        assertEquals(0L, orphaned.byteCount)
    }

    @Test
    fun `explicit orphan cleanup removes actual leftover bytes and catalog row`() {
        val hash = "b".repeat(64)
        val attachmentId = "orphan-video-fixture"
        val file = File(context.filesDir, "attachments/v1/$hash.mp4").also {
            it.parentFile!!.mkdirs()
            it.writeBytes(ByteArray(777))
        }
        database.privateAttachmentAssetDao().insert(
            PrivateAttachmentAssetEntity(attachmentId, "attachments/v1/$hash.mp4", "video/mp4", "deleted.mp4", 99_999L, hash),
        )
        insertCompletedTask("stale-delete-task")
        database.p6kZipImportTaskDao().insertAssetOccurrenceReceipt(
            P6KZipAssetOccurrenceReceiptEntity(
                taskId = "stale-delete-task",
                entryName = "deleted.mp4",
                sourceConversationId = "source",
                sourceMessageId = "source-message",
                conversationId = "already-deleted-conversation",
                messageId = "already-deleted-message",
                attachmentId = attachmentId,
                linkedAtMs = 1L,
            ),
        )
        database.p6kZipImportTaskDao().insertAssetLinkProvenance(
            P6KZipAssetLinkProvenanceEntity(
                attachmentId = attachmentId,
                taskId = "stale-delete-task",
                entryName = "deleted.mp4",
                sha256 = hash,
                conversationId = "already-deleted-conversation",
                messageId = "already-deleted-message",
                importedAtEpochMs = 1L,
            ),
        )
        val manager = AndroidPrivacyDataManager(context, database, "test")
        val preview = manager.preview(com.nanzhufeng.ai.domain.PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES)
        assertEquals(777L, preview.aggregates.single().byteCount)

        val result = manager.delete(
            com.nanzhufeng.ai.domain.PrivacyDeletionRequest(
                scope = com.nanzhufeng.ai.domain.PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES,
                previewFingerprint = preview.fingerprint,
            ),
        )

        assertTrue(result is com.nanzhufeng.ai.domain.PrivacyDeletionResult.Completed)
        assertFalse(file.exists())
        assertEquals(null, database.privateAttachmentAssetDao().findById(attachmentId))
        assertEquals(0L, manager.inventory().aggregates.associateBy { it.key }.getValue("orphaned_attachment_files").byteCount)
    }

    @Test
    fun `inventory counts archive backed item but attributes physical bytes to ZIP only once`() {
        val taskId = "zip-cleanup-fixture"
        val entryName = "mapped.png"
        val bytes = "verified imported image bytes".toByteArray()
        val hash = sha256(bytes)
        insertCompletedTask(taskId)
        val attachmentId = "attachment-fixture"
        database.privateAttachmentAssetDao().insert(
            PrivateAttachmentAssetEntity(
                attachmentId = attachmentId,
                storageKey = P6KZipArchiveAssetStorage.key(taskId, entryName),
                mimeType = "image/png",
                displayName = "导入图片.png",
                byteCount = bytes.size.toLong(),
                sha256 = hash,
            ),
        )
        val sql = database.openHelper.writableDatabase
        sql.execSQL(
            "INSERT INTO p6k_zip_asset_candidates (taskId,entryName,sha256,byteCount,mimeType,role,attachmentId) VALUES (?,?,?,?,?,?,?)",
            arrayOf(taskId, entryName, hash, bytes.size, "image/png", "SOURCE_MAPPED", attachmentId),
        )
        sql.execSQL(
            "INSERT INTO p6k_zip_asset_link_provenance (attachmentId,taskId,entryName,sha256,conversationId,messageId,importedAtEpochMs) VALUES (?,?,?,?,?,?,?)",
            arrayOf(attachmentId, taskId, entryName, hash, "conversation", "message", 1L),
        )
        sql.execSQL(
            "INSERT INTO p6k_zip_asset_candidates (taskId,entryName,sha256,byteCount,mimeType,role) VALUES (?,?,?,?,?,?)",
            arrayOf(taskId, "unmapped.dat", sha256("unused".toByteArray()), 6, "text/plain", "UNMAPPED"),
        )
        val archive = writeArchive(taskId, mapOf(entryName to bytes, "unmapped.dat" to "unused".toByteArray()))
        val manager = AndroidPrivacyDataManager(context, database, "test")

        val before = manager.inventory()
        val imageAggregate = before.aggregates.associateBy { it.key }.getValue("attachment_images")
        assertEquals(0L, imageAggregate.count)
        assertEquals(0L, imageAggregate.byteCount)
        val importFiles = before.aggregates.associateBy { it.key }.getValue("import_source_assets")
        assertTrue(importFiles.byteCount >= archive.length())
        assertFalse(before.aggregates.any { it.key == "zip_archives" })
        assertEquals(1L, before.importedZipCleanup.originalPackageCount)
        assertEquals(1L, before.importedZipCleanup.importedAttachmentCount)
        assertEquals(1L, before.importedZipCleanup.sourceDependentAttachmentCount)
        assertFalse(before.importedZipCleanup.allImportedAttachmentsManaged)

        val result = manager.cleanupImportedZipPackages()
        assertTrue(result.toString(), result is ImportedZipCleanupResult.Completed)
        result as ImportedZipCleanupResult.Completed
        assertEquals(1L, result.deletedPackageCount)
        assertEquals(1L, result.materializedAttachmentCount)
        assertFalse(archive.exists())
        assertEquals(1, database.p6kZipImportTaskDao().assets(taskId).size)

        val stored = requireNotNull(RoomPrivateAttachmentRepository(database).findById(AttachmentId(attachmentId)))
        assertTrue(stored.reference.startsWith("attachments/v1/"))
        val opened = AndroidPrivateAttachmentStore(context).openVerified(stored) as AttachmentOpenResult.Opened
        assertArrayEquals(bytes, opened.open().use { it.readBytes() })
        val after = manager.inventory().importedZipCleanup
        assertEquals(0L, after.originalPackageCount)
        assertEquals(0L, after.sourceDependentAttachmentCount)
        assertTrue(after.allImportedAttachmentsManaged)
        val managedImage = manager.inventory().aggregates.associateBy { it.key }.getValue("attachment_images")
        assertEquals(0L, managedImage.byteCount)
    }

    @Test
    fun `incomplete recovery keeps original ZIP and rejects cleanup`() {
        val taskId = "zip-blocked-fixture"
        insertCompletedTask(taskId)
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO p6k_zip_asset_recovery_jobs (taskId,state,totalOccurrences,linkedOccurrences,totalConversations,processedConversations,failedConversations,uniqueAssets,missingEntries,unattributedCandidates,sourceReferenceRecords,fallbackNamedAssets,inferredGeneratedImages,originLinkedLibraryImages,inferredLibraryImages,lastFailureKind,lastFailureAtMs,indexVersion,updatedAtMs) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(taskId, "PARTIAL", 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0, "INTERRUPTED", 1L, 1, 1L),
        )
        val archive = writeArchive(taskId, mapOf("pending.dat" to "pending".toByteArray()))

        val manager = AndroidPrivacyDataManager(context, database, "test")
        assertEquals(1L, manager.inventory().importedZipCleanup.blockedPackageCount)
        assertTrue(manager.cleanupImportedZipPackages() is ImportedZipCleanupResult.Rejected)
        assertTrue(archive.isFile)
    }

    @Test
    fun `cleanup repairs linked candidate whose private asset catalog row is missing`() {
        val taskId = "zip-missing-catalog"
        val entryName = "recovered.pdf"
        val attachmentId = "attachment-missing-catalog"
        val bytes = "verified historical PDF bytes".toByteArray()
        val hash = sha256(bytes)
        insertCompletedTask(taskId)
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO p6k_zip_asset_candidates (taskId,entryName,sha256,byteCount,mimeType,role,attachmentId) VALUES (?,?,?,?,?,?,?)",
            arrayOf(taskId, entryName, hash, bytes.size, "application/pdf", "SOURCE_MAPPED", attachmentId),
        )
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO p6k_zip_asset_catalog (taskId,entryName,sha256,byteCount,mimeType,displayName,attachmentId,missingInArchive,verificationState) VALUES (?,?,?,?,?,?,?,?,?)",
            arrayOf(taskId, entryName, hash, bytes.size, "application/pdf", "历史报告.pdf", attachmentId, 0, "LEGACY_BACKFILLED"),
        )
        val archive = writeArchive(taskId, mapOf(entryName to bytes))
        val manager = AndroidPrivacyDataManager(context, database, "test")

        assertEquals(1L, manager.inventory().importedZipCleanup.sourceDependentAttachmentCount)
        val result = manager.cleanupImportedZipPackages()

        assertTrue(result.toString(), result is ImportedZipCleanupResult.Completed)
        result as ImportedZipCleanupResult.Completed
        assertEquals(1L, result.materializedAttachmentCount)
        assertFalse(archive.exists())
        val stored = requireNotNull(RoomPrivateAttachmentRepository(database).findById(AttachmentId(attachmentId)))
        assertEquals("历史报告.pdf", stored.displayName)
        assertTrue(stored.reference.startsWith("attachments/v1/"))
        val opened = AndroidPrivateAttachmentStore(context).openVerified(stored) as AttachmentOpenResult.Opened
        assertArrayEquals(bytes, opened.open().use { it.readBytes() })
        assertEquals(0L, manager.inventory().importedZipCleanup.sourceDependentAttachmentCount)
    }

    @Test
    fun `cleanup recovers deduplicated attachment from retained ZIP when recorded source ZIP is gone`() {
        val missingTaskId = "zip-old-source"
        val retainedTaskId = "zip-current-source"
        val oldEntry = "old-image.png"
        val retainedEntry = "current-image.png"
        val attachmentId = "attachment-shared-source"
        val bytes = "same verified bytes in a newer retained export".toByteArray()
        val hash = sha256(bytes)
        insertCompletedTask(missingTaskId)
        insertCompletedTask(retainedTaskId)
        database.privateAttachmentAssetDao().insert(
            PrivateAttachmentAssetEntity(
                attachmentId = attachmentId,
                storageKey = P6KZipArchiveAssetStorage.key(missingTaskId, oldEntry),
                mimeType = "image/png",
                displayName = "重复图片.png",
                byteCount = bytes.size.toLong(),
                sha256 = hash,
            ),
        )
        val sql = database.openHelper.writableDatabase
        sql.execSQL(
            "INSERT INTO p6k_zip_asset_candidates (taskId,entryName,sha256,byteCount,mimeType,role,attachmentId) VALUES (?,?,?,?,?,?,?)",
            arrayOf(missingTaskId, oldEntry, hash, bytes.size, "image/png", "SOURCE_MAPPED", attachmentId),
        )
        sql.execSQL(
            "INSERT INTO p6k_zip_asset_candidates (taskId,entryName,sha256,byteCount,mimeType,role,attachmentId) VALUES (?,?,?,?,?,?,?)",
            arrayOf(retainedTaskId, retainedEntry, hash, bytes.size, "image/png", "SOURCE_MAPPED", attachmentId),
        )
        val retainedArchive = writeArchive(retainedTaskId, mapOf(retainedEntry to bytes))
        val manager = AndroidPrivacyDataManager(context, database, "test")

        // Two candidate rows are one stable attachment and must not be reported as two dependencies.
        assertEquals(1L, manager.inventory().importedZipCleanup.sourceDependentAttachmentCount)
        val result = manager.cleanupImportedZipPackages()

        assertTrue(result.toString(), result is ImportedZipCleanupResult.Completed)
        assertFalse(retainedArchive.exists())
        val stored = requireNotNull(RoomPrivateAttachmentRepository(database).findById(AttachmentId(attachmentId)))
        assertTrue(stored.reference.startsWith("attachments/v1/"))
        val opened = AndroidPrivateAttachmentStore(context).openVerified(stored) as AttachmentOpenResult.Opened
        assertArrayEquals(bytes, opened.open().use { it.readBytes() })
        assertEquals(0L, manager.inventory().importedZipCleanup.sourceDependentAttachmentCount)
    }

    @Test
    fun `cleanup keeps every retained ZIP while any linked attachment has no verified recovery source`() {
        val retainedTaskId = "zip-safe-retained"
        val missingTaskId = "zip-missing-required"
        insertCompletedTask(retainedTaskId)
        insertCompletedTask(missingTaskId)
        val retainedArchive = writeArchive(retainedTaskId, mapOf("unused.dat" to "unused".toByteArray()))
        val missingBytes = "required but unavailable".toByteArray()
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO p6k_zip_asset_candidates (taskId,entryName,sha256,byteCount,mimeType,role,attachmentId) VALUES (?,?,?,?,?,?,?)",
            arrayOf(
                missingTaskId,
                "required.pdf",
                sha256(missingBytes),
                missingBytes.size,
                "application/pdf",
                "SOURCE_MAPPED",
                "attachment-without-source",
            ),
        )
        val manager = AndroidPrivacyDataManager(context, database, "test")

        val result = manager.cleanupImportedZipPackages()

        assertTrue(result is ImportedZipCleanupResult.Partial)
        assertTrue(retainedArchive.isFile)
        assertEquals(1L, manager.inventory().importedZipCleanup.sourceDependentAttachmentCount)
    }

    private fun insertCompletedTask(taskId: String) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO p6k_zip_import_tasks (id,provider,displayName,byteCount,packageHash,status,failure,formatVersion,createdAtEpochMs,updatedAtEpochMs) VALUES (?,?,?,?,?,?,?,?,?,?)",
            arrayOf(taskId, "CHATGPT", "selected.zip", 100L, "package", "COMPLETED", null, "1", 1L, 1L),
        )
    }

    private fun writeArchive(taskId: String, entries: Map<String, ByteArray>): File {
        val archive = File(context.filesDir, "p6k-zip-import/v1/archives/$taskId.zip")
        archive.parentFile?.mkdirs()
        ZipOutputStream(archive.outputStream()).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return archive
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
