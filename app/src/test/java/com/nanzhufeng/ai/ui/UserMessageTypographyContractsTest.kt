package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMessageTypographyContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test fun `user bubbles have a separate restrained Markdown hierarchy`() {
        val userTypography = source.substring(source.indexOf("private val UserBubbleTypography"), source.indexOf("private fun markdownInlineText"))
        assertTrue(source.contains("val typography = typographyOverride ?: if (assistantDocument) AssistantDocumentTypography else UserBubbleTypography"))
        for (token in listOf(
            "headingOne = 18.sp", "headingTwo = 17.sp", "headingMinor = 16.sp",
            "body = 15.sp", "bodyLineHeight = 23.sp", "note = 13.sp", "noteLineHeight = 19.sp",
            "headingOneWeight = FontWeight.SemiBold", "headingTwoWeight = FontWeight.Medium", "headingMinorWeight = FontWeight.Medium",
        )) assertTrue("missing restrained user type token $token", userTypography.contains(token))
        assertFalse(userTypography.contains("FontWeight.ExtraBold"))
    }

    @Test fun `assistant document hierarchy remains independent from the user bubble standard`() {
        val assistantTypography = source.substring(source.indexOf("private val AssistantDocumentTypography"), source.indexOf("private val UserBubbleTypography"))
        assertTrue(assistantTypography.contains("headingOne = 26.sp"))
        assertTrue(assistantTypography.contains("headingOneWeight = FontWeight.ExtraBold"))
        assertTrue(source.contains("Arrangement.spacedBy(if (assistantDocument) 8.dp else 6.dp)"))
    }
}
