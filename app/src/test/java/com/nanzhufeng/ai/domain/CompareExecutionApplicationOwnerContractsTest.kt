package com.nanzhufeng.ai.domain

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareExecutionApplicationOwnerContractsTest {
    private val now = Instant.parse("2026-08-16T04:00:00Z")
    private val provider = ProviderRegistryEntry(
        ProviderHandle("openrouter"), "OpenRouter", ProviderAdapterIdentity.OPENROUTER_OPENAI_COMPATIBLE,
        "https://openrouter.ai/api/v1",
    )
    private val chatgpt = deployment(
        CompareMvpLogicalModels.chatgpt.id, "openrouter.chatgpt.fixture", "openai/gpt-fixture", "price-gpt-v1", "USD", 10, 4,
    )
    private val claude = deployment(
        CompareMvpLogicalModels.claude.id, "openrouter.claude.fixture", "anthropic/claude-fixture", "price-claude-v1", "USD", 11, 5,
    )

    @Test fun `two-target Compare calls no Auto and freezes one unchecked summary over one canonical context and text hash`() {
        var autoCalls = 0
        val owner = owner(
            snapshot(chatgpt, claude),
            auto = AutoRoutingPort { _, _ -> autoCalls += 1; error("Compare must not route") },
        )

        val confirmation = (owner.requestConfirmation(request()) as CompareExecutionApplicationResult.Confirmation).value

        assertEquals(0, autoCalls)
        assertFalse(confirmation.acknowledged)
        assertEquals("a".repeat(64), confirmation.contextHash)
        assertEquals(2, confirmation.recipients.size)
        assertEquals(listOf("compare-fixture:branch:1", "compare-fixture:branch:2"), confirmation.recipients.map { it.branchId.value })
        assertEquals(listOf("openai/gpt-fixture", "anthropic/claude-fixture"), confirmation.recipients.map(CompareExecutionRecipient::providerModelId))
        assertEquals(listOf("price-gpt-v1", "price-claude-v1"), confirmation.recipients.map(CompareExecutionRecipient::priceVersion))
        val inputBound = ConservativeInputBillingBudget.inputTokenUpperBound("中文 🙂 text")
        assertEquals(inputBound * 10 + 100 * 4, confirmation.recipients[0].maximumBudgetMicros)
        assertEquals(inputBound * 11 + 100 * 5, confirmation.recipients[1].maximumBudgetMicros)
        assertEquals(confirmation.recipients.sumOf(CompareExecutionRecipient::maximumBudgetMicros), confirmation.totalMaximumBudgetMicros)
        assertFalse(confirmation.toString().contains("中文 🙂 text"))
    }

    @Test fun `Compare rejects attachments third target duplicate logical deployment and any non ChatGPT Claude pair`() {
        val owner = owner(snapshot(chatgpt, claude))
        fun blocker(value: CompareExecutionApplicationRequest) =
            (owner.requestConfirmation(value) as CompareExecutionApplicationResult.Blocked).blocker

        assertEquals(CompareExecutionApplicationBlocker.ATTACHMENTS_NOT_SUPPORTED, blocker(request(attachmentCount = 1)))
        assertEquals(CompareExecutionApplicationBlocker.THIRD_TARGET_NOT_SUPPORTED, blocker(request(targets = listOf(selection(chatgpt), selection(claude), selection(chatgpt.copy(id = ModelDeploymentId("third"), providerModelId = "third"))))))
        assertEquals(CompareExecutionApplicationBlocker.DUPLICATE_LOGICAL_MODEL, blocker(request(targets = listOf(selection(chatgpt), selection(chatgpt.copy(id = ModelDeploymentId("same-logical"), providerModelId = "same-logical"))))))
        assertEquals(CompareExecutionApplicationBlocker.DUPLICATE_DEPLOYMENT, blocker(request(targets = listOf(selection(chatgpt), ModelDeploymentSelection(CompareMvpLogicalModels.claude.id, chatgpt.id, provider.handle)))))
        assertEquals(
            CompareExecutionApplicationBlocker.ONLY_CHATGPT_AND_CLAUDE_SUPPORTED,
            blocker(request(targets = listOf(selection(chatgpt), ModelDeploymentSelection(LogicalModelId("logical.other"), claude.id, provider.handle)))),
        )
    }

    @Test fun `registry rejects unavailable catalog and unknown price before issuing any confirmation`() {
        val noCatalog = object : MultiProviderModelRegistry {
            override fun currentSnapshot() = null
            override fun previousStableSnapshot() = null
            override fun publish(snapshot: MultiProviderRegistrySnapshot) = MultiProviderRegistryMutationResult.Rejected(MultiProviderRegistryRejection.READ_ONLY_PROJECTION)
            override fun rollbackToPreviousStable() = MultiProviderRegistryMutationResult.Rejected(MultiProviderRegistryRejection.READ_ONLY_PROJECTION)
            override fun resolve(selection: ModelDeploymentSelection) = MultiProviderRegistryResolution.Rejected(MultiProviderRegistryRejection.UNKNOWN_DEPLOYMENT)
        }
        assertEquals(
            CompareExecutionApplicationBlocker.REGISTRY_REJECTED,
            (owner(noCatalog).requestConfirmation(request()) as CompareExecutionApplicationResult.Blocked).blocker,
        )
        val unknownPrice = claude.copy(pricing = ModelPricing())
        assertEquals(
            CompareExecutionApplicationBlocker.PRICE_UNAVAILABLE,
            (owner(snapshot(chatgpt, unknownPrice)).requestConfirmation(request(targets = listOf(selection(chatgpt), selection(unknownPrice)))) as CompareExecutionApplicationResult.Blocked).blocker,
        )
    }

    @Test fun `mixed currencies and arithmetic overflow fail closed instead of adding incomparable or wrapped budgets`() {
        val eurClaude = claude.copy(pricing = ModelPricing("price-claude-v1", "EUR", 11, 5))
        assertEquals(
            CompareExecutionApplicationBlocker.CURRENCY_MISMATCH,
            (owner(snapshot(chatgpt, eurClaude)).requestConfirmation(request(targets = listOf(selection(chatgpt), selection(eurClaude)))) as CompareExecutionApplicationResult.Blocked).blocker,
        )
        val overflowChatgpt = chatgpt.copy(pricing = ModelPricing("overflow", "USD", Long.MAX_VALUE, 1))
        assertEquals(
            CompareExecutionApplicationBlocker.BUDGET_OVERFLOW,
            (owner(snapshot(overflowChatgpt, claude)).requestConfirmation(request(targets = listOf(selection(overflowChatgpt), selection(claude)))) as CompareExecutionApplicationResult.Blocked).blocker,
        )
    }

    @Test fun `confirmation creates exactly two content-free independent grants while only the owner retains the pre-dispatch lease`() {
        val owner = owner(snapshot(chatgpt, claude))
        val confirmation = (owner.requestConfirmation(request()) as CompareExecutionApplicationResult.Confirmation).value
        owner.setAcknowledgement(confirmation.id, true)

        val granted = (owner.confirm(confirmation.id) as CompareExecutionApplicationResult.Granted).plan

        assertEquals(CompareSynthesisPolicy.NOT_REQUESTED, granted.synthesisPolicy)
        assertEquals(CompareSharedContextPolicy.EXPLICIT_ADOPTION_ONLY, granted.sharedContextPolicy)
        assertEquals(2, granted.branchGrants.size)
        assertEquals(listOf("compare-fixture:branch:1", "compare-fixture:branch:2"), granted.branchGrants.map { it.branchId.value })
        assertEquals(2, granted.branchGrants.map(CompareBranchExecutionGrant::grantId).toSet().size)
        assertEquals(confirmation.textSha256, granted.textSha256)
        assertEquals(confirmation.expiresAt, granted.expiresAt)
        assertTrue(granted.branchGrants.all {
            it.contextHash == "a".repeat(64) && it.textSha256 == confirmation.textSha256 &&
                it.expiresAt == confirmation.expiresAt && it.confirmationScopeFingerprint == confirmation.scopeFingerprint
        })
        assertFalse(granted.toString().contains("中文 🙂 text"))
        assertEquals(1, owner.retainedSensitiveRequestCountForContractTest())
        assertEquals(CompareExecutionApplicationBlocker.CONFIRMATION_CONSUMED, (owner.confirm(confirmation.id) as CompareExecutionApplicationResult.Blocked).blocker)
        assertEquals(CompareExecutionApplicationBlocker.CONFIRMATION_CONSUMED, (owner.cancel(confirmation.id) as CompareExecutionApplicationResult.Blocked).blocker)
        assertEquals(0, owner.retainedSensitiveRequestCountForContractTest())
    }

    @Test fun `cancel expiration and a single changed target release original text and invalidate the full summary`() {
        val clock = MutableClock(now)
        val owner = owner(snapshot(chatgpt, claude), clock = clock)
        val cancelled = (owner.requestConfirmation(request()) as CompareExecutionApplicationResult.Confirmation).value
        assertEquals(CompareExecutionApplicationBlocker.CONFIRMATION_CONSUMED, (owner.cancel(cancelled.id) as CompareExecutionApplicationResult.Blocked).blocker)
        assertEquals(0, owner.retainedSensitiveRequestCountForContractTest())

        val changed = (owner.requestConfirmation(request()) as CompareExecutionApplicationResult.Confirmation).value
        assertEquals(
            CompareExecutionApplicationBlocker.CONFIRMATION_SCOPE_CHANGED,
            (owner.invalidateForChangedTargets(changed.id, listOf(selection(chatgpt), selection(chatgpt.copy(id = ModelDeploymentId("replacement"), providerModelId = "replacement")))) as CompareExecutionApplicationResult.Blocked).blocker,
        )
        assertEquals(0, owner.retainedSensitiveRequestCountForContractTest())

        val expiring = (owner.requestConfirmation(request()) as CompareExecutionApplicationResult.Confirmation).value
        clock.instant = now.plusSeconds(CompareExecutionApplicationOwner.CONFIRMATION_TTL_MINUTES * 60 + 1)
        assertEquals(CompareExecutionApplicationBlocker.CONFIRMATION_EXPIRED, (owner.setAcknowledgement(expiring.id, true) as CompareExecutionApplicationResult.Blocked).blocker)
        assertEquals(0, owner.retainedSensitiveRequestCountForContractTest())
        assertEquals(3, owner.terminalReplayFactCountForContractTest())
    }

    @Test fun `one deployment catalog change invalidates the entire acknowledgement without silently replacing a branch`() {
        val registry = InMemoryMultiProviderModelRegistry(listOf(snapshot(chatgpt, claude)))
        val owner = owner(registry)
        val confirmation = (owner.requestConfirmation(request()) as CompareExecutionApplicationResult.Confirmation).value
        owner.setAcknowledgement(confirmation.id, true)
        registry.publish(snapshot(chatgpt, claude.copy(providerModelId = "anthropic/claude-changed", pricing = ModelPricing("price-claude-v2", "USD", 12, 5))))

        assertEquals(CompareExecutionApplicationBlocker.CONFIRMATION_SCOPE_CHANGED, (owner.confirm(confirmation.id) as CompareExecutionApplicationResult.Blocked).blocker)
        assertEquals(0, owner.retainedSensitiveRequestCountForContractTest())
    }

    @Test fun `one batch port receives the same context two grants and one lease then acceptance is one-shot`() {
        val owner = owner(snapshot(chatgpt, claude))
        val confirmation = (owner.requestConfirmation(request()) as CompareExecutionApplicationResult.Confirmation).value
        owner.setAcknowledgement(confirmation.id, true)
        val granted = (owner.confirm(confirmation.id) as CompareExecutionApplicationResult.Granted).plan
        val session = dispatchSession(granted)
        val store = DispatchStore(session)
        var calls = 0
        val result = owner.dispatch(confirmation.id, session.sessionId, store, CompareBatchDispatchPort { batch ->
            calls += 1
            assertEquals(request().context, batch.canonicalContext)
            assertEquals(granted.branchGrants, batch.grants)
            assertEquals(session.sessionId, batch.sessionId)
            assertEquals(100, batch.outputTokenLimit)
            assertEquals(session.branches.map { it.branchId }.toSet(), batch.branchExecutionHandles.map { it.branchId }.toSet())
            assertEquals(session.branches.map { "execution-${it.branchId.value}" }.toSet(), batch.branchExecutionHandles.map { it.executionId.value }.toSet())
            assertEquals("中文 🙂 text", batch.textLease.take()?.text)
            CompareBatchDispatchAcceptance.Accepted
        })

        assertEquals(CompareDispatchApplicationResult.Accepted(session.sessionId), result)
        assertEquals(1, calls)
        assertEquals(CompareDispatchRecoveryState.ACCEPTED, store.state)
        assertEquals(0, owner.retainedSensitiveRequestCountForContractTest())
        assertEquals(CompareExecutionApplicationBlocker.DISPATCH_RETRY_REQUIRES_FRESH_CONFIRMATION,
            (owner.dispatch(confirmation.id, session.sessionId, store) as CompareDispatchApplicationResult.Blocked).blocker)
    }

    @Test fun `dispatch rejection exception mismatch and expiry release the lease and require fresh confirmation`() {
        fun grantedOwner(): Triple<CompareExecutionApplicationOwner, CompareExecutionSummaryConfirmation, CompareExecutionGrantedPlan> {
            val owner = owner(snapshot(chatgpt, claude))
            val confirmation = (owner.requestConfirmation(request()) as CompareExecutionApplicationResult.Confirmation).value
            owner.setAcknowledgement(confirmation.id, true)
            return Triple(owner, confirmation, (owner.confirm(confirmation.id) as CompareExecutionApplicationResult.Granted).plan)
        }
        val (rejectedOwner, rejectedConfirmation, rejectedPlan) = grantedOwner()
        val rejectedStore = DispatchStore(dispatchSession(rejectedPlan))
        assertEquals(CompareExecutionApplicationBlocker.DISPATCH_PORT_REJECTED,
            (rejectedOwner.dispatch(rejectedConfirmation.id, rejectedStore.plan.sessionId, rejectedStore, CompareBatchDispatchPort { CompareBatchDispatchAcceptance.Rejected }) as CompareDispatchApplicationResult.Blocked).blocker)
        assertEquals(CompareDispatchRecoveryState.REJECTED_RETRY_REQUIRED, rejectedStore.state)
        assertEquals(0, rejectedOwner.retainedSensitiveRequestCountForContractTest())

        val (exceptionOwner, exceptionConfirmation, exceptionPlan) = grantedOwner()
        val exceptionStore = DispatchStore(dispatchSession(exceptionPlan))
        assertEquals(CompareExecutionApplicationBlocker.DISPATCH_PORT_EXCEPTION,
            (exceptionOwner.dispatch(exceptionConfirmation.id, exceptionStore.plan.sessionId, exceptionStore, CompareBatchDispatchPort { throw IllegalStateException("fake") }) as CompareDispatchApplicationResult.Blocked).blocker)
        assertEquals(CompareDispatchRecoveryState.REJECTED_RETRY_REQUIRED, exceptionStore.state)
        assertEquals(0, exceptionOwner.retainedSensitiveRequestCountForContractTest())

        val (mismatchOwner, mismatchConfirmation, mismatchPlan) = grantedOwner()
        val mismatched = DispatchStore(dispatchSession(mismatchPlan))
        assertEquals(CompareExecutionApplicationBlocker.DISPATCH_SESSION_SCOPE_MISMATCH,
            (mismatchOwner.dispatch(mismatchConfirmation.id, CompareConversationSessionId("missing-session"), mismatched) as CompareDispatchApplicationResult.Blocked).blocker)
        assertEquals(0, mismatchOwner.retainedSensitiveRequestCountForContractTest())

        val clock = MutableClock(now)
        val expiredOwner = owner(snapshot(chatgpt, claude), clock = clock)
        val expiredConfirmation = (expiredOwner.requestConfirmation(request()) as CompareExecutionApplicationResult.Confirmation).value
        expiredOwner.setAcknowledgement(expiredConfirmation.id, true)
        val expiredPlan = (expiredOwner.confirm(expiredConfirmation.id) as CompareExecutionApplicationResult.Granted).plan
        clock.instant = expiredConfirmation.expiresAt.plusSeconds(1)
        assertEquals(CompareExecutionApplicationBlocker.CONFIRMATION_EXPIRED,
            (expiredOwner.dispatch(expiredConfirmation.id, dispatchSession(expiredPlan).sessionId, DispatchStore(dispatchSession(expiredPlan))) as CompareDispatchApplicationResult.Blocked).blocker)
        assertEquals(0, expiredOwner.retainedSensitiveRequestCountForContractTest())
    }

    @Test fun `owner source cannot call the Direct second-confirmation or any transport runtime owner`() {
        val source = File("src/main/java/com/nanzhufeng/ai/domain/CompareExecutionApplicationOwner.kt").readText()
        val dispatchSource = File("src/main/java/com/nanzhufeng/ai/domain/CompareBatchDispatchPort.kt").readText()
        assertFalse(source.contains("DirectExecutionApplicationOwner"))
        assertFalse(source.contains("ProviderTransportRequest("))
        assertFalse(source.contains("RealTextExecutionPreflightOrchestrator"))
        assertFalse(source.contains("RealTextExecutionCoordinator"))
        assertFalse(source.contains("AppContainer"))
        assertFalse(source.contains("androidx."))
        assertFalse(dispatchSource.contains("http"))
        assertFalse(dispatchSource.contains("ApiKey"))
        assertFalse(dispatchSource.contains("ProviderTransportRequest("))
    }

    private fun owner(
        snapshot: MultiProviderRegistrySnapshot,
        auto: AutoRoutingPort = AutoRoutingPort { _, _ -> error("Auto must not run") },
        clock: Clock = Clock.fixed(now, ZoneOffset.UTC),
    ) = owner(InMemoryMultiProviderModelRegistry(listOf(snapshot)), auto, clock)

    private fun owner(
        registry: MultiProviderModelRegistry,
        auto: AutoRoutingPort = AutoRoutingPort { _, _ -> error("Auto must not run") },
        clock: Clock = Clock.fixed(now, ZoneOffset.UTC),
    ) = CompareExecutionApplicationOwner(registry, MultiModelOrchestrator(auto), clock)

    private fun request(
        targets: List<ModelDeploymentSelection> = listOf(selection(chatgpt), selection(claude)),
        attachmentCount: Int = 0,
    ) = CompareExecutionApplicationRequest(
        requestId = OrchestrationRequestId("compare-fixture"),
        context = CanonicalContextSnapshotRef(CanonicalContextSnapshotId("context-fixture"), "a".repeat(64), 1),
        targets = targets,
        text = ProviderTransportEphemeralTextInput("中文 🙂 text"),
        outputTokenLimit = 100,
        attachmentCount = attachmentCount,
    )

    private fun selection(deployment: ModelDeploymentDescriptor) =
        ModelDeploymentSelection(deployment.logicalModelId, deployment.id, deployment.provider)

    private fun deployment(
        logical: LogicalModelId,
        id: String,
        providerModel: String,
        priceVersion: String,
        currency: String,
        input: Long,
        output: Long,
    ) = ModelDeploymentDescriptor(
        ModelDeploymentId(id), logical, provider.handle, providerModel, ModelDeploymentState.ACTIVE,
        ModelPricing(priceVersion, currency, input, output),
    )

    private fun snapshot(vararg deployments: ModelDeploymentDescriptor) = MultiProviderRegistrySnapshot(
        id = MultiProviderRegistrySnapshotId("snapshot-${deployments.joinToString { it.pricing.priceVersion ?: "unknown" }}"),
        schemaVersion = 1,
        catalogVersion = "catalog-${deployments.joinToString { it.pricing.priceVersion ?: "unknown" }}",
        capturedAt = now,
        providers = listOf(provider),
        logicalModels = CompareMvpLogicalModels.defaultPair,
        deployments = deployments.toList(),
    )

    private fun dispatchSession(granted: CompareExecutionGrantedPlan) = CompareConversationSessionPlan(
        sessionId = CompareConversationSessionId("dispatch-session-${granted.confirmationId}"),
        intentId = CompareConversationSessionIntentId("dispatch-intent-${granted.confirmationId}"),
        confirmationId = granted.confirmationId,
        requestFingerprint = granted.requestFingerprint,
        conversationId = ConversationId("conversation"),
        parentUserMessageId = MessageNodeId("user-leaf"),
        canonicalContext = request().context,
        sharedCurrentLeafAtPlanning = MessageNodeId("user-leaf"),
        branches = granted.branchGrants.mapIndexed { index, grant ->
            CompareConversationBranchPlan(grant.branchId, grant, MessageNodeId("assistant-$index"), InvocationId("invocation-$index"), ProviderAttemptId("attempt-$index"), CompareBranchCancellationId("cancel-$index"))
        },
        createdAt = now,
    )

    private class DispatchStore(val plan: CompareConversationSessionPlan) : CompareConversationSessionStore {
        var state = CompareDispatchRecoveryState.RETRY_REQUIRED
        private fun readValue() = CompareConversationSessionRead(plan, plan.branches.map { branch ->
            CompareBranchRuntimeReference(branch.branchId, ConversationRealTextExecutionId("execution-${branch.branchId.value}"), "reservation-${branch.branchId.value}", 1, 0, CompareBranchReservationState.RESERVED, null, Instant.parse("2026-08-16T04:00:00Z"))
        }, state)
        override fun persist(plan: CompareConversationSessionPlan) = CompareSessionStoreResult.Stored(readValue())
        override fun read(sessionId: CompareConversationSessionId) = readValue().takeIf { sessionId == plan.sessionId }
        override fun recordTerminal(intent: CompareBranchTerminalIntent, at: Instant) = CompareSessionStoreResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        override fun recordDispatchIntent(intent: CompareDispatchIntent, at: Instant) = CompareSessionStoreResult.Stored(readValue().also { state = CompareDispatchRecoveryState.PENDING_ACCEPTANCE_RETRY_REQUIRED })
        override fun recordDispatchAccepted(intent: CompareDispatchIntent, at: Instant) = CompareSessionStoreResult.Stored(readValue().also { state = CompareDispatchRecoveryState.ACCEPTED })
        override fun recordDispatchRejected(intent: CompareDispatchIntent, at: Instant) = CompareSessionStoreResult.Stored(readValue().also { state = CompareDispatchRecoveryState.REJECTED_RETRY_REQUIRED })
        override fun storeFollowUp(intent: CompareBranchFollowUpIntent) = CompareSessionStoreResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        override fun storeAdoption(intent: CompareBranchAdoptionIntent) = CompareSessionStoreResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
        override fun storeSynthesis(intent: CompareSynthesisIntent) = CompareSessionStoreResult.Rejected(CompareConversationSessionRejection.SESSION_UNKNOWN)
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant(): Instant = instant
    }
}
