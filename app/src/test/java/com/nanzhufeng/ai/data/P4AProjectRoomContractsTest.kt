package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomProjectRepository
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
class P4AProjectRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomProjectRepository
    private lateinit var useCase: ManageProjectUseCase
    @Before fun setup() { database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build(); repository = RoomProjectRepository(database); useCase = ManageProjectUseCase(ProjectDomain(clock), repository) }
    @After fun close() = database.close()

    @Test fun `create pin archive revisions and intent replay survive repository rebuild`() {
        val id = ProjectId("project")
        val create = ProjectIntent(ProjectIntentId("create"), ProjectIntentAction.CREATE, id, title = "项目")
        assertTrue(useCase.execute(create) is ProjectMutationResult.Applied)
        assertTrue(useCase.execute(create) is ProjectMutationResult.Replayed)
        assertTrue(useCase.execute(create.copy(title = "冲突")) is ProjectMutationResult.Rejected)
        useCase.execute(ProjectIntent(ProjectIntentId("instruction"), ProjectIntentAction.UPDATE_INSTRUCTION, id, instruction = "范围"))
        useCase.execute(ProjectIntent(ProjectIntentId("pin"), ProjectIntentAction.PIN, id))
        assertEquals(listOf(id), repository.list(ProjectListScope.ACTIVE).map { it.project.id })
        useCase.execute(ProjectIntent(ProjectIntentId("archive"), ProjectIntentAction.ARCHIVE, id))
        assertEquals(listOf(id), RoomProjectRepository(database).list(ProjectListScope.ARCHIVED).map { it.project.id })
        assertEquals("范围", RoomProjectRepository(database).findById(id)!!.activeInstruction!!.content)
    }

    @Test fun `schema eight to nine leaves conversations untouched and adds P4A tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p4a-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(8) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, projectId TEXT, currentLeafMessageId TEXT, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, defaultProviderId TEXT, defaultModelId TEXT, harnessId TEXT, harnessVersion INTEGER, contextPolicyVersion INTEGER NOT NULL, archivedAtEpochMs INTEGER, pinnedAtEpochMs INTEGER, deletedAtEpochMs INTEGER, schemaVersion INTEGER NOT NULL)"); db.execSQL("INSERT INTO conversations VALUES ('c', 'P3', NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL, 1)") }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_8_9.migrate(sqlite)
        sqlite.query("SELECT title, projectId FROM conversations WHERE id='c'").use { assertTrue(it.moveToFirst()); assertEquals("P3", it.getString(0)); assertEquals(null, it.getString(1)) }
        listOf("projects", "project_instruction_revisions", "project_intents", "knowledge_project_scopes").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }
}
