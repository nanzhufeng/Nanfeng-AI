package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AttachmentInputTokenEstimate
import com.nanzhufeng.ai.domain.ErrorBodyRedactor
import com.nanzhufeng.ai.domain.ResolvedModel
import com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ComposerModelChoice
import com.nanzhufeng.ai.domain.ComposerModelSlot
import com.nanzhufeng.ai.domain.classifyProviderFailure
import java.math.BigDecimal
import java.math.RoundingMode

/** Provider boundary: the executor sees only this contract, never provider JSON field names. */
interface ChatProviderAdapter {
    val providerId: ProviderId
    /** Only an Adapter may declare a provider-specific request augmentation. */
    fun requestOptions(model: ResolvedModel, choice: ComposerModelChoice): ChatRequestOptions = ChatRequestOptions.Standard
    /** A hosted model may have a different explicit request receiver from its brand/provider. */
    fun executionProviderId(options: ChatRequestOptions): ProviderId = providerId
    fun endpointPath(options: ChatRequestOptions): String = "/chat/completions"
    fun supportsStreaming(model: ResolvedModel, options: ChatRequestOptions): Boolean = model.capabilities.supportsStreaming
    fun prepare(
        model: ResolvedModel,
        messages: List<Pair<String, String>>,
        attachments: List<ChatAttachment>,
        stream: Boolean,
        options: ChatRequestOptions = ChatRequestOptions.Standard,
    ): ChatAdapterPrepareResult
    /** Transport deadlines are provider protocol metadata, never executor-specific model guesses. */
    fun readTimeoutMillis(model: ResolvedModel, attachments: List<ChatAttachment>, stream: Boolean): Int = 90_000
    fun decodeNonStreaming(body: String): ChatAdapterDecodedResult
    /** The Adapter, rather than the transport, owns its SSE JSON field names. */
    fun decodeStreamingEvent(body: String): ProviderSseEvent?
    fun classifyHttpFailure(status: Int, responseBody: String): ProviderDiagnosticErrorClass
}

/**
 * Request-scoped, provider-owned augmentation. It is intentionally not a model ID heuristic:
 * OpenRouter's server tool is available to any compatible routed model, while direct providers
 * must opt in through their own adapters before the UI can describe a request as web-grounded.
 */
enum class OfficialWebSearchRoute {
    NONE,
    OPENROUTER_SERVER_TOOL,
    QWEN_CHAT_COMPLETIONS,
    QWEN_RESPONSES,
    DEEPSEEK_RESPONSES,
}

data class ChatRequestOptions(
    val webSearchRoute: OfficialWebSearchRoute = OfficialWebSearchRoute.NONE,
) {
    val liveWebSearch: Boolean get() = webSearchRoute != OfficialWebSearchRoute.NONE
    companion object { val Standard = ChatRequestOptions() }
}

sealed interface ChatAdapterPrepareResult {
    data class Ready(
        /** Present for compact text-only diagnostics/contracts; streamed attachments never materialize here. */
        val jsonBody: String,
        val body: ProviderChatRequestBody = ProviderChatRequestBody.Utf8Json(jsonBody),
    ) : ChatAdapterPrepareResult
    data object AttachmentUnsupported : ChatAdapterPrepareResult
}
sealed interface ChatAdapterDecodedResult {
    data class Text(
        val text: String,
        val inputTokens: Long?,
        val outputTokens: Long?,
        /** Retained at the Adapter boundary; ordinary chat does not execute provider tools. */
        val reasoning: String? = null,
        val toolCalls: List<ChatToolCall> = emptyList(),
        /** True even when a provider emitted a malformed or fragmented tool call. */
        val toolCallEncountered: Boolean = false,
        /** Provider-returned public sources; never infer them from the model's prose. */
        val webSources: List<ProviderWebSource> = emptyList(),
        /** OpenRouter's authoritative `usage.cost`, projected as USD micro-units. */
        val reportedCostUsdMicros: Long? = null,
        /** Provider-reported input cache hits; used only by a local estimate when no cost arrives. */
        val cachedInputTokens: Long? = null,
    ) : ChatAdapterDecodedResult
    /** Parsed but deliberately not executed by the ordinary-chat surface. */
    data class ToolCalls(
        val reasoning: String?,
        val toolCalls: List<ChatToolCall>,
        val inputTokens: Long?,
        val outputTokens: Long?,
    ) : ChatAdapterDecodedResult
    data object EmptyOrMalformed : ChatAdapterDecodedResult
}

data class ChatToolCall(val id: String?, val name: String, val argumentsJson: String)

/** A public source returned by a provider's own web-search result, not model-generated metadata. */
data class ProviderWebSource(val url: String, val title: String? = null) {
    init { require(url.startsWith("https://") || url.startsWith("http://")) }
}

/**
 * Preserve provider-returned sources in the user-visible result. The presentation owner turns
 * these Markdown links into the existing source chips, so raw provider JSON never reaches UI.
 */
fun appendProviderWebSources(text: String, sources: List<ProviderWebSource>): String {
    val distinct = sources.asSequence()
        .filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
        .distinctBy { it.url }
        .take(10)
        .toList()
    if (distinct.isEmpty()) return text
    return buildString {
        append(text.trimEnd())
        append("\n\n来源：")
        distinct.forEach { source ->
            append("\n- [")
            append((source.title?.takeIf(String::isNotBlank) ?: source.url.substringAfter("//").substringBefore('/'))
                .replace("[", "(").replace("]", ")"))
            append("](").append(source.url).append(")")
        }
    }
}

enum class ChatAttachmentKind { IMAGE, PDF, VIDEO, AUDIO, FILE }
data class ChatAttachment(
    val kind: ChatAttachmentKind,
    val mimeType: String,
    val fileName: String,
    val byteCount: Long,
    private val openSource: (() -> java.io.InputStream)? = null,
    /** A short-lived HTTPS reference from the configured Nanfeng gateway, never persisted here. */
    val remoteUrl: String? = null,
) {
    init {
        require(byteCount > 0)
        require((openSource == null) != (remoteUrl == null))
        require(remoteUrl == null || remoteUrl.startsWith("https://"))
    }
    constructor(kind: ChatAttachmentKind, mimeType: String, fileName: String, bytes: ByteArray) :
        this(kind, mimeType, fileName, bytes.size.toLong(), { java.io.ByteArrayInputStream(bytes) })
    fun open(): java.io.InputStream = requireNotNull(openSource) { "远程附件不能被重新编码为本地 Base64。" }.invoke()
    fun withRemoteUrl(url: String): ChatAttachment = ChatAttachment(kind, mimeType, fileName, byteCount, remoteUrl = url)
}

/**
 * The material markers are part of the model-visible request, never persisted as message text.
 * Keep one marker per submitted item so a response can distinguish multiple images from a PDF
 * or video without falling back to the ambiguous word "附件".
 */
internal fun attachmentReferenceInstruction(attachments: List<ChatAttachment>): String {
    val markers = attachments.joinToString("、") { attachment ->
        when (attachment.kind) {
            ChatAttachmentKind.IMAGE -> "<图片>"
            ChatAttachmentKind.PDF -> "<PDF>"
            ChatAttachmentKind.VIDEO -> "<视频>"
            ChatAttachmentKind.AUDIO -> "<音频>"
            ChatAttachmentKind.FILE -> "<文件>"
        }
    }
    return "本条消息按顺序包含：$markers。回答涉及这些材料时，先单独一行写出对应标签；正文使用图片、PDF、视频、音频或文件等具体类型，不要笼统称为附件。"
}

/**
 * Conservative local planning estimate for native multimodal parts.  It is not billing or a
 * provider tokenizer: the real server usage remains authoritative.  The cost is intentionally
 * based on the original bytes so the full file is still sent when the selected model can accept
 * it; no preview image or PDF first page substitutes for the source file.
 */
fun ChatAttachment.estimatedInputTokens(estimate: AttachmentInputTokenEstimate): Int {
    val kib = ((byteCount + 1023L) / 1024L).coerceAtLeast(1L)
    val cost = when (kind) {
        ChatAttachmentKind.IMAGE -> maxOf(estimate.imageMinimumTokens.toLong(), kib * estimate.imageTokensPerKiB)
        ChatAttachmentKind.PDF -> maxOf(estimate.pdfMinimumTokens.toLong(), kib * estimate.pdfTokensPerKiB)
        ChatAttachmentKind.VIDEO -> maxOf(estimate.videoMinimumTokens.toLong(), kib * estimate.videoTokensPerKiB)
        // The current profile catalog has no separate audio/file accounting field.  Use its
        // conservative video/PDF coefficients only for local context planning; server usage is
        // still the billing source of truth.
        ChatAttachmentKind.AUDIO -> maxOf(estimate.videoMinimumTokens.toLong(), kib * estimate.videoTokensPerKiB)
        ChatAttachmentKind.FILE -> maxOf(estimate.pdfMinimumTokens.toLong(), kib * estimate.pdfTokensPerKiB)
    }
    return cost.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** Shared OpenAI-compatible envelope; its codec is not named after OpenRouter. */
abstract class OpenAiCompatibleChatAdapter : ChatProviderAdapter {
    override fun decodeNonStreaming(body: String): ChatAdapterDecodedResult {
        val decoded = OpenAiCompatibleJsonCodec.decode(body) ?: return ChatAdapterDecodedResult.EmptyOrMalformed
        return decoded.text.cleanChatReply()?.let {
            ChatAdapterDecodedResult.Text(it, decoded.inputTokens, decoded.outputTokens, decoded.reasoning, decoded.toolCalls, decoded.toolCallEncountered, decoded.webSources, decoded.reportedCostUsdMicros, decoded.cachedInputTokens)
        } ?: decoded.toolCalls.takeIf(List<ChatToolCall>::isNotEmpty)?.let {
            ChatAdapterDecodedResult.ToolCalls(decoded.reasoning, it, decoded.inputTokens, decoded.outputTokens)
        } ?: ChatAdapterDecodedResult.EmptyOrMalformed
    }
    override fun classifyHttpFailure(status: Int, responseBody: String) = classifyProviderFailure(status, ErrorBodyRedactor.redact(responseBody))
    protected fun textOnlyBody(model: ResolvedModel, messages: List<Pair<String, String>>, stream: Boolean, options: ChatRequestOptions) = buildString {
        append("{\"model\":\""); append(model.modelId.escapeJson()); append("\",\"messages\":[")
        messages.forEachIndexed { index, (role, text) ->
            if (index > 0) append(',')
            append("{\"role\":\""); append(role.escapeJson()); append("\",\"content\":\""); append(text.escapeJson()); append("\"}")
        }
        append("]")
        model.maxOutputTokens?.let { append(",\"max_tokens\":").append(it) }
        appendRequestOptions(options, stream)
    }
}

open class OpenRouterChatAdapter : OpenAiCompatibleChatAdapter() {
    override open val providerId = ProviderId.OPENROUTER

    override fun prepare(model: ResolvedModel, messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, stream: Boolean, options: ChatRequestOptions): ChatAdapterPrepareResult {
        // OpenRouter's `file` content part is for provider-supported file modalities such as PDF.
        // Markdown/TXT/JSON/CSV must remain model-visible text; sending them as generic `file`
        // parts can make a mixed image + document message reach the model without either item.
        val inlineTextFiles = inlineUtf8TextFiles(attachments) ?: return ChatAdapterPrepareResult.AttachmentUnsupported
        return when {
            attachments.isEmpty() -> ChatAdapterPrepareResult.Ready(textOnlyBody(model, messages, stream, options))
            attachments.all { it.kind == ChatAttachmentKind.FILE } ->
                ChatAdapterPrepareResult.Ready(textOnlyBody(model, messages + ("user" to inlineTextFiles), stream, options))
            else -> ChatAdapterPrepareResult.Ready(
                "<streamed-openrouter-chat-body>",
                openRouterMultimodalBody(model, messages, attachments, inlineTextFiles, stream, options),
            )
        }
    }
    // The non-streaming completion contains OpenRouter's structured url_citation annotations.
    // Prefer that authoritative final envelope over a stream that only exposes generated prose.
    override fun supportsStreaming(model: ResolvedModel, options: ChatRequestOptions): Boolean =
        !options.liveWebSearch && super.supportsStreaming(model, options)
    override fun decodeStreamingEvent(body: String) = OpenAiCompatibleJsonCodec.decodeStream(body)
}

/**
 * DashScope OpenAI-compatible Chat Completions has its own contract even where a content part
 * happens to share a name with OpenRouter.  Keeping it independent prevents future Qwen changes
 * from leaking into OpenRouter serialization.
 */
class QwenChatAdapter : OpenAiCompatibleChatAdapter() {
    override val providerId = ProviderId.QWEN

    override fun endpointPath(options: ChatRequestOptions): String =
        if (options.webSearchRoute == OfficialWebSearchRoute.QWEN_RESPONSES) "/responses" else super.endpointPath(options)

    override fun supportsStreaming(model: ResolvedModel, options: ChatRequestOptions): Boolean =
        if (options.webSearchRoute == OfficialWebSearchRoute.QWEN_RESPONSES) false else super.supportsStreaming(model, options)

    override fun prepare(model: ResolvedModel, messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, stream: Boolean, options: ChatRequestOptions): ChatAdapterPrepareResult {
        if (options.webSearchRoute == OfficialWebSearchRoute.QWEN_RESPONSES) {
            return if (attachments.isEmpty()) ChatAdapterPrepareResult.Ready(qwenResponsesWebSearchBody(model, messages))
            else ChatAdapterPrepareResult.AttachmentUnsupported
        }
        if (attachments.any { it.remoteUrl != null }) return ChatAdapterPrepareResult.AttachmentUnsupported
        // DashScope Chat Completions accepts Qwen3.7+ text files as ordinary text, not as the
        // OpenRouter-specific `file` content part. Keep the complete UTF-8 payload in a separate
        // user message, so a Markdown/JSON/CSV attachment never turns the request into a
        // schema-invalid content array.
        val inlineTextFiles = inlineUtf8TextFiles(attachments) ?: return ChatAdapterPrepareResult.AttachmentUnsupported
        return if (attachments.isEmpty()) ChatAdapterPrepareResult.Ready(textOnlyBody(model, messages, stream, options))
        else if (attachments.all { it.kind == ChatAttachmentKind.FILE }) {
            ChatAdapterPrepareResult.Ready(textOnlyBody(model, messages + ("user" to inlineTextFiles), stream, options))
        } else ChatAdapterPrepareResult.Ready("<streamed-qwen-chat-body>", qwenMultimodalBody(model, messages, attachments, inlineTextFiles, stream, options))
    }
    /** Qwen PDF understanding can take up to five minutes before its first token. */
    override fun readTimeoutMillis(model: ResolvedModel, attachments: List<ChatAttachment>, stream: Boolean): Int =
        if (attachments.any { it.kind == ChatAttachmentKind.PDF }) 300_000 else super.readTimeoutMillis(model, attachments, stream)
    override fun decodeStreamingEvent(body: String) = OpenAiCompatibleJsonCodec.decodeStream(body)
    override fun decodeNonStreaming(body: String): ChatAdapterDecodedResult =
        ResponsesWebSearchJsonCodec.decode(body) ?: super.decodeNonStreaming(body)
}
class DeepSeekChatAdapter : OpenAiCompatibleChatAdapter() {
    override val providerId = ProviderId.DEEPSEEK

    override fun endpointPath(options: ChatRequestOptions): String =
        if (options.webSearchRoute == OfficialWebSearchRoute.DEEPSEEK_RESPONSES) "/responses" else super.endpointPath(options)

    // DeepSeek's Responses stream uses semantic SSE event names rather than the OpenAI Chat
    // Completions chunks consumed by this transport. Keep this call non-streaming until that
    // separate protocol is implemented, while retaining server-side search and its final result.
    override fun supportsStreaming(model: ResolvedModel, options: ChatRequestOptions): Boolean =
        if (options.webSearchRoute == OfficialWebSearchRoute.DEEPSEEK_RESPONSES) false else super.supportsStreaming(model, options)

    override fun prepare(model: ResolvedModel, messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, stream: Boolean, options: ChatRequestOptions) = when {
        attachments.isNotEmpty() -> ChatAdapterPrepareResult.AttachmentUnsupported
        options.webSearchRoute == OfficialWebSearchRoute.DEEPSEEK_RESPONSES ->
            ChatAdapterPrepareResult.Ready(deepSeekResponsesWebSearchBody(model, messages))
        else -> ChatAdapterPrepareResult.Ready(textOnlyBody(model, messages, stream, options))
    }
    override fun decodeStreamingEvent(body: String) = OpenAiCompatibleJsonCodec.decodeStream(body)
    override fun decodeNonStreaming(body: String): ChatAdapterDecodedResult =
        ResponsesWebSearchJsonCodec.decode(body) ?: super.decodeNonStreaming(body)
}

class ChatProviderAdapters(adapters: Set<ChatProviderAdapter> = setOf(OpenRouterChatAdapter(), QwenChatAdapter(), DeepSeekChatAdapter())) {
    private val byProvider = adapters.associateBy { it.providerId }
    fun adapter(providerId: ProviderId) = byProvider[providerId]
}

/**
 * Keep the model's complete accepted reply.  A presentation-size truncation here would turn a
 * successful long response into a silent data-loss bug; transport/body limits are enforced
 * separately before the provider JSON reaches this projection.
 */
private fun String.cleanChatReply() = replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "").trim().takeIf { it.isNotBlank() }
private fun String.escapeJson() = buildString { for (char in this@escapeJson) when (char) { '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char) } }
private fun StringBuilder.appendRequestOptions(options: ChatRequestOptions, stream: Boolean) {
    when (options.webSearchRoute) {
        OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL -> append(",\"tools\":[{\"type\":\"openrouter:web_search\",\"parameters\":{\"max_results\":5,\"max_total_results\":10}}]")
        OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS -> append(",\"enable_search\":true,\"search_options\":{\"forced_search\":true}")
        OfficialWebSearchRoute.NONE, OfficialWebSearchRoute.QWEN_RESPONSES, OfficialWebSearchRoute.DEEPSEEK_RESPONSES -> Unit
    }
    append(",\"stream\":").append(stream)
    append('}')
}

private fun requestOptionsSuffix(options: ChatRequestOptions, stream: Boolean): String = buildString {
    when (options.webSearchRoute) {
        OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL -> append(",\"tools\":[{\"type\":\"openrouter:web_search\",\"parameters\":{\"max_results\":5,\"max_total_results\":10}}]")
        OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS -> append(",\"enable_search\":true,\"search_options\":{\"forced_search\":true}")
        OfficialWebSearchRoute.NONE, OfficialWebSearchRoute.QWEN_RESPONSES, OfficialWebSearchRoute.DEEPSEEK_RESPONSES -> Unit
    }
    append(",\"stream\":").append(stream)
    append('}')
}

/** OpenRouter serialization stays separate even though current content field names overlap Qwen. */
private fun openRouterMultimodalBody(model: ResolvedModel, messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, inlineTextFiles: String, stream: Boolean, options: ChatRequestOptions): ProviderChatRequestBody {
    val parts = mutableListOf<ProviderChatRequestBody.Part>()
    fun text(value: String) { parts += ProviderChatRequestBody.Part.Utf8(value) }
    text("{\"model\":\"${model.modelId.escapeJson()}\",\"messages\":[")
    messages.forEachIndexed { index, (role, content) ->
        if (index > 0) text(",")
        text("{\"role\":\"${role.escapeJson()}\",\"content\":\"${content.escapeJson()}\"}")
    }
    if (messages.isNotEmpty()) text(",")
    text("{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"")
    text((attachmentReferenceInstruction(attachments) + inlineTextFiles).escapeJson())
    text("\"}")
    attachments.filterNot { it.kind == ChatAttachmentKind.FILE }.forEach { attachment ->
        when (attachment.kind) {
            ChatAttachmentKind.IMAGE -> text(",{\"type\":\"image_url\",\"image_url\":{\"url\":\"")
            ChatAttachmentKind.PDF -> text(",{\"type\":\"file\",\"file\":{\"filename\":\"${attachment.fileName.escapeJson()}\",\"file_data\":\"")
            ChatAttachmentKind.VIDEO -> text(",{\"type\":\"video_url\",\"video_url\":{\"url\":\"")
            ChatAttachmentKind.AUDIO -> text(",{\"type\":\"input_audio\",\"input_audio\":{\"data\":\"")
            ChatAttachmentKind.FILE -> error("Text files are serialized into the text message before this loop.")
        }
        if (attachment.kind == ChatAttachmentKind.AUDIO) {
            parts += ProviderChatRequestBody.Part.Base64File(attachment.byteCount, attachment::open)
            text("\",\"format\":\"${attachment.audioFormat()}\"}}")
        } else {
            attachment.remoteUrl?.let { url ->
                text(url.escapeJson())
            } ?: run {
                text("data:${attachment.mimeType};base64,")
                parts += ProviderChatRequestBody.Part.Base64File(attachment.byteCount, attachment::open)
            }
            text("\"}}")
        }
    }
    text("]}]")
    model.maxOutputTokens?.let { text(",\"max_tokens\":$it") }
    text(requestOptionsSuffix(options, stream))
    return ProviderChatRequestBody.Segmented(parts)
}

/** Qwen-specific request segments keep binary content in a verified stream, never a giant String. */
private fun qwenMultimodalBody(model: ResolvedModel, messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, inlineTextFiles: String, stream: Boolean, options: ChatRequestOptions): ProviderChatRequestBody {
    val parts = mutableListOf<ProviderChatRequestBody.Part>()
    fun text(value: String) { parts += ProviderChatRequestBody.Part.Utf8(value) }
    text("{\"model\":\"${model.modelId.escapeJson()}\",\"messages\":[")
    messages.forEachIndexed { index, (role, content) ->
        if (index > 0) text(",")
        text("{\"role\":\"${role.escapeJson()}\",\"content\":\"${content.escapeJson()}\"}")
    }
    if (messages.isNotEmpty()) text(",")
    text("{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"")
    text((attachmentReferenceInstruction(attachments) + inlineTextFiles).escapeJson())
    text("\"}")
    attachments.filterNot { it.kind == ChatAttachmentKind.FILE }.forEach { attachment ->
        when (attachment.kind) {
            ChatAttachmentKind.IMAGE -> text(",{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:${attachment.mimeType};base64,")
            ChatAttachmentKind.PDF -> text(",{\"type\":\"file\",\"file\":{\"filename\":\"${attachment.fileName.escapeJson()}\",\"file_data\":\"data:${attachment.mimeType};base64,")
            ChatAttachmentKind.VIDEO -> text(",{\"type\":\"video_url\",\"video_url\":{\"url\":\"data:${attachment.mimeType};base64,")
            ChatAttachmentKind.AUDIO -> text(",{\"type\":\"input_audio\",\"input_audio\":{\"data\":\"")
            ChatAttachmentKind.FILE -> error("Qwen text files are serialized into the text message before this loop.")
        }
        parts += ProviderChatRequestBody.Part.Base64File(attachment.byteCount, attachment::open)
        if (attachment.kind == ChatAttachmentKind.AUDIO) text("\",\"format\":\"${attachment.audioFormat()}\"}}")
        else text("\"}}")
    }
    text("]}]")
    model.maxOutputTokens?.let { text(",\"max_tokens\":$it") }
    text(requestOptionsSuffix(options, stream))
    return ProviderChatRequestBody.Segmented(parts)
}

/** Supported text files are serialized as complete UTF-8 message text, never generic file parts. */
private fun inlineUtf8TextFiles(attachments: List<ChatAttachment>): String? {
    val textFiles = attachments.filter { it.kind == ChatAttachmentKind.FILE }
    if (textFiles.isEmpty()) return ""
    if (textFiles.any { it.mimeType !in INLINE_TEXT_FILE_MIME_TYPES }) return null
    return runCatching {
        buildString {
            textFiles.forEach { attachment ->
                append("\n\n以下是文件 ")
                append(attachment.fileName)
                append(" 的完整 UTF-8 文本：\n---\n")
                append(attachment.open().bufferedReader(Charsets.UTF_8).use { it.readText() })
                append("\n---")
            }
        }
    }.getOrNull()
}

private val INLINE_TEXT_FILE_MIME_TYPES = setOf(
    "text/plain", "text/markdown", "application/json", "text/csv",
)

private fun ChatAttachment.audioFormat(): String = when (mimeType) {
    "audio/mpeg" -> "mp3"
    "audio/wav" -> "wav"
    "audio/mp4" -> "m4a"
    else -> "mp3"
}

/** Qwen Responses uses its own built-in web_search tool and returns sources in output items. */
private fun qwenResponsesWebSearchBody(model: ResolvedModel, messages: List<Pair<String, String>>): String = buildString {
    append("{\"model\":\"").append(model.modelId.escapeJson()).append("\",\"input\":[")
    messages.forEachIndexed { index, (role, content) ->
        if (index > 0) append(',')
        append("{\"role\":\"").append(role.escapeJson()).append("\",\"content\":[{\"type\":\"input_text\",\"text\":\"")
        append(content.escapeJson()).append("\"}]}")
    }
    // Qwen3.8-Max thinks by default; DashScope rejects tool_choice=required in that mode.
    // Declaring the built-in tool preserves real web-search access while the provider chooses it.
    append("],\"tools\":[{\"type\":\"web_search\"}],\"stream\":false")
    model.maxOutputTokens?.let { append(",\"max_output_tokens\":").append(it) }
    append('}')
}

/** DeepSeek V4 Pro's native Responses web_search is server-executed and must be forced for an
 * explicit real-time request. This is intentionally a DeepSeek /responses request, never a
 * relay of DeepSeek's model ID through another provider. */
private fun deepSeekResponsesWebSearchBody(model: ResolvedModel, messages: List<Pair<String, String>>): String = buildString {
    append("{\"model\":\"").append(model.modelId.escapeJson()).append("\",\"input\":[")
    messages.forEachIndexed { index, (role, content) ->
        if (index > 0) append(',')
        append("{\"role\":\"").append(role.escapeJson()).append("\",\"content\":[{\"type\":\"input_text\",\"text\":\"")
        append(content.escapeJson()).append("\"}]}")
    }
    append("],\"tools\":[{\"type\":\"web_search\"}],\"tool_choice\":{\"type\":\"web_search\"},\"stream\":false")
    model.maxOutputTokens?.let { append(",\"max_output_tokens\":").append(it) }
    append('}')
}

/** Minimal provider-specific Responses projection; never reuses an OpenAI Chat Completions codec. */
private object ResponsesWebSearchJsonCodec {
    fun decode(raw: String): ChatAdapterDecodedResult? = runCatching {
        val root = StrictJson.parse(raw).objectValue() ?: return null
        val text = root.stringValue("output_text")?.takeIf(String::isNotBlank)
            ?: root.arrayValue("output")
                ?.asSequence()
                ?.mapNotNull { it.objectValue() }
                ?.flatMap { item -> item.arrayValue("content").orEmpty().asSequence() }
                ?.mapNotNull { it.objectValue()?.stringValue("text") }
                ?.joinToString("")
                ?.takeIf(String::isNotBlank)
            // A normal Qwen/DeepSeek Chat Completions reply has `choices`, not `output_text`.
            // This is an unsupported Responses shape, not a malformed ordinary chat response;
            // return null so the provider adapter can fall back to its normal decoder.
            ?: return null
        val usage = root.objectValue("usage")
        ChatAdapterDecodedResult.Text(
            text.cleanChatReply() ?: return ChatAdapterDecodedResult.EmptyOrMalformed,
            usage?.long("input_tokens", "prompt_tokens"), usage?.long("output_tokens", "completion_tokens"),
            webSources = root.arrayValue("output").orEmpty().asSequence()
                .mapNotNull { it.objectValue() }
                .filter { it.stringValue("type") == "web_search_call" }
                .flatMap { item -> item.objectValue("action")?.arrayValue("sources").orEmpty().asSequence() }
                .mapNotNull { raw -> raw.objectValue()?.let { source ->
                    source.stringValue("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?.let { url -> ProviderWebSource(url, source.stringValue("title") ?: source.stringValue("name")) }
                } }
                .distinctBy(ProviderWebSource::url)
                .toList(),
            cachedInputTokens = usage?.objectValue("input_tokens_details")?.long("cached_tokens")
                ?: usage?.objectValue("prompt_tokens_details")?.long("cached_tokens"),
        )
    }.getOrNull()

    @Suppress("UNCHECKED_CAST") private fun Any?.objectValue(): Map<String, Any?>? = this as? Map<String, Any?>
    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?>? = this[key].objectValue()
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.arrayValue(key: String): List<Any?>? = this[key] as? List<Any?>
    private fun Map<String, Any?>.stringValue(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.long(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
        when (val value = this[key]) {
            is java.math.BigDecimal -> runCatching { value.longValueExact() }.getOrNull()
            is Number -> value.toLong()
            else -> null
        }
    }
}

/** OpenAI-compatible JSON projection; it intentionally has no OpenRouter-specific dependency. */
private object OpenAiCompatibleJsonCodec {
    data class Decoded(val text: String, val reasoning: String?, val toolCalls: List<ChatToolCall>, val toolCallEncountered: Boolean, val inputTokens: Long?, val outputTokens: Long?, val webSources: List<ProviderWebSource>, val reportedCostUsdMicros: Long?, val cachedInputTokens: Long?)

    fun decode(raw: String): Decoded? = runCatching {
        val root = StrictJson.parse(raw).objectValue() ?: return null
        val choice = root.arrayValue("choices")?.firstOrNull().objectValue() ?: return null
        val message = choice.objectValue("message") ?: return null
        val text = message.contentText().orEmpty()
        val reasoning = message.stringValue("reasoning_content")?.takeIf(String::isNotBlank)
            ?: message.stringValue("reasoning")?.takeIf(String::isNotBlank)
        val rawTools = message.arrayValue("tool_calls")
        val tools = rawTools.toolCalls()
        val usage = root.objectValue("usage")
        Decoded(
            text, reasoning, tools, rawTools?.isNotEmpty() == true,
            usage?.long("prompt_tokens", "input_tokens"), usage?.long("completion_tokens", "output_tokens"),
            message.arrayValue("annotations").orEmpty().asSequence()
                .mapNotNull { it.objectValue()?.objectValue("url_citation") }
                .mapNotNull { citation -> citation.stringValue("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    ?.let { url -> ProviderWebSource(url, citation.stringValue("title")) } }
                .distinctBy(ProviderWebSource::url)
                .toList(),
            usage?.decimalMicros("cost"),
            usage?.objectValue("input_tokens_details")?.long("cached_tokens")
                ?: usage?.objectValue("prompt_tokens_details")?.long("cached_tokens"),
        )
    }.getOrNull()

    fun decodeStream(raw: String): ProviderSseEvent? = runCatching {
        val root = StrictJson.parse(raw).objectValue() ?: return null
        val choice = root.arrayValue("choices")?.firstOrNull().objectValue()
        val delta = choice?.objectValue("delta")
        val usage = root.objectValue("usage")
        val text = delta?.contentText()
        val reasoning = delta?.stringValue("reasoning_content")?.takeIf(String::isNotBlank)
            ?: delta?.stringValue("reasoning")?.takeIf(String::isNotBlank)
        val rawTools = delta?.arrayValue("tool_calls")
        val tools = rawTools.toolCalls().orEmpty()
        val input = usage?.long("prompt_tokens", "input_tokens")
        val output = usage?.long("completion_tokens", "output_tokens")
        val cost = usage?.decimalMicros("cost")
        val cachedInput = usage?.objectValue("input_tokens_details")?.long("cached_tokens")
            ?: usage?.objectValue("prompt_tokens_details")?.long("cached_tokens")
        if (text == null && reasoning == null && tools.isEmpty() && input == null && output == null && cost == null) null
        else ProviderSseEvent(text, reasoning, input, output, reportedCostUsdMicros = cost, toolCallEncountered = rawTools?.isNotEmpty() == true, cachedInputTokens = cachedInput)
    }.getOrNull()

    private fun Map<String, Any?>.contentText(): String? = when (val content = this["content"]) {
        is String -> content
        is List<*> -> buildString {
            content.forEach { part ->
                val objectPart = part.objectValue() ?: return@forEach
                if (objectPart.stringValue("type") in setOf("text", "output_text")) append(objectPart.stringValue("text").orEmpty())
            }
        }.takeIf(String::isNotBlank)
        else -> null
    }

    private fun List<Any?>?.toolCalls(): List<ChatToolCall> = buildList {
        this@toolCalls ?: return@buildList
        this@toolCalls.forEach { rawCall ->
            val call = rawCall.objectValue() ?: return@forEach
            val function = call.objectValue("function") ?: return@forEach
            val name = function.stringValue("name")?.takeIf(String::isNotBlank) ?: return@forEach
            add(ChatToolCall(call.stringValue("id")?.takeIf(String::isNotBlank), name, function.stringValue("arguments") ?: "{}"))
        }
    }

    private fun Map<String, Any?>.long(vararg keys: String): Long? = keys.asSequence().mapNotNull { key ->
        when (val value = this[key]) {
            is java.math.BigDecimal -> runCatching { value.longValueExact() }.getOrNull()
            is Number -> value.toLong()
            else -> null
        }
    }.firstOrNull()

    private fun Map<String, Any?>.decimalMicros(key: String): Long? = when (val value = this[key]) {
        is BigDecimal -> runCatching {
            value.movePointRight(6).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull()
        is Number -> runCatching {
            BigDecimal(value.toString()).movePointRight(6).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull()
        else -> null
    }

    @Suppress("UNCHECKED_CAST") private fun Any?.objectValue(): Map<String, Any?>? = this as? Map<String, Any?>
    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?>? = this[key].objectValue()
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.arrayValue(key: String): List<Any?>? = this[key] as? List<Any?>
    private fun Map<String, Any?>.stringValue(key: String): String? = this[key] as? String
}
