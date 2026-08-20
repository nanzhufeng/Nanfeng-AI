package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationBranchHistory
import com.nanzhufeng.ai.domain.ConversationMutationResult
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.EditConversationUserMessageUseCase
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.MessageTree
import com.nanzhufeng.ai.domain.SwitchConversationBranchUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P3FConversationEditBranchHistoryContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T04:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomConversationRepository
    private lateinit var tree: ConversationTreeService

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomConversationRepository(database)
        tree = ConversationTreeService(clock)
    }

    @After fun tearDown() = database.close()

    @Test fun `editing creates immutable local user leaf and every leaf remains switchable after rebuild`() {
        val user = tree.append(tree.create("P3F"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("原问题"))))
        val originalUserId = user.conversation.currentLeafMessageId!!
        val answered = tree.append(user, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("原回答"))))
        val originalAssistantId = answered.conversation.currentLeafMessageId!!
        repository.save(answered)

        val edit = EditConversationUserMessageUseCase(tree, repository)
        val revised = edit.execute(repository.findById(answered.conversation.id)!!, originalUserId, listOf(ContentBlock.Text("修订问题"))) as ConversationMutationResult.Saved
        val rebuilt = RoomConversationRepository(database).findById(revised.snapshot.conversation.id)!!
        val leaves = ConversationBranchHistory.leaves(rebuilt)

        assertEquals("原问题", (MessageTree(rebuilt.conversation, rebuilt.nodes).node(originalUserId).content.single() as ContentBlock.Text).text)
        assertTrue(leaves.any { it.leafId == originalAssistantId && it.role == MessageRole.ASSISTANT })
        assertTrue(leaves.any { it.leafId == rebuilt.conversation.currentLeafMessageId && it.role == MessageRole.USER && it.isCurrent })

        val switched = SwitchConversationBranchUseCase(tree, repository).execute(rebuilt, originalAssistantId) as ConversationMutationResult.Saved
        assertEquals(listOf("原问题", "原回答"), MessageTree(switched.snapshot.conversation, switched.snapshot.nodes).contextPath().map { (it.content.single() as ContentBlock.Text).text })
    }

    @Test fun `only current-path text-only user messages are offered for editing`() {
        val attachment = AttachmentReference("attachments/v1/local", "image/png", id = AttachmentId("attachment-p3f"), byteCount = 1, sha256 = "a".repeat(64))
        val withAttachment = tree.append(
            tree.create("附件边界"),
            AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("保留附件"), ContentBlock.Attachment(attachment))),
        )
        val textOnly = tree.append(withAttachment, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("可编辑文字"))))
        val editable = ConversationBranchHistory.editableUserMessages(textOnly)

        assertEquals(1, editable.size)
        assertEquals("可编辑文字", editable.single().text)
        assertFalse(editable.any { it.messageId == withAttachment.conversation.currentLeafMessageId })
    }
}
