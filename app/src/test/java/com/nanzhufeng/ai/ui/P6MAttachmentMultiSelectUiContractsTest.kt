package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class P6MAttachmentMultiSelectUiContractsTest {
    @Test fun `both normal and temporary composer pickers use the native multi-select surface`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        for (token in listOf(
            "PickMultipleVisualMedia(com.nanzhufeng.ai.domain.CONVERSATION_ATTACHMENT_MAX_COUNT)",
            "PickMultipleVisualMedia(com.nanzhufeng.ai.domain.TemporaryConversationRecovery.MAX_ATTACHMENTS)",
            "PickVisualMedia.ImageAndVideo",
            "OpenMultipleDocuments()",
            "onConversationVisualPickerResults(uris)",
            "onTemporaryVisualPickerResults(uris)",
            "onConversationDocumentPickerResults(uris)",
            "onTemporaryDocumentPickerResults(uris)",
        )) assertTrue("missing $token", app.contains(token))
    }

    @Test fun `visual and document batches retain supported items while reporting individual rejections`() {
        val owner = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val reader = File("src/main/java/com/nanzhufeng/ai/data/AndroidGallerySelectionReader.kt").readText()
        for (token in listOf(
            "fun onConversationVisualPickerResults(uris: List<Uri>)",
            "fun onConversationDocumentPickerResults(uris: List<Uri>)",
            "fun onTemporaryVisualPickerResults(uris: List<Uri>)",
            "fun onTemporaryDocumentPickerResults(uris: List<Uri>)",
            "AttachmentBatchOutcome",
            "uris.forEach { uri ->",
            "wasAlreadyAttached",
            "另有 \${outcome.rejectionReasons.size} 项未加入",
        )) assertTrue("missing $token", owner.contains(token))
        assertTrue(reader.contains("fun openConversationVisual(uri: Uri)"))
        assertTrue(reader.contains("CONVERSATION_ALLOWED_VIDEO_MIME_TYPES"))
    }

    @Test fun `composer describes the visual picker as images and videos`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        assertTrue(workspace.contains("添加图片和视频"))
    }
}
