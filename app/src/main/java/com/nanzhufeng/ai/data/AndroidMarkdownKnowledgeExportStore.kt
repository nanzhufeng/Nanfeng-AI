package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.MarkdownExportManifest
import com.nanzhufeng.ai.domain.MarkdownExportResult
import com.nanzhufeng.ai.domain.MarkdownKnowledgeExportStore
import com.nanzhufeng.ai.domain.MemoryDomain
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

/** P4-H writes a new private Markdown file atomically, then reads that exact final file to verify hash. */
class AndroidMarkdownKnowledgeExportStore(context: Context) : MarkdownKnowledgeExportStore {
    private val root = File(context.filesDir, "exports/markdown/v1")
    override fun write(markdown: String, manifest: MarkdownExportManifest): MarkdownExportResult? = runCatching {
        if (!root.exists() && !root.mkdirs()) return null
        val name = "knowledge-${DateTimeFormatter.ISO_INSTANT.format(manifest.exportedAt).replace(Regex("[^0-9TZ]"), "")}.md"
        val target = File(root, name); val part = File(root, ".${name}.part"); val bytes = markdown.toByteArray(Charsets.UTF_8)
        FileOutputStream(part).use { out -> out.write(bytes); out.fd.sync() }
        if (!part.renameTo(target)) { part.delete(); return null }
        val readBack = target.readBytes(); val hash = MemoryDomain.sha256(readBack.decodeToString())
        if (!readBack.contentEquals(bytes) || hash != MemoryDomain.sha256(markdown)) { target.delete(); return null }
        val manifestFile = File(root, "$name.manifest.json"); val manifestPart = File(root, ".${manifestFile.name}.part")
        val manifestJson = "{\"format\":\"${manifest.format}\",\"version\":${manifest.version},\"exportedAt\":\"${manifest.exportedAt}\",\"itemCount\":${manifest.itemCount},\"markdownFile\":\"$name\",\"markdownSha256\":\"$hash\",\"fidelity\":\"${manifest.fidelity.replace("\\", "\\\\").replace("\"", "\\\"")}\"}"
        FileOutputStream(manifestPart).use { out -> out.write(manifestJson.toByteArray()); out.fd.sync() }
        if (!manifestPart.renameTo(manifestFile) || manifestFile.readText().contains(hash).not()) { target.delete(); manifestFile.delete(); return null }
        MarkdownExportResult(target.name, target.length(), hash, manifest)
    }.getOrNull()
}
