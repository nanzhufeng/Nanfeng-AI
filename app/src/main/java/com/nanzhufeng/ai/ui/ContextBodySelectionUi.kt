package com.nanzhufeng.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nanzhufeng.ai.domain.ContextBodyCandidate
import com.nanzhufeng.ai.domain.ContextBodyLayer
import com.nanzhufeng.ai.domain.ContextBodySourceKind
import com.nanzhufeng.ai.domain.MemoryId
import com.nanzhufeng.ai.domain.LocalContextCompressionBudgetPreset
import com.nanzhufeng.ai.domain.MemoryDomain

@Composable
fun ContextBodySelectionDialog(state: ContextBodySelectionUiState, viewModel: ContextBodySelectionViewModel) {
    Dialog(onDismissRequest = viewModel::dismissDialog, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.White, shape = CardShape, modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("本次 Context 正文选择", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("所有项默认关闭；只作本机瞬时预览，不会发送给 Provider。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    }
                    IconButton(onClick = viewModel::dismissDialog) { Icon(Icons.Outlined.Close, contentDescription = "关闭 Context 选择") }
                }
                if (state.isLoading) Text("正在从本机核对当前会话范围…", color = SecondaryText)
                else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val candidates = state.plan?.candidates.orEmpty()
                    SelectionRow("L1 · 当前项目指令", "仅当前项目最新非空 revision。", state.includeProjectInstruction, candidates.any { it.kind == ContextBodySourceKind.PROJECT_INSTRUCTION }, viewModel::toggleProjectInstruction)
                    SelectionRow("L2 · 当前会话路径", "仅当前根到叶 Text；草稿、分支、附件和 Tool 均排除。", state.includeConversationPath, candidates.any { it.kind == ContextBodySourceKind.CONVERSATION_PATH }, viewModel::toggleConversationPath)
                    candidates.filter { viewModel.isMemoryCandidate(it.kind) }.groupBy(ContextBodyCandidate::layer).forEach { (layer, items) ->
                        Text(layerLabel(layer), style = MaterialTheme.typography.labelLarge, color = SecondaryText)
                        items.forEach { candidate ->
                            SelectionRow(candidate.title, memoryDescription(candidate.kind), MemoryId(candidate.id) in state.selectedMemoryIds, true) { viewModel.toggleMemory(MemoryId(candidate.id)) }
                        }
                    }
                    Text("Knowledge（默认关闭）", style = MaterialTheme.typography.labelLarge, color = SecondaryText)
                    OutlinedTextField(
                        value = state.knowledgeQuery,
                        onValueChange = viewModel::searchKnowledge,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("搜索标题、正文、标签或来源") },
                    )
                    if (state.knowledgeQuery.isNotBlank() && state.knowledgeResults.isEmpty()) Text("没有当前 GLOBAL/项目范围内的 ACTIVE Knowledge。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    state.knowledgeResults.forEach { result ->
                        SelectionRow(result.title, "${if (result.scope.name == "GLOBAL") "GLOBAL" else "当前项目"} · ${result.snippet}", result.id in state.selectedKnowledgeIds, true) { viewModel.toggleKnowledge(result) }
                    }
                    Text("L3 · 本地短期操作轨迹（默认关闭）", style = MaterialTheme.typography.labelLarge, color = SecondaryText)
                    SelectionRow(
                        "启用 LOCAL_L3_METADATA",
                        "仅当前会话的终态本地动作元数据；不读取正文、日志、Provider 事件或缓存。",
                        state.localActionTraceEnabled,
                        state.localActionTracePlan != null,
                        viewModel::toggleLocalActionTraceEnabled,
                    )
                    if (state.localActionTraceEnabled) {
                        val traceCandidates = state.localActionTracePlan?.candidates.orEmpty()
                        if (traceCandidates.isEmpty()) Text("当前会话没有可证明用户发起的终态本地动作。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                        traceCandidates.forEach { candidate ->
                            SelectionRow(
                                "${actionLabel(candidate.actionKind)} · ${candidate.terminalState.name}",
                                "安全 ID：${shortHash(candidate.selectionId)} · ${candidate.occurredAt} · ${candidate.sourceVersion}",
                                candidate.selectionId in state.selectedLocalActionTraceIds,
                                true,
                            ) { viewModel.toggleLocalActionTrace(candidate.selectionId) }
                        }
                        Text("编辑用户消息和切分支缺少 append-only 动作谱系，已排除；不显示正文、模型、Provider、Token、费用、附件、URI、路径、命令或日志。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                        state.localActionTracePreview?.let { preview ->
                            Text("LOCAL_L3_METADATA · ${preview.format} · ${preview.entries.size} 条", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            preview.entries.forEach { entry ->
                                Surface(color = Color(0xFFF5F8F6), shape = CardShape, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${actionLabel(entry.actionKind)} · ${entry.terminalState.name}", style = MaterialTheme.typography.labelLarge)
                                        Text("安全 ID：${shortHash(entry.stableIdSafeSummary)} · ${entry.occurredAt}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                        Text("来源版本：${entry.sourceVersion} · 当前会话元数据，不是正文或 Prompt。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = viewModel::previewLocalActionTrace,
                            enabled = !state.isLoading && state.selectedLocalActionTraceIds.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("本机预览 L3 元数据") }
                    }
                    Text("抽取预算（由 P4-J 本机策略解释）", style = MaterialTheme.typography.labelLarge, color = SecondaryText)
                    LocalContextCompressionBudgetPreset.entries.forEach { preset ->
                        SelectionRow(budgetTitle(preset), budgetDetail(preset), state.budgetPreset == preset, true) { viewModel.selectBudget(preset) }
                    }
                    state.preview?.let { preview ->
                        Text("EXTRACTIVE_LOCAL · p4j-extractive-v1 · ${preview.compression.entries.size} 条", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("这是确定性前后原文抽取，不是语义摘要；不会构造 Prompt 或发送给 Provider。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                        if (preview.compression.entries.isEmpty()) Text("尚未选择来源，因此零条压缩结果。", color = SecondaryText)
                        preview.compression.entries.forEach { entry ->
                            Surface(color = Color(0xFFF5F8F6), shape = CardShape, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${layerLabel(entry.layer)} · ${entry.kind.name}", style = MaterialTheme.typography.labelLarge)
                                    Text("来源 ID 安全摘要：${safeSummary(entry.sourceId)} · ${if (entry.omittedCodePointCount > 0) "已截断" else "未截断"}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                    Text("原始 ${entry.originalCodePointCount} · 保留 ${entry.retainedCodePointCount} · 省略 ${entry.omittedCodePointCount} code points", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                    Text("source SHA-256：${shortHash(entry.sourceContentHash)} · compressed SHA-256：${shortHash(entry.compressedContentHash)}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                }
                            }
                        }
                        val prefix = preview.stablePrefix
                        Text("稳定前缀元数据 · ${prefix.sources.size} 个显式稳定来源", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("${prefix.format} · fingerprint：${shortHash(prefix.fingerprint)}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                        Text(
                            if (prefix.sources.isEmpty()) "本次没有已明确选择的稳定来源；未形成可用前缀内容。"
                            else "仅记录 L0/L1 的 layer、kind、修订和 hash；没有 Provider cache、命中率或费用结论。",
                            style = MaterialTheme.typography.bodySmall, color = SecondaryText,
                        )
                        if (state.prefixInvalidationReasons.isNotEmpty()) Text("若未来存在缓存，此次变化应失效：${state.prefixInvalidationReasons.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    }
                }
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SecondaryText) }
                Button(onClick = viewModel::previewExtractive, enabled = !state.isLoading && state.plan != null, modifier = Modifier.fillMaxWidth()) { Text("本机抽取预览") }
                OutlinedButton(onClick = viewModel::dismissDialog, modifier = Modifier.fillMaxWidth()) { Text("关闭并丢弃选择") }
            }
        }
    }
}

@Composable
private fun SelectionRow(title: String, detail: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Surface(color = Color(0xFFF5F8F6), shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = if (enabled) { { _: Boolean -> onToggle() } } else null)
            Column(Modifier.weight(1f)) { Text(title); Text(detail, style = MaterialTheme.typography.bodySmall, color = SecondaryText) }
        }
    }
}

private fun layerLabel(layer: ContextBodyLayer): String = when (layer) {
    ContextBodyLayer.L0_STABLE -> "L0 稳定上下文"
    ContextBodyLayer.L1_PROJECT -> "L1 当前项目"
    ContextBodyLayer.L2_CONVERSATION -> "L2 当前会话"
    ContextBodyLayer.L3_RECENT_ACTIONS -> "L3 短期操作"
}
private fun memoryDescription(kind: ContextBodySourceKind): String = when (kind) {
    ContextBodySourceKind.GLOBAL_MEMORY -> "GLOBAL · 仅本次明确选择"
    ContextBodySourceKind.PROJECT_MEMORY -> "当前 Project · 仅本次明确选择"
    ContextBodySourceKind.CONVERSATION_MEMORY -> "当前 Conversation · 仅本次明确选择"
    else -> ""
}

private fun budgetTitle(preset: LocalContextCompressionBudgetPreset): String = when (preset) {
    LocalContextCompressionBudgetPreset.COMPACT -> "紧凑 · 160 / 480 code points"
    LocalContextCompressionBudgetPreset.BALANCED -> "平衡 · 480 / 1800 code points"
    LocalContextCompressionBudgetPreset.EXPANDED -> "扩展 · 960 / 3600 code points"
}
private fun budgetDetail(preset: LocalContextCompressionBudgetPreset): String = when (preset) {
    LocalContextCompressionBudgetPreset.COMPACT -> "单条 / 总预算；仅本机预览。"
    LocalContextCompressionBudgetPreset.BALANCED -> "单条 / 总预算；默认档位。"
    LocalContextCompressionBudgetPreset.EXPANDED -> "单条 / 总预算；不会改变来源或算法。"
}
private fun safeSummary(sourceId: String): String = "id#${MemoryDomain.sha256(sourceId).take(12)}"
private fun shortHash(value: String): String = "${value.take(12)}…"
private fun actionLabel(action: com.nanzhufeng.ai.domain.ConversationActionKind): String = when (action) {
    com.nanzhufeng.ai.domain.ConversationActionKind.CONTINUE -> "继续"
    com.nanzhufeng.ai.domain.ConversationActionKind.RETRY -> "重试"
    com.nanzhufeng.ai.domain.ConversationActionKind.CHANGE_MODEL -> "换模型重答"
}
