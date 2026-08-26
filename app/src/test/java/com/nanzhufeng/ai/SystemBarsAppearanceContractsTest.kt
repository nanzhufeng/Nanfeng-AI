package com.nanzhufeng.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarsAppearanceContractsTest {
    private val activity = File("src/main/java/com/nanzhufeng/ai/NanfengAiActivity.kt").readText()
    private val style = File("src/main/res/values/styles.xml").readText()

    @Test
    fun `app always requests dark system glyphs on its light edge to edge canvas`() {
        for (token in listOf(
            "SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)",
            "isAppearanceLightStatusBars = true",
            "isAppearanceLightNavigationBars = true",
            "override fun onWindowFocusChanged(hasFocus: Boolean)",
            "if (hasFocus) applyLightSystemBars()",
        )) assertTrue("missing explicit light-system-bar token: $token", activity.contains(token))
        for (token in listOf(
            "<item name=\"android:windowLightStatusBar\">true</item>",
            "<item name=\"android:windowLightNavigationBar\">true</item>",
        )) assertTrue("missing launch-theme system-bar token: $token", style.contains(token))
    }
}
