package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.NfaiSyncPreparedSnapshot
import com.nanzhufeng.ai.domain.NfaiSyncRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P7FConversationSyncWireFormatTest {
    @Test fun `current selected-conversation payload restores only its text tree`() {
        val snapshot = NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 3,
            records = listOf(NfaiSyncRecord("conversation", "conversation-1", 2, "NORMAL", """
                {"title":"跨端对话","currentLeafMessageId":"m2","createdAtEpochMs":1000,"updatedAtEpochMs":2000,"surface":"CHAT","nodes":[
                  {"id":"m1","parentMessageId":null,"siblingPosition":0,"role":"USER","createdAtEpochMs":1000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["你好"]},
                  {"id":"m2","parentMessageId":"m1","siblingPosition":0,"role":"ASSISTANT","createdAtEpochMs":2000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["你好，我在。"]}
                ]}
            """.trimIndent())),
        )

        val restored = P7FConversationSyncWireFormat.decode(snapshot)

        assertEquals("conversation-1", restored.conversation.id.value)
        assertEquals("跨端对话", restored.conversation.title)
        assertEquals("m2", restored.conversation.currentLeafMessageId?.value)
        assertEquals(2, restored.nodes.size)
        assertEquals("你好，我在。", restored.nodes.last().content.single().let { it as com.nanzhufeng.ai.domain.ContentBlock.Text }.text)
    }

    @Test fun `legacy Desktop payload restores its conversation but rejects unsafe records`() {
        val snapshot = NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 2,
            records = listOf(
                NfaiSyncRecord("conversation", "desktop-1", 1, "NORMAL", """
                  {"title":"Desktop 对话","createdAt":"2026-09-12T00:00:00Z","updatedAt":"2026-09-12T00:01:00Z","currentLeafId":"m1","messages":[
                    {"id":"m1","parentId":null,"ordinal":0,"role":"USER","createdAt":"2026-09-12T00:00:00Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"仅恢复文本"}]}
                  ]}
                """.trimIndent()),
                NfaiSyncRecord("safe_settings", "profile-interests", 1, "NORMAL", "{\"interests\":\"不导入\"}"),
            ),
        )

        val restored = P7FConversationSyncWireFormat.decode(snapshot)

        assertEquals("desktop-1", restored.conversation.id.value)
        assertEquals("仅恢复文本", (restored.nodes.single().content.single() as com.nanzhufeng.ai.domain.ContentBlock.Text).text)
        assertTrue(restored.nodes.all { it.content.all { block -> block is com.nanzhufeng.ai.domain.ContentBlock.Text } })
    }
}
