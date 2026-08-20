package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P4JLocalContextCompressionContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T16:00:00Z"), ZoneOffset.UTC)
    private val tree = ConversationTreeService(clock)

    @Test fun `empty explicit selection stays empty and never reads legacy memory sources`() {
        val conversation = tree.append(
            tree.create("P4-J", settings = ConversationSettings(memorySources = listOf(MemorySourceReference("legacy", "LONG_TERM")))),
            AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("不能自动压缩这里"))),
        )
        val result = useCase(conversation).execute(LocalContextCompressionRequest(ExplicitContextBodyRequest(conversation.conversation.id))) as LocalContextCompressionResult.Compressed

        assertTrue(result.snapshot.entries.isEmpty())
        assertFalse(result.snapshot.toString().contains("不能自动压缩这里"))
    }

    @Test fun `only explicitly selected current path is compressed with stable evidence`() {
        val conversation = tree.append(
            tree.create("P4-J"),
            AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("用户已选择的本地正文"))),
        )
        val result = useCase(conversation).execute(
            LocalContextCompressionRequest(ExplicitContextBodyRequest(conversation.conversation.id, includeConversationPath = true)),
        ) as LocalContextCompressionResult.Compressed

        val entry = result.snapshot.entries.single()
        assertEquals(ContextBodySourceKind.CONVERSATION_PATH, entry.kind)
        assertEquals("用户已选择的本地正文", entry.compressedBody)
        assertEquals(0, entry.omittedCodePointCount)
        assertEquals(LOCAL_CONTEXT_COMPRESSION_POLICY_VERSION, entry.policyVersion)
        assertEquals(MemoryDomain.sha256(entry.compressedBody), entry.compressedContentHash)
    }

    @Test fun `extractive truncation preserves code point boundaries head tail and omission evidence`() {
        val domain = LocalContextCompressionDomain()
        val source = ExplicitContextBodyEntry(ContextBodyLayer.L2_CONVERSATION, ContextBodySourceKind.CONVERSATION_PATH, "message", "当前对话消息", "甲😀乙丙丁戊己", MemoryDomain.sha256("source"), MessageRole.USER)
        val result = domain.compress(ConversationId("conversation"), metadata(), listOf(source), LocalContextCompressionPolicy(maxEntryCodePoints = 4, maxTotalCodePoints = 4)) as LocalContextCompressionResult.Compressed

        val entry = result.snapshot.entries.single()
        assertEquals("甲😀戊己", entry.compressedBody)
        assertEquals(7, entry.originalCodePointCount)
        assertEquals(4, entry.retainedCodePointCount)
        assertEquals(3, entry.omittedCodePointCount)
    }

    @Test fun `ordering is deterministic and post-selection sensitive content rejects everything`() {
        val domain = LocalContextCompressionDomain()
        val first = ExplicitContextBodyEntry(ContextBodyLayer.L2_CONVERSATION, ContextBodySourceKind.CONVERSATION_PATH, "z", "z", "正文", MemoryDomain.sha256("z"))
        val second = ExplicitContextBodyEntry(ContextBodyLayer.L0_STABLE, ContextBodySourceKind.GLOBAL_MEMORY, "a", "a", "记忆", MemoryDomain.sha256("a"))
        val ordered = domain.compress(ConversationId("conversation"), metadata(), listOf(first, second), LocalContextCompressionPolicy(maxEntryCodePoints = 10, maxTotalCodePoints = 10)) as LocalContextCompressionResult.Compressed
        assertEquals(listOf("a", "z"), ordered.snapshot.entries.map { it.sourceId })

        val sensitive = first.copy(body = "Authorization: should-not-appear")
        val rejected = domain.compress(ConversationId("conversation"), metadata(), listOf(first, sensitive.copy(sourceId = "other")), LocalContextCompressionPolicy()) as LocalContextCompressionResult.Rejected
        assertEquals(LocalContextCompressionRejection.SENSITIVE_CONTENT, rejected.reason)
    }

    @Test fun `P4-D missing conversation rejection remains explicit`() {
        val missing = ReadLocalContextCompressionUseCase(ReadExplicitContextBodyUseCase(FakeConversationRepository(), FakeProjectRepository(), FakeMemoryRepository()))
        val result = missing.execute(LocalContextCompressionRequest(ExplicitContextBodyRequest(ConversationId("missing")))) as LocalContextCompressionResult.Rejected
        assertEquals(LocalContextCompressionRejection.MISSING_CONVERSATION, result.reason)
    }

    private fun useCase(snapshot: ConversationSnapshot): ReadLocalContextCompressionUseCase =
        ReadLocalContextCompressionUseCase(ReadExplicitContextBodyUseCase(FakeConversationRepository(snapshot), FakeProjectRepository(), FakeMemoryRepository()))

    private fun metadata(): ContextSelectionSnapshot = ContextSelectionSnapshot(
        conversationId = ConversationId("conversation"), currentBranchId = null,
        projectContext = ProjectInstructionResolution.resolve(null).projectContext,
        messages = emptyList(), sourceBoundaries = emptyList(),
    )

    private class FakeConversationRepository(private val snapshot: ConversationSnapshot? = null) : ConversationRepository {
        override fun save(snapshot: ConversationSnapshot): ConversationSnapshot = snapshot
        override fun findById(id: ConversationId): ConversationSnapshot? = snapshot?.takeIf { it.conversation.id == id }
        override fun listActive(): List<Conversation> = listOfNotNull(snapshot?.conversation)
    }
    private class FakeProjectRepository : ProjectRepository {
        override fun apply(intent: ProjectIntent, fingerprint: String, mutate: () -> ProjectSnapshot): ProjectMutationResult = error("unused")
        override fun findById(id: ProjectId): ProjectSnapshot? = null
        override fun list(scope: ProjectListScope): List<ProjectSnapshot> = emptyList()
        override fun projectForConversation(conversationId: ConversationId): ProjectId? = null
        override fun knowledgeScope(knowledgeId: KnowledgeItemId): ProjectId? = null
        override fun assignConversation(intent: ProjectIntent, fingerprint: String): ProjectMutationResult = error("unused")
        override fun assignKnowledge(intent: ProjectIntent, fingerprint: String): ProjectMutationResult = error("unused")
    }
    private class FakeMemoryRepository : MemoryRepository {
        override fun mutate(intent: MemoryIntent, fingerprint: String): MemoryMutationResult = error("unused")
        override fun findById(id: MemoryId): MemorySnapshot? = null
        override fun list(scope: MemoryScopeKind?, status: MemoryStatus?, search: String): List<MemorySnapshot> = emptyList()
        override fun conflict(id: MemoryConflictId): MemoryConflict? = null
    }
}
