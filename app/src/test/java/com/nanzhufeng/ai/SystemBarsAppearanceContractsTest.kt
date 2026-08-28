package com.nanzhufeng.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarsAppearanceContractsTest {
    private val activity = File("src/main/java/com/nanzhufeng/ai/NanfengAiActivity.kt").readText()
    private val style = File("src/main/res/values/styles.xml").readText()

    @Test
    fun `launch has a light fallback while Compose owns glyph contrast for the active skin`() {
        for (token in listOf(
            "SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)",
            "isAppearanceLightStatusBars = true",
            "isAppearanceLightNavigationBars = true",
            "override fun onWindowFocusChanged(hasFocus: Boolean)",
            "Appearance is owned by the Compose skin",
        )) assertTrue("missing explicit light-system-bar token: $token", activity.contains(token))
        assertTrue("focus regain must not overwrite the active dark skin", !activity.contains("if (hasFocus) applyLightSystemBars()"))
        for (token in listOf(
            "<item name=\"android:windowLightStatusBar\">true</item>",
            "<item name=\"android:windowLightNavigationBar\">true</item>",
        )) assertTrue("missing launch-theme system-bar token: $token", style.contains(token))
    }
}
