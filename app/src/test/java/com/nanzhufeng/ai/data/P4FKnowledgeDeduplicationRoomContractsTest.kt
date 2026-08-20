package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomKnowledgeRepository
import com.nanzhufeng.ai.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P4FKnowledgeDeduplicationRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var manage: ManageKnowledgeUseCase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        manage = ManageKnowledgeUseCase(KnowledgeDomain(clock), RoomKnowledgeRepository(database, clock))
    }
    @After fun close() = database.close()

    @Test fun `room-backed candidates are read-only and do not append revisions`() {
        val anchor = KnowledgeItemId("anchor")
        val duplicate = KnowledgeItemId("duplicate")
        create(anchor, "本地资料", "同一份正文")
        create(duplicate, "本地资料", "另一份正文")

        val before = manage.detail(duplicate)!!.revisions.size
        val result = manage.duplicateCandidates(anchor) as KnowledgeDuplicateCandidatesResult.Available

        assertEquals(listOf(duplicate), result.candidates.map { it.knowledgeId })
        assertEquals(before, manage.detail(duplicate)!!.revisions.size)
    }

    private fun create(id: KnowledgeItemId, title: String, body: String) {
        manage.execute(KnowledgeIntent(KnowledgeIntentId.new(), KnowledgeIntentAction.CREATE_MANUAL, id, title, body))
    }
}
