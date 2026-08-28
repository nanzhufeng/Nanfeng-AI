package com.nanzhufeng.ai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentOpenResult
import com.nanzhufeng.ai.domain.AttachmentReference
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P6KZipArchiveAttachmentStorageContractsTest {
    @Test
    fun `archive-backed imported attachment opens through the native verified stream without deleting its shared ZIP`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val taskId = "archive-attachment-fixture"
        val entryName = "file_fixture.dat"
        val bytes = "source-owned attachment".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val archive = File(context.filesDir, "p6k-zip-import/v1/archives/$taskId.zip")
        archive.parentFile?.mkdirs()
        ZipOutputStream(archive.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(entryName))
            output.write(bytes)
            output.closeEntry()
        }
        try {
            val reference = AttachmentReference(
                P6KZipArchiveAssetStorage.key(taskId, entryName),
                "text/plain",
                "附件.txt",
                AttachmentId.new(),
                bytes.size.toLong(),
                hash,
            )
            val store = AndroidPrivateAttachmentStore(context)
            val opened = store.openVerified(reference) as AttachmentOpenResult.Opened
            assertArrayEquals(bytes, opened.open().use { it.readBytes() })
            assertTrue(store.deletePrivateCopy(reference))
            assertTrue(archive.isFile)

            val tampered = reference.copy(sha256 = "f".repeat(64))
            assertTrue(store.openVerified(tampered) is AttachmentOpenResult.Rejected)
            assertFalse(P6KZipArchiveAssetStorage.parse("p6k-zip-assets/v1/../escape") != null)
        } finally {
            archive.delete()
        }
    }
}
