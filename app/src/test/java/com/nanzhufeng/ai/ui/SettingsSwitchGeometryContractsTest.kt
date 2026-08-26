package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSwitchGeometryContractsTest {
    @Test
    fun `all settings switches share the compressed widened track geometry`() {
        val dimensions = File("src/main/java/com/nanzhufeng/ai/ui/SettingsControlDimensions.kt").readText()
        val settings = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val model = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()

        assertTrue(dimensions.contains("SettingsSwitchTrackWidth = 74.4.dp"))
        assertTrue(dimensions.contains("SettingsSwitchTrackHeight = 15.4.dp"))
        assertTrue(settings.contains("width(SettingsSwitchTrackWidth).height(SettingsSwitchTrackHeight)"))
        assertTrue(Regex("width\\(SettingsSwitchTrackWidth\\)\\.height\\(SettingsSwitchTrackHeight\\)").findAll(model).count() == 2)
        assertTrue(!settings.contains("width(62.dp).height(22.dp)"))
        assertTrue(!model.contains("width(62.dp).height(22.dp)"))
    }
}
