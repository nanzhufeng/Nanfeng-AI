package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.VideoPreviewPositionStore

/** App-private playback resume state. The attachment ID is the only persisted identifier. */
class AndroidVideoPreviewPositionStore(context: Context) : VideoPreviewPositionStore {
    private val preferences = context.applicationContext.getSharedPreferences("local-video-preview-position-v1", Context.MODE_PRIVATE)
    override fun positionFor(attachmentId: AttachmentId): Long = preferences.getLong(key(attachmentId), 0L).coerceAtLeast(0L)
    override fun savePosition(attachmentId: AttachmentId, positionMillis: Long) { preferences.edit().putLong(key(attachmentId), positionMillis.coerceAtLeast(0L)).apply() }
    private fun key(attachmentId: AttachmentId) = "attachment.${attachmentId.value}"
}
