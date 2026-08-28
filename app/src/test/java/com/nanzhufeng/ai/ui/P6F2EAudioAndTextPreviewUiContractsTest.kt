package com.nanzhufeng.ai.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class P6F2EAudioAndTextPreviewUiContractsTest {
    @Test fun `workspace keeps explicit local audio and inert text actions`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        for (token in listOf("AudioPreviewDialog", "TextPreviewDialog", "inert UTF-8", "不会渲染 HTML、执行链接、脚本或 Markdown 指令", "onOpenAudioPreview", "onOpenTextPreview", "AttachmentTextSnippet", "AttachmentTextPreviewUnavailable", "textAttachmentFormatLabel", "preview?.textPreview")) assertTrue(token, source.contains(token))
        assertTrue(java.io.File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText().contains("\"audio/*\""))
    }

    @Test fun `audio timeline previews while dragging and commits one local seek on release`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val audio = source.substring(source.indexOf("private fun AudioPreviewDialog"), source.indexOf("private fun TextPreviewDialog"))
        for (token in listOf("scrubPositionMillis", "previewTimelineSeek", "commitTimelineSeek", "if (scrubPositionMillis == null)", "AudioScrubbableTimeline", "onPreviewPosition = ::previewTimelineSeek", "onCommitPosition = ::commitTimelineSeek", "autoHide = false", "DialogProperties(usePlatformDefaultWidth = false)", "DismissibleDialogBackdrop { onClose(position()) }")) assertTrue("missing audio playback rule: $token", audio.contains(token))
        val timeline = source.substring(source.indexOf("private fun AudioScrubbableTimeline"), source.indexOf("/** A compact, inert document cover."))
        for (token in listOf("awaitEachGesture", "awaitPointerEvent", "changedToUpIgnoreConsumed", "onCommitPosition()", "if (!committed) onCancel()", "音频播放进度，可左右拖动调整位置")) assertTrue("missing single-owner timeline rule: $token", timeline.contains(token))
        assertTrue("timeline must not stack tap and drag recognizers", !timeline.contains("detectDragGestures"))
        assertTrue("audio dialog must use one fixed header action group", audio.contains("AudioPreviewTopActions(") && !audio.contains("FilePreviewTopActions("))
        assertTrue("audio title needs a fixed gap before its actions", audio.contains("Modifier.weight(1f).padding(end = 8.dp)"))
        assertTrue("audio preview must use its dark semantic player surface", audio.contains("val darkAudioPreview = ForegroundSurface.red < 0.5f") && audio.contains("if (darkAudioPreview) Color(0xFF2A3438) else Color(0xFF183551)"))
        assertTrue("audio header actions must receive the active skin", audio.contains("dark = darkAudioPreview"))
        val audioActions = source.substring(source.indexOf("private fun AudioPreviewTopActions"), source.indexOf("private fun PdfPreviewDialog"))
        assertTrue("audio header actions must avoid invisible dark-skin glyphs", audioActions.contains("val container = if (dark) Color.White.copy(alpha = 0.16f) else Color(0xFFF1F4F2)") && audioActions.contains("val content = if (dark) Color.White else BodyText"))
        val viewModel = java.io.File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        assertTrue("audio opening notice must match autoplay", viewModel.contains("正在打开本地音频并自动播放。"))
        assertTrue("stale tap-to-play notice must be gone", !viewModel.contains("点击播放才会开始。"))
    }

    @Test fun `text and PDF previews derive chrome and canvases from the active dark surface`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val pdf = source.substring(source.indexOf("private fun PdfPreviewDialog"), source.indexOf("private fun VideoPreviewDialog"))
        val text = source.substring(source.indexOf("private fun TextPreviewDialog"), source.indexOf("private fun formatVideoDuration"))
        for (viewer in listOf(pdf, text)) {
            assertTrue(viewer.contains("isDarkFilePreviewSurface()"))
            assertTrue(viewer.contains("localFilePreviewCanvas("))
            assertTrue(viewer.contains("PreviewCloseButton(dark = dark"))
        }
        assertTrue(text.contains("FilePreviewTopActions(\n                            dark = darkTextPreview"))
        assertTrue(!text.contains("dark = false"))
        assertTrue(!text.contains("background(Color(0xFFF1F4F2))"))
    }
}
