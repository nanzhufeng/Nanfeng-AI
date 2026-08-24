package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MMO4HCompareVisibleEntryContractsTest {
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val executor = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()

    @Test fun `compare is one of the five logical model slots not a second composer action`() {
        for (token in listOf("ComposerModelSlot.COMPARE", "ComposerModelSlot.DAILY", "ComposerModelSlot.DEEP", "ComposerModelSlot.MULTIMODAL", "label = \"自动\"")) {
            assertTrue("missing $token", workspace.contains(token))
        }
        assertFalse(workspace.contains("ComposerCompareEntry("))
        assertFalse(workspace.contains("onRequestCompare"))
    }

    @Test fun `selected compare uses the same ordinary send executor and saves both labelled replies`() {
        assertTrue(executor.contains("if (choice.isCompare)"))
        assertTrue(executor.contains("replies.joinToString"))
        assertTrue(executor.contains("replies.size != choice.routes.size"))
    }
}
