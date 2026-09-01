package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.GlmOcrTransport
import com.nanzhufeng.ai.domain.GlmOcrTransportRequest
import com.nanzhufeng.ai.domain.GlmOcrTransportResult
import org.json.JSONArray
import org.json.JSONObject

/** Fixed official GLM-OCR endpoint. It cannot accept a user URL or a chat model selection. */
class OfficialGlmOcrTransport(
    private val transport: ProviderChatTransport = OfficialProviderChatTransport(),
) : GlmOcrTransport {
    override fun execute(request: GlmOcrTransportRequest, credential: CharArray): GlmOcrTransportResult {
        val prefix = "{\"model\":\"glm-ocr\",\"file\":\"data:${request.mimeType};base64,"
        val body = ProviderChatRequestBody.Segmented(listOf(
            ProviderChatRequestBody.Part.Utf8(prefix),
            ProviderChatRequestBody.Part.Base64File(request.source.byteCount, request.source.open),
            ProviderChatRequestBody.Part.Utf8("\"}"),
        ))
        return when (val outcome = transport.execute(
            ProviderChatRequest(
                endpoint = "https://open.bigmodel.cn/api/paas/v4/layout_parsing",
                jsonBody = "",
                body = body,
                expectsStream = false,
                readTimeoutMillis = 300_000,
                maxResponseBytes = ProviderResponseByteBudget.DOCUMENT_MAX_BYTES,
            ),
            credential,
        )) {
            is ProviderChatOutcome.HttpResponse -> if (outcome.statusCode in 200..299) {
                decodeSuccess(outcome.responseBody)
            } else {
                GlmOcrTransportResult.Failed(httpCode(outcome.statusCode))
            }
            ProviderChatOutcome.TimedOut -> GlmOcrTransportResult.Failed("TIMEOUT_UNKNOWN")
            is ProviderChatOutcome.NetworkFailure -> GlmOcrTransportResult.Failed("NETWORK_UNKNOWN")
            ProviderChatOutcome.ResponseTooLarge -> GlmOcrTransportResult.Failed("RESPONSE_TOO_LARGE")
            ProviderChatOutcome.Cancelled -> GlmOcrTransportResult.Failed("CANCELLED")
            is ProviderChatOutcome.StreamedResponse -> GlmOcrTransportResult.Failed("UNEXPECTED_STREAM")
        }
    }

    private fun decodeSuccess(raw: String): GlmOcrTransportResult = runCatching {
        val json = JSONObject(raw)
        val markdown = json.markdownResult()
        if (markdown.isBlank()) return GlmOcrTransportResult.Failed("EMPTY_MARKDOWN")
        val usage = json.optJSONObject("usage")
        val info = json.optJSONObject("data_info")
        GlmOcrTransportResult.Completed(
            markdown = markdown,
            requestId = json.optString("request_id").ifBlank { json.optString("id") }.takeIf(String::isNotBlank),
            pageCount = info?.optInt("num_pages", -1)?.takeIf { it >= 0 },
            inputTokens = usage?.optLongOrNull("prompt_tokens") ?: usage?.optLongOrNull("input_tokens"),
            outputTokens = usage?.optLongOrNull("completion_tokens") ?: usage?.optLongOrNull("output_tokens"),
        )
    }.getOrElse { GlmOcrTransportResult.Failed("RESPONSE_FORMAT") }

    private fun JSONObject.markdownResult(): String {
        val value = opt("md_results")
        return when (value) {
            is String -> value
            is JSONArray -> buildString {
                for (index in 0 until value.length()) {
                    if (isNotEmpty()) append("\n\n")
                    val item = value.opt(index)
                    append(if (item is JSONObject) item.optString("markdown").ifBlank { item.optString("content") } else item?.toString().orEmpty())
                }
            }
            is JSONObject -> value.optString("markdown").ifBlank { value.optString("content") }
            else -> ""
        }
    }

    private fun JSONObject.optLongOrNull(name: String): Long? = if (has(name) && !isNull(name)) optLong(name).takeIf { it >= 0 } else null

    private fun httpCode(status: Int): String = when (status) {
        400 -> "HTTP_400"
        401, 403 -> "AUTHENTICATION"
        402 -> "BALANCE"
        408 -> "HTTP_408_UNKNOWN"
        413 -> "SOURCE_TOO_LARGE"
        429 -> "RATE_LIMIT"
        in 500..599 -> "HTTP_${status}_UNKNOWN"
        else -> "HTTP_$status"
    }
}
