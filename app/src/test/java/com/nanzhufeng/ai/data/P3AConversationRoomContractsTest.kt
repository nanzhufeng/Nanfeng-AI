package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.MessageRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P3AConversationRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-12T15:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomConversationRepository
    private lateinit var service: ConversationTreeService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomConversationRepository(database)
        service = ConversationTreeService(clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `new conversation append ordering idempotency and process rebuild keep one local truth`() {
        val created = service.create("Room 会话", projectId = "project-p3a")
        val withUser = service.append(created, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("第一条"))))
        val snapshot = service.append(withUser, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("第二条"))))

        val saved = repository.save(snapshot)
        val recreated = RoomConversationRepository(database)

        assertEquals(snapshot, saved)
        assertEquals(snapshot, recreated.save(snapshot))
        assertEquals(snapshot, recreated.findById(snapshot.conversation.id))
        assertEquals(listOf("第一条", "第二条"), com.nanzhufeng.ai.domain.MessageTree(
            recreated.findById(snapshot.conversation.id)!!.conversation,
            recreated.findById(snapshot.conversation.id)!!.nodes,
        ).contextPath().map { (it.content.single() as ContentBlock.Text).text })
        assertEquals(listOf(snapshot.conversation), recreated.listActive())
    }

    @Test
    fun `K3 parsed continuation protocol survives Room without becoming visible text`() {
        val snapshot = service.append(
            service.append(service.create("K3 续接"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("继续")))),
            AppendMessageRequest(
                MessageRole.ASSISTANT,
                listOf(
                    ContentBlock.Reasoning("推理"),
                    ContentBlock.ProviderToolCall("call_1", "lookup", "{\"q\":\"K3\"}"),
                    ContentBlock.Text("结论"),
                ),
            ),
        )

        repository.save(snapshot)

        assertEquals(snapshot, RoomConversationRepository(database).findById(snapshot.conversation.id))
    }

    @Test
    fun `transaction failure rejects conflicting immutable node and leaves stored snapshot unchanged`() {
        val baseline = repository.save(
            service.append(service.create("事务合同"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("原文")))),
        )
        val original = baseline.nodes.single()
        val appended = service.append(
            baseline,
            AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("如果事务失败不能留下我"))),
        )
        val conflicting = appended.copy(
            nodes = appended.nodes.map { node ->
                if (node.id == original.id) node.copy(content = listOf(ContentBlock.Text("非法改写"))) else node
            }.reversed(),
        )

        val failed = runCatching { repository.save(conflicting) }

        assertTrue(failed.isFailure)
        assertEquals(baseline, repository.findById(baseline.conversation.id))
    }

    @Test
    fun `schema three to four retains P2 capture knowledge candidate and ledger rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p3a-migration-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE capture_drafts (id TEXT NOT NULL PRIMARY KEY, text TEXT, createdAtEpochMs INTEGER NOT NULL, schemaVersion INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE knowledge_items (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, candidateId TEXT NOT NULL, invocationId TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, harnessVersion INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, schemaVersion INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE generated_candidates (id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, draftId TEXT NOT NULL, invocationId TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, harnessVersion INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, generatedAtEpochMs INTEGER NOT NULL, schemaVersion INTEGER NOT NULL, status TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE invocation_records (id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, harnessVersion INTEGER NOT NULL, completedAtEpochMs INTEGER NOT NULL, status TEXT NOT NULL, errorCode TEXT, registrySnapshotId TEXT, pricingVersion TEXT, inputTokens INTEGER, outputTokens INTEGER, totalTokens INTEGER, cachedInputTokens INTEGER, costPriceVersion TEXT, costCurrencyCode TEXT, costTotalMicros INTEGER)")
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val sqlite = helper.writableDatabase
        sqlite.execSQL("INSERT INTO capture_drafts VALUES ('draft-p2', 'P2 草稿', 1, 1)")
        sqlite.execSQL("INSERT INTO knowledge_items VALUES ('knowledge-p2', '标题', '正文', 'candidate-p2', 'invocation-p2', 'MOCK', 'mock', 1, 1, 1)")
        sqlite.execSQL("INSERT INTO generated_candidates VALUES ('candidate-p2', 'task', 'draft-p2', 'invocation-p2', 'MOCK', 'mock', 1, '标题', '正文', 1, 1, 'PENDING_REVIEW', 1)")
        sqlite.execSQL("INSERT INTO invocation_records VALUES ('invocation-p2', 'task', 'MOCK', 'mock', 1, 1, 'SUCCEEDED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)")

        NanfengAiDatabase.MIGRATION_3_4.migrate(sqlite)

        listOf("capture_drafts", "knowledge_items", "generated_candidates", "invocation_records").forEach { table ->
            sqlite.query("SELECT COUNT(*) FROM $table").use { cursor -> assertTrue("迁移丢失 P2 表：$table", cursor.moveToFirst() && cursor.getInt(0) == 1) }
        }
        listOf("conversations", "message_nodes", "message_content_blocks", "conversation_drafts", "conversation_memory_sources").forEach { table ->
            sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { cursor -> assertTrue("迁移缺少 P3-A 表：$table", cursor.moveToFirst()) }
        }
        helper.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `conversation schema keeps content separate from ledger and excludes secret transport columns`() {
        val forbidden = setOf("apikey", "authorization", "prompt", "response", "credential", "costtotal", "inputtokens", "outputtokens")
        val tables = listOf("conversations", "message_nodes", "message_content_blocks", "conversation_drafts", "conversation_memory_sources")

        tables.flatMap { table ->
            database.openHelper.writableDatabase.query("PRAGMA table_info($table)").use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }.toList()
            }
        }.forEach { column -> assertFalse("P3-A 会话表不能复制敏感运行字段：$column", forbidden.any { column.contains(it, ignoreCase = true) }) }
        assertNotNull(database.conversationDao())
    }
}
