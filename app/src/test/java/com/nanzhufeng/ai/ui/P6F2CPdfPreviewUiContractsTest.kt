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
        for (token in listOf("PdfPreviewDialog", "上一页", "下一页", "FilePreviewTopActions", "关闭文件预览", "contentScale = ContentScale.Fit", "onOpenPdfPreview", "onOpenPdfPage")) assertTrue(token, workspace.contains(token))
        assertFalse(viewer.contains("本地 PDF 阅读"))
        assertFalse(viewer.contains("PDF 脚本、表单动作、外部资源和自动链接均不执行"))
        assertFalse(workspace.contains("http://"))
        assertFalse(workspace.contains("https://"))
    }

    @Test fun `PDF viewer reads only current attachment ID and persists only page position`() {
        for (token in listOf("currentAttachmentReferences", "pdfPreviewPosition.pageFor", "pdfPreviewPosition.savePage", "attachmentPreview.pdfPage")) assertTrue(token, viewModel.contains(token))
        assertFalse(viewModel.contains("selectedPath"))
    }
}
