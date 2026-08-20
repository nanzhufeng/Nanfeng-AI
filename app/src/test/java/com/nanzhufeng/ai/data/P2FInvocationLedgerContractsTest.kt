package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.ai.MockAiTaskRunner
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomInvocationRepository
import com.nanzhufeng.ai.domain.AiTask
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AiTaskId
import com.nanzhufeng.ai.domain.AiTaskRunResult
import com.nanzhufeng.ai.domain.CaptureDraftFactory
import com.nanzhufeng.ai.domain.EgressConsent
import com.nanzhufeng.ai.domain.HarnessProfile
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.InvocationRecord
import com.nanzhufeng.ai.domain.InvocationStatus
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelDescriptor
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.RunAiTaskUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P2FInvocationLedgerContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var ledger: RoomInvocationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ledger = RoomInvocationRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `ledger transaction round trips all runtime levels and keeps null distinct from zero`() {
        val succeeded = record("success", InvocationStatus.SUCCEEDED, 100, ProviderUsage(inputTokens = 0, outputTokens = 7),
            ProviderCost(priceVersion = "fixture-v1", currencyCode = "USD", totalMicros = 0))
        val failed = record("failed", InvocationStatus.FAILED, 200, ProviderUsage(), ProviderCost(), AiTaskError.ProviderUnavailable)
        val cancelled = record("cancelled", InvocationStatus.CANCELLED, 300, ProviderUsage(), ProviderCost(), AiTaskError.ProviderTimedOut)

        ledger.save(succeeded)
        ledger.save(failed)
        ledger.save(cancelled)

        val restored = ledger.findById(succeeded.id)!!
        assertEquals(succeeded, restored)
        assertEquals(0L, restored.usage.inputTokens)
        assertEquals(0L, restored.cost.totalMicros)
        assertTrue(restored.taskRun.attempts.single().generation != null)
        assertNull(ledger.findById(failed.id)!!.usage.inputTokens)
        assertTrue(ledger.findById(failed.id)!!.cost.isUnknown)
        assertEquals(listOf(cancelled.id, failed.id, succeeded.id), ledger.listNewestFirst().map { it.id })
    }

    @Test
    fun `confirmation gate writes blocked task run without a fabricated attempt`() {
        val draft = CaptureDraftFactory(clock).fromManualText("仅用于本地门禁合同")
        val model = ModelDescriptor("mock-balanced", "Mock", ModelCapabilities(true, false, false))
        val task = AiTask(
            id = AiTaskId("blocked-task"),
            draftId = draft.id,
            providerId = ProviderId.MOCK,
            model = model,
            harness = HarnessProfile("capture-organize", 1),
            consent = null,
            createdAt = clock.instant(),
        )

        val result = RunAiTaskUseCase(MockAiTaskRunner(clock), ledger, clock).execute(task, draft)

        assertTrue(result is AiTaskRunResult.Failure)
        val persisted = ledger.listNewestFirst().single()
        assertEquals(InvocationStatus.BLOCKED, persisted.status)
        assertEquals(AiTaskError.ConsentRequired, persisted.error)
        assertTrue(persisted.taskRun.attempts.isEmpty())
    }

    @Test
    fun `same stable invocation id is idempotent across repository recreation`() {
        val record = record("rebuild", InvocationStatus.FAILED, 100, ProviderUsage(), ProviderCost(), AiTaskError.ProviderUnavailable)
        ledger.save(record)
        val recreated = RoomInvocationRepository(database)

        assertEquals(record, recreated.save(record))
        assertEquals(1, recreated.listNewestFirst().size)
    }

    @Test
    fun `ledger schema does not contain prompt response key or attachment content columns`() {
        val forbidden = setOf("prompt", "response", "apiKey", "key", "image", "attachment", "body", "content")
        val tables = listOf("invocation_records", "invocation_task_runs", "provider_attempts", "generations", "generation_validations")

        tables.flatMap { table ->
            database.openHelper.writableDatabase.query("PRAGMA table_info($table)").use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }.toList()
            }
        }.forEach { column -> assertFalse("敏感列不应出现：$column", forbidden.any { token -> column.contains(token, ignoreCase = true) }) }
    }

    @Test
    fun `migration one to two retains existing capture data and creates invocation ledger tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p2f-migration-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE capture_drafts (id TEXT NOT NULL PRIMARY KEY, text TEXT, createdAtEpochMs INTEGER NOT NULL, schemaVersion INTEGER NOT NULL)")
                    }

                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val sqlite = helper.writableDatabase
        sqlite.execSQL("INSERT INTO capture_drafts VALUES ('draft-v1', '保留的旧草稿', 1, 1)")

        NanfengAiDatabase.MIGRATION_1_2.migrate(sqlite)

        sqlite.query("SELECT text FROM capture_drafts WHERE id = 'draft-v1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("保留的旧草稿", cursor.getString(0))
        }
        listOf("invocation_records", "invocation_task_runs", "provider_attempts", "generations", "generation_validations").forEach { table ->
            sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { cursor ->
                assertTrue("迁移缺少表：$table", cursor.moveToFirst())
            }
        }
        helper.close()
        context.deleteDatabase(name)
    }

    private fun record(
        suffix: String,
        status: InvocationStatus,
        secondsAfterStart: Long,
        usage: ProviderUsage,
        cost: ProviderCost,
        error: AiTaskError? = null,
    ): InvocationRecord {
        val completedAt = clock.instant().plusSeconds(secondsAfterStart)
        val base = InvocationRecord(
            id = InvocationId("invocation-$suffix"),
            taskId = AiTaskId("task-$suffix"),
            providerId = ProviderId.MOCK,
            modelId = "mock-local",
            harnessVersion = 2,
            completedAt = completedAt,
            status = status,
            error = error,
            usage = usage,
            cost = cost,
            pricingVersion = cost.priceVersion,
        )
        return base.copy(taskRun = base.taskRun.copy(startedAt = completedAt.minusMillis(250)))
    }
}
