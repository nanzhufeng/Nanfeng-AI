package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RetrievalCostGroupingTest {
    private fun call(id: String, root: String? = "question", amount: Long? = 100000, role: String = "RETRIEVAL") = ChatCallAccounting(
        id, root, "conversation", role, ProviderId.OPENROUTER, "openai/gpt-6-luna", "openai/gpt-6-luna", "GPT-6 Luna",
        Instant.EPOCH, Instant.ofEpochMilli(20), "COMPLETED", ProviderUsage(100, 20, 120, 10, 5),
        if (amount == null) ProviderCost() else ProviderCost("fixture", "USD", amount),
        if (amount == null) null else ConversationCostSource.PROVIDER_RESPONSE,
    )

    @Test fun `six searches for one question become one row while answer stays separate`() {
        val searches = (1..6).map { call("search-$it") }
        val answer = call("answer", amount = 18087, role = "ANSWER").copy(
            providerId = ProviderId.RELAYCORE_CLAUDE, requestedModelId = "claude-sonnet-5-5", actualModelId = "claude-sonnet-5-5",
            modelDisplayName = "Sonnet 5.5 · Kiro正价", cost = ProviderCost("fixture", "CNY", 18087),
        )
        val attribution = AssistantResponseModelAttribution(MessageNodeId("message"), NormalChatSendAttemptId("question"),
            answer.providerId, answer.providerId, answer.requestedModelId, answer.modelDisplayName, Instant.EPOCH,
            accountingCalls = searches + answer)
        val rows = mergeConversationCostRecords(listOf(attribution), searches + answer)
        assertEquals(2, rows.size)
        val retrieval = rows.single { it.physicalCallRole == "RETRIEVAL" }
        assertEquals("¥4.032185", retrieval.footerCostLabel())
        assertEquals(600L, retrieval.usage.inputTokens)
        assertEquals(120L, retrieval.usage.outputTokens)
        assertEquals(6, retrieval.accountingCalls.size)
        assertEquals("¥0.018087", rows.single { it.physicalCallRole == "ANSWER" }.footerCostLabel())
        assertEquals(7, mergePhysicalCallCostRecords(listOf(attribution), searches + answer).size)
        assertEquals(100000L, searches.first().cost.totalMicros)
    }

    @Test fun `different questions channels models and unlinked history are never guessed into one group`() {
        val calls = listOf(call("a"), call("b", root = "other"), call("c", root = null), call("d", root = null),
            call("e").copy(requestedModelId = "openai/gpt-6.1-sol"), call("f").copy(modelDisplayName = "Different channel"))
        assertEquals(6, mergeConversationCostRecords(emptyList(), calls).size)
    }

    @Test fun `failed and pending search costs remain known plus unknown instead of zero`() {
        val calls = listOf(call("done"), call("failed", amount = null).copy(status = "NETWORK"),
            call("pending", amount = null).copy(status = "STREAM_PENDING", finishedAt = null))
        val row = mergeConversationCostRecords(emptyList(), calls).single()
        assertEquals("¥0.672031 + 未知", row.footerCostLabel())
        assertTrue(row.physicalCallStatus!!.contains("NETWORK"))
        assertTrue(row.physicalCallStatus!!.contains("STREAM_PENDING"))
        assertEquals(3, row.accountingCalls.size)
        assertEquals("¥0.672031", CnyMoneyDisplay.summaryLabel(row.ledgerCostFacts()))
        assertEquals(2, row.ledgerCostFacts().count { it.totalMicros == null })
    }

    @Test fun `known zero all unknown and overflowing tokens remain distinct`() {
        val zero = mergeConversationCostRecords(emptyList(), listOf(call("a", amount = 0), call("b", amount = 0))).single()
        assertEquals("¥0", zero.footerCostLabel())
        val unknown = mergeConversationCostRecords(emptyList(), listOf(call("c", amount = null), call("d", amount = null))).single()
        assertNull(unknown.footerCostLabel())
        val overflow = mergeConversationCostRecords(emptyList(), listOf(call("e").copy(usage = ProviderUsage(Long.MAX_VALUE)), call("f"))).single()
        assertNull(overflow.usage.inputTokens)
    }

    @Test fun `legacy attributions for different attempts of one message are not dropped`() {
        val legacy = AssistantResponseModelAttribution(MessageNodeId("same-reply"), NormalChatSendAttemptId("first"),
            ProviderId.OPENROUTER, ProviderId.OPENROUTER, "openai/gpt-6-luna", "GPT-6 Luna", Instant.EPOCH,
            cost = ProviderCost("fixture", "USD", 100000), costSource = ConversationCostSource.PROVIDER_RESPONSE)
        val rows = mergeConversationCostRecords(listOf(legacy, legacy.copy(attemptId = NormalChatSendAttemptId("retry"))), emptyList())
        assertEquals(2, rows.size)
    }
}
