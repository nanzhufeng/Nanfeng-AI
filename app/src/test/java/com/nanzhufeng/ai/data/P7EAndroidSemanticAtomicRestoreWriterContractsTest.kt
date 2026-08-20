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
import com.nanzhufeng.ai.data.local.MessageContentBlockEntity
import com.nanzhufeng.ai.data.local.MessageNodeEntity
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.ProjectEntity
import com.nanzhufeng.ai.data.local.ProjectInstructionRevisionEntity
import com.nanzhufeng.ai.domain.NfaiSyncKnownAnswerMaterial
import com.nanzhufeng.ai.domain.NfaiSyncResult
import com.nanzhufeng.ai.domain.NfaiSyncV1Gateway
import com.nanzhufeng.ai.domain.P7ERestoreMode
import com.nanzhufeng.ai.domain.P7ERestorePlanCoordinator
import com.nanzhufeng.ai.domain.P7ERestoreResult
import com.nanzhufeng.ai.domain.P7ESemanticSnapshotMapper
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Candidate Room write, typed reference validation and post-switch fresh-Room semantic readback. */
@RunWith(RobolectricTestRunner::class)
class P7EAndroidSemanticAtomicRestoreWriterContractsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun cleanup() {
        context.deleteDatabase("p7e-semantic-source.db")
        context.deleteDatabase("nanfeng-ai.db")
    }

    @Test fun `semantic plan is decoded through DAOs and atomically reopens with identical typed records`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val restored = executor.submit<Boolean> {
                val source = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "p7e-semantic-source.db").build()
                val target = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "nanfeng-ai.db").build()
                try {
                    seed(source)
                    val expected = P7ESemanticSnapshotMapper.toPreparedSnapshot(
                        AndroidP7ESemanticSnapshotSource(source).snapshot("semantic-document", 7),
                    )
                    val envelope = (NfaiSyncV1Gateway.sealKnownAnswer(
                        expected,
                        "fixture recovery only".toCharArray(),
                        NfaiSyncKnownAnswerMaterial(ByteArray(32) { 7 }, ByteArray(16) { 2 }, ByteArray(12) { 3 }, ByteArray(12) { 4 }),
                    ) as NfaiSyncResult.Sealed).canonicalEnvelope
                    val writer = AndroidP7ESemanticAtomicRestoreWriter(context, target)
                    val coordinator = P7ERestorePlanCoordinator(writer)
                    val plan = coordinator.plan(envelope, "fixture recovery only".toCharArray(), "com.nanzhufeng.ai", "semantic-document", 7, P7ERestoreMode.EMPTY_LOCAL)
                    require(plan is P7ERestoreResult.Planned)
                    val result = coordinator.restore(plan.plan)
                    require(result is P7ERestoreResult.Restored) { "restore=$result" }
                    val reopened = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "nanfeng-ai.db").build()
                    try {
                        val actual = P7ESemanticSnapshotMapper.toPreparedSnapshot(
                            AndroidP7ESemanticSnapshotSource(reopened).snapshot("semantic-document", 7),
                        )
                        actual.records == expected.records
                    } finally {
                        reopened.close()
                    }
                } finally {
                    source.close()
                }
            }.get()
            assertTrue(restored)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun `semantic writer rejects dangling relation before it can replace current Room truth`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val title = executor.submit<String> {
                val target = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "nanfeng-ai.db").build()
                try {
                    target.projectDao().insertProject(ProjectEntity("before", "Before", "", null, null, 1, 1, null, null, null, 1))
                    val broken = P7ESemanticSnapshotMapper.toPreparedSnapshot(
                        com.nanzhufeng.ai.domain.P7ESemanticSnapshot(
                            "com.nanzhufeng.ai", "semantic-document", 1,
                            listOf(
                                com.nanzhufeng.ai.domain.P7ESemanticState("relation", "broken", 1, valueJson = "{\"relationshipKey\":\"broken\",\"type\":\"SUPPORTS\",\"fromKnowledgeId\":\"missing\",\"toKnowledgeId\":\"missing\",\"scopeKind\":\"GLOBAL\",\"projectId\":null,\"status\":\"ACTIVE\",\"createdAtEpochMs\":1,\"updatedAtEpochMs\":1,\"revisions\":[]}"),
                                com.nanzhufeng.ai.domain.P7ESemanticState("safe_settings", "safe-settings", 1, valueJson = "{\"values\":{}}"),
                            ),
                        ),
                    )
                    val writer = AndroidP7ESemanticAtomicRestoreWriter(context, target)
                    val plan = com.nanzhufeng.ai.domain.P7ERestorePlan("nfai.sync.restore-plan.v1", "semantic-document", 1, "not-used", broken.records, P7ERestoreMode.REPLACE_LOCAL_CONFIRMED)
                    assertTrue(runCatching { writer.stage(plan) }.isFailure)
                    target.projectDao().findProject("before")?.title.orEmpty()
                } finally {
                    target.close()
                }
            }.get()
            assertEquals("Before", title)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun `interrupted confirmed replacement restores the checkpoint rather than clearing current Room`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val title = executor.submit<String> {
                val source = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "p7e-semantic-source.db").build()
                val target = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "nanfeng-ai.db").build()
                try {
                    seed(source)
                    target.projectDao().insertProject(ProjectEntity("before", "Before interrupt", "", null, null, 1, 1, null, null, null, 1))
                    val prepared = P7ESemanticSnapshotMapper.toPreparedSnapshot(
                        AndroidP7ESemanticSnapshotSource(source).snapshot("semantic-document", 7),
                    )
                    val envelope = (NfaiSyncV1Gateway.sealKnownAnswer(
                        prepared,
                        "fixture recovery only".toCharArray(),
                        NfaiSyncKnownAnswerMaterial(ByteArray(32) { 7 }, ByteArray(16) { 2 }, ByteArray(12) { 3 }, ByteArray(12) { 4 }),
                    ) as NfaiSyncResult.Sealed).canonicalEnvelope
                    val coordinator = P7ERestorePlanCoordinator(AndroidP7ESemanticAtomicRestoreWriter(context, target))
                    val plan = coordinator.plan(envelope, "fixture recovery only".toCharArray(), "com.nanzhufeng.ai", "semantic-document", 7, P7ERestoreMode.REPLACE_LOCAL_CONFIRMED)
                    require(plan is P7ERestoreResult.Planned)
                    var checks = 0
                    assertEquals(P7ERestoreResult.Rejected("INTERRUPTED"), coordinator.restore(plan.plan) { checks++ > 0 })
                    val reopened = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "nanfeng-ai.db").build()
                    try {
                        reopened.projectDao().findProject("before")?.title.orEmpty()
                    } finally {
                        reopened.close()
                    }
                } finally {
                    source.close()
                }
            }.get()
            assertEquals("Before interrupt", title)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun seed(database: NanfengAiDatabase) {
        database.projectDao().insertProject(ProjectEntity("project", "项目", "非敏感", null, null, 1, 2, null, null, null, 1))
        database.projectDao().insertRevision(ProjectInstructionRevisionEntity("project-r1", "project", 1, "本机指令", "USER", "hash-project", 2, 1))
        database.conversationDao().insertConversation(ConversationEntity("conversation", "对话", "project", "message", 1, 2, null, null, null, null, 1, null, null, null, 1))
        database.conversationDao().insertNode(MessageNodeEntity("message", "conversation", null, 0, "USER", 2, "COMPLETED", 1, null, null, null, null, 1))
        database.conversationDao().insertBlocks(listOf(MessageContentBlockEntity("message", 0, "TEXT", "非敏感消息", null, null, null, null, null, null, null, null, 1)))
        listOf("knowledge-a", "knowledge-b").forEach { id ->
            database.knowledgeDao().insertKnowledge(KnowledgeItemEntity(id, id, "非敏感正文", "local", "local", "MOCK", "fixture", 1, 1, 1))
            database.knowledgeDao().insertRevision(KnowledgeRevisionEntity("$id-r1", id, 1, id, "非敏感正文", "ACTIVE", "project", "hash-$id", 1))
            database.knowledgeDao().upsertProjectScope(com.nanzhufeng.ai.data.local.KnowledgeProjectScopeEntity(id, "project", 1, 1))
        }
        database.memoryDao().insertMemory(MemoryEntity("memory", "记忆", "非敏感记忆", "CONVERSATION", null, "conversation", "CONVERSATION||conversation", "USER_CONFIRMED", "user", "本机", "ACTIVE", "hash-memory", "concept-memory", 1, 2, 2, null, 1))
        database.memoryDao().insertRevision(MemoryRevisionEntity("memory-r1", "memory", 1, "记忆", "非敏感记忆", "CONVERSATION", null, "conversation", "USER_CONFIRMED", "user", "本机", "ACTIVE", "hash-memory", 1, 1))
        database.knowledgeRelationshipDao().insert(KnowledgeRelationshipEntity("relation", "relation-key", "SUPPORTS", "knowledge-a", "knowledge-b", "PROJECT", "project", "ACTIVE", 1, 2, "intent", "intent", "USER"))
        database.knowledgeRelationshipDao().insertRevision(KnowledgeRelationshipRevisionEntity("relation-r1", "relation", 1, "CREATE", "ACTIVE", "intent", 1))
    }
}
