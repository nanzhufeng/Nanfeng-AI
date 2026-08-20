package com.nanzhufeng.ai.domain

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareConversationSessionOwnerContractsTest {
    private val now = Instant.parse("2026-08-16T05:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val provider = ProviderRegistryEntry(
        ProviderHandle("openrouter"), "OpenRouter", ProviderAdapterIdentity.OPENROUTER_OPENAI_COMPATIBLE,
        "https://openrouter.ai/api/v1",
    )
    private val chatgpt = deployment(CompareMvpLogicalModels.chatgpt.id, "gpt", "openai/gpt-fixture", "gpt-price", "USD", 10, 4)
    private val claude = deployment(CompareMvpLogicalModels.claude.id, "claude", "anthropic/claude-fixture", "claude-price", "USD", 11, 5)

    @Test fun `session plan reserves two assistant invocation attempt identities against the existing user leaf without changing tree truth`() {
        val snapshot = userLeafSnapshot()
        val parent = snapshot.conversation.currentLeafMessageId!!
        val granted = grantedPlan()
        val owner = CompareConversationSessionOwner(clock)

        val result = owner.plan(sessionIntent("session-1", granted, snapshot, parent), snapshot) as CompareConversationSessionResult.Planned
        val plan = result.plan

        assertEquals(snapshot.conversation.id, plan.conversationId)
        assertEquals(parent, plan.parentUserMessageId)
        assertEquals(parent, plan.sharedCurrentLeafAtPlanning)
        assertEquals(snapshot.nodes, userLeafSnapshotReference(snapshot).nodes)
        assertEquals(parent, snapshot.conversation.currentLeafMessageId)
        assertEquals(2, plan.branches.size)
        assertEquals(2, plan.branches.map(CompareConversationBranchPlan::assistantMessageId).toSet().size)
        assertEquals(2, plan.branches.map(CompareConversationBranchPlan::invocationId).toSet().size)
        assertEquals(2, plan.branches.map(CompareConversationBranchPlan::attemptId).toSet().size)
        assertTrue(plan.branches.all { it.state == CompareBranchReservationState.RESERVED && snapshot.nodes.none { node -> node.id == it.assistantMessageId } })
        assertEquals(CompareSynthesisPolicy.NOT_REQUESTED, plan.synthesisPolicy)
        assertEquals(CompareSharedContextPolicy.EXPLICIT_ADOPTION_ONLY, plan.sharedContextPolicy)
    }

    @Test fun `grant is one-shot while exact session intent replays and invalid parent never forks a parallel tree`() {
        val snapshot = userLeafSnapshot()
        val parent = snapshot.conversation.currentLeafMessageId!!
        val granted = grantedPlan()
        val owner = CompareConversationSessionOwner(clock)
        val intent = sessionIntent("session-replay", granted, snapshot, parent)

        val planned = owner.plan(intent, snapshot) as CompareConversationSessionResult.Planned
        assertEquals(planned.plan, (owner.plan(intent, snapshot) as CompareConversationSessionResult.Replayed).plan)
        assertEquals(
            CompareConversationSessionRejection.GRANT_ALREADY_CONSUMED,
            (owner.plan(sessionIntent("other-session", granted, snapshot, parent), snapshot) as CompareConversationSessionResult.Rejected).reason,
        )
        val answered = answeredSnapshot()
        val wrongParent = answered.nodes.first { it.role == MessageRole.USER }.id
        assertEquals(
            CompareConversationSessionRejection.PARENT_NOT_CURRENT_USER_LEAF,
            (CompareConversationSessionOwner(clock).plan(sessionIntent("wrong-parent", grantedPlan(), answered, wrongParent), answered) as CompareConversationSessionResult.Rejected).reason,
        )
        assertEquals(1, owner.plannedSessionCountForContractTest())
    }

    @Test fun `partial failure and cancellation remain independent and successful branch follow-up excludes the sibling path`() {
        val snapshot = userLeafSnapshot()
        val owner = CompareConversationSessionOwner(clock)
        val initial = (owner.plan(sessionIntent("terminal", grantedPlan(), snapshot, snapshot.conversation.currentLeafMessageId!!), snapshot) as CompareConversationSessionResult.Planned).plan
        val success = initial.branches[0]
        val cancelled = initial.branches[1]

        val afterSuccess = (owner.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("success"), initial.sessionId, success.branchId, CompareBranchReservationState.SUCCEEDED)) as CompareBranchTerminalResult.Updated).plan
        assertEquals(CompareBranchReservationState.SUCCEEDED, afterSuccess.branches[0].state)
        assertEquals(CompareBranchReservationState.RESERVED, afterSuccess.branches[1].state)
        assertEquals(
            CompareConversationSessionRejection.CANCELLATION_ID_MISMATCH,
            (owner.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("bad-cancel"), initial.sessionId, cancelled.branchId, CompareBranchReservationState.CANCELLED, CompareBranchCancellationId("wrong"))) as CompareBranchTerminalResult.Rejected).reason,
        )
        val terminal = (owner.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("cancel"), initial.sessionId, cancelled.branchId, CompareBranchReservationState.CANCELLED, cancelled.cancellationId)) as CompareBranchTerminalResult.Updated).plan
        assertEquals(listOf(CompareBranchReservationState.SUCCEEDED, CompareBranchReservationState.CANCELLED), terminal.branches.map(CompareConversationBranchPlan::state))

        val followUp = (owner.planFollowUp(CompareBranchFollowUpIntent(CompareBranchFollowUpIntentId("follow-up"), initial.sessionId, success.branchId, context("branch-context", "b"))) as CompareBranchFollowUpResult.Planned).plan
        assertEquals(success.assistantMessageId, followUp.branchTailAssistantMessageId)
        assertEquals(setOf(cancelled.assistantMessageId), followUp.excludedSiblingAssistantMessageIds)
        assertEquals("b".repeat(64), followUp.canonicalContext.contentHash)
        assertEquals(snapshot.conversation.currentLeafMessageId, initial.sharedCurrentLeafAtPlanning)
    }

    @Test fun `adoption only plans one successful branch and preserves its sibling without changing shared branch`() {
        val snapshot = userLeafSnapshot()
        val owner = CompareConversationSessionOwner(clock)
        val session = (owner.plan(sessionIntent("adopt", grantedPlan(), snapshot, snapshot.conversation.currentLeafMessageId!!), snapshot) as CompareConversationSessionResult.Planned).plan
        val first = session.branches[0]
        val second = session.branches[1]
        owner.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("adopt-success"), session.sessionId, first.branchId, CompareBranchReservationState.SUCCEEDED))

        val adoptionIntent = CompareBranchAdoptionIntent(CompareBranchAdoptionIntentId("adopt-first"), session.sessionId, first.branchId)
        val adopted = (owner.planAdoption(adoptionIntent) as CompareBranchAdoptionResult.Planned).plan
        assertEquals(first.assistantMessageId, adopted.adoptedAssistantMessageId)
        assertEquals(setOf(second.assistantMessageId), adopted.preservedSiblingAssistantMessageIds)
        assertTrue(adopted.requiresExplicitUserAdoption)
        assertEquals(adopted, (owner.planAdoption(adoptionIntent) as CompareBranchAdoptionResult.Replayed).plan)
        assertEquals(
            CompareConversationSessionRejection.ADOPTION_REQUIRES_SUCCESS,
            (owner.planAdoption(CompareBranchAdoptionIntent(CompareBranchAdoptionIntentId("adopt-second"), session.sessionId, second.branchId)) as CompareBranchAdoptionResult.Rejected).reason,
        )
        assertEquals(snapshot.conversation.currentLeafMessageId, session.sharedCurrentLeafAtPlanning)
    }

    @Test fun `synthesis is absent by default then requires both successful branches a new context and a fresh unchecked consent budget gate`() {
        val snapshot = userLeafSnapshot()
        val owner = CompareConversationSessionOwner(clock)
        val session = (owner.plan(sessionIntent("synthesis", grantedPlan(), snapshot, snapshot.conversation.currentLeafMessageId!!), snapshot) as CompareConversationSessionResult.Planned).plan
        val first = session.branches[0]
        val second = session.branches[1]
        val blocked = CompareSynthesisIntent(CompareSynthesisIntentId("blocked-synthesis"), session.sessionId, listOf(first.branchId, second.branchId), context("synthesis-context", "c"), gate("blocked"))
        assertEquals(
            CompareConversationSessionRejection.SYNTHESIS_REQUIRES_TWO_SUCCESSFUL_BRANCHES,
            (owner.planSynthesis(blocked) as CompareSynthesisResult.Rejected).reason,
        )
        owner.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("synthesis-first"), session.sessionId, first.branchId, CompareBranchReservationState.SUCCEEDED))
        owner.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("synthesis-second"), session.sessionId, second.branchId, CompareBranchReservationState.SUCCEEDED))
        val allowed = CompareSynthesisIntent(CompareSynthesisIntentId("allowed-synthesis"), session.sessionId, listOf(first.branchId, second.branchId), context("synthesis-context", "c"), gate("allowed"))

        val synthesis = (owner.planSynthesis(allowed) as CompareSynthesisResult.Planned).plan
        assertEquals(listOf(first.assistantMessageId, second.assistantMessageId), synthesis.sourceAssistantMessageIds)
        assertTrue(synthesis.isIndependentInvocation && synthesis.mayNotBeTreatedAsOriginalCompareResponse)
        assertFalse(synthesis.freshConsentBudgetGate.acknowledged)
        assertEquals(synthesis, (owner.planSynthesis(allowed) as CompareSynthesisResult.Replayed).plan)
    }

    @Test fun `pure session owner has no repository runtime transport Direct or content block dependency`() {
        val source = File("src/main/java/com/nanzhufeng/ai/domain/CompareConversationSessionOwner.kt").readText()
        assertFalse(source.contains("ConversationRepository"))
        assertFalse(source.contains("ConversationRuntime"))
        assertFalse(source.contains("ConversationRealText"))
        assertFalse(source.contains("ProviderTransport"))
        assertFalse(source.contains("DirectExecutionApplicationOwner"))
        assertFalse(source.contains("ContentBlock"))
    }

    @Test fun `expired one-shot Compare grant cannot create a delayed session plan`() {
        val snapshot = userLeafSnapshot()
        val expiredClock = Clock.fixed(now.plusSeconds(CompareExecutionApplicationOwner.CONFIRMATION_TTL_MINUTES * 60 + 1), ZoneOffset.UTC)
        val owner = CompareConversationSessionOwner(expiredClock)

        assertEquals(
            CompareConversationSessionRejection.GRANT_EXPIRED,
            (owner.plan(sessionIntent("expired", grantedPlan(), snapshot, snapshot.conversation.currentLeafMessageId!!), snapshot) as CompareConversationSessionResult.Rejected).reason,
        )
    }

    private fun userLeafSnapshot(): ConversationSnapshot {
        val tree = ConversationTreeService(clock)
        return tree.append(tree.create("Compare tree"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("Compare input parent"))))
    }

    private fun answeredSnapshot(): ConversationSnapshot {
        val tree = ConversationTreeService(clock)
        return tree.append(
            tree.append(tree.create("Compare answered tree"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("Compare input parent")))),
            AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("Existing answer"))),
        )
    }

    /** Captures the expected same immutable value without invoking a second tree mutation. */
    private fun userLeafSnapshotReference(snapshot: ConversationSnapshot): ConversationSnapshot = snapshot

    private fun sessionIntent(
        id: String,
        granted: CompareExecutionGrantedPlan,
        snapshot: ConversationSnapshot,
        parent: MessageNodeId,
    ) = CompareConversationSessionIntent(
        CompareConversationSessionIntentId(id), granted, snapshot.conversation.id, parent,
        CanonicalContextSnapshotRef(CanonicalContextSnapshotId("compare-context"), "a".repeat(64), 1),
    )

    private fun grantedPlan(): CompareExecutionGrantedPlan {
        val registry = InMemoryMultiProviderModelRegistry(listOf(snapshot(chatgpt, claude)))
        val owner = CompareExecutionApplicationOwner(registry, MultiModelOrchestrator { _, _ -> error("Auto must not run") }, clock)
        val request = CompareExecutionApplicationRequest(
            OrchestrationRequestId("compare-grant"), CanonicalContextSnapshotRef(CanonicalContextSnapshotId("compare-context"), "a".repeat(64), 1),
            listOf(selection(chatgpt), selection(claude)), ProviderTransportEphemeralTextInput("compare text"), 100, 0,
        )
        val confirmation = (owner.requestConfirmation(request) as CompareExecutionApplicationResult.Confirmation).value
        owner.setAcknowledgement(confirmation.id, true)
        return (owner.confirm(confirmation.id) as CompareExecutionApplicationResult.Granted).plan
    }

    private fun context(id: String, hashChar: String) =
        CanonicalContextSnapshotRef(CanonicalContextSnapshotId(id), hashChar.repeat(64), 2)

    private fun gate(id: String) = CompareSynthesisConsentBudgetGate(id, "d".repeat(64), "USD", 1000, now.plusSeconds(60))

    private fun selection(deployment: ModelDeploymentDescriptor) = ModelDeploymentSelection(deployment.logicalModelId, deployment.id, deployment.provider)

    private fun deployment(logical: LogicalModelId, id: String, model: String, priceVersion: String, currency: String, input: Long, output: Long) =
        ModelDeploymentDescriptor(ModelDeploymentId(id), logical, provider.handle, model, ModelDeploymentState.ACTIVE, ModelPricing(priceVersion, currency, input, output))

    private fun snapshot(vararg deployments: ModelDeploymentDescriptor) = MultiProviderRegistrySnapshot(
        MultiProviderRegistrySnapshotId("snapshot-${deployments.joinToString { it.id.value }}"), 1, "catalog-v1", now,
        listOf(provider), CompareMvpLogicalModels.defaultPair, deployments.toList(),
    )
}
