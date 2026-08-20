package com.nanzhufeng.ai.domain

import java.util.concurrent.atomic.AtomicBoolean

/** Cooperative cancellation token for bounded local CPU/IO loops; never persisted or used for background work. */
interface TaskCancellation {
    fun isCancelled(): Boolean
    fun throwIfCancelled() {
        if (isCancelled()) throw LocalTaskCancelledException()
    }
}

class LocalTaskCancellation : TaskCancellation {
    private val cancelled = AtomicBoolean(false)
    fun cancel() { cancelled.set(true) }
    override fun isCancelled(): Boolean = cancelled.get()
}

object NoTaskCancellation : TaskCancellation { override fun isCancelled(): Boolean = false }
class LocalTaskCancelledException : RuntimeException()
