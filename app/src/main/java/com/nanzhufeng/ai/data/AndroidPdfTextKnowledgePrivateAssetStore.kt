package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.PDF_TEXT_MAX_BYTES
import com.nanzhufeng.ai.domain.PdfTextImportAsset
import com.nanzhufeng.ai.domain.PdfTextImportFailure
import com.nanzhufeng.ai.domain.PdfTextPrivateAssetStore
import com.nanzhufeng.ai.domain.PdfTextPrivateCopyRequest
import com.nanzhufeng.ai.domain.PdfTextPrivateCopyResult
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** P4-N owns this private namespace. External URIs and source paths are intentionally never persisted. */
class AndroidPdfTextKnowledgePrivateAssetStore(context: Context) : PdfTextPrivateAssetStore {
    private val root = File(context.filesDir, "pdf-text-import-assets/v1")
    override fun copy(request: PdfTextPrivateCopyRequest): PdfTextPrivateCopyResult = runCatching {
        if (request.bytes.size.toLong() > PDF_TEXT_MAX_BYTES) return PdfTextPrivateCopyResult.Failed(PdfTextImportFailure.TOO_LARGE)
        if (!root.exists() && !root.mkdirs()) return PdfTextPrivateCopyResult.Failed(PdfTextImportFailure.PRIVATE_COPY_FAILED)
        val safe = request.displayName.substringAfterLast('/').replace(Regex(" \\(\\d+\\)$"), "").replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "document.pdf" }
        val key = "pdf-text-import-assets/v1/${request.taskId.value}/$safe"; val base = root.parentFile!!.parentFile!!; val target = File(base, key); target.parentFile?.mkdirs(); val part = File(target.parentFile, ".${target.name}.part")
        FileOutputStream(part).use { it.write(request.bytes); it.fd.sync() }
        if (!part.renameTo(target)) { part.delete(); return PdfTextPrivateCopyResult.Failed(PdfTextImportFailure.PRIVATE_COPY_FAILED) }
        PdfTextPrivateCopyResult.Copied(PdfTextImportAsset(key, request.mimeType, safe, request.bytes.size.toLong(), request.bytes.sha256()))
    }.getOrElse { PdfTextPrivateCopyResult.Failed(PdfTextImportFailure.PRIVATE_COPY_FAILED) }
    override fun read(storageKey: String): ByteArray? = if (storageKey.matches(Regex("pdf-text-import-assets/v1/[A-Za-z0-9-]+/[A-Za-z0-9._-]+"))) File(root.parentFile!!.parentFile!!, storageKey).takeIf(File::isFile)?.readBytes() else null
}
private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
