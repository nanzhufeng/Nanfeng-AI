package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The only normal-chat egress owner.  This default implementation is deliberately
 * retains no draft text and cannot read a Key, attachment, network client, receipt store, or
 * usage ledger. It only owns the short-lived, content-free user confirmation.
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
                NormalChatExternalSendBlocker.READY
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
    READY,
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
    val providerDisplayName: String = "OpenRouter"
    val providerHandle: String = "openrouter"
    val modelDisplayName: String = "已保存预设"
    val modelId: String = "按本机设置解析"
    val preset: String = "已保存预设"
    val registrySnapshot: String = "发送前复核"
    val priceVersion: String = "未知"
    val currency: String = "未知"
    val maximumFee: String = "以服务商实际计费为准"
    val isConfirmable: Boolean get() = blocker == NormalChatExternalSendBlocker.READY
}
