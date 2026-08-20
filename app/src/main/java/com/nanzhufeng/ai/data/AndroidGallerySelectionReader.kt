package com.nanzhufeng.ai.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.CONVERSATION_ALLOWED_IMAGE_MIME_TYPES
import com.nanzhufeng.ai.domain.CONVERSATION_ATTACHMENT_MAX_SOURCE_PIXELS
import com.nanzhufeng.ai.domain.GalleryImageSelection

sealed interface AndroidGalleryOpenResult {
    data class Opened(val selection: GalleryImageSelection) : AndroidGalleryOpenResult
    data class Rejected(val error: AiTaskError) : AndroidGalleryOpenResult
}

class AndroidGallerySelectionReader(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun open(uri: Uri): AndroidGalleryOpenResult {
        if (uri.scheme != "content") return AndroidGalleryOpenResult.Rejected(AiTaskError.AttachmentSourceUnavailable)
        val mimeType = resolver.getType(uri)
            ?.takeIf { it in CONVERSATION_ALLOWED_IMAGE_MIME_TYPES }
            ?: return AndroidGalleryOpenResult.Rejected(AiTaskError.AttachmentUnsupportedType)

        val bounds = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
                bounds.takeIf { it.outWidth > 0 && it.outHeight > 0 && it.outWidth.toLong() * it.outHeight.toLong() <= CONVERSATION_ATTACHMENT_MAX_SOURCE_PIXELS }
            }
        }.getOrNull()
        if (bounds == null) return AndroidGalleryOpenResult.Rejected(AiTaskError.AttachmentUnsupportedType)

        val input = runCatching { resolver.openInputStream(uri) }.getOrNull()
            ?: return AndroidGalleryOpenResult.Rejected(AiTaskError.AttachmentSourceUnavailable)
        return AndroidGalleryOpenResult.Opened(GalleryImageSelection(
            input = input,
            mimeType = mimeType,
            displayName = displayName(uri),
            width = bounds.outWidth,
            height = bounds.outHeight,
        ))
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }.getOrNull()
}
