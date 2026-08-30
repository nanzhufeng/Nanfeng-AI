package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantGeneratedImageGroupUiContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test
    fun `assistant images use one left aligned main preview with switchable thumbnails and original viewer`() {
        val bubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun RightAlignedUserBubble"))
        val gallery = source.substring(source.indexOf("private fun AssistantGeneratedImageGroup"), source.indexOf("private fun AttachmentPreviewChip"))

        for (token in listOf(
            "assistantImageBlocks",
            "assistantOtherAttachmentBlocks",
            "horizontalAlignment = Alignment.Start",
            "AssistantGeneratedImageGroup(",
            "onOpenImagePreview = onOpenImagePreview",
            "assistantAttachmentContent()",
            "AssistantMessageActionRow",
        )) assertTrue("missing assistant gallery owner token $token", bubble.contains(token))

        for (token in listOf(
            "var selectedIdValue by rememberSaveable",
            "maximumWidth = maxWidth.coerceAtMost(340.dp)",
            "maximumHeight = 260.dp",
            "contentScale = ContentScale.Fit",
            "onClick = { onOpenImagePreview(selected.attachment.id) }",
            "horizontalScroll(rememberScrollState())",
            "Arrangement.spacedBy(8.dp)",
            "size(48.dp)",
            "contentScale = ContentScale.Crop",
            "MaterialTheme.colorScheme.primary",
            "AI 生成图片，点击全屏查看",
        )) assertTrue("missing generated image gallery token $token", gallery.contains(token))
        assertTrue(gallery.contains("contentScale = ContentScale.Fit"))
    }

    @Test
    fun `assistant image sequence is ascending across gallery viewer swipe and batch download`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), ascendingAssistantImageOrder(listOf(7, 6, 5, 4, 3, 2, 1)))

        val bubble = source.substring(source.indexOf("private fun MessageBubble"), source.indexOf("private fun RightAlignedUserBubble"))
        val previewOwner = source.substring(source.indexOf("state.imagePreview?.let"), source.indexOf("state.pdfPreview?.let"))
        assertTrue(bubble.contains("ascendingAssistantImageOrder(attachmentBlocks.filter"))
        assertTrue(previewOwner.contains("else ascendingAssistantImageOrder(imageBlocks)"))
        assertTrue(previewOwner.indexOf("else ascendingAssistantImageOrder(imageBlocks)") < previewOwner.indexOf("?.map { it.id }"))
        assertTrue(previewOwner.contains("MessageRole.USER) imageBlocks"))
    }

    @Test
    fun `import provenance is a concise theme tinted source label rather than a model lock warning`() {
        val provenance = source.substring(source.indexOf("private fun ImportedConversationProvenance"), source.indexOf("private data class TranscriptScrollMetrics"))
        for (token in listOf(
            "ImportedConversationProvenance(\"从 ChatGPT 导入\")",
            "ImportedConversationProvenance(\"从 Claude 导入\")",
            "ImportedConversationProvenance(\"从 ChatGPT ZIP 导入\")",
        )) assertTrue("missing concise provenance label $token", source.contains(token))
        assertTrue(provenance.contains("MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)"))
        assertTrue(provenance.contains("modifier = Modifier.fillMaxWidth()"))
        assertTrue(provenance.contains("Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp)"))
        assertTrue(provenance.contains("textAlign = TextAlign.Center"))
        assertFalse(source.contains("本地静态文本，不关联模型、Provider、费用或调用记录"))
    }
}
