package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomKnowledgeRelationshipRepository
import com.nanzhufeng.ai.data.local.RoomKnowledgeRepository
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
class P4GKnowledgeRelationshipRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var knowledge: ManageKnowledgeUseCase
    private lateinit var relationships: ManageKnowledgeRelationshipsUseCase
    @Before fun setup() { database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build(); knowledge = ManageKnowledgeUseCase(KnowledgeDomain(clock), RoomKnowledgeRepository(database, clock)); relationships = ManageKnowledgeRelationshipsUseCase(KnowledgeRelationshipDomain(clock), RoomKnowledgeRelationshipRepository(database, clock)); create("a", "甲", "正文 A"); create("b", "乙", "正文 B") }
    @After fun close() = database.close()

    @Test fun `confirm replay revoke and audit revisions survive Room reads`() {
        val intent = confirmation("confirm")
        val first = relationships.confirm(intent) as KnowledgeRelationshipMutationResult.Applied
        assertEquals(KnowledgeRelationshipStatus.ACTIVE, first.snapshot.relationship.status)
        assertTrue(relationships.confirm(intent) is KnowledgeRelationshipMutationResult.Replayed)
        assertEquals(1, relationships.list().single().revisions.size)
        val revoke = KnowledgeRelationshipIntent(KnowledgeRelationshipIntentId("revoke"), KnowledgeRelationshipAction.REVOKE, relationshipId = first.snapshot.relationship.id)
        assertTrue(relationships.revoke(revoke) is KnowledgeRelationshipMutationResult.Applied)
        val revoked = relationships.list(KnowledgeRelationshipListFilter(status = KnowledgeRelationshipStatus.REVOKED)).single()
        assertEquals(2, revoked.revisions.size); assertEquals(KnowledgeRelationshipStatus.REVOKED, revoked.relationship.status)
    }

    @Test fun `schema eleven to twelve preserves knowledge and appends relation tables only`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p4g-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(11) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE knowledge_items (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)"); db.execSQL("INSERT INTO knowledge_items VALUES ('k','已存在知识')") }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_11_12.migrate(sqlite)
        sqlite.query("SELECT title FROM knowledge_items WHERE id='k'").use { assertTrue(it.moveToFirst()); assertEquals("已存在知识", it.getString(0)) }
        listOf("knowledge_relationships", "knowledge_relationship_revisions", "knowledge_relationship_intents").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }

    private fun create(id: String, title: String, body: String) { knowledge.execute(KnowledgeIntent(KnowledgeIntentId("create-$id"), KnowledgeIntentAction.CREATE_MANUAL, KnowledgeItemId(id), title, body)) }
    private fun confirmation(id: String): KnowledgeRelationshipIntent {
        val a = requireNotNull(knowledge.detail(KnowledgeItemId("a"))); val b = requireNotNull(knowledge.detail(KnowledgeItemId("b")))
        fun expected(s: KnowledgeSnapshot) = KnowledgeRelationshipEndpointExpectation(s.item.id, s.revisions.maxOf { it.revision }, s.lifecycle.contentHash)
        return KnowledgeRelationshipIntent(KnowledgeRelationshipIntentId(id), KnowledgeRelationshipAction.CONFIRM, type = KnowledgeRelationshipType.RELATED, first = expected(a), second = expected(b))
    }
}
