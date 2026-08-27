package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CenteredDialogScrimContractsTest {
    @Test
    fun `all standard centered dialogs use the shared light neutral scrim`() {
        val owner = File("src/main/java/com/nanzhufeng/ai/ui/P5ADialogDismissBehavior.kt").readText()
        for (token in listOf(
            "P5ACenteredDialogScrimAlpha = 0.12f",
            "private fun P5ALightDialogScrimEffect()",
            "setDimAmount(P5ACenteredDialogScrimAlpha)",
            "WindowManager.LayoutParams.FLAG_DIM_BEHIND",
            "P5ALightDialogScrimEffect()\n            P5ADialogEdgeDismissEffect(onDismissRequest)",
        )) assertTrue("missing shared light-scrim token $token", owner.contains(token))

        val uiSources = File("src/main/java/com/nanzhufeng/ai/ui").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "P5ADialogDismissBehavior.kt" }
            .toList()
        assertFalse(uiSources.any { it.readText().contains("import androidx.compose.material3.AlertDialog") })
        assertFalse(uiSources.any { it.readText().contains("import androidx.compose.ui.window.Dialog\n") })
    }
}
