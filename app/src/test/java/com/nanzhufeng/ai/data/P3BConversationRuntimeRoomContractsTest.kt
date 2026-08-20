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
import com.nanzhufeng.ai.domain.ConversationRuntimePersistenceResult
import com.nanzhufeng.ai.domain.ConversationRuntimeStateMachine
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.MessageDeliveryState
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.RuntimeCancelled
import com.nanzhufeng.ai.domain.RuntimeContentDelta
import com.nanzhufeng.ai.domain.RuntimeRunStarted
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
class P3BConversationRuntimeRoomContractsTest {
    private val now = Instant.parse("2026-08-12T17:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val safe = AiRuntimeSecurityMetadata("LOCAL_DETERMINISTIC_FIXTURE")
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomConversationRepository
    private lateinit var snapshot: com.nanzhufeng.ai.domain.ConversationSnapshot

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomConversationRepository(database)
        snapshot = repository.save(ConversationTreeService(clock).create("Room runtime"))
    }

    @After fun tearDown() = database.close()

    @Test fun `event replay deduplicates and conflict or out of order is rejected without changing projection`() {
        val machine = ConversationRuntimeStateMachine(clock)
        val invocation = InvocationId("room-invocation")
        val message = MessageNodeId("room-message")
        val startedEvent = RuntimeRunStarted(AiRuntimeEventId("r0"), invocation, snapshot.conversation.id, message, 0, now, safe)
        val started = machine.apply(snapshot, null, startedEvent)
        assertTrue(repository.apply(started, startedEvent) is ConversationRuntimePersistenceResult.Applied)
        val deltaEvent = RuntimeContentDelta(AiRuntimeEventId("r1"), invocation, snapshot.conversation.id, message, 1, now, "本地", safe)
        val delta = machine.apply(started.snapshot, started.state, deltaEvent)
        assertTrue(repository.apply(delta, deltaEvent) is ConversationRuntimePersistenceResult.Applied)

        val recreated = RoomConversationRepository(database)
        val replay = recreated.apply(delta, deltaEvent)
        assertTrue(replay is ConversationRuntimePersistenceResult.Replayed)
        val conflict = recreated.apply(delta, deltaEvent.copy(eventId = AiRuntimeEventId("conflict"), delta = "不同"))
        assertTrue(conflict is ConversationRuntimePersistenceResult.Rejected)
        val stored = recreated.findById(snapshot.conversation.id)!!
        assertEquals("本地", (stored.nodes.single().content.single() as com.nanzhufeng.ai.domain.ContentBlock.Text).text)
        assertEquals(2L, recreated.stateFor(snapshot.conversation.id)!!.nextExpectedSequence)
    }

    @Test fun `cancelled projection checkpoint and state survive repository rebuild without ledger content columns`() {
        val machine = ConversationRuntimeStateMachine(clock)
        val invocation = InvocationId("cancel-invocation")
        val message = MessageNodeId("cancel-message")
        val startedEvent = RuntimeRunStarted(AiRuntimeEventId("c0"), invocation, snapshot.conversation.id, message, 0, now, safe)
        val started = machine.apply(snapshot, null, startedEvent)
        repository.apply(started, startedEvent)
        val deltaEvent = RuntimeContentDelta(AiRuntimeEventId("c1"), invocation, snapshot.conversation.id, message, 1, now, "部分输出", safe)
        val delta = machine.apply(started.snapshot, started.state, deltaEvent)
        repository.apply(delta, deltaEvent)
        val cancelEvent = RuntimeCancelled(AiRuntimeEventId("c2"), invocation, snapshot.conversation.id, message, 2, now, safe)
        val cancelled = machine.apply(delta.snapshot, delta.state, cancelEvent)
        repository.apply(cancelled, cancelEvent)

        val recreated = RoomConversationRepository(database)
        assertEquals(com.nanzhufeng.ai.domain.ConversationRuntimeStatus.CANCELLED, recreated.stateFor(snapshot.conversation.id)!!.status)
        val node = recreated.findById(snapshot.conversation.id)!!.nodes.single()
        assertEquals(MessageDeliveryState.CANCELLED, node.deliveryState)
        assertEquals("部分输出", (node.content.single() as com.nanzhufeng.ai.domain.ContentBlock.Text).text)
        val forbidden = setOf("apikey", "authorization", "prompt", "response", "credential", "raw", "content")
        listOf("ai_runtime_events", "conversation_runtime_states").flatMap { table ->
            database.openHelper.writableDatabase.query("PRAGMA table_info($table)").use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }.toList()
            }
        }.forEach { column -> assertFalse("P3-B runtime table cannot store raw content: $column", forbidden.any { column.contains(it, true) }) }
    }

    @Test fun `schema four to five retains P2 and P3 rows without destructive reset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p3b-migration-${java.util.UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE capture_drafts (id TEXT NOT NULL PRIMARY KEY, text TEXT, createdAtEpochMs INTEGER NOT NULL, schemaVersion INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                db.execSQL("CREATE TABLE message_nodes (id TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL, deliveryState TEXT NOT NULL)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase
        sqlite.execSQL("INSERT INTO capture_drafts VALUES ('p2', '保留', 1, 1)")
        sqlite.execSQL("INSERT INTO conversations VALUES ('p3', '保留会话')")
        sqlite.execSQL("INSERT INTO message_nodes VALUES ('m3', 'p3', 'PARTIAL')")
        NanfengAiDatabase.MIGRATION_4_5.migrate(sqlite)
        listOf("capture_drafts", "conversations", "message_nodes").forEach { table ->
            sqlite.query("SELECT COUNT(*) FROM $table").use { cursor -> assertTrue(cursor.moveToFirst() && cursor.getInt(0) == 1) }
        }
        listOf("ai_runtime_events", "conversation_runtime_states").forEach { table ->
            sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { cursor -> assertTrue(cursor.moveToFirst()) }
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
