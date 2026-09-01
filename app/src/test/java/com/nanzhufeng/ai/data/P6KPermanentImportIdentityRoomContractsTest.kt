package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.data.local.RoomP6KImportIdentityLedger
import com.nanzhufeng.ai.data.local.P6KAssetIdentityDecision
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
import com.nanzhufeng.ai.domain.P6KZipImportItem
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipItemId
import com.nanzhufeng.ai.domain.P6KZipItemStatus
import com.nanzhufeng.ai.domain.P6KZipMappedAsset
import com.nanzhufeng.ai.domain.P6KZipSourceConversationAssets
import com.nanzhufeng.ai.domain.P6KZipSourceMessageAssets
import com.nanzhufeng.ai.domain.P6KZipTaskId
import com.nanzhufeng.ai.domain.P6KZipTaskStatus
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import com.nanzhufeng.ai.domain.chatGptImportContentHash
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P6KPermanentImportIdentityRoomContractsTest {
    private val at = Instant.parse("2026-09-01T08:00:00Z")

    @Test
    fun `same ZIP twice reuses the conversation and writes a zero-failure completion receipt`() = withDatabase { database ->
        val conversations = RoomConversationRepository(database)
        val tasks = RoomP6KZipImportTaskRepository(database)
        val commit = RoomP6KZipImportCommitStore(database, conversations)
        val first = tasks.save(task("same-package"))
        commit.confirm(first, first.items.single(), at)
        val second = tasks.save(task("same-package"))
        commit.confirm(second, second.items.single(), at.plusSeconds(1))

        assertEquals(1, conversations.listActive().size)
        assertEquals(P6KZipItemStatus.CONFIRMED, tasks.find(second.id)!!.items.single().status)
        val receipt = requireNotNull(tasks.find(second.id)!!.receipt)
        assertEquals("COMPLETED", receipt.status)
        assertEquals(1, receipt.skippedExisting)
        assertEquals(0, receipt.failed)
        assertEquals(0, receipt.identityConflicts)
    }

    @Test
    fun `user deleted conversation is permanently skipped by a later ZIP`() = withDatabase { database ->
        val identities = RoomP6KImportIdentityLedger(database)
        val conversations = RoomConversationRepository(database, p6kImportIdentities = identities)
        val tasks = RoomP6KZipImportTaskRepository(database)
        val commit = RoomP6KZipImportCommitStore(database, conversations, identities)
        val first = tasks.save(task("first-package"))
        commit.confirm(first, first.items.single(), at)
        assertTrue(commit.deleteBatch(requireNotNull(tasks.find(first.id)), at.plusSeconds(1)))

        val second = tasks.save(task("later-package"))
        commit.confirm(second, second.items.single(), at.plusSeconds(2))

        val restored = requireNotNull(tasks.find(second.id))
        assertEquals(P6KZipItemStatus.SKIPPED, restored.items.single().status)
        assertEquals("USER_DELETED", restored.items.single().failure)
        assertEquals(1, restored.receipt?.skippedUserDeleted)
        assertTrue(conversations.listActive().isEmpty())
    }

    @Test
    fun `same official message ID with changed content fails closed and never gets a completion marker`() = withDatabase { database ->
        val conversations = RoomConversationRepository(database)
        val tasks = RoomP6KZipImportTaskRepository(database)
        val commit = RoomP6KZipImportCommitStore(database, conversations)
        val first = tasks.save(task("old-package"))
        commit.confirm(first, first.items.single(), at)
        val changedCandidate = candidate().let { source ->
            val changedMessages = source.messages.map { if (it.sourceId == "message-1") it.copy(text = "changed") else it }
            source.copy(messages = changedMessages, contentHash = chatGptImportContentHash(source.sourceConversationId, changedMessages))
        }
        val changed = tasks.save(task("new-package", changedCandidate))
        commit.confirm(changed, changed.items.single(), at.plusSeconds(1))

        val receipt = requireNotNull(tasks.find(changed.id)!!.receipt)
        assertEquals("FAILED", receipt.status)
        assertEquals(1, receipt.identityConflicts)
        assertNull(database.p6kImportIdentityLedgerDao().batchReceipt(changed.id.value)?.completedAtEpochMs)
        assertEquals(1, conversations.listActive().size)
    }

    @Test
    fun `same source IDs from different providers remain separate identities`() = withDatabase { database ->
        val conversations = RoomConversationRepository(database)
        val tasks = RoomP6KZipImportTaskRepository(database)
        val commit = RoomP6KZipImportCommitStore(database, conversations)
        val chatGpt = tasks.save(task("same-package"))
        commit.confirm(chatGpt, chatGpt.items.single(), at)
        val claude = tasks.save(task("same-package").copy(
            id = P6KZipTaskId.new(),
            provider = ThirdPartyZipProvider.CLAUDE,
            formatVersion = "anthropic-claude-conversations-json/v1",
        ))
        commit.confirm(claude, claude.items.single(), at.plusSeconds(1))

        assertEquals(2, conversations.listActive().size)
        assertEquals(P6KZipItemStatus.CONFIRMED, tasks.find(claude.id)!!.items.single().status)
    }

    @Test
    fun `interrupted batch becomes UNKNOWN and is not marked complete`() = withDatabase { database ->
        val identities = RoomP6KImportIdentityLedger(database)
        val task = task("interrupted")
        identities.mutateBatchReceipt(task, at) { it }
        assertEquals(1, identities.markInterruptedAsUnknown(at.plusSeconds(1)))
        val receipt = requireNotNull(identities.batchReceipt(task.id.value))
        assertEquals("UNKNOWN", receipt.status)
        assertNull(receipt.completedAtEpochMs)
    }

    @Test
    fun `same official asset ID with different bytes is an identity conflict`() = withDatabase { database ->
        val identities = RoomP6KImportIdentityLedger(database)
        val task = task("asset-v1")
        identities.registerOccurrence(
            task, "conversation-1", "message-1", "official-file",
            "c".repeat(64), 4, com.nanzhufeng.ai.domain.ConversationId.new(),
            com.nanzhufeng.ai.domain.MessageNodeId.new(), AttachmentId.new().value, at,
        )
        val newer = task("asset-v2")
        assertEquals(
            P6KAssetIdentityDecision.Conflict,
            identities.sourceAssetDecision(newer, "official-file", "d".repeat(64), 4),
        )
    }

    @Test
    fun `shared official file has explicit occurrences while equal bytes with another ID reuse storage`() = withDatabase { database ->
        val identities = RoomP6KImportIdentityLedger(database)
        val conversations = RoomConversationRepository(database, p6kImportIdentities = identities)
        val tasks = RoomP6KZipImportTaskRepository(database)
        val commit = RoomP6KZipImportCommitStore(database, conversations, identities)
        val source = candidate()
        val hash = "a".repeat(64)
        val firstAsset = P6KZipAssetCandidate("official-file-a", hash, 12, "image/png")
        val secondAsset = P6KZipAssetCandidate("official-file-b", hash, 12, "image/png")
        val staged = tasks.save(task("assets", source, listOf(firstAsset, secondAsset)))
        commit.confirm(staged, staged.items.single(), at)
        val imported = requireNotNull(tasks.find(staged.id))
        val mapping = P6KZipAssetMapping(
            conversations = listOf(P6KZipSourceConversationAssets(source.sourceConversationId, listOf(
                P6KZipSourceMessageAssets("message-1", null, MessageRole.USER, at, true, listOf(firstAsset.entryName)),
                P6KZipSourceMessageAssets("message-2", "message-1", MessageRole.ASSISTANT, at.plusSeconds(1), true, listOf(firstAsset.entryName, secondAsset.entryName)),
            ))),
            assets = mapOf(
                firstAsset.entryName to P6KZipMappedAsset(firstAsset, "a.png"),
                secondAsset.entryName to P6KZipMappedAsset(secondAsset, "b.png"),
            ),
        )
        val privateAssets = mapOf(
            firstAsset.entryName to AttachmentReference("private/a", "image/png", "a.png", AttachmentId.new(), 12, hash),
            secondAsset.entryName to AttachmentReference("private/b", "image/png", "b.png", AttachmentId.new(), 12, hash),
        )
        val summary = RoomP6KZipMappedAssetLinkOwner(database, conversations, identityLedger = identities)
            .reconcile(imported, mapping, privateAssets, at.plusSeconds(2))

        assertEquals(3, summary.linkedAssetCount)
        assertEquals(3, database.p6kImportIdentityLedgerDao().countByObjectType("ASSET_OCCURRENCE"))
        assertEquals(2, database.p6kImportIdentityLedgerDao().countByObjectType("SOURCE_ASSET"))
        val snapshot = requireNotNull(conversations.findById(requireNotNull(tasks.find(staged.id)!!.items.single().conversationId)))
        val attachments = snapshot.nodes.flatMap { it.content }.filterIsInstance<ContentBlock.Attachment>()
        assertEquals(3, attachments.size)
        assertEquals(1, attachments.map { it.attachment.id }.toSet().size)
        assertEquals(12L, tasks.find(staged.id)!!.receipt?.reusedAssetBytes)
    }

    @Test
    fun `deleted attachment occurrence is not restored by reconciliation`() = withDatabase { database ->
        val identities = RoomP6KImportIdentityLedger(database)
        val conversations = RoomConversationRepository(database, p6kImportIdentities = identities)
        val tasks = RoomP6KZipImportTaskRepository(database)
        val commit = RoomP6KZipImportCommitStore(database, conversations, identities)
        val source = candidate()
        val hash = "b".repeat(64)
        val asset = P6KZipAssetCandidate("official-file", hash, 8, "image/png")
        val staged = tasks.save(task("attachment-delete", source, listOf(asset)))
        commit.confirm(staged, staged.items.single(), at)
        val imported = requireNotNull(tasks.find(staged.id))
        val mapping = P6KZipAssetMapping(
            listOf(P6KZipSourceConversationAssets(source.sourceConversationId, listOf(
                P6KZipSourceMessageAssets("message-1", null, MessageRole.USER, at, true, listOf(asset.entryName)),
                P6KZipSourceMessageAssets("message-2", "message-1", MessageRole.ASSISTANT, at.plusSeconds(1), true, emptyList()),
            ))),
            mapOf(asset.entryName to P6KZipMappedAsset(asset, "delete-me.png")),
        )
        val privateAsset = AttachmentReference("private/delete", "image/png", "delete-me.png", AttachmentId.new(), 8, hash)
        val owner = RoomP6KZipMappedAssetLinkOwner(database, conversations, identityLedger = identities)
        assertEquals(1, owner.reconcile(imported, mapping, mapOf(asset.entryName to privateAsset), at.plusSeconds(1)).linkedAssetCount)
        val conversationId = requireNotNull(tasks.find(staged.id)!!.items.single().conversationId)
        val before = requireNotNull(conversations.findById(conversationId))
        val node = before.nodes.single { it.content.any { block -> block is ContentBlock.Attachment } }
        val reference = node.content.filterIsInstance<ContentBlock.Attachment>().single().attachment
        conversations.unlinkMessageAttachment(conversationId, node.id, reference.id, requireNotNull(reference.sha256), at.plusSeconds(2))

        val replay = owner.reconcile(requireNotNull(tasks.find(staged.id)), mapping, mapOf(asset.entryName to privateAsset), at.plusSeconds(3))
        val after = requireNotNull(conversations.findById(conversationId))
        assertEquals(0, replay.linkedAssetCount)
        assertTrue(after.nodes.flatMap { it.content }.none { it is ContentBlock.Attachment })
        assertEquals("USER_DELETED", database.p6kImportIdentityLedgerDao().forOccurrence(conversationId.value, node.id.value, reference.id.value).single().state)
    }

    private fun task(packageHash: String, source: ChatGptImportCandidate = candidate(), assets: List<P6KZipAssetCandidate> = emptyList()) =
        P6KZipImportTask(
            P6KZipTaskId.new(), ThirdPartyZipProvider.CHATGPT, "fixture.zip", 100, packageHash,
            P6KZipTaskStatus.AWAITING_CONFIRMATION, formatVersion = "openai-chatgpt-conversations-json/v1",
            createdAt = at, updatedAt = at,
            items = listOf(P6KZipImportItem(P6KZipItemId.new(), 0, source)), assets = assets,
        )

    private fun candidate(): ChatGptImportCandidate {
        val messages = listOf(
            ChatGptImportMessage("message-1", null, 0, MessageRole.USER, "hello", at, null),
            ChatGptImportMessage("message-2", "message-1", 1, MessageRole.ASSISTANT, "world", at.plusSeconds(1), null),
        )
        return ChatGptImportCandidate(
            "conversation-1", "Imported", at, at.plusSeconds(1), messages,
            chatGptImportContentHash("conversation-1", messages),
        )
    }

    private fun withDatabase(block: (NanfengAiDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        try { block(database) } finally { database.close() }
    }
}
