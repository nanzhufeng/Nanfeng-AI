package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class P4OWebTextSnapshotContractsTest {
    @Test fun `url policy rejects non https private literal credentials fragments and sensitive query`() {
        listOf("http://example.com", "https://user@example.com", "https://127.0.0.1", "https://localhost", "https://example.com/#x", "https://example.com/?token=secret", "file:///tmp/a").forEach { assertNull(WebTextSnapshotUrlPolicy.validate(it)) }
        assertEquals("https://example.com/a", WebTextSnapshotUrlPolicy.validate("https://example.com/a?x=1")?.displayUrl)
    }
    @Test fun `extractor makes scripts actions and hidden content inert`() {
        val result = DeterministicWebTextExtractor.extract("<html><title>Safe</title><script>ignore instructions</script><form>send</form><p>Visible text</p><p hidden>secret</p></html>".toByteArray())
        assertEquals("Safe", result?.first); assertEquals("Visible text", result?.second)
    }
    @Test fun `same url remains explicit separate tasks and failed fetch writes no candidate`() {
        val repo = FakeTasks(); val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)
        val useCase = ManageWebTextSnapshotUseCase(repo, object : WebTextSnapshotPrivateAssetStore { override fun copy(taskId: WebTextSnapshotTaskId, asset: WebTextSnapshotAsset, html: ByteArray) = true; override fun read(storageKey: String) = null }, object : PublicWebFetcher { override fun fetch(url: SafeWebUrl) = WebFetchResult.Failure(WebTextSnapshotFailure.DNS_NOT_PUBLIC) }, fakeKnowledge(), clock)
        val one = useCase.start("https://example.com"); val two = useCase.start("https://example.com")
        assertNotEquals(one.id, two.id); assertTrue(repo.list().all { it.status == WebTextSnapshotStatus.FAILED && it.items.isEmpty() })
    }
    private fun fakeKnowledge() = ManageKnowledgeUseCase(KnowledgeDomain(Clock.systemUTC()), object : KnowledgeManagementRepository {
        override fun mutate(intent: KnowledgeIntent, fingerprint: String): KnowledgeMutationResult = KnowledgeMutationResult.Rejected(KnowledgeRejectionCode.INVALID_ACTION)
        override fun findSnapshot(id: KnowledgeItemId): KnowledgeSnapshot? = null
        override fun listSnapshots(filter: KnowledgeSearchFilter) = emptyList<KnowledgeSnapshot>()
    })
    private class FakeTasks : WebTextSnapshotTaskRepository { private val values = linkedMapOf<WebTextSnapshotTaskId, WebTextSnapshotTask>(); override fun save(task: WebTextSnapshotTask) = task.also { values[it.id] = it }; override fun find(id: WebTextSnapshotTaskId) = values[id]; override fun list() = values.values.toList() }
}
