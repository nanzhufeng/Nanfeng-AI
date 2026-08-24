package com.nanzhufeng.ai.domain

/**
 * Local, deterministic registry owner. It deliberately has no transport: a later authorized
 * synchronizer may submit a reviewed snapshot, but cannot publish an unverified remote result.
 */
class InMemoryVersionedModelRegistry(
    initialSnapshots: List<ModelRegistrySnapshot> = emptyList(),
    initialPreviousStableSnapshots: List<ModelRegistrySnapshot> = emptyList(),
) : VersionedModelRegistry {
    private val current = initialSnapshots
        .filter { it.verificationStatus == RegistryVerificationStatus.VERIFIED }
        .associateBy { it.providerId }
        .toMutableMap()
    private val previousStable = initialPreviousStableSnapshots
        .filter { it.verificationStatus == RegistryVerificationStatus.VERIFIED }
        .associateBy { it.providerId }
        .toMutableMap()

    override fun currentSnapshot(providerId: ProviderId): ModelRegistrySnapshot? = current[providerId]

    override fun previousStableSnapshot(providerId: ProviderId): ModelRegistrySnapshot? = previousStable[providerId]

    override fun publishVerified(snapshot: ModelRegistrySnapshot): ModelRegistryPublicationResult {
        if (snapshot.providerId != ProviderId.OPENROUTER ||
            snapshot.verificationStatus != RegistryVerificationStatus.VERIFIED ||
            snapshot.models.isEmpty() || snapshot.presetMappings.isEmpty()
        ) {
            return ModelRegistryPublicationResult.Rejected(AiTaskError.ModelRegistrySnapshotInvalid)
        }
        val previous = current[snapshot.providerId]
        if (previous?.id == snapshot.id) {
            return ModelRegistryPublicationResult.Rejected(AiTaskError.ModelRegistrySnapshotInvalid)
        }
        if (previous != null) previousStable[snapshot.providerId] = previous
        current[snapshot.providerId] = snapshot
        return ModelRegistryPublicationResult.Published(snapshot, previous)
    }

    override fun resolve(providerId: ProviderId, presetId: ModelPresetId): ModelRegistryResolution {
        val snapshot = current[providerId]
            ?: return ModelRegistryResolution.Rejected(AiTaskError.ModelRegistrySnapshotUnverified)
        // A logical label is a promise to the user.  A registry built by guessing a nearby
        // catalog entry must never be used for ordinary chat: it could spend the wrong model
        // or send the request to a model the user did not choose.
        if (snapshot.mappingUsesFallback) {
            return ModelRegistryResolution.Rejected(AiTaskError.ModelRegistryModelUnavailable)
        }
        val model = snapshot.modelFor(presetId)
            ?: return ModelRegistryResolution.Rejected(AiTaskError.ModelRegistryModelUnavailable)
        // A logical model choice must never silently resolve to a provider's discounted
        // Fast lane. Old persisted catalog snapshots can outlive a mapping-policy update,
        // so reject them here as well; the sender then refreshes the public catalog and only
        // proceeds with a standard real-time variant.
        if (model.id.contains(":fast", ignoreCase = true) || model.displayName.contains("(Fast)", ignoreCase = true)) {
            return ModelRegistryResolution.Rejected(AiTaskError.ModelRegistryModelUnavailable)
        }
        return ModelRegistryResolution.Resolved(snapshot, model)
    }
}
