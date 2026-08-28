package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipImportCommitStore
import com.nanzhufeng.ai.data.local.RoomP6KZipImportTaskRepository
import com.nanzhufeng.ai.data.local.RoomP6KZipMappedAssetLinkOwner
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.ChatGptImportCandidate
import com.nanzhufeng.ai.domain.ChatGptImportMessage
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationListScope
import com.nanzhufeng.ai.domain.ConversationSearchCategory
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.P6KZipAssetMapping
import com.nanzhufeng.ai.domain.P6KZipAssetRole
import com.nanzhufeng.ai.domain.P6KZipImportItem
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipItemId
import com.nanzhufeng.ai.domain.P6KZipMappedAsset
import com.nanzhufeng.ai.domain.P6KZipSourceConversationAssets
import com.nanzhufeng.ai.domain.P6KZipSourceMessageAssets
import com.nanzhufeng.ai.domain.P6KZipTaskId
import com.nanzhufeng.ai.domain.P6KZipTaskStatus
import com.nanzhufeng.ai.domain.SearchConversationAttachmentsUseCase
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import com.nanzhufeng.ai.domain.chatGptImportContentHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class P6KZipMappedAssetRecoveryRoomContractsTest {
    @Test
    fun `attachment-only source message is restored into normal message tree and search`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val conversations = RoomConversationRepository(database)
            val tasks = RoomP6KZipImportTaskRepository(database)
            val at = Instant.parse("2026-08-27T10:00:00Z")
            val messages = listOf(
                ChatGptImportMessage("user-text", null, 0, MessageRole.USER, "先看文件", at, null),
                ChatGptImportMessage("assistant", "user-text", 1, MessageRole.ASSISTANT, "已看到", at.plusSeconds(2), null),
            )
            val candidate = ChatGptImportCandidate("source-conversation", "导入对话", at, at.plusSeconds(2), messages, chatGptImportContentHash("source-conversation", messages))
            val hash = "1".repeat(64)
            val asset = com.nanzhufeng.ai.domain.P6KZipAssetCandidate("file_fixture.dat", hash, 9, "image/png")
            val item = P6KZipImportItem(P6KZipItemId.new(), 0, candidate)
            val staged = tasks.save(P6KZipImportTask(P6KZipTaskId.new(), ThirdPartyZipProvider.CHATGPT, "fixture.zip", 100, "package", P6KZipTaskStatus.AWAITING_CONFIRMATION, createdAt = at, updatedAt = at, items = listOf(item), assets = listOf(asset)))
            RoomP6KZipImportCommitStore(database, conversations).confirm(staged, staged.items.single(), at)
            val committed = requireNotNull(tasks.find(staged.id))
            val mapping = P6KZipAssetMapping(
                conversations = listOf(
                    P6KZipSourceConversationAssets(
                        "source-conversation",
                        listOf(
                            P6KZipSourceMessageAssets("user-text", null, MessageRole.USER, at, true, emptyList()),
                            P6KZipSourceMessageAssets("asset-only", "user-text", MessageRole.USER, at.plusSeconds(1), false, listOf(asset.entryName)),
                            P6KZipSourceMessageAssets("assistant", "asset-only", MessageRole.ASSISTANT, at.plusSeconds(2), true, emptyList()),
                        ),
                    ),
                ),
                assets = mapOf(asset.entryName to P6KZipMappedAsset(asset.copy(sourceConversationId = "source-conversation", sourceMessageId = "asset-only"), "截图.png")),
            )
            val privateAsset = AttachmentReference(P6KZipArchiveAssetStorage.key(staged.id.value, asset.entryName), "image/png", "截图.png", AttachmentId.new(), 9, hash)
            val summary = RoomP6KZipMappedAssetLinkOwner(database, conversations).reconcile(committed, mapping, mapOf(asset.entryName to privateAsset), at.plusSeconds(3))

            assertEquals(1, summary.linkedAssetCount)
            assertEquals(1, summary.createdMessageCount)
            val conversationId = requireNotNull(tasks.find(staged.id)).items.single().conversationId
            val snapshot = requireNotNull(conversations.findById(requireNotNull(conversationId)))
            val attachmentNode = snapshot.nodes.single { it.content.any { block -> block is ContentBlock.Attachment } }
            val assistantNode = snapshot.nodes.single { it.content.filterIsInstance<ContentBlock.Text>().any { text -> text.text == "已看到" } }
            assertEquals(attachmentNode.id, assistantNode.parentMessageId)
            assertEquals("截图.png", attachmentNode.content.filterIsInstance<ContentBlock.Attachment>().single().attachment.displayName)
            assertEquals(1, SearchConversationAttachmentsUseCase(conversations).browse(ConversationSearchCategory.IMAGE, ConversationListScope.ALL).size)
            assertEquals(P6KZipAssetRole.SOURCE_MAPPED, tasks.find(staged.id)?.assets?.single()?.role)
            assertNotNull(database.privateAttachmentAssetDao().findBySha256(hash))
        } finally {
            database.close()
        }
    }

    @Test
    fun `restored official path keeps a valid descendant leaf when its last renderable message has a child`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val conversations = RoomConversationRepository(database)
            val tasks = RoomP6KZipImportTaskRepository(database)
            val at = Instant.parse("2026-08-27T10:00:00Z")
            val messages = listOf(
                ChatGptImportMessage("root", null, 0, MessageRole.USER, "开始", at, null),
                ChatGptImportMessage("middle", "root", 1, MessageRole.ASSISTANT, "中间", at.plusSeconds(1), null),
                ChatGptImportMessage("preserved-child", "middle", 2, MessageRole.USER, "保留分支", at.plusSeconds(2), null),
            )
            val candidate = ChatGptImportCandidate("source-with-structural-tail", "导入分支", at, at.plusSeconds(2), messages, chatGptImportContentHash("source-with-structural-tail", messages))
            val hash = "2".repeat(64)
            val asset = com.nanzhufeng.ai.domain.P6KZipAssetCandidate("file_branch.dat", hash, 7, "image/png")
            val item = P6KZipImportItem(P6KZipItemId.new(), 0, candidate)
            val staged = tasks.save(P6KZipImportTask(P6KZipTaskId.new(), ThirdPartyZipProvider.CHATGPT, "fixture.zip", 100, "package", P6KZipTaskStatus.AWAITING_CONFIRMATION, createdAt = at, updatedAt = at, items = listOf(item), assets = listOf(asset)))
            RoomP6KZipImportCommitStore(database, conversations).confirm(staged, staged.items.single(), at)
            val committed = requireNotNull(tasks.find(staged.id))
            val before = requireNotNull(conversations.findById(requireNotNull(committed.items.single().conversationId)))
            val existingLeaf = requireNotNull(before.conversation.currentLeafMessageId)
            val mapping = P6KZipAssetMapping(
                conversations = listOf(
                    P6KZipSourceConversationAssets(
                        "source-with-structural-tail",
                        listOf(
                            P6KZipSourceMessageAssets("root", null, MessageRole.USER, at, true, emptyList()),
                            P6KZipSourceMessageAssets("middle", "root", MessageRole.ASSISTANT, at.plusSeconds(1), true, listOf(asset.entryName)),
                        ),
                    ),
                ),
                assets = mapOf(asset.entryName to P6KZipMappedAsset(asset.copy(sourceConversationId = "source-with-structural-tail", sourceMessageId = "middle"), "分支截图.png")),
            )
            val reference = AttachmentReference(P6KZipArchiveAssetStorage.key(staged.id.value, asset.entryName), "image/png", "分支截图.png", AttachmentId.new(), 7, hash)

            val summary = RoomP6KZipMappedAssetLinkOwner(database, conversations).reconcile(committed, mapping, mapOf(asset.entryName to reference), at.plusSeconds(3))
            val after = requireNotNull(conversations.findById(requireNotNull(committed.items.single().conversationId)))

            assertEquals(1, summary.linkedAssetCount)
            assertEquals(0, summary.failedConversationCount)
            assertEquals(existingLeaf, after.conversation.currentLeafMessageId)
            assertEquals(1, after.nodes.flatMap { it.content }.filterIsInstance<ContentBlock.Attachment>().size)
        } finally {
            database.close()
        }
    }
}
