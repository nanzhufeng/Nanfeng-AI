package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.*
import java.time.Instant
import java.util.concurrent.Callable

class RoomOfflineEvalRepository(private val database: NanfengAiDatabase) : EvalRunRepository {
    override fun append(run: EvalRun): EvalRun = database.runInTransaction(Callable {
        database.offlineEvalDao().insertRun(OfflineEvalRunEntity(run.id.value, run.datasetId, run.datasetVersion, run.domainVersion, run.appVersion, run.schemaVersion, run.fixtureManifestHash, run.assertionVersion, run.rubricVersion, run.securitySummary, run.startedAt.toEpochMilli(), run.completedAt.toEpochMilli()))
        database.offlineEvalDao().insertResults(run.results.map { result -> OfflineEvalCaseResultEntity(run.id.value, result.caseId.value, result.verdict.name) })
        database.offlineEvalDao().insertAssertions(run.results.flatMap { result -> result.assertions.map { assertion -> OfflineEvalAssertionEntity(run.id.value, assertion.id, result.caseId.value, assertion.fact.name, assertion.verdict.name, assertion.detail) } })
        run
    })
    override fun list(): List<EvalRun> = database.offlineEvalDao().runs().map { run ->
        val results = database.offlineEvalDao().results(run.id).map { result -> CaseResult(EvalCaseId(result.caseId), EvalVerdict.valueOf(result.verdict), database.offlineEvalDao().assertions(run.id, result.caseId).map { assertion -> DeterministicAssertion(assertion.assertionId, EvalCaseId(assertion.caseId), EvalFact.valueOf(assertion.fact), EvalVerdict.valueOf(assertion.verdict), assertion.detail) }) }
        EvalRun(EvalRunId(run.id), run.datasetId, run.datasetVersion, run.domainVersion, run.appVersion, run.schemaVersion, run.fixtureManifestHash, run.assertionVersion, run.rubricVersion, run.securitySummary, Instant.ofEpochMilli(run.startedAtEpochMs), Instant.ofEpochMilli(run.completedAtEpochMs), results)
    }
    override fun appendScore(score: HumanScore): HumanScore { database.offlineEvalDao().insertScore(OfflineEvalHumanScoreEntity(0, score.runId.value, score.caseId.value, score.dimension.name, score.score, score.reviewerAlias, score.rubricVersion, score.notedAt.toEpochMilli(), score.note)); return score }
    override fun scores(runId: EvalRunId): List<HumanScore> = database.offlineEvalDao().scores(runId.value).map { HumanScore(EvalRunId(it.runId), EvalCaseId(it.caseId), HumanScoreDimension.valueOf(it.dimension), it.score, it.reviewerAlias, it.rubricVersion, Instant.ofEpochMilli(it.notedAtEpochMs), it.note) }
}
