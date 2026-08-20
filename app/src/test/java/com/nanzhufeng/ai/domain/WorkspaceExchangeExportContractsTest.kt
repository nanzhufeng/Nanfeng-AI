package com.nanzhufeng.ai.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WorkspaceExchangeExportContractsTest {
    private val now = Instant.parse("2026-08-20T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `explicit complete workspace maps projects knowledge memory relation and private attachment`() {
        val fixture = fixture()
        val prepared = ExportWorkspaceExchangeUseCase(fixture, "0.3.0-test", clock) { "workspace-exchange-test" }
            .prepare(fixture.selection(), NfaiExchangeSafeSettings("zh-CN", "SYSTEM"))
        assertTrue("prepared=$prepared", prepared is WorkspaceExchangePreparation.Prepared)
        val snapshot = (prepared as WorkspaceExchangePreparation.Prepared).snapshot
        val exchange = JSONObject(snapshot.exchangeJson)
        assertEquals(1, exchange.getJSONArray("projects").length())
        assertEquals(1, exchange.getJSONArray("conversations").length())
        assertEquals(2, exchange.getJSONArray("knowledge").length())
        assertEquals(1, exchange.getJSONArray("memory").length())
        assertEquals(1, exchange.getJSONArray("relations").length())
        assertEquals(1, snapshot.assets.size)
        val output = createTempFile("p6-workspace", ".nfai-exchange")
        try {
            assertTrue(NfaiExchangeV1Gateway.export(snapshot, output) is NfaiExchangeResult.Exported)
            val preflight = NfaiExchangeV1Gateway.preflight(output) as NfaiExchangeResult.Preflighted
            assertEquals(1, preflight.value.projectCount)
            assertEquals(2, preflight.value.knowledgeCount)
            assertEquals(1, preflight.value.memoryCount)
            assertEquals(1, preflight.value.relationCount)
        } finally {
            output.delete()
        }
    }

    @Test fun `mapper fails closed when a scoped dependency relation endpoint or attachment is not explicitly selected`() {
        val fixture = fixture()
        val base = fixture.selection().objects
        val invalidSelections = listOf(
            fixture.selection(base.copy(projectIds = emptySet())),
            fixture.selection(base.copy(knowledgeIds = setOf("knowledge-1"))),
            fixture.selection(base.copy(attachmentIds = emptySet())),
        )
        invalidSelections.forEach { selection ->
            val result = ExportWorkspaceExchangeUseCase(fixture, "0.3.0-test", clock) { "workspace-exchange-test" }
                .prepare(selection, NfaiExchangeSafeSettings("zh-CN", "SYSTEM"))
            assertTrue("result=$result", result is WorkspaceExchangePreparation.Rejected)
        }
    }

    @Test fun `unreadable private attachment rejects before a package can be prepared`() {
        val fixture = fixture(readableAttachment = false)
        val result = ExportWorkspaceExchangeUseCase(fixture, "0.3.0-test", clock) { "workspace-exchange-test" }
            .prepare(fixture.selection(), NfaiExchangeSafeSettings("zh-CN", "SYSTEM"))
        assertTrue("result=$result", result is WorkspaceExchangePreparation.Rejected)
    }

    private fun Fixture.selection(objects: NfaiExchangeExportSelection = this.selection): NfaiExchangeWorkspaceSelection =
        NfaiExchangeWorkspaceSelection(objects, objects.attachmentIds.associateWith { "NORMAL" })

    private fun fixture(readableAttachment: Boolean = true): Fixture {
        val projectId = ProjectId("project-1")
        val conversationId = ConversationId("conversation-1")
        val assetId = AttachmentId("asset-1")
        val bytes = "abc".toByteArray()
        val asset = AttachmentReference("attachments/v1/asset-1", "text/plain", "note.txt", assetId, bytes.size.toLong(), hash(bytes))
        val conversationReference = asset.toConversationReference()
        val messageId = MessageNodeId("message-1")
        val conversation = ConversationSnapshot(
            Conversation(conversationId, "完整工作区", projectId.value, messageId, now, now),
            listOf(MessageNode(messageId, conversationId, null, 0, MessageRole.USER, listOf(ContentBlock.Text("文本"), ContentBlock.Attachment(conversationReference)), now)),
            ConversationDraft(updatedAt = now),
        )
        val project = ProjectSnapshot(Project(projectId, "项目", createdAt = now, updatedAt = now), emptyList())
        fun knowledge(id: String, body: String) = KnowledgeSnapshot(
            KnowledgeItem(KnowledgeItemId(id), id, body, emptyList(), CandidateProvenance(CandidateId("candidate-$id"), InvocationId("invocation-$id"), ProviderId.MOCK, "test", 1), now),
            KnowledgeLifecycle(scope = KnowledgeScope.PROJECT, projectId = projectId, contentHash = hash(body.toByteArray()), updatedAt = now),
            emptyList(),
        )
        val firstKnowledge = knowledge("knowledge-1", "知识一")
        val secondKnowledge = knowledge("knowledge-2", "知识二")
        val memory = MemorySnapshot(
            MemoryItem(MemoryId("memory-1"), "记忆", "持久记忆", MemoryScope(MemoryScopeKind.PROJECT, projectId = projectId), MemorySource.USER_CONFIRMED, "user", "本机", MemoryStatus.ACTIVE, hash("持久记忆".toByteArray()), hash("持久记忆".toByteArray()), now, now, now),
            emptyList(),
        )
        val relation = KnowledgeRelationshipSnapshot(
            KnowledgeRelationship(KnowledgeRelationshipId("relation-1"), KnowledgeRelationshipType.RELATED, firstKnowledge.item.id, secondKnowledge.item.id, KnowledgeScope.PROJECT, projectId, KnowledgeRelationshipStatus.ACTIVE, now, now, KnowledgeRelationshipIntentId("relation-intent-1"), KnowledgeRelationshipIntentId("relation-intent-1"), KnowledgeRelationshipSuggestionSource.MANUAL),
            emptyList(),
        )
        return Fixture(
            projects = mapOf(projectId to project), conversations = mapOf(conversationId to conversation), knowledge = mapOf(firstKnowledge.item.id to firstKnowledge, secondKnowledge.item.id to secondKnowledge),
            memories = mapOf(memory.memory.id to memory), relations = mapOf(relation.relationship.id to relation), attachments = mapOf(assetId to asset), bytes = bytes, readableAttachment = readableAttachment,
            selection = NfaiExchangeExportSelection(setOf(projectId.value), setOf(conversationId.value), setOf(firstKnowledge.item.id.value, secondKnowledge.item.id.value), setOf(memory.memory.id.value), setOf(relation.relationship.id.value), setOf(assetId.value)),
        )
    }

    private class Fixture(
        val projects: Map<ProjectId, ProjectSnapshot>, val conversations: Map<ConversationId, ConversationSnapshot>, val knowledge: Map<KnowledgeItemId, KnowledgeSnapshot>,
        val memories: Map<MemoryId, MemorySnapshot>, val relations: Map<KnowledgeRelationshipId, KnowledgeRelationshipSnapshot>, val attachments: Map<AttachmentId, AttachmentReference>,
        val bytes: ByteArray, val readableAttachment: Boolean, val selection: NfaiExchangeExportSelection,
    ) : NfaiExchangeWorkspaceSource {
        override fun project(id: ProjectId) = projects[id]
        override fun conversation(id: ConversationId) = conversations[id]
        override fun knowledge(id: KnowledgeItemId) = knowledge[id]
        override fun memory(id: MemoryId) = memories[id]
        override fun relationship(id: KnowledgeRelationshipId) = relations[id]
        override fun attachment(id: AttachmentId) = attachments[id]
        override fun readPrivateAttachment(asset: AttachmentReference) = if (readableAttachment) AttachmentReadResult.Content(bytes) else AttachmentReadResult.Rejected(AiTaskError.AttachmentSourceUnavailable)
    }

    private fun hash(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
