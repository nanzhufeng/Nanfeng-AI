package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ClaudeImportUiState(val working: Boolean = false, val tasks: List<ClaudeImportTask> = emptyList(), val selected: ClaudeImportTask? = null)
class ClaudeExportImportViewModel(private val imports: ManageClaudeExportImportUseCase) : ViewModel() {
    var state by mutableStateOf(ClaudeImportUiState()); private set
    fun show() = reload(); fun open(task: ClaudeImportTask) { state = state.copy(selected = task) }; fun back() { state = state.copy(selected = null) }
    fun selectedFile(name: String, mime: String, bytes: ByteArray) { state = state.copy(working = true); viewModelScope.launch { val task = withContext(Dispatchers.IO) { imports.select(name, mime, bytes) }; reload(task.id) } }
    fun confirm(id: ClaudeImportItemId) = mutate { imports.confirm(it, id) }; fun skip(id: ClaudeImportItemId) = mutate { imports.skip(it, id) }; fun retry(id: ClaudeImportTaskId) = mutate(id) { imports.retry(it) }; fun cancel() = mutate { imports.cancel(it) }
    private fun mutate(action: (ClaudeImportTaskId) -> Unit) { state.selected?.id?.let { mutate(it, action) } }; private fun mutate(id: ClaudeImportTaskId, action: (ClaudeImportTaskId) -> Unit) { state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { action(id) }; reload(id) } }
    private fun reload(selected: ClaudeImportTaskId? = state.selected?.id) { viewModelScope.launch { val tasks = withContext(Dispatchers.IO) { imports.list() }; state = state.copy(working = false, tasks = tasks, selected = selected?.let { id -> tasks.firstOrNull { it.id == id } }) } }
    class Factory(private val imports: ManageClaudeExportImportUseCase) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = ClaudeExportImportViewModel(imports) as T }
}

@Composable fun ClaudeExportImportSettingsPage(state: ClaudeImportUiState, onChoose: () -> Unit, onOpen: (ClaudeImportTask) -> Unit, onBack: () -> Unit, onRetry: (ClaudeImportTaskId) -> Unit, onCancel: () -> Unit, showHeader: Boolean = true, actionLabel: String = "选择 conversations.json", grouped: Boolean = false, showTaskList: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth()) = Column(modifier) {
    if (showHeader) {
        Text("Claude 对话导入", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("选择 Claude 导出的 conversations.json，只在本机导入。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
    if (!grouped) Spacer(Modifier.height(12.dp))
    if (state.selected == null) {
        if (grouped) DataStorageGroupedActionRow(label = actionLabel, onClick = onChoose, enabled = !state.working, working = state.working)
        else Button(onClick = onChoose, enabled = !state.working, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape, colors = ButtonDefaults.buttonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text(actionLabel) }
        if (showTaskList) state.tasks.forEach { task -> TextButton(onClick = { onOpen(task) }, modifier = Modifier.fillMaxWidth()) { Text("${task.asset?.displayName ?: "导入任务"} · ${task.status}") } }
    } else {
        val task = state.selected
        Text("${task.status} · 本地任务可在重启后恢复", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
        task.items.forEach { item ->
            HorizontalDivider(Modifier.padding(vertical = 6.dp)); Text(item.candidate?.title ?: "不可导入会话", style = MaterialTheme.typography.bodyLarge)
            Text(item.failure?.name ?: item.status.name, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("返回任务") }
            if (task.status == ClaudeImportTaskStatus.FAILED) OutlinedButton(onClick = { onRetry(task.id) }, enabled = !state.working, shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("重试") }
            else if (task.status !in setOf(ClaudeImportTaskStatus.COMPLETED, ClaudeImportTaskStatus.CANCELLED)) TextButton(onClick = onCancel, enabled = !state.working) { Text("取消") }
        }
    }
}
