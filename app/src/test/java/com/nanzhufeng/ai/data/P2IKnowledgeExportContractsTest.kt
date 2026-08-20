package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.data.InMemoryKnowledgeRepository
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.CandidateId
import com.nanzhufeng.ai.domain.CandidateProvenance
import com.nanzhufeng.ai.domain.CaptureSourceType
import com.nanzhufeng.ai.domain.ExportKnowledgePackageUseCase
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.KnowledgeExportFailure
import com.nanzhufeng.ai.domain.KnowledgeExportMapper
import com.nanzhufeng.ai.domain.KnowledgeExportResult
import com.nanzhufeng.ai.domain.KnowledgeItem
import com.nanzhufeng.ai.domain.KnowledgeItemId
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.SourceEvidence
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P2IKnowledgeExportContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-12T16:00:00Z"), ZoneOffset.UTC)
    private lateinit var root: File
    private lateinit var repository: InMemoryKnowledgeRepository
    private lateinit var store: AndroidKnowledgeExportStore
    private lateinit var export: ExportKnowledgePackageUseCase

    @Before fun setUp() {
        root = Files.createTempDirectory("p2i-export-").toFile()
        repository = InMemoryKnowledgeRepository()
        store = AndroidKnowledgeExportStore(root)
        export = ExportKnowledgePackageUseCase(repository, store, clock)
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    @Test fun `empty knowledge creates no file`() {
        assertEquals(KnowledgeExportResult.EmptyKnowledge, export.execute())
        assertNull(store.latestVerified())
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun `real package round trips unicode newlines sources and safe attachment metadata`() {
        val item = knowledge(
            id = "knowledge:中文",
            title = "标题 😀",
            body = "第一行\n第二行：南枫 AI",
            sourceEvidence = listOf(
                SourceEvidence(CaptureSourceType.MANUAL_TEXT, Instant.parse("2026-08-12T15:00:00Z"), "manual-text", setOf("text")),
                SourceEvidence(CaptureSourceType.ANDROID_TEXT_SHARE, Instant.parse("2026-08-12T15:01:00Z"), "content://must-not-export", setOf("text", "unsafe field")),
                SourceEvidence(CaptureSourceType.IMAGE, Instant.parse("2026-08-12T15:02:00Z"), "android-photo-picker", setOf("image")),
            ),
            attachment = readyAttachment(),
        )
        repository.save(item)

        val result = export.execute() as KnowledgeExportResult.Success
        val actual = result.export

        assertEquals(listOf(KnowledgeExportMapper.item(item)), actual.payload.items)
        assertEquals(1, actual.manifest.protocolVersion)
        assertEquals(1, actual.manifest.knowledgeCount)
        assertTrue(actual.sha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(actual.fileName.endsWith(".nfai"))
        assertEquals("exports/knowledge/v1/${actual.fileName}", actual.relativeLocation)
        val rebuilt = AndroidKnowledgeExportStore(root).latestVerified() as KnowledgeExportResult.Success
        assertEquals(actual.fileName, rebuilt.export.fileName)
        assertEquals(actual.payload, rebuilt.export.payload)
        val packageText = zipEntry(File(root, actual.fileName), "knowledge.json").decodeToString()
        assertFalse(packageText.contains("content://must-not-export"))
        assertFalse(packageText.contains("unsafe field"))
        assertFalse(packageText.contains("secret-photo.jpg"))
        assertFalse(packageText.contains("attachments/v1/secret-private-path"))
        assertTrue(packageText.contains("第一行\\n第二行：南枫 AI"))
    }

    @Test fun `multiple exports use confirmed knowledge only and never overwrite`() {
        val older = knowledge("knowledge:a", "较早", "正式 A", createdAt = Instant.parse("2026-08-12T14:00:00Z"))
        val newer = knowledge("knowledge:z", "较晚", "正式 Z", createdAt = Instant.parse("2026-08-12T15:00:00Z"))
        repository.save(older)
        repository.save(newer)

        val first = export.execute() as KnowledgeExportResult.Success
        val second = export.execute() as KnowledgeExportResult.Success

        assertFalse(first.export.fileName == second.export.fileName)
        assertEquals(listOf("knowledge:z", "knowledge:a"), first.export.payload.items.map { it.id })
        assertEquals(2, root.listFiles { file -> file.name.endsWith(".nfai") }!!.size)
        assertEquals(first.export.payload.items, second.export.payload.items)
    }

    @Test fun `cancelled export leaves no success package`() {
        repository.save(knowledge("knowledge:cancel", "取消", "正式知识"))

        assertEquals(KnowledgeExportResult.Cancelled, export.execute { true })
        assertNull(store.latestVerified())
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun `write failure does not leave package pretending to succeed`() {
        val blockedRoot = File(root, "not-a-directory").apply { writeText("file") }
        val blocked = ExportKnowledgePackageUseCase(repository.apply { save(knowledge("knowledge:fail", "失败", "正式知识")) }, AndroidKnowledgeExportStore(blockedRoot), clock)

        assertEquals(KnowledgeExportResult.Failed(KnowledgeExportFailure.OUTPUT_UNAVAILABLE), blocked.execute())
        assertFalse(blockedRoot.name.endsWith(".nfai"))
    }

    @Test fun `readback rejects corrupted payload hash and recreated store returns only verified fact`() {
        repository.save(knowledge("knowledge:hash", "哈希", "正式知识"))
        val success = export.execute() as KnowledgeExportResult.Success
        val file = File(root, success.export.fileName)
        val manifest = zipEntry(file, "manifest.json")
        rewritePackage(file, manifest, "{\"schema\":\"nanfeng-ai.knowledge-payload\",\"schemaVersion\":1,\"knowledge\":[]}".encodeToByteArray())

        assertEquals(KnowledgeExportResult.Failed(KnowledgeExportFailure.INTEGRITY_MISMATCH), store.verify(file))
        assertEquals(KnowledgeExportResult.Failed(KnowledgeExportFailure.READ_BACK_FAILED), AndroidKnowledgeExportStore(root).latestVerified())
    }

    private fun knowledge(
        id: String,
        title: String,
        body: String,
        sourceEvidence: List<SourceEvidence> = listOf(SourceEvidence(CaptureSourceType.MANUAL_TEXT, Instant.parse("2026-08-12T14:00:00Z"), "manual-text", setOf("text"))),
        attachment: AttachmentReference? = null,
        createdAt: Instant = Instant.parse("2026-08-12T14:00:00Z"),
    ) = KnowledgeItem(
        id = KnowledgeItemId(id),
        title = title,
        body = body,
        sourceEvidence = sourceEvidence,
        provenance = CandidateProvenance(CandidateId("candidate:$id"), InvocationId("invocation:$id"), ProviderId.MOCK, "mock-local-balanced-v1", 1),
        createdAt = createdAt,
        attachments = listOfNotNull(attachment),
    )

    private fun readyAttachment() = AttachmentReference(
        reference = "attachments/v1/secret-private-path.jpg",
        mimeType = "image/jpeg",
        displayName = "secret-photo.jpg",
        id = AttachmentId("attachment:1"),
        byteCount = 3,
        sha256 = "a".repeat(64),
    )

    private fun zipEntry(file: File, name: String): ByteArray = ZipFile(file).use { zip ->
        zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }
    }

    private fun rewritePackage(file: File, manifest: ByteArray, payload: ByteArray) {
        val temporary = File(file.parentFile, "${file.name}.rewrite")
        FileOutputStream(temporary).use { output -> ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
            zip.putNextEntry(ZipEntry("knowledge.json")); zip.write(payload); zip.closeEntry()
        } }
        assertTrue(file.delete())
        assertTrue(temporary.renameTo(file))
    }
}
