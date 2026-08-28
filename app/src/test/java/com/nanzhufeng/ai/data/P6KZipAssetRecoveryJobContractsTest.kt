package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipAssetRecoveryJobRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipImportCommitStore
import com.nanzhufeng.ai.data.local.RoomP6KZipImportTaskRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipMappedAssetLinkOwner
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.ChatGptImportCandidate
import com.nanzhufeng.ai.domain.ChatGptImportMessage
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.P6KZipAssetCandidate
import com.nanzhufeng.ai.domain.P6KZipAssetMapping
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJob
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryState
import com.nanzhufeng.ai.domain.P6KZipImportItem
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipItemId
import com.nanzhufeng.ai.domain.P6KZipMappedAsset
import com.nanzhufeng.ai.domain.P6KZipSourceConversationAssets
import com.nanzhufeng.ai.domain.P6KZipSourceMessageAssets
import com.nanzhufeng.ai.domain.P6KZipTaskId
import com.nanzhufeng.ai.domain.P6KZipTaskStatus
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import com.nanzhufeng.ai.domain.chatGptImportContentHash
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P6KZipAssetRecoveryJobContractsTest {
    @Test
    fun `linking failure preserves the previous conversation checkpoint and resumes without duplicates`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NanfengAiDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val at = Instant.parse("2026-08-28T06:00:00Z")
            val tasks = RoomP6KZipImportTaskRepository(database)
            val jobs = RoomP6KZipAssetRecoveryJobRepository(database)
            val conversations = RoomConversationRepository(database)
            val commit = RoomP6KZipImportCommitStore(database, conversations)
            val fixtures = (1..3).map { number -> fixture(number, at.plusSeconds(number.toLong())) }
            var task = tasks.save(
                P6KZipImportTask(
                    id = P6KZipTaskId.new(), provider = ThirdPartyZipProvider.CHATGPT,
                    displayName = "selected.zip", byteCount = 100, packageHash = "package",
                    status = P6KZipTaskStatus.AWAITING_CONFIRMATION,
                    createdAt = at, updatedAt = at,
                    items = fixtures.mapIndexed { index, fixture -> P6KZipImportItem(P6KZipItemId.new(), index, fixture.candidate) },
                    assets = fixtures.map(Fixture::asset),
                ),
            )
            task.items.forEach { item -> commit.confirm(requireNotNull(tasks.find(task.id)), item, at) }
            task = requireNotNull(tasks.find(task.id))
            val mapping = P6KZipAssetMapping(
                fixtures.map(Fixture::sourceConversation),
                fixtures.associate { it.asset.entryName to P6KZipMappedAsset(it.asset, it.displayName) },
            )
            val prepared = fixtures.associate { fixture ->
                fixture.asset.entryName to AttachmentReference(
                    P6KZipArchiveAssetStorage.key(task.id.value, fixture.asset.entryName),
                    fixture.asset.mimeType, fixture.displayName, AttachmentId.new(),
                    fixture.asset.byteCount, fixture.asset.sha256,
                )
            }
            val initial = jobs.save(
                P6KZipAssetRecoveryJob(
                    task.id, P6KZipAssetRecoveryState.LINKING,
                    totalOccurrences = 3, totalConversations = 3, uniqueAssets = 3,
                    updatedAtMs = at.toEpochMilli(),
                ),
            )
            val firstFailures = mutableListOf<Throwable>()
            val first = RoomP6KZipMappedAssetLinkOwner(
                database, conversations, firstFailures::add,
                beforeConversationCommit = { number, _ -> if (number == 2) error("TEST_LINKING_FAILURE") },
            ).reconcileResumable(task, mapping, prepared, initial, at.plusSeconds(1))

            assertEquals(1, first.failedConversationCount)
            val partial = requireNotNull(jobs.find(task.id))
            assertEquals(P6KZipAssetRecoveryState.PARTIAL, partial.state)
            assertEquals(1, partial.processedConversations)
            assertEquals(1, partial.linkedOccurrences)

            val resumed = RoomP6KZipMappedAssetLinkOwner(database, conversations)
                .reconcileResumable(requireNotNull(tasks.find(task.id)), mapping, prepared, partial, at.plusSeconds(2))
            val completed = requireNotNull(jobs.find(task.id))
            assertEquals(0, resumed.failedConversationCount)
            assertEquals(P6KZipAssetRecoveryState.COMPLETED, completed.state)
            assertEquals(3, completed.processedConversations)
            assertEquals(3, completed.linkedOccurrences)
            assertEquals(3, database.p6kZipImportTaskDao().assets(task.id.value).count { it.attachmentId != null })
            assertEquals(3, task.items.sumOf { item ->
                val conversationId = requireNotNull(requireNotNull(tasks.find(task.id)).items.single { it.id == item.id }.conversationId)
                requireNotNull(conversations.findById(conversationId)).nodes.flatMap { it.content }.filterIsInstance<ContentBlock.Attachment>().size
            })
        } finally {
            database.close()
        }
    }

    private fun fixture(number: Int, at: Instant): Fixture {
        val sourceConversationId = "source-$number"
        val sourceMessageId = "message-$number"
        val messages = listOf(ChatGptImportMessage(sourceMessageId, null, 0, MessageRole.USER, "text-$number", at, null))
        val candidate = ChatGptImportCandidate(
            sourceConversationId, "title-$number", at, at, messages,
            chatGptImportContentHash(sourceConversationId, messages),
        )
        val asset = P6KZipAssetCandidate(
            "file-$number.dat", number.toString().repeat(64), number.toLong(), "image/png",
            sourceConversationId = sourceConversationId, sourceMessageId = sourceMessageId,
        )
        return Fixture(
            candidate, asset, "image-$number.png",
            P6KZipSourceConversationAssets(
                sourceConversationId,
                listOf(P6KZipSourceMessageAssets(sourceMessageId, null, MessageRole.USER, at, true, listOf(asset.entryName))),
            ),
        )
    }

    private data class Fixture(
        val candidate: ChatGptImportCandidate,
        val asset: P6KZipAssetCandidate,
        val displayName: String,
        val sourceConversation: P6KZipSourceConversationAssets,
    )
}
