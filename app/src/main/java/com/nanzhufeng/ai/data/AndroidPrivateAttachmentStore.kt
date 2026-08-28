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

class AndroidPrivateAttachmentStore(context: Context) : PrivateAttachmentStore {
    private val appFilesDirectory = context.applicationContext.filesDir
    private val appCacheDirectory = context.applicationContext.cacheDir
    private val root = File(appFilesDirectory, ATTACHMENTS_ROOT)
    private val archiveRoot = File(appFilesDirectory, P6K_ARCHIVES_ROOT)
    private val archivePreviewRoot = File(appCacheDirectory, P6K_PREVIEW_CACHE_ROOT)

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
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = FileInputStream(file).use { input -> DigestInputStream(input, digest).use { it.readBytes() } }
            if (digest.digest().toHex() != attachment.sha256) {
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
        val file = verifiedFileFor(attachment) ?: return AttachmentOpenResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        if (!file.isFile || file.length() != attachment.byteCount || file.sha256() != attachment.sha256) {
            return AttachmentOpenResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        }
        return AttachmentOpenResult.Opened(attachment.byteCount) { FileInputStream(file) }
    }

    /** Decode a bounded thumbnail directly from the private file; full originals never enter Compose. */
    override fun thumbnail(attachment: AttachmentReference): AttachmentThumbnailResult {
        if (!attachment.isReadyPrivateCopy()) return AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentNotReady)
        val file = verifiedFileFor(attachment) ?: return AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        if (!file.isFile || file.length() != attachment.byteCount) return AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > MAX_SOURCE_PIXELS) {
                return AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentUnsupportedType)
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(width, height)
                inPreferredConfig = Bitmap.Config.RGB_565
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
    override fun pdfPage(attachment: AttachmentReference, pageNumber: Int): AttachmentPdfPageResult {
        if (attachment.mimeType != "application/pdf" || !attachment.isReadyPrivateCopy()) return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        val file = verifiedFileFor(attachment) ?: return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        if (!file.isFile || file.length() != attachment.byteCount || file.sha256() != attachment.sha256) return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val pageCount = renderer.pageCount
                    if (pageCount <= 0) return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
                    val index = (pageNumber - 1).coerceIn(0, pageCount - 1)
                    renderer.openPage(index).use { page ->
                        val scale = minOf(1f, MAX_PDF_PREVIEW_EDGE.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1))
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        if (width.toLong() * height > MAX_PDF_PREVIEW_PIXELS) return AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentTooLarge)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val output = java.io.ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, output)
                        bitmap.recycle()
                        val bytes = output.toByteArray()
                        if (bytes.size > MAX_THUMBNAIL_BYTES) AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentTooLarge)
                        else AttachmentPdfPageResult.Ready(AttachmentPdfPage(AttachmentThumbnail(bytes, width, height), index + 1, pageCount))
                    }
                }
            }
        } catch (_: Exception) {
            AttachmentPdfPageResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        }
    }

    override fun videoMetadata(attachment: AttachmentReference): AttachmentVideoMetadataResult {
        if (attachment.mimeType != "video/mp4" || !attachment.isReadyPrivateCopy()) return AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        val file = verifiedFileFor(attachment) ?: return AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
        if (!file.isFile || file.length() != attachment.byteCount || file.sha256() != attachment.sha256) return AttachmentVideoMetadataResult.Rejected(AiTaskError.AttachmentIntegrityMismatch)
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
        if (!file.isFile || file.length() != attachment.byteCount || file.sha256() != attachment.sha256) return null
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
        val file = safeFileFor(attachment.reference) ?: return false
        return !file.exists() || file.delete()
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
        if (cached.isFile && cached.length() == expectedBytes && cached.sha256() == expectedHash) return cached
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
        "text/plain", "text/markdown", "application/json", "text/csv" -> bytes.isNotEmpty() && bytes.none { it == 0.toByte() }
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
        "text/plain" -> ".txt"
        else -> ".bin"
    }

    private companion object {
        const val ATTACHMENTS_ROOT = "attachments/v1"
        const val P6K_ARCHIVES_ROOT = "p6k-zip-import/v1/archives"
        const val P6K_PREVIEW_CACHE_ROOT = "p6k-zip-attachment-previews/v1"
        const val MAX_ATTACHMENT_BYTES = 20L * 1024L * 1024L
        const val MAGIC_PREFIX_BYTES = 4096
        const val MAX_SOURCE_PIXELS = 40_000_000L
        const val MAX_THUMBNAIL_EDGE = 512
        const val MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024
        const val MAX_PDF_PREVIEW_EDGE = 1440
        const val MAX_PDF_PREVIEW_PIXELS = 4_000_000L
        const val MAX_VIDEO_DURATION_MILLIS = 4L * 60L * 60L * 1000L
    }
}

private class ImageTooLargeException : Exception()
private class AttachmentMagicMismatchException : Exception()
