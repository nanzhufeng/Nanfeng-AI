package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectExecutionApplicationOwnerContractsTest {
    private val now = Instant.parse("2026-08-16T01:00:00Z")
    private val provider = ProviderRegistryEntry(ProviderHandle("openrouter"), "OpenRouter", ProviderAdapterIdentity.OPENROUTER_OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1")
    private val logical = LogicalModelDescriptor(LogicalModelId("logical.fixture"), "Fixture logical model")
    private val deployment = ModelDeploymentDescriptor(
        ModelDeploymentId("openrouter.fixture"), logical.id, provider.handle, "openrouter/test-v1", ModelDeploymentState.ACTIVE,
        ModelPricing("catalog-v1", "USD", 10, 5),
    )

    @Test fun `Direct resolves one exact deployment and freezes all confirmation scope without Auto routing or credential presence`() {
        var autoCalls = 0
        val credentials = PresenceOnlyCredentialStore(CredentialPresence.PRESENT)
        val owner = owner(InMemoryMultiProviderModelRegistry(listOf(snapshot(deployment))), credentials) { _, _ -> autoCalls += 1; error("Direct must not route") }

        val result = owner.requestConfirmation(request()) as DirectExecutionApplicationResult.Confirmation

        assertEquals(0, autoCalls)
        assertEquals(0, credentials.presenceCalls)
        assertFalse(credentials.loadWasCalled)
        assertEquals(logical.id, result.value.logicalModelId)
        assertEquals(deployment.id, result.value.deploymentId)
        assertEquals(provider.handle, result.value.provider)
        assertEquals("openrouter/test-v1", result.value.providerModelId)
        assertEquals("b".repeat(64), result.value.requestFingerprint)
        assertEquals("a".repeat(64), result.value.contextHash)
        assertEquals(DirectExecutionTextCategory.CURRENT_DRAFT_TEXT_ONLY, result.value.textCategory)
        assertEquals(770, result.value.maximumBudgetMicros)
        assertEquals(
            ConservativeInputBillingBudget.maximumBudgetMicros("十二字本地测试文本", 100, 10, 5),
            result.value.maximumBudgetMicros,
        )
        assertEquals(64, result.value.scopeFingerprint.length)
        assertFalse(result.value.scopeFingerprint.contains("十二字本地测试文本"))
        assertFalse(result.value.acknowledged)
    }

    @Test fun `attachments inactive deployment and unknown price fail before credential presence or any coordinator path`() {
        val credentials = PresenceOnlyCredentialStore(CredentialPresence.PRESENT)
        val attachments = owner(InMemoryMultiProviderModelRegistry(listOf(snapshot(deployment))), credentials)
            .requestConfirmation(request(attachmentCount = 1)) as DirectExecutionApplicationResult.Blocked
        assertEquals(DirectExecutionApplicationBlocker.ATTACHMENTS_NOT_SUPPORTED, attachments.blocker)

        val inactive = deployment.copy(state = ModelDeploymentState.RETIRED)
        val inactiveResult = owner(InMemoryMultiProviderModelRegistry(listOf(snapshot(inactive))), credentials)
            .requestConfirmation(request()) as DirectExecutionApplicationResult.Blocked
        assertEquals(DirectExecutionApplicationBlocker.REGISTRY_REJECTED, inactiveResult.blocker)

        val unknown = deployment.copy(pricing = ModelPricing())
        val unknownResult = owner(InMemoryMultiProviderModelRegistry(listOf(snapshot(unknown))), credentials)
            .requestConfirmation(request()) as DirectExecutionApplicationResult.Blocked
        assertEquals(DirectExecutionApplicationBlocker.PRICE_UNAVAILABLE, unknownResult.blocker)
        assertEquals(0, credentials.presenceCalls)
        assertFalse(credentials.loadWasCalled)
    }

    @Test fun `unchecked expired cancelled or consumed confirmation never reaches credential presence or transport coordination`() {
        val credentials = PresenceOnlyCredentialStore(CredentialPresence.PRESENT)
        val owner = owner(InMemoryMultiProviderModelRegistry(listOf(snapshot(deployment))), credentials)
        val confirmation = (owner.requestConfirmation(request()) as DirectExecutionApplicationResult.Confirmation).value

        assertEquals(DirectExecutionApplicationBlocker.CONFIRMATION_NOT_ACKNOWLEDGED, (owner.confirm(confirmation.id) as DirectExecutionApplicationResult.Blocked).blocker)
        assertEquals(DirectExecutionApplicationBlocker.CONFIRMATION_CONSUMED, (owner.cancel(confirmation.id) as DirectExecutionApplicationResult.Blocked).blocker)
        assertEquals(DirectExecutionApplicationBlocker.CONFIRMATION_CONSUMED, (owner.confirm(confirmation.id) as DirectExecutionApplicationResult.Blocked).blocker)
        assertEquals(0, owner.retainedSensitiveRequestCountForContractTest())
        assertEquals(1, owner.terminalReplayFactCountForContractTest())
        assertEquals(0, credentials.presenceCalls)
        assertFalse(credentials.loadWasCalled)
    }

    @Test fun `missing credential blocks through P3 presence only and never loads a key`() {
        val credentials = PresenceOnlyCredentialStore(CredentialPresence.MISSING)
        val owner = owner(InMemoryMultiProviderModelRegistry(listOf(snapshot(deployment))), credentials)
        val confirmation = (owner.requestConfirmation(request()) as DirectExecutionApplicationResult.Confirmation).value
        owner.setAcknowledgement(confirmation.id, true)

        assertEquals(DirectExecutionApplicationBlocker.PREFLIGHT_BLOCKED, (owner.confirm(confirmation.id) as DirectExecutionApplicationResult.Blocked).blocker)
        assertEquals(1, credentials.presenceCalls)
        assertFalse(credentials.loadWasCalled)
    }

    @Test fun `confirmed scope reuses P3 preflight then the disabled coordinator and can be consumed only once`() {
        var autoCalls = 0
        val credentials = PresenceOnlyCredentialStore(CredentialPresence.PRESENT)
        val owner = owner(InMemoryMultiProviderModelRegistry(listOf(snapshot(deployment))), credentials) { _, _ -> autoCalls += 1; error("Auto must not run") }
        val confirmation = (owner.requestConfirmation(request()) as DirectExecutionApplicationResult.Confirmation).value
        owner.setAcknowledgement(confirmation.id, true)

        val coordinated = owner.confirm(confirmation.id) as DirectExecutionApplicationResult.Coordinated
        val terminal = (coordinated.result as RealTextExecutionCoordinatorResult.Executed).terminal as RealTextExecutionCoordinatorTerminalResult.Failed
        assertEquals("COORDINATOR_USAGE_RESERVATION_REJECTED", terminal.safeErrorCode)
        assertEquals(1, credentials.presenceCalls)
        assertFalse(credentials.loadWasCalled)
        assertEquals(0, autoCalls)
        assertEquals(DirectExecutionApplicationBlocker.CONFIRMATION_CONSUMED, (owner.confirm(confirmation.id) as DirectExecutionApplicationResult.Blocked).blocker)
        assertEquals(0, owner.retainedSensitiveRequestCountForContractTest())
    }

    @Test fun `registry upgrade after acknowledgement invalidates the confirmation before credential presence`() {
        val credentials = PresenceOnlyCredentialStore(CredentialPresence.PRESENT)
        val registry = InMemoryMultiProviderModelRegistry(listOf(snapshot(deployment)))
        val owner = owner(registry, credentials)
        val confirmation = (owner.requestConfirmation(request()) as DirectExecutionApplicationResult.Confirmation).value
        owner.setAcknowledgement(confirmation.id, true)
        registry.publish(snapshot(deployment.copy(pricing = ModelPricing("catalog-v2", "USD", 10, 5))))

        assertEquals(DirectExecutionApplicationBlocker.CONFIRMATION_SCOPE_CHANGED, (owner.confirm(confirmation.id) as DirectExecutionApplicationResult.Blocked).blocker)
        assertEquals(0, credentials.presenceCalls)
        assertFalse(credentials.loadWasCalled)
        assertEquals(0, owner.retainedSensitiveRequestCountForContractTest())
    }

    @Test fun `scope fingerprint binds request context text and budget without disclosing text`() {
        val credentials = PresenceOnlyCredentialStore(CredentialPresence.PRESENT)
        val owner = owner(InMemoryMultiProviderModelRegistry(listOf(snapshot(deployment))), credentials)
        val original = (owner.requestConfirmation(request()) as DirectExecutionApplicationResult.Confirmation).value
        val changed = (owner.requestConfirmation(
            request().copy(
                requestId = OrchestrationRequestId("direct-fixture-2"),
                context = CanonicalContextSnapshotRef(CanonicalContextSnapshotId("context-fixture-2"), "c".repeat(64), 2),
                text = ProviderTransportEphemeralTextInput("🙂 不同文本"),
                outputTokenLimit = 101,
            ),
        ) as DirectExecutionApplicationResult.Confirmation).value

        assertFalse(original.scopeFingerprint == changed.scopeFingerprint)
        assertFalse(changed.scopeFingerprint.contains("不同文本"))
        assertEquals(2, owner.retainedSensitiveRequestCountForContractTest())
    }

    @Test fun `expiration and preflight terminal release original text while retaining only bounded replay facts`() {
        val mutableClock = MutableClock(now)
        val missingCredentials = PresenceOnlyCredentialStore(CredentialPresence.MISSING)
        val missingOwner = owner(InMemoryMultiProviderModelRegistry(listOf(snapshot(deployment))), missingCredentials, clock = mutableClock)
        val missing = (missingOwner.requestConfirmation(request()) as DirectExecutionApplicationResult.Confirmation).value
        missingOwner.setAcknowledgement(missing.id, true)
        assertEquals(DirectExecutionApplicationBlocker.PREFLIGHT_BLOCKED, (missingOwner.confirm(missing.id) as DirectExecutionApplicationResult.Blocked).blocker)
        assertEquals(0, missingOwner.retainedSensitiveRequestCountForContractTest())
        assertEquals(DirectExecutionApplicationBlocker.CONFIRMATION_CONSUMED, (missingOwner.confirm(missing.id) as DirectExecutionApplicationResult.Blocked).blocker)

        val expiryOwner = owner(InMemoryMultiProviderModelRegistry(listOf(snapshot(deployment))), PresenceOnlyCredentialStore(CredentialPresence.PRESENT), clock = mutableClock)
        val expiring = (expiryOwner.requestConfirmation(request()) as DirectExecutionApplicationResult.Confirmation).value
        mutableClock.instant = now.plusSeconds(DirectExecutionApplicationOwner.CONFIRMATION_TTL_MINUTES * 60 + 1)
        assertEquals(DirectExecutionApplicationBlocker.CONFIRMATION_EXPIRED, (expiryOwner.setAcknowledgement(expiring.id, true) as DirectExecutionApplicationResult.Blocked).blocker)
        assertEquals(0, expiryOwner.retainedSensitiveRequestCountForContractTest())
        assertEquals(1, expiryOwner.terminalReplayFactCountForContractTest())
    }

    private fun owner(
        registry: MultiProviderModelRegistry,
        credentials: PresenceOnlyCredentialStore,
        clock: Clock = Clock.fixed(now, ZoneOffset.UTC),
        auto: AutoRoutingPort = AutoRoutingPort { _, _ -> error("Auto must not run") },
    ): DirectExecutionApplicationOwner {
        val model = ModelDescriptor(deployment.providerModelId, "Fixture", ModelCapabilities(true, false, true), pricing = deployment.pricing)
        val legacy = ModelRegistrySnapshot(
            ModelRegistrySnapshotId("legacy-snapshot"), 1, ProviderId.OPENROUTER, "catalog-v1", RegistrySnapshotSource.LOCAL_FIXTURE,
            now, RegistryVerificationStatus.VERIFIED, now, listOf(model), listOf(ModelPresetMapping(ModelPresetId.GPT_5_6_TERRA, model.id)), catalogSha256 = "c".repeat(64),
        )
        val settings = object : ModelServiceSettingsRepository {
            override fun load(providerId: ProviderId) = ProviderSettings(ProviderId.OPENROUTER, true, ModelPresetId.GPT_5_6_TERRA)
            override fun save(settings: ProviderSettings) = settings
        }
        return DirectExecutionApplicationOwner(
            registry, MultiModelOrchestrator(auto),
            RealTextExecutionPreflightOrchestrator(settings, credentials, InMemoryVersionedModelRegistry(listOf(legacy)), clock),
            RealTextExecutionCoordinator(now = { now }), clock,
        )
    }

    private fun request(attachmentCount: Int = 0) = DirectExecutionApplicationRequest(
        OrchestrationRequestId("direct-fixture"),
        CanonicalContextSnapshotRef(CanonicalContextSnapshotId("context-fixture"), "a".repeat(64), 1),
        ModelDeploymentSelection(logical.id, deployment.id, provider.handle),
        ProviderTransportEphemeralTextInput("十二字本地测试文本"),
        ConversationRealTextExecutionRequest(
            ConversationRealTextExecutionId("execution"), "idempotency", "b".repeat(64), ConversationId("conversation"),
            MessageNodeId("user"), MessageNodeId("assistant"), InvocationId("invocation"), ProviderAttemptId("attempt"), now,
        ),
        ModelPresetId.GPT_5_6_TERRA, 100, attachmentCount, UsageLedgerSource.ANDROID_LOCAL,
    )

    private fun snapshot(value: ModelDeploymentDescriptor) = MultiProviderRegistrySnapshot(
        MultiProviderRegistrySnapshotId("snapshot-${value.pricing.priceVersion ?: "unknown"}-${value.state}"), 1,
        value.pricing.priceVersion ?: "unknown", now, listOf(provider), listOf(logical), listOf(value),
    )

    private class PresenceOnlyCredentialStore(private val presence: CredentialPresence) : ProviderCredentialStore {
        var presenceCalls = 0
        var loadWasCalled = false
        override fun credentialPresence(providerId: ProviderId): CredentialPresence = presence.also { presenceCalls += 1 }
        override fun hasCredential(providerId: ProviderId): Boolean = error("Presence-only boundary must not call hasCredential")
        override fun saveCredential(providerId: ProviderId, credential: CharArray): Boolean = error("No credential writes")
        override fun loadCredential(providerId: ProviderId): CharArray? {
            loadWasCalled = true
            error("No credential reads")
        }
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant(): Instant = instant
    }
}
