package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MMO4HCompareVisibleEntryContractsTest {
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val routing = File("src/main/java/com/nanzhufeng/ai/domain/ChatModelRouting.kt").readText()
    private val executor = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()

    @Test fun `compare is removed from the public model catalog and composer picker`() {
        val publicSlotsStart = workspace.indexOf("listOf(\n                        com.nanzhufeng.ai.domain.ComposerModelSlot.DAILY")
        val publicSlots = workspace.substring(publicSlotsStart, workspace.indexOf(").forEach { slot ->", publicSlotsStart))
        assertFalse(publicSlots.contains("ComposerModelSlot.COMPARE"))
        assertFalse(routing.contains("logical:compare:"))
        assertFalse(workspace.contains("ComposerCompareEntry("))
        assertFalse(workspace.contains("onRequestCompare"))
    }

    @Test fun `selected compare uses the same ordinary send executor and saves both labelled replies`() {
        assertTrue(executor.contains("if (choice.isCompare)"))
        assertTrue(executor.contains("val renderedBlocks = if (choice.isCompare)"))
        assertTrue(executor.contains("NanfengModelServiceCatalog.preset(preset).displayName"))
        assertTrue(executor.contains("reply.reasoning?.let { add(ContentBlock.Reasoning(it)) }"))
        assertTrue(executor.contains("replies.size != choice.routes.size"))
    }
}
