package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AiTask
import com.nanzhufeng.ai.domain.AiTaskRunResult
import com.nanzhufeng.ai.domain.AiTaskRunner
import com.nanzhufeng.ai.domain.CandidateId
import com.nanzhufeng.ai.domain.CaptureDraft
import com.nanzhufeng.ai.domain.GeneratedCandidate
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.InvocationRecord
import com.nanzhufeng.ai.domain.InvocationStatus
import com.nanzhufeng.ai.domain.ProviderCost
import java.time.Clock

/** Local-only provider substitute. It never opens a network connection or reads credentials. */
class MockAiTaskRunner(private val clock: Clock) : AiTaskRunner {
    var invocationCount: Int = 0
        private set

    override fun run(task: AiTask, draft: CaptureDraft): AiTaskRunResult.Success {
        invocationCount += 1
        val now = clock.instant()
        val seed = draft.text ?: draft.attachments.firstOrNull()?.displayName ?: "图片"
        return AiTaskRunResult.Success(
            candidate = GeneratedCandidate(
                id = CandidateId.new(), taskId = task.id, title = "Mock 整理结果", body = "已整理：$seed", generatedAt = now,
            ),
            invocation = InvocationRecord(
                id = InvocationId.new(), taskId = task.id, providerId = task.providerId, modelId = task.model.id,
                harnessVersion = task.harness.version, completedAt = now, status = InvocationStatus.SUCCEEDED,
                cost = ProviderCost(priceVersion = "mock-local-v1", currencyCode = "CNY", totalMicros = 0),
            ),
        )
    }
}
