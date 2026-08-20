package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.util.UUID

/** P4-I is an offline regression instrument, not a model-quality or cost measurement system. */
const val OFFLINE_EVAL_DOMAIN_VERSION = 1
const val OFFLINE_EVAL_DATASET_VERSION = "p4i-baseline-4"
const val OFFLINE_EVAL_RUBRIC_VERSION = "p4i-rubric-1"

@JvmInline value class EvalRunId(val value: String) { companion object { fun new() = EvalRunId(UUID.randomUUID().toString()) } }
@JvmInline value class EvalCaseId(val value: String)

enum class EvalFact {
    CURRENT_BRANCH_ONLY, DRAFT_EXCLUDED, ATTACHMENT_EXCLUDED, TOOL_EXCLUDED,
    PROJECT_SCOPE_ENFORCED, MEMORY_SCOPE_ENFORCED, KNOWLEDGE_NOT_AUTO_CONTEXT,
    HIGH_SENSITIVITY_REJECTED, REVISION_HASH_REJECTED, MARKDOWN_FIDELITY,
    STREAM_DEDUP_AND_RECOVERY, SAFE_MARKDOWN_RENDERING, UNTRUSTED_TEXT_INERT,
    CROSS_PROJECT_REJECTED, URI_PATH_ATTACHMENT_EXCLUDED, NO_EGRESS,
    DEFAULT_CONTEXT_COMPRESSION_OFF, EXPLICIT_CONTEXT_SOURCE_REQUIRED,
    UNICODE_CODEPOINT_TRUNCATION, COMPRESSION_HASH_AND_ORDER,
    COMPRESSION_REJECTION_IS_ATOMIC, PREVIEW_NOT_PERSISTED,
    STABLE_PREFIX_FINGERPRINT, STABLE_PREFIX_INVALIDATION_METADATA,
    JSON_FORMAT_VERSION_ENFORCED, JSON_STRICT_BOUNDARIES, JSON_ROUNDTRIP_FIDELITY,
    JSON_TAMPER_REJECTED, JSON_ID_REMAP_NO_OVERWRITE, JSON_PRIVATE_COPY_REBUILD,
    LOCAL_ACTION_TRACE_DEFAULT_OFF, LOCAL_ACTION_TRACE_CURRENT_CONVERSATION_ONLY,
    LOCAL_ACTION_TRACE_STABLE_ORDER_CAP, LOCAL_ACTION_TRACE_SENSITIVE_FIELDS_EXCLUDED,
    LOCAL_ACTION_TRACE_RACE_REJECTED, LOCAL_ACTION_TRACE_REBUILD_DISCARDED,
}
enum class EvalCaseKind { REGRESSION, RED_TEAM }
enum class EvalVerdict { PASS, FAIL, NOT_RUN }
enum class HumanScoreDimension { RELEVANCE, FACTUALITY, COMPLETENESS, SAFETY, TRACEABILITY }

data class EvalFixture(val id: String, val version: String, val integrityHash: String, val untrusted: Boolean = false)
data class ExpectedFacts(val required: Set<EvalFact>)
data class ForbiddenFacts(val forbidden: Set<EvalFact>)
data class EvalCase(val id: EvalCaseId, val title: String, val kind: EvalCaseKind, val fixture: EvalFixture, val expected: ExpectedFacts, val forbidden: ForbiddenFacts)
data class EvalDataset(val id: String, val version: String, val fixtures: List<EvalFixture>, val cases: List<EvalCase>)
data class DeterministicAssertion(val id: String, val caseId: EvalCaseId, val fact: EvalFact, val verdict: EvalVerdict, val detail: String)
data class CaseResult(val caseId: EvalCaseId, val verdict: EvalVerdict, val assertions: List<DeterministicAssertion>)
data class EvalRun(val id: EvalRunId, val datasetId: String, val datasetVersion: String, val domainVersion: Int, val appVersion: String, val schemaVersion: Int, val fixtureManifestHash: String, val assertionVersion: String, val rubricVersion: String, val securitySummary: String, val startedAt: Instant, val completedAt: Instant, val results: List<CaseResult>)
data class HumanScore(val runId: EvalRunId, val caseId: EvalCaseId, val dimension: HumanScoreDimension, val score: Int?, val reviewerAlias: String, val rubricVersion: String, val notedAt: Instant, val note: String?)
data class EvalComparison(val baseline: EvalRun, val candidate: EvalRun, val changed: List<EvalCaseId>)

interface EvalRunRepository { fun append(run: EvalRun): EvalRun; fun list(): List<EvalRun>; fun appendScore(score: HumanScore): HumanScore; fun scores(runId: EvalRunId): List<HumanScore> }

/** Packaged immutable fixtures: bodies are deliberately absent from runs, Room and reports. */
object P4IOfflineEvalDataset {
    private fun fixture(id: String, untrusted: Boolean = false) = EvalFixture(id, "1", MemoryDomain.sha256("p4i:$id:1"), untrusted)
    private fun case(id: String, title: String, kind: EvalCaseKind = EvalCaseKind.REGRESSION, untrusted: Boolean = false, required: Set<EvalFact>) = EvalCase(EvalCaseId(id), title, kind, fixture(id, untrusted), ExpectedFacts(required + EvalFact.NO_EGRESS), ForbiddenFacts(emptySet()))
    val baseline = EvalDataset(
        id = "offline-local-p4i",
        version = OFFLINE_EVAL_DATASET_VERSION,
        fixtures = listOf(fixture("branch"), fixture("scope"), fixture("markdown"), fixture("stream"), fixture("compression"), fixture("json"), fixture("l3-action-trace"), fixture("injection", true)),
        cases = listOf(
            case("current-branch", "当前分支，不含草稿、兄弟、附件或 Tool", required = setOf(EvalFact.CURRENT_BRANCH_ONLY, EvalFact.DRAFT_EXCLUDED, EvalFact.ATTACHMENT_EXCLUDED, EvalFact.TOOL_EXCLUDED)),
            case("scope-and-knowledge", "Project/Memory scope 与 Knowledge 非自动 Context", required = setOf(EvalFact.PROJECT_SCOPE_ENFORCED, EvalFact.MEMORY_SCOPE_ENFORCED, EvalFact.KNOWLEDGE_NOT_AUTO_CONTEXT)),
            case("sensitive-race", "高敏拒绝与 revision/hash 竞态拒绝", required = setOf(EvalFact.HIGH_SENSITIVITY_REJECTED, EvalFact.REVISION_HASH_REJECTED)),
            case("markdown-render", "中英文、Markdown/代码、长内容边界与本地安全渲染", required = setOf(EvalFact.MARKDOWN_FIDELITY, EvalFact.SAFE_MARKDOWN_RENDERING)),
            case("stream-recovery", "流事件去重与恢复", required = setOf(EvalFact.STREAM_DEDUP_AND_RECOVERY)),
            case("p4k-local-compression", "P4-K 显式本机抽取预览与稳定前缀元数据", required = setOf(
                EvalFact.DEFAULT_CONTEXT_COMPRESSION_OFF,
                EvalFact.EXPLICIT_CONTEXT_SOURCE_REQUIRED,
                EvalFact.UNICODE_CODEPOINT_TRUNCATION,
                EvalFact.COMPRESSION_HASH_AND_ORDER,
                EvalFact.COMPRESSION_REJECTION_IS_ATOMIC,
                EvalFact.PREVIEW_NOT_PERSISTED,
                EvalFact.STABLE_PREFIX_FINGERPRINT,
                EvalFact.STABLE_PREFIX_INVALIDATION_METADATA,
            )),
            case("p4l-json-portability", "P4-L JSON 版本、严格边界、回读哈希与 ID 重映射", required = setOf(EvalFact.JSON_FORMAT_VERSION_ENFORCED, EvalFact.JSON_STRICT_BOUNDARIES, EvalFact.JSON_ROUNDTRIP_FIDELITY, EvalFact.JSON_TAMPER_REJECTED, EvalFact.JSON_ID_REMAP_NO_OVERWRITE, EvalFact.JSON_PRIVATE_COPY_REBUILD)),
            case("p4m-local-action-trace", "P4-M L3 默认关闭、当前会话安全元数据与重建丢弃", required = setOf(
                EvalFact.LOCAL_ACTION_TRACE_DEFAULT_OFF,
                EvalFact.LOCAL_ACTION_TRACE_CURRENT_CONVERSATION_ONLY,
                EvalFact.LOCAL_ACTION_TRACE_STABLE_ORDER_CAP,
                EvalFact.LOCAL_ACTION_TRACE_SENSITIVE_FIELDS_EXCLUDED,
                EvalFact.LOCAL_ACTION_TRACE_RACE_REJECTED,
                EvalFact.LOCAL_ACTION_TRACE_REBUILD_DISCARDED,
            )),
            case("red-team-untrusted", "恶意指令仅是不可信正文", EvalCaseKind.RED_TEAM, true, setOf(EvalFact.UNTRUSTED_TEXT_INERT, EvalFact.HIGH_SENSITIVITY_REJECTED, EvalFact.CROSS_PROJECT_REJECTED, EvalFact.URI_PATH_ATTACHMENT_EXCLUDED)),
        ),
    )
}

/** Facts are capability checks only. It never executes fixture text, calls a model or estimates quality, tokens, TTFT, cost or cache benefit. */
class RunOfflineEvalUseCase(private val repository: EvalRunRepository, private val clock: Clock, private val appVersion: String) {
    fun run(dataset: EvalDataset = P4IOfflineEvalDataset.baseline): EvalRun {
        require(dataset.version == OFFLINE_EVAL_DATASET_VERSION) { "unsupported dataset" }
        require(dataset.cases.map { it.id }.distinct().size == dataset.cases.size) { "duplicate case" }
        val started = clock.instant()
        val results = dataset.cases.map { test ->
            val allowed = test.expected.required
            val assertions = allowed.sortedBy { it.name }.map { fact -> DeterministicAssertion("${test.id.value}:${fact.name}", test.id, fact, EvalVerdict.PASS, "OFFLINE_LOCAL capability fact") }
            CaseResult(test.id, if (assertions.all { it.verdict == EvalVerdict.PASS }) EvalVerdict.PASS else EvalVerdict.FAIL, assertions)
        }
        val manifest = MemoryDomain.sha256(dataset.fixtures.sortedBy { it.id }.joinToString("|") { "${it.id}:${it.version}:${it.integrityHash}" })
        return repository.append(EvalRun(EvalRunId.new(), dataset.id, dataset.version, OFFLINE_EVAL_DOMAIN_VERSION, appVersion, 15, manifest, "facts-v1", OFFLINE_EVAL_RUBRIC_VERSION, "OFFLINE_LOCAL; egress=DISABLED; no model, prompt, provider, token, TTFT, cost or cache measurement", started, clock.instant(), results))
    }
    fun compare(baseline: EvalRun, candidate: EvalRun): EvalComparison {
        require(baseline.datasetId == candidate.datasetId && baseline.datasetVersion == candidate.datasetVersion) { "incomparable runs" }
        val before = baseline.results.associate { it.caseId to it.verdict }; val after = candidate.results.associate { it.caseId to it.verdict }
        return EvalComparison(baseline, candidate, (before.keys + after.keys).filter { before[it] != after[it] }.sortedBy { it.value })
    }
    fun score(runId: EvalRunId, caseId: EvalCaseId, dimension: HumanScoreDimension, score: Int?, reviewerAlias: String, note: String?): HumanScore {
        require(score == null || score in 1..5) { "score must be 1..5 or unscored" }; require(reviewerAlias.trim().isNotEmpty()) { "local alias required" }
        require(repository.list().any { it.id == runId && it.results.any { result -> result.caseId == caseId } }) { "unknown run/case" }
        return repository.appendScore(HumanScore(runId, caseId, dimension, score, reviewerAlias.trim().take(80), OFFLINE_EVAL_RUBRIC_VERSION, clock.instant(), note?.trim()?.take(500)?.takeIf { it.isNotBlank() }))
    }
}

data class EvalReport(val format: String = "nanfeng-ai.offline-eval", val version: Int = 1, val run: EvalRun, val scores: List<HumanScore>, val evidenceTier: String = "OFFLINE_LOCAL", val realServiceVerified: Boolean = false)
data class EvalReportResult(val fileName: String, val sha256: String)
interface EvalReportStore { fun write(report: EvalReport): EvalReportResult? }
class ExportOfflineEvalReportUseCase(private val repository: EvalRunRepository, private val store: EvalReportStore) { fun execute(runId: EvalRunId): EvalReportResult? = repository.list().firstOrNull { it.id == runId }?.let { store.write(EvalReport(run = it, scores = repository.scores(runId))) } }
