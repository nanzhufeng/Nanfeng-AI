package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomKnowledgeRepository
import com.nanzhufeng.ai.data.local.RoomMarkdownImportTaskRepository
import com.nanzhufeng.ai.domain.*
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Real Room plus app-private files; the cleanup failure is a deterministic File.delete seam only. */
@RunWith(RobolectricTestRunner::class)
class P5CTaskDeletionRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var context: Context
    private lateinit var db: NanfengAiDatabase
    private lateinit var tasks: RoomMarkdownImportTaskRepository
    private lateinit var imports: ManageMarkdownImportUseCase

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        tasks = RoomMarkdownImportTaskRepository(db)
        imports = ManageMarkdownImportUseCase(tasks, AndroidMarkdownPrivateAssetStore(context), MarkdownKnowledgeAdapter(KnowledgeDomain(clock)), ManageKnowledgeUseCase(KnowledgeDomain(clock), RoomKnowledgeRepository(db, clock)), clock)
    }
    @After fun close() { db.close(); File(context.filesDir, "p5c-pending-delete").deleteRecursively(); File(context.filesDir, "markdown-import-assets/v1").deleteRecursively(); File(context.filesDir, "p6k-zip-import/v1").deleteRecursively() }

    @Test fun `privacy inventory keeps aggregate import facts without exposing raw ZIP bytes`() {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO p6k_zip_import_tasks (id, provider, displayName, byteCount, packageHash, status, failure, formatVersion, createdAtEpochMs, updatedAtEpochMs) VALUES ('p5c-zip', 'CHATGPT', 'archive.zip', 4096, 'hash', 'COMPLETED', NULL, '1', 1, 1)")
        sql.execSQL("INSERT INTO p6k_zip_import_provenance (conversationId, taskId, itemId, sourceConversationId, packageHash, contentHash, importedAtEpochMs, adapterId, adapterVersion) VALUES ('p5c-conversation', 'p5c-zip', 'p5c-item', 'p5c-source', 'hash', 'content', 1, 'test', 1)")
        sql.execSQL("INSERT INTO p6k_zip_asset_candidates (taskId, entryName, sha256, byteCount, mimeType, role) VALUES ('p5c-zip', 'asset', 'asset-hash', 12, 'image/png', 'UNMAPPED')")
        val archive = File(context.filesDir, "p6k-zip-import/v1/archives/p5c-zip.zip").also { it.parentFile!!.mkdirs(); it.writeBytes(ByteArray(4096)) }

        val inventory = AndroidPrivacyDataManager(context, db, "test").inventory()
        val values = inventory.aggregates.associateBy { it.key }

        assertEquals(1L, values.getValue("zip_import_batches").count)
        assertEquals(1L, values.getValue("zip_imported_conversations").count)
        assertEquals(1L, values.getValue("zip_pending_media").count)
        assertFalse(values.containsKey("zip_archives"))
        assertEquals(1L, inventory.importedZipCleanup.originalPackageCount)
        assertTrue(archive.isFile)
    }

    @Test fun `one selected cancelled fixture deletes without touching another historical task`() {
        val historical = cancelled("history.md")
        val fixture = cancelled("p5c-visible-fixture.md")
        val manager = AndroidPrivacyDataManager(context, db, "test")
        val first = manager.preview(PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS)
        assertEquals(2, first.taskCandidates.size)
        assertTrue(first.taskCandidates.all { it.privateAssetCount == 1L && it.safeIdSummary.length == 12 })
        val selected = setOf(fixture.id.value)
        val selectedPreview = manager.preview(PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS, selected)
        val result = manager.delete(PrivacyDeletionRequest(PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS, selectedPreview.fingerprint, selectedTaskIds = selected))
        assertTrue(result is PrivacyDeletionResult.Completed)
        assertNull(tasks.find(fixture.id))
        assertNotNull(tasks.find(historical.id))
        assertFalse(File(context.filesDir, "markdown-import-assets/v1/${fixture.id.value}").exists())
        assertTrue(File(context.filesDir, "markdown-import-assets/v1/${historical.id.value}").exists())
        assertTrue(manager.retryFailedTaskDeletion() is PrivacyDeletionResult.Completed)
    }

    @Test fun `file cleanup partial deletes Room once and explicit retry only clears quarantine`() {
        val fixture = cancelled("p5c-partial-fixture.md")
        val selected = setOf(fixture.id.value)
        val failing = AndroidPrivacyDataManager(context, db, "test") { false }
        val preview = failing.preview(PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS, selected)
        val partial = failing.delete(PrivacyDeletionRequest(PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS, preview.fingerprint, selectedTaskIds = selected))
        assertTrue(partial is PrivacyDeletionResult.Partial)
        assertNull(tasks.find(fixture.id))
        assertTrue(File(context.filesDir, "p5c-pending-delete/tasks").exists())
        val retried = AndroidPrivacyDataManager(context, db, "test").retryFailedTaskDeletion()
        assertTrue(retried is PrivacyDeletionResult.Completed)
        assertFalse(File(context.filesDir, "p5c-pending-delete/tasks").exists())
    }

    private fun cancelled(name: String): MarkdownImportTask = imports.cancel(imports.select(name, "text/markdown", "# $name\nnon-sensitive fixture".toByteArray()).id)
}
