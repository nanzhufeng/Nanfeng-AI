package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRealTextExecutionRepository
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionPrepareResult
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionRequest
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionState
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionTransitionResult
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.ProviderAttemptId
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P3IConversationRealTextExecutionRoomContractsTest {
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomConversationRealTextExecutionRepository
    private val request = ConversationRealTextExecutionRequest(
        ConversationRealTextExecutionId("p3i-execution"), "p3i-intent", "b".repeat(64), ConversationId("p3i-conversation"),
        MessageNodeId("p3i-user"), MessageNodeId("p3i-assistant"), InvocationId("p3i-invocation"), ProviderAttemptId("p3i-attempt"),
        Instant.parse("2026-08-15T12:00:00Z"),
    )

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomConversationRealTextExecutionRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun `prepare replays exact request but rejects changed fact`() {
        assertTrue(repository.prepare(request) is ConversationRealTextExecutionPrepareResult.Prepared)
        assertTrue(repository.prepare(request) is ConversationRealTextExecutionPrepareResult.Replayed)
        assertTrue(repository.prepare(request.copy(requestFingerprint = "c".repeat(64))) is ConversationRealTextExecutionPrepareResult.Conflict)
    }

    @Test fun `terminal failure survives repository reconstruction and cannot be overwritten`() {
        repository.prepare(request)
        val failed = repository.transition(request.executionId, ConversationRealTextExecutionState.PREPARED, ConversationRealTextExecutionState.FAILED, request.createdAt.plusSeconds(1), "EGRESS_DISABLED")
        assertEquals(ConversationRealTextExecutionState.FAILED, (failed as ConversationRealTextExecutionTransitionResult.Updated).record.state)
        val rebuilt = RoomConversationRealTextExecutionRepository(database)
        assertEquals(ConversationRealTextExecutionState.FAILED, rebuilt.findById(request.executionId)?.state)
        assertTrue(rebuilt.transition(request.executionId, ConversationRealTextExecutionState.FAILED, ConversationRealTextExecutionState.SUCCEEDED, request.createdAt.plusSeconds(2)) is ConversationRealTextExecutionTransitionResult.Rejected)
    }

    @Test fun `table has no prompt response credential URI path attachment usage or cost columns`() {
        val prohibited = setOf("prompt", "response", "credential", "secret", "authorization", "uri", "path", "attachment", "usage", "cost")
        val columns = mutableSetOf<String>()
        database.openHelper.writableDatabase.query("PRAGMA table_info(conversation_real_text_executions)").use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name")).lowercase()
        }
        assertFalse(columns.any { column -> prohibited.any(column::contains) })
    }
}
