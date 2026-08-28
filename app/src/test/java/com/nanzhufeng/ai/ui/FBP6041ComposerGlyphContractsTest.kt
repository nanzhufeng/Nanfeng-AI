package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FBP6041ComposerGlyphContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val roundedSend = File("src/main/res/drawable/ic_nanfeng_send_rounded.xml").readText()
    private val roundedAdd = File("src/main/res/drawable/ic_nanfeng_composer_add_rounded.xml").readText()

    @Test
    fun `FB-P6-041 changes only add and idle-send glyph tokens`() {
        for (token in listOf(
            "private val ComposerAddGlyphSize = 30.dp",
            "private val ComposerSendGlyphSize = 24.dp",
            "private val ComposerStopGlyphSize = 22.5.dp",
            "contentDescription = attachmentDescription",
            "painterResource(R.drawable.ic_nanfeng_composer_add_rounded)",
            "modifier = Modifier.size(ComposerAddGlyphSize)",
            "attachmentDescription = \"添加到草稿\"",
            "attachmentDescription = \"添加附件\"",
            "painterResource(R.drawable.ic_nanfeng_send_rounded)",
            "modifier = Modifier.size(ComposerSendGlyphSize)",
            "Icon(Icons.Rounded.Stop, contentDescription = \"停止生成\", tint = glyphColor, modifier = Modifier.size(ComposerStopGlyphSize))",
            "modifier = Modifier.size(48.dp)",
            "shape = CircleShape",
            "Modifier.size(ComposerSendSurfaceSize)",
        )) assertTrue("missing $token", source.contains(token))
        for (token in listOf("android:strokeLineCap=\"round\"", "android:strokeLineJoin=\"round\"", "android:strokeWidth=\"1.6\"")) {
            assertTrue("missing rounded send vector token $token", roundedSend.contains(token))
        }
        for (token in listOf("android:pathData=\"M8,16L24,16M16,8L16,24\"", "android:strokeLineCap=\"round\"", "android:strokeLineJoin=\"round\"", "android:strokeWidth=\"2.4\"")) {
            assertTrue("missing enlarged rounded add vector token $token", roundedAdd.contains(token))
        }
        assertFalse(source.contains("Icons.AutoMirrored.Outlined.Send"))
        assertFalse(source.contains("Icons.Outlined.ArrowUpward, contentDescription = contentDescription"))
    }
}
