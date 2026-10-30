package com.nanzhufeng.ai.ai

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Test

/** Replace only the socket; parsing, terminal handling and callbacks are production code. */
internal class InterruptedStreamConnection(wire: String, private val failRead: Boolean = false,
    private val failClose: Boolean = false, private val failHeaders: Boolean = false, private val readError: java.io.IOException? = null) : HttpsURLConnection(URL("https://example.invalid/chat/completions")) {
    val written = ByteArrayOutputStream()
    var disconnected = false
    var closes = 0
    private val bytes = ByteArrayInputStream(wire.toByteArray())
    override fun getOutputStream() = written
    override fun getResponseCode(): Int { if(failHeaders) throw EOFException("synthetic headers failure"); return 200 }
    override fun getInputStream() = object : InputStream() {
        override fun read(): Int = bytes.read().also { if (it < 0 && failRead) throw (readError ?: EOFException("synthetic read failure")) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            bytes.read(buffer, offset, length).also { if (it < 0 && failRead) throw (readError ?: EOFException("synthetic read failure")) }
        override fun close() { closes++; if (failClose) throw EOFException("synthetic close failure") }
    }
    override fun disconnect() { disconnected = true }
    override fun connect() = Unit
    override fun usingProxy() = false
    override fun getCipherSuite() = "synthetic"
    override fun getLocalCertificates(): Array<Certificate>? = null
    override fun getServerCertificates(): Array<Certificate> = emptyArray()
}

class ProviderStreamInterruptionTest {
    @Test fun `stream socket timeout retains decoded usage and remains timeout`() {
        val connection=InterruptedStreamConnection("data: received\n\n",failRead=true,readError=java.net.SocketTimeoutException("synthetic timeout"))
        val outcome=OfficialProviderChatTransport {connection}.execute(ProviderChatRequest("https://example.invalid/chat/completions","{}",expectsStream=true,
            streamEventDecoder={ProviderSseEvent(text="partial",inputTokens=100,outputTokens=20,reportedProviderCost=ProviderReportedCost(9001,"USD","synthetic-v1"))}),"synthetic".toCharArray())
        assertTrue("received facts must survive timeout",outcome is ProviderChatOutcome.NetworkFailure)
        val failed=outcome as ProviderChatOutcome.NetworkFailure
        assertTrue(failed.diagnosticCode().startsWith("TRANSPORT_TIMEOUT_"));assertEquals(100L,failed.partialResponse?.inputTokens)
        assertEquals(9001L,failed.partialResponse?.reportedProviderCost?.totalMicros)
        assertEquals("MISSING_COMPLETION",failed.partialResponse?.finishReason)
        assertEquals(1,connection.closes)
    }

    @Test fun `SSE response byte cap covers reasoning tools and unterminated lines`() {
        for(prefix in listOf("data: ",": ")) {
            val connection=InterruptedStreamConnection(prefix+"x".repeat(300000))
            val outcome=OfficialProviderChatTransport {connection}.execute(ProviderChatRequest("https://example.invalid/chat/completions","{}",expectsStream=true,
                maxResponseBytes=ProviderResponseByteBudget.MIN_BYTES,
                streamEventDecoder={ProviderSseEvent(reasoning=it)}),"synthetic".toCharArray())
            assertEquals(ProviderChatOutcome.ResponseTooLarge,outcome)
            assertEquals(1,connection.closes)
        }
    }

    @Test fun `complete non streaming body survives cleanup EOF but a read EOF remains unknown`() {
        val body = """{"choices":[{"message":{"content":"complete"},"finish_reason":"stop"}],"usage":{"prompt_tokens":100,"completion_tokens":20}}"""
        val complete = InterruptedStreamConnection(body, failClose=true)
        val response = OfficialProviderChatTransport { complete }.execute(
            ProviderChatRequest("https://example.invalid/chat/completions", "{}", expectsStream=false), "synthetic".toCharArray())
        assertEquals(ProviderChatOutcome.HttpResponse(200, body), response)
        assertEquals(1, complete.closes)
        val interrupted = InterruptedStreamConnection(body, failRead=true, failClose=true)
        val failed = OfficialProviderChatTransport { interrupted }.execute(
            ProviderChatRequest("https://example.invalid/chat/completions", "{}", expectsStream=false), "synthetic".toCharArray())
        assertTrue(failed is ProviderChatOutcome.NetworkFailure)
        assertEquals(1, interrupted.closes)
    }
    @Test fun `header EOF is distinct from body or cleanup failures without fabricating HTTP status`() {
        val connection=InterruptedStreamConnection("",failHeaders=true)
        var opened=0
        val result=OfficialProviderChatTransport {opened++;connection}.execute(
            ProviderChatRequest("https://example.invalid/chat/completions","{}"),"synthetic".toCharArray()) as ProviderChatOutcome.NetworkFailure
        assertEquals(ProviderTransportStage.RESPONSE_HEADERS,result.stage)
        assertNull(result.httpStatus);assertNull(result.partialResponse)
        assertEquals(1,opened)
        assertEquals("{}",connection.written.toString("UTF-8"))
    }

    @Test fun `user cancellation remains cancellation even when the callback also throws`() {
        val connection=InterruptedStreamConnection("data: answer\n\ndata: [DONE]\n\n",failClose=true)
        val cancellation=ProviderChatCancellation()
        val request=ProviderChatRequest("https://example.invalid/chat/completions","{}",expectsStream=true,cancellation=cancellation,
            onTextDelta={cancellation.cancel();throw java.util.concurrent.CancellationException("synthetic cancellation")},
            streamEventDecoder={ProviderSseEvent(text="answer")})
        assertEquals(ProviderChatOutcome.Cancelled,OfficialProviderChatTransport {connection}.execute(request,"synthetic".toCharArray()))
        assertTrue(connection.disconnected)
    }

    @Test fun `cleanup cannot override failed or incomplete provider terminals`() {
        for (terminal in listOf(ProviderStreamTerminal.FAILED,ProviderStreamTerminal.INCOMPLETE)) {
            val connection=InterruptedStreamConnection("data: partial\n\ndata: [DONE]\n\n",failClose=true)
            val request=ProviderChatRequest("https://example.invalid/chat/completions","{}",expectsStream=true,
                streamEventDecoder={ ProviderSseEvent(text="unfinished",terminal=terminal) })
            val result=OfficialProviderChatTransport {connection}.execute(request,"synthetic".toCharArray()) as ProviderChatOutcome.StreamedResponse
            assertEquals(terminal.name,result.finishReason)
        }
    }

    @Test fun `callback errors reach their local owner instead of becoming network failures`() {
        val connection=InterruptedStreamConnection("data: answer\n\ndata: [DONE]\n\n")
        val request=ProviderChatRequest("https://example.invalid/chat/completions","{}",expectsStream=true,
            onTextDelta={ throw java.io.IOException("synthetic persistence failure") },
            streamEventDecoder={ ProviderSseEvent(text="answer",inputTokens=100,outputTokens=20) })
        val error=assertThrows(java.io.IOException::class.java) {
            OfficialProviderChatTransport {connection}.execute(request,"synthetic".toCharArray())
        }
        assertEquals("synthetic persistence failure",error.message)
        assertEquals(1,connection.closes)
        assertTrue(connection.disconnected)
    }

    @Test fun `explicit cancellation still wins over received response and cleanup errors`() {
        val connection=InterruptedStreamConnection("data: answer\n\ndata: [DONE]\n\n",failClose=true)
        val cancellation=ProviderChatCancellation()
        val request=ProviderChatRequest("https://example.invalid/chat/completions","{}",expectsStream=true,cancellation=cancellation,
            onTextDelta={cancellation.cancel()},streamEventDecoder={ProviderSseEvent(text="answer",terminal=ProviderStreamTerminal.COMPLETED)})
        assertEquals(ProviderChatOutcome.Cancelled,OfficialProviderChatTransport {connection}.execute(request,"synthetic".toCharArray()))
    }

    @Test fun `cleanup failure without a provider terminal remains unknown`() {
        val connection=InterruptedStreamConnection("data: partial\n\n",failClose=true)
        val request=ProviderChatRequest("https://example.invalid/chat/completions","{}",expectsStream=true,
            streamEventDecoder={ ProviderSseEvent(text="unfinished") })
        val result=OfficialProviderChatTransport {connection}.execute(request,"synthetic".toCharArray())
        assertTrue("cleanup recovery must not promote unfinished text",result is ProviderChatOutcome.NetworkFailure)
        assertEquals(ProviderTransportStage.RESPONSE_CLOSE,(result as ProviderChatOutcome.NetworkFailure).stage)
        assertEquals(200,result.httpStatus)
    }

    @Test fun `read EOF keeps decoded facts separate from diagnostic strings and never resends`() {
        val connection=InterruptedStreamConnection("data: partial\n\n",failRead=true,failClose=true)
        var opened=0
        val request=ProviderChatRequest("https://example.invalid/chat/completions","{}",expectsStream=true,
            streamEventDecoder={ ProviderSseEvent(text="synthetic private text",reasoning="synthetic private reasoning",
                inputTokens=100,outputTokens=20,returnedModelId="same-model") })
        val result=OfficialProviderChatTransport {opened++;connection}.execute(request,"synthetic".toCharArray()) as ProviderChatOutcome.NetworkFailure
        assertEquals("TRANSPORT_IO_EOFException_AT_RESPONSE_BODY",result.diagnosticCode())
        assertEquals(200,result.httpStatus)
        assertEquals("synthetic private text",result.partialResponse?.text)
        assertEquals("synthetic private reasoning",result.partialResponse?.reasoning)
        assertEquals("MISSING_COMPLETION",result.partialResponse?.finishReason)
        assertEquals("same-model",result.partialResponse?.returnedModelId)
        assertFalse(result.toString().contains("synthetic private"))
        assertEquals(1,opened)
        assertEquals(1,connection.closes)
    }

    @Test fun `cleanup EOF cannot erase a complete parsed answer and settlement`() {
        val connection = InterruptedStreamConnection("data: answer\n\ndata: settled\n\ndata: [DONE]\n\n", failClose=true)
        var opened=0
        val request = ProviderChatRequest("https://example.invalid/chat/completions", "{}", expectsStream=true,
            streamEventDecoder = { data -> when(data) {
                "answer" -> ProviderSseEvent(text="complete answer", terminal=ProviderStreamTerminal.COMPLETED)
                "settled" -> ProviderSseEvent(inputTokens=100, outputTokens=20)
                else -> null
            } })
        val outcome=OfficialProviderChatTransport { opened++; connection }.execute(request,"synthetic".toCharArray())
        assertTrue("parsed completion must survive resource cleanup",outcome is ProviderChatOutcome.StreamedResponse)
        val reply=outcome as ProviderChatOutcome.StreamedResponse
        assertEquals("complete answer",reply.text)
        assertEquals("COMPLETED",reply.finishReason)
        assertEquals(100L,reply.inputTokens)
        assertEquals(20L,reply.outputTokens)
        assertEquals(1,opened)
        assertTrue(connection.disconnected)
    }
}
