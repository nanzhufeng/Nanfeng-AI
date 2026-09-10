package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.InMemoryVersionedModelRegistry
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelDescriptor
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelPresetMapping
import com.nanzhufeng.ai.domain.ModelPricing
import com.nanzhufeng.ai.domain.ModelRegistrySnapshot
import com.nanzhufeng.ai.domain.ModelRegistrySnapshotId
import com.nanzhufeng.ai.domain.ModelRegistrySnapshotStore
import com.nanzhufeng.ai.domain.ModelRegistryResolution
import com.nanzhufeng.ai.domain.OpenRouterCatalogFailure
import com.nanzhufeng.ai.domain.OpenRouterCatalogFetchResult
import com.nanzhufeng.ai.domain.OpenRouterCatalogModel
import com.nanzhufeng.ai.domain.OpenRouterCatalogResponse
import com.nanzhufeng.ai.domain.OpenRouterRegistryCatalogClient
import com.nanzhufeng.ai.domain.OpenRouterRegistrySnapshotVerifier
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.RegistrySnapshotSource
import com.nanzhufeng.ai.domain.RegistryVerificationStatus
import com.nanzhufeng.ai.domain.StoredModelRegistrySnapshots
import com.nanzhufeng.ai.domain.VerifyOpenRouterRegistryResult
import com.nanzhufeng.ai.domain.VerifyOpenRouterRegistryUseCase
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P2JOpenRouterRegistryContractsTest {
    private val now = Instant.parse("2026-08-12T13:23:55Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `parser retains only registry whitelist and request policy has no inference shape`() {
        val parsed = OpenRouterCatalogJsonParser.parse(
            """{"data":[{"id":"anthropic/claude-sonnet-test","name":"Claude Test","description":"must-not-persist","api_key":"must-not-persist","context_length":8192,"architecture":{"input_modalities":["text","image"],"output_modalities":["text"]},"supported_parameters":["response_format"],"pricing":{"prompt":"0.000003","completion":"0.000015","cache_read":"0"}}]}""".toByteArray(),
        ).single()

        assertEquals("anthropic/claude-sonnet-test", parsed.id)
        assertEquals(setOf("text", "image"), parsed.inputModalities)
        assertEquals("0.000003", parsed.promptUsdPerToken)
        assertEquals("GET", OpenRouterRegistryRequestPolicy.METHOD)
        assertEquals("https://openrouter.ai/api/v1/models", OpenRouterRegistryRequestPolicy.URL)
        assertFalse(OpenRouterRegistryRequestPolicy.URL.contains("chat/completions"))
    }

    @Test
    fun `verified snapshot has deterministic hash pricing and complete Claude preset mapping`() {
        val snapshot = OpenRouterRegistrySnapshotVerifier().verify(
            OpenRouterCatalogResponse(claudeCatalog(), "etag-safe"), now,
        )!!

        assertEquals(RegistryVerificationStatus.VERIFIED, snapshot.verificationStatus)
        assertEquals(64, snapshot.catalogSha256!!.length)
        assertEquals(snapshot.catalogSha256!!.take(16), snapshot.catalogVersion)
        assertEquals(3L, snapshot.models.first { it.id.contains("opus") }.pricing.inputMicrosPerToken)
        assertEquals(0L, snapshot.models.first { it.id.contains("opus") }.pricing.cachedInputMicrosPerToken)
        assertTrue(snapshot.presetMappings.map(ModelPresetMapping::presetId).containsAll(setOf(
            ModelPresetId.CLAUDE_OPUS_5,
            ModelPresetId.GPT_5_6_SOL,
            ModelPresetId.GPT_5_6_TERRA,
            ModelPresetId.GEMINI_3_7_FLASH,
        )))
        assertFalse(snapshot.mappingUsesFallback)
        assertTrue(snapshot.sourceUrl!!.startsWith("https://openrouter.ai/"))
    }

    @Test
    fun `Fable 5 point 1 and Astra use their exact catalog IDs without reviving Fable 5`() {
        val snapshot = requireNotNull(OpenRouterRegistrySnapshotVerifier().verify(
            OpenRouterCatalogResponse(
                claudeCatalog() + listOf(
                    catalogModel("anthropic/claude-fable-5.1-20260831", "Claude Fable 5.1", "0.000004", "0.00002"),
                    catalogModel("openai/gpt-6-astra", "GPT-6 Astra", "0.000005", "0.000025"),
                ),
                null,
            ),
            now,
        ))

        assertEquals("anthropic/claude-fable-5.1-20260831", snapshot.modelFor(ModelPresetId.CLAUDE_FABLE_5_1)?.id)
        assertEquals("openai/gpt-6-astra", snapshot.modelFor(ModelPresetId.GPT_6_ASTRA)?.id)
        assertNull(snapshot.modelFor(ModelPresetId.CLAUDE_FABLE_5))
    }

    @Test
    fun `fast provider variants never map or resolve as a normal preset`() {
        val snapshot = OpenRouterRegistrySnapshotVerifier().verify(
            OpenRouterCatalogResponse(
                claudeCatalog() + catalogModel("anthropic/claude-opus-5:fast", "Claude Opus (Fast)", "0.000001", "0.000005"),
                null,
            ),
            now,
        )!!

        assertEquals(
            "anthropic/claude-opus-5-test",
            snapshot.modelFor(ModelPresetId.CLAUDE_OPUS_5)?.id,
        )
        val staleFastSnapshot = snapshot.copy(
            models = listOf(catalogModel("anthropic/claude-opus-5:fast", "Claude Opus (Fast)", "0.000001", "0.000005").toDescriptor()),
            presetMappings = listOf(ModelPresetMapping(ModelPresetId.CLAUDE_OPUS_5, "anthropic/claude-opus-5:fast")),
        )
        val registry = InMemoryVersionedModelRegistry(initialSnapshots = listOf(staleFastSnapshot))
        assertTrue(registry.resolve(ProviderId.OPENROUTER, ModelPresetId.CLAUDE_OPUS_5) is ModelRegistryResolution.Rejected)
    }

    @Test
    fun `removed Grok 4 point 5 is ignored while exact 4 point 6 remains mapped`() {
        val grok45 = catalogModel("x-ai/grok-4.5", "Grok 4.5", null, null).copy(
            contextWindowTokens = 500_000,
            maxOutputTokens = 450_000,
            inputModalities = setOf("text", "image", "file"),
            supportedParameters = setOf("reasoning", "tools", "response_format"),
        )
        val grokHigh = catalogModel("x-ai/grok-4.6", "Grok 4.6", "0.000002", "0.000006").copy(
            contextWindowTokens = 500_000,
            maxOutputTokens = 450_000,
            inputModalities = setOf("text", "image", "file"),
            supportedParameters = setOf("reasoning_effort", "tools", "response_format"),
        )
        val snapshot = requireNotNull(OpenRouterRegistrySnapshotVerifier().verify(
            OpenRouterCatalogResponse(claudeCatalog() + listOf(grok45, grokHigh), null), now,
        ))

        assertNull(snapshot.modelFor(ModelPresetId.GROK_4_1_FAST))
        assertNull(snapshot.modelFor(ModelPresetId.GROK_4_5))
        assertEquals("x-ai/grok-4.6", snapshot.modelFor(ModelPresetId.GROK_4_6_HIGH)?.id)
        assertTrue(snapshot.models.any { it.id == "x-ai/grok-4.5" })
        assertEquals(450_000L, snapshot.modelFor(ModelPresetId.GROK_4_6_HIGH)?.maxOutputTokens)
    }

    @Test
    fun `standard GPT presets never select similarly named Pro variants`() {
        val snapshot = OpenRouterRegistrySnapshotVerifier().verify(
            OpenRouterCatalogResponse(
                claudeCatalog() + listOf(
                    catalogModel("openai/gpt-5.6-sol-pro", "GPT Sol Pro", "0.000004", "0.00002"),
                    catalogModel("openai/gpt-5.6-terra-pro", "GPT Terra Pro", "0.000003", "0.000015"),
                ),
                null,
            ),
            now,
        )!!

        assertEquals("openai/gpt-5.6-sol", snapshot.modelFor(ModelPresetId.GPT_5_6_SOL)?.id)
        assertEquals("openai/gpt-5.6-terra", snapshot.modelFor(ModelPresetId.GPT_5_6_TERRA)?.id)
    }

    @Test
    fun `stale Pro mappings are normalized to the explicit standard GPT request ID`() {
        val proSol = catalogModel("openai/gpt-5.6-sol-pro", "GPT Sol Pro", "0.000004", "0.00002").toDescriptor()
        val proTerra = catalogModel("openai/gpt-5.6-terra-pro", "GPT Terra Pro", "0.000003", "0.000015").toDescriptor()
        val snapshot = ModelRegistrySnapshot(
            id = ModelRegistrySnapshotId("pro-stale"), schemaVersion = 1, providerId = ProviderId.OPENROUTER,
            catalogVersion = "pro-stale", source = RegistrySnapshotSource.OPENROUTER_CATALOG, capturedAt = now,
            verificationStatus = RegistryVerificationStatus.VERIFIED, lastVerifiedAt = now,
            models = listOf(proSol, proTerra),
            presetMappings = listOf(
                ModelPresetMapping(ModelPresetId.GPT_5_6_SOL, proSol.id),
                ModelPresetMapping(ModelPresetId.GPT_5_6_TERRA, proTerra.id),
            ),
        )
        val registry = InMemoryVersionedModelRegistry(initialSnapshots = listOf(snapshot))

        val sol = registry.resolve(ProviderId.OPENROUTER, ModelPresetId.GPT_5_6_SOL) as ModelRegistryResolution.Resolved
        val terra = registry.resolve(ProviderId.OPENROUTER, ModelPresetId.GPT_5_6_TERRA) as ModelRegistryResolution.Resolved

        assertEquals("openai/gpt-5.6-sol", sol.model.id)
        assertEquals("openai/gpt-5.6-terra", terra.model.id)
        assertFalse(sol.model.displayName.contains("Pro"))
        assertFalse(terra.model.displayName.contains("Pro"))
    }

    @Test
    fun `negative or fractional provider prices become unknown instead of crashing verification`() {
        val models = claudeCatalog().toMutableList().apply {
            this[0] = this[0].copy(promptUsdPerToken = "-1", completionUsdPerToken = "0.0000003")
        }

        val snapshot = OpenRouterRegistrySnapshotVerifier().verify(OpenRouterCatalogResponse(models, null), now)

        assertNotNull(snapshot)
        assertNull(snapshot!!.models.first { it.id.contains("opus") }.pricing.inputMicrosPerToken)
        assertNull(snapshot.models.first { it.id.contains("opus") }.pricing.outputMicrosPerToken)
    }

    @Test
    fun `failed read keeps the current stable snapshot and writes nothing`() {
        val stable = stableSnapshot("stable")
        val registry = InMemoryVersionedModelRegistry(listOf(stable))
        val store = RecordingStore()
        val result = VerifyOpenRouterRegistryUseCase(
            client = FailingClient(OpenRouterCatalogFailure.TIMEOUT), verifier = OpenRouterRegistrySnapshotVerifier(),
            registry = registry, store = store, clock = clock,
        ).execute()

        assertTrue(result is VerifyOpenRouterRegistryResult.StableFallback)
        assertEquals("stable", registry.currentSnapshot(ProviderId.OPENROUTER)!!.catalogVersion)
        assertNull(store.persisted)
    }

    @Test
    fun `sanitized snapshots atomically reload without raw catalog fields`() {
        val root = Files.createTempDirectory("p2j-registry-").toFile()
        val store = AndroidModelRegistrySnapshotStore(root)
        val snapshot = OpenRouterRegistrySnapshotVerifier().verify(OpenRouterCatalogResponse(claudeCatalog(), null), now)!!

        assertTrue(store.persist(StoredModelRegistrySnapshots(snapshot, null)))
        val restored = store.load().current

        assertNotNull(restored)
        assertEquals(snapshot.catalogSha256, restored!!.catalogSha256)
        assertEquals(snapshot.models.map(ModelDescriptor::id), restored.models.map(ModelDescriptor::id))
        assertFalse(File(root, "snapshots.json").readText().contains("must-not-persist"))

        val tampered = File(root, "snapshots.json")
        tampered.writeText(tampered.readText().replace("Claude Opus", "Changed"))
        assertNull(store.load().current)
    }

    private fun claudeCatalog(): List<OpenRouterCatalogModel> = listOf(
        catalogModel("anthropic/claude-opus-5-test", "Claude Opus", "0.000003", "0.000015"),
        catalogModel("anthropic/claude-sonnet-test", "Claude Sonnet", "0.000002", "0.00001"),
        catalogModel("anthropic/claude-haiku-test", "Claude Haiku", null, null),
        catalogModel("openai/gpt-5.6-sol", "GPT Sol", "0.000003", "0.000015"),
        catalogModel("openai/gpt-5.6-terra", "GPT Terra", "0.000002", "0.00001"),
        catalogModel("google/gemini-3.7-flash", "Gemini Flash", "0.000001", "0.000005"),
    )

    private fun catalogModel(id: String, name: String, prompt: String?, completion: String?) = OpenRouterCatalogModel(
        id = id, displayName = name, contextWindowTokens = 200_000,
        inputModalities = setOf("text"), outputModalities = setOf("text"),
        supportedParameters = setOf("response_format"), promptUsdPerToken = prompt,
        completionUsdPerToken = completion, cacheReadUsdPerToken = if (prompt == null) null else "0",
    )

    private fun OpenRouterCatalogModel.toDescriptor() = ModelDescriptor(
        id = id,
        displayName = displayName,
        capabilities = ModelCapabilities(supportsText = true, supportsVision = false, supportsPdf = false, supportsStreaming = true),
    )

    private fun stableSnapshot(version: String) = ModelRegistrySnapshot(
        id = ModelRegistrySnapshotId("snapshot-$version"), schemaVersion = 1, providerId = ProviderId.OPENROUTER,
        catalogVersion = version, source = RegistrySnapshotSource.OPENROUTER_CATALOG, capturedAt = now,
        verificationStatus = RegistryVerificationStatus.VERIFIED, lastVerifiedAt = now,
        models = listOf(ModelDescriptor("anthropic/claude-sonnet-test", "Stable", ModelCapabilities(true, false, false))),
        presetMappings = ModelPresetId.entries.map { ModelPresetMapping(it, "anthropic/claude-sonnet-test") },
    )

    private class FailingClient(private val failure: OpenRouterCatalogFailure) : OpenRouterRegistryCatalogClient {
        override fun fetchCatalog(): OpenRouterCatalogFetchResult = OpenRouterCatalogFetchResult.Failed(failure)
    }

    private class RecordingStore : ModelRegistrySnapshotStore {
        var persisted: StoredModelRegistrySnapshots? = null
        override fun load(): StoredModelRegistrySnapshots = StoredModelRegistrySnapshots(null, null)
        override fun persist(snapshots: StoredModelRegistrySnapshots): Boolean {
            persisted = snapshots
            return true
        }
    }
}
