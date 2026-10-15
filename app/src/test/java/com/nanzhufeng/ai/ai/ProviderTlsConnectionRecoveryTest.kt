package com.nanzhufeng.ai.ai

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.OutputStream
import java.net.URL
import java.security.cert.Certificate
import java.security.cert.CertificateException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.*
import org.junit.Test

/** Production transport boundary; only the physical connection is replaced. */
class ProviderTlsConnectionRecoveryTest {
    private fun disconnected() = SSLHandshakeException("synthetic peer close").apply { initCause(EOFException()) }
    private class Connection(
        val setupError: Exception? = null,
        val responseError: Exception? = null,
    ) : HttpsURLConnection(URL("https://example.invalid/chat/completions")) {
        val written = ByteArrayOutputStream()
        var closed = false
        override fun getOutputStream(): OutputStream { setupError?.let { throw it }; return written }
        override fun getResponseCode(): Int { responseError?.let { throw it }; return 200 }
        override fun getInputStream() = ByteArrayInputStream("{}".toByteArray())
        override fun disconnect() { closed = true }
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun getCipherSuite() = "synthetic"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    private fun request() = ProviderChatRequest("https://example.invalid/chat/completions", "{\"model\":\"same-model\"}", expectsStream=false)

    @Test fun `peer EOF before any body bytes reconnects once and sends the exact body once`() {
        val first=Connection(setupError=disconnected());val second=Connection();val connections=listOf(first,second)
        var opened=0;var accepted=0
        val transport=OfficialProviderChatTransport { connections[opened++] }
        val request=request().copy(onAccepted={accepted++})
        assertTrue(transport.execute(request,"synthetic-key".toCharArray()) is ProviderChatOutcome.HttpResponse)
        assertEquals(2,opened);assertEquals(0,first.written.size());assertEquals(request.jsonBody,second.written.toString("UTF-8"))
        assertEquals(1,accepted);assertTrue(first.closed);assertTrue(second.closed)
        assertEquals(first.getRequestProperty("Idempotency-Key"),second.getRequestProperty("Idempotency-Key"))
    }

    @Test fun `certificate validation failure does not reconnect or weaken verification`() {
        val error=SSLHandshakeException("synthetic certificate failure").apply {initCause(CertificateException())}
        var opened=0;val connection=Connection(setupError=error)
        val result=OfficialProviderChatTransport {opened++;connection}.execute(request(),"synthetic-key".toCharArray())
        assertEquals(1,opened);assertEquals(0,connection.written.size())
        assertEquals(ProviderNetworkFailureKind.TLS,(result as ProviderChatOutcome.NetworkFailure).kind)
        assertEquals("CertificateException",result.causeClass)
    }

    @Test fun `disconnect after body transmission never repeats the paid request`() {
        var opened=0;val connection=Connection(responseError=disconnected());val request=request()
        val result=OfficialProviderChatTransport {opened++;connection}.execute(request,"synthetic-key".toCharArray())
        assertEquals(1,opened);assertEquals(request.jsonBody,connection.written.toString("UTF-8"))
        assertTrue(result is ProviderChatOutcome.NetworkFailure)
    }

    @Test fun `repeated pre-body peer disconnect stops after one reconnect`() {
        var opened=0
        val result=OfficialProviderChatTransport {opened++;Connection(setupError=disconnected())}.execute(request(),"synthetic-key".toCharArray())
        assertEquals(2,opened);assertTrue(result is ProviderChatOutcome.NetworkFailure)
    }
}
