package com.nanzhufeng.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MemoryUiState(
    val memories: List<MemorySnapshot> = emptyList(),
    val scopeFilter: MemoryScopeKind? = null,
    val statusFilter: MemoryStatus? = MemoryStatus.ACTIVE,
    val search: String = "",
    val dialogVisible: Boolean = false,
    val selectedId: MemoryId? = null,
    val selectedForBatch: Set<MemoryId> = emptySet(),
    val pendingConflict: MemoryConflict? = null,
    val notice: String? = null,
)

/** UI adapter for explicit user-owned Memory actions. It never reads ContextSelection or memorySources. */
class MemoryViewModel(private val manage: ManageMemoryUseCase) : ViewModel() {
    var state by mutableStateOf(MemoryUiState())
        private set
    init { reload() }
    /** User-facing entry is a single global memory summary, never a context-control console. */
    fun showDialog() {
        state = state.copy(
            dialogVisible = true,
            scopeFilter = MemoryScopeKind.GLOBAL,
            statusFilter = MemoryStatus.ACTIVE,
            search = "",
            selectedId = null,
            selectedForBatch = emptySet(),
            pendingConflict = null,
        )
        reload()
    }
    fun dismissDialog() { state = state.copy(dialogVisible = false, pendingConflict = null, selectedForBatch = emptySet()) }
    fun setScopeFilter(scope: MemoryScopeKind?) { state = state.copy(scopeFilter = scope); reload() }
    fun setStatusFilter(status: MemoryStatus?) { state = state.copy(statusFilter = status); reload() }
    fun updateSearch(search: String) { state = state.copy(search = search); reload() }
    /** Re-reads the local, user-confirmed summary; it never generates or fetches a replacement. */
    fun refreshSummary() { state = state.copy(search = ""); reload("已刷新本机记忆摘要。") }
    /** A memory-page question is a local query, never a silent provider request. */
    fun querySummary(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        state = state.copy(search = normalized)
        reload("已显示与“$normalized”相关的本机记忆。")
    }
    /** Appending always stays explicit and therefore uses the same sensitivity gate as other Memory writes. */
    fun appendSummaryUpdate(raw: String) {
        val addition = raw.trim()
        if (addition.isBlank()) return
        val latest = state.memories.maxByOrNull { it.memory.updatedAt }
        if (latest == null) {
            create("概览", addition, MemoryScope(MemoryScopeKind.GLOBAL))
        } else {
            update(
                id = latest.memory.id,
                title = latest.memory.title,
                body = "${latest.memory.body.trim()}\n\n$addition",
                scope = latest.memory.scope,
            )
        }
    }
    /** Confirmation has already happened in the UI. Deleting a summary never changes its use setting. */
    fun clearSummary() = viewModelScope.launch {
        val ids = state.memories.filter { it.memory.status == MemoryStatus.ACTIVE }.map { it.memory.id }
        if (ids.isEmpty()) {
            state = state.copy(notice = "没有可删除的记忆摘要。")
            return@launch
        }
        when (val result = withContext(Dispatchers.IO) {
            manage.execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.BULK_DELETE, memoryIds = ids))
        }) {
            is MemoryMutationResult.Applied, is MemoryMutationResult.Replayed -> {
                state = state.copy(search = "", notice = "记忆摘要已删除。", selectedForBatch = emptySet())
                reload()
            }
            is MemoryMutationResult.Duplicate -> state = state.copy(notice = "记忆状态发生变化，请刷新后再试。")
            is MemoryMutationResult.Conflict -> state = state.copy(notice = "记忆删除遇到冲突，未删除。")
            is MemoryMutationResult.Rejected -> state = state.copy(notice = rejectionText(result.code))
        }
    }
    /** This setting applies prospectively and deliberately preserves saved summaries for later re-enable. */
    fun markSummaryGenerationAndUseDisabled() {
        state = state.copy(notice = "已关闭记忆摘要生成和应用；已保存的记忆仍保留在本机。")
    }
    fun select(id: MemoryId) { state = state.copy(selectedId = id) }
    fun toggleBatch(id: MemoryId) { state = state.copy(selectedForBatch = state.selectedForBatch.let { if (id in it) it - id else it + id }) }
    fun reload(notice: String? = null) = viewModelScope.launch {
        val loaded = withContext(Dispatchers.IO) { manage.list(state.scopeFilter, state.statusFilter, state.search) }
        val selected = state.selectedId?.takeIf { current -> loaded.any { it.memory.id == current } } ?: loaded.firstOrNull()?.memory?.id
        state = state.copy(memories = loaded, selectedId = selected, notice = notice ?: state.notice)
    }
    fun create(title: String, body: String, scope: MemoryScope) = execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.CREATE, MemoryId.new(), title, body, scope), "记忆已在本机确认并保存；启用记忆后会按当前问题自动检索。")
    fun saveSuggestedSummary(conversationId: ConversationId?, draft: com.nanzhufeng.ai.domain.MemorySummaryDraft) = execute(
        MemoryIntent(
            id = MemoryIntentId.new(), action = MemoryIntentAction.CREATE, memoryId = MemoryId.new(),
            title = draft.title, body = draft.body, scope = MemoryScope(MemoryScopeKind.GLOBAL),
            sourceStableId = "conversation:${conversationId?.value.orEmpty()}",
            sourceSummary = "用户在对话末尾确认加入的本机记忆摘要",
        ),
        "已加入记忆摘要。",
    )
    fun update(id: MemoryId, title: String, body: String, scope: MemoryScope) = execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.UPDATE, id, title, body, scope), "已追加新的本地修订；启用记忆后会按当前问题自动检索。")
    fun pause(id: MemoryId, paused: Boolean) = execute(MemoryIntent(MemoryIntentId.new(), if (paused) MemoryIntentAction.PAUSE else MemoryIntentAction.RESTORE, id), if (paused) "记忆已暂停，不会进入后续对话上下文。" else "记忆已恢复为可用；启用记忆后会按当前问题自动检索。")
    fun delete(id: MemoryId) = execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.DELETE, id), "记忆已软删除；普通列表不显示正文，历史仍可审计。")
    fun deleteSelected() = execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.BULK_DELETE, memoryIds = state.selectedForBatch.toList()), "已软删除选中的记忆；普通列表不显示正文。")
    fun resolveConflict(resolution: MemoryConflictResolution) = state.pendingConflict?.let { conflict -> execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.RESOLVE_CONFLICT, conflictId = conflict.id, conflictResolution = resolution), when (resolution) { MemoryConflictResolution.KEEP_EXISTING -> "已保留现有记忆。"; MemoryConflictResolution.CREATE_PARALLEL -> "已明确选择并存保存。"; MemoryConflictResolution.REVISE_EXISTING -> "已明确选择更新现有记忆并追加修订。" }) }
    private fun execute(intent: MemoryIntent, success: String) = viewModelScope.launch {
        when (val result = withContext(Dispatchers.IO) { manage.execute(intent) }) {
            is MemoryMutationResult.Applied -> { state = state.copy(notice = success, pendingConflict = null, selectedForBatch = emptySet()); reload() }
            is MemoryMutationResult.Replayed -> { state = state.copy(notice = "已回读相同的本地 Memory 操作。", pendingConflict = null); reload() }
            is MemoryMutationResult.Duplicate -> { state = state.copy(notice = "相同范围内的规范化内容已存在，未重复保存。", selectedId = result.existing.memory.id); reload() }
            is MemoryMutationResult.Conflict -> state = state.copy(pendingConflict = result.conflict, notice = "发现同范围同概念的不同内容，请明确选择处理方式。")
            is MemoryMutationResult.Rejected -> state = state.copy(notice = rejectionText(result.code))
        }
    }
    class Factory(private val manage: ManageMemoryUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MemoryViewModel(manage) as T
    }
}

private fun rejectionText(code: MemoryRejectionCode): String = when (code) {
    MemoryRejectionCode.HIGH_SENSITIVITY_PASSWORD, MemoryRejectionCode.HIGH_SENSITIVITY_API_KEY, MemoryRejectionCode.HIGH_SENSITIVITY_AUTHORIZATION, MemoryRejectionCode.HIGH_SENSITIVITY_RECOVERY_CODE, MemoryRejectionCode.HIGH_SENSITIVITY_PAYMENT_CARD -> "已拒绝高敏感内容；正文未写入本地 Memory。"
    MemoryRejectionCode.INVALID_SCOPE_REFERENCE -> "关联的 Project 或 Conversation 不存在，未保存。"
    MemoryRejectionCode.EMPTY_TITLE -> "请填写记忆标题。"
    MemoryRejectionCode.BODY_EMPTY -> "请填写记忆正文。"
    MemoryRejectionCode.TITLE_TOO_LONG, MemoryRejectionCode.BODY_TOO_LONG -> "内容超过本地 Memory 边界。"
    else -> "Memory 操作未完成：${code.name}。"
}
