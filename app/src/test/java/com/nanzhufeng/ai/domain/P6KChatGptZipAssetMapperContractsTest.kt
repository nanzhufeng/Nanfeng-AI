package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class P6KChatGptZipAssetMapperContractsTest {
    @Test
    fun `official file-name map and message attachment id create one exact source binding`() {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1)
        val candidate = P6KZipAssetCandidate("file_fixture.dat", image.sha256ForTest(), image.size.toLong(), "application/octet-stream")
        val archive = File.createTempFile("p6k-asset-map-", ".zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.entry("conversation_asset_file_names.json", """{"file_fixture.dat":"原始截图.png"}""".toByteArray())
                zip.entry(
                    "conversations-000.json",
                    """[{"id":"conversation","create_time":1,"current_node":"assistant","mapping":{"user":{"parent":null,"message":{"author":{"role":"user"},"create_time":1,"content":{"parts":[{"content_type":"image_asset_pointer","asset_pointer":"sediment://file_fixture"}]},"metadata":{"attachments":[{"id":"file_fixture"}]}}},"assistant":{"parent":"user","message":{"author":{"role":"assistant"},"create_time":2,"content":{"parts":["已收到"]},"metadata":{}}}}}]""".toByteArray(),
                )
                zip.entry("file_fixture.dat", image)
            }

            val result = P6KChatGptZipAssetMapper().map(archive, listOf(candidate)) as P6KZipAssetMappingResult.Mapped
            val mapped = result.value.assets.getValue("file_fixture.dat")
            assertEquals("原始截图.png", mapped.displayName)
            assertEquals("image/png", mapped.candidate.mimeType)
            assertEquals("conversation", mapped.candidate.sourceConversationId)
            assertEquals("user", mapped.candidate.sourceMessageId)
            assertEquals(listOf("user", "assistant"), result.value.conversations.single().currentPath.map { it.sourceMessageId })
            assertTrue(!result.value.conversations.single().currentPath.first().hasSafeText)
            assertEquals(1, result.value.conversations.single().currentPath.first().sourceReferenceRecords)
            assertEquals(listOf("file_fixture.dat"), result.value.conversations.single().currentPath.first().entryNames)
            assertTrue(result.value.fallbackNamedEntries.isEmpty())
        } finally {
            archive.delete()
        }
    }

    @Test
    fun `official reference missing from archive remains visible without becoming a mapped asset`() {
        val bytes = byteArrayOf(1, 2, 3)
        val candidate = P6KZipAssetCandidate("file_present.dat", bytes.sha256ForTest(), bytes.size.toLong(), "application/octet-stream")
        val archive = File.createTempFile("p6k-missing-asset-map-", ".zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.entry(
                    "conversations-000.json",
                    """[{"id":"conversation","create_time":1,"current_node":"message","mapping":{"message":{"parent":null,"message":{"author":{"role":"user"},"create_time":1,"content":{"parts":[]},"metadata":{"attachments":[{"id":"file_present"},{"id":"file_missing"}]}}}}}]""".toByteArray(),
                )
                zip.entry("file_present.dat", bytes)
            }

            val mapping = (P6KChatGptZipAssetMapper().map(archive, listOf(candidate)) as P6KZipAssetMappingResult.Mapped).value
            val message = mapping.conversations.single().currentPath.single()
            assertEquals(listOf("file_present.dat", "file_missing.dat"), message.entryNames)
            assertEquals(2, message.sourceReferenceRecords)
            assertEquals(setOf("file_present.dat"), mapping.assets.keys)
            assertEquals(setOf("file_present.dat"), mapping.fallbackNamedEntries)
        } finally {
            archive.delete()
        }
    }

    @Test
    fun `one official file id referenced by multiple messages keeps every exact occurrence`() {
        val bytes = byteArrayOf(1, 2, 3)
        val candidate = P6KZipAssetCandidate("file_shared.dat", bytes.sha256ForTest(), bytes.size.toLong(), "application/octet-stream")
        val archive = File.createTempFile("p6k-shared-asset-map-", ".zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.entry(
                    "conversations-000.json",
                    """[{"id":"conversation","create_time":1,"current_node":"second","mapping":{"first":{"parent":null,"message":{"author":{"role":"user"},"create_time":1,"content":{"parts":[]},"metadata":{"attachments":[{"id":"file_shared"}]} }},"second":{"parent":"first","message":{"author":{"role":"assistant"},"create_time":2,"content":{"parts":[]},"metadata":{"attachments":[{"id":"file_shared"}]}}}}}]""".toByteArray(),
                )
                zip.entry("file_shared.dat", bytes)
            }

            val result = P6KChatGptZipAssetMapper().map(archive, listOf(candidate)) as P6KZipAssetMappingResult.Mapped
            assertEquals(listOf(listOf("file_shared.dat"), listOf("file_shared.dat")), result.value.conversations.single().currentPath.map { it.entryNames })
            assertEquals("first", result.value.assets.getValue("file_shared.dat").candidate.sourceMessageId)
        } finally {
            archive.delete()
        }
    }

    @Test
    fun `library image-gen original is restored to the bounded current-path assistant record`() {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1)
        val candidate = P6KZipAssetCandidate("file_generated.dat", image.sha256ForTest(), image.size.toLong(), "application/octet-stream")
        val archive = File.createTempFile("p6k-generated-asset-map-", ".zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.entry(
                    "library_files.json",
                    """[{"file_id":"file_generated","file_name":"generated.png","created_at":"1970-01-01T00:01:45Z","image_gen_generation_id":"generation"}]""".toByteArray(),
                )
                zip.entry(
                    "conversations-000.json",
                    """[{"id":"conversation","create_time":1,"current_node":"assistant","mapping":{"user":{"parent":null,"message":{"author":{"role":"user"},"create_time":90,"content":{"content_type":"text","parts":["画一张图"]},"metadata":{}}},"assistant":{"parent":"user","message":{"author":{"role":"assistant"},"create_time":100,"content":{"content_type":"reasoning_recap","parts":["已生成"]},"metadata":{}}}}}]""".toByteArray(),
                )
                zip.entry("file_generated.dat", image)
            }

            val mapping = (P6KChatGptZipAssetMapper().map(archive, listOf(candidate)) as P6KZipAssetMappingResult.Mapped).value
            assertEquals(setOf("file_generated.dat"), mapping.inferredGeneratedImageEntries)
            assertEquals("generated.png", mapping.assets.getValue("file_generated.dat").displayName)
            assertEquals("assistant", mapping.assets.getValue("file_generated.dat").candidate.sourceMessageId)
            val assistant = mapping.conversations.single().currentPath.last()
            assertEquals(listOf("file_generated.dat"), assistant.entryNames)
            assertEquals(1, assistant.sourceReferenceRecords)
        } finally {
            archive.delete()
        }
    }

    @Test
    fun `library image-gen original outside bounded message windows stays unattributed`() {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1)
        val candidate = P6KZipAssetCandidate("file_unbounded.dat", image.sha256ForTest(), image.size.toLong(), "application/octet-stream")
        val archive = File.createTempFile("p6k-unbounded-asset-map-", ".zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.entry(
                    "library_files.json",
                    """[{"file_id":"file_unbounded","file_name":"unbounded.png","created_at":"1970-01-01T00:10:00Z","image_gen_generation_id":"generation"}]""".toByteArray(),
                )
                zip.entry(
                    "conversations-000.json",
                    """[{"id":"conversation","create_time":1,"current_node":"assistant","mapping":{"assistant":{"parent":null,"message":{"author":{"role":"assistant"},"create_time":100,"content":{"content_type":"reasoning_recap","parts":["较早消息"]},"metadata":{}}}}}]""".toByteArray(),
                )
                zip.entry("file_unbounded.dat", image)
            }

            val mapping = (P6KChatGptZipAssetMapper().map(archive, listOf(candidate)) as P6KZipAssetMappingResult.Mapped).value
            assertTrue(mapping.inferredGeneratedImageEntries.isEmpty())
            assertTrue(mapping.assets.isEmpty())
            assertTrue(mapping.conversations.single().currentPath.single().entryNames.isEmpty())
        } finally {
            archive.delete()
        }
    }

    @Test
    fun `library image with official origin thread and message restores by exact ids`() {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1)
        val candidate = P6KZipAssetCandidate("file_origin.dat", image.sha256ForTest(), image.size.toLong(), "application/octet-stream")
        val archive = File.createTempFile("p6k-origin-image-map-", ".zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.entry(
                    "library_files.json",
                    """[{"file_id":"file_origin","file_name":"origin.png","mime_type":"image/png","created_at":"1970-01-01T00:10:00Z","origination_thread_id":"conversation","origination_message_id":"assistant"}]""".toByteArray(),
                )
                zip.entry(
                    "conversations-000.json",
                    """[{"id":"conversation","create_time":1,"current_node":"assistant","mapping":{"assistant":{"parent":null,"message":{"author":{"role":"assistant"},"create_time":100,"content":{"content_type":"text","parts":["可见消息"]},"metadata":{}}}}}]""".toByteArray(),
                )
                zip.entry("file_origin.dat", image)
            }

            val mapping = (P6KChatGptZipAssetMapper().map(archive, listOf(candidate)) as P6KZipAssetMappingResult.Mapped).value
            assertEquals(setOf("file_origin.dat"), mapping.originLinkedLibraryImageEntries)
            assertTrue(mapping.inferredLibraryImageEntries.isEmpty())
            assertEquals("assistant", mapping.assets.getValue("file_origin.dat").candidate.sourceMessageId)
        } finally {
            archive.delete()
        }
    }

    @Test
    fun `anonymous library image stays unattributed when two conversations are similarly near`() {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1)
        val candidate = P6KZipAssetCandidate("file_ambiguous.dat", image.sha256ForTest(), image.size.toLong(), "application/octet-stream")
        val archive = File.createTempFile("p6k-ambiguous-image-map-", ".zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.entry(
                    "library_files.json",
                    """[{"file_id":"file_ambiguous","file_name":"ambiguous.png","mime_type":"image/png","created_at":"1970-01-01T00:10:00Z"}]""".toByteArray(),
                )
                zip.entry(
                    "conversations-000.json",
                    """[{"id":"first","create_time":1,"current_node":"one","mapping":{"one":{"parent":null,"message":{"author":{"role":"assistant"},"create_time":500,"content":{"content_type":"text","parts":["一"]},"metadata":{}}}}},{"id":"second","create_time":1,"current_node":"two","mapping":{"two":{"parent":null,"message":{"author":{"role":"assistant"},"create_time":750,"content":{"content_type":"text","parts":["二"]},"metadata":{}}}}}]""".toByteArray(),
                )
                zip.entry("file_ambiguous.dat", image)
            }

            val mapping = (P6KChatGptZipAssetMapper().map(archive, listOf(candidate)) as P6KZipAssetMappingResult.Mapped).value
            assertTrue(mapping.inferredLibraryImageEntries.isEmpty())
            assertTrue(mapping.assets.isEmpty())
        } finally {
            archive.delete()
        }
    }

    private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name)); write(bytes); closeEntry()
    }
}

private fun ByteArray.sha256ForTest(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(this).joinToString("") { "%02x".format(it) }
