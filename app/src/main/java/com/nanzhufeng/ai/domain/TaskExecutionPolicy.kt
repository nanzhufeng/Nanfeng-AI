package com.nanzhufeng.ai.domain

import java.time.Clock

/**
 * P5-B's app-level execution inventory. It governs lifecycle and recovery only;
 * each adapter keeps its own task table, identity and domain meaning.
 */
enum class LocalTaskKind { MARKDOWN_IMPORT, JSON_IMPORT, PDF_TEXT_IMPORT, WEB_TEXT_SNAPSHOT, OFFLINE_EVAL, CONVERSATION_LOCAL_FIXTURE }
enum class LocalTaskOwner { MARKDOWN_ADAPTER, JSON_ADAPTER, PDF_TEXT_ADAPTER, WEB_TEXT_ADAPTER, OFFLINE_EVAL, CONVERSATION_RUNTIME }
enum class LocalTaskDispatcher { IO, DEFAULT }
enum class ProcessDeathDisposition { FAIL_INTERRUPTED, NO_RUNNING_RECORD }

data class LocalTaskExecutionRule(
    val kind: LocalTaskKind,
    val owner: LocalTaskOwner,
    val dispatcher: LocalTaskDispatcher,
    val processDeath: ProcessDeathDisposition,
    val requiresUserRetry: Boolean,
    val networkAllowedOnlyInForeground: Boolean,
    val permitsSystemBackgroundScheduling: Boolean = false,
)

object TaskExecutionPolicy {
    val rules = listOf(
        LocalTaskExecutionRule(LocalTaskKind.MARKDOWN_IMPORT, LocalTaskOwner.MARKDOWN_ADAPTER, LocalTaskDispatcher.IO, ProcessDeathDisposition.FAIL_INTERRUPTED, true, false),
        LocalTaskExecutionRule(LocalTaskKind.JSON_IMPORT, LocalTaskOwner.JSON_ADAPTER, LocalTaskDispatcher.IO, ProcessDeathDisposition.FAIL_INTERRUPTED, true, false),
        LocalTaskExecutionRule(LocalTaskKind.PDF_TEXT_IMPORT, LocalTaskOwner.PDF_TEXT_ADAPTER, LocalTaskDispatcher.DEFAULT, ProcessDeathDisposition.FAIL_INTERRUPTED, true, false),
        LocalTaskExecutionRule(LocalTaskKind.WEB_TEXT_SNAPSHOT, LocalTaskOwner.WEB_TEXT_ADAPTER, LocalTaskDispatcher.IO, ProcessDeathDisposition.FAIL_INTERRUPTED, true, true),
        LocalTaskExecutionRule(LocalTaskKind.OFFLINE_EVAL, LocalTaskOwner.OFFLINE_EVAL, LocalTaskDispatcher.DEFAULT, ProcessDeathDisposition.NO_RUNNING_RECORD, true, false),
        LocalTaskExecutionRule(LocalTaskKind.CONVERSATION_LOCAL_FIXTURE, LocalTaskOwner.CONVERSATION_RUNTIME, LocalTaskDispatcher.DEFAULT, ProcessDeathDisposition.FAIL_INTERRUPTED, true, false),
    )

    /** WorkManager is intentionally absent: every potentially long task needs user confirmation or cannot be replayed safely. */
    const val workManagerIntroduced = false
    fun rule(kind: LocalTaskKind): LocalTaskExecutionRule = requireNotNull(rules.firstOrNull { it.kind == kind })
}

data class TaskRecoveryAuditResult(
    val markdownInterrupted: Int,
    val jsonInterrupted: Int,
    val pdfInterrupted: Int,
    val webInterrupted: Int,
    val conversationInterrupted: Int,
) {
    val totalInterrupted: Int get() = markdownInterrupted + jsonInterrupted + pdfInterrupted + webInterrupted + conversationInterrupted
}

/** Runs once for every process creation. It never starts work, retries a network request, or writes Knowledge. */
class TaskRecoveryAudit(
    private val markdown: ManageMarkdownImportUseCase,
    private val json: ManageJsonKnowledgeImportUseCase,
    private val pdf: ManagePdfTextKnowledgeImportUseCase,
    private val web: ManageWebTextSnapshotUseCase,
    private val conversations: ConversationRepository,
    private val conversationList: ConversationListRepository,
    private val runtime: ConversationRuntimeRepository,
    private val applyRuntimeEvent: ApplyConversationRuntimeEventUseCase,
    private val clock: Clock,
) {
    fun recoverAfterProcessStart(): TaskRecoveryAuditResult {
        val conversationInterrupted = conversationList.list(ConversationListScope.ALL).count { conversation ->
            val state = runtime.stateFor(conversation.id) ?: return@count false
            if (state.status != ConversationRuntimeStatus.STREAMING) return@count false
            val result = applyRuntimeEvent.execute(
                RuntimeFailed(
                    eventId = AiRuntimeEventId.new(), invocationId = state.invocationId,
                    conversationId = state.conversationId, messageId = state.messageId,
                    sequence = state.nextExpectedSequence, emittedAt = clock.instant(),
                    safeErrorCode = "INTERRUPTED", security = AiRuntimeSecurityMetadata("LOCAL_PROCESS_RECOVERY"),
                ),
            )
            result is ConversationRuntimePersistenceResult.Applied || result is ConversationRuntimePersistenceResult.Replayed
        }
        return TaskRecoveryAuditResult(
            markdownInterrupted = markdown.recoverInterrupted(),
            jsonInterrupted = json.recoverInterrupted(),
            pdfInterrupted = pdf.recoverInterrupted(),
            webInterrupted = web.recoverInterrupted(),
            conversationInterrupted = conversationInterrupted,
        )
    }
}
