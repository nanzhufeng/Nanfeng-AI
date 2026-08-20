package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/** MM-O4-A text-only input. It remains process-memory-only until the summary confirmation ends. */
data class CompareExecutionApplicationRequest(
    val requestId: OrchestrationRequestId,
    val context: CanonicalContextSnapshotRef,
    val targets: List<ModelDeploymentSelection>,
    val text: ProviderTransportEphemeralTextInput,
    val outputTokenLimit: Long,
    val attachmentCount: Int,
) {
    init {
        require(outputTokenLimit in 1..16_384)
        require(attachmentCount >= 0)
    }
}

enum class CompareExecutionApplicationBlocker {
    ATTACHMENTS_NOT_SUPPORTED,
    EXACTLY_TWO_TARGETS_REQUIRED,
    THIRD_TARGET_NOT_SUPPORTED,
    DUPLICATE_LOGICAL_MODEL,
    DUPLICATE_DEPLOYMENT,
    ONLY_CHATGPT_AND_CLAUDE_SUPPORTED,
    ORCHESTRATION_REJECTED,
    REGISTRY_REJECTED,
    PRICE_UNAVAILABLE,
    CURRENCY_MISMATCH,
    BUDGET_OVERFLOW,
    CONFIRMATION_UNKNOWN,
    CONFIRMATION_NOT_ACKNOWLEDGED,
    CONFIRMATION_EXPIRED,
    CONFIRMATION_CONSUMED,
    CONFIRMATION_SCOPE_CHANGED,
    DISPATCH_RETRY_REQUIRES_FRESH_CONFIRMATION,
    DISPATCH_SESSION_SCOPE_MISMATCH,
    DISPATCH_STORE_REJECTED,
    DISPATCH_PORT_REJECTED,
    DISPATCH_PORT_EXCEPTION,
}

/** Safe disclosure and grant facts. None of these fields contain the input text or a transport route. */
data class CompareExecutionRecipient(
    val branchId: CompareBranchId,
    val logicalModelId: LogicalModelId,
    val deploymentId: ModelDeploymentId,
    val provider: ProviderHandle,
    val providerModelId: String,
    val catalogVersion: String,
    val priceVersion: String,
    val currencyCode: String,
    val maximumBudgetMicros: Long,
) {
    init {
        require(priceVersion.isNotBlank())
        require(currencyCode.matches(Regex("[A-Z]{3}")))
        require(maximumBudgetMicros >= 0)
    }
}

/** One unchecked, five-minute summary confirmation for both recipients. */
data class CompareExecutionSummaryConfirmation(
    val id: String,
    val requestId: OrchestrationRequestId,
    val requestFingerprint: String,
    val contextHash: String,
    val textSha256: String,
    val recipients: List<CompareExecutionRecipient>,
    val currencyCode: String,
    val totalMaximumBudgetMicros: Long,
    val scopeFingerprint: String,
    val expiresAt: Instant,
    val acknowledged: Boolean = false,
) {
    init {
        require(requestFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(contextHash.matches(Regex("[0-9a-f]{64}")))
        require(textSha256.matches(Regex("[0-9a-f]{64}")))
        require(scopeFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(recipients.size == COMPARE_MVP_TARGET_COUNT)
        require(currencyCode.matches(Regex("[A-Z]{3}")))
        require(totalMaximumBudgetMicros >= 0)
    }

    companion object {
        const val COMPARE_MVP_TARGET_COUNT = 2
    }
}

/** Content-free, independently addressable branch authorization. It cannot initiate a transport. */
data class CompareBranchExecutionGrant(
    val grantId: String,
    val branchId: CompareBranchId,
    val requestFingerprint: String,
    val contextHash: String,
    /** SHA-256 only; the branch plan never receives the original input text. */
    val textSha256: String,
    val logicalModelId: LogicalModelId,
    val deploymentId: ModelDeploymentId,
    val provider: ProviderHandle,
    val providerModelId: String,
    val catalogVersion: String,
    val priceVersion: String,
    val currencyCode: String,
    val maximumBudgetMicros: Long,
    val confirmationScopeFingerprint: String,
    /** The branch grant cannot outlive the one-time summary confirmation window. */
    val expiresAt: Instant,
) {
    init {
        require(grantId.isNotBlank())
        require(requestFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(contextHash.matches(Regex("[0-9a-f]{64}")))
        require(textSha256.matches(Regex("[0-9a-f]{64}")))
        require(confirmationScopeFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(maximumBudgetMicros >= 0)
    }
}

data class CompareExecutionGrantedPlan(
    val confirmationId: String,
    val requestFingerprint: String,
    val contextHash: String,
    val textSha256: String,
    val expiresAt: Instant,
    val synthesisPolicy: CompareSynthesisPolicy = CompareSynthesisPolicy.NOT_REQUESTED,
    val sharedContextPolicy: CompareSharedContextPolicy = CompareSharedContextPolicy.EXPLICIT_ADOPTION_ONLY,
    val branchGrants: List<CompareBranchExecutionGrant>,
) {
    init {
        require(requestFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(contextHash.matches(Regex("[0-9a-f]{64}")))
        require(textSha256.matches(Regex("[0-9a-f]{64}")))
        require(branchGrants.size == CompareExecutionSummaryConfirmation.COMPARE_MVP_TARGET_COUNT)
        require(branchGrants.all {
            it.requestFingerprint == requestFingerprint && it.contextHash == contextHash &&
                it.textSha256 == textSha256 && it.expiresAt == expiresAt
        })
    }
}

sealed interface CompareExecutionApplicationResult {
    data class Confirmation(val value: CompareExecutionSummaryConfirmation) : CompareExecutionApplicationResult
    data class Granted(val plan: CompareExecutionGrantedPlan) : CompareExecutionApplicationResult
    data class Blocked(val blocker: CompareExecutionApplicationBlocker) : CompareExecutionApplicationResult
}

sealed interface CompareDispatchApplicationResult {
    data class Accepted(val sessionId: CompareConversationSessionId) : CompareDispatchApplicationResult
    data class Blocked(val blocker: CompareExecutionApplicationBlocker) : CompareDispatchApplicationResult
}

/**
 * The only MM-O4-A application owner. It creates a summary consent and two content-free grants.
 * It has no Direct owner, credential, HTTP, transport execution, Room, Usage, UI, or production composition dependency.
 */
class CompareExecutionApplicationOwner(
    private val registry: MultiProviderModelRegistry,
    private val orchestrator: MultiModelOrchestrator,
    private val clock: Clock,
) {
    private data class IssuedConfirmation(
        val request: CompareExecutionApplicationRequest,
        val confirmation: CompareExecutionSummaryConfirmation,
        var acknowledged: Boolean = false,
    )

    private data class DispatchReadyConfirmation(
        val request: CompareExecutionApplicationRequest,
        val confirmation: CompareExecutionSummaryConfirmation,
        val grantedPlan: CompareExecutionGrantedPlan,
    )

    private data class TerminalConfirmationFact(val recordedAt: Instant)

    private sealed interface Preparation {
        data class Ready(
            val branches: List<CompareExecutionRecipient>,
            val requestFingerprint: String,
            val textSha256: String,
            val currencyCode: String,
            val totalMaximumBudgetMicros: Long,
            val scopeFingerprint: String,
        ) : Preparation

        data class Blocked(val blocker: CompareExecutionApplicationBlocker) : Preparation
    }

    private val issued = LinkedHashMap<String, IssuedConfirmation>()
    /** The only retained original-text reference after confirmation and before batch acceptance. */
    private val dispatchReady = LinkedHashMap<String, DispatchReadyConfirmation>()
    private val terminalReplayFacts = LinkedHashMap<String, TerminalConfirmationFact>()
    private var nextConfirmationSequence = 0L

    @Synchronized
    fun requestConfirmation(request: CompareExecutionApplicationRequest): CompareExecutionApplicationResult {
        val now = clock.instant()
        pruneExpiredIssued(now)
        val prepared = prepare(request)
        if (prepared is Preparation.Blocked) return CompareExecutionApplicationResult.Blocked(prepared.blocker)
        prepared as Preparation.Ready
        val confirmation = CompareExecutionSummaryConfirmation(
            id = "compare:${prepared.scopeFingerprint.take(24)}:${nextConfirmationSequence++}",
            requestId = request.requestId,
            requestFingerprint = prepared.requestFingerprint,
            contextHash = request.context.contentHash,
            textSha256 = prepared.textSha256,
            recipients = prepared.branches,
            currencyCode = prepared.currencyCode,
            totalMaximumBudgetMicros = prepared.totalMaximumBudgetMicros,
            scopeFingerprint = prepared.scopeFingerprint,
            expiresAt = now.plus(CONFIRMATION_TTL_MINUTES, ChronoUnit.MINUTES),
        )
        evictOldestIssuedIfNeeded(now)
        issued[confirmation.id] = IssuedConfirmation(request, confirmation)
        return CompareExecutionApplicationResult.Confirmation(confirmation)
    }

    @Synchronized
    fun setAcknowledgement(id: String, checked: Boolean): CompareExecutionApplicationResult {
        val now = clock.instant()
        pruneExpiredIssued(now, exceptId = id)
        val active = issued[id] ?: return if (dispatchReady.containsKey(id)) {
            CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_CONSUMED)
        } else replayOrUnknown(id)
        if (isExpired(active.confirmation)) {
            retire(id, now)
            return CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_EXPIRED)
        }
        active.acknowledged = checked
        return CompareExecutionApplicationResult.Confirmation(active.confirmation.copy(acknowledged = checked))
    }

    /** A changed target is never partially substituted: retire the whole summary and request a new one. */
    @Synchronized
    fun invalidateForChangedTargets(
        id: String,
        replacementTargets: List<ModelDeploymentSelection>,
    ): CompareExecutionApplicationResult {
        val now = clock.instant()
        pruneExpiredIssued(now, exceptId = id)
        val active = issued[id]
        val dispatchActive = dispatchReady[id]
        val confirmation = active?.confirmation ?: dispatchActive?.confirmation ?: return replayOrUnknown(id)
        val request = active?.request ?: dispatchActive!!.request
        if (isExpired(confirmation)) {
            retire(id, now)
            return CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_EXPIRED)
        }
        if (replacementTargets != request.targets) {
            retire(id, now)
            return CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_SCOPE_CHANGED)
        }
        return CompareExecutionApplicationResult.Confirmation(confirmation.copy(acknowledged = active?.acknowledged ?: true))
    }

    @Synchronized
    fun cancel(id: String): CompareExecutionApplicationResult {
        val now = clock.instant()
        pruneExpiredIssued(now, exceptId = id)
        val confirmation = issued[id]?.confirmation ?: dispatchReady[id]?.confirmation ?: return replayOrUnknown(id)
        if (isExpired(confirmation)) {
            retire(id, now)
            return CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_EXPIRED)
        }
        retire(id, now)
        return CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_CONSUMED)
    }

    @Synchronized
    fun confirm(id: String): CompareExecutionApplicationResult {
        val now = clock.instant()
        pruneExpiredIssued(now, exceptId = id)
        val active = issued[id] ?: return if (dispatchReady.containsKey(id)) {
            CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_CONSUMED)
        } else replayOrUnknown(id)
        if (isExpired(active.confirmation)) {
            retire(id, now)
            return CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_EXPIRED)
        }
        if (!active.acknowledged) {
            return CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_NOT_ACKNOWLEDGED)
        }
        val prepared = prepare(active.request)
        if (prepared !is Preparation.Ready || prepared.scopeFingerprint != active.confirmation.scopeFingerprint) {
            retire(id, now)
            return CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_SCOPE_CHANGED)
        }

        val branchGrants = prepared.branches.map { recipient ->
            CompareBranchExecutionGrant(
                grantId = "compare:${prepared.scopeFingerprint.take(24)}:${recipient.branchId.value.substringAfterLast(':')}",
                branchId = recipient.branchId,
                requestFingerprint = prepared.requestFingerprint,
                contextHash = active.request.context.contentHash,
                textSha256 = prepared.textSha256,
                logicalModelId = recipient.logicalModelId,
                deploymentId = recipient.deploymentId,
                provider = recipient.provider,
                providerModelId = recipient.providerModelId,
                catalogVersion = recipient.catalogVersion,
                priceVersion = recipient.priceVersion,
                currencyCode = recipient.currencyCode,
                maximumBudgetMicros = recipient.maximumBudgetMicros,
                confirmationScopeFingerprint = prepared.scopeFingerprint,
                expiresAt = active.confirmation.expiresAt,
            )
        }
        val grantedPlan = CompareExecutionGrantedPlan(
                confirmationId = active.confirmation.id,
                requestFingerprint = prepared.requestFingerprint,
                contextHash = active.request.context.contentHash,
                textSha256 = prepared.textSha256,
                expiresAt = active.confirmation.expiresAt,
                branchGrants = branchGrants,
            )
        // Grants are content-free, but the owner retains the sole in-memory text lease until the
        // all-or-nothing dispatch port accepts or every terminal path releases it.
        issued.remove(id)
        dispatchReady[id] = DispatchReadyConfirmation(active.request, active.confirmation, grantedPlan)
        return CompareExecutionApplicationResult.Granted(grantedPlan)
    }

    /**
     * The only MM-O4-D hand-off: the same Context, two confirmed grants and one text lease are
     * presented to a single batch port under this owner's lock. No branch-by-branch delivery is
     * possible, and all paths revoke the lease before returning.
     */
    @Synchronized
    fun dispatch(
        confirmationId: String,
        sessionId: CompareConversationSessionId,
        store: CompareConversationSessionStore,
        port: CompareBatchDispatchPort = FailClosedCompareBatchDispatchPort,
    ): CompareDispatchApplicationResult {
        val now = clock.instant()
        pruneExpiredIssued(now, exceptId = confirmationId)
        val active = dispatchReady[confirmationId]
            ?: return CompareDispatchApplicationResult.Blocked(CompareExecutionApplicationBlocker.DISPATCH_RETRY_REQUIRES_FRESH_CONFIRMATION)
        if (isExpired(active.confirmation)) {
            retire(confirmationId, now)
            return CompareDispatchApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_EXPIRED)
        }
        val prepared = prepare(active.request)
        if (prepared !is Preparation.Ready || prepared.scopeFingerprint != active.confirmation.scopeFingerprint) {
            retire(confirmationId, now)
            return CompareDispatchApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_SCOPE_CHANGED)
        }
        val session = store.read(sessionId)
        if (session == null || !matchesDispatchSession(active.grantedPlan, session.plan)) {
            retire(confirmationId, now)
            return CompareDispatchApplicationResult.Blocked(CompareExecutionApplicationBlocker.DISPATCH_SESSION_SCOPE_MISMATCH)
        }
        val intent = CompareDispatchIntent(
            id = CompareDispatchIntentId("compare:dispatch:$confirmationId"), sessionId = sessionId,
            confirmationId = confirmationId, requestFingerprint = active.grantedPlan.requestFingerprint,
            context = active.request.context, branchGrantIds = active.grantedPlan.branchGrants.map(CompareBranchExecutionGrant::grantId),
        )
        when (store.recordDispatchIntent(intent, now)) {
            is CompareSessionStoreResult.Rejected -> {
                retire(confirmationId, now)
                return CompareDispatchApplicationResult.Blocked(CompareExecutionApplicationBlocker.DISPATCH_STORE_REJECTED)
            }
            else -> Unit
        }
        val lease = CompareEphemeralTextLease(active.request.text)
        val handles = session.plan.branches.map { branch ->
            val runtime = session.runtimeReferences.first { it.branchId == branch.branchId }
            CompareBatchBranchExecutionHandle(
                branchId = branch.branchId, conversationId = session.plan.conversationId,
                parentUserMessageId = session.plan.parentUserMessageId, assistantMessageId = branch.assistantMessageId,
                invocationId = branch.invocationId, attemptId = branch.attemptId, executionId = runtime.executionId,
                cancellationId = branch.cancellationId, usageReservationReplayToken = runtime.usageReservationReplayToken,
            )
        }
        return try {
            when (port.accept(CompareBatchDispatch(sessionId, active.request.context, active.grantedPlan.branchGrants, handles, active.request.outputTokenLimit, lease))) {
                CompareBatchDispatchAcceptance.Accepted -> {
                    val stored = store.recordDispatchAccepted(intent, clock.instant())
                    retire(confirmationId, clock.instant())
                    if (stored is CompareSessionStoreResult.Rejected) {
                        CompareDispatchApplicationResult.Blocked(CompareExecutionApplicationBlocker.DISPATCH_STORE_REJECTED)
                    } else CompareDispatchApplicationResult.Accepted(sessionId)
                }
                CompareBatchDispatchAcceptance.Rejected -> {
                    store.recordDispatchRejected(intent, clock.instant())
                    retire(confirmationId, clock.instant())
                    CompareDispatchApplicationResult.Blocked(CompareExecutionApplicationBlocker.DISPATCH_PORT_REJECTED)
                }
            }
        } catch (_: Exception) {
            store.recordDispatchRejected(intent, clock.instant())
            retire(confirmationId, clock.instant())
            CompareDispatchApplicationResult.Blocked(CompareExecutionApplicationBlocker.DISPATCH_PORT_EXCEPTION)
        } finally {
            lease.release()
        }
    }

    private fun prepare(request: CompareExecutionApplicationRequest): Preparation {
        if (request.attachmentCount > 0) return Preparation.Blocked(CompareExecutionApplicationBlocker.ATTACHMENTS_NOT_SUPPORTED)
        if (request.targets.size < CompareExecutionSummaryConfirmation.COMPARE_MVP_TARGET_COUNT) {
            return Preparation.Blocked(CompareExecutionApplicationBlocker.EXACTLY_TWO_TARGETS_REQUIRED)
        }
        if (request.targets.size > CompareExecutionSummaryConfirmation.COMPARE_MVP_TARGET_COUNT) {
            return Preparation.Blocked(CompareExecutionApplicationBlocker.THIRD_TARGET_NOT_SUPPORTED)
        }
        if (request.targets.map(ModelDeploymentSelection::logicalModelId).toSet().size != request.targets.size) {
            return Preparation.Blocked(CompareExecutionApplicationBlocker.DUPLICATE_LOGICAL_MODEL)
        }
        if (request.targets.map(ModelDeploymentSelection::deploymentId).toSet().size != request.targets.size) {
            return Preparation.Blocked(CompareExecutionApplicationBlocker.DUPLICATE_DEPLOYMENT)
        }
        if (request.targets.map(ModelDeploymentSelection::logicalModelId).toSet() != CompareMvpLogicalModels.defaultPair.map(LogicalModelDescriptor::id).toSet()) {
            return Preparation.Blocked(CompareExecutionApplicationBlocker.ONLY_CHATGPT_AND_CLAUDE_SUPPORTED)
        }
        val planned = orchestrator.plan(
            MultiModelOrchestrationRequest.Compare(
                requestId = request.requestId,
                context = request.context,
                targets = request.targets,
                maxParallelTargets = CompareExecutionSummaryConfirmation.COMPARE_MVP_TARGET_COUNT,
            ),
        )
        val plan = (planned as? MultiModelOrchestrationResult.Planned)?.plan as? MultiModelExecutionPlan.Compare
            ?: return Preparation.Blocked(CompareExecutionApplicationBlocker.ORCHESTRATION_REJECTED)
        if (plan.branches.size != CompareExecutionSummaryConfirmation.COMPARE_MVP_TARGET_COUNT ||
            plan.branches.any { it.target.context != request.context }
        ) return Preparation.Blocked(CompareExecutionApplicationBlocker.ORCHESTRATION_REJECTED)

        val recipients = plan.branches.map { branch ->
            val resolved = registry.resolve(branch.target.selection)
            if (resolved !is MultiProviderRegistryResolution.Resolved) {
                return Preparation.Blocked(
                    if ((resolved as MultiProviderRegistryResolution.Rejected).reason == MultiProviderRegistryRejection.PRICE_UNAVAILABLE) {
                        CompareExecutionApplicationBlocker.PRICE_UNAVAILABLE
                    } else {
                        CompareExecutionApplicationBlocker.REGISTRY_REJECTED
                    },
                )
            }
            val pricing = resolved.deployment.pricing
            val priceVersion = pricing.priceVersion ?: return Preparation.Blocked(CompareExecutionApplicationBlocker.PRICE_UNAVAILABLE)
            val currency = pricing.currencyCode ?: return Preparation.Blocked(CompareExecutionApplicationBlocker.PRICE_UNAVAILABLE)
            val input = pricing.inputMicrosPerToken ?: return Preparation.Blocked(CompareExecutionApplicationBlocker.PRICE_UNAVAILABLE)
            val output = pricing.outputMicrosPerToken ?: return Preparation.Blocked(CompareExecutionApplicationBlocker.PRICE_UNAVAILABLE)
            val maximum = ConservativeInputBillingBudget.maximumBudgetMicros(request.text.text, request.outputTokenLimit, input, output)
                ?: return Preparation.Blocked(CompareExecutionApplicationBlocker.BUDGET_OVERFLOW)
            CompareExecutionRecipient(
                branchId = branch.branchId,
                logicalModelId = resolved.deployment.logicalModelId,
                deploymentId = resolved.deployment.id,
                provider = resolved.provider.handle,
                providerModelId = resolved.deployment.providerModelId,
                catalogVersion = resolved.snapshot.catalogVersion,
                priceVersion = priceVersion,
                currencyCode = currency,
                maximumBudgetMicros = maximum,
            )
        }
        val currency = recipients.first().currencyCode
        if (recipients.any { it.currencyCode != currency }) {
            return Preparation.Blocked(CompareExecutionApplicationBlocker.CURRENCY_MISMATCH)
        }
        var total = 0L
        recipients.forEach { recipient ->
            total = safeAdd(total, recipient.maximumBudgetMicros)
                ?: return Preparation.Blocked(CompareExecutionApplicationBlocker.BUDGET_OVERFLOW)
        }
        val textSha256 = sha256(request.text.text)
        val requestFingerprint = sha256(
            listOf(
                "request-id=${request.requestId.value}",
                "context-id=${request.context.id.value}",
                "context-hash=${request.context.contentHash}",
                "context-revision=${request.context.revision}",
                "text-sha256=$textSha256",
            ).joinToString("\n"),
        )
        val scopeFingerprint = sha256(
            buildList {
                add("request-fingerprint=$requestFingerprint")
                add("output-token-limit=${request.outputTokenLimit}")
                add("currency=$currency")
                add("total-maximum-budget-micros=$total")
                recipients.forEach { recipient ->
                    add(
                        listOf(
                            "branch=${recipient.branchId.value}",
                            "logical-model=${recipient.logicalModelId.value}",
                            "deployment=${recipient.deploymentId.value}",
                            "provider=${recipient.provider.value}",
                            "provider-model=${recipient.providerModelId}",
                            "catalog=${recipient.catalogVersion}",
                            "price=${recipient.priceVersion}",
                            "currency=${recipient.currencyCode}",
                            "maximum-budget-micros=${recipient.maximumBudgetMicros}",
                        ).joinToString("\n"),
                    )
                }
            }.joinToString("\n"),
        )
        return Preparation.Ready(recipients, requestFingerprint, textSha256, currency, total, scopeFingerprint)
    }

    private fun isExpired(confirmation: CompareExecutionSummaryConfirmation): Boolean =
        !clock.instant().isBefore(confirmation.expiresAt)

    private fun pruneExpiredIssued(now: Instant, exceptId: String? = null) {
        issued.entries.toList().forEach { (id, active) ->
            if (id != exceptId && !now.isBefore(active.confirmation.expiresAt)) retire(id, now)
        }
        dispatchReady.entries.toList().forEach { (id, active) ->
            if (id != exceptId && !now.isBefore(active.confirmation.expiresAt)) retire(id, now)
        }
        val iterator = terminalReplayFacts.entries.iterator()
        while (iterator.hasNext()) {
            if (!now.isBefore(iterator.next().value.recordedAt.plus(TERMINAL_FACT_TTL_MINUTES, ChronoUnit.MINUTES))) iterator.remove()
        }
    }

    private fun evictOldestIssuedIfNeeded(now: Instant) {
        while (issued.size + dispatchReady.size >= MAX_ACTIVE_CONFIRMATIONS) {
            val oldest = issued.entries.firstOrNull()?.key ?: dispatchReady.entries.first().key
            retire(oldest, now)
        }
    }

    private fun retire(id: String, now: Instant) {
        issued.remove(id)
        dispatchReady.remove(id)
        terminalReplayFacts[id] = TerminalConfirmationFact(now)
        while (terminalReplayFacts.size > MAX_TERMINAL_REPLAY_FACTS) terminalReplayFacts.remove(terminalReplayFacts.entries.first().key)
    }

    private fun replayOrUnknown(id: String): CompareExecutionApplicationResult =
        if (terminalReplayFacts.containsKey(id)) {
            CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_CONSUMED)
        } else {
            CompareExecutionApplicationResult.Blocked(CompareExecutionApplicationBlocker.CONFIRMATION_UNKNOWN)
        }

    private fun safeAdd(left: Long, right: Long): Long? = runCatching { Math.addExact(left, right) }.getOrNull()

    private fun matchesDispatchSession(granted: CompareExecutionGrantedPlan, session: CompareConversationSessionPlan): Boolean =
        session.confirmationId == granted.confirmationId && session.requestFingerprint == granted.requestFingerprint &&
            session.canonicalContext.contentHash == granted.contextHash && session.branches.map { it.grant }.toSet() == granted.branchGrants.toSet()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    internal fun retainedSensitiveRequestCountForContractTest(): Int = issued.size + dispatchReady.size
    internal fun terminalReplayFactCountForContractTest(): Int = terminalReplayFacts.size

    companion object {
        const val CONFIRMATION_TTL_MINUTES = 5L
        const val TERMINAL_FACT_TTL_MINUTES = 10L
        const val MAX_ACTIVE_CONFIRMATIONS = 128
        const val MAX_TERMINAL_REPLAY_FACTS = 256
    }
}
