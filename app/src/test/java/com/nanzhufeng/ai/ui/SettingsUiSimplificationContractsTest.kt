package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiSimplificationContractsTest {
    @Test
    fun `normal settings keep developer controls out of the user path`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val hierarchy = app.substring(app.indexOf("private fun SettingsHierarchy"), app.indexOf("private fun WorkbenchRoute"))

        for (token in listOf("ModelServiceStatusCard", "ConversationManagementSettingsCard", "PrivacyDataCard", "onOpenImport", "onOpenBackup", "打开备份与恢复")) {
            assertTrue("missing user setting $token", hierarchy.contains(token))
        }
        for (token in listOf("InvocationLedgerCard", "P6GModelSelectionSettingsCard", "P6ETemporaryMaintenanceAcceptanceCard", "installP6GLocalFixtureCatalog")) {
            assertFalse("developer control leaked: $token", hierarchy.contains(token))
        }
    }

    @Test
    fun `same-purpose settings use one selection surface instead of button rows`() {
        val model = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
        val privacy = File("src/main/java/com/nanzhufeng/ai/ui/PrivacyDataUi.kt").readText()
        val conversations = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

        assertTrue(model.contains("AiPresetSelectionSurface"))
        assertFalse(model.contains("SettingSection("))
        assertFalse(model.contains("固定官方兼容端点"))
        assertTrue(model.contains("val savedKeyMask = \"••••••••\""))
        assertTrue(model.contains("keyEdited && it.isNotBlank()"))
        assertTrue(model.contains("已安全保存。输入新值可替换。"))
        assertTrue(privacy.contains("val cleanupScopes"))
        assertTrue(privacy.contains("DropdownMenu("))
        assertFalse(privacy.contains("安全诊断 JSON"))
        val management = conversations.substring(conversations.indexOf("internal fun ConversationManagementSettingsCard"), conversations.indexOf("private fun ConversationSelector"))
        assertTrue(management.contains("DropdownMenu("))
        assertTrue(management.contains("Text(\"查看：\$selectedScope\""))
    }
}
