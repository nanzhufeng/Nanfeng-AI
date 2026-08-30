package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerContextDisclosureContractsTest {
    @Test fun `answer disclosure keeps titles but never projects source bodies`() {
        val disclosure = ContextSelectionAuditRecord(
            createdAt = Instant.parse("2026-08-27T00:00:00Z"),
            conversationId = "conversation-1",
            attemptId = NormalChatSendAttemptId("attempt-1"),
            assistantMessageId = MessageNodeId("assistant-1"),
            providerId = ProviderId.OPENROUTER,
            modelId = "model-1",
            tokenizerId = "cl100k_base",
            budget = ContextBudget.safeDefault(),
            selectedSources = listOf(
                ContextSelectionSource("记忆", "memory-1", "用户偏好", 42),
                ContextSelectionSource("知识库", "knowledge-1", "项目规范", 84),
                ContextSelectionSource("历史对话", "history-1", "先前排错", 64),
                ContextSelectionSource("记忆", "memory-1", "用户偏好", 42),
            ),
        ).answerContextDisclosure()

        assertEquals(listOf("用户偏好", "项目规范", "先前排错"), disclosure.sources.map(AnswerContextSourceDisclosure::title))
        assertTrue(disclosure.sources.first().whyUsed.contains("已启用记忆"))
        assertTrue(disclosure.sources[1].whyUsed.contains("资料库搜索"))
        assertTrue(disclosure.sources[2].whyUsed.contains("普通历史对话"))
        assertEquals(3, disclosure.sources.size)
    }

    @Test fun `new participation audit distinguishes direct profile and current path from retrieval`() {
        val disclosure = ContextSelectionAuditRecord(
            createdAt = Instant.parse("2026-08-29T00:00:00Z"),
            providerId = ProviderId.OPENROUTER,
            modelId = "model-1",
            tokenizerId = "cl100k_base",
            budget = ContextBudget.safeDefault(),
            participationAuditAvailable = true,
            retrievalAudit = ContextRetrievalAudit("投资决策", memorySearched = true, selectedMemoryCount = 1, knowledgeSearched = true, selectedKnowledgeCount = 0),
            selectedSources = listOf(
                ContextSelectionSource("当前对话路径", "current-conversation-path", "此前 4 条消息", 0),
                ContextSelectionSource("个性化资料", "assistant-profile", "关注方向", 0),
                ContextSelectionSource("自定义指令", "assistant-custom-instructions", "已保存的自定义指令", 0),
            ),
        ).answerContextDisclosure()

        assertEquals(listOf("当前对话路径", "个性化资料", "自定义指令"), disclosure.sources.map(AnswerContextSourceDisclosure::kind))
        assertTrue(disclosure.sources[0].whyUsed.contains("此前消息"))
        assertTrue(disclosure.sources[1].whyUsed.contains("个性化资料"))
        assertTrue(disclosure.sources[2].whyUsed.contains("自定义指令"))
    }

    @Test fun `temporary isolation disables every normal context and automatic persistence route`() {
        assertEquals(false, TemporaryConversationIsolation.memoryRetrievalEnabled)
        assertEquals(false, TemporaryConversationIsolation.knowledgeRetrievalEnabled)
        assertEquals(false, TemporaryConversationIsolation.normalHistorySearchEnabled)
        assertEquals(false, TemporaryConversationIsolation.automaticTitleEnabled)
        assertEquals(false, TemporaryConversationIsolation.automaticMemorySummaryEnabled)
        assertEquals(false, TemporaryConversationIsolation.providerEgressEnabled)
        assertTrue(TemporaryConversationIsolation.entryNotice().contains("不会读取记忆或资料库"))
        assertTrue(TemporaryConversationIsolation.sentNotice().contains("不会写入记忆、资料库、搜索或自动沉淀"))
    }
}
