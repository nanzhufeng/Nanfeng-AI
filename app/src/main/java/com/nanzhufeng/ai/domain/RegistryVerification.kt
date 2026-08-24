package com.nanzhufeng.ai.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Clock

const val OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models"

enum class RegistryVerificationDisplayStatus { NOT_VERIFIED, VERIFIED, STABLE_FALLBACK }

data class RegistryVerificationStatusView(
    val status: RegistryVerificationDisplayStatus,
    val sourceUrl: String? = null,
    val verifiedAt: java.time.Instant? = null,
    val catalogVersion: String? = null,
    val failure: OpenRouterCatalogFailure? = null,
) {
    val shortCatalogVersion: String? get() = catalogVersion?.take(12)
}

sealed interface VerifyOpenRouterRegistryResult {
    data class Verified(val snapshot: ModelRegistrySnapshot) : VerifyOpenRouterRegistryResult
    data class StableFallback(
        val snapshot: ModelRegistrySnapshot,
        val failure: OpenRouterCatalogFailure,
    ) : VerifyOpenRouterRegistryResult
    data class Unavailable(val failure: OpenRouterCatalogFailure) : VerifyOpenRouterRegistryResult
}

class OpenRouterRegistrySnapshotVerifier {
    fun verify(response: OpenRouterCatalogResponse, capturedAt: java.time.Instant): ModelRegistrySnapshot? =
        runCatching { verifyCatalog(response, capturedAt) }.getOrNull()

    private fun verifyCatalog(response: OpenRouterCatalogResponse, capturedAt: java.time.Instant): ModelRegistrySnapshot? {
        val normalized = response.models
            .filter { it.id.isNotBlank() && it.displayName.isNotBlank() }
            .sortedBy { it.id }
        if (normalized.isEmpty() || normalized.map(OpenRouterCatalogModel::id).distinct().size != normalized.size) return null

        val provisionalModels = normalized.map { model ->
            val knownPricing = listOf(model.promptUsdPerToken, model.completionUsdPerToken, model.cacheReadUsdPerToken).any { it != null }
            ModelDescriptor(
                id = model.id,
                displayName = model.displayName,
                capabilities = ModelCapabilities(
                    supportsText = "text" in model.inputModalities && "text" in model.outputModalities,
                    supportsVision = "image" in model.inputModalities,
                    supportsStreaming = "stream" in model.supportedParameters,
                    supportsStructuredOutput = "response_format" in model.supportedParameters,
                ),
                contextWindowTokens = model.contextWindowTokens,
                maxOutputTokens = model.maxOutputTokens,
                pricing = if (knownPricing) ModelPricing(
                    priceVersion = "registry-pending",
                    currencyCode = "USD",
                    inputMicrosPerToken = model.promptUsdPerToken?.toMicrosOrNull(),
                    outputMicrosPerToken = model.completionUsdPerToken?.toMicrosOrNull(),
                    cachedInputMicrosPerToken = model.cacheReadUsdPerToken?.toMicrosOrNull(),
                ) else ModelPricing(),
                inputModalities = model.inputModalities,
                outputModalities = model.outputModalities,
                supportedParameters = model.supportedParameters,
            )
        }
        val mapping = mapClaudePresets(provisionalModels) ?: return null
        val hash = ModelRegistrySnapshotHasher.sha256(provisionalModels, mapping.mappings)
        val version = hash.take(16)
        val models = provisionalModels.map { model -> model.copy(
            pricing = model.pricing.copy(priceVersion = model.pricing.priceVersion?.let { version }),
        ) }
        return ModelRegistrySnapshot(
            id = ModelRegistrySnapshotId.new(), schemaVersion = 1, providerId = ProviderId.OPENROUTER,
            catalogVersion = version, source = RegistrySnapshotSource.OPENROUTER_CATALOG, capturedAt = capturedAt,
            verificationStatus = RegistryVerificationStatus.VERIFIED, lastVerifiedAt = capturedAt,
            models = models, presetMappings = mapping.mappings, sourceUrl = OPENROUTER_MODELS_URL,
            sourceEtag = response.sourceEtag?.takeIf { it.length <= 256 }, catalogSha256 = hash,
            mappingUsesFallback = mapping.usesFallback,
        )
    }

    private fun mapClaudePresets(models: List<ModelDescriptor>): Mapping? {
        val candidates = models.filter { model ->
            !model.id.contains(":batch", ignoreCase = true) &&
                !model.id.contains(":fast", ignoreCase = true) &&
                !model.displayName.contains("(Fast)", ignoreCase = true) &&
                model.capabilities.supportsText
        }.sortedWith(compareByDescending<ModelDescriptor> { it.contextWindowTokens ?: 0L }.thenByDescending { it.id })
        if (candidates.isEmpty()) return null

        fun exact(tokens: List<String>): ModelDescriptor? =
            candidates.firstOrNull { candidate -> tokens.any { token -> token in candidate.id } }

        // Do not guess.  Catalog names change, but a logical product label may only become
        // selectable when the public catalog contains its explicit mapping.
        val fable = exact(listOf("fable-5", "fable_5"))
        val opus = exact(listOf("opus-5", "opus_5")) ?: return null
        val sonnet = exact(listOf("sonnet-5", "sonnet_5"))
        val haiku = exact(listOf("haiku-4-5", "haiku_4_5", "haiku"))
        val sol = exact(listOf("gpt-5.6-sol", "gpt-5-6-sol")) ?: return null
        val terra = exact(listOf("gpt-5.6-terra", "gpt-5-6-terra")) ?: return null
        val luna = exact(listOf("gpt-5.6-luna", "gpt-5-6-luna"))
        val gemini = exact(listOf("gemini-3.7-flash", "gemini-3-7-flash")) ?: return null
        val mappings = buildList {
            fable?.let { add(ModelPresetMapping(ModelPresetId.CLAUDE_FABLE_5, it.id)) }
            add(ModelPresetMapping(ModelPresetId.CLAUDE_OPUS_5, opus.id))
            sonnet?.let { add(ModelPresetMapping(ModelPresetId.CLAUDE_SONNET_5, it.id)) }
            haiku?.let { add(ModelPresetMapping(ModelPresetId.CLAUDE_HAIKU_4_5, it.id)) }
            add(ModelPresetMapping(ModelPresetId.GPT_5_6_SOL, sol.id))
            add(ModelPresetMapping(ModelPresetId.GPT_5_6_TERRA, terra.id))
            luna?.let { add(ModelPresetMapping(ModelPresetId.GPT_5_6_LUNA, it.id)) }
            add(ModelPresetMapping(ModelPresetId.GEMINI_3_7_FLASH, gemini.id))
        }
        return Mapping(mappings, usesFallback = false)
    }

    private fun String.toMicrosOrNull(): Long? = runCatching {
        BigDecimal(this).movePointRight(6).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
            .takeIf { it >= 0L }
    }.getOrNull()

    private data class Mapping(val mappings: List<ModelPresetMapping>, val usesFallback: Boolean)
}

object ModelRegistrySnapshotHasher {
    fun sha256(models: List<ModelDescriptor>, mappings: List<ModelPresetMapping>): String {
        val canonical = models.sortedBy { it.id }.joinToString("\n") { model ->
            listOf(
                model.id, model.displayName, model.contextWindowTokens?.toString().orEmpty(), model.maxOutputTokens?.toString().orEmpty(),
                model.inputModalities.sorted().joinToString(","), model.outputModalities.sorted().joinToString(","),
                model.supportedParameters.sorted().joinToString(","), model.pricing.inputMicrosPerToken?.toString().orEmpty(),
                model.pricing.outputMicrosPerToken?.toString().orEmpty(), model.pricing.cachedInputMicrosPerToken?.toString().orEmpty(),
            ).joinToString("|")
        } + "\n--mappings--\n" + mappings.sortedBy { it.presetId.name }.joinToString("\n") {
            "${it.presetId.name}|${it.modelId}"
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

class VerifyOpenRouterRegistryUseCase(
    private val client: OpenRouterRegistryCatalogClient,
    private val verifier: OpenRouterRegistrySnapshotVerifier,
    private val registry: VersionedModelRegistry,
    private val store: ModelRegistrySnapshotStore,
    private val clock: Clock,
) {
    fun execute(): VerifyOpenRouterRegistryResult {
        val current = registry.currentSnapshot(ProviderId.OPENROUTER)
        val fetched = client.fetchCatalog()
        val failure = when (fetched) {
            is OpenRouterCatalogFetchResult.Failed -> fetched.reason
            is OpenRouterCatalogFetchResult.Fetched -> null
        }
        if (failure != null) return fallback(current, failure)
        val snapshot = verifier.verify((fetched as OpenRouterCatalogFetchResult.Fetched).response, clock.instant())
            ?: return fallback(current, OpenRouterCatalogFailure.MALFORMED_RESPONSE)
        val persisted = store.persist(StoredModelRegistrySnapshots(snapshot, current))
        if (!persisted) return fallback(current, OpenRouterCatalogFailure.MALFORMED_RESPONSE)
        return when (registry.publishVerified(snapshot)) {
            is ModelRegistryPublicationResult.Published -> VerifyOpenRouterRegistryResult.Verified(snapshot)
            is ModelRegistryPublicationResult.Rejected -> fallback(current, OpenRouterCatalogFailure.MALFORMED_RESPONSE)
        }
    }

    private fun fallback(current: ModelRegistrySnapshot?, failure: OpenRouterCatalogFailure): VerifyOpenRouterRegistryResult =
        current?.let { VerifyOpenRouterRegistryResult.StableFallback(it, failure) }
            ?: VerifyOpenRouterRegistryResult.Unavailable(failure)
}

class LoadRegistryVerificationStatusUseCase(private val registry: VersionedModelRegistry) {
    fun execute(): RegistryVerificationStatusView {
        val current = registry.currentSnapshot(ProviderId.OPENROUTER) ?: return RegistryVerificationStatusView(RegistryVerificationDisplayStatus.NOT_VERIFIED)
        return RegistryVerificationStatusView(
            status = RegistryVerificationDisplayStatus.VERIFIED, sourceUrl = current.sourceUrl,
            verifiedAt = current.lastVerifiedAt, catalogVersion = current.catalogVersion,
        )
    }
}
