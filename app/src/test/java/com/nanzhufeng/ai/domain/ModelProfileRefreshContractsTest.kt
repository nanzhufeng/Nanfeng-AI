package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProfileRefreshContractsTest {
    @Test fun `stale direct profile is refreshed from provider model IDs without replacing capabilities`() {
        val clock = Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC)
        val directory = FakeDirectory(mapOf(
            ModelPresetId.QWEN_3_7_PLUS to profile("qwen-plus", ModelHealth.AVAILABLE, Instant.EPOCH),
            ModelPresetId.QWEN_3_8_MAX to profile("qwen-max", ModelHealth.AVAILABLE, Instant.EPOCH),
        ))
        val result = RefreshModelProfilesUseCase(directory, FakeCredentials(), ProviderModelListClient { _, _, _ -> ProviderModelListResult.Available(setOf("qwen-plus")) }, clock)
            .refreshIfStale(ProviderId.QWEN, ModelPresetId.QWEN_3_7_PLUS, "https://example.invalid/v1")

        assertEquals(ModelProfileRefreshResult.UPDATED, result)
        assertEquals(ModelHealth.UNKNOWN, directory.profile(ModelPresetId.QWEN_3_7_PLUS)?.health)
        assertEquals(ModelHealth.UNAVAILABLE, directory.profile(ModelPresetId.QWEN_3_8_MAX)?.health)
        assertTrue(directory.profile(ModelPresetId.QWEN_3_7_PLUS)?.capabilities?.supportsVideo == true)
        assertTrue(directory.replaced)
    }

    @Test fun `fresh cache does not spend a credential on a directory request`() {
        val directory = FakeDirectory(mapOf(ModelPresetId.DEEPSEEK_V4_PRO to profile("deepseek", ModelHealth.UNKNOWN, Instant.now())))
        val credentials = FakeCredentials()
        val result = RefreshModelProfilesUseCase(directory, credentials, ProviderModelListClient { _, _, _ -> error("must not call") }, Clock.systemUTC())
            .refreshIfStale(ProviderId.DEEPSEEK, ModelPresetId.DEEPSEEK_V4_PRO, "https://example.invalid/v1")

        assertEquals(ModelProfileRefreshResult.FRESH, result)
        assertFalse(credentials.loaded)
    }

    @Test fun `explicit data supplied replacement ID can refresh without a code change`() {
        val clock = Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC)
        val directory = FakeDirectory(mapOf(
            ModelPresetId.QWEN_3_7_PLUS to profile("qwen-plus", ModelHealth.UNKNOWN, Instant.EPOCH)
                .copy(alternateModelIds = setOf("qwen-plus-latest")),
        ))
        RefreshModelProfilesUseCase(directory, FakeCredentials(), ProviderModelListClient { _, _, _ ->
            ProviderModelListResult.Available(setOf("qwen-plus-latest"))
        }, clock).refreshIfStale(ProviderId.QWEN, ModelPresetId.QWEN_3_7_PLUS, "https://example.invalid/v1")

        assertEquals("qwen-plus-latest", directory.profile(ModelPresetId.QWEN_3_7_PLUS)?.modelId)
        assertEquals(ModelHealth.UNKNOWN, directory.profile(ModelPresetId.QWEN_3_7_PLUS)?.health)
    }

    private fun profile(id: String, health: ModelHealth, updated: Instant) = ResolvedModel(
        ProviderId.QWEN, id, id,
        ModelCapabilities(true, true, true, supportsPdf = true, supportsVideo = true),
        131_072, health, updated, null, 8_192,
    )

    private class FakeDirectory(initial: Map<ModelPresetId, ResolvedModel>) : ModelProfileDirectory {
        private var values = initial
        var replaced = false
        override fun profile(presetId: ModelPresetId) = values[presetId]
        override fun profiles() = values
        override fun replaceIfNewer(profiles: Map<ModelPresetId, ResolvedModel>): Boolean { values = profiles; replaced = true; return true }
    }
    private class FakeCredentials : ProviderCredentialStore {
        var loaded = false
        override fun hasCredential(providerId: ProviderId) = true
        override fun saveCredential(providerId: ProviderId, credential: CharArray) = true
        override fun loadCredential(providerId: ProviderId): CharArray { loaded = true; return "credential".toCharArray() }
    }
}
