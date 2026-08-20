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

data class ChatGptImportUiState(val visible: Boolean = false, val working: Boolean = false, val tasks: List<ChatGptImportTask> = emptyList(), val selected: ChatGptImportTask? = null)
class ChatGptExportImportViewModel(private val imports: ManageChatGptExportImportUseCase) : ViewModel() {
    var state by mutableStateOf(ChatGptImportUiState()); private set
    fun show() = reload(open = true); fun dismiss() { if (!state.working) state = state.copy(visible = false) }; fun open(task: ChatGptImportTask) { state = state.copy(selected = task) }; fun back() { state = state.copy(selected = null) }
    fun selectedFile(name: String, mime: String, bytes: ByteArray) { state = state.copy(working = true); viewModelScope.launch { val task = withContext(Dispatchers.IO) { imports.select(name, mime, bytes) }; reload(task.id, true) } }
    fun confirm(id: ChatGptImportItemId) = mutate { imports.confirm(it, id) }; fun skip(id: ChatGptImportItemId) = mutate { imports.skip(it, id) }; fun retry(id: ChatGptImportTaskId) = mutate(id) { imports.retry(it) }; fun cancel() = mutate { imports.cancel(it) }
    private fun mutate(action: (ChatGptImportTaskId) -> Unit) { state.selected?.id?.let { mutate(it, action) } }; private fun mutate(id: ChatGptImportTaskId, action: (ChatGptImportTaskId) -> Unit) { state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { action(id) }; reload(id) } }
    private fun reload(selected: ChatGptImportTaskId? = state.selected?.id, open: Boolean = false) { viewModelScope.launch { val tasks = withContext(Dispatchers.IO) { imports.list() }; state = state.copy(visible = state.visible || open, working = false, tasks = tasks, selected = selected?.let { id -> tasks.firstOrNull { it.id == id } }) } }
    class Factory(private val imports: ManageChatGptExportImportUseCase) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = ChatGptExportImportViewModel(imports) as T }
}
@Composable fun ChatGptExportImportCard(onOpen: () -> Unit) = WhiteCard { Text("ChatGPT 对话导入", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text("仅选择 ChatGPT data export 的 conversations.json。内容只作本地不执行文本；选择后直接导入普通历史。", color = SecondaryText, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(10.dp)); Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { Text("导入 ChatGPT JSON") } }
@Composable fun ChatGptExportImportSettingsPage(state: ChatGptImportUiState, onChoose: () -> Unit, onOpen: (ChatGptImportTask) -> Unit, onBack: () -> Unit, onRetry: (ChatGptImportTaskId) -> Unit, onCancel: () -> Unit) = WhiteCard {
    Text("ChatGPT 对话导入", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Text("仅选择 ChatGPT data export 的 conversations.json。系统选择后复制到 app-private 并直接导入；不会上传或执行内容。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Text("device · local-only · sensitive", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(12.dp))
    if (state.selected == null) {
        Button(onClick = onChoose, enabled = !state.working, colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { Text("选择 conversations.json") }
        state.tasks.forEach { task -> TextButton(onClick = { onOpen(task) }, modifier = Modifier.fillMaxWidth()) { Text("${task.asset?.displayName ?: "导入任务"} · ${task.status}") } }
    } else {
        val task = state.selected
        Text("${task.status} · 本地任务可在重启后恢复", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
        task.items.forEach { item ->
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(item.candidate?.title ?: "不可导入会话", style = MaterialTheme.typography.bodyLarge)
            Text(item.failure?.name ?: item.status.name, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("返回任务") }
            if (task.status == ChatGptImportTaskStatus.FAILED) OutlinedButton(onClick = { onRetry(task.id) }, enabled = !state.working) { Text("重试") }
            else if (task.status !in setOf(ChatGptImportTaskStatus.COMPLETED, ChatGptImportTaskStatus.CANCELLED)) TextButton(onClick = onCancel, enabled = !state.working) { Text("取消") }
        }
    }
}
