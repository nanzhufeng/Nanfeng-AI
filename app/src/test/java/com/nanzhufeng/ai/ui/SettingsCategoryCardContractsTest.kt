package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCategoryCardContractsTest {
    @Test
    fun `settings home groups ordinary entries into four cards with canvas dividers`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val categories = app.substringAfter("private fun SettingsCategoryList(").substringBefore("@Composable\nprivate fun SettingsCategoryGroup")
        val group = app.substringAfter("private fun SettingsCategoryGroup(").substringBefore("@Composable\nprivate fun SettingsCategoryDivider")

        for (title in listOf("对话", "外观", "应用与数据", "工作区")) {
            assertTrue("missing settings group $title", categories.contains("SettingsCategoryGroup(title = \"$title\")"))
        }
        assertTrue(categories.contains("grouped = true"))
        assertTrue(categories.contains("SettingsCategoryDivider()"))
        assertTrue(group.contains("Card("))
        assertTrue(group.contains("containerColor = ForegroundSurface"))
        assertTrue(app.contains("private fun SettingsCategoryDivider() = Spacer("))
        assertTrue(app.contains("height(8.dp).background(SettingsPageBackground)"))
    }
}
