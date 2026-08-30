package com.nanzhufeng.ai.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_MIME_TYPES
import com.nanzhufeng.ai.domain.ConversationAttachmentSelection
import com.nanzhufeng.ai.domain.DOCX_MIME_TYPE
import com.nanzhufeng.ai.domain.PPTX_MIME_TYPE
import com.nanzhufeng.ai.domain.XLSX_MIME_TYPE
import com.nanzhufeng.ai.domain.normalizedAttachmentExtension

sealed interface AndroidDocumentOpenResult {
    data class Opened(val selection: ConversationAttachmentSelection) : AndroidDocumentOpenResult
    data object Cancelled : AndroidDocumentOpenResult
    data class Rejected(val reason: String) : AndroidDocumentOpenResult
}

/** DocumentsUI adapter: it reads once, privately copies through the domain, and never persists a URI. */
class AndroidDocumentSelectionReader(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun open(uri: Uri?): AndroidDocumentOpenResult {
        if (uri == null) return AndroidDocumentOpenResult.Cancelled
        if (uri.scheme != "content") return AndroidDocumentOpenResult.Rejected("文件来源不可读取，当前草稿未改变。")
        val displayName = displayName(uri)
        val mime = canonicalConversationMime(resolver.getType(uri), displayName)
            ?: return AndroidDocumentOpenResult.Rejected("暂不支持该文件类型。")
        val input = runCatching { resolver.openInputStream(uri) }.getOrNull()
            ?: return AndroidDocumentOpenResult.Rejected("文件不可读取，当前草稿未改变。")
        return AndroidDocumentOpenResult.Opened(ConversationAttachmentSelection(input, mime, displayName))
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
        }
    }.getOrNull()?.takeIf { !it.contains("content://") && !it.startsWith('/') }

    /** DocumentsUI commonly reports WAV/M4A under vendor aliases; canonicalize before magic-byte validation. */
    private fun canonicalConversationMime(reportedMime: String?, displayName: String?): String? {
        val normalized = reportedMime?.lowercase()
        if (normalized in CONVERSATION_ALLOWED_MIME_TYPES) return normalized
        val extension = normalizedAttachmentExtension(displayName)
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "md", "markdown" -> "text/markdown"
            "json" -> "application/json"
            "csv" -> "text/csv"
            "xml" -> "application/xml"
            "yaml", "yml" -> "application/x-yaml"
            "html", "htm" -> "text/html"
            "txt", "log", "ini", "cfg", "conf", "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "css", "sql", "sh" -> "text/plain"
            "docx" -> DOCX_MIME_TYPE
            "xlsx" -> XLSX_MIME_TYPE
            "pptx" -> PPTX_MIME_TYPE
            "zip" -> "application/zip"
            else -> when {
                normalized in setOf("audio/x-wav", "audio/wave", "audio/vnd.wave") -> "audio/wav"
                normalized in setOf("audio/x-m4a", "audio/m4a") -> "audio/mp4"
                normalized == "application/x-zip-compressed" -> "application/zip"
                else -> null
            }
        }
    }
}
