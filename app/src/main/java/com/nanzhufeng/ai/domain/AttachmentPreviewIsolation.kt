package com.nanzhufeng.ai.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/** A single bounded decode lane; generation keeps its existing execution owner. */
object AttachmentPreviewWork {
    val dispatcher = Executors.newSingleThreadExecutor { task -> Thread(task, "attachment-preview").apply { isDaemon = true } }.asCoroutineDispatcher()
}

enum class AttachmentPreviewFailure { UNAVAILABLE, MEMORY_PRESSURE }

suspend fun protectAttachmentPreview(onFailure: (AttachmentPreviewFailure) -> Unit, work: suspend () -> Unit) {
    try { work() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: OutOfMemoryError) { onFailure(AttachmentPreviewFailure.MEMORY_PRESSURE) }
    catch (_: Exception) { onFailure(AttachmentPreviewFailure.UNAVAILABLE) }
}
