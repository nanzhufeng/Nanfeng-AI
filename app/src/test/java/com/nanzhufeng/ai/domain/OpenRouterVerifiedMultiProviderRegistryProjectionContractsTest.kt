package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterVerifiedMultiProviderRegistryProjectionContractsTest {
    private val now = Instant.parse("2026-08-16T02:00:00Z")

    @Test fun `verified OpenRouter catalog projects explicit ChatGPT and multiple Claude deployments with actual target facts`() {
        val source = snapshot()
        val projection = OpenRouterVerifiedMultiProviderRegistryProjection(FixedRegistry(source))

        val projected = projection.currentSnapshot()!!
        assertEquals("catalog-v1", projected.catalogVersion)
        assertEquals(listOf("logical.chatgpt", "logical.claude"), projected.logicalModels.map { it.id.value })
        assertEquals(2, projected.deployments.count { it.logicalModelId == CompareMvpLogicalModels.claude.id })
        assertTrue(projected.deployments.none { it.providerModelId.startsWith("curated:") })

        val deployment = projected.deployments.first { it.providerModelId == "openai/gpt-dynamic-v1" }
        val resolved = projection.resolve(ModelDeploymentSelection(deployment.logicalModelId, deployment.id, deployment.provider))
            as MultiProviderRegistryResolution.Resolved
        val historical = HistoricalDeploymentReference.from(resolved)
        assertEquals("openai/gpt-dynamic-v1", historical.providerModelId)
        assertEquals("catalog-v1", historical.catalogVersion)
        assertEquals("price-v1", historical.priceVersion)
    }

    @Test fun `unverified fixture fallback and missing mappings fail closed without a synthetic snapshot`() {
        val unverified = snapshot().copy(verificationStatus = RegistryVerificationStatus.UNVERIFIED, lastVerifiedAt = null)
        assertProjectionRejected(unverified, OpenRouterMultiProviderProjectionRejection.SNAPSHOT_NOT_VERIFIED)

        val fixture = snapshot().copy(source = RegistrySnapshotSource.LOCAL_FIXTURE)
        assertProjectionRejected(fixture, OpenRouterMultiProviderProjectionRejection.SNAPSHOT_SOURCE_UNSUPPORTED)

        val fallback = snapshot().copy(mappingUsesFallback = true)
        assertProjectionRejected(fallback, OpenRouterMultiProviderProjectionRejection.PRESET_MAPPING_FALLBACK)

        val missing = snapshot(mappings = emptyList())
        assertProjectionRejected(missing, OpenRouterMultiProviderProjectionRejection.LOGICAL_MODEL_MAPPING_UNAVAILABLE)
    }

    @Test fun `unknown price remains an exact price unavailable rejection and projection is read only`() {
        val source = snapshot().copy(models = snapshot().models.map {
            if (it.id == "openai/gpt-dynamic-v1") it.copy(pricing = ModelPricing()) else it
        })
        val projection = OpenRouterVerifiedMultiProviderRegistryProjection(FixedRegistry(source))
        val deployment = projection.currentSnapshot()!!.deployments.first { it.providerModelId == "openai/gpt-dynamic-v1" }

        assertEquals(
            MultiProviderRegistryRejection.PRICE_UNAVAILABLE,
            (projection.resolve(ModelDeploymentSelection(deployment.logicalModelId, deployment.id, deployment.provider)) as MultiProviderRegistryResolution.Rejected).reason,
        )
        assertTrue(projection.publish(projection.currentSnapshot()!!) is MultiProviderRegistryMutationResult.Rejected)
        assertTrue(projection.rollbackToPreviousStable() is MultiProviderRegistryMutationResult.Rejected)
    }

    @Test fun `projection does not read credentials or invoke Auto during construction and container only registers the inert owner`() {
        val credentials = RecordingCredentials()
        val settings = object : ModelServiceSettingsRepository {
            override fun load(providerId: ProviderId): ProviderSettings = error("Construction must not load settings")
            override fun save(settings: ProviderSettings): ProviderSettings = error("Construction must not save settings")
        }
        DirectExecutionProductionComposition.create(
            registry = FixedRegistry(snapshot()),
            settings = settings,
            credentials = credentials,
            clock = java.time.Clock.systemUTC(),
        )
        assertEquals(0, credentials.presenceCalls)
        assertEquals(0, credentials.hasCalls)
        assertEquals(0, credentials.loadCalls)

        val appContainer = java.io.File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()
        val activity = java.io.File("src/main/java/com/nanzhufeng/ai/NanfengAiActivity.kt").readText()
        assertTrue(appContainer.contains("val directExecutionApplicationOwner"))
        assertTrue(appContainer.contains("DirectExecutionProductionComposition.create"))
        assertFalse(activity.contains("directExecutionApplicationOwner"))
    }

    private fun assertProjectionRejected(snapshot: ModelRegistrySnapshot, expected: OpenRouterMultiProviderProjectionRejection) {
        val projection = OpenRouterVerifiedMultiProviderRegistryProjection(FixedRegistry(snapshot))
        assertEquals(expected, (projection.projection() as OpenRouterMultiProviderProjectionResult.Rejected).reason)
        assertNull(projection.currentSnapshot())
    }

    private fun snapshot(mappings: List<ModelPresetMapping> = defaultMappings()) = ModelRegistrySnapshot(
        id = ModelRegistrySnapshotId("openrouter-verified-v1"),
        schemaVersion = 1,
        providerId = ProviderId.OPENROUTER,
        catalogVersion = "catalog-v1",
        source = RegistrySnapshotSource.OPENROUTER_CATALOG,
        capturedAt = now,
        verificationStatus = RegistryVerificationStatus.VERIFIED,
        lastVerifiedAt = now,
        models = listOf(
            ModelDescriptor("openai/gpt-dynamic-v1", "misleading display name", ModelCapabilities(true, false, true), pricing = ModelPricing("price-v1", "USD", 3, 9)),
            ModelDescriptor("anthropic/claude-dynamic-v1", "not used for routing", ModelCapabilities(true, false, true), pricing = ModelPricing("price-v1", "USD", 4, 12)),
            ModelDescriptor("anthropic/claude-dynamic-v2", "also not used", ModelCapabilities(true, false, true), pricing = ModelPricing("price-v1", "USD", 5, 15)),
        ),
        presetMappings = mappings,
        catalogSha256 = "a".repeat(64),
    )

    private fun defaultMappings() = listOf(
        ModelPresetMapping(ModelPresetId.GPT_5_6_TERRA, "openai/gpt-dynamic-v1"),
        ModelPresetMapping(ModelPresetId.CLAUDE_SONNET_5, "anthropic/claude-dynamic-v1"),
        ModelPresetMapping(ModelPresetId.CLAUDE_OPUS_5, "anthropic/claude-dynamic-v2"),
    )

    private class FixedRegistry(private val snapshot: ModelRegistrySnapshot?) : VersionedModelRegistry {
        override fun currentSnapshot(providerId: ProviderId): ModelRegistrySnapshot? = snapshot
        override fun previousStableSnapshot(providerId: ProviderId): ModelRegistrySnapshot? = null
        override fun publishVerified(snapshot: ModelRegistrySnapshot): ModelRegistryPublicationResult = error("Read only")
        override fun resolve(providerId: ProviderId, presetId: ModelPresetId): ModelRegistryResolution = error("Not used")
    }

    private class RecordingCredentials : ProviderCredentialStore {
        var presenceCalls = 0
        var hasCalls = 0
        var loadCalls = 0
        override fun credentialPresence(providerId: ProviderId): CredentialPresence = CredentialPresence.MISSING.also { presenceCalls += 1 }
        override fun hasCredential(providerId: ProviderId): Boolean = false.also { hasCalls += 1 }
        override fun saveCredential(providerId: ProviderId, credential: CharArray): Boolean = error("No writes")
        override fun loadCredential(providerId: ProviderId): CharArray? = null.also { loadCalls += 1 }
    }
}
