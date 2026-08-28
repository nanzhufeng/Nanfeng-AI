package com.nanzhufeng.ai.domain

import java.time.Instant

data class ContextSelectionSource(val kind: String, val stableId: String, val title: String, val estimatedTokens: Int)

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
    val noAdditionalSourceExplanation: String,
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
                    "记忆" -> "已启用记忆；该条内容按本次问题和当前对话范围在本机匹配。"
                    "知识库" -> "已启用资料库搜索；该条资料按本次问题和当前项目范围在本机匹配。"
                    "历史对话" -> "该普通历史对话按本次问题和当前项目范围在本机匹配，用于补充上下文。"
                    else -> "该本地资料按本次问题和当前对话范围在本机匹配。"
                },
            )
        }
    return AnswerContextDisclosure(
        sources = sources,
        noAdditionalSourceExplanation = "本次未加入记忆、资料库或历史对话；仅使用本轮输入、当前对话路径及固定系统规则。",
    )
}
