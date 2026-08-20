package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealTextExecutionCoordinatorContractsTest {
    private val now = Instant.parse("2026-08-15T14:00:00Z")

    @Test fun `ready plan coordinates reserve start partial terminal and replay without retaining text`() {
        val usage = RecordingUsagePort()
        val runtime = RecordingRuntimePort()
        val transport = ScriptedTransport(listOf(
            ProviderTransportNormalizedEvent.Started(null),
            ProviderTransportNormalizedEvent.TextDelta("合成分段"),
            ProviderTransportNormalizedEvent.Completed,
        ), ProviderTransportTerminalOutcome.COMPLETED)
        val coordinator = RealTextExecutionCoordinator(transport, runtime, usage) { now }

        val first = coordinator.coordinate(plan())
        assertTrue(first is RealTextExecutionCoordinatorResult.Executed)
        val terminal = (first as RealTextExecutionCoordinatorResult.Executed).terminal
        assertTrue(terminal is RealTextExecutionCoordinatorTerminalResult.Succeeded)
        assertEquals(listOf("reserve"), usage.calls)
        assertEquals(listOf("start", "partial", "finish:SUCCEEDED"), runtime.calls)
        assertEquals(1, transport.calls)

        val replay = coordinator.coordinate(plan())
        assertTrue(replay is RealTextExecutionCoordinatorResult.Replayed)
        assertEquals(listOf("reserve"), usage.calls)
        assertEquals(1, transport.calls)
        assertEquals("real-text-execution-coordinator-v1", REAL_TEXT_EXECUTION_COORDINATOR_PROTOCOL)
        assertNoSensitiveReceiptSurface()
    }

    @Test fun `failed stream releases reservation and terminal never exposes provider text`() {
        val usage = RecordingUsagePort()
        val runtime = RecordingRuntimePort()
        val coordinator = RealTextExecutionCoordinator(
            ScriptedTransport(listOf(
                ProviderTransportNormalizedEvent.Started(null),
                ProviderTransportNormalizedEvent.TextDelta("仅在调用栈内"),
                ProviderTransportNormalizedEvent.Failed(ProviderTransportSafeErrorCode.PROVIDER_NETWORK_UNAVAILABLE),
            ), ProviderTransportTerminalOutcome.FAILED, ProviderTransportSafeErrorCode.PROVIDER_NETWORK_UNAVAILABLE),
            runtime, usage,
        ) { now }

        val result = coordinator.coordinate(plan()) as RealTextExecutionCoordinatorResult.Executed
        val failure = result.terminal as RealTextExecutionCoordinatorTerminalResult.Failed
        assertEquals("PROVIDER_NETWORK_UNAVAILABLE", failure.safeErrorCode)
        assertEquals(listOf("reserve", "release:PROVIDER_NETWORK_UNAVAILABLE"), usage.calls)
        assertEquals(listOf("start", "partial", "finish:FAILED"), runtime.calls)
        assertFalse(RealTextExecutionCoordinatorTerminalResult.Failed::class.java.declaredFields.any { it.name.contains("text", true) })
    }

    @Test fun `cancellation after stream start produces one cancelled terminal and releases reservation`() {
        val usage = RecordingUsagePort()
        val runtime = RecordingRuntimePort()
        val externalCancellation = MutableCancellation()
        val transport = object : RealTextExecutionCoordinatorTransport {
            override fun execute(plan: RealTextExecutionReadyPlan, cancellation: RealTextExecutionCoordinatorCancellation, sink: ProviderTransportEventSink): ProviderTransportSafeResult {
                sink.onEvent(ProviderTransportNormalizedEvent.Started(null))
                externalCancellation.cancel()
                assertTrue(cancellation.isCancellationRequested())
                sink.onEvent(ProviderTransportNormalizedEvent.Cancelled(ProviderTransportSafeErrorCode.TRANSPORT_CANCELLED))
                return metadata(plan, ProviderTransportTerminalOutcome.CANCELLED, ProviderTransportSafeErrorCode.TRANSPORT_CANCELLED, 2)
            }
        }
        val coordinator = RealTextExecutionCoordinator(transport, runtime, usage) { now }

        val result = coordinator.coordinate(plan(), externalCancellation) as RealTextExecutionCoordinatorResult.Executed
        val cancelled = result.terminal as RealTextExecutionCoordinatorTerminalResult.Cancelled
        assertEquals("TRANSPORT_CANCELLED", cancelled.safeErrorCode)
        assertEquals(listOf("reserve", "release:TRANSPORT_CANCELLED"), usage.calls)
        assertEquals(listOf("start", "finish:CANCELLED"), runtime.calls)
    }

    @Test fun `default ports reject before transport and a changed same execution plan conflicts`() {
        val defaultResult = RealTextExecutionCoordinator(now = { now }).coordinate(plan()) as RealTextExecutionCoordinatorResult.Executed
        val defaultFailure = defaultResult.terminal as RealTextExecutionCoordinatorTerminalResult.Failed
        assertEquals("COORDINATOR_USAGE_RESERVATION_REJECTED", defaultFailure.safeErrorCode)

        val coordinator = RealTextExecutionCoordinator(
            ScriptedTransport(listOf(ProviderTransportNormalizedEvent.Started(null), ProviderTransportNormalizedEvent.Completed), ProviderTransportTerminalOutcome.COMPLETED),
            RecordingRuntimePort(), RecordingUsagePort(),
        ) { now }
        assertTrue(coordinator.coordinate(plan()) is RealTextExecutionCoordinatorResult.Executed)
        assertTrue(coordinator.coordinate(plan().copy(modelId = ProviderTransportModelId("openrouter/other"))) is RealTextExecutionCoordinatorResult.Conflict)
    }

    private fun plan() = RealTextExecutionReadyPlan(
        preflightId = RealTextExecutionPreflightId("preflight"),
        cancellation = RealTextExecutionCancellationIdentity(
            RealTextExecutionPreflightId("preflight"), ConversationRealTextExecutionId("execution"), InvocationId("invocation"),
            ProviderAttemptId("attempt"), "a".repeat(64),
        ),
        providerHandle = ProviderTransportProviderHandle("openrouter"),
        modelId = ProviderTransportModelId("openrouter/test-v1"),
        presetId = ModelPresetId.GPT_5_6_TERRA,
        feeConfirmationFingerprint = "b".repeat(64),
        usageReservation = RealTextUsageReservationPlan(
            "p3-preflight:preflight", ConversationRealTextExecutionId("execution"), ConversationId("conversation"), MessageNodeId("user"),
            InvocationId("invocation"), ProviderAttemptId("attempt"), ProviderTransportModelId("openrouter/test-v1"),
            3, 100, 530, "USD", "c".repeat(64), UsageLedgerSource.ANDROID_LOCAL,
        ),
        attachments = emptyList(),
        plannedAt = now,
    )

    private class RecordingUsagePort : RealTextExecutionUsageReservationPort {
        val calls = mutableListOf<String>()
        override fun reserve(plan: RealTextUsageReservationPlan, at: Instant): RealTextExecutionUsageReservationResult {
            calls += "reserve"
            return RealTextExecutionUsageReservationResult.Reserved
        }
        override fun release(plan: RealTextUsageReservationPlan, safeErrorCode: String, at: Instant): RealTextExecutionUsageReservationResult {
            calls += "release:$safeErrorCode"
            return RealTextExecutionUsageReservationResult.Reserved
        }
    }

    private class RecordingRuntimePort : RealTextExecutionRuntimeReceiptPort {
        val calls = mutableListOf<String>()
        override fun start(plan: RealTextExecutionReadyPlan, at: Instant): RealTextExecutionRuntimeReceiptResult {
            calls += "start"
            return RealTextExecutionRuntimeReceiptResult.Applied(receipt(plan, ConversationRealTextExecutionState.RUNNING))
        }
        override fun appendPartial(plan: RealTextExecutionReadyPlan, delta: String, at: Instant): RealTextExecutionRuntimeReceiptResult {
            calls += "partial"
            return RealTextExecutionRuntimeReceiptResult.Applied(receipt(plan, ConversationRealTextExecutionState.RUNNING))
        }
        override fun finish(plan: RealTextExecutionReadyPlan, state: ConversationRealTextExecutionState, safeErrorCode: String?, at: Instant): RealTextExecutionRuntimeReceiptResult {
            calls += "finish:$state"
            return RealTextExecutionRuntimeReceiptResult.Applied(receipt(plan, state, safeErrorCode))
        }
        private fun receipt(plan: RealTextExecutionReadyPlan, state: ConversationRealTextExecutionState, error: String? = null) =
            RealTextExecutionRuntimeReceipt(plan.cancellation.executionId, plan.cancellation.invocationId, plan.cancellation.attemptId,
                plan.cancellation.requestFingerprint, state, error)
    }

    private class ScriptedTransport(
        private val events: List<ProviderTransportNormalizedEvent>,
        private val outcome: ProviderTransportTerminalOutcome,
        private val error: ProviderTransportSafeErrorCode? = null,
    ) : RealTextExecutionCoordinatorTransport {
        var calls = 0
        override fun execute(plan: RealTextExecutionReadyPlan, cancellation: RealTextExecutionCoordinatorCancellation, sink: ProviderTransportEventSink): ProviderTransportSafeResult {
            calls += 1
            events.forEach { sink.onEvent(it) }
            return metadata(plan, outcome, error, events.size)
        }
    }

    private class MutableCancellation : RealTextExecutionCoordinatorCancellation {
        private var cancelled = false
        fun cancel() { cancelled = true }
        override fun isCancellationRequested() = cancelled
    }

    private fun assertNoSensitiveReceiptSurface() {
        listOf(RealTextExecutionRuntimeReceipt::class.java, RealTextExecutionCoordinatorTerminalResult.Succeeded::class.java).forEach { type ->
            assertTrue(type.declaredFields.map { it.name }.none {
                it.contains("text", true) || it.contains("prompt", true) || it.contains("credential", true) || it.contains("key", true) ||
                    it.contains("authorization", true) || it.contains("uri", true) || it.contains("path", true) || it.contains("attachment", true) ||
                    it.contains("token", true) || it.contains("cost", true)
            })
        }
    }
}

private fun metadata(
    plan: RealTextExecutionReadyPlan,
    outcome: ProviderTransportTerminalOutcome,
    error: ProviderTransportSafeErrorCode?,
    count: Int,
) = ProviderTransportSafeResult(ProviderTransportSafeRunMetadata(
    plan.cancellation.executionId, plan.cancellation.invocationId, plan.cancellation.attemptId, plan.cancellation.requestFingerprint,
    plan.providerHandle, plan.modelId, null, outcome, error, count,
))
