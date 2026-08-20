package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelRegistryResolution
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.VersionedModelRegistry
import java.math.BigDecimal
import java.math.RoundingMode

/** P2-E is deliberately codec-only: this contract contains no HTTP client, API key or transport. */
enum class OpenRouterMessageRole { SYSTEM, USER, ASSISTANT }

data class OpenRouterMessage(val role: OpenRouterMessageRole, val content: String) {
    init {
        require(content.isNotBlank()) { "OpenRouter 消息不能为空。" }
    }
}

data class OpenRouterChatCompletionRequest(
    val modelId: String,
    val messages: List<OpenRouterMessage>,
    val outputContractVersion: Int,
    val temperature: BigDecimal = BigDecimal("0.2"),
    val stream: Boolean = false,
) {
    init {
        require(modelId.isNotBlank()) { "OpenRouter 请求必须指定模型。" }
        require(messages.isNotEmpty()) { "OpenRouter 请求必须至少包含一条消息。" }
        require(outputContractVersion > 0) { "输出合同版本必须为正数。" }
        require(!stream) { "P2-E 的结构化捕获合同只允许非流式请求。" }
        require(temperature >= BigDecimal.ZERO && temperature <= BigDecimal.ONE) { "温度必须在 0 到 1 之间。" }
    }
}

data class EncodedOpenRouterRequest(val jsonBody: String, val protocolVersion: Int = 1)

data class DecodedOpenRouterResponse(
    val providerRequestId: String?,
    val structuredContent: String,
    val usage: ProviderUsage,
    val cost: ProviderCost,
    val protocolVersion: Int = 1,
)

/** Only these reviewed fields may leave the Adapter as a local, user-reviewable candidate. */
data class SanitizedOpenRouterCandidate(val title: String, val body: String)

sealed interface OpenRouterAdapterEncodeResult {
    data class Encoded(val request: EncodedOpenRouterRequest) : OpenRouterAdapterEncodeResult
    data class Rejected(val error: AiTaskError) : OpenRouterAdapterEncodeResult
}

sealed interface OpenRouterAdapterDecodeResult {
    data class Decoded(val response: DecodedOpenRouterResponse) : OpenRouterAdapterDecodeResult
    data class Rejected(val error: AiTaskError) : OpenRouterAdapterDecodeResult
}

class OpenRouterOfflineAdapterContract(
    private val registry: VersionedModelRegistry,
    private val codec: OpenRouterJsonCodec = OpenRouterJsonCodec(),
) {
    fun encode(presetId: ModelPresetId, request: OpenRouterChatCompletionRequest): OpenRouterAdapterEncodeResult {
        val resolved = registry.resolve(ProviderId.OPENROUTER, presetId)
        if (resolved !is ModelRegistryResolution.Resolved) {
            return OpenRouterAdapterEncodeResult.Rejected((resolved as ModelRegistryResolution.Rejected).error)
        }
        if (resolved.model.id != request.modelId || !resolved.model.capabilities.supportsText ||
            !resolved.model.capabilities.supportsStructuredOutput
        ) {
            return OpenRouterAdapterEncodeResult.Rejected(AiTaskError.ModelRegistryModelUnavailable)
        }
        return OpenRouterAdapterEncodeResult.Encoded(codec.encodeRequest(request))
    }

    fun decode(jsonBody: String): OpenRouterAdapterDecodeResult = codec.decodeResponse(jsonBody)
}

class OpenRouterJsonCodec {
    fun encodeRequest(request: OpenRouterChatCompletionRequest): EncodedOpenRouterRequest {
        val messages = request.messages.joinToString(separator = ",") { message ->
            "{\"role\":${message.role.name.lowercase().asJsonString()},\"content\":${message.content.asJsonString()}}"
        }
        val temperature = request.temperature.stripTrailingZeros().toPlainString()
        return EncodedOpenRouterRequest(
            jsonBody = "{\"model\":${request.modelId.asJsonString()},\"messages\":[$messages],\"stream\":false," +
                "\"temperature\":$temperature,\"response_format\":{\"type\":\"json_object\"}," +
                "\"metadata\":{\"nanfeng_output_contract_version\":${request.outputContractVersion}}}",
        )
    }

    fun decodeResponse(jsonBody: String): OpenRouterAdapterDecodeResult = runCatching {
        val root = StrictJson.parse(jsonBody).asObject()
        val choices = root.requiredArray("choices")
        val firstChoice = choices.firstOrNull().asObject()
        val message = firstChoice.requiredObject("message")
        val content = message.requiredString("content")
        require(content.isNotBlank()) { "响应内容为空。" }
        val usageObject = root.optionalObject("usage")
        val usage = ProviderUsage(
            inputTokens = usageObject.optionalLong("prompt_tokens"),
            outputTokens = usageObject.optionalLong("completion_tokens"),
            totalTokens = usageObject.optionalLong("total_tokens"),
            cachedInputTokens = usageObject.optionalObject("prompt_tokens_details").optionalLong("cached_tokens"),
        )
        val costMicros = usageObject.optionalDecimal("cost")?.toMicros()
        val priceVersion = root.optionalString("price_version") ?: usageObject.optionalString("price_version")
        val currency = root.optionalString("currency") ?: usageObject.optionalString("currency")
        val cost = ProviderCost(
            priceVersion = priceVersion,
            currencyCode = currency,
            totalMicros = costMicros,
        )
        OpenRouterAdapterDecodeResult.Decoded(
            DecodedOpenRouterResponse(
                providerRequestId = root.optionalString("id"),
                structuredContent = content,
                usage = usage,
                cost = cost,
            ),
        )
    }.getOrElse { OpenRouterAdapterDecodeResult.Rejected(AiTaskError.ProviderResponseFormatInvalid) }

    /**
     * Provider JSON is an in-memory transport detail. This projects only title/body and drops
     * every other field before a Candidate can be constructed or persisted.
     */
    fun sanitizeCandidate(structuredContent: String): SanitizedOpenRouterCandidate? = runCatching {
        val objectValue = StrictJson.parse(structuredContent).asObject()
        val title = objectValue.optionalString("title").cleanCandidateField(MAX_TITLE_LENGTH)
        val body = objectValue.optionalString("body").cleanCandidateField(MAX_BODY_LENGTH)
        require(title.isNotEmpty() || body.isNotEmpty()) { "候选内容为空。" }
        SanitizedOpenRouterCandidate(title = title, body = body)
    }.getOrNull()

    private companion object {
        const val MAX_TITLE_LENGTH = 240
        const val MAX_BODY_LENGTH = 12_000
    }
}

private fun String?.cleanCandidateField(maxLength: Int): String = this.orEmpty()
    .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
    .trim()
    .take(maxLength)

private fun String.asJsonString(): String = buildString {
    append('"')
    for (char in this@asJsonString) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
        }
    }
    append('"')
}

private fun BigDecimal.toMicros(): Long = movePointRight(6)
    .setScale(0, RoundingMode.UNNECESSARY)
    .longValueExact()

private fun Any?.asObject(): Map<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    return this as? Map<String, Any?> ?: error("预期 JSON object。")
}

private fun Any?.asArray(): List<Any?> {
    @Suppress("UNCHECKED_CAST")
    return this as? List<Any?> ?: error("预期 JSON array。")
}

private fun Map<String, Any?>.requiredObject(key: String): Map<String, Any?> = get(key).asObject()

private fun Map<String, Any?>.requiredArray(key: String): List<Any?> = get(key).asArray()

private fun Map<String, Any?>.requiredString(key: String): String = get(key) as? String ?: error("缺少字符串 $key。")

private fun Map<String, Any?>?.optionalObject(key: String): Map<String, Any?> = this?.get(key)?.asObject() ?: emptyMap()

private fun Map<String, Any?>.optionalString(key: String): String? = get(key) as? String

private fun Map<String, Any?>.optionalLong(key: String): Long? = when (val value = get(key)) {
    null -> null
    is BigDecimal -> value.longValueExact()
    else -> error("$key 必须是整数。")
}

private fun Map<String, Any?>.optionalDecimal(key: String): BigDecimal? = when (val value = get(key)) {
    null -> null
    is BigDecimal -> value
    else -> error("$key 必须是数值。")
}

/** Shared package-local strict reader for provider codecs; it has no transport or persistence role. */
internal object StrictJson {
    fun parse(input: String): Any? = Reader(input).parse()

    private class Reader(private val input: String) {
        private var index = 0

        fun parse(): Any? {
            skipWhitespace()
            val value = readValue()
            skipWhitespace()
            require(index == input.length) { "JSON 尾部存在额外字符。" }
            return value
        }

        private fun readValue(): Any? {
            skipWhitespace()
            return when (peek()) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readKeyword("true", true)
                'f' -> readKeyword("false", false)
                'n' -> readKeyword("null", null)
                '-', in '0'..'9' -> readNumber()
                else -> error("无效 JSON 值。")
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            val result = linkedMapOf<String, Any?>()
            if (consume('}')) return result
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                result[key] = readValue()
                skipWhitespace()
                if (consume('}')) return result
                expect(',')
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            val result = mutableListOf<Any?>()
            if (consume(']')) return result
            while (true) {
                result += readValue()
                skipWhitespace()
                if (consume(']')) return result
                expect(',')
            }
        }

        private fun readString(): String {
            expect('"')
            return buildString {
                while (true) {
                    val character = next()
                    when (character) {
                        '"' -> return@buildString
                        '\\' -> append(readEscape())
                        else -> {
                            require(character.code >= 0x20) { "JSON 字符串不能包含控制字符。" }
                            append(character)
                        }
                    }
                }
            }
        }

        private fun readEscape(): Char = when (val escaped = next()) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> input.substring(index, index + 4).also { index += 4 }.toInt(16).toChar()
            else -> error("无效 JSON 转义。")
        }

        private fun readNumber(): BigDecimal {
            val start = index
            if (peek() == '-') index += 1
            readDigits()
            if (consume('.')) readDigits()
            if (peekOrNull() == 'e' || peekOrNull() == 'E') {
                index += 1
                if (peekOrNull() == '+' || peekOrNull() == '-') index += 1
                readDigits()
            }
            return input.substring(start, index).toBigDecimal()
        }

        private fun readDigits() {
            val start = index
            while (peekOrNull()?.isDigit() == true) index += 1
            require(index > start) { "JSON 数值缺少数字。" }
        }

        private fun <T> readKeyword(keyword: String, value: T): T {
            require(input.regionMatches(index, keyword, 0, keyword.length)) { "无效 JSON 关键字。" }
            index += keyword.length
            return value
        }

        private fun skipWhitespace() {
            while (peekOrNull()?.isWhitespace() == true) index += 1
        }

        private fun consume(expected: Char): Boolean = if (peekOrNull() == expected) {
            index += 1
            true
        } else false

        private fun expect(expected: Char) {
            require(next() == expected) { "预期 JSON 字符 $expected。" }
        }

        private fun peek(): Char = peekOrNull() ?: error("JSON 意外结束。")
        private fun peekOrNull(): Char? = input.getOrNull(index)
        private fun next(): Char = input.getOrNull(index++) ?: error("JSON 意外结束。")
    }
}
