package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P6HChatGptExportJsonContractsTest {
    private val adapter = ChatGptExportJsonAdapter()
    private fun source(extra: String = "") = """[{"id":"c-1","title":"Imported","create_time":1700000000,"update_time":1700000001,"mapping":{"m-1":{"parent":null,"children":["m-2"],"message":{"author":{"role":"user"},"content":{"parts":["hello"]},"create_time":1700000000}},"m-2":{"parent":"m-1","children":[],"message":{"author":{"role":"assistant"},"content":{"parts":["world"]},"metadata":{"model_slug":"gpt-safe"},"create_time":1700000001}}}$extra}]"""

    @Test fun `strict parser retains bounded tree role text time and imported model only`() {
        val result = adapter.parse(source().toByteArray()) as ChatGptParseResult.Parsed
        val candidate = requireNotNull(result.items.single().candidate)
        assertEquals("c-1", candidate.sourceConversationId); assertEquals(2, candidate.messages.size)
        assertEquals(MessageRole.USER, candidate.messages[0].role); assertEquals("gpt-safe", candidate.messages[1].importedModel)
    }
    @Test fun `duplicate malformed and unsafe tree facts fail closed`() {
        assertEquals(ChatGptImportFailure.DUPLICATE_KEY, rejected(source().replace("\"id\":\"c-1\"", "\"id\":\"c-1\",\"id\":\"c-1\"")))
        val invalidTree = adapter.parse(source().replace("\"parent\":\"m-1\"", "\"parent\":\"absent\"").toByteArray()) as ChatGptParseResult.Parsed
        assertEquals(ChatGptImportFailure.INVALID_TREE, invalidTree.items.single().failure)
        val unsupported = adapter.parse(source().replace("\"assistant\"", "\"system\"").toByteArray()) as ChatGptParseResult.Parsed
        assertEquals(ChatGptImportFailure.UNSUPPORTED_ROLE, unsupported.items.single().failure)
        assertEquals(ChatGptImportFailure.MALFORMED_JSON, rejected("["))
    }
    @Test fun `unknown top level fields are inert while only supported message fields become candidates`() {
        val parsed = adapter.parse(source().replace("\"title\":\"Imported\"", "\"title\":\"Imported\",\"unknown\":{\"tool\":\"ignore\"}" ).toByteArray()) as ChatGptParseResult.Parsed
        assertTrue(parsed.items.single().candidate!!.messages.all { it.text.isNotBlank() })
    }
    @Test fun `raw ZIP bytes are not a supported JSON export substitute`() {
        assertEquals(ChatGptImportFailure.MALFORMED_JSON, rejected("PK\u0003\u0004conversations.json"))
    }
    private fun rejected(value: String): ChatGptImportFailure = (adapter.parse(value.toByteArray()) as ChatGptParseResult.Rejected).failure
}
