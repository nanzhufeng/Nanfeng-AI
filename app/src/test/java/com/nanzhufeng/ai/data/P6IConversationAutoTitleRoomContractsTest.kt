package com.nanzhufeng.ai.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.ConversationDraftSubmissionResult
import com.nanzhufeng.ai.domain.ConversationMutationResult
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.AppendConversationMessageUseCase
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.CreateConversationUseCase
import com.nanzhufeng.ai.domain.SaveConversationDraftUseCase
import com.nanzhufeng.ai.domain.SubmitConversationDraftUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P6IConversationAutoTitleRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC)

    @Test fun `pending title eligibility survives room readback after the first reply`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = RoomConversationRepository(database, draftClock = clock)
            val created = CreateConversationUseCase(ConversationTreeService(clock), repository).execute()
                as ConversationMutationResult.Saved
            SaveConversationDraftUseCase(repository, clock).execute(created.snapshot.conversation.id, "请帮我整理 Android 设置页面层级", emptyList())
            val submitted = SubmitConversationDraftUseCase(repository, repository, ConversationTreeService(clock)).execute(created.snapshot.conversation.id)
            assertTrue(submitted is ConversationDraftSubmissionResult.Submitted)
            val readback = repository.findById(created.snapshot.conversation.id)!!
            assertEquals("新对话", readback.conversation.title)
            assertEquals(true, readback.conversation.autoTitlePending)
            assertEquals("", readback.draft.text)
            val replied = AppendConversationMessageUseCase(ConversationTreeService(clock), repository).execute(
                readback,
                AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("## Android 设置页面重新规划\n\n正文"))),
            ) as ConversationMutationResult.Saved
            assertEquals("新对话", replied.snapshot.conversation.title)
            assertEquals(true, replied.snapshot.conversation.autoTitlePending)
        } finally {
            database.close()
        }
    }

    @Test fun `schema twenty five to twenty six preserves conversations and keeps old titles ineligible`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "p6i-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(25) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                    db.execSQL("INSERT INTO conversations VALUES ('existing', '已有会话')")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val sqlite = helper.writableDatabase
        NanfengAiDatabase.MIGRATION_25_26.migrate(sqlite)
        sqlite.query("SELECT title, autoTitlePending FROM conversations WHERE id='existing'").use {
            assertTrue(it.moveToFirst())
            assertEquals("已有会话", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
