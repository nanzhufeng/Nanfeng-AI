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
        val model = snapshot.modelFor(presetId)
            ?: return ModelRegistryResolution.Rejected(AiTaskError.ModelRegistryModelUnavailable)
        return ModelRegistryResolution.Resolved(snapshot, model)
    }
}
