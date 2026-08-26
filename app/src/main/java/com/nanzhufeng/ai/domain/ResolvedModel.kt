package com.nanzhufeng.ai.domain

import java.time.Instant

enum class ModelHealth { AVAILABLE, DEGRADED, UNAVAILABLE, UNKNOWN }

/**
 * Provider/model-supplied planning coefficients. They are deliberately distinct from billing
 * tokens and live in model metadata rather than request/executor conditionals.
 */
data class AttachmentInputTokenEstimate(
    val imageMinimumTokens: Int,
    val imageTokensPerKiB: Int,
    val pdfMinimumTokens: Int,
    val pdfTokensPerKiB: Int,
    val videoMinimumTokens: Int,
    val videoTokensPerKiB: Int,
) {
    init { require(listOf(imageMinimumTokens, imageTokensPerKiB, pdfMinimumTokens, pdfTokensPerKiB, videoMinimumTokens, videoTokensPerKiB).all { it > 0 }) }
    companion object {
        /** Safe fallback for catalog entries that do not publish multimodal token accounting. */
        val Conservative = AttachmentInputTokenEstimate(1_024, 4, 2_048, 8, 8_192, 2)
    }
}

/** The only model record consumed by chat, Auto routing and Provider adapters. */
data class ResolvedModel(
    val providerId: ProviderId,
    val modelId: String,
    val displayName: String,
    val capabilities: ModelCapabilities,
    val contextWindowTokens: Long?,
    val health: ModelHealth,
    val metadataUpdatedAt: Instant?,
    val healthCheckedAt: Instant?,
    val maxOutputTokens: Long? = null,
    val tokenizerId: String = "heuristic-v1",
    /** Data-supplied, verified replacement IDs. Never inferred from a name prefix. */
    val alternateModelIds: Set<String> = emptySet(),
    val attachmentInputTokenEstimate: AttachmentInputTokenEstimate = AttachmentInputTokenEstimate.Conservative,
)

sealed interface ResolvedModelResult {
    data class Resolved(val model: ResolvedModel) : ResolvedModelResult
    data class Unavailable(val reason: String) : ResolvedModelResult
}

fun interface ModelResolver { fun resolve(presetId: ModelPresetId): ResolvedModelResult }

/** An updatable directory: cached provider metadata wins over the bundled offline seed. */
interface ModelProfileDirectory {
    fun profile(presetId: ModelPresetId): ResolvedModel?
    fun profiles(): Map<ModelPresetId, ResolvedModel>
    fun replaceIfNewer(profiles: Map<ModelPresetId, ResolvedModel>): Boolean
}

/** Content-free model-list refresh. It never receives a conversation, attachment or prompt. */
fun interface ModelProfileRefresher {
    fun refreshIfStale(providerId: ProviderId, selectedPreset: ModelPresetId, endpoint: String): ModelProfileRefreshResult
}

enum class ModelProfileRefreshResult { SKIPPED, FRESH, UPDATED, UNAVAILABLE }

data class ModelHealthObservation(val health: ModelHealth, val checkedAt: Instant)

interface ModelHealthStore {
    fun observation(providerId: ProviderId, presetId: ModelPresetId): ModelHealthObservation?
    fun record(providerId: ProviderId, presetId: ModelPresetId, observation: ModelHealthObservation)
}

interface ModelHealthReporter {
    fun recordSuccess(presetId: ModelPresetId)
    fun recordFailure(presetId: ModelPresetId, error: ProviderDiagnosticErrorClass)
}

class UnifiedModelResolver(
    private val registry: VersionedModelRegistry,
    private val profileDirectory: ModelProfileDirectory,
    private val healthStore: ModelHealthStore,
) : ModelResolver, ModelHealthReporter {
    override fun resolve(presetId: ModelPresetId): ResolvedModelResult {
        val provider = NanfengModelServiceCatalog.providerFor(presetId)
        if (provider != ProviderId.OPENROUTER) return profileDirectory.profile(presetId)
            ?.let { withObservedHealth(it, presetId) }?.let { ResolvedModelResult.Resolved(it) }
            ?: ResolvedModelResult.Unavailable("模型档案不存在或已失效。")
        return when (val value = registry.resolve(provider, presetId)) {
            is ModelRegistryResolution.Resolved -> ResolvedModelResult.Resolved(value.model.let { descriptor ->
                withObservedHealth(ResolvedModel(provider, descriptor.id, descriptor.displayName, descriptor.capabilities.copy(
                    supportsPdf = "pdf" in descriptor.inputModalities || "file" in descriptor.inputModalities,
                    supportsVideo = "video" in descriptor.inputModalities,
                    supportsAudio = "audio" in descriptor.inputModalities,
                    supportsTools = "tools" in descriptor.supportedParameters || "tool_choice" in descriptor.supportedParameters,
                    supportsReasoning = "reasoning" in descriptor.supportedParameters || "reasoning_effort" in descriptor.supportedParameters,
                ), descriptor.contextWindowTokens,
                    ModelHealth.UNKNOWN, null, null, descriptor.maxOutputTokens, "openrouter-catalog-v1"), presetId)
            })
            is ModelRegistryResolution.Rejected -> {
                // The public catalog is authoritative once present. On a true cold start only,
                // a separate catalog GET outage must not prevent the user's fixed standard text
                // preset from reaching OpenRouter at all. These are exact product-owned IDs,
                // not guessed relatives; any provider rejection is still shown and health-marked.
                if (registry.currentSnapshot(ProviderId.OPENROUTER) == null) {
                    OpenRouterColdStartTextFallback.resolve(presetId)?.let { fallback ->
                        return ResolvedModelResult.Resolved(withObservedHealth(fallback, presetId))
                    }
                }
                ResolvedModelResult.Unavailable("OpenRouter 模型目录尚未确认。")
            }
        }
    }

    override fun recordSuccess(presetId: ModelPresetId) = record(presetId, ModelHealth.AVAILABLE)

    override fun recordFailure(presetId: ModelPresetId, error: ProviderDiagnosticErrorClass) {
        when (error) {
            ProviderDiagnosticErrorClass.MODEL_NOT_FOUND -> record(presetId, ModelHealth.UNAVAILABLE)
            ProviderDiagnosticErrorClass.SERVER -> record(presetId, ModelHealth.DEGRADED)
            else -> Unit // Credentials, quota, rate limit and local network are not model health.
        }
    }

    private fun record(presetId: ModelPresetId, health: ModelHealth) {
        healthStore.record(NanfengModelServiceCatalog.providerFor(presetId), presetId, ModelHealthObservation(health, Instant.now()))
    }

    private fun withObservedHealth(model: ResolvedModel, presetId: ModelPresetId): ResolvedModel {
        val observation = healthStore.observation(model.providerId, presetId) ?: return model
        return model.copy(health = observation.health, healthCheckedAt = observation.checkedAt)
    }
}

/**
 * Exact, standard (never Pro/Fast) text-only identities used solely while no public registry
 * snapshot exists. They keep a catalog-host outage from becoming a global chat outage; after the
 * first verified snapshot, its mapping is the only source of truth again.
 */
private object OpenRouterColdStartTextFallback {
    private val ids = mapOf(
        ModelPresetId.CLAUDE_FABLE_5 to "anthropic/claude-fable-5",
        ModelPresetId.CLAUDE_OPUS_5 to "anthropic/claude-opus-5",
        ModelPresetId.CLAUDE_SONNET_5 to "anthropic/claude-sonnet-5",
        ModelPresetId.CLAUDE_HAIKU_4_5 to "anthropic/claude-haiku-4.5",
        ModelPresetId.GPT_5_6_SOL to "openai/gpt-5.6-sol",
        ModelPresetId.GPT_5_6_TERRA to "openai/gpt-5.6-terra",
        ModelPresetId.GPT_5_6_LUNA to "openai/gpt-5.6-luna",
        ModelPresetId.GEMINI_3_7_FLASH to "google/gemini-3.7-flash",
    )

    fun resolve(presetId: ModelPresetId): ResolvedModel? = ids[presetId]?.let { modelId ->
        ResolvedModel(
            providerId = ProviderId.OPENROUTER,
            modelId = modelId,
            displayName = NanfengModelServiceCatalog.preset(presetId).displayName,
            capabilities = ModelCapabilities(supportsText = true, supportsVision = false, supportsStreaming = true),
            contextWindowTokens = null,
            health = ModelHealth.UNKNOWN,
            metadataUpdatedAt = null,
            healthCheckedAt = null,
            tokenizerId = "openrouter-cold-start-v1",
        )
    }
}
