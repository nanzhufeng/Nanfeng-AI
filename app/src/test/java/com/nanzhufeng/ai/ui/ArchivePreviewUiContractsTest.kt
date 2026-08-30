package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivePreviewUiContractsTest {
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

    @Test fun `archive cards open an inert bounded local index instead of a thumbnail error`() {
        for (token in listOf("ArchivePreviewDialog", "压缩包内容", "点击查看压缩包内容", "preview.entries", "preview.totalEntryCount", "onOpenEntry(entry)", "preview.containerPath", "preview.directoryPath", "进入")) {
            assertTrue(token, workspace.contains(token))
        }
        for (token in listOf("isSafeArchiveAttachment", "attachmentPreview.archive", "attachmentPreview.archiveEntry", "openArchiveEntry", "archiveEntryReturnPreview", "archivePreview", "entry.isDirectory", "directoryPath.dropLast")) {
            assertTrue(token, viewModel.contains(token))
        }
        val viewer = workspace.substring(workspace.indexOf("private fun ArchivePreviewDialog"), workspace.indexOf("private fun TextPreviewSystemBarsEffect"))
        assertFalse(viewer.contains("ZipInputStream"))
        assertFalse(viewer.contains("extract"))
    }
}
