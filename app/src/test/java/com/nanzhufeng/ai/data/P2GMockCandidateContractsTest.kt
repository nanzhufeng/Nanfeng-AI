package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.ai.MockAiTaskRunner
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomCaptureDraftRepository
import com.nanzhufeng.ai.data.local.RoomGeneratedCandidateRepository
import com.nanzhufeng.ai.data.local.RoomInvocationRepository
import com.nanzhufeng.ai.data.local.RoomKnowledgeRepository
import com.nanzhufeng.ai.domain.CandidateReviewStatus
import com.nanzhufeng.ai.domain.CaptureDraftFactory
import com.nanzhufeng.ai.domain.ConfirmAiRequest
import com.nanzhufeng.ai.domain.PersistGeneratedCandidateUseCase
import com.nanzhufeng.ai.domain.RunAiTaskUseCase
import com.nanzhufeng.ai.domain.SaveCandidateReviewUseCase
import com.nanzhufeng.ai.domain.SaveKnowledgeItemUseCase
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
class P2GMockCandidateContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-12T13:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var drafts: RoomCaptureDraftRepository
    private lateinit var candidates: RoomGeneratedCandidateRepository
    private lateinit var ledger: RoomInvocationRepository
    private lateinit var knowledge: RoomKnowledgeRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        drafts = RoomCaptureDraftRepository(database)
        candidates = RoomGeneratedCandidateRepository(database)
        ledger = RoomInvocationRepository(database)
        knowledge = RoomKnowledgeRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun `preview or cancellation creates neither attempt nor ledger record`() {
        val draft = CaptureDraftFactory(clock).fromManualText("取消确认时保留的草稿")
        val confirmation = ConfirmAiRequest(clock)

        val preview = confirmation.preview(draft)

        assertEquals("mock-local-balanced-v1", preview.model.id)
        assertEquals(0, preview.imageCount)
        assertTrue(ledger.listNewestFirst().isEmpty())
    }

    @Test fun `confirmed mock invocation persists ledger then candidate separately and survives repository recreation`() {
        val draft = drafts.save(CaptureDraftFactory(clock).fromManualText("需要核对的本地文本"))
        val confirmation = ConfirmAiRequest(clock)
        val task = confirmation.approve(draft)
        val result = RunAiTaskUseCase(MockAiTaskRunner(clock), ledger, clock).execute(task, draft)
        assertTrue(result is com.nanzhufeng.ai.domain.AiTaskRunResult.Success)
        result as com.nanzhufeng.ai.domain.AiTaskRunResult.Success

        val stored = PersistGeneratedCandidateUseCase(candidates, clock).execute(result.candidate, result.invocation, task, draft)
        val restored = RoomGeneratedCandidateRepository(database).findLatestPendingReview()

        assertEquals(CandidateReviewStatus.PENDING_REVIEW, stored.status)
        assertEquals(stored, restored)
        assertEquals(1, ledger.listNewestFirst().size)
        assertEquals(1, ledger.findById(result.invocation.id)!!.taskRun.attempts.size)
        assertNull(knowledge.findById(com.nanzhufeng.ai.domain.KnowledgeItemId("knowledge:${stored.candidate.id.value}")))
    }

    @Test fun `candidate is editable and only confirmed save creates idempotent knowledge`() {
        val fixture = createPendingCandidate()
        val save = SaveCandidateReviewUseCase(SaveKnowledgeItemUseCase(knowledge, clock), candidates, clock)
        val invocation = ledger.findById(fixture.stored.invocationId)!!
        val draft = drafts.findById(fixture.stored.draftId)!!

        val first = save.execute(fixture.stored, invocation, draft, "人工修改标题", "人工修改正文", userConfirmed = true)
        val second = save.execute(fixture.stored, invocation, draft, "人工修改标题", "人工修改正文", userConfirmed = true)

        assertTrue(first is com.nanzhufeng.ai.domain.SaveKnowledgeResult.Saved)
        assertTrue(second is com.nanzhufeng.ai.domain.SaveKnowledgeResult.Saved)
        assertEquals(1, knowledge.listAll().size)
        assertEquals("人工修改正文", knowledge.listAll().single().body)
        assertEquals(CandidateReviewStatus.SAVED, candidates.findById(fixture.stored.candidate.id)!!.status)
        assertNull(candidates.findLatestPendingReview())
    }

    @Test fun `discarding a candidate never creates knowledge and keeps its invocation`() {
        val fixture = createPendingCandidate()
        val save = SaveCandidateReviewUseCase(SaveKnowledgeItemUseCase(knowledge, clock), candidates, clock)

        save.discard(fixture.stored)

        assertEquals(CandidateReviewStatus.DISCARDED, candidates.findById(fixture.stored.candidate.id)!!.status)
        assertTrue(knowledge.listAll().isEmpty())
        assertFalse(ledger.listNewestFirst().isEmpty())
    }

    @Test fun `migration two to three retains ledger table and adds candidate table`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p2g-migration-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE invocation_records (id TEXT NOT NULL PRIMARY KEY)")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val sqlite = helper.writableDatabase
        sqlite.execSQL("INSERT INTO invocation_records VALUES ('existing-ledger')")

        NanfengAiDatabase.MIGRATION_2_3.migrate(sqlite)

        sqlite.query("SELECT id FROM invocation_records WHERE id = 'existing-ledger'").use { assertTrue(it.moveToFirst()) }
        sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='generated_candidates'").use { assertTrue(it.moveToFirst()) }
        helper.close()
        context.deleteDatabase(name)
    }

    private fun createPendingCandidate(): Fixture {
        val draft = drafts.save(CaptureDraftFactory(clock).fromManualText("候选草稿"))
        val task = ConfirmAiRequest(clock).approve(draft)
        val result = RunAiTaskUseCase(MockAiTaskRunner(clock), ledger, clock).execute(task, draft) as com.nanzhufeng.ai.domain.AiTaskRunResult.Success
        return Fixture(PersistGeneratedCandidateUseCase(candidates, clock).execute(result.candidate, result.invocation, task, draft))
    }

    private data class Fixture(val stored: com.nanzhufeng.ai.domain.StoredGeneratedCandidate)
}
