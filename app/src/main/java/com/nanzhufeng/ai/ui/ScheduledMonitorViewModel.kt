package com.nanzhufeng.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.data.AndroidScheduledMonitorScheduler
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ScheduledMonitorCadence
import com.nanzhufeng.ai.domain.ScheduledMonitorRepository
import com.nanzhufeng.ai.domain.ScheduledMonitorSuggestion
import com.nanzhufeng.ai.domain.ScheduledMonitorDraftSource
import com.nanzhufeng.ai.domain.ReminderDraftRefiner
import com.nanzhufeng.ai.domain.ReminderDraftRefinementResult
import com.nanzhufeng.ai.domain.ScheduledMonitorStatus
import com.nanzhufeng.ai.domain.ScheduledMonitorTask
import com.nanzhufeng.ai.domain.ScheduledMonitorTaskId
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScheduledMonitorDraft(
    val title: String = "",
    val instruction: String = "",
    val cadence: ScheduledMonitorCadence = ScheduledMonitorCadence.DAILY,
    val sourceConversationId: ConversationId? = null,
)

data class ScheduledMonitorUiState(
    val visible: Boolean = false,
    val creating: Boolean = false,
    val refining: Boolean = false,
    val tasks: List<ScheduledMonitorTask> = emptyList(),
    val selectedTaskId: ScheduledMonitorTaskId? = null,
    val draft: ScheduledMonitorDraft = ScheduledMonitorDraft(),
    val notice: String? = null,
)

/** UI adapter for user-created monitors. A completed turn may produce a local editable draft;
 * neither transcript nor attachments are persisted here or sent by this class. */
class ScheduledMonitorViewModel(
    private val repository: ScheduledMonitorRepository,
    private val scheduler: AndroidScheduledMonitorScheduler,
    private val reminderDraftRefiner: ReminderDraftRefiner,
    private val clock: Clock,
) : ViewModel() {
    var state by mutableStateOf(ScheduledMonitorUiState())
        private set

    fun open(taskId: ScheduledMonitorTaskId? = null) {
        state = state.copy(visible = true, selectedTaskId = taskId ?: state.selectedTaskId)
        reload()
    }

    fun dismiss() { state = state.copy(visible = false, creating = false, refining = false, notice = null) }

    fun startCreate(
        sourceConversationId: ConversationId? = null,
        suggestedTitle: String = "",
        suggestedInstruction: String = "",
        suggestedCadence: ScheduledMonitorCadence = ScheduledMonitorCadence.DAILY,
    ) {
        state = state.copy(
            visible = true,
            creating = true,
            refining = false, notice = null,
            draft = ScheduledMonitorDraft(
                title = suggestedTitle.ifBlank { "新的监控" },
                instruction = suggestedInstruction,
                cadence = suggestedCadence,
                sourceConversationId = sourceConversationId,
            ),
        )
    }

    fun cancelCreate() { state = state.copy(creating = false, notice = null) }
    fun startCreate(sourceConversationId: ConversationId?, suggestion: ScheduledMonitorSuggestion) = startCreate(
        sourceConversationId = sourceConversationId,
        suggestedTitle = suggestion.title,
        suggestedInstruction = suggestion.instruction,
        suggestedCadence = suggestion.cadence,
    )

    /** The user tapped the conversation-tail action and explicitly authorized this narrow Qwen call. */
    fun refineConversationReminder(sourceConversationId: ConversationId?, source: ScheduledMonitorDraftSource) {
        if (sourceConversationId == null) return
        state = state.copy(visible = true, creating = true, refining = true, notice = null)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { reminderDraftRefiner.refine(sourceConversationId, source) }) {
                is ReminderDraftRefinementResult.Draft -> state = state.copy(
                    refining = false,
                    draft = ScheduledMonitorDraft(result.suggestion.title, result.suggestion.instruction, result.suggestion.cadence, sourceConversationId),
                )
                ReminderDraftRefinementResult.NotEligible -> state = state.copy(
                    creating = false, refining = false, notice = "这段讨论没有明确、可持续的监控目标，未创建提醒草案。",
                )
                is ReminderDraftRefinementResult.Failed -> state = state.copy(
                    creating = false, refining = false, notice = "提醒草案整理未完成：${result.safeCode}。请检查千问配置后重试。",
                )
            }
        }
    }

    fun updateTitle(value: String) { state = state.copy(draft = state.draft.copy(title = value.take(20))) }
    fun updateInstruction(value: String) { state = state.copy(draft = state.draft.copy(instruction = value.take(8_000))) }
    fun updateCadence(value: ScheduledMonitorCadence) { state = state.copy(draft = state.draft.copy(cadence = value)) }

    fun create() {
        val draft = state.draft
        if (draft.title.isBlank() || draft.instruction.isBlank()) {
            state = state.copy(notice = "请写清任务名称和监控要求。")
            return
        }
        viewModelScope.launch {
            val now = clock.instant()
            val task = ScheduledMonitorTask(
                id = ScheduledMonitorTaskId.new(),
                title = draft.title.trim().take(20),
                instruction = draft.instruction.trim(),
                sourceConversationId = draft.sourceConversationId,
                cadence = draft.cadence,
                // Standard Terra is intentional: scheduled work never silently selects a Pro tier.
                modelPresetId = ModelPresetId.GPT_5_6_TERRA,
                status = ScheduledMonitorStatus.ACTIVE,
                nextRunAt = now,
                createdAt = now,
                updatedAt = now,
            )
            val saved = withContext(Dispatchers.IO) { repository.create(task) }
            scheduler.schedule(saved)
            state = state.copy(creating = false, selectedTaskId = saved.id, notice = "已保存，首次监控将联网运行。")
            reload()
        }
    }

    fun setPaused(task: ScheduledMonitorTask, paused: Boolean) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                repository.setStatus(task.id, if (paused) ScheduledMonitorStatus.PAUSED else ScheduledMonitorStatus.ACTIVE, clock.instant())
            }
            if (updated == null) {
                state = state.copy(notice = "任务已不存在。")
            } else {
                if (paused) scheduler.cancel(updated.id) else scheduler.schedule(updated.copy(nextRunAt = clock.instant()))
                state = state.copy(notice = if (paused) "监控已暂停，不会再联网。" else "监控已恢复，下一次将联网运行。")
                reload()
            }
        }
    }

    fun delete(task: ScheduledMonitorTask) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { repository.delete(task.id) }
            scheduler.cancel(task.id)
            state = state.copy(selectedTaskId = state.selectedTaskId?.takeUnless { it == task.id }, notice = if (deleted) "计划已删除。" else "任务已不存在。")
            reload()
        }
    }

    fun reload() {
        viewModelScope.launch {
            val tasks = withContext(Dispatchers.IO) { repository.list() }
            val selected = state.selectedTaskId?.takeIf { selectedId -> tasks.any { it.id == selectedId } }
                ?: tasks.firstOrNull()?.id
            state = state.copy(tasks = tasks, selectedTaskId = selected)
        }
    }

    class Factory(
        private val repository: ScheduledMonitorRepository,
        private val scheduler: AndroidScheduledMonitorScheduler,
        private val reminderDraftRefiner: ReminderDraftRefiner,
        private val clock: Clock,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduledMonitorViewModel(repository, scheduler, reminderDraftRefiner, clock) as T
    }
}
