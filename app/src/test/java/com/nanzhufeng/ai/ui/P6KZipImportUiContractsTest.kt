package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** K9: Settings may expose task state, never the selected ZIP filename. */
class P6KZipImportUiContractsTest {
    @Test fun `ZIP Settings uses an anonymous batch label while retaining direct import and revoke actions`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/P6KZipImportUi.kt").readText()

        assertTrue(source.contains("导入 ChatGPT ZIP"))
        assertTrue(source.contains("导入 Claude ZIP"))
        assertTrue(source.contains("导入批次"))
        assertTrue(source.contains("删除导入批次"))
        assertTrue(source.contains("init { show() }"))
        assertTrue(source.contains("已保留任务与私有副本；请重试删除"))
        assertFalse(source.contains("task.displayName"))
        assertFalse(source.contains("item.candidate!!.title"))
        assertFalse(source.contains("target.conversationTitle"))
    }

    @Test fun `conversation ZIP pickers request only ZIP document MIME types`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val pickerActions = app.substring(app.indexOf("onOpenP6KChatGptZip ="), app.indexOf("onClearP6KZip ="))

        for (token in listOf("p6kChatGptZipPicker.launch(arrayOf(\"application/zip\", \"application/x-zip-compressed\"))", "p6kClaudeZipPicker.launch(arrayOf(\"application/zip\", \"application/x-zip-compressed\"))")) {
            assertTrue("missing ZIP-only picker request $token", pickerActions.contains(token))
        }
        assertFalse(pickerActions.contains("arrayOf(\"*/*\")"))
    }
}
