package com.nanzhufeng.ai.ui

import com.nanzhufeng.ai.domain.AssistantResponseModelAttribution
import com.nanzhufeng.ai.domain.ContextBudget
import com.nanzhufeng.ai.domain.ContextSelectionAuditRecord
import com.nanzhufeng.ai.domain.ContextSelectionSource
import com.nanzhufeng.ai.domain.ConversationStyle
import com.nanzhufeng.ai.domain.LocalContextBroker
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.NormalChatSendAttemptId
import com.nanzhufeng.ai.domain.ProviderId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerInformationDisclosureBehaviorTest {
    @Test fun `durable answer facts win over fallback context and sources exclude style with stable deduplication`() {
        val attribution = AssistantResponseModelAttribution(
            assistantMessageId = MessageNodeId("answer-info-message"),
            attemptId = NormalChatSendAttemptId("answer-info-attempt"),
            providerId = ProviderId.OPENROUTER,
            receiverProviderId = ProviderId.OPENROUTER,
            modelId = "openai/gpt-5.6",
            modelDisplayName = "GPT-5.6",
            recordedAt = Instant.EPOCH,
            conversationStyle = ConversationStyle.PROFESSIONAL,
            webSearchUsed = false,
        )
        val audits = listOf(
            audit(
                webSearchUsed = true,
                sources = listOf(
                    ContextSelectionSource("对话风格", "style", "直言不讳", 5),
                    ContextSelectionSource("记忆", "memory-1", "已确认偏好", 8),
                    ContextSelectionSource("记忆", "memory-1", "重复标题", 8),
                ),
            ),
        )

        val disclosure = audits.answerInformationDisclosure(listOf(attribution))

        assertEquals("专业可靠", disclosure.styleLabel)
        assertEquals(false, disclosure.webSearchUsed)
        assertEquals(listOf("记忆" to "memory-1"), disclosure.sources.map { it.kind to it.stableId })
    }

    @Test fun `legacy answer uses bound context fallback and otherwise remains explicitly unknown`() {
        val fallback = listOf(
            audit(
                webSearchUsed = true,
                sources = listOf(ContextSelectionSource("对话风格", "style", "高效务实", 5)),
            ),
        ).answerInformationDisclosure(emptyList())
        assertEquals("高效务实", fallback.styleLabel)
        assertEquals(true, fallback.webSearchUsed)

        val unknown = emptyList<ContextSelectionAuditRecord>().answerInformationDisclosure(emptyList())
        assertNull(unknown.styleLabel)
        assertNull(unknown.webSearchUsed)
        assertEquals(emptyList<Any>(), unknown.sources)
    }

    @Test fun `source groups retain actual titles and profile fields without inventing absent sources`() {
        val disclosure = listOf(audit(null, listOf(
            ContextSelectionSource("个性化资料", "profile", "昵称、职业／角色", 0),
            ContextSelectionSource("自定义指令", "instructions", "已保存的自定义指令", 0),
            ContextSelectionSource("知识库", "knowledge-1", "跨端开发规范", 3),
            ContextSelectionSource("知识库", "knowledge-2", "第二份资料", 3),
            ContextSelectionSource("记忆", "memory", "长期 Memory", 3),
            ContextSelectionSource("当前对话路径", "path", "此前 8 条消息", 3),
        ))).answerInformationDisclosure(emptyList())
        assertEquals(listOf(
            AnswerContextSourceGroup("个性化资料", listOf("昵称", "职业／角色")),
            AnswerContextSourceGroup("自定义指令", listOf("已保存的自定义指令")),
            AnswerContextSourceGroup("资料库", listOf("跨端开发规范", "第二份资料")),
            AnswerContextSourceGroup("长期记忆", listOf("长期 Memory")),
            AnswerContextSourceGroup("当前对话路径", listOf("此前 8 条消息")),
        ), disclosure.sources.answerContextSourceGroups())
        assertEquals(emptyList<AnswerContextSourceGroup>(), emptyList<com.nanzhufeng.ai.domain.AnswerContextSourceDisclosure>().answerContextSourceGroups())
    }

    private fun audit(
        webSearchUsed: Boolean?,
        sources: List<ContextSelectionSource>,
    ) = ContextSelectionAuditRecord(
        createdAt = Instant.EPOCH,
        providerId = ProviderId.OPENROUTER,
        modelId = "openai/gpt-5.6",
        tokenizerId = "heuristic-v1",
        budget = ContextBudget(8_192, 1_024, 4_096, 2_048, 2_048),
        selectedSources = sources,
        indexStatus = LocalContextBroker.AssemblyStatus.READY,
        webSearchUsed = webSearchUsed,
    )
}
