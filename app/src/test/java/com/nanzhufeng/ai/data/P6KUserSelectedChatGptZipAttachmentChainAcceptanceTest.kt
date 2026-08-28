package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipImportCommitStore
import com.nanzhufeng.ai.data.local.RoomP6KZipImportTaskRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipMappedAssetLinkOwner
import com.nanzhufeng.ai.data.local.RoomP6KZipAssetRecoveryJobRepository
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationListScope
import com.nanzhufeng.ai.domain.ConversationSearchCategory
import com.nanzhufeng.ai.domain.ManageP6KChatGptZipImportUseCase
import com.nanzhufeng.ai.domain.P6KChatGptZipAssetMapper
import com.nanzhufeng.ai.domain.P6KChatGptZipCandidateMapper
import com.nanzhufeng.ai.domain.P6KChatGptZipMappingResult
import com.nanzhufeng.ai.domain.P6KZipAssetCandidate
import com.nanzhufeng.ai.domain.P6KZipAssetMappingResult
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJob
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryState
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipTaskId
import com.nanzhufeng.ai.domain.P6KZipTaskStatus
import com.nanzhufeng.ai.domain.SearchConversationAttachmentsUseCase
import com.nanzhufeng.ai.domain.MessageTree
import com.nanzhufeng.ai.domain.ThirdPartyZipInventoryPolicy
import com.nanzhufeng.ai.domain.ThirdPartyZipInventoryResult
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Full real selected ZIP, isolated Room, no body or filename output and no device. */
@RunWith(RobolectricTestRunner::class)
class P6KUserSelectedChatGptZipAttachmentChainAcceptanceTest {
    @Test
    fun `selected real source package restores every officially owned attachment into Room`() {
        val archive = File(selectedPath("nanfeng.ai.p6k.newZip", "NANFENG_AI_P6K_NEW_ZIP"))
        assumeTrue("requires an explicitly selected newer ChatGPT ZIP", archive.isFile)
        val inventory = ThirdPartyZipInventoryPolicy.inspect(archive) as ThirdPartyZipInventoryResult.InventoriedUnsupported
        val conversationEntries = inventory.entries.filter { it.name.matches(Regex("^conversations(?:[-_]\\d+)?\\.json$")) }
        val textMapping = P6KChatGptZipCandidateMapper().map(ThirdPartyZipProvider.CHATGPT, archive, conversationEntries) as P6KChatGptZipMappingResult.Mapped
        val assets = inventory.entries.filter { it.mimeType != "application/json" }.map { entry ->
            P6KZipAssetCandidate(entry.name, sha256(entry.name), entry.uncompressedBytes, entry.mimeType)
        }
        val sourceMapping = (P6KChatGptZipAssetMapper().map(archive, assets) as P6KZipAssetMappingResult.Mapped).value
        val at = Instant.parse("2026-08-28T00:00:00Z")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val tasks = RoomP6KZipImportTaskRepository(database)
            val conversations = RoomConversationRepository(database)
            val staged = tasks.save(
                P6KZipImportTask(
                    P6KZipTaskId.new(), ThirdPartyZipProvider.CHATGPT, "selected.zip", archive.length(),
                    "selected-real-package", P6KZipTaskStatus.AWAITING_CONFIRMATION,
                    formatVersion = textMapping.formatVersion, createdAt = at, updatedAt = at,
                    items = textMapping.items, assets = assets,
                ),
            )
            val imported = ManageP6KChatGptZipImportUseCase(
                tasks,
                RoomP6KZipImportCommitStore(database, conversations),
                Clock.fixed(at, ZoneOffset.UTC),
            ).importAll(staged.id)
            val prepared = sourceMapping.assets.mapValues { (entryName, mapped) ->
                AttachmentReference(
                    P6KZipArchiveAssetStorage.key(staged.id.value, entryName), mapped.candidate.mimeType,
                    mapped.displayName, AttachmentId.new(), mapped.candidate.byteCount, mapped.candidate.sha256,
                )
            }
            val failures = mutableListOf<Throwable>()
            val recoveryConversationCount = sourceMapping.conversations.count { conversation ->
                conversation.currentPath.any { message -> message.entryNames.any(sourceMapping.assets::containsKey) }
            }
            val jobs = RoomP6KZipAssetRecoveryJobRepository(database)
            val initialJob = jobs.save(
                P6KZipAssetRecoveryJob(
                    taskId = staged.id,
                    state = P6KZipAssetRecoveryState.LINKING,
                    totalOccurrences = 1616,
                    totalConversations = recoveryConversationCount,
                    uniqueAssets = 1615,
                    updatedAtMs = at.toEpochMilli(),
                ),
            )
            val summary = RoomP6KZipMappedAssetLinkOwner(database, conversations, failures::add)
                .reconcileResumable(imported, sourceMapping, prepared, initialJob, at.plusSeconds(1))
            assertTrue(
                failures.joinToString(separator = "\n") { error ->
                    "${error.javaClass.name}: ${error.message.orEmpty()}\n${error.stackTraceToString()}"
                },
                failures.isEmpty(),
            )
            assertEquals(1616, summary.linkedAssetCount)
            val completedJob = requireNotNull(jobs.find(staged.id))
            assertEquals(P6KZipAssetRecoveryState.COMPLETED, completedJob.state)
            assertEquals(1616, completedJob.totalOccurrences)
            assertEquals(1616, completedJob.linkedOccurrences)
            assertEquals(0, completedJob.failedConversations)
            assertEquals(1615, database.p6kZipImportTaskDao().assetCatalogCount(staged.id.value))
            assertEquals(1616, database.p6kZipImportTaskDao().assetOccurrenceCount(staged.id.value))
            assertEquals(1616, database.p6kZipImportTaskDao().assetOccurrenceReceiptCount(staged.id.value))
            val currentPathReferences = conversations.snapshotsForSearch().flatMap { snapshot ->
                MessageTree(snapshot.conversation, snapshot.nodes).contextPath()
                    .flatMap { node -> node.content.filterIsInstance<ContentBlock.Attachment>().map { it.attachment } }
            }
            val currentPathClassCounts = currentPathReferences.groupingBy { reference ->
                when {
                    reference.mimeType.startsWith("image/") -> "image"
                    reference.mimeType.startsWith("video/") -> "video"
                    reference.mimeType.startsWith("audio/") -> "audio"
                    else -> "file"
                }
            }.eachCount()
            assertEquals(1616, currentPathReferences.size)
            assertEquals(mapOf("image" to 1417, "file" to 118, "video" to 75, "audio" to 6), currentPathClassCounts)
            val catalogue = SearchConversationAttachmentsUseCase(conversations)
            assertEquals(1417, catalogue.browse(ConversationSearchCategory.IMAGE, ConversationListScope.ALL).size)
            assertEquals(75, catalogue.browse(ConversationSearchCategory.VIDEO, ConversationListScope.ALL).size)
            assertEquals(6, catalogue.browse(ConversationSearchCategory.AUDIO, ConversationListScope.ALL).size)
            assertEquals(118, catalogue.browse(ConversationSearchCategory.FILE, ConversationListScope.ALL).size)
        } finally {
            database.close()
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun selectedPath(property: String, environment: String): String =
        System.getProperty(property).orEmpty().ifBlank { System.getenv(environment).orEmpty() }
}
