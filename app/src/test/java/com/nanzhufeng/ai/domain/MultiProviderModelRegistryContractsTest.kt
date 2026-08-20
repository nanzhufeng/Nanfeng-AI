package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiProviderModelRegistryContractsTest {
    private val openRouter = ProviderRegistryEntry(
        handle = ProviderHandle("openrouter"),
        displayName = "OpenRouter",
        adapter = ProviderAdapterIdentity.OPENROUTER_OPENAI_COMPATIBLE,
        fixedEndpoint = "https://openrouter.ai/api/v1",
    )
    private val logicalClaude = LogicalModelDescriptor(
        id = CompareMvpLogicalModels.claude.id,
        displayName = CompareMvpLogicalModels.claude.displayName,
    )
    private val deployment = ModelDeploymentDescriptor(
        id = ModelDeploymentId("openrouter.anthropic.claude-sonnet"),
        logicalModelId = logicalClaude.id,
        provider = openRouter.handle,
        providerModelId = "anthropic/claude-sonnet-fixture",
        state = ModelDeploymentState.ACTIVE,
        pricing = ModelPricing("openrouter-price-v1", "USD", 3, 15, 0),
    )

    @Test fun `Compare MVP starts with exactly the ChatGPT and Claude logical models without pinning provider model IDs`() {
        assertEquals(
            listOf(LogicalModelId("logical.chatgpt"), LogicalModelId("logical.claude")),
            CompareMvpLogicalModels.defaultPair.map(LogicalModelDescriptor::id),
        )
        assertEquals(listOf("ChatGPT", "Claude"), CompareMvpLogicalModels.defaultPair.map(LogicalModelDescriptor::displayName))
    }

    @Test fun `OpenRouter first adapter maps logical model deployment and provider independently`() {
        val registry = InMemoryMultiProviderModelRegistry(listOf(snapshot("catalog-v1", deployment)))

        val resolved = registry.resolve(
            ModelDeploymentSelection(logicalClaude.id, deployment.id, openRouter.handle),
        ) as MultiProviderRegistryResolution.Resolved

        assertEquals(logicalClaude.id, resolved.deployment.logicalModelId)
        assertEquals(openRouter.handle, resolved.deployment.provider)
        assertEquals("anthropic/claude-sonnet-fixture", resolved.deployment.providerModelId)
        assertEquals(ProviderAdapterIdentity.OPENROUTER_OPENAI_COMPATIBLE, resolved.provider.adapter)
    }

    @Test fun `catalog upgrade retains a stable previous snapshot and rollback restores it`() {
        val v1 = snapshot("catalog-v1", deployment)
        val v2Deployment = deployment.copy(
            providerModelId = "anthropic/claude-sonnet-v2-fixture",
            pricing = ModelPricing("openrouter-price-v2", "USD", 4, 18, 0),
        )
        val registry = InMemoryMultiProviderModelRegistry(listOf(v1))

        assertTrue(registry.publish(snapshot("catalog-v2", v2Deployment)) is MultiProviderRegistryMutationResult.Published)
        assertEquals("catalog-v1", registry.previousStableSnapshot()!!.catalogVersion)
        assertEquals("catalog-v2", registry.currentSnapshot()!!.catalogVersion)

        assertTrue(registry.rollbackToPreviousStable() is MultiProviderRegistryMutationResult.RolledBack)
        assertEquals("catalog-v1", registry.currentSnapshot()!!.catalogVersion)
        assertEquals("catalog-v2", registry.previousStableSnapshot()!!.catalogVersion)
    }

    @Test fun `unknown pricing and inactive deployments fail closed without changing selection`() {
        val unknownPrice = deployment.copy(
            id = ModelDeploymentId("openrouter.unknown-price"),
            providerModelId = "anthropic/unknown-price-fixture",
            pricing = ModelPricing(),
        )
        val retired = deployment.copy(
            id = ModelDeploymentId("openrouter.retired"),
            providerModelId = "anthropic/retired-fixture",
            state = ModelDeploymentState.RETIRED,
        )
        val registry = InMemoryMultiProviderModelRegistry(listOf(snapshot("catalog-v1", unknownPrice, retired)))

        assertEquals(
            MultiProviderRegistryRejection.PRICE_UNAVAILABLE,
            (registry.resolve(ModelDeploymentSelection(logicalClaude.id, unknownPrice.id, openRouter.handle)) as MultiProviderRegistryResolution.Rejected).reason,
        )
        assertEquals(
            MultiProviderRegistryRejection.DEPLOYMENT_INACTIVE,
            (registry.resolve(ModelDeploymentSelection(logicalClaude.id, retired.id, openRouter.handle)) as MultiProviderRegistryResolution.Rejected).reason,
        )
    }

    @Test fun `historical deployment reference keeps actual recipient catalog and price facts after an upgrade`() {
        val registry = InMemoryMultiProviderModelRegistry(listOf(snapshot("catalog-v1", deployment)))
        val historical = HistoricalDeploymentReference.from(
            registry.resolve(ModelDeploymentSelection(logicalClaude.id, deployment.id, openRouter.handle)) as MultiProviderRegistryResolution.Resolved,
        )
        registry.publish(snapshot("catalog-v2", deployment.copy(providerModelId = "anthropic/changed-later", pricing = ModelPricing("openrouter-price-v2", "USD", 9, 9, 0))))

        assertEquals(openRouter.handle, historical.provider)
        assertEquals("anthropic/claude-sonnet-fixture", historical.providerModelId)
        assertEquals(deployment.id, historical.deploymentId)
        assertEquals("catalog-v1", historical.catalogVersion)
        assertEquals("openrouter-price-v1", historical.priceVersion)
    }

    @Test fun `selection cannot silently substitute a different logical model or provider`() {
        val registry = InMemoryMultiProviderModelRegistry(listOf(snapshot("catalog-v1", deployment)))

        assertEquals(
            MultiProviderRegistryRejection.DEPLOYMENT_LOGICAL_MODEL_MISMATCH,
            (registry.resolve(ModelDeploymentSelection(LogicalModelId("logical.other"), deployment.id, openRouter.handle)) as MultiProviderRegistryResolution.Rejected).reason,
        )
        assertEquals(
            MultiProviderRegistryRejection.DEPLOYMENT_PROVIDER_MISMATCH,
            (registry.resolve(ModelDeploymentSelection(logicalClaude.id, deployment.id, ProviderHandle("other-provider"))) as MultiProviderRegistryResolution.Rejected).reason,
        )
    }

    private fun snapshot(catalogVersion: String, vararg deployments: ModelDeploymentDescriptor) = MultiProviderRegistrySnapshot(
        id = MultiProviderRegistrySnapshotId("snapshot-$catalogVersion"),
        schemaVersion = 1,
        catalogVersion = catalogVersion,
        capturedAt = Instant.parse("2026-08-15T00:00:00Z"),
        providers = listOf(openRouter),
        logicalModels = listOf(CompareMvpLogicalModels.chatgpt, logicalClaude),
        deployments = deployments.toList(),
    )
}
