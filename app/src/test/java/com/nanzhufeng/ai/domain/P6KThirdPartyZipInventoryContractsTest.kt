package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class P6KThirdPartyZipInventoryContractsTest {
    private val conversations = """[{"id":"c-1","title":"Imported","create_time":1700000000,"update_time":1700000001,"mapping":{"m-1":{"parent":null,"children":["m-2"],"message":{"author":{"role":"user"},"content":{"parts":["hello"]},"create_time":1700000000}},"m-2":{"parent":"m-1","children":[],"message":{"author":{"role":"assistant"},"content":{"parts":["world"]},"create_time":1700000001}}}}]"""
    @Test fun `safe synthetic zip is inventoried but cannot become an import candidate without a registered manifest`() {
        val file = fixture("manifest.json" to "{}", "assets/image.png" to "pixels"); try {
            val result = ThirdPartyZipInventoryPolicy.inspect(file) as ThirdPartyZipInventoryResult.InventoriedUnsupported
            assertEquals(listOf("manifest.json", "assets/image.png"), result.entries.map { it.name })
            assertEquals("application/json", result.entries.first().mimeType)
        } finally { file.delete() }
    }
    @Test fun `path traversal duplicate entries and compressed bombs fail closed`() {
        val traversal = fixture("../escape.json" to "{}"); try { assertEquals(ThirdPartyZipInventoryFailure.UNSAFE_PATH, (ThirdPartyZipInventoryPolicy.inspect(traversal) as ThirdPartyZipInventoryResult.Rejected).failure) } finally { traversal.delete() }
        val duplicate = fixture("A.json" to "{}", "a.json" to "{}"); try { assertEquals(ThirdPartyZipInventoryFailure.DUPLICATE_ENTRY, (ThirdPartyZipInventoryPolicy.inspect(duplicate) as ThirdPartyZipInventoryResult.Rejected).failure) } finally { duplicate.delete() }
        val bomb = fixture("large.txt" to "x".repeat(2_000_000)); try { assertEquals(ThirdPartyZipInventoryFailure.COMPRESSION_BOMB, (ThirdPartyZipInventoryPolicy.inspect(bomb) as ThirdPartyZipInventoryResult.Rejected).failure) } finally { bomb.delete() }
    }
    @Test fun `registered ChatGPT root conversations JSON only becomes inert candidates and media stays unowned`() {
        val file = fixture("conversations.json" to conversations, "assets/image.png" to "pixels")
        try {
            val inventory = ThirdPartyZipInventoryPolicy.inspect(file) as ThirdPartyZipInventoryResult.InventoriedUnsupported
            val result = P6KChatGptZipCandidateMapper().map(ThirdPartyZipProvider.CHATGPT, file, inventory.entries) as P6KChatGptZipMappingResult.Mapped
            assertEquals(P6K_CHATGPT_ZIP_FORMAT_VERSION, result.formatVersion)
            assertEquals("c-1", result.items.single().candidate!!.sourceConversationId)
            assertEquals(P6KZipAssetRole.UNMAPPED_REJECTED, result.assets.single().role)
            assertEquals(null, result.assets.single().sourceConversationId)
            assertTrue(result.assets.single().sha256.matches(Regex("[0-9a-f]{64}")))
        } finally { file.delete() }
    }
    @Test fun `official numbered ChatGPT conversation JSON files aggregate only through the same strict parser`() {
        val second = conversations.replace("c-1", "c-2").replace("m-1", "n-1").replace("m-2", "n-2")
        val file = fixture("conversations-000001.json" to conversations, "conversations-000002.json" to second)
        try {
            val inventory = ThirdPartyZipInventoryPolicy.inspect(file) as ThirdPartyZipInventoryResult.InventoriedUnsupported
            val result = P6KChatGptZipCandidateMapper().map(ThirdPartyZipProvider.CHATGPT, file, inventory.entries) as P6KChatGptZipMappingResult.Mapped
            assertEquals(P6K_CHATGPT_NUMBERED_ZIP_FORMAT_VERSION, result.formatVersion)
            assertEquals(listOf("c-1", "c-2"), result.items.map { it.candidate!!.sourceConversationId })
        } finally { file.delete() }
    }
    @Test fun `Claude uses its own strict root conversation parser and malformed data fails closed`() {
        val noRoot = fixture("assets/image.png" to "pixels")
        val malformed = fixture("conversations.json" to "not-json")
        try {
            val mapper = P6KChatGptZipCandidateMapper()
            val noRootInventory = ThirdPartyZipInventoryPolicy.inspect(noRoot) as ThirdPartyZipInventoryResult.InventoriedUnsupported
            assertEquals(P6KZipCandidateFailure.MISSING_CONVERSATIONS_JSON, (mapper.map(ThirdPartyZipProvider.CHATGPT, noRoot, noRootInventory.entries) as P6KChatGptZipMappingResult.Waiting).failure)
            val malformedInventory = ThirdPartyZipInventoryPolicy.inspect(malformed) as ThirdPartyZipInventoryResult.InventoriedUnsupported
            assertEquals(P6KZipCandidateFailure.CHATGPT_CONVERSATIONS_NOT_STRICT, (mapper.map(ThirdPartyZipProvider.CHATGPT, malformed, malformedInventory.entries) as P6KChatGptZipMappingResult.Rejected).failure)
            assertEquals(P6KZipCandidateFailure.CLAUDE_CONVERSATIONS_NOT_STRICT, (mapper.map(ThirdPartyZipProvider.CLAUDE, malformed, malformedInventory.entries) as P6KChatGptZipMappingResult.Rejected).failure)
            val claude = fixture("conversations.json" to """[{"uuid":"00000000-0000-0000-0000-000000000001","name":"Claude","created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:01Z","chat_messages":[{"uuid":"00000000-0000-0000-0000-000000000011","parent_message_uuid":null,"sender":"human","text":"hello","created_at":"2026-01-01T00:00:00Z"}]}]""")
            try {
                val claudeInventory = ThirdPartyZipInventoryPolicy.inspect(claude) as ThirdPartyZipInventoryResult.InventoriedUnsupported
                val mapped = mapper.map(ThirdPartyZipProvider.CLAUDE, claude, claudeInventory.entries) as P6KChatGptZipMappingResult.Mapped
                assertEquals(P6K_CLAUDE_ZIP_FORMAT_VERSION, mapped.formatVersion)
                assertEquals("00000000-0000-0000-0000-000000000001", mapped.items.single().candidate!!.sourceConversationId)
            } finally { claude.delete() }
        } finally { noRoot.delete(); malformed.delete() }
    }
    private fun fixture(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("p6k-", ".zip")
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } }
        return file
    }
}
