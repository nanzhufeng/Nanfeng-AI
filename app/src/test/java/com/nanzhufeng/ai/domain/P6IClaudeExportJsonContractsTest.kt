package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class P6IClaudeExportJsonContractsTest {
    private val adapter = ClaudeExportJsonAdapter()
    private fun source(messages: String = """[
        {"uuid":"11111111-1111-1111-1111-111111111111","sender":"human","created_at":"2026-08-15T00:00:00Z","text":"hello","parent_message_uuid":null},
        {"uuid":"22222222-2222-2222-2222-222222222222","sender":"assistant","created_at":"2026-08-15T00:00:01Z","content":"world","parent_message_uuid":"11111111-1111-1111-1111-111111111111"}
    ]""") = """[{"uuid":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","name":"Imported","created_at":"2026-08-15T00:00:00Z","updated_at":"2026-08-15T00:00:01Z","chat_messages":$messages,"unknown":{"tool":"inert"}}]"""

    @Test fun `strict parser retains source tree roles content and timestamps as inert text`() {
        val parsed = adapter.parse(source().toByteArray()) as ClaudeExportParseResult.Parsed
        val candidate = requireNotNull(parsed.items.single().candidate)
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", candidate.sourceConversationId)
        assertEquals(2, candidate.messages.size)
        assertEquals(MessageRole.USER, candidate.messages[0].role)
        assertEquals(0, candidate.messages[0].siblingPosition)
        assertEquals("11111111-1111-1111-1111-111111111111", candidate.messages[1].parentSourceId)
        assertEquals(MessageRole.ASSISTANT, candidate.messages[1].role)
        assertEquals("world", candidate.messages[1].text)
        assertNotNull(candidate.contentHash)
        val nullTextWithContent = adapter.parse(source().replace("\"content\":\"world\"", "\"text\":null,\"content\":\"world\"").toByteArray()) as ClaudeExportParseResult.Parsed
        assertEquals("world", nullTextWithContent.items.single().candidate!!.messages[1].text)
    }

    @Test fun `invalid package facts reject while unsafe conversations remain item failures`() {
        assertEquals(ClaudeExportParseFailure.DUPLICATE_KEY, rejected(source().replace("\"name\":\"Imported\"", "\"name\":\"Imported\",\"name\":\"Again\"")))
        assertEquals(ClaudeExportParseFailure.MALFORMED_JSON, rejected("["))
        assertEquals(ClaudeExportParseFailure.ROOT_NOT_ARRAY, rejected("{}"))
        val invalidTree = adapter.parse(source().replace("\"parent_message_uuid\":\"11111111-1111-1111-1111-111111111111\"", "\"parent_message_uuid\":\"33333333-3333-3333-3333-333333333333\"").toByteArray()) as ClaudeExportParseResult.Parsed
        assertEquals(ClaudeExportParseFailure.INVALID_TREE, invalidTree.items.single().failure)
    }

    @Test fun `blank Claude title uses the same safe fallback as Desktop`() {
        val parsed = adapter.parse(source().replace("\"name\":\"Imported\"", "\"name\":\"   \"").toByteArray()) as ClaudeExportParseResult.Parsed
        assertEquals("未命名 Claude 对话", parsed.items.single().candidate!!.title)
    }

    @Test fun `unsupported roles nontext payloads and duplicate source ids fail closed per conversation`() {
        val unsupportedRole = adapter.parse(source().replace("\"assistant\"", "\"system\"").toByteArray()) as ClaudeExportParseResult.Parsed
        assertEquals(ClaudeExportParseFailure.UNSUPPORTED_ROLE, unsupportedRole.items.single().failure)
        val unsupportedContent = adapter.parse(source().replace("\"content\":\"world\"", "\"content\":{\"tool\":\"ignore\"}").toByteArray()) as ClaudeExportParseResult.Parsed
        assertEquals(ClaudeExportParseFailure.UNSUPPORTED_CONTENT, unsupportedContent.items.single().failure)
        val duplicate = adapter.parse(source().replace("22222222-2222-2222-2222-222222222222", "11111111-1111-1111-1111-111111111111").toByteArray()) as ClaudeExportParseResult.Parsed
        assertEquals(ClaudeExportParseFailure.DUPLICATE_SOURCE_ID, duplicate.items.single().failure)
    }

    @Test fun `raw ZIP bytes are not a supported JSON export substitute`() {
        assertEquals(ClaudeExportParseFailure.MALFORMED_JSON, rejected("PK\u0003\u0004conversations.json"))
    }

    @Test fun `task machine retains only private asset state and commits a confirmed candidate atomically`() {
        val repository = InMemoryTasks()
        val assets = InMemoryAssets()
        val useCase = ManageClaudeExportImportUseCase(repository, assets, adapter, object : ClaudeConversationCommitStore {
            override fun commit(candidate: ClaudeExportCandidate, packageHash: String, importedAt: Instant) = ClaudeImportCommitResult.Created(ConversationId("local-import"))
        }, Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC))
        val staged = useCase.select("conversations.json", "application/json", source().toByteArray())
        assertEquals(ClaudeImportTaskStatus.COMPLETED, staged.status)
        assertEquals(1, staged.items.size)
        assertEquals(ConversationId("local-import"), staged.items.single().conversationId)
        assertEquals(listOf("claude-import-assets/v1/${staged.id.value}"), assets.keys)
    }

    @Test fun `failed candidates prevent a task from being reported as fully completed`() {
        val repository = InMemoryTasks(); val assets = InMemoryAssets()
        val useCase = ManageClaudeExportImportUseCase(repository, assets, adapter, object : ClaudeConversationCommitStore {
            override fun commit(candidate: ClaudeExportCandidate, packageHash: String, importedAt: Instant) = ClaudeImportCommitResult.Created(ConversationId("local-import"))
        }, Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC))
        val mixed = source().dropLast(1) + ",{\"uuid\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\",\"name\":null,\"created_at\":\"bad\",\"updated_at\":\"2026-08-15T00:00:01Z\",\"chat_messages\":[]}]"
        val staged = useCase.select("conversations.json", "application/json", mixed.toByteArray())
        assertEquals(ClaudeImportTaskStatus.PARTIALLY_COMPLETED, staged.status)
    }

    private class InMemoryTasks : ClaudeImportTaskRepository {
        private val tasks = linkedMapOf<ClaudeImportTaskId, ClaudeImportTask>()
        override fun save(task: ClaudeImportTask): ClaudeImportTask = task.also { tasks[it.id] = it }
        override fun find(id: ClaudeImportTaskId): ClaudeImportTask? = tasks[id]
        override fun list(): List<ClaudeImportTask> = tasks.values.toList()
    }

    private class InMemoryAssets : ClaudePrivateAssetStore {
        val keys = mutableListOf<String>()
        private val bytes = mutableMapOf<String, ByteArray>()
        override fun copy(request: ClaudePrivateCopyRequest): ClaudePrivateCopyResult {
            val key = "claude-import-assets/v1/${request.taskId.value}"; keys += key; bytes[key] = request.bytes
            return ClaudePrivateCopyResult.Copied(ClaudeImportAsset(key, request.mimeType, request.displayName, request.bytes.size.toLong(), "safe-package-hash"))
        }
        override fun read(storageKey: String): ByteArray? = bytes[storageKey]
    }

    private fun rejected(source: String): ClaudeExportParseFailure =
        (adapter.parse(source.toByteArray()) as ClaudeExportParseResult.Rejected).failure
}
