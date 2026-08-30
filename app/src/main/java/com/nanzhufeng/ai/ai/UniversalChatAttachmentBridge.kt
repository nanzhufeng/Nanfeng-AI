package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AiTaskId
import com.nanzhufeng.ai.domain.AttachmentOpenResult
import com.nanzhufeng.ai.domain.ConversationCostEstimator
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.InvocationRecord
import com.nanzhufeng.ai.domain.InvocationRepository
import com.nanzhufeng.ai.domain.InvocationStatus
import com.nanzhufeng.ai.domain.GlmOcrTransport
import com.nanzhufeng.ai.domain.GlmOcrTransportRequest
import com.nanzhufeng.ai.domain.GlmOcrTransportResult
import com.nanzhufeng.ai.domain.LoadModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelResolver
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.ResolvedModel
import com.nanzhufeng.ai.domain.ResolvedModelResult
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.time.Clock

/**
 * App-level attachment capability: models receive either their native media parts or a complete
 * text projection produced before the selected model is called.  The bridge never changes the
 * selected answer model and never stores source bytes, prompts or bridge output in its ledger.
 */
fun interface ChatAttachmentBridge {
    fun resolve(
        conversationId: ConversationId,
        targetModel: ResolvedModel,
        userRequest: String,
        attachments: List<ChatAttachment>,
        forceTextProjection: Boolean,
    ): ChatAttachmentBridgeResult
}

sealed interface ChatAttachmentBridgeResult {
    data class Ready(
        val contextText: String,
        val providerAttachments: List<ChatAttachment>,
        val receivers: Set<String> = emptySet(),
    ) : ChatAttachmentBridgeResult

    data class Failed(val reason: Failure) : ChatAttachmentBridgeResult

    enum class Failure { SERVICE_NOT_CONFIGURED, CREDENTIAL_MISSING, FORMAT, NETWORK, PROVIDER }
}

object PassthroughChatAttachmentBridge : ChatAttachmentBridge {
    override fun resolve(
        conversationId: ConversationId,
        targetModel: ResolvedModel,
        userRequest: String,
        attachments: List<ChatAttachment>,
        forceTextProjection: Boolean,
    ) = ChatAttachmentBridgeResult.Ready("", attachments)
}

class UniversalChatAttachmentBridge(
    private val configuration: LoadModelServiceConfigurationUseCase,
    private val credentials: ProviderCredentialStore,
    private val modelResolver: ModelResolver,
    private val providerTransport: ProviderChatTransport,
    private val glmOcrTransport: GlmOcrTransport,
    private val invocations: InvocationRepository,
    private val clock: Clock,
) : ChatAttachmentBridge {
    override fun resolve(
        conversationId: ConversationId,
        targetModel: ResolvedModel,
        userRequest: String,
        attachments: List<ChatAttachment>,
        forceTextProjection: Boolean,
    ): ChatAttachmentBridgeResult {
        if (attachments.isEmpty()) return ChatAttachmentBridgeResult.Ready("", emptyList())
        val providerAttachments = mutableListOf<ChatAttachment>()
        val localOrBridgeText = mutableListOf<String>()
        val qwenMedia = mutableListOf<ChatAttachment>()
        val receivers = linkedSetOf<String>()

        attachments.forEach { attachment ->
            if (attachment.kind == ChatAttachmentKind.FILE) {
                val text = inlineUtf8TextFiles(listOf(attachment))
                    ?: return ChatAttachmentBridgeResult.Failed(ChatAttachmentBridgeResult.Failure.FORMAT)
                localOrBridgeText += text
                return@forEach
            }
            // Official web-search protocols are often text-only even when the same model's
            // ordinary chat endpoint accepts media. Project all binary material before entering
            // those routes so toggling search never revives an attachment-schema failure.
            if (!forceTextProjection && targetModel.nativelyAccepts(attachment.kind)) {
                providerAttachments += attachment
                return@forEach
            }
            if (attachment.kind == ChatAttachmentKind.PDF) {
                extractPdfText(attachment)?.let { text ->
                    localOrBridgeText += materialText(attachment, "本机 PDF 文本层", text)
                    return@forEach
                }
                when (val ocr = transcribePdfWithGlmOcr(conversationId, attachment)) {
                    is BridgeText.Completed -> {
                        localOrBridgeText += materialText(attachment, "智谱 GLM-OCR", ocr.text)
                        receivers += "智谱 GLM-OCR"
                    }
                    is BridgeText.Unavailable -> qwenMedia += attachment
                    is BridgeText.Failed -> return ChatAttachmentBridgeResult.Failed(ocr.reason)
                }
            } else {
                qwenMedia += attachment
            }
        }

        if (qwenMedia.isNotEmpty()) {
            when (val qwen = describeWithQwen(conversationId, userRequest, qwenMedia)) {
                is BridgeText.Completed -> {
                    localOrBridgeText += qwen.text
                    receivers += "千问 Qwen3.7-Plus"
                }
                is BridgeText.Unavailable -> {
                    // Images still have an OCR fallback when the visual bridge is not configured.
                    if (qwenMedia.all { it.kind == ChatAttachmentKind.IMAGE }) {
                        qwenMedia.forEach { image ->
                            when (val ocr = transcribeImageWithGlmOcr(conversationId, image)) {
                                is BridgeText.Completed -> {
                                    localOrBridgeText += materialText(image, "智谱 GLM-OCR", ocr.text)
                                    receivers += "智谱 GLM-OCR"
                                }
                                is BridgeText.Unavailable -> return ChatAttachmentBridgeResult.Failed(ocr.reason)
                                is BridgeText.Failed -> return ChatAttachmentBridgeResult.Failed(ocr.reason)
                            }
                        }
                    } else return ChatAttachmentBridgeResult.Failed(qwen.reason)
                }
                is BridgeText.Failed -> return ChatAttachmentBridgeResult.Failed(qwen.reason)
            }
        }

        val context = localOrBridgeText.joinToString("\n\n").takeIf(String::isNotBlank).orEmpty()
        if (context.length > MAX_BRIDGE_CONTEXT_CHARS) {
            return ChatAttachmentBridgeResult.Failed(ChatAttachmentBridgeResult.Failure.FORMAT)
        }
        return ChatAttachmentBridgeResult.Ready(context, providerAttachments, receivers)
    }

    private fun describeWithQwen(
        conversationId: ConversationId,
        userRequest: String,
        attachments: List<ChatAttachment>,
    ): BridgeText {
        val config = configuration.execute(ProviderId.QWEN)
            ?: return BridgeText.Unavailable(ChatAttachmentBridgeResult.Failure.SERVICE_NOT_CONFIGURED)
        if (!config.settings.enabled) return BridgeText.Unavailable(ChatAttachmentBridgeResult.Failure.SERVICE_NOT_CONFIGURED)
        val credential = credentials.loadCredential(ProviderId.QWEN)
            ?: return BridgeText.Unavailable(ChatAttachmentBridgeResult.Failure.CREDENTIAL_MISSING)
        val model = (modelResolver.resolve(ModelPresetId.QWEN_3_7_PLUS) as? ResolvedModelResult.Resolved)?.model
            ?: return BridgeText.Unavailable(ChatAttachmentBridgeResult.Failure.SERVICE_NOT_CONFIGURED).also { credential.fill('\u0000') }
        val adapter = QwenChatAdapter()
        val prompt = buildString {
            append("你是南枫 AI 的附件解析层。请忠实读取所有材料，输出供另一个文本模型继续分析的 Markdown：保留可见文字、表格、顺序、画面事实、时间轴和不确定处；不要回答用户的最终问题，不得编造看不见的内容。")
            userRequest.trim().take(4_000).takeIf(String::isNotBlank)?.let { append("\n用户后续任务：").append(it) }
        }
        val prepared = adapter.prepare(model, listOf("user" to prompt), attachments, stream = false)
            as? ChatAdapterPrepareResult.Ready
            ?: return BridgeText.Failed(ChatAttachmentBridgeResult.Failure.FORMAT).also { credential.fill('\u0000') }
        val startedAt = clock.instant()
        val outcome = try {
            providerTransport.execute(
                ProviderChatRequest(
                    endpoint = "${config.provider.fixedEndpoint}${adapter.endpointPath(ChatRequestOptions.Standard)}",
                    jsonBody = prepared.jsonBody,
                    body = prepared.body,
                    expectsStream = false,
                    readTimeoutMillis = 300_000,
                    maxStreamDurationMillis = 300_000,
                    maxResponseBytes = ProviderResponseByteBudget.forMaxOutputTokens(model.maxOutputTokens),
                ),
                credential,
            )
        } finally {
            credential.fill('\u0000')
        }
        val decoded = (outcome as? ProviderChatOutcome.HttpResponse)
            ?.takeIf { it.statusCode in 200..299 }
            ?.let { adapter.decodeNonStreaming(it.responseBody) as? ChatAdapterDecodedResult.Text }
        val usage = ProviderUsage(
            inputTokens = decoded?.inputTokens,
            outputTokens = decoded?.outputTokens,
            cachedInputTokens = decoded?.cachedInputTokens,
            reasoningTokens = decoded?.reasoningTokens,
        )
        val cost = ConversationCostEstimator.estimate(model.modelId, usage, clock.instant()) ?: ProviderCost()
        recordBridgeInvocation(conversationId, ProviderId.QWEN, model.modelId, startedAt, decoded != null, usage, cost)
        return decoded?.text?.takeIf(String::isNotBlank)?.let(BridgeText::Completed)
            ?: BridgeText.Failed(
                if (outcome is ProviderChatOutcome.NetworkFailure || outcome is ProviderChatOutcome.TimedOut) ChatAttachmentBridgeResult.Failure.NETWORK
                else ChatAttachmentBridgeResult.Failure.PROVIDER,
            )
    }

    private fun transcribePdfWithGlmOcr(conversationId: ConversationId, attachment: ChatAttachment): BridgeText =
        transcribeWithGlmOcr(conversationId, attachment)

    private fun transcribeImageWithGlmOcr(conversationId: ConversationId, attachment: ChatAttachment): BridgeText =
        transcribeWithGlmOcr(conversationId, attachment)

    private fun transcribeWithGlmOcr(conversationId: ConversationId, attachment: ChatAttachment): BridgeText {
        val config = configuration.execute(ProviderId.ZHIPU)
            ?: return BridgeText.Unavailable(ChatAttachmentBridgeResult.Failure.SERVICE_NOT_CONFIGURED)
        if (!config.settings.enabled) return BridgeText.Unavailable(ChatAttachmentBridgeResult.Failure.SERVICE_NOT_CONFIGURED)
        val credential = credentials.loadCredential(ProviderId.ZHIPU)
            ?: return BridgeText.Unavailable(ChatAttachmentBridgeResult.Failure.CREDENTIAL_MISSING)
        val startedAt = clock.instant()
        val result = try {
            glmOcrTransport.execute(
                GlmOcrTransportRequest(AttachmentOpenResult.Opened(attachment.byteCount, attachment::open), attachment.mimeType),
                credential,
            )
        } finally {
            credential.fill('\u0000')
        }
        return when (result) {
            is GlmOcrTransportResult.Completed -> {
                val usage = ProviderUsage(
                    inputTokens = result.inputTokens,
                    outputTokens = result.outputTokens,
                    totalTokens = listOfNotNull(result.inputTokens, result.outputTokens).sum().takeIf { it > 0 },
                )
                val total = usage.totalTokens
                val cost = ProviderCost(
                    priceVersion = total?.let { "glm-ocr-cny-0.2-per-million-v1" },
                    currencyCode = total?.let { "CNY" },
                    totalMicros = total?.let { (it * 200_000L + 999_999L) / 1_000_000L },
                )
                recordBridgeInvocation(conversationId, ProviderId.ZHIPU, "glm-ocr", startedAt, true, usage, cost)
                BridgeText.Completed(result.markdown)
            }
            is GlmOcrTransportResult.Failed -> {
                recordBridgeInvocation(conversationId, ProviderId.ZHIPU, "glm-ocr", startedAt, false)
                BridgeText.Failed(ChatAttachmentBridgeResult.Failure.PROVIDER)
            }
        }
    }

    private fun extractPdfText(attachment: ChatAttachment): String? = runCatching {
        val bytes = attachment.open().use { it.readBytes() }
        if (bytes.size < 5 || !bytes.copyOfRange(0, minOf(bytes.size, 8)).toString(Charsets.ISO_8859_1).startsWith("%PDF-")) return null
        PDDocument.load(bytes, "", null, null, MemoryUsageSetting.setupMixed(2L * 1024 * 1024)).use { document ->
            if (document.isEncrypted || !document.currentAccessPermission.canExtractContent()) return null
            val text = PDFTextStripper().apply { sortByPosition = false }.getText(document)
                .replace("\r\n", "\n").replace('\r', '\n').trim()
            text.takeIf(String::isNotBlank)
        }
    }.getOrNull()

    private fun materialText(attachment: ChatAttachment, source: String, text: String): String =
        "以下是附件 ${attachment.fileName} 经${source}完整转换的内容：\n---\n$text\n---"

    private fun recordBridgeInvocation(
        conversationId: ConversationId,
        providerId: ProviderId,
        modelId: String,
        startedAt: java.time.Instant,
        succeeded: Boolean,
        usage: ProviderUsage = ProviderUsage(),
        cost: ProviderCost = ProviderCost(),
    ) {
        val id = InvocationId.new()
        val completedAt = clock.instant().coerceAtLeast(startedAt)
        runCatching {
            invocations.save(
                InvocationRecord(
                    id = id,
                    taskId = AiTaskId("chat-attachment-bridge:${conversationId.value}:${id.value}"),
                    providerId = providerId,
                    modelId = modelId,
                    harnessVersion = 1,
                    completedAt = completedAt,
                    status = if (succeeded) InvocationStatus.SUCCEEDED else InvocationStatus.FAILED,
                    error = if (succeeded) null else AiTaskError.ProviderFailure,
                    pricingVersion = cost.priceVersion,
                    usage = usage,
                    cost = cost,
                ),
            )
        }
    }

    private fun ResolvedModel.nativelyAccepts(kind: ChatAttachmentKind): Boolean = when (kind) {
        ChatAttachmentKind.IMAGE -> capabilities.supportsVision
        ChatAttachmentKind.PDF -> capabilities.supportsPdf
        ChatAttachmentKind.VIDEO -> capabilities.supportsVideo
        ChatAttachmentKind.AUDIO -> capabilities.supportsAudio
        ChatAttachmentKind.FILE -> true
    }

    private sealed interface BridgeText {
        data class Completed(val text: String) : BridgeText
        data class Unavailable(val reason: ChatAttachmentBridgeResult.Failure) : BridgeText
        data class Failed(val reason: ChatAttachmentBridgeResult.Failure) : BridgeText
    }

    private companion object { const val MAX_BRIDGE_CONTEXT_CHARS = 800_000 }
}
