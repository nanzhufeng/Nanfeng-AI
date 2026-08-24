package com.nanzhufeng.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.CandidateReviewStatus
import com.nanzhufeng.ai.domain.CaptureSourceType
import com.nanzhufeng.ai.domain.KnowledgeDetail
import com.nanzhufeng.ai.domain.KnowledgeDuplicateCandidatesRejection
import com.nanzhufeng.ai.domain.KnowledgeDuplicateCandidatesResult
import com.nanzhufeng.ai.domain.KnowledgeDuplicateReason
import com.nanzhufeng.ai.domain.KnowledgeItemId
import com.nanzhufeng.ai.domain.KnowledgeListEntry
import com.nanzhufeng.ai.domain.ReadKnowledgeLibraryUseCase
import com.nanzhufeng.ai.domain.ManageKnowledgeUseCase
import com.nanzhufeng.ai.domain.KnowledgeSearchFilter
import com.nanzhufeng.ai.domain.KnowledgeSearchResult
import com.nanzhufeng.ai.domain.KnowledgeStatus
import com.nanzhufeng.ai.domain.KnowledgeSnapshot
import com.nanzhufeng.ai.domain.KnowledgeIntent
import com.nanzhufeng.ai.domain.KnowledgeIntentAction
import com.nanzhufeng.ai.domain.KnowledgeIntentId
import com.nanzhufeng.ai.domain.KnowledgeMutationResult
import com.nanzhufeng.ai.domain.KnowledgeRejectionCode
import com.nanzhufeng.ai.domain.KnowledgeScope
import com.nanzhufeng.ai.domain.KnowledgeRelationshipAction
import com.nanzhufeng.ai.domain.KnowledgeRelationshipEndpointExpectation
import com.nanzhufeng.ai.domain.KnowledgeRelationshipIntent
import com.nanzhufeng.ai.domain.KnowledgeRelationshipIntentId
import com.nanzhufeng.ai.domain.KnowledgeRelationshipListFilter
import com.nanzhufeng.ai.domain.KnowledgeRelationshipMutationResult
import com.nanzhufeng.ai.domain.KnowledgeRelationshipSnapshot
import com.nanzhufeng.ai.domain.KnowledgeRelationshipStatus
import com.nanzhufeng.ai.domain.KnowledgeRelationshipSuggestionSource
import com.nanzhufeng.ai.domain.KnowledgeRelationshipType
import com.nanzhufeng.ai.domain.ManageKnowledgeRelationshipsUseCase
import com.nanzhufeng.ai.domain.SourceEvidence
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KnowledgeLibraryUiState(
    val dialogVisible: Boolean = false,
    val isLoading: Boolean = false,
    val entries: List<KnowledgeListEntry> = emptyList(),
    val detail: KnowledgeDetail? = null,
    val managedDetail: KnowledgeSnapshot? = null,
    val query: String = "",
    val status: KnowledgeStatus = KnowledgeStatus.ACTIVE,
    val sourceType: CaptureSourceType? = null,
    val editing: Boolean = false,
    val creating: Boolean = false,
    val editTitle: String = "",
    val editBody: String = "",
    val editTags: String = "",
    val duplicateCandidates: KnowledgeDuplicateCandidatesResult? = null,
    val mutationMessage: String? = null,
    val relationshipUi: KnowledgeRelationshipUiState = KnowledgeRelationshipUiState(),
)

data class KnowledgeRelationshipUiState(
    val builderVisible: Boolean = false,
    val listVisible: Boolean = false,
    val anchor: KnowledgeSnapshot? = null,
    val candidates: List<KnowledgeSearchResult> = emptyList(),
    val selectedTarget: KnowledgeSearchResult? = null,
    val type: KnowledgeRelationshipType = KnowledgeRelationshipType.RELATED,
    val records: List<KnowledgeRelationshipSnapshot> = emptyList(),
    val recordStatus: KnowledgeRelationshipStatus? = KnowledgeRelationshipStatus.ACTIVE,
    val result: KnowledgeRelationshipMutationResult? = null,
)

class KnowledgeLibraryViewModel(
    private val readKnowledgeLibrary: ReadKnowledgeLibraryUseCase,
    private val manageKnowledge: ManageKnowledgeUseCase,
    private val manageRelationships: ManageKnowledgeRelationshipsUseCase,
) : ViewModel() {
    var state by mutableStateOf(KnowledgeLibraryUiState())
        private set

    fun showDialog() {
        state = state.copy(dialogVisible = true, isLoading = true, detail = null, duplicateCandidates = null)
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { filteredEntries() }
            state = KnowledgeLibraryUiState(dialogVisible = true, entries = entries)
        }
    }

    fun openDetail(id: KnowledgeItemId) {
        if (state.isLoading) return
        state = state.copy(isLoading = true)
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) { readKnowledgeLibrary.detail(id) }
            val managed = withContext(Dispatchers.IO) { manageKnowledge.detail(id) }
            state = state.copy(isLoading = false, detail = detail, managedDetail = managed, duplicateCandidates = null)
        }
    }

    fun backToList() {
        state = state.copy(detail = null, managedDetail = null, duplicateCandidates = null)
    }

    fun dismissDialog() {
        if (!state.isLoading) state = state.copy(dialogVisible = false, detail = null, managedDetail = null, duplicateCandidates = null)
    }

    fun updateSearch(value: String) { state = state.copy(query = value); reload() }
    fun setStatus(status: KnowledgeStatus) { state = state.copy(status = status); reload() }
    fun setSourceType(sourceType: CaptureSourceType?) { state = state.copy(sourceType = sourceType); reload() }
    fun archiveOrRestore() {
        val snapshot = state.managedDetail ?: return
        val action = if (snapshot.lifecycle.status == KnowledgeStatus.ARCHIVED) KnowledgeIntentAction.RESTORE else KnowledgeIntentAction.ARCHIVE
        mutate(KnowledgeIntent(KnowledgeIntentId.new(), action, snapshot.item.id))
    }
    fun deleteOrRestoreTrash() {
        val snapshot = state.managedDetail ?: return
        val action = if (snapshot.lifecycle.status == KnowledgeStatus.DELETED) KnowledgeIntentAction.RESTORE_FROM_TRASH else KnowledgeIntentAction.DELETE
        mutate(KnowledgeIntent(KnowledgeIntentId.new(), action, snapshot.item.id))
    }
    fun startCreate() { state = state.copy(editing = true, creating = true, detail = null, managedDetail = null, editTitle = "", editBody = "", editTags = "") }
    fun startEdit() { state.managedDetail?.let { snapshot -> state = state.copy(editing = true, creating = false, editTitle = snapshot.item.title, editBody = snapshot.item.body, editTags = snapshot.lifecycle.tags.joinToString(", ")) } }
    fun updateEdit(title: String = state.editTitle, body: String = state.editBody, tags: String = state.editTags) { state = state.copy(editTitle = title, editBody = body, editTags = tags, mutationMessage = null) }
    fun cancelEdit() { state = state.copy(editing = false, creating = false) }
    fun saveEdit() {
        val snapshot = state.managedDetail; val tags = state.editTags.split(',').map { it.trim() }.filter(String::isNotEmpty).toSet()
        val intent = if (state.creating) KnowledgeIntent(KnowledgeIntentId.new(), KnowledgeIntentAction.CREATE_MANUAL, KnowledgeItemId.new(), state.editTitle, state.editBody, tags, KnowledgeScope.GLOBAL)
        else snapshot?.let { KnowledgeIntent(KnowledgeIntentId.new(), KnowledgeIntentAction.UPDATE, it.item.id, state.editTitle, state.editBody, tags, it.lifecycle.scope, it.lifecycle.projectId) } ?: return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { manageKnowledge.execute(intent) }) {
                is KnowledgeMutationResult.Applied, is KnowledgeMutationResult.Replayed -> {
                    state = state.copy(editing = false, creating = false, mutationMessage = null); backToList(); reload()
                }
                is KnowledgeMutationResult.Rejected -> state = state.copy(mutationMessage = "未保存：${result.code.label()}。请修正后重试。")
            }
        }
    }
    fun findDuplicateCandidates() {
        val id = state.managedDetail?.item?.id ?: return
        state = state.copy(isLoading = true, duplicateCandidates = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { manageKnowledge.duplicateCandidates(id) }
            state = state.copy(isLoading = false, duplicateCandidates = result)
        }
    }
    fun startRelationshipBuilder() {
        val anchor = state.managedDetail ?: return
        state = state.copy(relationshipUi = state.relationshipUi.copy(builderVisible = true, anchor = anchor, result = null, selectedTarget = null))
        viewModelScope.launch {
            val candidates = withContext(Dispatchers.IO) {
                manageKnowledge.search(KnowledgeSearchFilter(scope = anchor.lifecycle.scope, projectId = anchor.lifecycle.projectId))
                    .filter { it.id != anchor.item.id }
            }
            state = state.copy(relationshipUi = state.relationshipUi.copy(candidates = candidates))
        }
    }
    fun dismissRelationshipBuilder() { state = state.copy(relationshipUi = state.relationshipUi.copy(builderVisible = false, result = null, selectedTarget = null)) }
    fun selectRelationshipType(type: KnowledgeRelationshipType) { state = state.copy(relationshipUi = state.relationshipUi.copy(type = type, result = null)) }
    fun selectRelationshipTarget(target: KnowledgeSearchResult) { state = state.copy(relationshipUi = state.relationshipUi.copy(selectedTarget = target, result = null)) }
    fun confirmRelationship() {
        val anchor = state.relationshipUi.anchor ?: return; val target = state.relationshipUi.selectedTarget ?: return
        val suggestion = (state.duplicateCandidates as? KnowledgeDuplicateCandidatesResult.Available)?.candidates?.any { it.knowledgeId == target.id }
            ?.let { if (it) KnowledgeRelationshipSuggestionSource.P4F_DEDUPLICATION_CANDIDATE else KnowledgeRelationshipSuggestionSource.MANUAL }
            ?: KnowledgeRelationshipSuggestionSource.MANUAL
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                manageRelationships.confirm(KnowledgeRelationshipIntent(
                    id = KnowledgeRelationshipIntentId.new(), action = KnowledgeRelationshipAction.CONFIRM, type = state.relationshipUi.type,
                    first = KnowledgeRelationshipEndpointExpectation(anchor.item.id, anchor.revisions.maxOfOrNull { it.revision } ?: 1, anchor.lifecycle.contentHash),
                    second = KnowledgeRelationshipEndpointExpectation(target.id, target.revision, target.contentHash), suggestionSource = suggestion,
                ))
            }
            val records = withContext(Dispatchers.IO) { manageRelationships.list(KnowledgeRelationshipListFilter(endpointId = anchor.item.id, status = state.relationshipUi.recordStatus)) }
            state = state.copy(relationshipUi = state.relationshipUi.copy(result = result, records = records))
        }
    }
    fun showRelationshipList(status: KnowledgeRelationshipStatus? = KnowledgeRelationshipStatus.ACTIVE) {
        val anchor = state.managedDetail ?: return
        state = state.copy(relationshipUi = state.relationshipUi.copy(listVisible = true, anchor = anchor, recordStatus = status, result = null))
        viewModelScope.launch {
            val records = withContext(Dispatchers.IO) { manageRelationships.list(KnowledgeRelationshipListFilter(endpointId = anchor.item.id, status = status)) }
            state = state.copy(relationshipUi = state.relationshipUi.copy(records = records))
        }
    }
    fun dismissRelationshipList() { state = state.copy(relationshipUi = state.relationshipUi.copy(listVisible = false)) }
    fun revokeRelationship(id: com.nanzhufeng.ai.domain.KnowledgeRelationshipId) {
        val anchor = state.relationshipUi.anchor ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { manageRelationships.revoke(KnowledgeRelationshipIntent(KnowledgeRelationshipIntentId.new(), KnowledgeRelationshipAction.REVOKE, relationshipId = id)) }
            val records = withContext(Dispatchers.IO) { manageRelationships.list(KnowledgeRelationshipListFilter(endpointId = anchor.item.id, status = state.relationshipUi.recordStatus)) }
            state = state.copy(relationshipUi = state.relationshipUi.copy(records = records))
        }
    }
    private fun mutate(intent: KnowledgeIntent) = viewModelScope.launch {
        withContext(Dispatchers.IO) { manageKnowledge.execute(intent) }; backToList(); reload()
    }
    private fun reload() = viewModelScope.launch {
        val entries = withContext(Dispatchers.IO) { filteredEntries() }; state = state.copy(entries = entries)
    }
    private fun filteredEntries(): List<KnowledgeListEntry> {
        // P2's library projection is intentionally ACTIVE-only. P4-E lifecycle filters must instead
        // project the same filtered Knowledge snapshot, otherwise an archived/trash row could not be restored.
        return manageKnowledge.search(KnowledgeSearchFilter(query = state.query, status = state.status, sourceType = state.sourceType)).mapNotNull { result ->
            manageKnowledge.detail(result.id)?.item?.let { item ->
                KnowledgeListEntry(item.id, item.title, item.body, item.sourceEvidence, item.createdAt)
            }
        }
    }

    class Factory(private val readKnowledgeLibrary: ReadKnowledgeLibraryUseCase, private val manageKnowledge: ManageKnowledgeUseCase, private val manageRelationships: ManageKnowledgeRelationshipsUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(KnowledgeLibraryViewModel::class.java))
            return KnowledgeLibraryViewModel(readKnowledgeLibrary, manageKnowledge, manageRelationships) as T
        }
    }
}

@Composable
fun KnowledgeLibraryCard(state: KnowledgeLibraryUiState, onOpen: () -> Unit) {
    WhiteCard {
        Text("本地知识", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("只显示已确认保存的本地知识；候选、草稿和调用记录不会混入。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
        ) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(if (state.entries.isEmpty()) "查看本地知识" else "查看本地知识（${state.entries.size}）")
        }
    }
}

@Composable
fun KnowledgeLibraryDialog(
    state: KnowledgeLibraryUiState,
    onDismiss: () -> Unit,
    onOpenDetail: (KnowledgeItemId) -> Unit,
    onBackToList: () -> Unit,
    onSearch: (String) -> Unit,
    onStatus: (KnowledgeStatus) -> Unit,
    onSource: (CaptureSourceType?) -> Unit,
    onArchiveRestore: () -> Unit,
    onDeleteRestore: () -> Unit,
    onStartCreate: () -> Unit,
    onStartEdit: () -> Unit,
    onEditTitle: (String) -> Unit,
    onEditBody: (String) -> Unit,
    onEditTags: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onFindDuplicateCandidates: () -> Unit,
    onStartRelationshipBuilder: () -> Unit,
    onShowRelationshipList: () -> Unit,
) {
    val detail = state.detail
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (detail != null) {
                    TextButton(onClick = onBackToList, enabled = !state.isLoading) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回知识列表")
                    }
                }
                Text(if (detail == null) "本地知识" else "知识详情", fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            when {
                state.isLoading -> KnowledgeLoading()
                state.editing -> KnowledgeEditor(state, onEditTitle, onEditBody, onEditTags)
                detail != null -> KnowledgeDetailContent(detail, state.managedDetail, state.duplicateCandidates, state.relationshipUi.records.size, onArchiveRestore, onDeleteRestore, onStartEdit, onFindDuplicateCandidates, onStartRelationshipBuilder, onShowRelationshipList)
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(state.query, onSearch, Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索标题、正文、标签、来源") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { KnowledgeStatus.entries.forEach { status -> TextButton(onClick = { onStatus(status) }) { Text(if (state.status == status) "● ${status.label()}" else status.label()) } }; TextButton(onClick = onStartCreate) { Text("新建") } }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf<CaptureSourceType?>(null, CaptureSourceType.MANUAL_TEXT, CaptureSourceType.ANDROID_TEXT_SHARE, CaptureSourceType.IMAGE).forEach { source -> TextButton(onClick = { onSource(source) }) { Text(if (state.sourceType == source) "● ${source.label()}" else source.label()) } } }
                    if (state.entries.isEmpty()) KnowledgeEmptyState() else KnowledgeList(state.entries, onOpenDetail)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = if (state.editing) onCancelEdit else onDismiss, enabled = !state.isLoading) { Text(if (state.editing) "取消" else "关闭") }
        },
        dismissButton = if (state.editing) ({ TextButton(onClick = onSaveEdit) { Text("保存修订") } }) else null,
    )
}

@Composable
private fun KnowledgeLoading() {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text("正在读取本地知识…", color = SecondaryText)
    }
}

@Composable
private fun KnowledgeEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(36.dp))
        Text("尚无已保存的本地知识", fontWeight = FontWeight.Medium)
        Text("候选确认保存后会出现在这里；当前不会展示 Mock 或示例数据。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun KnowledgeList(entries: List<KnowledgeListEntry>, onOpenDetail: (KnowledgeItemId) -> Unit) {
    Column(
        modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = NeutralBorder)
            TextButton(
                onClick = { onOpenDetail(entry.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, color = BodyText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.summary, color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${entry.sourceEvidence.joinToString(" · ") { it.sourceLabel() }} · ${formatKnowledgeTime(entry.savedAt)}",
                        color = SecondaryText,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = "查看详情", tint = BrandGreen)
            }
        }
    }
}

@Composable
private fun KnowledgeDetailContent(detail: KnowledgeDetail, managed: KnowledgeSnapshot?, duplicateCandidates: KnowledgeDuplicateCandidatesResult?, relationshipCount: Int, onArchiveRestore: () -> Unit, onDeleteRestore: () -> Unit, onStartEdit: () -> Unit, onFindDuplicateCandidates: () -> Unit, onStartRelationshipBuilder: () -> Unit, onShowRelationshipList: () -> Unit) {
    val item = detail.item
    Column(
        modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(item.body, color = BodyText)
        HorizontalDivider(color = NeutralBorder)
        Text("来源", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        item.sourceEvidence.forEach { source ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(source.sourceLabel(), fontWeight = FontWeight.Medium)
                Text("来源引用：${source.sourceReference ?: "未提供"}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("接收时间：${formatKnowledgeTime(source.receivedAt)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (item.attachments.isNotEmpty()) {
            Text("关联附件：${item.attachments.size} 项（仅保留本地引用，未显示原图或附件正文）", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(color = NeutralBorder)
        Text("生成与保存溯源", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("Candidate：${item.provenance.candidateId.value}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Text("候选状态：${detail.candidateStatus?.toChineseLabel() ?: "历史知识未保留候选记录"}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Text("Invocation：${item.provenance.invocationId.value}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Text("服务：${item.provenance.providerId.toDisplayLabel()} · 模型：${item.provenance.modelId} · Harness v${item.provenance.harnessVersion}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Text("保存时间：${formatKnowledgeTime(item.createdAt)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        managed?.let { snapshot ->
            Text("状态：${snapshot.lifecycle.status.label()} · ${snapshot.lifecycle.scope.name}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Text("标签：${snapshot.lifecycle.tags.ifEmpty { setOf("未标注") }.joinToString(" · ")}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Text("修订：${snapshot.revisions.size} 条", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Row { TextButton(onClick = onStartEdit, enabled = snapshot.lifecycle.status != KnowledgeStatus.DELETED) { Text("编辑") }; TextButton(onClick = onArchiveRestore) { Text(if (snapshot.lifecycle.status == KnowledgeStatus.ARCHIVED) "恢复归档" else "归档") }; TextButton(onClick = onDeleteRestore) { Text(if (snapshot.lifecycle.status == KnowledgeStatus.DELETED) "从回收站恢复" else "移入回收站") } }
            if (snapshot.lifecycle.status == KnowledgeStatus.ACTIVE) {
                TextButton(onClick = onFindDuplicateCandidates) { Text("本机查找重复候选") }
                TextButton(onClick = onStartRelationshipBuilder) { Text("建立本地关系") }
            }
            TextButton(onClick = onShowRelationshipList) { Text("查看关系（$relationshipCount）") }
        }
        duplicateCandidates?.let { DuplicateCandidatesContent(it) }
        Text("不会显示 API Key、完整 Prompt、完整服务响应、原图或附件正文。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DuplicateCandidatesContent(result: KnowledgeDuplicateCandidatesResult) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    HorizontalDivider(color = NeutralBorder)
    Text("重复候选（仅本机只读）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    when (result) {
        is KnowledgeDuplicateCandidatesResult.Available -> if (result.candidates.isEmpty()) {
            Text("未发现同一范围内的确定性重复候选。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("不会自动合并、建立关系或改变任何 Knowledge。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            result.candidates.forEach { candidate ->
                Text("${candidate.title} · r${candidate.revision} · ${candidate.reasons.joinToString("、") { it.label() }}", color = BodyText, style = MaterialTheme.typography.bodySmall)
            }
        }
        is KnowledgeDuplicateCandidatesResult.Rejected -> Text(result.code.label(), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

private fun KnowledgeDuplicateReason.label(): String = when (this) {
    KnowledgeDuplicateReason.EXACT_CONTENT -> "内容完全相同"
    KnowledgeDuplicateReason.SAME_NORMALIZED_TITLE -> "规范化标题相同"
}

private fun KnowledgeDuplicateCandidatesRejection.label(): String = when (this) {
    KnowledgeDuplicateCandidatesRejection.MISSING_KNOWLEDGE -> "该 Knowledge 已不存在，未返回候选。"
    KnowledgeDuplicateCandidatesRejection.INELIGIBLE_KNOWLEDGE -> "仅活动 Knowledge 可查找候选。"
    KnowledgeDuplicateCandidatesRejection.HIGH_SENSITIVITY -> "检测到高敏正文，未返回任何候选。"
}

@Composable private fun KnowledgeEditor(state: KnowledgeLibraryUiState, onTitle: (String) -> Unit, onBody: (String) -> Unit, onTags: (String) -> Unit) = Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(if (state.creating) "新建本地 Knowledge" else "编辑 Knowledge（追加修订）", fontWeight = FontWeight.SemiBold)
    OutlinedTextField(state.editTitle, onTitle, Modifier.fillMaxWidth(), label = { Text("标题") })
    OutlinedTextField(state.editBody, onBody, Modifier.fillMaxWidth().heightIn(min = 140.dp), label = { Text("正文") })
    OutlinedTextField(state.editTags, onTags, Modifier.fillMaxWidth(), label = { Text("标签（以逗号分隔）") })
    Text("仅本地保存；高敏正文会在写入前拒绝，不会自动加入对话 Context。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    state.mutationMessage?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
}

private fun KnowledgeRejectionCode.label(): String = when (this) {
    KnowledgeRejectionCode.EMPTY_TITLE -> "标题不能为空"
    KnowledgeRejectionCode.EMPTY_BODY -> "正文不能为空"
    KnowledgeRejectionCode.TITLE_TOO_LONG -> "标题过长"
    KnowledgeRejectionCode.BODY_TOO_LONG -> "正文过长"
    KnowledgeRejectionCode.INVALID_TAG -> "标签格式无效"
    KnowledgeRejectionCode.INVALID_SCOPE -> "范围无效"
    KnowledgeRejectionCode.MISSING_KNOWLEDGE -> "Knowledge 已不存在"
    KnowledgeRejectionCode.INVALID_ACTION -> "当前操作无效"
    KnowledgeRejectionCode.HIGH_SENSITIVITY -> "检测到高敏内容"
}

private fun KnowledgeStatus.label(): String = when (this) { KnowledgeStatus.ACTIVE -> "活动"; KnowledgeStatus.ARCHIVED -> "归档"; KnowledgeStatus.DELETED -> "回收站" }
private fun CaptureSourceType?.label(): String = when (this) { null -> "全部来源"; CaptureSourceType.MANUAL_TEXT -> "手工"; CaptureSourceType.ANDROID_TEXT_SHARE -> "分享"; CaptureSourceType.IMAGE -> "图片" }

private fun SourceEvidence.sourceLabel(): String = when (sourceType) {
    CaptureSourceType.MANUAL_TEXT -> "手工文本"
    CaptureSourceType.ANDROID_TEXT_SHARE -> "系统文本分享${sourceReference?.let { "（$it）" }.orEmpty()}"
    CaptureSourceType.IMAGE -> "相册图片"
}

private fun CandidateReviewStatus.toChineseLabel(): String = when (this) {
    CandidateReviewStatus.PENDING_REVIEW -> "待核对"
    CandidateReviewStatus.SAVED -> "已确认保存"
    CandidateReviewStatus.DISCARDED -> "已丢弃"
}

private fun com.nanzhufeng.ai.domain.ProviderId.toDisplayLabel(): String = when (this) {
    com.nanzhufeng.ai.domain.ProviderId.MOCK -> "本地 Mock（非真实服务）"
    com.nanzhufeng.ai.domain.ProviderId.OPENROUTER -> "OpenRouter（未验证真实连接）"
    com.nanzhufeng.ai.domain.ProviderId.QWEN -> "Qwen 官方直连（未验证真实连接）"
    com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK -> "DeepSeek 官方直连（未验证真实连接）"
}

private fun formatKnowledgeTime(instant: Instant): String = instant.atZone(ZoneId.systemDefault()).format(KNOWLEDGE_TIME_FORMAT)

private val KNOWLEDGE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
