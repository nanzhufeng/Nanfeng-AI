package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipImportCommitStore
import com.nanzhufeng.ai.data.local.RoomP6KZipImportTaskRepository
import com.nanzhufeng.ai.domain.ManageP6KChatGptZipImportUseCase
import com.nanzhufeng.ai.domain.P6KChatGptZipCandidateMapper
import com.nanzhufeng.ai.domain.P6KZipCandidateFailure
import com.nanzhufeng.ai.domain.P6KZipImportItem
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipItemId
import com.nanzhufeng.ai.domain.P6KZipItemStatus
import com.nanzhufeng.ai.domain.P6KZipTaskId
import com.nanzhufeng.ai.domain.P6KZipTaskStatus
import com.nanzhufeng.ai.domain.P6K_CHATGPT_ZIP_FORMAT_VERSION
import com.nanzhufeng.ai.domain.ThirdPartyZipInventoryPolicy
import com.nanzhufeng.ai.domain.ThirdPartyZipInventoryResult
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P6KChatGptZipCommitRoomContractsTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_002_000), ZoneOffset.UTC)
    private val conversationsJson = """[{"id":"c-1","title":"Imported","create_time":1700000000,"update_time":1700000001,"mapping":{"m-1":{"parent":null,"children":["m-2"],"message":{"author":{"role":"user"},"content":{"parts":["hello"]},"create_time":1700000000}},"m-2":{"parent":"m-1","children":[],"message":{"author":{"role":"assistant"},"content":{"parts":["world"]},"create_time":1700000001}}}}]"""

    @Test fun `synthetic ZIP direct import survives database reopen with its real conversation tree`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p6k-k2-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val file = zip("conversations.json" to conversationsJson, "assets/image.png" to "pixels")
        try {
            val inventory = ThirdPartyZipInventoryPolicy.inspect(file) as ThirdPartyZipInventoryResult.InventoriedUnsupported
            val mapped = P6KChatGptZipCandidateMapper().map(ThirdPartyZipProvider.CHATGPT, file, inventory.entries) as com.nanzhufeng.ai.domain.P6KChatGptZipMappingResult.Mapped
            val task = P6KZipImportTask(P6KZipTaskId.new(), ThirdPartyZipProvider.CHATGPT, "synthetic.zip", file.length(), "package-hash", P6KZipTaskStatus.AWAITING_CONFIRMATION, formatVersion = P6K_CHATGPT_ZIP_FORMAT_VERSION, createdAt = clock.instant(), updatedAt = clock.instant(), items = mapped.items, assets = mapped.assets)
            val database = open(context, name); val tasks = RoomP6KZipImportTaskRepository(database); val conversations = RoomConversationRepository(database); tasks.save(task)
            assertTrue(conversations.listActive().isEmpty())
            val imports = ManageP6KChatGptZipImportUseCase(tasks, RoomP6KZipImportCommitStore(database, conversations), clock)
            val confirmed = imports.importAll(task.id)
            val conversationId = requireNotNull(confirmed.items.single().conversationId)
            assertEquals(P6KZipItemStatus.CONFIRMED, confirmed.items.single().status); assertEquals(P6KZipTaskStatus.COMPLETED, confirmed.status)
            assertEquals(listOf("hello", "world"), conversations.findById(conversationId)!!.nodes.map { (it.content.single() as com.nanzhufeng.ai.domain.ContentBlock.Text).text })
            database.close()
            val reopened = open(context, name); val reopenedTasks = RoomP6KZipImportTaskRepository(reopened); val reopenedConversations = RoomConversationRepository(reopened)
            assertEquals(conversationId, reopenedTasks.find(task.id)!!.items.single().conversationId)
            assertNotNull(reopenedConversations.findById(conversationId)); reopened.close()
        } finally { file.delete(); context.deleteDatabase(name) }
    }

    @Test fun `reimport conflict leaves the second item failed and writes no second conversation`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val tasks = RoomP6KZipImportTaskRepository(database); val conversations = RoomConversationRepository(database); val imports = ManageP6KChatGptZipImportUseCase(tasks, RoomP6KZipImportCommitStore(database, conversations), clock)
            val candidate = sampleCandidate(); val first = task("same-package", candidate); tasks.save(first); imports.confirm(first.id, first.items.single().id)
            val conflictingCandidate = candidate.copy(contentHash = "different-content-hash")
            val second = task("same-package", conflictingCandidate); tasks.save(second); val result = imports.confirm(second.id, second.items.single().id)
            assertEquals(P6KZipItemStatus.FAILED, result.items.single().status); assertEquals("CONFLICT_REIMPORT", result.items.single().failure); assertEquals(1, conversations.listActive().size)
            assertNull(result.items.single().conversationId)
        } finally { database.close() }
    }

    private fun task(packageHash: String, candidate: com.nanzhufeng.ai.domain.ChatGptImportCandidate) = P6KZipImportTask(P6KZipTaskId.new(), ThirdPartyZipProvider.CHATGPT, "synthetic.zip", 1, packageHash, P6KZipTaskStatus.AWAITING_CONFIRMATION, createdAt = clock.instant(), updatedAt = clock.instant(), items = listOf(P6KZipImportItem(P6KZipItemId.new(), 0, candidate)))
    private fun sampleCandidate(): com.nanzhufeng.ai.domain.ChatGptImportCandidate {
        val file = zip("conversations.json" to conversationsJson)
        return try { val inventory = ThirdPartyZipInventoryPolicy.inspect(file) as ThirdPartyZipInventoryResult.InventoriedUnsupported; ((P6KChatGptZipCandidateMapper().map(ThirdPartyZipProvider.CHATGPT, file, inventory.entries) as com.nanzhufeng.ai.domain.P6KChatGptZipMappingResult.Mapped).items.single().candidate!!) } finally { file.delete() }
    }
    private fun open(context: Context, name: String) = Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
    private fun zip(vararg entries: Pair<String, String>): File = File.createTempFile("p6k-k2-", ".zip").also { file -> ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } } }
}
