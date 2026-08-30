package com.nanzhufeng.ai.acceptance

import android.app.Application
import android.util.Base64
import com.nanzhufeng.ai.app.AppContainer
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.AttachmentImportRequest
import com.nanzhufeng.ai.domain.AttachmentImportResult
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationAttachmentReference
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.toConversationReference
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.FutureTask

/**
 * Disposable, separate-package visual acceptance data.
 *
 * The fixture reaches the same private-file store, attachment catalogue and conversation
 * repository as production. It never reads a user file, credential, network response or another
 * installed package, and it refuses to add a second fixture to a non-empty acceptance database.
 */
class SearchAttachmentAcceptanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val clock = Clock.fixed(Instant.parse("2026-08-29T02:00:00Z"), ZoneOffset.UTC)
        val seed = FutureTask { seed(clock) }
        Thread(seed, "search-attachment-acceptance-seed").start()
        seed.get()
    }

    private fun seed(clock: Clock) {
        val container = AppContainer(this, clock)
        if (container.conversationRepository.listActive().isNotEmpty()) return

        val sharedImage = import(
            container,
            "image/png",
            "共享图片-最后引用才清理.png",
            validPng(12_345),
        )
        val video = import(container, "video/mp4", "演示视频.mp4", mp4(234_567))
        val audio = import(container, "audio/wav", "语音记录.wav", wav(34_567))
        val pdf = import(container, "application/pdf", "完整说明.pdf", pdf(1_234_567))
        val text = import(container, "text/plain", "补充说明.txt", plainText(543))

        val tree = ConversationTreeService(clock)
        var snapshot = tree.create("搜索附件验收")
        listOf(
            MessageRole.USER to sharedImage,
            MessageRole.ASSISTANT to pdf,
            MessageRole.USER to audio,
            MessageRole.ASSISTANT to video,
            MessageRole.USER to sharedImage,
            MessageRole.ASSISTANT to text,
        ).forEach { (role, attachment) ->
            snapshot = tree.append(
                snapshot,
                AppendMessageRequest(role, listOf(ContentBlock.Attachment(attachment))),
            )
        }
        container.conversationRepository.save(snapshot)
    }

    private fun import(
        container: AppContainer,
        mimeType: String,
        displayName: String,
        bytes: ByteArray,
    ): ConversationAttachmentReference {
        val imported = container.privateAttachmentStore.import(
            AttachmentImportRequest(ByteArrayInputStream(bytes), mimeType, displayName),
        ) as? AttachmentImportResult.Imported ?: error("验收附件导入失败：$displayName")
        return container.privateAttachmentRepository.save(imported.attachment).toConversationReference()
    }

    private fun validPng(size: Int): ByteArray = padded(
        Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFElEQVR4nGP8z8DAwMDAxMDAwMDAAAANHQEDasKb6QAAAABJRU5ErkJggg==",
            Base64.DEFAULT,
        ),
        size,
    )

    private fun mp4(size: Int): ByteArray = padded(
        byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()) +
            "isom".encodeToByteArray(),
        size,
    )

    private fun wav(size: Int): ByteArray = padded(
        "RIFF0000WAVEfmt ".encodeToByteArray(),
        size,
    )

    private fun pdf(size: Int): ByteArray = padded("%PDF-1.7\n% acceptance\n".encodeToByteArray(), size)

    private fun plainText(size: Int): ByteArray = ByteArray(size) { 'A'.code.toByte() }

    private fun padded(prefix: ByteArray, size: Int): ByteArray {
        require(prefix.size <= size)
        return ByteArray(size).also { prefix.copyInto(it) }
    }
}
