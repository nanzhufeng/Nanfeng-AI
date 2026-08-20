package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentTransferCompatibilityContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test fun `public media save fails visibly below scoped storage instead of using unavailable APIs`() {
        val transfer = source.substring(source.indexOf("private fun saveAttachmentToUserCollection"), source.indexOf("private class AttachmentTransferException"))
        assertTrue(transfer.contains("Build.VERSION.SDK_INT < Build.VERSION_CODES.Q"))
        assertTrue(transfer.contains("当前 Android 系统不支持安全保存到图库，请升级系统后重试。"))
        assertTrue(transfer.contains("MediaStore.Downloads.EXTERNAL_CONTENT_URI"))
        assertTrue(transfer.contains("MediaStore.MediaColumns.IS_PENDING"))
    }

    @Test fun `MP4 remux maps extractor flags to codec buffer flags`() {
        val remux = source.substring(source.indexOf("private fun remuxDownloadedMp4"), source.indexOf("private fun isGalleryMediaMime"))
        assertTrue(source.contains("@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)\nprivate fun remuxDownloadedMp4"))
        assertTrue(remux.contains("android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME"))
        assertTrue(remux.contains("info.set(0, size, extractor.sampleTime, flags)"))
    }

    @Test fun `video gesture layer exposes a real click action for accessibility services`() {
        val viewer = source.substring(source.indexOf("private fun VideoPreviewDialog"), source.indexOf("private fun VideoControlOverlay"))
        assertTrue(viewer.contains("isClickable = true"))
        assertTrue(viewer.contains("setOnClickListener"))
        assertTrue(viewer.contains("else -> performClick()"))
    }
}
