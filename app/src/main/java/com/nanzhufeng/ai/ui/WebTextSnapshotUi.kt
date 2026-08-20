package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WebTextSnapshotUiState(val visible: Boolean = false, val working: Boolean = false, val tasks: List<WebTextSnapshotTask> = emptyList(), val selected: WebTextSnapshotTask? = null, val inputUrl: String = "", val confirmed: Boolean = false)
class WebTextSnapshotViewModel(private val useCase: ManageWebTextSnapshotUseCase) : ViewModel() {
    var state by mutableStateOf(WebTextSnapshotUiState()); private set
    fun show() { viewModelScope.launch { withContext(Dispatchers.IO) { useCase.recoverInterrupted() }; reload(true) } }; fun dismiss() { if (!state.working) state = state.copy(visible = false) }; fun open(task: WebTextSnapshotTask) { state = state.copy(selected = task) }; fun back() { state = state.copy(selected = null, confirmed = false) }
    fun updateUrl(value: String) { state = state.copy(inputUrl = value, confirmed = false) }; fun setConfirmed(value: Boolean) { state = state.copy(confirmed = value) }
    fun start() { if (!state.confirmed || state.working) return; state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { useCase.start(state.inputUrl) }; reload() } }
    fun retry(task: WebTextSnapshotTask) { if (!state.confirmed || state.working) return; state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { useCase.retry(task.id, state.inputUrl.ifBlank { task.requestedUrl }) }; reload(task.id) } }
    fun cancel() = state.selected?.id?.let { id -> state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { useCase.cancel(id) }; reload(id) } }
    fun skip(id: WebTextSnapshotItemId) = mutate { task -> useCase.skip(task.id, id) }
    fun confirm(item: WebTextSnapshotItem) = mutate { task -> useCase.confirm(task.id, item.id, item.title, item.body) }
    private fun mutate(action: (WebTextSnapshotTask) -> Unit) { val task = state.selected ?: return; state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { action(task) }; reload(task.id) } }
    private fun reload(open: Boolean = false) = reload(null, open)
    private fun reload(selectedId: WebTextSnapshotTaskId?, open: Boolean = false) { viewModelScope.launch { val tasks = withContext(Dispatchers.IO) { useCase.list() }; state = state.copy(visible = state.visible || open, working = false, tasks = tasks, selected = selectedId?.let { id -> tasks.firstOrNull { it.id == id } }) } }
    class Factory(private val useCase: ManageWebTextSnapshotUseCase) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = WebTextSnapshotViewModel(useCase) as T }
}

@Composable fun WebTextSnapshotCard(onOpen: () -> Unit) = WhiteCard { Text("网页文本快照", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)); Text("仅在你前台输入并确认公共 HTTPS 地址后抓取一个 HTML 页面；网络恢复、后台或重启都不会自动重试。", color = SecondaryText, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(12.dp)); Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = BrandGreen), modifier = Modifier.fillMaxWidth()) { Text("输入 HTTPS 地址") } }
@Composable fun WebTextSnapshotDialog(state: WebTextSnapshotUiState, onDismiss: () -> Unit, onBack: () -> Unit, onOpen: (WebTextSnapshotTask) -> Unit, onUrl: (String) -> Unit, onConsent: (Boolean) -> Unit, onStart: () -> Unit, onRetry: (WebTextSnapshotTask) -> Unit, onConfirm: (WebTextSnapshotItem) -> Unit, onSkip: (WebTextSnapshotItemId) -> Unit, onCancel: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, title = { Text(if (state.selected == null) "网页文本快照" else "网页快照任务") }, text = { Column(Modifier.p5aKeyboardTraversal().verticalScroll(rememberScrollState()).heightIn(max = 520.dp)) {
        if (state.selected == null) { OutlinedTextField(state.inputUrl, onUrl, label = { Text("HTTPS URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Row { Checkbox(state.confirmed, onConsent); Text("我确认抓取此公开网页的主 HTML") }; Text("拒绝登录、私网、敏感参数和非 HTML 内容。", color = SecondaryText, style = MaterialTheme.typography.bodySmall); state.tasks.forEach { task -> TextButton(onClick = { onOpen(task) }) { Text("${task.status} · ${task.requestedUrl}") } } }
        else { val task = requireNotNull(state.selected); Text("${task.status} · ${task.asset?.host ?: task.requestedUrl}", fontWeight = FontWeight.SemiBold); task.failure?.let { Text("安全失败：$it", color = ErrorRed) }; task.items.forEach { item -> Column { Text(item.title, fontWeight = FontWeight.SemiBold); Text(item.body.take(600), style = MaterialTheme.typography.bodySmall); Text("${item.status}", color = SecondaryText); if (item.status == WebTextSnapshotItemStatus.PENDING_CONFIRMATION) Row { Button(onClick = { onConfirm(item) }) { Text("确认写入") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { onSkip(item.id) }) { Text("跳过") } } } } }
    } }, confirmButton = { if (state.selected == null) Button(onClick = onStart, enabled = state.confirmed && !state.working) { Text("开始安全抓取") } else { val task = requireNotNull(state.selected); if (task.status == WebTextSnapshotStatus.FAILED) Button(onClick = { onRetry(task) }, enabled = state.confirmed && !state.working) { Text("确认后重试") } else TextButton(onClick = onBack) { Text("返回") } } }, dismissButton = { if (state.selected != null && state.selected.status !in setOf(WebTextSnapshotStatus.COMPLETED, WebTextSnapshotStatus.CANCELLED)) TextButton(onClick = onCancel) { Text("取消") } else TextButton(onClick = onDismiss) { Text("关闭") } })
}
