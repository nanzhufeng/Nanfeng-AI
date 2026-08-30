package com.nanzhufeng.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentImportRequest
import com.nanzhufeng.ai.domain.AttachmentImportResult
import com.nanzhufeng.ai.domain.AttachmentReadResult
import com.nanzhufeng.ai.domain.AttachmentOpenResult
import com.nanzhufeng.ai.domain.AttachmentArchiveEntry
import com.nanzhufeng.ai.domain.AttachmentArchiveIndex
import com.nanzhufeng.ai.domain.AttachmentArchiveIndexResult
import com.nanzhufeng.ai.domain.AttachmentArchiveEntryContent
import com.nanzhufeng.ai.domain.AttachmentArchiveEntryReadResult
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.AttachmentThumbnail
import com.nanzhufeng.ai.domain.AttachmentThumbnailResult
import com.nanzhufeng.ai.domain.AttachmentPdfPage
import com.nanzhufeng.ai.domain.AttachmentPdfPageResult
import com.nanzhufeng.ai.domain.AttachmentVideoPreview
import com.nanzhufeng.ai.domain.AttachmentVideoPreviewResult
import com.nanzhufeng.ai.domain.AttachmentVideoMetadata
import com.nanzhufeng.ai.domain.AttachmentVideoMetadataResult
import com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_MIME_TYPES
import com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_IMAGE_MIME_TYPES
import com.nanzhufeng.ai.domain.DOCX_MIME_TYPE
import com.nanzhufeng.ai.domain.PPTX_MIME_TYPE
import com.nanzhufeng.ai.domain.XLSX_MIME_TYPE
import com.nanzhufeng.ai.domain.PrivateAttachmentStore
import com.nanzhufeng.ai.domain.isReadyPrivateCopy
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.sqrt

internal data class PdfPreviewRenderSize(val width: Int, val height: Int)

private class PdfRendererSession(
    val reference: String,
    private val expectedBytes: Long,
    private val expectedSha256: String,
    private val file: File,
    private val verifiedLastModified: Long,
    private val descriptor: ParcelFileDescriptor,
    val renderer: PdfRenderer,
) {
    fun matches(attachment: AttachmentReference): Boolean =
        attachment.reference == reference &&
            attachment.byteCount == expectedBytes &&
            attachment.sha256 == expectedSha256 &&
            file.isFile &&
            file.length() == expectedBytes &&
            file.lastModified() == verifiedLastModified

    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

/** A private attachment that has completed a full digest check in this app process.
 *
 * The cache never makes the first read cheaper: callers still verify the complete file before
 * it is added. It only prevents later preview stages from reading the same immutable private
 * bytes again, and expires immediately when its file metadata changes. */
private data class VerifiedAttachmentFile(
    val expectedBytes: Long,
    val expectedHash: String,
    val absolutePath: String,
    val lastModified: Long,
)

/**
 * PdfRenderer reports page dimensions near 72 DPI. Rendering that intrinsic size and stretching it
 * to a phone screen makes text visibly soft, so preview pages are deliberately rasterized above the
 * reported size while remaining within explicit edge and decoded-bitmap memory limits.
 */
internal fun pdfPreviewRenderSize(
    pageWidth: Int,
    pageHeight: Int,
    maximumEdge: Int = 2_880,
    maximumPixels: Long = 6_000_000L,
): PdfPreviewRenderSize {
    val sourceWidth = pageWidth.coerceAtLeast(1)
    val sourceHeight = pageHeight.coerceAtLeast(1)
    val edgeScale = maximumEdge.toDouble() / maxOf(sourceWidth, sourceHeight)
    val pixelScale = sqrt(maximumPixels.toDouble() / (sourceWidth.toLong() * sourceHeight).toDouble())
    val scale = minOf(edgeScale, pixelScale)
    return PdfPreviewRenderSize(
        width = (sourceWidth * scale).toInt().coerceAtLeast(1),
        height = (sourceHeight * scale).toInt().coerceAtLeast(1),
    )
}

class AndroidPrivateAttachmentStore(context: Context) : PrivateAttachmentStore {
    private val appFilesDirectory = context.applicationContext.filesDir
    private val appCacheDirectory = context.applicationContext.cacheDir
    private val root = File(appFilesDirectory, ATTACHMENTS_ROOT)
    private val archiveRoot = File(appFilesDirectory, P6K_ARCHIVES_ROOT)
    private val archivePreviewRoot = File(appCacheDirectory, P6K_PREVIEW_CACHE_ROOT)
    /** First access remains SHA-256 checked. This bounded process-local cache avoids re-hashing
     * the same private file between search-card projection and the user's explicit open. */
    private val verifiedAttachmentFiles = LinkedHashMap<String, VerifiedAttachmentFile>(16, 0.75f, true)
    private val verifiedAttachmentFilesLock = Any()
    /** PdfRenderer is seekable and relatively expensive to reopen. Keep only the actively read
     * document, after one full SHA-256 verification, and invalidate it on any file metadata change. */
    private val pdfRendererLock = Any()
    private var pdfRendererSession: PdfRendererSession? = null

    override fun import(request: AttachmentImportRequest): AttachmentImportResult {
        if (request.mimeType !in CONVERSATION_ALLOWED_MIME_TYPES) return AttachmentImportResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        if (!root.exists() && !root.mkdirs()) return AttachmentImportResult.Rejected(AiTaskError.AttachmentImportFailed)

        val temporary = runCatching { File.createTempFile("import-", ".part", root) }.getOrNull()
            ?: return AttachmentImportResult.Rejected(AiTaskError.AttachmentImportFailed)
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val byteCount = FileOutputStream(temporary).use { output ->
                DigestOutputStream(output, digest).use { digestOutput -> request.input.copyAttachmentTo(digestOutput, request.mimeType) }
            }
            if (byteCount <= 0) return AttachmentImportResult.Rejected(AiTaskError.AttachmentImportFailed)

            val sha256 = digest.digest().toHex()
            val fileName = "$sha256${extensionFor(request.mimeType)}"
            val finalFile = File(root, fileName)
            if (finalFile.exists()) {
                if (finalFile.length() != byteCount || finalFile.sha256() != sha256) {
                    return AttachmentImportResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
                }
                temporary.delete()
            } else {
                Files.move(temporary.toPath(), finalFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
            AttachmentImportResult.Imported(
                AttachmentReference(
                    reference = "$ATTACHMENTS_ROOT/$fileName",
                    mimeType = request.mimeType,
                    displayName = request.displayName,
                    id = AttachmentId.new(),
                    byteCount = byteCount,
                    sha256 = sha256,
                ),
            )
        } catch (_: ImageTooLargeException) {
            AttachmentImportResult.Rejected(AiTaskError.AttachmentTooLarge)
        } catch (_: AttachmentMagicMismatchException) {
            AttachmentImportResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        } catch (_: Exception) {
            AttachmentImportResult.Rejected(AiTaskError.AttachmentImportFailed)
        } finally {
            if (temporary.exists()) temporary.delete()
            request.input.close()
        }
    }

    override fun read(attachment: AttachmentReference): AttachmentReadResult {
        if (!attachment.isReadyPrivateCopy()) return AttachmentReadResult.Rejected(AiTaskError.AttachmentNotReady)
        val file = verifiedFileFor(attachment) ?: return AttachmentReadResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        if (!file.isFile || file.length() != attachment.byteCount) return AttachmentReadResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        return try {
            val alreadyVerified = isVerifiedAttachmentFile(attachment, file)
            val bytes = if (alreadyVerified) {
                FileInputStream(file).use { it.readBytes() }
            } else {
                val digest = MessageDigest.getInstance("SHA-256")
                val read = FileInputStream(file).use { input -> DigestInputStream(input, digest).use { it.readBytes() } }
                if (digest.digest().toHex() != attachment.sha256) return AttachmentReadResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
                markVerifiedAttachmentFile(attachment, file)
                read
            }
            if (bytes.size.toLong() != attachment.byteCount) {
                AttachmentReadResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
            } else {
                AttachmentReadResult.Content(bytes)
            }
        } catch (_: Exception) {
            AttachmentReadResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        }
    }

    /** Recheck the immutable private copy before handing an opaque stream to the egress owner. */
    override fun openVerified(attachment: AttachmentReference): AttachmentOpenResult {
        if (!attachment.isReadyPrivateCopy()) return AttachmentOpenResult.Rejected(AiTaskError.AttachmentNotReady)
        val expectedBytes = attachment.byteCount ?: return AttachmentOpenResult.Rejected(AiTaskError.AttachmentNotReady)
        val file = verifiedFileFor(attachment) ?: return AttachmentOpenResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        if (!file.matchesVerifiedAttachment(attachment)) {
            return AttachmentOpenResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        }
        return AttachmentOpenResult.Opened(expectedBytes) { FileInputStream(file) }
    }

    override fun archiveIndex(
        attachment: AttachmentReference,
        containerPath: List<String>,
        directoryPath: List<String>,
    ): AttachmentArchiveIndexResult {
        if (!attachment.isReadyPrivateCopy() || !isZipAttachment(attachment.mimeType, attachment.displayName)) {
            return AttachmentArchiveIndexResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        }
        val file = verifiedFileFor(attachment)
            ?: return AttachmentArchiveIndexResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        if (!file.matchesVerifiedAttachment(attachment)) {
            return AttachmentArchiveIndexResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        }
        return try {
            if (containerPath.isEmpty()) indexRootArchive(file, directoryPath)
            else {
                val nested = nestedArchiveBytes(file, containerPath)
                    ?: return AttachmentArchiveIndexResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
                indexArchiveBytes(nested, directoryPath)
            }
        } catch (_: Exception) {
            AttachmentArchiveIndexResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        }
    }

    override fun archiveEntry(
        attachment: AttachmentReference,
        containerPath: List<String>,
        entryPath: String,
    ): AttachmentArchiveEntryReadResult {
        if (!attachment.isReadyPrivateCopy() || !isZipAttachment(attachment.mimeType, attachment.displayName)) {
            return AttachmentArchiveEntryReadResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        }
        val file = verifiedFileFor(attachment)
            ?: return AttachmentArchiveEntryReadResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        if (!file.matchesVerifiedAttachment(attachment)) {
            return AttachmentArchiveEntryReadResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        }
        val safePath = archiveDisplayPath(entryPath)?.takeIf { it == entryPath }
            ?: return AttachmentArchiveEntryReadResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        val mimeType = archiveEntryMimeType(safePath)
            ?: return AttachmentArchiveEntryReadResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        val bytes = try {
            if (containerPath.isEmpty()) readRootArchiveEntry(file, safePath, mimeType)
            else nestedArchiveBytes(file, containerPath)?.let { readArchiveByteEntry(it, safePath, mimeType) }
        } catch (_: Exception) {
            null
        } ?: return AttachmentArchiveEntryReadResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        return AttachmentArchiveEntryReadResult.Content(AttachmentArchiveEntryContent(safePath, mimeType, bytes))
    }

    override fun archivePdfPage(
        attachment: AttachmentReference,
        containerPath: List<String>,
        entryPath: String,
        pageNumber: Int,
    ): AttachmentPdfPageResult {
        val content = archiveEntry(attachment, containerPath, entryPath) as? AttachmentArchiveEntryReadResult.Content
            ?: return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        if (content.entry.mimeType != "application/pdf") return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        if (!archivePreviewRoot.exists() && !archivePreviewRoot.mkdirs()) return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentImportFailed)
        val digest = MessageDigest.getInstance("SHA-256").digest(content.entry.bytes).toHex()
        val file = File(archivePreviewRoot, "$digest.pdf")
        if (!file.isFile || file.length() != content.entry.bytes.size.toLong()) {
            val temporary = runCatching { File.createTempFile("archive-pdf-", ".part", archivePreviewRoot) }.getOrNull()
                ?: return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentImportFailed)
            try {
                FileOutputStream(temporary).use { it.write(content.entry.bytes) }
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentImportFailed)
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> renderPdfPage(renderer, pageNumber) }
            }
        } catch (_: Exception) {
            AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        }
    }

    /** Decode a bounded thumbnail directly from the private file; full originals never enter Compose. */
    override fun thumbnail(attachment: AttachmentReference): AttachmentThumbnailResult {
        if (!attachment.isReadyPrivateCopy()) return AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentNotReady)
        val file = verifiedFileFor(attachment) ?: return AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        if (!file.matchesVerifiedAttachment(attachment)) return AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0 || width > MAX_PREVIEW_SOURCE_EDGE || height > MAX_PREVIEW_SOURCE_EDGE) {
                return AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentUnsupportedType)
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(width, height)
                // Catalogue thumbnails are already bounded by inSampleSize. ARGB_8888 avoids
                // the visible colour banding/blocking that RGB_565 produced on long screenshots.
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                ?: return AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
            val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_THUMBNAIL_EDGE) {
                val ratio = MAX_THUMBNAIL_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            } else bitmap
            val output = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 90, output)
            val bytes = output.toByteArray()
            val result = if (bytes.size <= MAX_THUMBNAIL_BYTES) {
                AttachmentThumbnailResult.Ready(AttachmentThumbnail(bytes, scaled.width, scaled.height))
            } else AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentImportFailed)
            scaled.recycle()
            result
        } catch (_: Exception) {
            AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        }
    }

    /** Android's PDFRenderer has no JavaScript, form action or external-resource execution surface. */
    override fun pdfPage(attachment: AttachmentReference, pageNumber: Int): AttachmentPdfPageResult = synchronized(pdfRendererLock) {
        if (attachment.mimeType != "application/pdf" || !attachment.isReadyPrivateCopy()) return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        val session = pdfRendererSessionFor(attachment)
            ?: return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        return try {
            renderPdfPage(session.renderer, pageNumber)
        } catch (_: Exception) {
            closePdfRendererSession()
            AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        }
    }

    override fun videoMetadata(attachment: AttachmentReference): AttachmentVideoMetadataResult {
        if (attachment.mimeType != "video/mp4" || !attachment.isReadyPrivateCopy()) return AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        val file = verifiedFileFor(attachment) ?: return AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        if (!file.matchesVerifiedAttachment(attachment)) return AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?.takeIf { it in 1..MAX_VIDEO_DURATION_MILLIS } ?: return AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
                val poster = frame.toBoundedPoster() ?: return AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentTooLarge)
                AttachmentVideoMetadataResult.Ready(AttachmentVideoMetadata(poster, duration))
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {
            AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        }
    }

    override fun videoPreview(attachment: AttachmentReference): AttachmentVideoPreviewResult = when (val metadata = videoMetadata(attachment)) {
        is AttachmentVideoMetadataResult.Ready -> {
            if (P6KZipArchiveAssetStorage.parse(attachment.reference) != null) {
                when (val opened = openVerified(attachment)) {
                    is AttachmentOpenResult.Opened -> AttachmentVideoPreviewResult.Ready(AttachmentVideoPreview(metadata.metadata.poster, metadata.metadata.durationMillis, open = opened.open))
                    is AttachmentOpenResult.Rejected -> AttachmentVideoPreviewResult.Rejected(opened.error)
                }
            } else {
                val file = verifiedFileFor(attachment)
                    ?: return AttachmentVideoPreviewResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
                val bytes = runCatching { FileInputStream(file).use { it.readBytes() } }.getOrNull()
                    ?: return AttachmentVideoPreviewResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
                AttachmentVideoPreviewResult.Ready(AttachmentVideoPreview(metadata.metadata.poster, metadata.metadata.durationMillis, bytes = bytes))
            }
        }
        is AttachmentVideoMetadataResult.Rejected -> AttachmentVideoPreviewResult.Rejected(metadata.error)
    }

    override fun audioDurationMillis(attachment: AttachmentReference): Long? {
        if (attachment.mimeType !in setOf("audio/mpeg", "audio/wav", "audio/mp4") || !attachment.isReadyPrivateCopy()) return null
        val file = verifiedFileFor(attachment) ?: return null
        if (!file.matchesVerifiedAttachment(attachment)) return null
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L }
            } finally {
                // MediaMetadataRetriever only implements AutoCloseable from API 29; minSdk remains 26.
                retriever.release()
            }
        }.getOrNull()
    }

    override fun deletePrivateCopy(attachment: AttachmentReference): Boolean {
        if (P6KZipArchiveAssetStorage.parse(attachment.reference) != null) return true
        synchronized(pdfRendererLock) {
            if (pdfRendererSession?.reference == attachment.reference) closePdfRendererSession()
        }
        val file = safeFileFor(attachment.reference) ?: return false
        return !file.exists() || file.delete()
    }

    /** Reuses the active descriptor only while owner identity, expected digest and file metadata
     * still match. The first open always performs the complete digest verification. */
    private fun pdfRendererSessionFor(attachment: AttachmentReference): PdfRendererSession? {
        val active = pdfRendererSession
        if (active != null && active.matches(attachment)) return active
        closePdfRendererSession()

        val expectedBytes = attachment.byteCount ?: return null
        val expectedHash = attachment.sha256 ?: return null
        val file = verifiedFileFor(attachment) ?: return null
        if (!file.matchesVerifiedAttachment(attachment)) return null
        val descriptor = runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull() ?: return null
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (_: Exception) {
            descriptor.close()
            return null
        }
        return PdfRendererSession(
            reference = attachment.reference,
            expectedBytes = expectedBytes,
            expectedSha256 = expectedHash,
            file = file,
            verifiedLastModified = file.lastModified(),
            descriptor = descriptor,
            renderer = renderer,
        ).also { pdfRendererSession = it }
    }

    private fun closePdfRendererSession() {
        pdfRendererSession?.close()
        pdfRendererSession = null
    }

    private fun renderPdfPage(renderer: PdfRenderer, pageNumber: Int): AttachmentPdfPageResult {
        val pageCount = renderer.pageCount
        if (pageCount <= 0) return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        val index = (pageNumber - 1).coerceIn(0, pageCount - 1)
        return renderer.openPage(index).use { page ->
            val renderSize = pdfPreviewRenderSize(page.width, page.height, MAX_PDF_PREVIEW_EDGE, MAX_PDF_PREVIEW_PIXELS)
            val width = renderSize.width
            val height = renderSize.height
            if (width.toLong() * height > MAX_PDF_PREVIEW_PIXELS) return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentTooLarge)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val output = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, output)
            bitmap.recycle()
            val bytes = output.toByteArray()
            if (bytes.size > MAX_PDF_PREVIEW_BYTES) AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentTooLarge)
            else AttachmentPdfPageResult.Ready(AttachmentPdfPage(AttachmentThumbnail(bytes, width, height), index + 1, pageCount))
        }
    }

    private fun indexRootArchive(file: File, directoryPath: List<String>): AttachmentArchiveIndexResult = java.util.zip.ZipFile(file).use { zip ->
        fun index(path: List<String>) = archiveDirectoryIndex(path) { add ->
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                add(entry.name, entry.isDirectory, entry.size.takeIf { it >= 0L })
            }
        }
        val initial = index(directoryPath)
        // Export tools commonly wrap every item in one presentation folder. Do not make that
        // wrapper look like a ZIP containing no files: show its direct children on the first
        // screen, while retaining their full paths so folder and file taps remain exact.
        val singleRoot = (initial as? AttachmentArchiveIndexResult.Ready)
            ?.index
            ?.entries
            ?.singleOrNull()
            ?.takeIf { directoryPath.isEmpty() && it.isDirectory }
        if (singleRoot == null) initial else index(listOf(singleRoot.path))
    }

    private fun indexArchiveBytes(bytes: ByteArray, directoryPath: List<String>): AttachmentArchiveIndexResult =
        archiveDirectoryIndex(directoryPath) { add ->
            java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    add(entry.name, entry.isDirectory, entry.size.takeIf { it >= 0L })
                    zip.closeEntry()
                }
            }
        }

    /**
     * A ZIP can omit explicit directory entries.  Derive immediate safe children while scanning,
     * so every reachable file remains browsable without extracting an archive tree to disk.
     */
    private fun archiveDirectoryIndex(
        directoryPath: List<String>,
        scan: ((rawPath: String, isDirectory: Boolean, byteCount: Long?) -> Unit) -> Unit,
    ): AttachmentArchiveIndexResult {
        val safeDirectory = archiveDirectorySegments(directoryPath)
            ?: return AttachmentArchiveIndexResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        val entries = linkedMapOf<String, AttachmentArchiveEntry>()
        var totalEntryCount = 0
        var truncated = false
        try {
            scan { rawPath, isDirectory, byteCount ->
                totalEntryCount += 1
                if (totalEntryCount > MAX_ARCHIVE_SCANNED_ENTRIES) throw ArchiveIndexTooLargeException()
                val safePath = archiveDisplayPath(rawPath)
                if (safePath != null) {
                    val segments = safePath.split('/')
                    if (segments.size > safeDirectory.size && segments.take(safeDirectory.size) == safeDirectory) {
                        val childSegments = segments.take(safeDirectory.size + 1)
                        val childPath = childSegments.joinToString("/")
                        val childIsDirectory = isDirectory || segments.size > childSegments.size
                        val child = AttachmentArchiveEntry(
                            path = childPath,
                            byteCount = if (childIsDirectory) null else byteCount,
                            isDirectory = childIsDirectory,
                            mimeType = if (childIsDirectory) null else archiveEntryMimeType(childPath),
                        )
                        val existing = entries[childPath]
                        if (existing == null) {
                            if (entries.size < MAX_ARCHIVE_PREVIEW_ENTRIES) entries[childPath] = child
                            else truncated = true
                        } else if (child.isDirectory && !existing.isDirectory) {
                            entries[childPath] = child
                        }
                    }
                }
            }
        } catch (_: ArchiveIndexTooLargeException) {
            return AttachmentArchiveIndexResult.Rejected(AiTaskError.AttachmentTooLarge)
        }
        return AttachmentArchiveIndexResult.Ready(
            AttachmentArchiveIndex(
                entries = entries.values.sortedWith(compareByDescending<AttachmentArchiveEntry> { it.isDirectory }.thenBy { it.path.lowercase() }),
                totalEntryCount = totalEntryCount,
                truncated = truncated,
            ),
        )
    }

    private fun nestedArchiveBytes(file: File, containerPath: List<String>): ByteArray? {
        if (containerPath.isEmpty() || containerPath.size > MAX_NESTED_ARCHIVE_DEPTH) return null
        var current: ByteArray? = null
        containerPath.forEachIndexed { index, rawPath ->
            val path = archiveDisplayPath(rawPath)?.takeIf { it == rawPath && archiveEntryMimeType(it) == "application/zip" } ?: return null
            current = if (index == 0) readRootArchiveEntry(file, path, "application/zip")
            else readArchiveByteEntry(current ?: return null, path, "application/zip")
            if (current == null) return null
        }
        return current
    }

    private fun readRootArchiveEntry(file: File, requestedPath: String, mimeType: String): ByteArray? = java.util.zip.ZipFile(file).use { zip ->
        var match: java.util.zip.ZipEntry? = null
        var scanned = 0
        val iterator = zip.entries()
        while (iterator.hasMoreElements()) {
            val entry = iterator.nextElement()
            scanned += 1
            if (scanned > MAX_ARCHIVE_SCANNED_ENTRIES) return null
            if (archiveDisplayPath(entry.name) == requestedPath) {
                if (match != null) return null
                match = entry
            }
        }
        val entry = match ?: return null
        if (!safeReadableArchiveEntry(entry.size, entry.compressedSize, entry.isDirectory)) return null
        val bytes = zip.getInputStream(entry).use { it.readBoundedArchiveEntry(entry.size) } ?: return null
        bytes.takeIf { magicMatches(mimeType, it.take(MAGIC_PREFIX_BYTES).toByteArray()) }
    }

    private fun readArchiveByteEntry(archiveBytes: ByteArray, requestedPath: String, mimeType: String): ByteArray? {
        var found: ByteArray? = null
        var scanned = 0
        java.util.zip.ZipInputStream(archiveBytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                scanned += 1
                if (scanned > MAX_ARCHIVE_SCANNED_ENTRIES) return null
                if (archiveDisplayPath(entry.name) == requestedPath) {
                    if (found != null || !safeReadableArchiveEntry(entry.size, entry.compressedSize, entry.isDirectory)) return null
                    val decoded = zip.readBoundedArchiveEntry(entry.size) ?: return null
                    if (entry.compressedSize > 0L && decoded.size.toLong() > entry.compressedSize * MAX_ARCHIVE_COMPRESSION_RATIO) return null
                    found = decoded
                }
                zip.closeEntry()
            }
        }
        return found?.takeIf { magicMatches(mimeType, it.take(MAGIC_PREFIX_BYTES).toByteArray()) }
    }

    private fun safeReadableArchiveEntry(size: Long, compressedSize: Long, directory: Boolean): Boolean {
        if (directory || size == 0L || size > MAX_ARCHIVE_ENTRY_BYTES) return false
        return size < 0L || compressedSize < 0L || (compressedSize > 0L && size <= compressedSize * MAX_ARCHIVE_COMPRESSION_RATIO)
    }

    private fun java.io.InputStream.readBoundedArchiveEntry(expectedSize: Long): ByteArray? {
        val initialSize = expectedSize.takeIf { it > 0L }?.let { minOf(it, 64L * 1024L).toInt() } ?: DEFAULT_BUFFER_SIZE
        val output = java.io.ByteArrayOutputStream(initialSize)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_ARCHIVE_ENTRY_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray().takeIf { total > 0L && (expectedSize < 0L || total == expectedSize) }
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_THUMBNAIL_EDGE * 2 || height / sample > MAX_THUMBNAIL_EDGE * 2) sample *= 2
        return sample
    }

    private fun Bitmap.toBoundedPoster(): AttachmentThumbnail? {
        val scaled = if (maxOf(width, height) > MAX_THUMBNAIL_EDGE) {
            val ratio = MAX_THUMBNAIL_EDGE.toFloat() / maxOf(width, height)
            Bitmap.createScaledBitmap(this, (width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1), true)
        } else this
        return try {
            val output = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 90, output)
            output.toByteArray().takeIf { it.size <= MAX_THUMBNAIL_BYTES }?.let { AttachmentThumbnail(it, scaled.width, scaled.height) }
        } finally {
            if (scaled !== this) scaled.recycle()
            recycle()
        }
    }

    private fun safeFileFor(storageKey: String): File? {
        if (!storageKey.startsWith("$ATTACHMENTS_ROOT/")) return null
        val resolved = File(appFilesDirectory, storageKey)
        return resolved.takeIf { candidate -> candidate.canonicalPath.startsWith(root.canonicalPath + File.separator) }
    }

    /** Archive entries remain single-copy in the retained import package. Android decoders that
     * require a seekable file receive a hash-verified cache materialization, never the ZIP path. */
    private fun verifiedFileFor(attachment: AttachmentReference): File? {
        safeFileFor(attachment.reference)?.let { return it }
        val location = P6KZipArchiveAssetStorage.parse(attachment.reference) ?: return null
        val archive = File(archiveRoot, "${location.taskId}.zip")
        if (!archive.isFile) return null
        if (!archivePreviewRoot.exists() && !archivePreviewRoot.mkdirs()) return null
        val expectedBytes = attachment.byteCount ?: return null
        val expectedHash = attachment.sha256 ?: return null
        val cached = File(archivePreviewRoot, "$expectedHash${extensionFor(attachment.mimeType)}")
        if (cached.isFile && cached.length() == expectedBytes) {
            if (isVerifiedAttachmentFile(attachment, cached) || cached.sha256() == expectedHash) {
                markVerifiedAttachmentFile(attachment, cached)
                return cached
            }
        }
        val temporary = runCatching { File.createTempFile("archive-", ".part", archivePreviewRoot) }.getOrNull() ?: return null
        return try {
            java.util.zip.ZipFile(archive).use { zip ->
                val entry = zip.getEntry(location.entryName) ?: return null
                if (entry.isDirectory || entry.name != location.entryName || entry.size != expectedBytes) return null
                val digest = MessageDigest.getInstance("SHA-256")
                val copied = zip.getInputStream(entry).use { input ->
                    FileOutputStream(temporary).use { output ->
                        DigestOutputStream(output, digest).use { digestOutput -> input.copyTo(digestOutput) }
                    }
                }
                if (copied != expectedBytes || digest.digest().toHex() != expectedHash) return null
            }
            Files.move(temporary.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING)
            markVerifiedAttachmentFile(attachment, cached)
            cached
        } catch (_: Exception) {
            null
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun File.sha256(): String = FileInputStream(this).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(input, digest).use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (stream.read(buffer) >= 0) Unit
        }
        digest.digest().toHex()
    }

    private fun File.matchesVerifiedAttachment(attachment: AttachmentReference): Boolean {
        if (!isFile || length() != attachment.byteCount) return false
        if (isVerifiedAttachmentFile(attachment, this)) return true
        if (sha256() != attachment.sha256) return false
        markVerifiedAttachmentFile(attachment, this)
        return true
    }

    private fun isVerifiedAttachmentFile(attachment: AttachmentReference, file: File): Boolean = synchronized(verifiedAttachmentFilesLock) {
        val verified = verifiedAttachmentFiles[attachment.reference] ?: return@synchronized false
        verified.expectedBytes == attachment.byteCount &&
            verified.expectedHash == attachment.sha256 &&
            verified.absolutePath == file.absolutePath &&
            verified.lastModified == file.lastModified() &&
            file.isFile && file.length() == attachment.byteCount
    }

    private fun markVerifiedAttachmentFile(attachment: AttachmentReference, file: File) = synchronized(verifiedAttachmentFilesLock) {
        val expectedBytes = attachment.byteCount ?: return@synchronized
        val expectedHash = attachment.sha256 ?: return@synchronized
        verifiedAttachmentFiles[attachment.reference] = VerifiedAttachmentFile(
            expectedBytes = expectedBytes,
            expectedHash = expectedHash,
            absolutePath = file.absolutePath,
            lastModified = file.lastModified(),
        )
        while (verifiedAttachmentFiles.size > 24) {
            val oldestKey = verifiedAttachmentFiles.entries.iterator().next().key
            verifiedAttachmentFiles.remove(oldestKey)
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun java.io.InputStream.copyAttachmentTo(output: java.io.OutputStream, mimeType: String): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val prefix = java.io.ByteArrayOutputStream(MAGIC_PREFIX_BYTES)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_ATTACHMENT_BYTES) throw ImageTooLargeException()
            if (prefix.size() < MAGIC_PREFIX_BYTES) prefix.write(buffer, 0, minOf(count, MAGIC_PREFIX_BYTES - prefix.size()))
            output.write(buffer, 0, count)
        }
        if (!magicMatches(mimeType, prefix.toByteArray())) throw AttachmentMagicMismatchException()
        return total
    }

    private fun magicMatches(mimeType: String, bytes: ByteArray): Boolean = when (mimeType) {
        "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        "image/png" -> bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        "image/webp" -> bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        "video/mp4" -> bytes.size >= 12 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp"
        "audio/mpeg" -> bytes.size >= 3 && (String(bytes, 0, 3, Charsets.US_ASCII) == "ID3" || (bytes[0].toInt() and 0xFF) == 0xFF)
        "audio/wav" -> bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
        "audio/mp4" -> bytes.size >= 16 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" && String(bytes, 8, 4, Charsets.US_ASCII).contains("M4A")
        "application/pdf" -> bytes.size >= 5 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-"
        "text/plain", "text/markdown", "application/json", "text/csv", "application/xml", "text/xml", "application/x-yaml", "text/yaml", "text/html" -> bytes.isNotEmpty() && bytes.none { it == 0.toByte() }
        DOCX_MIME_TYPE, XLSX_MIME_TYPE, PPTX_MIME_TYPE, "application/zip" -> bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            ((bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) ||
                (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()) ||
                (bytes[2] == 7.toByte() && bytes[3] == 8.toByte()))
        else -> false
    }

    private fun extensionFor(mimeType: String): String = when (mimeType) {
        "image/jpeg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "video/mp4" -> ".mp4"
        "audio/mpeg" -> ".mp3"
        "audio/wav" -> ".wav"
        "audio/mp4" -> ".m4a"
        "application/pdf" -> ".pdf"
        "text/markdown" -> ".md"
        "application/json" -> ".json"
        "text/csv" -> ".csv"
        "application/xml", "text/xml" -> ".xml"
        "application/x-yaml", "text/yaml" -> ".yaml"
        "text/html" -> ".html"
        "text/plain" -> ".txt"
        DOCX_MIME_TYPE -> ".docx"
        XLSX_MIME_TYPE -> ".xlsx"
        PPTX_MIME_TYPE -> ".pptx"
        "application/zip" -> ".zip"
        else -> ".bin"
    }

    private companion object {
        const val ATTACHMENTS_ROOT = "attachments/v1"
        const val P6K_ARCHIVES_ROOT = "p6k-zip-import/v1/archives"
        const val P6K_PREVIEW_CACHE_ROOT = "p6k-zip-attachment-previews/v1"
        const val MAX_ATTACHMENT_BYTES = 20L * 1024L * 1024L
        const val MAGIC_PREFIX_BYTES = 4096
        const val MAX_PREVIEW_SOURCE_EDGE = 200_000
        const val MAX_THUMBNAIL_EDGE = 512
        const val MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024
        const val MAX_PDF_PREVIEW_EDGE = 2_880
        const val MAX_PDF_PREVIEW_PIXELS = 6_000_000L
        const val MAX_PDF_PREVIEW_BYTES = 8 * 1024 * 1024
        const val MAX_ARCHIVE_PREVIEW_ENTRIES = 2_000
        const val MAX_ARCHIVE_SCANNED_ENTRIES = 10_000
        const val MAX_NESTED_ARCHIVE_DEPTH = 5
        const val MAX_ARCHIVE_ENTRY_BYTES = 20L * 1024L * 1024L
        const val MAX_ARCHIVE_COMPRESSION_RATIO = 200L
        const val MAX_VIDEO_DURATION_MILLIS = 4L * 60L * 60L * 1000L
    }
}

private fun isZipAttachment(mimeType: String, displayName: String?): Boolean =
    mimeType in setOf("application/zip", "application/x-zip-compressed") ||
        (mimeType == "application/octet-stream" && normalizedFileExtension(displayName) == "zip")

private fun archiveDisplayPath(raw: String): String? {
    if (raw.any(Char::isISOControl)) return null
    val normalized = raw.replace('\\', '/')
    if (normalized.startsWith('/') || normalized.length > 500) return null
    val segments = normalized.trimEnd('/').split('/')
    if (segments.any { it.isBlank() || it == "." || it == ".." || it.contains(':') }) return null
    return segments.joinToString("/").takeIf(String::isNotBlank)
}

/** Rejects anything that could turn a folder tap into an unchecked archive path. */
private fun archiveDirectorySegments(path: List<String>): List<String>? = path.takeIf { it.size <= 100 }?.takeIf { segments ->
    segments.all { segment ->
        archiveDisplayPath(segment) == segment && !segment.contains('/')
    }
}

private fun archiveEntryMimeType(path: String): String? = when (normalizedFileExtension(path)) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "m4a" -> "audio/mp4"
    "pdf" -> "application/pdf"
    "md", "markdown" -> "text/markdown"
    "txt" -> "text/plain"
    "json" -> "application/json"
    "csv" -> "text/csv"
    "xml" -> "application/xml"
    "yaml", "yml" -> "application/x-yaml"
    "html", "htm" -> "text/html"
    "log", "ini", "cfg", "conf", "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "css", "sql", "sh" -> "text/plain"
    "docx" -> DOCX_MIME_TYPE
    "xlsx" -> XLSX_MIME_TYPE
    "pptx" -> PPTX_MIME_TYPE
    "zip" -> "application/zip"
    else -> null
}

private fun normalizedFileExtension(displayName: String?): String = displayName.orEmpty()
    .trim()
    .replace(Regex("[\u3002\uFF0E]\\s*(?=[A-Za-z0-9]+$)"), ".")
    .substringAfterLast('.', "")
    .trim()
    .lowercase()

private class ImageTooLargeException : Exception()
private class AttachmentMagicMismatchException : Exception()
private class ArchiveIndexTooLargeException : Exception()
