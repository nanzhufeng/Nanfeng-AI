package com.nanzhufeng.ai.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.PrivacyDataManager
import com.nanzhufeng.ai.domain.PrivacyDeleteScope
import com.nanzhufeng.ai.domain.PrivacyDeletionPreview
import com.nanzhufeng.ai.domain.PrivacyDeletionRequest
import com.nanzhufeng.ai.domain.PrivacyDeletionResult
import com.nanzhufeng.ai.domain.PrivacyInventory
import com.nanzhufeng.ai.domain.PrivacyTaskDeletionCandidate
import com.nanzhufeng.ai.domain.SecurityDiagnosticResult
import com.nanzhufeng.ai.domain.ImportedZipCleanupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PrivacyDataUiState(
    val visible: Boolean = false,
    val working: Boolean = false,
    val inventory: PrivacyInventory = PrivacyInventory.EmptySnapshot,
    val preview: PrivacyDeletionPreview? = null,
    val taskCandidates: List<PrivacyTaskDeletionCandidate> = emptyList(),
    val selectedTaskIds: Set<String> = emptySet(),
    val retryAvailable: Boolean = false,
    val confirmation: String = "",
    val notice: String? = null,
    val error: String? = null,
)

class PrivacyDataViewModel(private val manager: PrivacyDataManager) : ViewModel() {
    var state by mutableStateOf(PrivacyDataUiState(inventory = manager.cachedInventory()))
        private set
    private var refreshJob: Job? = null
    private var previewJob: Job? = null

    fun show() { state = state.copy(visible = true, notice = null, error = null); refresh() }
    fun dismiss() { if (!state.working) state = state.copy(visible = false, preview = null, taskCandidates = emptyList(), selectedTaskIds = emptySet(), confirmation = "", error = null) }
    fun preview(scope: PrivacyDeleteScope) {
        previewJob?.cancel()
        state = state.copy(working = true, preview = null, taskCandidates = emptyList(), selectedTaskIds = emptySet(), retryAvailable = false, confirmation = "", notice = null, error = null)
        previewJob = viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) { manager.preview(scope) }
                state = if (scope == PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS) state.copy(working = false, preview = null, taskCandidates = preview.taskCandidates)
                else state.copy(working = false, preview = preview)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                state = state.copy(working = false, error = "清理范围读取失败；本机数据未改变。")
            }
        }
    }
    fun toggleTask(candidate: PrivacyTaskDeletionCandidate) {
        val selected = state.selectedTaskIds.toMutableSet().also { if (!it.add(candidate.selectionId)) it.remove(candidate.selectionId) }
        state = state.copy(selectedTaskIds = selected, preview = null, notice = null, error = null)
    }
    fun previewSelectedTasks() {
        if (state.selectedTaskIds.isEmpty()) return
        val selectedTaskIds = state.selectedTaskIds
        previewJob?.cancel()
        state = state.copy(working = true, preview = null, notice = null, error = null)
        previewJob = viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) {
                    manager.preview(PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS, selectedTaskIds)
                }
                state = state.copy(working = false, preview = preview)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                state = state.copy(working = false, error = "清理范围读取失败；本机数据未改变。")
            }
        }
    }
    fun confirmation(value: String) { state = state.copy(confirmation = value) }
    fun delete() {
        val preview = state.preview ?: return
        state = state.copy(working = true, notice = null, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { manager.delete(PrivacyDeletionRequest(preview.scope, preview.fingerprint, state.confirmation, state.selectedTaskIds)) }
            val inventory = if (result is PrivacyDeletionResult.Completed || result is PrivacyDeletionResult.Partial) {
                withContext(Dispatchers.IO) { manager.inventory() }
            } else null
            state = when (result) {
                is PrivacyDeletionResult.Completed -> state.copy(working = false, preview = null, taskCandidates = emptyList(), selectedTaskIds = emptySet(), retryAvailable = false, confirmation = "", notice = "已按预览范围删除；安装身份和签名未受影响。", inventory = requireNotNull(inventory))
                is PrivacyDeletionResult.Rejected -> state.copy(working = false, error = result.reason)
                is PrivacyDeletionResult.Partial -> state.copy(working = false, retryAvailable = true, error = "删除部分完成（${result.retryableFailureCount} 项待重试）；未静默忽略。", inventory = requireNotNull(inventory))
            }
        }
    }
    fun retryFailedTaskDeletion() {
        state = state.copy(working = true, notice = null, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { manager.retryFailedTaskDeletion() }
            val inventory = if (result is PrivacyDeletionResult.Completed || result is PrivacyDeletionResult.Partial) {
                withContext(Dispatchers.IO) { manager.inventory() }
            } else null
            state = when (result) {
                is PrivacyDeletionResult.Completed -> state.copy(working = false, retryAvailable = false, notice = "已完成失败剩余项重试；没有自动继续删除。", inventory = requireNotNull(inventory))
                is PrivacyDeletionResult.Partial -> state.copy(working = false, retryAvailable = true, error = "仍有 ${result.retryableFailureCount} 项待重试；未超出原选择范围。", inventory = requireNotNull(inventory))
                is PrivacyDeletionResult.Rejected -> state.copy(working = false, error = result.reason)
            }
        }
    }
    fun cleanupImportedZipPackages() {
        state = state.copy(working = true, notice = null, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { manager.cleanupImportedZipPackages() }
            state = when (result) {
                is ImportedZipCleanupResult.Completed -> state.copy(
                    working = false,
                    notice = if (result.deletedPackageCount == 0L) "没有需要删除的 ZIP 原始包。"
                    else "已整理 ${result.materializedAttachmentCount} 个导入附件，并删除 ${result.deletedPackageCount} 个 ZIP 原始包。",
                    inventory = withContext(Dispatchers.IO) { manager.inventory() },
                )
                is ImportedZipCleanupResult.Partial -> state.copy(
                    working = false,
                    error = result.reason,
                    inventory = withContext(Dispatchers.IO) { manager.inventory() },
                )
                is ImportedZipCleanupResult.Rejected -> state.copy(working = false, error = result.reason)
            }
        }
    }
    fun exportTo(uri: Uri) {
        state = state.copy(working = true, notice = null, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { manager.exportSecurityDiagnostic(uri) }
            state = when (result) {
                is SecurityDiagnosticResult.Completed -> state.copy(working = false, notice = "诊断已回读校验 SHA-256：${result.artifact.sha256.take(12)}…")
                is SecurityDiagnosticResult.Rejected -> state.copy(working = false, error = result.reason)
                is SecurityDiagnosticResult.Failed -> state.copy(working = false, error = result.reason)
            }
        }
    }
    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val inventory = withContext(Dispatchers.IO) { manager.inventory() }
            state = state.copy(inventory = inventory)
        }
    }

    class Factory(private val manager: PrivacyDataManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PrivacyDataViewModel::class.java)); return PrivacyDataViewModel(manager) as T
        }
    }
}
