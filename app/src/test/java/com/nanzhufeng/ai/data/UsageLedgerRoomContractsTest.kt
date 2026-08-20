package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomUsageLedgerRepository
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionId
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.UsageFactGrade
import com.nanzhufeng.ai.domain.UsageLedgerAppendResult
import com.nanzhufeng.ai.domain.UsageLedgerEntry
import com.nanzhufeng.ai.domain.UsageLedgerEntryId
import com.nanzhufeng.ai.domain.UsageLedgerKind
import com.nanzhufeng.ai.domain.UsageLedgerSource
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsageLedgerRoomContractsTest {
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomUsageLedgerRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomUsageLedgerRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun `append exact fact replays changed fact conflicts and reconstructed repository derives pending reconciliation`() {
        val pending = entry("pending", "pending-token", UsageLedgerKind.STREAM_PENDING, UsageFactGrade.ESTIMATED, null, "d".repeat(64))
        assertTrue(repository.append(pending) is UsageLedgerAppendResult.Appended)
        assertTrue(repository.append(pending) is UsageLedgerAppendResult.Replayed)
        assertTrue(repository.append(pending.copy(actualModelId = "provider/changed")) is UsageLedgerAppendResult.Conflict)
        val reconciliation = entry("reconciled", "reconciled-token", UsageLedgerKind.RECONCILIATION_ADJUSTMENT, UsageFactGrade.RECONCILED, "provider/actual", "d".repeat(64), pending.entryId, input = 4, output = 6, adjustment = -2)
        assertTrue(repository.append(reconciliation) is UsageLedgerAppendResult.Appended)

        val rebuilt = RoomUsageLedgerRepository(database).readModelForExecution(pending.executionId)
        assertTrue(rebuilt.pendingStreamEntries.isEmpty())
        assertEquals(4, rebuilt.inputTokens)
        assertEquals(-2, rebuilt.reconciliationAdjustmentMicros)
    }

    @Test fun `reconciliation cannot rewrite or target a different stream pending fact`() {
        val pending = entry("pending", "pending-token", UsageLedgerKind.STREAM_PENDING, UsageFactGrade.ESTIMATED, null, "d".repeat(64))
        assertTrue(repository.append(pending) is UsageLedgerAppendResult.Appended)
        val mismatch = entry("reconciled", "reconciled-token", UsageLedgerKind.RECONCILIATION_ADJUSTMENT, UsageFactGrade.RECONCILED, "provider/actual", "e".repeat(64), pending.entryId)
        assertTrue(repository.append(mismatch) is UsageLedgerAppendResult.Conflict)
    }

    @Test fun `table whitelist excludes content credentials authorizations locations and attachments`() {
        val prohibited = setOf("prompt", "response", "credential", "secret", "authorization", "uri", "path", "attachment", "payload")
        val columns = mutableSetOf<String>()
        database.openHelper.writableDatabase.query("PRAGMA table_info(usage_ledger_entries)").use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name")).lowercase()
        }
        assertFalse(columns.any { field -> prohibited.any(field::contains) })
        assertEquals(
            setOf("entryid", "replaytoken", "executionid", "conversationid", "branchleafmessageid", "invocationid", "attemptid", "kind", "factgrade", "requestedmodelid", "actualmodelid", "inputtokens", "outputtokens", "cachedinputtokens", "chargemicros", "budgetmicros", "adjustmentmicros", "currencycode", "reconciliationfingerprint", "reconcilesentryid", "source", "occurredatepochms"),
            columns,
        )
    }

    private fun entry(
        id: String, token: String, kind: UsageLedgerKind, grade: UsageFactGrade, actualModel: String?,
        reconciliation: String?, reconciles: UsageLedgerEntryId? = null, input: Long? = null, output: Long? = null, adjustment: Long? = null,
    ) = UsageLedgerEntry(
        entryId = UsageLedgerEntryId(id), replayToken = token, executionId = ConversationRealTextExecutionId("execution"),
        conversationId = ConversationId("conversation"), branchLeafMessageId = MessageNodeId("branch"), invocationId = InvocationId("invocation"),
        attemptId = ProviderAttemptId("attempt"), kind = kind, factGrade = grade, requestedModelId = "provider/requested",
        actualModelId = actualModel, inputTokens = input, outputTokens = output, cachedInputTokens = null, chargeMicros = null,
        budgetMicros = null, adjustmentMicros = adjustment, currencyCode = if (adjustment == null) null else "USD",
        reconciliationFingerprint = reconciliation, reconcilesEntryId = reconciles, source = UsageLedgerSource.ANDROID_LOCAL,
        occurredAt = Instant.parse("2026-08-15T14:00:00Z"),
    )
}
