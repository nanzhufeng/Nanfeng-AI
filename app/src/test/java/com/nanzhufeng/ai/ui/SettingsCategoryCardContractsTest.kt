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

        for (title in listOf("对话", "外观", "数据管理", "工作区")) {
            assertTrue("missing settings group $title", categories.contains("SettingsCategoryGroup(title = \"$title\")"))
        }
        assertTrue(categories.contains("grouped = true"))
        assertTrue(categories.contains("SettingsCategoryDivider()"))
        assertTrue(group.contains("Card("))
        assertTrue(group.contains("containerColor = ForegroundSurface"))
        assertTrue(app.contains("private fun SettingsCategoryDivider() = Spacer("))
        assertTrue(app.contains("SettingsGroupedCardDividerHeight = 4.dp"))
        assertTrue(app.contains("height(SettingsGroupedCardDividerHeight).background(SettingsPageBackground)"))
    }

    @Test
    fun `settings entry icons express model network transfer and local storage`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val categories = app.substringAfter("private fun SettingsCategoryList(").substringBefore("@Composable\nprivate fun SettingsCategoryGroup")

        assertTrue(categories.contains("SettingsCategoryRow(Icons.Rounded.Hub, \"模型与联网\""))
        assertTrue(categories.contains("SettingsCategoryRow(Icons.Rounded.ImportExport, \"导入与导出\", iconTint = settingsUtilityIconTint()"))
        assertTrue(categories.contains("SettingsCategoryRow(Icons.Rounded.Storage, \"本机数据\", iconTint = settingsUtilityIconTint()"))
    }
}
