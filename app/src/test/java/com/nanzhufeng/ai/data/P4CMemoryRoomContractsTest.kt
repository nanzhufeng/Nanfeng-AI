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
