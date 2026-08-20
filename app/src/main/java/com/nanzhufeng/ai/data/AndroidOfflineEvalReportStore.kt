package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.*
import com.nanzhufeng.ai.domain.MemoryDomain
import java.io.File
import java.io.FileOutputStream

class AndroidOfflineEvalReportStore(context: Context) : EvalReportStore {
    private val root = File(context.filesDir, "exports/offline-eval/v1")
    override fun write(report: EvalReport): EvalReportResult? = runCatching {
        if (!root.exists() && !root.mkdirs()) return null
        val name = "offline-eval-${report.run.id.value}.json"; val target = File(root, name); val part = File(root, ".${name}.part")
        fun q(value: String?) = "\"${(value ?: "").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
        val cases = report.run.results.joinToString(",") { result -> "{\"caseId\":${q(result.caseId.value)},\"verdict\":${q(result.verdict.name)},\"assertions\":[${result.assertions.joinToString(",") { assertion -> "{\"id\":${q(assertion.id)},\"fact\":${q(assertion.fact.name)},\"verdict\":${q(assertion.verdict.name)}}" }}]}" }
        val scores = report.scores.joinToString(",") { score -> "{\"caseId\":${q(score.caseId.value)},\"dimension\":${q(score.dimension.name)},\"score\":${score.score ?: "null"},\"reviewerAlias\":${q(score.reviewerAlias)},\"rubricVersion\":${q(score.rubricVersion)},\"notedAt\":${q(score.notedAt.toString())}}" }
        val json = "{\"format\":${q(report.format)},\"version\":${report.version},\"evidenceTier\":${q(report.evidenceTier)},\"realServiceVerified\":false,\"run\":{\"id\":${q(report.run.id.value)},\"datasetId\":${q(report.run.datasetId)},\"datasetVersion\":${q(report.run.datasetVersion)},\"domainVersion\":${report.run.domainVersion},\"appVersion\":${q(report.run.appVersion)},\"schemaVersion\":${report.run.schemaVersion},\"fixtureManifestHash\":${q(report.run.fixtureManifestHash)},\"assertionVersion\":${q(report.run.assertionVersion)},\"rubricVersion\":${q(report.run.rubricVersion)},\"securitySummary\":${q(report.run.securitySummary)},\"cases\":[${cases}]},\"humanScores\":[${scores}]}"
        val bytes = json.toByteArray(Charsets.UTF_8); FileOutputStream(part).use { it.write(bytes); it.fd.sync() }; if (!part.renameTo(target)) return null
        val readBack = target.readBytes(); val hash = MemoryDomain.sha256(readBack.decodeToString()); if (!readBack.contentEquals(bytes) || !readBack.decodeToString().contains("OFFLINE_LOCAL")) { target.delete(); return null }
        val manifest = File(root, "$name.manifest.json"); val manifestPart = File(root, ".${manifest.name}.part"); val manifestJson = "{\"format\":\"nanfeng-ai.offline-eval-manifest\",\"version\":1,\"reportFile\":${q(name)},\"sha256\":${q(hash)}}"; FileOutputStream(manifestPart).use { it.write(manifestJson.toByteArray()); it.fd.sync() }; if (!manifestPart.renameTo(manifest) || !manifest.readText().contains(hash)) { target.delete(); manifest.delete(); return null }
        EvalReportResult(name, hash)
    }.getOrNull()

    /** Read-only integrity check used by local tests and recovery UI; it never parses or executes report content. */
    fun verify(fileName: String): Boolean = runCatching {
        if (fileName.contains('/') || fileName.contains('\\')) return false
        val report = File(root, fileName); val manifest = File(root, "$fileName.manifest.json")
        if (!report.isFile || !manifest.isFile) return false
        val hash = MemoryDomain.sha256(report.readText(Charsets.UTF_8))
        report.readText(Charsets.UTF_8).contains("\"evidenceTier\":\"OFFLINE_LOCAL\"") && manifest.readText(Charsets.UTF_8).contains("\"sha256\":\"$hash\"")
    }.getOrDefault(false)
}
