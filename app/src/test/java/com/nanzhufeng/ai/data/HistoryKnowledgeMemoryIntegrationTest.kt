package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.*
import com.nanzhufeng.ai.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryKnowledgeMemoryIntegrationTest {
    @Test fun `new facts in an existing conversation update memory and reopens do not call provider again`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("history_knowledge_auto_curation_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val now = Instant.parse("2026-11-02T00:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val db = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val conversations = object : ConversationRepository {
                var snapshot = snapshot(4, now)
                override fun save(snapshot: ConversationSnapshot): ConversationSnapshot { this.snapshot = snapshot; return snapshot }
                override fun findById(id: ConversationId) = snapshot.takeIf { it.conversation.id == id }
                override fun listActive() = listOf(snapshot.conversation)
            }
            val memory = ManageMemoryUseCase(MemoryDomain(clock), RoomMemoryRepository(db, clock))
            val knowledge = RoomKnowledgeRepository(db, clock)
            var calls = 0
            var failLocalMemoryOnce = false
            fun owner() = AutomaticHistoryKnowledgeCurationOwner(
                settings = { AssistantExperienceSettings(autoHistoryKnowledgeEnabled = true) },
                conversations = conversations,
                readSource = ReadHistoryKnowledgeCurationSourceUseCase(conversations),
                refiner = HistoryKnowledgeRefiner {
                    calls++
                    HistoryKnowledgeCurationResult.Draft(HistoryKnowledgeCurationDraft("持续项目的长期规则", "第${calls}版资料：" + "用户明确要求保留资料来源并优先保护已有数据。".repeat(5), emptySet(), ProviderId.DEEPSEEK, "deepseek-flash", .95))
                },
                checkpoint = AndroidHistoryKnowledgeCurationCheckpointStore(context),
                readKnowledge = ReadKnowledgeLibraryUseCase(knowledge, RoomGeneratedCandidateRepository(db)),
                manageKnowledge = ManageKnowledgeUseCase(KnowledgeDomain(clock), knowledge),
                saveAutomaticMemory = { id, draft ->
                    if (failLocalMemoryOnce) { failLocalMemoryOnce = false; error("local fixture failure") }
                    memory.saveAutomaticSummary(id, draft.title, draft.body)
                },
            )
            assertTrue(owner().curateNext() is AutomaticHistoryKnowledgeCurationResult.Saved)
            assertEquals(1, memory.list(null, MemoryStatus.ACTIVE, "").size)
            assertEquals(HistoryKnowledgeCurationReason.NO_ELIGIBLE_CONVERSATION, (owner().curateNext() as AutomaticHistoryKnowledgeCurationResult.NotEligible).reason)
            assertEquals(1, calls)
            conversations.snapshot = snapshot(6, now)
            assertTrue(owner().curateNext() is AutomaticHistoryKnowledgeCurationResult.Saved)
            val updated = memory.list(null, MemoryStatus.ACTIVE, "").single()
            assertTrue(updated.memory.body.startsWith("第2版"))
            assertEquals(2, updated.revisions.size)
            val witness = AndroidHistoryCurationReceipts.memoryWitness(memory.list(null, null, ""))
            assertEquals(1, witness.getInt("automaticMemoryCount"))
            assertEquals(2, witness.getInt("automaticRevisionTotal"))
            assertFalse(witness.toString().contains(updated.memory.body))
            assertFalse(witness.toString().contains(updated.memory.id.value))
            // Model output is already durable in Knowledge; local failure must recover without resending.
            conversations.snapshot = snapshot(8, now)
            failLocalMemoryOnce = true
            assertTrue(runCatching { owner().curateNext() }.isFailure)
            owner().curateNext()
            assertEquals(3, calls)
            assertTrue(memory.list(null, MemoryStatus.ACTIVE, "").single().memory.body.startsWith("第3版"))
            memory.execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.UPDATE, updated.memory.id, "手动摘要", "由我维护", MemoryScope(MemoryScopeKind.GLOBAL)))
            conversations.snapshot = snapshot(10, now)
            owner().curateNext()
            assertEquals("由我维护", memory.list(null, MemoryStatus.ACTIVE, "").single().memory.body)
            assertEquals(4, knowledge.listAll().size)
        } finally { db.close() }
    }

    private fun snapshot(count: Int, now: Instant): ConversationSnapshot {
        val id = ConversationId("history-integration")
        val nodes = (0 until count).map { index -> MessageNode(
            MessageNodeId("m$index"), id, if (index == 0) null else MessageNodeId("m${index - 1}"), 0,
            if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
            listOf(ContentBlock.Text("第${index}条确认：" + "长期项目必须保护数据并保留来源。".repeat(12))), now.plusSeconds(index.toLong()),
        ) }
        return ConversationSnapshot(Conversation(id, "长期项目", currentLeafMessageId = nodes.last().id, createdAt = now, updatedAt = now.plusSeconds(count.toLong())), nodes, ConversationDraft(updatedAt = now))
    }
}
