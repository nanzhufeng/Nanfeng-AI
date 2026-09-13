package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ResolvedModel
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * App-level attachment capability: a selected model receives only its own native media parts,
 * plus bounded text extracted locally. Unsupported binary material is rejected before egress;
 * ordinary chat never sends it through another model as an implicit conversion step.
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

class UniversalChatAttachmentBridge : ChatAttachmentBridge {
    override fun resolve(
        conversationId: ConversationId,
        targetModel: ResolvedModel,
        userRequest: String,
        attachments: List<ChatAttachment>,
        forceTextProjection: Boolean,
    ): ChatAttachmentBridgeResult {
        if (attachments.isEmpty()) return ChatAttachmentBridgeResult.Ready("", emptyList())
        val providerAttachments = mutableListOf<ChatAttachment>()
        val localText = mutableListOf<String>()

        attachments.forEach { attachment ->
            if (attachment.kind == ChatAttachmentKind.FILE) {
                val text = inlineUtf8TextFiles(listOf(attachment))
                    ?: return ChatAttachmentBridgeResult.Failed(ChatAttachmentBridgeResult.Failure.FORMAT)
                localText += text
                return@forEach
            }
            // A text-only search protocol may not carry media even when the ordinary endpoint
            // can. It may use a local PDF text layer, but never a second provider as a bridge.
            if (!forceTextProjection && targetModel.nativelyAccepts(attachment.kind)) {
                providerAttachments += attachment
                return@forEach
            }
            if (attachment.kind == ChatAttachmentKind.PDF) {
                extractPdfText(attachment)?.let { text ->
                    localText += materialText(attachment, "本机 PDF 文本层", text)
                    return@forEach
                }
            } else {
                return ChatAttachmentBridgeResult.Failed(ChatAttachmentBridgeResult.Failure.FORMAT)
            }
        }

        val context = localText.joinToString("\n\n").takeIf(String::isNotBlank).orEmpty()
        if (context.length > MAX_BRIDGE_CONTEXT_CHARS) {
            return ChatAttachmentBridgeResult.Failed(ChatAttachmentBridgeResult.Failure.FORMAT)
        }
        return ChatAttachmentBridgeResult.Ready(context, providerAttachments)
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

    private fun ResolvedModel.nativelyAccepts(kind: ChatAttachmentKind): Boolean = when (kind) {
        ChatAttachmentKind.IMAGE -> capabilities.supportsVision
        ChatAttachmentKind.PDF -> capabilities.supportsPdf
        ChatAttachmentKind.VIDEO -> capabilities.supportsVideo
        ChatAttachmentKind.AUDIO -> capabilities.supportsAudio
        ChatAttachmentKind.FILE -> true
    }
    private companion object { const val MAX_BRIDGE_CONTEXT_CHARS = 800_000 }
}
