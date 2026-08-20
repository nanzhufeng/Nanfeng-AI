package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.JsonKnowledgeAsset
import com.nanzhufeng.ai.domain.JsonKnowledgeExportManifest
import com.nanzhufeng.ai.domain.JsonKnowledgeExportResult
import com.nanzhufeng.ai.domain.JsonKnowledgeExportStore
import com.nanzhufeng.ai.domain.JsonKnowledgeFailure
import com.nanzhufeng.ai.domain.JsonKnowledgePrivateAssetStore
import com.nanzhufeng.ai.domain.JsonKnowledgePrivateCopyRequest
import com.nanzhufeng.ai.domain.JsonKnowledgePrivateCopyResult
import com.nanzhufeng.ai.domain.MemoryDomain
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.format.DateTimeFormatter

/** P4-L owns a separate private JSON asset namespace; no external URI or path is persisted. */
class AndroidJsonKnowledgePrivateAssetStore(context: Context) : JsonKnowledgePrivateAssetStore {
    private val root = File(context.filesDir, "json-knowledge-import-assets/v1")
    override fun copy(request: JsonKnowledgePrivateCopyRequest): JsonKnowledgePrivateCopyResult = runCatching {
        if (request.bytes.size > 768 * 1024) return JsonKnowledgePrivateCopyResult.Failed(JsonKnowledgeFailure.TOO_LARGE)
        if (!root.exists() && !root.mkdirs()) return JsonKnowledgePrivateCopyResult.Failed(JsonKnowledgeFailure.PRIVATE_COPY_FAILED)
        val safe = request.displayName.substringAfterLast('/').replace(Regex(" \\(\\d+\\)$"), "").replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "knowledge.json" }
        val key = "json-knowledge-import-assets/v1/${request.taskId.value}/$safe"; val target = File(root.parentFile!!.parentFile!!, key); target.parentFile?.mkdirs(); val part = File(target.parentFile, ".${target.name}.part")
        FileOutputStream(part).use { it.write(request.bytes); it.fd.sync() }; if (!part.renameTo(target)) { part.delete(); return JsonKnowledgePrivateCopyResult.Failed(JsonKnowledgeFailure.PRIVATE_COPY_FAILED) }
        JsonKnowledgePrivateCopyResult.Copied(JsonKnowledgeAsset(key, request.mimeType, safe, request.bytes.size.toLong(), request.bytes.digest()))
    }.getOrElse { JsonKnowledgePrivateCopyResult.Failed(JsonKnowledgeFailure.PRIVATE_COPY_FAILED) }
    override fun read(storageKey: String): ByteArray? = if (storageKey.matches(Regex("json-knowledge-import-assets/v1/[A-Za-z0-9-]+/[A-Za-z0-9._-]+"))) File(root.parentFile!!.parentFile!!, storageKey).takeIf(File::isFile)?.readBytes() else null
}

/** Writes canonical UTF-8 JSON atomically and verifies the exact final file before reporting success. */
class AndroidJsonKnowledgeExportStore(context: Context) : JsonKnowledgeExportStore {
    private val root = File(context.filesDir, "exports/json-knowledge/v1")
    override fun write(document: String, manifest: JsonKnowledgeExportManifest): JsonKnowledgeExportResult? = runCatching {
        if (!root.exists() && !root.mkdirs()) return null
        val name = "knowledge-${DateTimeFormatter.ISO_INSTANT.format(manifest.exportedAt).replace(Regex("[^0-9TZ]"), "")}.json"; val target = File(root, name); val part = File(root, ".${name}.part"); val bytes = document.toByteArray(Charsets.UTF_8)
        FileOutputStream(part).use { it.write(bytes); it.fd.sync() }; if (!part.renameTo(target)) { part.delete(); return null }
        val readBack = target.readBytes(); val hash = readBack.digest(); if (!readBack.contentEquals(bytes)) { target.delete(); return null }
        val manifestFile = File(root, "$name.manifest.json"); val manifestPart = File(root, ".${manifestFile.name}.part")
        val payload = "{\"format\":\"${manifest.format}\",\"schemaVersion\":${manifest.schemaVersion},\"exportedAt\":\"${manifest.exportedAt}\",\"itemCount\":${manifest.itemCount},\"jsonFile\":\"$name\",\"sha256\":\"$hash\",\"fidelity\":\"${manifest.fidelity.replace("\"", "\\\"")}\"}"
        FileOutputStream(manifestPart).use { it.write(payload.toByteArray(Charsets.UTF_8)); it.fd.sync() }; if (!manifestPart.renameTo(manifestFile) || !manifestFile.readText(Charsets.UTF_8).contains(hash)) { target.delete(); manifestFile.delete(); return null }
        JsonKnowledgeExportResult(name, target.length(), hash, manifest)
    }.getOrNull()
}
private fun ByteArray.digest() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
