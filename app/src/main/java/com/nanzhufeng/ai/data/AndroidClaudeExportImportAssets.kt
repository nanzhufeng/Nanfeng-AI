package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.CLAUDE_EXPORT_MAX_BYTES
import com.nanzhufeng.ai.domain.ClaudeExportParseFailure
import com.nanzhufeng.ai.domain.ClaudeImportAsset
import com.nanzhufeng.ai.domain.ClaudePrivateAssetStore
import com.nanzhufeng.ai.domain.ClaudePrivateCopyRequest
import com.nanzhufeng.ai.domain.ClaudePrivateCopyResult
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** The caller consumes the system URI before this isolated owner receives bytes. */
class AndroidClaudeExportPrivateAssetStore(context: Context) : ClaudePrivateAssetStore {
    private val root = File(context.filesDir, "claude-export-import-assets/v1")

    override fun copy(request: ClaudePrivateCopyRequest): ClaudePrivateCopyResult = runCatching {
        if (request.bytes.size.toLong() !in 1..CLAUDE_EXPORT_MAX_BYTES) return ClaudePrivateCopyResult.Failed(ClaudeExportParseFailure.TOO_LARGE)
        if (!root.exists() && !root.mkdirs()) return ClaudePrivateCopyResult.Failed()
        val safeName = request.displayName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "conversations.json" }
        val key = "claude-export-import-assets/v1/${request.taskId.value}/$safeName"
        val target = File(root.parentFile!!.parentFile!!, key)
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, ".${target.name}.part")
        FileOutputStream(part).use { output -> output.write(request.bytes); output.fd.sync() }
        if (!part.renameTo(target)) { part.delete(); return ClaudePrivateCopyResult.Failed() }
        ClaudePrivateCopyResult.Copied(ClaudeImportAsset(key, request.mimeType, safeName, request.bytes.size.toLong(), request.bytes.sha256()))
    }.getOrElse { ClaudePrivateCopyResult.Failed() }

    override fun read(storageKey: String): ByteArray? =
        if (storageKey.matches(Regex("claude-export-import-assets/v1/[A-Za-z0-9-]+/[A-Za-z0-9._-]+"))) {
            File(root.parentFile!!.parentFile!!, storageKey).takeIf(File::isFile)?.readBytes()
        } else null
}

private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
