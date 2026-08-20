package com.nanzhufeng.ai.domain

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * P6-A semantic exchange boundary. This is deliberately independent from Room and P5-D:
 * callers build an explicit snapshot, users explicitly select it, and preflight never writes
 * a production database. SAF integration is a later UI concern; it must stage to app-private
 * storage before calling [preflight].
 */
const val NFAI_EXCHANGE_V1_FORMAT = "nfai.exchange"
const val NFAI_EXCHANGE_V1_VERSION = 1
private const val PACKAGE_FORMAT = "nfai.exchange.package"
private const val MAX_ENTRIES = 100_000
private const val MAX_UNCOMPRESSED_BYTES = 128L * 1024L * 1024L
private const val MAX_COMPRESSION_RATIO = 200L

data class NfaiExchangeAsset(val entry: String, val bytes: ByteArray)
data class NfaiExchangeExportSelection(
    val projectIds: Set<String>,
    val conversationIds: Set<String>,
    val knowledgeIds: Set<String>,
    val memoryIds: Set<String>,
    val relationIds: Set<String>,
    /** Asset IDs are explicit because bytes may cross the SAF boundary only by user choice. */
    val attachmentIds: Set<String> = emptySet(),
) {
    init { require(projectIds.isNotEmpty() || conversationIds.isNotEmpty() || knowledgeIds.isNotEmpty() || memoryIds.isNotEmpty() || relationIds.isNotEmpty()) { "跨端交换必须有明确选择。" } }
}
data class NfaiExchangePreparedSnapshot(
    val selection: NfaiExchangeExportSelection,
    val exchangeJson: String,
    val assets: List<NfaiExchangeAsset> = emptyList(),
)
data class NfaiExchangePreflight(
    val semanticHash: String,
    val projectCount: Int,
    val conversationCount: Int,
    val knowledgeCount: Int,
    val memoryCount: Int,
    val relationCount: Int,
    val hasHighSensitiveData: Boolean,
    val packageBytes: Long,
)

sealed interface NfaiExchangeResult {
    data class Exported(val sha256: String, val byteCount: Long) : NfaiExchangeResult
    data class Preflighted(val value: NfaiExchangePreflight) : NfaiExchangeResult
    data class Rejected(val code: String) : NfaiExchangeResult
}

/** Callable Android gateway; no Room, Key, Provider, Prompt, or import mutation path exists here. */
object NfaiExchangeV1Gateway {
    /**
     * Produces the one canonical exchange JSON accepted by [export]. Callers provide semantic
     * facts but never calculate a hash over their own non-canonical serialization.
     */
    fun withComputedSemanticHash(exchange: JSONObject): String {
        val normalized = JSONObject(canonical(exchange))
        val export = normalized.getJSONObject("export")
        export.put("semanticHash", semanticHash(normalized))
        validateExchange(normalized)
        return canonical(normalized)
    }

    fun export(snapshot: NfaiExchangePreparedSnapshot, destination: File): NfaiExchangeResult = runCatching {
        val exchange = JSONObject(snapshot.exchangeJson)
        val verified = validateExchange(exchange)
        require(snapshot.selection.projectIds == exchange.getJSONArray("projects").ids()) { "选择与项目快照不一致。" }
        require(snapshot.selection.conversationIds == exchange.getJSONArray("conversations").ids()) { "选择与会话快照不一致。" }
        require(snapshot.selection.knowledgeIds == exchange.getJSONArray("knowledge").ids()) { "选择与知识快照不一致。" }
        require(snapshot.selection.memoryIds == exchange.getJSONArray("memory").ids()) { "选择与记忆快照不一致。" }
        require(snapshot.selection.relationIds == exchange.getJSONArray("relations").ids()) { "选择与关系快照不一致。" }
        require(snapshot.selection.attachmentIds == exchange.assetIds()) { "选择与附件快照不一致。" }
        require(snapshot.assets.map(NfaiExchangeAsset::entry).toSet() == exchange.assetEntries()) { "附件内容与消息引用不一致。" }
        val entries = linkedMapOf(
            "payload/projects.json" to canonical(exchange.getJSONArray("projects")).toByteArray(),
            "payload/conversations.json" to canonical(exchange.getJSONArray("conversations")).toByteArray(),
            "payload/knowledge.json" to canonical(exchange.getJSONArray("knowledge")).toByteArray(),
            "payload/memory.json" to canonical(exchange.getJSONArray("memory")).toByteArray(),
            "payload/relations.json" to canonical(exchange.getJSONArray("relations")).toByteArray(),
            "payload/settings.safe.json" to canonical(exchange.getJSONObject("settings")).toByteArray(),
        )
        snapshot.assets.forEach { asset ->
            require(asset.entry.matches(Regex("assets/[a-f0-9]{64}"))) { "资产路径不受控。" }
            require(sha256(asset.bytes) == asset.entry.removePrefix("assets/")) { "资产哈希不一致。" }
            entries[asset.entry] = asset.bytes
        }
        val manifest = JSONObject().apply {
            put("format", PACKAGE_FORMAT); put("packageVersion", 1); put("exchangeVersion", NFAI_EXCHANGE_V1_VERSION); put("export", exchange.getJSONObject("export"));
            put("files", JSONArray(entries.entries.sortedBy { it.key }.map { (path, bytes) -> JSONObject().put("path", path).put("byteCount", bytes.size).put("sha256", sha256(bytes)) }))
        }
        destination.outputStream().use { raw -> ZipOutputStream(raw).use { zip ->
            writeZip(zip, "manifest.json", canonical(manifest).toByteArray())
            entries.toSortedMap().forEach { (path, bytes) -> writeZip(zip, path, bytes) }
        } }
        val preflight = preflight(destination).getOrThrowPreflight()
        require(preflight.semanticHash == verified.semanticHash) { "导出语义 hash 不一致。" }
        NfaiExchangeResult.Exported(sha256(destination.readBytes()), destination.length())
    }.getOrElse { NfaiExchangeResult.Rejected("EXPORT_REJECTED") }

    fun preflight(stagedPackage: File): NfaiExchangeResult = runCatching {
        require(stagedPackage.isFile && stagedPackage.length() in 1..MAX_UNCOMPRESSED_BYTES) { "包大小无效。" }
        ZipFile(stagedPackage).use { zip ->
            val entries = linkedMapOf<String, ByteArray>(); var total = 0L
            val names = buildList { val iterator = zip.entries(); while (iterator.hasMoreElements()) add(iterator.nextElement().name) }
            require(names.size in 1..MAX_ENTRIES && names.size == names.toSet().size) { "ZIP entry 异常。" }
            names.forEach { name ->
                require(name.isSafeEntry()) { "ZIP 路径不安全。" }
                val entry = zip.getEntry(name)!!
                require(entry.size in 0..MAX_UNCOMPRESSED_BYTES && entry.compressedSize > 0 && entry.size / entry.compressedSize <= MAX_COMPRESSION_RATIO) { "ZIP 压缩异常。" }
                total += entry.size; require(total <= MAX_UNCOMPRESSED_BYTES) { "ZIP 总大小超限。" }
                entries[name] = zip.getInputStream(entry).use { it.readBounded(MAX_UNCOMPRESSED_BYTES) }
            }
            val manifest = JSONObject(entries["manifest.json"]?.toString(Charsets.UTF_8) ?: error("Manifest 缺失"))
            require(manifest.optString("format") == PACKAGE_FORMAT && manifest.optInt("packageVersion") == 1 && manifest.optInt("exchangeVersion") == NFAI_EXCHANGE_V1_VERSION) { "不支持的交换包。" }
            val files = manifest.getJSONArray("files"); require(entries.size == files.length() + 1) { "存在未知 entry。" }
            repeat(files.length()) { index ->
                val file = files.getJSONObject(index); val bytes = entries[file.getString("path")] ?: error("Manifest entry 缺失")
                require(bytes.size.toLong() == file.getLong("byteCount") && sha256(bytes) == file.getString("sha256")) { "entry 完整性失败。" }
            }
            val exchange = JSONObject().apply {
                put("format", NFAI_EXCHANGE_V1_FORMAT); put("version", NFAI_EXCHANGE_V1_VERSION); put("export", manifest.getJSONObject("export"))
                put("projects", JSONArray(entries.required("payload/projects.json").toString(Charsets.UTF_8))); put("conversations", JSONArray(entries.required("payload/conversations.json").toString(Charsets.UTF_8)))
                put("knowledge", JSONArray(entries.required("payload/knowledge.json").toString(Charsets.UTF_8))); put("memory", JSONArray(entries.required("payload/memory.json").toString(Charsets.UTF_8)))
                put("relations", JSONArray(entries.required("payload/relations.json").toString(Charsets.UTF_8))); put("settings", JSONObject(entries.required("payload/settings.safe.json").toString(Charsets.UTF_8)))
            }
            val verified = validateExchange(exchange)
            require(entries.keys.filter { it.startsWith("assets/") }.toSet() == exchange.assetEntries()) { "附件内容与消息引用不一致。" }
            NfaiExchangeResult.Preflighted(NfaiExchangePreflight(verified.semanticHash, exchange.getJSONArray("projects").length(), exchange.getJSONArray("conversations").length(), exchange.getJSONArray("knowledge").length(), exchange.getJSONArray("memory").length(), exchange.getJSONArray("relations").length(), verified.highSensitive, stagedPackage.length()))
        }
    }.getOrElse { NfaiExchangeResult.Rejected("PREFLIGHT_REJECTED") }

    private data class Verified(val semanticHash: String, val highSensitive: Boolean)
    private fun validateExchange(exchange: JSONObject): Verified {
        exchange.requireExact("format", "version", "export", "projects", "conversations", "knowledge", "memory", "relations", "settings")
        require(exchange.getString("format") == NFAI_EXCHANGE_V1_FORMAT && exchange.getInt("version") == NFAI_EXCHANGE_V1_VERSION) { "协议版本不支持。" }
        rejectForbidden(exchange)
        val export = exchange.getJSONObject("export"); export.requireExact("id", "createdAt", "origin", "semanticHash", "sensitivity"); requireStableId(export.getString("id")); requireHash(export.getString("semanticHash"));
        require(export.getJSONObject("origin").getString("platform") in setOf("ANDROID", "DESKTOP")) { "来源平台无效。" }
        val expectedHash = semanticHash(exchange); require(export.getString("semanticHash") == expectedHash) { "semantic hash 不一致。" }
        listOf("projects", "conversations", "knowledge", "memory", "relations").forEach { key -> require(exchange.get(key) is JSONArray) { "$key 必须是数组。" } }
        val ids = mutableSetOf<String>(); listOf("projects", "conversations", "knowledge", "memory", "relations").forEach { key -> exchange.getJSONArray(key).forEachObject { value -> value.requireKey("id"); require(ids.add(value.getString("id"))) { "稳定 ID 重复。" }; requireStableId(value.getString("id")) } }
        exchange.getJSONArray("knowledge").forEachObject { value -> requireHash(value.getString("contentHash")); require(sha256(value.getString("body").toByteArray()) == value.getString("contentHash")) { "知识正文 hash 不一致。" } }
        exchange.getJSONArray("memory").forEachObject { value -> requireHash(value.getString("contentHash")); require(sha256(value.getString("body").toByteArray()) == value.getString("contentHash")) { "记忆正文 hash 不一致。" } }
        exchange.getJSONArray("conversations").forEachObject { conversation ->
            val messages = conversation.getJSONArray("messages"); val messageIds = messages.ids(); require(messageIds.contains(conversation.getString("currentLeafId"))) { "current leaf 不存在。" }
            messages.forEachObject { message -> if (message.getString("delivery") == "PARTIAL") require(message.getString("role") == "assistant") { "部分消息只允许 assistant。" }; message.getJSONArray("blocks").forEachObject { block -> if (block.getString("kind") == "ASSET_REF") { val asset = block.getJSONObject("asset"); require(asset.getString("entry") == "assets/${asset.getString("sha256")}") { "资产必须内容寻址。" } } } }
        }
        return Verified(expectedHash, canonical(exchange).contains("\"HIGH_SENSITIVE\""))
    }
    private fun semanticHash(exchange: JSONObject): String { val copy = JSONObject(canonical(exchange)); copy.getJSONObject("export").remove("semanticHash"); return sha256(canonical(copy).toByteArray()) }
    private fun canonical(value: Any?): String = when (value) { null, JSONObject.NULL -> "null"; is String -> quote(value); is Boolean, is Int, is Long -> value.toString(); is Number -> require(value.toDouble().isFinite() && value.toLong().toDouble() == value.toDouble()) { "仅允许安全整数。" }.let { value.toLong().toString() }; is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(separator = ",", prefix = "{", postfix = "}") { "${quote(it)}:${canonical(value.get(it))}" }; is JSONArray -> (0 until value.length()).joinToString(separator = ",", prefix = "[", postfix = "]") { canonical(value.get(it)) }; else -> error("不支持的 JSON 值。") }
    private fun quote(value: String): String = buildString { append('"'); value.forEach { char -> when (char) { '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\u000C' -> append("\\f"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char) } }; append('"') }
    private fun rejectForbidden(value: Any?) { when (value) { is JSONObject -> value.keys().asSequence().toList().forEach { key -> require(!Regex("(?i)(credential|api[_-]?key|authorization|provider[_-]?(raw|payload)|runtime[_-]?chunk|diagnostic|route[_-]?pref|uri|path)").containsMatchIn(key)) { "禁止的跨端字段。" }; rejectForbidden(value.get(key)) }; is JSONArray -> (0 until value.length()).forEach { rejectForbidden(value.get(it)) } } }
    private fun writeZip(zip: ZipOutputStream, path: String, bytes: ByteArray) { val entry = java.util.zip.ZipEntry(path).apply { time = 0L }; zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry() }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun NfaiExchangeResult.getOrThrowPreflight(): NfaiExchangePreflight = (this as? NfaiExchangeResult.Preflighted)?.value ?: error("导出预检失败。")
}

private fun String.isSafeEntry() = isNotBlank() && !startsWith('/') && !contains("..") && !contains('\\')
private fun Map<String, ByteArray>.required(key: String) = get(key) ?: error("缺少 $key")
private fun java.io.InputStream.readBounded(max: Long): ByteArray { val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); while (true) { val read = read(buffer); if (read < 0) break; require(out.size().toLong() + read <= max) { "entry 超限。" }; out.write(buffer, 0, read) }; return out.toByteArray() }
private fun JSONArray.ids(): Set<String> = buildSet { forEachObject { add(it.getString("id")) } }
private fun JSONArray.forEachObject(block: (JSONObject) -> Unit) { repeat(length()) { index -> block(getJSONObject(index)) } }
private fun JSONObject.assetIds(): Set<String> = buildSet {
    getJSONArray("conversations").forEachObject { conversation ->
        conversation.getJSONArray("messages").forEachObject { message ->
            message.getJSONArray("blocks").forEachObject { block ->
                if (block.getString("kind") == "ASSET_REF") add(block.getJSONObject("asset").getString("id"))
            }
        }
    }
}
private fun JSONObject.assetEntries(): Set<String> = buildSet {
    getJSONArray("conversations").forEachObject { conversation ->
        conversation.getJSONArray("messages").forEachObject { message ->
            message.getJSONArray("blocks").forEachObject { block ->
                if (block.getString("kind") == "ASSET_REF") add(block.getJSONObject("asset").getString("entry"))
            }
        }
    }
}
private fun JSONObject.requireKey(key: String) { require(has(key)) { "缺少 $key" } }
private fun JSONObject.requireExact(vararg keys: String) { val actual = this.keys().asSequence().toSet(); require(actual == keys.toSet()) { "未知或缺少字段：$actual" } }
private fun requireStableId(value: String) { require(value.matches(Regex("[a-z0-9][a-z0-9_-]{1,63}"))) { "稳定 ID 无效。" } }
private fun requireHash(value: String) { require(value.matches(Regex("[a-f0-9]{64}"))) { "SHA-256 无效。" } }
