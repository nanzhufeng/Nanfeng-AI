package com.nanzhufeng.ai.domain

import java.time.Instant
import java.util.UUID

@JvmInline value class NormalChatSendAttemptId(val value: String) {
    companion object { fun new() = NormalChatSendAttemptId(UUID.randomUUID().toString()) }
}

/** Durable, content-free truth for one user-authorized provider request. */
enum class NormalChatSendAttemptStatus { PENDING, SENDING, ACCEPTED, STREAMING, COMPLETED, FAILED, UNKNOWN, CANCELLED }

data class NormalChatSendAttempt(
    val attemptId: NormalChatSendAttemptId,
    val messageId: MessageNodeId,
    val conversationId: ConversationId,
    val providerId: ProviderId,
    val modelId: String,
    val idempotencyKey: String,
    val status: NormalChatSendAttemptStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val safeErrorCode: String? = null,
    /** Null only for rows created before explicit hosted-model receiver provenance existed. */
    val egressProviderId: ProviderId? = null,
    /** Null only for attempts created before the single-send authorization contract existed. */
    val egressAuthorizedAt: Instant? = null,
    val egressDisclosureVersion: String? = null,
) {
    init {
        require(modelId.isNotBlank() && idempotencyKey.isNotBlank())
        require(safeErrorCode == null || safeErrorCode.matches(Regex("[A-Z0-9_]{1,64}")))
        require(egressDisclosureVersion == null || egressDisclosureVersion in setOf(NormalChatEgressAuthorization.DISCLOSURE_VERSION, NormalChatEgressAuthorization.LEGACY_DISCLOSURE_VERSION))
    }
}

interface NormalChatSendAttemptStore {
    fun create(attempt: NormalChatSendAttempt): NormalChatSendAttempt
    fun transition(id: NormalChatSendAttemptId, expected: Set<NormalChatSendAttemptStatus>, next: NormalChatSendAttemptStatus, updatedAt: Instant, safeErrorCode: String? = null): NormalChatSendAttempt?
    fun findLatestForConversation(conversationId: ConversationId): NormalChatSendAttempt?
    /** A deliberate user decision closes an indeterminate request without issuing network I/O. */
    fun markFailed(id: NormalChatSendAttemptId, updatedAt: Instant, safeErrorCode: String): NormalChatSendAttempt?
    /** Any process-interrupted in-flight request has an unknown server outcome until the user decides. */
    fun markInterruptedAsUnknown(updatedAt: Instant): Int
}
