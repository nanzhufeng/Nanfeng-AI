package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationTitleGenerationRecord
import com.nanzhufeng.ai.domain.ConversationTitleGenerationRecordStore
import com.nanzhufeng.ai.domain.ConversationTitleGenerationStatus
import com.nanzhufeng.ai.domain.ConversationTitleRefinementResult
import com.nanzhufeng.ai.domain.ConversationTitleSource
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfiguredConversationTitleRefinerContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
    private val source = ConversationTitleSource("请整理 Android 设置页面层级", "可以按入口和使用频率重新组织设置页面。")

    @Test fun `DeepSeek V4 Flash is the shared first choice when all background providers are available`() {
        val transport = RecordingTransport(listOf(success("Android设置规划")))
        val records = Records()
        val result = refiner(setOf(ProviderId.DEEPSEEK, ProviderId.ZHIPU, ProviderId.QWEN), records, transport).refine(ConversationId("conversation"), source)

        assertEquals(ConversationTitleRefinementResult.Title("Android设置规划"), result)
        assertEquals(listOf("deepseek-v4-flash"), transport.modelIds())
        assertFalse(transport.requestBodies.single().contains("gpt-5.6-sol"))
        assertFalse(transport.requestBodies.single().contains("claude-opus-5"))
        assertTrue(transport.requestBodies.single().contains("汉字、英文字母或阿拉伯数字"))
        assertEquals(ConversationTitleGenerationStatus.SUCCEEDED, records.values.single().status)
        assertEquals(ProviderId.DEEPSEEK, records.values.single().providerId)
        assertEquals("deepseek-v4-flash", records.values.single().modelId)
        assertEquals(ConversationCostSource.LOCAL_ESTIMATE, records.values.single().costSource)
    }

    @Test fun `GLM 5_3 Flash is the second choice when DeepSeek fails`() {
        val transport = RecordingTransport(listOf(ProviderChatOutcome.HttpResponse(503, "temporarily unavailable"), success("标题整理模型规则")))
        val records = Records()
        val result = refiner(setOf(ProviderId.DEEPSEEK, ProviderId.ZHIPU, ProviderId.QWEN), records, transport).refine(ConversationId("conversation"), source)

        assertEquals(ConversationTitleRefinementResult.Title("标题整理模型规则"), result)
        assertEquals(listOf("deepseek-v4-flash", "glm-5.3-flash"), transport.modelIds())
        assertEquals(listOf(ProviderId.DEEPSEEK, ProviderId.ZHIPU), records.values.map { it.providerId })
    }

    @Test fun `Qwen Flash stays last after DeepSeek and GLM fail`() {
        val transport = RecordingTransport(listOf(
            ProviderChatOutcome.HttpResponse(503, "temporarily unavailable"),
            ProviderChatOutcome.HttpResponse(503, "temporarily unavailable"),
            success("千问末位兜底标题"),
        ))
        val records = Records()
        val result = refiner(setOf(ProviderId.DEEPSEEK, ProviderId.ZHIPU, ProviderId.QWEN), records, transport).refine(ConversationId("conversation"), source)

        assertEquals(ConversationTitleRefinementResult.Title("千问末位兜底标题"), result)
        assertEquals(listOf("deepseek-v4-flash", "glm-5.3-flash", "qwen3.6-flash"), transport.modelIds())
        assertEquals(listOf(ConversationTitleGenerationStatus.FAILED, ConversationTitleGenerationStatus.FAILED, ConversationTitleGenerationStatus.SUCCEEDED), records.values.map { it.status })
        assertEquals("HTTP_503", records.values.first().safeErrorCode)
    }

    @Test fun `no configured service leaves a safe retryable failure record`() {
        val records = Records()
        val transport = RecordingTransport(emptyList())
        val result = refiner(emptySet(), records, transport).refine(ConversationId("conversation"), source)

        assertEquals(ConversationTitleRefinementResult.Failed("NO_CONFIGURED_TITLE_MODEL"), result)
        assertTrue(transport.requestBodies.isEmpty())
        assertEquals(listOf(ProviderId.DEEPSEEK, ProviderId.ZHIPU, ProviderId.QWEN), records.values.map { it.providerId })
        assertEquals(listOf("SERVICE_DISABLED", "SERVICE_DISABLED", "SERVICE_DISABLED"), records.values.map { it.safeErrorCode })
    }

    private fun refiner(enabled: Set<ProviderId>, records: Records, transport: RecordingTransport) = ConfiguredConversationTitleRefiner(
        records = records,
        configuration = LoadModelServiceConfigurationUseCase(Settings(enabled), Credentials(enabled)),
        credentials = Credentials(enabled),
        modelResolver = ModelResolver { preset -> ResolvedModelResult.Resolved(model(preset)) },
        transport = transport,
        clock = clock,
    )

    private fun model(preset: ModelPresetId): ResolvedModel = ResolvedModel(
        providerId = NanfengModelServiceCatalog.providerFor(preset),
        modelId = when (preset) {
            ModelPresetId.DEEPSEEK_V4_FLASH -> "deepseek-v4-flash"
            ModelPresetId.GLM_5_3_FLASH -> "glm-5.3-flash"
            ModelPresetId.QWEN_3_6_FLASH -> "qwen3.6-flash"
            else -> error("Unexpected title preset $preset")
        },
        displayName = NanfengModelServiceCatalog.preset(preset).displayName,
        capabilities = ModelCapabilities(true, false, true),
        contextWindowTokens = 16_000,
        health = ModelHealth.AVAILABLE,
        metadataUpdatedAt = clock.instant(),
        healthCheckedAt = clock.instant(),
        maxOutputTokens = 256,
    )

    private fun success(title: String) = ProviderChatOutcome.HttpResponse(
        200,
        "{\"choices\":[{\"message\":{\"content\":\"{\\\"title\\\":\\\"$title\\\"}\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4}}",
    )

    private class Settings(private val enabled: Set<ProviderId>) : ModelServiceSettingsRepository {
        override fun load(providerId: ProviderId) = ProviderSettings(providerId, providerId in enabled, NanfengModelServiceCatalog.defaultPreset(providerId))
        override fun save(settings: ProviderSettings) = settings
    }

    private class Credentials(private val enabled: Set<ProviderId>) : ProviderCredentialStore {
        override fun hasCredential(providerId: ProviderId) = providerId in enabled
        override fun saveCredential(providerId: ProviderId, credential: CharArray) = true
        override fun loadCredential(providerId: ProviderId) = if (providerId in enabled) "test-credential".toCharArray() else null
    }

    private class Records : ConversationTitleGenerationRecordStore {
        val values = mutableListOf<ConversationTitleGenerationRecord>()
        override fun record(value: ConversationTitleGenerationRecord) { values += value }
        override fun listNewestFirst() = values.asReversed()
    }

    private class RecordingTransport(outcomes: List<ProviderChatOutcome>) : ProviderChatTransport {
        private val remaining = outcomes.toMutableList()
        val requestBodies = mutableListOf<String>()
        override fun execute(request: ProviderChatRequest, credential: CharArray): ProviderChatOutcome {
            requestBodies += request.jsonBody
            return remaining.removeAt(0)
        }
        fun modelIds() = requestBodies.map { Regex("\\\"model\\\":\\\"([^\\\"]+)\\\"").find(it)?.groupValues?.get(1) ?: "" }
    }
}
