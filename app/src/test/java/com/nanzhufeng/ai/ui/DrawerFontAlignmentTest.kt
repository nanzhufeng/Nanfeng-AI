package com.nanzhufeng.ai.ui

import com.nanzhufeng.ai.domain.ProviderId
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawerFontAlignmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `ordinary settings action accommodates a large system font without clipping`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme(typography = Typography(labelLarge = TextStyle(fontSize = 21.sp, lineHeight = 30.sp))) {
                    Box(Modifier.fillMaxSize()) {
                        InvocationLedgerCard(InvocationLedgerUiState(), {})
                    }
                }
            }
        }
        val text = compose.onNodeWithText("查看调用记录", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val button = compose.onNode(hasText("查看调用记录") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val bounds = text.fetchSemanticsNode().boundsInRoot
        assertTrue("settings action clips the rendered line", bounds.top + layouts.single().multiParagraph.height <= button.bottom + 0.5f)
        assertEquals(button.center.y, bounds.center.y, 1f)
    }

    @Test fun `local and cloud labels stay centered and unclipped across app and system font scales`() {
        val scales = mutableStateOf(1f to 1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scales.value.second), LocalAppTextScale provides scales.value.first) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) {
                    Box(Modifier.width(480.dp)) {
                        DrawerNavigationListControls(false, false, true, {}, {}, {}, {})
                    }
                    }
                }
            }
        }
        for (scale in listOf(1f to 1f, 1.24f to 1.3f, 1.5f to 2f)) {
            compose.runOnIdle { scales.value = scale }
            compose.waitForIdle()
            for (label in listOf("本地", "云端")) {
                val text = compose.onNodeWithText(label, useUnmergedTree = true)
                val layouts = mutableListOf<TextLayoutResult>()
                text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.single().lineCount)
                val button = compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot
                val textBounds = text.fetchSemanticsNode().boundsInRoot
                // Paragraph height is fractional; Text's measured size rounds to whole pixels.
                // Compare the actual painted line against its clipping Surface, not that rounding.
                assertTrue("$label at $scale clips its line: text=$textBounds button=$button paragraph=${layouts.single().multiParagraph.height} style=${layouts.single().layoutInput.style.fontSize}/${layouts.single().layoutInput.style.lineHeight}", textBounds.top + layouts.single().multiParagraph.height <= button.bottom + 0.5f)
                assertTrue(textBounds.top >= button.top - 0.5f)
                assertEquals("$label at $scale is vertically centered", button.center.y, textBounds.center.y, 1f)
            }
        }
    }
    @Test fun `Relay segmented strip is shorter centered and independently clickable at large fonts`() {
        val current = mutableStateOf(ProviderId.RELAYCORE_CLAUDE)
        val scales = mutableStateOf(1f to 1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,scales.value.second),LocalAppTextScale provides scales.value.first) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.width(480.dp).testTag("provider-controls")) {
                            ModelProviderSegmentedTabs(listOf(ProviderId.OPENROUTER to "OpenRouter",ProviderId.RELAYCORE_CLAUDE to "RelayCore",ProviderId.DEEPSEEK to "DeepSeek",ProviderId.ZHIPU to "智谱",ProviderId.QWEN to "Qwen"),ProviderId.RELAYCORE_CLAUDE,horizontalInset=0.dp,onSelect={})
                            ModelProviderSegmentedTabs(listOf(ProviderId.RELAYCORE_CLAUDE to "Claude",ProviderId.RELAYCORE_GPT to "GPT",ProviderId.RELAYCORE_GEMINI to "Gemini"),current.value,widthFraction=0.6f,onSelect={current.value=it})
                        }
                    }
                }
            }
        }
        for (scale in listOf(1f to 1f,1.5f to 2f)) {
            compose.runOnIdle { scales.value=scale };compose.waitForIdle()
            val platforms=listOf("OpenRouter","RelayCore","DeepSeek","智谱","Qwen").map { compose.onNode(hasText(it) and hasClickAction()).fetchSemanticsNode().boundsInRoot }
            val profiles=listOf("Claude","GPT","Gemini").map { compose.onNode(hasText(it) and hasClickAction()).fetchSemanticsNode().boundsInRoot }
            val outerWidth=platforms.last().right-platforms.first().left
            val innerWidth=profiles.last().right-profiles.first().left
            val availableWidth=compose.onNodeWithTag("provider-controls").fetchSemanticsNode().boundsInRoot.width
            assertEquals(availableWidth-6f,outerWidth,1f)
            assertTrue(innerWidth < outerWidth * 0.65f)
            assertEquals((platforms.first().left+platforms.last().right)/2,(profiles.first().left+profiles.last().right)/2,1f)
            assertEquals(profiles.first().width,profiles.last().width,1f)
            for (label in listOf("Claude","GPT","Gemini")) {
                val node=compose.onNodeWithText(label,useUnmergedTree=true)
                val layouts=mutableListOf<TextLayoutResult>();node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val button=compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot
                val text=node.fetchSemanticsNode().boundsInRoot
                assertTrue(text.top+layouts.single().multiParagraph.height <= button.bottom+0.5f)
                assertEquals(button.center.y,text.center.y,1f)
            }
        }
        compose.onNodeWithText("GPT").performClick();compose.runOnIdle { assertEquals(ProviderId.RELAYCORE_GPT,current.value) }
        compose.onNodeWithText("Gemini").performClick();compose.runOnIdle { assertEquals(ProviderId.RELAYCORE_GEMINI,current.value) }
    }

}
