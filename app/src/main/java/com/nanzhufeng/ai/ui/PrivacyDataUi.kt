package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import com.nanzhufeng.ai.domain.PrivacyDeleteScope

@Composable
internal fun PrivacyDataPage(state: PrivacyDataUiState, onPreview: (PrivacyDeleteScope) -> Unit, onToggleTask: (com.nanzhufeng.ai.domain.PrivacyTaskDeletionCandidate) -> Unit, onPreviewSelectedTasks: () -> Unit, onConfirmation: (String) -> Unit, onDelete: () -> Unit, onRetryFailedTaskDeletion: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PrivacyStorageSummary(state.inventory)
                var cleanupDialogVisible by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { cleanupDialogVisible = true },
                    enabled = !state.working,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = P5AInteractiveShape,
                    border = null,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText),
                ) { Text(state.preview?.scope?.label() ?: "选择清理范围") }
                if (cleanupDialogVisible) {
                    PrivacyCleanupScopeDialog(
                        selected = state.preview?.scope,
                        onDismiss = { cleanupDialogVisible = false },
                        onSelect = { scope -> onPreview(scope); cleanupDialogVisible = false },
                    )
                }
                if (state.taskCandidates.isNotEmpty()) {
                    Text("选择要清理的失败任务。", color = SecondaryText)
                    state.taskCandidates.forEach { candidate ->
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(candidate.selectionId in state.selectedTaskIds, { onToggleTask(candidate) }, enabled = !state.working)
                            Text("失败任务 · 附件 ${candidate.privateAssetCount} 个", modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    OutlinedButton(onClick = onPreviewSelectedTasks, enabled = !state.working && state.selectedTaskIds.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("预览已选 ${state.selectedTaskIds.size} 项") }
                }
                state.preview?.let { preview ->
                    Text("已确认清理范围。", color = SecondaryText)
                    if (preview.scope.requiresPhrase) {
                        Text("确认文字", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = state.confirmation,
                            onValueChange = onConfirmation,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("输入：删除全部本地业务数据") },
                            singleLine = true,
                            shape = P5AInteractiveShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                focusedContainerColor = ForegroundSurface,
                                unfocusedContainerColor = ForegroundSurface,
                            ),
                        )
                    }
                    Button(onClick = onDelete, enabled = !state.working && (!preview.scope.requiresPhrase || state.confirmation == "删除全部本地业务数据"), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = P5AInteractiveShape, colors = ButtonDefaults.buttonColors(containerColor = ForegroundSurface, contentColor = ErrorRed)) { Text(if (preview.scope.requiresPhrase) "确认删除全部本地业务数据" else "确认删除此范围") }
                }
                if (state.retryAvailable) OutlinedButton(onClick = onRetryFailedTaskDeletion, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("重新清理") }
                state.notice?.let { Text(it, color = AccentOrange) }; state.error?.let { Text(it, color = ErrorRed) }
    }
}

@Composable
private fun PrivacyStorageSummary(inventory: com.nanzhufeng.ai.domain.PrivacyInventory?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ForegroundSurface,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (inventory == null) {
                Text("正在读取本机数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                return@Column
            }
            val totalBytes = inventory.aggregates.sumOf { it.byteCount }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("本机数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("数据默认保存在本机", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
                Text(formatStorageBytes(totalBytes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("API Key", color = SecondaryText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(if (inventory.credentialReferencePresent) "已保存在本机" else "未保存", color = if (inventory.credentialReferencePresent) AccentOrange else SecondaryText, style = MaterialTheme.typography.bodyMedium)
            }
            inventory.aggregates
                .filter { it.count > 0 || it.byteCount > 0 }
                .sortedBy { it.key }
                .forEach { aggregate ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(privacyAggregateLabel(aggregate.key), color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${aggregate.count} 项", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        if (aggregate.byteCount > 0) {
                            Spacer(Modifier.padding(start = 8.dp))
                            Text(formatStorageBytes(aggregate.byteCount), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
        }
    }
}

@Composable
private fun PrivacyCleanupScopeDialog(selected: PrivacyDeleteScope?, onDismiss: () -> Unit, onSelect: (PrivacyDeleteScope) -> Unit) {
    val scopes = listOf(
        PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS,
        PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH,
        PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp),
            color = ForegroundSurface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("选择清理范围", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                scopes.forEach { scope ->
                    Surface(
                        onClick = { onSelect(scope) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = if (scope == selected) AccentOrangeSoft else NeutralSystemSurface,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(scope.label(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(scope.detail(), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun formatStorageBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun PrivacyDeleteScope.label(): String = when (this) {
    PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS -> "清理失败任务"
    PrivacyDeleteScope.OFFLINE_EVAL_RUNS -> "清理本地运行记录"
    PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH -> "清空知识与记忆回收站"
    PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA -> "删除全部本地数据"
}

private fun PrivacyDeleteScope.detail(): String = when (this) {
    PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS -> "选择后可逐项清理失败任务的附件。"
    PrivacyDeleteScope.OFFLINE_EVAL_RUNS -> "清理本机运行记录。"
    PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH -> "只清空已放入知识与记忆回收站的内容。"
    PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA -> "删除全部本机业务数据，需输入确认文字。"
}

private fun privacyAggregateLabel(key: String): String = when {
    key == "conversation_drafts" -> "对话草稿"
    key == "conversations" -> "对话"
    key == "messages" -> "消息"
    key == "memory" -> "记忆"
    key.contains("assets") -> "附件与导入资料"
    else -> key.replace('_', ' ')
}
