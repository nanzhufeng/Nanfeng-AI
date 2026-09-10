package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomMemoryRepository
import com.nanzhufeng.ai.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P4CMemoryRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T12:30:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomMemoryRepository
    private lateinit var useCase: ManageMemoryUseCase
    @Before fun setup() { database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build(); repository = RoomMemoryRepository(database, clock); useCase = ManageMemoryUseCase(MemoryDomain(clock), repository) }
    @After fun close() = database.close()

    @Test fun `create revise pause restore delete batch search conflict and rebuild are explicit`() {
        val first = MemoryId("first")
        assertTrue(useCase.execute(MemoryIntent(MemoryIntentId("create"), MemoryIntentAction.CREATE, first, "偏好", "使用 中文回答", MemoryScope(MemoryScopeKind.GLOBAL))) is MemoryMutationResult.Applied)
        assertTrue(useCase.execute(MemoryIntent(MemoryIntentId("same"), MemoryIntentAction.CREATE, MemoryId("other"), " 偏好 ", " 使用\n中文回答 ", MemoryScope(MemoryScopeKind.GLOBAL))) is MemoryMutationResult.Duplicate)
        val conflict = useCase.execute(MemoryIntent(MemoryIntentId("conflict"), MemoryIntentAction.CREATE, MemoryId("candidate"), "偏好", "保持简洁", MemoryScope(MemoryScopeKind.GLOBAL))) as MemoryMutationResult.Conflict
        assertTrue(useCase.execute(MemoryIntent(MemoryIntentId("resolve"), MemoryIntentAction.RESOLVE_CONFLICT, conflictId = conflict.conflict.id, conflictResolution = MemoryConflictResolution.REVISE_EXISTING)) is MemoryMutationResult.Applied)
        val revised = repository.findById(first)!!
        assertEquals(2, revised.revisions.size)
        assertEquals("保持简洁", revised.memory.body)
        useCase.execute(MemoryIntent(MemoryIntentId("pause"), MemoryIntentAction.PAUSE, first))
        assertEquals(MemoryStatus.PAUSED, RoomMemoryRepository(database, clock).findById(first)!!.memory.status)
        useCase.execute(MemoryIntent(MemoryIntentId("restore"), MemoryIntentAction.RESTORE, first))
        assertEquals(1, repository.list(null, MemoryStatus.ACTIVE, "简洁").size)
        useCase.execute(MemoryIntent(MemoryIntentId("delete"), MemoryIntentAction.BULK_DELETE, memoryIds = listOf(first)))
        assertEquals(0, repository.list(null, MemoryStatus.ACTIVE, "").size)
        assertEquals(MemoryStatus.DELETED, repository.list(null, MemoryStatus.DELETED, "").single().memory.status)
    }

    @Test fun `replace complete summary removes all active old sections and survives repository rebuild`() {
        for (id in listOf("section-a", "section-b")) {
            useCase.execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.CREATE,
                MemoryId(id), id, "old section $id", MemoryScope(MemoryScopeKind.GLOBAL)))
        }
        val baseline = repository.list(MemoryScopeKind.GLOBAL, MemoryStatus.ACTIVE, "")
        val request = MemorySummaryReplacement(MemoryIntentId.new(), MemoryId("replacement"),
            "Entirely rewritten summary", baseline.associate { it.memory.id to it.revisions.maxOf { r -> r.revision } })
        assertTrue(useCase.replaceSummary(request) is MemoryMutationResult.Applied)
        val rebuilt = RoomMemoryRepository(database, clock)
        assertEquals("Entirely rewritten summary", rebuilt.list(MemoryScopeKind.GLOBAL, MemoryStatus.ACTIVE, "").single().memory.body)
        baseline.forEach { old ->
            val persisted = rebuilt.findById(old.memory.id)!!
            assertEquals(MemoryStatus.DELETED, persisted.memory.status)
            assertEquals(old.memory.body, persisted.revisions.first().body)
        }
        assertTrue(useCase.replaceSummary(request) is MemoryMutationResult.Replayed)
    }

    private fun seedSection(id: String = "original"): MemorySnapshot {
        useCase.execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.CREATE, MemoryId(id),
            id, "Original body", MemoryScope(MemoryScopeKind.GLOBAL)))
        return repository.findById(MemoryId(id))!!
    }
    private fun replacement(body: String = "Replacement body") = MemorySummaryReplacement(
        MemoryIntentId.new(), MemoryId.new(), body,
        repository.list(MemoryScopeKind.GLOBAL, MemoryStatus.ACTIVE, "")
            .associate { it.memory.id to it.revisions.maxOf { r -> r.revision } },
    )

    @Test fun `concurrent addition or revision rejects stale replacement and preserves current data`() {
        val original = seedSection()
        val request = replacement()
        seedSection("new-section")
        assertEquals(MemoryMutationResult.Rejected(MemoryRejectionCode.STALE_SUMMARY), useCase.replaceSummary(request))
        assertEquals(2, repository.list(MemoryScopeKind.GLOBAL, MemoryStatus.ACTIVE, "").size)
        val secondRequest = replacement()
        useCase.execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.UPDATE, original.memory.id,
            "original", "Concurrent edit", MemoryScope(MemoryScopeKind.GLOBAL)))
        assertEquals(MemoryMutationResult.Rejected(MemoryRejectionCode.STALE_SUMMARY), useCase.replaceSummary(secondRequest))
        assertEquals("Concurrent edit", repository.findById(original.memory.id)!!.memory.body)
    }

    @Test fun `invalid replacement leaves old summary intact and never saves sensitive body`() {
        seedSection()
        assertEquals(MemoryMutationResult.Rejected(MemoryRejectionCode.BODY_EMPTY), useCase.replaceSummary(replacement(" ")))
        assertEquals(MemoryMutationResult.Rejected(MemoryRejectionCode.HIGH_SENSITIVITY_PASSWORD), useCase.replaceSummary(replacement("密码: fixture-secret")))
        assertEquals("Original body", repository.list(null, null, "").single().memory.body)
    }

    @Test fun `transaction failure rolls back both replacement and old section deletion`() {
        seedSection()
        val request = replacement()
        database.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER reject_summary_receipt BEFORE INSERT ON memory_intents WHEN NEW.action = 'REPLACE_SUMMARY' BEGIN SELECT RAISE(ABORT, 'fixture write failure'); END")
        try {
            var failed = false
            try { useCase.replaceSummary(request) } catch (_: android.database.SQLException) { failed = true }
            assertTrue("Expected durable write failure", failed)
            assertEquals("Original body", repository.list(null, null, "").single().memory.body)
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_summary_receipt")
        }
        assertTrue(useCase.replaceSummary(request) is MemoryMutationResult.Applied)
    }

    @Test fun `whole replacement survives database close reopen and preserves paused memories`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "memory-editor-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            var diskRepository = RoomMemoryRepository(disk, clock)
            val diskUseCase = ManageMemoryUseCase(MemoryDomain(clock), diskRepository)
            val pausedId = MemoryId.new()
            diskUseCase.execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.CREATE, pausedId,
                "Paused section", "Keep this paused", MemoryScope(MemoryScopeKind.GLOBAL)))
            diskUseCase.execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.PAUSE, pausedId))
            val request = MemorySummaryReplacement(MemoryIntentId.new(), MemoryId.new(), "Saved full text", emptyMap())
            assertTrue(diskUseCase.replaceSummary(request) is MemoryMutationResult.Applied)
            disk.close()
            disk = Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
            diskRepository = RoomMemoryRepository(disk, clock)
            assertEquals("Saved full text", diskRepository.list(MemoryScopeKind.GLOBAL, MemoryStatus.ACTIVE, "").single().memory.body)
            assertEquals(MemoryStatus.PAUSED, diskRepository.findById(pausedId)!!.memory.status)
        } finally { disk.close(); context.deleteDatabase(name) }
    }

    @Test fun `persisted replacement excludes old text from rebuilt real SQLite retrieval index`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "memory-retrieval-${UUID.randomUUID()}.db"
        val disk = Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val diskRepository = RoomMemoryRepository(disk, clock)
            val diskUseCase = ManageMemoryUseCase(MemoryDomain(clock), diskRepository)
            diskUseCase.execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.CREATE, MemoryId.new(),
                "Previous", "Original description", MemoryScope(MemoryScopeKind.GLOBAL)))
            val request = MemorySummaryReplacement(MemoryIntentId.new(), MemoryId.new(), "Replacement description",
                diskRepository.list(MemoryScopeKind.GLOBAL, MemoryStatus.ACTIVE, "")
                    .associate { it.memory.id to it.revisions.maxOf { r -> r.revision } })
            assertTrue(diskUseCase.replaceSummary(request) is MemoryMutationResult.Applied)
            disk.close()
            java.sql.DriverManager.registerDriver(org.sqlite.JDBC())
            java.sql.DriverManager.getConnection("jdbc:sqlite:${context.getDatabasePath(name).absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    com.nanzhufeng.ai.data.local.ContextIndexSchema.statements().forEach(statement::execute)
                    statement.executeQuery("SELECT COUNT(*) FROM memory_context_fts WHERE memory_context_fts MATCH 'original*' AND status='ACTIVE'").use { result ->
                        assertTrue(result.next()); assertEquals(0, result.getInt(1))
                    }
                    statement.executeQuery("SELECT COUNT(*) FROM memory_context_fts WHERE memory_context_fts MATCH 'replacement*' AND status='ACTIVE'").use { result ->
                        assertTrue(result.next()); assertEquals(1, result.getInt(1))
                    }
                }
            }
        } finally { disk.close(); context.deleteDatabase(name) }
    }

    @Test fun `high sensitive content never creates a row or conflict candidate`() {
        val result = useCase.execute(MemoryIntent(MemoryIntentId("secret"), MemoryIntentAction.CREATE, MemoryId("secret"), "密码", "密码: unsafe-secret", MemoryScope(MemoryScopeKind.GLOBAL)))
        assertEquals(MemoryMutationResult.Rejected(MemoryRejectionCode.HIGH_SENSITIVITY_PASSWORD), result)
        assertTrue(repository.list(null, null, "").isEmpty())
    }

    @Test fun `schema nine to ten preserves p4b rows and adds only memory tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p4c-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, projectId TEXT, currentLeafMessageId TEXT, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, defaultProviderId TEXT, defaultModelId TEXT, harnessId TEXT, harnessVersion INTEGER, contextPolicyVersion INTEGER NOT NULL, archivedAtEpochMs INTEGER, pinnedAtEpochMs INTEGER, deletedAtEpochMs INTEGER, schemaVersion INTEGER NOT NULL)"); db.execSQL("CREATE TABLE projects (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL, colorSemantic TEXT, iconSemantic TEXT, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, archivedAtEpochMs INTEGER, pinnedAtEpochMs INTEGER, deletedAtEpochMs INTEGER, schemaVersion INTEGER NOT NULL)"); db.execSQL("INSERT INTO conversations VALUES ('c', 'P4B', NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL, 1)") }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_9_10.migrate(sqlite)
        sqlite.query("SELECT title FROM conversations WHERE id='c'").use { assertTrue(it.moveToFirst()); assertEquals("P4B", it.getString(0)) }
        listOf("memories", "memory_revisions", "memory_intents", "memory_conflicts").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }
}
