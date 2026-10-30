package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test

class AttachmentInventoryFailureContractsTest {
    @Test fun `interrupted attachment reading retains known usage without becoming ready`() {
        val partial = ProviderChatOutcome.StreamedResponse(200,"partial",null,100,20,null,false,
            finishReason="MISSING_COMPLETION",returnedModelId="google/gemini-4-argon",cachedInputTokens=40,reasoningTokens=10)
        val failed = read(ProviderChatOutcome.NetworkFailure(ProviderNetworkFailureKind.IO,"EOFException",partialResponse=partial)) as AttachmentSearchInventory.Result.Failed
        assertEquals("NETWORK",failed.code)
        assertEquals(100L,failed.reply?.inputTokens)
        assertEquals(20L,failed.reply?.outputTokens)
        assertEquals(40L,failed.reply?.cachedInputTokens)
        assertEquals("google/gemini-4-argon",failed.reply?.returnedModelId)
    }
    @Test fun `unresolved timeout size and missing terminal inventory must stop downstream requests`() {
        val outcomes=listOf(ProviderChatOutcome.HttpResponse(408,"{}"),ProviderChatOutcome.HttpResponse(504,"{}"),
            ProviderChatOutcome.ResponseTooLarge,ProviderChatOutcome.StreamedResponse(200,"partial",null,1,1,null,false))
        for(outcome in outcomes) {
            val result=read(outcome)
            assertTrue("${outcome.javaClass.simpleName} must stay failed",result is AttachmentSearchInventory.Result.Failed)
            assertTrue("${outcome.javaClass.simpleName} must stay unknown",(result as AttachmentSearchInventory.Result.Failed).unknown)
        }
    }
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
