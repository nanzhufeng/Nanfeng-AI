package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.ChatGptImportAsset
import com.nanzhufeng.ai.domain.ChatGptImportFailure
import com.nanzhufeng.ai.domain.ChatGptPrivateAssetStore
import com.nanzhufeng.ai.domain.ChatGptPrivateCopyRequest
import com.nanzhufeng.ai.domain.ChatGptPrivateCopyResult
import com.nanzhufeng.ai.domain.CHATGPT_EXPORT_MAX_BYTES
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** The OpenDocument URI is consumed by UI and discarded before this owner returns. */
class AndroidChatGptExportPrivateAssetStore(context: Context) : ChatGptPrivateAssetStore {
    private val root = File(context.filesDir, "chatgpt-export-import-assets/v1")
    override fun copy(request: ChatGptPrivateCopyRequest): ChatGptPrivateCopyResult = runCatching {
        if (request.bytes.size.toLong() !in 1..CHATGPT_EXPORT_MAX_BYTES) return ChatGptPrivateCopyResult.Failed(ChatGptImportFailure.TOO_LARGE)
        if (!root.exists() && !root.mkdirs()) return ChatGptPrivateCopyResult.Failed(ChatGptImportFailure.PRIVATE_COPY_FAILED)
        val safeName = request.displayName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "conversations.json" }
        val key = "chatgpt-export-import-assets/v1/${request.taskId.value}/$safeName"; val target = File(root.parentFile!!.parentFile!!, key); target.parentFile?.mkdirs()
        val part = File(target.parentFile, ".${target.name}.part")
        FileOutputStream(part).use { output -> output.write(request.bytes); output.fd.sync() }
        if (!part.renameTo(target)) { part.delete(); return ChatGptPrivateCopyResult.Failed(ChatGptImportFailure.PRIVATE_COPY_FAILED) }
        ChatGptPrivateCopyResult.Copied(ChatGptImportAsset(key, request.mimeType, safeName, request.bytes.size.toLong(), request.bytes.sha256()))
    }.getOrElse { ChatGptPrivateCopyResult.Failed(ChatGptImportFailure.PRIVATE_COPY_FAILED) }
    override fun read(storageKey: String): ByteArray? = if (storageKey.matches(Regex("chatgpt-export-import-assets/v1/[A-Za-z0-9-]+/[A-Za-z0-9._-]+"))) File(root.parentFile!!.parentFile!!, storageKey).takeIf(File::isFile)?.readBytes() else null
}
private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
