package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.InMemoryVersionedModelRegistry
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelDescriptor
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelPresetMapping
import com.nanzhufeng.ai.domain.ModelPricing
import com.nanzhufeng.ai.domain.ModelServiceConfiguration
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog
import com.nanzhufeng.ai.domain.ProviderSettings
import com.nanzhufeng.ai.domain.CredentialState
import com.nanzhufeng.ai.domain.ModelRegistrySnapshot
import com.nanzhufeng.ai.domain.ModelRegistrySnapshotId
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.RegistrySnapshotSource
import com.nanzhufeng.ai.domain.RegistryVerificationStatus
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

class P2LRealServiceAcceptanceContractsTest {
    private val now = Instant.parse("2026-08-12T14:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `fake dry run passes for exact synthetic text without key bytes authorization network or ledger`() {
        val spec = spec()
        val report = preflight(spec).execute(spec, consent(spec))

        assertTrue(report.passed)
        assertEquals(RealServiceCostEstimateState.KNOWN_WITHIN_CAP, report.costEstimateState)
        assertEquals(436L, report.estimatedCostMicros)
        assertFalse(report.authorizationConstructed)
        assertEquals(0, report.networkRequestsConstructed)
        assertFalse(report.credentialBytesRead)
        assertFalse(report.toString().contains(P2LSyntheticFixtures.textContent(P2LSyntheticFixtures.text.id)!!))
        assertFalse(report.toString().contains("test-key-not-real"))
        assertTrue(report.checks.all(RealServiceDryRunCheck::passed))
    }

    @Test
    fun `budget tamper expired registry and unsupported capability all block before any real attempt`() {
        val budgetSpec = spec(costCapMicros = 100)
        val budget = preflight(budgetSpec).execute(budgetSpec, consent(budgetSpec))
        assertFalse(budget.passed)
        assertTrue(RealServiceDryRunIssue.BUDGET_CAP_EXCEEDED in budget.issues)

        val alteredFixture = P2LSyntheticFixtures.text.copy(sha256 = "b".repeat(64))
        val tamperedSpec = spec(fixture = alteredFixture)
        val tampered = preflight(tamperedSpec).execute(tamperedSpec, consent(tamperedSpec))
        assertFalse(tampered.passed)
        assertTrue(RealServiceDryRunIssue.FIXTURE_TAMPERED_OR_UNSUPPORTED in tampered.issues)

        val staleSpec = spec(registryCatalogSha256 = "c".repeat(64))
        val stale = preflight(staleSpec).execute(staleSpec, consent(staleSpec))
        assertFalse(stale.passed)
        assertTrue(RealServiceDryRunIssue.REGISTRY_MISSING_OR_STALE in stale.issues)

        val noStructured = preflight(spec(), structured = false).execute(spec(), consent(spec()))
        assertFalse(noStructured.passed)
        assertTrue(RealServiceDryRunIssue.MODEL_UNAVAILABLE_OR_CAPABILITY_MISMATCH in noStructured.issues)
        assertEquals(0, noStructured.networkRequestsConstructed)
    }

    @Test
    fun `credential boundary only receives presence and missing credential is a blocked offline report`() {
        val spec = spec()
        var presenceCalls = 0
        val report = RealServiceDryRunPreflight(
            registry(spec),
            CredentialPresenceProbe { presenceCalls += 1; com.nanzhufeng.ai.domain.CredentialPresence.MISSING },
            OpenRouterEgressPolicy.Disabled,
            clock = clock,
        ).execute(spec, consent(spec))

        assertEquals(1, presenceCalls)
        assertFalse(report.passed)
        assertTrue(RealServiceDryRunIssue.CREDENTIAL_NOT_PRESENT in report.issues)
        assertFalse(report.credentialBytesRead)
        assertEquals(0, report.networkRequestsConstructed)
    }

    @Test
    fun `single use nonce is exact spec only and remains consumed after process reconstruction`() {
        val directory = Files.createTempDirectory("nanfeng-ai-p2l-token").toFile()
        val spec = spec()
        val initialStore = FileRealServiceAcceptanceTokenStore(directory)
        val service = RealServiceAcceptanceTokenService(initialStore, clock)
        val token = service.issue(spec, consent(spec), now.plusSeconds(60))
        assertNotNull(token)
        assertEquals(RealServiceTokenConsumeResult.SpecMismatch, service.consume(spec.copy(runId = "p2l-other-run"), token!!.nonce))
        assertEquals(RealServiceTokenConsumeResult.Consumed, service.consume(spec, token.nonce))

        val rebuilt = RealServiceAcceptanceTokenService(FileRealServiceAcceptanceTokenStore(directory), clock)
        assertEquals(RealServiceTokenConsumeResult.AlreadyConsumed, rebuilt.consume(spec, token.nonce))
        assertEquals(RealServiceTokenState.CONSUMED, FileRealServiceAcceptanceTokenStore(directory).find(token.nonce)?.state)
    }

    @Test
    fun `expired or invalid consent cannot issue a token and image remains deliberately unavailable in p2k`() {
        val spec = spec()
        val tokens = RealServiceAcceptanceTokenService(InMemoryRealServiceAcceptanceTokenStore(), clock)
        assertNull(tokens.issue(spec, consent(spec), now))
        assertNull(tokens.issue(spec, consent(spec).copy(explicitSingleUseAccepted = false), now.plusSeconds(60)))

        val imageSpec = spec(fixture = P2LSyntheticFixtures.image)
        val report = preflight(imageSpec).execute(imageSpec, consent(imageSpec))
        assertFalse(report.passed)
        assertTrue(RealServiceDryRunIssue.CONTENT_UNSAFE_OR_OVERSIZE in report.issues)
        assertEquals("96x64", report.fixtureDimensions)
        assertEquals("image/svg+xml", report.fixtureMimeType)
    }

    @Test
    fun `ui readiness never reads credentials and distinguishes incomplete from waiting authorization`() {
        assertEquals(
            RealServiceAcceptanceUiState.PREPARATION_INCOMPLETE,
            LoadRealServiceAcceptanceUiStatusUseCase(InMemoryVersionedModelRegistry()).execute().state,
        )
        val waiting = LoadRealServiceAcceptanceUiStatusUseCase(registry(spec())).execute()
        assertEquals(RealServiceAcceptanceUiState.WAITING_USER_AUTHORIZATION, waiting.state)
        assertEquals(listOf("p2l-text-organize-v1", "p2l-image-shapes-v1"), waiting.fixtureIds)
    }

    @Test
    fun `p2m v2 binds a new exact synthetic text runspec to the saved enabled preset and cannot reissue its nonce`() {
        val registry = registry(spec())
        val configuration = ModelServiceConfiguration(
            provider = NanfengModelServiceCatalog.openRouter,
            settings = ProviderSettings(ProviderId.OPENROUTER, enabled = true, presetId = ModelPresetId.CLAUDE_SONNET_5),
            preset = NanfengModelServiceCatalog.preset(ModelPresetId.CLAUDE_SONNET_5),
            credentialState = CredentialState.STORED,
        )
        val spec = requireNotNull(P2MOpenRouterTextAcceptance.spec(configuration, registry))
        assertEquals("anthropic/p2l-fixture", spec.modelId)
        assertEquals("snapshot-p2l", spec.registrySnapshotId)
        assertEquals("a".repeat(64), spec.registryCatalogSha256)
        assertEquals("p2m-openrouter-text-v2", spec.runId)
        assertEquals(P2MSyntheticFixtures.textV2, spec.fixture)
        assertEquals("fa005b85661eb71ff1b63b2e5b5804d14ae1f71a0a430b28769fcd2635907cae", spec.fixture.sha256)
        assertEquals(0, spec.retryCount)
        assertEquals(10_000L, spec.costCapMicros)

        val tokens = RealServiceAcceptanceTokenService(InMemoryRealServiceAcceptanceTokenStore(), clock)
        val nonce = P2MOpenRouterTextAcceptance.nonce()
        assertNotNull(tokens.issue(spec, P2MOpenRouterTextAcceptance.consent(spec, now), now.plusSeconds(60), nonce))
        assertNull(tokens.issue(spec, P2MOpenRouterTextAcceptance.consent(spec, now), now.plusSeconds(60), nonce))
        assertEquals(RealServiceTokenConsumeResult.Consumed, tokens.consume(spec, nonce))
        assertEquals(RealServiceTokenConsumeResult.AlreadyConsumed, tokens.consume(spec, nonce))
    }

    @Test
    fun `p2m v2 dry run accepts only its new fixture with zero egress and legacy identifiers remain readable`() {
        val registry = registry(spec())
        val configuration = ModelServiceConfiguration(
            provider = NanfengModelServiceCatalog.openRouter,
            settings = ProviderSettings(ProviderId.OPENROUTER, enabled = true, presetId = ModelPresetId.CLAUDE_SONNET_5),
            preset = NanfengModelServiceCatalog.preset(ModelPresetId.CLAUDE_SONNET_5),
            credentialState = CredentialState.STORED,
        )
        val p2mSpec = requireNotNull(P2MOpenRouterTextAcceptance.spec(configuration, registry))
        val report = RealServiceDryRunPreflight(
            registry, CredentialPresenceProbe { com.nanzhufeng.ai.domain.CredentialPresence.PRESENT },
            OpenRouterEgressPolicy.Disabled, clock = clock,
        ).execute(p2mSpec, P2MOpenRouterTextAcceptance.consent(p2mSpec, now))

        assertTrue(report.passed)
        assertFalse(report.authorizationConstructed)
        assertEquals(0, report.networkRequestsConstructed)
        assertFalse(report.credentialBytesRead)
        assertTrue(P2MOpenRouterTextAcceptance.isKnownTaskId(P2MOpenRouterTextAcceptance.LEGACY_RUN_ID))
        assertTrue(P2MOpenRouterTextAcceptance.isKnownTaskId(P2MOpenRouterTextAcceptance.LEGACY_TASK_ID))
        assertTrue(P2MOpenRouterTextAcceptance.isKnownTaskId(P2MOpenRouterTextAcceptance.TASK_ID))
    }

    @Test
    fun `p2m refuses disabled or credential-missing saved configurations before any nonce`() {
        val registry = registry(spec())
        val disabled = ModelServiceConfiguration(
            provider = NanfengModelServiceCatalog.openRouter,
            settings = ProviderSettings(ProviderId.OPENROUTER, enabled = false, presetId = ModelPresetId.CLAUDE_HAIKU_4_5),
            preset = NanfengModelServiceCatalog.preset(ModelPresetId.CLAUDE_HAIKU_4_5),
            credentialState = CredentialState.STORED,
        )
        val missing = disabled.copy(settings = disabled.settings.copy(enabled = true), credentialState = CredentialState.MISSING)
        assertNull(P2MOpenRouterTextAcceptance.spec(disabled, registry))
        assertNull(P2MOpenRouterTextAcceptance.spec(missing, registry))
    }

    private fun preflight(spec: RealServiceRunSpec, structured: Boolean = true) = RealServiceDryRunPreflight(
        registry(spec, structured), CredentialPresenceProbe { com.nanzhufeng.ai.domain.CredentialPresence.PRESENT },
        OpenRouterEgressPolicy.Disabled, clock = clock,
    )

    private fun registry(spec: RealServiceRunSpec, structured: Boolean = true) = InMemoryVersionedModelRegistry(listOf(snapshot(spec, structured)))

    private fun snapshot(spec: RealServiceRunSpec, structured: Boolean) = ModelRegistrySnapshot(
        id = ModelRegistrySnapshotId("snapshot-p2l"), schemaVersion = 1, providerId = ProviderId.OPENROUTER,
        catalogVersion = "catalog-p2l", source = RegistrySnapshotSource.OPENROUTER_CATALOG, capturedAt = now,
        verificationStatus = RegistryVerificationStatus.VERIFIED, lastVerifiedAt = now,
        models = listOf(ModelDescriptor(
            id = "anthropic/p2l-fixture", displayName = "P2-L Fixture", contextWindowTokens = 4096,
            capabilities = ModelCapabilities(true, true, false, supportsStructuredOutput = structured),
            pricing = ModelPricing("price-p2l", "USD", 1, 1),
        )),
        presetMappings = ModelPresetId.entries.map { ModelPresetMapping(it, "anthropic/p2l-fixture") },
        catalogSha256 = "a".repeat(64),
    )

    private fun spec(
        fixture: RealServiceFixtureDescriptor = P2LSyntheticFixtures.text,
        registryCatalogSha256: String = "a".repeat(64),
        costCapMicros: Long = RealServiceAcceptanceContract.DEFAULT_COST_CAP_MICROS,
    ) = RealServiceRunSpec(
        runId = "p2l-text-run-v1", providerId = ProviderId.OPENROUTER, modelId = "anthropic/p2l-fixture",
        registrySnapshotId = "snapshot-p2l", registryCatalogSha256 = registryCatalogSha256, fixture = fixture,
        maxInputTokens = RealServiceAcceptanceContract.DEFAULT_MAX_INPUT_TOKENS,
        maxOutputTokens = RealServiceAcceptanceContract.DEFAULT_MAX_OUTPUT_TOKENS,
        costCapMicros = costCapMicros, currencyCode = "USD", consentFingerprint = "d".repeat(64),
        credentialHandle = RealServiceAcceptanceContract.CREDENTIAL_HANDLE,
        connectTimeoutMs = RealServiceAcceptanceContract.DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs = RealServiceAcceptanceContract.DEFAULT_READ_TIMEOUT_MS,
        retryCount = RealServiceAcceptanceContract.DEFAULT_RETRY_COUNT,
        expectedState = RealServiceExpectedState(),
    )

    private fun consent(spec: RealServiceRunSpec) = RealServiceAcceptanceConsent(
        runSpecFingerprint = spec.fingerprint(), consentFingerprint = spec.consentFingerprint,
        acceptedAt = now, explicitSingleUseAccepted = true,
    )
}
