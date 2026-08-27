package com.nanzhufeng.ai.ui

import com.nanzhufeng.ai.domain.AppFontSize
import com.nanzhufeng.ai.domain.AppearanceSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceFontSizeContractsTest {
    @Test
    fun `standard font size preserves the existing baseline and the other choices are bounded`() {
        assertEquals(AppFontSize.STANDARD, AppearanceSettings().fontSize)
        assertEquals(0.80f, AppFontSize.SMALL.scale, 0.001f)
        assertEquals(1.00f, AppFontSize.STANDARD.scale, 0.001f)
        assertEquals(1.24f, AppFontSize.LARGE.scale, 0.001f)
        assertEquals(0.80f, AppFontSize.SMALL.iconScale, 0.001f)
        assertEquals(1.24f, AppFontSize.LARGE.iconScale, 0.001f)
    }

    @Test
    fun `font size is durable in the app private appearance preference`() {
        val repository = File("src/main/java/com/nanzhufeng/ai/data/AndroidAppearanceSettingsRepository.kt").readText()

        assertTrue(repository.contains("const val FONT_SIZE = \"app_font_size\""))
        assertTrue(repository.contains("fontSize = preferences.getString(FONT_SIZE, null).toEnumOrDefault(AppFontSize.STANDARD)"))
        assertTrue(repository.contains(".putString(FONT_SIZE, settings.fontSize.name)"))
    }

    @Test
    fun `appearance page offers exactly the three global text choices`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val controls = app.substring(
            app.indexOf("private fun AppearanceSettingsControls"),
            app.indexOf("private fun AppearancePreferenceRow"),
        )

        for (token in listOf(
            "title = \"字体大小\"",
            "AppearancePicker.FONT_SIZE",
            "com.nanzhufeng.ai.domain.AppFontSize.entries.forEach",
            "it.copy(fontSize = option)",
            "AppFontSize.SMALL -> \"小\"",
            "AppFontSize.STANDARD -> \"标准\"",
            "AppFontSize.LARGE -> \"大\"",
            "previewIcon = Icons.Rounded.FormatSize",
            "previewScale = option.scale",
        )) assertTrue("missing font-size choice contract: $token", app.contains(token) || controls.contains(token))
    }

    @Test
    fun `root typography and explicit reading sizes share the global scaling owner`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val conversation = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val scaleOwner = app.substring(
            app.indexOf("private fun TextStyle.scaledForAppFontSize"),
            app.indexOf("private const val SettingsTextScaleFactor"),
        )

        assertTrue(scaleOwner.contains("fontSize = fontSize * scale"))
        assertTrue(scaleOwner.contains("lineHeight = lineHeight * scale"))
        assertTrue(app.contains("val appTypography = remember(appearance.fontSize)"))
        assertTrue(app.contains("Typography().scaledForAppFontSize(appearance.fontSize.scale)"))
        assertTrue(app.contains("typography = appTypography"))
        assertTrue(app.contains("LocalAppIconScale provides appearance.fontSize.iconScale"))
        assertTrue(app.contains("LocalAppTextScale provides appearance.fontSize.scale"))
        assertTrue(app.contains("internal fun scaledAppIconSize(base: Dp): Dp"))
        assertTrue(app.contains("internal fun scaledAppTextUnit(base: androidx.compose.ui.unit.TextUnit)"))
        assertTrue(conversation.contains("scaledAppTextUnit(value * ConversationTextScaleFactor)"))
        assertTrue(conversation.contains("val drawerIdentityVisualSize = scaledAppIconSize(36.dp)"))
        assertTrue(conversation.contains("Modifier.height(drawerIdentityHeaderReservedHeight)"))
        assertTrue(conversation.contains("fontSize = scaledAppTextUnit(22.sp)"))
        assertTrue(conversation.contains("\"搜索\",\n                            color = BodyText,\n                            fontSize = scaledConversationTextUnit(16.sp)"))
    }
}
