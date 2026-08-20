package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P8AgentLedgerRoomContractsTest {
    private lateinit var database: NanfengAiDatabase
    private lateinit var runtime: ControlledAgentRuntime
    @Before fun setup() { database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build(); runtime = ControlledAgentRuntime(RoomAgentLedger(database), LocalTestOnlyAgentToolRegistry.fixture(), Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)) }
    @After fun close() = database.close()

    @Test fun `Room rebuild reads stable ordered run steps events checkpoints and receipt`() {
        val run = (runtime.start(AgentRunRequest(id = "room-run", idempotencyKey = "room-key", budget = AgentBudget(3, 3, 1), riskCeiling = AgentRiskLevel.LOCAL_REVERSIBLE, permissionGrant = AgentPermission.LOCAL_REVERSIBLE_FIXTURE)) as AgentExecutionResult.Accepted).snapshot.run.id
        runtime.execute(run, AgentToolInvocation("room-step", "fixture_reversible_action", "untrusted fixture")); runtime.pause(run)
        val rebuilt = RoomAgentLedger(database).findRun(run)!!
        assertEquals(listOf(0L), rebuilt.steps.map { it.sequence }); assertEquals(rebuilt.events.map { it.sequence }.sorted(), rebuilt.events.map { it.sequence }); assertEquals(AgentRunStatus.PAUSED, rebuilt.run.status)
        assertNotNull(RoomAgentLedger(database).findReceipt("room-step")); assertEquals(1L, rebuilt.run.usedSideEffects)
    }

    @Test fun `schema nineteen to twenty retains sync table and appends agent ledger`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p8-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(19) { override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE sync_jobs (accountRef TEXT NOT NULL PRIMARY KEY)"); db.execSQL("INSERT INTO sync_jobs VALUES ('existing')") }; override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_19_20.migrate(sqlite)
        sqlite.query("SELECT accountRef FROM sync_jobs").use { assertTrue(it.moveToFirst()); assertEquals("existing", it.getString(0)) }
        listOf("agent_runs", "agent_steps", "agent_events", "agent_checkpoints", "agent_side_effect_receipts").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }
}
