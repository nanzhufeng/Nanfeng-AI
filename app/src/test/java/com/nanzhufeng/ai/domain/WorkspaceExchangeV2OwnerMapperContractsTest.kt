package com.nanzhufeng.ai.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WorkspaceExchangeV2OwnerMapperContractsTest {
    private val now = Instant.parse("2026-08-20T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `read-only owners map to exact v2 fields and validate without a package`() {
        val source = fixture()
        val mapped = mapper(source).map(source.selection, NfaiExchangeSafeSettings("zh-CN", "SYSTEM"))
        assertTrue("mapped=$mapped", mapped is WorkspaceExchangeV2Mapping.Prepared)
        val exchange = JSONObject((mapped as WorkspaceExchangeV2Mapping.Prepared).exchangeJson)
        assertEquals("FOREST", exchange.getJSONArray("projects").getJSONObject(0).getJSONObject("appearance").getString("color"))
        assertEquals(1, exchange.getJSONArray("projects").getJSONObject(0).getJSONArray("instructionHistory").length())
        assertEquals("OPENROUTER", exchange.getJSONArray("conversations").getJSONObject(0).getJSONObject("settings").getString("defaultProviderId"))
        assertEquals(1, exchange.getJSONArray("conversations").getJSONObject(0).getJSONObject("settings").getJSONArray("memorySources").length())
        assertEquals(1, exchange.getJSONArray("knowledge").getJSONObject(0).getJSONArray("attachments").length())
        assertEquals("assets/${source.asset.sha256}", exchange.getJSONArray("knowledge").getJSONObject(0).getJSONArray("attachments").getJSONObject(0).getString("entry"))
        assertEquals("Memory title", exchange.getJSONArray("memory").getJSONObject(0).getString("title"))
        assertEquals("PROJECT", exchange.getJSONArray("relations").getJSONObject(0).getString("scope"))
        assertEquals(exchange.getJSONObject("export").getString("semanticHash"), NfaiExchangeV2Ir.validate(exchange).semanticHash)
        assertEquals(1, source.privateReadCount)
    }

    @Test fun `mapper rejects locators unsafe memory source and missing owner history`() {
        listOf(
            fixture(sourceReference = "content://forbidden"),
            fixture(memorySourceSummary = "/private/owner-only"),
            fixture(withMemoryHistory = false),
            fixture(withRelationHistory = false),
        ).forEach { source ->
            val mapped = mapper(source).map(source.selection, NfaiExchangeSafeSettings("zh-CN", "SYSTEM"))
            assertTrue("mapped=$mapped", mapped is WorkspaceExchangeV2Mapping.Rejected)
            assertEquals(0, source.privateReadCount)
        }
    }

    private fun mapper(source: Fixture) = NfaiExchangeV2OwnerMapper(source, "0.3.0-test", clock) { "export-v2-test" }

    private fun fixture(
        sourceReference: String? = null,
        memorySourceSummary: String = "Local confirmation",
        withMemoryHistory: Boolean = true,
        withRelationHistory: Boolean = true,
    ): Fixture {
        val projectId = ProjectId("project-v2-01")
        val conversationId = ConversationId("conversation-v2-01")
        val knowledgeId = KnowledgeItemId("knowledge-v2-01")
        val memoryId = MemoryId("memory-v2-01")
        val assetBytes = "fixture".toByteArray()
        val asset = AttachmentReference("attachments/v1/asset-v2-01", "text/plain", "fixture.txt", AttachmentId("asset-v2-01"), assetBytes.size.toLong(), hash(assetBytes))
        val project = ProjectSnapshot(
            Project(projectId, "Local project", "Owner-fidelity fixture", ProjectColorSemantic.FOREST, ProjectIconSemantic.FOLDER, now, now, schemaVersion = 1),
            listOf(ProjectInstructionRevision(ProjectInstructionRevisionId("instruction-v2-01"), projectId, 1, "Use only local notes", ProjectInstructionSource.USER, hash("Use only local notes".toByteArray()), now)),
        )
        val memoryTitle = "Memory title"; val memoryBody = "Memory body"
        val memory = MemoryItem(memoryId, memoryTitle, memoryBody, MemoryScope(MemoryScopeKind.PROJECT, projectId = projectId), MemorySource.USER_CONFIRMED, "user-action", memorySourceSummary, MemoryStatus.ACTIVE, ownerHash(memoryTitle, memoryBody), conceptHash(memoryTitle), now, now, now)
        val memorySnapshot = MemorySnapshot(memory, if (withMemoryHistory) listOf(MemoryRevision(MemoryRevisionId("memory-revision-v2-01"), memoryId, 1, memoryTitle, memoryBody, memory.scope, memory.source, memory.sourceStableId, memory.sourceSummary, memory.status, memory.contentHash, now)) else emptyList())
        val settings = ConversationSettings(ProviderId.OPENROUTER, "local.fixture", "fixture-harness", 1, listOf(MemorySourceReference(memoryId.value, MemorySource.USER_CONFIRMED.name, 1)), 1, 1)
        val messageId = MessageNodeId("message-v2-01")
        val conversation = ConversationSnapshot(Conversation(conversationId, "Local chat", projectId.value, messageId, now, now, settings = settings), listOf(MessageNode(messageId, conversationId, null, 0, MessageRole.USER, listOf(ContentBlock.Text("Fixture only")), now)), ConversationDraft(updatedAt = now))
        val knowledgeTitle = "Knowledge title"; val knowledgeBody = "Knowledge body"; val knowledgeHash = ownerHash(knowledgeTitle, knowledgeBody)
        val knowledgeItem = KnowledgeItem(knowledgeId, knowledgeTitle, knowledgeBody, listOf(SourceEvidence(CaptureSourceType.MANUAL_TEXT, now, sourceReference, setOf("title", "body"))), CandidateProvenance(CandidateId("candidate-v2-01"), InvocationId("invocation-v2-01"), ProviderId.MOCK, "local.fixture", 1), now, listOf(asset))
        val knowledgeSnapshot = KnowledgeSnapshot(knowledgeItem, KnowledgeLifecycle(KnowledgeStatus.ACTIVE, KnowledgeScope.PROJECT, projectId, setOf("fixture"), knowledgeHash, now), listOf(KnowledgeRevision(KnowledgeRevisionId("knowledge-revision-v2-01"), knowledgeId, 1, knowledgeTitle, knowledgeBody, KnowledgeStatus.ACTIVE, KnowledgeScope.PROJECT, projectId, setOf("fixture"), knowledgeHash, now)))
        val relationId = KnowledgeRelationshipId("relation-v2-01")
        val relation = KnowledgeRelationship(KnowledgeRelationshipId("relation-v2-01"), KnowledgeRelationshipType.RELATED, knowledgeId, knowledgeId, KnowledgeScope.PROJECT, projectId, KnowledgeRelationshipStatus.ACTIVE, now, now, KnowledgeRelationshipIntentId("relation-intent-v2-01"), KnowledgeRelationshipIntentId("relation-intent-v2-01"), KnowledgeRelationshipSuggestionSource.MANUAL)
        val relationSnapshot = KnowledgeRelationshipSnapshot(relation, if (withRelationHistory) listOf(KnowledgeRelationshipRevision(KnowledgeRelationshipRevisionId("relation-revision-v2-01"), relationId, 1, KnowledgeRelationshipAction.CONFIRM, KnowledgeRelationshipStatus.ACTIVE, relation.createdByIntentId, now)) else emptyList())
        return Fixture(project, conversation, knowledgeSnapshot, memorySnapshot, relationSnapshot, asset, assetBytes)
    }

    private class Fixture(
        val project: ProjectSnapshot,
        val conversation: ConversationSnapshot,
        val knowledge: KnowledgeSnapshot,
        val memory: MemorySnapshot,
        val relation: KnowledgeRelationshipSnapshot,
        val asset: AttachmentReference,
        private val assetBytes: ByteArray,
    ) : NfaiExchangeWorkspaceSource {
        var privateReadCount = 0
        val selection = NfaiExchangeWorkspaceSelection(
            NfaiExchangeExportSelection(setOf(project.project.id.value), setOf(conversation.conversation.id.value), setOf(knowledge.item.id.value), setOf(memory.memory.id.value), setOf(relation.relationship.id.value), setOf(asset.id.value)),
            mapOf(asset.id.value to "NORMAL"),
        )
        override fun project(id: ProjectId) = project.takeIf { it.project.id == id }
        override fun conversation(id: ConversationId) = conversation.takeIf { it.conversation.id == id }
        override fun knowledge(id: KnowledgeItemId) = knowledge.takeIf { it.item.id == id }
        override fun memory(id: MemoryId) = memory.takeIf { it.memory.id == id }
        override fun relationship(id: KnowledgeRelationshipId) = relation.takeIf { it.relationship.id == id }
        override fun attachment(id: AttachmentId) = asset.takeIf { it.id == id }
        override fun readPrivateAttachment(asset: AttachmentReference): AttachmentReadResult { privateReadCount++; return AttachmentReadResult.Content(assetBytes) }
    }

    private fun ownerHash(title: String, body: String) = MemoryDomain.sha256("${canonical(title)}\n${canonical(body)}")
    private fun conceptHash(title: String) = MemoryDomain.sha256(canonical(title))
    private fun canonical(value: String) = value.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")
    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
