package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ordinary chat is one direct path: commit locally, render, then make the configured request. */
class P3JNormalChatExplicitEgressContractsTest {
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
    private val executor = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()
    private val appContainer = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()
    private val activity = File("src/main/java/com/nanzhufeng/ai/NanfengAiActivity.kt").readText()

    @Test fun `ordinary send has no confirmation owner or second composer path`() {
        assertTrue(viewModel.contains("normalChatOpenRouterExecutor.execute("))
        assertTrue(viewModel.contains("reload(keepSending = true)"))
        assertFalse(workspace.contains("NormalChatExplicitEgressConfirmationDialog"))
        assertFalse(workspace.contains("externalSendConfirmation"))
        assertFalse(viewModel.contains("requestNormalChatExternalSendConfirmation"))
        assertFalse(viewModel.contains("NormalChatRealTextExecutionOwner"))
    }

    @Test fun `direct send commits exact draft and makes only its remaining attachments eligible`() {
        assertTrue(viewModel.contains("draftMutationMutex.withLock"))
        assertTrue(executor.contains("submitDraft.execute(conversationId)"))
        assertTrue(executor.contains("onLocalSubmission()"))
        assertTrue(executor.contains("ATTACHMENTS_UNSUPPORTED"))
        assertTrue(executor.contains("Only attachments still referenced by the exact submitted draft"))
        assertTrue(executor.contains("file_data"))
        assertTrue(executor.contains("video_url"))
        assertTrue(executor.contains("attachmentStore.openVerified(asset)"))
        assertFalse(executor.contains("attachmentStore.read(asset)"))
        assertFalse(executor.contains("attachmentStore.pdfPage(asset, 1)"))
        assertFalse(executor.contains("attachmentStore.videoPreview(asset)"))
        assertTrue(executor.contains("credentials.loadCredential(executionProviderId)"))
        assertTrue(executor.contains("selection.readConversationOverride(conversationId).modelId\n            ?: selection.readGlobalDefault().modelId"))
        assertTrue(executor.contains("val automatic = routingPolicy.autoRoutingEnabled && (selectedId == null || choice == ComposerModelRoutingCatalog.auto)"))
        assertTrue(executor.contains("val presets = if (automatic) listOf(autoPreset) else choice.routes"))
        assertFalse(executor.contains("automaticFallbackOrder"))
        assertTrue(executor.contains("fun cancelActive(conversationId: ConversationId)"))
        assertTrue(viewModel.contains("normalChatOpenRouterExecutor.cancelActive(visibleRuntime.conversationId)"))
    }

    @Test fun `composer structure stays anchored while its only model entry opens the menu`() {
        val submitLambda = workspace.substringAfter("onSubmit = {").substringBefore("onStop = onStop")
        assertTrue(submitLambda.contains("onSubmitDraft()"))
        assertTrue(workspace.contains("private val ComposerSendSurfaceSize = 36.dp"))
        assertTrue(workspace.contains("ComposerModelEntry("))
        assertTrue(workspace.contains("state.p6gConversationOverride?.modelId ?: state.p6gGlobalDefault.modelId"))
        assertFalse(workspace.contains("ComposerCompareEntry("))
    }

    @Test fun `unknown provider result has an explicit original key retry or mark failed decision`() {
        assertTrue(executor.contains("fun retryLatestAttempt(conversationId: ConversationId)"))
        assertTrue(executor.contains("existingAttempt = attempt"))
        assertTrue(executor.contains("attempt.idempotencyKey"))
        assertTrue(executor.contains("fun markLatestAttemptFailed"))
        assertTrue(workspace.contains("按原编号重试"))
        assertTrue(workspace.contains("标记失败"))
        assertTrue(workspace.contains("可能重复调用或扣费"))
        val retry = executor.substringAfter("fun retryLatestAttempt(conversationId: ConversationId)").substringBefore("fun markLatestAttemptFailed")
        assertTrue(retry.contains("ProviderChatCancellation().also { activeCalls[conversationId] = it }"))
        assertTrue(retry.contains("activeCalls.remove(conversationId, cancellation)"))
    }

    @Test fun `mixed non streaming text and tool call never claims a completed answer`() {
        assertTrue(executor.contains("val toolCallEncountered = !requestOptions.liveWebSearch && (toolOnly != null || reply?.toolCallEncountered == true)"))
        assertTrue(executor.contains("when { toolCallEncountered -> \"TOOL_CALL_UNSUPPORTED\""))
    }

    @Test fun `normal streaming completion does not silently clip a model reply`() {
        assertFalse(executor.contains(".take(12_000)"))
    }

    @Test fun `deep OpenRouter requests distinguish a live web tool from mere provider connectivity`() {
        assertTrue(executor.contains("val requestedOptions = adapter.requestOptions(resolvedModel, choice)"))
        assertTrue(executor.contains("ChatRequestOptions(OfficialWebSearchRoute.QWEN_RESPONSES)"))
        assertTrue(executor.contains("val systemFact = systemFactForRequest(requestOptions)"))
        assertTrue(executor.contains("当前本机日期为 \$date"))
        assertTrue(executor.contains("OpenRouter 官方实时网页检索"))
        assertTrue(executor.contains("千问官方 Responses 实时网页检索（回答模型为 DeepSeek V4 Pro）"))
        assertTrue(executor.contains("不得把训练数据截止时间说成当前日期"))
        assertTrue(executor.contains("webSearchRoute=\${options.webSearchRoute.name}"))
        assertTrue(workspace.contains("OpenRouter · 官方实时联网检索"))
        assertTrue(workspace.contains("DeepSeek 回答 · 千问官方实时检索"))
    }

    @Test fun `interrupted attempt recovery never writes Room during AppContainer construction and refreshes its owner`() {
        assertFalse(appContainer.contains("RoomNormalChatSendAttemptStore(database).also"))
        assertTrue(appContainer.contains("fun recoverInterruptedNormalChatAttemptsAfterProcessStart"))
        assertTrue(activity.contains("val recoveredAttempts = withContext(Dispatchers.IO)"))
        assertTrue(activity.contains("recoverInterruptedNormalChatAttemptsAfterProcessStart()"))
        assertTrue(activity.contains("if (recoveredAttempts > 0) conversationFoundationViewModel.reload()"))
    }
}
