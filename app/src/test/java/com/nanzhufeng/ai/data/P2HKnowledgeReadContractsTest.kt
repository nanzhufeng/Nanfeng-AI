package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
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
import com.nanzhufeng.ai.domain.KnowledgeItem
import com.nanzhufeng.ai.domain.KnowledgeItemId
import com.nanzhufeng.ai.domain.CandidateProvenance
import com.nanzhufeng.ai.domain.CandidateId
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.PersistGeneratedCandidateUseCase
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ReadKnowledgeLibraryUseCase
import com.nanzhufeng.ai.domain.RunAiTaskUseCase
import com.nanzhufeng.ai.domain.SaveCandidateReviewUseCase
import com.nanzhufeng.ai.domain.SaveKnowledgeItemUseCase
import com.nanzhufeng.ai.domain.SourceEvidence
import com.nanzhufeng.ai.domain.CaptureSourceType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P2HKnowledgeReadContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-12T14:00:00Z"), ZoneOffset.UTC)
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

    @Test fun `empty library contains no fixture or candidate content`() {
        val read = ReadKnowledgeLibraryUseCase(knowledge, candidates)

        assertTrue(read.list().isEmpty())
        assertNull(read.detail(KnowledgeItemId("knowledge:missing")))
    }

    @Test fun `confirmed candidate is recoverable as saved knowledge with safe provenance`() {
        val draft = drafts.save(CaptureDraftFactory(clock).fromManualText("只有本地保存的草稿"))
        val task = ConfirmAiRequest(clock).approve(draft)
        val invocation = (RunAiTaskUseCase(MockAiTaskRunner(clock), ledger, clock).execute(task, draft)
            as com.nanzhufeng.ai.domain.AiTaskRunResult.Success)
        val stored = PersistGeneratedCandidateUseCase(candidates, clock).execute(invocation.candidate, invocation.invocation, task, draft)
        SaveCandidateReviewUseCase(SaveKnowledgeItemUseCase(knowledge, clock), candidates, clock)
            .execute(stored, invocation.invocation, draft, "人工确认标题", "完整且已确认的知识正文", userConfirmed = true)

        val rebuiltRead = ReadKnowledgeLibraryUseCase(RoomKnowledgeRepository(database), RoomGeneratedCandidateRepository(database))
        val entry = rebuiltRead.list().single()
        val detail = requireNotNull(rebuiltRead.detail(entry.id))

        assertEquals("人工确认标题", entry.title)
        assertEquals("完整且已确认的知识正文", detail.item.body)
        assertEquals(CaptureSourceType.MANUAL_TEXT, entry.sourceEvidence.single().sourceType)
        assertEquals(stored.candidate.id, detail.item.provenance.candidateId)
        assertEquals(stored.invocationId, detail.item.provenance.invocationId)
        assertEquals(CandidateReviewStatus.SAVED, detail.candidateStatus)
    }

    @Test fun `knowledge list has stable newest first order after repository recreation`() {
        val later = knowledgeItem("knowledge:z", "较晚保存", Instant.parse("2026-08-12T14:02:00Z"))
        val earlier = knowledgeItem("knowledge:a", "较早保存", Instant.parse("2026-08-12T14:01:00Z"))
        knowledge.save(earlier)
        knowledge.save(later)

        val entries = ReadKnowledgeLibraryUseCase(RoomKnowledgeRepository(database), candidates).list()

        assertEquals(listOf(later.id, earlier.id), entries.map { it.id })
    }

    private fun knowledgeItem(id: String, title: String, createdAt: Instant) = KnowledgeItem(
        id = KnowledgeItemId(id),
        title = title,
        body = "可读取正文",
        sourceEvidence = listOf(SourceEvidence(CaptureSourceType.ANDROID_TEXT_SHARE, createdAt, "com.example.sender", setOf("text"))),
        provenance = CandidateProvenance(CandidateId("candidate:$id"), InvocationId("invocation:$id"), ProviderId.MOCK, "mock-local-balanced-v1", 1),
        createdAt = createdAt,
    )
}
