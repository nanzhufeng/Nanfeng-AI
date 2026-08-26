package com.nanzhufeng.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentReferenceInstructionTest {
    @Test
    fun `material instructions retain one concrete marker per submitted item`() {
        val instruction = attachmentReferenceInstruction(
            listOf(
                ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "chart.png", byteArrayOf(1)),
                ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "report.pdf", byteArrayOf(2)),
                ChatAttachment(ChatAttachmentKind.IMAGE, "image/jpeg", "photo.jpg", byteArrayOf(3)),
                ChatAttachment(ChatAttachmentKind.VIDEO, "video/mp4", "clip.mp4", byteArrayOf(4)),
                ChatAttachment(ChatAttachmentKind.AUDIO, "audio/mpeg", "memo.mp3", byteArrayOf(5)),
                ChatAttachment(ChatAttachmentKind.FILE, "text/plain", "notes.txt", byteArrayOf(6)),
            ),
        )

        assertEquals(2, Regex("<图片>").findAll(instruction).count())
        for (marker in listOf("<PDF>", "<视频>", "<音频>", "<文件>", "先单独一行写出对应标签", "具体类型")) assertTrue(instruction.contains(marker))
        assertFalse(instruction.contains("请结合本条消息附件回答"))
    }
}
