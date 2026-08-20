package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.MarkdownImportAsset
import com.nanzhufeng.ai.domain.MarkdownImportFailure
import com.nanzhufeng.ai.domain.MarkdownPrivateAssetStore
import com.nanzhufeng.ai.domain.MarkdownPrivateCopyRequest
import com.nanzhufeng.ai.domain.MarkdownPrivateCopyResult
import com.nanzhufeng.ai.domain.MemoryDomain
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Stores the selected Markdown in app-private task assets. No URI, permission token or absolute path escapes. */
class AndroidMarkdownPrivateAssetStore(context: Context) : MarkdownPrivateAssetStore {
    private val root = File(context.filesDir, "markdown-import-assets/v1")
    override fun copy(request: MarkdownPrivateCopyRequest): MarkdownPrivateCopyResult = runCatching {
        if (request.bytes.size.toLong() > 512 * 1024L) return MarkdownPrivateCopyResult.Failed(MarkdownImportFailure.TOO_LARGE)
        if (!root.exists() && !root.mkdirs()) return MarkdownPrivateCopyResult.Failed(MarkdownImportFailure.PRIVATE_COPY_FAILED)
        val safeName = request.displayName.substringAfterLast('/').replace(Regex(" \\(\\d+\\)$"), "").replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "import.md" }
        val storageKey = "markdown-import-assets/v1/${request.taskId.value}/$safeName"
        val target = File(contextRoot(), storageKey)
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, ".${target.name}.part")
        FileOutputStream(part).use { output -> output.write(request.bytes); output.fd.sync() }
        if (!part.renameTo(target)) { part.delete(); return MarkdownPrivateCopyResult.Failed(MarkdownImportFailure.PRIVATE_COPY_FAILED) }
        MarkdownPrivateCopyResult.Copied(MarkdownImportAsset(storageKey, request.mimeType, safeName, request.bytes.size.toLong(), request.bytes.sha256()))
    }.getOrElse { MarkdownPrivateCopyResult.Failed(MarkdownImportFailure.PRIVATE_COPY_FAILED) }
    override fun read(storageKey: String): ByteArray? {
        if (!storageKey.matches(Regex("markdown-import-assets/v1/[A-Za-z0-9-]+/[A-Za-z0-9._-]+"))) return null
        return File(contextRoot(), storageKey).takeIf(File::isFile)?.readBytes()
    }
    private fun contextRoot(): File = root.parentFile!!.parentFile!!
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
