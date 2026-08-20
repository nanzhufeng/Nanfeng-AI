package com.nanzhufeng.ai.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.data.AndroidGalleryOpenResult
import com.nanzhufeng.ai.data.AndroidGallerySelectionReader
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AiRequestPreview
import com.nanzhufeng.ai.domain.AiTaskRunResult
import com.nanzhufeng.ai.domain.CandidateReviewStatus
import com.nanzhufeng.ai.domain.CaptureDraft
import com.nanzhufeng.ai.domain.CaptureDraftRepository
import com.nanzhufeng.ai.domain.CaptureGalleryImageResult
import com.nanzhufeng.ai.domain.CaptureGalleryImageUseCase
import com.nanzhufeng.ai.domain.CaptureTextDraftResult
import com.nanzhufeng.ai.domain.CaptureTextDraftUseCase
import com.nanzhufeng.ai.domain.CaptureTextInput
import com.nanzhufeng.ai.domain.RestoreCaptureDraftResult
import com.nanzhufeng.ai.domain.RestoreLatestCaptureDraftUseCase
import com.nanzhufeng.ai.domain.ConfirmAiRequest
import com.nanzhufeng.ai.domain.GeneratedCandidateRepository
import com.nanzhufeng.ai.domain.InvocationRepository
import com.nanzhufeng.ai.domain.PersistGeneratedCandidateUseCase
import com.nanzhufeng.ai.domain.RunAiTaskUseCase
import com.nanzhufeng.ai.domain.SaveCandidateReviewUseCase
import com.nanzhufeng.ai.domain.SaveKnowledgeResult
import com.nanzhufeng.ai.domain.StoredGeneratedCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

data class CapturedImageState(
    val draft: CaptureDraft,
    val previewBytes: ByteArray,
)

data class CaptureScreenState(
    val isRestoring: Boolean = true,
    val isImporting: Boolean = false,
    val isSavingText: Boolean = false,
    val capturedText: CaptureDraft? = null,
    val capturedImage: CapturedImageState? = null,
    val notice: String? = null,
    val error: CaptureUiError? = null,
    val requestPreview: AiRequestPreview? = null,
    val isConsentChecked: Boolean = false,
    val isRunningAi: Boolean = false,
    val pendingCandidate: StoredGeneratedCandidate? = null,
    val isSavingCandidate: Boolean = false,
)

data class CaptureUiError(val title: String, val suggestion: String)

class CaptureViewModel(
    private val galleryReader: AndroidGallerySelectionReader,
    private val captureGalleryImage: CaptureGalleryImageUseCase,
    private val captureTextDraft: CaptureTextDraftUseCase,
    private val restoreLatestCaptureDraft: RestoreLatestCaptureDraftUseCase,
    private val draftRepository: CaptureDraftRepository,
    private val confirmAiRequest: ConfirmAiRequest,
    private val runAiTask: RunAiTaskUseCase,
    private val persistGeneratedCandidate: PersistGeneratedCandidateUseCase,
    private val generatedCandidateRepository: GeneratedCandidateRepository,
    private val invocationRepository: InvocationRepository,
    private val saveCandidateReview: SaveCandidateReviewUseCase,
) : ViewModel() {
    var state by mutableStateOf(CaptureScreenState())
        private set
    private var restoreJob: Job? = null

    init {
        restoreDraft()
        restorePendingCandidate()
    }

    fun onTextInput(input: CaptureTextInput) {
        restoreJob?.cancel()
        state = state.copy(isRestoring = false, isSavingText = true, notice = null, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { captureTextDraft.execute(input) }
            state = when (result) {
                is CaptureTextDraftResult.Saved -> state.copy(
                    isRestoring = false,
                    isSavingText = false,
                    capturedText = result.draft,
                    capturedImage = null,
                    notice = if (input.sourceType == com.nanzhufeng.ai.domain.CaptureSourceType.ANDROID_TEXT_SHARE) {
                        "已保存系统分享的文本草稿。"
                    } else {
                        "文本已保存为本地草稿。"
                    },
                )
                is CaptureTextDraftResult.Rejected -> state.copy(
                    isSavingText = false,
                    error = result.error.toUiError(),
                )
            }
        }
    }

    fun onPhotoPickerResult(uri: Uri?) {
        if (uri == null) {
            state = state.copy(
                isImporting = false,
                notice = "已取消选择，当前草稿没有改变。",
                error = null,
            )
            return
        }
        restoreJob?.cancel()
        state = state.copy(isImporting = true, notice = null, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (val opened = galleryReader.open(uri)) {
                    is AndroidGalleryOpenResult.Opened -> captureGalleryImage.execute(opened.selection)
                    is AndroidGalleryOpenResult.Rejected -> CaptureGalleryImageResult.Rejected(opened.error)
                }
            }
            state = when (result) {
                CaptureGalleryImageResult.Cancelled -> state.copy(
                    isImporting = false,
                    notice = "已取消选择，当前草稿没有改变。",
                )
                is CaptureGalleryImageResult.Saved -> state.copy(
                    isRestoring = false,
                    isImporting = false,
                    capturedImage = CapturedImageState(result.draft, result.previewBytes),
                    capturedText = null,
                    notice = "图片已复制到 App 私有空间，并保存为本地草稿。",
                )
                is CaptureGalleryImageResult.Rejected -> state.copy(
                    isImporting = false,
                    error = result.error.toUiError(),
                )
            }
        }
    }

    fun clearMessage() {
        state = state.copy(notice = null, error = null)
    }

    fun requestAiConfirmation() {
        val draft = state.currentDraft() ?: run {
            state = state.copy(error = CaptureUiError("请先保存一份本地草稿", "文字或图片必须先成为本地草稿，才可以进入本地 Mock 整理。"))
            return
        }
        if (state.isRunningAi || state.pendingCandidate != null) return
        state = state.copy(requestPreview = confirmAiRequest.preview(draft), isConsentChecked = false, notice = null, error = null)
    }

    fun setEgressConsentChecked(checked: Boolean) {
        state = state.copy(isConsentChecked = checked)
    }

    fun dismissAiConfirmation() {
        if (!state.isRunningAi) state = state.copy(requestPreview = null, isConsentChecked = false)
    }

    fun confirmAndRunAi() {
        val draft = state.currentDraft() ?: return
        if (!state.isConsentChecked || state.requestPreview == null || state.isRunningAi) return
        val task = confirmAiRequest.approve(draft)
        state = state.copy(requestPreview = null, isConsentChecked = false, isRunningAi = true, notice = null, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runAiTask.execute(task, draft) }
            state = when (result) {
                is AiTaskRunResult.Success -> {
                    val stored = withContext(Dispatchers.IO) {
                        persistGeneratedCandidate.execute(result.candidate, result.invocation, task, draft)
                    }
                    state.copy(isRunningAi = false, pendingCandidate = stored, notice = "本地 Mock 已完成；结果仍是候选，尚未保存为知识。")
                }
                is AiTaskRunResult.Failure -> state.copy(
                    isRunningAi = false,
                    error = result.error.toUiError(),
                )
            }
        }
    }

    fun saveCandidate(title: String, body: String) {
        val candidate = state.pendingCandidate ?: return
        if (state.isSavingCandidate || candidate.status != CandidateReviewStatus.PENDING_REVIEW) return
        state = state.copy(isSavingCandidate = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val invocation = requireNotNull(invocationRepository.findById(candidate.invocationId))
                val draft = requireNotNull(draftRepository.findById(candidate.draftId))
                saveCandidateReview.execute(candidate, invocation, draft, title.trim(), body.trim(), userConfirmed = true)
            }
            state = when (result) {
                is SaveKnowledgeResult.Saved -> state.copy(
                    isSavingCandidate = false,
                    pendingCandidate = null,
                    notice = "已确认并保存为本地知识。",
                )
                is SaveKnowledgeResult.Rejected -> state.copy(isSavingCandidate = false, error = result.error.toUiError())
            }
        }
    }

    fun discardCandidate() {
        val candidate = state.pendingCandidate ?: return
        if (state.isSavingCandidate) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { saveCandidateReview.discard(candidate) }
            state = state.copy(pendingCandidate = null, notice = "已取消候选核对；本地草稿和调用记录仍被保留。")
        }
    }

    private fun restoreDraft() {
        restoreJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { restoreLatestCaptureDraft.execute() }
            state = when (result) {
                RestoreCaptureDraftResult.Empty -> state.copy(isRestoring = false)
                is RestoreCaptureDraftResult.RestoredText -> state.copy(
                    isRestoring = false,
                    capturedText = result.draft,
                    capturedImage = null,
                    notice = "已恢复上次保存的文本草稿。",
                )
                is RestoreCaptureDraftResult.RestoredImage -> state.copy(
                    isRestoring = false,
                    capturedImage = CapturedImageState(result.draft, result.previewBytes),
                    capturedText = null,
                    notice = "已恢复上次保存的图片草稿。",
                )
                is RestoreCaptureDraftResult.Rejected -> state.copy(
                    isRestoring = false,
                    error = result.error.toUiError(),
                )
            }
        }
    }

    private fun restorePendingCandidate() {
        viewModelScope.launch {
            val pending = withContext(Dispatchers.IO) { generatedCandidateRepository.findLatestPendingReview() }
            if (pending != null) state = state.copy(pendingCandidate = pending)
        }
    }

    class Factory(
        private val galleryReader: AndroidGallerySelectionReader,
        private val captureGalleryImage: CaptureGalleryImageUseCase,
        private val captureTextDraft: CaptureTextDraftUseCase,
        private val restoreLatestCaptureDraft: RestoreLatestCaptureDraftUseCase,
        private val draftRepository: CaptureDraftRepository,
        private val confirmAiRequest: ConfirmAiRequest,
        private val runAiTask: RunAiTaskUseCase,
        private val persistGeneratedCandidate: PersistGeneratedCandidateUseCase,
        private val generatedCandidateRepository: GeneratedCandidateRepository,
        private val invocationRepository: InvocationRepository,
        private val saveCandidateReview: SaveCandidateReviewUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CaptureViewModel::class.java))
            return CaptureViewModel(
                galleryReader, captureGalleryImage, captureTextDraft, restoreLatestCaptureDraft, draftRepository,
                confirmAiRequest, runAiTask, persistGeneratedCandidate, generatedCandidateRepository,
                invocationRepository, saveCandidateReview,
            ) as T
        }
    }
}

internal fun CaptureScreenState.currentDraft(): CaptureDraft? = capturedImage?.draft ?: capturedText

private fun AiTaskError.toUiError(): CaptureUiError = when (this) {
    AiTaskError.AttachmentUnsupportedType -> CaptureUiError(
        title = "这张图片暂时无法读取",
        suggestion = "请选择 JPG、PNG、WebP 或系统相册中可正常预览的图片。",
    )
    AiTaskError.AttachmentTooLarge -> CaptureUiError(
        title = "图片超过 20 MB",
        suggestion = "请先压缩图片，或选择体积更小的版本后重试。",
    )
    AiTaskError.AttachmentSourceUnavailable -> CaptureUiError(
        title = "系统没有提供可读取的图片",
        suggestion = "请返回相册重新选择；原有草稿不会丢失。",
    )
    AiTaskError.AttachmentIntegrityMismatch -> CaptureUiError(
        title = "私有副本校验失败",
        suggestion = "请重新选择图片；若反复出现，请保留当前页面用于诊断。",
    )
    AiTaskError.PersistenceConflict -> CaptureUiError(
        title = "草稿保存失败",
        suggestion = "请稍后重试；已选择图片不会被误报为保存成功。",
    )
    AiTaskError.CaptureTextBlank -> CaptureUiError(
        title = "文本为空，未创建草稿",
        suggestion = "请输入或分享包含文字的内容；已有草稿不会丢失。",
    )
    AiTaskError.CandidateNotConfirmed -> CaptureUiError("候选尚未确认", "请在核对页确认保存；取消不会把候选变成知识。")
    AiTaskError.ProvenanceMismatch -> CaptureUiError("候选来源无法核对", "请保留草稿并重新发起本地 Mock 整理。")
    AiTaskError.ConsentRequired -> CaptureUiError("本次确认已失效", "请重新打开确认面并主动勾选确认。")
    else -> CaptureUiError(
        title = "图片导入失败",
        suggestion = "请重新选择图片；当前草稿没有改变。",
    )
}
