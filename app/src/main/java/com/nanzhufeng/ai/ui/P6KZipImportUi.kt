package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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

data class P6KZipImportUiState(val working: Boolean = false, val workingProvider: ThirdPartyZipProvider? = null, val tasks: List<P6KZipImportTask> = emptyList(), val viewedTaskId: String? = null, val manualTargets: List<P6KZipManualLinkTarget> = emptyList(), val selectedAssetEntryName: String? = null, val selectedTarget: P6KZipManualLinkTarget? = null, val revokeFailure: Boolean = false)
class P6KZipImportViewModel(private val store: AndroidP6KZipIntakeStore) : ViewModel() {
    var state by mutableStateOf(P6KZipImportUiState()); private set
    init { show() }
    fun show() { viewModelScope.launch { state = state.copy(tasks = withContext(Dispatchers.IO) { store.list() }) } }
    fun selected(provider: ThirdPartyZipProvider, name: String, mime: String, input: InputStream) { state = state.copy(working = true, workingProvider = provider, revokeFailure = false); viewModelScope.launch { val tasks = withContext(Dispatchers.IO) { val task = store.stage(provider, name, mime, input); listOf(task) + store.list().filterNot { it.id == task.id } }; state = state.copy(working = false, workingProvider = null, tasks = tasks) } }
    fun clear(id: String) { viewModelScope.launch { val result = withContext(Dispatchers.IO) { store.cancel(id) to store.list() }; state = state.copy(tasks = result.second, revokeFailure = !result.first) } }
    fun view(id: String) { viewModelScope.launch { state = state.copy(viewedTaskId = id, manualTargets = withContext(Dispatchers.IO) { store.manualLinkTargets(id) }, selectedAssetEntryName = null, selectedTarget = null) } }
    fun selectAsset(entryName: String) { state = state.copy(selectedAssetEntryName = entryName) }
    fun selectTarget(target: P6KZipManualLinkTarget) { state = state.copy(selectedTarget = target) }
    fun link() { val taskId = state.viewedTaskId ?: return; val entry = state.selectedAssetEntryName ?: return; val target = state.selectedTarget ?: return; state = state.copy(working = true); viewModelScope.launch { val updated = withContext(Dispatchers.IO) { store.linkUnmappedAsset(taskId, entry, target.conversationId.value, target.messageId.value) }; state = state.copy(working = false, tasks = listOf(updated) + store.list().filterNot { it.id == updated.id }, manualTargets = store.manualLinkTargets(taskId), selectedAssetEntryName = null, selectedTarget = null) } }
    class Factory(private val store: AndroidP6KZipIntakeStore) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = P6KZipImportViewModel(store) as T }
}

@androidx.compose.runtime.Composable fun P6KZipImportSettingsCard(state: P6KZipImportUiState, onChatGpt: () -> Unit, onClaude: () -> Unit, grouped: Boolean = false) = Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
    if (grouped) {
        DataStorageGroupedActionRow(label = "导入 ChatGPT ZIP", onClick = onChatGpt, enabled = !state.working, working = state.workingProvider == ThirdPartyZipProvider.CHATGPT)
        DataStorageGroupedDivider()
        DataStorageGroupedActionRow(label = "导入 Claude ZIP", onClick = onClaude, enabled = !state.working, working = state.workingProvider == ThirdPartyZipProvider.CLAUDE)
    } else {
        Text("ZIP 导入", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onChatGpt, enabled = !state.working, modifier = Modifier.weight(1f).height(48.dp), shape = P5AInteractiveShape, colors = ButtonDefaults.buttonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("ChatGPT") }
            OutlinedButton(onClick = onClaude, enabled = !state.working, modifier = Modifier.weight(1f).height(48.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("Claude") }
        }
    }
}

/** A single privacy-safe detail page for JSON and ZIP imports. It never renders source names or chat text. */
@androidx.compose.runtime.Composable fun ImportResultsDetailsPage(
    chatGptState: ChatGptImportUiState,
    claudeState: ClaudeImportUiState,
    zipState: P6KZipImportUiState,
    onClearZipBatch: (String) -> Unit,
    showJson: Boolean,
    showZip: Boolean,
) {
    var pendingDeleteZipId by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("按导入来源和批次汇总。为保护本机隐私，这里不显示聊天正文、标题或原始文件名。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        if (showZip && zipState.revokeFailure) Text("导入批次尚未完全撤销，已保留任务与私有副本；请重试删除。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        if ((showJson && chatGptState.tasks.isEmpty() && claudeState.tasks.isEmpty()) || (showZip && zipState.tasks.isEmpty())) {
            Text("还没有导入记录。", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }
        if (showJson) chatGptState.tasks.forEachIndexed { index, task ->
            ImportResultDetailCard(
                title = "ChatGPT JSON · 批次 ${index + 1}",
                status = chatGptStatusLabel(task.status.name),
                importedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.ChatGptImportItemStatus.CONFIRMED },
                failedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.ChatGptImportItemStatus.FAILED },
                skippedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.ChatGptImportItemStatus.SKIPPED },
            )
        }
        if (showJson) claudeState.tasks.forEachIndexed { index, task ->
            ImportResultDetailCard(
                title = "Claude JSON · 批次 ${index + 1}",
                status = chatGptStatusLabel(task.status.name),
                importedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.ClaudeImportItemStatus.CONFIRMED },
                failedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.ClaudeImportItemStatus.FAILED },
                skippedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.ClaudeImportItemStatus.SKIPPED },
            )
        }
        if (showZip) zipState.tasks.forEachIndexed { index, task ->
            ImportResultDetailCard(
                title = "${task.provider.name} ZIP · 批次 ${index + 1}",
                status = chatGptStatusLabel(task.status.name),
                importedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.P6KZipItemStatus.CONFIRMED },
                failedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.P6KZipItemStatus.FAILED },
                skippedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.P6KZipItemStatus.SKIPPED },
                restoredAssetCount = task.assets.count { it.attachmentId != null },
                unresolvedAssetCount = task.assets.count { it.attachmentId == null },
                profileSummary = when {
                    task.profile.mappedFieldCount > 0 -> "${task.profile.mappedFieldCount} 项个性化资料已按安全规则处理"
                    else -> "未导入个性化资料"
                },
                onDelete = { pendingDeleteZipId = task.id.value },
                deleteEnabled = !zipState.working,
            )
        }
    }
    pendingDeleteZipId?.let { taskId ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDeleteZipId = null },
            title = { Text("删除本批次？") },
            text = { Text("这会从本机列表移除该批次导入的对话，不会删除原有对话或源文件。") },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingDeleteZipId = null }) { Text("取消") } },
            confirmButton = {
                Button(onClick = { onClearZipBatch(taskId); pendingDeleteZipId = null }, shape = P5AInteractiveShape) { Text("删除本批次") }
            },
        )
    }
}

@androidx.compose.runtime.Composable private fun ImportResultDetailCard(
    title: String,
    status: String,
    importedCount: Int,
    failedCount: Int,
    skippedCount: Int,
    restoredAssetCount: Int = 0,
    unresolvedAssetCount: Int = 0,
    profileSummary: String? = null,
    onDelete: (() -> Unit)? = null,
    deleteEnabled: Boolean = true,
) = Surface(modifier = Modifier.fillMaxWidth(), shape = CardShape, color = ForegroundSurface) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = BodyText)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = AccentOrange)
        }
        Text("$importedCount 个对话已导入", style = MaterialTheme.typography.bodyMedium, color = BodyText)
        if (failedCount > 0) Text("$failedCount 条无可显示正文", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        if (skippedCount > 0) Text("$skippedCount 条已跳过", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        if (restoredAssetCount > 0) Text("$restoredAssetCount 个附件已恢复到原对话。", style = MaterialTheme.typography.bodySmall, color = BodyText)
        if (unresolvedAssetCount > 0) Text("$unresolvedAssetCount 个文件缺少官方对话归属，未自动关联。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        profileSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SecondaryText) }
        onDelete?.let { action ->
            OutlinedButton(onClick = action, enabled = deleteEnabled, shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = SettingsPageBackground, contentColor = BodyText)) { Text("删除本批次") }
        }
    }
}

private fun chatGptStatusLabel(rawStatus: String): String = when (rawStatus) {
    "COMPLETED" -> "已完成"
    "FAILED" -> "未完成"
    "CANCELLED" -> "已取消"
    "PARTIALLY_COMPLETED" -> "部分完成"
    "AWAITING_CONFIRMATION" -> "等待确认"
    "PARSING" -> "正在导入"
    else -> "已创建"
}
