package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FBP6041ComposerGlyphContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test
    fun `FB-P6-041 changes only add and idle-send glyph tokens`() {
        for (token in listOf(
            "private val ComposerAddGlyphSize = 19.2.dp",
            "private val ComposerSendGlyphSize = 18.dp",
            "private val ComposerStopGlyphSize = 22.5.dp",
            "contentDescription = attachmentDescription",
            "modifier = Modifier.size(ComposerAddGlyphSize)",
            "attachmentDescription = \"添加到草稿\"",
            "attachmentDescription = \"添加附件\"",
            "import androidx.compose.material.icons.automirrored.outlined.Send",
            "Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = contentDescription, tint = glyphColor, modifier = Modifier.size(ComposerSendGlyphSize).graphicsLayer(rotationZ = -90f)",
            "Modifier.size(ComposerSendGlyphSize).graphicsLayer(rotationZ = -90f)",
            "Icon(Icons.Outlined.Stop, contentDescription = \"停止生成\", tint = glyphColor, modifier = Modifier.size(ComposerStopGlyphSize))",
            "modifier = Modifier.size(48.dp)",
            "shape = CircleShape",
            "Modifier.size(ComposerSendSurfaceSize)",
        )) assertTrue("missing $token", source.contains(token))
        assertFalse(source.contains("Icons.Outlined.ArrowUpward, contentDescription = contentDescription"))
    }
}
