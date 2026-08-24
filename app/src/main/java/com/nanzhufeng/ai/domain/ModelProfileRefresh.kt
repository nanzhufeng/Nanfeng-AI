package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Duration

/** A provider list only proves current model IDs; capability metadata remains the verified profile. */
sealed interface ProviderModelListResult {
    data class Available(val modelIds: Set<String>) : ProviderModelListResult
    data object Unavailable : ProviderModelListResult
}

fun interface ProviderModelListClient {
    fun fetch(providerId: ProviderId, endpoint: String, credential: CharArray): ProviderModelListResult
}

class RefreshModelProfilesUseCase(
    private val directory: ModelProfileDirectory,
    private val credentials: ProviderCredentialStore,
    private val client: ProviderModelListClient,
    private val clock: Clock,
) : ModelProfileRefresher {
    override fun refreshIfStale(providerId: ProviderId, selectedPreset: ModelPresetId, endpoint: String): ModelProfileRefreshResult {
        if (providerId !in setOf(ProviderId.QWEN, ProviderId.DEEPSEEK)) return ModelProfileRefreshResult.SKIPPED
        val selected = directory.profile(selectedPreset) ?: return ModelProfileRefreshResult.UNAVAILABLE
        if (selected.metadataUpdatedAt?.let { Duration.between(it, clock.instant()) < MAX_AGE } == true) return ModelProfileRefreshResult.FRESH
        val credential = credentials.loadCredential(providerId) ?: return ModelProfileRefreshResult.UNAVAILABLE
        val fetched = try { client.fetch(providerId, endpoint, credential) } finally { credential.fill('\u0000') }
        val ids = (fetched as? ProviderModelListResult.Available)?.modelIds.orEmpty()
        if (ids.isEmpty()) return ModelProfileRefreshResult.UNAVAILABLE
        val now = clock.instant()
        val refreshed = directory.profiles().mapValues { (_, profile) ->
            if (profile.providerId != providerId) profile else {
                // A replacement is accepted only when a verified profile explicitly lists it.
                // We never guess from model-name similarity or silently downgrade to a sibling.
                val verifiedId = sequenceOf(profile.modelId).plus(profile.alternateModelIds.asSequence())
                    .filter(ids::contains).distinct().singleOrNull()
                profile.copy(
                modelId = verifiedId ?: profile.modelId,
                health = if (verifiedId != null) ModelHealth.UNKNOWN else ModelHealth.UNAVAILABLE,
                metadataUpdatedAt = now,
                healthCheckedAt = null,
                )
            }
        }
        return if (directory.replaceIfNewer(refreshed)) ModelProfileRefreshResult.UPDATED else ModelProfileRefreshResult.FRESH
    }

    private companion object { val MAX_AGE: Duration = Duration.ofHours(24) }
}
