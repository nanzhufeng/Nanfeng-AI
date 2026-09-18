package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AttachmentInputTokenEstimate
import com.nanzhufeng.ai.domain.ErrorBodyRedactor
import com.nanzhufeng.ai.domain.ResolvedModel
import com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ComposerModelChoice
import com.nanzhufeng.ai.domain.ComposerModelSlot
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.classifyProviderFailure
import com.nanzhufeng.ai.domain.OFFICE_OPEN_XML_MIME_TYPES
import com.nanzhufeng.ai.domain.extractOfficeOpenXmlText
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI

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
    fun prepareContinuation(
        model: ResolvedModel,
        messages: List<ChatHistoryMessage>,
        attachments: List<ChatAttachment>,
        stream: Boolean,
        options: ChatRequestOptions = ChatRequestOptions.Standard,
    ): ChatAdapterPrepareResult = prepare(model, messages.map { it.role to it.content }, attachments, stream, options)
    /** Transport deadlines are provider protocol metadata, never executor-specific model guesses. */
    fun readTimeoutMillis(model: ResolvedModel, attachments: List<ChatAttachment>, stream: Boolean): Int = 90_000
    fun readTimeoutMillis(model: ResolvedModel, attachments: List<ChatAttachment>, stream: Boolean, options: ChatRequestOptions): Int =
        readTimeoutMillis(model, attachments, stream)
    /** Active streaming still has a total lifetime; repeated output must not bypass the deadline. */
    fun maxStreamDurationMillis(model: ResolvedModel, attachments: List<ChatAttachment>, options: ChatRequestOptions): Int = 300_000
    /** Provider protocol traces are buffered only for routes that have exhibited exact leakage. */
    fun streamTextMode(options: ChatRequestOptions): ProviderStreamTextMode = ProviderStreamTextMode.IMMEDIATE
    fun decodeNonStreaming(body: String): ChatAdapterDecodedResult
    /** The Adapter, rather than the transport, owns its SSE JSON field names. */
    fun decodeStreamingEvent(body: String): ProviderSseEvent?
    /** Request options select a provider protocol without making the transport inspect JSON. */
    fun decodeStreamingEvent(body: String, options: ChatRequestOptions): ProviderSseEvent? = decodeStreamingEvent(body)
    fun classifyHttpFailure(status: Int, responseBody: String): ProviderDiagnosticErrorClass
}

/**
 * A charge explicitly returned by the provider for this one response.  It is deliberately
 * distinct from catalogue pricing: callers may persist and sync this fact, but must never
 * synthesize it from token counts.
 */
data class ProviderReportedCost(
    val totalMicros: Long,
    val currencyCode: String,
    val priceVersion: String,
) {
    init {
        require(totalMicros >= 0) { "服务端结算金额不能为负数。" }
        require(currencyCode.matches(Regex("[A-Z]{3}"))) { "服务端结算币种必须是 ISO 大写三字码。" }
        require(priceVersion.isNotBlank()) { "服务端结算必须保留来源版本。" }
    }
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
    ZHIPU_CHAT_COMPLETIONS,
}

data class ChatRequestOptions(
    val webSearchRoute: OfficialWebSearchRoute = OfficialWebSearchRoute.NONE,
    /** OpenRouter's supported cross-provider reasoning control, selected by product policy. */
    val reasoningEffort: ReasoningEffort? = null,
) {
    val liveWebSearch: Boolean get() = webSearchRoute != OfficialWebSearchRoute.NONE
    companion object { val Standard = ChatRequestOptions() }
}

enum class ReasoningEffort(val wireValue: String) {
    HIGH("high"),
}

/**
 * Deep selections that represent Claude or ChatGPT reasoning products share one explicit
 * OpenRouter request setting.  This policy is based on product presets rather than an arbitrary
 * provider model-ID substring, so both direct deep selection and Auto's chosen route are covered.
 */
internal object OpenRouterDeepReasoningPolicy {
    private val highPresets = setOf(
        ModelPresetId.CLAUDE_FABLE_5_1,
        ModelPresetId.CLAUDE_OPUS_5,
        ModelPresetId.GPT_6_ASTRA,
        ModelPresetId.GPT_5_6_SOL,
    )

    fun forChoice(choice: ComposerModelChoice): ChatRequestOptions =
        if (choice.routes.any { it in highPresets }) ChatRequestOptions(reasoningEffort = ReasoningEffort.HIGH)
        else ChatRequestOptions.Standard

    fun forPreset(preset: ModelPresetId, current: ChatRequestOptions): ChatRequestOptions =
        if (preset in highPresets) current.copy(reasoningEffort = ReasoningEffort.HIGH) else current
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
        /** A provider-settled amount.  This is not a local token-price estimate. */
        val reportedProviderCost: ProviderReportedCost? = null,
        /** Provider-reported input cache hits; used only by a local estimate when no cost arrives. */
        val cachedInputTokens: Long? = null,
        /** Provider-reported subset of output tokens spent on hidden reasoning. */
        val reasoningTokens: Long? = null,
        /** Provider-owned search execution evidence, independent of the request switch. */
        val webSearchPerformed: Boolean = false,
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
data class ChatToolCallDelta(val index: Int, val id: String?, val name: String?, val argumentsDelta: String)

/** Parsed message protocol; provider-specific adapters may preserve fields beyond visible text. */
data class ChatHistoryMessage(
    val role: String,
    val content: String,
    val reasoningContent: String? = null,
    val toolCalls: List<ChatToolCall> = emptyList(),
)

/** A public source returned by a provider's own web-search result, not model-generated metadata. */
data class ProviderWebSource(val url: String, val title: String? = null) {
    init { require(isValidPublicHttpUrl(url)) { "Provider web source must be an absolute HTTP(S) URL with a host." } }

    companion object {
        fun fromProvider(url: String?, title: String?): ProviderWebSource? {
            val normalized = url?.trim()?.takeIf(::isValidPublicHttpUrl) ?: return null
            return ProviderWebSource(normalized, title?.trim()?.takeIf(String::isNotBlank))
        }

        fun isValidPublicHttpUrl(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.isAbsolute &&
                uri.scheme.lowercase() in setOf("http", "https") &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        }.getOrDefault(false)
    }
}

/**
 * Preserve provider-returned sources in the user-visible result. The presentation owner turns
 * these Markdown links into the existing source chips, so raw provider JSON never reaches UI.
 */
fun appendProviderWebSources(text: String, sources: List<ProviderWebSource>): String {
    val distinct = sources.asSequence()
        .filter { ProviderWebSource.isValidPublicHttpUrl(it.url) }
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
            ChatAdapterDecodedResult.Text(
                text = it,
                inputTokens = decoded.inputTokens,
                outputTokens = decoded.outputTokens,
                reasoning = decoded.reasoning,
                toolCalls = decoded.toolCalls,
                toolCallEncountered = decoded.toolCallEncountered,
                webSources = decoded.webSources,
                reportedProviderCost = decoded.providerReportedCost(providerId),
                cachedInputTokens = decoded.cachedInputTokens,
                reasoningTokens = decoded.reasoningTokens,
            )
        } ?: decoded.toolCalls.takeIf(List<ChatToolCall>::isNotEmpty)?.let {
            ChatAdapterDecodedResult.ToolCalls(decoded.reasoning, it, decoded.inputTokens, decoded.outputTokens)
        } ?: ChatAdapterDecodedResult.EmptyOrMalformed
    }
    /** One provider-neutral parser feeds every direct OpenAI-compatible service. */
    protected fun decodeOpenAiCompatibleStreamingEvent(body: String): ProviderSseEvent? =
        OpenAiCompatibleJsonCodec.decodeStream(body, providerId)

    override fun classifyHttpFailure(status: Int, responseBody: String) = classifyProviderFailure(status, ErrorBodyRedactor.redact(responseBody))
    protected fun textOnlyBody(model: ResolvedModel, messages: List<Pair<String, String>>, stream: Boolean, options: ChatRequestOptions) = buildString {
        append("{\"model\":\""); append(model.modelId.escapeJson()); append("\",\"messages\":[")
        messages.forEachIndexed { index, (role, text) ->
            if (index > 0) append(',')
            append("{\"role\":\""); append(role.escapeJson()); append("\",\"content\":\""); append(text.escapeJson()); append("\"}")
        }
        append("]")
        appendOutputTokenLimit(model)
        appendProviderOwnedRequestOptions(model)
        appendRequestOptions(options, stream)
    }

    protected open fun StringBuilder.appendOutputTokenLimit(model: ResolvedModel) {
        model.maxOutputTokens?.let { append(",\"max_tokens\":").append(it) }
    }

    /** Provider-only request fields stay behind the selected Adapter instead of leaking into
     * the shared OpenAI-compatible envelope used by unrelated services. */
    protected open fun StringBuilder.appendProviderOwnedRequestOptions(model: ResolvedModel) = Unit
}

open class OpenRouterChatAdapter : OpenAiCompatibleChatAdapter() {
    override open val providerId = ProviderId.OPENROUTER

    override fun requestOptions(model: ResolvedModel, choice: ComposerModelChoice): ChatRequestOptions =
        OpenRouterDeepReasoningPolicy.forChoice(choice)

    /** Grok product presets own their reasoning mode and a bounded product output budget. The
     * provider capability ceiling may be much larger, but sending it as the default maximum would
     * create avoidable latency and cost risk for an ordinary conversation. */
    protected override fun StringBuilder.appendOutputTokenLimit(model: ResolvedModel) {
        openRouterRequestOutputLimit(model)?.let { append(",\"max_tokens\":").append(it) }
    }

    protected override fun StringBuilder.appendProviderOwnedRequestOptions(model: ResolvedModel) {
        append(openRouterModelOptionsSuffix(model.modelId))
    }

    override fun prepare(model: ResolvedModel, messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, stream: Boolean, options: ChatRequestOptions): ChatAdapterPrepareResult {
        return prepareContinuation(model, messages.map { ChatHistoryMessage(it.first, it.second) }, attachments, stream, options)
    }

    override fun prepareContinuation(model: ResolvedModel, messages: List<ChatHistoryMessage>, attachments: List<ChatAttachment>, stream: Boolean, options: ChatRequestOptions): ChatAdapterPrepareResult {
        // OpenRouter's `file` content part is for provider-supported file modalities such as PDF.
        // Markdown/TXT/JSON/CSV must remain model-visible text; sending them as generic `file`
        // parts can make a mixed image + document message reach the model without either item.
        val inlineTextFiles = inlineUtf8TextFiles(attachments) ?: return ChatAdapterPrepareResult.AttachmentUnsupported
        return when {
            attachments.isEmpty() -> ChatAdapterPrepareResult.Ready(
                if (model.modelId == KIMI_K3_MODEL_ID) kimiK3TextBody(model, messages, stream, options)
                else textOnlyBody(model, messages.map { it.role to it.content }, stream, options),
            )
            attachments.all { it.kind == ChatAttachmentKind.FILE } ->
                ChatAdapterPrepareResult.Ready(
                    if (model.modelId == KIMI_K3_MODEL_ID) kimiK3TextBody(model, messages + ChatHistoryMessage("user", inlineTextFiles), stream, options)
                    else textOnlyBody(model, messages.map { it.role to it.content } + ("user" to inlineTextFiles), stream, options),
                )
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
    override fun decodeStreamingEvent(body: String) = decodeOpenAiCompatibleStreamingEvent(body)
}

internal const val KIMI_K3_MODEL_ID = "moonshotai/kimi-k3"
internal const val GROK_4_1_FAST_MODEL_ID = "x-ai/grok-4.1-fast"
internal const val GROK_4_6_MODEL_ID = "x-ai/grok-4.6"
private const val GROK_PRODUCT_OUTPUT_TOKENS = 65_536L

private fun openRouterRequestOutputLimit(model: ResolvedModel): Long? = when (model.modelId) {
    GROK_4_1_FAST_MODEL_ID, GROK_4_6_MODEL_ID ->
        (model.maxOutputTokens ?: GROK_PRODUCT_OUTPUT_TOKENS).coerceAtMost(GROK_PRODUCT_OUTPUT_TOKENS)
    else -> model.maxOutputTokens
}

private fun openRouterModelOptionsSuffix(modelId: String): String = when (modelId) {
    // The daily preset stays fast and economical; deep synthesis is represented by 4.6 High.
    GROK_4_1_FAST_MODEL_ID -> ",\"reasoning\":{\"enabled\":false}"
    GROK_4_6_MODEL_ID -> ",\"reasoning\":{\"effort\":\"high\"}"
    else -> ""
}

/** K3 continuity requires its assistant reasoning/tool protocol, not a lossy content-only copy. */
private fun kimiK3TextBody(model: ResolvedModel, messages: List<ChatHistoryMessage>, stream: Boolean, options: ChatRequestOptions) = buildString {
    append("{\"model\":\""); append(model.modelId.escapeJson()); append("\",\"messages\":[")
    messages.forEachIndexed { index, message ->
        if (index > 0) append(',')
        append(kimiK3MessageJson(message))
    }
    append(']')
    model.maxOutputTokens?.let { append(",\"max_tokens\":").append(it) }
    appendRequestOptions(options, stream)
}

private fun kimiK3MessageJson(message: ChatHistoryMessage): String = buildString {
    append("{\"role\":\""); append(message.role.escapeJson()); append("\",\"content\":\""); append(message.content.escapeJson()); append('"')
    message.reasoningContent?.takeIf(String::isNotBlank)?.let {
        append(",\"reasoning_content\":\""); append(it.escapeJson()); append('"')
    }
    if (message.toolCalls.isNotEmpty()) {
        append(",\"tool_calls\":[")
        message.toolCalls.forEachIndexed { index, call ->
            if (index > 0) append(',')
            append("{\"type\":\"function\"")
            call.id?.let { append(",\"id\":\"").append(it.escapeJson()).append('"') }
            append(",\"function\":{\"name\":\"").append(call.name.escapeJson())
            append("\",\"arguments\":\"").append(call.argumentsJson.escapeJson()).append("\"}}")
        }
        append(']')
    }
    append('}')
}

/**
 * DashScope OpenAI-compatible Chat Completions has its own contract even where a content part
 * happens to share a name with OpenRouter.  Keeping it independent prevents future Qwen changes
 * from leaking into OpenRouter serialization.
 */
class QwenChatAdapter : OpenAiCompatibleChatAdapter() {
    override val providerId = ProviderId.QWEN

    protected override fun StringBuilder.appendOutputTokenLimit(model: ResolvedModel) {
        if (model.modelId == QWEN_3_8_MAX_MODEL_ID) {
            append(",\"max_completion_tokens\":").append(qwen38RequestOutputLimit(model))
        } else {
            model.maxOutputTokens?.let { append(",\"max_tokens\":").append(it) }
        }
    }

    protected override fun StringBuilder.appendProviderOwnedRequestOptions(model: ResolvedModel) {
        if (model.modelId == QWEN_3_8_MAX_MODEL_ID) {
            // Qwen3.8-Max otherwise defaults to xhigh (131,072 reasoning tokens). Low preserves
            // deliberate reasoning with a provider-defined 4,096-token budget. Historical
            // reasoning is never returned as an input message, so make that boundary explicit.
            append(",\"reasoning_effort\":\"low\",\"preserve_thinking\":false")
        }
    }

    override fun endpointPath(options: ChatRequestOptions): String =
        if (options.webSearchRoute == OfficialWebSearchRoute.QWEN_RESPONSES) "/responses" else super.endpointPath(options)

    override fun supportsStreaming(model: ResolvedModel, options: ChatRequestOptions): Boolean =
        super.supportsStreaming(model, options)

    override fun prepare(model: ResolvedModel, messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, stream: Boolean, options: ChatRequestOptions): ChatAdapterPrepareResult {
        if (options.webSearchRoute == OfficialWebSearchRoute.QWEN_RESPONSES) {
            return if (attachments.isEmpty()) ChatAdapterPrepareResult.Ready(qwenResponsesWebSearchBody(model, messages, stream))
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
        } else ChatAdapterPrepareResult.Ready("<streamed-qwen-chat-body>", inlineMultimodalChatBody(model, messages, attachments, inlineTextFiles, stream, options))
    }
    /** Qwen PDF understanding can take up to five minutes before its first token. */
    override fun readTimeoutMillis(model: ResolvedModel, attachments: List<ChatAttachment>, stream: Boolean): Int =
        if (attachments.any { it.kind == ChatAttachmentKind.PDF }) 300_000 else super.readTimeoutMillis(model, attachments, stream)
    override fun readTimeoutMillis(model: ResolvedModel, attachments: List<ChatAttachment>, stream: Boolean, options: ChatRequestOptions): Int =
        if (options.webSearchRoute == OfficialWebSearchRoute.QWEN_RESPONSES) 180_000
        else readTimeoutMillis(model, attachments, stream)
    override fun maxStreamDurationMillis(model: ResolvedModel, attachments: List<ChatAttachment>, options: ChatRequestOptions): Int =
        if (attachments.any { it.kind == ChatAttachmentKind.PDF }) 420_000
        else if (options.webSearchRoute == OfficialWebSearchRoute.QWEN_RESPONSES) 300_000
        else if (options.webSearchRoute == OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS) 180_000
        else super.maxStreamDurationMillis(model, attachments, options)
    override fun streamTextMode(options: ChatRequestOptions): ProviderStreamTextMode =
        when (options.webSearchRoute) {
            OfficialWebSearchRoute.QWEN_RESPONSES -> ProviderStreamTextMode.RESPONSES_API
            OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS -> ProviderStreamTextMode.BUFFER_QWEN_WEB_SEARCH
            else -> super.streamTextMode(options)
        }
    override fun decodeStreamingEvent(body: String) = decodeOpenAiCompatibleStreamingEvent(body)
    override fun decodeStreamingEvent(body: String, options: ChatRequestOptions): ProviderSseEvent? =
        if (options.webSearchRoute == OfficialWebSearchRoute.QWEN_RESPONSES) ResponsesSseJsonCodec.decode(body)
        else decodeStreamingEvent(body)
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

    override fun prepare(model: ResolvedModel, messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, stream: Boolean, options: ChatRequestOptions): ChatAdapterPrepareResult {
        val inlineTextFiles = inlineUtf8TextFiles(attachments) ?: return ChatAdapterPrepareResult.AttachmentUnsupported
        val images = attachments.filter { it.kind == ChatAttachmentKind.IMAGE }
        if (attachments.any { it.kind !in setOf(ChatAttachmentKind.FILE, ChatAttachmentKind.IMAGE) } ||
            (images.isNotEmpty() && (model.modelId != "deepseek-flash" || !model.capabilities.supportsVision)) ||
            images.any { it.mimeType !in setOf("image/jpeg", "image/png", "image/gif", "image/webp") || it.byteCount > 32L * 1024 * 1024 }
        ) return ChatAdapterPrepareResult.AttachmentUnsupported
        // Leave room for the JSON envelope within the official 48 MiB request cap.
        if (images.sumOf { ((it.byteCount + 2) / 3) * 4 } + messages.sumOf { it.second.toByteArray().size.toLong() } + inlineTextFiles.toByteArray().size > 47L * 1024 * 1024)
            return ChatAdapterPrepareResult.AttachmentUnsupported
        if (images.isNotEmpty()) {
            val body = deepSeekImageBody(model, messages, attachments, inlineTextFiles, stream, options)
            if (body.contentLength > 48L * 1024 * 1024) return ChatAdapterPrepareResult.AttachmentUnsupported
            return ChatAdapterPrepareResult.Ready("<streamed-deepseek-chat-body>", body)
        }
        val effectiveMessages = if (inlineTextFiles.isBlank()) messages else messages + ("user" to inlineTextFiles)
        return if (options.webSearchRoute == OfficialWebSearchRoute.DEEPSEEK_RESPONSES) {
            ChatAdapterPrepareResult.Ready(deepSeekResponsesWebSearchBody(model, effectiveMessages))
        } else ChatAdapterPrepareResult.Ready(textOnlyBody(model, effectiveMessages, stream, options))
    }
    override fun decodeStreamingEvent(body: String) = decodeOpenAiCompatibleStreamingEvent(body)
    override fun decodeNonStreaming(body: String): ChatAdapterDecodedResult =
        ResponsesWebSearchJsonCodec.decode(body) ?: super.decodeNonStreaming(body)
}

/**
 * Zhipu BigModel uses the standard OpenAI-compatible Chat Completions envelope.  The requested
 * GLM preset is exposed as text-only until its per-modality API payload is publicly verified;
 * this prevents an attachment from being silently reshaped or claimed as sent.
 */
class ZhipuChatAdapter : OpenAiCompatibleChatAdapter() {
    override val providerId = ProviderId.ZHIPU

    protected override fun StringBuilder.appendProviderOwnedRequestOptions(model: ResolvedModel) {
        if (model.modelId == "glm-5.3" || model.modelId == "glm-5.3-flash") {
            append(",\"thinking\":{\"type\":\"enabled\"},\"reasoning_effort\":\"max\"")
        }
    }

    override fun prepare(model: ResolvedModel, messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, stream: Boolean, options: ChatRequestOptions): ChatAdapterPrepareResult {
        val inlineTextFiles = inlineUtf8TextFiles(attachments) ?: return ChatAdapterPrepareResult.AttachmentUnsupported
        if (attachments.any { it.kind != ChatAttachmentKind.FILE }) return ChatAdapterPrepareResult.AttachmentUnsupported
        val effectiveMessages = if (inlineTextFiles.isBlank()) messages else messages + ("user" to inlineTextFiles)
        return ChatAdapterPrepareResult.Ready(textOnlyBody(model, effectiveMessages, stream, options))
    }

    override fun decodeStreamingEvent(body: String) = decodeOpenAiCompatibleStreamingEvent(body)
}

class ChatProviderAdapters(adapters: Set<ChatProviderAdapter> = setOf(OpenRouterChatAdapter(), QwenChatAdapter(), DeepSeekChatAdapter(), ZhipuChatAdapter())) {
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
        OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS -> append(",\"tools\":[{\"type\":\"web_search\",\"web_search\":{\"enable\":true,\"search_engine\":\"search_std\",\"search_result\":true,\"count\":5,\"content_size\":\"medium\"}}],\"tool_choice\":\"auto\"")
        OfficialWebSearchRoute.NONE, OfficialWebSearchRoute.QWEN_RESPONSES, OfficialWebSearchRoute.DEEPSEEK_RESPONSES -> Unit
    }
    options.reasoningEffort?.let { append(",\"reasoning\":{\"effort\":\"").append(it.wireValue).append("\"}") }
    append(",\"stream\":").append(stream)
    append('}')
}

private fun requestOptionsSuffix(options: ChatRequestOptions, stream: Boolean): String = buildString {
    when (options.webSearchRoute) {
        OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL -> append(",\"tools\":[{\"type\":\"openrouter:web_search\",\"parameters\":{\"max_results\":5,\"max_total_results\":10}}]")
        OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS -> append(",\"enable_search\":true,\"search_options\":{\"forced_search\":true}")
        OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS -> append(",\"tools\":[{\"type\":\"web_search\",\"web_search\":{\"enable\":true,\"search_engine\":\"search_std\",\"search_result\":true,\"count\":5,\"content_size\":\"medium\"}}],\"tool_choice\":\"auto\"")
        OfficialWebSearchRoute.NONE, OfficialWebSearchRoute.QWEN_RESPONSES, OfficialWebSearchRoute.DEEPSEEK_RESPONSES -> Unit
    }
    options.reasoningEffort?.let { append(",\"reasoning\":{\"effort\":\"").append(it.wireValue).append("\"}") }
    append(",\"stream\":").append(stream)
    append('}')
}

/** OpenRouter serialization stays separate even though current content field names overlap Qwen. */
private fun openRouterMultimodalBody(model: ResolvedModel, messages: List<ChatHistoryMessage>, attachments: List<ChatAttachment>, inlineTextFiles: String, stream: Boolean, options: ChatRequestOptions): ProviderChatRequestBody {
    val parts = mutableListOf<ProviderChatRequestBody.Part>()
    fun text(value: String) { parts += ProviderChatRequestBody.Part.Utf8(value) }
    text("{\"model\":\"${model.modelId.escapeJson()}\",\"messages\":[")
    messages.forEachIndexed { index, message ->
        if (index > 0) text(",")
        text(if (model.modelId == KIMI_K3_MODEL_ID) kimiK3MessageJson(message) else "{\"role\":\"${message.role.escapeJson()}\",\"content\":\"${message.content.escapeJson()}\"}")
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
    openRouterRequestOutputLimit(model)?.let { text(",\"max_tokens\":$it") }
    text(openRouterModelOptionsSuffix(model.modelId))
    text(requestOptionsSuffix(options, stream))
    return ProviderChatRequestBody.Segmented(parts)
}

/** Compatible inline payloads keep binary content in a verified stream, never a giant String. */
private fun inlineMultimodalChatBody(model: ResolvedModel, messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, inlineTextFiles: String, stream: Boolean, options: ChatRequestOptions): ProviderChatRequestBody {
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
internal fun inlineUtf8TextFiles(attachments: List<ChatAttachment>): String? {
    val textFiles = attachments.filter { it.kind == ChatAttachmentKind.FILE }
    if (textFiles.isEmpty()) return ""
    return runCatching {
        buildString {
            textFiles.forEach { attachment ->
                val bytes = attachment.open().use { it.readBytes() }
                val text = when {
                    attachment.mimeType in INLINE_TEXT_FILE_MIME_TYPES -> bytes.decodeStrictUtf8()
                    attachment.mimeType in OFFICE_OPEN_XML_MIME_TYPES ->
                        extractOfficeOpenXmlText(bytes, attachment.mimeType)?.text
                    else -> null
                } ?: return null
                append("\n\n以下是文件 ")
                append(attachment.fileName)
                append(if (attachment.mimeType in INLINE_TEXT_FILE_MIME_TYPES) " 的完整 UTF-8 文本：\n---\n" else " 的完整可读文本：\n---\n")
                append(text)
                append("\n---")
            }
        }
    }.getOrNull()
}

private val INLINE_TEXT_FILE_MIME_TYPES = setOf(
    "text/plain", "text/markdown", "application/json", "text/csv",
    "application/xml", "text/xml", "application/x-yaml", "text/yaml", "text/html",
)

private fun ByteArray.decodeStrictUtf8(): String? = runCatching {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(this))
        .toString()
}.getOrNull()

private fun ChatAttachment.audioFormat(): String = when (mimeType) {
    "audio/mpeg" -> "mp3"
    "audio/wav" -> "wav"
    "audio/mp4" -> "m4a"
    else -> "mp3"
}

/** Qwen Responses uses its own built-in web_search tool and returns sources in output items. */
private fun qwenResponsesWebSearchBody(model: ResolvedModel, messages: List<Pair<String, String>>, stream: Boolean): String = buildString {
    append("{\"model\":\"").append(model.modelId.escapeJson()).append("\",\"input\":[")
    messages.forEachIndexed { index, (role, content) ->
        if (index > 0) append(',')
        append("{\"role\":\"").append(role.escapeJson()).append("\",\"content\":[{\"type\":\"input_text\",\"text\":\"")
        append(content.escapeJson()).append("\"}]}")
    }
    // DashScope defaults Qwen3.8-Max Responses to xhigh. Explicit low reasoning retains useful
    // analysis while bounding the provider-defined reasoning budget to 4,096 tokens. The total
    // completion ceiling covers both hidden reasoning and the final answer. Other Qwen presets
    // keep their established contract.
    append(']')
    if (model.modelId == QWEN_3_8_MAX_MODEL_ID) append(",\"reasoning\":{\"effort\":\"low\"}")
    append(",\"tools\":[{\"type\":\"web_search\"}],\"store\":false,\"stream\":").append(stream)
    val outputLimit = if (model.modelId == QWEN_3_8_MAX_MODEL_ID) qwen38RequestOutputLimit(model) else model.maxOutputTokens
    outputLimit?.let { append(",\"max_output_tokens\":").append(it) }
    append('}')
}

internal const val QWEN_3_8_MAX_MODEL_ID = "qwen3.8-max"
private const val QWEN_3_8_MAX_REQUEST_OUTPUT_TOKENS = 16_384L
private fun qwen38RequestOutputLimit(model: ResolvedModel): Long =
    (model.maxOutputTokens ?: QWEN_3_8_MAX_REQUEST_OUTPUT_TOKENS)
        .coerceAtMost(QWEN_3_8_MAX_REQUEST_OUTPUT_TOKENS)

/** DeepSeek V4's native Responses web_search is server-executed and must be forced for an
 * explicit real-time request. This is intentionally a DeepSeek /responses request, never a
 * relay of DeepSeek's model ID through another provider. */
/** The two official image envelopes share streamed original bytes; PDFs/video remain unsupported. */
private fun deepSeekImageBody(model: ResolvedModel, messages: List<Pair<String, String>>, attachments: List<ChatAttachment>, inlineTextFiles: String, stream: Boolean, options: ChatRequestOptions): ProviderChatRequestBody {
    if (options.webSearchRoute != OfficialWebSearchRoute.DEEPSEEK_RESPONSES) {
        return inlineMultimodalChatBody(model, messages, attachments, inlineTextFiles, stream, options)
    }
    val parts = mutableListOf<ProviderChatRequestBody.Part>()
    fun text(value: String) { parts += ProviderChatRequestBody.Part.Utf8(value) }
    text("{\"model\":\"${model.modelId.escapeJson()}\",\"input\":[")
    messages.forEachIndexed { index, (role, content) ->
        if (index > 0) text(",")
        text("{\"role\":\"${role.escapeJson()}\",\"content\":[{\"type\":\"input_text\",\"text\":\"${content.escapeJson()}\"}]}")
    }
    if (messages.isNotEmpty()) text(",")
    text("{\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":\"${(attachmentReferenceInstruction(attachments) + inlineTextFiles).escapeJson()}\"}")
    attachments.filter { it.kind == ChatAttachmentKind.IMAGE }.forEach {
        text(",{\"type\":\"input_image\",\"image_url\":\"data:${it.mimeType};base64,")
        parts += ProviderChatRequestBody.Part.Base64File(it.byteCount, it::open)
        text("\"}")
    }
    text("]}],\"tools\":[{\"type\":\"web_search\"}],\"tool_choice\":{\"type\":\"web_search\"},\"stream\":false")
    model.maxOutputTokens?.let { text(",\"max_output_tokens\":$it") }
    text("}")
    return ProviderChatRequestBody.Segmented(parts)
}

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
        val outputItems = root.arrayValue("output").orEmpty().mapNotNull { it.objectValue() }
        // Responses envelopes contain distinct reasoning/search/message items.  Only the
        // assistant message's output_text is an answer.  Flattening every `content.text` here
        // previously leaked DeepSeek's English planning trace into the visible reply.
        val text = root.stringValue("output_text")?.takeIf(String::isNotBlank)
            ?: outputItems.asSequence()
                .filter { it.stringValue("type") == "message" }
                .flatMap { item -> item.arrayValue("content").orEmpty().asSequence() }
                .mapNotNull { it.objectValue() }
                .filter { it.stringValue("type") in setOf("output_text", "text") }
                .mapNotNull { it.stringValue("text") }
                .joinToString("")
                .takeIf(String::isNotBlank)
            // A normal Qwen/DeepSeek Chat Completions reply has `choices`, not `output_text`.
            // This is an unsupported Responses shape, not a malformed ordinary chat response;
            // return null so the provider adapter can fall back to its normal decoder.
            ?: return null
        val usage = root.objectValue("usage")
        ChatAdapterDecodedResult.Text(
            text = ProviderWebSearchToolTraceText.visibleText(text).cleanChatReply()
                ?: return ChatAdapterDecodedResult.EmptyOrMalformed,
            inputTokens = usage?.long("input_tokens", "prompt_tokens"),
            outputTokens = usage?.long("output_tokens", "completion_tokens"),
            reasoning = outputItems.asSequence()
                .filter { it.stringValue("type") in setOf("reasoning", "analysis") }
                .flatMap { item -> item.reasoningFragments().asSequence() }
                .joinToString("\n")
                .cleanChatReply(),
            webSources = outputItems.asSequence()
                .filter { it.stringValue("type") == "web_search_call" }
                .flatMap { item -> item.objectValue("action")?.arrayValue("sources").orEmpty().asSequence() }
                .mapNotNull { raw -> raw.objectValue()?.let { source ->
                    ProviderWebSource.fromProvider(
                        source.stringValue("url"),
                        source.stringValue("title") ?: source.stringValue("name"),
                    )
                } }
                .distinctBy(ProviderWebSource::url)
                .toList(),
            webSearchPerformed = outputItems.any { it.stringValue("type") == "web_search_call" },
            cachedInputTokens = usage?.objectValue("input_tokens_details")?.long("cached_tokens")
                ?: usage?.objectValue("prompt_tokens_details")?.long("cached_tokens"),
            reasoningTokens = usage?.reasoningTokens(),
        )
    }.getOrNull()

    @Suppress("UNCHECKED_CAST") private fun Any?.objectValue(): Map<String, Any?>? = this as? Map<String, Any?>
    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?>? = this[key].objectValue()
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.arrayValue(key: String): List<Any?>? = this[key] as? List<Any?>
    private fun Map<String, Any?>.stringValue(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.reasoningFragments(): List<String> = buildList {
        listOfNotNull(stringValue("reasoning"), stringValue("reasoning_content"), stringValue("summary"), stringValue("text"))
            .filter(String::isNotBlank)
            .forEach(::add)
        arrayValue("summary").orEmpty().forEach { item -> item.objectValue()?.stringValue("text")?.takeIf(String::isNotBlank)?.let(::add) }
        arrayValue("content").orEmpty().forEach { item ->
            item.objectValue()?.takeIf { it.stringValue("type") in setOf("reasoning", "reasoning_text", "analysis") }
                ?.stringValue("text")?.takeIf(String::isNotBlank)?.let(::add)
        }
    }
    private fun Map<String, Any?>.long(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
        when (val value = this[key]) {
            is java.math.BigDecimal -> runCatching { value.longValueExact() }.getOrNull()
            is Number -> value.toLong()
            else -> null
        }
    }
    private fun Map<String, Any?>.reasoningTokens(): Long? =
        objectValue("output_tokens_details")?.long("reasoning_tokens")
            ?: objectValue("completion_tokens_details")?.long("reasoning_tokens")
}

/**
 * DashScope Responses streams semantic events rather than Chat Completions chunks. Only final
 * answer deltas and reasoning deltas become message content; search/tool events remain protocol
 * metadata. A completed envelope contributes usage and provider-owned public source URLs.
 */
private object ResponsesSseJsonCodec {
    fun decode(raw: String): ProviderSseEvent? = runCatching {
        val root = StrictJson.parse(raw).objectValue() ?: return null
        when (root.stringValue("type")) {
            "response.output_text.delta" -> ProviderSseEvent(text = root.stringValue("delta")?.takeIf(String::isNotEmpty))
            "response.reasoning_text.delta" -> ProviderSseEvent(reasoning = root.stringValue("delta")?.takeIf(String::isNotEmpty))
            "response.completed" -> root.terminalEvent(ProviderStreamTerminal.COMPLETED)
            "response.incomplete" -> root.terminalEvent(ProviderStreamTerminal.INCOMPLETE)
            "response.failed" -> root.terminalEvent(ProviderStreamTerminal.FAILED)
            else -> null
        }
    }.getOrNull()

    private fun Map<String, Any?>.terminalEvent(terminal: ProviderStreamTerminal): ProviderSseEvent {
        val response = objectValue("response") ?: this
        val usage = response.objectValue("usage")
        return ProviderSseEvent(
            inputTokens = usage?.long("input_tokens", "prompt_tokens"),
            outputTokens = usage?.long("output_tokens", "completion_tokens"),
            cachedInputTokens = usage?.objectValue("input_tokens_details")?.long("cached_tokens")
                ?: usage?.objectValue("prompt_tokens_details")?.long("cached_tokens"),
            reasoningTokens = usage?.reasoningTokens(),
            webSources = response.webSources(),
            terminal = terminal,
        )
    }

    private fun Map<String, Any?>.webSources(): List<ProviderWebSource> =
        arrayValue("output").orEmpty().asSequence()
            .mapNotNull { it.objectValue() }
            .filter { it.stringValue("type") == "web_search_call" }
            .flatMap { item -> item.objectValue("action")?.arrayValue("sources").orEmpty().asSequence() }
            .mapNotNull { raw -> raw.objectValue()?.let { source ->
                ProviderWebSource.fromProvider(
                    source.stringValue("url"),
                    source.stringValue("title") ?: source.stringValue("name"),
                )
            } }
            .distinctBy(ProviderWebSource::url)
            .take(10)
            .toList()

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
    private fun Map<String, Any?>.reasoningTokens(): Long? =
        objectValue("output_tokens_details")?.long("reasoning_tokens")
            ?: objectValue("completion_tokens_details")?.long("reasoning_tokens")
}

/** OpenAI-compatible JSON projection; it intentionally has no OpenRouter-specific dependency. */
private object OpenAiCompatibleJsonCodec {
    data class Decoded(
        val text: String,
        val reasoning: String?,
        val toolCalls: List<ChatToolCall>,
        val toolCallEncountered: Boolean,
        val inputTokens: Long?,
        val outputTokens: Long?,
        val webSources: List<ProviderWebSource>,
        val reportedCostMicros: Long?,
        val reportedCostCurrencyCode: String?,
        val reportedCostPriceVersion: String?,
        val cachedInputTokens: Long?,
        val reasoningTokens: Long?,
    ) {
        fun providerReportedCost(providerId: ProviderId): ProviderReportedCost? = providerReportedCost(
            providerId, reportedCostMicros, reportedCostCurrencyCode, reportedCostPriceVersion,
        )
    }

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
            root.webSources(message),
            usage?.decimalMicros("cost"),
            usage?.stringValue("currency"),
            usage?.stringValue("price_version"),
            usage?.objectValue("input_tokens_details")?.long("cached_tokens")
                ?: usage?.objectValue("prompt_tokens_details")?.long("cached_tokens"),
            usage?.reasoningTokens(),
        )
    }.getOrNull()

    fun decodeStream(raw: String, providerId: ProviderId): ProviderSseEvent? = runCatching {
        val root = StrictJson.parse(raw).objectValue() ?: return null
        val choice = root.arrayValue("choices")?.firstOrNull().objectValue()
        val delta = choice?.objectValue("delta")
        val usage = root.objectValue("usage")
        val text = delta?.contentText()
        val reasoning = delta?.stringValue("reasoning_content")?.takeIf(String::isNotBlank)
            ?: delta?.stringValue("reasoning")?.takeIf(String::isNotBlank)
        val rawTools = delta?.arrayValue("tool_calls")
        val tools = rawTools.toolCalls().orEmpty()
        val toolDeltas = rawTools.toolCallDeltas()
        val input = usage?.long("prompt_tokens", "input_tokens")
        val output = usage?.long("completion_tokens", "output_tokens")
        val cost = usage?.decimalMicros("cost")
        val providerCost = providerReportedCost(providerId, cost, usage?.stringValue("currency"), usage?.stringValue("price_version"))
        val cachedInput = usage?.objectValue("input_tokens_details")?.long("cached_tokens")
            ?: usage?.objectValue("prompt_tokens_details")?.long("cached_tokens")
        val reasoningTokens = usage?.reasoningTokens()
        val webSources = root.webSources(delta.orEmpty())
        val terminal = when (choice?.stringValue("finish_reason")) {
            "length", "max_tokens" -> ProviderStreamTerminal.INCOMPLETE
            "content_filter" -> ProviderStreamTerminal.FAILED
            "stop" -> ProviderStreamTerminal.COMPLETED
            else -> null
        }
        if (terminal == null && text == null && reasoning == null && toolDeltas.isEmpty() && input == null && output == null && cost == null && reasoningTokens == null && webSources.isEmpty()) null
        else ProviderSseEvent(
            text, reasoning, input, output,
            reportedProviderCost = providerCost,
            toolCallEncountered = rawTools?.isNotEmpty() == true,
            webSources = webSources,
            toolCallDeltas = toolDeltas,
            cachedInputTokens = cachedInput,
            reasoningTokens = reasoningTokens,
            terminal = terminal,
        )
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

    /** Zhipu returns web-search sources at the envelope level; OpenRouter uses annotations. */
    private fun Map<String, Any?>.webSources(message: Map<String, Any?>): List<ProviderWebSource> = buildList {
        message.arrayValue("annotations").orEmpty().forEach { raw ->
            raw.objectValue()?.objectValue("url_citation")?.asProviderWebSource()?.let(::add)
        }
        arrayValue("web_search").orEmpty().forEach { raw ->
            val item = raw.objectValue() ?: return@forEach
            item.asProviderWebSource()?.let(::add)
            listOf("search_result", "search_results", "results", "sources").forEach { key ->
                item.arrayValue(key).orEmpty().forEach { candidate -> candidate.objectValue()?.asProviderWebSource()?.let(::add) }
            }
        }
    }.distinctBy(ProviderWebSource::url).take(10)

    private fun Map<String, Any?>.asProviderWebSource(): ProviderWebSource? {
        return ProviderWebSource.fromProvider(
            stringValue("url") ?: stringValue("link"),
            stringValue("title") ?: stringValue("name"),
        )
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

    private fun List<Any?>?.toolCallDeltas(): List<ChatToolCallDelta> = buildList {
        this@toolCallDeltas ?: return@buildList
        this@toolCallDeltas.forEachIndexed { fallbackIndex, rawCall ->
            val call = rawCall.objectValue() ?: return@forEachIndexed
            val function = call.objectValue("function")
            add(ChatToolCallDelta(
                index = call.intValue("index") ?: fallbackIndex,
                id = call.stringValue("id")?.takeIf(String::isNotBlank),
                name = function?.stringValue("name")?.takeIf(String::isNotBlank),
                argumentsDelta = function?.stringValue("arguments").orEmpty(),
            ))
        }
    }

    private fun Map<String, Any?>.intValue(key: String): Int? = when (val value = this[key]) {
        is java.math.BigDecimal -> runCatching { value.intValueExact() }.getOrNull()
        is Number -> value.toInt()
        else -> null
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

    private fun Map<String, Any?>.reasoningTokens(): Long? =
        objectValue("output_tokens_details")?.long("reasoning_tokens")
            ?: objectValue("completion_tokens_details")?.long("reasoning_tokens")

    @Suppress("UNCHECKED_CAST") private fun Any?.objectValue(): Map<String, Any?>? = this as? Map<String, Any?>
    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?>? = this[key].objectValue()
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.arrayValue(key: String): List<Any?>? = this[key] as? List<Any?>
    private fun Map<String, Any?>.stringValue(key: String): String? = this[key] as? String
}

/**
 * The wire field is provider-neutral.  A response-declared currency wins; the fallback is only
 * the official billing currency for direct endpoints that omit it, never a calculated amount.
 */
private fun providerReportedCost(
    providerId: ProviderId,
    totalMicros: Long?,
    reportedCurrencyCode: String?,
    reportedPriceVersion: String?,
): ProviderReportedCost? {
    val amount = totalMicros?.takeIf { it >= 0 } ?: return null
    val currency = reportedCurrencyCode?.trim()?.uppercase(java.util.Locale.ROOT)
        ?.takeIf { it.matches(Regex("[A-Z]{3}")) }
        ?: providerId.directProviderBillingCurrency()
        ?: return null
    val version = reportedPriceVersion?.trim()?.takeIf(String::isNotBlank)
        ?: "${providerId.name.lowercase(java.util.Locale.ROOT)}-provider-response"
    return ProviderReportedCost(amount, currency, version)
}

private fun ProviderId.directProviderBillingCurrency(): String? = when (this) {
    ProviderId.OPENROUTER -> "USD"
    ProviderId.QWEN, ProviderId.DEEPSEEK, ProviderId.ZHIPU -> "CNY"
    ProviderId.MOCK -> null
}
