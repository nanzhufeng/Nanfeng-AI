package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationDrawerEdgeFadeContractsTest {
    private val workspaceSource = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val edgeFadeSource = File("src/main/java/com/nanzhufeng/ai/ui/ConversationEdgeGrayFade.kt").readText()
    private val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

    @Test
    fun `drawer has no independent full screen gray fade cover`() {
        assertTrue(edgeFadeSource.contains("topBand: Dp = 128.dp"))
        assertTrue(edgeFadeSource.contains("bottomBand: Dp = 112.dp"))
        assertTrue(edgeFadeSource.contains("TopEdgeFadeOpaqueAlpha = 1f"))
        assertTrue(edgeFadeSource.contains("0.25f to edgeColor.copy(alpha = 0.96f)"))
        assertTrue(edgeFadeSource.contains("0.5f to edgeColor.copy(alpha = 0.76f)"))
        assertTrue(edgeFadeSource.contains("0.75f to edgeColor.copy(alpha = 0.36f)"))
        assertTrue(edgeFadeSource.contains("BottomEdgeFadeOpaqueAlpha = 0.98f"))
        assertTrue(workspaceSource.contains("Modifier.conversationEdgeGrayFade()"))
        assertFalse(workspaceSource.contains("DrawerContentEdgeGrayFade"))
        assertFalse(workspaceSource.contains("DrawerBottomActionFade"))
        val drawer = workspaceSource.substring(
            workspaceSource.indexOf("private fun ConversationNavigationDrawer"),
            workspaceSource.indexOf("private fun ConversationBatchEditControls"),
        )
        assertTrue(drawer.contains(".conversationEdgeGrayFade(edgeColor = ConversationDrawerBaseSurface)"))
        assertFalse(drawer.contains("conversationEdgeGrayFade(edgeColor = ConversationDrawerCanvas)"))
        assertFalse(drawer.contains("DrawerContentEdgeGrayFade"))
        val viewportModifier = drawer.substring(
            drawer.indexOf(".fillMaxSize()"),
            drawer.indexOf("verticalArrangement = Arrangement.spacedBy(8.dp)"),
        )
        assertTrue(viewportModifier.indexOf("conversationEdgeGrayFade(edgeColor = ConversationDrawerBaseSurface)") < viewportModifier.indexOf("verticalScroll(rememberScrollState())"))
        assertTrue(viewportModifier.indexOf("verticalScroll(rememberScrollState())") < viewportModifier.indexOf("statusBarsPadding()"))
        assertTrue(viewportModifier.indexOf("statusBarsPadding()") < viewportModifier.indexOf(".padding("))
    }

    @Test
    fun `drawer keeps a bright base through the status bar while gray remains edge-only`() {
        assertTrue(appSource.contains("ConversationDrawerBaseSurface by mutableStateOf(Color.White)"))
        assertTrue(appSource.contains("ConversationDrawerCanvas by mutableStateOf(Color(0xFFF2F2F2))"))
        assertTrue(workspaceSource.contains("drawerTonalElevation = 0.dp"))
        assertTrue(workspaceSource.contains(".fillMaxHeight()\n                        .background(ConversationDrawerBaseSurface)"))
        assertEquals(3, Regex("Box\\(.*fillMaxSize\\(\\)\\.background\\(ConversationDrawerBaseSurface\\)\\)").findAll(workspaceSource).count())
    }

    @Test
    fun `drawer quick actions retain their own neutral surfaces and readable centered text`() {
        val drawer = workspaceSource.substring(
            workspaceSource.indexOf("private fun ConversationNavigationDrawer"),
            workspaceSource.indexOf("private fun WorkProjectNavigationDrawer"),
        )
        val conversationRow = workspaceSource.substring(
            workspaceSource.indexOf("private fun ConversationNavigationRow"),
            workspaceSource.indexOf("private fun ConversationRowSwipeActions"),
        )
        assertTrue(drawer.contains("color = ConversationDrawerQuickActionSurface,\n                    shape = P5AInteractiveShape,"))
        assertTrue(drawer.contains("color = ConversationDrawerQuickActionSurface,\n                    shape = P5AInteractiveShape,\n                    modifier = Modifier.fillMaxWidth().height(42.dp)"))
        assertTrue(drawer.contains("horizontalArrangement = Arrangement.Center,"))
        assertTrue(drawer.contains("Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(scaledAppIconSize(17.dp)), tint = BodyText"))
        assertTrue(drawer.contains("\"搜索\",\n                            color = BodyText,\n                            fontSize = scaledConversationTextUnit(16.sp)"))
        assertTrue(drawer.contains("\"已计划\",\n                            color = BodyText,\n                            fontSize = scaledConversationTextUnit(16.sp)"))
        assertTrue(conversationRow.contains("Text(conversation.title, modifier = Modifier.weight(1f), fontSize = scaledConversationTextUnit(14.sp), lineHeight = scaledConversationTextUnit(18.sp), fontWeight = FontWeight.Normal"))
    }
}
