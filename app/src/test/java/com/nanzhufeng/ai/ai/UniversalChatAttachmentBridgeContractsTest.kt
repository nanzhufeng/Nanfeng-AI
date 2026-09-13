package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelHealth
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ResolvedModel
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalChatAttachmentBridgeContractsTest {
    private val now = Instant.parse("2026-08-30T10:00:00Z")

    @Test fun `text only target rejects an image without calling another model`() {
        val result = UniversalChatAttachmentBridge().resolve(
            ConversationId("conversation-a"),
            model(ProviderId.DEEPSEEK, "deepseek-flash", vision = false, video = false),
            "分析这张图",
            listOf(ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "chart.png", byteArrayOf(1, 2, 3))),
            false,
        )

        assertEquals(ChatAttachmentBridgeResult.Failed(ChatAttachmentBridgeResult.Failure.FORMAT), result)
    }

    @Test fun `non-native PDF stays local or rejects without falling back to OCR`() {
        val result = UniversalChatAttachmentBridge().resolve(
            ConversationId("conversation-b"),
            model(ProviderId.ZHIPU, "glm-5.3", vision = false, pdf = false, video = false),
            "整理",
            listOf(ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "scan.pdf", "%PDF-broken".toByteArray())),
            false,
        )

        assertFalse(result is ChatAttachmentBridgeResult.Ready && result.receivers.isNotEmpty())
        if (result is ChatAttachmentBridgeResult.Ready) assertTrue(result.providerAttachments.isEmpty())
    }

    @Test fun `text file stays local before the selected model receives the request`() {
        val result = UniversalChatAttachmentBridge().resolve(
            ConversationId("conversation-c"),
            model(ProviderId.ZHIPU, "glm-5.3", vision = false, video = false),
            "整理",
            listOf(ChatAttachment(ChatAttachmentKind.FILE, "text/markdown", "notes.md", "# 标题\n\n完整内容".toByteArray())),
            true,
        ) as ChatAttachmentBridgeResult.Ready

        assertTrue(result.contextText.contains("完整 UTF-8 文本"))
        assertTrue(result.contextText.contains("完整内容"))
        assertTrue(result.providerAttachments.isEmpty())
        assertTrue(result.receivers.isEmpty())
    }

    @Test fun `native vision model receives the original image directly`() {
        val image = ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "chart.png", byteArrayOf(1, 2, 3))
        val result = UniversalChatAttachmentBridge().resolve(
            ConversationId("conversation-native"),
            model(ProviderId.QWEN, "qwen3.7-plus", vision = true, video = false),
            "分析图片",
            listOf(image),
            false,
        ) as ChatAttachmentBridgeResult.Ready

        assertTrue(result.contextText.isEmpty())
        assertEquals(listOf(image), result.providerAttachments)
        assertTrue(result.receivers.isEmpty())
    }

    @Test fun `ordinary chat bridge source contains no alternate provider path`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/ai/UniversalChatAttachmentBridge.kt").readText()
        assertFalse(source.contains("describeWithQwen"))
        assertFalse(source.contains("transcribeWithGlmOcr"))
        assertFalse(source.contains("chat-attachment-bridge:"))
    }

    private fun model(provider: ProviderId, id: String, vision: Boolean, pdf: Boolean = true, video: Boolean) = ResolvedModel(
        providerId = provider,
        modelId = id,
        displayName = id,
        capabilities = ModelCapabilities(true, vision, pdf, supportsVideo = video),
        contextWindowTokens = 1_000_000,
        health = ModelHealth.AVAILABLE,
        metadataUpdatedAt = now,
        healthCheckedAt = now,
        maxOutputTokens = 8_192,
    )
}
