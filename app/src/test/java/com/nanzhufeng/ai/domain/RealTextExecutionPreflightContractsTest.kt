package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealTextExecutionPreflightContractsTest {
    private val now = Instant.parse("2026-08-15T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `configured presence verified preset fee and reservation produce a safe ready plan without reading a key`() {
        val credentials = PresenceOnlyCredentialStore(CredentialPresence.PRESENT)
        val result = orchestrator(credentials).preflight(request()) as RealTextExecutionPreflightResult.Ready

        assertTrue(credentials.loadWasCalled.not())
        assertEquals("openrouter", result.plan.providerHandle.value)
        assertEquals("openrouter/test-v1", result.plan.modelId.value)
        assertEquals(27, result.plan.usageReservation.conservativeInputTokenUpperBound)
        assertEquals(770, result.plan.usageReservation.budgetMicros)
        assertEquals(0, result.plan.attachments.size)
        assertTrue(RealTextExecutionReadyPlan::class.java.declaredFields.none {
            it.name.contains("text", true) || it.name.contains("prompt", true) || it.name.contains("credential", true) ||
                it.name.contains("key", true) || it.name.contains("authorization", true) || it.name.contains("payload", true)
        })
    }

    @Test fun `absence disabled preset and model mismatches fail closed before a ready plan`() {
        val missing = orchestrator(PresenceOnlyCredentialStore(CredentialPresence.MISSING)).preflight(request())
        assertBlocked(missing, RealTextPreflightBlocker.CREDENTIAL_NOT_CONFIGURED)

        val disabled = orchestrator(PresenceOnlyCredentialStore(CredentialPresence.PRESENT), enabled = false).preflight(request())
        assertBlocked(disabled, RealTextPreflightBlocker.PROVIDER_DISABLED)

        val mismatch = orchestrator(PresenceOnlyCredentialStore(CredentialPresence.PRESENT), modelId = "openrouter/other-v1").preflight(request())
        assertBlocked(mismatch, RealTextPreflightBlocker.MODEL_ROUTE_MISMATCH)
    }

    @Test fun `text fee and unknown price are blocked without constructing a reservation`() {
        assertBlocked(
            orchestrator(PresenceOnlyCredentialStore(CredentialPresence.PRESENT)).preflight(request(text = "Bearer secret-not-allowed")),
            RealTextPreflightBlocker.TEXT_INVALID,
        )
        assertBlocked(
            orchestrator(PresenceOnlyCredentialStore(CredentialPresence.PRESENT)).preflight(request(feeMaximum = 769)),
            RealTextPreflightBlocker.FEE_CONFIRMATION_INSUFFICIENT,
        )
        assertBlocked(
            orchestrator(PresenceOnlyCredentialStore(CredentialPresence.PRESENT), knownPrice = false).preflight(request()),
            RealTextPreflightBlocker.PRICE_UNAVAILABLE,
        )
    }

    @Test fun `attachment requires an active matching safe authorization but preflight never consumes it`() {
        val intent = attachmentIntent()
        val absent = orchestrator(PresenceOnlyCredentialStore(CredentialPresence.PRESENT)).preflight(
            request(attachments = listOf(RealTextAttachmentPreflightProof(intent, null))),
        )
        assertBlocked(absent, RealTextPreflightBlocker.ATTACHMENT_AUTHORIZATION_REQUIRED)

        val owner = AttachmentEgressAuthorizationOwner(clock)
        val consent = ExplicitAttachmentEgressConsent(
            "attachment-consent", now, intent.fingerprint(), AttachmentEgressFeeConfirmation("attachment-fee", now, "p1", "USD", 1),
        )
        val summary = (owner.authorize(AttachmentEgressAuthorizationRequest(
            AttachmentEgressAuthorizationId("attachment-auth"), "attachment-replay", intent, consent, now.plusSeconds(60),
        )) as AttachmentEgressAuthorizationResult.Authorized).summary
        val ready = orchestrator(PresenceOnlyCredentialStore(CredentialPresence.PRESENT)).preflight(
            request(attachments = listOf(RealTextAttachmentPreflightProof(intent, summary))),
        ) as RealTextExecutionPreflightResult.Ready
        assertEquals(1, ready.plan.attachments.size)
        assertTrue(owner.consume(summary.authorizationId) is AttachmentEgressAuthorizationResult.Authorized)
    }

    @Test fun `cancellation and execution binding mismatch stop before configured credential is queried`() {
        val credentials = PresenceOnlyCredentialStore(CredentialPresence.PRESENT)
        assertBlocked(orchestrator(credentials).preflight(request(), object : RealTextPreflightCancellation {
            override fun isCancelled(): Boolean = true
        }), RealTextPreflightBlocker.CANCELLED)
        assertFalse(credentials.presenceWasCalled)

        val badTransport = request().transportRequest.copy(execution = ConversationRealTextExecutionTransportHandle(
            ConversationRealTextExecutionId("different"), InvocationId("different"), ProviderAttemptId("different"), "c".repeat(64),
        ))
        assertBlocked(orchestrator(credentials).preflight(request().copy(transportRequest = badTransport)), RealTextPreflightBlocker.EXECUTION_BINDING_MISMATCH)
    }

    @Test fun `UTF-8 conservative billing ceiling covers CJK whitespace emoji and arithmetic overflow`() {
        assertEquals(27, ConservativeInputBillingBudget.inputTokenUpperBound("十二字本地测试文本"))
        assertEquals(6, ConservativeInputBillingBudget.inputTokenUpperBound(" \t🙂"))
        assertEquals(4, ConservativeInputBillingBudget.inputTokenUpperBound("🙂"))
        assertEquals(
            null,
            ConservativeInputBillingBudget.maximumBudgetMicros("a", 1, Long.MAX_VALUE, Long.MAX_VALUE),
        )
    }

    private fun orchestrator(
        credentials: PresenceOnlyCredentialStore,
        enabled: Boolean = true,
        modelId: String = "openrouter/test-v1",
        knownPrice: Boolean = true,
    ): RealTextExecutionPreflightOrchestrator {
        val pricing = if (knownPrice) ModelPricing("catalog-v1", "USD", 10, 5) else ModelPricing()
        val model = ModelDescriptor(modelId, "Test", ModelCapabilities(supportsText = true, supportsVision = false, supportsStreaming = true), pricing = pricing)
        val snapshot = ModelRegistrySnapshot(
            ModelRegistrySnapshotId("snapshot"), 1, ProviderId.OPENROUTER, "catalog-v1", RegistrySnapshotSource.LOCAL_FIXTURE,
            now, RegistryVerificationStatus.VERIFIED, now, listOf(model), listOf(ModelPresetMapping(ModelPresetId.GPT_5_6_TERRA, modelId)), catalogSha256 = "a".repeat(64),
        )
        val settings = object : ModelServiceSettingsRepository {
            override fun load(providerId: ProviderId) = ProviderSettings(ProviderId.OPENROUTER, enabled, ModelPresetId.GPT_5_6_TERRA)
            override fun save(settings: ProviderSettings) = settings
        }
        return RealTextExecutionPreflightOrchestrator(settings, credentials, InMemoryVersionedModelRegistry(listOf(snapshot)), clock)
    }

    private fun request(
        text: String = "十二字本地测试文本",
        feeMaximum: Long = 770,
        attachments: List<RealTextAttachmentPreflightProof> = emptyList(),
    ): RealTextExecutionPreflightRequest {
        val execution = ConversationRealTextExecutionRequest(
            ConversationRealTextExecutionId("execution"), "idempotency", "b".repeat(64), ConversationId("conversation"),
            MessageNodeId("user"), MessageNodeId("assistant"), InvocationId("invocation"), ProviderAttemptId("attempt"), now,
        )
        val transport = ProviderTransportRequest(
            ConversationRealTextExecutionTransportHandle(execution.executionId, execution.invocationId, execution.attemptId, execution.requestFingerprint),
            ProviderTransportRoute(ProviderTransportProviderHandle("openrouter"), ProviderTransportModelId("openrouter/test-v1")),
            ProviderTransportEphemeralTextInput(text),
        )
        return RealTextExecutionPreflightRequest(
            RealTextExecutionPreflightId("preflight"), execution, transport, ProviderId.OPENROUTER, ModelPresetId.GPT_5_6_TERRA,
            100, RealTextFeeConfirmation("fee-confirmation", now, execution.requestFingerprint, "catalog-v1", "USD", feeMaximum),
            attachments, UsageLedgerSource.ANDROID_LOCAL,
        )
    }

    private fun attachmentIntent(): AttachmentEgressIntent = AttachmentEgressIntent(
        AttachmentId("attachment"), "d".repeat(64), "image/png", 100, ConversationId("conversation"), ConversationRealTextExecutionId("execution"),
        AttachmentEgressCapability("openrouter", "openrouter/test-v1", 1, setOf("image/png"), setOf(AttachmentEgressContentType.IMAGE), 1_000),
    )

    private fun assertBlocked(result: RealTextExecutionPreflightResult, expected: RealTextPreflightBlocker) {
        assertTrue(result is RealTextExecutionPreflightResult.Blocked)
        assertTrue((result as RealTextExecutionPreflightResult.Blocked).blockers.contains(expected))
    }

    private class PresenceOnlyCredentialStore(private val presence: CredentialPresence) : ProviderCredentialStore {
        var presenceWasCalled = false
        var loadWasCalled = false
        override fun credentialPresence(providerId: ProviderId): CredentialPresence {
            presenceWasCalled = true
            return presence
        }
        override fun hasCredential(providerId: ProviderId): Boolean = error("Preflight must not call hasCredential directly.")
        override fun saveCredential(providerId: ProviderId, credential: CharArray): Boolean = error("Preflight must not save credentials.")
        override fun loadCredential(providerId: ProviderId): CharArray? {
            loadWasCalled = true
            error("Preflight must not load a credential.")
        }
    }
}
