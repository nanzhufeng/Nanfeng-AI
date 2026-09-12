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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.nanzhufeng.ai.domain.CaptureSourceType
import com.nanzhufeng.ai.domain.KnowledgeDetail
import com.nanzhufeng.ai.domain.KnowledgeDuplicateCandidatesResult
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
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.HistoryKnowledgeCurationDraft
import com.nanzhufeng.ai.domain.HistoryKnowledgeCurationResult
import com.nanzhufeng.ai.domain.HistoryKnowledgeRefiner
import com.nanzhufeng.ai.domain.ReadHistoryKnowledgeCurationSourceResult
import com.nanzhufeng.ai.domain.ReadHistoryKnowledgeCurationSourceUseCase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val editing: Boolean = false,
    val creating: Boolean = false,
    val editTitle: String = "",
    val editBody: String = "",
    val editTags: String = "",
    val duplicateCandidates: KnowledgeDuplicateCandidatesResult? = null,
    val mutationMessage: String? = null,
    val relationshipUi: KnowledgeRelationshipUiState = KnowledgeRelationshipUiState(),
    val historyCurationConversationId: ConversationId? = null,
    val historyCurationDraft: HistoryKnowledgeCurationDraft? = null,
    val historyCurationError: String? = null,
    val historyCurationLoading: Boolean = false,
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
    private val readHistoryCurationSource: ReadHistoryKnowledgeCurationSourceUseCase,
    private val historyKnowledgeRefiner: HistoryKnowledgeRefiner,
) : ViewModel() {
    var state by mutableStateOf(KnowledgeLibraryUiState())
        private set
    private var reloadJob: Job? = null
    private var reloadGeneration = 0L

    fun showDialog() {
        state = KnowledgeLibraryUiState(dialogVisible = true, isLoading = true)
        reload(clearLoading = true)
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

    fun updateSearch(value: String) {
        if (state.query == value) return
        state = state.copy(query = value)
        reload(debounce = value.isNotBlank())
    }
    fun setStatus(status: KnowledgeStatus) { state = state.copy(status = status); reload() }
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

    /** First step only opens the explicit egress confirmation; it never calls a model. */
    fun requestHistoryCuration(conversationId: ConversationId?) {
        conversationId ?: return
        state = state.copy(historyCurationConversationId = conversationId, historyCurationDraft = null, historyCurationError = null)
    }
    fun cancelHistoryCuration() { state = state.copy(historyCurationConversationId = null, historyCurationDraft = null, historyCurationError = null, historyCurationLoading = false) }
    fun confirmHistoryCuration() {
        val conversationId = state.historyCurationConversationId ?: return
        state = state.copy(historyCurationLoading = true, historyCurationError = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (val source = readHistoryCurationSource.execute(conversationId)) {
                    is ReadHistoryKnowledgeCurationSourceResult.Available -> historyKnowledgeRefiner.refine(source.source)
                    ReadHistoryKnowledgeCurationSourceResult.MissingConversation -> HistoryKnowledgeCurationResult.Failed("MISSING_CONVERSATION")
                    ReadHistoryKnowledgeCurationSourceResult.NoText -> HistoryKnowledgeCurationResult.NotEligible
                }
            }
            state = when (result) {
                is HistoryKnowledgeCurationResult.Draft -> state.copy(historyCurationLoading = false, historyCurationDraft = result.value)
                HistoryKnowledgeCurationResult.NotEligible -> state.copy(historyCurationLoading = false, historyCurationError = "这条对话没有足够明确的长期资料，未生成候选。")
                is HistoryKnowledgeCurationResult.Failed -> state.copy(historyCurationLoading = false, historyCurationError = "整理未完成：${result.safeCode}。")
            }
        }
    }
    fun updateHistoryCurationDraft(title: String, body: String, tags: String) {
        state.historyCurationDraft?.let { draft -> state = state.copy(historyCurationDraft = draft.copy(title = title, body = body, tags = tags.split(',').map(String::trim).filter(String::isNotBlank).toSet())) }
    }
    fun saveHistoryCurationDraft() {
        val conversationId = state.historyCurationConversationId ?: return
        val draft = state.historyCurationDraft ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                manageKnowledge.execute(KnowledgeIntent(KnowledgeIntentId.new(), KnowledgeIntentAction.CREATE_HISTORY_CONVERSATION, KnowledgeItemId.new(), draft.title, draft.body, draft.tags, KnowledgeScope.GLOBAL, importReference = "conversation:${conversationId.value}", generatedProviderId = draft.providerId, generatedModelId = draft.modelId))
            }
            if (result is KnowledgeMutationResult.Applied || result is KnowledgeMutationResult.Replayed) { cancelHistoryCuration(); reload() }
            else state = state.copy(historyCurationError = "候选未保存，请检查内容后重试。")
        }
    }
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
    private fun reload(debounce: Boolean = false, clearLoading: Boolean = false) {
        val query = state.query
        val status = state.status
        val generation = ++reloadGeneration
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (debounce) delay(180)
            val entries = withContext(Dispatchers.IO) { filteredEntries(query, status) }
            if (generation == reloadGeneration && state.query == query && state.status == status) {
                state = state.copy(entries = entries, isLoading = if (clearLoading) false else state.isLoading)
            }
        }
    }
    private fun filteredEntries(query: String, status: KnowledgeStatus): List<KnowledgeListEntry> {
        // P2's library projection is intentionally ACTIVE-only. P4-E lifecycle filters must instead
        // project the same filtered Knowledge snapshot, otherwise an archived/trash row could not be restored.
        return manageKnowledge.search(KnowledgeSearchFilter(query = query, status = status)).map { result ->
            KnowledgeListEntry(result.id, result.title, result.snippet, result.sourceEvidence, result.createdAt)
        }
    }

    class Factory(private val readKnowledgeLibrary: ReadKnowledgeLibraryUseCase, private val manageKnowledge: ManageKnowledgeUseCase, private val manageRelationships: ManageKnowledgeRelationshipsUseCase, private val readHistoryCurationSource: ReadHistoryKnowledgeCurationSourceUseCase, private val historyKnowledgeRefiner: HistoryKnowledgeRefiner) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(KnowledgeLibraryViewModel::class.java))
            return KnowledgeLibraryViewModel(readKnowledgeLibrary, manageKnowledge, manageRelationships, readHistoryCurationSource, historyKnowledgeRefiner) as T
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
fun KnowledgeLibraryPage(
    state: KnowledgeLibraryUiState,
    currentConversationId: ConversationId?,
    onOpenDetail: (KnowledgeItemId) -> Unit,
    onSearch: (String) -> Unit,
    onStatus: (KnowledgeStatus) -> Unit,
    onArchiveRestore: () -> Unit,
    onDeleteRestore: () -> Unit,
    onStartCreate: () -> Unit,
    onStartEdit: () -> Unit,
    onEditTitle: (String) -> Unit,
    onEditBody: (String) -> Unit,
    onEditTags: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onRequestHistoryCuration: (ConversationId?) -> Unit,
    onConfirmHistoryCuration: () -> Unit,
    onCancelHistoryCuration: () -> Unit,
    onUpdateHistoryCurationDraft: (String, String, String) -> Unit,
    onSaveHistoryCurationDraft: () -> Unit,
) {
    val detail = state.detail
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.isLoading -> KnowledgeLoading()
            state.editing -> KnowledgeEditor(state, onEditTitle, onEditBody, onEditTags)
            detail != null -> KnowledgeDetailContent(detail, state.managedDetail, onArchiveRestore, onDeleteRestore, onStartEdit)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KnowledgeStatusSelector(state.status, onStatus)
                Text("搜索", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(state.query, onSearch, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("搜索标题、正文、标签、来源") })
                KnowledgePrimaryActions(
                    canOrganizeConversation = currentConversationId != null,
                    onStartCreate = onStartCreate,
                    onOrganizeConversation = { currentConversationId?.let(onRequestHistoryCuration) },
                )
                if (state.entries.isEmpty()) KnowledgeEmptyState() else KnowledgeList(state.entries, onOpenDetail)
            }
        }
        if (state.editing) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancelEdit, enabled = !state.isLoading) { Text("取消") }
                Button(onClick = onSaveEdit, enabled = !state.isLoading) { Text("保存修订") }
            }
        }
    }
    state.historyCurationConversationId?.let {
        AlertDialog(
            onDismissRequest = { if (!state.historyCurationLoading) onCancelHistoryCuration() },
            title = { Text(if (state.historyCurationDraft == null) "整理为资料候选" else "核对资料候选") },
            text = {
                if (state.historyCurationDraft == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("将把当前对话的文字内容发送给已启用的直连模型，按以下顺序选择：", color = SecondaryText)
                        Text("DeepSeek V4.1 Flash → GLM-5.3 Flash → Qwen3.6 Flash", fontWeight = FontWeight.SemiBold)
                        Text("生成可复用的资料候选。不会发送附件，不会修改原对话；可能产生模型用量。", color = SecondaryText)
                        state.historyCurationError?.let { Text(it, color = ErrorRed) }
                    }
                } else {
                    val draft = state.historyCurationDraft
                    Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Text("来源：当前本地对话；模型：", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                            Text(draft.modelId, color = SecondaryText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedTextField(draft.title, { onUpdateHistoryCurationDraft(it, draft.body, draft.tags.joinToString(",")) }, Modifier.fillMaxWidth(), label = { Text("标题") })
                        OutlinedTextField(draft.body, { onUpdateHistoryCurationDraft(draft.title, it, draft.tags.joinToString(",")) }, Modifier.fillMaxWidth().heightIn(min = 150.dp), label = { Text("资料内容") })
                        OutlinedTextField(draft.tags.joinToString(","), { onUpdateHistoryCurationDraft(draft.title, draft.body, it) }, Modifier.fillMaxWidth(), label = { Text("标签") })
                    }
                }
            },
            confirmButton = {
                if (state.historyCurationDraft == null) Button(onClick = onConfirmHistoryCuration, enabled = !state.historyCurationLoading) { Text(if (state.historyCurationLoading) "正在整理…" else "确认并整理") }
                else Button(onClick = onSaveHistoryCurationDraft) { Text("确认保存") }
            },
            dismissButton = { TextButton(onClick = onCancelHistoryCuration, enabled = !state.historyCurationLoading) { Text("取消") } },
        )
    }
}

@Composable
private fun KnowledgeStatusSelector(
    selected: KnowledgeStatus,
    onSelect: (KnowledgeStatus) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NeutralSystemSurface,
        shape = P5AInteractiveShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            KnowledgeStatus.entries.forEach { status ->
                val isSelected = status == selected
                Surface(
                    onClick = { onSelect(status) },
                    modifier = Modifier.weight(1f).height(38.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isSelected) Color.White else BodyText,
                    shape = P5AInteractiveShape,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(status.filterLabel(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun KnowledgePrimaryActions(
    canOrganizeConversation: Boolean,
    onStartCreate: () -> Unit,
    onOrganizeConversation: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStartCreate,
            modifier = Modifier.weight(1f).height(46.dp),
            shape = P5AInteractiveShape,
            colors = ButtonDefaults.buttonColors(containerColor = ForegroundSurface, contentColor = BodyText),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("新建知识")
        }
        if (canOrganizeConversation) {
            Button(
                onClick = onOrganizeConversation,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = P5AInteractiveShape,
                colors = ButtonDefaults.buttonColors(containerColor = ForegroundSurface, contentColor = BodyText),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("整理当前对话")
            }
        }
    }
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
private fun KnowledgeDetailContent(
    detail: KnowledgeDetail,
    managed: KnowledgeSnapshot?,
    onArchiveRestore: () -> Unit,
    onDeleteRestore: () -> Unit,
    onStartEdit: () -> Unit,
) {
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
                Text(source.sourceType.displayLabel(), fontWeight = FontWeight.Medium)
                Text(formatKnowledgeTime(source.receivedAt), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (item.attachments.isNotEmpty()) {
            Text("附件：${item.attachments.size} 项", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(color = NeutralBorder)
        managed?.let { snapshot ->
            Surface(color = ForegroundSurface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("保存于 ${formatKnowledgeTime(item.createdAt)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Text("状态 · ${snapshot.lifecycle.status.label()}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Text("标签 · ${snapshot.lifecycle.tags.ifEmpty { setOf("未标注") }.joinToString(" · ")}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
            KnowledgeDetailActions(snapshot.lifecycle.status, onStartEdit, onArchiveRestore, onDeleteRestore)
        }
    }
}

@Composable
private fun KnowledgeDetailActions(
    status: KnowledgeStatus,
    onEdit: () -> Unit,
    onArchiveRestore: () -> Unit,
    onDeleteRestore: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (status != KnowledgeStatus.DELETED) {
            KnowledgeDetailIconAction(Icons.Rounded.Edit, "编辑", onEdit)
        }
        if (status != KnowledgeStatus.DELETED) {
            KnowledgeDetailIconAction(
                icon = if (status == KnowledgeStatus.ARCHIVED) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                contentDescription = if (status == KnowledgeStatus.ARCHIVED) "恢复归档" else "归档",
                onClick = onArchiveRestore,
            )
        }
        KnowledgeDetailIconAction(
            icon = if (status == KnowledgeStatus.DELETED) Icons.Rounded.RestoreFromTrash else Icons.Rounded.DeleteOutline,
            contentDescription = if (status == KnowledgeStatus.DELETED) "从回收站恢复" else "移入回收站",
            onClick = onDeleteRestore,
            danger = status != KnowledgeStatus.DELETED,
        )
    }
}

@Composable
private fun KnowledgeDetailIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = ForegroundSurface,
        contentColor = if (danger) ErrorRed else MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable private fun KnowledgeEditor(state: KnowledgeLibraryUiState, onTitle: (String) -> Unit, onBody: (String) -> Unit, onTags: (String) -> Unit) = Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(if (state.creating) "新建本地 Knowledge" else "编辑 Knowledge（追加修订）", fontWeight = FontWeight.SemiBold)
    OutlinedTextField(state.editTitle, onTitle, Modifier.fillMaxWidth(), label = { Text("标题") })
    OutlinedTextField(state.editBody, onBody, Modifier.fillMaxWidth().heightIn(min = 140.dp), label = { Text("正文") })
    OutlinedTextField(state.editTags, onTags, Modifier.fillMaxWidth(), label = { Text("标签（以逗号分隔）") })
    Text("仅本地保存；高敏正文会在写入前拒绝。开启资料库搜索后，相关内容会按当前问题自动检索并加入对话上下文。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
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
private fun KnowledgeStatus.filterLabel(): String = when (this) { KnowledgeStatus.ACTIVE -> "知识"; KnowledgeStatus.ARCHIVED -> "归档"; KnowledgeStatus.DELETED -> "回收站" }

private fun SourceEvidence.sourceLabel(): String = when (sourceType) {
    CaptureSourceType.MANUAL_TEXT -> "手工文本"
    CaptureSourceType.ANDROID_TEXT_SHARE -> "系统文本分享${sourceReference?.let { "（$it）" }.orEmpty()}"
    CaptureSourceType.IMAGE -> "相册图片"
    CaptureSourceType.HISTORY_CONVERSATION -> "历史对话整理${sourceReference?.let { "（$it）" }.orEmpty()}"
}

private fun CaptureSourceType.displayLabel(): String = when (this) {
    CaptureSourceType.MANUAL_TEXT -> "手工创建"
    CaptureSourceType.ANDROID_TEXT_SHARE -> "系统分享"
    CaptureSourceType.IMAGE -> "图片整理"
    CaptureSourceType.HISTORY_CONVERSATION -> "对话整理"
}

private fun formatKnowledgeTime(instant: Instant): String = instant.atZone(ZoneId.systemDefault()).format(KNOWLEDGE_TIME_FORMAT)

private val KNOWLEDGE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
