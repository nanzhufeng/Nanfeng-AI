package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.PdfPreviewPositionStore

/** App-private UI state only; the key is a stable attachment ID, never a URI or filesystem path. */
class AndroidPdfPreviewPositionStore(context: Context) : PdfPreviewPositionStore {
    private val preferences = context.applicationContext.getSharedPreferences("local-pdf-preview-position-v1", Context.MODE_PRIVATE)

    override fun pageFor(attachmentId: AttachmentId): Int = preferences.getInt(key(attachmentId), 1).coerceAtLeast(1)

    override fun savePage(attachmentId: AttachmentId, pageNumber: Int) {
        preferences.edit().putInt(key(attachmentId), pageNumber.coerceAtLeast(1)).apply()
    }

    private fun key(attachmentId: AttachmentId) = "attachment.${attachmentId.value}"
}
