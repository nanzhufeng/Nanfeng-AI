package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRealTextExecutionRepository
import com.nanzhufeng.ai.data.local.RoomConversationRealTextTestExecutionAdapter
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionRequest
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionState
import com.nanzhufeng.ai.domain.ConversationRealTextNormalizedEvent
import com.nanzhufeng.ai.domain.ConversationRealTextTestExecutionResult
import com.nanzhufeng.ai.domain.ConversationRuntimeStateMachine
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.MessageDeliveryState
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.ScriptedConversationRealTextTestTransport
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Callable
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
class P3IConversationRealTextTestTransportAdapterContractsTest {
    private val now = Instant.parse("2026-08-15T14:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var conversations: RoomConversationRepository
    private lateinit var executions: RoomConversationRealTextExecutionRepository
    private lateinit var adapter: RoomConversationRealTextTestExecutionAdapter

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        conversations = RoomConversationRepository(database)
        executions = RoomConversationRealTextExecutionRepository(database)
        adapter = newAdapter(conversations, executions)
    }

    @After fun tearDown() = database.close()

    @Test fun `partial assistant normalized events and same physical attempt persist together`() {
        val request = request("success")

        assertTrue(adapter.start(request) is ConversationRealTextTestExecutionResult.Started)
        val partial = conversations.findById(request.conversationId)!!.nodes.single { it.id == request.assistantMessageId }
        assertEquals(MessageRole.ASSISTANT, partial.role)
        assertEquals(MessageDeliveryState.PARTIAL, partial.deliveryState)
        assertEquals(request.invocationId, partial.invocation!!.invocationId)
        assertEquals(ConversationRealTextExecutionState.RUNNING, executions.findById(request.executionId)!!.state)

        assertTrue(adapter.apply(request, ConversationRealTextNormalizedEvent.TextDelta("规范化本地文本")) is ConversationRealTextTestExecutionResult.Updated)
        assertTrue(adapter.apply(request, ConversationRealTextNormalizedEvent.Completed) is ConversationRealTextTestExecutionResult.Updated)

        val completed = conversations.findById(request.conversationId)!!.nodes.single { it.id == request.assistantMessageId }
        assertEquals(MessageDeliveryState.COMPLETE, completed.deliveryState)
        assertEquals("规范化本地文本", (completed.content.single() as ContentBlock.Text).text)
        val receipt = executions.findById(request.executionId)!!
        assertEquals(ConversationRealTextExecutionState.SUCCEEDED, receipt.state)
        assertEquals(request.attemptId, receipt.request.attemptId)
        assertEquals(completed.invocation!!.invocationId, receipt.request.invocationId)
    }

    @Test fun `scripted transport writes failed and cancelled terminal states without a retry`() {
        val failed = request("failed")
        val failure = adapter.execute(failed, ScriptedConversationRealTextTestTransport(listOf(
            ConversationRealTextNormalizedEvent.TextDelta("可保留的部分文本"),
            ConversationRealTextNormalizedEvent.Failed("TEST_TRANSPORT_FAILURE"),
        )))
        assertEquals(ConversationRealTextExecutionState.FAILED, (failure as ConversationRealTextTestExecutionResult.Updated).record.state)
        assertEquals(MessageDeliveryState.FAILED, conversations.findById(failed.conversationId)!!.nodes.single { it.id == failed.assistantMessageId }.deliveryState)
        assertTrue(adapter.apply(failed, ConversationRealTextNormalizedEvent.Completed) is ConversationRealTextTestExecutionResult.Replayed)

        val cancelled = request("cancelled")
        val cancellation = adapter.execute(cancelled, ScriptedConversationRealTextTestTransport(listOf(
            ConversationRealTextNormalizedEvent.TextDelta("停止前文本"),
            ConversationRealTextNormalizedEvent.Cancelled(),
        )))
        assertEquals(ConversationRealTextExecutionState.CANCELLED, (cancellation as ConversationRealTextTestExecutionResult.Updated).record.state)
        assertEquals(MessageDeliveryState.CANCELLED, conversations.findById(cancelled.conversationId)!!.nodes.single { it.id == cancelled.assistantMessageId }.deliveryState)
    }

    @Test fun `receipt running transition and partial assistant roll back as one room transaction`() {
        val request = request("rollback")
        val failure = runCatching {
            database.runInTransaction(Callable {
                assertTrue(adapter.start(request) is ConversationRealTextTestExecutionResult.Started)
                throw IllegalStateException("test rollback")
            })
        }
        assertTrue(failure.isFailure)
        assertNull(executions.findById(request.executionId))
        assertTrue(conversations.findById(request.conversationId)!!.nodes.none { it.id == request.assistantMessageId })
    }

    @Test fun `restart reads running receipt and only an explicit event can reach terminal`() {
        val request = request("restart")
        assertTrue(adapter.start(request) is ConversationRealTextTestExecutionResult.Started)
        assertTrue(adapter.apply(request, ConversationRealTextNormalizedEvent.TextDelta("重启前部分文本")) is ConversationRealTextTestExecutionResult.Updated)

        val rebuilt = newAdapter(RoomConversationRepository(database), RoomConversationRealTextExecutionRepository(database))
        val replay = rebuilt.start(request) as ConversationRealTextTestExecutionResult.Replayed
        assertEquals(ConversationRealTextExecutionState.RUNNING, replay.record.state)
        assertEquals("重启前部分文本", ((conversations.findById(request.conversationId)!!.nodes.single { it.id == request.assistantMessageId }.content.single()) as ContentBlock.Text).text)

        assertTrue(rebuilt.apply(request, ConversationRealTextNormalizedEvent.Cancelled("TEST_PROCESS_RESTART_CANCELLED")) is ConversationRealTextTestExecutionResult.Updated)
        assertEquals(ConversationRealTextExecutionState.CANCELLED, executions.findById(request.executionId)!!.state)
        val columns = database.openHelper.writableDatabase.query("PRAGMA table_info(conversation_real_text_executions)").use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }.toList()
        }
        assertFalse(columns.any { column -> setOf("prompt", "response", "credential", "authorization", "uri", "path", "attachment", "usage", "cost").any { column.contains(it, true) } })
    }

    private fun newAdapter(
        conversationRepository: RoomConversationRepository,
        executionRepository: RoomConversationRealTextExecutionRepository,
    ) = RoomConversationRealTextTestExecutionAdapter(
        database, conversationRepository, executionRepository, ConversationRuntimeStateMachine(clock), clock,
    )

    private fun request(label: String): ConversationRealTextExecutionRequest {
        val empty = conversations.save(ConversationTreeService(clock).create("P3-I $label"))
        val withUser = conversations.save(ConversationTreeService(clock).append(empty, AppendMessageRequest(
            MessageRole.USER, listOf(ContentBlock.Text("本地用户问题 $label")),
        )))
        val userId = requireNotNull(withUser.conversation.currentLeafMessageId)
        return ConversationRealTextExecutionRequest(
            executionId = ConversationRealTextExecutionId("p3i-$label-execution"),
            idempotencyKey = "p3i-$label-idempotency",
            requestFingerprint = label.length.toString(16).padStart(64, 'a'),
            conversationId = withUser.conversation.id,
            userMessageId = userId,
            assistantMessageId = MessageNodeId("p3i-$label-assistant"),
            invocationId = InvocationId("p3i-$label-invocation"),
            attemptId = ProviderAttemptId("p3i-$label-attempt"),
            createdAt = now,
        )
    }
}
