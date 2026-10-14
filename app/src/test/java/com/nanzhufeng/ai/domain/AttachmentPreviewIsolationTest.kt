package com.nanzhufeng.ai.domain

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AttachmentPreviewIsolationTest {
    @Test fun `preview exceptions do not cancel sibling generation`() = runBlocking {
        var generationFinished = false
        val failures = mutableListOf<AttachmentPreviewFailure>()
        coroutineScope {
            val generation = launch { yield(); generationFinished = true }
            protectAttachmentPreview(failures::add) { throw IllegalStateException("private payload must not reach logs") }
            protectAttachmentPreview(failures::add) { throw OutOfMemoryError() }
            generation.join()
        }
        assertTrue(generationFinished)
        assertEquals(listOf(AttachmentPreviewFailure.UNAVAILABLE, AttachmentPreviewFailure.MEMORY_PRESSURE), failures)
    }
    @Test fun `explicit preview cancellation remains cancellation`() = runBlocking {
        var failed = false
        try { protectAttachmentPreview({ failed = true }) { throw CancellationException() }; fail() }
        catch (_: CancellationException) { assertFalse(failed) }
    }
    @Test fun `decode lane serializes allocations without occupying generation dispatcher`() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>(); val release = java.util.concurrent.CountDownLatch(1)
        val first = async(AttachmentPreviewWork.dispatcher) { firstStarted.complete(Unit); release.await() }
        firstStarted.await()
        val second = async(AttachmentPreviewWork.dispatcher) { "second" }
        assertFalse(second.isCompleted)
        val generated = withContext(Dispatchers.Default) { "generation" }
        assertEquals("generation", generated)
        release.countDown(); first.await(); assertEquals("second",second.await())
    }
}
