package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nanzhufeng.ai.domain.PrivacyDeleteScope

@Composable
internal fun PrivacyDataDialog(state: PrivacyDataUiState, onDismiss: () -> Unit, onPreview: (PrivacyDeleteScope) -> Unit, onToggleTask: (com.nanzhufeng.ai.domain.PrivacyTaskDeletionCandidate) -> Unit, onPreviewSelectedTasks: () -> Unit, onConfirmation: (String) -> Unit, onDelete: () -> Unit, onRetryFailedTaskDeletion: () -> Unit, onOpenBackup: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("隐私与数据") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("数据默认保存在本机。")
                state.inventory?.let { inventory ->
                    Text(if (inventory.credentialReferencePresent) "API Key 已保存在本机。" else "尚未保存 API Key。", color = SecondaryText)
                }
                OutlinedButton(onClick = onOpenBackup, enabled = !state.working, modifier = Modifier.fillMaxWidth(), shape = P5AInteractiveShape) { Text("打开备份与恢复") }
                var cleanupExpanded by remember { mutableStateOf(false) }
                val cleanupScopes = listOf(
                    PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS,
                    PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH,
                    PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA,
                )
                Box {
                    OutlinedButton(
                        onClick = { cleanupExpanded = true },
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                        shape = P5AInteractiveShape,
                    ) {
                        Text(state.preview?.scope?.label() ?: "选择清理范围", modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "选择清理范围")
                    }
                    DropdownMenu(
                        expanded = cleanupExpanded,
                        onDismissRequest = { cleanupExpanded = false },
                        containerColor = Color.White,
                        shape = P5AInteractiveShape,
                    ) {
                        cleanupScopes.forEach { scope ->
                            DropdownMenuItem(
                                text = { Text(scope.label()) },
                                onClick = { onPreview(scope); cleanupExpanded = false },
                            )
                        }
                    }
                }
                if (state.taskCandidates.isNotEmpty()) {
                    Text("选择要清理的失败任务。", color = SecondaryText)
                    state.taskCandidates.forEach { candidate ->
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(candidate.selectionId in state.selectedTaskIds, { onToggleTask(candidate) }, enabled = !state.working)
                            Text("失败任务 · 附件 ${candidate.privateAssetCount} 个", modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    OutlinedButton(onClick = onPreviewSelectedTasks, enabled = !state.working && state.selectedTaskIds.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = P5AInteractiveShape) { Text("预览已选 ${state.selectedTaskIds.size} 项") }
                }
                state.preview?.let { preview ->
                    Text("已确认清理范围。", color = SecondaryText)
                    if (preview.scope.requiresPhrase) {
                        OutlinedTextField(value = state.confirmation, onValueChange = onConfirmation, modifier = Modifier.fillMaxWidth(), label = { Text("输入：删除全部本地业务数据") }, singleLine = true)
                    }
                    Button(onClick = onDelete, enabled = !state.working && (!preview.scope.requiresPhrase || state.confirmation == "删除全部本地业务数据"), modifier = Modifier.fillMaxWidth(), shape = P5AInteractiveShape) { Text(if (preview.scope.requiresPhrase) "确认删除全部本地业务数据" else "确认删除此范围") }
                }
                if (state.retryAvailable) OutlinedButton(onClick = onRetryFailedTaskDeletion, enabled = !state.working, modifier = Modifier.fillMaxWidth(), shape = P5AInteractiveShape) { Text("重新清理") }
                state.notice?.let { Text(it, color = BrandGreen) }; state.error?.let { Text(it, color = ErrorRed) }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss, enabled = !state.working, shape = P5AInteractiveShape) { Text("关闭") } },
    )
}

private fun PrivacyDeleteScope.label(): String = when (this) {
    PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS -> "清理失败任务"
    PrivacyDeleteScope.OFFLINE_EVAL_RUNS -> "清理本地运行记录"
    PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH -> "清空知识与记忆回收站"
    PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA -> "删除全部本地数据"
}
