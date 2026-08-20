package com.nanzhufeng.ai.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class P6F2EAudioAndTextPreviewUiContractsTest {
    @Test fun `workspace keeps explicit local audio and inert text actions`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        for (token in listOf("AudioPreviewDialog", "开始本地播放", "关闭后会恢复到当前位置", "TextPreviewDialog", "inert UTF-8", "不会渲染 HTML、执行链接、脚本或 Markdown 指令", "onOpenAudioPreview", "onOpenTextPreview")) assertTrue(token, source.contains(token))
        assertTrue(java.io.File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText().contains("\"audio/*\""))
    }
}
