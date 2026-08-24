package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalContextBrokerContractsTest {
    @Test fun `model budget carries its tokenizer identity into local selection`() {
        val model = ResolvedModel(
            ProviderId.QWEN, "model", "model", ModelCapabilities(true, true, true),
            32_768, ModelHealth.UNKNOWN, null, null, tokenizerId = "heuristic-v1",
        )
        assertEquals("heuristic-v1", ContextBudget.forModel(model).tokenizerId)
    }

    private val clock = Clock.fixed(Instant.parse("2026-08-23T05:00:00Z"), ZoneOffset.UTC)
    private val tree = ConversationTreeService(clock)

    @Test fun `every model receives the same locally ranked library memory and historical context`() {
        val current = tree.append(tree.create("当前"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("请根据迁移方案继续处理"))))
        val result = LocalContextBroker(
            FakeIndex(
                memories = listOf(hit("m", "记忆", "偏好", "迁移时保留本机数据")),
                knowledge = listOf(hit("k", "知识库", "迁移手册", "迁移方案需要先做完整性校验")),
                history = listOf(hit("h", "历史对话", "旧会话", "历史迁移方案：先备份再校验")),
            ),
        ).assemble(current, "请根据迁移方案继续处理")

        val outbound = result.messages.joinToString("\n") { it.text }
        assertTrue(outbound.contains("迁移时保留本机数据"))
        assertTrue(outbound.contains("迁移方案需要先做完整性校验"))
        assertTrue(outbound.contains("历史迁移方案"))
        assertTrue(outbound.contains("请根据迁移方案继续处理"))
        assertTrue(result.messages.any { it.role == MessageRole.SYSTEM })
    }

    @Test fun `attachments and tool output do not enter outbound context`() {
        val attachment = ConversationAttachmentReference(AttachmentId("a"), "image/png", "secret.png", 1, "a".repeat(64))
        val user = tree.append(tree.create("当前"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("正文"), ContentBlock.Attachment(attachment))))
        val current = tree.append(user, AppendMessageRequest(MessageRole.TOOL, listOf(ContentBlock.ToolResult("tool", "不能外发的工具正文"))))
        val result = LocalContextBroker(FakeIndex()).assemble(current, "正文")
        val outbound = result.messages.joinToString("\n") { it.text }
        assertFalse(outbound.contains("secret.png"))
        assertFalse(outbound.contains("不能外发的工具正文"))
    }

    @Test fun `retrieval is selected by token budget without character-cutting a record`() {
        val current = tree.append(tree.create("当前"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("迁移"))))
        val fullEntry = "完整资料必须保持完整，不能按字符截断。"
        val result = LocalContextBroker(FakeIndex(knowledge = listOf(
            hit("one", "知识库", "完整资料", fullEntry),
            hit("two", "知识库", "超额资料", "这条资料超过剩余预算，不应出现任何片段".repeat(10)),
        ))).assemble(current, "迁移", ContextBudget(200, 10, 100, 10, 90))

        val outbound = result.messages.joinToString("\n") { it.text }
        assertTrue(outbound.contains(fullEntry))
        assertFalse(outbound.contains("不应出现任何片段"))
    }

    @Test fun `current user turn is mandatory and oversized input is never reduced into a sendable context`() {
        val current = tree.append(tree.create("当前"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("必须完整保留"))))
        val broker = LocalContextBroker(FakeIndex(knowledge = listOf(hit("one", "知识库", "相关", "不应在超额时外发"))))

        val fit = broker.assemble(current, "必须完整保留", ContextBudget(128, 16, 112, 44, 68), attachmentInputTokens = 20)
        assertTrue(fit.messages.last().text.contains("必须完整保留"))
        val overflow = broker.assemble(current, "超额".repeat(100), ContextBudget(128, 16, 112, 44, 68))
        assertTrue(overflow.status == LocalContextBroker.AssemblyStatus.INPUT_TOO_LARGE)
        assertTrue(overflow.messages.isEmpty())
    }

    @Test fun `identical retrieval bodies are deduplicated across local stores`() {
        val current = tree.append(tree.create("当前"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("迁移"))))
        val duplicate = "先备份再迁移"
        val result = LocalContextBroker(FakeIndex(
            memories = listOf(hit("m", "记忆", "迁移规则", duplicate)),
            knowledge = listOf(hit("k", "知识库", "迁移规则", duplicate)),
        )).assemble(current, "迁移")

        assertTrue(result.selectedSources.size == 1)
    }

    private fun hit(id: String, kind: String, title: String, body: String) = LocalContextIndexHit(id, kind, title, body, clock.instant().toEpochMilli(), -1.0)

    private class FakeIndex(
        private val memories: List<LocalContextIndexHit> = emptyList(),
        private val knowledge: List<LocalContextIndexHit> = emptyList(),
        private val history: List<LocalContextIndexHit> = emptyList(),
    ) : LocalContextIndex {
        override fun searchActiveMemories(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int) = memories.take(limit)
        override fun searchActiveKnowledge(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int) = knowledge.take(limit)
        override fun searchActiveHistory(queryTerms: Set<String>, scope: ContextRetrievalScope, limit: Int) = history.take(limit)
        override fun activeKnowledgeCount() = knowledge.size
        override fun status() = LocalContextIndexStatus.AVAILABLE
    }
}
