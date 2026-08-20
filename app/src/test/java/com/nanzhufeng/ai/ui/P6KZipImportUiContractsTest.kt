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
        assertTrue(source.contains("已保留任务与私有副本；请重试删除"))
        assertFalse(source.contains("task.displayName"))
        assertFalse(source.contains("item.candidate!!.title"))
        assertFalse(source.contains("target.conversationTitle"))
    }
}
