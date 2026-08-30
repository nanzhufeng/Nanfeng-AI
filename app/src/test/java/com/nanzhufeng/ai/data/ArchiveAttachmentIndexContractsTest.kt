package com.nanzhufeng.ai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.domain.AttachmentArchiveIndexResult
import com.nanzhufeng.ai.domain.AttachmentArchiveEntryReadResult
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArchiveAttachmentIndexContractsTest {
    @Test fun `archive backed ZIP lists bounded inert entries with names types and sizes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nestedZip = zipBytes(
            "docs/说明。 md" to "# 说明".toByteArray(),
            "images/example.png" to byteArrayOf(1, 2, 3),
            "nested.zip" to zipBytes("inside/readme.txt" to "nested text".toByteArray()),
            "../escape.txt" to "blocked".toByteArray(),
        )
        val taskId = "archive-index-fixture"
        val entryName = "nested_archive.dat"
        val outerArchive = File(context.filesDir, "p6k-zip-import/v1/archives/$taskId.zip")
        outerArchive.parentFile?.mkdirs()
        ZipOutputStream(outerArchive.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(entryName))
            output.write(nestedZip)
            output.closeEntry()
        }
        try {
            val hash = MessageDigest.getInstance("SHA-256").digest(nestedZip).joinToString("") { "%02x".format(it) }
            val reference = AttachmentReference(
                reference = P6KZipArchiveAssetStorage.key(taskId, entryName),
                mimeType = "application/octet-stream",
                displayName = "开发文档。 zip",
                id = AttachmentId.new(),
                byteCount = nestedZip.size.toLong(),
                sha256 = hash,
            )

            val result = AndroidPrivateAttachmentStore(context).archiveIndex(reference) as AttachmentArchiveIndexResult.Ready

            assertEquals(4, result.index.totalEntryCount)
            assertTrue(result.index.entries.single { it.path == "docs" }.isDirectory)
            assertTrue(result.index.entries.single { it.path == "images" }.isDirectory)
            assertTrue(result.index.entries.none { it.path.contains("escape") })
            assertTrue(!result.index.truncated)

            val docs = AndroidPrivateAttachmentStore(context).archiveIndex(reference, emptyList(), listOf("docs")) as AttachmentArchiveIndexResult.Ready
            assertEquals("text/markdown", docs.index.entries.single().mimeType)
            assertEquals("docs/说明。 md", docs.index.entries.single().path)
            val images = AndroidPrivateAttachmentStore(context).archiveIndex(reference, emptyList(), listOf("images")) as AttachmentArchiveIndexResult.Ready
            assertEquals(3L, images.index.entries.single().byteCount)
            assertEquals("images/example.png", images.index.entries.single().path)

            val markdown = AndroidPrivateAttachmentStore(context).archiveEntry(reference, emptyList(), "docs/说明。 md") as AttachmentArchiveEntryReadResult.Content
            assertEquals("# 说明", markdown.entry.bytes.toString(Charsets.UTF_8))

            val nested = AndroidPrivateAttachmentStore(context).archiveIndex(reference, listOf("nested.zip")) as AttachmentArchiveIndexResult.Ready
            assertEquals("inside", nested.index.entries.single().path)
            assertTrue(nested.index.entries.single().isDirectory)
            val nestedInside = AndroidPrivateAttachmentStore(context).archiveIndex(reference, listOf("nested.zip"), listOf("inside")) as AttachmentArchiveIndexResult.Ready
            assertEquals("inside/readme.txt", nestedInside.index.entries.single().path)
        } finally {
            outerArchive.delete()
        }
    }

    @Test fun `a single export wrapper folder opens directly to its clickable contents`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val taskId = "archive-index-single-root"
        val bytes = zipBytes(
            "南枫知识库开发文档-Mac复用-20260803/README.md" to "# readme".toByteArray(),
            "南枫知识库开发文档-Mac复用-20260803/docs/guide.txt" to "guide".toByteArray(),
        )
        val archive = File(context.filesDir, "p6k-zip-import/v1/archives/$taskId.zip")
        archive.parentFile?.mkdirs()
        ZipOutputStream(archive.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("wrapped_archive.dat"))
            output.write(bytes)
            output.closeEntry()
        }
        try {
            val reference = AttachmentReference(
                reference = P6KZipArchiveAssetStorage.key(taskId, "wrapped_archive.dat"),
                mimeType = "application/octet-stream",
                displayName = "开发文档.zip",
                id = AttachmentId.new(),
                byteCount = bytes.size.toLong(),
                sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            )
            val index = AndroidPrivateAttachmentStore(context).archiveIndex(reference) as AttachmentArchiveIndexResult.Ready

            assertEquals(
                listOf("南枫知识库开发文档-Mac复用-20260803/docs", "南枫知识库开发文档-Mac复用-20260803/README.md"),
                index.index.entries.map { it.path },
            )
            assertTrue(index.index.entries.first().isDirectory)
            val readme = AndroidPrivateAttachmentStore(context).archiveEntry(
                reference,
                emptyList(),
                "南枫知识库开发文档-Mac复用-20260803/README.md",
            ) as AttachmentArchiveEntryReadResult.Content
            assertEquals("# readme", readme.entry.bytes.toString(Charsets.UTF_8))
        } finally {
            archive.delete()
        }
    }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { output ->
            entries.forEach { (name, value) ->
                output.putNextEntry(ZipEntry(name))
                output.write(value)
                output.closeEntry()
            }
        }
        bytes.toByteArray()
    }
}
