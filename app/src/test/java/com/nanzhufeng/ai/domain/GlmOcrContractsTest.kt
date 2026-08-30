package com.nanzhufeng.ai.domain

import com.nanzhufeng.ai.ai.OfficialGlmOcrTransport
import com.nanzhufeng.ai.ai.ProviderChatOutcome
import com.nanzhufeng.ai.ai.ProviderChatRequest
import com.nanzhufeng.ai.ai.ProviderChatTransport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlmOcrContractsTest {
    @Test
    fun `utility preset is visible in settings catalog but absent from composer routing`() {
        val preset = NanfengModelServiceCatalog.preset(ModelPresetId.GLM_OCR)
        assertEquals("GLM-OCR", preset.displayName)
        assertEquals(ModelPresetUsage.DOCUMENT_OCR, preset.usage)
        assertEquals(ProviderId.ZHIPU, NanfengModelServiceCatalog.providerFor(preset.id))
        assertFalse(ComposerModelRoutingCatalog.choices.any { ModelPresetId.GLM_OCR in it.routes })
        assertFalse(NanfengModelServiceCatalog.autoRoutingCandidates(AutoRoutingFacts()).contains(ModelPresetId.GLM_OCR))
    }

    @Test
    fun `source and generated markdown keep exact durable lineage and continue as attachment`() {
        val fixture = fixture(GlmOcrTransportResult.Completed("# 表格\n\n|A|B|", "req-1", 2, 100, 40))
        val imported = assertType<GlmOcrImportResult.Imported>(fixture.owner.import(
            ConversationAttachmentSelection(ByteArrayInputStream("%PDF-1.7 sample".toByteArray()), "application/pdf", "财报.pdf"),
        )).task
        assertEquals(GlmOcrTaskStatus.READY, imported.status)
        assertEquals(imported.sourceAttachmentId, fixture.assets.findBySha256(imported.sourceSha256)?.id)
        val verifiedSource = requireNotNull(fixture.owner.sourceDocument(imported.id))
        assertEquals("财报.pdf", verifiedSource.displayName)
        assertEquals("application/pdf", verifiedSource.mimeType)
        assertEquals("%PDF-1.7 sample", verifiedSource.opened.open().use { it.readBytes().toString(Charsets.UTF_8) })
        val previewReference = requireNotNull(fixture.owner.sourceReference(imported.id))
        assertEquals(imported.sourceAttachmentId, previewReference.id)
        assertEquals(imported.sourceSha256, previewReference.sha256)

        fixture.owner.queue(imported.id)
        val completed = assertType<GlmOcrProcessResult.Completed>(fixture.owner.process(imported.id)).task
        assertEquals(GlmOcrTaskStatus.COMPLETED, completed.status)
        assertEquals(imported.sourceAttachmentId, completed.sourceAttachmentId)
        assertNotNull(completed.resultAttachmentId)
        assertEquals("# 表格\n\n|A|B|", fixture.owner.markdown(imported.id))
        assertEquals(2, completed.pageCount)
        assertEquals(28L, completed.costCnyMicros)
        val invocation = requireNotNull(fixture.invocations.listNewestFirst().single())
        assertEquals(InvocationStatus.SUCCEEDED, invocation.status)
        assertEquals(ProviderId.ZHIPU, invocation.providerId)
        assertEquals("glm-ocr", invocation.modelId)
        assertEquals(100L, invocation.usage.inputTokens)
        assertEquals(40L, invocation.usage.outputTokens)
        assertEquals(28L, invocation.cost.totalMicros)
        assertEquals(completed.toInvocationTaskId(), invocation.taskId)

        val continued = assertType<GlmOcrContinueResult.Created>(fixture.owner.continueInNewConversation(imported.id))
        assertEquals("conversation-with-markdown", continued.conversationId.value)
        assertEquals(completed.resultAttachmentId, fixture.attachedResult?.id)
    }

    @Test
    fun `unknown timeout is terminal and never silently retried by owner`() {
        val fixture = fixture(GlmOcrTransportResult.Failed("TIMEOUT_UNKNOWN"))
        val task = assertType<GlmOcrImportResult.Imported>(fixture.owner.import(
            ConversationAttachmentSelection(ByteArrayInputStream(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2)), "image/jpeg", "scan.jpg"),
        )).task
        fixture.owner.queue(task.id)
        val failed = assertType<GlmOcrProcessResult.Failed>(fixture.owner.process(task.id)).task
        assertEquals(GlmOcrTaskStatus.FAILED, failed.status)
        assertEquals("TIMEOUT_UNKNOWN", failed.safeErrorCode)
        assertEquals(InvocationStatus.FAILED, fixture.invocations.listNewestFirst().single().status)
        assertEquals(AiTaskError.ProviderTimedOut, fixture.invocations.listNewestFirst().single().error)
        assertType<GlmOcrProcessResult.Failed>(fixture.owner.process(task.id))
    }

    @Test
    fun `source and markdown are browsable and markdown body is searchable`() {
        val fixture = fixture(GlmOcrTransportResult.Completed("# 财报\n\n经营现金流明显改善", "req-search", 1, 20, 10))
        val imported = assertType<GlmOcrImportResult.Imported>(fixture.owner.import(
            ConversationAttachmentSelection(ByteArrayInputStream("%PDF-1.7 sample".toByteArray()), "application/pdf", "公司年报.pdf"),
        )).task
        fixture.owner.queue(imported.id)
        assertType<GlmOcrProcessResult.Completed>(fixture.owner.process(imported.id))

        val browsed = fixture.owner.searchDocuments("", ConversationSearchCategory.ALL)
        assertEquals(listOf(GlmOcrSearchDocumentKind.SOURCE, GlmOcrSearchDocumentKind.MARKDOWN), browsed.map { it.kind })
        assertEquals(2, fixture.owner.searchDocuments("", ConversationSearchCategory.FILE).size)
        val contentHit = fixture.owner.searchDocuments("现金流", ConversationSearchCategory.ALL).single()
        assertEquals(GlmOcrSearchDocumentKind.MARKDOWN, contentHit.kind)
        assertTrue(contentHit.matchSnippet.orEmpty().contains("现金流"))
        assertTrue(fixture.owner.searchDocuments("不存在", ConversationSearchCategory.ALL).isEmpty())
    }

    @Test
    fun `deleting a completed task removes its lineage and both unshared files`() {
        val fixture = fixture(GlmOcrTransportResult.Completed("# result", "req-delete", 1, 10, 10))
        val imported = assertType<GlmOcrImportResult.Imported>(fixture.owner.import(
            ConversationAttachmentSelection(ByteArrayInputStream("%PDF-1.7 delete".toByteArray()), "application/pdf", "delete.pdf"),
        )).task
        fixture.owner.queue(imported.id)
        assertType<GlmOcrProcessResult.Completed>(fixture.owner.process(imported.id))

        val deleted = assertType<GlmOcrDeleteResult.Deleted>(fixture.owner.delete(imported.id))

        assertEquals(2, deleted.removedAttachmentCount)
        assertEquals(0, deleted.retainedSharedAttachmentCount)
        assertTrue(fixture.owner.searchDocuments("", ConversationSearchCategory.ALL).isEmpty())
        assertTrue(fixture.assets.values().isEmpty())
        assertEquals(2, fixture.store.deletedCopies)
    }

    @Test
    fun `oversized source is rejected and its private copy is removed`() {
        val fixture = fixture(GlmOcrTransportResult.Completed("unused", null, null, null, null))

        val result = fixture.owner.import(
            ConversationAttachmentSelection(
                ByteArrayInputStream(ByteArray(GLM_OCR_IMAGE_MAX_BYTES.toInt() + 1)),
                "image/png",
                "too-large.png",
            ),
        )

        assertType<GlmOcrImportResult.Rejected>(result)
        assertEquals(1, fixture.store.deletedCopies)
        assertTrue(fixture.assets.values().isEmpty())
    }

    @Test
    fun `official transport streams base64 into fixed layout endpoint and decodes markdown only`() {
        var captured: ProviderChatRequest? = null
        val fake = object : ProviderChatTransport {
            override fun execute(request: ProviderChatRequest, credential: CharArray): ProviderChatOutcome {
                captured = request
                return ProviderChatOutcome.HttpResponse(
                    200,
                    """{"id":"ocr-7","md_results":"# OCR","data_info":{"num_pages":1},"usage":{"prompt_tokens":12,"completion_tokens":8},"layout_details":[{"ignored":"raw"}]}""",
                )
            }
        }
        val result = assertType<GlmOcrTransportResult.Completed>(OfficialGlmOcrTransport(fake).execute(
            GlmOcrTransportRequest(AttachmentOpenResult.Opened(3) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, "image/png"),
            "not-a-real-key".toCharArray(),
        ))
        assertEquals("# OCR", result.markdown)
        assertEquals(1, result.pageCount)
        assertNotNull(captured)
        val request = requireNotNull(captured)
        assertEquals("https://open.bigmodel.cn/api/paas/v4/layout_parsing", request.endpoint)
        assertEquals(com.nanzhufeng.ai.ai.ProviderResponseByteBudget.DOCUMENT_MAX_BYTES, request.maxResponseBytes)
        val body = ByteArrayOutputStream().also { request.effectiveBody().writeTo(it) }.toString(Charsets.UTF_8.name())
        assertTrue(body.startsWith("{\"model\":\"glm-ocr\",\"file\":\"data:image/png;base64,"))
        assertTrue(body.endsWith("AQID\"}"))
        assertFalse(body.contains("layout_details"))
    }

    private fun fixture(outcome: GlmOcrTransportResult): Fixture {
        val tasks = InMemoryGlmOcrTasks()
        val store = InMemoryPrivateStore()
        val assets = InMemoryAssets()
        val credential = InMemoryCredentialStore()
        val invocations = InMemoryInvocationRepository()
        val settings = object : ModelServiceSettingsRepository {
            override fun load(providerId: ProviderId) = ProviderSettings(providerId, true, NanfengModelServiceCatalog.defaultPreset(providerId))
            override fun save(settings: ProviderSettings) = settings
        }
        var attached: AttachmentReference? = null
        val owner = GlmOcrTaskOwner(
            tasks,
            store,
            assets,
            LoadModelServiceConfigurationUseCase(settings, credential),
            credential,
            GlmOcrTransport { _, _ -> outcome },
            GlmOcrConversationDraftCreator { markdown -> attached = markdown; GlmOcrContinueResult.Created(ConversationId("conversation-with-markdown")) },
            Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC),
            invocations,
        )
        return Fixture(owner, assets, store, invocations) { attached }
    }

    private inline fun <reified T> assertType(value: Any?): T {
        assertTrue("Expected ${T::class.java.simpleName}, got ${value?.javaClass?.simpleName}", value is T)
        return value as T
    }

    private data class Fixture(
        val owner: GlmOcrTaskOwner,
        val assets: InMemoryAssets,
        val store: InMemoryPrivateStore,
        val invocations: InMemoryInvocationRepository,
        private val attachment: () -> AttachmentReference?,
    ) { val attachedResult get() = attachment() }

    private class InMemoryGlmOcrTasks : GlmOcrTaskRepository {
        private val values = linkedMapOf<String, GlmOcrTask>()
        override fun save(task: GlmOcrTask): GlmOcrTask = task.also { values[it.id.value] = it }
        override fun find(id: GlmOcrTaskId) = values[id.value]
        override fun listNewestFirst() = values.values.sortedByDescending { it.createdAt }
        override fun delete(id: GlmOcrTaskId): Boolean = values.remove(id.value) != null
    }

    private class InMemoryAssets : PrivateAttachmentRepository {
        private val values = linkedMapOf<String, AttachmentReference>()
        override fun save(asset: AttachmentReference): AttachmentReference {
            val existing = asset.sha256?.let(::findBySha256)
            if (existing != null) return existing
            values[asset.id.value] = asset
            return asset
        }
        override fun findById(id: AttachmentId) = values[id.value]
        override fun findBySha256(sha256: String) = values.values.firstOrNull { it.sha256 == sha256 }
        override fun deleteIfUnreferenced(id: AttachmentId, deletePrivateCopy: (AttachmentReference) -> Boolean): PrivateAttachmentCleanupResult {
            val attachment = values[id.value] ?: return PrivateAttachmentCleanupResult.Missing
            if (!deletePrivateCopy(attachment)) return PrivateAttachmentCleanupResult.DeleteFailed
            values.remove(id.value)
            return PrivateAttachmentCleanupResult.Deleted
        }
        fun values(): List<AttachmentReference> = values.values.toList()
    }

    private class InMemoryPrivateStore : PrivateAttachmentStore {
        private val bytes = linkedMapOf<String, ByteArray>()
        var deletedCopies = 0
            private set
        override fun import(request: AttachmentImportRequest): AttachmentImportResult {
            val content = request.input.use { it.readBytes() }
            val hash = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
            val id = AttachmentId.new()
            bytes[id.value] = content
            return AttachmentImportResult.Imported(AttachmentReference("attachments/v1/$hash", request.mimeType, request.displayName, id, content.size.toLong(), hash))
        }
        override fun read(attachment: AttachmentReference): AttachmentReadResult = bytes[attachment.id.value]?.let(AttachmentReadResult::Content)
            ?: AttachmentReadResult.Rejected(AiTaskError.AttachmentNotReady)
        override fun openVerified(attachment: AttachmentReference): AttachmentOpenResult = bytes[attachment.id.value]?.let { content ->
            AttachmentOpenResult.Opened(content.size.toLong()) { ByteArrayInputStream(content) }
        } ?: AttachmentOpenResult.Rejected(AiTaskError.AttachmentNotReady)
        override fun thumbnail(attachment: AttachmentReference) = AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        override fun deletePrivateCopy(attachment: AttachmentReference): Boolean =
            (bytes.remove(attachment.id.value) != null).also { if (it) deletedCopies += 1 }
    }

    private class InMemoryCredentialStore : ProviderCredentialStore {
        override fun hasCredential(providerId: ProviderId) = providerId == ProviderId.ZHIPU
        override fun saveCredential(providerId: ProviderId, credential: CharArray) = true
        override fun loadCredential(providerId: ProviderId) = if (providerId == ProviderId.ZHIPU) "not-a-real-key".toCharArray() else null
    }

    private class InMemoryInvocationRepository : InvocationRepository {
        private val values = linkedMapOf<String, InvocationRecord>()
        override fun save(record: InvocationRecord): InvocationRecord = record.also { values[it.id.value] = it }
        override fun findById(id: InvocationId): InvocationRecord? = values[id.value]
        override fun listNewestFirst(): List<InvocationRecord> = values.values.reversed()
    }
}
