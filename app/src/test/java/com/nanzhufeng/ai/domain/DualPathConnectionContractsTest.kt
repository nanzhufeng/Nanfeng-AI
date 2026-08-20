package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DualPathConnectionContractsTest {
    private val guarded = ConnectionCapabilitySnapshot(
        connection = ConnectionCapability.ONLINE_NETWORK_UNVERIFIED,
        providerConfiguration = ProviderConfiguration.READY_FOR_GUARD,
        credentialPresence = CredentialPresence.PRESENT,
        catalogFreshness = CatalogFreshness.CURRENT,
        egressConsent = EgressConsentState.REQUIRED_PER_INTENT,
        syncCapability = SyncCapability.ENCRYPTED_SYNC_NOT_CONFIGURED,
        degradedReasons = setOf(DegradedReason.NETWORK_UNVERIFIED, DegradedReason.EGRESS_CONSENT_REQUIRED, DegradedReason.SYNC_NOT_CONFIGURED),
    )

    @Test fun `local selection is ready while online does not become local success`() {
        assertTrue(ConnectionPathGuard(guarded).selectLocal() is PathSelectionResult.LocalReady)
        val online = ConnectionPathGuard(guarded).selectOnline(OnlineIntent("online-1", DataPath.LOCAL_ONLY, EgressConsentState.GRANTED_FOR_CURRENT_INTENT, true, true))
        assertTrue(online is PathSelectionResult.OnlineBlocked)
        assertTrue((online as PathSelectionResult.OnlineBlocked).localFallbackAvailable)
    }

    @Test fun `cancel retry unknown cost and repeat intent are explicit`() {
        val guard = ConnectionPathGuard(guarded)
        assertEquals(PathSelectionResult.Cancelled, guard.selectOnline(OnlineIntent("cancel", DataPath.LOCAL_ONLY, EgressConsentState.CANCELLED, true, true)))
        val first = guard.selectOnline(OnlineIntent("unknown", DataPath.LOCAL_ONLY, EgressConsentState.GRANTED_FOR_CURRENT_INTENT, false, true)) as PathSelectionResult.OnlineBlocked
        assertTrue(DegradedReason.UNKNOWN_COST in first.reasons)
        assertTrue(guard.selectOnline(OnlineIntent("unknown", DataPath.LOCAL_ONLY, EgressConsentState.GRANTED_FOR_CURRENT_INTENT, false, true)) is PathSelectionResult.Replayed)
    }

    @Test fun `encrypted sync is separately guarded from execution`() {
        val result = ConnectionPathGuard(guarded).selectOnline(OnlineIntent("sync", DataPath.ENCRYPTED_SYNC, EgressConsentState.GRANTED_FOR_CURRENT_INTENT, true, true)) as PathSelectionResult.OnlineBlocked
        assertTrue(DegradedReason.SYNC_NOT_CONFIGURED in result.reasons)
    }

    @Test fun `guidance intent cannot become online ready`() {
        val guard = ConnectionPathGuard(guarded)
        assertTrue(guard.selectLocal() is PathSelectionResult.LocalReady)
        val guidance = guard.selectOnline(OnlineIntent("p10b-guidance", DataPath.LOCAL_ONLY, EgressConsentState.REQUIRED_PER_INTENT, false, false)) as PathSelectionResult.OnlineBlocked
        assertTrue(DegradedReason.EGRESS_CONSENT_REQUIRED in guidance.reasons)
        assertTrue(DegradedReason.UNKNOWN_COST in guidance.reasons)
        assertTrue(DegradedReason.MODEL_UNAVAILABLE in guidance.reasons)
    }
}
