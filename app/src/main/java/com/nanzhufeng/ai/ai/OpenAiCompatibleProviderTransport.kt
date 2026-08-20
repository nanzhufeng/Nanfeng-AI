package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.DisabledNoNetworkProviderTransport
import com.nanzhufeng.ai.domain.ProviderTransport
import com.nanzhufeng.ai.domain.ProviderTransportCancellationToken
import com.nanzhufeng.ai.domain.ProviderTransportEventNormalizer
import com.nanzhufeng.ai.domain.ProviderTransportEventSink
import com.nanzhufeng.ai.domain.ProviderTransportFailureKind
import com.nanzhufeng.ai.domain.ProviderTransportFailureSignal
import com.nanzhufeng.ai.domain.ProviderTransportModelId
import com.nanzhufeng.ai.domain.ProviderTransportNormalizationResult
import com.nanzhufeng.ai.domain.ProviderTransportNormalizedEvent
import com.nanzhufeng.ai.domain.ProviderTransportRequest
import com.nanzhufeng.ai.domain.ProviderTransportSafeResult
import com.nanzhufeng.ai.domain.ProviderTransportUpstreamEvent
import java.nio.charset.StandardCharsets

/**
 * OpenAI-compatible P3 HTTP core. It deliberately contains no socket, URLConnection, OkHttp,
 * ProviderCredentialStore, or credential decryption implementation. A future separately
 * authorized platform adapter may implement [OpenAiCompatibleHttpClient] behind this boundary.
 */
const val OPENAI_COMPATIBLE_PROVIDER_TRANSPORT_PROTOCOL = "openai-compatible-provider-transport-v1"

/** Only reviewed provider presets may choose an endpoint; callers cannot supply arbitrary URLs. */
enum class OpenAiCompatibleProviderPreset(
    val providerHandle: String,
    val chatCompletionsEndpoint: String,
) {
    OPENROUTER("openrouter", "https://openrouter.ai/api/v1/chat/completions"),
    ;

    companion object {
        fun forHandle(handle: String): OpenAiCompatibleProviderPreset? = entries.firstOrNull { it.providerHandle == handle }
    }
}

/** A handle is opaque metadata. It has no API-key bytes, getter, header value, or decryption API. */
interface ProtectedProviderCredentialHandle {
    val providerHandle: String
    val handleReference: String
}

enum class OpenAiCompatibleResponseMode { SERVER_SENT_EVENTS, JSON }

/** Strictly bounded request knobs; no arbitrary body/header/endpoint extension point exists. */
data class OpenAiCompatibleRequestProfile(
    val responseMode: OpenAiCompatibleResponseMode,
    val temperatureMilli: Int = 200,
    val maxOutputTokens: Int? = null,
) {
    init {
        require(temperatureMilli in 0..1_000) { "温度必须在 0 到 1000 毫之间。" }
        require(maxOutputTokens == null || maxOutputTokens in 1..16_384) { "输出 token 上限不合法。" }
    }
}

/** Request bytes are ephemeral and must be consumed only by the injected HTTP client. */
data class OpenAiCompatibleHttpRequest(
    val preset: OpenAiCompatibleProviderPreset,
    val method: String,
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    init {
        require(method == "POST") { "OpenAI-compatible 文本请求必须使用 POST。" }
        require(headers.keys == setOf("Accept", "Content-Type", "Idempotency-Key")) { "请求头超出受限白名单。" }
        require(headers.values.none(String::isBlank) && body.size in 1..MAX_HTTP_BODY_BYTES) { "HTTP 请求不在安全边界内。" }
    }

    private companion object { const val MAX_HTTP_BODY_BYTES = 256 * 1024 }
}

/** Raw response bytes are in-memory-only transport material, never part of a safe result. */
sealed interface OpenAiCompatibleHttpOutcome {
    data class Response(
        val statusCode: Int,
        val contentType: String,
        val body: ByteArray,
    ) : OpenAiCompatibleHttpOutcome {
        init {
            require(statusCode in 100..599 && contentType.length <= 120 && body.size <= MAX_RESPONSE_BYTES) {
                "HTTP 响应不在安全边界内。"
            }
        }
    }

    data object Cancelled : OpenAiCompatibleHttpOutcome
    data object TimedOut : OpenAiCompatibleHttpOutcome
    data object NetworkUnavailable : OpenAiCompatibleHttpOutcome
    /** The platform adapter could not obtain a locally protected credential before opening a connection. */
    data object CredentialUnavailable : OpenAiCompatibleHttpOutcome
    /** A bounded platform adapter refused an oversized or structurally unsafe response before parsing it. */
    data object ResponseTooLarge : OpenAiCompatibleHttpOutcome
    data object DisabledNoNetwork : OpenAiCompatibleHttpOutcome

    companion object { const val MAX_RESPONSE_BYTES = 1 * 1024 * 1024 }
}

/** Implementations receive an opaque protected handle; this P3 core cannot inspect credential bytes. */
interface OpenAiCompatibleHttpClient {
    fun execute(
        request: OpenAiCompatibleHttpRequest,
        credential: ProtectedProviderCredentialHandle,
        cancellation: ProviderTransportCancellationToken,
    ): OpenAiCompatibleHttpOutcome
}

/** Default is fail-closed and makes no HTTP attempt. */
object DisabledOpenAiCompatibleHttpClient : OpenAiCompatibleHttpClient {
    override fun execute(
        request: OpenAiCompatibleHttpRequest,
        credential: ProtectedProviderCredentialHandle,
        cancellation: ProviderTransportCancellationToken,
    ): OpenAiCompatibleHttpOutcome = OpenAiCompatibleHttpOutcome.DisabledNoNetwork
}

sealed interface OpenAiCompatiblePayloadParseResult {
    data class Parsed(val events: List<ProviderTransportUpstreamEvent>) : OpenAiCompatiblePayloadParseResult
    data object Malformed : OpenAiCompatiblePayloadParseResult
}

/**
 * Strict, transient-only parser for the two OpenAI-compatible response shapes. It emits only
 * normalized control/text events and drops ids, usage, costs, status details, and every payload
 * field not needed to render the stream.
 */
object OpenAiCompatiblePayloadParser {
    fun parse(
        mode: OpenAiCompatibleResponseMode,
        contentType: String,
        body: ByteArray,
    ): OpenAiCompatiblePayloadParseResult = runCatching {
        when (mode) {
            OpenAiCompatibleResponseMode.JSON -> {
                require(contentType.lowercase().startsWith("application/json")) { "响应类型不是 JSON。" }
                parseJson(body.toString(StandardCharsets.UTF_8))
            }
            OpenAiCompatibleResponseMode.SERVER_SENT_EVENTS -> {
                require(contentType.lowercase().startsWith("text/event-stream")) { "响应类型不是 SSE。" }
                parseSse(body.toString(StandardCharsets.UTF_8))
            }
        }
    }.getOrElse { OpenAiCompatiblePayloadParseResult.Malformed }

    private fun parseJson(raw: String): OpenAiCompatiblePayloadParseResult {
        val root = StrictJson.parse(raw).asStrictObject()
        val actualModel = root.optionalSafeModel()
        val choices = root.requiredStrictArray("choices")
        require(choices.size == 1) { "必须恰有一个选择。" }
        val message = choices.single().asStrictObject().requiredStrictObject("message")
        val content = message.requiredStrictString("content")
        require(content.isNotEmpty() && content.length <= MAX_DELTA_CHARS) { "文本内容不合法。" }
        return OpenAiCompatiblePayloadParseResult.Parsed(listOf(
            ProviderTransportUpstreamEvent.StreamStarted(actualModel),
            ProviderTransportUpstreamEvent.TextDelta(content),
            ProviderTransportUpstreamEvent.Completed,
        ))
    }

    private fun parseSse(raw: String): OpenAiCompatiblePayloadParseResult {
        var actualModel: ProviderTransportModelId? = null
        val deltas = mutableListOf<String>()
        var sawDone = false
        raw.lineSequence().forEach { line ->
            if (!line.startsWith("data:")) return@forEach
            val data = line.removePrefix("data:").trimStart()
            require(data.isNotEmpty()) { "SSE data 不能为空。" }
            if (data == "[DONE]") {
                sawDone = true
                return@forEach
            }
            require(!sawDone) { "SSE 终态后仍有数据。" }
            val root = StrictJson.parse(data).asStrictObject()
            val model = root.optionalSafeModel()
            if (model != null) {
                require(actualModel == null || actualModel == model) { "SSE 模型标识不一致。" }
                actualModel = model
            }
            val choices = root.requiredStrictArray("choices")
            require(choices.size == 1) { "必须恰有一个选择。" }
            val delta = choices.single().asStrictObject().requiredStrictObject("delta")
            if (delta.containsKey("content") && delta["content"] != null) {
                val text = delta.requiredStrictString("content")
                require(text.isNotEmpty() && text.length <= MAX_DELTA_CHARS) { "SSE 文本增量不合法。" }
                deltas += text
            }
        }
        require(sawDone) { "SSE 缺少 DONE 终态。" }
        return OpenAiCompatiblePayloadParseResult.Parsed(buildList {
            add(ProviderTransportUpstreamEvent.StreamStarted(actualModel))
            deltas.forEach { add(ProviderTransportUpstreamEvent.TextDelta(it)) }
            add(ProviderTransportUpstreamEvent.Completed)
        })
    }

    private fun Map<String, Any?>.optionalSafeModel(): ProviderTransportModelId? {
        val model = this["model"] ?: return null
        return ProviderTransportModelId(model as? String ?: error("模型标识必须是字符串。"))
    }

    private const val MAX_DELTA_CHARS = 64 * 1024
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asStrictObject(): Map<String, Any?> = this as? Map<String, Any?> ?: error("预期 JSON object。")

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.requiredStrictArray(key: String): List<Any?> = this[key] as? List<Any?> ?: error("缺少数组 $key。")

private fun Map<String, Any?>.requiredStrictObject(key: String): Map<String, Any?> = this[key].asStrictObject()

private fun Map<String, Any?>.requiredStrictString(key: String): String = this[key] as? String ?: error("缺少字符串 $key。")

/**
 * Unregistered infrastructure implementation. The default client is disabled, so constructing
 * this class cannot initiate network activity. No AppContainer, ViewModel, UI, Room, Ledger, or
 * attachment owner refers to it in this increment.
 */
class OpenAiCompatibleProviderTransport(
    private val credential: ProtectedProviderCredentialHandle,
    private val profile: OpenAiCompatibleRequestProfile,
    private val httpClient: OpenAiCompatibleHttpClient = DisabledOpenAiCompatibleHttpClient,
) : ProviderTransport {
    override fun execute(
        request: ProviderTransportRequest,
        cancellation: ProviderTransportCancellationToken,
        sink: ProviderTransportEventSink,
    ): ProviderTransportSafeResult {
        if (cancellation.isCancellationRequested()) return emit(ProviderTransportEventNormalizer(request).cancel(), sink)
        val preset = OpenAiCompatibleProviderPreset.forHandle(request.route.providerHandle.value)
            ?: return fail(request, ProviderTransportFailureKind.INVALID_REQUEST, sink)
        if (credential.providerHandle != request.route.providerHandle.value) {
            return fail(request, ProviderTransportFailureKind.AUTHORIZATION, sink)
        }
        val encoded = OpenAiCompatibleRequestEncoder.encode(preset, request, profile)
        return when (val outcome = httpClient.execute(encoded, credential, cancellation)) {
            OpenAiCompatibleHttpOutcome.DisabledNoNetwork -> DisabledNoNetworkProviderTransport.execute(request, cancellation, sink)
            OpenAiCompatibleHttpOutcome.Cancelled -> emit(ProviderTransportEventNormalizer(request).cancel(), sink)
            OpenAiCompatibleHttpOutcome.TimedOut -> fail(request, ProviderTransportFailureKind.TIMEOUT, sink)
            OpenAiCompatibleHttpOutcome.NetworkUnavailable -> fail(request, ProviderTransportFailureKind.NETWORK, sink)
            OpenAiCompatibleHttpOutcome.CredentialUnavailable -> fail(request, ProviderTransportFailureKind.AUTHORIZATION, sink)
            OpenAiCompatibleHttpOutcome.ResponseTooLarge -> fail(request, ProviderTransportFailureKind.MALFORMED_RESPONSE, sink)
            is OpenAiCompatibleHttpOutcome.Response -> {
                if (cancellation.isCancellationRequested()) return emit(ProviderTransportEventNormalizer(request).cancel(), sink)
                if (outcome.statusCode !in 200..299) return fail(request, ProviderTransportFailureSignal(ProviderTransportFailureKind.UNKNOWN, outcome.statusCode), sink)
                when (val parsed = OpenAiCompatiblePayloadParser.parse(profile.responseMode, outcome.contentType, outcome.body)) {
                    OpenAiCompatiblePayloadParseResult.Malformed -> fail(request, ProviderTransportFailureKind.MALFORMED_RESPONSE, sink)
                    is OpenAiCompatiblePayloadParseResult.Parsed -> emitParsed(request, parsed.events, sink)
                }
            }
        }
    }

    private fun fail(
        request: ProviderTransportRequest,
        kind: ProviderTransportFailureKind,
        sink: ProviderTransportEventSink,
    ): ProviderTransportSafeResult = fail(request, ProviderTransportFailureSignal(kind), sink)

    private fun fail(
        request: ProviderTransportRequest,
        signal: ProviderTransportFailureSignal,
        sink: ProviderTransportEventSink,
    ): ProviderTransportSafeResult = emit(ProviderTransportEventNormalizer(request).failBeforeStream(signal), sink)

    private fun emitParsed(
        request: ProviderTransportRequest,
        events: List<ProviderTransportUpstreamEvent>,
        sink: ProviderTransportEventSink,
    ): ProviderTransportSafeResult {
        val normalizer = ProviderTransportEventNormalizer(request)
        events.forEach { upstream ->
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
        is ProviderTransportNormalizationResult.Event -> error("传输必须以终态结束。")
        is ProviderTransportNormalizationResult.Terminal -> {
            sink.onEvent(normalized.event)
            normalized.result
        }
        is ProviderTransportNormalizationResult.Replayed -> normalized.result
    }
}

object OpenAiCompatibleRequestEncoder {
    fun encode(
        preset: OpenAiCompatibleProviderPreset,
        request: ProviderTransportRequest,
        profile: OpenAiCompatibleRequestProfile,
    ): OpenAiCompatibleHttpRequest {
        val temperature = (profile.temperatureMilli / 1_000.0).toString().trimEnd('0').trimEnd('.').ifEmpty { "0" }
        val maxTokens = profile.maxOutputTokens?.let { ",\"max_tokens\":$it" }.orEmpty()
        val body = "{\"model\":${request.route.modelId.value.jsonString()},\"messages\":[{\"role\":\"user\",\"content\":${request.input.text.jsonString()}}]," +
            "\"stream\":${profile.responseMode == OpenAiCompatibleResponseMode.SERVER_SENT_EVENTS},\"temperature\":$temperature$maxTokens}"
        return OpenAiCompatibleHttpRequest(
            preset = preset,
            method = "POST",
            headers = linkedMapOf(
                "Accept" to if (profile.responseMode == OpenAiCompatibleResponseMode.SERVER_SENT_EVENTS) "text/event-stream" else "application/json",
                "Content-Type" to "application/json; charset=utf-8",
                "Idempotency-Key" to request.execution.executionId.value,
            ),
            body = body.toByteArray(StandardCharsets.UTF_8),
        )
    }
}

private fun String.jsonString(): String = buildString {
    append('"')
    for (char in this@jsonString) when (char) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\b' -> append("\\b")
        '\u000C' -> append("\\f")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
    }
    append('"')
}
