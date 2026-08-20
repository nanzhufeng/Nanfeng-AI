package com.nanzhufeng.ai.domain

import com.nanzhufeng.ai.ai.MockAiTaskRunner
import com.nanzhufeng.ai.data.InMemoryKnowledgeRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P1ContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC)
    private val factory = CaptureDraftFactory(clock)
    private val model = ModelDescriptor("mock-balanced", "Mock 均衡", ModelCapabilities(true, true, false))
    private val harness = HarnessProfile("capture-organize", 1)

    @Test
    fun `manual share and image inputs normalize into source-aware drafts`() {
        val manual = factory.fromManualText("  手工文字  ")
        val share = factory.fromAndroidTextShare("分享文字", "com.example.source")
        val image = factory.fromImage(AttachmentReference("pending://photo", "image/jpeg", "photo.jpg"), "gallery")

        assertEquals("手工文字", manual.text)
        assertEquals(CaptureSourceType.MANUAL_TEXT, manual.sourceEvidence.single().sourceType)
        assertEquals(CaptureSourceType.ANDROID_TEXT_SHARE, share.sourceEvidence.single().sourceType)
        assertEquals("com.example.source", share.sourceEvidence.single().sourceReference)
        assertEquals(CaptureSourceType.IMAGE, image.sourceEvidence.single().sourceType)
        assertEquals("image/jpeg", image.attachments.single().mimeType)
    }

    @Test
    fun `unconfirmed task is blocked before mock provider runs`() {
        val runner = MockAiTaskRunner(clock)
        val draft = factory.fromManualText("待确认")
        val task = taskFor(draft, consent = null)

        val result = RunAiTaskUseCase(runner, TestInvocationRepository(), clock).execute(task, draft)

        assertTrue(result is AiTaskRunResult.Failure)
        assertEquals(AiTaskError.ConsentRequired, (result as AiTaskRunResult.Failure).error)
        assertEquals(InvocationStatus.BLOCKED, result.invocation.status)
        assertEquals(0, runner.invocationCount)
    }

    @Test
    fun `changing content invalidates existing consent`() {
        val runner = MockAiTaskRunner(clock)
        val original = factory.fromManualText("原始内容")
        val changed = original.copy(text = "修改后的内容")
        val task = taskFor(original, consentFor(original))

        val result = RunAiTaskUseCase(runner, TestInvocationRepository(), clock).execute(task, changed)

        assertEquals(AiTaskError.ConsentRequired, (result as AiTaskRunResult.Failure).error)
        assertEquals(0, runner.invocationCount)
    }

    @Test
    fun `unconfirmed candidate cannot create knowledge item`() {
        val draft = factory.fromManualText("候选内容")
        val runner = MockAiTaskRunner(clock)
        val task = taskFor(draft, consentFor(draft))
        val result = RunAiTaskUseCase(runner, TestInvocationRepository(), clock).execute(task, draft) as AiTaskRunResult.Success

        val saved = SaveKnowledgeItemUseCase(InMemoryKnowledgeRepository(), clock)
            .execute(result.candidate, result.invocation, task, draft, userConfirmed = false)

        assertEquals(SaveKnowledgeResult.Rejected(AiTaskError.CandidateNotConfirmed), saved)
    }

    @Test
    fun `confirmed candidate persists and reads with evidence and provenance`() {
        val draft = factory.fromAndroidTextShare("需要保存", "com.example.share")
        val task = taskFor(draft, consentFor(draft))
        val result = RunAiTaskUseCase(MockAiTaskRunner(clock), TestInvocationRepository(), clock).execute(task, draft) as AiTaskRunResult.Success
        val repository = InMemoryKnowledgeRepository()

        val saved = (SaveKnowledgeItemUseCase(repository, clock)
            .execute(result.candidate, result.invocation, task, draft, userConfirmed = true)
            as SaveKnowledgeResult.Saved).item

        val restored = repository.findById(saved.id)
        assertNotNull(restored)
        assertEquals(CaptureSourceType.ANDROID_TEXT_SHARE, restored!!.sourceEvidence.single().sourceType)
        assertEquals(result.invocation.id, restored.provenance.invocationId)
        assertEquals(task.model.id, restored.provenance.modelId)
    }

    @Test
    fun `candidate from another task is rejected as provenance mismatch`() {
        val draft = factory.fromManualText("来源必须匹配")
        val task = taskFor(draft, consentFor(draft))
        val result = RunAiTaskUseCase(MockAiTaskRunner(clock), TestInvocationRepository(), clock).execute(task, draft) as AiTaskRunResult.Success
        val otherTask = task.copy(id = AiTaskId.new())

        val saved = SaveKnowledgeItemUseCase(InMemoryKnowledgeRepository(), clock)
            .execute(result.candidate, result.invocation, otherTask, draft, userConfirmed = true)

        assertEquals(SaveKnowledgeResult.Rejected(AiTaskError.ProvenanceMismatch), saved)
    }

    private fun taskFor(draft: CaptureDraft, consent: EgressConsent?): AiTask = AiTask(
        id = AiTaskId.new(), draftId = draft.id, providerId = ProviderId.MOCK, model = model,
        harness = harness, consent = consent, createdAt = clock.instant(),
    )

    private fun consentFor(draft: CaptureDraft): EgressConsent = EgressConsent(
        approvedAt = clock.instant(), providerId = ProviderId.MOCK, modelId = model.id, contentFingerprint = draft.egressFingerprint(),
    )
}

private class TestInvocationRepository : InvocationRepository {
    private val records = linkedMapOf<InvocationId, InvocationRecord>()

    override fun save(record: InvocationRecord): InvocationRecord = records.getOrPut(record.id) { record }
    override fun findById(id: InvocationId): InvocationRecord? = records[id]
    override fun listNewestFirst(): List<InvocationRecord> = records.values.reversed()
}
