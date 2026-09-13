package com.nanzhufeng.ai.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.data.AndroidDocumentOpenResult
import com.nanzhufeng.ai.data.AndroidDocumentSelectionReader
import com.nanzhufeng.ai.data.AndroidGlmOcrScheduler
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.ConversationAttachmentOriginalPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentPdfPreview
import com.nanzhufeng.ai.domain.ConversationAttachmentPreviewProjection
import com.nanzhufeng.ai.domain.ConversationAttachmentReference
import com.nanzhufeng.ai.domain.ConversationAttachmentTextPreview
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.GlmOcrContinueResult
import com.nanzhufeng.ai.domain.GlmOcrImportResult
import com.nanzhufeng.ai.domain.GlmOcrTask
import com.nanzhufeng.ai.domain.GlmOcrTaskId
import com.nanzhufeng.ai.domain.GlmOcrTaskOwner
import com.nanzhufeng.ai.domain.GlmOcrTaskStatus
import com.nanzhufeng.ai.domain.PdfPreviewPositionStore
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val OCR_PDF_PAGE_CACHE_MAX_ENTRIES = 4
private const val OCR_PDF_PAGE_CACHE_MAX_BYTES = 24L * 1024L * 1024L
private const val OCR_PDF_NEIGHBOUR_PREFETCH_DELAY_MS = 120L

private data class OcrPdfPageCacheKey(
    val attachmentId: String,
    val sourceSha256: String,
    val pageNumber: Int,
)

data class GlmOcrWorkspaceUiState(
    val loading: Boolean = true,
    val importing: Boolean = false,
    val tasks: List<GlmOcrTask> = emptyList(),
    val selectedTaskId: GlmOcrTaskId? = null,
    val resultReference: ConversationAttachmentReference? = null,
    val textPreview: ConversationAttachmentTextPreview? = null,
    val attachmentTransfer: AttachmentTransferRequest? = null,
    val actionTaskId: GlmOcrTaskId? = null,
    val createdConversationId: ConversationId? = null,
    val notice: String? = null,
    val imagePreview: ConversationAttachmentOriginalPreview? = null,
    val pdfPreview: ConversationAttachmentPdfPreview? = null,
    val pdfPreviewLoading: Boolean = false,
) {
    val selectedTask: GlmOcrTask? get() = tasks.firstOrNull { it.id == selectedTaskId }
    val hasActiveTask: Boolean get() = tasks.any { it.status == GlmOcrTaskStatus.QUEUED || it.status == GlmOcrTaskStatus.PROCESSING }
}

class GlmOcrWorkspaceViewModel(
    private val owner: GlmOcrTaskOwner,
    private val scheduler: AndroidGlmOcrScheduler,
    private val documents: AndroidDocumentSelectionReader,
    private val attachmentPreview: ConversationAttachmentPreviewProjection,
    private val pdfPreviewPosition: PdfPreviewPositionStore,
) : ViewModel() {
    var state by mutableStateOf(GlmOcrWorkspaceUiState())
        private set

    fun selectSource(uri: Uri?) {
        if (state.importing || uri == null) return
        state = state.copy(importing = true, notice = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (val opened = documents.open(uri)) {
                    is AndroidDocumentOpenResult.Opened -> owner.import(opened.selection)
                    AndroidDocumentOpenResult.Cancelled -> GlmOcrImportResult.Rejected("已取消选择，现有转写保持不变。")
                    is AndroidDocumentOpenResult.Rejected -> GlmOcrImportResult.Rejected(opened.reason)
                }
            }
            when (result) {
                is GlmOcrImportResult.Imported -> reload(
                    selected = result.task.id,
                    notice = "原始文件已安全保存到本机，尚未发送给智谱。",
                    importing = false,
                )
                is GlmOcrImportResult.Rejected -> state = state.copy(importing = false, notice = result.reason)
            }
        }
    }

    fun openDetail(id: GlmOcrTaskId) {
        state = state.copy(
            selectedTaskId = id,
            resultReference = null,
            textPreview = null,
            notice = null,
        )
        reload(id, null)
    }

    fun closeDetail() {
        sourceOpenGeneration += 1
        pdfPreviewRequestGeneration += 1
        pdfNeighbourPrefetchJob?.cancel()
        pdfNeighbourPrefetchJob = null
        openedPdfReference = null
        openedSourceReference = null
        state = state.copy(
            selectedTaskId = null,
            resultReference = null,
            textPreview = null,
            attachmentTransfer = null,
            notice = null,
            imagePreview = null,
            pdfPreview = null,
            pdfPreviewLoading = false,
        )
    }

    private var openedPdfReference: com.nanzhufeng.ai.domain.ConversationAttachmentReference? = null
    private var openedSourceReference: com.nanzhufeng.ai.domain.ConversationAttachmentReference? = null
    private var sourceOpenGeneration = 0L
    private var pdfPreviewRequestGeneration = 0L
    private val pdfPageCache = LinkedHashMap<OcrPdfPageCacheKey, ConversationAttachmentPdfPreview>(8, 0.75f, true)
    private var pdfPageCacheBytes = 0L
    private var pdfNeighbourPrefetchJob: Job? = null

    fun openSource(id: GlmOcrTaskId) {
        val generation = ++sourceOpenGeneration
        val task = state.tasks.firstOrNull { it.id == id }
        state = if (task?.sourceMimeType == "application/pdf") {
            state.copy(
                notice = null,
                pdfPreview = ConversationAttachmentPdfPreview(
                    id = task.sourceAttachmentId,
                    displayName = task.sourceDisplayName,
                    byteCount = task.sourceByteCount,
                    page = null,
                    canTransfer = true,
                ),
                pdfPreviewLoading = true,
            )
        } else state.copy(notice = null)
        viewModelScope.launch {
            // sourceReference resolves both the OCR task and its durable attachment through
            // Room. Never let a Compose click callback execute that database chain on Main.
            val reference = withContext(Dispatchers.IO) { owner.sourceReference(id) }
            if (generation != sourceOpenGeneration || state.selectedTaskId != id) return@launch
            if (reference == null) {
                state = state.copy(
                    pdfPreview = state.pdfPreview?.copy(unavailableReason = "原始文件未能通过本机完整性校验。"),
                    pdfPreviewLoading = false,
                    notice = if (task?.sourceMimeType == "application/pdf") null else "原始文件未能通过本机完整性校验。",
                )
                return@launch
            }
            openedSourceReference = reference
            when {
                reference.mimeType.startsWith("image/") -> {
                val preview = withContext(Dispatchers.IO) { attachmentPreview.original(reference) }
                    if (generation != sourceOpenGeneration || state.selectedTaskId != id) return@launch
                state = state.copy(
                    imagePreview = preview.copy(canTransfer = true),
                    notice = preview.unavailableReason.takeIf { preview.bytes == null },
                )
            }
                reference.mimeType == "application/pdf" -> {
                    openedPdfReference = reference
                    loadPdfPage(pdfPreviewPosition.pageFor(reference.id))
                }
                else -> state = state.copy(notice = "该原始文件不是可预览的图片或 PDF。")
            }
        }
    }

    fun closeImagePreview() {
        sourceOpenGeneration += 1
        openedSourceReference = null
        state = state.copy(imagePreview = null)
    }

    fun openPdfPage(pageNumber: Int) { loadPdfPage(pageNumber) }

    private fun loadPdfPage(pageNumber: Int) {
        val reference = openedPdfReference ?: return
        pdfNeighbourPrefetchJob?.cancel()
        val requestGeneration = ++pdfPreviewRequestGeneration
        cachedPdfPage(reference, pageNumber)?.let { cached ->
            cached.page?.let { pdfPreviewPosition.savePage(reference.id, it.pageNumber) }
            state = state.copy(pdfPreview = cached, pdfPreviewLoading = false, notice = null)
            prefetchPdfNeighbours(reference, cached)
            return
        }
        state = state.copy(pdfPreviewLoading = true, notice = null)
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) { attachmentPreview.pdfPage(reference, pageNumber) }
            if (requestGeneration != pdfPreviewRequestGeneration || openedPdfReference?.id != reference.id) return@launch
            cachePdfPage(reference, preview)
            preview.page?.let { pdfPreviewPosition.savePage(reference.id, it.pageNumber) }
            val visiblePreview = if (preview.page != null || state.pdfPreview?.page == null) preview.copy(canTransfer = true) else state.pdfPreview
            state = state.copy(
                pdfPreview = visiblePreview,
                pdfPreviewLoading = false,
                notice = preview.unavailableReason.takeIf { preview.page == null },
            )
            if (preview.page != null) prefetchPdfNeighbours(reference, preview)
        }
    }

    private fun cachedPdfPage(reference: ConversationAttachmentReference, pageNumber: Int): ConversationAttachmentPdfPreview? =
        pdfPageCache[OcrPdfPageCacheKey(reference.id.value, reference.sha256, pageNumber)]

    private fun cachePdfPage(reference: ConversationAttachmentReference, preview: ConversationAttachmentPdfPreview) {
        val page = preview.page ?: return
        val key = OcrPdfPageCacheKey(reference.id.value, reference.sha256, page.pageNumber)
        pdfPageCache.put(key, preview.copy(canTransfer = true))?.page?.image?.bytes?.size?.let { pdfPageCacheBytes -= it }
        pdfPageCacheBytes += page.image.bytes.size
        val iterator = pdfPageCache.entries.iterator()
        while ((pdfPageCache.size > OCR_PDF_PAGE_CACHE_MAX_ENTRIES || pdfPageCacheBytes > OCR_PDF_PAGE_CACHE_MAX_BYTES) && iterator.hasNext()) {
            val removed = iterator.next().value
            pdfPageCacheBytes -= removed.page?.image?.bytes?.size ?: 0
            iterator.remove()
        }
    }

    /** Match the conversation/search PDF reader: an explicit page tap wins, then adjacent pages
     * are rendered into the same bounded cache without replacing the currently visible page. */
    private fun prefetchPdfNeighbours(reference: ConversationAttachmentReference, preview: ConversationAttachmentPdfPreview) {
        val page = preview.page ?: return
        val neighbours = listOf(page.pageNumber + 1, page.pageNumber - 1)
            .filter { it in 1..page.pageCount && cachedPdfPage(reference, it) == null }
        if (neighbours.isEmpty()) return
        pdfNeighbourPrefetchJob?.cancel()
        pdfNeighbourPrefetchJob = viewModelScope.launch {
            delay(OCR_PDF_NEIGHBOUR_PREFETCH_DELAY_MS)
            for (neighbour in neighbours) {
                if (openedPdfReference?.id != reference.id || cachedPdfPage(reference, neighbour) != null) return@launch
                val prefetched = withContext(Dispatchers.IO) { attachmentPreview.pdfPage(reference, neighbour) }
                if (openedPdfReference?.id != reference.id) return@launch
                cachePdfPage(reference, prefetched)
            }
        }
    }

    fun closePdfPreview() {
        sourceOpenGeneration += 1
        pdfPreviewRequestGeneration += 1
        pdfNeighbourPrefetchJob?.cancel()
        pdfNeighbourPrefetchJob = null
        openedPdfReference = null
        openedSourceReference = null
        state = state.copy(pdfPreview = null, pdfPreviewLoading = false)
    }

    fun openMarkdown(id: GlmOcrTaskId) {
        state = state.copy(notice = null)
        viewModelScope.launch {
            val reference = state.resultReference?.takeIf { state.selectedTaskId == id }
                ?: withContext(Dispatchers.IO) { owner.resultReference(id) }
            if (state.selectedTaskId != id) return@launch
            if (reference == null) {
                state = state.copy(notice = "Markdown 文件未能从本机回读。")
                return@launch
            }
            val preview = withContext(Dispatchers.IO) { attachmentPreview.text(reference) }
            if (state.selectedTaskId == id) {
                state = state.copy(
                    resultReference = reference,
                    textPreview = preview,
                    notice = preview.unavailableReason.takeIf { preview.text == null },
                )
            }
        }
    }

    fun closeTextPreview() { state = state.copy(textPreview = null) }

    fun requestAttachmentTransfer(id: AttachmentId, action: AttachmentTransferAction) {
        val reference = listOfNotNull(state.resultReference, openedSourceReference, openedPdfReference).firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val item = withContext(Dispatchers.IO) {
                when (val opened = attachmentPreview.openVerified(reference)) {
                    is com.nanzhufeng.ai.domain.AttachmentOpenResult.Opened -> AttachmentTransferItem(
                        id = reference.id,
                        displayName = reference.displayName,
                        mimeType = reference.mimeType,
                        byteCount = opened.byteCount,
                        open = opened.open,
                    )
                    is com.nanzhufeng.ai.domain.AttachmentOpenResult.Rejected -> null
                }
            }
            state = if (item == null) {
                state.copy(notice = "该本地 Markdown 文件不可用，无法${if (action == AttachmentTransferAction.DOWNLOAD) "下载" else "分享"}。")
            } else {
                state.copy(
                    attachmentTransfer = AttachmentTransferRequest(item.id, action, item.displayName, item.mimeType, item.byteCount, item.open, listOf(item)),
                    notice = null,
                )
            }
        }
    }

    fun consumeAttachmentTransfer(id: AttachmentId) {
        if (state.attachmentTransfer?.id == id) state = state.copy(attachmentTransfer = null)
    }

    fun startSelected() {
        val task = state.selectedTask ?: return
        viewModelScope.launch {
            val queued = withContext(Dispatchers.IO) { owner.queue(task.id) }
            if (queued == null) state = state.copy(notice = "任务未能从本机回读。")
            else {
                val scheduled = runCatching { scheduler.enqueue(task.id) }.isSuccess
                if (scheduled) reload(task.id, "已加入转写队列；离开页面后任务仍会继续。")
                else {
                    withContext(Dispatchers.IO) { owner.failScheduling(task.id) }
                    reload(task.id, "转写任务未能加入后台队列，请稍后手动重试。")
                }
            }
        }
    }

    fun retry(taskId: GlmOcrTaskId) {
        viewModelScope.launch {
            val queued = withContext(Dispatchers.IO) { owner.queue(taskId) }
            if (queued == null) state = state.copy(notice = "任务未能从本机回读。")
            else {
                val scheduled = runCatching { scheduler.enqueue(taskId) }.isSuccess
                if (scheduled) reload(taskId, "已按你的操作重新转写；不会自动连续重试。")
                else {
                    withContext(Dispatchers.IO) { owner.failScheduling(taskId) }
                    reload(taskId, "转写任务未能加入后台队列，请稍后手动重试。")
                }
            }
        }
    }

    fun showActions(id: GlmOcrTaskId) { state = state.copy(actionTaskId = id) }
    fun dismissActions() { state = state.copy(actionTaskId = null) }

    fun continueInNewConversation() {
        val id = state.actionTaskId ?: return
        continueInNewConversation(id, dismissActions = true)
    }

    fun continueSelectedInNewConversation(id: GlmOcrTaskId) {
        continueInNewConversation(id, dismissActions = false)
    }

    private fun continueInNewConversation(id: GlmOcrTaskId, dismissActions: Boolean) {
        state = state.copy(actionTaskId = if (dismissActions) null else state.actionTaskId, notice = null)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { owner.continueInNewConversation(id) }) {
                is GlmOcrContinueResult.Created -> state = state.copy(createdConversationId = result.conversationId)
                is GlmOcrContinueResult.Rejected -> state = state.copy(notice = result.reason)
            }
        }
    }

    fun consumeCreatedConversation() { state = state.copy(createdConversationId = null) }
    fun refresh() { reload(state.selectedTaskId, state.notice) }

    fun pollWhileActive() {
        viewModelScope.launch {
            while (isActive && state.hasActiveTask) {
                delay(900)
                reload(state.selectedTaskId, state.notice)
            }
        }
    }

    private fun reload(selected: GlmOcrTaskId?, notice: String?, importing: Boolean = state.importing) {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val tasks = owner.tasks()
                val selectedTask = selected?.let { id -> tasks.firstOrNull { it.id == id } }
                val result = selectedTask?.takeIf { it.status == GlmOcrTaskStatus.COMPLETED }?.let { owner.resultReference(it.id) }
                Triple(tasks, selectedTask, result)
            }
            state = state.copy(
                loading = false,
                importing = importing,
                tasks = snapshot.first,
                selectedTaskId = snapshot.second?.id,
                resultReference = snapshot.third,
                notice = if (snapshot.second?.status == GlmOcrTaskStatus.COMPLETED && snapshot.third == null) {
                    "Markdown 文件未能从本机回读。"
                } else notice,
            )
        }
    }

    class Factory(
        private val owner: GlmOcrTaskOwner,
        private val scheduler: AndroidGlmOcrScheduler,
        private val documents: AndroidDocumentSelectionReader,
        private val attachmentPreview: ConversationAttachmentPreviewProjection,
        private val pdfPreviewPosition: PdfPreviewPositionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GlmOcrWorkspaceViewModel::class.java))
            return GlmOcrWorkspaceViewModel(owner, scheduler, documents, attachmentPreview, pdfPreviewPosition) as T
        }
    }
}

@Composable
internal fun GlmOcrWorkspacePage(
    state: GlmOcrWorkspaceUiState,
    viewModel: GlmOcrWorkspaceViewModel,
    onBack: () -> Unit,
    onOpenConversation: (ConversationId) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::selectSource)
    val detailVisible = state.selectedTask != null
    val returnToPreviousLevel = if (detailVisible) viewModel::closeDetail else onBack
    BackHandler(onBack = returnToPreviousLevel)
    LaunchedEffect(state.hasActiveTask) { if (state.hasActiveTask) viewModel.pollWhileActive() }
    LaunchedEffect(state.createdConversationId) {
        state.createdConversationId?.let {
            onOpenConversation(it)
            viewModel.consumeCreatedConversation()
        }
    }
    state.attachmentTransfer?.let { transfer ->
        AttachmentTransferEffect(transfer, viewModel::consumeAttachmentTransfer)
    }

    Surface(
        modifier = Modifier.fillMaxSize().p5aDismissOnInwardEdgeSwipe(returnToPreviousLevel),
        color = ForegroundSurface,
    ) {
        Column(Modifier.fillMaxSize()) {
            OcrTopBar(onBack = returnToPreviousLevel)
            if (detailVisible) {
                OcrTaskDetail(
                    state = state,
                    onOpenSource = viewModel::openSource,
                    onStart = viewModel::startSelected,
                    onRetry = viewModel::retry,
                    onOpenMarkdown = viewModel::openMarkdown,
                    onContinueInNewConversation = viewModel::continueSelectedInNewConversation,
                )
            } else {
                OcrTaskList(
                    state = state,
                    onOpenDetail = viewModel::openDetail,
                    onLongPress = viewModel::showActions,
                    onChoose = { picker.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) },
                )
            }
        }
    }

    state.actionTaskId?.let { taskId ->
        val task = state.tasks.firstOrNull { it.id == taskId }
        AlertDialog(
            onDismissRequest = viewModel::dismissActions,
            shape = P5AMultilineSurfaceShape,
            containerColor = ForegroundSurface,
            title = { Text("Markdown 文件", fontWeight = FontWeight.Bold) },
            text = { Text("${task?.sourceDisplayName.orEmpty()} → ${task?.resultName().orEmpty()}", color = SecondaryText) },
            confirmButton = {
                TextButton(onClick = viewModel::continueInNewConversation) {
                    Text("在新对话中继续", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissActions) { Text("取消") } },
        )
    }
    state.imagePreview?.let { preview ->
        SharedImagePreviewDialog(
            preview = preview,
            relatedImageIds = listOf(preview.id),
            onOpenImagePreview = {},
            onClose = viewModel::closeImagePreview,
            onRequestAttachmentTransfers = { ids: List<AttachmentId>, action: AttachmentTransferAction ->
                ids.forEach { id -> viewModel.requestAttachmentTransfer(id, action) }
            },
        )
    }
    state.pdfPreview?.let { preview ->
        CompositionLocalProvider(LocalAttachmentTransferRequest provides viewModel::requestAttachmentTransfer) {
            SharedPdfPreviewDialog(preview, state.pdfPreviewLoading, viewModel::openPdfPage, viewModel::closePdfPreview)
        }
    }
    state.textPreview?.let { preview ->
        SharedTextPreviewDialog(
            preview = preview,
            onClose = viewModel::closeTextPreview,
            onRequestAttachmentTransfer = viewModel::requestAttachmentTransfer,
        )
    }
}

@Composable
private fun OcrTopBar(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
        }
        Text(
            GLM_OCR_WORKSPACE_TITLE,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun OcrTaskList(
    state: GlmOcrWorkspaceUiState,
    onOpenDetail: (GlmOcrTaskId) -> Unit,
    onLongPress: (GlmOcrTaskId) -> Unit,
    onChoose: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.notice?.let { message -> item { OcrNotice(message) } }
            if (state.loading) {
                item { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
            } else if (state.tasks.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("还没有转写结果", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("从底部选择图片或 PDF 开始。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                items(state.tasks, key = { it.id.value }) { task ->
                    OcrTaskRow(
                        task = task,
                        onClick = { onOpenDetail(task.id) },
                        onLongPress = { if (task.status == GlmOcrTaskStatus.COMPLETED) onLongPress(task.id) },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { if (!state.importing) onChoose() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            shape = RoundedCornerShape(28.dp),
            containerColor = AccentOrange,
            contentColor = Color.White,
            icon = {
                if (state.importing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.UploadFile, contentDescription = null)
            },
            text = { Text(if (state.importing) "正在导入…" else "选择图片或 PDF", fontWeight = FontWeight.Bold) },
        )
    }
}

@Composable
private fun OcrTaskDetail(
    state: GlmOcrWorkspaceUiState,
    onOpenSource: (GlmOcrTaskId) -> Unit,
    onStart: () -> Unit,
    onRetry: (GlmOcrTaskId) -> Unit,
    onOpenMarkdown: (GlmOcrTaskId) -> Unit,
    onContinueInNewConversation: (GlmOcrTaskId) -> Unit,
) {
    val task = state.selectedTask ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(task.sourceDisplayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        state.notice?.let { message -> item { OcrNotice(message) } }
        item { Text("原始文件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onOpenSource(task.id) }, onLongClick = {}),
                shape = P5AMultilineSurfaceShape,
                color = PageBackground,
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Description, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.sourceDisplayName, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${task.sourceMimeType} · ${task.sourceByteCount.fileSizeLabel()}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        Text("点击查看原始 ${if (task.sourceMimeType == "application/pdf") "PDF" else "图片"}", color = AccentOrange, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SecondaryText)
                }
            }
        }
        when (task.status) {
            GlmOcrTaskStatus.READY -> item { OcrStartCard(onStart) }
            GlmOcrTaskStatus.QUEUED, GlmOcrTaskStatus.PROCESSING -> item { OcrProgressCard(task) }
            GlmOcrTaskStatus.FAILED -> item { OcrFailureCard(task, onRetry) }
            GlmOcrTaskStatus.COMPLETED -> {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Markdown 文件", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { onContinueInNewConversation(task.id) }) { Text("加入新对话", color = AccentOrange) }
                    }
                }
                item {
                    val result = state.resultReference
                    if (result == null) {
                        Text("Markdown 文件未能从本机回读。", color = ErrorRed)
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onOpenMarkdown(task.id) }, onLongClick = {}),
                            shape = P5AMultilineSurfaceShape,
                            color = PageBackground,
                        ) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Description, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(result.displayName ?: task.resultName(), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${result.mimeType} · ${result.byteCount.fileSizeLabel()}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                                    Text("点击查看文本内容", color = AccentOrange, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SecondaryText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrStartCard(onStart: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = P5AMultilineSurfaceShape, color = PageBackground) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("开始转写", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = P5AInteractiveShape,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
            ) { Text("开始转写", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun OcrProgressCard(task: GlmOcrTask) {
    Surface(Modifier.fillMaxWidth(), shape = P5AMultilineSurfaceShape, color = PageBackground) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (task.status == GlmOcrTaskStatus.QUEUED) "等待处理" else "正在转写", fontWeight = FontWeight.Bold)
                Text("离开页面后任务仍会继续。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OcrFailureCard(task: GlmOcrTask, onRetry: (GlmOcrTaskId) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = P5AMultilineSurfaceShape, color = PageBackground) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(task.safeErrorCode.userErrorLabel(), color = ErrorRed, fontWeight = FontWeight.Bold)
            Button(
                onClick = { onRetry(task.id) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = P5AInteractiveShape,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
            ) { Text("重新转写", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun OcrNotice(message: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = PageBackground) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = if (message.contains("失败") || message.contains("未能")) ErrorRed else SecondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun OcrTaskRow(task: GlmOcrTask, onClick: () -> Unit, onLongPress: () -> Unit) {
    val status = when (task.status) {
        GlmOcrTaskStatus.READY -> "待确认"
        GlmOcrTaskStatus.QUEUED -> "等待处理"
        GlmOcrTaskStatus.PROCESSING -> "正在转写"
        GlmOcrTaskStatus.COMPLETED -> "已生成"
        GlmOcrTaskStatus.FAILED -> task.safeErrorCode.userErrorLabel()
    }
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = P5AMultilineSurfaceShape,
        color = PageBackground,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = ForegroundSurface) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    tint = if (task.status == GlmOcrTaskStatus.COMPLETED) AccentOrange else SecondaryText,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.sourceDisplayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.sourceDisplayName, color = SecondaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = SecondaryText, modifier = Modifier.padding(horizontal = 5.dp).size(14.dp))
                    Text(task.resultName(), color = SecondaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                val accounting = buildList {
                    task.pageCount?.let { add("$it 页") }
                    (listOfNotNull(task.inputTokens, task.outputTokens).sum().takeIf { it > 0 })?.let { add("$it Token") }
                    task.costCnyMicros?.let { add("¥%.4f".format(it / 1_000_000.0)) }
                    add(task.updatedAt.timestampLabel())
                }.joinToString(" · ")
                Text("$status · $accounting", color = if (task.status == GlmOcrTaskStatus.FAILED) ErrorRed else SecondaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (task.status in setOf(GlmOcrTaskStatus.QUEUED, GlmOcrTaskStatus.PROCESSING)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SecondaryText)
            }
        }
    }
}

private fun GlmOcrTask.resultName(): String = sourceDisplayName.substringBeforeLast('.', sourceDisplayName).ifBlank { "GLM-OCR" } + "-OCR.md"
private fun Long.fileSizeLabel(): String = when {
    this >= 1024L * 1024L -> "%.1f MB".format(this / (1024.0 * 1024.0))
    this >= 1024L -> "%.1f KB".format(this / 1024.0)
    else -> "$this B"
}
private fun java.time.Instant.timestampLabel(): String = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(this)
private fun String?.userErrorLabel(): String = when (this) {
    "ZHIPU_NOT_ENABLED", "CREDENTIAL_MISSING" -> "请先在设置启用智谱并保存 API Key"
    "AUTHENTICATION" -> "API Key 无效或无 GLM-OCR 权限"
    "BALANCE" -> "智谱余额不足"
    "RATE_LIMIT" -> "请求过于频繁"
    "SOURCE_TOO_LARGE" -> "文件超过服务限制"
    "RESPONSE_TOO_LARGE", "MARKDOWN_TOO_LARGE" -> "返回内容过大"
    "RESPONSE_FORMAT", "EMPTY_MARKDOWN" -> "服务未返回可用 Markdown"
    "TIMEOUT_UNKNOWN", "NETWORK_UNKNOWN" -> "结果未知；为避免重复计费未自动重试"
    "SCHEDULER_UNAVAILABLE" -> "后台队列暂不可用"
    "RESULT_PERSISTENCE", "RESULT_METADATA", "INTERNAL_FAILURE" -> "本机结果保存失败"
    else -> "转写失败"
}
