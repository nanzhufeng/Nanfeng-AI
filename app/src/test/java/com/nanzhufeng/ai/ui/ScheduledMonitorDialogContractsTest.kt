package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledMonitorDialogContractsTest {
    @Test fun `planned title is centered against the page while close icon stays at the right edge`() {
        val dialog = File("src/main/java/com/nanzhufeng/ai/ui/ScheduledMonitorDialog.kt").readText()
        val header = dialog.substringAfter("Box(modifier = Modifier.fillMaxWidth().height(48.dp))").substringBefore("if (state.creating && state.refining)")

        assertTrue(header.contains("Alignment.CenterStart else Alignment.Center"))
        assertTrue(header.contains("IconButton("))
        assertTrue(header.contains("modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)"))
        assertTrue(header.contains("Icon(Icons.Rounded.Close, contentDescription = \"关闭已计划\")"))
        assertFalse(header.contains("Text(\"关闭\")"))
        assertTrue(header.contains("semantics { heading() }"))
    }

    @Test fun `planned page omits the redundant summary sentence`() {
        val dialog = File("src/main/java/com/nanzhufeng/ai/ui/ScheduledMonitorDialog.kt").readText()

        assertFalse(dialog.contains("按所选周期联网生成简报，可随时暂停或删除。"))
    }

    @Test fun `closing planned tasks returns to the conversation drawer`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val monitorOwner = app
            .substringAfter("if (scheduledMonitorViewModel.state.visible) ScheduledMonitorDialog(")
            .substringBefore("onStartCreate =")

        assertTrue(monitorOwner.contains("scheduledMonitorViewModel.dismiss()"))
        assertTrue(monitorOwner.contains("onExitSettingsToConversationDrawer()"))
    }
}
