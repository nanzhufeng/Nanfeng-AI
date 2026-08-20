package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class P6JNanfengKnowledgeExportJsonContractsTest {
    @Test fun `knowledge export retains only visible static conversation text`() {
        val bytes = requireNotNull(javaClass.classLoader?.getResourceAsStream("p6j-nanfeng-knowledge-export.json")) { "fixture missing" }.readBytes()
        val parsed = NanfengKnowledgeExportJsonAdapter().parse(bytes) as NanfengKnowledgeExportParseResult.Parsed
        val candidate = requireNotNull(parsed.items.first().candidate)
        assertEquals("knowledge-record-101", candidate.sourceConversationId)
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), candidate.messages.map { it.role })
        assertEquals("P6J Alpha user text", candidate.messages[0].text)
        assertEquals("P6J Alpha assistant text", candidate.messages[1].text)
        assertFalse(candidate.messages.joinToString(" ") { it.text }.contains("must not import"))
        assertFalse(candidate.messages.joinToString(" ") { it.text }.contains("tool directive"))
        assertEquals(NanfengKnowledgeExportParseFailure.NOT_CONVERSATION_RECORD, parsed.items[1].failure)
    }

    @Test fun `package errors reject while a non conversation record stays item local`() {
        assertEquals(
            NanfengKnowledgeExportParseFailure.ROOT_NOT_ARRAY,
            (NanfengKnowledgeExportJsonAdapter().parse("{}".toByteArray()) as NanfengKnowledgeExportParseResult.Rejected).failure,
        )
        val duplicate = "[{\"id\":1,\"id\":2}]".toByteArray()
        assertEquals(
            NanfengKnowledgeExportParseFailure.DUPLICATE_KEY,
            (NanfengKnowledgeExportJsonAdapter().parse(duplicate) as NanfengKnowledgeExportParseResult.Rejected).failure,
        )
        assertTrue(NANFENG_KNOWLEDGE_EXPORT_MAX_BYTES < 64L * 1024 * 1024)
    }

    @Test fun `acceptance fixture supplies two safe static conversations for confirm and skip`() {
        val bytes = requireNotNull(javaClass.classLoader?.getResourceAsStream("p6j-nanfeng-knowledge-export-acceptance.json")).readBytes()
        val parsed = NanfengKnowledgeExportJsonAdapter().parse(bytes) as NanfengKnowledgeExportParseResult.Parsed
        assertEquals(listOf("P6J Acceptance Alpha", "P6J Acceptance Skip"), parsed.items.map { requireNotNull(it.candidate).title })
        assertFalse(parsed.items.first().candidate!!.messages.joinToString(" ") { it.text }.contains("must not import"))
    }

    @Test fun `private task confirms only the valid record through its atomic commit port`() {
        val tasks = mutableMapOf<NanfengKnowledgeImportTaskId, NanfengKnowledgeImportTask>()
        val bytes = requireNotNull(javaClass.classLoader?.getResourceAsStream("p6j-nanfeng-knowledge-export.json")).readBytes()
        val assetStore = object : NanfengKnowledgePrivateAssetStore {
            override fun copy(request: NanfengKnowledgePrivateCopyRequest) = NanfengKnowledgePrivateCopyResult.Copied(NanfengKnowledgeImportAsset("p6j/${request.taskId.value}.json", request.mimeType, request.displayName, request.bytes.size.toLong(), "package-hash"))
            override fun read(storageKey: String) = bytes
        }
        val commits = object : NanfengKnowledgeConversationCommitStore {
            var calls = 0
            override fun commit(candidate: NanfengKnowledgeExportCandidate, packageHash: String, importedAt: Instant): NanfengKnowledgeImportCommitResult { calls++; return NanfengKnowledgeImportCommitResult.Created(ConversationId("p6j-conversation")) }
        }
        val useCase = ManageNanfengKnowledgeExportImportUseCase(
            tasks = object : NanfengKnowledgeImportTaskRepository { override fun save(task: NanfengKnowledgeImportTask) = task.also { tasks[it.id] = it }; override fun find(id: NanfengKnowledgeImportTaskId) = tasks[id]; override fun list() = tasks.values.toList() },
            assets = assetStore, adapter = NanfengKnowledgeExportJsonAdapter(), commits = commits,
            clock = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC),
        )
        val staged = useCase.select("knowledge.json", "application/json", bytes)
        assertEquals(NanfengKnowledgeImportTaskStatus.PARTIALLY_COMPLETED, staged.status)
        assertEquals(NanfengKnowledgeImportItemStatus.FAILED, staged.items[1].status)
        assertEquals(NanfengKnowledgeImportItemStatus.CONFIRMED, staged.items.first().status)
        assertEquals(1, commits.calls)
    }
}
