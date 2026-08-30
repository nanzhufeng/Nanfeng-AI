package com.nanzhufeng.ai.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeepSeekModelPickerPricingContractsTest {
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test fun `both DeepSeek picker rows consume the live shared pricing period`() {
        val overlay = workspace.substringAfter("private fun ComposerMenuOverlay(").substringBefore("private fun ComposerModelPickerHeader(")
        assertTrue(overlay.contains("DeepSeekPricingWindow.periodAt(java.time.Instant.now())"))
        assertTrue(overlay.contains("DeepSeekPricingWindow.millisUntilNextTransition(now)"))
        assertTrue(overlay.contains("providerId == com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK"))
        assertTrue(overlay.contains("\"${'$'}{deepSeekPricingPeriod.pickerLabel} · 官方实时联网检索\""))
    }

    @Test fun `only DeepSeek pricing status uses theme accent and bold weight`() {
        val row = workspace.substringAfter("private fun ComposerModelOverlayRow(").substringBefore("private fun ComposerOverlayAction(")
        assertTrue(row.contains("emphasizedDetailPrefix: String? = null"))
        assertTrue(row.contains("withStyle(SpanStyle(color = AccentOrange, fontWeight = FontWeight.Bold))"))
        assertTrue(row.contains("append(detail.removePrefix(prefix))"))
        assertTrue(row.contains("color = SecondaryText"))
        assertTrue(row.contains("fontWeight = FontWeight.Normal"))
        assertTrue(workspace.contains("emphasizedDetailPrefix = deepSeekPricingPeriod.pickerLabel.takeIf { isDeepSeek }"))
    }
}
