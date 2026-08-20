package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P4BContextSelectionContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T13:00:00Z"), ZoneOffset.UTC)
    private val tree = ConversationTreeService(clock)
    private val projectDomain = ProjectDomain(clock)

    @Test fun `selection uses only current path and never leaks text or draft`() {
        val created = tree.create("P4-B")
        val root = tree.append(created, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("根消息不得泄漏"))))
        val assistant = tree.append(root, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("当前回答不得泄漏"))))
        val siblingUser = tree.editUserMessage(assistant, root.nodes.single { it.role == MessageRole.USER }.id, listOf(ContentBlock.Text("隐藏分支不得泄漏")))
        val sibling = tree.append(siblingUser, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("当前分支回答不得泄漏"))))
        val current = tree.saveDraft(sibling, "草稿不得泄漏", emptyList())

        val selected = ContextSelectionDomain().select(current, null)

        assertEquals(sibling.conversation.currentLeafMessageId, selected.messages.last().messageId)
        assertEquals(2, selected.messages.size)
        assertFalse(selected.messages.any { it.messageId == assistant.conversation.currentLeafMessageId })
        assertFalse(selected.toString().contains("不得泄漏"))
        assertNull(selected.projectContext.projectId)
        assertEquals(ContextSourceDisposition.EXCLUDED, selected.boundary(ContextSourceKind.CONVERSATION_DRAFT).disposition)
        assertEquals(ContextSourceDisposition.EXCLUDED, selected.boundary(ContextSourceKind.SIBLING_BRANCHES).disposition)
    }

    @Test fun `project revision is metadata only and memory knowledge attachments tool remain outside scope`() {
        val project = projectDomain.reviseInstruction(
            projectDomain.create(ProjectIntent(ProjectIntentId("project"), ProjectIntentAction.CREATE, ProjectId("p4b"), title = "项目")),
            "项目正文不得泄漏",
        )
        val conversation = tree.append(
            tree.create("P4-B", projectId = "p4b", settings = ConversationSettings(memorySources = listOf(MemorySourceReference("memory-1", "LONG_TERM")))),
            AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("对话正文不得泄漏"))),
        )
        val selected = ContextSelectionDomain().select(conversation, project)

        assertEquals(project.activeInstruction!!.id, selected.projectContext.instructionRevisionId)
        assertEquals(project.activeInstruction!!.contentHash, selected.projectContext.instructionHash)
        assertFalse(selected.toString().contains("正文不得泄漏"))
        assertFalse(selected.toString().contains("memory-1"))
        listOf(ContextSourceKind.MEMORY, ContextSourceKind.KNOWLEDGE, ContextSourceKind.RETRIEVAL, ContextSourceKind.SUMMARY_AND_COMPRESSION, ContextSourceKind.CACHE_PREFIX).forEach {
            assertEquals(ContextSourceDisposition.NOT_IMPLEMENTED, selected.boundary(it).disposition)
        }
        listOf(ContextSourceKind.ATTACHMENTS, ContextSourceKind.TOOL_RESULTS).forEach {
            assertEquals(ContextSourceDisposition.EXCLUDED, selected.boundary(it).disposition)
        }
    }

    @Test fun `use case rejects missing conversation and inconsistent project instead of falling back`() {
        val repository = FakeConversationRepository()
        val projects = FakeProjectRepository()
        val useCase = ReadContextSelectionUseCase(repository, projects)
        assertEquals(ContextSelectionResult.Rejected(ContextSelectionRejection.MISSING_CONVERSATION), useCase.execute(ConversationId("missing")))

        val missingProjectConversation = tree.create("孤立", projectId = "absent")
        repository.snapshot = missingProjectConversation
        assertEquals(ContextSelectionResult.Rejected(ContextSelectionRejection.INCONSISTENT_PROJECT_REFERENCE), useCase.execute(missingProjectConversation.conversation.id))
    }

    private fun ContextSelectionSnapshot.boundary(kind: ContextSourceKind): ContextSourceBoundary =
        requireNotNull(sourceBoundaries.singleOrNull { it.kind == kind })

    private class FakeConversationRepository(var snapshot: ConversationSnapshot? = null) : ConversationRepository {
        override fun save(snapshot: ConversationSnapshot): ConversationSnapshot = snapshot.also { this.snapshot = it }
        override fun findById(id: ConversationId): ConversationSnapshot? = snapshot?.takeIf { it.conversation.id == id }
        override fun listActive(): List<Conversation> = listOfNotNull(snapshot?.conversation)
    }

    private class FakeProjectRepository : ProjectRepository {
        override fun apply(intent: ProjectIntent, fingerprint: String, mutate: () -> ProjectSnapshot): ProjectMutationResult = error("not used")
        override fun findById(id: ProjectId): ProjectSnapshot? = null
        override fun list(scope: ProjectListScope): List<ProjectSnapshot> = emptyList()
        override fun projectForConversation(conversationId: ConversationId): ProjectId? = null
        override fun knowledgeScope(knowledgeId: KnowledgeItemId): ProjectId? = null
        override fun assignConversation(intent: ProjectIntent, fingerprint: String): ProjectMutationResult = error("not used")
        override fun assignKnowledge(intent: ProjectIntent, fingerprint: String): ProjectMutationResult = error("not used")
    }
}
