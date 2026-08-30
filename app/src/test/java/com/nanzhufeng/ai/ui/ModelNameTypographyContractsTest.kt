package com.nanzhufeng.ai.ui

import androidx.compose.ui.text.font.FontWeight
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelNameTypographyContractsTest {
    @Test
    fun `model name helper bolds only the concrete model segment`() {
        val text = modelNameAnnotatedText(
            prefix = "OpenRouter · ",
            modelName = "GPT-5.6 Sol",
            suffix = " · 已完成",
        )

        assertEquals("OpenRouter · GPT-5.6 Sol · 已完成", text.text)
        val bold = text.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("GPT-5.6 Sol", text.text.substring(bold.start, bold.end))
    }

    @Test
    fun `every model surface including the main composer entry is bold`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val composer = workspace.substringAfter("private fun ComposerModelEntry(").substringBefore("private fun ConversationComposerDock")
        val picker = workspace.substringAfter("private fun ComposerModelOverlayRow(").substringBefore("private fun ComposerOverlayAction")
        val pickerHeader = workspace.substringAfter("private fun ComposerModelPickerHeader(").substringBefore("private fun ComposerModelPickerSectionLabel")
        val assistantFooter = workspace.substringAfter("private fun AssistantMessageActionRow(").substringBefore("private fun MessageActionPopup")
        val settings = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
        val ledgers = listOf(
            File("src/main/java/com/nanzhufeng/ai/ui/InvocationLedgerUi.kt").readText(),
            File("src/main/java/com/nanzhufeng/ai/ui/ConversationCostLedgerUi.kt").readText(),
            File("src/main/java/com/nanzhufeng/ai/ui/KnowledgeLibraryUi.kt").readText(),
            File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText(),
            File("src/main/java/com/nanzhufeng/ai/ui/ScheduledMonitorDialog.kt").readText(),
        )

        assertTrue(composer.contains("fontWeight = FontWeight.Bold"))
        assertFalse(composer.contains("fontWeight = FontWeight.Normal"))
        assertTrue(picker.contains("Text(label, fontWeight = FontWeight.Bold"))
        assertFalse(picker.contains("labelIsModelName"))
        assertTrue(pickerHeader.contains("fontWeight = FontWeight.Bold"))
        assertTrue(assistantFooter.contains("assistantFooterMetadataText"))
        assertTrue(workspace.contains("modelNameAnnotatedText(") && workspace.contains("fontWeight = FontWeight.Bold"))
        assertTrue(settings.contains("Text(selected.displayName, fontWeight = FontWeight.Bold)"))
        assertTrue(settings.contains("Text(preset.displayName, fontWeight = FontWeight.Bold"))
        assertTrue(ledgers.first().contains("modelDisplayNameForUser(modelId)"))
        assertTrue(ledgers.all { it.contains("modelNameAnnotatedText") || it.contains("fontWeight = FontWeight.Bold") })
    }
}
