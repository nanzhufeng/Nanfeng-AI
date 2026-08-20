package com.nanzhufeng.ai.domain

import java.time.Instant

/**
 * Pure registry boundary for MM-O2. It separates the model the product means to use from the
 * concrete deployment and the Provider adapter that receives a request. It has no credential,
 * transport, UI, database, or remote-catalog behavior.
 */
@JvmInline
value class MultiProviderRegistrySnapshotId(val value: String) {
    init { require(value.isNotBlank() && value == value.trim()) }
}

enum class ProviderAdapterIdentity { OPENROUTER_OPENAI_COMPATIBLE }

data class ProviderRegistryEntry(
    val handle: ProviderHandle,
    val displayName: String,
    val adapter: ProviderAdapterIdentity,
    val fixedEndpoint: String,
) {
    init {
        require(displayName.isNotBlank())
        require(fixedEndpoint.startsWith("https://"))
    }
}

data class LogicalModelDescriptor(
    val id: LogicalModelId,
    val displayName: String,
) {
    init { require(displayName.isNotBlank()) }
}

/**
 * Confirmed Compare MVP intent, expressed only as stable logical models. Deployment resolution
 * stays registry-driven, so no volatile Provider-facing model ID is frozen into this contract.
 */
object CompareMvpLogicalModels {
    val chatgpt = LogicalModelDescriptor(LogicalModelId("logical.chatgpt"), "ChatGPT")
    val claude = LogicalModelDescriptor(LogicalModelId("logical.claude"), "Claude")
    val defaultPair: List<LogicalModelDescriptor> = listOf(chatgpt, claude)
}

enum class ModelDeploymentState { ACTIVE, RETIRED }

data class ModelDeploymentDescriptor(
    val id: ModelDeploymentId,
    val logicalModelId: LogicalModelId,
    val provider: ProviderHandle,
    /** Provider-facing model identity. It is not a logical model ID or a display name. */
    val providerModelId: String,
    val state: ModelDeploymentState,
    val pricing: ModelPricing = ModelPricing(),
) {
    init { require(providerModelId.isNotBlank() && providerModelId == providerModelId.trim()) }

    val hasKnownPrice: Boolean get() = pricing.inputMicrosPerToken != null && pricing.outputMicrosPerToken != null
}

data class MultiProviderRegistrySnapshot(
    val id: MultiProviderRegistrySnapshotId,
    val schemaVersion: Int,
    val catalogVersion: String,
    val capturedAt: Instant,
    val providers: List<ProviderRegistryEntry>,
    val logicalModels: List<LogicalModelDescriptor>,
    val deployments: List<ModelDeploymentDescriptor>,
) {
    init {
        require(schemaVersion > 0)
        require(catalogVersion.isNotBlank())
        require(providers.map(ProviderRegistryEntry::handle).distinct().size == providers.size)
        require(logicalModels.map(LogicalModelDescriptor::id).distinct().size == logicalModels.size)
        require(deployments.map(ModelDeploymentDescriptor::id).distinct().size == deployments.size)
        require(deployments.map { it.provider to it.providerModelId }.distinct().size == deployments.size) {
            "同一 Provider 的实际模型 ID 只能对应一个部署。"
        }
        require(deployments.all { deployment -> providers.any { it.handle == deployment.provider } }) {
            "部署必须引用同一目录中的 Provider。"
        }
        require(deployments.all { deployment -> logicalModels.any { it.id == deployment.logicalModelId } }) {
            "部署必须引用同一目录中的逻辑模型。"
        }
    }
}

enum class MultiProviderRegistryRejection {
    READ_ONLY_PROJECTION,
    SNAPSHOT_ALREADY_CURRENT,
    NO_PREVIOUS_STABLE_SNAPSHOT,
    UNKNOWN_DEPLOYMENT,
    DEPLOYMENT_LOGICAL_MODEL_MISMATCH,
    DEPLOYMENT_PROVIDER_MISMATCH,
    DEPLOYMENT_INACTIVE,
    PRICE_UNAVAILABLE,
}

sealed interface MultiProviderRegistryResolution {
    data class Resolved(
        val snapshot: MultiProviderRegistrySnapshot,
        val provider: ProviderRegistryEntry,
        val deployment: ModelDeploymentDescriptor,
    ) : MultiProviderRegistryResolution

    data class Rejected(val reason: MultiProviderRegistryRejection) : MultiProviderRegistryResolution
}

sealed interface MultiProviderRegistryMutationResult {
    data class Published(
        val current: MultiProviderRegistrySnapshot,
        val previousStable: MultiProviderRegistrySnapshot?,
    ) : MultiProviderRegistryMutationResult

    data class RolledBack(
        val current: MultiProviderRegistrySnapshot,
        val previousStable: MultiProviderRegistrySnapshot,
    ) : MultiProviderRegistryMutationResult

    data class Rejected(val reason: MultiProviderRegistryRejection) : MultiProviderRegistryMutationResult
}

/** Content-free readback facts captured at invocation time; this does not persist anything itself. */
data class HistoricalDeploymentReference(
    val provider: ProviderHandle,
    val providerModelId: String,
    val deploymentId: ModelDeploymentId,
    val catalogVersion: String,
    val priceVersion: String?,
) {
    companion object {
        fun from(resolution: MultiProviderRegistryResolution.Resolved) = HistoricalDeploymentReference(
            provider = resolution.provider.handle,
            providerModelId = resolution.deployment.providerModelId,
            deploymentId = resolution.deployment.id,
            catalogVersion = resolution.snapshot.catalogVersion,
            priceVersion = resolution.deployment.pricing.priceVersion,
        )
    }
}

interface MultiProviderModelRegistry {
    fun currentSnapshot(): MultiProviderRegistrySnapshot?
    fun previousStableSnapshot(): MultiProviderRegistrySnapshot?
    fun publish(snapshot: MultiProviderRegistrySnapshot): MultiProviderRegistryMutationResult
    fun rollbackToPreviousStable(): MultiProviderRegistryMutationResult
    fun resolve(selection: ModelDeploymentSelection): MultiProviderRegistryResolution
}

/**
 * Process-local owner used until an explicitly authorized persistence Adapter is introduced.
 * Publishing is intentionally caller-driven: this owner never fetches a catalog or probes a Provider.
 */
class InMemoryMultiProviderModelRegistry(
    initialSnapshots: List<MultiProviderRegistrySnapshot> = emptyList(),
    initialPreviousStableSnapshot: MultiProviderRegistrySnapshot? = null,
) : MultiProviderModelRegistry {
    private var current = initialSnapshots.lastOrNull()
    private var previousStable = initialPreviousStableSnapshot ?: initialSnapshots.dropLast(1).lastOrNull()

    override fun currentSnapshot(): MultiProviderRegistrySnapshot? = current

    override fun previousStableSnapshot(): MultiProviderRegistrySnapshot? = previousStable

    override fun publish(snapshot: MultiProviderRegistrySnapshot): MultiProviderRegistryMutationResult {
        if (snapshot.id == current?.id) {
            return MultiProviderRegistryMutationResult.Rejected(MultiProviderRegistryRejection.SNAPSHOT_ALREADY_CURRENT)
        }
        val previous = current
        previousStable = previous
        current = snapshot
        return MultiProviderRegistryMutationResult.Published(snapshot, previous)
    }

    override fun rollbackToPreviousStable(): MultiProviderRegistryMutationResult {
        val rollback = previousStable
            ?: return MultiProviderRegistryMutationResult.Rejected(MultiProviderRegistryRejection.NO_PREVIOUS_STABLE_SNAPSHOT)
        val displaced = current ?: return MultiProviderRegistryMutationResult.Rejected(MultiProviderRegistryRejection.NO_PREVIOUS_STABLE_SNAPSHOT)
        current = rollback
        previousStable = displaced
        return MultiProviderRegistryMutationResult.RolledBack(rollback, displaced)
    }

    override fun resolve(selection: ModelDeploymentSelection): MultiProviderRegistryResolution {
        val snapshot = current ?: return MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.UNKNOWN_DEPLOYMENT)
        val deployment = snapshot.deployments.firstOrNull { it.id == selection.deploymentId }
            ?: return MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.UNKNOWN_DEPLOYMENT)
        if (deployment.logicalModelId != selection.logicalModelId) {
            return MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.DEPLOYMENT_LOGICAL_MODEL_MISMATCH)
        }
        if (deployment.provider != selection.provider) {
            return MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.DEPLOYMENT_PROVIDER_MISMATCH)
        }
        if (deployment.state != ModelDeploymentState.ACTIVE) {
            return MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.DEPLOYMENT_INACTIVE)
        }
        if (!deployment.hasKnownPrice) {
            return MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.PRICE_UNAVAILABLE)
        }
        val provider = snapshot.providers.first { it.handle == deployment.provider }
        return MultiProviderRegistryResolution.Resolved(snapshot, provider, deployment)
    }
}
