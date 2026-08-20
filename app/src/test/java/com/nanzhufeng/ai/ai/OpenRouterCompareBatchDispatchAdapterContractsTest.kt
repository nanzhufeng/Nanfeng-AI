package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.CanonicalContextSnapshotId
import com.nanzhufeng.ai.domain.CanonicalContextSnapshotRef
import com.nanzhufeng.ai.domain.CompareBatchBranchExecutionHandle
import com.nanzhufeng.ai.domain.CompareBatchDispatch
import com.nanzhufeng.ai.domain.CompareBatchDispatchAcceptance
import com.nanzhufeng.ai.domain.CompareBranchCancellationId
import com.nanzhufeng.ai.domain.CompareBranchExecutionGrant
import com.nanzhufeng.ai.domain.CompareBranchId
import com.nanzhufeng.ai.domain.CompareBranchReservationState
import com.nanzhufeng.ai.domain.CompareBranchTerminalIntent
import com.nanzhufeng.ai.domain.CompareConversationBranchPlan
import com.nanzhufeng.ai.domain.CompareConversationSessionId
import com.nanzhufeng.ai.domain.CompareConversationSessionIntentId
import com.nanzhufeng.ai.domain.CompareConversationSessionPlan
import com.nanzhufeng.ai.domain.CompareConversationSessionRead
import com.nanzhufeng.ai.domain.CompareConversationSessionRejection
import com.nanzhufeng.ai.domain.CompareConversationSessionStore
import com.nanzhufeng.ai.domain.CompareDispatchIntent
import com.nanzhufeng.ai.domain.CompareDispatchRecoveryState
import com.nanzhufeng.ai.domain.CompareEphemeralTextLease
import com.nanzhufeng.ai.domain.CompareSessionStoreResult
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionState
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.LogicalModelId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.ModelDeploymentId
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.ProviderHandle
import com.nanzhufeng.ai.domain.ProviderTransportEphemeralTextInput
import com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorSafeErrorCode
import com.nanzhufeng.ai.domain.RealTextExecutionReadyPlan
import com.nanzhufeng.ai.domain.RealTextExecutionRuntimeReceipt
import com.nanzhufeng.ai.domain.RealTextExecutionRuntimeReceiptPort
import com.nanzhufeng.ai.domain.RealTextExecutionRuntimeReceiptResult
import com.nanzhufeng.ai.domain.RealTextExecutionUsageReservationPort
import com.nanzhufeng.ai.domain.RealTextExecutionUsageReservationResult
import com.nanzhufeng.ai.domain.RealTextUsageReservationPlan
import com.nanzhufeng.ai.domain.UsageLedgerSource
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterCompareBatchDispatchAdapterContractsTest {
    private val now = Instant.parse("2026-08-16T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `two OpenRouter routes consume one lease once and disabled defaults produce independent safe branch failures`() {
        val batch = batch()
        val store = Store(batch)
        val adapter = OpenRouterCompareBatchDispatchAdapter(store, clock)

        assertEquals(CompareBatchDispatchAcceptance.Accepted, adapter.accept(batch))
        assertEquals(null, batch.textLease.take())
        assertEquals(listOf(CompareBranchReservationState.FAILED, CompareBranchReservationState.FAILED), store.terminals.map { it.nextState })
        assertEquals(2, store.terminals.map { it.branchId }.toSet().size)
    }

    @Test fun `provider facing model comes from each grant and partial outcome never rolls back sibling`() {
        val batch = batch()
        val store = Store(batch)
        val client = object : OpenAiCompatibleHttpClient {
            val models = mutableListOf<String>()
            override fun execute(request: OpenAiCompatibleHttpRequest, credential: ProtectedProviderCredentialHandle, cancellation: com.nanzhufeng.ai.domain.ProviderTransportCancellationToken): OpenAiCompatibleHttpOutcome {
                val body = request.body.toString(Charsets.UTF_8)
                models += if (body.contains("provider/gpt")) "provider/gpt" else "provider/claude"
                return if (body.contains("provider/gpt")) OpenAiCompatibleHttpOutcome.Response(200, "application/json", "{\"model\":\"provider/gpt\",\"choices\":[{\"message\":{\"content\":\"ok\"}}]}".toByteArray())
                else OpenAiCompatibleHttpOutcome.DisabledNoNetwork
            }
        }
        val adapter = OpenRouterCompareBatchDispatchAdapter(store, DisabledOpenRouterProtectedCredentialHandle, client, Receipts(), Usage(), clock)

        assertEquals(CompareBatchDispatchAcceptance.Accepted, adapter.accept(batch))
        assertEquals(setOf("provider/gpt", "provider/claude"), client.models.toSet())
        assertEquals(CompareBranchReservationState.SUCCEEDED, store.terminals.first { it.branchId.value == "branch:1" }.nextState)
        assertEquals(CompareBranchReservationState.FAILED, store.terminals.first { it.branchId.value == "branch:2" }.nextState)
    }

    @Test fun `wrong provider is rejected before consuming the lease and source has no legacy adapter key load UI or production registration`() {
        val rejected = batch(grants = listOf(grant("branch:1", "other"), grant("branch:2", "openrouter")))
        assertEquals(CompareBatchDispatchAcceptance.Rejected, OpenRouterCompareBatchDispatchAdapter(Store(rejected), clock).accept(rejected))
        assertTrue(rejected.textLease.take() != null)
        val modelMismatch = batch(grants = listOf(grant("branch:1", "openrouter").copy(logicalModelId = LogicalModelId("logical.other")), grant("branch:2", "openrouter")))
        assertEquals(CompareBatchDispatchAcceptance.Rejected, OpenRouterCompareBatchDispatchAdapter(Store(modelMismatch), clock).accept(modelMismatch))
        assertTrue(modelMismatch.textLease.take() != null)

        val source = File("src/main/java/com/nanzhufeng/ai/ai/OpenRouterCompareBatchDispatchAdapter.kt").readText()
        assertFalse(source.contains("OpenRouterInferenceAdapter"))
        assertFalse(source.contains("loadCredential"))
        assertFalse(source.contains("HttpsURLConnection"))
        assertFalse(source.contains("AppContainer"))
        assertFalse(source.contains("ViewModel"))
        assertFalse(source.contains("UsageLedgerRepository"))
    }

    private fun batch(grants: List<CompareBranchExecutionGrant> = listOf(grant("branch:1", "openrouter"), grant("branch:2", "openrouter"))): CompareBatchDispatch {
        val handles = grants.mapIndexed { index, grant ->
            CompareBatchBranchExecutionHandle(grant.branchId, ConversationId("conversation"), MessageNodeId("user"), MessageNodeId("assistant-$index"),
                InvocationId("invocation-$index"), ProviderAttemptId("attempt-$index"), ConversationRealTextExecutionId("execution-$index"),
                CompareBranchCancellationId("cancel-$index"), "reservation-$index")
        }
        return CompareBatchDispatch(CompareConversationSessionId("session"), CanonicalContextSnapshotRef(CanonicalContextSnapshotId("context"), "a".repeat(64), 1),
            grants, handles, 128, CompareEphemeralTextLease(ProviderTransportEphemeralTextInput("synthetic Compare input")))
    }

    private fun grant(branch: String, provider: String) = CompareBranchExecutionGrant("grant-$branch", CompareBranchId(branch), "b".repeat(64), "a".repeat(64), "c".repeat(64),
        if (branch.endsWith('1')) LogicalModelId("logical.chatgpt") else LogicalModelId("logical.claude"), ModelDeploymentId("deployment-$branch"), ProviderHandle(provider),
        if (branch.endsWith('1')) "provider/gpt" else "provider/claude", "catalog", "price", "USD", 100, "d".repeat(64), now.plusSeconds(300))

    private class Store(batch: CompareBatchDispatch) : CompareConversationSessionStore {
        val terminals = mutableListOf<CompareBranchTerminalIntent>()
        private val recordedAt = Instant.parse("2026-08-16T10:00:00Z")
        private val plan = CompareConversationSessionPlan(batch.sessionId, CompareConversationSessionIntentId("intent"), "confirmation", "b".repeat(64), ConversationId("conversation"), MessageNodeId("user"), batch.canonicalContext, MessageNodeId("user"),
            batch.grants.mapIndexed { index, grant -> CompareConversationBranchPlan(grant.branchId, grant, MessageNodeId("assistant-$index"), InvocationId("invocation-$index"), ProviderAttemptId("attempt-$index"), CompareBranchCancellationId("cancel-$index")) }, createdAt = recordedAt)
        private fun readValue() = CompareConversationSessionRead(plan, plan.branches.mapIndexed { index, branch ->
            com.nanzhufeng.ai.domain.CompareBranchRuntimeReference(branch.branchId, ConversationRealTextExecutionId("execution-$index"), "reservation-$index", 1, 0, CompareBranchReservationState.RESERVED, null, recordedAt)
        }, CompareDispatchRecoveryState.ACCEPTED)
        override fun persist(plan: CompareConversationSessionPlan) = CompareSessionStoreResult.Stored(readValue())
        override fun read(sessionId: CompareConversationSessionId) = readValue().takeIf { sessionId == plan.sessionId }
        override fun recordTerminal(intent: CompareBranchTerminalIntent, at: Instant): CompareSessionStoreResult<CompareConversationSessionRead> { terminals += intent; return CompareSessionStoreResult.Stored(readValue()) }
        override fun recordDispatchIntent(intent: CompareDispatchIntent, at: Instant) = CompareSessionStoreResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        override fun recordDispatchAccepted(intent: CompareDispatchIntent, at: Instant) = CompareSessionStoreResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        override fun recordDispatchRejected(intent: CompareDispatchIntent, at: Instant) = CompareSessionStoreResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        override fun storeFollowUp(intent: com.nanzhufeng.ai.domain.CompareBranchFollowUpIntent) = CompareSessionStoreResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        override fun storeAdoption(intent: com.nanzhufeng.ai.domain.CompareBranchAdoptionIntent) = CompareSessionStoreResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        override fun storeSynthesis(intent: com.nanzhufeng.ai.domain.CompareSynthesisIntent) = CompareSessionStoreResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
    }

    private class Usage : RealTextExecutionUsageReservationPort {
        override fun reserve(plan: RealTextUsageReservationPlan, at: Instant) = RealTextExecutionUsageReservationResult.Reserved
        override fun release(plan: RealTextUsageReservationPlan, safeErrorCode: String, at: Instant) = RealTextExecutionUsageReservationResult.Reserved
    }

    private class Receipts : RealTextExecutionRuntimeReceiptPort {
        override fun start(plan: RealTextExecutionReadyPlan, at: Instant) = RealTextExecutionRuntimeReceiptResult.Applied(receipt(plan, ConversationRealTextExecutionState.RUNNING))
        override fun appendPartial(plan: RealTextExecutionReadyPlan, delta: String, at: Instant) = RealTextExecutionRuntimeReceiptResult.Applied(receipt(plan, ConversationRealTextExecutionState.RUNNING))
        override fun finish(plan: RealTextExecutionReadyPlan, state: ConversationRealTextExecutionState, safeErrorCode: String?, at: Instant) = RealTextExecutionRuntimeReceiptResult.Applied(receipt(plan, state, safeErrorCode))
        private fun receipt(plan: RealTextExecutionReadyPlan, state: ConversationRealTextExecutionState, error: String? = null) = RealTextExecutionRuntimeReceipt(plan.cancellation.executionId, plan.cancellation.invocationId, plan.cancellation.attemptId, plan.cancellation.requestFingerprint, state, error)
    }
}
