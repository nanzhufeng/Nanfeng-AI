package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * P1 pre-egress capability gate. It is an in-memory, fail-closed domain owner and has no file,
 * credential, provider, request-body, transport, persistence or UI dependency.
 */
const val ATTACHMENT_EGRESS_ELIGIBILITY_PROTOCOL = "attachment-egress-eligibility-v1"

@JvmInline
value class AttachmentEgressAuthorizationId(val value: String) {
    companion object { fun new() = AttachmentEgressAuthorizationId(UUID.randomUUID().toString()) }
}

enum class AttachmentEgressContentType { IMAGE, VIDEO, AUDIO, DOCUMENT }

enum class AttachmentEgressRejection {
    CONSENT_REQUIRED,
    CONSENT_SCOPE_MISMATCH,
    FEE_CONFIRMATION_REQUIRED,
    CAPABILITY_DENIED,
    MIME_DENIED,
    SIZE_DENIED,
    CONTENT_TYPE_DENIED,
    EXPIRED,
    REVOKED,
    CONSUMED,
    REPLAY_CONFLICT,
    UNKNOWN_AUTHORIZATION,
}

/** Safe capability metadata only; it does not hold a URL, credential, provider configuration or route. */
data class AttachmentEgressCapability(
    val providerHandle: String,
    val modelId: String,
    val capabilityVersion: Int,
    val allowedMimeTypes: Set<String>,
    val allowedContentTypes: Set<AttachmentEgressContentType>,
    val maxByteCount: Long,
) {
    init {
        require(safeHandle(providerHandle) && safeModel(modelId)) { "服务商或模型能力标识不合法。" }
        require(capabilityVersion > 0 && allowedMimeTypes.isNotEmpty() && allowedContentTypes.isNotEmpty()) { "附件能力声明不完整。" }
        require(allowedMimeTypes.all(::safeMimeType) && maxByteCount in 1..CONVERSATION_ATTACHMENT_MAX_BYTES) { "附件能力边界不合法。" }
    }

    fun fingerprint(): String = sha256("$providerHandle|$modelId|$capabilityVersion|${allowedMimeTypes.sorted().joinToString(",")}|${allowedContentTypes.sortedBy { it.name }.joinToString(",")}|$maxByteCount")
}

/** The acknowledgement is passed in memory and only its fingerprint reaches the grant summary. */
data class AttachmentEgressFeeConfirmation(
    val confirmationId: String,
    val acknowledgedAt: Instant,
    val priceVersion: String?,
    val currencyCode: String?,
    val estimatedMicros: Long?,
) {
    init {
        require(safeHandle(confirmationId)) { "费用确认标识不合法。" }
        require(estimatedMicros == null || estimatedMicros >= 0) { "费用估算不能为负数。" }
        require(currencyCode == null || currencyCode.matches(Regex("[A-Z]{3}"))) { "费用币种不合法。" }
        require(estimatedMicros == null || (!priceVersion.isNullOrBlank() && currencyCode != null)) { "已知费用必须有价格版本和币种。" }
    }

    fun fingerprint(): String = sha256("$confirmationId|${acknowledgedAt.toEpochMilli()}|${priceVersion.orEmpty()}|${currencyCode.orEmpty()}|${estimatedMicros ?: "unknown"}")
}

/** An intent has only stable IDs, MIME/size, hashes and a capability declaration; no name, URI or bytes. */
data class AttachmentEgressIntent(
    val attachmentId: AttachmentId,
    val attachmentSha256: String,
    val mimeType: String,
    val byteCount: Long,
    val conversationId: ConversationId,
    val executionId: ConversationRealTextExecutionId,
    val capability: AttachmentEgressCapability,
) {
    init {
        require(attachmentSha256.matches(Regex("[0-9a-f]{64}")) && safeMimeType(mimeType)) { "附件安全摘要或 MIME 不合法。" }
        require(byteCount in 1..CONVERSATION_ATTACHMENT_MAX_BYTES) { "附件大小不在本地安全边界内。" }
    }

    val contentType: AttachmentEgressContentType get() = contentTypeFor(mimeType)
    fun fingerprint(): String = sha256("${attachmentId.value}|$attachmentSha256|$mimeType|$byteCount|${conversationId.value}|${executionId.value}|${capability.fingerprint()}")

    companion object {
        fun from(reference: ConversationAttachmentReference, conversationId: ConversationId, executionId: ConversationRealTextExecutionId, capability: AttachmentEgressCapability) =
            AttachmentEgressIntent(reference.id, reference.sha256, reference.mimeType, reference.byteCount, conversationId, executionId, capability)
    }
}

/** The presence of this object represents a future explicit action; P1 adds no UI that creates it. */
data class ExplicitAttachmentEgressConsent(
    val consentId: String,
    val approvedAt: Instant,
    val intentFingerprint: String,
    val feeConfirmation: AttachmentEgressFeeConfirmation?,
) {
    init {
        require(safeHandle(consentId) && intentFingerprint.matches(Regex("[0-9a-f]{64}"))) { "附件同意事实不合法。" }
    }

    fun fingerprint(): String = sha256("$consentId|${approvedAt.toEpochMilli()}|$intentFingerprint|${feeConfirmation?.fingerprint().orEmpty()}")
}

data class AttachmentEgressAuthorizationRequest(
    val authorizationId: AttachmentEgressAuthorizationId,
    val replayToken: String,
    val intent: AttachmentEgressIntent,
    val consent: ExplicitAttachmentEgressConsent?,
    val expiresAt: Instant,
) {
    init { require(replayToken.matches(Regex("[A-Za-z0-9._:-]{1,160}"))) { "附件授权重放标识不合法。" } }
}

enum class AttachmentEgressAuthorizationState { ACTIVE, REVOKED, CONSUMED, EXPIRED }

/** No display name, local reference, URI, path, bytes, prompt, response, key or authorization value is exposed. */
data class AttachmentEgressSafeSummary(
    val authorizationId: AttachmentEgressAuthorizationId,
    val attachmentId: AttachmentId,
    val attachmentSha256: String,
    val mimeType: String,
    val byteCount: Long,
    val contentType: AttachmentEgressContentType,
    val conversationId: ConversationId,
    val executionId: ConversationRealTextExecutionId,
    val providerHandle: String,
    val modelId: String,
    val capabilityFingerprint: String,
    val consentFingerprint: String,
    val feeConfirmationFingerprint: String,
    val expiresAt: Instant,
    val state: AttachmentEgressAuthorizationState,
)

sealed interface AttachmentEgressAuthorizationResult {
    data class Authorized(val summary: AttachmentEgressSafeSummary) : AttachmentEgressAuthorizationResult
    data class Replayed(val summary: AttachmentEgressSafeSummary) : AttachmentEgressAuthorizationResult
    data class Rejected(val reason: AttachmentEgressRejection) : AttachmentEgressAuthorizationResult
}

/**
 * Memory-only ownership means process restart discards every grant and is therefore a safe denial.
 * `consume` only marks an authorization single-use; it never reads an asset or initiates egress.
 */
class AttachmentEgressAuthorizationOwner(private val clock: Clock) {
    private data class Stored(val request: AttachmentEgressAuthorizationRequest, var state: AttachmentEgressAuthorizationState)
    private val byId = mutableMapOf<AttachmentEgressAuthorizationId, Stored>()
    private val byReplayToken = mutableMapOf<String, AttachmentEgressAuthorizationId>()

    fun project(intent: AttachmentEgressIntent): AttachmentEgressRejection? = eligibilityFailure(intent)

    fun authorize(request: AttachmentEgressAuthorizationRequest): AttachmentEgressAuthorizationResult {
        val now = clock.instant()
        byReplayToken[request.replayToken]?.let { existingId ->
            val existing = requireNotNull(byId[existingId])
            if (existing.request != request) return AttachmentEgressAuthorizationResult.Rejected(AttachmentEgressRejection.REPLAY_CONFLICT)
            return when (stateAt(existing, now)) {
                AttachmentEgressAuthorizationState.ACTIVE -> AttachmentEgressAuthorizationResult.Replayed(summary(existing, now))
                AttachmentEgressAuthorizationState.REVOKED -> AttachmentEgressAuthorizationResult.Rejected(AttachmentEgressRejection.REVOKED)
                AttachmentEgressAuthorizationState.CONSUMED -> AttachmentEgressAuthorizationResult.Rejected(AttachmentEgressRejection.CONSUMED)
                AttachmentEgressAuthorizationState.EXPIRED -> AttachmentEgressAuthorizationResult.Rejected(AttachmentEgressRejection.EXPIRED)
            }
        }
        byId[request.authorizationId]?.let { existing ->
            return if (existing.request == request) AttachmentEgressAuthorizationResult.Replayed(summary(existing, now))
            else AttachmentEgressAuthorizationResult.Rejected(AttachmentEgressRejection.REPLAY_CONFLICT)
        }
        val rejection = requestValidationFailure(request, now) ?: eligibilityFailure(request.intent)
        if (rejection != null) return AttachmentEgressAuthorizationResult.Rejected(rejection)
        val stored = Stored(request, AttachmentEgressAuthorizationState.ACTIVE)
        byId[request.authorizationId] = stored
        byReplayToken[request.replayToken] = request.authorizationId
        return AttachmentEgressAuthorizationResult.Authorized(summary(stored, now))
    }

    fun revoke(id: AttachmentEgressAuthorizationId): AttachmentEgressAuthorizationResult = transition(id, AttachmentEgressAuthorizationState.REVOKED)
    fun consume(id: AttachmentEgressAuthorizationId): AttachmentEgressAuthorizationResult = transition(id, AttachmentEgressAuthorizationState.CONSUMED)

    private fun transition(id: AttachmentEgressAuthorizationId, target: AttachmentEgressAuthorizationState): AttachmentEgressAuthorizationResult {
        val stored = byId[id] ?: return AttachmentEgressAuthorizationResult.Rejected(AttachmentEgressRejection.UNKNOWN_AUTHORIZATION)
        val now = clock.instant()
        return when (stateAt(stored, now)) {
            AttachmentEgressAuthorizationState.ACTIVE -> {
                stored.state = target
                AttachmentEgressAuthorizationResult.Authorized(summary(stored, now))
            }
            AttachmentEgressAuthorizationState.REVOKED -> AttachmentEgressAuthorizationResult.Rejected(AttachmentEgressRejection.REVOKED)
            AttachmentEgressAuthorizationState.CONSUMED -> AttachmentEgressAuthorizationResult.Rejected(AttachmentEgressRejection.CONSUMED)
            AttachmentEgressAuthorizationState.EXPIRED -> AttachmentEgressAuthorizationResult.Rejected(AttachmentEgressRejection.EXPIRED)
        }
    }

    private fun requestValidationFailure(request: AttachmentEgressAuthorizationRequest, now: Instant): AttachmentEgressRejection? {
        if (!now.isBefore(request.expiresAt)) return AttachmentEgressRejection.EXPIRED
        val consent = request.consent ?: return AttachmentEgressRejection.CONSENT_REQUIRED
        if (consent.intentFingerprint != request.intent.fingerprint() || consent.approvedAt.isAfter(now)) return AttachmentEgressRejection.CONSENT_SCOPE_MISMATCH
        if (consent.feeConfirmation == null || consent.feeConfirmation.acknowledgedAt.isAfter(now)) return AttachmentEgressRejection.FEE_CONFIRMATION_REQUIRED
        return null
    }

    private fun eligibilityFailure(intent: AttachmentEgressIntent): AttachmentEgressRejection? = when {
        intent.contentType !in intent.capability.allowedContentTypes -> AttachmentEgressRejection.CONTENT_TYPE_DENIED
        intent.mimeType !in intent.capability.allowedMimeTypes -> AttachmentEgressRejection.MIME_DENIED
        intent.byteCount > intent.capability.maxByteCount -> AttachmentEgressRejection.SIZE_DENIED
        else -> null
    }

    private fun stateAt(stored: Stored, now: Instant): AttachmentEgressAuthorizationState {
        if (stored.state == AttachmentEgressAuthorizationState.ACTIVE && !now.isBefore(stored.request.expiresAt)) stored.state = AttachmentEgressAuthorizationState.EXPIRED
        return stored.state
    }

    private fun summary(stored: Stored, now: Instant): AttachmentEgressSafeSummary {
        val request = stored.request
        val intent = request.intent
        val consent = requireNotNull(request.consent) { "已授权事实必须有显式同意。" }
        return AttachmentEgressSafeSummary(
            request.authorizationId, intent.attachmentId, intent.attachmentSha256, intent.mimeType, intent.byteCount,
            intent.contentType, intent.conversationId, intent.executionId, intent.capability.providerHandle,
            intent.capability.modelId, intent.capability.fingerprint(), consent.fingerprint(),
            requireNotNull(consent.feeConfirmation).fingerprint(), request.expiresAt, stateAt(stored, now),
        )
    }
}

private fun contentTypeFor(mimeType: String): AttachmentEgressContentType = when {
    mimeType.startsWith("image/") -> AttachmentEgressContentType.IMAGE
    mimeType.startsWith("video/") -> AttachmentEgressContentType.VIDEO
    mimeType.startsWith("audio/") -> AttachmentEgressContentType.AUDIO
    else -> AttachmentEgressContentType.DOCUMENT
}

private fun safeHandle(value: String) = value.matches(Regex("[A-Za-z0-9._:-]{1,160}"))
private fun safeModel(value: String) = value.matches(Regex("[A-Za-z0-9._:/-]{1,200}"))
private fun safeMimeType(value: String) = value.matches(Regex("[a-z0-9!#$&^_.+-]{1,80}/[a-z0-9!#$&^_.+-]{1,80}"))
private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
