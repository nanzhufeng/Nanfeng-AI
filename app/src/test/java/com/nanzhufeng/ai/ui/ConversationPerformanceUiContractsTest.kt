package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPerformanceUiContractsTest {
    @Test
    fun `attachment previews stay lazy and bitmap decoding stays off the compose thread`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

        assertTrue(workspace.contains("LaunchedEffect(attachmentBlocks)"))
        assertTrue(workspace.contains("onEnsureAttachmentPreview(block.attachment)"))
        assertTrue(workspace.contains("private fun rememberDecodedBitmap("))
        assertTrue(workspace.contains("withContext(Dispatchers.Default)"))
        assertEquals(1, "BitmapFactory.decodeByteArray".toRegex().findAll(workspace).count())

        assertTrue(viewModel.contains("fun ensureAttachmentPreview(reference: ConversationAttachmentReference)"))
        assertTrue(viewModel.contains("attachmentPreviewRequests.add(reference.id)"))
        assertTrue(viewModel.contains("withContext(Dispatchers.IO) { attachmentPreview.original(reference) }"))
        assertFalse(
            viewModel.contains(
                "attachmentReferences.distinctBy { it.id }.associate { it.id to attachmentPreview.project(it) }",
            ),
        )
    }

    @Test
    fun `picker reads and captured image decoding stay off the compose thread`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

        assertTrue(app.contains("val pickerIoScope = rememberCoroutineScope()"))
        assertTrue(app.contains("pickerIoScope.launch"))
        assertTrue(app.contains("withContext(Dispatchers.IO)"))
        assertTrue(app.contains("produceState<ImageBitmap?>"))
        assertTrue(app.contains("withContext(Dispatchers.Default)"))
    }
}
