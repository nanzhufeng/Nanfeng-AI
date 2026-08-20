package com.nanzhufeng.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.ContextBodySourceKind
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ExplicitContextBodyPlan
import com.nanzhufeng.ai.domain.ExplicitContextBodyPlanResult
import com.nanzhufeng.ai.domain.ExplicitContextBodyRequest
import com.nanzhufeng.ai.domain.ExplicitContextBodyResult
import com.nanzhufeng.ai.domain.ExplicitContextBodySnapshot
import com.nanzhufeng.ai.domain.MemoryId
import com.nanzhufeng.ai.domain.KnowledgeItemId
import com.nanzhufeng.ai.domain.KnowledgeSearchResult
import com.nanzhufeng.ai.domain.ContextKnowledgeSearchResult
import com.nanzhufeng.ai.domain.KnowledgeContextVersion
import com.nanzhufeng.ai.domain.ReadExplicitContextBodyUseCase
import com.nanzhufeng.ai.domain.LocalContextCompressionBudgetPreset
import com.nanzhufeng.ai.domain.LocalContextCompressionRequest
import com.nanzhufeng.ai.domain.LocalContextPreviewResult
import com.nanzhufeng.ai.domain.ReadLocalContextPreviewUseCase
import com.nanzhufeng.ai.domain.StablePrefixInvalidationReason
import com.nanzhufeng.ai.domain.StableContextPrefixMetadata
import com.nanzhufeng.ai.domain.toPolicy
import com.nanzhufeng.ai.domain.LocalActionTracePlan
import com.nanzhufeng.ai.domain.LocalActionTracePlanResult
import com.nanzhufeng.ai.domain.LocalActionTraceRequest
import com.nanzhufeng.ai.domain.LocalActionTraceResult
import com.nanzhufeng.ai.domain.LocalActionTraceSnapshot
import com.nanzhufeng.ai.domain.ReadExplicitLocalActionTraceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** P4-D controls are deliberately transient: no selection is written to Memory or Conversation settings. */
data class ContextBodySelectionUiState(
    val dialogVisible: Boolean = false,
    val isLoading: Boolean = false,
    val conversationId: ConversationId? = null,
    val plan: ExplicitContextBodyPlan? = null,
    val includeProjectInstruction: Boolean = false,
    val includeConversationPath: Boolean = false,
    val selectedMemoryIds: Set<MemoryId> = emptySet(),
    val knowledgeQuery: String = "",
    val knowledgeResults: List<KnowledgeSearchResult> = emptyList(),
    val selectedKnowledgeIds: Set<KnowledgeItemId> = emptySet(),
    val selectedKnowledgeVersions: Map<KnowledgeItemId, KnowledgeContextVersion> = emptyMap(),
    val localActionTracePlan: LocalActionTracePlan? = null,
    val localActionTraceEnabled: Boolean = false,
    val selectedLocalActionTraceIds: Set<String> = emptySet(),
    val localActionTracePreview: LocalActionTraceSnapshot? = null,
    val budgetPreset: LocalContextCompressionBudgetPreset = LocalContextCompressionBudgetPreset.BALANCED,
    val preview: LocalContextPreviewResult.Available? = null,
    val previousStablePrefix: StableContextPrefixMetadata? = null,
    val prefixInvalidationReasons: Set<StablePrefixInvalidationReason> = emptySet(),
    val notice: String? = null,
)

class ContextBodySelectionViewModel(
    private val read: ReadExplicitContextBodyUseCase,
    private val previewReader: ReadLocalContextPreviewUseCase,
    private val traceReader: ReadExplicitLocalActionTraceUseCase,
) : ViewModel() {
    var state by mutableStateOf(ContextBodySelectionUiState())
        private set

    fun showDialog(conversationId: ConversationId?) {
        if (conversationId == null) {
            state = state.copy(notice = "请先选择本地对话；未读取任何 Context 正文。")
            return
        }
        state = ContextBodySelectionUiState(dialogVisible = true, isLoading = true, conversationId = conversationId)
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { read.plan(conversationId) to traceReader.plan(conversationId) }
            when (val result = results.first) {
                is ExplicitContextBodyPlanResult.Available -> state = state.copy(isLoading = false, plan = result.plan, localActionTracePlan = (results.second as? LocalActionTracePlanResult.Available)?.plan, notice = (results.second as? LocalActionTracePlanResult.Rejected)?.let { rejectionText(it.reason.name) })
                is ExplicitContextBodyPlanResult.Rejected -> state = state.copy(isLoading = false, notice = rejectionText(result.reason.name))
            }
        }
    }

    fun dismissDialog() { state = ContextBodySelectionUiState() }
    fun toggleProjectInstruction() { discardPreview { copy(includeProjectInstruction = !includeProjectInstruction) } }
    fun toggleConversationPath() { discardPreview { copy(includeConversationPath = !includeConversationPath) } }
    fun toggleMemory(id: MemoryId) { discardPreview { copy(selectedMemoryIds = selectedMemoryIds.let { if (id in it) it - id else it + id }) } }
    fun searchKnowledge(query: String) {
        val conversationId = state.conversationId ?: return
        discardPreview { copy(knowledgeQuery = query, isLoading = true) }
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { read.searchKnowledge(conversationId, query) }) {
                is ContextKnowledgeSearchResult.Available -> state = state.copy(isLoading = false, knowledgeResults = result.results)
                is ContextKnowledgeSearchResult.Rejected -> state = state.copy(isLoading = false, knowledgeResults = emptyList(), notice = rejectionText(result.reason.name))
            }
        }
    }
    fun toggleKnowledge(result: KnowledgeSearchResult) {
        val id = result.id
        if (id in state.selectedKnowledgeIds) discardPreview { copy(selectedKnowledgeIds = selectedKnowledgeIds - id, selectedKnowledgeVersions = selectedKnowledgeVersions - id) }
        else discardPreview { copy(selectedKnowledgeIds = selectedKnowledgeIds + id, selectedKnowledgeVersions = selectedKnowledgeVersions + (id to KnowledgeContextVersion(result.revision, result.contentHash)) ) }
    }
    fun toggleLocalActionTraceEnabled() { discardAllPreviews { copy(localActionTraceEnabled = !localActionTraceEnabled, selectedLocalActionTraceIds = emptySet()) } }
    fun toggleLocalActionTrace(id: String) { if (state.localActionTraceEnabled) discardTracePreview { copy(selectedLocalActionTraceIds = selectedLocalActionTraceIds.let { if (id in it) it - id else it + id }) } }

    fun previewLocalActionTrace() {
        val conversationId = state.conversationId ?: return
        if (!state.localActionTraceEnabled || state.selectedLocalActionTraceIds.isEmpty()) return
        state = state.copy(isLoading = true, notice = null)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { traceReader.execute(LocalActionTraceRequest(conversationId, state.selectedLocalActionTraceIds)) }) {
                is LocalActionTraceResult.Available -> state = state.copy(isLoading = false, localActionTracePreview = result.snapshot, notice = "已生成 LOCAL_L3_METADATA 本机预览；没有正文、Prompt 或发送链。")
                is LocalActionTraceResult.Rejected -> state = state.copy(isLoading = false, localActionTracePreview = null, notice = rejectionText(result.reason.name))
            }
        }
    }

    fun selectBudget(preset: LocalContextCompressionBudgetPreset) {
        discardPreview { copy(budgetPreset = preset) }
    }

    fun previewExtractive() {
        val conversationId = state.conversationId ?: return
        state = state.copy(isLoading = true, notice = null)
        val request = ExplicitContextBodyRequest(conversationId, state.includeProjectInstruction, state.includeConversationPath, state.selectedMemoryIds, state.selectedKnowledgeIds, state.selectedKnowledgeVersions)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { previewReader.execute(LocalContextCompressionRequest(request, state.budgetPreset.toPolicy())) }) {
                is LocalContextPreviewResult.Available -> {
                    val oldPrefix = state.previousStablePrefix ?: state.preview?.stablePrefix
                    state = state.copy(
                        isLoading = false,
                        preview = result,
                        previousStablePrefix = null,
                        prefixInvalidationReasons = oldPrefix?.let { previewReader.invalidationReasons(it, result.stablePrefix) }.orEmpty(),
                        notice = "已生成 EXTRACTIVE_LOCAL 本机抽取预览；没有构造 Prompt 或发送内容。",
                    )
                }
                is LocalContextPreviewResult.Rejected -> state = state.copy(isLoading = false, preview = null, previousStablePrefix = null, prefixInvalidationReasons = emptySet(), notice = rejectionText(result.reason.name))
            }
        }
    }

    fun isMemoryCandidate(kind: ContextBodySourceKind): Boolean = kind in setOf(ContextBodySourceKind.GLOBAL_MEMORY, ContextBodySourceKind.PROJECT_MEMORY, ContextBodySourceKind.CONVERSATION_MEMORY)
    private fun discardPreview(transform: ContextBodySelectionUiState.() -> ContextBodySelectionUiState) {
        val retainedPrefix = state.preview?.stablePrefix ?: state.previousStablePrefix
        state = state.transform().copy(preview = null, previousStablePrefix = retainedPrefix, prefixInvalidationReasons = emptySet())
    }
    private fun discardTracePreview(transform: ContextBodySelectionUiState.() -> ContextBodySelectionUiState) { state = state.transform().copy(localActionTracePreview = null) }
    private fun discardAllPreviews(transform: ContextBodySelectionUiState.() -> ContextBodySelectionUiState) {
        val retainedPrefix = state.preview?.stablePrefix ?: state.previousStablePrefix
        state = state.transform().copy(preview = null, previousStablePrefix = retainedPrefix, prefixInvalidationReasons = emptySet(), localActionTracePreview = null)
    }
    class Factory(private val read: ReadExplicitContextBodyUseCase, private val previewReader: ReadLocalContextPreviewUseCase, private val traceReader: ReadExplicitLocalActionTraceUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ContextBodySelectionViewModel(read, previewReader, traceReader) as T
    }
}

private fun rejectionText(code: String): String = when (code) {
    "MISSING_CONVERSATION" -> "当前会话未能从本机回读；没有预览。"
    "INCONSISTENT_PROJECT_REFERENCE", "STALE_CONTEXT" -> "会话或项目状态已变化；没有混合旧正文，请重新打开。"
    "INELIGIBLE_MEMORY" -> "所选 Memory 已暂停、删除或不属于当前范围；没有预览。"
    "INELIGIBLE_KNOWLEDGE" -> "所选 Knowledge 已归档、删除或不属于当前 GLOBAL/项目范围；没有预览。"
    "SENSITIVE_CONTENT" -> "已拒绝敏感正文；没有保存、发送或记录该预览。"
    "INVALID_COMPRESSION_REQUEST" -> "本机抽取预算或来源无效；没有预览。"
    "INELIGIBLE_ACTION" -> "所选 L3 动作已不再属于当前会话终态谱系；没有预览。"
    else -> "本机 Context 预览未完成。"
}
