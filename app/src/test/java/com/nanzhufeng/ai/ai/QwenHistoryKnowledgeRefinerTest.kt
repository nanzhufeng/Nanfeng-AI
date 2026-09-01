package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.DirectChatCallAuditRecord
import com.nanzhufeng.ai.domain.DirectChatCallAuditStore
import com.nanzhufeng.ai.domain.DirectChatCallAuditSummary
import com.nanzhufeng.ai.domain.HistoryKnowledgeCurationResult
import com.nanzhufeng.ai.domain.HistoryKnowledgeCurationSource
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
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenHistoryKnowledgeRefinerTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC)
    private val source = HistoryKnowledgeCurationSource(
        conversationId = ConversationId("conversation"),
        conversationTitle = "长期资料整理",
        transcript = "用户：我希望把长期可复用的决策原则保留为历史资料。",
        messageCount = 4,
    )

    @Test fun `title and history refiners share DeepSeek GLM and Qwen order`() {
        assertEquals(
            listOf(
                ProviderId.DEEPSEEK to ModelPresetId.DEEPSEEK_V4_FLASH,
                ProviderId.ZHIPU to ModelPresetId.GLM_5_3_FLASH,
                ProviderId.QWEN to ModelPresetId.QWEN_3_6_FLASH,
            ),
            TitleAndHistoryRefinementRouting.candidates.map { it.providerId to it.preset },
        )
    }

    @Test fun `history refinement prefers DeepSeek V4 Flash`() {
        val transport = RecordingTransport(listOf(success()))
        val audit = Audit()
        val result = refiner(setOf(ProviderId.DEEPSEEK, ProviderId.ZHIPU, ProviderId.QWEN), transport, audit).refine(source)

        val draft = (result as HistoryKnowledgeCurationResult.Draft).value
        assertEquals(ProviderId.DEEPSEEK, draft.providerId)
        assertEquals("deepseek-v4-flash", draft.modelId)
        assertEquals(listOf("deepseek-v4-flash"), transport.modelIds())
        assertEquals(listOf(ProviderId.DEEPSEEK), audit.values.map { it.providerId })
    }

    @Test fun `history refinement falls back to GLM then keeps Qwen last`() {
        val transport = RecordingTransport(listOf(
            ProviderChatOutcome.HttpResponse(503, "temporarily unavailable"),
            ProviderChatOutcome.HttpResponse(503, "temporarily unavailable"),
            success(),
        ))
        val audit = Audit()
        val result = refiner(setOf(ProviderId.DEEPSEEK, ProviderId.ZHIPU, ProviderId.QWEN), transport, audit).refine(source)

        val draft = (result as HistoryKnowledgeCurationResult.Draft).value
        assertEquals(ProviderId.QWEN, draft.providerId)
        assertEquals(listOf("deepseek-v4-flash", "glm-5.3-flash", "qwen3.6-flash"), transport.modelIds())
        assertEquals(listOf(ProviderId.DEEPSEEK, ProviderId.ZHIPU, ProviderId.QWEN), audit.values.map { it.providerId })
        assertEquals(listOf("low", "max", "low"), audit.values.map { it.reasoningLevel })
    }

    @Test fun `history refinement uses GLM when DeepSeek is not configured`() {
        val transport = RecordingTransport(listOf(success()))
        val audit = Audit()
        val result = refiner(setOf(ProviderId.ZHIPU, ProviderId.QWEN), transport, audit).refine(source)

        val draft = (result as HistoryKnowledgeCurationResult.Draft).value
        assertEquals(ProviderId.ZHIPU, draft.providerId)
        assertEquals("glm-5.3-flash", draft.modelId)
        assertEquals(listOf("glm-5.3-flash"), transport.modelIds())
        assertEquals("max", audit.values.single().reasoningLevel)
    }

    @Test fun `missing model configuration fails locally without provider audit`() {
        val transport = RecordingTransport(emptyList())
        val audit = Audit()

        val result = refiner(emptySet(), transport, audit).refine(source)

        assertEquals("SERVICE_DISABLED", (result as HistoryKnowledgeCurationResult.Failed).safeCode)
        assertTrue(transport.requestBodies.isEmpty())
        assertTrue(audit.values.isEmpty())
    }

    @Test fun `network failures remain retryable facts and audit only real attempts`() {
        val transport = RecordingTransport(
            List(3) { ProviderChatOutcome.NetworkFailure(ProviderNetworkFailureKind.CONNECT) },
        )
        val audit = Audit()

        val result = refiner(setOf(ProviderId.DEEPSEEK, ProviderId.ZHIPU, ProviderId.QWEN), transport, audit).refine(source)

        assertEquals("NETWORK", (result as HistoryKnowledgeCurationResult.Failed).safeCode)
        assertEquals(3, transport.requestBodies.size)
        assertEquals(3, audit.values.size)
        assertTrue(audit.values.all { it.status == "FAILED:NETWORK" })
    }

    @Test fun `accepts a bounded eligible history knowledge draft`() {
        val result = parseHistoryKnowledgeCuration(
            """{"eligible":true,"title":"长期投资决策原则","body":"长期投资应区分资产质量和当前价格，优先关注安全边际、分批建仓与风险控制。遇到估值明显偏高时，不因害怕错过而追高；当基本假设变化时重新评估。该规则只适用于计划配置给权益资产的长期资金，现金储备与短期支出必须独立管理，并在关键假设变化后重新核验。","tags":["投资","安全边际"],"confidence":0.93}""",
            ProviderId.DEEPSEEK,
            "deepseek-v4-flash",
        )
        assertTrue(result.toString(), result is HistoryKnowledgeCurationResult.Draft)
        val draft = (result as HistoryKnowledgeCurationResult.Draft).value
        assertEquals("长期投资决策原则", draft.title)
        assertEquals(setOf("投资", "安全边际"), draft.tags)
        assertEquals(ProviderId.DEEPSEEK, draft.providerId)
    }

    @Test fun `rejects non durable conversations without manufacturing a draft`() {
        val result = parseHistoryKnowledgeCuration(
            """{"eligible":false,"title":"","body":"","tags":[],"confidence":0}""",
            ProviderId.QWEN,
            "qwen3.6-flash",
        )
        assertTrue(result.toString(), result is HistoryKnowledgeCurationResult.NotEligible)
    }

    private fun refiner(enabled: Set<ProviderId>, transport: RecordingTransport, audit: Audit) = QwenHistoryKnowledgeRefiner(
        configuration = LoadModelServiceConfigurationUseCase(Settings(enabled), Credentials(enabled)),
        credentials = Credentials(enabled),
        modelResolver = ModelResolver { preset -> ResolvedModelResult.Resolved(model(preset)) },
        transport = transport,
        clock = clock,
        audit = audit,
    )

    private fun model(preset: ModelPresetId): ResolvedModel = ResolvedModel(
        providerId = NanfengModelServiceCatalog.providerFor(preset),
        modelId = when (preset) {
            ModelPresetId.DEEPSEEK_V4_FLASH -> "deepseek-v4-flash"
            ModelPresetId.GLM_5_3_FLASH -> "glm-5.3-flash"
            ModelPresetId.QWEN_3_6_FLASH -> "qwen3.6-flash"
            else -> error("Unexpected history preset $preset")
        },
        displayName = NanfengModelServiceCatalog.preset(preset).displayName,
        capabilities = ModelCapabilities(true, false, true),
        contextWindowTokens = 16_000,
        health = ModelHealth.AVAILABLE,
        metadataUpdatedAt = clock.instant(),
        healthCheckedAt = clock.instant(),
        maxOutputTokens = 1_536,
    )

    private fun success(): ProviderChatOutcome.HttpResponse {
        val body = "长期资料应只保留用户明确确认且未来可复用的偏好、决策和工作方法。一次性问答、短期行情和模型自行推断不应写入资料库；如果信息不完整或缺少用户确认，应直接判定为不适合整理。"
        val payload = """{"eligible":true,"title":"长期资料整理规则","body":"$body","tags":["历史资料"],"confidence":0.93}"""
        val escaped = payload.replace("\\", "\\\\").replace("\"", "\\\"")
        return ProviderChatOutcome.HttpResponse(
            200,
            """{"choices":[{"message":{"content":"$escaped"}}],"usage":{"prompt_tokens":20,"completion_tokens":12}}""",
        )
    }

    private class Settings(private val enabled: Set<ProviderId>) : ModelServiceSettingsRepository {
        override fun load(providerId: ProviderId) = ProviderSettings(providerId, providerId in enabled, NanfengModelServiceCatalog.defaultPreset(providerId))
        override fun save(settings: ProviderSettings) = settings
    }

    private class Credentials(private val enabled: Set<ProviderId>) : ProviderCredentialStore {
        override fun hasCredential(providerId: ProviderId) = providerId in enabled
        override fun saveCredential(providerId: ProviderId, credential: CharArray) = true
        override fun loadCredential(providerId: ProviderId) = if (providerId in enabled) "test-credential".toCharArray() else null
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

    private class Audit : DirectChatCallAuditStore {
        val values = mutableListOf<DirectChatCallAuditRecord>()
        override fun append(record: DirectChatCallAuditRecord) { values += record }
        override fun summary() = DirectChatCallAuditSummary(values.size, 0)
        override fun listNewestFirst() = values.asReversed()
    }
}
