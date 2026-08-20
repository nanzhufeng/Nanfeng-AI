package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the P3-J root-overlay contract and the default fail-closed normal-chat owner. */
class P3JNormalChatExplicitEgressContractsTest {
    private val contract = File("../docs/P3J_NORMAL_CHAT_EXPLICIT_EGRESS_CONFIRMATION_CONTRACT.md").readText()
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
    private val appContainer = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()

    private val owner = File("src/main/java/com/nanzhufeng/ai/domain/NormalChatRealTextExecutionOwner.kt").readText()

    @Test fun `normal chat keeps submit local and isolates the unregistered confirmation owner from the composer`() {
        assertTrue(workspace.contains("onSubmit = {"))
        assertTrue(viewModel.contains("submitDraft.execute(id)"))
        assertTrue(viewModel.contains("未连接 Provider"))
        assertFalse(workspace.contains("onRequestNormalChatExternalSendConfirmation()"))
        assertTrue(viewModel.contains("requestNormalChatExternalSendConfirmation"))
        assertTrue(appContainer.contains("normalChatRealTextExecutionOwner = NormalChatRealTextExecutionOwner(clock)"))
        assertTrue(owner.contains("class NormalChatRealTextExecutionOwner"))
        assertTrue(owner.contains("There is intentionally no confirm/send API"))
        assertFalse(owner.contains("ProviderTransport"))
        assertFalse(owner.contains("Credential"))
        assertFalse(owner.contains("Http"))
        assertFalse(owner.contains("UsageLedger"))
        assertTrue(appContainer.contains("p2mRealTextExecutionBridge"))
    }

    @Test fun `composer submit wiring changes no layout structure or frozen surface tokens`() {
        val submitLambda = workspace.substringAfter("onSubmit = {").substringBefore("onStop = onStop")
        assertTrue(submitLambda.contains("onSubmitDraft()"))
        assertFalse(submitLambda.contains("onRequestNormalChatExternalSendConfirmation"))
        assertTrue(submitLambda.contains("workSentFromMessageCount"))
        assertTrue(submitLambda.contains("chatSentFromMessageCount"))
        // The permitted fix is callback removal only. These guard the existing composer structure and surface.
        for (required in listOf(
            "Box(\n                        modifier = Modifier\n                            .align(Alignment.BottomCenter)",
            "padding(start = 18.dp, end = 18.dp, bottom = 6.dp)",
            "private val ComposerSendSurfaceSize = 36.dp",
            "DraftComposer(",
        )) assertTrue(required, workspace.contains(required))
    }

    @Test fun `contract requires explicit scope bound consent and blocks attachment egress`() {
        for (required in listOf(
            "唯一生产编排 owner 必须是新增的", "未勾选的、一次性的显式确认框",
            "超过 **5 分钟**", "P3-J 为 **text-only**", "ATTACHMENTS_NOT_SUPPORTED",
            "不自动重试、后台续发", "NormalChatRealTextExecutionOwner",
            "P3-I prepare + P0 reserve", "P3 普通聊天 egress 保持\n未注册且 fail-closed",
        )) assertTrue(required, contract.contains(required))
    }

    @Test fun `confirmation is a root sibling and does not alter frozen composer or drawer geometry`() {
        assertTrue(workspace.contains("private fun NormalChatExplicitEgressConfirmationDialog"))
        assertTrue(workspace.contains("state.externalSendConfirmation?.let"))
        assertTrue(workspace.contains("root sibling of the workspace"))
        assertTrue(workspace.contains("private val ComposerSendSurfaceSize = 36.dp"))
        assertTrue(workspace.contains("modifier = Modifier.fillMaxSize().padding(18.dp)"))
        assertTrue(workspace.contains("ModalNavigationDrawer("))
        assertTrue(workspace.contains("附件不会发送"))
        assertTrue(workspace.contains("确认默认未勾选，并将在 5 分钟后过期"))
        assertTrue(workspace.contains("enabled = confirmation.acknowledgementChecked && confirmation.isConfirmable && !expired"))
    }
}
