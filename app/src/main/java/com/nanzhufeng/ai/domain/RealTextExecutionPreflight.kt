package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

/**
 * P3 fail-closed preflight/orchestrator. It builds a transient plan only: it never invokes a
 * transport, reads a credential, opens an attachment, consumes an authorization, appends Usage,
 * creates a receipt, or writes any business storage.
 */
const val REAL_TEXT_EXECUTION_PREFLIGHT_PROTOCOL = "real-text-execution-preflight-v1"

enum class RealTextPreflightBlocker {
    CANCELLED,
    EXECUTION_BINDING_MISMATCH,
    PROVIDER_UNSUPPORTED,
    PROVIDER_CONFIGURATION_UNAVAILABLE,
    PROVIDER_DISABLED,
    PRESET_MISMATCH,
    CREDENTIAL_NOT_CONFIGURED,
    REGISTRY_UNVERIFIED,
    MODEL_UNAVAILABLE,
    MODEL_CAPABILITY_UNAVAILABLE,
    MODEL_ROUTE_MISMATCH,
    TEXT_INVALID,
    PRICE_UNAVAILABLE,
    FEE_CONFIRMATION_REQUIRED,
    FEE_CONFIRMATION_SCOPE_MISMATCH,
    FEE_CONFIRMATION_EXPIRED,
    FEE_CONFIRMATION_INSUFFICIENT,
    ATTACHMENT_INELIGIBLE,
    ATTACHMENT_AUTHORIZATION_REQUIRED,
    ATTACHMENT_SCOPE_MISMATCH,
}

@JvmInline
value class RealTextExecutionPreflightId(val value: String) {
    init { require(value.matches(Regex("[A-Za-z0-9._:-]{1,160}"))) { "预检标识不合法。" } }
}

/** Future visible confirmation may create this proof; preflight only validates its safe fields. */
data class RealTextFeeConfirmation(
    val confirmationId: String,
    val approvedAt: Instant,
    val requestFingerprint: String,
    val priceVersion: String,
    val currencyCode: String,
    val acknowledgedMaximumMicros: Long,
) {
    init {
        require(confirmationId.matches(Regex("[A-Za-z0-9._:-]{1,160}"))) { "费用确认标识不合法。" }
        require(requestFingerprint.matches(Regex("[0-9a-f]{64}"))) { "费用确认请求指纹不合法。" }
        require(priceVersion.isNotBlank() && currencyCode.matches(Regex("[A-Z]{3}")) && acknowledgedMaximumMicros >= 0) {
            "费用确认内容不完整。"
        }
    }

    fun fingerprint(): String = sha256("$confirmationId|${approvedAt.toEpochMilli()}|$requestFingerprint|$priceVersion|$currencyCode|$acknowledgedMaximumMicros")
}

/** No file reference or bytes. A summary proves a separate P1 gate was active, but is not consumed. */
data class RealTextAttachmentPreflightProof(
    val intent: AttachmentEgressIntent,
    val authorization: AttachmentEgressSafeSummary?,
)

data class RealTextExecutionPreflightRequest(
    val preflightId: RealTextExecutionPreflightId,
    val executionRequest: ConversationRealTextExecutionRequest,
    val transportRequest: ProviderTransportRequest,
    val providerId: ProviderId,
    val presetId: ModelPresetId,
    val outputTokenLimit: Long,
    val feeConfirmation: RealTextFeeConfirmation?,
    val attachments: List<RealTextAttachmentPreflightProof> = emptyList(),
    val usageSource: UsageLedgerSource,
) {
    init {
        require(outputTokenLimit in 1..16_384) { "输出 token 上限不合法。" }
        require(attachments.map { it.intent.attachmentId }.distinct().size == attachments.size) { "附件预检不能重复。" }
    }
}

/** Identity only; it supplies a future cancellation owner no action or transport handle. */
data class RealTextExecutionCancellationIdentity(
    val preflightId: RealTextExecutionPreflightId,
    val executionId: ConversationRealTextExecutionId,
    val invocationId: InvocationId,
    val attemptId: ProviderAttemptId,
    val requestFingerprint: String,
)

/** A proposed P0 BUDGET_RESERVATION fact, deliberately not a UsageLedgerEntry and never appended here. */
data class RealTextUsageReservationPlan(
    val replayToken: String,
    val executionId: ConversationRealTextExecutionId,
    val conversationId: ConversationId,
    val branchLeafMessageId: MessageNodeId,
    val invocationId: InvocationId,
    val attemptId: ProviderAttemptId,
    val requestedModelId: ProviderTransportModelId,
    /** Conservative local billing ceiling, not Provider-reported token usage. */
    val conservativeInputTokenUpperBound: Long,
    val reservedOutputTokens: Long,
    val budgetMicros: Long,
    val currencyCode: String,
    val reconciliationFingerprint: String,
    val source: UsageLedgerSource,
) {
    init {
        require(replayToken.matches(Regex("[A-Za-z0-9._:-]{1,160}"))) { "预留重放标识不合法。" }
        require(conservativeInputTokenUpperBound >= 0 && reservedOutputTokens > 0 && budgetMicros >= 0) { "预留数量不合法。" }
        require(currencyCode.matches(Regex("[A-Z]{3}")) && reconciliationFingerprint.matches(Regex("[0-9a-f]{64}"))) {
            "预留安全元数据不合法。"
        }
    }

    /** Compatibility projection for the existing Usage adapter; never Provider-reported usage. */
    val estimatedInputTokens: Long get() = conservativeInputTokenUpperBound
}

/** Safe attachment projection only; it cannot be used to read or upload an asset. */
data class RealTextAttachmentPlan(
    val attachmentId: AttachmentId,
    val attachmentSha256: String,
    val mimeType: String,
    val byteCount: Long,
    val authorizationId: AttachmentEgressAuthorizationId,
    val authorizationExpiresAt: Instant,
    val capabilityFingerprint: String,
    val consentFingerprint: String,
    val feeConfirmationFingerprint: String,
)

/** Ready is only a future-call prerequisite. It does not retain text or carry a transport/client. */
data class RealTextVerifiedCompareDeployment(
    val logicalModelId: LogicalModelId,
    val deploymentId: ModelDeploymentId,
    val provider: ProviderHandle,
    val catalogVersion: String,
    val priceVersion: String,
) {
    init { require(catalogVersion.isNotBlank() && priceVersion.isNotBlank()) }
}

data class RealTextExecutionReadyPlan(
    val preflightId: RealTextExecutionPreflightId,
    val cancellation: RealTextExecutionCancellationIdentity,
    val providerHandle: ProviderTransportProviderHandle,
    val modelId: ProviderTransportModelId,
    /** Normal P3 selection only; Compare keeps its verified deployment separately. */
    val presetId: ModelPresetId? = null,
    /** Content-free Compare-only target. It never substitutes for a user Settings preset. */
    val verifiedCompareDeployment: RealTextVerifiedCompareDeployment? = null,
    val feeConfirmationFingerprint: String,
    val usageReservation: RealTextUsageReservationPlan,
    val attachments: List<RealTextAttachmentPlan>,
    val plannedAt: Instant,
) {
    init { require((presetId == null) != (verifiedCompareDeployment == null)) }
}

sealed interface RealTextExecutionPreflightResult {
    data class Ready(val plan: RealTextExecutionReadyPlan) : RealTextExecutionPreflightResult
    data class Blocked(val blockers: Set<RealTextPreflightBlocker>) : RealTextExecutionPreflightResult
}

interface RealTextPreflightCancellation {
    fun isCancelled(): Boolean
}

object RealTextPreflightNotCancelled : RealTextPreflightCancellation {
    override fun isCancelled(): Boolean = false
}

/** Pure local validity check; it does not sanitize/rewrite or persist the transient text. */
object RealTextPreflightTextPolicy {
    fun isAllowed(input: ProviderTransportEphemeralTextInput): Boolean {
        val text = input.text
        return text.length <= MAX_TEXT_CHARS && text.none { it.code in 0..8 || it.code in 11..12 || it.code in 14..31 } &&
            SENSITIVE_MARKERS.none { it.containsMatchIn(text) }
    }

    private const val MAX_TEXT_CHARS = 12_000
    private val SENSITIVE_MARKERS = listOf(
        Regex("(?i)\\b(authorization|api[ _-]?key|bearer)\\b"),
        Regex("(?i)\\bsk-[a-z0-9_-]{12,}"),
    )
}

/**
 * A read-only, unregistered orchestrator. Its only credential interaction is
 * [ProviderCredentialStore.credentialPresence], whose contract forbids exposing credential bytes.
 */
class RealTextExecutionPreflightOrchestrator(
    private val settings: ModelServiceSettingsRepository,
    private val credentials: ProviderCredentialStore,
    private val registry: VersionedModelRegistry,
    private val clock: Clock,
) {
    fun preflight(
        request: RealTextExecutionPreflightRequest,
        cancellation: RealTextPreflightCancellation = RealTextPreflightNotCancelled,
    ): RealTextExecutionPreflightResult {
        if (cancellation.isCancelled()) return blocked(RealTextPreflightBlocker.CANCELLED)
        if (!sameExecutionBinding(request.executionRequest, request.transportRequest)) {
            return blocked(RealTextPreflightBlocker.EXECUTION_BINDING_MISMATCH)
        }
        val provider = providerFor(request.transportRequest.route.providerHandle) ?: return blocked(RealTextPreflightBlocker.PROVIDER_UNSUPPORTED)
        if (provider != request.providerId) return blocked(RealTextPreflightBlocker.PROVIDER_UNSUPPORTED)
        val configured = runCatching { settings.load(provider) }.getOrNull()
            ?: return blocked(RealTextPreflightBlocker.PROVIDER_CONFIGURATION_UNAVAILABLE)
        if (configured.providerId != provider || !configured.enabled) return blocked(RealTextPreflightBlocker.PROVIDER_DISABLED)
        if (configured.presetId != request.presetId) return blocked(RealTextPreflightBlocker.PRESET_MISMATCH)
        if (credentials.credentialPresence(provider) != CredentialPresence.PRESENT) return blocked(RealTextPreflightBlocker.CREDENTIAL_NOT_CONFIGURED)
        val resolved = registry.resolve(provider, request.presetId)
        if (resolved !is ModelRegistryResolution.Resolved) return blocked(
            if ((resolved as ModelRegistryResolution.Rejected).error == AiTaskError.ModelRegistrySnapshotUnverified) {
                RealTextPreflightBlocker.REGISTRY_UNVERIFIED
            } else RealTextPreflightBlocker.MODEL_UNAVAILABLE,
        )
        val model = resolved.model
        if (!model.capabilities.supportsText) return blocked(RealTextPreflightBlocker.MODEL_CAPABILITY_UNAVAILABLE)
        if (model.id != request.transportRequest.route.modelId.value) return blocked(RealTextPreflightBlocker.MODEL_ROUTE_MISMATCH)
        if (!RealTextPreflightTextPolicy.isAllowed(request.transportRequest.input)) return blocked(RealTextPreflightBlocker.TEXT_INVALID)
        if (cancellation.isCancelled()) return blocked(RealTextPreflightBlocker.CANCELLED)
        val reservation = reservationPlan(request, model) ?: return blocked(RealTextPreflightBlocker.PRICE_UNAVAILABLE)
        val confirmation = request.feeConfirmation ?: return blocked(RealTextPreflightBlocker.FEE_CONFIRMATION_REQUIRED)
        val now = clock.instant()
        when {
            confirmation.requestFingerprint != request.transportRequest.execution.requestFingerprint ||
                confirmation.priceVersion != model.pricing.priceVersion || confirmation.currencyCode != model.pricing.currencyCode ->
                return blocked(RealTextPreflightBlocker.FEE_CONFIRMATION_SCOPE_MISMATCH)
            confirmation.approvedAt.isAfter(now) -> return blocked(RealTextPreflightBlocker.FEE_CONFIRMATION_EXPIRED)
            confirmation.acknowledgedMaximumMicros < reservation.budgetMicros -> return blocked(RealTextPreflightBlocker.FEE_CONFIRMATION_INSUFFICIENT)
        }
        val attachmentPlans = mutableListOf<RealTextAttachmentPlan>()
        request.attachments.forEach { proof ->
            when (val checked = attachmentPlan(proof, request, now)) {
                is AttachmentPlanCheck.Planned -> attachmentPlans += checked.plan
                is AttachmentPlanCheck.Blocked -> return blocked(checked.blocker)
            }
        }
        if (cancellation.isCancelled()) return blocked(RealTextPreflightBlocker.CANCELLED)
        return RealTextExecutionPreflightResult.Ready(RealTextExecutionReadyPlan(
            preflightId = request.preflightId,
            cancellation = RealTextExecutionCancellationIdentity(
                request.preflightId, request.executionRequest.executionId, request.executionRequest.invocationId,
                request.executionRequest.attemptId, request.executionRequest.requestFingerprint,
            ),
            providerHandle = request.transportRequest.route.providerHandle,
            modelId = request.transportRequest.route.modelId,
            presetId = request.presetId,
            feeConfirmationFingerprint = confirmation.fingerprint(),
            usageReservation = reservation,
            attachments = attachmentPlans,
            plannedAt = now,
        ))
    }

    private fun attachmentPlan(
        proof: RealTextAttachmentPreflightProof,
        request: RealTextExecutionPreflightRequest,
        now: Instant,
    ): AttachmentPlanCheck {
        val intent = proof.intent
        if (AttachmentEgressAuthorizationOwner(clock).project(intent) != null) {
            return AttachmentPlanCheck.Blocked(RealTextPreflightBlocker.ATTACHMENT_INELIGIBLE)
        }
        val summary = proof.authorization ?: return AttachmentPlanCheck.Blocked(RealTextPreflightBlocker.ATTACHMENT_AUTHORIZATION_REQUIRED)
        if (summary.state != AttachmentEgressAuthorizationState.ACTIVE || !now.isBefore(summary.expiresAt) ||
            summary.attachmentId != intent.attachmentId || summary.attachmentSha256 != intent.attachmentSha256 ||
            summary.mimeType != intent.mimeType || summary.byteCount != intent.byteCount || summary.contentType != intent.contentType ||
            summary.conversationId != request.executionRequest.conversationId || summary.executionId != request.executionRequest.executionId ||
            summary.providerHandle != request.transportRequest.route.providerHandle.value || summary.modelId != request.transportRequest.route.modelId.value ||
            summary.capabilityFingerprint != intent.capability.fingerprint()
        ) return AttachmentPlanCheck.Blocked(RealTextPreflightBlocker.ATTACHMENT_SCOPE_MISMATCH)
        return AttachmentPlanCheck.Planned(RealTextAttachmentPlan(
            intent.attachmentId, intent.attachmentSha256, intent.mimeType, intent.byteCount,
            summary.authorizationId, summary.expiresAt, summary.capabilityFingerprint,
            summary.consentFingerprint, summary.feeConfirmationFingerprint,
        ))
    }

    private fun reservationPlan(request: RealTextExecutionPreflightRequest, model: ModelDescriptor): RealTextUsageReservationPlan? {
        val pricing = model.pricing
        val inputRate = pricing.inputMicrosPerToken ?: return null
        val outputRate = pricing.outputMicrosPerToken ?: return null
        val priceVersion = pricing.priceVersion ?: return null
        val currency = pricing.currencyCode ?: return null
        val inputTokens = ConservativeInputBillingBudget.inputTokenUpperBound(request.transportRequest.input.text)
        val budget = ConservativeInputBillingBudget.maximumBudgetMicros(
            request.transportRequest.input.text,
            request.outputTokenLimit,
            inputRate,
            outputRate,
        ) ?: return null
        val fingerprint = sha256("${request.executionRequest.requestFingerprint}|${model.id}|$priceVersion|$currency|$inputTokens|${request.outputTokenLimit}|$budget")
        return RealTextUsageReservationPlan(
            replayToken = "p3-preflight:${request.preflightId.value}",
            executionId = request.executionRequest.executionId,
            conversationId = request.executionRequest.conversationId,
            branchLeafMessageId = request.executionRequest.userMessageId,
            invocationId = request.executionRequest.invocationId,
            attemptId = request.executionRequest.attemptId,
            requestedModelId = request.transportRequest.route.modelId,
            conservativeInputTokenUpperBound = inputTokens,
            reservedOutputTokens = request.outputTokenLimit,
            budgetMicros = budget,
            currencyCode = currency,
            reconciliationFingerprint = fingerprint,
            source = request.usageSource,
        )
    }

    private fun sameExecutionBinding(execution: ConversationRealTextExecutionRequest, transport: ProviderTransportRequest): Boolean {
        val handle = transport.execution
        return handle.executionId == execution.executionId && handle.invocationId == execution.invocationId &&
            handle.attemptId == execution.attemptId && handle.requestFingerprint == execution.requestFingerprint
    }

    private fun providerFor(handle: ProviderTransportProviderHandle): ProviderId? = when (handle.value) {
        "openrouter" -> ProviderId.OPENROUTER
        else -> null
    }

    private fun blocked(vararg blockers: RealTextPreflightBlocker) = RealTextExecutionPreflightResult.Blocked(blockers.toSet())

    private sealed interface AttachmentPlanCheck {
        data class Planned(val plan: RealTextAttachmentPlan) : AttachmentPlanCheck
        data class Blocked(val blocker: RealTextPreflightBlocker) : AttachmentPlanCheck
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
