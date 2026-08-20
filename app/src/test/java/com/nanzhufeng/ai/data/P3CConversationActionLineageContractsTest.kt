package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.AiRuntimeEventId
import com.nanzhufeng.ai.domain.AiRuntimeSecurityMetadata
import com.nanzhufeng.ai.domain.ConversationActionIntentId
import com.nanzhufeng.ai.domain.ConversationActionKind
import com.nanzhufeng.ai.domain.ConversationActionOrchestrator
import com.nanzhufeng.ai.domain.ConversationActionRequest
import com.nanzhufeng.ai.domain.ConversationActionResult
import com.nanzhufeng.ai.domain.ConversationRuntimePersistenceResult
import com.nanzhufeng.ai.domain.ConversationRuntimeStateMachine
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.InMemoryVersionedModelRegistry
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.MessageDeliveryState
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.P3CLocalFixtureRegistry
import com.nanzhufeng.ai.domain.RuntimeCancelled
import com.nanzhufeng.ai.domain.RuntimeCompleted
import com.nanzhufeng.ai.domain.RuntimeContentDelta
import com.nanzhufeng.ai.domain.RuntimeRunStarted
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P3CConversationActionLineageContractsTest {
    private val now = Instant.parse("2026-08-12T18:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val safe = AiRuntimeSecurityMetadata("LOCAL_DETERMINISTIC_FIXTURE")
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomConversationRepository
    private lateinit var tree: ConversationTreeService
    private lateinit var machine: ConversationRuntimeStateMachine
    private lateinit var actions: ConversationActionOrchestrator

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomConversationRepository(database)
        tree = ConversationTreeService(clock)
        machine = ConversationRuntimeStateMachine(clock)
        actions = ConversationActionOrchestrator(
            repository, repository, repository,
            InMemoryVersionedModelRegistry(listOf(P3CLocalFixtureRegistry.snapshot)), machine, clock,
        )
    }

    @After fun tearDown() = database.close()

    @Test fun `continue retains cancelled partial fact and creates child attempt lineage`() {
        val source = terminalAssistant(MessageDeliveryState.CANCELLED, "已保留部分")
        val request = ConversationActionRequest(ConversationActionIntentId("continue-1"), source.conversationId, source.messageId, ConversationActionKind.CONTINUE)
        val started = actions.execute(request) as ConversationActionResult.Started
        val restored = repository.findById(source.conversationId)!!
        val original = restored.nodes.first { it.id == source.messageId }
        val continued = restored.nodes.first { it.id == started.lineage.createdMessageId }
        assertEquals(MessageDeliveryState.CANCELLED, original.deliveryState)
        assertEquals("已保留部分", (original.content.single() as ContentBlock.Text).text)
        assertEquals(source.messageId, continued.parentMessageId)
        assertEquals(continued.id, restored.conversation.currentLeafMessageId)
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.ASSISTANT),
            com.nanzhufeng.ai.domain.MessageTree(restored.conversation, restored.nodes).contextPath().map { it.role })
        assertEquals(source.invocationId, started.lineage.previousInvocationId)
        assertEquals(InvocationId("p3c:continue-1:invocation"), started.lineage.invocationId)
        assertEquals(listOf(started.lineage), RoomConversationRepository(database).lineagesForConversation(source.conversationId))
    }

    @Test fun `retry and change model create immutable siblings and only new leaf is selected`() {
        val source = terminalAssistant(MessageDeliveryState.COMPLETE, "原回答")
        val retry = actions.execute(ConversationActionRequest(ConversationActionIntentId("retry-1"), source.conversationId, source.messageId, ConversationActionKind.RETRY)) as ConversationActionResult.Started
        val afterRetry = repository.findById(source.conversationId)!!
        val old = afterRetry.nodes.first { it.id == source.messageId }
        val retryNode = afterRetry.nodes.first { it.id == retry.lineage.createdMessageId }
        assertEquals("原回答", (old.content.single() as ContentBlock.Text).text)
        assertEquals(old.parentMessageId, retryNode.parentMessageId)
        assertEquals(retryNode.id, afterRetry.conversation.currentLeafMessageId)
        terminalCurrent(retry.projection.state, MessageDeliveryState.CANCELLED)
        val change = actions.execute(ConversationActionRequest(ConversationActionIntentId("change-1"), source.conversationId, retry.lineage.createdMessageId, ConversationActionKind.CHANGE_MODEL, ModelPresetId.CLAUDE_HAIKU_4_5)) as ConversationActionResult.Started
        val afterChange = repository.findById(source.conversationId)!!
        val changed = afterChange.nodes.first { it.id == change.lineage.createdMessageId }
        assertEquals(old.parentMessageId, changed.parentMessageId)
        assertEquals(changed.id, afterChange.conversation.currentLeafMessageId)
        assertEquals("fixture-local-fast-v1", change.lineage.selection.modelId)
        assertEquals(3, afterChange.nodes.count { it.parentMessageId == old.parentMessageId })
    }

    @Test fun `invalid action duplicate intent and failed transaction have deterministic results`() {
        val complete = terminalAssistant(MessageDeliveryState.COMPLETE, "完整回答")
        val rejectedContinue = actions.execute(ConversationActionRequest(ConversationActionIntentId("bad-continue"), complete.conversationId, complete.messageId, ConversationActionKind.CONTINUE))
        assertTrue(rejectedContinue is ConversationActionResult.Rejected)

        val request = ConversationActionRequest(ConversationActionIntentId("same-intent"), complete.conversationId, complete.messageId, ConversationActionKind.RETRY)
        val first = actions.execute(request) as ConversationActionResult.Started
        val replay = actions.execute(request) as ConversationActionResult.Replayed
        assertEquals(first.lineage.invocationId, replay.lineage.invocationId)
        val conflict = actions.execute(request.copy(actionKind = ConversationActionKind.CHANGE_MODEL, targetPreset = ModelPresetId.CLAUDE_HAIKU_4_5))
        assertTrue(conflict is ConversationActionResult.Rejected)

        val before = repository.findById(complete.conversationId)!!
        val badLineage = first.lineage.copy(intentId = ConversationActionIntentId("rollback"), originMessageId = MessageNodeId("missing"))
        val rejected = repository.applyAction(first.projection, RuntimeRunStarted(AiRuntimeEventId("rollback"), first.lineage.invocationId, complete.conversationId, first.lineage.createdMessageId, 0, now, safe), badLineage)
        assertTrue(rejected is com.nanzhufeng.ai.domain.ConversationActionPersistenceResult.Rejected)
        assertEquals(before.conversation.currentLeafMessageId, repository.findById(complete.conversationId)!!.conversation.currentLeafMessageId)
        assertNull(repository.lineageForIntent(ConversationActionIntentId("rollback")))
    }

    @Test fun `schema five to six preserves earlier rows and lineage excludes sensitive columns`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p3c-migration-${java.util.UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE capture_drafts (id TEXT NOT NULL PRIMARY KEY, text TEXT, createdAtEpochMs INTEGER NOT NULL, schemaVersion INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                db.execSQL("CREATE TABLE message_nodes (id TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL, deliveryState TEXT NOT NULL)")
                db.execSQL("CREATE TABLE ai_runtime_events (eventId TEXT NOT NULL PRIMARY KEY, invocationId TEXT NOT NULL)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase
        sqlite.execSQL("INSERT INTO capture_drafts VALUES ('p2', '保留草稿', 1, 1)")
        sqlite.execSQL("INSERT INTO conversations VALUES ('c', 'P3')")
        sqlite.execSQL("INSERT INTO message_nodes VALUES ('m', 'c', 'CANCELLED')")
        sqlite.execSQL("INSERT INTO ai_runtime_events VALUES ('e', 'i')")
        NanfengAiDatabase.MIGRATION_5_6.migrate(sqlite)
        listOf("capture_drafts", "conversations", "message_nodes", "ai_runtime_events").forEach { table ->
            sqlite.query("SELECT COUNT(*) FROM $table").use { cursor -> assertTrue(cursor.moveToFirst() && cursor.getInt(0) == 1) }
        }
        sqlite.query("PRAGMA table_info(conversation_attempt_lineages)").use { cursor ->
            val columns = generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }.toList()
            assertFalse(columns.any { column -> setOf("key", "prompt", "response", "content", "body", "authorization", "credential", "raw").any { column.contains(it, true) } })
        }
        helper.close()
        context.deleteDatabase(name)
    }

    private data class Source(val conversationId: com.nanzhufeng.ai.domain.ConversationId, val messageId: MessageNodeId, val invocationId: InvocationId)

    private fun terminalAssistant(state: MessageDeliveryState, text: String): Source {
        val created = repository.save(tree.create("P3-C"))
        val withUser = repository.save(tree.append(created, com.nanzhufeng.ai.domain.AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("问题")))))
        val invocation = InvocationId("source-${state.name}")
        val message = MessageNodeId("source-message-${state.name}")
        val startedEvent = RuntimeRunStarted(AiRuntimeEventId("source-start-${state.name}"), invocation, withUser.conversation.id, message, 0, now, safe)
        val started = machine.apply(withUser, null, startedEvent)
        repository.apply(started, startedEvent)
        val delta = RuntimeContentDelta(AiRuntimeEventId("source-delta-${state.name}"), invocation, withUser.conversation.id, message, 1, now, text, safe)
        val projected = machine.apply(started.snapshot, started.state, delta)
        repository.apply(projected, delta)
        terminalCurrent(projected.state, state)
        return Source(withUser.conversation.id, message, invocation)
    }

    private fun terminalCurrent(runtime: com.nanzhufeng.ai.domain.ConversationRuntimeState, state: MessageDeliveryState) {
        val event = when (state) {
            MessageDeliveryState.CANCELLED -> RuntimeCancelled(AiRuntimeEventId("terminal-${runtime.invocationId.value}"), runtime.invocationId, runtime.conversationId, runtime.messageId, runtime.nextExpectedSequence, now, safe)
            MessageDeliveryState.COMPLETE -> RuntimeCompleted(AiRuntimeEventId("terminal-${runtime.invocationId.value}"), runtime.invocationId, runtime.conversationId, runtime.messageId, runtime.nextExpectedSequence, now, safe)
            else -> error("test only creates complete or cancelled source")
        }
        val snapshot = repository.findById(runtime.conversationId)!!
        val projection = machine.apply(snapshot, runtime, event)
        assertTrue(repository.apply(projection, event) is ConversationRuntimePersistenceResult.Applied)
    }
}
