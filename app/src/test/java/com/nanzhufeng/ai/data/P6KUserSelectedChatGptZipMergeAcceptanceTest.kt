package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipImportCommitStore
import com.nanzhufeng.ai.data.local.RoomP6KZipImportTaskRepository
import com.nanzhufeng.ai.domain.ConversationListScope
import com.nanzhufeng.ai.domain.ConversationImportSource
import com.nanzhufeng.ai.domain.ConversationManagementDomain
import com.nanzhufeng.ai.domain.ManageP6KChatGptZipImportUseCase
import com.nanzhufeng.ai.domain.P6KChatGptZipCandidateMapper
import com.nanzhufeng.ai.domain.P6KZipImportItem
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipTaskId
import com.nanzhufeng.ai.domain.P6KZipTaskStatus
import com.nanzhufeng.ai.domain.ThirdPartyZipInventoryPolicy
import com.nanzhufeng.ai.domain.ThirdPartyZipInventoryResult
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import com.nanzhufeng.ai.domain.SearchConversationsUseCase
import com.nanzhufeng.ai.domain.ConversationSearchProjection
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Opt-in real-package acceptance: callers provide explicitly selected ZIP paths as JVM properties.
 * It reads no conversation body into test output and never copies ZIP assets or touches a device.
 */
@RunWith(RobolectricTestRunner::class)
class P6KUserSelectedChatGptZipMergeAcceptanceTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_780_000_000_000), ZoneOffset.UTC)

    @Test fun `older selected ChatGPT export then newer selected cumulative export deduplicates and preserves navigation search and task history`() {
        val old = File(selectedPath("nanfeng.ai.p6k.oldZip", "NANFENG_AI_P6K_OLD_ZIP"))
        val newer = File(selectedPath("nanfeng.ai.p6k.newZip", "NANFENG_AI_P6K_NEW_ZIP"))
        assumeTrue("requires explicitly selected old and newer ZIP paths", old.isFile && newer.isFile)
        val oldMapped = map(old); val newMapped = map(newer)
        val oldCandidates = oldMapped.items.mapNotNull(P6KZipImportItem::candidate)
        val newCandidates = newMapped.items.mapNotNull(P6KZipImportItem::candidate)
        val oldBySource = oldCandidates.associateBy { it.sourceConversationId }
        val newBySource = newCandidates.associateBy { it.sourceConversationId }
        val shared = oldBySource.keys intersect newBySource.keys
        val changed = shared.filter { oldBySource.getValue(it).contentHash != newBySource.getValue(it).contentHash }.toSet()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val tasks = RoomP6KZipImportTaskRepository(database); val conversations = RoomConversationRepository(database)
            val imports = ManageP6KChatGptZipImportUseCase(tasks, RoomP6KZipImportCommitStore(database, conversations), clock)
            val oldTask = task("old-selected-package", old, oldMapped.items); tasks.save(oldTask)
            val importedOld = imports.importAll(oldTask.id)
            val oldConversationIds = importedOld.items.mapNotNull { item ->
                item.candidate?.let { candidate -> item.conversationId?.let { candidate.sourceConversationId to it } }
            }.toMap()
            val newTask = task("new-selected-package", newer, newMapped.items); tasks.save(newTask)
            val importedNew = imports.importAll(newTask.id)

            val importedCandidateCount = importedNew.items.count { it.candidate != null && it.conversationId != null }
            val terminalSummary = importedNew.items.groupingBy { it.status.name + ":" + (it.failure ?: "NONE") }.eachCount()
            val oldTerminalSummary = importedOld.items.groupingBy { it.status.name + ":" + (it.failure ?: "NONE") }.eachCount()
            assertEquals("old statuses=$oldTerminalSummary new candidates=$importedCandidateCount task statuses=$terminalSummary", newBySource.size, conversations.listActive().size)
            assertEquals("old statuses=$oldTerminalSummary new task statuses=$terminalSummary", newBySource.size, importedCandidateCount)
            assertEquals(shared, importedNew.items.mapNotNull { it.candidate?.sourceConversationId }.filter { it in shared }.toSet())
            importedNew.items.filter { it.candidate != null }.forEach { item ->
                val candidate = requireNotNull(item.candidate); val conversationId = requireNotNull(item.conversationId)
                oldConversationIds[candidate.sourceConversationId]?.let { assertEquals(it, conversationId) }
                assertEquals(candidate.messages.size, conversations.findById(conversationId)!!.nodes.size)
            }
            assertTrue(changed.isNotEmpty())
            assertEquals(newBySource.size, database.p6kZipImportTaskDao().provenanceForTask(newTask.id.value).size)
            assertEquals(2, tasks.list().count { it.status == P6KZipTaskStatus.COMPLETED })
            val ordered = conversations.listActive().map { it.updatedAt.toEpochMilli() }
            assertEquals(ordered.sortedDescending(), ordered)
            val safeQuery = newCandidates.first().title.lowercase().trim()
            assumeTrue(safeQuery.isNotBlank())
            assertTrue(conversations.searchLocalIndex(safeQuery, ConversationListScope.ACTIVE).isNotEmpty())
            val searched = SearchConversationsUseCase(conversations, ConversationSearchProjection(ConversationManagementDomain(clock))).execute(safeQuery, ConversationListScope.ACTIVE)
            assertTrue(searched.isNotEmpty())
            assertTrue(searched.any { it.importSource == ConversationImportSource.CHATGPT_ZIP })
        } finally { database.close() }
    }

    private fun map(file: File): com.nanzhufeng.ai.domain.P6KChatGptZipMappingResult.Mapped {
        val inventory = ThirdPartyZipInventoryPolicy.inspect(file) as ThirdPartyZipInventoryResult.InventoriedUnsupported
        // The user-flow assertion exercises conversation merge only.  Inventory has already
        // checked every archive entry and limit; pass just provider JSON entries so this repeatable
        // acceptance test does not re-hash several GiB of intentionally unowned media.
        return P6KChatGptZipCandidateMapper().map(ThirdPartyZipProvider.CHATGPT, file, inventory.entries.filter { it.mimeType == "application/json" }) as com.nanzhufeng.ai.domain.P6KChatGptZipMappingResult.Mapped
    }

    private fun task(packageHash: String, file: File, items: List<P6KZipImportItem>) =
        P6KZipImportTask(P6KZipTaskId.new(), ThirdPartyZipProvider.CHATGPT, "selected.zip", file.length(), packageHash, P6KZipTaskStatus.AWAITING_CONFIRMATION, createdAt = clock.instant(), updatedAt = clock.instant(), items = items)

    private fun selectedPath(property: String, environment: String): String =
        System.getProperty(property).orEmpty().ifBlank { System.getenv(environment).orEmpty() }
}
