package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.security.MessageDigest

/** MM-O3-A: unregistered application boundary for an explicitly selected Direct deployment. */
enum class DirectExecutionTextCategory { CURRENT_DRAFT_TEXT_ONLY }

enum class DirectExecutionApplicationBlocker {
    ATTACHMENTS_NOT_SUPPORTED,
    REGISTRY_REJECTED,
    PROVIDER_UNSUPPORTED,
    PRICE_UNAVAILABLE,
    CONFIRMATION_UNKNOWN,
    CONFIRMATION_NOT_ACKNOWLEDGED,
    CONFIRMATION_EXPIRED,
    CONFIRMATION_CONSUMED,
    CONFIRMATION_SCOPE_CHANGED,
    PREFLIGHT_BLOCKED,
}

data class DirectExecutionApplicationRequest(
    val requestId: OrchestrationRequestId,
    val context: CanonicalContextSnapshotRef,
    val target: ModelDeploymentSelection,
    /** Process-memory-only text, retained only until confirmation reaches a terminal state. */
    val text: ProviderTransportEphemeralTextInput,
    val execution: ConversationRealTextExecutionRequest,
    val presetId: ModelPresetId,
    val outputTokenLimit: Long,
    val attachmentCount: Int,
    val usageSource: UsageLedgerSource,
) {
    init {
        require(outputTokenLimit in 1..16_384)
        require(attachmentCount >= 0)
    }
}

/** All fields are safe, immutable scope facts required for explicit user confirmation. */
data class DirectExecutionConfirmation(
    val id: String,
    val requestFingerprint: String,
    val contextHash: String,
    val logicalModelId: LogicalModelId,
    val deploymentId: ModelDeploymentId,
    val provider: ProviderHandle,
    val providerModelId: String,
    val textCategory: DirectExecutionTextCategory,
    val catalogVersion: String,
    val priceVersion: String,
    val currencyCode: String,
    val maximumBudgetMicros: Long,
    /** Opaque digest of the complete confirmation scope; it never contains request text. */
    val scopeFingerprint: String,
    val expiresAt: Instant,
    val acknowledged: Boolean = false,
) {
    init {
        require(requestFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(contextHash.matches(Regex("[a-f0-9]{64}")))
        require(scopeFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(priceVersion.isNotBlank() && currencyCode.matches(Regex("[A-Z]{3}")))
        require(maximumBudgetMicros >= 0)
    }
}

sealed interface DirectExecutionApplicationResult {
    data class Confirmation(val value: DirectExecutionConfirmation) : DirectExecutionApplicationResult
    data class Coordinated(val result: RealTextExecutionCoordinatorResult) : DirectExecutionApplicationResult
    data class Blocked(val blocker: DirectExecutionApplicationBlocker) : DirectExecutionApplicationResult
}

/**
 * The sole MM-O3-A Direct owner. It resolves an exact deployment, creates a one-time confirmation,
 * then delegates only a confirmed scope to P3 preflight and coordinator. It owns no UI, Key bytes,
 * HTTP client, Room registration, usage ledger, or Auto Router.
 */
class DirectExecutionApplicationOwner(
    private val registry: MultiProviderModelRegistry,
    private val orchestrator: MultiModelOrchestrator,
    private val preflight: RealTextExecutionPreflightOrchestrator,
    private val coordinator: RealTextExecutionCoordinator,
    private val clock: Clock,
) {
    private data class IssuedConfirmation(
        val request: DirectExecutionApplicationRequest,
        val confirmation: DirectExecutionConfirmation,
        var acknowledged: Boolean = false,
    )

    /** Content-free replay fact retained only briefly after request material is released. */
    private data class TerminalConfirmationFact(
        val disposition: TerminalConfirmationDisposition,
        val recordedAt: Instant,
    )

    private enum class TerminalConfirmationDisposition {
        CANCELLED,
        EXPIRED,
        CONFIRMED,
        SCOPE_CHANGED,
        PREFLIGHT_BLOCKED,
        PROVIDER_UNSUPPORTED,
    }

    private val issued = LinkedHashMap<String, IssuedConfirmation>()
    private val terminalReplayFacts = LinkedHashMap<String, TerminalConfirmationFact>()
    private var nextConfirmationSequence = 0L

    @Synchronized
    fun requestConfirmation(request: DirectExecutionApplicationRequest): DirectExecutionApplicationResult {
        pruneExpiredIssued(clock.instant())
        if (request.attachmentCount > 0) return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.ATTACHMENTS_NOT_SUPPORTED)
        val planned = orchestrator.plan(MultiModelOrchestrationRequest.Direct(request.requestId, request.context, request.target))
        val direct = (planned as? MultiModelOrchestrationResult.Planned)?.plan as? MultiModelExecutionPlan.Direct
            ?: return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.REGISTRY_REJECTED)
        val resolved = registry.resolve(direct.target.selection)
        if (resolved !is MultiProviderRegistryResolution.Resolved) {
            return DirectExecutionApplicationResult.Blocked(
                if ((resolved as MultiProviderRegistryResolution.Rejected).reason == MultiProviderRegistryRejection.PRICE_UNAVAILABLE) {
                    DirectExecutionApplicationBlocker.PRICE_UNAVAILABLE
                } else {
                    DirectExecutionApplicationBlocker.REGISTRY_REJECTED
                },
            )
        }
        val providerId = providerId(resolved.provider.handle)
            ?: return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.PROVIDER_UNSUPPORTED)
        val pricing = resolved.deployment.pricing
        val priceVersion = pricing.priceVersion ?: return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.PRICE_UNAVAILABLE)
        val currency = pricing.currencyCode ?: return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.PRICE_UNAVAILABLE)
        val input = pricing.inputMicrosPerToken ?: return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.PRICE_UNAVAILABLE)
        val output = pricing.outputMicrosPerToken ?: return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.PRICE_UNAVAILABLE)
        val maximum = ConservativeInputBillingBudget.maximumBudgetMicros(request.text.text, request.outputTokenLimit, input, output)
            ?: return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.PRICE_UNAVAILABLE)
        val now = clock.instant()
        val scopeFingerprint = confirmationScopeFingerprint(request, resolved, priceVersion, currency, maximum)
        val confirmation = DirectExecutionConfirmation(
            id = "direct:${scopeFingerprint.take(24)}:${nextConfirmationSequence++}",
            requestFingerprint = request.execution.requestFingerprint,
            contextHash = request.context.contentHash,
            logicalModelId = resolved.deployment.logicalModelId,
            deploymentId = resolved.deployment.id,
            provider = resolved.provider.handle,
            providerModelId = resolved.deployment.providerModelId,
            textCategory = DirectExecutionTextCategory.CURRENT_DRAFT_TEXT_ONLY,
            catalogVersion = resolved.snapshot.catalogVersion,
            priceVersion = priceVersion,
            currencyCode = currency,
            maximumBudgetMicros = maximum,
            scopeFingerprint = scopeFingerprint,
            expiresAt = now.plus(CONFIRMATION_TTL_MINUTES, ChronoUnit.MINUTES),
        )
        evictOldestIssuedIfNeeded(now)
        issued[confirmation.id] = IssuedConfirmation(request, confirmation)
        // ProviderId is deliberately resolved only to prove this deployment can enter P3 later.
        check(providerId == ProviderId.OPENROUTER)
        return DirectExecutionApplicationResult.Confirmation(confirmation)
    }

    @Synchronized
    fun setAcknowledgement(id: String, checked: Boolean): DirectExecutionApplicationResult {
        val now = clock.instant()
        pruneExpiredIssued(now, exceptId = id)
        val active = issued[id] ?: return replayOrUnknown(id)
        if (isExpired(active.confirmation)) {
            retire(id, TerminalConfirmationDisposition.EXPIRED, now)
            return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.CONFIRMATION_EXPIRED)
        }
        active.acknowledged = checked
        return DirectExecutionApplicationResult.Confirmation(active.confirmation.copy(acknowledged = checked))
    }

    @Synchronized
    fun cancel(id: String): DirectExecutionApplicationResult {
        val now = clock.instant()
        pruneExpiredIssued(now, exceptId = id)
        val active = issued[id] ?: return replayOrUnknown(id)
        if (isExpired(active.confirmation)) {
            retire(id, TerminalConfirmationDisposition.EXPIRED, now)
            return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.CONFIRMATION_EXPIRED)
        }
        retire(id, TerminalConfirmationDisposition.CANCELLED, now)
        return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.CONFIRMATION_CONSUMED)
    }

    @Synchronized
    fun confirm(id: String): DirectExecutionApplicationResult {
        val now = clock.instant()
        pruneExpiredIssued(now, exceptId = id)
        val active = issued[id] ?: return replayOrUnknown(id)
        if (isExpired(active.confirmation)) {
            retire(id, TerminalConfirmationDisposition.EXPIRED, now)
            return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.CONFIRMATION_EXPIRED)
        }
        if (!active.acknowledged) return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.CONFIRMATION_NOT_ACKNOWLEDGED)
        val resolved = registry.resolve(active.request.target)
        val currentMaximum = (resolved as? MultiProviderRegistryResolution.Resolved)?.let { current ->
            val pricing = current.deployment.pricing
            val input = pricing.inputMicrosPerToken ?: return@let null
            val output = pricing.outputMicrosPerToken ?: return@let null
            ConservativeInputBillingBudget.maximumBudgetMicros(active.request.text.text, active.request.outputTokenLimit, input, output)
        }
        if (resolved !is MultiProviderRegistryResolution.Resolved || currentMaximum == null ||
            !sameScope(active.confirmation, active.request, resolved, currentMaximum)
        ) {
            retire(id, TerminalConfirmationDisposition.SCOPE_CHANGED, now)
            return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.CONFIRMATION_SCOPE_CHANGED)
        }
        val providerId = providerId(resolved.provider.handle)
            ?: run {
                retire(id, TerminalConfirmationDisposition.PROVIDER_UNSUPPORTED, now)
                return DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.PROVIDER_UNSUPPORTED)
            }
        // The owner map releases the only retained original request before P3 is invoked.
        retire(id, TerminalConfirmationDisposition.CONFIRMED, now)
        val fee = RealTextFeeConfirmation(
            confirmationId = active.confirmation.id,
            approvedAt = clock.instant(),
            requestFingerprint = active.request.execution.requestFingerprint,
            priceVersion = active.confirmation.priceVersion,
            currencyCode = active.confirmation.currencyCode,
            acknowledgedMaximumMicros = active.confirmation.maximumBudgetMicros,
        )
        val transport = ProviderTransportRequest(
            execution = ConversationRealTextExecutionTransportHandle(
                active.request.execution.executionId, active.request.execution.invocationId,
                active.request.execution.attemptId, active.request.execution.requestFingerprint,
            ),
            route = ProviderTransportRoute(
                ProviderTransportProviderHandle(resolved.provider.handle.value),
                ProviderTransportModelId(resolved.deployment.providerModelId),
            ),
            input = active.request.text,
        )
        val preflightResult = preflight.preflight(
            RealTextExecutionPreflightRequest(
                preflightId = RealTextExecutionPreflightId("direct:${active.request.execution.requestFingerprint.take(32)}"),
                executionRequest = active.request.execution,
                transportRequest = transport,
                providerId = providerId,
                presetId = active.request.presetId,
                outputTokenLimit = active.request.outputTokenLimit,
                feeConfirmation = fee,
                attachments = emptyList(),
                usageSource = active.request.usageSource,
            ),
        )
        return when (preflightResult) {
            is RealTextExecutionPreflightResult.Blocked -> {
                replaceTerminalDisposition(id, TerminalConfirmationDisposition.PREFLIGHT_BLOCKED, now)
                DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.PREFLIGHT_BLOCKED)
            }
            is RealTextExecutionPreflightResult.Ready -> DirectExecutionApplicationResult.Coordinated(coordinator.coordinate(preflightResult.plan))
        }
    }

    private fun isExpired(confirmation: DirectExecutionConfirmation): Boolean = !clock.instant().isBefore(confirmation.expiresAt)

    private fun sameScope(
        confirmation: DirectExecutionConfirmation,
        request: DirectExecutionApplicationRequest,
        resolved: MultiProviderRegistryResolution.Resolved,
        maximumBudgetMicros: Long,
    ): Boolean =
        confirmation.logicalModelId == resolved.deployment.logicalModelId &&
            confirmation.deploymentId == resolved.deployment.id &&
            confirmation.provider == resolved.provider.handle &&
            confirmation.providerModelId == resolved.deployment.providerModelId &&
            confirmation.catalogVersion == resolved.snapshot.catalogVersion &&
            confirmation.priceVersion == resolved.deployment.pricing.priceVersion &&
            confirmation.currencyCode == resolved.deployment.pricing.currencyCode &&
            confirmation.maximumBudgetMicros == maximumBudgetMicros &&
            confirmation.scopeFingerprint == confirmationScopeFingerprint(
                request,
                resolved,
                confirmation.priceVersion,
                confirmation.currencyCode,
                maximumBudgetMicros,
            )

    private fun providerId(handle: ProviderHandle): ProviderId? = when (handle.value) {
        "openrouter" -> ProviderId.OPENROUTER
        else -> null
    }

    private fun confirmationScopeFingerprint(
        request: DirectExecutionApplicationRequest,
        resolved: MultiProviderRegistryResolution.Resolved,
        priceVersion: String,
        currencyCode: String,
        maximumBudgetMicros: Long,
    ): String = sha256(
        listOf(
            "request-id=${request.requestId.value}",
            "execution-fingerprint=${request.execution.requestFingerprint}",
            "context-hash=${request.context.contentHash}",
            "logical-model=${resolved.deployment.logicalModelId.value}",
            "deployment=${resolved.deployment.id.value}",
            "provider=${resolved.provider.handle.value}",
            "provider-model=${resolved.deployment.providerModelId}",
            "catalog=${resolved.snapshot.catalogVersion}",
            "price=$priceVersion",
            "currency=$currencyCode",
            "text-sha256=${sha256(request.text.text)}",
            "output-token-limit=${request.outputTokenLimit}",
            "maximum-budget-micros=$maximumBudgetMicros",
        ).joinToString("\n"),
    )

    private fun pruneExpiredIssued(now: Instant, exceptId: String? = null) {
        issued.entries.toList().forEach { (id, active) ->
            if (id != exceptId && !now.isBefore(active.confirmation.expiresAt)) {
                retire(id, TerminalConfirmationDisposition.EXPIRED, now)
            }
        }
        val terminalIterator = terminalReplayFacts.entries.iterator()
        while (terminalIterator.hasNext()) {
            val fact = terminalIterator.next().value
            if (!now.isBefore(fact.recordedAt.plus(TERMINAL_FACT_TTL_MINUTES, ChronoUnit.MINUTES))) {
                terminalIterator.remove()
            }
        }
    }

    private fun evictOldestIssuedIfNeeded(now: Instant) {
        while (issued.size >= MAX_ACTIVE_CONFIRMATIONS) {
            val oldestId = issued.entries.first().key
            retire(oldestId, TerminalConfirmationDisposition.CANCELLED, now)
        }
    }

    private fun retire(id: String, disposition: TerminalConfirmationDisposition, now: Instant) {
        issued.remove(id)
        terminalReplayFacts[id] = TerminalConfirmationFact(disposition, now)
        while (terminalReplayFacts.size > MAX_TERMINAL_REPLAY_FACTS) {
            terminalReplayFacts.remove(terminalReplayFacts.entries.first().key)
        }
    }

    private fun replaceTerminalDisposition(id: String, disposition: TerminalConfirmationDisposition, now: Instant) {
        if (terminalReplayFacts.containsKey(id)) terminalReplayFacts[id] = TerminalConfirmationFact(disposition, now)
    }

    private fun replayOrUnknown(id: String): DirectExecutionApplicationResult =
        if (terminalReplayFacts.containsKey(id)) {
            DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.CONFIRMATION_CONSUMED)
        } else {
            DirectExecutionApplicationResult.Blocked(DirectExecutionApplicationBlocker.CONFIRMATION_UNKNOWN)
        }

    internal fun retainedSensitiveRequestCountForContractTest(): Int = issued.size
    internal fun terminalReplayFactCountForContractTest(): Int = terminalReplayFacts.size

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        const val CONFIRMATION_TTL_MINUTES = 5L
        const val TERMINAL_FACT_TTL_MINUTES = 10L
        const val MAX_ACTIVE_CONFIRMATIONS = 128
        const val MAX_TERMINAL_REPLAY_FACTS = 256
    }
}
