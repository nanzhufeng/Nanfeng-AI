package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P6F2CPdfPreviewUiContractsTest {
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

    @Test fun `PDF card opens a direct page canvas with only essential navigation`() {
        val viewer = workspace.substring(workspace.indexOf("private fun PdfPreviewDialog"), workspace.indexOf("private fun VideoPreviewDialog"))
        for (token in listOf("PdfPreviewDialog", "PdfPageCanvas", "FilePreviewTopActions", "关闭文件预览", "contentScale = ContentScale.Fit", "onOpenPdfPreview", "onOpenPdfPage", "horizontalDistancePx <= -swipeThresholdPx")) assertTrue(token, workspace.contains(token))
        assertFalse(viewer.contains("本地 PDF 阅读"))
        assertFalse(viewer.contains("PDF 脚本、表单动作、外部资源和自动链接均不执行"))
        assertFalse(viewer.contains("http://"))
        assertFalse(viewer.contains("https://"))
    }

    @Test fun `PDF viewer reads only current attachment ID and persists only page position`() {
        for (token in listOf("openedPdfReference", "pdfPreviewRequestGeneration", "pdfPreviewLoading", "pdfPreviewPosition.pageFor", "pdfPreviewPosition.savePage", "attachmentPreview.pdfPage")) assertTrue(token, viewModel.contains(token))
        assertFalse(viewModel.contains("selectedPath"))
    }

    @Test fun `PDF paging caches bounded encoded pages and prefetches neighbours without replacing visible state`() {
        for (token in listOf(
            "PDF_PAGE_CACHE_MAX_ENTRIES = 4",
            "PDF_PAGE_CACHE_MAX_BYTES = 24L * 1024L * 1024L",
            "cachedPdfPage",
            "cachePdfPage",
            "prefetchPdfNeighbours",
            "PDF_NEIGHBOUR_PREFETCH_DELAY_MS",
        )) assertTrue(token, viewModel.contains(token))
        val prefetch = viewModel.substring(viewModel.indexOf("private fun prefetchPdfNeighbours"), viewModel.indexOf("fun closePdfPreview"))
        assertFalse(prefetch.contains("state = state.copy"))
    }

    @Test fun `PDF viewer has reliable controls and document gestures without image double tap`() {
        val viewer = workspace.substring(workspace.indexOf("private fun PdfPreviewDialog"), workspace.indexOf("private fun rememberLocalPlaybackFile"))
        for (token in listOf("PdfPageCanvas", "detectTransformGestures", "awaitEachGesture", "navigationBarsPadding", "zIndex(3f)", "!loading", "DisposableEffect(bitmap)")) assertTrue(token, viewer.contains(token))
        assertFalse(viewer.contains("onDoubleTap ="))
        assertFalse(viewer.contains("toggleFilePreviewChrome"))
    }
}
