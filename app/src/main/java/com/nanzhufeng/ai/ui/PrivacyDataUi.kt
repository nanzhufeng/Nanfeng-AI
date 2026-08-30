package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import com.nanzhufeng.ai.domain.ConversationSearchCategory
import com.nanzhufeng.ai.domain.PrivacyDeleteScope

@Composable
internal fun PrivacyDataPage(state: PrivacyDataUiState, onPreview: (PrivacyDeleteScope) -> Unit, onToggleTask: (com.nanzhufeng.ai.domain.PrivacyTaskDeletionCandidate) -> Unit, onPreviewSelectedTasks: () -> Unit, onConfirmation: (String) -> Unit, onDelete: () -> Unit, onRetryFailedTaskDeletion: () -> Unit, onCleanupImportedZipPackages: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val zipCleanup = state.inventory?.importedZipCleanup
                var zipCleanupDialogVisible by remember { mutableStateOf(false) }
                if (zipCleanup != null && zipCleanup.shouldShowImportedZipStatus()) {
                    ImportedZipCleanupReadiness(zipCleanup)
                }
                if (zipCleanup != null && (zipCleanup.originalPackageCount > 0 || zipCleanup.pendingDeletionCount > 0)) {
                    OutlinedButton(
                        onClick = { zipCleanupDialogVisible = true },
                        enabled = !state.working && (zipCleanup.canDeleteOriginalPackages || zipCleanup.pendingDeletionCount > 0),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = P5AInteractiveShape,
                        border = null,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = ErrorRed),
                    ) {
                        Text(
                            when {
                                zipCleanup.pendingDeletionCount > 0 -> "重新清理 ZIP 原始包"
                                zipCleanup.sourceDependentAttachmentCount > 0 -> "整理并删除 ZIP 原始包"
                                else -> "删除 ZIP 原始包"
                            },
                        )
                    }
                }
                if (zipCleanupDialogVisible) {
                    ImportedZipCleanupDialog(
                        status = requireNotNull(zipCleanup),
                        onDismiss = { if (!state.working) zipCleanupDialogVisible = false },
                        onConfirm = {
                            zipCleanupDialogVisible = false
                            onCleanupImportedZipPackages()
                        },
                    )
                }
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

private fun com.nanzhufeng.ai.domain.ImportedZipCleanupStatus.shouldShowImportedZipStatus(): Boolean =
    originalPackageCount > 0 || pendingDeletionCount > 0 || importedAttachmentCount > 0 || sourceDependentAttachmentCount > 0

@Composable
private fun ImportedZipCleanupReadiness(status: com.nanzhufeng.ai.domain.ImportedZipCleanupStatus) {
    val (title, detail, color) = when {
        status.pendingDeletionCount > 0 -> Triple(
            "ZIP 清理待完成",
            "原始包已移出导入位置，仍有 ${status.pendingDeletionCount} 个隔离文件待清理。请重新清理，期间不要手动删除应用文件。",
            AccentOrange,
        )
        status.blockedPackageCount > 0 -> Triple(
            "请保留 ZIP 原始包",
            "仍有 ${status.blockedPackageCount} 个导入任务未完成。完成前，原始包仍是恢复来源。",
            ErrorRed,
        )
        status.sourceDependentAttachmentCount > 0 -> Triple(
            "尚未全部内置",
            if (status.originalPackageCount > 0) {
                "${status.sourceDependentAttachmentCount} 个已归属附件仍依赖 ZIP。点“整理并删除”会先复制到本机受管存储并逐项校验；任一步失败都会保留原始包。"
            } else {
                "${status.sourceDependentAttachmentCount} 个已归属附件尚未转为本机受管存储，但找不到对应 ZIP 原始包。请重新导入原始包后再整理。"
            },
            AccentOrange,
        )
        status.originalPackageCount > 0 && status.allImportedAttachmentsManaged -> Triple(
            "可以安全删除 ZIP 原始包",
            "已确认导入资料不再依赖 ZIP；可删除 ${status.originalPackageCount} 个原始包。",
            AccentOrange,
        )
        else -> Triple(
            "导入资料已内置",
            "${status.importedAttachmentCount} 个导入附件已保存在本机受管存储，ZIP 原始包已删除。",
            AccentOrange,
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = NeutralSystemSurface,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = color, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun PrivacyStorageSummary(
    inventory: com.nanzhufeng.ai.domain.PrivacyInventory?,
    onOpenSearchCategory: (ConversationSearchCategory) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenProjects: () -> Unit,
) {
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
            val values = inventory.aggregates.associateBy { it.key }
            val contentRows = listOf(
                PrivacySummaryRow("对话", values["conversations"], "个") { onOpenSearchCategory(ConversationSearchCategory.ALL) },
                PrivacySummaryRow("消息", values["messages"], "条") { onOpenSearchCategory(ConversationSearchCategory.TEXT) },
                PrivacySummaryRow("记忆", values["memory"], "条", onOpenMemory),
                PrivacySummaryRow("知识", values["knowledge"], "条", onOpenKnowledge),
                PrivacySummaryRow("项目", values["projects"], "个", onOpenProjects),
            ).filter(PrivacySummaryRow::hasData)
            val attachmentRows = listOf(
                PrivacySummaryRow("图片", values["attachment_images"], "个") { onOpenSearchCategory(ConversationSearchCategory.IMAGE) },
                PrivacySummaryRow("视频", values["attachment_videos"], "个") { onOpenSearchCategory(ConversationSearchCategory.VIDEO) },
                PrivacySummaryRow("音频", values["attachment_audio"], "个") { onOpenSearchCategory(ConversationSearchCategory.AUDIO) },
                PrivacySummaryRow("文档与其他文件", values["attachment_files"], "个") { onOpenSearchCategory(ConversationSearchCategory.FILE) },
                PrivacySummaryRow("其他导入资料", values["import_source_assets"], "份"),
                PrivacySummaryRow("待清理残留文件", values["orphaned_attachment_files"], "个"),
            ).filter(PrivacySummaryRow::hasData)
            val totalBytes = (contentRows + attachmentRows).sumOf { it.aggregate?.byteCount ?: 0L }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("本机数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(formatStorageBytes(totalBytes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            PrivacySummarySection("对话与内容", contentRows)
            PrivacySummarySection("附件与导入资料", attachmentRows)
            PrivacyImportSummary(inventory)
        }
    }
}

private data class PrivacySummaryRow(
    val label: String,
    val aggregate: com.nanzhufeng.ai.domain.PrivacyAggregate?,
    val unit: String,
    val onClick: (() -> Unit)? = null,
) {
    fun hasData() = aggregate?.let { it.count > 0 || it.byteCount > 0 } == true
}

@Composable
private fun PrivacySummarySection(title: String, rows: List<PrivacySummaryRow>) {
    if (rows.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            val content: @Composable () -> Unit = {
                val aggregate = requireNotNull(row.aggregate)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.label, color = BodyText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${aggregate.count} ${row.unit} · ${formatStorageBytes(aggregate.byteCount)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    if (row.onClick != null) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = "进入${row.label}",
                            tint = SecondaryText,
                            modifier = Modifier.padding(start = 6.dp).size(scaledAppIconSize(18.dp)),
                        )
                    }
                }
            }
            if (row.onClick != null) {
                Surface(
                    onClick = row.onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = NeutralSystemSurface,
                    content = content,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = NeutralSystemSurface,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun PrivacyImportSummary(inventory: com.nanzhufeng.ai.domain.PrivacyInventory) {
    val values = inventory.aggregates.associateBy { it.key }
    val importedConversations = listOf("chatgpt_json_imported_conversations", "claude_json_imported_conversations", "zip_imported_conversations").sumOf { values[it]?.count ?: 0L }
    val importBatches = listOf("chatgpt_json_import_batches", "claude_json_import_batches", "zip_import_batches", "markdown_tasks", "json_tasks", "pdf_tasks", "web_tasks").sumOf { values[it]?.count ?: 0L }
    val importedAttachments = values["zip_imported_attachments"]?.count ?: 0L
    val importedAttachmentBytes = values["zip_imported_attachments"]?.byteCount ?: 0L
    val glmOcrTasks = values["glm_ocr_tasks"]?.count ?: 0L
    val glmOcrFiles = values["glm_ocr_attachments"]
    val profileFields = values["zip_imported_profile_fields"]?.count ?: 0L
    if (importedConversations == 0L && importBatches == 0L && importedAttachments == 0L && profileFields == 0L && glmOcrTasks == 0L) return
    Text("导入概况", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    listOf(
        "导入批次" to "$importBatches 批",
        "已导入对话" to "$importedConversations 个",
        "已导入附件" to "$importedAttachments 个 · ${formatStorageBytes(importedAttachmentBytes)}",
        "已导入个性化资料" to "$profileFields 项",
        "南枫转写" to "$glmOcrTasks 条 · ${glmOcrFiles?.count ?: 0L} 个文件 · ${formatStorageBytes(glmOcrFiles?.byteCount ?: 0L)}",
    ).filterNot { (_, value) -> value.startsWith("0 ") }.forEach { (label, value) ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(value, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ImportedZipCleanupDialog(
    status: com.nanzhufeng.ai.domain.ImportedZipCleanupStatus,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val detail = when {
        status.pendingDeletionCount > 0 ->
            "原始包已从导入位置移出，但隔离区仍有文件待清理。重新清理会继续完成这一步。"
        status.sourceDependentAttachmentCount > 0 ->
            "会先将 ${status.sourceDependentAttachmentCount} 个仍依赖 ZIP 的附件写入本机受管存储并逐项校验；成功后才删除原始包。任何一步失败都会保留原始包。"
        else ->
            "已确认导入资料不再依赖 ZIP。将删除 ${status.originalPackageCount} 个 ZIP 原始包，未归属文件也会移除。此操作不可撤销。"
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp),
            color = ForegroundSurface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("删除 ZIP 原始包", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = P5AInteractiveShape) { Text("取消") }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f), shape = P5AInteractiveShape) { Text("整理并删除") }
                }
            }
        }
    }
}

@Composable
private fun PrivacyCleanupScopeDialog(selected: PrivacyDeleteScope?, onDismiss: () -> Unit, onSelect: (PrivacyDeleteScope) -> Unit) {
    val scopes = listOf(
        PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS,
        PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES,
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
    bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun PrivacyDeleteScope.label(): String = when (this) {
    PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS -> "清理失败任务"
    PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES -> "清理残留附件"
    PrivacyDeleteScope.OFFLINE_EVAL_RUNS -> "清理本地运行记录"
    PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH -> "清空知识与记忆回收站"
    PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA -> "删除全部本地数据"
}

private fun PrivacyDeleteScope.detail(): String = when (this) {
    PrivacyDeleteScope.TEMPORARY_FAILED_TASK_ASSETS -> "选择后可逐项清理失败任务的附件。"
    PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES -> "清理已无消息、草稿、知识或转写任务引用，但仍占用空间的本机附件。"
    PrivacyDeleteScope.OFFLINE_EVAL_RUNS -> "清理本机运行记录。"
    PrivacyDeleteScope.KNOWLEDGE_MEMORY_TRASH -> "只清空已放入知识与记忆回收站的内容。"
    PrivacyDeleteScope.ALL_LOCAL_BUSINESS_DATA -> "删除全部本机业务数据，需输入确认文字。"
}
