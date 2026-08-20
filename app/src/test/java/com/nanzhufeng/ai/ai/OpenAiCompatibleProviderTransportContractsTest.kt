package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ConversationRealTextExecutionId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionTransportHandle
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.ProviderTransportCancellationToken
import com.nanzhufeng.ai.domain.ProviderTransportEventSink
import com.nanzhufeng.ai.domain.ProviderTransportModelId
import com.nanzhufeng.ai.domain.ProviderTransportNormalizedEvent
import com.nanzhufeng.ai.domain.ProviderTransportProviderHandle
import com.nanzhufeng.ai.domain.ProviderTransportRequest
import com.nanzhufeng.ai.domain.ProviderTransportRoute
import com.nanzhufeng.ai.domain.ProviderTransportEphemeralTextInput
import com.nanzhufeng.ai.domain.ProviderTransportNotCancelled
import com.nanzhufeng.ai.domain.ProviderTransportSafeErrorCode
import com.nanzhufeng.ai.domain.ProviderTransportTerminalOutcome
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleProviderTransportContractsTest {
    @Test fun `fixed preset encodes exact openai compatible request and receives only an opaque handle`() {
        val client = FakeHttpClient(jsonResponse("{\"model\":\"openrouter/test-v1\",\"choices\":[{\"message\":{\"content\":\"本地 mock 响应\"}}]}"))
        val result = transport(OpenAiCompatibleResponseMode.JSON, client).execute(request(), ProviderTransportNotCancelled, ProviderTransportEventSink { })

        val sent = requireNotNull(client.request)
        assertEquals(OpenAiCompatibleProviderPreset.OPENROUTER, sent.preset)
        assertEquals("POST", sent.method)
        assertEquals(setOf("Accept", "Content-Type", "Idempotency-Key"), sent.headers.keys)
        assertEquals(
            "{\"model\":\"openrouter/test-v1\",\"messages\":[{\"role\":\"user\",\"content\":\"本地合成输入\"}],\"stream\":false,\"temperature\":0.2,\"max_tokens\":128}",
            sent.body.toString(StandardCharsets.UTF_8),
        )
        assertEquals(ProviderTransportTerminalOutcome.COMPLETED, result.metadata.terminalOutcome)
        assertFalse(OpenAiCompatibleHttpRequest::class.java.declaredFields.any { it.name.contains("authorization", true) || it.name.contains("credential", true) })
        assertTrue(ProtectedProviderCredentialHandle::class.java.methods.none { it.name.contains("key", true) || it.name.contains("decrypt", true) })
    }

    @Test fun `sse is normalized into the existing p3 stream without raw payload result`() {
        val sse = """
            data: {"model":"openrouter/test-v1","choices":[{"delta":{"content":"甲"}}]}

            data: {"choices":[{"delta":{"content":"乙"}}]}

            data: [DONE]
        """.trimIndent()
        val observed = mutableListOf<ProviderTransportNormalizedEvent>()
        val result = transport(OpenAiCompatibleResponseMode.SERVER_SENT_EVENTS, FakeHttpClient(sseResponse(sse)))
            .execute(request(), ProviderTransportNotCancelled, ProviderTransportEventSink { observed += it })

        assertEquals(listOf("Started", "TextDelta", "TextDelta", "Completed"), observed.map { it::class.simpleName })
        assertEquals("甲", (observed[1] as ProviderTransportNormalizedEvent.TextDelta).text)
        assertEquals("乙", (observed[2] as ProviderTransportNormalizedEvent.TextDelta).text)
        assertEquals(ProviderTransportTerminalOutcome.COMPLETED, result.metadata.terminalOutcome)
        assertTrue(result.metadata.actualModelId == ProviderTransportModelId("openrouter/test-v1"))
    }

    @Test fun `malformed nonstream response and http status become safe failures without raw payload metadata`() {
        val malformed = transport(OpenAiCompatibleResponseMode.JSON, FakeHttpClient(jsonResponse("{\"choices\":[]}")))
            .execute(request(), ProviderTransportNotCancelled, ProviderTransportEventSink { })
        assertEquals(ProviderTransportSafeErrorCode.PROVIDER_RESPONSE_MALFORMED, malformed.metadata.safeErrorCode)

        val rateLimited = transport(OpenAiCompatibleResponseMode.JSON, FakeHttpClient(OpenAiCompatibleHttpOutcome.Response(429, "application/json", "ignored".toByteArray())))
            .execute(request(), ProviderTransportNotCancelled, ProviderTransportEventSink { })
        assertEquals(ProviderTransportSafeErrorCode.PROVIDER_RATE_LIMITED, rateLimited.metadata.safeErrorCode)
        assertTrue(OpenAiCompatibleHttpOutcome.Response::class.java.declaredFields.none { it.name.contains("error", true) })
        assertTrue(com.nanzhufeng.ai.domain.ProviderTransportSafeRunMetadata::class.java.declaredFields.none {
            it.name.contains("body", true) || it.name.contains("payload", true) || it.name.contains("status", true) || it.name.contains("credential", true)
        })
    }

    @Test fun `cancellation before dispatch never calls client and emits a safe terminal`() {
        val client = FakeHttpClient(jsonResponse("{\"choices\":[{\"message\":{\"content\":\"不应发送\"}}]}"))
        val result = transport(OpenAiCompatibleResponseMode.JSON, client)
            .execute(request(), object : ProviderTransportCancellationToken {
                override fun isCancellationRequested(): Boolean = true
            }, ProviderTransportEventSink { })
        assertEquals(0, client.calls)
        assertEquals(ProviderTransportTerminalOutcome.CANCELLED, result.metadata.terminalOutcome)
        assertEquals(ProviderTransportSafeErrorCode.TRANSPORT_CANCELLED, result.metadata.safeErrorCode)
    }

    @Test fun `cancellation after mock response skips parsing and text projection`() {
        val token = SwitchingCancellationToken()
        val client = object : OpenAiCompatibleHttpClient {
            var calls = 0
            override fun execute(
                request: OpenAiCompatibleHttpRequest,
                credential: ProtectedProviderCredentialHandle,
                cancellation: ProviderTransportCancellationToken,
            ): OpenAiCompatibleHttpOutcome {
                calls += 1
                token.cancel()
                return jsonResponse("{\"choices\":[{\"message\":{\"content\":\"不得投影\"}}]}")
            }
        }
        val observed = mutableListOf<ProviderTransportNormalizedEvent>()
        val result = transport(OpenAiCompatibleResponseMode.JSON, client).execute(
            request(), token, ProviderTransportEventSink { observed += it },
        )
        assertEquals(1, client.calls)
        assertEquals(listOf("Cancelled"), observed.map { it::class.simpleName })
        assertEquals(ProviderTransportTerminalOutcome.CANCELLED, result.metadata.terminalOutcome)
    }

    @Test fun `default client is fail closed and never fabricates text or invokes a network client`() {
        val observed = mutableListOf<ProviderTransportNormalizedEvent>()
        val result = OpenAiCompatibleProviderTransport(handle(), OpenAiCompatibleRequestProfile(OpenAiCompatibleResponseMode.JSON))
            .execute(request(), ProviderTransportNotCancelled, ProviderTransportEventSink { observed += it })
        assertTrue(observed.isEmpty())
        assertEquals(ProviderTransportTerminalOutcome.DISABLED_NO_NETWORK, result.metadata.terminalOutcome)
        assertEquals(ProviderTransportSafeErrorCode.TRANSPORT_DISABLED_NO_NETWORK, result.metadata.safeErrorCode)
    }

    @Test fun `mismatched protected handle fails before mock dispatch`() {
        val client = FakeHttpClient(jsonResponse("{\"choices\":[{\"message\":{\"content\":\"不应发送\"}}]}"))
        val result = OpenAiCompatibleProviderTransport(
            object : ProtectedProviderCredentialHandle {
                override val providerHandle = "another-provider"
                override val handleReference = "test-ref"
            },
            OpenAiCompatibleRequestProfile(OpenAiCompatibleResponseMode.JSON), client,
        ).execute(request(), ProviderTransportNotCancelled, ProviderTransportEventSink { })
        assertEquals(0, client.calls)
        assertEquals(ProviderTransportSafeErrorCode.PROVIDER_AUTHORIZATION_FAILED, result.metadata.safeErrorCode)
    }

    private fun transport(mode: OpenAiCompatibleResponseMode, client: OpenAiCompatibleHttpClient) = OpenAiCompatibleProviderTransport(
        handle(), OpenAiCompatibleRequestProfile(mode, maxOutputTokens = 128), client,
    )

    private fun handle() = object : ProtectedProviderCredentialHandle {
        override val providerHandle = "openrouter"
        override val handleReference = "test-protected-handle"
    }

    private fun request() = ProviderTransportRequest(
        execution = ConversationRealTextExecutionTransportHandle(
            ConversationRealTextExecutionId("p3-http-execution"), InvocationId("p3-http-invocation"),
            ProviderAttemptId("p3-http-attempt"), "b".repeat(64),
        ),
        route = ProviderTransportRoute(ProviderTransportProviderHandle("openrouter"), ProviderTransportModelId("openrouter/test-v1")),
        input = ProviderTransportEphemeralTextInput("本地合成输入"),
    )

    private fun jsonResponse(body: String) = OpenAiCompatibleHttpOutcome.Response(200, "application/json; charset=utf-8", body.toByteArray())
    private fun sseResponse(body: String) = OpenAiCompatibleHttpOutcome.Response(200, "text/event-stream", body.toByteArray())

    private class FakeHttpClient(private val next: OpenAiCompatibleHttpOutcome) : OpenAiCompatibleHttpClient {
        var calls = 0
        var request: OpenAiCompatibleHttpRequest? = null
        override fun execute(
            request: OpenAiCompatibleHttpRequest,
            credential: ProtectedProviderCredentialHandle,
            cancellation: ProviderTransportCancellationToken,
        ): OpenAiCompatibleHttpOutcome {
            calls += 1
            this.request = request
            return next
        }
    }

    private class SwitchingCancellationToken : ProviderTransportCancellationToken {
        private var cancelled = false
        override fun isCancellationRequested(): Boolean = cancelled
        fun cancel() { cancelled = true }
    }
}
