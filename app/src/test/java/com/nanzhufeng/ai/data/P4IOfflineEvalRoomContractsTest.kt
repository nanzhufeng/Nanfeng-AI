package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomOfflineEvalRepository
import com.nanzhufeng.ai.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P4IOfflineEvalRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomOfflineEvalRepository
    @Before fun setup() { database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build(); repository = RoomOfflineEvalRepository(database) }
    @After fun close() = database.close()

    @Test fun `run reconstruction scores and fixture isolation survive Room rebuild`() {
        val run = RunOfflineEvalUseCase(repository, clock, "test").run(); val rebuilt = repository.list().single()
        Assert.assertEquals(run.fixtureManifestHash, rebuilt.fixtureManifestHash); Assert.assertEquals(run.results.size, rebuilt.results.size); Assert.assertFalse(rebuilt.toString().contains("content://"))
        val score = HumanScore(run.id, run.results.first().caseId, HumanScoreDimension.TRACEABILITY, null, "alias", OFFLINE_EVAL_RUBRIC_VERSION, clock.instant(), null)
        repository.appendScore(score); Assert.assertEquals(1, repository.scores(run.id).size)
    }

    @Test fun `schema thirteen to fourteen retains markdown task table and appends eval tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p4i-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(13) { override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE markdown_import_tasks (id TEXT NOT NULL PRIMARY KEY)"); db.execSQL("INSERT INTO markdown_import_tasks VALUES ('m')") }; override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_13_14.migrate(sqlite)
        sqlite.query("SELECT id FROM markdown_import_tasks").use { Assert.assertTrue(it.moveToFirst()); Assert.assertEquals("m", it.getString(0)) }
        listOf("offline_eval_runs", "offline_eval_case_results", "offline_eval_assertions", "offline_eval_human_scores").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { Assert.assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }

    @Test fun `report roundtrip hash and tamper rejection are local only`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val store = AndroidOfflineEvalReportStore(context)
        val repository = object : EvalRunRepository { val runs = mutableListOf<EvalRun>(); override fun append(run: EvalRun) = run.also(runs::add); override fun list() = runs; override fun appendScore(score: HumanScore) = score; override fun scores(runId: EvalRunId) = emptyList<HumanScore>() }
        val run = RunOfflineEvalUseCase(repository, clock, "test").run(); val result = ExportOfflineEvalReportUseCase(repository, store).execute(run.id)!!
        Assert.assertTrue(store.verify(result.fileName)); val file = java.io.File(context.filesDir, "exports/offline-eval/v1/${result.fileName}"); file.appendText("tamper"); Assert.assertFalse(store.verify(result.fileName))
    }
}
