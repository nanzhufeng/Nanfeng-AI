package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the narrow MM-O4-H visual/interaction contract without starting a Provider. */
class MMO4HCompareVisibleEntryContractsTest {
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

    @Test fun `model menu adds one explicit Compare choice without changing existing option row geometry`() {
        assertTrue(workspace.contains("对比 ChatGPT + Claude"))
        assertTrue(workspace.contains("52.dp * (modelOptions.size + if (onSelectCompare == null) 0 else 1) + 16.dp"))
        assertTrue(workspace.contains("modelOptions.forEach"))
        assertTrue(workspace.contains("onSelectCompare = { composerMenu = ComposerMenu.NONE; onRequestCompare() }"))
    }

    @Test fun `explicit Compare executes without a second product confirmation dialog`() {
        for (token in listOf("CompareExplicitEgressConfirmationDialog", "确认 Compare 外发", "确认并调用", "onExpireCompare")) {
            assertFalse(token, workspace.contains(token))
        }
        assertTrue(viewModel.contains("compareVisibleExecutionOwner.execute(conversationId, draft)"))
    }

    @Test fun `blank Compare draft returns before the execution owner is touched`() {
        val requestCompare = viewModel
            .substringAfter("fun requestCompareChatGptAndClaude()")
            .substringBefore("fun onConversationPhotoPickerResult")
        val blankGuard = requestCompare.indexOf("draft.text.isBlank()) return")
        val execution = requestCompare.indexOf("compareVisibleExecutionOwner.execute(conversationId, draft)")

        assertTrue(blankGuard >= 0)
        assertTrue(execution >= 0)
        assertTrue(blankGuard < execution)
    }

    @Test fun `ordinary submit stays local and Compare is a separate explicit callback`() {
        val submitLambda = workspace.substringAfter("onSubmit = {").substringBefore("onStop = onStop")
        assertTrue(submitLambda.contains("onSubmitDraft()"))
        assertFalse(submitLambda.contains("onRequestCompare"))
        assertTrue(viewModel.contains("fun requestCompareChatGptAndClaude()"))
        assertFalse(viewModel.contains("fun confirmCompare()"))
    }

    @Test fun `normal Composer Compare entry and long press preserve ordinary model tap`() {
        assertTrue(workspace.contains("ComposerCompareEntry(onClick = onRequestCompare)"))
        assertTrue(workspace.contains("onClick = onToggleModel"))
        assertTrue(workspace.contains("onLongClick = onRequestCompare"))
        assertTrue(workspace.contains("长按对比 ChatGPT + Claude"))
    }
}
