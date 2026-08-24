package com.nanzhufeng.ai.ai

import java.io.ByteArrayOutputStream
import java.io.BufferedReader
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/**
 * Small common transport for the three reviewed, fixed OpenAI-compatible endpoints.  It does
 * not accept a user-supplied URL, persist a request, or log headers/body/response.
 */
data class ProviderChatRequest(
    val endpoint: String,
    val jsonBody: String,
    /** Optional streaming body; when absent, [jsonBody] is sent as ordinary UTF-8 JSON. */
    val body: ProviderChatRequestBody? = null,
    val expectsStream: Boolean = false,
    val onTextDelta: (String) -> Unit = {},
    val onAccepted: () -> Unit = {},
    /** Provider-specific JSON projection. Framing remains transport-owned, fields do not. */
    val streamEventDecoder: (String) -> ProviderSseEvent? = { null },
    val idempotencyKey: String = UUID.randomUUID().toString(),
    /** Adapter-selected per-read deadline; a supported long document may need a longer first token. */
    val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MS,
    /** Derived from the resolved model output ceiling; never a fixed UI text-truncation limit. */
    val maxResponseBytes: Int = ProviderResponseByteBudget.DEFAULT_BYTES,
    val cancellation: ProviderChatCancellation? = null,
) {
    init {
        require(readTimeoutMillis in 1_000..300_000)
        require(maxResponseBytes in ProviderResponseByteBudget.MIN_BYTES..ProviderResponseByteBudget.MAX_BYTES)
    }

    internal fun effectiveBody(): ProviderChatRequestBody = body ?: ProviderChatRequestBody.Utf8Json(jsonBody)
}

/**
 * Request data is written directly to the socket.  Attachment bytes are never expanded into a
 * whole Base64 String in memory merely to form a Chat Completions JSON body.
 */
sealed interface ProviderChatRequestBody {
    val contentLength: Long
    fun writeTo(output: OutputStream)

    data class Utf8Json(private val json: String) : ProviderChatRequestBody {
        private val bytes by lazy { json.toByteArray(Charsets.UTF_8) }
        override val contentLength: Long get() = bytes.size.toLong()
        override fun writeTo(output: OutputStream) = output.write(bytes)
    }

    data class Segmented(private val parts: List<Part>) : ProviderChatRequestBody {
        init { require(parts.isNotEmpty()) }
        override val contentLength: Long = parts.fold(0L) { total, part -> Math.addExact(total, part.contentLength) }
        override fun writeTo(output: OutputStream) {
            parts.forEach { part -> part.writeTo(output) }
        }
    }

    sealed interface Part {
        val contentLength: Long
        fun writeTo(output: OutputStream)

        data class Utf8(val text: String) : Part {
            private val bytes by lazy { text.toByteArray(Charsets.UTF_8) }
            override val contentLength: Long get() = bytes.size.toLong()
            override fun writeTo(output: OutputStream) = output.write(bytes)
        }

        /** The opener is an opaque, already hash-verified app-private source. */
        data class Base64File(val byteCount: Long, val open: () -> InputStream) : Part {
            init { require(byteCount > 0) }
            override val contentLength: Long get() = ((byteCount + 2L) / 3L) * 4L
            override fun writeTo(output: OutputStream) {
                open().use { input ->
                    Base64.getEncoder().wrap(NonClosingOutputStream(output)).use { encoded ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied = Math.addExact(copied, read.toLong())
                            if (copied > byteCount) throw IOException("附件流长度变化")
                            encoded.write(buffer, 0, read)
                        }
                        if (copied != byteCount) throw IOException("附件流长度变化")
                    }
                }
            }
        }
    }
}

private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
    override fun close() = flush()
}

private const val DEFAULT_READ_TIMEOUT_MS = 90_000

/**
 * A bounded transport allocation, scaled from the resolved model's declared output capacity.
 * The generous UTF-8/JSON factor handles non-ASCII text and envelope fields; this does not
 * change the provider's requested token budget or clip a decoded answer.
 */
object ProviderResponseByteBudget {
    const val DEFAULT_BYTES = 1 * 1024 * 1024
    const val MIN_BYTES = 64 * 1024
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val JSON_UTF8_BYTES_PER_TOKEN = 16L
    private const val ENVELOPE_BYTES = 64L * 1024L

    fun forMaxOutputTokens(maxOutputTokens: Long?): Int {
        if (maxOutputTokens == null) return DEFAULT_BYTES
        return (maxOutputTokens.coerceAtLeast(1) * JSON_UTF8_BYTES_PER_TOKEN + ENVELOPE_BYTES)
            .coerceIn(DEFAULT_BYTES.toLong(), MAX_BYTES.toLong()).toInt()
    }
}

/** Provider-neutral projection emitted by an Adapter after it decodes one SSE data payload. */
data class ProviderSseEvent(
    val text: String? = null,
    /** Kept separate from visible text; ordinary chat never merges provider reasoning into an answer. */
    val reasoning: String? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    /** Normal chat never runs tools; retaining this fact prevents a false empty-answer success. */
    val toolCallEncountered: Boolean = false,
)

/** Cancels one in-flight request without retaining headers, body, or response content. */
class ProviderChatCancellation {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var connection: HttpsURLConnection? = null

    fun cancel() {
        if (cancelled.compareAndSet(false, true)) connection?.disconnect()
    }

    internal fun attach(connection: HttpsURLConnection) {
        this.connection = connection
        if (cancelled.get()) connection.disconnect()
    }

    fun isCancelled(): Boolean = cancelled.get()
}

sealed interface ProviderChatOutcome {
    data class HttpResponse(val statusCode: Int, val responseBody: String) : ProviderChatOutcome
    /** Text was parsed incrementally from SSE and is never retained as a raw Provider payload. */
    data class StreamedResponse(
        val statusCode: Int,
        val text: String,
        val reasoning: String?,
        val inputTokens: Long?,
        val outputTokens: Long?,
        val toolCallEncountered: Boolean,
    ) : ProviderChatOutcome
    data object TimedOut : ProviderChatOutcome
    data object NetworkFailure : ProviderChatOutcome
    data object ResponseTooLarge : ProviderChatOutcome
    data object Cancelled : ProviderChatOutcome
}

interface ProviderChatTransport {
    fun execute(request: ProviderChatRequest, credential: CharArray): ProviderChatOutcome
}

class OfficialProviderChatTransport : ProviderChatTransport {
    override fun execute(request: ProviderChatRequest, credential: CharArray): ProviderChatOutcome = runCatching {
        val requestBody = request.effectiveBody()
        val connection = (java.net.URL(request.endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            doInput = true
            doOutput = true
            useCaches = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = request.readTimeoutMillis
            setRequestProperty("Accept", if (request.expectsStream) "text/event-stream" else "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Idempotency-Key", request.idempotencyKey)
            setFixedLengthStreamingMode(requestBody.contentLength)
        }
        request.cancellation?.attach(connection)
        try {
            if (request.cancellation?.isCancelled() == true) return ProviderChatOutcome.Cancelled
            connection.setRequestProperty("Authorization", "Bearer ${credential.concatToString()}")
            connection.outputStream.use { requestBody.writeTo(it) }
            if (request.cancellation?.isCancelled() == true) return ProviderChatOutcome.Cancelled
            val status = connection.responseCode
            if (status in 200..299) request.onAccepted()
            if (status in 200..299 && request.expectsStream) {
                val streamed = connection.inputStream.use { stream ->
                    ProviderSseDecoder.read(stream, request.onTextDelta, request.streamEventDecoder)
                }
                return ProviderChatOutcome.StreamedResponse(
                    status, streamed.text, streamed.reasoning, streamed.inputTokens, streamed.outputTokens, streamed.toolCallEncountered,
                )
            }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readAtMost(request.maxResponseBytes) }
            if (stream != null && body == null) return ProviderChatOutcome.ResponseTooLarge
            ProviderChatOutcome.HttpResponse(status, body?.toString(Charsets.UTF_8).orEmpty())
        } finally {
            connection.disconnect()
        }
    }.getOrElse { error ->
        when {
            request.cancellation?.isCancelled() == true -> ProviderChatOutcome.Cancelled
            error is SocketTimeoutException -> ProviderChatOutcome.TimedOut
            else -> ProviderChatOutcome.NetworkFailure
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
    }
}

/**
 * Minimal tolerant SSE framing for ordinary chat. Provider-specific JSON is decoded by the
 * Adapter; comments and empty events are ignored, and one malformed chunk does not discard
 * prior visible text. The raw event JSON never escapes this method.
 */
internal object ProviderSseDecoder {
    data class Result(val text: String, val reasoning: String?, val inputTokens: Long?, val outputTokens: Long?, val toolCallEncountered: Boolean)

    fun read(input: java.io.InputStream, onDelta: (String) -> Unit, decodeEvent: (String) -> ProviderSseEvent?): Result {
        var inputTokens: Long? = null
        var outputTokens: Long? = null
        var toolCallEncountered = false
        val output = StringBuilder()
        val reasoning = StringBuilder()
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            var eventData = StringBuilder()
            fun consumeEvent() {
                val data = eventData.toString().trim()
                eventData = StringBuilder()
                if (data.isBlank() || data == "[DONE]") return
                decodeEvent(data)?.let { event ->
                    event.text?.let { delta -> output.append(delta); onDelta(delta) }
                    event.reasoning?.let(reasoning::append)
                    event.inputTokens?.let { inputTokens = it }
                    event.outputTokens?.let { outputTokens = it }
                    toolCallEncountered = toolCallEncountered || event.toolCallEncountered
                }
            }
            while (true) {
                val line = reader.readLine() ?: break
                when {
                    line.isEmpty() -> consumeEvent()
                    line.startsWith(":") -> Unit
                    line.startsWith("data:") -> {
                        if (eventData.isNotEmpty()) eventData.append('\n')
                        eventData.append(line.removePrefix("data:").trimStart())
                    }
                }
            }
            consumeEvent()
        }
        return Result(output.toString(), reasoning.toString().takeIf(String::isNotBlank), inputTokens, outputTokens, toolCallEncountered)
    }
}

private fun java.io.InputStream.readAtMost(maxBytes: Int): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        if (output.size() + read > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
