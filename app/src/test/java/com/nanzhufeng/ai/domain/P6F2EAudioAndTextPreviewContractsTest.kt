package com.nanzhufeng.ai.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P6F2EAudioAndTextPreviewContractsTest {
    private val audio = AttachmentReference("attachments/v1/audio.mp3", "audio/mpeg", "local.mp3", AttachmentId("audio-preview"), 4, "a".repeat(64))
    private val text = AttachmentReference("attachments/v1/text.txt", "text/plain", "local.txt", AttachmentId("text-preview"), 8, "b".repeat(64))

    @Test fun `audio reads verified bytes only after explicit typed request`() {
        val preview = ConversationAttachmentPreviewProjection(FakeAssets(audio), FakeStore(byteArrayOf(1, 2, 3, 4))).audio(audio.toConversationReference())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), preview.bytes)
        assertNull(preview.unavailableReason)
        assertNotNull(ConversationAttachmentPreviewProjection(FakeAssets(audio.copy(sha256 = "c".repeat(64))), FakeStore(byteArrayOf(1))).audio(audio.toConversationReference()).unavailableReason)
    }

    @Test fun `text strips bom and stays inert bounded utf8`() {
        val preview = ConversationAttachmentPreviewProjection(FakeAssets(text), FakeStore("\uFEFFhello".toByteArray())).text(text.toConversationReference())
        assertEquals("hello", preview.text)
        assertEquals(false, preview.truncated)
        assertNotNull(ConversationAttachmentPreviewProjection(FakeAssets(text), FakeStore(byteArrayOf(0xC3.toByte()))).text(text.toConversationReference()).unavailableReason)
    }

    @Test fun `text is capped and rejects missing or tampered owner facts`() {
        val oversized = "x".repeat(128 * 1024 + 9).toByteArray()
        val preview = ConversationAttachmentPreviewProjection(FakeAssets(text), FakeStore(oversized)).text(text.toConversationReference())
        assertEquals(128 * 1024, preview.text?.length)
        assertTrue(preview.truncated)
        assertNotNull(ConversationAttachmentPreviewProjection(FakeAssets(null), FakeStore(byteArrayOf())).text(text.toConversationReference()).unavailableReason)
        assertNotNull(ConversationAttachmentPreviewProjection(FakeAssets(text.copy(sha256 = "c".repeat(64))), FakeStore("hello".toByteArray())).text(text.toConversationReference()).unavailableReason)
    }

    private class FakeAssets(private val value: AttachmentReference?) : PrivateAttachmentRepository {
        override fun save(asset: AttachmentReference) = asset
        override fun findById(id: AttachmentId) = value?.takeIf { it.id == id }
        override fun findBySha256(sha256: String) = value?.takeIf { it.sha256 == sha256 }
    }

    private class FakeStore(private val bytes: ByteArray) : PrivateAttachmentStore {
        override fun import(request: AttachmentImportRequest) = AttachmentImportResult.Rejected(AiTaskError.AttachmentImportFailed)
        override fun read(attachment: AttachmentReference) = AttachmentReadResult.Content(bytes)
        override fun thumbnail(attachment: AttachmentReference) = AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentNotReady)
    }
}
