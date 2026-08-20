package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.ConversationEntity
import com.nanzhufeng.ai.data.local.KnowledgeItemEntity
import com.nanzhufeng.ai.data.local.KnowledgeRelationshipEntity
import com.nanzhufeng.ai.data.local.KnowledgeRelationshipRevisionEntity
import com.nanzhufeng.ai.data.local.KnowledgeRevisionEntity
import com.nanzhufeng.ai.data.local.MemoryEntity
import com.nanzhufeng.ai.data.local.MemoryRevisionEntity
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.ProjectEntity
import com.nanzhufeng.ai.domain.P7ESemanticSnapshotMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P7EAndroidSemanticSnapshotSourceContractsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `typed Android domain facts map one stable semantic record per P7-E object kind`() {
        val database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        try {
            database.projectDao().insertProject(ProjectEntity("project-p7e", "项目", "非敏感", null, null, 1, 2, null, null, null, 1))
            database.conversationDao().insertConversation(ConversationEntity("conversation-p7e", "对话", "project-p7e", null, 1, 2, null, null, null, null, 1, null, null, null, 1))
            database.knowledgeDao().insertKnowledge(KnowledgeItemEntity("knowledge-p7e", "知识", "非敏感正文", "local", "local", "LOCAL", "LOCAL", 1, 1, 1))
            database.knowledgeDao().insertRevision(KnowledgeRevisionEntity("knowledge-revision-p7e", "knowledge-p7e", 1, "知识", "非敏感正文", "ACTIVE", null, "", 1))
            database.memoryDao().insertMemory(MemoryEntity("memory-p7e", "记忆", "非敏感记忆", "GLOBAL", null, null, "GLOBAL", "USER_CONFIRMED", "user", "本机", "ACTIVE", "hash", "concept", 1, 2, 2, null, 1))
            database.memoryDao().insertRevision(MemoryRevisionEntity("memory-revision-p7e", "memory-p7e", 1, "记忆", "非敏感记忆", "GLOBAL", null, null, "USER_CONFIRMED", "user", "本机", "ACTIVE", "hash", 1, 1))
            database.knowledgeRelationshipDao().insert(KnowledgeRelationshipEntity("relation-p7e", "relation-p7e", "SUPPORTS", "knowledge-p7e", "knowledge-p7e", "GLOBAL", null, "ACTIVE", 1, 2, "p7e", "p7e", "USER"))
            database.knowledgeRelationshipDao().insertRevision(KnowledgeRelationshipRevisionEntity("relation-revision-p7e", "relation-p7e", 1, "CREATE", "ACTIVE", "p7e", 1))

            val snapshot = AndroidP7ESemanticSnapshotSource(database).snapshot("document-p7e", 4)
            assertEquals(setOf("project", "conversation", "knowledge", "memory", "relation", "safe_settings"), snapshot.states.map { it.kind }.toSet())
            assertTrue(P7ESemanticSnapshotMapper.toPreparedSnapshot(snapshot).records.all { it.contentJson.contains("nfai.sync.semantic-record.v1") })
        } finally {
            database.close()
        }
    }

    @Test fun `high sensitivity and unsupported attachment state reject the whole semantic plan`() {
        val database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        try {
            database.projectDao().insertProject(ProjectEntity("project-p7e", "项目", "api key: must not sync", null, null, 1, 1, null, null, null, 1))
            assertTrue(runCatching { AndroidP7ESemanticSnapshotSource(database).snapshot("document-p7e", 1) }.isFailure)
        } finally {
            database.close()
        }
    }
}
