package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PillShapeScopeContractsTest {
    private val adaptiveUi = File("src/main/java/com/nanzhufeng/ai/ui/P5AAdaptiveUi.kt").readText()
    private val settingsUi = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
    private val conversationContract = File("../docs/ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md").readText()

    @Test fun `pill is restricted to single line controls and history-library switch has no confirmation dialog`() {
        assertTrue(adaptiveUi.contains("Pill contours are reserved for single-line foreground controls only."))
        assertTrue(adaptiveUi.contains("internal val P5AInteractiveShape = RoundedCornerShape(999.dp)"))
        assertTrue(adaptiveUi.contains("internal val P5AMultilineSurfaceShape = RoundedCornerShape(24.dp)"))

        assertFalse(settingsUi.contains("autoHistoryCurationConsentVisible"))
        assertFalse(settingsUi.contains("开启历史资料库？"))
        assertTrue(conversationContract.contains("胶囊形状只用于单行白色控件／卡片"))
        assertTrue(conversationContract.contains("确认弹层及长文本承载面一律使用圆角矩形"))
    }
}
