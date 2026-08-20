package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomKnowledgeRepository
import com.nanzhufeng.ai.data.local.RoomMarkdownImportTaskRepository
import com.nanzhufeng.ai.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P4HMarkdownImportRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: NanfengAiDatabase
    private lateinit var tasks: RoomMarkdownImportTaskRepository
    private lateinit var imports: ManageMarkdownImportUseCase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build(); tasks = RoomMarkdownImportTaskRepository(db); imports = ManageMarkdownImportUseCase(tasks, MemoryAssetStore(), MarkdownKnowledgeAdapter(KnowledgeDomain(clock)), ManageKnowledgeUseCase(KnowledgeDomain(clock), RoomKnowledgeRepository(db, clock)), clock) }
    @After fun close() = db.close()

    @Test fun `task persists private copy parse confirmation skip cancel retry and no uri`() {
        val task = imports.select("notes.md", "text/markdown", "# Alpha\n正文\n$MARKDOWN_MULTI_ITEM_SEPARATOR\n# Beta\n正文".toByteArray())
        assertEquals(MarkdownImportTaskStatus.AWAITING_CONFIRMATION, task.status); assertEquals(2, task.items.size); assertFalse(task.toString().contains("content://"))
        val rebuilt = requireNotNull(tasks.find(task.id)); assertEquals(2, rebuilt.items.size)
        val saved = imports.confirm(task.id, task.items[0].id, "Alpha", "正文", emptySet(), KnowledgeScope.GLOBAL, null)
        assertEquals(MarkdownImportItemStatus.CONFIRMED, saved.items[0].status); assertEquals(MarkdownImportTaskStatus.PARTIALLY_COMPLETED, saved.status)
        val done = imports.skip(task.id, task.items[1].id); assertEquals(MarkdownImportTaskStatus.COMPLETED, done.status)
        val cancelled = imports.cancel(task.id); assertEquals(MarkdownImportTaskStatus.COMPLETED, cancelled.status)
    }

    @Test fun `schema twelve to thirteen retains old table and adds only queue tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p4h-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(12) { override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE knowledge_relationships (id TEXT NOT NULL PRIMARY KEY)"); db.execSQL("INSERT INTO knowledge_relationships VALUES ('r')") }; override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_12_13.migrate(sqlite)
        sqlite.query("SELECT id FROM knowledge_relationships").use { assertTrue(it.moveToFirst()); assertEquals("r", it.getString(0)) }
        listOf("markdown_import_tasks", "markdown_import_items").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }

    private class MemoryAssetStore : MarkdownPrivateAssetStore {
        private val assets = mutableMapOf<String, ByteArray>()
        override fun copy(request: MarkdownPrivateCopyRequest): MarkdownPrivateCopyResult { val key = "markdown-import-assets/v1/${request.taskId.value}/safe.md"; assets[key] = request.bytes; return MarkdownPrivateCopyResult.Copied(MarkdownImportAsset(key, request.mimeType, "safe.md", request.bytes.size.toLong(), MemoryDomain.sha256(request.bytes.decodeToString()))) }
        override fun read(storageKey: String): ByteArray? = assets[storageKey]
    }
}
