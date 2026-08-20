package com.nanzhufeng.ai.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class P6F2DVideoPreviewUiContractsTest {
    @Test fun `workspace expands directly into a local video player with one gesture owner`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val viewer = source.substring(source.indexOf("private fun VideoPreviewDialog"), source.indexOf("private fun AudioPreviewDialog"))
        for (token in listOf("VideoPreviewDialog", "VideoView", "VideoControlOverlay", "VideoPlaybackOverlay", "controlsVisible", "delay(12_000)", "onSingleTapConfirmed", "onBottomButton", "onTimeline", "onDoubleTap", "onFling", "ACTION_MOVE", "edgeExitHandled", "fromLeftEdge || fromRightEdge", "togglePlayback()", "Surface(color = Color.Black", "onOpenVideoPreview", "onCloseVideoPreview")) assertTrue(token, source.contains(token))
        org.junit.Assert.assertFalse(viewer.contains("MediaController"))
        org.junit.Assert.assertFalse(viewer.contains("开始本地播放"))
        org.junit.Assert.assertFalse(viewer.contains("点击播放前不会自动播放、上传或外发"))
    }

    @Test fun `video frames remain identifiable before playback in both transcript and composer`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val chip = source.substring(source.indexOf("private fun AttachmentPreviewChip"), source.indexOf("private fun PdfPreviewDialog"))
        val composer = source.substring(source.indexOf("private fun ComposerAttachmentPreview"), source.indexOf("private fun ComposerMenuOverlay"))
        for (token in listOf("private fun VideoAttachmentOverlay", "Icons.Filled.PlayArrow", "视频预览，可播放", "formatVideoDuration(duration)")) assertTrue(token, source.contains(token))
        assertTrue(chip.contains("if (isVideo) VideoAttachmentOverlay(preview?.videoDurationMillis)"))
        assertTrue(composer.contains("if (isVideo) VideoAttachmentOverlay(preview?.videoDurationMillis)"))
        assertTrue(chip.contains("val previewCorner = if (isVideo) 20.dp else if (isImage) 10.dp else 10.dp"))
        assertTrue(chip.contains("isVideo -> Modifier.widthIn(min = 144.dp, max = 220.dp).height(128.dp)"))
        assertTrue(chip.contains("isImage -> originalAspectPreviewModifier(bitmap, maxEdge = 220.dp, fallbackSize = 92.dp)"))
        org.junit.Assert.assertFalse(chip.contains("isImage -> Modifier.border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(previewCorner))"))
        assertTrue(composer.contains("val previewCorner = if (isVideo) 20.dp else if (isImage) 10.dp else 14.dp"))
    }

    @Test fun `expanded video keeps central and bottom controls in one toggle state and supports double tap playback`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val viewer = source.substring(source.indexOf("private fun VideoPreviewDialog"), source.indexOf("private fun AudioPreviewDialog"))
        assertTrue(viewer.contains("var isPlaying"))
        assertTrue(viewer.contains("if (controlsVisible && preview.bytes != null && localFile != null)"))
        assertTrue(viewer.contains("VideoPlaybackOverlay(\n                        isPlaying = isPlaying,\n                        onToggle = ::togglePlayback"))
        assertTrue(viewer.contains("val onCentralButton = kotlin.math.hypot(event.x - centerX, event.y - centerY) <= centralRadiusPx"))
        assertTrue(viewer.contains("if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow"))
        assertTrue(viewer.contains("videoView?.start()"))
        assertTrue(viewer.contains("view.pause()"))
        assertTrue(viewer.contains("delay(250)"))
        assertTrue(viewer.contains("Color.Black.copy(alpha = 0.44f)"))
    }

    @Test fun `image thumbnails use the same half-strength corner treatment in search`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val start = source.indexOf("private fun SearchAttachmentCard")
        val searchCard = source.substring(start, source.indexOf("private fun SearchAttachmentRow", start))
        assertTrue(searchCard.contains("RoundedCornerShape(if (isVideo) 20.dp else 10.dp)"))
        assertTrue(searchCard.contains("Color.Black.copy(alpha = if (isVideo) 0.18f else 0.08f)"))
    }
}
