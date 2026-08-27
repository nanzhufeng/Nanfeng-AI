package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSwitchGeometryContractsTest {
    @Test
    fun `all settings switches apply the requested transform once instead of compounding it`() {
        val dimensions = File("src/main/java/com/nanzhufeng/ai/ui/SettingsControlDimensions.kt").readText()
        val settings = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val model = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val assistantExperienceViewModel = File("src/main/java/com/nanzhufeng/ai/ui/AssistantExperienceSettingsViewModel.kt").readText()
        val notificationViewModel = File("src/main/java/com/nanzhufeng/ai/ui/NotificationReminderSettingsViewModel.kt").readText()
        val conversationViewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val allUiSources = File("src/main/java/com/nanzhufeng/ai/ui")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        assertTrue(dimensions.contains("SettingsSwitchTrackWidth = 58.dp"))
        assertTrue(dimensions.contains("SettingsSwitchTrackHeight = 28.dp"))
        assertTrue(dimensions.contains("SettingsSwitchThumbDiameter = 21.dp"))
        assertFalse(dimensions.contains("SettingsSwitchTrackWidth = 74.4.dp"))
        assertFalse(dimensions.contains("SettingsSwitchTrackHeight = 15.4.dp"))
        assertFalse(dimensions.contains("SettingsSwitchTrackWidth = 62.4.dp"))
        assertFalse(dimensions.contains("SettingsSwitchTrackHeight = 22.4.dp"))
        assertTrue(dimensions.contains("requiredSize(SettingsSwitchTrackWidth, SettingsSwitchTouchTargetHeight)"))
        assertTrue(dimensions.contains("size(SettingsSwitchTrackWidth, SettingsSwitchTrackHeight)"))
        assertTrue(dimensions.contains("role = Role.Switch"))
        assertTrue(dimensions.contains("onSurface.copy(alpha = 0.16f)"))
        assertFalse(dimensions.contains("MaterialTheme.colorScheme.surfaceVariant"))
        assertTrue(Regex("\\bSettingsSwitch\\(").findAll(settings).count() == 1)
        assertTrue(Regex("\\bSettingsSwitch\\(").findAll(model).count() == 2)
        assertTrue(Regex("\\bSettingsSwitch\\(").findAll(workspace).count() == 1)
        assertFalse(settings.contains("androidx.compose.material3.Switch"))
        assertFalse(model.contains("androidx.compose.material3.Switch"))
        assertFalse(workspace.contains("androidx.compose.material3.Switch"))
        assertFalse(allUiSources.contains("import androidx.compose.material3.Switch"))
        assertTrue(assistantExperienceViewModel.contains("onlyWebSearchToggle"))
        assertTrue(assistantExperienceViewModel.contains("notice = if (onlyWebSearchToggle) null"))
        assertFalse(notificationViewModel.contains("通知与提醒设置已保存在本机。"))
        assertTrue(conversationViewModel.contains("is ConversationWebSearchOverrideMutationResult.Applied -> {\n                    state = state.copy(notice = null)\n                    reload()"))
    }
}
