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
import com.nanzhufeng.ai.data.P6KZipImportUiStore
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJob
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryState
import com.nanzhufeng.ai.domain.P6KZipAssetRole
import com.nanzhufeng.ai.domain.P6KZipManualLinkTarget
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

data class P6KZipImportUiState(val working: Boolean = false, val workingProvider: ThirdPartyZipProvider? = null, val tasks: List<P6KZipImportTask> = emptyList(), val recoveryJobs: Map<String, P6KZipAssetRecoveryJob> = emptyMap(), val viewedTaskId: String? = null, val manualTargets: List<P6KZipManualLinkTarget> = emptyList(), val selectedAssetEntryName: String? = null, val selectedTarget: P6KZipManualLinkTarget? = null, val revokeFailure: Boolean = false)
class P6KZipImportViewModel(private val store: P6KZipImportUiStore) : ViewModel() {
    var state by mutableStateOf(P6KZipImportUiState()); private set
    private var projectionRefresh: Job? = null
    init { show() }
    fun show() {
        projectionRefresh?.cancel()
        projectionRefresh = viewModelScope.launch {
            do {
                val snapshot = withContext(Dispatchers.IO) { store.list() to store.recoveryJobs() }
                state = state.copy(tasks = snapshot.first, recoveryJobs = snapshot.second.associateBy { it.taskId.value })
                val active = snapshot.second.any { it.state in setOf(P6KZipAssetRecoveryState.PENDING, P6KZipAssetRecoveryState.INDEXING, P6KZipAssetRecoveryState.MAPPING, P6KZipAssetRecoveryState.LINKING) }
                if (active) delay(750) else break
            } while (true)
        }
    }
    fun selected(provider: ThirdPartyZipProvider, name: String, mime: String, input: InputStream) { state = state.copy(working = true, workingProvider = provider, revokeFailure = false); viewModelScope.launch { val snapshot = withContext(Dispatchers.IO) { val task = store.stage(provider, name, mime, input); Triple(task, store.list(), store.recoveryJobs()) }; state = state.copy(working = false, workingProvider = null, tasks = listOf(snapshot.first) + snapshot.second.filterNot { it.id == snapshot.first.id }, recoveryJobs = snapshot.third.associateBy { it.taskId.value }); show() } }
    fun clear(id: String) { viewModelScope.launch { val result = withContext(Dispatchers.IO) { Triple(store.cancel(id), store.list(), store.recoveryJobs()) }; state = state.copy(tasks = result.second, recoveryJobs = result.third.associateBy { it.taskId.value }, revokeFailure = !result.first) } }
    fun retry(id: String) { viewModelScope.launch { val jobs = withContext(Dispatchers.IO) { store.retryAssetRecovery(id); store.recoveryJobs() }; state = state.copy(recoveryJobs = jobs.associateBy { it.taskId.value }); show() } }
    fun view(id: String) { viewModelScope.launch { state = state.copy(viewedTaskId = id, manualTargets = withContext(Dispatchers.IO) { store.manualLinkTargets(id) }, selectedAssetEntryName = null, selectedTarget = null) } }
    fun selectAsset(entryName: String) { state = state.copy(selectedAssetEntryName = entryName) }
    fun selectTarget(target: P6KZipManualLinkTarget) { state = state.copy(selectedTarget = target) }
    fun link() { val taskId = state.viewedTaskId ?: return; val entry = state.selectedAssetEntryName ?: return; val target = state.selectedTarget ?: return; state = state.copy(working = true); viewModelScope.launch { val updated = withContext(Dispatchers.IO) { store.linkUnmappedAsset(taskId, entry, target.conversationId.value, target.messageId.value) }; state = state.copy(working = false, tasks = listOf(updated) + store.list().filterNot { it.id == updated.id }, manualTargets = store.manualLinkTargets(taskId), selectedAssetEntryName = null, selectedTarget = null) } }
    class Factory(private val store: P6KZipImportUiStore) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = P6KZipImportViewModel(store) as T }
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
    onRetryZipRecovery: (String) -> Unit,
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
            val recovery = zipState.recoveryJobs[task.id.value]
            ImportResultDetailCard(
                title = "${task.provider.name} ZIP · 批次 ${index + 1}",
                status = chatGptStatusLabel(task.status.name),
                importedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.P6KZipItemStatus.CONFIRMED },
                failedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.P6KZipItemStatus.FAILED },
                skippedCount = task.items.count { it.status == com.nanzhufeng.ai.domain.P6KZipItemStatus.SKIPPED },
                restoredAssetCount = recovery?.linkedOccurrences ?: task.assets.count { it.attachmentId != null },
                unresolvedAssetCount = recovery?.unattributedCandidates ?: 0,
                missingSourceAssetCount = recovery?.missingEntries ?: 0,
                fallbackNamedAssetCount = recovery?.fallbackNamedAssets ?: 0,
                sourceReferenceRecords = recovery?.sourceReferenceRecords ?: 0,
                uniqueReferencedAssetCount = recovery?.let { it.uniqueAssets + it.missingEntries } ?: 0,
                recoveryLabel = recovery?.let { job ->
                    when (job.state) {
                        P6KZipAssetRecoveryState.PENDING -> "附件恢复已排队"
                        P6KZipAssetRecoveryState.INDEXING -> "正在索引导出包"
                        P6KZipAssetRecoveryState.MAPPING -> "正在确认官方归属"
                        P6KZipAssetRecoveryState.LINKING -> "正在恢复 ${job.linkedOccurrences}/${job.uniqueAssets}"
                        P6KZipAssetRecoveryState.COMPLETED -> "附件恢复已完成"
                        P6KZipAssetRecoveryState.PARTIAL -> "恢复已中断，可从 ${job.processedConversations}/${job.totalConversations} 续跑"
                        P6KZipAssetRecoveryState.FAILED -> "附件恢复未完成"
                    }
                },
                onRetryRecovery = recovery?.takeIf { it.state in setOf(P6KZipAssetRecoveryState.PARTIAL, P6KZipAssetRecoveryState.FAILED) }?.let { { onRetryZipRecovery(task.id.value) } },
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
    missingSourceAssetCount: Int = 0,
    fallbackNamedAssetCount: Int = 0,
    sourceReferenceRecords: Int = 0,
    uniqueReferencedAssetCount: Int = 0,
    recoveryLabel: String? = null,
    onRetryRecovery: (() -> Unit)? = null,
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
        if (missingSourceAssetCount > 0) Text("$missingSourceAssetCount 个附件有官方引用，但 ChatGPT 导出包中缺少文件；不是本地恢复丢失。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        if (unresolvedAssetCount > 0) Text("$unresolvedAssetCount 个文件缺少官方对话归属，未自动关联。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        if (fallbackNamedAssetCount > 0) Text("$fallbackNamedAssetCount 个已恢复附件缺少官方显示名，已使用文件 ID 回退命名。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        if (sourceReferenceRecords > uniqueReferencedAssetCount && uniqueReferencedAssetCount > 0) Text("$sourceReferenceRecords 条官方引用记录涉及 $uniqueReferencedAssetCount 个唯一附件 ID。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        recoveryLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BodyText) }
        onRetryRecovery?.let { retry -> OutlinedButton(onClick = retry, shape = P5AInteractiveShape, border = null) { Text("重试附件恢复") } }
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
