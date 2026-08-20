package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.NANFENG_KNOWLEDGE_EXPORT_MAX_BYTES
import com.nanzhufeng.ai.domain.NanfengKnowledgeExportParseFailure
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportAsset
import com.nanzhufeng.ai.domain.NanfengKnowledgePrivateAssetStore
import com.nanzhufeng.ai.domain.NanfengKnowledgePrivateCopyRequest
import com.nanzhufeng.ai.domain.NanfengKnowledgePrivateCopyResult
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** P6-J receives bytes only after OpenDocument has consumed the transient system URI. */
class AndroidNanfengKnowledgeExportPrivateAssetStore(context: Context) : NanfengKnowledgePrivateAssetStore {
    private val root = File(context.filesDir, "nanfeng-knowledge-export-import-assets/v1")

    override fun copy(request: NanfengKnowledgePrivateCopyRequest): NanfengKnowledgePrivateCopyResult = runCatching {
        if (request.bytes.size.toLong() !in 1..NANFENG_KNOWLEDGE_EXPORT_MAX_BYTES) return NanfengKnowledgePrivateCopyResult.Failed(NanfengKnowledgeExportParseFailure.TOO_LARGE)
        if (!root.exists() && !root.mkdirs()) return NanfengKnowledgePrivateCopyResult.Failed()
        val safeName = request.displayName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "knowledge-export.json" }
        if (!safeName.endsWith(".json", ignoreCase = true)) return NanfengKnowledgePrivateCopyResult.Failed(NanfengKnowledgeExportParseFailure.INVALID_RECORD)
        val key = "nanfeng-knowledge-export-import-assets/v1/${request.taskId.value}/$safeName"
        val target = File(root.parentFile!!.parentFile!!, key); target.parentFile?.mkdirs()
        val part = File(target.parentFile, ".${target.name}.part")
        FileOutputStream(part).use { output -> output.write(request.bytes); output.fd.sync() }
        if (!part.renameTo(target)) { part.delete(); return NanfengKnowledgePrivateCopyResult.Failed() }
        NanfengKnowledgePrivateCopyResult.Copied(NanfengKnowledgeImportAsset(key, request.mimeType, safeName, request.bytes.size.toLong(), request.bytes.p6jSha256()))
    }.getOrElse { NanfengKnowledgePrivateCopyResult.Failed() }

    override fun read(storageKey: String): ByteArray? =
        if (storageKey.matches(Regex("nanfeng-knowledge-export-import-assets/v1/[A-Za-z0-9-]+/[A-Za-z0-9._-]+\\.json"))) File(root.parentFile!!.parentFile!!, storageKey).takeIf(File::isFile)?.readBytes() else null
}

private fun ByteArray.p6jSha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
