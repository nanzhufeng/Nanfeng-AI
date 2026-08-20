package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.ConversationMutationResult
import com.nanzhufeng.ai.domain.ConversationSurface
import com.nanzhufeng.ai.domain.ConversationSurfaceRepository
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.CreateConversationUseCase
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
class P6JConversationSurfaceRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC)

    @Test fun `chat and work persist as separate content streams while sharing the conversation contract`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = RoomConversationRepository(database)
            val create = CreateConversationUseCase(ConversationTreeService(clock), repository)
            val chat = create.execute(title = "对话") as ConversationMutationResult.Saved
            val work = create.execute(title = "工作", surface = ConversationSurface.WORK) as ConversationMutationResult.Saved

            assertEquals(ConversationSurface.CHAT, repository.findById(chat.snapshot.conversation.id)?.conversation?.surface)
            assertEquals(ConversationSurface.WORK, repository.findById(work.snapshot.conversation.id)?.conversation?.surface)
            assertEquals(listOf(chat.snapshot.conversation.id), repository.listActive().map { it.id })
            assertEquals(listOf(work.snapshot.conversation.id), (repository as ConversationSurfaceRepository).listActive(ConversationSurface.WORK).map { it.id })
        } finally {
            database.close()
        }
    }

    @Test fun `schema twenty six to twenty seven marks existing content as chat`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p6j-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(26) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, archivedAtEpochMs INTEGER, deletedAtEpochMs INTEGER, updatedAtEpochMs INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO conversations VALUES ('existing', '已有对话', NULL, NULL, 1)")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val sqlite = helper.writableDatabase
        try {
            NanfengAiDatabase.MIGRATION_26_27.migrate(sqlite)
            sqlite.query("SELECT surface FROM conversations WHERE id='existing'").use {
                assertTrue(it.moveToFirst())
                assertEquals("CHAT", it.getString(0))
            }
            sqlite.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='conversations'").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getString(0).contains("surface TEXT NOT NULL DEFAULT 'CHAT'"))
            }
            sqlite.query("SELECT 1 FROM sqlite_master WHERE type='index' AND name='index_conversations_surface_deletedAtEpochMs_archivedAtEpochMs_updatedAtEpochMs'").use {
                assertTrue(it.moveToFirst())
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
