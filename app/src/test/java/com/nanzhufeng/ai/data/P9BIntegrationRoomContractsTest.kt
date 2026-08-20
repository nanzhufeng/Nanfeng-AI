package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.domain.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class P9BIntegrationRoomContractsTest {
    private lateinit var database: NanfengAiDatabase
    @Before fun setup() { database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = database.close()
    @Test fun `Room reopens secret free local test audit without target body`() {
        val target = object : P9BLocalTestOnlyTarget { override val appHandle = "app_fixture"; override fun preview(subjectHandle: String, limit: Int, cursor: String?) = P9BPreview(1, "a".repeat(64), 1, null); override fun readback(subjectHandle: String) = preview(subjectHandle, 1, null) }
        val harness = P9BLocalTestOnlyHarness(RoomP9BIntegrationLedger(database), target, Clock.fixed(Instant.ofEpochMilli(1), ZoneOffset.UTC))
        val raw = """{"format":"nfai.integration-contract","version":1,"mode":"LOCAL_TEST_ONLY","requestId":"request_room","idempotencyKey":"idem_room","appHandle":"app_fixture","subjectHandle":"subject_fixture","capability":"READ_ONLY_PREVIEW","permission":"READ_ONLY","classification":"NON_SENSITIVE","provenance":{"source":"LOCAL_TEST_ONLY","revision":1,"contentHash":"${"a".repeat(64)}"},"page":{"limit":1,"cursor":null},"expiresAtEpochMs":null}"""
        harness.request(raw); harness.authorize("request_room"); harness.preview("request_room"); harness.confirm("request_room"); harness.result("request_room")
        val reopened = RoomP9BIntegrationLedger(database).byRequest("request_room")!!
        Assert.assertEquals(P9BState.RESULT_READY, reopened.session.state); Assert.assertEquals(5, reopened.events.size); Assert.assertFalse(reopened.toString().contains("content://"))
    }
    @Test fun `schema twenty to twenty one retains agent ledger and appends P9B tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p9b-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(20) { override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE agent_runs (id TEXT NOT NULL PRIMARY KEY)"); db.execSQL("INSERT INTO agent_runs VALUES ('existing')") }; override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_20_21.migrate(sqlite)
        sqlite.query("SELECT id FROM agent_runs").use { Assert.assertTrue(it.moveToFirst()); Assert.assertEquals("existing", it.getString(0)) }
        listOf("p9b_integration_sessions", "p9b_integration_events", "p9b_integration_receipts").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { Assert.assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }
}
