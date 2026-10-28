package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test

class AttachmentInventoryFailureContractsTest {
    private fun read(outcome: ProviderChatOutcome): AttachmentSearchInventory.Result {
        val model = ResolvedModel(ProviderId.OPENROUTER, "google/gemini-4-argon", "Gemini 4 Argon",
            ModelCapabilities(true, true, true), 1048576, ModelHealth.UNKNOWN, null, null, 65536)
        return AttachmentSearchInventory.read(model, OpenRouterChatAdapter(), "https://openrouter.ai/api/v1",
            emptyList(), object : ProviderChatTransport {
                override fun execute(request: ProviderChatRequest, credential: CharArray) = outcome
            }, { "synthetic-key".toCharArray() }, null, {})
    }
    @Test fun `TLS network interruption remains network failure instead of malformed data`() {
        val failed = read(ProviderChatOutcome.NetworkFailure(ProviderNetworkFailureKind.TLS,
            "SSLHandshakeException", "EOFException")) as AttachmentSearchInventory.Result.Failed
        assertEquals("NETWORK", failed.code)
        assertEquals("TRANSPORT_TLS_SSLHandshakeException_CAUSE_EOFException", failed.safeDiagnosticDetail)
    }
    @Test fun `network diagnostic excludes unsafe exception labels`() {
        val failed = read(ProviderChatOutcome.NetworkFailure(ProviderNetworkFailureKind.TLS,
            "private exception message", "private body text")) as AttachmentSearchInventory.Result.Failed
        assertEquals("TRANSPORT_TLS", failed.safeDiagnosticDetail)
    }
    @Test fun `timeout remains timeout instead of malformed data`() {
        assertEquals("TIMEOUT", (read(ProviderChatOutcome.TimedOut) as AttachmentSearchInventory.Result.Failed).code)
    }
    @Test fun `HTTP rejection retains status instead of malformed data`() {
        assertEquals("HTTP_403", (read(ProviderChatOutcome.HttpResponse(403, "{}")) as AttachmentSearchInventory.Result.Failed).code)
    }
    @Test fun `invalid successful response is still response format failure`() {
        assertEquals("RESPONSE_FORMAT", (read(ProviderChatOutcome.HttpResponse(200, "{}")) as AttachmentSearchInventory.Result.Failed).code)
    }
}
