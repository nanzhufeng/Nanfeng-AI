package com.nanzhufeng.ai.domain

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** Optional work after a durable reply. Same-conversation work is serialized, never awaited by Send. */
class ConversationCompletionWorkQueue(private val executor: Executor) {
    private val tails = ConcurrentHashMap<String, CompletableFuture<Void>>()

    fun enqueue(key: String, work: () -> Unit) {
        synchronized(tails) {
            val previous = tails[key] ?: CompletableFuture.completedFuture<Void>(null)
            val next = previous.handle { _, _ -> Unit }.thenRunAsync({ work() }, executor)
            tails[key] = next
            next.whenComplete { _, _ -> tails.remove(key, next) }
        }
    }
}
