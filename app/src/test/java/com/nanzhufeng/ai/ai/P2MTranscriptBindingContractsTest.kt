package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AiTaskId
import com.nanzhufeng.ai.domain.Conversation
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationRepository
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.ConversationTranscriptPresentation
import com.nanzhufeng.ai.domain.GenerationId
import com.nanzhufeng.ai.domain.GenerationRecord
import com.nanzhufeng.ai.domain.GenerationStatus
import com.nanzhufeng.ai.domain.GenerationValidation
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.InvocationRecord
import com.nanzhufeng.ai.domain.InvocationStatus
import com.nanzhufeng.ai.domain.MessagePresentationRenderer
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.ModelRegistrySnapshotId
import com.nanzhufeng.ai.domain.ProviderAttempt
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.ProviderAttemptStatus
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.TaskRun
import com.nanzhufeng.ai.domain.TaskRunId
import com.nanzhufeng.ai.domain.TaskRunStatus
import com.nanzhufeng.ai.domain.ValidationId
import com.nanzhufeng.ai.domain.ValidationStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2MTranscriptBindingContractsTest {
    @Test fun `successful P2M task run binds one assistant owner and projects its persisted duration`() {
        val repository = MemoryConversations()
        val invocation = successfulInvocation()
        val owner = P2MTranscriptOwner(repository)

        val bound = owner.bindSuccessfulInvocation(invocation) as P2MTranscriptBindingResult.Bound
        val snapshot = repository.findById(bound.conversationId)!!
        val assistant = snapshot.nodes.single { it.role == MessageRole.ASSISTANT }
        val presented = ConversationTranscriptPresentation(MessagePresentationRenderer())
            .render(snapshot.nodes, emptyList(), mapOf(invocation.id to invocation))
            .single { it.message.messageId == assistant.id }

        assertEquals(invocation.id, assistant.invocation?.invocationId)
        assertEquals("用时 2 秒", presented.metadata.workDurationLabel)
        assertEquals(2, snapshot.nodes.size)
        assertTrue(!bound.replayed)
    }

    @Test fun `binding is deterministic across restart reads and never duplicates its assistant`() {
        val repository = MemoryConversations()
        val owner = P2MTranscriptOwner(repository)
        val first = owner.bindSuccessfulInvocation(successfulInvocation()) as P2MTranscriptBindingResult.Bound
        val replay = owner.bindSuccessfulInvocation(successfulInvocation()) as P2MTranscriptBindingResult.Bound

        assertEquals(first.conversationId, replay.conversationId)
        assertEquals(first.assistantMessageId, replay.assistantMessageId)
        assertTrue(replay.replayed)
        assertEquals(1, repository.listActive().size)
        assertEquals(1, repository.findById(first.conversationId)!!.nodes.count { it.role == MessageRole.ASSISTANT })
    }

    @Test fun `failed cancelled and zero elapsed records never invent a duration`() {
        val repository = MemoryConversations()
        val owner = P2MTranscriptOwner(repository)

        assertTrue(owner.bindSuccessfulInvocation(blockedInvocation()) is P2MTranscriptBindingResult.Ignored)
        assertTrue(owner.bindSuccessfulInvocation(cancelledInvocation()) is P2MTranscriptBindingResult.Ignored)
        assertEquals(0, repository.listActive().size)

        val zero = successfulInvocation(started = Instant.parse("2026-08-14T00:00:02Z"), completed = Instant.parse("2026-08-14T00:00:02Z"))
        val bound = owner.bindSuccessfulInvocation(zero) as P2MTranscriptBindingResult.Bound
        val assistant = repository.findById(bound.conversationId)!!.nodes.single { it.role == MessageRole.ASSISTANT }
        val projected = ConversationTranscriptPresentation(MessagePresentationRenderer())
            .render(repository.findById(bound.conversationId)!!.nodes, emptyList(), mapOf(zero.id to zero))
            .single { it.message.messageId == assistant.id }
        assertNull(projected.metadata.workDurationLabel)
    }

    private fun successfulInvocation(
        started: Instant = Instant.parse("2026-08-14T00:00:00Z"),
        completed: Instant = Instant.parse("2026-08-14T00:00:02Z"),
    ): InvocationRecord {
        val id = InvocationId("p2m-transcript-success")
        val taskId = AiTaskId(P2MOpenRouterTextAcceptance.RUN_ID)
        val attempt = ProviderAttempt(
            id = ProviderAttemptId("p2m-transcript-attempt"), providerId = ProviderId.OPENROUTER,
            modelId = "openrouter-model", registrySnapshotId = ModelRegistrySnapshotId("verified-snapshot"),
            startedAt = started, completedAt = completed, status = ProviderAttemptStatus.SUCCEEDED,
            generation = GenerationRecord(
                GenerationId("p2m-transcript-generation"), completed, GenerationStatus.COMPLETED,
                GenerationValidation(ValidationId("p2m-transcript-validation"), completed, ValidationStatus.PASSED, 1),
            ),
        )
        return InvocationRecord(
            id = id, taskId = taskId, providerId = ProviderId.OPENROUTER, modelId = "openrouter-model",
            harnessVersion = 1, completedAt = completed, status = InvocationStatus.SUCCEEDED,
            registrySnapshotId = ModelRegistrySnapshotId("verified-snapshot"),
            taskRun = TaskRun(TaskRunId("p2m-transcript-run"), taskId, started, completed, TaskRunStatus.SUCCEEDED, listOf(attempt)),
        )
    }

    private fun blockedInvocation(): InvocationRecord = InvocationRecord(
        id = InvocationId("p2m-transcript-blocked"), taskId = AiTaskId(P2MOpenRouterTextAcceptance.RUN_ID),
        providerId = ProviderId.OPENROUTER, modelId = "openrouter-model", harnessVersion = 1,
        completedAt = Instant.EPOCH, status = InvocationStatus.BLOCKED, error = AiTaskError.ProviderCredentialInvalid,
    )

    private fun cancelledInvocation(): InvocationRecord {
        val started = Instant.EPOCH
        val completed = Instant.EPOCH.plusSeconds(1)
        val id = InvocationId("p2m-transcript-cancelled")
        val taskId = AiTaskId(P2MOpenRouterTextAcceptance.RUN_ID)
        val attempt = ProviderAttempt(
            id = ProviderAttemptId("p2m-transcript-cancelled-attempt"), providerId = ProviderId.OPENROUTER,
            modelId = "openrouter-model", registrySnapshotId = null, startedAt = started, completedAt = completed,
            status = ProviderAttemptStatus.CANCELLED, error = AiTaskError.ProviderRequestCancelled,
        )
        return InvocationRecord(
            id = id, taskId = taskId, providerId = ProviderId.OPENROUTER, modelId = "openrouter-model",
            harnessVersion = 1, completedAt = completed, status = InvocationStatus.CANCELLED,
            error = AiTaskError.ProviderRequestCancelled,
            taskRun = TaskRun(TaskRunId("p2m-transcript-cancelled-run"), taskId, started, completed, TaskRunStatus.CANCELLED, listOf(attempt)),
        )
    }

    private class MemoryConversations : ConversationRepository {
        private val snapshots = linkedMapOf<ConversationId, ConversationSnapshot>()
        override fun save(snapshot: ConversationSnapshot): ConversationSnapshot = snapshot.also { snapshots[it.conversation.id] = it }
        override fun findById(id: ConversationId): ConversationSnapshot? = snapshots[id]
        override fun listActive(): List<Conversation> = snapshots.values.map(ConversationSnapshot::conversation)
    }
}
