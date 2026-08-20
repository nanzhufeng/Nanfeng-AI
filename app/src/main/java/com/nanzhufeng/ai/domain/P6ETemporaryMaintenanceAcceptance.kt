package com.nanzhufeng.ai.domain

import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Fixed, no-input acceptance fixture for a separately installed non-production build.
 * It uses the production temporary owner and its attachment cleanup use case, but refuses
 * to run while any recovery record already exists.  It never reads system time or user files.
 */
data class P6ETemporaryMaintenanceAcceptanceReceipt(
    val retainedAt23h59: Boolean,
    val removedAt24h: Boolean,
    val attachmentRemovedAt24h: Boolean,
    val messageWasPresentBeforeExpiry: Boolean,
    val modelOverrideWasPresentBeforeExpiry: Boolean,
)

class P6ETemporaryMaintenanceAcceptanceHarness(
    private val recovery: TemporaryConversationRecoveryStore,
    private val privateStore: PrivateAttachmentStore,
    private val assets: PrivateAttachmentRepository,
) {
    fun run(): P6ETemporaryMaintenanceAcceptanceReceipt {
        check(recovery.readActive() == null) { "验收夹具拒绝覆盖已有临时聊天。" }
        val clock = MutableClock(Instant.parse("2026-08-14T00:00:00Z"))
        val owner = TemporaryConversationDomain(recovery, clock)
        val add = AddTemporaryConversationAttachmentUseCase(owner, privateStore, assets)
        val clear = ClearTemporaryConversationUseCase(owner, assets, privateStore)
        owner.enterOrRestore()
        owner.updateModelOverride("local.p6e-acceptance")
        val added = add.add(
            ConversationAttachmentSelection(
                input = ByteArrayInputStream("p6e acceptance private copy".encodeToByteArray()),
                mimeType = "text/plain",
                displayName = "p6e-acceptance.txt",
            ),
        ) as? TemporaryAttachmentResult.Added ?: error("验收私有附件未能创建")
        owner.updateDraft("P6E_ACCEPTANCE_MESSAGE", added.recovery.draftAttachmentIds)
        val sent = owner.appendOfflineMessage()
        val attachmentId = sent.messages.single().attachmentIds.single()

        clock.advance(Duration.ofHours(23).plusMinutes(59))
        val retained = !clear.pruneExpired()
        val before = owner.readRecovery()
        val messagePresent = before?.messages?.singleOrNull()?.text == "P6E_ACCEPTANCE_MESSAGE"
        val overridePresent = before?.modelOverrideId == "local.p6e-acceptance"

        clock.advance(Duration.ofMinutes(1))
        val removed = clear.pruneExpired() && owner.readRecovery() == null
        return P6ETemporaryMaintenanceAcceptanceReceipt(
            retainedAt23h59 = retained && before != null,
            removedAt24h = removed,
            attachmentRemovedAt24h = assets.findById(attachmentId) == null,
            messageWasPresentBeforeExpiry = messagePresent,
            modelOverrideWasPresentBeforeExpiry = overridePresent,
        )
    }

    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant(): Instant = now
        fun advance(duration: Duration) { now = now.plus(duration) }
    }
}
