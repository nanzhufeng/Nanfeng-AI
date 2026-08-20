package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.data.AndroidP6KZipIntakeStore
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipAssetRole
import com.nanzhufeng.ai.domain.P6KZipManualLinkTarget
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

data class P6KZipImportUiState(val working: Boolean = false, val tasks: List<P6KZipImportTask> = emptyList(), val viewedTaskId: String? = null, val manualTargets: List<P6KZipManualLinkTarget> = emptyList(), val selectedAssetEntryName: String? = null, val selectedTarget: P6KZipManualLinkTarget? = null, val revokeFailure: Boolean = false)
class P6KZipImportViewModel(private val store: AndroidP6KZipIntakeStore) : ViewModel() {
    var state by mutableStateOf(P6KZipImportUiState()); private set
    init { show() }
    fun show() { viewModelScope.launch { state = state.copy(tasks = withContext(Dispatchers.IO) { store.list() }) } }
    fun selected(provider: ThirdPartyZipProvider, name: String, mime: String, input: InputStream) { state = state.copy(working = true, revokeFailure = false); viewModelScope.launch { val tasks = withContext(Dispatchers.IO) { val task = store.stage(provider, name, mime, input); listOf(task) + store.list().filterNot { it.id == task.id } }; state = state.copy(working = false, tasks = tasks) } }
    fun clear(id: String) { viewModelScope.launch { val result = withContext(Dispatchers.IO) { store.cancel(id) to store.list() }; state = state.copy(tasks = result.second, revokeFailure = !result.first) } }
    fun view(id: String) { viewModelScope.launch { state = state.copy(viewedTaskId = id, manualTargets = withContext(Dispatchers.IO) { store.manualLinkTargets(id) }, selectedAssetEntryName = null, selectedTarget = null) } }
    fun selectAsset(entryName: String) { state = state.copy(selectedAssetEntryName = entryName) }
    fun selectTarget(target: P6KZipManualLinkTarget) { state = state.copy(selectedTarget = target) }
    fun link() { val taskId = state.viewedTaskId ?: return; val entry = state.selectedAssetEntryName ?: return; val target = state.selectedTarget ?: return; state = state.copy(working = true); viewModelScope.launch { val updated = withContext(Dispatchers.IO) { store.linkUnmappedAsset(taskId, entry, target.conversationId.value, target.messageId.value) }; state = state.copy(working = false, tasks = listOf(updated) + store.list().filterNot { it.id == updated.id }, manualTargets = store.manualLinkTargets(taskId), selectedAssetEntryName = null, selectedTarget = null) } }
    class Factory(private val store: AndroidP6KZipIntakeStore) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = P6KZipImportViewModel(store) as T }
}

@androidx.compose.runtime.Composable fun P6KZipImportSettingsCard(state: P6KZipImportUiState, onChatGpt: () -> Unit, onClaude: () -> Unit, onClear: (String) -> Unit, onView: (String) -> Unit, onSelectAsset: (String) -> Unit, onSelectTarget: (P6KZipManualLinkTarget) -> Unit, onLink: () -> Unit) = WhiteCard {
    Text("ChatGPT / Claude ZIP 预检", style = MaterialTheme.typography.titleMedium)
    Text("选择 ZIP 后会直接导入已验证的文本对话；只会识别版本化的低风险个性化资料，账户与安全资料不会导入。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp)); Button(onClick = onChatGpt, enabled = !state.working, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("导入 ChatGPT ZIP") }
    Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = onClaude, enabled = !state.working, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("导入 Claude ZIP") }
    if (state.revokeFailure) Text("导入批次尚未完全撤销，已保留任务与私有副本；请重试删除。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    state.tasks.take(3).forEachIndexed { index, task -> Spacer(Modifier.height(10.dp)); Text("${task.provider.name} 导入批次 ${index + 1} · ${task.status}", style = MaterialTheme.typography.bodyMedium); Text(task.failure?.name ?: "${task.items.count { it.candidate != null }} 个对话结果 · ${task.assets.size} 个未关联媒体候选 · 个性化 ${task.profile.status}（${task.profile.mappedFieldCount}）", color = SecondaryText, style = MaterialTheme.typography.bodySmall); if (task.items.any { it.candidate != null }) OutlinedButton(onClick = { onView(task.id.value) }, enabled = !state.working) { Text("查看导入结果") }; OutlinedButton(onClick = { onClear(task.id.value) }, enabled = !state.working) { Text("删除导入批次") } }
    state.tasks.firstOrNull { it.id.value == state.viewedTaskId }?.let { task ->
        Spacer(Modifier.height(12.dp)); Text("${task.provider.name} 对话导入结果", style = MaterialTheme.typography.titleSmall)
        Text("仅导入严格文本；未关联媒体和账户资料未导入。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        task.items.filter { it.candidate != null }.forEachIndexed { index, item ->
            Spacer(Modifier.height(8.dp)); Text("对话 ${index + 1}", style = MaterialTheme.typography.bodyMedium); Text(item.status.name, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        val candidates = task.assets.filter { it.role != P6KZipAssetRole.MANUAL_LINKED }
        if (candidates.isNotEmpty()) {
            Spacer(Modifier.height(12.dp)); Text("人工关联未关联媒体", style = MaterialTheme.typography.titleSmall)
            Text("先选择一项媒体，再选择该 ZIP 已导入会话中的一条消息；不会显示文件名或内容。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            candidates.forEachIndexed { index, asset -> OutlinedButton(onClick = { onSelectAsset(asset.entryName) }, enabled = !state.working && state.manualTargets.isNotEmpty()) { Text("媒体 ${index + 1} · ${asset.mimeType} · ${asset.byteCount} B${if (asset.role == P6KZipAssetRole.MANUAL_LINK_FAILED) " · 可重试" else ""}") } }
            state.manualTargets.forEachIndexed { index, target -> OutlinedButton(onClick = { onSelectTarget(target) }, enabled = !state.working) { Text("对话 ${index + 1} · ${target.role.name} · 第 ${target.messageOrdinal} 条") } }
            Button(onClick = onLink, enabled = !state.working && state.selectedAssetEntryName != null && state.selectedTarget != null, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("关联到所选消息") }
        }
    }
}
