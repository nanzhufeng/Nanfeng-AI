package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderTransportBoundaryContractsTest {
    @Test fun `typed ephemeral request has a configurable safe route but no credential or attachment surface`() {
        val request = request()
        assertEquals("demo-provider", request.route.providerHandle.value)
        assertEquals("demo/text-v1", request.route.modelId.value)
        assertEquals("本地合成输入", request.input.text)
        assertTrue(ProviderTransportRequest::class.java.declaredFields.map { it.name }.none {
            it.contains("credential", true) || it.contains("key", true) || it.contains("authorization", true) ||
                it.contains("uri", true) || it.contains("path", true) || it.contains("attachment", true)
        })
    }

    @Test fun `local stub normalizes stream and terminal result retains metadata only`() {
        val observed = mutableListOf<ProviderTransportNormalizedEvent>()
        val result = LocalStubProviderTransport(listOf(
            ProviderTransportUpstreamEvent.StreamStarted(ProviderTransportModelId("demo/text-v1-revision")),
            ProviderTransportUpstreamEvent.TextDelta("局部文本"),
            ProviderTransportUpstreamEvent.Completed,
        )).execute(request(), ProviderTransportNotCancelled, ProviderTransportEventSink { observed += it })

        assertEquals(listOf("Started", "TextDelta", "Completed"), observed.map { it::class.simpleName })
        assertEquals(ProviderTransportTerminalOutcome.COMPLETED, result.metadata.terminalOutcome)
        assertEquals("demo/text-v1-revision", result.metadata.actualModelId!!.value)
        assertEquals(3, result.metadata.normalizedEventCount)
        assertTrue(ProviderTransportSafeRunMetadata::class.java.declaredFields.map { it.name }.none {
            it.contains("input", true) || it.contains("text", true) || it.contains("prompt", true) ||
                it.contains("response", true) || it.contains("credential", true) || it.contains("key", true) ||
                it.contains("authorization", true) || it.contains("uri", true) || it.contains("path", true) ||
                it.contains("attachment", true) || it.contains("token", true) || it.contains("cost", true)
        })
    }

    @Test fun `error classifier exposes only safe categories and strips status from terminal result`() {
        val cases = listOf(
            ProviderTransportFailureSignal(ProviderTransportFailureKind.UNKNOWN, 401) to ProviderTransportSafeErrorCode.PROVIDER_AUTHENTICATION_FAILED,
            ProviderTransportFailureSignal(ProviderTransportFailureKind.UNKNOWN, 402) to ProviderTransportSafeErrorCode.PROVIDER_QUOTA_EXHAUSTED,
            ProviderTransportFailureSignal(ProviderTransportFailureKind.UNKNOWN, 429) to ProviderTransportSafeErrorCode.PROVIDER_RATE_LIMITED,
            ProviderTransportFailureSignal(ProviderTransportFailureKind.UNKNOWN, 504) to ProviderTransportSafeErrorCode.PROVIDER_TIMEOUT,
            ProviderTransportFailureSignal(ProviderTransportFailureKind.SERVICE, 503) to ProviderTransportSafeErrorCode.PROVIDER_UNAVAILABLE,
            ProviderTransportFailureSignal(ProviderTransportFailureKind.MALFORMED_RESPONSE) to ProviderTransportSafeErrorCode.PROVIDER_RESPONSE_MALFORMED,
        )
        cases.forEach { (signal, expected) -> assertEquals(expected, ProviderTransportFailureClassifier.classify(signal)) }

        val failure = LocalStubProviderTransport(listOf(
            ProviderTransportUpstreamEvent.StreamStarted(),
            ProviderTransportUpstreamEvent.Failed(ProviderTransportFailureSignal(ProviderTransportFailureKind.UNKNOWN, 429)),
        )).execute(request(), ProviderTransportNotCancelled, ProviderTransportEventSink { })
        assertEquals(ProviderTransportSafeErrorCode.PROVIDER_RATE_LIMITED, failure.metadata.safeErrorCode)
        assertFalse(ProviderTransportSafeRunMetadata::class.java.declaredFields.any { it.name.contains("status", true) })
    }

    @Test fun `cancellation is terminal and no later scripted delta is delivered`() {
        val cancelled = MutableCancellationToken()
        val observed = mutableListOf<ProviderTransportNormalizedEvent>()
        val result = LocalStubProviderTransport(listOf(
            ProviderTransportUpstreamEvent.StreamStarted(),
            ProviderTransportUpstreamEvent.TextDelta("应当被取消阻止"),
            ProviderTransportUpstreamEvent.Completed,
        ), beforeEach = { cancelled.cancel() }).execute(request(), cancelled, ProviderTransportEventSink { observed += it })

        assertEquals(listOf("Cancelled"), observed.map { it::class.simpleName })
        assertEquals(ProviderTransportTerminalOutcome.CANCELLED, result.metadata.terminalOutcome)
        assertEquals(ProviderTransportSafeErrorCode.TRANSPORT_CANCELLED, result.metadata.safeErrorCode)
    }

    @Test fun `out of order stream is safely failed and disabled transport never fabricates a reply`() {
        val observed = mutableListOf<ProviderTransportNormalizedEvent>()
        val protocolFailure = LocalStubProviderTransport(listOf(ProviderTransportUpstreamEvent.TextDelta("不应开始")))
            .execute(request(), ProviderTransportNotCancelled, ProviderTransportEventSink { observed += it })
        assertEquals(listOf("Failed"), observed.map { it::class.simpleName })
        assertEquals(ProviderTransportSafeErrorCode.TRANSPORT_PROTOCOL_VIOLATION, protocolFailure.metadata.safeErrorCode)

        val disabledEvents = mutableListOf<ProviderTransportNormalizedEvent>()
        val disabled = DisabledNoNetworkProviderTransport.execute(request(), ProviderTransportNotCancelled, ProviderTransportEventSink { disabledEvents += it })
        assertTrue(disabledEvents.isEmpty())
        assertEquals(ProviderTransportTerminalOutcome.DISABLED_NO_NETWORK, disabled.metadata.terminalOutcome)
        assertEquals(ProviderTransportSafeErrorCode.TRANSPORT_DISABLED_NO_NETWORK, disabled.metadata.safeErrorCode)
    }

    private fun request() = ProviderTransportRequest(
        execution = ConversationRealTextExecutionTransportHandle(
            ConversationRealTextExecutionId("p3-transport-execution"), InvocationId("p3-transport-invocation"),
            ProviderAttemptId("p3-transport-attempt"), "a".repeat(64),
        ),
        route = ProviderTransportRoute(ProviderTransportProviderHandle("demo-provider"), ProviderTransportModelId("demo/text-v1")),
        input = ProviderTransportEphemeralTextInput("本地合成输入"),
    )

    private class MutableCancellationToken : ProviderTransportCancellationToken {
        private var cancelled = false
        fun cancel() { cancelled = true }
        override fun isCancellationRequested(): Boolean = cancelled
    }

    /** Test-only local stub; it has no default script and no production registration. */
    private class LocalStubProviderTransport(
        private val events: List<ProviderTransportUpstreamEvent>,
        private val beforeEach: (() -> Unit)? = null,
    ) : ProviderTransport {
        override fun execute(
            request: ProviderTransportRequest,
            cancellation: ProviderTransportCancellationToken,
            sink: ProviderTransportEventSink,
        ): ProviderTransportSafeResult {
            val normalizer = ProviderTransportEventNormalizer(request)
            events.forEach { upstream ->
                beforeEach?.invoke()
                if (cancellation.isCancellationRequested()) return emit(normalizer.cancel(), sink)
                when (val normalized = normalizer.accept(upstream)) {
                    is ProviderTransportNormalizationResult.Event -> sink.onEvent(normalized.event)
                    is ProviderTransportNormalizationResult.Terminal -> return emit(normalized, sink)
                    is ProviderTransportNormalizationResult.Replayed -> return normalized.result
                }
            }
            return emit(normalizer.cancel(), sink)
        }

        private fun emit(
            normalized: ProviderTransportNormalizationResult,
            sink: ProviderTransportEventSink,
        ): ProviderTransportSafeResult = when (normalized) {
            is ProviderTransportNormalizationResult.Terminal -> {
                sink.onEvent(normalized.event)
                normalized.result
            }
            is ProviderTransportNormalizationResult.Replayed -> normalized.result
            is ProviderTransportNormalizationResult.Event -> error("测试脚本必须以终态结束。")
        }
    }
}
