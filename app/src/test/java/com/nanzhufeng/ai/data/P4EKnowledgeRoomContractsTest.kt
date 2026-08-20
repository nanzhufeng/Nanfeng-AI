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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P4EKnowledgeRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var manage: ManageKnowledgeUseCase
    private lateinit var repository: RoomKnowledgeRepository
    @Before fun setUp() { database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build(); repository = RoomKnowledgeRepository(database, clock); manage = ManageKnowledgeUseCase(KnowledgeDomain(clock), repository) }
    @After fun close() = database.close()

    @Test fun `manual create revision tags archive trash and restore survive room rebuild`() {
        val id = KnowledgeItemId("knowledge-e")
        assertTrue(manage.execute(KnowledgeIntent(KnowledgeIntentId("create"), KnowledgeIntentAction.CREATE_MANUAL, id, "本地检索", "Markdown `code` 与中文", setOf("Android", "检索"))) is KnowledgeMutationResult.Applied)
        assertTrue(manage.execute(KnowledgeIntent(KnowledgeIntentId("edit"), KnowledgeIntentAction.UPDATE, id, "本地检索", "English search body", setOf("code", "search"))) is KnowledgeMutationResult.Applied)
        assertEquals(2, repository.findSnapshot(id)!!.revisions.size)
        assertEquals(setOf("code", "search"), repository.findSnapshot(id)!!.lifecycle.tags)
        assertEquals(listOf(id), manage.search(KnowledgeSearchFilter(query = "english")).map { it.id })
        manage.execute(KnowledgeIntent(KnowledgeIntentId("archive"), KnowledgeIntentAction.ARCHIVE, id))
        assertTrue(manage.search(KnowledgeSearchFilter(query = "", status = KnowledgeStatus.ACTIVE)).isEmpty())
        assertEquals(KnowledgeStatus.ARCHIVED, RoomKnowledgeRepository(database, clock).findSnapshot(id)!!.lifecycle.status)
        manage.execute(KnowledgeIntent(KnowledgeIntentId("restore"), KnowledgeIntentAction.RESTORE, id))
        manage.execute(KnowledgeIntent(KnowledgeIntentId("delete"), KnowledgeIntentAction.DELETE, id))
        assertEquals(KnowledgeStatus.DELETED, repository.findSnapshot(id)!!.lifecycle.status)
        manage.execute(KnowledgeIntent(KnowledgeIntentId("trash-restore"), KnowledgeIntentAction.RESTORE_FROM_TRASH, id))
        assertEquals(KnowledgeStatus.ACTIVE, repository.findSnapshot(id)!!.lifecycle.status)
    }
}
