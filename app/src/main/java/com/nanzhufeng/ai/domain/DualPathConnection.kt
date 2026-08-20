package com.nanzhufeng.ai.domain

/**
 * P10-A shared product vocabulary for the two independently authorized paths.
 * It is deliberately a pure status/guard contract: it cannot load a credential,
 * create an Authorization header, run HTTP, or enqueue sync.
 */
enum class ConnectionCapability { LOCAL_OFFLINE_READY, ONLINE_CONFIGURATION_REQUIRED, ONLINE_NETWORK_UNVERIFIED }
enum class ProviderConfiguration { NOT_CONFIGURED, DISABLED, READY_FOR_GUARD }
enum class CatalogFreshness { NOT_AVAILABLE, CURRENT, STALE }
enum class EgressConsentState { REQUIRED_PER_INTENT, GRANTED_FOR_CURRENT_INTENT, CANCELLED }
enum class SyncCapability { LOCAL_ONLY_READY, ENCRYPTED_SYNC_NOT_CONFIGURED, ENCRYPTED_SYNC_READY }
enum class DegradedReason { NO_CREDENTIAL, PROVIDER_DISABLED, CATALOG_UNAVAILABLE, CATALOG_STALE, NETWORK_UNVERIFIED, EGRESS_CONSENT_REQUIRED, UNKNOWN_COST, MODEL_UNAVAILABLE, SYNC_NOT_CONFIGURED }
enum class ModelExecutionPath { LOCAL_OFFLINE, ONLINE_PROVIDER }
enum class DataPath { LOCAL_ONLY, ENCRYPTED_SYNC }

data class ConnectionCapabilitySnapshot(
    val connection: ConnectionCapability,
    val providerConfiguration: ProviderConfiguration,
    val credentialPresence: CredentialPresence,
    val catalogFreshness: CatalogFreshness,
    val egressConsent: EgressConsentState,
    val syncCapability: SyncCapability,
    val degradedReasons: Set<DegradedReason>,
) {
    val localExecutionAvailable: Boolean get() = true
    val localDataAvailable: Boolean get() = true
    val onlineExecutionAvailable: Boolean get() = degradedReasons.isEmpty()
    val encryptedSyncAvailable: Boolean get() = syncCapability == SyncCapability.ENCRYPTED_SYNC_READY
}

/** The only selection result. A blocked online choice never becomes a local success. */
sealed interface PathSelectionResult {
    data class LocalReady(val dataPath: DataPath = DataPath.LOCAL_ONLY) : PathSelectionResult
    data class OnlineReady(val dataPath: DataPath) : PathSelectionResult
    data class OnlineBlocked(val reasons: Set<DegradedReason>, val localFallbackAvailable: Boolean = true) : PathSelectionResult
    data object Cancelled : PathSelectionResult
    data class Replayed(val original: PathSelectionResult) : PathSelectionResult
}

data class OnlineIntent(
    val id: String,
    val dataPath: DataPath,
    val consent: EgressConsentState,
    val costKnown: Boolean,
    val modelAvailable: Boolean,
)

/**
 * In-memory contract seam for tests. Production callers create a new coordinator for a user
 * action and do not register any fake transport in release DI.
 */
class ConnectionPathGuard(private val snapshot: ConnectionCapabilitySnapshot) {
    private val results = linkedMapOf<String, PathSelectionResult>()

    fun selectLocal(): PathSelectionResult = PathSelectionResult.LocalReady()

    fun selectOnline(intent: OnlineIntent): PathSelectionResult {
        results[intent.id]?.let { return PathSelectionResult.Replayed(it) }
        if (intent.consent == EgressConsentState.CANCELLED) return PathSelectionResult.Cancelled.also { results[intent.id] = it }
        val reasons = buildSet {
            addAll(snapshot.degradedReasons)
            if (intent.consent != EgressConsentState.GRANTED_FOR_CURRENT_INTENT) add(DegradedReason.EGRESS_CONSENT_REQUIRED)
            if (!intent.costKnown) add(DegradedReason.UNKNOWN_COST)
            if (!intent.modelAvailable) add(DegradedReason.MODEL_UNAVAILABLE)
            if (intent.dataPath == DataPath.ENCRYPTED_SYNC && !snapshot.encryptedSyncAvailable) add(DegradedReason.SYNC_NOT_CONFIGURED)
        }
        val result = if (reasons.isEmpty()) PathSelectionResult.OnlineReady(intent.dataPath)
        else PathSelectionResult.OnlineBlocked(reasons)
        results[intent.id] = result
        return result
    }
}

class ReadConnectionCapabilityUseCase(
    private val loadModelConfiguration: LoadModelServiceConfigurationUseCase,
    private val loadRegistryStatus: LoadRegistryVerificationStatusUseCase,
    private val encryptedSyncConfigured: () -> Boolean,
) {
    fun execute(): ConnectionCapabilitySnapshot {
        val configuration = loadModelConfiguration.execute()
        val registry = loadRegistryStatus.execute()
        val providerConfiguration = when {
            configuration == null -> ProviderConfiguration.NOT_CONFIGURED
            !configuration.settings.enabled -> ProviderConfiguration.DISABLED
            configuration.credentialState == CredentialState.MISSING -> ProviderConfiguration.NOT_CONFIGURED
            else -> ProviderConfiguration.READY_FOR_GUARD
        }
        val credentialPresence = if (configuration?.credentialState == CredentialState.STORED) CredentialPresence.PRESENT else CredentialPresence.MISSING
        val catalogFreshness = when (registry.status) {
            RegistryVerificationDisplayStatus.VERIFIED -> CatalogFreshness.CURRENT
            RegistryVerificationDisplayStatus.STABLE_FALLBACK -> CatalogFreshness.STALE
            RegistryVerificationDisplayStatus.NOT_VERIFIED -> CatalogFreshness.NOT_AVAILABLE
        }
        val reasons = buildSet {
            if (credentialPresence == CredentialPresence.MISSING) add(DegradedReason.NO_CREDENTIAL)
            if (providerConfiguration == ProviderConfiguration.DISABLED) add(DegradedReason.PROVIDER_DISABLED)
            when (catalogFreshness) {
                CatalogFreshness.NOT_AVAILABLE -> add(DegradedReason.CATALOG_UNAVAILABLE)
                CatalogFreshness.STALE -> add(DegradedReason.CATALOG_STALE)
                CatalogFreshness.CURRENT -> Unit
            }
            // This phase deliberately performs no reachability probe. Network is unknown, not healthy.
            add(DegradedReason.NETWORK_UNVERIFIED)
            add(DegradedReason.EGRESS_CONSENT_REQUIRED)
        }
        val syncCapability = if (encryptedSyncConfigured()) SyncCapability.ENCRYPTED_SYNC_READY else SyncCapability.ENCRYPTED_SYNC_NOT_CONFIGURED
        val withSync = if (syncCapability == SyncCapability.ENCRYPTED_SYNC_NOT_CONFIGURED) reasons + DegradedReason.SYNC_NOT_CONFIGURED else reasons
        return ConnectionCapabilitySnapshot(
            connection = if (providerConfiguration == ProviderConfiguration.READY_FOR_GUARD) ConnectionCapability.ONLINE_NETWORK_UNVERIFIED else ConnectionCapability.ONLINE_CONFIGURATION_REQUIRED,
            providerConfiguration = providerConfiguration,
            credentialPresence = credentialPresence,
            catalogFreshness = catalogFreshness,
            egressConsent = EgressConsentState.REQUIRED_PER_INTENT,
            syncCapability = syncCapability,
            degradedReasons = withSync,
        )
    }
}
