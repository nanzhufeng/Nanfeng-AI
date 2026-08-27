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
    private val adapters = File("src/main/java/com/nanzhufeng/ai/ai/ChatProviderAdapters.kt").readText()
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
        assertTrue(executor.contains("asset.mimeType.startsWith(\"audio/\") -> ChatAttachmentKind.AUDIO"))
        assertTrue(executor.contains("else -> ChatAttachmentKind.FILE"))
        assertTrue(adapters.contains("input_audio"))
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

    @Test fun `unknown provider result keeps the original key internally while showing one plain retry action`() {
        assertTrue(executor.contains("fun retryLatestAttempt(conversationId: ConversationId)"))
        assertTrue(executor.contains("existingAttempt = attempt"))
        assertTrue(executor.contains("attempt.idempotencyKey"))
        assertTrue(executor.contains("failureReason = recoveryFailureReason"))
        assertTrue(workspace.contains("Text(\"重试\")"))
        assertTrue(workspace.contains("recovery.failureReason"))
        assertFalse(workspace.contains("按原编号重试"))
        assertFalse(workspace.contains("标记失败"))
        assertFalse(workspace.contains("原接收方："))
        assertFalse(workspace.contains("可能重复调用或扣费"))
        val retry = executor.substringAfter("fun retryLatestAttempt(conversationId: ConversationId)").substringBefore("fun markLatestAttemptFailed")
        assertTrue(retry.contains("ProviderChatCancellation().also { call ->"))
        assertTrue(retry.contains("activeCalls[conversationId] = call"))
        assertTrue(retry.contains("if (cancellationRequested.remove(conversationId)) call.cancel()"))
        assertTrue(retry.contains("activeCalls.remove(conversationId, cancellation)"))
    }

    @Test fun `retry immediately closes the failure decision and restores a durable generation placeholder`() {
        val retry = executor.substringAfter("fun retryLatestAttempt(conversationId: ConversationId)").substringBefore("fun markLatestAttemptFailed")
        assertTrue(viewModel.contains("normalSendRecovery = null"))
        assertTrue(viewModel.contains("normalSendRetryInProgress = true"))
        assertTrue(workspace.contains("state.normalSendRecovery?.takeIf { !state.isSending }"))
        assertTrue(retry.contains("startProviderRuntimeForExistingUser.execute(conversationId, user.id)"))
        assertTrue(retry.contains("val resumedRuntime = ActiveProviderRuntime(restarted.runtime)"))
        assertTrue(workspace.contains("南枫AI 继续生成…"))
    }

    @Test fun `mixed non streaming text and tool call never claims a completed answer`() {
        assertTrue(executor.contains("val toolCallEncountered = !requestOptions.liveWebSearch && (toolOnly != null || reply?.toolCallEncountered == true)"))
        assertTrue(executor.contains("when { toolCallEncountered -> \"TOOL_CALL_UNSUPPORTED\""))
    }

    @Test fun `normal streaming completion does not silently clip a model reply`() {
        assertFalse(executor.contains(".take(12_000)"))
    }

    @Test fun `current-information OpenRouter requests distinguish a live web tool from model selection`() {
        assertTrue(executor.contains("val requestedOptions = ChatRequestOptions.Standard"))
        assertTrue(executor.contains("appendProviderWebSources(reply.text, reply.webSources)"))
        assertTrue(executor.contains("is VerifyOpenRouterRegistryResult.Unavailable -> Unit"))
        assertTrue(executor.contains("val attachmentFact = attachmentReferenceInstruction(attachments)"))
        assertTrue(executor.contains("EvidenceFirstAnalysisPolicy.modeFor(userMessage, attachments)"))
        assertTrue(executor.contains("val isFirstAssistantReply = snapshot.nodes.none"))
        assertTrue(executor.contains("systemFactForRequest(requestOptions, experience, fulfilledAnalysisMode, userMessage, isFirstAssistantReply)"))
        assertTrue(executor.contains("experience.modelInstruction(isFirstAssistantReply)"))
        assertTrue(executor.contains("当前本机日期为 \$date"))
        assertTrue(executor.contains("OpenRouter 官方实时网页检索"))
        assertTrue(executor.contains("千问官方 Responses 实时网页检索"))
        assertTrue(executor.contains("DeepSeek 官方 Responses 实时网页检索"))
        assertTrue(executor.contains("不得把训练数据截止时间说成当前日期"))
        assertTrue(executor.contains("webSearchRoute=\${options.webSearchRoute.name}"))
        assertTrue(executor.contains("recordResponseFormatDiagnostic"))
        assertTrue(workspace.contains("OpenRouter · 官方实时联网检索"))
        assertTrue(workspace.contains("DeepSeek · 官方实时联网检索"))
    }

    @Test fun `process recovery is service owned and Activity never changes an in flight task`() {
        assertFalse(appContainer.contains("RoomNormalChatSendAttemptStore(database).also"))
        assertFalse(appContainer.contains("fun recoverInterruptedNormalChatAttemptsAfterProcessStart"))
        assertFalse(activity.contains("recoverInterruptedNormalChatAttemptsAfterProcessStart()"))
        assertTrue(activity.contains("GenerationForegroundService.  This Activity"))
    }
}
