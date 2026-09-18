package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.toConversationReference
import com.nanzhufeng.ai.domain.ConversationDraftResult
import com.nanzhufeng.ai.domain.ConversationDraftSubmissionResult
import com.nanzhufeng.ai.domain.CreateConversationUseCase
import com.nanzhufeng.ai.domain.ConversationMutationResult
import com.nanzhufeng.ai.domain.ConversationSurface
import org.junit.Assert.assertNotEquals
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.SaveConversationDraftUseCase
import com.nanzhufeng.ai.domain.SubmitConversationDraftUseCase
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
class P3DConversationDraftRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomConversationRepository
    private lateinit var tree: ConversationTreeService

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomConversationRepository(database)
        tree = ConversationTreeService(clock)
    }
    @After fun tearDown() = database.close()

    @Test fun `new draft survives reopen resumes by surface and expires exactly one hour after editing`() {
        val now = clock.instant()
        val fresh = RoomConversationRepository(database, draftClock = clock)
        val create = CreateConversationUseCase(tree, fresh)
        val original = (create.resumeOrCreateDraft() as ConversationMutationResult.Saved).snapshot
        val id = original.conversation.id
        SaveConversationDraftUseCase(fresh, clock).execute(id, "未发送的文字", emptyList())
        val before = RoomConversationRepository(database, draftClock = Clock.fixed(now.plusMillis(3_599_999), ZoneOffset.UTC))
        val restored = (CreateConversationUseCase(tree, before).resumeOrCreateDraft() as ConversationMutationResult.Saved).snapshot
        assertEquals(id, restored.conversation.id)
        assertEquals("未发送的文字", restored.draft.text)
        assertEquals(now, restored.draft.updatedAt)
        val work = (CreateConversationUseCase(tree, before).resumeOrCreateDraft(surface = ConversationSurface.WORK) as ConversationMutationResult.Saved).snapshot
        assertNotEquals(id, work.conversation.id)
        assertEquals("", work.draft.text)
        val expired = RoomConversationRepository(database, draftClock = Clock.fixed(now.plusMillis(3_600_000), ZoneOffset.UTC))
        assertEquals("", expired.findById(id)!!.draft.text)
        assertEquals("", before.findById(id)!!.draft.text)
        assertTrue(expired.findById(id)!!.nodes.isEmpty())
    }

    @Test fun `editing a new draft renews expiry but reading does not`() {
        val fresh = RoomConversationRepository(database, draftClock = clock)
        val original = (CreateConversationUseCase(tree, fresh).resumeOrCreateDraft() as ConversationMutationResult.Saved).snapshot
        val id = original.conversation.id
        SaveConversationDraftUseCase(fresh, clock).execute(id, "first", emptyList())
        val later = Clock.fixed(clock.instant().plusSeconds(1_800), ZoneOffset.UTC)
        SaveConversationDraftUseCase(fresh, later).execute(id, "second", emptyList())
        val reopened = RoomConversationRepository(database, draftClock = Clock.fixed(clock.instant().plusSeconds(3_600), ZoneOffset.UTC))
        assertEquals("second", reopened.findById(id)!!.draft.text)
        assertEquals(later.instant(), reopened.findById(id)!!.draft.updatedAt)
    }

    @Test fun `draft preserves exact multiline editor text deduplicates attachments and survives repository rebuild`() {
        val snapshot = repository.save(tree.create("草稿"))
        val attachment = AttachmentReference("private/a", "image/png", id = AttachmentId("a"), byteCount = 1, sha256 = "a".repeat(64))
        val draftText = "  本地草稿\n第二行\n"
        val saved = SaveConversationDraftUseCase(repository, clock).execute(snapshot.conversation.id, draftText, listOf(attachment.toConversationReference(), attachment.toConversationReference())) as ConversationDraftResult.Saved
        assertEquals(draftText, saved.draft.text)
        assertEquals(1, saved.draft.attachments.size)
        assertEquals(saved.draft, RoomConversationRepository(database).loadDraft(snapshot.conversation.id))
    }

    @Test fun `successful submit atomically clears draft while rejected stale submission preserves it`() {
        val original = repository.save(tree.create("提交"))
        val save = SaveConversationDraftUseCase(repository, clock)
        save.execute(original.conversation.id, "待发送", emptyList())
        val sent = SubmitConversationDraftUseCase(repository, repository, tree).execute(original.conversation.id)
        assertTrue(sent is ConversationDraftSubmissionResult.Submitted)
        val after = repository.findById(original.conversation.id)!!
        assertEquals("", after.draft.text)
        assertEquals(MessageRole.USER, after.nodes.single().role)

        save.execute(original.conversation.id, "保留我", emptyList())
        val stale = after.copy(draft = after.draft)
        val rejected = repository.submitDraft(stale, after.draft)
        assertTrue(rejected is ConversationDraftSubmissionResult.Rejected)
        assertEquals("保留我", repository.loadDraft(original.conversation.id)!!.text)
    }
}
