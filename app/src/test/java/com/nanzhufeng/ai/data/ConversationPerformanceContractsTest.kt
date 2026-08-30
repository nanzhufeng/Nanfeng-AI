package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationListScope
import com.nanzhufeng.ai.domain.ConversationManagementDomain
import com.nanzhufeng.ai.domain.ConversationSearchProjection
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.SearchConversationsUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationPerformanceContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC)
    private val queries = CopyOnWriteArrayList<String>()
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomConversationRepository
    private lateinit var tree: ConversationTreeService

    @Before
    fun setUp() {
        val directExecutor = Executor { command -> command.run() }
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NanfengAiDatabase::class.java,
        ).allowMainThreadQueries()
            .setQueryCallback({ sql, _ -> queries += sql }, directExecutor)
            .build()
        repository = RoomConversationRepository(database)
        tree = ConversationTreeService(clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `long conversation reload uses one batched block query instead of one query per message`() {
        var snapshot = tree.create("长会话性能门")
        repeat(80) { index ->
            snapshot = tree.append(
                snapshot,
                AppendMessageRequest(
                    role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                    content = listOf(ContentBlock.Text("第 $index 条性能样本")),
                ),
            )
        }
        repository.save(snapshot)
        queries.clear()

        val loaded = requireNotNull(repository.findById(snapshot.conversation.id))
        assertEquals(
            (0 until 80).map { "第 $it 条性能样本" },
            com.nanzhufeng.ai.domain.MessageTree(loaded.conversation, loaded.nodes).contextPath()
                .map { node -> (node.content.single() as ContentBlock.Text).text },
        )

        val selects = selectQueries()
        assertEquals(1, selects.count { it.contains("FROM message_content_blocks WHERE messageId IN") })
        assertFalse(selects.any { it.contains("FROM message_content_blocks WHERE messageId = ?") })
        val snapshotSelects = selects.filter { sql ->
            listOf("FROM conversations", "FROM message_nodes", "FROM message_content_blocks", "FROM conversation_drafts", "FROM conversation_draft_attachments", "FROM conversation_memory_sources")
                .any(sql::contains)
        }
        assertTrue("单会话回读查询数应与消息数无关，实际为 ${snapshotSelects.size}", snapshotSelects.size <= 6)
    }

    @Test
    fun `large drawer list batches memory sources and search avoids full snapshot rebuilds`() {
        repeat(30) { index ->
            repository.save(
                tree.append(
                    tree.create("会话 $index"),
                    AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("共同性能关键词 $index"))),
                ),
            )
        }

        queries.clear()
        assertEquals(30, repository.list(ConversationListScope.ACTIVE).size)
        val listSelects = selectQueries()
        assertEquals(1, listSelects.count { it.contains("FROM conversation_memory_sources WHERE conversationId IN") })
        assertFalse(listSelects.any { it.contains("FROM conversation_memory_sources WHERE conversationId = ?") })

        queries.clear()
        val search = SearchConversationsUseCase(
            repository,
            ConversationSearchProjection(ConversationManagementDomain(clock)),
        )
        assertEquals(30, search.execute("共同性能关键词", ConversationListScope.ACTIVE).size)
        val searchSelects = selectQueries()
        assertFalse(searchSelects.any { it.contains("FROM conversations WHERE id = ?") })
        assertFalse(searchSelects.any { it.contains("FROM message_content_blocks WHERE messageId = ?") })
        assertTrue("搜索查询数应与会话和消息数无关，实际为 ${searchSelects.size}", searchSelects.size <= 7)
    }

    private fun selectQueries(): List<String> = queries.filter { it.trimStart().startsWith("SELECT", ignoreCase = true) || it.trimStart().startsWith("WITH", ignoreCase = true) }
}
