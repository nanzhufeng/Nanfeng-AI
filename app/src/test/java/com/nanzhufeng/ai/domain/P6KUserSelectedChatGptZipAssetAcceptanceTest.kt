package com.nanzhufeng.ai.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in acceptance for a user-selected official export. It reads only archive structure and
 * source ownership fields, prints no title/body/filename, and touches no device.
 */
class P6KUserSelectedChatGptZipAssetAcceptanceTest {
    @Test
    fun `selected official export maps every source-owned current-path asset without guessing`() {
        val archive = File(selectedPath("nanfeng.ai.p6k.newZip", "NANFENG_AI_P6K_NEW_ZIP"))
        assumeTrue("requires an explicitly selected newer ChatGPT ZIP", archive.isFile)
        val inventory = ThirdPartyZipInventoryPolicy.inspect(archive) as ThirdPartyZipInventoryResult.InventoriedUnsupported
        val conversationEntries = inventory.entries.filter { entry -> entry.name.matches(Regex("^conversations(?:[-_]\\d+)?\\.json$")) }
        val textMapping = P6KChatGptZipCandidateMapper().map(ThirdPartyZipProvider.CHATGPT, archive, conversationEntries) as P6KChatGptZipMappingResult.Mapped
        val candidates = inventory.entries
            .filterNot { entry -> entry.name == "conversations.json" || entry.name.matches(Regex("^conversations(?:[-_]\\d+)?\\.json$")) || entry.mimeType == "application/json" }
            .map { entry -> P6KZipAssetCandidate(entry.name, "0".repeat(64), entry.uncompressedBytes, entry.mimeType) }
        val mapping = (P6KChatGptZipAssetMapper().map(archive, candidates) as P6KZipAssetMappingResult.Mapped).value
        val referenced = mapping.conversations.flatMap { conversation -> conversation.currentPath.flatMap(P6KZipSourceMessageAssets::entryNames) }.toSet()
        val occurrences = mapping.conversations.flatMap { conversation ->
            conversation.currentPath.flatMap { message ->
                message.entryNames.map { entryName -> Triple(conversation.sourceConversationId, message.sourceMessageId, entryName) }
            }
        }.toSet()
        val sourceReferenceRecords = mapping.conversations.sumOf { conversation -> conversation.currentPath.sumOf { it.sourceReferenceRecords } }
        val importedSourceIds = textMapping.items.mapNotNull { item -> item.candidate?.sourceConversationId }.toSet()
        val referencedByImportedConversations = mapping.conversations
            .filter { conversation -> conversation.sourceConversationId in importedSourceIds }
            .flatMap { conversation -> conversation.currentPath.flatMap(P6KZipSourceMessageAssets::entryNames) }
            .toSet()

        assertEquals(855, sourceReferenceRecords)
        assertEquals(855, occurrences.size)
        assertEquals(1, referenced.minus(mapping.assets.keys).size)
        assertEquals(853, mapping.assets.size)
        assertEquals(822, candidates.size - mapping.assets.size)
        assertEquals(2, mapping.fallbackNamedEntries.size)
        assertEquals(0, mapping.assets.keys.minus(referencedByImportedConversations).size)
        assertTrue(mapping.assets.values.any { it.candidate.mimeType.startsWith("image/") })
        assertTrue(mapping.assets.values.any { it.candidate.mimeType.startsWith("video/") })
        assertTrue(mapping.assets.values.any { it.candidate.mimeType.startsWith("audio/") })
        assertTrue(mapping.assets.values.any { it.candidate.mimeType == "application/pdf" || it.candidate.mimeType.startsWith("text/") })
        assertTrue(mapping.assets.values.all { mapped -> mapped.candidate.sourceConversationId != null && mapped.candidate.sourceMessageId != null })
    }

    private fun selectedPath(property: String, environment: String): String =
        System.getProperty(property).orEmpty().ifBlank { System.getenv(environment).orEmpty() }
}
