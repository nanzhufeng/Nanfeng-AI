package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ResolvedModel
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * App-level attachment capability: a selected model receives only its own native media parts,
 * plus bounded text extracted locally. Unsupported binary material is rejected before egress;
 * ordinary chat never sends it through an undisclosed recipient. Explicit auxiliary reading is authorized by the executor.
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
        val searchProjection:()->Pair<String,List<ChatAttachment>> = {contextText to providerAttachments.filter {it.kind!=ChatAttachmentKind.FILE}},
        val readingAttachments: List<ChatAttachment> = emptyList(),
    ) : ChatAttachmentBridgeResult {
        private val projected by lazy(searchProjection)
        val searchText:String get()=projected.first
        val unprojectedSearchAttachments:List<ChatAttachment> get()=projected.second
    }

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
        val reading = mutableListOf<ChatAttachment>()


        attachments.forEach { attachment ->
            if (attachment.kind == ChatAttachmentKind.FILE) {
                val text = (if (attachment.mimeType == "application/zip") extractArchiveText(attachment)
                    else inlineUtf8TextFiles(listOf(attachment)))
                    ?: return ChatAttachmentBridgeResult.Failed(ChatAttachmentBridgeResult.Failure.FORMAT)
                localText += text
                return@forEach
            }
            val pdf=if(attachment.kind==ChatAttachmentKind.PDF) readPdf(attachment)
                ?: return ChatAttachmentBridgeResult.Failed(ChatAttachmentBridgeResult.Failure.FORMAT) else null
            if (com.nanzhufeng.ai.domain.AttachmentInputPolicy.acceptsNative(targetModel,attachment.mimeType,forceTextProjection)) {
                providerAttachments += attachment
                return@forEach
            }
            if(pdf?.text!=null) {
                localText += materialText(attachment,"本机 PDF 文本层",pdf.text)
            } else if(com.nanzhufeng.ai.domain.AttachmentInputPolicy.readerFor(attachment.mimeType)!=null) {
                reading += attachment
            } else {
                return ChatAttachmentBridgeResult.Failed(ChatAttachmentBridgeResult.Failure.FORMAT)
            }
        }

        val context = localText.joinToString("\n\n").takeIf(String::isNotBlank).orEmpty()
        if (context.length > MAX_BRIDGE_CONTEXT_CHARS) {
            return ChatAttachmentBridgeResult.Failed(ChatAttachmentBridgeResult.Failure.FORMAT)
        }
        return ChatAttachmentBridgeResult.Ready(context,providerAttachments,searchProjection={
            val extra=mutableListOf<String>();val unprojected=mutableListOf<ChatAttachment>()
            providerAttachments.forEach {attachment ->
                val pdf=if(attachment.kind==ChatAttachmentKind.PDF) extractPdfText(attachment) else null
                if(pdf!=null) extra+=materialText(attachment,"本机 PDF 文本层",pdf) else unprojected+=attachment
            }
            (localText+extra).joinToString("\n\n") to unprojected
        }, receivers=reading.mapNotNull {com.nanzhufeng.ai.domain.AttachmentInputPolicy.readerFor(it.mimeType)?.label}.toSet(), readingAttachments=reading.toList())
    }

    private data class PdfProjection(val text:String?)
    private fun readPdf(attachment:ChatAttachment):PdfProjection? = runCatching {
        val bytes=attachment.open().use {it.readBytes()}
        if(!bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray())) return null
        PDDocument.load(bytes,"",null,null,MemoryUsageSetting.setupMixed(2L*1024*1024)).use {document ->
            if(document.isEncrypted || !document.currentAccessPermission.canExtractContent() || document.numberOfPages !in 1..2000) return null
            val pieces=mutableListOf<String>();var requiresReading=false;var count=0
            for(index in 0 until document.numberOfPages) {
                val page=document.getPage(index)
                val text=PDFTextStripper().apply {startPage=index+1;endPage=index+1}.getText(document).replace("\r\n","\n").trim()
                count+=text.length;if(count>MAX_BRIDGE_CONTEXT_CHARS) return null
                val parser=com.tom_roush.pdfbox.pdfparser.PDFStreamParser(page).apply {parse()}
                val hasDrawing=parser.tokens.any {(it as? com.tom_roush.pdfbox.contentstream.operator.Operator)?.name in setOf("S","s","f","F","f*","B","B*","b","b*","sh","Do")}
                if(text.isBlank() || hasDrawing || page.resources?.xObjectNames?.iterator()?.hasNext()==true) requiresReading=true
                pieces+=text
            }
            PdfProjection(if(requiresReading) null else pieces.joinToString("\n\n"))
        }
    }.getOrNull()
    private fun extractPdfText(attachment:ChatAttachment):String?=readPdf(attachment)?.text

    private fun materialText(attachment: ChatAttachment, source: String, text: String): String =
        "以下是附件 ${attachment.fileName} 经${source}完整转换的内容：\n---\n$text\n---"

    /** All readable members must succeed; never send a directory listing as their contents. */
    private fun extractArchiveText(attachment: ChatAttachment): String? = runCatching {
        val texts = mutableListOf<String>()
        var entries = 0
        var totalBytes = 0L
        var contextChars = 0
        val paths = mutableSetOf<String>()
        java.util.zip.ZipInputStream(attachment.open()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > 2_000) return null
                val path = entry.name
                if (path.startsWith('/') || path.contains('\\') || path.contains(':') || path.contains('\u0000') ||
                    path.trimEnd('/').split('/').any { it.isBlank() || it == "." || it == ".." } || !paths.add(path)) return null
                if (entry.isDirectory) { if (zip.read() != -1) return null; zip.closeEntry(); continue }
                val mime = when (path.substringAfterLast('.', "").lowercase()) {
                    "txt" -> "text/plain"; "md", "markdown" -> "text/markdown"
                    "json" -> "application/json"; "csv" -> "text/csv"; "xml" -> "application/xml"
                    "yaml", "yml" -> "application/x-yaml"; "html", "htm" -> "text/html"
                    "docx" -> com.nanzhufeng.ai.domain.DOCX_MIME_TYPE
                    "xlsx" -> com.nanzhufeng.ai.domain.XLSX_MIME_TYPE
                    "pptx" -> com.nanzhufeng.ai.domain.PPTX_MIME_TYPE
                    "pdf" -> "application/pdf"
                    else -> return null
                }
                val bytes = java.io.ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (output.size() + count > 16 * 1024 * 1024 || totalBytes > 40L * 1024 * 1024) return null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                val member = ChatAttachment(if (mime == "application/pdf") ChatAttachmentKind.PDF else ChatAttachmentKind.FILE, mime, path, bytes)
                val text = if (member.kind == ChatAttachmentKind.PDF) extractPdfText(member)?.let { materialText(member, "本机 PDF 文本层", it) }
                    else inlineUtf8TextFiles(listOf(member))
                if (text.isNullOrBlank()) return null
                contextChars += text.length
                if (contextChars > MAX_BRIDGE_CONTEXT_CHARS) return null
                texts += text
                zip.closeEntry()
            }
        }
        texts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }.getOrNull()

    private companion object { const val MAX_BRIDGE_CONTEXT_CHARS = 800_000 }
}
