package com.nanzhufeng.ai.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalChatCompletionPersistenceContractsTest {
    private val executor = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()
    private val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
    private val background = File("src/main/java/com/nanzhufeng/ai/background/NormalChatBackgroundExecution.kt").readText()

    @Test fun `reasoning persistence is an optional post completion supplement`() {
        val completion = executor.indexOf("runtime?.complete(visibleReply)")
        val reasoningAfterCompletion = executor.indexOf("val reasoningRetained = runtime?.retainReasoning", completion)
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

    @Test fun `manual sibling preset uses the shared provider credential and keeps exact attribution`() {
        val requestOne = executor.substringAfter("private fun requestOne(").substringBefore("private fun recordResponseAttribution(")

        assertTrue(requestOne.contains("configuration.execute(executionProviderId)"))
        assertTrue(requestOne.contains("credentials.hasCredential(executionProviderId)"))
        assertFalse(requestOne.contains("config.settings.presetId"))
        assertTrue(requestOne.contains("modelId, resolvedModel.displayName"))
        assertTrue(executor.contains("receiverProviderId, reply.modelId, reply.modelDisplayName"))
    }
}
