package com.nanzhufeng.ai.domain

import java.security.MessageDigest

/** Explicit product identities; no catalog display name or UI selection participates in this map. */
data class OpenRouterLogicalModelMapping(
    val logicalModel: LogicalModelDescriptor,
    val presets: Set<ModelPresetId>,
) {
    init { require(presets.isNotEmpty()) }
}

object DirectV1OpenRouterLogicalModelMappings {
    val values = listOf(
        OpenRouterLogicalModelMapping(
            CompareMvpLogicalModels.chatgpt,
            setOf(ModelPresetId.GPT_5_6_SOL, ModelPresetId.GPT_5_6_TERRA, ModelPresetId.GPT_5_6_LUNA),
        ),
        OpenRouterLogicalModelMapping(
            CompareMvpLogicalModels.claude,
            setOf(
                ModelPresetId.CLAUDE_FABLE_5,
                ModelPresetId.CLAUDE_OPUS_5,
                ModelPresetId.CLAUDE_SONNET_5,
                ModelPresetId.CLAUDE_HAIKU_4_5,
            ),
        ),
    )
}

enum class OpenRouterMultiProviderProjectionRejection {
    SNAPSHOT_UNAVAILABLE,
    SNAPSHOT_NOT_VERIFIED,
    SNAPSHOT_SOURCE_UNSUPPORTED,
    SNAPSHOT_HASH_UNAVAILABLE,
    PRESET_MAPPING_FALLBACK,
    LOGICAL_MODEL_MAPPING_UNAVAILABLE,
    AMBIGUOUS_LOGICAL_MODEL_MAPPING,
}

sealed interface OpenRouterMultiProviderProjectionResult {
    data class Projected(val snapshot: MultiProviderRegistrySnapshot) : OpenRouterMultiProviderProjectionResult
    data class Rejected(val reason: OpenRouterMultiProviderProjectionRejection) : OpenRouterMultiProviderProjectionResult
}

/**
 * Read-only production composition adapter. It projects only the current verified OpenRouter
 * catalog into the MM-O2 registry shape. It neither fetches a catalog nor consults a credential,
 * display name, P6-G selection, UI state, fixture, or Provider transport.
 */
class OpenRouterVerifiedMultiProviderRegistryProjection(
    private val source: VersionedModelRegistry,
    private val mappings: List<OpenRouterLogicalModelMapping> = DirectV1OpenRouterLogicalModelMappings.values,
) : MultiProviderModelRegistry {
    fun projection(): OpenRouterMultiProviderProjectionResult = project(source.currentSnapshot(ProviderId.OPENROUTER))

    override fun currentSnapshot(): MultiProviderRegistrySnapshot? =
        (projection() as? OpenRouterMultiProviderProjectionResult.Projected)?.snapshot

    override fun previousStableSnapshot(): MultiProviderRegistrySnapshot? =
        (project(source.previousStableSnapshot(ProviderId.OPENROUTER)) as? OpenRouterMultiProviderProjectionResult.Projected)?.snapshot

    override fun publish(snapshot: MultiProviderRegistrySnapshot): MultiProviderRegistryMutationResult =
        MultiProviderRegistryMutationResult.Rejected(MultiProviderRegistryRejection.READ_ONLY_PROJECTION)

    override fun rollbackToPreviousStable(): MultiProviderRegistryMutationResult =
        MultiProviderRegistryMutationResult.Rejected(MultiProviderRegistryRejection.READ_ONLY_PROJECTION)

    override fun resolve(selection: ModelDeploymentSelection): MultiProviderRegistryResolution {
        val projection = projection()
        if (projection !is OpenRouterMultiProviderProjectionResult.Projected) {
            return MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.UNKNOWN_DEPLOYMENT)
        }
        return resolveSnapshot(projection.snapshot, selection)
    }

    private fun project(sourceSnapshot: ModelRegistrySnapshot?): OpenRouterMultiProviderProjectionResult {
        val snapshot = sourceSnapshot ?: return OpenRouterMultiProviderProjectionResult.Rejected(
            OpenRouterMultiProviderProjectionRejection.SNAPSHOT_UNAVAILABLE,
        )
        if (snapshot.providerId != ProviderId.OPENROUTER || snapshot.verificationStatus != RegistryVerificationStatus.VERIFIED ||
            snapshot.lastVerifiedAt == null
        ) return OpenRouterMultiProviderProjectionResult.Rejected(OpenRouterMultiProviderProjectionRejection.SNAPSHOT_NOT_VERIFIED)
        if (snapshot.source != RegistrySnapshotSource.OPENROUTER_CATALOG) {
            return OpenRouterMultiProviderProjectionResult.Rejected(OpenRouterMultiProviderProjectionRejection.SNAPSHOT_SOURCE_UNSUPPORTED)
        }
        if (snapshot.catalogSha256 == null) {
            return OpenRouterMultiProviderProjectionResult.Rejected(OpenRouterMultiProviderProjectionRejection.SNAPSHOT_HASH_UNAVAILABLE)
        }
        if (snapshot.mappingUsesFallback) {
            return OpenRouterMultiProviderProjectionResult.Rejected(OpenRouterMultiProviderProjectionRejection.PRESET_MAPPING_FALLBACK)
        }

        val mappedModels = linkedMapOf<String, Pair<LogicalModelDescriptor, ModelDescriptor>>()
        mappings.forEach { mapping ->
            mapping.presets.forEach { preset ->
                val model = snapshot.modelFor(preset) ?: return@forEach
                val existing = mappedModels[model.id]
                if (existing != null && existing.first.id != mapping.logicalModel.id) {
                    return OpenRouterMultiProviderProjectionResult.Rejected(
                        OpenRouterMultiProviderProjectionRejection.AMBIGUOUS_LOGICAL_MODEL_MAPPING,
                    )
                }
                mappedModels[model.id] = mapping.logicalModel to model
            }
        }
        if (mappedModels.isEmpty()) {
            return OpenRouterMultiProviderProjectionResult.Rejected(
                OpenRouterMultiProviderProjectionRejection.LOGICAL_MODEL_MAPPING_UNAVAILABLE,
            )
        }

        val provider = ProviderRegistryEntry(
            handle = ProviderHandle("openrouter"),
            displayName = "OpenRouter",
            adapter = ProviderAdapterIdentity.OPENROUTER_OPENAI_COMPATIBLE,
            fixedEndpoint = "https://openrouter.ai/api/v1",
        )
        val deployments = mappedModels.values.map { (logical, model) ->
            ModelDeploymentDescriptor(
                id = ModelDeploymentId("openrouter:${stableId(snapshot.id.value, logical.id.value, model.id)}"),
                logicalModelId = logical.id,
                provider = provider.handle,
                providerModelId = model.id,
                state = ModelDeploymentState.ACTIVE,
                pricing = model.pricing,
            )
        }
        val logicalModels = mappings.map(OpenRouterLogicalModelMapping::logicalModel)
            .filter { logical -> deployments.any { it.logicalModelId == logical.id } }
        return OpenRouterMultiProviderProjectionResult.Projected(
            MultiProviderRegistrySnapshot(
                id = MultiProviderRegistrySnapshotId("openrouter:${stableId(snapshot.id.value)}"),
                schemaVersion = snapshot.schemaVersion,
                catalogVersion = snapshot.catalogVersion,
                capturedAt = snapshot.capturedAt,
                providers = listOf(provider),
                logicalModels = logicalModels,
                deployments = deployments,
            ),
        )
    }

    private fun resolveSnapshot(
        snapshot: MultiProviderRegistrySnapshot,
        selection: ModelDeploymentSelection,
    ): MultiProviderRegistryResolution {
        val deployment = snapshot.deployments.firstOrNull { it.id == selection.deploymentId }
            ?: return MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.UNKNOWN_DEPLOYMENT)
        if (deployment.logicalModelId != selection.logicalModelId) {
            return MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.DEPLOYMENT_LOGICAL_MODEL_MISMATCH)
        }
        if (deployment.provider != selection.provider) {
            return MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.DEPLOYMENT_PROVIDER_MISMATCH)
        }
        if (!deployment.hasKnownPrice) {
            return MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.PRICE_UNAVAILABLE)
        }
        return MultiProviderRegistryResolution.Resolved(snapshot, snapshot.providers.single(), deployment)
    }

    private fun stableId(vararg values: String): String = MessageDigest.getInstance("SHA-256")
        .digest(values.joinToString("\u0000").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
