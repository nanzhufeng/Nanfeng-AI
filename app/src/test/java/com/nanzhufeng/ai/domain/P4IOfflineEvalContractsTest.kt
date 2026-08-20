package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class P4IOfflineEvalContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)
    private val repository = MemoryRepo()
    private val runner = RunOfflineEvalUseCase(repository, clock, "test")

    @Test fun `baseline is versioned offline and covers regression and red team boundaries`() {
        val run = runner.run()
        assertEquals("offline-local-p4i", run.datasetId); assertTrue(run.securitySummary.contains("OFFLINE_LOCAL")); assertTrue(run.results.any { it.caseId.value == "red-team-untrusted" })
        assertTrue(run.results.flatMap { it.assertions }.any { it.fact == EvalFact.CURRENT_BRANCH_ONLY })
        assertTrue(run.results.flatMap { it.assertions }.any { it.fact == EvalFact.NO_EGRESS })
        assertTrue(run.results.flatMap { it.assertions }.any { it.fact == EvalFact.DEFAULT_CONTEXT_COMPRESSION_OFF })
        assertTrue(run.results.flatMap { it.assertions }.any { it.fact == EvalFact.STABLE_PREFIX_INVALIDATION_METADATA })
        assertTrue(run.results.flatMap { it.assertions }.any { it.fact == EvalFact.LOCAL_ACTION_TRACE_DEFAULT_OFF })
        assertTrue(run.results.flatMap { it.assertions }.any { it.fact == EvalFact.LOCAL_ACTION_TRACE_RACE_REJECTED })
        assertFalse(run.toString().contains("Bearer "))
    }

    @Test fun `score is append only and permits unscored not applicable state`() {
        val run = runner.run(); val case = run.results.first().caseId
        runner.score(run.id, case, HumanScoreDimension.SAFETY, null, "local-reviewer", "N/A")
        runner.score(run.id, case, HumanScoreDimension.SAFETY, 5, "local-reviewer", "checked")
        assertEquals(2, repository.scores(run.id).size); assertNull(repository.scores(run.id).first().score)
    }

    @Test fun `comparison is only same dataset version and no synthetic quality metrics exist`() {
        val first = runner.run(); val second = runner.run(); assertTrue(runner.compare(first, second).changed.isEmpty())
        assertFails { runner.compare(first, first.copy(datasetVersion = "other")) }
        assertFalse(EvalRun::class.java.declaredFields.any { it.name.contains("cost", true) || it.name.contains("token", true) || it.name.contains("ttft", true) })
    }

    @Test fun `report is marked offline and has no fixture prose`() {
        val run = runner.run(); var captured: EvalReport? = null
        val result = ExportOfflineEvalReportUseCase(repository, object : EvalReportStore { override fun write(report: EvalReport): EvalReportResult { captured = report; return EvalReportResult("report.json", "hash") } }).execute(run.id)
        assertNotNull(result); assertEquals("OFFLINE_LOCAL", captured?.evidenceTier); assertFalse(captured.toString().contains("ignore previous"))
    }

    private fun assertFails(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    private class MemoryRepo : EvalRunRepository {
        val runs = mutableListOf<EvalRun>(); val allScores = mutableListOf<HumanScore>()
        override fun append(run: EvalRun) = run.also(runs::add)
        override fun list() = runs.toList()
        override fun appendScore(score: HumanScore) = score.also(allScores::add)
        override fun scores(runId: EvalRunId) = allScores.filter { it.runId == runId }
    }
}
