package com.nanzhufeng.ai.domain

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ConversationCompletionWorkQueueTest {
    @Test fun `slow title serializes same conversation without blocking another conversation`() {
        val executor = Executors.newFixedThreadPool(2)
        val queue = ConversationCompletionWorkQueue(executor)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val sameConversation = CountDownLatch(1)
        val otherConversation = CountDownLatch(1)
        try {
            queue.enqueue("CHAT:one") { entered.countDown(); release.await() }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            queue.enqueue("CHAT:one") { sameConversation.countDown() }
            queue.enqueue("CHAT:two") { otherConversation.countDown() }
            assertTrue(otherConversation.await(2, TimeUnit.SECONDS))
            assertEquals(1L, sameConversation.count)
            release.countDown()
            assertTrue(sameConversation.await(2, TimeUnit.SECONDS))
        } finally { release.countDown(); executor.shutdown(); assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)) }
    }

    @Test fun `failed optional work does not discard later work`() {
        val executor = Executors.newSingleThreadExecutor()
        val queue = ConversationCompletionWorkQueue(executor)
        val done = CountDownLatch(1)
        try {
            queue.enqueue("CHAT:one") { error("synthetic optional failure") }
            queue.enqueue("CHAT:one") { done.countDown() }
            assertTrue(done.await(2,TimeUnit.SECONDS))
        } finally { executor.shutdown(); assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)) }
    }
}
