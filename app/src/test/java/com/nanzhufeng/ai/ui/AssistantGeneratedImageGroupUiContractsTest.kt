package com.nanzhufeng.ai.ui

import java.io.File
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
            "size(width = 58.dp, height = 48.dp)",
            "MaterialTheme.colorScheme.primary",
            "AI 生成图片，点击全屏查看",
        )) assertTrue("missing generated image gallery token $token", gallery.contains(token))
        assertFalse(gallery.contains("ContentScale.Crop"))
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
        assertTrue(provenance.contains("Modifier.wrapContentWidth(Alignment.Start)"))
        assertFalse(source.contains("本地静态文本，不关联模型、Provider、费用或调用记录"))
    }
}
