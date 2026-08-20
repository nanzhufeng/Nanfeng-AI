package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class P5BTaskExecutionPolicyContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)

    @Test fun `policy has no system scheduled or automatic network execution`() {
        assertFalse(TaskExecutionPolicy.workManagerIntroduced)
        assertEquals(LocalTaskKind.entries.toSet(), TaskExecutionPolicy.rules.map { it.kind }.toSet())
        assertTrue(TaskExecutionPolicy.rules.none { it.permitsSystemBackgroundScheduling })
        assertTrue(TaskExecutionPolicy.rule(LocalTaskKind.WEB_TEXT_SNAPSHOT).networkAllowedOnlyInForeground)
        assertTrue(TaskExecutionPolicy.rules.filter { it.kind != LocalTaskKind.OFFLINE_EVAL }.all { it.processDeath == ProcessDeathDisposition.FAIL_INTERRUPTED })
    }

    @Test fun `persisted import work maps to interrupted and completed cancel is stable`() {
        val knowledge = ManageKnowledgeUseCase(KnowledgeDomain(clock), EmptyKnowledgeRepository)
        val markdownTasks = MarkdownTasks().also { it.save(MarkdownImportTask(ImportTaskId.new(), MarkdownImportTaskStatus.PARSING, null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)) }
        val jsonTasks = JsonTasks().also { it.save(JsonKnowledgeTask(JsonKnowledgeTaskId.new(), JsonKnowledgeTaskStatus.PARSING, null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)) }
        val pdfTasks = PdfTasks().also { it.save(PdfTextImportTask(PdfTextImportTaskId.new(), PdfTextImportTaskStatus.EXTRACTING, null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)) }
        val webTasks = WebTasks().also { it.save(WebTextSnapshotTask(WebTextSnapshotTaskId.new(), WebTextSnapshotStatus.FETCHING, "https://example.test/", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)) }

        val markdown = ManageMarkdownImportUseCase(markdownTasks, EmptyMarkdownAssets, MarkdownKnowledgeAdapter(KnowledgeDomain(clock)), knowledge, clock)
        val json = ManageJsonKnowledgeImportUseCase(jsonTasks, EmptyJsonAssets, JsonKnowledgeAdapter(KnowledgeDomain(clock)), knowledge, clock)
        val pdf = ManagePdfTextKnowledgeImportUseCase(pdfTasks, EmptyPdfAssets, PdfTextKnowledgeAdapter(KnowledgeDomain(clock)), knowledge, clock)
        val web = ManageWebTextSnapshotUseCase(webTasks, EmptyWebAssets, EmptyFetcher, knowledge, clock)

        assertEquals(1, markdown.recoverInterrupted()); assertEquals(MarkdownImportFailure.INTERRUPTED, markdown.list().single().failure)
        assertEquals(1, json.recoverInterrupted()); assertEquals(JsonKnowledgeFailure.INTERRUPTED, json.list().single().failure)
        assertEquals(1, pdf.recoverInterrupted()); assertEquals(PdfTextImportFailure.INTERRUPTED, pdf.list().single().failure)
        assertEquals(1, web.recoverInterrupted()); assertEquals(WebTextSnapshotFailure.INTERRUPTED, web.list().single().failure)

        val completed = markdownTasks.save(markdown.list().single().copy(status = MarkdownImportTaskStatus.COMPLETED, failure = null))
        assertEquals(MarkdownImportTaskStatus.COMPLETED, markdown.cancel(completed.id).status)
    }

    private object EmptyKnowledgeRepository : KnowledgeManagementRepository {
        override fun mutate(intent: KnowledgeIntent, fingerprint: String): KnowledgeMutationResult = error("not used")
        override fun findSnapshot(id: KnowledgeItemId): KnowledgeSnapshot? = null
        override fun listSnapshots(filter: KnowledgeSearchFilter): List<KnowledgeSnapshot> = emptyList()
    }
    private class MarkdownTasks : MarkdownImportTaskRepository { private val values = linkedMapOf<ImportTaskId, MarkdownImportTask>(); override fun save(task: MarkdownImportTask) = task.also { values[it.id] = it }; override fun find(id: ImportTaskId) = values[id]; override fun list() = values.values.toList() }
    private class JsonTasks : JsonKnowledgeTaskRepository { private val values = linkedMapOf<JsonKnowledgeTaskId, JsonKnowledgeTask>(); override fun save(task: JsonKnowledgeTask) = task.also { values[it.id] = it }; override fun find(id: JsonKnowledgeTaskId) = values[id]; override fun list() = values.values.toList() }
    private class PdfTasks : PdfTextImportTaskRepository { private val values = linkedMapOf<PdfTextImportTaskId, PdfTextImportTask>(); override fun save(task: PdfTextImportTask) = task.also { values[it.id] = it }; override fun find(id: PdfTextImportTaskId) = values[id]; override fun list() = values.values.toList() }
    private class WebTasks : WebTextSnapshotTaskRepository { private val values = linkedMapOf<WebTextSnapshotTaskId, WebTextSnapshotTask>(); override fun save(task: WebTextSnapshotTask) = task.also { values[it.id] = it }; override fun find(id: WebTextSnapshotTaskId) = values[id]; override fun list() = values.values.toList() }
    private object EmptyMarkdownAssets : MarkdownPrivateAssetStore { override fun copy(request: MarkdownPrivateCopyRequest): MarkdownPrivateCopyResult = error("not used"); override fun read(storageKey: String): ByteArray? = null }
    private object EmptyJsonAssets : JsonKnowledgePrivateAssetStore { override fun copy(request: JsonKnowledgePrivateCopyRequest): JsonKnowledgePrivateCopyResult = error("not used"); override fun read(storageKey: String): ByteArray? = null }
    private object EmptyPdfAssets : PdfTextPrivateAssetStore { override fun copy(request: PdfTextPrivateCopyRequest): PdfTextPrivateCopyResult = error("not used"); override fun read(storageKey: String): ByteArray? = null }
    private object EmptyWebAssets : WebTextSnapshotPrivateAssetStore { override fun copy(taskId: WebTextSnapshotTaskId, asset: WebTextSnapshotAsset, html: ByteArray) = false; override fun read(storageKey: String): ByteArray? = null }
    private object EmptyFetcher : PublicWebFetcher { override fun fetch(url: SafeWebUrl): WebFetchResult = WebFetchResult.Failure(WebTextSnapshotFailure.NETWORK) }
}
