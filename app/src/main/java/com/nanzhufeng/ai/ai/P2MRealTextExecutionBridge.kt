package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionRequest
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderTransportEphemeralTextInput
import com.nanzhufeng.ai.domain.ProviderTransportModelId
import com.nanzhufeng.ai.domain.ProviderTransportProviderHandle
import com.nanzhufeng.ai.domain.ProviderTransportRequest
import com.nanzhufeng.ai.domain.ProviderTransportRoute
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinator
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorResult
import com.nanzhufeng.ai.domain.RealTextExecutionPreflightId
import com.nanzhufeng.ai.domain.RealTextExecutionPreflightOrchestrator
import com.nanzhufeng.ai.domain.RealTextExecutionPreflightRequest
import com.nanzhufeng.ai.domain.RealTextExecutionPreflightResult
import com.nanzhufeng.ai.domain.RealTextFeeConfirmation
import com.nanzhufeng.ai.domain.UsageLedgerSource
import java.time.Clock
import java.time.Instant

/**
 * Typed evidence that the existing P2-M visible confirmation owner has already accepted this
 * exact RunSpec. It is internal-only and carries no new UI state, text, credential, or egress grant.
 */
data class P2MExistingVisibleConfirmation(
    val runSpecFingerprint: String,
    val acceptedAt: Instant,
) {
    init { require(runSpecFingerprint.matches(Regex("[0-9a-f]{64}"))) { "P2-M 确认指纹不合法。" } }
}

enum class P2MRealTextExecutionBridgeBlocker {
    VISIBLE_CONFIRMATION_REQUIRED,
    VISIBLE_CONFIRMATION_SCOPE_MISMATCH,
    P2M_READINESS_REQUIRED,
    P2M_FIXTURE_UNAVAILABLE,
    P2M_PRICING_UNAVAILABLE,
    PREFLIGHT_BLOCKED,
}

sealed interface P2MRealTextExecutionBridgeResult {
    data class Blocked(val blocker: P2MRealTextExecutionBridgeBlocker) : P2MRealTextExecutionBridgeResult
    data class Coordinated(
        val result: RealTextExecutionCoordinatorResult,
    ) : P2MRealTextExecutionBridgeResult
}

/**
 * Background-only P2-M bridge. Its coordinator is intentionally injected with default fail-closed
 * ports in AppContainer, so a successful preflight still cannot load a Key, send HTTP, read an
 * attachment, or append a real Usage Ledger entry. Its outcome never changes the legacy P2-M flow.
 */
class P2MRealTextExecutionBridge(
    private val preflight: RealTextExecutionPreflightOrchestrator,
    private val coordinator: RealTextExecutionCoordinator,
    private val clock: Clock,
) {
    fun coordinate(
        readiness: P2MRealServiceReadiness,
        visibleConfirmation: P2MExistingVisibleConfirmation?,
    ): P2MRealTextExecutionBridgeResult {
        val confirmation = visibleConfirmation
            ?: return P2MRealTextExecutionBridgeResult.Blocked(P2MRealTextExecutionBridgeBlocker.VISIBLE_CONFIRMATION_REQUIRED)
        val ready = readiness as? P2MRealServiceReadiness.Ready
            ?: return P2MRealTextExecutionBridgeResult.Blocked(P2MRealTextExecutionBridgeBlocker.P2M_READINESS_REQUIRED)
        val spec = ready.spec
        if (confirmation.runSpecFingerprint != spec.fingerprint()) {
            return P2MRealTextExecutionBridgeResult.Blocked(P2MRealTextExecutionBridgeBlocker.VISIBLE_CONFIRMATION_SCOPE_MISMATCH)
        }
        val input = P2MSyntheticFixtures.textContent(spec.fixture.id)
            ?: return P2MRealTextExecutionBridgeResult.Blocked(P2MRealTextExecutionBridgeBlocker.P2M_FIXTURE_UNAVAILABLE)
        val pricing = ready.model.pricing
        val priceVersion = pricing.priceVersion
            ?: return P2MRealTextExecutionBridgeResult.Blocked(P2MRealTextExecutionBridgeBlocker.P2M_PRICING_UNAVAILABLE)
        val currency = pricing.currencyCode
            ?: return P2MRealTextExecutionBridgeResult.Blocked(P2MRealTextExecutionBridgeBlocker.P2M_PRICING_UNAVAILABLE)
        val stable = spec.fingerprint().take(24)
        val execution = ConversationRealTextExecutionRequest(
            executionId = ConversationRealTextExecutionId("p2m-bridge-execution-$stable"),
            idempotencyKey = "p2m-bridge-intent-$stable",
            requestFingerprint = spec.fingerprint(),
            conversationId = ConversationId("p2m-bridge-conversation-$stable"),
            userMessageId = MessageNodeId("p2m-bridge-user-$stable"),
            assistantMessageId = MessageNodeId("p2m-bridge-assistant-$stable"),
            invocationId = InvocationId("p2m-bridge-invocation-$stable"),
            attemptId = ProviderAttemptId("p2m-bridge-attempt-$stable"),
            createdAt = confirmation.acceptedAt,
        )
        val transport = ProviderTransportRequest(
            execution = com.nanzhufeng.ai.domain.ConversationRealTextExecutionTransportHandle(
                execution.executionId, execution.invocationId, execution.attemptId, execution.requestFingerprint,
            ),
            route = ProviderTransportRoute(ProviderTransportProviderHandle("openrouter"), ProviderTransportModelId(spec.modelId)),
            input = ProviderTransportEphemeralTextInput(input),
        )
        val request = RealTextExecutionPreflightRequest(
            preflightId = RealTextExecutionPreflightId("p2m-bridge-preflight-$stable"),
            executionRequest = execution,
            transportRequest = transport,
            providerId = ProviderId.OPENROUTER,
            presetId = ready.configuration.settings.presetId,
            outputTokenLimit = spec.maxOutputTokens.toLong(),
            feeConfirmation = RealTextFeeConfirmation(
                confirmationId = "p2m-visible-confirmation-$stable",
                approvedAt = confirmation.acceptedAt,
                requestFingerprint = spec.fingerprint(),
                priceVersion = priceVersion,
                currencyCode = currency,
                acknowledgedMaximumMicros = spec.costCapMicros,
            ),
            attachments = emptyList(),
            usageSource = UsageLedgerSource.ANDROID_LOCAL,
        )
        return when (val result = preflight.preflight(request)) {
            is RealTextExecutionPreflightResult.Blocked ->
                P2MRealTextExecutionBridgeResult.Blocked(P2MRealTextExecutionBridgeBlocker.PREFLIGHT_BLOCKED)
            is RealTextExecutionPreflightResult.Ready ->
                P2MRealTextExecutionBridgeResult.Coordinated(coordinator.coordinate(result.plan))
        }
    }
}
