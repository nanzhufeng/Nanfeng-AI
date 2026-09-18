package com.nanzhufeng.ai.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.*
import com.nanzhufeng.ai.background.NormalChatGenerationRegistry
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationDataAreaRoomTest {
    @Test fun `physical files preserve same ids independent drafts and messages across reopen`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "area-room-${java.util.UUID.randomUUID()}").also { it.mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getDatabasePath(name: String) = File(root, name)
        }
        val clock = Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC)
        val tree = ConversationTreeService(clock)
        val original = tree.create("initial")
        fun open(area: ConversationSurface) = Room.databaseBuilder(context, NanfengAiDatabase::class.java, ConversationDataArea.databaseName(area)).allowMainThreadQueries().build()
        try {
            for (area in ConversationSurface.entries) {
                val database = open(area)
                try {
                    val repository = RoomConversationRepository(database, draftClock = clock)
                    repository.save(tree.append(original.copy(conversation = original.conversation.copy(surface = area, title = area.name)), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("${area.name} message")))))
                    SaveConversationDraftUseCase(repository, clock).execute(original.conversation.id, "${area.name} draft", emptyList())
                } finally { database.close() }
            }
            assertNotEquals(context.getDatabasePath(ConversationDataArea.databaseName(ConversationSurface.CHAT)).canonicalFile,
                context.getDatabasePath(ConversationDataArea.databaseName(ConversationSurface.WORK)).canonicalFile)
            for (area in ConversationSurface.entries) {
                val database = open(area)
                try {
                    val restored = RoomConversationRepository(database, draftClock = clock).findById(original.conversation.id)!!
                    assertEquals(area.name, restored.conversation.title)
                    assertEquals("${area.name} draft", restored.draft.text)
                    assertEquals(listOf(ContentBlock.Text("${area.name} message")), restored.nodes.single().content)
                } finally { database.close() }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun `explicit message reference copies only selected text and never changes source`() {
        val clock = Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC)
        val tree = ConversationTreeService(clock)
        val first = tree.append(tree.create("source"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("selected text"))))
        val source = tree.append(first, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("unselected text"))))
        val reference = CrossAreaMessageReference.copyToNewDraft(source, first.nodes.single().id, ConversationSurface.WORK, tree, clock)
        assertEquals(ConversationSurface.WORK, reference.conversation.surface)
        assertTrue(reference.nodes.isEmpty())
        assertTrue(reference.draft.text.contains("selected text"))
        assertFalse(reference.draft.text.contains("unselected text"))
        assertTrue(reference.draft.text.contains(first.nodes.single().id.value))
        assertTrue(reference.draft.attachments.isEmpty())
        assertEquals("source", source.conversation.title)
        assertEquals(2, source.nodes.size)
        assertEquals("", source.draft.text)
        assertTrue(runCatching { CrossAreaMessageReference.copyToNewDraft(source, MessageNodeId("missing"), ConversationSurface.WORK, tree, clock) }.isFailure)
        assertTrue(runCatching { CrossAreaMessageReference.copyToNewDraft(source, first.nodes.single().id, ConversationSurface.CHAT, tree, clock) }.isFailure)
    }

    @Test fun `moving app data verifies and retains both independent database files`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "area-move-${java.util.UUID.randomUUID()}").also { it.mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(root,"files").also { it.mkdirs() }
            override fun getCacheDir() = File(root,"cache").also { it.mkdirs() }
            override fun getDatabasePath(name: String) = File(File(root,"databases").also { it.mkdirs() },name)
            override fun getExternalFilesDir(type: String?) = File(root,"external").also { it.mkdirs() }
        }
        try {
            for (area in ConversationSurface.entries) {
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(ConversationDataArea.databaseName(area)),null).use {
                    it.execSQL("CREATE TABLE marker(value TEXT)")
                    it.execSQL("INSERT INTO marker VALUES(?)",arrayOf(area.name))
                }
            }
            val owner = AndroidDataStorageLocationOwner(context)
            assertTrue(owner.moveTo(AndroidDataStorageLocation.EXTERNAL) {} is AndroidDataStorageMoveResult.RestartRequired)
            for (area in ConversationSurface.entries) {
                val copied = owner.storageContext().getDatabasePath(ConversationDataArea.databaseName(area))
                android.database.sqlite.SQLiteDatabase.openDatabase(copied.path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT value FROM marker",null).use { row -> assertTrue(row.moveToFirst());assertEquals(area.name,row.getString(0)) }
                }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun `legacy mixed database migrates work records and sync receipts atomically and only once`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "area-upgrade-${java.util.UUID.randomUUID()}").also { it.mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getDatabasePath(name: String) = File(root,name)
            override fun getFilesDir() = File(root,"files").also { it.mkdirs() }
        }
        fun factory(name: String) = Room.databaseBuilder(context,NanfengAiDatabase::class.java,name).allowMainThreadQueries().build()
        val clock=Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"),ZoneOffset.UTC)
        val tree=ConversationTreeService(clock)
        val chat=tree.append(tree.create("chat"),AppendMessageRequest(MessageRole.USER,listOf(ContentBlock.Text("chat text"))))
        val work=tree.append(tree.create("work",surface=ConversationSurface.WORK),AppendMessageRequest(MessageRole.USER,listOf(ContentBlock.Text("work text"))))
        try {
            val legacy=factory(ConversationDataArea.databaseName(ConversationSurface.CHAT))
            val repository=RoomConversationRepository(legacy,draftClock=clock)
            repository.save(chat);repository.save(work)
            SaveConversationDraftUseCase(repository,clock).execute(work.conversation.id,"work draft",emptyList())
            legacy.openHelper.writableDatabase.execSQL("INSERT INTO manual_conversation_sync_state VALUES(?,?,?,?,?,?,?)",arrayOf<Any>("account",work.conversation.id.value,"old-document",7,"payload-hash","local-hash",0))
            legacy.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            legacy.close()
            val failed=runCatching { com.nanzhufeng.ai.data.local.ConversationAreaDatabaseOwner.split(context,
                context.getDatabasePath(ConversationDataArea.databaseName(ConversationSurface.CHAT)),
                context.getDatabasePath(ConversationDataArea.databaseName(ConversationSurface.WORK))) { error("injected commit failure") } }
            assertTrue(failed.isFailure)
            val unchanged=factory(ConversationDataArea.databaseName(ConversationSurface.CHAT))
            try {
                val repo=RoomConversationRepository(unchanged,draftClock=clock)
                assertNotNull(repo.findById(chat.conversation.id));assertNotNull(repo.findById(work.conversation.id))
            } finally { unchanged.close() }
            for(area in ConversationSurface.entries) {
                val db=com.nanzhufeng.ai.data.local.ConversationAreaDatabaseOwner.open(context,area,::factory)
                try {
                    val repo=RoomConversationRepository(db,draftClock=clock)
                    val own=if(area==ConversationSurface.WORK)work else chat
                    val peer=if(area==ConversationSurface.WORK)chat else work
                    assertNotNull(repo.findById(own.conversation.id))
                    assertNull(repo.findById(peer.conversation.id))
                    if(area==ConversationSurface.WORK) {
                        assertEquals("work draft",repo.findById(work.conversation.id)!!.draft.text)
                        db.openHelper.readableDatabase.query("SELECT documentId,remoteRevision FROM manual_conversation_sync_state").use { row ->
                            assertTrue(row.moveToFirst());assertEquals("old-document",row.getString(0));assertEquals(7,row.getInt(1))
                        }
                    } else db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM manual_conversation_sync_state").use { it.moveToFirst();assertEquals(0,it.getInt(0)) }
                } finally { db.close() }
            }
            assertTrue(File(root,"data-area-migration-v1/before-split.sqlite").isFile)
            val reopened=com.nanzhufeng.ai.data.local.ConversationAreaDatabaseOwner.open(context,ConversationSurface.WORK,::factory)
            try { assertEquals("work draft",RoomConversationRepository(reopened,draftClock=clock).findById(work.conversation.id)!!.draft.text) } finally { reopened.close() }
        } finally { root.deleteRecursively() }
    }

    @org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.NATIVE)
    @Test fun `opening interrupted legacy database recovers hot journal before inspecting identity`() {
        val base=ApplicationProvider.getApplicationContext<Context>()
        val root=File(base.cacheDir,"area-hot-journal-${java.util.UUID.randomUUID()}").also { it.mkdirs() }
        val context=object : ContextWrapper(base) {
            override fun getDatabasePath(name: String) = File(root,name)
            override fun getFilesDir() = File(root,"files").also { it.mkdirs() }
        }
        fun factory(name: String)=Room.databaseBuilder(context,NanfengAiDatabase::class.java,name).allowMainThreadQueries().build()
        val path=context.getDatabasePath("nanfeng-ai.db")
        try {
            val legacy=factory(path.name)
            val sql=legacy.openHelper.writableDatabase
            for(i in 0 until 80) sql.execSQL("INSERT INTO capture_drafts (id,text,createdAtEpochMs,schemaVersion) VALUES (?, ?,0,1)",arrayOf("row-$i","original".repeat(1024)))
            sql.query("PRAGMA wal_checkpoint(TRUNCATE)").close();legacy.close()
            val raw=android.database.sqlite.SQLiteDatabase.openDatabase(path.path,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
            raw.disableWriteAheadLogging()
            raw.rawQuery("PRAGMA journal_mode=DELETE",null).use { assertTrue(it.moveToFirst());assertEquals("delete",it.getString(0).lowercase()) }
            raw.execSQL("PRAGMA cache_size=1")
            raw.beginTransaction()
            try {
                raw.execSQL("UPDATE capture_drafts SET text=?",arrayOf("candidate".repeat(1024)))
                // Capture the on-disk state of a writer that disappears without commit/rollback.
                path.copyTo(File(root,"crashed-db"))
                File(path.path+"-journal").copyTo(File(root,"crashed-journal"))
            } finally { raw.endTransaction();raw.close() }
            File(root,"crashed-db").copyTo(path,overwrite=true)
            File(root,"crashed-journal").copyTo(File(path.path+"-journal"),overwrite=true)
            val recovered=com.nanzhufeng.ai.data.local.ConversationAreaDatabaseOwner.open(context,ConversationSurface.CHAT,::factory)
            try {
                recovered.openHelper.readableDatabase.query("SELECT COUNT(*) FROM capture_drafts WHERE text=?",arrayOf("original".repeat(1024))).use { row ->
                    assertTrue(row.moveToFirst());assertEquals(80,row.getInt(0))
                }
            } finally { recovered.close() }
        } finally { root.deleteRecursively() }
    }

    @Test fun `generation registration cannot cancel or claim the other area's same id`() {
        val id = ConversationId("same-id")
        try {
            NormalChatGenerationRegistry.markRunning(id, ConversationSurface.CHAT)
            NormalChatGenerationRegistry.markRunning(id, ConversationSurface.WORK)
            NormalChatGenerationRegistry.markFinished(id, ConversationSurface.WORK)
            assertTrue(NormalChatGenerationRegistry.isRunning(id, ConversationSurface.CHAT))
            assertFalse(NormalChatGenerationRegistry.isRunning(id, ConversationSurface.WORK))
        } finally {
            ConversationSurface.entries.forEach { NormalChatGenerationRegistry.markFinished(id, it) }
        }
    }
}
