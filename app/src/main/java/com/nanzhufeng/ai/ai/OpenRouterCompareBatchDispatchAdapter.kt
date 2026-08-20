package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.CompareBatchDispatch
import com.nanzhufeng.ai.domain.CompareBatchDispatchAcceptance
import com.nanzhufeng.ai.domain.CompareBatchDispatchPort
import com.nanzhufeng.ai.domain.CompareBranchReservationState
import com.nanzhufeng.ai.domain.CompareBranchTerminalIntent
import com.nanzhufeng.ai.domain.CompareBranchTerminalIntentId
import com.nanzhufeng.ai.domain.CompareConversationSessionRejection
import com.nanzhufeng.ai.domain.CompareConversationSessionStore
import com.nanzhufeng.ai.domain.CompareSessionStoreResult
import com.nanzhufeng.ai.domain.CompareMvpLogicalModels
import com.nanzhufeng.ai.domain.ConservativeInputBillingBudget
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionTransportHandle
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionState
import com.nanzhufeng.ai.domain.ProviderTransportCancellationToken
import com.nanzhufeng.ai.domain.ProviderTransportEphemeralTextInput
import com.nanzhufeng.ai.domain.ProviderTransportEventSink
import com.nanzhufeng.ai.domain.ProviderTransportModelId
import com.nanzhufeng.ai.domain.ProviderTransportProviderHandle
import com.nanzhufeng.ai.domain.ProviderTransportRequest
import com.nanzhufeng.ai.domain.ProviderTransportRoute
import com.nanzhufeng.ai.domain.ProviderTransportSafeErrorCode
import com.nanzhufeng.ai.domain.ProviderTransportSafeResult
import com.nanzhufeng.ai.domain.ProviderTransportSafeRunMetadata
import com.nanzhufeng.ai.domain.ProviderTransportTerminalOutcome
import com.nanzhufeng.ai.domain.ProviderTransport
import com.nanzhufeng.ai.domain.RealTextExecutionCancellationIdentity
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinator
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorCancellation
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorResult
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorTerminalResult
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorTransport
import com.nanzhufeng.ai.domain.RealTextExecutionPreflightId
import com.nanzhufeng.ai.domain.RealTextExecutionReadyPlan
import com.nanzhufeng.ai.domain.RealTextExecutionRuntimeReceiptPort
import com.nanzhufeng.ai.domain.RealTextExecutionUsageReservationPort
import com.nanzhufeng.ai.domain.RealTextUsageReservationPlan
import com.nanzhufeng.ai.domain.RealTextVerifiedCompareDeployment
import com.nanzhufeng.ai.domain.UsageLedgerSource
import java.security.MessageDigest
import java.time.Clock

/** Opaque disabled handle for readiness construction; it has no credential bytes or load API. */
object DisabledOpenRouterProtectedCredentialHandle : ProtectedProviderCredentialHandle {
    override val providerHandle: String = "openrouter"
    override val handleReference: String = "disabled-openrouter-credential"
}

/**
 * MM-O4-E's only OpenRouter Compare adapter. It consumes one batch lease once, routes each grant
 * through the existing OpenAI-compatible transport and one P3 coordinator, then appends only
 * independent content-free branch terminal facts. It neither registers a production client nor
 * reads a credential; defaults are disabled and therefore make no network request.
 */
class OpenRouterCompareBatchDispatchAdapter(
    private val store: CompareConversationSessionStore,
    private val credential: ProtectedProviderCredentialHandle = DisabledOpenRouterProtectedCredentialHandle,
    private val httpClient: OpenAiCompatibleHttpClient = DisabledOpenAiCompatibleHttpClient,
    private val runtimeReceipts: RealTextExecutionRuntimeReceiptPort,
    private val usageReservations: RealTextExecutionUsageReservationPort,
    private val clock: Clock,
) : CompareBatchDispatchPort {
    constructor(
        store: CompareConversationSessionStore,
        clock: Clock,
    ) : this(
        store = store,
        runtimeReceipts = com.nanzhufeng.ai.domain.DisabledRealTextExecutionRuntimeReceiptPort,
        usageReservations = com.nanzhufeng.ai.domain.DisabledRealTextExecutionUsageReservationPort,
        clock = clock,
    )

    override fun accept(batch: CompareBatchDispatch): CompareBatchDispatchAcceptance {
        if (!validBatch(batch)) return CompareBatchDispatchAcceptance.Rejected
        val leasedInput = batch.textLease.take() ?: return CompareBatchDispatchAcceptance.Rejected
        val branchPlans = try {
            batch.grants.map { grant ->
                val handle = batch.branchExecutionHandles.first { it.branchId == grant.branchId }
                val ready = readyPlan(batch, grant, handle, leasedInput)
                ready to ProviderTransportRequest(
                    ConversationRealTextExecutionTransportHandle(handle.executionId, handle.invocationId, handle.attemptId, grant.requestFingerprint),
                    ProviderTransportRoute(ProviderTransportProviderHandle("openrouter"), ProviderTransportModelId(grant.providerModelId)),
                    ProviderTransportEphemeralTextInput(leasedInput.text),
                )
            }
        } catch (_: Exception) {
            return CompareBatchDispatchAcceptance.Rejected
        }
        val bridge = CompareCoordinatorTransport(
            branchPlans.associate { (ready, request) -> ready.cancellation.executionId to request }.toMutableMap(),
            OpenAiCompatibleProviderTransport(
                credential = credential,
                profile = OpenAiCompatibleRequestProfile(OpenAiCompatibleResponseMode.JSON, maxOutputTokens = batch.outputTokenLimit.toInt()),
                httpClient = httpClient,
            ),
        )
        return try {
            val coordinator = RealTextExecutionCoordinator(bridge, runtimeReceipts, usageReservations, clock::instant)
            val outcomes = branchPlans.map { (ready, _) -> ready to coordinator.coordinate(ready) }
            if (outcomes.all { (ready, result) -> appendTerminal(batch, ready, result) }) CompareBatchDispatchAcceptance.Accepted
            else CompareBatchDispatchAcceptance.Rejected
        } finally {
            bridge.release()
        }
    }

    private fun validBatch(batch: CompareBatchDispatch): Boolean =
        batch.grants.size == 2 && batch.grants.all { grant ->
            grant.provider.value == "openrouter" && grant.providerModelId.isNotBlank() &&
                grant.catalogVersion.isNotBlank() && grant.priceVersion.isNotBlank() &&
                grant.contextHash == batch.canonicalContext.contentHash
        } && batch.branchExecutionHandles.size == 2 &&
            batch.branchExecutionHandles.map { it.branchId }.toSet() == batch.grants.map { it.branchId }.toSet() &&
            batch.grants.map { it.logicalModelId }.toSet() == CompareMvpLogicalModels.defaultPair.map { it.id }.toSet() &&
            batch.grants.map { it.deploymentId }.toSet().size == 2

    private fun readyPlan(
        batch: CompareBatchDispatch,
        grant: com.nanzhufeng.ai.domain.CompareBranchExecutionGrant,
        handle: com.nanzhufeng.ai.domain.CompareBatchBranchExecutionHandle,
        input: ProviderTransportEphemeralTextInput,
    ): RealTextExecutionReadyPlan {
        val inputUpperBound = ConservativeInputBillingBudget.inputTokenUpperBound(input.text)
        val reconciliation = sha256(listOf(
            grant.requestFingerprint, grant.grantId, grant.deploymentId.value, grant.providerModelId,
            inputUpperBound.toString(), batch.outputTokenLimit.toString(), grant.maximumBudgetMicros.toString(), grant.currencyCode,
        ).joinToString("|"))
        return RealTextExecutionReadyPlan(
            preflightId = RealTextExecutionPreflightId("compare:${batch.sessionId.value}:${handle.branchId.value}:preflight"),
            cancellation = RealTextExecutionCancellationIdentity(
                RealTextExecutionPreflightId("compare:${batch.sessionId.value}:${handle.branchId.value}:preflight"),
                handle.executionId, handle.invocationId, handle.attemptId, grant.requestFingerprint,
            ),
            providerHandle = ProviderTransportProviderHandle("openrouter"),
            modelId = ProviderTransportModelId(grant.providerModelId),
            presetId = null,
            verifiedCompareDeployment = RealTextVerifiedCompareDeployment(
                grant.logicalModelId, grant.deploymentId, grant.provider, grant.catalogVersion, grant.priceVersion,
            ),
            feeConfirmationFingerprint = grant.confirmationScopeFingerprint,
            usageReservation = RealTextUsageReservationPlan(
                replayToken = handle.usageReservationReplayToken, executionId = handle.executionId,
                conversationId = handle.conversationId, branchLeafMessageId = handle.parentUserMessageId,
                invocationId = handle.invocationId, attemptId = handle.attemptId,
                requestedModelId = ProviderTransportModelId(grant.providerModelId),
                conservativeInputTokenUpperBound = inputUpperBound, reservedOutputTokens = batch.outputTokenLimit,
                budgetMicros = grant.maximumBudgetMicros, currencyCode = grant.currencyCode,
                reconciliationFingerprint = reconciliation, source = UsageLedgerSource.ANDROID_LOCAL,
            ),
            attachments = emptyList(), plannedAt = clock.instant(),
        )
    }

    private fun appendTerminal(
        batch: CompareBatchDispatch,
        plan: RealTextExecutionReadyPlan,
        result: RealTextExecutionCoordinatorResult,
    ): Boolean {
        val branch = batch.branchExecutionHandles.firstOrNull { it.executionId == plan.cancellation.executionId } ?: return false
        val terminal = when (result) {
            is RealTextExecutionCoordinatorResult.Executed -> result.terminal
            is RealTextExecutionCoordinatorResult.Replayed -> result.terminal
            is RealTextExecutionCoordinatorResult.Conflict -> return false
        }
        val state = when (terminal) {
            is RealTextExecutionCoordinatorTerminalResult.Succeeded -> CompareBranchReservationState.SUCCEEDED
            is RealTextExecutionCoordinatorTerminalResult.Failed -> CompareBranchReservationState.FAILED
            is RealTextExecutionCoordinatorTerminalResult.Cancelled -> CompareBranchReservationState.CANCELLED
        }
        val intent = CompareBranchTerminalIntent(
            CompareBranchTerminalIntentId("compare:${batch.sessionId.value}:${branch.branchId.value}:execution:${branch.executionId.value}"),
            batch.sessionId, branch.branchId, state,
            cancellationId = if (state == CompareBranchReservationState.CANCELLED) branch.cancellationId else null,
        )
        return store.recordTerminal(intent, clock.instant()) !is CompareSessionStoreResult.Rejected
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** In-memory bridge that forgets both raw requests after this one batch has completed. */
private class CompareCoordinatorTransport(
    private val requests: MutableMap<com.nanzhufeng.ai.domain.ConversationRealTextExecutionId, ProviderTransportRequest>,
    private val transport: ProviderTransport,
) : RealTextExecutionCoordinatorTransport {
    override fun execute(
        plan: RealTextExecutionReadyPlan,
        cancellation: RealTextExecutionCoordinatorCancellation,
        sink: ProviderTransportEventSink,
    ): ProviderTransportSafeResult {
        val request = requests.remove(plan.cancellation.executionId) ?: return mismatch(plan)
        if (request.execution.executionId != plan.cancellation.executionId || request.execution.invocationId != plan.cancellation.invocationId ||
            request.execution.attemptId != plan.cancellation.attemptId || request.execution.requestFingerprint != plan.cancellation.requestFingerprint ||
            request.route.providerHandle.value != plan.providerHandle.value || request.route.modelId != plan.modelId
        ) return mismatch(plan)
        return transport.execute(request, object : ProviderTransportCancellationToken {
            override fun isCancellationRequested(): Boolean = cancellation.isCancellationRequested()
        }, sink)
    }

    fun release() { requests.clear() }

    private fun mismatch(plan: RealTextExecutionReadyPlan) = ProviderTransportSafeResult(ProviderTransportSafeRunMetadata(
        plan.cancellation.executionId, plan.cancellation.invocationId, plan.cancellation.attemptId,
        plan.cancellation.requestFingerprint, plan.providerHandle, plan.modelId, null,
        ProviderTransportTerminalOutcome.FAILED, ProviderTransportSafeErrorCode.TRANSPORT_PROTOCOL_VIOLATION, 0,
    ))
}
