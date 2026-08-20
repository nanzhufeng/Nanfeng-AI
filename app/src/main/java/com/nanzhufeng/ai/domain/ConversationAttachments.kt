package com.nanzhufeng.ai.domain

/** P3-G's fixed local-only capability. It is intentionally not an egress consent. */
enum class ConversationAttachmentEgressScope { LOCAL_ONLY_NO_EGRESS }

sealed interface AddConversationAttachmentResult {
    data class Added(val draft: ConversationDraft) : AddConversationAttachmentResult
    data object Cancelled : AddConversationAttachmentResult
    data class Rejected(val reason: String) : AddConversationAttachmentResult
}

/**
 * Photo Picker input -> private copy -> Attachment Domain catalog -> safe Conversation reference.
 * This use case never creates a RunSpec, Authorization, invocation, runtime event or network call.
 */
class AddConversationImageAttachmentUseCase(
    private val privateStore: PrivateAttachmentStore,
    private val assets: PrivateAttachmentRepository,
    private val drafts: ConversationDraftRepository,
    private val clock: java.time.Clock,
) {
    fun execute(conversationId: ConversationId, selection: GalleryImageSelection?): AddConversationAttachmentResult {
        return add(conversationId, selection?.let { ConversationAttachmentSelection(it.input, it.mimeType, it.displayName, it.width, it.height) })
    }

    fun add(conversationId: ConversationId, selection: ConversationAttachmentSelection?): AddConversationAttachmentResult {
        if (selection == null) return AddConversationAttachmentResult.Cancelled
        if (selection.mimeType !in CONVERSATION_ALLOWED_MIME_TYPES) return reject("只支持 JPG、PNG、WebP、MP4、MP3、WAV、M4A、PDF 或安全文本文件。")
        if (selection.mimeType in CONVERSATION_ALLOWED_IMAGE_MIME_TYPES) {
            val pixels = selection.width?.toLong()?.times(selection.height?.toLong() ?: 0L)
            if (pixels == null || pixels <= 0 || pixels > CONVERSATION_ATTACHMENT_MAX_SOURCE_PIXELS) {
                return reject("图片像素超过本地安全解码上限，当前草稿未改变。")
            }
        }
        val current = drafts.loadDraft(conversationId) ?: return reject("会话草稿未能从本机回读。")
        if (current.attachments.size >= CONVERSATION_ATTACHMENT_MAX_COUNT) return reject("每个会话最多添加 $CONVERSATION_ATTACHMENT_MAX_COUNT 张图片。")
        val imported = privateStore.import(AttachmentImportRequest(selection.input, selection.mimeType, selection.displayName))
        val importedAsset = when (imported) {
            is AttachmentImportResult.Imported -> imported.attachment
            is AttachmentImportResult.Rejected -> return reject(imported.error.toConversationAttachmentReason())
        }
        val stableAsset = runCatching { assets.save(importedAsset) }
            .getOrElse { return reject("附件已复制但目录写入失败，当前草稿未改变。") }
        val safeReference = runCatching { stableAsset.toConversationReference() }
            .getOrElse { return reject("附件元数据不完整，当前草稿未改变。") }
        if (current.attachments.any { it.id == safeReference.id || it.sha256 == safeReference.sha256 }) {
            return AddConversationAttachmentResult.Added(current)
        }
        val next = runCatching {
            ConversationDraftPolicy.normalize(current.text, current.attachments + safeReference, clock.instant())
        }.getOrElse { return reject(it.message ?: "图片不符合当前会话限制。") }
        return runCatching { AddConversationAttachmentResult.Added(drafts.saveDraft(conversationId, next)) }
            .getOrElse { reject("图片草稿未保存，原有文字和附件仍保留。") }
    }

    private fun reject(reason: String) = AddConversationAttachmentResult.Rejected(reason)
}

/** Input adapters expose a stream and safe display metadata only; URI/path never reaches the domain. */
data class ConversationAttachmentSelection(
    val input: java.io.InputStream,
    val mimeType: String,
    val displayName: String?,
    val width: Int? = null,
    val height: Int? = null,
)

sealed interface RemoveConversationAttachmentResult {
    data class Removed(val draft: ConversationDraft) : RemoveConversationAttachmentResult
    data class Rejected(val reason: String) : RemoveConversationAttachmentResult
}

/** Removing a conversation reference never deletes the private asset or any other reference. */
class RemoveConversationAttachmentUseCase(
    private val drafts: ConversationDraftRepository,
    private val clock: java.time.Clock,
) {
    fun execute(conversationId: ConversationId, id: AttachmentId): RemoveConversationAttachmentResult {
        val current = drafts.loadDraft(conversationId) ?: return RemoveConversationAttachmentResult.Rejected("会话草稿未能从本机回读。")
        val nextAttachments = current.attachments.filterNot { it.id == id }
        if (nextAttachments.size == current.attachments.size) return RemoveConversationAttachmentResult.Rejected("该图片不在当前会话草稿中。")
        val next = ConversationDraftPolicy.normalize(current.text, nextAttachments, clock.instant())
        return runCatching { RemoveConversationAttachmentResult.Removed(drafts.saveDraft(conversationId, next)) }
            .getOrElse { RemoveConversationAttachmentResult.Rejected("移除未保存，当前草稿仍保留。") }
    }
}

data class ConversationAttachmentPreview(
    val id: AttachmentId,
    val mimeType: String,
    val displayName: String?,
    val byteCount: Long,
    val thumbnail: AttachmentThumbnail?,
    val videoDurationMillis: Long? = null,
    val audioDurationMillis: Long? = null,
    val unavailableReason: String? = null,
)

/** Original display bytes exist only while the user has the one local preview open. */
data class ConversationAttachmentOriginalPreview(
    val id: AttachmentId,
    val mimeType: String,
    val displayName: String?,
    val byteCount: Long,
    val bytes: ByteArray?,
    val unavailableReason: String? = null,
)

data class ConversationAttachmentPdfPreview(
    val id: AttachmentId,
    val displayName: String?,
    val byteCount: Long,
    val page: AttachmentPdfPage?,
    val unavailableReason: String? = null,
)

data class ConversationAttachmentVideoPreview(
    val id: AttachmentId,
    val displayName: String?,
    val byteCount: Long,
    val poster: AttachmentThumbnail? = null,
    val durationMillis: Long? = null,
    val positionMillis: Long = 0L,
    val bytes: ByteArray? = null,
    val unavailableReason: String? = null,
)

data class ConversationAttachmentAudioPreview(
    val id: AttachmentId,
    val displayName: String?,
    val byteCount: Long,
    val mimeType: String,
    val durationMillis: Long? = null,
    val positionMillis: Long = 0L,
    /** Present only while the explicit local player is open. */
    val bytes: ByteArray? = null,
    val unavailableReason: String? = null,
)

data class ConversationAttachmentTextPreview(
    val id: AttachmentId,
    val displayName: String?,
    val mimeType: String,
    val byteCount: Long,
    val text: String? = null,
    val truncated: Boolean = false,
    val unavailableReason: String? = null,
)

/** UI asks this projection for a bounded thumbnail; it never receives a private path or URI. */
class ConversationAttachmentPreviewProjection(
    private val assets: PrivateAttachmentRepository,
    private val privateStore: PrivateAttachmentStore,
) {
    /** TEMP owns opaque IDs; resolve the same safe reference only when a local preview is needed. */
    fun referenceFor(id: AttachmentId): ConversationAttachmentReference? =
        assets.findById(id)?.toConversationReference()

    fun project(id: AttachmentId): ConversationAttachmentPreview? =
        referenceFor(id)?.let(::project)

    fun project(reference: ConversationAttachmentReference): ConversationAttachmentPreview {
        val asset = assets.findById(reference.id)
            ?: return ConversationAttachmentPreview(reference.id, reference.mimeType, reference.displayName, reference.byteCount, null, unavailableReason = "本地附件不可用")
        if (asset.mimeType != reference.mimeType || asset.byteCount != reference.byteCount || asset.sha256 != reference.sha256) {
            return ConversationAttachmentPreview(reference.id, reference.mimeType, reference.displayName, reference.byteCount, null, unavailableReason = "本地附件校验不一致")
        }
        if (reference.mimeType == "video/mp4") {
            return when (val result = privateStore.videoMetadata(asset)) {
                is AttachmentVideoMetadataResult.Ready -> ConversationAttachmentPreview(
                    reference.id, reference.mimeType, reference.displayName, reference.byteCount,
                    result.metadata.poster, result.metadata.durationMillis,
                )
                is AttachmentVideoMetadataResult.Rejected -> ConversationAttachmentPreview(
                    reference.id, reference.mimeType, reference.displayName, reference.byteCount,
                    null, unavailableReason = "本地视频无法安全播放",
                )
            }
        }
        if (reference.mimeType in CONVERSATION_ALLOWED_AUDIO_MIME_TYPES) {
            return ConversationAttachmentPreview(reference.id, reference.mimeType, reference.displayName, reference.byteCount, null, audioDurationMillis = privateStore.audioDurationMillis(asset), unavailableReason = null)
        }
        return when (val result = privateStore.thumbnail(asset)) {
            is AttachmentThumbnailResult.Ready -> ConversationAttachmentPreview(reference.id, reference.mimeType, reference.displayName, reference.byteCount, result.thumbnail)
            is AttachmentThumbnailResult.Rejected -> ConversationAttachmentPreview(reference.id, reference.mimeType, reference.displayName, reference.byteCount, null, unavailableReason = "本地缩略图不可用")
        }
    }

    /** Full pixels are read only after an explicit preview tap and never expose a private path. */
    fun original(reference: ConversationAttachmentReference): ConversationAttachmentOriginalPreview {
        val asset = assets.findById(reference.id)
            ?: return ConversationAttachmentOriginalPreview(reference.id, reference.mimeType, reference.displayName, reference.byteCount, null, "本地附件不可用")
        if (asset.mimeType != reference.mimeType || asset.byteCount != reference.byteCount || asset.sha256 != reference.sha256) {
            return ConversationAttachmentOriginalPreview(reference.id, reference.mimeType, reference.displayName, reference.byteCount, null, "本地附件校验不一致")
        }
        return when (val result = privateStore.read(asset)) {
            is AttachmentReadResult.Content -> ConversationAttachmentOriginalPreview(reference.id, reference.mimeType, reference.displayName, reference.byteCount, result.bytes)
            is AttachmentReadResult.Rejected -> ConversationAttachmentOriginalPreview(reference.id, reference.mimeType, reference.displayName, reference.byteCount, null, "本地原图不可用")
        }
    }

    /** PDFRenderer receives only the matching private asset after an explicit user page request. */
    fun pdfPage(reference: ConversationAttachmentReference, pageNumber: Int): ConversationAttachmentPdfPreview {
        val asset = assets.findById(reference.id)
            ?: return ConversationAttachmentPdfPreview(reference.id, reference.displayName, reference.byteCount, null, "本地 PDF 附件不可用")
        if (reference.mimeType != "application/pdf" || asset.mimeType != reference.mimeType || asset.byteCount != reference.byteCount || asset.sha256 != reference.sha256) {
            return ConversationAttachmentPdfPreview(reference.id, reference.displayName, reference.byteCount, null, "本地 PDF 附件校验不一致")
        }
        return when (val result = privateStore.pdfPage(asset, pageNumber)) {
            is AttachmentPdfPageResult.Ready -> ConversationAttachmentPdfPreview(reference.id, reference.displayName, reference.byteCount, result.page)
            is AttachmentPdfPageResult.Rejected -> ConversationAttachmentPdfPreview(reference.id, reference.displayName, reference.byteCount, null, "本地 PDF 无法安全阅读")
        }
    }

    /** Video metadata/frame and display bytes are read only after the user opens this attachment. */
    fun video(reference: ConversationAttachmentReference): ConversationAttachmentVideoPreview {
        val asset = assets.findById(reference.id)
            ?: return ConversationAttachmentVideoPreview(reference.id, reference.displayName, reference.byteCount, unavailableReason = "本地视频附件不可用")
        if (reference.mimeType != "video/mp4" || asset.mimeType != reference.mimeType || asset.byteCount != reference.byteCount || asset.sha256 != reference.sha256) {
            return ConversationAttachmentVideoPreview(reference.id, reference.displayName, reference.byteCount, unavailableReason = "本地视频附件校验不一致")
        }
        return when (val result = privateStore.videoPreview(asset)) {
            is AttachmentVideoPreviewResult.Ready -> ConversationAttachmentVideoPreview(reference.id, reference.displayName, reference.byteCount, poster = result.preview.poster, durationMillis = result.preview.durationMillis, bytes = result.preview.bytes)
            is AttachmentVideoPreviewResult.Rejected -> ConversationAttachmentVideoPreview(reference.id, reference.displayName, reference.byteCount, unavailableReason = "本地视频无法安全播放")
        }
    }

    /** Explicit, hash-checked local audio read. The UI receives no storage capability. */
    fun audio(reference: ConversationAttachmentReference): ConversationAttachmentAudioPreview {
        val asset = assets.findById(reference.id)
            ?: return ConversationAttachmentAudioPreview(reference.id, reference.displayName, reference.byteCount, reference.mimeType, unavailableReason = "本地音频附件不可用")
        if (reference.mimeType !in CONVERSATION_ALLOWED_AUDIO_MIME_TYPES || asset.mimeType != reference.mimeType || asset.byteCount != reference.byteCount || asset.sha256 != reference.sha256) {
            return ConversationAttachmentAudioPreview(reference.id, reference.displayName, reference.byteCount, reference.mimeType, unavailableReason = "本地音频附件校验不一致")
        }
        return when (val result = privateStore.read(asset)) {
            is AttachmentReadResult.Content -> ConversationAttachmentAudioPreview(reference.id, reference.displayName, reference.byteCount, reference.mimeType, durationMillis = privateStore.audioDurationMillis(asset), bytes = result.bytes)
            is AttachmentReadResult.Rejected -> ConversationAttachmentAudioPreview(reference.id, reference.displayName, reference.byteCount, reference.mimeType, unavailableReason = "本地音频无法安全播放")
        }
    }

    /** Text is decoded as inert, bounded UTF-8. Markdown/JSON/CSV are never interpreted. */
    fun text(reference: ConversationAttachmentReference): ConversationAttachmentTextPreview {
        val asset = assets.findById(reference.id)
            ?: return ConversationAttachmentTextPreview(reference.id, reference.displayName, reference.mimeType, reference.byteCount, unavailableReason = "本地文本附件不可用")
        if (reference.mimeType !in setOf("text/plain", "text/markdown", "application/json", "text/csv") || asset.mimeType != reference.mimeType || asset.byteCount != reference.byteCount || asset.sha256 != reference.sha256) {
            return ConversationAttachmentTextPreview(reference.id, reference.displayName, reference.mimeType, reference.byteCount, unavailableReason = "本地文本附件校验不一致")
        }
        val bytes = when (val result = privateStore.read(asset)) {
            is AttachmentReadResult.Content -> result.bytes
            is AttachmentReadResult.Rejected -> return ConversationAttachmentTextPreview(reference.id, reference.displayName, reference.mimeType, reference.byteCount, unavailableReason = "本地文本无法安全读取")
        }
        val truncated = bytes.size > MAX_INERT_TEXT_PREVIEW_BYTES
        val bounded = bytes.copyOf(minOf(bytes.size, MAX_INERT_TEXT_PREVIEW_BYTES))
        val decoded = runCatching {
            java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bounded)).toString().removePrefix("\uFEFF")
        }.getOrNull() ?: return ConversationAttachmentTextPreview(reference.id, reference.displayName, reference.mimeType, reference.byteCount, unavailableReason = "文件不是可安全解码的 UTF-8 文本")
        return ConversationAttachmentTextPreview(reference.id, reference.displayName, reference.mimeType, reference.byteCount, text = decoded, truncated = truncated)
    }
}

const val MAX_INERT_TEXT_PREVIEW_BYTES = 128 * 1024

private fun AiTaskError.toConversationAttachmentReason() = when (this) {
    AiTaskError.AttachmentUnsupportedType -> "文件类型或内容标识不受支持。"
    AiTaskError.AttachmentTooLarge -> "单个附件不能超过 20 MB。"
    else -> "附件未能复制到本机私有空间，当前草稿未改变。"
}
