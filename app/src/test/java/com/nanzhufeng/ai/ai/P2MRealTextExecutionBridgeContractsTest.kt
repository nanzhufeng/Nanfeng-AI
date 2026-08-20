package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.CredentialPresence
import com.nanzhufeng.ai.domain.CredentialState
import com.nanzhufeng.ai.domain.InMemoryVersionedModelRegistry
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelDescriptor
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelPresetMapping
import com.nanzhufeng.ai.domain.ModelPricing
import com.nanzhufeng.ai.domain.ModelRegistrySnapshot
import com.nanzhufeng.ai.domain.ModelRegistrySnapshotId
import com.nanzhufeng.ai.domain.ModelServiceConfiguration
import com.nanzhufeng.ai.domain.ModelServiceSettingsRepository
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderSettings
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinator
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorResult
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorTerminalResult
import com.nanzhufeng.ai.domain.RealTextExecutionPreflightOrchestrator
import com.nanzhufeng.ai.domain.RegistrySnapshotSource
import com.nanzhufeng.ai.domain.RegistryVerificationStatus
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2MRealTextExecutionBridgeContractsTest {
    private val now = Instant.parse("2026-08-15T15:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `existing confirmed P2M readiness reaches preflight then default coordinator rejects before transport`() {
        val credentials = PresenceOnlyCredentials(CredentialPresence.PRESENT)
        val bridge = bridge(credentials)
        val ready = readiness()

        val result = bridge.coordinate(ready, P2MExistingVisibleConfirmation(ready.spec.fingerprint(), now))
        assertTrue(result is P2MRealTextExecutionBridgeResult.Coordinated)
        val coordinated = (result as P2MRealTextExecutionBridgeResult.Coordinated).result
        assertTrue(coordinated is RealTextExecutionCoordinatorResult.Executed)
        val terminal = (coordinated as RealTextExecutionCoordinatorResult.Executed).terminal
        assertTrue(terminal is RealTextExecutionCoordinatorTerminalResult.Failed)
        assertEquals("COORDINATOR_USAGE_RESERVATION_REJECTED", (terminal as RealTextExecutionCoordinatorTerminalResult.Failed).safeErrorCode)
        assertTrue(credentials.presenceWasCalled)
        assertFalse(credentials.loadWasCalled)
    }

    @Test fun `missing mismatched and not-ready inputs fail closed before credential presence`() {
        val credentials = PresenceOnlyCredentials(CredentialPresence.PRESENT)
        val bridge = bridge(credentials)
        val ready = readiness()

        assertEquals(
            P2MRealTextExecutionBridgeResult.Blocked(P2MRealTextExecutionBridgeBlocker.VISIBLE_CONFIRMATION_REQUIRED),
            bridge.coordinate(ready, null),
        )
        assertEquals(
            P2MRealTextExecutionBridgeResult.Blocked(P2MRealTextExecutionBridgeBlocker.VISIBLE_CONFIRMATION_SCOPE_MISMATCH),
            bridge.coordinate(ready, P2MExistingVisibleConfirmation("b".repeat(64), now)),
        )
        assertEquals(
            P2MRealTextExecutionBridgeResult.Blocked(P2MRealTextExecutionBridgeBlocker.P2M_READINESS_REQUIRED),
            bridge.coordinate(P2MRealServiceReadiness.Blocked("fixture"), P2MExistingVisibleConfirmation("a".repeat(64), now)),
        )
        assertFalse(credentials.presenceWasCalled)
        assertFalse(credentials.loadWasCalled)
    }

    @Test fun `bridge has no attachment key or HTTP surface and executor calls it before legacy dry run`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ai/P2MRealTextExecutionBridge.kt").readText()
        val executor = File("src/main/java/com/nanzhufeng/ai/ai/RealServiceAcceptance.kt").readText()
        val container = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()
        assertFalse(source.contains("loadCredential"))
        assertFalse(source.contains("HttpsURLConnection"))
        assertFalse(source.contains("HttpClient"))
        assertFalse(source.contains("attachments = listOf"))
        assertTrue(executor.indexOf("realTextExecutionBridge?.coordinate") < executor.lastIndexOf("RealServiceDryRunPreflight("))
        assertTrue(container.contains("RealTextExecutionCoordinator(now = clock::instant)"))
        assertFalse(source.contains("UsageLedgerRepository"))
        assertFalse(container.contains("RealTextExecutionUsageReservationPort"))
    }

    private fun bridge(credentials: PresenceOnlyCredentials): P2MRealTextExecutionBridge {
        val registry = registry()
        val settings = object : ModelServiceSettingsRepository {
            override fun load(providerId: ProviderId) = ProviderSettings(ProviderId.OPENROUTER, true, ModelPresetId.CLAUDE_SONNET_5)
            override fun save(settings: ProviderSettings) = settings
        }
        return P2MRealTextExecutionBridge(
            RealTextExecutionPreflightOrchestrator(settings, credentials, registry, clock),
            RealTextExecutionCoordinator(now = clock::instant),
            clock,
        )
    }

    private fun readiness(): P2MRealServiceReadiness.Ready {
        val registry = registry()
        val model = registry.resolve(ProviderId.OPENROUTER, ModelPresetId.CLAUDE_SONNET_5)
            .let { it as com.nanzhufeng.ai.domain.ModelRegistryResolution.Resolved }.model
        val configuration = ModelServiceConfiguration(
            NanfengModelServiceCatalog.openRouter,
            ProviderSettings(ProviderId.OPENROUTER, true, ModelPresetId.CLAUDE_SONNET_5),
            NanfengModelServiceCatalog.preset(ModelPresetId.CLAUDE_SONNET_5),
            CredentialState.STORED,
        )
        return P2MRealServiceReadiness.Ready(configuration, requireNotNull(P2MOpenRouterTextAcceptance.spec(configuration, registry)), model)
    }

    private fun registry() = InMemoryVersionedModelRegistry(listOf(ModelRegistrySnapshot(
        id = ModelRegistrySnapshotId("p2m-bridge-snapshot"), schemaVersion = 1, providerId = ProviderId.OPENROUTER,
        catalogVersion = "p2m-bridge-catalog", source = RegistrySnapshotSource.LOCAL_FIXTURE, capturedAt = now,
        verificationStatus = RegistryVerificationStatus.VERIFIED, lastVerifiedAt = now,
        models = listOf(ModelDescriptor(
            "anthropic/p2m-bridge", "P2M Bridge", ModelCapabilities(true, false, false, true),
            pricing = ModelPricing("p2m-bridge-price", "USD", 1, 1),
        )),
        presetMappings = listOf(ModelPresetMapping(ModelPresetId.CLAUDE_SONNET_5, "anthropic/p2m-bridge")),
        catalogSha256 = "a".repeat(64),
    )))

    private class PresenceOnlyCredentials(private val presence: CredentialPresence) : ProviderCredentialStore {
        var presenceWasCalled = false
        var loadWasCalled = false
        override fun credentialPresence(providerId: ProviderId): CredentialPresence { presenceWasCalled = true; return presence }
        override fun hasCredential(providerId: ProviderId): Boolean = error("Bridge must use credentialPresence only.")
        override fun saveCredential(providerId: ProviderId, credential: CharArray): Boolean = error("Bridge must not save a credential.")
        override fun loadCredential(providerId: ProviderId): CharArray? { loadWasCalled = true; error("Bridge must not load a credential.") }
    }
}
