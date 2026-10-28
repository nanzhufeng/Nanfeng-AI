package com.nanzhufeng.ai.acceptance

import java.io.File
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

/** Acceptance-only phase evidence. Delegates TLS and bytes unchanged; never records content or headers. */
internal object PublicLiveConnectionTrace {
    fun open(endpoint: String, file: File): HttpsURLConnection {
        val delegate = URL(endpoint).openConnection() as HttpsURLConnection
        fun record(phase: String, error: Throwable? = null, value: Long? = null) {
            synchronized(PublicLiveConnectionTrace) {
                val rows = runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
                rows.put(JSONObject().apply {
                    put("phase", phase)
                    put("host", delegate.url.host)
                    put("atMillis", System.currentTimeMillis())
                    value?.let { put("value", it) }
                    error?.let { problem ->
                        put("errorClass", problem.javaClass.simpleName)
                        put("causeClass", problem.cause?.javaClass?.simpleName)
                        val message = generateSequence(problem) { it.cause }.take(8)
                            .mapNotNull { it.message }.joinToString(" ").lowercase()
                        put("errorTerms", JSONArray(listOf("unexpected end", "stream was reset", "connection reset",
                            "closed", "broken pipe", "timeout", "content-length", "handshake", "ssl",
                            "refused", "goaway", "internal_error", "eof", "cancelled", "insufficient",
                            "exceeded", "too many").filter { it in message }))
                        put("frames", JSONArray(problem.stackTrace.take(5).map { "${it.className}.${it.methodName}:${it.lineNumber}" }))
                    }
                })
                val temporary = File(file.parentFile, file.name + ".tmp")
                temporary.writeText(rows.toString(2))
                check(temporary.renameTo(file))
            }
        }
        return object : HttpsURLConnection(delegate.url) {
            override fun connect() = delegate.connect()
            override fun disconnect() = delegate.disconnect()
            override fun usingProxy() = delegate.usingProxy()
            override fun getCipherSuite(): String = delegate.cipherSuite
            override fun getLocalCertificates(): Array<Certificate>? = delegate.localCertificates
            override fun getServerCertificates(): Array<Certificate> = delegate.serverCertificates
            override fun setRequestMethod(method: String) { delegate.requestMethod = method }
            override fun setInstanceFollowRedirects(value: Boolean) { delegate.instanceFollowRedirects = value }
            override fun setDoInput(value: Boolean) { delegate.doInput = value }
            override fun setDoOutput(value: Boolean) { delegate.doOutput = value }
            override fun setUseCaches(value: Boolean) { delegate.useCaches = value }
            override fun setConnectTimeout(value: Int) { delegate.connectTimeout = value }
            override fun setReadTimeout(value: Int) { delegate.readTimeout = value }
            override fun setFixedLengthStreamingMode(value: Long) = delegate.setFixedLengthStreamingMode(value)
            override fun setFixedLengthStreamingMode(value: Int) = delegate.setFixedLengthStreamingMode(value)
            override fun setRequestProperty(name: String, value: String) = delegate.setRequestProperty(name, value)
            override fun getOutputStream(): OutputStream {
                record("OPEN_OUTPUT")
                val stream = try { delegate.outputStream } catch (error: Throwable) {
                    record("OPEN_OUTPUT_FAILED", error); throw error
                }
                return object : FilterOutputStream(stream) {
                    private var written = 0L
                    override fun write(value: Int) { out.write(value); written++ }
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        try { out.write(bytes, offset, length); written += length }
                        catch (error: Throwable) { record("WRITE_FAILED", error, written); throw error }
                    }
                    override fun close() {
                        try { out.close(); record("BODY_CLOSED", value = written) }
                        catch (error: Throwable) { record("BODY_CLOSE_FAILED", error, written); throw error }
                    }
                }
            }
            override fun getResponseCode(): Int = try {
                delegate.responseCode.also { record("RESPONSE_HEADERS", value = it.toLong()) }
            } catch (error: Throwable) { record("RESPONSE_HEADERS_FAILED", error); throw error }
            override fun getInputStream(): InputStream {
                val stream = try { delegate.inputStream } catch (error: Throwable) {
                    record("OPEN_INPUT_FAILED", error); throw error
                }
                return object : FilterInputStream(stream) {
                    private var received = 0L
                    override fun read(): Int = try { super.read().also { if (it >= 0) received++ } }
                    catch (error: Throwable) { record("READ_FAILED", error, received); throw error }
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = try {
                        super.read(bytes, offset, length).also { if (it > 0) received += it }
                    } catch (error: Throwable) { record("READ_FAILED", error, received); throw error }
                    override fun close() { super.close(); record("INPUT_CLOSED", value = received) }
                }
            }
            override fun getErrorStream(): InputStream? = delegate.errorStream
        }
    }
}
