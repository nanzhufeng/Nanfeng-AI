package com.nanzhufeng.ai.ui

import com.nanzhufeng.ai.data.P6KZipImportUiStore
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJob
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipManualLinkTarget
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P6KZipImportViewModelRecoveryContractsTest {
    @Test
    fun `show reads only persisted task and recovery job projections without ZIP IO`() {
        val listed = CountDownLatch(1)
        val listReads = AtomicInteger()
        val zipReads = AtomicInteger()
        val store = object : P6KZipImportUiStore {
            override fun list(): List<P6KZipImportTask> {
                listReads.incrementAndGet()
                listed.countDown()
                return emptyList()
            }
            override fun recoveryJobs(): List<P6KZipAssetRecoveryJob> = emptyList()
            override fun stage(provider: ThirdPartyZipProvider, displayName: String, mimeType: String, input: InputStream): P6KZipImportTask { zipReads.incrementAndGet(); error("not called") }
            override fun retryAssetRecovery(taskId: String) { zipReads.incrementAndGet() }
            override fun cancel(id: String): Boolean { zipReads.incrementAndGet(); return false }
            override fun manualLinkTargets(taskId: String): List<P6KZipManualLinkTarget> { zipReads.incrementAndGet(); return emptyList() }
            override fun linkUnmappedAsset(taskId: String, entryName: String, conversationId: String, messageId: String): P6KZipImportTask { zipReads.incrementAndGet(); error("not called") }
        }

        P6KZipImportViewModel(store)

        assertTrue("show did not read its persisted projection", listed.await(2, TimeUnit.SECONDS))
        assertEquals(1, listReads.get())
        assertEquals(0, zipReads.get())
    }
}
