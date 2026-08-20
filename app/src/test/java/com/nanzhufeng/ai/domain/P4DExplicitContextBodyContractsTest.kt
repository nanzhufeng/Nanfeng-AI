package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P4DExplicitContextBodyContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T15:00:00Z"), ZoneOffset.UTC)
    private val tree = ConversationTreeService(clock)
    private val projectDomain = ProjectDomain(clock)

    @Test fun `all controls default off and legacy memory sources never inject a body`() {
        val conversation = tree.append(
            tree.create("P4-D", settings = ConversationSettings(memorySources = listOf(MemorySourceReference("legacy-memory", "LONG_TERM")))),
            AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("只有明确选择才可预览"))),
        )
        val memories = FakeMemoryRepository(listOf(memory(MemoryScope(MemoryScopeKind.GLOBAL), "偏好", "不要自动注入")))
        val useCase = useCase(conversation, null, memories)

        val plan = (useCase.plan(conversation.conversation.id) as ExplicitContextBodyPlanResult.Available).plan
        assertTrue(plan.candidates.any { it.kind == ContextBodySourceKind.GLOBAL_MEMORY })
        val selected = useCase.execute(ExplicitContextBodyRequest(conversation.conversation.id)) as ExplicitContextBodyResult.Selected

        assertTrue(selected.snapshot.entries.isEmpty())
        assertFalse(selected.snapshot.toString().contains("不要自动注入"))
    }

    @Test fun `explicit choices map eligible global project and conversation content to L0 L1 L2`() {
        val project = projectDomain.reviseInstruction(
            projectDomain.create(ProjectIntent(ProjectIntentId("create"), ProjectIntentAction.CREATE, ProjectId("project"), title = "项目")),
            "只遵守已确认的项目规则",
        )
        val created = tree.create("P4-D", projectId = "project")
        val user = tree.append(created, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("当前用户正文"))))
        val conversation = tree.append(user, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("当前助手正文"))))
        val global = memory(MemoryScope(MemoryScopeKind.GLOBAL), "全局偏好", "全局正文")
        val projectMemory = memory(MemoryScope(MemoryScopeKind.PROJECT, projectId = ProjectId("project")), "项目偏好", "项目正文")
        val conversationMemory = memory(MemoryScope(MemoryScopeKind.CONVERSATION, conversationId = conversation.conversation.id), "会话偏好", "会话正文")
        val useCase = useCase(conversation, project, FakeMemoryRepository(listOf(global, projectMemory, conversationMemory)))

        val result = useCase.execute(
            ExplicitContextBodyRequest(
                conversation.conversation.id,
                includeProjectInstruction = true,
                includeConversationPath = true,
                selectedMemoryIds = setOf(global.memory.id, projectMemory.memory.id, conversationMemory.memory.id),
            ),
        ) as ExplicitContextBodyResult.Selected

        assertTrue(result.snapshot.entries.any { it.kind == ContextBodySourceKind.PROJECT_INSTRUCTION && it.body == "只遵守已确认的项目规则" })
        assertEquals(2, result.snapshot.entries.count { it.kind == ContextBodySourceKind.CONVERSATION_PATH })
        assertEquals(setOf(ContextBodyLayer.L0_STABLE, ContextBodyLayer.L1_PROJECT, ContextBodyLayer.L2_CONVERSATION), result.snapshot.entries.map { it.layer }.toSet())
        assertTrue(result.snapshot.entries.any { it.kind == ContextBodySourceKind.GLOBAL_MEMORY && it.body == "全局正文" })
        assertTrue(result.snapshot.entries.any { it.kind == ContextBodySourceKind.PROJECT_MEMORY && it.body == "项目正文" })
        assertTrue(result.snapshot.entries.any { it.kind == ContextBodySourceKind.CONVERSATION_MEMORY && it.body == "会话正文" })
        assertEquals(ContextBodyDisposition.EXCLUDED, result.snapshot.boundaries.single { it.kind == ContextBodySourceKind.RECENT_ACTIONS }.disposition)
    }

    @Test fun `path reads text only and never reads draft siblings attachments or tool summaries`() {
        val attachment = ConversationAttachmentReference(AttachmentId("a"), "image/png", "hidden.png", 1, "a".repeat(64))
        val root = tree.append(tree.create("P4-D"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("根文本"), ContentBlock.Attachment(attachment))))
        val tool = tree.append(root, AppendMessageRequest(MessageRole.TOOL, listOf(ContentBlock.ToolResult("tool", "不得读取的 Tool 摘要"))))
        val current = tree.append(tool, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("当前文本"))))
        val sibling = tree.editUserMessage(current, root.nodes.single { it.role == MessageRole.USER }.id, listOf(ContentBlock.Text("兄弟文本")))
        val selected = tree.append(sibling, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("选择文本"), ContentBlock.Attachment(attachment))))
        val withDraft = tree.saveDraft(selected, "草稿文本", emptyList())
        val useCase = useCase(withDraft, null, FakeMemoryRepository())

        val result = useCase.execute(ExplicitContextBodyRequest(withDraft.conversation.id, includeConversationPath = true)) as ExplicitContextBodyResult.Selected
        val preview = result.snapshot.entries.joinToString("|") { it.body }

        assertTrue(preview.contains("兄弟文本") && preview.contains("选择文本"))
        assertFalse(preview.contains("当前文本") || preview.contains("草稿文本") || preview.contains("Tool 摘要") || preview.contains("hidden.png"))
        assertFalse(result.snapshot.entries.any { it.kind == ContextBodySourceKind.TOOL_RESULT })
    }

    @Test fun `paused foreign and sensitive Memory are rejected without partial body`() {
        val conversation = tree.append(tree.create("P4-D"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("安全正文"))))
        val paused = memory(MemoryScope(MemoryScopeKind.GLOBAL), "暂停", "暂停正文", MemoryStatus.PAUSED)
        val foreign = memory(MemoryScope(MemoryScopeKind.CONVERSATION, conversationId = ConversationId("other")), "其他会话", "其他正文")
        val sensitive = memory(MemoryScope(MemoryScopeKind.GLOBAL), "敏感", "API Key: should-not-preview")
        val rejectedUseCase = useCase(conversation, null, FakeMemoryRepository(listOf(paused, foreign)))
        assertEquals(ExplicitContextBodyRejection.INELIGIBLE_MEMORY, (rejectedUseCase.execute(ExplicitContextBodyRequest(conversation.conversation.id, selectedMemoryIds = setOf(paused.memory.id))) as ExplicitContextBodyResult.Rejected).reason)

        val sensitiveUseCase = useCase(conversation, null, FakeMemoryRepository(listOf(sensitive)))
        assertEquals(ExplicitContextBodyRejection.SENSITIVE_CONTENT, (sensitiveUseCase.execute(ExplicitContextBodyRequest(conversation.conversation.id, selectedMemoryIds = setOf(sensitive.memory.id))) as ExplicitContextBodyResult.Rejected).reason)
    }

    @Test fun `missing conversation and inconsistent project remain explicit rejections`() {
        val missing = ReadExplicitContextBodyUseCase(FakeConversationRepository(), FakeProjectRepository(), FakeMemoryRepository())
        assertEquals(ExplicitContextBodyRejection.MISSING_CONVERSATION, (missing.plan(ConversationId("missing")) as ExplicitContextBodyPlanResult.Rejected).reason)

        val orphan = tree.create("孤立", projectId = "absent")
        val inconsistent = useCase(orphan, null, FakeMemoryRepository())
        assertEquals(ExplicitContextBodyRejection.INCONSISTENT_PROJECT_REFERENCE, (inconsistent.plan(orphan.conversation.id) as ExplicitContextBodyPlanResult.Rejected).reason)
    }

    private fun useCase(conversation: ConversationSnapshot, project: ProjectSnapshot?, memories: MemoryRepository): ReadExplicitContextBodyUseCase =
        ReadExplicitContextBodyUseCase(FakeConversationRepository(conversation), FakeProjectRepository(project), memories)

    private fun memory(scope: MemoryScope, title: String, body: String, status: MemoryStatus = MemoryStatus.ACTIVE): MemorySnapshot {
        val item = MemoryItem(MemoryId.new(), title, body, scope, MemorySource.USER_CONFIRMED, "user", "用户明确创建", status, MemoryDomain.sha256("$title|$body"), MemoryDomain.sha256(title), clock.instant(), clock.instant(), clock.instant())
        return MemorySnapshot(item, listOf(MemoryRevision(MemoryRevisionId.new(), item.id, 1, title, body, scope, item.source, item.sourceStableId, item.sourceSummary, status, item.contentHash, clock.instant())))
    }

    private class FakeConversationRepository(private val snapshot: ConversationSnapshot? = null) : ConversationRepository {
        override fun save(snapshot: ConversationSnapshot): ConversationSnapshot = snapshot
        override fun findById(id: ConversationId): ConversationSnapshot? = snapshot?.takeIf { it.conversation.id == id }
        override fun listActive(): List<Conversation> = listOfNotNull(snapshot?.conversation)
    }
    private class FakeProjectRepository(private val snapshot: ProjectSnapshot? = null) : ProjectRepository {
        override fun apply(intent: ProjectIntent, fingerprint: String, mutate: () -> ProjectSnapshot): ProjectMutationResult = error("unused")
        override fun findById(id: ProjectId): ProjectSnapshot? = snapshot?.takeIf { it.project.id == id }
        override fun list(scope: ProjectListScope): List<ProjectSnapshot> = listOfNotNull(snapshot)
        override fun projectForConversation(conversationId: ConversationId): ProjectId? = null
        override fun knowledgeScope(knowledgeId: KnowledgeItemId): ProjectId? = null
        override fun assignConversation(intent: ProjectIntent, fingerprint: String): ProjectMutationResult = error("unused")
        override fun assignKnowledge(intent: ProjectIntent, fingerprint: String): ProjectMutationResult = error("unused")
    }
    private class FakeMemoryRepository(private val items: List<MemorySnapshot> = emptyList()) : MemoryRepository {
        override fun mutate(intent: MemoryIntent, fingerprint: String): MemoryMutationResult = error("P4-D has no write path")
        override fun findById(id: MemoryId): MemorySnapshot? = items.firstOrNull { it.memory.id == id }
        override fun list(scope: MemoryScopeKind?, status: MemoryStatus?, search: String): List<MemorySnapshot> = items.filter { status == null || it.memory.status == status }
        override fun conflict(id: MemoryConflictId): MemoryConflict? = null
    }
    private fun List<ContextBodyBoundary>.single(kind: ContextBodySourceKind): ContextBodyBoundary = single { it.kind == kind }
}
