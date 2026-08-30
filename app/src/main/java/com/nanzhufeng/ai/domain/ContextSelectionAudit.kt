package com.nanzhufeng.ai.domain

import java.time.Instant

data class ContextSelectionSource(
    val kind: String,
    val stableId: String,
    val title: String,
    val estimatedTokens: Int,
    /** True only when the selected conversation/Memory/Knowledge candidate passed a model relevance check. */
    val selectedByModel: Boolean = false,
)

/** Metadata only: records a high-confidence topic's local retrieval outcome without retaining query text or bodies. */
data class ContextRetrievalAudit(
    val topic: String,
    val memorySearched: Boolean,
    val selectedMemoryCount: Int,
    val knowledgeSearched: Boolean,
    val selectedKnowledgeCount: Int,
)

data class ContextSelectionAuditRecord(
    val createdAt: Instant,
    /** The local conversation that produced this selection; legacy records may not have it. */
    val conversationId: String? = null,
    /** The send attempt that assembled this context; legacy records may not have it. */
    val attemptId: NormalChatSendAttemptId? = null,
    /** Bound only after a visible assistant response has been persisted. */
    val assistantMessageId: MessageNodeId? = null,
    val providerId: ProviderId,
    val modelId: String,
    val tokenizerId: String,
    val budget: ContextBudget,
    val selectedSources: List<ContextSelectionSource>,
    val indexStatus: LocalContextBroker.AssemblyStatus = LocalContextBroker.AssemblyStatus.READY,
    /** Older records did not capture direct profile/current-path participation. */
    val participationAuditAvailable: Boolean = false,
    val retrievalAudit: ContextRetrievalAudit? = null,
)

/** Local debugging only. Bodies, prompts, attachments, responses and credentials are forbidden. */
interface ContextSelectionAuditStore {
    fun append(record: ContextSelectionAuditRecord)
    /** Associates the newest unbound context assembly for an attempt with its visible answer. */
    fun bindAnswer(attemptId: NormalChatSendAttemptId, assistantMessageId: MessageNodeId)
    /** Answer-level projection. Context diagnostics that never produced an answer stay hidden here. */
    fun forAssistantMessages(messageIds: Collection<MessageNodeId>): Map<MessageNodeId, List<ContextSelectionAuditRecord>>
    fun recent(limit: Int = 8): List<ContextSelectionAuditRecord>
}

/** Content-free, answer-level explanation; selected source bodies are deliberately never shown. */
data class AnswerContextSourceDisclosure(
    val kind: String,
    val stableId: String,
    val title: String,
    val whyUsed: String,
)

data class AnswerContextDisclosure(
    val sources: List<AnswerContextSourceDisclosure>,
)

fun ContextSelectionAuditRecord.answerContextDisclosure(): AnswerContextDisclosure {
    val sources = selectedSources
        .distinctBy { "${it.kind}\u0000${it.stableId}" }
        .map { source ->
            AnswerContextSourceDisclosure(
                kind = source.kind,
                stableId = source.stableId,
                title = source.title,
                whyUsed = when (source.kind) {
                    "记忆" -> if (source.selectedByModel) "先由本机索引召回候选，再由本轮模型判断其与问题相关。" else "已启用记忆；该条内容按本次问题和当前对话范围在本机匹配。"
                    "知识库" -> if (source.selectedByModel) "先由本机索引召回候选，再由本轮模型判断其与问题相关。" else "已启用资料库搜索；该条资料按本次问题和当前项目范围在本机匹配。"
                    "历史对话" -> if (source.selectedByModel) "先由本机历史索引召回候选，再由本轮模型判断其与问题相关。" else "该普通历史对话按本次问题和当前项目范围在本机匹配，用于补充上下文。"
                    "当前对话路径" -> "当前对话中的此前消息已随本次请求发送。"
                    "个性化资料" -> "已使用设置中的个性化资料。"
                    "自定义指令" -> "已使用已保存的自定义指令。"
                    "对话风格" -> "已使用已选的对话风格。"
                    else -> "该本地资料按本次问题和当前对话范围在本机匹配。"
                },
            )
        }
    return AnswerContextDisclosure(
        sources = sources,
    )
}
