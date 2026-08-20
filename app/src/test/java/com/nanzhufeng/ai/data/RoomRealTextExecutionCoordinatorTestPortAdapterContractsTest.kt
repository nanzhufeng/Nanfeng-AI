package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRealTextExecutionRepository
import com.nanzhufeng.ai.data.local.RoomRealTextExecutionCoordinatorTestPortAdapter
import com.nanzhufeng.ai.data.local.RoomUsageLedgerRepository
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionPrepareResult
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionRequest
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionState
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.ProviderTransportEventSink
import com.nanzhufeng.ai.domain.ProviderTransportModelId
import com.nanzhufeng.ai.domain.ProviderTransportNormalizedEvent
import com.nanzhufeng.ai.domain.ProviderTransportProviderHandle
import com.nanzhufeng.ai.domain.ProviderTransportSafeResult
import com.nanzhufeng.ai.domain.ProviderTransportSafeRunMetadata
import com.nanzhufeng.ai.domain.ProviderTransportTerminalOutcome
import com.nanzhufeng.ai.domain.RealTextExecutionCancellationIdentity
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinator
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorCancellation
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorResult
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorTransport
import com.nanzhufeng.ai.domain.RealTextExecutionPreflightId
import com.nanzhufeng.ai.domain.RealTextExecutionReadyPlan
import com.nanzhufeng.ai.domain.RealTextExecutionRuntimeReceiptResult
import com.nanzhufeng.ai.domain.RealTextExecutionUsageReservationResult
import com.nanzhufeng.ai.domain.RealTextUsageReservationPlan
import com.nanzhufeng.ai.domain.UsageLedgerKind
import com.nanzhufeng.ai.domain.UsageLedgerSource
import java.time.Instant
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

/** Contracts for the unregistered Room ports; these fixtures never construct a Conversation reply. */
@RunWith(RobolectricTestRunner::class)
class RoomRealTextExecutionCoordinatorTestPortAdapterContractsTest {
    private val now = Instant.parse("2026-08-15T14:30:00Z")
    private lateinit var database: NanfengAiDatabase
    private lateinit var executions: RoomConversationRealTextExecutionRepository
    private lateinit var usage: RoomUsageLedgerRepository
    private lateinit var ports: RoomRealTextExecutionCoordinatorTestPortAdapter

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java,
        ).allowMainThreadQueries().build()
        executions = RoomConversationRealTextExecutionRepository(database)
        usage = RoomUsageLedgerRepository(database)
        ports = newPorts()
    }

    @After fun tearDown() = database.close()

    @Test fun `coordinator persists start partial terminal and only an append-only reservation`() {
        val plan = preparePlan("success")
        val result = coordinator(plan, listOf(
            ProviderTransportNormalizedEvent.Started(null),
            ProviderTransportNormalizedEvent.TextDelta("transient-test-delta"),
            ProviderTransportNormalizedEvent.Completed,
        ), ProviderTransportTerminalOutcome.COMPLETED).coordinate(plan)

        assertTrue(result is RealTextExecutionCoordinatorResult.Executed)
        assertEquals(ConversationRealTextExecutionState.SUCCEEDED, executions.findById(plan.cancellation.executionId)!!.state)
        assertEquals(listOf(UsageLedgerKind.BUDGET_RESERVATION), usage.entriesForExecution(plan.cancellation.executionId).map { it.kind })
        assertNoSensitivePersistenceSurface()
    }

    @Test fun `failed and cancelled coordinator terminals append one matching budget release`() {
        val failed = preparePlan("failed")
        coordinator(failed, listOf(
            ProviderTransportNormalizedEvent.Started(null),
            ProviderTransportNormalizedEvent.Failed(com.nanzhufeng.ai.domain.ProviderTransportSafeErrorCode.PROVIDER_NETWORK_UNAVAILABLE),
        ), ProviderTransportTerminalOutcome.FAILED, com.nanzhufeng.ai.domain.ProviderTransportSafeErrorCode.PROVIDER_NETWORK_UNAVAILABLE)
            .coordinate(failed)

        val cancelled = preparePlan("cancelled")
        coordinator(cancelled, listOf(
            ProviderTransportNormalizedEvent.Started(null),
            ProviderTransportNormalizedEvent.Cancelled(com.nanzhufeng.ai.domain.ProviderTransportSafeErrorCode.TRANSPORT_CANCELLED),
        ), ProviderTransportTerminalOutcome.CANCELLED, com.nanzhufeng.ai.domain.ProviderTransportSafeErrorCode.TRANSPORT_CANCELLED)
            .coordinate(cancelled)

        assertEquals(ConversationRealTextExecutionState.FAILED, executions.findById(failed.cancellation.executionId)!!.state)
        assertEquals(ConversationRealTextExecutionState.CANCELLED, executions.findById(cancelled.cancellation.executionId)!!.state)
        listOf(failed, cancelled).forEach { plan ->
            assertEquals(
                setOf(UsageLedgerKind.BUDGET_RESERVATION, UsageLedgerKind.BUDGET_RELEASE),
                usage.entriesForExecution(plan.cancellation.executionId).map { it.kind }.toSet(),
            )
        }
    }

    @Test fun `rebuilt ports read running facts and exact reservations replay while conflicts fail closed`() {
        val plan = preparePlan("rebuild")
        assertTrue(ports.release(plan.usageReservation, "TEST_WITHOUT_RESERVATION", now) is RealTextExecutionUsageReservationResult.Rejected)
        assertTrue(ports.reserve(plan.usageReservation, now) is RealTextExecutionUsageReservationResult.Reserved)
        assertTrue(ports.start(plan, now) is RealTextExecutionRuntimeReceiptResult.Applied)

        val rebuilt = newPorts()
        assertTrue(rebuilt.reserve(plan.usageReservation, now.plusSeconds(1)) is RealTextExecutionUsageReservationResult.Replayed)
        assertTrue(rebuilt.start(plan, now.plusSeconds(1)) is RealTextExecutionRuntimeReceiptResult.Replayed)
        assertTrue(rebuilt.appendPartial(plan, "transient-after-rebuild", now.plusSeconds(2)) is RealTextExecutionRuntimeReceiptResult.Applied)
        assertTrue(rebuilt.reserve(plan.usageReservation.copy(budgetMicros = 531), now.plusSeconds(3)) is RealTextExecutionUsageReservationResult.Rejected)
        assertTrue(rebuilt.start(plan.copy(cancellation = plan.cancellation.copy(requestFingerprint = "f".repeat(64))), now.plusSeconds(3)) is RealTextExecutionRuntimeReceiptResult.Rejected)
        assertTrue(rebuilt.finish(plan, ConversationRealTextExecutionState.CANCELLED, "TEST_REBUILD_CANCELLED", now.plusSeconds(4)) is RealTextExecutionRuntimeReceiptResult.Applied)
        assertEquals(ConversationRealTextExecutionState.CANCELLED, executions.findById(plan.cancellation.executionId)!!.state)
    }

    @Test fun `outer Room transaction rolls back both reservation and running fact`() {
        val plan = preparePlan("rollback")
        val failure = runCatching {
            database.runInTransaction(Callable {
                assertTrue(ports.reserve(plan.usageReservation, now) is RealTextExecutionUsageReservationResult.Reserved)
                assertTrue(ports.start(plan, now) is RealTextExecutionRuntimeReceiptResult.Applied)
                throw IllegalStateException("test rollback")
            })
        }

        assertTrue(failure.isFailure)
        assertEquals(ConversationRealTextExecutionState.PREPARED, executions.findById(plan.cancellation.executionId)!!.state)
        assertTrue(usage.entriesForExecution(plan.cancellation.executionId).isEmpty())
    }

    private fun preparePlan(label: String): RealTextExecutionReadyPlan {
        val request = ConversationRealTextExecutionRequest(
            executionId = ConversationRealTextExecutionId("p3c-$label-execution"),
            idempotencyKey = "p3c-$label-idempotency",
            requestFingerprint = label.length.toString(16).padStart(64, 'a'),
            conversationId = ConversationId("p3c-$label-conversation"),
            userMessageId = MessageNodeId("p3c-$label-user"),
            assistantMessageId = MessageNodeId("p3c-$label-assistant"),
            invocationId = InvocationId("p3c-$label-invocation"),
            attemptId = ProviderAttemptId("p3c-$label-attempt"),
            createdAt = now,
        )
        assertTrue(executions.prepare(request) is ConversationRealTextExecutionPrepareResult.Prepared)
        return RealTextExecutionReadyPlan(
            preflightId = RealTextExecutionPreflightId("p3c-$label-preflight"),
            cancellation = RealTextExecutionCancellationIdentity(
                RealTextExecutionPreflightId("p3c-$label-preflight"), request.executionId, request.invocationId,
                request.attemptId, request.requestFingerprint,
            ),
            providerHandle = ProviderTransportProviderHandle("test-provider"),
            modelId = ProviderTransportModelId("test-provider/test-model"),
            presetId = ModelPresetId.GPT_5_6_TERRA,
            feeConfirmationFingerprint = "b".repeat(64),
            usageReservation = RealTextUsageReservationPlan(
                replayToken = "p3c-$label-reservation",
                executionId = request.executionId,
                conversationId = request.conversationId,
                branchLeafMessageId = request.userMessageId,
                invocationId = request.invocationId,
                attemptId = request.attemptId,
                requestedModelId = ProviderTransportModelId("test-provider/test-model"),
                conservativeInputTokenUpperBound = 3,
                reservedOutputTokens = 100,
                budgetMicros = 530,
                currencyCode = "USD",
                reconciliationFingerprint = "c".repeat(64),
                source = UsageLedgerSource.ANDROID_LOCAL,
            ),
            attachments = emptyList(),
            plannedAt = now,
        )
    }

    private fun coordinator(
        plan: RealTextExecutionReadyPlan,
        events: List<ProviderTransportNormalizedEvent>,
        outcome: ProviderTransportTerminalOutcome,
        error: com.nanzhufeng.ai.domain.ProviderTransportSafeErrorCode? = null,
    ) = RealTextExecutionCoordinator(
        transport = object : RealTextExecutionCoordinatorTransport {
            override fun execute(
                plan: RealTextExecutionReadyPlan,
                cancellation: RealTextExecutionCoordinatorCancellation,
                sink: ProviderTransportEventSink,
            ): ProviderTransportSafeResult {
                events.forEach(sink::onEvent)
                return ProviderTransportSafeResult(ProviderTransportSafeRunMetadata(
                    plan.cancellation.executionId, plan.cancellation.invocationId, plan.cancellation.attemptId,
                    plan.cancellation.requestFingerprint, plan.providerHandle, plan.modelId, null, outcome, error, events.size,
                ))
            }
        },
        runtimeReceipts = ports,
        usageReservations = ports,
        now = { now },
    )

    private fun newPorts() = RoomRealTextExecutionCoordinatorTestPortAdapter(database, executions, usage)

    private fun assertNoSensitivePersistenceSurface() {
        val prohibited = setOf("prompt", "response", "credential", "secret", "authorization", "uri", "path", "attachment", "payload", "delta")
        listOf("conversation_real_text_executions", "usage_ledger_entries").forEach { table ->
            val columns = mutableSetOf<String>()
            database.openHelper.writableDatabase.query("PRAGMA table_info($table)").use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name")).lowercase()
            }
            assertFalse(columns.any { column -> prohibited.any(column::contains) })
        }
        assertNull(executions.findById(ConversationRealTextExecutionId("missing")))
    }
}
