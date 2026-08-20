package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The only normal-chat egress owner.  This default implementation is deliberately
 * fail-closed: it retains no draft text, has no P3 ports, and cannot read a Key,
 * attachment, network client, receipt store, or usage ledger.
 */
class NormalChatRealTextExecutionOwner(
    private val clock: Clock,
) {
    fun requestConfirmation(request: NormalChatExternalSendIntent): NormalChatExternalSendConfirmation {
        val now = clock.instant()
        return NormalChatExternalSendConfirmation(
            createdAt = now,
            expiresAt = now.plus(EXTERNAL_SEND_CONFIRMATION_TTL_MINUTES, ChronoUnit.MINUTES),
            draftCharacterCount = request.draftCharacterCount,
            attachmentCount = request.attachmentCount,
            acknowledgementChecked = false,
            blocker = if (request.attachmentCount > 0) {
                NormalChatExternalSendBlocker.ATTACHMENTS_NOT_SUPPORTED
            } else {
                NormalChatExternalSendBlocker.EGRESS_UNREGISTERED
            },
        )
    }

    fun setAcknowledgement(
        confirmation: NormalChatExternalSendConfirmation,
        checked: Boolean,
    ): NormalChatExternalSendConfirmation = confirmation.copy(
        acknowledgementChecked = checked && !isExpired(confirmation),
    )

    fun expire(confirmation: NormalChatExternalSendConfirmation): NormalChatExternalSendConfirmation =
        confirmation.copy(acknowledgementChecked = false, blocker = NormalChatExternalSendBlocker.CONFIRMATION_EXPIRED)

    fun isExpired(confirmation: NormalChatExternalSendConfirmation): Boolean =
        !clock.instant().isBefore(confirmation.expiresAt)

    /** There is intentionally no confirm/send API until a separately authorized P3 owner exists. */
    companion object {
        const val EXTERNAL_SEND_CONFIRMATION_TTL_MINUTES = 5L
    }
}

/** Safe UI intent only: draft text and attachment references never enter this owner. */
data class NormalChatExternalSendIntent(
    val draftCharacterCount: Int,
    val attachmentCount: Int,
) {
    init {
        require(draftCharacterCount > 0) { "外发确认需要非空草稿。" }
        require(attachmentCount >= 0) { "附件数量不合法。" }
    }
}

enum class NormalChatExternalSendBlocker {
    EGRESS_UNREGISTERED,
    ATTACHMENTS_NOT_SUPPORTED,
    CONFIRMATION_EXPIRED,
}

/**
 * All fields are disclosure-only safe metadata.  In particular, there is no
 * prompt, attachment ID, provider credential, URI, endpoint, receipt, or cost value.
 */
data class NormalChatExternalSendConfirmation(
    val createdAt: Instant,
    val expiresAt: Instant,
    val draftCharacterCount: Int,
    val attachmentCount: Int,
    val acknowledgementChecked: Boolean,
    val blocker: NormalChatExternalSendBlocker,
) {
    val providerDisplayName: String = "未注册服务商"
    val providerHandle: String = "未注册"
    val modelDisplayName: String = "未注册模型"
    val modelId: String = "未注册"
    val preset: String = "未注册"
    val registrySnapshot: String = "未注册"
    val priceVersion: String = "未知"
    val currency: String = "未知"
    val maximumFee: String = "未知（不可确认）"
    val isConfirmable: Boolean = false
}
