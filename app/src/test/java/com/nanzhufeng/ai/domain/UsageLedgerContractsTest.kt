package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLedgerContractsTest {
    private val at = Instant.parse("2026-08-15T14:00:00Z")

    @Test fun `pending stream is reconciled by an immutable adjustment and read model retains requested actual model facts`() {
        val pending = entry(
            id = "pending", token = "stream-pending", kind = UsageLedgerKind.STREAM_PENDING,
            grade = UsageFactGrade.ESTIMATED, actualModel = null, reconciliation = "a".repeat(64),
        )
        val adjustment = entry(
            id = "adjustment", token = "stream-adjustment", kind = UsageLedgerKind.RECONCILIATION_ADJUSTMENT,
            grade = UsageFactGrade.RECONCILED, actualModel = "provider/actual", reconciliation = "a".repeat(64),
            reconciles = pending.entryId, input = 12, output = 8, charge = 37, adjustment = -3,
        )

        val model = UsageLedgerReadModelFactory.create(listOf(adjustment, pending))

        assertTrue(model.pendingStreamEntries.isEmpty())
        assertEquals(setOf("provider/requested"), model.requestedModelIds)
        assertEquals(setOf("provider/actual"), model.actualModelIds)
        assertEquals(12, model.inputTokens)
        assertEquals(8, model.outputTokens)
        assertEquals(37, model.chargeMicros)
        assertEquals(-3, model.reconciliationAdjustmentMicros)
    }

    @Test fun `budget reservation release and cross platform origin remain append only safe facts`() {
        val reservation = entry("reserve", "budget-reserve", UsageLedgerKind.BUDGET_RESERVATION, UsageFactGrade.ESTIMATED, budget = 100)
        val release = entry("release", "budget-release", UsageLedgerKind.BUDGET_RELEASE, UsageFactGrade.RECONCILED, budget = 40)
        val model = UsageLedgerReadModelFactory.create(listOf(release, reservation))

        assertEquals(100, model.reservedBudgetMicros)
        assertEquals(40, model.releasedBudgetMicros)
        assertEquals(UsageLedgerSource.CROSS_PLATFORM_IMPORT, reservation.source)
    }

    @Test fun `domain field whitelist excludes content credentials locations attachments and transport capability`() {
        val prohibited = setOf("prompt", "response", "credential", "secret", "authorization", "uri", "path", "attachment", "payload", "transport")
        assertFalse(UsageLedgerEntry::class.java.declaredFields.map { it.name.lowercase() }.any { field -> prohibited.any(field::contains) })
        assertEquals("usage-ledger-v1", USAGE_LEDGER_PROTOCOL)
    }

    private fun entry(
        id: String,
        token: String,
        kind: UsageLedgerKind,
        grade: UsageFactGrade,
        actualModel: String? = "provider/actual",
        reconciliation: String? = null,
        reconciles: UsageLedgerEntryId? = null,
        input: Long? = null,
        output: Long? = null,
        charge: Long? = null,
        budget: Long? = null,
        adjustment: Long? = null,
    ) = UsageLedgerEntry(
        entryId = UsageLedgerEntryId(id), replayToken = token, executionId = ConversationRealTextExecutionId("execution"),
        conversationId = ConversationId("conversation"), branchLeafMessageId = MessageNodeId("branch"),
        invocationId = InvocationId("invocation"), attemptId = ProviderAttemptId("attempt"), kind = kind,
        factGrade = grade, requestedModelId = "provider/requested", actualModelId = actualModel,
        inputTokens = input, outputTokens = output, cachedInputTokens = null, chargeMicros = charge,
        budgetMicros = budget, adjustmentMicros = adjustment, currencyCode = if (listOfNotNull(charge, budget, adjustment).isEmpty()) null else "USD",
        reconciliationFingerprint = reconciliation, reconcilesEntryId = reconciles,
        source = UsageLedgerSource.CROSS_PLATFORM_IMPORT, occurredAt = at,
    )
}
