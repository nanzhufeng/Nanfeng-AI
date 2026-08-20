package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nanzhufeng.ai.domain.KnowledgeRelationshipId
import com.nanzhufeng.ai.domain.KnowledgeRelationshipMutationResult
import com.nanzhufeng.ai.domain.KnowledgeRelationshipStatus
import com.nanzhufeng.ai.domain.KnowledgeRelationshipType

@Composable
fun KnowledgeRelationshipBuilderDialog(state: KnowledgeRelationshipUiState, onDismiss: () -> Unit, onType: (KnowledgeRelationshipType) -> Unit, onTarget: (com.nanzhufeng.ai.domain.KnowledgeSearchResult) -> Unit, onConfirm: () -> Unit) {
    val anchor = state.anchor ?: return
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("建立本地 Knowledge 关系", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("仅对两个现有、活动且同一 GLOBAL/项目范围的 Knowledge 生效。不会合并正文、删除条目或加入 Context。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("起点：${anchor.item.title} · 修订 ${anchor.revisions.maxOfOrNull { it.revision } ?: 1}", fontWeight = FontWeight.Medium)
                Text("先选择关系类型", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { KnowledgeRelationshipType.entries.forEach { type -> TextButton(onClick = { onType(type) }) { Text(if (state.type == type) "● ${type.label()}" else type.label()) } } }
                Text("再选择另一项并确认", style = MaterialTheme.typography.labelLarge)
                if (state.candidates.isEmpty()) Text("没有同一范围内的其他活动 Knowledge。", color = SecondaryText)
                state.candidates.forEach { candidate ->
                    TextButton(onClick = { onTarget(candidate) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(if (state.selectedTarget?.id == candidate.id) "● ${candidate.title}" else candidate.title, fontWeight = FontWeight.Medium)
                            Text("${candidate.scope.name} · 修订 ${candidate.revision}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                state.result?.let { Text(it.label(), color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = state.selectedTarget != null) { Text("确认建立") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭（不建立）") } },
    )
}

@Composable
fun KnowledgeRelationshipListDialog(state: KnowledgeRelationshipUiState, onDismiss: () -> Unit, onStatus: (KnowledgeRelationshipStatus?) -> Unit, onRevoke: (KnowledgeRelationshipId) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("本地关系与审计", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("关系不复制正文、URI、路径或附件，也不会自动进入 Context、Prompt、导出或 Provider。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Row { listOf(KnowledgeRelationshipStatus.ACTIVE, KnowledgeRelationshipStatus.REVOKED, null).forEach { status -> TextButton(onClick = { onStatus(status) }) { Text(if (state.recordStatus == status) "● ${status.label()}" else status.label()) } } }
                if (state.records.isEmpty()) Text("此筛选下没有关系记录。", color = SecondaryText)
                state.records.forEach { snapshot ->
                    val relationship = snapshot.relationship
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(relationship.type.label(), fontWeight = FontWeight.Medium)
                        Text("端点：${relationship.fromKnowledgeId.value} → ${relationship.toKnowledgeId.value}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        Text("${relationship.status.label()} · 审计修订 ${snapshot.revisions.size} · ${relationship.suggestionSource.sourceLabel()}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        if (relationship.status == KnowledgeRelationshipStatus.ACTIVE) TextButton(onClick = { onRevoke(relationship.id) }) { Text("撤销关系") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun KnowledgeRelationshipType.label() = when (this) {
    KnowledgeRelationshipType.RELATED -> "相关"
    KnowledgeRelationshipType.DUPLICATE_CANDIDATE -> "疑似重复"
    KnowledgeRelationshipType.SUPPORTS -> "支持"
    KnowledgeRelationshipType.CONTRADICTS -> "矛盾"
}
private fun KnowledgeRelationshipStatus?.label() = when (this) { KnowledgeRelationshipStatus.ACTIVE -> "活动"; KnowledgeRelationshipStatus.REVOKED -> "已撤销"; null -> "全部" }
private fun com.nanzhufeng.ai.domain.KnowledgeRelationshipSuggestionSource.sourceLabel() = if (this == com.nanzhufeng.ai.domain.KnowledgeRelationshipSuggestionSource.P4F_DEDUPLICATION_CANDIDATE) "来自 P4-F 建议" else "人工选择"
private fun KnowledgeRelationshipMutationResult.label() = when (this) {
    is KnowledgeRelationshipMutationResult.Applied -> "关系已本地确认并追加审计修订。"
    is KnowledgeRelationshipMutationResult.Replayed -> "此确认已处理，未重复写入。"
    is KnowledgeRelationshipMutationResult.Rejected -> "未建立关系：${code.name}。请重新检查范围、状态或内容修订。"
}
