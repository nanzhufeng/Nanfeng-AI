package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.GlmOcrTransport
import com.nanzhufeng.ai.domain.GlmOcrTransportResult
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.InvocationRecord
import com.nanzhufeng.ai.domain.InvocationRepository
import com.nanzhufeng.ai.domain.LoadModelServiceConfigurationUseCase
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelHealth
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelResolver
import com.nanzhufeng.ai.domain.ModelServiceSettingsRepository
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderSettings
import com.nanzhufeng.ai.domain.ResolvedModel
import com.nanzhufeng.ai.domain.ResolvedModelResult
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalChatAttachmentBridgeContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-30T10:00:00Z"), ZoneOffset.UTC)

    @Test fun `text only target receives image as Qwen markdown without changing target model`() {
        val invocations = InvocationLog()
        var sentBody = ""
        val bridge = bridge(
            enabled = setOf(ProviderId.QWEN),
            invocations = invocations,
            providerTransport = object : ProviderChatTransport {
                override fun execute(request: ProviderChatRequest, credential: CharArray): ProviderChatOutcome {
                    sentBody = ByteArrayOutputStream().also { request.effectiveBody().writeTo(it) }.toString(Charsets.UTF_8)
                    return ProviderChatOutcome.HttpResponse(
                        200,
                        """{"choices":[{"message":{"content":"# 图片解析\n画面包含一张表格。"}}],"usage":{"prompt_tokens":120,"completion_tokens":20}}""",
                    )
                }
            },
        )
        val target = model(ProviderId.DEEPSEEK, "deepseek-v4-flash", vision = false, video = false)
        val image = ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "chart.png", byteArrayOf(1, 2, 3))

        val result = bridge.resolve(ConversationId("conversation-a"), target, "分析这张图", listOf(image), false) as ChatAttachmentBridgeResult.Ready

        assertTrue(result.contextText.contains("图片解析"))
        assertTrue(result.providerAttachments.isEmpty())
        assertEquals(setOf("千问 Qwen3.7-Plus"), result.receivers)
        assertTrue(sentBody.contains("\"image_url\""))
        assertEquals("qwen3.7-plus", invocations.values.single().modelId)
        assertEquals("deepseek-v4-flash", target.modelId)
    }

    @Test fun `scanned PDF falls back to GLM OCR and becomes text for any target`() {
        val invocations = InvocationLog()
        val bridge = bridge(
            enabled = setOf(ProviderId.ZHIPU),
            invocations = invocations,
            glm = GlmOcrTransport { _, _ ->
                GlmOcrTransportResult.Completed("# PDF\n扫描页文字", "request-1", 1, 30, 10)
            },
        )
        val target = model(ProviderId.ZHIPU, "glm-5.3", vision = false, video = false)
        val pdf = ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "scan.pdf", "%PDF-broken".toByteArray())

        val result = bridge.resolve(ConversationId("conversation-b"), target, "整理", listOf(pdf), false) as ChatAttachmentBridgeResult.Ready

        assertTrue(result.contextText.contains("扫描页文字"))
        assertTrue(result.providerAttachments.isEmpty())
        assertEquals(setOf("智谱 GLM-OCR"), result.receivers)
        assertEquals("glm-ocr", invocations.values.single().modelId)
    }

    @Test fun `GLM flagship receives markdown as a complete local text projection`() {
        val invocations = InvocationLog()
        val bridge = bridge(enabled = emptySet(), invocations = invocations)
        val target = model(ProviderId.ZHIPU, "glm-5.3", vision = false, video = false)
        val markdown = ChatAttachment(
            ChatAttachmentKind.FILE,
            "text/markdown",
            "notes.md",
            "# 标题\n\n完整内容".toByteArray(),
        )

        val result = bridge.resolve(
            ConversationId("conversation-c"),
            target,
            "整理",
            listOf(markdown),
            forceTextProjection = true,
        ) as ChatAttachmentBridgeResult.Ready

        assertTrue(result.contextText.contains("完整 UTF-8 文本"))
        assertTrue(result.contextText.contains("完整内容"))
        assertTrue(result.providerAttachments.isEmpty())
        assertTrue(invocations.values.isEmpty())
    }

    @Test fun `GLM flagship receives video as Qwen markdown and remains the answer model`() {
        val invocations = InvocationLog()
        var sentBody = ""
        val bridge = bridge(
            enabled = setOf(ProviderId.QWEN),
            invocations = invocations,
            providerTransport = object : ProviderChatTransport {
                override fun execute(request: ProviderChatRequest, credential: CharArray): ProviderChatOutcome {
                    sentBody = ByteArrayOutputStream().also { request.effectiveBody().writeTo(it) }.toString(Charsets.UTF_8)
                    return ProviderChatOutcome.HttpResponse(
                        200,
                        """{"choices":[{"message":{"content":"# 视频转写\n00:00 出现一张图表。"}}],"usage":{"prompt_tokens":90,"completion_tokens":16}}""",
                    )
                }
            },
        )
        val target = model(ProviderId.ZHIPU, "glm-5.3", vision = false, video = false)
        val video = ChatAttachment(ChatAttachmentKind.VIDEO, "video/mp4", "clip.mp4", byteArrayOf(1, 2, 3))

        val result = bridge.resolve(ConversationId("conversation-video"), target, "总结视频", listOf(video), false) as ChatAttachmentBridgeResult.Ready

        assertTrue(result.contextText.contains("视频转写"))
        assertTrue(result.providerAttachments.isEmpty())
        assertEquals(setOf("千问 Qwen3.7-Plus"), result.receivers)
        assertTrue(sentBody.contains("\"video_url\""))
        assertEquals("qwen3.7-plus", invocations.values.single().modelId)
        assertEquals("glm-5.3", target.modelId)
    }

    private fun bridge(
        enabled: Set<ProviderId>,
        invocations: InvocationLog,
        providerTransport: ProviderChatTransport = object : ProviderChatTransport {
            override fun execute(request: ProviderChatRequest, credential: CharArray): ProviderChatOutcome = ProviderChatOutcome.NetworkFailure
        },
        glm: GlmOcrTransport = GlmOcrTransport { _, _ -> GlmOcrTransportResult.Failed("disabled") },
    ) = UniversalChatAttachmentBridge(
        configuration = LoadModelServiceConfigurationUseCase(Settings(enabled), Credentials(enabled)),
        credentials = Credentials(enabled),
        modelResolver = ModelResolver { preset ->
            if (preset == ModelPresetId.QWEN_3_7_PLUS) ResolvedModelResult.Resolved(model(ProviderId.QWEN, "qwen3.7-plus", vision = true, video = true))
            else ResolvedModelResult.Unavailable("not used")
        },
        providerTransport = providerTransport,
        glmOcrTransport = glm,
        invocations = invocations,
        clock = clock,
    )

    private fun model(provider: ProviderId, id: String, vision: Boolean, video: Boolean) = ResolvedModel(
        providerId = provider,
        modelId = id,
        displayName = id,
        capabilities = ModelCapabilities(true, vision, true, supportsVideo = video),
        contextWindowTokens = 1_000_000,
        health = ModelHealth.AVAILABLE,
        metadataUpdatedAt = clock.instant(),
        healthCheckedAt = clock.instant(),
        maxOutputTokens = 8_192,
    )

    private class Settings(private val enabled: Set<ProviderId>) : ModelServiceSettingsRepository {
        override fun load(providerId: ProviderId) = ProviderSettings(providerId, providerId in enabled, NanfengModelServiceCatalog.defaultPreset(providerId))
        override fun save(settings: ProviderSettings) = settings
    }

    private class Credentials(private val enabled: Set<ProviderId>) : ProviderCredentialStore {
        override fun hasCredential(providerId: ProviderId) = providerId in enabled
        override fun saveCredential(providerId: ProviderId, credential: CharArray) = true
        override fun loadCredential(providerId: ProviderId) = if (providerId in enabled) "test-key".toCharArray() else null
    }

    private class InvocationLog : InvocationRepository {
        val values = mutableListOf<InvocationRecord>()
        override fun save(record: InvocationRecord) = record.also(values::add)
        override fun findById(id: InvocationId) = values.firstOrNull { it.id == id }
        override fun listNewestFirst() = values.asReversed()
    }
}
