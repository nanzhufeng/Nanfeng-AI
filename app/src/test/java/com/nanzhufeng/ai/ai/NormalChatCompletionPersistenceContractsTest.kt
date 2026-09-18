package com.nanzhufeng.ai.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalChatCompletionPersistenceContractsTest {
    private val executor = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()
    private val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
    private val background = File("src/main/java/com/nanzhufeng/ai/background/NormalChatBackgroundExecution.kt").readText()
    private val transcript = File("src/main/java/com/nanzhufeng/ai/domain/TranscriptPresentation.kt").readText()
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test fun `reasoning persistence is an optional post completion supplement`() {
        val completion = executor.indexOf("runtime?.complete(visibleReply)")
        val reasoningAfterCompletion = executor.indexOf("val reasoningRetained = runtime?.retainProviderContinuation", completion)
        assertTrue("canonical visible reply must complete atomically before optional reasoning is saved", completion >= 0 && reasoningAfterCompletion > completion)
        assertTrue(executor.contains("finalVisibleText = finalVisibleText"))
        assertTrue(executor.contains("REASONING_NOT_SAVED"))
        assertTrue(executor.contains("Retain provider reasoning only after the visible reply"))
    }

    @Test fun `response accounting and attempt failures have distinct user facing contracts`() {
        listOf("LOCAL_RESPONSE_PERSISTENCE", "LOCAL_ACCOUNTING_PERSISTENCE", "LOCAL_ATTEMPT_PERSISTENCE").forEach {
            assertTrue("missing precise local failure code: $it", executor.contains(it))
            assertTrue("missing user explanation for $it", viewModel.contains(it))
        }
        assertFalse("a same-question request must not be framed as forbidden", viewModel.contains("请先不要重复发送"))
        assertTrue(viewModel.contains("同题新请求"))
    }

    @Test fun `background completion notice remains non blocking after service ownership`() {
        assertTrue(background.contains("NOTICE:${'$'}{notice.name}"))
        assertTrue(viewModel.contains("toNormalChatBackgroundNoticeLabel"))
        assertTrue(viewModel.contains("normalChatCompletionNoticeLabel"))
    }

    @Test fun `completed answers persist independently of optional source evidence`() {
        val nonStreaming = executor.substringAfter("is ProviderChatOutcome.HttpResponse").substringBefore("is ProviderChatOutcome.StreamedResponse")
        val streaming = executor.substringAfter("is ProviderChatOutcome.StreamedResponse").substringBefore("ProviderChatOutcome.TimedOut")

        for (branch in listOf(nonStreaming, streaming)) {
            assertTrue(branch.contains("WebSearchGroundingPolicy.completedAuditStatus"))
            assertFalse(branch.contains("OneResult.Failed(Code.WEB_SEARCH_NO_SOURCES)"))
            assertTrue(branch.contains("runtime?.complete(visibleReply)"))
        }
        assertTrue(viewModel.contains("WEB_SEARCH_UNAVAILABLE"))
        assertTrue(viewModel.contains("本次未保存为完整回答"))
    }

    @Test fun `failed provider runtime stays visible inside the transcript after reload`() {
        assertTrue(transcript.contains("val safeErrorCode: String? = null"))
        assertTrue(transcript.contains("node.deliveryState == MessageDeliveryState.FAILED"))
        assertTrue(workspace.contains("AssistantGenerationFailure(transcript.metadata.safeErrorCode)"))
        assertTrue(workspace.contains("回答未完成"))
        assertTrue(workspace.contains("normalChatResultLabel(code, sent = true)"))
    }

    @Test fun `manual sibling preset uses the shared provider credential and keeps exact attribution`() {
        val requestOne = executor.substringAfter("private fun requestOne(").substringBefore("private fun recordResponseAttribution(")

        assertTrue(requestOne.contains("configuration.execute(executionProviderId)"))
        assertTrue(requestOne.contains("credentials.hasCredential(executionProviderId)"))
        assertFalse(requestOne.contains("config.settings.presetId"))
        assertTrue(requestOne.contains("modelId, resolvedModel.displayName"))
        assertTrue(executor.contains("receiverProviderId, reply.modelId, reply.modelDisplayName"))
    }
}
